package btc.renaud.questcodex

import com.typewritermc.engine.paper.plugin

fun parseSlots(rawSlots: List<String>, context: String): List<Int> {
    if (rawSlots.isEmpty()) return emptyList()
    val resolved = linkedSetOf<Int>()
    for (raw in rawSlots) {
        val token = raw.trim()
        if (token.isEmpty()) continue
        val rangeParts = token.split('-', limit = 2).map { it.trim() }
        if (rangeParts.size == 1) {
            val value = rangeParts[0].toIntOrNull()
            if (value != null) {
                resolved += value
            } else {
                plugin.logger.warning(
                    "[QuestCodex] Invalid slot '$token' for $context. Expected a number or range."
                )
            }
            continue
        }

        val start = rangeParts[0].toIntOrNull()
        val end = rangeParts[1].toIntOrNull()
        if (start == null || end == null) {
            plugin.logger.warning(
                "[QuestCodex] Invalid slot range '$token' for $context. Expected numeric bounds."
            )
            continue
        }

        val (min, max) = if (start <= end) start to end else end to start
        for (slot in min..max) {
            resolved += slot
        }
    }
    return resolved.toList()
}
