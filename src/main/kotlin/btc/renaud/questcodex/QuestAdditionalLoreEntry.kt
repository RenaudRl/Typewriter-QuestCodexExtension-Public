package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.MultiLine
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.quest.QuestEntry

@Entry(
    "quest_additional_lore",
    "Defines additional lore for a specific quest by status",
    Colors.RED,
    "mdi:note-text"
)
class QuestAdditionalLoreEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Quest to attach the additional lore to")
    val quest: Ref<QuestEntry> = emptyRef(),
    @Help("Lore additionnel affiché en bas du bouton quand le statut est Not Started")
    @Placeholder @Colored @MultiLine
    val additionalLoreNotStarted: String = "",
    @Help("Lore additionnel affiché en bas du bouton quand le statut est In Progress")
    @Placeholder @Colored @MultiLine
    val additionalLoreInProgress: String = "",
    @Help("Lore additionnel affiché en bas du bouton quand le statut est Completed")
    @Placeholder @Colored @MultiLine
    val additionalLoreCompleted: String = "",
) : ManifestEntry