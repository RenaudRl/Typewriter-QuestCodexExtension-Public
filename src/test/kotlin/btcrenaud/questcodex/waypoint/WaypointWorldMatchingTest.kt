package btcrenaud.questcodex.waypoint

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaypointWorldMatchingTest {
    private val playerWorldUuid = UUID.fromString("74c8760f-8d9f-4e06-a6f7-7f410f8f0df2")

    @Test
    fun matchesCanonicalUuidWorldIdentifier() {
        assertTrue(
            matchesPlayerWorld(
                positionWorldIdentifier = playerWorldUuid.toString(),
                playerWorldName = "palier1",
                playerWorldUuid = playerWorldUuid,
            ),
        )
    }

    @Test
    fun matchesLegacyWorldNameCaseInsensitively() {
        assertTrue(
            matchesPlayerWorld(
                positionWorldIdentifier = "PALIER1",
                playerWorldName = "palier1",
                playerWorldUuid = playerWorldUuid,
            ),
        )
    }

    @Test
    fun rejectsAnotherWorld() {
        assertFalse(
            matchesPlayerWorld(
                positionWorldIdentifier = "world",
                playerWorldName = "palier1",
                playerWorldUuid = playerWorldUuid,
            ),
        )
    }
}
