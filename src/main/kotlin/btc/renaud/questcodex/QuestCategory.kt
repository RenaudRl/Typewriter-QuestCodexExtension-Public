package btc.renaud.questcodex

import com.typewritermc.core.entries.Ref
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.quest.entries.QuestEntry
import com.typewritermc.quest.QuestStatus
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import btc.renaud.questcodex.QuestCodexConfig
import btc.renaud.questcodex.CategoryMenuSettings
import btc.renaud.questcodex.CategoryStatus
import btc.renaud.questcodex.categoryStatus
import btc.renaud.questcodex.allQuests
import com.typewritermc.engine.paper.utils.asMini
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import btc.renaud.questcodex.asMiniWithoutItalic
import net.kyori.adventure.text.format.TextDecoration
import btc.renaud.questcodex.isEffectivelyEmpty
import org.bukkit.Material
import com.typewritermc.engine.paper.utils.item.CustomItem

/**
 * Represents a collection of quests grouped under a single category.
 *
 * Categories can be dynamically registered through the API and are later
 * exposed to players through the Quest Codex menus.
 */
data class QuestCategory(
    val name: String,
    /** Title used for the menu inventory. */
    var title: String = name,
    /** Display name applied to the category icon. */
    var iconName: String = name,
    /** Number of rows shown in the menu (3-6). */
    var rows: Int = 3,
    /** Item used as icon for this category. */
    var item: Item = CustomItem(),
    /** Optional name color/style override for main menu. */
    var nameColor: String = "",
    /** Optional explicit slot of this category inside the menu. */
    var slot: Int? = null,
    /** Custom quest slots for the quest menu. */
    var questSlots: List<Int> = emptyList(),
    /** Whether to hide quests that are locked in the menu. */
    var hideLockedQuests: Boolean = false,
    /** Whether to hide this category from menus when it is locked. */
    var hideWhenLocked: Boolean = false,
    /** Criteria determining when the category becomes active. */
    var activeCriteria: List<Criteria> = emptyList(),
    /** Criteria determining when the category is completed. */
    var completedCriteria: List<Criteria> = emptyList(),
    /** Lore shown when the category is blocked. */
    var blockedMessage: List<String> = emptyList(),
    /** Lore shown when the category is in progress. */
    var activeMessage: List<String> = emptyList(),
    /** Lore shown when the category is completed. */
    var completedMessage: List<String> = emptyList(),
    /** Display order for this category. Categories with lower numbers appear first. */
    var order: Int = 0,
    val quests: MutableList<Ref<QuestEntry>> = mutableListOf(),
    /** Optional ordering for quests within this category. */
    val questOrders: MutableMap<String, Int> = mutableMapOf(),
    /** Optional quest item overrides per quest and status. */
    val questItems: MutableMap<String, QuestItemOverrides> = mutableMapOf(),
    /** Optional quest display overrides per quest and status. */
    val questDisplays: MutableMap<String, QuestDisplayOverrides> = mutableMapOf(),
    /** Optional extra lore appended after the quest's main lore for each quest, per status. */
    val questAdditionalLore: MutableMap<String, QuestAdditionalLore> = mutableMapOf(),
    /** Optional restriction messages for quests in this category. */
    val restrictions: MutableMap<String, List<String>> = mutableMapOf(),
    /** Parent category if this category is a sub-category. */
    var parent: QuestCategory? = null,
    /** Child categories registered under this category. */
    val subCategories: MutableList<QuestCategory> = mutableListOf(),
    /** Optional per-category quest count lore override. */
    var categoryLoreQuestCountOverride: List<String>? = null,
    /** Optional per-category lore override. */
    var categoryLoreOverride: List<String>? = null,
    /** Link to a specific quest. */
    var refQuest: String = "",
    /** Whether to show the prologue replay button. */
    var showPrologueButton: Boolean = true,
    /** Whether to show the quest list. */
    var showQuestButton: Boolean = true,
    /** Whether to show sub-categories. */
    var showCategoriesButton: Boolean = true,
)

