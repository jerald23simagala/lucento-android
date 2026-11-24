package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Safe to spend's second figure, the money on hand (tester round, 2026-11-17; docs/CALCULATIONS.md "Safe to spend"):
 * which wallets count, what a card's credit adds, what is taken off, and the lower-of-two rule.
 */
class MoneyOnHandTest {

    private val today = LocalDate.parse("2026-10-22") // month end 2026-10-31
    private fun php(minor: Long) = Money(minor, "PHP")

    private fun wallet(
        id: Long,
        type: WalletType,
        minor: Long,
        spendable: Boolean? = null,
        credit: Long? = null,
        debt: DebtTerms? = null,
        code: String = "PHP",
    ) = Wallet(
        id, "W$id", type, Money(minor, code), 0L,
        debt = debt, spendable = spendable, spendableCredit = credit?.let { Money(it, code) },
    )

    private val bank = wallet(1, WalletType.BANK, 9_120_00)
    private val gcash = wallet(2, WalletType.E_WALLET, 2_340_00)
    private val cash = wallet(3, WalletType.CASH, 500_00)
    private val savings = wallet(4, WalletType.SAVINGS, 48_000_00)
    private val mp2 = wallet(5, WalletType.INVESTMENT, 152_300_00)
    private val loan = wallet(7, WalletType.LOAN, 80_000_00, spendable = true)

    private fun card(owed: Long, chosen: Long?, limit: Long? = 60_000_00, spendable: Boolean? = true, statementDay: Int? = null, dueDay: Int? = null) =
        wallet(
            6, WalletType.CREDIT_CARD, owed, spendable = spendable, credit = chosen,
            debt = DebtTerms(creditLimit = limit?.let(::php), statementDay = statementDay, dueDay = dueDay),
        )

    private fun schedule(
        id: Long,
        type: TransactionType,
        amount: Long,
        wallet: Long,
        start: String,
        to: Long? = null,
        interest: Long = 0,
        category: Long? = if (type == TransactionType.TRANSFER_OUT) null else 5,
    ) = RecurringSchedule(
        id, "S$id", type, php(amount), wallet, category, Frequency.MONTHLY, 1, LocalDate.parse(start), null, 0, false,
        toWalletId = to, interest = php(interest),
    )

    private fun compute(wallets: List<Wallet>, schedules: List<RecurringSchedule> = emptyList(), transactions: List<Transaction> = emptyList()) =
        MoneyOnHandCalculator.compute("PHP", today, wallets, transactions, schedules)

    @Test fun `cash, bank and e-wallet count by default, savings, investments, cards and loans do not`() {
        val money = compute(listOf(bank, gcash, cash, savings, mp2, card(18_400_00, chosen = 10_000_00, spendable = null), loan))!!
        assertEquals(listOf(1L, 2L, 3L), money.sources.map { it.walletId })
        assertEquals(php(11_960_00), money.total)
        assertEquals(php(11_960_00), money.allows)
    }

    @Test fun `the person's choice wins over the type's default, never for a loan`() {
        val money = compute(listOf(bank.copy(spendable = false), savings.copy(spendable = true), mp2.copy(spendable = true), loan))!!
        assertEquals(listOf(4L, 5L), money.sources.map { it.walletId })
        assertEquals(php(200_300_00), money.total)
        assertNull(compute(listOf(bank.copy(spendable = false), loan)))
    }

    @Test fun `a card adds the credit chosen, never more than is available, and nothing without a limit`() {
        // 60,000 limit − 18,400 owed = 41,600 available: the 10,000 chosen counts in full.
        assertEquals(php(10_000_00), MoneyOnHandCalculator.contribution(card(18_400_00, chosen = 10_000_00)))
        // 55,000 owed leaves 5,000 available: only that counts.
        assertEquals(php(5_000_00), MoneyOnHandCalculator.contribution(card(55_000_00, chosen = 10_000_00)))
        // Over the limit: nothing.
        assertEquals(php(0), MoneyOnHandCalculator.contribution(card(61_000_00, chosen = 10_000_00)))
        assertNull(MoneyOnHandCalculator.contribution(card(18_400_00, chosen = 10_000_00, limit = null)))
        assertNull(MoneyOnHandCalculator.contribution(card(18_400_00, chosen = null)))
        assertNull(MoneyOnHandCalculator.contribution(card(18_400_00, chosen = 0)))
        val money = compute(listOf(bank, card(18_400_00, chosen = 10_000_00)))!!
        assertTrue(money.sources.single { it.walletId == 6L }.credit)
        assertEquals(php(19_120_00), money.total)
    }

