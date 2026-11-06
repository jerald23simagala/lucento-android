package com.lucento.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucento.app.core.containerFactory
import com.lucento.app.data.preferences.ProfilePhotoChoice
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.ThemeMode
import kotlinx.coroutines.flow.combine
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

/**
 * Whether to fetch the Google account's picture on its own: signed in with Google ([url]), the choice still the default
 * ([ProfilePhotoChoice.GOOGLE]), no photo on the phone ([version] null), and not already tried for this URL in this
 * process. Never over a photo the person picked or removed.
 */
internal fun shouldFetchGooglePhoto(url: String?, choice: ProfilePhotoChoice, version: Long?, alreadyTried: String?): Boolean =
    url != null && choice == ProfilePhotoChoice.GOOGLE && version == null && url != alreadyTried

class AppViewModel(
    private val preferences: UserPreferencesRepository,
    signedInEmail: Flow<String?> = flowOf(null),
    /** The profile picture file; null in tests. */
    private val photos: ProfilePhotos? = null,
    accountPhotoUrl: Flow<String?> = flowOf(null),
    /** Lucento Shared (docs/shared); null in tests. */
    private val sharedSpace: com.lucento.app.data.sharedspace.SharedSpaceRepository? = null,
    /** Multicurrency: for the app-wide "≈ ₱…" values (null in tests: none shown). */
    wallets: com.lucento.app.data.repository.WalletRepository? = null,
    transactions: com.lucento.app.data.repository.TransactionRepository? = null,
) : ViewModel() {
    /** Multicurrency: every wallet's and movement's value in the main currency, for the "≈ ₱…" lines under them. */
    val fxDisplay: StateFlow<com.lucento.app.domain.fx.FxDisplay> =
        if (wallets == null || transactions == null) {
            kotlinx.coroutines.flow.MutableStateFlow(com.lucento.app.domain.fx.FxDisplay.NONE)
        } else {
            combine(
                wallets.observeWallets(),
                transactions.observeTransactions(),
                preferences.preferences.map { it.primaryCurrency }.distinctUntilChanged(),
            ) { w, t, main -> com.lucento.app.domain.fx.FxDisplay.of(main, w, t) }
                .flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.Eagerly, com.lucento.app.domain.fx.FxDisplay.NONE)
        }

    /** Lucento Shared: what this phone may change (a Partner's phone doesn't change the financial structure). */
    val sharedAccess: StateFlow<com.lucento.app.domain.SharedAccess> = (sharedSpace?.access ?: flowOf(com.lucento.app.domain.SharedAccess.Solo))
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.lucento.app.domain.SharedAccess.Solo)

    /** Lucento Shared: names for "Added by …" on transactions. */
    val people: StateFlow<com.lucento.app.domain.PeopleDirectory> = (sharedSpace?.people ?: flowOf(com.lucento.app.domain.PeopleDirectory()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.lucento.app.domain.PeopleDirectory())

    /**
     * Lucento Shared, for Home's prompt: an invitation waiting for this account, or a membership this phone doesn't show
     * yet — never on a phone that already holds a space, and not once put aside.
     */
    val spacePrompt: StateFlow<com.lucento.app.data.sharedspace.SpacePrompt?> =
        (sharedSpace?.prompt ?: flowOf(null)).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Learn where this account stands in Lucento Shared (role, names, invitations); quiet and at most every 5 minutes.
        sharedSpace?.let { space -> viewModelScope.launch { runCatching { space.refreshIfStale() } } }
    }

    /** Home's prompt: "Not now" (what it points to stays in Settings › Lucento Shared). */
    fun dismissSpacePrompt(key: String) {
        viewModelScope.launch { sharedSpace?.dismiss(key) }
    }
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
    fun setPhotoFromDevice(uri: Uri) =
        changePhoto("That picture couldn’t be used. Try another one.", ProfilePhotoChoice.OWN) { it.importFrom(uri) }

    /**
     * Back to the Google picture (the default): one download from Google's image host, then it's kept on this phone like
     * any other.
     */
    fun useGooglePhoto() {
        val url = googlePhotoUrl.value ?: return
        changePhoto("Couldn’t get your Google photo. Check your connection and try again.", ProfilePhotoChoice.GOOGLE) {
            it.importFromGoogle(url)
        }
    }

    /** Removed by choice: initials from now on, and the Google picture isn't fetched again for this account. */
    fun removePhoto() {
        val store = photos ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete() }
            preferences.setProfilePhoto(version = null, choice = ProfilePhotoChoice.NONE)
        }
    }

    private fun changePhoto(failure: String, choice: ProfilePhotoChoice, save: (ProfilePhotos) -> Boolean) {
        val store = photos ?: return
        if (_photoBusy.value) return
        _photoBusy.value = true
        _photoMessage.value = null
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { save(store) }
                if (saved) preferences.setProfilePhoto(System.currentTimeMillis(), choice) else _photoMessage.value = failure
            } finally {
                _photoBusy.value = false
            }
        }
    }

    /** The Google picture URL last fetched on its own in this process, so a failure (offline) isn't retried in a loop. */
    private var autoPhotoTried: String? = null

    init {
        // Google by default (owner, 2026-11-05): signed in with Google, no photo on the phone and the choice still
        // GOOGLE, the account's picture is fetched once, quietly. A photo the person picked, or a removal, is left alone
        // until the account changes (the account wipe resets the choice). A failed fetch tries again on the next launch.
        viewModelScope.launch {
            combine(
                googlePhotoUrl,
                preferences.preferences.map { it.profilePhotoChoice to it.profilePhotoVersion }.distinctUntilChanged(),
            ) { url, photo -> url to photo }
                .collect { (url, photo) ->
                    val (choice, version) = photo
                    if (!shouldFetchGooglePhoto(url, choice, version, autoPhotoTried) || _photoBusy.value) return@collect
                    val store = photos ?: return@collect
                    autoPhotoTried = url
                    _photoBusy.value = true
                    try {
                        val saved = withContext(Dispatchers.IO) { store.importFromGoogle(url!!) }
                        if (saved) preferences.setProfilePhoto(System.currentTimeMillis(), ProfilePhotoChoice.GOOGLE)
                    } finally {
                        _photoBusy.value = false
                    }
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

    fun setThemeId(theme: ThemeId) {
        viewModelScope.launch { preferences.setThemeId(theme) }
    }

    companion object {
        val Factory = containerFactory {
            AppViewModel(it.preferences, it.auth.observeSignedInEmail(), it.profilePhotos, it.auth.observeAccountPhotoUrl(), it.sharedSpace, it.wallets, it.transactions)
        }
    }
}
