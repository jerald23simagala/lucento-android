package com.lucento.app.domain

import com.lucento.app.core.LucentoLinks
import com.lucento.app.core.money.Money
import com.lucento.app.feature.account.AccountDeletionCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Tester round 3's pure rules: card setup split, withdrawals, spending slices, period range, delete guard, links. */
class TesterRound3RulesTest {
    private fun php(minor: Long) = Money(minor, "PHP")

    @Test fun cardSetupSplitsTheBalanceIntoDueAndNotYetDue() {
        assertEquals(CardSetup.Split.AllDue, CardSetup.split(php(30_000), null))
        assertEquals(CardSetup.Split.AllDue, CardSetup.split(php(30_000), php(30_000)))
        assertEquals(CardSetup.Split.PartlyDue(php(12_000), php(18_000)), CardSetup.split(php(30_000), php(12_000)))
        assertEquals(CardSetup.Split.PartlyDue(php(0), php(30_000)), CardSetup.split(php(30_000), php(0)))
        assertEquals(CardSetup.Split.TooHigh, CardSetup.split(php(30_000), php(30_001)))
        assertEquals(CardSetup.Split.TooHigh, CardSetup.split(php(30_000), php(-1)))
    }

    @Test fun outsideMovementsAreAllowedWhereTheyMeanSomethingAndReadByTheirNote() {
        assertEquals(
            setOf(WalletType.CASH, WalletType.BANK, WalletType.E_WALLET, WalletType.SAVINGS),
            WalletType.entries.filter(OutsideMovement.WITHDRAWAL::allowedFor).toSet(),
        )
        assertEquals(
            setOf(WalletType.CREDIT_CARD, WalletType.LOAN),
            WalletType.entries.filter(OutsideMovement.EXTERNAL_PAYMENT::allowedFor).toSet(),
        )
        assertFalse("what it was for is required", OutsideMovement.reasonValid("   "))
        assertEquals("Withdrawal · Rent deposit", OutsideMovement.WITHDRAWAL.note(" Rent deposit "))
        assertEquals("External payment · Paid by Mom", OutsideMovement.EXTERNAL_PAYMENT.note("Paid by Mom"))
        assertEquals(OutsideMovement.MAX_REASON + "Withdrawal · ".length, OutsideMovement.WITHDRAWAL.note("x".repeat(500)).length)
        fun row(type: TransactionType, note: String) = Transaction(1, type, php(100), 1, null, LocalDate.parse("2026-11-10"), note, 0L)
        assertEquals(OutsideMovement.WITHDRAWAL, OutsideMovement.of(row(TransactionType.ADJUSTMENT_OUT, "Withdrawal")))
        assertEquals(OutsideMovement.WITHDRAWAL, OutsideMovement.of(row(TransactionType.ADJUSTMENT_OUT, "Withdrawal · Gift")))
        assertEquals("Gift", OutsideMovement.reasonOf(row(TransactionType.ADJUSTMENT_OUT, "Withdrawal · Gift")))
        assertEquals(OutsideMovement.EXTERNAL_PAYMENT, OutsideMovement.of(row(TransactionType.ADJUSTMENT_IN, "External payment · Mom")))
        assertEquals("a correction is neither", null, OutsideMovement.of(row(TransactionType.ADJUSTMENT_OUT, "")))
        assertEquals("the type must match", null, OutsideMovement.of(row(TransactionType.ADJUSTMENT_IN, "Withdrawal · Gift")))
        assertEquals(null, OutsideMovement.of(row(TransactionType.EXPENSE, "Withdrawal")))
        assertEquals(null, OutsideMovement.of(row(TransactionType.ADJUSTMENT_OUT, "Withdrawals are fun")))
    }

    @Test fun spendingSlicesKeepTheLargestAndGroupTheRestAsOther() {
        val shares = (1L..8L).map { CategoryShare(it, php(1_000 - it * 100)) } + CategoryShare(99, php(0))
        val slices = Insights.spendingSlices(shares, maxSlices = 6)
        assertEquals(6, slices.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), slices.take(5).map { it.categoryId })
        val other = slices.last()
        assertTrue(other.isOther)
        // Categories 6, 7 and 8 hold 400, 300 and 200.
        assertEquals(php(400 + 300 + 200), other.amount)
        assertEquals("slices add up to the total", Money.sum("PHP", shares.map { it.amount }), Money.sum("PHP", slices.map { it.amount }))
        // Few categories: no Other; zero amounts dropped.
        assertEquals(2, Insights.spendingSlices(listOf(CategoryShare(1, php(5)), CategoryShare(null, php(2)), CategoryShare(3, php(0)))).size)
    }

    @Test fun thePeriodRangeStartsOnTheFirstMonthAndEndsToday() {
        val today = LocalDate.parse("2026-11-10")
        val range = Insights.periodRange(YearMonth.from(today), 3, today)
        assertEquals(LocalDate.parse("2026-09-01"), range.start)
        assertEquals(today, range.endInclusive)
        assertEquals(LocalDate.parse("2026-11-30"), Insights.periodRange(YearMonth.of(2026, 11), 1, LocalDate.parse("2026-12-05")).endInclusive)
    }

    @Test fun deletingNeedsTheAccountsOwnEmail() {
        assertTrue(AccountDeletionCopy.emailMatches(" Jerald@Example.com ", "jerald@example.com"))
        assertFalse(AccountDeletionCopy.emailMatches("", "jerald@example.com"))
        assertFalse(AccountDeletionCopy.emailMatches("jerald@example.co", "jerald@example.com"))
        assertFalse("no account email, nothing matches", AccountDeletionCopy.emailMatches("x@y.z", null))
    }

    @Test fun sharingPointsAtTheWebsiteUntilThePlayListingIsPublic() {
        assertEquals(if (LucentoLinks.PLAY_LISTING_PUBLIC) LucentoLinks.PLAY_STORE else LucentoLinks.WEBSITE, LucentoLinks.shareLink)
        assertTrue(LucentoLinks.shareMessage().endsWith(LucentoLinks.shareLink))
        assertTrue(LucentoLinks.PLAY_STORE.endsWith("id=com.lucento.app"))
        listOf(LucentoLinks.WEBSITE, LucentoLinks.COMMUNITY, LucentoLinks.PLAY_STORE).forEach { assertTrue(it.startsWith("https://")) }
    }
}
