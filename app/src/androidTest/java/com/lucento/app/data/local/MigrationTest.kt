package com.lucento.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.domain.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (emulator/device) migration tests. They read the exported schemas in app/schemas,
 * so `app/schemas/com.lucento.app.data.local.LucentoDatabase/1.json`, `2.json` and `3.json` must be committed.
 * Run with: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LucentoDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsWalletsAndSeedsCategories() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO wallets (name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at) " +
                    "VALUES ('Cash', 'CASH', 'USD', 12345, 0, 0, 1, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT name, opening_balance_minor FROM wallets").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Cash", c.getString(0))
            assertEquals(12345L, c.getLong(1))
        }
        db.query("SELECT COUNT(*) FROM categories").use { c ->
            c.moveToFirst()
            assertEquals(DefaultCategories.all.size, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM transactions").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun migrate1To2_transactionTableEnforcesUniqueClientUuid() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO wallets (id, name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at) " +
                    "VALUES (1, 'Cash', 'CASH', 'USD', 0, 0, 0, 1, 1)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)
        val insert = "INSERT INTO transactions (type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, client_uuid, created_at, updated_at) " +
            "VALUES ('EXPENSE', 450, 'USD', 1, NULL, 20000, '', 'same-key', 1, 1)"
        db.execSQL(insert)
        val duplicateRejected = runCatching { db.execSQL(insert) }.isFailure
        assertEquals(true, duplicateRejected)
    }

    @Test
    fun migrate2To3_keepsTransactionsAsIncomeOrExpenseAndAddsPlanTables() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO wallets (id, name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at) " +
                    "VALUES (1, 'Cash', 'CASH', 'USD', 1000, 0, 0, 1, 1)"
            )
            execSQL(
                "INSERT INTO transactions (type, amount_minor, currency_code, wallet_id, category_id, occurred_on, note, client_uuid, created_at, updated_at) " +
                    "VALUES ('EXPENSE', 450, 'USD', 1, NULL, 20000, 'Lunch', 'k1', 1, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT type, amount_minor, transfer_group FROM transactions").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("EXPENSE", c.getString(0))
            assertEquals(450L, c.getLong(1))
            assertEquals(true, c.isNull(2))
        }
        listOf("budgets", "goals", "goal_contributions", "recurring").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun migrate1To3_runsTheFullChain() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO wallets (name, type, currency_code, opening_balance_minor, sort_order, archived, created_at, updated_at) " +
                    "VALUES ('Bank', 'BANK', 'EUR', 5000, 0, 0, 1, 1)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, *Migrations.ALL)
        db.query("SELECT opening_balance_minor FROM wallets").use { c ->
            c.moveToFirst()
            assertEquals(5000L, c.getLong(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
