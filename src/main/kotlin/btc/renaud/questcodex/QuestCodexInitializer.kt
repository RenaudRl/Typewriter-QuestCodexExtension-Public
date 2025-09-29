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
                blockedMessage = it.blockedMessage.replace("\r", "").split("\n"),
                activeMessage = it.activeMessage.replace("\r", "").split("\n"),
                completedMessage = it.completedMessage.replace("\r", "").split("\n"),
                hideLockedQuests = it.hideLockedQuests,
                hideWhenLocked = it.hideWhenLocked,
                iconName = it.iconName,
            )
        }

        // Assign quests to their categories based on the quest references
        Query.find<QuestCategoryEntry>().forEach { entry ->
            val overrides = QuestItemOverrides(
                notStarted = entry.notStartedItem,
                inProgress = entry.inProgressItem,
                completed = entry.completedItem,
            )
            if (entry.questOrders.size > entry.questRefs.size) {
                plugin.logger.warning(
                    "[QuestCodex] Quest category '${entry.category}' defines more quest orders than quest refs; extra orders will be ignored."
                )
            }
            entry.questRefs.forEachIndexed { index, ref ->
                val quest = ref.get()
                if (quest != null) {
                    val order = entry.questOrders.getOrNull(index)?.takeIf { it != 0 }
                    QuestCategoryRegistry.addQuest(
                        entry.category,
                        ref,
                        quest,
                        order,
                        overrides.takeIf { it.hasOverrides() },
                    )
                }
            }
        }

        // Register restriction messages for quests
        Query.find<QuestCategoryRestrictionEntry>().forEach { entry ->
            val lines = entry.message.replace("\r", "").split("\n")
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
        val future = Bukkit.getScheduler().callSyncMethod(plugin) {
            action()
            null
        }
        try {
            future.get()
        } catch (throwable: Exception) {
            future.cancel(true)
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