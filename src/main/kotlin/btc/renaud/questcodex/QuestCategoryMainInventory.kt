package btc.renaud.questcodex

import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.server
import com.typewritermc.engine.paper.utils.item.Item
import net.kyori.adventure.text.format.TextDecoration
import com.typewritermc.quest.QuestStatus
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import kotlin.math.min

class QuestCategoryMainInventory(
    private val player: Player,
    private val parent: QuestCategory? = null,
    var currentPage: Int = 0,
) : InventoryHolder {
    private val categories: List<QuestCategory> =
        (parent?.subCategories ?: QuestCategoryRegistry.roots())
            .sortedWith(compareBy({ if (it.order == 0) Int.MAX_VALUE else it.order }, { it.title }))
            .filter { !(it.hideWhenLocked && it.categoryStatus(player) == CategoryStatus.BLOCKED) }
    private val isSubMenu: Boolean = parent != null
    private val menuConfig = if (isSubMenu) QuestCodexConfig.subMenu else QuestCodexConfig.mainMenu
    private val rows: Int = menuConfig.rows.coerceIn(2, 6)
    private val categoriesPerRow: Int = menuConfig.categoriesPerRow.coerceIn(1, 9)
    private val size: Int = rows * 9
    private val autoSlots: List<Int> = buildList {
        val usableRows = (rows - 1).coerceAtLeast(0)
        for (row in 0 until usableRows) {
            val start = row * 9
            for (column in 0 until categoriesPerRow) {
                add(start + column)
            }
        }
    }
    private val inventory: Inventory = server.createInventory(
        this,
        size,
        (if (isSubMenu) {
            menuConfig.title.replace("<category>", parent!!.title.parsePlaceholders(player))
        } else {
            menuConfig.title
        }).parsePlaceholders(player).asMini(),
    )
    private val previousSlot: Int = menuConfig.previousButton.resolveSlot(rows, size)
    private val nextSlot: Int = menuConfig.nextButton.resolveSlot(rows, size)
    private val infoSlot: Int = menuConfig.infoButton.resolveSlot(rows, size)
    private val backSlot: Int = menuConfig.backButton?.resolveSlot(rows, size) ?: -1
    private val placementMap: Map<Int, List<CategoryPlacement>>
    private val maxPageIndex: Int

    val slots: MutableMap<Int, QuestCategory> = mutableMapOf()

    init {
        val (placements, maxPage) = computePlacements()
        placementMap = placements
        maxPageIndex = maxPage
        loadPage(currentPage)
    }

    private fun computePlacements(): Pair<Map<Int, List<CategoryPlacement>>, Int> {
        val inventorySize = size
        val explicitPlacements = categories.mapNotNull { category ->
            val slot = category.slot
            if (slot != null && slot >= 0) {
                val page = slot / inventorySize
                val slotInPage = slot % inventorySize
                if (slotInPage in 0 until inventorySize) {
                    CategoryPlacement(page, slotInPage, category)
                } else {
                    null
                }
            } else {
                null
            }
        }
        val explicitByPage = explicitPlacements.groupBy { it.page }
        val autoCategories = categories.filter { it.slot == null || it.slot!! < 0 }
        val allPlacements = explicitPlacements.toMutableList()
        var autoIndex = 0
        var page = 0
        val maxExplicitPage = explicitPlacements.maxOfOrNull { it.page } ?: -1
        while (autoIndex < autoCategories.size) {
            val usedSlots = (explicitByPage[page]?.map { it.slot } ?: emptyList()).toMutableSet()
            val availableSlots = autoSlots.filter { it in 0 until inventorySize && it !in usedSlots }
            if (availableSlots.isNotEmpty()) {
                val chunk = autoCategories.subList(autoIndex, min(autoIndex + availableSlots.size, autoCategories.size))
                chunk.forEachIndexed { index, category ->
                    allPlacements += CategoryPlacement(page, availableSlots[index], category)
                }
                autoIndex += chunk.size
            }
            if (availableSlots.isEmpty() && explicitByPage[page].isNullOrEmpty()) {
                if (page >= maxExplicitPage && autoIndex >= autoCategories.size) {
                    break
                }
            }
            page++
            if (page > 200) break
        }
        val grouped = allPlacements.groupBy { it.page }.mapValues { (_, list) ->
            list.sortedBy { it.slot }
        }
        val maxPage = grouped.keys.maxOrNull()?.coerceAtLeast(0) ?: 0
        return grouped to maxPage
    }

    fun loadPage(page: Int) {
        currentPage = page.coerceIn(0, maxPageIndex)
        inventory.clear()
        slots.clear()

        val pagePlacements = placementMap[currentPage].orEmpty()
        pagePlacements.forEach { placement ->
            val item = buildCategoryItem(placement.category)
            inventory.setItem(placement.slot, item)
            slots[placement.slot] = placement.category
        }

        inventory.fillWith(player, menuConfig.fill)

        if (currentPage > 0) {
            inventory.setItem(previousSlot, menuConfig.previousButton.toItemTemplate().buildItem(player, Material.ARROW))
        }
        if (currentPage < maxPageIndex) {
            inventory.setItem(nextSlot, menuConfig.nextButton.toItemTemplate().buildItem(player, Material.ARROW))
        }
        inventory.setItem(infoSlot, menuConfig.infoButton.toItemTemplate().buildItem(player, Material.PAPER))
        if (parent != null && menuConfig.backButton != null && backSlot in 0 until size) {
            inventory.setItem(backSlot, menuConfig.backButton.toItemTemplate().buildItem(player, Material.BARRIER))
        }
    }

    private fun buildCategoryItem(category: QuestCategory): ItemStack {
        val baseItem = when {
            category.item != Item.Empty -> category.item.build(player)
            menuConfig.categoryItem != Item.Empty -> menuConfig.categoryItem.build(player)
            else -> ItemStack(Material.BOOK)
        }
        return baseItem.apply {
            itemMeta = itemMeta.apply {
                val styleString = if (category.nameColor.isNotBlank()) category.nameColor else menuConfig.categoryNameColor
                val styleComponent = styleString.parsePlaceholders(player).asMini()
                var nameComponent = category.title.parsePlaceholders(player).asMiniWithoutItalic()
                nameComponent = nameComponent.style(styleComponent.style())
                if (menuConfig.categoryNameBold) {
                    nameComponent = nameComponent.decoration(TextDecoration.BOLD, true)
                }
                displayName(nameComponent)

                val quests = category.allQuests()
                val total = quests.size
                val completed = quests.count { it.questStatus(player) == QuestStatus.COMPLETED }
                val status = category.categoryStatus(player)

                val loreLines = mutableListOf<String>()
                menuConfig.categoryLoreQuestCount.forEach { template ->
                    loreLines += template
                        .replace("<completed>", completed.toString())
                        .replace("<total>", total.toString())
                }
                if (status != CategoryStatus.BLOCKED) {
                    menuConfig.categoryLore.forEach { loreLines += it }
                }
                when (status) {
                    CategoryStatus.BLOCKED -> loreLines += category.blockedMessage
                    CategoryStatus.IN_PROGRESS -> loreLines += category.activeMessage
                    CategoryStatus.COMPLETED -> loreLines += category.completedMessage
                }

                lore(
                    loreLines.flatMap { it.split("\n") }
                        .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
                )
            }
        }
    }

    override fun getInventory(): Inventory = inventory

    fun maxPage(): Int = maxPageIndex
    fun previousSlot(): Int = previousSlot
    fun nextSlot(): Int = nextSlot
    fun infoSlot(): Int = infoSlot
    fun backSlot(): Int = backSlot
    fun parent(): QuestCategory? = parent

    private data class CategoryPlacement(val page: Int, val slot: Int, val category: QuestCategory)
}
