package btcrenaud.questcodex.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import java.util.UUID

/** Persistent snapshots used to recover an interrupted dialogue or cinematic. */
@Entry(
    "quest_codex_recovery_artifact",
    "Quest Codex Recovery Data",
    Colors.YELLOW,
    "mdi:shield-sync",
)
@Tags("quest", "recovery", "artifact")
class QuestCodexRecoveryArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    override val artifactId: String = UUID.randomUUID().toString(),
) : ArtifactEntry
