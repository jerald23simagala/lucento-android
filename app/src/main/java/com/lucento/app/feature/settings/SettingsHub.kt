package com.lucento.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucento.app.BuildConfig
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.domain.Feature

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
            icon = Icons.Outlined.CloudSync,
            title = SettingsPage.ACCOUNT.title,
            summary = accountSummary(current.cloudSync, System.currentTimeMillis()),
            onClick = { onOpenPage(SettingsPage.ACCOUNT) },
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

/** The Privacy & security row's summary: the app lock, and how many data-sharing switches are on (none by default). */
internal fun privacySummary(prefs: UserPreferences, smartReadingOffered: Boolean = true): String {
    val lock = if (prefs.appLock) "App lock on" else "App lock off"
    // Ask Lucento's AI answers use the same proxy and sign-in as Smarter reading, so they're offered in the same builds.
    val sharing = listOf(prefs.crashReports, prefs.usageCounts, prefs.smartReading && smartReadingOffered, prefs.askLucentoAi && smartReadingOffered).count { it }
    val shared = when (sharing) {
        0 -> "nothing shared"
        1 -> "1 sharing option on"
        else -> "$sharing sharing options on"
    }
    return "$lock · $shared"
}
