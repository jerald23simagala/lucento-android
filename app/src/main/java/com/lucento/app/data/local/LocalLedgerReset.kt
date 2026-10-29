package com.lucento.app.data.local

import com.lucento.app.data.media.GoalPhotos
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.sync.SyncPreferences

/**
 * Removes this phone's financial data and the sync binding so the next account starts empty.
 * Income categories are seeded again, the same way a fresh install does. Theme and other device
 * preferences are left alone.
 *
 * QA O-1: a kill after the tables were cleared but before the binding was dropped left the pull cursor and the
 * "synced" state behind, so the next sync pulled only rows newer than the cursor and the ledger stayed empty.
 * Now a durable marker is committed first, the binding and cursor go before the tables, and an interrupted wipe
 * is finished on the next launch ([resumeIfInterrupted]).
 */
class LocalLedgerReset(
    private val database: LucentoDatabase,
    private val syncPreferences: SyncPreferences,
    private val goalPhotos: GoalPhotos,
    private val userPreferences: UserPreferencesRepository,
    private val marker: WipeMarker,
) {
    suspend fun wipe() = runWipe(
        marker = marker,
        clearBinding = { syncPreferences.clearBinding() },
        clearTables = {
            // One Room transaction, then the seed; both run on this coroutine and have committed when it returns.
            database.clearAllTables()
            CategorySeed.insertFreshInstall(database.openHelper.writableDatabase, System.currentTimeMillis())
            // Room only refreshes observed queries for its own transactions; the seed above wrote through the raw
            // helper, so tell observers explicitly that every table changed (no stale ledger kept in a flow).
            database.invalidationTracker.refreshVersionsAsync()
        },
        clearRest = {
            goalPhotos.deleteAll()
            // Smarter reading sends composer text. A wiped phone must not keep that opt-in for the next account.
            userPreferences.setSmartReading(false)
        },
    )

    /** Commits the marker synchronously. Called on the sign-out confirm tap, before any network call (QA R9-01). */
    fun markPending(): Boolean = marker.set()

    fun isPending(): Boolean = marker.isSet()

    /** Finishes a wipe a kill interrupted. Call at launch, before anything syncs. True when one was finished. */
    suspend fun resumeIfInterrupted(): Boolean {
        if (!marker.isSet()) return false
        wipe()
        return true
    }
}

/** A flag that survives a kill: set with `commit()` before a wipe starts, cleared when it has finished. */
interface WipeMarker {
    fun set(): Boolean
    fun clear(): Boolean
    fun isSet(): Boolean

    class Prefs(private val prefs: android.content.SharedPreferences) : WipeMarker {
        override fun set() = prefs.edit().putBoolean(KEY, true).commit()
        override fun clear() = prefs.edit().remove(KEY).commit()
        override fun isSet() = prefs.getBoolean(KEY, false)
        private companion object { const val KEY = "ledger_wipe_pending" }
    }
}

/** The wipe order. Every step is idempotent, so running it again after a kill is safe. */
internal suspend fun runWipe(
    marker: WipeMarker,
    clearBinding: suspend () -> Unit,
    clearTables: suspend () -> Unit,
    clearRest: suspend () -> Unit,
) {
    check(marker.set()) { "Could not record the wipe" }
    clearBinding()
    // clearTables must return only once its transaction has committed (it is not launched or deferred), so the
    // marker below is never cleared while rows could survive a kill (QA R9-02).
    clearTables()
    clearRest()
    check(marker.clear()) { "Could not clear the wipe marker" }
}
