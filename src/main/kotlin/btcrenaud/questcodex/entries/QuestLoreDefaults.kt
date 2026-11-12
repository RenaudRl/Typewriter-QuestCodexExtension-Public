package btcrenaud.questcodex.entries

import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder

/**
 * Lore a quest button shows when no `quest_assignment` sets one for the quest's state.
 *
 * Off by default so a codex configured before 2.18 looks exactly the same after the update.
 */
data class QuestLoreDefaults(
    @Help("Show a default lore on quest buttons: a message for not started and completed quests, the active objectives for quests in progress.")
    @Default("false")
    val enabled: Boolean = false,
    @Help("Lore of a quest the player has not started. Leave empty to show nothing. Supports MiniMessage.")
    @Placeholder @Colored @MultiLine
    @Default("\"<gray>Not started yet\"")
    val notStartedMessage: String = "<gray>Not started yet",
    @Help("Line shown above the active objectives of a quest in progress. Leave empty to list the objectives without a title. Supports MiniMessage.")
    @Placeholder @Colored @MultiLine
    @Default("\"<gold>Objectives:\"")
    val objectivesHeader: String = "<gold>Objectives:",
    @Help("Lore of a completed quest. Leave empty to show nothing. Supports MiniMessage.")
    @Placeholder @Colored @MultiLine
    @Default("\"<green>Quest completed\"")
    val completedMessage: String = "<green>Quest completed",
)
