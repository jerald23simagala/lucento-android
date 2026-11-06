package com.lucento.app.data.fx

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class OnlineFxRatesTest {

    private val today = LocalDate.of(2026, 11, 6)
    private val currencyApi = """{"date":"2026-10-30","usd":{"php":63.69377614,"aed":3.6725,"tiny":1.2e-05}}"""

    private fun rates(enabled: Boolean = true, online: Boolean = true, answers: Map<String, String?> = emptyMap(), asked: MutableList<String> = mutableListOf()) =
        OnlineFxRates(
            cache = InMemoryFxRateCache(),
            enabled = { enabled },
            isOnline = { online },
            fetch = { url -> asked += url; answers.entries.firstOrNull { url.contains(it.key) }?.value },
            today = { today },
        )

    @Test
    fun `rates are read as exact decimals, never doubles`() {
        assertEquals(BigDecimal("63.69377614"), OnlineFxRates.parseCurrencyApi(currencyApi, "USD", "PHP"))
        assertEquals(BigDecimal("0.000012"), OnlineFxRates.parseCurrencyApi(currencyApi, "USD", "TINY")!!.stripTrailingZeros())
        assertEquals(BigDecimal("63.68"), OnlineFxRates.parseFrankfurter("""{"amount":1.0,"base":"USD","rates":{"PHP":63.68}}""", "PHP"))
        assertNull(OnlineFxRates.parseCurrencyApi("""{"usd":{"php":"63"}}""", "USD", "PHP")) // a string isn't a rate
        assertNull(OnlineFxRates.parseCurrencyApi("not json", "USD", "PHP"))
        assertNull(OnlineFxRates.parseFrankfurter("""{"rates":{"PHP":0}}""", "PHP"))
    }

    @Test
    fun `a past day asks for that day, today asks for the latest, and the answer is cached`() = runTest {
        val asked = mutableListOf<String>()
        val source = rates(answers = mapOf("@2026-10-30/v1/currencies/usd" to currencyApi), asked = asked)
        assertEquals(BigDecimal("63.69377614"), source.rate("USD", "PHP", LocalDate.of(2026, 10, 30)))
        assertEquals(BigDecimal("63.69377614"), source.rate("USD", "PHP", LocalDate.of(2026, 10, 30)))
        assertEquals("cached: one request", 1, asked.size)

        val todayAsked = mutableListOf<String>()
        rates(answers = mapOf("@latest" to currencyApi), asked = todayAsked).rate("USD", "PHP", today.plusDays(3))
        assertTrue("a future date uses today's: ${todayAsked.first()}", todayAsked.first().contains("@latest"))
    }

    @Test
    fun `when the first service fails, the mirror and then the central bank are tried`() = runTest {
        val asked = mutableListOf<String>()
        val source = rates(answers = mapOf("frankfurter" to """{"rates":{"PHP":63.68}}"""), asked = asked)
        assertEquals(BigDecimal("63.68"), source.rate("USD", "PHP", LocalDate.of(2026, 10, 30)))
        assertEquals(3, asked.size)
        assertTrue(asked[1].contains("currency-api.pages.dev"))
        assertTrue(asked[2].contains("api.frankfurter.dev/v1/2026-10-30?base=USD&symbols=PHP"))
    }

    @Test
    fun `nothing is asked when it's turned off or the phone is offline`() = runTest {
        val asked = mutableListOf<String>()
        assertNull(rates(enabled = false, asked = asked).rate("USD", "PHP", today))
        assertNull(rates(online = false, asked = asked).rate("USD", "PHP", today))
        assertTrue(asked.isEmpty())
        assertEquals(BigDecimal.ONE, rates(enabled = false).rate("PHP", "PHP", today))
    }
}
