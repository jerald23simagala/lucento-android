package com.lucento.app.domain

/**
 * Lucento Shared (docs/shared/PRODUCT_SPEC.md): one Owner's financial environment shared with Partners. The Shared
 * Space id is the Owner's account id. These are the internal role names; screens use people's names wherever they can.
 */
enum class SpaceRole { OWNER, PARTNER }

/**
 * A wallet's organizational label inside a Shared Space: Shared, the Owner's personal wallet, or a Partner's. Never an
 * access rule — everyone in the space sees every wallet and can record against it. Null on a wallet means it was never
 * classified, which reads as [SHARED] inside a space and is not shown at all outside one.
 */
enum class WalletClassification {
    SHARED,
    OWNER,
    PARTNER;

    companion object {
        val NAMES: Set<String> = entries.map { it.name }.toSet()

        /** Null for anything this version doesn't know (a pulled value is then read as unclassified, never as an error). */
        fun fromKey(key: String?): WalletClassification? = entries.firstOrNull { it.name == key }
    }
}

/**
 * What this phone may change. Solo users and Owners manage everything. A Partner records, edits and deletes
 * transactions but doesn't change the financial structure (wallets, categories, budgets, goals, schedules, debts) —
 * enforced by the database for every request; the app hides those controls so nothing is offered that would be refused.
 */
data class SharedAccess(
    val role: SpaceRole? = null,
    /** The Owner's display name, for "Only Alex can change wallets". Null outside a space. */
    val ownerName: String? = null,
    /** On the Owner's phone, the Partner's display name (for "Pat’s" wallets); null when there is none or unknown. */
    val partnerName: String? = null,
) {
    val inSpace: Boolean get() = role != null
    val isPartner: Boolean get() = role == SpaceRole.PARTNER
    val canManageStructure: Boolean get() = role != SpaceRole.PARTNER

    /** "Alex" or, without a name, "the Owner" — for sentences about who manages the structure. */
    val ownerDisplay: String get() = ownerName?.takeIf { it.isNotBlank() } ?: "the Owner"

    /**
     * The other person when this ledger really is shared — the Owner on a Partner's phone, the Partner on the Owner's —
     * for the "Shared with …" indicator. Null when solo, and for an Owner nobody has joined yet (an invitation alone
     * shares nothing).
     */
    val sharedWith: String?
        get() = when (role) {
            SpaceRole.PARTNER -> ownerDisplay
            SpaceRole.OWNER -> partnerName?.takeIf { it.isNotBlank() }
            null -> null
        }

    companion object {
        val Solo = SharedAccess()
    }
}

/** "Added by …" and "Edited by …" for one transaction; either can be null. */
data class Attribution(val addedBy: String?, val editedBy: String?)

/**
 * Turns the account ids stamped on transactions into names (pure, so it is unit-tested). [ownerId] is the Shared
 * Space's Owner (also the environment every row belongs to); null when this phone isn't in a space.
 */
data class PeopleDirectory(
    val myId: String? = null,
    val ownerId: String? = null,
    /** Account id → display name: the space's members now, plus anyone who was a member before (so history keeps names). */
    val names: Map<String, String> = emptyMap(),
) {
    /** "you" for this phone's account, the person's name, or "someone else" for an account this phone never learned. */
    fun nameOf(id: String): String = when (id) {
        myId -> YOU
        else -> names[id]?.takeIf { it.isNotBlank() } ?: SOMEONE
    }

    /**
     * Attribution for a transaction, or null when there is nothing worth saying: a solo phone sees none for its own
     * entries. A row written before attribution existed (no creator) can only have been written by the Owner — the
     * environment was theirs alone — so inside a space it reads as theirs. An edit is mentioned only when someone other
     * than the creator made the last change.
     */
    fun attribution(createdBy: String?, updatedBy: String?): Attribution? {
        val creator = createdBy ?: ownerId
        val someoneElse = listOfNotNull(createdBy, updatedBy).any { it != myId }
        if (ownerId == null && !someoneElse) return null
        val added = creator?.let(::nameOf)
        val edited = updatedBy?.takeIf { it != creator }?.let(::nameOf)
        if (added == null && edited == null) return null
        return Attribution(added, edited)
    }

    companion object {
        const val YOU = "you"
        const val SOMEONE = "someone else"
    }
}

/** A short display name: the label someone chose, else their email's local part, else null. */
fun displayNameOf(label: String?, email: String?): String? =
    label?.trim()?.takeIf { it.isNotEmpty() } ?: email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