    @Test fun `what leaves the spending money by month end is taken off, income and money kept aside are not`() {
        val schedules = listOf(
            schedule(10, TransactionType.EXPENSE, 5_200_00, wallet = 1, start = "2026-10-28"), // bill from the bank
            schedule(11, TransactionType.EXPENSE, 900_00, wallet = 4, start = "2026-10-29"), // bill from savings: not on hand
            schedule(12, TransactionType.EXPENSE, 3_000_00, wallet = 1, start = "2026-11-03"), // next month
            schedule(13, TransactionType.EXPENSE, 1_500_00, wallet = 1, start = "2026-10-25", to = 7, interest = 500_00), // loan payment
            schedule(14, TransactionType.TRANSFER_OUT, 1_000_00, wallet = 1, start = "2026-10-30", to = 5), // into MP2
            schedule(15, TransactionType.TRANSFER_OUT, 700_00, wallet = 1, start = "2026-10-30", to = 2), // bank → GCash: stays on hand
            schedule(16, TransactionType.INCOME, 40_000_00, wallet = 1, start = "2026-10-30"), // salary: never added
        )
        val money = compute(listOf(bank, gcash, savings, mp2, loan), schedules)!!
        val byKind = money.deductions.groupBy { it.kind }
        assertEquals(php(5_200_00), byKind.getValue(MoneyOnHand.Kind.BILLS).single().amount)
        // A loan payment leaves the account in full, interest included.
        assertEquals(php(2_000_00), byKind.getValue(MoneyOnHand.Kind.DEBT_PAYMENT).single().amount)
        assertEquals(LocalDate.parse("2026-10-25"), byKind.getValue(MoneyOnHand.Kind.DEBT_PAYMENT).single().date)
        assertEquals(listOf("S14"), byKind.getValue(MoneyOnHand.Kind.TRANSFER).map { it.name })
        assertEquals(php(11_460_00), money.total)
        assertEquals(php(8_200_00), money.deducted)
        assertEquals(php(3_260_00), money.allows)
    }

    @Test fun `a card's payment from its billing days comes off the account that pays it, unless a scheduled payment stands for it`() {
        // Statement on the 5th, due on the 25th: the 1,250 owed is due this month, paid from the bank (the spending
        // account with the most money).
        val visa = card(1_250_00, chosen = null, spendable = null, statementDay = 5, dueDay = 25)
        val money = compute(listOf(bank, gcash, visa))!!
        val due = money.deductions.single { it.kind == MoneyOnHand.Kind.CARD_PAYMENT }
        assertEquals(php(1_250_00), due.amount)
        assertEquals(LocalDate.parse("2026-10-25"), due.date)
        assertEquals("W6 payment", due.name)

        val scheduled = schedule(20, TransactionType.EXPENSE, 1_250_00, wallet = 1, start = "2026-10-25", to = 6)
        val withSchedule = compute(listOf(bank, gcash, visa), listOf(scheduled))!!
        assertTrue(withSchedule.deductions.none { it.kind == MoneyOnHand.Kind.CARD_PAYMENT })
        assertEquals(php(1_250_00), withSchedule.deductions.single { it.kind == MoneyOnHand.Kind.DEBT_PAYMENT }.amount)
    }

    @Test fun `other currencies are never mixed in`() {
        val usd = wallet(9, WalletType.BANK, 410_00, code = "USD")
        assertEquals(listOf(1L), compute(listOf(bank, usd))!!.sources.map { it.walletId })
        assertNull(MoneyOnHandCalculator.compute("JPY", today, listOf(bank, usd), emptyList(), emptyList()))
    }

    // ---------------------------------------------------------------- Safe to spend: the lower of the two

    private val food = Category(5, "Food", CategoryKind.EXPENSE, "groceries")
    private val budget = listOf(Budget(1, 5, php(42_590_00)))
    private fun spent(minor: Long) = Transaction(100, TransactionType.EXPENSE, php(minor), 1, 5, LocalDate.parse("2026-10-10"), "", 0L)

