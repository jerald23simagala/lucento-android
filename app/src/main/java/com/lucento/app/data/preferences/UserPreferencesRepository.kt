package com.lucento.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucento.app.core.money.CurrencyRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Currency
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val onboardingComplete: Boolean,
    val themeMode: ThemeMode,
    val primaryCurrency: String,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val primaryCurrency = stringPreferencesKey("primary_currency")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserPreferences(
                onboardingComplete = p[Keys.onboardingComplete] ?: false,
                themeMode = p[Keys.themeMode]
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                primaryCurrency = p[Keys.primaryCurrency]
                    ?.takeIf { CurrencyRules.isSupported(it) }
                    ?: defaultCurrency(),
            )
        }

    suspend fun completeOnboarding(primaryCurrency: String) {
        context.dataStore.edit {
            it[Keys.primaryCurrency] = primaryCurrency
            it[Keys.onboardingComplete] = true
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setPrimaryCurrency(code: String) {
        require(CurrencyRules.isSupported(code))
        context.dataStore.edit { it[Keys.primaryCurrency] = code }
    }

    companion object {
        fun defaultCurrency(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
                ?.takeIf { it != "XXX" }
                ?: "USD"
    }
}
