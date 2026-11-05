package com.lucento.app.domain.ask

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.GoalCalculator
import com.lucento.app.domain.Insights
import com.lucento.app.domain.LoanMath
import com.lucento.app.domain.ScheduleOutlook
import com.lucento.app.domain.ScheduledFlow
import com.lucento.app.domain.ScheduledItem
import com.lucento.app.domain.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** How figures and dates read in Ask Lucento's answers and reports (the reader's locale). */
object Figures {
    fun plain(money: Money): String = MoneyFormatter.format(money)
    fun signed(money: Money): String = MoneyFormatter.formatSigned(money, showPlus = true)
}

internal fun longDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)

internal fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofPattern("MMM d", locale))

internal fun monthName(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " ${month.year}"

/** A question Ask Lucento can answer on the phone: which report, for which month, how many rows. */
data class AskRoute(val kind: ReportKind, val month: YearMonth, val top: Int = 5)

/**
 * Reads a question for one of the preset reports, on the phone and without AI: plain keyword rules ("top 5 spending
 * categories", "net worth", "budget last month"). Null when the question is about something else — then only the AI
 * (when the user turned it on) can answer it. Never presented as AI.
 */
object AskRouter {

    fun route(question: String, today: LocalDate): AskRoute? {
        val q = " " + question.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ") + " "
        val kind = when {
            q.has("categor") || q.has("where does my money go") || q.has("where did my money go") || q.has("spend the most") ||
                q.has("biggest expense") || q.has("top spending") -> ReportKind.TOP_CATEGORIES
            q.has("net worth") || q.has("networth") || q.has(" worth ") || q.has("balance sheet") || q.has(" assets") -> ReportKind.NET_WORTH
            q.has("budget") -> ReportKind.BUDGET_VS_ACTUAL
            q.has(" debt") || q.has(" loan") || q.has("credit card") || q.has(" owe ") || q.has(" cards ") -> ReportKind.DEBTS
            q.has("cash flow") || q.has("cashflow") || q.has(" income") || q.has(" earn") || q.has(" spent") || q.has(" spend") ||
                q.has(" expenses") || q.has("net flow") || q.has(" saved") || q.has(" save ") || q.has(" saving") -> ReportKind.CASH_FLOW
            else -> return null
        }
        val top = Regex(" (\\d{1,2}) ").find(q)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..20 } ?: 5
        return AskRoute(kind, monthIn(q, today), top)
    }

    /** "last month", "previous month", a month's name (this year, or last year when that month is still ahead). */
    internal fun monthIn(q: String, today: LocalDate): YearMonth {
        val current = YearMonth.from(today)
        if (q.has("last month") || q.has("previous month") || q.has("past month")) return current.minusMonths(1)
        val named = Month.entries.firstOrNull { m ->
            val full = m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT)
            when {
                // "May I…" isn't a month: May counts only after "in", "for", "of" or "since".
                m == Month.MAY -> listOf(" in may ", " for may ", " of may ", " since may ").any { q.has(it) }
                else -> q.has(" $full ") || q.has(" ${full.take(3)} ")
            }
        } ?: return current
        val thisYear = YearMonth.of(today.year, named)
        return if (thisYear > current) thisYear.minusYears(1) else thisYear
    }

    private fun String.has(word: String) = contains(word)
}

/** One-sentence answers for reports calculated on the phone; the report card beneath holds every figure. */
object LocalAnswers {