    @Test fun `with a budget, Safe to spend is the lower figure and says which one set it`() {
        val bill = schedule(10, TransactionType.EXPENSE, 6_450_00, wallet = 1, start = "2026-10-28")
        val toMp2 = schedule(14, TransactionType.TRANSFER_OUT, 1_000_00, wallet = 1, start = "2026-10-30", to = 5)
        val safe = CashflowCalculator.safeToSpend(
            "PHP", today, listOf(spent(31_298_00)), listOf(bank, mp2), budget, listOf(bill, toMp2), listOf(food),
        )!!
        assertEquals(SafeToSpend.Basis.BUDGET, safe.basis)
        // Budget: 42,590 − 31,298 − 6,450 = 4,842. Money: 9,120 − 6,450 − 1,000 = 1,670.
        assertEquals(php(4_842_00), safe.budgetAllows)
        assertEquals(php(1_670_00), safe.moneyOnHand!!.allows)
        assertEquals(php(1_670_00), safe.amount)
        assertTrue(safe.limitedByMoney)
        assertEquals(php(167_00), safe.perDay) // 1,670 over 10 days
        // The transfer is never a bill: Bills due is the budget's own.
        assertEquals(php(6_450_00), safe.committed)
    }

    @Test fun `plenty on hand leaves the budget's figure, and no spending wallet leaves the budget alone`() {
        val rich = bank.copy(balance = php(90_000_00))
        val safe = CashflowCalculator.safeToSpend("PHP", today, listOf(spent(31_298_00)), listOf(rich), budget, emptyList(), listOf(food))!!
        assertEquals(php(11_292_00), safe.amount)
        assertFalse(safe.limitedByMoney)

        val noSpending = CashflowCalculator.safeToSpend("PHP", today, listOf(spent(31_298_00)), listOf(savings), budget, emptyList(), listOf(food))!!
        assertNull(noSpending.moneyOnHand)
        assertEquals(php(11_292_00), noSpending.amount)
        assertFalse(noSpending.limitedByMoney)
    }

    @Test fun `without a budget the money on hand is the estimate, transfers and card payments included`() {
        val visa = card(1_250_00, chosen = null, spendable = null, statementDay = 5, dueDay = 25)
        val toSavings = schedule(14, TransactionType.TRANSFER_OUT, 1_000_00, wallet = 1, start = "2026-10-30", to = 4)
        val safe = CashflowCalculator.safeToSpend("PHP", today, emptyList(), listOf(bank, savings, visa), emptyList(), listOf(toSavings), listOf(food))!!
        assertEquals(SafeToSpend.Basis.BALANCES, safe.basis)
        assertEquals(php(9_120_00), safe.spendableBalance)
        assertEquals(php(6_870_00), safe.amount) // 9,120 − 1,250 − 1,000
        assertEquals(php(2_250_00), safe.committed)
        assertFalse(safe.limitedByMoney)
    }

    @Test fun `a schedule can be a transfer only with somewhere else to go`() {
        val transfer = schedule(14, TransactionType.TRANSFER_OUT, 1_000_00, wallet = 1, start = "2026-10-30", to = 5)
        assertTrue(transfer.isTransfer)
        assertTrue(transfer.isLinked)
        assertFalse(transfer.isDebtPayment)
        assertFalse(transfer.isCollection)
        assertEquals(ScheduledFlow.TRANSFER, transfer.flow)
        assertEquals(php(-1_000_00), transfer.signedOccurrence)
        assertEquals(php(0), RecurringMath.committedExpenses(listOf(transfer), "PHP", LocalDate.parse("2026-10-31")))
        assertTrue(runCatching { schedule(15, TransactionType.TRANSFER_OUT, 1_000_00, wallet = 1, start = "2026-10-30") }.isFailure)
        assertTrue(runCatching { schedule(16, TransactionType.TRANSFER_OUT, 1_000_00, wallet = 1, start = "2026-10-30", to = 1) }.isFailure)
        assertTrue(runCatching { schedule(17, TransactionType.TRANSFER_IN, 1_000_00, wallet = 1, start = "2026-10-30", to = 5) }.isFailure)
    }

