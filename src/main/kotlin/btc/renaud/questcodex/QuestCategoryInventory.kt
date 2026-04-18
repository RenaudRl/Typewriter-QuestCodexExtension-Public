package btc.renaud.questcodex

import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.utils.limitLineLength
import com.typewritermc.engine.paper.utils.server
import com.typewritermc.engine.paper.utils.splitComponents
import com.typewritermc.engine.paper.entry.descendants
import com.typewritermc.engine.paper.entry.entries.LinesEntry
import com.typewritermc.quest.entries.ObjectiveEntry
import com.typewritermc.core.entries.ref
import com.typewritermc.quest.entries.QuestEntry
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.entries.questShowingObjectives
import com.typewritermc.quest.isQuestTracked
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

fun Inventory.fillWith(player: Player, template: ItemTemplate) {
    val fillMaterialStr = QuestCodexConfig.questMenu.defaultFillMaterial
    val fillMaterial = Material.getMaterial(fillMaterialStr) ?: Material.GRAY_STAINED_GLASS_PANE
    val fillItem = template.buildItem(player, fillMaterial)
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

    internal val menuConfig = QuestCodexConfig.questMenu
    private val inventory: Inventory = server.createInventory(
        this,
        category.rows * 9,
        category.title.parsePlaceholders(player).asMini()
    )
    private val quests: List<QuestEntry> = category.quests.mapNotNull { it.get() }
    private val slotToQuest: MutableMap<Int, QuestEntry> = mutableMapOf()
    private val slotToCategory: MutableMap<Int, QuestCategory> = mutableMapOf()
    private val questSlots: List<Int>
    val previousSlot: Int
    val nextSlot: Int
    val sortSlot: Int
    val backSlot: Int
    val replaySlot: Int
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
        replaySlot = QuestCodexConfig.replayMenu.replayButton.resolveSlot(category.rows, inventorySize)

        maxQuestsPerPage = questSlots.size

        loadPage(currentPage)
    }

    fun loadPage(page: Int) {
        currentPage = page
        inventory.clear()
        slotToQuest.clear()
        slotToCategory.clear()

        val questViews = quests.map { quest -> quest to quest.questStatus(player) }
            .let { pairs ->
                if (category.hideLockedQuests) {
                    pairs.filter { it.second != QuestStatus.INACTIVE }
                } else {
                    pairs
                }
            }
            .map { (quest, status) -> QuestView(quest, status) }
        val filteredViews = when (sort) {
            SortOption.ALL -> questViews
            SortOption.COMPLETED -> questViews.filter { it.status == QuestStatus.COMPLETED }
            SortOption.IN_PROGRESS -> questViews.filter { it.status == QuestStatus.ACTIVE }
            SortOption.NOT_STARTED -> questViews.filter { it.status == QuestStatus.INACTIVE }
        }
        val insertionOrder = category.quests.mapIndexedNotNull { index, ref ->
            ref.get()?.id?.let { it to index }
        }.toMap()
        val visibleViews = filteredViews.filter { view ->
            val overrides = category.questDisplays[view.quest.id]
            val stateOverride = overrides?.state(view.status)
            stateOverride?.hideQuest != true
        }
        val sortedViews = visibleViews.sortedWith(
            compareBy<QuestView>(
                { category.questOrders[it.quest.id] ?: Int.MAX_VALUE },
                { insertionOrder[it.quest.id] ?: Int.MAX_VALUE },
                { it.quest.displayName.get(player).parsePlaceholders(player) },
            )
        )
        filteredQuestsCount = visibleViews.size

        val subCategories = if (category.showCategoriesButton) {
            category.subCategories.filter { 
                !(it.hideWhenLocked && it.categoryStatus(player) == CategoryStatus.BLOCKED) 
            }
        } else emptyList()

        val allItems = buildList {
            // We only show categories on the first page, or should they be paginated too?
            // User requested to have them in the same menu. Let's include them in allItems for pagination.
            addAll(subCategories)
            addAll(sortedViews)
        }

        val startIndex = page * maxQuestsPerPage
        val endIndex = (startIndex + maxQuestsPerPage).coerceAtMost(allItems.size)
        val itemsToDisplay = allItems.subList(startIndex, endIndex)
        filteredQuestsCount = allItems.size // Use all items for pagination count

        for ((item, slot) in itemsToDisplay.zip(questSlots)) {
            if (item is QuestCategory) {
                val categoryButton = item.buildIcon(player, QuestCodexConfig.subMenu)
                inventory.setItem(slot, categoryButton)
                slotToCategory[slot] = item
                continue
            }
            
            val view = item as QuestView
            val quest = view.quest
            val ref = quest.ref()
            val status = view.status
            val displayOverride = category.questDisplays[quest.id]?.state(status)
            val objectiveOrder = quest.children.descendants(ObjectiveEntry::class).mapIndexed { index, objectiveRef ->
                objectiveRef.id to index
            }.toMap()
            val objectives: List<ObjectiveEntry> = player.questShowingObjectives(ref)
                .filter { obj -> !QuestPlusIntegration.isHidden(obj, player) }
                .sortedBy { objectiveOrder[it.id] ?: Int.MAX_VALUE }
                .toList()
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
            val customLore = displayOverride?.lore ?: emptyList()
            val statusLore = when (status) {
                QuestStatus.COMPLETED -> {
                    val loreLines = mutableListOf<String>()
                    loreLines.addAll(customLore)
                    loreLines.addAll(menuConfig.questButtons.completed.lore)
                    loreLines
                }
                QuestStatus.INACTIVE -> {
                    val loreLines = mutableListOf<String>()
                    loreLines.addAll(customLore)
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
                    if (!displayOverride?.hideObjectives.isTrue() && objectives.isNotEmpty()) {
                        if (descriptionLore.isEmpty()) {
                            loreLines.add("")
                        }
                        loreLines.addAll(objectives.map { it.display(player) }.filter { it.isNotBlank() })
                    }
                    loreLines.addAll(customLore)
                    loreLines.addAll(menuConfig.questButtons.inProgress.lore)
                    if (player isQuestTracked ref) {
                        loreLines.addAll(menuConfig.questButtons.untrackLore)
                    } else {
                        loreLines.addAll(menuConfig.questButtons.trackLore)
                    }
                    loreLines
                }
            }.flatMap { it.split("\n") }

            val additionalLore = category.questAdditionalLore[quest.id]?.forStatus(status).orEmpty()
            
            // Collect QuestPlus specific Lore if tracked
            val gpsLore = if (status == QuestStatus.ACTIVE && player isQuestTracked ref) {
                objectives.mapNotNull { QuestPlusIntegration.getGpsCodexLore(it, player) }
                    .filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val rawLoreLines = buildList {
                addAll(descriptionLore)
                addAll(statusLore)
                
                if (gpsLore.isNotEmpty()) {
                    if (isNotEmpty()) add("")
                    addAll(gpsLore)
                }

                if (additionalLore.isNotEmpty() && (descriptionLore.isNotEmpty() || statusLore.isNotEmpty() || gpsLore.isNotEmpty())) {
                    add("")
                }
                addAll(additionalLore)
            }

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

            val overrideItem = category.questItems[quest.id]?.itemFor(status)?.takeIf { !it.isEffectivelyEmpty() }
            val baseStack: ItemStack = overrideItem?.build(player) ?: template.baseItem(player, Material.BARRIER)
            val meta = baseStack.itemMeta
            if (meta != null) {
                val nameComponent = displayOverride?.name?.takeIf { it.isNotBlank() }
                    ?.parsePlaceholders(player)
                    ?.asMiniWithoutItalic()
                    ?: quest.displayName.get(player).parsePlaceholders(player).asMiniWithoutItalic()
                meta.displayName(nameComponent)
                meta.lore(lore)
                baseStack.itemMeta = meta
            }
            val questButton = baseStack

            inventory.setItem(slot, questButton)
            slotToQuest[slot] = quest
        }

        // Fill remaining quest slots with a dedicated placeholder
        for (slot in questSlots.drop(itemsToDisplay.size)) {
            val fillMaterialStr = menuConfig.defaultFillMaterial
            val fillMaterial = Material.getMaterial(fillMaterialStr) ?: Material.GRAY_STAINED_GLASS_PANE
            inventory.setItem(slot, menuConfig.emptyQuest.buildItem(player, fillMaterial))
        }

        if (menuConfig.fillEnabled) {
            inventory.fillWith(player, menuConfig.fill)
        }
        setupButtons()
    }

    private fun setupButtons() {
        val maxPage = ((filteredQuestsCount - 1) / maxQuestsPerPage).coerceAtLeast(0)

        val barrierMaterialStr = menuConfig.defaultBarrierMaterial
        val barrierMaterial = Material.getMaterial(barrierMaterialStr) ?: Material.BARRIER

        if (currentPage > 0 && menuConfig.buttons.previous.enabled) {
            inventory.setItem(previousSlot, menuConfig.buttons.previous.toItemTemplate().buildItem(player, barrierMaterial))
        }

        if (currentPage < maxPage && menuConfig.buttons.next.enabled) {
            inventory.setItem(nextSlot, menuConfig.buttons.next.toItemTemplate().buildItem(player, barrierMaterial))
        }

        if (menuConfig.buttons.sort.enabled) {
            val sortTemplate = menuConfig.buttons.sort
            val sortLore = mutableListOf<Component>()
            // Keep any custom lore configured for the button
            sortTemplate.lore.flatMap { it.split("\n") }
                .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
                .forEach { sortLore.add(it) }
            // Separate custom lore from the dynamic sort options
            if (sortLore.isNotEmpty()) {
                sortLore.add("".asMiniWithoutItalic())
            }
            listOf(
                SortOption.ALL to menuConfig.buttons.sort.allName,
                SortOption.COMPLETED to menuConfig.buttons.sort.completedName,
                SortOption.IN_PROGRESS to menuConfig.buttons.sort.inProgressName,
                SortOption.NOT_STARTED to menuConfig.buttons.sort.notStartedName
            ).map { (option, name) ->
                val sortSettings = menuConfig.buttons.sort
                val selected = sort == option
                val rawFormat = if (selected) sortSettings.selectedFormat else sortSettings.unselectedFormat
                val formatWithName = if (rawFormat.contains("{name}")) rawFormat else "$rawFormat{name}"
                val formatWithPrefix = if (selected && !formatWithName.contains("{prefix}")) {
                    "{prefix}$formatWithName"
                } else {
                    formatWithName
                }
                val resolved = formatWithPrefix
                    .replace("{prefix}", if (selected) sortSettings.selectedPrefix else "")
                    .replace("{name}", name)
                resolved.parsePlaceholders(player).asMiniWithoutItalic()
            }.forEach { sortLore.add(it) }

            val sortButton = sortTemplate.toItemTemplate().buildItem(
                player,
                barrierMaterial,
                loreOverride = sortLore,
            )
            inventory.setItem(sortSlot, sortButton)
        }

        if (menuConfig.buttons.back.enabled) {
            inventory.setItem(backSlot, menuConfig.buttons.back.toItemTemplate().buildItem(player, barrierMaterial))
        }

        val replayConfig = QuestCodexConfig.replayMenu
        if (replayConfig.enabled && replayConfig.replayButton.enabled && category.showPrologueButton) {
            val replayBarrierMaterialStr = replayConfig.defaultBarrierMaterial
            val replayBarrierMaterial = Material.getMaterial(replayBarrierMaterialStr) ?: Material.ENDER_EYE
            inventory.setItem(replaySlot, replayConfig.replayButton.toItemTemplate().buildItem(player, replayBarrierMaterial))
        }
    }

    override fun getInventory(): Inventory = inventory

    fun questForSlot(slot: Int): QuestEntry? = slotToQuest[slot]
    fun categoryForSlot(slot: Int): QuestCategory? = slotToCategory[slot]

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

private data class QuestView(val quest: QuestEntry, val status: QuestStatus)

private fun Boolean?.isTrue(): Boolean = this == true

