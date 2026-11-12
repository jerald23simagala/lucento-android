package com.lucento.app.data.entitlement

import com.lucento.app.data.billing.FakePurchaseSource
import com.lucento.app.data.billing.PurchaseQuery
import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.ProFeature
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.PurchaseRecord
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scenarios of docs/MONETIZATION.md §10 that run on the JVM: cache first, Play adds, the server rules, nothing unreachable removes. */
class EntitlementManagerTest {
    private val day = 24 * 60 * 60 * 1000L
    private var now = 1_800_000_000_000L
    private val store = InMemorySnapshotStore()
    private val cache = SealedEntitlementCache(store, HmacSealer(ByteArray(32) { 1 }))
    private val play = FakePurchaseSource()
    private var serverRows: List<Entitlement>? = null
    private val remote = EntitlementRemote { serverRows }

    private fun manager() = EntitlementManager(cache, play, remote, clock = { now })

    private fun lifetimePurchase(boughtAt: Long = now - day) =
        PurchaseRecord(PlayProducts.PRO_LIFETIME, "tok-life", "GPA.life", boughtAt, pending = false, acknowledged = true, autoRenewing = false)

    private fun subscriptionPurchase(pending: Boolean = false) =
        PurchaseRecord(PlayProducts.PRO_SUBSCRIPTION, "tok-sub", "GPA.sub", now - day, pending = pending, acknowledged = true, autoRenewing = true)

