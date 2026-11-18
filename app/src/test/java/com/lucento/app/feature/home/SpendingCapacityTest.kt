package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.CashflowCalculator
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.fx.FxTranslation
import com.lucento.app.feature.budget.budgetSafeToSpend
import com.lucento.app.feature.home.SpendingCapacity.Limit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Monthly budget card's two limits and its verdict (owner's redesign, 2026-11-18; docs/CALCULATIONS.md "Safe to
 * spend"), read from the real calculators: Budget remaining = budget - spent - upcoming expenses; Spendable wallets =
 * the spending wallets less what leaves them by the month's end; Safe to spend = the lower, never shown below zero.
 */
class SpendingCapacityTest {

    private val today = LocalDate.parse("2026-10-22") // 10 days left including today; month end 2026-10-31
    private fun php(minor: Long) = Money(minor, "PHP")

    private val food = Category(5, "Food", CategoryKind.EXPENSE, "groceries")
    private val categories = listOf(food)
    private val budgets = listOf(Budget(1, 5, php(20_000_00)))

    private val bank = Wallet(1, "Bank", WalletType.BANK, php(50_000_00), 0L)
    private val gcash = Wallet(2, "GCash", WalletType.E_WALLET, php(2_340_00), 0L)
    private val savings = Wallet(4, "Savings", WalletType.SAVINGS, php(48_000_00), 0L)
    private val mp2 = Wallet(5, "MP2", WalletType.INVESTMENT, php(152_300_00), 0L)

    private var nextId = 100L
    private fun spent(minor: Long, wallet: Long = 1, date: String = "2026-10-10") =
        Transaction(nextId++, TransactionType.EXPENSE, php(minor), wallet, 5, LocalDate.parse(date), "", 0L)

    private fun bill(minor: Long, wallet: Long = 1, start: String = "2026-10-28") =
        RecurringSchedule(nextId++, "Bill", TransactionType.EXPENSE, php(minor), wallet, 5, Frequency.MONTHLY, 1, LocalDate.parse(start), null, 0, false)

    private fun transfer(minor: Long, from: Long, to: Long, start: String = "2026-10-30") = RecurringSchedule(
        nextId++, "Transfer", TransactionType.TRANSFER_OUT, php(minor), from, null, Frequency.MONTHLY, 1, LocalDate.parse(start), null, 0, false,
        toWalletId = to,
    )

    private fun capacity(
        wallets: List<Wallet>,
        transactions: List<Transaction> = emptyList(),
        schedules: List<RecurringSchedule> = emptyList(),
    ): SpendingCapacity {
        val safe = CashflowCalculator.safeToSpend("PHP", today, transactions, wallets, budgets, schedules, categories)!!
        return SpendingCapacity.of(safe)!!
    }

    // ---------------------------------------------------------------- Budget remaining

    @Test fun `budget remaining is the budget less what is spent and what is due, shown with its two parts`() {
        val c = capacity(listOf(bank), listOf(spent(5_000_00)), listOf(bill(3_000_00)))
        assertEquals(php(15_000_00), c.remainingBeforeUpcoming) // 20,000 - 5,000
        assertEquals(php(3_000_00), c.upcomingExpenses)
        assertEquals(php(12_000_00), c.budgetRemaining) // 20,000 - 5,000 - 3,000
        // The breakdown always adds up to the figure it explains.
        assertEquals(c.budgetRemaining, c.remainingBeforeUpcoming - c.upcomingExpenses)
        // Plenty in the bank: the budget sets Safe to spend, spread over the 10 days left.
        assertEquals(Limit.BUDGET, c.limit)
        assertEquals(php(12_000_00), c.safeToSpend)
        assertEquals(php(1_200_00), c.perDay)
        assertEquals(10, c.daysLeft)
        assertEquals(SpendingCapacity.LIMITED_BY_BUDGET, c.headline)
        assertEquals(SpendingCapacity.LOWER_OF_BOTH, c.detail)
    }

    // ---------------------------------------------------------------- Spendable wallets

