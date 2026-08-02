package btcrenaud.questcodex.waypoint

import kotlin.test.Test
import kotlin.test.assertEquals

class WaypointDisplayTransformTest {
    @Test
    fun `keeps a rotated square centered around its display position`() {
        val translation = centeredDisplayTranslation(
            scale = 0.5f,
            scaleY = 64.0f,
            rotation = 90.0f,
            centerVertically = false,
        )

        assertEquals(-0.25f, translation.x, absoluteTolerance = 0.0001f)
        assertEquals(0.0f, translation.y, absoluteTolerance = 0.0001f)
        assertEquals(0.25f, translation.z, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `centers the vertical axis only for a regular block display`() {
        val centered = centeredDisplayTranslation(0.65f, 0.65f, 0.0f, centerVertically = true)
        val baseAnchored = centeredDisplayTranslation(0.5f, 64.0f, 45.0f, centerVertically = false)

        assertEquals(-0.325f, centered.y, absoluteTolerance = 0.0001f)
        assertEquals(0.0f, baseAnchored.y, absoluteTolerance = 0.0001f)
    }
}
