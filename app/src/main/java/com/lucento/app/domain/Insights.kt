package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/** Total spent (or earned) in one calendar month, in one currency. */
data class MonthAmount(val month: YearMonth, val amount: Money)

/** Income and expenses recorded in one calendar month, in one currency. Transfers are never included. */
data class FlowMonth(val month: YearMonth, val income: Money, val expense: Money) {
    val net: Money get() = income - expense
}

/** Share of a period's spending that belongs to one category. [categoryId] null means uncategorized. */
data class CategoryShare(val categoryId: Long?, val amount: Money)

/**
 * Deterministic, presentation-ready insights derived only from recorded transactions.
 * Every function works within a single currency; nothing is converted or estimated.
 */
object Insights {

    /**
     * Totals of [type] per calendar month for the [months] months ending at [endMonth] (inclusive),
     * oldest first. Months without transactions are present with a zero amount so charts stay aligned.
     */
    fun monthlyTotals(
        transactions: List<Transaction>,
        currencyCode: String,
        endMonth: YearMonth,
        months: Int,
        type: TransactionType = TransactionType.EXPENSE,
    ): List<MonthAmount> {
        require(months > 0) { "months must be positive" }
        val startMonth = endMonth.minusMonths((months - 1).toLong())
        val byMonth = transactions
            .asSequence()
            .filter { it.type == type && it.currencyCode == currencyCode }
            .filter { val m = YearMonth.from(it.date); !m.isBefore(startMonth) && !m.isAfter(endMonth) }
            .groupBy { YearMonth.from(it.date) }
        return (0 until months).map { offset ->
            val month = startMonth.plusMonths(offset.toLong())
            MonthAmount(month, Money.sum(currencyCode, byMonth[month].orEmpty().map { it.amount }))
        }
    }

    /**
     * Income and expenses per month for the [months] months ending at [endMonth], oldest first, zero-filled. Only
     * INCOME and EXPENSE transactions count, so transfers between the user's own wallets (two linked legs) never
     * appear as either.
     */
    fun incomeExpenseTrend(
        transactions: List<Transaction>,
        currencyCode: String,
        endMonth: YearMonth,
        months: Int,
    ): List<FlowMonth> {
        val income = monthlyTotals(transactions, currencyCode, endMonth, months, TransactionType.INCOME)
        val expense = monthlyTotals(transactions, currencyCode, endMonth, months, TransactionType.EXPENSE)
        return income.zip(expense) { i, e -> FlowMonth(i.month, i.amount, e.amount) }
    }

    /**
     * Spending per category within [range] in one currency, largest first (ties broken by category id,
     * uncategorized last) so the order is stable between recompositions.
     */
    fun categoryBreakdown(
        transactions: List<Transaction>,
        currencyCode: String,
        range: DateRange,
        type: TransactionType = TransactionType.EXPENSE,
    ): List<CategoryShare> =
        transactions
            .filter { it.type == type && it.currencyCode == currencyCode && it.date in range }
            .groupBy { it.categoryId }
            .map { (categoryId, list) -> CategoryShare(categoryId, Money.sum(currencyCode, list.map { it.amount })) }
            .sortedWith(
                compareByDescending<CategoryShare> { it.amount.amountMinor }
                    .thenBy { it.categoryId == null }
                    .thenBy { it.categoryId ?: Long.MAX_VALUE },
            )

    /**
     * Net movement (income minus expense) per currency for a set of transactions — used for daily
     * subtotals in history. Transfers between the user's own wallets and balance adjustments are excluded.
     * Primary currency first, then alphabetical.
     */
    fun netByCurrency(transactions: List<Transaction>, primaryCurrency: String): List<Money> =
        transactions
            .filter { it.type.isCashFlow }
            .groupBy { it.currencyCode }
            .map { (code, list) -> Money.sum(code, list.map { it.signedAmount }) }
            .sortedWith(compareBy<Money> { it.currencyCode != primaryCurrency }.thenBy { it.currencyCode })

    /**
     * [part] as a whole-number percentage of [whole], rounded half-up. Returns null when [whole] is zero
     * or negative (no meaningful ratio). May exceed 100, e.g. when spending exceeds income.
     */
    fun percentOf(part: Money, whole: Money): Int? {
        require(part.currencyCode == whole.currencyCode) { "Currency mismatch" }
        if (whole.amountMinor <= 0L) return null
        return BigDecimal.valueOf(part.amountMinor)
            .multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(whole.amountMinor), 0, RoundingMode.HALF_UP)
            .toInt()
    }

    /**
     * Fraction (0..1) of [part] relative to [whole] for drawing bars. Display-only: never use the
     * result for arithmetic on money. Values outside the range are clamped.
     */
    fun fraction(part: Money, whole: Money): Float {
        require(part.currencyCode == whole.currencyCode) { "Currency mismatch" }
        if (whole.amountMinor <= 0L || part.amountMinor <= 0L) return 0f
        if (part.amountMinor >= whole.amountMinor) return 1f
        return BigDecimal.valueOf(part.amountMinor)
            .divide(BigDecimal.valueOf(whole.amountMinor), 4, RoundingMode.HALF_UP)
            .toFloat()
    }

    /**
     * How [current] compares with [previous], as a whole percentage of the previous amount's size (rounded half-up):
     * +8 when net flow grew from 1,000 to 1,080, −50 when it halved. Null when [previous] is zero (no base to compare).
     */
    fun changePercent(current: Money, previous: Money): Int? {
        require(current.currencyCode == previous.currencyCode) { "Currency mismatch" }
        if (previous.amountMinor == 0L) return null
        return BigDecimal.valueOf(Math.subtractExact(current.amountMinor, previous.amountMinor))
            .multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(previous.amountMinor).abs(), 0, RoundingMode.HALF_UP)
            .toInt()
    }

    /**
     * Round gridline values (in major units) for a chart whose largest bar is [maxMinor]: zero, then equal steps of 1, 2,
     * 2.5 or 5 × a power of ten — the largest step that still gives at most [steps] + 1 intervals — the last at or above
     * the largest bar. Only zero when there is nothing to draw.
     */
    fun axisTicks(maxMinor: Long, fractionDigits: Int, steps: Int = 4): List<Long> {
        if (maxMinor <= 0L) return listOf(0L)
        var unit = 1L
        repeat(fractionDigits) { unit *= 10 }
        val maxMajor = (maxMinor + unit - 1) / unit
        var magnitude = 1L
        while (magnitude * 10 <= maxMajor / steps) magnitude *= 10
        val candidates = buildList {
            add(magnitude)
            add(2 * magnitude)
            if (magnitude >= 10) add(magnitude / 10 * 25)
            add(5 * magnitude)
            add(10 * magnitude)
            add(20 * magnitude)
        }
        val step = candidates.first { (maxMajor + it - 1) / it <= steps + 1 }
        val count = ((maxMajor + step - 1) / step).coerceAtLeast(1)
        return (0..count).map { it * step }
    }
}
