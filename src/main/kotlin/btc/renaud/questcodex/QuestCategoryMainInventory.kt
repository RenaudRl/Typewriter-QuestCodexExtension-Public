package btc.renaud.questcodex

import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.snippets.snippet
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.server
import btc.renaud.questcodex.setModelData
import btc.renaud.questcodex.asMiniWithoutItalic
import net.kyori.adventure.text.format.TextDecoration
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.quest.QuestStatus
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class QuestCategoryMainInventory(
    private val player: Player,
    private val parent: QuestCategory? = null,
    var currentPage: Int = 0,
) : InventoryHolder {
    private val categories: List<QuestCategory> =
        (parent?.subCategories ?: QuestCategoryRegistry.roots())
            .sortedBy { it.title }
            .filter { !(it.hideWhenLocked && it.categoryStatus(player) == CategoryStatus.BLOCKED) }
    private val isSubMenu: Boolean = parent != null
    private val rows: Int = if (isSubMenu) codexSubMenuRows.coerceIn(2, 6) else codexMainMenuRows.coerceIn(2, 6)
    private val categoriesPerRow: Int =
        if (isSubMenu) codexSubMenuCategoriesPerRow.coerceIn(1, 9) else codexMainMenuCategoriesPerRow.coerceIn(1, 9)
    private val size: Int = rows * 9
    private val categoriesPerPage = (rows - 1) * categoriesPerRow
    private val previousSlot =
        (rows - 1) * 9 + (if (isSubMenu) codexSubButtonPreviousColumn else codexMainButtonPreviousColumn).coerceIn(0, 8)
    private val nextSlot =
        (rows - 1) * 9 + (if (isSubMenu) codexSubButtonNextColumn else codexMainButtonNextColumn).coerceIn(0, 8)
    private val infoSlot =
        (rows - 1) * 9 + (if (isSubMenu) codexSubButtonInfoColumn else codexMainButtonInfoColumn).coerceIn(0, 8)
    private val backSlot =
        (rows - 1) * 9 + (if (isSubMenu) codexSubButtonBackColumn else codexMainButtonBackColumn).coerceIn(0, 8)
    private val inventory: Inventory = server.createInventory(
        this,
        size,
        (if (isSubMenu) {
            codexSubMenuTitle.replace("<category>", parent!!.title.parsePlaceholders(player))
        } else {
            codexMainMenuTitle
        }).parsePlaceholders(player).asMini(),
    )
    val slots: MutableMap<Int, QuestCategory> = mutableMapOf()
    private val maxPage: Int = if (categories.isEmpty()) 0 else (categories.size - 1) / categoriesPerPage

    init {
        loadPage(currentPage)
    }

    fun loadPage(page: Int) {
        currentPage = page.coerceIn(0, maxPage)
        inventory.clear()
        slots.clear()

        val startIndex = currentPage * categoriesPerPage
        val endIndex = (startIndex + categoriesPerPage).coerceAtMost(categories.size)
        val categoriesToDisplay = categories.subList(startIndex, endIndex)

        categoriesToDisplay.forEachIndexed { index, category ->
            val baseItem = if (category.item != Item.Empty) {
                category.item.build(player)
            } else {
                ItemStack(Material.getMaterial(codexMainCategoryMaterial) ?: Material.BOOK)
            }
            val item = baseItem.apply {
                itemMeta = itemMeta.apply {
                    val styleString = if (category.nameColor.isNotBlank()) {
                        category.nameColor
                    } else {
                        codexMainCategoryNameColor
                    }
                    val styleComponent = styleString.parsePlaceholders(player).asMini()
                    var nameComponent = category.title.parsePlaceholders(player).asMiniWithoutItalic()
                    nameComponent = nameComponent.style(styleComponent.style())
                    if (codexMainCategoryNameBold) {
                        nameComponent = nameComponent.decoration(TextDecoration.BOLD, true)
                    }
                    displayName(nameComponent)

                    val quests = category.allQuests()
                    val total = quests.size
                    val completed = quests.count { it.questStatus(player) == QuestStatus.COMPLETED }
                    val status = category.categoryStatus(player)

                    val loreLines = mutableListOf<String>()
                    codexMainCategoryLoreQuestCount.forEach {
                        loreLines += it
                            .replace("<completed>", completed.toString())
                            .replace("<total>", total.toString())
                    }
                    if (status != CategoryStatus.BLOCKED) {
                        codexMainCategoryLore.forEach { loreLines += it }
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
                    if (codexMainCategoryCMD != 0) {
                        setModelData(codexMainCategoryCMD)
                    }
                }
            }
            val row = index / categoriesPerRow
            val col = index % categoriesPerRow
            val slot = row * 9 + col
            inventory.setItem(slot, item)
            slots[slot] = category
        }

        val fillMaterial = if (isSubMenu) codexSubMenuFillMaterial else codexMainMenuFillMaterial
        val fillName = if (isSubMenu) codexSubMenuFillName else codexMainMenuFillName
        val fillLore = if (isSubMenu) codexSubMenuFillLore else codexMainMenuFillLore
        val fillCMD = if (isSubMenu) codexSubMenuFillCMD else codexMainMenuFillCMD
        val fillItem = ItemStack(Material.getMaterial(fillMaterial) ?: Material.GRAY_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta.apply {
                displayName(fillName.parsePlaceholders(player).asMiniWithoutItalic())
                lore(
                    fillLore.flatMap { it.split("\n") }
                        .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
                )
                if (fillCMD != 0) setModelData(fillCMD)
            }
        }
        for (i in 0 until size) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, fillItem)
            }
        }

        // Previous button
        if (currentPage > 0) {
            val prevMaterial = if (isSubMenu) codexSubButtonPreviousMaterial else codexMainButtonPreviousMaterial
            val prevName = if (isSubMenu) codexSubButtonPreviousName else codexMainButtonPreviousName
            val prevLore = if (isSubMenu) codexSubButtonPreviousLore else codexMainButtonPreviousLore
            val prevCMD = if (isSubMenu) codexSubButtonPreviousCMD else codexMainButtonPreviousCMD
            val prev = ItemStack(Material.getMaterial(prevMaterial) ?: Material.ARROW).apply {
                itemMeta = itemMeta.apply {
                    displayName(prevName.parsePlaceholders(player).asMiniWithoutItalic())
                    lore(
                        prevLore.flatMap { it.split("\n") }
                            .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
                    )
                    if (prevCMD != 0) setModelData(prevCMD)
                }
            }
            inventory.setItem(previousSlot, prev)
        }

        // Next button
        if (currentPage < maxPage) {
            val nextMaterial = if (isSubMenu) codexSubButtonNextMaterial else codexMainButtonNextMaterial
            val nextName = if (isSubMenu) codexSubButtonNextName else codexMainButtonNextName
            val nextLore = if (isSubMenu) codexSubButtonNextLore else codexMainButtonNextLore
            val nextCMD = if (isSubMenu) codexSubButtonNextCMD else codexMainButtonNextCMD
            val next = ItemStack(Material.getMaterial(nextMaterial) ?: Material.ARROW).apply {
                itemMeta = itemMeta.apply {
                    displayName(nextName.parsePlaceholders(player).asMiniWithoutItalic())
                    lore(
                        nextLore.flatMap { it.split("\n") }
                            .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
                    )
                    if (nextCMD != 0) setModelData(nextCMD)
                }
            }
            inventory.setItem(nextSlot, next)
        }

        // Info button
        val infoMaterial = if (isSubMenu) codexSubButtonInfoMaterial else codexMainButtonInfoMaterial
        val infoName = if (isSubMenu) codexSubButtonInfoName else codexMainButtonInfoName
        val infoLore = if (isSubMenu) codexSubButtonInfoLore else codexMainButtonInfoLore
        val infoCMD = if (isSubMenu) codexSubButtonInfoCMD else codexMainButtonInfoCMD
        val info = ItemStack(Material.getMaterial(infoMaterial) ?: Material.PAPER).apply {
            itemMeta = itemMeta.apply {
                displayName(infoName.parsePlaceholders(player).asMiniWithoutItalic())
                lore(
                    infoLore.flatMap { it.split("\n") }
                        .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
                )
                if (infoCMD != 0) setModelData(infoCMD)
            }
        }
        inventory.setItem(infoSlot, info)

        // Back button for sub-category menus
        if (parent != null) {
            val backMaterial = if (isSubMenu) codexSubButtonBackMaterial else codexMainButtonBackMaterial
            val backName = if (isSubMenu) codexSubButtonBackName else codexMainButtonBackName
            val backLore = if (isSubMenu) codexSubButtonBackLore else codexMainButtonBackLore
            val backCMD = if (isSubMenu) codexSubButtonBackCMD else codexMainButtonBackCMD
            val back = ItemStack(Material.getMaterial(backMaterial) ?: Material.BARRIER).apply {
                itemMeta = itemMeta.apply {
                    displayName(backName.parsePlaceholders(player).asMiniWithoutItalic())
                    lore(
                        backLore.flatMap { it.split("\n") }
                            .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
                    )
                    if (backCMD != 0) setModelData(backCMD)
                }
            }
            inventory.setItem(backSlot, back)
        }
    }

    override fun getInventory(): Inventory = inventory

    fun maxPage(): Int = maxPage
    fun previousSlot(): Int = previousSlot
    fun nextSlot(): Int = nextSlot
    fun infoSlot(): Int = infoSlot
    fun backSlot(): Int = backSlot
    fun parent(): QuestCategory? = parent
}

