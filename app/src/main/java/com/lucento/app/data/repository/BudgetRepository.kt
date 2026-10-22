package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.BudgetDao
import com.lucento.app.data.local.BudgetEntity
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.domain.Budget
import com.lucento.app.domain.CategoryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BudgetRepository(
    private val dao: BudgetDao,
    private val categories: CategoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object LimitNotPositive : SaveResult
        /** An overall budget, or one for this category, already exists in this currency. */
        data object Duplicate : SaveResult
        /** Budgets can only cover expense categories. */
        data object InvalidCategory : SaveResult
        data object NotFound : SaveResult
    }

    fun observeBudgets(): Flow<List<Budget>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(id: Long?, categoryId: Long?, limit: Money): SaveResult {
        if (limit.amountMinor <= 0L) return SaveResult.LimitNotPositive
        if (categoryId != null) {
            val category = categories.getById(categoryId)
            if (category == null || category.kind != CategoryKind.EXPENSE.name) return SaveResult.InvalidCategory
        }
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

    /** Removing a budget never touches transactions. */
    suspend fun delete(id: Long) = dao.deleteById(id)
}

internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    limit = Money(limitMinor, currencyCode),
    createdOn = java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
)
