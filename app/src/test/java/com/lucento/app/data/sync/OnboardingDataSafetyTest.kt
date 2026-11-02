package com.lucento.app.data.sync

import com.lucento.app.data.local.BudgetEntity
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.data.local.GoalEntity
import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.local.SyncTombstoneEntity
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.FakeBudgetDao
import com.lucento.app.data.repository.FakeCategoryDao
import com.lucento.app.data.repository.FakeDebtTermsDao
import com.lucento.app.data.repository.FakeGoalDao
import com.lucento.app.data.repository.FakeRecurringDao
import com.lucento.app.data.repository.FakeSyncDao
import com.lucento.app.data.repository.FakeTransactionDao
import com.lucento.app.data.repository.FakeWalletDao
import com.lucento.app.domain.CategoryPresets
import com.lucento.app.domain.CategorySelection
import com.lucento.app.feature.onboarding.SetupRoute
import com.lucento.app.feature.onboarding.setupRoute
import com.lucento.app.feature.settings.FirstSync
import com.lucento.app.feature.settings.firstSyncDecision
import com.lucento.app.feature.settings.syncAfterAccountWipe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * P0-2: onboarding and first sign-in never duplicate, replace or lose financial records. Each test checks record counts
 * and ids on the device and on [FakeSyncServer], not screen state. Covers the account check that decides between a new
 * setup and a restore, the first sync's pull-first branch (including a retry after it failed half-way), the narrowed
 * first-sign-in category rule, and onboarding's category selection on top of account data.
 */
class OnboardingDataSafetyTest {

    private val user = "22222222-2222-4222-8222-222222222222"
    private val otherUser = "33333333-3333-4333-8333-333333333333"
    private val server = FakeSyncServer()

    private inner class Device(var userId: String? = user, var now: Long = 1_000_000_000_000L) {
        var wallets = FakeWalletDao()
        var categories = FakeCategoryDao()
        var transactions = FakeTransactionDao()
        var budgets = FakeBudgetDao()
        var goals = FakeGoalDao()
        var recurring = FakeRecurringDao()
        var debtTerms = FakeDebtTermsDao()
        var tombstones = FakeSyncDao()
        val prefs = FakeSyncPreferences(enabled = true)
        /** Wraps the server, e.g. to fail a request. */
        var wrap: (SyncRemote) -> SyncRemote = { it }

        init {
            link()
        }

        private fun link() {
            wallets.transactions = transactions
            categories.transactions = transactions
            categories.budgets = budgets
        }

        /** The sign-out wipe in its production order ([com.lucento.app.data.local.runWipe]): binding, tables, the rest. */
        suspend fun signOutWipe(resetSetup: () -> Unit = {}) {
            val marker = object : com.lucento.app.data.local.WipeMarker {
                var value = false
                override fun set() = true.also { value = true }
                override fun clear() = true.also { value = false }
                override fun isSet() = value
            }
            com.lucento.app.data.local.runWipe(
                marker,
                clearBinding = { prefs.syncedUser = null; prefs.initialPullDone = null; prefs.pullCursor = null },
                clearTables = {
                    wallets = FakeWalletDao(); categories = FakeCategoryDao(); transactions = FakeTransactionDao()
                    budgets = FakeBudgetDao(); goals = FakeGoalDao(); recurring = FakeRecurringDao()
                    debtTerms = FakeDebtTermsDao(); tombstones = FakeSyncDao()
                    link()
                },
                clearRest = { resetSetup() },
            )
            assertFalse(marker.isSet())
            userId = null
        }

        fun repository(
            remote: SyncRemote? = wrap(server.remote(userId)),
            transact: suspend (suspend () -> Unit) -> Unit = { it() },
        ) = SyncRepository(
            remote, wallets, categories, transactions, budgets, goals, tombstones, prefs,
            recurring = recurring, debtTerms = debtTerms, clock = { now }, transact = transact,
        )

        suspend fun sync(): SyncResult = repository().sync()
        suspend fun probe(): AccountProbe = repository().probeAccount()

        suspend fun addWallet(name: String = "Cash"): WalletEntity {
            val id = wallets.insert(WalletEntity(name = name, type = "CASH", currencyCode = "USD", openingBalanceMinor = 10_000, createdAt = now, updatedAt = now, clientUuid = UUID.randomUUID().toString()))
            return wallets.getById(id)!!
        }

        suspend fun addCategory(name: String, kind: String = "EXPENSE"): CategoryEntity {
            val id = categories.insert(CategoryEntity(name = name, kind = kind, iconKey = "other", sortOrder = 0, createdAt = now, updatedAt = 0, clientUuid = UUID.randomUUID().toString()))
            return categories.getById(id)!!
        }

        suspend fun addTransaction(walletId: Long, amountMinor: Long, categoryId: Long? = null): TransactionEntity {
            val id = transactions.insert(TransactionEntity(type = "EXPENSE", amountMinor = amountMinor, currencyCode = "USD", walletId = walletId, categoryId = categoryId, occurredOn = 20_000, note = "", clientUuid = UUID.randomUUID().toString(), createdAt = now, updatedAt = now))
            return transactions.getById(id)!!
        }

        suspend fun addTransfer(fromId: Long, toId: Long, amountMinor: Long) {
            val group = UUID.randomUUID().toString()
            listOf("TRANSFER_OUT" to fromId, "TRANSFER_IN" to toId).forEach { (type, walletId) ->
                transactions.insert(TransactionEntity(type = type, amountMinor = amountMinor, currencyCode = "USD", walletId = walletId, categoryId = null, occurredOn = 20_000, note = "", clientUuid = "$group-${type.lowercase()}", createdAt = now, updatedAt = now, transferGroup = group))
            }
        }

        suspend fun addBudget(categoryId: Long, limitMinor: Long) {
            budgets.insert(BudgetEntity(categoryId = categoryId, currencyCode = "USD", limitMinor = limitMinor, createdAt = now, updatedAt = now, clientUuid = UUID.randomUUID().toString()))
        }

        suspend fun addGoal(name: String = "Holiday") {
            goals.insert(GoalEntity(name = name, targetMinor = 500_00, currencyCode = "USD", targetDate = null, walletId = null, createdAt = now, updatedAt = now, clientUuid = UUID.randomUUID().toString()))
        }

        suspend fun addSchedule(walletId: Long, categoryId: Long) {
            recurring.insert(RecurringEntity(name = "Rent", type = "EXPENSE", amountMinor = 900_00, currencyCode = "USD", walletId = walletId, categoryId = categoryId, frequency = "MONTHLY", intervalCount = 1, startOn = 20_000, endOn = null, nextIndex = 0, paused = false, createdAt = now, updatedAt = now, clientUuid = UUID.randomUUID().toString()))
        }
    }

