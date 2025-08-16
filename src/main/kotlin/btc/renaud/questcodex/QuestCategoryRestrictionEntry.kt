package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.quest.QuestEntry

/**
 * Defines additional restriction lore for quests in a specific category.
 * The message is appended below the default "Not Started" lore.
 */
@Entry(
    "quest_category_restriction",
    "Adds restriction lore to a quest within a category",
    Colors.RED,
    "mdi:book-alert",
)
class QuestCategoryRestrictionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Quests to apply the restriction to")
    val questRefs: List<Ref<QuestEntry>> = emptyList(),
    @Help("Category the quests belong to")
    val category: String = "",
    @Help("Additional lore lines shown when the quest is not started")
    @Placeholder
    @Colored
    @MultiLine
    val message: String = "",
) : ManifestEntry
