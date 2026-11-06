package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.TransactionRepository.TransferInput
import com.lucento.app.data.repository.TransactionRepository.TransferResult
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.fx.FxLedger
import com.lucento.app.domain.fx.FxQuotes
import com.lucento.app.domain.fx.FxSource
import com.lucento.app.domain.fx.FxTransfer
import com.lucento.app.domain.fx.FxValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Multicurrency (owner, 2026-11-05): values in the main currency are stored and kept, and transfers cross currencies. */
class MulticurrencyRepositoryTest {

    private val walletDao = FakeWalletDao()
    private val transactionDao = FakeTransactionDao()
    private val wallets = WalletRepository(walletDao, transactionDao, clock = { 1L })
    private val transactions = TransactionRepository(transactionDao, walletDao, FakeCategoryDao(), clock = { 2L })
    private val day = LocalDate.parse("2026-11-06")
    private val main = "PHP"

    private var pesos = 0L
    private var dollars = 0L
    private var euros = 0L

    @Before fun setUp() = runTest {
        pesos = (wallets.create("Bank", WalletType.BANK, Money(1_000_000, "PHP")) as WalletRepository.SaveResult.Saved).id
        dollars = (wallets.create("Dollars", WalletType.BANK, Money(10_000, "USD"), fxBasisRate = BigDecimal("55")) as WalletRepository.SaveResult.Saved).id
        euros = (wallets.create("Euros", WalletType.CASH, Money(0, "EUR"), fxBasisRate = BigDecimal("60")) as WalletRepository.SaveResult.Saved).id
    }

    private suspend fun all() = transactions.observeTransactions().first()
    private suspend fun walletList() = wallets.observeWallets().first()

    @Test fun `a wallet in another currency keeps the rate it was opened at`() = runTest {
        val usd = walletList().first { it.id == dollars }
        assertEquals(0, BigDecimal("55").compareTo(usd.fxBasisRate))
        wallets.setFxBasis(dollars, BigDecimal("56.5"))
        assertEquals(0, BigDecimal("56.5").compareTo(walletList().first { it.id == dollars }.fxBasisRate))
        // A (still unused) wallet moved to another currency drops a rate that belonged to the old one.
        wallets.update(euros, "Euros", WalletType.CASH, Money(0, "GBP"))
        assertNull(walletList().first { it.id == euros }.fxBasisRate)
    }

    @Test fun `a movement's value in the main currency is stored, and none for a main-currency wallet`() = runTest {
        transactions.save(TransactionRepository.Input(null, "x", TransactionType.EXPENSE, Money(1_000, "USD"), dollars, null, day, "",
            fx = FxValue(Money(58_000, main), FxSource.MARKET)))
        transactions.save(TransactionRepository.Input(null, "p", TransactionType.EXPENSE, Money(1_000, "PHP"), pesos, null, day, "",
            fx = FxValue(Money(1_000, main), FxSource.MARKET)))
        val saved = all().associateBy { it.clientUuid }
        assertEquals(Money(58_000, main), saved.getValue("x").baseAmount)
        assertEquals(FxSource.MARKET, saved.getValue("x").fxSource)
        assertNull(saved.getValue("p").baseAmount)
        assertNull(saved.getValue("p").fxSource)
    }

    @Test fun `an edit stores what it is given, so the editor passes the value it keeps`() = runTest {
        transactions.save(TransactionRepository.Input(null, "x", TransactionType.EXPENSE, Money(1_000, "USD"), dollars, null, day, "",
            fx = FxValue(Money(58_000, main), FxSource.ENTERED)))
        val id = all().single { it.clientUuid == "x" }.id
        transactions.save(TransactionRepository.Input(id, "x", TransactionType.EXPENSE, Money(2_000, "USD"), dollars, null, day, "",
            fx = FxValue(Money(116_000, main), FxSource.ENTERED)))
        assertEquals(Money(116_000, main), all().single { it.id == id }.baseAmount)
        transactions.save(TransactionRepository.Input(id, "x", TransactionType.EXPENSE, Money(2_000, "USD"), dollars, null, day, ""))
        assertNull("no value: it follows the wallet's average", all().single { it.id == id }.baseAmount)
    }

