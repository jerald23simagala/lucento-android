package com.lucento.app.data.sharedspace

import com.lucento.app.domain.SpaceRole
import com.lucento.app.domain.displayNameOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** A Partner of a Shared Space, as the database reports them (supabase/migrations/20261105120000_lucento_shared.sql). */
@Serializable
data class SpaceMember(val id: String, val label: String, val email: String, val joinedAt: String?) {
    val displayName: String get() = displayNameOf(label, email) ?: "Partner"
}

enum class InviteStatus {
    PENDING, ACCEPTED, DECLINED, REVOKED, EXPIRED;

    companion object {
        fun fromKey(key: String?): InviteStatus = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: PENDING
    }
}

/** An invitation the Owner sent. */
@Serializable
data class SpaceInvite(val id: String, val email: String, val status: InviteStatus, val createdAt: String?, val expiresAt: String?)

/** An invitation addressed to the signed-in account's verified email. */
@Serializable
data class ReceivedInvitation(
    val id: String,
    val ownerId: String,
    val inviterLabel: String,
    val inviterEmail: String,
    val createdAt: String?,
    val expiresAt: String?,
) {
    val inviterName: String get() = displayNameOf(inviterLabel, inviterEmail) ?: "Someone"
}

/**
 * Everything `get_my_shared_space()` returns: the caller's role (null = in no Shared Space), the space and its people,
 * the Owner's recent invitations, and invitations addressed to the caller.
 */
@Serializable
data class SharedSpaceSnapshot(
    val role: SpaceRole? = null,
    val ownerId: String? = null,
    val ownerLabel: String = "",
    val ownerEmail: String = "",
    val maxPartners: Int = 1,
    val members: List<SpaceMember> = emptyList(),
    val invites: List<SpaceInvite> = emptyList(),
    val received: List<ReceivedInvitation> = emptyList(),
) {
    val ownerName: String get() = displayNameOf(ownerLabel, ownerEmail) ?: "the Owner"
    val pendingInvites: List<SpaceInvite> get() = invites.filter { it.status == InviteStatus.PENDING }
    /** Free places: the Partner limit minus Partners and pending invitations (an invitation holds a place). */
    val openPlaces: Int get() = (maxPartners - members.size - pendingInvites.size).coerceAtLeast(0)

    companion object {
        /** Parses the RPC's JSON. Unknown or missing fields fall back to empty values; a role this version doesn't know is none. */
        fun fromJson(json: JsonObject): SharedSpaceSnapshot = SharedSpaceSnapshot(
            role = json.text("role")?.let { name -> SpaceRole.entries.firstOrNull { it.name == name } },
            ownerId = json.text("owner_id"),
            ownerLabel = json.text("owner_label").orEmpty(),
            ownerEmail = json.text("owner_email").orEmpty(),
            maxPartners = (json["max_partners"] as? JsonPrimitive)?.intOrNull ?: 1,
            members = json.objects("members").mapNotNull { m ->
                SpaceMember(
                    id = m.text("partner_id") ?: return@mapNotNull null,
                    label = m.text("label").orEmpty(),
                    email = m.text("email").orEmpty(),
                    joinedAt = m.text("joined_at"),
                )
            },
            invites = json.objects("invites").mapNotNull { i ->
                SpaceInvite(
                    id = i.text("id") ?: return@mapNotNull null,
                    email = i.text("email").orEmpty(),
                    status = InviteStatus.fromKey(i.text("status")),
                    createdAt = i.text("created_at"),
                    expiresAt = i.text("expires_at"),
                )
            },
            received = json.objects("received").mapNotNull { i ->
                ReceivedInvitation(
                    id = i.text("id") ?: return@mapNotNull null,
                    ownerId = i.text("owner_id") ?: return@mapNotNull null,
                    inviterLabel = i.text("inviter_label").orEmpty(),
                    inviterEmail = i.text("inviter_email").orEmpty(),
                    createdAt = i.text("created_at"),
                    expiresAt = i.text("expires_at"),
                )
            },
        )

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

        private fun JsonObject.objects(key: String): List<JsonObject> =
            (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }
}

/**
 * A refusal from the database, by its code ('lucento_space:<code>' in the migration), or a failure to reach it. Every
 * code has one plain sentence ([message]); none of them carries tokens or request details.
 */
class SharedSpaceException(val code: String, cause: Throwable? = null) : Exception("lucento_space:$code", cause) {
    companion object {
        const val OFFLINE = "offline"
        const val UNAVAILABLE = "unavailable"
        const val NOT_SIGNED_IN = "not_authenticated"

        /** Reads the code out of a database error message such as "lucento_space:space_full". */
        fun codeOf(message: String?): String? = message?.substringAfter("lucento_space:", "")?.takeIf { it.isNotEmpty() }
            ?.takeWhile { it.isLetterOrDigit() || it == '_' }
    }
}

/** One plain sentence per refusal code. [other] names the other person where it reads better ("Alex"). */
fun sharedSpaceMessage(code: String, other: String? = null): String = when (code) {
    "invalid_email" -> "That doesn’t look like an email address."
    "self_invite" -> "That’s your own email. Invite the person you want to share with."
    "already_invited" -> "You’ve already invited that email. The invitation is waiting for them."
    "already_member" -> "That person is already in your Shared Space."
    "space_full" -> "Your Shared Space is full. A Shared Space has room for one partner for now — remove them, or withdraw the pending invitation, to invite someone else."
    "no_space" -> "There’s no Shared Space yet. Create one first."
    "already_partner" -> "This account is already part of a Shared Space. Leave it first."
    "owns_space" -> "You have your own Shared Space with someone in it or an invitation waiting. End it in Lucento Shared before joining another."
    "own_space" -> "That invitation is from your own Shared Space."
    "email_unverified" -> "Confirm your email address first, then try again."
    "invite_not_found" -> "This invitation is no longer available."
    "invite_accepted" -> "This invitation has already been accepted."
    "invite_declined" -> "This invitation was declined. Ask ${other ?: "them"} to send a new one."
    "invite_revoked" -> "${other ?: "The sender"} withdrew this invitation. Ask them to send a new one if you still want to join."
    "invite_expired" -> "This invitation has expired. Ask ${other ?: "them"} to send a new one."
    "partner_not_found" -> "That person is no longer in your Shared Space."
    "not_partner" -> "This account isn’t part of a Shared Space any more."
    "has_partners" -> "Remove your partner before closing the Shared Space."
    SharedSpaceException.NOT_SIGNED_IN -> "Sign in again to use Lucento Shared."
    SharedSpaceException.OFFLINE -> "You’re offline. Try again when you’re connected — nothing was changed."
    SharedSpaceException.UNAVAILABLE -> "Lucento Shared isn’t available on the server yet. Nothing was changed."
    else -> "Something went wrong. Nothing was changed — try again."
}

/** Lowercased and trimmed, the way the database stores invitation emails. Null when it can't be an address. */
fun normalizedEmail(raw: String): String? {
    val email = raw.trim().lowercase()
    return email.takeIf { it.length in 3..254 && EMAIL.matches(it) }
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

internal fun JsonElement?.isJsonNull(): Boolean = this == null || this is JsonNull
