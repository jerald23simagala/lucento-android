package com.lucento.app.domain.ask

import com.lucento.app.core.money.Money
import com.lucento.app.domain.BalanceHistory
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.Category
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.DateRange
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.Insights
import com.lucento.app.domain.LoanMath
import com.lucento.app.domain.RecurringMath
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The reports Ask Lucento can prepare. Every figure in them is calculated on the phone from the user's records. */
enum class ReportKind(val title: String) {
    NET_WORTH("Net worth statement"),
    CASH_FLOW("Cash flow statement"),
    BUDGET_VS_ACTUAL("Budget vs actual"),
    DEBTS("Debts & loans summary"),
    TOP_CATEGORIES("Top spending categories"),
}

/**
 * One row of a report: a [label] (a wallet, a category), an optional [detail] under it, and its figures — one per
 * column of its section ([ReportSection.columns]); null leaves a cell empty. [emphasis] marks totals.
 */
data class ReportLine(
    val label: String,
    val values: List<Money?> = emptyList(),
    val detail: String? = null,
    val emphasis: Boolean = false,
)

data class ReportSection(
    val title: String,
    val columns: List<String>,
    val lines: List<ReportLine>,
    val total: ReportLine? = null,
    /** Shown in place of the lines when there are none. */
    val emptyText: String = "Nothing here.",
)

/**
 * A finished report in one currency: the [headline] figure, its sections and the notes that say how it was made. The
 * same object is drawn on screen and written to the PDF, so the two can never disagree.
 */
data class Report(
    val kind: ReportKind,
    val currencyCode: String,
    /** "As of November 3, 2026", "November 2026". */
    val period: String,
    val headline: ReportLine,
    val sections: List<ReportSection>,
    val notes: List<String>,
    /** The month it covers (a net worth or debts statement: the month it is as of). */
    val month: YearMonth,
    /** The period as it reads inside a sentence: "November 2026 so far", "October 2026", "as of November 3, 2026". */
    val phrase: String = period,
) {
    val title: String get() = kind.title
}

/** Everything a report is calculated from; all of it already on the phone. */
data class ReportInputs(
    val wallets: List<Wallet>,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val budgets: List<Budget>,
    val schedules: List<RecurringSchedule>,
    val today: LocalDate,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** Active savings goals and their contributions (for Ask Lucento's AI; the reports don't use them). */
    val goals: List<com.lucento.app.domain.Goal> = emptyList(),
    val contributions: List<com.lucento.app.domain.GoalContribution> = emptyList(),
)

/**
 * Builds the preset reports (docs/CALCULATIONS.md "Ask Lucento reports"). Pure: each one reuses the calculator the rest
 * of the app uses for the same figure — [BalanceHistory] / net worth, [BudgetPlanner], [Insights], [LoanMath],
 * [CreditCardMath] — so a report always matches Home, Budget, Wallets and Insights. Currencies are never converted:
 * a report is in one currency and names the others it leaves out.
 */
object ReportBuilder {

    fun build(kind: ReportKind, inputs: ReportInputs, currency: String, month: YearMonth, top: Int = 5): Report = when (kind) {
        ReportKind.NET_WORTH -> netWorth(inputs, currency, month)
        ReportKind.CASH_FLOW -> cashFlow(inputs, currency, month)
        ReportKind.BUDGET_VS_ACTUAL -> budgetVsActual(inputs, currency, month)
        ReportKind.DEBTS -> debts(inputs, currency)
        ReportKind.TOP_CATEGORIES -> topCategories(inputs, currency, month, top)
    }

