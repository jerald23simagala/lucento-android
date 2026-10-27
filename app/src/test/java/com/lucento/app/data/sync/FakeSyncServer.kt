package com.lucento.app.data.sync

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * In-memory stand-in for the Supabase project, reproducing what supabase/migrations/ enforce (migration 5 included):
 * rows isolated per user (RLS); an id owned by another user can't be written; user_id can't change; every insert
 * starts at revision 1; an update whose base_revision equals the stored revision is a causal successor and is
 * accepted whatever its edit time, any other update only if its edit time is strictly later (ties keep the stored
 * row); updated_at more than 5 minutes ahead is clamped before comparing; every accepted write gets
 * server_updated_at from the **server's** clock. Upserts return only the rows that were accepted.
 */
class FakeSyncServer(var serverNow: Long = 1_000_000_000_000L) {
    /** table -> id -> row */
    val tables = mutableMapOf<String, MutableMap<String, JsonObject>>()
    var failNextFetch = false
    /** Runs before each page read, e.g. to simulate another device writing while this one is paging. */
    var beforePage: ((table: String, pageIndex: Int) -> Unit)? = null
    private var pageCalls = 0

    fun rows(table: String, userId: String): List<JsonObject> =
        tables[table].orEmpty().values.filter { it.str("user_id") == userId }

    fun live(table: String, userId: String): List<JsonObject> = rows(table, userId).filter { it["deleted_at"].isNullish() }

    /** Server-side write path shared by upsert and soft delete; returns the stored row if accepted. */
    fun write(table: String, userId: String, incoming: JsonObject): JsonObject? {
        require(incoming.str("user_id") == userId) { "new row violates row-level security policy" }
        val t = tables.getOrPut(table) { mutableMapOf() }
        val id = incoming.str("id")
        val existing = t[id]
        var row = incoming
        if (millis(row, "updated_at") > serverNow + 5 * 60_000) row = JsonObject(row + ("updated_at" to JsonPrimitive(SyncTime.toIso(serverNow))))
        val revision = if (existing == null) {
            1L
        } else {
            require(existing.str("user_id") == userId) { "new row violates row-level security policy" }
            val stored = existing.long("revision")!!
            val causal = row.long("base_revision") == stored
            if (!causal && millis(row, "updated_at") <= millis(existing, "updated_at")) return null
            stored + 1
        }
        serverNow += 1
        val merged = (existing ?: JsonObject(emptyMap())) + row + mapOf(
            "revision" to JsonPrimitive(revision),
            "base_revision" to JsonNull,
            "server_updated_at" to JsonPrimitive(SyncTime.toIso(serverNow)),
        )
        return JsonObject(merged).also { t[id] = it }
    }

    fun remote(userId: String?): SyncRemote = object : SyncRemote {
        override suspend fun currentUserId(): String? = userId

        override suspend fun upsert(table: String, rows: List<JsonObject>): List<JsonObject> =
            rows.mapNotNull { incoming ->
                write(table, userId!!, JsonObject(incoming - "revision" - "server_updated_at"))
                    ?.let { JsonObject(mapOf("id" to it.getValue("id"), "revision" to it.getValue("revision"))) }
            }

        override suspend fun softDelete(table: String, userId: String, id: String, baseRevision: Long?, atIso: String): Boolean {
            val existing = tables[table]?.get(id)?.takeIf { it.str("user_id") == userId } ?: return false
            val patch = existing + mapOf(
                "deleted_at" to JsonPrimitive(atIso),
                "updated_at" to JsonPrimitive(atIso),
                "base_revision" to (baseRevision?.let(::JsonPrimitive) ?: JsonNull),
            )
            return write(table, userId, JsonObject(patch)) != null
        }

        override suspend fun fetchByIds(table: String, userId: String, ids: List<String>): List<JsonObject> =
            rows(table, userId).filter { it.str("id") in ids }

        override suspend fun fetchPage(
            table: String,
            userId: String,
            sinceIso: String,
            after: SyncRemote.PageKey?,
            limit: Long,
        ): List<JsonObject> {
            beforePage?.invoke(table, pageCalls++)
            if (failNextFetch) {
                failNextFetch = false
                error("connection reset")
            }
            val since = SyncTime.fromIso(sinceIso)
            return rows(table, userId)
                .filter { millis(it, "server_updated_at") >= since }
                .sortedWith(compareBy<JsonObject>({ millis(it, "server_updated_at") }, { it.str("id") }))
                .filter { row ->
                    after == null || millis(row, "server_updated_at") > SyncTime.fromIso(after.serverUpdatedAt) ||
                        (millis(row, "server_updated_at") == SyncTime.fromIso(after.serverUpdatedAt) && row.str("id") > after.id)
                }
                .take(limit.toInt())
        }
    }

    private fun millis(row: JsonObject, key: String) = SyncTime.fromIso(row.str(key))
}

fun JsonObject.str(key: String): String = this.getValue(key).jsonPrimitive.content
private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun kotlinx.serialization.json.JsonElement?.isNullish() = this == null || this is JsonNull
