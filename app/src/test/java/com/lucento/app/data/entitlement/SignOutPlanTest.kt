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
import com.lucento.app.domain.entitlement.PlanNotices
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.PurchaseRecord
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KNOWN_ISSUES PB-12 (2026-11-14): once nobody is signed in, the account's own rows from the server leave the phone. A
 * grant or a purchase recorded on someone's account used to keep unlocking Pro on a phone they had only signed in to.
 * Google Play's reading stays, keeping the server's last confirmation; what the server said has ended stays; and the
 * "your plan has ended" notice says nothing.
 */
class SignOutPlanTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val cache = SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(ByteArray(32) { 6 }))
    private val play = FakePurchaseSource()
    private val memory = InMemoryPlanMemory()

    /** The account's rows on Lucento's server; null: signed out (never asked) or unreachable. */
    private var serverRows: List<Entitlement>? = null

    /** The purchase check's answer; null: Lucento can't be reached. */
    private var checkAnswer: EntitlementStatus? = null
    private val checker = PurchaseChecker { checkAnswer?.let { PurchaseVerdict.Known(it) } ?: PurchaseVerdict.Unavailable }

    private fun manager() = EntitlementManager(cache, play, { serverRows }, checker = checker, planMemory = memory, clock = { now })

    private fun grant() = Entitlement(
        source = EntitlementSource.GRANT, status = EntitlementStatus.ACTIVE, grants = Capability.entries.toSet(),
        startsAtMillis = now - day, expiresAtMillis = now + 300 * day, productId = null, verifiedAtMillis = now,
        verifiedBy = Verifier.SERVER,
    )

    /** Bought ten days ago: past the three days Google Play's word carries a purchase on its own (L-6). */
    private fun lifetime() = PurchaseRecord(
        PlayProducts.PRO_LIFETIME, "tok-life", "GPA.life", now - 10 * day, pending = false, acknowledged = true, autoRenewing = false,
    )

    /** Both rows the server keeps for that Lifetime on the account. */
    private fun serverLifetime(status: EntitlementStatus): List<Entitlement> {
        val life = Entitlement(
            source = EntitlementSource.PLAY_LIFETIME, status = status, grants = setOf(Capability.LOCAL_PRO),
            startsAtMillis = now - 10 * day, expiresAtMillis = null, productId = PlayProducts.PRO_LIFETIME,
            purchaseTokenHash = PurchaseInterpreter.tokenHash("tok-life"), verifiedAtMillis = now - day, verifiedBy = Verifier.SERVER,
        )
        return listOf(
            life,
            life.copy(
                source = EntitlementSource.PLAY_LIFETIME_SERVICES, grants = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED),
                expiresAtMillis = now + 355 * day,
            ),
        )
    }

    @Test fun a_grant_leaves_with_the_account_and_no_plan_ended_notice_follows() = runTest {
        serverRows = listOf(grant())
        val m = manager()
        m.refresh()
        assertEquals(DisplayPlan.PRO, m.state.value.displayPlan(now))

        serverRows = null                                       // signed out: the server isn't asked
        m.signedOut()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        assertEquals("remembered first, so the notice has nothing to say", DisplayPlan.FREE, memory.value)
        assertNull(PlanNotices.after(memory.value, m.state.value.displayPlan(now)))

        val restarted = manager()
        restarted.refresh()
        assertEquals("gone after a restart too", DisplayPlan.FREE, restarted.state.value.displayPlan(now))
    }

    @Test fun the_payers_own_play_purchase_stays_after_signing_out_offline() = runTest {
        play.setPurchases(listOf(lifetime()))
        serverRows = serverLifetime(EntitlementStatus.ACTIVE)    // linked to the account; the server covers it
        val m = manager()
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))

        serverRows = null
        checkAnswer = null                                      // and Lucento can't be reached
        m.signedOut()
        assertEquals("Google Play's own purchase, with the server's last word", DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))
        assertTrue(m.state.value.entitlements.all { it.verifiedBy == Verifier.PLAY_LOCAL })
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))
    }

    @Test fun play_unreachable_at_the_sign_out_the_confirmation_waits_for_its_next_reading() = runTest {
        play.setPurchases(listOf(lifetime()))
        serverRows = serverLifetime(EntitlementStatus.ACTIVE)
        val m = manager()
        m.refresh()

        serverRows = null
        play.failure = "Play is busy"
        m.signedOut()
        play.failure = null
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))
    }

    @Test fun what_the_server_ended_stays_so_a_refund_stays_off() = runTest {
        play.setPurchases(listOf(lifetime()))                   // Play's copy still lists the refunded Lifetime
        serverRows = serverLifetime(EntitlementStatus.EXPIRED)
        val m = manager()
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))

        serverRows = null
        m.signedOut()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))
        m.applyPlayUpdate(PurchaseQuery.Success(listOf(lifetime())))
        assertEquals("a Play push can't bring it back", DisplayPlan.FREE, m.state.value.displayPlan(now))
        val restarted = manager()
        restarted.refresh()
        assertEquals("nor a restart", DisplayPlan.FREE, restarted.state.value.displayPlan(now))
    }

    @Test fun with_nothing_from_the_server_a_sign_out_changes_nothing() = runTest {
        play.setPurchases(listOf(lifetime()))
        checkAnswer = EntitlementStatus.ACTIVE
        val m = manager()
        m.refresh()
        val before = m.state.value
        m.signedOut()
        assertEquals(before, m.state.value)
        assertNull("nothing to remember", memory.value)
    }
}
