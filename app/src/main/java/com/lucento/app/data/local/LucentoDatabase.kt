package com.lucento.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local-first source of truth. Schema JSON is exported to app/schemas for migration review.
 * Every future version bump must ship an explicit [androidx.room.migration.Migration];
 * destructive fallback is intentionally not enabled because it would erase financial data.
 */
@Database(
    entities = [
        WalletEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        RecurringEntity::class,
        SyncTombstoneEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class LucentoDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun recurringDao(): RecurringDao
    abstract fun syncDao(): SyncDao

    companion object {
        private const val NAME = "lucento.db"

        /** [name] is overridable only so instrumented tests can open a throwaway file through the real setup. */
        fun build(context: Context, name: String = NAME): LucentoDatabase =
            Room.databaseBuilder(context.applicationContext, LucentoDatabase::class.java, name)
                .addMigrations(*Migrations.ALL)
                .addCallback(object : RoomDatabase.Callback() {
                    // Fresh installs only (income categories; expense categories come from onboarding).
                    // Upgrades from v1 are seeded by MIGRATION_1_2.
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        CategorySeed.insertFreshInstall(db, createdAt = System.currentTimeMillis())
                    }

                    // After any migrations: makes sure the sync triggers exist, including on a fresh install, which
                    // Room creates from the entities without running any migration (see SyncTriggers).
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        SyncTriggers.ensure(db)
                    }
                })
                .build()
    }
}
