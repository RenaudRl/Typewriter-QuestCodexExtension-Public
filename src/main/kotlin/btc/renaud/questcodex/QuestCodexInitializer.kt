package btc.renaud.questcodex

import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.logging.Level
import com.typewritermc.engine.paper.utils.item.*
import kotlin.reflect.KClass

/**
 * Basic initializer for the Quest Codex extension.
 * Currently this does not perform any logic but exists so the extension is
 * recognized by the Typewriter engine.
 */
@Singleton
object QuestCodexInitializer : Initializable {
    private val listener = QuestCategoryListener()
    private var refreshTask: FoliaScheduler.TaskHandle? = null

    override suspend fun initialize() {
        val manager = Bukkit.getPluginManager()
        manager.registerEvents(listener, plugin)

        // BTC Engine Native Support
        try {
            val itemCompanion = Class.forName("com.typewritermc.engine.paper.utils.item.Item\$Companion")
            val instance = itemCompanion.getField("INSTANCE").get(null)
            val registerAll = itemCompanion.getMethod("registerAll")
            registerAll.invoke(instance)
            plugin.logger.info("[QuestCodex] BTC Custom Engine detected. Native item types registered.")
        } catch (_: Exception) {
            // Not running on BTC Engine or registerAll not available
        }

        QuestCodexConfig.reset()
        val settingsEntries = Query.find<QuestCodexSettingsEntry>().toList()
        if (settingsEntries.isEmpty()) {
            plugin.logger.info("[QuestCodex] No quest_codex_settings entry found; using transient defaults. (Note: in-game item capture requires a published entry).")
            QuestCodexConfig.apply(QuestCodexDefaults.settingsEntry())
        } else {
            settingsEntries.forEach { entry ->
                QuestCodexConfig.apply(entry)
            }
        }

        // Register categories defined through typewriter entries
        Query.find<QuestCategoryDefinitionEntry>().forEach {
            QuestCategoryRegistry.register(
                name = it.category,
                title = it.title.ifBlank { it.category },
                rows = it.rows,
                item = it.item,
                nameColor = it.nameColor,
                parent = it.parent,
                order = it.order,
                slot = it.slot.takeIf { slot -> slot >= 0 },
                questSlots = parseSlots(it.questSlots, "category ${it.category}"),
                activeCriteria = it.activeCriteria,
                completedCriteria = it.completedCriteria,
                blockedMessage = parseLines(it.blockedMessage),
                activeMessage = parseLines(it.activeMessage),
                completedMessage = parseLines(it.completedMessage),
                hideLockedQuests = it.hideLockedQuests,
                hideWhenLocked = it.hideWhenLocked,
                iconName = it.iconName,
                categoryLoreQuestCount = parseOptionalLore(it.categoryLoreQuestCount),
                categoryLore = parseOptionalLore(it.categoryLore),
                refQuest = it.refQuest,
                showPrologueButton = it.showPrologueButton,
                showQuestButton = it.showQuestButton,
                showCategoriesButton = it.showCategoriesButton,
            )
        }

        // Register prologue replay categories
        Query.find<PrologueReplayCategoryEntry>().forEach { entry ->
            PrologueReplayRegistry.register(
                id = entry.id,
                title = entry.title.ifBlank { entry.name },
                item = entry.item,
                order = entry.order,
                slot = entry.slot ?: -1,
                questCategory = entry.questCategory,
                entries = entry.entries
            )
        }

        // Load per-quest additional lore entries (global, status-based)
        val additionalLoreByQuestId: Map<String, QuestAdditionalLore> = Query.find<QuestAdditionalLoreEntry>()
            .associate { entry ->
                val questId = entry.quest.id
                questId to QuestAdditionalLore(
                    notStarted = parseLines(entry.additionalLoreNotStarted),
                    inProgress = parseLines(entry.additionalLoreInProgress),
                    completed = parseLines(entry.additionalLoreCompleted),
                )
            }

        // Assign quests to their categories based on the quest references
        Query.find<QuestCategoryEntry>().forEach { entry ->
            val defaultItemOverrides = QuestItemOverrides(
                notStarted = entry.notStartedItem,
                inProgress = entry.inProgressItem,
                completed = entry.completedItem,
            ).takeIf { it.hasOverrides() }
            val defaultDisplayOverrides = QuestDisplayOverrides(
                notStarted = QuestStateDisplayOverride(
                    name = entry.notStartedName,
                    lore = parseLines(entry.notStartedLore),
                    hideQuest = entry.hideWhenNotStarted,
                ),
                inProgress = QuestStateDisplayOverride(
                    name = entry.inProgressName,
                    lore = parseLines(entry.inProgressLore),
                    hideQuest = entry.hideWhenInProgress,
                    hideObjectives = entry.hideObjectivesWhenInProgress,
                ),
                completed = QuestStateDisplayOverride(
                    name = entry.completedName,
                    lore = parseLines(entry.completedLore),
                    hideQuest = entry.hideWhenCompleted,
                    hideObjectives = entry.hideObjectivesWhenCompleted,
                ),
            ).takeIf { it.hasOverrides() }
            if (entry.questOrders.size > entry.questRefs.size) {
                plugin.logger.warning(
                    "[QuestCodex] Quest category '${entry.category}' defines more quest orders than quest refs; extra orders will be ignored."
                )
            }
            val questOverrideMap = buildQuestOverrideMap(entry)
            val unusedOverrides = questOverrideMap.keys.toMutableSet()
            entry.questRefs.forEachIndexed { index, ref ->
                val questId = ref.id
                if (questId.isBlank()) return@forEachIndexed
                val questOverride = questOverrideMap[questId]
                if (questOverride != null) {
                    unusedOverrides -= questId
                }
                val quest = ref.get()
                if (quest != null) {
                    val order = entry.questOrders.getOrNull(index)?.takeIf { it != 0 }
                    val questItemOverrides = questOverride?.toItemOverrides()?.takeIf { it.hasOverrides() }
                    val questDisplayOverrides = questOverride?.toDisplayOverrides()?.takeIf { it.hasOverrides() }
                    val overrideAdditionalLore = questOverride?.additionalLore()
                    val baseAdditionalLore = additionalLoreByQuestId[questId] ?: QuestAdditionalLore()
                    val mergedAdditionalLore = overrideAdditionalLore?.let { baseAdditionalLore.overrideWith(it) }
                        ?: baseAdditionalLore
                    val mergedItemOverrides = when {
                        defaultItemOverrides != null && questItemOverrides != null ->
                            defaultItemOverrides.overrideWith(questItemOverrides)
                        questItemOverrides != null -> questItemOverrides
                        else -> defaultItemOverrides
                    }
                    val mergedDisplayOverrides = when {
                        defaultDisplayOverrides != null && questDisplayOverrides != null ->
                            defaultDisplayOverrides.overrideWith(questDisplayOverrides)
                        questDisplayOverrides != null -> questDisplayOverrides
                        else -> defaultDisplayOverrides
                    }
                    QuestCategoryRegistry.addQuest(
                        entry.category,
                        ref,
                        quest,
                        order,
                        mergedItemOverrides,
                        mergedDisplayOverrides,
                        mergedAdditionalLore.takeIf { it.hasContent() },
                    )
                } else {
                    if (questId.isNotBlank() && questOverride != null) {
                        plugin.logger.warning(
                            "[QuestCodex] Quest category '${entry.category}' defines overrides for quest $questId, but the quest could not be resolved."
                        )
                    }
                }
            }
            if (unusedOverrides.isNotEmpty()) {
                plugin.logger.warning(
                    "[QuestCodex] Quest category '${entry.category}' defines overrides for quests ${unusedOverrides.joinToString()} that are not part of quest_refs. The overrides were ignored."
                )
            }
        }

        // Register restriction messages for quests
        Query.find<QuestCategoryRestrictionEntry>().forEach { entry ->
            val lines = parseLines(entry.message)
            entry.questRefs.forEach { ref ->
                if (ref.get() != null) {
                    QuestCategoryRegistry.setRestriction(entry.category, ref, lines)
                }
            }
        }

        // Periodic refresh for open Codex inventories to avoid manual reloads
        refreshTask = FoliaScheduler.runAtFixedRate(40L, 40L) {
            Bukkit.getOnlinePlayers().forEach { player ->
                val holder = player.openInventory.topInventory.holder
                if (holder is QuestCategoryInventory) {
                    holder.loadPage(holder.currentPage)
                } else if (holder is QuestCategoryMainInventory) {
                    holder.loadPage(holder.currentPage)
                }
            }
        }
    }

