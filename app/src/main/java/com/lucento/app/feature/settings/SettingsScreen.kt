package com.lucento.app.feature.settings

import androidx.compose.material.icons.outlined.Restore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.BuildConfig
import com.lucento.app.R
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.LucentoMark
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.preferences.ThemeMode
import androidx.fragment.app.FragmentActivity
import com.lucento.app.core.security.DeviceAuth
import com.lucento.app.domain.Feature
import com.lucento.app.domain.backup.BackupPreview
import com.lucento.app.domain.backup.RestoreMode
import java.time.LocalDate

/**
 * P2-4: Settings is a short list of pages instead of one long scroll. [SettingsPage.FEATURES] has its own route
 * (EnabledFeaturesScreen); the rest render here.
 */
enum class SettingsPage(val key: String, val title: String) {
    ACCOUNT("account", "Account & sync"),
    MONEY("money", "Money"),
    DATA("data", "Your data"),
    PRIVACY("privacy", "Privacy & security"),
    FEATURES("features", "Enabled features"),
    /** Lucento Shared: opens its own screen (SharedSpaceRoute), like [FEATURES]. */
    SHARED("shared", "Lucento Shared"),
    /** Lucento Pro: opens its own screen (ProRoute), like [SHARED]. */
    PRO("pro", "Lucento Pro"),
    ;

    companion object {
        fun fromKey(key: String?): SettingsPage? = entries.firstOrNull { it.key == key }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    page: SettingsPage? = null,
    onOpenPage: (SettingsPage) -> Unit = {},
    /** Test builds: open Google's account picker as soon as the page shows (profile menu › Switch account). */
    startAccountSwitch: Boolean = false,
    /**
     * Whose ViewModel to use: the hub's, for a page opened from it, so the hub and its pages share one account check and
     * one account prompt (QA M3). Null uses this screen's own.
     */
    viewModelOwner: androidx.lifecycle.ViewModelStoreOwner? = null,
    viewModel: SettingsViewModel = viewModel(
        viewModelStoreOwner = viewModelOwner ?: checkNotNull(androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current),
        factory = SettingsViewModel.Factory,
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var backupPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }
    var lockNotice by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // Once per visit (saved across rotation): the picker shouldn't reopen after it was cancelled.
    var switchStarted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(startAccountSwitch) {
        if (startAccountSwitch && !switchStarted) {
            switchStarted = true
            viewModel.connectGoogleAccount(context)
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(context.contentResolver, uri) { exportMessage = it }
    }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(context.contentResolver, uri) { exportMessage = it }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.inspectBackup(
                context.contentResolver,
                uri,
                onPreview = { backupPreview = it },
                onError = { exportMessage = it },
            )
        }
    }

