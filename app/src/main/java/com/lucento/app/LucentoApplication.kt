package com.lucento.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.withTransaction
import com.lucento.app.data.local.LucentoDatabase
import com.lucento.app.data.preferences.SharedPreferencesCrashReportsStore
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
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
import com.lucento.app.data.usage.SupabaseUsageRemote
import com.lucento.app.domain.UsageCountsService
import com.lucento.app.domain.UsageEventDeleter
import com.lucento.app.domain.UsageEventSender
import java.util.UUID
import java.io.File
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
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            container.usageCounts.retryPendingDeletion()
            observeOnline().collect { online ->
                if (online) container.usageCounts.retryPendingDeletion()
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // Every new, edited or deleted row is uploaded shortly after it is saved, not only on "Sync now".
            uploadRequests(container.syncRepository.observePendingChanges()).collect {
                if (container.syncPreferences.isEnabled()) SyncScheduler.enqueueAfterLocalWrite(this@LucentoApplication)
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            // A different account already signed in, with nothing unsynced, is wiped before the next sync
            // so this phone never uploads the previous ledger. Unsynced changes wait for a confirmation in Settings.
            // A sign-out wipe killed halfway is finished first (QA O-1).
            // Normally already finished synchronously in onCreate (see finishInterruptedWipeBeforeUi); this is the
            // fallback if that attempt threw.
            com.lucento.app.data.sync.SyncFlight.exclusive { container.ledgerReset.resumeIfInterrupted() }
            when (container.accountBoundary.afterSignIn()) {
                AccountBoundary.AfterSignIn.Wiped -> {
                    container.syncPreferences.setEnabled(true)
                    SyncScheduler.ensurePeriodic(this@LucentoApplication)
                    container.syncRepository.sync()
                }
                else -> if (container.syncPreferences.isEnabled()) SyncScheduler.ensurePeriodic(this@LucentoApplication)
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
    val wallets: WalletRepository by lazy {
        WalletRepository(
            dao = database.walletDao(),
            transactionDao = database.transactionDao(),
            scheduleCount = { id -> database.recurringDao().countForWallet(id) },
            debtTerms = database.debtTermsDao(),
            runner = runner,
        )
    }
    val categories: CategoryRepository by lazy { CategoryRepository(database.categoryDao(), runner = runner) }
    val transactions: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao(), database.walletDao(), database.categoryDao(), runner = runner)
    }
    val budgets: BudgetRepository by lazy { BudgetRepository(database.budgetDao(), database.categoryDao()) }
    val goals: GoalRepository by lazy { GoalRepository(database.goalDao()) }
    val recurring: RecurringRepository by lazy {
        RecurringRepository(database.recurringDao(), database.walletDao(), database.categoryDao(), transactions, runner = runner)
    }

    // --- optional cloud sync (see docs/SYNC.md); every value here is safe to hold even when unconfigured ---

    private val sessionStore: com.lucento.app.data.sync.SessionStore? by lazy {
        if (SupabaseModule.isConfigured) com.lucento.app.data.sync.SessionStore.forApp(application, BuildConfig.SUPABASE_URL) else null
    }
    private val supabaseClient by lazy { SupabaseModule.create(sessionStore) }
    val syncPreferences: SyncPreferences by lazy { SyncPreferences(application) }
    val auth: AuthRepository by lazy { AuthRepository(
            supabaseClient,
            clearStoredSession = { sessionStore?.clearNow() ?: true },
            isOnline = { application.isOnlineNow() },
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
        )
    }

    val ledgerReset: LocalLedgerReset by lazy {
        LocalLedgerReset(
            database, syncPreferences, GoalPhotos(application), preferences,
            marker = com.lucento.app.data.local.WipeMarker.Prefs(
                application.getSharedPreferences("lucento_ledger_wipe", android.content.Context.MODE_PRIVATE),
            ),
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
