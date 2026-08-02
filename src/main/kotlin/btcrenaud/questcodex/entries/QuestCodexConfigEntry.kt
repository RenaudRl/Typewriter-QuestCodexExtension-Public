package btcrenaud.questcodex.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.engine.paper.entry.ManifestEntry
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.utils.DefaultSoundId
import com.typewritermc.engine.paper.utils.Sound

@Entry("quest_codex", "Quest Codex Global Settings", Colors.YELLOW, "mdi:book-open-variant")
class QuestCodexConfigEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Sound played when a menu is opened.")
    val soundOnOpen: Sound = defaultSound("minecraft:item.book.page_turn"),
    @Help("Sound played when switching between categories/menus.")
    val soundOnSwitch: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    @Help("Sound played when clicking a navigation button.")
    val soundOnClick: Sound = defaultSound("minecraft:item.flintandsteel.use"),
    @Help("Sound played when tracking a quest.")
    val soundOnTrack: Sound = defaultSound("minecraft:entity.arrow.hit_player"),
    @Help("Sound played when untracking a quest.")
    val soundOnUntrack: Sound = defaultSound("minecraft:entity.arrow.hit_player"),
    @Help("Default number of inventory rows for category menus (3-6).")
    val defaultRows: Int = 4,
    @Help("Allow QuestCodex to track several active quests while the official Quest tracker displays the most recently selected one.")
    val multiTrackingEnabled: Boolean = true,
    @Help("Maximum number of quests tracked simultaneously by QuestCodex.")
    val maxTrackedQuests: Int = 5,
    @Help("Artifact used to persist each player's tracked quest ids and their tracking order.")
    val trackingArtifact: Ref<QuestCodexTrackingArtifactEntry> = emptyRef(),
    @Help("Enable safe recovery of active Typewriter dialogues and cinematics after a disconnect.")
    val recoveryEnabled: Boolean = true,
    @Help("Artifact used to persist active dialogue and cinematic recovery snapshots.")
    val recoveryArtifact: Ref<QuestCodexRecoveryArtifactEntry> = emptyRef(),
    @Help("How long an interrupted interaction remains restorable, in seconds.")
    val recoveryRetentionSeconds: Long = 120,
    @Help("Ticks to wait after join before restoring an interrupted interaction.")
    val recoveryRestoreDelayTicks: Long = 2,

    @Help("Message shown when a player starts tracking a quest. Placeholders: {quest}")
    @Placeholder @Colored
    val nowTrackingMessage: String = "<green>Now tracking: {quest}</green>",
    @Help("Message shown when a player stops tracking a quest. Placeholders: {quest}")
    @Placeholder @Colored
    val stoppedTrackingMessage: String = "<gray>No longer tracking: {quest}</gray>",
    @Help("Message shown when clicking an inactive quest. Placeholders: {quest}")
    @Placeholder @Colored
    val questInactiveMessage: String = "<gold>{quest} — Start this quest!</gold>",
    @Help("Message shown when clicking a completed quest. Placeholders: {quest}")
    @Placeholder @Colored
    val questCompletedMessage: String = "<green>{quest} — Completed!</green>",
    @Help("Lore line showing category progress. Placeholders: {completed}, {total}")
    @Placeholder @Colored
    val categoryProgressMessage: String = "<gray>{completed}/{total} quests</gray>",
    @Help("Lore hint telling the player to click to see quests.")
    @Placeholder @Colored
    val categoryClickHint: String = "<yellow>Click to view quests</yellow>",
    @Help("Lore hint telling the player to click to track/un-track this quest.")
    @Placeholder @Colored
    val questTrackHint: String = "<yellow>Click to track</yellow>",
    @Help("Lore hint shown for a quest already tracked by QuestCodex.")
    @Placeholder @Colored
    val questUntrackHint: String = "<yellow>Click to stop tracking</yellow>",
    @Help("Message shown when the simultaneous tracking limit is reached. Placeholder: {max}.")
    @Placeholder @Colored
    val trackingLimitMessage: String = "<red>You can track at most {max} quests.</red>",
    @Help("Entry triggered to open the main menu (e.g. an open_gui entry) when no category_menu with an empty category exists. Used by /tw codex and the BACK button.")
    val mainMenuTrigger: Ref<TriggerableEntry> = emptyRef(),
) : ManifestEntry