val codexMainMenuTitle: String by snippet("codex.menu.main.title", "Quest Categories")
val codexMainMenuRows: Int by snippet("codex.menu.main.rows", 6, "The number of rows in the categories menu.")
val codexMainMenuCategoriesPerRow: Int by snippet(
    "codex.menu.main.categories-per-row",
    9,
    "Number of categories per row in the categories menu."
)
val codexMainCategoryMaterial: String by snippet("codex.menu.main.category.material", "BOOK")
val codexMainCategoryNameColor: String by snippet("codex.menu.main.category.name-color", "<yellow>")
val codexMainCategoryNameBold: Boolean by snippet("codex.menu.main.category.name-bold", false)
val codexMainCategoryCMD: Int by snippet("codex.menu.main.category.custom-model-data", 0)
val codexMainCategoryLoreQuestCount: List<String> by snippet(
    "codex.menu.main.category.lore-quest-count",
    listOf("<gray><completed>/<total> quests</gray>")
)
val codexMainCategoryLore: List<String> by snippet(
    "codex.menu.main.category.lore",
    listOf("<gray>Click to view quests</gray>")
)
val codexMainMenuFillMaterial: String by snippet("codex.menu.main.fill.material", "GRAY_STAINED_GLASS_PANE")
val codexMainMenuFillName: String by snippet("codex.menu.main.fill.name", "")
val codexMainMenuFillLore: List<String> by snippet("codex.menu.main.fill.lore", emptyList<String>())
val codexMainMenuFillCMD: Int by snippet("codex.menu.main.fill.custom-model-data", 0)

