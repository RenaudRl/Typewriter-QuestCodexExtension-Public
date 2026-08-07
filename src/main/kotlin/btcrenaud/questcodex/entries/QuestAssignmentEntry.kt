package btcrenaud.questcodex.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.*
import com.typewritermc.engine.paper.content.modes.custom.HoldingItemContentMode
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.utils.item.CustomItem
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.quest.entries.QuestEntry

/**
 * Assigns quests to a category with optional per-quest display overrides.
 *
 * Multiple entries can reference the same category to add more quests.
 * Quest order is controlled via [orders] (aligned with [questRefs]).
 *
 * Example:
 * ```
 * quest_assignment:
 *   category: "main_quests"
 *   quest_refs:
 *     - "quest:the_beginning"
 *     - "quest:the_journey"
 *   orders: [0, 1]
 *   not_started_item: PAPER
 *   in_progress_item: CLOCK
 *   completed_item: EMERALD
 * ```
 */
@Entry("quest_assignment", "Quest Assignment to Category", Colors.GREEN, "mdi:bookmark-plus")
class QuestAssignmentEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Category to assign quests to.")
    val category: String = "",
    @Help("Quests to assign to this category.")
    val questRefs: List<Ref<QuestEntry>> = emptyList(),
    @Help(
        "Display order for each quest, aligned index-by-index with questRefs. Lower numbers appear " +
            "first. Optional: leave it empty and quests keep the order you listed them in. Ordered " +
            "quests always come before unordered ones, and the order is the same for every player."
    )
    val orders: List<Int> = emptyList(),
    @Help("Override: item shown when the quest has not started.")
    @ContentEditor(HoldingItemContentMode::class)
    val notStartedItem: Item? = null,
    @Help("Override: item shown when the quest is in progress.")
    @ContentEditor(HoldingItemContentMode::class)
    val inProgressItem: Item? = null,
    @Help("Override: item shown when the quest is completed.")
    @ContentEditor(HoldingItemContentMode::class)
    val completedItem: Item? = null,
    @Help("Override: name shown when the quest has not started.")
    @Placeholder
    @Colored
    val notStartedName: String = "",
    @Help("Override: name shown when the quest is in progress.")
    @Placeholder
    @Colored
    val inProgressName: String = "",
    @Help("Override: name shown when the quest is completed.")
    @Placeholder
    @Colored
    val completedName: String = "",
    @Help("Override: additional lore when the quest has not started.")
    @Placeholder
    @Colored
    @MultiLine
    val notStartedLore: String = "",
    @Help("Override: additional lore when the quest is in progress.")
    @Placeholder
    @Colored
    @MultiLine
    val inProgressLore: String = "",
    @Help("Override: additional lore when the quest is completed.")
    @Placeholder
    @Colored
    @MultiLine
    val completedLore: String = "",
    @Help("Hide this quest while not started.")
    val hideWhenNotStarted: Boolean = false,
    @Help("Hide this quest while in progress.")
    val hideWhenInProgress: Boolean = false,
    @Help("Hide this quest when completed.")
    val hideWhenCompleted: Boolean = false,
    @Help("Hide objectives while in progress.")
    val hideObjectivesWhenInProgress: Boolean = false,
    @Help("Hide objectives when completed.")
    val hideObjectivesWhenCompleted: Boolean = false,
) : ManifestEntry
