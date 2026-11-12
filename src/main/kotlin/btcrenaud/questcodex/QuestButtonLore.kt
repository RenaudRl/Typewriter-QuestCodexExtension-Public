package btcrenaud.questcodex

import btcrenaud.questcodex.entries.QuestLoreDefaults
import com.typewritermc.quest.QuestStatus

/**
 * Decides the lore lines of a quest button, before the tracking hint is appended.
 *
 * Order: the `quest_assignment` lore of the state (or the default message when there is none),
 * then the automatic objective list, then the `quest_lore` lines. Writing [OBJECTIVES_TOKEN] in
 * either lore places the list there instead; the token is replaced even when the defaults are off,
 * so it never reaches the player as raw text.
 */
object QuestButtonLore {
    const val OBJECTIVES_TOKEN = "<objectives>"

    /**
     * @param objectives the rendered objectives the player currently sees, highest priority first.
     * @return lines that may still contain `\n`; the caller splits them.
     */
    fun compose(
        status: QuestStatus,
        defaults: QuestLoreDefaults,
        override: QuestStateDisplayOverride?,
        additional: List<String>,
        objectives: List<String>,
    ): List<String> {
        val visible = if (override?.hideObjectives == true) emptyList() else objectives.filter { it.isNotBlank() }
        val base = override?.lore?.takeIf { it.isNotEmpty() }
            ?: if (defaults.enabled) defaults.messageFor(status) else emptyList()
        val usesToken = (base + additional).any { OBJECTIVES_TOKEN in it }
        val automatic = if (defaults.enabled && status == QuestStatus.ACTIVE && !usesToken && visible.isNotEmpty()) {
            listOfNotNull(defaults.objectivesHeader.ifBlank { null }) + visible
        } else {
            emptyList()
        }

        return (base + automatic + additional).mapNotNull { line -> line.withObjectives(visible) }
    }

    private fun QuestLoreDefaults.messageFor(status: QuestStatus): List<String> {
        val message = when (status) {
            QuestStatus.INACTIVE -> notStartedMessage
            QuestStatus.ACTIVE -> ""
            QuestStatus.COMPLETED -> completedMessage
        }
        return if (message.isBlank()) emptyList() else listOf(message)
    }

    /** `null` when the line held only the token and there is nothing to put in its place. */
    private fun String.withObjectives(objectives: List<String>): String? {
        if (OBJECTIVES_TOKEN !in this) return this
        return replace(OBJECTIVES_TOKEN, objectives.joinToString("\n")).ifBlank { null }
    }
}
