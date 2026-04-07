package btc.renaud.questcodex

import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.entry.temporal.TemporalSettings
import com.typewritermc.engine.paper.entry.temporal.TemporalStartTrigger
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.interaction.PlayerSessionManager
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.item.toItem
import com.typewritermc.engine.paper.utils.server
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PrologueReplayInventory(
    private val player: Player,
    private val initialCategory: PrologueReplayCategory? = null,
    var currentPage: Int = 0
) : InventoryHolder, KoinComponent {

    private val sessionManager: PlayerSessionManager by inject()
    private val menuConfig = QuestCodexConfig.replayMenu
    private val rows = menuConfig.rows.coerceIn(2, 6)
    private val size = rows * 9
    private val inventory: Inventory = server.createInventory(
        this,
        size,
        (initialCategory?.title ?: menuConfig.title).parsePlaceholders(player).asMini()
    )

    private val backSlot = menuConfig.backButton.resolveSlot(rows, size)
    private val autoSlots: List<Int> by lazy {
        val configured = parseSlots(menuConfig.slots, "replay menu")
        if (configured.isNotEmpty()) {
            configured.filter { it in 0 until size }
        } else {
            // Default layout: rows-1 used for items
            buildList {
                for (i in 0 until (rows - 1) * 9) {
                    add(i)
                }
            }
        }
    }

    private val slotToReplay = mutableMapOf<Int, PrologueReplayEntryData>()
    private val slotToCategory = mutableMapOf<Int, PrologueReplayCategory>()

    init {
        loadPage(currentPage)
    }

    fun loadPage(page: Int) {
        currentPage = page
        inventory.clear()
        slotToReplay.clear()
        slotToCategory.clear()

        if (initialCategory != null) {
            renderEntries(initialCategory)
        } else {
            renderCategories()
        }

        if (menuConfig.backButton.enabled) {
            val barrierMaterialStr = menuConfig.defaultBarrierMaterial
            val barrierMaterial = Material.getMaterial(barrierMaterialStr) ?: Material.BARRIER
            inventory.setItem(backSlot, menuConfig.backButton.toItemTemplate().buildItem(player, barrierMaterial))
        }
    }

    private fun renderCategories() {
        val categories = PrologueReplayRegistry.all()
        categories.forEach { category ->
            val slot = if (category.slot >= 0) category.slot else -1
            if (slot in 0 until size) {
                inventory.setItem(slot, buildCategoryItem(category))
                slotToCategory[slot] = category
            }
        }

        // Auto layout for categories without explicit slot
        val autoCategories = categories.filter { it.slot < 0 }
        var autoIndex = 0
        for (slot in autoSlots) {
            if (inventory.getItem(slot) == null && autoIndex < autoCategories.size) {
                val category = autoCategories[autoIndex++]
                inventory.setItem(slot, buildCategoryItem(category))
                slotToCategory[slot] = category
            }
        }
    }

    private fun renderEntries(category: PrologueReplayCategory) {
        val entries = category.entries
        entries.forEach { entry ->
            if (entry.slot in 0 until size) {
                inventory.setItem(entry.slot, buildEntryItem(entry))
                slotToReplay[entry.slot] = entry
            }
        }

        // Auto layout for entries without explicit slot
        val autoEntries = entries.filter { it.slot < 0 }
        var autoIndex = 0
        for (slot in autoSlots) {
            if (inventory.getItem(slot) == null && autoIndex < autoEntries.size) {
                val entry = autoEntries[autoIndex++]
                inventory.setItem(slot, buildEntryItem(entry))
                slotToReplay[slot] = entry
            }
        }
    }

    private fun buildCategoryItem(category: PrologueReplayCategory): ItemStack {
        val fillMaterialStr = menuConfig.defaultFillMaterial
        val fillMaterial = Material.getMaterial(fillMaterialStr) ?: Material.GRAY_STAINED_GLASS_PANE
        val template = ItemTemplate(
            item = category.item,
            name = category.title,
            lore = listOf("<gray>Click to view replays</gray>")
        )
        return template.buildItem(player, fillMaterial)
    }

    private fun buildEntryItem(entry: PrologueReplayEntryData): ItemStack {
        val completed = entry.completeCriteria.isNotEmpty() && entry.completeCriteria.matches(player)
        val unlocked = entry.unlockCriteria.isEmpty() || entry.unlockCriteria.matches(player)

        val template = when {
            completed -> ItemTemplate(
                item = if (!entry.completedItem.isEffectivelyEmpty()) entry.completedItem else if (!entry.unlockedItem.isEffectivelyEmpty()) entry.unlockedItem else ItemStack(Material.ENDER_EYE).toItem(),
                name = entry.completedName.ifEmpty { entry.unlockedName },
                lore = entry.completedLore.ifEmpty { entry.unlockedLore }
            )
            unlocked -> ItemTemplate(
                item = if (!entry.unlockedItem.isEffectivelyEmpty()) entry.unlockedItem else ItemStack(Material.ENDER_EYE).toItem(),
                name = entry.unlockedName,
                lore = entry.unlockedLore
            )
            else -> ItemTemplate(
                item = if (!entry.lockedItem.isEffectivelyEmpty()) entry.lockedItem else ItemStack(Material.GRAY_DYE).toItem(),
                name = entry.lockedName,
                lore = entry.lockedLore
            )
        }
        val barrierMaterialStr = menuConfig.defaultBarrierMaterial
        val barrierMaterial = Material.getMaterial(barrierMaterialStr) ?: Material.BARRIER
        return template.buildItem(player, barrierMaterial)
    }

    fun handleInteraction(slot: Int) {
        if (slot == backSlot && menuConfig.backButton.enabled) {
            if (initialCategory != null) {
                // Return to category selection
                player.openInventory(PrologueReplayInventory(player).inventory)
            } else {
                // Return to quest codex
                player.openInventory(QuestCategoryMainInventory(player).inventory)
            }
            return
        }

        val category = slotToCategory[slot]
        if (category != null) {
            player.openInventory(PrologueReplayInventory(player, category).inventory)
            return
        }

        val replay = slotToReplay[slot]
        if (replay != null) {
            val unlocked = replay.unlockCriteria.isEmpty() || replay.unlockCriteria.matches(player)
            if (unlocked) {
                if (replay.cinematic.isSet) {
                    player.closeInventory()
                    sessionManager.triggerActions(
                        player,
                        context(),
                        listOf(com.typewritermc.engine.paper.entry.entries.EntryTrigger(replay.cinematic))
                    )
                }
            } else {
                player.sendMessage("<red>This cinematic replay is still locked.</red>".asMini())
            }
        }
    }

    override fun getInventory(): Inventory = inventory
}
