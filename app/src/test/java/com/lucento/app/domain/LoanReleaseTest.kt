package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.domain.fx.FxLedger
import com.lucento.app.domain.fx.FxSource
import com.lucento.app.domain.fx.FxValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** A new loan's proceeds and charges, a new receivable's money lent, and what they started from (tester round, 2026-11-27). */
class LoanReleaseTest {

    private fun php(minor: Long) = Money(minor, "PHP")
    private fun usd(minor: Long) = Money(minor, "USD")

    private fun wallet(
        id: Long,
        type: WalletType,
        balance: Money,
        opening: Money = balance,
        spendable: Boolean? = null,
        rate: BigDecimal? = null,
    ) = Wallet(
        id = id, name = "W$id", type = type, balance = balance, createdAtEpochMillis = 0, openingBalance = opening,
        spendable = spendable, fxBasisRate = rate,
    )

    private var nextId = 1L
    private fun tx(
        type: TransactionType,
        amount: Money,
        wallet: Long,
        group: String? = null,
        key: String? = null,
        date: String = "2026-11-27",
    ) = Transaction(
        id = nextId++, type = type, amount = amount, walletId = wallet, categoryId = null, date = LocalDate.parse(date), note = "",
        createdAtEpochMillis = 0, transferGroup = group, clientUuid = key,
    )

    @Test fun aLoansChargesStayOnItAndTheRestReachesTheAccount() {
        assertEquals(
            LoanRelease.Plan.Ready(moved = php(95_000_00), charges = php(5_000_00)),
            LoanRelease.plan(WalletType.LOAN, php(100_000_00), php(5_000_00)),
        )
        // No charges: all of it arrives.
        assertEquals(
            LoanRelease.Plan.Ready(moved = php(100_000_00), charges = php(0)),
            LoanRelease.plan(WalletType.LOAN, php(100_000_00), null),
        )
        assertEquals(
            LoanRelease.Plan.Ready(moved = php(100_000_00), charges = php(0)),
            LoanRelease.plan(WalletType.LOAN, php(100_000_00), php(0)),
        )
    }

    @Test fun chargesThatTakeTheWholeLoanOrNothingOwedRecordNothing() {
        assertEquals(LoanRelease.Plan.ChargesTooHigh, LoanRelease.plan(WalletType.LOAN, php(5_000_00), php(5_000_00)))
        assertEquals(LoanRelease.Plan.ChargesTooHigh, LoanRelease.plan(WalletType.LOAN, php(5_000_00), php(6_000_00)))
        assertEquals(LoanRelease.Plan.ChargesTooHigh, LoanRelease.plan(WalletType.LOAN, php(5_000_00), php(-1)))
        assertEquals(LoanRelease.Plan.NothingOwed, LoanRelease.plan(WalletType.LOAN, php(0), null))
        assertEquals(LoanRelease.Plan.NothingOwed, LoanRelease.plan(WalletType.RECEIVABLE, php(-100), null))
    }

    @Test fun aReceivableHasNoChargesSoAllOfItLeavesTheAccount() {
        assertEquals(
            LoanRelease.Plan.Ready(moved = php(10_000_00), charges = php(0)),
            LoanRelease.plan(WalletType.RECEIVABLE, php(10_000_00), php(500_00)),
        )
        assertTrue(LoanRelease.appliesTo(WalletType.LOAN))
        assertTrue(LoanRelease.appliesTo(WalletType.RECEIVABLE))
        WalletType.entries.filterNot { it == WalletType.LOAN || it == WalletType.RECEIVABLE }.forEach {
            assertFalse("$it takes no release", LoanRelease.appliesTo(it))
        }
    }

