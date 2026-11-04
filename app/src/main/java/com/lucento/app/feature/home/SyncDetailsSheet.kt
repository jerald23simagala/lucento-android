package com.lucento.app.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.feature.settings.syncStatusText

/** What the sync sheet says and offers, worked out without Compose so it is unit-tested (P2-2). */
data class SyncSheetCopy(
    val headline: String,
    val body: String,
    /** The main button: Sync now, Try again, or null when there is nothing to do (offline, syncing, sync off). */
    val action: String?,
    /** Whether the main button is a first sync, which must go through Settings (it asks before combining data). */
    val actionOpensSettings: Boolean = false,
)

fun syncSheetCopy(status: CloudStatus, details: SyncDetails, nowMillis: Long): SyncSheetCopy = when (status) {
    CloudStatus.LOCAL -> if (!details.configured) SyncSheetCopy(
        headline = "Cloud sync isn’t available",
        body = "This version of Lucento doesn’t include cloud sync. Everything works on this phone.",
        action = null,
    ) else SyncSheetCopy(
        headline = "Cloud sync is off",
        body = "Your wallets, transactions, budgets and goals stay on this phone. Connect an account to back them up and use them on another device.",
        action = "Set up cloud sync",
        actionOpensSettings = true,
    )
    CloudStatus.OFFLINE -> SyncSheetCopy(
        headline = "You’re offline",
        body = "Everything you add or change is saved on this phone and uploads when you’re back online." +
            pendingSuffix(details.pendingChanges),
        action = null,
    )
    CloudStatus.SYNCING -> SyncSheetCopy(
        headline = "Syncing",
        body = if (details.everSynced) "Sending and receiving changes. You can keep using Lucento."
        else "Downloading your account for the first time. Keep Lucento open — this can take a moment on a slow connection.",
        action = null,
    )
    // The account guard is not a passing failure: retrying can't fix it, Settings asks what to do (QA M2).
    CloudStatus.ERROR -> if (details.accountMismatch) SyncSheetCopy(
        headline = "This phone holds another account’s data",
        body = "Sync is paused so it can’t mix two accounts. Open Account & sync to choose what happens to this phone’s data.",
        action = "Open Account & sync",
        actionOpensSettings = true,
    ) else SyncSheetCopy(
        headline = "Sync needs attention",
        body = "The last sync didn’t finish: ${details.lastError ?: "unknown error"}. Nothing on this phone was lost. " +
            "Try again now, or Lucento tries again at its next scheduled sync.",
        action = "Try again",
    )
    CloudStatus.PENDING -> SyncSheetCopy(
        headline = "Waiting to sync",
        body = syncStatusText(details.lastSyncedAtMillis, nowMillis, details.pendingChanges, null) +
            ". " + SYNC_CADENCE,
        action = if (details.everSynced) "Sync now" else "Finish setting up sync",
        actionOpensSettings = !details.everSynced,
    )
    CloudStatus.SYNCED -> SyncSheetCopy(
        headline = "Synced",
        body = syncStatusText(details.lastSyncedAtMillis, nowMillis, 0, null) + ", nothing waiting. " + SYNC_CADENCE,
        action = "Sync now",
    )
}

/**
 * When syncs happen, in plain words, so "Synced" is never mistaken for live (owner, 2026-11-04). True to
 * [com.lucento.app.data.sync.ForegroundSync] and the 30-minute background schedule ([com.lucento.app.data.sync.SyncScheduler]).
 */
internal const val SYNC_CADENCE =
    "Lucento syncs when you open it and a few seconds after each change while it’s open. In the background it syncs " +
        "about every 30 minutes, though your phone may delay that to save battery. Other devices see your changes after their next sync."

private fun pendingSuffix(pending: Int): String = when {
    pending <= 0 -> ""
    pending == 1 -> " 1 change is waiting."
    else -> " $pending changes are waiting."
}

/**
 * P2-2: what the Home sync chip opens. One place to see the state, act on it (Sync now / Try again) and read a
 * "changed on another device" notice (P2-6); the full controls stay in Settings › Account & sync.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SyncDetailsSheet(
    status: CloudStatus,
    details: SyncDetails,
    onSyncNow: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onDismissOverridden: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = syncSheetCopy(status, details, System.currentTimeMillis())
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding(),
        ) {
            SheetHeader(
                title = copy.headline,
                subtitle = details.email?.takeIf { status != CloudStatus.LOCAL }?.let { "Cloud sync · $it" } ?: "Cloud sync",
                icon = when (status) {
                    CloudStatus.ERROR -> Icons.Outlined.CloudOff
                    CloudStatus.OFFLINE -> Icons.Outlined.CloudOff
                    CloudStatus.LOCAL -> Icons.Outlined.PhoneAndroid
                    else -> Icons.Outlined.CloudDone
                },
                tone = when (status) {
                    CloudStatus.ERROR -> com.lucento.app.core.designsystem.component.StatusTones.negative
                    CloudStatus.OFFLINE, CloudStatus.PENDING -> com.lucento.app.core.designsystem.component.StatusTones.warning
                    else -> com.lucento.app.core.designsystem.component.StatusTones.brand
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(copy.body, style = MaterialTheme.typography.bodyMedium)
            overriddenNotice(details.overriddenChanges)?.let { notice ->
                Spacer(Modifier.height(Spacing.md))
                InfoNote(notice, actionLabel = "Got it", onAction = onDismissOverridden)
            }
            Spacer(Modifier.height(Spacing.lg))
            copy.action?.let { label ->
                LucentoPrimaryButton(
                    text = label,
                    icon = if (copy.actionOpensSettings) null else Icons.Outlined.Sync,
                    onClick = if (copy.actionOpensSettings) onOpenAccountSettings else onSyncNow,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.xs))
            }
            if (!copy.actionOpensSettings) {
                LucentoTextButton(text = "Account & sync settings", onClick = onOpenAccountSettings)
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
