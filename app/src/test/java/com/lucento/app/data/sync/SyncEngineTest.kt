package com.lucento.app.data.sync

import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.data.local.DebtTermsEntity
import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.local.GoalContributionEntity
import com.lucento.app.data.local.GoalEntity
import com.lucento.app.data.local.SyncTombstoneEntity
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.repository.FakeBudgetDao
import com.lucento.app.data.repository.FakeCategoryDao
import com.lucento.app.data.repository.FakeDebtTermsDao
import com.lucento.app.data.repository.FakeRecurringDao
import com.lucento.app.data.repository.FakeGoalDao
import com.lucento.app.data.repository.FakeSyncDao
import com.lucento.app.data.repository.FakeTransactionDao
import com.lucento.app.data.repository.FakeWalletDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The push/pull/merge engine end to end against [FakeSyncServer], with two independent "devices" (separate
 * in-memory databases and clocks) signed into the same account. These are the data-safety guarantees in
 * docs/SYNC.md; each test names the guarantee it protects.
 */
class SyncEngineTest {

    private val user = "11111111-1111-4111-8111-111111111111"
    private val server = FakeSyncServer()

    /** One phone: its own Room fakes, its own clock, its own sync preferences. */
    private inner class Device(var now: Long = 1_000_000_000_000L, var userId: String? = user, val pageSize: Long = 500) {
        val wallets = FakeWalletDao()
        val categories = FakeCategoryDao()
        val transactions = FakeTransactionDao()
        val budgets = FakeBudgetDao()
        val goals = FakeGoalDao()
        val recurring = FakeRecurringDao()
        val debtTerms = FakeDebtTermsDao()
        val tombstones = FakeSyncDao()
        val prefs = FakeSyncPreferences(enabled = true)

        init {
            wallets.transactions = transactions
            categories.transactions = transactions
            categories.budgets = budgets
        }

        var serverOverride: FakeSyncServer? = null
        /** The next upsert is applied by the server, but its reply is lost (as on a dropped connection). */
        var dropNextUpsertResponse = false

        suspend fun sync(): SyncResult {
            val real = (serverOverride ?: server).remote(userId)
            val remote = object : SyncRemote by real {
                override suspend fun upsert(table: String, rows: List<JsonObject>): List<JsonObject> {
                    val accepted = real.upsert(table, rows)
                    if (dropNextUpsertResponse && rows.isNotEmpty()) {
                        dropNextUpsertResponse = false
                        error("connection reset after the write")
                    }
                    return accepted
                }
            }
            return SyncRepository(
                remote, wallets, categories, transactions, budgets, goals, tombstones, prefs,
                recurring = recurring, debtTerms = debtTerms, clock = { now }, pageSize = pageSize,
            ).sync()
        }

        suspend fun addWallet(name: String = "Cash"): WalletEntity {
            val id = wallets.insert(
                WalletEntity(name = name, type = "CASH", currencyCode = "USD", openingBalanceMinor = 0, createdAt = now, updatedAt = now, clientUuid = UUID.randomUUID().toString())
            )
            return wallets.getById(id)!!
        }

        suspend fun addCategory(name: String, clientUuid: String? = UUID.randomUUID().toString(), kind: String = "EXPENSE"): CategoryEntity {
            val id = categories.insert(CategoryEntity(name = name, kind = kind, iconKey = "other", sortOrder = 0, createdAt = now, updatedAt = 0, clientUuid = clientUuid))
            return categories.getById(id)!!
        }

        suspend fun addTransaction(walletId: Long, amountMinor: Long, categoryId: Long? = null, note: String = ""): TransactionEntity {
            val id = transactions.insert(
                TransactionEntity(type = "EXPENSE", amountMinor = amountMinor, currencyCode = "USD", walletId = walletId, categoryId = categoryId, occurredOn = 20_000, note = note, clientUuid = UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
            )
            return transactions.getById(id)!!
        }

        /** An ordinary repository edit: bumps updated_at; the Room trigger then marks the row dirty. */
        suspend fun editNote(t: TransactionEntity, note: String) {
            val current = transactions.getById(t.id)!!
            transactions.update(current.copy(note = note, updatedAt = now, dirty = true))
        }

        /** A local delete; the Room AFTER DELETE trigger records the tombstone. */
        suspend fun deleteTransaction(t: TransactionEntity) {
            val current = transactions.getById(t.id)!!
            transactions.deleteById(t.id)
            tombstones.insert(SyncTombstoneEntity(tableName = "transactions", clientUuid = t.clientUuid, deletedAt = now, baseRevision = current.baseRevision))
        }

        suspend fun deleteWallet(w: WalletEntity) {
            val current = wallets.getById(w.id)!!
            wallets.deleteById(w.id)
            tombstones.insert(SyncTombstoneEntity(tableName = "wallets", clientUuid = w.clientUuid!!, deletedAt = now, baseRevision = current.baseRevision))
        }
    }

    private fun success(result: SyncResult): SyncReport {
        assertTrue("expected success, got $result", result is SyncResult.Success)
        return (result as SyncResult.Success).report
    }

    @Test
    fun `two setups on one account fold their copies of a category into one, keeping set budgets`() = runTest {
        // An offline install set up "Rent" and budgeted it; a second setup on the same account picked "Rent" too.
        val phone = Device()
        val rent = phone.addCategory("Rent", clientUuid = "b-rent")
        phone.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = rent.id, currencyCode = "USD", limitMinor = 0, createdAt = phone.now, updatedAt = phone.now, clientUuid = UUID.randomUUID().toString()))
        success(phone.sync())

