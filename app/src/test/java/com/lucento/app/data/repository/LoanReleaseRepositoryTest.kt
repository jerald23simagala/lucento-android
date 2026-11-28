package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Insights
import com.lucento.app.domain.LoanMath
import com.lucento.app.domain.LoanRelease
import com.lucento.app.domain.Transfer
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.fx.FxSource
import com.lucento.app.domain.fx.FxValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tester round (2026-11-27): a new loan saved with its proceeds and charges, or a new receivable with the money lent,
 * in one database transaction: the wallet opens at zero and these rows make up what is owed.
 */
class LoanReleaseRepositoryTest {

    private val walletDao = FakeWalletDao()
    private val categoryDao = FakeCategoryDao()
    private val transactionDao = FakeTransactionDao()
    private val categories = CategoryRepository(categoryDao, clock = { 500L })
    private var feeLookups = 0
    private val wallets = WalletRepository(
        walletDao, transactionDao, clock = { 1_000L }, debtTerms = FakeDebtTermsDao(),
        feeCategory = {
            feeLookups++
            categories.ensure(LoanRelease.FEES_CATEGORY, CategoryKind.EXPENSE, LoanRelease.FEES_ICON)
        },
    )
    private val transactions = TransactionRepository(transactionDao, walletDao, categoryDao, clock = { 2_000L })
    private val day = LocalDate.parse("2026-11-27")
    private val month = YearMonth.from(day)
    private val monthly = DebtTerms(repayment = DebtRepayment.MONTHLY, termMonths = 24, installment = Money(5_000_00, "PHP"))

    private fun php(minor: Long) = Money(minor, "PHP")
    private fun usd(minor: Long) = Money(minor, "USD")
    private suspend fun account(name: String, type: WalletType, money: Money) =
        (wallets.create(name, type, money) as WalletRepository.SaveResult.Saved).id
    private suspend fun wallet(id: Long) = wallets.observeWallets().first().first { it.id == id }
    private suspend fun all() = transactions.observeTransactions().first()
    private fun release(account: Long, moved: Money, charges: Money, key: String = "rel-1", fx: LoanRelease.Values = LoanRelease.Values()) =
        WalletRepository.Release(accountId = account, moved = moved, charges = charges, date = day, key = key, fx = fx)

    @Test fun aNewLoansProceedsGoIntoTheAccountAndItsChargesStayOnItAsSpending() = runTest {
        val bank = account("BDO Savings", WalletType.BANK, php(20_000_00))
        val saved = wallets.create(
            "Car loan", WalletType.LOAN, php(100_000_00), monthly,
            release = release(bank, php(95_000_00), php(5_000_00)),
        )
        val loan = (saved as WalletRepository.SaveResult.Saved).id
        val owed = wallet(loan)
        assertEquals("it opens at zero: the rows make up what is owed", php(0), owed.openingBalance)
        assertEquals(php(100_000_00), owed.balance)
        assertEquals(php(115_000_00), wallet(bank).balance)

        val rows = all()
        assertEquals(3, rows.size)
        val transfer = Transfer.pair(rows).getValue("rel-1")
        assertEquals(loan, transfer.fromWalletId)
        assertEquals(bank, transfer.toWalletId)
        assertEquals(php(95_000_00), transfer.amount)
        assertEquals(LoanRelease.PROCEEDS_NOTE, transfer.note)
        assertEquals(day, transfer.date)
        assertEquals(listOf("rel-1-in", "rel-1-out"), rows.filter { it.isTransfer }.map { it.clientUuid }.sortedBy { it })

        val charges = rows.single { it.type == TransactionType.EXPENSE }
        assertEquals(loan, charges.walletId)
        assertEquals(php(5_000_00), charges.amount)
        assertEquals(LoanRelease.CHARGES_NOTE, charges.note)
        assertEquals(LoanRelease.chargesKey("rel-1"), charges.clientUuid)
        val fees = categories.observeCategories().first().single { it.name == LoanRelease.FEES_CATEGORY }
        assertEquals(CategoryKind.EXPENSE, fees.kind)
        assertEquals(fees.id, charges.categoryId)
        assertEquals(1, feeLookups)

        // The charges are this month's spending (owner, 2026-11-27); the proceeds are never income.
        assertEquals(php(5_000_00), Insights.monthlyTotals(rows, "PHP", month, 1).single().amount)
        assertEquals(php(0), Insights.monthlyTotals(rows, "PHP", month, 1, TransactionType.INCOME).single().amount)

        // The whole loan is principal, so its progress starts at nothing paid rather than at zero borrowed.
        val progress = LoanMath.progress(owed, rows)!!
        assertEquals(php(100_000_00), progress.principal)
        assertEquals(php(0), progress.paid)
        assertEquals(php(100_000_00), progress.remaining)
    }

