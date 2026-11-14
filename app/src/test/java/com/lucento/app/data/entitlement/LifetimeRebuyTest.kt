package com.lucento.app.data.entitlement

import android.app.Activity
import com.lucento.app.data.billing.BillingProblem
import com.lucento.app.data.billing.FakePurchaseSource
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.billing.PurchaseChecker
import com.lucento.app.data.billing.PurchaseQuery
import com.lucento.app.data.billing.PurchaseResult
import com.lucento.app.data.billing.PurchaseVerdict
import com.lucento.app.data.billing.PurchaseVerifier
import com.lucento.app.data.billing.VerifyOutcome
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.PurchaseRecord
import com.lucento.app.domain.entitlement.Verifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Buying Pro Lifetime again after a refund (owner's test 2026-11-13, docs/MONETIZATION.md §5.3 L-7). A Play Console refund
 * without **Remove entitlement** leaves the purchase owned in Google Play. Lucento ends it, so Play refused a new one ("You
 * already own this item") while Lucento showed the free plan. Lucento now releases the purchase it ended, and only that,
 * after asking its server again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LifetimeRebuyTest {
    private val day = 24 * 60 * 60 * 1000L
    private var now = 1_800_000_000_000L
    private val cache = SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(ByteArray(32) { 4 }))
    private val play = FakePurchaseSource()
    private val lifetimePrice = ProductPrice(PlayProducts.PRO_LIFETIME, null, "test-lifetime", "₱3,490.00 (test)", null)

    /** The account's rows on Lucento's server; null: signed out or unreachable. */
    private var serverRows: List<Entitlement>? = null

    /** What the purchase check says about the refunded purchase now (null: no answer); any other purchase is live. */
    private var refundedAnswer: EntitlementStatus? = EntitlementStatus.EXPIRED
    private var checks = 0
    private val checker = PurchaseChecker { p ->
        checks++
        if (p.purchaseToken != "tok-life") return@PurchaseChecker PurchaseVerdict.Known(EntitlementStatus.ACTIVE)
        refundedAnswer?.let { PurchaseVerdict.Known(it) } ?: PurchaseVerdict.Unavailable
    }

    /** What /api/billing/verify does with a new purchase: records it (the default) or fails. */
    private var verifyAnswer: VerifyOutcome = VerifyOutcome.VERIFIED

    private fun lifetime(token: String = "tok-life", boughtAt: Long = now - 2 * day) = PurchaseRecord(
        PlayProducts.PRO_LIFETIME, token, "GPA.life", boughtAt, pending = false, acknowledged = true, autoRenewing = false,
    )

    /** Both rows the server writes for a Lifetime (apply_play_purchase). */
    private fun serverLifetimeRows(token: String, status: EntitlementStatus, boughtAt: Long = now - 2 * day): List<Entitlement> {
        val hash = PurchaseInterpreter.tokenHash(token)
        val life = Entitlement(
            source = EntitlementSource.PLAY_LIFETIME, status = status, grants = setOf(Capability.LOCAL_PRO), startsAtMillis = boughtAt,
            expiresAtMillis = null, productId = PlayProducts.PRO_LIFETIME, purchaseTokenHash = hash, verifiedAtMillis = now,
            verifiedBy = Verifier.SERVER,
        )
        val servicesEnd = boughtAt + PurchaseInterpreter.LIFETIME_SERVICES_MILLIS
        return listOf(
            life,
            life.copy(
                source = EntitlementSource.PLAY_LIFETIME_SERVICES,
                status = if (status.grantsAccess && now >= servicesEnd) EntitlementStatus.EXPIRED else status,
                grants = setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), expiresAtMillis = servicesEnd,
            ),
        )
    }

    /** Signed in, as in a release build: the server records each new purchase it's sent (unless [verifyAnswer] says not). */
    private fun signedIn(withChecker: Boolean = true): EntitlementManager {
        val verifier = PurchaseVerifier { p ->
            if (verifyAnswer == VerifyOutcome.VERIFIED) {
                serverRows = serverRows.orEmpty().filterNot { it.productId == p.productId } +
                    serverLifetimeRows(p.purchaseToken, EntitlementStatus.ACTIVE, boughtAt = p.purchaseTimeMillis)
            }
            verifyAnswer
        }
        return EntitlementManager(cache, play, { serverRows }, verifier = verifier, checker = checker.takeIf { withChecker }, clock = { now })
    }

    private suspend fun owned(): List<String> = (play.queryPurchases() as PurchaseQuery.Success).purchases.map { it.purchaseToken }

    @Test fun the_owners_test_signed_in_the_refunded_lifetime_is_released_and_a_new_one_can_be_bought() = runTest {
        val results = mutableListOf<PurchaseResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { play.results.collect { results += it } }
        play.setPurchases(listOf(lifetime()))                               // refunded, but Play still holds it
        serverRows = serverLifetimeRows("tok-life", EntitlementStatus.EXPIRED) // Google said CANCELLED
        val m = signedIn()
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))

        play.launchPurchase(Activity(), lifetimePrice, obfuscatedAccountId = null)
        assertEquals("what the owner saw", PurchaseResult.Failed(BillingProblem.ALREADY_OWNED), results.last())

        val asked = checks
        assertEquals(ReleaseOutcome(found = 1, released = 1), m.releaseEndedLifetime())
        assertEquals("asked Lucento again right before releasing", asked + 1, checks)
        assertEquals(listOf("tok-life"), play.consumed)
        assertEquals("Play no longer holds it", emptyList<String>(), owned())
        assertEquals("releasing never brings it back", DisplayPlan.FREE, m.state.value.displayPlan(now))

        play.launchPurchase(Activity(), lifetimePrice, obfuscatedAccountId = null)
        assertEquals(PurchaseResult.Completed, results.last())
        m.refresh()                                                          // Play lists the new purchase; the server records it
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))
        assertEquals("only the refunded purchase was ever released", listOf("tok-life"), play.consumed)
    }

    @Test fun signed_in_a_new_lifetime_counts_before_the_server_has_recorded_it() = runTest {
        // QA review 2026-11-13 (KNOWN_ISSUES PB-9): verify fails right after the re-purchase, while the server still answers
        // with the refunded purchase's rows. Play's reading of the new purchase must not be dropped by that answer.
        play.setPurchases(listOf(lifetime()))
        serverRows = serverLifetimeRows("tok-life", EntitlementStatus.EXPIRED)
        val m = signedIn()
        m.refresh()
        m.releaseEndedLifetime()
        play.launchPurchase(Activity(), lifetimePrice, obfuscatedAccountId = null)

        for (failure in listOf(VerifyOutcome.UNAVAILABLE, VerifyOutcome.TEST_PURCHASE_REFUSED)) {
            verifyAnswer = failure
            m.refresh()
            assertEquals("after $failure", DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))
        }
        assertEquals("the server still only knows the refunded one", PurchaseInterpreter.tokenHash("tok-life"),
            serverRows!!.first().purchaseTokenHash)

        verifyAnswer = VerifyOutcome.VERIFIED                                 // recorded at a later refresh
        m.accountChanged()                                                    // (a settled answer is otherwise not resent)
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))
        assertTrue(m.state.value.entitlements.any { it.verifiedBy == Verifier.SERVER && it.status == EntitlementStatus.ACTIVE })
    }

    @Test fun signed_out_the_purchase_checks_answer_is_enough_to_release_it() = runTest {
        play.setPurchases(listOf(lifetime()))
        val m = EntitlementManager(cache, play, { null }, checker = checker, clock = { now })
        m.refresh()
        assertEquals(DisplayPlan.FREE, m.state.value.displayPlan(now))

        assertEquals(ReleaseOutcome(found = 1, released = 1), m.releaseEndedLifetime())
        assertEquals(emptyList<String>(), owned())

        play.launchPurchase(Activity(), lifetimePrice, obfuscatedAccountId = null)
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, m.state.value.displayPlan(now))
    }

    @Test fun the_fresh_answer_decides_a_purchase_the_server_no_longer_calls_ended_is_kept() = runTest {
        play.setPurchases(listOf(lifetime()))
        serverRows = serverLifetimeRows("tok-life", EntitlementStatus.EXPIRED)
        val m = signedIn()
        m.refresh()
        refundedAnswer = EntitlementStatus.ACTIVE                           // e.g. a server reading corrected since
        assertEquals(ReleaseOutcome.NOTHING, m.releaseEndedLifetime())
        assertEquals(emptyList<String>(), play.consumed)
        assertEquals(listOf("tok-life"), owned())
    }

    @Test fun without_a_fresh_answer_nothing_is_released_and_the_page_can_say_so() = runTest {
        play.setPurchases(listOf(lifetime()))
        serverRows = serverLifetimeRows("tok-life", EntitlementStatus.EXPIRED)
        val m = signedIn()
        m.refresh()
        refundedAnswer = null                                                // Lucento unreachable
        assertEquals(ReleaseOutcome(found = 1, released = 0), m.releaseEndedLifetime())
        assertEquals(emptyList<String>(), play.consumed)

        // A build without the purchase check (debug) never releases anything.
        assertEquals(ReleaseOutcome.NOTHING, signedIn(withChecker = false).releaseEndedLifetime())
        assertEquals(emptyList<String>(), play.consumed)
    }

    @Test fun a_lifetime_that_counts_is_never_released_not_even_after_its_included_year() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 400 * day)))
        serverRows = serverLifetimeRows("tok-life", EntitlementStatus.ACTIVE, boughtAt = now - 400 * day)
        refundedAnswer = EntitlementStatus.ACTIVE
        val m = signedIn()
        m.refresh()
        assertEquals(DisplayPlan.LIFETIME, m.state.value.displayPlan(now))   // the services year is over, the Lifetime isn't

        val asked = checks
        assertEquals(ReleaseOutcome.NOTHING, m.releaseEndedLifetime())
        assertEquals("a live purchase isn't even asked about", asked, checks)
        assertEquals(emptyList<String>(), play.consumed)
        assertEquals(listOf("tok-life"), owned())
    }

    @Test fun nothing_is_released_while_play_cant_be_read() = runTest {
        play.setPurchases(listOf(lifetime()))
        serverRows = serverLifetimeRows("tok-life", EntitlementStatus.EXPIRED)
        val m = signedIn()
        m.refresh()
        play.failure = "Play unreachable"
        assertEquals(ReleaseOutcome.NOTHING, m.releaseEndedLifetime())
        assertEquals(emptyList<String>(), play.consumed)
    }

    @Test fun when_play_wont_release_it_nothing_changes_and_the_page_can_say_so() = runTest {
        play.setPurchases(listOf(lifetime()))
        serverRows = serverLifetimeRows("tok-life", EntitlementStatus.EXPIRED)
        val m = signedIn()
        m.refresh()
        val before = m.state.value
        play.consumeSucceeds = false
        assertEquals(ReleaseOutcome(found = 1, released = 0), m.releaseEndedLifetime())
        assertEquals(listOf("tok-life"), owned())
        assertEquals(before, m.state.value)

        play.consumeSucceeds = true                                          // a later try works
        assertEquals(ReleaseOutcome(found = 1, released = 1), m.releaseEndedLifetime())
        assertTrue(owned().isEmpty())
    }
}
