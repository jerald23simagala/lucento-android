package com.lucento.app.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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

    /**
     * Whether [table] holds at least one of the user's rows that is not soft-deleted. Used to tell a returning account
     * from a new one; a deleted row means nothing is there to bring back. [kind] narrows `categories` to one kind, and
     * [activeOnly] skips archived rows. Throws on any network or server error — an error is never reported as "no rows".
     */
    suspend fun hasLiveRows(table: String, userId: String, kind: String? = null, activeOnly: Boolean = false): Boolean

    /**
     * Lucento Shared: the Owner's id of the Shared Space the signed-in account is a Partner of, or null when it is none.
     * Throws on any network or server error — a failed request is never reported as "not a Partner".
     */
    suspend fun partnerSpaceOwnerId(): String?

    /**
     * Multicurrency: the main currency of the ledger [ledgerUserId] (the account's own, or the Owner's in a Shared Space)
     * as set on the server, or null when none is set yet. Throws on network or server errors.
     */
    suspend fun mainCurrency(ledgerUserId: String): String? = null

    /** Multicurrency: sets it, once. The server keeps the first value (there is no way to change it). */
    suspend fun setMainCurrency(ledgerUserId: String, code: String) = Unit
}

class SupabaseSyncRemote(private val client: SupabaseClient) : SyncRemote {

    override suspend fun currentUserId(): String? {
        // The session is restored from storage asynchronously at startup; reading it before that completes would
        // report "signed out" to a background SyncWorker running in a fresh process. Bounded, so it can't hang a sync.
        kotlinx.coroutines.withTimeoutOrNull(AUTH_READY_TIMEOUT_MILLIS) { client.auth.awaitInitialization() } ?: throw AuthNotReady()
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

    override suspend fun hasLiveRows(table: String, userId: String, kind: String?, activeOnly: Boolean): Boolean =
        client.from(table).select(columns = Columns.list("id")) {
            filter {
                eq("user_id", userId)
                exact("deleted_at", null)
                if (kind != null) eq("kind", kind)
                if (activeOnly) eq("archived", false)
            }
            limit(1)
        }.decodeList<JsonObject>().isNotEmpty()

    override suspend fun partnerSpaceOwnerId(): String? {
        val me = currentUserId() ?: return null
        // RLS shows a Partner their own membership row (supabase/migrations/20261105120000_lucento_shared.sql).
        return client.from("space_members").select(columns = Columns.list("owner_id")) {
            filter { eq("partner_id", me) }
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()?.get("owner_id")?.jsonPrimitive?.contentOrNull
    }

    override suspend fun mainCurrency(ledgerUserId: String): String? =
        // RLS shows the account's own row and, to a Partner, their Owner's (supabase/migrations/…_multicurrency.sql).
        client.from(LEDGER_SETTINGS).select(columns = Columns.list("main_currency")) {
            filter { eq("user_id", ledgerUserId) }
            limit(1)
        }.decodeList<JsonObject>().firstOrNull()?.get("main_currency")?.jsonPrimitive?.contentOrNull

    override suspend fun setMainCurrency(ledgerUserId: String, code: String) {
        try {
            client.from(LEDGER_SETTINGS).insert(
                kotlinx.serialization.json.buildJsonObject {
                    put("user_id", kotlinx.serialization.json.JsonPrimitive(ledgerUserId))
                    put("main_currency", kotlinx.serialization.json.JsonPrimitive(code))
                },
            )
        } catch (e: io.github.jan.supabase.postgrest.exception.PostgrestRestException) {
            // Another phone of this account set it first (unique user_id): the next cycle reads and adopts that one.
            if (e.code != "23505") throw e
        }
    }

    companion object {
        const val LEDGER_SETTINGS = "ledger_settings"
        const val SERVER_UPDATED_AT = "server_updated_at"
        const val REVISION = "revision"
    }
}

/** How long a sync waits for the stored session to load before reporting a retryable failure. */
private const val AUTH_READY_TIMEOUT_MILLIS = 20_000L
