package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.BudgetDao
import com.lucento.app.data.local.BudgetEntity
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.domain.Budget
import com.lucento.app.domain.CategoryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Category allocations. Since 0.5 every budget row belongs to one active expense category; the overall budget is the
 * sum of these rows (see [com.lucento.app.domain.BudgetPlanner]) and is never stored or edited on its own.
 */
class BudgetRepository(
    private val dao: BudgetDao,
    private val categories: CategoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object LimitNotPositive : SaveResult
        /** This category already has an allocation in this currency. */
        data object Duplicate : SaveResult
        /** Allocations can only belong to an active expense category. */
        data object InvalidCategory : SaveResult
        data object NotFound : SaveResult
    }

    /** All rows, including any legacy "everything" row (which every calculation ignores). */
    fun observeBudgets(): Flow<List<Budget>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(id: Long?, categoryId: Long, limit: Money): SaveResult {
        if (limit.amountMinor <= 0L) return SaveResult.LimitNotPositive
        val category = categories.getById(categoryId)
        if (category == null || category.kind != CategoryKind.EXPENSE.name || category.archived) return SaveResult.InvalidCategory
        if (dao.countMatching(categoryId, limit.currencyCode, excludeId = id ?: 0L) > 0) return SaveResult.Duplicate
        val now = clock()
        if (id == null) {
            val newId = dao.insert(
                BudgetEntity(
                    categoryId = categoryId,
                    currencyCode = limit.currencyCode,
                    limitMinor = limit.amountMinor,
                    createdAt = now,
                    updatedAt = now,
                    clientUuid = UUID.randomUUID().toString(),
                )
            )
            return SaveResult.Saved(newId)
        }
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        dao.update(
            existing.copy(
                categoryId = categoryId,
                currencyCode = limit.currencyCode,
                limitMinor = limit.amountMinor,
                updatedAt = now,
            )
        )
        return SaveResult.Saved(id)
    }

    /**
     * Sets the monthly allocation of [categoryId] in [currencyCode]: creates it, updates it in place (keeping its id and
     * creation date), or removes it when [limit] is null. The overall budget follows automatically.
     */
    suspend fun setAllocation(categoryId: Long, currencyCode: String, limit: Money?): SaveResult {
        if (limit != null) require(limit.currencyCode == currencyCode) { "Currency mismatch" }
        val existing = dao.findAllocation(categoryId, currencyCode)
        if (limit == null) {
            existing?.let { dao.deleteById(it.id) }
            return SaveResult.Saved(existing?.id ?: 0L)
        }
        return save(existing?.id, categoryId, limit)
    }

    /** Removing an allocation never touches transactions. */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Removes the legacy "budget for everything" rows the user has been told about. Returns how many were removed. */
    suspend fun removeLegacyOverall(): Int = dao.deleteLegacyOverall()
}

internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    limit = Money(limitMinor, currencyCode),
    createdOn = java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
)
