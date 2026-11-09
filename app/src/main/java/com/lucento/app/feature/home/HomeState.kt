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
import com.lucento.app.domain.CategoryBudgetLine
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.SafeToSpend
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
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.TransactionQueries
import com.lucento.app.domain.UpcomingPayment
import com.lucento.app.domain.Wallet
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Monthly budget card's formula, read top to bottom (docs/CALCULATIONS.md):
 * budget − used = remaining, where used = budgeted + unbudgeted spending (every expense counts), and for this month
 * remaining − bills due = safe to spend. Each figure is taken from [BudgetPlan] or [SafeToSpend], never recomputed,
 * so the card can't disagree with the Budget tab.
 */
data class BudgetFormula(
    val budget: Money,
    val used: Money,
    val budgeted: Money,
    val unbudgeted: Money,
    val remaining: Money,
    /** Unpaid scheduled bills (any category; a debt payment's interest only) due by month end; null outside the current month. */
    val billsDue: Money?,
    val safeToSpend: Money?,
) {
    /** Remaining reads "Overspent" (red) once spending passes the budget, "Remaining" (green) otherwise. */
    val overspent: Boolean get() = remaining.isNegative

    /** Safe to spend always closes this month's card (owner, 2026-11-03: "sticky"); a past month ends at Remaining. */
    val showsSafeToSpend: Boolean get() = safeToSpend != null

    /** Nothing is left to spend safely: bills due or overspending have taken the rest. The card then reads 0, in red. */
    val nothingSafe: Boolean get() = safeToSpend != null && safeToSpend.amountMinor <= 0L

    /** The figure the card shows: what is safe to spend, never below zero (owner, 2026-11-03). Null for a past month. */
    val safeToSpendShown: Money? get() = safeToSpend?.let { if (it.amountMinor < 0L) Money.zero(it.currencyCode) else it }
}

/** The net-flow state that picks the indicator animation and colour. Zero is neutral, never "good" or "bad". */
enum class FlowDirection { POSITIVE, NEGATIVE, NEUTRAL }

/**
 * The cloud-sync state shown in the Home header. Local-first: [LOCAL] (nothing leaves the device) is the normal
 * state for everyone who has not opted into sync, never a warning.
 */
enum class CloudStatus(val label: String) {
    LOCAL("On device"),
    SYNCED("Synced"),
    /** P2-2: a sync is running in this process (including a retry after a failure). */
    SYNCING("Syncing"),
    PENDING("Waiting to sync"),
    OFFLINE("Offline"),
    ERROR("Needs attention"),
    /** Lucento Pro: sync is on but the plan doesn't include it; uploads wait, nothing is lost (docs/MONETIZATION.md §6.4). */
    PAUSED("Sync paused"),
    ;

    companion object {
        /**
         * [configured]: this build has a sync backend; [signedIn]/[enabled]: the user opted in. [pendingChanges] counts
         * local rows and deletions not yet confirmed by the server. Offline wins over an error, because a failed
         * sync while offline is expected and the user can do nothing about it but reconnect. A running sync shows as
         * Syncing even after an earlier failure: it is the retry.
         */
        fun of(
            configured: Boolean,
            signedIn: Boolean,
            enabled: Boolean,
            hasError: Boolean,
            everSynced: Boolean,
            pendingChanges: Int,
            online: Boolean,
            running: Boolean = false,
            planAllows: Boolean = true,
        ): CloudStatus = when {
            !configured || !signedIn || !enabled -> LOCAL
            !planAllows -> PAUSED
            !online -> OFFLINE
            running -> SYNCING
            hasError -> ERROR
            pendingChanges > 0 || !everSynced -> PENDING
            else -> SYNCED
        }
    }
}

/** What the sync details sheet says (P2-2). All local: preferences, Room counts and this process's sync state. */
/**
 * The header chip's words (owner, 2026-11-04: "it always says Synced — is it real time, or is something pending?").
 * Synced says how long ago, Waiting how many changes, so the chip is never a bare "Synced"; [syncChipDescription] is
 * the same in full words for TalkBack.
 */
