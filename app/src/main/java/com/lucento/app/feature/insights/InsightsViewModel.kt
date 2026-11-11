package com.lucento.app.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Budget
import com.lucento.app.domain.Category
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class InsightsData(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val primaryCurrency: String = "USD",
    val features: FeatureSet = FeatureSet.ALL,
    val transactions: List<Transaction> = emptyList(),
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    /** Lucento Pro (docs/MONETIZATION.md §3.4): 6- and 12-month views are Pro. */
    val longPeriodsLocked: Boolean = false,
) {
    val categoriesById: Map<Long, Category> = categories.associateBy { it.id }

    /** Main currency when used, otherwise the first wallet's — never a conversion. */
    val focusCurrency: String
        get() = when {
            wallets.any { it.currencyCode == primaryCurrency } || transactions.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            else -> wallets.firstOrNull()?.currencyCode ?: primaryCurrency
        }
}

class InsightsViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    preferences: UserPreferencesRepository,
    /** Lucento Pro: the plan this phone holds; null in tests (everything allowed). */
    entitlements: StateFlow<com.lucento.app.domain.entitlement.EntitlementState>? = null,
) : ViewModel() {
    init {
        // Opening Insights earns the Financial Explorer milestone (a local flag; nothing is sent).
        viewModelScope.launch { runCatching { preferences.setInsightsVisited() } }
    }

    val data: StateFlow<InsightsData> = combine(
        combine(
            transactions.observeTransactions(),
            wallets.observeWallets(),
            categories.observeCategories(),
            budgets.observeBudgets(),
            preferences.preferences,
        ) { t, w, c, b, p ->
            // Multicurrency: Insights reads everything in the main currency, FX gains as income and FX losses as expenses,
            // each under its own category (docs/CALCULATIONS.md "Exchange rates").
            val ledger = com.lucento.app.domain.fx.FxTranslation.translate(p.primaryCurrency, w, t)
            InsightsData(false, LocalDate.now(), p.primaryCurrency, p.features, ledger.withFxResults, ledger.wallets, ledger.withFxCategories(c), b)
        },
        entitlements ?: kotlinx.coroutines.flow.flowOf(null),
    ) { data, plan ->
        if (plan == null) data
        else {
            val now = System.currentTimeMillis()
            data.copy(
                longPeriodsLocked = !com.lucento.app.domain.entitlement.ProGates.insightsPeriodAllowed(12, plan, now),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsData())

    companion object {
        val Factory = containerFactory { InsightsViewModel(it.transactions, it.wallets, it.categories, it.budgets, it.preferences, it.entitlements.state) }
    }
}
