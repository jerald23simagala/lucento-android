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
     * subtotals in history. Transfers between the user's own wallets are excluded. Primary currency
     * first, then alphabetical.
     */
    fun netByCurrency(transactions: List<Transaction>, primaryCurrency: String): List<Money> =
        transactions
            .filterNot { it.isTransfer }
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
}
