package btcrenaud.questcodex.recovery

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.util.UUID

private const val FORMAT_VERSION = 1
private const val MAX_SNAPSHOTS = 10_000

sealed interface RecoveryInteraction {
    data class Temporal(val pageId: String, val frame: Int) : RecoveryInteraction
    data class Dialogue(val entryId: String) : RecoveryInteraction
}

data class RecoverySnapshot(
    val playerId: String,
    val updatedAt: Long,
    val expiresAt: Long,
    val interaction: RecoveryInteraction,
)

/** Versioned, defensive codec for the player recovery artifact. */
object RecoverySnapshotCodec {
    fun encode(snapshots: Collection<RecoverySnapshot>): String {
        val root = JsonObject()
        root.addProperty("version", FORMAT_VERSION)
        val players = JsonObject()

        snapshots.asSequence()
            .filter { isValidPlayerId(it.playerId) }
            .sortedBy { it.playerId }
            .take(MAX_SNAPSHOTS)
            .forEach { snapshot ->
                val value = JsonObject()
                value.addProperty("updatedAt", snapshot.updatedAt)
                value.addProperty("expiresAt", snapshot.expiresAt)
                value.add("interaction", encodeInteraction(snapshot.interaction))
                players.add(snapshot.playerId, value)
            }

        root.add("players", players)
        return root.toString()
    }

    fun decode(raw: String, now: Long): List<RecoverySnapshot> {
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
        if (root.get("version")?.asInt != FORMAT_VERSION) return emptyList()
        val players = root.getAsJsonObject("players") ?: return emptyList()

        return players.entrySet().asSequence()
            .take(MAX_SNAPSHOTS)
            .mapNotNull { (playerId, value) -> decodeSnapshot(playerId, value, now) }
            .toList()
    }

    private fun encodeInteraction(interaction: RecoveryInteraction): JsonObject = JsonObject().apply {
        when (interaction) {
            is RecoveryInteraction.Temporal -> {
                addProperty("type", "TEMPORAL")
                addProperty("pageId", interaction.pageId)
                addProperty("frame", interaction.frame.coerceAtLeast(0))
            }

            is RecoveryInteraction.Dialogue -> {
                addProperty("type", "DIALOGUE")
                addProperty("entryId", interaction.entryId)
            }
        }
    }

    private fun decodeSnapshot(
        playerId: String,
        value: com.google.gson.JsonElement,
        now: Long,
    ): RecoverySnapshot? {
        if (!isValidPlayerId(playerId) || !value.isJsonObject) return null
        val objectValue = value.asJsonObject
        val updatedAt = objectValue.long("updatedAt") ?: return null
        val expiresAt = objectValue.long("expiresAt") ?: return null
        if (expiresAt <= now || updatedAt > expiresAt) return null

        val interaction = objectValue.get("interaction")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.decodeInteraction()
            ?: return null

        return RecoverySnapshot(playerId, updatedAt, expiresAt, interaction)
    }

    private fun JsonObject.decodeInteraction(): RecoveryInteraction? {
        return when (get("type")?.asString?.uppercase()) {
            "TEMPORAL" -> {
                val pageId = get("pageId")?.asString?.takeIf(String::isNotBlank) ?: return null
                val frame = get("frame")?.asInt?.coerceAtLeast(0) ?: return null
                RecoveryInteraction.Temporal(pageId, frame)
            }

            "DIALOGUE" -> {
                val entryId = get("entryId")?.asString?.takeIf(String::isNotBlank) ?: return null
                RecoveryInteraction.Dialogue(entryId)
            }

            else -> null
        }
    }

    private fun JsonObject.long(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private fun isValidPlayerId(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess
}
