package com.lucento.app.data.sync

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.preferences.AccountSettingsStore
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.FakeBudgetDao
import com.lucento.app.data.repository.FakeCategoryDao
import com.lucento.app.data.repository.FakeDebtTermsDao
import com.lucento.app.data.repository.FakeGoalDao
import com.lucento.app.data.repository.FakeRecurringDao
import com.lucento.app.data.repository.FakeSyncDao
import com.lucento.app.data.repository.FakeTransactionDao
import com.lucento.app.data.repository.FakeWalletDao
import com.lucento.app.domain.settings.AccountSetting
import com.lucento.app.domain.settings.AccountSettingsDocument
import com.lucento.app.domain.settings.AccountSettingsMerge
import com.lucento.app.domain.settings.SettingEntry
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
 * Settings that follow the account, over sync (docs/SYNC.md "Settings"; supabase/migrations/20261122090000_user_settings.sql).
 * Tester feedback 2026-11-22: sync brought back the records but not the settings, so a restore meant setting up again.
 */
class AccountSettingsSyncTest {

    private val owner = "11111111-1111-4111-8111-111111111111"
    private val partner = "22222222-2222-4222-8222-222222222222"
    private val server = FakeSyncServer().apply { members[partner] = owner }

    private val theme = stringPreferencesKey("theme_id")
    private val themeMode = stringPreferencesKey("theme_mode")
    private val features = stringSetPreferencesKey("enabled_features")
    private val name = stringPreferencesKey("display_name")
    private val cents = booleanPreferencesKey("show_cents")
    private val walletsView = stringPreferencesKey("wallets_view_mode")
    private val defaultWallet = longPreferencesKey("default_wallet_id")
    private val dirty = stringSetPreferencesKey("account_settings_dirty")

    /** [alreadySynced]: this phone synced the account's records before settings synced (it updated, it didn't join). */
    private inner class Device(
        var account: String,
        space: String? = null,
        var now: Long = 1_000_000_000_000L,
        alreadySynced: Boolean = false,
    ) {
        val wallets = FakeWalletDao()
        val categories = FakeCategoryDao()
        val transactions = FakeTransactionDao()
        val tombstones = FakeSyncDao()
        var prefs = FakeSyncPreferences(enabled = true).apply {
            sharedSpaceOwner = space
            if (alreadySynced) {
                syncedUser = account
                initialPullDone = account
            }
        }
        val settings: MutablePreferences = mutablePreferencesOf()
        var ownCloud = true
        var lockedThemes = emptySet<String>()

        init {
            wallets.transactions = transactions
            categories.transactions = transactions
        }

        /** The app's store, over [settings] instead of a DataStore (same functions UserPreferencesRepository uses). */
        private val store = object : AccountSettingsStore {
            override suspend fun bindAccountSettings(account: String, claiming: Boolean) =
                UserPreferencesRepository.bindAccountSettingsInto(this@Device.settings, account, claiming)

            override suspend fun mergeAccountSettings(
                account: String,
                document: JsonObject?,
                settings: Set<AccountSetting>,
                walletUuid: (Long) -> String?,
                walletId: (String) -> Long?,
                lockedThemes: Set<String>,
            ): AccountSettingsMerge.Result =
                UserPreferencesRepository.mergeAccountSettingsInto(this@Device.settings, account, document, settings, walletUuid, walletId, lockedThemes)

            override suspend fun markAccountSettingsSent(upload: AccountSettingsMerge.Upload) =
                UserPreferencesRepository.markAccountSettingsSentInto(this@Device.settings, upload)
        }

        /** The person changes settings here, as through UserPreferencesRepository's tracking edit. */
        fun change(block: MutablePreferences.() -> Unit) {
            val before = UserPreferencesRepository.accountSettingsSnapshot(settings)
            settings.block()
            UserPreferencesRepository.recordAccountSettingChanges(settings, before, now)
        }

        suspend fun sync(): SyncResult = SyncRepository(
            server.remote(account), wallets, categories, transactions, FakeBudgetDao(), FakeGoalDao(), tombstones, prefs,
            recurring = FakeRecurringDao(), debtTerms = FakeDebtTermsDao(), clock = { now },
            accountSettings = store, accountSettingsUploadAllowed = { ownCloud }, lockedThemes = { lockedThemes },
        ).sync()

        /** A sync built with the settings store but without its plan checks, which must then deny (Pro-gate review F1). */
        suspend fun syncWithoutPlanChecks(): SyncResult = SyncRepository(
            server.remote(account), wallets, categories, transactions, FakeBudgetDao(), FakeGoalDao(), tombstones, prefs,
            recurring = FakeRecurringDao(), debtTerms = FakeDebtTermsDao(), clock = { now },
            accountSettings = store,
        ).sync()

        /** Signs out (the wipe's preference part and a cleared binding), then signs in as [next]. */
        fun signOutAndSignIn(next: String) {
            UserPreferencesRepository.resetAccountSetup(settings)
            prefs = FakeSyncPreferences(enabled = true)
            account = next
        }

        suspend fun wallet(walletName: String): WalletEntity {
            val id = wallets.insert(
                WalletEntity(
                    name = walletName, type = "BANK", currencyCode = "PHP", openingBalanceMinor = 0, createdAt = now, updatedAt = now,
                    clientUuid = UUID.randomUUID().toString(),
                ),
            )
            return wallets.getById(id)!!
        }
    }

