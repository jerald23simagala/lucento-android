package com.lucento.app.feature.pro

import android.app.Activity
import com.lucento.app.data.billing.BillingProblem
import com.lucento.app.data.billing.FakePurchaseSource
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.billing.PurchaseChecker
import com.lucento.app.data.billing.PurchaseQuery
import com.lucento.app.data.billing.PurchaseSource
import com.lucento.app.data.billing.PurchaseVerdict
import com.lucento.app.data.entitlement.EntitlementManager
import com.lucento.app.data.entitlement.HmacSealer
import com.lucento.app.data.entitlement.InMemorySnapshotStore
import com.lucento.app.data.entitlement.SealedEntitlementCache
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Pro page's side of buying Lifetime again after a refund (owner's test 2026-11-13, MONETIZATION §5.3 L-7). Buy
 * Lifetime releases a refunded Lifetime Google Play still holds before Play's sheet opens. When Play still says "You
 * already own this item", the page reads Play and the server again and says what it found.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProRebuyFlowTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L
    private val play = FakePurchaseSource()
    private val lifetimePrice = ProductPrice(PlayProducts.PRO_LIFETIME, null, "test-lifetime", "₱3,490.00 (test)", null)

    /** What the purchase check answers for the first Lifetime (null: Lucento unreachable); a new purchase is live. */
    private var answer: EntitlementStatus? = null
    private val checker = PurchaseChecker { p ->
        if (p.purchaseToken != "tok-life") return@PurchaseChecker PurchaseVerdict.Known(EntitlementStatus.ACTIVE)
        answer?.let { PurchaseVerdict.Known(it) } ?: PurchaseVerdict.Unavailable
    }
    private val manager = EntitlementManager(
        SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(ByteArray(32) { 5 })), play, { null }, checker = checker, clock = { now },
    )

    private fun lifetime(boughtAt: Long) =
        PurchaseRecord(PlayProducts.PRO_LIFETIME, "tok-life", "GPA.life", boughtAt, pending = false, acknowledged = true, autoRenewing = false)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun page() = ProViewModel(manager, play, simulator = null, clock = { now })

    @Test fun buying_lifetime_after_a_refund_releases_the_refunded_one_first_and_the_purchase_goes_through() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 2 * day)))  // refunded, still held by Play
        answer = EntitlementStatus.EXPIRED
        manager.refresh()
        assertEquals(DisplayPlan.FREE, manager.state.value.displayPlan(now))
        val vm = page()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.buy(Activity(), lifetimePrice)

        assertEquals(listOf("tok-life"), play.consumed)
        assertEquals("Pro Lifetime is yours. Everything that runs on this phone is unlocked, for good.", vm.state.value.purchaseMessage)
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, manager.state.value.displayPlan(now))
        assertFalse(vm.state.value.purchasing)
    }

    @Test fun when_play_says_already_owned_and_lucento_learns_it_was_refunded_it_is_released_and_the_page_says_so() = runTest {
        // Bought 10 days ago, and Lucento couldn't be reached: paused, not yet known to be refunded.
        play.setPurchases(listOf(lifetime(boughtAt = now - 10 * day)))
        manager.refresh()
        assertTrue(manager.state.value.awaitingCheck(now))
        val vm = page()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        answer = EntitlementStatus.EXPIRED                               // reachable again, and Google says cancelled
        vm.buy(Activity(), lifetimePrice)                                // Play refuses: it still holds the purchase

        assertEquals(listOf("tok-life"), play.consumed)
        assertTrue(vm.state.value.purchaseMessage.orEmpty().startsWith("Google Play was still holding your earlier Pro Lifetime"))
        assertFalse(vm.state.value.purchasing)
        assertEquals(DisplayPlan.FREE, manager.state.value.displayPlan(now))

        vm.buy(Activity(), lifetimePrice)                                // and now Play sells it
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, manager.state.value.displayPlan(now))
    }

    /** Like the Billing Library: a refused launch is returned *and* posted to the purchases listener. */
    private class ReportsTwice(private val fake: FakePurchaseSource) : PurchaseSource by fake {
        override suspend fun launchPurchase(activity: Activity, price: ProductPrice, obfuscatedAccountId: String?): BillingProblem? {
            val owned = (fake.queryPurchases() as? PurchaseQuery.Success)?.purchases.orEmpty()
                .any { it.productId == price.productId && !it.pending }
            val answer = fake.launchPurchase(activity, price, obfuscatedAccountId) // posts Failed(ALREADY_OWNED) when owned
            return if (owned) BillingProblem.ALREADY_OWNED else answer
        }
    }

    @Test fun already_owned_reported_twice_is_settled_once() = runTest {
        // QA review 2026-11-13: the second report found nothing left to release and overwrote the first one's answer.
        play.setPurchases(listOf(lifetime(boughtAt = now - 10 * day)))
        manager.refresh()                                                // paused: Lucento unreachable
        val vm = ProViewModel(manager, ReportsTwice(play), simulator = null, clock = { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        answer = EntitlementStatus.EXPIRED
        vm.buy(Activity(), lifetimePrice)

        assertEquals("released exactly once", listOf("tok-life"), play.consumed)
        assertTrue(vm.state.value.purchaseMessage.orEmpty().startsWith("Google Play was still holding your earlier Pro Lifetime"))
        assertFalse(vm.state.value.purchasing)

        vm.buy(Activity(), lifetimePrice)
        assertEquals("Pro Lifetime is yours. Everything that runs on this phone is unlocked, for good.", vm.state.value.purchaseMessage)
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, manager.state.value.displayPlan(now))
    }

    @Test fun when_play_says_already_owned_and_the_purchase_is_live_the_page_says_it_works_and_releases_nothing() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 10 * day)))
        manager.refresh()                                                // paused: Lucento unreachable
        val vm = page()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        answer = EntitlementStatus.ACTIVE
        vm.buy(Activity(), lifetimePrice)

        assertEquals(emptyList<String>(), play.consumed)
        assertEquals(
            "Your Google account already owns this, and your Pro Lifetime is working on this phone now.",
            vm.state.value.purchaseMessage,
        )
        assertEquals(DisplayPlan.LIFETIME_WITH_SERVICES, manager.state.value.displayPlan(now))
    }

    @Test fun restore_says_a_refunded_lifetime_was_refunded_instead_of_no_purchase_found() = runTest {
        play.setPurchases(listOf(lifetime(boughtAt = now - 2 * day)))
        answer = EntitlementStatus.EXPIRED
        val vm = page()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.restore()

        assertTrue(vm.state.value.message.orEmpty().startsWith("Google Play reports your Pro Lifetime as refunded or cancelled"))
        assertEquals("Restore never releases anything", emptyList<String>(), play.consumed)
    }
}
