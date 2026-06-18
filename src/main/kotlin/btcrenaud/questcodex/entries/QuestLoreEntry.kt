package btcrenaud.questcodex.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.*
import com.typewritermc.engine.paper.entry.ManifestEntry

/**
 * Adds status-dependent additional lore to a quest button in the codex.
 *
 * Unlike [QuestAssignmentEntry] which defines display overrides for a quest
 * within a specific category, this entry adds extra lore lines that appear
 * at the bottom of the quest button.
 *
 * Set [category] to restrict this lore to a specific category.
 * Leave [category] empty to apply globally across all categories.
 *
 * Example (category-specific):
 * ```
 * quest_lore:
 *   quest: "quest:the_beginning"
 *   category: "main_quests"
 *   not_started_lore: "<gray>Talk to the village elder"
 *   in_progress_lore: "<yellow>Find the ancient artifact"
 *   completed_lore: "<green>You found the artifact!"
 * ```
 */
@Entry("quest_lore", "Quest Additional Lore", Colors.PURPLE, "mdi:text-box")
class QuestLoreEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Quest to add lore to.")
    val quest: Ref<com.typewritermc.quest.entries.QuestEntry> = emptyRef(),
    @Help("Category this lore applies to. Leave empty to apply globally.")
    val category: String = "",
    @Help("Additional lore shown when the quest has not started.")
    @Placeholder
    @Colored
    @MultiLine
    val notStartedLore: String = "",
    @Help("Additional lore shown when the quest is in progress.")
    @Placeholder
    @Colored
    @MultiLine
    val inProgressLore: String = "",
    @Help("Additional lore shown when the quest is completed.")
    @Placeholder
    @Colored
    @MultiLine
    val completedLore: String = "",
) : ManifestEntry
