package com.lucento.app.domain.settings

import com.lucento.app.domain.settings.AccountSettingsMerge.Decision
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/SYNC.md "Settings": how one phone's settings and the account's document come together, one setting at a time. */
class AccountSettingsMergeTest {

    private fun entry(value: String, at: Long) = SettingEntry(JsonPrimitive(value), at)
    private fun local(value: String?, at: Long?, dirty: Boolean = false) = LocalSetting(value?.let(::JsonPrimitive), at, dirty)
    private fun doc(vararg entries: Pair<AccountSetting, SettingEntry>) =
        JsonObject(entries.associate { (s, e) -> s.key to AccountSettingsDocument.encode(e) })

    @Test
    fun `a change is dated after the value it replaced, even when this phone's clock runs behind`() {
        assertEquals(5_000L, AccountSettingsMerge.changedAt(previous = null, now = 5_000))
        assertEquals(5_000L, AccountSettingsMerge.changedAt(previous = 4_000, now = 5_000))
        // Another phone's value is dated 9 000; this phone's clock says 5 000: the change still comes after it.
        assertEquals(9_001L, AccountSettingsMerge.changedAt(previous = 9_000, now = 5_000))
        assertEquals(Long.MAX_VALUE, AccountSettingsMerge.changedAt(previous = Long.MAX_VALUE, now = 5_000))
    }

    @Test
    fun `with nothing waiting here, the phone takes what the account has`() {
        val d = AccountSettingsMerge.decide(local("sunrise", at = 100), entry("grove", 200), joining = false)
        assertEquals(Decision.Take(entry("grove", 200), replacesLocalChange = false), d)
        // Already the account's value: nothing to do.
        assertEquals(Decision.Keep, AccountSettingsMerge.decide(local("grove", at = 200), entry("grove", 200), joining = false))
    }

    @Test
    fun `an updated phone keeps what it showed against another phone's values from before settings synced`() {
        // QA review 2026-11-22: the first phone to sync after the update must not silently restyle every other phone.
        assertEquals(Decision.Keep, AccountSettingsMerge.decide(local("mono", at = null), entry("grove", 0), joining = false))
        assertEquals(Decision.Keep, AccountSettingsMerge.decide(local(null, at = null), entry("grove", 0), joining = false))
        // A change made anywhere since the update does reach it.
        assertEquals(Decision.Take(entry("grove", 500), replacesLocalChange = false), AccountSettingsMerge.decide(local("mono", at = null), entry("grove", 500), joining = false))
        // A phone joining the account (a restore, a new phone) takes those values.
        assertTrue(AccountSettingsMerge.decide(local("mono", at = null), entry("grove", 0), joining = true) is Decision.Take)
    }

    @Test
    fun `another account's leftover always gives way, and is never sent`() {
        val leftover = LocalSetting(JsonPrimitive("grove"), at = null, dirty = false, inherited = true)
        assertTrue(AccountSettingsMerge.decide(leftover, entry("mono", 0), joining = false) is Decision.Take)
        assertNull(AccountSettingsMerge.upload(server = null, mapOf(AccountSetting.THEME to leftover)))
    }

    @Test
    fun `a change waiting here stays when it is at least as new, and gives way to a newer one`() {
        assertEquals(Decision.Keep, AccountSettingsMerge.decide(local("mono", at = 300, dirty = true), entry("grove", 200), joining = false))
        // A tie keeps this phone's change (it goes up next), so two phones never trade places forever.
        assertEquals(Decision.Keep, AccountSettingsMerge.decide(local("mono", at = 200, dirty = true), entry("grove", 200), joining = false))
        val lost = AccountSettingsMerge.decide(local("mono", at = 100, dirty = true), entry("grove", 200), joining = false)
        assertEquals(Decision.Take(entry("grove", 200), replacesLocalChange = true), lost)
        // The same value from both phones isn't a lost change.
        val same = AccountSettingsMerge.decide(local("grove", at = 100, dirty = true), entry("grove", 200), joining = false)
        assertEquals(Decision.Take(entry("grove", 200), replacesLocalChange = false), same)
    }

    @Test
    fun `a phone joining the account takes the account's settings, even over a change made here`() {
        val d = AccountSettingsMerge.decide(local("mono", at = 900, dirty = true), entry("grove", 0), joining = true)
        // Not reported as a lost change: joining an account means using its settings (the join dialog says so).
        assertEquals(Decision.Take(entry("grove", 0), replacesLocalChange = false), d)
        // A value this phone never synced (no time) is recorded as the account's.
        assertTrue(AccountSettingsMerge.decide(local("grove", at = null), entry("grove", 0), joining = true) is Decision.Take)
    }

