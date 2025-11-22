package btc.renaud.questcodex

import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.engine.paper.command.dsl.*
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.msg
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.trackQuest
import com.typewritermc.quest.unTrackQuest

/** Provides commands to track or untrack quests without chat messages. */
@TypewriterCommand
fun CommandTree.questCommand() = literal("quest") {
    withPermission("typewriter.quest")
    literal("track") {
        withPermission("typewriter.quest.track")
        questEntry("quest") { quest ->
            executePlayerOrTarget { target ->
                val questEntry = quest()
                if (questEntry.questStatus(target) != QuestStatus.ACTIVE) {
                    if (sender != target) {
                        sender.msg("<red>${target.name} doesn't have ${questEntry.id} active, skipping track command.</red>")
                    }
                    plugin.logger.fine(
                        "[QuestCodex] Ignoring quest track request for ${target.name} because ${questEntry.id} is not active."
                    )
                    return@executePlayerOrTarget
                }
                target.trackQuest(questEntry.ref())
                plugin.logger.fine("[QuestCodex] ${target.name} is now tracking ${questEntry.id} via command.")
            }
        }
    }
    literal("untrack") {
        withPermission("typewriter.quest.untrack")
        executePlayerOrTarget { target ->
            target.unTrackQuest()
            plugin.logger.fine("[QuestCodex] ${target.name} stopped tracking their quest via command.")
        }
    }
}