    private fun success(result: SyncResult): SyncReport {
        assertTrue("expected success, got $result", result is SyncResult.Success)
        return (result as SyncResult.Success).report
    }

    /** A full account set up on an old phone: two wallets, a transfer, categories, a budget, a goal and a schedule. */
    private suspend fun fullAccount(): Device {
        val old = Device()
        val rent = old.addCategory("Rent")
        old.addCategory("Boat fund") // a category the user named themselves
        val cash = old.addWallet("Cash")
        val bank = old.addWallet("Bank")
        old.addTransaction(cash.id, 900_00, categoryId = rent.id)
        old.addTransfer(bank.id, cash.id, 200_00)
        old.addBudget(rent.id, 1_000_00)
        old.addGoal()
        old.addSchedule(cash.id, rent.id)
        success(old.sync())
        return old
    }

    private fun serverIds(table: String) = server.live(table, user).map { it.str("id") }.toSet()

    // ---------------------------------------------------------------- the account check

    @Test
    fun `new account with an empty cloud is detected as empty`() = runTest {
        assertEquals(AccountProbe.Empty(user), Device().probe())
        assertEquals(SetupRoute.NEW_SETUP, setupRoute(Device().probe(), confirmedNewFor = null))
    }

    @Test
    fun `an account with wallets is detected as existing`() = runTest {
        val old = Device()
        old.addWallet()
        success(old.sync())
        assertEquals(AccountProbe.HasData(user), Device().probe())
    }

    @Test
    fun `an account with a wallet but no transactions is detected`() = runTest {
        val old = Device()
        old.addWallet()
        old.addCategory("Salary", kind = "INCOME")
        success(old.sync())
        assertEquals(0, server.live("transactions", user).size)
        assertEquals(AccountProbe.HasData(user), Device().probe())
    }

    @Test
    fun `the check uses the restore's own rule - a goal or an archived category alone is empty`() = runTest {
        // RestorePlan.hasData: a wallet or an active spending category. Using the same rule means "Welcome back" never
        // ends in "nothing to restore" (QA M2). A goal-only account sets up as new; its goal arrives with the first sync.
        val old = Device()
        old.addGoal()
        val gone = old.addCategory("Groceries")
        old.categories.update(gone.copy(archived = true, updatedAt = old.now + 1))
        success(old.sync())
        assertEquals(1, server.live("goals", user).size)
        assertEquals(AccountProbe.Empty(user), Device().probe())
    }

    // ---------------------------------------------------------------- P0-2.1: transactions, schedules, budgets

    /** Soft-deletes [table] row [id] on the server as another device would (causal: based on its current revision). */
    private suspend fun softDeleteOnServer(table: String, id: String, atMillis: Long) {
        val row = server.live(table, user).single { it.str("id") == id }
        val revision = (row["revision"] as kotlinx.serialization.json.JsonPrimitive).content.toLong()
        assertTrue(server.remote(user).softDelete(table, user, id, revision, SyncTime.toIso(atMillis)))
    }

    @Test
    fun `transactions are detected when their wallet was deleted on another device`() = runTest {
        // A transaction always has a wallet row; the only way the account holds a live transaction without a live wallet
        // is a concurrent delete-and-add across devices. Before P0-2.1 this account looked empty.
        val old = Device()
        val cash = old.addWallet()
        old.addTransaction(cash.id, 42_00)
        success(old.sync())
        softDeleteOnServer("wallets", cash.clientUuid!!, old.now + 1_000)
        assertTrue(server.live("wallets", user).isEmpty())
        assertEquals(1, server.live("transactions", user).size)

        assertEquals(AccountProbe.HasData(user), Device().probe())
    }

    @Test
    fun `scheduled payments are detected when their wallet was deleted on another device`() = runTest {
        val old = Device()
        val cash = old.addWallet()
        val rent = old.addCategory("Rent", kind = "INCOME") // no spending category in the account
        old.addSchedule(cash.id, rent.id)
        success(old.sync())
        softDeleteOnServer("wallets", cash.clientUuid!!, old.now + 1_000)
        assertTrue(server.live("wallets", user).isEmpty() && server.live("transactions", user).isEmpty())

        assertEquals(AccountProbe.HasData(user), Device().probe())
    }

