package com.lucento.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class FxRateTest {

    private fun usd(minor: Long) = Money(minor, "USD")

    @Test
    fun `converts into the target currency's minor units`() {
        assertEquals(Money(561_234, "PHP"), FxRate("USD", "PHP", BigDecimal("56.1234")).convert(usd(10_000)))
        // JPY has no minor units, BHD has three.
        assertEquals(Money(37_120, "PHP"), FxRate("JPY", "PHP", BigDecimal("0.3712")).convert(Money(1_000, "JPY")))
        assertEquals(Money(37_700, "BHD"), FxRate("USD", "BHD", BigDecimal("0.377")).convert(usd(10_000)))
    }

    @Test
    fun `rounds once, half-even`() {
        val rate = FxRate("USD", "PHP", BigDecimal("0.5"))
        assertEquals(Money(0, "PHP"), rate.convert(usd(1))) // 0.005 → 0.00
        assertEquals(Money(2, "PHP"), rate.convert(usd(3))) // 0.015 → 0.02
        assertEquals(Money(-2, "PHP"), rate.convert(usd(-3)))
    }

    @Test
    fun `two real amounts imply the rate actually paid`() {
        val implied = FxRate.implied(Money(560_000, "PHP"), usd(10_000))
        assertEquals("PHP", implied.from)
        assertEquals("USD", implied.to)
        // Inverting a rate already rounded to 12 digits lands within its last digit (56.0000000001); shown, it's 56.
        assertEquals("56", implied.inverse().display())
        assertEquals("0.017857", implied.display())
    }

    @Test
    fun `typed rates are parsed strictly`() {
        assertEquals(BigDecimal("56.12"), FxRate.parse(" 56.12 "))
        assertEquals(BigDecimal("1234.5"), FxRate.parse("1,234.5"))
        assertEquals(BigDecimal("0.0035"), FxRate.parse(".0035"))
        listOf("0", "-1", "abc", "1e5", "", null, "1.2.3").forEach { assertNull(it, FxRate.parse(it)) }
    }

    @Test
    fun `stored rates are canonical`() {
        assertEquals("56.1234", FxRate.encode(BigDecimal("56.12340000")))
        assertEquals("0.00350877192982", FxRate.encode(BigDecimal("0.003508771929824561")))
        assertEquals("56", FxRate.displayRate(BigDecimal("56.0000")))
    }

    @Test
    fun `a rate must be positive and overflow throws`() {
        assertThrows(IllegalArgumentException::class.java) { FxRate("USD", "PHP", BigDecimal.ZERO) }
        assertThrows(ArithmeticException::class.java) {
            FxRate("USD", "PHP", BigDecimal("999999999")).convert(usd(Long.MAX_VALUE / 10))
        }
    }
}
