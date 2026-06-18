package btcrenaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.*
import com.typewritermc.engine.paper.content.modes.custom.HoldingItemContentMode
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.utils.item.CustomItem
import com.typewritermc.engine.paper.utils.item.Item

/**
 * Defines a quest category.
 *
 * A category groups quests together in the codex. Categories can be nested
 * via [parent] to create sub-categories.
 *
 * To add quests to this category, use [QuestAssignmentEntry].
 * To configure the menu for this category, use [CategoryMenuEntry].
 *
 * Example:
 * ```
 * quest_category:
 *   category: "main_quests"
 *   title: "<gold>Main Story"
 *   icon: GOLDEN_SWORD
 *   order: 0
 *
 * quest_category:
 *   category: "side_quests"
 *   title: "<aqua>Side Quests"
 *   icon: BOOK
 *   parent: "main_quests"
 *   order: 1
 * ```
 */
@Entry("quest_category", "Quest Category", Colors.RED, "mdi:book")
class QuestCategoryDefinitionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Unique category identifier (lowercase, no spaces).")
    val category: String = "",
    @Help("Display title shown in the codex menu.")
    @Placeholder
    @Colored
    val title: String = "",
    @Help("Item used as the category icon in menus.")
    @ContentEditor(HoldingItemContentMode::class)
    val icon: Item = CustomItem(),
    @Help("Parent category name. Leave empty for a top-level category.")
    val parent: String = "",
    @Help("Display order (lower = first). Categories with the same order are sorted alphabetically.")
    val order: Int = 0,
    @Help("Criteria required for this category to be visible.")
    val activeCriteria: List<Criteria> = emptyList(),
    @Help("Criteria required for this category to be marked as completed.")
    val completedCriteria: List<Criteria> = emptyList(),
    @Help("Message shown when the category is locked (player does not meet active criteria).")
    @Placeholder
    @Colored
    @MultiLine
    val blockedMessage: String = "",
    @Help("Message shown when the category is in progress.")
    @Placeholder
    @Colored
    @MultiLine
    val activeMessage: String = "",
    @Help("Message shown when the category is completed.")
    @Placeholder
    @Colored
    @MultiLine
    val completedMessage: String = "",
    @Help("Whether to hide this category when locked.")
    val hideWhenLocked: Boolean = false,
) : ManifestEntry
