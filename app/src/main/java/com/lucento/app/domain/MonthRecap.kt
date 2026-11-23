package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One category's money in a month: what was spent (or, in [RecapBudget.over], how far over), with its share. */
data class CategoryAmount(val categoryId: Long?, val amount: Money, val percent: Int? = null)

/** How a category's spending moved from the month before. [change] is signed: positive means more was spent. */
data class CategoryChange(val categoryId: Long?, val current: Money, val previous: Money) {
    val change: Money get() = current - previous
}

/** A month's budget as the recap tells it, from the same [BudgetPlan] as the Budget tab. */
data class RecapBudget(
    val total: Money,
    val spent: Money,
    /** Categories with an amount that month. */
    val budgeted: Int,
    /** Of those, the ones that didn't go over. */
    val within: Int,
    /** The categories that went over, by how much, most first. */
    val over: List<CategoryAmount>,
    /**
     * The day the first amount in this currency was set, when that was after the month's 1st: the month didn't have a
     * budget all through, so it can't count for Steady Month (critic r2, P1-3). Null when the budget was there from the 1st,
     * or when an amount without a date leaves it unknown.
     */
    val since: LocalDate? = null,
) {
    /** Budget minus every expense: negative when the month went over. */
    val remaining: Money get() = total - spent
    val kept: Boolean get() = !remaining.isNegative
}

/**
 * One month's story for the Milestones page (docs/CALCULATIONS.md "Milestones recap"). Every figure comes from a calculator
 * the rest of the app already uses for it, so the recap always agrees with Home, Budget, Wallets and Goals. One currency,
 * the main one; nothing is converted here.
 */
data class MonthRecap(
    val month: YearMonth,
    val currencyCode: String,
    /** Before today's month: final figures. Otherwise "so far". */
    val finished: Boolean,
    val income: Money,
    val spent: Money,
    /** Null when Budgets is off or the month had no budget. */
    val budget: RecapBudget?,
    /** The month's largest expense categories (at most [MonthRecaps.TOP]). */
    val top: List<CategoryAmount>,
    /** The category whose spending moved most from the month before; null without a month before to compare. */
    val change: CategoryChange?,
    /**
     * Set for a month still running: [change] compares it with the month before up to this day (critic r1, P1-5), never
     * with all of that month. Null when two whole months are compared.
     */
    val changeThrough: LocalDate? = null,
    /** Net worth at the month's end (today's for the current month); null without wallets in this currency. */
    val netWorth: Money?,
    /** Net worth's change since the month before ended; null when nothing existed then to compare with. */
    val netWorthChange: Money?,
    /** Money set aside for goals less money taken back; null when Goals is off or no goal is in this currency. */
    val setAside: Money?,
    /** Goals that reached their target during the month. */
    val goalsReached: Int,
    /** Payments into cards and loans; null when Debts is off or there are none in this currency. */
    val paidToDebts: Money?,
    /** Days with income or expenses recorded (any currency). */
    val daysRecorded: Int,
    /** The month's days so far, today included (its length once finished). */
    val days: Int,
) {
    /** Income minus expenses: what was kept, negative when more went out than came in. */
    val net: Money get() = income - spent
    val hasRecords: Boolean get() = daysRecorded > 0
}

/** One month in the year view: what was kept and, for a finished month with a budget, whether it stayed within it. */
data class RecapMonthResult(
    val month: YearMonth,
    val net: Money,
    val hasRecords: Boolean,
    val finished: Boolean,
    /** Null for the current month, a month without records, or one without a budget. */
    val budgetKept: Boolean?,
    /**
     * The month's budget against what was spent (so far, for the current month): the colour and the percentage of its
     * square on the Milestones page (owner, 2026-11-23). Null with Budgets off, without a budget that month, without
     * records, or for a month still to come.
     */
    val budget: MonthBudgetResult? = null,
    /** After today's month: nothing to tell yet (an empty square). */
    val upcoming: Boolean = false,
)

/**
 * A month's budget result for the year's squares, from the Budget tab's own plan ([BudgetPlanner.plan]): the share used
 * the way the Budget tab rounds it ([Insights.percentOf], half-up) and its level ([BudgetPlanner.health]: on track,
 * getting close from 80 %, over from 100 %), so a square always says what the Budget tab says about that month.
 */
data class MonthBudgetResult(val total: Money, val spent: Money) {
    val remaining: Money get() = total - spent
    val percentUsed: Int get() = Insights.percentOf(spent, total) ?: 0
    val health: BudgetHealth get() = BudgetPlanner.health(total, spent)
}

/**
 * A calendar year's 12 months, January first, with their totals up to [endMonth] (December, or today's month for this
 * year); months after today's are [RecapMonthResult.upcoming] (docs/CALCULATIONS.md "Milestones: the budget year").
 */
