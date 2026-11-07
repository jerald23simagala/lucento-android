package com.lucento.app.data.entitlement

import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.Verifier
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime

/**
 * The server's copy of this person's plan: `public.entitlements` read under their own JWT (RLS returns their rows only;
 * docs/monetization/SERVER_CONTRACT.md §8). Null when signed out or when the request fails — which, by
 * [EntitlementManager]'s rules, changes nothing on the phone. A project without the migration answers 404, which is
 * "unreachable" too: a release build must ship with the production migration (KNOWN_ISSUES).
 */
class SupabaseEntitlementRemote(private val client: SupabaseClient) : EntitlementRemote {
    override suspend fun fetch(): List<Entitlement>? {
        if (client.auth.currentUserOrNull() == null) return null
        val rows = runCatching {
            client.from(TABLE).select(columns = Columns.ALL).decodeList<JsonObject>()
        }.getOrElse { return null }
        return EntitlementRows.parse(rows)
    }

    private companion object {
        const val TABLE = "entitlements"
    }
}

/** `public.entitlements` rows → [Entitlement]s. Pure, so the mapping is a JVM test. A row this version can't read is skipped. */
object EntitlementRows {
    fun parse(rows: List<JsonObject>): List<Entitlement> = rows.mapNotNull(::parseRow)

    fun parseRow(row: JsonObject): Entitlement? = runCatching {
        val grants = (row["grants"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.mapTo(LinkedHashSet()) { Capability.valueOf(it) } ?: return null
        if (grants.isEmpty()) return null
        Entitlement(
            source = EntitlementSource.valueOf(row.string("source") ?: return null),
            status = EntitlementStatus.valueOf(row.string("status") ?: return null),
            grants = grants,
            startsAtMillis = row.string("starts_at")?.let(::millis) ?: 0L,
            expiresAtMillis = row.string("expires_at")?.let(::millis),
            productId = row.string("product_id"),
            basePlanId = row.string("base_plan_id"),
            purchaseTokenHash = row.string("purchase_token_hash"),
            verifiedAtMillis = row.string("verified_at")?.let(::millis) ?: 0L,
            verifiedBy = Verifier.SERVER,
            autoRenewing = (row["auto_renewing"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }.getOrNull()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** Postgres `timestamptz` as PostgREST serialises it ("2027-05-06T04:38:37.920967+00:00"). */
    internal fun millis(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
}