    fun summary(report: Report): String {
        val head = report.headline.values.firstOrNull()
        val zero = Money.zero(report.currencyCode)
        val figure = Figures.plain(head ?: zero)
        fun total(title: String) = report.sections.firstOrNull { it.title == title }?.total?.values?.firstOrNull() ?: zero
        return when (report.kind) {
            ReportKind.TOP_CATEGORIES -> {
                val rows = report.sections.firstOrNull()?.lines.orEmpty().filterNot { it.label == "Everything else" }
                val first = rows.firstOrNull()
                if (first == null) {
                    "No spending is recorded in ${report.currencyCode} for ${report.phrase}."
                } else {
                    "${first.label.substringAfter(". ")} leads your spending for ${report.phrase} at ${Figures.plain(first.values.first() ?: zero)}" +
                        (first.detail?.let { " (${it.substringBefore(" ")})" } ?: "") +
                        ". Your top ${rows.size} ${if (rows.size == 1) "is" else "are"} below — $figure spent in all."
                }
            }
            ReportKind.NET_WORTH -> "Your net worth is $figure ${report.phrase}" + (report.headline.detail?.let { ", $it." } ?: ".")
            ReportKind.CASH_FLOW -> "In ${report.phrase}, ${Figures.plain(total("Income"))} came in and ${Figures.plain(total("Expenses"))} went out: " +
                "a ${report.headline.label.lowercase(Locale.getDefault())} of ${Figures.signed(head ?: zero)}."
            ReportKind.BUDGET_VS_ACTUAL -> if (report.headline.detail == "No budget set for this month") {
                "There’s no budget for ${report.phrase} yet. Set category budgets on the Budget tab and I’ll compare them with your spending."
            } else {
                "For ${report.phrase}: ${report.headline.detail}. " +
                    if (head != null && head.isNegative) "You’re ${Figures.plain(-head)} over." else "$figure left to spend."
            }
            ReportKind.DEBTS -> if (head == null || head.isZero) "You don’t owe anything on cards or loans in ${report.currencyCode}."
            else "You owe $figure across ${report.headline.detail}."
        }
    }

    /** Questions for the AI, offered as chips while AI answers are on: plans and decisions, projections, transactions. */
    val adviceSuggestions: List<String> = listOf(
        "Make me a plan to save more each month",
        "Will I stay within budget this month?",
        "Which debt should I pay off first?",
        "When will I reach my goals at this pace?",
        "Which subscriptions should I cut?",
        "Anything unusual in my spending lately?",
    )

    /** The preset questions offered as chips; each is answered on the phone. */
    val suggestions: List<String> = listOf(
        "What are my top 5 spending categories?",
        "What’s my net worth?",
        "How’s my cash flow this month?",
        "Am I on budget this month?",
        "How are my debts doing?",
    )
}

/**
 * One figure the AI may quote: it writes `[[id]]` and the app puts in [value], calculated on the phone. [amount] (an
 * amount of money) or [number] (a count, a percentage, days) lets the AI calculate with it in `[[= … ]]` ([AskCalc]);
 * neither is sent — only the id, the label and the value as text.
 */
data class SnapshotFigure(
    val id: String,
    val label: String,
    val value: String,
    val amount: Money? = null,
    val number: BigDecimal? = null,
)

/**
 * What Ask Lucento's AI sees (docs/ASK.md), every figure calculated on the phone and labelled, with an id:
 * - summaries: net worth and balances, income, expenses and net flow by month, spending and income by category, the
 *   budget, debts and what's still scheduled;
 * - projections: this month's pace and projected spending, safe to spend, averages over the last full months, the share
 *   of income kept, money on hand;
 * - goals: target, saved, still to save, the monthly amount needed and the projected date;
 * - debts in depth: what's owed, monthly interest at the rate, instalments left;
 * - transaction patterns: top payees, monthly repeats, unusual and possibly duplicated charges;
 * - the last [AskSnapshotBuilder.TRANSACTION_DAYS] days of transactions, newest first, while there is room.
 * Owner, 2026-11-04: transaction-level answers and advice, under their own consent (the v2 AI answers consent). Main
 * currency only; never receipts or photos. Wallet, category and payee names are the user's own words.
 */
data class AskSnapshot(
    val currency: String,
    val today: LocalDate,
    val figures: List<SnapshotFigure>,
) {
    val byId: Map<String, SnapshotFigure> = figures.associateBy { it.id }
}

object AskSnapshotBuilder {

    const val MAX_FIGURES = 400
    const val TRANSACTION_DAYS = 60L
    private const val MONTHS = 12
    private const val CATEGORY_ROWS = 15
    private const val AVERAGE_MONTHS = 3

