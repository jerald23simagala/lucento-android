package com.lucento.app.data.usage

import com.lucento.app.domain.UsageCounts
import com.lucento.app.domain.UsageEvent
import com.lucento.app.domain.UsageEventDeleter
import com.lucento.app.domain.UsageEventRefused
import com.lucento.app.domain.UsageEventSender
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Sends one count through public.record_usage_event (security audit F-01): clients have no INSERT on usage_events any
 * more, and the function caps what one install, and the whole project, may store in a day. It answers false when a cap
 * dropped the count; that is final, never retried. A count the database refuses (a name it doesn't list, a malformed
 * call) throws [UsageEventRefused], so the queue drops it instead of retrying it forever.
 */
class SupabaseUsageRemote(private val client: SupabaseClient) : UsageEventSender, UsageEventDeleter {
    override suspend fun insert(installId: String, event: UsageEvent, appVersion: String) {
        if (!UsageCounts.allows(event.name)) return
        try {
            client.postgrest.rpc(
                "record_usage_event",
                buildJsonObject {
                    put("p_install_id", installId)
                    put("p_event", event.name)
                    put("p_app_version", appVersion.take(UsageCounts.MAX_APP_VERSION))
                    put(
                        "p_props",
                        buildJsonObject {
                            UsageCounts.props(event.props).forEach { (key, value) -> put(key, value) }
                        },
                    )
                },
            )
        } catch (e: PostgrestRestException) {
            if (UsageCounts.isRefusal(e.code)) throw UsageEventRefused(e.code)
            throw e
        }
    }

    override suspend fun delete(installId: String) {
        client.postgrest.rpc(
            "delete_usage_events",
            buildJsonObject { put("p_install_id", installId) },
        )
    }
}