fun QuestCategory.buildIcon(player: Player, menuConfig: CategoryMenuSettings): ItemStack {
    val fillMaterialStr = menuConfig.defaultFillMaterial
    val fillMaterial = Material.getMaterial(fillMaterialStr) ?: Material.BOOK
    val baseItem = when {
        !item.isEffectivelyEmpty() -> item.build(player)
        !menuConfig.categoryItem.isEffectivelyEmpty() -> menuConfig.categoryItem.build(player)
        else -> ItemStack(fillMaterial)
    }
    val meta = baseItem.itemMeta ?: return baseItem
    val styleString = if (nameColor.isNotBlank()) nameColor else menuConfig.categoryNameColor
    val styleComponent = styleString.parsePlaceholders(player).asMini()
    val rawIconName = if (iconName.isNotBlank()) iconName else title
    var nameComponent = rawIconName.parsePlaceholders(player).asMiniWithoutItalic()
    nameComponent = nameComponent.style(styleComponent.style())
    if (menuConfig.categoryNameBold) {
        nameComponent = nameComponent.decoration(TextDecoration.BOLD, true)
    }
    meta.displayName(nameComponent)

    val quests = allQuests()
    val total = quests.size
    val completed = quests.count { it.questStatus(player) == QuestStatus.COMPLETED }
    val status = categoryStatus(player)

    val loreLines = mutableListOf<String>()
    val questCountTemplates = categoryLoreQuestCountOverride
        ?: menuConfig.categoryLoreQuestCount
    questCountTemplates.forEach { template ->
        loreLines += template
            .replace("<completed>", completed.toString())
            .replace("<total>", total.toString())
    }
    if (status != CategoryStatus.BLOCKED) {
        val baseLoreTemplates = categoryLoreOverride ?: menuConfig.categoryLore
        baseLoreTemplates.forEach { loreLines += it }
    }
    when (status) {
        CategoryStatus.BLOCKED -> loreLines += blockedMessage
        CategoryStatus.IN_PROGRESS -> loreLines += activeMessage
        CategoryStatus.COMPLETED -> loreLines += completedMessage
    }

    meta.lore(
        loreLines.flatMap { it.split("\n") }
            .map { it.parsePlaceholders(player).asMiniWithoutItalic() }
    )
    baseItem.itemMeta = meta
    return baseItem
}

data class QuestItemOverrides(
    val notStarted: Item = CustomItem(),
    val inProgress: Item = CustomItem(),
    val completed: Item = CustomItem(),
) {
    fun hasOverrides(): Boolean =
        notStarted != Item.Empty || inProgress != Item.Empty || completed != Item.Empty

    fun itemFor(status: QuestStatus): Item = when (status) {
        QuestStatus.INACTIVE -> notStarted
        QuestStatus.ACTIVE -> inProgress
        QuestStatus.COMPLETED -> completed
    }

    fun overrideWith(other: QuestItemOverrides): QuestItemOverrides = QuestItemOverrides(
        notStarted = if (other.notStarted != Item.Empty) other.notStarted else notStarted,
        inProgress = if (other.inProgress != Item.Empty) other.inProgress else inProgress,
        completed = if (other.completed != Item.Empty) other.completed else completed,
    )
}

data class QuestStateDisplayOverride(
    val name: String = "",
    val lore: List<String> = emptyList(),
    val hideObjectives: Boolean = false,
    val hideQuest: Boolean = false,
) {
    fun hasOverrides(): Boolean =
        name.isNotBlank() || lore.isNotEmpty() || hideObjectives || hideQuest

    fun overrideWith(other: QuestStateDisplayOverride): QuestStateDisplayOverride = QuestStateDisplayOverride(
        name = if (other.name.isNotBlank()) other.name else name,
        lore = if (other.lore.isNotEmpty()) other.lore else lore,
        hideObjectives = hideObjectives || other.hideObjectives,
        hideQuest = hideQuest || other.hideQuest,
    )
}

data class QuestDisplayOverrides(
    val notStarted: QuestStateDisplayOverride = QuestStateDisplayOverride(),
    val inProgress: QuestStateDisplayOverride = QuestStateDisplayOverride(),
    val completed: QuestStateDisplayOverride = QuestStateDisplayOverride(),
) {
    fun hasOverrides(): Boolean =
        notStarted.hasOverrides() || inProgress.hasOverrides() || completed.hasOverrides()

    fun overrideWith(other: QuestDisplayOverrides): QuestDisplayOverrides = QuestDisplayOverrides(
        notStarted = notStarted.overrideWith(other.notStarted),
        inProgress = inProgress.overrideWith(other.inProgress),
        completed = completed.overrideWith(other.completed),
    )

    fun state(status: QuestStatus): QuestStateDisplayOverride = when (status) {
        QuestStatus.INACTIVE -> notStarted
        QuestStatus.ACTIVE -> inProgress
        QuestStatus.COMPLETED -> completed
    }
}