    fun build(inputs: ReportInputs, currency: String): AskSnapshot {
        val today = inputs.today
        val current = YearMonth.from(today)
        val out = ArrayList<SnapshotFigure>()
        fun add(id: String, label: String, value: String, amount: Money? = null, number: BigDecimal? = null) {
            if (out.size < MAX_FIGURES) out.add(SnapshotFigure(id, label.take(100), value.take(40), amount, number))
        }
        fun money(id: String, label: String, m: Money) = add(id, label, Figures.plain(m), amount = m)
        fun count(id: String, label: String, n: Long) = add(id, label, n.toString(), number = BigDecimal.valueOf(n))
        fun percent(id: String, label: String, p: Int) = add(id, label, "$p%", number = BigDecimal.valueOf(p.toLong()))
        val names = inputs.categories.associate { it.id to it.name }
        val walletNames = inputs.wallets.associate { it.id to it.name }

        // Net worth and each wallet's balance.
        val worth = ReportBuilder.netWorth(inputs, currency, current)
        worth.headline.values.firstOrNull()?.let { money("nw", "Net worth today", it) }
        worth.sections.getOrNull(0)?.total?.values?.firstOrNull()?.let { money("nw_assets", "Total assets today", it) }
        worth.sections.getOrNull(1)?.total?.values?.firstOrNull()?.let { money("nw_owed", "Total owed today (cards and loans)", it) }
        worth.sections.getOrNull(2)?.lines?.firstOrNull { it.label.startsWith("Change since") }?.let { line ->
            line.values.firstOrNull()?.let { money("nw_change", "Net worth: ${line.label.lowercase(Locale.getDefault())}", it) }
        }
        inputs.wallets.filter { it.currencyCode == currency }.take(30).forEachIndexed { i, w ->
            money("w$i", "${w.name} (${ReportBuilder.kindOf(w.type)}) ${if (w.type.isLiability) "owed" else "balance"}", w.balance)
        }

        // Income, expenses and net flow by month.
        Insights.incomeExpenseTrend(inputs.transactions, currency, current, MONTHS).forEach { m ->
            val key = m.month.toString().replace("-", "")
            val name = monthName(m.month) + if (m.month == current) " (so far)" else ""
            money("in_$key", "Income, $name", m.income)
            money("out_$key", "Expenses, $name", m.expense)
            money("net_$key", "Net flow (income − expenses), $name", m.net)
        }

        // Spending and income by category, this month and last.
        listOf(0L, 1L).forEach { back ->
            val month = current.minusMonths(back)
            val range = ReportBuilder.monthRange(month, today)
            val name = monthName(month) + if (back == 0L) " (so far)" else ""
            Insights.categoryBreakdown(inputs.transactions, currency, range).take(CATEGORY_ROWS).forEachIndexed { i, s ->
                money("cat${back}_$i", "Spent on ${s.categoryId?.let(names::get) ?: "Uncategorized"}, $name", s.amount)
            }
            Insights.categoryBreakdown(inputs.transactions, currency, range, TransactionType.INCOME).take(8).forEachIndexed { i, s ->
                money("inc${back}_$i", "Income from ${s.categoryId?.let(names::get) ?: "Uncategorized"}, $name", s.amount)
            }
        }

        // This month's budget.
        val plan = BudgetPlanner.plan(inputs.categories, inputs.budgets, inputs.transactions, currency, current)
        if (plan.hasBudget) {
            money("bud_total", "Budget, ${monthName(current)}", plan.total)
            money("bud_spent", "Spent against the budget, ${monthName(current)} so far", plan.spent)
            money("bud_left", "Budget left, ${monthName(current)} (negative = over)", plan.remaining)
            plan.percentUsed?.let { percent("bud_pct", "Budget used, ${monthName(current)}", it) }
            plan.allocatedLines.take(20).forEachIndexed { i, line ->
                line.limit?.let { money("bud${i}_limit", "${line.category.name} budget", it) }
                money("bud${i}_spent", "${line.category.name} spent this month", line.spent)
                line.remaining?.let { money("bud${i}_left", "${line.category.name} budget left (negative = over)", it) }
            }
        }

        // Debts: what's owed, the rate and its monthly interest, a card's amount due, a loan's instalments.
        inputs.wallets.filter { it.type.isLiability && it.currencyCode == currency }.take(10).forEachIndexed { i, w ->
            money("debt${i}_owed", "${w.name} owed now", w.balance)
            w.debt?.interestRateBps?.let { bps ->
                add("debt${i}_rate", "${w.name} interest rate", "${DebtMath.formatRate(bps)} a year")
                money("debt${i}_int", "${w.name} interest a month on what's owed now, at that rate (estimate)", DebtMath.monthlyInterest(w.balance, bps))
            }
            CreditCardMath.cycle(w, inputs.transactions, today)?.takeIf { !it.amountDue.isZero }?.let {
                money("debt${i}_due", "${w.name} amount due by ${shortDate(it.dueDate)}", it.amountDue)
            }
            w.debt?.installment?.takeIf { it.currencyCode == currency }?.let { money("debt${i}_inst", "${w.name} monthly instalment", it) }
            LoanMath.progress(w, inputs.transactions)?.let { lp ->
                count("debt${i}_inst_left", "${w.name} instalments left", (lp.installments - lp.installmentsPaid).coerceAtLeast(0).toLong())
            }
        }

        // What's still scheduled this month (overdue included); transfers apart.
        val active = inputs.schedules.filter { !it.paused }
        val dues = CreditCardMath.dues(inputs.wallets, inputs.transactions, today, current.atEndOfMonth())
        val items = ScheduleOutlook.items(active, dues, today, today, current.atEndOfMonth())
        val totals = ScheduleOutlook.totals(items, currency)
        if (!totals.isEmpty) {
            money("sch_in", "Scheduled income still to come, ${monthName(current)}", totals.income)
            money("sch_out", "Scheduled bills still to pay, ${monthName(current)}", totals.expenses)
            money("sch_tr", "Scheduled card and loan payments (transfers), ${monthName(current)}", totals.transfers)
            items.filter { it.amount.currencyCode == currency }.take(8).forEachIndexed { i, item ->
                val what = when (item) {
                    is ScheduledItem.Occurrence -> item.schedule.name
                    is ScheduledItem.CardPayment -> "${item.due.wallet.name} payment"
                }
                val kind = when (item.flow) {
                    ScheduledFlow.INCOME -> "income"
                    ScheduledFlow.EXPENSE -> "bill"
                    ScheduledFlow.TRANSFER -> "transfer"
                }
                money("sch$i", "$what ($kind) ${if (item.overdue) "overdue since" else "due"} ${shortDate(item.date)}", item.amount)
            }
        }

        // Projections: this month's pace, safe to spend, averages over the last full months, money on hand.
        val burn = CashflowCalculator.burnRate(currency, today, inputs.transactions)
        money("pace_day", "Average daily spending, ${monthName(current)} so far", burn.dailyAverage)
        money("proj_out", "Projected spending for all of ${monthName(current)} at this pace (estimate)", burn.projectedMonthSpend)
        count("days_gone", "Days of ${monthName(current)} so far, today included", today.dayOfMonth.toLong())
        count("days_left", "Days left in ${monthName(current)}, today included", (current.lengthOfMonth() - today.dayOfMonth + 1).toLong())
        CashflowCalculator.safeToSpend(currency, today, inputs.transactions, inputs.wallets, inputs.budgets, inputs.schedules, inputs.categories)?.let { safe ->
            money("safe", "Safe to spend for the rest of ${monthName(current)} (as on Home)", safe.amount)
            safe.perDay?.let { money("safe_day", "Safe to spend a day for the rest of ${monthName(current)}", it) }
        }
        val firstRecord = inputs.transactions.filter { it.currencyCode == currency && !it.isTransfer && !it.isAdjustment }.minOfOrNull { it.date }
        val fullMonths = if (firstRecord == null) emptyList() else
            (1..AVERAGE_MONTHS).map { current.minusMonths(it.toLong()) }.filter { !it.isBefore(YearMonth.from(firstRecord)) }
        if (fullMonths.isNotEmpty()) {
            val n = fullMonths.size
            val trend = Insights.incomeExpenseTrend(inputs.transactions, currency, current.minusMonths(1), n)
            val income = Money.sum(currency, trend.map { it.income })
            val expense = Money.sum(currency, trend.map { it.expense })
            val over = if (n == 1) "in ${monthName(fullMonths.first())}" else "a month, over the last $n full months"
            money("avg_in", "Average income $over", CashflowCalculator.divide(income, n.toLong()))
            money("avg_out", "Average expenses $over", CashflowCalculator.divide(expense, n.toLong()))
            money("avg_net", "Average net flow (income − expenses) $over", CashflowCalculator.divide(income - expense, n.toLong()))
            if (income.amountMinor > 0L) {
                val kept = BigDecimal.valueOf((income - expense).amountMinor).multiply(BigDecimal(100))
                    .divide(BigDecimal.valueOf(income.amountMinor), 0, java.math.RoundingMode.HALF_UP).toInt()
                percent("save_rate", "Share of income kept (net flow ÷ income), last $n full ${if (n == 1) "month" else "months"}", kept)
            }
            val range = com.lucento.app.domain.DateRange(fullMonths.last().atDay(1), fullMonths.first().atEndOfMonth())
            Insights.categoryBreakdown(inputs.transactions, currency, range).take(10).forEachIndexed { i, s ->
                money("avgcat$i", "Average a month on ${s.categoryId?.let(names::get) ?: "Uncategorized"}, last $n full ${if (n == 1) "month" else "months"}", CashflowCalculator.divide(s.amount, n.toLong()))
            }
        }
        val spendable = inputs.wallets.filter { it.type.isSpendable && it.currencyCode == currency }
        if (spendable.isNotEmpty()) money("on_hand", "Cash, bank and e-wallet balances together", Money.sum(currency, spendable.map { it.balance }))
        val savings = inputs.wallets.filter { it.type == com.lucento.app.domain.WalletType.SAVINGS && it.currencyCode == currency }
        if (savings.isNotEmpty()) money("savings", "Savings account balances together", Money.sum(currency, savings.map { it.balance }))

        // Goals.
        inputs.goals.filter { it.currencyCode == currency }.take(10).forEachIndexed { i, goal ->
            val p = GoalCalculator.progress(goal, inputs.contributions, today)
            val name = "Goal “${goal.name.trim().take(30)}”"
            money("g${i}_target", "$name: target", goal.target)
            money("g${i}_saved", "$name: saved so far", p.saved)
            money("g${i}_left", "$name: still to save", p.remaining)
            percent("g${i}_pct", "$name: share saved", p.percent)
            goal.targetDate?.let { add("g${i}_date", "$name: target date", it.toString()) }
            p.requiredPerMonth?.let { money("g${i}_need", "$name: needed a month to reach it by the target date", it) }
            p.projectedCompletion?.let { add("g${i}_eta", "$name: reached around this date at the pace so far (estimate)", it.toString()) }
        }

        // Transaction patterns.
        TransactionSignals.topPayees(inputs.transactions, currency, today.minusDays(89), today).forEachIndexed { i, payee ->
            money("p$i", "Spent on “${payee.name}”, last 90 days (${payee.count} ${if (payee.count == 1) "time" else "times"})", payee.total)
        }
        TransactionSignals.repeating(inputs.transactions, currency, today).forEachIndexed { i, r ->
            money("r$i", "Repeats monthly: “${r.name}”, latest ${r.latest.date} (seen in ${r.months} months)", r.latest.amount)
        }
        TransactionSignals.unusual(inputs.transactions, currency, today).forEachIndexed { i, u ->
            val category = u.transaction.categoryId?.let(names::get) ?: "Uncategorized"
            money("u$i", "Unusually large $category expense: “${u.transaction.note.trim().take(30)}” on ${u.transaction.date}", u.transaction.amount)
            money("u${i}_usual", "Typical $category expense (median of the 90 days before this month)", u.typical)
        }
        TransactionSignals.duplicates(inputs.transactions, currency, today).forEachIndexed { i, d ->
            money("d$i", "Possible duplicate: “${d.first.note.trim().take(30)}” twice on ${d.first.date} in ${walletNames[d.first.walletId] ?: "one account"}", d.first.amount)
        }

        // Recent transactions, newest first, while there is room. A transfer is listed once, as "from A to B".
        val legs = inputs.transactions.filter { it.transferGroup != null }.groupBy { it.transferGroup }
        val recent = inputs.transactions
            .filter { it.currencyCode == currency && !it.isAdjustment && it.type != TransactionType.TRANSFER_IN }
            .filter { !it.date.isAfter(today) && !it.date.isBefore(today.minusDays(TRANSACTION_DAYS - 1)) }
            .sortedWith(compareByDescending<com.lucento.app.domain.Transaction> { it.date }.thenByDescending { it.createdAtEpochMillis })
        val room = (MAX_FIGURES - out.size - 1).coerceAtLeast(0)
        count("t_count", "Transactions in the last $TRANSACTION_DAYS days (ids t0, t1, … list the newest ${minOf(room, recent.size)})", recent.size.toLong())
        recent.take(room).forEachIndexed { i, t ->
            val what = when (t.type) {
                TransactionType.EXPENSE -> "expense · ${t.categoryId?.let(names::get) ?: "uncategorized"} · ${walletNames[t.walletId] ?: "?"}"
                TransactionType.INCOME -> "income · ${t.categoryId?.let(names::get) ?: "uncategorized"} · ${walletNames[t.walletId] ?: "?"}"
                else -> {
                    val to = legs[t.transferGroup]?.firstOrNull { it.type == TransactionType.TRANSFER_IN }?.walletId
                    "transfer · from ${walletNames[t.walletId] ?: "?"} to ${to?.let(walletNames::get) ?: "?"}"
                }
            }
            val note = t.note.trim().replace(Regex("\\s+"), " ").take(36)
            money("t$i", "${t.date} · $what" + if (note.isNotEmpty()) " · “$note”" else "", t.amount)
        }
        return AskSnapshot(currency, today, out)
    }
}

