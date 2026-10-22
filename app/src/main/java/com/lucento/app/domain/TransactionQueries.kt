package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.time.LocalDate

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "Range end is before start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    companion object {
        fun monthOf(date: LocalDate): DateRange =
            DateRange(date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()))
    }
}

enum class RangePreset {
    ALL, THIS_MONTH, LAST_MONTH, LAST_30_DAYS;

    /** Resolves to a concrete range for [today]; null means unbounded. */
    fun resolve(today: LocalDate): DateRange? = when (this) {
        ALL -> null
        THIS_MONTH -> DateRange.monthOf(today)
        LAST_MONTH -> DateRange.monthOf(today.minusMonths(1))
        LAST_30_DAYS -> DateRange(today.minusDays(29), today)
    }
}

data class TransactionFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val range: DateRange? = null,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || type != null || walletId != null || categoryId != null || range != null
}

data class DayGroup(val date: LocalDate, val items: List<Transaction>)

data class PeriodTotals(val income: Money, val expense: Money) {
    val currencyCode: String get() = income.currencyCode
    val net: Money get() = income - expense
}

/** Pure, deterministic queries over an in-memory transaction list. */
object TransactionQueries {

    fun filter(
        transactions: List<Transaction>,
        filter: TransactionFilter,
        categoryNames: Map<Long, String> = emptyMap(),
        walletNames: Map<Long, String> = emptyMap(),
    ): List<Transaction> {
        val q = filter.query.trim().lowercase().replace(',', '.')
        return transactions.filter { t ->
            (filter.type == null || t.type == filter.type) &&
                (filter.walletId == null || t.walletId == filter.walletId) &&
                (filter.categoryId == null || t.categoryId == filter.categoryId) &&
                (filter.range == null || t.date in filter.range) &&
                (q.isEmpty() || matches(t, q, categoryNames, walletNames))
        }
    }

    private fun matches(t: Transaction, q: String, categoryNames: Map<Long, String>, walletNames: Map<Long, String>): Boolean =
        t.note.lowercase().contains(q) ||
            (t.categoryId?.let { categoryNames[it] }?.lowercase()?.contains(q) == true) ||
            (walletNames[t.walletId]?.lowercase()?.contains(q) == true) ||
            t.amount.toBigDecimal().toPlainString().contains(q)

    /** Groups an already-sorted list into consecutive days, preserving order. */
    fun groupByDay(transactions: List<Transaction>): List<DayGroup> {
        val groups = ArrayList<DayGroup>()
        var currentDate: LocalDate? = null
        var bucket = ArrayList<Transaction>()
        for (t in transactions) {
            if (t.date != currentDate) {
                currentDate?.let { groups.add(DayGroup(it, bucket)) }
                currentDate = t.date
                bucket = ArrayList()
            }
            bucket.add(t)
        }
        currentDate?.let { groups.add(DayGroup(it, bucket)) }
        return groups
    }

    /**
     * Income and expense totals per currency within [range]; primary currency first, then alphabetical.
     * Currencies are never mixed. Transfers (M2) will be excluded by construction.
     */
    fun totalsByCurrency(
        transactions: List<Transaction>,
        range: DateRange,
        primaryCurrency: String,
    ): List<PeriodTotals> = totalsByCurrency(transactions.filter { it.date in range }, primaryCurrency)

    /** Income and expense totals per currency over all of [transactions]; primary currency first. */
    fun totalsByCurrency(
        transactions: List<Transaction>,
        primaryCurrency: String,
    ): List<PeriodTotals> =
        transactions
            .groupBy { it.currencyCode }
            .map { (code, list) ->
                PeriodTotals(
                    income = Money.sum(code, list.filter { it.type == TransactionType.INCOME }.map { it.amount }),
                    expense = Money.sum(code, list.filter { it.type == TransactionType.EXPENSE }.map { it.amount }),
                )
            }
            .sortedWith(compareBy<PeriodTotals> { it.currencyCode != primaryCurrency }.thenBy { it.currencyCode })
}
