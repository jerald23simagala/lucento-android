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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

/** Seeds [DefaultCategories] once: on fresh install (database callback) or when upgrading from v1. */
object CategorySeed {
    fun insertDefaults(db: SupportSQLiteDatabase, createdAt: Long) {
        val nextOrderByKind = HashMap<String, Int>()
        DefaultCategories.all.forEach { seed ->
            val order = nextOrderByKind[seed.kind.name] ?: 0
            nextOrderByKind[seed.kind.name] = order + 1
            db.execSQL(
                "INSERT INTO `categories` (`name`, `kind`, `icon_key`, `sort_order`, `archived`, `created_at`) " +
                    "VALUES ('${seed.name}', '${seed.kind.name}', '${seed.iconKey}', $order, 0, $createdAt)"
            )
        }
    }
}
