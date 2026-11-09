package com.lucento.app.feature.sharedspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.sharedspace.JoinResult
import com.lucento.app.data.sharedspace.ReceivedInvitation
import com.lucento.app.data.sharedspace.SharedSpaceCache
import com.lucento.app.data.sharedspace.SharedSpaceRepository
import com.lucento.app.data.sharedspace.SharedSpaceSnapshot
import com.lucento.app.data.sharedspace.SpaceActionResult
import com.lucento.app.data.sharedspace.sharedSpaceMessage
import com.lucento.app.domain.SpaceRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where this account stands, for the Lucento Shared screen. */
sealed interface SpaceMode {
    /** This build has no cloud sync, which Lucento Shared needs. */
    data object Unavailable : SpaceMode
    data object NeedsAccount : SpaceMode
    data class Solo(val received: List<ReceivedInvitation>) : SpaceMode
    data class Owner(val snapshot: SharedSpaceSnapshot) : SpaceMode
    data class Partner(val ownerName: String, val ownerEmail: String, val myLabel: String) : SpaceMode

    /** The account is a Partner (it joined on another phone), but this phone still shows the person's own ledger. */
    data class Elsewhere(val ownerName: String) : SpaceMode
}

/**
 * Invitations this account can review on this phone. An Owner sees them too: the server closes an Owner's space that
 * nobody is in when they join someone else's ([SharedSpaceSnapshot.closesOnJoin]). None once the account is a Partner.
 */
val SpaceMode.joinableInvitations: List<ReceivedInvitation>
    get() = when (this) {
        is SpaceMode.Solo -> received
        is SpaceMode.Owner -> snapshot.received
        else -> emptyList()
    }

/** An Owner's space with nobody in it and no invitation waiting: accepting another invitation closes it (server rule). */
val SharedSpaceSnapshot.closesOnJoin: Boolean
    get() = role == SpaceRole.OWNER && members.isEmpty() && pendingInvites.isEmpty()

enum class JoinStage { NONE, JOINING, JOINED }

data class SharedSpaceUiState(
    val mode: SpaceMode = SpaceMode.Solo(emptyList()),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    /** A suggestion for "Your name in the Shared Space". */
    val suggestedName: String = "",
    val myEmail: String? = null,
    /** The invitation being reviewed before joining; null on the main page. */
    val reviewing: ReceivedInvitation? = null,
    val joinStage: JoinStage = JoinStage.NONE,
    /** Changes on this phone not yet synced (lost when the ledger switches). */
    val unsynced: Int = 0,
    /** Set right after an invitation was sent: the screen offers the share sheet for it. */
    val justInvited: String? = null,
    /**
     * Whether this account's plan lets it invite a Partner (Lucento Pro's SHARED capability; the server's
     * `max_partners` is 0 without it). Being someone's Partner is always free, so this gates only the Owner's side.
     */
    val invitesIncluded: Boolean = true,
)