        val other = Device()
        val otherRent = other.addCategory("rent ", clientUuid = "a-rent")
        other.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = otherRent.id, currencyCode = "USD", limitMinor = 1_500_00, createdAt = other.now, updatedAt = other.now, clientUuid = UUID.randomUUID().toString()))
        val cash = other.addWallet()
        other.addTransaction(cash.id, 1_500_00, categoryId = otherRent.id)
        success(other.sync())

        // Both devices end with the copy every device picks ("a-rent"), its set amount, and the spending on it.
        success(phone.sync())
        listOf(phone, other).forEach { d ->
            val live = d.categories.getAll().filter { it.name.trim().equals("rent", ignoreCase = true) }
            assertEquals(listOf("a-rent"), live.map { it.clientUuid })
            val budgets = d.budgets.rows.value.filter { it.categoryId == live.single().id }
            assertEquals(listOf(1_500_00L), budgets.map { it.limitMinor })
        }
        assertTrue(phone.transactions.rows.value.all { it.categoryId == phone.categories.findByClientUuid("a-rent")!!.id })
    }

    @Test
    fun `D3 an offline delete loses to a later edit from another phone, even when it is uploaded last`() = runTest {
        // Device sync QA C-2: phone A deletes "coffee" while offline; phone B edits it afterwards and syncs; A reconnects
        // later. The delete must carry the time the user deleted, not the upload time, so B's newer edit survives.
        val a = Device()
        val cash = a.addWallet()
        val coffee = a.addTransaction(cash.id, 450, note = "coffee")
        success(a.sync())
        val b = Device(now = a.now)
        success(b.sync())

        a.now += 60_000
        a.deleteTransaction(coffee) // offline: nothing uploaded yet
        b.now = a.now + 60_000
        b.editNote(b.transactions.rows.value.single { it.clientUuid == coffee.clientUuid }, "coffee, large")
        success(b.sync())

        a.now = b.now + 10 * 60_000 // A comes back online much later
        success(a.sync())
        success(b.sync())

        listOf(a, b).forEach { d ->
            assertEquals(listOf("coffee, large"), d.transactions.rows.value.filter { it.clientUuid == coffee.clientUuid }.map { it.note })
        }
        assertEquals(1, server.live("transactions", user).count { it.str("id") == coffee.clientUuid })
    }

    @Test
    fun `D3 a delete made after the other phone's edit still wins when uploaded`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        val coffee = a.addTransaction(cash.id, 450, note = "coffee")
        success(a.sync())
        val b = Device(now = a.now)
        success(b.sync())

        b.now = a.now + 60_000
        b.editNote(b.transactions.rows.value.single { it.clientUuid == coffee.clientUuid }, "coffee, large")
        success(b.sync())
        a.now = b.now + 60_000
        a.deleteTransaction(coffee) // later than B's edit, uploaded later still
        a.now += 10 * 60_000
        success(a.sync())
        success(b.sync())

        listOf(a, b).forEach { d -> assertTrue(d.transactions.rows.value.none { it.clientUuid == coffee.clientUuid }) }
        assertTrue(server.live("transactions", user).none { it.str("id") == coffee.clientUuid })
    }

    /** Two phones on one account that both hold "coffee", synced. */
    private suspend fun twoPhonesWithCoffee(): Triple<Device, Device, TransactionEntity> {
        val a = Device()
        val cash = a.addWallet()
        val coffee = a.addTransaction(cash.id, 450, note = "coffee")
        success(a.sync())
        val b = Device(now = a.now)
        success(b.sync())
        return Triple(a, b, coffee)
    }

    private fun Device.coffee(c: TransactionEntity) = transactions.rows.value.singleOrNull { it.clientUuid == c.clientUuid }

    @Test
    fun `P2-6 an older pending edit replaced by a newer edit elsewhere is reported to that phone only`() = runTest {
        val (a, b, coffee) = twoPhonesWithCoffee()
        a.now += 60_000
        a.editNote(a.coffee(coffee)!!, "older edit") // offline, older
        b.now = a.now + 60_000
        b.editNote(b.coffee(coffee)!!, "newer edit")
        val bReport = success(b.sync())
        a.now = b.now + 60_000
        val aReport = success(a.sync())

        assertEquals("newer edit", a.coffee(coffee)!!.note)
        assertEquals(1, aReport.overridden)
        assertEquals(1, a.prefs.overriddenChanges)
        assertEquals(0, bReport.overridden)
        assertEquals(0, b.prefs.overriddenChanges + success(b.sync()).overridden)
    }

    @Test
    fun `P2-6 a delete undone by a newer edit elsewhere is reported`() = runTest {
        val (a, b, coffee) = twoPhonesWithCoffee()
        a.now += 60_000
        a.deleteTransaction(a.coffee(coffee)!!)
        b.now = a.now + 60_000
        b.editNote(b.coffee(coffee)!!, "kept")
        success(b.sync())
        a.now = b.now + 60_000
        val report = success(a.sync())

        assertEquals("kept", a.coffee(coffee)!!.note)
        assertEquals(1, report.overridden)
    }

    @Test
    fun `P2-6 a pending edit removed by a newer delete elsewhere is reported`() = runTest {
        val (a, b, coffee) = twoPhonesWithCoffee()
        a.now += 60_000
        a.editNote(a.coffee(coffee)!!, "older edit")
        b.now = a.now + 60_000
        b.deleteTransaction(b.coffee(coffee)!!)
        success(b.sync())
        a.now = b.now + 60_000
        val report = success(a.sync())

        assertEquals(null, a.coffee(coffee))
        assertEquals(1, report.overridden)
    }

    @Test
    fun `P2-6 this phone's edit winning, or an ordinary pull, is never reported`() = runTest {
        val (a, b, coffee) = twoPhonesWithCoffee()
        b.now = a.now + 60_000
        b.editNote(b.coffee(coffee)!!, "older edit")
        success(b.sync())
        a.now = b.now + 60_000
        a.editNote(a.coffee(coffee)!!, "newer edit") // A's later edit wins over B's
        val aReport = success(a.sync())
        b.now = a.now + 60_000
        val bReport = success(b.sync()) // B's edit was already synced: replacing it is an ordinary pull, not a lost change

        assertEquals("newer edit", b.coffee(coffee)!!.note)
        assertEquals(0, aReport.overridden)
        assertEquals(0, bReport.overridden)
    }

    @Test
    fun `C-3 two phones budgeting the same category end with one budget and the same amount everywhere`() = runTest {
        val a = Device()
        val groceries = a.addCategory("Groceries", clientUuid = "cat-groceries")
        success(a.sync())
        val b = Device(now = a.now)
        success(b.sync())
        val bGroceries = b.categories.findByClientUuid("cat-groceries")!!

        a.now += 60_000 // A, offline, budgets 300
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = groceries.id, currencyCode = "USD", limitMinor = 300_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-zzz"))
        b.now = a.now + 60_000 // B budgets 400 later and syncs first
        b.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = bGroceries.id, currencyCode = "USD", limitMinor = 400_00, createdAt = b.now, updatedAt = b.now, clientUuid = "b-aaa"))
        success(b.sync())
        a.now = b.now + 60_000
        val aReport = success(a.sync())
        b.now = a.now + 60_000
        success(b.sync())

        listOf(a, b).forEach { d ->
            val rows = d.budgets.rows.value.filter { it.categoryId != null }
            assertEquals("one row on each device", 1, rows.size)
            assertEquals(400_00L, rows.single().limitMinor) // the later edit
            assertEquals("b-aaa", rows.single().clientUuid) // the same row everywhere
        }
        // A's 300 was uploaded earlier in that same sync, so it is no longer an unsynced change: no notice (QA M5).
        assertEquals(0, aReport.overridden)
    }

    @Test
    fun `C-3 fold notice - only when the amount shown came from an unsynced edit, never for a 0 row`() = runTest {
        val a = Device()
        val food = a.addCategory("Food", clientUuid = "cat-food")
        // Budget shows the first stored row WITH an amount: 500 here, and the fold keeps 500 -> no notice.
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = food.id, currencyCode = "USD", limitMinor = 0, createdAt = a.now, updatedAt = a.now + 5, clientUuid = "b-1"))
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = food.id, currencyCode = "USD", limitMinor = 500_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-2"))
        assertEquals(0, success(a.sync()).overridden)

        fun row(id: Long, minor: Long, dirty: Boolean, category: Long = 7) = com.lucento.app.data.local.BudgetEntity(
            id = id, categoryId = category, currencyCode = "USD", limitMinor = minor, createdAt = 0, updatedAt = 0, dirty = dirty,
        )
        // Shown 300 (first stored, unsynced) and the fold keeps 900: this phone's unsynced amount was replaced.
        assertTrue(budgetFoldReplacesUnsynced(listOf(row(1, 300_00, dirty = true), row(2, 900_00, dirty = false)), setOf(7L), 900_00))
        // Already synced: not "an unsynced change of this phone".
        assertFalse(budgetFoldReplacesUnsynced(listOf(row(1, 300_00, dirty = false), row(2, 900_00, dirty = false)), setOf(7L), 900_00))
        // A 0 row is never what Budget showed; an archived category's budget isn't shown at all.
        assertFalse(budgetFoldReplacesUnsynced(listOf(row(1, 0, dirty = true), row(2, 500_00, dirty = true)), setOf(7L), 500_00))
        assertFalse(budgetFoldReplacesUnsynced(listOf(row(1, 300_00, dirty = true), row(2, 900_00, dirty = false)), emptySet(), 900_00))
    }

    @Test
    fun `P2-6 a never-synced row adopting a server row is not reported as replaced (QA M7)`() {
        assertFalse(overridesLocalChange(PullDecision.UPDATE, serverWins = false, localExists = true, localDirty = true, localBaseRevision = null))
        assertTrue(overridesLocalChange(PullDecision.UPDATE, serverWins = false, localExists = true, localDirty = true, localBaseRevision = 3))
        assertTrue(overridesLocalChange(PullDecision.INSERT, serverWins = true, localExists = false, localDirty = false, localBaseRevision = null))
    }

    @Test
    fun `C-3 the fold keeps the winning edit's own time, so a later fold still compares real edit times`() = runTest {
        val a = Device()
        val rent = a.addCategory("Rent", clientUuid = "cat-rent")
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = rent.id, currencyCode = "USD", limitMinor = 300_00, createdAt = a.now, updatedAt = 1_000, clientUuid = "a-keeper"))
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = rent.id, currencyCode = "USD", limitMinor = 900_00, createdAt = a.now, updatedAt = 2_000, clientUuid = "z-copy"))
        a.now = 9_999_999
        success(a.sync())
        val kept = a.budgets.rows.value.single { it.categoryId == rent.id }
        assertEquals("a-keeper", kept.clientUuid)
        assertEquals(900_00L, kept.limitMinor)
        assertEquals(2_000L, kept.updatedAt) // not the fold's clock
    }

    @Test
    fun `C-3 a set amount beats an unset one in the budget fold`() = runTest {
        val a = Device()
        val rent = a.addCategory("Rent", clientUuid = "cat-rent")
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = rent.id, currencyCode = "USD", limitMinor = 900_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-1"))
        a.now += 60_000
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = rent.id, currencyCode = "USD", limitMinor = 0, createdAt = a.now, updatedAt = a.now, clientUuid = "b-2"))
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = rent.id, currencyCode = "EUR", limitMinor = 50_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-3"))
        success(a.sync())

        val usd = a.budgets.rows.value.filter { it.currencyCode == "USD" }
        assertEquals(listOf(900_00L), usd.map { it.limitMinor })
        assertEquals(listOf(50_00L), a.budgets.rows.value.filter { it.currencyCode == "EUR" }.map { it.limitMinor }) // other currency untouched
    }

    @Test
    fun `budget lifecycle - a this-month change and a future change reach the other phone with their months`() = runTest {
        val a = Device()
        val food = a.addCategory("Food", clientUuid = "cat-food")
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = food.id, currencyCode = "USD", limitMinor = 500_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-usual"))
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = food.id, currencyCode = "USD", limitMinor = 650_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-nov", month = "2026-11"))
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = food.id, currencyCode = "USD", limitMinor = 700_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-dec", effectiveFrom = "2026-12"))
        success(a.sync())
        val b = Device(now = a.now + 60_000)
        success(b.sync())

        val rows = b.budgets.rows.value.associateBy { it.clientUuid }
        assertEquals(3, rows.size) // a month-only row and a recurring row are different budgets, never folded together
        assertEquals("2026-11" to null, rows.getValue("b-nov").month to rows.getValue("b-nov").effectiveFrom)
        assertEquals(null to "2026-12", rows.getValue("b-dec").month to rows.getValue("b-dec").effectiveFrom)
        assertEquals(null to null, rows.getValue("b-usual").month to rows.getValue("b-usual").effectiveFrom)
        val pushed = server.rows("budgets", user).associateBy { it.str("id") }
        assertEquals("2026-11", pushed.getValue("b-nov").str("month"))
        assertEquals("2026-12", pushed.getValue("b-dec").str("effective_from"))
    }

    @Test
    fun `budget lifecycle - two phones changing the same month offline end with one amount for that month`() = runTest {
        val a = Device()
        val food = a.addCategory("Food", clientUuid = "cat-food")
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = food.id, currencyCode = "USD", limitMinor = 500_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-usual"))
        success(a.sync())
        val b = Device(now = a.now)
        success(b.sync())
        val bFood = b.categories.findByClientUuid("cat-food")!!

        a.now += 60_000
        a.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = food.id, currencyCode = "USD", limitMinor = 600_00, createdAt = a.now, updatedAt = a.now, clientUuid = "b-zzz", month = "2026-11"))
        b.now = a.now + 60_000
        b.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = bFood.id, currencyCode = "USD", limitMinor = 650_00, createdAt = b.now, updatedAt = b.now, clientUuid = "b-aaa", month = "2026-11"))
        success(b.sync())
        a.now = b.now + 60_000
        success(a.sync())
        b.now = a.now + 60_000
        success(b.sync())

        listOf(a, b).forEach { d ->
            val nov = d.budgets.rows.value.filter { it.month == "2026-11" }
            assertEquals("one November amount on each device", 1, nov.size)
            assertEquals(650_00L, nov.single().limitMinor) // the later edit
            assertEquals(500_00L, d.budgets.rows.value.single { it.month == null }.limitMinor) // the usual amount untouched
        }
    }

    @Test
    fun `a pulled wallet with an institution id this build can't read keeps the wallet and drops only the logo`() = runTest {
        val a = Device()
        val w = a.addWallet("Everyday")
        success(a.sync())
        // Another client (or a future id format) writes an id outside the registry pattern.
        val stored = server.rows("wallets", user).single()
        server.tables.getValue("wallets")[stored.str("id")] = JsonObject(stored + ("institution_id" to JsonPrimitive("Bank-Of-Somewhere")))

        val b = Device(now = a.now + 60_000)
        success(b.sync())
        val pulled = b.wallets.findByClientUuid(w.clientUuid!!)
        assertNotNull("the wallet still arrives", pulled)
        assertNull(pulled!!.institutionId)
    }

    @Test
    fun `a category fold compares budget amounts within one scope only`() = runTest {
        // The copy has a newer every-month amount (600) and a November amount (100); the keeper has November 0.
        // November must end at 100 (a set amount beats 0), not take the every-month amount that was folded first.
        val phone = Device()
        val keep = phone.addCategory("Food", clientUuid = "a-food")
        phone.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = keep.id, currencyCode = "USD", limitMinor = 0, createdAt = phone.now, updatedAt = phone.now, clientUuid = "k-nov", month = "2026-11"))
        success(phone.sync())

        val other = Device(now = phone.now + 60_000)
        val copy = other.addCategory("food", clientUuid = "b-food")
        other.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = copy.id, currencyCode = "USD", limitMinor = 600_00, createdAt = other.now, updatedAt = other.now + 5, clientUuid = "c-every", effectiveFrom = "2026-11"))
        other.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = copy.id, currencyCode = "USD", limitMinor = 100_00, createdAt = other.now, updatedAt = other.now, clientUuid = "c-nov", month = "2026-11"))
        success(other.sync())
        success(phone.sync())

        listOf(phone, other).forEach { d ->
            val food = d.categories.findByClientUuid("a-food")!!
            val rows = d.budgets.rows.value.filter { it.categoryId == food.id }
            assertEquals(100_00L, rows.single { it.month == "2026-11" }.limitMinor)
            assertEquals(600_00L, rows.single { it.effectiveFrom == "2026-11" }.limitMinor)
        }
    }

    @Test
    fun `a wallet's institution reaches the other phone, and clearing it syncs too`() = runTest {
        val a = Device()
        val w = a.addWallet("Everyday")
        a.wallets.update(w.copy(institutionId = "ph_bdo", updatedAt = a.now + 1))
        success(a.sync())
        assertEquals("ph_bdo", server.rows("wallets", user).single().str("institution_id"))
        val b = Device(now = a.now + 60_000)
        success(b.sync())
        assertEquals("ph_bdo", b.wallets.findByClientUuid(w.clientUuid!!)!!.institutionId)

        a.now += 120_000
        a.wallets.update(a.wallets.getById(w.id)!!.copy(institutionId = null, updatedAt = a.now, dirty = true))
        success(a.sync())
        b.now = a.now + 60_000
        success(b.sync())
        assertNull(b.wallets.findByClientUuid(w.clientUuid!!)!!.institutionId)
    }

    /** An account set up on an older phone: Rent (budgeted) and Water, plus a wallet. */
    private suspend fun existingAccount(): Device {
        val old = Device()
        val rent = old.addCategory("Rent")
        old.addCategory("Water")
        val cash = old.addWallet()
        old.addTransaction(cash.id, 900_00, categoryId = rent.id)
        success(old.sync())
        return old
    }

    @Test
    fun `R6-14 first sign-in drops an unused local category the account does not have`() = runTest {
        existingAccount()
        val fresh = Device()
        fresh.addCategory("Streaming services") // a preset this phone picked and never used
        success(fresh.sync())

        assertTrue(server.live("categories", user).none { it.str("name") == "Streaming services" })
        assertTrue(fresh.categories.getAll().none { it.name == "Streaming services" })
        assertEquals(setOf("Rent", "Water"), fresh.categories.getAll().map { it.name }.toSet())
    }

    @Test
    fun `R6-14 first sign-in keeps a used local category and uploads it`() = runTest {
        existingAccount()
        val fresh = Device()
        val gym = fresh.addCategory("Gym")
        val wallet = fresh.addWallet("Wallet")
        fresh.addTransaction(wallet.id, 50_00, categoryId = gym.id)
        val books = fresh.addCategory("Books")
        fresh.budgets.insert(com.lucento.app.data.local.BudgetEntity(categoryId = books.id, currencyCode = "USD", limitMinor = 0, createdAt = fresh.now, updatedAt = fresh.now, clientUuid = UUID.randomUUID().toString()))
        success(fresh.sync())

        val names = server.live("categories", user).map { it.str("name") }.toSet()
        assertTrue("Gym" in names)
        assertTrue("Books" in names) // a budget is a reference too
        assertEquals("Gym", fresh.categories.getById(fresh.transactions.rows.value.first { it.amountMinor == 50_00L }.categoryId!!)!!.name)
    }

    @Test
    fun `R6-14 a matching name merges into the account's copy, unused or used`() = runTest {
        val old = existingAccount()
        val accountRent = server.live("categories", user).single { it.str("name") == "Rent" }.str("id")
        val fresh = Device()
        fresh.addCategory("rent ") // unused copy: the account's Rent stays, no second row
        val water = fresh.addCategory("Water")
        val wallet = fresh.addWallet("Wallet")
        fresh.addTransaction(wallet.id, 30_00, categoryId = water.id) // used copy: folded, spending kept
        success(fresh.sync())
        success(old.sync())

        val live = server.live("categories", user)
        assertEquals(1, live.count { it.str("name").trim().equals("rent", ignoreCase = true) })
        assertEquals(accountRent, live.single { it.str("name").trim().equals("rent", ignoreCase = true) }.str("id"))
        // The used copy folds into one Water on this phone. (The fake DAOs record no tombstone on delete, which the
        // Room trigger does on a device, so the server side of that fold is covered by the existing fold test.)
        assertEquals(1, fresh.categories.getAll().count { it.name == "Water" })
        val tx = fresh.transactions.rows.value.first { it.amountMinor == 30_00L }
        assertEquals("Water", fresh.categories.getById(tx.categoryId!!)!!.name)
    }

    @Test
    fun `R6-14 a first sign-in into an empty account uploads everything as before`() = runTest {
        val fresh = Device()
        fresh.addCategory("Streaming services")
        success(fresh.sync())
        assertTrue(server.live("categories", user).any { it.str("name") == "Streaming services" })
    }

    @Test
    fun `R9-03 an account with no income categories keeps this phone's income defaults and uploads them`() = runTest {
        existingAccount() // expense categories only
        val fresh = Device()
        fresh.addCategory("Streaming services") // expense: the account has expense categories, so it is dropped
        fresh.addCategory("Salary", kind = "INCOME")
        fresh.addCategory("Freelance", kind = "INCOME")
        success(fresh.sync())

        val live = server.live("categories", user).map { it.str("name") }.toSet()
        assertTrue("Salary" in live)
        assertTrue("Freelance" in live)
        assertTrue("Streaming services" !in live)
        assertEquals(setOf("Rent", "Water", "Salary", "Freelance"), fresh.categories.getAll().map { it.name }.toSet())
    }

    @Test
    fun `R9-03 an account that has income categories still drops unused local income ones`() = runTest {
        val old = Device()
        old.addCategory("Rent")
        old.addCategory("Salary", kind = "INCOME")
        old.addWallet()
        success(old.sync())
        val fresh = Device()
        // Lucento's own defaults/presets, never used. (A name the user typed is kept since P0-2 — see
        // OnboardingDataSafetyTest.)
        fresh.addCategory("Investments", kind = "INCOME")
        fresh.addCategory("Gifts and celebrations", kind = "EXPENSE")
        success(fresh.sync())

        assertEquals(setOf("Rent", "Salary"), fresh.categories.getAll().map { it.name }.toSet())
        assertEquals(setOf("Rent", "Salary"), server.live("categories", user).map { it.str("name") }.toSet())
    }

    @Test
    fun `R9-03 an account with no categories of any type keeps every local default`() = runTest {
        val old = Device()
        old.addWallet() // data, but no categories at all
        success(old.sync())
        val fresh = Device()
        fresh.addCategory("Groceries")
        fresh.addCategory("Salary", kind = "INCOME")
        success(fresh.sync())

        assertEquals(setOf("Groceries", "Salary"), server.live("categories", user).map { it.str("name") }.toSet())
    }

    @Test
    fun `R9-03 drop rule keeps a type the account lacks`() {
        assertFalse(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 0, schedules = 0, accountHasKind = false))
        assertTrue(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 0, schedules = 0, accountHasKind = true))
    }

    @Test
    fun `R6-14 drop rule`() {
        assertTrue(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 0, schedules = 0))
        assertFalse(FirstSignInCategories.drop(hasServerRow = true, transactions = 0, budgets = 0, schedules = 0))
        assertFalse(FirstSignInCategories.drop(hasServerRow = false, transactions = 1, budgets = 0, schedules = 0))
        assertFalse(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 1, schedules = 0))
        assertFalse(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 0, schedules = 1))
    }

    @Test
    fun `first sign-in uploads existing local data, and retrying never duplicates it`() = runTest {
        val phone = Device()
        val cash = phone.addWallet()
        repeat(3) { phone.addTransaction(cash.id, 100L * (it + 1)) }

        success(phone.sync())
        success(phone.sync())
        success(phone.sync())

        assertEquals(1, server.live("wallets", user).size)
        assertEquals(3, server.live("transactions", user).size)
        assertTrue(phone.transactions.rows.value.none { it.dirty })
        assertEquals(user, phone.prefs.syncedUser)
    }

    @Test
    fun `a second device receives everything with its references remapped to its own ids`() = runTest {
        val a = Device()
        val cash = a.addWallet("Everyday")
        val food = a.addCategory("Food")
        a.addTransaction(cash.id, 4_550, food.id, "Lunch")
        success(a.sync())

        val b = Device()
        b.addWallet("Unrelated local wallet") // b's own ids differ from a's
        val report = success(b.sync())

        assertEquals(3, report.pulled)
        val pulled = b.transactions.rows.value.single()
        assertEquals(4_550L, pulled.amountMinor)
        assertEquals("Everyday", b.wallets.getById(pulled.walletId)!!.name)
        assertEquals("Food", b.categories.getById(pulled.categoryId!!)!!.name)
        assertTrue(!pulled.dirty)
    }

    @Test
    fun `a backlog larger than one page arrives completely`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        repeat(1_234) { a.addTransaction(cash.id, it + 1L) }
        success(a.sync())

        val b = Device(pageSize = 500)
        success(b.sync())

        assertEquals(1_234, b.transactions.rows.value.size)
        assertEquals((1..1_234L).sum(), b.transactions.rows.value.sumOf { it.amountMinor })
    }

    @Test
    fun `a device whose clock is hours behind still has its new rows pulled by others`() = runTest {
        val a = Device(now = 1_000_000_000_000L)
        val cash = a.addWallet()
        success(a.sync())
        val b = Device(now = 1_000_000_000_000L)
        success(b.sync())

        a.now -= 6 * 60 * 60 * 1000 // a's clock jumps six hours into the past
        a.addTransaction(cash.id, 999)
        success(a.sync())
        success(b.sync())

        assertEquals(listOf(999L), b.transactions.rows.value.map { it.amountMinor })
    }

    @Test
    fun `a clean row accepts the server's edit even when it carries an older timestamp`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        val t = a.addTransaction(cash.id, 100, note = "first")
        success(a.sync())
        val b = Device(now = a.now + 60 * 60 * 1000) // b's clock runs an hour ahead
        success(b.sync())
        // b edits with its fast clock; the server clamps nothing (within 5 min of server time is not guaranteed here),
        // then a, with a normal clock, edits later in real time.
        b.editNote(b.transactions.rows.value.single(), "b's edit")
        success(b.sync())
        success(a.sync())
        a.now += 1_000
        a.editNote(a.transactions.getById(t.id)!!, "a's later edit")
        success(a.sync())
        success(b.sync())

        // The server kept whichever write it accepted; both devices converge on the server's copy.
        val serverNote = server.live("transactions", user).single().str("note")
        assertEquals(serverNote, b.transactions.rows.value.single().note)
        assertEquals(serverNote, a.transactions.getById(t.id)!!.note)
    }

    @Test
    fun `an unsynced local edit newer than the server copy is kept, then wins on the next push`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        val t = a.addTransaction(cash.id, 100, note = "original")
        success(a.sync())
        val b = Device()
        success(b.sync())

        a.now += 1_000
        a.editNote(a.transactions.getById(t.id)!!, "older edit on a")
        success(a.sync())
        b.now += 5_000
        b.editNote(b.transactions.rows.value.single(), "newer edit on b, offline")
        val report = success(b.sync()) // push first: the server accepts b's newer edit

        assertEquals("newer edit on b, offline", server.live("transactions", user).single().str("note"))
        assertEquals("newer edit on b, offline", b.transactions.rows.value.single().note)
        assertEquals(0, report.conflicts)
        success(a.sync())
        assertEquals("newer edit on b, offline", a.transactions.getById(t.id)!!.note)
    }

    @Test
    fun `a stale push never overwrites a newer server row`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        val t = a.addTransaction(cash.id, 100, note = "v1")
        success(a.sync())
        val b = Device()
        success(b.sync())
        b.now += 10_000
        b.editNote(b.transactions.rows.value.single(), "v2 from b")
        success(b.sync())

        a.now += 1_000 // a edits offline, earlier than b's edit
        a.editNote(a.transactions.getById(t.id)!!, "v1.5 from a")
        success(a.sync())

        assertEquals("v2 from b", server.live("transactions", user).single().str("note"))
        assertEquals("v2 from b", a.transactions.getById(t.id)!!.note)
    }

    @Test
    fun `deleting a wallet and its transactions elsewhere is applied here children-first without failing`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        val t1 = a.addTransaction(cash.id, 100)
        val t2 = a.addTransaction(cash.id, 200)
        success(a.sync())
        val b = Device()
        success(b.sync())
        assertEquals(2, b.transactions.rows.value.size)

        a.now += 1_000
        a.deleteTransaction(t1)
        a.deleteTransaction(t2)
        a.deleteWallet(cash)
        success(a.sync())
        val report = success(b.sync())

        assertTrue(b.wallets.rows.value.isEmpty())
        assertTrue(b.transactions.rows.value.isEmpty())
        assertEquals(3, report.deletedLocally)
        assertEquals(0, server.live("wallets", user).size)
    }

    @Test
    fun `a remote wallet delete that a new local transaction still depends on is kept, not fatal`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        success(a.sync())
        val b = Device()
        success(b.sync())

        a.now += 1_000
        a.deleteWallet(cash)
        success(a.sync())
        b.now += 2_000
        b.addTransaction(b.wallets.rows.value.single().id, 777) // b uses the wallet before hearing of the delete
        val report = success(b.sync())

        assertEquals(1, b.wallets.rows.value.size)
        assertEquals(listOf(777L), b.transactions.rows.value.map { it.amountMinor })
        assertEquals(1, report.conflicts)
    }

    @Test
    fun `a deleted transaction does not come back on another device or after a resync`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        val t = a.addTransaction(cash.id, 100)
        success(a.sync())
        val b = Device()
        success(b.sync())

        a.now += 1_000
        a.deleteTransaction(t)
        success(a.sync())
        success(b.sync())
        success(a.sync())
        success(b.sync())

        assertTrue(a.transactions.rows.value.isEmpty())
        assertTrue(b.transactions.rows.value.isEmpty())
    }

    @Test
    fun `a deleted goal contribution is removed on other devices too`() = runTest {
        val a = Device()
        val goalId = a.goals.insert(
            GoalEntity(name = "Trip", targetMinor = 10_000, currencyCode = "USD", targetDate = null, walletId = null, createdAt = a.now, updatedAt = a.now, clientUuid = "goal-1")
        )
        val contributionId = a.goals.insertContribution(
            GoalContributionEntity(goalId = goalId, amountMinor = 2_500, occurredOn = 20_000, note = "", clientUuid = "c-1", createdAt = a.now)
        )
        success(a.sync())
        val b = Device()
        success(b.sync())
        assertEquals(2_500L, b.goals.contributions.value.single().amountMinor)

        val pushed = a.goals.contributions.value.single { it.id == contributionId }
        a.goals.deleteContribution(contributionId)
        a.tombstones.insert(SyncTombstoneEntity(tableName = "goal_contributions", clientUuid = "c-1", deletedAt = a.now, baseRevision = pushed.baseRevision))
        success(a.sync())
        success(b.sync())

        assertTrue(b.goals.contributions.value.isEmpty())
    }

    @Test
    fun `fresh-install seed categories get a stable id, keep their transactions categorised, and merge across devices`() = runTest {
        val a = Device()
        val salary = a.addCategory("Salary", clientUuid = null, kind = "INCOME") // as CategorySeed inserts it
        val cash = a.addWallet()
        a.addTransaction(cash.id, 300_000, salary.id)
        success(a.sync())

        val b = Device()
        b.addCategory("Salary", clientUuid = null, kind = "INCOME") // b's own fresh-install seed
        success(b.sync())

        assertEquals(1, server.live("categories", user).size)
        assertEquals(1, b.categories.rows.value.size)
        val pulledTx = b.transactions.rows.value.single()
        assertEquals("Salary", b.categories.getById(pulledTx.categoryId!!)!!.name)
    }

    @Test
    fun `a different account on the same device is refused and nothing is pushed or pulled`() = runTest {
        val phone = Device()
        val cash = phone.addWallet()
        phone.addTransaction(cash.id, 100)
        success(phone.sync())

        phone.userId = "22222222-2222-4222-8222-222222222222"
        phone.addTransaction(cash.id, 200)
        val result = phone.sync()

        assertEquals(SyncResult.AccountMismatch, result)
        assertTrue(server.rows("transactions", "22222222-2222-4222-8222-222222222222").isEmpty())
        assertEquals(2, phone.transactions.rows.value.size) // nothing local touched
    }

    @Test
    fun `a network failure mid-sync loses nothing and the next attempt completes`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        a.addTransaction(cash.id, 100)
        success(a.sync())
        val b = Device()
        b.prefs.pullCursor = null
        server.failNextFetch = true

        val failed = b.sync()
        assertTrue(failed is SyncResult.Retryable)
        // The stored, user-visible error is a safe description, never the raw exception text (which can carry headers).
        assertTrue(b.prefs.lastError != null && !b.prefs.lastError!!.contains("connection reset"))
        assertNull(b.prefs.pullCursor) // cursor not advanced past anything
        success(b.sync())
        assertEquals(1, b.transactions.rows.value.size)
    }

    @Test
    fun `signed out or unconfigured, sync touches nothing`() = runTest {
        val phone = Device(userId = null)
        phone.addTransaction(phone.addWallet().id, 100)
        assertEquals(SyncResult.NotSignedIn, phone.sync())
        assertTrue(server.tables.isEmpty())
        assertTrue(phone.transactions.rows.value.single().dirty)
    }

    @Test
    fun `sync now after an offline sign-out requires sign-in and downloads nothing`() = runTest {
        val source = Device()
        val cash = source.addWallet()
        source.addTransaction(cash.id, 42_00)
        success(source.sync())
        assertEquals(1, server.live("transactions", user).size)

        val phone = Device()
        var token: String? = "access-token"
        var downloads = 0
        endLocalSession(
            signOutLocal = { error("offline") },
            clearSession = { token = null },
        )
        assertNull(token)

        val real = server.remote(user)
        val remote = object : SyncRemote by real {
            override suspend fun currentUserId(): String? = if (token == null) null else user
            override suspend fun fetchByIds(table: String, userId: String, ids: List<String>): List<JsonObject> {
                downloads += 1
                return real.fetchByIds(table, userId, ids)
            }
            override suspend fun fetchPage(
                table: String,
                userId: String,
                sinceIso: String,
                after: SyncRemote.PageKey?,
                limit: Long,
            ): List<JsonObject> {
                downloads += 1
                return real.fetchPage(table, userId, sinceIso, after, limit)
            }
        }
        val result = SyncRepository(
            remote,
            phone.wallets,
            phone.categories,
            phone.transactions,
            phone.budgets,
            phone.goals,
            phone.tombstones,
            phone.prefs,
            recurring = phone.recurring,
            debtTerms = phone.debtTerms,
            clock = { phone.now },
        ).sync()

        assertEquals(SyncResult.NotSignedIn, result)
        assertEquals(0, downloads)
        assertEquals(0, phone.transactions.rows.value.size)
        assertEquals(1, server.live("transactions", user).size)
    }

    // --- causal conflict resolution (server revisions) ---

    @Test
    fun `an edit made after syncing wins even from a device whose clock is an hour slow`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        val t = a.addTransaction(cash.id, 100, note = "original")
        success(a.sync())
        val b = Device()
        success(b.sync())
        b.now += 10 * 60_000
        b.editNote(b.transactions.rows.value.single(), "b's edit at 10:10")
        success(b.sync())

        success(a.sync()) // a sees b's edit first...
        assertEquals("b's edit at 10:10", a.transactions.getById(t.id)!!.note)
        a.now -= 60 * 60_000 // ...then edits it with a clock an hour slow
        a.editNote(a.transactions.getById(t.id)!!, "a's later edit, slow clock")
        val report = success(a.sync())
        success(b.sync())

        // Time-only last-write-wins silently dropped this edit; causally it is the newest version.
        assertEquals("a's later edit, slow clock", server.live("transactions", user).single().str("note"))
        assertEquals("a's later edit, slow clock", b.transactions.rows.value.single().note)
        assertEquals(0, report.conflicts)
    }

    @Test
    fun `truly concurrent edits resolve to the later edit on every device, in either push order`() = runTest {
        for (laterPushesFirst in listOf(false, true)) {
            val srv = FakeSyncServer()
            fun device() = Device().also { it.serverOverride = srv }
            val a = device()
            val cash = a.addWallet()
            a.addTransaction(cash.id, 100, note = "base")
            success(a.sync())
            val b = device()
            success(b.sync())

            a.now += 1_000
            a.editNote(a.transactions.rows.value.single(), "a, earlier")
            b.now += 5_000
            b.editNote(b.transactions.rows.value.single(), "b, later")
            if (laterPushesFirst) { success(b.sync()); success(a.sync()) } else { success(a.sync()); success(b.sync()) }
            success(a.sync())
            success(b.sync())

            assertEquals("b, later", srv.live("transactions", user).single().str("note"))
            assertEquals("b, later", a.transactions.rows.value.single().note)
            assertEquals("b, later", b.transactions.rows.value.single().note)
            assertTrue(a.transactions.rows.value.single().let { !it.dirty })
        }
    }

    @Test
    fun `a delete that lost to a newer concurrent edit brings the edited row back`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        val t = a.addTransaction(cash.id, 100, note = "base")
        success(a.sync())
        val b = Device()
        success(b.sync())

        a.now += 1_000
        a.deleteTransaction(t) // offline delete
        b.now += 5_000
        b.editNote(b.transactions.rows.value.single(), "edited after the delete")
        success(b.sync())
        val report = success(a.sync())

        assertEquals("edited after the delete", a.transactions.rows.value.single().note)
        assertEquals("edited after the delete", server.live("transactions", user).single().str("note"))
        assertTrue(report.conflicts >= 1)
    }

    @Test
    fun `a row updated by another device while pages are being read is not skipped`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        repeat(1_200) { a.addTransaction(cash.id, it + 1L) }
        success(a.sync())
        val firstRowId = server.live("transactions", user).minByOrNull { it.str("server_updated_at") }!!.str("id")
        // While device b reads the second page of transactions, device a's earlier write lands on an early row,
        // moving it to the end of the order. With offset paging the following rows shifted and one was skipped.
        var transactionPages = 0
        server.beforePage = { table, _ ->
            if (table == "transactions" && ++transactionPages == 2) {
                val row = server.tables.getValue("transactions").getValue(firstRowId)
                server.write("transactions", user, JsonObject(row + mapOf(
                    "note" to JsonPrimitive("moved"),
                    "updated_at" to JsonPrimitive(SyncTime.toIso(a.now + 1)),
                    "base_revision" to row.getValue("revision"),
                )))
            }
        }

        val b = Device(pageSize = 500)
        success(b.sync())

        assertEquals(1_200, b.transactions.rows.value.size)
        assertEquals((1..1_200L).sum(), b.transactions.rows.value.sumOf { it.amountMinor })
        assertEquals("moved", b.transactions.findByClientUuid(firstRowId)!!.note)
    }

    @Test
    fun `a push whose response was lost is retried without a duplicate or a lost edit`() = runTest {
        val phone = Device()
        val cash = phone.addWallet()
        phone.addTransaction(cash.id, 100, note = "only once")
        phone.dropNextUpsertResponse = true // the server applies the write; the reply never arrives

        assertTrue(phone.sync() is SyncResult.Retryable)
        assertTrue(phone.transactions.rows.value.single().dirty) // not knowing, it keeps the edit pending
        success(phone.sync())

        assertEquals(1, server.live("transactions", user).size)
        val local = phone.transactions.rows.value.single()
        assertEquals("only once", local.note)
        assertTrue(!local.dirty)
        assertEquals(server.live("transactions", user).single().str("revision").toLong(), local.baseRevision)
    }

    // --- v9: recurring schedules and debt terms ---

    @Test
    fun `a debt payment schedule and the loan's terms reach a second device intact`() = runTest {
        val a = Device()
        val bank = a.addWallet("Bank")
        val loanId = a.wallets.insert(
            WalletEntity(name = "Car loan", type = "LOAN", currencyCode = "USD", openingBalanceMinor = 500_000, createdAt = a.now, updatedAt = a.now, clientUuid = UUID.randomUUID().toString())
        )
        a.debtTerms.upsert(DebtTermsEntity(loanId, interestRateBps = 1250, repayment = "MONTHLY", termMonths = 24, installmentMinor = 23_654, updatedAt = a.now))
        val interest = a.addCategory("Debt interest")
        a.recurring.insert(
            RecurringEntity(
                name = "Monthly Payment - Car loan", type = "EXPENSE", amountMinor = 23_654, currencyCode = "USD", walletId = bank.id,
                categoryId = interest.id, frequency = "MONTHLY", intervalCount = 1, startOn = 20_000, endOn = 20_700, nextIndex = 2,
                paused = false, createdAt = a.now, updatedAt = a.now, toWalletId = loanId, clientUuid = "sched-1",
            )
        )
        assertTrue(a.sync() is SyncResult.Success)
        assertTrue(a.recurring.rows.value.none { it.dirty })

        val b = Device(now = a.now + 1_000)
        assertTrue(b.sync() is SyncResult.Success)
        val schedule = b.recurring.rows.value.single()
        val bLoan = b.wallets.findByClientUuid(a.wallets.getById(loanId)!!.clientUuid!!)!!
        assertEquals("sched-1", schedule.clientUuid)
        assertEquals(b.wallets.findByClientUuid(bank.clientUuid!!)!!.id, schedule.walletId)
        assertEquals(bLoan.id, schedule.toWalletId)
        assertEquals(b.categories.findByClientUuid(interest.clientUuid!!)!!.id, schedule.categoryId)
        assertEquals(2, schedule.nextIndex)
        assertEquals(false, schedule.dirty)
        val terms = b.debtTerms.get(bLoan.id)!!
        assertEquals(1250, terms.interestRateBps)
        assertEquals("MONTHLY", terms.repayment)
        assertEquals(24, terms.termMonths)
        assertEquals(23_654L, terms.installmentMinor)
    }

    @Test
    fun `marking a payment paid on one device advances the schedule on the other, and deletes travel`() = runTest {
        val a = Device()
        val bank = a.addWallet("Bank")
        val id = a.recurring.insert(
            RecurringEntity(
                name = "Rent", type = "EXPENSE", amountMinor = 120_000, currencyCode = "USD", walletId = bank.id, categoryId = null,
                frequency = "MONTHLY", intervalCount = 1, startOn = 20_000, endOn = null, nextIndex = 0, paused = false,
                createdAt = a.now, updatedAt = a.now, clientUuid = "rent",
            )
        )
        a.sync()
        val b = Device(now = a.now + 1_000)
        b.sync()

        // A marks it paid: next_index moves on and updated_at changes, so the trigger marks it dirty.
        a.now += 5_000
        a.recurring.update(a.recurring.getById(id)!!.copy(nextIndex = 1, updatedAt = a.now, dirty = true))
        a.sync()
        b.now += 10_000
        b.sync()
        assertEquals(1, b.recurring.rows.value.single().nextIndex)

        // B deletes it; A drops it on the next sync.
        val onB = b.recurring.rows.value.single()
        b.recurring.deleteById(onB.id)
        b.tombstones.insert(SyncTombstoneEntity(tableName = "recurring", clientUuid = "rent", deletedAt = b.now, baseRevision = onB.baseRevision))
        b.sync()
        a.now += 20_000
        a.sync()
        assertTrue(a.recurring.rows.value.isEmpty())
    }

    @Test
    fun `clearing a loan's terms syncs as no terms`() = runTest {
        val a = Device()
        val loanId = a.wallets.insert(
            WalletEntity(name = "Loan", type = "LOAN", currencyCode = "USD", openingBalanceMinor = 100_000, createdAt = a.now, updatedAt = a.now, clientUuid = "loan")
        )
        a.debtTerms.upsert(DebtTermsEntity(loanId, interestRateBps = 900, repayment = "ONE_TIME", termMonths = null, installmentMinor = null, updatedAt = a.now))
        a.sync()
        val b = Device(now = a.now + 1_000)
        b.sync()
        assertEquals(900, b.debtTerms.get(b.wallets.findByClientUuid("loan")!!.id)!!.interestRateBps)

        // The terms are removed on A; WalletRepository bumps the wallet, which goes out without them.
        a.now += 5_000
        a.debtTerms.delete(loanId)
        a.wallets.update(a.wallets.getById(loanId)!!.copy(updatedAt = a.now, dirty = true))
        a.sync()
        b.now += 10_000
        b.sync()
        assertNull(b.debtTerms.get(b.wallets.findByClientUuid("loan")!!.id))
    }

    @Test
    fun `a pending local edit made at the same moment is not overwritten`() = runTest {
        val a = Device()
        val cash = a.addWallet()
        a.addTransaction(cash.id, 100, note = "base")
        success(a.sync())
        val b = Device()
        success(b.sync())

        val same = a.now + 5_000
        a.now = same
        b.now = same
        a.editNote(a.transactions.rows.value.single(), "from a")
        b.editNote(b.transactions.rows.value.single(), "from b")
        success(b.sync())
        success(a.sync())

        assertEquals("from a", a.transactions.rows.value.single().note)
        assertEquals("from a", server.live("transactions", user).single().str("note"))
        assertFalse(a.transactions.rows.value.single().dirty)
        success(b.sync())
        assertEquals("from a", b.transactions.rows.value.single().note)
    }

    @Test
    fun `a transaction whose wallet or category was deleted is kept and does not stall the cursor`() = runTest {
        val a = Device()
        val cash = a.addWallet("Cash")
        val food = a.addCategory("Food")
        success(a.sync())
        val b = Device()
        success(b.sync())

        a.now += 1_000
        a.deleteWallet(cash)
        val foodRow = a.categories.getById(food.id)!!
        a.categories.deleteById(food.id)
        a.tombstones.insert(
            SyncTombstoneEntity(tableName = "categories", clientUuid = food.clientUuid!!, deletedAt = a.now, baseRevision = foodRow.baseRevision),
        )
        success(a.sync())

        // The deletions are already behind the cursor, so this pull will not see the parent rows themselves.
        val deletedThrough = maxOf(
            SyncTime.fromIso(server.rows("wallets", user).single().str("server_updated_at")),
            SyncTime.fromIso(server.rows("categories", user).single { it.str("name") == "Food" }.str("server_updated_at")),
        )
        a.prefs.pullCursor = SyncTime.toIso(deletedThrough + 1)

        b.now += 2_000
        b.addTransaction(
            b.wallets.rows.value.single { it.name == "Cash" }.id,
            4242,
            b.categories.rows.value.single { it.name == "Food" }.id,
            note = "offline lunch",
        )
        success(b.sync())
        // A later row, far enough past the transaction that a deferred cursor would stay on the transaction.
        server.serverNow += 120_000
        b.now += 3_000
        b.addWallet("Savings")
        success(b.sync())

        val report = success(a.sync())
        assertEquals("offline lunch", a.transactions.rows.value.single().note)
        assertEquals(4242L, a.transactions.rows.value.single().amountMinor)
        assertEquals(SyncRepository.RESTORED_ACCOUNT_NAME, a.wallets.rows.value.single { it.clientUuid == cash.clientUuid }.name)
        assertEquals(SyncRepository.UNCATEGORIZED_NAME, a.categories.getById(a.transactions.rows.value.single().categoryId!!)!!.name)
        assertTrue(report.parentsRestored >= 2)
        assertTrue(a.wallets.rows.value.any { it.name == "Savings" })
        val cursor = SyncTime.fromIso(a.prefs.pullCursor!!)
        val transactionAt = SyncTime.fromIso(server.live("transactions", user).single().str("server_updated_at"))
        assertTrue("cursor $cursor stayed on the transaction at $transactionAt", cursor > transactionAt)

        success(a.sync())
        assertEquals(1, a.transactions.rows.value.size)

        val fresh = Device()
        success(fresh.sync())
        assertEquals("offline lunch", fresh.transactions.rows.value.single().note)
        assertEquals("Cash", fresh.wallets.rows.value.single { it.clientUuid == cash.clientUuid }.name)
        assertEquals("Food", fresh.categories.getById(fresh.transactions.rows.value.single().categoryId!!)!!.name)
    }
}