    private fun success(result: SyncResult): SyncReport {
        assertTrue("expected success, got $result", result is SyncResult.Success)
        return (result as SyncResult.Success).report
    }

    private fun serverValue(account: String, setting: AccountSetting) =
        server.settingsRows[account]?.first?.let { AccountSettingsDocument.entry(it, setting) }?.value

    @Test
    fun `the tester's case - a restored phone comes back with the settings, not only the records`() = runTest {
        val old = Device(owner)
        val bank = old.wallet("Bank")
        old.change {
            this[theme] = "grove"
            this[themeMode] = "DARK"
            this[features] = setOf("budgets", "insights")
            this[name] = "Alex"
            this[cents] = false
            this[walletsView] = "rows"
            this[defaultWallet] = bank.id
        }
        success(old.sync())
        assertNull("everything reached the account", old.settings[dirty])

        val restored = Device(owner)
        val report = success(restored.sync())
        assertEquals("grove", restored.settings[theme])
        assertEquals("DARK", restored.settings[themeMode])
        assertEquals(setOf("budgets", "insights"), restored.settings[features])
        assertEquals("Alex", restored.settings[name])
        assertEquals(false, restored.settings[cents])
        assertEquals("rows", restored.settings[walletsView])
        // The default wallet travels by its stable id and lands on this phone's copy of the same wallet.
        val localBank = restored.wallets.rows.value.single { it.clientUuid == bank.clientUuid }
        assertEquals(localBank.id, restored.settings[defaultWallet])
        assertEquals(7, report.settingsTaken)
    }

    @Test
    fun `two phones that changed different settings both keep their change`() = runTest {
        val a = Device(owner)
        val b = Device(owner)
        success(a.sync())
        success(b.sync())
        a.change { this[theme] = "mono" }
        b.change { this[cents] = false }
        success(a.sync())
        success(b.sync())
        success(a.sync())
        for (phone in listOf(a, b)) {
            assertEquals("mono", phone.settings[theme])
            assertEquals(false, phone.settings[cents])
        }
    }

    @Test
    fun `the same setting changed on two phones - the later change wins, and the phone whose change lost is told`() = runTest {
        val a = Device(owner)
        val b = Device(owner)
        success(a.sync())
        success(b.sync())
        a.change { this[theme] = "mono" }
        b.now += 60_000
        b.change { this[theme] = "aurora" }
        // The later change goes up first; the earlier one, synced after it, gives way.
        success(b.sync())
        val report = success(a.sync())
        assertEquals("aurora", a.settings[theme])
        assertEquals(1, report.overridden)
        assertEquals(1, a.prefs.overriddenChanges)
        assertEquals(JsonPrimitive("aurora"), serverValue(owner, AccountSetting.THEME))
    }

    @Test
    fun `a change made after taking another phone's wins, even when this phone's clock runs an hour behind`() = runTest {
        val a = Device(owner)
        val slow = Device(owner, now = 1_000_000_000_000L - 3_600_000L)
        a.change { this[theme] = "grove" }
        success(a.sync())
        success(slow.sync())
        assertEquals("grove", slow.settings[theme])
        slow.change { this[theme] = "mono" }
        success(slow.sync())
        success(a.sync())
        assertEquals("mono", a.settings[theme])
    }

