package com.lucento.app.data.backup

import com.lucento.app.data.local.BudgetEntity
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.data.local.DebtTermsEntity
import com.lucento.app.data.local.GoalContributionEntity
import com.lucento.app.data.local.GoalEntity
import com.lucento.app.data.local.LucentoDatabase
import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.local.SyncTombstoneEntity
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.media.GoalPhotos
import com.lucento.app.data.preferences.UserPreferencesRepository
import com.lucento.app.data.repository.TransactionRunner
import com.lucento.app.domain.Feature
import com.lucento.app.domain.backup.BackupBudget
import com.lucento.app.domain.backup.BackupCategory
import com.lucento.app.domain.backup.BackupDebtTerms
import com.lucento.app.domain.backup.BackupDeletion
import com.lucento.app.domain.backup.BackupGoal
import com.lucento.app.domain.backup.BackupGoalContribution
import com.lucento.app.domain.backup.BackupRecurring
import com.lucento.app.domain.backup.BackupSettings
import com.lucento.app.domain.backup.BackupSnapshot
import com.lucento.app.domain.backup.BackupStore
import com.lucento.app.domain.backup.BackupTables
import com.lucento.app.domain.backup.BackupTransaction
import com.lucento.app.domain.backup.BackupWallet
import com.lucento.app.domain.backup.RestoredSync
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/**
 * Room-backed [BackupStore]. [replaceAll] wipes and rewrites the ledger inside one database transaction,
 * then updates device preferences. A failure inside the transaction leaves the previous ledger in place.
 * Every restored row is [RestoredSync.DIRTY] with a null base revision, signed in or not.
 */
