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
data class CloudSyncUiState(
    val configured: Boolean,
    val googleSignInConfigured: Boolean,
    val signedInEmail: String?,
    val status: SyncStatus,
    val syncing: Boolean = false,
    val lastMessage: String? = null,
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
) : ViewModel() {

    private val syncing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState?> =
        combine(
            preferences.preferences,
            wallets.observeWallets(),
            transactions.observeTransactions(),
            syncPreferences.status,
            syncing,
            syncMessage,
            auth.observeSignedInEmail(),
        ) { values ->
            val prefs = values[0] as UserPreferences
            val w = values[1] as List<*>
            val t = values[2] as List<*>
            val syncStatus = values[3] as SyncStatus
            val isSyncing = values[4] as Boolean
            val message = values[5] as String?
            val email = values[6] as String?
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
                ),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Signs in, then turns cloud sync on and runs a first sync immediately. Never touches local data itself. */
    /** [activity] hosts Google's account picker; it is passed through for this call only and never stored. */
    fun connectGoogleAccount(activity: Context) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            syncMessage.value = null
            when (val result = auth.signInWithGoogle(activity)) {
                AuthRepository.SignInResult.Success -> {
                    syncPreferences.setEnabled(true)
                    SyncScheduler.ensurePeriodic(application)
                    runSyncNow()
                }
                AuthRepository.SignInResult.Cancelled -> Unit
                AuthRepository.SignInResult.NotConfigured ->
                    syncMessage.value = "Cloud sync isn’t available in this version of Lucento yet."
                is AuthRepository.SignInResult.Failed -> syncMessage.value = result.message
            }
            syncing.value = false
        }
    }

    /** Signs out and turns sync off. Local wallets, transactions, budgets and goals are never touched. */
    fun disconnectAccount() {
        viewModelScope.launch {
            SyncScheduler.cancelPeriodic(application)
            syncPreferences.setEnabled(false)
            auth.signOut()
            syncMessage.value = null
        }
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
            SyncResult.AccountMismatch -> SyncRepository.ACCOUNT_MISMATCH_MESSAGE
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
            )
        }
    }
}