    @Test fun `pesos to dollars — two legs in their own currencies, the dollars carry the pesos paid`() = runTest {
        val (outFx, inFx) = FxTransfer.legValues(main, Money(560_000, "PHP"), Money(10_000, "USD"), value = null)
        val result = transactions.saveTransfer(
            TransferInput(null, "buy", pesos, dollars, Money(560_000, "PHP"), day, "", received = Money(10_000, "USD"), outFx = outFx, inFx = inFx),
        )
        assertTrue(result is TransferResult.Saved)
        val legs = all()
        val out = legs.single { it.type == TransactionType.TRANSFER_OUT }
        val inLeg = legs.single { it.type == TransactionType.TRANSFER_IN }
        assertEquals(Money(560_000, "PHP"), out.amount)
        assertNull(out.baseAmount)
        assertEquals(Money(10_000, "USD"), inLeg.amount)
        assertEquals(Money(560_000, main), inLeg.baseAmount)
        assertEquals(FxSource.TRANSFER, inLeg.fxSource)
        assertEquals(Money(20_000, "USD"), walletList().first { it.id == dollars }.balance)
        // And the dollar wallet's average moves to what was paid: ($100 at 55 + $100 at 56) / $200 = 55.5.
        val book = FxLedger.build(main, walletList(), legs)
        assertEquals(0, BigDecimal("55.5").compareTo(book.positions.getValue(dollars).averageRate))
    }

    @Test fun `between currencies the amount received is required, within one it must match`() = runTest {
        assertEquals(TransferResult.CurrencyMismatch, transactions.saveTransfer(TransferInput(null, "a", pesos, dollars, Money(1_000, "PHP"), day, "")))
        assertEquals(
            TransferResult.CurrencyMismatch,
            transactions.saveTransfer(TransferInput(null, "b", pesos, dollars, Money(1_000, "PHP"), day, "", received = Money(1_000, "EUR"))),
        )
        val other = (wallets.create("Cash", WalletType.CASH, Money(0, "PHP")) as WalletRepository.SaveResult.Saved).id
        assertEquals(
            TransferResult.CurrencyMismatch,
            transactions.saveTransfer(TransferInput(null, "c", pesos, other, Money(1_000, "PHP"), day, "", received = Money(999, "PHP"))),
        )
        assertTrue(all().isEmpty())
    }

    @Test fun `dollars to euros — both legs carry the same value, the euros take it in as paid`() = runTest {
        val (outFx, inFx) = FxTransfer.legValues(main, Money(5_000, "USD"), Money(4_600, "EUR"), FxValue(Money(290_000, main), FxSource.MARKET))
        assertEquals(FxValue(Money(290_000, main), FxSource.MARKET), outFx)
        assertEquals(FxValue(Money(290_000, main), FxSource.TRANSFER), inFx)
        transactions.saveTransfer(TransferInput(null, "fx", dollars, euros, Money(5_000, "USD"), day, "", received = Money(4_600, "EUR"), outFx = outFx, inFx = inFx))
        val book = FxLedger.build(main, walletList(), all())
        assertEquals(Money(290_000, main), book.positions.getValue(euros).carrying)
        // $50 sold at 58 that were carried at 55: a ₱150 gain.
        assertEquals(listOf(Money(15_000, main)), book.realized.map { it.amount })
    }

    @Test fun `the day's rate first, then the offline choice`() = runTest {
        transactions.save(TransactionRepository.Input(null, "old", TransactionType.EXPENSE, Money(1_000, "USD"), dollars, null, day.minusDays(3), "",
            fx = FxValue(Money(57_000, main), FxSource.MARKET)))
        val usd = walletList().first { it.id == dollars }
        val book = FxLedger.build(main, walletList(), all())
        val last = FxLedger.lastRate(main, usd, all())
        assertEquals(0, BigDecimal("57").compareTo(last))
        val amount = Money(2_000, "USD")

        val market = FxQuotes.quote(main, usd, amount, BigDecimal("58"), FxSource.LAST_RATE, book, last)!!
        assertEquals(FxSource.MARKET to Money(116_000, main), market.source to market.value)
        val offlineLast = FxQuotes.quote(main, usd, amount, null, FxSource.LAST_RATE, book, last)!!
        assertEquals(FxSource.LAST_RATE to Money(114_000, main), offlineLast.source to offlineLast.value)
        val offlineAverage = FxQuotes.quote(main, usd, amount, null, FxSource.AVERAGE, book, last)!!
        assertEquals(FxSource.AVERAGE, offlineAverage.source)
        assertEquals(Money(110_000, main), offlineAverage.value) // still at 55: the $10 spent left at the average

        // No last rate yet: "last rate" falls back to the average. A main-currency wallet needs no quote at all.
        assertEquals(FxSource.AVERAGE, FxQuotes.quote(main, usd, amount, null, FxSource.LAST_RATE, book, lastRate = null)!!.source)
        assertNull(FxQuotes.quote(main, walletList().first { it.id == pesos }, Money(100, "PHP"), null, FxSource.LAST_RATE, book, null))
    }
}
