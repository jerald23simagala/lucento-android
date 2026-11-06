package com.lucento.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
import com.lucento.app.domain.backup.BackupSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Currency
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The colour theme (docs/DESIGN.md › Themes). Independent of [ThemeMode]: a theme defines its own light and dark
 * palettes, except Midnight, which is dark only and ignores the mode while it is chosen (the mode is kept, so switching
 * back restores it). A device choice like [WalletsViewMode]: kept across sign-outs, never synced or backed up.
 */
enum class ThemeId(val key: String) {
    SUNRISE("sunrise"),
    MIDNIGHT("midnight"),
    GROVE("grove"),
    MONO("mono"),
    AURORA("aurora");

    companion object {
        /** Unknown or missing values (a newer build's theme, a damaged file) fall back to Sunrise, the brand theme. */
        fun fromKey(key: String?): ThemeId = entries.firstOrNull { it.key == key } ?: SUNRISE
    }
}

/** How the Wallets screen lists accounts: rows, or card faces two to a row. A device choice, never synced or backed up. */
enum class WalletsViewMode(val key: String) {
    ROWS("rows"),
    CARDS("cards");

    companion object {
        /** Unknown or missing values fall back to cards, the default (owner, 2026-11-04: the gallery opens first). */
        fun fromKey(key: String?): WalletsViewMode = entries.firstOrNull { it.key == key } ?: CARDS
    }
}

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
    /**
     * Ask Lucento's AI answers: a free-form question goes to Gemini through Lucento's proxy with a summary of the user's
     * figures (never transactions, notes or payees). Off by default, set only from Ask Lucento's consent sheet or
     * Settings; never restored from a backup, so a restore can't switch sharing on.
     */
    val askLucentoAi: Boolean = false,
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
    /** Bumped by every sign-out wipe, so the next account's onboarding never reuses the previous run's screen state. */
    val setupGeneration: Int = 0,
    /**
     * App lock (P2-10): opening Lucento, or returning after a while, asks for the phone's fingerprint, face or screen
     * lock. A device choice: never in backups, kept across account switches.
     */
    val appLock: Boolean = false,
    /** Hides Lucento's screen in the recent-apps view (P2-10). A device choice like [appLock]. */
    val hideInRecents: Boolean = false,
    /**
     * The Dashboard shows amounts as dots (owner, 2026-11-04). Hiding is one tap; showing them again asks for the
     * phone's fingerprint, face or screen lock. A device choice like [appLock]: never in backups.
     */
    val hideAmounts: Boolean = false,
    /** P2-5: the user hid Home's "turn on" offers for modules that are off; Settings › Enabled features still has them. */
    val moduleOffersHidden: Boolean = false,
    /** How the user said they pay (onboarding); the guided setup suggests these account types first. Empty = not asked. */
    val spendTypes: Set<com.lucento.app.domain.SpendType> = emptySet(),
    /** Milestones already acknowledged (Part D); null until first baselined, so older installs aren't congratulated in bulk. */
    val achievementsSeen: Set<com.lucento.app.domain.Achievement>? = null,
    /** The user has opened Insights at least once (the Financial Explorer milestone). Local only. */
    val insightsVisited: Boolean = false,
    /** The one-time "+" spotlight after setup was shown and dismissed (or + was used). A device choice. */
    val addSpotlightSeen: Boolean = false,
    /** Wallets screen: rows or card faces. A device choice, kept across sign-outs like the theme. */
    val walletsViewMode: WalletsViewMode = WalletsViewMode.CARDS,
    /**
     * When the profile photo was last set (epoch millis), or null for none: the avatar reloads the picture whenever it
     * changes. The photo itself is a private file ([com.lucento.app.data.media.ProfilePhotos]).
     */
    val profilePhotoVersion: Long? = null,
    /** Where the profile photo comes from: the Google account's picture by default, or the person's own choice. */
    val profilePhotoChoice: ProfilePhotoChoice = ProfilePhotoChoice.GOOGLE,
    /** The colour theme. A device choice: kept across sign-outs, never in backups. */
    val themeId: ThemeId = ThemeId.SUNRISE,
)

