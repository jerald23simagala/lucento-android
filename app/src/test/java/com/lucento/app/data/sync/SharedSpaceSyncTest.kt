package com.lucento.app.data.sync

import com.lucento.app.data.local.CategoryEntity
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Lucento Shared (docs/shared): an Owner's phone and a Partner's phone on one Shared Space, against [FakeSyncServer]
 * with the rules of supabase/migrations/20261105120000_lucento_shared.sql. The Partner syncs the Owner's environment
 * under their own account, may change only transactions, and loses access the moment the membership is gone.
 */
class SharedSpaceSyncTest {

    private val owner = "11111111-1111-4111-8111-111111111111"
    private val partner = "22222222-2222-4222-8222-222222222222"
    private val server = FakeSyncServer().apply { members[partner] = owner }

    /** One phone: its own Room fakes, clock and sync preferences. [space] binds it to that Owner's Shared Space. */
    private inner class Device(val account: String, space: String? = null, var now: Long = 1_000_000_000_000L) {
        val wallets = FakeWalletDao()
        val categories = FakeCategoryDao()
        val transactions = FakeTransactionDao()
        val budgets = FakeBudgetDao()
        val goals = FakeGoalDao()
        val recurring = FakeRecurringDao()
        val debtTerms = FakeDebtTermsDao()
        val tombstones = FakeSyncDao()
        val prefs = FakeSyncPreferences(enabled = true).apply { sharedSpaceOwner = space }
        val revokedFor = mutableListOf<String>()
        val softDeletes = mutableListOf<String>()
        var dropNextUpsertResponse = false

        init {
            wallets.transactions = transactions
            categories.transactions = transactions
            categories.budgets = budgets
        }

        suspend fun sync(): SyncResult {
            val real = server.remote(account)
            val remote = object : SyncRemote by real {
                override suspend fun upsert(table: String, rows: List<JsonObject>): List<JsonObject> {
                    val accepted = real.upsert(table, rows)
                    if (dropNextUpsertResponse && rows.isNotEmpty()) {
                        dropNextUpsertResponse = false
                        error("connection reset after the write")
                    }
                    return accepted
                }

                override suspend fun softDelete(table: String, userId: String, id: String, baseRevision: Long?, atIso: String): Boolean {
                    softDeletes += table
                    return real.softDelete(table, userId, id, baseRevision, atIso)
                }
            }
            return SyncRepository(
                remote, wallets, categories, transactions, budgets, goals, tombstones, prefs,
                recurring = recurring, debtTerms = debtTerms, clock = { now },
                onMembershipRevoked = { revokedFor += it },
            ).sync()
        }

        suspend fun addWallet(name: String = "Household", classification: String? = null): WalletEntity {
            val id = wallets.insert(
                WalletEntity(
                    name = name, type = "BANK", currencyCode = "PHP", openingBalanceMinor = 0, createdAt = now, updatedAt = now,
                    clientUuid = UUID.randomUUID().toString(), classification = classification,
                )
            )
            return wallets.getById(id)!!
        }

        suspend fun addCategory(name: String = "Groceries"): CategoryEntity {
            val id = categories.insert(
                CategoryEntity(name = name, kind = "EXPENSE", iconKey = "cart", sortOrder = 0, createdAt = now, updatedAt = now, clientUuid = UUID.randomUUID().toString())
            )
            return categories.getById(id)!!
        }

        /** What the composer writes: a new row, stamped with this phone's account (display only). */
        suspend fun addTransaction(walletId: Long, amountMinor: Long, note: String = "", categoryId: Long? = null): TransactionEntity {
            val id = transactions.insert(
                TransactionEntity(
                    type = "EXPENSE", amountMinor = amountMinor, currencyCode = "PHP", walletId = walletId, categoryId = categoryId,
                    occurredOn = 20_396, note = note, clientUuid = UUID.randomUUID().toString(), createdAt = now, updatedAt = now,
                    createdBy = account,
                )
            )
            return transactions.getById(id)!!
        }

        fun tx(clientUuid: String) = transactions.rows.value.singleOrNull { it.clientUuid == clientUuid }

        /** An ordinary edit: bumps updated_at; the Room trigger then marks the row dirty. */
        suspend fun editNote(clientUuid: String, note: String) {
            val current = tx(clientUuid)!!
            transactions.update(current.copy(note = note, updatedAt = now, dirty = true, updatedBy = account))
        }

        /** A local delete; the Room AFTER DELETE trigger records the tombstone. */
        suspend fun deleteTransaction(clientUuid: String) {
            val current = tx(clientUuid)!!
            transactions.deleteById(current.id)
            tombstones.insert(SyncTombstoneEntity(tableName = "transactions", clientUuid = clientUuid, deletedAt = now, baseRevision = current.baseRevision))
        }

        suspend fun pending(): Int = tombstones.unpushedTombstones().size + transactions.getDirty().size + wallets.getDirty().size +
            categories.getDirty().size + budgets.getDirty().size
    }

