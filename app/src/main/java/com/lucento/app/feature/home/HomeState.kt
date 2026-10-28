package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.domain.BudgetHealth
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
import com.lucento.app.domain.NetWorthComposer
import com.lucento.app.domain.NetWorthComposition
import com.lucento.app.domain.PacePosition
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
 * The cloud-sync state shown in the Home header. Local-first: [LOCAL] (nothing leaves the device) is the normal
 * state for everyone who has not opted into sync, never a warning.
 */
enum class CloudStatus(val label: String) {
    LOCAL("On device"),
    SYNCED("Synced"),
    PENDING("Sync needed"),
    OFFLINE("Offline"),
    ERROR("Sync issue"),
    ;

    companion object {
        /**
         * [configured]: this build has a sync backend; [signedIn]/[enabled]: the user opted in. [pendingChanges] counts
         * local rows and deletions not yet confirmed by the server. Offline wins over an error, because a failed
         * sync while offline is expected and the user can do nothing about it but reconnect.
         */
        fun of(
            configured: Boolean,
            signedIn: Boolean,
            enabled: Boolean,
            hasError: Boolean,
            everSynced: Boolean,
            pendingChanges: Int,
            online: Boolean,
        ): CloudStatus = when {
            !configured || !signedIn || !enabled -> LOCAL
            !online -> OFFLINE
            hasError -> ERROR
            pendingChanges > 0 || !everSynced -> PENDING
            else -> SYNCED
        }
    }
}

/**
 * The words on the monthly budget card: Lucento's one-line [bubble] (the mascot's speech bubble beside the month pill),
 * the one-line [message] in the advice banner, and its second line [detail] (the daily allowance) when there is one. The verdict is always spelled out, so meaning never depends on colour. Built from the same
 * [BudgetPlan] and [BudgetBurn] the card draws.
 */
data class PaceInsight(val position: PacePosition?, val bubble: String, val message: String, val detail: String? = null)

object PaceCopy {

    fun describe(plan: BudgetPlan, burn: BudgetBurn, monthName: String, isCurrentMonth: Boolean): PaceInsight {
        val used = burn.consumedPercent ?: 0
        val pace = burn.pace
        if (!plan.hasBudget) {
            return PaceInsight(
                position = null,
                bubble = "Set a budget and I’ll keep pace with you.",
                message = "No budget for $monthName yet. Give your categories an amount and Lucento compares your spending with the calendar.",
            )
        }
        if (plan.health == BudgetHealth.EXCEEDED) {
            val over = MoneyFormatter.format(-plan.remaining)
            val total = MoneyFormatter.format(plan.total)
            return if (isCurrentMonth) {
                PaceInsight(
                    PacePosition.AHEAD,
                    "Over budget. Let’s regroup!",
                    "You’re $over over your $total budget with ${daysLeftLabel(burn.daysLeft)} left. Pause non-essential spending where you can.",
                )
            } else {
                PaceInsight(PacePosition.AHEAD, "$monthName went over budget.", "$monthName ended $over over the $total budget.")
            }
        }
        if (!isCurrentMonth) {
            return PaceInsight(
                pace,
                "$monthName is wrapped up!",
                "$monthName ended at $used% of budget, with ${MoneyFormatter.format(plan.remaining)} unused.",
            )
        }
        val allowance = burn.dailyAllowance?.let { "About ${MoneyFormatter.format(it)} a day keeps you within budget" }
        return when (pace) {
            PacePosition.AHEAD -> PaceInsight(pace, "Ahead of the month. Let’s slow down!", "Spending faster than the month", allowance)
            PacePosition.BEHIND -> PaceInsight(pace, "Below the month pace. All good!", "Spending slower than the month", allowance)
            else -> PaceInsight(PacePosition.ALIGNED, "On track! Keep it up!", "Spending in step with the month", allowance)
        }
    }

}

/**
 * Phrase for [BudgetBurn.daysLeft]: today counts, so the last day of the month is "1 day", never "0 days"
 * beside a full day's allowance. The number is [BudgetBurn.daysLeft]; this only pluralises it.
 */
internal fun daysLeftLabel(daysLeft: Int): String = if (daysLeft == 1) "1 day" else "$daysLeft days"

/**
 * Everything the Home dashboard shows, in the order it is shown: the header (brand, sync status, month), the
 * Budget pace card (budget used against month elapsed, with the mascot), net worth with shortcuts, the savings goal
 * closest to done and scheduled transactions. All figures are derived from stored data through the domain
 * calculators; nothing is invented, and the pace card and the Budget screen read the same [BudgetPlan].
 */
