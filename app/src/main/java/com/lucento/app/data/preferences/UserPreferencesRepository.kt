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
import com.lucento.app.domain.settings.AccountSetting
import com.lucento.app.domain.settings.AccountSettingsDocument
import com.lucento.app.domain.settings.AccountSettingsMerge
import com.lucento.app.domain.settings.LocalSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.IOException
import java.util.Currency
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The colour theme (docs/DESIGN.md › Themes). Independent of [ThemeMode]: a theme defines its own light and dark
 * palettes, except Midnight, which is dark only and ignores the mode while it is chosen (the mode is kept, so switching
 * back restores it). Kept across sign-outs; follows the account and is in backups ([AccountSetting.THEME]; until
 * 2026-11-22 it was a device choice, owner decision 2026-11-05, reversed after tester feedback).
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

        /** The stored keys of the themes named in [ids] (ProGates names themes by their enum name). */
        fun keysOf(ids: Set<String>): Set<String> = entries.filter { it.name in ids }.mapTo(mutableSetOf()) { it.key }
    }
}

/** How the Wallets screen lists accounts: rows, or card faces two to a row. Follows the account ([AccountSetting.WALLETS_VIEW]). */
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
    /**
     * The monthly check-in (owner, 2026-11-23): the latest month planned, or whose check-in was dismissed. Home never asks
     * about a month up to it again. Follows the account ([AccountSetting.BUDGET_MONTH_PLANNED]); never goes back.
     */
    val budgetMonthPlanned: java.time.YearMonth? = null,
    /** The latest finished month whose recap was seen, or dismissed, from the check-in ([AccountSetting.RECAP_MONTH_SEEN]). */
    val recapMonthSeen: java.time.YearMonth? = null,
    /** Optional name shown on the profile button and menu; never required. Follows the account ([AccountSetting.DISPLAY_NAME]). */
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
     * user after the consent dialog in Settings; nothing is sent to a model while it is off. Since 2026-11-23 each message
     * uses one of the AI questions Ask Lucento uses, so a consent given before that no longer counts (see [Keys.smartReading]).
     */
    val smartReading: Boolean = false,
    /**
     * Ask Lucento's AI answers are on: a free-form question goes to Gemini through Lucento's proxy with a summary of the
     * person's figures and their transactions from the last 60 days (docs/ASK.md). On by default for a signed-in account
     * (owner, 2026-11-17); false only after the person turned them off. Nothing is sent until [askAiConfirmed].
     */
    val askLucentoAi: Boolean = true,
    /**
     * The person agreed to what AI answers send: at their first question (Ask Lucento's card), in Ask's sheet or in
     * Settings' dialog. Never restored from a backup, so a restore can't switch sharing on; cleared with the account
     * (sign-out, reset), so the next account confirms for itself.
     */
    val askAiConfirmed: Boolean = false,
    /** Crash reports. Off until the user turns them on in Settings. Nothing is sent while this is false. */
    val crashReports: Boolean = false,
    /** Anonymous usage counts. Off until the user opts in. Nothing is sent while this is false. */
    val usageCounts: Boolean = false,
    /**
     * Multicurrency: fetch the day's exchange rates from free services (two currency codes and a date per request, never
     * an amount), and only when something is recorded in another currency. On by default (owner, 2026-11-06), and the
     * person can turn it off; offline or off, rates are typed or taken from the wallet's own history.
     */
    val fxRatesOnline: Boolean = true,
    /**
     * Online voice (owner, 2026-11-13): the composer's microphone may let the phone's speech service (Google's on most
     * phones) go online, so the recording may reach Google. Off by default: Lucento asks it to stay offline. Turned on
     * only after the consent prompt (composer or Settings › Privacy). A device choice like crash reports: kept across
     * sign-outs, never in backups.
     */
    val onlineVoice: Boolean = false,
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
    /**
     * Settings › Money › "Show cents" (tester round, 2026-11-17). Off: the amounts people read are rounded to whole
     * units; amount inputs and the transaction view keep their cents. Follows the account ([AccountSetting.SHOW_CENTS]).
     */
    val showCents: Boolean = true,
    /** P2-5: the user hid Home's "turn on" offers for modules that are off; Settings › Enabled features still has them. */
    val moduleOffersHidden: Boolean = false,
    /** How the user said they pay (onboarding); the guided setup suggests these account types first. Empty = not asked. */
    val spendTypes: Set<com.lucento.app.domain.SpendType> = emptySet(),
    /** Milestones already acknowledged (Part D); null until first baselined, so older installs aren't congratulated in bulk. */
    val achievementsSeen: Set<com.lucento.app.domain.Achievement>? = null,
    /**
     * The award keys this phone knew when it last recorded its awards; null before it kept the record. Awards an update
     * adds are recorded quietly for the past ([com.lucento.app.domain.Achievements.newToThisPhone]). Local only.
     */
    val achievementsKnown: Set<String>? = null,
    /** The user has opened Insights at least once (the Financial Explorer milestone). Local only. */
    val insightsVisited: Boolean = false,
    /** App lock has been turned on at some point (the Secured award). Local only; never cleared by turning lock off. */
    val appLockUsed: Boolean = false,
    /** The one-time "+" spotlight after setup was shown and dismissed (or + was used). A device choice. */
    val addSpotlightSeen: Boolean = false,
    /** Wallets screen: rows or card faces. Kept across sign-outs like the theme, and follows the account. */
    val walletsViewMode: WalletsViewMode = WalletsViewMode.CARDS,
    /**
     * When the profile photo was last set (epoch millis), or null for none: the avatar reloads the picture whenever it
     * changes. The photo itself is a private file ([com.lucento.app.data.media.ProfilePhotos]).
     */
    val profilePhotoVersion: Long? = null,
    /** Where the profile photo comes from: the Google account's picture by default, or the person's own choice. */
    val profilePhotoChoice: ProfilePhotoChoice = ProfilePhotoChoice.GOOGLE,
    /** The colour theme. Kept across sign-outs; follows the account and is in backups (see [ThemeId]). */
    val themeId: ThemeId = ThemeId.SUNRISE,
    /**
     * Lucento Pro's welcome: the plans already welcomed on this phone (keys from
     * [com.lucento.app.domain.entitlement.ProWelcome.planKeys]), so a renewal or a re-read never replays it.
     */
    val proWelcomed: Set<String> = emptySet(),
    /** When the Lucento Pro medal was presented (epoch millis); null before. The medal is kept for good. */
    val proMemberSince: Long? = null,
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

/**
 * Backup version 6's customisations as a phone holds them (docs/BACKUP.md): each is null when the phone has no value of its
 * own, so the file never presents a default as a choice and a restore of it leaves the other phone's own.
 */
data class BackupCustomisations(
    val themeId: String?,
    val showCents: Boolean?,
    val walletsView: String?,
    val moduleOffersHidden: Boolean?,
    val fxRatesOnline: Boolean?,
)

/** The monthly check-in's two months as stored ("yyyy-MM", or none), kept by "Not this month" for its Undo. */
data class CheckInMarks(val planned: String?, val recapSeen: String?)

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

class UserPreferencesRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val crashReportsFlag: CrashReportsFlag.Store,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountSetupStore, AccountSettingsStore {

