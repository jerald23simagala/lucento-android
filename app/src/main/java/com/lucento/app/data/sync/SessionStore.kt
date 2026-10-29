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
class SessionStore(private val prefs: SharedPreferences, val key: String) {

    /** Backing settings for supabase-kt's `SettingsSessionManager`; `commit = true` makes each write durable. */
    val settings: Settings get() = SharedPreferencesSettings(prefs, commit = true)

    /** Deletes the stored session now, on the calling thread. True once the file has been written. */
    fun clearNow(): Boolean = prefs.edit().remove(key).commit()

    fun hasSession(): Boolean = prefs.contains(key)

    companion object {
        fun forApp(context: Context, supabaseUrl: String): SessionStore = SessionStore(
            prefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE),
            key = sessionKey(supabaseUrl),
        )
    }
}

/**
 * supabase-kt 3.1.4's default session key (`createDefaultSettingsKey(url) + "-session"`, which is marked internal).
 * Kept identical so a session saved before this change is still found and still deleted.
 */
internal fun sessionKey(supabaseUrl: String): String =
    "sb-" + supabaseUrl.removeSuffix("/").replace('/', '-').replace('.', '-') + "-session"
