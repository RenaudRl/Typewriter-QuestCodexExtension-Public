package btcrenaud.questcodex.tracking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackedQuestFollowTest {

    @Test
    fun `a newly active quest joins the tracked quests with multi-tracking`() {
        val tracked = linkedSetOf("first")

        val changed = tracked.follow("second", multiTrackingEnabled = true)

        assertTrue(changed)
        assertEquals(listOf("first", "second"), tracked.toList())
    }

    @Test
    fun `a newly active quest replaces the tracked one without multi-tracking`() {
        val tracked = linkedSetOf("first")

        val changed = tracked.follow("second", multiTrackingEnabled = false)

        assertTrue(changed)
        assertEquals(listOf("second"), tracked.toList())
    }

    @Test
    fun `following the quest already tracked changes nothing`() {
        val single = linkedSetOf("first")
        val multi = linkedSetOf("first", "second")

        assertFalse(single.follow("first", multiTrackingEnabled = false))
        assertFalse(multi.follow("first", multiTrackingEnabled = true))
        assertEquals(listOf("first", "second"), multi.toList())
    }
}