    // An email account created here: "Check your inbox" until it is verified, then "Email verified" and sync turns on.
    val verification by viewModel.verification.state.collectAsStateWithLifecycle()
    com.lucento.app.feature.account.AccountStatusDialog(
        waiting = verification,
        ready = null,
        onVisible = viewModel.verification::onVisible,
        onResend = viewModel.verification::resendEmail,
        onChangeEmail = viewModel.verification::cancel,
        onPassword = viewModel.verification::providePassword,
        onContinue = {},
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = page?.title ?: "Settings", onBack = onBack)
        state?.let { current ->
            SettingsContent(
                current = current,
                page = page,
                onOpenPage = onOpenPage,
                onFxRatesOnline = viewModel::setFxRatesOnline,
                onFeature = viewModel::setFeatureEnabled,
                onSmartReading = viewModel::setSmartReading,
                onAskLucentoAi = viewModel::setAskLucentoAi,
                onCrashReports = viewModel::setCrashReports,
                onExport = {
                    exportMessage = null
                    exporter.launch("lucento-transactions-${LocalDate.now()}.csv")
                },
                onExportBackup = {
                    exportMessage = null
                    backupExporter.launch("lucento-backup-${LocalDate.now()}.json")
                },
                onRestoreBackup = {
                    exportMessage = null
                    backupImporter.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                exportMessage = exportMessage,
                // LocalContext here is MainActivity, which Credential Manager needs to show the account picker.
                onConnectGoogle = { viewModel.connectGoogleAccount(context) },
                onConnectEmail = viewModel::connectEmailAccount,
                onDisconnect = viewModel::disconnectAccount,
                onSyncNow = viewModel::syncNow,
                onChangePassword = viewModel::changePassword,
                onDeleteAccount = viewModel::requestDeleteAccount,
                onUsageCounts = viewModel::setUsageCounts,
                onAppLock = { enable ->
                    lockNotice = null
                    val activity = context as? FragmentActivity
                    when {
                        !enable -> viewModel.setAppLock(false)
                        activity == null || !DeviceAuth.isAvailable(context) ->
                            lockNotice = "Set a screen lock in your phone’s settings first, then turn this on."
                        // Proving it works before turning it on, so nobody locks themselves out.
                        else -> DeviceAuth.authenticate(activity, "Turn on app lock", "Confirm it’s you") { outcome ->
                            when (outcome) {
                                DeviceAuth.Outcome.SUCCESS -> viewModel.setAppLock(true)
                                DeviceAuth.Outcome.CANCELLED -> Unit
                                DeviceAuth.Outcome.UNAVAILABLE ->
                                    lockNotice = "Set a screen lock in your phone’s settings first, then turn this on."
                            }
                        }
                    }
                },
                onHideInRecents = viewModel::setHideInRecents,
                lockNotice = lockNotice,
            )
            current.cloudSync.deletion?.let { deletion ->
                com.lucento.app.feature.account.DeleteAccountDialog(
                    prompt = deletion,
                    onConfirm = viewModel::confirmDeleteAccount,
                    onDismiss = viewModel::dismissDeleteAccount,
                    onManageSubscription = {
                        val url = "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                    },
                )
            }
            current.cloudSync.prompt?.takeIf { it.kind == AccountPrompt.Kind.MERGE }?.let {
                // P0-2: this phone and the account both have records. Both are kept; the user decides whether to join them.
                ConfirmDialog(
                    title = "Add this phone’s records to your account?",
                    // P0-2.1: accurate about what combining does. Categories with the same name merge, and when both sides
                    // budgeted that category, one amount is kept (sync behaviour unchanged; see NEXT_TASKS budget conflicts).
                    text = "Your account already has Lucento data, and this phone has records of its own. Your wallets, " +
                        "transactions, goals and scheduled payments from both are kept and added together.\n\n" +
                        "Categories with the same name are combined into one. If both had a budget for that category, " +
                        "the most recently changed amount is kept — check your budgets afterwards.\n\n" +
                        "Anything you recorded in both places will appear twice and be counted twice in your totals " +
                        "until you delete the extra copy.\n\n" +
                        "Choose Don’t connect to leave this phone as it is, not signed in.",
                    confirmLabel = "Add to account",
                    onConfirm = viewModel::confirmAccountPrompt,
                    onDismiss = viewModel::dismissAccountPrompt,
                    dismissLabel = "Don’t connect",
                )
            }
            current.cloudSync.prompt?.takeIf { it.kind != AccountPrompt.Kind.MERGE }?.let { prompt ->
                val unsynced = signOutUnsyncedMessage(prompt.unsyncedCount)
                val signOutWithLoss = prompt.kind == AccountPrompt.Kind.SIGN_OUT && unsynced != null
                ConfirmDialog(
                    title = if (prompt.kind == AccountPrompt.Kind.SIGN_OUT) "Disconnect this account?" else "Switch account?",
                    text = if (prompt.kind == AccountPrompt.Kind.SIGN_OUT) {
                        "This removes the account’s wallets, transactions, budgets and goals from this phone, and " +
                            "Lucento starts again from setup. Sign back in to download them again." +
                            (unsynced?.let { " $it" } ?: "")
                    } else {
                        "This phone’s data belongs to another account. It will be removed so it is never uploaded " +
                            "to the account you just signed in to, and Lucento then checks that account and sets it up." +
                            if (prompt.hasUnsynced) " Changes that haven’t synced will be lost." else ""
                    },
                    confirmLabel = if (signOutWithLoss) "Sync first" else if (prompt.kind == AccountPrompt.Kind.SIGN_OUT) "Disconnect" else "Remove and switch",
                    destructive = !signOutWithLoss,
                    onConfirm = if (signOutWithLoss) viewModel::syncBeforeSignOut else viewModel::confirmAccountPrompt,
                    onDismiss = viewModel::dismissAccountPrompt,
                    secondaryLabel = if (signOutWithLoss) "Sign out anyway" else null,
                    onSecondary = if (signOutWithLoss) viewModel::confirmAccountPrompt else null,
                    secondaryDestructive = true,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxl))
    }

    backupPreview?.let { preview ->
        RestorePreviewDialog(
            preview = preview,
            onMerge = {
                backupPreview = null
                viewModel.restoreBackup(RestoreMode.MERGE) { exportMessage = it }
            },
            onReplace = { confirmReplace = true },
            onDismiss = { backupPreview = null },
        )
    }
    if (confirmReplace) {
        ConfirmDialog(
            title = "Replace everything on this device?",
            text = "This erases the wallets, transactions, budgets, goals and schedules on this phone, then imports the file. It can’t be undone.",
            confirmLabel = "Replace",
            destructive = true,
            onConfirm = {
                confirmReplace = false
                backupPreview = null
                viewModel.restoreBackup(RestoreMode.REPLACE) { exportMessage = it }
            },
            onDismiss = { confirmReplace = false },
        )
    }

}

/** Just the Enabled features list, reached from More. */
@Composable
fun EnabledFeaturesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Enabled features", onBack = onBack)
        state?.let { EnabledFeaturesSection(it, viewModel::setFeatureEnabled, showHeader = false) }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * Optional modules with switches. Turning one off only hides it — its data stays and returns when it is
 * turned back on. Dependencies are explained inline, and a switch is disabled when turning it off would
 * hide data the user is actively relying on (more than one account).
 */
@Composable
fun EnabledFeaturesSection(state: SettingsUiState, onFeature: (Feature, Boolean) -> Unit, showHeader: Boolean = true) {
    val features = state.preferences.features
    Column {
        if (showHeader) SectionHeader("Enabled features", Modifier.padding(top = Spacing.section))
        Text(
            "Turn modules on or off to keep Lucento as simple as you like. Turning one off never deletes anything — " +
                "your budgets, goals and history come back when you turn it on again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        Feature.entries.forEachIndexed { index, feature ->
            val on = features.isSwitchedOn(feature)
            val missing = features.missingRequirements(feature)
            val lockedOn = feature == Feature.MULTIPLE_WALLETS && on && state.walletCount > 1
            SwitchRow(
                title = feature.title,
                description = feature.description,
                note = when {
                    lockedOn -> "You have ${state.walletCount} accounts, so this stays on."
                    on && missing.isNotEmpty() -> "Needs ${missing.joinToString { it.title }} — turn it on to use this."
                    else -> null
                },
                checked = on,
                enabled = !lockedOn,
                onCheckedChange = { onFeature(feature, it) },
            )
            if (index < Feature.entries.lastIndex) Hairline()
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Transactions, categories and your main account are always available — Lucento’s numbers depend on them. " +
                "Reminder notifications aren’t available yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsContent(
    current: SettingsUiState,
    page: SettingsPage?,
    onOpenPage: (SettingsPage) -> Unit,
    onFxRatesOnline: (Boolean) -> Unit,
    onFeature: (Feature, Boolean) -> Unit,
    onSmartReading: (Boolean) -> Unit,
    onAskLucentoAi: (Boolean) -> Unit,
    onCrashReports: (Boolean) -> Unit,
    onExport: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    exportMessage: String?,
    onConnectGoogle: () -> Unit,
    onConnectEmail: (String, String, EmailMode) -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onChangePassword: (String) -> Unit,
    onDeleteAccount: () -> Unit,
    onUsageCounts: (Boolean) -> Unit,
    onAppLock: (Boolean) -> Unit = {},
    onHideInRecents: (Boolean) -> Unit = {},
    lockNotice: String? = null,
) {
    val prefs = current.preferences
    Column {
      when (page) {
      null -> SettingsHub(current, onOpenPage)
      SettingsPage.MONEY -> {
        // Multicurrency (owner, 2026-11-05): the main currency is chosen once. Every value in another currency is kept in
        // it, so it can't change afterwards — shown here, not editable.
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(Sizes.iconTile)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    MoneyFormatter.currencySymbol(prefs.primaryCurrency).take(3),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Main currency", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${prefs.primaryCurrency} · ${MoneyFormatter.currencyName(prefs.primaryCurrency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Outlined.Lock, contentDescription = "Can’t be changed", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "All your totals are in your main currency. Wallets in other currencies keep their own, and each of their " +
                "transactions is converted at its own rate. It was set when you started and can’t be changed, so every " +
                "converted amount stays comparable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionHeader("Exchange rates", Modifier.padding(top = Spacing.section))
        SwitchRow(
            title = "Get exchange rates online",
            description = "Fills in each day’s rate when you record something in another currency. Only two currency " +
                "codes and a date are sent — never an amount — to free rate services (currency-api on jsDelivr, or the " +
                "European Central Bank’s rates). Nothing is fetched while you only use your main currency.",
            note = if (prefs.fxRatesOnline) null else "Off: you type rates, or use a wallet’s last or average rate.",
            checked = prefs.fxRatesOnline,
            onCheckedChange = onFxRatesOnline,
        )
      }
      SettingsPage.FEATURES -> EnabledFeaturesSection(current, onFeature, showHeader = false)
      SettingsPage.SHARED, SettingsPage.PRO -> Unit
      SettingsPage.ACCOUNT -> CloudSyncSection(current.cloudSync, onConnectGoogle, onDisconnect, onSyncNow, onConnectEmail, onChangePassword, showHeader = false, onDeleteAccount = onDeleteAccount)
      SettingsPage.DATA -> {
        NavRow(
            icon = Icons.Outlined.FileDownload,
            title = "Export transactions (CSV)",
            summary = (if (current.transactionCount == 1) "1 row" else "${current.transactionCount} rows") + " · opens in any spreadsheet app",
            onClick = onExport,
        )
        BackupExportNotice()
        NavRow(
            icon = Icons.Outlined.FileDownload,
            title = "Export backup (JSON)",
            summary = "Wallets, transactions, budgets, goals, debts and settings",
            onClick = onExportBackup,
            wrapTitle = true,
        )
        // Lucento Shared: a restore rewrites wallets and categories, which a Partner doesn't change.
        if (com.lucento.app.feature.sharedspace.LocalSharedAccess.current.canManageStructure) {
            NavRow(
                icon = Icons.Outlined.FileUpload,
                title = "Restore backup",
                summary = "Merge with this phone, or replace everything here",
                onClick = onRestoreBackup,
            )
        } else {
            val owner = com.lucento.app.feature.sharedspace.LocalSharedAccess.current.ownerDisplay
            com.lucento.app.core.designsystem.component.InfoNote(
                text = "Restoring a backup replaces wallets and categories, which $owner manages in this Shared Space. " +
                    "You can still export one.",
                icon = Icons.Outlined.FileUpload,
            )
        }
        exportMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.sm))
        InfoNote(
            "A backup is one JSON file you choose where to save. Restore it to merge with this phone, or to replace " +
                "everything here. CSV export is only a spreadsheet of transactions. Goal photos stay on this phone, and " +
                "the file never includes sign-in or crash reports. Uninstalling Lucento or clearing its storage erases " +
                "anything that isn’t in a backup or in cloud sync.",
        )
      }
      SettingsPage.PRIVACY -> {
        // P2-10 (D8): both optional, both off by default, both only on this phone.
        SectionHeader("On this phone", Modifier.padding(top = Spacing.sm))
        SwitchRow(
            title = "Lock Lucento",
            checked = prefs.appLock,
            onCheckedChange = onAppLock,
            description = "Ask for your fingerprint, face or screen lock when you open Lucento or come back after a minute away. Uses your phone’s own lock; Lucento stores nothing.",
            note = lockNotice,
        )
        SwitchRow(
            title = "Hide in recent apps",
            checked = prefs.hideInRecents,
            onCheckedChange = onHideInRecents,
            description = "Shows a blank preview instead of your balances when you switch apps. This also blocks screenshots and screen recording of Lucento.",
        )
        SectionHeader("What leaves this phone", Modifier.padding(top = Spacing.section))
        // Said once (N-22): nothing is sent unless cloud sync or one of the switches below is on.
        Fact(
            if (current.cloudSync.status.enabled) Icons.Filled.CloudDone else Icons.Outlined.PhoneAndroid,
            if (current.cloudSync.status.enabled) "Cloud sync is on" else "Nothing, unless you turn something on",
            if (current.cloudSync.status.enabled)
                "Wallets, transactions, budgets and goals are copied to your account, readable only by you. Everything below stays off until you switch it on."
            else
                "Your data stays in Lucento’s private storage on this phone. Cloud sync, crash reports, usage counts and Smarter reading each send something only after you turn them on.",
        )
        SwitchRow(
            title = "Send crash reports",
            checked = prefs.crashReports,
            onCheckedChange = onCrashReports,
            description = "Off unless you turn this on. A report is the stack and the kind of error — never an amount, a note, or what you typed.",
        )
        SwitchRow(
            title = "Share anonymous usage counts",
            checked = prefs.usageCounts,
            onCheckedChange = onUsageCounts,
            description = "Off unless you turn this on. Event names and timings only, with a random install id. Never amounts or what you type. Kept 90 days. Turning this off deletes the counts for this install.",
        )
        // Debug builds without a test proxy have no Smarter reading (P0-1): nothing to switch on.
        if (current.cloudSync.configured && current.cloudSync.signedInEmail != null && com.lucento.app.BuildConfig.AI_INTERPRET_URL.isNotBlank()) {
            SmartReadingSection(
                on = prefs.smartReading,
                signedIn = true,
                onChange = onSmartReading,
            )
        }
        if (current.cloudSync.configured && current.cloudSync.signedInEmail != null && com.lucento.app.BuildConfig.AI_ASK_URL.isNotBlank()) {
            AskLucentoAiSection(on = prefs.askLucentoAi, onChange = onAskLucentoAi)
        }
        Hairline(inset = Sizes.iconTile + Spacing.md)
        Fact(Icons.Outlined.KeyboardVoice, "Voice and receipts are read on this phone", "Speech is turned into text by your phone’s own speech service and receipts by on-device text recognition; Lucento reads both offline.")
        Hairline(inset = Sizes.iconTile + Spacing.md)
        Fact(Icons.Outlined.CloudOff, "Not in cloud backups", "Your financial data is excluded from Android’s automatic cloud backup.")
        // The documents agreed to when creating an account, readable any time (and offline).
        var legal by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<com.lucento.app.feature.legal.LegalDoc?>(null) }
        legal?.let { com.lucento.app.feature.legal.LegalSheet(it, onDismiss = { legal = null }) }
        SectionHeader("Legal", Modifier.padding(top = Spacing.section))
        com.lucento.app.core.designsystem.component.NavRow(
            icon = Icons.Outlined.PrivacyTip,
            title = com.lucento.app.feature.legal.LegalDocuments.privacy.title,
            summary = "What stays on this phone and what leaves it",
            onClick = { legal = com.lucento.app.feature.legal.LegalDoc.PRIVACY },
        )
        com.lucento.app.core.designsystem.component.NavRow(
            icon = Icons.AutoMirrored.Outlined.Article,
            title = com.lucento.app.feature.legal.LegalDocuments.terms.title,
            summary = "Updated ${com.lucento.app.feature.legal.LegalDocuments.UPDATED}",
            onClick = { legal = com.lucento.app.feature.legal.LegalDoc.TERMS },
        )

      }
      }
    }
}

/**
 * Optional Google Sign-In + cloud sync (see docs/SYNC.md). Every core feature works without this section
 * ever being touched — it only explains and controls something extra, never something required.
 */
@Composable
internal fun CloudSyncSection(
    state: CloudSyncUiState,
    onConnectGoogle: () -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onConnectEmail: (String, String, EmailMode) -> Unit = { _, _, _ -> },
    onChangePassword: (String) -> Unit = {},
    showHeader: Boolean = true,
    onDeleteAccount: () -> Unit = {},
) {
    Column {
        if (showHeader) SectionHeader("Cloud sync", Modifier.padding(top = Spacing.section))
        when {
            !state.configured -> {
                InfoNote("Cloud sync isn’t available in this version of Lucento yet. Everything else works fully offline.")
            }
            state.signedInEmail == null -> {
                Text(
                    "Back up your wallets, transactions, budgets and goals and keep them in sync across your " +
                        "devices. Lucento stays fully usable without this — your data stays on this phone until you " +
                        "connect an account and keep sync on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                // Without GOOGLE_WEB_CLIENT_ID (see docs/SYNC.md "Setup required") only the email option is offered.
                ConnectAccountPanel(
                    busy = state.syncing,
                    googleAvailable = state.googleSignInConfigured,
                    onGoogle = onConnectGoogle,
                    onEmail = onConnectEmail,
                )
            }
            else -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(
                        // A check mark beside "Last sync failed" would contradict the text: failures get the warning look.
                        icon = if (state.status.lastError != null) Icons.Outlined.CloudOff else Icons.Filled.CloudDone,
                        container = if (state.status.lastError != null) LucentoTheme.extendedColors.warningContainer else MaterialTheme.colorScheme.primaryContainer,
                        content = if (state.status.lastError != null) LucentoTheme.extendedColors.warning else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(state.signedInEmail, style = MaterialTheme.typography.titleMedium)
                        Text(
                            syncStatusLine(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = onSyncNow, enabled = !state.syncing) {
                        if (state.syncing) {
                            CircularProgressIndicator(Modifier.size(Sizes.iconTile / 2), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Sync now")
                        }
                    }
                    OutlinedButton(onClick = onDisconnect, enabled = !state.syncing) { Text("Disconnect") }
                }
                // Test builds only: one tap to Google's account picker. Signing in as another account is the ordinary
                // account switch (AccountBoundary): this phone's ledger is removed first — after a confirmation when
                // changes haven't synced — and onboarding restores the chosen account, its Shared Space included.
                if (BuildConfig.DEBUG && state.googleSignInConfigured) {
                    Spacer(Modifier.height(Spacing.md))
                    com.lucento.app.core.designsystem.component.LucentoSecondaryButton(
                        text = "Switch account",
                        icon = Icons.Outlined.SwapHoriz,
                        enabled = !state.syncing,
                        onClick = onConnectGoogle,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Test builds only. Pick another Google account on this phone: this account’s data leaves the " +
                            "phone (it stays in the account) and the other account’s comes back from the cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                ChangePasswordForm(busy = state.syncing, onSubmit = onChangePassword)
                // Google Play's account-deletion requirement (docs/ACCOUNT_DELETION.md): always here while signed in,
                // whatever the plan, and never hidden behind sync.
                Spacer(Modifier.height(Spacing.lg))
                TextButton(
                    onClick = onDeleteAccount,
                    enabled = !state.syncing,
                    modifier = Modifier.heightIn(min = Sizes.minTouch),
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        com.lucento.app.feature.account.AccountDeletionCopy.ENTRY,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    com.lucento.app.feature.account.AccountDeletionCopy.ENTRY_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Lucento Pro: signed in, but the plan doesn't include cloud sync (docs/MONETIZATION.md §3.5). Nothing is lost.
        if (state.lockedByPlan && state.signedInEmail != null) {
            Spacer(Modifier.height(Spacing.sm))
            com.lucento.app.feature.pro.ProNote(
                from = com.lucento.app.domain.entitlement.ProFeature.CLOUD_SYNC,
                text =
                if (state.status.enabled) "Cloud sync is paused: it’s part of Lucento Pro. Everything stays on this phone and in your account; uploads resume when your plan includes it. A backup file is always free."
                else "Cloud sync is part of Lucento Pro. You’re signed in; everything stays on this phone. A backup file is always free.",
            )
        }
        // The status line already shows the stored error; don't print the same sentence twice (account mismatch).
        state.lastMessage?.takeIf { state.signedInEmail == null || it != state.status.lastError }?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun syncStatusLine(state: CloudSyncUiState): String = syncStatusText(
    lastSyncedAtMillis = state.status.lastSyncedAtMillis,
    nowMillis = System.currentTimeMillis(),
    pendingChanges = state.pendingChanges,
    lastError = state.status.lastError,
)

/** A statement of how Lucento behaves — informational, not a toggle. */
@Composable
private fun Fact(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), verticalAlignment = Alignment.Top) {
        IconTile(
            icon = icon,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.xxs))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Smarter reading (opt-in, off by default): typed and spoken messages are read by Gemini through Lucento's server.
 * Turning it on always goes through a dialog that says what is sent and where; it needs a sync account, which carries
 * the daily allowance. Receipts are always read on this phone, and so is everything when this is off or unreachable.
 */
@Composable
private fun SmartReadingSection(on: Boolean, signedIn: Boolean, onChange: (Boolean) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    SectionHeader("Smarter reading", Modifier.padding(top = Spacing.section))
    SwitchRow(
        title = "Read messages with Gemini",
        checked = on,
        onCheckedChange = { wanted -> if (wanted) asking = true else onChange(false) },
        enabled = signedIn || on,
        description = "Better at messy, multi-item messages. Off or offline, Lucento reads them on this phone as before.",
        note = if (!signedIn) "Connect a sync account above first — it keeps your daily allowance." else null,
    )
    if (asking) {
        ConfirmDialog(
            title = "Send messages to Gemini?",
            text = "When you type or speak in the composer, Lucento sends that message, today’s date, your main currency " +
                "and the names of your categories and accounts to Lucento’s server, which asks Google’s Gemini to read it. " +
                "Balances and your history are not sent. ${com.lucento.app.feature.legal.LegalDocuments.GEMINI_DATA_USE} Each " +
                "account has a daily allowance; after it, reading happens on this phone. You review every row before " +
                "anything is saved, receipts are still read on this phone, and you can turn this off at any time.",
            confirmLabel = "Turn on",
            onConfirm = {
                asking = false
                onChange(true)
            },
            onDismiss = { asking = false },
        )
    }
}

/**
 * Ask Lucento's AI answers (docs/ASK.md): off until the user agrees to what is sent. Reports are always calculated on
 * this phone, with or without it.
 */
@Composable
private fun AskLucentoAiSection(on: Boolean, onChange: (Boolean) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    SectionHeader("Ask Lucento", Modifier.padding(top = Spacing.section))
    SwitchRow(
        title = "AI answers with Gemini",
        checked = on,
        onCheckedChange = { wanted -> if (wanted) asking = true else onChange(false) },
        description = "Ask anything about your money and get advice. Off, Ask Lucento still prepares your reports on this phone.",
    )
    if (asking) {
        ConfirmDialog(
            title = "Turn on AI answers?",
            text = "When you ask Ask Lucento a question of your own, Lucento sends it, with your figures — monthly totals, " +
                "categories, balances, budgets, debts, goals and upcoming bills — projections worked out on this phone, and " +
                "your transactions from the last 60 days in your main currency (date, amount, type, category, account and " +
                "note) to Lucento’s server, which asks Google’s Gemini. ${com.lucento.app.feature.legal.LegalDocuments.GEMINI_DATA_USE} " +
                "Receipts, photos, older transactions and other " +
                "currencies are never sent. Every amount in an answer is worked out on this phone. Gemini's advice is " +
                "based on your figures, but AI can be inaccurate, so check it before you act on it. Each account has a " +
                "daily allowance, shared with Smarter reading, and you can turn this off at any time.",
            confirmLabel = "Turn on",
            onConfirm = {
                asking = false
                onChange(true)
            },
            onDismiss = { asking = false },
        )
    }
}

/**
 * Shown before a backup is written. The lines wrap with the font size; the Settings screen scrolls,
 * so nothing is clipped at a large font scale.
 */
@Composable
private fun BackupExportNotice() {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Text(
            stringResource(R.string.backup_goal_photos_not_included),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.backup_file_not_encrypted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RestorePreviewDialog(
    preview: BackupPreview,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        icon = {
            com.lucento.app.core.designsystem.component.IconTile(
                icon = androidx.compose.material.icons.Icons.Outlined.Restore,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 52.dp,
                shape = androidx.compose.foundation.shape.CircleShape,
            )
        },
        title = { Text("Restore this backup?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(preview.summary(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Merge keeps records already on this phone and updates one only when the file is newer. " +
                        "Replace erases this phone’s records and uses the file. Theme and currency change only when you replace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onMerge) { Text("Merge") }
                TextButton(onClick = onReplace) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