    @Test fun theMoneyMovesThroughAnAccountInTheSameCurrencyOnly() {
        assertTrue(LoanRelease.canUse(wallet(1, WalletType.BANK, php(0)), "PHP"))
        // A loan can pay a card off directly; money lent can come from a card's cash advance.
        assertTrue(LoanRelease.canUse(wallet(2, WalletType.CREDIT_CARD, php(0)), "PHP"))
        assertFalse(LoanRelease.canUse(wallet(3, WalletType.BANK, usd(0)), "PHP"))
        assertFalse(LoanRelease.canUse(wallet(4, WalletType.OTHER_ASSET, php(0)), "PHP"))
        // A receivable never: a transfer out of it would read as an instalment received, one into it as more money lent.
        assertFalse(LoanRelease.canUse(wallet(5, WalletType.RECEIVABLE, php(0)), "PHP"))
    }

    @Test fun theSummaryShowsTheAccountsBalanceAfterTheMoneyMoves() {
        val bank = wallet(1, WalletType.BANK, php(20_000_00))
        val card = wallet(2, WalletType.CREDIT_CARD, php(30_000_00))
        val proceeds = LoanRelease.Plan.Ready(php(95_000_00), php(5_000_00))
        val lent = LoanRelease.Plan.Ready(php(10_000_00), php(0))
        assertEquals(php(115_000_00), LoanRelease.accountAfter(bank, WalletType.LOAN, proceeds))
        assertEquals(php(10_000_00), LoanRelease.accountAfter(bank, WalletType.RECEIVABLE, lent))
        // A card the proceeds pay off owes less (here more than all of it: a credit); a card lent from owes more.
        assertEquals(php(-65_000_00), LoanRelease.accountAfter(card, WalletType.LOAN, proceeds))
        assertEquals(php(40_000_00), LoanRelease.accountAfter(card, WalletType.RECEIVABLE, lent))
    }

    @Test fun anAccountAddedAfterTheDayProbablyHasTheMoneyInItsBalanceAlready() {
        val utc = java.time.ZoneOffset.UTC
        val addedNov20 = wallet(1, WalletType.BANK, php(0)).copy(
            createdAtEpochMillis = LocalDate.parse("2026-11-20").atTime(9, 0).toInstant(utc).toEpochMilli(),
        )
        assertTrue(LoanRelease.addedAfter(addedNov20, LocalDate.parse("2026-11-19"), utc))
        assertFalse(LoanRelease.addedAfter(addedNov20, LocalDate.parse("2026-11-20"), utc))
        assertFalse(LoanRelease.addedAfter(addedNov20, LocalDate.parse("2026-11-27"), utc))
    }

    @Test fun loanFeesNeverTakesCardInterestOrFeesFromScansAndMessages() {
        // Scans and typed messages pick a category by icon; "fees" would come before "debt" for card interest.
        val categories = listOf(
            Category(id = 1, name = DebtMath.INTEREST_CATEGORY, kind = CategoryKind.EXPENSE, iconKey = DebtMath.INTEREST_ICON),
            Category(id = 2, name = LoanRelease.FEES_CATEGORY, kind = CategoryKind.EXPENSE, iconKey = LoanRelease.FEES_ICON),
        )
        val resolve = { intent: String -> com.lucento.app.domain.capture.merchant.CategoryIntents.resolve(intent, CategoryKind.EXPENSE, categories) }
        assertEquals(1L, resolve("interest"))
        listOf("fees", "bank_fees", "tax").forEach { assertFalse("$it went to Loan fees", resolve(it) == 2L) }
    }

