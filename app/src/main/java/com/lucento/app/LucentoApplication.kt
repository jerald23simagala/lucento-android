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

class LucentoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
    val categories: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }
    val transactions: TransactionRepository by lazy {
        TransactionRepository(database.transactionDao(), database.walletDao(), database.categoryDao(), runner = runner)
    }
    val budgets: BudgetRepository by lazy { BudgetRepository(database.budgetDao(), database.categoryDao()) }
    val goals: GoalRepository by lazy { GoalRepository(database.goalDao()) }
    val recurring: RecurringRepository by lazy {
        RecurringRepository(database.recurringDao(), database.walletDao(), database.categoryDao(), transactions, runner = runner)
    }
}