    @Test fun `spendable wallets are what the spending wallets hold less what leaves them, savings and investments left out`() {
        val bank9 = bank.copy(balance = php(9_120_00))
        val c = capacity(
            listOf(bank9, gcash, savings, mp2),
            listOf(spent(5_000_00)),
            listOf(bill(3_000_00), transfer(1_000_00, from = 1, to = 4)),
        )
        val wallets = c.wallets!!
        assertEquals(php(11_460_00), wallets.total) // 9,120 + 2,340; savings and MP2 aren't spending money
        assertEquals(php(4_000_00), wallets.deducted) // the bill and the transfer into savings
        assertEquals(php(7_460_00), wallets.allows)
        assertEquals(Limit.WALLETS, c.limit)
        assertEquals(php(7_460_00), c.safeToSpend)
        assertEquals(SpendingCapacity.LIMITED_BY_WALLETS, c.headline)
    }

    @Test fun `an upcoming transfer into savings comes off the wallets only, never the budget`() {
        val bank9 = bank.copy(balance = php(9_120_00))
        val without = capacity(listOf(bank9, savings), listOf(spent(5_000_00)))
        val with = capacity(listOf(bank9, savings), listOf(spent(5_000_00)), listOf(transfer(1_000_00, from = 1, to = 4)))
        assertEquals(without.budgetRemaining, with.budgetRemaining)
        assertTrue(with.upcomingExpenses.isZero)
        assertEquals(php(1_000_00), without.wallets!!.allows - with.wallets!!.allows)
    }

    @Test fun `an upcoming bill comes off both limits, and Safe to spend falls by it once, never twice`() {
        val bank9 = bank.copy(balance = php(9_120_00))
        val without = capacity(listOf(bank9), listOf(spent(5_000_00)))
        val with = capacity(listOf(bank9), listOf(spent(5_000_00)), listOf(bill(3_000_00)))
        assertEquals(php(3_000_00), without.budgetRemaining - with.budgetRemaining)
        assertEquals(php(3_000_00), without.wallets!!.allows - with.wallets!!.allows)
        // The lower of the two, not their sum: 9,120 - 3,000 = 6,120 against 12,000.
        assertEquals(php(3_000_00), without.safeToSpend - with.safeToSpend)
        assertEquals(php(6_120_00), with.safeToSpend)
    }

    @Test fun `a transfer between two spending wallets is never taken off`() {
        val bank9 = bank.copy(balance = php(9_120_00))
        val without = capacity(listOf(bank9, gcash), listOf(spent(5_000_00)))
        val with = capacity(listOf(bank9, gcash), listOf(spent(5_000_00)), listOf(transfer(700_00, from = 1, to = 2)))
        assertTrue(with.wallets!!.deducted.isZero)
        assertEquals(without.wallets!!.allows, with.wallets!!.allows)
        assertEquals(without.safeToSpend, with.safeToSpend)
    }

    @Test fun `a card adds the credit counted, less what this month put on it`() {
        val bank5 = bank.copy(balance = php(5_000_00))
        val visa = Wallet(
            6, "Visa", WalletType.CREDIT_CARD, php(2_000_00), 0L,
            debt = DebtTerms(creditLimit = php(60_000_00)), spendable = true, spendableCredit = php(10_000_00),
        )
        val c = capacity(listOf(bank5, visa), listOf(spent(2_000_00, wallet = 6, date = "2026-10-12")))
        // 10,000 counted - 2,000 charged this month = 8,000, plus the bank's 5,000.
        assertEquals(php(13_000_00), c.wallets!!.total)
        assertEquals(php(18_000_00), c.budgetRemaining) // the charge is spending, once
        assertEquals(Limit.WALLETS, c.limit)
        assertEquals(php(13_000_00), c.safeToSpend)
    }

    @Test fun `choosing wallets changes the wallet figure and which limit sets Safe to spend`() {
        val bank9 = bank.copy(balance = php(9_120_00))
        val both = capacity(listOf(bank9, gcash, savings), listOf(spent(5_000_00)))
        assertEquals(php(11_460_00), both.wallets!!.allows)
        assertEquals(Limit.WALLETS, both.limit)

        val bankOnly = capacity(listOf(bank9, gcash.copy(spendable = false), savings), listOf(spent(5_000_00)))
        assertEquals(php(9_120_00), bankOnly.wallets!!.allows)
        assertEquals(php(9_120_00), bankOnly.safeToSpend)

        // Counting savings as spending money too lifts the wallets past the budget, which then sets the figure.
        val withSavings = capacity(listOf(bank9, gcash, savings.copy(spendable = true)), listOf(spent(5_000_00)))
        assertEquals(php(59_460_00), withSavings.wallets!!.allows)
        assertEquals(Limit.BUDGET, withSavings.limit)
        assertEquals(php(15_000_00), withSavings.safeToSpend)
    }

