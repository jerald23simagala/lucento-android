package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.CategoryUsage
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.data.repository.TransactionRepository.Input
import com.lucento.app.data.repository.TransactionRepository.SaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class TransactionRepositoryTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1_000L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2_000L })
    private val categories = CategoryRepository(categoryDao, clock = { 1_000L })

    private var usdWallet = 0L
    private var eurWallet = 0L
    private var cardWallet = 0L
    private var groceries = 0L
    private var salary = 0L

    private val today = LocalDate.parse("2025-06-15")

    @Before fun setUp() = runTest {
        usdWallet = (wallets.create("Everyday", WalletType.BANK, Money(100_000, "USD")) as WalletRepository.SaveResult.Saved).id
        eurWallet = (wallets.create("Euro cash", WalletType.CASH, Money(5_000, "EUR")) as WalletRepository.SaveResult.Saved).id
        cardWallet = (wallets.create("Visa", WalletType.CREDIT_CARD, Money(20_000, "USD")) as WalletRepository.SaveResult.Saved).id
        groceries = (categories.create("Groceries", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
        salary = (categories.create("Salary", CategoryKind.INCOME) as CategoryRepository.SaveResult.Saved).id
    }

    private fun input(
        uuid: String = "uuid-1",
        id: Long? = null,
        type: TransactionType = TransactionType.EXPENSE,
        amount: Money = Money(4_50, "USD"),
        wallet: Long = usdWallet,
        category: Long? = groceries,
        note: String = "",
    ) = Input(id, uuid, type, amount, wallet, category, today, note)

    @Test fun createsATransactionAndStoresDateAsEpochDay() = runTest {
        val result = transactions.save(input(note = "  Milk and bread  "))
        assertTrue(result is SaveResult.Saved)
        val saved = transactions.observeTransactions().first().single()
        assertEquals(Money(450, "USD"), saved.amount)
        assertEquals(today, saved.date)
        assertEquals("Milk and bread", saved.note)
        assertEquals(TransactionType.EXPENSE, saved.type)
    }

    @Test fun repeatedSubmissionWithSameKeyDoesNotDuplicate() = runTest {
        val first = transactions.save(input(uuid = "same")) as SaveResult.Saved
        val second = transactions.save(input(uuid = "same")) as SaveResult.Saved
        assertEquals(first.id, second.id)
        assertEquals(1, transactions.observeTransactions().first().size)
    }

    @Test fun distinctKeysCreateDistinctTransactions() = runTest {
        transactions.save(input(uuid = "a"))
        transactions.save(input(uuid = "b"))
        assertEquals(2, transactions.observeTransactions().first().size)
    }

    @Test fun rejectsZeroAndNegativeAmounts() = runTest {
        assertEquals(SaveResult.AmountNotPositive, transactions.save(input(amount = Money(0, "USD"))))
        assertEquals(SaveResult.AmountNotPositive, transactions.save(input(uuid = "n", amount = Money(-5, "USD"))))
        assertTrue(transactions.observeTransactions().first().isEmpty())
    }

    @Test fun rejectsCurrencyThatDiffersFromTheWallet() = runTest {
        assertEquals(SaveResult.CurrencyMismatch, transactions.save(input(amount = Money(100, "EUR"), wallet = usdWallet)))
        assertTrue(transactions.observeTransactions().first().isEmpty())
    }

    @Test fun rejectsUnknownWallet() = runTest {
        assertEquals(SaveResult.WalletNotFound, transactions.save(input(wallet = 999)))
    }

    @Test fun rejectsCategoryOfTheWrongKind() = runTest {
        assertEquals(SaveResult.CategoryMismatch, transactions.save(input(type = TransactionType.EXPENSE, category = salary)))
        assertEquals(SaveResult.CategoryMismatch, transactions.save(input(uuid = "x", category = 999)))
    }

    @Test fun categoryIsOptional() = runTest {
        assertTrue(transactions.save(input(category = null)) is SaveResult.Saved)
    }

    @Test fun updateChangesFieldsButKeepsIdentity() = runTest {
        val id = (transactions.save(input()) as SaveResult.Saved).id
        val result = transactions.save(
            input(id = id, type = TransactionType.INCOME, amount = Money(9_99, "USD"), category = salary, note = "Refund")
        )
        assertEquals(SaveResult.Saved(id), result)
        val row = transactions.observeTransactions().first().single()
        assertEquals(id, row.id)
        assertEquals(TransactionType.INCOME, row.type)
        assertEquals(Money(999, "USD"), row.amount)
        assertEquals(salary, row.categoryId)
        assertEquals(1, transactionDao.rows.value.size)
    }

    @Test fun updatingAMissingTransactionFails() = runTest {
        assertEquals(SaveResult.TransactionNotFound, transactions.save(input(id = 42)))
    }

    @Test fun deleteRemovesTheTransaction() = runTest {
        val id = (transactions.save(input()) as SaveResult.Saved).id
        transactions.delete(id)
        assertTrue(transactions.observeTransactions().first().isEmpty())
    }

    // ---- wallet balances derived from transactions --------------------------------------------

    @Test fun walletBalanceReflectsIncomeAndExpenses() = runTest {
        transactions.save(input(uuid = "1", amount = Money(2_500, "USD")))                                  // -25.00
        transactions.save(input(uuid = "2", type = TransactionType.INCOME, amount = Money(50_000, "USD"), category = salary))
        val bank = wallets.observeWallets().first().first { it.id == usdWallet }
        assertEquals(Money(100_000 - 2_500 + 50_000, "USD"), bank.balance)
        assertEquals(Money(100_000, "USD"), bank.openingBalance)
    }

    @Test fun creditCardPurchaseIncreasesAmountOwed() = runTest {
        transactions.save(input(uuid = "1", amount = Money(4_000, "USD"), wallet = cardWallet))
        transactions.save(input(uuid = "2", type = TransactionType.INCOME, amount = Money(1_000, "USD"), wallet = cardWallet, category = salary))
        val card = wallets.observeWallets().first().first { it.id == cardWallet }
        assertEquals(Money(23_000, "USD"), card.balance) // owed 200 + 40 - 10
        assertEquals(Money(-23_000, "USD"), card.netWorthContribution)
    }

    @Test fun otherWalletsAreUnaffected() = runTest {
        transactions.save(input(uuid = "1", amount = Money(2_500, "USD")))
        val euro = wallets.observeWallets().first().first { it.id == eurWallet }
        assertEquals(Money(5_000, "EUR"), euro.balance)
    }

    // ---- wallet guards -------------------------------------------------------------------------

    @Test fun walletWithTransactionsCannotBeDeleted() = runTest {
        transactions.save(input())
        assertEquals(WalletRepository.DeleteResult.HasTransactions(1), wallets.delete(usdWallet))
        assertTrue(walletDao.rows.value.any { it.id == usdWallet })
    }

    @Test fun emptyWalletCanBeDeleted() = runTest {
        assertEquals(WalletRepository.DeleteResult.Deleted, wallets.delete(eurWallet))
        assertTrue(walletDao.rows.value.none { it.id == eurWallet })
    }

    @Test fun walletCurrencyIsLockedOnceTransactionsExist() = runTest {
        transactions.save(input())
        val result = wallets.update(usdWallet, "Everyday", WalletType.BANK, Money(100_000, "EUR"))
        assertEquals(WalletRepository.SaveResult.CurrencyLocked(com.lucento.app.domain.WalletUsage(1, 0)), result)
        assertEquals(WalletRepository.SaveResult.Saved(usdWallet), wallets.update(usdWallet, "Renamed", WalletType.BANK, Money(1, "USD")))
    }

    @Test fun walletCurrencyCanChangeBeforeAnyTransactions() = runTest {
        val result = wallets.update(eurWallet, "Euro cash", WalletType.CASH, Money(5_000, "GBP"))
        assertEquals(WalletRepository.SaveResult.Saved(eurWallet), result)
    }

    // ---- categories ----------------------------------------------------------------------------

    @Test fun categoryNamesAreUniquePerKindIgnoringCase() = runTest {
        assertEquals(CategoryRepository.SaveResult.DuplicateName, categories.create("groceries", CategoryKind.EXPENSE))
        assertTrue(categories.create("Groceries", CategoryKind.INCOME) is CategoryRepository.SaveResult.Saved)
        assertEquals(CategoryRepository.SaveResult.BlankName, categories.create("   ", CategoryKind.EXPENSE))
    }

    @Test fun usedCategoryIsArchivedNotDeleted() = runTest {
        categoryDao.transactionCounts[groceries] = 3
        assertEquals(CategoryRepository.DeleteResult.Archived(CategoryUsage(3, 0, 0)), categories.remove(groceries))
        assertTrue(categoryDao.rows.value.first { it.id == groceries }.archived)
    }

    @Test fun unusedCategoryIsDeleted() = runTest {
        assertEquals(CategoryRepository.DeleteResult.Deleted, categories.remove(salary))
        assertTrue(categoryDao.rows.value.none { it.id == salary })
    }

    @Test fun renameValidatesAndPersists() = runTest {
        assertEquals(CategoryRepository.SaveResult.Saved(groceries), categories.rename(groceries, "Food"))
        assertEquals("Food", categoryDao.rows.value.first { it.id == groceries }.name)
        assertEquals(CategoryRepository.SaveResult.NotFound, categories.rename(999, "x"))
    }
}
