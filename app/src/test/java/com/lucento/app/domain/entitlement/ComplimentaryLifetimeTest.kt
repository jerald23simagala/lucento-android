package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A complimentary Pro Lifetime from Mission Control (FD-V5, review M5) is written as two GRANT rows: a phone row shaped
 * like paid Lifetime's (product lucento_pro_lifetime, LOCAL_PRO, no expiry) and a product-less services row that ends
 * after 365 days. It reads as Lifetime wherever the plan is put into words. Google Play's own Lifetime rules (a refund,
 * releasing a purchase) never apply to it.
 */
class ComplimentaryLifetimeTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val services = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED)

    private fun grant(
        product: String?,
        grants: Set<Capability>,
        expiresAt: Long?,
        status: EntitlementStatus = EntitlementStatus.ACTIVE,
        verifiedAt: Long = now - 1000,
    ) = Entitlement(
        source = EntitlementSource.GRANT, status = status, grants = grants, startsAtMillis = now - 10 * day,
        expiresAtMillis = expiresAt, productId = product, verifiedAtMillis = verifiedAt, verifiedBy = Verifier.SERVER,
    )

    private fun phoneRow(status: EntitlementStatus = EntitlementStatus.ACTIVE, expiresAt: Long? = null, verifiedAt: Long = now - 1000) =
        grant(PlayProducts.PRO_LIFETIME, setOf(Capability.LOCAL_PRO), expiresAt, status, verifiedAt)

    private fun servicesRow(expiresAt: Long = now + 355 * day, status: EntitlementStatus = EntitlementStatus.ACTIVE) =
        grant(null, services, expiresAt, status)

    /** Paid Lifetime as the phone reads it from Google Play ([PurchaseInterpreter]), bought [daysAgo] days ago. */
    private fun paidLifetime(daysAgo: Long) = PurchaseInterpreter.entitlements(
        listOf(
            PurchaseRecord(
                PlayProducts.PRO_LIFETIME, "tok-life", "GPA.life", now - daysAgo * day,
                pending = false, acknowledged = true, autoRenewing = false,
            ),
        ),
        now,
    )

    @Test fun both_complimentary_rows_read_as_lifetime_with_services() {
        val state = EntitlementState(listOf(phoneRow(), servicesRow()))
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, state.displayPlan(now))
        assertTrue(state.lifetimeIsComplimentary(now))
        assertFalse(state.complimentaryLifetimeEnded())
    }

    @Test fun only_the_phone_row_live_reads_as_lifetime() {
        // The services year is over: the server marks the row EXPIRED, and it is past the offline grace either way.
        val ended = EntitlementState(listOf(phoneRow(), servicesRow(expiresAt = now - 5 * day, status = EntitlementStatus.EXPIRED)))
        assertEquals(DisplayPlan.LIFETIME, ended.displayPlan(now))
        val lapsed = EntitlementState(listOf(phoneRow(), servicesRow(expiresAt = now - 5 * day)))
        assertEquals(DisplayPlan.LIFETIME, lapsed.displayPlan(now))
        val phoneOnly = EntitlementState(listOf(phoneRow()))
        assertEquals(DisplayPlan.LIFETIME, phoneOnly.displayPlan(now))
        assertTrue(phoneOnly.lifetimeIsComplimentary(now))
    }

    @Test fun a_normal_courtesy_grant_still_reads_as_pro() {
        val ninetyDays = EntitlementState(listOf(grant(null, Capability.entries.toSet(), expiresAt = now + 80 * day)))
        assertEquals(DisplayPlan.PRO, ninetyDays.displayPlan(now))
        assertFalse(ninetyDays.lifetimeIsComplimentary(now))
        // Pro on the phone only, from a grant without the Lifetime product, is a grant, not a Lifetime.
        val phoneOnlyGrant = EntitlementState(listOf(grant(null, setOf(Capability.LOCAL_PRO), expiresAt = now + 80 * day)))
        assertEquals(DisplayPlan.PRO, phoneOnlyGrant.displayPlan(now))
    }

    @Test fun paid_lifetime_reads_as_before() {
        val yearOne = EntitlementState(paidLifetime(daysAgo = 1))
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, yearOne.displayPlan(now))
        assertFalse(yearOne.lifetimeIsComplimentary(now))
        val afterTheYear = EntitlementState(paidLifetime(daysAgo = 400))
        assertEquals(DisplayPlan.LIFETIME, afterTheYear.displayPlan(now))
        assertFalse(afterTheYear.lifetimeIsComplimentary(now))
    }

    @Test fun a_paid_lifetime_alongside_a_complimentary_one_is_the_one_shown_as_owned() {
        val both = EntitlementState(paidLifetime(daysAgo = 400) + phoneRow() + servicesRow())
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, both.displayPlan(now))
        assertFalse(both.lifetimeIsComplimentary(now))
    }

    @Test fun a_complimentary_lifetime_never_waits_for_a_google_play_check() {
        // Grants are the server's and keep their own dates: a phone that hasn't heard from it for 40 days keeps Lifetime.
        val quiet = EntitlementState(listOf(phoneRow(verifiedAt = now - 40 * day)))
        assertEquals(DisplayPlan.LIFETIME, quiet.displayPlan(now))
        assertFalse(quiet.awaitingCheck(now))
    }

    @Test fun taking_a_complimentary_lifetime_back_is_a_plan_ending_never_a_refund() {
        // Mission Control's revoke: both rows REVOKED with the end set to the moment it was taken back.
        val revoked = EntitlementState(
            listOf(
                phoneRow(status = EntitlementStatus.REVOKED, expiresAt = now - day),
                servicesRow(expiresAt = now - day, status = EntitlementStatus.REVOKED),
            ),
        )
        assertEquals(DisplayPlan.FREE, revoked.displayPlan(now))
        assertFalse("Google Play's refund notice is for Play purchases only", revoked.lifetimeEnded())
        assertTrue(revoked.complimentaryLifetimeEnded())
        assertEquals(
            PlanNotice.PRO_ENDED,
            PlanNotices.after(DisplayPlan.LIFETIME_WITH_SERVICES, DisplayPlan.FREE, revoked.awaitingCheck(now), revoked.complimentaryLifetimeEnded()),
        )
    }

    @Test fun a_refunded_play_lifetime_keeps_the_refund_notice_even_with_a_complimentary_row_on_record() {
        val refunded = paidLifetime(daysAgo = 400).map { it.copy(status = EntitlementStatus.REVOKED, verifiedBy = Verifier.SERVER) }
        val state = EntitlementState(refunded + phoneRow(status = EntitlementStatus.REVOKED, expiresAt = now - day))
        assertTrue(state.lifetimeEnded())
        assertFalse(state.complimentaryLifetimeEnded())
        assertEquals(PlanNotice.LIFETIME_ENDED, PlanNotices.after(DisplayPlan.LIFETIME, DisplayPlan.FREE, false, state.complimentaryLifetimeEnded()))
    }

    @Test fun a_complimentary_row_is_never_a_google_play_purchase_to_release() {
        val owned = listOf(
            PurchaseRecord(PlayProducts.PRO_LIFETIME, "tok-life", "GPA.life", now - 2 * day, pending = false, acknowledged = true, autoRenewing = false),
        )
        val rows = listOf(phoneRow(status = EntitlementStatus.REVOKED, expiresAt = now - day))
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(rows, owned))
    }
}