    // ---------------------------------------------------------------- The verdict

    @Test fun `equal limits say both are limiting`() {
        val c = capacity(listOf(bank.copy(balance = php(15_000_00))), listOf(spent(5_000_00)))
        assertEquals(c.budgetRemaining, c.wallets!!.allows)
        assertEquals(Limit.BOTH, c.limit)
        assertEquals(php(15_000_00), c.safeToSpend)
        assertEquals(SpendingCapacity.LIMITED_BY_BOTH, c.headline)
    }

    @Test fun `at zero the verdict shows 0 and says what took it`() {
        // The budget used exactly, nothing due.
        val used = capacity(listOf(bank), listOf(spent(20_000_00)))
        assertTrue(used.nothingSafe)
        assertEquals(php(0), used.shown)
        assertEquals(SpendingCapacity.BUDGET_USED, used.headline)

        // What's left of the budget is due to bills.
        val billed = capacity(listOf(bank), listOf(spent(15_000_00)), listOf(bill(5_000_00)))
        assertEquals(php(0), billed.budgetRemaining)
        assertEquals(SpendingCapacity.BILLS_TAKE_BUDGET, billed.headline)

        // The bank holds exactly what's due from it.
        val taken = capacity(listOf(bank.copy(balance = php(3_000_00))), listOf(spent(5_000_00)), listOf(bill(3_000_00)))
        assertEquals(Limit.WALLETS, taken.limit)
        assertEquals(php(0), taken.safeToSpend)
        assertEquals(SpendingCapacity.WALLETS_TAKEN, taken.headline)
    }

    @Test fun `below zero the verdict shows 0, never a negative figure`() {
        // Overspent: 25,000 against 20,000.
        val over = capacity(listOf(bank), listOf(spent(25_000_00)))
        assertTrue(over.overspent)
        assertEquals(php(-5_000_00), over.remainingBeforeUpcoming)
        assertEquals(php(-5_000_00), over.safeToSpend)
        assertEquals(php(0), over.shown)
        assertEquals(SpendingCapacity.OVER_BUDGET, over.headline)

        // An overdrawn wallet that once held money: its balance is recorded, and it leaves nothing.
        val overdrawn = Wallet(3, "Cash", WalletType.CASH, php(-3_000_00), 0L, openingBalance = php(2_000_00))
        val empty = capacity(listOf(overdrawn), listOf(spent(5_000_00, wallet = 3)))
        assertEquals(Limit.WALLETS, empty.limit)
        assertEquals(php(-3_000_00), empty.safeToSpend)
        assertEquals(php(0), empty.shown)
        assertEquals(SpendingCapacity.WALLETS_EMPTY, empty.headline)
    }

    @Test fun `without a spending wallet only the budget is used`() {
        val c = capacity(listOf(savings, mp2), listOf(spent(5_000_00)))
        assertNull(c.wallets)
        assertFalse(c.walletsCounted)
        assertEquals(Limit.BUDGET_ONLY, c.limit)
        assertEquals(c.budgetRemaining, c.safeToSpend)
        assertEquals(SpendingCapacity.LIMITED_BY_BUDGET, c.headline)
        assertEquals(SpendingCapacity.NO_SPENDING_WALLETS, c.detail)
    }

    @Test fun `spending wallets with no money recorded don't count yet`() {
        // Cash started at zero and only ever had expenses: its -8,000 is what was spent, not money on hand.
        val cashOnly = Wallet(3, "Cash", WalletType.CASH, php(-8_000_00), 0L, openingBalance = php(0))
        val c = capacity(listOf(cashOnly), listOf(spent(8_000_00, wallet = 3)))
        assertFalse(c.walletsCounted)
        assertEquals(Limit.BUDGET_ONLY, c.limit)
        assertEquals(php(12_000_00), c.safeToSpend)
        assertEquals(SpendingCapacity.NOTHING_RECORDED, c.detail)
    }

