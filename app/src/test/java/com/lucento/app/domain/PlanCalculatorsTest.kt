package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlanCalculatorsTest {

    private var nextId = 1L
    private fun tx(type: TransactionType, minor: Long, date: String, category: Long? = null, code: String = "USD", wallet: Long = 1) =
        Transaction(nextId++, type, Money(minor, code), wallet, category, LocalDate.parse(date), "", 0L,
            transferGroup = if (type.isTransfer) "g" else null)

    // ---------------------------------------------------------------- budgets

    @Test fun budgetCountsOnlyExpensesInItsMonthCurrencyAndCategory() {
        val october = YearMonth.of(2026, 10)
        val txs = listOf(
            tx(TransactionType.EXPENSE, 1_000, "2026-10-01", category = 7),
            tx(TransactionType.EXPENSE, 2_000, "2026-10-31", category = 8),
            tx(TransactionType.EXPENSE, 4_000, "2026-09-30", category = 7), // other month
            tx(TransactionType.EXPENSE, 8_000, "2026-10-10", category = 7, code = "EUR"), // other currency
            tx(TransactionType.INCOME, 16_000, "2026-10-10", category = 7), // income never offsets
            tx(TransactionType.TRANSFER_OUT, 32_000, "2026-10-10"), // transfers never count
        )
        val legacyOverall = Budget(1, null, Money(10_000, "USD"))
        val food = Budget(2, 7, Money(1_000, "USD"))
        // A pre-0.5 "everything" row covers nothing any more.
        assertEquals(Money.zero("USD"), BudgetCalculator.spent(legacyOverall, txs, october))
        assertEquals(Money(1_000, "USD"), BudgetCalculator.spent(food, txs, october))
        assertEquals(listOf(2L), BudgetCalculator.progressAll(listOf(legacyOverall, food), txs, october).map { it.budget.id })
    }

    @Test fun budgetStatusThresholds() {
        val b = Budget(1, 3, Money(10_000, "USD"))
        val m = YearMonth.of(2026, 10)
        assertEquals(BudgetStatus.ON_TRACK, BudgetProgress(b, m, Money(7_999, "USD")).status)
        assertEquals(BudgetStatus.NEAR_LIMIT, BudgetProgress(b, m, Money(8_000, "USD")).status)
        assertEquals(BudgetStatus.NEAR_LIMIT, BudgetProgress(b, m, Money(10_000, "USD")).status)
        assertEquals(BudgetStatus.OVER_LIMIT, BudgetProgress(b, m, Money(10_001, "USD")).status)
        assertEquals(Money(-1, "USD"), BudgetProgress(b, m, Money(10_001, "USD")).remaining)
    }

    // ---------------------------------------------------------------- goals

    @Test fun goalProgressAndProjection() {
        val today = LocalDate.parse("2026-10-22")
        val goal = Goal(1, "Fund", Money(120_000, "USD"), LocalDate.parse("2027-03-15"), null, 0)
        val contributions = listOf(
            GoalContribution(1, 1, Money(30_000, "USD"), LocalDate.parse("2026-08-23"), ""),
            GoalContribution(2, 1, Money(30_000, "USD"), LocalDate.parse("2026-09-22"), ""),
        )
        val p = GoalCalculator.progress(goal, contributions, today)
        assertEquals(Money(60_000, "USD"), p.saved)
        assertEquals(Money(60_000, "USD"), p.remaining)
        assertEquals(50, p.percent)
        // 60,000 over 60 days -> 1,000/day -> 60 more days.
        assertEquals(today.plusDays(60), p.projectedCompletion)
        // Oct, Nov, Dec, Jan, Feb, Mar = 6 months -> 10,000 per month.
        assertEquals(Money(10_000, "USD"), p.requiredPerMonth)
    }

    @Test fun reachedGoalHasNothingRemaining() {
        val goal = Goal(1, "Fund", Money(100, "USD"), null, null, 0)
        val p = GoalCalculator.progress(goal, listOf(GoalContribution(1, 1, Money(150, "USD"), LocalDate.parse("2026-01-01"), "")), LocalDate.parse("2026-02-01"))
        assertEquals(true, p.reached)
        assertEquals(Money.zero("USD"), p.remaining)
        assertEquals(100, p.percent)
        assertNull(p.projectedCompletion)
    }

    @Test fun noPaceMeansNoProjection() {
        val today = LocalDate.parse("2026-10-22")
        assertNull(GoalCalculator.projectCompletion(emptyList(), Money(100, "USD"), today))
        val withdrawn = listOf(
            GoalContribution(1, 1, Money(100, "USD"), today.minusDays(40), ""),
            GoalContribution(2, 1, Money(-100, "USD"), today.minusDays(10), ""),
        )
        assertNull(GoalCalculator.projectCompletion(withdrawn, Money(100, "USD"), today))
    }

    @Test fun requiredPerMonthRoundsUpAndHandlesPastDates() {
        val today = LocalDate.parse("2026-10-22")
        assertEquals(Money(334, "USD"), GoalCalculator.requiredPerMonth(Money(1_000, "USD"), LocalDate.parse("2026-12-01"), today))
        assertEquals(Money(1_000, "USD"), GoalCalculator.requiredPerMonth(Money(1_000, "USD"), LocalDate.parse("2026-01-01"), today))
        assertNull(GoalCalculator.requiredPerMonth(Money(1_000, "USD"), null, today))
    }

    // ---------------------------------------------------------------- recurring

    private fun schedule(
        start: String,
        frequency: Frequency = Frequency.MONTHLY,
        interval: Int = 1,
        next: Int = 0,
        paused: Boolean = false,
        end: String? = null,
        minor: Long = 1_000,
        type: TransactionType = TransactionType.EXPENSE,
        code: String = "USD",
    ) = RecurringSchedule(1, "Bill", type, Money(minor, code), 1, null, frequency, interval, LocalDate.parse(start), end?.let(LocalDate::parse), next, paused)

    @Test fun monthlyOccurrencesKeepTheMonthEndAnchor() {
        val s = schedule("2026-01-31")
        assertEquals(LocalDate.parse("2026-02-28"), RecurringMath.occurrence(s, 1))
        assertEquals(LocalDate.parse("2026-03-31"), RecurringMath.occurrence(s, 2))
        assertEquals(LocalDate.parse("2028-02-29"), RecurringMath.occurrence(schedule("2026-02-28", Frequency.YEARLY).copy(startDate = LocalDate.parse("2024-02-29")), 4))
    }

    @Test fun weeklyAndIntervals() {
        val s = schedule("2026-10-01", Frequency.WEEKLY, interval = 2)
        assertEquals(LocalDate.parse("2026-10-29"), RecurringMath.occurrence(s, 2))
    }

    @Test fun dueThroughIncludesOverdueAndStopsAtEnd() {
        val s = schedule("2026-10-01", Frequency.WEEKLY, end = "2026-10-20")
        val due = RecurringMath.dueThrough(s, LocalDate.parse("2026-10-31"))
        assertEquals(listOf("2026-10-01", "2026-10-08", "2026-10-15"), due.map { it.second.toString() })
        assertEquals(emptyList<Pair<Int, LocalDate>>(), RecurringMath.dueThrough(s.copy(paused = true), LocalDate.parse("2026-10-31")))
        assertNull(s.copy(nextIndex = 3).nextDue)
    }

    @Test fun committedExpensesOnlyCountUnpaidExpenseOccurrencesInCurrency() {
        val rent = schedule("2026-10-05", minor = 50_000, next = 0)
        val paid = schedule("2026-10-01", minor = 7_000, next = 1) // October already paid -> next is Nov 1
        val salary = schedule("2026-10-25", minor = 99_000, type = TransactionType.INCOME)
        val euro = schedule("2026-10-25", minor = 5_000, code = "EUR")
        val total = RecurringMath.committedExpenses(listOf(rent, paid, salary, euro), "USD", LocalDate.parse("2026-10-31"))
        assertEquals(Money(50_000, "USD"), total)
    }

    @Test fun scheduleStates() {
        val today = LocalDate.parse("2026-10-22")
        assertEquals(RecurringState.UPCOMING, RecurringMath.state(schedule("2026-10-30"), today))
        assertEquals(RecurringState.ACTIVE, RecurringMath.state(schedule("2026-12-30"), today))
        assertEquals(RecurringState.PAUSED, RecurringMath.state(schedule("2026-10-30", paused = true), today))
        assertEquals(RecurringState.FINISHED, RecurringMath.state(schedule("2026-01-01", end = "2026-01-02", next = 1), today))
    }

    // ---------------------------------------------------------------- balances

    @Test fun loanBalanceTracksOwed() {
        val opening = Money(100_000, "USD")
        // 10,000 drawn into a bank (transfer out), 2,000 interest (expense), 15,000 repaid (transfer in).
        val owed = WalletBalanceCalculator.currentBalance(opening, WalletType.LOAN, 0, 2_000, transferInMinor = 15_000, transferOutMinor = 10_000)
        assertEquals(Money(97_000, "USD"), owed)
        val asset = WalletBalanceCalculator.currentBalance(opening, WalletType.BANK, 500, 200, transferInMinor = 1_000, transferOutMinor = 300)
        assertEquals(Money(101_000, "USD"), asset)
    }
}
