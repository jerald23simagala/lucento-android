package com.lucento.app.data.repository

import com.lucento.app.data.local.BudgetDao
import com.lucento.app.data.local.BudgetEntity
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.data.local.GoalContributionEntity
import com.lucento.app.data.local.GoalContributionRow
import com.lucento.app.data.local.GoalDao
import com.lucento.app.data.local.GoalEntity
import com.lucento.app.data.local.RecurringDao
import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.local.SyncDao
import com.lucento.app.data.local.SyncTombstoneEntity
import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.local.WalletFlowRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory DAO fakes so repository rules run as plain JVM tests (no Android, no Room). */
class FakeWalletDao : WalletDao {
    val rows = MutableStateFlow<List<WalletEntity>>(emptyList())
    private var nextId = 1L

    /** Optional link so deleteById mirrors the real ON DELETE RESTRICT from transactions. */
    var transactions: FakeTransactionDao? = null

    override fun observeActive(): Flow<List<WalletEntity>> =
        rows.map { list -> list.filter { !it.archived }.sortedBy { it.sortOrder } }

    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }

    override suspend fun countActiveWithName(name: String, excludeId: Long) =
        rows.value.count { !it.archived && it.name.equals(name, ignoreCase = true) && it.id != excludeId }

    override suspend fun maxSortOrder() = rows.value.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun insert(wallet: WalletEntity): Long {
        val id = nextId++
        rows.value = rows.value + wallet.copy(id = id)
        return id
    }

    override suspend fun update(wallet: WalletEntity) {
        rows.value = rows.value.map { if (it.id == wallet.id) wallet else it }
    }

    override suspend fun deleteById(id: Long) {
        check(transactions?.rows?.value?.none { it.walletId == id } ?: true) { "FOREIGN KEY constraint failed" }
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun getDirty() = rows.value.filter { it.dirty }

    override suspend fun findByClientUuid(clientUuid: String) = rows.value.firstOrNull { it.clientUuid == clientUuid }

    override suspend fun markPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int {
        val match = rows.value.firstOrNull { it.clientUuid == clientUuid } ?: return 0
        rows.value = rows.value.map {
            if (it.id == match.id) it.copy(baseRevision = revision, syncedAt = syncedAt, dirty = it.dirty && it.updatedAt != expectedUpdatedAt) else it
        }
        return 1
    }
}

class FakeCategoryDao : CategoryDao {
    val rows = MutableStateFlow<List<CategoryEntity>>(emptyList())
    var transactionCounts = mutableMapOf<Long, Int>()
    private var nextId = 1L

    /** Same order as the real query: kind, sort order, id. */
    override fun observeAll(): Flow<List<CategoryEntity>> =
        rows.map { list -> list.sortedWith(compareBy<CategoryEntity> { it.kind }.thenBy { it.sortOrder }.thenBy { it.id }) }

    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }

    override suspend fun getAll() = rows.value.sortedWith(compareBy<CategoryEntity> { it.kind }.thenBy { it.sortOrder }.thenBy { it.id })

    override suspend fun countActiveWithName(kind: String, name: String, excludeId: Long) =
        rows.value.count { !it.archived && it.kind == kind && it.name.equals(name, ignoreCase = true) && it.id != excludeId }

    override suspend fun maxSortOrder(kind: String) = rows.value.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun transactionCount(id: Long) =
        (transactionCounts[id] ?: 0) + (transactions?.rows?.value?.count { it.categoryId == id } ?: 0)

    /** Optional links so the fake mirrors the real foreign keys (budgets CASCADE, recurring SET NULL). */
    var transactions: FakeTransactionDao? = null
    var budgets: FakeBudgetDao? = null
    var recurring: FakeRecurringDao? = null

    override suspend fun budgetCount(id: Long) = budgets?.rows?.value?.count { it.categoryId == id } ?: 0

    override suspend fun recurringCount(id: Long) = recurring?.rows?.value?.count { it.categoryId == id } ?: 0

    override suspend fun insert(category: CategoryEntity): Long {
        val id = nextId++
        rows.value = rows.value + category.copy(id = id)
        return id
    }

    override suspend fun update(category: CategoryEntity) {
        rows.value = rows.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
        budgets?.let { b -> b.rows.value = b.rows.value.filterNot { it.categoryId == id } } // ON DELETE CASCADE
        recurring?.let { r -> r.rows.value = r.rows.value.map { if (it.categoryId == id) it.copy(categoryId = null) else it } }
        transactions?.let { t -> t.rows.value = t.rows.value.map { if (it.categoryId == id) it.copy(categoryId = null) else it } }
    }

    override suspend fun getDirty() = rows.value.filter { it.dirty }

    override suspend fun findByClientUuid(clientUuid: String) = rows.value.firstOrNull { it.clientUuid == clientUuid }

    override suspend fun markPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int {
        val match = rows.value.firstOrNull { it.clientUuid == clientUuid } ?: return 0
        rows.value = rows.value.map {
            if (it.id == match.id) it.copy(baseRevision = revision, syncedAt = syncedAt, dirty = it.dirty && it.updatedAt != expectedUpdatedAt) else it
        }
        return 1
    }

    override suspend fun getWithoutClientUuid() = rows.value.filter { it.clientUuid == null }

    override suspend fun assignClientUuid(id: Long, clientUuid: String) {
        rows.value = rows.value.map { if (it.id == id && it.clientUuid == null) it.copy(clientUuid = clientUuid) else it }
    }
}

