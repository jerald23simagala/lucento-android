package com.lucento.app.data.local

import com.lucento.app.data.preferences.AccountSetupStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Removes this phone's financial data and the sync binding so the next account starts empty.
 * Income categories are seeded again, the same way a fresh install does, and the account's setup is reset so the next
 * account goes through onboarding (F-7, [com.lucento.app.data.preferences.UserPreferencesRepository.resetAccountSetup]).
 * Theme and other device preferences are left alone.
 *
 * QA O-1: a kill after the tables were cleared but before the binding was dropped left the pull cursor and the
 * "synced" state behind, so the next sync pulled only rows newer than the cursor and the ledger stayed empty.
 * Now a durable marker is committed first, the binding and cursor go before the tables, and an interrupted wipe
 * is finished on the next launch ([resumeIfInterrupted]).
 *
 * The Room and file steps are passed in ([clearLedgerTables] in production) so the order and the setup reset are
 * tested on the JVM.
 */
class LocalLedgerReset(
    private val marker: WipeMarker,
    private val clearBinding: suspend () -> Unit,
    private val clearTables: suspend () -> Unit,
    /** Goal pictures and the profile photo: both belong to the account being removed. */
    private val deletePhotos: suspend () -> Unit,
    private val accountSetup: AccountSetupStore,
    /** Lucento Shared: empties every table without seeding income categories (a Shared Space brings its own). */
    private val clearTablesUnseeded: suspend () -> Unit = clearTables,
    /** Lucento Shared: the ledger's own pictures (goal photos) — the person's profile photo stays. */
    private val deleteLedgerPhotos: suspend () -> Unit = deletePhotos,
    /**
     * What else this phone remembers about the account being removed: Lucento Shared's cached space, names and dismissed
     * invitations. Without it the next account briefly saw the previous one's Shared Space (role, people, invitations)
     * until the next refresh, up to five minutes.
     */
    private val forgetAccount: suspend () -> Unit = {},
) {
    suspend fun wipe() = runWipe(
        marker = marker,
        clearBinding = clearBinding,
        clearTables = clearTables,
        clearRest = {
            deletePhotos()
            forgetAccount()
            // Last: this edit sends the app back to onboarding (F-7), which may end the screen that started the wipe.
            accountSetup.resetForNextAccount()
        },
    )

    /**
     * Lucento Shared, joining: replaces this phone's ledger with an empty one bound to a Shared Space ([bind] records the
     * Owner as the environment to sync), ready for its first pull. Nothing is seeded — every category comes from the
     * space, and a Partner's phone could never upload a seeded one. Same order and marker as [wipe]; a kill part-way is
     * finished on the next launch by [resumeIfInterrupted], which clears the binding too and lets onboarding find the
     * membership and restore the space.
     */
    suspend fun switchToSharedSpace(bind: suspend () -> Unit) = runWipe(
        marker = marker,
        clearBinding = {
            clearBinding()
            bind()
        },
        clearTables = clearTablesUnseeded,
        clearRest = {
            deleteLedgerPhotos()
            accountSetup.resetForSharedSpace()
        },
    )

    /**
     * Lucento Shared, leaving or removed: the shared ledger leaves this phone, which goes back through onboarding to the
     * person's own account (restored there if it has data). The person stays signed in; their name and photo stay.
     */
    suspend fun returnToOwnLedger() = runWipe(
        marker = marker,
        clearBinding = clearBinding,
        clearTables = clearTables,
        clearRest = {
            deleteLedgerPhotos()
            accountSetup.resetForOwnLedger()
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

/**
 * Empties every table in one Room transaction and seeds the fresh-install income categories. Returns only once both have
 * committed, which [runWipe] relies on.
 */
fun clearLedgerTables(database: LucentoDatabase, seed: Boolean = true) {
    database.clearAllTables()
    if (seed) CategorySeed.insertFreshInstall(database.openHelper.writableDatabase, System.currentTimeMillis())
    // Room only refreshes observed queries for its own transactions; the seed above wrote through the raw
    // helper, so tell observers explicitly that every table changed (no stale ledger kept in a flow).
    database.invalidationTracker.refreshVersionsAsync()
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

/**
 * The wipe order. Every step is idempotent, so running it again after a kill is safe. Not cancellable: a caller's
 * scope ending mid-wipe (the switch to onboarding can clear the screen that started it) would leave the marker set, and
 * the next launch would then wipe the next account's freshly restored ledger.
 */
internal suspend fun runWipe(
    marker: WipeMarker,
    clearBinding: suspend () -> Unit,
    clearTables: suspend () -> Unit,
    clearRest: suspend () -> Unit,
) = withContext(NonCancellable) {
    check(marker.set()) { "Could not record the wipe" }
    clearBinding()
    // clearTables must return only once its transaction has committed (it is not launched or deferred), so the
    // marker below is never cleared while rows could survive a kill (QA R9-02).
    clearTables()
    clearRest()
    check(marker.clear()) { "Could not clear the wipe marker" }
}