    @Test fun withoutChargesOnlyTheTransferIsWrittenAndNoCategoryIsMade() = runTest {
        val cash = account("Cash", WalletType.CASH, php(1_000_00))
        val loan = (wallets.create("Salary loan", WalletType.LOAN, php(30_000_00), release = release(cash, php(30_000_00), php(0)))
            as WalletRepository.SaveResult.Saved).id
        assertEquals(2, all().size)
        assertTrue(all().all { it.isTransfer })
        assertEquals(php(30_000_00), wallet(loan).balance)
        assertEquals(php(31_000_00), wallet(cash).balance)
        assertEquals(0, feeLookups)
        assertTrue(categories.observeCategories().first().none { it.name == LoanRelease.FEES_CATEGORY })
    }

    @Test fun aNewReceivablesMoneyComesOutOfTheAccount() = runTest {
        val bank = account("BPI", WalletType.BANK, php(50_000_00))
        val lent = (wallets.create("Loan to Ana", WalletType.RECEIVABLE, php(10_000_00), release = release(bank, php(10_000_00), php(0)))
            as WalletRepository.SaveResult.Saved).id
        val receivable = wallet(lent)
        assertEquals(php(0), receivable.openingBalance)
        assertEquals(php(10_000_00), receivable.balance)
        assertEquals(php(40_000_00), wallet(bank).balance)
        val transfer = Transfer.pair(all()).values.single()
        assertEquals(bank, transfer.fromWalletId)
        assertEquals(lent, transfer.toWalletId)
        assertEquals(LoanRelease.LENT_NOTE, transfer.note)
        assertEquals(php(10_000_00), LoanMath.principal(receivable, all()))
        assertEquals("lending is never spending", php(0), Insights.monthlyTotals(all(), "PHP", month, 1).single().amount)
    }

    @Test fun anAccountInAnotherCurrencyOrOneThatIsGoneWritesNothing() = runTest {
        val dollars = account("Dollar account", WalletType.BANK, usd(1_000_00))
        val before = wallets.observeWallets().first().size
        assertEquals(
            WalletRepository.SaveResult.ReleaseAccountMissing,
            wallets.create("Car loan", WalletType.LOAN, php(1_000_00), release = release(dollars, php(1_000_00), php(0))),
        )
        assertEquals(
            WalletRepository.SaveResult.ReleaseAccountMissing,
            wallets.create("Car loan", WalletType.LOAN, php(1_000_00), release = release(9_999, php(1_000_00), php(0))),
        )
        val house = account("House", WalletType.OTHER_ASSET, php(1_000_000_00))
        assertEquals(
            WalletRepository.SaveResult.ReleaseAccountMissing,
            wallets.create("Car loan", WalletType.LOAN, php(1_000_00), release = release(house, php(1_000_00), php(0))),
        )
        assertEquals(before + 1, wallets.observeWallets().first().size)
        assertTrue(all().isEmpty())
    }

