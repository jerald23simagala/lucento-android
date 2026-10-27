package com.lucento.app.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

/**
 * Everything [SyncRepository] needs from the server, as an interface so the engine runs against an in-memory
 * fake in JVM tests. Rows travel as JSON objects keyed by column name; the DTOs in SyncModels.kt map them.
 */
interface SyncRemote {
    /** The signed-in user's id, after the persisted session has finished loading; null when signed out. */
    suspend fun currentUserId(): String?

    /**
     * Insert-or-update by `id`. Returns `{id, revision}` for each row the server **accepted**; a row missing from the
     * result lost a concurrent conflict (or was a retry of a write already applied) and was left unchanged.
     */
    suspend fun upsert(table: String, rows: List<JsonObject>): List<JsonObject>

    /** Soft delete of one row, based on [baseRevision]. True if the server accepted it (same rule as an edit). */
    suspend fun softDelete(table: String, userId: String, id: String, baseRevision: Long?, atIso: String): Boolean

    /** The user's current server copies of these rows (used to resolve pushes the server rejected). */
    suspend fun fetchByIds(table: String, userId: String, ids: List<String>): List<JsonObject>

    /**
     * One page of the user's rows with `server_updated_at` at or after [sinceIso], ordered by (`server_updated_at`,
     * `id`) and strictly after [after] in that order. Keyset, not offset: a row updated while pages are being read
     * moves to the end instead of shifting the next page and causing a skip.
     */
    suspend fun fetchPage(table: String, userId: String, sinceIso: String, after: PageKey?, limit: Long): List<JsonObject>

    /** Position in (`server_updated_at`, `id`) order. [serverUpdatedAt] is the server's exact string (µs precision). */
    data class PageKey(val serverUpdatedAt: String, val id: String)
}

class SupabaseSyncRemote(private val client: SupabaseClient) : SyncRemote {

    override suspend fun currentUserId(): String? {
        // The session is restored from storage asynchronously at startup; reading it before that completes would
        // report "signed out" to a background SyncWorker running in a fresh process.
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull()?.id
    }

    override suspend fun upsert(table: String, rows: List<JsonObject>): List<JsonObject> {
        if (rows.isEmpty()) return emptyList()
        // server_updated_at and revision are always assigned by the server; never send client values for them.
        return client.from(table).upsert(rows.map { JsonObject(it - SERVER_UPDATED_AT - REVISION) }) {
            onConflict = "id"
            select(Columns.list("id", REVISION))
        }.decodeList<JsonObject>()
    }

    override suspend fun softDelete(table: String, userId: String, id: String, baseRevision: Long?, atIso: String): Boolean =
        client.from(table).update({
            set("deleted_at", atIso)
            set("updated_at", atIso)
            set("base_revision", baseRevision)
        }) {
            select(Columns.list("id"))
            filter {
                eq("user_id", userId)
                eq("id", id)
            }
        }.decodeList<JsonObject>().isNotEmpty()

    override suspend fun fetchByIds(table: String, userId: String, ids: List<String>): List<JsonObject> {
        if (ids.isEmpty()) return emptyList()
        return client.from(table).select(columns = Columns.ALL) {
            filter {
                eq("user_id", userId)
                isIn("id", ids)
            }
        }.decodeList<JsonObject>()
    }

    override suspend fun fetchPage(
        table: String,
        userId: String,
        sinceIso: String,
        after: SyncRemote.PageKey?,
        limit: Long,
    ): List<JsonObject> = client.from(table).select(columns = Columns.ALL) {
        filter {
            eq("user_id", userId)
            gte(SERVER_UPDATED_AT, sinceIso)
            if (after != null) {
                val at = after.serverUpdatedAt.removeSuffix("+00:00").let { if (it.endsWith("Z")) it else "${it}Z" }
                or {
                    gt(SERVER_UPDATED_AT, at)
                    and {
                        eq(SERVER_UPDATED_AT, at)
                        gt("id", after.id)
                    }
                }
            }
        }
        order(SERVER_UPDATED_AT, Order.ASCENDING)
        order("id", Order.ASCENDING)
        limit(limit)
    }.decodeList<JsonObject>()

    companion object {
        const val SERVER_UPDATED_AT = "server_updated_at"
        const val REVISION = "revision"
    }
}
