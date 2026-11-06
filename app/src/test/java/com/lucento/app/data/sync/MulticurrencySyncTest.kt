package com.lucento.app.data.sync

import com.lucento.app.data.local.TransactionEntity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Multicurrency over sync (supabase/migrations/20261106090000_multicurrency.sql): values travel with their rows, a
 * one-currency ledger sends exactly what v12 sent, and every phone of a ledger ends up with the same main currency.
 */
class MulticurrencySyncTest {

    private val owner = "11111111-1111-4111-8111-111111111111"
    private val partner = "22222222-2222-4222-8222-222222222222"
    private val server = FakeSyncServer().apply { members[partner] = owner }

    private inner class Device(val account: String, var main: String? = "PHP", space: String? = null) {
        val wallets = FakeWalletDao()
        val categories = FakeCategoryDao()
        val transactions = FakeTransactionDao()
        val tombstones = FakeSyncDao()
        val prefs = FakeSyncPreferences(enabled = true).apply { sharedSpaceOwner = space }
        val now = 1_000_000_000_000L

        init {
            wallets.transactions = transactions
            categories.transactions = transactions
        }

        suspend fun sync(): SyncResult = SyncRepository(
            server.remote(account), wallets, categories, transactions, FakeBudgetDao(), FakeGoalDao(), tombstones, prefs,
            recurring = FakeRecurringDao(), debtTerms = FakeDebtTermsDao(), clock = { now },
            mainCurrency = { main },
            adoptMainCurrency = { main = it },
        ).sync()

        suspend fun wallet(currency: String, basis: String? = null): WalletEntity {
            val id = wallets.insert(
                WalletEntity(
                    name = "$currency wallet", type = "BANK", currencyCode = currency, openingBalanceMinor = 10_000, createdAt = now,
                    updatedAt = now, clientUuid = UUID.randomUUID().toString(), fxBasisRate = basis,
                ),
            )
            return wallets.getById(id)!!
        }

        suspend fun expense(wallet: WalletEntity, amountMinor: Long, base: Long? = null): TransactionEntity {
            val id = transactions.insert(
                TransactionEntity(
                    type = "EXPENSE", amountMinor = amountMinor, currencyCode = wallet.currencyCode, walletId = wallet.id,
                    categoryId = null, occurredOn = 20_398, note = "", clientUuid = UUID.randomUUID().toString(), createdAt = now,
                    updatedAt = now, baseAmountMinor = base, baseCurrency = base?.let { "PHP" }, fxSource = base?.let { "MARKET" },
                ),
            )
            return transactions.getById(id)!!
        }
    }

    private fun success(result: SyncResult) = assertTrue("expected success, got $result", result is SyncResult.Success)

    private fun sent(table: String): List<JsonObject> = server.upserts.filter { it.first == table }.flatMap { it.second }

    @Test
    fun `a one-currency ledger sends no FX keys at all, exactly what v12 sent`() = runTest {
        val phone = Device(owner)
        val pesos = phone.wallet("PHP")
        phone.expense(pesos, 5_000)
        success(phone.sync())
        assertTrue(sent("wallets").none { it.containsKey("fx_basis_rate") || it.containsKey("fx_revalue") })
        assertTrue(sent("transactions").none { it.containsKey("base_amount_minor") || it.containsKey("fx_source") })
    }

    @Test
    fun `with a wallet in another currency, every row of the batch carries the keys, nulls included`() = runTest {
        val phone = Device(owner)
        val pesos = phone.wallet("PHP")
        val dollars = phone.wallet("USD", basis = "55")
        phone.expense(pesos, 5_000)
        phone.expense(dollars, 1_000, base = 58_000)
        success(phone.sync())

        val wallets = sent("wallets").associateBy { it["currency_code"] }
        assertEquals(JsonPrimitive("55"), wallets.getValue(JsonPrimitive("USD"))["fx_basis_rate"])
        assertEquals(JsonNull, wallets.getValue(JsonPrimitive("PHP"))["fx_basis_rate"])
        val rows = sent("transactions").associateBy { it["currency_code"] }
        assertEquals(JsonPrimitive(58_000), rows.getValue(JsonPrimitive("USD"))["base_amount_minor"])
        assertEquals(JsonPrimitive("PHP"), rows.getValue(JsonPrimitive("USD"))["base_currency"])
        assertEquals(JsonNull, rows.getValue(JsonPrimitive("PHP"))["base_amount_minor"])
    }

    @Test
    fun `another phone of the ledger gets the rates and values`() = runTest {
        val first = Device(owner)
        val dollars = first.wallet("USD", basis = "55")
        val spend = first.expense(dollars, 1_000, base = 58_000)
        success(first.sync())

        val second = Device(owner)
        success(second.sync())
        assertEquals("55", second.wallets.rows.value.single().fxBasisRate)
        val pulled = second.transactions.rows.value.single { it.clientUuid == spend.clientUuid }
        assertEquals(58_000L, pulled.baseAmountMinor)
        assertEquals("PHP", pulled.baseCurrency)
        assertEquals("MARKET", pulled.fxSource)
    }

    @Test
    fun `a value that doesn't read right is dropped, never misread — the row is kept`() = runTest {
        val first = Device(owner)
        val dollars = first.wallet("USD", basis = "55")
        val spend = first.expense(dollars, 1_000, base = 58_000)
        success(first.sync())
        // Another client stored a value in the row's own currency (the database refuses this; a fake bypass here).
        val table = server.tables.getValue("transactions")
        table[spend.clientUuid] = JsonObject(table.getValue(spend.clientUuid) + ("base_currency" to JsonPrimitive("USD")))

        val second = Device(owner)
        success(second.sync())
        val pulled = second.transactions.rows.value.single { it.clientUuid == spend.clientUuid }
        assertNull(pulled.baseAmountMinor)
        assertNull(pulled.fxSource)
        assertEquals(1_000L, pulled.amountMinor)
    }

    @Test
    fun `the first phone sets the ledger's main currency, and a phone with another one takes it`() = runTest {
        val first = Device(owner, main = "PHP")
        success(first.sync())
        assertEquals("PHP", server.mainCurrencies[owner])

        val reinstalled = Device(owner, main = "USD")
        success(reinstalled.sync())
        assertEquals("the server's main currency wins", "PHP", reinstalled.main)
        assertEquals("and the server keeps the first", "PHP", server.mainCurrencies[owner])
    }

    @Test
    fun `a partner's phone takes the owner's main currency and never sets one`() = runTest {
        val partnerPhone = Device(partner, main = "USD", space = owner)
        success(partnerPhone.sync())
        assertFalse("nothing set from a Partner's phone", owner in server.mainCurrencies || partner in server.mainCurrencies)

        success(Device(owner, main = "PHP").sync())
        success(partnerPhone.sync())
        assertEquals("PHP", partnerPhone.main)
    }

    @Test
    fun `a project without multicurrency still syncs a one-currency ledger`() = runTest {
        server.noLedgerSettings = true
        val phone = Device(owner)
        phone.expense(phone.wallet("PHP"), 5_000)
        success(phone.sync())
        assertEquals("PHP", phone.main)
        assertEquals(1, server.live("transactions", owner).size)
    }
}