/**
 * The profile photo's source (owner, 2026-11-05: "Google by default, the user can override"). [GOOGLE]: a Google
 * account's picture is fetched after each sign-in when no photo is on the phone. [OWN]: a picture the person picked.
 * [NONE]: they removed it — initials, and no fetching. Belongs to the account: back to [GOOGLE] when the account changes.
 */
enum class ProfilePhotoChoice(val key: String) {
    GOOGLE("google"),
    OWN("own"),
    NONE("none"),
    ;

    companion object {
        fun fromKey(key: String?): ProfilePhotoChoice = entries.firstOrNull { it.key == key } ?: GOOGLE
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = UserPreferencesRepository.DATA_STORE_NAME)

/** What a sign-out wipe resets in the preferences (F-7). An interface so the wipe can be tested without Android. */
interface AccountSetupStore {
    suspend fun resetForNextAccount()

    /**
     * Lucento Shared, joining: this phone's ledger becomes a Shared Space's. Only what belonged to the old ledger goes
     * (default wallet, budget acknowledgements, the guided setup); onboarding stays as it is, and so does everything
     * about the person (name, photo, consents, theme, modules).
     */
    suspend fun resetForSharedSpace() = resetForNextAccount()

    /**
     * Lucento Shared, leaving or removed: back to the person's own account through onboarding, which restores it (or
     * sets it up). Like [resetForNextAccount], but it is the same person, so their name, photo and consents stay.
     */
    suspend fun resetForOwnLedger() = resetForNextAccount()
}

class UserPreferencesRepository(
    private val context: Context,
    private val crashReportsFlag: CrashReportsFlag.Store = SharedPreferencesCrashReportsStore(context),
) : AccountSetupStore {

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
        /**
         * v2 (owner, 2026-11-04): AI answers now send recent transactions, goals and projections. Agreeing to v1
         * ("ask_lucento_ai", summaries only) doesn't cover that, so v1 is ignored and removed: the user is asked again.
         */
        val askLucentoAi = booleanPreferencesKey("ask_lucento_ai_v2")
        val askLucentoAiV1 = booleanPreferencesKey("ask_lucento_ai")
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
        val setupGeneration = intPreferencesKey("setup_generation")
        val appLock = booleanPreferencesKey("app_lock")
        val hideInRecents = booleanPreferencesKey("hide_in_recents")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val moduleOffersHidden = booleanPreferencesKey("module_offers_hidden")
        val spendTypes = stringSetPreferencesKey("spend_types")
        val achievementsSeen = stringSetPreferencesKey("achievements_seen")
        val insightsVisited = booleanPreferencesKey("insights_visited")
        val addSpotlightSeen = booleanPreferencesKey("add_spotlight_seen")
        val walletsViewMode = stringPreferencesKey("wallets_view_mode")
        val profilePhotoVersion = longPreferencesKey("profile_photo_version")
        val profilePhotoChoice = stringPreferencesKey("profile_photo_choice")
        val themeId = stringPreferencesKey("theme_id")
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
                askLucentoAi = p[Keys.askLucentoAi] ?: false,
                crashReports = p[Keys.crashReports] ?: false,
                usageCounts = p[Keys.usageCounts] ?: false,
                localDataNoticeSeen = p[Keys.localDataNoticeSeen] ?: false,
                setupGeneration = p[Keys.setupGeneration] ?: 0,
                appLock = p[Keys.appLock] ?: false,
                hideInRecents = p[Keys.hideInRecents] ?: false,
                hideAmounts = p[Keys.hideAmounts] ?: false,
                moduleOffersHidden = p[Keys.moduleOffersHidden] ?: false,
                spendTypes = com.lucento.app.domain.SpendType.fromKeys(p[Keys.spendTypes].orEmpty()),
                achievementsSeen = p[Keys.achievementsSeen]?.mapNotNull(com.lucento.app.domain.Achievement::fromKey)?.toSet(),
                insightsVisited = p[Keys.insightsVisited] ?: false,
                addSpotlightSeen = p[Keys.addSpotlightSeen] ?: false,
                walletsViewMode = WalletsViewMode.fromKey(p[Keys.walletsViewMode]),
                profilePhotoVersion = p[Keys.profilePhotoVersion],
                profilePhotoChoice = ProfilePhotoChoice.fromKey(p[Keys.profilePhotoChoice]),
                themeId = ThemeId.fromKey(p[Keys.themeId]),
            )
        }

    /** One atomic edit, so no screen ever sees a half-reset state. See [resetAccountSetup]. */
    override suspend fun resetForNextAccount() {
        context.dataStore.edit { resetAccountSetup(it) }
    }

    override suspend fun resetForSharedSpace() {
        context.dataStore.edit { resetForSharedSpaceLedger(it) }
    }

    override suspend fun resetForOwnLedger() {
        context.dataStore.edit { p ->
            val name = p[Keys.displayName]
            val photo = p[Keys.profilePhotoVersion]
            val photoChoice = p[Keys.profilePhotoChoice]
            val smart = p[Keys.smartReading]
            val ai = p[Keys.askLucentoAi]
            val aiV1 = p[Keys.askLucentoAiV1]
            resetAccountSetup(p)
            name?.let { p[Keys.displayName] = it }
            photo?.let { p[Keys.profilePhotoVersion] = it }
            photoChoice?.let { p[Keys.profilePhotoChoice] = it }
            smart?.let { p[Keys.smartReading] = it }
            ai?.let { p[Keys.askLucentoAi] = it }
            aiV1?.let { p[Keys.askLucentoAiV1] = it }
        }
    }

    /**
     * Saves the onboarding choices in one atomic edit, then marks onboarding complete. Called only after the chosen
     * categories were written to the database, so a completed onboarding always has the user's categories.
     */
    suspend fun completeOnboarding(
        primaryCurrency: String,
        themeMode: ThemeMode,
        features: Set<Feature>,
        spendTypes: Set<com.lucento.app.domain.SpendType> = emptySet(),
    ) {
        context.dataStore.edit {
            it[Keys.spendTypes] = spendTypes.map { type -> type.name }.toSet()
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

    suspend fun setAskLucentoAi(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.askLucentoAi] = enabled
            it.remove(Keys.askLucentoAiV1)
        }
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

    suspend fun setAppLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.appLock] = enabled }
    }

    suspend fun setHideInRecents(enabled: Boolean) {
        context.dataStore.edit { it[Keys.hideInRecents] = enabled }
    }

    suspend fun setHideAmounts(hidden: Boolean) {
        context.dataStore.edit { it[Keys.hideAmounts] = hidden }
    }

    suspend fun setModuleOffersHidden(hidden: Boolean) {
        context.dataStore.edit { it[Keys.moduleOffersHidden] = hidden }
    }

    suspend fun setWalletsViewMode(mode: WalletsViewMode) {
        context.dataStore.edit { it[Keys.walletsViewMode] = mode.key }
    }

    /** A new profile photo was saved ([version] = when), or it was removed (null). */
    suspend fun setProfilePhotoVersion(version: Long?) {
        context.dataStore.edit { if (version == null) it.remove(Keys.profilePhotoVersion) else it[Keys.profilePhotoVersion] = version }
    }

    /** A photo change and where it came from, in one edit ([version] null = removed). */
    suspend fun setProfilePhoto(version: Long?, choice: ProfilePhotoChoice) {
        context.dataStore.edit {
            if (version == null) it.remove(Keys.profilePhotoVersion) else it[Keys.profilePhotoVersion] = version
            if (choice == ProfilePhotoChoice.GOOGLE) it.remove(Keys.profilePhotoChoice) else it[Keys.profilePhotoChoice] = choice.key
        }
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

    suspend fun setThemeId(theme: ThemeId) {
        context.dataStore.edit { it[Keys.themeId] = theme.key }
    }

    /** Adds [achievements] to the acknowledged set (and baselines it on first use). */
    suspend fun acknowledgeAchievements(achievements: Set<com.lucento.app.domain.Achievement>) {
        context.dataStore.edit {
            it[Keys.achievementsSeen] = it[Keys.achievementsSeen].orEmpty() + achievements.map { a -> a.key }
        }
    }

    suspend fun setInsightsVisited() {
        context.dataStore.edit { if (it[Keys.insightsVisited] != true) it[Keys.insightsVisited] = true }
    }

    suspend fun setAddSpotlightSeen() {
        context.dataStore.edit { it[Keys.addSpotlightSeen] = true }
    }

    suspend fun setPrimaryCurrency(code: String) {
        require(CurrencyRules.isSupported(code))
        context.dataStore.edit { it[Keys.primaryCurrency] = code }
    }

    /**
     * Writes the preferences a backup is allowed to carry. Crash reports and the one-time local-data notice
     * stay as they are on this device.
     */
    suspend fun applyBackupSettings(settings: BackupSettings, defaultWalletLocalId: Long?) {
        val mode = runCatching { ThemeMode.valueOf(settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
        require(CurrencyRules.isSupported(settings.primaryCurrency))
        context.dataStore.edit { prefs ->
            prefs[Keys.onboardingComplete] = settings.onboardingComplete
            prefs[Keys.themeMode] = mode.name
            prefs[Keys.primaryCurrency] = settings.primaryCurrency
            prefs[Keys.enabledFeatures] = settings.enabledFeatures.toSet()
            if (settings.acknowledgedBudgetMonth == null) {
                prefs.remove(Keys.acknowledgedBudgetMonth)
            } else {
                prefs[Keys.acknowledgedBudgetMonth] = settings.acknowledgedBudgetMonth
            }
            if (settings.displayName.isNullOrBlank()) prefs.remove(Keys.displayName) else prefs[Keys.displayName] = settings.displayName
            prefs[Keys.categorySetupDone] = settings.categorySetupDone
            if (defaultWalletLocalId == null) prefs.remove(Keys.defaultWalletId) else prefs[Keys.defaultWalletId] = defaultWalletLocalId
            if (settings.guideStep == null) prefs.remove(Keys.guideStep) else prefs[Keys.guideStep] = settings.guideStep
            prefs[Keys.smartReading] = settings.smartReading
        }
    }

    companion object {
        const val DATA_STORE_NAME = "user_preferences"

        /**
         * After a sign-out wipe the ledger is a fresh install's, so the setup that belonged to it goes too (F-7): the next
         * account goes through onboarding, which checks it and restores or sets it up. Kept: device choices (theme,
         * currency, modules), consent for crash reports and usage counts, and notices already shown. Smarter reading is
         * switched off because it sends composer text and was opted into for the previous account.
         */
        internal fun resetAccountSetup(p: MutablePreferences) {
            // A new generation only when onboarding had finished, so its finished ViewModel isn't reused. A wipe started
            // inside onboarding (leaving a partial restore, or a switch while restoring) keeps the onboarding that is
            // running: a new one would run a second restore beside it.
            if (p[Keys.onboardingComplete] == true) p[Keys.setupGeneration] = (p[Keys.setupGeneration] ?: 0) + 1
            p[Keys.onboardingComplete] = false
            p[Keys.categorySetupDone] = false
            p.remove(Keys.guideStep)
            p.remove(Keys.spendTypes)
            p.remove(Keys.achievementsSeen)
            p.remove(Keys.insightsVisited)
            p.remove(Keys.defaultWalletId)
            p.remove(Keys.displayName)
            // The photo belongs to the account like the name; its file goes with the ledger wipe (LocalLedgerReset). The
            // next account starts on its Google picture again (ProfilePhotoChoice.GOOGLE).
            p.remove(Keys.profilePhotoVersion)
            p.remove(Keys.profilePhotoChoice)
            p.remove(Keys.acknowledgedBudgetMonth)
            p[Keys.smartReading] = false
            p[Keys.askLucentoAi] = false
            p.remove(Keys.askLucentoAiV1)
        }

        /**
         * Lucento Shared: the ledger on this phone is replaced by a Shared Space's. A finished onboarding stays finished
         * (a new generation, so screens holding the old ledger's state start over); the guided setup and choices tied to
         * the old ledger go. Categories are the Owner's to set up, so the category prompt is treated as done.
         */
        internal fun resetForSharedSpaceLedger(p: MutablePreferences) {
            if (p[Keys.onboardingComplete] == true) p[Keys.setupGeneration] = (p[Keys.setupGeneration] ?: 0) + 1
            p[Keys.categorySetupDone] = true
            p.remove(Keys.guideStep)
            p.remove(Keys.defaultWalletId)
            p.remove(Keys.acknowledgedBudgetMonth)
            p.remove(Keys.achievementsSeen)
        }
        const val MAX_DISPLAY_NAME = 40

        fun defaultCurrency(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
                ?.takeIf { it != "XXX" }
                ?: "USD"
    }
}