    @Test
    fun `the upload carries this phone's changes and fills only what the account has no entry for`() {
        val server = doc(AccountSetting.THEME to entry("grove", 200))
        val upload = AccountSettingsMerge.upload(
            server,
            mapOf(
                AccountSetting.THEME to local("grove", at = 200),
                AccountSetting.THEME_MODE to local("DARK", at = 500, dirty = true),
                AccountSetting.WALLETS_VIEW to local("rows", at = null),
                // No value of its own here (the default applies): not a choice to share.
                AccountSetting.DISPLAY_NAME to local(null, at = null),
            ),
        )!!
        assertEquals(mapOf(AccountSetting.THEME_MODE to 500L), upload.sent)
        assertEquals(mapOf(AccountSetting.WALLETS_VIEW to 0L), upload.seeded)
        assertEquals(entry("grove", 200), AccountSettingsDocument.entry(upload.document, AccountSetting.THEME))
        assertEquals(entry("DARK", 500), AccountSettingsDocument.entry(upload.document, AccountSetting.THEME_MODE))
        assertEquals(entry("rows", 0), AccountSettingsDocument.entry(upload.document, AccountSetting.WALLETS_VIEW))
        assertFalse(upload.document.containsKey(AccountSetting.DISPLAY_NAME.key))
    }

    @Test
    fun `a cleared value goes up as null, so the other phones clear it too`() {
        val upload = AccountSettingsMerge.upload(
            doc(AccountSetting.DISPLAY_NAME to entry("Alex", 100)),
            mapOf(AccountSetting.DISPLAY_NAME to local(null, at = 300, dirty = true)),
        )!!
        assertEquals(SettingEntry(JsonNull, 300), AccountSettingsDocument.entry(upload.document, AccountSetting.DISPLAY_NAME))
    }

    @Test
    fun `nothing goes up when the account already has everything this phone would send`() {
        val server = doc(AccountSetting.THEME to entry("grove", 200))
        assertNull(AccountSettingsMerge.upload(server, mapOf(AccountSetting.THEME to local("sunrise", at = null))))
        assertNull(AccountSettingsMerge.upload(server, mapOf(AccountSetting.THEME to local("grove", at = 200))))
    }

    @Test
    fun `settings a newer version wrote are carried through untouched`() {
        val future = JsonObject(mapOf(AccountSettingsDocument.VALUE to JsonPrimitive("x"), AccountSettingsDocument.AT to JsonPrimitive(9), "by" to JsonPrimitive("phone-2")))
        val server = JsonObject(mapOf("week_starts_on" to future, AccountSetting.THEME.key to AccountSettingsDocument.encode(entry("grove", 1))))
        val upload = AccountSettingsMerge.upload(server, mapOf(AccountSetting.THEME to local("mono", at = 5, dirty = true)))!!
        assertEquals(future, upload.document["week_starts_on"])
    }

    @Test
    fun `an entry this build can't read is never filled over, only replaced by a change made here`() {
        val damaged = JsonObject(mapOf(AccountSetting.THEME.key to JsonPrimitive("not an entry")))
        assertNull(AccountSettingsDocument.entry(damaged, AccountSetting.THEME))
        assertNull(AccountSettingsMerge.upload(damaged, mapOf(AccountSetting.THEME to local("grove", at = null))))
        val changed = AccountSettingsMerge.upload(damaged, mapOf(AccountSetting.THEME to local("grove", at = 7, dirty = true)))!!
        assertEquals(entry("grove", 7), AccountSettingsDocument.entry(changed.document, AccountSetting.THEME))
    }

    @Test
    fun `values are checked before use`() {
        assertTrue(AccountSetting.THEME_MODE.accepts(JsonPrimitive("DARK")))
        assertFalse(AccountSetting.THEME_MODE.accepts(JsonPrimitive("dark")))
        assertTrue(AccountSetting.THEME.accepts(JsonPrimitive("a_newer_theme")))
        assertFalse(AccountSetting.THEME.accepts(JsonPrimitive("Not A Key")))
        assertTrue(AccountSetting.FEATURES.accepts(JsonArray(listOf(JsonPrimitive("budgets"), JsonPrimitive("insights")))))
        assertFalse(AccountSetting.FEATURES.accepts(JsonArray(listOf(JsonPrimitive("budgets"), JsonPrimitive("budgets")))))
        assertFalse(AccountSetting.FEATURES.accepts(JsonPrimitive("budgets")))
        assertTrue(AccountSetting.DISPLAY_NAME.accepts(JsonPrimitive("Alex")))
        assertFalse(AccountSetting.DISPLAY_NAME.accepts(JsonPrimitive("x".repeat(41))))
        assertFalse(AccountSetting.DISPLAY_NAME.accepts(JsonPrimitive("   ")))
        assertTrue(AccountSetting.SHOW_CENTS.accepts(JsonPrimitive(false)))
        assertFalse(AccountSetting.SHOW_CENTS.accepts(JsonPrimitive("false")))
        assertTrue(AccountSetting.BUDGET_MONTH_SEEN.accepts(JsonPrimitive("2026-11")))
        assertFalse(AccountSetting.BUDGET_MONTH_SEEN.accepts(JsonPrimitive("2026-13")))
        // Null is every setting's "no value of its own".
        assertTrue(AccountSetting.entries.all { it.accepts(JsonNull) })
    }

    @Test
    fun `a Partner's phone keeps to the person's own settings`() {
        assertFalse(AccountSetting.DEFAULT_WALLET in AccountSetting.PERSONAL)
        assertFalse(AccountSetting.BUDGET_MONTH_SEEN in AccountSetting.PERSONAL)
        assertTrue(AccountSetting.THEME in AccountSetting.PERSONAL)
        assertEquals(AccountSetting.entries.size - 2, AccountSetting.PERSONAL.size)
    }
}
