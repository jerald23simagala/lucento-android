package com.lucento.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.domain.DefaultCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (emulator/device) migration tests. They read the exported schemas in app/schemas, so
 * `app/schemas/com.lucento.app.data.local.LucentoDatabase/1.json`, `2.json` and `3.json` must be committed —
 * authentic files exported by Room for those versions, never hand-written.
 *
 * Each test seeds representative rows at the old version, migrates, lets MigrationTestHelper validate the
 * resulting schema against the exported one, and then checks that every row kept its meaning.
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LucentoDatabase::class.java,
    )

    private fun SupportSQLiteDatabase.long(sql: String): Long = query(sql).use { c ->
        c.moveToFirst()
        c.getLong(0)
    }

    private fun SupportSQLiteDatabase.assertNoForeignKeyViolations() {
        query("PRAGMA foreign_key_check").use { c -> assertEquals("foreign key violations", 0, c.count) }
    }

    private fun SupportSQLiteDatabase.seedV1Wallets() {
        execSQL(
            "INSERT INTO wallets (id, name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at) " +
                "VALUES (1, 'Cash', 'CASH', 'USD', 12345, 0, 0, 1, 1)"
        )
        execSQL(
            "INSERT INTO wallets (id, name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at) " +
                "VALUES (2, 'Visa', 'CREDIT_CARD', 'USD', 50000, 1, 0, 2, 2)"
        )
        execSQL(
            "INSERT INTO wallets (id, name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at) " +
                "VALUES (3, 'Old yen', 'CASH', 'JPY', 900, 2, 1, 3, 3)"
        )
    }

    @Test
    fun migrate1To2_keepsWalletsExactlyAndSeedsCategories() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedV1Wallets()
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT id, name, type, currency_code, opening_balance_minor, archived FROM wallets ORDER BY id").use { c ->
            assertEquals(3, c.count)
            c.moveToFirst()
            assertEquals("Cash", c.getString(1)); assertEquals("CASH", c.getString(2)); assertEquals("USD", c.getString(3)); assertEquals(12345L, c.getLong(4))
            c.moveToNext()
            assertEquals("CREDIT_CARD", c.getString(2)); assertEquals(50000L, c.getLong(4))
            c.moveToNext()
            assertEquals("JPY", c.getString(3)); assertEquals(1, c.getInt(5))
        }
        assertEquals(DefaultCategories.all.size.toLong(), db.long("SELECT COUNT(*) FROM categories"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM categories WHERE archived = 1"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM transactions"))
        db.assertNoForeignKeyViolations()
    }

    @Test
    fun migrate1To2_transactionTableEnforcesUniqueClientUuidAndWalletRestrict() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedV1Wallets()
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)
        db.execSQL("PRAGMA foreign_keys = ON")
        val insert = "INSERT INTO transactions (type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, client_uuid, created_at, updated_at) " +
            "VALUES ('EXPENSE', 450, 'USD', 1, NULL, 20000, '', 'same-key', 1, 1)"
        db.execSQL(insert)
        assertTrue("duplicate client_uuid must be rejected", runCatching { db.execSQL(insert) }.isFailure)
        assertTrue("a wallet with history can't be deleted", runCatching { db.execSQL("DELETE FROM wallets WHERE id = 1") }.isFailure)
    }

    private fun SupportSQLiteDatabase.seedV2History() {
        seedV1Wallets()
        // v2 has categories; seed two of our own with known ids so references can be checked.
        execSQL("INSERT INTO categories (id, name, kind, icon_key, sort_order, archived, created_at) VALUES (100, 'Coffee', 'EXPENSE', 'dining', 0, 0, 1)")
        execSQL("INSERT INTO categories (id, name, kind, icon_key, sort_order, archived, created_at) VALUES (101, 'Salary', 'INCOME', 'work', 0, 0, 1)")
        execSQL("INSERT INTO categories (id, name, kind, icon_key, sort_order, archived, created_at) VALUES (102, 'Old', 'EXPENSE', 'other', 1, 1, 1)")
        execSQL(
            "INSERT INTO transactions (id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, client_uuid, created_at, updated_at) " +
                "VALUES (1, 'EXPENSE', 450, 'USD', 1, 100, 20000, 'Latte, \"oat\"', 'k1', 1, 1)"
        )
        execSQL(
            "INSERT INTO transactions (id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, client_uuid, created_at, updated_at) " +
                "VALUES (2, 'INCOME', 300000, 'USD', 1, 101, 20001, 'Pay', 'k2', 1, 1)"
        )
        execSQL(
            "INSERT INTO transactions (id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, client_uuid, created_at, updated_at) " +
                "VALUES (3, 'EXPENSE', 9999, 'USD', 2, 102, 20002, '', 'k3', 1, 1)"
        )
    }

    @Test
    fun migrate2To3_keepsEveryTransactionAsIncomeOrExpenseAndAddsPlanTables() {
        helper.createDatabase(TEST_DB, 2).apply {
            seedV2History()
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT id, type, amount_minor, currency_code, wallet_id, category_id, note, transfer_group FROM transactions ORDER BY id").use { c ->
            assertEquals(3, c.count)
            c.moveToFirst()
            assertEquals("EXPENSE", c.getString(1)); assertEquals(450L, c.getLong(2)); assertEquals(100L, c.getLong(5))
            assertEquals("Latte, \"oat\"", c.getString(6)); assertTrue(c.isNull(7))
            c.moveToNext()
            assertEquals("INCOME", c.getString(1)); assertEquals(300000L, c.getLong(2)); assertTrue(c.isNull(7))
            c.moveToNext()
            assertEquals(2L, c.getLong(4)); assertEquals(102L, c.getLong(5)); assertTrue(c.isNull(7))
        }
        assertEquals(1L, db.long("SELECT archived FROM categories WHERE id = 102"))
        listOf("budgets", "goals", "goal_contributions", "recurring").forEach { table ->
            assertEquals("$table starts empty", 0L, db.long("SELECT COUNT(*) FROM $table"))
        }
        // The new tables accept rows that reference migrated data.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO budgets (category_id, currency_code, limit_minor, created_at, updated_at) VALUES (100, 'USD', 20000, 1, 1)")
        db.execSQL(
            "INSERT INTO recurring (name, type, amount_minor, currency_code, wallet_id, category_id, frequency, interval_count, start_on, end_on, next_index, paused, created_at, updated_at) " +
                "VALUES ('Rent', 'EXPENSE', 120000, 'USD', 1, 100, 'MONTHLY', 1, 20000, NULL, 0, 0, 1, 1)"
        )
        db.assertNoForeignKeyViolations()
    }

    @Test
    fun migrate3To4_keepsGoalsAndLeavesTheirPictureAutomatic() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                "INSERT INTO goals (id, name, target_minor, currency_code, target_date, wallet_id, archived, created_at, updated_at) " +
                    "VALUES (1, 'Vacation to Japan', 300000, 'USD', NULL, NULL, 0, 1, 1)"
            )
            execSQL("INSERT INTO goal_contributions (goal_id, amount_minor, occurred_on, note, client_uuid, created_at) VALUES (1, 120000, 20000, '', 'c1', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)
        db.query("SELECT name, target_minor, scene_key FROM goals WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Vacation to Japan", c.getString(0)); assertEquals(300000L, c.getLong(1)); assertTrue(c.isNull(2))
        }
        assertEquals(120000L, db.long("SELECT SUM(amount_minor) FROM goal_contributions WHERE goal_id = 1"))
        db.execSQL("UPDATE goals SET scene_key = 'japan_fuji' WHERE id = 1")
        db.assertNoForeignKeyViolations()
    }

    @Test
    fun migrate4To5_addsAnEmptyPhotoPathAndKeepsGoals() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO goals (id, name, target_minor, currency_code, target_date, wallet_id, archived, created_at, updated_at, scene_key) " +
                    "VALUES (1, 'Laptop', 150000, 'USD', NULL, NULL, 0, 1, 1, 'mountains')"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, Migrations.MIGRATION_4_5)
        db.query("SELECT name, scene_key, photo_path FROM goals WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Laptop", c.getString(0)); assertEquals("mountains", c.getString(1)); assertTrue(c.isNull(2))
        }
        db.execSQL("UPDATE goals SET photo_path = '/data/user/0/app/files/goal_photos/x.jpg' WHERE id = 1")
        db.assertNoForeignKeyViolations()
    }

    @Test
    fun migrate5To6_backfillsClientUuidsAndKeepsEveryRow() {
        helper.createDatabase(TEST_DB, 5).apply {
            seedV1Wallets()
            execSQL("INSERT INTO categories (id, name, kind, icon_key, sort_order, archived, created_at) VALUES (100, 'Coffee', 'EXPENSE', 'dining', 0, 0, 1000)")
            execSQL(
                "INSERT INTO transactions (id, type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, client_uuid, created_at, updated_at) " +
                    "VALUES (1, 'EXPENSE', 450, 'USD', 1, 100, 20000, 'Latte', 'tx-1', 1, 1)"
            )
            execSQL("INSERT INTO budgets (id, category_id, currency_code, limit_minor, created_at, updated_at) VALUES (1, 100, 'USD', 20000, 1, 1)")
            execSQL(
                "INSERT INTO goals (id, name, target_minor, currency_code, target_date, wallet_id, archived, created_at, updated_at) " +
                    "VALUES (1, 'Vacation', 300000, 'USD', NULL, NULL, 0, 1, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, Migrations.MIGRATION_5_6)

        // Every row that lacked a client_uuid got a unique, non-null one.
        listOf("wallets" to 3, "categories" to 1, "budgets" to 1, "goals" to 1).forEach { (table, expectedCount) ->
            db.query("SELECT COUNT(*) FROM `$table`").use { assertTrue(it.moveToFirst()); assertEquals(expectedCount.toLong(), it.getLong(0)) }
            db.query("SELECT COUNT(DISTINCT client_uuid) FROM `$table`").use { assertTrue(it.moveToFirst()); assertEquals(expectedCount.toLong(), it.getLong(0)) }
            db.query("SELECT COUNT(*) FROM `$table` WHERE client_uuid IS NULL").use { assertTrue(it.moveToFirst()); assertEquals(0L, it.getLong(0)) }
        }
        // Existing rows start dirty (never pushed) and categories' updated_at was backfilled from created_at.
        assertEquals(1000L, db.long("SELECT updated_at FROM categories WHERE id = 100"))
        assertEquals(3L, db.long("SELECT COUNT(*) FROM wallets WHERE dirty = 1"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM sync_tombstones"))
        db.assertNoForeignKeyViolations()
    }

    @Test
    fun migrate5To6_deletingASyncedRowWritesATombstoneAndEditingOneMarksItDirtyAgain() {
        helper.createDatabase(TEST_DB, 5).apply {
            seedV1Wallets()
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, Migrations.MIGRATION_5_6)

        // Simulate wallet 2 having already been pushed once.
        db.execSQL("UPDATE wallets SET dirty = 0, synced_at = 500 WHERE id = 2")

        // An ordinary edit (bumps updated_at, leaves synced_at alone) must flip dirty back to 1.
        db.execSQL("UPDATE wallets SET name = 'Visa (renamed)', updated_at = 999 WHERE id = 2")
        assertEquals(1L, db.long("SELECT dirty FROM wallets WHERE id = 2"))

        // A sync pull applying a remote row sets a fresh synced_at in the same statement, so it must NOT be
        // re-marked dirty even though updated_at also changes.
        db.execSQL("UPDATE wallets SET name = 'From cloud', updated_at = 1500, dirty = 0, synced_at = 1500 WHERE id = 2")
        assertEquals(0L, db.long("SELECT dirty FROM wallets WHERE id = 2"))

        // Deleting a wallet that had a client_uuid must record a tombstone with that same id.
        val uuid = db.query("SELECT client_uuid FROM wallets WHERE id = 3").let { c -> c.moveToFirst(); val v = c.getString(0); c.close(); v }
        db.execSQL("DELETE FROM wallets WHERE id = 3")
        db.query("SELECT table_name, client_uuid, pushed FROM sync_tombstones").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("wallets", c.getString(0))
            assertEquals(uuid, c.getString(1))
            assertEquals(0, c.getInt(2))
        }
        db.assertNoForeignKeyViolations()
    }

    @Test
    fun migrate1To8_runsTheFullChainAndRoomOpensTheResult() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedV1Wallets()
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, *Migrations.ALL)
        assertEquals(12345L, db.long("SELECT opening_balance_minor FROM wallets WHERE id = 1"))
        assertEquals(50000L, db.long("SELECT opening_balance_minor FROM wallets WHERE id = 2"))
        assertEquals(DefaultCategories.all.size.toLong(), db.long("SELECT COUNT(*) FROM categories"))
        db.assertNoForeignKeyViolations()
        db.close()

        // The real database class (identity hash, DAOs) must open the migrated file.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, LucentoDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            val wallets = runBlocking { room.walletDao().observeActive().first() }
            assertEquals(listOf("Cash", "Visa"), wallets.map { it.name }) // archived wallet stays hidden, not lost
            assertEquals("JPY", runBlocking { room.walletDao().getById(3) }?.currencyCode)
        } finally {
            room.close()
        }
    }

    @Test
    fun migrate6To7_addsBaseRevisionAndTombstonesRecordIt() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO wallets (id, name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at, client_uuid, dirty, synced_at) " +
                    "VALUES (1, 'Cash', 'CASH', 'USD', 12345, 0, 0, 1, 1, 'w-1', 0, 5)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, Migrations.MIGRATION_6_7)

        db.query("SELECT opening_balance_minor, client_uuid, dirty, base_revision FROM wallets WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(12345L, c.getLong(0)); assertEquals("w-1", c.getString(1)); assertEquals(0, c.getInt(2)); assertTrue(c.isNull(3))
        }
        db.execSQL("UPDATE wallets SET base_revision = 5 WHERE id = 1")
        db.execSQL("DELETE FROM wallets WHERE id = 1")
        db.query("SELECT client_uuid, base_revision FROM sync_tombstones").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("w-1", c.getString(0)); assertEquals(5L, c.getLong(1))
        }
        db.assertNoForeignKeyViolations()
    }

    @Test
    fun migrate7To8_keepsSchedulesAsTheyWereAndAddsDebtTermsThatFollowTheirWallet() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO wallets (id, name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at, client_uuid, dirty) " +
                    "VALUES (1, 'Bank', 'BANK', 'USD', 100000, 0, 0, 1, 1, 'w-1', 0), (2, 'Car loan', 'LOAN', 'USD', 500000, 1, 0, 1, 1, 'w-2', 0)"
            )
            execSQL(
                "INSERT INTO recurring (id, name, type, amount_minor, currency_code, wallet_id, category_id, frequency, interval_count, start_on, end_on, next_index, paused, created_at, updated_at) " +
                    "VALUES (1, 'Rent', 'EXPENSE', 120000, 'USD', 1, NULL, 'MONTHLY', 1, 20000, NULL, 3, 0, 1, 1)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, Migrations.MIGRATION_7_8)

        db.query("SELECT amount_minor, next_index, to_wallet_id, interest_minor FROM recurring WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(120000L, c.getLong(0)); assertEquals(3, c.getInt(1)); assertTrue(c.isNull(2)); assertEquals(0L, c.getLong(3))
        }
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO debt_terms (wallet_id, interest_rate_bps, repayment, term_months, installment_minor, updated_at) VALUES (2, 1250, 'MONTHLY', 24, 23654, 1)")
        assertEquals(1L, db.long("SELECT COUNT(*) FROM debt_terms"))
        db.execSQL("DELETE FROM wallets WHERE id = 2")
        assertEquals(0L, db.long("SELECT COUNT(*) FROM debt_terms"))
        db.assertNoForeignKeyViolations()
    }

    /**
     * A fresh install never runs a migration — Room creates the tables from the entities — so the sync triggers must
     * come from the open callback. Before SyncTriggers, a new install had none: deletes never reached other devices and
     * edits after the first sync were never pushed.
     */
    @Test
    fun freshInstall_hasTheSyncTriggers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(FRESH_DB)
        val room = LucentoDatabase.build(context, FRESH_DB)
        try {
            runBlocking {
                val wallets = room.walletDao()
                val id = wallets.insert(
                    WalletEntity(name = "Cash", type = "CASH", currencyCode = "USD", openingBalanceMinor = 0, createdAt = 1, updatedAt = 1, clientUuid = "w-fresh")
                )
                wallets.markPushed("w-fresh", expectedUpdatedAt = 1, revision = 3, syncedAt = 10)
                assertEquals(false, wallets.getById(id)!!.dirty)

                wallets.update(wallets.getById(id)!!.copy(name = "Cash box", updatedAt = 2)) // an ordinary edit
                assertEquals("an edit after a sync must be marked for upload", true, wallets.getById(id)!!.dirty)

                wallets.deleteById(id)
                val tombstone = room.syncDao().unpushedTombstones().single()
                assertEquals("w-fresh", tombstone.clientUuid)
                assertEquals(3L, tombstone.baseRevision)
            }
        } finally {
            room.close()
            context.deleteDatabase(FRESH_DB)
        }
    }

    private companion object {
        const val FRESH_DB = "fresh-install-test"
        const val TEST_DB = "migration-test"
    }
}
