package com.lucento.app.feature.home

import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DateRange
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.GoalsOverview
import com.lucento.app.domain.GoalsSummary
import com.lucento.app.domain.NetFlow
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.PeriodTotals
import com.lucento.app.domain.RecurringMath
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionQueries
import com.lucento.app.domain.UpcomingPayment
import com.lucento.app.domain.Wallet
import java.time.LocalDate
import java.time.YearMonth

/** The net-flow state that picks the indicator animation and colour. Zero is neutral, never "good" or "bad". */
enum class FlowDirection { POSITIVE, NEGATIVE, NEUTRAL }

/**
 * Everything the Home dashboard shows, in the order it is shown: the budget hero (with the Sun), net flow, the
 * burn-rate bar, the savings level and upcoming recurring payments. All figures are derived from stored data
 * through the domain calculators; nothing is invented, and the hero, the burn bar and the Budget screen read the
 * same [BudgetPlan].
 */
data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val primaryCurrency: String = "USD",
    val displayName: String? = null,
    val features: FeatureSet = FeatureSet.ALL,
    val wallets: List<Wallet> = emptyList(),
    val netWorth: NetWorthCalculator.Summary? = null,
    /**
     * The currency the dashboard is shown in: the main currency when the user holds or spends it,
     * otherwise the currency of their first wallet. Never a conversion.
     */
    val focusCurrency: String = "USD",
    val month: YearMonth = YearMonth.now(),
    val monthTotals: PeriodTotals? = null,
    /** This month's totals in currencies other than [focusCurrency]. */
    val otherMonthTotals: List<PeriodTotals> = emptyList(),
    /** The month's category-based budget in [focusCurrency]; null when Budgets is switched off. */
    val budget: BudgetPlan? = null,
    val burn: BudgetBurn? = null,
    /** Active expense categories — zero means category setup is unfinished. */
    val expenseCategoryCount: Int = 0,
    /** Other currencies with category allocations (shown in full on the Budget screen). */
    val otherBudgetCurrencies: List<String> = emptyList(),
    val netFlow: NetFlow? = null,
    /** Savings level in [focusCurrency]; null when Savings goals is off or there are no goals in it. */
    val goals: GoalsOverview? = null,
    val hasAnyGoals: Boolean = false,
    /** Next unpaid recurring items (overdue flagged), in date order; empty when Recurring is off. */
    val upcoming: List<UpcomingPayment> = emptyList(),
    val hasAnySchedules: Boolean = false,
    val categoriesById: Map<Long, Category> = emptyMap(),
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
    val hasWallets: Boolean get() = wallets.isNotEmpty()

    val flowDirection: FlowDirection
        get() {
            val net = netFlow?.net ?: return FlowDirection.NEUTRAL
            return when {
                net.isZero -> FlowDirection.NEUTRAL
                net.isNegative -> FlowDirection.NEGATIVE
                else -> FlowDirection.POSITIVE
            }
        }
}

/** Pure assembly of [HomeUiState] from repository data. No Android dependencies, so it is unit-tested. */
object HomeStateBuilder {

    fun build(
        all: List<Transaction>,
        wallets: List<Wallet>,
        categories: List<Category>,
        primaryCurrency: String,
        today: LocalDate,
        budgets: List<Budget> = emptyList(),
        schedules: List<RecurringSchedule> = emptyList(),
        features: FeatureSet = FeatureSet.ALL,
        goals: List<Goal> = emptyList(),
        contributions: List<GoalContribution> = emptyList(),
        displayName: String? = null,
    ): HomeUiState {
        val focus = when {
            wallets.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            all.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            else -> wallets.firstOrNull()?.currencyCode ?: primaryCurrency
        }
        val month = YearMonth.from(today)
        val monthTotals = TransactionQueries.totalsByCurrency(all, DateRange.monthOf(today), focus)
        val budgetsOn = features.isEnabled(Feature.BUDGETS)
        val recurringOn = features.isEnabled(Feature.RECURRING)
        val goalsOn = features.isEnabled(Feature.SAVINGS_GOALS)
        // Disabled modules keep their data but do not influence the dashboard.
        val plan = if (budgetsOn) BudgetPlanner.plan(categories, budgets, all, focus, month) else null
        val activeSchedules = if (recurringOn) schedules else emptyList()

        return HomeUiState(
            loading = false,
            today = today,
            primaryCurrency = primaryCurrency,
            displayName = displayName,
            features = features,
            wallets = wallets,
            netWorth = if (wallets.isEmpty()) null else NetWorthCalculator.summarize(wallets, primaryCurrency),
            focusCurrency = focus,
            month = month,
            monthTotals = monthTotals.firstOrNull { it.currencyCode == focus },
            otherMonthTotals = monthTotals.filter { it.currencyCode != focus },
            budget = plan,
            burn = plan?.let { BudgetPlanner.burn(it, today) },
            expenseCategoryCount = categories.count { it.kind == CategoryKind.EXPENSE && !it.archived },
            otherBudgetCurrencies = if (budgetsOn) BudgetPlanner.currencies(budgets, categories, focus).drop(1) else emptyList(),
            netFlow = if (wallets.isEmpty()) null else CashflowCalculator.netFlow(focus, month, all),
            goals = if (goalsOn) GoalsSummary.overview(goals, contributions, focus, today) else null,
            hasAnyGoals = goalsOn && goals.isNotEmpty(),
            upcoming = RecurringMath.upcomingPayments(activeSchedules, today),
            hasAnySchedules = activeSchedules.any { !it.isFinished },
            categoriesById = categories.associateBy { it.id },
        )
    }
}
