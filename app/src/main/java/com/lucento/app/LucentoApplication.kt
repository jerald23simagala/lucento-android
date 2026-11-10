package com.lucento.app

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.withTransaction
import com.lucento.app.data.backup.RoomBackupStore
import com.lucento.app.data.local.LucentoDatabase
import com.lucento.app.data.preferences.SharedPreferencesCrashReportsStore
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.EntitlementGate
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.TransactionRunner
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.core.crash.CrashReporting
import com.lucento.app.core.network.observeOnline
import com.lucento.app.core.crash.CrashReportsFlag
import com.lucento.app.data.local.LocalLedgerReset
import com.lucento.app.data.media.GoalPhotos
import com.lucento.app.core.network.isOnlineNow
import com.lucento.app.data.sync.AccountBoundary
import com.lucento.app.data.sync.AuthRepository
import com.lucento.app.data.sync.SupabaseModule
import com.lucento.app.data.sync.SupabaseSyncRemote
import com.lucento.app.data.sync.SyncFlight
import com.lucento.app.data.sync.SyncPreferences
import com.lucento.app.data.sync.SyncRepository
import com.lucento.app.data.sync.SyncScheduler
import com.lucento.app.data.sync.shouldSyncOnLaunch
import com.lucento.app.data.sync.backgroundSyncAllowed
import com.lucento.app.data.usage.SupabaseUsageRemote
import com.lucento.app.domain.UsageCountsService
import com.lucento.app.domain.UsageEventDeleter
import com.lucento.app.domain.UsageEventSender
import com.lucento.app.domain.backup.BackupEngine
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.lucento.app.data.sync.uploadRequests

class LucentoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The manifest keeps collection off until this call. The flag is synchronous, so an opted-in user is
        // enabled before the rest of startup. DataStore is read only when the flag has never been written.
        CrashReporting.install(storedCrashReports())
        container = AppContainer(this)
        // QA R9-02: finish an interrupted sign-out wipe before any screen can read (and show) the old ledger.
        finishInterruptedWipeBeforeUi(container.ledgerReset::isPending) {
            SyncFlight.exclusive { container.ledgerReset.resumeIfInterrupted() }
        }
        // Safe even when cloud sync has never been turned on: ensurePeriodic() only (re-)schedules work that
        // SyncWorker itself immediately no-ops out of unless the preference is on (see SyncWorker.doWork).
        // Sessions a sign-out ended but the server hasn't confirmed revoking (offline, killed): retried by RevokeWorker.
        if (container.pendingRevokes.all().isNotEmpty()) com.lucento.app.data.sync.RevokeScheduler.schedule(this)
        // The welcome email (docs/EMAILS.md): asked for once per signed-in account; nothing while signed out.
        container.welcomeEmail.start(CoroutineScope(SupervisorJob() + Dispatchers.Default), container.auth.observeUserId())
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            container.usageCounts.retryPendingDeletion()
            observeOnline().collect { online ->
                if (online) container.usageCounts.retryPendingDeletion()
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // Every new, edited or deleted row is uploaded shortly after it is saved, not only on "Sync now".
            uploadRequests(container.syncRepository.observePendingChanges()).collect {
                if (backgroundSyncAllowed(container.syncPreferences.isEnabled(), container.onboardingComplete(), container.planAllowsSync())) {
                    // Open: send it now. In the background (or if the app is closed before it runs): WorkManager.
                    if (container.inForeground.get()) container.foregroundSync.request(forced = true)
                    else SyncScheduler.enqueueAfterLocalWrite(this@LucentoApplication)
                }
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // A different account already signed in, with nothing unsynced, is wiped before the next sync
            // so this phone never uploads the previous ledger. Unsynced changes wait for a confirmation in Settings.
            // A sign-out wipe killed halfway is finished first (QA O-1).
            // Normally already finished synchronously in onCreate (see finishInterruptedWipeBeforeUi); this is the
            // fallback if that attempt threw.
            try {
                com.lucento.app.data.sync.SyncFlight.exclusive { container.ledgerReset.resumeIfInterrupted() }
            } catch (e: com.lucento.app.data.sync.SyncFlight.Busy) {
                return@launch // another process is mid-sync; the next launch finishes the wipe
            }
            val boundary = container.accountBoundary.afterSignIn()
            // An unfinished onboarding never syncs from here (P0-2): it checks the account itself and turns sync on
            // when it finishes. The account-boundary wipe above still applies.
            if (!container.onboardingComplete()) return@launch
            when (boundary) {
                AccountBoundary.AfterSignIn.Wiped -> {
                    container.syncPreferences.setEnabled(true)
                    SyncScheduler.ensurePeriodic(this@LucentoApplication)
                    container.syncRepository.sync()
                }
                else -> if (container.syncPreferences.isEnabled()) {
                    SyncScheduler.ensurePeriodic(this@LucentoApplication)
                    // ensurePeriodic keeps an existing schedule (KEEP), so a launch alone never pulled (QA R6-11).
                    if (shouldSyncOnLaunch(true, container.auth.currentUserId(), container.planAllowsSync())) SyncScheduler.syncNow(this@LucentoApplication)
                }
            }
        }
    }

    /**
     * Opt-in for this process, read before any other startup work. Missing flag and no DataStore file means off,
     * without opening DataStore. An older install that already chose is copied across once.
     */
    private fun storedCrashReports(): Boolean {
        val store = SharedPreferencesCrashReportsStore(this)
        return CrashReportsFlag.enabledForStartup(store) {
            val file = preferencesDataStoreFile(UserPreferencesRepository.DATA_STORE_NAME)
            if (!file.exists()) {
                false
            } else {
                runBlocking { UserPreferencesRepository(this@LucentoApplication).preferences.first().crashReports }
            }
        }
    }
}