class RoomBackupStore(
    private val database: LucentoDatabase,
    private val runner: TransactionRunner,
    private val preferences: UserPreferencesRepository,
    private val goalPhotos: GoalPhotos,
) : BackupStore {

    override suspend fun read(): BackupSnapshot = runner.run {
        ensureIds()
        readSnapshot()
    }

    override suspend fun replaceAll(snapshot: BackupSnapshot) {
        val droppedPhotos = runner.run {
            ensureIds()
            val previousPhotos = database.goalDao().getAll().mapNotNull { it.photoPath }.toSet()
            val revisions = revisionIndex()
            database.transactionDao().deleteAll()
            database.recurringDao().deleteAll()
            database.goalDao().deleteAllContributions()
            database.budgetDao().deleteAll()
            database.debtTermsDao().deleteAll()
            database.goalDao().deleteAll()
            database.walletDao().deleteAll()
            database.categoryDao().deleteAll()
            // Deletes above write tombstones. Clear them and keep only the ones this restore still needs.
            database.syncDao().deleteAll()
            insertAll(snapshot)
            for (deletion in snapshot.deletions) {
                database.syncDao().insert(
                    SyncTombstoneEntity(
                        tableName = deletion.table,
                        clientUuid = deletion.id,
                        deletedAt = deletion.deletedAt,
                        pushed = false,
                        baseRevision = revisions[deletion.table]?.get(deletion.id),
                    ),
                )
            }
            previousPhotos - snapshot.goals.mapNotNull { it.photoPath }.toSet()
        }
        droppedPhotos.forEach { goalPhotos.delete(it) }
        val defaultWallet = snapshot.settings.defaultWalletId?.let { database.walletDao().findByClientUuid(it)?.id }
        preferences.applyBackupSettings(snapshot.settings, defaultWallet)
    }

    override suspend fun mergeAll(snapshot: BackupSnapshot) {
        val droppedPhotos = runner.run {
            ensureIds()
            val previousPhotos = database.goalDao().getAll().mapNotNull { it.photoPath }.toSet()
            val categoryIds = upsertCategories(snapshot.categories)
            val walletIds = upsertWallets(snapshot.wallets)
            upsertDebts(snapshot.debtTerms, walletIds)
            upsertBudgets(snapshot.budgets, categoryIds)
            val goalIds = upsertGoals(snapshot.goals, walletIds)
            upsertContributions(snapshot.goalContributions, goalIds)
            upsertTransactions(snapshot.transactions, walletIds, categoryIds)
            upsertRecurring(snapshot.recurring, walletIds, categoryIds)
            deleteMissing(snapshot)
            insertMissingTombstones(snapshot.deletions)
            previousPhotos - database.goalDao().getAll().mapNotNull { it.photoPath }.toSet()
        }
        droppedPhotos.forEach { goalPhotos.delete(it) }
        val defaultWallet = snapshot.settings.defaultWalletId?.let { database.walletDao().findByClientUuid(it)?.id }
        preferences.applyBackupSettings(snapshot.settings, defaultWallet)
    }

    private suspend fun ensureIds() {
        database.walletDao().getWithoutClientUuid().forEach {
            database.walletDao().assignClientUuid(it.id, UUID.randomUUID().toString())
        }
        database.categoryDao().getWithoutClientUuid().forEach {
            database.categoryDao().assignClientUuidForBackup(it.id, UUID.randomUUID().toString())
        }
        database.budgetDao().getWithoutClientUuid().forEach {
            database.budgetDao().assignClientUuid(it.id, UUID.randomUUID().toString())
        }
        database.goalDao().getWithoutClientUuid().forEach {
            database.goalDao().assignClientUuid(it.id, UUID.randomUUID().toString())
        }
        database.recurringDao().getWithoutClientUuid().forEach {
            database.recurringDao().assignClientUuid(it.id, UUID.randomUUID().toString())
        }
    }

    private suspend fun readSnapshot(): BackupSnapshot {
        val wallets = database.walletDao().getAll()
        val categories = database.categoryDao().getAll()
        val walletUuid = wallets.associate { it.id to requireNotNull(it.clientUuid) }
        val categoryUuid = categories.associate { it.id to requireNotNull(it.clientUuid) }
        val goals = database.goalDao().getAll()
        val goalUuid = goals.associate { it.id to requireNotNull(it.clientUuid) }
        val prefs = preferences.preferences.first()
        val settings = BackupSettings(
            onboardingComplete = prefs.onboardingComplete,
            themeMode = prefs.themeMode.name,
            primaryCurrency = prefs.primaryCurrency,
            enabledFeatures = Feature.entries.map { it.key }.filter { key ->
                prefs.features.isSwitchedOn(Feature.fromKey(key)!!)
            },
            acknowledgedBudgetMonth = prefs.acknowledgedBudgetMonth?.toString(),
            displayName = prefs.displayName,
            categorySetupDone = prefs.categorySetupDone,
            defaultWalletId = prefs.defaultWalletId?.let { id -> wallets.firstOrNull { it.id == id }?.clientUuid },
            guideStep = prefs.guideStep?.key,
            smartReading = prefs.smartReading,
        )
        return BackupSnapshot(
            wallets = wallets.map { it.toBackup() },
            categories = categories.map { it.toBackup() },
            transactions = database.transactionDao().getAll().map { it.toBackup(walletUuid, categoryUuid) },
            budgets = database.budgetDao().getAll().map { it.toBackup(categoryUuid) },
            goals = goals.map { it.toBackup(walletUuid) },
            goalContributions = database.goalDao().getAllContributions().map { it.toBackup(goalUuid) },
            recurring = database.recurringDao().getAll().map { it.toBackup(walletUuid, categoryUuid) },
            debtTerms = database.debtTermsDao().getAll().mapNotNull { it.toBackup(walletUuid) },
            deletions = database.syncDao().getAll().map { it.toBackup() }.filter { it.table in BackupTables.ALL },
            settings = settings,
        )
    }

    private suspend fun revisionIndex(): Map<String, Map<String, Long?>> = mapOf(
        BackupTables.WALLETS to database.walletDao().getAll().mapNotNull { row -> row.clientUuid?.let { it to row.baseRevision } }.toMap(),
        BackupTables.CATEGORIES to database.categoryDao().getAll().mapNotNull { row -> row.clientUuid?.let { it to row.baseRevision } }.toMap(),
        BackupTables.TRANSACTIONS to database.transactionDao().getAll().associate { it.clientUuid to it.baseRevision },
        BackupTables.BUDGETS to database.budgetDao().getAll().mapNotNull { row -> row.clientUuid?.let { it to row.baseRevision } }.toMap(),
        BackupTables.GOALS to database.goalDao().getAll().mapNotNull { row -> row.clientUuid?.let { it to row.baseRevision } }.toMap(),
        BackupTables.GOAL_CONTRIBUTIONS to database.goalDao().getAllContributions().associate { it.clientUuid to it.baseRevision },
        BackupTables.RECURRING to database.recurringDao().getAll().mapNotNull { row -> row.clientUuid?.let { it to row.baseRevision } }.toMap(),
    )

    private suspend fun insertAll(snapshot: BackupSnapshot) {
        val categoryIds = HashMap<String, Long>(snapshot.categories.size)
        for (row in snapshot.categories) {
            categoryIds[row.id] = database.categoryDao().insert(
                CategoryEntity(
                    name = row.name,
                    kind = row.kind.name,
                    iconKey = row.iconKey,
                    sortOrder = row.sortOrder,
                    archived = row.archived,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    clientUuid = row.id,
                    dirty = RestoredSync.DIRTY,
                    syncedAt = RestoredSync.syncedAt,
                    baseRevision = RestoredSync.baseRevision,
                ),
            )
        }
        val walletIds = HashMap<String, Long>(snapshot.wallets.size)
        for (row in snapshot.wallets) {
            walletIds[row.id] = database.walletDao().insert(
                WalletEntity(
                    name = row.name,
                    type = row.type.name,
                    currencyCode = row.currency,
                    openingBalanceMinor = row.openingBalanceMinor,
                    sortOrder = row.sortOrder,
                    archived = row.archived,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    clientUuid = row.id,
                    dirty = RestoredSync.DIRTY,
                    syncedAt = RestoredSync.syncedAt,
                    baseRevision = RestoredSync.baseRevision,
                ),
            )
        }
        for (row in snapshot.debtTerms) {
            database.debtTermsDao().upsert(
                DebtTermsEntity(
                    walletId = walletIds.getValue(row.walletId),
                    interestRateBps = row.interestRateBps,
                    repayment = row.repayment.name,
                    termMonths = row.termMonths,
                    installmentMinor = row.installmentMinor,
                    updatedAt = row.updatedAt,
                ),
            )
        }
        for (row in snapshot.budgets) {
            database.budgetDao().insert(
                BudgetEntity(
                    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
                    currencyCode = row.currency,
                    limitMinor = row.limitMinor,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    clientUuid = row.id,
                    dirty = RestoredSync.DIRTY,
                    syncedAt = RestoredSync.syncedAt,
                    baseRevision = RestoredSync.baseRevision,
                ),
            )
        }
        val goalIds = HashMap<String, Long>(snapshot.goals.size)
        for (row in snapshot.goals) {
            goalIds[row.id] = database.goalDao().insert(
                GoalEntity(
                    name = row.name,
                    targetMinor = row.targetMinor,
                    currencyCode = row.currency,
                    targetDate = row.targetDate?.toEpochDay(),
                    walletId = row.walletId?.let { walletIds.getValue(it) },
                    archived = row.archived,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    sceneKey = row.sceneKey,
                    photoPath = row.photoPath,
                    clientUuid = row.id,
                    dirty = RestoredSync.DIRTY,
                    syncedAt = RestoredSync.syncedAt,
                    baseRevision = RestoredSync.baseRevision,
                ),
            )
        }
        for (row in snapshot.goalContributions) {
            database.goalDao().insertContribution(
                GoalContributionEntity(
                    goalId = goalIds.getValue(row.goalId),
                    amountMinor = row.amountMinor,
                    occurredOn = row.occurredOn.toEpochDay(),
                    note = row.note,
                    clientUuid = row.id,
                    createdAt = row.createdAt,
                    dirty = RestoredSync.DIRTY,
                    syncedAt = RestoredSync.syncedAt,
                    baseRevision = RestoredSync.baseRevision,
                ),
            )
        }
        for (row in snapshot.transactions) {
            database.transactionDao().insert(
                TransactionEntity(
                    type = row.type.name,
                    amountMinor = row.amountMinor,
                    currencyCode = row.currency,
                    walletId = walletIds.getValue(row.walletId),
                    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
                    occurredOn = row.occurredOn.toEpochDay(),
                    note = row.note,
                    clientUuid = row.id,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    transferGroup = row.transferGroup,
                    dirty = RestoredSync.DIRTY,
                    syncedAt = RestoredSync.syncedAt,
                    baseRevision = RestoredSync.baseRevision,
                ),
            )
        }
        for (row in snapshot.recurring) {
            database.recurringDao().insert(
                RecurringEntity(
                    name = row.name,
                    type = row.type.name,
                    amountMinor = row.amountMinor,
                    currencyCode = row.currency,
                    walletId = walletIds.getValue(row.walletId),
                    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
                    frequency = row.frequency.name,
                    intervalCount = row.intervalCount,
                    startOn = row.startOn.toEpochDay(),
                    endOn = row.endOn?.toEpochDay(),
                    nextIndex = row.nextIndex,
                    paused = row.paused,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    toWalletId = row.toWalletId?.let { walletIds.getValue(it) },
                    interestMinor = row.interestMinor,
                    clientUuid = row.id,
                    dirty = RestoredSync.DIRTY,
                    syncedAt = RestoredSync.syncedAt,
                    baseRevision = RestoredSync.baseRevision,
                ),
            )
        }
    }

    private suspend fun upsertCategories(rows: List<BackupCategory>): Map<String, Long> {
        val ids = HashMap<String, Long>(rows.size)
        for (row in rows) {
            val existing = database.categoryDao().findByClientUuid(row.id)
            ids[row.id] = when {
                existing == null -> database.categoryDao().insert(newCategory(row))
                existing.toBackup() == row -> existing.id
                else -> {
                    database.categoryDao().update(existing.edited(row))
                    existing.id
                }
            }
        }
        return ids
    }

    private suspend fun upsertWallets(rows: List<BackupWallet>): Map<String, Long> {
        val ids = HashMap<String, Long>(rows.size)
        for (row in rows) {
            val existing = database.walletDao().findByClientUuid(row.id)
            ids[row.id] = when {
                existing == null -> database.walletDao().insert(newWallet(row))
                existing.toBackup() == row -> existing.id
                else -> {
                    database.walletDao().update(existing.edited(row))
                    existing.id
                }
            }
        }
        return ids
    }

    private suspend fun upsertDebts(rows: List<BackupDebtTerms>, walletIds: Map<String, Long>) {
        for (row in rows) {
            val walletLocal = walletIds.getValue(row.walletId)
            val existing = database.debtTermsDao().get(walletLocal)
            if (existing != null && existing.toBackup(mapOf(walletLocal to row.walletId)) == row) continue
            database.debtTermsDao().upsert(
                DebtTermsEntity(
                    walletId = walletLocal,
                    interestRateBps = row.interestRateBps,
                    repayment = row.repayment.name,
                    termMonths = row.termMonths,
                    installmentMinor = row.installmentMinor,
                    updatedAt = row.updatedAt,
                ),
            )
            val wallet = database.walletDao().getById(walletLocal) ?: continue
            if (wallet.updatedAt == row.updatedAt && wallet.dirty && wallet.baseRevision == null) continue
            database.walletDao().update(
                wallet.copy(
                    updatedAt = maxOf(wallet.updatedAt, row.updatedAt),
                    dirty = true,
                    syncedAt = null,
                    baseRevision = null,
                ),
            )
        }
    }

    private suspend fun upsertBudgets(rows: List<BackupBudget>, categoryIds: Map<String, Long>) {
        val categoryUuid = categoryIds.entries.associate { (uuid, id) -> id to uuid }
        for (row in rows) {
            val existing = database.budgetDao().findByClientUuid(row.id)
            when {
                existing == null -> database.budgetDao().insert(newBudget(row, categoryIds))
                existing.toBackup(categoryUuid) == row -> Unit
                else -> database.budgetDao().update(existing.edited(row, categoryIds))
            }
        }
    }

    private suspend fun upsertGoals(rows: List<BackupGoal>, walletIds: Map<String, Long>): Map<String, Long> {
        val walletUuid = walletIds.entries.associate { (uuid, id) -> id to uuid }
        val ids = HashMap<String, Long>(rows.size)
        for (row in rows) {
            val existing = database.goalDao().findGoalByClientUuid(row.id)
            ids[row.id] = when {
                existing == null -> database.goalDao().insert(newGoal(row, walletIds))
                existing.toBackup(walletUuid) == row -> existing.id
                else -> {
                    database.goalDao().update(existing.edited(row, walletIds))
                    existing.id
                }
            }
        }
        return ids
    }

    private suspend fun upsertContributions(rows: List<BackupGoalContribution>, goalIds: Map<String, Long>) {
        val goalUuid = goalIds.entries.associate { (uuid, id) -> id to uuid }
        for (row in rows) {
            val existing = database.goalDao().findContributionByClientUuid(row.id)
            when {
                existing == null -> database.goalDao().insertContribution(newContribution(row, goalIds))
                existing.toBackup(goalUuid) == row -> Unit
                else -> {
                    database.goalDao().deleteContribution(existing.id)
                    database.goalDao().insertContribution(newContribution(row, goalIds))
                }
            }
        }
    }

    private suspend fun upsertTransactions(
        rows: List<BackupTransaction>,
        walletIds: Map<String, Long>,
        categoryIds: Map<String, Long>,
    ) {
        val walletUuid = walletIds.entries.associate { (uuid, id) -> id to uuid }
        val categoryUuid = categoryIds.entries.associate { (uuid, id) -> id to uuid }
        for (row in rows) {
            val existing = database.transactionDao().findByClientUuid(row.id)
            when {
                existing == null -> database.transactionDao().insert(newTransaction(row, walletIds, categoryIds))
                existing.toBackup(walletUuid, categoryUuid) == row -> Unit
                else -> database.transactionDao().update(existing.edited(row, walletIds, categoryIds))
            }
        }
    }

    private suspend fun upsertRecurring(
        rows: List<BackupRecurring>,
        walletIds: Map<String, Long>,
        categoryIds: Map<String, Long>,
    ) {
        val walletUuid = walletIds.entries.associate { (uuid, id) -> id to uuid }
        val categoryUuid = categoryIds.entries.associate { (uuid, id) -> id to uuid }
        for (row in rows) {
            val existing = database.recurringDao().findByClientUuid(row.id)
            when {
                existing == null -> database.recurringDao().insert(newRecurring(row, walletIds, categoryIds))
                existing.toBackup(walletUuid, categoryUuid) == row -> Unit
                else -> database.recurringDao().update(existing.edited(row, walletIds, categoryIds))
            }
        }
    }

    private suspend fun deleteMissing(snapshot: BackupSnapshot) {
        val transactions = snapshot.transactions.map { it.id }.toSet()
        database.transactionDao().getAll().filter { it.clientUuid !in transactions }.forEach {
            database.transactionDao().deleteById(it.id)
        }
        val recurring = snapshot.recurring.map { it.id }.toSet()
        database.recurringDao().getAll().filter { it.clientUuid !in recurring }.forEach {
            database.recurringDao().deleteById(it.id)
        }
        val contributions = snapshot.goalContributions.map { it.id }.toSet()
        database.goalDao().getAllContributions().filter { it.clientUuid !in contributions }.forEach {
            database.goalDao().deleteContribution(it.id)
        }
        val budgets = snapshot.budgets.map { it.id }.toSet()
        database.budgetDao().getAll().filter { it.clientUuid !in budgets }.forEach {
            database.budgetDao().deleteById(it.id)
        }
        val debtWallets = snapshot.debtTerms.map { it.walletId }.toSet()
        database.debtTermsDao().getAll().forEach { terms ->
            val uuid = database.walletDao().getById(terms.walletId)?.clientUuid
            if (uuid == null || uuid !in debtWallets) database.debtTermsDao().delete(terms.walletId)
        }
        val goals = snapshot.goals.map { it.id }.toSet()
        database.goalDao().getAll().filter { it.clientUuid !in goals }.forEach {
            database.goalDao().deleteById(it.id)
        }
        val wallets = snapshot.wallets.map { it.id }.toSet()
        database.walletDao().getAll().filter { it.clientUuid !in wallets }.forEach {
            database.walletDao().deleteById(it.id)
        }
        val categories = snapshot.categories.map { it.id }.toSet()
        database.categoryDao().getAll().filter { it.clientUuid !in categories }.forEach {
            database.categoryDao().deleteById(it.id)
        }
    }

    private suspend fun insertMissingTombstones(deletions: List<BackupDeletion>) {
        val have = database.syncDao().getAll().map { it.tableName to it.clientUuid }.toSet()
        for (deletion in tombstonesToAdd(deletions, have)) {
            database.syncDao().insert(
                SyncTombstoneEntity(
                    tableName = deletion.table,
                    clientUuid = deletion.id,
                    deletedAt = deletion.deletedAt,
                    pushed = false,
                    baseRevision = null,
                ),
            )
        }
    }
}

