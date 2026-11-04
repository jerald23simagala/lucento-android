package com.lucento.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferences
import com.lucento.app.data.preferences.UserPreferencesRepository
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lucento.app.data.media.ProfilePhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
    /** The profile picture file; null in tests. */
    private val photos: ProfilePhotos? = null,
    accountPhotoUrl: Flow<String?> = flowOf(null),
) : ViewModel() {
    /** The cloud account on this device, for the profile card; null when signed out. */
    val signedInEmail: StateFlow<String?> = signedInEmail.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The signed-in Google account's picture URL, when there is one: "Use my Google photo" is offered only then. */
    val googlePhotoUrl: StateFlow<String?> = accountPhotoUrl.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The profile photo for every avatar; reloaded from its file whenever it is set or removed. Null without one. */
    val profilePhoto: StateFlow<ImageBitmap?> = preferences.preferences
        .map { it.profilePhotoVersion }
        .distinctUntilChanged()
        .map { version -> if (version == null) null else photos?.load()?.asImageBitmap() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _photoBusy = MutableStateFlow(false)
    /** A photo is being saved (or downloaded from Google); the profile menu shows progress and ignores repeat taps. */
    val photoBusy: StateFlow<Boolean> = _photoBusy

    private val _photoMessage = MutableStateFlow<String?>(null)
    /** Why the last photo change didn't work, in words; cleared on the next attempt. */
    val photoMessage: StateFlow<String?> = _photoMessage

    /** Keeps the picked image (from the system photo picker) as the profile photo. Nothing leaves the phone. */
    fun setPhotoFromDevice(uri: Uri) = changePhoto("That picture couldn’t be used. Try another one.") { it.importFrom(uri) }

    /**
     * The user asked for their Google picture: one download from Google's image host (the only network use for a photo),
     * then it's kept on this phone like any other.
     */
    fun useGooglePhoto() {
        val url = googlePhotoUrl.value ?: return
        changePhoto("Couldn’t get your Google photo. Check your connection and try again.") { it.importFromGoogle(url) }
    }

    fun removePhoto() {
        val store = photos ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete() }
            preferences.setProfilePhotoVersion(null)
        }
    }

    private fun changePhoto(failure: String, save: (ProfilePhotos) -> Boolean) {
        val store = photos ?: return
        if (_photoBusy.value) return
        _photoBusy.value = true
        _photoMessage.value = null
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { save(store) }
                if (saved) preferences.setProfilePhotoVersion(System.currentTimeMillis()) else _photoMessage.value = failure
            } finally {
                _photoBusy.value = false
            }
        }
    }

    val uiState: StateFlow<AppUiState> = preferences.preferences
        .map<UserPreferences, AppUiState> { AppUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)

    /** Used when a disabled module is opened from a link: the user can switch it back on in place. */
    fun markAddSpotlightSeen() {
        viewModelScope.launch { preferences.setAddSpotlightSeen() }
    }

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
        val Factory = containerFactory {
            AppViewModel(it.preferences, it.auth.observeSignedInEmail(), it.profilePhotos, it.auth.observeAccountPhotoUrl())
        }
    }
}