    @Test
    fun `budgets are detected when their category is archived and there is no wallet`() = runTest {
        val old = Device()
        val rent = old.addCategory("Rent")
        old.addBudget(rent.id, 900_00)
        old.categories.update(old.categories.getById(rent.id)!!.copy(archived = true, updatedAt = old.now + 1, dirty = true))
        success(old.sync())
        assertTrue(server.live("wallets", user).isEmpty())

        assertEquals(AccountProbe.HasData(user), Device().probe())
    }

    @Test
    fun `deleted transactions, schedules and budgets do not count`() = runTest {
        val old = Device()
        val cash = old.addWallet()
        val rent = old.addCategory("Rent")
        val tx = old.addTransaction(cash.id, 42_00, categoryId = rent.id)
        success(old.sync())
        listOf("transactions" to tx.clientUuid, "wallets" to cash.clientUuid!!).forEach { (table, id) ->
            softDeleteOnServer(table, id, old.now + 1_000)
        }
        softDeleteOnServer("categories", rent.clientUuid!!, old.now + 1_000)
        assertTrue(server.rows("transactions", user).isNotEmpty()) // still on the server, soft-deleted

        assertEquals(AccountProbe.Empty(user), Device().probe())
    }

    @Test
    fun `an authentication failure during the check is never treated as a new account`() = runTest {
        val phone = Device()
        phone.wrap = { real ->
            object : SyncRemote by real {
                override suspend fun currentUserId(): String = error("401 JWT expired")
            }
        }
        val probe = phone.probe()
        assertTrue(probe is AccountProbe.Unavailable)
        assertEquals(SetupRoute.RETRY, setupRoute(probe, confirmedNewFor = null))
    }

    @Test
    fun `a permission refusal on any table is never treated as a new account`() = runTest {
        // The early tables answer "none"; a later one is refused (RLS / missing grant). One refusal is enough.
        listOf("wallets", "categories", "transactions", "recurring", "budgets").forEach { refused ->
            val phone = Device()
            phone.wrap = { real ->
                object : SyncRemote by real {
                    override suspend fun hasLiveRows(table: String, userId: String, kind: String?, activeOnly: Boolean): Boolean =
                        if (table == refused) error("42501 permission denied for table $table") else false
                }
            }
            assertTrue("refusal on $refused", phone.probe() is AccountProbe.Unavailable)
        }
    }

    @Test
    fun `the check stops at the first hit - an account with a wallet costs one request`() = runTest {
        val old = Device()
        old.addWallet()
        success(old.sync())
        val asked = mutableListOf<String>()
        val phone = Device()
        phone.wrap = { real ->
            object : SyncRemote by real {
                override suspend fun hasLiveRows(table: String, userId: String, kind: String?, activeOnly: Boolean): Boolean {
                    asked += table
                    return real.hasLiveRows(table, userId, kind, activeOnly)
                }
            }
        }
        assertEquals(AccountProbe.HasData(user), phone.probe())
        assertEquals(listOf("wallets"), asked)
    }

    @Test
    fun `an archived-wallet-only account passes both the check and the restore`() = runTest {
        // An account shape the check calls "has data" must also pass the restore's rule after a full download, or the
        // user would see "Welcome back" and then "nothing to restore" (the restore reads active wallets only).
        suspend fun restoredHasData(phone: Device): Boolean {
            success(phone.sync())
            // What the restore reads: active wallets only (WalletRepository.observeWallets).
            val active = phone.wallets.getAll().filter { !it.archived }.map {
                com.lucento.app.domain.Wallet(it.id, it.name, com.lucento.app.domain.WalletType.CASH, com.lucento.app.core.money.Money(it.openingBalanceMinor, it.currencyCode), it.createdAt)
            }
            val cats = phone.categories.getAll().map {
                com.lucento.app.domain.Category(id = it.id, name = it.name, kind = com.lucento.app.domain.CategoryKind.valueOf(it.kind), iconKey = it.iconKey, archived = it.archived)
            }
            return com.lucento.app.domain.RestorePlan.hasData(active, cats, phone.repository().localHasRestorableRecords())
        }

        // An archived wallet only.
        val a = Device()
        val w = a.addWallet()
        a.wallets.update(a.wallets.getById(w.id)!!.copy(archived = true, updatedAt = a.now + 1, dirty = true))
        success(a.sync())
        assertEquals(AccountProbe.HasData(user), Device().probe())
        assertTrue(restoredHasData(Device()))
    }

    @Test
    fun `a budget-only account passes both the check and the restore`() = runTest {
        val old = Device()
        val rent = old.addCategory("Rent")
        old.addBudget(rent.id, 900_00)
        old.categories.update(old.categories.getById(rent.id)!!.copy(archived = true, updatedAt = old.now + 1, dirty = true))
        success(old.sync())

        val phone = Device()
        assertEquals(AccountProbe.HasData(user), phone.probe())
        success(phone.sync())
        assertEquals(1, phone.budgets.getAll().size) // downloaded once
        assertTrue(phone.repository().localHasRestorableRecords())
    }

