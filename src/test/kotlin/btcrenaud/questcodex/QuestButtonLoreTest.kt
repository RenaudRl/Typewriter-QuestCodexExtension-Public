package btcrenaud.questcodex

import btcrenaud.questcodex.entries.QuestCodexConfigEntry
import btcrenaud.questcodex.entries.QuestLoreDefaults
import com.typewritermc.quest.QuestStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Guards which lines a quest button shows, from the defaults, the overrides and the active objectives. */
class QuestButtonLoreTest {

    private val enabled = QuestLoreDefaults(
        enabled = true,
        notStartedMessage = "not started",
        objectivesHeader = "header",
        completedMessage = "completed",
    )

    private fun override(lore: List<String> = emptyList(), hideObjectives: Boolean = false) =
        QuestStateDisplayOverride(lore = lore, hideObjectives = hideObjectives)

    @Test
    fun `disabled defaults keep the 2_17_2 lore`() {
        val lines = QuestButtonLore.compose(
            QuestStatus.ACTIVE, QuestLoreDefaults(), override(listOf("a")), listOf("b"), listOf("o"),
        )

        assertEquals(listOf("a", "b"), lines)
    }

    @Test
    fun `active quest lists header then objectives before additional lore`() {
        val lines = QuestButtonLore.compose(QuestStatus.ACTIVE, enabled, null, listOf("b"), listOf("o1", "o2"))

        assertEquals(listOf("header", "o1", "o2", "b"), lines)
    }

    @Test
    fun `not started and completed show their message`() {
        assertEquals(listOf("not started"), QuestButtonLore.compose(QuestStatus.INACTIVE, enabled, null, emptyList(), emptyList()))
        assertEquals(listOf("completed"), QuestButtonLore.compose(QuestStatus.COMPLETED, enabled, null, emptyList(), emptyList()))
    }

    @Test
    fun `assignment lore replaces the default message`() {
        val lines = QuestButtonLore.compose(QuestStatus.INACTIVE, enabled, override(listOf("custom")), emptyList(), emptyList())

        assertEquals(listOf("custom"), lines)
    }

    @Test
    fun `hideObjectives removes the list and empties the token`() {
        val hidden = override(hideObjectives = true)

        assertEquals(emptyList(), QuestButtonLore.compose(QuestStatus.ACTIVE, enabled, hidden, emptyList(), listOf("o")))
        assertEquals(
            listOf("before"),
            QuestButtonLore.compose(QuestStatus.ACTIVE, enabled, hidden, listOf("before", "<objectives>"), listOf("o")),
        )
    }

    @Test
    fun `token is replaced even when disabled and suppresses the automatic list`() {
        val additional = listOf("<gold>To do: <objectives>")

        assertEquals(
            listOf("<gold>To do: o1\no2"),
            QuestButtonLore.compose(QuestStatus.ACTIVE, QuestLoreDefaults(), null, additional, listOf("o1", "o2")),
        )
        assertEquals(
            listOf("<gold>To do: o1"),
            QuestButtonLore.compose(QuestStatus.ACTIVE, enabled, null, additional, listOf("o1")),
        )
    }

    @Test
    fun `token alone with no objective drops its line`() {
        val lines = QuestButtonLore.compose(QuestStatus.ACTIVE, enabled, null, listOf("<objectives>", "b"), emptyList())

        assertEquals(listOf("b"), lines)
    }

    @Test
    fun `blank objective and blank default produce no line`() {
        val silent = enabled.copy(notStartedMessage = "", objectivesHeader = "")

        assertEquals(emptyList(), QuestButtonLore.compose(QuestStatus.INACTIVE, silent, null, emptyList(), emptyList()))
        assertEquals(listOf("o"), QuestButtonLore.compose(QuestStatus.ACTIVE, silent, null, emptyList(), listOf(" ", "o")))
    }

    @Test
    fun `blank lines written by the admin are kept as spacing`() {
        val lines = QuestButtonLore.compose(QuestStatus.COMPLETED, enabled, null, listOf("", "b"), emptyList())

        assertEquals(listOf("completed", "", "b"), lines)
    }

    @Test
    fun `description comes first, then the objectives, in every state and even with defaults off`() {
        val description = listOf("Find the relic.")

        assertEquals(
            listOf("Find the relic.", "header", "o", "b"),
            QuestButtonLore.compose(QuestStatus.ACTIVE, enabled, null, listOf("b"), listOf("o"), description),
        )
        assertEquals(
            listOf("Find the relic."),
            QuestButtonLore.compose(QuestStatus.INACTIVE, QuestLoreDefaults(), null, emptyList(), emptyList(), description),
        )
    }

    @Test
    fun `description token places it and removes it from the top`() {
        val lines = QuestButtonLore.compose(
            QuestStatus.COMPLETED, QuestLoreDefaults(), override(listOf("<gold><description>")), emptyList(), emptyList(),
            listOf("Find the relic."),
        )

        assertEquals(listOf("<gold>Find the relic."), lines)
    }

    @Test
    fun `description token without description drops its line`() {
        val lines = QuestButtonLore.compose(QuestStatus.INACTIVE, QuestLoreDefaults(), null, listOf("<description>", "b"), emptyList())

        assertEquals(listOf("b"), lines)
    }

    @Test
    fun `public defaults are off and in english`() {
        assertEquals(
            QuestLoreDefaults(false, "<gray>Not started yet", "<gold>Objectives:", "<green>Quest completed"),
            QuestLoreDefaults(),
        )
        assertFalse(QuestCodexConfigEntry().questLoreDefaults.enabled, "an existing codex must not change after the update")
    }
}
