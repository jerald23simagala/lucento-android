package com.lucento.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferences: UserPreferencesRepository) : ViewModel() {

    val uiState: StateFlow<UserPreferences?> =
        preferences.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setPrimaryCurrency(code: String) {
        viewModelScope.launch { preferences.setPrimaryCurrency(code) }
    }

    companion object {
        val Factory = containerFactory { SettingsViewModel(it.preferences) }
    }
}