    constructor(context: Context) : this(context.dataStore, SharedPreferencesCrashReportsStore(context))

    /**
     * A change the person makes. A setting that follows the account ([AccountSetting]) and changed is marked as changed on
     * this phone, with when, and goes up with the next sync (docs/SYNC.md "Settings"). Every setter uses this.
     */
    private suspend fun trackedEdit(transform: suspend (MutablePreferences) -> Unit) {
        store.edit { p ->
            val before = accountSettingsSnapshot(p)
            transform(p)
            recordAccountSettingChanges(p, before, clock())
        }
    }

    /**
     * Not the person's change: sync's own writes, the resets that hand the phone to another account or ledger, and the
     * defaults a setup writes. Nothing here is marked for sync, so none of it reaches another phone as a choice.
     */
    private suspend fun untrackedEdit(transform: suspend (MutablePreferences) -> Unit) {
        store.edit { transform(it) }
    }

    /**
     * Grows with every change to a setting that follows the account while any is waiting to go up, and is 0 when nothing
     * waits, so `uploadRequests` asks for a sync after each change, a second change of a waiting setting included.
     */
    val accountSettingChanges: Flow<Int> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { if (it[Keys.accountSettingsDirty].isNullOrEmpty()) 0 else it[Keys.accountSettingsChanges] ?: 0 }
        .distinctUntilChanged()

    /** Whether a setting changed here still waits to reach the account (a sync isn't "up to date" while one does). */
    suspend fun accountSettingsWaiting(): Boolean = !store.data.first()[Keys.accountSettingsDirty].isNullOrEmpty()

    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val primaryCurrency = stringPreferencesKey("primary_currency")
        val enabledFeatures = stringSetPreferencesKey("enabled_features")
        val acknowledgedBudgetMonth = stringPreferencesKey("acknowledged_budget_month")
        val budgetMonthPlanned = stringPreferencesKey("budget_month_planned")
        val recapMonthSeen = stringPreferencesKey("recap_month_seen")
        val displayName = stringPreferencesKey("display_name")
        val categorySetupDone = booleanPreferencesKey("category_setup_done")
        val defaultWalletId = longPreferencesKey("default_wallet_id")
        val guideStep = stringPreferencesKey("guide_step")
        /**
         * Version 2 (owner, 2026-11-23: "We turn off Smarter Reading"): its messages now use the AI questions Ask Lucento
         * uses, so everyone who had it on is asked again with the new consent. The first consent's key is ignored and
         * removed on the next change ([smartReadingV1]).
         */
        val smartReading = booleanPreferencesKey("smart_reading_v2")
        val smartReadingV1 = booleanPreferencesKey("smart_reading")
        val fxRatesOnline = booleanPreferencesKey("fx_rates_online")
        val onlineVoice = booleanPreferencesKey("online_voice")
        /**
         * The agreement to AI answers. v2 (owner, 2026-11-04): they send recent transactions, goals and projections, and
         * agreeing to v1 ("ask_lucento_ai", summaries only) doesn't cover that, so v1 is ignored and removed. Since
         * 2026-11-17 AI answers are on by default and this key records only that the person confirmed what is sent; an
         * agreement given before then still counts.
         */
        val askAiConfirmed = booleanPreferencesKey("ask_lucento_ai_v2")
        val askLucentoAiV1 = booleanPreferencesKey("ask_lucento_ai")
        /** The person turned AI answers off (owner, 2026-11-17: they are on by default). */
        val askAiOff = booleanPreferencesKey("ask_ai_off")
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
        val showCents = booleanPreferencesKey("show_cents")
        val moduleOffersHidden = booleanPreferencesKey("module_offers_hidden")
        val spendTypes = stringSetPreferencesKey("spend_types")
        val achievementsSeen = stringSetPreferencesKey("achievements_seen")
        val achievementsKnown = stringSetPreferencesKey("achievements_known")
        val insightsVisited = booleanPreferencesKey("insights_visited")
        val appLockUsed = booleanPreferencesKey("app_lock_used")
        val addSpotlightSeen = booleanPreferencesKey("add_spotlight_seen")
        val walletsViewMode = stringPreferencesKey("wallets_view_mode")
        val profilePhotoVersion = longPreferencesKey("profile_photo_version")
        val profilePhotoChoice = stringPreferencesKey("profile_photo_choice")
        val themeId = stringPreferencesKey("theme_id")
        val proWelcomed = stringSetPreferencesKey("pro_welcomed")
        val proMemberSince = longPreferencesKey("pro_member_since")
        /** A [com.lucento.app.feature.account.SignOutNotice] key, written just before a sign-out wipe; read once by onboarding. */
        val signOutNotice = stringPreferencesKey("sign_out_notice")

        /** Settings that follow the account (docs/SYNC.md "Settings") changed here since the account accepted them. */
        val accountSettingsDirty = stringSetPreferencesKey("account_settings_dirty")

        /** The account the settings here belong to (set at the start of each sync; cleared by a sign-out). */
        val accountSettingsAccount = stringPreferencesKey("account_settings_account")

        /** The account this phone joins: its next merge uses that account's settings (see AccountSettingsMerge.decide). */
        val accountSettingsJoin = stringPreferencesKey("account_settings_join")

        /** After a sign-out, a one-way hash of the account the settings belonged to, so its own return is recognised. */
        val accountSettingsPrevious = stringPreferencesKey("account_settings_previous")

        /** Settings left by an account that signed out and not changed since: shown here, never sent to another account. */
        val accountSettingsInherited = stringSetPreferencesKey("account_settings_inherited")

        /** Counts the person's changes to settings that follow the account ([accountSettingChanges]). */
        val accountSettingsChanges = intPreferencesKey("account_settings_changes")