    /** Runs a full download, then applies the restore's own rule (OnboardingViewModel.runRestore → RestorePlan.hasData). */
    private suspend fun restorePasses(phone: Device): Boolean {
        success(phone.sync())
        val active = phone.wallets.getAll().filter { !it.archived }.map {
            com.lucento.app.domain.Wallet(it.id, it.name, com.lucento.app.domain.WalletType.CASH, com.lucento.app.core.money.Money(it.openingBalanceMinor, it.currencyCode), it.createdAt)
        }
        val cats = phone.categories.getAll().map {
            com.lucento.app.domain.Category(id = it.id, name = it.name, kind = com.lucento.app.domain.CategoryKind.valueOf(it.kind), iconKey = it.iconKey, archived = it.archived)
        }
        return com.lucento.app.domain.RestorePlan.hasData(active, cats, phone.repository().localHasRestorableRecords())
    }

    @Test
    fun `a transaction-only account passes both the check and the restore`() = runTest {
        val old = Device()
        val cash = old.addWallet()
        val tx = old.addTransaction(cash.id, 42_00)
        success(old.sync())
        softDeleteOnServer("wallets", cash.clientUuid!!, old.now + 1_000)
        val serverBefore = server.rows("transactions", user).map { it.str("id") to it["revision"] }

        val phone = Device()
        assertEquals(AccountProbe.HasData(user), phone.probe())
        assertTrue(restorePasses(phone))
        assertEquals(listOf(tx.clientUuid), phone.transactions.getAll().map { it.clientUuid }) // downloaded once
        assertEquals(42_00L, phone.transactions.getAll().single().amountMinor)
        assertEquals(serverBefore, server.rows("transactions", user).map { it.str("id") to it["revision"] }) // nothing re-uploaded
        assertTrue(server.live("wallets", user).isEmpty()) // the deleted wallet is not brought back
    }

    @Test
    fun `a schedule-only account passes both the check and the restore`() = runTest {
        val old = Device()
        val cash = old.addWallet()
        val salary = old.addCategory("Salary", kind = "INCOME")
        old.addSchedule(cash.id, salary.id)
        success(old.sync())
        softDeleteOnServer("wallets", cash.clientUuid!!, old.now + 1_000)
        val serverBefore = server.rows("recurring", user).map { it.str("id") to it["revision"] }

        val phone = Device()
        assertEquals(AccountProbe.HasData(user), phone.probe())
        assertTrue(restorePasses(phone))
        assertEquals(1, phone.recurring.getAll().size) // downloaded once
        assertEquals(900_00L, phone.recurring.getAll().single().amountMinor)
        assertEquals(serverBefore, server.rows("recurring", user).map { it.str("id") to it["revision"] })
        assertTrue(server.live("wallets", user).isEmpty())
    }

    @Test
    fun `a failing account check during the first sync claims nothing and uploads nothing`() = runTest {
        val phone = Device()
        val wallet = phone.addWallet()
        phone.addTransaction(wallet.id, 5_00)
        phone.wrap = { real ->
            object : SyncRemote by real {
                override suspend fun hasLiveRows(table: String, userId: String, kind: String?, activeOnly: Boolean): Boolean =
                    error("offline")
            }
        }
        assertTrue(phone.sync() is SyncResult.Retryable)
        assertNull(phone.prefs.syncedUser)
        assertNull(phone.prefs.initialPullDone)
        assertTrue(server.rows("wallets", user).isEmpty())
        assertTrue(server.rows("transactions", user).isEmpty())
    }

    @Test
    fun `a local category named like an archived account category is kept`() = runTest {
        val old = Device()
        old.addCategory("Water") // the account has active expense categories
        val boat = old.addCategory("Boat fund")
        success(old.sync())
        // A repository edit; on a device the Room trigger marks it dirty.
        old.categories.update(old.categories.getById(boat.id)!!.copy(archived = true, updatedAt = old.now + 1, dirty = true))
        success(old.sync())

        val phone = Device()
        phone.addCategory("Boat fund") // the user typed it again on this phone, unused
        success(phone.sync())
        assertTrue(phone.categories.getAll().any { it.name == "Boat fund" && !it.archived })
    }

    @Test
    fun `partial remote data - a spending category alone - is detected`() = runTest {
        val old = Device()
        old.addCategory("Groceries")
        success(old.sync())
        assertEquals(AccountProbe.HasData(user), Device().probe())
    }

    @Test
    fun `seeded income categories alone mean an empty account`() = runTest {
        val old = Device()
        old.addCategory("Salary", kind = "INCOME")
        success(old.sync())
        assertEquals(AccountProbe.Empty(user), Device().probe())
    }

    @Test
    fun `an account whose records were all deleted counts as empty`() = runTest {
        val old = Device()
        val cash = old.addWallet()
        success(old.sync())
        val synced = old.wallets.getById(cash.id)!!
        old.wallets.deleteById(cash.id)
        old.tombstones.insert(SyncTombstoneEntity(tableName = "wallets", clientUuid = cash.clientUuid!!, deletedAt = old.now, baseRevision = synced.baseRevision))
        success(old.sync())
        assertTrue(server.rows("wallets", user).isNotEmpty()) // the soft-deleted row is still on the server
        assertEquals(AccountProbe.Empty(user), Device().probe())
    }

