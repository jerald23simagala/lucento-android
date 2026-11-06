package com.lucento.app.data.sharedspace

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * What this phone last learned about Lucento Shared, so screens know the role, the people's names and any invitation
 * offline. Never financial data, and never the source of truth for access: a Partner's phone is identified by the sync
 * binding ([com.lucento.app.data.sync.SyncPreferencesStore.sharedSpaceOwnerId]); the database decides everything else.
 */
data class SharedSpaceCache(
    val snapshot: SharedSpaceSnapshot? = null,
    val refreshedAtMillis: Long? = null,
    /** Account id → name for everyone this phone has seen in its space, kept after they leave so history keeps names. */
    val knownNames: Map<String, String> = emptyMap(),
    val dismissedInvitations: Set<String> = emptySet(),
    /** Shown once, in onboarding, after the shared ledger left this phone. */
    val notice: SpaceNotice? = null,
)

/** Why the shared ledger left this phone, for onboarding. [unsyncedLost]: changes made here never reached the space. */
data class SpaceNotice(val kind: Kind, val ownerName: String, val unsyncedLost: Boolean = false) {
    enum class Kind { REMOVED, LEFT }
}

/** The notice in plain words. It speaks only about this phone: nothing can be promised about other phones or exported files. */
val SpaceNotice.title: String
    get() = when (kind) {
        SpaceNotice.Kind.LEFT -> "You left $ownerName’s Shared Space"
        SpaceNotice.Kind.REMOVED -> "$ownerName’s Shared Space is no longer shared with you"
    }

val SpaceNotice.body: String
    get() {
        val what = when (kind) {
            SpaceNotice.Kind.LEFT -> "$ownerName’s finances are no longer on this phone, and everything you added stays in the Shared Space."
            SpaceNotice.Kind.REMOVED -> "This account isn’t part of it any more, so $ownerName’s finances were removed from this phone."
        }
        val lost = if (unsyncedLost) " Changes made on this phone that hadn’t synced yet couldn’t be kept." else ""
        return "$what$lost Your own Lucento comes back from your account next — or, if it’s empty, you can set it up now."
    }

interface SharedSpaceStore {
    val cache: Flow<SharedSpaceCache>
    suspend fun current(): SharedSpaceCache = cache.first()
    /** Saves a fresh snapshot and adds its people to [SharedSpaceCache.knownNames]. */
    suspend fun saveSnapshot(snapshot: SharedSpaceSnapshot, atMillis: Long)
    suspend fun rememberName(id: String, name: String)
    suspend fun dismissInvitation(id: String)
    suspend fun setNotice(notice: SpaceNotice?)
    /** Forgets the space (snapshot and names) when the ledger switches; invitations dismissed and the notice stay. */
    suspend fun clearSpace()
    /** Forgets everything — for a different account signing in, or signing out: nothing here belongs to the next one. */
    suspend fun clearAll()
}

/** The names a snapshot introduces: the Owner and every Partner. */
fun SharedSpaceSnapshot.names(): Map<String, String> = buildMap {
    ownerId?.let { put(it, ownerName) }
    members.forEach { put(it.id, it.displayName) }
}

private val Context.sharedSpaceDataStore: DataStore<Preferences> by preferencesDataStore(name = "shared_space")

class DataStoreSharedSpaceStore(private val context: Context) : SharedSpaceStore {
    private object Keys {
        val snapshot = stringPreferencesKey("snapshot")
        val refreshedAt = longPreferencesKey("refreshed_at")
        val names = stringPreferencesKey("known_names")
        val dismissed = stringSetPreferencesKey("dismissed_invitations")
        val notice = stringPreferencesKey("notice")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val namesSerializer = MapSerializer(String.serializer(), String.serializer())

