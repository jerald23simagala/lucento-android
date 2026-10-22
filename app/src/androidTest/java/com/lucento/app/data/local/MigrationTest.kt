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
    fun migrate1To3_runsTheFullChainAndRoomOpensTheResult() {
        helper.createDatabase(TEST_DB, 1).apply {
            seedV1Wallets()
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, *Migrations.ALL)
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

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
