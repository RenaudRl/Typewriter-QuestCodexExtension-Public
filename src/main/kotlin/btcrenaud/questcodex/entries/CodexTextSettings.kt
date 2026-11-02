package btcrenaud.questcodex.entries

import btcrenaud.questcodex.navigation.CodexNavAction
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Placeholder

/**
 * Labels of the built-in navigation buttons, set from the `quest_codex` entry.
 *
 * A tagged slot that carries its own item keeps its own name; these labels are what the button
 * shows when the slot has no configured item.
 */
data class CodexNavLabels(
    @Help("Label of the next page button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<yellow>Next\"")
    val next: String = "<yellow>Next",
    @Help("Label of the previous page button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<yellow>Previous\"")
    val previous: String = "<yellow>Previous",
    @Help("Label of the scroll up button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<white>Up\"")
    val up: String = "<white>Up",
    @Help("Label of the scroll down button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<white>Down\"")
    val down: String = "<white>Down",
    @Help("Label of the scroll left button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<white>Left\"")
    val left: String = "<white>Left",
    @Help("Label of the scroll right button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<white>Right\"")
    val right: String = "<white>Right",
    @Help("Label of the back button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<red>Back\"")
    val back: String = "<red>Back",
    @Help("Label of the close button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<red>Close\"")
    val close: String = "<red>Close",
    @Help("Label of the sort button, shown when its slot has no configured item. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<yellow>Sort\"")
    val sort: String = "<yellow>Sort",
) {
    /** The label of the button for [action]. */
    fun labelFor(action: CodexNavAction): String = when (action) {
        CodexNavAction.PAGE_NEXT -> next
        CodexNavAction.PAGE_PREV -> previous
        CodexNavAction.SCROLL_UP -> up
        CodexNavAction.SCROLL_DOWN -> down
        CodexNavAction.SCROLL_LEFT -> left
        CodexNavAction.SCROLL_RIGHT -> right
        CodexNavAction.BACK -> back
        CodexNavAction.CLOSE -> close
        CodexNavAction.SORT -> sort
    }
}

/**
 * Texts of the sort button, set from the `quest_codex` entry. A `category_menu` that sets its own
 * label or lore for a sort mode (its `sortDisplay` list) keeps it; these apply when it does not.
 */
data class CodexSortTexts(
    @Help("Sort button label while every quest is listed. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<yellow>📋 All quests\"")
    val allLabel: String = "<yellow>📋 All quests",
    @Help("Sort button label while only the quests not started are listed. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<white>📋 Not started\"")
    val notStartedLabel: String = "<white>📋 Not started",
    @Help("Sort button label while only the quests in progress are listed. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<green>📋 In progress\"")
    val activeLabel: String = "<green>📋 In progress",
    @Help("Sort button label while only the completed quests are listed. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<gray>📋 Completed\"")
    val completedLabel: String = "<gray>📋 Completed",
    @Help("Lore line of the sort button telling the player a click changes the sorting. Supports MiniMessage.")
    @Placeholder @Colored
    @Default("\"<gray>Click to change sorting\"")
    val hint: String = "<gray>Click to change sorting",
) {
    /** The label shown while [mode] is the active sort. */
    fun labelFor(mode: SortModeConfig): String = when (mode) {
        SortModeConfig.ALL -> allLabel
        SortModeConfig.NOT_STARTED -> notStartedLabel
        SortModeConfig.ACTIVE -> activeLabel
        SortModeConfig.COMPLETED -> completedLabel
    }
}
