package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/MONETIZATION.md §6.3 rule 7: told once, after a real downgrade; never for an upgrade or a first look. */
class PlanNoticesTest {
    @Test fun nothing_on_first_look_or_without_a_change() {
        for (p in DisplayPlan.entries) {
            assertNull(PlanNotices.after(null, p))
            assertNull(PlanNotices.after(p, p))
        }
    }

    @Test fun upgrades_say_nothing() {
        assertNull(PlanNotices.after(DisplayPlan.FREE, DisplayPlan.PRO))
        assertNull(PlanNotices.after(DisplayPlan.FREE, DisplayPlan.LIFETIME))
        assertNull(PlanNotices.after(DisplayPlan.LIFETIME, DisplayPlan.LIFETIME_WITH_SERVICES))
        assertNull(PlanNotices.after(DisplayPlan.LIFETIME, DisplayPlan.PRO))
        assertNull(PlanNotices.after(DisplayPlan.PRO, DisplayPlan.LIFETIME_WITH_SERVICES))
        assertNull(PlanNotices.after(DisplayPlan.LIFETIME_WITH_SERVICES, DisplayPlan.PRO))
    }

    @Test fun downgrades_say_what_ended() {
        assertEquals(PlanNotice.PRO_ENDED, PlanNotices.after(DisplayPlan.PRO, DisplayPlan.FREE))
        assertEquals(PlanNotice.SERVICES_ENDED, PlanNotices.after(DisplayPlan.LIFETIME_WITH_SERVICES, DisplayPlan.LIFETIME))
        assertEquals("a subscriber who also owns Lifetime keeps everything local", PlanNotice.SERVICES_ENDED, PlanNotices.after(DisplayPlan.PRO, DisplayPlan.LIFETIME))
        assertEquals(PlanNotice.LIFETIME_ENDED, PlanNotices.after(DisplayPlan.LIFETIME, DisplayPlan.FREE))
        assertEquals(PlanNotice.LIFETIME_ENDED, PlanNotices.after(DisplayPlan.LIFETIME_WITH_SERVICES, DisplayPlan.FREE))
    }
}
