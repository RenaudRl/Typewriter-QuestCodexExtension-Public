package btc.renaud.questcodex

import com.typewritermc.core.entries.ref
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.plugin
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.isQuestTracked
import com.typewritermc.quest.trackQuest
import com.typewritermc.quest.unTrackQuest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import java.util.logging.Level

/** Handles interactions with quest category inventories. */
class QuestCategoryListener : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder

        if (event.action == InventoryAction.COLLECT_TO_CURSOR &&
            (holder is QuestCategoryInventory || holder is QuestCategoryMainInventory)
        ) {
            event.isCancelled = true
            return
        }

        when (holder) {
            is QuestCategoryInventory -> {
                // Cancel all interactions with the menu inventory and shift-clicks from the player inventory
                if (event.clickedInventory != event.view.bottomInventory || event.isShiftClick) {
                    event.isCancelled = true
                }

                if (event.clickedInventory != event.view.topInventory) return

                when (event.rawSlot) {
                    holder.previousSlot -> {
                        if (holder.currentPage > 0) {
                            holder.loadPage(holder.currentPage - 1)
                            player.openInventory(holder.getInventory())
                            player.playCodexSound(codexSoundButtonPrevious)
                            player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                    holder.nextSlot -> {
                        val maxPage = (holder.filteredQuestsCount - 1) / holder.maxQuestsPerPage
                        if (holder.currentPage < maxPage) {
                            holder.loadPage(holder.currentPage + 1)
                            player.openInventory(holder.getInventory())
                            player.playCodexSound(codexSoundButtonNext)
                            player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                    holder.sortSlot -> {
                        holder.sort = holder.sort.next()
                        holder.loadPage(0)
                        player.openInventory(holder.getInventory())
                        player.playCodexSound(codexSoundButtonSort)
                        player.playCodexSound(codexSoundMenuSwitch)
                    }
                    holder.backSlot -> {
                        val parent = holder.category.parent
                        player.openInventory(QuestCategoryMainInventory(player, parent).getInventory())
                        player.playCodexSound(codexSoundButtonBack)
                        player.playCodexSound(codexSoundMenuSwitch)
                    }
                    else -> {
                        val quest = holder.questForSlot(event.rawSlot) ?: return
                        if (event.isLeftClick) {
                            val ref = quest.ref()
                            val status = quest.questStatus(player)
                            if (status != QuestStatus.ACTIVE) {
                                return
                            }
                            if (player isQuestTracked ref) {
                                player.unTrackQuest()
                                player.playCodexSound(codexSoundButtonQuestUntrack)
                            } else {
                                player.trackQuest(ref)
                                player.playCodexSound(codexSoundButtonQuestTrack)
                            }
                            holder.loadPage(holder.currentPage)
                            player.openInventory(holder.getInventory())
                            player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                }
            }

            is QuestCategoryMainInventory -> {
                if (event.clickedInventory != event.view.bottomInventory || event.isShiftClick) {
                    event.isCancelled = true
                }

                if (event.clickedInventory != event.view.topInventory) return

                when (event.rawSlot) {
                    holder.previousSlot() -> {
                        if (holder.currentPage > 0) {
                            holder.loadPage(holder.currentPage - 1)
                            player.openInventory(holder.getInventory())
                            player.playCodexSound(codexSoundButtonPrevious)
                            player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                    holder.nextSlot() -> {
                        if (holder.currentPage < holder.maxPage()) {
                            holder.loadPage(holder.currentPage + 1)
                            player.openInventory(holder.getInventory())
                            player.playCodexSound(codexSoundButtonNext)
                            player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                    holder.backSlot() -> {
                        val commands = holder.backCommands()
                            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                        if (commands.isNotEmpty()) {
                            player.closeInventory()
                            commands.forEach { rawCommand ->
                                val parsed = rawCommand.parsePlaceholders(player)
                                val normalized = parsed.removePrefix("/").trim()
                                if (normalized.isEmpty()) return@forEach
                                val executed = player.performCommand(normalized)
                                if (!executed) {
                                    plugin.logger.log(
                                        Level.WARNING,
                                        "[QuestCodex] Failed to execute back button command '$normalized' for player ${player.name}."
                                    )
                                }
                            }
                            player.playCodexSound(codexSoundButtonBack)
                            player.playCodexSound(codexSoundMenuSwitch)
                            return
                        }
                        val parent = holder.parent()
                        player.openInventory(QuestCategoryMainInventory(player, parent?.parent).getInventory())
                        player.playCodexSound(codexSoundButtonBack)
                        player.playCodexSound(codexSoundMenuSwitch)
                    }
                    holder.infoSlot() -> {
                        // No action for info button
                    }
                    else -> {
                        val category = holder.slots[event.rawSlot] ?: return
                        if (category.categoryStatus(player) == CategoryStatus.BLOCKED) {
                            return
                        }
                        if (category.subCategories.isNotEmpty()) {
                            player.openInventory(QuestCategoryMainInventory(player, category).getInventory())
                        } else {
                            player.openInventory(QuestCategoryInventory(player, category).getInventory())
                        }
                        player.playCodexSound(codexSoundButtonCategory)
                        player.playCodexSound(codexSoundMenuOpen)
                    }
                }
            }

            else -> return
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder
        if (holder is QuestCategoryInventory || holder is QuestCategoryMainInventory) {
            event.isCancelled = true
        }
    }
}