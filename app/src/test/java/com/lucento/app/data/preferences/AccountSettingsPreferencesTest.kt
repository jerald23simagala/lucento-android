package com.lucento.app.data.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.lucento.app.domain.Feature
import com.lucento.app.domain.backup.BackupSettings
import com.lucento.app.domain.settings.AccountSetting
import com.lucento.app.domain.settings.AccountSettingsDocument
import com.lucento.app.domain.settings.SettingEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings that follow the account, as stored on the phone (docs/SYNC.md "Settings"): which changes are marked for
 * sync, how the account's document is merged in, and what a sign-out, a restore and a backup do to them.
 */
class AccountSettingsPreferencesTest {

    private val themeMode = stringPreferencesKey("theme_mode")
    private val theme = stringPreferencesKey("theme_id")
    private val features = stringSetPreferencesKey("enabled_features")
    private val name = stringPreferencesKey("display_name")
    private val cents = booleanPreferencesKey("show_cents")
    private val walletsView = stringPreferencesKey("wallets_view_mode")
    private val fx = booleanPreferencesKey("fx_rates_online")
    private val aiOff = booleanPreferencesKey("ask_ai_off")
    private val aiConfirmed = booleanPreferencesKey("ask_lucento_ai_v2")
    private val defaultWallet = longPreferencesKey("default_wallet_id")
    private val budgetMonth = stringPreferencesKey("acknowledged_budget_month")
    private val plannedMonth = stringPreferencesKey("budget_month_planned")
    private val recapSeen = stringPreferencesKey("recap_month_seen")
    private val smart = booleanPreferencesKey("smart_reading_v2")
    private val onlineVoice = booleanPreferencesKey("online_voice")
    private val crash = booleanPreferencesKey("crash_reports")
    private val usage = booleanPreferencesKey("usage_counts")
    private val appLock = booleanPreferencesKey("app_lock")
    private val hideAmounts = booleanPreferencesKey("hide_amounts")
    private val hideInRecents = booleanPreferencesKey("hide_in_recents")
    private val dirty = stringSetPreferencesKey("account_settings_dirty")
    private val bound = stringPreferencesKey("account_settings_account")
    private val inherited = stringSetPreferencesKey("account_settings_inherited")
    private fun at(setting: AccountSetting) = longPreferencesKey("account_setting_at_${setting.key}")

    private val wallets = mapOf(7L to "wallet-cash", 8L to "wallet-bank")
    private fun uuid(id: Long) = wallets[id]
    private fun id(uuid: String) = wallets.entries.firstOrNull { it.value == uuid }?.key

    /** The person's change, as [UserPreferencesRepository]'s tracked edit makes it. */
    private fun MutablePreferences.change(now: Long, block: MutablePreferences.() -> Unit) {
        val before = UserPreferencesRepository.accountSettingsSnapshot(this)
        block()
        UserPreferencesRepository.recordAccountSettingChanges(this, before, now)
    }

    /** A phone's first sync with [account] (a restore, a new phone): it joins. */
    private fun claim(p: MutablePreferences, account: String = "account-1") =
        UserPreferencesRepository.bindAccountSettingsInto(p, account, claiming = true)

    private fun merge(
        p: MutablePreferences,
        document: JsonObject?,
        account: String = "account-1",
        settings: Set<AccountSetting> = AccountSetting.entries.toSet(),
        lockedThemes: Set<String> = emptySet(),
    ) = UserPreferencesRepository.mergeAccountSettingsInto(p, account, document, settings, ::uuid, ::id, lockedThemes)

    private fun doc(vararg entries: Pair<AccountSetting, Pair<JsonElement, Long>>) =
        JsonObject(entries.associate { (s, e) -> s.key to AccountSettingsDocument.encode(SettingEntry(e.first, e.second)) })

    @Test
    fun `changing a setting that follows the account marks it, with when`() {
        val p = mutablePreferencesOf()
        p.change(now = 1_000) { this[theme] = "grove" }
        assertEquals(setOf("theme"), p[dirty])
        assertEquals(1_000L, p[at(AccountSetting.THEME)])
        // Writing the same value again is no change.
        p.change(now = 2_000) { this[theme] = "grove" }
        assertEquals(1_000L, p[at(AccountSetting.THEME)])
        // A later change on a phone whose clock went back is still dated after the first.
        p.change(now = 500) { this[theme] = "mono" }
        assertEquals(1_001L, p[at(AccountSetting.THEME)])
    }

