package com.lucento.app.data.sharedspace

import com.lucento.app.data.sync.SyncResult
import com.lucento.app.domain.PeopleDirectory
import com.lucento.app.domain.SharedAccess
import com.lucento.app.domain.SpaceRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The phone's side of a ledger switch (wired in AppContainer to LocalLedgerReset, the sync binding and the sync engine),
 * as an interface so the joins and departures below are tested on the JVM.
 */
interface LedgerTransitions {
    /**
     * Replaces this phone's ledger with an empty one bound to [ownerId]'s Shared Space and turns sync on. Marker-protected
     * like a sign-out wipe; nothing of the previous ledger survives it.
     */
    suspend fun joinSpace(ownerId: String)

    /**
     * Removes the shared ledger. The phone goes back through onboarding to the person's own account, which is restored
     * there (or set up when it is empty). The person stays signed in.
     */
    suspend fun returnToOwnLedger()

    /** One full sync: after joining, the space's first pull. */
    suspend fun sync(): SyncResult

    suspend fun hasUnsyncedChanges(): Boolean
}

sealed interface SpaceActionResult {
    data object Done : SpaceActionResult
    data class Failed(val code: String) : SpaceActionResult
}

/** Lucento Shared on Home: something this account can act on from this phone. "Not now" puts it away by [key]. */
sealed interface SpacePrompt {
    val key: String

    data class Invitation(val invitation: ReceivedInvitation) : SpacePrompt {
        override val key: String get() = invitation.id
    }

    /** The account joined on another phone; this phone still shows the person's own ledger. */
    data class JoinedElsewhere(val ownerId: String, val ownerName: String) : SpacePrompt {
        override val key: String get() = "space:$ownerId"
    }
}

sealed interface JoinResult {
    /** The phone holds the space now. [downloaded] is false when the first download didn't finish; it resumes on its own. */
    data class Joined(val ownerId: String, val downloaded: Boolean) : JoinResult
    data class Failed(val code: String) : JoinResult
}

/**
 * Lucento Shared (docs/shared): one Owner's financial environment, shared with Partners who sign in with their own
 * accounts. The database is the authority on every rule (supabase/migrations/20261105120000_lucento_shared.sql); this
 * class calls it, keeps a cache for the screens, and moves this phone between ledgers when someone joins or leaves.
 *
 * Joins and departures run in [scope] (the app's), so closing the screen that started one never leaves it half-done.
 */
