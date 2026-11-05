package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.RecurringRepository.Input
import com.lucento.app.data.repository.RecurringRepository.PayResult
import com.lucento.app.data.repository.RecurringRepository.SaveResult
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.LoanMath
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.ScheduledFlow
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.flow
import com.lucento.app.domain.signedOccurrence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Owner round 10: a Receivable wallet — a loan in reverse. It is an asset with a loan's terms; instalments come back as
 * transfers out of it into a money account (scheduled as collections), and interest received is income.
 */
class ReceivableTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val recurringDao = FakeRecurringDao()
    private val termsDao = FakeDebtTermsDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L }, scheduleCount = { recurringDao.countForWallet(it) }, debtTerms = termsDao)
    private val categories = CategoryRepository(categoryDao, clock = { 1L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
    private val recurring = RecurringRepository(recurringDao, walletDao, categoryDao, transactions, clock = { 3L })

    private val first = LocalDate.parse("2026-11-05")
    private fun php(minor: Long) = Money(minor, "PHP")
    private var bank = 0L
    private var lent = 0L
    private var loan = 0L
    private var interestIncome = 0L

    /** ₱60,000 lent to Ana, repaid ₱5,300 a month over 12 months at 10%. */
    private val terms = DebtTerms(interestRateBps = 1000, repayment = DebtRepayment.MONTHLY, termMonths = 12, installment = php(530_000))

    @Before fun setUp() = runTest {
        bank = (wallets.create("Bank", WalletType.BANK, php(10_000_000)) as WalletRepository.SaveResult.Saved).id
        lent = (wallets.create("Loan to Ana", WalletType.RECEIVABLE, php(6_000_000), terms) as WalletRepository.SaveResult.Saved).id
        loan = (wallets.create("Car loan", WalletType.LOAN, php(50_000_000)) as WalletRepository.SaveResult.Saved).id
        interestIncome = categories.ensure(DebtMath.INTEREST_INCOME_CATEGORY, CategoryKind.INCOME, DebtMath.INTEREST_INCOME_ICON)!!
    }

    private suspend fun wallet(id: Long) = wallets.observeWallets().first().first { it.id == id }

    private fun collection(principal: Long = 480_000, interest: Long = 50_000, into: Long = bank, from: Long? = lent, type: TransactionType = TransactionType.INCOME) = Input(
        id = null, name = "Monthly Collection - Loan to Ana", type = type, amount = php(principal),
        walletId = into, categoryId = interestIncome, frequency = Frequency.MONTHLY, interval = 1,
        startDate = first, endDate = first.plusMonths(11), toWalletId = from, interest = php(interest),
    )

    @Test fun aReceivableIsAnAssetThatKeepsALoansTerms() = runTest {
        val receivable = wallet(lent)
        assertEquals(terms, receivable.debt)
        assertFalse(receivable.type.isLiability)
        assertFalse(receivable.type.isSpendable)
        // What Ana owes counts towards net worth: bank + receivable − car loan.
        val summary = NetWorthCalculator.summarize(wallets.observeWallets().first(), "PHP")
        assertEquals(php(10_000_000 + 6_000_000 - 50_000_000), summary.totalFor("PHP"))
        // Switching it to a plain asset drops the terms, as for any asset.
        wallets.update(lent, "Loan to Ana", WalletType.SAVINGS, php(6_000_000), terms)
        assertEquals(null, wallet(lent).debt)
    }

    @Test fun collectingRecordsInterestAsIncomeAndTheRestAsATransfer() = runTest {
        val id = (recurring.save(collection()) as SaveResult.Saved).id
        assertTrue(recurring.markPaid(id) is PayResult.Recorded)

        val all = transactions.observeTransactions().first()
        val interest = all.single { it.type == TransactionType.INCOME }
        assertEquals(php(50_000), interest.amount)
        assertEquals(lent, interest.walletId)
        assertEquals(interestIncome, interest.categoryId)
        val legs = all.filter { it.isTransfer }
        assertEquals(listOf(php(530_000), php(530_000)), legs.map { it.amount })
        assertEquals(lent, legs.single { it.type == TransactionType.TRANSFER_OUT }.walletId)
        assertEquals(bank, legs.single { it.type == TransactionType.TRANSFER_IN }.walletId)
        // The bank receives the whole instalment; what Ana owes goes down by the principal only.
        assertEquals(php(10_000_000 + 530_000), wallet(bank).balance)
        assertEquals(php(6_000_000 - 480_000), wallet(lent).balance)
        assertEquals(1, recurring.observeSchedules().first().single().nextIndex)
        // The next mark records next month's instalment (each occurrence once, keyed by its date).
        recurring.markPaid(id)
        assertEquals(php(6_000_000 - 2 * 480_000), wallet(lent).balance)
        assertEquals(6, transactions.observeTransactions().first().size)
    }

    @Test fun noInterestMeansOnlyATransfer() = runTest {
        val id = (recurring.save(collection(principal = 530_000, interest = 0)) as SaveResult.Saved).id
        recurring.markPaid(id)
        assertTrue(transactions.observeTransactions().first().all { it.isTransfer })
        assertEquals(php(6_000_000 - 530_000), wallet(lent).balance)
    }

    @Test fun refusesCollectionsThatDontComeFromAReceivableIntoMoney() = runTest {
        val card = (wallets.create("Visa", WalletType.CREDIT_CARD, php(0)) as WalletRepository.SaveResult.Saved).id
        val other = (wallets.create("Loan to Ben", WalletType.RECEIVABLE, php(100_000)) as WalletRepository.SaveResult.Saved).id
        assertEquals(SaveResult.InvalidDebt, recurring.save(collection(from = loan))) // income from a loan
        assertEquals(SaveResult.InvalidDebt, recurring.save(collection(from = bank))) // income from a bank
        assertEquals(SaveResult.InvalidDebt, recurring.save(collection(into = card))) // into a card
        assertEquals(SaveResult.InvalidDebt, recurring.save(collection(into = other))) // into another receivable
        assertEquals(SaveResult.InvalidDebt, recurring.save(collection(into = lent))) // into itself
        // A debt payment can't "pay" a receivable either (no category: the debt-interest one would be filled in later).
        assertEquals(SaveResult.InvalidDebt, recurring.save(collection(type = TransactionType.EXPENSE).copy(categoryId = null)))
        assertTrue(recurringDao.rows.value.isEmpty())
    }

    @Test fun aCollectionIsATransferInTheOutlookAndMoneyComingIn() = runTest {
        val id = (recurring.save(collection()) as SaveResult.Saved).id
        val schedule: RecurringSchedule = recurring.observeSchedules().first().single { it.id == id }
        assertTrue(schedule.isCollection)
        assertFalse(schedule.isDebtPayment)
        assertEquals(ScheduledFlow.TRANSFER, schedule.flow)
        assertEquals(php(530_000), schedule.signedOccurrence)
        // Never counted as a bill: committed expenses ignore it.
        assertEquals(php(0), com.lucento.app.domain.RecurringMath.committedExpenses(listOf(schedule), "PHP", first.plusMonths(3)))
    }

    @Test fun progressCountsInstalmentsReceived() = runTest {
        val id = (recurring.save(collection()) as SaveResult.Saved).id
        recurring.markPaid(id)
        val all = transactions.observeTransactions().first()
        val progress = LoanMath.progress(wallet(lent), all.filter { it.walletId == lent })!!
        assertEquals(1, progress.installmentsPaid)
        assertEquals(12, progress.installments)
        assertEquals(php(480_000), progress.paid)
        assertEquals(php(6_000_000 - 480_000), progress.remaining)
    }

    @Test fun recordPaymentGoesToTheAccountTheLastInstalmentWentTo() = runTest {
        val cash = (wallets.create("Cash", WalletType.CASH, php(1)) as WalletRepository.SaveResult.Saved).id
        val all = wallets.observeWallets().first()
        // Nothing received yet: the money account with the most money.
        assertEquals(bank, CreditCardMath.receivingWallet(wallet(lent), all, emptyList())?.id)
        transactions.saveTransfer(
            TransactionRepository.TransferInput(group = null, clientUuid = "tr-1", fromWalletId = lent, toWalletId = cash, amount = php(530_000), date = first, note = "Ana")
        )
        val history = transactions.observeTransactions().first()
        assertEquals(cash, CreditCardMath.receivingWallet(wallet(lent), wallets.observeWallets().first(), history)?.id)
        // A card's payment never comes from a receivable.
        val card = (wallets.create("Visa", WalletType.CREDIT_CARD, php(0)) as WalletRepository.SaveResult.Saved).id
        val payer = CreditCardMath.payingWallet(wallet(card), wallets.observeWallets().first(), history)
        assertTrue(payer?.type != WalletType.RECEIVABLE)
    }
}
