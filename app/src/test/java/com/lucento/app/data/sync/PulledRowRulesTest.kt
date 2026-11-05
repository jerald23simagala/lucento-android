package com.lucento.app.data.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P2-9 / N-06: pulled rows that break the record rules are skipped, never reinterpreted. */
class PulledRowRulesTest {

    private fun tx(type: String = "EXPENSE", amount: Long = 1250, currency: String = "USD", deleted: Boolean = false) = buildJsonObject {
        put("type", type); put("amount_minor", amount); put("currency_code", currency)
        if (deleted) put("deleted_at", "2026-11-01T00:00:00Z")
    }

    @Test
    fun `valid rows pass`() {
        assertNull(PulledRowRules.problem("transactions", tx()))
        assertNull(PulledRowRules.problem("transactions", tx(type = "ADJUSTMENT_OUT", amount = 0)))
        assertNull(PulledRowRules.problem("wallets", buildJsonObject { put("type", "LOAN"); put("currency_code", "BHD"); put("opening_balance_minor", -500) }))
        // Owner round 10: receivables sync like any other wallet.
        assertNull(PulledRowRules.problem("wallets", buildJsonObject { put("type", "RECEIVABLE"); put("currency_code", "PHP"); put("opening_balance_minor", 6_000_000) }))
        assertNull(PulledRowRules.problem("categories", buildJsonObject { put("kind", "INCOME") }))
        assertNull(PulledRowRules.problem("recurring", buildJsonObject {
            put("type", "EXPENSE"); put("frequency", "MONTHLY"); put("interval_count", 1); put("amount_minor", 90000); put("currency_code", "PHP")
        }))
    }

    @Test
    fun `unknown types, bad currencies and impossible amounts are refused`() {
        assertEquals("type", PulledRowRules.problem("transactions", tx(type = "REFUND")))
        assertEquals("amount_minor", PulledRowRules.problem("transactions", tx(amount = -5)))
        assertEquals("amount_minor", PulledRowRules.problem("transactions", tx(amount = 1_000_000_000_000_001)))
        assertEquals("currency_code", PulledRowRules.problem("transactions", tx(currency = "usd")))
        assertEquals("type", PulledRowRules.problem("wallets", buildJsonObject { put("type", "CRYPTO"); put("currency_code", "USD"); put("opening_balance_minor", 0) }))
        assertEquals("kind", PulledRowRules.problem("categories", buildJsonObject { put("kind", "TRANSFER") }))
        assertEquals("interval_count", PulledRowRules.problem("recurring", buildJsonObject {
            put("type", "EXPENSE"); put("frequency", "MONTHLY"); put("interval_count", 0); put("amount_minor", 1); put("currency_code", "USD")
        }))
        assertEquals("limit_minor", PulledRowRules.problem("budgets", JsonObject(mapOf("currency_code" to JsonPrimitive("USD")))))
    }

    @Test
    fun `a soft-deleted row is never blocked - the delete always applies`() {
        assertNull(PulledRowRules.problem("transactions", tx(type = "REFUND", deleted = true)))
    }

    @Test
    fun `an unreadable institution id never rejects the wallet, and a budget row is never both scopes`() {
        assertNull(PulledRowRules.problem("wallets", buildJsonObject {
            put("type", "BANK"); put("currency_code", "USD"); put("opening_balance_minor", 0); put("institution_id", "Not-An-Id")
        }))
        fun budget(month: String?, from: String?) = buildJsonObject {
            put("currency_code", "USD"); put("limit_minor", 100)
            month?.let { put("month", it) }; from?.let { put("effective_from", it) }
        }
        assertNull(PulledRowRules.problem("budgets", budget("2026-11", null)))
        assertNull(PulledRowRules.problem("budgets", budget(null, "2026-11")))
        assertEquals("effective_from", PulledRowRules.problem("budgets", budget("2026-11", "2026-11")))
        assertEquals("month", PulledRowRules.problem("budgets", budget("2026-13", null)))
    }
}