    @Test fun theSuggestedAccountIsSpendingMoneyWithTheMostInIt() {
        val cash = wallet(1, WalletType.CASH, php(2_000_00))
        val bank = wallet(2, WalletType.BANK, php(30_000_00))
        val savings = wallet(3, WalletType.SAVINGS, php(90_000_00))
        val card = wallet(4, WalletType.CREDIT_CARD, php(1_000_00))
        val dollars = wallet(5, WalletType.BANK, usd(999_999_00))
        assertEquals(bank, LoanRelease.suggestedAccount(listOf(cash, bank, savings, card, dollars), "PHP"))
        // Savings the person counts as spending money ranks with the rest of it.
        val counted = savings.copy(spendable = true)
        assertEquals(counted, LoanRelease.suggestedAccount(listOf(cash, bank, counted), "PHP"))
        // No spending money: another money account; never a card, loan, receivable or investment.
        assertEquals(savings, LoanRelease.suggestedAccount(listOf(savings, card), "PHP"))
        assertNull(LoanRelease.suggestedAccount(listOf(card, wallet(6, WalletType.INVESTMENT, php(5_000_00))), "PHP"))
        assertEquals(dollars, LoanRelease.suggestedAccount(listOf(cash, dollars), "USD"))
    }

    @Test fun chargesCountOnlyWhenTheyBelongToAReleaseOfTheSameLoan() {
        val loan = 7L
        val history = listOf(
            tx(TransactionType.TRANSFER_OUT, php(95_000_00), loan, group = "g1", key = "g1-out"),
            tx(TransactionType.TRANSFER_IN, php(95_000_00), 2, group = "g1", key = "g1-in"),
            tx(TransactionType.EXPENSE, php(5_000_00), loan, key = LoanRelease.chargesKey("g1")),
            // A payment's interest is not a release charge.
            tx(TransactionType.TRANSFER_IN, php(10_000_00), loan, group = "p1", key = "p1-in"),
            tx(TransactionType.EXPENSE, php(2_000_00), loan, key = DebtPaymentLink.interestKey("p1")),
            // A charge whose transfer is gone, and one moved to another wallet, no longer count.
            tx(TransactionType.EXPENSE, php(300_00), loan, key = LoanRelease.chargesKey("deleted")),
            tx(TransactionType.EXPENSE, php(400_00), 9, key = LoanRelease.chargesKey("g1")),
        )
        assertEquals(mapOf("g1" to php(5_000_00)), LoanRelease.chargesByRelease(history, loan))
    }

    @Test fun aLoanStartsFromItsOpeningBalancePlusWhatWasBorrowedIntoYourAccounts() {
        val history = listOf(
            tx(TransactionType.TRANSFER_OUT, php(95_000_00), 7, group = "g1", key = "g1-out"),
            tx(TransactionType.EXPENSE, php(5_000_00), 7, key = LoanRelease.chargesKey("g1")),
            // A late fee raises what is owed but isn't principal.
            tx(TransactionType.EXPENSE, php(500_00), 7, key = "fee"),
            // Another wallet's rows and another currency's are ignored.
            tx(TransactionType.TRANSFER_OUT, php(1_000_00), 8, group = "x", key = "x-out"),
            tx(TransactionType.TRANSFER_OUT, usd(1_000_00), 7, group = "y", key = "y-out"),
        )
        val loan = wallet(7, WalletType.LOAN, balance = php(100_500_00), opening = php(0))
        assertEquals(php(100_000_00), LoanMath.borrowed(loan, history))
        assertEquals(php(100_000_00), LoanMath.principal(loan, history))
        // A top-up later is borrowed too, on top of an opening balance.
        val topped = wallet(7, WalletType.LOAN, balance = php(150_000_00), opening = php(50_000_00))
        assertEquals(php(150_000_00), LoanMath.principal(topped, history))
        // Nothing borrowed since: the opening balance, as before.
        assertEquals(php(80_000_00), LoanMath.principal(wallet(7, WalletType.LOAN, php(60_000_00), opening = php(80_000_00)), emptyList()))
    }

    @Test fun aReceivableStartsFromWhatWasLentAndOtherWalletsFromTheirOpeningBalance() {
        val history = listOf(
            tx(TransactionType.TRANSFER_IN, php(10_000_00), 3, group = "l1", key = "l1-in"),
            // A collection (money coming back) is not lending.
            tx(TransactionType.TRANSFER_OUT, php(2_000_00), 3, group = "c1", key = "c1-out"),
        )
        val lent = wallet(3, WalletType.RECEIVABLE, balance = php(8_000_00), opening = php(0))
        assertEquals(php(10_000_00), LoanMath.principal(lent, history))
        val bank = wallet(3, WalletType.BANK, balance = php(8_000_00), opening = php(1_000_00))
        assertEquals(php(1_000_00), LoanMath.principal(bank, history))
        assertEquals(php(0), LoanMath.borrowed(bank, history))
    }

