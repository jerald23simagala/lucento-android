package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Goal
import com.lucento.app.domain.GoalContribution
import com.lucento.app.domain.GoalsSummary
import com.lucento.app.domain.PacePosition
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The dashboard's budget pace, month selection, sync status, net worth breakdown and featured goal. */
class DashboardRefreshTest {

    private val food = Category(11, "Food", CategoryKind.EXPENSE, "groceries")
    private val budget = listOf(Budget(1, 11, Money(100_000, "USD"))) // $1,000.00
    private var nextId = 1L

    private fun spend(minor: Long, date: String) =
        Transaction(nextId++, TransactionType.EXPENSE, Money(minor, "USD"), 1L, 11, LocalDate.parse(date), "", 0L)

    private fun burnOn(today: String, spent: Long) =
        BudgetPlanner.plan(listOf(food), budget, listOf(spend(spent, today)), "USD", YearMonth.from(LocalDate.parse(today)))
            .let { BudgetPlanner.burn(it, LocalDate.parse(today)) }

    // ---- budget pace: budget used vs month elapsed, real calendar days

    @Test fun spendingAheadOfTheMonth() {
        // 16 June = day 16 of 30 → 53% of the month; 68% of the budget used.
        val burn = burnOn("2026-06-16", 68_000)
        assertEquals(68, burn.consumedPercent)
        assertEquals(16, burn.daysElapsed)
        assertEquals(30, burn.daysInMonth)
        assertEquals(53, burn.elapsedPercent)
        assertEquals(PacePosition.AHEAD, burn.pace)
    }

    @Test fun spendingInStepWithTheMonth() {
        assertEquals(PacePosition.ALIGNED, burnOn("2026-06-16", 55_000).pace) // 55% vs 53%
        assertEquals(PacePosition.ALIGNED, burnOn("2026-06-16", 63_000).pace) // within the 10-point margin
    }

    @Test fun spendingSlowerThanTheMonth() {
        assertEquals(PacePosition.BEHIND, burnOn("2026-06-16", 30_000).pace) // 30% vs 53%
    }

    @Test fun februaryUsesItsOwnLength() {
        val leap = burnOn("2028-02-15", 10_000)
        assertEquals(29, leap.daysInMonth)
        assertEquals(51, leap.elapsedPercent) // 15 of 29 days, rounded down like every elapsed share
        assertEquals(28, burnOn("2026-02-14", 10_000).daysInMonth)
    }

    @Test fun noBudgetMeansNoPaceVerdict() {
        val plan = BudgetPlanner.plan(listOf(food), emptyList(), listOf(spend(5_000, "2026-06-16")), "USD", YearMonth.of(2026, 6))
        val burn = BudgetPlanner.burn(plan, LocalDate.parse("2026-06-16"))
        assertNull(burn.pace)
        val insight = PaceCopy.describe(plan, burn, "June", isCurrentMonth = true)
        assertNull(insight.position)
        assertTrue(insight.message.startsWith("No budget for June yet."))
    }

    @Test fun insightWordsFollowTheFigures() {
        fun insight(spent: Long) = BudgetPlanner.plan(listOf(food), budget, listOf(spend(spent, "2026-06-16")), "USD", YearMonth.of(2026, 6))
            .let { PaceCopy.describe(it, BudgetPlanner.burn(it, LocalDate.parse("2026-06-16")), "June", isCurrentMonth = true) }
        val ahead = insight(68_000)
        assertEquals(PacePosition.AHEAD, ahead.position)
        assertEquals("Ahead of the month. Let’s slow down!", ahead.bubble)
        assertEquals("Spending faster than the month", ahead.message)
        assertTrue(ahead.detail!!.endsWith("a day keeps you within budget")) // the daily allowance from the same plan
        assertEquals("On track! Keep it up!", insight(55_000).bubble)
        assertEquals("Spending slower than the month", insight(30_000).message)
        val over = insight(112_500)
        assertEquals("Over budget. Let’s regroup!", over.bubble)
        // 16 June through 30 June, today included: the same 15 days the allowance divides by.
        assertEquals(15, BudgetPlanner.burn(
            BudgetPlanner.plan(listOf(food), budget, listOf(spend(112_500, "2026-06-16")), "USD", YearMonth.of(2026, 6)),
            LocalDate.parse("2026-06-16"),
        ).daysLeft)
        assertTrue(over.message, over.message.contains("$125.00 over your $1,000.00 budget with 15 days left"))
    }