class FakeTransactionDao : TransactionDao {
    val rows = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<TransactionEntity>> =
        rows.map { list -> list.sortedWith(compareByDescending<TransactionEntity> { it.occurredOn }.thenByDescending { it.id }) }

    override fun observeWalletFlows(): Flow<List<WalletFlowRow>> = rows.map { list ->
        list.groupBy { it.walletId }.map { (walletId, items) ->
            WalletFlowRow(
                walletId = walletId,
                incomeMinor = items.filter { it.type == "INCOME" }.sumOf { it.amountMinor },
                expenseMinor = items.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor },
                transferInMinor = items.filter { it.type == "TRANSFER_IN" }.sumOf { it.amountMinor },
                transferOutMinor = items.filter { it.type == "TRANSFER_OUT" }.sumOf { it.amountMinor },
            )
        }
    }

    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }

    override suspend fun findByClientUuid(clientUuid: String) = rows.value.firstOrNull { it.clientUuid == clientUuid }

    override suspend fun getByTransferGroup(group: String) = rows.value.filter { it.transferGroup == group }

    override suspend fun countForWallet(walletId: Long) = rows.value.count { it.walletId == walletId }

    override suspend fun insert(transaction: TransactionEntity): Long {
        // Mirrors the unique index on client_uuid.
        check(rows.value.none { it.clientUuid == transaction.clientUuid }) { "UNIQUE constraint failed: client_uuid" }
        val id = nextId++
        rows.value = rows.value + transaction.copy(id = id)
        return id
    }

    override suspend fun update(transaction: TransactionEntity) {
        rows.value = rows.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteByTransferGroup(group: String) {
        rows.value = rows.value.filterNot { it.transferGroup == group }
    }

    override suspend fun getDirty() = rows.value.filter { it.dirty }

    override suspend fun markPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int {
        val match = rows.value.firstOrNull { it.clientUuid == clientUuid } ?: return 0
        rows.value = rows.value.map {
            if (it.id == match.id) it.copy(baseRevision = revision, syncedAt = syncedAt, dirty = it.dirty && it.updatedAt != expectedUpdatedAt) else it
        }
        return 1
    }
}

class FakeBudgetDao : BudgetDao {
    val rows = MutableStateFlow<List<BudgetEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<BudgetEntity>> = rows

    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }

    override suspend fun countMatching(categoryId: Long?, currencyCode: String, excludeId: Long) =
        rows.value.count { it.currencyCode == currencyCode && it.id != excludeId && it.categoryId == categoryId }

    override suspend fun insert(budget: BudgetEntity): Long {
        val id = nextId++
        rows.value = rows.value + budget.copy(id = id)
        return id
    }

    override suspend fun update(budget: BudgetEntity) {
        rows.value = rows.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun findAllocation(categoryId: Long, currencyCode: String) =
        rows.value.filter { it.categoryId == categoryId && it.currencyCode == currencyCode }.minByOrNull { it.id }

    override suspend fun deleteLegacyOverall(): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.categoryId == null }
        return before - rows.value.size
    }

    override suspend fun getDirty() = rows.value.filter { it.dirty }

    override suspend fun findByClientUuid(clientUuid: String) = rows.value.firstOrNull { it.clientUuid == clientUuid }

    override suspend fun markPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int {
        val match = rows.value.firstOrNull { it.clientUuid == clientUuid } ?: return 0
        rows.value = rows.value.map {
            if (it.id == match.id) it.copy(baseRevision = revision, syncedAt = syncedAt, dirty = it.dirty && it.updatedAt != expectedUpdatedAt) else it
        }
        return 1
    }
}

