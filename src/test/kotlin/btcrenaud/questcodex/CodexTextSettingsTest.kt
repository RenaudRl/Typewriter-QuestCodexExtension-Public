package btcrenaud.questcodex

import btcrenaud.questcodex.entries.CodexNavLabels
import btcrenaud.questcodex.entries.CodexSortTexts
import btcrenaud.questcodex.entries.SortModeConfig
import btcrenaud.questcodex.navigation.CodexNavAction
import kotlin.test.Test
import kotlin.test.assertEquals

/** Guards the texts of the built-in buttons: each action reads its own setting, defaults unchanged. */
class CodexTextSettingsTest {

    @Test
    fun `nav labels start as the extension always showed them`() {
        val labels = CodexNavLabels()

        val shown = CodexNavAction.entries.associateWith { labels.labelFor(it) }

        assertEquals(
            mapOf(
                CodexNavAction.PAGE_NEXT to "<yellow>Next",
                CodexNavAction.PAGE_PREV to "<yellow>Previous",
                CodexNavAction.SCROLL_UP to "<white>Up",
                CodexNavAction.SCROLL_DOWN to "<white>Down",
                CodexNavAction.SCROLL_LEFT to "<white>Left",
                CodexNavAction.SCROLL_RIGHT to "<white>Right",
                CodexNavAction.BACK to "<red>Back",
                CodexNavAction.CLOSE to "<red>Close",
                CodexNavAction.SORT to "<yellow>Sort",
            ),
            shown,
        )
    }

    @Test
    fun `an edited nav label reaches only its own action`() {
        val labels = CodexNavLabels(close = "<gray>Fermer")

        assertEquals("<gray>Fermer", labels.labelFor(CodexNavAction.CLOSE))
        assertEquals("<red>Back", labels.labelFor(CodexNavAction.BACK))
    }

    @Test
    fun `sort labels start as the extension always showed them`() {
        val texts = CodexSortTexts()

        assertEquals("<yellow>📋 All quests", texts.labelFor(SortModeConfig.ALL))
        assertEquals("<white>📋 Not started", texts.labelFor(SortModeConfig.NOT_STARTED))
        assertEquals("<green>📋 In progress", texts.labelFor(SortModeConfig.ACTIVE))
        assertEquals("<gray>📋 Completed", texts.labelFor(SortModeConfig.COMPLETED))
        assertEquals("<gray>Click to change sorting", texts.hint)
    }

    @Test
    fun `every sort mode reads a different setting`() {
        val texts = CodexSortTexts(allLabel = "a", notStartedLabel = "b", activeLabel = "c", completedLabel = "d")

        val shown = SortModeConfig.entries.map { texts.labelFor(it) }

        assertEquals(listOf("a", "b", "c", "d"), shown)
    }
}