/** An answer ready to show: plain text in [parts], each figure marked so it can be shown as verified. */
data class CheckedAnswer(
    val parts: List<Part>,
    /** True when the AI wrote an amount itself instead of quoting one of the user's figures. */
    val hasUnverifiedFigures: Boolean,
) {
    data class Part(val text: String, val isFigure: Boolean)

    val text: String get() = parts.joinToString("") { it.text }
}

/**
 * The safety pass on every AI answer, before it is shown: figure ids are replaced with the values calculated on the
 * phone (an unknown id becomes "—"), links are removed, the length is capped, and any amount the model wrote itself
 * is detected so the answer says it couldn't be checked. Nothing in an answer can change data or open anything.
 */
object AnswerCheck {

    const val MAX_ANSWER = 2_500
    private val TOKEN = Regex("\\[\\[\\s*(=[^\\[\\]]{1,${AskCalc.MAX_EXPRESSION}}|[A-Za-z0-9_]{1,40})\\s*]]")
    private val LINK = Regex("(?i)\\b(?:https?://|www\\.)\\S+")
    /**
     * What reads as an amount the model wrote itself: a currency sign or a real ISO code before a number, a grouped
     * number ("12,500"), or one with two decimals ("45.50"). A rate is not an amount (owner round 10: the adviser may
     * quote and assume rates), so "7.03%" and "6.50 %" don't count, nor do letters that aren't a currency ("SSS 2024").
     */
    private val MONEY_LIKE = Regex("\\p{Sc}\\s?\\d|\\b([A-Z]{3})\\s?\\d|\\d{1,3}(?:[,.\\u00A0]\\d{3})+(?![\\d.,]*\\s?%)|\\d+[.,]\\d{2}\\b(?!\\s?%)")

