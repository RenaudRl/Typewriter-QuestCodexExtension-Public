package btc.renaud.questcodex

import com.typewritermc.core.entries.ref
import com.typewritermc.quest.isQuestTracked
import com.typewritermc.quest.trackQuest
import com.typewritermc.quest.unTrackQuest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

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
                            if (codexSoundButtonPrevious.isNotBlank()) player.playCodexSound(codexSoundButtonPrevious)
                            if (codexSoundMenuSwitch.isNotBlank()) player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                    holder.nextSlot -> {
                        val maxPage = (holder.filteredQuestsCount - 1) / holder.maxQuestsPerPage
                        if (holder.currentPage < maxPage) {
                            holder.loadPage(holder.currentPage + 1)
                            player.openInventory(holder.getInventory())
                            if (codexSoundButtonNext.isNotBlank()) player.playCodexSound(codexSoundButtonNext)
                            if (codexSoundMenuSwitch.isNotBlank()) player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                    holder.sortSlot -> {
                        holder.sort = holder.sort.next()
                        holder.loadPage(0)
                        player.openInventory(holder.getInventory())
                        if (codexSoundButtonSort.isNotBlank()) player.playCodexSound(codexSoundButtonSort)
                        if (codexSoundMenuSwitch.isNotBlank()) player.playCodexSound(codexSoundMenuSwitch)
                    }
                    holder.backSlot -> {
                        val parent = holder.category.parent
                        player.openInventory(QuestCategoryMainInventory(player, parent).getInventory())
                        if (codexSoundButtonBack.isNotBlank()) player.playCodexSound(codexSoundButtonBack)
                        if (codexSoundMenuSwitch.isNotBlank()) player.playCodexSound(codexSoundMenuSwitch)
                    }
                    else -> {
                        val quest = holder.questForSlot(event.rawSlot) ?: return
                        if (event.isLeftClick) {
                            val ref = quest.ref()
                            if (player isQuestTracked ref) {
                                player.unTrackQuest()
                                if (codexSoundButtonQuestUntrack.isNotBlank()) player.playCodexSound(codexSoundButtonQuestUntrack)
                            } else {
                                player.trackQuest(ref)
                                if (codexSoundButtonQuestTrack.isNotBlank()) player.playCodexSound(codexSoundButtonQuestTrack)
                            }
                            holder.loadPage(holder.currentPage)
                            player.openInventory(holder.getInventory())
                            if (codexSoundMenuSwitch.isNotBlank()) player.playCodexSound(codexSoundMenuSwitch)
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
                            if (codexSoundButtonPrevious.isNotBlank()) player.playCodexSound(codexSoundButtonPrevious)
                            if (codexSoundMenuSwitch.isNotBlank()) player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                    holder.nextSlot() -> {
                        if (holder.currentPage < holder.maxPage()) {
                            holder.loadPage(holder.currentPage + 1)
                            player.openInventory(holder.getInventory())
                            if (codexSoundButtonNext.isNotBlank()) player.playCodexSound(codexSoundButtonNext)
                            if (codexSoundMenuSwitch.isNotBlank()) player.playCodexSound(codexSoundMenuSwitch)
                        }
                    }
                    holder.backSlot() -> {
                        val parent = holder.parent()
                        player.openInventory(QuestCategoryMainInventory(player, parent?.parent).getInventory())
                        if (codexSoundButtonBack.isNotBlank()) player.playCodexSound(codexSoundButtonBack)
                        if (codexSoundMenuSwitch.isNotBlank()) player.playCodexSound(codexSoundMenuSwitch)
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
                        if (codexSoundButtonCategory.isNotBlank()) player.playCodexSound(codexSoundButtonCategory)
                        if (codexSoundMenuOpen.isNotBlank()) player.playCodexSound(codexSoundMenuOpen)
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