    @Test fun `kinds belong to their type and unknown keys read as none`() {
        assertEquals(WalletKind.MP2, WalletKind.fromKey("mp2", WalletType.INVESTMENT))
        assertNull(WalletKind.fromKey("mp2", WalletType.BANK))
        assertNull(WalletKind.fromKey("space_rocket", WalletType.INVESTMENT))
        assertNull(WalletKind.fromKey(null, WalletType.INVESTMENT))
        assertTrue(WalletKind.of(WalletType.BANK).isEmpty())
        assertTrue(WalletType.INVESTMENT.hasKinds && WalletType.LOAN.hasKinds && WalletType.OTHER_ASSET.hasKinds)
        assertFalse(WalletType.CASH.hasKinds)
        // Every stored key fits what sync and backup accept, and keys never repeat.
        assertTrue(WalletKind.entries.all { WalletKind.KEY_PATTERN.matches(it.key) })
        assertTrue(WalletIcon.entries.all { WalletKind.KEY_PATTERN.matches(it.key) })
        assertEquals(WalletKind.entries.size, WalletKind.entries.map { it.key }.toSet().size)
        assertEquals(WalletIcon.entries.size, WalletIcon.entries.map { it.key }.toSet().size)
    }

    // ---------------------------------------------------------------- reviews of 2026-11-17 (QA and design)

    private fun charge(id: Long, minor: Long, date: String, wallet: Long = 6) =
        Transaction(id, TransactionType.EXPENSE, php(minor), wallet, 5, LocalDate.parse(date), "", 0L)

    @Test fun `counted credit is used up by what this month puts on the card, and a bill marked paid leaves the figure as it was`() {
        // Limit 60,000, 10,000 counted; 5,000 in the bank.
        val bank5 = bank.copy(balance = php(5_000_00))
        val spentOnCard = compute(listOf(bank5, card(owed = 9_000_00, chosen = 10_000_00)), transactions = listOf(charge(110, 9_000_00, "2026-10-12")))!!
        val source = spentOnCard.sources.single { it.credit }
        assertEquals(php(9_000_00), source.used)
        assertEquals(php(1_000_00), source.amount)
        assertEquals(php(6_000_00), spentOnCard.total)

        // A 1,000 bill from the card: due before Mark paid, spent on the card after. The figure is the same either way.
        val bill = schedule(20, TransactionType.EXPENSE, 1_000_00, wallet = 6, start = "2026-10-25")
        val before = compute(listOf(bank5, card(owed = 0, chosen = 10_000_00)), listOf(bill))!!
        val after = compute(
            listOf(bank5, card(owed = 1_000_00, chosen = 10_000_00)), listOf(bill.copy(nextIndex = 1)),
            listOf(charge(111, 1_000_00, "2026-10-25")),
        )!!
        assertEquals(php(14_000_00), before.allows)
        assertEquals(php(14_000_00), after.allows)
    }

    @Test fun `charges already in a statement due this month are that payment's, never counted twice`() {
        // Statement on the 5th, due on the 25th, both in October; today is the 22nd.
        val visa = card(owed = 5_000_00, chosen = 10_000_00, statementDay = 5, dueDay = 25)
        val billed = charge(120, 3_000_00, "2026-10-03")
        val since = charge(121, 2_000_00, "2026-10-10")
        val money = compute(listOf(bank, visa), transactions = listOf(billed, since))!!
        val source = money.sources.single { it.credit }
        assertEquals(php(2_000_00), source.used)
        assertEquals(php(8_000_00), source.amount)
        // The 3,000 statement comes off the bank instead, as the card's payment due on the 25th.
        assertEquals(php(3_000_00), money.deductions.single { it.kind == MoneyOnHand.Kind.CARD_PAYMENT }.amount)
    }

    @Test fun `more put on the card than is counted comes off the spending money`() {
        val source = compute(listOf(bank, card(owed = 12_000_00, chosen = 10_000_00)), transactions = listOf(charge(130, 12_000_00, "2026-10-08")))!!
            .sources.single { it.credit }
        assertEquals(php(-2_000_00), source.amount)
    }

