package btc.renaud.questcodex

import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.snippets.snippet
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.limitLineLength
import com.typewritermc.engine.paper.utils.splitComponents
import com.typewritermc.engine.paper.utils.server
import com.typewritermc.engine.paper.entry.descendants
import com.typewritermc.engine.paper.entry.entries.LinesEntry
import com.typewritermc.core.entries.ref
import com.typewritermc.quest.ObjectiveEntry
import com.typewritermc.quest.QuestEntry
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.questShowingObjectives
import com.typewritermc.quest.isQuestTracked
import btc.renaud.questcodex.setModelData
import btc.renaud.questcodex.asMiniWithoutItalic
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

fun Inventory.fillWith(player: Player) {
    val fillItem = ItemStack(
        Material.getMaterial(codexMenuFillMaterial) ?: Material.GRAY_STAINED_GLASS_PANE
    ).apply {
        itemMeta = itemMeta.apply {
            displayName(codexMenuFillName.parsePlaceholders(player).asMiniWithoutItalic())
            lore(codexMenuFillLore.map { it.parsePlaceholders(player).asMiniWithoutItalic() })
            if (codexMenuFillCMD != 0) setModelData(codexMenuFillCMD)
        }
    }
    for (i in 0 until size) {
        if (getItem(i) == null) {
            setItem(i, fillItem)
        }
    }
}

/**
 * Inventory representing a single quest category. Quests are paginated and can
 * be sorted based on the player's current status.
 */