class SharedSpaceRepository(
    /** Null when this build has no cloud sync configured: Lucento Shared needs it. */
    private val remote: SharedSpaceRemote?,
    private val store: SharedSpaceStore,
    /** The sync binding: the Owner whose space this phone holds as a Partner, or null (solo, or an Owner). */
    private val partnerOf: Flow<String?>,
    private val readPartnerOf: suspend () -> String?,
    private val signedInUserId: Flow<String?>,
    private val transitions: LedgerTransitions,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val available: Boolean get() = remote != null

    val cache: Flow<SharedSpaceCache> = store.cache

    /**
     * What this phone may change. The sync binding decides "Partner" (it is what the sync engine acts on, and works
     * offline); the last snapshot decides "Owner".
     */
    val access: Flow<SharedAccess> = combine(partnerOf, store.cache) { owner, cache -> accessFor(owner, cache) }

    /** Names for "Added by …": everyone in the space now, and everyone this phone has seen in it. */
    val people: Flow<PeopleDirectory> = combine(partnerOf, store.cache, signedInUserId) { owner, cache, me ->
        val snapshot = cache.snapshot
        val spaceOwner = owner ?: snapshot?.takeIf { it.role == SpaceRole.OWNER }?.ownerId
        PeopleDirectory(myId = me, ownerId = spaceOwner, names = cache.knownNames + (snapshot?.names() ?: emptyMap()))
    }

    /** Home's prompt, if any: never on a phone that already holds a space. */
    val prompt: Flow<SpacePrompt?> = combine(partnerOf, store.cache) { owner, cache -> promptFor(owner, cache) }

    private val refreshing = Mutex()

    /** Asks the database where this account stands. Saves the answer for offline use. */
    suspend fun refresh(): SpaceActionResult {
        val r = remote ?: return SpaceActionResult.Failed(SharedSpaceException.UNAVAILABLE)
        return refreshing.withLock {
            try {
                store.saveSnapshot(r.snapshot(), clock())
                SpaceActionResult.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: SharedSpaceException) {
                SpaceActionResult.Failed(e.code)
            }
        }
    }

    /** [refresh], at most once per [maxAgeMillis], and silent: for app start and Home. */
    suspend fun refreshIfStale(maxAgeMillis: Long = STALE_AFTER_MILLIS) {
        if (remote == null || signedInUserId.first() == null) return
        val last = store.current().refreshedAtMillis
        if (last != null && clock() - last < maxAgeMillis) return
        refresh()
    }

    // ---------------------------------------------------------------- the Owner

    suspend fun createSpace(myName: String): SpaceActionResult = act { it.createSpace(myName.trim().take(MAX_LABEL)) }

    suspend fun invite(email: String): SpaceActionResult {
        val normalized = normalizedEmail(email) ?: return SpaceActionResult.Failed("invalid_email")
        return act { it.invite(normalized) }
    }

    suspend fun revokeInvite(inviteId: String): SpaceActionResult = act { it.revokeInvite(inviteId) }

    suspend fun removePartner(partnerId: String): SpaceActionResult = act { it.removePartner(partnerId) }

    suspend fun closeSpace(): SpaceActionResult = act { it.closeSpace() }

    suspend fun setMyName(name: String): SpaceActionResult = act { it.setMyLabel(name.trim().take(MAX_LABEL)) }

    // ---------------------------------------------------------------- the invited person

    suspend fun decline(inviteId: String): SpaceActionResult = act { it.declineInvite(inviteId) }

    /** Puts Home's prompt away ([SpacePrompt.key]); what it pointed to stays in Settings › Lucento Shared. */
    suspend fun dismiss(promptKey: String) = store.dismissInvitation(promptKey)

    suspend fun hasUnsyncedChanges(): Boolean = transitions.hasUnsyncedChanges()

    /**
     * Accepts [invitation] and moves this phone to the Shared Space: the server first (atomic there — membership and the
     * accepted invitation together), then this phone's ledger is replaced by the space's, which downloads straight away.
     * The person's own data stays in their own account. Callers confirm first when [hasUnsyncedChanges].
     */
    suspend fun join(invitation: ReceivedInvitation, myName: String): JoinResult = scope.async {
        val r = remote ?: return@async JoinResult.Failed(SharedSpaceException.UNAVAILABLE)
        val ownerId = try {
            r.acceptInvite(invitation.id, myName.trim().take(MAX_LABEL))
        } catch (e: SharedSpaceException) {
            return@async JoinResult.Failed(e.code)
        }
        enterSpace(ownerId, invitation.inviterName)
        downloadSpace(ownerId)
    }.await()

    /**
     * Onboarding, before anything is restored (a new phone, a reinstall, a join a kill interrupted): when the signed-in
     * account is a Partner, this phone takes the space — an empty ledger bound to it — for the restore to download.
     * True when the phone holds the space now (or already did). False for everyone else, and when the server can't be
     * asked (offline, or a server without Lucento Shared), so onboarding carries on as it always has. A phone still
     * bound to a space the account is no longer part of gives it back first.
     */
    suspend fun adoptMembership(): Boolean = scope.async {
        val bound = readPartnerOf()
        if (refresh() !is SpaceActionResult.Done) return@async bound != null
        val snapshot = store.current().snapshot
        val owner = snapshot?.takeIf { it.role == SpaceRole.PARTNER }?.ownerId
        when {
            owner == null -> {
                if (bound != null) {
                    returnHome(SpaceNotice(SpaceNotice.Kind.REMOVED, currentOwnerName(), transitions.hasUnsyncedChanges()))
                    refresh()
                }
                false
            }
            owner != bound -> {
                enterSpace(owner, snapshot.ownerName)
                true
            }
            else -> true
        }
    }.await()

    /**
     * The account joined on another phone, and this phone still shows the person's own ledger: it switches to the
     * space and downloads it, like joining. Callers confirm first when [hasUnsyncedChanges].
     */
    suspend fun openMembershipHere(): JoinResult = scope.async {
        (refresh() as? SpaceActionResult.Failed)?.let { return@async JoinResult.Failed(it.code) }
        val snapshot = store.current().snapshot?.takeIf { it.role == SpaceRole.PARTNER }
        val owner = snapshot?.ownerId ?: return@async JoinResult.Failed("not_partner")
        if (readPartnerOf() != owner) enterSpace(owner, snapshot.ownerName)
        downloadSpace(owner)
    }.await()

    /** An invitation for onboarding to offer: the newest one waiting that wasn't put away. Read from the last snapshot. */
    suspend fun invitationToOffer(): ReceivedInvitation? {
        val cache = store.current()
        val snapshot = cache.snapshot?.takeIf { it.role != SpaceRole.PARTNER } ?: return null
        return snapshot.received.firstOrNull { it.id !in cache.dismissedInvitations }
    }

    private suspend fun enterSpace(ownerId: String, ownerName: String) {
        store.clearSpace()
        store.rememberName(ownerId, ownerName)
        transitions.joinSpace(ownerId)
        refresh()
    }

    private suspend fun downloadSpace(ownerId: String): JoinResult = when (transitions.sync()) {
        is SyncResult.Success -> JoinResult.Joined(ownerId, downloaded = true)
        // Removed again before the download: the revocation callback already returned the phone to its own account.
        is SyncResult.MembershipRevoked -> JoinResult.Failed("not_partner")
        else -> JoinResult.Joined(ownerId, downloaded = false)
    }

    /**
     * A Partner leaves: the server first (their access ends there), then the shared ledger leaves this phone, which goes
     * back to the person's own account. Changes not yet synced are lost — callers confirm first.
     */
    suspend fun leave(): SpaceActionResult = scope.async {
        val r = remote ?: return@async SpaceActionResult.Failed(SharedSpaceException.UNAVAILABLE)
        val ownerName = currentOwnerName()
        val lost = transitions.hasUnsyncedChanges()
        try {
            r.leave()
        } catch (e: SharedSpaceException) {
            // Already out (removed meanwhile): the phone still has to give the ledger back.
            if (e.code != "not_partner") return@async SpaceActionResult.Failed(e.code)
        }
        // A phone that still shows the person's own ledger (they joined on another phone) keeps it.
        if (readPartnerOf() != null) returnHome(SpaceNotice(SpaceNotice.Kind.LEFT, ownerName, lost)) else refresh()
        SpaceActionResult.Done
    }.await()

    /**
     * The sync engine found this phone's membership gone (removed by the Owner, or left on another phone). The database
     * already refuses this account; the shared ledger leaves the phone now and the person is told why in onboarding.
     */
    suspend fun endedByOwner(ownerId: String) = scope.async {
        if (readPartnerOf() != ownerId) return@async
        returnHome(SpaceNotice(SpaceNotice.Kind.REMOVED, currentOwnerName(), transitions.hasUnsyncedChanges()))
    }.await()

    private suspend fun returnHome(notice: SpaceNotice) {
        store.setNotice(notice)
        store.clearSpace()
        transitions.returnToOwnLedger()
    }

    /** The notice onboarding shows after the shared ledger left this phone; null when there is none. */
    val notice: Flow<SpaceNotice?> = store.cache.map { it.notice }.distinctUntilChanged()

    suspend fun pendingNotice(): SpaceNotice? = store.current().notice

    /** The notice for onboarding, once; null when there is none. */
    suspend fun takeNotice(): SpaceNotice? = store.current().notice?.also { store.setNotice(null) }

    private suspend fun currentOwnerName(): String {
        val cache = store.current()
        val owner = readPartnerOf()
        return cache.snapshot?.takeIf { it.ownerId == owner }?.ownerName ?: owner?.let { cache.knownNames[it] } ?: "the Owner"
    }

    private suspend fun act(block: suspend (SharedSpaceRemote) -> Unit): SpaceActionResult {
        val r = remote ?: return SpaceActionResult.Failed(SharedSpaceException.UNAVAILABLE)
        return try {
            block(r)
            refresh()
            SpaceActionResult.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: SharedSpaceException) {
            SpaceActionResult.Failed(e.code)
        }
    }

    companion object {
        const val MAX_LABEL = 60
        const val STALE_AFTER_MILLIS = 5 * 60_000L

        internal fun promptFor(partnerOf: String?, cache: SharedSpaceCache): SpacePrompt? {
            if (partnerOf != null) return null
            val snapshot = cache.snapshot ?: return null
            val candidates = if (snapshot.role == SpaceRole.PARTNER) {
                listOfNotNull(snapshot.ownerId?.let { SpacePrompt.JoinedElsewhere(it, snapshot.ownerName) })
            } else {
                snapshot.received.map { SpacePrompt.Invitation(it) }
            }
            return candidates.firstOrNull { it.key !in cache.dismissedInvitations }
        }

        internal fun accessFor(partnerOf: String?, cache: SharedSpaceCache): SharedAccess {
            val snapshot = cache.snapshot
            return when {
                partnerOf != null -> SharedAccess(
                    SpaceRole.PARTNER,
                    ownerName = snapshot?.takeIf { it.ownerId == partnerOf }?.ownerName ?: cache.knownNames[partnerOf],
                )
                snapshot?.role == SpaceRole.OWNER -> SharedAccess(
                    SpaceRole.OWNER,
                    ownerName = snapshot.ownerName,
                    partnerName = snapshot.members.firstOrNull()?.displayName,
                )
                else -> SharedAccess.Solo
            }
        }
    }
}
