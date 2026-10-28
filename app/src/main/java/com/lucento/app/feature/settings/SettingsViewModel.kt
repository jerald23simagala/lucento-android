package com.lucento.app.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.Context
import com.lucento.app.core.applicationContainerFactory
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.sync.AccountBoundary
import com.lucento.app.data.sync.AuthRepository
import com.lucento.app.data.sync.SupabaseModule
import com.lucento.app.data.sync.SyncPreferences
import com.lucento.app.data.sync.SyncRepository
import com.lucento.app.data.sync.SyncResult
import com.lucento.app.data.sync.SyncScheduler
import com.lucento.app.data.sync.SyncStatus
import com.lucento.app.domain.CsvExporter
import com.lucento.app.domain.Feature
import kotlinx.coroutines.Dispatchers
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
/** A wipe the user still has to confirm. [unsynced] means changes that have not reached the server would be lost. */
data class AccountPrompt(val kind: Kind, val unsynced: Boolean) {
    enum class Kind { SIGN_OUT, SWITCH }
}

data class CloudSyncUiState(
    val configured: Boolean,
    val googleSignInConfigured: Boolean,
    val signedInEmail: String?,
    val status: SyncStatus,
    val syncing: Boolean = false,
    val lastMessage: String? = null,
    val prompt: AccountPrompt? = null,
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
) : ViewModel() {

    private val syncing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow<String?>(null)
    private val prompt = MutableStateFlow<AccountPrompt?>(null)

    init {
        // A previous session can already be a different account (the app was closed on the confirm step).
        // Ready means "same account or signed out" — opening Settings must not start a sync on its own.
        viewModelScope.launch {
            when (val check = accounts.afterSignIn()) {
                AccountBoundary.AfterSignIn.Ready -> Unit
                AccountBoundary.AfterSignIn.Wiped -> enableSyncAndRun()
                is AccountBoundary.AfterSignIn.ConfirmSwitch ->
                    prompt.value = AccountPrompt(AccountPrompt.Kind.SWITCH, check.unsynced)
            }
        }
    }

    val uiState: StateFlow<SettingsUiState?> =
        combine(
            preferences.preferences,
            wallets.observeWallets(),
            transactions.observeTransactions(),
            syncPreferences.status,
            syncing,
            syncMessage,
            auth.observeSignedInEmail(),
            prompt,
        ) { values ->
            val prefs = values[0] as UserPreferences
            val w = values[1] as List<*>
            val t = values[2] as List<*>
            val syncStatus = values[3] as SyncStatus
            val isSyncing = values[4] as Boolean
            val message = values[5] as String?
            val email = values[6] as String?
            val accountPrompt = values[7] as AccountPrompt?
            SettingsUiState(
                preferences = prefs,
                walletCount = w.size,
                transactionCount = t.size,
                cloudSync = CloudSyncUiState(
                    configured = SupabaseModule.isConfigured,
                    googleSignInConfigured = SupabaseModule.isGoogleSignInConfigured,
                    signedInEmail = email,
                    status = syncStatus,
                    syncing = isSyncing,
                    lastMessage = message,
                    prompt = accountPrompt,
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

    /** An email account: [mode] creates one or signs in to an existing one; then sync turns on as for Google. */
    fun connectEmailAccount(email: String, password: String, mode: EmailMode) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            syncMessage.value = null
            handleSignIn(
                if (mode == EmailMode.CREATE) auth.signUpWithEmail(email, password) else auth.signInWithEmail(email, password)
            )
            syncing.value = false
        }
    }

    private suspend fun handleSignIn(result: AuthRepository.SignInResult) {
        when (result) {
            AuthRepository.SignInResult.Success -> followSignIn(accounts.afterSignIn())
            AuthRepository.SignInResult.Cancelled -> Unit
            AuthRepository.SignInResult.NotConfigured ->
                syncMessage.value = "Cloud sync isn’t available in this version of Lucento yet."
            is AuthRepository.SignInResult.ConfirmEmail ->
                syncMessage.value = "Almost there: open the link we sent to ${result.email}, then come back and sign in."
            is AuthRepository.SignInResult.Failed -> syncMessage.value = result.message
        }
    }

    /** Asks before removing this account's data from the phone. Nothing is deleted until [confirmAccountPrompt]. */
    fun disconnectAccount() {
        if (syncing.value) return
        viewModelScope.launch {
            prompt.value = AccountPrompt(AccountPrompt.Kind.SIGN_OUT, syncRepository.hasUnsyncedChanges())
        }
    }

    fun confirmAccountPrompt() {
        val current = prompt.value ?: return
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            prompt.value = null
            when (current.kind) {
                AccountPrompt.Kind.SIGN_OUT -> {
                    accounts.signOutAndWipe()
                    syncMessage.value = "Signed out. This account’s data has been removed from this phone."
                }
                AccountPrompt.Kind.SWITCH -> {
                    accounts.confirmSwitch()
                    syncMessage.value = null
                    enableSyncAndRun()
                }
            }
            syncing.value = false
        }
    }

    /** Keeps the previous ledger. A pending account switch drops the new session instead. */
    fun dismissAccountPrompt() {
        val current = prompt.value ?: return
        prompt.value = null
        if (current.kind == AccountPrompt.Kind.SWITCH) {
            viewModelScope.launch { accounts.cancelSwitch() }
        }
    }

    private suspend fun followSignIn(check: AccountBoundary.AfterSignIn) {
        when (check) {
            AccountBoundary.AfterSignIn.Ready, AccountBoundary.AfterSignIn.Wiped -> enableSyncAndRun()
            is AccountBoundary.AfterSignIn.ConfirmSwitch ->
                prompt.value = AccountPrompt(AccountPrompt.Kind.SWITCH, check.unsynced)
        }
    }

    private suspend fun enableSyncAndRun() {
        syncPreferences.setEnabled(true)
        SyncScheduler.ensurePeriodic(application)
        runSyncNow()
    }

    fun syncNow() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            runSyncNow()
            syncing.value = false
        }
    }

    private suspend fun runSyncNow() {
        syncMessage.value = null
        syncMessage.value = when (val result = syncRepository.sync()) {
            is SyncResult.Success -> {
                val r = result.report
                "Synced — ${r.pushed} sent, ${r.pulled} received" +
                    if (r.conflicts > 0) ". ${r.conflicts} changed on two devices at once — the later edit was kept" else ""
            }
            SyncResult.NotSignedIn -> "Not connected — sign in to sync."
            SyncResult.AccountMismatch -> {
                prompt.value = AccountPrompt(AccountPrompt.Kind.SWITCH, syncRepository.hasUnsyncedChanges())
                SyncRepository.ACCOUNT_MISMATCH_MESSAGE
            }
            SyncResult.NotConfigured -> "Cloud sync isn’t available in this version of Lucento yet."
            is SyncResult.Retryable -> "Sync couldn't finish (${result.message}) — your data is safe and it will retry."
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setPrimaryCurrency(code: String) {
        viewModelScope.launch { preferences.setPrimaryCurrency(code) }
    }

    /** Switching a module never touches its data; it only changes what is shown. */
    /** Smarter reading; turned on only from the consent dialog in Settings. */
    fun setSmartReading(enabled: Boolean) {
        viewModelScope.launch { preferences.setSmartReading(enabled) }
    }

    fun setFeatureEnabled(feature: Feature, enabled: Boolean) {
        viewModelScope.launch { preferences.setFeatureEnabled(feature, enabled) }
    }

    private var exporting = false

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
            )
        }
    }
}
