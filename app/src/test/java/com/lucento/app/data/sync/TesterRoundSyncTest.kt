package com.lucento.app.data.sync

import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.repository.FakeBudgetDao
import com.lucento.app.data.repository.FakeCategoryDao
import com.lucento.app.data.repository.FakeDebtTermsDao
import com.lucento.app.data.repository.FakeGoalDao
import com.lucento.app.data.repository.FakeRecurringDao
import com.lucento.app.data.repository.FakeSyncDao
import com.lucento.app.data.repository.FakeTransactionDao
import com.lucento.app.data.repository.FakeWalletDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Tester round (2026-11-17) over sync (supabase/migrations/20261119090000_wallet_kinds_and_spending_money.sql): a phone
 * that never sets a kind, icon or spending choice sends exactly what v13 sent; one that does sends the keys on every
 * wallet of the batch; another phone gets them; scheduled transfers travel; and pulled rows are checked like the server.
 */
class TesterRoundSyncTest {

    private val owner = "11111111-1111-4111-8111-111111111111"
    private val server = FakeSyncServer()

    private inner class Device {
        val wallets = FakeWalletDao()
        val categories = FakeCategoryDao()
        val transactions = FakeTransactionDao()
        val recurring = FakeRecurringDao()
        val now = 1_000_000_000_000L

        init {
            wallets.transactions = transactions
            categories.transactions = transactions
        }

        suspend fun sync(): SyncResult = SyncRepository(
            server.remote(owner), wallets, categories, transactions, FakeBudgetDao(), FakeGoalDao(), FakeSyncDao(),
            FakeSyncPreferences(enabled = true), recurring = recurring, debtTerms = FakeDebtTermsDao(), clock = { now },
            mainCurrency = { "PHP" }, adoptMainCurrency = {},
        ).sync()

        suspend fun wallet(name: String, type: String, kind: String? = null, icon: String? = null, spendable: Boolean? = null, credit: Long? = null): WalletEntity {
            val id = wallets.insert(
                WalletEntity(
                    name = name, type = type, currencyCode = "PHP", openingBalanceMinor = 10_000, createdAt = now, updatedAt = now,
                    clientUuid = UUID.randomUUID().toString(), kind = kind, iconKey = icon, spendable = spendable, spendableCreditMinor = credit,
                ),
            )
            return wallets.getById(id)!!
        }
    }

    private fun success(result: SyncResult) = assertTrue("expected success, got $result", result is SyncResult.Success)

    private fun sent(table: String): List<JsonObject> = server.upserts.filter { it.first == table }.flatMap { it.second }

    private val v14Keys = listOf("kind", "icon_key", "spendable", "spendable_credit_minor")

    @Test
    fun `a phone that never sets them sends none of the new keys`() = runTest {
        val phone = Device()
        phone.wallet("Bank", "BANK")
        phone.wallet("Cash", "CASH")
        success(phone.sync())
        assertTrue(sent("wallets").none { row -> v14Keys.any { row.containsKey(it) } })
    }

    @Test
    fun `once one wallet uses them, every wallet of the batch carries them, nulls included, and another phone gets them`() = runTest {
        val first = Device()
        first.wallet("Bank", "BANK")
        first.wallet("MP2 2026", "INVESTMENT", kind = "mp2", icon = "sprout", spendable = false)
        first.wallet("Visa", "CREDIT_CARD", spendable = true, credit = 1_000_000)
        success(first.sync())

        val rows = sent("wallets").associateBy { (it["name"] as JsonPrimitive).content }
        assertEquals(JsonPrimitive("INVESTMENT"), rows.getValue("MP2 2026")["type"])
        assertEquals(JsonPrimitive("mp2"), rows.getValue("MP2 2026")["kind"])
        assertEquals(JsonPrimitive("sprout"), rows.getValue("MP2 2026")["icon_key"])
        assertEquals(JsonPrimitive(false), rows.getValue("MP2 2026")["spendable"])
        assertEquals(JsonNull, rows.getValue("Bank")["kind"])
        assertEquals(JsonNull, rows.getValue("Bank")["spendable"])
        assertEquals(JsonPrimitive(1_000_000L), rows.getValue("Visa")["spendable_credit_minor"])

        val second = Device()
        success(second.sync())
        val pulled = second.wallets.rows.value.associateBy { it.name }
        assertEquals("INVESTMENT", pulled.getValue("MP2 2026").type)
        assertEquals("mp2", pulled.getValue("MP2 2026").kind)
        assertEquals("sprout", pulled.getValue("MP2 2026").iconKey)
        assertEquals(false, pulled.getValue("MP2 2026").spendable)
        assertEquals(true, pulled.getValue("Visa").spendable)
        assertEquals(1_000_000L, pulled.getValue("Visa").spendableCreditMinor)
        assertNull(pulled.getValue("Bank").kind)
    }

