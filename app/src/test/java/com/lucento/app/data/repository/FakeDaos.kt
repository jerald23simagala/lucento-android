package com.lucento.app.data.repository

import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.WalletEntity
import com.lucento.app.data.local.WalletFlowRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory DAO fakes so repository rules run as plain JVM tests (no Android, no Room). */
class FakeWalletDao : WalletDao {
    val rows = MutableStateFlow<List<WalletEntity>>(emptyList())
    private var nextId = 1L

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
        rows.value = rows.value.filterNot { it.id == id }
    }
}

class FakeCategoryDao : CategoryDao {
    val rows = MutableStateFlow<List<CategoryEntity>>(emptyList())
    var transactionCounts = mutableMapOf<Long, Int>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<CategoryEntity>> = rows

    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }

    override suspend fun countActiveWithName(kind: String, name: String, excludeId: Long) =
        rows.value.count { !it.archived && it.kind == kind && it.name.equals(name, ignoreCase = true) && it.id != excludeId }

    override suspend fun maxSortOrder(kind: String) = rows.value.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun transactionCount(id: Long) = transactionCounts[id] ?: 0

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
            )
        }
    }

    override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }

    override suspend fun findByClientUuid(clientUuid: String) = rows.value.firstOrNull { it.clientUuid == clientUuid }

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
}