    @Test
    fun `a network failure during the check is never treated as a new account`() = runTest {
        fullAccount()
        val phone = Device()
        phone.wrap = { real ->
            object : SyncRemote by real {
                override suspend fun hasLiveRows(table: String, userId: String, kind: String?, activeOnly: Boolean): Boolean =
                    error("offline")
            }
        }
        val probe = phone.probe()
        assertTrue(probe is AccountProbe.Unavailable)
        assertEquals(SetupRoute.RETRY, setupRoute(probe, confirmedNewFor = null))
        // Nothing was written anywhere by checking.
        assertTrue(phone.wallets.getAll().isEmpty() && phone.categories.getAll().isEmpty())
        assertNull(phone.prefs.syncedUser)
    }

    @Test
    fun `detection is scoped to the signed-in account`() = runTest {
        fullAccount() // belongs to `user`
        assertEquals(AccountProbe.Empty(otherUser), Device(userId = otherUser).probe())
    }

    @Test
    fun `signed out and unconfigured checks set up locally`() = runTest {
        assertEquals(AccountProbe.NotSignedIn, Device(userId = null).probe())
        assertEquals(AccountProbe.NotConfigured, Device().repository(remote = null).probeAccount())
        assertEquals(SetupRoute.LOCAL, setupRoute(AccountProbe.NotSignedIn, null))
        assertEquals(SetupRoute.LOCAL, setupRoute(AccountProbe.NotConfigured, null))
    }

    @Test
    fun `routing - a returning account restores unless the user already chose to set it up as new`() {
        assertEquals(SetupRoute.RESTORE, setupRoute(AccountProbe.HasData(user), confirmedNewFor = null))
        assertEquals(SetupRoute.RESTORE, setupRoute(AccountProbe.HasData(user), confirmedNewFor = otherUser))
        assertEquals(SetupRoute.NEW_SETUP, setupRoute(AccountProbe.HasData(user), confirmedNewFor = user))
        assertEquals(SetupRoute.RETRY, setupRoute(AccountProbe.Unavailable("x"), confirmedNewFor = user))
    }

    // ---------------------------------------------------------------- restoring a returning account

    @Test
    fun `restoring a returning account preserves every record once, with the same ids`() = runTest {
        fullAccount()
        val phone = Device()
        success(phone.sync())
        success(phone.sync()) // repeated initialization / a second auth callback

        assertEquals(serverIds("wallets"), phone.wallets.getAll().map { it.clientUuid }.toSet())
        assertEquals(serverIds("transactions"), phone.transactions.getAll().map { it.clientUuid }.toSet())
        assertEquals(serverIds("budgets"), phone.budgets.getAll().map { it.clientUuid }.toSet())
        assertEquals(serverIds("goals"), phone.goals.getAll().map { it.clientUuid }.toSet())
        assertEquals(serverIds("recurring"), phone.recurring.getAll().map { it.clientUuid }.toSet())
        assertEquals(serverIds("categories"), phone.categories.getAll().map { it.clientUuid }.toSet())
        // Counts, not only ids: nothing added on either side.
        assertEquals(2, phone.wallets.getAll().size)
        assertEquals(3, phone.transactions.getAll().size) // one expense + both transfer legs
        assertEquals(2, server.live("wallets", user).size)
        assertEquals(3, server.live("transactions", user).size)
        assertEquals(1, server.live("budgets", user).size)
        assertEquals(1, server.live("goals", user).size)
        assertEquals(1, server.live("recurring", user).size)
        // The transfer arrives whole: one OUT and one IN of the same amount.
        val legs = phone.transactions.getAll().filter { it.transferGroup != null }
        assertEquals(setOf("TRANSFER_OUT", "TRANSFER_IN"), legs.map { it.type }.toSet())
        assertEquals(1, legs.map { it.amountMinor }.distinct().size)
        assertTrue(phone.categories.getAll().any { it.name == "Boat fund" })
    }

    @Test
    fun `an interrupted first sync pulls first again on retry and creates nothing extra`() = runTest {
        fullAccount()
        val phone = Device()
        phone.addCategory("Streaming services") // an unused preset picked on this phone
        var failPull = true
        phone.wrap = { real ->
            object : SyncRemote by real {
                override suspend fun fetchPage(table: String, userId: String, sinceIso: String, after: SyncRemote.PageKey?, limit: Long): List<JsonObject> {
                    if (failPull && table == "transactions") error("connection reset mid-pull")
                    return real.fetchPage(table, userId, sinceIso, after, limit)
                }
            }
        }
        assertTrue(phone.sync() is SyncResult.Retryable)
        // The claim stands (no other account may follow), but the first pull is not marked done.
        assertEquals(user, phone.prefs.syncedUser)
        assertNull(phone.prefs.initialPullDone)
        assertTrue(server.live("categories", user).none { it.str("name") == "Streaming services" })

        failPull = false
        success(phone.sync())
        success(phone.sync())
        assertEquals(user, phone.prefs.initialPullDone)
        // The retry took the pull-first branch: the unused preset was tidied away, not uploaded.
        assertTrue(server.live("categories", user).none { it.str("name") == "Streaming services" })
        assertEquals(serverIds("wallets"), phone.wallets.getAll().map { it.clientUuid }.toSet())
        assertEquals(serverIds("transactions"), phone.transactions.getAll().map { it.clientUuid }.toSet())
        assertEquals(2, server.live("wallets", user).size)
        assertEquals(3, server.live("transactions", user).size)
    }

