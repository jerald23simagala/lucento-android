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
    ],
    version = 3,
    exportSchema = true,
)
abstract class LucentoDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun goalDao(): GoalDao
    abstract fun recurringDao(): RecurringDao

    companion object {
        private const val NAME = "lucento.db"

        fun build(context: Context): LucentoDatabase =
            Room.databaseBuilder(context.applicationContext, LucentoDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .addCallback(object : RoomDatabase.Callback() {
                    // Fresh installs only; upgrades are seeded by MIGRATION_1_2.
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        CategorySeed.insertDefaults(db, createdAt = System.currentTimeMillis())
                    }
                })
                .build()
    }
}