class QuestCategoryInventory(
    val player: Player,
    val category: QuestCategory,
    var sort: SortOption = SortOption.ALL,
) : InventoryHolder {

    private val inventory: Inventory = server.createInventory(
        this,
        category.rows * 9,
        category.title.parsePlaceholders(player).asMini()
    )
    private val quests: List<QuestEntry> = category.quests.mapNotNull { it.get() }
    private val slotToQuest: MutableMap<Int, QuestEntry> = mutableMapOf()
    private val questSlots: List<Int>
    val previousSlot: Int
    val nextSlot: Int
    val sortSlot: Int
    val backSlot: Int
    val maxQuestsPerPage: Int
    var filteredQuestsCount: Int = 0
    var currentPage = 0

    init {
        val questRows = (category.rows - 2).coerceAtLeast(1)
        val bottomRowStart = (category.rows - 1) * 9
        val questsPerRow = codexMenuQuestsPerRow.coerceIn(1, 9)
        questSlots = buildList {
            for (row in 0 until questRows) {
                val start = row * 9
                addAll(start until start + questsPerRow)
            }
        }

        previousSlot = bottomRowStart + codexButtonPreviousColumn.coerceIn(0, 8)
        nextSlot = bottomRowStart + codexButtonNextColumn.coerceIn(0, 8)
        sortSlot = bottomRowStart + codexButtonSortColumn.coerceIn(0, 8)
        backSlot = bottomRowStart + codexButtonBackColumn.coerceIn(0, 8)

        maxQuestsPerPage = questSlots.size

        loadPage(currentPage)
    }

    fun loadPage(page: Int) {
        currentPage = page
        inventory.clear()
        slotToQuest.clear()

        val filteredQuests = when (sort) {
            SortOption.ALL -> quests
            SortOption.COMPLETED -> quests.filter { it.questStatus(player) == QuestStatus.COMPLETED }
            SortOption.IN_PROGRESS -> quests.filter { it.questStatus(player) == QuestStatus.ACTIVE }
            SortOption.NOT_STARTED -> quests.filter { it.questStatus(player) == QuestStatus.INACTIVE }
        }
        val sortedQuests = filteredQuests.sortedBy { it.displayName.get(player).parsePlaceholders(player) }
        filteredQuestsCount = filteredQuests.size

        val startIndex = page * maxQuestsPerPage
        val endIndex = (startIndex + maxQuestsPerPage).coerceAtMost(sortedQuests.size)
        val questsToDisplay = sortedQuests.subList(startIndex, endIndex)

        for ((quest, slot) in questsToDisplay.zip(questSlots)) {
            val ref = quest.ref()
            val status = quest.questStatus(player)
            val objectives: List<ObjectiveEntry> = player.questShowingObjectives(ref).toList()
            val description = quest.children.descendants(LinesEntry::class).mapNotNull { it.get() }

            // Build description lore from LinesEntry
            val descriptionLore = mutableListOf<String>()
            description.forEach { linesEntry ->
                val lines = linesEntry.lines(player)
                if (lines.isNotBlank()) {
                    if (descriptionLore.isEmpty()) {
                        descriptionLore.add("")
                    }
                    descriptionLore.addAll(lines.split("\n"))
                }
            }

            // Add empty line separator if we have description
            if (descriptionLore.isNotEmpty()) {
                descriptionLore.add("")
            }
            
            // Build status-specific lore
            val statusLore = when (status) {
                QuestStatus.COMPLETED -> codexButtonQuestCompletedLore
                QuestStatus.INACTIVE -> {
                    val loreLines = mutableListOf<String>()
                    loreLines.addAll(codexButtonQuestNotStartedLore)
                    category.restrictions[quest.id]?.let { extra ->
                        if (extra.isNotEmpty()) {
                            if (codexButtonQuestNotStartedLore.isNotEmpty()) loreLines.add("")
                            loreLines.addAll(extra)
                        }
                    }
                    loreLines
                }
                QuestStatus.ACTIVE -> {
                    val loreLines = mutableListOf<String>()
                    if (objectives.isNotEmpty()) {
                        if (descriptionLore.isEmpty()) {
                            loreLines.add("")
                        }
                        loreLines.addAll(objectives.map { it.display(player) })
                    }
                    loreLines.addAll(codexButtonQuestInProgressLore)
                    if (player isQuestTracked ref) {
                        loreLines.addAll(codexButtonQuestUntrackLore)
                    } else {
                        loreLines.addAll(codexButtonQuestTrackLore)
                    }
                    loreLines
                }
            }.flatMap { it.split("\n") }

            val rawLoreLines = descriptionLore + statusLore

            val lore = mutableListOf<net.kyori.adventure.text.Component>()
            for (line in rawLoreLines) {
                val components = line.parsePlaceholders(player)
                    .limitLineLength()
                    .splitComponents()
                    .map { component: net.kyori.adventure.text.Component ->
                        component.decoration(TextDecoration.ITALIC, false)
                    }
                lore += components
            }

            val materialKey = when (status) {
                QuestStatus.COMPLETED -> codexButtonQuestMaterialCompleted
                QuestStatus.ACTIVE -> codexButtonQuestMaterialInProgress
                QuestStatus.INACTIVE -> codexButtonQuestMaterialNotStarted
            }

            val cmd = when (status) {
                QuestStatus.COMPLETED -> codexButtonQuestCMDCompleted
                QuestStatus.ACTIVE -> codexButtonQuestCMDInProgress
                QuestStatus.INACTIVE -> codexButtonQuestCMDNotStarted
            }

            val questButton = ItemStack(Material.getMaterial(materialKey.uppercase()) ?: Material.BARRIER).apply {
                itemMeta = itemMeta.apply {
                    displayName(quest.displayName.get(player).parsePlaceholders(player).asMiniWithoutItalic())
                    lore(lore)
                    setModelData(cmd)
                }
            }

            inventory.setItem(slot, questButton)
            slotToQuest[slot] = quest
        }

        // Fill remaining quest slots with a dedicated placeholder
        for (slot in questSlots.drop(questsToDisplay.size)) {
            val emptyItem = ItemStack(Material.getMaterial(codexMenuEmptyQuestMaterial) ?: Material.GRAY_STAINED_GLASS_PANE).apply {
                itemMeta = itemMeta.apply {
                    displayName(codexMenuEmptyQuestName.parsePlaceholders(player).asMiniWithoutItalic())
                    lore(codexMenuEmptyQuestLore.flatMap { it.split("\n") }.map { it.parsePlaceholders(player).asMiniWithoutItalic() })
                    setModelData(codexMenuEmptyQuestCMD)
                }
            }
            inventory.setItem(slot, emptyItem)
        }

        inventory.fillWith(player)
        setupButtons()
    }

    private fun setupButtons() {
        val maxPage = ((filteredQuestsCount - 1) / maxQuestsPerPage).coerceAtLeast(0)

        if (currentPage > 0) {
            val previousButton = ItemStack(Material.getMaterial(codexButtonPreviousMaterial) ?: Material.BARRIER).apply {
                itemMeta = itemMeta.apply {
                    displayName(codexButtonPreviousName.parsePlaceholders(player).asMiniWithoutItalic())
                    lore(codexButtonPreviousLore.flatMap { it.split("\n") }.map { it.parsePlaceholders(player).asMiniWithoutItalic() })
                    setModelData(codexButtonPreviousCMD)
                }
            }
            inventory.setItem(previousSlot, previousButton)
        }

        if (currentPage < maxPage) {
            val nextButton = ItemStack(Material.getMaterial(codexButtonNextMaterial) ?: Material.BARRIER).apply {
                itemMeta = itemMeta.apply {
                    displayName(codexButtonNextName.parsePlaceholders(player).asMiniWithoutItalic())
                    lore(codexButtonNextLore.flatMap { it.split("\n") }.map { it.parsePlaceholders(player).asMiniWithoutItalic() })
                    setModelData(codexButtonNextCMD)
                }
            }
            inventory.setItem(nextSlot, nextButton)
        }

        val sortLore = mutableListOf<Component>()
        sortLore.add("".asMiniWithoutItalic())
        listOf(
            SortOption.ALL to codexButtonSortAllName,
            SortOption.COMPLETED to codexButtonSortCompletedName,
            SortOption.IN_PROGRESS to codexButtonSortInProgressName,
            SortOption.NOT_STARTED to codexButtonSortNotStartedName
        ).map { (option, name) ->
            val prefix = if (sort == option) codexButtonSortSelectedPrefix else ""
            val format = if (sort == option) codexButtonSortSelectedFormat else codexButtonSortUnselectedFormat
            format.replace("{prefix}", prefix).replace("{name}", name)
                .parsePlaceholders(player).asMiniWithoutItalic()
        }.forEach { sortLore.add(it) }

        val sortButton = ItemStack(Material.getMaterial(codexButtonSortTitleMaterial) ?: Material.BARRIER).apply {
            itemMeta = itemMeta.apply {
                displayName(codexButtonSortTitleName.parsePlaceholders(player).asMiniWithoutItalic())
                lore(sortLore)
                setModelData(codexButtonSortTitleCMD)
            }
        }
        inventory.setItem(sortSlot, sortButton)

        val backButton = ItemStack(Material.getMaterial(codexButtonBackMaterial) ?: Material.BARRIER).apply {
            itemMeta = itemMeta.apply {
                displayName(codexButtonBackName.parsePlaceholders(player).asMiniWithoutItalic())
                lore(codexButtonBackLore.flatMap { it.split("\n") }.map { it.parsePlaceholders(player).asMiniWithoutItalic() })
                setModelData(codexButtonBackCMD)
            }
        }
        inventory.setItem(backSlot, backButton)
    }

    override fun getInventory(): Inventory = inventory

    fun questForSlot(slot: Int): QuestEntry? = slotToQuest[slot]

    enum class SortOption {
        ALL, COMPLETED, IN_PROGRESS, NOT_STARTED;
        fun next(): SortOption = values()[(ordinal + 1) % values().size]
    }
}

