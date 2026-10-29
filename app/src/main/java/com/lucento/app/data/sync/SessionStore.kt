package com.lucento.app.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Where supabase-kt keeps the signed-in session on this phone: the same SharedPreferences file and key its default
 * session manager uses, so sessions saved by earlier builds still load.
 *
 * The default manager writes through `SharedPreferences.apply()`, which only queues the disk write. A force-stop
 * within about a second of sign-out dropped the queued delete, so the next launch loaded the old session again
 * (QA R6-01). Every write here goes through `commit()`, which returns only after the file is written.
 */
class SessionStore(
    private val prefs: SharedPreferences,
    val key: String,
    /** Keys an earlier Lucento build wrote by mistake (QA R6-13). Deleted with [key] on sign-out. */
    private val legacyKeys: List<String> = emptyList(),
) {

    /** Backing settings for supabase-kt's `SettingsSessionManager`; `commit = true` makes each write durable. */
    val settings: Settings get() = SharedPreferencesSettings(prefs, commit = true)

    /** Deletes the stored session now, on the calling thread. True once the file has been written. */
    fun clearNow(): Boolean = prefs.edit().apply {
        remove(key)
        legacyKeys.forEach(::remove)
    }.commit()

    /**
     * Moves a session that a #6/#7 test build saved under a wrong key back to [key], unless [key] already holds one.
     * The wrong copy is deleted either way, so no refresh token is left behind.
     */
    fun migrateLegacy(): Boolean {
        val stray = legacyKeys.filter { it != key && prefs.contains(it) }
        if (stray.isEmpty()) return true
        val edit = prefs.edit()
        if (!prefs.contains(key)) prefs.getString(stray.first(), null)?.let { edit.putString(key, it) }
        stray.forEach(edit::remove)
        return edit.commit()
    }

    fun hasSession(): Boolean = prefs.contains(key)

    companion object {
        fun forApp(context: Context, supabaseUrl: String): SessionStore = SessionStore(
            prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE),
            key = sessionKey(supabaseUrl),
            legacyKeys = listOf(legacySessionKey(supabaseUrl)),
        ).also { it.migrateLegacy() }
    }
}

/**
 * supabase-kt 3.1.4's default session key, derived exactly as the library does (checked in its bytecode):
 * `SupabaseClientBuilder.build()` keeps `url.split("//").last()` as `supabaseUrl`, and `createDefaultSettingsKey`
 * turns that into `"sb-" + removeSuffix("/").replace('/', '-').replace('.', '-')`, then `"-session"` is appended.
 * So "https://abc.supabase.co" is stored as "sb-abc-supabase-co-session", with no scheme.
 */
internal fun sessionKey(supabaseUrl: String): String =
    "sb-" + supabaseUrl.split("//").last().removeSuffix("/").replace('/', '-').replace('.', '-') + "-session"

/** The wrong key the first R6-01 build used: the scheme was kept ("sb-https:--abc-supabase-co-session"). */
internal fun legacySessionKey(supabaseUrl: String): String =
    "sb-" + supabaseUrl.removeSuffix("/").replace('/', '-').replace('.', '-') + "-session"
