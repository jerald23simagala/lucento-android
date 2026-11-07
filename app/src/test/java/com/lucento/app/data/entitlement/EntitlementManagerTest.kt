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

    @Test fun lifetime_bought_then_offline_for_sixty_days_with_play_and_server_unreachable_still_owns_local_pro() = runTest {
        play.setPurchases(listOf(lifetimePurchase()))
        serverRows = null
        manager().refresh()                                   // the purchase is verified once, with Play reachable
        assertTrue(store.snapshot != null)

        now += 60 * day
        play.failure = "SERVICE_UNAVAILABLE"                  // airplane mode: Play's query fails
        val m = manager()                                     // a fresh process
        m.loadCached()
        assertTrue("from the cache, first frame", m.state.value.has(Capability.LOCAL_PRO, now))
        m.refresh()
        assertTrue("a failed Play query removes nothing (L-2)", m.state.value.has(Capability.LOCAL_PRO, now))
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))

        now += 400 * day                                      // past the included year, still offline
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
        assertFalse(m.state.value.has(Capability.CLOUD, now))
        assertEquals(DisplayPlan.LIFETIME, m.state.value.displayPlan(now))
        assertEquals(Access.Allowed, m.check(ProFeature.FOREIGN_CURRENCY_WALLETS, 0))
        assertTrue(m.check(ProFeature.CLOUD_SYNC, 0) is Access.Locked)
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