    @Test fun progressCountsTheReleaseAsPrincipalSoAFirstPaymentShowsAsPaid() {
        val terms = DebtTerms(repayment = DebtRepayment.MONTHLY, termMonths = 12, installment = php(9_000_00))
        val history = listOf(
            tx(TransactionType.TRANSFER_OUT, php(95_000_00), 7, group = "g1", key = "g1-out"),
            tx(TransactionType.EXPENSE, php(5_000_00), 7, key = LoanRelease.chargesKey("g1")),
            // One payment: 9,000 of which 1,000 interest, so 8,000 of principal.
            tx(TransactionType.EXPENSE, php(1_000_00), 7, key = DebtPaymentLink.interestKey("p1")),
            tx(TransactionType.TRANSFER_IN, php(9_000_00), 7, group = "p1", key = "p1-in"),
        )
        val loan = wallet(7, WalletType.LOAN, balance = php(92_000_00), opening = php(0)).copy(debt = terms)
        val progress = LoanMath.progress(loan, history)!!
        assertEquals(php(100_000_00), progress.principal)
        assertEquals(php(8_000_00), progress.paid)
        assertEquals(php(92_000_00), progress.remaining)
        assertEquals(progress.principal - progress.paid, progress.remaining)
        assertEquals(1, progress.installmentsPaid)
    }

    @Test fun inTheMainCurrencyNothingExtraIsStored() {
        val plan = LoanRelease.Plan.Ready(php(95_000_00), php(5_000_00))
        assertEquals(LoanRelease.Values(), LoanRelease.values("PHP", WalletType.LOAN, null, wallet(2, WalletType.BANK, php(0)), plan, null))
        assertEquals(LoanRelease.Values(), LoanRelease.values(null, WalletType.LOAN, null, wallet(2, WalletType.BANK, php(0)), plan, null))
    }

    @Test fun aForeignLoansRowsAreValuedAtItsOwnRateAndTheAccountTakesTheSameValue() {
        // A USD loan valued at 58.50 pesos to the dollar: 950.00 arrives, 50.00 of charges stay on the loan.
        val plan = LoanRelease.Plan.Ready(usd(950_00), usd(50_00))
        val values = LoanRelease.values("PHP", WalletType.LOAN, BigDecimal("58.50"), wallet(2, WalletType.BANK, usd(0)), plan, null)
        assertEquals(FxValue(php(55_575_00), FxSource.AVERAGE), values.outFx)
        assertEquals(FxValue(php(55_575_00), FxSource.TRANSFER), values.inFx)
        assertEquals(FxValue(php(2_925_00), FxSource.AVERAGE), values.chargesFx)
    }

    @Test fun aForeignReceivablesMoneyLeavesTheAccountAtItsAverage() {
        // A USD account that holds 1,000.00 at 56.00: lending 100.00 takes 5,600.00 of its value with it.
        val account = wallet(2, WalletType.BANK, balance = usd(1_000_00), opening = usd(1_000_00), rate = BigDecimal("56.00"))
        val book = FxLedger.build("PHP", listOf(account), emptyList())
        val plan = LoanRelease.Plan.Ready(usd(100_00), usd(0))
        val values = LoanRelease.values("PHP", WalletType.RECEIVABLE, BigDecimal("58.50"), account, plan, book)
        assertEquals(FxValue(php(5_600_00), FxSource.AVERAGE), values.outFx)
        assertEquals(FxValue(php(5_600_00), FxSource.TRANSFER), values.inFx)
        assertNull(values.chargesFx)
    }
}
