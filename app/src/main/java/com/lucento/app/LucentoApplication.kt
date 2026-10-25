package com.lucento.app

import android.app.Application
import androidx.room.withTransaction
import com.lucento.app.data.local.LucentoDatabase
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.BudgetRepository
import com.lucento.app.data.repository.CategoryRepository
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.TransactionRunner
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.data.sync.AuthRepository
import com.lucento.app.data.sync.SupabaseModule
import com.lucento.app.data.sync.SupabaseSyncRemote
import com.lucento.app.data.sync.SyncPreferences
import com.lucento.app.data.sync.SyncRepository
import com.lucento.app.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LucentoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Safe even when cloud sync has never been turned on: ensurePeriodic() only (re-)schedules work that
        // SyncWorker itself immediately no-ops out of unless the preference is on (see SyncWorker.doWork).
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            if (container.syncPreferences.isEnabled()) SyncScheduler.ensurePeriodic(this@LucentoApplication)
        }
    }
}

/** Minimal manual dependency container. Swap for Hilt if the graph grows substantially. */
class AppContainer(application: Application) {
    private val database: LucentoDatabase by lazy { LucentoDatabase.build(application) }

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

    private val supabaseClient by lazy { SupabaseModule.create() }
    val syncPreferences: SyncPreferences by lazy { SyncPreferences(application) }
    val auth: AuthRepository by lazy { AuthRepository(supabaseClient) }
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
        )
    }
}
