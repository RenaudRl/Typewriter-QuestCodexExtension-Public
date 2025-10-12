package btc.renaud.questcodex

import com.typewritermc.core.entries.Ref
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.quest.QuestEntry
import com.typewritermc.quest.QuestStatus
import org.bukkit.entity.Player

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
    var item: Item = Item.Empty,
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
)

data class QuestItemOverrides(
    val notStarted: Item = Item.Empty,
    val inProgress: Item = Item.Empty,
    val completed: Item = Item.Empty,
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
        item: Item = Item.Empty,
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
    ): QuestCategory {
        val key = name.lowercase()
        val category = categories.getOrPut(key) { QuestCategory(name) }
        category.title = title
        category.iconName = iconName.takeIf { it.isNotBlank() } ?: title
        category.rows = rows.coerceIn(3, 6)
        if (item != Item.Empty) {
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
            val parentCategory = ensure(parent)
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
        return category
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
    ) {
        val category = ensure(categoryName)
        if (!category.quests.contains(questRef)) {
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
    }

    /**
     * Set a restriction message for a quest within a category.
     */
    fun setRestriction(categoryName: String, quest: Ref<QuestEntry>, message: List<String>) {
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
