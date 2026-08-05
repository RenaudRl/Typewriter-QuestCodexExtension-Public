package btcrenaud.questcodex.waypoint

import java.util.UUID
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaypointProjectionTest {

    private val eye = Vec3(0.0, 64.0, 0.0)

    @Test
    fun `keeps the marker on the eye to target axis`() {
        val target = Vec3(300.0, 90.0, -120.0)
        val marker = projectOntoSphere(eye, target, radius = 12.0, referenceRadius = 12.0, constantApparentSize = true)

        val toTarget = target.minus(eye).normalized()
        val toMarker = marker.position.minus(eye).normalized()

        assertEquals(toTarget.x, toMarker.x, absoluteTolerance = 1.0E-9)
        assertEquals(toTarget.y, toMarker.y, absoluteTolerance = 1.0E-9)
        assertEquals(toTarget.z, toMarker.z, absoluteTolerance = 1.0E-9)
    }

    @Test
    fun `pulls a distant target onto the projection sphere`() {
        val marker = projectOntoSphere(
            eye, Vec3(1000.0, 64.0, 0.0), radius = 12.0, referenceRadius = 12.0, constantApparentSize = true,
        )
        assertEquals(12.0, marker.position.minus(eye).length(), absoluteTolerance = 1.0E-9)
        assertEquals(1.0, marker.scaleFactor, absoluteTolerance = 1.0E-9)
    }

    @Test
    fun `leaves a target closer than the radius on the spot`() {
        val target = Vec3(3.0, 64.0, 4.0) // 5 blocks away
        val marker = projectOntoSphere(eye, target, radius = 12.0, referenceRadius = 12.0, constantApparentSize = true)

        assertEquals(5.0, marker.position.minus(eye).length(), absoluteTolerance = 1.0E-9)
        // Half the reference distance means half the scale, so the apparent size holds.
        assertEquals(5.0 / 12.0, marker.scaleFactor, absoluteTolerance = 1.0E-9)
    }

    @Test
    fun `keeps the raw scale when constant apparent size is disabled`() {
        val marker = projectOntoSphere(
            eye, Vec3(500.0, 64.0, 0.0), radius = 12.0, referenceRadius = 12.0, constantApparentSize = false,
        )
        assertEquals(1.0, marker.scaleFactor, absoluteTolerance = 1.0E-9)
    }

    @Test
    fun `adaptive radius anchors on the target within the near distance`() {
        assertEquals(4.0, adaptiveRadius(4.0, nearDistance = 10.0, band = 4.0, projectionRadius = 12.0))
        assertEquals(10.0, adaptiveRadius(10.0, nearDistance = 10.0, band = 4.0, projectionRadius = 12.0))
    }

    @Test
    fun `adaptive radius reaches the projection sphere past the transition band`() {
        assertEquals(12.0, adaptiveRadius(40.0, nearDistance = 10.0, band = 4.0, projectionRadius = 12.0), 1.0E-9)
    }

    @Test
    fun `adaptive radius stays continuous across the transition`() {
        var previous = adaptiveRadius(9.9, nearDistance = 10.0, band = 4.0, projectionRadius = 12.0)
        var distance = 10.0
        while (distance <= 20.0) {
            val current = adaptiveRadius(distance, nearDistance = 10.0, band = 4.0, projectionRadius = 12.0)
            // No visual jump: a 0.1 block step must never move the marker by more than 0.15.
            assertTrue(abs(current - previous) < 0.15, "jump at distance $distance: $previous -> $current")
            previous = current
            distance += 0.1
        }
    }

    @Test
    fun `declutter leaves well separated markers untouched`() {
        val offsets = declutterOffsets(
            listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(-1.0, 0.0, 0.0)),
            minAngleDegrees = 4.0,
            spacing = 0.6,
        )
        assertEquals(listOf(0.0, 0.0, 0.0), offsets)
    }

    @Test
    fun `declutter stacks markers sharing a line of sight`() {
        val almostIdentical = Vec3(1.0, 0.01, 0.0) // ~0.57 degrees apart
        val offsets = declutterOffsets(
            listOf(Vec3(1.0, 0.0, 0.0), almostIdentical, Vec3(1.0, 0.02, 0.0)),
            minAngleDegrees = 4.0,
            spacing = 0.6,
        )
        assertEquals(listOf(0.0, 0.6, 1.2), offsets)
    }

    @Test
    fun `declutter is disabled by a zero angle`() {
        val offsets = declutterOffsets(
            listOf(Vec3(1.0, 0.0, 0.0), Vec3(1.0, 0.001, 0.0)),
            minAngleDegrees = 0.0,
            spacing = 0.6,
        )
        assertEquals(listOf(0.0, 0.0), offsets)
    }

    @Test
    fun `declutter steps over levels already taken by another entry`() {
        val ahead = Vec3(1.0, 0.0, 0.0)
        val offsets = declutterOffsets(
            listOf(ahead),
            minAngleDegrees = 4.0,
            spacing = 0.6,
            occupied = listOf(DeclutterClaim(ahead, 0.0)),
        )
        assertEquals(listOf(0.6), offsets)
    }

    @Test
    fun `declutter ignores a foreign claim pointing elsewhere`() {
        val offsets = declutterOffsets(
            listOf(Vec3(1.0, 0.0, 0.0)),
            minAngleDegrees = 4.0,
            spacing = 0.6,
            occupied = listOf(DeclutterClaim(Vec3(0.0, 0.0, 1.0), 0.0)),
        )
        assertEquals(listOf(0.0), offsets)
    }

    @Test
    fun `registry gives the lower entry id the bottom level`() {
        val playerId = UUID.randomUUID()
        val ahead = listOf(Vec3(1.0, 0.0, 0.0))
        try {
            val first = WaypointDeclutterRegistry.offsets(playerId, "aaa", ahead, 4.0, 0.6)
            val second = WaypointDeclutterRegistry.offsets(playerId, "bbb", ahead, 4.0, 0.6)
            assertEquals(listOf(0.0), first)
            assertEquals(listOf(0.6), second)

            // Priority is stable: re-rendering must not swap them around.
            assertEquals(listOf(0.0), WaypointDeclutterRegistry.offsets(playerId, "aaa", ahead, 4.0, 0.6))
            assertEquals(listOf(0.6), WaypointDeclutterRegistry.offsets(playerId, "bbb", ahead, 4.0, 0.6))
        } finally {
            WaypointDeclutterRegistry.releasePlayer(playerId)
        }
    }

    @Test
    fun `registry frees a level once the entry stops rendering`() {
        val playerId = UUID.randomUUID()
        val ahead = listOf(Vec3(1.0, 0.0, 0.0))
        try {
            WaypointDeclutterRegistry.offsets(playerId, "aaa", ahead, 4.0, 0.6)
            assertEquals(listOf(0.6), WaypointDeclutterRegistry.offsets(playerId, "bbb", ahead, 4.0, 0.6))

            WaypointDeclutterRegistry.release(playerId, "aaa")
            assertEquals(listOf(0.0), WaypointDeclutterRegistry.offsets(playerId, "bbb", ahead, 4.0, 0.6))
        } finally {
            WaypointDeclutterRegistry.releasePlayer(playerId)
        }
    }

    @Test
    fun `registry keeps players apart`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val ahead = listOf(Vec3(1.0, 0.0, 0.0))
        try {
            WaypointDeclutterRegistry.offsets(first, "aaa", ahead, 4.0, 0.6)
            assertEquals(listOf(0.0), WaypointDeclutterRegistry.offsets(second, "bbb", ahead, 4.0, 0.6))
        } finally {
            WaypointDeclutterRegistry.releasePlayer(first)
            WaypointDeclutterRegistry.releasePlayer(second)
        }
    }

    @Test
    fun `projection survives a target sitting on the player's eyes`() {
        val marker = projectOntoSphere(eye, eye, radius = 12.0, referenceRadius = 12.0, constantApparentSize = true)
        assertEquals(eye, marker.position)
        assertTrue(marker.scaleFactor > 0.0)
    }
}
