package com.lucento.app.feature.budget

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.feature.home.HomeStateBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Decision D1 (2026-11-03): Budget's per-day figure is "Safe per day", the same calculation as Home's safe to spend. */
class BudgetDailyFigureTest {

    private val today = LocalDate.parse("2025-03-20") // 12 days left including today
    private var nextId = 100L

    private val groceries = Category(11, "Groceries", CategoryKind.EXPENSE, "groceries")
    private val housing = Category(10, "Housing", CategoryKind.EXPENSE, "housing")
    private val categories = listOf(housing, groceries)
    private val wallets = listOf(Wallet(1, "Bank", WalletType.BANK, Money(500_000, "USD"), 0L))
    private val budgets = listOf(Budget(1, 10, Money(50_000, "USD")), Budget(2, 11, Money(10_000, "USD")))

    private fun tx(amount: Long, date: String, category: Long?) =
        Transaction(nextId++, TransactionType.EXPENSE, Money(amount, "USD"), 1L, category, LocalDate.parse(date), "", 0L)

    private val transactions = listOf(tx(30_000, "2025-03-02", 10), tx(6_000, "2025-03-05", 11), tx(4_000, "2025-02-10", 11))

    private fun bill(amount: Long) = RecurringSchedule(
        1, "Internet", TransactionType.EXPENSE, Money(amount, "USD"), 1L, 11L, Frequency.MONTHLY, 1, LocalDate.parse("2025-03-25"), null, 0, false,
    )

    private fun figure(month: YearMonth = YearMonth.from(today), schedules: List<RecurringSchedule> = listOf(bill(2_000)), budgets: List<Budget> = this.budgets): DailyFigure? {
        val plan = BudgetPlanner.plan(categories, budgets, transactions, "USD", month)
        return budgetDailyFigure(plan, BudgetPlanner.burn(plan, today), today, transactions, wallets, budgets, schedules, categories)
    }

    @Test fun safePerDaySetsAsideBillsDueAndSpreadsTheRestOverTheDaysLeft() {
        val daily = figure() as DailyFigure.SafePerDay
        // 60,000 budget − 36,000 spent − 2,000 internet = 22,000 over 12 days = 1,833.33 → 1,833 (rounded down).
        assertEquals(Money(1_833, "USD"), daily.perDay)
        assertEquals(12, daily.daysLeft)
        assertEquals(Money(2_000, "USD"), daily.committed)
    }

    @Test fun matchesHomesSafeToSpendForTheSameData() {
        val home = HomeStateBuilder.build(transactions, wallets, categories, "USD", today, budgets, listOf(bill(2_000)))
        val daily = figure() as DailyFigure.SafePerDay
        assertEquals(home.safeToSpend!!.perDay, daily.perDay)
        assertEquals(home.safeToSpend!!.committed, daily.committed)
    }

    @Test fun nothingLeftOnceBillsAreSetAsideShowsSafeToSpendItself() {
        val daily = figure(schedules = listOf(bill(30_000))) as DailyFigure.NothingLeft
        assertEquals(Money(60_000 - 36_000 - 30_000, "USD"), daily.safe)
        assertTrue(daily.safe.isNegative)
    }

    @Test fun withoutBillsItIsTheRemainingSpreadOverTheDaysLeft() {
        val daily = figure(schedules = emptyList()) as DailyFigure.SafePerDay
        assertEquals(Money(24_000 / 12, "USD"), daily.perDay)
        assertTrue(daily.committed.isZero)
    }

    @Test fun aPastMonthShowsItsDailyAverage() {
        val daily = figure(month = YearMonth.of(2025, 2)) as DailyFigure.DailyAverage
        assertEquals(28, daily.days)
        assertEquals(Money(143, "USD"), daily.perDay) // 4,000 ÷ 28 = 142.857… → 143 (half-up, BudgetPlanner.burn)
    }

    @Test fun noBudgetNoFigure() {
        assertNull(figure(budgets = emptyList()))
    }

    @Test fun paceVerdictWordsAreSharedWithHome() {
        val plan = BudgetPlanner.plan(categories, budgets, transactions, "USD", YearMonth.from(today))
        assertEquals(PaceVerdict.ON_TRACK, paceVerdict(plan, com.lucento.app.domain.PacePosition.ALIGNED, isCurrentMonth = true))
        assertEquals(PaceVerdict.UNDER, paceVerdict(plan, com.lucento.app.domain.PacePosition.BEHIND, isCurrentMonth = true))
        assertEquals("Under budget", verdictLabel(plan, com.lucento.app.domain.PacePosition.BEHIND, isCurrentMonth = true))
    }
}
