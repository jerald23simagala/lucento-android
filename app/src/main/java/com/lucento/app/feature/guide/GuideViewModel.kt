package com.lucento.app.feature.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Budget
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GuideStep
import com.lucento.app.domain.GuidedSetup
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the guided setup shows: where the story stands and what the user has set up so far. */
data class GuideUiState(
    val loading: Boolean = true,
    val step: GuideStep = GuideStep.WALLETS,
    val features: FeatureSet = FeatureSet.ALL,
    val currency: String = "USD",
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val transactionCount: Int = 0,
) {
    val steps: List<GuideStep> get() = GuidedSetup.steps(features)
    val assets: List<Wallet> get() = wallets.filter { !it.type.isLiability }
    val debts: List<Wallet> get() = wallets.filter { it.type.isLiability }

    /** Active spending categories, the rows of the budget step. */
    val expenseCategories: List<Category> get() = categories.filter { it.kind == CategoryKind.EXPENSE && !it.archived }

    /** The amount already set for [categoryId] in the main currency, if any. */
    fun allocation(categoryId: Long): Money? =
        budgets.firstOrNull { it.categoryId == categoryId && it.limit.currencyCode == currency && it.limit.amountMinor > 0 }?.limit

    val budgetedCount: Int get() = expenseCategories.count { allocation(it.id) != null }

    val canContinue: Boolean
        get() = GuidedSetup.canContinue(step, assets.size, budgetedCount, expenseCategories.size)
}

class GuideViewModel(
    private val preferences: UserPreferencesRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    private val budgets: BudgetRepository,
    goals: GoalRepository,
    transactions: TransactionRepository,
    private val usage: com.lucento.app.domain.UsageCountsService? = null,
) : ViewModel() {

    /** Anonymous usage counts: the guided setup continues onboarding's numbering, pages 9 to 14. */
    fun onStepShown(step: GuideStep) {
        val counts = usage ?: return
        viewModelScope.launch { counts.stepViewed(guideStepNumber(step)) }
    }

    private val data = combine(
        wallets.observeWallets(),
        categories.observeCategories(),
        budgets.observeBudgets(),
        goals.observeGoals(),
        transactions.observeTransactions(),
    ) { w, c, b, g, t -> Snapshot(w, c, b, g, t.count { !it.isTransfer || it.signedAmount.isNegative }) }

    private class Snapshot(val wallets: List<Wallet>, val categories: List<Category>, val budgets: List<Budget>, val goals: List<Goal>, val transactions: Int)

    val uiState: StateFlow<GuideUiState> = combine(preferences.preferences, data) { p, d ->
        GuideUiState(
            loading = false,
            step = GuidedSetup.resume(p.guideStep ?: GuideStep.WELCOME, p.features),
            features = p.features,
            currency = p.primaryCurrency,
            wallets = d.wallets,
            categories = d.categories,
            budgets = d.budgets,
            goals = d.goals,
            transactionCount = d.transactions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideUiState())

    private var saving = false

    /** Moves to the next step (saved, so the story resumes there); after the last one the guide is finished. */
    fun next() {
        val state = uiState.value
        if (!state.canContinue && !GuidedSetup.isSkippable(state.step)) return
        viewModelScope.launch { preferences.setGuideStep(GuidedSetup.next(state.step, state.features) ?: GuideStep.WELCOME) }
    }

    fun back() {
        val state = uiState.value
        val previous = state.steps.lastOrNull { it.ordinal < state.step.ordinal } ?: return
        viewModelScope.launch { preferences.setGuideStep(previous) }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setGuideStep(null)
            onDone()
        }
    }

    /**
     * Saves the budget step's amounts (category id → text as typed) in the main currency, then moves on. Empty fields
     * are left unset. Returns, through [onResult], the ids whose text isn't a valid amount (nothing moves on then).
     */
    fun saveBudgets(amounts: Map<Long, String>, decimalSeparator: Char, onResult: (Set<Long>) -> Unit) {
        if (saving) return
        val currency = uiState.value.currency
        val parsed = amounts.filterValues { it.isNotBlank() }.mapValues { (_, text) -> MoneyParser.parseOrNull(text, currency, decimalSeparator) }
        val invalid = parsed.filterValues { it == null || it.amountMinor <= 0 }.keys
        if (invalid.isNotEmpty()) return onResult(invalid)
        saving = true
        viewModelScope.launch {
            try {
                parsed.forEach { (categoryId, money) ->
                    if (budgets.setAllocation(categoryId, currency, money!!) !is BudgetRepository.SaveResult.Saved) {
                        onResult(setOf(categoryId))
                        return@launch
                    }
                }
                onResult(emptySet())
                val state = uiState.value
                val budgeted = state.expenseCategories.count { it.id in parsed.keys || state.allocation(it.id) != null }
                if (GuidedSetup.canContinue(GuideStep.BUDGETS, state.assets.size, budgeted, state.expenseCategories.size)) {
                    preferences.setGuideStep(GuidedSetup.next(GuideStep.BUDGETS, state.features) ?: GuideStep.WELCOME)
                }
            } finally {
                saving = false
            }
        }
    }

    companion object {
        val Factory = containerFactory {
            GuideViewModel(it.preferences, it.wallets, it.categories, it.budgets, it.goals, it.transactions, it.usageCounts)
        }
    }
}

/**
 * The `onboarding_step_viewed` number for a guided-setup page: accounts 9, cards and loans 10, budget 11, goal 12,
 * first transactions 13, welcome 14. Fixed per page, so a skipped page leaves a gap instead of shifting the rest.
 */
internal fun guideStepNumber(step: GuideStep): Int = GUIDE_FIRST_STEP_NUMBER + step.ordinal

/** Onboarding sends 1 to 8; the guided setup follows on from there. */
internal const val GUIDE_FIRST_STEP_NUMBER = 9
