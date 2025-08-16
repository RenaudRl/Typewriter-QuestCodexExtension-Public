package btc.renaud.questcodex

import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.engine.paper.command.dsl.*
import com.typewritermc.quest.QuestEntry
import com.typewritermc.quest.trackQuest
import com.typewritermc.quest.unTrackQuest

/** Provides commands to track or untrack quests without chat messages. */
@TypewriterCommand
fun CommandTree.questCommand() = literal("quest") {
    withPermission("typewriter.quest")
    literal("track") {
        withPermission("typewriter.quest.track")
        entry<QuestEntry>("quest") { quest ->
            executePlayerOrTarget { target ->
                target.trackQuest(quest().ref())
            }
        }
    }
    literal("untrack") {
        withPermission("typewriter.quest.untrack")
        executePlayerOrTarget { target ->
            target.unTrackQuest()
        }
    }
}