    @Test
    fun `consents, AI answers and this phone's own lock never become account settings`() {
        val p = mutablePreferencesOf()
        p.change(now = 1_000) {
            this[smart] = true
            this[onlineVoice] = true
            this[crash] = true
            this[usage] = true
            this[appLock] = true
            this[hideAmounts] = true
            this[hideInRecents] = true
            this[aiOff] = true
            this[aiConfirmed] = true
        }
        assertNull(p[dirty])
        assertTrue(AccountSetting.entries.none { p[at(it)] != null })
    }

    @Test
    fun `a restored phone takes the account's settings and records them as the account's`() {
        val p = mutablePreferencesOf()
        claim(p)
        val result = merge(
            p,
            doc(
                AccountSetting.THEME to (JsonPrimitive("grove") to 100L),
                AccountSetting.THEME_MODE to (JsonPrimitive("DARK") to 100L),
                AccountSetting.FEATURES to (JsonArray(listOf(JsonPrimitive("budgets"), JsonPrimitive("insights"))) to 100L),
                AccountSetting.DISPLAY_NAME to (JsonPrimitive("Alex") to 100L),
                AccountSetting.SHOW_CENTS to (JsonPrimitive(false) to 100L),
                AccountSetting.WALLETS_VIEW to (JsonPrimitive("rows") to 0L),
                AccountSetting.FX_RATES_ONLINE to (JsonPrimitive(false) to 100L),
                AccountSetting.DEFAULT_WALLET to (JsonPrimitive("wallet-bank") to 100L),
                AccountSetting.BUDGET_MONTH_SEEN to (JsonPrimitive("2026-10") to 100L),
            ),
        )
        assertEquals("grove", p[theme])
        assertEquals("DARK", p[themeMode])
        assertEquals(setOf("budgets", "insights"), p[features])
        assertEquals("Alex", p[name])
        assertEquals(false, p[cents])
        assertEquals("rows", p[walletsView])
        assertEquals(false, p[fx])
        assertEquals(8L, p[defaultWallet])
        assertEquals("2026-10", p[budgetMonth])
        assertEquals(9, result.taken)
        assertEquals(0, result.replaced)
        assertNull(result.upload)
        assertEquals(100L, p[at(AccountSetting.THEME)])
        assertEquals("account-1", p[bound])
        assertNull(p[dirty])
    }

    @Test
    fun `an account without settings yet gets this phone's own choices, never its defaults`() {
        val p = mutablePreferencesOf(theme to "aurora", walletsView to "rows")
        p.change(now = 5_000) { this[name] = "Sam" }
        claim(p)
        val upload = merge(p, document = null).upload!!
        assertEquals(mapOf(AccountSetting.DISPLAY_NAME to 5_000L), upload.sent)
        assertEquals(setOf(AccountSetting.THEME, AccountSetting.WALLETS_VIEW), upload.seeded.keys)
        // Settings with no value of their own here (the theme mode, cents, the modules…) aren't sent.
        assertFalse(upload.document.containsKey(AccountSetting.THEME_MODE.key))
        assertFalse(upload.document.containsKey(AccountSetting.SHOW_CENTS.key))
    }

    @Test
    fun `a phone that synced before the update keeps its own settings, and takes a change made since`() {
        // QA review: the first phone to sync after the update must not silently restyle the others.
        val p = mutablePreferencesOf(theme to "mono", defaultWallet to 7L)
        val result = merge(p, doc(AccountSetting.THEME to (JsonPrimitive("grove") to 0L), AccountSetting.DEFAULT_WALLET to (JsonPrimitive("wallet-bank") to 0L)))
        assertEquals("mono", p[theme])
        assertEquals(7L, p[defaultWallet])
        assertNull(result.upload)
        merge(p, doc(AccountSetting.THEME to (JsonPrimitive("aurora") to 500L)))
        assertEquals("aurora", p[theme])
    }

    @Test
    fun `a theme this build doesn't know leaves the phone's own`() {
        val p = mutablePreferencesOf(theme to "mono")
        val result = merge(p, doc(AccountSetting.THEME to (JsonPrimitive("a_newer_theme") to 100L)))
        assertEquals("mono", p[theme])
        assertEquals(0, result.taken)
        // It counts as the account's entry: nothing goes up over it.
        assertNull(result.upload)
    }

    @Test
    fun `a Lucento Pro theme from the account isn't applied where this phone's plan can't pick it`() {
        val p = mutablePreferencesOf(theme to "sunrise")
        merge(p, doc(AccountSetting.THEME to (JsonPrimitive("aurora") to 100L)), lockedThemes = setOf("aurora"))
        assertEquals("sunrise", p[theme])
        // One already chosen here stays (MONETIZATION §6.3).
        val q = mutablePreferencesOf(theme to "aurora")
        merge(q, doc(AccountSetting.THEME to (JsonPrimitive("aurora") to 100L)), lockedThemes = setOf("aurora"))
        assertEquals("aurora", q[theme])
    }

