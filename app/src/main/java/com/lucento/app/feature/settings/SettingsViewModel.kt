package com.lucento.app.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.Context
import com.lucento.app.BuildConfig
import com.lucento.app.core.applicationContainerFactory
import com.lucento.app.core.crash.CrashReportOptOut
import com.lucento.app.core.crash.CrashReporting
import com.lucento.app.domain.UsageCountsService
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.sync.AccountBoundary
import com.lucento.app.data.sync.AccountProbe
import com.lucento.app.data.sync.AuthRepository
import com.lucento.app.data.sync.SupabaseModule
import com.lucento.app.data.sync.SyncPreferences
import com.lucento.app.data.sync.SyncRepository
import com.lucento.app.data.sync.SyncResult
import com.lucento.app.data.sync.SyncScheduler
import com.lucento.app.data.sync.SyncStatus
import com.lucento.app.domain.CsvExporter
import com.lucento.app.domain.Feature
import com.lucento.app.domain.backup.BackupCodec
import com.lucento.app.domain.backup.BackupEngine
import com.lucento.app.domain.backup.BackupFailure
import com.lucento.app.domain.backup.BackupFormatException
import com.lucento.app.domain.backup.BackupPreview
import com.lucento.app.domain.backup.RestoreMode
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val preferences: UserPreferences,
    /** Active wallets; Multiple wallets can only be turned off while there is at most one. */
    val walletCount: Int,
    val transactionCount: Int,
    val cloudSync: CloudSyncUiState,
)

/** Everything the "Cloud sync" section needs, kept separate so the rest of Settings never depends on it. */
/**
 * A wipe the user still has to confirm. [unsyncedCount]: changes that have not reached the server and would be lost.
 * [signOut]: for [Kind.SIGN_OUT], where this phone's records stand, which decides what the dialog offers.
 */
data class AccountPrompt(
    val kind: Kind,
    val unsyncedCount: Int,
    val signOut: com.lucento.app.feature.account.SignOutCase? = null,
) {
    /** [MERGE]: a first sync would join this phone's own records with an account that already has data (P0-2). */
    enum class Kind { SIGN_OUT, SWITCH, MERGE }

    val hasUnsynced: Boolean get() = unsyncedCount > 0
}

data class CloudSyncUiState(
    val configured: Boolean,
    val googleSignInConfigured: Boolean,
    val signedInEmail: String?,
    val status: SyncStatus,
    val syncing: Boolean = false,
    val lastMessage: String? = null,
    val prompt: AccountPrompt? = null,
    /** Dirty rows and unpushed deletions. "Synced just now" is withheld while this is not zero. */
    val pendingChanges: Int = 0,
    /** Lucento Pro: this phone's plan doesn't include cloud sync (docs/MONETIZATION.md §3.5); sync is off or paused, nothing lost. */
    val lockedByPlan: Boolean = false,
    /** The open delete-account confirmation (docs/ACCOUNT_DELETION.md), or null. */
    val deletion: com.lucento.app.feature.account.DeleteAccountPrompt? = null,
)

