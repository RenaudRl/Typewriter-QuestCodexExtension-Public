package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.*
import com.typewritermc.engine.paper.content.modes.custom.HoldingItemContentMode
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.utils.item.Item

/**
 * Defines a quest category and its menu configuration. Categories can be
 * declared even if no quests are yet associated with them.
 */
@Entry(
    "quest_category_definition",
    "Defines a quest category for the quest codex",
    Colors.RED,
    "mdi:book"
)
class QuestCategoryDefinitionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Unique name of the category")
    val category: String = "",
    @Help("Parent category this category belongs to")
    val parent: String = "",
    @Help("Title used for the menu inventory")
    @Placeholder
    val title: String = "",
    @Help("Number of rows in the inventory menu (3-6)")
    val rows: Int = 3,
    @Help("Item used as the icon for this category")
    @ContentEditor(HoldingItemContentMode::class)
    val item: Item = Item.Empty,
    @Help("Color/style applied to the category name in the menu")
    @Placeholder
    val nameColor: String = "",
    @Help("Criteria required for the category to become active")
    val activeCriteria: List<Criteria> = emptyList(),
    @Help("Criteria required for the category to be considered completed")
    val completedCriteria: List<Criteria> = emptyList(),
    @Help("Hide quests that are locked from this category's menu")
    val hideLockedQuests: Boolean = false,
    @Help("Hide this category from menus when it is locked")
    val hideWhenLocked: Boolean = false,
    @Help("Lore shown when the category is blocked")
    @Placeholder
    @Colored
    @MultiLine
    val blockedMessage: String = "",
    @Help("Lore shown when the category is in progress")
    @Placeholder
    @Colored
    @MultiLine
    val activeMessage: String = "",
    @Help("Lore shown when the category is completed")
    @Placeholder
    @Colored
    @MultiLine
    val completedMessage: String = "",
) : ManifestEntry