    @Test
    fun `a refused Pro theme doesn't say this phone's change was replaced`() {
        // Pro-gate review 2026-11-23 (F2): the phone keeps its own theme, so "changed on another device" would be untrue.
        val p = mutablePreferencesOf()
        p.change(now = 50) { this[theme] = "midnight" }
        val result = merge(p, doc(AccountSetting.THEME to (JsonPrimitive("aurora") to 100L)), lockedThemes = setOf("aurora"))
        assertEquals("midnight", p[theme])
        assertEquals(0, result.replaced)
        assertEquals(0, result.taken)
        // The account's newer choice counts as seen: the older change here doesn't go up over it...
        assertNull(result.upload)
        assertNull(p[dirty])
        assertEquals(100L, p[at(AccountSetting.THEME)])
        // ...and once the plan allows it (a purchase), the next merge applies it.
        merge(p, doc(AccountSetting.THEME to (JsonPrimitive("aurora") to 100L)))
        assertEquals("aurora", p[theme])
    }

    @Test
    fun `a change that gives way to a newer one from another phone still says so`() {
        val p = mutablePreferencesOf()
        p.change(now = 50) { this[theme] = "midnight" }
        val result = merge(p, doc(AccountSetting.THEME to (JsonPrimitive("grove") to 100L)))
        assertEquals("grove", p[theme])
        assertEquals(1, result.replaced)
    }

    @Test
    fun `a default wallet that isn't on this phone leaves none here`() {
        val p = mutablePreferencesOf(defaultWallet to 7L)
        merge(p, doc(AccountSetting.DEFAULT_WALLET to (JsonPrimitive("wallet-elsewhere") to 100L)))
        assertNull(p[defaultWallet])
    }

    @Test
    fun `a waiting default wallet that has no stable id yet stays waiting rather than going up as none`() {
        val p = mutablePreferencesOf()
        p.change(now = 100) { this[defaultWallet] = 99L }
        val result = merge(p, document = null)
        assertNull(result.upload)
        assertEquals(setOf("default_wallet"), p[dirty])
    }

    @Test
    fun `the check-in's months follow the account and never go back`() {
        val p = mutablePreferencesOf(plannedMonth to "2026-12", recapSeen to "2026-11")
        merge(
            p,
            doc(
                AccountSetting.BUDGET_MONTH_PLANNED to (JsonPrimitive("2026-11") to 500L),
                AccountSetting.RECAP_MONTH_SEEN to (JsonPrimitive("2026-10") to 500L),
            ),
        )
        assertEquals("2026-12", p[plannedMonth])
        assertEquals("2026-11", p[recapSeen])
        // A later month planned on another phone reaches this one: the card stops asking here too.
        val q = mutablePreferencesOf()
        claim(q)
        merge(q, doc(AccountSetting.BUDGET_MONTH_PLANNED to (JsonPrimitive("2027-01") to 600L)))
        assertEquals("2027-01", q[plannedMonth])
    }

    @Test
    fun `marking a month planned or seen only ever moves forward`() {
        val p = mutablePreferencesOf(plannedMonth to "2026-12")
        UserPreferencesRepository.laterMonth(p, plannedMonth, java.time.YearMonth.of(2026, 11))
        assertEquals("2026-12", p[plannedMonth])
        UserPreferencesRepository.laterMonth(p, plannedMonth, java.time.YearMonth.of(2027, 1))
        assertEquals("2027-01", p[plannedMonth])
        UserPreferencesRepository.laterMonth(p, recapSeen, java.time.YearMonth.of(2026, 11))
        assertEquals("2026-11", p[recapSeen])
    }

    @Test
    fun `the check-in's months belong to the ledger, so a sign-out and joining a Shared Space clear them`() {
        val p = mutablePreferencesOf(plannedMonth to "2026-12", recapSeen to "2026-11")
        UserPreferencesRepository.resetAccountSetup(p)
        assertNull(p[plannedMonth])
        assertNull(p[recapSeen])
        val q = mutablePreferencesOf(plannedMonth to "2026-12", recapSeen to "2026-11")
        UserPreferencesRepository.resetForSharedSpaceLedger(q)
        assertNull(q[plannedMonth])
        assertNull(q[recapSeen])
    }

    @Test
    fun `the budget moment never goes back to an earlier month`() {
        val p = mutablePreferencesOf(budgetMonth to "2026-11")
        merge(p, doc(AccountSetting.BUDGET_MONTH_SEEN to (JsonPrimitive("2026-10") to 500L)))
        assertEquals("2026-11", p[budgetMonth])
    }

