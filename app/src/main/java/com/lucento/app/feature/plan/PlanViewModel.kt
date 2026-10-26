package com.lucento.app.feature.plan

import com.lucento.app.domain.transactable
import com.lucento.app.domain.GoalCalculator
import com.lucento.app.domain.GoalMilestone
import com.lucento.app.domain.GoalMilestones
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Budget
import com.lucento.app.domain.Category
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** Everything the goals, debts and recurring screens (and the More menu) read. Calculations happen in the domain layer. */
data class PlanUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val primaryCurrency: String = "USD",
    val features: FeatureSet = FeatureSet.ALL,
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val contributions: List<GoalContribution> = emptyList(),
    val schedules: List<RecurringSchedule> = emptyList(),
    val acknowledgedBudgetMonth: java.time.YearMonth? = null,
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
    val categoriesById: Map<Long, Category> = categories.associateBy { it.id }

    /** Currencies the user actually uses, main currency first — the only sensible budget/goal currencies. */
    val currencies: List<String>
        get() = (listOf(primaryCurrency) + wallets.map { it.currencyCode }).distinct()
}

/** Parsed-amount errors shared by the plan editors. */
sealed interface AmountError {
    data object Invalid : AmountError
    data object Zero : AmountError
    data class TooPrecise(val digits: Int) : AmountError
    data object TooLarge : AmountError
}

/** Parses a positive amount through [MoneyParser] (the only way user input becomes money). */
fun parsePositive(text: String, currencyCode: String, separator: Char): Pair<Money?, AmountError?> =
    when (val parsed = MoneyParser.parse(text, currencyCode, separator)) {
        is MoneyParser.Result.Success -> if (parsed.money.amountMinor > 0L) parsed.money to null else null to AmountError.Zero
        MoneyParser.Result.Empty, MoneyParser.Result.Invalid -> null to AmountError.Invalid
        is MoneyParser.Result.TooManyDecimals -> null to AmountError.TooPrecise(parsed.allowed)
        MoneyParser.Result.TooLarge -> null to AmountError.TooLarge
    }

fun AmountError.message(currencyCode: String, separator: Char): String = when (this) {
    AmountError.Invalid -> "Enter an amount, like 1500${separator}00."
    AmountError.Zero -> "The amount must be greater than zero."
    is AmountError.TooPrecise -> if (digits == 0) "$currencyCode has no decimal places." else "$currencyCode allows up to $digits decimal places."
    AmountError.TooLarge -> "That amount is larger than Lucento supports."
}

class PlanViewModel(
    transactions: TransactionRepository,
    wallets: WalletRepository,
    categories: CategoryRepository,
    private val budgets: BudgetRepository,
    private val goals: GoalRepository,
    private val recurring: RecurringRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private data class Base(
        val transactions: List<Transaction>,
        val wallets: List<Wallet>,
        val categories: List<Category>,
        val budgets: List<Budget>,
    )

    private data class Plans(
        val goals: List<Goal>,
        val contributions: List<GoalContribution>,
        val schedules: List<RecurringSchedule>,
    )

    val uiState: StateFlow<PlanUiState> = combine(
        combine(
            transactions.observeTransactions(),
            wallets.observeWallets(),
            categories.observeCategories(),
            budgets.observeBudgets(),
        ) { t, w, c, b -> Base(t, w, c, b) },
        combine(goals.observeGoals(), goals.observeContributions(), recurring.observeSchedules()) { g, c, s -> Plans(g, c, s) },
        preferences.preferences,
    ) { base, plans, prefs ->
        PlanUiState(
            loading = false,
            today = LocalDate.now(),
            primaryCurrency = prefs.primaryCurrency,
            features = prefs.features,
            wallets = base.wallets.transactable(),
            categories = base.categories,
            transactions = base.transactions,
            budgets = base.budgets,
            goals = plans.goals,
            contributions = plans.contributions,
            schedules = plans.schedules,
            acknowledgedBudgetMonth = prefs.acknowledgedBudgetMonth,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    private var busy = false

    /**
     * Runs one write at a time so double taps cannot create duplicates. If the database refuses the write
     * (it was rolled back), [onResult] is not called: the form stays open with the user's input, nothing is
     * reported as saved, and the app does not crash.
     */
    private fun <T> write(block: suspend () -> T, onResult: (T) -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Lucento", "Plan write failed and was rolled back", e)
                null
            } finally {
                busy = false
            }
            if (result != null) onResult(result)
        }
    }

    // Budgets moved to the Budget tab (feature/budget/BudgetViewModel) in 0.5.

    // ---------------------------------------------------------------- goals

    fun saveGoal(
        id: Long?,
        name: String,
        target: Money,
        targetDate: LocalDate?,
        walletId: Long?,
        sceneKey: String?,
        photoPath: String?,
        onResult: (GoalRepository.SaveResult) -> Unit,
    ) = write({ goals.save(id, name, target, targetDate, walletId, sceneKey, photoPath) }, onResult)

    /**
     * Saves a contribution. [onMilestone] is called once, after the write succeeded, if this contribution moved
     * the goal past halfway or to its target — never on recomposition and never for a withdrawal.
     */
    fun contribute(
        goalId: Long,
        amount: Money,
        date: LocalDate,
        note: String,
        key: String,
        onResult: (GoalRepository.ContributionResult) -> Unit,
        onMilestone: (GoalMilestone) -> Unit = {},
    ) {
        val snapshot = uiState.value
        val goal = snapshot.goals.firstOrNull { it.id == goalId }
        val before = goal?.let { g -> runCatching { GoalCalculator.saved(g, snapshot.contributions) }.getOrNull() }
        write({ goals.contribute(goalId, amount, date, note, key) }) { result ->
            onResult(result)
            if (result == GoalRepository.ContributionResult.Saved && goal != null && before != null && amount.currencyCode == goal.currencyCode) {
                val after = runCatching { before + amount }.getOrNull()
                after?.let { GoalMilestones.crossed(goal.target, before, it) }?.let(onMilestone)
            }
        }
    }

    fun deleteContribution(id: Long) = write({ goals.deleteContribution(id) }) {}

    fun deleteGoal(id: Long) = write({ goals.delete(id) }) {}

    // ---------------------------------------------------------------- recurring

    fun saveSchedule(input: RecurringRepository.Input, onResult: (RecurringRepository.SaveResult) -> Unit) =
        write({ recurring.save(input) }, onResult)

    fun markPaid(id: Long, amount: Money?, date: LocalDate?, onResult: (RecurringRepository.PayResult) -> Unit) =
        write({ recurring.markPaid(id, amount, date) }, onResult)

    fun skip(id: Long) = write({ recurring.skip(id) }) {}

    fun setPaused(id: Long, paused: Boolean) = write({ recurring.setPaused(id, paused) }) {}

    fun deleteSchedule(id: Long) = write({ recurring.delete(id) }) {}

    companion object {
        val Factory = containerFactory {
            PlanViewModel(it.transactions, it.wallets, it.categories, it.budgets, it.goals, it.recurring, it.preferences)
        }

        fun newKey(): String = UUID.randomUUID().toString()
    }
}
