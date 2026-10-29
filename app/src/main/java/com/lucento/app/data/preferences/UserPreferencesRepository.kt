package com.lucento.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lucento.app.core.crash.CrashReportsFlag
import com.lucento.app.core.money.CurrencyRules
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FeatureSet
import com.lucento.app.domain.UsageLedger
import com.lucento.app.domain.UsageLedgerCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
    /** Last month whose kept budget was acknowledged ("2026-09"), so the moment is shown only once. */
    val acknowledgedBudgetMonth: java.time.YearMonth? = null,
    /** Optional name shown on the profile button and menu. Stored only on this device; never required. */
    val displayName: String? = null,
    /**
     * True once the user has chosen their budget categories (in onboarding since 0.5, or later from the Budget
     * screen). Installs that completed onboarding before 0.5 start false: they keep their categories and are offered
     * the category setup on the Budget screen, never forced back through onboarding.
     */
    val categorySetupDone: Boolean = false,
    /**
     * The wallet new transactions go to unless the user picks another (cash, bank or e-wallet only). Null when none is
     * chosen; the composer then asks. May point at a wallet that no longer exists — readers check it against the list.
     */
    val defaultWalletId: Long? = null,
    /**
     * Smarter reading: typed messages are read by Gemini through Lucento's proxy. Off by default, turned on only by the
     * user after the consent dialog in Settings; nothing is sent to a model while it is off.
     */
    val smartReading: Boolean = false,
    /** Crash reports. Off until the user turns them on in Settings. Nothing is sent while this is false. */
    val crashReports: Boolean = false,
    /** Anonymous usage counts. Off until the user opts in. Nothing is sent while this is false. */
    val usageCounts: Boolean = false,
    /**
     * The one-time Home note that data stays on this phone and uninstalling erases it. New setup marks this seen.
     * Installs that finished onboarding before the note existed start unseen, so they are told once.
     */
    val localDataNoticeSeen: Boolean = false,
    /** Where the guided setup after onboarding stands; null when finished or never started (older installs). */
    val guideStep: com.lucento.app.domain.GuideStep? = null,
    /** Budget suggestions stay hidden until this date (exclusive). Null when they have not been snoozed. */
    val budgetSuggestionSnoozedUntil: java.time.LocalDate? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = UserPreferencesRepository.DATA_STORE_NAME)

