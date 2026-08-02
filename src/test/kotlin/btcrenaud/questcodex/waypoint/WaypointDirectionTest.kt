package btcrenaud.questcodex.waypoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaypointDirectionTest {
    @Test
    fun `maps the eight horizontal sectors`() {
        val expected = listOf(
            WaypointDirection.NORTH to 0.0,
            WaypointDirection.NORTH_EAST to 45.0,
            WaypointDirection.EAST to 90.0,
            WaypointDirection.SOUTH_EAST to 135.0,
            WaypointDirection.SOUTH to 180.0,
            WaypointDirection.SOUTH_WEST to -135.0,
            WaypointDirection.WEST to -90.0,
            WaypointDirection.NORTH_WEST to -45.0,
        )

        expected.forEach { (direction, relativeYaw) ->
            assertEquals(direction, WaypointDirectionMath.fromRelativeYaw(relativeYaw))
        }
    }

    @Test
    fun `normalizes yaw across the wrap boundary`() {
        assertEquals(179.0, WaypointDirectionMath.wrapDegrees(-181.0))
        assertEquals(-179.0, WaypointDirectionMath.wrapDegrees(181.0))
    }

    @Test
    fun `shows only the front hemisphere for a 180 degree view`() {
        assertTrue(isWithinHorizontalViewCone(0f, 0.0, 0.0, 0.0, 10.0, 180.0))
        assertTrue(isWithinHorizontalViewCone(0f, 0.0, 0.0, 10.0, 0.0, 180.0))
        assertFalse(isWithinHorizontalViewCone(0f, 0.0, 0.0, 0.0, -10.0, 180.0))
    }
}
