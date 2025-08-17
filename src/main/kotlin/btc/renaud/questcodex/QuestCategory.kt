package btc.renaud.questcodex

import com.typewritermc.core.entries.Ref
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.matches
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.quest.QuestEntry
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
    /** Number of rows shown in the menu (3-6). */
    var rows: Int = 3,
    /** Item used as icon for this category. */
    var item: Item = Item.Empty,
    /** Optional name color/style override for main menu. */
    var nameColor: String = "",
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
    val quests: MutableList<Ref<QuestEntry>> = mutableListOf(),
    /** Optional restriction messages for quests in this category. */
    val restrictions: MutableMap<String, List<String>> = mutableMapOf(),
    /** Parent category if this category is a sub-category. */
    var parent: QuestCategory? = null,
    /** Child categories registered under this category. */
    val subCategories: MutableList<QuestCategory> = mutableListOf(),
)

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
        activeCriteria: List<Criteria> = emptyList(),
        completedCriteria: List<Criteria> = emptyList(),
        blockedMessage: List<String> = emptyList(),
        activeMessage: List<String> = emptyList(),
        completedMessage: List<String> = emptyList(),
        hideLockedQuests: Boolean = false,
        hideWhenLocked: Boolean = false,
    ): QuestCategory {
        val key = name.lowercase()
        val category = categories.getOrPut(key) { QuestCategory(name) }
        category.title = title
        category.rows = rows.coerceIn(3, 6)
        if (item != Item.Empty) {
            category.item = item
        }
        if (nameColor.isNotBlank()) {
            category.nameColor = nameColor
        }
        if (parent.isNotBlank()) {
            val parentCategory = ensure(parent)
            category.parent = parentCategory
            if (!parentCategory.subCategories.contains(category)) {
                parentCategory.subCategories.add(category)
            }
        }
        category.activeCriteria = activeCriteria
        category.completedCriteria = completedCriteria
        category.blockedMessage = blockedMessage
        category.activeMessage = activeMessage
        category.completedMessage = completedMessage
        category.hideLockedQuests = hideLockedQuests
        category.hideWhenLocked = hideWhenLocked
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
    fun addQuest(categoryName: String, quest: Ref<QuestEntry>) {
        val category = ensure(categoryName)
        if (!category.quests.contains(quest)) {
            category.quests.add(quest)
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
    fun all(): Collection<QuestCategory> = categories.values

    /**
     * Return root categories (those without a parent).
     */
    fun roots(): Collection<QuestCategory> = categories.values.filter { it.parent == null }
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