    private fun success(result: SyncResult): SyncReport {
        assertTrue("expected success, got $result", result is SyncResult.Success)
        return (result as SyncResult.Success).report
    }

    /** The Owner's phone with a wallet, a category and one expense, synced; and the Partner's phone after joining. */
    private suspend fun ownerAndPartner(): Triple<Device, Device, TransactionEntity> {
        val o = Device(owner)
        val wallet = o.addWallet()
        val groceries = o.addCategory()
        val t = o.addTransaction(wallet.id, 1_250_00, note = "groceries", categoryId = groceries.id)
        success(o.sync())
        val p = Device(partner, space = owner, now = o.now)
        success(p.sync())
        return Triple(o, p, t)
    }

    @Test
    fun `a partner's phone downloads the owner's ledger under the partner's own account`() = runTest {
        val (_, p, t) = ownerAndPartner()

        assertEquals(listOf("Household"), p.wallets.rows.value.map { it.name })
        assertEquals(listOf("Groceries"), p.categories.getAll().map { it.name })
        assertEquals("groceries", p.tx(t.clientUuid)!!.note)
        // The account binding stays the partner's own; the environment is the owner's.
        assertEquals(partner, p.prefs.syncedUser)
        assertEquals(owner, p.prefs.initialPullDone)
        assertEquals(0, p.pending())
    }

    @Test
    fun `a transaction a partner adds offline syncs into the owner's environment, stamped as theirs`() = runTest {
        val (o, p, _) = ownerAndPartner()
        p.now += 60_000
        val coffee = p.addTransaction(p.wallets.rows.value.single().id, 120_00, note = "coffee") // offline
        assertEquals(1, p.pending())

        success(p.sync())
        success(o.sync())

        val row = server.rows("transactions", owner).single { it.str("id") == coffee.clientUuid }
        assertEquals(owner, row.str("user_id"))
        assertEquals(partner, row.str("created_by"))
        assertEquals(partner, o.tx(coffee.clientUuid)!!.createdBy)
        assertEquals("coffee", o.tx(coffee.clientUuid)!!.note)
        assertEquals(0, p.pending())
    }

    @Test
    fun `a forged creator never reaches the server and the server's attribution comes back unchanged otherwise`() = runTest {
        val (_, p, _) = ownerAndPartner()
        // Even a row that claims another creator locally is stamped by the server with the signed-in account.
        val wallet = p.wallets.rows.value.single()
        val id = p.transactions.insert(
            TransactionEntity(
                type = "EXPENSE", amountMinor = 5_00, currencyCode = "PHP", walletId = wallet.id, categoryId = null, occurredOn = 20_396,
                note = "", clientUuid = UUID.randomUUID().toString(), createdAt = p.now, updatedAt = p.now, createdBy = owner,
            )
        )
        success(p.sync())

        val pushed = server.upserts.filter { it.first == "transactions" }.flatMap { it.second }
        assertTrue("attribution is never uploaded", pushed.none { "created_by" in it || "updated_by" in it })
        // The pull after the push brings the server's attribution back to the row that was otherwise unchanged.
        assertEquals(partner, p.transactions.getById(id)!!.createdBy)
        assertFalse(p.transactions.getById(id)!!.dirty)
    }

