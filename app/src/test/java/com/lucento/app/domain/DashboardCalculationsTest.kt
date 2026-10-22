package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Savings level, net worth composition, income/expense trends and upcoming payments. */
class DashboardCalculationsTest {

    private val today = LocalDate.parse("2026-10-22")
    private var nextId = 1L

    // ---------------------------------------------------------------- savings goals

    private fun goal(id: Long, target: Long, code: String = "PHP", date: String? = null) =
        Goal(id, "G$id", Money(target, code), date?.let(LocalDate::parse), null, id)

    private fun put(goal: Long, minor: Long, code: String = "PHP") =
        GoalContribution(nextId++, goal, Money(minor, code), LocalDate.parse("2026-09-01"), "")

    @Test fun savingsLevelCapsEachGoalAndIgnoresOtherCurrencies() {
        val goals = listOf(goal(1, 10_000), goal(2, 30_000), goal(3, 5_000), goal(4, 1_000, code = "USD"))
        val contributions = listOf(put(1, 12_000), put(2, 6_000), put(3, -500), put(4, 900, code = "USD"))
        val o = GoalsSummary.overview(goals, contributions, "PHP", today)!!
        assertEquals(3, o.goalCount)
        assertEquals(1, o.reachedCount)
        // min(12,000, 10,000) + 6,000 + max(−500, 0) = 16,000 of 45,000.
        assertEquals(Money(16_000, "PHP"), o.saved)
        assertEquals(Money(45_000, "PHP"), o.target)
        assertEquals(36, o.percent)
        assertEquals(2L, o.closest!!.goal.id) // 20% beats 0%; goal 1 is already reached
        assertEquals(1, o.otherCurrencyGoals)
    }

    @Test fun noGoalsInCurrencyGivesNoLevel() {
        assertNull(GoalsSummary.overview(emptyList(), emptyList(), "PHP", today))
        assertNull(GoalsSummary.overview(listOf(goal(1, 100, code = "USD")), emptyList(), "PHP", today))
    }

    @Test fun goalsAreNeverCountedInNetWorth() {
        val wallets = listOf(Wallet(1, "Savings", WalletType.SAVINGS, Money(50_000, "PHP"), 0L))
        val before = NetWorthComposer.compose(wallets, "PHP").netWorth
        // Earmarking 20,000 of the savings account for a goal does not add an asset.
        GoalsSummary.overview(listOf(goal(1, 30_000)), listOf(put(1, 20_000)), "PHP", today)
        assertEquals(before, NetWorthComposer.compose(wallets, "PHP").netWorth)
        assertEquals(Money(50_000, "PHP"), before)
    }

    // ---------------------------------------------------------------- net worth composition

    private fun wallet(type: WalletType, minor: Long, code: String = "PHP") = Wallet(nextId++, "W", type, Money(minor, code), 0L)

    @Test fun compositionAgreesWithSummaryAndGroupsByType() {
        val wallets = listOf(
            wallet(WalletType.BANK, 40_000), wallet(WalletType.BANK, 10_000), wallet(WalletType.CASH, 2_000),
            wallet(WalletType.SAVINGS, 30_000), wallet(WalletType.CREDIT_CARD, 7_000), wallet(WalletType.LOAN, 100_000),
            wallet(WalletType.BANK, 999, code = "USD"),
        )
        val c = NetWorthComposer.compose(wallets, "PHP")
        assertEquals(listOf(WalletType.BANK, WalletType.SAVINGS, WalletType.CASH), c.assets.map { it.type })
        assertEquals(2, c.assets.first().walletCount)
        assertEquals(Money(82_000, "PHP"), c.totalAssets)
        assertEquals(Money(107_000, "PHP"), c.totalLiabilities)
        assertEquals(Money(-25_000, "PHP"), c.netWorth) // negative net worth is shown, not hidden
        assertEquals(NetWorthCalculator.summarize(wallets, "PHP").totalFor("PHP"), c.netWorth)
        assertEquals(listOf("PHP", "USD"), NetWorthComposer.currencies(wallets, "PHP"))
    }