    @Test
    fun `a category the user named is kept and uploaded on first sign-in, an unused preset copy is not`() = runTest {
        fullAccount()
        val phone = Device()
        phone.addCategory("Pet insurance") // typed by the user, never used
        phone.addCategory("Streaming services") // a preset, never used
        phone.addCategory("boat fund ") // a copy of an account category the user named
        success(phone.sync())

        val names = server.live("categories", user).map { it.str("name") }
        assertTrue("Pet insurance" in names)
        assertTrue("Streaming services" !in names)
        assertEquals(1, names.count { CategoryPresets.normalize(it) == "boat fund" })
        assertTrue(phone.categories.getAll().any { it.name == "Pet insurance" })
    }

    @Test
    fun `local records on a phone that signs into an existing account are all kept alongside the account's`() = runTest {
        fullAccount()
        val phone = Device()
        val wallet = phone.addWallet("Savings")
        phone.addTransaction(wallet.id, 12_34)
        assertTrue(phone.repository().localHasFinancialData())
        success(phone.sync())

        assertEquals(3, server.live("wallets", user).size) // Cash, Bank + this phone's Savings
        assertEquals(4, server.live("transactions", user).size)
        assertEquals(3, phone.wallets.getAll().size)
        assertEquals(4, phone.transactions.getAll().size)
    }

    @Test
    fun `local financial data means wallets, transactions, budgets, goals or schedules - not categories`() = runTest {
        val phone = Device()
        phone.addCategory("Groceries")
        phone.addCategory("Salary", kind = "INCOME")
        assertFalse(phone.repository().localHasFinancialData())
        phone.addGoal()
        assertTrue(phone.repository().localHasFinancialData())
    }

    // ---------------------------------------------------------------- builds before P0-2

    @Test
    fun `a phone that already completed a sync before P0-2 is not taken through a first pull again`() = runTest {
        fullAccount()
        val phone = Device()
        success(phone.sync())
        phone.prefs.initialPullDone = null // an older build kept no marker
        phone.addCategory("Streaming services") // added since; a normal edit that must upload
        success(phone.sync())

        assertEquals(user, phone.prefs.initialPullDone)
        assertTrue(server.live("categories", user).any { it.str("name") == "Streaming services" })
    }

    @Test
    fun `a phone whose first sync never finished before P0-2 pulls first`() = runTest {
        fullAccount()
        val phone = Device()
        phone.prefs.syncedUser = user // claimed by an older build, which then failed: no lastSynced, no marker
        phone.addCategory("Streaming services")
        success(phone.sync())

        assertTrue(server.live("categories", user).none { it.str("name") == "Streaming services" })
        assertEquals(serverIds("wallets"), phone.wallets.getAll().map { it.clientUuid }.toSet())
    }

    // ---------------------------------------------------------------- ownership

    @Test
    fun `a different account never receives this phone's records`() = runTest {
        val phone = Device()
        phone.addWallet()
        success(phone.sync())
        phone.userId = otherUser
        assertEquals(SyncResult.AccountMismatch, phone.sync())
        assertTrue(server.rows("wallets", otherUser).isEmpty())
    }

    // ---------------------------------------------------------------- P1-5: transactional pull

    @Test
    fun `P1-5 - an edit saved while a pulled delete waits is kept, then wins on the server (D3)`() = runTest {
        val a = Device()
        val w = a.addWallet("Cash")
        success(a.sync())
        val b = Device(now = a.now + 10)
        success(b.sync())
        softDeleteOnServer("wallets", w.clientUuid!!, a.now + 1_000)

        // The second unit of work in B's pull is the queued delete; an edit is saved in the app just before it runs.
        var units = 0
        val result = b.repository(transact = { block ->
            units++
            if (units == 2) {
                val local = b.wallets.findByClientUuid(w.clientUuid!!)!!
                b.wallets.update(local.copy(name = "Cash (edited)", updatedAt = a.now + 5_000, dirty = true))
            }
            block()
        }).sync()
        assertTrue(result is SyncResult.Success)
        assertEquals("Cash (edited)", b.wallets.findByClientUuid(w.clientUuid!!)?.name) // not deleted under the edit

        b.now = a.now + 6_000
        success(b.sync())
        assertEquals(listOf("Cash (edited)"), server.live("wallets", user).map { it.str("name") }) // newer edit beats the delete
    }

    @Test
    fun `P1-5 - without an edit in between, a pulled delete still applies`() = runTest {
        val a = Device()
        val w = a.addWallet("Cash")
        success(a.sync())
        val b = Device(now = a.now + 10)
        success(b.sync())
        softDeleteOnServer("wallets", w.clientUuid!!, a.now + 1_000)
        success(b.repository(transact = { block -> block() }).sync())
        assertNull(b.wallets.findByClientUuid(w.clientUuid!!))
    }

    // ---------------------------------------------------------------- P1-8: budgets when categories are combined

    private suspend fun combineRent(accountAmount: Long, accountAt: Long, phoneAmount: Long, phoneAt: Long): Long {
        val old = Device(now = accountAt)
        val rent = old.addCategory("Rent")
        old.addBudget(rent.id, accountAmount)
        success(old.sync())
        val phone = Device(now = phoneAt)
        val local = phone.addCategory("Rent")
        phone.addBudget(local.id, phoneAmount)
        success(phone.sync())
        // The fold runs on the phone; its tombstones come from Room triggers, which these fakes don't have (the server
        // side of the fold was device-verified in QA O-14/O-15). Here: one Rent, one budget, the decided amount.
        assertEquals(1, phone.categories.getAll().count { it.name == "Rent" })
        assertEquals("one Rent budget after combining", 1, phone.budgets.getAll().size)
        return phone.budgets.getAll().single().limitMinor
    }