    @Test
    fun `a partner edits the owner's transaction and the owner receives it with both names kept`() = runTest {
        val (o, p, t) = ownerAndPartner()
        p.now += 60_000
        p.editNote(t.clientUuid, "groceries and soap")

        success(p.sync())
        success(o.sync())

        val ownerCopy = o.tx(t.clientUuid)!!
        assertEquals("groceries and soap", ownerCopy.note)
        assertEquals(owner, ownerCopy.createdBy)
        assertEquals(partner, ownerCopy.updatedBy)
    }

    @Test
    fun `a partner deletes a transaction and it leaves the owner's phone too`() = runTest {
        val (o, p, t) = ownerAndPartner()
        p.now += 60_000
        p.deleteTransaction(t.clientUuid)

        success(p.sync())
        success(o.sync())

        assertNull(o.tx(t.clientUuid))
        assertTrue(server.live("transactions", owner).none { it.str("id") == t.clientUuid })
        assertEquals(listOf("transactions"), p.softDeletes)
    }

    @Test
    fun `concurrent edits by owner and partner converge on the later edit, and the losing phone is told`() = runTest {
        val (o, p, t) = ownerAndPartner()
        o.now += 60_000
        o.editNote(t.clientUuid, "owner's version") // offline, earlier
        p.now = o.now + 60_000
        p.editNote(t.clientUuid, "partner's version") // later

        val partnerReport = success(p.sync())
        val ownerReport = success(o.sync()) // the owner's older edit was still waiting: it loses, and the owner is told

        assertEquals("partner's version", o.tx(t.clientUuid)!!.note)
        assertEquals("partner's version", p.tx(t.clientUuid)!!.note)
        assertEquals(0, partnerReport.overridden)
        assertEquals(1, ownerReport.overridden)
        assertEquals(1, o.prefs.overriddenChanges)
        assertEquals(0, o.pending() + p.pending())
    }

    @Test
    fun `a partner's later edit of a transaction the owner already synced simply wins, with no notice`() = runTest {
        val (o, p, t) = ownerAndPartner()
        o.now += 60_000
        o.editNote(t.clientUuid, "owner's version")
        success(o.sync())
        p.now = o.now + 60_000
        p.editNote(t.clientUuid, "partner's version") // made from the older copy: concurrent, but later

        success(p.sync())
        success(o.sync())

        assertEquals("partner's version", o.tx(t.clientUuid)!!.note)
        assertEquals(0, o.prefs.overriddenChanges + p.prefs.overriddenChanges)
    }

    @Test
    fun `an edit made after the other person's delete brings the transaction back everywhere`() = runTest {
        val (o, p, t) = ownerAndPartner()
        o.now += 60_000
        o.deleteTransaction(t.clientUuid)
        success(o.sync())
        p.now = o.now + 60_000
        p.editNote(t.clientUuid, "still bought it") // the partner hadn't seen the delete and edits later

        success(p.sync())
        success(o.sync())

        assertEquals("still bought it", p.tx(t.clientUuid)!!.note)
        assertEquals("still bought it", o.tx(t.clientUuid)!!.note)
        assertEquals(1, server.live("transactions", owner).count { it.str("id") == t.clientUuid })
    }

    @Test
    fun `both people deleting the same transaction settles without errors or leftovers`() = runTest {
        val (o, p, t) = ownerAndPartner()
        o.now += 60_000
        o.deleteTransaction(t.clientUuid)
        p.now = o.now + 1_000
        p.deleteTransaction(t.clientUuid)

        success(o.sync())
        success(p.sync())
        success(o.sync())

        assertNull(o.tx(t.clientUuid))
        assertNull(p.tx(t.clientUuid))
        assertTrue(server.live("transactions", owner).none { it.str("id") == t.clientUuid })
        assertEquals(0, o.pending() + p.pending())
    }

    @Test
    fun `a partner's push whose reply is lost is retried without a duplicate`() = runTest {
        val (o, p, _) = ownerAndPartner()
        p.now += 60_000
        val coffee = p.addTransaction(p.wallets.rows.value.single().id, 120_00, note = "coffee")
        p.dropNextUpsertResponse = true
        assertTrue(p.sync() is SyncResult.Retryable)
        success(p.sync())
        success(o.sync())

        assertEquals(1, server.rows("transactions", owner).count { it.str("id") == coffee.clientUuid })
        assertEquals(1, o.transactions.rows.value.count { it.clientUuid == coffee.clientUuid })
        assertEquals(0, p.pending())
    }