    @Test
    fun `another phone saving between this phone's read and write - it merges again, and both changes survive`() = runTest {
        val a = Device(owner)
        a.change { this[theme] = "grove" }
        success(a.sync())
        a.change { this[themeMode] = "DARK" }
        // Just before a's save, another phone of the account saves Show cents off on top of the same revision.
        server.beforeSettingsSave = {
            val (document, revision) = server.settingsRows.getValue(owner)
            server.settingsRows[owner] = JsonObject(
                document + (AccountSetting.SHOW_CENTS.key to AccountSettingsDocument.encode(SettingEntry(JsonPrimitive(false), 5L))),
            ) to revision + 1
        }
        success(a.sync())
        assertEquals(false, a.settings[cents])
        assertEquals(JsonPrimitive("grove"), serverValue(owner, AccountSetting.THEME))
        assertEquals(JsonPrimitive("DARK"), serverValue(owner, AccountSetting.THEME_MODE))
        assertEquals(JsonPrimitive(false), serverValue(owner, AccountSetting.SHOW_CENTS))
        assertEquals(3L, server.settingsRows.getValue(owner).second)
        assertNull(a.settings[dirty])
    }

    @Test
    fun `a project without the settings table still syncs the records, and the settings wait`() = runTest {
        server.noUserSettings = true
        val phone = Device(owner)
        phone.wallet("Cash")
        phone.change { this[theme] = "grove" }
        success(phone.sync())
        assertEquals(1, server.live("wallets", owner).size)
        assertEquals(setOf("theme"), phone.settings[dirty])

        server.noUserSettings = false
        success(phone.sync())
        assertEquals(JsonPrimitive("grove"), serverValue(owner, AccountSetting.THEME))
        assertNull(phone.settings[dirty])
    }

    @Test
    fun `when the plan refuses the settings, the cycle still finishes and the settings wait`() = runTest {
        // The server's gate holds CLOUD for nobody (security audit F-01).
        server.cloud = mutableSetOf()
        val phone = Device(owner)
        phone.change { this[theme] = "grove" }
        success(phone.sync())
        assertTrue(server.settingsSaves.isEmpty())
        assertEquals(setOf("theme"), phone.settings[dirty])
    }

    @Test
    fun `while the records' uploads are paused, the settings aren't sent either`() = runTest {
        server.cloud = mutableSetOf()
        val phone = Device(owner)
        phone.wallet("Cash")
        phone.change { this[theme] = "grove" }
        val result = phone.sync()
        assertTrue("got $result", result is SyncResult.UploadsPaused)
        assertTrue(server.settingsSaves.isEmpty())
        assertEquals(setOf("theme"), phone.settings[dirty])
    }

    @Test
    fun `a Partner without cloud of their own keeps the settings here, and syncs the space's records`() = runTest {
        val partnerPhone = Device(partner, space = owner).apply { ownCloud = false }
        partnerPhone.change { this[theme] = "aurora" }
        success(partnerPhone.sync())
        assertTrue(server.settingsSaves.isEmpty())
        assertEquals(setOf("theme"), partnerPhone.settings[dirty])
    }

    @Test
    fun `a Partner's settings are their own - saved under their account, without the space's default wallet`() = runTest {
        val ownerPhone = Device(owner)
        val bank = ownerPhone.wallet("Bank")
        success(ownerPhone.sync())
        val partnerPhone = Device(partner, space = owner)
        success(partnerPhone.sync())
        val sharedBank = partnerPhone.wallets.rows.value.single { it.clientUuid == bank.clientUuid }
        partnerPhone.change {
            this[theme] = "aurora"
            this[defaultWallet] = sharedBank.id
        }
        success(partnerPhone.sync())
        assertEquals(JsonPrimitive("aurora"), serverValue(partner, AccountSetting.THEME))
        assertNull(serverValue(partner, AccountSetting.DEFAULT_WALLET))
        // The space's default wallet isn't left waiting to go anywhere.
        assertNull(partnerPhone.settings[dirty])
        assertNull("the Owner's settings are untouched", server.settingsRows[owner]?.first?.get(AccountSetting.THEME.key))
    }