    @Test
    fun `P1-8 - combining keeps the more recently edited budget, whichever device is newer`() = runTest {
        assertEquals(900_00L, combineRent(accountAmount = 900_00, accountAt = 2_000_000_000_000L, phoneAmount = 500_00, phoneAt = 1_000_000_000_000L))
    }

    @Test
    fun `P1-8 - a newer budget on the phone wins over the account's older one`() = runTest {
        assertEquals(500_00L, combineRent(accountAmount = 900_00, accountAt = 1_000_000_000_000L, phoneAmount = 500_00, phoneAt = 2_000_000_000_000L))
    }

    // ---------------------------------------------------------------- F-7: the next account after a sign-out

    private val accountTables = listOf("wallets", "categories", "transactions", "budgets", "goals", "recurring")

    /** Every server row of [uid] (deleted ones too) as id:revision, per table. */
    private fun accountFingerprint(uid: String) =
        accountTables.associateWith { t -> server.rows(t, uid).map { "${it.str("id")}:${it["revision"]}" }.sorted() }

    private fun accountIds(uid: String) = accountTables.flatMap { t -> server.rows(t, uid).map { it.str("id") } }.toSet()

    /** Account A, set up and synced on [phone]: a wallet, a category, a transaction, a budget and a goal. */
    private suspend fun accountA(phone: Device) {
        val cash = phone.addWallet("A cash")
        val food = phone.addCategory("A food")
        phone.addTransaction(cash.id, 12_00, categoryId = food.id)
        phone.addBudget(food.id, 300_00)
        phone.addGoal("A goal")
        success(phone.sync())
    }

    @Test
    fun `F-7 - after A signs out, an empty account B gets the initial setup and none of A's records`() = runTest {
        val phone = Device()
        accountA(phone)
        val aIds = accountIds(user)
        val aBefore = accountFingerprint(user)

        var setupReset = false
        phone.signOutWipe { setupReset = true }
        assertTrue(setupReset) // the production wipe's setup reset itself is covered by LocalLedgerResetTest
        assertNull(phone.prefs.syncedUser)
        assertNull(phone.prefs.initialPullDone)
        assertTrue(phone.wallets.getAll().isEmpty() && phone.transactions.getAll().isEmpty() && phone.budgets.getAll().isEmpty() && phone.goals.getAll().isEmpty())

        // B signs in during that onboarding: the check finds nothing, so B is set up as new.
        phone.userId = otherUser
        val probe = phone.probe()
        assertEquals(AccountProbe.Empty(otherUser), probe)
        assertEquals(SetupRoute.NEW_SETUP, setupRoute(probe, confirmedNewFor = null))
        // Onboarding's category picks, then sync turns on when it finishes.
        phone.addCategory("Rent")
        phone.addCategory("Groceries")
        success(phone.sync())

        assertEquals(setOf("Rent", "Groceries"), server.live("categories", otherUser).map { it.str("name") }.toSet())
        assertTrue(listOf("wallets", "transactions", "budgets", "goals", "recurring").all { server.rows(it, otherUser).isEmpty() })
        assertTrue(accountIds(otherUser).intersect(aIds).isEmpty())
        assertEquals(aBefore, accountFingerprint(user))
        assertEquals(otherUser, phone.prefs.syncedUser)
    }

    @Test
    fun `F-7 - after A signs out, a returning account B is restored, not set up again, and A's records stay out`() = runTest {
        val bPhone = Device(userId = otherUser)
        bPhone.addWallet("B bank")
        bPhone.addCategory("B rent")
        success(bPhone.sync())
        val bBefore = accountFingerprint(otherUser)

        val phone = Device()
        accountA(phone)
        val aBefore = accountFingerprint(user)
        phone.signOutWipe()

        phone.userId = otherUser
        val probe = phone.probe()
        assertEquals(AccountProbe.HasData(otherUser), probe)
        assertEquals(SetupRoute.RESTORE, setupRoute(probe, confirmedNewFor = null))
        assertTrue(restorePasses(phone))

        assertEquals(listOf("B bank"), phone.wallets.getAll().map { it.name })
        assertEquals(setOf("B rent"), phone.categories.getAll().map { it.name }.toSet())
        assertTrue(phone.transactions.getAll().isEmpty() && phone.budgets.getAll().isEmpty() && phone.goals.getAll().isEmpty())
        assertEquals(bBefore, accountFingerprint(otherUser)) // the restore uploaded nothing into B
        assertEquals(aBefore, accountFingerprint(user))
    }

    @Test
    fun `F-7 - a failed check of account B after A signs out sets nothing up and uploads nothing`() = runTest {
        val phone = Device()
        accountA(phone)
        val aBefore = accountFingerprint(user)
        phone.signOutWipe()

        phone.userId = otherUser
        phone.wrap = { real ->
            object : SyncRemote by real {
                override suspend fun hasLiveRows(table: String, userId: String, kind: String?, activeOnly: Boolean): Boolean =
                    error("offline")
            }
        }
        val probe = phone.probe()
        assertTrue(probe is AccountProbe.Unavailable)
        assertEquals(SetupRoute.RETRY, setupRoute(probe, confirmedNewFor = null))
        // Even a sync attempted anyway claims nothing and uploads nothing.
        phone.addCategory("Rent")
        assertTrue(phone.sync() is SyncResult.Retryable)
        assertNull(phone.prefs.syncedUser)
        assertTrue(accountFingerprint(otherUser).values.all { it.isEmpty() })
        assertEquals(aBefore, accountFingerprint(user))
    }

