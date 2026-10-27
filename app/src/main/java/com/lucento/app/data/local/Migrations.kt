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

    /**
     * v6: optional cloud sync bookkeeping (see docs/SYNC.md). Purely additive and backward compatible —
     * nothing here changes what any existing row means, and an app that never signs in never looks at
     * these columns.
     *
     * - `wallets`, `categories`, `budgets`, `goals` gain a `client_uuid`: a stable id shared across a
     *   user's devices. `transactions` and `goal_contributions` already had one. Existing rows are
     *   backfilled with a freshly generated UUID (there is nothing to reconcile with yet — this device
     *   has never synced).
     * - Every syncable table gains `dirty` (1 = not yet pushed; existing rows start dirty so the first
     *   sync uploads full history) and `synced_at` (this device's last confirmed push/pull for the row).
     * - `categories` additionally gains `updated_at` (it never needed one locally), so it can take part
     *   in the same last-write-wins ordering as every other table.
     * - A new `sync_tombstones` table plus an `AFTER DELETE` trigger on every syncable table records a
     *   deletion's `client_uuid` automatically, however the delete happened (including ON DELETE CASCADE),
     *   so the sync engine can push a tombstone without any existing repository knowing sync exists.
     * - An `AFTER UPDATE` trigger on every table that can be edited sets `dirty = 1` whenever a row's
     *   `updated_at` changes. It is guarded so the sync engine's own writes never re-trigger it: a plain
     *   repository edit always leaves `synced_at` untouched (`NEW.synced_at IS OLD.synced_at`), while the
     *   sync engine's pull-merge always sets a fresh `synced_at` in the same statement — so only genuine
     *   user edits are ever marked dirty by the trigger.
     */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Explicit literal statements only (no loops or string templates): tools/check_migrations.py replays the
            // SQL literals of this block against 5.json/6.json in CI, and released migrations must stay frozen.

            // --- dirty / synced_at on every syncable table ---
            db.execSQL("ALTER TABLE `wallets` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `wallets` ADD COLUMN `synced_at` INTEGER")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `synced_at` INTEGER")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `synced_at` INTEGER")
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `synced_at` INTEGER")
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `synced_at` INTEGER")
            db.execSQL("ALTER TABLE `goal_contributions` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `goal_contributions` ADD COLUMN `synced_at` INTEGER")

            // --- stable client_uuid (a random version-4 UUID per existing row, generated in SQL) ---
            db.execSQL("ALTER TABLE `wallets` ADD COLUMN `client_uuid` TEXT")
            db.execSQL(
                "UPDATE `wallets` SET client_uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                    "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
                    "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) WHERE client_uuid IS NULL"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_wallets_client_uuid` ON `wallets` (`client_uuid`)")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `client_uuid` TEXT")
            db.execSQL(
                "UPDATE `categories` SET client_uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                    "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
                    "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) WHERE client_uuid IS NULL"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_client_uuid` ON `categories` (`client_uuid`)")
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `client_uuid` TEXT")
            db.execSQL(
                "UPDATE `budgets` SET client_uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                    "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
                    "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) WHERE client_uuid IS NULL"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_client_uuid` ON `budgets` (`client_uuid`)")
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `client_uuid` TEXT")
            db.execSQL(
                "UPDATE `goals` SET client_uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                    "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
                    "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) WHERE client_uuid IS NULL"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goals_client_uuid` ON `goals` (`client_uuid`)")

            // --- categories gains updated_at (needed for last-write-wins) ---
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `updated_at` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `categories` SET updated_at = created_at")

            // --- tombstones: one row per deletion of a row that had a client_uuid ---
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_tombstones` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`table_name` TEXT NOT NULL, " +
                    "`client_uuid` TEXT NOT NULL, " +
                    "`deleted_at` INTEGER NOT NULL, " +
                    "`pushed` INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_tombstones_pushed` ON `sync_tombstones` (`pushed`)")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_wallets_tombstone` AFTER DELETE ON `wallets` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at) VALUES ('wallets', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_categories_tombstone` AFTER DELETE ON `categories` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at) VALUES ('categories', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_transactions_tombstone` AFTER DELETE ON `transactions` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at) VALUES ('transactions', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_budgets_tombstone` AFTER DELETE ON `budgets` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at) VALUES ('budgets', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_goals_tombstone` AFTER DELETE ON `goals` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at) VALUES ('goals', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_goal_contributions_tombstone` AFTER DELETE ON `goal_contributions` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at) VALUES ('goal_contributions', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)); END"
            )

            // --- dirty-on-edit trigger (not needed for goal_contributions: it is append/delete-only) ---
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_wallets_dirty` AFTER UPDATE ON `wallets` " +
                    "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                    "BEGIN UPDATE `wallets` SET dirty = 1 WHERE id = NEW.id; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_categories_dirty` AFTER UPDATE ON `categories` " +
                    "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                    "BEGIN UPDATE `categories` SET dirty = 1 WHERE id = NEW.id; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_transactions_dirty` AFTER UPDATE ON `transactions` " +
                    "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                    "BEGIN UPDATE `transactions` SET dirty = 1 WHERE id = NEW.id; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_budgets_dirty` AFTER UPDATE ON `budgets` " +
                    "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                    "BEGIN UPDATE `budgets` SET dirty = 1 WHERE id = NEW.id; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_goals_dirty` AFTER UPDATE ON `goals` " +
                    "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                    "BEGIN UPDATE `goals` SET dirty = 1 WHERE id = NEW.id; END"
            )
        }
    }

    /**
     * v7: causal conflict resolution (see docs/SYNC.md "Conflict resolution"). Purely additive.
     * - Every synced table gains a nullable `base_revision`: the server `revision` the local row was last synced at.
     *   A push based on the current server revision is a causal successor and wins whatever the device clock says.
     * - `sync_tombstones` gains `base_revision`, and the tombstone triggers are recreated to record it, so a delete
     *   can be told apart from a concurrent edit too.
     * Literal statements only: tools/check_migrations.py replays them in CI.
     */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `wallets` ADD COLUMN `base_revision` INTEGER")
            db.execSQL("ALTER TABLE `categories` ADD COLUMN `base_revision` INTEGER")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `base_revision` INTEGER")
            db.execSQL("ALTER TABLE `budgets` ADD COLUMN `base_revision` INTEGER")
            db.execSQL("ALTER TABLE `goals` ADD COLUMN `base_revision` INTEGER")
            db.execSQL("ALTER TABLE `goal_contributions` ADD COLUMN `base_revision` INTEGER")
            db.execSQL("ALTER TABLE `sync_tombstones` ADD COLUMN `base_revision` INTEGER")
            db.execSQL("DROP TRIGGER IF EXISTS `trg_wallets_tombstone`")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_wallets_tombstone` AFTER DELETE ON `wallets` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('wallets', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
            )
            db.execSQL("DROP TRIGGER IF EXISTS `trg_categories_tombstone`")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_categories_tombstone` AFTER DELETE ON `categories` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('categories', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
            )
            db.execSQL("DROP TRIGGER IF EXISTS `trg_transactions_tombstone`")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_transactions_tombstone` AFTER DELETE ON `transactions` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('transactions', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
            )
            db.execSQL("DROP TRIGGER IF EXISTS `trg_budgets_tombstone`")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_budgets_tombstone` AFTER DELETE ON `budgets` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('budgets', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
            )
            db.execSQL("DROP TRIGGER IF EXISTS `trg_goals_tombstone`")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_goals_tombstone` AFTER DELETE ON `goals` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('goals', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
            )
            db.execSQL("DROP TRIGGER IF EXISTS `trg_goal_contributions_tombstone`")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_goal_contributions_tombstone` AFTER DELETE ON `goal_contributions` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('goal_contributions', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
            )
        }
    }

    /**
     * v8 — debt terms and debt payments (docs/CALCULATIONS.md, "Debt terms"):
     * - new `debt_terms` table: optional interest rate, repayment (one-time / monthly), term and instalment per card or
     *   loan, removed with its wallet;
     * - `recurring` gains `to_wallet_id` (the debt a payment goes to) and `interest_minor` (its interest part, 0 for
     *   every existing schedule, which keeps its meaning exactly).
     * Literal statements only: tools/check_migrations.py replays them in CI.
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `debt_terms` (`wallet_id` INTEGER NOT NULL, `interest_rate_bps` INTEGER, " +
                    "`repayment` TEXT NOT NULL, `term_months` INTEGER, `installment_minor` INTEGER, `updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`wallet_id`), FOREIGN KEY(`wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL("ALTER TABLE `recurring` ADD COLUMN `to_wallet_id` INTEGER")
            db.execSQL("ALTER TABLE `recurring` ADD COLUMN `interest_minor` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v9 — recurring schedules join cloud sync (docs/SYNC.md): `recurring` gains the same bookkeeping as every synced
     * table — a stable random `client_uuid` (backfilled for existing schedules), `dirty` (existing rows start dirty so
     * the first sync uploads them), `synced_at` and `base_revision` — plus the tombstone and dirty-on-edit triggers.
     * Debt terms need no local change: they travel as columns of their wallet's cloud row.
     * Literal statements only: tools/check_migrations.py replays them in CI.
     */
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `recurring` ADD COLUMN `client_uuid` TEXT")
            db.execSQL(
                "UPDATE `recurring` SET client_uuid = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                    "substr(hex(randomblob(2)), 2) || '-' || substr('89ab', 1 + (abs(random()) % 4), 1) || " +
                    "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6))) WHERE client_uuid IS NULL"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_client_uuid` ON `recurring` (`client_uuid`)")
            db.execSQL("ALTER TABLE `recurring` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `recurring` ADD COLUMN `synced_at` INTEGER")
            db.execSQL("ALTER TABLE `recurring` ADD COLUMN `base_revision` INTEGER")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_recurring_tombstone` AFTER DELETE ON `recurring` WHEN OLD.client_uuid IS NOT NULL " +
                    "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('recurring', OLD.client_uuid, " +
                    "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS `trg_recurring_dirty` AFTER UPDATE ON `recurring` " +
                    "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                    "BEGIN UPDATE `recurring` SET dirty = 1 WHERE id = NEW.id; END"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
    )
}

/**
 * The sync triggers at the current schema version (identical SQL to the latest migration that creates them).
 * Run on every open with IF NOT EXISTS: Room builds a fresh install's tables from the entities and never runs a
 * migration, so without this a new install would have no tombstones and no dirty-on-edit at all.
 */
object SyncTriggers {
    fun ensure(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_wallets_tombstone` AFTER DELETE ON `wallets` WHEN OLD.client_uuid IS NOT NULL " +
                "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('wallets', OLD.client_uuid, " +
                "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_categories_tombstone` AFTER DELETE ON `categories` WHEN OLD.client_uuid IS NOT NULL " +
                "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('categories', OLD.client_uuid, " +
                "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_transactions_tombstone` AFTER DELETE ON `transactions` WHEN OLD.client_uuid IS NOT NULL " +
                "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('transactions', OLD.client_uuid, " +
                "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_budgets_tombstone` AFTER DELETE ON `budgets` WHEN OLD.client_uuid IS NOT NULL " +
                "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('budgets', OLD.client_uuid, " +
                "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_goals_tombstone` AFTER DELETE ON `goals` WHEN OLD.client_uuid IS NOT NULL " +
                "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('goals', OLD.client_uuid, " +
                "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_goal_contributions_tombstone` AFTER DELETE ON `goal_contributions` WHEN OLD.client_uuid IS NOT NULL " +
                "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('goal_contributions', OLD.client_uuid, " +
                "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_recurring_tombstone` AFTER DELETE ON `recurring` WHEN OLD.client_uuid IS NOT NULL " +
                "BEGIN INSERT INTO `sync_tombstones` (table_name, client_uuid, deleted_at, base_revision) VALUES ('recurring', OLD.client_uuid, " +
                "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER), OLD.base_revision); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_recurring_dirty` AFTER UPDATE ON `recurring` " +
                "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                "BEGIN UPDATE `recurring` SET dirty = 1 WHERE id = NEW.id; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_wallets_dirty` AFTER UPDATE ON `wallets` " +
                "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                "BEGIN UPDATE `wallets` SET dirty = 1 WHERE id = NEW.id; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_categories_dirty` AFTER UPDATE ON `categories` " +
                "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                "BEGIN UPDATE `categories` SET dirty = 1 WHERE id = NEW.id; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_transactions_dirty` AFTER UPDATE ON `transactions` " +
                "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                "BEGIN UPDATE `transactions` SET dirty = 1 WHERE id = NEW.id; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_budgets_dirty` AFTER UPDATE ON `budgets` " +
                "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                "BEGIN UPDATE `budgets` SET dirty = 1 WHERE id = NEW.id; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `trg_goals_dirty` AFTER UPDATE ON `goals` " +
                "WHEN NEW.updated_at != OLD.updated_at AND NEW.synced_at IS OLD.synced_at " +
                "BEGIN UPDATE `goals` SET dirty = 1 WHERE id = NEW.id; END"
        )
    }
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
            // `updated_at`, `dirty`, `synced_at` and `client_uuid` (added in v6, see MIGRATION_5_6) are left to
            // their column defaults here: this seeding runs both inside MIGRATION_1_2 (schema v2, which has
            // none of those columns yet) and for fresh installs at the current schema, so it must stay valid
            // at every version — never add a v6+ column to this statement.
            db.execSQL(
                "INSERT INTO `categories` (`name`, `kind`, `icon_key`, `sort_order`, `archived`, `created_at`) " +
                    "VALUES ('${seed.name}', '${seed.kind.name}', '${seed.iconKey}', $order, 0, $createdAt)"
            )
        }
    }
}