val codexMainButtonPreviousName: String by snippet("codex.menu.main.button.previous.name", "Previous Page")
val codexMainButtonPreviousMaterial: String by snippet("codex.menu.main.button.previous.material", "ARROW")
val codexMainButtonPreviousLore: List<String> by snippet(
    "codex.menu.main.button.previous.lore",
    listOf("<gray>Go to previous page</gray>")
)
val codexMainButtonPreviousColumn: Int by snippet("codex.menu.main.button.previous.column", 0)
val codexMainButtonPreviousCMD: Int by snippet("codex.menu.main.button.previous.custom-model-data", 0)

val codexMainButtonNextName: String by snippet("codex.menu.main.button.next.name", "Next Page")
val codexMainButtonNextMaterial: String by snippet("codex.menu.main.button.next.material", "ARROW")
val codexMainButtonNextLore: List<String> by snippet(
    "codex.menu.main.button.next.lore",
    listOf("<gray>Go to next page</gray>")
)
val codexMainButtonNextColumn: Int by snippet("codex.menu.main.button.next.column", 8)
val codexMainButtonNextCMD: Int by snippet("codex.menu.main.button.next.custom-model-data", 0)

val codexMainButtonInfoName: String by snippet("codex.menu.main.button.info.name", "Info")
val codexMainButtonInfoMaterial: String by snippet("codex.menu.main.button.info.material", "PAPER")
val codexMainButtonInfoLore: List<String> by snippet(
    "codex.menu.main.button.info.lore",
    listOf("<gray>Completed: %typewriter_total_completed%/%typewriter_total_quests%</gray>")
)
val codexMainButtonInfoColumn: Int by snippet("codex.menu.main.button.info.column", 4)
val codexMainButtonInfoCMD: Int by snippet("codex.menu.main.button.info.custom-model-data", 0)

