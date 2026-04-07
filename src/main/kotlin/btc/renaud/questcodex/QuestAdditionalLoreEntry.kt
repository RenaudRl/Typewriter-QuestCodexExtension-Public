package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.*
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.quest.entries.QuestEntry

@Entry(
    "quest_additional_lore",
    "Adds additional status-dependent lore to a quest button",
    Colors.PINK,
    "mdi:card-text-outline"
)
class QuestAdditionalLoreEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Quest reference to apply this additional lore to")
    val quest: Ref<QuestEntry> = emptyRef(),
    @Help("Additional lore shown when the quest is not started")
    @Placeholder
    @Colored
    @MultiLine
    val additionalLoreNotStarted: String = "",
    @Help("Additional lore shown when the quest is in progress")
    @Placeholder
    @Colored
    @MultiLine
    val additionalLoreInProgress: String = "",
    @Help("Additional lore shown when the quest is completed")
    @Placeholder
    @Colored
    @MultiLine
    val additionalLoreCompleted: String = "",
) : ManifestEntry