    override suspend fun shutdown() {
        InventoryClickEvent.getHandlerList().unregister(listener)
        refreshTask?.cancel()

        when {
            Bukkit.isPrimaryThread() -> closeQuestInventories()
            plugin.isEnabled -> runSynchronously(::closeQuestInventories)
            else -> closeQuestInventories()
        }
    }

    private fun closeQuestInventories() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val holder = player.openInventory.topInventory.holder
            if (holder is QuestCategoryInventory || holder is QuestCategoryMainInventory || holder is PrologueReplayInventory) {
                player.closeInventory()
            }
        }
    }

    private fun runSynchronously(action: () -> Unit) {
        try {
            FoliaScheduler.runSync(action)
        } catch (throwable: Throwable) {
            plugin.logger.log(Level.SEVERE, "[QuestCodex] Failed to execute shutdown action", throwable)
            action()
        }
    }

    private fun validateRef(ref: com.typewritermc.core.entries.Ref<*>, entryId: String, entryType: String, fieldName: String): Boolean {
        if (ref.id.isBlank()) {
            plugin.logger.warning("[QuestCodex] Entry '$entryId' ($entryType) has an empty '$fieldName' reference. Skipping field.")
            return false
        }
        return true
    }
}


private fun parseLines(raw: String): List<String> {
    val sanitized = raw.replace("\r", "")
    if (sanitized.isBlank()) return emptyList()
    return sanitized.split("\n")
}

