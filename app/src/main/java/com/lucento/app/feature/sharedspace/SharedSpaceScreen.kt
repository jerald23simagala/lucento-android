package com.lucento.app.feature.sharedspace

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoFields
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.LucentoWorkingIndicator
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MascotScene
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.data.sharedspace.InviteStatus
import com.lucento.app.data.sharedspace.ReceivedInvitation
import com.lucento.app.data.sharedspace.SharedSpaceSnapshot
import com.lucento.app.data.sharedspace.SpaceInvite
import com.lucento.app.data.sharedspace.SpaceMember
import com.lucento.app.data.sharedspace.normalizedEmail
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings › Lucento Shared (docs/shared/UX_FLOW.md): create a Shared Space and invite someone, see the invitation,
 * the person sharing it and remove them — or, invited, review and join; as a Partner, leave. Screens say people's names;
 * "Owner" and "Partner" appear only where no name is known.
 */
@Composable
fun SharedSpaceScreen(
    onBack: () -> Unit,
    /** Settings › Account & sync, to connect an account first. */
    onOpenAccount: () -> Unit,
    /** After joining: the shared ledger is on this phone — go to Home. */
    onJoined: () -> Unit,
    /** Opened from Home's invitation card: go straight to reviewing this invitation; leaving the review goes back. */
    reviewInviteId: String? = null,
    viewModel: SharedSpaceViewModel = viewModel(factory = SharedSpaceViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // While this phone switches ledgers, Back waits: leaving mid-way would land on a screen whose data is being replaced.
    BackHandler(enabled = state.joinStage == JoinStage.JOINING) {}

    // From Home's card: the invitation itself, not the page around it (owner test 2026-11-05: "Review" landed on this
    // page, next to "Create a Shared Space", and that was tapped instead of joining).
    var openedFromHome by rememberSaveable { mutableStateOf(false) }
    var linkHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(reviewInviteId, state.mode) {
        if (linkHandled || reviewInviteId.isNullOrBlank()) return@LaunchedEffect
        val invitation = state.mode.joinableInvitations.firstOrNull { it.id == reviewInviteId } ?: return@LaunchedEffect
        linkHandled = true
        openedFromHome = true
        viewModel.review(invitation)
    }

    val reviewing = state.reviewing
    if (reviewing != null) {
        val leaveReview = {
            if (openedFromHome) onBack() else viewModel.stopReviewing()
        }
        BackHandler(enabled = state.joinStage != JoinStage.JOINING) { leaveReview() }
        InvitationReview(
            invitation = reviewing,
            suggestedName = state.suggestedName,
            unsynced = state.unsynced,
            joining = state.joinStage == JoinStage.JOINING,
            busy = state.busy,
            message = state.message,
            onJoin = { name -> viewModel.join(reviewing, name, onJoined) },
            onDecline = { viewModel.decline(reviewing) },
            onBack = leaveReview,
            closesOwnSpace = (state.mode as? SpaceMode.Owner)?.snapshot?.closesOnJoin == true,
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Lucento Shared", onBack = onBack)
        // The invite form shows its own refusal right under the button (owner test 2026-11-09: a note up here was out of
        // view with the keyboard open, so a second tap looked like nothing happened).
        state.message?.takeUnless { state.mode.showsInviteForm && state.invitesIncluded }?.let { message ->
            InfoNote(text = message, icon = Icons.Outlined.SyncProblem, actionLabel = "OK", onAction = viewModel::dismissMessage)
            Spacer(Modifier.height(Spacing.md))
        }
        when (val mode = state.mode) {
            SpaceMode.Unavailable -> EmptyState(
                title = "Not available in this version",
                body = "Lucento Shared works through your Lucento account and cloud sync, which this version of the app doesn’t include.",
                illustration = { MascotScene(mood = MascotMood.Curious, mascotSize = 96.dp) },
            )
            SpaceMode.NeedsAccount -> EmptyState(
                title = "Sign in to share",
                body = "Lucento Shared connects two Lucento accounts to one set of finances. Sign in or create an account first — " +
                    "your data stays on this phone until you do.",
                illustration = { MascotScene(mood = MascotMood.Curious, mascotSize = 96.dp) },
                action = {
                    LucentoPrimaryButton(
                        text = "Connect an account",
                        onClick = onOpenAccount,
                    )
                },
            )
            is SpaceMode.Solo -> SoloPage(mode, state, viewModel)
            is SpaceMode.Owner -> OwnerPage(mode.snapshot, state, viewModel)
            is SpaceMode.Partner -> PartnerPage(mode, state, viewModel)
            is SpaceMode.Elsewhere -> ElsewherePage(mode, state, viewModel, onJoined)
        }
        Spacer(Modifier.height(Spacing.xxxl))
    }
}

// ---------------------------------------------------------------------------------------------------- not sharing

@Composable
private fun ColumnScope.SoloPage(mode: SpaceMode.Solo, state: SharedSpaceUiState, viewModel: SharedSpaceViewModel) {
    mode.received.forEach { invitation ->
        InvitationSpotlight(
            invitation = invitation,
            myName = state.suggestedName,
            actionLabel = "Review invitation",
            onAction = { viewModel.review(invitation) },
        )
        Spacer(Modifier.height(Spacing.lg))
    }
    // An invitation waiting: joining is the way forward, so starting a space of your own is a deliberate second choice
    // rather than a primary button right under the invitation.
    var startingOwn by rememberSaveable { mutableStateOf(false) }
    if (mode.received.isNotEmpty() && !startingOwn) {
        LucentoTextButton(
            text = "Start my own Shared Space instead",
            onClick = { startingOwn = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        return
    }

    Intro(
        title = "Manage money together",
        body = "Share this ledger with one other person — a partner, a friend, a roommate or family. You both see every " +
            "wallet, balance and transaction, and both can add, edit or delete transactions. You keep managing wallets, " +
            "budgets, goals, categories and scheduled payments.",
    )
    Spacer(Modifier.height(Spacing.lg))
    if (!state.invitesIncluded) {
        // Inviting is Pro; joining someone else's space is free, so the invitations above stay reviewable.
        com.lucento.app.feature.pro.ProNote(
            text = com.lucento.app.feature.pro.ProCopy.lockedLine(com.lucento.app.domain.entitlement.ProFeature.SHARED_SPACE),
            from = com.lucento.app.domain.entitlement.ProFeature.SHARED_SPACE,
        )
        return
    }
    var name by rememberSaveable(state.suggestedName) { mutableStateOf(state.suggestedName) }
    NameField(value = name, onValueChange = { name = it }, label = "Your name in the Shared Space")
    Spacer(Modifier.height(Spacing.md))
    LucentoPrimaryButton(
        text = "Create a Shared Space",
        icon = Icons.Outlined.Group,
        enabled = !state.busy,
        onClick = { viewModel.createSpace(name) },
    )
    Spacer(Modifier.height(Spacing.md))
    Text(
        "It uses your Lucento account and cloud sync. Nothing about your data changes until someone joins.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------------------------------- the Owner

@Composable
private fun ColumnScope.OwnerPage(snapshot: SharedSpaceSnapshot, state: SharedSpaceUiState, viewModel: SharedSpaceViewModel) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    var closing by rememberSaveable { mutableStateOf(false) }

    // Right after an invitation is sent: the system share sheet with a message to pass on (Lucento sends no email).
    LaunchedEffect(state.justInvited) {
        val email = state.justInvited ?: return@LaunchedEffect
        shareInvitation(context, email, snapshot.ownerEmail)
        viewModel.shareShown()
    }

    val partner = snapshot.members.firstOrNull()
    val pending = snapshot.pendingInvites.firstOrNull()
    // Invitations to this account still count: the server closes an empty space of your own when you join someone else's.
    snapshot.received.forEach { invitation ->
        val canJoin = snapshot.closesOnJoin
        InvitationSpotlight(
            invitation = invitation,
            myName = snapshot.ownerLabel.ifBlank { state.suggestedName },
            note = when {
                canJoin -> "Joining closes your own Shared Space — nobody is in it yet."
                partner != null -> "To join, first remove ${partner.displayName} and close your Shared Space."
                else -> "To join, first withdraw your invitation and close your Shared Space."
            },
            actionLabel = if (canJoin) "Review invitation" else null,
            onAction = if (canJoin) ({ viewModel.review(invitation) }) else null,
        )
        Spacer(Modifier.height(Spacing.section))
    }
    when {
        partner != null -> PartnerCard(partner, onRemove = { removing = partner.id })
        pending != null -> PendingInviteCard(
            invite = pending,
            busy = state.busy,
            onShare = { shareInvitation(context, pending.email, snapshot.ownerEmail) },
            onRevoke = { viewModel.revoke(pending.id) },
        )
        // A space made on Pro that has since lapsed: the server allows no Partner on Free.
        !state.invitesIncluded -> com.lucento.app.feature.pro.ProNote(
            text = com.lucento.app.feature.pro.ProCopy.lockedLine(com.lucento.app.domain.entitlement.ProFeature.SHARED_SPACE),
            from = com.lucento.app.domain.entitlement.ProFeature.SHARED_SPACE,
        )
        else -> {
            Intro(
                title = "Your Shared Space is ready",
                body = "Invite the person you want to share with. They’ll find the invitation when they sign in to " +
                    "Lucento with this email — Lucento doesn’t send email, so you’ll get a message to pass on.",
            )
            Spacer(Modifier.height(Spacing.lg))
            var email by rememberSaveable { mutableStateOf("") }
            val valid = normalizedEmail(email) != null
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(254) },
                label = { Text("Their email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                shape = LucentoFields.shape,
                colors = LucentoFields.filledColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
            LucentoPrimaryButton(
                text = "Send invitation",
                icon = Icons.Outlined.PersonAdd,
                enabled = valid && !state.busy,
                onClick = { viewModel.invite(email) { email = ""; haptics.confirm() } },
            )
            state.message?.let { message ->
                Spacer(Modifier.height(Spacing.md))
                InfoNote(text = message, icon = Icons.Outlined.SyncProblem, actionLabel = "OK", onAction = viewModel::dismissMessage)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "They’ll see everything in this ledger and can add, edit and delete transactions. Wallets, budgets and " +
                    "goals stay yours to manage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(Spacing.section))
    YourName(current = snapshot.ownerLabel.ifBlank { state.suggestedName }, busy = state.busy, onSave = viewModel::setMyName)

    val history = snapshot.invites.filter { it.status != InviteStatus.PENDING }
    if (history.isNotEmpty()) {
        SectionHeader("Invitations", Modifier.padding(top = Spacing.section))
        history.take(5).forEach { InviteRow(it) }
    }

    if (partner == null) {
        Spacer(Modifier.height(Spacing.section))
        LucentoTextButton(
            text = "Close Shared Space",
            color = MaterialTheme.colorScheme.error,
            onClick = { closing = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    removing?.let { id ->
        val name = snapshot.members.firstOrNull { it.id == id }?.displayName ?: "your partner"
        ConfirmDialog(
            title = "Remove $name?",
            text = "$name loses access to this ledger right away. Everything they added stays. What their phone already " +
                "downloaded, and any backup they exported, can’t be taken back.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                removing = null
                haptics.confirm()
                viewModel.removePartner(id)
            },
            onDismiss = { removing = null },
        )
    }
    if (closing) {
        ConfirmDialog(
            title = "Close your Shared Space?",
            text = "Any invitation still waiting is withdrawn. Your own data stays exactly as it is.",
            confirmLabel = "Close",
            destructive = true,
            onConfirm = {
                closing = false
                viewModel.closeSpace()
            },
            onDismiss = { closing = false },
        )
    }
}

@Composable
private fun PartnerCard(partner: SpaceMember, onRemove: () -> Unit) {
    LucentoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Group, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Shared with ${partner.displayName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    listOfNotNull(partner.email.takeIf { it.isNotBlank() }, partner.joinedAt?.let { "since ${shortDate(it)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Bullet("${partner.displayName} sees every wallet, balance, budget, goal and transaction.")
        Bullet("${partner.displayName} can add, edit and delete transactions.")
        Bullet("Wallets, budgets, goals, categories and scheduled payments stay yours to manage.")
        Spacer(Modifier.height(Spacing.md))
        LucentoSecondaryButton(text = "Remove ${partner.displayName}", icon = Icons.Outlined.PersonRemove, onClick = onRemove)
    }
}

@Composable
private fun PendingInviteCard(invite: SpaceInvite, busy: Boolean, onShare: () -> Unit, onRevoke: () -> Unit) {
    val extended = LucentoTheme.extendedColors
    LucentoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Mail, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Invitation waiting", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(invite.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill("Pending", TonePair(extended.infoContainer, extended.info))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            listOfNotNull(invite.createdAt?.let { "Sent ${shortDate(it)}" }, invite.expiresAt?.let { "expires ${shortDate(it)}" }).joinToString(" · ") +
                ". They join by signing in to Lucento with this email.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        LucentoPrimaryButton(
            text = "Share invitation",
            icon = Icons.Outlined.Share,
            onClick = onShare,
        )
        LucentoTextButton(text = "Withdraw invitation", enabled = !busy, onClick = onRevoke, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun InviteRow(invite: SpaceInvite) {
    val extended = LucentoTheme.extendedColors
    val (label, tone) = when (invite.status) {
        InviteStatus.ACCEPTED -> "Accepted" to TonePair(extended.positiveContainer, extended.positive)
        InviteStatus.DECLINED -> "Declined" to TonePair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
        InviteStatus.REVOKED -> "Withdrawn" to TonePair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
        InviteStatus.EXPIRED -> "Expired" to TonePair(extended.warningContainer, extended.warning)
        InviteStatus.PENDING -> "Pending" to TonePair(extended.infoContainer, extended.info)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(invite.email, style = MaterialTheme.typography.bodyLarge)
            invite.createdAt?.let { Text("Sent ${shortDate(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        StatusPill(label, tone)
    }
}

// ---------------------------------------------------------------------------------------------------- the Partner

@Composable
private fun ColumnScope.PartnerPage(mode: SpaceMode.Partner, state: SharedSpaceUiState, viewModel: SharedSpaceViewModel) {
    var leaving by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    LucentoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Group, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("You’re sharing ${mode.ownerName}’s finances", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (mode.ownerEmail.isNotBlank()) {
                    Text(mode.ownerEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Bullet("You see every wallet, balance, budget, goal and transaction.")
        Bullet("You can add, edit and delete transactions — anyone’s.")
        Bullet("${mode.ownerName} manages wallets, budgets, goals, categories and scheduled payments.")
    }
    Spacer(Modifier.height(Spacing.section))
    YourName(current = mode.myLabel.ifBlank { state.suggestedName }, busy = state.busy, onSave = viewModel::setMyName)
    Spacer(Modifier.height(Spacing.section))
    LucentoSecondaryButton(text = "Leave Shared Space", icon = Icons.AutoMirrored.Outlined.Logout, enabled = !state.busy, onClick = { leaving = true })

    if (leaving) {
        val lost = if (state.unsynced > 0) {
            " ${state.unsynced} ${if (state.unsynced == 1) "change on this phone hasn’t" else "changes on this phone haven’t"} synced yet and will be lost."
        } else {
            ""
        }
        ConfirmDialog(
            title = "Leave ${mode.ownerName}’s Shared Space?",
            text = "This phone stops showing ${mode.ownerName}’s finances and goes back to your own Lucento — your own data " +
                "comes back from your account. Everything you added stays in the Shared Space.$lost",
            confirmLabel = "Leave",
            destructive = true,
            onConfirm = {
                leaving = false
                haptics.confirm()
                viewModel.leave()
            },
            onDismiss = { leaving = false },
        )
    }
}

// ---------------------------------------------------------------------------------------------------- joined elsewhere

/** The account joined on another phone; this one still shows the person's own Lucento. */
@Composable
private fun ColumnScope.ElsewherePage(mode: SpaceMode.Elsewhere, state: SharedSpaceUiState, viewModel: SharedSpaceViewModel, onJoined: () -> Unit) {
    val who = mode.ownerName
    if (state.joinStage == JoinStage.JOINING) {
        Spacer(Modifier.height(Spacing.xxl))
        LucentoWorkingIndicator("Switching this phone to $who’s Shared Space…")
        return
    }
    var leaving by rememberSaveable { mutableStateOf(false) }
    LucentoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Group, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("You’re in $who’s Shared Space", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    "You joined on another phone. This phone still shows your own Lucento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Bullet("Switch to see $who’s finances on this phone and add to them.")
        Bullet("Your own Lucento data stays safe in your account and comes back if you leave.")
        if (state.unsynced > 0) Bullet(unsyncedWarning(state.unsynced))
    }
    Spacer(Modifier.height(Spacing.lg))
    LucentoPrimaryButton(
        text = "Switch this phone",
        icon = Icons.Outlined.CheckCircle,
        enabled = !state.busy,
        onClick = { viewModel.openHere(who, onJoined) },
    )
    LucentoTextButton(
        text = "Leave $who’s Shared Space",
        enabled = !state.busy,
        onClick = { leaving = true },
        modifier = Modifier.fillMaxWidth(),
    )
    if (leaving) {
        ConfirmDialog(
            title = "Leave $who’s Shared Space?",
            text = "Your account stops sharing $who’s finances. Everything you added stays in the Shared Space, and this " +
                "phone keeps your own Lucento.",
            confirmLabel = "Leave",
            destructive = true,
            onConfirm = {
                leaving = false
                viewModel.leave()
            },
            onDismiss = { leaving = false },
        )
    }
}

private fun unsyncedWarning(count: Int): String =
    "$count ${if (count == 1) "change on this phone hasn’t" else "changes on this phone haven’t"} reached your account " +
        "yet and will be lost. To keep them, sync first in Settings › Account & sync."

// ---------------------------------------------------------------------------------------------------- joining

/**
 * Reviewing an invitation before joining: what sharing means, what happens on this phone, and the name the other person
 * will see. Used here and by onboarding (a new account invited before it set anything up).
 */
@Composable
internal fun InvitationReview(
    invitation: ReceivedInvitation,
    suggestedName: String,
    unsynced: Int,
    joining: Boolean,
    busy: Boolean,
    message: String?,
    onJoin: (String) -> Unit,
    onDecline: () -> Unit,
    onBack: () -> Unit,
    notNowLabel: String = "Not now",
    /** The person owns a Shared Space nobody is in: joining closes it (server rule), so the review says so. */
    closesOwnSpace: Boolean = false,
) {
    val who = invitation.inviterName
    var name by rememberSaveable(suggestedName) { mutableStateOf(suggestedName) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Invitation", onBack = onBack.takeIf { !joining })
        if (joining) {
            Spacer(Modifier.height(Spacing.xxl))
            LucentoWorkingIndicator("Joining $who’s Shared Space and downloading it…")
            Text(
                "Keep Lucento open for a moment. If the connection drops, it finishes on its own the next time this phone syncs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        message?.let {
            InfoNote(text = it, icon = Icons.Outlined.SyncProblem)
            Spacer(Modifier.height(Spacing.md))
        }
        InvitationSpotlight(invitation = invitation, myName = name.ifBlank { suggestedName })
        Spacer(Modifier.height(Spacing.lg))
        LucentoCard {
            Text("What sharing means", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            Bullet("You’ll both see all of $who’s wallets, balances, budgets, goals and transactions.")
            Bullet("You can add, edit and delete transactions.")
            Bullet("$who manages wallets, budgets, goals, categories and scheduled payments.")
            Bullet("You can leave at any time.")
        }
        Spacer(Modifier.height(Spacing.md))
        LucentoCard {
            Text("On this phone", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            Bullet("This phone switches to the Shared Space.")
            Bullet("Your own Lucento data stays safe in your account and comes back if you leave.")
            if (closesOwnSpace) Bullet("Your own Shared Space closes. Nobody is in it yet, and you can start one again later.")
            if (unsynced > 0) Bullet(unsyncedWarning(unsynced))
        }
        Spacer(Modifier.height(Spacing.lg))
        NameField(value = name, onValueChange = { name = it }, label = "Your name, as $who will see it")
        Spacer(Modifier.height(Spacing.lg))
        LucentoPrimaryButton(text = "Join $who’s Shared Space", icon = Icons.Outlined.CheckCircle, enabled = !busy, onClick = { onJoin(name) })
        LucentoTextButton(text = "Decline", enabled = !busy, onClick = onDecline, modifier = Modifier.fillMaxWidth())
        LucentoTextButton(text = notNowLabel, onClick = onBack, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.xxxl))
    }
}

// ---------------------------------------------------------------------------------------------------- pieces

@Composable
private fun Intro(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (body.isNotBlank()) {
        Spacer(Modifier.height(Spacing.sm))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(60)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = LucentoFields.shape,
        colors = LucentoFields.filledColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun YourName(current: String, busy: Boolean, onSave: (String) -> Unit) {
    var name by rememberSaveable(current) { mutableStateOf(current) }
    SectionHeader("Your name", supporting = "How you appear on transactions you add or edit.")
    Spacer(Modifier.height(Spacing.sm))
    NameField(value = name, onValueChange = { name = it }, label = "Name")
    if (name.trim() != current.trim() && name.isNotBlank()) {
        Spacer(Modifier.height(Spacing.sm))
        LucentoSecondaryButton(text = "Save name", enabled = !busy, onClick = { onSave(name) })
    }
}

/** "Nov 12" from the database's timestamp; the raw value if it can't be read. */
internal fun shortDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
}.getOrDefault(iso.take(10))

/** The system share sheet with a message to pass on: Lucento itself sends no email. */
private fun shareInvitation(context: android.content.Context, inviteeEmail: String, ownerEmail: String) {
    val from = ownerEmail.takeIf { it.isNotBlank() }?.let { " from $it" }.orEmpty()
    val text = "I’ve invited you to share our finances in Lucento. Open Lucento and sign in with $inviteeEmail — the " +
        "invitation$from shows up when you do (it’s also in Settings › Lucento Shared)."
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join my Shared Space in Lucento")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share invitation")) }
}

/** The Owner's invite form is on screen (no Partner, nothing pending): it shows refusals right under its button. */
private val SpaceMode.showsInviteForm: Boolean
    get() = this is SpaceMode.Owner && snapshot.members.isEmpty() && snapshot.pendingInvites.isEmpty()
