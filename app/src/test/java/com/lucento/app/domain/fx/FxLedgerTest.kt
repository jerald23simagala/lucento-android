package com.lucento.app.domain.fx

import com.lucento.app.core.money.Money
import com.lucento.app.domain.NetWorthComposer
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletBalanceCalculator
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The average method (owner, 2026-11-05): money in raises the average, money out leaves at it, and the difference from
 * its own rate is a realized FX gain (income) or loss (expense). Main currency PHP throughout.
 */
class FxLedgerTest {

    private val main = "PHP"
    private val day = LocalDate.of(2026, 11, 1)
    private var nextId = 1L

    private fun php(minor: Long) = Money(minor, "PHP")
    private fun usd(minor: Long) = Money(minor, "USD")

    private fun wallet(id: Long, type: WalletType = WalletType.BANK, opening: Money = usd(0), basis: String? = "55") =
        Wallet(id = id, name = "USD $id", type = type, balance = opening, createdAtEpochMillis = 0, openingBalance = opening,
            fxBasisRate = basis?.let(::BigDecimal))

    private fun move(
        walletId: Long,
        type: TransactionType,
        amount: Money,
        base: Money? = null,
        source: FxSource? = base?.let { FxSource.MARKET },
        daysLater: Long = 0,
        group: String? = null,
    ) = Transaction(
        id = nextId++, type = type, amount = amount, walletId = walletId, categoryId = null, date = day.plusDays(daysLater),
        note = "", createdAtEpochMillis = nextId, transferGroup = group, baseAmount = base, fxSource = source,
    )

    @Test
    fun `the opening balance is valued at the wallet's rate`() {
        val book = FxLedger.build(main, listOf(wallet(1, opening = usd(10_000))), emptyList())
        val position = book.positions.getValue(1)
        assertEquals(php(550_000), position.carrying)
        assertEquals(0, BigDecimal("55").compareTo(position.averageRate))
    }

    @Test
    fun `money out leaves at the average and the difference is realized`() {
        val spend = move(1, TransactionType.EXPENSE, usd(1_000), base = php(58_000)) // $10 at 58
        val book = FxLedger.build(main, listOf(wallet(1, opening = usd(10_000))), listOf(spend))
        assertEquals(php(495_000), book.positions.getValue(1).carrying) // $90 still at 55
        assertEquals(php(58_000), book.values.getValue(spend.id)) // the expense counts at its own rate
        assertEquals(listOf(php(3_000)), book.realized.map { it.amount }) // +₱30: the dollars had gained
    }

    @Test
    fun `money in at a new rate moves the average`() {
        val income = move(1, TransactionType.INCOME, usd(5_000), base = php(300_000)) // $50 at 60
        val position = FxLedger.build(main, listOf(wallet(1, opening = usd(10_000))), listOf(income)).positions.getValue(1)
        assertEquals(php(850_000), position.carrying)
        assertEquals("56.6666666667", position.averageRate!!.toPlainString())
    }

    @Test
    fun `the average rate never makes a gain or loss`() {
        val spend = move(1, TransactionType.EXPENSE, usd(2_500), base = php(1), source = FxSource.AVERAGE)
        val book = FxLedger.build(main, listOf(wallet(1, opening = usd(10_000))), listOf(spend))
        assertTrue(book.realized.isEmpty())
        assertEquals(php(137_500), book.values.getValue(spend.id)) // a quarter of ₱5,500, whatever was stored
    }

    @Test
    fun `buying dollars with pesos sets the rate actually paid`() {
        // ₱5,600 → $100: the in-leg carries the pesos paid.
        val buy = move(1, TransactionType.TRANSFER_IN, usd(10_000), base = php(560_000), source = FxSource.TRANSFER, group = "g")
        val position = FxLedger.build(main, listOf(wallet(1, basis = null)), listOf(buy)).positions.getValue(1)
        assertEquals(php(560_000), position.carrying)
        assertEquals(0, BigDecimal("56").compareTo(position.averageRate))
    }

    @Test
    fun `selling dollars realizes the change since they were bought`() {
        val buy = move(1, TransactionType.TRANSFER_IN, usd(10_000), base = php(560_000), source = FxSource.TRANSFER)
        val sell = move(1, TransactionType.TRANSFER_OUT, usd(4_000), base = php(232_000), source = FxSource.TRANSFER, daysLater = 30)
        val book = FxLedger.build(main, listOf(wallet(1, basis = null)), listOf(buy, sell))
        assertEquals(listOf(php(8_000)), book.realized.map { it.amount }) // $40 sold at 58, carried at 56
        assertEquals(php(336_000), book.positions.getValue(1).carrying)
    }

