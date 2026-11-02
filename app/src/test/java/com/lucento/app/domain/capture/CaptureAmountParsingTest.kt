package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** P0-3: the composer reads written amounts with the same rules as every amount field, and asks when unsure. */
class CaptureAmountParsingTest {

    private val today = LocalDate.parse("2026-11-01")
    private val categories = listOf(Category(11, "Cafes and coffee", CategoryKind.EXPENSE, "cafe"))
    private fun context(currency: String, separator: Char) = InterpretationContext(
        listOf(Wallet(1, "Cash", WalletType.CASH, Money(0, currency), 0)), categories, today, defaultWalletId = 1,
        decimalSeparator = separator,
    )
    private val enUS = context("USD", '.')
    private val deDE = context("EUR", ',')

    private fun one(text: String, ctx: InterpretationContext) = BatchEntryParser.parse(text, ctx).single()

    @Test
    fun `12,50 is twelve fifty in the composer too, whatever the phone`() {
        assertEquals(BigDecimal("12.5"), one("coffee 12,50", enUS).amount)
        assertEquals(BigDecimal("12.5"), one("coffee 12,50", deDE).amount)
        assertEquals(BigDecimal("12.5"), one("coffee 12.50", deDE).amount)
    }

    @Test
    fun `thousands still read as thousands where nothing else is possible`() {
        assertEquals(BigDecimal("1500"), one("coffee 1,500", enUS).amount)
        assertEquals(BigDecimal("1500"), one("kaffee 1.500", deDE).amount)
        assertEquals(BigDecimal("1250.5"), one("coffee 1,250.50", enUS).amount)
        assertEquals(BigDecimal("1250.5"), one("kaffee 1.250,50", deDE).amount)
        assertEquals(BigDecimal("1500"), one("coffee 1,5k", enUS).amount)
        assertEquals(BigDecimal("1500"), one("coffee 1.5k", enUS).amount)
    }

    @Test
    fun `a number with two readings becomes a one-tap choice, never a guess`() {
        val us = one("coffee 1.500", enUS)
        assertNull(us.amount)
        assertTrue(CandidateFlag.AMOUNT_AMBIGUOUS in us.flags)
        assertEquals(listOf(BigDecimal("1.5"), BigDecimal("1500")), us.amountOptions)
        val de = one("kaffee 3,500", deDE)
        assertNull(de.amount)
        assertEquals(listOf(BigDecimal("3.5"), BigDecimal("3500")), de.amountOptions)
    }

    @Test
    fun `too many decimals is not an amount - never read as thousands`() {
        val row = one("coffee 12.345", enUS)
        assertNull(row.amount)
        assertTrue(CandidateFlag.AMOUNT_MISSING in row.flags)
    }

    @Test
    fun `three-decimal accounts read three decimals`() {
        assertEquals(BigDecimal("1.234"), one("coffee 1.234", context("BHD", '.')).amount)
        val row = one("coffee 1,234", context("BHD", '.'))
        assertEquals(listOf(BigDecimal("1.234"), BigDecimal("1234")), row.amountOptions)
    }

    @Test
    fun `space and apostrophe grouping is never cut short`() {
        val fr = InterpretationContext(listOf(Wallet(1, "Cash", WalletType.CASH, Money(0, "EUR"), 0)), categories, today, 1, decimalSeparator = ',', spaceGrouping = true)
        assertEquals(BigDecimal("1000.5"), one("loyer 1 000,50", fr).amount)
        assertEquals(BigDecimal("1000.5"), one("loyer 1 000,50", fr).amount)
        assertEquals(BigDecimal("1000.5"), one("loyer 1 000,50", deDE).amount)
        assertEquals(BigDecimal("1000"), one("rent 1 000", fr).amount)
        assertEquals(BigDecimal("1000.5"), one("miete 1'000.50", context("CHF", '.')).amount)
        // A plain space on a phone that doesn't group with spaces: one number or two? Asked, never 0.50 or 1.
        val us = one("rent 1 000,50", enUS)
        assertNull(us.amount)
        assertTrue(CandidateFlag.AMOUNT_AMBIGUOUS in us.flags)
        assertTrue(us.amountOptions.any { it.compareTo(BigDecimal("1000.50")) == 0 })
    }

    @Test
    fun `two numbers joined by a space on a space-grouping phone are flagged for a check`() {
        val fr = InterpretationContext(listOf(Wallet(1, "Cash", WalletType.CASH, Money(0, "EUR"), 0)), categories, today, 1, decimalSeparator = ',', spaceGrouping = true)
        val row = one("déjeuner 250 180", fr)
        assertEquals(BigDecimal("250180"), row.amount)
        assertTrue(CandidateFlag.AMOUNT_UNVERIFIED in row.flags)
        assertTrue(CandidateFlag.AMOUNT_UNVERIFIED !in one("rent 1 000", fr).flags)
    }

    @Test
    fun `an unreadable number next to a readable one is offered, not taken`() {
        val row = one("salary 2.5L or 300", context("INR", '.'))
        assertNull(row.amount)
        assertTrue(CandidateFlag.AMOUNT_AMBIGUOUS in row.flags)
    }

    @Test
    fun `letters right after a number never cut its decimals`() {
        assertEquals(BigDecimal("3.5"), one("coffee 3.50eur", context("EUR", '.')).amount)
        assertEquals(BigDecimal("1500"), one("spent 1,500php on groceries", context("PHP", '.')).amount)
        assertEquals(BigDecimal("250"), one("lunch 250pesos", context("PHP", '.')).amount)
        // A scale Lucento doesn't read ("lakh", "bn") is not half an amount: it asks.
        val lakh = one("salary 2.5L", context("INR", '.'))
        assertNull(lakh.amount)
        assertTrue(CandidateFlag.AMOUNT_MISSING in lakh.flags || CandidateFlag.AMOUNT_AMBIGUOUS in lakh.flags)
        assertNull(one("bonus 1.2bn", enUS).amount)
    }

    @Test
    fun `an unreadable number is never replaced by another one`() {
        // Same clause: the readable number is offered, not taken.
        val same = one("lunch 1,5 with 2 sodas", context("JPY", ','))
        assertNull(same.amount)
        assertTrue(CandidateFlag.AMOUNT_AMBIGUOUS in same.flags)
        // Its own clause: kept as a row that asks, never dropped in favour of the next clause's number.
        val rows = BatchEntryParser.parse("lunch 1,5 and 2 sodas", context("JPY", ','))
        assertEquals(2, rows.size)
        assertNull(rows[0].amount)
        assertTrue(CandidateFlag.AMOUNT_MISSING in rows[0].flags)
    }

    @Test
    fun `Persian and Arabic digits are read in the composer`() {
        assertEquals(BigDecimal("12.5"), one("coffee ۱۲٫۵", enUS).amount)
        assertEquals(BigDecimal("12.5"), one("coffee ١٢٫٥٠", deDE).amount)
    }

    @Test
    fun `review text round-trips without becoming ambiguous`() {
        listOf(BigDecimal("12.500"), BigDecimal("1500"), BigDecimal("1.5E+3"), BigDecimal("0.10")).forEach { value ->
            listOf('.', ',').forEach { sep ->
                val text = ReviewRow.plain(value, sep)
                val parsed = com.lucento.app.core.money.MoneyParser.parse(text, "USD", sep)
                assertEquals("$value → \"$text\"", value.movePointRight(2).toBigIntegerExact().toLong(), (parsed as com.lucento.app.core.money.MoneyParser.Result.Success).money.amountMinor)
            }
        }
    }
}
