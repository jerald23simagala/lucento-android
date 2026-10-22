package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetCalculator
import com.lucento.app.domain.BudgetProgress
import com.lucento.app.domain.BudgetStatus
import com.lucento.app.domain.BurnRate
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryShare
import com.lucento.app.domain.DateRange
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Insights
import com.lucento.app.domain.MonthAmount
import com.lucento.app.domain.NetFlow
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.PeriodTotals
import com.lucento.app.domain.RecurringMath
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.SafeToSpend
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionQueries
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.Wallet
import java.time.LocalDate
import java.time.YearMonth

/** A bill or income due soon, from a recurring schedule. */
data class UpcomingItem(val schedule: RecurringSchedule, val dueDate: LocalDate)

/** Everything the Home dashboard shows. All figures are derived from stored data; nothing is invented. */
data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val primaryCurrency: String = "USD",
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
    val safeToSpend: SafeToSpend? = null,
    val burnRate: BurnRate? = null,
    val netFlow: NetFlow? = null,
    /** Budgets at or over 80% this month (only when Budgets is enabled). */
    val budgetAlerts: List<BudgetProgress> = emptyList(),
    /** Unpaid recurring items due within the next week, overdue included (only when Recurring is enabled). */
    val upcoming: List<UpcomingItem> = emptyList(),
    /** Spending per month in [focusCurrency], oldest first, ending with the current month. */
    val trend: List<MonthAmount> = emptyList(),
    /** This month's spending by category in [focusCurrency], largest first. */
    val topCategories: List<CategoryShare> = emptyList(),
    /** Latest activity; each transfer appears once (its outgoing leg). */
    val recent: List<Transaction> = emptyList(),
    val transfers: Map<String, Transfer> = emptyMap(),
    val transactionCount: Int = 0,
    val categoriesById: Map<Long, Category> = emptyMap(),
    val hasBudget: Boolean = false,
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
    val hasWallets: Boolean get() = wallets.isNotEmpty()
    val hasTrend: Boolean get() = trend.any { !it.amount.isZero }
    val monthSpend: Money? get() = monthTotals?.expense
}

/** Pure assembly of [HomeUiState] from repository data. No Android dependencies, so it is unit-tested. */
object HomeStateBuilder {
    const val TREND_MONTHS = 6
    const val RECENT_COUNT = 5
    const val TOP_CATEGORIES = 4
    const val UPCOMING_DAYS = 7L

    /** Pure assembly of the Home state; [all] must be sorted newest first (as the DAO returns it). */
    fun build(
        all: List<Transaction>,
        wallets: List<Wallet>,
        categories: List<Category>,
        primaryCurrency: String,
        today: LocalDate,
        budgets: List<Budget> = emptyList(),
        schedules: List<RecurringSchedule> = emptyList(),
        features: FeatureSet = FeatureSet.ALL,
    ): HomeUiState {
        val focus = when {
            wallets.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            all.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            else -> wallets.firstOrNull()?.currencyCode ?: primaryCurrency
        }
        val month = YearMonth.from(today)
        val monthRange = DateRange.monthOf(today)
        val monthTotals = TransactionQueries.totalsByCurrency(all, monthRange, focus)
        val budgetsOn = features.isEnabled(Feature.BUDGETS)
        val recurringOn = features.isEnabled(Feature.RECURRING)
        // Disabled modules keep their data but do not influence the dashboard.
        val activeBudgets = if (budgetsOn) budgets else emptyList()
        val activeSchedules = if (recurringOn) schedules else emptyList()
        val withoutInLegs = all.filterNot { it.type == TransactionType.TRANSFER_IN }

        return HomeUiState(
            loading = false,
            today = today,
            primaryCurrency = primaryCurrency,
            features = features,
            wallets = wallets,
            netWorth = if (wallets.isEmpty()) null else NetWorthCalculator.summarize(wallets, primaryCurrency),
            focusCurrency = focus,
            month = month,
            monthTotals = monthTotals.firstOrNull { it.currencyCode == focus },
            otherMonthTotals = monthTotals.filter { it.currencyCode != focus },
            safeToSpend = if (wallets.isEmpty()) {
                null
            } else {
                CashflowCalculator.safeToSpend(focus, today, all, wallets, activeBudgets, activeSchedules)
            },
            burnRate = if (wallets.isEmpty()) null else CashflowCalculator.burnRate(focus, today, all),
            netFlow = if (wallets.isEmpty()) null else CashflowCalculator.netFlow(focus, month, all),
            budgetAlerts = BudgetCalculator.progressAll(activeBudgets, all, month)
                .filter { it.status != BudgetStatus.ON_TRACK },
            upcoming = RecurringMath.upcoming(activeSchedules, today.plusDays(UPCOMING_DAYS))
                .map { (s, d) -> UpcomingItem(s, d) },
            trend = Insights.monthlyTotals(all, focus, month, TREND_MONTHS),
            topCategories = Insights.categoryBreakdown(all, focus, monthRange).take(TOP_CATEGORIES),
            recent = withoutInLegs.take(RECENT_COUNT),
            transfers = Transfer.pair(all),
            transactionCount = withoutInLegs.size,
            categoriesById = categories.associateBy { it.id },
            hasBudget = activeBudgets.any { it.isOverall && it.currencyCode == focus },
        )
    }
}
