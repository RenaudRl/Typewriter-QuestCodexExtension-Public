package btc.renaud.questcodex

import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit
import org.bukkit.event.inventory.InventoryClickEvent

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

        // Register categories defined through typewriter entries
        Query.find<QuestCategoryDefinitionEntry>().forEach {
            QuestCategoryRegistry.register(
                it.category,
                it.title.ifBlank { it.category },
                it.rows,
                it.item,
                it.nameColor,
                it.parent,
                it.activeCriteria,
                it.completedCriteria,
                it.blockedMessage.replace("\r", "").split("\n"),
                it.activeMessage.replace("\r", "").split("\n"),
                it.completedMessage.replace("\r", "").split("\n"),
            )
        }

        // Assign quests to their categories based on the quest references
        Query.find<QuestCategoryEntry>().forEach { entry ->
            entry.questRefs.forEach { ref ->
                if (ref.get() != null) {
                    QuestCategoryRegistry.addQuest(entry.category, ref)
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

        // Closing inventories must happen on the main server thread to avoid
        // asynchronous InventoryCloseEvent errors during reloads.
        Bukkit.getScheduler().callSyncMethod(plugin) {
            Bukkit.getOnlinePlayers().forEach { player ->
                val holder = player.openInventory.topInventory.holder
                if (holder is QuestCategoryInventory || holder is QuestCategoryMainInventory) {
                    player.closeInventory()
                }
            }
        }.get()
    }
}