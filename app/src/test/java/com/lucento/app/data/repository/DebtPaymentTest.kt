package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.RecurringRepository.Input
import com.lucento.app.data.repository.RecurringRepository.PayResult
import com.lucento.app.data.repository.RecurringRepository.SaveResult
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Debt terms on cards and loans, and recurring debt payments split into principal and interest. */
class DebtPaymentTest {

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
    private fun usd(minor: Long) = Money(minor, "USD")
    private var bank = 0L
    private var loan = 0L
    private var interestCategory = 0L

    private val terms = DebtTerms(interestRateBps = 1250, repayment = DebtRepayment.MONTHLY, termMonths = 24, installment = usd(23_654))

    @Before fun setUp() = runTest {
        bank = (wallets.create("Bank", WalletType.BANK, usd(1_000_000)) as WalletRepository.SaveResult.Saved).id
        loan = (wallets.create("Car loan", WalletType.LOAN, usd(500_000), terms) as WalletRepository.SaveResult.Saved).id
        interestCategory = categories.ensure(DebtMath.INTEREST_CATEGORY, CategoryKind.EXPENSE, DebtMath.INTEREST_ICON)!!
    }

    private suspend fun wallet(id: Long) = wallets.observeWallets().first().first { it.id == id }

    private fun payment(principal: Long = 18_446, interest: Long = 5_208, from: Long = bank, to: Long? = loan) = Input(
        id = null, name = "Monthly Payment - Car loan", type = TransactionType.EXPENSE, amount = usd(principal),
        walletId = from, categoryId = interestCategory, frequency = Frequency.MONTHLY, interval = 1,
        startDate = first, endDate = first.plusMonths(23), toWalletId = to, interest = usd(interest),
    )

    @Test fun termsAreKeptForACardOrLoanAndDroppedForAnAsset() = runTest {
        assertEquals(terms, wallet(loan).debt)
        wallets.update(bank, "Bank", WalletType.BANK, usd(1_000_000), terms)
        assertNull(wallet(bank).debt)
        wallets.update(loan, "Car loan", WalletType.LOAN, usd(500_000), null)
        assertNull(wallet(loan).debt)
    }

    @Test fun theInterestCategoryIsCreatedOnceAndRestoredIfArchived() = runTest {
        assertEquals(interestCategory, categories.ensure(DebtMath.INTEREST_CATEGORY, CategoryKind.EXPENSE, DebtMath.INTEREST_ICON))
        categoryDao.update(categoryDao.getById(interestCategory)!!.copy(archived = true))
        assertEquals(interestCategory, categories.ensure("debt interest", CategoryKind.EXPENSE, DebtMath.INTEREST_ICON))
        assertTrue(!categoryDao.getById(interestCategory)!!.archived)
        assertEquals(1, categoryDao.getAll().count { it.name == DebtMath.INTEREST_CATEGORY })
    }

    @Test fun payingRecordsInterestAsSpendingAndTheRestAgainstTheDebt() = runTest {
        val id = (recurring.save(payment()) as SaveResult.Saved).id
        assertTrue(recurring.markPaid(id) is PayResult.Recorded)

        val all = transactions.observeTransactions().first()
        val interest = all.single { it.type == TransactionType.EXPENSE }
        assertEquals(usd(5_208), interest.amount)
        assertEquals(loan, interest.walletId)
        assertEquals(interestCategory, interest.categoryId)
        val legs = all.filter { it.isTransfer }
        assertEquals(listOf(usd(23_654), usd(23_654)), legs.map { it.amount })
        // Bank pays the whole instalment; the loan goes down by the principal only.
        assertEquals(usd(1_000_000 - 23_654), wallet(bank).balance)
        assertEquals(usd(500_000 - 18_446), wallet(loan).balance)
        assertEquals(1, recurring.observeSchedules().first().single().nextIndex)
    }

    @Test fun theSplitCanChangeWhenMarkingPaid() = runTest {
        val id = (recurring.save(payment()) as SaveResult.Saved).id
        recurring.markPaid(id, amount = usd(19_000), interest = usd(4_654))
        assertEquals(usd(500_000 - 19_000), wallet(loan).balance)
        assertEquals(usd(1_000_000 - 23_654), wallet(bank).balance)
    }

    @Test fun noInterestMeansOnlyATransfer() = runTest {
        val id = (recurring.save(payment(principal = 23_654, interest = 0)) as SaveResult.Saved).id
        recurring.markPaid(id)
        assertTrue(transactions.observeTransactions().first().all { it.isTransfer })
        assertEquals(usd(500_000 - 23_654), wallet(loan).balance)
    }

    @Test fun refusesDebtPaymentsThatDontPayADebtFromMoney() = runTest {
        val eur = (wallets.create("Euro cash", WalletType.CASH, Money(0, "EUR")) as WalletRepository.SaveResult.Saved).id
        assertEquals(SaveResult.InvalidDebt, recurring.save(payment(to = bank)))
        assertEquals(SaveResult.InvalidDebt, recurring.save(payment(from = loan)))
        assertEquals(SaveResult.InvalidDebt, recurring.save(payment(interest = -1)))
        assertEquals(SaveResult.InvalidDebt, recurring.save(payment(to = null)))
        assertEquals(SaveResult.CurrencyMismatch, recurring.save(payment(from = eur)))
        assertTrue(recurringDao.rows.value.isEmpty())
    }

    @Test fun aDebtWithAPaymentPlanCantBeDeleted() = runTest {
        recurring.save(payment())
        assertTrue(wallets.delete(loan) is WalletRepository.DeleteResult.HasSchedules)
    }
}