class SharedSpaceViewModel(
    private val space: SharedSpaceRepository,
    signedInEmail: Flow<String?>,
    partnerOf: Flow<String?>,
    displayName: Flow<String?>,
    private val pendingChanges: Flow<Int> = flowOf(0),
    invitesIncluded: Flow<Boolean> = flowOf(true),
) : ViewModel() {

    private val local = MutableStateFlow(SharedSpaceUiState())

    val state: StateFlow<SharedSpaceUiState> = combine(
        local, space.cache, partnerOf, signedInEmail, combine(displayName, pendingChanges, invitesIncluded) { name, pending, included -> Triple(name, pending, included) },
    ) { ui, cache, owner, email, (name, pending, included) ->
        ui.copy(
            mode = modeOf(cache, owner, email),
            suggestedName = name?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@').orEmpty(),
            myEmail = email,
            unsynced = pending,
            invitesIncluded = included,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SharedSpaceUiState())

    private fun modeOf(cache: SharedSpaceCache, partnerOf: String?, email: String?): SpaceMode {
        if (!space.available) return SpaceMode.Unavailable
        if (email == null) return SpaceMode.NeedsAccount
        val snapshot = cache.snapshot
        if (partnerOf != null) {
            val same = snapshot?.takeIf { it.ownerId == partnerOf }
            val me = same?.members?.firstOrNull { it.email.equals(email, ignoreCase = true) }
            return SpaceMode.Partner(
                ownerName = same?.ownerName ?: cache.knownNames[partnerOf] ?: "the Owner",
                ownerEmail = same?.ownerEmail.orEmpty(),
                myLabel = me?.label.orEmpty(),
            )
        }
        if (snapshot?.role == SpaceRole.OWNER) return SpaceMode.Owner(snapshot)
        if (snapshot?.role == SpaceRole.PARTNER) return SpaceMode.Elsewhere(snapshot.ownerName)
        return SpaceMode.Solo(snapshot?.received.orEmpty())
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.value = local.value.copy(loading = true)
            val result = space.refresh()
            local.value = local.value.copy(
                loading = false,
                // Offline is fine here: the screen shows what this phone last knew.
                message = (result as? SpaceActionResult.Failed)?.code?.takeUnless { it == "offline" }?.let(::sharedSpaceMessage),
            )
        }
    }

    fun dismissMessage() {
        local.value = local.value.copy(message = null)
    }

    private fun run(onDone: (() -> Unit)? = null, action: suspend () -> SpaceActionResult) {
        if (local.value.busy) return
        local.value = local.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val result = try {
                action()
            } finally {
                local.value = local.value.copy(busy = false)
            }
            when (result) {
                SpaceActionResult.Done -> onDone?.invoke()
                is SpaceActionResult.Failed -> local.value = local.value.copy(message = sharedSpaceMessage(result.code, otherName()))
            }
        }
    }

    private fun otherName(): String? = when (val mode = state.value.mode) {
        is SpaceMode.Partner -> mode.ownerName
        is SpaceMode.Elsewhere -> mode.ownerName
        else -> state.value.reviewing?.inviterName
    }

    // ---------------------------------------------------------------- the Owner

    fun createSpace(myName: String) = run { space.createSpace(myName.ifBlank { state.value.suggestedName }) }

    fun invite(email: String, onSent: () -> Unit) = run(onDone = {
        local.value = local.value.copy(justInvited = email.trim().lowercase())
        onSent()
    }) { space.invite(email) }

    fun shareShown() {
        local.value = local.value.copy(justInvited = null)
    }

    fun revoke(inviteId: String) = run { space.revokeInvite(inviteId) }

    fun removePartner(partnerId: String) = run { space.removePartner(partnerId) }

    fun closeSpace() = run { space.closeSpace() }

    fun setMyName(name: String) = run { space.setMyName(name) }

    // ---------------------------------------------------------------- the invited person / the Partner

    fun review(invitation: ReceivedInvitation) {
        local.value = local.value.copy(reviewing = invitation, message = null)
    }

    fun stopReviewing() {
        if (local.value.joinStage == JoinStage.JOINING) return
        local.value = local.value.copy(reviewing = null, message = null)
    }

    fun decline(invitation: ReceivedInvitation) = run(onDone = { local.value = local.value.copy(reviewing = null) }) {
        space.decline(invitation.id)
    }

    /** Joins: the server first, then this phone switches to the space and downloads it. */
    fun join(invitation: ReceivedInvitation, myName: String, onJoined: () -> Unit) =
        switchPhone(other = invitation.inviterName, onJoined) { space.join(invitation, myName.ifBlank { state.value.suggestedName }) }

    /** Joined on another phone: this phone switches to the space too. */
    fun openHere(ownerName: String, onJoined: () -> Unit) = switchPhone(other = ownerName, onJoined) { space.openMembershipHere() }

    private fun switchPhone(other: String, onJoined: () -> Unit, action: suspend () -> JoinResult) {
        if (local.value.joinStage == JoinStage.JOINING) return
        local.value = local.value.copy(joinStage = JoinStage.JOINING, message = null)
        viewModelScope.launch {
            when (val result = action()) {
                is JoinResult.Joined -> {
                    local.value = local.value.copy(joinStage = JoinStage.JOINED, reviewing = null)
                    onJoined()
                }
                is JoinResult.Failed -> local.value = local.value.copy(
                    joinStage = JoinStage.NONE,
                    message = sharedSpaceMessage(result.code, other),
                )
            }
        }
    }

    /** Leaves: the server first; then this phone goes back to the person's own account (through onboarding). */
    fun leave() = run { space.leave() }

    companion object {
        val Factory = containerFactory {
            SharedSpaceViewModel(
                space = it.sharedSpace,
                signedInEmail = it.auth.observeSignedInEmail(),
                partnerOf = it.syncPreferences.sharedSpaceOwner,
                displayName = it.preferences.preferences.map { prefs -> prefs.displayName },
                pendingChanges = it.syncRepository.observePendingChanges(),
                invitesIncluded = it.entitlements.state.map { plan ->
                    plan.has(com.lucento.app.domain.entitlement.Capability.SHARED, System.currentTimeMillis())
                },
            )
        }
    }
}