object QuestCodexConfig {
    var soundOnOpen: Sound = defaultSound("minecraft:item.book.page_turn")
        private set
    var soundOnSwitch: Sound = defaultSound("minecraft:item.flintandsteel.use")
        private set
    var soundOnClick: Sound = defaultSound("minecraft:item.flintandsteel.use")
        private set
    var soundOnTrack: Sound = defaultSound("minecraft:entity.arrow.hit_player")
        private set
    var soundOnUntrack: Sound = defaultSound("minecraft:entity.arrow.hit_player")
        private set
    var defaultRows: Int = 4
        private set
    var multiTrackingEnabled: Boolean = true
        private set
    var maxTrackedQuests: Int = 5
        private set
    var trackingArtifact: Ref<QuestCodexTrackingArtifactEntry> = emptyRef()
        private set
    var recoveryEnabled: Boolean = true
        private set
    var recoveryArtifact: Ref<QuestCodexRecoveryArtifactEntry> = emptyRef()
        private set
    var recoveryRetentionSeconds: Long = 120
        private set
    var recoveryRestoreDelayTicks: Long = 2
        private set

    var nowTrackingMessage: String = "<green>Now tracking: {quest}</green>"
        private set
    var stoppedTrackingMessage: String = "<gray>No longer tracking: {quest}</gray>"
        private set
    var questInactiveMessage: String = "<gold>{quest} — Start this quest!</gold>"
        private set
    var questCompletedMessage: String = "<green>{quest} — Completed!</green>"
        private set
    var categoryProgressMessage: String = "<gray>{completed}/{total} quests</gray>"
        private set
    var categoryClickHint: String = "<yellow>Click to view quests</yellow>"
        private set
    var questTrackHint: String = "<yellow>Click to track</yellow>"
        private set
    var questUntrackHint: String = "<yellow>Click to stop tracking</yellow>"
        private set
    var trackingLimitMessage: String = "<red>You can track at most {max} quests.</red>"
        private set
    var mainMenuTrigger: Ref<TriggerableEntry> = emptyRef()
        private set

    fun reset() {
        soundOnOpen = defaultSound("minecraft:item.book.page_turn")
        soundOnSwitch = defaultSound("minecraft:item.flintandsteel.use")
        soundOnClick = defaultSound("minecraft:item.flintandsteel.use")
        soundOnTrack = defaultSound("minecraft:entity.arrow.hit_player")
        soundOnUntrack = defaultSound("minecraft:entity.arrow.hit_player")
        defaultRows = 4
        multiTrackingEnabled = true
        maxTrackedQuests = 5
        trackingArtifact = emptyRef()
        recoveryEnabled = true
        recoveryArtifact = emptyRef()
        recoveryRetentionSeconds = 120
        recoveryRestoreDelayTicks = 2
        nowTrackingMessage = "<green>Now tracking: {quest}</green>"
        stoppedTrackingMessage = "<gray>No longer tracking: {quest}</gray>"
        questInactiveMessage = "<gold>{quest} — Start this quest!</gold>"
        questCompletedMessage = "<green>{quest} — Completed!</green>"
        categoryProgressMessage = "<gray>{completed}/{total} quests</gray>"
        categoryClickHint = "<yellow>Click to view quests</yellow>"
        questTrackHint = "<yellow>Click to track</yellow>"
        questUntrackHint = "<yellow>Click to stop tracking</yellow>"
        trackingLimitMessage = "<red>You can track at most {max} quests.</red>"
        mainMenuTrigger = emptyRef()
    }

    fun apply(entry: QuestCodexConfigEntry) {
        soundOnOpen = entry.soundOnOpen
        soundOnSwitch = entry.soundOnSwitch
        soundOnClick = entry.soundOnClick
        soundOnTrack = entry.soundOnTrack
        soundOnUntrack = entry.soundOnUntrack
        defaultRows = entry.defaultRows.coerceIn(3, 6)
        multiTrackingEnabled = entry.multiTrackingEnabled
        maxTrackedQuests = entry.maxTrackedQuests.coerceAtLeast(1)
        trackingArtifact = entry.trackingArtifact
        recoveryEnabled = entry.recoveryEnabled
        recoveryArtifact = entry.recoveryArtifact
        recoveryRetentionSeconds = entry.recoveryRetentionSeconds.coerceIn(10, 86_400)
        recoveryRestoreDelayTicks = entry.recoveryRestoreDelayTicks.coerceIn(1, 20)
        nowTrackingMessage = entry.nowTrackingMessage
        stoppedTrackingMessage = entry.stoppedTrackingMessage
        questInactiveMessage = entry.questInactiveMessage
        questCompletedMessage = entry.questCompletedMessage
        categoryProgressMessage = entry.categoryProgressMessage
        categoryClickHint = entry.categoryClickHint
        questTrackHint = entry.questTrackHint
        questUntrackHint = entry.questUntrackHint
        trackingLimitMessage = entry.trackingLimitMessage
        mainMenuTrigger = entry.mainMenuTrigger
    }
}

private fun defaultSound(id: String): Sound = Sound(DefaultSoundId(id))
