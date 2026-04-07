package btc.renaud.questcodex

import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.engine.paper.command.dsl.*
import com.typewritermc.engine.paper.utils.msg
import org.bukkit.entity.Player

/**
 * Provides a single /questcodex command that can open either the main menu
 * or a specific quest category.
 */
@TypewriterCommand
fun CommandTree.questCodexCommand() = literal("questcodex") {
    // /questcodex -> open main category menu
    executes {
        val player = sender as? Player
        if (player == null) {
            sender.msg("<red>Only players can open quest categories")
            return@executes
        }
        player.openInventory(QuestCategoryMainInventory(player).getInventory())
        player.playCodexSound(codexSoundMenuOpen)
    }

    // /questcodex replay [category]
    literal("replay") {
        executes {
            val player = sender as? Player
            if (player == null) {
                sender.msg("<red>Only players can open replay menus")
                return@executes
            }
            player.openInventory(PrologueReplayInventory(player).getInventory())
            player.playCodexSound(codexSoundMenuOpen)
        }

        string("category") { category ->
            executes {
                val player = sender as? Player
                if (player == null) {
                    sender.msg("<red>Only players can open replay menus")
                    return@executes
                }
                val name = category()
                val replayCategory = PrologueReplayRegistry.find(name)
                if (replayCategory == null) {
                    sender.msg("<red>Unknown replay category: $name")
                    return@executes
                }
                player.openInventory(PrologueReplayInventory(player, replayCategory).getInventory())
                player.playCodexSound(codexSoundMenuOpen)
            }
        }
    }

    // /questcodex <category> [sort]
    string("category") { category ->
        executes {
            val name = category()
            val questCategory = QuestCategoryRegistry.find(name)
            if (questCategory == null) {
                sender.msg("<red>Unknown quest category: $name")
                return@executes
            }

            val player = sender as? Player
            if (player == null) {
                sender.msg("<red>Only players can open quest categories")
                return@executes
            }

            openQuestCodexMenu(player, questCategory)
            player.playCodexSound(codexSoundMenuOpen)
        }

        string("sort") { sort ->
            executes {
                val name = category()
                val questCategory = QuestCategoryRegistry.find(name)
                if (questCategory == null) {
                    sender.msg("<red>Unknown quest category: $name")
                    return@executes
                }

                val player = sender as? Player
                if (player == null) {
                    sender.msg("<red>Only players can open quest categories")
                    return@executes
                }

                val option = try {
                    QuestCategoryInventory.SortOption.valueOf(sort().uppercase())
                } catch (_: IllegalArgumentException) {
                    sender.msg("<red>Unknown sort option: ${sort()}")
                    return@executes
                }

                openQuestCodexMenu(player, questCategory, option)
                player.playCodexSound(codexSoundMenuOpen)
            }
        }
    }
}