    @Test fun daysLeftLabelCountsTodayOnTheFirstMidAndLastDayAndAcrossMonthBoundaries() {
        fun overOn(date: String, month: YearMonth = YearMonth.from(LocalDate.parse(date))): String {
            val plan = BudgetPlanner.plan(listOf(food), budget, listOf(spend(112_500, date)), "USD", month)
            val burn = BudgetPlanner.burn(plan, LocalDate.parse(date))
            return PaceCopy.describe(plan, burn, "Month", isCurrentMonth = month == YearMonth.from(LocalDate.parse(date))).message
        }
        assertTrue(overOn("2026-06-01").contains("with 30 days left"))
        assertTrue(overOn("2026-06-16").contains("with 15 days left"))
        assertTrue(overOn("2026-06-30").contains("with 1 day left"))
        assertEquals("1 day", daysLeftLabel(1))
        assertEquals("0 days", daysLeftLabel(0))

        // Non-leap February, leap February, and the December/January boundary. The allowance uses this same count.
        fun days(date: String, month: YearMonth = YearMonth.from(LocalDate.parse(date))) =
            BudgetPlanner.burn(
                BudgetPlanner.plan(listOf(food), budget, emptyList(), "USD", month),
                LocalDate.parse(date),
            ).daysLeft
        assertEquals(28, days("2026-02-01"))
        assertEquals(1, days("2026-02-28"))
        assertEquals(29, days("2028-02-01"))
        assertEquals(1, days("2028-02-29"))
        assertEquals(1, days("2026-12-31"))
        assertEquals(0, days("2027-01-01", YearMonth.of(2026, 12))) // December is finished
        assertEquals(31, days("2027-01-01"))
    }

    @Test fun pastMonthIsDescribedAsFinished() {
        val plan = BudgetPlanner.plan(listOf(food), budget, listOf(spend(91_000, "2026-05-10")), "USD", YearMonth.of(2026, 5))
        val burn = BudgetPlanner.burn(plan, LocalDate.parse("2026-06-16"))
        assertEquals(100, burn.elapsedPercent)
        assertEquals(31, burn.daysElapsed)
        assertEquals("May ended at 91% of budget, with $90.00 unused.", PaceCopy.describe(plan, burn, "May", isCurrentMonth = false).message)
    }

    // ---- month selection