    @Test
    fun `a phone joining an account takes the account's settings, and fills in what the account has none of`() = runTest {
        val first = Device(owner)
        first.change { this[theme] = "grove" }
        success(first.sync())

        // A second phone set up on its own (later, and with a choice of its own the account doesn't have), then joined.
        val second = Device(owner, now = 1_000_000_000_000L + 86_400_000L)
        second.change {
            this[theme] = "mono"
            this[walletsView] = "rows"
        }
        val report = success(second.sync())
        assertEquals("grove", second.settings[theme])
        assertEquals(0, report.overridden)
        assertEquals(JsonPrimitive("grove"), serverValue(owner, AccountSetting.THEME))
        assertEquals(JsonPrimitive("rows"), serverValue(owner, AccountSetting.WALLETS_VIEW))
        assertFalse(second.settings[dirty].orEmpty().contains("theme"))
    }

    @Test
    fun `updating Lucento changes nothing on phones that already synced, and a change made since reaches them`() = runTest {
        // QA review 2026-11-22: two phones set up differently both update; the first to sync must not restyle the other.
        val a = Device(owner, alreadySynced = true).apply { settings[theme] = "grove"; settings[cents] = false }
        val b = Device(owner, alreadySynced = true).apply { settings[theme] = "mono" }
        success(a.sync())
        success(b.sync())
        assertEquals("mono", b.settings[theme])
        assertNull("b keeps showing cents", b.settings[cents])
        // A restore on a new phone gets what the account holds.
        val restored = Device(owner)
        success(restored.sync())
        assertEquals("grove", restored.settings[theme])
        // A change made since the update reaches every phone.
        a.now += 60_000
        a.change { this[theme] = "aurora" }
        success(a.sync())
        success(b.sync())
        assertEquals("aurora", b.settings[theme])
    }

    @Test
    fun `after a sign-out the next account never receives what the previous one left on the phone`() = runTest {
        val phone = Device(owner)
        phone.change {
            this[theme] = "grove"
            this[cents] = false
        }
        success(phone.sync())
        phone.signOutAndSignIn(partner)
        success(phone.sync())
        assertNull("nothing of the owner's went into the next account", server.settingsRows[partner])
    }

    @Test
    fun `the same account signing back in keeps its settings, and a change still waiting goes up`() = runTest {
        val phone = Device(owner)
        phone.change { this[theme] = "grove" }
        success(phone.sync())
        server.noUserSettings = true
        phone.now += 60_000
        phone.change { this[cents] = false }
        success(phone.sync())
        server.noUserSettings = false
        phone.signOutAndSignIn(owner)
        success(phone.sync())
        assertEquals("grove", phone.settings[theme])
        assertEquals(JsonPrimitive(false), serverValue(owner, AccountSetting.SHOW_CENTS))
    }

    @Test
    fun `when other phones keep saving first, the cycle still finishes and the change waits`() = runTest {
        val a = Device(owner)
        a.change { this[theme] = "grove" }
        success(a.sync())
        a.change { this[cents] = false }
        fun anotherPhoneSaves() {
            val (document, revision) = server.settingsRows.getValue(owner)
            server.settingsRows[owner] = document to revision + 1
            server.beforeSettingsSave = ::anotherPhoneSaves
        }
        server.beforeSettingsSave = ::anotherPhoneSaves
        success(a.sync())
        assertEquals(SyncRepository.SETTINGS_ATTEMPTS + 1, server.settingsSaves.size)
        assertEquals(setOf("show_cents"), a.settings[dirty])
    }

    @Test
    fun `an account's Pro theme isn't applied on a phone whose plan can't pick it`() = runTest {
        val first = Device(owner)
        first.change { this[theme] = "aurora" }
        success(first.sync())
        val restored = Device(owner).apply { lockedThemes = setOf("aurora") }
        success(restored.sync())
        assertNull(restored.settings[theme])
    }

    @Test
    fun `a sync built without the plan checks applies no Pro theme and saves nothing`() = runTest {
        // Pro-gate review 2026-11-23 (F1): both checks deny unless the app wires them in.
        val first = Device(owner)
        first.change { this[theme] = "aurora" }
        success(first.sync())
        val restored = Device(owner)
        success(restored.syncWithoutPlanChecks())
        assertNull(restored.settings[theme])
        val saves = server.settingsSaves.size
        restored.change { this[cents] = false }
        success(restored.syncWithoutPlanChecks())
        assertEquals(saves, server.settingsSaves.size)
        assertEquals(setOf("show_cents"), restored.settings[dirty])
    }
}
