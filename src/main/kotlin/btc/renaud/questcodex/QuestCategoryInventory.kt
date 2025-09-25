package btc.renaud.questcodex

import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.limitLineLength
import com.typewritermc.engine.paper.utils.server
import com.typewritermc.engine.paper.utils.splitComponents
import com.typewritermc.engine.paper.entry.descendants
import com.typewritermc.engine.paper.entry.entries.LinesEntry
import com.typewritermc.core.entries.ref
import com.typewritermc.quest.ObjectiveEntry
import com.typewritermc.quest.QuestEntry
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.questShowingObjectives
import com.typewritermc.quest.isQuestTracked
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

fun Inventory.fillWith(player: Player, template: ItemTemplate) {
    val fillItem = template.buildItem(player, Material.GRAY_STAINED_GLASS_PANE)
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

    private val menuConfig = QuestCodexConfig.questMenu
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
        val inventorySize = inventory.size
        val defaultSlots = if (category.questSlots.isNotEmpty()) {
            category.questSlots.filter { it in 0 until inventorySize }
        } else {
            defaultQuestSlots(category.rows, menuConfig.questsPerRow)
        }
        questSlots = defaultSlots.ifEmpty { defaultQuestSlots(category.rows, menuConfig.questsPerRow) }

        previousSlot = menuConfig.buttons.previous.resolveSlot(category.rows, inventorySize)
        nextSlot = menuConfig.buttons.next.resolveSlot(category.rows, inventorySize)
        sortSlot = menuConfig.buttons.sort.resolveSlot(category.rows, inventorySize)
        backSlot = menuConfig.buttons.back.resolveSlot(category.rows, inventorySize)

        maxQuestsPerPage = questSlots.size

        loadPage(currentPage)
    }

    fun loadPage(page: Int) {
        currentPage = page
        inventory.clear()
        slotToQuest.clear()

        val availableQuests = if (category.hideLockedQuests) {
            quests.filter { it.questStatus(player) != QuestStatus.INACTIVE }
        } else {
            quests
        }
        val filteredQuests = when (sort) {
            SortOption.ALL -> availableQuests
            SortOption.COMPLETED -> availableQuests.filter { it.questStatus(player) == QuestStatus.COMPLETED }
            SortOption.IN_PROGRESS -> availableQuests.filter { it.questStatus(player) == QuestStatus.ACTIVE }
            SortOption.NOT_STARTED -> availableQuests.filter { it.questStatus(player) == QuestStatus.INACTIVE }
        }
        val insertionOrder = category.quests.mapIndexedNotNull { index, ref ->
            ref.get()?.id?.let { it to index }
        }.toMap()
        val sortedQuests = filteredQuests.sortedWith(
            compareBy<QuestEntry>(
                { category.questOrders[it.id] ?: Int.MAX_VALUE },
                { insertionOrder[it.id] ?: Int.MAX_VALUE },
                { it.displayName.get(player).parsePlaceholders(player) },
            )
        )
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
                QuestStatus.COMPLETED -> menuConfig.questButtons.completed.lore
                QuestStatus.INACTIVE -> {
                    val loreLines = mutableListOf<String>()
                    loreLines.addAll(menuConfig.questButtons.notStarted.lore)
                    category.restrictions[quest.id]?.let { extra ->
                        if (extra.isNotEmpty()) {
                            if (menuConfig.questButtons.notStarted.lore.isNotEmpty()) loreLines.add("")
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
                    loreLines.addAll(menuConfig.questButtons.inProgress.lore)
                    if (player isQuestTracked ref) {
                        loreLines.addAll(menuConfig.questButtons.untrackLore)
                    } else {
                        loreLines.addAll(menuConfig.questButtons.trackLore)
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

            val template = when (status) {
                QuestStatus.COMPLETED -> menuConfig.questButtons.completed
                QuestStatus.ACTIVE -> menuConfig.questButtons.inProgress
                QuestStatus.INACTIVE -> menuConfig.questButtons.notStarted
            }

            val overrideItem = category.questItems[quest.id]?.itemFor(status)?.takeIf { it != Item.Empty }
            val baseStack: ItemStack = overrideItem?.build(player) ?: template.baseItem(player, Material.BARRIER)
            val questButton = baseStack.apply {
                itemMeta = itemMeta.apply {
                    displayName(quest.displayName.get(player).parsePlaceholders(player).asMiniWithoutItalic())
                    lore(lore)
                }
            }

            inventory.setItem(slot, questButton)
            slotToQuest[slot] = quest
        }

        // Fill remaining quest slots with a dedicated placeholder
        for (slot in questSlots.drop(questsToDisplay.size)) {
            inventory.setItem(slot, menuConfig.emptyQuest.buildItem(player, Material.GRAY_STAINED_GLASS_PANE))
        }

        inventory.fillWith(player, menuConfig.fill)
        setupButtons()
    }

    private fun setupButtons() {
        val maxPage = ((filteredQuestsCount - 1) / maxQuestsPerPage).coerceAtLeast(0)

        if (currentPage > 0) {
            inventory.setItem(previousSlot, menuConfig.buttons.previous.toItemTemplate().buildItem(player, Material.BARRIER))
        }

        if (currentPage < maxPage) {
            inventory.setItem(nextSlot, menuConfig.buttons.next.toItemTemplate().buildItem(player, Material.BARRIER))
        }

        val sortLore = mutableListOf<Component>()
        sortLore.add("".asMiniWithoutItalic())
        listOf(
            SortOption.ALL to menuConfig.buttons.sort.allName,
            SortOption.COMPLETED to menuConfig.buttons.sort.completedName,
            SortOption.IN_PROGRESS to menuConfig.buttons.sort.inProgressName,
            SortOption.NOT_STARTED to menuConfig.buttons.sort.notStartedName
        ).map { (option, name) ->
            val prefix = if (sort == option) menuConfig.buttons.sort.selectedPrefix else ""
            val format = if (sort == option) menuConfig.buttons.sort.selectedFormat else menuConfig.buttons.sort.unselectedFormat
            format.replace("{prefix}", prefix).replace("{name}", name)
                .parsePlaceholders(player).asMiniWithoutItalic()
        }.forEach { sortLore.add(it) }

        val sortTemplate = menuConfig.buttons.sort
        val sortButton = sortTemplate.toItemTemplate().buildItem(
            player,
            Material.BARRIER,
            loreOverride = sortLore,
        )
        inventory.setItem(sortSlot, sortButton)

        inventory.setItem(backSlot, menuConfig.buttons.back.toItemTemplate().buildItem(player, Material.BARRIER))
    }

    override fun getInventory(): Inventory = inventory

    fun questForSlot(slot: Int): QuestEntry? = slotToQuest[slot]

    enum class SortOption {
        ALL, COMPLETED, IN_PROGRESS, NOT_STARTED;
        fun next(): SortOption = values()[(ordinal + 1) % values().size]
    }
}

private fun defaultQuestSlots(rows: Int, questsPerRow: Int): List<Int> {
    val questRows = (rows - 2).coerceAtLeast(1)
    val perRow = questsPerRow.coerceIn(1, 9)
    return buildList {
        for (row in 0 until questRows) {
            val start = row * 9
            for (column in 0 until perRow) {
                add(start + column)
            }
        }
    }
}