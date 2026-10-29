package com.lucento.app.data.usage

import com.lucento.app.domain.UsageCounts
import com.lucento.app.domain.UsageEvent
import com.lucento.app.domain.UsageEventDeleter
import com.lucento.app.domain.UsageEventSender
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Inserts one row and does not ask the server to return it. The anon and authenticated roles
 * have no select privilege on usage_events, so a returning representation would fail.
 */
class SupabaseUsageRemote(private val client: SupabaseClient) : UsageEventSender, UsageEventDeleter {
    override suspend fun insert(installId: String, event: UsageEvent, appVersion: String) {
        if (!UsageCounts.allows(event.name)) return
        client.from("usage_events").insert(
            buildJsonObject {
                put("install_id", installId)
                put("event", event.name)
                put("app_version", appVersion.take(UsageCounts.MAX_APP_VERSION))
                put(
                    "props",
                    buildJsonObject {
                        UsageCounts.props(event.props).forEach { (key, value) -> put(key, value) }
                    },
                )
            },
        )
    }

    override suspend fun delete(installId: String) {
        client.postgrest.rpc(
            "delete_usage_events",
            buildJsonObject { put("p_install_id", installId) },
        )
    }
}
