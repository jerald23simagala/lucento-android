package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The reason under Safe to spend (Home's card, the Budget tile and the widget share it) follows its cause — QA,
 * 2026-11-17: an empty or overdrawn wallet with nothing due said "What's due takes your money on hand".
 */
class SafeToSpendCaptionTest {

    private val today = LocalDate.parse("2026-10-22")
    private fun php(minor: Long) = Money(minor, "PHP")
    private val food = Category(5, "Food", CategoryKind.EXPENSE, "groceries")
    private val budget = listOf(Budget(1, 5, php(20_000_00)))
    private val spent = Transaction(100, TransactionType.EXPENSE, php(5_000_00), 1, 5, LocalDate.parse("2026-10-10"), "", 0L)

    private fun formula(wallet: Wallet, schedules: List<RecurringSchedule> = emptyList()): BudgetFormula {
        val safe = CashflowCalculator.safeToSpend("PHP", today, listOf(spent), listOf(wallet), budget, schedules, listOf(food))
        val plan = BudgetPlanner.plan(listOf(food), budget, listOf(spent), "PHP", YearMonth.from(today))
        return HomeStateBuilder.budgetFormula(plan, safe)
    }

    @Test fun `each cause has its own reason`() {
        assertEquals(BudgetFormula.CAPTION_MONEY_EMPTY, BudgetFormula.caption(limitedByMoney = true, moneyEmpty = true, nothingSafe = true, overspent = false))
        assertEquals(BudgetFormula.CAPTION_MONEY_TAKEN, BudgetFormula.caption(limitedByMoney = true, moneyEmpty = false, nothingSafe = true, overspent = false))
        assertEquals(BudgetFormula.CAPTION_MONEY_LIMITS, BudgetFormula.caption(limitedByMoney = true, moneyEmpty = false, nothingSafe = false, overspent = false))
        assertNull(BudgetFormula.caption(limitedByMoney = false, moneyEmpty = false, nothingSafe = false, overspent = false))
        assertEquals(BudgetFormula.CAPTION_OVER_BUDGET, BudgetFormula.caption(limitedByMoney = false, moneyEmpty = false, nothingSafe = true, overspent = true))
        assertEquals(BudgetFormula.CAPTION_BILLS, BudgetFormula.caption(limitedByMoney = false, moneyEmpty = false, nothingSafe = true, overspent = false))
        assertEquals(
            BudgetFormula.CAPTION_BUDGET_USED,
            BudgetFormula.caption(limitedByMoney = false, moneyEmpty = false, nothingSafe = true, overspent = false, budgetUsedUp = true),
        )
    }

    @Test fun `a budget spent to the last peso says so, even with bills still due`() {
        // QA 2026-11-18: the widget said "Bills due take what's left" with nothing left before them.
        val bank = Wallet(1, "Bank", WalletType.BANK, php(90_000_00), 0L)
        val used = Transaction(101, TransactionType.EXPENSE, php(15_000_00), 1, 5, LocalDate.parse("2026-10-11"), "", 0L)
        val safe = CashflowCalculator.safeToSpend("PHP", today, listOf(spent, used), listOf(bank), budget, emptyList(), listOf(food))
        val plan = BudgetPlanner.plan(listOf(food), budget, listOf(spent, used), "PHP", YearMonth.from(today))
        assertEquals(BudgetFormula.CAPTION_BUDGET_USED, HomeStateBuilder.budgetFormula(plan, safe).safeCaption)
        val rent = RecurringSchedule(
            9, "Rent", TransactionType.EXPENSE, php(1_000_00), 1L, 5L, Frequency.MONTHLY, 1, LocalDate.parse("2026-10-28"), null, 0, false,
        )
        val billed = CashflowCalculator.safeToSpend("PHP", today, listOf(spent, used), listOf(bank), budget, listOf(rent), listOf(food))
        assertEquals(BudgetFormula.CAPTION_BUDGET_USED, HomeStateBuilder.budgetFormula(plan, billed).safeCaption)
    }

    @Test fun `an overdrawn wallet with nothing due says it shows no money, not that something is due`() {
        // It started with 2,000, so its balance is recorded money; nothing is scheduled.
        val overdrawn = Wallet(1, "Cash", WalletType.CASH, php(-3_000_00), 0L, openingBalance = php(2_000_00))
        val f = formula(overdrawn)
        assertEquals(true, f.limitedByMoney)
        assertEquals(BudgetFormula.CAPTION_MONEY_EMPTY, f.safeCaption)
    }

    @Test fun `money there but due elsewhere says what's due takes it, and a little left says it limits`() {
        val bank = Wallet(1, "Bank", WalletType.BANK, php(4_000_00), 0L)
        val rent = RecurringSchedule(
            9, "Rent", TransactionType.EXPENSE, php(6_000_00), 1L, 5L, Frequency.MONTHLY, 1, LocalDate.parse("2026-10-28"), null, 0, false,
        )
        assertEquals(BudgetFormula.CAPTION_MONEY_TAKEN, formula(bank, listOf(rent)).safeCaption)
        assertEquals(BudgetFormula.CAPTION_MONEY_LIMITS, formula(bank).safeCaption)
    }
}