    @Test fun `without a budget there are no limits to show`() {
        val safe = CashflowCalculator.safeToSpend("PHP", today, emptyList(), listOf(bank), emptyList(), emptyList(), categories)
        assertNull(safe?.let { SpendingCapacity.of(it) })
    }

    // ---------------------------------------------------------------- QA review (2026-11-18)

    @Test fun `a budget already used up before the bills says so, not that the bills took it`() {
        val c = capacity(listOf(bank), listOf(spent(20_000_00)), listOf(bill(1_000_00)))
        assertEquals(php(0), c.remainingBeforeUpcoming)
        assertEquals(php(-1_000_00), c.budgetRemaining)
        assertEquals(SpendingCapacity.BUDGET_USED, c.headline)
    }

    @Test fun `when the wallets are the lower limit, their cause is named even over budget`() {
        // Over budget by 5,000, and the only wallet is overdrawn by more: the wallets set the 0.
        val overdrawn = Wallet(3, "Cash", WalletType.CASH, php(-10_000_00), 0L, openingBalance = php(2_000_00))
        val c = capacity(listOf(overdrawn), listOf(spent(25_000_00, wallet = 3)))
        assertTrue(c.overspent)
        assertEquals(Limit.WALLETS, c.limit)
        assertEquals(SpendingCapacity.WALLETS_EMPTY, c.headline)
    }

    @Test fun `equal limits at zero still say both are limiting`() {
        // A bank that once held money, now empty, and a budget spent to the last peso.
        val emptyBank = bank.copy(balance = php(0), openingBalance = php(1_000_00))
        val c = capacity(listOf(emptyBank), listOf(spent(20_000_00)))
        assertEquals(Limit.BOTH, c.limit)
        assertEquals(php(0), c.shown)
        assertEquals(SpendingCapacity.LIMITED_BY_BOTH, c.headline)
    }

    @Test fun `a loan payment's interest is an upcoming expense, while the whole payment leaves the wallets`() {
        val loan = Wallet(7, "Loan", WalletType.LOAN, php(80_000_00), 0L)
        val payment = RecurringSchedule(
            nextId++, "Loan", TransactionType.EXPENSE, php(1_500_00), 1, 5, Frequency.MONTHLY, 1, LocalDate.parse("2026-10-25"), null, 0, false,
            toWalletId = 7, interest = php(500_00),
        )
        val c = capacity(listOf(bank.copy(balance = php(9_120_00)), loan), listOf(spent(5_000_00)), listOf(payment))
        assertEquals(php(500_00), c.upcomingExpenses)
        assertEquals(php(2_000_00), c.wallets!!.deducted)
        // Not a bill: it shows under payments and transfers, so nothing reads as counted twice.
        assertTrue(c.walletBills!!.isZero)
        assertEquals(php(2_000_00), c.walletOutflows)
    }

    @Test fun `a bill paid from savings lowers the budget's limit but not the wallets'`() {
        val c = capacity(listOf(bank.copy(balance = php(9_120_00)), savings), listOf(spent(5_000_00)), listOf(bill(3_000_00, wallet = 4)))
        assertEquals(php(3_000_00), c.upcomingExpenses)
        assertTrue(c.wallets!!.deducted.isZero)
        assertEquals(php(9_120_00), c.wallets!!.allows)
    }

    @Test fun `what's due from the wallets splits into their bills and everything else, and adds up`() {
        val c = capacity(
            listOf(bank.copy(balance = php(9_120_00)), savings),
            listOf(spent(5_000_00)),
            listOf(bill(3_000_00), bill(800_00, wallet = 4), transfer(1_000_00, from = 1, to = 4)),
        )
        assertEquals(php(3_000_00), c.walletBills) // the bank's bill; the one from savings isn't on hand
        assertEquals(php(1_000_00), c.walletOutflows)
        assertEquals(c.wallets!!.deducted, c.walletBills!! + c.walletOutflows!!)
        // The wallets' bills are among the upcoming expenses, which also hold the one from savings.
        assertEquals(php(3_800_00), c.upcomingExpenses)
    }

