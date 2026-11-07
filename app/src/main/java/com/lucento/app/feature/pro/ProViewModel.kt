package com.lucento.app.feature.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.billing.DebugPlanSimulator
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.billing.PurchaseSource
import com.lucento.app.data.billing.SimulatedPlan
import com.lucento.app.data.entitlement.EntitlementManager
import com.lucento.app.data.entitlement.RefreshOutcome
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.PlayProducts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProUiState(
    val entitlements: EntitlementState = EntitlementState.FREE,
    /** Google Play's prices for this person; empty until Lucento Pro is on sale (no price is ever typed into the app). */
    val prices: List<ProductPrice> = emptyList(),
    val restoring: Boolean = false,
    /** What Restore purchases found, in words (customer copy D-8). */
    val message: String? = null,
    /** Test builds: the simulated plan, or null for the account's own. */
    val simulated: SimulatedPlan? = null,
    val simulatorAvailable: Boolean = false,
)

/** The Lucento Pro page (docs/MONETIZATION.md §5.5 / Phase 3): the plan, what each includes, prices from Play, restore. */
class ProViewModel(
    private val entitlements: EntitlementManager,
    private val purchases: PurchaseSource,
    /** Test builds only; null in release. */
    private val simulator: DebugPlanSimulator? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val prices = MutableStateFlow<List<ProductPrice>>(emptyList())
    private val restoring = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<ProUiState> = combine(
        entitlements.state,
        prices,
        restoring,
        message,
        simulator?.current ?: flowOf(null),
    ) { plan, p, busy, said, simulated ->
        ProUiState(plan, p, busy, said, simulated, simulatorAvailable = simulator != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProUiState(simulatorAvailable = simulator != null))

    init {
        viewModelScope.launch { prices.value = runCatching { purchases.queryPrices(PlayProducts.ALL) }.getOrDefault(emptyList()) }
    }

    /** Re-reads Google Play and the server. One at a time; the answer says what it found. */
    fun restore() {
        if (restoring.value) return
        restoring.value = true
        message.value = null
        viewModelScope.launch {
            try {
                val before = entitlements.state.value.displayPlan(clock())
                val outcome = runCatching { entitlements.refresh() }.getOrElse { RefreshOutcome(playReached = false, serverReached = false) }
                val after = entitlements.state.value.displayPlan(clock())
                message.value = ProCopy.restoreMessage(before, after, outcome)
            } finally {
                restoring.value = false
            }
        }
    }

    /** Test builds: switch to [plan] (null: the account's own) and re-read at once. */
    fun simulate(plan: SimulatedPlan?) {
        val sim = simulator ?: return
        message.value = null
        sim.select(plan)
        viewModelScope.launch { runCatching { entitlements.refresh() } }
    }

    companion object {
        val Factory = containerFactory { ProViewModel(it.entitlements, it.purchaseSource, it.planSimulator) }
    }
}
