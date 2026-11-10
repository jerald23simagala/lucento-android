package com.lucento.app.data.sync

import com.lucento.app.data.local.SyncTombstoneEntity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Security audit F-01 (supabase/migrations/20261111090000_cloud_write_gate_and_quotas.sql): the server refuses uploads
 * from an account whose plan doesn't include cloud sync (42501), or past its row ceiling (LU001). The phone must keep
 * every local change, still pull, explain it in plain words, and not retry a write that can't succeed — until the plan
 * changes or the cooldown ends. Also: uploads go in chunks under the server's batch cap, and a row the server's size
 * bounds would refuse stays pending on its own instead of failing its batch.
 */
class UploadRefusalTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val server = FakeSyncServer().apply { cloud = mutableSetOf() }

    private inner class Device(var now: Long = 1_000_000_000_000L) {
        val wallets = FakeWalletDao()
        val categories = FakeCategoryDao()
        val transactions = FakeTransactionDao()
        val budgets = FakeBudgetDao()
        val goals = FakeGoalDao()
        val recurring = FakeRecurringDao()
        val debtTerms = FakeDebtTermsDao()
        val tombstones = FakeSyncDao()
        val prefs = FakeSyncPreferences(enabled = true)
        var plan = "free"
        var refusalsReported = 0

        init {
            wallets.transactions = transactions
            categories.transactions = transactions
            categories.budgets = budgets
        }

        suspend fun sync(): SyncResult = SyncRepository(
            server.remote(user), wallets, categories, transactions, budgets, goals, tombstones, prefs,
            recurring = recurring, debtTerms = debtTerms, clock = { now },
            planFingerprint = { plan }, onUploadsRefused = { refusalsReported++ },
        ).sync()

        suspend fun addWallet(): WalletEntity {
            val id = wallets.insert(
                WalletEntity(name = "Cash", type = "CASH", currencyCode = "USD", openingBalanceMinor = 0, createdAt = now, updatedAt = now, clientUuid = UUID.randomUUID().toString())
            )
            return wallets.getById(id)!!
        }

        suspend fun addTransaction(walletId: Long, note: String = ""): TransactionEntity {
            val id = transactions.insert(
                TransactionEntity(type = "EXPENSE", amountMinor = 100, currencyCode = "USD", walletId = walletId, categoryId = null, occurredOn = 20_000, note = note, clientUuid = UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
            )
            return transactions.getById(id)!!
        }

        suspend fun pending(): Int = transactions.getDirty().size + wallets.getDirty().size + tombstones.unpushedTombstones().size
    }

    private fun paused(result: SyncResult): SyncResult.UploadsPaused {
        assertTrue("expected UploadsPaused, got $result", result is SyncResult.UploadsPaused)
        return result as SyncResult.UploadsPaused
    }

    @Test
    fun `a refused upload keeps every local change pending and explains the plan`() = runTest {
        val phone = Device()
        val cash = phone.addWallet()
        phone.addTransaction(cash.id, "lunch")
        val before = phone.pending()

        val result = paused(phone.sync())

        assertEquals(UploadRefusal.PLAN, result.reason)
        assertEquals("nothing was lost or marked as sent", before, phone.pending())
        assertTrue(server.rows("wallets", user).isEmpty())
        assertEquals(SyncRepository.refusalMessage(UploadRefusal.PLAN), phone.prefs.lastError)
        assertTrue("never a technical code", phone.prefs.lastError!!.none { it.isDigit() })
        assertEquals("the app re-reads its plan once", 1, phone.refusalsReported)
    }

    @Test
    fun `under the same plan a refused upload is not retried until the cooldown ends`() = runTest {
        val phone = Device()
        phone.addTransaction(phone.addWallet().id)
        paused(phone.sync())
        val attempts = server.upserts.size

        phone.now += 5 * 60_000L
        paused(phone.sync())
        phone.now += 5 * 60_000L
        paused(phone.sync())
        assertEquals("no request while it can't succeed", attempts, server.upserts.size)
        assertEquals("reported once, not per cycle", 1, phone.refusalsReported)

        phone.now += SyncRepository.UPLOAD_REFUSAL_COOLDOWN_MILLIS
        paused(phone.sync())
        assertTrue("one attempt after the cooldown", server.upserts.size > attempts)
    }

    @Test
    fun `renewing the plan resumes uploads at once`() = runTest {
        val phone = Device()
        phone.addTransaction(phone.addWallet().id)
        paused(phone.sync())

        server.cloud!!.add(user)
        phone.plan = "pro-renewed"
        val result = phone.sync()

        assertTrue("expected success, got $result", result is SyncResult.Success)
        assertEquals(0, phone.pending())
        assertEquals(1, server.rows("transactions", user).size)
        assertEquals(null, phone.prefs.uploadsRefused())
        assertEquals(null, phone.prefs.lastError)
    }

    @Test
    fun `a lapsed account still pulls everything it stored while it had the plan`() = runTest {
        server.cloud!!.add(user)
        val first = Device()
        first.addTransaction(first.addWallet().id, "from the first phone")
        assertTrue(first.sync() is SyncResult.Success)

        server.cloud!!.remove(user)
        val second = Device()
        second.addTransaction(second.addWallet().id, "made after the lapse")
        val result = paused(second.sync())

        assertTrue("pulled the first phone's wallet and transaction", result.report.pulled >= 2)
        assertTrue(second.transactions.getAll().any { it.note == "from the first phone" })
        assertTrue("its own change waits", second.transactions.getAll().any { it.note == "made after the lapse" && it.dirty })
    }

    @Test
    fun `a refused deletion stays queued`() = runTest {
        server.cloud!!.add(user)
        val phone = Device()
        val t = phone.addTransaction(phone.addWallet().id)
        assertTrue(phone.sync() is SyncResult.Success)

        server.cloud!!.remove(user)
        val synced = phone.transactions.getById(t.id)!!
        phone.transactions.deleteById(t.id)
        phone.tombstones.insert(SyncTombstoneEntity(tableName = "transactions", clientUuid = t.clientUuid, deletedAt = phone.now, baseRevision = synced.baseRevision))
        paused(phone.sync())

        assertEquals(1, phone.tombstones.unpushedTombstones().size)
        assertTrue("still live on the server", server.live("transactions", user).isNotEmpty())
    }

    @Test
    fun `the storage ceiling pauses uploads with its own message`() = runTest {
        server.cloud!!.add(user)
        server.rowCeiling = 1
        val phone = Device()
        val cash = phone.addWallet()
        phone.addTransaction(cash.id)
        phone.addTransaction(cash.id)

        val result = paused(phone.sync())

        assertEquals(UploadRefusal.STORAGE_LIMIT, result.reason)
        assertEquals(SyncRepository.refusalMessage(UploadRefusal.STORAGE_LIMIT), phone.prefs.lastError)
        assertEquals("both transactions wait", 2, phone.transactions.getDirty().size)
    }

    @Test
    fun `uploads go in chunks under the server's batch cap`() = runTest {
        server.cloud!!.add(user)
        val phone = Device()
        val cash = phone.addWallet()
        repeat(1_203) { phone.addTransaction(cash.id) }

        assertTrue(phone.sync() is SyncResult.Success)

        val sizes = server.upserts.filter { it.first == "transactions" }.map { it.second.size }
        assertEquals(listOf(500, 500, 203), sizes)
        assertEquals(1_203, server.rows("transactions", user).size)
        assertEquals(0, phone.transactions.getDirty().size)
    }

    @Test
    fun `a row over the server's size bounds waits alone instead of failing its batch`() = runTest {
        server.cloud!!.add(user)
        val phone = Device()
        val cash = phone.addWallet()
        phone.addTransaction(cash.id, "fine")
        val long = phone.addTransaction(cash.id, "x".repeat(PulledRowRules.MAX_NOTE + 1))

        val result = phone.sync()

        assertTrue("expected success, got $result", result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).report.invalidSkipped)
        assertEquals(1, server.rows("transactions", user).size)
        assertTrue(phone.transactions.getById(long.id)!!.dirty)
    }
}
