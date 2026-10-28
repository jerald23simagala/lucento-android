package com.lucento.app.core.crash

/**
 * Synchronous mirror of the crash-reports opt-in.
 *
 * DataStore is loaded after [android.app.Application.onCreate] begins, so a crash in that window was never
 * sent for someone who had already turned reports on. This flag is written whenever the preference is saved.
 * The first time it is missing, startup copies the DataStore value once; a later DataStore read cannot overwrite
 * a value already stored here.
 */
object CrashReportsFlag {
    /** Small durable key/value store. [commit] must finish the write before it returns. */
    interface Store {
        fun getBoolean(key: String, default: Boolean): Boolean

        /** Writes every entry in one durable commit. */
        fun commit(edits: Map<String, Boolean>)
    }

    /** False when the flag has never been written. */
    fun isEnabled(store: Store): Boolean = store.getBoolean(KEY_ENABLED, false)

    /** Records [enabled] and marks the mirror as migrated, so a later DataStore read cannot replace it. */
    fun set(store: Store, enabled: Boolean) {
        store.commit(mapOf(KEY_ENABLED to enabled, KEY_MIGRATED to true))
    }

    /**
     * Copies [dataStoreValue] only when this store has never been set or migrated.
     * Returns the value startup should give to [CrashReporting.install].
     */
    fun migrate(store: Store, dataStoreValue: Boolean): Boolean {
        if (store.getBoolean(KEY_MIGRATED, false)) return isEnabled(store)
        set(store, dataStoreValue)
        return dataStoreValue
    }

    /**
     * Value for [CrashReporting.install]. [readDataStore] runs only when the mirror does not exist yet.
     */
    fun enabledForStartup(store: Store, readDataStore: () -> Boolean): Boolean {
        if (store.getBoolean(KEY_MIGRATED, false)) return isEnabled(store)
        return migrate(store, readDataStore())
    }

    private const val KEY_ENABLED = "crash_reports"
    private const val KEY_MIGRATED = "migrated_from_datastore"
}