    private fun serverLifetime(
        verifiedAt: Long,
        status: EntitlementStatus = EntitlementStatus.ACTIVE,
        tokenHash: String? = null,
        source: EntitlementSource = EntitlementSource.PLAY_LIFETIME,
    ) = Entitlement(
        source = source, status = status,
        grants = if (source == EntitlementSource.PLAY_LIFETIME) setOf(Capability.LOCAL_PRO) else setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED),
        startsAtMillis = verifiedAt, expiresAtMillis = if (source == EntitlementSource.PLAY_LIFETIME) null else verifiedAt + 365 * day,
        productId = PlayProducts.PRO_LIFETIME, purchaseTokenHash = tokenHash, verifiedAtMillis = verifiedAt, verifiedBy = Verifier.SERVER,
    )

    private fun serverGrant(expiresAt: Long = now + 30 * day) = Entitlement(
        source = EntitlementSource.GRANT, status = EntitlementStatus.ACTIVE, grants = Capability.entries.toSet(),
        startsAtMillis = now - day, expiresAtMillis = expiresAt, productId = null, verifiedAtMillis = now, verifiedBy = Verifier.SERVER,
    )

    @Test fun a_new_free_user_is_free_and_every_gate_answers_per_the_catalog() = runTest {
        val m = manager()
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertEquals(Access.Allowed, m.check(ProFeature.MONEY_WALLETS, 2))
        assertTrue(m.check(ProFeature.MONEY_WALLETS, 3) is Access.LimitReached)
        assertTrue(m.check(ProFeature.CLOUD_SYNC, 0) is Access.Locked)
    }

    @Test fun a_lifetime_works_offline_for_a_month_then_pauses_until_play_or_the_server_answers() = runTest {
        play.setPurchases(listOf(lifetimePurchase()))
        serverRows = null
        manager().refresh()                                   // the purchase is read once, with Play reachable
        assertTrue(store.snapshot != null)
        val readAt = now

        now = readAt + 29 * day
        play.failure = "SERVICE_UNAVAILABLE"                  // Lucento can't read Play (Play Store off, or its copy gone stale)
        val m = manager()                                     // a fresh process
        m.loadCached()
        assertTrue("from the cache, first frame", m.state.value.has(Capability.LOCAL_PRO, now))
        m.refresh()
        assertTrue("a failed Play query removes nothing", m.state.value.has(Capability.LOCAL_PRO, now))
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))

        now = readAt + Entitlement.RECHECK_MILLIS     // a month without a word from Play or the server (L-2)
        m.refresh()
        assertFalse(m.state.value.has(Capability.LOCAL_PRO, now))
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertTrue("paused, not refunded", m.state.value.awaitingCheck(now))
        assertTrue(m.check(ProFeature.FOREIGN_CURRENCY_WALLETS, 0) is Access.Locked)

        play.failure = null                                   // Play answers again
        m.refresh()
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
        assertFalse(m.state.value.awaitingCheck(now))
    }

    @Test fun past_the_included_year_local_pro_stays_while_play_keeps_confirming_it() = runTest {
        play.setPurchases(listOf(lifetimePurchase()))
        val m = manager()
        m.refresh()
        now += 400 * day
        m.refresh()                                           // Play read again
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
        assertFalse(m.state.value.has(Capability.CLOUD, now))
        assertEquals(DisplayPlan.LIFETIME, m.state.value.displayPlan(now))
        assertEquals(Access.Allowed, m.check(ProFeature.FOREIGN_CURRENCY_WALLETS, 0))
        assertTrue(m.check(ProFeature.CLOUD_SYNC, 0) is Access.Locked)
    }

    @Test fun a_lifetime_only_the_server_knows_counts_for_a_month_after_the_phone_last_heard_from_it() = runTest {
        play.setPurchases(emptyList())                        // bought with another Google account: only the server knows
        serverRows = listOf(serverLifetime(verifiedAt = now - 200 * day))
        val m = manager()
        m.refresh()                                           // heard from the server now
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
        val heardAt = now

        serverRows = null                                     // the server unreachable from here on
        now = heardAt + Entitlement.RECHECK_MILLIS - 1
        m.refresh()
        assertTrue("within the month", m.state.value.has(Capability.LOCAL_PRO, now))
        // And after a restart, from the cache.
        val restarted = manager()
        restarted.loadCached()
        assertTrue(restarted.state.value.has(Capability.LOCAL_PRO, now))

        now = heardAt + Entitlement.RECHECK_MILLIS
        assertFalse(m.state.value.has(Capability.LOCAL_PRO, now))
        assertTrue(m.state.value.awaitingCheck(now))

        serverRows = listOf(serverLifetime(verifiedAt = heardAt - 200 * day)) // the server answers again
        m.refresh()
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
    }

    private fun serverSubscription(verifiedAt: Long, expiresAt: Long, tokenHash: String? = null) = Entitlement(
        source = EntitlementSource.PLAY_SUBSCRIPTION, status = EntitlementStatus.ACTIVE, grants = Capability.entries.toSet(),
        startsAtMillis = verifiedAt, expiresAtMillis = expiresAt, productId = PlayProducts.PRO_SUBSCRIPTION, basePlanId = "annual",
        purchaseTokenHash = tokenHash, verifiedAtMillis = verifiedAt, verifiedBy = Verifier.SERVER, autoRenewing = true,
    )

    @Test fun a_refunded_annual_subscriber_who_blocks_the_server_is_paused_within_a_month() = runTest {
        play.setPurchases(listOf(subscriptionPurchase()))
        serverRows = listOf(serverSubscription(verifiedAt = now, expiresAt = now + 360 * day))
        val m = manager()
        m.refresh()
        assertEquals(DisplayPlan.PRO, m.state.value.displayPlan(now))
        val heardAt = now

        play.setPurchases(emptyList())                        // refunded with revoke: Play no longer lists it
        serverRows = null                                     // and Lucento's server is blocked from here on
        now = heardAt + Entitlement.RECHECK_MILLIS - 1
        m.refresh()
        assertTrue("the cached server row, within the month", m.state.value.has(Capability.LOCAL_PRO, now))
        now = heardAt + Entitlement.RECHECK_MILLIS
        assertFalse("not the rest of the paid year", m.state.value.has(Capability.LOCAL_PRO, now))
        assertTrue(m.state.value.awaitingCheck(now))
    }

    @Test fun an_honest_subscriber_stays_pro_past_a_month_while_play_keeps_confirming_even_with_the_server_down() = runTest {
        play.setPurchases(listOf(subscriptionPurchase()))
        serverRows = listOf(serverSubscription(verifiedAt = now, expiresAt = now + 360 * day))
        val m = manager()
        m.refresh()
        serverRows = null                                     // the server unreachable for two months
        now += 60 * day
        m.refresh()                                           // but Play answers
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
        assertEquals(DisplayPlan.PRO, m.state.value.displayPlan(now))
        assertFalse(m.state.value.awaitingCheck(now))
    }

    @Test fun a_purchase_the_server_revoked_stays_off_while_plays_copy_on_the_phone_still_lists_it() = runTest {
        play.setPurchases(listOf(lifetimePurchase()))         // Play's copy on the phone hasn't caught up with the refund
        val hash = PurchaseInterpreter.tokenHash("tok-life")
        serverRows = listOf(
            serverLifetime(verifiedAt = now, status = EntitlementStatus.REVOKED, tokenHash = hash),
            serverLifetime(verifiedAt = now, status = EntitlementStatus.REVOKED, tokenHash = hash, source = EntitlementSource.PLAY_LIFETIME_SERVICES),
        )
        val m = manager()
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertFalse(m.state.value.has(Capability.CLOUD, now))

        serverRows = null                                     // offline: no server answer to settle it
        m.refresh()
        assertEquals("still off offline", DisplayPlan.FREE, m.state.value.displayPlan(now))
        m.applyPlayUpdate(PurchaseQuery.Success(listOf(lifetimePurchase())))
        assertEquals("still off after a Play push", DisplayPlan.FREE, m.state.value.displayPlan(now))
        val restarted = manager()
        restarted.loadCached()
        assertEquals("still off after a restart", DisplayPlan.FREE, restarted.state.value.displayPlan(now))

        play.setPurchases(listOf(lifetimePurchase().copy(purchaseToken = "tok-life-2"))) // bought again
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))
    }

    @Test fun a_successful_play_query_listing_nothing_drops_the_phones_rows_but_not_the_servers() = runTest {
        play.setPurchases(listOf(subscriptionPurchase()))
        serverRows = listOf(serverGrant())
        val m = manager()
        m.refresh()
        assertEquals(2, m.state.value.entitlements.size)

        play.setPurchases(emptyList())                        // another Google account in Play, or the subscription lapsed
        serverRows = null                                     // and the server is unreachable
        m.refresh()
        assertEquals(listOf(EntitlementSource.GRANT), m.state.value.entitlements.map { it.source })
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
    }

    @Test fun the_server_overrides_the_phone_for_a_key_it_knows_including_a_revocation() = runTest {
        play.setPurchases(listOf(lifetimePurchase()))
        val m = manager()
        m.refresh()
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))

        serverRows = listOf(
            Entitlement(
                source = EntitlementSource.PLAY_LIFETIME, status = EntitlementStatus.REVOKED, grants = setOf(Capability.LOCAL_PRO),
                startsAtMillis = now - day, expiresAtMillis = null, productId = PlayProducts.PRO_LIFETIME,
                verifiedAtMillis = now, verifiedBy = Verifier.SERVER,
            ),
        )
        m.refresh()
        assertFalse("refund with revoke ends it", m.state.value.has(Capability.LOCAL_PRO, now))
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        // The services row the phone derived stays (the server never listed that key) but a REVOKED Lifetime reads Free.
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
    }

    @Test fun a_pending_purchase_grants_nothing_and_completes_through_the_play_update() = runTest {
        play.setPurchases(listOf(subscriptionPurchase(pending = true)))
        val m = manager()
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertTrue(m.state.value.pending() != null)

        m.applyPlayUpdate(PurchaseQuery.Success(listOf(subscriptionPurchase(pending = false))))
        assertEquals(DisplayPlan.PRO, m.state.value.displayPlan(now))
        assertTrue(m.state.value.pending() == null)
        // And it was persisted for the next start.
        val fresh = manager()
        fresh.loadCached()
        assertEquals(DisplayPlan.PRO, fresh.state.value.displayPlan(now))
    }

    @Test fun a_failed_play_update_changes_nothing() = runTest {
        play.setPurchases(listOf(lifetimePurchase()))
        val m = manager()
        m.refresh()
        m.applyPlayUpdate(PurchaseQuery.Failed("DISCONNECTED"))
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
    }

    @Test fun an_expired_subscription_is_free_after_the_offline_grace_without_any_server_contact() = runTest {
        play.setPurchases(listOf(subscriptionPurchase()))
        val m = manager()
        m.refresh()
        assertTrue(m.state.value.has(Capability.CLOUD, now))
        // Play's local horizon passes and the phone cannot see Play again.
        now += 35 * day + Entitlement.OFFLINE_GRACE_MILLIS
        play.failure = "DISCONNECTED"
        m.refresh()
        assertFalse(m.state.value.has(Capability.CLOUD, now))
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
    }

    @Test fun access_flow_follows_the_plan_and_the_usage() = runTest {
        val m = manager()
        m.refresh()
        assertTrue(m.access(ProFeature.SAVINGS_GOALS, flowOf(2)).first() is Access.LimitReached)
        play.setPurchases(listOf(subscriptionPurchase()))
        m.refresh()
        assertEquals(Access.Allowed, m.access(ProFeature.SAVINGS_GOALS, flowOf(2)).first())
        assertTrue(m.access(ProFeature.SAVINGS_GOALS, flowOf(20)).first() is Access.LimitReached)
    }

    @Test fun refresh_says_what_it_reached_and_marks_the_manager_ready() = runTest {
        val m = manager()
        assertFalse("not ready before the first refresh", m.ready.value)
        m.loadCached()
        assertFalse("the cache alone isn't a refresh", m.ready.value)
        serverRows = listOf(serverGrant())
        assertEquals(RefreshOutcome(playReached = true, serverReached = true), m.refresh())
        assertTrue(m.ready.value)
        play.failure = "DISCONNECTED"
        serverRows = null
        assertEquals(RefreshOutcome(playReached = false, serverReached = false), m.refresh())
        assertTrue("nothing reached removes nothing", m.state.value.has(Capability.LOCAL_PRO, now))
    }

    @Test fun a_tampered_cache_reads_as_free_until_play_or_the_server_restore_it() = runTest {
        play.setPurchases(listOf(lifetimePurchase()))
        manager().refresh()
        val sealed = store.snapshot!!
        store.snapshot = sealed.copy(payload = sealed.payload + " ")
        val m = manager()
        m.loadCached()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        m.refresh()                                           // Play is reachable: restored in one step
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
    }
}
