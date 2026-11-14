package com.lucento.app.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which purchases Lucento may release so a Lifetime can be bought again (docs/MONETIZATION.md §5.3 L-7). Releasing can't
 * be undone, so every way a purchase must be kept is pinned here.
 */
class LifetimeReleaseTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private fun lifetime(token: String = "tok-life", pending: Boolean = false) = PurchaseRecord(
        PlayProducts.PRO_LIFETIME, token, "GPA.life", now - 2 * day, pending = pending, acknowledged = true, autoRenewing = false,
    )

    private fun subscription(token: String = "tok-sub") = PurchaseRecord(
        PlayProducts.PRO_SUBSCRIPTION, token, "GPA.sub", now - 2 * day, pending = false, acknowledged = true, autoRenewing = true,
    )

    /** One row of a purchase, as the server writes it or as the phone holds Play's reading. */
    private fun row(
        token: String,
        status: EntitlementStatus,
        source: EntitlementSource = EntitlementSource.PLAY_LIFETIME,
        verifiedBy: Verifier = Verifier.SERVER,
        lucentoCheckedAt: Long? = null,
        product: String = PlayProducts.PRO_LIFETIME,
    ) = Entitlement(
        source = source, status = status, grants = setOf(Capability.LOCAL_PRO), startsAtMillis = now - 2 * day,
        expiresAtMillis = if (source == EntitlementSource.PLAY_LIFETIME) null else now + 300 * day, productId = product,
        purchaseTokenHash = PurchaseInterpreter.tokenHash(token), verifiedAtMillis = now, verifiedBy = verifiedBy,
        lucentoCheckedAtMillis = lucentoCheckedAt,
    )

    /** Both rows of a Lifetime in one state, like apply_play_purchase and the purchase check leave them. */
    private fun lifetimeRows(
        token: String,
        status: EntitlementStatus,
        verifiedBy: Verifier = Verifier.SERVER,
        lucentoCheckedAt: Long? = null,
    ) = listOf(
        row(token, status, verifiedBy = verifiedBy, lucentoCheckedAt = lucentoCheckedAt),
        row(token, status, EntitlementSource.PLAY_LIFETIME_SERVICES, verifiedBy, lucentoCheckedAt),
    )

    @Test fun the_owners_case_a_refunded_lifetime_play_still_lists_is_released() {
        // Play Console refund without "Remove entitlement": Google says CANCELLED, stored as EXPIRED; Play still lists it.
        val signedIn = lifetimeRows("tok-life", EntitlementStatus.EXPIRED)
        assertEquals(listOf(lifetime()), LifetimeRelease.releasable(signedIn, listOf(lifetime())))

        // Signed out: the purchase check answered for Play's reading on the phone.
        val signedOut = lifetimeRows("tok-life", EntitlementStatus.EXPIRED, Verifier.PLAY_LOCAL, lucentoCheckedAt = now)
        assertEquals(listOf(lifetime()), LifetimeRelease.releasable(signedOut, listOf(lifetime())))
    }

    @Test fun a_charged_back_or_revoked_lifetime_is_released_too() {
        val rows = lifetimeRows("tok-life", EntitlementStatus.REVOKED)
        assertEquals(listOf(lifetime()), LifetimeRelease.releasable(rows, listOf(lifetime())))
    }

    @Test fun a_live_lifetime_is_never_released() {
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(lifetimeRows("tok-life", EntitlementStatus.ACTIVE), listOf(lifetime())))
        assertEquals("nothing known about it yet", emptyList<PurchaseRecord>(), LifetimeRelease.releasable(emptyList(), listOf(lifetime())))
    }

    @Test fun the_included_year_ending_is_not_the_lifetime_ending() {
        val rows = listOf(
            row("tok-life", EntitlementStatus.ACTIVE),
            row("tok-life", EntitlementStatus.EXPIRED, EntitlementSource.PLAY_LIFETIME_SERVICES),
        )
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(rows, listOf(lifetime())))
    }

    @Test fun a_row_of_the_same_purchase_that_still_grants_keeps_it() {
        val rows = listOf(
            row("tok-life", EntitlementStatus.EXPIRED),
            row("tok-life", EntitlementStatus.ACTIVE, EntitlementSource.PLAY_LIFETIME_SERVICES),
        )
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(rows, listOf(lifetime())))
    }

    @Test fun only_the_ended_token_is_released_never_a_new_purchase() {
        // Refunded once, bought again: Play lists the new one (and, if it lags, the old one).
        val rows = lifetimeRows("tok-old", EntitlementStatus.EXPIRED) + lifetimeRows("tok-new", EntitlementStatus.ACTIVE)
        val owned = listOf(lifetime("tok-old"), lifetime("tok-new"))
        assertEquals(listOf(lifetime("tok-old")), LifetimeRelease.releasable(rows, owned))
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(rows, listOf(lifetime("tok-new"))))
    }

    @Test fun a_pending_payment_and_a_paused_lifetime_are_kept() {
        val pending = lifetime(pending = true)
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(lifetimeRows("tok-life", EntitlementStatus.EXPIRED), listOf(pending)))
        // Paused for want of a check (L-2, L-6) is not ended: the row still says ACTIVE.
        val paused = lifetimeRows("tok-life", EntitlementStatus.ACTIVE, Verifier.PLAY_LOCAL)
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(paused, listOf(lifetime())))
    }

    @Test fun only_lucentos_server_can_end_a_lifetime_for_release() {
        // A Play reading the purchase check never answered can't have been ended by the server.
        val unchecked = lifetimeRows("tok-life", EntitlementStatus.EXPIRED, Verifier.PLAY_LOCAL, lucentoCheckedAt = null)
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(unchecked, listOf(lifetime())))
    }

    @Test fun subscriptions_are_never_released() {
        val rows = listOf(
            row("tok-sub", EntitlementStatus.REVOKED, EntitlementSource.PLAY_SUBSCRIPTION, product = PlayProducts.PRO_SUBSCRIPTION),
        )
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(rows, listOf(subscription())))
        // Even a subscription token that somehow matches an ended Lifetime row.
        assertEquals(emptyList<PurchaseRecord>(), LifetimeRelease.releasable(lifetimeRows("tok-sub", EntitlementStatus.EXPIRED), listOf(subscription())))
    }
}