    @Test fun `a refund settles what was owed when the month opened first, then gives back this month's charges`() {
        val refund = Transaction(131, TransactionType.INCOME, php(500_00), 6, null, LocalDate.parse("2026-10-15"), "", 0L)
        val lastMonth = charge(132, 4_000_00, "2026-09-28")
        // September's 4,000 is still owed: the refund goes to it, and October's 2,000 stays used.
        val carried = compute(
            listOf(bank, card(owed = 5_500_00, chosen = 10_000_00)),
            transactions = listOf(charge(133, 2_000_00, "2026-10-09"), refund, lastMonth),
        )!!.sources.single { it.credit }
        assertEquals(php(2_000_00), carried.used)
        assertEquals(php(8_000_00), carried.amount)
        // With September paid off, the refund gives back what it took.
        val paidSeptember = Transaction(134, TransactionType.TRANSFER_IN, php(4_000_00), 6, null, LocalDate.parse("2026-09-30"), "", 0L)
        val clear = compute(
            listOf(bank, card(owed = 1_500_00, chosen = 10_000_00)),
            transactions = listOf(charge(133, 2_000_00, "2026-10-09"), refund, lastMonth, paidSeptember),
        )!!.sources.single { it.credit }
        assertEquals(php(1_500_00), clear.used)
        assertEquals(php(8_500_00), clear.amount)
    }

    @Test fun `a loan payment left unmarked for months never takes more than the loan still owes`() {
        // 12,000 a month since July, never marked paid: four payments due by October's end, but only 20,000 is owed.
        val owedLoan = wallet(7, WalletType.LOAN, 20_000_00)
        val instalment = schedule(30, TransactionType.EXPENSE, 12_000_00, wallet = 1, start = "2026-07-15", to = 7)
        val payment = compute(listOf(bank.copy(balance = php(90_000_00)), owedLoan), listOf(instalment))!!
            .deductions.single { it.kind == MoneyOnHand.Kind.DEBT_PAYMENT }
        assertEquals(4, payment.count)
        assertEquals(php(20_000_00), payment.amount)
    }

    @Test fun `a transfer schedule into a card stands for the card's payment, so it isn't taken twice`() {
        // Only a synced row from a modified client can be one (the editor and backups refuse it).
        val visa = card(owed = 5_000_00, chosen = null, spendable = null, statementDay = 5, dueDay = 25)
        val topUp = schedule(31, TransactionType.TRANSFER_OUT, 5_000_00, wallet = 1, start = "2026-10-24", to = 6)
        val money = compute(listOf(bank, visa), listOf(topUp), listOf(charge(140, 5_000_00, "2026-10-02")))!!
        assertTrue(money.deductions.none { it.kind == MoneyOnHand.Kind.CARD_PAYMENT })
        assertEquals(php(5_000_00), money.deducted)
    }

    @Test fun `wallets with nothing but spending recorded leave Safe to spend to the budget`() {
        // Cash started at zero and only ever had expenses: its −8,000 is minus what was spent, not money on hand.
        val cashOnly = Wallet(3, "Cash", WalletType.CASH, php(-8_000_00), 0L, openingBalance = php(0))
        val spend = Transaction(150, TransactionType.EXPENSE, php(8_000_00), 3, 5, LocalDate.parse("2026-10-05"), "", 0L)
        val safe = CashflowCalculator.safeToSpend("PHP", today, listOf(spend), listOf(cashOnly), budget, emptyList(), listOf(food))!!
        assertFalse(safe.moneyOnHand!!.recorded)
        assertFalse(safe.limitedByMoney)
        assertEquals(php(34_590_00), safe.amount) // 42,590 − 8,000
        // Once money is recorded in it — income here — it counts.
        val salary = Transaction(151, TransactionType.INCOME, php(1_000_00), 3, null, LocalDate.parse("2026-10-06"), "", 0L)
        val counted = CashflowCalculator.safeToSpend(
            "PHP", today, listOf(spend, salary), listOf(cashOnly.copy(balance = php(-7_000_00))), budget, emptyList(), listOf(food),
        )!!
        assertTrue(counted.moneyOnHand!!.recorded)
        assertTrue(counted.limitedByMoney)
        assertEquals(php(-7_000_00), counted.amount)
        // A starting balance counts as recorded too, and so does a card whose credit the person chose.
        assertTrue(MoneyOnHandCalculator.hasMoneyRecorded(bank, emptyList()))
        assertTrue(compute(listOf(card(owed = 0, chosen = 10_000_00)))!!.recorded)
    }

    // ---------------------------------------------------------------- QA re-check (2026-11-18): payments and refunds

    /** A payment from [from] into the card: both legs, linked, as the transfer sheet saves them. */
    private fun payCard(id: Long, minor: Long, date: String, from: Long = 1) = listOf(
        Transaction(id, TransactionType.TRANSFER_OUT, php(minor), from, null, LocalDate.parse(date), "", 0L, "pay$id"),
        Transaction(id + 1, TransactionType.TRANSFER_IN, php(minor), 6, null, LocalDate.parse(date), "", 0L, "pay$id"),
    )

