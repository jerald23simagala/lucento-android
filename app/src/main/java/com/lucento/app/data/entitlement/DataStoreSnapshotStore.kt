package com.lucento.app.data.entitlement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * Its own DataStore file, apart from the account's preferences and the sync binding: a plan belongs to the Google account
 * on this phone, not to the Lucento account, so it stays through sign-out and account switches (like `theme_id`). Android
 * cloud backup and device transfer are off for the whole app (`data_extraction_rules.xml`), so a new phone starts empty
 * and recovers through Play or the Lucento account.
 */
private val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore(name = "entitlement_preferences")

class DataStoreSnapshotStore(private val context: Context) : SnapshotStore {
    private object Keys {
        val payload = stringPreferencesKey("snapshot_json")
        val seal = stringPreferencesKey("snapshot_seal")
    }

    override suspend fun read(): SealedSnapshot? {
        val p = context.entitlementDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
        val payload = p[Keys.payload] ?: return null
        val seal = p[Keys.seal] ?: return null
        return SealedSnapshot(payload, seal)
    }

    override suspend fun write(snapshot: SealedSnapshot) {
        context.entitlementDataStore.edit {
            it[Keys.payload] = snapshot.payload
            it[Keys.seal] = snapshot.seal
        }
    }
}