// ----- Snippet configuration -----
val codexButtonPreviousName: String by snippet("codex.menu.button.previous.name", "Previous Page")
val codexButtonPreviousMaterial: String by snippet("codex.menu.button.previous.material", "ARROW")
val codexButtonPreviousLore: List<String> by snippet(
    "codex.menu.button.previous.lore",
    listOf("<gray>Go to previous page</gray>")
)
val codexButtonPreviousColumn: Int by snippet("codex.menu.button.previous.column", 0)
val codexButtonPreviousCMD: Int by snippet("codex.menu.button.previous.custom-model-data", 0)

val codexButtonNextName: String by snippet("codex.menu.button.next.name", "Next Page")
val codexButtonNextMaterial: String by snippet("codex.menu.button.next.material", "ARROW")
val codexButtonNextLore: List<String> by snippet(
    "codex.menu.button.next.lore",
    listOf("<gray>Go to next page</gray>")
)
val codexButtonNextColumn: Int by snippet("codex.menu.button.next.column", 8)
val codexButtonNextCMD: Int by snippet("codex.menu.button.next.custom-model-data", 0)

val codexButtonBackName: String by snippet("codex.menu.button.back.name", "Back")
val codexButtonBackMaterial: String by snippet("codex.menu.button.back.material", "BARRIER")
val codexButtonBackLore: List<String> by snippet(
    "codex.menu.button.back.lore",
    listOf("<gray>Return to categories</gray>")
)
val codexButtonBackColumn: Int by snippet("codex.menu.button.back.column", 7)
val codexButtonBackCMD: Int by snippet("codex.menu.button.back.custom-model-data", 0)

