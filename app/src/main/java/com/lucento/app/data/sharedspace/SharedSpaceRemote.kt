package com.lucento.app.data.sharedspace

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The Lucento Shared RPCs (supabase/migrations/20261105120000_lucento_shared.sql), as an interface so the repository runs
 * against a fake in JVM tests. Every call acts as the signed-in account — the database takes the caller from the session,
 * never from an argument — and failures are [SharedSpaceException]s carrying the database's refusal code.
 */
interface SharedSpaceRemote {
    suspend fun snapshot(): SharedSpaceSnapshot
    suspend fun createSpace(ownerLabel: String)
    suspend fun setMyLabel(label: String)
    /** Returns the new invitation's id. */
    suspend fun invite(email: String): String
    suspend fun revokeInvite(inviteId: String)
    /** Joins the space; returns its Owner's id (the Shared Space id). Atomic on the server. */
    suspend fun acceptInvite(inviteId: String, myLabel: String): String
    suspend fun declineInvite(inviteId: String)
    suspend fun removePartner(partnerId: String)
    /** Leaves the space; returns its Owner's id. */
    suspend fun leave(): String
    suspend fun closeSpace()
}

class SupabaseSharedSpaceRemote(private val client: SupabaseClient) : SharedSpaceRemote {

    override suspend fun snapshot(): SharedSpaceSnapshot =
        SharedSpaceSnapshot.fromJson(Json.parseToJsonElement(call("get_my_shared_space")).jsonObject)

    override suspend fun createSpace(ownerLabel: String) {
        call("create_shared_space", buildJsonObject { put("p_owner_label", ownerLabel) })
    }

    override suspend fun setMyLabel(label: String) {
        call("set_my_space_label", buildJsonObject { put("p_label", label) })
    }

    override suspend fun invite(email: String): String =
        uuidResult(call("create_space_invite", buildJsonObject { put("p_email", email) }))

    override suspend fun revokeInvite(inviteId: String) {
        call("revoke_space_invite", buildJsonObject { put("p_invite_id", inviteId) })
    }

    override suspend fun acceptInvite(inviteId: String, myLabel: String): String =
        uuidResult(call("accept_space_invite", buildJsonObject {
            put("p_invite_id", inviteId)
            put("p_partner_label", myLabel)
        }))

    override suspend fun declineInvite(inviteId: String) {
        call("decline_space_invite", buildJsonObject { put("p_invite_id", inviteId) })
    }

    override suspend fun removePartner(partnerId: String) {
        call("remove_space_partner", buildJsonObject { put("p_partner_id", partnerId) })
    }

    override suspend fun leave(): String = uuidResult(call("leave_shared_space"))

    override suspend fun closeSpace() {
        call("close_shared_space")
    }

    /** One RPC, after the stored session has loaded (an early call would go out signed out). Returns the raw body. */
    private suspend fun call(function: String, parameters: JsonObject? = null): String {
        try {
            withTimeoutOrNull(AUTH_WAIT_MS) { client.auth.awaitInitialization() }
            if (client.auth.currentSessionOrNull() == null) throw SharedSpaceException(SharedSpaceException.NOT_SIGNED_IN)
            val result = if (parameters == null) client.postgrest.rpc(function) else client.postgrest.rpc(function, parameters)
            return result.data
        } catch (e: CancellationException) {
            throw e
        } catch (e: SharedSpaceException) {
            throw e
        } catch (e: RestException) {
            // Only the database's own code is kept: the exception's message embeds request headers (see SyncModels).
            val code = SharedSpaceException.codeOf(e.error) ?: SharedSpaceException.codeOf(e.description)
                ?: if (e.statusCode == 404) SharedSpaceException.UNAVAILABLE else "server"
            throw SharedSpaceException(code)
        } catch (e: HttpRequestException) {
            throw SharedSpaceException(SharedSpaceException.OFFLINE)
        } catch (e: Exception) {
            throw SharedSpaceException(if (com.lucento.app.data.sync.isTimeout(e)) SharedSpaceException.OFFLINE else "server")
        }
    }

    /** A uuid-returning function's body is a JSON string. */
    private fun uuidResult(body: String): String =
        (Json.parseToJsonElement(body) as? JsonPrimitive)?.contentOrNull ?: throw SharedSpaceException("server")

    private companion object {
        const val AUTH_WAIT_MS = 10_000L
    }
}