class SettingsViewModel(
    private val application: Application,
    private val preferences: UserPreferencesRepository,
    private val transactions: TransactionRepository,
    private val wallets: WalletRepository,
    private val categories: CategoryRepository,
    private val auth: AuthRepository,
    private val syncPreferences: SyncPreferences,
    private val syncRepository: SyncRepository,
    private val accounts: AccountBoundary,
    private val usage: UsageCountsService? = null,
    private val backup: BackupEngine,
    private val appLock: com.lucento.app.core.security.AppLockSession? = null,
    /** Lucento Pro: the plan this phone holds; null in tests (everything allowed). */
    private val entitlements: kotlinx.coroutines.flow.StateFlow<com.lucento.app.domain.entitlement.EntitlementState>? = null,
    private val partnerOfSpace: kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
) : ViewModel() {

    private val syncing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow<String?>(null)
    private val prompt = MutableStateFlow<AccountPrompt?>(null)
    private val deletion = MutableStateFlow<com.lucento.app.feature.account.DeleteAccountPrompt?>(null)

    init {
        // A previous session can already be a different account (the app was closed on the confirm step).
        // Ready means "same account or signed out" — opening Settings must not start a sync on its own.
        viewModelScope.launch {
            when (val check = accounts.afterSignIn()) {
                AccountBoundary.AfterSignIn.Ready -> Unit
                AccountBoundary.AfterSignIn.Wiped -> continueAfterWipe()
                is AccountBoundary.AfterSignIn.ConfirmSwitch ->
                    prompt.value = AccountPrompt(AccountPrompt.Kind.SWITCH, syncRepository.pendingChangeCount())
            }
        }
    }

    val uiState: StateFlow<SettingsUiState?> =
        combine(
            preferences.preferences,
            wallets.observeWallets(),
            transactions.observeCount(),
            syncPreferences.status,
            syncing,
            syncMessage,
            auth.observeSignedInEmail(),
            prompt,
            syncRepository.observePendingChanges(),
            entitlements ?: kotlinx.coroutines.flow.flowOf(null),
            partnerOfSpace,
            deletion,
        ) { values ->
            val prefs = values[0] as UserPreferences
            val w = values[1] as List<*>
            val transactionTotal = values[2] as Int
            val syncStatus = values[3] as SyncStatus
            val isSyncing = values[4] as Boolean
            val message = values[5] as String?
            val email = values[6] as String?
            val accountPrompt = values[7] as AccountPrompt?
            val pending = values[8] as Int
            val plan = values[9] as com.lucento.app.domain.entitlement.EntitlementState?
            val partner = values[10] as Boolean
            val deleting = values[11] as com.lucento.app.feature.account.DeleteAccountPrompt?
            val lockedByPlan = plan != null && !com.lucento.app.domain.entitlement.ProGates.syncAllowedByPlan(plan, partner, System.currentTimeMillis())
            SettingsUiState(
                preferences = prefs,
                walletCount = w.size,
                transactionCount = transactionTotal,
                cloudSync = CloudSyncUiState(
                    configured = SupabaseModule.isConfigured,
                    googleSignInConfigured = SupabaseModule.isGoogleSignInConfigured,
                    signedInEmail = email,
                    status = syncStatus,
                    syncing = isSyncing,
                    lastMessage = message,
                    prompt = accountPrompt,
                    pendingChanges = pending,
                    lockedByPlan = lockedByPlan,
                    deletion = deleting,
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Signs in, then turns cloud sync on. A different account wipes the previous ledger before anything is pushed. */
    /** [activity] hosts Google's account picker; it is passed through for this call only and never stored. */
    fun connectGoogleAccount(activity: Context) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            syncMessage.value = null
            handleSignIn(auth.signInWithGoogle(activity))
            syncing.value = false
        }
    }

    /**
     * An email account: [mode] creates one (recording the Terms and Privacy Policy version agreed to) or signs in to an
     * existing one; then sync turns on as for Google. A new account first waits for its email to be verified
     * ([verification]), as in onboarding.
     */
    fun connectEmailAccount(email: String, password: String, mode: EmailMode) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            syncMessage.value = null
            val result = if (mode == EmailMode.CREATE) {
                auth.signUpWithEmail(email, password, acceptedTermsVersion = com.lucento.app.feature.legal.LegalDocuments.VERSION)
            } else {
                auth.signInWithEmail(email, password)
            }
            when (result) {
                is AuthRepository.SignInResult.ConfirmEmail -> verification.start(result.email, password)
                is AuthRepository.SignInResult.AlreadyRegistered -> when (auth.checkEmailConfirmed(email, password)) {
                    // Their own account (made earlier, or verified while Lucento was closed): simply signed in.
                    AuthRepository.EmailCheck.CONFIRMED -> handleSignIn(AuthRepository.SignInResult.Success)
                    AuthRepository.EmailCheck.PENDING -> verification.start(email, password, emailJustSent = false)
                    AuthRepository.EmailCheck.WRONG_PASSWORD ->
                        syncMessage.value = "$email already has a Lucento account. Choose Sign in to use it — or continue with Google if you made it that way."
                    AuthRepository.EmailCheck.UNREACHABLE ->
                        syncMessage.value = "Couldn’t reach Lucento to finish. Check your connection and try again."
                }
                else -> handleSignIn(result)
            }
            syncing.value = false
        }
    }

    /** "Check your inbox" for an account created here; once verified it is signed in and sync turns on. */
    val verification = com.lucento.app.feature.account.EmailVerification(
        scope = viewModelScope,
        check = auth::checkEmailConfirmed,
        resend = auth::resendConfirmation,
        onVerified = { handleSignIn(AuthRepository.SignInResult.Success) },
    )

    private suspend fun handleSignIn(result: AuthRepository.SignInResult) {
        when (result) {
            AuthRepository.SignInResult.Success -> followSignIn(accounts.afterSignIn())
            AuthRepository.SignInResult.Cancelled -> Unit
            AuthRepository.SignInResult.NotConfigured ->
                syncMessage.value = "Cloud sync isn’t available in this version of Lucento yet."
            is AuthRepository.SignInResult.ConfirmEmail ->
                syncMessage.value = "Almost there: open the link we sent to ${result.email}, then come back and sign in."
            is AuthRepository.SignInResult.AlreadyRegistered ->
                syncMessage.value = "${result.email} already has a Lucento account. Choose Sign in to use it."
            is AuthRepository.SignInResult.Failed -> syncMessage.value = result.message
        }
    }

    /**
     * Asks before signing out. What the dialog offers follows where the records stand
     * ([com.lucento.app.feature.account.signOutCase]); nothing changes until the person picks one of its actions.
     */
    fun requestSignOut() {
        if (syncing.value) return
        viewModelScope.launch {
            // The profile menu's row can outlive the session by a frame; never ask a signed-out phone to sign out.
            if (auth.observeSignedInEmail().first() == null) return@launch
            prompt.value = signOutPrompt()
        }
    }

    private suspend fun signOutPrompt(): AccountPrompt {
        val pending = syncRepository.pendingChangeCount()
        val case = com.lucento.app.feature.account.signOutCase(
            phoneOnly = accounts.ledgerIsPhoneOnly(),
            pendingChanges = pending,
            canUpload = planAllowsSyncNow(),
        )
        return AccountPrompt(AccountPrompt.Kind.SIGN_OUT, pending, case)
    }

    /**
     * "Keep them here" (owner decision 2026-11-12, F-2): signs out of an account this phone never synced with. The
     * records were never uploaded, so they stay; only the session, sync and what the phone cached about the account go.
     * If a first sync claimed the records in the meantime, nothing changes and the dialog asks again with what now applies.
     */
    fun keepRecordsAndSignOut() {
        val current = prompt.value ?: return
        if (syncing.value || current.signOut != com.lucento.app.feature.account.SignOutCase.PHONE_ONLY) return
        viewModelScope.launch {
            syncing.value = true
            prompt.value = null
            val kept = withContext(NonCancellable) { accounts.signOutKeepingLedger() }
            if (kept) {
                syncMessage.value = com.lucento.app.feature.account.SignOutCopy.KEPT
            } else {
                syncMessage.value = com.lucento.app.feature.account.SignOutCopy.KEEP_REFUSED
                prompt.value = signOutPrompt()
            }
            syncing.value = false
        }
    }

    /** A line under the account (the sign-out dialog's backup result). */
    fun showAccountMessage(text: String) {
        syncMessage.value = text
    }

    /** Opens the delete-account confirmation (docs/ACCOUNT_DELETION.md). Nothing changes until [confirmDeleteAccount]. */
    fun requestDeleteAccount() {
        if (syncing.value) return
        deletion.value = com.lucento.app.feature.account.DeleteAccountPrompt(
            com.lucento.app.feature.account.DeletionPlan.of(entitlements?.value, System.currentTimeMillis()),
        )
    }

    /** Closes the confirmation; never while the server call runs. */
    fun dismissDeleteAccount() {
        if (deletion.value?.deleting == true) return
        deletion.value = null
    }

    /**
     * Deletes the account on the server first. Only after the server confirms is this phone wiped, in the sign-out
     * order (durable marker, local session, ledger, then the server revoke, which simply finds nothing). A refused or
     * unanswered call changes nothing on the phone, and the dialog stays open with the reason.
     */
    fun confirmDeleteAccount() {
        val current = deletion.value ?: return
        if (current.deleting || syncing.value) return
        deletion.value = current.copy(deleting = true, error = null)
        viewModelScope.launch {
            syncing.value = true
            when (val result = withContext(NonCancellable) { auth.deleteAccount() }) {
                is com.lucento.app.feature.account.DeleteAccountResult.Failed ->
                    deletion.value = current.copy(deleting = false, error = result.message)
                com.lucento.app.feature.account.DeleteAccountResult.Deleted -> {
                    val wiped = withContext(NonCancellable) {
                        runCatching {
                            check(accounts.markWipePendingNow()) { "Could not record the wipe" }
                            auth.beginSignOutNow()
                            // The wipe ends on the welcome screen; this line tells the person there what happened.
                            preferences.setSignOutNotice(com.lucento.app.feature.account.SignOutNotice.ACCOUNT_DELETED.key)
                            accounts.signOutAndWipe()
                        }.onFailure { runCatching { preferences.setSignOutNotice(null) } }.isSuccess
                    }
                    deletion.value = null
                    syncMessage.value = if (wiped) {
                        com.lucento.app.feature.account.AccountDeletionCopy.DONE
                    } else {
                        "Your account was deleted, but this phone’s records couldn’t be erased just now. Choose " +
                            "${com.lucento.app.feature.account.SignOutCopy.ACTION} to finish."
                    }
                }
            }
            syncing.value = false
        }
    }

    /** Uploads what it can, then counts again so the dialog shows what would still be lost. */
    fun syncBeforeSignOut() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            if (syncPreferences.syncedUserId() == null) {
                // Never synced from this phone: uploading means joining it to the account, which needs the same consent.
                prompt.value = null
                firstSyncCheck()
            } else {
                runSyncNow()
                prompt.value = signOutPrompt()
            }
            syncing.value = false
        }
    }

    fun confirmAccountPrompt() {
        val current = prompt.value ?: return
        if (syncing.value) return
        // Durable before the coroutine is even scheduled: a force-stop straight after this tap relaunches signed out.
        // R9-01: the wipe marker is committed first, then the stored session goes; no network call before either.
        if (current.kind == AccountPrompt.Kind.SIGN_OUT) {
            // QA-N1: without a durable wipe marker, signing out could leave a half-removed ledger; stop instead of crashing.
            if (!accounts.markWipePendingNow()) {
                syncMessage.value = "Couldn’t sign out safely just now. Nothing was removed — please try again."
                return
            }
            // Records the session for revoking on the server, then deletes it (R10-01): survives a kill from here on.
            auth.beginSignOutNow()
        }
        viewModelScope.launch {
            syncing.value = true
            prompt.value = null
            when (current.kind) {
                // Not cancellable: the wipe sends the app back to onboarding, which can clear this ViewModel before the
                // server revoke that follows the wipe has run.
                AccountPrompt.Kind.SIGN_OUT -> {
                    // Written before the wipe, which ends on the welcome screen (the screen that set it is gone by then).
                    val notice = if (current.signOut == com.lucento.app.feature.account.SignOutCase.PHONE_ONLY) {
                        com.lucento.app.feature.account.SignOutNotice.REMOVED
                    } else {
                        com.lucento.app.feature.account.SignOutNotice.SIGNED_OUT
                    }
                    withContext(NonCancellable) {
                        preferences.setSignOutNotice(notice.key)
                        try {
                            accounts.signOutAndWipe()
                        } catch (e: Throwable) {
                            runCatching { preferences.setSignOutNotice(null) }
                            throw e
                        }
                    }
                    syncMessage.value = notice.text
                }
                AccountPrompt.Kind.SWITCH -> {
                    withContext(NonCancellable) { accounts.confirmSwitch() }
                    syncMessage.value = null
                    continueAfterWipe()
                }
                AccountPrompt.Kind.MERGE -> {
                    syncMessage.value = null
                    enableSyncAndRun()
                }
            }
            syncing.value = false
        }
    }

    /** Keeps the previous ledger. A pending account switch or merge drops the new session instead; nothing is removed. */
    fun dismissAccountPrompt() {
        val current = prompt.value ?: return
        prompt.value = null
        when (current.kind) {
            AccountPrompt.Kind.SWITCH -> viewModelScope.launch { accounts.cancelSwitch() }
            AccountPrompt.Kind.MERGE -> viewModelScope.launch {
                disconnectUnsynced()
                syncMessage.value = "Not connected. This phone’s records stay here, and nothing was uploaded."
            }
            AccountPrompt.Kind.SIGN_OUT -> Unit
        }
    }

    private suspend fun followSignIn(check: AccountBoundary.AfterSignIn) {
        when (check) {
            AccountBoundary.AfterSignIn.Ready -> firstSyncCheck()
            AccountBoundary.AfterSignIn.Wiped -> continueAfterWipe()
            is AccountBoundary.AfterSignIn.ConfirmSwitch ->
                prompt.value = AccountPrompt(AccountPrompt.Kind.SWITCH, syncRepository.pendingChangeCount())
        }
    }

    /**
     * A phone that has never synced, signing in (P0-2, scenario C). When both this phone and the account hold records,
     * the user confirms joining them first — both are kept, nothing is replaced. When the account can't be checked,
     * nothing is uploaded and the new session is dropped, so a network error is never taken for an empty account.
     */
    private suspend fun firstSyncCheck() {
        val bound = syncPreferences.syncedUserId() != null
        val probe = if (bound) null else syncRepository.probeAccount()
        val localData = probe is AccountProbe.HasData && syncRepository.localHasFinancialData()
        when (firstSyncDecision(bound, probe, localData)) {
            FirstSync.SYNC -> enableSyncAndRun()
            FirstSync.ASK_MERGE -> prompt.value = AccountPrompt(AccountPrompt.Kind.MERGE, 0)
            FirstSync.DISCONNECT -> {
                disconnectUnsynced()
                syncMessage.value = "Couldn’t reach your account to check what’s already in it, so nothing was uploaded " +
                    "and you’re not connected. Your records are safe on this phone — try again when you’re online."
            }
        }
    }

    /** Ends a session that never synced from this phone: nothing to wipe, nothing uploaded. */
    private suspend fun disconnectUnsynced() {
        if (syncPreferences.syncedUserId() != null) return
        syncPreferences.setEnabled(false)
        auth.signOut()
        auth.revokeOnServer()
    }

    /** Read after the wipe has returned, not inferred from cancellation: this ViewModel may outlive the switch. */
    private suspend fun continueAfterWipe() {
        if (syncAfterAccountWipe(preferences.preferences.first().onboardingComplete)) enableSyncAndRun()
    }

    private suspend fun enableSyncAndRun() {
        // Lucento Pro: the account stays connected (free AI allowance, Shared invitations); only the upload waits for a plan.
        if (!planAllowsSyncNow()) {
            syncMessage.value = "Cloud sync is part of Lucento Pro. You’re signed in, and everything stays on this phone; a backup file is always free."
            return
        }
        syncPreferences.setEnabled(true)
        SyncScheduler.ensurePeriodic(application)
        runSyncNow()
    }

    private suspend fun planAllowsSyncNow(): Boolean {
        val plan = entitlements?.value ?: return true
        return com.lucento.app.domain.entitlement.ProGates.syncAllowedByPlan(plan, partnerOfSpace.first(), System.currentTimeMillis())
    }

    /** Email accounts only. Length and the breach check run before the password is sent. */
    fun changePassword(password: String) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            syncMessage.value = when (val result = auth.changePassword(password)) {
                AuthRepository.SignInResult.Success -> "Password updated."
                is AuthRepository.SignInResult.Failed -> result.message
                AuthRepository.SignInResult.NotConfigured -> "Cloud sync isn’t available in this version of Lucento yet."
                else -> "Couldn’t update the password. Try again."
            }
            syncing.value = false
        }
    }

    fun syncNow() {
        if (syncing.value || prompt.value != null) return
        viewModelScope.launch {
            syncing.value = true
            // Never synced from this phone (for example, the merge question was left unanswered): ask the same first-sync
            // question as at sign-in, so "Sync now" can't upload this phone's records into an account without consent.
            if (syncPreferences.syncedUserId() == null && auth.currentUserId() != null) firstSyncCheck() else runSyncNow()
            syncing.value = false
        }
    }

    private suspend fun runSyncNow() {
        syncMessage.value = null
        syncMessage.value = when (val result = syncRepository.sync()) {
            is SyncResult.Success -> {
                val r = result.report
                "Synced — ${r.pushed} sent, ${r.pulled} received" +
                    (if (r.conflicts > 0) ". ${r.conflicts} changed on two devices at once — the later edit was kept" else "") +
                    (if (r.parentsRestored > 0) ". ${r.parentsRestored} deleted account or category restored so a transaction was kept" else "")
            }
            SyncResult.NotSignedIn -> "Not connected — sign in to sync."
            SyncResult.AccountMismatch -> {
                prompt.value = AccountPrompt(AccountPrompt.Kind.SWITCH, syncRepository.pendingChangeCount())
                SyncRepository.ACCOUNT_MISMATCH_MESSAGE
            }
            SyncResult.NotConfigured -> "Cloud sync isn’t available in this version of Lucento yet."
            is SyncResult.Retryable -> "Sync couldn't finish (${result.message}) — your data is safe and it will retry."
            is SyncResult.MembershipRevoked -> "This phone is no longer part of that Shared Space."
            is SyncResult.UploadsPaused -> "Received ${result.report.pulled}. ${result.message}"
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    /** Multicurrency: "Get exchange rates online" (Settings › Money). */
    fun setFxRatesOnline(enabled: Boolean) {
        viewModelScope.launch { preferences.setFxRatesOnline(enabled) }
    }

    /** Switching a module never touches its data; it only changes what is shown. */
    /** Online voice; the screen shows the consent before passing true. */
    fun setOnlineVoice(enabled: Boolean) {
        viewModelScope.launch { preferences.setOnlineVoice(enabled) }
    }

    /** Smarter reading; turned on only from the consent dialog in Settings. */
    fun setSmartReading(enabled: Boolean) {
        viewModelScope.launch { preferences.setSmartReading(enabled) }
    }

    fun setAskLucentoAi(enabled: Boolean) {
        viewModelScope.launch { preferences.setAskLucentoAi(enabled) }
    }

    /**
     * The off choice is stored in DataStore and committed to `lucento_crash_reports` before collection
     * is disabled and unsent reports are dropped.
     */
    fun setCrashReports(enabled: Boolean) {
        viewModelScope.launch {
            CrashReportOptOut.apply(
                enabled = enabled,
                persistDataStore = preferences::writeCrashReportsPreference,
                persistMirror = preferences::commitCrashReportsMirror,
                setCollectionEnabled = CrashReporting::setCollectionEnabled,
                deleteUnsentReports = CrashReporting::deleteUnsentReports,
            )
        }
    }

    /** Anonymous usage counts. Off stops sending and clears the local queue. */
    fun setUsageCounts(enabled: Boolean) {
        val counts = usage ?: return
        viewModelScope.launch { counts.setOptIn(enabled) }
    }

    /**
     * P2-10. Called with true only after the system prompt succeeded, so this session counts as unlocked and the lock
     * doesn't close over Settings the moment it turns on.
     */
    fun setAppLock(enabled: Boolean) {
        if (enabled) appLock?.unlock()
        viewModelScope.launch { preferences.setAppLock(enabled) }
    }

    fun setHideInRecents(enabled: Boolean) {
        viewModelScope.launch { preferences.setHideInRecents(enabled) }
    }

    fun setFeatureEnabled(feature: Feature, enabled: Boolean) {
        viewModelScope.launch { preferences.setFeatureEnabled(feature, enabled) }
    }

    private var exporting = false
    private var pendingRestore: String? = null

    /** Writes every transaction as CSV to [uri] (chosen by the user through the system file picker). */
    fun exportCsv(resolver: ContentResolver, uri: Uri, onResult: (String) -> Unit) {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            val message = try {
                val all = transactions.observeTransactions().first()
                val walletNames = wallets.observeWallets().first().associate { it.id to it.name }
                val categoryNames = categories.observeCategories().first().associate { it.id to it.name }
                val csv = CsvExporter.export(all, walletNames, categoryNames)
                withContext(Dispatchers.IO) {
                    val stream = resolver.openOutputStream(uri) ?: error("Could not open the file")
                    stream.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                "Exported ${all.size} ${if (all.size == 1) "row" else "rows"}"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "Export failed — nothing was changed. Try another location."
            } finally {
                exporting = false
            }
            onResult(message)
        }
    }

    /** Writes a versioned JSON backup to [uri]. Does not include sign-in, crash reports, or goal photos. */
    fun exportBackup(resolver: ContentResolver, uri: Uri, onResult: (String) -> Unit) {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            val message = try {
                val json = withContext(Dispatchers.IO) { backup.export(BuildConfig.VERSION_NAME) }
                withContext(Dispatchers.IO) {
                    val stream = resolver.openOutputStream(uri) ?: error("Could not open the file")
                    stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                "Backup saved"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "Couldn’t save the backup. Nothing was changed."
            } finally {
                exporting = false
            }
            onResult(message)
        }
    }

    /** Reads [uri] and, when it is a valid backup, keeps it until [restoreBackup]. A bad file changes nothing. */
    fun inspectBackup(resolver: ContentResolver, uri: Uri, onPreview: (BackupPreview) -> Unit, onError: (String) -> Unit) {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readBackup(resolver, uri) }
                val preview = withContext(Dispatchers.IO) { backup.preview(text) }
                pendingRestore = text
                onPreview(preview)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BackupFormatException) {
                pendingRestore = null
                onError(e.message ?: "This file isn’t a valid Lucento backup. Nothing was changed.")
            } catch (e: Exception) {
                pendingRestore = null
                onError("Couldn’t read that file. Nothing was changed.")
            } finally {
                exporting = false
            }
        }
    }

    fun restoreBackup(mode: RestoreMode, onResult: (String) -> Unit) {
        val json = pendingRestore ?: return
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { backup.restore(json, mode) }
                pendingRestore = null
                if (mode == RestoreMode.REPLACE) "Replaced this device’s data with the backup." else "Merged the backup into this device."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BackupFormatException) {
                e.message ?: "This file isn’t a valid Lucento backup. Nothing was changed."
            } catch (e: Exception) {
                "Couldn’t finish restoring. Nothing was changed."
            } finally {
                exporting = false
            }
            onResult(message)
        }
    }

    private fun readBackup(resolver: ContentResolver, uri: Uri): String {
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw BackupFormatException(BackupFailure.MALFORMED, "Couldn’t read that file. Nothing was changed.")
        if (bytes.size > BackupCodec.MAX_CHARS) {
            throw BackupFormatException(BackupFailure.TOO_LARGE, "This backup is too large to restore. Nothing was changed.")
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw BackupFormatException(BackupFailure.MALFORMED, "This file isn’t valid JSON. Nothing was changed.")
        }
    }

    companion object {
        val Factory = applicationContainerFactory { app, container ->
            SettingsViewModel(
                application = app,
                preferences = container.preferences,
                transactions = container.transactions,
                wallets = container.wallets,
                categories = container.categories,
                auth = container.auth,
                syncPreferences = container.syncPreferences,
                syncRepository = container.syncRepository,
                accounts = container.accountBoundary,
                usage = container.usageCounts,
                backup = container.backup,
                appLock = container.appLock,
                entitlements = container.entitlements.state,
                partnerOfSpace = kotlinx.coroutines.flow.flow { container.syncPreferences.sharedSpaceOwner.collect { emit(it != null) } },
            )
        }
    }
}
