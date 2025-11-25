package btc.renaud.questcodex

import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit
import org.bukkit.event.inventory.InventoryClickEvent
import java.util.logging.Level

/**
 * Basic initializer for the Quest Codex extension.
 * Currently this does not perform any logic but exists so the extension is
 * recognized by the Typewriter engine.
 */
@Singleton
object QuestCodexInitializer : Initializable {
    private val listener = QuestCategoryListener()

    override suspend fun initialize() {
        val manager = Bukkit.getPluginManager()
        manager.registerEvents(listener, plugin)

        QuestCodexConfig.reset()
        val settingsEntries = Query.find<QuestCodexSettingsEntry>().toList()
        if (settingsEntries.isEmpty()) {
            plugin.logger.fine("[QuestCodex] No quest_codex_settings entry found; applying default configuration.")
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
                questSlots = parseQuestSlots(it.questSlots, it.category),
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
            val defaultAdditionalLore = entry.questAdditionalLore.map { parseLines(it) }
            if (entry.questOrders.size > entry.questRefs.size) {
                plugin.logger.warning(
                    "[QuestCodex] Quest category '${entry.category}' defines more quest orders than quest refs; extra orders will be ignored."
                )
            }
            val questOverrideMap = buildQuestOverrideMap(entry)
            val unusedOverrides = questOverrideMap.keys.toMutableSet()
            entry.questRefs.forEachIndexed { index, ref ->
                val questId = ref.id
                val questOverride = questOverrideMap[questId]
                if (questOverride != null) {
                    unusedOverrides -= questId
                }
                val quest = ref.get()
                if (quest != null) {
                    val order = entry.questOrders.getOrNull(index)?.takeIf { it != 0 }
                    val questItemOverrides = questOverride?.toItemOverrides()?.takeIf { it.hasOverrides() }
                    val questDisplayOverrides = questOverride?.toDisplayOverrides()?.takeIf { it.hasOverrides() }
                    val questAdditionalLore = questOverride?.additionalLoreLines().orEmpty()
                    val defaultAdditionalLoreForQuest = defaultAdditionalLore.getOrNull(index).orEmpty()
                    val mergedAdditionalLore = when {
                        questAdditionalLore.isNotEmpty() -> questAdditionalLore
                        else -> defaultAdditionalLoreForQuest
                    }
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
                        mergedAdditionalLore.takeIf { it.isNotEmpty() },
                    )
                } else if (questOverride != null) {
                    plugin.logger.warning(
                        "[QuestCodex] Quest category '${entry.category}' defines overrides for quest $questId, but the quest could not be resolved."
                    )
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
    }

    override suspend fun shutdown() {
        InventoryClickEvent.getHandlerList().unregister(listener)

        when {
            Bukkit.isPrimaryThread() -> closeQuestInventories()
            plugin.isEnabled -> runSynchronously(::closeQuestInventories)
            else -> closeQuestInventories()
        }
    }

    private fun closeQuestInventories() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val holder = player.openInventory.topInventory.holder
            if (holder is QuestCategoryInventory || holder is QuestCategoryMainInventory) {
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
}

private fun parseQuestSlots(rawSlots: List<String>, category: String): List<Int> {
    if (rawSlots.isEmpty()) return emptyList()
    val resolved = linkedSetOf<Int>()
    for (raw in rawSlots) {
        val token = raw.trim()
        if (token.isEmpty()) continue
        val rangeParts = token.split('-', limit = 2).map { it.trim() }
        if (rangeParts.size == 1) {
            val value = rangeParts[0].toIntOrNull()
            if (value != null) {
                resolved += value
            } else {
                plugin.logger.warning(
                    "[QuestCodex] Invalid quest slot '$token' for category $category. Expected a number or range."
                )
            }
            continue
        }

        val start = rangeParts[0].toIntOrNull()
        val end = rangeParts[1].toIntOrNull()
        if (start == null || end == null) {
            plugin.logger.warning(
                "[QuestCodex] Invalid quest slot range '$token' for category $category. Expected numeric bounds."
            )
            continue
        }

        val (min, max) = if (start <= end) start to end else end to start
        for (slot in min..max) {
            resolved += slot
        }
    }
    return resolved.toList()
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

private fun QuestCategoryQuestOverride.additionalLoreLines(): List<String> = parseLines(additionalLore)

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

