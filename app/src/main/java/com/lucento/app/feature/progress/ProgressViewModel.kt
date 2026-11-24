package com.lucento.app.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Achievement
import com.lucento.app.domain.Achievements
import com.lucento.app.domain.Category
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.ProgressFacts
import com.lucento.app.domain.RecapInputs
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.fx.TranslatedLedger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** What the Progress page is built from; the recaps themselves are worked out by [com.lucento.app.domain.MonthRecaps]. */
data class ProgressUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val primaryCurrency: String = "USD",
    /** The main currency, or the currency the records are in when none is (never a conversion). */
    val currency: String = "USD",
    val features: FeatureSet = FeatureSet.ALL,
    val inputs: RecapInputs? = null,
    /** The translated ledger, for the budget moment ([com.lucento.app.domain.BudgetMilestones]). */
    val ledger: TranslatedLedger? = null,
    val categoriesById: Map<Long, Category> = emptyMap(),
    /** The first month with anything recorded: the month stepper goes no further back. */
    val earliestMonth: YearMonth = YearMonth.now(),
    /** What the award cabinet shows as earned ([Achievements.shownEarned]). */
    val earned: Set<Achievement> = emptySet(),
    val facts: ProgressFacts? = null,
    val proMemberSince: Long? = null,
    /** Lucento Pro (docs/MONETIZATION.md §3.4): the budget year, like Insights' 12 months. */
    val yearLocked: Boolean = false,
    val acknowledgedBudgetMonth: YearMonth? = null,
)

class ProgressViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    budgets: BudgetRepository,
    goals: GoalRepository,
    private val preferences: UserPreferencesRepository,
    /** Lucento Pro: the plan this phone holds; null in tests (everything allowed). */
    entitlements: StateFlow<EntitlementState>? = null,
) : ViewModel() {

    private data class Ledger(
        val transactions: List<com.lucento.app.domain.Transaction>,
        val wallets: List<com.lucento.app.domain.Wallet>,
        val categories: List<Category>,
        val budgets: List<com.lucento.app.domain.Budget>,
    )

    private data class Savings(
        val goals: List<com.lucento.app.domain.Goal>,
        val archived: List<com.lucento.app.domain.Goal>,
        val contributions: List<com.lucento.app.domain.GoalContribution>,
    )

    val uiState: StateFlow<ProgressUiState> = combine(
        combine(transactions.observeTransactions(), wallets.observeWallets(), categories.observeCategories(), budgets.observeBudgets(), ::Ledger),
        combine(goals.observeGoals(), goals.observeArchivedGoals(), goals.observeContributions(), ::Savings),
        preferences.preferences,
        entitlements ?: flowOf(null),
    ) { data, savings, prefs, plan ->
        val today = LocalDate.now()
        // Multicurrency (docs/CALCULATIONS.md "Exchange rates"): the same translated ledger as Home and Budget.
        val ledger = com.lucento.app.domain.fx.FxTranslation.translate(prefs.primaryCurrency, data.wallets, data.transactions)
        val currency = com.lucento.app.domain.FocusCurrency.of(prefs.primaryCurrency, ledger.wallets, ledger.transactions)
        val facts = Achievements.facts(
            data.transactions, data.wallets, data.categories, data.budgets, savings.goals + savings.archived, today,
            prefs.insightsVisited, prefs.appLockUsed,
            contributions = savings.contributions,
            ledger = ledger,
            primaryCurrency = prefs.primaryCurrency,
        )
        val current = YearMonth.from(today)
        ProgressUiState(
            loading = false,
            today = today,
            primaryCurrency = prefs.primaryCurrency,
            currency = currency,
            features = prefs.features,
            inputs = RecapInputs(
                wallets = ledger.wallets,
                transactions = ledger.transactions,
                withFxResults = ledger.withFxResults,
                categories = data.categories,
                budgets = data.budgets,
                // Archived goals count: money set aside for them, and a target reached, happened all the same.
                goals = savings.goals + savings.archived,
                contributions = savings.contributions,
                features = prefs.features,
                today = today,
            ),
            ledger = ledger,
            categoriesById = data.categories.associateBy { it.id },
            earliestMonth = data.transactions.minOfOrNull { it.date }?.let(YearMonth::from)?.coerceAtMost(current) ?: current,
            earned = Achievements.shownEarned(Achievements.earned(facts), prefs.achievementsSeen),
            facts = facts,
            proMemberSince = prefs.proMemberSince,
            yearLocked = plan != null &&
                !com.lucento.app.domain.entitlement.ProGates.insightsPeriodAllowed(12, plan, System.currentTimeMillis()),
            acknowledgedBudgetMonth = prefs.acknowledgedBudgetMonth,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    /** The check-in's recap of [month] was looked at here: Home stops offering it. */
    fun markRecapSeen(month: YearMonth) {
        viewModelScope.launch { runCatching { preferences.setRecapMonthSeen(month) } }
    }

    /** The month kept within budget was celebrated here, so the Budget tab doesn't celebrate it again (and the reverse). */
    fun acknowledgeBudgetMonth(month: YearMonth) {
        viewModelScope.launch { runCatching { preferences.setAcknowledgedBudgetMonth(month) } }
    }

    companion object {
        val Factory = containerFactory {
            ProgressViewModel(it.transactions, it.wallets, it.categories, it.budgets, it.goals, it.preferences, it.entitlements.state)
        }
    }
}
