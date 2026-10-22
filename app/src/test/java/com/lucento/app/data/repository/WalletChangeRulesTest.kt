package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.WalletRepository.SaveResult
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletChangePolicy
import com.lucento.app.domain.WalletChangeViolation
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.WalletUsage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Wallet type and currency edits must never reinterpret recorded history (audit findings 1.1 and 1.2). */
class WalletChangeRulesTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val recurringDao = FakeRecurringDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L }, scheduleCount = { recurringDao.countForWallet(it) })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
    private val recurring = RecurringRepository(recurringDao, walletDao, categoryDao, transactions, clock = { 3L })
    private val day = LocalDate.parse("2026-10-01")

    private var bank = 0L
    private var cash = 0L
    private var food = 0L
    private var pay = 0L

    @Before fun setUp() = runTest {
        bank = (wallets.create("Bank", WalletType.BANK, Money(100_000, "PHP")) as SaveResult.Saved).id
        cash = (wallets.create("Cash", WalletType.CASH, Money(5_000, "PHP")) as SaveResult.Saved).id
        food = (CategoryRepository(categoryDao).create("Food", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
        pay = (CategoryRepository(categoryDao).create("Pay", CategoryKind.INCOME) as CategoryRepository.SaveResult.Saved).id
    }

    private suspend fun record(type: TransactionType, wallet: Long, minor: Long, key: String) =
        transactions.save(
            TransactionRepository.Input(null, key, type, Money(minor, "PHP"), wallet, if (type == TransactionType.INCOME) pay else food, day, "")
        )

    private suspend fun stored(id: Long) = walletDao.getById(id)!!
    private suspend fun balance(id: Long) = wallets.observeWallets().first().first { it.id == id }.balance

    // ---- type changes

    @Test fun emptyWalletMayChangeToAnyType() = runTest {
        assertEquals(SaveResult.Saved(bank), wallets.update(bank, "Bank", WalletType.CREDIT_CARD, Money(100_000, "PHP")))
        assertEquals("CREDIT_CARD", stored(bank).type)
        assertEquals(SaveResult.Saved(bank), wallets.update(bank, "Bank", WalletType.SAVINGS, Money(100_000, "PHP")))
    }

    @Test fun walletWithIncomeCannotBecomeALiability() = runTest {
        record(TransactionType.INCOME, bank, 20_000, "i1")
        val before = balance(bank)
        val result = wallets.update(bank, "Bank", WalletType.LOAN, Money(100_000, "PHP"))
        assertEquals(SaveResult.TypeLocked(WalletUsage(1, 0)), result)
        assertEquals("BANK", stored(bank).type)
        assertEquals(before, balance(bank))
        assertEquals(Money(120_000, "PHP"), balance(bank))
    }

    @Test fun walletWithExpensesCannotBecomeALiability() = runTest {
        record(TransactionType.EXPENSE, cash, 1_000, "e1")
        assertTrue(wallets.update(cash, "Cash", WalletType.CREDIT_CARD, Money(5_000, "PHP")) is SaveResult.TypeLocked)
        assertEquals(Money(4_000, "PHP"), balance(cash))
    }

    @Test fun liabilityWithHistoryCannotBecomeAnAsset() = runTest {
        val card = (wallets.create("Card", WalletType.CREDIT_CARD, Money(0, "PHP")) as SaveResult.Saved).id
        record(TransactionType.EXPENSE, card, 3_000, "card-1")
        assertTrue(wallets.update(card, "Card", WalletType.BANK, Money(0, "PHP")) is SaveResult.TypeLocked)
        assertEquals(Money(3_000, "PHP"), balance(card)) // still the amount owed
    }

    @Test fun walletWithTransferHistoryIsLockedOnBothSides() = runTest {
        transactions.saveTransfer(TransactionRepository.TransferInput(null, "t1", bank, cash, Money(10_000, "PHP"), day, ""))
        assertTrue(wallets.update(bank, "Bank", WalletType.CREDIT_CARD, Money(100_000, "PHP")) is SaveResult.TypeLocked)
        assertTrue(wallets.update(cash, "Cash", WalletType.LOAN, Money(5_000, "PHP")) is SaveResult.TypeLocked)
        assertEquals(Money(90_000, "PHP"), balance(bank))
        assertEquals(Money(15_000, "PHP"), balance(cash))
    }

    @Test fun walletReferencedOnlyByARecurringScheduleIsLocked() = runTest {
        recurring.save(
            RecurringRepository.Input(null, "Rent", TransactionType.EXPENSE, Money(9_000, "PHP"), bank, food, Frequency.MONTHLY, 1, day, null)
        )
        assertEquals(SaveResult.TypeLocked(WalletUsage(0, 1)), wallets.update(bank, "Bank", WalletType.LOAN, Money(100_000, "PHP")))
    }

    @Test fun sameSideTypeChangesStayAllowedForUsedWallets() = runTest {
        record(TransactionType.EXPENSE, bank, 1_000, "e1")
        assertEquals(SaveResult.Saved(bank), wallets.update(bank, "Bank", WalletType.SAVINGS, Money(100_000, "PHP")))
        assertEquals(Money(99_000, "PHP"), balance(bank))
        val card = (wallets.create("Card", WalletType.CREDIT_CARD, Money(0, "PHP")) as SaveResult.Saved).id
        record(TransactionType.EXPENSE, card, 500, "c1")
        assertEquals(SaveResult.Saved(card), wallets.update(card, "Card", WalletType.LOAN, Money(0, "PHP")))
    }

    @Test fun rejectedChangeLeavesEveryFieldUntouched() = runTest {
        record(TransactionType.INCOME, bank, 1_000, "i1")
        val before = stored(bank)
        wallets.update(bank, "Renamed", WalletType.CREDIT_CARD, Money(1, "USD"))
        assertEquals(before, stored(bank))
        assertEquals(1, transactionDao.rows.value.size)
        assertEquals("PHP", transactionDao.rows.value.single().currencyCode)
    }

    @Test fun updatingAMissingWalletIsNotFound() = runTest {
        assertEquals(SaveResult.NotFound, wallets.update(999, "x", WalletType.CASH, Money(0, "PHP")))
    }

    // ---- currency changes

    @Test fun unusedWalletMayChangeCurrency() = runTest {
        assertEquals(SaveResult.Saved(cash), wallets.update(cash, "Cash", WalletType.CASH, Money(5_000, "USD")))
        assertEquals("USD", stored(cash).currencyCode)
    }

    @Test fun currencyIsLockedByTransactions() = runTest {
        record(TransactionType.EXPENSE, cash, 100, "e1")
        assertEquals(SaveResult.CurrencyLocked(WalletUsage(1, 0)), wallets.update(cash, "Cash", WalletType.CASH, Money(5_000, "USD")))
    }

    /** Regression for 1.2: an unused wallet with a schedule could switch currency, after which paying failed. */
    @Test fun currencyIsLockedByARecurringScheduleSoItCanStillBePaid() = runTest {
        val id = (
            recurring.save(
                RecurringRepository.Input(null, "Netflix", TransactionType.EXPENSE, Money(549, "PHP"), cash, food, Frequency.MONTHLY, 1, day, null)
            ) as RecurringRepository.SaveResult.Saved
            ).id
        assertEquals(0, transactionDao.countForWallet(cash))
        val change = wallets.update(cash, "Cash", WalletType.CASH, Money(5_000, "USD"))
        assertEquals(SaveResult.CurrencyLocked(WalletUsage(0, 1)), change)
        assertEquals("PHP", stored(cash).currencyCode)
        assertEquals("PHP", recurringDao.getById(id)!!.currencyCode)
        assertTrue(recurring.markPaid(id) is RecurringRepository.PayResult.Recorded)
        assertEquals(Money(549, "PHP"), transactionDao.rows.value.single().let { Money(it.amountMinor, it.currencyCode) })
    }

    @Test fun walletUsageCountsTransactionsAndSchedules() = runTest {
        record(TransactionType.EXPENSE, bank, 100, "e1")
        recurring.save(RecurringRepository.Input(null, "Gym", TransactionType.EXPENSE, Money(100, "PHP"), bank, food, Frequency.WEEKLY, 1, day, null))
        assertEquals(WalletUsage(1, 1), wallets.usage(bank))
        assertEquals(WalletUsage.Unused, wallets.usage(cash))
    }

    // ---- the pure policy

    @Test fun policyMatrix() {
        val used = WalletUsage(1, 0)
        assertNull(WalletChangePolicy.check(WalletType.CASH, "PHP", WalletType.LOAN, "USD", WalletUsage.Unused))
        assertNull(WalletChangePolicy.check(WalletType.CASH, "PHP", WalletType.BANK, "PHP", used))
        assertNull(WalletChangePolicy.check(WalletType.CREDIT_CARD, "PHP", WalletType.LOAN, "PHP", used))
        assertEquals(WalletChangeViolation.TypeLocked(used), WalletChangePolicy.check(WalletType.E_WALLET, "PHP", WalletType.CREDIT_CARD, "PHP", used))
        assertEquals(WalletChangeViolation.CurrencyLocked(used), WalletChangePolicy.check(WalletType.CASH, "PHP", WalletType.CASH, "EUR", used))
    }
}
