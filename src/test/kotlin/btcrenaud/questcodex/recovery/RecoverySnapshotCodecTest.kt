package btcrenaud.questcodex.recovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecoverySnapshotCodecTest {
    @Test
    fun `round trips temporal recovery snapshots`() {
        val original = RecoverySnapshot(
            playerId = "11111111-1111-1111-1111-111111111111",
            updatedAt = 100L,
            expiresAt = 200L,
            interaction = RecoveryInteraction.Temporal("intro", 42),
        )

        val decoded = RecoverySnapshotCodec.decode(
            RecoverySnapshotCodec.encode(listOf(original)),
            now = 150L,
        )

        assertEquals(listOf(original), decoded)
    }

    @Test
    fun `round trips dialogue recovery snapshots`() {
        val original = RecoverySnapshot(
            playerId = "22222222-2222-2222-2222-222222222222",
            updatedAt = 100L,
            expiresAt = 200L,
            interaction = RecoveryInteraction.Dialogue("dialogue_intro"),
        )

        val decoded = RecoverySnapshotCodec.decode(
            RecoverySnapshotCodec.encode(listOf(original)),
            now = 150L,
        )

        assertEquals(listOf(original), decoded)
    }

    @Test
    fun `ignores expired and malformed snapshots`() {
        val raw = """
            {
              "version": 1,
              "players": {
                "33333333-3333-3333-3333-333333333333": {
                  "updatedAt": 1,
                  "expiresAt": 2,
                  "interaction": {"type":"TEMPORAL","pageId":"expired","frame":10}
                },
                "not-a-uuid": {
                  "updatedAt": 1,
                  "expiresAt": 9999,
                  "interaction": {"type":"DIALOGUE","entryId":"bad"}
                }
              }
            }
        """.trimIndent()

        assertEquals(emptyList(), RecoverySnapshotCodec.decode(raw, now = 3L))
        assertNull(RecoverySnapshotCodec.decode("not-json", now = 1L).firstOrNull())
    }
}