val codexMainButtonBackName: String by snippet("codex.menu.main.button.back.name", "Back")
val codexMainButtonBackMaterial: String by snippet("codex.menu.main.button.back.material", "BARRIER")
val codexMainButtonBackLore: List<String> by snippet(
    "codex.menu.main.button.back.lore",
    listOf("<gray>Return to parent</gray>")
)
val codexMainButtonBackColumn: Int by snippet("codex.menu.main.button.back.column", 7)
val codexMainButtonBackCMD: Int by snippet("codex.menu.main.button.back.custom-model-data", 0)

// Sub-category menu configuration
val codexSubMenuTitle: String by snippet("codex.menu.sub.title", "<category>")
val codexSubMenuRows: Int by snippet("codex.menu.sub.rows", 6, "The number of rows in sub-category menus.")
val codexSubMenuCategoriesPerRow: Int by snippet(
    "codex.menu.sub.categories-per-row",
    9,
    "Number of sub-categories per row in the sub-category menu.",
)
val codexSubMenuFillMaterial: String by snippet("codex.menu.sub.fill.material", "GRAY_STAINED_GLASS_PANE")
val codexSubMenuFillName: String by snippet("codex.menu.sub.fill.name", "")
val codexSubMenuFillLore: List<String> by snippet("codex.menu.sub.fill.lore", emptyList<String>())
val codexSubMenuFillCMD: Int by snippet("codex.menu.sub.fill.custom-model-data", 0)

val codexSubButtonPreviousName: String by snippet("codex.menu.sub.button.previous.name", "Previous Page")
val codexSubButtonPreviousMaterial: String by snippet("codex.menu.sub.button.previous.material", "ARROW")
val codexSubButtonPreviousLore: List<String> by snippet(
    "codex.menu.sub.button.previous.lore",
    listOf("<gray>Go to previous page</gray>")
)
val codexSubButtonPreviousColumn: Int by snippet("codex.menu.sub.button.previous.column", 0)
val codexSubButtonPreviousCMD: Int by snippet("codex.menu.sub.button.previous.custom-model-data", 0)

val codexSubButtonNextName: String by snippet("codex.menu.sub.button.next.name", "Next Page")
val codexSubButtonNextMaterial: String by snippet("codex.menu.sub.button.next.material", "ARROW")
val codexSubButtonNextLore: List<String> by snippet(
    "codex.menu.sub.button.next.lore",
    listOf("<gray>Go to next page</gray>")
)
val codexSubButtonNextColumn: Int by snippet("codex.menu.sub.button.next.column", 8)
val codexSubButtonNextCMD: Int by snippet("codex.menu.sub.button.next.custom-model-data", 0)

val codexSubButtonInfoName: String by snippet("codex.menu.sub.button.info.name", "Info")
val codexSubButtonInfoMaterial: String by snippet("codex.menu.sub.button.info.material", "PAPER")
val codexSubButtonInfoLore: List<String> by snippet(
    "codex.menu.sub.button.info.lore",
    listOf("<gray>Completed: %typewriter_total_completed%/%typewriter_total_quests%</gray>")
)
val codexSubButtonInfoColumn: Int by snippet("codex.menu.sub.button.info.column", 4)
val codexSubButtonInfoCMD: Int by snippet("codex.menu.sub.button.info.custom-model-data", 0)

val codexSubButtonBackName: String by snippet("codex.menu.sub.button.back.name", "Back")
val codexSubButtonBackMaterial: String by snippet("codex.menu.sub.button.back.material", "BARRIER")
val codexSubButtonBackLore: List<String> by snippet(
    "codex.menu.sub.button.back.lore",
    listOf("<gray>Return to parent</gray>")
)
val codexSubButtonBackColumn: Int by snippet("codex.menu.sub.button.back.column", 7)
val codexSubButtonBackCMD: Int by snippet("codex.menu.sub.button.back.custom-model-data", 0)