    @Test fun compositionHandlesZeroAndNoData() {
        val empty = NetWorthComposer.compose(emptyList(), "PHP")
        assertTrue(empty.isEmpty)
        assertEquals(Money.zero("PHP"), empty.netWorth)
        val zero = NetWorthComposer.compose(listOf(wallet(WalletType.CASH, 0)), "PHP")
        assertFalse(zero.isEmpty)
        assertEquals(Money.zero("PHP"), zero.netWorth)
    }

    // ---------------------------------------------------------------- trends

    private fun tx(type: TransactionType, minor: Long, date: String, code: String = "PHP", group: String? = null) =
        Transaction(nextId++, type, Money(minor, code), 1, null, LocalDate.parse(date), "", 0L, group)

    @Test fun incomeAndExpenseTrendExcludesTransfersAndOtherCurrencies() {
        val txs = listOf(
            tx(TransactionType.INCOME, 50_000, "2026-10-01"),
            tx(TransactionType.EXPENSE, 20_000, "2026-10-05"),
            tx(TransactionType.TRANSFER_OUT, 9_000, "2026-10-06", group = "t"),
            tx(TransactionType.TRANSFER_IN, 9_000, "2026-10-06", group = "t"),
            tx(TransactionType.EXPENSE, 7_000, "2026-08-31"),
            tx(TransactionType.INCOME, 1_000, "2026-10-02", code = "USD"),
        )
        val trend = Insights.incomeExpenseTrend(txs, "PHP", YearMonth.of(2026, 10), 3)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), trend.map { it.month })
        assertEquals(Money(7_000, "PHP"), trend[0].expense)
        assertTrue(trend[1].income.isZero && trend[1].expense.isZero)
        assertEquals(Money(50_000, "PHP"), trend[2].income)
        assertEquals(Money(20_000, "PHP"), trend[2].expense)
        assertEquals(Money(30_000, "PHP"), trend[2].net)
    }

    // ---------------------------------------------------------------- upcoming recurring payments

    private fun schedule(id: Long, start: String, paused: Boolean = false, next: Int = 0, end: String? = null) = RecurringSchedule(
        id, "S$id", TransactionType.EXPENSE, Money(1_000, "PHP"), 1, null, Frequency.MONTHLY, 1,
        LocalDate.parse(start), end?.let(LocalDate::parse), next, paused,
    )

    @Test fun upcomingPaymentsAreChronologicalWithOverdueFlaggedAndLimited() {
        val list = listOf(
            schedule(1, "2026-11-15"),
            schedule(2, "2026-10-01"), // overdue (Oct 1 not yet paid)
            schedule(3, "2026-10-25"),
            schedule(4, "2026-10-23", paused = true),
            schedule(5, "2026-12-30"), // beyond the 30-day horizon
            schedule(6, "2026-09-10", next = 2), // index 2 = Nov 10
            schedule(7, "2026-01-05", end = "2026-03-05", next = 3), // finished
        )
        val upcoming = RecurringMath.upcomingPayments(list, today)
        assertEquals(listOf(2L, 3L, 6L, 1L), upcoming.map { it.schedule.id })
        assertEquals(listOf(true, false, false, false), upcoming.map { it.overdue })
        assertEquals(LocalDate.parse("2026-11-10"), upcoming[2].dueDate)
        assertEquals(2, RecurringMath.upcomingPayments(list, today, limit = 2).size)
    }

    @Test fun markingPaidAdvancesTheScheduleSoTheNextDateAppears() {
        val s = schedule(1, "2026-10-20")
        assertEquals(LocalDate.parse("2026-10-20"), RecurringMath.upcomingPayments(listOf(s), today).single().dueDate)
        // The repository advances nextIndex in the same transaction that records the payment.
        val paid = s.copy(nextIndex = 1)
        val next = RecurringMath.upcomingPayments(listOf(paid), today).single()
        assertEquals(LocalDate.parse("2026-11-20"), next.dueDate)
        assertFalse(next.overdue)
    }
}
