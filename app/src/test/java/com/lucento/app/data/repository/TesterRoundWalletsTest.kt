package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletKind
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Tester round (2026-11-17): investment wallets with a kind and an icon, the spending-money choice, and scheduled
 * transfers between the person's own accounts.
 */
class TesterRoundWalletsTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val recurringDao = FakeRecurringDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L }, scheduleCount = { recurringDao.countForWallet(it) })
    private val categories = CategoryRepository(categoryDao, clock = { 1L })
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2L })
    private val recurring = RecurringRepository(recurringDao, walletDao, categoryDao, transactions, clock = { 5L })

    private var bank = 0L
    private var mp2 = 0L
    private var card = 0L
    private var rent = 0L
    private val start = LocalDate.parse("2026-01-31")

    private fun saved(result: WalletRepository.SaveResult) = (result as WalletRepository.SaveResult.Saved).id

    @Before fun setUp() = runTest {
        bank = saved(wallets.create("Bank", WalletType.BANK, Money(100_000, "USD")))
        mp2 = saved(
            wallets.create(
                "MP2 2026", WalletType.INVESTMENT, Money(50_000, "USD"),
                extras = WalletExtras(kind = WalletKind.MP2, iconKey = "sprout"),
            ),
        )
        card = saved(
            wallets.create(
                "Visa", WalletType.CREDIT_CARD, Money(2_000, "USD"),
                debt = DebtTerms(creditLimit = Money(60_000, "USD")),
                extras = WalletExtras(spendable = true, spendableCredit = Money(10_000, "USD")),
            ),
        )
        rent = (categories.create("Rent", CategoryKind.EXPENSE) as CategoryRepository.SaveResult.Saved).id
    }

    private suspend fun wallet(id: Long) = wallets.observeWallets().first().single { it.id == id }

    // ---- wallets

    @Test fun anInvestmentKeepsItsKindAndIconAndIsNotSpendingMoneyByDefault() = runTest {
        val investment = wallet(mp2)
        assertEquals(WalletType.INVESTMENT, investment.type)
        assertEquals(WalletKind.MP2, investment.kind)
        assertEquals("sprout", investment.iconKey)
        assertNull(investment.spendable)
        assertFalse(investment.isSpendingMoney)
        assertTrue(wallet(bank).isSpendingMoney)
    }

    @Test fun aCardKeepsItsChosenCreditAndOnlyWhatFitsTheTypeIsStored() = runTest {
        val visa = wallet(card)
        assertEquals(true, visa.spendable)
        assertEquals(Money(10_000, "USD"), visa.spendableCredit)
        // A stock kind or a card's credit never sticks to a bank account; an icon that isn't one is dropped.
        val odd = saved(
            wallets.create(
                "Everyday", WalletType.BANK, Money(0, "USD"),
                extras = WalletExtras(kind = WalletKind.STOCKS, iconKey = "not_an_icon", spendable = false, spendableCredit = Money(500, "USD")),
            ),
        )
        val entity = walletDao.getById(odd)!!
        assertNull(entity.kind)
        assertNull(entity.iconKey)
        assertEquals(false, entity.spendable)
        assertNull(entity.spendableCreditMinor)
    }

    @Test fun anEditWithoutExtrasKeepsThemAndATypeChangeDropsAKindThatNoLongerFits() = runTest {
        assertTrue(wallets.update(mp2, "MP2", WalletType.INVESTMENT, Money(50_000, "USD")) is WalletRepository.SaveResult.Saved)
        assertEquals(WalletKind.MP2, wallet(mp2).kind)
        assertEquals("sprout", wallet(mp2).iconKey)
        // Savings has no kinds; the chosen icon stays.
        assertTrue(wallets.update(mp2, "MP2", WalletType.SAVINGS, Money(50_000, "USD"), null, null) is WalletRepository.SaveResult.Saved)
        assertNull(wallet(mp2).kind)
        assertEquals("sprout", wallet(mp2).iconKey)
    }

    @Test fun spendingMoneyCanBeSwitchedPerWalletButNeverForALoan() = runTest {
        assertTrue(wallets.setSpendable(mp2, true) is WalletRepository.SaveResult.Saved)
        assertTrue(wallet(mp2).isSpendingMoney)
        assertTrue(wallets.setSpendable(bank, false) is WalletRepository.SaveResult.Saved)
        assertFalse(wallet(bank).isSpendingMoney)
        val loan = saved(wallets.create("Car loan", WalletType.LOAN, Money(80_000, "USD")))
        wallets.setSpendable(loan, true)
        assertNull(walletDao.getById(loan)!!.spendable)
        assertFalse(wallet(loan).isSpendingMoney)
    }

    // ---- scheduled transfers

    private fun transferInput(from: Long = bank, to: Long? = mp2, category: Long? = null, interest: Money? = null) =
        RecurringRepository.Input(
            null, "MP2 contribution", TransactionType.TRANSFER_OUT, Money(1_000, "USD"), from, category,
            Frequency.MONTHLY, 1, start, null, toWalletId = to, interest = interest,
        )

    @Test fun aScheduledTransferMovesMoneyBetweenTwoMoneyAccounts() = runTest {
        val result = recurring.save(transferInput())
        assertTrue(result is RecurringRepository.SaveResult.Saved)
        val schedule = recurring.observeSchedules().first().single()
        assertTrue(schedule.isTransfer)
        assertNull(schedule.categoryId)
        assertEquals(mp2, schedule.toWalletId)
    }

    @Test fun aScheduledTransferIsRefusedWithoutTwoMoneyAccountsInOneCurrencyOrWithACategory() = runTest {
        val usd = RecurringRepository.SaveResult.InvalidTransfer
        assertEquals(usd, recurring.save(transferInput(to = null)))
        assertEquals(usd, recurring.save(transferInput(to = bank)))
        assertEquals(usd, recurring.save(transferInput(to = card))) // a card's payment has its own schedule
        assertEquals(usd, recurring.save(transferInput(category = rent)))
        assertEquals(usd, recurring.save(transferInput(interest = Money(10, "USD"))))
        val euros = saved(wallets.create("Euros", WalletType.SAVINGS, Money(0, "EUR")))
        assertEquals(usd, recurring.save(transferInput(to = euros)))
        val house = saved(wallets.create("House", WalletType.OTHER_ASSET, Money(1_000_000, "USD")))
        assertEquals(usd, recurring.save(transferInput(to = house)))
        assertTrue(recurring.observeSchedules().first().isEmpty())
    }

    @Test fun markingAScheduledTransferDoneRecordsOneTransferOnceAndAdvances() = runTest {
        val id = (recurring.save(transferInput()) as RecurringRepository.SaveResult.Saved).id
        assertTrue(recurring.markPaid(id) is RecurringRepository.PayResult.Recorded)
        val legs = transactions.observeTransactions().first()
        assertEquals(setOf(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN), legs.map { it.type }.toSet())
        assertEquals(Money(99_000, "USD"), wallet(bank).balance)
        assertEquals(Money(51_000, "USD"), wallet(mp2).balance)
        assertEquals(1, recurring.observeSchedules().first().single().nextIndex)
        // Never income or spending.
        assertTrue(legs.none { it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME })
        // The next one records the next occurrence; the same occurrence can't be recorded twice.
        assertTrue(recurring.markPaid(id, amount = Money(1_500, "USD")) is RecurringRepository.PayResult.Recorded)
        assertEquals(4, transactions.observeTransactions().first().size)
        assertEquals(Money(97_500, "USD"), wallet(bank).balance)
    }
}
