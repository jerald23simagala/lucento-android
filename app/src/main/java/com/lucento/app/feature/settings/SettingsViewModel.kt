package com.lucento.app.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.CsvExporter
import com.lucento.app.domain.Feature
import kotlinx.coroutines.Dispatchers
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
)

class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    private val transactions: TransactionRepository,
    private val wallets: WalletRepository,
    private val categories: CategoryRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState?> =
        combine(preferences.preferences, wallets.observeWallets(), transactions.observeTransactions()) { prefs, w, t ->
            SettingsUiState(prefs, w.size, t.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        val Factory = containerFactory { SettingsViewModel(it.preferences, it.transactions, it.wallets, it.categories) }
    }
}
