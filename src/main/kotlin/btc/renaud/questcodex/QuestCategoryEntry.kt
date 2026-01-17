package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.ContentEditor
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.engine.paper.content.modes.custom.HoldingItemContentMode
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.quest.entries.QuestEntry

@Entry(
    "quest_category",
    "Adds quests to a quest codex category",
    Colors.RED,
    "mdi:book"
)
class QuestCategoryEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Quests to register in the category")
    val questRefs: List<Ref<QuestEntry>> = emptyList(),
    @Help("Optional display order for each quest. Provide values aligned with questRefs; lower numbers appear first.")
    val questOrders: List<Int> = emptyList(),
    @Help("Optional per-quest overrides. Values defined here take priority over the defaults configured below.")
    val questOverrides: List<QuestCategoryQuestOverride> = emptyList(),
    @Help("Name of the category to register the quests in")
    val category: String = "",
    @Help("Item displayed when the quest is locked/inactive")
    @ContentEditor(HoldingItemContentMode::class)
    val notStartedItem: Item = Item.Empty,
    @Help("Item displayed while the quest is in progress")
    @ContentEditor(HoldingItemContentMode::class)
    val inProgressItem: Item = Item.Empty,
    @Help("Item displayed once the quest is completed")
    @ContentEditor(HoldingItemContentMode::class)
    val completedItem: Item = Item.Empty,
    @Help("Custom name used when the quest is not started")
    @Placeholder
    @Colored
    val notStartedName: String = "",
    @Help("Custom name used when the quest is in progress")
    @Placeholder
    @Colored
    val inProgressName: String = "",
    @Help("Custom name used when the quest is completed")
    @Placeholder
    @Colored
    val completedName: String = "",
    @Help("Additional lore shown when the quest is not started")
    @Placeholder
    @Colored
    @MultiLine
    val notStartedLore: String = "",
    @Help("Additional lore shown when the quest is in progress")
    @Placeholder
    @Colored
    @MultiLine
    val inProgressLore: String = "",
    @Help("Additional lore shown when the quest is completed")
    @Placeholder
    @Colored
    @MultiLine
    val completedLore: String = "",
    @Help("Hide this quest from the menu while it is not started")
    val hideWhenNotStarted: Boolean = false,
    @Help("Hide this quest from the menu while it is in progress")
    val hideWhenInProgress: Boolean = false,
    @Help("Hide this quest from the menu once it is completed")
    val hideWhenCompleted: Boolean = false,
    @Help("Hide quest objectives while the quest is in progress")
    val hideObjectivesWhenInProgress: Boolean = false,
    @Help("Hide quest objectives once the quest is completed")
    val hideObjectivesWhenCompleted: Boolean = false,
) : ManifestEntry

data class QuestCategoryQuestOverride(
    @Help("Quest to apply the overrides to")
    val quest: Ref<QuestEntry> = emptyRef(),
    @Help("Item displayed when the quest is locked/inactive")
    @ContentEditor(HoldingItemContentMode::class)
    val notStartedItem: Item = Item.Empty,
    @Help("Item displayed while the quest is in progress")
    @ContentEditor(HoldingItemContentMode::class)
    val inProgressItem: Item = Item.Empty,
    @Help("Item displayed once the quest is completed")
    @ContentEditor(HoldingItemContentMode::class)
    val completedItem: Item = Item.Empty,
    @Help("Custom name used when the quest is not started")
    @Placeholder
    @Colored
    val notStartedName: String = "",
    @Help("Custom name used when the quest is in progress")
    @Placeholder
    @Colored
    val inProgressName: String = "",
    @Help("Custom name used when the quest is completed")
    @Placeholder
    @Colored
    val completedName: String = "",
    @Help("Additional lore shown when the quest is not started")
    @Placeholder
    @Colored
    @MultiLine
    val notStartedLore: String = "",
    @Help("Additional lore shown when the quest is in progress")
    @Placeholder
    @Colored
    @MultiLine
    val inProgressLore: String = "",
    @Help("Additional lore shown when the quest is completed")
    @Placeholder
    @Colored
    @MultiLine
    val completedLore: String = "",
    @Help("Lore additionnel affiche en bas du bouton quand le statut est Not Started (override)")
    @Placeholder
    @Colored
    @MultiLine
    val additionalLoreNotStarted: String = "",
    @Help("Lore additionnel affiche en bas du bouton quand le statut est In Progress (override)")
    @Placeholder
    @Colored
    @MultiLine
    val additionalLoreInProgress: String = "",
    @Help("Lore additionnel affiche en bas du bouton quand le statut est Completed (override)")
    @Placeholder
    @Colored
    @MultiLine
    val additionalLoreCompleted: String = "",
    @Help("Hide this quest from the menu while it is not started")
    val hideWhenNotStarted: Boolean = false,
    @Help("Hide this quest from the menu while it is in progress")
    val hideWhenInProgress: Boolean = false,
    @Help("Hide this quest from the menu once it is completed")
    val hideWhenCompleted: Boolean = false,
    @Help("Hide quest objectives while the quest is in progress")
    val hideObjectivesWhenInProgress: Boolean = false,
    @Help("Hide quest objectives once the quest is completed")
    val hideObjectivesWhenCompleted: Boolean = false,
)