    @Test fun monthIsClampedToMonthsWithData() {
        val today = LocalDate.parse("2026-06-16")
        val wallet = Wallet(1, "Bank", WalletType.BANK, Money(0, "USD"), 0L)
        val history = listOf(spend(1_000, "2026-04-03"), spend(2_000, "2026-06-01"))
        fun build(month: YearMonth) = HomeStateBuilder.build(history, listOf(wallet), listOf(food), "USD", today, budgets = budget, month = month)

        val april = build(YearMonth.of(2026, 4))
        assertEquals(YearMonth.of(2026, 4), april.month)
        assertEquals(YearMonth.of(2026, 4), april.earliestMonth)
        assertFalse(april.canGoToPreviousMonth)
        assertTrue(april.canGoToNextMonth)
        assertFalse(april.isCurrentMonth)
        assertEquals(Money(1_000, "USD"), april.budget!!.spent) // the plan follows the chosen month

        assertEquals(YearMonth.of(2026, 4), build(YearMonth.of(2025, 1)).month) // before the first transaction
        val future = build(YearMonth.of(2026, 9))
        assertEquals(YearMonth.of(2026, 6), future.month) // never past the current month
        assertTrue(future.isCurrentMonth)
        assertFalse(future.canGoToNextMonth)
        // The selector lists this month back to the first month with data, newest first.
        assertEquals(listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 5), YearMonth.of(2026, 4)), future.availableMonths)
    }

    @Test fun netWorthChangeIsThisMonthsIncomeMinusSpending() {
        val today = LocalDate.parse("2026-06-16")
        val wallet = Wallet(1, "Bank", WalletType.BANK, Money(500_000, "USD"), 0L)
        val pay = Transaction(nextId++, TransactionType.INCOME, Money(300_000, "USD"), 1L, null, LocalDate.parse("2026-06-01"), "", 0L)
        val history = listOf(pay, spend(120_000, "2026-06-05"), spend(9_999, "2026-05-20"))
        val state = HomeStateBuilder.build(history, listOf(wallet), listOf(food), "USD", today, month = YearMonth.of(2026, 5))
        // Always the current calendar month, whatever month the budget card shows; last month's spending is excluded.
        assertEquals(Money(180_000, "USD"), state.netWorthMonthChange)
        assertNull(HomeStateBuilder.build(emptyList(), listOf(wallet), listOf(food), "USD", today).netWorthMonthChange)
    }

    // ---- sync status

    @Test fun syncStatusIsLocalUnlessTheUserOptedIn() {
        fun of(configured: Boolean = true, signedIn: Boolean = true, enabled: Boolean = true, error: Boolean = false, ever: Boolean = true, pending: Int = 0, online: Boolean = true) =
            CloudStatus.of(configured, signedIn, enabled, error, ever, pending, online)
        assertEquals(CloudStatus.LOCAL, of(configured = false))
        assertEquals(CloudStatus.LOCAL, of(signedIn = false))
        assertEquals(CloudStatus.LOCAL, of(enabled = false, error = true, online = false))
        assertEquals(CloudStatus.SYNCED, of())
        assertEquals(CloudStatus.PENDING, of(pending = 3))
        assertEquals(CloudStatus.PENDING, of(ever = false))
        assertEquals(CloudStatus.ERROR, of(error = true, pending = 2))
        assertEquals(CloudStatus.OFFLINE, of(online = false, error = true, pending = 2))
    }

    // ---- net worth on Home

    @Test fun netWorthBreakdownMatchesTheSummaryAndKeepsCurrenciesApart() {
        val wallets = listOf(
            Wallet(1, "Bank", WalletType.BANK, Money(300_000, "USD"), 0L),
            Wallet(2, "Car", WalletType.OTHER_ASSET, Money(1_200_000, "USD"), 0L),
            Wallet(3, "Loan", WalletType.LOAN, Money(500_000, "USD"), 0L),
            Wallet(4, "Euro", WalletType.CASH, Money(10_000, "EUR"), 0L),
        )
        val state = HomeStateBuilder.build(emptyList(), wallets, emptyList(), "USD", LocalDate.parse("2026-06-16"))
        val c = state.netWorthComposition!!
        assertEquals(Money(1_500_000, "USD"), c.totalAssets)
        assertEquals(Money(500_000, "USD"), c.totalLiabilities)
        assertEquals(Money(1_000_000, "USD"), c.netWorth)
        assertEquals(state.netWorth!!.totalFor("USD"), c.netWorth)
        assertEquals(listOf(Money(10_000, "EUR")), state.otherNetWorth)
    }

    // ---- featured savings goal

    private fun goal(id: Long, target: Long, date: String? = null, created: Long = id) =
        Goal(id, "Goal $id", Money(target, "USD"), date?.let(LocalDate::parse), null, created)

    private fun saved(goal: Long, minor: Long) = GoalContribution(goal * 100, goal, Money(minor, "USD"), LocalDate.parse("2026-06-01"), "")

    @Test fun featuredGoalIsTheInProgressGoalClosestToDone() {
        val goals = listOf(goal(1, 100_000), goal(2, 50_000), goal(3, 10_000))
        val contributions = listOf(saved(1, 30_000), saved(2, 40_000), saved(3, 10_000)) // 30%, 80%, reached
        val top = GoalsSummary.overview(goals, contributions, "USD", LocalDate.parse("2026-06-16"))!!.top!!
        assertEquals(2L, top.goal.id)
        assertEquals(80, top.percent)
    }

    @Test fun tiesGoToTheEarlierTargetDate() {
        val goals = listOf(goal(1, 100_000, "2027-01-01"), goal(2, 100_000, "2026-12-01"))
        val contributions = listOf(saved(1, 50_000), saved(2, 50_000))
        assertEquals(2L, GoalsSummary.overview(goals, contributions, "USD", LocalDate.parse("2026-06-16"))!!.top!!.goal.id)
    }

    @Test fun whenEveryGoalIsReachedTheNewestIsFeatured() {
        val goals = listOf(goal(1, 10_000, created = 5), goal(2, 10_000, created = 9))
        val contributions = listOf(saved(1, 10_000), saved(2, 12_000))
        val top = GoalsSummary.overview(goals, contributions, "USD", LocalDate.parse("2026-06-16"))!!.top!!
        assertEquals(2L, top.goal.id)
        assertTrue(top.reached)
    }

    @Test fun noGoalsInTheCurrencyMeansNoFeaturedGoal() {
        assertNull(GoalsSummary.overview(listOf(Goal(1, "Trip", Money(1_000, "EUR"), null, null, 0)), emptyList(), "USD", LocalDate.parse("2026-06-16")))
    }
}