fun syncChipLabel(status: CloudStatus, details: SyncDetails, nowMillis: Long): String = when (status) {
    CloudStatus.SYNCED -> details.lastSyncedAtMillis?.let { "Synced · ${shortAgo(nowMillis - it)}" } ?: status.label
    CloudStatus.PENDING -> when {
        !details.everSynced -> "Not synced yet"
        details.pendingChanges == 1 -> "1 to sync"
        details.pendingChanges > 1 -> "${details.pendingChanges} to sync"
        else -> status.label
    }
    else -> status.label
}

fun syncChipDescription(status: CloudStatus, details: SyncDetails, nowMillis: Long): String = "Sync status: " + when (status) {
    CloudStatus.SYNCED, CloudStatus.PENDING ->
        com.lucento.app.feature.settings.syncStatusText(details.lastSyncedAtMillis, nowMillis, details.pendingChanges, null)
    else -> status.label
}

/** "now", "5m", "3h", "2d": how long ago, short enough for the header chip. Never negative (a clock moved back). */
fun shortAgo(elapsedMillis: Long): String {
    val minutes = elapsedMillis.coerceAtLeast(0L) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

data class SyncDetails(
    val email: String? = null,
    val lastSyncedAtMillis: Long? = null,
    val pendingChanges: Int = 0,
    val lastError: String? = null,
    /** P2-6: this phone's own changes replaced by newer ones from another device, until dismissed. */
    val overriddenChanges: Int = 0,
    /** This build has a sync backend at all (debug builds without test keys don't). */
    val configured: Boolean = true,
) {
    val everSynced: Boolean get() = lastSyncedAtMillis != null

    /** The stored error is the account guard: this phone's ledger belongs to a different account. */
    val accountMismatch: Boolean get() = lastError == com.lucento.app.data.sync.SyncRepository.ACCOUNT_MISMATCH_MESSAGE
}

/**
 * P2-1: Home's recent activity — the [limit] newest entries by date, then by entry order. A transfer is two linked legs;
 * only the outgoing one is listed so it shows once. Nothing is converted or summed here.
 */
fun recentActivity(all: List<Transaction>, limit: Int = 3): List<Transaction> =
    all.filter { it.type != TransactionType.TRANSFER_IN }
        .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })
        .take(limit)

/**
 * P2-6: the notice shown only when this phone's own unsynced change lost to a newer one from another device. Null when
 * nothing was replaced. The newest version is the one kept on every device (owner decisions D2, D3).
 */
