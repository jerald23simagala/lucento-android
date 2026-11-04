package com.lucento.app.domain.ask

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Budget
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One household for Ask Lucento's tests, on November 15, 2026, in USD (plus one EUR wallet that must never be added in):
 * a bank and cash, a card and a loan; October and November income and spending; a loan payment (a transfer), a balance
 * adjustment, budgets for groceries and rent, and a scheduled loan instalment.
 */
internal object AskFixtures {
    val today: LocalDate = LocalDate.parse("2026-11-15")

    fun usd(minor: Long) = Money(minor, "USD")

    val bank = Wallet(1, "Everyday bank", WalletType.BANK, usd(500_000), 0, openingBalance = usd(400_000))
    val cash = Wallet(2, "Cash", WalletType.CASH, usd(20_000), 0, openingBalance = usd(15_000))
    val card = Wallet(
        3, "Travel card", WalletType.CREDIT_CARD, usd(45_000), 0, openingBalance = usd(45_000),
        debt = DebtTerms(interestRateBps = 2_400, statementDay = 20, dueDay = 10),
    )
    val loan = Wallet(
        4, "Car loan", WalletType.LOAN, usd(900_000), 0, openingBalance = usd(1_000_000),
        debt = DebtTerms(interestRateBps = 600, repayment = DebtRepayment.MONTHLY, termMonths = 24, installment = usd(45_000)),
    )
    val euros = Wallet(5, "Lisbon account", WalletType.BANK, Money(10_000, "EUR"), 0)

    val categories = listOf(
        Category(10, "Groceries", CategoryKind.EXPENSE, "cart"),
        Category(11, "Rent", CategoryKind.EXPENSE, "home"),
        Category(12, "Dining", CategoryKind.EXPENSE, "food"),
        Category(20, "Salary", CategoryKind.INCOME, "salary"),
    )

    /** A note no summary may ever carry. */
    const val PRIVATE_NOTE = "Birthday gift for Sam"

    private var id = 100L
    private fun tx(type: TransactionType, minor: Long, wallet: Long, category: Long?, date: String, note: String = "", group: String? = null) =
        Transaction(id++, type, usd(minor), wallet, category, LocalDate.parse(date), note, 0L, transferGroup = group)

    val transactions: List<Transaction> = listOf(
        // October
        tx(TransactionType.INCOME, 300_000, 1, 20, "2026-10-01"),
        tx(TransactionType.EXPENSE, 25_000, 1, 10, "2026-10-12"),
        // November
        tx(TransactionType.INCOME, 300_000, 1, 20, "2026-11-01"),
        tx(TransactionType.EXPENSE, 120_000, 1, 11, "2026-11-02"),
        tx(TransactionType.EXPENSE, 30_000, 1, 10, "2026-11-05", note = PRIVATE_NOTE),
        tx(TransactionType.EXPENSE, 8_000, 2, 12, "2026-11-06"),
        tx(TransactionType.EXPENSE, 2_000, 2, null, "2026-11-07"),
        tx(TransactionType.TRANSFER_OUT, 50_000, 1, null, "2026-11-08", group = "loan-1"),
        tx(TransactionType.TRANSFER_IN, 50_000, 4, null, "2026-11-08", group = "loan-1"),
        tx(TransactionType.ADJUSTMENT_IN, 5_000, 2, null, "2026-11-09"),
        // A euro expense: listed apart, never added to USD figures.
        Transaction(id++, TransactionType.EXPENSE, Money(4_000, "EUR"), 5, 10, LocalDate.parse("2026-11-03"), "", 0L),
    )

    val budgets = listOf(
        Budget(id = 1, categoryId = 10, limit = usd(40_000)),
        Budget(id = 2, categoryId = 11, limit = usd(120_000)),
    )

    val loanInstalment = RecurringSchedule(
        7, "Car loan instalment", TransactionType.EXPENSE, usd(40_000), walletId = 1, categoryId = null, frequency = Frequency.MONTHLY,
        interval = 1, startDate = LocalDate.parse("2026-11-20"), endDate = null, nextIndex = 0, paused = false,
        toWalletId = 4, interest = usd(5_000),
    )

    fun inputs() = ReportInputs(
        wallets = listOf(bank, cash, card, loan, euros),
        transactions = transactions,
        categories = categories,
        budgets = budgets,
        schedules = listOf(loanInstalment),
        today = today,
        zone = ZoneOffset.UTC,
    )
}