val codexButtonQuestCompletedLore: List<String> by snippet(
    "codex.menu.button.quest.completed.lore",
    listOf("<green>✓ Completed</green>")
)
val codexButtonQuestNotStartedLore: List<String> by snippet(
    "codex.menu.button.quest.not-started.lore",
    listOf("<gray>○ Not Started</gray>")
)
val codexButtonQuestInProgressLore: List<String> by snippet(
    "codex.menu.button.quest.in-progress.lore",
    listOf("<yellow>⚡ In Progress</yellow>")
)
val codexButtonQuestTrackLore: List<String> by snippet(
    "codex.menu.button.quest.track.lore",
    listOf("<gray>Click to track</gray>")
)
val codexButtonQuestUntrackLore: List<String> by snippet(
    "codex.menu.button.quest.untrack.lore",
    listOf("<gray>Click to untrack</gray>")
)
val codexButtonQuestMaterialCompleted: String by snippet(
    "codex.menu.button.quest.completed.material",
    "ENCHANTED_BOOK"
)
val codexButtonQuestMaterialInProgress: String by snippet(
    "codex.menu.button.quest.in-progress.material",
    "WRITABLE_BOOK"
)
val codexButtonQuestMaterialNotStarted: String by snippet(
    "codex.menu.button.quest.not-started.material",
    "BOOK"
)
val codexButtonQuestCMDCompleted: Int by snippet("codex.menu.button.quest.completed.custom-model-data", 0)
val codexButtonQuestCMDInProgress: Int by snippet("codex.menu.button.quest.in-progress.custom-model-data", 0)
val codexButtonQuestCMDNotStarted: Int by snippet("codex.menu.button.quest.not-started.custom-model-data", 0)
val codexButtonSortColumn: Int by snippet("codex.menu.button.sort.column", 4)
val codexButtonSortTitleName: String by snippet("codex.menu.button.sort.title.name", "Sort")
val codexButtonSortTitleMaterial: String by snippet("codex.menu.button.sort.title.material", "COMPARATOR")
val codexButtonSortTitleCMD: Int by snippet("codex.menu.button.sort.title.custom-model-data", 0)
val codexButtonSortSelectedPrefix: String by snippet("codex.menu.button.sort.selected-prefix", "\u2192 ")
val codexButtonSortSelectedFormat: String by snippet(
    "codex.menu.button.sort.selected-format",
    "<green>{prefix}{name}</green>"
)
val codexButtonSortUnselectedFormat: String by snippet(
    "codex.menu.button.sort.unselected-format",
    "<white>{name}</white>"
)
val codexButtonSortAllName: String by snippet("codex.menu.button.sort.all.name", "All Quests")
val codexButtonSortCompletedName: String by snippet("codex.menu.button.sort.completed.name", "Completed")
val codexButtonSortInProgressName: String by snippet("codex.menu.button.sort.in-progress.name", "In Progress")
val codexButtonSortNotStartedName: String by snippet("codex.menu.button.sort.not-started.name", "Not Started")

val codexMenuQuestsPerRow: Int by snippet(
    "codex.menu.quests-per-row",
    9,
    "Number of quests per row in the quest menu."
)
val codexMenuFillMaterial: String by snippet("codex.menu.fill.material", "GRAY_STAINED_GLASS_PANE")
val codexMenuFillName: String by snippet("codex.menu.fill.name", "")
val codexMenuFillLore: List<String> by snippet("codex.menu.fill.lore", emptyList<String>())
val codexMenuFillCMD: Int by snippet("codex.menu.fill.custom-model-data", 0)

val codexMenuEmptyQuestMaterial: String by snippet("codex.menu.empty-quest.material", codexMenuFillMaterial)
val codexMenuEmptyQuestName: String by snippet("codex.menu.empty-quest.name", "")
val codexMenuEmptyQuestLore: List<String> by snippet(
    "codex.menu.empty-quest.lore",
    listOf("<gray>No quests available</gray>")
)
val codexMenuEmptyQuestCMD: Int by snippet("codex.menu.empty-quest.custom-model-data", 0)