    @Test fun `paying the card as you go leaves the figure where it was`() {
        // N-A: 3,000 on the card, then paid from the bank two days later.
        val bank20 = bank.copy(balance = php(20_000_00))
        val before = compute(listOf(bank20, card(owed = 3_000_00, chosen = 10_000_00)), transactions = listOf(charge(160, 3_000_00, "2026-10-10")))!!
        val after = compute(
            listOf(bank.copy(balance = php(17_000_00)), card(owed = 0, chosen = 10_000_00)),
            transactions = listOf(charge(160, 3_000_00, "2026-10-10")) + payCard(161, 3_000_00, "2026-10-12"),
        )!!
        assertEquals(php(27_000_00), before.allows)
        assertEquals(php(27_000_00), after.allows)
        assertEquals(php(0), after.sources.single { it.credit }.used)
    }

    @Test fun `paying the statement due this month leaves the figure where it was`() {
        // Statement on the 5th (3,000), due on the 25th, paid from the bank on the 20th; 2,000 charged since.
        val billed = charge(170, 3_000_00, "2026-10-03")
        val since = charge(171, 2_000_00, "2026-10-10")
        val before = compute(listOf(bank, card(owed = 5_000_00, chosen = 10_000_00, statementDay = 5, dueDay = 25)), transactions = listOf(billed, since))!!
        val after = compute(
            listOf(bank.copy(balance = php(6_120_00)), card(owed = 2_000_00, chosen = 10_000_00, statementDay = 5, dueDay = 25)),
            transactions = listOf(billed, since) + payCard(172, 3_000_00, "2026-10-20"),
        )!!
        assertEquals(php(14_120_00), before.allows) // 9,120 + 8,000 − 3,000
        assertEquals(php(14_120_00), after.allows)  // 6,120 + 8,000
        assertTrue(after.deductions.none { it.kind == MoneyOnHand.Kind.CARD_PAYMENT })
    }

    @Test fun `a refund after the statement counts once`() {
        // N-B: a 1,000 refund on the 16th lowers the statement's amount due; it doesn't also give back credit.
        val billed = charge(180, 3_000_00, "2026-10-03")
        val since = charge(181, 2_000_00, "2026-10-10")
        val refund = Transaction(182, TransactionType.INCOME, php(1_000_00), 6, null, LocalDate.parse("2026-10-16"), "", 0L)
        val before = compute(listOf(bank, card(owed = 5_000_00, chosen = 10_000_00, statementDay = 5, dueDay = 25)), transactions = listOf(billed, since))!!
        val after = compute(listOf(bank, card(owed = 4_000_00, chosen = 10_000_00, statementDay = 5, dueDay = 25)), transactions = listOf(billed, since, refund))!!
        assertEquals(php(1_000_00), after.allows - before.allows)
    }

    @Test fun `a statement paid from savings keeps its charges in the counted credit`() {
        // N-C: the card is paid from savings (last payment came from there), so nothing is set aside from spending money
        // for its statement, and the month's charges before it still use the counted credit.
        val visa = card(owed = 5_000_00, chosen = 10_000_00, statementDay = 5, dueDay = 25)
        val txs = listOf(charge(190, 3_000_00, "2026-10-03"), charge(191, 2_000_00, "2026-10-10")) + payCard(192, 1_000_00, "2026-09-24", from = 4)
        val money = compute(listOf(bank, savings, visa), transactions = txs)!!
        assertTrue(money.deductions.none { it.kind == MoneyOnHand.Kind.CARD_PAYMENT })
        assertEquals(php(5_000_00), money.sources.single { it.credit }.used)
        assertEquals(php(9_120_00 + 5_000_00), money.allows)
    }

    @Test fun `a debt payment held to what is owed says so`() {
        val owedLoan = wallet(7, WalletType.LOAN, 20_000_00)
        val instalment = schedule(30, TransactionType.EXPENSE, 12_000_00, wallet = 1, start = "2026-07-15", to = 7)
        assertTrue(compute(listOf(bank, owedLoan), listOf(instalment))!!.deductions.single().capped)
        val bigLoan = wallet(7, WalletType.LOAN, 500_000_00)
        assertFalse(compute(listOf(bank, bigLoan), listOf(instalment))!!.deductions.single().capped)
    }
}
