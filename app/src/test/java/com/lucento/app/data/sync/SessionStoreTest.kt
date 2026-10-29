package com.lucento.app.data.sync

import android.content.SharedPreferences
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.createDefaultSettingsKey
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA R6-01: a force-stop right after sign-out kept the session, because the delete went through apply().
 * [DiskPrefs] keeps apply() writes in memory until a flush that a force-stop never reaches; [DiskPrefs.kill]
 * drops them, like the process dying.
 */
class SessionStoreTest {

    @Test fun clearNowSurvivesAnImmediateKill() {
        val disk = DiskPrefs(mutableMapOf("sb-x-session" to "token"))
        assertTrue(SessionStore(disk, "sb-x-session").clearNow())
        disk.kill()
        assertFalse(disk.contains("sb-x-session"))
    }

    @Test fun anApplyDeleteIsLostOnAnImmediateKill() {
        // The bug, reproduced: the same delete through apply() does not reach the file.
        val disk = DiskPrefs(mutableMapOf("sb-x-session" to "token"))
        disk.edit().remove("sb-x-session").apply()
        disk.kill()
        assertTrue(disk.contains("sb-x-session"))
    }

    @Test fun supabaseDeleteThroughTheStoreIsDurable() = runTest {
        val disk = DiskPrefs(mutableMapOf("sb-x-session" to "token"))
        val store = SessionStore(disk, "sb-x-session")
        SettingsSessionManager(store.settings, store.key).deleteSession()
        disk.kill()
        assertFalse(store.hasSession())
    }

    @Test fun confirmClearsTheStoredSessionBeforeTheWipeStarts() = runTest {
        val disk = DiskPrefs(mutableMapOf("sb-x-session" to "token"))
        val store = SessionStore(disk, "sb-x-session")
        val auth = AuthRepository(client = null, clearStoredSession = store::clearNow)
        assertTrue(auth.clearStoredSessionNow())
        // Killed before signOutAndWipe ran at all, with the ledger untouched: still signed out on relaunch.
        disk.kill()
        assertFalse(store.hasSession())
    }

    @Test fun aFailingStoreReportsFalseInsteadOfThrowing() {
        val auth = AuthRepository(client = null, clearStoredSession = { error("disk full") })
        assertFalse(auth.clearStoredSessionNow())
    }

    @OptIn(SupabaseInternal::class)
    @Test fun sessionKeyIsTheLibrarysOwnKey() {
        // The library's own derivation: the client keeps the URL without its scheme, then createDefaultSettingsKey.
        val url = "https://abc.supabase.co"
        val client = createSupabaseClient(url, "anon-key") {}
        val libraryKey = createDefaultSettingsKey(client.supabaseUrl) + "-session"
        assertEquals("sb-abc-supabase-co-session", libraryKey)
        assertEquals(libraryKey, sessionKey(url))
        assertEquals(libraryKey, sessionKey("$url/"))
        assertEquals("sb-https:--abc-supabase-co-session", legacySessionKey(url))
    }

    @Test fun aSessionSavedByTheStockLibraryIsStillReadAfterTheUpgrade() = runTest {
        val url = "https://abc.supabase.co"
        val disk = DiskPrefs(mutableMapOf())
        // Before the upgrade: the stock manager, apply()-based settings, the library's key.
        SettingsSessionManager(SharedPreferencesSettings(disk), "sb-abc-supabase-co-session").saveSession(session("old-refresh"))
        disk.flush()
        // After the upgrade: Lucento's store over the same file.
        val store = SessionStore(disk, sessionKey(url), listOf(legacySessionKey(url)))
        assertTrue(store.migrateLegacy())
        val loaded = SettingsSessionManager(store.settings, store.key).loadSession()
        assertEquals("old-refresh", loaded?.refreshToken)
    }

    @Test fun signOutDeletesTheRealKeyAndTheWrongKey() {
        val url = "https://abc.supabase.co"
        val disk = DiskPrefs(mutableMapOf(sessionKey(url) to "a", legacySessionKey(url) to "b"))
        val store = SessionStore(disk, sessionKey(url), listOf(legacySessionKey(url)))
        assertTrue(store.clearNow())
        disk.kill()
        assertFalse(disk.contains(sessionKey(url)))
        assertFalse(disk.contains(legacySessionKey(url)))
    }

    @Test fun aSessionUnderTheWrongKeyMovesToTheRealKey() {
        val url = "https://abc.supabase.co"
        val disk = DiskPrefs(mutableMapOf(legacySessionKey(url) to "json"))
        val store = SessionStore(disk, sessionKey(url), listOf(legacySessionKey(url)))
        assertTrue(store.migrateLegacy())
        disk.kill()
        assertEquals("json", disk.getString(sessionKey(url), null))
        assertFalse(disk.contains(legacySessionKey(url)))
    }

    @Test fun theWrongKeyNeverOverwritesTheRealSession() {
        val url = "https://abc.supabase.co"
        val disk = DiskPrefs(mutableMapOf(sessionKey(url) to "current", legacySessionKey(url) to "stale"))
        SessionStore(disk, sessionKey(url), listOf(legacySessionKey(url))).migrateLegacy()
        assertEquals("current", disk.getString(sessionKey(url), null))
        assertFalse(disk.contains(legacySessionKey(url)))
    }

    private fun session(refresh: String) = UserSession(
        accessToken = "access",
        refreshToken = refresh,
        expiresIn = 3600,
        tokenType = "bearer",
    )

    @Test fun signOutClearsTheSessionBeforeSyncStopsAndTheWipe() = runTest {
        val order = mutableListOf<String>()
        val boundary = AccountBoundary(
            currentUserId = { "u" }, syncedUserId = { "u" }, hasUnsyncedChanges = { false },
            prepare = { order += "prepare" }, wipe = { order += "wipe" }, signOut = { order += "session" }, exclusive = { it() },
        )
        boundary.signOutAndWipe()
        assertEquals(listOf("session", "prepare", "wipe"), order)
    }

    /** SharedPreferences whose commit() writes the "file" and whose apply() waits for a flush. */
    private class DiskPrefs(private val file: MutableMap<String, Any?>) : SharedPreferences {
        private val pending = mutableMapOf<String, Any?>()
        private val pendingRemovals = mutableSetOf<String>()
        private fun view(): Map<String, Any?> = (file - pendingRemovals) + pending

        fun kill() { pending.clear(); pendingRemovals.clear() }

        /** The queued apply() writes reach the file, as they do when the process lives long enough. */
        fun flush() {
            pendingRemovals.forEach(file::remove)
            file.putAll(pending)
            kill()
        }

        override fun getAll(): MutableMap<String, *> = view().toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = view()[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = view()[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = view()[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = view()[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = view()[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = view().containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val puts = mutableMapOf<String, Any?>()
            val removes = mutableSetOf<String>()
            override fun putString(key: String, value: String?) = apply { puts[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { puts[key] = values }
            override fun putInt(key: String, value: Int) = apply { puts[key] = value }
            override fun putLong(key: String, value: Long) = apply { puts[key] = value }
            override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
            override fun remove(key: String) = apply { removes += key; puts.remove(key) }
            override fun clear() = apply { removes += view().keys }
            override fun commit(): Boolean {
                removes.forEach { file.remove(it); pending.remove(it); pendingRemovals.remove(it) }
                file.putAll(puts)
                return true
            }
            override fun apply() {
                pendingRemovals += removes
                removes.forEach { pending.remove(it) }
                pending.putAll(puts)
            }
        }
    }
}
