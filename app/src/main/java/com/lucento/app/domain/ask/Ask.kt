package com.lucento.app.domain.ask

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.Insights
import com.lucento.app.domain.ScheduleOutlook
import com.lucento.app.domain.ScheduledFlow
import com.lucento.app.domain.ScheduledItem
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

    /** The preset questions offered as chips; each is answered on the phone. */
    val suggestions: List<String> = listOf(
        "What are my top 5 spending categories?",
        "What’s my net worth?",
        "How’s my cash flow this month?",
        "Am I on budget this month?",
        "How are my debts doing?",
    )
}

/** One figure the AI may quote: it writes `[[id]]` and the app puts in [value], calculated on the phone. */
data class SnapshotFigure(val id: String, val label: String, val value: String)

/**
 * What Ask Lucento's AI sees (owner, 2026-11-03: summaries only): totals by month and category, balances by wallet,
 * budgets, debts and what's scheduled — each as a labelled figure with an id. Never transactions, notes, payees or
 * receipts. Wallet and category names are the user's own labels.
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
    private const val MONTHS = 12
    private const val CATEGORY_ROWS = 15

    fun build(inputs: ReportInputs, currency: String): AskSnapshot {
        val today = inputs.today
        val current = YearMonth.from(today)
        val out = ArrayList<SnapshotFigure>()
        fun add(id: String, label: String, value: String) {
            if (out.size < MAX_FIGURES) out.add(SnapshotFigure(id, label.take(100), value.take(40)))
        }
        fun money(id: String, label: String, m: Money) = add(id, label, Figures.plain(m))

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
            val names = inputs.categories.associate { it.id to it.name }
            val name = monthName(month) + if (back == 0L) " (so far)" else ""
            Insights.categoryBreakdown(inputs.transactions, currency, range).take(CATEGORY_ROWS).forEachIndexed { i, s ->
                money("cat${back}_$i", "Spent on ${s.categoryId?.let(names::get) ?: "Uncategorized"}, $name", s.amount)
            }
            Insights.categoryBreakdown(inputs.transactions, currency, range, com.lucento.app.domain.TransactionType.INCOME).take(8).forEachIndexed { i, s ->
                money("inc${back}_$i", "Income from ${s.categoryId?.let(names::get) ?: "Uncategorized"}, $name", s.amount)
            }
        }

        // This month's budget.
        val plan = BudgetPlanner.plan(inputs.categories, inputs.budgets, inputs.transactions, currency, current)
        if (plan.hasBudget) {
            money("bud_total", "Budget, ${monthName(current)}", plan.total)
            money("bud_spent", "Spent against the budget, ${monthName(current)} so far", plan.spent)
            money("bud_left", "Budget left, ${monthName(current)} (negative = over)", plan.remaining)
            plan.percentUsed?.let { add("bud_pct", "Budget used, ${monthName(current)}", "$it%") }
            plan.allocatedLines.take(20).forEachIndexed { i, line ->
                line.limit?.let { money("bud${i}_limit", "${line.category.name} budget", it) }
                money("bud${i}_spent", "${line.category.name} spent this month", line.spent)
                line.remaining?.let { money("bud${i}_left", "${line.category.name} budget left (negative = over)", it) }
            }
        }

        // Debts.
        inputs.wallets.filter { it.type.isLiability && it.currencyCode == currency }.take(10).forEachIndexed { i, w ->
            w.debt?.interestRateBps?.let { add("debt${i}_rate", "${w.name} interest rate", "${DebtMath.formatRate(it)} a year") }
            CreditCardMath.cycle(w, inputs.transactions, today)?.takeIf { !it.amountDue.isZero }?.let {
                money("debt${i}_due", "${w.name} amount due by ${shortDate(it.dueDate)}", it.amountDue)
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

    const val MAX_ANSWER = 1_500
    private val TOKEN = Regex("\\[\\[([A-Za-z0-9_]{1,40})]]")
    private val LINK = Regex("(?i)\\b(?:https?://|www\\.)\\S+")
    private val MONEY_LIKE = Regex("\\p{Sc}\\s?\\d|\\b[A-Z]{3}\\s?\\d|\\d{1,3}(?:[,.\\u00A0]\\d{3})+|\\d+[.,]\\d{2}\\b")

    fun check(answer: String, snapshot: AskSnapshot): CheckedAnswer {
        val cleaned = LINK.replace(answer.trim(), "").replace("**", "").take(MAX_ANSWER)
        val parts = ArrayList<CheckedAnswer.Part>()
        var unverified = false
        var last = 0
        TOKEN.findAll(cleaned).forEach { match ->
            val before = cleaned.substring(last, match.range.first)
            if (MONEY_LIKE.containsMatchIn(before)) unverified = true
            if (before.isNotEmpty()) parts.add(CheckedAnswer.Part(before, isFigure = false))
            val figure = snapshot.byId[match.groupValues[1]]
            parts.add(CheckedAnswer.Part(figure?.value ?: "—", isFigure = figure != null))
            last = match.range.last + 1
        }
        val tail = cleaned.substring(last)
        if (MONEY_LIKE.containsMatchIn(tail)) unverified = true
        if (tail.isNotEmpty()) parts.add(CheckedAnswer.Part(tail, isFigure = false))
        return CheckedAnswer(parts, unverified)
    }

    /** Follow-up suggestions from the AI: at most three, short, plain. */
    fun followUps(raw: List<String>): List<String> =
        raw.map { LINK.replace(it, "").replace(TOKEN, "").trim() }.filter { it.length in 4..90 }.distinct().take(3)
}