fun overriddenNotice(count: Int): String? = when {
    count <= 0 -> null
    count == 1 -> "A change you made on this phone was replaced by a newer change from another device. The newest version is kept everywhere."
    else -> "$count changes you made on this phone were replaced by newer changes from another device. The newest version is kept everywhere."
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
        // N-15: on the month's last day a "per day" figure is just what's left, so say that plainly instead.
        val allowance = burn.dailyAllowance?.let {
            if (burn.daysLeft == 1) "Last day of $monthName: ${MoneyFormatter.format(plan.remaining)} left in your budget"
            else "About ${MoneyFormatter.format(it)} a day keeps you within budget"
        }
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
/** One slice of the month's spending: a category (or "No category", or the rest as "Other") in one currency. */
data class SpendingSlice(val categoryId: Long?, val label: String, val amount: Money, val fraction: Float, val isOther: Boolean = false)

/**
 * Part E: the month's spending by category in [currency] — the [top] largest, then everything else as "Other".
 * Expenses only (transfers, adjustments and income never count), never converted. Empty when nothing was spent.
 */
fun spendingBreakdown(
    all: List<Transaction>,
    categoriesById: Map<Long, Category>,
    currency: String,
    month: YearMonth,
    top: Int = 4,
): List<SpendingSlice> {
    val expenses = all.filter { it.type == TransactionType.EXPENSE && it.currencyCode == currency && YearMonth.from(it.date) == month }
    if (expenses.isEmpty()) return emptyList()
    val groups = expenses.groupBy { it.categoryId }
        .map { (id, list) -> id to Money.sum(currency, list.map { it.amount }) }
        .sortedWith(compareByDescending<Pair<Long?, Money>> { it.second.amountMinor }.thenBy { it.first ?: Long.MAX_VALUE })
    val total = Money.sum(currency, groups.map { it.second })
    fun share(m: Money) = if (total.amountMinor == 0L) 0f else (m.amountMinor.toDouble() / total.amountMinor).toFloat()
    val shown = groups.take(top).map { (id, amount) ->
        SpendingSlice(id, id?.let { categoriesById[it]?.name } ?: "No category", amount, share(amount))
    }
    val rest = groups.drop(top)
    return if (rest.isEmpty()) shown
    else shown + Money.sum(currency, rest.map { it.second }).let { SpendingSlice(null, "Other", it, share(it), isOther = true) }
}

/**
 * Part D: the one-time "+" spotlight shows after setup (the guided setup finished) until the first expense or income is
 * recorded or the user dismisses it. People who already record never see it.
 */
fun showAddSpotlight(seen: Boolean, guidePending: Boolean, recordedCount: Int): Boolean = !seen && !guidePending && recordedCount == 0

/** Existing installs that finished setup before the local-only warning are shown it once on Home. */
fun showLocalDataNotice(onboardingComplete: Boolean, noticeSeen: Boolean): Boolean =
    onboardingComplete && !noticeSeen

data class HomeUiState(
    val loading: Boolean = true,
    /** One-time warning for people who finished setup before that copy existed. */
    val showLocalDataNotice: Boolean = false,
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
    /** P2-1: the latest few entries, newest first; a transfer appears once (its outgoing leg). */
    val recent: List<Transaction> = emptyList(),
    /** The transfers among [recent], by group, so each row can say "From → To". */
    val recentTransfers: Map<String, com.lucento.app.domain.Transfer> = emptyMap(),
    /** Overdue and due-within-a-week items, for the header bell; empty when Recurring is off. */
    val dueSoon: List<UpcomingPayment> = emptyList(),
    val hasAnySchedules: Boolean = false,
    val categoriesById: Map<Long, Category> = emptyMap(),
    val cloudStatus: CloudStatus = CloudStatus.LOCAL,
    /** The user hid the Dashboard's amounts (shown as "₱••••"); showing them again asks for the phone's lock. */
    val hideAmounts: Boolean = false,
    val syncDetails: SyncDetails = SyncDetails(),
    /** P2-5: the user hid the offers for modules that are off. */
    val moduleOffersHidden: Boolean = false,
    /** Part E: the shown month's spending by category in [focusCurrency]. */
    val spending: List<SpendingSlice> = emptyList(),
    /** Part D: milestones earned from the user's records. */
    val earned: Set<com.lucento.app.domain.Achievement> = emptySet(),
    /** The records behind [earned], for each award's progress in the cabinet; null until loaded. */
    val awardFacts: com.lucento.app.domain.ProgressFacts? = null,
    /** Part D: the milestone to acknowledge now (once, after the write that earned it); null when none. */
    val milestoneToShow: com.lucento.app.domain.Achievement? = null,
    /** Other awards earned at the same time as [milestoneToShow]; presented in the same ceremony, never queued. */
    val milestonesAlsoNew: List<com.lucento.app.domain.Achievement> = emptyList(),
    /** Part D: first time milestones exist on this install — record what's already earned without a fuss. */
    val needsMilestoneBaseline: Boolean = false,
    /** Expenses and income recorded so far (internal movements excluded). */
    val recordedCount: Int = 0,
    /** Part D: the one-time "+" spotlight after setup, until the first entry or a dismissal. */
    val showAddSpotlight: Boolean = false,
    /** Wallet card: balances at the end of [month] (today's for the current month) in [focusCurrency]. */
    val monthComposition: NetWorthComposition? = null,
    /** Wallet card: net wallet balance at the end of last month and [month] (oldest first), for "vs last month". */
    val balanceHistory: List<com.lucento.app.domain.MonthBalance> = emptyList(),
    /** Change across [balanceHistory] in whole percent, only when meaningful (see BalanceHistory.trendPercent). */
    val balanceTrend: Int? = null,
    /**
     * Budget card: what can still be spent this month after the bills due by its end ([SafeToSpend.Basis.BUDGET]
     * only). Null for past months, without a budget, or when Budgets is off — the card then shows what's left instead.
     */
    val safeToSpend: SafeToSpend? = null,
    /** Budget card ("To watch"): up to five budgeted categories with spending in [month], closest to their limit first. */
    val watchLines: List<CategoryBudgetLine> = emptyList(),
    /** Upcoming scheduled items due by the end of [month] (overdue included); empty for past months. */
    val scheduledThisMonth: List<UpcomingPayment> = emptyList(),
    /**
     * Credit card payments due by the end of [month] (overdue included), from each card's billing days — reminders only,
     * never part of [safeToSpend] (CreditCardMath). Empty for past months.
     */
    val cardDues: List<com.lucento.app.domain.CardDue> = emptyList(),
    /** Card payments overdue or due within a week, for the header bell beside [dueSoon]. */
    val cardDuesSoon: List<com.lucento.app.domain.CardDue> = emptyList(),
    /** The newest entries dated in [month], newest first (a transfer once). */
    val recentInMonth: List<Transaction> = emptyList(),
) {
    /** Getting started (progressive disclosure): shown until accounts, a budget and a first entry are all in place. */
    val gettingStartedLeft: List<com.lucento.app.domain.Achievement>
        get() = listOf(
            com.lucento.app.domain.Achievement.MONEY_KEEPER,
            com.lucento.app.domain.Achievement.BUDGET_BUILDER,
            com.lucento.app.domain.Achievement.FIRST_STEP,
        ).filter { it !in earned && (it != com.lucento.app.domain.Achievement.BUDGET_BUILDER || features.isEnabled(Feature.BUDGETS)) }
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
        /** Archived goals only count toward milestones (setting a goal stays earned after archiving it). */
        archivedGoals: List<Goal> = emptyList(),
        contributions: List<GoalContribution> = emptyList(),
        displayName: String? = null,
        /** The month to describe; clamped to [earliest month, current month]. */
        month: YearMonth = YearMonth.from(today),
        cloudStatus: CloudStatus = CloudStatus.LOCAL,
        visitedInsights: Boolean = false,
    ): HomeUiState {
        // Multicurrency (docs/CALCULATIONS.md "Exchange rates"): every figure below is in the main currency — wallets in
        // other currencies at their carrying value, their movements at their own value. Lists of what was recorded
        // (recent activity, card dues) keep each movement's own currency. A wallet with no rate yet stays in its own.
        val ledger = com.lucento.app.domain.fx.FxTranslation.translate(primaryCurrency, wallets, all)
        val valued = ledger.wallets
        val recorded = ledger.transactions
        val focus = com.lucento.app.domain.FocusCurrency.of(primaryCurrency, valued, recorded)
        val current = YearMonth.from(today)
        val earliest = all.minOfOrNull { it.date }?.let { YearMonth.from(it) }?.coerceAtMost(current) ?: current
        val shown = month.coerceIn(earliest, current)
        val monthTotals = TransactionQueries.totalsByCurrency(recorded, DateRange.monthOf(shown.atDay(1)), focus)
        val budgetsOn = features.isEnabled(Feature.BUDGETS)
        val recurringOn = features.isEnabled(Feature.RECURRING)
        val goalsOn = features.isEnabled(Feature.SAVINGS_GOALS)
        // Disabled modules keep their data but do not influence the dashboard.
        val plan = if (budgetsOn) BudgetPlanner.plan(categories, budgets, recorded, focus, shown) else null
        val activeSchedules = if (recurringOn) schedules else emptyList()
        val netWorth = if (wallets.isEmpty()) null else NetWorthCalculator.summarize(valued, primaryCurrency)
        val monthChange = if (wallets.isEmpty()) null else CashflowCalculator.netFlow(focus, current, recorded).net.takeUnless { it.isZero }
        val spending = spendingBreakdown(recorded, categories.associateBy { it.id }, focus, shown)
        // Past balances replay movements and FX results together, so they land on the carrying values.
        val history = if (wallets.isEmpty()) emptyList() else com.lucento.app.domain.BalanceHistory.lastMonths(valued, ledger.withFxResults, focus, shown, today, count = 2)
        val recentInMonth = recentActivity(all.filter { YearMonth.from(it.date) == shown }, limit = RECENT_LIMIT)
        val months = generateSequence(current) { it.minusMonths(1) }
            .takeWhile { !it.isBefore(earliest) }
            .take(MAX_SELECTABLE_MONTHS)
            .toList()
        val awardFacts = com.lucento.app.domain.Achievements.facts(all, wallets, categories, budgets, goals + archivedGoals, today, visitedInsights)

        return HomeUiState(
            loading = false,
            today = today,
            primaryCurrency = primaryCurrency,
            displayName = displayName,
            features = features,
            wallets = wallets,
            netWorth = netWorth,
            netWorthComposition = if (wallets.isEmpty()) null else NetWorthComposer.compose(valued, focus),
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
            otherBudgetCurrencies = if (budgetsOn) BudgetPlanner.currencies(budgets, categories, focus, shown).drop(1) else emptyList(),
            netFlow = if (wallets.isEmpty()) null else CashflowCalculator.netFlow(focus, shown, recorded),
            goals = if (goalsOn) GoalsSummary.overview(goals, contributions, focus, today) else null,
            hasAnyGoals = goalsOn && goals.isNotEmpty(),
            upcoming = RecurringMath.upcomingPayments(activeSchedules, today),
            recent = recentActivity(all, limit = RECENT_LIMIT),
            dueSoon = RecurringMath.dueSoon(activeSchedules, today),
            hasAnySchedules = activeSchedules.any { !it.isFinished },
            categoriesById = categories.associateBy { it.id },
            cloudStatus = cloudStatus,
            spending = spending,
            earned = com.lucento.app.domain.Achievements.earned(awardFacts),
            awardFacts = awardFacts,
            recordedCount = all.count { com.lucento.app.domain.Achievements.countsAsRecorded(it.type) },
            monthComposition = if (wallets.isEmpty()) null else com.lucento.app.domain.BalanceHistory.compositionAt(valued, ledger.withFxResults, focus, shown, today),
            balanceHistory = history,
            balanceTrend = com.lucento.app.domain.BalanceHistory.comparableTrendPercent(history, valued, focus),
            safeToSpend = if (plan?.hasBudget == true && shown == current) {
                CashflowCalculator.safeToSpend(focus, today, recorded, valued, budgets, activeSchedules, categories)
                    ?.takeIf { it.basis == SafeToSpend.Basis.BUDGET }
            } else {
                null
            },
            watchLines = watchList(plan),
            scheduledThisMonth = if (shown != current) emptyList() else RecurringMath.upcomingPayments(
                activeSchedules,
                today,
                horizonDays = java.time.temporal.ChronoUnit.DAYS.between(today, shown.atEndOfMonth()),
                limit = SCHEDULED_LIMIT,
            ),
            recentInMonth = recentInMonth,
            cardDues = if (shown != current) emptyList() else com.lucento.app.domain.CreditCardMath.dues(wallets, all, today, shown.atEndOfMonth()),
            cardDuesSoon = com.lucento.app.domain.CreditCardMath.dues(wallets, all, today, today.plusDays(RecurringMath.DUE_SOON_DAYS)),
            recentTransfers = (recentActivity(all, limit = RECENT_LIMIT) + recentInMonth).mapNotNull { it.transferGroup }.toSet().let { groups ->
                if (groups.isEmpty()) emptyMap() else com.lucento.app.domain.Transfer.pair(all.filter { it.transferGroup in groups })
            },
        )
    }

    /**
     * The budget card's "To watch" list: budgeted categories that have spending this month, closest to their limit first
     * (the plan's own order, [BudgetPlanner.plan]), at most [WATCH_LIMIT]. Untouched categories are left out: a 0 % bar
     * tells the user nothing.
     */
    fun watchList(plan: BudgetPlan?): List<CategoryBudgetLine> =
        plan?.allocatedLines.orEmpty().filter { it.spent.amountMinor > 0L }.take(WATCH_LIMIT)

    const val WATCH_LIMIT = 5

    /**
     * The budget card's formula. [safe] is [HomeUiState.safeToSpend]: present only for this month's budget, so a past
     * month ends at Remaining.
     */
    fun budgetFormula(plan: BudgetPlan, safe: SafeToSpend?): BudgetFormula = BudgetFormula(
        budget = plan.total,
        used = plan.spent,
        budgeted = plan.budgetedSpending,
        unbudgeted = plan.unbudgetedSpending,
        remaining = plan.remaining,
        billsDue = safe?.committed,
        safeToSpend = safe?.amount,
    )

    /** The dashboard's scheduled section lists at most this many. */
    const val SCHEDULED_LIMIT = 3

    /** Home lists up to five recent entries (Part E). */
    const val RECENT_LIMIT = 5
}
