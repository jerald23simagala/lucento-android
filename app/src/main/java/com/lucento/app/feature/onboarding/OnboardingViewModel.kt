package com.lucento.app.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.lucento.app.core.appContainer
import com.lucento.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val savedState: SavedStateHandle,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    val step: StateFlow<Int> = savedState.getStateFlow(KEY_STEP, 0)
    val currency: StateFlow<String> =
        savedState.getStateFlow(KEY_CURRENCY, UserPreferencesRepository.defaultCurrency())

    private var finishing = false

    fun next() {
        savedState[KEY_STEP] = (step.value + 1).coerceAtMost(LAST_STEP)
    }

    fun back() {
        savedState[KEY_STEP] = (step.value - 1).coerceAtLeast(0)
    }

    fun selectCurrency(code: String) {
        savedState[KEY_CURRENCY] = code
    }

    fun finish() {
        if (finishing) return
        finishing = true
        viewModelScope.launch { preferences.completeOnboarding(currency.value) }
    }

    companion object {
        const val LAST_STEP = 2
        private const val KEY_STEP = "step"
        private const val KEY_CURRENCY = "currency"

        val Factory = viewModelFactory {
            initializer { OnboardingViewModel(createSavedStateHandle(), appContainer().preferences) }
        }
    }
}
