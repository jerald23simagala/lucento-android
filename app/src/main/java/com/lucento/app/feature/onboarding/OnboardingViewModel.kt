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
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FinancialFocus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Short, skippable setup: welcome → focus → features → currency & appearance → confirmation.
 * Choices only set the *initial* module configuration; everything can be changed later in Settings.
 * No personal information is collected.
 */
class OnboardingViewModel(
    private val savedState: SavedStateHandle,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

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

    private var finishing = false

    fun next() {
        val current = step.value
        // Leaving the focus step: start the feature list from what the user said they care about.
        if (current == STEP_FOCUS) {
            savedState[KEY_FEATURES] = ArrayList(FinancialFocus.initialFeatures(focuses.value).map { it.key })
        }
        savedState[KEY_STEP] = (current + 1).coerceAtMost(LAST_STEP)
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
        if (finishing) return
        finishing = true
        viewModelScope.launch { preferences.completeOnboarding(currency.value, theme.value, features.value) }
    }

    /** Skips the remaining questions with safe defaults: every module on, system theme. */
    fun skip() {
        if (finishing) return
        finishing = true
        viewModelScope.launch { preferences.completeOnboarding(currency.value, theme.value, Feature.entries.toSet()) }
    }

    companion object {
        const val STEP_WELCOME = 0
        const val STEP_FOCUS = 1
        const val STEP_FEATURES = 2
        const val STEP_ESSENTIALS = 3
        const val STEP_CONFIRM = 4
        const val LAST_STEP = STEP_CONFIRM
        private const val KEY_STEP = "step"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_FOCUS = "focus"
        private const val KEY_FEATURES = "features"
        private const val KEY_THEME = "theme"

        val Factory = viewModelFactory {
            initializer { OnboardingViewModel(createSavedStateHandle(), appContainer().preferences) }
        }
    }
}
