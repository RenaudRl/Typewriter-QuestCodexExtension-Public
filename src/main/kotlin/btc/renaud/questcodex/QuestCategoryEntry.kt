package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
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
    @Help("Name of the category to register the quests in")
    val category: String = "",
) : ManifestEntry