    @Test
    fun `a card paid off ends at zero, the rate difference realized`() {
        val charge = move(2, TransactionType.EXPENSE, usd(10_000), base = php(550_000))
        val payment = move(2, TransactionType.TRANSFER_IN, usd(10_000), base = php(560_000), source = FxSource.TRANSFER, daysLater = 20)
        val card = wallet(2, type = WalletType.CREDIT_CARD, basis = null)
        val book = FxLedger.build(main, listOf(card), listOf(charge, payment))
        assertEquals(php(0), book.positions.getValue(2).carrying)
        assertEquals(listOf(php(-10_000)), book.realized.map { it.amount }) // paid ₱100 more than it was carried at
    }

    @Test
    fun `history is replayed by date, so a back-dated movement changes what follows`() {
        val later = move(1, TransactionType.EXPENSE, usd(1_000), base = php(58_000), daysLater = 10)
        val earlier = move(1, TransactionType.INCOME, usd(10_000), base = php(600_000), daysLater = 1)
        val book = FxLedger.build(main, listOf(wallet(1, opening = usd(10_000))), listOf(later, earlier))
        // Average after the back-dated income: (5,500 + 6,000) / 200 = 57.5 → $10 costs ₱575, gain ₱5.
        assertEquals(listOf(php(500)), book.realized.map { it.amount })
    }

    @Test
    fun `a wallet with an opening balance and no rate is left unvalued`() {
        val book = FxLedger.build(main, listOf(wallet(1, opening = usd(10_000), basis = null)), emptyList())
        assertTrue(1L in book.unvalued)
        assertNull(book.positions[1])
    }

    @Test
    fun `movements without a rate of their own use the wallet's rate while it holds nothing`() {
        val legacy = move(1, TransactionType.INCOME, usd(1_000))
        val book = FxLedger.build(main, listOf(wallet(1)), listOf(legacy))
        assertEquals(php(55_000), book.positions.getValue(1).carrying)
        assertTrue(book.realized.isEmpty())
    }

    @Test
    fun `an overdraft brought back to zero settles what was left`() {
        val out = move(1, TransactionType.EXPENSE, usd(15_000), base = php(840_000)) // $150 at 56 from $100 held at 55
        val back = move(1, TransactionType.INCOME, usd(5_000), base = php(290_000), daysLater = 1) // $50 at 58
        val book = FxLedger.build(main, listOf(wallet(1, opening = usd(10_000))), listOf(out, back))
        assertEquals(php(0), book.positions.getValue(1).carrying)
        // $150 out at 56: $100 leaves at the average (₱5,500) and the $50 overdraft at its own 56 (₱2,800); the $100 sold
        // for ₱5,600 → gain ₱100. Refilling the $50 overdraft at 58 costs ₱2,900 against ₱2,800 carried → loss ₱100.
        assertEquals(listOf(php(10_000), php(-10_000)), book.realized.map { it.amount })
    }

    @Test
    fun `main-currency wallets are not touched`() {
        val peso = Wallet(9, "Cash", WalletType.CASH, php(100_000), 0)
        val book = FxLedger.build(main, listOf(peso), listOf(move(9, TransactionType.EXPENSE, php(5_000))))
        assertTrue(book.positions.isEmpty() && book.values.isEmpty() && book.unvalued.isEmpty())
    }

    @Test
    fun `translated wallets join the main-currency totals, and their history adds up`() {
        val opening = usd(10_000)
        val spend = move(1, TransactionType.EXPENSE, usd(1_000), base = php(58_000))
        val income = move(1, TransactionType.INCOME, usd(5_000), base = php(300_000), daysLater = 2)
        val dollars = wallet(1, opening = opening).copy(balance = usd(14_000))
        val peso = Wallet(9, "Cash", WalletType.CASH, php(100_000), 0)
        val ledger = FxTranslation.translate(main, listOf(dollars, peso), listOf(spend, income))

        val composition = NetWorthComposer.compose(ledger.wallets, main)
        assertEquals(php(100_000 + 795_000), composition.netWorth) // ₱1,000 cash + $140 carried at ₱7,950
        assertEquals(php(58_000), ledger.transactions.first { it.id == spend.id }.amount)

        val gain = ledger.fxResults.single()
        assertEquals(TransactionType.INCOME, gain.type)
        assertEquals(FxCategories.GAIN_ID, gain.categoryId)
        assertEquals(php(3_000), gain.amount)

        // Replaying the translated wallet with its movements and FX results lands exactly on the carrying value.
        val translated = ledger.wallets.first { it.id == 1L }
        val replayed = WalletBalanceCalculator.balanceOn(
            translated.openingBalance, translated.type, 1L, ledger.withFxResults, day.plusYears(1),
        )
        assertEquals(translated.balance, replayed)
    }
}