    /**
     * Assets and liabilities wallet by wallet at the end of [month] (today, for the current month), net worth, and its
     * change since the end of the month before. Balances are rebuilt exactly as Insights' net worth history does.
     */
    fun netWorth(inputs: ReportInputs, currency: String, month: YearMonth): Report {
        val current = YearMonth.from(inputs.today)
        val asOf = if (month >= current) inputs.today else month.atEndOfMonth()
        val wallets = walletsAt(inputs, asOf).filter { it.currencyCode == currency }
        // Before any wallet in this currency existed there is nothing to state: say so rather than show zeros.
        val none = "No $currency wallets existed in Lucento on ${longDate(asOf)}."
        val (owed, owned) = wallets.partition { it.type.isLiability }
        fun lines(list: List<Wallet>) = list.sortedByDescending { it.balance.amountMinor }.map { ReportLine(it.name, listOf(it.balance), kindOf(it.type)) }
        val totalAssets = Money.sum(currency, owned.map { it.balance })
        val totalOwed = Money.sum(currency, owed.map { it.balance })
        val net = totalAssets - totalOwed
        val previousEnd = month.minusMonths(1).atEndOfMonth()
        val before = walletsAt(inputs, previousEnd).filter { it.currencyCode == currency }
        val change = if (before.isEmpty()) null else net - Money.sum(currency, before.map { it.netWorthContribution })
        return Report(
            kind = ReportKind.NET_WORTH,
            currencyCode = currency,
            period = "As of ${longDate(asOf)}",
            phrase = "as of ${longDate(asOf)}",
            headline = ReportLine(
                "Net worth",
                listOf(net),
                if (wallets.isEmpty()) none else change?.let { "${Figures.signed(it)} since ${longDate(previousEnd)}" },
                emphasis = true,
            ),
            sections = if (wallets.isEmpty()) listOf(ReportSection("Wallets", listOf("Balance"), emptyList(), emptyText = none)) else listOf(
                ReportSection("Assets", listOf("Balance"), lines(owned), ReportLine("Total assets", listOf(totalAssets), emphasis = true), "No assets in $currency."),
                ReportSection("Liabilities", listOf("Owed"), lines(owed), ReportLine("Total owed", listOf(totalOwed), emphasis = true), "Nothing owed in $currency."),
                ReportSection(
                    "Net worth",
                    listOf("Amount"),
                    listOfNotNull(
                        ReportLine("Assets", listOf(totalAssets)),
                        ReportLine("Less liabilities", listOf(-totalOwed)),
                        change?.let { ReportLine("Change since ${longDate(previousEnd)}", listOf(it)) },
                    ),
                    ReportLine("Net worth", listOf(net), emphasis = true),
                ),
            ),
            notes = listOfNotNull(
                "Balances as recorded in Lucento on this phone. Net worth = assets − liabilities.",
                "Savings goals aren’t added: their money is already in these balances.",
                otherCurrenciesNote(inputs.wallets.map { it.currencyCode }, currency),
            ),
            month = YearMonth.from(asOf),
        )
    }

    /**
     * Income and expenses by category for [month], their net flow, and — apart, because they move money between the
     * user's own wallets and never change the net — transfers and balance adjustments.
     */
    fun cashFlow(inputs: ReportInputs, currency: String, month: YearMonth): Report {
        val range = monthRange(month, inputs.today)
        val names = inputs.categories.associate { it.id to it.name }
        fun byCategory(type: TransactionType): Pair<List<ReportLine>, Money> {
            val shares = Insights.categoryBreakdown(inputs.transactions, currency, range, type)
            val total = Money.sum(currency, shares.map { it.amount })
            return shares.map { s ->
                ReportLine(
                    s.categoryId?.let(names::get) ?: "Uncategorized",
                    listOf(s.amount),
                    Insights.percentOf(s.amount, total)?.let { "$it% of ${if (type == TransactionType.INCOME) "income" else "expenses"}" },
                )
            } to total
        }
        val (income, totalIncome) = byCategory(TransactionType.INCOME)
        val (expenses, totalExpenses) = byCategory(TransactionType.EXPENSE)
        val net = totalIncome - totalExpenses
        val inMonth = inputs.transactions.filter { it.currencyCode == currency && it.date in range }
        val transfers = Money.sum(currency, inMonth.filter { it.type == TransactionType.TRANSFER_OUT }.map { it.amount })
        val adjustments = Money.sum(currency, inMonth.filter { it.type.isAdjustment }.map { it.signedAmount })
        return Report(
            kind = ReportKind.CASH_FLOW,
            currencyCode = currency,
            period = periodLabel(month, inputs.today),
            phrase = periodPhrase(month, inputs.today),
            headline = ReportLine(if (net.isNegative) "Net outflow" else "Net inflow", listOf(net), "Income ${Figures.plain(totalIncome)} · expenses ${Figures.plain(totalExpenses)}", emphasis = true),
            sections = listOf(
                ReportSection("Income", listOf("Amount"), income, ReportLine("Total income", listOf(totalIncome), emphasis = true), "No income recorded."),
                ReportSection("Expenses", listOf("Amount"), expenses, ReportLine("Total expenses", listOf(totalExpenses), emphasis = true), "No expenses recorded."),
                ReportSection(
                    "Net flow",
                    listOf("Amount"),
                    listOf(ReportLine("Income", listOf(totalIncome)), ReportLine("Less expenses", listOf(-totalExpenses))),
                    ReportLine("Net flow", listOf(net), emphasis = true),
                ),
                ReportSection(
                    "Not in net flow",
                    listOf("Amount"),
                    listOfNotNull(
                        ReportLine("Transfers between your wallets", listOf(transfers), "Card and loan payments included: they move your own money"),
                        adjustments.takeIf { !it.isZero }?.let { ReportLine("Balance adjustments", listOf(it), "Corrections to match real balances") },
                    ),
                ),
            ),
            notes = listOfNotNull(
                "Recorded income and expenses in $currency, ${rangeLabel(range)}. Net flow = income − expenses.",
                otherCurrenciesNote(inputs.transactions.filter { it.type.isCashFlow && it.date in range }.map { it.currencyCode }, currency),
            ),
            month = month,
        )
    }