    @Test
    fun `a scheduled transfer travels to another phone`() = runTest {
        val first = Device()
        val bank = first.wallet("Bank", "BANK")
        val mp2 = first.wallet("MP2 2026", "INVESTMENT")
        first.recurring.insert(
            RecurringEntity(
                name = "MP2 contribution", type = "TRANSFER_OUT", amountMinor = 100_000, currencyCode = "PHP", walletId = bank.id,
                categoryId = null, frequency = "MONTHLY", intervalCount = 1, startOn = 20_400, endOn = null, nextIndex = 0,
                paused = false, createdAt = first.now, updatedAt = first.now, toWalletId = mp2.id, clientUuid = UUID.randomUUID().toString(),
            ),
        )
        success(first.sync())
        assertEquals(JsonPrimitive("TRANSFER_OUT"), sent("recurring").single()["type"])

        val second = Device()
        success(second.sync())
        val schedule = second.recurring.getAll().single()
        assertEquals("TRANSFER_OUT", schedule.type)
        assertNull(schedule.categoryId)
        val names = second.wallets.rows.value.associate { it.id to it.name }
        assertEquals("Bank", names[schedule.walletId])
        assertEquals("MP2 2026", names[schedule.toWalletId])
    }

    @Test
    fun `pulled rows are held to the server's rules for the new values`() {
        fun wallet(extra: Map<String, Any?>) = buildJsonObject {
            put("type", "INVESTMENT")
            put("currency_code", "PHP")
            put("opening_balance_minor", 0)
            extra.forEach { (key, value) ->
                when (value) {
                    null -> put(key, JsonNull)
                    is String -> put(key, value)
                    is Long -> put(key, value)
                    is Boolean -> put(key, value)
                }
            }
        }
        assertNull(PulledRowRules.problem("wallets", wallet(mapOf("kind" to "mp2", "icon_key" to "sprout", "spendable_credit_minor" to 0L))))
        assertNull(PulledRowRules.problem("wallets", wallet(mapOf("kind" to null, "spendable" to true))))
        assertEquals("kind", PulledRowRules.problem("wallets", wallet(mapOf("kind" to "Stocks!"))))
        assertEquals("icon_key", PulledRowRules.problem("wallets", wallet(mapOf("icon_key" to "x".repeat(33)))))
        assertEquals("spendable_credit_minor", PulledRowRules.problem("wallets", wallet(mapOf("spendable_credit_minor" to -1L))))

        fun schedule(type: String) = buildJsonObject {
            put("type", type)
            put("currency_code", "PHP")
            put("frequency", "MONTHLY")
            put("interval_count", 1)
            put("amount_minor", 100)
        }
        assertNull(PulledRowRules.problem("recurring", schedule("TRANSFER_OUT")))
        assertEquals("type", PulledRowRules.problem("recurring", schedule("TRANSFER_IN")))
    }

    @Test
    fun `a pulled scheduled transfer into a card is skipped, as the editor and backups refuse it`() = runTest {
        // QA, 2026-11-17: the server only checks that it has a destination; the phone holds it to the editor's rules.
        val first = Device()
        val bank = first.wallet("Bank", "BANK")
        val card = first.wallet("Visa", "CREDIT_CARD")
        first.recurring.insert(
            RecurringEntity(
                name = "Visa top-up", type = "TRANSFER_OUT", amountMinor = 100_000, currencyCode = "PHP", walletId = bank.id,
                categoryId = null, frequency = "MONTHLY", intervalCount = 1, startOn = 20_400, endOn = null, nextIndex = 0,
                paused = false, createdAt = first.now, updatedAt = first.now, toWalletId = card.id, clientUuid = UUID.randomUUID().toString(),
            ),
        )
        success(first.sync())

        val second = Device()
        success(second.sync())
        assertTrue(second.recurring.getAll().isEmpty())
        assertEquals(setOf("Bank", "Visa"), second.wallets.rows.value.map { it.name }.toSet())
    }
}
