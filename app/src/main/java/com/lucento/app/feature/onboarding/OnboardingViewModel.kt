package com.lucento.app.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lucento.app.core.appContainer
import com.lucento.app.data.preferences.ThemeMode
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.domain.CategoryPresets
import com.lucento.app.feature.budget.CategoryPicks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FinancialFocus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Setup: welcome → focus → features → spending categories → review → currency & appearance → cloud sync (optional)
 * → confirmation.
 *
 * Focus and features can be skipped (safe defaults). Choosing spending categories cannot: the budget is built from
 * them, so at least one must be chosen and reviewed before onboarding completes. Categories are written to the
 * database first; only then is onboarding marked complete, so a finished onboarding always has the user's categories.
 * Choices only set the *initial* configuration; everything can be changed later. No personal information is collected.
 */
class OnboardingViewModel(
    private val savedState: SavedStateHandle,
    private val preferences: UserPreferencesRepository,
    private val categories: CategoryRepository,
    /** Optional cloud sync; null in tests. Nothing is sent unless the user connects an account on the sync step. */
    private val auth: com.lucento.app.data.sync.AuthRepository? = null,
    private val syncPreferences: com.lucento.app.data.sync.SyncPreferences? = null,
    private val appContext: android.content.Context? = null,
) : ViewModel() {

    /** The connected account's email, or null while signed out. */
    val signedInEmail: StateFlow<String?> = (auth?.observeSignedInEmail() ?: kotlinx.coroutines.flow.flowOf(null))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    val syncAvailable: Boolean get() = auth != null && com.lucento.app.data.sync.SupabaseModule.isConfigured
    val googleAvailable: Boolean get() = com.lucento.app.data.sync.SupabaseModule.isGoogleSignInConfigured

    /** Google's account picker runs from [activity]; it is used for this call only and not retained. */
    fun connectGoogle(activity: android.content.Context) = connect { auth?.signInWithGoogle(activity) }

    fun connectEmail(email: String, password: String, mode: com.lucento.app.feature.settings.EmailMode) = connect {
        if (mode == com.lucento.app.feature.settings.EmailMode.CREATE) auth?.signUpWithEmail(email, password) else auth?.signInWithEmail(email, password)
    }

    /**
     * Signing in turns sync on, but the first sync waits until onboarding finishes (see [finish]): pulling an existing
     * account's data in the middle of setup would mix it with categories the user hasn't confirmed yet.
     */
    private fun connect(signIn: suspend () -> com.lucento.app.data.sync.AuthRepository.SignInResult?) {
        if (_connecting.value) return
        _connecting.value = true
        _syncMessage.value = null
        viewModelScope.launch {
            try {
                _syncMessage.value = when (val result = signIn()) {
                    com.lucento.app.data.sync.AuthRepository.SignInResult.Success -> {
                        syncPreferences?.setEnabled(true)
                        null
                    }
                    com.lucento.app.data.sync.AuthRepository.SignInResult.Cancelled, null -> null
                    com.lucento.app.data.sync.AuthRepository.SignInResult.NotConfigured ->
                        "Cloud sync isn’t available in this version of Lucento yet. You can keep going — everything works offline."
                    is com.lucento.app.data.sync.AuthRepository.SignInResult.ConfirmEmail ->
                        "Almost there: open the link we sent to ${result.email}, then sign in here (or later in Settings)."
                    is com.lucento.app.data.sync.AuthRepository.SignInResult.Failed -> result.message
                }
            } finally {
                _connecting.value = false
            }
        }
    }

    val step: StateFlow<Int> = savedState.getStateFlow(KEY_STEP, 0)
    val currency: StateFlow<String> =
        savedState.getStateFlow(KEY_CURRENCY, UserPreferencesRepository.defaultCurrency())

    val focuses: StateFlow<Set<FinancialFocus>> = savedState.getStateFlow(KEY_FOCUS, arrayListOf(FinancialFocus.EVERYDAY_SPENDING.name))
        .map { names -> names.mapNotNull { n -> FinancialFocus.entries.firstOrNull { it.name == n } }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, setOf(FinancialFocus.EVERYDAY_SPENDING))

    val features: StateFlow<Set<Feature>> = savedState.getStateFlow(KEY_FEATURES, ArrayList(FinancialFocus.initialFeatures(emptySet()).map { it.key }))
        .map { keys -> keys.mapNotNull(Feature::fromKey).toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FinancialFocus.initialFeatures(emptySet()))

    val theme: StateFlow<ThemeMode> = savedState.getStateFlow(KEY_THEME, ThemeMode.SYSTEM.name)
        .map { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val picks: StateFlow<CategoryPicks> = combine(
        savedState.getStateFlow(KEY_PRESETS, ArrayList(CategoryPresets.all.filter { it.recommended }.map { it.key })),
        savedState.getStateFlow(KEY_CUSTOM, ArrayList<String>()),
    ) { keys, custom -> CategoryPicks(presetKeys = keys.toSet(), customNames = custom.toList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CategoryPicks(presetKeys = CategoryPresets.recommendedKeys))

    private val _error = MutableStateFlow<String?>(null)
    /** Shown when saving the categories failed; onboarding stays open with every choice intact. */
    val error: StateFlow<String?> = _error

    private var finishing = false

    fun next() {
        val current = step.value
        // Leaving the focus step: start the feature list from what the user said they care about.
        if (current == STEP_FOCUS) {
            savedState[KEY_FEATURES] = ArrayList(FinancialFocus.initialFeatures(focuses.value).map { it.key })
        }
        // Category selection is mandatory: the review step needs at least one category.
        if ((current == STEP_CATEGORIES || current == STEP_REVIEW) && picks.value.count == 0) return
        savedState[KEY_STEP] = (current + 1).coerceAtMost(LAST_STEP)
    }

    fun goTo(target: Int) {
        if (target in 0..LAST_STEP && target <= step.value) savedState[KEY_STEP] = target
    }

    fun setPicks(value: CategoryPicks) {
        savedState[KEY_PRESETS] = ArrayList(value.presetKeys.toList())
        savedState[KEY_CUSTOM] = ArrayList(value.customNames)
    }

    fun back() {
        savedState[KEY_STEP] = (step.value - 1).coerceAtLeast(0)
    }

    fun toggleFocus(focus: FinancialFocus) {
        val current = focuses.value
        val next = if (focus in current) current - focus else current + focus
        savedState[KEY_FOCUS] = ArrayList(next.map { it.name })
    }

    fun setFeature(feature: Feature, on: Boolean) {
        val next = if (on) features.value + feature else features.value - feature
        savedState[KEY_FEATURES] = ArrayList(next.map { it.key })
    }

    fun selectCurrency(code: String) {
        savedState[KEY_CURRENCY] = code
    }

    /** Applied immediately so the user sees the theme they pick. */
    fun selectTheme(mode: ThemeMode) {
        savedState[KEY_THEME] = mode.name
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun finish() {
        if (finishing || picks.value.count == 0) return
        finishing = true
        _error.value = null
        viewModelScope.launch {
            try {
                // Existing rows (only income categories on a fresh install) are matched by name, never duplicated.
                categories.applySelection(picks.value.choices(emptyList()))
                preferences.completeOnboarding(currency.value, theme.value, features.value)
                // Connected on the sync step: start syncing now that the user's own setup is saved.
                val context = appContext
                if (context != null && syncPreferences?.isEnabled() == true && signedInEmail.value != null) {
                    com.lucento.app.data.sync.SyncScheduler.ensurePeriodic(context)
                    com.lucento.app.data.sync.SyncScheduler.syncNow(context)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Lucento", "Saving onboarding categories failed", e)
                _error.value = "Your categories couldn’t be saved. Nothing was changed — please try again."
                finishing = false
            }
        }
    }

    /**
     * Skips the optional questions (focus and features) with safe defaults — every module on — and continues to the
     * spending categories, which are never skipped.
     */
    fun skip() {
        if (step.value >= STEP_CATEGORIES) return
        savedState[KEY_FEATURES] = ArrayList(Feature.entries.map { it.key })
        savedState[KEY_STEP] = STEP_CATEGORIES
    }

    companion object {
        const val STEP_WELCOME = 0
        const val STEP_FOCUS = 1
        const val STEP_FEATURES = 2
        const val STEP_CATEGORIES = 3
        const val STEP_REVIEW = 4
        const val STEP_ESSENTIALS = 5
        const val STEP_SYNC = 6
        const val STEP_CONFIRM = 7
        const val LAST_STEP = STEP_CONFIRM
        private const val KEY_STEP = "step"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_FOCUS = "focus"
        private const val KEY_FEATURES = "features"
        private const val KEY_THEME = "theme"
        private const val KEY_PRESETS = "category_presets"
        private const val KEY_CUSTOM = "category_custom"

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                OnboardingViewModel(
                    createSavedStateHandle(), container.preferences, container.categories,
                    auth = container.auth, syncPreferences = container.syncPreferences, appContext = app?.applicationContext,
                )
            }
        }
    }
}