    /** Each budgeted category's budget, spending and what's left for [month] — the Budget screen's own plan. */
    fun budgetVsActual(inputs: ReportInputs, currency: String, month: YearMonth): Report {
        val plan = BudgetPlanner.plan(inputs.categories, inputs.budgets, inputs.transactions, currency, month)
        val zero = Money.zero(currency)
        val allocated = plan.allocatedLines.map { line ->
            ReportLine(
                line.category.name,
                listOf(line.limit, line.spent, line.remaining),
                line.percentUsed?.let { "$it% used" + if ((line.remaining ?: zero).isNegative) " · over budget" else "" },
            )
        }
        val unbudgeted = plan.unallocatedLines.filter { !it.spent.isZero }.map { ReportLine(it.category.name, listOf(null, it.spent, null)) } +
            listOfNotNull(plan.uncategorizedSpending.takeIf { !it.isZero }?.let { ReportLine("Uncategorized", listOf(null, it, null)) })
        return Report(
            kind = ReportKind.BUDGET_VS_ACTUAL,
            currencyCode = currency,
            period = periodLabel(month, inputs.today),
            phrase = periodPhrase(month, inputs.today),
            headline = ReportLine(
                if (plan.remaining.isNegative) "Over budget" else "Left to spend",
                listOf(plan.remaining),
                if (plan.hasBudget) "Budget ${Figures.plain(plan.total)} · spent ${Figures.plain(plan.spent)}" + (plan.percentUsed?.let { " · $it% used" } ?: "") else "No budget set for this month",
                emphasis = true,
            ),
            sections = listOf(
                ReportSection(
                    "Budgeted categories",
                    listOf("Budget", "Spent", "Left"),
                    allocated,
                    ReportLine("Budgeted categories", listOf(plan.total, plan.budgetedSpending, plan.total - plan.budgetedSpending), emphasis = true),
                    "No category has a budget for this month.",
                ),
                ReportSection(
                    "Spending without a budget",
                    listOf("Budget", "Spent", "Left"),
                    unbudgeted,
                    ReportLine("Without a budget", listOf(null, plan.unbudgetedSpending, null), emphasis = true).takeIf { unbudgeted.isNotEmpty() },
                    "All spending was in budgeted categories.",
                ),
                ReportSection(
                    "Month",
                    listOf("Budget", "Spent", "Left"),
                    emptyList(),
                    ReportLine("Total", listOf(plan.total, plan.spent, plan.remaining), emphasis = true),
                ),
            ),
            notes = listOf(
                "The month’s budget is the sum of its category budgets. Spent counts every expense in $currency that month, budgeted or not.",
                "Transfers, income and goal contributions are never spending.",
            ),
            month = month,
        )
    }

    /** Every card and loan in [currency]: what is owed, the rate, a loan's progress, a card's amount due, the next payment. */
    fun debts(inputs: ReportInputs, currency: String): Report {
        val today = inputs.today
        val debts = inputs.wallets.filter { it.type.isLiability && it.currencyCode == currency }.sortedByDescending { it.balance.amountMinor }
        fun line(wallet: Wallet): ReportLine {
            val parts = buildList {
                add(kindOf(wallet.type))
                wallet.debt?.interestRateBps?.let { add("${DebtMath.formatRate(it)} a year") }
                LoanMath.progress(wallet, inputs.transactions)?.let { add("${it.installmentsPaid} of ${it.installments} paid") }
                CreditCardMath.cycle(wallet, inputs.transactions, today)?.takeIf { !it.amountDue.isZero }?.let {
                    add("${Figures.plain(it.amountDue)} due ${shortDate(it.dueDate)}")
                }
                nextPayment(wallet, inputs.schedules, today)?.let { (date, amount) -> add("next payment ${Figures.plain(amount)} on ${shortDate(date)}") }
            }
            return ReportLine(wallet.name, listOf(wallet.balance), parts.joinToString(" · "))
        }
        val total = Money.sum(currency, debts.map { it.balance })
        val loans = debts.filter { it.type == WalletType.LOAN }
        val cards = debts.filter { it.type == WalletType.CREDIT_CARD }
        return Report(
            kind = ReportKind.DEBTS,
            currencyCode = currency,
            period = "As of ${longDate(today)}",
            phrase = "as of ${longDate(today)}",
            headline = ReportLine("Total owed", listOf(total), if (debts.size == 1) "1 card or loan" else "${debts.size} cards and loans", emphasis = true),
            sections = listOf(
                ReportSection("Loans", listOf("Owed"), loans.map(::line), ReportLine("Loans", listOf(Money.sum(currency, loans.map { it.balance })), emphasis = true).takeIf { loans.isNotEmpty() }, "No loans in $currency."),
                ReportSection("Credit cards", listOf("Owed"), cards.map(::line), ReportLine("Cards", listOf(Money.sum(currency, cards.map { it.balance })), emphasis = true).takeIf { cards.isNotEmpty() }, "No credit cards in $currency."),
            ),
            notes = listOfNotNull(
                "Amounts owed as recorded in Lucento. Rates are the ones you entered; Lucento never fetches them.",
                "A card’s amount due is what you owed on its statement day, less payments since.",
                otherCurrenciesNote(inputs.wallets.filter { it.type.isLiability }.map { it.currencyCode }, currency),
            ),
            month = YearMonth.from(today),
        )
    }

