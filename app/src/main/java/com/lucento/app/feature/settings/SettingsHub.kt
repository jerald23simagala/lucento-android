package com.lucento.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.lucento.app.BuildConfig
import com.lucento.app.core.LucentoLinks
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LocalMessageHosts
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.domain.Feature
import com.lucento.app.feature.help.LucentoActions
import com.lucento.app.feature.legal.LegalDocuments

/**
 * The Settings landing page (P2-4): one row per page, each saying what is set there, then About. Replaces the single
 * long scroll where cloud sync, data and privacy were hard to find (N-22).
 */
@Composable
internal fun SettingsHub(current: SettingsUiState, onOpenPage: (SettingsPage) -> Unit) {
    val prefs = current.preferences
    val inset = Sizes.iconTile + Spacing.md
    Column {
        NavRow(
            icon = Icons.Outlined.WorkspacePremium,
            title = SettingsPage.PRO.title,
            summary = com.lucento.app.feature.pro.ProCopy.planStatus(
                com.lucento.app.feature.pro.LocalEntitlements.current, System.currentTimeMillis(),
            ).title,
            onClick = { onOpenPage(SettingsPage.PRO) },
        )
        Hairline(inset = inset)
        NavRow(
            icon = Icons.Outlined.CloudSync,
            title = SettingsPage.ACCOUNT.title,
            summary = accountSummary(current.cloudSync, System.currentTimeMillis()),
            onClick = { onOpenPage(SettingsPage.ACCOUNT) },
            wrapTitle = true,
        )
        Hairline(inset = inset)
        val shared = com.lucento.app.feature.sharedspace.LocalSharedAccess.current
        NavRow(
            icon = Icons.Outlined.Group,
            title = SettingsPage.SHARED.title,
            summary = when {
                shared.isPartner -> "Sharing ${shared.ownerDisplay}’s finances"
                shared.role == com.lucento.app.domain.SpaceRole.OWNER -> "Your Shared Space is on"
                else -> "Manage money together with one other person"
            },
            onClick = { onOpenPage(SettingsPage.SHARED) },
            wrapTitle = true,
        )
        Hairline(inset = inset)
        NavRow(
            icon = Icons.Outlined.Payments,
            title = SettingsPage.MONEY.title,
            summary = "Main currency ${prefs.primaryCurrency}",
            onClick = { onOpenPage(SettingsPage.MONEY) },
        )
        Hairline(inset = inset)
        NavRow(
            icon = Icons.Outlined.FolderOpen,
            title = SettingsPage.DATA.title,
            summary = "Export, backup and restore",
            onClick = { onOpenPage(SettingsPage.DATA) },
        )
        Hairline(inset = inset)
        NavRow(
            icon = Icons.Outlined.Lock,
            title = SettingsPage.PRIVACY.title,
            summary = privacySummary(
                prefs,
                // Smarter reading counts only where its switch is offered (signed in, with a proxy in this build).
                smartReadingOffered = current.cloudSync.configured && current.cloudSync.signedInEmail != null &&
                    BuildConfig.AI_INTERPRET_URL.isNotBlank(),
            ),
            onClick = { onOpenPage(SettingsPage.PRIVACY) },
            wrapTitle = true,
        )
        Hairline(inset = inset)
        NavRow(
            icon = Icons.Outlined.ToggleOn,
            title = SettingsPage.FEATURES.title,
            summary = "${Feature.entries.count { prefs.features.isEnabled(it) }} of ${Feature.entries.size} on",
            onClick = { onOpenPage(SettingsPage.FEATURES) },
        )
        Hairline(inset = inset)
        var showWidgets by rememberSaveable { mutableStateOf(false) }
        NavRow(
            icon = Icons.Outlined.Widgets,
            title = "Home-screen widgets",
            summary = "Quick add and Safe to spend",
            onClick = { showWidgets = true },
            wrapTitle = true,
        )
        if (showWidgets) com.lucento.app.feature.widget.WidgetsSheet(onDismiss = { showWidgets = false })

        HelpAndFeedback(inset)

        SectionHeader("About", Modifier.padding(top = Spacing.section))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LucentoMark(size = 40.dp, animate = false)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text("Lucento ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Financial clarity without the complexity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Set in Plus Jakarta Sans, licensed under the SIL Open Font License 1.1. Text recognition by Google ML Kit (on-device). " +
                "Animations play with Airbnb Lottie (Apache License 2.0); the Sun and Moon, illustrations and motion are original to Lucento.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Help & feedback (tester report 2026-11-25, docs/HELP_AND_FEEDBACK.md): answers, a way to reach us, the community, a
 * rating and a share, in Settings where testers looked for them. Each row hands over to another app on a tap, so Lucento
 * sends nothing; when the phone has no app for it, a message says what to do instead.
 */
@Composable
private fun HelpAndFeedback(inset: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val messages = LocalMessageHosts.current
    val clipboard = LocalClipboardManager.current
    fun cannotOpen(url: String) = messages?.top?.show("Nothing on this phone can open $url")

    SectionHeader("Help & feedback", Modifier.padding(top = Spacing.section))
    NavRow(
        icon = Icons.AutoMirrored.Outlined.HelpOutline,
        title = "Questions and answers",
        summary = "Common questions, answered on lucentoapp.com",
        onClick = { if (!LucentoActions.browse(context, LucentoLinks.FAQ)) cannotOpen(LucentoLinks.FAQ) },
        wrapTitle = true,
        external = true,
        onClickLabel = "Open in your browser",
    )
    Hairline(inset = inset)
    NavRow(
        icon = Icons.Outlined.MailOutline,
        title = "Contact support",
        summary = "Email ${LegalDocuments.CONTACT_EMAIL} · we reply by email",
        onClick = {
            if (!LucentoActions.emailSupport(context)) {
                messages?.top?.show("No email app on this phone. Write to ${LegalDocuments.CONTACT_EMAIL}", actionLabel = "Copy") {
                    clipboard.setText(AnnotatedString(LegalDocuments.CONTACT_EMAIL))
                }
            }
        },
        wrapTitle = true,
        external = true,
        onClickLabel = "Write an email",
    )
    Hairline(inset = inset)
    NavRow(
        icon = Icons.Outlined.Forum,
        title = "Lucento Community",
        summary = "Questions, ideas and help, in our Facebook group",
        onClick = { if (!LucentoActions.browse(context, LucentoLinks.COMMUNITY)) cannotOpen(LucentoLinks.COMMUNITY) },
        wrapTitle = true,
        external = true,
        onClickLabel = "Open in Facebook",
    )
    Hairline(inset = inset)
    NavRow(
        icon = Icons.Outlined.StarOutline,
        title = "Rate Lucento",
        summary = "Leave a rating or review on Google Play",
        onClick = { if (!LucentoActions.openPlayListing(context)) cannotOpen(LucentoLinks.PLAY_STORE) },
        wrapTitle = true,
        external = true,
        onClickLabel = "Open Google Play",
    )
    Hairline(inset = inset)
    NavRow(
        icon = Icons.Outlined.Share,
        title = "Share Lucento",
        summary = "Tell a friend about Lucento",
        onClick = { LucentoActions.share(context) },
        wrapTitle = true,
        external = true,
        onClickLabel = "Share",
    )
}

/** The Account & sync row's summary: who is signed in and how sync stands, or that the data stays on this phone. */
internal fun accountSummary(state: CloudSyncUiState, nowMillis: Long): String = when {
    !state.configured -> "Not available in this version"
    state.signedInEmail == null -> "Off · your data stays on this phone"
    else -> "${state.signedInEmail} · " + syncStatusText(
        lastSyncedAtMillis = state.status.lastSyncedAtMillis,
        nowMillis = nowMillis,
        pendingChanges = state.pendingChanges,
        lastError = state.status.lastError,
    )
}

/**
 * The Privacy & security row's summary: the app lock, and how many data-sharing options are on. None by default: AI
 * answers are on for a signed-in account but count only once the person confirmed them, since nothing is sent before.
 */
internal fun privacySummary(prefs: UserPreferences, smartReadingOffered: Boolean = true): String {
    val lock = if (prefs.appLock) "App lock on" else "App lock off"
    // Ask Lucento's AI answers use the same proxy and sign-in as Smarter reading, so they're offered in the same builds.
    val aiAnswersSending = prefs.askLucentoAi && prefs.askAiConfirmed && smartReadingOffered
    val sharing = listOf(prefs.crashReports, prefs.usageCounts, prefs.smartReading && smartReadingOffered, aiAnswersSending).count { it }
    val shared = when (sharing) {
        0 -> "nothing shared"
        1 -> "1 sharing option on"
        else -> "$sharing sharing options on"
    }
    return "$lock · $shared"
}
