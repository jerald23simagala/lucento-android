package com.lucento.app.feature.wallets

import com.lucento.app.core.money.Money
import com.lucento.app.data.preferences.WalletsViewMode
import com.lucento.app.domain.NetWorthCalculator
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Core screens refinement: the Wallets hero's trend, the asset/liability split, the filters and the view preference. */
class WalletsOverviewTest {

    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2)
    private fun millis(date: LocalDate) = date.atStartOfDay(utc).toInstant().toEpochMilli()
    private fun usd(minor: Long) = Money(minor, "USD")

    // Same ledger as BalanceHistoryTest: today's bank 1,350 and card owing 100.
    private val bank = Wallet(1, "Bank", WalletType.BANK, usd(1_350_00), millis(LocalDate.of(2026, 8, 1)), openingBalance = usd(1_000_00))
    private val card = Wallet(2, "Card", WalletType.CREDIT_CARD, usd(100_00), millis(LocalDate.of(2026, 8, 1)), openingBalance = usd(0))
    private val lateCash = Wallet(3, "New", WalletType.CASH, usd(50_00), millis(LocalDate.of(2026, 11, 1)), openingBalance = usd(50_00))
    private var id = 1L
    private fun tx(type: TransactionType, minor: Long, wallet: Long, date: LocalDate, group: String? = null) =
        Transaction(id++, type, usd(minor), wallet, null, date, "", 0, group)

    private val all = listOf(
        tx(TransactionType.INCOME, 500_00, 1, LocalDate.of(2026, 9, 5)),
        tx(TransactionType.EXPENSE, 200_00, 2, LocalDate.of(2026, 9, 20)),
        tx(TransactionType.TRANSFER_OUT, 100_00, 1, LocalDate.of(2026, 10, 3), "g"),
        tx(TransactionType.TRANSFER_IN, 100_00, 2, LocalDate.of(2026, 10, 3), "g"),
        tx(TransactionType.EXPENSE, 50_00, 1, LocalDate.of(2026, 11, 1)),
    )

    @Test fun trendCoversUpToSixMonthsAndEndsAtTodaysNetWorth() {
        val wallets = listOf(bank, card)
        val trend = WalletsOverview.trend(wallets, all, "USD", today, zone = utc)
        // June and July come before any wallet existed, so they are left out rather than drawn as zero.
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), trend.history.map { it.month })
        assertEquals(listOf(1_000_00L, 1_300_00L, 1_300_00L, 1_250_00L), trend.history.map { it.netWorth.amountMinor })
        assertEquals(NetWorthCalculator.summarize(wallets, "USD").totalFor("USD"), trend.history.last().netWorth)
    }

    @Test fun changePillIsMonthOnMonthNotAcrossTheWholeTrend() {
        val trend = WalletsOverview.trend(listOf(bank, card), all, "USD", today, zone = utc)
        assertEquals(-4, trend.monthOnMonth) // October 1,300 → November 1,250; August → November would be +25
    }

    @Test fun noChangeWhenANewWalletWouldCountAsGrowth() {
        val trend = WalletsOverview.trend(listOf(bank, card, lateCash), all, "USD", today, zone = utc)
        assertNull(trend.monthOnMonth)
    }

    @Test fun noChangeWithASingleMonth() {
        val fresh = Wallet(9, "Fresh", WalletType.CASH, usd(10_00), millis(today), openingBalance = usd(10_00))
        val trend = WalletsOverview.trend(listOf(fresh), emptyList(), "USD", today, zone = utc)
        assertEquals(1, trend.history.size)
        assertNull(trend.monthOnMonth)
    }

    @Test fun assetShareAddsUpToAHundredAndSkipsEmptySides() {
        assertEquals(69, WalletsOverview.assetShare(usd(8_177_50), usd(3_750_00))) // 68.56 % → 69, liabilities 31
        assertNull(WalletsOverview.assetShare(usd(8_177_50), usd(0)))
        assertNull(WalletsOverview.assetShare(usd(0), usd(3_750_00)))
        assertNull(WalletsOverview.assetShare(usd(500_00), usd(-20_00))) // an overpaid card
    }

    @Test fun filtersBySideAndType() {
        val savings = Wallet(4, "Savings", WalletType.SAVINGS, usd(5_200_00), 0L)
        val loan = Wallet(5, "Loan", WalletType.LOAN, usd(3_450_00), 0L)
        val wallets = listOf(bank, card, lateCash, savings, loan)
        assertEquals(listOf(1L, 3L, 4L), WalletsOverview.filter(wallets, WalletSide.ASSETS, null).map { it.id })
        assertEquals(listOf(2L, 5L), WalletsOverview.filter(wallets, WalletSide.LIABILITIES, null).map { it.id })
        assertEquals(listOf(5L), WalletsOverview.filter(wallets, WalletSide.ALL, WalletType.LOAN).map { it.id })
        assertEquals(emptyList<Long>(), WalletsOverview.filter(wallets, WalletSide.ASSETS, WalletType.LOAN).map { it.id })
    }

    @Test fun typeChipsOfferOnlyTypesTheUserHasOnThatSide() {
        val wallets = listOf(card, bank, lateCash, bank.copy(id = 7))
        assertEquals(listOf(WalletType.CASH, WalletType.BANK, WalletType.CREDIT_CARD), WalletsOverview.typesOn(wallets, WalletSide.ALL))
        assertEquals(listOf(WalletType.CASH, WalletType.BANK), WalletsOverview.typesOn(wallets, WalletSide.ASSETS))
        assertEquals(listOf(WalletType.CREDIT_CARD), WalletsOverview.typesOn(wallets, WalletSide.LIABILITIES))
    }

    @Test fun viewModeReadsItsStoredKeyAndFallsBackToRows() {
        assertEquals(WalletsViewMode.CARDS, WalletsViewMode.fromKey("cards"))
        assertEquals(WalletsViewMode.ROWS, WalletsViewMode.fromKey("rows"))
        // Cards (the gallery) are the default when nothing was chosen.
        assertEquals(WalletsViewMode.CARDS, WalletsViewMode.fromKey(null))
        assertEquals(WalletsViewMode.CARDS, WalletsViewMode.fromKey("grid"))
        WalletsViewMode.entries.forEach { assertEquals(it, WalletsViewMode.fromKey(it.key)) }
    }
}
