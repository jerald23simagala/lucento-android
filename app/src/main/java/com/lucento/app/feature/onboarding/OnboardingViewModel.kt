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
import com.lucento.app.domain.FirstRunDefaults
import com.lucento.app.domain.FirstRunStep
import com.lucento.app.domain.OnboardingFlow
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.startingBalance
import com.lucento.app.feature.budget.CategoryPicks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FinancialFocus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "I already have an account" on the welcome screen: reminder → sign in → download → Home (or normal setup). */
enum class RestoreStage { NONE, WARN, SIGN_IN, RESTORING, EMPTY, FAILED }

/**
 * Short first run: welcome → currency → starting balance, then the composer (opened once onboarding completes).
 *
 * Theme, categories, goals, recurring payments and the other modules use [FirstRunDefaults]. Sync stays off.
 * Categories are the recommended set, written before onboarding is marked complete. People who already finished
 * an older setup are not sent here again ([OnboardingFlow.showOnboarding]).
 */
class OnboardingViewModel(
    private val savedState: SavedStateHandle,
    private val preferences: UserPreferencesRepository,
    private val categories: CategoryRepository,
    /** Optional cloud sync; null in tests. Nothing is sent unless the user connects an account on the sync step. */
    private val auth: com.lucento.app.data.sync.AuthRepository? = null,
    private val syncPreferences: com.lucento.app.data.sync.SyncPreferences? = null,
    private val appContext: android.content.Context? = null,
    /** For "I already have an account": the first sync pulls the account, then wallets and categories are read back. */
    private val sync: com.lucento.app.data.sync.SyncRepository? = null,
    private val wallets: com.lucento.app.data.repository.WalletRepository? = null,
    private val accounts: com.lucento.app.data.sync.AccountBoundary? = null,
) : ViewModel() {

    /** The connected account's email, or null while signed out. */
    val signedInEmail: StateFlow<String?> = (auth?.observeSignedInEmail() ?: kotlinx.coroutines.flow.flowOf(null))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage

    private val _switchPrompt = MutableStateFlow(false)
    /** A different account signed in while this phone still has unsynced data. Nothing is wiped until confirmed. */
    val switchPrompt: StateFlow<Boolean> = _switchPrompt

    val syncAvailable: Boolean get() = auth != null && com.lucento.app.data.sync.SupabaseModule.isConfigured
    val googleAvailable: Boolean get() = com.lucento.app.data.sync.SupabaseModule.isGoogleSignInConfigured

    /** The user chose to connect by email: open the server connection while they type. */
    fun prepareConnection() {
        auth?.prewarm()
    }

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
                        when (accounts?.afterSignIn()) {
                            is com.lucento.app.data.sync.AccountBoundary.AfterSignIn.ConfirmSwitch -> {
                                _switchPrompt.value = true
                                null
                            }
                            else -> {
                                syncPreferences?.setEnabled(true)
                                // Restoring: this phone has nothing of its own yet, so the account can be pulled right away.
                                if (restoreStage.value == RestoreStage.SIGN_IN) runRestore()
                                null
                            }
                        }
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

    // ---------------------------------------------------------------- restoring an account on a new phone

    /** Where "I already have an account" stands; [RestoreStage.NONE] is the normal setup. Survives process death. */
    val restoreStage: StateFlow<RestoreStage> = savedState.getStateFlow(KEY_RESTORE, RestoreStage.NONE.name)
        .map { runCatching { RestoreStage.valueOf(it) }.getOrDefault(RestoreStage.NONE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RestoreStage.NONE)

    private val _restoreMessage = MutableStateFlow<String?>(null)
    /** Why the last restore attempt stopped (shown with Try again). */
    val restoreMessage: StateFlow<String?> = _restoreMessage

    private var restoring = false

    init {
        // The app was closed mid-download: pick it up again rather than leave the spinner with nothing behind it.
        if (savedState.get<String>(KEY_RESTORE) == RestoreStage.RESTORING.name) runRestore()
    }

    private fun setRestoreStage(stage: RestoreStage) {
        savedState[KEY_RESTORE] = stage.name
    }

    /** Drops unsynced changes and the previous ledger, then continues with the account just signed in. */
    fun confirmSwitch() {
        viewModelScope.launch {
            accounts?.confirmSwitch()
            _switchPrompt.value = false
            syncPreferences?.setEnabled(true)
            if (restoreStage.value == RestoreStage.SIGN_IN) runRestore()
        }
    }

    /** Keeps the previous ledger and leaves the new session. */
    fun cancelSwitch() {
        viewModelScope.launch {
            accounts?.cancelSwitch()
            _switchPrompt.value = false
        }
    }

    /** "I already have an account": first the reminder to sync the old phone. */
    fun startRestore() {
        if (!syncAvailable) return
        _syncMessage.value = null
        setRestoreStage(RestoreStage.WARN)
    }

    /** Past the reminder: sign in (or, already signed in, go straight to the download). */
    fun continueRestore() {
        if (signedInEmail.value != null) runRestore() else setRestoreStage(RestoreStage.SIGN_IN)
    }

    /** Back out of restoring to the welcome screen; nothing was written. */
    fun cancelRestore() {
        if (restoreStage.value == RestoreStage.RESTORING) return
        _restoreMessage.value = null
        setRestoreStage(RestoreStage.NONE)
    }

    /**
     * The account had nothing to bring back (or the user chose to): continue with the normal setup from the first
     * question. A connected account stays connected, so the sync step shows it and syncing starts when setup finishes.
     */
    fun setUpAsNew() {
        _restoreMessage.value = null
        setRestoreStage(RestoreStage.NONE)
        savedState[KEY_SHORT] = true
        savedState[KEY_STEP] = FirstRunStep.CURRENCY.ordinal
    }

    /**
     * Pulls the account with a full sync (this phone holds only the seeded income categories, which merge with the
     * account's own), then finishes onboarding without the story or guided setup — or, for an empty account, moves to
     * [RestoreStage.EMPTY]. Nothing is marked complete unless the download succeeded.
     */
    fun runRestore() {
        val repository = sync ?: return
        if (restoring) return
        restoring = true
        _restoreMessage.value = null
        setRestoreStage(RestoreStage.RESTORING)
        viewModelScope.launch {
            try {
                when (accounts?.afterSignIn()) {
                    is com.lucento.app.data.sync.AccountBoundary.AfterSignIn.ConfirmSwitch -> {
                        restoring = false
                        _switchPrompt.value = true
                        setRestoreStage(RestoreStage.SIGN_IN)
                        return@launch
                    }
                    else -> syncPreferences?.setEnabled(true)
                }
                when (val result = repository.sync()) {
                    is com.lucento.app.data.sync.SyncResult.Success -> {
                        val restoredWallets = wallets?.observeWallets()?.first().orEmpty()
                        val restoredCategories = categories.observeCategories().first()
                        if (com.lucento.app.domain.RestorePlan.hasData(restoredWallets, restoredCategories)) {
                            preferences.completeRestore(
                                primaryCurrency = com.lucento.app.domain.RestorePlan.primaryCurrency(restoredWallets, currency.value),
                                themeMode = theme.value,
                                features = com.lucento.app.domain.RestorePlan.features,
                                categoriesChosen = com.lucento.app.domain.RestorePlan.hasSpendingCategories(restoredCategories),
                            )
                            appContext?.let { com.lucento.app.data.sync.SyncScheduler.ensurePeriodic(it) }
                        } else {
                            setRestoreStage(RestoreStage.EMPTY)
                        }
                    }
                    com.lucento.app.data.sync.SyncResult.NotSignedIn -> {
                        _restoreMessage.value = "You’re signed out. Sign in again to bring your data across."
                        setRestoreStage(RestoreStage.SIGN_IN)
                    }
                    com.lucento.app.data.sync.SyncResult.NotConfigured -> {
                        _restoreMessage.value = "Cloud sync isn’t available in this version of Lucento."
                        setRestoreStage(RestoreStage.FAILED)
                    }
                    com.lucento.app.data.sync.SyncResult.AccountMismatch -> {
                        _switchPrompt.value = true
                        setRestoreStage(RestoreStage.SIGN_IN)
                    }
                    is com.lucento.app.data.sync.SyncResult.Retryable -> {
                        _restoreMessage.value = "Your data couldn’t be downloaded (${result.message}). Check your connection and try again."
                        setRestoreStage(RestoreStage.FAILED)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Lucento", "Restoring the account failed", e)
                _restoreMessage.value = "Your data couldn’t be downloaded. Check your connection and try again."
                setRestoreStage(RestoreStage.FAILED)
            } finally {
                restoring = false
            }
        }
    }

    private val stepIndex = savedState.getStateFlow(KEY_STEP, 0)
    private val shortFlow = savedState.getStateFlow(KEY_SHORT, false)

    /** Welcome, currency or wallet. The composer is the fourth screen and opens after [finishWallet]. */
    val step: StateFlow<FirstRunStep> = combine(stepIndex, shortFlow) { index, short ->
        OnboardingFlow.resume(onboardingComplete = false, savedIndex = index, shortFlow = short) ?: FirstRunStep.WELCOME
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FirstRunStep.WELCOME)
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
        if (current == FirstRunStep.WALLET || current == FirstRunStep.FIRST_ENTRY) return
        val following = OnboardingFlow.next(current) ?: return
        if (following == FirstRunStep.FIRST_ENTRY) return
        savedState[KEY_SHORT] = true
        savedState[KEY_STEP] = following.ordinal
    }

    fun goTo(target: FirstRunStep) {
        if (target.ordinal <= step.value.ordinal && target != FirstRunStep.FIRST_ENTRY) {
            savedState[KEY_SHORT] = true
            savedState[KEY_STEP] = target.ordinal
        }
    }

    fun setPicks(value: CategoryPicks) {
        savedState[KEY_PRESETS] = ArrayList(value.presetKeys.toList())
        savedState[KEY_CUSTOM] = ArrayList(value.customNames)
    }

    fun back() {
        val previous = OnboardingFlow.previous(step.value) ?: return
        if (previous == FirstRunStep.FIRST_ENTRY) return
        savedState[KEY_SHORT] = true
        savedState[KEY_STEP] = previous.ordinal
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

    /**
     * Saves the recommended categories and one cash wallet, then marks onboarding complete. The shell opens the
     * composer because [UserPreferencesRepository.completeOnboarding] sets the pending-first-entry flag.
     * An empty balance starts at zero. A second tap reuses a wallet of the same name instead of duplicating it.
     */
    fun finishWallet(name: String, balanceText: String) {
        if (finishing) return
        val code = currency.value
        val separator = com.lucento.app.core.money.MoneyFormatter.decimalSeparator()
        val balance = startingBalance(balanceText, code, separator)
        if (balance == null) {
            _error.value = "Enter an amount, or leave it empty to start at zero."
            return
        }
        val walletName = name.trim().ifEmpty { FirstRunDefaults.DEFAULT_WALLET_NAME }
        finishing = true
        _error.value = null
        viewModelScope.launch {
            try {
                val picks = CategoryPicks(presetKeys = FirstRunDefaults.categoryPresetKeys())
                categories.applySelection(picks.choices(emptyList()))
                val repository = wallets
                if (repository != null) {
                    val existing = repository.observeWallets().first().firstOrNull {
                        !it.type.isLiability && it.currencyCode == code && it.name.equals(walletName, ignoreCase = true)
                    }
                    val id = existing?.id ?: when (val result = repository.create(walletName, WalletType.CASH, balance)) {
                        is com.lucento.app.data.repository.WalletRepository.SaveResult.Saved -> result.id
                        else -> {
                            _error.value = "That account couldn’t be saved. Nothing else was changed — please try again."
                            finishing = false
                            return@launch
                        }
                    }
                    preferences.setDefaultWallet(id)
                }
                preferences.completeOnboarding(code, theme.value, FirstRunDefaults.features())
                val context = appContext
                if (context != null && syncPreferences?.isEnabled() == true && signedInEmail.value != null) {
                    com.lucento.app.data.sync.SyncScheduler.ensurePeriodic(context)
                    com.lucento.app.data.sync.SyncScheduler.syncNow(context)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Lucento", "Saving the first account failed", e)
                _error.value = "Your account couldn’t be saved. Nothing was changed — please try again."
                finishing = false
            }
        }
    }

    companion object {
        private const val KEY_STEP = "step"
        private const val KEY_SHORT = "short_flow"
        private const val KEY_RESTORE = "restore_stage"
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
                    sync = container.syncRepository, wallets = container.wallets,
                    accounts = container.accountBoundary,
                )
            }
        }
    }
}
