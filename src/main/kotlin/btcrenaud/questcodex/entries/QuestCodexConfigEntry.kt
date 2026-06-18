package btcrenaud.questcodex.entries

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Placeholder
import com.typewritermc.core.extension.annotations.Colored
import com.typewritermc.engine.paper.entry.ManifestEntry
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

    @Help("Message shown when a player starts tracking a quest. Placeholders: {quest}")
    @Placeholder @Colored
    val nowTrackingMessage: String = "<green>Tu suis maintenant : {quest}</green>",
    @Help("Message shown when a player stops tracking a quest. Placeholders: {quest}")
    @Placeholder @Colored
    val stoppedTrackingMessage: String = "<gray>Tu ne suis plus : {quest}</gray>",
    @Help("Message shown when clicking an inactive quest. Placeholders: {quest}")
    @Placeholder @Colored
    val questInactiveMessage: String = "<gold>{quest} — Commence cette quete !</gold>",
    @Help("Message shown when clicking a completed quest. Placeholders: {quest}")
    @Placeholder @Colored
    val questCompletedMessage: String = "<green>{quest} — Completee !</green>",
    @Help("Lore line showing category progress. Placeholders: {completed}, {total}")
    @Placeholder @Colored
    val categoryProgressMessage: String = "<gray>{completed}/{total} quetes</gray>",
    @Help("Lore hint telling the player to click to see quests.")
    @Placeholder @Colored
    val categoryClickHint: String = "<yellow>Clique pour voir les quetes</yellow>",
    @Help("Lore hint telling the player to click to track/un-track this quest.")
    @Placeholder @Colored
    val questTrackHint: String = "<yellow>Clique pour suivre</yellow>",
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

    var nowTrackingMessage: String = "<green>Tu suis maintenant : {quest}</green>"
        private set
    var stoppedTrackingMessage: String = "<gray>Tu ne suis plus : {quest}</gray>"
        private set
    var questInactiveMessage: String = "<gold>{quest} — Commence cette quete !</gold>"
        private set
    var questCompletedMessage: String = "<green>{quest} — Completee !</green>"
        private set
    var categoryProgressMessage: String = "<gray>{completed}/{total} quetes</gray>"
        private set
    var categoryClickHint: String = "<yellow>Clique pour voir les quetes</yellow>"
        private set
    var questTrackHint: String = "<yellow>Clique pour suivre</yellow>"
        private set

    fun reset() {
        soundOnOpen = defaultSound("minecraft:item.book.page_turn")
        soundOnSwitch = defaultSound("minecraft:item.flintandsteel.use")
        soundOnClick = defaultSound("minecraft:item.flintandsteel.use")
        soundOnTrack = defaultSound("minecraft:entity.arrow.hit_player")
        soundOnUntrack = defaultSound("minecraft:entity.arrow.hit_player")
        defaultRows = 4
        nowTrackingMessage = "<green>Tu suis maintenant : {quest}</green>"
        stoppedTrackingMessage = "<gray>Tu ne suis plus : {quest}</gray>"
        questInactiveMessage = "<gold>{quest} — Commence cette quete !</gold>"
        questCompletedMessage = "<green>{quest} — Completee !</green>"
        categoryProgressMessage = "<gray>{completed}/{total} quetes</gray>"
        categoryClickHint = "<yellow>Clique pour voir les quetes</yellow>"
        questTrackHint = "<yellow>Clique pour suivre</yellow>"
    }

    fun apply(entry: QuestCodexConfigEntry) {
        soundOnOpen = entry.soundOnOpen
        soundOnSwitch = entry.soundOnSwitch
        soundOnClick = entry.soundOnClick
        soundOnTrack = entry.soundOnTrack
        soundOnUntrack = entry.soundOnUntrack
        defaultRows = entry.defaultRows.coerceIn(3, 6)
        nowTrackingMessage = entry.nowTrackingMessage
        stoppedTrackingMessage = entry.stoppedTrackingMessage
        questInactiveMessage = entry.questInactiveMessage
        questCompletedMessage = entry.questCompletedMessage
        categoryProgressMessage = entry.categoryProgressMessage
        categoryClickHint = entry.categoryClickHint
        questTrackHint = entry.questTrackHint
    }
}

private fun defaultSound(id: String): Sound = Sound(DefaultSoundId(id))
