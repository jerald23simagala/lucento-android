package com.lucento.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `addition and subtraction are exact in minor units`() {
        // 0.1 + 0.2 is the classic floating-point failure; minor units make it exact.
        val a = Money(10, "USD")
        val b = Money(20, "USD")
        assertEquals(Money(30, "USD"), a + b)
        assertEquals(Money(-10, "USD"), a - b)
        assertEquals(BigDecimal("0.30"), (a + b).toBigDecimal())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mixing currencies is rejected`() {
        Money(100, "USD") + Money(100, "EUR")
    }

    @Test(expected = ArithmeticException::class)
    fun `overflow is detected rather than wrapping`() {
        Money(Long.MAX_VALUE, "USD") + Money(1, "USD")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid currency code is rejected`() {
        Money(1, "usd")
    }

    @Test
    fun `fraction digits follow ISO 4217`() {
        assertEquals(2, CurrencyRules.fractionDigits("USD"))
        assertEquals(0, CurrencyRules.fractionDigits("JPY"))
        assertEquals(3, CurrencyRules.fractionDigits("BHD"))
        assertEquals(BigDecimal("1.234"), Money(1234, "BHD").toBigDecimal())
        assertEquals(BigDecimal("1234"), Money(1234, "JPY").toBigDecimal())
    }

    @Test
    fun `sum of empty list is zero`() {
        assertTrue(Money.sum("PHP", emptyList()).isZero)
    }

    @Test
    fun `sum adds all items`() {
        val total = Money.sum("EUR", listOf(Money(199, "EUR"), Money(1, "EUR"), Money(-50, "EUR")))
        assertEquals(Money(150, "EUR"), total)
    }

    @Test
    fun `negation and abs`() {
        assertEquals(Money(-5, "USD"), -Money(5, "USD"))
        assertEquals(Money(5, "USD"), Money(-5, "USD").abs())
    }
}
