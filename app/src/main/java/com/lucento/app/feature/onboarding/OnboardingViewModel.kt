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
import kotlinx.coroutines.flow.first
import com.lucento.app.domain.Feature
import com.lucento.app.domain.FinancialFocus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "I already have an account" on the welcome screen: reminder → sign in → download → Home (or normal setup). */
/**
 * "I already have Lucento". [PLAN_NEEDED]: signed in, but bringing synced data to the phone is Lucento Pro — said on its
 * own page, never by dropping back to the sign-in form (owner test round 2: after a password reset it looked as if
 * signing in had failed).
 */
enum class RestoreStage { NONE, WARN, SIGN_IN, RESTORING, EMPTY, FAILED, PLAN_NEEDED }

/**
 * Setup, in three stages people see (see [com.lucento.app.domain.SetupStage]):
 * welcome (start fresh, sign in to an account, or restore a backup file) → **Personalize**: focus → how you pay →
 * spending categories → review → currency & appearance → **Set up**: back up and sync (account, file or this phone
 * only) → summary, then the guided setup (wallets, budget, …) → **Get started**.
 *
 * Focus and payment types can be skipped (safe defaults); tracking spending and budgeting are always included. Choosing spending categories cannot: the budget is built from
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
    /** For "I already have an account": the first sync pulls the account, then wallets and categories are read back. */
    private val sync: com.lucento.app.data.sync.SyncRepository? = null,
    private val wallets: com.lucento.app.data.repository.WalletRepository? = null,
    private val accounts: com.lucento.app.data.sync.AccountBoundary? = null,
    private val usage: com.lucento.app.domain.UsageCountsService? = null,
    /** Restoring a backup file on the welcome screen (offline: nothing is sent anywhere). Null in tests. */
    private val backup: com.lucento.app.domain.backup.BackupEngine? = null,
    /**
     * Lucento Shared (docs/shared): an account that is a Partner gets its Shared Space here instead of its own ledger,
     * a new account can join the space it was invited to, and the reason a space left this phone is shown. Null in tests.
     */
    private val space: com.lucento.app.data.sharedspace.SharedSpaceRepository? = null,
    /**
     * Lucento Pro (docs/MONETIZATION.md §3.5): whether this account's plan includes cloud restore and sync. Asked after a
     * sign-in, so it must re-read the server first (the account's GRANT or purchase rows). Null in tests: always allowed.
     * A Partner's binding to their Shared Space counts as allowed (the space is on the Owner's plan).
     */
    private val planAllowsCloud: (suspend () -> Boolean)? = null,
    /**
     * Google Play on this phone holds a Pro purchase made while signed out
     * ([com.lucento.app.data.entitlement.EntitlementManager.boughtWithoutAccount]); false in tests.
     */
    purchaseWithoutAccount: kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
) : ViewModel() {

    /**
     * Owner decision 2026-11-14: the account that signs in keeps a purchase made signed out for good, so the sign-in step
     * says so first while Google Play on this phone holds one.
     */
    val purchaseWithoutAccount: StateFlow<Boolean> = purchaseWithoutAccount.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Anonymous usage counts: one `onboarding_step_viewed` per screen, numbered from 1 (welcome). */
    fun onStepShown(step: Int) {
        val counts = usage ?: return
        viewModelScope.launch {
            if (step == STEP_WELCOME) counts.noteWelcome()
            counts.stepViewed(onboardingStepNumber(step))
        }
    }

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

    /**
     * The user switched to email: open the server connection while they type, and drop a message left from the Google
     * attempt (e.g. "No Google account could be used"), which no longer applies to what is on screen.
     */
    fun prepareConnection() {
        _syncMessage.value = null
        auth?.prewarm()
    }

    /** Google's account picker runs from [activity]; it is used for this call only and not retained. */
    fun connectGoogle(activity: android.content.Context) = connect { auth?.signInWithGoogle(activity) }

    /**
     * An email account. Creating one records the Terms and Privacy Policy version agreed to on the form, then waits for
     * the address to be verified ([verification]); an address that already has an account is tried with the same
     * password, so a person who made theirs earlier (or verified it while Lucento was closed) simply carries on.
     */
    fun connectEmail(email: String, password: String, mode: com.lucento.app.feature.settings.EmailMode) {
        val creating = mode == com.lucento.app.feature.settings.EmailMode.CREATE
        connect(email = email, password = password) {
            if (creating) {
                auth?.signUpWithEmail(email, password, acceptedTermsVersion = com.lucento.app.feature.legal.LegalDocuments.VERSION)
            } else {
                auth?.signInWithEmail(email, password)
            }
        }
    }

    // ---------------------------------------------------------------- a new account becoming ready

    /**
     * Waiting for a new email account's link to be opened ("Check your inbox"); the address survives a restart in
     * saved state, the password never does.
     */
    val verification = com.lucento.app.feature.account.EmailVerification(
        scope = viewModelScope,
        check = { email, password -> auth?.checkEmailConfirmed(email, password) ?: com.lucento.app.data.sync.AuthRepository.EmailCheck.UNREACHABLE },
        resend = { email -> auth?.resendConfirmation(email) ?: com.lucento.app.data.sync.AuthRepository.ResendResult.FAILED },
        onVerified = { afterSignIn() },
        persist = { email -> if (email == null) savedState.remove<String>(KEY_VERIFY_EMAIL) else savedState[KEY_VERIFY_EMAIL] = email },
        restoredEmail = savedState.get<String>(KEY_VERIFY_EMAIL),
    )

    private val _accountReady = MutableStateFlow<com.lucento.app.feature.account.AccountReady?>(null)
    /** "Email verified" / "You're connected" on the setup path, until the person continues (or it moves on by itself). */
    val accountReady: StateFlow<com.lucento.app.feature.account.AccountReady?> = _accountReady

    /** From the account dialog: on to personalizing Lucento. */
    fun continueFromAccount() {
        if (_accountReady.value?.settling == true) return
        _accountReady.value = null
        if (step.value == STEP_SYNC) {
            savedState[KEY_BACKUP_PATH] = BackupPath.ACCOUNT.name
            moveTo(STEP_FOCUS)
        }
    }

    private val _accountCheck = MutableStateFlow<AccountCheck>(AccountCheck.Idle)
    /** The connected account is being checked, or couldn't be (P0-2). Shown on the sync step. */
    val accountCheck: StateFlow<AccountCheck> = _accountCheck

    private val _holdsAccountData = MutableStateFlow(false)
    /**
     * Part of the connected account is already on this phone (a restore that didn't finish). Leaving the account then
     * removes that partial copy, so the screen asks first.
     */
    val holdsAccountData: StateFlow<Boolean> = _holdsAccountData

    /** The restore started because a returning account was recognised, not because the user asked: "Welcome back". */
    val welcomeBack: StateFlow<Boolean> = savedState.getStateFlow(KEY_WELCOME_BACK, false)

    /** Lucento Shared: "I was invited to share" on the welcome screen — the sign-in speaks of the invitation. */
    val joiningByInvitation: StateFlow<Boolean> = savedState.getStateFlow(KEY_INVITED, false)

    /**
     * Signing in on the setup path checks the account first (P0-2): an account that already has data is restored
     * instead of being set up again. Sync is turned on only when onboarding finishes (or a restore starts), so nothing
     * is pulled into — or uploaded from — a half-finished setup.
     */
    private fun connect(
        email: String? = null,
        password: String? = null,
        signIn: suspend () -> com.lucento.app.data.sync.AuthRepository.SignInResult?,
    ) {
        if (_connecting.value) return
        _connecting.value = true
        _syncMessage.value = null
        viewModelScope.launch {
            try {
                _syncMessage.value = when (val result = signIn()) {
                    com.lucento.app.data.sync.AuthRepository.SignInResult.Success -> {
                        afterSignIn()
                        null
                    }
                    com.lucento.app.data.sync.AuthRepository.SignInResult.Cancelled, null -> null
                    com.lucento.app.data.sync.AuthRepository.SignInResult.NotConfigured ->
                        "Cloud sync isn’t available in this version of Lucento yet. You can keep going — everything works offline."
                    is com.lucento.app.data.sync.AuthRepository.SignInResult.ConfirmEmail -> {
                        // "Check your inbox": it notices the verification by itself and continues from there.
                        if (password != null) verification.start(result.email, password)
                        null
                    }
                    is com.lucento.app.data.sync.AuthRepository.SignInResult.AlreadyRegistered ->
                        existingAccount(result.email, password)
                    is com.lucento.app.data.sync.AuthRepository.SignInResult.Failed -> result.message
                }
            } finally {
                _connecting.value = false
            }
        }
    }

    /**
     * "Create account" for an address that already has one. The same password is tried: it is the person's own account
     * (made earlier, or verified while Lucento was closed) — carry on, a returning account being restored as always.
     * Otherwise they're pointed to signing in. Returns the message to show, or null.
     */
    private suspend fun existingAccount(email: String, password: String?): String? {
        val repository = auth ?: return null
        val check = if (password != null) repository.checkEmailConfirmed(email, password) else com.lucento.app.data.sync.AuthRepository.EmailCheck.WRONG_PASSWORD
        return when (check) {
            com.lucento.app.data.sync.AuthRepository.EmailCheck.CONFIRMED -> {
                afterSignIn()
                null
            }
            com.lucento.app.data.sync.AuthRepository.EmailCheck.PENDING -> {
                verification.start(email, password!!, emailJustSent = false)
                null
            }
            com.lucento.app.data.sync.AuthRepository.EmailCheck.WRONG_PASSWORD -> if (restoreStage.value == RestoreStage.SIGN_IN) {
                "$email already has a Lucento account. Choose Sign in to use it — or continue with Google if you made it that way."
            } else {
                "$email already has a Lucento account. If it’s yours, go back to the start and choose “I already have " +
                    "Lucento” to sign in — or continue with Google if you made it that way."
            }
            com.lucento.app.data.sync.AuthRepository.EmailCheck.UNREACHABLE ->
                "Couldn’t reach Lucento to finish. Check your connection and try again."
        }
    }

    /** Signed in (Google, email, or a verified new email account): a different account is confirmed first. */
    private suspend fun afterSignIn() {
        when (accounts?.afterSignIn()) {
            is com.lucento.app.data.sync.AccountBoundary.AfterSignIn.ConfirmSwitch -> _switchPrompt.value = true
            else -> continueAfterSignIn()
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

    // Lucento Shared state, declared before init: its coroutine can reach it before the constructor finishes.

    private val _signOutNotice = MutableStateFlow<com.lucento.app.feature.account.SignOutNotice?>(null)

    /**
     * After a sign-out or an account deletion brought this phone back to setup: one line on the welcome screen, so the
     * person knows what happened. Read (and cleared) once when onboarding starts.
     */
    val signOutNotice: StateFlow<com.lucento.app.feature.account.SignOutNotice?> = _signOutNotice

    /** Why a Shared Space just left this phone (left, or no longer shared); shown once over onboarding. */
    val spaceNotice: StateFlow<com.lucento.app.data.sharedspace.SpaceNotice?> =
        (space?.notice ?: kotlinx.coroutines.flow.flowOf(null)).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _invitationOffer = MutableStateFlow<com.lucento.app.data.sharedspace.ReceivedInvitation?>(null)
    /**
     * An invitation waiting for an account that has nothing of its own yet, offered before the person sets anything up.
     * Joining makes this phone the Shared Space's; "Set up my own" continues the normal setup.
     */
    val invitationOffer: StateFlow<com.lucento.app.data.sharedspace.ReceivedInvitation?> = _invitationOffer

    private val _joiningSpace = MutableStateFlow(false)
    val joiningSpace: StateFlow<Boolean> = _joiningSpace

    private val _offerBusy = MutableStateFlow(false)
    val offerBusy: StateFlow<Boolean> = _offerBusy

    private val _offerMessage = MutableStateFlow<String?>(null)
    val offerMessage: StateFlow<String?> = _offerMessage

    /** The Owner's name while this phone holds their Shared Space (the restore is then downloading it); else null. */
    val spaceOwner: StateFlow<String?> = (space?.access?.map { if (it.isPartner) it.ownerDisplay else null } ?: kotlinx.coroutines.flow.flowOf(null))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** "Your name, as Alex will see it": the profile name, else the email's first part. */
    val spaceNameSuggestion: StateFlow<String> = combine(preferences.preferences.map { it.displayName }, signedInEmail) { name, email ->
        name?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@').orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            val key = try {
                preferences.takeSignOutNotice()
            } catch (e: java.io.IOException) {
                null
            }
            _signOutNotice.value = com.lucento.app.feature.account.SignOutNotice.fromKey(key)
        }
        // The app was closed mid-download: pick it up again rather than leave the spinner with nothing behind it.
        if (savedState.get<String>(KEY_RESTORE) == RestoreStage.RESTORING.name) {
            runRestore()
        } else {
            // Already signed in from an earlier, unfinished setup (the app was closed, or restarted after a crash):
            // a returning account is recognised now rather than set up again (P0-2, scenario D). A failed check says
            // nothing here; finishing checks again. After a Shared Space left this phone, the notice is read first.
            viewModelScope.launch {
                refreshHoldsAccountData()
                if (space?.pendingNotice() != null) return@launch
                if (auth?.currentUserId() != null && restoreStage.value == RestoreStage.NONE) checkAccount(quiet = true)
            }
        }
    }

    // ---------------------------------------------------------------- Lucento Shared

    /** The notice was read: the person's own account is checked next, as for any returning account. */
    fun acknowledgeSpaceNotice() {
        viewModelScope.launch {
            space?.takeNotice()
            if (auth?.currentUserId() != null && restoreStage.value == RestoreStage.NONE) checkAccount(quiet = true)
        }
    }

    /**
     * True when this phone now holds the Shared Space the signed-in account is a Partner in (see
     * [com.lucento.app.data.sharedspace.SharedSpaceRepository.adoptMembership]). Any failure means "carry on as before".
     */
    private suspend fun adoptSpace(): Boolean = try {
        space?.adoptMembership() == true
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("Lucento", "Checking Lucento Shared during onboarding failed", e)
        false
    }

    private suspend fun offerInvitation() {
        _offerMessage.value = null
        _invitationOffer.value = try {
            space?.invitationToOffer()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** "Set up my own Lucento": the invitation stays in Settings › Lucento Shared (and off Home) until it expires. */
    fun setInvitationAside() {
        val invitation = _invitationOffer.value ?: return
        if (_joiningSpace.value) return
        _invitationOffer.value = null
        _offerMessage.value = null
        viewModelScope.launch { space?.dismiss(invitation.id) }
    }

    fun declineInvitation() {
        val repository = space ?: return
        val invitation = _invitationOffer.value ?: return
        if (_joiningSpace.value || _offerBusy.value) return
        _offerBusy.value = true
        viewModelScope.launch {
            try {
                when (val result = repository.decline(invitation.id)) {
                    com.lucento.app.data.sharedspace.SpaceActionResult.Done -> {
                        _invitationOffer.value = null
                        _offerMessage.value = null
                    }
                    is com.lucento.app.data.sharedspace.SpaceActionResult.Failed ->
                        _offerMessage.value = com.lucento.app.data.sharedspace.sharedSpaceMessage(result.code, invitation.inviterName)
                }
            } finally {
                _offerBusy.value = false
            }
        }
    }

    /**
     * Joins the offered Shared Space: the server accepts, this phone becomes the space's and downloads it, and onboarding
     * finishes the way a restore does — nothing of the person's own is set up.
     */
    fun joinInvitation(name: String) {
        val repository = space ?: return
        val invitation = _invitationOffer.value ?: return
        if (_joiningSpace.value) return
        _joiningSpace.value = true
        _offerMessage.value = null
        viewModelScope.launch {
            try {
                when (val result = repository.join(invitation, name.ifBlank { spaceNameSuggestion.value })) {
                    is com.lucento.app.data.sharedspace.JoinResult.Joined -> {
                        _invitationOffer.value = null
                        savedState[KEY_WELCOME_BACK] = false
                        restore(membershipChecked = true)
                    }
                    is com.lucento.app.data.sharedspace.JoinResult.Failed ->
                        _offerMessage.value = com.lucento.app.data.sharedspace.sharedSpaceMessage(result.code, invitation.inviterName)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Lucento", "Joining a Shared Space during onboarding failed", e)
                _offerMessage.value = "Couldn’t join right now. Check your connection and try again."
            } finally {
                _joiningSpace.value = false
            }
        }
    }

    /** After a sign-in on either path: restoring pulls right away; the setup path checks the account first. */
    private suspend fun continueAfterSignIn() {
        if (restoreStage.value == RestoreStage.SIGN_IN) {
            // Restoring: this phone has nothing of its own yet, so the account can be pulled right away. The plan is asked
            // inside restore(), after the account-boundary check and the Shared Space check (a Partner never needs one).
            syncPreferences?.setEnabled(true)
            runRestore()
        } else {
            // A new account on the setup path: "Email verified" / "You're connected", then on to personalizing. An
            // account that turns out to hold data goes to its restore instead, and an invitation waiting for it is
            // offered first — neither needs the dialog.
            val verified = verification.state.value as? com.lucento.app.feature.account.EmailVerification.State.Verified
            val viaEmail = verified != null
            val email = verified?.email ?: signedInEmail.value
                ?: kotlinx.coroutines.withTimeoutOrNull(2_000) { auth?.observeSignedInEmail()?.first { it != null } }.orEmpty()
            if (step.value == STEP_SYNC) _accountReady.value = com.lucento.app.feature.account.AccountReady(email, viaEmail, settling = true)
            val route = checkAccount(quiet = false)
            _accountReady.value = if (route == SetupRoute.NEW_SETUP && _invitationOffer.value == null && step.value == STEP_SYNC) {
                com.lucento.app.feature.account.AccountReady(email, viaEmail, settling = false)
            } else {
                null
            }
        }
    }

    /**
     * Asks whether the connected account already holds data and acts on the answer ([setupRoute]): a returning account
     * goes to the restore ("Welcome back"); an empty one is remembered as new; a failed check shows Try again unless
     * [quiet]. Writes nothing to the ledger — except that an account that is a Lucento Shared Partner gets its Shared
     * Space on this phone (an empty ledger bound to it), which the restore then downloads. An account with nothing of its
     * own is offered a waiting invitation when [offerInvitation].
     */
    private suspend fun checkAccount(quiet: Boolean, offerInvitation: Boolean = true): SetupRoute? {
        val repository = sync ?: return null
        if (!quiet) _accountCheck.value = AccountCheck.Checking
        if (adoptSpace()) {
            _accountCheck.value = AccountCheck.Idle
            savedState[KEY_WELCOME_BACK] = true
            syncPreferences?.setEnabled(true)
            restore(membershipChecked = true)
            return SetupRoute.RESTORE
        }
        val probe = repository.probeAccount()
        val route = setupRoute(probe, savedState[KEY_NEW_FOR])
        when (route) {
            SetupRoute.RESTORE -> {
                _accountCheck.value = AccountCheck.Idle
                savedState[KEY_WELCOME_BACK] = true
                syncPreferences?.setEnabled(true)
                restore(membershipChecked = true)
            }
            SetupRoute.NEW_SETUP -> {
                _accountCheck.value = AccountCheck.Idle
                (probe as? com.lucento.app.data.sync.AccountProbe.Empty)?.let { savedState[KEY_NEW_FOR] = it.userId }
                if (offerInvitation) offerInvitation()
            }
            SetupRoute.RETRY -> if (!quiet) {
                refreshHoldsAccountData()
                _accountCheck.value = AccountCheck.Unavailable
            }
            SetupRoute.LOCAL -> _accountCheck.value = AccountCheck.Idle
        }
        return route
    }

    /** Try again after the account couldn't be checked. */
    fun retryAccountCheck() {
        if (_accountCheck.value == AccountCheck.Checking) return
        viewModelScope.launch { checkAccount(quiet = false) }
    }

    private suspend fun refreshHoldsAccountData() {
        _holdsAccountData.value = syncPreferences?.syncedUserId() != null
    }

    /**
     * The way out when the account can't be checked or brought across (P0-2: never stuck in onboarding): disconnect it
     * and set Lucento up on this phone only; sync can be turned on later in Settings. Nothing of the user's own is on
     * this phone yet — onboarding hasn't finished — and nothing was uploaded. When a restore had already brought part of
     * the account here, that partial copy is removed (the account keeps everything) through the ordinary sign-out wipe,
     * so it is never mixed into a new setup; the screen confirms this first.
     */
    fun leaveAccount() {
        if (restoring || restoreStage.value == RestoreStage.RESTORING) return
        viewModelScope.launch {
            try {
                // A phone bound to a Shared Space (even before its first download) goes through the wipe too, which
                // drops that binding: a setup on this phone alone must not stay tied to someone else's space.
                if (syncPreferences?.syncedUserId() != null || syncPreferences?.sharedSpaceOwnerId() != null) {
                    accounts?.signOutAndWipe()
                } else {
                    auth?.signOut()
                    auth?.revokeOnServer()
                }
                syncPreferences?.setEnabled(false)
                _accountCheck.value = AccountCheck.Idle
                savedState[KEY_WELCOME_BACK] = false
                savedState.remove<String>(KEY_NEW_FOR)
                _restoreMessage.value = null
                setRestoreStage(RestoreStage.NONE)
                _syncMessage.value = "Not connected. Your account keeps everything — sign in again any time, here or in " +
                    "Settings, to bring it to this phone."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Lucento", "Leaving the account during onboarding failed", e)
                _syncMessage.value = "Couldn’t disconnect the account. Try again."
            } finally {
                refreshHoldsAccountData()
            }
        }
    }

    private fun setRestoreStage(stage: RestoreStage) {
        savedState[KEY_RESTORE] = stage.name
    }

    /** Drops unsynced changes and the previous ledger, then continues with the account just signed in. */
    fun confirmSwitch() {
        viewModelScope.launch {
            accounts?.confirmSwitch()
            _switchPrompt.value = false
            continueAfterSignIn()
        }
    }

    /** Keeps the previous ledger and leaves the new session. */
    fun cancelSwitch() {
        viewModelScope.launch {
            accounts?.cancelSwitch()
            _switchPrompt.value = false
        }
    }

    /**
     * "I already have Lucento": straight to choosing the account (owner, 2026-11-06); the reminder to sync the old phone
     * first is a note on that screen. Already signed in, it goes on to the download.
     */
    fun startRestore() {
        if (!syncAvailable) return
        _syncMessage.value = null
        savedState[KEY_INVITED] = false
        continueRestore()
    }

    /**
     * Lucento Shared, "I was invited to share": choose the account the invitation was sent to. After signing in the
     * account is checked like any other — a Partner already gets the space; otherwise its invitation is offered (an
     * account with data of its own restores first, and Home offers it).
     */
    fun startInvited() {
        if (!syncAvailable) return
        _syncMessage.value = null
        savedState[KEY_INVITED] = true
        continueRestore()
    }

    /** Past the reminder: sign in (or, already signed in, go straight to the download). */
    fun continueRestore() {
        if (signedInEmail.value != null) runRestore() else setRestoreStage(RestoreStage.SIGN_IN)
    }

    /** Back out of restoring to the welcome screen; nothing was written. */
    fun cancelRestore() {
        if (restoreStage.value == RestoreStage.RESTORING) return
        _restoreMessage.value = null
        savedState[KEY_WELCOME_BACK] = false
        savedState[KEY_INVITED] = false
        setRestoreStage(RestoreStage.NONE)
    }

    /**
     * The account had nothing to bring back (or the user chose to): continue with the normal setup from the first
     * question. A connected account stays connected, so the sync step shows it and syncing starts when setup finishes.
     */
    fun setUpAsNew() {
        _restoreMessage.value = null
        savedState[KEY_WELCOME_BACK] = false
        setRestoreStage(RestoreStage.NONE)
        moveTo(STEP_FOCUS)
        // The account was downloaded and had nothing to bring back: don't send it to the restore again when finishing.
        viewModelScope.launch { auth?.currentUserId()?.let { savedState[KEY_NEW_FOR] = it } }
    }

    /**
     * Pulls the account with a full sync (this phone holds only the seeded income categories, which merge with the
     * account's own), then finishes onboarding without the story or guided setup — or, for an empty account, moves to
     * [RestoreStage.EMPTY]. Nothing is marked complete unless the download succeeded.
     *
     * Lucento Shared: an account that is a Partner restores its Shared Space instead, and always finishes — an empty
     * space is still the person's to use, and only its Owner sets it up.
     */
    fun runRestore() = restore(membershipChecked = false)

    /**
     * Lucento Pro: a returning account restores from the cloud only on a plan that includes it. Otherwise the person is
     * told, stays signed in, and can restore a backup file (free) or start fresh; nothing in the account is touched.
     *
     * The account is checked first (read-only): one with nothing saved has nothing that needs Pro, so it gets the same
     * page as an empty download — set up as new and stay signed in — instead of the plan page (owner test round 3: a
     * Free Partner invited to a Shared Space went round in a loop between the plan page and the welcome screen). A
     * waiting invitation is offered either way; joining one uses the Owner's plan. A check that can't finish is never
     * treated as "empty".
     */
    private suspend fun cloudAllowedForRestore(repository: com.lucento.app.data.sync.SyncRepository): Boolean {
        val allowed = planAllowsCloud ?: return true
        if (allowed()) return true
        _restoreMessage.value = null
        val probe = repository.probeAccount()
        (probe as? com.lucento.app.data.sync.AccountProbe.Empty)?.let { savedState[KEY_NEW_FOR] = it.userId }
        setRestoreStage(restoreStageWithoutCloud(probe))
        offerInvitation()
        return false
    }

    /**
     * Back from the Lucento Plan page opened on [RestoreStage.PLAN_NEEDED]: if the plan now includes restoring (bought
     * or restored a purchase), the restore carries on by itself; otherwise the page stays as it was.
     */
    fun planClosed() {
        if (restoring || restoreStage.value != RestoreStage.PLAN_NEEDED) return
        viewModelScope.launch {
            if (planAllowsCloud?.invoke() != false) runRestore()
        }
    }

    /** [runRestore]; [membershipChecked] when the Lucento Shared check already ran in this attempt. */
    private fun restore(membershipChecked: Boolean) {
        val repository = sync ?: return
        if (restoring) return
        restoring = true
        _restoreMessage.value = null
        _invitationOffer.value = null
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
                if (!membershipChecked) adoptSpace()
                // Lucento Pro: after the boundary check (never before — adopting a space replaces this phone's ledger) and
                // after the Shared check: a Partner restores on the Owner's plan; anyone else needs cloud restore.
                val partnerOfSpace = syncPreferences?.sharedSpaceOwnerId() != null
                if (!partnerOfSpace && !cloudAllowedForRestore(repository)) {
                    syncPreferences?.setEnabled(false)
                    restoring = false
                    return@launch
                }
                when (val result = repository.sync()) {
                    // UploadsPaused: the download finished; only this phone's uploads wait for the plan (security audit
                    // F-01), so the restore is complete either way.
                    is com.lucento.app.data.sync.SyncResult.Success, is com.lucento.app.data.sync.SyncResult.UploadsPaused -> {
                        val partner = syncPreferences?.sharedSpaceOwnerId() != null
                        val restoredWallets = wallets?.observeWallets()?.first().orEmpty()
                        val restoredCategories = categories.observeCategories().first()
                        // Same rule as the check before onboarding (P0-2.1): archived wallets, transactions, schedules
                        // and budgets count too, so a "Welcome back" never ends in "nothing to restore".
                        val otherRecords = repository.localHasRestorableRecords()
                        if (partner || com.lucento.app.domain.RestorePlan.hasData(restoredWallets, restoredCategories, otherRecords)) {
                            preferences.completeRestore(
                                primaryCurrency = com.lucento.app.domain.RestorePlan.primaryCurrency(restoredWallets, currency.value),
                                themeMode = theme.value,
                                features = com.lucento.app.domain.RestorePlan.features,
                                categoriesChosen = partner || com.lucento.app.domain.RestorePlan.hasSpendingCategories(restoredCategories),
                            )
                            appContext?.let { com.lucento.app.data.sync.SyncScheduler.ensurePeriodic(it) }
                        } else {
                            setRestoreStage(RestoreStage.EMPTY)
                            offerInvitation()
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
                        // The technical reason is kept in sync status for diagnosis; the screen says what to do.
                        _restoreMessage.value = "Check your connection and try again."
                        setRestoreStage(RestoreStage.FAILED)
                    }
                    is com.lucento.app.data.sync.SyncResult.MembershipRevoked -> {
                        // Lucento Shared: the space this phone was joining no longer includes this account.
                        _restoreMessage.value = "That Shared Space isn’t shared with this account any more. Try again to restore your own data."
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
                refreshHoldsAccountData()
            }
        }
    }

    val step: StateFlow<Int> = savedState.getStateFlow(KEY_STEP, 0)

    /**
     * The furthest step reached in this run, so the stepper keeps a finished stage finished when the user goes back to
     * an earlier screen. Saved with the step (it survives process death too).
     */
    val furthestStep: StateFlow<Int> = savedState.getStateFlow(KEY_FURTHEST, 0)

    /** Every step change goes through here: the step, and the furthest step reached when it moves past it. */
    private fun moveTo(target: Int) {
        savedState[KEY_STEP] = target
        if (target > furthestStep.value) savedState[KEY_FURTHEST] = target
    }
    val currency: StateFlow<String> =
        savedState.getStateFlow(KEY_CURRENCY, UserPreferencesRepository.defaultCurrency())

    /** Always includes [FinancialFocus.MANDATORY]: tracking spending and budgeting can't be switched off here. */
    val focuses: StateFlow<Set<FinancialFocus>> = savedState.getStateFlow(KEY_FOCUS, ArrayList(FinancialFocus.MANDATORY.map { it.name }))
        .map { names -> names.mapNotNull { n -> FinancialFocus.entries.firstOrNull { it.name == n } }.toSet() + FinancialFocus.MANDATORY }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FinancialFocus.MANDATORY)

    val spendTypes: StateFlow<Set<com.lucento.app.domain.SpendType>> =
        savedState.getStateFlow(KEY_SPEND, ArrayList(com.lucento.app.domain.SpendType.DEFAULT.map { it.name }))
            .map { com.lucento.app.domain.SpendType.fromKeys(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.lucento.app.domain.SpendType.DEFAULT)

    /**
     * The choice on "Keep your money safe": sync with an account, or this phone only. Nothing is chosen until the person
     * picks one; each choice then shows what comes next (Google or email; Continue).
     */
    val backupPath: StateFlow<BackupPath?> = savedState.getStateFlow<String?>(KEY_BACKUP_PATH, null)
        .map { name -> name?.let { runCatching { BackupPath.valueOf(it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectBackupPath(path: BackupPath) {
        // A message from an attempt to connect (e.g. the email couldn't be sent) belongs to that attempt, not the new choice.
        if (path != backupPath.value) _syncMessage.value = null
        savedState[KEY_BACKUP_PATH] = path.name
    }

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
        // Leaving the focus or payment step: modules follow what the user cares about and how they pay.
        if (current == STEP_FOCUS || current == STEP_SPEND) {
            val modules = FinancialFocus.initialFeatures(focuses.value) + com.lucento.app.domain.SpendType.features(spendTypes.value)
            savedState[KEY_FEATURES] = ArrayList(modules.map { it.key })
        }
        // Category selection is mandatory: the review step needs at least one category.
        if ((current == STEP_CATEGORIES || current == STEP_REVIEW) && picks.value.count == 0) return
        moveTo((current + 1).coerceAtMost(LAST_STEP))
    }

    fun goTo(target: Int) {
        if (target in 0..LAST_STEP && target <= step.value) moveTo(target)
    }

    fun setPicks(value: CategoryPicks) {
        savedState[KEY_PRESETS] = ArrayList(value.presetKeys.toList())
        savedState[KEY_CUSTOM] = ArrayList(value.customNames)
    }

    fun back() {
        moveTo((step.value - 1).coerceAtLeast(0))
    }

    fun toggleFocus(focus: FinancialFocus) {
        if (focus.mandatory) return
        val current = focuses.value
        val next = if (focus in current) current - focus else current + focus
        savedState[KEY_FOCUS] = ArrayList(next.map { it.name })
    }

    fun toggleSpendType(type: com.lucento.app.domain.SpendType) {
        val current = spendTypes.value
        savedState[KEY_SPEND] = ArrayList((if (type in current) current - type else current + type).map { it.name })
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
                // P0-2: a connected account is checked before anything is set up. One that already has data is restored
                // instead (no second set of categories, wallets or budgets); one that can't be checked stops here, with
                // nothing written and onboarding not complete, and the sync step offers Try again.
                val userId = auth?.currentUserId()
                if (userId == null && (syncPreferences?.syncedUserId() != null || syncPreferences?.sharedSpaceOwnerId() != null)) {
                    // An interrupted restore left part of an account here and the session has since ended: never finish
                    // a new setup on top of it. Sign in to complete the restore, or leave the account (removes the copy).
                    finishing = false
                    refreshHoldsAccountData()
                    _restoreMessage.value = "Sign in again to finish bringing your data across — or use Lucento without " +
                        "this account."
                    setRestoreStage(RestoreStage.SIGN_IN)
                    return@launch
                }
                if (userId != null && syncAvailable && savedState.get<String>(KEY_NEW_FOR) != userId) {
                    when (checkAccount(quiet = true, offerInvitation = false)) {
                        SetupRoute.RESTORE -> {
                            finishing = false
                            return@launch
                        }
                        SetupRoute.RETRY -> {
                            // Said on the summary itself (Try again, or finish without syncing): the account step is
                            // now at the start, and sending the person back through every question would be a detour.
                            finishing = false
                            refreshHoldsAccountData()
                            _accountCheck.value = AccountCheck.Unavailable
                            return@launch
                        }
                        SetupRoute.NEW_SETUP, SetupRoute.LOCAL, null -> Unit
                    }
                }
                // Existing rows (only income categories on a fresh install) are matched by name, never duplicated, and a
                // category that came from the account is never removed for being unchosen.
                categories.applySelection(picks.value.choices(emptyList()), keepAccountCategories = true)
                preferences.completeOnboarding(currency.value, theme.value, features.value, spendTypes.value)
                // Connected on the sync step: sync is turned on only now that the user's own setup is saved.
                val context = appContext
                if (context != null && syncAvailable && auth?.currentUserId() != null && (planAllowsCloud?.invoke() ?: true)) {
                    syncPreferences?.setEnabled(true)
                    com.lucento.app.data.sync.SyncScheduler.ensurePeriodic(context)
                    com.lucento.app.data.sync.SyncScheduler.enqueueAfterLocalWrite(context)
                } else {
                    // Not connected: clear any switch left on by an earlier restore attempt.
                    syncPreferences?.setEnabled(false)
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
     * Skips the optional questions (focus and payment types) with safe defaults — every module on — and continues to
     * the spending categories, which are never skipped.
     */
    fun skip() {
        if (step.value >= STEP_CATEGORIES) return
        savedState[KEY_FEATURES] = ArrayList(Feature.entries.map { it.key })
        moveTo(STEP_CATEGORIES)
    }

    // ---------------------------------------------------------------- restoring a backup file (offline)

    private val _filePreview = MutableStateFlow<com.lucento.app.domain.backup.BackupPreview?>(null)
    /** What the chosen backup file holds; shown for confirmation before anything is written. */
    val filePreview: StateFlow<com.lucento.app.domain.backup.BackupPreview?> = _filePreview

    private val _fileMessage = MutableStateFlow<String?>(null)
    /** Why a backup file couldn't be read or restored. Nothing was changed when this is set. */
    val fileMessage: StateFlow<String?> = _fileMessage

    private val _fileBusy = MutableStateFlow(false)
    val fileBusy: StateFlow<Boolean> = _fileBusy

    private var pendingFile: String? = null

    /** Reads a backup file and previews it. A file that isn't a valid Lucento backup changes nothing. */
    fun inspectBackupFile(read: suspend () -> String) {
        val engine = backup ?: return
        if (_fileBusy.value) return
        _fileBusy.value = true
        _fileMessage.value = null
        viewModelScope.launch {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { read() }
                _filePreview.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { engine.preview(text) }
                pendingFile = text
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.lucento.app.domain.backup.BackupFormatException) {
                pendingFile = null
                _fileMessage.value = e.message ?: "This file isn’t a valid Lucento backup. Nothing was changed."
            } catch (e: Exception) {
                pendingFile = null
                _fileMessage.value = "Couldn’t read that file. Nothing was changed."
            } finally {
                _fileBusy.value = false
            }
        }
    }

    fun cancelBackupFile() {
        pendingFile = null
        _filePreview.value = null
    }

    /**
     * Restores the previewed file onto this new phone (replacing the seeded starter rows) and opens Lucento with it — no
     * story, no guided setup, nothing uploaded (sync stays off until the user connects an account in Settings). The
     * backup's own settings come with it; if it predates finishing onboarding, onboarding is finished here.
     */
    fun restoreBackupFile() {
        val engine = backup ?: return
        val json = pendingFile ?: return
        if (_fileBusy.value) return
        _fileBusy.value = true
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    engine.restore(json, com.lucento.app.domain.backup.RestoreMode.REPLACE)
                }
                pendingFile = null
                _filePreview.value = null
                syncPreferences?.setEnabled(false)
                val restored = preferences.preferences.first()
                if (!restored.onboardingComplete) {
                    val restoredWallets = wallets?.observeWallets()?.first().orEmpty()
                    val restoredCategories = categories.observeCategories().first()
                    preferences.completeRestore(
                        primaryCurrency = com.lucento.app.domain.RestorePlan.primaryCurrency(restoredWallets, restored.primaryCurrency),
                        themeMode = restored.themeMode,
                        features = com.lucento.app.domain.RestorePlan.features,
                        categoriesChosen = com.lucento.app.domain.RestorePlan.hasSpendingCategories(restoredCategories),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: com.lucento.app.domain.backup.BackupFormatException) {
                _fileMessage.value = e.message ?: "This file isn’t a valid Lucento backup. Nothing was changed."
            } catch (e: Exception) {
                android.util.Log.w("Lucento", "Restoring a backup file during onboarding failed", e)
                _fileMessage.value = "Couldn’t finish restoring. Nothing was changed — try again."
            } finally {
                _fileBusy.value = false
            }
        }
    }

    val fileRestoreAvailable: Boolean get() = backup != null

    companion object {
        const val STEP_WELCOME = 0
        /**
         * "Keep your money safe" comes right after Get started (owner, 2026-11-06): a new person decides first whether
         * Lucento syncs with an account — and creates it — then personalizes. Usage step numbers keep their old meaning
         * ([onboardingStepNumber]).
         */
        const val STEP_SYNC = 1
        const val STEP_FOCUS = 2
        /** "How do you pay?" (replaced the feature switches). */
        const val STEP_SPEND = 3
        const val STEP_CATEGORIES = 4
        const val STEP_REVIEW = 5
        const val STEP_ESSENTIALS = 6
        const val STEP_CONFIRM = 7
        const val LAST_STEP = STEP_CONFIRM
        private const val KEY_STEP = "step"
        private const val KEY_FURTHEST = "furthest_step"
        private const val KEY_RESTORE = "restore_stage"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_FOCUS = "focus"
        private const val KEY_FEATURES = "features"
        private const val KEY_SPEND = "spend_types"
        private const val KEY_BACKUP_PATH = "backup_path"
        private const val KEY_THEME = "theme"
        private const val KEY_PRESETS = "category_presets"
        private const val KEY_CUSTOM = "category_custom"
        private const val KEY_WELCOME_BACK = "welcome_back"
        private const val KEY_INVITED = "joining_by_invitation"
        /** The account the user chose to set up as new after its restore found nothing (see [setupRoute]). */
        private const val KEY_NEW_FOR = "new_setup_for"
        /** The address of a new email account waiting to be verified (never its password). */
        private const val KEY_VERIFY_EMAIL = "verify_email"

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                OnboardingViewModel(
                    createSavedStateHandle(), container.preferences, container.categories,
                    auth = container.auth, syncPreferences = container.syncPreferences, appContext = app?.applicationContext,
                    sync = container.syncRepository, wallets = container.wallets,
                    accounts = container.accountBoundary,
                    usage = container.usageCounts,
                    backup = container.backup,
                    space = container.sharedSpace,
                    planAllowsCloud = {
                        // The account just signed in: fetch its rows before deciding (a Partner's binding counts too).
                        runCatching { container.entitlements.refresh() }
                        container.planAllowsSync()
                    },
                    purchaseWithoutAccount = container.entitlements.boughtWithoutAccount,
                )
            }
        }
    }
}

/**
 * The choice on "Keep your money safe". Only [ACCOUNT] sends anything, and only after sign-in. A backup file is no
 * longer a separate choice: anyone can export one from Settings › Your data, whichever they pick.
 */
enum class BackupPath { ACCOUNT, DEVICE_ONLY }

/**
 * Which of the visible stages an onboarding screen belongs to. The welcome screen and "Keep your money safe" show none:
 * they are how a person gets in, before the setup proper begins.
 */
internal fun onboardingStage(step: Int): com.lucento.app.domain.SetupStage? = when (step) {
    OnboardingViewModel.STEP_WELCOME, OnboardingViewModel.STEP_SYNC -> null
    in OnboardingViewModel.STEP_FOCUS..OnboardingViewModel.STEP_ESSENTIALS -> com.lucento.app.domain.SetupStage.PERSONALIZE
    else -> com.lucento.app.domain.SetupStage.SET_UP
}

/**
 * The 1-based screen number sent with `onboarding_step_viewed`. The numbers name screens, not positions, so counts stay
 * comparable after "Keep your money safe" moved to second place: welcome 1, focus 2, payments 3, categories 4, review 5,
 * currency and look 6, keep your money safe 7, summary 8.
 */
internal fun onboardingStepNumber(step: Int): Int = when (step.coerceIn(0, OnboardingViewModel.LAST_STEP)) {
    OnboardingViewModel.STEP_WELCOME -> 1
    OnboardingViewModel.STEP_FOCUS -> 2
    OnboardingViewModel.STEP_SPEND -> 3
    OnboardingViewModel.STEP_CATEGORIES -> 4
    OnboardingViewModel.STEP_REVIEW -> 5
    OnboardingViewModel.STEP_ESSENTIALS -> 6
    OnboardingViewModel.STEP_SYNC -> 7
    else -> 8
}

