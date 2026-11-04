package com.lucento.app.domain

import com.lucento.app.core.money.Money
import com.lucento.app.feature.home.HomeStateBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** Dashboard wallet card: month-end balances, the 3-month history and the month-scoped sections. */
class BalanceHistoryTest {

    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 2)
    private fun millis(date: LocalDate) = date.atStartOfDay(utc).toInstant().toEpochMilli()
    private fun usd(minor: Long) = Money(minor, "USD")

    private val bank = Wallet(1, "Bank", WalletType.BANK, usd(1_000_00), millis(LocalDate.of(2026, 8, 1)), openingBalance = usd(1_000_00))
    private val card = Wallet(2, "Card", WalletType.CREDIT_CARD, usd(0), millis(LocalDate.of(2026, 8, 1)), openingBalance = usd(0))
    private val lateWallet = Wallet(3, "New", WalletType.CASH, usd(50_00), millis(LocalDate.of(2026, 11, 1)), openingBalance = usd(50_00))
    private var id = 1L
    private fun tx(type: TransactionType, minor: Long, wallet: Long, date: LocalDate, group: String? = null) =
        Transaction(id++, type, usd(minor), wallet, null, date, "", 0, group)

    private val all = listOf(
        tx(TransactionType.INCOME, 500_00, 1, LocalDate.of(2026, 9, 5)),
        tx(TransactionType.EXPENSE, 200_00, 2, LocalDate.of(2026, 9, 20)),             // card purchase: owed 200
        tx(TransactionType.TRANSFER_OUT, 100_00, 1, LocalDate.of(2026, 10, 3), "g"),   // pay the card 100
        tx(TransactionType.TRANSFER_IN, 100_00, 2, LocalDate.of(2026, 10, 3), "g"),
        tx(TransactionType.EXPENSE, 50_00, 1, LocalDate.of(2026, 11, 1)),
    )

    @Test fun `month-end balances follow the same rules as today's, and wallets created later are left out`() {
        val sep = BalanceHistory.walletsAt(listOf(bank, card, lateWallet), all, LocalDate.of(2026, 9, 30), utc)
        assertEquals(listOf(1L, 2L), sep.map { it.id })
        assertEquals(1_500_00L, sep.first { it.id == 1L }.balance.amountMinor)
        assertEquals(200_00L, sep.first { it.id == 2L }.balance.amountMinor) // owed
        val oct = BalanceHistory.compositionAt(listOf(bank, card, lateWallet), all, "USD", YearMonth.of(2026, 10), today, utc)
        assertEquals(1_400_00L, oct.totalAssets.amountMinor)
        assertEquals(100_00L, oct.totalLiabilities.amountMinor)
        assertEquals(1_300_00L, oct.netWorth.amountMinor) // a transfer to the card changes nothing in net worth
    }

    @Test fun `the current month uses today's balances exactly`() {
        val now = listOf(bank.copy(balance = usd(1_350_00)), card.copy(balance = usd(100_00)))
        val nov = BalanceHistory.compositionAt(now, all, "USD", YearMonth.of(2026, 11), today, utc)
        assertEquals(1_250_00L, nov.netWorth.amountMinor)
    }

    @Test fun `no percentage when a wallet was added after the first month, since its opening is not growth`() {
        val now = listOf(bank.copy(balance = usd(1_350_00)), card.copy(balance = usd(100_00)), lateWallet)
        val history = BalanceHistory.lastMonths(now, all, "USD", YearMonth.of(2026, 11), today, count = 2, zone = utc)
        assertEquals(listOf(1_300_00L, 1_300_00L), history.map { it.netWorth.amountMinor }) // 1250 + the new 50
        assertNull(BalanceHistory.comparableTrendPercent(history, now, "USD", utc))
        val same = now.dropLast(1)
        val sameHistory = BalanceHistory.lastMonths(same, all, "USD", YearMonth.of(2026, 11), today, count = 2, zone = utc)
        assertEquals(-4, BalanceHistory.comparableTrendPercent(sameHistory, same, "USD", utc))
        // A wallet in another currency doesn't affect this currency's comparison.
        val euroCash = lateWallet.copy(balance = Money(50_00, "EUR"), openingBalance = Money(50_00, "EUR"))
        assertEquals(-4, BalanceHistory.comparableTrendPercent(sameHistory, same + euroCash, "USD", utc))
    }

    @Test fun `three months oldest first, and a trend only from a positive start`() {
        val now = listOf(bank.copy(balance = usd(1_350_00)), card.copy(balance = usd(100_00)))
        val history = BalanceHistory.lastMonths(now, all, "USD", YearMonth.of(2026, 11), today, zone = utc)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), history.map { it.month })
        assertEquals(listOf(1_300_00L, 1_300_00L, 1_250_00L), history.map { it.netWorth.amountMinor })
        assertEquals(-4, BalanceHistory.trendPercent(history)) // 1300 -> 1250
        assertNull(BalanceHistory.trendPercent(history.mapIndexed { i, m -> if (i == 0) m.copy(netWorth = usd(-10_00)) else m }))
        assertNull(BalanceHistory.trendPercent(history.take(1)))
    }

    @Test fun `dashboard sections follow the selected month`() {
        val schedules = listOf(
            RecurringSchedule(1, "Rent", TransactionType.EXPENSE, usd(500_00), 1, null, Frequency.MONTHLY, 1, LocalDate.of(2026, 11, 15), null, 0, false),
        )
        val wallets = listOf(bank.copy(balance = usd(1_350_00)), card.copy(balance = usd(100_00)))
        val nov = HomeStateBuilder.build(all, wallets, emptyList(), "USD", today, schedules = schedules)
        assertEquals(listOf(LocalDate.of(2026, 11, 15)), nov.scheduledThisMonth.map { it.dueDate })
        assertEquals(1, nov.recentInMonth.size) // only November's entry
        val oct = HomeStateBuilder.build(all, wallets, emptyList(), "USD", today, schedules = schedules, month = YearMonth.of(2026, 10))
        assertTrue("past months list nothing as upcoming", oct.scheduledThisMonth.isEmpty())
        assertEquals(setOf(LocalDate.of(2026, 10, 3)), oct.recentInMonth.map { it.date }.toSet())
        assertEquals("October shows its end-of-month balance", 1_300_00L, oct.monthComposition!!.netWorth.amountMinor)
        assertEquals(YearMonth.of(2026, 10), oct.balanceHistory.last().month)
    }
}
