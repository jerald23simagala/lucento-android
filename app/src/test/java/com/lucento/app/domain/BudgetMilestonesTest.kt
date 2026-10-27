package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetMilestonesTest {
    private val today = LocalDate.parse("2026-10-03")
    private val food = Category(4, "Food", CategoryKind.EXPENSE, "groceries")
    private val fun_ = Category(5, "Fun", CategoryKind.EXPENSE, "entertainment")
    private val categories = listOf(food, fun_)
    private val created = LocalDate.parse("2026-08-15")
    // Total = 6,000 + 4,000 = 10,000.
    private val budgets = listOf(
        Budget(1, 4, Money(6_000, "PHP"), createdOn = created),
        Budget(2, 5, Money(4_000, "PHP"), createdOn = created),
    )
    private fun spend(minor: Long, date: String, category: Long? = 4) =
        Transaction(minor, TransactionType.EXPENSE, Money(minor, "PHP"), 1, category, LocalDate.parse(date), "", 0)

    private fun ack(budgets: List<Budget>, txs: List<Transaction>, currency: String = "PHP", last: YearMonth? = null) =
        BudgetMilestones.monthToAcknowledge(categories, budgets, txs, currency, today, last)

    @Test fun keptLastMonthIsAcknowledged() =
        assertEquals(YearMonth.of(2026, 9), ack(budgets, listOf(spend(9_000, "2026-09-10")))?.month)

    @Test fun overspendingTheCategoryTotalIsNeverCelebrated() =
        assertNull(ack(budgets, listOf(spend(6_001, "2026-09-10"), spend(4_000, "2026-09-11", category = 5))))

    @Test fun exactlyAtTheTotalCounts() =
        assertEquals(YearMonth.of(2026, 9), ack(budgets, listOf(spend(10_000, "2026-09-10")))?.month)

    @Test fun shownOnlyOnce() = assertNull(ack(budgets, listOf(spend(1, "2026-09-10")), last = YearMonth.of(2026, 9)))

    @Test fun emptyMonthsAndAllocationsCreatedMidMonthDoNotCount() {
        assertNull(ack(budgets, emptyList()))
        val late = budgets.map { it.copy(createdOn = LocalDate.parse("2026-09-02")) }
        assertNull(ack(late, listOf(spend(1, "2026-09-10"))))
        assertNull(ack(budgets.map { it.copy(createdOn = null) }, listOf(spend(1, "2026-09-10"))))
    }

    @Test fun onlyTheMatchingCurrencyAndNeverALegacyOverallRow() {
        assertNull(ack(budgets, listOf(spend(1, "2026-09-10")), currency = "USD"))
        assertNull(ack(listOf(Budget(9, null, Money(10_000, "PHP"), createdOn = created)), listOf(spend(1, "2026-09-10"))))
    }
}
