package com.lucento.app.domain.backup

import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.Feature
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val exportedAt = Instant.parse("2026-03-01T00:00:00Z").toEpochMilli()

    @Test fun exportThenImportRoundTrip() = runTest {
        val original = sample()
        val store = MemoryBackupStore(original)
        val engine = engine(store)
        val json = engine.export("0.5.0")
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"appVersion\": \"0.5.0\""))
        assertTrue(json.contains("\"exportedAt\": \"2026-03-01T00:00:00Z\""))
        assertTrue(json.contains("\"amountMinor\": 450"))
        assertTrue(json.contains("\"currency\": \"USD\""))
        assertTrue(json.contains("\"occurredOn\": \"2026-01-10\""))
        assertFalse(json.contains("photoPath"))
        assertFalse(json.contains("/data/goal.jpg"))
        assertFalse(json.contains("crashReports"))
        assertFalse(json.contains("accessToken"))
        assertFalse(json.contains("installId"))
        assertFalse(json.contains("\"dirty\""))
        assertFalse(json.contains("baseRevision"))
        assertFalse(json.contains("syncedAt"))

        store.snapshot = BackupSnapshot()
        engine.restore(json, RestoreMode.REPLACE)
        val expected = original.copy(goals = original.goals.map { it.copy(photoPath = null) })
        assertEquals(expected, store.snapshot)
        assertEquals(setOf("wal-bank", "wal-card", "cat-food", "cat-pay", "tx-1", "tx-2", "bud-1", "goal-1", "contrib-1", "rec-1"), store.pendingIds)
        assertTrue(RestoredSync.DIRTY)
        assertNull(RestoredSync.syncedAt)
        assertNull(RestoredSync.baseRevision)
    }

    @Test fun previewReportsCountsAndDateRange() {
        val json = BackupCodec.encode(BackupDocument(1, "0.5.0", exportedAt, sample()))
        val preview = BackupEngine(MemoryBackupStore(BackupSnapshot())).preview(json)
        assertEquals(2, preview.counts.wallets)
        assertEquals(2, preview.counts.transactions)
        assertEquals(1, preview.counts.debts)
        assertEquals(LocalDate.parse("2026-01-10"), preview.earliestTransaction)
        assertEquals(LocalDate.parse("2026-01-20"), preview.latestTransaction)
        assertTrue(preview.summary().contains("2 wallets"))
        assertTrue(preview.summary().contains("Transactions from 2026-01-10 to 2026-01-20."))
    }

    @Test fun mergeKeepsTheNewerUpdatedAtAndLocalOnlyRows() = runTest {
        val localWallet = wallet("shared", "On device", updatedAt = 2_000)
        val olderFile = wallet("shared", "From file", updatedAt = 1_000)
        val newerFile = wallet("shared", "From file", updatedAt = 3_000)
        val localOnly = wallet("local-only", "Stay", updatedAt = 2_000)
        val fileOnly = wallet("file-only", "Arrive", updatedAt = 2_000)
        val store = MemoryBackupStore(snapshot(localWallet, localOnly))
        val engine = engine(store)

        engine.restore(encode(snapshot(olderFile, fileOnly)), RestoreMode.MERGE)
        assertEquals("On device", store.snapshot.wallets.first { it.id == "shared" }.name)
        assertEquals(setOf("shared", "local-only", "file-only"), store.snapshot.wallets.map { it.id }.toSet())

        store.snapshot = snapshot(localWallet, localOnly)
        engine.restore(encode(snapshot(newerFile)), RestoreMode.MERGE)
        assertEquals("From file", store.snapshot.wallets.first { it.id == "shared" }.name)
        assertTrue(store.snapshot.wallets.any { it.id == "local-only" })

        store.snapshot = snapshot(localWallet).copy(settings = BackupSettings(themeMode = "DARK", primaryCurrency = "USD"))
        engine.restore(
            encode(snapshot(localWallet.copy(name = "Tied", updatedAt = localWallet.updatedAt)).copy(
                settings = BackupSettings(themeMode = "LIGHT", primaryCurrency = "EUR"),
            )),
            RestoreMode.MERGE,
        )
        assertEquals("On device", store.snapshot.wallets.single().name)
        assertEquals("DARK", store.snapshot.settings.themeMode)
    }

    @Test fun mergeDropsARowWhenTheFileDeletionIsNewer() = runTest {
        val kept = transaction("tx-new", updatedAt = 5_000)
        val dropped = transaction("tx-old", updatedAt = 1_000)
        val store = MemoryBackupStore(snapshot(transactions = listOf(kept, dropped)))
        val deletion = BackupDeletion(BackupTables.TRANSACTIONS, "tx-old", deletedAt = 4_000)
        engine(store).restore(
            encode(snapshot(transactions = listOf(kept), deletions = listOf(deletion))),
            RestoreMode.MERGE,
        )
        assertEquals(listOf("tx-new"), store.snapshot.transactions.map { it.id })
        assertEquals(listOf(deletion), store.snapshot.deletions)
    }

    @Test fun replaceRemovesLocalRowsAndRecordsDeletionsAtomically() = runTest {
        val local = snapshot(wallet("local-only", "Gone", updatedAt = 1_000))
            .copy(settings = BackupSettings(themeMode = "DARK", primaryCurrency = "USD"))
        val file = snapshot(wallet("from-file", "Kept", updatedAt = 1_000))
            .copy(settings = BackupSettings(themeMode = "LIGHT", primaryCurrency = "USD"))
        val store = MemoryBackupStore(local)
        val engine = engine(store)

        store.failOnWrite = true
        val failed = runCatching { engine.restore(encode(file), RestoreMode.REPLACE) }
        assertTrue(failed.isFailure)
        assertEquals(local, store.snapshot)
        assertEquals(0, store.writes)

        store.failOnWrite = false
        engine.restore(encode(file), RestoreMode.REPLACE)
        assertEquals(listOf("from-file"), store.snapshot.wallets.map { it.id })
        assertEquals("LIGHT", store.snapshot.settings.themeMode)
        assertEquals(
            listOf(BackupDeletion(BackupTables.WALLETS, "local-only", deletedAt = exportedAt)),
            store.snapshot.deletions,
        )
        assertTrue(store.pendingIds.contains("from-file"))
    }

    @Test fun malformedJsonAndBadVersionChangeNothing() = runTest {
        val store = MemoryBackupStore(sample())
        val engine = engine(store)
        val valid = engine.export("0.5.0")

        assertEquals(BackupFailure.MALFORMED, failure(engine, store, "{"))
        assertEquals(BackupFailure.MALFORMED, failure(engine, store, valid.replace("\"amountMinor\": 450", "\"amountMinor\": 4.50")))
        assertEquals(BackupFailure.UNSUPPORTED_VERSION, failure(engine, store, valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")))
        assertEquals(BackupFailure.INVALID, failure(engine, store, valid.replaceFirst("{", "{\"accessToken\":\"secret\",")))
        assertEquals(BackupFailure.INVALID, failure(engine, store, valid.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"")))
        assertEquals(sample(), store.snapshot)
        assertEquals(0, store.writes)
    }

    @Test fun versionZeroMigratesAccountsAndEpochDates() = runTest {
        val json = """
            {
              "schemaVersion": 0,
              "appVersion": "0.4.0",
              "exportedAt": 1760000000000,
              "accounts": [{
                "id": "wal-cash",
                "name": "Cash",
                "type": "CASH",
                "currency": "EUR",
                "openingBalanceMinor": 500,
                "sortOrder": 0,
                "archived": false,
                "createdAt": 1760000000000,
                "updatedAt": 1760000000000
              }],
              "categories": [],
              "transactions": [{
                "id": "tx-bus",
                "type": "EXPENSE",
                "amountMinor": 200,
                "currency": "EUR",
                "walletId": "wal-cash",
                "occurredOn": 20000,
                "note": "bus",
                "createdAt": 1760000000000,
                "updatedAt": 1760000000000
              }],
              "budgets": [],
              "goals": [],
              "goalContributions": []
            }
        """.trimIndent()
        val store = MemoryBackupStore(BackupSnapshot())
        engine(store).restore(json, RestoreMode.REPLACE)
        val wallet = store.snapshot.wallets.single()
        assertEquals("Cash", wallet.name)
        assertEquals("EUR", wallet.currency)
        assertEquals(500L, wallet.openingBalanceMinor)
        val transaction = store.snapshot.transactions.single()
        assertEquals(LocalDate.ofEpochDay(20_000), transaction.occurredOn)
        assertEquals(200L, transaction.amountMinor)
        assertEquals("EUR", store.snapshot.settings.primaryCurrency)
        assertTrue(store.pendingIds.contains("tx-bus"))
    }

    @Test fun tenThousandTransactionsRoundTripQuickly() = runTest {
        val base = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val day = LocalDate.parse("2024-01-01")
        val wallet = wallet("wal-bank", "Everyday", updatedAt = base)
        val transactions = List(10_000) { index ->
            transaction("tx-$index", amount = 100L + index, updatedAt = base + index, day = day.plusDays((index % 1_500).toLong()))
        }
        val snapshot = snapshot(wallet, transactions = transactions)
        val started = System.nanoTime()
        val json = BackupCodec.encode(BackupDocument(1, "0.5.0", base, snapshot))
        val decoded = BackupCodec.decode(json)
        val store = MemoryBackupStore(
            snapshot(wallet, transactions = listOf(transactions[0].copy(note = "local newer", updatedAt = transactions[0].updatedAt + 10))),
        )
        engine(store).restore(json, RestoreMode.MERGE)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("10k backup took ${elapsedMs}ms", elapsedMs < 10_000)
        assertEquals(10_000, decoded.snapshot.transactions.size)
        assertEquals(10_000, store.snapshot.transactions.size)
        assertEquals(100L + 4_321, store.snapshot.transactions.first { it.id == "tx-4321" }.amountMinor)
        assertEquals("local newer", store.snapshot.transactions.first { it.id == "tx-0" }.note)
        assertEquals("n1", store.snapshot.transactions.first { it.id == "tx-1" }.note)
    }

    private fun failure(engine: BackupEngine, store: MemoryBackupStore, json: String): BackupFailure {
        val error = runCatching { kotlinx.coroutines.runBlocking { engine.restore(json, RestoreMode.REPLACE) } }
        val exception = error.exceptionOrNull() as? BackupFormatException
        assertTrue("expected BackupFormatException, got ${error.exceptionOrNull()}", exception != null)
        return exception!!.failure
    }

    private fun engine(store: MemoryBackupStore) = BackupEngine(store, clock = { exportedAt })

    private fun encode(snapshot: BackupSnapshot): String =
        BackupCodec.encode(BackupDocument(1, "0.5.0", exportedAt, snapshot))

    private class MemoryBackupStore(initial: BackupSnapshot) : BackupStore {
        var snapshot: BackupSnapshot = initial
        var writes: Int = 0
        var failOnWrite: Boolean = false
        var pendingIds: Set<String> = emptySet()
            private set

        override suspend fun read(): BackupSnapshot = snapshot

        override suspend fun replaceAll(snapshot: BackupSnapshot) {
            val checkpoint = this.snapshot
            val checkpointPending = pendingIds
            try {
                this.snapshot = BackupSnapshot()
                if (failOnWrite) error("disk full")
                this.snapshot = snapshot
                writes += 1
                pendingIds = liveIds(snapshot)
            } catch (error: Throwable) {
                this.snapshot = checkpoint
                pendingIds = checkpointPending
                throw error
            }
        }
    }

    private fun sample(): BackupSnapshot {
        val at = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()
        return BackupSnapshot(
            wallets = listOf(
                BackupWallet("wal-bank", "Everyday", WalletType.BANK, "USD", 10_000, 0, false, at, at),
                BackupWallet("wal-card", "Visa", WalletType.CREDIT_CARD, "USD", 2_000, 1, false, at, at),
            ),
            categories = listOf(
                BackupCategory("cat-food", "Groceries", CategoryKind.EXPENSE, "groceries", 0, false, at, at),
                BackupCategory("cat-pay", "Salary", CategoryKind.INCOME, "salary", 0, false, at, at),
            ),
            transactions = listOf(
                BackupTransaction("tx-1", TransactionType.EXPENSE, 450, "USD", "wal-bank", "cat-food", LocalDate.parse("2026-01-10"), "Milk", at, at, null),
                BackupTransaction("tx-2", TransactionType.INCOME, 5_000, "USD", "wal-bank", "cat-pay", LocalDate.parse("2026-01-20"), "", at, at, null),
            ),
            budgets = listOf(BackupBudget("bud-1", "cat-food", "USD", 30_000, at, at)),
            goals = listOf(
                BackupGoal("goal-1", "Trip", 100_000, "USD", LocalDate.parse("2026-12-01"), "wal-bank", false, "travel", at, at, photoPath = "/data/goal.jpg"),
            ),
            goalContributions = listOf(
                BackupGoalContribution("contrib-1", "goal-1", 2_500, LocalDate.parse("2026-02-01"), "saved", at),
            ),
            recurring = listOf(
                BackupRecurring(
                    "rec-1", "Rent", TransactionType.EXPENSE, 120_000, "USD", "wal-bank", "cat-food",
                    Frequency.MONTHLY, 1, LocalDate.parse("2026-01-01"), null, 0, false, null, 0, at, at,
                ),
            ),
            debtTerms = listOf(BackupDebtTerms("wal-card", 1_250, DebtRepayment.MONTHLY, 12, 5_000, at)),
            settings = BackupSettings(
                onboardingComplete = true,
                themeMode = "DARK",
                primaryCurrency = "USD",
                enabledFeatures = Feature.entries.map { it.key }.filter { it == "budgets" || it == "multiple_wallets" },
                acknowledgedBudgetMonth = "2026-01",
                displayName = "Ada",
                categorySetupDone = true,
                defaultWalletId = "wal-bank",
                guideStep = null,
                smartReading = false,
            ),
        )
    }

    private fun wallet(id: String, name: String, updatedAt: Long) = BackupWallet(
        id = id,
        name = name,
        type = WalletType.CASH,
        currency = "USD",
        openingBalanceMinor = 0,
        sortOrder = 0,
        archived = false,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private fun transaction(
        id: String,
        amount: Long = 100,
        updatedAt: Long = 1_000,
        day: LocalDate = LocalDate.parse("2026-01-10"),
    ) = BackupTransaction(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = amount,
        currency = "USD",
        walletId = "wal-bank",
        categoryId = null,
        occurredOn = day,
        note = "n${id.removePrefix("tx-")}",
        createdAt = updatedAt,
        updatedAt = updatedAt,
        transferGroup = null,
    )

    private fun snapshot(
        vararg wallets: BackupWallet,
        transactions: List<BackupTransaction> = emptyList(),
        deletions: List<BackupDeletion> = emptyList(),
    ): BackupSnapshot {
        val rows = if (wallets.isEmpty()) listOf(wallet("wal-bank", "Everyday", 1_000)) else wallets.toList()
        return BackupSnapshot(
            wallets = rows,
            transactions = transactions,
            deletions = deletions,
            settings = BackupSettings(themeMode = "LIGHT", primaryCurrency = "USD", onboardingComplete = true),
        )
    }
}

private fun liveIds(snapshot: BackupSnapshot): Set<String> = buildSet {
    snapshot.wallets.forEach { add(it.id) }
    snapshot.categories.forEach { add(it.id) }
    snapshot.transactions.forEach { add(it.id) }
    snapshot.budgets.forEach { add(it.id) }
    snapshot.goals.forEach { add(it.id) }
    snapshot.goalContributions.forEach { add(it.id) }
    snapshot.recurring.forEach { add(it.id) }
    snapshot.debtTerms.forEach { add(it.walletId) }
}
