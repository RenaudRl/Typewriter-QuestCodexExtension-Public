package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.ContentEditor
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.content.modes.custom.HoldingItemContentMode
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.quest.QuestEntry

/**
 * Typewriter entry used to link a quest to a specific category. When the
 * extension starts all these entries are scanned and the quests are registered
 * with the [QuestCategoryRegistry].
 */
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
) : ManifestEntry