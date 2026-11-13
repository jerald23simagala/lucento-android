package com.lucento.app.data.entitlement

import com.lucento.app.data.billing.FakePurchaseSource
import com.lucento.app.data.billing.PurchaseChecker
import com.lucento.app.data.billing.PurchaseQuery
import com.lucento.app.data.billing.PurchaseVerdict
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.PurchaseRecord
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The purchase check that needs no account (owner decisions 2026-11-13, docs/MONETIZATION.md §5.3 L-6): what the owner
 * asked to be sure of. A refunded purchase can't come back by signing out, clearing data, reinstalling or switching Play
 * profiles. The only thing left is the lag of Google Play's copy on the phone, limited by 3 days, or 90 days for a
 * purchase confirmed before its refund.
 */
class PurchaseCheckTest {
    private val day = 24 * 60 * 60 * 1000L
    private var now = 1_800_000_000_000L
    private val store = InMemorySnapshotStore()
    private val cache = SealedEntitlementCache(store, HmacSealer(ByteArray(32) { 3 }))
    private val play = FakePurchaseSource()
    private var serverRows: List<Entitlement>? = null

    /** What Lucento's server answers; null: unreachable (offline, or Lucento blocked). */
    private var answer: EntitlementStatus? = null
    private var calls = 0
    private val checker = PurchaseChecker { calls++; answer?.let { PurchaseVerdict.Known(it) } ?: PurchaseVerdict.Unavailable }

    private fun manager() = EntitlementManager(cache, play, { serverRows }, checker = checker, clock = { now })

    private fun lifetime(boughtAt: Long, token: String = "tok-life") = PurchaseRecord(
        PlayProducts.PRO_LIFETIME, token, "GPA.life", boughtAt, pending = false, acknowledged = true, autoRenewing = false,
        signedData = """{"purchaseToken":"$token"}""", signature = "sig",
    )