data class YearRecap(
    val endMonth: YearMonth,
    val currencyCode: String,
    val months: List<RecapMonthResult>,
    val income: Money,
    val spent: Money,
    /** Net worth's change from the end of the month before the first one to the end of [endMonth]; null when unknown. */
    val netWorthChange: Money?,
) {
    val net: Money get() = income - spent
    val recordedMonths: Int get() = months.count { it.hasRecords }
    /** The first month with records; the year is told from there when it began later than the window (critic r1, P2-8). */
    val firstRecordedMonth: YearMonth? get() = months.firstOrNull { it.hasRecords }?.month
    /** Months with records where at least as much came in as went out. */
    val keptMonths: Int get() = months.count { it.hasRecords && !it.net.isNegative }
    val budgetedMonths: Int get() = months.count { it.budgetKept != null }
    val withinBudgetMonths: Int get() = months.count { it.budgetKept == true }
}

/** Everything the recaps are worked out from; the translated ledger's wallets and movements (docs "Exchange rates"). */
data class RecapInputs(
    /** Wallets at their carrying value in the main currency where they have a rate (the translated ledger's). */
    val wallets: List<Wallet>,
    /** The translated ledger's movements: spending in other currencies at its own value, FX results left out. */
    val transactions: List<Transaction>,
    /** The same with FX results, which balances are rebuilt from (so past balances land on carrying values). */
    val withFxResults: List<Transaction>,
    val categories: List<Category>,
    val budgets: List<Budget>,
    val goals: List<Goal>,
    val contributions: List<GoalContribution>,
    val features: FeatureSet,
    val today: LocalDate,
    val zone: ZoneId = ZoneId.systemDefault(),
)

object MonthRecaps {
    /** How many of the month's largest categories the recap names. */
    const val TOP = 3

    /** The months the year view covers. */
    const val YEAR = 12

    fun month(inputs: RecapInputs, currency: String, month: YearMonth): MonthRecap {
        val today = inputs.today
        val current = YearMonth.from(today)
        val range = DateRange(month.atDay(1), month.atEndOfMonth())
        val flow = CashflowCalculator.netFlow(currency, month, inputs.transactions)

        val breakdown = Insights.categoryBreakdown(inputs.transactions, currency, range)
        val top = breakdown.take(TOP).map { CategoryAmount(it.categoryId, it.amount, Insights.percentOf(it.amount, flow.expense)) }

        val previous = month.minusMonths(1)
        // A month still running is compared with the same days of the month before ("so far"), never with all of it.
        val sameDays = month == current
        val previousEnd = if (sameDays) previous.atDay(minOf(today.dayOfMonth, previous.lengthOfMonth())) else previous.atEndOfMonth()
        val previousRange = DateRange(previous.atDay(1), previousEnd)
        val previousHasRecords = inputs.transactions.any { it.type.isCashFlow && it.date in previousRange }
        val change = if (!previousHasRecords) null else biggestChange(
            breakdown,
            Insights.categoryBreakdown(inputs.transactions, currency, previousRange),
            currency,
        )

        val budget = if (inputs.features.isEnabled(Feature.BUDGETS)) budgetOf(inputs, currency, month) else null

        val history = if (inputs.wallets.none { it.currencyCode == currency }) emptyList()
        else BalanceHistory.lastMonths(inputs.wallets, inputs.withFxResults, currency, month, today, count = 2, zone = inputs.zone)
        val end = history.lastOrNull()?.takeIf { it.month == month }?.netWorth
        val netWorthChange = if (history.size == 2 && end != null) end - history.first().netWorth else null

        val goals = if (inputs.features.isEnabled(Feature.SAVINGS_GOALS)) inputs.goals.filter { it.currencyCode == currency } else emptyList()
        val goalIds = goals.map { it.id }.toSet()
        val goalMoves = inputs.contributions.filter { it.goalId in goalIds && it.amount.currencyCode == currency }
        val setAside = if (goals.isEmpty()) null else Money.sum(currency, goalMoves.filter { it.date in range }.map { it.amount })
        val reached = goals.count { goal ->
            val own = goalMoves.filter { it.goalId == goal.id }
            val before = Money.sum(currency, own.filter { it.date.isBefore(range.start) }.map { it.amount })
            val after = Money.sum(currency, own.filter { !it.date.isAfter(range.endInclusive) }.map { it.amount })
            before < goal.target && after >= goal.target
        }

        val debts = if (inputs.features.isEnabled(Feature.DEBTS_LOANS)) {
            inputs.wallets.filter { it.type.isLiability && it.currencyCode == currency }
        } else {
            emptyList()
        }
        val debtIds = debts.map { it.id }.toSet()
        val paid = if (debts.isEmpty()) null else Money.sum(
            currency,
            inputs.transactions.filter { it.type == TransactionType.TRANSFER_IN && it.walletId in debtIds && it.currencyCode == currency && it.date in range }
                .map { it.amount },
        )

        val recordedDays = inputs.transactions.filter { it.type.isCashFlow && it.date in range }.map { it.date }.toSet()
        return MonthRecap(
            month = month,
            currencyCode = currency,
            finished = month < current,
            income = flow.income,
            spent = flow.expense,
            budget = budget,
            top = top,
            change = change,
            changeThrough = if (sameDays && change != null) previousEnd else null,
            netWorth = end,
            netWorthChange = netWorthChange,
            setAside = setAside,
            goalsReached = reached,
            paidToDebts = paid,
            daysRecorded = recordedDays.size,
            days = when {
                month < current -> month.lengthOfMonth()
                month == current -> today.dayOfMonth
                else -> 0
            },
        )
    }

