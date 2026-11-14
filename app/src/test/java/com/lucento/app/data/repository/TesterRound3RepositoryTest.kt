package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.TransactionRepository.OutsideResult
import com.lucento.app.domain.CardSetup
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Insights
import com.lucento.app.domain.LoanMath
import com.lucento.app.domain.OutsideMovement
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Tester round 3: withdrawals and external payments, and a new card's amount due at setup. */
class TesterRound3RepositoryTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1_000L }, debtTerms = FakeDebtTermsDao())
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2_000L })
    private val today = LocalDate.parse("2026-11-10")

    private fun php(minor: Long) = Money(minor, "PHP")
    private suspend fun create(name: String, type: WalletType, minor: Long, debt: DebtTerms? = null, unbilled: Money? = null) =
        (wallets.create(name, type, php(minor), debt, unbilledAtSetup = unbilled, setupDay = today) as WalletRepository.SaveResult.Saved).id
    private suspend fun wallet(id: Long) = wallets.observeWallets().first().first { it.id == id }
    private suspend fun record(kind: OutsideMovement, wallet: Long, minor: Long, reason: String, key: String, date: LocalDate = today) =
        transactions.recordOutsideMovement(kind, wallet, php(minor), date, reason, key, today)

    @Test fun aWithdrawalLowersTheBalanceWithoutCountingAsSpending() = runTest {
        val bank = create("BPI", WalletType.BANK, 50_000_00)
        assertTrue(record(OutsideMovement.WITHDRAWAL, bank, 12_000_00, "  Moved to my investment  ", "w1") is OutsideResult.Recorded)
        assertEquals(php(38_000_00), wallet(bank).balance)
        val row = transactions.observeTransactions().first().single()
        assertEquals(TransactionType.ADJUSTMENT_OUT, row.type)
        assertEquals("Withdrawal · Moved to my investment", row.note)
        assertEquals(OutsideMovement.WITHDRAWAL, OutsideMovement.of(row))
        val spent = Insights.monthlyTotals(transactions.observeTransactions().first(), "PHP", YearMonth.from(today), 1).single().amount
        assertEquals("never spending", php(0), spent)
    }

    @Test fun whatItWasForIsRequiredAndASecondTapRecordsOnce() = runTest {
        val cash = create("Cash", WalletType.CASH, 5_000_00)
        assertEquals(OutsideResult.ReasonRequired, record(OutsideMovement.WITHDRAWAL, cash, 1_000_00, "   ", "k0"))
        record(OutsideMovement.WITHDRAWAL, cash, 1_000_00, "Allowance", "same-key")
        record(OutsideMovement.WITHDRAWAL, cash, 1_000_00, "Allowance", "same-key")
        assertEquals(1, transactions.observeTransactions().first().size)
    }

    @Test fun withdrawalsAreForMoneyAccountsAndExternalPaymentsForDebts() = runTest {
        val card = create("Visa", WalletType.CREDIT_CARD, 1_000_00)
        val loan = create("Car loan", WalletType.LOAN, 1_000_00)
        val owed = create("Ana", WalletType.RECEIVABLE, 1_000_00)
        val savings = create("Savings", WalletType.SAVINGS, 1_000_00)
        listOf(card, loan, owed).forEach {
            assertEquals(OutsideResult.NotAllowed, record(OutsideMovement.WITHDRAWAL, it, 100, "x", "w$it"))
        }
        listOf(savings, owed).forEach {
            assertEquals(OutsideResult.NotAllowed, record(OutsideMovement.EXTERNAL_PAYMENT, it, 100, "x", "e$it"))
        }
        assertTrue(record(OutsideMovement.WITHDRAWAL, savings, 100, "x", "ks") is OutsideResult.Recorded)
        assertEquals(OutsideResult.FutureDate, record(OutsideMovement.WITHDRAWAL, savings, 100, "x", "kf", today.plusDays(1)))
        assertEquals(OutsideResult.NotPositive, record(OutsideMovement.WITHDRAWAL, savings, 0, "x", "kz"))
        assertEquals(
            OutsideResult.CurrencyMismatch,
            transactions.recordOutsideMovement(OutsideMovement.WITHDRAWAL, savings, Money(100, "USD"), today, "x", "ku", today),
        )
    }

    @Test fun anExternalPaymentLowersWhatIsOwedAndCountsAsALoanInstalment() = runTest {
        val loan = create(
            "Car loan", WalletType.LOAN, 120_000_00,
            DebtTerms(repayment = DebtRepayment.MONTHLY, termMonths = 12, installment = php(10_000_00)),
        )
        assertTrue(record(OutsideMovement.EXTERNAL_PAYMENT, loan, 10_000_00, "Paid by Mom as a gift", "x1") is OutsideResult.Recorded)
        val w = wallet(loan)
        assertEquals(php(110_000_00), w.balance)
        val row = transactions.observeTransactions().first().single()
        assertEquals(TransactionType.ADJUSTMENT_IN, row.type)
        assertEquals("External payment · Paid by Mom as a gift", row.note)
        val progress = LoanMath.progress(w, transactions.observeTransactions().first())!!
        assertEquals(1, progress.installmentsPaid)
        assertEquals(php(10_000_00), progress.paid)
        // Not income either.
        val income = Insights.monthlyTotals(transactions.observeTransactions().first(), "PHP", YearMonth.from(today), 1, TransactionType.INCOME)
        assertEquals(php(0), income.single().amount)
    }

    @Test fun anExternalPaymentOnACardLowersTheAmountDue() = runTest {
        val card = create("BPI Mastercard", WalletType.CREDIT_CARD, 30_000_00, DebtTerms(statementDay = 25, dueDay = 12))
        record(OutsideMovement.EXTERNAL_PAYMENT, card, 5_000_00, "Dad covered it", "x2")
        val cycle = CreditCardMath.cycle(wallet(card), transactions.observeTransactions().first(), today)!!
        assertEquals(php(25_000_00), cycle.amountDue)
    }

    @Test fun aNewCardsAmountDueIsWhatTheFirstReminderAsksFor() = runTest {
        // Statement on the 25th, due on the 12th: on 10 Nov the October 25 statement is due on 12 Nov.
        val terms = DebtTerms(statementDay = 25, dueDay = 12)
        val owed = php(30_000_00)
        val split = CardSetup.split(owed, php(12_000_00)) as CardSetup.Split.PartlyDue
        val card = create("BPI Mastercard", WalletType.CREDIT_CARD, owed.amountMinor, terms, unbilled = split.unbilled)
        val w = wallet(card)
        assertEquals("the card still owes everything", owed, w.balance)
        val cycle = CreditCardMath.cycle(w, transactions.observeTransactions().first(), today)!!
        assertEquals(php(12_000_00), cycle.amountDue)
        assertEquals(LocalDate.parse("2026-11-12"), cycle.dueDate)
        val row = transactions.observeTransactions().first().single()
        assertTrue(CardSetup.isUnbilledOpening(row))
        assertEquals(php(18_000_00), row.amount)
        assertEquals(today, row.date)
        // After the next statement (25 Nov) everything owed is on it.
        val later = CreditCardMath.cycle(w, transactions.observeTransactions().first(), LocalDate.parse("2026-11-26"))!!
        assertEquals(owed, later.amountDue)
    }

    @Test fun withoutAnAmountDueTheWholeBalanceIsDueAsBefore() = runTest {
        val card = create("Visa", WalletType.CREDIT_CARD, 30_000_00, DebtTerms(statementDay = 25, dueDay = 12))
        assertEquals(0, transactions.observeTransactions().first().size)
        assertEquals(php(30_000_00), CreditCardMath.cycle(wallet(card), emptyList(), today)!!.amountDue)
    }

    @Test fun anUnbilledPartIsIgnoredForAnythingButACard() = runTest {
        val bank = create("Bank", WalletType.BANK, 1_000_00, unbilled = php(500_00))
        assertEquals(php(1_000_00), wallet(bank).balance)
        assertEquals(0, transactions.observeTransactions().first().size)
    }
}
