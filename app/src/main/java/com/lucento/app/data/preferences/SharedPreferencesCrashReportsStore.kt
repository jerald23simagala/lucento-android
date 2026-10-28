package com.lucento.app.data.preferences

import android.content.Context
import com.lucento.app.core.crash.CrashReportsFlag

/**
 * SharedPreferences backing for [CrashReportsFlag]. [commit] uses `commit()` rather than `apply()`,
 * so the opt-in is on disk before the caller returns.
 */
class SharedPreferencesCrashReportsStore(context: Context) : CrashReportsFlag.Store {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun commit(edits: Map<String, Boolean>) {
        val editor = prefs.edit()
        for ((key, value) in edits) editor.putBoolean(key, value)
        editor.commit()
    }

    companion object {
        const val PREFS_NAME = "lucento_crash_reports"
    }
}