    @Test fun anArchivedAccountOrAReceivableCanNotTakeTheMoney() = runTest {
        val old = account("Old bank", WalletType.BANK, php(5_000_00))
        walletDao.update(walletDao.getById(old)!!.copy(archived = true))
        val ben = account("Ben owes me", WalletType.RECEIVABLE, php(1_000_00))
        listOf(old, ben).forEachIndexed { i, id ->
            assertEquals(
                WalletRepository.SaveResult.ReleaseAccountMissing,
                wallets.create("Loan $i", WalletType.LOAN, php(1_000_00), release = release(id, php(1_000_00), php(0), key = "k$i")),
            )
        }
        assertTrue(all().isEmpty())
        assertTrue(wallets.observeWallets().first().none { it.type == WalletType.LOAN })
    }

    @Test fun aDayThatHasNotComeYetIsRefused() = runTest {
        val bank = account("BDO", WalletType.BANK, php(0))
        val tomorrow = WalletRepository.Release(bank, php(1_000_00), php(0), LocalDate.now().plusDays(1), "later")
        assertEquals(WalletRepository.SaveResult.ReleaseInvalid, wallets.create("Loan", WalletType.LOAN, php(1_000_00), release = tomorrow))
        assertTrue(all().isEmpty())
    }

    @Test fun theRowsSayWhoAddedThem() = runTest {
        val shared = WalletRepository(walletDao, transactionDao, clock = { 1_000L }, debtTerms = FakeDebtTermsDao(), actor = { "owner-1" })
        val bank = (shared.create("BDO", WalletType.BANK, php(0)) as WalletRepository.SaveResult.Saved).id
        shared.create("Loan", WalletType.LOAN, php(1_000_00), release = release(bank, php(900_00), php(100_00)))
        assertEquals(3, all().size)
        assertTrue(transactionDao.observeAll().first().all { it.createdBy == "owner-1" })
    }

    @Test fun amountsThatDoNotAddUpOrATypeWithoutOneWriteNothing() = runTest {
        val bank = account("BDO", WalletType.BANK, php(0))
        val refused = listOf(
            // Proceeds + charges must be what is owed.
            wallets.create("A", WalletType.LOAN, php(100_000_00), release = release(bank, php(95_000_00), php(4_000_00))),
            wallets.create("B", WalletType.LOAN, php(100_000_00), release = release(bank, php(0), php(100_000_00))),
            // A receivable has no charges; a card takes no release.
            wallets.create("C", WalletType.RECEIVABLE, php(10_000_00), release = release(bank, php(9_000_00), php(1_000_00))),
            wallets.create("D", WalletType.CREDIT_CARD, php(10_000_00), release = release(bank, php(10_000_00), php(0))),
        )
        refused.forEach { assertEquals(WalletRepository.SaveResult.ReleaseInvalid, it) }
        assertEquals(1, wallets.observeWallets().first().size)
        assertTrue(all().isEmpty())
        assertEquals(0, feeLookups)
    }

    @Test fun aForeignLoansRowsKeepTheirValuesInTheMainCurrency() = runTest {
        val dollars = account("Dollar account", WalletType.BANK, usd(0))
        val plan = LoanRelease.Plan.Ready(usd(950_00), usd(50_00))
        val fx = LoanRelease.values("PHP", WalletType.LOAN, java.math.BigDecimal("58.50"), wallet(dollars), plan, null)
        wallets.create(
            "Dollar loan", WalletType.LOAN, usd(1_000_00), fxBasisRate = java.math.BigDecimal("58.50"),
            release = release(dollars, plan.moved, plan.charges, fx = fx),
        )
        val rows = all()
        assertEquals(FxValue(php(55_575_00), FxSource.AVERAGE).base, rows.single { it.type == TransactionType.TRANSFER_OUT }.baseAmount)
        val arriving = rows.single { it.type == TransactionType.TRANSFER_IN }
        assertEquals(php(55_575_00), arriving.baseAmount)
        assertEquals(FxSource.TRANSFER, arriving.fxSource)
        assertEquals(php(2_925_00), rows.single { it.type == TransactionType.EXPENSE }.baseAmount)
    }
}