private fun parseOptionalLore(raw: String): List<String>? {
    val sanitized = raw.replace("\r", "")
    if (sanitized.isEmpty()) return null
    val trimmed = sanitized.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed == "-") return emptyList()
    return sanitized.split("\n")
}

private fun QuestCategoryQuestOverride.toItemOverrides(): QuestItemOverrides = QuestItemOverrides(
    notStarted = notStartedItem,
    inProgress = inProgressItem,
    completed = completedItem,
)

private fun QuestCategoryQuestOverride.toDisplayOverrides(): QuestDisplayOverrides = QuestDisplayOverrides(
    notStarted = QuestStateDisplayOverride(
        name = notStartedName,
        lore = parseLines(notStartedLore),
        hideQuest = hideWhenNotStarted,
    ),
    inProgress = QuestStateDisplayOverride(
        name = inProgressName,
        lore = parseLines(inProgressLore),
        hideQuest = hideWhenInProgress,
        hideObjectives = hideObjectivesWhenInProgress,
    ),
    completed = QuestStateDisplayOverride(
        name = completedName,
        lore = parseLines(completedLore),
        hideQuest = hideWhenCompleted,
        hideObjectives = hideObjectivesWhenCompleted,
    ),
)

private fun QuestCategoryQuestOverride.additionalLore(): QuestAdditionalLore = QuestAdditionalLore(
    notStarted = parseLines(additionalLoreNotStarted),
    inProgress = parseLines(additionalLoreInProgress),
    completed = parseLines(additionalLoreCompleted),
)

private fun buildQuestOverrideMap(entry: QuestCategoryEntry): Map<String, QuestCategoryQuestOverride> {
    if (entry.questOverrides.isEmpty()) return emptyMap()
    val overridesByQuest = mutableMapOf<String, QuestCategoryQuestOverride>()
    entry.questOverrides.forEach { override ->
        val questId = override.quest.id
        if (questId.isBlank()) {
            plugin.logger.warning(
                "[QuestCodex] Quest category '${entry.category}' defines an override without a quest reference; the override will be ignored."
            )
            return@forEach
        }
        val previous = overridesByQuest.put(questId, override)
        if (previous != null) {
            plugin.logger.warning(
                "[QuestCodex] Quest category '${entry.category}' defines multiple overrides for quest $questId; the last override will be used."
            )
        }
    }
    return overridesByQuest
}
