package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalMilestonesTest {
    private val target = Money(100_000, "PHP")
    private fun m(minor: Long) = Money(minor, "PHP")

    @Test fun crossingHalfway() = assertEquals(GoalMilestone.HALFWAY, GoalMilestones.crossed(target, m(40_000), m(50_000)))

    @Test fun reachingTheTargetWinsOverHalfway() = assertEquals(GoalMilestone.REACHED, GoalMilestones.crossed(target, m(10_000), m(120_000)))

    @Test fun noRepeatOnceAlreadyPast() {
        assertNull(GoalMilestones.crossed(target, m(50_000), m(60_000)))
        assertNull(GoalMilestones.crossed(target, m(100_000), m(110_000)))
    }

    @Test fun justBelowDoesNotCount() = assertNull(GoalMilestones.crossed(target, m(0), m(49_999)))

    @Test fun withdrawalsAndZeroTargetsNeverCelebrate() {
        assertNull(GoalMilestones.crossed(target, m(120_000), m(90_000)))
        assertNull(GoalMilestones.crossed(m(0), m(0), m(10)))
    }

    @Test fun oddTargetsUseExactArithmetic() {
        val odd = Money(3, "JPY") // halfway is 1.5 yen: 1 is below, 2 is past
        assertNull(GoalMilestones.crossed(odd, Money(0, "JPY"), Money(1, "JPY")))
        assertEquals(GoalMilestone.HALFWAY, GoalMilestones.crossed(odd, Money(1, "JPY"), Money(2, "JPY")))
    }
}
