package com.lucento.app.data.billing

import android.app.Activity
import com.lucento.app.domain.entitlement.PlayProducts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test builds: the fake purchase flow behind the simulator's "Test store" switch (docs/MONETIZATION.md §5.7). */
@OptIn(ExperimentalCoroutinesApi::class)
class TestStoreTest {
    private val fake = FakePurchaseSource()
    private val store = InMemorySimulatorStore()
    private val simulator = DebugPlanSimulator(store, fake, clock = { 1_800_000_000_000L })

    @Test fun the_test_store_is_off_by_default_and_lists_marked_test_prices_when_on() = runTest {
        assertFalse(simulator.testStore.value)
        assertEquals(emptyList<ProductPrice>(), fake.queryPrices(PlayProducts.ALL))
        simulator.setTestStore(true)
        assertTrue(store.testStore)
        val prices = fake.queryPrices(PlayProducts.ALL)
        assertEquals(PlayProducts.ALL, prices.map { it.productId }.toSet())
        assertTrue("every test price says so", prices.all { it.formattedPrice.endsWith("(test)") })
        simulator.setTestStore(false)
        assertEquals(emptyList<ProductPrice>(), fake.queryPrices(PlayProducts.ALL))
    }

    @Test fun a_test_purchase_joins_what_the_account_owns_unacknowledged_and_reports_completed() = runTest {
        val seen = mutableListOf<PurchaseResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fake.results.collect { seen += it } }
        simulator.setTestStore(true)
        val lifetime = fake.queryPrices(PlayProducts.ALL).first { it.productId == PlayProducts.PRO_LIFETIME }
        assertNull(fake.launchPurchase(Activity(), lifetime, obfuscatedAccountId = null))
        assertEquals(listOf<PurchaseResult>(PurchaseResult.Completed), seen)
        val owned = (fake.queryPurchases() as PurchaseQuery.Success).purchases
        assertEquals(listOf(PlayProducts.PRO_LIFETIME), owned.map { it.productId })
        assertFalse("acknowledged later, like a real purchase", owned.single().acknowledged)

        assertNull(fake.launchPurchase(Activity(), lifetime, obfuscatedAccountId = null))
        assertEquals(PurchaseResult.Failed(BillingProblem.ALREADY_OWNED), seen.last())
    }

    @Test fun without_billing_nothing_launches() = runTest {
        fake.setAvailability(BillingAvailability.UNAVAILABLE)
        val price = TEST_PRICES.first()
        assertEquals(BillingProblem.UNAVAILABLE, fake.launchPurchase(Activity(), price, obfuscatedAccountId = null))
        assertEquals(BillingProblem.UNAVAILABLE, UnavailablePurchaseSource.launchPurchase(Activity(), price, null))
        assertTrue("a build that can't bill never answers 'owns nothing'", UnavailablePurchaseSource.queryPurchases() is PurchaseQuery.Failed)
    }
}
