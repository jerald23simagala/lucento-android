package com.lucento.app.data.sync

/** In-memory [SyncPreferencesStore] so [SyncRepository] can run as a plain JVM test (no Context/DataStore). */
class FakeSyncPreferences(
    private var enabled: Boolean = false,
    private var lastSyncedAtMillis: Long? = null,
) : SyncPreferencesStore {
    var lastError: String? = null
        private set

    override suspend fun isEnabled(): Boolean = enabled

    override suspend fun lastSyncedAtMillis(): Long? = lastSyncedAtMillis

    override suspend fun setLastSyncedAtMillis(millis: Long) {
        lastSyncedAtMillis = millis
    }

    override suspend fun setLastError(message: String) {
        lastError = message
    }

    override suspend fun clearLastError() {
        lastError = null
    }
}