data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val primaryCurrency: String = "USD",
    val displayName: String? = null,
    val features: FeatureSet = FeatureSet.ALL,
    val wallets: List<Wallet> = emptyList(),
    val netWorth: NetWorthCalculator.Summary? = null,
    /** Assets, liabilities and their difference in [focusCurrency], from the same balances as [netWorth]. */
    val netWorthComposition: NetWorthComposition? = null,
    /** Net worth in currencies other than [focusCurrency] (shown separately, never converted). */
    val otherNetWorth: List<Money> = emptyList(),
    /**
     * The currency the dashboard is shown in: the main currency when the user holds or spends it,
     * otherwise the currency of their first wallet. Never a conversion.
     */
    val focusCurrency: String = "USD",
    /** The month the budget card and cash flow describe; the current month unless the user picked another. */
    val month: YearMonth = YearMonth.now(),
    /** The oldest month the month selector offers (the month of the first transaction, or this month). */
    val earliestMonth: YearMonth = YearMonth.now(),
    /** What the month selector lists, newest first: this month back to [earliestMonth] (at most two years). */
    val availableMonths: List<YearMonth> = listOf(YearMonth.now()),
    /**
     * How much net worth moved this calendar month through recorded income and spending in [focusCurrency]
     * (transfers move money between your own accounts and do not count). Null when nothing moved.
     */
    val netWorthMonthChange: Money? = null,
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
    /** Overdue and due-within-a-week items, for the header bell; empty when Recurring is off. */
    val dueSoon: List<UpcomingPayment> = emptyList(),
    val hasAnySchedules: Boolean = false,
    val categoriesById: Map<Long, Category> = emptyMap(),
    val cloudStatus: CloudStatus = CloudStatus.LOCAL,
) {
    val walletsById: Map<Long, Wallet> = wallets.associateBy { it.id }
    val hasWallets: Boolean get() = wallets.isNotEmpty()
    val isCurrentMonth: Boolean get() = month == YearMonth.from(today)
    val canGoToPreviousMonth: Boolean get() = month.isAfter(earliestMonth)
    val canGoToNextMonth: Boolean get() = month.isBefore(YearMonth.from(today))

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

    /** The month selector reaches back two years at most. */
    const val MAX_SELECTABLE_MONTHS = 24

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
        /** The month to describe; clamped to [earliest month, current month]. */
        month: YearMonth = YearMonth.from(today),
        cloudStatus: CloudStatus = CloudStatus.LOCAL,
    ): HomeUiState {
        val focus = when {
            wallets.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            all.any { it.currencyCode == primaryCurrency } -> primaryCurrency
            else -> wallets.firstOrNull()?.currencyCode ?: primaryCurrency
        }
        val current = YearMonth.from(today)
        val earliest = all.minOfOrNull { it.date }?.let { YearMonth.from(it) }?.coerceAtMost(current) ?: current
        val shown = month.coerceIn(earliest, current)
        val monthTotals = TransactionQueries.totalsByCurrency(all, DateRange.monthOf(shown.atDay(1)), focus)
        val budgetsOn = features.isEnabled(Feature.BUDGETS)
        val recurringOn = features.isEnabled(Feature.RECURRING)
        val goalsOn = features.isEnabled(Feature.SAVINGS_GOALS)
        // Disabled modules keep their data but do not influence the dashboard.
        val plan = if (budgetsOn) BudgetPlanner.plan(categories, budgets, all, focus, shown) else null
        val activeSchedules = if (recurringOn) schedules else emptyList()
        val netWorth = if (wallets.isEmpty()) null else NetWorthCalculator.summarize(wallets, primaryCurrency)
        val monthChange = if (wallets.isEmpty()) null else CashflowCalculator.netFlow(focus, current, all).net.takeUnless { it.isZero }
        val months = generateSequence(current) { it.minusMonths(1) }
            .takeWhile { !it.isBefore(earliest) }
            .take(MAX_SELECTABLE_MONTHS)
            .toList()

        return HomeUiState(
            loading = false,
            today = today,
            primaryCurrency = primaryCurrency,
            displayName = displayName,
            features = features,
            wallets = wallets,
            netWorth = netWorth,
            netWorthComposition = if (wallets.isEmpty()) null else NetWorthComposer.compose(wallets, focus),
            otherNetWorth = netWorth?.totals.orEmpty().filter { it.currencyCode != focus },
            focusCurrency = focus,
            month = shown,
            earliestMonth = earliest,
            availableMonths = months,
            netWorthMonthChange = monthChange,
            monthTotals = monthTotals.firstOrNull { it.currencyCode == focus },
            otherMonthTotals = monthTotals.filter { it.currencyCode != focus },
            budget = plan,
            burn = plan?.let { BudgetPlanner.burn(it, today) },
            expenseCategoryCount = categories.count { it.kind == CategoryKind.EXPENSE && !it.archived },
            otherBudgetCurrencies = if (budgetsOn) BudgetPlanner.currencies(budgets, categories, focus).drop(1) else emptyList(),
            netFlow = if (wallets.isEmpty()) null else CashflowCalculator.netFlow(focus, shown, all),
            goals = if (goalsOn) GoalsSummary.overview(goals, contributions, focus, today) else null,
            hasAnyGoals = goalsOn && goals.isNotEmpty(),
            upcoming = RecurringMath.upcomingPayments(activeSchedules, today),
            dueSoon = RecurringMath.dueSoon(activeSchedules, today),
            hasAnySchedules = activeSchedules.any { !it.isFinished },
            categoriesById = categories.associateBy { it.id },
            cloudStatus = cloudStatus,
        )
    }
}
