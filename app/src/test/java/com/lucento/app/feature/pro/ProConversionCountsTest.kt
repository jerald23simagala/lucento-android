package com.lucento.app.feature.pro

import android.app.Activity
import com.lucento.app.data.billing.BillingAvailability
import com.lucento.app.data.billing.FakePurchaseSource
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.entitlement.EntitlementManager
import com.lucento.app.data.entitlement.HmacSealer
import com.lucento.app.data.entitlement.InMemorySnapshotStore
import com.lucento.app.data.entitlement.SealedEntitlementCache
import com.lucento.app.domain.UsageCounts
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.ProFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The plan page's opt-in conversion counts (docs/growth/CRO_PLAN.md, CRO-2): plan_page_viewed once per visit with its door,
 * purchase_started with the plan chosen, purchase_completed only when Play confirms that purchase on this page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProConversionCountsTest {
    private val now = 1_800_000_000_000L
    private val play = FakePurchaseSource()
    private val manager = EntitlementManager(
        SealedEntitlementCache(InMemorySnapshotStore(), HmacSealer(ByteArray(32) { 7 })), play, { null }, clock = { now },
    )
    private val counted = mutableListOf<Pair<String, Map<String, String>>>()

    private val annualWithTrial =
        ProductPrice(PlayProducts.PRO_SUBSCRIPTION, "annual", "trial-offer", "₱1,190.00", "P1Y", freeTrialPeriodIso = "P7D")
    private val monthly = ProductPrice(PlayProducts.PRO_SUBSCRIPTION, "monthly", "monthly-offer", "₱149.00", "P1M")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun page() = ProViewModel(manager, play, simulator = null, clock = { now }, recordUsage = { name, props -> counted += name to props })

    @Test fun opening_the_page_counts_once_with_its_door() {
        val vm = page()
        vm.noteOpened(feature = null, via = "settings")
        vm.noteOpened(feature = null, via = "settings")  // Back from a deeper page, or the phone turned
        assertEquals(listOf(UsageCounts.PLAN_PAGE_VIEWED to mapOf("from" to "settings")), counted)
    }

    @Test fun a_lock_note_or_the_dialog_counts_as_the_wall_with_its_feature() {
        page().noteOpened(feature = ProFeature.MONEY_WALLETS, via = "")
        assertEquals(
            listOf(UsageCounts.PLAN_PAGE_VIEWED to mapOf("from" to "wall", "feature" to "money_wallets")),
            counted,
        )
    }

    @Test fun a_purchase_counts_its_start_and_its_completion_with_the_plan() = runTest {
        val vm = page()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.buy(Activity(), annualWithTrial)

        assertEquals(
            listOf(
                UsageCounts.PURCHASE_STARTED to mapOf("plan" to "annual_trial"),
                UsageCounts.PURCHASE_COMPLETED to mapOf("plan" to "annual_trial"),
            ),
            counted,
        )
    }

    @Test fun a_purchase_play_cannot_take_counts_only_its_start() = runTest {
        play.setAvailability(BillingAvailability.UNAVAILABLE)
        val vm = page()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.buy(Activity(), monthly)

        assertEquals(listOf(UsageCounts.PURCHASE_STARTED to mapOf("plan" to "monthly")), counted)
    }

    @Test fun the_dialog_memory_reports_each_features_first_dialog_once() {
        val shown = mutableListOf<ProFeature>()
        val memory = ProWallMemory(onFirstShown = { shown += it })
        memory.markMet(ProFeature.MONEY_WALLETS)
        memory.markMet(ProFeature.MONEY_WALLETS)
        memory.markMet(ProFeature.EXTRA_THEMES)
        assertEquals(listOf(ProFeature.MONEY_WALLETS, ProFeature.EXTRA_THEMES), shown)
    }
}
