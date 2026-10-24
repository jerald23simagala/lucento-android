package com.lucento.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
)

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_preferences")

/**
 * What [SyncRepository] needs from sync preferences, pulled out as an interface so a JVM test can fake it
 * without a real DataStore/Context (see FakeSyncPreferences in app/src/test).
 */
interface SyncPreferencesStore {
    suspend fun isEnabled(): Boolean
    suspend fun lastSyncedAtMillis(): Long?
    suspend fun setLastSyncedAtMillis(millis: Long)
    suspend fun setLastError(message: String)
    suspend fun clearLastError()
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
    }

    val status: Flow<SyncStatus> = context.syncDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            SyncStatus(
                enabled = p[Keys.enabled] ?: false,
                lastSyncedAtMillis = p[Keys.lastSyncedAt],
                lastError = p[Keys.lastError],
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

    private companion object {
        const val MAX_ERROR_LENGTH = 300
    }
}