class FakeGoalDao : GoalDao {
    val goals = MutableStateFlow<List<GoalEntity>>(emptyList())
    val contributions = MutableStateFlow<List<GoalContributionEntity>>(emptyList())
    private var nextId = 1L
    private var nextContributionId = 1L

    override fun observeActive(): Flow<List<GoalEntity>> = goals.map { list -> list.filter { !it.archived } }

    override fun observeContributions(): Flow<List<GoalContributionRow>> =
        combine(goals, contributions) { g, c ->
            val codes = g.associate { it.id to it.currencyCode }
            c.mapNotNull { e -> codes[e.goalId]?.let { GoalContributionRow(e.id, e.goalId, e.amountMinor, e.occurredOn, e.note, it) } }
        }

    override suspend fun getById(id: Long) = goals.value.firstOrNull { it.id == id }

    override suspend fun findContributionByClientUuid(clientUuid: String) =
        contributions.value.firstOrNull { it.clientUuid == clientUuid }

    override suspend fun savedMinor(goalId: Long) = contributions.value.filter { it.goalId == goalId }.sumOf { it.amountMinor }

    override suspend fun insert(goal: GoalEntity): Long {
        val id = nextId++
        goals.value = goals.value + goal.copy(id = id)
        return id
    }

    override suspend fun update(goal: GoalEntity) {
        goals.value = goals.value.map { if (it.id == goal.id) goal else it }
    }

    override suspend fun deleteById(id: Long) {
        goals.value = goals.value.filterNot { it.id == id }
        contributions.value = contributions.value.filterNot { it.goalId == id } // ON DELETE CASCADE
    }

    override suspend fun insertContribution(contribution: GoalContributionEntity): Long {
        check(contributions.value.none { it.clientUuid == contribution.clientUuid }) { "UNIQUE constraint failed" }
        val id = nextContributionId++
        contributions.value = contributions.value + contribution.copy(id = id)
        return id
    }

    override suspend fun deleteContribution(id: Long) {
        contributions.value = contributions.value.filterNot { it.id == id }
    }

    override suspend fun getDirtyGoals() = goals.value.filter { it.dirty }

    override suspend fun findGoalByClientUuid(clientUuid: String) = goals.value.firstOrNull { it.clientUuid == clientUuid }

    override suspend fun markGoalPushed(clientUuid: String, expectedUpdatedAt: Long, revision: Long, syncedAt: Long): Int {
        val match = goals.value.firstOrNull { it.clientUuid == clientUuid } ?: return 0
        goals.value = goals.value.map {
            if (it.id == match.id) it.copy(baseRevision = revision, syncedAt = syncedAt, dirty = it.dirty && it.updatedAt != expectedUpdatedAt) else it
        }
        return 1
    }

    override suspend fun getDirtyContributions() = contributions.value.filter { it.dirty }

    override suspend fun markContributionPushed(clientUuid: String, revision: Long, syncedAt: Long) {
        contributions.value = contributions.value.map {
            if (it.clientUuid == clientUuid) it.copy(dirty = false, baseRevision = revision, syncedAt = syncedAt) else it
        }
    }
}

class FakeRecurringDao : RecurringDao {
    val rows = MutableStateFlow<List<RecurringEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<RecurringEntity>> = rows

    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }

    override suspend fun countForWallet(walletId: Long) = rows.value.count { it.walletId == walletId }

    override suspend fun insert(schedule: RecurringEntity): Long {
        val id = nextId++
        rows.value = rows.value + schedule.copy(id = id)
        return id
    }

    override suspend fun update(schedule: RecurringEntity) {
        rows.value = rows.value.map { if (it.id == schedule.id) schedule else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

class FakeSyncDao : SyncDao {
    val rows = MutableStateFlow<List<SyncTombstoneEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun unpushedTombstones() = rows.value.filterNot { it.pushed }

    override suspend fun markPushed(ids: List<Long>) {
        rows.value = rows.value.map { if (it.id in ids) it.copy(pushed = true) else it }
    }

    override suspend fun pruneOldPushed(olderThan: Long) {
        rows.value = rows.value.filterNot { it.pushed && it.deletedAt < olderThan }
    }

    override suspend fun insert(tombstone: SyncTombstoneEntity): Long {
        val id = nextId++
        rows.value = rows.value + tombstone.copy(id = id)
        return id
    }
}
