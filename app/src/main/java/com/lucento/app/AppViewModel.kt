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
import kotlinx.coroutines.flow.filterNotNull
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
    /** Lucento Pro: the plan this phone holds; null in tests (every theme allowed). */
    private val entitlements: StateFlow<com.lucento.app.domain.entitlement.EntitlementState>? = null,
    /** True once the plan has been re-read in this process (the notice below waits for it). */
    entitlementsReady: Flow<Boolean> = flowOf(false),
    /** The plan this phone last showed; null in tests (no notice). */
    private val planMemory: com.lucento.app.data.entitlement.PlanMemory? = null,
    /** Goals, for Lucento Pro's tour (null in tests). */
    goals: com.lucento.app.data.repository.GoalRepository? = null,
) : ViewModel() {
    /** Lucento Pro: the one-time "your plan has ended" notice (docs/MONETIZATION.md §6.3 rule 7); null when there is none. */
    private val _planNotice = MutableStateFlow<com.lucento.app.domain.entitlement.PlanNotice?>(null)
    val planNotice: StateFlow<com.lucento.app.domain.entitlement.PlanNotice?> = _planNotice
    private var noticeFor: com.lucento.app.domain.entitlement.DisplayPlan? = null

    init {
        val memory = planMemory
        if (entitlements != null && memory != null) {
            viewModelScope.launch {
                combine(entitlements, entitlementsReady) { plan, ready ->
                    if (ready) {
                        val now = System.currentTimeMillis()
                        plan.displayPlan(now) to plan.awaitingCheck(now)
                    } else {
                        null
                    }
                }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { (current, awaitingCheck) ->
                        val notice = com.lucento.app.domain.entitlement.PlanNotices.after(memory.lastSeen(), current, awaitingCheck)
                        noticeFor = current
                        _planNotice.value = notice
                        // Remembered at once when there is nothing to say; after the person has read it otherwise.
                        if (notice == null) memory.remember(current)
                    }
            }
        }
    }

    /** Lucento Pro's tour: whether any wallet / goal exists (an empty page has a different button to point at). */
    val hasWallets: StateFlow<Boolean> = (wallets?.observeWallets()?.map { it.isNotEmpty() } ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val hasGoals: StateFlow<Boolean> = (goals?.observeGoals()?.map { it.isNotEmpty() } ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Lucento Pro: the plan this phone holds right now (FREE in tests without one). */
    val plan: StateFlow<com.lucento.app.domain.entitlement.EntitlementState> =
        entitlements ?: MutableStateFlow(com.lucento.app.domain.entitlement.EntitlementState.FREE)

    /** Lucento Pro's welcome (owner, 2026-11-12): what to play now, or null. See [com.lucento.app.domain.entitlement.ProWelcome]. */
    private val _proWelcome = MutableStateFlow<com.lucento.app.domain.entitlement.ProWelcomeRun?>(null)
    val proWelcome: StateFlow<com.lucento.app.domain.entitlement.ProWelcomeRun?> = _proWelcome

    // What was started or presented in this process, before its stored copy lands: never played twice in a row.
    private val welcomedThisProcess = mutableSetOf<String>()
    private var medalPresentedThisProcess = false
    private var latestForWelcome: Triple<com.lucento.app.domain.entitlement.EntitlementState, Boolean, UserPreferences>? = null

    init {
        if (entitlements != null) {
            viewModelScope.launch {
                combine(entitlements, entitlementsReady, preferences.preferences) { plan, ready, prefs -> Triple(plan, ready, prefs) }
                    .collect { latest ->
                        latestForWelcome = latest
                        considerProWelcome()
                    }
            }
        }
    }

    /**
     * Starts the welcome when one is due: only once the plan has been re-read in this process (a stale cache never
     * welcomes anyone), after onboarding and the guided setup, and never over a welcome already playing.
     */
    private fun considerProWelcome() {
        val (plan, ready, prefs) = latestForWelcome ?: return
        if (!ready || _proWelcome.value != null) return
        if (!prefs.onboardingComplete || prefs.guideStep != null) return
        _proWelcome.value = com.lucento.app.domain.entitlement.ProWelcome.toStart(
            state = plan,
            now = System.currentTimeMillis(),
            welcomed = prefs.proWelcomed + welcomedThisProcess,
            memberSince = prefs.proMemberSince,
            presentedThisProcess = medalPresentedThisProcess,
        )
    }

    /** The welcome began: its plans are welcomed for good (stored now, so a crash mid-tour can never loop it). */
    fun proWelcomeStarted(run: com.lucento.app.domain.entitlement.ProWelcomeRun) {
        if (run.planKeys.isEmpty()) return
        welcomedThisProcess += run.planKeys
        viewModelScope.launch { preferences.markProWelcomed(run.planKeys) }
    }

    /** The Lucento Pro medal is on screen: its date is kept (once; the medal stays even if Pro later ends). */
    fun proMedalPresented() {
        medalPresentedThisProcess = true
        // Dated from when Pro began (a restore on a new phone keeps the person's real "member since").
        val since = _proWelcome.value?.since ?: System.currentTimeMillis()
        viewModelScope.launch { preferences.setProMemberSince(since) }
    }

    fun finishProWelcome() {
        _proWelcome.value = null
        considerProWelcome()
    }

    /** "See your Pro cards" on the Lucento Pro page: the cards and the collection again, without the medal (owner). */
    fun replayProTour() {
        if (_proWelcome.value != null) return
        _proWelcome.value = com.lucento.app.domain.entitlement.ProWelcomeRun(com.lucento.app.domain.entitlement.ProWelcomeMode.REPLAY, emptySet())
    }

    fun dismissPlanNotice() {
        val plan = noticeFor ?: return
        _planNotice.value = null
        viewModelScope.launch { planMemory?.remember(plan) }
    }
    /** Lucento Pro: the themes this plan may not pick (docs/MONETIZATION.md §3.8). A locked theme already chosen stays. */
    val lockedThemes: StateFlow<Set<ThemeId>> =
        if (entitlements == null) MutableStateFlow(emptySet())
        else entitlements.map { plan -> com.lucento.app.domain.entitlement.ProGates.lockedThemeIds(plan, System.currentTimeMillis()).mapNotNull { id -> ThemeId.entries.firstOrNull { it.name == id } }.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
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

    private var spaceRefresh: kotlinx.coroutines.Job? = null

    init {
        // Learn where this account stands in Lucento Shared (role, names, invitations): at start, and again whenever the
        // account on this phone changes or finishes onboarding — an account switch wipes what the last account knew, and
        // a new invitation for the next account used to wait for a restart (owner, 2026-11-06: it only showed in
        // Settings). LucentoRoot also asks each time the app comes back to the foreground.
        if (sharedSpace != null) {
            viewModelScope.launch {
                combine(signedInEmail, preferences.preferences.map { it.onboardingComplete }) { email, onboarded -> email to onboarded }
                    .distinctUntilChanged()
                    .collect { refreshSharedSpace() }
            }
        }
    }

    /** Lucento Shared: asks where this account stands, quietly and at most once a minute (one ask at a time). */
    fun refreshSharedSpace() {
        val space = sharedSpace ?: return
        if (spaceRefresh?.isActive == true) return
        spaceRefresh = viewModelScope.launch { runCatching { space.refreshIfStale(SPACE_FRESH_MILLIS) } }
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
        if (theme in lockedThemes.value) return // the picker shows why; nothing is persisted
        viewModelScope.launch { preferences.setThemeId(theme) }
    }

    companion object {
        /** How long an answer about Lucento Shared counts as fresh when the app comes back or the account changes. */
        const val SPACE_FRESH_MILLIS = 60_000L

        val Factory = containerFactory {
            AppViewModel(it.preferences, it.auth.observeSignedInEmail(), it.profilePhotos, it.auth.observeAccountPhotoUrl(), it.sharedSpace, it.wallets, it.transactions, it.entitlements.state, it.entitlements.ready, it.planMemory, it.goals)
        }
    }
}
