package btc.renaud.questcodex

import com.typewritermc.core.entries.ref
import com.typewritermc.engine.paper.extensions.placeholderapi.parsePlaceholders
import com.typewritermc.engine.paper.plugin
import com.typewritermc.quest.QuestStatus
import com.typewritermc.quest.isQuestTracked
import com.typewritermc.quest.trackQuest
import com.typewritermc.quest.unTrackQuest
import com.typewritermc.core.interaction.context
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.quest.entries.questShowingObjectives
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import java.util.logging.Level

/**
 * Helper function to open the appropriate quest category menu based on its content.
 */
fun openQuestCodexMenu(
    player: Player,
    category: QuestCategory?,
    sort: QuestCategoryInventory.SortOption = QuestCategoryInventory.SortOption.ALL
) {
    if (category == null) {
        player.openInventory(QuestCategoryMainInventory(player).getInventory())
        return
    }

    val showQuests = category.showQuestButton && category.quests.isNotEmpty()
    
    val nextInventory = if (showQuests) {
        QuestCategoryInventory(player, category, sort).getInventory()
    } else {
        QuestCategoryMainInventory(player, category).getInventory()
    }
    player.openInventory(nextInventory)
}

/** Handles interactions with quest category inventories. */
class QuestCategoryListener : Listener {
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder ?: return

        try {
            if (event.action == InventoryAction.COLLECT_TO_CURSOR &&
                (holder is QuestCategoryInventory || holder is QuestCategoryMainInventory || holder is PrologueReplayInventory)
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
                            if (holder.menuConfig.buttons.previous.enabled && holder.currentPage > 0) {
                                holder.loadPage(holder.currentPage - 1)
                                player.openInventory(holder.getInventory())
                                player.playCodexSound(codexSoundButtonPrevious)
                                player.playCodexSound(codexSoundMenuSwitch)
                            }
                        }
                        holder.nextSlot -> {
                            if (holder.menuConfig.buttons.next.enabled) {
                                val maxPage = (holder.filteredQuestsCount - 1) / holder.maxQuestsPerPage
                                if (holder.currentPage < maxPage) {
                                    holder.loadPage(holder.currentPage + 1)
                                    player.openInventory(holder.getInventory())
                                    player.playCodexSound(codexSoundButtonNext)
                                    player.playCodexSound(codexSoundMenuSwitch)
                                }
                            }
                        }
                        holder.sortSlot -> {
                            if (holder.menuConfig.buttons.sort.enabled) {
                                holder.sort = holder.sort.next()
                                holder.loadPage(0)
                                player.openInventory(holder.getInventory())
                                player.playCodexSound(codexSoundButtonSort)
                                player.playCodexSound(codexSoundMenuSwitch)
                            }
                        }
                        holder.backSlot -> {
                            if (holder.menuConfig.buttons.back.enabled) {
                                val parent = holder.category.parent
                                openQuestCodexMenu(player, parent)
                                player.playCodexSound(codexSoundButtonBack)
                                player.playCodexSound(codexSoundMenuSwitch)
                            }
                        }
                        holder.replaySlot -> {
                            val replayConfig = QuestCodexConfig.replayMenu
                            if (replayConfig.enabled && replayConfig.replayButton.enabled) {
                                val replayCategory = PrologueReplayRegistry.findByQuestCategory(holder.category.name)
                                player.openInventory(PrologueReplayInventory(player, replayCategory).getInventory())
                                player.playCodexSound(codexSoundButtonCategory)
                                player.playCodexSound(codexSoundMenuOpen)
                            }
                        }
                        else -> {
                            val quest = holder.questForSlot(event.rawSlot)
                            if (quest != null) {
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
                                } else if (event.isLeftClick && event.isShiftClick) {
                                    // Handle Shift + Left Click from Quest+
                                    val ref = quest.ref()
                                    if (quest.questStatus(player) == QuestStatus.ACTIVE && player isQuestTracked ref) {
                                        val objectives = player.questShowingObjectives(ref)
                                        val triggered = objectives.any { QuestPlusIntegration.triggerGpsShiftClick(it, player) }

                                        if (triggered) {
                                            player.playCodexSound(codexSoundButtonQuestTrack)
                                            holder.loadPage(holder.currentPage)
                                            player.openInventory(holder.getInventory())
                                        }
                                    }
                                }
                                return
                            }

                            val subCategory = holder.categoryForSlot(event.rawSlot) ?: return
                            if (subCategory.categoryStatus(player) == CategoryStatus.BLOCKED) return

                            openQuestCodexMenu(player, subCategory)
                            player.playCodexSound(codexSoundButtonCategory)
                            player.playCodexSound(codexSoundMenuOpen)
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
                            if (holder.menuConfig.previousButton.enabled && holder.currentPage > 0) {
                                holder.loadPage(holder.currentPage - 1)
                                player.openInventory(holder.getInventory())
                                player.playCodexSound(codexSoundButtonPrevious)
                                player.playCodexSound(codexSoundMenuSwitch)
                            }
                        }
                        holder.nextSlot() -> {
                            if (holder.menuConfig.nextButton.enabled && holder.currentPage < holder.maxPage()) {
                                holder.loadPage(holder.currentPage + 1)
                                player.openInventory(holder.getInventory())
                                player.playCodexSound(codexSoundButtonNext)
                                player.playCodexSound(codexSoundMenuSwitch)
                            }
                        }
                        holder.backSlot() -> {
                            if (holder.menuConfig.backButton?.enabled == true) {
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
                                openQuestCodexMenu(player, parent?.parent)
                                player.playCodexSound(codexSoundButtonBack)
                                player.playCodexSound(codexSoundMenuSwitch)
                            }
                        }
                        holder.replaySlot() -> {
                            val replayConfig = QuestCodexConfig.replayMenu
                            if (replayConfig.enabled && replayConfig.replayButton.enabled) {
                                val parent = holder.parent()
                                val replayCategory = parent?.let { PrologueReplayRegistry.findByQuestCategory(it.name) }
                                player.openInventory(PrologueReplayInventory(player, replayCategory).getInventory())
                                player.playCodexSound(codexSoundButtonCategory)
                                player.playCodexSound(codexSoundMenuOpen)
                            }
                        }
                        holder.infoSlot() -> {
                            // No action for info button
                        }
                        else -> {
                            val category = holder.slots[event.rawSlot] ?: return
                            if (category.categoryStatus(player) == CategoryStatus.BLOCKED) {
                                return
                            }
                            
                            val showCategories = category.showCategoriesButton && category.subCategories.isNotEmpty()
                            val showQuests = category.showQuestButton
                            val showPrologue = category.showPrologueButton
                            
                            openQuestCodexMenu(player, category)
                            player.playCodexSound(codexSoundButtonCategory)
                            player.playCodexSound(codexSoundMenuOpen)
                        }
                    }
                }

                is PrologueReplayInventory -> {
                    if (event.clickedInventory != event.view.bottomInventory || event.isShiftClick) {
                        event.isCancelled = true
                    }
                    if (event.clickedInventory != event.view.topInventory) return
                    holder.handleInteraction(event.rawSlot)
                    player.playCodexSound(codexSoundButtonCategory)
                }
                else -> return
            }
        } catch (_: NoClassDefFoundError) {
            // Ignore if inventory classes are not loaded
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder ?: return
        try {
            if (holder is QuestCategoryInventory || holder is QuestCategoryMainInventory || holder is PrologueReplayInventory) {
                event.isCancelled = true
            }
        } catch (_: NoClassDefFoundError) {
            // Ignore if classes are not loaded
        }
    }
}