/** Minimal manual dependency container. Swap for Hilt if the graph grows substantially. */
class AppContainer(application: Application) {
    private val database: LucentoDatabase by lazy { LucentoDatabase.build(application) }

    init {
        // Process-wide sync lock. Released by the OS if this process dies mid-sync.
        SyncFlight.lockFile = File(application.filesDir, "sync/sync.lock")
    }

    /** Multi-row writes (transfers, paying a schedule) run inside one Room transaction. */
    private val runner: TransactionRunner by lazy {
        object : TransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = database.withTransaction { block() }
        }
    }

    val preferences: UserPreferencesRepository by lazy { UserPreferencesRepository(application) }

    /** P2-10: whether this process has been unlocked. Process-wide, so rotation never re-locks and a cold start does. */
    val appLock: com.lucento.app.core.security.AppLockSession by lazy { com.lucento.app.core.security.AppLockSession() }

    /** Background and launch sync wait for this (P0-2); onboarding's own restore syncs directly. */
    suspend fun onboardingComplete(): Boolean = preferences.preferences.first().onboardingComplete
    val backup: BackupEngine by lazy {
        BackupEngine(RoomBackupStore(database, runner, preferences, GoalPhotos(application)))
    }
    /**
     * Lucento Shared: a Partner's phone (bound to an Owner's Shared Space) doesn't change the financial structure. Read
     * from the sync binding on every write, so it holds offline and switches the moment the phone joins or leaves.
     */
    private val structureAccess = com.lucento.app.data.repository.StructureAccess { syncPreferences.sharedSpaceOwnerId() == null }

    /**
     * Lucento Pro (docs/MONETIZATION.md §5): the plan this phone holds — the cached snapshot first, then Google Play's
     * reading, then the server's rows (`public.entitlements`, read at start, on every sign-in change and on each return to
     * the foreground). Release builds read Google Play Billing ([purchaseSource]) and confirm each purchase with the server
     * ([com.lucento.app.data.billing.ProxyPurchaseVerifier]); test builds use the fake store and the plan simulator.
     */
    val entitlements: com.lucento.app.data.entitlement.EntitlementManager by lazy {
        val manager = com.lucento.app.data.entitlement.EntitlementManager(
            cache = com.lucento.app.data.entitlement.SealedEntitlementCache(
                store = com.lucento.app.data.entitlement.DataStoreSnapshotStore(application),
                sealer = com.lucento.app.data.entitlement.KeystoreSealer.create(application),
            ),
            purchases = purchaseSource,
            remote = entitlementRemote(),
            verifier = purchaseVerifier(),
            signedInAccountHash = {
                auth.currentUserId()?.let(com.lucento.app.domain.entitlement.PurchaseInterpreter::obfuscatedAccountId)
            },
        )
        manager.observePlayUpdates(entitlementScope)
        entitlementScope.launch { manager.refresh() }
        // A sign-in (or a switch) brings that account's rows; a sign-out leaves the cached snapshot, which Play-sourced
        // rows keep and server rows lose at the next successful fetch.
        entitlementScope.launch {
            auth.observeUserId().distinctUntilChanged().collect { if (it != null) { manager.accountChanged(); manager.refresh() } }
        }
        manager
    }
    private val entitlementScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Test builds' store: what the plan simulator drives. Never constructed in a release build. */
    private val fakePurchases: com.lucento.app.data.billing.FakePurchaseSource by lazy { com.lucento.app.data.billing.FakePurchaseSource() }

    /**
     * Where purchases come from: Google Play in a release built with Play's licence key; nothing in a release without it
     * (it doesn't bill, and never removes a plan); the fake store in test builds (the `.debug` id can never bill).
     */
    val purchaseSource: com.lucento.app.data.billing.PurchaseSource by lazy {
        when {
            BuildConfig.DEBUG -> fakePurchases
            BuildConfig.PLAY_LICENSE_KEY.isBlank() -> com.lucento.app.data.billing.UnavailablePurchaseSource
            else -> com.lucento.app.data.billing.PlayPurchaseSource(application, entitlementScope, BuildConfig.PLAY_LICENSE_KEY)
        }
    }

    /** Asks Lucento's site for the welcome email once per account (docs/EMAILS.md). */
    val welcomeEmail: com.lucento.app.data.account.WelcomeEmailRequester by lazy {
        val endpoint = if (supabaseClient == null) "" else BuildConfig.ACCOUNT_WELCOME_URL
        com.lucento.app.data.account.WelcomeEmailRequester(
            endpoint,
            com.lucento.app.data.account.PrefsWelcomeMemory(application),
            com.lucento.app.data.account.WelcomeEmailRequester.proxyAsk(endpoint, auth::accessToken),
        )
    }

    /** Release builds only: the server checks each purchase with Play and keeps the account's copy. */
    private fun purchaseVerifier(): com.lucento.app.data.billing.PurchaseVerifier? =
        if (BuildConfig.DEBUG || BuildConfig.BILLING_VERIFY_URL.isBlank() || supabaseClient == null) {
            null
        } else {
            com.lucento.app.data.billing.ProxyPurchaseVerifier(BuildConfig.BILLING_VERIFY_URL, BuildConfig.APPLICATION_ID, auth::accessToken)
        }

    /** Test builds only (docs/MONETIZATION.md §5.7): switch plans without Google Play. Null in release builds. */
    val planSimulator: com.lucento.app.data.billing.DebugPlanSimulator? by lazy {
        if (BuildConfig.DEBUG) {
            com.lucento.app.data.billing.DebugPlanSimulator(com.lucento.app.data.billing.PrefsSimulatorStore(application), fakePurchases)
        } else {
            null
        }
    }

    /** The server's rows; while a test build simulates a plan they read as "no rows", so the two never mix. */
    private fun entitlementRemote(): com.lucento.app.data.entitlement.EntitlementRemote? {
        val server = supabaseClient?.let { com.lucento.app.data.entitlement.SupabaseEntitlementRemote(it) }
        val simulator = planSimulator ?: return server
        return com.lucento.app.data.entitlement.EntitlementRemote { if (simulator.ignoresServer) emptyList() else server?.fetch() }
    }

    /** The plan this phone last showed, for the one-time "your plan has ended" notice (device-level, like the cache). */
    val planMemory: com.lucento.app.data.entitlement.PlanMemory by lazy { com.lucento.app.data.entitlement.DataStorePlanMemory(application) }

    /** Phase 2: the repositories ask the plan. The cache is loaded first, so a cold start never answers Free by accident. */
    private val planGate: EntitlementGate = EntitlementGate { feature, used ->
        entitlements.loadCached()
        entitlements.check(feature, used)
    }

    /** Re-reads Play and the server; MainActivity calls it on every return to the foreground. Never blocks the caller. */
    fun refreshEntitlements() {
        entitlementScope.launch { runCatching { entitlements.refresh() } }
    }

    /**
     * Lucento Pro: whether this phone's plan lets it sync (docs/MONETIZATION.md §3.5). A Partner in a Shared Space syncs on
     * the Owner's plan. Read from the cached snapshot, so it holds offline.
     */
    suspend fun planAllowsSync(): Boolean {
        entitlements.loadCached()
        return com.lucento.app.domain.entitlement.ProGates.syncAllowedByPlan(
            entitlements.state.value, partnerOfSpace = syncPreferences.sharedSpaceOwnerId() != null, now = System.currentTimeMillis(),
        )
    }

    /** Multicurrency: the main currency counts as chosen once onboarding is done (the wallet gate and sync read the same). */
    private suspend fun mainCurrencyOrNull(): String? =
        preferences.preferences.first().let { p -> p.primaryCurrency.takeIf { p.onboardingComplete } }

    val wallets: WalletRepository by lazy {
        WalletRepository(
            dao = database.walletDao(),
            transactionDao = database.transactionDao(),
            scheduleCount = { id -> database.recurringDao().countForWallet(id) },
            debtTerms = database.debtTermsDao(),
            runner = runner,
            access = structureAccess,
            gate = planGate,
            mainCurrency = ::mainCurrencyOrNull,
        )
    }
    val institutions: com.lucento.app.core.banks.InstitutionRepository by lazy {
        com.lucento.app.core.banks.InstitutionRepository { application.assets.open("banks/institutions.json").bufferedReader().use { it.readText() } }
    }
    val categories: CategoryRepository by lazy { CategoryRepository(database.categoryDao(), runner = runner, access = structureAccess) }
    val transactions: TransactionRepository by lazy {
        TransactionRepository(
            database.transactionDao(), database.walletDao(), database.categoryDao(), runner = runner,
            shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            actor = { auth.signedInUserIdNow() },
        )
    }
    val budgets: BudgetRepository by lazy { BudgetRepository(database.budgetDao(), database.categoryDao(), runner = runner, access = structureAccess) }
    val goals: GoalRepository by lazy { GoalRepository(database.goalDao(), access = structureAccess, gate = planGate) }
    val recurring: RecurringRepository by lazy {
        RecurringRepository(database.recurringDao(), database.walletDao(), database.categoryDao(), transactions, runner = runner, access = structureAccess)
    }

    // --- optional cloud sync (see docs/SYNC.md); every value here is safe to hold even when unconfigured ---

    /** Sessions ended on this phone that the server hasn't confirmed revoking yet (R10-01). */
    val pendingRevokes: com.lucento.app.data.sync.PendingRevokeStore by lazy {
        com.lucento.app.data.sync.PrefsPendingRevokeStore.forApp(application)
    }
    val revokes: com.lucento.app.data.sync.PendingRevokeDrainer by lazy {
        com.lucento.app.data.sync.PendingRevokeDrainer(pendingRevokes, http = {
            if (SupabaseModule.isConfigured) com.lucento.app.data.sync.SupabaseRevokeHttp(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) else null
        })
    }

    private val sessionStore: com.lucento.app.data.sync.SessionStore? by lazy {
        if (SupabaseModule.isConfigured) {
            com.lucento.app.data.sync.SessionStore.forApp(application, BuildConfig.SUPABASE_URL).also { store ->
                // Before the Auth plugin loads it: a session a killed sign-out had already ended is not restored.
                store.dropIfEnded(pendingRevokes.all())
            }
        } else {
            null
        }
    }
    private val supabaseClient by lazy { SupabaseModule.create(sessionStore) }
    val syncPreferences: SyncPreferences by lazy { SyncPreferences(application) }
    val auth: AuthRepository by lazy { AuthRepository(
            supabaseClient,
            clearStoredSession = { sessionStore?.clearNow() ?: true },
            isOnline = { application.isOnlineNow() },
            pendingRevokes = pendingRevokes,
            drainRevokes = { revokes.drain() },
            scheduleRevoke = { com.lucento.app.data.sync.RevokeScheduler.schedule(application) },
        ) }

    /** The composer's reader: Gemini when Smarter reading is on (see docs/CAPTURE.md), else this phone. */
    val interpreter: com.lucento.app.domain.capture.TransactionInterpreter by lazy {
        com.lucento.app.data.ai.HybridTransactionInterpreter(
            local = com.lucento.app.domain.capture.LocalTransactionInterpreter(),
            remote = com.lucento.app.data.ai.RemoteTransactionInterpreter(accessToken = auth::accessToken),
            enabled = { preferences.preferences.first().smartReading },
            currency = { preferences.preferences.first().primaryCurrency },
        )
    }

    /** Ask Lucento's AI answers: only while the user's consent is on (docs/ASK.md). Reports never need it. */
    val askClient: com.lucento.app.data.ai.AskLucentoClient by lazy {
        com.lucento.app.data.ai.AskLucentoClient(accessToken = auth::accessToken)
    }

    /** Ask Lucento's reports as PDF files, written on the phone. */
    val reportPdf: com.lucento.app.data.reports.ReportPdfWriter by lazy { com.lucento.app.data.reports.ReportPdfWriter(application) }
    val syncRepository: SyncRepository by lazy {
        SyncRepository(
            remote = supabaseClient?.let(::SupabaseSyncRemote),
            wallets = database.walletDao(),
            categories = database.categoryDao(),
            transactions = database.transactionDao(),
            budgets = database.budgetDao(),
            goals = database.goalDao(),
            syncMeta = database.syncDao(),
            preferences = syncPreferences,
            recurring = database.recurringDao(),
            debtTerms = database.debtTermsDao(),
            transact = { block -> runner.run { block() } },
            // Lucento Shared: this phone's Partner membership is gone — the shared ledger leaves the phone.
            onMembershipRevoked = { ownerId -> sharedSpace.endedByOwner(ownerId) },
            // Multicurrency: the main currency counts as chosen once onboarding is done; the ledger's (on the server) wins.
            mainCurrency = {
                preferences.preferences.first().let { p -> p.primaryCurrency.takeIf { p.onboardingComplete } }
            },
            adoptMainCurrency = { code ->
                if (com.lucento.app.core.money.CurrencyRules.isSupported(code)) preferences.setPrimaryCurrency(code)
            },
            // Security audit F-01: the server refuses uploads without the CLOUD plan. The plan this phone holds decides
            // whether a refusal is retried; the refusal itself makes the phone re-read its plan.
            planFingerprint = {
                entitlements.loadCached()
                com.lucento.app.domain.entitlement.ProGates.cloudPlanFingerprint(entitlements.state.value)
            },
            onUploadsRefused = { refreshEntitlements() },
        )
    }

    /** True while Lucento's screen is in front (MainActivity onStart / onStop); syncs then run in the app. */
    val inForeground = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Syncs straight away while the app is open (see [com.lucento.app.data.sync.ForegroundSync]). */
    internal val foregroundSync: com.lucento.app.data.sync.ForegroundSync by lazy {
        com.lucento.app.data.sync.ForegroundSync(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            allowed = { backgroundSyncAllowed(syncPreferences.isEnabled(), onboardingComplete(), planAllowsSync()) },
            upToDate = {
                val last = syncPreferences.lastSyncedAtMillis()
                last != null && System.currentTimeMillis() - last < com.lucento.app.data.sync.ForegroundSync.FRESH_MILLIS &&
                    syncRepository.pendingChangeCount() == 0
            },
            sync = { syncRepository.sync() },
        )
    }

    /** The profile picture (private file; from the photo picker, or the Google account's picture on request). */
    val profilePhotos: com.lucento.app.data.media.ProfilePhotos by lazy { com.lucento.app.data.media.ProfilePhotos(application) }

    /** Multicurrency: the day's exchange rates, fetched only while "Exchange rates online" is on (docs/MULTICURRENCY.md). */
    val fxRates: com.lucento.app.data.fx.FxRateSource by lazy {
        com.lucento.app.data.fx.OnlineFxRates(
            cache = com.lucento.app.data.fx.DataStoreFxRateCache(application),
            enabled = { preferences.preferences.first().fxRatesOnline },
            isOnline = { application.isOnlineNow() },
        )
    }

    val ledgerReset: LocalLedgerReset by lazy {
        val photos = GoalPhotos(application)
        LocalLedgerReset(
            marker = com.lucento.app.data.local.WipeMarker.Prefs(
                application.getSharedPreferences("lucento_ledger_wipe", android.content.Context.MODE_PRIVATE),
            ),
            clearBinding = { syncPreferences.clearBinding() },
            clearTables = { com.lucento.app.data.local.clearLedgerTables(database) },
            deletePhotos = {
                photos.deleteAll()
                profilePhotos.delete()
            },
            accountSetup = preferences,
            clearTablesUnseeded = { com.lucento.app.data.local.clearLedgerTables(database, seed = false) },
            deleteLedgerPhotos = { photos.deleteAll() },
            // Same DataStore file as sharedSpace's store (preferencesDataStore is one instance per process).
            forgetAccount = { com.lucento.app.data.sharedspace.DataStoreSharedSpaceStore(application).clearAll() },
        )
    }

    /** Lucento Shared (docs/shared): one Owner's financial environment shared with a Partner. */
    val sharedSpace: com.lucento.app.data.sharedspace.SharedSpaceRepository by lazy {
        com.lucento.app.data.sharedspace.SharedSpaceRepository(
            remote = supabaseClient?.let { com.lucento.app.data.sharedspace.SupabaseSharedSpaceRemote(it) },
            store = com.lucento.app.data.sharedspace.DataStoreSharedSpaceStore(application),
            partnerOf = syncPreferences.sharedSpaceOwner,
            readPartnerOf = syncPreferences::sharedSpaceOwnerId,
            signedInUserId = auth.observeUserId(),
            transitions = object : com.lucento.app.data.sharedspace.LedgerTransitions {
                override suspend fun joinSpace(ownerId: String) {
                    // The same order as a sign-out wipe: a durable marker first, background sync stopped, then the
                    // ledger replaced under the sync lock. Sync comes back on bound to the space.
                    check(ledgerReset.markPending()) { "Could not record the ledger switch" }
                    SyncScheduler.cancelPeriodic(application)
                    syncPreferences.setEnabled(false)
                    SyncFlight.exclusive { ledgerReset.switchToSharedSpace(bind = { syncPreferences.setSharedSpaceOwnerId(ownerId) }) }
                    syncPreferences.setEnabled(true)
                    SyncScheduler.ensurePeriodic(application)
                }

                override suspend fun returnToOwnLedger() {
                    check(ledgerReset.markPending()) { "Could not record the ledger switch" }
                    SyncScheduler.cancelPeriodic(application)
                    syncPreferences.setEnabled(false)
                    SyncFlight.exclusive { ledgerReset.returnToOwnLedger() }
                }

                override suspend fun sync() = syncRepository.sync()

                override suspend fun hasUnsyncedChanges() = syncRepository.hasUnsyncedChanges()
            },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    private val usageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val usageCounts: UsageCountsService by lazy {
        val remote = supabaseClient?.let(::SupabaseUsageRemote)
        UsageCountsService(
            load = { preferences.readUsageLedger() },
            save = { preferences.writeUsageLedger(it) },
            send = UsageEventSender { id, event, version -> if (remote != null) remote.insert(id, event, version) },
            deleteRemote = UsageEventDeleter { id -> if (remote != null) remote.delete(id) },
            newInstallId = { UUID.randomUUID().toString() },
            now = System::currentTimeMillis,
            appVersion = BuildConfig.VERSION_NAME,
            launchInBackground = { block -> usageScope.launch { block() } },
        )
    }

    /** Sign-out and account switching. Wipes the local ledger before another account can sync. */
    val accountBoundary: AccountBoundary by lazy {
        AccountBoundary(
            markWipePending = ledgerReset::markPending,
            revoke = auth::revokeOnServer,
            currentUserId = auth::currentUserId,
            syncedUserId = syncPreferences::syncedUserId,
            hasUnsyncedChanges = syncRepository::hasUnsyncedChanges,
            prepare = {
                SyncScheduler.cancelPeriodic(application)
                syncPreferences.setEnabled(false)
            },
            wipe = ledgerReset::wipe,
            signOut = auth::signOut,
        )
    }
}

/**
 * QA R9-02: an interrupted sign-out wipe is finished before any Activity (and so any ViewModel or Room flow) starts,
 * instead of racing the first screen that reads the ledger. Blocks onCreate only when the marker is set.
 */
internal fun finishInterruptedWipeBeforeUi(isPending: () -> Boolean, resume: suspend () -> Unit) {
    if (!isPending()) return
    runCatching { runBlocking(kotlinx.coroutines.Dispatchers.IO) { resume() } }
}