    override val cache: Flow<SharedSpaceCache> = context.sharedSpaceDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            SharedSpaceCache(
                snapshot = p[Keys.snapshot]?.let { runCatching { json.decodeFromString(SharedSpaceSnapshot.serializer(), it) }.getOrNull() },
                refreshedAtMillis = p[Keys.refreshedAt],
                knownNames = p[Keys.names]?.let { runCatching { json.decodeFromString(namesSerializer, it) }.getOrNull() }.orEmpty(),
                dismissedInvitations = p[Keys.dismissed].orEmpty(),
                notice = p[Keys.notice]?.let(::decodeNotice),
            )
        }

    override suspend fun saveSnapshot(snapshot: SharedSpaceSnapshot, atMillis: Long) {
        context.sharedSpaceDataStore.edit { p ->
            p[Keys.snapshot] = json.encodeToString(SharedSpaceSnapshot.serializer(), snapshot)
            p[Keys.refreshedAt] = atMillis
            val known = p[Keys.names]?.let { runCatching { json.decodeFromString(namesSerializer, it) }.getOrNull() }.orEmpty()
            p[Keys.names] = json.encodeToString(namesSerializer, known + snapshot.names())
        }
    }

    override suspend fun rememberName(id: String, name: String) {
        context.sharedSpaceDataStore.edit { p ->
            val known = p[Keys.names]?.let { runCatching { json.decodeFromString(namesSerializer, it) }.getOrNull() }.orEmpty()
            p[Keys.names] = json.encodeToString(namesSerializer, known + (id to name))
        }
    }

    override suspend fun dismissInvitation(id: String) {
        context.sharedSpaceDataStore.edit { it[Keys.dismissed] = it[Keys.dismissed].orEmpty() + id }
    }

    override suspend fun setNotice(notice: SpaceNotice?) {
        context.sharedSpaceDataStore.edit { if (notice == null) it.remove(Keys.notice) else it[Keys.notice] = encodeNotice(notice) }
    }

    override suspend fun clearSpace() {
        context.sharedSpaceDataStore.edit {
            it.remove(Keys.snapshot)
            it.remove(Keys.refreshedAt)
            it.remove(Keys.names)
        }
    }

    override suspend fun clearAll() {
        context.sharedSpaceDataStore.edit { it.clear() }
    }

    /** "KIND|lost|name": the name last, since it may contain anything. */
    private fun encodeNotice(notice: SpaceNotice) = "${notice.kind.name}|${if (notice.unsyncedLost) 1 else 0}|${notice.ownerName}"

    private fun decodeNotice(raw: String): SpaceNotice? {
        val parts = raw.split('|', limit = 3)
        val kind = runCatching { SpaceNotice.Kind.valueOf(parts[0]) }.getOrNull() ?: return null
        return SpaceNotice(kind, ownerName = parts.getOrElse(2) { "" }, unsyncedLost = parts.getOrNull(1) == "1")
    }
}

/** In-memory [SharedSpaceStore] (JVM tests and previews). */
class InMemorySharedSpaceStore(initial: SharedSpaceCache = SharedSpaceCache()) : SharedSpaceStore {
    private val state = MutableStateFlow(initial)
    override val cache: Flow<SharedSpaceCache> = state

    override suspend fun saveSnapshot(snapshot: SharedSpaceSnapshot, atMillis: Long) {
        state.value = state.value.copy(snapshot = snapshot, refreshedAtMillis = atMillis, knownNames = state.value.knownNames + snapshot.names())
    }

    override suspend fun rememberName(id: String, name: String) {
        state.value = state.value.copy(knownNames = state.value.knownNames + (id to name))
    }

    override suspend fun dismissInvitation(id: String) {
        state.value = state.value.copy(dismissedInvitations = state.value.dismissedInvitations + id)
    }

    override suspend fun setNotice(notice: SpaceNotice?) {
        state.value = state.value.copy(notice = notice)
    }

    override suspend fun clearSpace() {
        state.value = state.value.copy(snapshot = null, refreshedAtMillis = null, knownNames = emptyMap())
    }

    override suspend fun clearAll() {
        state.value = SharedSpaceCache()
    }
}
