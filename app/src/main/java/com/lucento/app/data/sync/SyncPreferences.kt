package com.lucento.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** What the Settings screen shows for cloud sync; also read by [SyncRepository] for its pull cursor. */
data class SyncStatus(
    val enabled: Boolean,
    val lastSyncedAtMillis: Long?,
    val lastError: String?,
    /** P2-6: this phone's unsynced changes replaced by newer ones from another device, until the user dismisses it. */
    val overriddenChanges: Int = 0,
)

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_preferences")

/**
 * What [SyncRepository] needs from sync preferences, pulled out as an interface so a JVM test can fake it
 * without a real DataStore/Context (see FakeSyncPreferences in app/src/test).
 */
interface SyncPreferencesStore {
    suspend fun isEnabled(): Boolean
    /** When this device last finished a sync, by its own clock. For display only — never used to select rows. */
    suspend fun lastSyncedAtMillis(): Long?
    suspend fun setLastSyncedAtMillis(millis: Long)
    suspend fun setLastError(message: String)
    suspend fun clearLastError()
    /** Pull cursor in **server** time (`server_updated_at`), so no device clock decides which rows are new. */
    suspend fun pullCursorIso(): String?
    suspend fun setPullCursorIso(iso: String)
    /** The account this device's rows were first synced with; sync refuses to mix in a different one. */
    suspend fun syncedUserId(): String?
    suspend fun setSyncedUserId(userId: String)
    /**
     * The account whose first pull (and the first-sign-in category tidy-up after it) has finished on this phone. Kept
     * apart from [syncedUserId], which is claimed before anything is pulled so no other account can follow: a first
     * sync that fails half-way leaves the claim but not this, so the retry pulls first again instead of uploading.
     */
    suspend fun initialPullDoneFor(): String?
    suspend fun setInitialPullDoneFor(userId: String)
    /** P2-6: adds to the "changed on another device" count shown to the user (never decreased by sync). */
    suspend fun addOverriddenChanges(count: Int)

    /**
     * Lucento Shared: when this phone holds a Shared Space as a Partner, the Owner's account id — the environment every
     * synced row belongs to — while [syncedUserId] stays the Partner's own account. Null for everyone else (solo users and
     * Owners sync their own environment). Cleared with the rest of the binding whenever the ledger is wiped.
     */
    suspend fun sharedSpaceOwnerId(): String?
    suspend fun setSharedSpaceOwnerId(ownerId: String?)
}

/**
 * The only state cloud sync keeps outside Room (see docs/SYNC.md). Everything here is bookkeeping about
 * *when* sync last ran, never financial data itself — that always lives in, and only in, Room.
 */
class SyncPreferences(private val context: Context) : SyncPreferencesStore {

    private object Keys {
        val enabled = booleanPreferencesKey("cloud_sync_enabled")
        val lastSyncedAt = longPreferencesKey("last_synced_at_millis")
        val lastError = stringPreferencesKey("last_sync_error")
        val pullCursor = stringPreferencesKey("pull_cursor_server_time")
        val syncedUserId = stringPreferencesKey("synced_user_id")
        val initialPullDoneFor = stringPreferencesKey("initial_pull_done_for")
        val overridden = intPreferencesKey("overridden_changes")
        val sharedSpaceOwner = stringPreferencesKey("shared_space_owner_id")
    }

    /** Lucento Shared: the Owner whose Shared Space this phone holds as a Partner, or null. */
    val sharedSpaceOwner: Flow<String?> = context.syncDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.sharedSpaceOwner] }

    override suspend fun sharedSpaceOwnerId(): String? = context.syncDataStore.data.first()[Keys.sharedSpaceOwner]

    override suspend fun setSharedSpaceOwnerId(ownerId: String?) {
        context.syncDataStore.edit { if (ownerId == null) it.remove(Keys.sharedSpaceOwner) else it[Keys.sharedSpaceOwner] = ownerId }
    }

    val status: Flow<SyncStatus> = context.syncDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            SyncStatus(
                enabled = p[Keys.enabled] ?: false,
                lastSyncedAtMillis = p[Keys.lastSyncedAt],
                lastError = p[Keys.lastError],
                overriddenChanges = p[Keys.overridden] ?: 0,
            )
        }

    override suspend fun isEnabled(): Boolean = status.first().enabled

    suspend fun setEnabled(enabled: Boolean) {
        context.syncDataStore.edit { it[Keys.enabled] = enabled }
    }

    override suspend fun lastSyncedAtMillis(): Long? = status.first().lastSyncedAtMillis

    override suspend fun setLastSyncedAtMillis(millis: Long) {
        context.syncDataStore.edit { it[Keys.lastSyncedAt] = millis }
    }

    override suspend fun setLastError(message: String) {
        context.syncDataStore.edit { it[Keys.lastError] = message.take(MAX_ERROR_LENGTH) }
    }

    override suspend fun clearLastError() {
        context.syncDataStore.edit { it.remove(Keys.lastError) }
    }

    override suspend fun pullCursorIso(): String? = context.syncDataStore.data.first()[Keys.pullCursor]

    override suspend fun setPullCursorIso(iso: String) {
        context.syncDataStore.edit { it[Keys.pullCursor] = iso }
    }

    override suspend fun syncedUserId(): String? = context.syncDataStore.data.first()[Keys.syncedUserId]

    override suspend fun setSyncedUserId(userId: String) {
        context.syncDataStore.edit { it[Keys.syncedUserId] = userId }
    }

    override suspend fun initialPullDoneFor(): String? = context.syncDataStore.data.first()[Keys.initialPullDoneFor]

    override suspend fun setInitialPullDoneFor(userId: String) {
        context.syncDataStore.edit { it[Keys.initialPullDoneFor] = userId }
    }

    override suspend fun addOverriddenChanges(count: Int) {
        if (count <= 0) return
        context.syncDataStore.edit { it[Keys.overridden] = (it[Keys.overridden] ?: 0) + count }
    }

    /** The user has seen the "changed on another device" notice, which said [seen]; anything counted since stays. */
    suspend fun clearOverriddenChanges(seen: Int) {
        context.syncDataStore.edit {
            val left = (it[Keys.overridden] ?: 0) - seen
            if (left > 0) it[Keys.overridden] = left else it.remove(Keys.overridden)
        }
    }

    /** Forgets which account this phone synced and the pull cursor. Called when that ledger is wiped. */
    suspend fun clearBinding() {
        context.syncDataStore.edit {
            it[Keys.enabled] = false
            it.remove(Keys.lastSyncedAt)
            it.remove(Keys.lastError)
            it.remove(Keys.pullCursor)
            it.remove(Keys.syncedUserId)
            it.remove(Keys.initialPullDoneFor)
            it.remove(Keys.overridden)
            it.remove(Keys.sharedSpaceOwner)
        }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 300
    }
}
