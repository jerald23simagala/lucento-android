package com.lucento.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lucento.app.domain.DefaultCategories

/**
 * Explicit, frozen migrations. Never edit a released migration; add a new one.
 * Destructive fallback is deliberately not configured anywhere.
 */
object Migrations {

    /** v1 (wallets) -> v2 (adds categories and transactions, seeds default categories). */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `categories` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`icon_key` TEXT NOT NULL, " +
                    "`sort_order` INTEGER NOT NULL, " +
                    "`archived` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_categories_kind_archived_sort_order` " +
                    "ON `categories` (`kind`, `archived`, `sort_order`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transactions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`amount_minor` INTEGER NOT NULL, " +
                    "`currency_code` TEXT NOT NULL, " +
                    "`wallet_id` INTEGER NOT NULL, " +
                    "`category_id` INTEGER, " +
                    "`occurred_on` INTEGER NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "`client_uuid` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                    "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_wallet_id` ON `transactions` (`wallet_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_category_id` ON `transactions` (`category_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_occurred_on` ON `transactions` (`occurred_on`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_client_uuid` " +
                    "ON `transactions` (`client_uuid`)"
            )
            CategorySeed.insertDefaults(db, createdAt = System.currentTimeMillis())
        }
    }

    /**
     * v2 -> v3: transfers, budgets, savings goals and recurring schedules. Purely additive — no existing
     * row or column is changed. The new nullable `transfer_group` column defaults to NULL, so every existing
     * transaction keeps its meaning (income or expense).
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transfer_group` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transfer_group` ON `transactions` (`transfer_group`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `budgets` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`category_id` INTEGER, " +
                    "`currency_code` TEXT NOT NULL, " +
                    "`limit_minor` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_category_id` ON `budgets` (`category_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_currency_code` ON `budgets` (`currency_code`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goals` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`target_minor` INTEGER NOT NULL, " +
                    "`currency_code` TEXT NOT NULL, " +
                    "`target_date` INTEGER, " +
                    "`wallet_id` INTEGER, " +
                    "`archived` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_wallet_id` ON `goals` (`wallet_id`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goal_contributions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`goal_id` INTEGER NOT NULL, " +
                    "`amount_minor` INTEGER NOT NULL, " +
                    "`occurred_on` INTEGER NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "`client_uuid` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`goal_id`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goal_id` ON `goal_contributions` (`goal_id`)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_contributions_client_uuid` " +
                    "ON `goal_contributions` (`client_uuid`)"
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`amount_minor` INTEGER NOT NULL, " +
                    "`currency_code` TEXT NOT NULL, " +
                    "`wallet_id` INTEGER NOT NULL, " +
                    "`category_id` INTEGER, " +
                    "`frequency` TEXT NOT NULL, " +
                    "`interval_count` INTEGER NOT NULL, " +
                    "`start_on` INTEGER NOT NULL, " +
                    "`end_on` INTEGER, " +
                    "`next_index` INTEGER NOT NULL, " +
                    "`paused` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                    "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_wallet_id` ON `recurring` (`wallet_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_category_id` ON `recurring` (`category_id`)")
        }
    }

    /** v4: savings goals can remember a chosen card picture (nullable, so existing goals stay automatic). */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `scene_key` TEXT")
        }
    }

    /** v5: goals can keep the user's own card photo (nullable path; existing goals keep their scene). */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `photo_path` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}

/**
 * Category seeding. [insertDefaults] is frozen (used by MIGRATION_1_2). Fresh installs since 0.5 use
 * [insertFreshInstall]: income categories only, because the user picks their own expense categories in onboarding.
 */
object CategorySeed {
    fun insertDefaults(db: SupportSQLiteDatabase, createdAt: Long) = insert(db, DefaultCategories.all, createdAt)

    fun insertFreshInstall(db: SupportSQLiteDatabase, createdAt: Long) = insert(db, DefaultCategories.freshInstall, createdAt)

    private fun insert(db: SupportSQLiteDatabase, seeds: List<DefaultCategories.Seed>, createdAt: Long) {
        val nextOrderByKind = HashMap<String, Int>()
        seeds.forEach { seed ->
            val order = nextOrderByKind[seed.kind.name] ?: 0
            nextOrderByKind[seed.kind.name] = order + 1
            db.execSQL(
                "INSERT INTO `categories` (`name`, `kind`, `icon_key`, `sort_order`, `archived`, `created_at`) " +
                    "VALUES ('${seed.name}', '${seed.kind.name}', '${seed.iconKey}', $order, 0, $createdAt)"
            )
        }
    }
}
