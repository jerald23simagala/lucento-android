package com.lucento.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.lucento.app.domain.Feature

sealed interface AppUiState {
    data object Loading : AppUiState
    data class Ready(val preferences: UserPreferences) : AppUiState
}

class AppViewModel(
    private val preferences: UserPreferencesRepository,
    signedInEmail: Flow<String?> = flowOf(null),
) : ViewModel() {
    /** The cloud account on this device, for the profile card; null when signed out. */
    val signedInEmail: StateFlow<String?> = signedInEmail.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<AppUiState> = preferences.preferences
        .map<UserPreferences, AppUiState> { AppUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)

    /** Used when a disabled module is opened from a link: the user can switch it back on in place. */
    fun setFeatureEnabled(feature: Feature, enabled: Boolean) {
        viewModelScope.launch { preferences.setFeatureEnabled(feature, enabled) }
    }

    /** Profile menu: the optional name shown on the avatar (stored only on this device). */
    fun setDisplayName(name: String) {
        viewModelScope.launch { preferences.setDisplayName(name) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    companion object {
        val Factory = containerFactory { AppViewModel(it.preferences, it.auth.observeSignedInEmail()) }
    }
}
