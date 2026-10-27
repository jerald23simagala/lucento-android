package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * End-to-end money semantics across calculators (audit finding 1.6): transfers, loans, card repayments,
 * goal earmarks, refunds and period boundaries.
 */
class MoneyFlowAuditTest {

    private var nextId = 1L
    private fun tx(type: TransactionType, minor: Long, wallet: Long, date: String, category: Long? = null, group: String? = null) =
        Transaction(nextId++, type, Money(minor, "PHP"), wallet, category, LocalDate.parse(date), "", 0, group)

    private fun wallet(id: Long, type: WalletType, opening: Long, txs: List<Transaction>): Wallet {
        val own = txs.filter { it.walletId == id }
        fun sum(t: TransactionType) = own.filter { it.type == t }.sumOf { it.amount.amountMinor }
        val opened = Money(opening, "PHP")
        return Wallet(
            id, "w$id", type,
            WalletBalanceCalculator.currentBalance(
                opened, type,
                sum(TransactionType.INCOME), sum(TransactionType.EXPENSE),
                sum(TransactionType.TRANSFER_IN), sum(TransactionType.TRANSFER_OUT),
            ),
            0, opened,
        )
    }

    private val bank = 1L
    private val card = 2L
    private val loan = 3L
    private val month = YearMonth.of(2026, 10)

    @Test fun cardPurchaseThenRepaymentIsSpentOnceAndNetWorthIsConsistent() {
        val txs = listOf(
            tx(TransactionType.EXPENSE, 10_000, card, "2026-10-03"),                     // purchase on the card
            tx(TransactionType.TRANSFER_OUT, 10_000, bank, "2026-10-20", group = "pay"), // repay principal
            tx(TransactionType.TRANSFER_IN, 10_000, card, "2026-10-20", group = "pay"),
            tx(TransactionType.EXPENSE, 350, card, "2026-10-25"),                        // interest is an expense
        )
        val flow = CashflowCalculator.netFlow("PHP", month, txs)
        assertEquals(Money(10_350, "PHP"), flow.expense)
        assertEquals(Money.zero("PHP"), flow.income)
        val wallets = listOf(wallet(bank, WalletType.BANK, 50_000, txs), wallet(card, WalletType.CREDIT_CARD, 0, txs))
        assertEquals(Money(40_000, "PHP"), wallets[0].balance)
        assertEquals(Money(350, "PHP"), wallets[1].balance) // still owed: the interest
        // Net worth falls by exactly what was spent, never twice.
        assertEquals(Money(50_000 - 10_350, "PHP"), NetWorthCalculator.summarize(wallets, "PHP").totalFor("PHP"))
    }

    @Test fun loanProceedsAreNotIncomeAndDoNotChangeNetWorth() {
        val txs = listOf(
            tx(TransactionType.TRANSFER_OUT, 200_000, loan, "2026-10-01", group = "draw"),
            tx(TransactionType.TRANSFER_IN, 200_000, bank, "2026-10-01", group = "draw"),
        )
        assertEquals(Money.zero("PHP"), CashflowCalculator.netFlow("PHP", month, txs).income)
        val wallets = listOf(wallet(bank, WalletType.BANK, 0, txs), wallet(loan, WalletType.LOAN, 0, txs))
        assertEquals(Money(200_000, "PHP"), wallets[1].balance)
        assertEquals(Money.zero("PHP"), NetWorthCalculator.summarize(wallets, "PHP").totalFor("PHP"))
    }

    @Test fun goalContributionsNeverChangeBalancesOrNetWorth() {
        val txs = listOf(tx(TransactionType.INCOME, 30_000, bank, "2026-10-01"))
        val before = NetWorthCalculator.summarize(listOf(wallet(bank, WalletType.BANK, 0, txs)), "PHP").totalFor("PHP")
        val goal = Goal(1, "Trip", Money(100_000, "PHP"), null, bank, 0)
        val contributions = listOf(GoalContribution(1, 1, Money(20_000, "PHP"), LocalDate.parse("2026-10-02"), ""))
        val progress = GoalCalculator.progress(goal, contributions, LocalDate.parse("2026-10-22"))
        assertEquals(Money(20_000, "PHP"), progress.saved)
        // Contributions are earmarks: the same transactions give the same balance and net worth.
        val after = NetWorthCalculator.summarize(listOf(wallet(bank, WalletType.BANK, 0, txs)), "PHP").totalFor("PHP")
        assertEquals(before, after)
        assertEquals(Money(30_000, "PHP"), after)
    }

