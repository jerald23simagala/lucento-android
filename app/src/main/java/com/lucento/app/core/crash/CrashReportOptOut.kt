package com.lucento.app.core.crash

/**
 * Turns crash reports on or off only after both copies of the choice are durable.
 * The DataStore value is written first, then the synchronous `lucento_crash_reports` mirror
 * (`commit`, not `apply`). Collection changes after that. Opting out then drops unsent reports.
 */
object CrashReportOptOut {
    suspend fun apply(
        enabled: Boolean,
        persistDataStore: suspend (Boolean) -> Unit,
        persistMirror: (Boolean) -> Unit,
        setCollectionEnabled: (Boolean) -> Unit,
        deleteUnsentReports: () -> Unit,
    ) {
        persistDataStore(enabled)
        persistMirror(enabled)
        setCollectionEnabled(enabled)
        if (!enabled) deleteUnsentReports()
    }
}