    // ---------------------------------------------------------------- Home and the Budget tab

    @Test fun `Home's card and the Budget tab show the same limits from the same data`() {
        val wallets = listOf(bank.copy(balance = php(9_120_00)), gcash, savings)
        val transactions = listOf(spent(5_000_00), spent(1_200_00, wallet = 2, date = "2026-10-15"))
        val schedules = listOf(bill(3_000_00), transfer(1_000_00, from = 1, to = 4))

        val home = HomeStateBuilder.build(transactions, wallets, categories, "PHP", today, budgets, schedules, FeatureSet.ALL)
        val fromHome = SpendingCapacity.of(home.safeToSpend!!)!!

        val ledger = FxTranslation.translate("PHP", wallets, transactions)
        val plan = BudgetPlanner.plan(categories, budgets, ledger.transactions, "PHP", YearMonth.from(today))
        val fromBudget = SpendingCapacity.of(budgetSafeToSpend(plan, today, ledger, budgets, schedules, categories)!!)!!

        assertEquals(fromHome, fromBudget)
        // A past month on the Budget tab has no limits (it closes on what was left).
        val september = BudgetPlanner.plan(categories, budgets, ledger.transactions, "PHP", YearMonth.of(2026, 9))
        assertNull(budgetSafeToSpend(september, today, ledger, budgets, schedules, categories))
    }

    @Test fun `with Recurring switched off both pages leave the schedules out alike`() {
        val wallets = listOf(bank.copy(balance = php(9_120_00)))
        val transactions = listOf(spent(5_000_00))
        val schedules = listOf(bill(3_000_00), transfer(1_000_00, from = 1, to = 4))
        val off = FeatureSet.ALL.with(Feature.RECURRING, false)
        val home = HomeStateBuilder.build(transactions, wallets, categories, "PHP", today, budgets, schedules, off)
        val fromHome = SpendingCapacity.of(home.safeToSpend!!)!!
        // The Budget tab's view model hands it no schedules while Recurring is off (BudgetViewModel).
        val ledger = FxTranslation.translate("PHP", wallets, transactions)
        val plan = BudgetPlanner.plan(categories, budgets, ledger.transactions, "PHP", YearMonth.from(today))
        val fromBudget = SpendingCapacity.of(budgetSafeToSpend(plan, today, ledger, budgets, emptyList(), categories)!!)!!
        assertEquals(fromHome, fromBudget)
        assertTrue(fromHome.upcomingExpenses.isZero)
        assertTrue(fromHome.wallets!!.deducted.isZero)
    }

    @Test fun `a valued foreign wallet and its bill count alike on both pages`() {
        // ¥50,000 cash at ₱0.38 a yen counts as ₱19,000 of spending money; its ¥10,000 bill as ₱3,800.
        val yen = Wallet(
            2, "Yen", WalletType.CASH, Money(50_000, "JPY"), 0L,
            openingBalance = Money(50_000, "JPY"), fxBasisRate = BigDecimal("0.38"),
        )
        val wallets = listOf(bank.copy(balance = php(9_120_00)), yen)
        val transactions = listOf(spent(5_000_00))
        val yenBill = RecurringSchedule(
            nextId++, "Rent", TransactionType.EXPENSE, Money(10_000, "JPY"), 2, 5, Frequency.MONTHLY, 1, LocalDate.parse("2026-10-28"), null, 0, false,
        )
        val home = HomeStateBuilder.build(transactions, wallets, categories, "PHP", today, budgets, listOf(yenBill), FeatureSet.ALL)
        val fromHome = SpendingCapacity.of(home.safeToSpend!!)!!
        val ledger = FxTranslation.translate("PHP", wallets, transactions)
        val plan = BudgetPlanner.plan(categories, budgets, ledger.transactions, "PHP", YearMonth.from(today))
        val fromBudget = SpendingCapacity.of(budgetSafeToSpend(plan, today, ledger, budgets, listOf(yenBill), categories)!!)!!
        assertEquals(fromHome, fromBudget)
        assertEquals(php(3_800_00), fromHome.upcomingExpenses)
        assertEquals(php(3_800_00), fromHome.walletBills)
    }
}
