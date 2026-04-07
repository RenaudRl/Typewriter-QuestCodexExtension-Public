package btc.renaud.questcodex

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.*
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.utils.item.CustomItem
import com.typewritermc.engine.paper.utils.item.Item
import com.typewritermc.engine.paper.entry.entries.ActionEntry

@Entry(
    "prologue_replay_category",
    "Groups cinematics for replay in the Quest Codex",
    Colors.RED,
    "mdi:movie-roll"
)
class PrologueReplayCategoryEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Title of the replay category")
    @Placeholder
    @Colored
    val title: String = "",
    @Help("Item used as the category icon")
    val item: Item = CustomItem(),
    @Help("Order in the replay menu")
    val order: Int = 0,
    @Help("Explicit slot in the replay menu")
    val slot: Int? = null,
    @Help("Quest category associated with this replay category")
    val questCategory: String = "",
    @Help("Individual cinematics available in this category")
    val entries: List<PrologueReplayEntryData> = emptyList(),
) : ManifestEntry

data class PrologueReplayEntryData(
    @Help("ID of the entry")
    val id: String = "",
    @Help("Human readable name")
    val name: String = "",
    @Help("Cinematic to replay. This must be an action entry tagged with 'cinematic'.")
    @OnlyTags("cinematic")
    val cinematic: Ref<ActionEntry> = emptyRef(),
    @Help("Slot in the menu (0-indexed). Use -1 for auto-layout.")
    val slot: Int = -1,
    @Help("Criteria required to unlock this replay")
    val unlockCriteria: List<Criteria> = emptyList(),
    @Help("Criteria required to mark this replay as fully completed (e.g. quest finished)")
    val completeCriteria: List<Criteria> = emptyList(),
    @Help("Item meta for the entry when locked")
    val lockedName: String = "",
    val lockedLore: List<String> = emptyList(),
    val lockedItem: Item = CustomItem(),
    @Help("Item meta for the entry when unlocked")
    val unlockedName: String = "",
    val unlockedLore: List<String> = emptyList(),
    val unlockedItem: Item = CustomItem(),
    @Help("Item meta for the entry when completed")
    val completedName: String = "",
    val completedLore: List<String> = emptyList(),
    val completedItem: Item = CustomItem(),
)