    @Test
    fun `a partner's phone never uploads a structure change, and the owner's copy replaces it`() = runTest {
        val (o, p, _) = ownerAndPartner()
        val wallet = p.wallets.rows.value.single()
        val category = p.categories.getAll().single()
        // Changes the app never offers a Partner: if one existed anyway, it must not fail the sync or reach the server.
        p.now += 60_000
        p.wallets.update(wallet.copy(name = "Renamed by partner", updatedAt = p.now, dirty = true))
        p.categories.update(category.copy(name = "Renamed too", updatedAt = p.now, dirty = true))
        val coffee = p.addTransaction(wallet.id, 120_00, note = "coffee")
        val before = server.upserts.size

        val report = success(p.sync())
        val partnerBatches = server.upserts.subList(before, server.upserts.size).toList()
        success(o.sync())

        assertTrue(partnerBatches.isNotEmpty())
        assertTrue("the partner's cycle sent transactions only", partnerBatches.all { it.first == "transactions" })
        assertEquals(2, report.notPermitted)
        assertEquals("Household", p.wallets.rows.value.single().name)
        assertEquals("Groceries", p.categories.getAll().single().name)
        assertEquals("coffee", o.tx(coffee.clientUuid)!!.note) // the transaction in the same cycle still went up
        assertEquals(0, p.pending())
    }

    @Test
    fun `deleting a wallet on the owner's phone reaches the partner without the partner sending a wallet delete`() = runTest {
        val (o, p, _) = ownerAndPartner()
        val spare = o.addWallet("Spare")
        success(o.sync())
        success(p.sync())
        o.now += 60_000
        val ownerSpare = o.wallets.getById(spare.id)!!
        o.wallets.deleteById(spare.id)
        o.tombstones.insert(SyncTombstoneEntity(tableName = "wallets", clientUuid = spare.clientUuid!!, deletedAt = o.now, baseRevision = ownerSpare.baseRevision))
        success(o.sync())

        success(p.sync())
        assertTrue(p.wallets.rows.value.none { it.clientUuid == spare.clientUuid })
        // The real database's trigger records the applied delete locally too: that echo is settled, never uploaded.
        p.tombstones.insert(SyncTombstoneEntity(tableName = "wallets", clientUuid = spare.clientUuid!!, deletedAt = p.now, baseRevision = null))
        success(p.sync())

        assertTrue(p.softDeletes.none { it == "wallets" })
        assertEquals(0, p.pending())
        assertTrue(p.wallets.rows.value.none { it.clientUuid == spare.clientUuid })
    }

    @Test
    fun `removing the partner stops their phone before it sends or receives anything`() = runTest {
        val (o, p, t) = ownerAndPartner()
        p.now += 60_000
        val late = p.addTransaction(p.wallets.rows.value.single().id, 99_00, note = "after removal")
        server.members.remove(partner)
        o.now += 60_000
        o.editNote(t.clientUuid, "owner edit after removal")
        success(o.sync())

        val result = p.sync()

        assertEquals(SyncResult.MembershipRevoked(owner), result)
        assertEquals(listOf(owner), p.revokedFor)
        assertTrue(server.rows("transactions", owner).none { it.str("id") == late.clientUuid })
        assertEquals("groceries", p.tx(t.clientUuid)!!.note) // nothing pulled either
    }

    @Test
    fun `without membership the database shows a partner nothing and refuses their writes`() = runTest {
        val (_, p, t) = ownerAndPartner()
        server.members.remove(partner)
        val remote = server.remote(partner)

        assertTrue(remote.fetchPage("transactions", owner, SyncTime.toIso(0), null, 500).isEmpty())
        assertFalse(remote.softDelete("transactions", owner, t.clientUuid, null, SyncTime.toIso(p.now)))
        val refused = runCatching {
            remote.upsert("transactions", listOf(server.rows("transactions", owner).single()))
        }
        assertTrue(refused.isFailure)
    }