    @Test fun the_owners_case_a_refunded_lifetime_after_signing_out_and_clearing_data_stays_off() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 2 * day)))  // Play's copy hasn't caught up with the refund
        answer = EntitlementStatus.EXPIRED                              // the server: Google says cancelled
        val m = manager()                                               // nothing cached: data cleared, signed out
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertFalse(m.state.value.has(Capability.CLOUD, now))
        assertFalse("ended, not waiting for a check", m.state.value.awaitingCheck(now))

        answer = null                                                   // offline from here on
        m.applyPlayUpdate(PurchaseQuery.Success(listOf(lifetime(boughtAt = now - 2 * day))))
        assertEquals("a Play push can't bring it back", DisplayPlan.FREE, m.state.value.displayPlan(now))
        val restarted = manager()
        restarted.refresh()
        assertEquals("nor a restart", DisplayPlan.FREE, restarted.state.value.displayPlan(now))
        val asked = calls
        now += 30 * day
        restarted.refresh()
        assertEquals("an ended purchase isn't asked about again", asked, calls)

        play.setPurchases(listOf(lifetime(boughtAt = now, token = "tok-life-2")))   // bought again: a new token
        answer = EntitlementStatus.ACTIVE
        restarted.refresh()
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, restarted.state.value.displayPlan(now))
    }

    @Test fun lucento_unreachable_after_a_data_clear_an_old_purchase_waits_for_the_check_and_a_new_one_has_three_days() = runTest {
        answer = null
        play.setPurchases(listOf(lifetime(boughtAt = now - 400 * day)))
        val m = manager()
        m.refresh()
        assertEquals("Google Play's word alone isn't enough for an old purchase", DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertTrue("paused: it says Lucento needs to check it", m.state.value.awaitingCheck(now))
        answer = EntitlementStatus.ACTIVE                               // one connection
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME, m.state.value.displayPlan(now))

        // A purchase made a day ago, before the server could confirm it: Play's word carries it to 3 days after buying.
        val fresh = PurchaseCheckTest()
        fresh.answer = null
        val bought = fresh.now - day
        fresh.play.setPurchases(listOf(fresh.lifetime(boughtAt = bought)))
        val f = fresh.manager()
        f.refresh()
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, f.state.value.displayPlan(fresh.now))
        fresh.now = bought + Entitlement.FIRST_CONFIRMATION_MILLIS
        f.refresh()
        assertEquals(DisplayPlan.FREE, f.state.value.displayPlan(fresh.now))
        assertTrue(f.state.value.awaitingCheck(fresh.now))
    }

    @Test fun a_confirmed_lifetime_works_offline_for_ninety_days_after_the_last_confirmation_then_pauses() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 400 * day)))
        answer = EntitlementStatus.ACTIVE
        val m = manager()
        m.refresh()
        val confirmedAt = now
        assertEquals(DisplayPlan.LIFETIME, m.state.value.displayPlan(now))

        answer = null                                                   // Lucento unreachable; Play keeps answering
        now = confirmedAt + Entitlement.CONFIRMATION_CAP_MILLIS - day
        m.refresh()
        assertEquals("still Pro on Play's word within the cap", DisplayPlan.LIFETIME, m.state.value.displayPlan(now))
        now = confirmedAt + Entitlement.CONFIRMATION_CAP_MILLIS
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertTrue("paused, not refunded", m.state.value.awaitingCheck(now))
        answer = EntitlementStatus.ACTIVE
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME, m.state.value.displayPlan(now))
    }

    @Test fun switching_play_profiles_never_adds_a_purchase_and_switching_back_to_a_refunded_one_keeps_it_off() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 10 * day)))
        answer = EntitlementStatus.ACTIVE
        val m = manager()
        m.refresh()
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))

        // Refunded; then a Play profile that owns nothing: Play lists nothing, so nothing is unlocked.
        play.setPurchases(emptyList())
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))

        // Back to the refunded profile while Lucento is unreachable: a new reading, older than 3 days, unconfirmed.
        answer = null
        play.setPurchases(listOf(lifetime(boughtAt = now - 10 * day)))
        m.refresh()
        assertEquals("switching back can't restart the 3 days", DisplayPlan.FREE, m.state.value.displayPlan(now))
        // Reachable again: the server says refunded, and it stays off.
        answer = EntitlementStatus.REVOKED
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertFalse(m.state.value.awaitingCheck(now))
    }

    @Test fun a_refund_reaches_an_online_phone_at_the_next_check_and_checks_are_spaced_out() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 40 * day)))
        answer = EntitlementStatus.ACTIVE
        val m = manager()
        m.refresh()
        assertEquals(1, calls)
        now += 60 * 60 * 1000L                                          // an hour later, the app comes back
        answer = EntitlementStatus.EXPIRED                              // refunded meanwhile
        m.refresh()
        assertEquals("not asked again within the interval", 1, calls)
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
        now += EntitlementManager.LUCENTO_CHECK_INTERVAL_MILLIS
        m.refresh()
        assertEquals(2, calls)
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
    }

    @Test fun a_purchase_the_signed_in_accounts_rows_cover_isnt_checked_again() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 40 * day)))
        val hash = PurchaseInterpreter.tokenHash("tok-life")
        // The server writes both rows of a Lifetime (apply_play_purchase), as here.
        val life = Entitlement(
            source = EntitlementSource.PLAY_LIFETIME, status = EntitlementStatus.ACTIVE, grants = setOf(Capability.LOCAL_PRO),
            startsAtMillis = now - 40 * day, expiresAtMillis = null, productId = PlayProducts.PRO_LIFETIME, purchaseTokenHash = hash,
            verifiedAtMillis = now, verifiedBy = Verifier.SERVER,
        )
        serverRows = listOf(
            life,
            life.copy(
                source = EntitlementSource.PLAY_LIFETIME_SERVICES, grants = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED),
                expiresAtMillis = now + 325 * day,
            ),
        )
        answer = EntitlementStatus.ACTIVE
        val m = manager()
        m.refresh()
        assertEquals(0, calls)
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
    }

    @Test fun a_pending_payment_waits_for_google_play_before_any_check() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now).copy(pending = true)))
        answer = EntitlementStatus.ACTIVE
        val m = manager()
        m.refresh()
        assertEquals(0, calls)
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
    }

    @Test fun a_lifetime_past_its_included_year_is_still_checked_and_stays() = runTest {
        // Its services row ends with the year; the Lifetime itself must keep being confirmed.
        play.setPurchases(listOf(lifetime(boughtAt = now - 400 * day)))
        answer = EntitlementStatus.ACTIVE
        val m = manager()
        m.refresh()
        now += 80 * day
        m.refresh()                                                     // Play and Lucento both answer
        now += 80 * day                                                 // 160 days after the first confirmation
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME, m.state.value.displayPlan(now))
        assertTrue(calls >= 3)
    }
}