data class QuestAdditionalLore(
    val notStarted: List<String> = emptyList(),
    val inProgress: List<String> = emptyList(),
    val completed: List<String> = emptyList(),
) {
    fun hasContent(): Boolean =
        notStarted.isNotEmpty() || inProgress.isNotEmpty() || completed.isNotEmpty()

    fun overrideWith(other: QuestAdditionalLore): QuestAdditionalLore = QuestAdditionalLore(
        notStarted = if (other.notStarted.isNotEmpty()) other.notStarted else notStarted,
        inProgress = if (other.inProgress.isNotEmpty()) other.inProgress else inProgress,
        completed = if (other.completed.isNotEmpty()) other.completed else completed,
    )

    fun forStatus(status: QuestStatus): List<String> = when (status) {
        QuestStatus.INACTIVE -> notStarted
        QuestStatus.ACTIVE -> inProgress
        QuestStatus.COMPLETED -> completed
    }
}

/**
 * Simple registry used by the extension to manage quest categories.
 */
object QuestCategoryRegistry {
    private val categories: MutableMap<String, QuestCategory> = mutableMapOf()

    /**
     * Register a new category if it doesn't already exist. When registering
     * an already existing category this will update the menu configuration.
     */
    fun register(
        name: String,
        title: String = name,
        rows: Int = 3,
        item: Item = CustomItem(),
        nameColor: String = "",
        parent: String = "",
        order: Int = 0,
        slot: Int? = null,
        questSlots: List<Int> = emptyList(),
        activeCriteria: List<Criteria> = emptyList(),
        completedCriteria: List<Criteria> = emptyList(),
        blockedMessage: List<String> = emptyList(),
        activeMessage: List<String> = emptyList(),
        completedMessage: List<String> = emptyList(),
        hideLockedQuests: Boolean = false,
        hideWhenLocked: Boolean = false,
        iconName: String = "",
        categoryLoreQuestCount: List<String>? = null,
        categoryLore: List<String>? = null,
        refQuest: String = "",
        showPrologueButton: Boolean = true,
        showQuestButton: Boolean = true,
        showCategoriesButton: Boolean = true,
    ): QuestCategory {
        val key = name.lowercase()
        val category = categories.getOrPut(key) { QuestCategory(name) }
        category.title = title
        category.iconName = iconName.takeIf { it.isNotBlank() } ?: title
        category.rows = rows.coerceIn(3, 6)
        if (!item.isEffectivelyEmpty()) {
            category.item = item
        }
        if (nameColor.isNotBlank()) {
            category.nameColor = nameColor
        }
        if (slot != null && slot >= 0) {
            category.slot = slot
        }
        if (questSlots.isNotEmpty()) {
            category.questSlots = questSlots.filter { it >= 0 }
        }
        if (parent.isNotBlank()) {
            val parentCategory = findByIdOrRefQuest(parent) ?: ensure(parent)
            category.parent = parentCategory
            if (!parentCategory.subCategories.contains(category)) {
                parentCategory.subCategories.add(category)
            }
        }
        category.order = order
        category.activeCriteria = activeCriteria
        category.completedCriteria = completedCriteria
        category.blockedMessage = blockedMessage
        category.activeMessage = activeMessage
        category.completedMessage = completedMessage
        category.hideLockedQuests = hideLockedQuests
        category.hideWhenLocked = hideWhenLocked
        category.categoryLoreQuestCountOverride = categoryLoreQuestCount
        category.categoryLoreOverride = categoryLore
        category.refQuest = refQuest
        category.showPrologueButton = showPrologueButton
        category.showQuestButton = showQuestButton
        category.showCategoriesButton = showCategoriesButton
        return category
    }

    private fun findByIdOrRefQuest(id: String): QuestCategory? {
        val key = id.lowercase()
        categories[key]?.let { return it }
        return categories.values.find { it.refQuest.equals(id, ignoreCase = true) }
    }

    private fun ensure(name: String): QuestCategory {
        val key = name.lowercase()
        return categories.getOrPut(key) { QuestCategory(name) }
    }

    /**
     * Find a category by its name (case insensitive).
     */
    fun find(name: String): QuestCategory? = categories[name.lowercase()]