    /** The [YEAR] months up to [endMonth] (never past today's month), oldest first. */
    /**
     * The calendar [year] for the Milestones page's budget squares (owner, 2026-11-23: "This calendar year"): January to
     * December, each month with what was kept and its budget result ([MonthBudgetResult], so far for today's month);
     * months after today's are [RecapMonthResult.upcoming] and add nothing. Totals and net worth's change run from January
     * to December, or to today's month in this year. A year after today's is all upcoming.
     */
    fun calendarYear(inputs: RecapInputs, currency: String, year: Int): YearRecap {
        val current = YearMonth.from(inputs.today)
        val budgetsOn = inputs.features.isEnabled(Feature.BUDGETS)
        val january = YearMonth.of(year, 1)
        val end = minOf(YearMonth.of(year, 12), current)
        val flows = (1..YEAR).map { YearMonth.of(year, it) }.filter { it <= current }
            .associateWith { CashflowCalculator.netFlow(currency, it, inputs.transactions) }
        val months = (1..YEAR).map { number ->
            val month = YearMonth.of(year, number)
            val flow = flows[month]
                ?: return@map RecapMonthResult(month, Money.zero(currency), hasRecords = false, finished = false, budgetKept = null, upcoming = true)
            val records = MonthCheckIn.hasRecords(inputs.transactions, month)
            val finished = month < current
            // A month without records has no result to show: a budget nobody recorded against would read as 0 % used.
            val budget = if (!budgetsOn || !records) null else {
                val plan = BudgetPlanner.plan(inputs.categories, inputs.budgets, inputs.transactions, currency, month)
                if (plan.hasBudget) MonthBudgetResult(plan.total, plan.spent) else null
            }
            val kept = budget?.takeIf { finished }?.let { !it.remaining.isNegative }
            RecapMonthResult(month, flow.net, records, finished, kept, budget)
        }
        val income = Money.sum(currency, flows.values.map { it.income })
        val spent = Money.sum(currency, flows.values.map { it.expense })
        val netWorthChange = if (end < january || inputs.wallets.none { it.currencyCode == currency }) null else {
            val before = BalanceHistory.lastMonths(inputs.wallets, inputs.withFxResults, currency, january.minusMonths(1), inputs.today, count = 1, zone = inputs.zone)
            val after = BalanceHistory.lastMonths(inputs.wallets, inputs.withFxResults, currency, end, inputs.today, count = 1, zone = inputs.zone)
            if (before.isEmpty() || after.isEmpty()) null else after.first().netWorth - before.first().netWorth
        }
        return YearRecap(end, currency, months, income, spent, netWorthChange)
    }

    /** The month's budget from the Budget tab's own plan. Null without a budget that month. */
    fun budgetOf(inputs: RecapInputs, currency: String, month: YearMonth): RecapBudget? {
        val plan = BudgetPlanner.plan(inputs.categories, inputs.budgets, inputs.transactions, currency, month)
        if (!plan.hasBudget) return null
        val allocated = plan.allocatedLines
        val over = allocated.filter { it.remaining?.isNegative == true }
            .map { CategoryAmount(it.category.id, -(it.remaining!!)) }
            .sortedWith(compareByDescending<CategoryAmount> { it.amount.amountMinor }.thenBy { it.categoryId })
        val rows = inputs.budgets.filter { !it.isLegacyOverall && it.currencyCode == currency }
        val since = rows.takeIf { r -> r.none { it.createdOn == null } }
            ?.mapNotNull { it.createdOn }?.minOrNull()
            ?.takeIf { it.isAfter(month.atDay(1)) }
        return RecapBudget(
            total = plan.total,
            spent = plan.spent,
            budgeted = allocated.size,
            within = allocated.size - over.size,
            over = over,
            since = since,
        )
    }

    /**
     * The category whose spending moved most between two months (by the size of the change), or null when nothing moved.
     * Ties go to the larger month's category, then the category id, so the answer is stable.
     */
    fun biggestChange(current: List<CategoryShare>, previous: List<CategoryShare>, currency: String): CategoryChange? {
        val now = current.associate { it.categoryId to it.amount }
        val before = previous.associate { it.categoryId to it.amount }
        val zero = Money.zero(currency)
        return (now.keys + before.keys)
            .map { id -> CategoryChange(id, now[id] ?: zero, before[id] ?: zero) }
            .filter { !it.change.isZero }
            .sortedWith(
                compareByDescending<CategoryChange> { it.change.abs().amountMinor }
                    .thenByDescending { it.current.amountMinor }
                    .thenBy { it.categoryId ?: Long.MAX_VALUE },
            )
            .firstOrNull()
    }
}