    /** The [top] expense categories of [month] with their share of all spending. */
    fun topCategories(inputs: ReportInputs, currency: String, month: YearMonth, top: Int = 5): Report {
        val range = monthRange(month, inputs.today)
        val names = inputs.categories.associate { it.id to it.name }
        val shares = Insights.categoryBreakdown(inputs.transactions, currency, range)
        val total = Money.sum(currency, shares.map { it.amount })
        val count = top.coerceIn(1, 20)
        val lines = shares.take(count).mapIndexed { i, s ->
            ReportLine("${i + 1}. ${s.categoryId?.let(names::get) ?: "Uncategorized"}", listOf(s.amount), Insights.percentOf(s.amount, total)?.let { "$it% of spending" })
        }
        val rest = Money.sum(currency, shares.drop(count).map { it.amount })
        return Report(
            kind = ReportKind.TOP_CATEGORIES,
            currencyCode = currency,
            period = periodLabel(month, inputs.today),
            phrase = periodPhrase(month, inputs.today),
            headline = ReportLine("Spent", listOf(total), if (shares.size == 1) "1 category" else "${shares.size} categories", emphasis = true),
            sections = listOf(
                ReportSection(
                    "Top ${lines.size.coerceAtLeast(1)}",
                    listOf("Spent"),
                    lines + listOfNotNull(rest.takeIf { !it.isZero }?.let { ReportLine("Everything else", listOf(it), Insights.percentOf(it, total)?.let { p -> "$p% of spending" }) }),
                    ReportLine("All spending", listOf(total), emphasis = true).takeIf { lines.isNotEmpty() },
                    "No spending recorded.",
                ),
            ),
            notes = listOf("Expenses in $currency, ${rangeLabel(range)}. Transfers and balance adjustments aren’t spending."),
            month = month,
        )
    }

    /** The month so far for the current month, the whole month otherwise. */
    fun monthRange(month: YearMonth, today: LocalDate): DateRange =
        DateRange(month.atDay(1), if (month == YearMonth.from(today)) today.coerceAtLeast(month.atDay(1)) else month.atEndOfMonth())

    private fun walletsAt(inputs: ReportInputs, date: LocalDate): List<Wallet> =
        if (!date.isBefore(inputs.today)) inputs.wallets else BalanceHistory.walletsAt(inputs.wallets, inputs.transactions, date, inputs.zone)

    /** The next unpaid occurrence of an active schedule that pays [wallet] off, and its full amount. */
    private fun nextPayment(wallet: Wallet, schedules: List<RecurringSchedule>, today: LocalDate): Pair<LocalDate, Money>? =
        schedules.filter { it.toWalletId == wallet.id && !it.paused }
            .mapNotNull { s -> RecurringMath.occurrence(s, s.nextIndex)?.takeIf { s.endDate == null || !it.isAfter(s.endDate) }?.let { it to s.total } }
            .minByOrNull { it.first }
            ?.takeIf { !it.first.isBefore(today.minusYears(1)) }

    private fun otherCurrenciesNote(codes: List<String>, currency: String): String? =
        codes.filter { it != currency }.distinct().sorted().takeIf { it.isNotEmpty() }
            ?.let { "Also recorded in ${it.joinToString()}: not included and never converted." }

    internal fun kindOf(type: WalletType): String = when (type) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank account"
        WalletType.E_WALLET -> "E-wallet"
        WalletType.SAVINGS -> "Savings"
        WalletType.CREDIT_CARD -> "Credit card"
        WalletType.LOAN -> "Loan"
        WalletType.RECEIVABLE -> "Receivable"
        WalletType.OTHER_ASSET -> "Other asset"
    }

    private fun periodLabel(month: YearMonth, today: LocalDate): String =
        if (month == YearMonth.from(today)) "${monthName(month)} · to ${shortDate(today)}" else monthName(month)

    private fun periodPhrase(month: YearMonth, today: LocalDate): String =
        if (month == YearMonth.from(today)) "${monthName(month)} so far" else monthName(month)

    private fun rangeLabel(range: DateRange): String = "${longDate(range.start)} to ${longDate(range.endInclusive)}"
}