    /**
     * Add a quest to a category, creating the category if needed.
     */
    fun addQuest(
        categoryName: String,
        questRef: Ref<QuestEntry>,
        quest: QuestEntry,
        order: Int? = null,
        overrides: QuestItemOverrides? = null,
        displayOverrides: QuestDisplayOverrides? = null,
        additionalLore: QuestAdditionalLore? = null,
    ) {
        val category = ensure(categoryName)
        if (questRef.id.isNotBlank() && !category.quests.contains(questRef)) {
            category.quests.add(questRef)
        }
        if (order != null) {
            category.questOrders[quest.id] = order
        } else {
            category.questOrders.remove(quest.id)
        }
        overrides?.takeIf { it.hasOverrides() }?.let { newOverrides ->
            val existingOverrides = category.questItems[quest.id]
            val merged = existingOverrides?.overrideWith(newOverrides) ?: newOverrides
            if (existingOverrides != null && merged != existingOverrides) {
                plugin.logger.fine(
                    "[QuestCodex] Updating quest item overrides for quest ${quest.id} in category ${category.name}."
                )
            }
            category.questItems[quest.id] = merged
        }
        displayOverrides?.takeIf { it.hasOverrides() }?.let { newOverrides ->
            val existingOverrides = category.questDisplays[quest.id]
            val merged = existingOverrides?.overrideWith(newOverrides) ?: newOverrides
            if (existingOverrides != null && merged != existingOverrides) {
                plugin.logger.fine(
                    "[QuestCodex] Updating quest display overrides for quest ${quest.id} in category ${category.name}."
                )
            }
            category.questDisplays[quest.id] = merged
        }
        if (additionalLore != null && additionalLore.hasContent()) {
            category.questAdditionalLore[quest.id] = additionalLore
        }
    }

    /**
     * Set a restriction message for a quest within a category.
     */
    fun setRestriction(categoryName: String, quest: Ref<QuestEntry>, message: List<String>) {
        if (quest.id.isBlank()) return
        val category = ensure(categoryName)
        quest.get()?.let { q ->
            category.restrictions[q.id] = message
        }
    }

    /**
     * Return all registered categories.
     */
    fun all(): Collection<QuestCategory> = categories.values.sortedWith(
        compareBy({ if (it.order == 0) Int.MAX_VALUE else it.order }, { it.title })
    )

    /**
     * Return root categories (those without a parent).
     */
    fun roots(): Collection<QuestCategory> = categories.values.filter { it.parent == null }.sortedWith(
        compareBy({ if (it.order == 0) Int.MAX_VALUE else it.order }, { it.title })
    )
}

/**
 * Recursively collect all quests registered under this category and its sub-categories.
 */
fun QuestCategory.allQuests(): List<QuestEntry> =
    (quests.mapNotNull { it.get() } + subCategories.flatMap { it.allQuests() }).distinct()

enum class CategoryStatus {
    BLOCKED,
    IN_PROGRESS,
    COMPLETED,
}

fun QuestCategory.categoryStatus(player: Player): CategoryStatus = when {
    completedCriteria.matches(player) -> CategoryStatus.COMPLETED
    activeCriteria.matches(player) -> CategoryStatus.IN_PROGRESS
    else -> CategoryStatus.BLOCKED
}

/**
 * Registry for cinematic replay categories.
 */
object PrologueReplayRegistry {
    private val categories: MutableMap<String, PrologueReplayCategory> = mutableMapOf()

    fun register(
        id: String,
        title: String,
        item: Item,
        order: Int,
        slot: Int,
        questCategory: String,
        entries: List<PrologueReplayEntryData>
    ) {
        categories[id.lowercase()] = PrologueReplayCategory(
            id = id,
            title = title,
            item = item,
            order = order,
            slot = slot,
            questCategory = questCategory,
            entries = entries
        )
    }

    fun find(id: String): PrologueReplayCategory? = categories[id.lowercase()]

    fun findByQuestCategory(categoryName: String): PrologueReplayCategory? {
        val key = categoryName.lowercase()
        return categories.values.find { it.questCategory.lowercase() == key }
    }

    fun all(): Collection<PrologueReplayCategory> = categories.values.sortedBy { it.order }
}

data class PrologueReplayCategory(
    val id: String,
    val title: String,
    val item: Item,
    val order: Int,
    val slot: Int,
    val questCategory: String,
    val entries: List<PrologueReplayEntryData>
)

