package btcrenaud.questcodex

import btcrenaud.questcodex.entries.QuestLoreDefaults
import com.typewritermc.quest.QuestStatus

/**
 * Decides the lore lines of a quest button, before the tracking hint is appended.
 *
 * Order: the quest description (from `quest_lore`), the `quest_assignment` lore of the state (or the
 * default message when there is none), the automatic objective list, then the `quest_lore` lines of
 * the state. Writing [DESCRIPTION_TOKEN] or [OBJECTIVES_TOKEN] in either lore places that part there
 * instead of its automatic position. Tokens are replaced even when the defaults are off, so they
 * never reach the player as raw text.
 */
object QuestButtonLore {
    const val OBJECTIVES_TOKEN = "<objectives>"
    const val DESCRIPTION_TOKEN = "<description>"

    /**
     * @param objectives the rendered objectives the player currently sees, highest priority first.
     * @param description the quest description lines, shown in every state.
     * @return lines that may still contain `\n`; the caller splits them.
     */
    fun compose(
        status: QuestStatus,
        defaults: QuestLoreDefaults,
        override: QuestStateDisplayOverride?,
        additional: List<String>,
        objectives: List<String>,
        description: List<String> = emptyList(),
    ): List<String> {
        val visible = if (override?.hideObjectives == true) emptyList() else objectives.filter { it.isNotBlank() }
        val base = override?.lore?.takeIf { it.isNotEmpty() }
            ?: if (defaults.enabled) defaults.messageFor(status) else emptyList()
        val configured = base + additional
        val top = if (configured.any { DESCRIPTION_TOKEN in it }) emptyList() else description
        val automatic = if (defaults.enabled && status == QuestStatus.ACTIVE && configured.none { OBJECTIVES_TOKEN in it } && visible.isNotEmpty()) {
            listOfNotNull(defaults.objectivesHeader.ifBlank { null }) + visible
        } else {
            emptyList()
        }
        val tokens = mapOf(
            OBJECTIVES_TOKEN to visible.joinToString("\n"),
            DESCRIPTION_TOKEN to description.joinToString("\n"),
        )

        return top + (base + automatic + additional).mapNotNull { line -> line.withTokens(tokens) }
    }

    private fun QuestLoreDefaults.messageFor(status: QuestStatus): List<String> {
        val message = when (status) {
            QuestStatus.INACTIVE -> notStartedMessage
            QuestStatus.ACTIVE -> ""
            QuestStatus.COMPLETED -> completedMessage
        }
        return if (message.isBlank()) emptyList() else listOf(message)
    }

    /** `null` when the line held only tokens and there is nothing to put in their place. */
    private fun String.withTokens(tokens: Map<String, String>): String? {
        if (tokens.keys.none { it in this }) return this
        return tokens.entries.fold(this) { line, (token, value) -> line.replace(token, value) }.ifBlank { null }
    }
}
