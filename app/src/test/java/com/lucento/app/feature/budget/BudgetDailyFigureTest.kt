package com.lucento.app.feature.budget

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.fx.FxSource
import com.lucento.app.domain.fx.FxTranslation
import com.lucento.app.feature.home.HomeStateBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
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
        // As BudgetScreen does: the plan and the per-day figure both read the translated ledger.
        val ledger = FxTranslation.translate("USD", wallets, transactions)
        val plan = BudgetPlanner.plan(categories, budgets, ledger.transactions, "USD", month)
        return budgetDailyFigure(plan, BudgetPlanner.burn(plan, today), today, ledger, budgets, schedules, categories)
    }

    // Multicurrency (audit D1, 2026-11-16): main currency PHP and a yen wallet with a rate. Its spending counts toward the
    // peso budget at its own value, in the hero and in Safe per day alike, as on Home.
    private val fxToday = LocalDate.parse("2026-11-16") // 15 days left including today
    private val pesoBank = Wallet(1, "Bank", WalletType.BANK, Money(1_000_000, "PHP"), 0L)
    private val yenCash = Wallet(
        2, "Yen", WalletType.CASH, Money(40_000, "JPY"), 0L,
        openingBalance = Money(50_000, "JPY"), fxBasisRate = BigDecimal("0.38"),
    )
    private val pesoBudget = listOf(Budget(1, 11, Money(1_000_000, "PHP"))) // ₱10,000 for Groceries

    private fun pesos(minor: Long, date: String) =
        Transaction(nextId++, TransactionType.EXPENSE, Money(minor, "PHP"), 1L, 11L, LocalDate.parse(date), "", 0L)

    /** Yen spending recorded with its value in pesos at the day's rate (0.38, the wallet's own, so no FX result). */
    private fun yen(amount: Long, pesoMinor: Long, date: String) = Transaction(
        nextId++, TransactionType.EXPENSE, Money(amount, "JPY"), 2L, 11L, LocalDate.parse(date), "", 0L,
        baseAmount = Money(pesoMinor, "PHP"), fxSource = FxSource.MARKET,
    )

    private fun foreignFigures(entries: List<Transaction>): Pair<BudgetPlan, DailyFigure?> {
        val ledger = FxTranslation.translate("PHP", listOf(pesoBank, yenCash), entries)
        val plan = BudgetPlanner.plan(categories, pesoBudget, ledger.transactions, "PHP", YearMonth.from(fxToday))
        return plan to budgetDailyFigure(plan, BudgetPlanner.burn(plan, fxToday), fxToday, ledger, pesoBudget, emptyList(), categories)
    }

    @Test fun foreignWalletSpendingCountsInSafePerDayAsInTheHeroAndOnHome() {
        // ₱6,000 from the bank + ¥10,000 worth ₱3,800 = ₱9,800 spent, so ₱200 left over 15 days = ₱13.33 a day.
        val entries = listOf(pesos(600_000, "2026-11-05"), yen(10_000, 380_000, "2026-11-10"))
        val (plan, daily) = foreignFigures(entries)
        assertEquals(Money(980_000, "PHP"), plan.spent)
        assertEquals(Money(20_000, "PHP"), plan.remaining)
        val safe = daily as DailyFigure.SafePerDay
        // Leaving the yen spending out gave ₱4,000 over 15 days = ₱266.66 a day beside a hero that said ₱200 remaining.
        assertEquals(Money(1_333, "PHP"), safe.perDay)
        assertEquals(15, safe.daysLeft)
        val home = HomeStateBuilder.build(entries, listOf(pesoBank, yenCash), categories, "PHP", fxToday, pesoBudget)
        assertEquals(home.safeToSpend!!.perDay, safe.perDay)
        assertEquals(home.budget!!.remaining, plan.remaining)
    }

    @Test fun foreignWalletSpendingThatTakesTheMonthOverLeavesNothingToSpreadPerDay() {
        // ₱6,000 + ¥15,000 worth ₱5,700 = ₱11,700: ₱1,700 over, which the hero shows as Overspent.
        val (plan, daily) = foreignFigures(listOf(pesos(600_000, "2026-11-05"), yen(15_000, 570_000, "2026-11-10")))
        assertEquals(Money(-170_000, "PHP"), plan.remaining)
        assertEquals(Money(-170_000, "PHP"), (daily as DailyFigure.NothingLeft).safe)
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