    private fun hasMoneyLike(text: String): Boolean =
        MONEY_LIKE.findAll(text).any { m -> m.groups[1]?.value?.let { AskCalc.currencyCode(it) != null } ?: true }

    fun check(answer: String, snapshot: AskSnapshot): CheckedAnswer {
        val cleaned = LINK.replace(answer.trim(), "").replace("**", "").take(MAX_ANSWER)
        val parts = ArrayList<CheckedAnswer.Part>()
        var unverified = false
        var last = 0
        TOKEN.findAll(cleaned).forEach { match ->
            val before = cleaned.substring(last, match.range.first)
            if (hasMoneyLike(before)) unverified = true
            if (before.isNotEmpty()) parts.add(CheckedAnswer.Part(before, isFigure = false))
            val ref = match.groupValues[1]
            // A figure by its id, or a calculation the phone does with its own figures.
            val shown = if (ref.startsWith("=")) AskCalc.evaluate(ref.drop(1), snapshot)?.let { AskCalc.format(it) } else snapshot.byId[ref]?.value
            parts.add(CheckedAnswer.Part(shown ?: "—", isFigure = shown != null))
            last = match.range.last + 1
        }
        val tail = cleaned.substring(last)
        if (hasMoneyLike(tail)) unverified = true
        if (tail.isNotEmpty()) parts.add(CheckedAnswer.Part(tail, isFigure = false))
        return CheckedAnswer(parts, unverified)
    }

    /** Follow-up suggestions from the AI: at most three, short, plain. */
    fun followUps(raw: List<String>): List<String> =
        raw.map { LINK.replace(it, "").replace(TOKEN, "").trim() }.filter { it.length in 4..90 }.distinct().take(3)
}
