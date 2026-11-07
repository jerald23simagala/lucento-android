package com.lucento.app.feature.pro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.billing.BillingProblem
import com.lucento.app.data.billing.DebugPlanSimulator
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.billing.PurchaseResult
import com.lucento.app.data.billing.PurchaseSource
import com.lucento.app.data.billing.SimulatedPlan
import com.lucento.app.data.billing.VerifyOutcome
import com.lucento.app.data.entitlement.EntitlementManager
import com.lucento.app.data.entitlement.RefreshOutcome
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProUiState(
    val entitlements: EntitlementState = EntitlementState.FREE,
    /** Google Play's prices for this person; empty until Lucento Pro is on sale (no price is ever typed into the app). */
    val prices: List<ProductPrice> = emptyList(),
    val restoring: Boolean = false,
    /** Google Play's purchase sheet is open (or opening): every buy button waits. */
    val purchasing: Boolean = false,
    /** What Restore purchases found, in words (customer copy D-8); shown under its button. */
    val message: String? = null,
    /** How the last purchase went (D-9), or Play's problem with it; shown under the plans, where the button was. */
    val purchaseMessage: String? = null,
    /** The server wouldn't record a purchase on this account (§5.4); explained under "Your purchases". */
    val verificationIssue: VerifyOutcome? = null,
    /** Test builds: the simulated plan, or null for the account's own. */
    val simulated: SimulatedPlan? = null,
    val simulatorAvailable: Boolean = false,
    /** Test builds: the fake purchase flow with test prices is on. */
    val testStore: Boolean = false,
)

/**
 * The Lucento Pro page (docs/MONETIZATION.md §5.5, §9): the plan, what each includes, Google Play's prices, buying through
 * Play, restore. A purchase's plan arrives through the entitlement manager like any other Play reading; this only words
 * how it went.
 */
class ProViewModel(
    private val entitlements: EntitlementManager,
    private val purchases: PurchaseSource,
    /** Test builds only; null in release. */
    private val simulator: DebugPlanSimulator? = null,
    /** The signed-in Lucento account, if any: its hash goes to Play with the order (§5.4 linking rule). */
    private val currentUserId: suspend () -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private data class Local(
        val prices: List<ProductPrice> = emptyList(),
        val restoring: Boolean = false,
        val purchasing: Boolean = false,
        val message: String? = null,
        val purchaseMessage: String? = null,
        /** The product of the purchase in flight, so its result can be worded. */
        val buying: String? = null,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ProUiState> = combine(
        entitlements.state,
        local,
        entitlements.verificationIssue,
        simulator?.current ?: flowOf(null),
        simulator?.testStore ?: flowOf(false),
    ) { plan, l, issue, simulated, testStore ->
        ProUiState(
            entitlements = plan,
            prices = l.prices,
            restoring = l.restoring,
            purchasing = l.purchasing,
            message = l.message,
            purchaseMessage = l.purchaseMessage,
            verificationIssue = issue,
            simulated = simulated,
            simulatorAvailable = simulator != null,
            testStore = testStore,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProUiState(simulatorAvailable = simulator != null))

    init {
        loadPrices()
        viewModelScope.launch {
            purchases.results.collect { result ->
                local.update { it.copy(purchasing = false, buying = null, purchaseMessage = ProCopy.purchaseResult(result, it.buying)) }
                // A completed purchase is already on its way through Play's update; a refresh also asks the server.
                if (result == PurchaseResult.Completed) runCatching { entitlements.refresh() }
            }
        }
    }

    private fun loadPrices() {
        viewModelScope.launch {
            val prices = runCatching { purchases.queryPrices(PlayProducts.ALL) }.getOrDefault(emptyList())
            local.update { it.copy(prices = prices) }
        }
    }

    /** Opens Google Play's purchase sheet for [price]. One at a time; how it went arrives from Play. */
    fun buy(activity: Activity, price: ProductPrice) {
        if (local.value.purchasing) return
        local.update { it.copy(purchasing = true, buying = price.productId, purchaseMessage = null, message = null) }
        viewModelScope.launch {
            val account = runCatching { currentUserId() }.getOrNull()?.let(PurchaseInterpreter::obfuscatedAccountId)
            val problem = runCatching { purchases.launchPurchase(activity, price, account) }.getOrDefault(BillingProblem.OTHER)
            if (problem != null) {
                local.update { it.copy(purchasing = false, buying = null, purchaseMessage = ProCopy.billingProblem(problem)) }
            }
        }
    }

    /** Re-reads Google Play and the server. One at a time; the answer says what it found. */
    fun restore() {
        if (local.value.restoring) return
        local.update { it.copy(restoring = true, message = null, purchaseMessage = null) }
        viewModelScope.launch {
            try {
                val before = entitlements.state.value.displayPlan(clock())
                val outcome = runCatching { entitlements.refresh() }.getOrElse { RefreshOutcome(playReached = false, serverReached = false) }
                val after = entitlements.state.value.displayPlan(clock())
                local.update { it.copy(message = ProCopy.restoreMessage(before, after, outcome)) }
            } finally {
                local.update { it.copy(restoring = false) }
            }
        }
    }

    /** Test builds: switch to [plan] (null: the account's own) and re-read at once. */
    fun simulate(plan: SimulatedPlan?) {
        val sim = simulator ?: return
        local.update { it.copy(message = null, purchaseMessage = null) }
        sim.select(plan)
        viewModelScope.launch { runCatching { entitlements.refresh() } }
    }

    /** Test builds: turn the fake purchase flow and its test prices on or off. */
    fun setTestStore(on: Boolean) {
        val sim = simulator ?: return
        sim.setTestStore(on)
        loadPrices()
    }

    companion object {
        val Factory = containerFactory {
            ProViewModel(it.entitlements, it.purchaseSource, it.planSimulator, currentUserId = it.auth::currentUserId)
        }
    }
}