    @Test fun refundsAreIncomeAndAreNotNettedAgainstBudgets() {
        val food = 7L
        val txs = listOf(
            tx(TransactionType.EXPENSE, 4_000, bank, "2026-10-05", category = food),
            tx(TransactionType.INCOME, 1_000, bank, "2026-10-06", category = 99), // refund recorded as income
        )
        val budget = Budget(1, food, Money(5_000, "PHP"))
        assertEquals(Money(4_000, "PHP"), BudgetCalculator.spent(budget, txs, month))
        assertEquals(Money(-3_000, "PHP"), CashflowCalculator.netFlow("PHP", month, txs).net)
    }

    @Test fun budgetMonthBoundariesUseCalendarDates() {
        val txs = listOf(
            tx(TransactionType.EXPENSE, 100, bank, "2026-09-30", category = 7),
            tx(TransactionType.EXPENSE, 200, bank, "2026-10-01", category = 7),
            tx(TransactionType.EXPENSE, 300, bank, "2026-10-31", category = 7),
            tx(TransactionType.EXPENSE, 400, bank, "2026-11-01", category = 7),
            tx(TransactionType.TRANSFER_OUT, 999, bank, "2026-10-15", category = 7, group = "g"),
        )
        val food = Budget(1, 7, Money(1_000, "PHP"))
        val categories = listOf(Category(7, "Food", CategoryKind.EXPENSE, "groceries"))
        assertEquals(Money(500, "PHP"), BudgetCalculator.spent(food, txs, month))
        assertEquals(Money(400, "PHP"), BudgetCalculator.spent(food, txs, YearMonth.of(2026, 11)))
        assertEquals(Money(500, "PHP"), BudgetPlanner.plan(categories, listOf(food), txs, "PHP", month).spent)
        // February in a leap year ends on the 29th.
        val feb = listOf(tx(TransactionType.EXPENSE, 50, bank, "2028-02-29", category = 7))
        assertEquals(Money(50, "PHP"), BudgetCalculator.spent(food, feb, YearMonth.of(2028, 2)))
    }

    @Test fun otherCurrenciesNeverLeakIntoTotals() {
        val txs = listOf(
            tx(TransactionType.EXPENSE, 1_000, bank, "2026-10-05"),
            Transaction(nextId++, TransactionType.EXPENSE, Money(5, "USD"), 9, null, LocalDate.parse("2026-10-05"), "", 0),
        )
        assertEquals(Money(1_000, "PHP"), CashflowCalculator.expensesIn(txs, "PHP", month))
        assertEquals(Money(5, "USD"), CashflowCalculator.expensesIn(txs, "USD", month))
    }

    @Test fun overflowThrowsInsteadOfWrapping() {
        val big = Money(Long.MAX_VALUE, "PHP")
        assertTrue(runCatching { big + Money(1, "PHP") }.exceptionOrNull() is ArithmeticException)
        assertTrue(runCatching { -Money(Long.MIN_VALUE, "PHP") }.exceptionOrNull() is ArithmeticException)
        assertTrue(runCatching { Money(1, "PHP") + Money(1, "USD") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun safeToSpendIgnoresTransfersAndSavings() {
        val savings = 4L
        val txs = listOf(
            tx(TransactionType.TRANSFER_OUT, 20_000, bank, "2026-10-10", group = "s"),
            tx(TransactionType.TRANSFER_IN, 20_000, savings, "2026-10-10", group = "s"),
        )
        val wallets = listOf(wallet(bank, WalletType.BANK, 50_000, txs), wallet(savings, WalletType.SAVINGS, 0, txs))
        val safe = CashflowCalculator.safeToSpend("PHP", LocalDate.parse("2026-10-22"), txs, wallets, emptyList(), emptyList())!!
        assertEquals(SafeToSpend.Basis.BALANCES, safe.basis)
        assertEquals(Money(30_000, "PHP"), safe.amount) // money moved to savings is no longer spendable
        assertEquals(Money.zero("PHP"), safe.spentThisMonth)
    }
}
