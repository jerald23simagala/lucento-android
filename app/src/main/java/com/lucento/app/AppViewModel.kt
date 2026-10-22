package com.lucento.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AppUiState {
    data object Loading : AppUiState
    data class Ready(val preferences: UserPreferences) : AppUiState
}

class AppViewModel(preferences: UserPreferencesRepository) : ViewModel() {
    val uiState: StateFlow<AppUiState> = preferences.preferences
        .map<UserPreferences, AppUiState> { AppUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)

    companion object {
        val Factory = containerFactory { AppViewModel(it.preferences) }
    }
}
