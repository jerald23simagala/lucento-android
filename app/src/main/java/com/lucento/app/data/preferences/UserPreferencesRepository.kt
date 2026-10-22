package com.lucento.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucento.app.core.money.CurrencyRules
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
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
    /** Optional modules the user has switched on. Defaults to everything for installs that predate the setting. */
    val features: FeatureSet = FeatureSet.ALL,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val primaryCurrency = stringPreferencesKey("primary_currency")
        val enabledFeatures = stringSetPreferencesKey("enabled_features")
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
                features = p[Keys.enabledFeatures]
                    ?.let { keys -> FeatureSet(keys.mapNotNull(Feature::fromKey).toSet()) }
                    ?: FeatureSet.ALL,
            )
        }

    /** Saves the onboarding choices in one atomic edit, then marks onboarding complete. */
    suspend fun completeOnboarding(primaryCurrency: String, themeMode: ThemeMode, features: Set<Feature>) {
        context.dataStore.edit {
            it[Keys.primaryCurrency] = primaryCurrency
            it[Keys.themeMode] = themeMode.name
            it[Keys.enabledFeatures] = features.map(Feature::key).toSet()
            it[Keys.onboardingComplete] = true
        }
    }

    /** Switches one optional module on or off. Never touches any financial data. */
    suspend fun setFeatureEnabled(feature: Feature, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.enabledFeatures]?.toSet() ?: Feature.entries.map(Feature::key).toSet()
            prefs[Keys.enabledFeatures] = if (enabled) current + feature.key else current - feature.key
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
