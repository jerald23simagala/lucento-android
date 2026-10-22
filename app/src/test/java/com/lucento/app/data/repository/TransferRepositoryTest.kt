package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.TransactionRepository.TransferInput
import com.lucento.app.data.repository.TransactionRepository.TransferResult
import com.lucento.app.domain.DateRange
import com.lucento.app.domain.TransactionQueries
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class TransferRepositoryTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })

    private var bank = 0L
    private var cash = 0L
    private var card = 0L
    private var loan = 0L
    private var euro = 0L
    private val today = LocalDate.parse("2026-10-22")

    @Before fun setUp() = runTest {
        bank = (wallets.create("Bank", WalletType.BANK, Money(100_000, "PHP")) as WalletRepository.SaveResult.Saved).id
        cash = (wallets.create("Cash", WalletType.CASH, Money(5_000, "PHP")) as WalletRepository.SaveResult.Saved).id
        card = (wallets.create("Card", WalletType.CREDIT_CARD, Money(30_000, "PHP")) as WalletRepository.SaveResult.Saved).id
        loan = (wallets.create("Car loan", WalletType.LOAN, Money(0, "PHP")) as WalletRepository.SaveResult.Saved).id
        euro = (wallets.create("Euro", WalletType.CASH, Money(1_000, "EUR")) as WalletRepository.SaveResult.Saved).id
    }

    private fun transfer(from: Long, to: Long, minor: Long, uuid: String = "t-1", group: String? = null, code: String = "PHP") =
        TransferInput(group, uuid, from, to, Money(minor, code), today, "Move")

    private suspend fun balance(id: Long) = wallets.observeWallets().first().first { it.id == id }.balance

    @Test fun transferMovesMoneyAndConservesTheTotal() = runTest {
        val result = transactions.saveTransfer(transfer(bank, cash, 20_000))
        assertTrue(result is TransferResult.Saved)
        assertEquals(Money(80_000, "PHP"), balance(bank))
        assertEquals(Money(25_000, "PHP"), balance(cash))
        val legs = transactions.observeTransactions().first()
        assertEquals(setOf(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN), legs.map { it.type }.toSet())
        assertEquals(1, legs.map { it.transferGroup }.toSet().size)
    }

    @Test fun transfersAreNeverIncomeOrSpending() = runTest {
        transactions.saveTransfer(transfer(bank, cash, 20_000))
        val all = transactions.observeTransactions().first()
        val totals = TransactionQueries.totalsByCurrency(all, DateRange.monthOf(today), "PHP")
        assertTrue(totals.isEmpty() || (totals.single().income.isZero && totals.single().expense.isZero))
    }

    @Test fun payingACardIsATransferThatLowersWhatIsOwed() = runTest {
        transactions.saveTransfer(transfer(bank, card, 10_000))
        assertEquals(Money(20_000, "PHP"), balance(card))
        assertEquals(Money(90_000, "PHP"), balance(bank))
    }

    @Test fun drawingLoanProceedsRaisesDebtAndIsNotIncome() = runTest {
        transactions.saveTransfer(transfer(loan, bank, 50_000))
        assertEquals(Money(50_000, "PHP"), balance(loan))
        assertEquals(Money(150_000, "PHP"), balance(bank))
    }

    @Test fun creationIsIdempotent() = runTest {
        val a = transactions.saveTransfer(transfer(bank, cash, 1_000, uuid = "same"))
        val b = transactions.saveTransfer(transfer(bank, cash, 1_000, uuid = "same"))
        assertEquals(a, b)
        assertEquals(2, transactionDao.rows.value.size)
    }

    @Test fun rejectsInvalidTransfers() = runTest {
        assertEquals(TransferResult.SameWallet, transactions.saveTransfer(transfer(bank, bank, 100)))
        assertEquals(TransferResult.AmountNotPositive, transactions.saveTransfer(transfer(bank, cash, 0)))
        assertEquals(TransferResult.CurrencyMismatch, transactions.saveTransfer(transfer(bank, euro, 100)))
        assertEquals(TransferResult.WalletNotFound, transactions.saveTransfer(transfer(bank, 999, 100)))
        assertTrue(transactionDao.rows.value.isEmpty())
    }

    @Test fun editingUpdatesBothLegs() = runTest {
        val group = (transactions.saveTransfer(transfer(bank, cash, 1_000)) as TransferResult.Saved).group
        transactions.saveTransfer(transfer(bank, card, 3_000, uuid = "ignored", group = group))
        val pair = Transfer.pair(transactions.observeTransactions().first()).getValue(group)
        assertEquals(Money(3_000, "PHP"), pair.amount)
        assertEquals(card, pair.toWalletId)
        assertEquals(Money(5_000, "PHP"), balance(cash))
        assertEquals(Money(27_000, "PHP"), balance(card))
    }

    @Test fun deletingEitherLegDeletesTheWholeTransfer() = runTest {
        transactions.saveTransfer(transfer(bank, cash, 1_000))
        val inLeg = transactionDao.rows.value.first { it.type == "TRANSFER_IN" }
        transactions.delete(inLeg.id)
        assertTrue(transactionDao.rows.value.isEmpty())
        assertEquals(Money(100_000, "PHP"), balance(bank))
    }

    @Test fun legsCannotBeEditedAsIncomeOrExpense() = runTest {
        transactions.saveTransfer(transfer(bank, cash, 1_000))
        val leg = transactionDao.rows.value.first()
        val result = transactions.save(
            TransactionRepository.Input(leg.id, "x", TransactionType.EXPENSE, Money(1_000, "PHP"), bank, null, today, "")
        )
        assertEquals(TransactionRepository.SaveResult.InvalidType, result)
        val direct = transactions.save(
            TransactionRepository.Input(null, "y", TransactionType.TRANSFER_OUT, Money(1_000, "PHP"), bank, null, today, "")
        )
        assertEquals(TransactionRepository.SaveResult.InvalidType, direct)
    }
}
