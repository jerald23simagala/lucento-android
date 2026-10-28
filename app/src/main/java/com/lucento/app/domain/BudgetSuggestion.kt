package com.lucento.app.domain

import com.lucento.app.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * One real expense that can feed a budget suggestion. Transfers, adjustments and income are not passed in.
 * A null [categoryId] is spending with no category: it counts as data, and it is not given an amount.
 */
data class SpentDay(
    val date: LocalDate,
    val categoryId: Long?,
    val categoryName: String?,
    val amountMinor: Long,
    val currencyCode: String,
)

data class BudgetSuggestionLine(
    val categoryId: Long,
    val categoryName: String,
    val suggested: Money,
)

data class BudgetSuggestion(
    /** How many days of spending the amounts were drawn from, from 7 to 14. */
    val days: Int,
    val lines: List<BudgetSuggestionLine>,
)

/**
 * After 7 to 14 days of real spending, suggest a monthly amount per category from what was actually spent.
 * Nothing here writes sample data. Categories that already have a budget are left out.
 */
object BudgetSuggestions {
    const val MIN_DAYS = 7
    const val MAX_DAYS = 14
    const val SNOOZE_DAYS = 7

    fun snoozeUntil(today: LocalDate): LocalDate = today.plusDays(SNOOZE_DAYS.toLong())

    fun suggest(
        expenses: List<SpentDay>,
        today: LocalDate,
        currencyCode: String,
        budgetedCategoryIds: Set<Long>,
        snoozedUntil: LocalDate?,
    ): BudgetSuggestion? {
        if (snoozedUntil != null && today.isBefore(snoozedUntil)) return null
        val spent = expenses.filter {
            it.currencyCode == currencyCode && it.amountMinor > 0L && !it.date.isAfter(today)
        }
        if (spent.isEmpty()) return null
        val first = spent.minOf { it.date }
        val elapsed = ChronoUnit.DAYS.between(first, today) + 1
        if (elapsed < MIN_DAYS) return null
        val windowDays = minOf(elapsed, MAX_DAYS.toLong()).toInt()
        val windowStart = today.minusDays((windowDays - 1).toLong())
        val inWindow = spent.filter { !it.date.isBefore(windowStart) }
        if (inWindow.isEmpty()) return null
        val distinctDays = inWindow.map { it.date }.distinct().size
        val ready = distinctDays >= MIN_DAYS || elapsed >= MAX_DAYS
        if (!ready) return null
        val monthDays = YearMonth.from(today).lengthOfMonth()
        val lines = inWindow
            .filter { it.categoryId != null && it.categoryId !in budgetedCategoryIds && !it.categoryName.isNullOrBlank() }
            .groupBy { it.categoryId!! }
            .map { (categoryId, rows) ->
                val name = rows.first().categoryName!!.trim()
                val sum = rows.fold(0L) { acc, row -> Math.addExact(acc, row.amountMinor) }
                BudgetSuggestionLine(categoryId, name, monthly(sum, monthDays, windowDays, currencyCode))
            }
            .filter { it.suggested.amountMinor > 0L }
            .sortedByDescending { it.suggested.amountMinor }
        if (lines.isEmpty()) return null
        return BudgetSuggestion(days = windowDays, lines = lines)
    }

    /** Scale the window's spending to this calendar month. Exact integer minor units, half up. */
    private fun monthly(sumMinor: Long, monthDays: Int, windowDays: Int, currencyCode: String): Money {
        val minor = BigDecimal.valueOf(sumMinor)
            .multiply(BigDecimal.valueOf(monthDays.toLong()))
            .divide(BigDecimal.valueOf(windowDays.toLong()), 0, RoundingMode.HALF_UP)
            .longValueExact()
        return Money(minor, currencyCode)
    }
}