    @Test
    fun `a classified wallet sends the label for the whole batch, and an unclassified batch sends none`() = runTest {
        val o = Device(owner)
        o.addWallet("Household", classification = "SHARED")
        o.addWallet("Alex's card", classification = null)
        success(o.sync())
        val labelled = server.upserts.single { it.first == "wallets" }.second
        assertTrue(labelled.all { "classification" in it })

        val solo = Device("33333333-3333-4333-8333-333333333333")
        solo.addWallet("Cash")
        success(solo.sync())
        val plain = server.upserts.last { it.first == "wallets" }.second
        assertTrue("a phone that never classifies sends exactly what v11 sent", plain.none { "classification" in it })
    }

    @Test
    fun `a label set on the owner's phone reaches the partner, and a phone without labels never clears it`() = runTest {
        val (o, p, _) = ownerAndPartner()
        val wallet = o.wallets.rows.value.single()
        o.now += 60_000
        o.wallets.update(wallet.copy(classification = "OWNER", updatedAt = o.now, dirty = true))
        success(o.sync())
        success(p.sync())
        assertEquals("OWNER", p.wallets.rows.value.single().classification)

        // An older phone of the owner (or one that never classified) renames the wallet: the label stays on the server.
        val stored = server.rows("wallets", owner).single()
        server.write("wallets", owner, JsonObject(stored - "classification" + mapOf(
            "name" to JsonPrimitive("Household account"),
            "updated_at" to JsonPrimitive(SyncTime.toIso(o.now + 60_000)),
            "base_revision" to stored.getValue("revision"),
        )))
        success(p.sync())
        assertEquals("Household account", p.wallets.rows.value.single().name)
        assertEquals("OWNER", p.wallets.rows.value.single().classification)
    }

    @Test
    fun `a label this version doesn't know arrives as unclassified instead of hiding the wallet`() = runTest {
        val (o, p, _) = ownerAndPartner()
        val stored = server.rows("wallets", owner).single()
        server.write("wallets", owner, JsonObject(stored + mapOf(
            "classification" to JsonPrimitive("HOUSEHOLD_V2"),
            "updated_at" to JsonPrimitive(SyncTime.toIso(o.now + 60_000)),
            "base_revision" to stored.getValue("revision"),
        )))
        success(p.sync())
        assertEquals(1, p.wallets.rows.value.size)
        assertNull(p.wallets.rows.value.single().classification)
    }

    @Test
    fun `a partner's phone leaves duplicate categories for the owner's phone to fold`() = runTest {
        val o = Device(owner)
        o.addWallet()
        o.addCategory("Rent")
        success(o.sync())
        // A second setup on the owner's account created another "Rent" that hasn't been folded yet.
        server.write("categories", owner, JsonObject(mapOf(
            "id" to JsonPrimitive("z-rent"), "user_id" to JsonPrimitive(owner), "name" to JsonPrimitive("rent"),
            "kind" to JsonPrimitive("EXPENSE"), "icon_key" to JsonPrimitive("home"), "sort_order" to JsonPrimitive(1),
            "archived" to JsonPrimitive(false), "created_at" to JsonPrimitive(SyncTime.toIso(o.now)),
            "updated_at" to JsonPrimitive(SyncTime.toIso(o.now)),
        )))
        val p = Device(partner, space = owner, now = o.now)
        val before = server.upserts.size

        success(p.sync())

        assertEquals(2, p.categories.getAll().count { it.name.trim().equals("rent", ignoreCase = true) })
        assertEquals(0, p.pending())
        assertTrue("the partner's phone wrote no category", server.upserts.drop(before).none { it.first == "categories" })
    }

    @Test
    fun `solo phones are unchanged - their rows carry no attribution or label keys and stay theirs`() = runTest {
        val solo = "33333333-3333-4333-8333-333333333333"
        val d = Device(solo)
        val w = d.addWallet("Cash")
        d.addTransaction(w.id, 10_00)
        success(d.sync())

        server.upserts.flatMap { it.second }.forEach { row ->
            assertEquals(solo, row.str("user_id"))
            assertFalse("created_by" in row || "updated_by" in row || "classification" in row)
        }
        assertEquals(solo, server.rows("transactions", solo).single().str("created_by"))
        assertEquals(solo, d.prefs.initialPullDone)
    }
}