        /**
         * When [setting]'s value here was chosen, or the time of the account's entry it was taken from or sent as. Absent
         * until the setting first goes through sync, and again after each sign-out.
         */
        fun settingAt(setting: AccountSetting) = longPreferencesKey("account_setting_at_${setting.key}")
    }

    val preferences: Flow<UserPreferences> = store.data
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
                budgetMonthPlanned = p[Keys.budgetMonthPlanned]?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() },
                recapMonthSeen = p[Keys.recapMonthSeen]?.let { runCatching { java.time.YearMonth.parse(it) }.getOrNull() },
                displayName = p[Keys.displayName]?.trim()?.takeIf { it.isNotEmpty() },
                categorySetupDone = p[Keys.categorySetupDone] ?: false,
                defaultWalletId = p[Keys.defaultWalletId],
                guideStep = com.lucento.app.domain.GuideStep.fromKey(p[Keys.guideStep]),
                smartReading = smartReadingOn(p),
                askLucentoAi = askAiOn(p),
                askAiConfirmed = askAiConfirmed(p),
                crashReports = p[Keys.crashReports] ?: false,
                usageCounts = p[Keys.usageCounts] ?: false,
                fxRatesOnline = p[Keys.fxRatesOnline] ?: true,
                onlineVoice = p[Keys.onlineVoice] ?: false,
                localDataNoticeSeen = p[Keys.localDataNoticeSeen] ?: false,
                setupGeneration = p[Keys.setupGeneration] ?: 0,
                appLock = p[Keys.appLock] ?: false,
                hideInRecents = p[Keys.hideInRecents] ?: false,
                hideAmounts = p[Keys.hideAmounts] ?: false,
                showCents = p[Keys.showCents] ?: true,
                moduleOffersHidden = p[Keys.moduleOffersHidden] ?: false,
                spendTypes = com.lucento.app.domain.SpendType.fromKeys(p[Keys.spendTypes].orEmpty()),
                achievementsSeen = p[Keys.achievementsSeen]?.mapNotNull(com.lucento.app.domain.Achievement::fromKey)?.toSet(),
                achievementsKnown = p[Keys.achievementsKnown],
                insightsVisited = p[Keys.insightsVisited] ?: false,
                // Installs that turned lock on before this flag existed count too.
                appLockUsed = p[Keys.appLockUsed] ?: (p[Keys.appLock] ?: false),
                addSpotlightSeen = p[Keys.addSpotlightSeen] ?: false,
                walletsViewMode = WalletsViewMode.fromKey(p[Keys.walletsViewMode]),
                profilePhotoVersion = p[Keys.profilePhotoVersion],
                profilePhotoChoice = ProfilePhotoChoice.fromKey(p[Keys.profilePhotoChoice]),
                themeId = ThemeId.fromKey(p[Keys.themeId]),
                proWelcomed = p[Keys.proWelcomed].orEmpty(),
                proMemberSince = p[Keys.proMemberSince],
            )
        }

    /** One atomic edit, so no screen ever sees a half-reset state. See [resetAccountSetup]. */
    override suspend fun resetForNextAccount() {
        untrackedEdit { resetAccountSetup(it) }
    }

    override suspend fun resetForSharedSpace() {
        untrackedEdit { resetForSharedSpaceLedger(it) }
    }

    override suspend fun resetForOwnLedger() {
        untrackedEdit { p ->
            val name = p[Keys.displayName]
            val photo = p[Keys.profilePhotoVersion]
            val photoChoice = p[Keys.profilePhotoChoice]
            val smart = p[Keys.smartReading]
            val ai = p[Keys.askAiConfirmed]
            val aiV1 = p[Keys.askLucentoAiV1]
            val aiOff = p[Keys.askAiOff]
            val welcomed = p[Keys.proWelcomed]
            val memberSince = p[Keys.proMemberSince]
            val settingsAccount = p[Keys.accountSettingsAccount]
            resetAccountSetup(p)
            name?.let { p[Keys.displayName] = it }
            photo?.let { p[Keys.profilePhotoVersion] = it }
            photoChoice?.let { p[Keys.profilePhotoChoice] = it }
            smart?.let { p[Keys.smartReading] = it }
            ai?.let { p[Keys.askAiConfirmed] = it }
            aiV1?.let { p[Keys.askLucentoAiV1] = it }
            aiOff?.let { p[Keys.askAiOff] = it }
            // The same person: their Lucento Pro welcome and medal stay.
            welcomed?.let { p[Keys.proWelcomed] = it }
            memberSince?.let { p[Keys.proMemberSince] = it }
            keepOwnSettings(p, settingsAccount)
        }
    }

    /**
     * Saves the onboarding choices in one atomic edit, then marks onboarding complete. Called only after the chosen
     * categories were written to the database, so a completed onboarding always has the user's categories. The setup's
     * choices are this phone's own but not marked as changes: an account this phone joins later keeps its own settings,
     * and gets these only where it has none (docs/SYNC.md "Settings").
     */
    suspend fun completeOnboarding(
        primaryCurrency: String,
        themeMode: ThemeMode,
        features: Set<Feature>,
        spendTypes: Set<com.lucento.app.domain.SpendType> = emptySet(),
    ) {
        untrackedEdit {
            it[Keys.spendTypes] = spendTypes.map { type -> type.name }.toSet()
            it[Keys.primaryCurrency] = primaryCurrency
            it[Keys.themeMode] = themeMode.name
            it[Keys.enabledFeatures] = features.map(Feature::key).toSet()
            it[Keys.categorySetupDone] = true
            it[Keys.onboardingComplete] = true
            it[Keys.localDataNoticeSeen] = true
            // A new user continues into the guided setup (wallets, budget, goal, first transactions).
            it[Keys.guideStep] = com.lucento.app.domain.GuideStep.WALLETS.key
            ownedHere(it, AccountSetting.THEME_MODE, AccountSetting.FEATURES)
        }
    }

    /** Onboarding's appearance step: shown at once, kept as this phone's own like [completeOnboarding]'s choices. */
    suspend fun previewThemeMode(mode: ThemeMode) {
        untrackedEdit {
            it[Keys.themeMode] = mode.name
            ownedHere(it, AccountSetting.THEME_MODE)
        }
    }

    /**
     * Finishes onboarding for an account restored on a new phone (its data already pulled by the first sync): the
     * remaining preferences are set in one edit and the guided setup is skipped, since the account is set up.
     * [categoriesChosen] is false when the account has no spending categories yet, so the category setup still asks.
     * See [completeRestoreInto] for which settings stay.
     */
    suspend fun completeRestore(primaryCurrency: String, themeMode: ThemeMode, features: Set<Feature>, categoriesChosen: Boolean) {
        untrackedEdit { completeRestoreInto(it, primaryCurrency, themeMode, features, categoriesChosen) }
    }

    suspend fun setSmartReading(enabled: Boolean) {
        trackedEdit {
            it.remove(Keys.smartReadingV1)
            it[Keys.smartReading] = enabled
        }
    }

    /** Multicurrency: "Exchange rates online" (see [UserPreferences.fxRatesOnline]). A device choice, kept across sign-outs. */
    suspend fun setFxRatesOnline(enabled: Boolean) {
        trackedEdit { it[Keys.fxRatesOnline] = enabled }
    }

    /** "Online voice" (see [UserPreferences.onlineVoice]). Called only after the person agreed to the consent text. */
    suspend fun setOnlineVoice(enabled: Boolean) {
        trackedEdit { it[Keys.onlineVoice] = enabled }
    }

    /**
     * AI answers on, from a consent the person accepted (Ask's first-question card or sheet, Settings' dialog): on and
     * agreed. Off: off, and the agreement withdrawn, so turning them on again shows what is sent again.
     */
    suspend fun setAskLucentoAi(enabled: Boolean) {
        trackedEdit { applyAskAi(it, enabled) }
    }

    /**
     * Signing out but keeping this phone's own records (owner decision 2026-11-12, F-2): the setup stays, but Smarter
     * reading and the agreement to Ask Lucento's AI answers were given while signed in, so they go as the sign-out wipe
     * does ([resetAccountSetup]): Smarter reading off, AI answers back to their default (on, nothing sent until the next
     * account confirms). Signing in again asks afresh. The settings that follow an account are untouched: no account
     * ever synced this phone's records, so none synced its settings either. A reset, not the person's change.
     */
    suspend fun forgetAccountConsents() {
        untrackedEdit {
            it[Keys.smartReading] = false
            forgetAskAi(it)
        }
    }

    override suspend fun bindAccountSettings(account: String, claiming: Boolean) {
        untrackedEdit { bindAccountSettingsInto(it, account, claiming) }
    }

    override suspend fun mergeAccountSettings(
        account: String,
        document: JsonObject?,
        settings: Set<AccountSetting>,
        walletUuid: (Long) -> String?,
        walletId: (String) -> Long?,
        lockedThemes: Set<String>,
    ): AccountSettingsMerge.Result {
        var result: AccountSettingsMerge.Result? = null
        // Sync's own write: the account's values are not the person's changes.
        untrackedEdit { result = mergeAccountSettingsInto(it, account, document, settings, walletUuid, walletId, lockedThemes) }
        return checkNotNull(result)
    }

    override suspend fun markAccountSettingsSent(upload: AccountSettingsMerge.Upload) {
        untrackedEdit { markAccountSettingsSentInto(it, upload) }
    }

    /** The line the welcome screen shows after a sign-out wipe ([com.lucento.app.feature.account.SignOutNotice] key). */
    suspend fun setSignOutNotice(key: String?) {
        trackedEdit { if (key == null) it.remove(Keys.signOutNotice) else it[Keys.signOutNotice] = key }
    }

    /** Reads and clears the welcome screen's sign-out line, so it is shown once. */
    suspend fun takeSignOutNotice(): String? {
        var key: String? = null
        trackedEdit {
            key = it[Keys.signOutNotice]
            it.remove(Keys.signOutNotice)
        }
        return key
    }

    /** DataStore copy of the crash-report choice. Written before the synchronous mirror. */
    suspend fun writeCrashReportsPreference(enabled: Boolean) {
        trackedEdit { it[Keys.crashReports] = enabled }
    }

    /** Synchronous `lucento_crash_reports` mirror. [CrashReportsFlag.set] uses commit(), not apply(). */
    fun commitCrashReportsMirror(enabled: Boolean) {
        CrashReportsFlag.set(crashReportsFlag, enabled)
    }

    suspend fun readUsageLedger(): UsageLedger {
        val prefs = store.data.first()
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
        trackedEdit { prefs ->
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
        trackedEdit { it[Keys.localDataNoticeSeen] = true }
    }

    suspend fun setAppLock(enabled: Boolean) {
        trackedEdit {
            it[Keys.appLock] = enabled
            if (enabled) it[Keys.appLockUsed] = true
        }
    }

    suspend fun setHideInRecents(enabled: Boolean) {
        trackedEdit { it[Keys.hideInRecents] = enabled }
    }

    suspend fun setHideAmounts(hidden: Boolean) {
        trackedEdit { it[Keys.hideAmounts] = hidden }
    }

    suspend fun setShowCents(shown: Boolean) {
        trackedEdit { it[Keys.showCents] = shown }
    }

    suspend fun setModuleOffersHidden(hidden: Boolean) {
        trackedEdit { it[Keys.moduleOffersHidden] = hidden }
    }

    suspend fun setWalletsViewMode(mode: WalletsViewMode) {
        trackedEdit { it[Keys.walletsViewMode] = mode.key }
    }

    /** A new profile photo was saved ([version] = when), or it was removed (null). */
    suspend fun setProfilePhotoVersion(version: Long?) {
        trackedEdit { if (version == null) it.remove(Keys.profilePhotoVersion) else it[Keys.profilePhotoVersion] = version }
    }

    /** A photo change and where it came from, in one edit ([version] null = removed). */
    suspend fun setProfilePhoto(version: Long?, choice: ProfilePhotoChoice) {
        trackedEdit {
            if (version == null) it.remove(Keys.profilePhotoVersion) else it[Keys.profilePhotoVersion] = version
            if (choice == ProfilePhotoChoice.GOOGLE) it.remove(Keys.profilePhotoChoice) else it[Keys.profilePhotoChoice] = choice.key
        }
    }






    /** Saves the guided setup's progress; null when it is finished (or was never started, as for older installs). */
    suspend fun setGuideStep(step: com.lucento.app.domain.GuideStep?) {
        trackedEdit { if (step == null) it.remove(Keys.guideStep) else it[Keys.guideStep] = step.key }
    }

    suspend fun setCategorySetupDone(done: Boolean) {
        trackedEdit { it[Keys.categorySetupDone] = done }
    }

    /** Blank clears the name. */
    suspend fun setDisplayName(name: String) {
        val cleaned = cleanDisplayName(name)
        trackedEdit { prefs ->
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
        trackedEdit { prefs ->
            val current = prefs[Keys.enabledFeatures]?.toSet() ?: Feature.entries.map(Feature::key).toSet()
            prefs[Keys.enabledFeatures] = if (enabled) current + feature.key else current - feature.key
        }
    }

    /** Null clears the default. */
    suspend fun setDefaultWallet(walletId: Long?) {
        trackedEdit { prefs ->
            if (walletId == null) prefs.remove(Keys.defaultWalletId) else prefs[Keys.defaultWalletId] = walletId
            Unit
        }
    }

    suspend fun setAcknowledgedBudgetMonth(month: java.time.YearMonth) {
        trackedEdit { it[Keys.acknowledgedBudgetMonth] = month.toString() }
    }

    /** The monthly check-in planned [month] (or it was dismissed). Never moves back to an earlier month. */
    suspend fun setBudgetMonthPlanned(month: java.time.YearMonth) {
        trackedEdit { laterMonth(it, Keys.budgetMonthPlanned, month) }
    }

    /** [month]'s recap was seen from the check-in, or dismissed. Never moves back to an earlier month. */
    suspend fun setRecapMonthSeen(month: java.time.YearMonth) {
        trackedEdit { laterMonth(it, Keys.recapMonthSeen, month) }
    }

    /**
     * "Not this month" on the check-in card: both of what it offered, in one change. Returns the months that were there,
     * for Undo ([restoreMonthCheckIn]).
     */
    suspend fun dismissMonthCheckIn(plan: java.time.YearMonth?, recap: java.time.YearMonth?): CheckInMarks {
        var before = CheckInMarks(null, null)
        trackedEdit { p ->
            before = CheckInMarks(p[Keys.budgetMonthPlanned], p[Keys.recapMonthSeen])
            plan?.let { laterMonth(p, Keys.budgetMonthPlanned, it) }
            recap?.let { laterMonth(p, Keys.recapMonthSeen, it) }
        }
        return before
    }

    /** Undo of [dismissMonthCheckIn]: the months exactly as they were (the one place they may move back). */
    suspend fun restoreMonthCheckIn(marks: CheckInMarks) {
        trackedEdit { p ->
            if (marks.planned == null) p.remove(Keys.budgetMonthPlanned) else p[Keys.budgetMonthPlanned] = marks.planned
            if (marks.recapSeen == null) p.remove(Keys.recapMonthSeen) else p[Keys.recapMonthSeen] = marks.recapSeen
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        trackedEdit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setThemeId(theme: ThemeId) {
        trackedEdit { it[Keys.themeId] = theme.key }
    }

    /** Adds [achievements] to the acknowledged set (and baselines it on first use). */
    suspend fun acknowledgeAchievements(achievements: Set<com.lucento.app.domain.Achievement>) {
        trackedEdit {
            it[Keys.achievementsSeen] = it[Keys.achievementsSeen].orEmpty() + achievements.map { a -> a.key }
        }
    }

    /**
     * Records quietly what is already [earned] among the awards this phone didn't know (the first run, or an update that
     * added awards), and that it now knows every award of this build. Nothing is presented for them.
     */
    suspend fun recordAwardsQuietly(earned: Set<com.lucento.app.domain.Achievement>) {
        trackedEdit {
            it[Keys.achievementsSeen] = it[Keys.achievementsSeen].orEmpty() + earned.map { a -> a.key }
            it[Keys.achievementsKnown] = it[Keys.achievementsKnown].orEmpty() + com.lucento.app.domain.Achievement.entries.map { a -> a.key }
        }
    }

    suspend fun setInsightsVisited() {
        trackedEdit { if (it[Keys.insightsVisited] != true) it[Keys.insightsVisited] = true }
    }

    suspend fun setAddSpotlightSeen() {
        trackedEdit { it[Keys.addSpotlightSeen] = true }
    }

    /** Lucento Pro's welcome: these plans have been welcomed (stored when it starts, so it never loops). */
    suspend fun markProWelcomed(planKeys: Set<String>) {
        if (planKeys.isEmpty()) return
        trackedEdit { it[Keys.proWelcomed] = it[Keys.proWelcomed].orEmpty() + planKeys }
    }

    /** The Lucento Pro medal was presented at [millis] (kept for good); null forgets it (test builds' tour only). */
    suspend fun setProMemberSince(millis: Long?) {
        trackedEdit { p -> if (millis == null) p.remove(Keys.proMemberSince) else if (p[Keys.proMemberSince] == null) p[Keys.proMemberSince] = millis }
    }

    suspend fun setPrimaryCurrency(code: String) {
        require(CurrencyRules.isSupported(code))
        trackedEdit { it[Keys.primaryCurrency] = code }
    }

    /**
     * A Replace restore: writes the preferences a backup carries ([applyBackupSettingsInto]). Crash reports, the one-time
     * local-data notice and this phone's consents stay as they are. A Lucento Pro theme is applied only where the plan
     * allows it ([themeAllowed], asked first), so a file can't unlock one. A restore is the person's change: what it
     * changes on this phone follows the account (docs/SYNC.md "Settings"); what it leaves as it was doesn't.
     * [themeAllowed] has no default, so no caller can leave the plan out (Pro-gate review 2026-11-23, F1).
     */
    suspend fun applyBackupSettings(
        settings: BackupSettings,
        defaultWalletLocalId: Long?,
        themeAllowed: suspend (ThemeId) -> Boolean,
    ) {
        require(CurrencyRules.isSupported(settings.primaryCurrency))
        val theme = settings.themeId?.let { key -> ThemeId.entries.firstOrNull { it.key == key } }?.takeIf { themeAllowed(it) }
        trackedEdit { applyBackupSettingsInto(it, settings, defaultWalletLocalId, theme) }
    }

    /** A Merge restore keeps this phone's preferences (docs/BACKUP.md); only a default wallet the merge removed goes. */
    suspend fun clearDefaultWalletAfterMerge(defaultWalletLocalId: Long?) {
        trackedEdit { if (defaultWalletLocalId == null) it.remove(Keys.defaultWalletId) }
    }

    /** Backup version 6's customisations as this phone holds them: null where it has no value of its own (the default). */
    suspend fun backupCustomisations(): BackupCustomisations {
        val p = store.data.first()
        return BackupCustomisations(
            themeId = p[Keys.themeId]?.takeIf { key -> ThemeId.entries.any { it.key == key } },
            showCents = p[Keys.showCents],
            walletsView = p[Keys.walletsViewMode]?.takeIf { key -> WalletsViewMode.entries.any { it.key == key } },
            moduleOffersHidden = p[Keys.moduleOffersHidden],
            fxRatesOnline = p[Keys.fxRatesOnline],
        )
    }

    companion object {
        const val DATA_STORE_NAME = "user_preferences"

        /**
         * After a sign-out wipe the ledger is a fresh install's, so the setup that belonged to it goes too (F-7): the next
         * account goes through onboarding, which checks it and restores or sets it up. Kept: the phone's choices (theme,
         * currency, modules), consent for crash reports and usage counts, and notices already shown. Smarter reading is
         * switched off because it sends composer text and was opted into for the previous account; AI answers go back to
         * their default ([forgetAskAi]), so the next account confirms them for itself. The settings that stay are set aside
         * as the signed-out account's ([forgetAccountSettingsSync]): that account signing back in gets them back as they
         * were, its waiting changes included; any other account replaces them and never receives them.
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
            p.remove(Keys.achievementsKnown)
            p.remove(Keys.insightsVisited)
            p.remove(Keys.appLockUsed)
            // Lucento Pro's welcome and medal belong to the person, like the name: the next account starts without them.
            p.remove(Keys.proWelcomed)
            p.remove(Keys.proMemberSince)
            p.remove(Keys.defaultWalletId)
            p.remove(Keys.displayName)
            // The photo belongs to the account like the name; its file goes with the ledger wipe (LocalLedgerReset). The
            // next account starts on its Google picture again (ProfilePhotoChoice.GOOGLE).
            p.remove(Keys.profilePhotoVersion)
            p.remove(Keys.profilePhotoChoice)
            p.remove(Keys.acknowledgedBudgetMonth)
            p.remove(Keys.budgetMonthPlanned)
            p.remove(Keys.recapMonthSeen)
            p[Keys.smartReading] = false
            forgetAskAi(p)
            forgetAccountSettingsSync(p)
        }

        /** Ask Lucento's AI answers are on unless the person turned them off (owner, 2026-11-17). */
        /** Smarter reading's consent: only the one given since it uses Ask Lucento's AI questions counts ([Keys.smartReading]). */
        internal fun smartReadingOn(p: Preferences): Boolean = p[Keys.smartReading] ?: false

        internal fun askAiOn(p: Preferences): Boolean = p[Keys.askAiOff] != true

        /** The person confirmed what AI answers send ([UserPreferences.askAiConfirmed]). */
        internal fun askAiConfirmed(p: Preferences): Boolean = p[Keys.askAiConfirmed] ?: false

        /** [UserPreferencesRepository.setAskLucentoAi] on [p]: on and agreed, or off with the agreement withdrawn. */
        internal fun applyAskAi(p: MutablePreferences, enabled: Boolean) {
            if (enabled) p.remove(Keys.askAiOff) else p[Keys.askAiOff] = true
            p[Keys.askAiConfirmed] = enabled
            p.remove(Keys.askLucentoAiV1)
        }

        /**
         * AI answers belong to the signed-in account: back to the default (on, not confirmed), so nothing is sent for the
         * next account until it confirms for itself.
         */
        internal fun forgetAskAi(p: MutablePreferences) {
            p.remove(Keys.askAiOff)
            p[Keys.askAiConfirmed] = false
            p.remove(Keys.askLucentoAiV1)
        }

        /**
         * Lucento Shared: the ledger on this phone is replaced by a Shared Space's. A finished onboarding stays finished
         * (a new generation, so screens holding the old ledger's state start over); the guided setup and choices tied to
         * the old ledger go. Categories are the Owner's to set up, so the category prompt is treated as done. The settings
         * about the old ledger (default wallet, the budget moment) no longer count as synced; the person's own stay theirs.
         */
        internal fun resetForSharedSpaceLedger(p: MutablePreferences) {
            if (p[Keys.onboardingComplete] == true) p[Keys.setupGeneration] = (p[Keys.setupGeneration] ?: 0) + 1
            p[Keys.categorySetupDone] = true
            p.remove(Keys.guideStep)
            p.remove(Keys.defaultWalletId)
            p.remove(Keys.acknowledgedBudgetMonth)
            p.remove(Keys.budgetMonthPlanned)
            p.remove(Keys.recapMonthSeen)
            p.remove(Keys.achievementsSeen)
            p.remove(Keys.achievementsKnown)
            forgetSettingsSync(p, AccountSetting.entries.filter { it.ledger })
        }
        const val MAX_DISPLAY_NAME = 40

        // ----------------------------------------------------------------------------- settings that follow the account

        /**
         * [setting]'s value on this phone, as the account's document writes it; null when it has none of its own (the
         * app's default applies). [defaultWallet] gives the document's form of the default wallet's local id.
         */
        private fun rawAccountSettingValue(p: Preferences, setting: AccountSetting, defaultWallet: (Long) -> JsonElement?): JsonElement? =
            when (setting) {
                AccountSetting.THEME_MODE -> p[Keys.themeMode]?.let(::JsonPrimitive)
                AccountSetting.THEME -> p[Keys.themeId]?.let(::JsonPrimitive)
                AccountSetting.FEATURES -> p[Keys.enabledFeatures]?.let { keys -> JsonArray(keys.sorted().map(::JsonPrimitive)) }
                AccountSetting.DISPLAY_NAME -> p[Keys.displayName]?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive)
                AccountSetting.SHOW_CENTS -> p[Keys.showCents]?.let(::JsonPrimitive)
                AccountSetting.WALLETS_VIEW -> p[Keys.walletsViewMode]?.let(::JsonPrimitive)
                AccountSetting.MODULE_OFFERS_HIDDEN -> p[Keys.moduleOffersHidden]?.let(::JsonPrimitive)
                AccountSetting.FX_RATES_ONLINE -> p[Keys.fxRatesOnline]?.let(::JsonPrimitive)
                AccountSetting.DEFAULT_WALLET -> p[Keys.defaultWalletId]?.let(defaultWallet)
                AccountSetting.BUDGET_MONTH_SEEN -> p[Keys.acknowledgedBudgetMonth]?.let(::JsonPrimitive)
                AccountSetting.BUDGET_MONTH_PLANNED -> p[Keys.budgetMonthPlanned]?.let(::JsonPrimitive)
                AccountSetting.RECAP_MONTH_SEEN -> p[Keys.recapMonthSeen]?.let(::JsonPrimitive)
            }

        /** What this phone would put in the account's document for [setting]; a damaged stored value is never sent. */
        internal fun accountSettingValue(p: Preferences, setting: AccountSetting, walletUuid: (Long) -> String?): JsonElement? =
            rawAccountSettingValue(p, setting) { id -> walletUuid(id)?.let(::JsonPrimitive) }?.takeIf { setting.accepts(it) }

        /** Whether [setting] holds a value chosen on this phone or brought by sync (see [Keys.settingAt]). */
        internal fun hasOwnValue(p: Preferences, setting: AccountSetting): Boolean = p[Keys.settingAt(setting)] != null

        /** Every setting's value as stored here (the default wallet by its local id), to see what an edit changed. */
        internal fun accountSettingsSnapshot(p: Preferences): Map<AccountSetting, JsonElement?> =
            AccountSetting.entries.associateWith { rawAccountSettingValue(p, it) { id -> JsonPrimitive(id) } }

        /**
         * After the person's edit: each setting whose value differs from [before] is marked changed here, dated
         * [AccountSettingsMerge.changedAt] (after the value it replaced, so it beats it on every phone). A changed setting
         * is the person's own from then on, never another account's leftover.
         */
        internal fun recordAccountSettingChanges(p: MutablePreferences, before: Map<AccountSetting, JsonElement?>, now: Long) {
            val changed = AccountSetting.entries.filter { before[it] != rawAccountSettingValue(p, it) { id -> JsonPrimitive(id) } }
            if (changed.isEmpty()) return
            changed.forEach { p[Keys.settingAt(it)] = AccountSettingsMerge.changedAt(p[Keys.settingAt(it)], now) }
            p[Keys.accountSettingsDirty] = p[Keys.accountSettingsDirty].orEmpty() + changed.map { it.key }
            ownedHere(p, *changed.toTypedArray())
            p[Keys.accountSettingsChanges] = (p[Keys.accountSettingsChanges] ?: 0) + 1
        }

        /** These settings now hold this phone's own choice (a setup's, or the person's), not another account's leftover. */
        internal fun ownedHere(p: MutablePreferences, vararg settings: AccountSetting) {
            val inherited = p[Keys.accountSettingsInherited] ?: return
            val left = inherited - settings.map { it.key }.toSet()
            if (left.isEmpty()) p.remove(Keys.accountSettingsInherited) else p[Keys.accountSettingsInherited] = left
        }

        /** The settings changed here and not yet accepted by the account. */
        internal fun accountSettingsDirty(p: Preferences): Set<AccountSetting> =
            p[Keys.accountSettingsDirty].orEmpty().mapNotNull(AccountSetting::fromKey).toSet()

        /**
         * Writes the account's [value] for [setting]. False, changing nothing, when this build can't use it (a theme or
         * wallets view it doesn't know, a damaged value, a Lucento Pro theme in [lockedThemes], or a budget moment older
         * than the one already seen here): the phone keeps its own. A default wallet that isn't on this phone (deleted, or
         * not downloaded yet) leaves none here, and the next merge tries again.
         */
        internal fun applyAccountSetting(
            p: MutablePreferences,
            setting: AccountSetting,
            value: JsonElement,
            walletId: (String) -> Long?,
            lockedThemes: Set<String>,
        ): Boolean {
            if (!setting.accepts(value)) return false
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            val flag = (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            fun <T> put(key: Preferences.Key<T>, v: T?) {
                if (v == null) p.remove(key) else p[key] = v
            }
            when (setting) {
                AccountSetting.THEME_MODE -> put(Keys.themeMode, text)
                AccountSetting.THEME -> {
                    if (text != null && ThemeId.entries.none { it.key == text }) return false
                    // MONETIZATION §3.8 and §6.3: a Pro theme is a new choice here, and this phone's plan doesn't allow it.
                    if (text != null && text in lockedThemes && p[Keys.themeId] != text) return false
                    put(Keys.themeId, text)
                }
                AccountSetting.FEATURES ->
                    // Keys this build doesn't know (a newer build's modules) are kept, so its own edits never drop them.
                    put(Keys.enabledFeatures, (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet())
                AccountSetting.DISPLAY_NAME -> put(Keys.displayName, text?.let(::cleanDisplayName)?.takeIf { it.isNotEmpty() })
                AccountSetting.SHOW_CENTS -> put(Keys.showCents, flag)
                AccountSetting.WALLETS_VIEW -> {
                    if (text != null && WalletsViewMode.entries.none { it.key == text }) return false
                    put(Keys.walletsViewMode, text)
                }
                AccountSetting.MODULE_OFFERS_HIDDEN -> put(Keys.moduleOffersHidden, flag)
                AccountSetting.FX_RATES_ONLINE -> put(Keys.fxRatesOnline, flag)
                AccountSetting.DEFAULT_WALLET -> put(Keys.defaultWalletId, text?.let(walletId))
                AccountSetting.BUDGET_MONTH_SEEN -> {
                    // The moment is shown once a month: an earlier month never brings it back ("yyyy-MM" sorts as text).
                    val seen = p[Keys.acknowledgedBudgetMonth]
                    if (seen != null && (text == null || text < seen)) return false
                    put(Keys.acknowledgedBudgetMonth, text)
                }
                // The check-in's months, like the budget moment: an earlier month never asks again about a later one.
                AccountSetting.BUDGET_MONTH_PLANNED -> {
                    val planned = p[Keys.budgetMonthPlanned]
                    if (planned != null && (text == null || text < planned)) return false
                    put(Keys.budgetMonthPlanned, text)
                }
                AccountSetting.RECAP_MONTH_SEEN -> {
                    val seen = p[Keys.recapMonthSeen]
                    if (seen != null && (text == null || text < seen)) return false
                    put(Keys.recapMonthSeen, text)
                }
            }
            return true
        }

        /**
         * [UserPreferencesRepository.bindAccountSettings] on [p]. A phone already holding [account]'s settings keeps them.
         * When [claiming], a sign-out's set-aside settings ([forgetAccountSettingsSync]) are this account's own again if it
         * is the one that signed out, waiting changes included. Otherwise they are another account's leftovers (or nobody
         * knows whose): their sync state goes, they stay inherited, and the next merge joins [account].
         */
        internal fun bindAccountSettingsInto(p: MutablePreferences, account: String, claiming: Boolean) {
            val current = p[Keys.accountSettingsAccount]
            if (current == account) return
            if (current != null) {
                // Another account's settings (never expected: sync refuses a different account): set aside as on a sign-out.
                forgetAccountSettingsSync(p)
            } else if (!claiming && p[Keys.accountSettingsPrevious] == null && p[Keys.accountSettingsInherited] == null) {
                // A phone that synced before settings did: this account's from now on, and nothing changes here.
                p[Keys.accountSettingsAccount] = account
                return
            }
            val previous = p[Keys.accountSettingsPrevious]
            p.remove(Keys.accountSettingsPrevious)
            p[Keys.accountSettingsAccount] = account
            if (previous != null && previous == accountHash(account)) {
                keepOwnSettings(p, account)
                return
            }
            // Someone else's leftovers lose the sync state they had; anything changed here since the sign-out stays.
            val inherited = p[Keys.accountSettingsInherited].orEmpty()
            AccountSetting.entries.filter { it.key in inherited }.forEach { p.remove(Keys.settingAt(it)) }
            val dirty = p[Keys.accountSettingsDirty].orEmpty() - inherited
            if (dirty.isEmpty()) p.remove(Keys.accountSettingsDirty) else p[Keys.accountSettingsDirty] = dirty
            p[Keys.accountSettingsJoin] = account
        }

        /**
         * The same person's settings, their own again (signing back in, or leaving a Shared Space): nothing is inherited,
         * and the binding is [account]'s. The ledger's two settings belonged to the records that were removed.
         */
        internal fun keepOwnSettings(p: MutablePreferences, account: String?) {
            p.remove(Keys.accountSettingsInherited)
            p.remove(Keys.accountSettingsPrevious)
            p.remove(Keys.accountSettingsJoin)
            if (account == null) p.remove(Keys.accountSettingsAccount) else p[Keys.accountSettingsAccount] = account
            forgetSettingsSync(p, AccountSetting.entries.filter { it.ledger })
        }

        /**
         * [UserPreferencesRepository.mergeAccountSettings] on [p], so the merge can be tested without a DataStore.
         * [lockedThemes] has no default, so no caller can leave the plan out (Pro-gate review 2026-11-23, F1).
         */
        internal fun mergeAccountSettingsInto(
            p: MutablePreferences,
            account: String,
            document: JsonObject?,
            settings: Set<AccountSetting>,
            walletUuid: (Long) -> String?,
            walletId: (String) -> Long?,
            lockedThemes: Set<String>,
        ): AccountSettingsMerge.Result {
            // Normally bound at the start of the cycle; settings set aside at a sign-out are never merged unbound.
            if (p[Keys.accountSettingsAccount] != account) bindAccountSettingsInto(p, account, claiming = false)
            val joining = p[Keys.accountSettingsJoin] == account
            // What this phone may not send stays out of its waiting list: a Partner's phone holds the Owner's records.
            val dirty = p[Keys.accountSettingsDirty].orEmpty().filter { key -> AccountSetting.fromKey(key)?.let { it in settings } == true }.toMutableSet()
            val inherited = p[Keys.accountSettingsInherited].orEmpty().toMutableSet()
            fun local(setting: AccountSetting) = LocalSetting(
                value = accountSettingValue(p, setting, walletUuid),
                at = p[Keys.settingAt(setting)],
                dirty = setting.key in dirty,
                inherited = setting.key in inherited,
            )
            var taken = 0
            var replaced = 0
            if (document != null) {
                for (setting in AccountSetting.entries.filter { it in settings }) {
                    val entry = AccountSettingsDocument.entry(document, setting) ?: continue
                    val here = local(setting)
                    val decision = AccountSettingsMerge.decide(here, entry, joining)
                    if (decision !is AccountSettingsMerge.Decision.Take) continue
                    val before = rawAccountSettingValue(p, setting) { id -> JsonPrimitive(id) }
                    applyAccountSetting(p, setting, entry.value, walletId, lockedThemes)
                    val changed = rawAccountSettingValue(p, setting) { id -> JsonPrimitive(id) } != before
                    if (changed) taken++
                    // The account's entry counts as seen even when this phone can't use it (a Pro theme its plan doesn't
                    // allow): an older change here never goes up over it, and a later purchase applies it.
                    p[Keys.settingAt(setting)] = entry.at
                    dirty -= setting.key
                    inherited -= setting.key
                    // "Changed on another device" only when this phone's change really gave way (Pro-gate review F2).
                    if (decision.replacesLocalChange && changed) replaced++
                }
            }
            if (dirty.isEmpty()) p.remove(Keys.accountSettingsDirty) else p[Keys.accountSettingsDirty] = dirty
            if (inherited.isEmpty()) p.remove(Keys.accountSettingsInherited) else p[Keys.accountSettingsInherited] = inherited
            p[Keys.accountSettingsAccount] = account
            // A setting whose value can't be written for the account (a default wallet with no stable id yet, a damaged
            // value) stays waiting here rather than going up as "no value".
            val sendable = AccountSetting.entries.filter { s ->
                s in settings && (rawAccountSettingValue(p, s) { id -> JsonPrimitive(id) } == null || accountSettingValue(p, s, walletUuid) != null)
            }
            val upload = AccountSettingsMerge.upload(document, sendable.associateWith(::local))
            if (upload == null) p.remove(Keys.accountSettingsJoin)
            return AccountSettingsMerge.Result(upload, taken, replaced)
        }

        /** [UserPreferencesRepository.markAccountSettingsSent] on [p]. */
        internal fun markAccountSettingsSentInto(p: MutablePreferences, upload: AccountSettingsMerge.Upload) {
            val dirty = p[Keys.accountSettingsDirty].orEmpty().toMutableSet()
            // A setting changed again while the upload was on its way has a newer time: it stays waiting.
            upload.sent.forEach { (setting, at) -> if ((p[Keys.settingAt(setting)] ?: 0L) == at) dirty -= setting.key }
            upload.seeded.forEach { (setting, at) -> if (p[Keys.settingAt(setting)] == null) p[Keys.settingAt(setting)] = at }
            if (dirty.isEmpty()) p.remove(Keys.accountSettingsDirty) else p[Keys.accountSettingsDirty] = dirty
            // The join is complete once the account holds this phone's part.
            p.remove(Keys.accountSettingsJoin)
        }

        /**
         * At a sign-out wipe: the settings stay on the phone, set aside as the signed-out account's. A one-way hash of that
         * account is kept (never its id), with each setting's sync state, so the same account signing back in gets them as
         * they were ([bindAccountSettingsInto]). Until a setting is changed here it counts as inherited: shown, but never
         * sent to another account.
         */
        internal fun forgetAccountSettingsSync(p: MutablePreferences) {
            // A wipe that runs again (an interrupted one finished at the next launch) finds no account: what it set aside stays.
            p[Keys.accountSettingsAccount]?.let { p[Keys.accountSettingsPrevious] = accountHash(it) }
            p.remove(Keys.accountSettingsAccount)
            p.remove(Keys.accountSettingsJoin)
            p[Keys.accountSettingsInherited] = AccountSetting.entries.map { it.key }.toSet()
        }

        private fun forgetSettingsSync(p: MutablePreferences, settings: Collection<AccountSetting>) {
            settings.forEach { p.remove(Keys.settingAt(it)) }
            val dirty = p[Keys.accountSettingsDirty].orEmpty() - settings.map { it.key }.toSet()
            if (dirty.isEmpty()) p.remove(Keys.accountSettingsDirty) else p[Keys.accountSettingsDirty] = dirty
        }

        /** SHA-256 of the account id: enough to recognise the same account, and nothing to read the id back from. */
        internal fun accountHash(account: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        /**
         * [UserPreferencesRepository.completeRestore] on [p]. Settings the restored account brought back stay, and so does
         * one chosen here ([hasOwnValue]). What another account left here and the restored one didn't replace goes back to
         * the default. The theme mode and modules then get [themeMode] and [features]; a default is written as no value at
         * all, so it never goes up as this person's choice.
         */
        internal fun completeRestoreInto(
            p: MutablePreferences,
            primaryCurrency: String,
            themeMode: ThemeMode,
            features: Set<Feature>,
            categoriesChosen: Boolean,
        ) {
            p[Keys.primaryCurrency] = primaryCurrency
            val inherited = p[Keys.accountSettingsInherited].orEmpty()
            AccountSetting.entries.filter { it.key in inherited }.forEach { setting ->
                // Back to the default, which is never a Pro theme.
                applyAccountSetting(p, setting, JsonNull, walletId = { null }, lockedThemes = emptySet())
                p.remove(Keys.settingAt(setting))
            }
            forgetSettingsSync(p, AccountSetting.entries.filter { it.key in inherited })
            p.remove(Keys.accountSettingsInherited)
            if (!hasOwnValue(p, AccountSetting.THEME_MODE)) {
                if (themeMode == ThemeMode.SYSTEM) p.remove(Keys.themeMode) else p[Keys.themeMode] = themeMode.name
            }
            if (!hasOwnValue(p, AccountSetting.FEATURES)) {
                if (features == Feature.entries.toSet()) p.remove(Keys.enabledFeatures) else p[Keys.enabledFeatures] = features.map(Feature::key).toSet()
            }
            p[Keys.categorySetupDone] = categoriesChosen
            p[Keys.onboardingComplete] = true
            p[Keys.localDataNoticeSeen] = true
            p.remove(Keys.guideStep)
        }

        /**
         * [UserPreferencesRepository.applyBackupSettings] on [p] (a Replace restore). Each value is written only where it
         * changes what this phone shows, and one equal to the default as no value at all, so an untouched default never goes
         * up as the person's newest choice. Smarter reading can be turned off by a file, never on (that needs this phone's
         * own consent). The budget moment never goes back to an earlier month. Version 6's customisations are null when the
         * file has no choice of its own: this phone keeps its own. [theme] is the file's theme where the plan allows it.
         */
        internal fun applyBackupSettingsInto(p: MutablePreferences, settings: BackupSettings, defaultWalletLocalId: Long?, theme: ThemeId?) {
            fun <T : Any> putEffective(key: Preferences.Key<T>, value: T, default: T) {
                if ((p[key] ?: default) == value) return
                if (value == default) p.remove(key) else p[key] = value
            }
            p[Keys.onboardingComplete] = settings.onboardingComplete
            val mode = runCatching { ThemeMode.valueOf(settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
            putEffective(Keys.themeMode, mode.name, ThemeMode.SYSTEM.name)
            p[Keys.primaryCurrency] = settings.primaryCurrency
            putEffective(Keys.enabledFeatures, settings.enabledFeatures.toSet(), Feature.entries.map(Feature::key).toSet())
            settings.acknowledgedBudgetMonth?.let { month ->
                val seen = p[Keys.acknowledgedBudgetMonth]
                if (seen == null || seen < month) p[Keys.acknowledgedBudgetMonth] = month
            }
            val name = settings.displayName?.let(::cleanDisplayName)?.takeIf { it.isNotEmpty() }
            if (name == null) p.remove(Keys.displayName) else p[Keys.displayName] = name
            p[Keys.categorySetupDone] = settings.categorySetupDone
            if (defaultWalletLocalId == null) p.remove(Keys.defaultWalletId) else p[Keys.defaultWalletId] = defaultWalletLocalId
            if (settings.guideStep == null) p.remove(Keys.guideStep) else p[Keys.guideStep] = settings.guideStep
            if (!settings.smartReading) p[Keys.smartReading] = false
            theme?.let { putEffective(Keys.themeId, it.key, ThemeId.SUNRISE.key) }
            settings.showCents?.let { putEffective(Keys.showCents, it, true) }
            settings.walletsView?.let { key -> WalletsViewMode.entries.firstOrNull { it.key == key } }
                ?.let { putEffective(Keys.walletsViewMode, it.key, WalletsViewMode.CARDS.key) }
            settings.moduleOffersHidden?.let { putEffective(Keys.moduleOffersHidden, it, false) }
            settings.fxRatesOnline?.let { putEffective(Keys.fxRatesOnline, it, true) }
        }

        /** Writes [month] to [key] unless a later month is already there ("yyyy-MM" sorts as text). */
        internal fun laterMonth(p: MutablePreferences, key: Preferences.Key<String>, month: java.time.YearMonth) {
            val text = month.toString()
            val stored = p[key]
            if (stored == null || stored < text) p[key] = text
        }

        /** The display name as [UserPreferencesRepository.setDisplayName] stores it. */
        internal fun cleanDisplayName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_DISPLAY_NAME)

        fun defaultCurrency(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
                ?.takeIf { it != "XXX" }
                ?: "USD"
    }
}