    @Test
    fun `a phone already linked keeps a newer change of its own, and loses an older one`() {
        val p = mutablePreferencesOf()
        claim(p)
        merge(p, doc(AccountSetting.THEME to (JsonPrimitive("grove") to 100L), AccountSetting.THEME_MODE to (JsonPrimitive("DARK") to 100L)))
        p.change(now = 300) { this[theme] = "mono" }
        p.change(now = 50) { this[themeMode] = "LIGHT" }
        // The theme mode change is dated 101 (after the value it replaced); another phone's is newer.
        val result = merge(p, doc(AccountSetting.THEME to (JsonPrimitive("grove") to 100L), AccountSetting.THEME_MODE to (JsonPrimitive("SYSTEM") to 200L)))
        assertEquals("mono", p[theme])
        assertEquals("SYSTEM", p[themeMode])
        assertEquals(1, result.replaced)
        assertEquals(mapOf(AccountSetting.THEME to 300L), result.upload!!.sent)
    }

    @Test
    fun `a sent change stops waiting unless it changed again while on its way`() {
        val p = mutablePreferencesOf()
        p.change(now = 100) { this[theme] = "grove" }
        p.change(now = 100) { this[cents] = false }
        val upload = merge(p, document = null).upload!!
        p.change(now = 200) { this[cents] = true }
        UserPreferencesRepository.markAccountSettingsSentInto(p, upload)
        assertEquals(setOf("show_cents"), p[dirty])
    }

    @Test
    fun `values seeded into the account count as synced once it accepts them`() {
        val p = mutablePreferencesOf(theme to "aurora")
        val upload = merge(p, document = null).upload!!
        UserPreferencesRepository.markAccountSettingsSentInto(p, upload)
        assertEquals(0L, p[at(AccountSetting.THEME)])
        assertNull(p[dirty])
    }

    /** Account 1's phone with a synced theme and a theme-mode change still waiting. */
    private fun accountOneSignedOut(): MutablePreferences {
        val p = mutablePreferencesOf(theme to "grove", themeMode to "DARK", cents to false)
        claim(p)
        merge(p, doc(AccountSetting.THEME to (JsonPrimitive("grove") to 100L)))
        p.change(now = 300) { this[themeMode] = "LIGHT" }
        UserPreferencesRepository.resetAccountSetup(p)
        return p
    }

    @Test
    fun `after a sign-out the next account never receives what the previous one left, and gets its own`() {
        val p = accountOneSignedOut()
        // The look stays on the phone until the next account says otherwise.
        assertEquals("grove", p[theme])
        assertEquals("LIGHT", p[themeMode])
        assertNull(p[bound])
        claim(p, account = "account-2")
        val result = merge(p, doc(AccountSetting.THEME_MODE to (JsonPrimitive("SYSTEM") to 0L)), account = "account-2")
        assertEquals("SYSTEM", p[themeMode])
        // Account 1's theme, cents and its waiting change go nowhere.
        assertNull(result.upload)
        assertNull(p[dirty])
    }

    @Test
    fun `the same account signing back in gets its settings back, a change still waiting included`() {
        val p = accountOneSignedOut()
        claim(p, account = "account-1")
        assertNull(p[inherited])
        val result = merge(p, doc(AccountSetting.THEME to (JsonPrimitive("grove") to 100L), AccountSetting.THEME_MODE to (JsonPrimitive("DARK") to 100L)))
        assertEquals("LIGHT", p[themeMode])
        assertEquals(mapOf(AccountSetting.THEME_MODE to 300L), result.upload!!.sent)
    }

    @Test
    fun `a sign-out wipe finished again after an interruption still recognises the same account`() {
        val p = accountOneSignedOut()
        UserPreferencesRepository.resetAccountSetup(p)
        claim(p, account = "account-1")
        assertNull(p[inherited])
        assertEquals("account-1", p[bound])
        assertEquals(setOf("theme_mode"), p[dirty])
    }

    @Test
    fun `a change made after a sign-out is the new person's own and goes to their account`() {
        val p = accountOneSignedOut()
        p.change(now = 900) { this[walletsView] = "rows" }
        claim(p, account = "account-2")
        val upload = merge(p, document = null, account = "account-2").upload!!
        assertEquals(mapOf(AccountSetting.WALLETS_VIEW to 900L), upload.sent)
        assertTrue(upload.seeded.isEmpty())
    }

