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
