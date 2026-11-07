package com.lucento.app.data.entitlement

import com.lucento.app.data.billing.FakePurchaseSource
import com.lucento.app.data.billing.PurchaseQuery
import com.lucento.app.data.billing.PurchaseVerifier
import com.lucento.app.data.billing.VerifyOutcome
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementStatus
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
 * Lucento Pro, Phase 4 (docs/MONETIZATION.md §9): after each Play reading the manager tells the server what it lacks, then
 * acknowledges what Play hasn't had acknowledged — whatever the server said — and leaves pending payments alone.
 */
class PurchaseConfirmationTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val cache = SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(ByteArray(32) { 2 }))
    private val play = FakePurchaseSource()
    private var serverRows: List<Entitlement>? = emptyList()
    private val fetches = mutableListOf<Int>()
    private val remote = EntitlementRemote { fetches += 1; serverRows }

    private val sent = mutableListOf<String>()
    private var answer: (PurchaseRecord) -> VerifyOutcome = { VerifyOutcome.VERIFIED }
    private val verifier = PurchaseVerifier { p -> sent += p.purchaseToken; answer(p) }

    private fun manager(verifier: PurchaseVerifier? = this.verifier) = EntitlementManager(cache, play, remote, clock = { now }, verifier = verifier)

    private fun lifetime(acknowledged: Boolean = false, pending: Boolean = false) =
        PurchaseRecord(PlayProducts.PRO_LIFETIME, "tok-life", "GPA.life", now - day, pending = pending, acknowledged = acknowledged, autoRenewing = false)

    /** The rows the server writes once it has verified the Lifetime purchase (SERVER_CONTRACT.md §3 step 4). */
    private fun serverLifetimeRows(): List<Entitlement> {
        val hash = PurchaseInterpreter.tokenHash("tok-life")
        return listOf(
            Entitlement(
                source = EntitlementSource.PLAY_LIFETIME, status = EntitlementStatus.ACTIVE, grants = setOf(Capability.LOCAL_PRO),
                startsAtMillis = now - day, expiresAtMillis = null, productId = PlayProducts.PRO_LIFETIME, purchaseTokenHash = hash,
                verifiedAtMillis = now, verifiedBy = Verifier.SERVER,
            ),
            Entitlement(
                source = EntitlementSource.PLAY_LIFETIME_SERVICES, status = EntitlementStatus.ACTIVE,
                grants = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), startsAtMillis = now - day,
                expiresAtMillis = now + 364 * day, productId = PlayProducts.PRO_LIFETIME, purchaseTokenHash = hash,
                verifiedAtMillis = now, verifiedBy = Verifier.SERVER,
            ),
        )
    }

    @Test fun a_new_purchase_goes_to_the_server_then_is_acknowledged_and_the_servers_rows_are_read_again() = runTest {
        play.setPurchases(listOf(lifetime()))
        answer = { serverRows = serverLifetimeRows(); VerifyOutcome.VERIFIED }
        val m = manager()
        m.refresh()
        assertEquals(listOf("tok-life"), sent)
        assertEquals(listOf("tok-life"), play.acknowledged)
        assertEquals("fetched, verified, fetched again", 2, fetches.size)
        assertTrue(m.state.value.entitlements.any { it.verifiedBy == Verifier.SERVER && it.source == EntitlementSource.PLAY_LIFETIME })
        assertNull(m.verificationIssue.value)
    }

    @Test fun a_purchase_the_server_already_holds_is_not_sent_again() = runTest {
        play.setPurchases(listOf(lifetime(acknowledged = true)))
        serverRows = serverLifetimeRows()
        manager().refresh()
        assertEquals(emptyList<String>(), sent)
        assertEquals("already acknowledged", emptyList<String>(), play.acknowledged)
    }

    @Test fun a_pending_payment_is_neither_sent_nor_acknowledged() = runTest {
        play.setPurchases(listOf(lifetime(pending = true)))
        manager().refresh()
        assertEquals(emptyList<String>(), sent)
        assertEquals(emptyList<String>(), play.acknowledged)
    }

    @Test fun signed_out_or_offline_still_acknowledges_and_tries_the_server_again_next_time() = runTest {
        play.setPurchases(listOf(lifetime()))
        answer = { VerifyOutcome.SIGNED_OUT }
        val m = manager()
        m.refresh()
        assertEquals("Play vouches for it: acknowledged within the three days either way", listOf("tok-life"), play.acknowledged)
        assertTrue("the plan holds from Play's reading alone", m.state.value.has(Capability.LOCAL_PRO, now))
        answer = { VerifyOutcome.UNAVAILABLE }
        m.refresh()
        assertEquals("asked again on the next refresh", listOf("tok-life", "tok-life"), sent)
    }

    @Test fun linked_to_another_account_is_reported_once_and_the_phone_keeps_its_plan() = runTest {
        play.setPurchases(listOf(lifetime()))
        answer = { VerifyOutcome.LINKED_ELSEWHERE }
        val m = manager()
        m.refresh()
        m.refresh()
        assertEquals("a settled answer isn't asked for again in this process", listOf("tok-life"), sent)
        assertEquals(VerifyOutcome.LINKED_ELSEWHERE, m.verificationIssue.value)
        assertTrue(m.state.value.has(Capability.LOCAL_PRO, now))
        assertEquals(listOf("tok-life"), play.acknowledged)
    }

    @Test fun after_an_account_switch_the_new_account_is_asked_and_the_old_note_goes() = runTest {
        play.setPurchases(listOf(lifetime(acknowledged = true)))
        answer = { VerifyOutcome.LINKED_ELSEWHERE }
        val m = manager()
        m.refresh()
        assertEquals(VerifyOutcome.LINKED_ELSEWHERE, m.verificationIssue.value)
        m.accountChanged()
        assertNull(m.verificationIssue.value)
        answer = { serverRows = serverLifetimeRows(); VerifyOutcome.VERIFIED }
        m.refresh()
        assertEquals("asked again for the new account", listOf("tok-life", "tok-life"), sent)
        assertNull(m.verificationIssue.value)
    }

    @Test fun a_purchase_completing_while_the_app_runs_is_confirmed_too() = runTest {
        val m = manager()
        m.refresh()
        answer = { serverRows = serverLifetimeRows(); VerifyOutcome.VERIFIED }
        m.applyPlayUpdate(PurchaseQuery.Success(listOf(lifetime())))
        assertEquals(listOf("tok-life"), sent)
        assertEquals(listOf("tok-life"), play.acknowledged)
        assertTrue(m.state.value.entitlements.any { it.verifiedBy == Verifier.SERVER })
    }

    @Test fun test_builds_without_a_verifier_only_acknowledge() = runTest {
        play.setPurchases(listOf(lifetime()))
        manager(verifier = null).refresh()
        assertEquals(emptyList<String>(), sent)
        assertEquals(listOf("tok-life"), play.acknowledged)
    }
}
