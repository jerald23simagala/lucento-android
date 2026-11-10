package com.lucento.app.data.entitlement

import com.lucento.app.data.billing.FakePurchaseSource
import com.lucento.app.data.billing.ProxyPurchaseVerifier
import com.lucento.app.data.billing.PurchaseVerifier
import com.lucento.app.data.billing.VerifyOutcome
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.ProGates
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.PurchaseRecord
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security audit F-03 and F-12 on the phone: a Google Play purchase made for a different Lucento account (two Lucento
 * accounts sharing one Google Play account) is never sent for the account signed in now; the server's new answers
 * (other_account, test_purchase_refused) are read as final. Also the plan fingerprint the sync cooldown compares (F-01).
 */
class PurchaseAccountBindingTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val me = "11111111-1111-4111-8111-111111111111"
    private val partnerOnSameGoogleAccount = "22222222-2222-4222-8222-222222222222"
    private val cache = SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(ByteArray(32) { 3 }))
    private val play = FakePurchaseSource()
    private val remote = EntitlementRemote { emptyList() }
    private val sent = mutableListOf<String>()
    private var answer: VerifyOutcome = VerifyOutcome.VERIFIED
    private val verifier = PurchaseVerifier { p -> sent += p.purchaseToken; answer }

    private fun manager(signedIn: String? = me) = EntitlementManager(
        cache, play, remote, verifier = verifier, clock = { now },
        signedInAccountHash = { signedIn?.let(PurchaseInterpreter::obfuscatedAccountId) },
    )

    private fun lifetime(madeFor: String?) = PurchaseRecord(
        PlayProducts.PRO_LIFETIME, "tok-life", "GPA.life", now - day, pending = false, acknowledged = false, autoRenewing = false,
        obfuscatedAccountId = madeFor?.let(PurchaseInterpreter::obfuscatedAccountId),
    )

    @Test fun a_purchase_made_for_another_lucento_account_is_never_sent_for_this_one() = runTest {
        play.setPurchases(listOf(lifetime(madeFor = partnerOnSameGoogleAccount)))
        val m = manager()
        m.refresh()
        assertEquals(emptyList<String>(), sent)
        assertEquals(VerifyOutcome.OTHER_ACCOUNT, m.verificationIssue.value)
        assertEquals("Play still has it acknowledged (it is the buyer's real purchase)", listOf("tok-life"), play.acknowledged)
        m.refresh()
        assertEquals("not asked again", emptyList<String>(), sent)
    }

    @Test fun the_buyers_own_purchase_is_sent() = runTest {
        play.setPurchases(listOf(lifetime(madeFor = me)))
        val m = manager()
        m.refresh()
        assertEquals(listOf("tok-life"), sent)
        assertNull(m.verificationIssue.value)
    }

    @Test fun a_purchase_made_while_signed_out_is_sent_under_the_documented_policy() = runTest {
        play.setPurchases(listOf(lifetime(madeFor = null)))
        manager().refresh()
        assertEquals(listOf("tok-life"), sent)
    }

    @Test fun the_servers_other_account_and_test_purchase_answers_are_final_and_shown() = runTest {
        play.setPurchases(listOf(lifetime(madeFor = null)))
        answer = VerifyOutcome.OTHER_ACCOUNT
        val m = manager()
        m.refresh()
        m.refresh()
        assertEquals("sent once", listOf("tok-life"), sent)
        assertEquals(VerifyOutcome.OTHER_ACCOUNT, m.verificationIssue.value)
        assertTrue(VerifyOutcome.TEST_PURCHASE_REFUSED.settled)
    }

    @Test fun the_verify_endpoint_answers_for_f03_and_f12() {
        assertEquals(VerifyOutcome.OTHER_ACCOUNT, ProxyPurchaseVerifier.outcome(409, """{"error":"other_account"}"""))
        assertEquals(VerifyOutcome.TEST_PURCHASE_REFUSED, ProxyPurchaseVerifier.outcome(403, """{"error":"test_purchase_refused"}"""))
        assertEquals("another 403 is not final", VerifyOutcome.UNAVAILABLE, ProxyPurchaseVerifier.outcome(403, null))
    }

    @Test fun made_for_another_account_needs_both_ids() {
        assertFalse(PurchaseInterpreter.madeForAnotherAccount(lifetime(madeFor = null), PurchaseInterpreter.obfuscatedAccountId(me)))
        assertFalse(PurchaseInterpreter.madeForAnotherAccount(lifetime(madeFor = partnerOnSameGoogleAccount), null))
        assertFalse(PurchaseInterpreter.madeForAnotherAccount(lifetime(madeFor = me), PurchaseInterpreter.obfuscatedAccountId(me)))
        assertTrue(PurchaseInterpreter.madeForAnotherAccount(lifetime(madeFor = partnerOnSameGoogleAccount), PurchaseInterpreter.obfuscatedAccountId(me)))
    }

    @Test fun the_cloud_plan_fingerprint_ignores_re_reads_and_follows_renewals() {
        fun row(expires: Long, verifiedAt: Long) = Entitlement(
            source = EntitlementSource.PLAY_SUBSCRIPTION, status = EntitlementStatus.ACTIVE,
            grants = setOf(Capability.LOCAL_PRO, Capability.CLOUD), startsAtMillis = now - day, expiresAtMillis = expires,
            productId = PlayProducts.PRO_SUBSCRIPTION, purchaseTokenHash = "h", verifiedAtMillis = verifiedAt, verifiedBy = Verifier.SERVER,
        )
        val before = ProGates.cloudPlanFingerprint(EntitlementState(listOf(row(now + day, now))))
        val reread = ProGates.cloudPlanFingerprint(EntitlementState(listOf(row(now + day, now + 5_000))))
        val renewed = ProGates.cloudPlanFingerprint(EntitlementState(listOf(row(now + 31 * day, now + 5_000))))
        assertEquals("a re-read changes nothing", before, reread)
        assertNotEquals("a renewal changes it", before, renewed)
        assertEquals("no CLOUD row: empty", "", ProGates.cloudPlanFingerprint(EntitlementState.FREE))
    }
}
