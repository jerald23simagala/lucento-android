package com.lucento.app.feature.wallets

import com.lucento.app.core.money.Money
import com.lucento.app.domain.LoanRelease
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The wallet editor's "Money received" / "Money lent" fields as saving reads them (tester round, 2026-11-27). */
class ReleaseDraftTest {

    private fun php(minor: Long) = Money(minor, "PHP")
    private val today = LocalDate.parse("2026-11-27")
    private val bank = Wallet(id = 2, name = "BDO Savings", type = WalletType.BANK, balance = php(20_000_00), createdAtEpochMillis = 0)
    private val dollars = Wallet(id = 3, name = "Dollars", type = WalletType.BANK, balance = Money(0, "USD"), createdAtEpochMillis = 0)

    private val loan = WalletDraft(
        name = "Car loan", type = WalletType.LOAN, currencyCode = "PHP", amountText = "100000",
        recordRelease = true, releaseAccountId = bank.id, chargesText = "5000",
    )
    private val lent = WalletDraft(
        name = "Loan to Ana", type = WalletType.RECEIVABLE, currencyCode = "PHP", amountText = "10000",
        recordRelease = true, releaseAccountId = bank.id,
    )

    private fun read(draft: WalletDraft, owed: Money = php(100_000_00), wallets: List<Wallet> = listOf(bank, dollars)) =
        ReleaseDraft.of(draft, owed, wallets, '.', today)

    private fun problem(draft: WalletDraft, owed: Money = php(100_000_00)) = (read(draft, owed) as ReleaseDraft.Problem).error

    @Test fun nothingIsRecordedUnlessANewLoanOrReceivableHasTheSwitchOn() {
        assertNull(read(loan.copy(recordRelease = false)))
        assertNull(read(loan.copy(id = 7)))
        assertNull(read(loan.copy(type = WalletType.CREDIT_CARD)))
        assertNull(read(loan.copy(type = WalletType.BANK)))
        // No account in its currency at all: the editor shows the switch off, so nothing is recorded (QA, 2026-11-27).
        assertNull(read(loan.copy(releaseAccountId = dollars.id), wallets = listOf(dollars)))
    }

    @Test fun aReceivableIsNeverTheOtherSide() {
        val owedByBen = Wallet(id = 4, name = "Ben owes me", type = WalletType.RECEIVABLE, balance = php(1_000_00), createdAtEpochMillis = 0)
        assertEquals(
            DraftError.ReleaseAccountUnavailable,
            (read(lent.copy(releaseAccountId = owedByBen.id), owed = php(10_000_00), wallets = listOf(bank, owedByBen)) as ReleaseDraft.Problem).error,
        )
    }

    @Test fun aLoanReadsIntoItsAccountItsSplitAndTheDay() {
        assertEquals(
            ReleaseDraft.Ready(bank, LoanRelease.Plan.Ready(php(95_000_00), php(5_000_00)), today),
            read(loan),
        )
        val earlier = today.minusDays(3)
        assertEquals(earlier, (read(loan.copy(releaseDate = earlier)) as ReleaseDraft.Ready).date)
        // Empty charges: none.
        assertEquals(LoanRelease.Plan.Ready(php(100_000_00), php(0)), (read(loan.copy(chargesText = "")) as ReleaseDraft.Ready).plan)
    }

    @Test fun problemsComeInTheOrderTheFormShowsThem() {
        assertEquals(DraftError.ReleaseNeedsAmount, problem(loan.copy(releaseAccountId = null), owed = php(0)))
        assertEquals(DraftError.ReleaseAccountRequired, problem(loan.copy(releaseAccountId = null, chargesText = "junk")))
        assertEquals(DraftError.ReleaseAccountUnavailable, problem(loan.copy(releaseAccountId = dollars.id)))
        assertEquals(DraftError.ReleaseAccountUnavailable, problem(loan.copy(releaseAccountId = 999)))
        assertEquals(DraftError.ChargesInvalid, problem(loan.copy(chargesText = "junk")))
        assertEquals(DraftError.ChargesInvalid, problem(loan.copy(chargesText = "-5")))
        assertEquals(DraftError.ChargesTooHigh, problem(loan.copy(chargesText = "100000")))
        assertEquals(DraftError.ReleaseDateInFuture, problem(loan.copy(releaseDate = today.plusDays(1))))
    }

    @Test fun aReceivableIgnoresTheChargesField() {
        val ready = read(lent.copy(chargesText = "junk"), owed = php(10_000_00)) as ReleaseDraft.Ready
        assertEquals(LoanRelease.Plan.Ready(php(10_000_00), php(0)), ready.plan)
        assertEquals(bank, ready.account)
    }

    @Test fun thePreviewAndTheSavedLineFollowTheFields() {
        assertEquals(LoanRelease.Plan.Ready(php(95_000_00), php(5_000_00)), releasePreview(loan, '.'))
        assertNull(releasePreview(loan.copy(amountText = ""), '.'))
        assertNull(releasePreview(loan.copy(chargesText = "junk"), '.'))
        assertNull(releasePreview(loan.copy(chargesText = "100000"), '.'))
        assertEquals("Car loan added. Its money went into BDO Savings.", releaseSavedMessage(loan, listOf(bank)))
        assertEquals("Loan to Ana added. The money came out of BDO Savings.", releaseSavedMessage(lent, listOf(bank)))
        assertNull(releaseSavedMessage(loan.copy(recordRelease = false), listOf(bank)))
        assertNull(releaseSavedMessage(loan.copy(id = 7), listOf(bank)))
    }
}
