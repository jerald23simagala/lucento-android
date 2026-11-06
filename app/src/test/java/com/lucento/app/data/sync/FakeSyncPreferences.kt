package com.lucento.app.data.sync

/** In-memory [SyncPreferencesStore] so [SyncRepository] can run as a plain JVM test (no Context/DataStore). */
class FakeSyncPreferences(
    private var enabled: Boolean = false,
    private var lastSyncedAtMillis: Long? = null,
) : SyncPreferencesStore {
    var lastError: String? = null
        private set
    var pullCursor: String? = null
    var syncedUser: String? = null

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

    override suspend fun pullCursorIso(): String? = pullCursor

    override suspend fun setPullCursorIso(iso: String) {
        pullCursor = iso
    }

    override suspend fun syncedUserId(): String? = syncedUser

    override suspend fun setSyncedUserId(userId: String) {
        syncedUser = userId
    }

    var initialPullDone: String? = null

    override suspend fun initialPullDoneFor(): String? = initialPullDone

    override suspend fun setInitialPullDoneFor(userId: String) {
        initialPullDone = userId
    }

    var overriddenChanges = 0

    override suspend fun addOverriddenChanges(count: Int) {
        overriddenChanges += count
    }

    /** Lucento Shared: the Owner whose space this fake phone holds as a Partner. */
    var sharedSpaceOwner: String? = null

    override suspend fun sharedSpaceOwnerId(): String? = sharedSpaceOwner

    override suspend fun setSharedSpaceOwnerId(ownerId: String?) {
        sharedSpaceOwner = ownerId
    }
}
