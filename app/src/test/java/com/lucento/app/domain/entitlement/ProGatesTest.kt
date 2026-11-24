package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProGatesTest {
    private val now = 1_800_000_000_000L
    private fun state(vararg caps: Capability) = EntitlementState(
        if (caps.isEmpty()) emptyList() else listOf(
            Entitlement(
                source = EntitlementSource.GRANT, status = EntitlementStatus.ACTIVE, grants = caps.toSet(), startsAtMillis = now - 1,
                expiresAtMillis = now + 1_000_000, productId = null, verifiedAtMillis = now, verifiedBy = Verifier.SERVER,
            ),
        ),
    )
    private val free = state()
    private val lifetimeOnly = state(Capability.LOCAL_PRO)
    private val pro = state(*Capability.entries.toTypedArray())

    @Test fun sync_needs_cloud_unless_the_phone_is_a_partner_in_a_shared_space() {
        assertFalse(ProGates.syncAllowedByPlan(free, partnerOfSpace = false, now = now))
        assertTrue("a Partner syncs on the Owner's plan", ProGates.syncAllowedByPlan(free, partnerOfSpace = true, now = now))
        assertFalse("Lifetime after its year has no cloud", ProGates.syncAllowedByPlan(lifetimeOnly, partnerOfSpace = false, now = now))
        assertTrue(ProGates.syncAllowedByPlan(pro, partnerOfSpace = false, now = now))
        assertEquals(ProGates.syncAllowedByPlan(free, false, now), ProGates.restoreAllowedByPlan(free, false, now))
        assertTrue(ProGates.restoreAllowedByPlan(free, partnerOfSpace = true, now = now))
    }

    @Test fun insights_three_months_are_free_longer_periods_are_local_pro() {
        assertTrue(ProGates.insightsPeriodAllowed(3, free, now))
        assertFalse(ProGates.insightsPeriodAllowed(6, free, now))
        assertFalse(ProGates.insightsPeriodAllowed(12, free, now))
        assertTrue(ProGates.insightsPeriodAllowed(12, lifetimeOnly, now))
        assertEquals(3, ProGates.FREE_INSIGHTS_MONTHS)
    }

    @Test fun pdf_is_local_pro() {
        assertFalse(ProGates.pdfAllowed(free, now))
        assertTrue(ProGates.pdfAllowed(lifetimeOnly, now))
    }

    @Test fun smart_reading_needs_the_online_ai_service() {
        assertFalse("Free: the server would refuse every message", ProGates.smartReadingAllowed(free, now))
        assertFalse("Lifetime after its included year", ProGates.smartReadingAllowed(lifetimeOnly, now))
        assertTrue(ProGates.smartReadingAllowed(pro, now))
        assertTrue("AI_PRO alone is enough", ProGates.smartReadingAllowed(state(Capability.AI_PRO), now))
        // A plan that has ended no longer covers it, once its offline grace is over.
        assertFalse(ProGates.smartReadingAllowed(pro, now + 1_000_000 + Entitlement.OFFLINE_GRACE_MILLIS))
    }

    @Test fun ai_allowance_is_pros_with_the_online_ai_service_and_the_free_plans_otherwise() {
        // Free still gets AI answers (5 a month, docs §3.6); Pro gets 45 a month. Both run per calendar month.
        assertFalse("Free: the free plan's allowance", ProGates.aiProAllowance(free, now))
        assertFalse("Lifetime after its included year: back to the free allowance", ProGates.aiProAllowance(lifetimeOnly, now))
        assertTrue(ProGates.aiProAllowance(pro, now))
        assertTrue("AI_PRO alone is enough", ProGates.aiProAllowance(state(Capability.AI_PRO), now))
    }

    @Test fun sunrise_and_midnight_are_free_the_other_three_are_pro() {
        assertEquals(setOf("GROVE", "MONO", "AURORA"), ProGates.PRO_THEME_IDS)
        for (t in listOf("SUNRISE", "MIDNIGHT", "GROVE", "MONO", "AURORA")) {
            assertEquals(t, t in ProGates.PRO_THEME_IDS, ProGates.themeIsPro(t))
            assertEquals("$t on free", !ProGates.themeIsPro(t), ProGates.themeAllowed(t, free, now))
            assertTrue("$t on lifetime", ProGates.themeAllowed(t, lifetimeOnly, now))
        }
        assertEquals(ProGates.PRO_THEME_IDS, ProGates.lockedThemeIds(free, now))
        assertEquals(emptySet<String>(), ProGates.lockedThemeIds(pro, now))
    }
}
