package dev.melcodes.kilometre.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Unit tests for the pure AAC milestone ladder. The ladder is [1000, 3000]:
// the first rendez-vous pédagogique around 1000 km and the final one at the
// 3000 km AAC minimum. There is deliberately no 2000 km milestone.
class AacMilestonesTest {

    @Test
    fun `fresh driver starts on the first milestone`() {
        assertEquals(1000, AacMilestones.firstUnreachedMilestone(0.0))
        assertEquals(1000, AacMilestones.firstUnreachedMilestone(92.9))
        assertEquals(1000, AacMilestones.firstUnreachedMilestone(999.9))
    }

    @Test
    fun `past the first milestone the target is the final one`() {
        // Exactly at 1000 km the first milestone is reached, so the target
        // advances to 3000.
        assertEquals(3000, AacMilestones.firstUnreachedMilestone(1000.0))
        assertEquals(3000, AacMilestones.firstUnreachedMilestone(2000.0))
        assertEquals(3000, AacMilestones.firstUnreachedMilestone(2999.9))
    }

    @Test
    fun `at or beyond the final milestone the target stays the final one`() {
        assertEquals(3000, AacMilestones.firstUnreachedMilestone(3000.0))
        assertEquals(3000, AacMilestones.firstUnreachedMilestone(4200.0))
    }

    @Test
    fun `nextGoalAfter walks the ladder then ends`() {
        assertEquals(3000, AacMilestones.nextGoalAfter(1000))
        assertNull(AacMilestones.nextGoalAfter(3000))
        // A custom (off-ladder) goal has no next milestone.
        assertNull(AacMilestones.nextGoalAfter(2500))
    }

    @Test
    fun `isRdvDue fires once per milestone and respects the notified guard`() {
        // Below the goal: not due.
        assertFalse(AacMilestones.isRdvDue(totalKm = 800.0, goal = 1000, notifiedKm = 0, complete = false))
        // Reached the goal, not yet notified: due.
        assertTrue(AacMilestones.isRdvDue(totalKm = 1000.0, goal = 1000, notifiedKm = 0, complete = false))
        // Already notified for this goal: not due again.
        assertFalse(AacMilestones.isRdvDue(totalKm = 1500.0, goal = 1000, notifiedKm = 1000, complete = false))
        // Journey complete: never due.
        assertFalse(AacMilestones.isRdvDue(totalKm = 3200.0, goal = 3000, notifiedKm = 1000, complete = true))
    }

    @Test
    fun `only the last milestone is final`() {
        assertFalse(AacMilestones.isFinalMilestone(1000))
        assertTrue(AacMilestones.isFinalMilestone(3000))
    }
}