    @Test
    fun `F-7 - Settings leaves the next account to onboarding once a wipe has reset the setup`() {
        assertFalse(syncAfterAccountWipe(onboardingComplete = false))
        assertTrue(syncAfterAccountWipe(onboardingComplete = true))
    }

    // ---------------------------------------------------------------- the rule itself

    @Test
    fun `first sign-in category rule`() {
        // Referenced, or already in the account: never dropped.
        assertFalse(FirstSignInCategories.drop(hasServerRow = true, transactions = 0, budgets = 0, schedules = 0))
        assertFalse(FirstSignInCategories.drop(hasServerRow = false, transactions = 1, budgets = 0, schedules = 0, copiesAccountCategory = true))
        // A name the user typed stays, even when the account has categories of that kind.
        assertFalse(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 0, schedules = 0, isPresetName = false))
        // An unused copy of an account category goes, preset or not.
        assertTrue(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 0, schedules = 0, isPresetName = false, copiesAccountCategory = true))
        // An unused preset goes only when the account has that kind (R9-03).
        assertTrue(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 0, schedules = 0, accountHasKind = true))
        assertFalse(FirstSignInCategories.drop(hasServerRow = false, transactions = 0, budgets = 0, schedules = 0, accountHasKind = false))
        assertTrue(FirstSignInCategories.isPresetName("Streaming services"))
        assertTrue(FirstSignInCategories.isPresetName("salary "))
        assertFalse(FirstSignInCategories.isPresetName("Boat fund"))
    }

    // ---------------------------------------------------------------- Settings' first sync, background sync

    @Test
    fun `settings asks before joining a phone with records to an account with data`() {
        assertEquals(FirstSync.ASK_MERGE, firstSyncDecision(alreadyBound = false, probe = AccountProbe.HasData(user), localHasData = true))
        assertEquals(FirstSync.SYNC, firstSyncDecision(alreadyBound = false, probe = AccountProbe.HasData(user), localHasData = false))
        assertEquals(FirstSync.SYNC, firstSyncDecision(alreadyBound = false, probe = AccountProbe.Empty(user), localHasData = true))
        // A failed check never uploads, whatever is on the phone.
        assertEquals(FirstSync.DISCONNECT, firstSyncDecision(alreadyBound = false, probe = AccountProbe.Unavailable("x"), localHasData = true))
        assertEquals(FirstSync.DISCONNECT, firstSyncDecision(alreadyBound = false, probe = AccountProbe.Unavailable("x"), localHasData = false))
        // Already synced with this account: no question.
        assertEquals(FirstSync.SYNC, firstSyncDecision(alreadyBound = true, probe = null, localHasData = true))
    }

    @Test
    fun `background sync never runs while onboarding is unfinished`() {
        assertTrue(backgroundSyncAllowed(syncEnabled = true, onboardingComplete = true))
        assertFalse(backgroundSyncAllowed(syncEnabled = true, onboardingComplete = false))
        assertFalse(backgroundSyncAllowed(syncEnabled = false, onboardingComplete = true))
    }

    // ---------------------------------------------------------------- onboarding's category choices

    @Test
    fun `onboarding's category choices never remove a category that came from the account`() = runTest {
        val dao = FakeCategoryDao()
        val fromAccount = dao.insert(CategoryEntity(name = "Boat fund", kind = "EXPENSE", iconKey = "other", sortOrder = 0, createdAt = 1, updatedAt = 1, clientUuid = "acct-boat", dirty = false, syncedAt = 1, baseRevision = 3))
        val localOnly = dao.insert(CategoryEntity(name = "Dining", kind = "EXPENSE", iconKey = "dining", sortOrder = 1, createdAt = 1, updatedAt = 1, clientUuid = "local-dining"))
        val repository = CategoryRepository(dao, clock = { 1L })
        val groceries = CategorySelection.Choice.FromPreset(CategoryPresets.preset("groceries")!!)

        repository.applySelection(listOf(groceries), keepAccountCategories = true)

        val active = dao.getAll().filter { !it.archived }.map { it.id }
        assertTrue(fromAccount in active)
        assertFalse(localOnly in active) // an unchosen local category is still removed as before
        assertNotNull(dao.getAll().firstOrNull { it.name == "Groceries" })
    }

    @Test
    fun `the Budget screen's category setup still removes unchosen categories, synced or not`() = runTest {
        val dao = FakeCategoryDao()
        val fromAccount = dao.insert(CategoryEntity(name = "Boat fund", kind = "EXPENSE", iconKey = "other", sortOrder = 0, createdAt = 1, updatedAt = 1, clientUuid = "acct-boat", dirty = false, syncedAt = 1, baseRevision = 3))
        val repository = CategoryRepository(dao, clock = { 1L })
        repository.applySelection(listOf(CategorySelection.Choice.FromPreset(CategoryPresets.preset("groceries")!!)))
        assertFalse(fromAccount in dao.getAll().filter { !it.archived }.map { it.id })
    }
}