    @Test
    fun `a restore puts what another account left back to the default, and keeps what the account brought`() {
        val p = accountOneSignedOut()
        claim(p, account = "account-2")
        merge(p, doc(AccountSetting.THEME to (JsonPrimitive("mono") to 500L)), account = "account-2")
        UserPreferencesRepository.completeRestoreInto(p, "PHP", com.lucento.app.data.preferences.ThemeMode.SYSTEM, Feature.entries.toSet(), categoriesChosen = true)
        assertEquals("mono", p[theme])
        assertNull("the previous account's cents", p[cents])
        assertNull("the previous account's theme mode", p[themeMode])
        assertNull(p[inherited])
    }

    @Test
    fun `joining a Shared Space forgets only the settings about the old ledger`() {
        val p = mutablePreferencesOf(theme to "grove", defaultWallet to 7L)
        claim(p)
        merge(p, doc(AccountSetting.THEME to (JsonPrimitive("grove") to 100L), AccountSetting.DEFAULT_WALLET to (JsonPrimitive("wallet-cash") to 100L)))
        UserPreferencesRepository.resetForSharedSpaceLedger(p)
        assertNull(p[defaultWallet])
        assertNull(p[at(AccountSetting.DEFAULT_WALLET)])
        assertEquals(100L, p[at(AccountSetting.THEME)])
        assertEquals("account-1", p[bound])
    }

    @Test
    fun `a Partner's phone neither takes nor sends the ledger's settings, and doesn't keep them waiting`() {
        val p = mutablePreferencesOf(defaultWallet to 7L, budgetMonth to "2026-09")
        p.change(now = 100) { this[defaultWallet] = 8L }
        val result = merge(
            p,
            doc(AccountSetting.DEFAULT_WALLET to (JsonPrimitive("wallet-cash") to 100L), AccountSetting.THEME to (JsonPrimitive("grove") to 100L)),
            settings = AccountSetting.PERSONAL,
        )
        assertEquals(8L, p[defaultWallet])
        assertEquals("grove", p[theme])
        assertNull(result.upload)
        assertNull(p[dirty])
    }

    // ------------------------------------------------------------------------------------------ backup (Replace)

    private fun replace(p: MutablePreferences, settings: BackupSettings, theme: ThemeId? = null) =
        p.change(now = 7_000) { UserPreferencesRepository.applyBackupSettingsInto(this, settings, defaultWalletLocalId = null, theme = theme) }

    private val defaultsFile = BackupSettings(
        onboardingComplete = true, themeMode = "SYSTEM", primaryCurrency = "USD", enabledFeatures = Feature.entries.map { it.key },
    )

    @Test
    fun `restoring a file of defaults changes nothing that would reach other phones`() {
        val p = mutablePreferencesOf()
        replace(p, defaultsFile)
        assertNull(p[dirty])
        assertNull(p[themeMode])
        assertNull(p[features])
    }

    @Test
    fun `a restore changes what differs, and writes a default as no value`() {
        val p = mutablePreferencesOf(themeMode to "DARK", cents to false)
        replace(p, defaultsFile.copy(showCents = true, walletsView = "rows", fxRatesOnline = false))
        assertNull(p[themeMode])
        assertNull(p[cents])
        assertEquals("rows", p[walletsView])
        assertEquals(false, p[fx])
        assertEquals(setOf("theme_mode", "show_cents", "wallets_view", "fx_rates_online"), p[dirty])
    }

    @Test
    fun `a version 5 file leaves this phone's customisations as they are`() {
        val p = mutablePreferencesOf(cents to false, walletsView to "rows", theme to "grove")
        replace(p, defaultsFile)
        assertEquals(false, p[cents])
        assertEquals("rows", p[walletsView])
        assertEquals("grove", p[theme])
    }

    @Test
    fun `a file turns Smarter reading off but never on`() {
        val off = mutablePreferencesOf(smart to false)
        replace(off, defaultsFile.copy(smartReading = true))
        assertEquals(false, off[smart])
        val on = mutablePreferencesOf(smart to true)
        replace(on, defaultsFile.copy(smartReading = false))
        assertEquals(false, on[smart])
    }

    @Test
    fun `a file's theme is applied only where the plan allows it, and its budget moment never goes back`() {
        val p = mutablePreferencesOf(budgetMonth to "2026-11")
        replace(p, defaultsFile.copy(themeId = "aurora", acknowledgedBudgetMonth = "2026-09"), theme = null)
        assertNull(p[theme])
        assertEquals("2026-11", p[budgetMonth])
        replace(p, defaultsFile.copy(themeId = "aurora"), theme = ThemeId.AURORA)
        assertEquals("aurora", p[theme])
    }
}