private fun WalletEntity.toBackup() = BackupWallet(
    id = requireNotNull(clientUuid),
    name = name,
    type = enumValueOf(type),
    currency = currencyCode,
    openingBalanceMinor = openingBalanceMinor,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun CategoryEntity.toBackup() = BackupCategory(
    id = requireNotNull(clientUuid),
    name = name,
    kind = enumValueOf(kind),
    iconKey = iconKey,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun TransactionEntity.toBackup(wallets: Map<Long, String>, categories: Map<Long, String>) = BackupTransaction(
    id = clientUuid,
    type = enumValueOf(type),
    amountMinor = amountMinor,
    currency = currencyCode,
    walletId = wallets.getValue(walletId),
    categoryId = categoryId?.let { categories.getValue(it) },
    occurredOn = LocalDate.ofEpochDay(occurredOn),
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    transferGroup = transferGroup,
)

private fun BudgetEntity.toBackup(categories: Map<Long, String>) = BackupBudget(
    id = requireNotNull(clientUuid),
    categoryId = categoryId?.let { categories.getValue(it) },
    currency = currencyCode,
    limitMinor = limitMinor,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun GoalEntity.toBackup(wallets: Map<Long, String>) = BackupGoal(
    id = requireNotNull(clientUuid),
    name = name,
    targetMinor = targetMinor,
    currency = currencyCode,
    targetDate = targetDate?.let(LocalDate::ofEpochDay),
    walletId = walletId?.let { wallets[it] },
    archived = archived,
    sceneKey = sceneKey,
    createdAt = createdAt,
    updatedAt = updatedAt,
    photoPath = photoPath,
)

private fun GoalContributionEntity.toBackup(goals: Map<Long, String>) = BackupGoalContribution(
    id = clientUuid,
    goalId = goals.getValue(goalId),
    amountMinor = amountMinor,
    occurredOn = LocalDate.ofEpochDay(occurredOn),
    note = note,
    createdAt = createdAt,
)

private fun RecurringEntity.toBackup(wallets: Map<Long, String>, categories: Map<Long, String>) = BackupRecurring(
    id = requireNotNull(clientUuid),
    name = name,
    type = enumValueOf(type),
    amountMinor = amountMinor,
    currency = currencyCode,
    walletId = wallets.getValue(walletId),
    categoryId = categoryId?.let { categories.getValue(it) },
    frequency = enumValueOf(frequency),
    intervalCount = intervalCount,
    startOn = LocalDate.ofEpochDay(startOn),
    endOn = endOn?.let(LocalDate::ofEpochDay),
    nextIndex = nextIndex,
    paused = paused,
    toWalletId = toWalletId?.let { wallets[it] },
    interestMinor = interestMinor,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun DebtTermsEntity.toBackup(wallets: Map<Long, String>): BackupDebtTerms? {
    val walletId = wallets[walletId] ?: return null
    return BackupDebtTerms(
        walletId = walletId,
        interestRateBps = interestRateBps,
        repayment = enumValueOf(repayment),
        termMonths = termMonths,
        installmentMinor = installmentMinor,
        updatedAt = updatedAt,
    )
}

private fun SyncTombstoneEntity.toBackup() = BackupDeletion(
    table = tableName,
    id = clientUuid,
    deletedAt = deletedAt,
)

private fun newCategory(row: BackupCategory) = CategoryEntity(
    name = row.name,
    kind = row.kind.name,
    iconKey = row.iconKey,
    sortOrder = row.sortOrder,
    archived = row.archived,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    clientUuid = row.id,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun CategoryEntity.edited(row: BackupCategory) = copy(
    name = row.name,
    kind = row.kind.name,
    iconKey = row.iconKey,
    sortOrder = row.sortOrder,
    archived = row.archived,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun newWallet(row: BackupWallet) = WalletEntity(
    name = row.name,
    type = row.type.name,
    currencyCode = row.currency,
    openingBalanceMinor = row.openingBalanceMinor,
    sortOrder = row.sortOrder,
    archived = row.archived,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    clientUuid = row.id,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun WalletEntity.edited(row: BackupWallet) = copy(
    name = row.name,
    type = row.type.name,
    currencyCode = row.currency,
    openingBalanceMinor = row.openingBalanceMinor,
    sortOrder = row.sortOrder,
    archived = row.archived,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun newBudget(row: BackupBudget, categoryIds: Map<String, Long>) = BudgetEntity(
    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
    currencyCode = row.currency,
    limitMinor = row.limitMinor,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    clientUuid = row.id,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun BudgetEntity.edited(row: BackupBudget, categoryIds: Map<String, Long>) = copy(
    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
    currencyCode = row.currency,
    limitMinor = row.limitMinor,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun newGoal(row: BackupGoal, walletIds: Map<String, Long>) = GoalEntity(
    name = row.name,
    targetMinor = row.targetMinor,
    currencyCode = row.currency,
    targetDate = row.targetDate?.toEpochDay(),
    walletId = row.walletId?.let { walletIds.getValue(it) },
    archived = row.archived,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    sceneKey = row.sceneKey,
    photoPath = row.photoPath,
    clientUuid = row.id,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun GoalEntity.edited(row: BackupGoal, walletIds: Map<String, Long>) = copy(
    name = row.name,
    targetMinor = row.targetMinor,
    currencyCode = row.currency,
    targetDate = row.targetDate?.toEpochDay(),
    walletId = row.walletId?.let { walletIds.getValue(it) },
    archived = row.archived,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    sceneKey = row.sceneKey,
    photoPath = row.photoPath,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun newContribution(row: BackupGoalContribution, goalIds: Map<String, Long>) = GoalContributionEntity(
    goalId = goalIds.getValue(row.goalId),
    amountMinor = row.amountMinor,
    occurredOn = row.occurredOn.toEpochDay(),
    note = row.note,
    clientUuid = row.id,
    createdAt = row.createdAt,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun newTransaction(
    row: BackupTransaction,
    walletIds: Map<String, Long>,
    categoryIds: Map<String, Long>,
) = TransactionEntity(
    type = row.type.name,
    amountMinor = row.amountMinor,
    currencyCode = row.currency,
    walletId = walletIds.getValue(row.walletId),
    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
    occurredOn = row.occurredOn.toEpochDay(),
    note = row.note,
    clientUuid = row.id,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    transferGroup = row.transferGroup,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun TransactionEntity.edited(
    row: BackupTransaction,
    walletIds: Map<String, Long>,
    categoryIds: Map<String, Long>,
) = copy(
    type = row.type.name,
    amountMinor = row.amountMinor,
    currencyCode = row.currency,
    walletId = walletIds.getValue(row.walletId),
    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
    occurredOn = row.occurredOn.toEpochDay(),
    note = row.note,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    transferGroup = row.transferGroup,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun newRecurring(
    row: BackupRecurring,
    walletIds: Map<String, Long>,
    categoryIds: Map<String, Long>,
) = RecurringEntity(
    name = row.name,
    type = row.type.name,
    amountMinor = row.amountMinor,
    currencyCode = row.currency,
    walletId = walletIds.getValue(row.walletId),
    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
    frequency = row.frequency.name,
    intervalCount = row.intervalCount,
    startOn = row.startOn.toEpochDay(),
    endOn = row.endOn?.toEpochDay(),
    nextIndex = row.nextIndex,
    paused = row.paused,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    toWalletId = row.toWalletId?.let { walletIds.getValue(it) },
    interestMinor = row.interestMinor,
    clientUuid = row.id,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

private fun RecurringEntity.edited(
    row: BackupRecurring,
    walletIds: Map<String, Long>,
    categoryIds: Map<String, Long>,
) = copy(
    name = row.name,
    type = row.type.name,
    amountMinor = row.amountMinor,
    currencyCode = row.currency,
    walletId = walletIds.getValue(row.walletId),
    categoryId = row.categoryId?.let { categoryIds.getValue(it) },
    frequency = row.frequency.name,
    intervalCount = row.intervalCount,
    startOn = row.startOn.toEpochDay(),
    endOn = row.endOn?.toEpochDay(),
    nextIndex = row.nextIndex,
    paused = row.paused,
    createdAt = row.createdAt,
    updatedAt = row.updatedAt,
    toWalletId = row.toWalletId?.let { walletIds.getValue(it) },
    interestMinor = row.interestMinor,
    dirty = true,
    syncedAt = null,
    baseRevision = null,
)

/**
 * Deletions from a merged backup that this device does not already know about. A tombstone this device has already
 * pushed counts as known: re-queueing it would stamp and upload the same delete again (QA R6-02).
 */
internal fun tombstonesToAdd(deletions: List<BackupDeletion>, known: Set<Pair<String, String>>): List<BackupDeletion> =
    deletions.filter { (it.table to it.id) !in known }.distinctBy { it.table to it.id }
