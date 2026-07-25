package btcrenaud.questcodex.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Tags
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry
import java.util.UUID

/**
 * Persistent, player-indexed storage for QuestCodex multi-tracking.
 *
 * The artifact stores quest ids in tracking order. The last id is restored as
 * the official Quest extension's primary tracker after the player reconnects.
 */
@Entry(
    "quest_codex_tracking_artifact",
    "Quest Codex Tracking Data",
    Colors.YELLOW,
    "mdi:database-clock",
)
@Tags("quest", "tracking", "artifact")
class QuestCodexTrackingArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    override val artifactId: String = UUID.randomUUID().toString(),
) : ArtifactEntry
