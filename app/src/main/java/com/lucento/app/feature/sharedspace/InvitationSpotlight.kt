package com.lucento.app.feature.sharedspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lucento.app.core.designsystem.component.PeoplePair
import com.lucento.app.core.designsystem.component.SpotlightCard
import com.lucento.app.data.sharedspace.ReceivedInvitation

/**
 * An invitation to someone's Shared Space, made to be noticed: the inviter and "you" side by side on the theme's hero
 * panel. On Home, on Settings › Lucento Shared and at the top of the review before joining (no action there — the
 * review's own Join button is the action).
 */
@Composable
fun InvitationSpotlight(
    invitation: ReceivedInvitation,
    myName: String?,
    modifier: Modifier = Modifier,
    note: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val who = invitation.inviterName
    SpotlightCard(
        eyebrow = "Invitation",
        title = "$who invited you to share finances",
        body = "Join $who’s Shared Space: one set of finances you both see and add to.",
        meta = invitationMeta(invitation),
        note = note,
        visual = { PeoplePair(first = who, second = myName) },
        actionLabel = actionLabel,
        onAction = onAction,
        dismissLabel = dismissLabel,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/** "From olivia@example.com · Expires Nov 12" — whichever parts are known. */
internal fun invitationMeta(invitation: ReceivedInvitation): String =
    listOfNotNull(
        invitation.inviterEmail.takeIf { it.isNotBlank() }?.let { "From $it" },
        invitation.expiresAt?.let { "Expires ${shortDate(it)}" },
    ).joinToString(" · ")