class UserPreferencesRepository(
    private val context: Context,
    private val crashReportsFlag: CrashReportsFlag.Store = SharedPreferencesCrashReportsStore(context),
) {

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val primaryCurrency = stringPreferencesKey("primary_currency")
        val enabledFeatures = stringSetPreferencesKey("enabled_features")
        val acknowledgedBudgetMonth = stringPreferencesKey("acknowledged_budget_month")
        val displayName = stringPreferencesKey("display_name")
        val categorySetupDone = booleanPreferencesKey("category_setup_done")
        val defaultWalletId = longPreferencesKey("default_wallet_id")
        val guideStep = stringPreferencesKey("guide_step")
        val smartReading = booleanPreferencesKey("smart_reading")
        val crashReports = booleanPreferencesKey("crash_reports")
        val usageCounts = booleanPreferencesKey("usage_counts")
        val usageInstallId = stringPreferencesKey("usage_install_id")
        val usageInstalledAt = longPreferencesKey("usage_installed_at")
        val usageOnboardingStartedAt = longPreferencesKey("usage_onboarding_started_at")
        val usageOnboardingSeen = booleanPreferencesKey("usage_onboarding_seen")
        val usageQueue = stringPreferencesKey("usage_queue")
        val usageSent = stringSetPreferencesKey("usage_sent")
        val usageAdjustPending = booleanPreferencesKey("usage_adjust_pending")
        val usagePendingDeletion = stringPreferencesKey("usage_pending_deletion_install_id")
        val localDataNoticeSeen = booleanPreferencesKey("local_data_notice_seen")
        val budgetSuggestionSnoozedUntil = stringPreferencesKey("budget_suggestion_snoozed_until")
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
                acknowledgedBudgetMonth = p[Keys.acknowledgedBudgetMonth]
                    ?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() },
                displayName = p[Keys.displayName]?.trim()?.takeIf { it.isNotEmpty() },
                categorySetupDone = p[Keys.categorySetupDone] ?: false,
                defaultWalletId = p[Keys.defaultWalletId],
                guideStep = com.lucento.app.domain.GuideStep.fromKey(p[Keys.guideStep]),
                smartReading = p[Keys.smartReading] ?: false,
                crashReports = p[Keys.crashReports] ?: false,
                usageCounts = p[Keys.usageCounts] ?: false,
                localDataNoticeSeen = p[Keys.localDataNoticeSeen] ?: false,
                budgetSuggestionSnoozedUntil = p[Keys.budgetSuggestionSnoozedUntil]
                    ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            )
        }

    /**
     * Saves the onboarding choices in one atomic edit, then marks onboarding complete. Called only after the chosen
     * categories were written to the database, so a completed onboarding always has the user's categories.
     */
    suspend fun completeOnboarding(primaryCurrency: String, themeMode: ThemeMode, features: Set<Feature>) {
        context.dataStore.edit {
            it[Keys.primaryCurrency] = primaryCurrency
            it[Keys.themeMode] = themeMode.name
            it[Keys.enabledFeatures] = features.map(Feature::key).toSet()
            it[Keys.categorySetupDone] = true
            it[Keys.onboardingComplete] = true
            it[Keys.localDataNoticeSeen] = true
            // A new user continues into the guided setup (wallets, budget, goal, first transactions).
            it[Keys.guideStep] = com.lucento.app.domain.GuideStep.WALLETS.key
        }
    }

    /**
     * Finishes onboarding for an account restored on a new phone (its data already pulled by the first sync): the
     * device-only preferences are set in one edit and the guided setup is skipped, since the account is set up.
     * [categoriesChosen] is false when the account has no spending categories yet, so the category setup still asks.
     */
    suspend fun completeRestore(primaryCurrency: String, themeMode: ThemeMode, features: Set<Feature>, categoriesChosen: Boolean) {
        context.dataStore.edit {
            it[Keys.primaryCurrency] = primaryCurrency
            it[Keys.themeMode] = themeMode.name
            it[Keys.enabledFeatures] = features.map(Feature::key).toSet()
            it[Keys.categorySetupDone] = categoriesChosen
            it[Keys.onboardingComplete] = true
            it[Keys.localDataNoticeSeen] = true
            it.remove(Keys.guideStep)
        }
    }

    suspend fun setSmartReading(enabled: Boolean) {
        context.dataStore.edit { it[Keys.smartReading] = enabled }
    }

    /** DataStore copy of the crash-report choice. Written before the synchronous mirror. */
    suspend fun writeCrashReportsPreference(enabled: Boolean) {
        context.dataStore.edit { it[Keys.crashReports] = enabled }
    }

    /** Synchronous `lucento_crash_reports` mirror. [CrashReportsFlag.set] uses commit(), not apply(). */
    fun commitCrashReportsMirror(enabled: Boolean) {
        CrashReportsFlag.set(crashReportsFlag, enabled)
    }

    suspend fun readUsageLedger(): UsageLedger {
        val prefs = context.dataStore.data.first()
        return UsageLedger(
            optedIn = prefs[Keys.usageCounts] ?: false,
            installId = prefs[Keys.usageInstallId],
            installedAtMillis = prefs[Keys.usageInstalledAt],
            onboardingStartedAtMillis = prefs[Keys.usageOnboardingStartedAt],
            onboardingSeen = prefs[Keys.usageOnboardingSeen] ?: false,
            queue = UsageLedgerCodec.decode(prefs[Keys.usageQueue].orEmpty()),
            sent = prefs[Keys.usageSent].orEmpty(),
            adjustPending = prefs[Keys.usageAdjustPending] ?: false,
            pendingDeletionInstallId = prefs[Keys.usagePendingDeletion],
        )
    }

    suspend fun writeUsageLedger(ledger: UsageLedger) {
        context.dataStore.edit { prefs ->
            prefs[Keys.usageCounts] = ledger.optedIn
            if (ledger.installId == null) prefs.remove(Keys.usageInstallId) else prefs[Keys.usageInstallId] = ledger.installId
            if (ledger.installedAtMillis == null) prefs.remove(Keys.usageInstalledAt) else prefs[Keys.usageInstalledAt] = ledger.installedAtMillis
            if (ledger.onboardingStartedAtMillis == null) {
                prefs.remove(Keys.usageOnboardingStartedAt)
            } else {
                prefs[Keys.usageOnboardingStartedAt] = ledger.onboardingStartedAtMillis
            }
            prefs[Keys.usageOnboardingSeen] = ledger.onboardingSeen
            prefs[Keys.usageQueue] = UsageLedgerCodec.encode(ledger.queue)
            prefs[Keys.usageSent] = ledger.sent
            prefs[Keys.usageAdjustPending] = ledger.adjustPending
            if (ledger.pendingDeletionInstallId == null) {
                prefs.remove(Keys.usagePendingDeletion)
            } else {
                prefs[Keys.usagePendingDeletion] = ledger.pendingDeletionInstallId
            }
        }
    }

    suspend fun setLocalDataNoticeSeen() {
        context.dataStore.edit { it[Keys.localDataNoticeSeen] = true }
    }







    suspend fun setBudgetSuggestionSnoozedUntil(date: java.time.LocalDate) {
        context.dataStore.edit { it[Keys.budgetSuggestionSnoozedUntil] = date.toString() }
    }

    /** Saves the guided setup's progress; null when it is finished (or was never started, as for older installs). */
    suspend fun setGuideStep(step: com.lucento.app.domain.GuideStep?) {
        context.dataStore.edit { if (step == null) it.remove(Keys.guideStep) else it[Keys.guideStep] = step.key }
    }

    suspend fun setCategorySetupDone(done: Boolean) {
        context.dataStore.edit { it[Keys.categorySetupDone] = done }
    }

    /** Blank clears the name. */
    suspend fun setDisplayName(name: String) {
        val cleaned = name.trim().replace(Regex("\\s+"), " ").take(MAX_DISPLAY_NAME)
        context.dataStore.edit { prefs ->
            if (cleaned.isEmpty()) {
                prefs.remove(Keys.displayName)
            } else {
                prefs[Keys.displayName] = cleaned
            }
            Unit
        }
    }

    /** Switches one optional module on or off. Never touches any financial data. */
    suspend fun setFeatureEnabled(feature: Feature, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.enabledFeatures]?.toSet() ?: Feature.entries.map(Feature::key).toSet()
            prefs[Keys.enabledFeatures] = if (enabled) current + feature.key else current - feature.key
        }
    }

    /** Null clears the default. */
    suspend fun setDefaultWallet(walletId: Long?) {
        context.dataStore.edit { prefs ->
            if (walletId == null) prefs.remove(Keys.defaultWalletId) else prefs[Keys.defaultWalletId] = walletId
            Unit
        }
    }

    suspend fun setAcknowledgedBudgetMonth(month: java.time.YearMonth) {
        context.dataStore.edit { it[Keys.acknowledgedBudgetMonth] = month.toString() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setPrimaryCurrency(code: String) {
        require(CurrencyRules.isSupported(code))
        context.dataStore.edit { it[Keys.primaryCurrency] = code }
    }

    companion object {
        const val DATA_STORE_NAME = "user_preferences"
        const val MAX_DISPLAY_NAME = 40

        fun defaultCurrency(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
                ?.takeIf { it != "XXX" }
                ?: "USD"
    }
}
