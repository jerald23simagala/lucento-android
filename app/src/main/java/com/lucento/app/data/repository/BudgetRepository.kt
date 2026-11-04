package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.data.local.BudgetDao
import com.lucento.app.data.local.BudgetEntity
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.domain.Budget
import com.lucento.app.domain.BudgetPlanner
import com.lucento.app.domain.CategoryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.util.UUID

/**
 * Category allocations. Since 0.5 every budget row belongs to one active expense category; the overall budget is the
 * sum of these rows (see [com.lucento.app.domain.BudgetPlanner]) and is never stored or edited on its own.
 */
class BudgetRepository(
    private val dao: BudgetDao,
    private val categories: CategoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val runner: TransactionRunner = TransactionRunner.Direct,
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
     * First-time setup (onboarding, guide) and "give this category an amount": a recurring amount from [from] onward —
     * the same as [setRecurring]. Earlier months are not given this amount. Null removes it from [from] onward.
     */
    suspend fun setAllocation(
        categoryId: Long,
        currencyCode: String,
        limit: Money?,
        from: YearMonth = YearMonth.now(),
    ): SaveResult = setRecurring(categoryId, currencyCode, from, limit)

    /**
     * Budget lifecycle C4 — "Change this month's budget": the amount for [month] only. Other months, past and future,
     * and the recurring amount are untouched. Null removes the override, so the recurring amount applies again.
     */
    suspend fun setMonthAmount(categoryId: Long, currencyCode: String, month: YearMonth, limit: Money?): SaveResult {
        validate(categoryId, currencyCode, limit)?.let { return it }
        val key = month.toString()
        // Find and write together, like the other lifecycle writes, so two quick saves can't create two rows.
        return runner.run {
            val existing = dao.findScope(categoryId, currencyCode, month = key, effectiveFrom = null)
            if (limit == null) {
                existing?.let { dao.deleteById(it.id) }
                SaveResult.Saved(existing?.id ?: 0L)
            } else {
                upsert(existing, categoryId, currencyCode, limit.amountMinor, month = key, effectiveFrom = null)
            }
        }
    }

    /**
     * Budget lifecycle C5 — "Update future monthly budgets": the recurring amount from [from] onward. Months before
     * [from] keep the amount that applied to them (an earlier recurring row stays as it is). Null stops the allocation
     * from [from] (a 0 row), or removes it entirely when nothing earlier exists.
     */
    suspend fun setRecurring(categoryId: Long, currencyCode: String, from: YearMonth, limit: Money?): SaveResult {
        validate(categoryId, currencyCode, limit)?.let { return it }
        return runner.run { writeRecurring(categoryId, currencyCode, from, limit) }
    }

    /**
     * "[month] and every month after": [month]'s own amount (if it had one) gives way to the new recurring amount.
     * Other months changed individually keep their amounts; months before [month] are untouched.
     */
    suspend fun setFrom(categoryId: Long, currencyCode: String, month: YearMonth, limit: Money?): SaveResult {
        validate(categoryId, currencyCode, limit)?.let { return it }
        return runner.run {
            dao.findScope(categoryId, currencyCode, month = month.toString(), effectiveFrom = null)?.let { dao.deleteById(it.id) }
            writeRecurring(categoryId, currencyCode, month, limit)
        }
    }

    /**
     * "Remove for [month] only": no amount that month (a 0 override when a recurring amount would otherwise apply).
     * The recurring amount and every other month are untouched.
     */
    suspend fun clearMonth(categoryId: Long, currencyCode: String, month: YearMonth): SaveResult {
        validate(categoryId, currencyCode, null)?.let { return it }
        return runner.run {
            val rows = dao.getForCategory(categoryId).filter { it.currencyCode == currencyCode }.map { it.toDomain() }
            val recurring = BudgetPlanner.recurringAllocation(rows, month)
            val existing = dao.findScope(categoryId, currencyCode, month = month.toString(), effectiveFrom = null)
            if (recurring == null || recurring.limit.amountMinor == 0L) {
                existing?.let { dao.deleteById(it.id) }
                SaveResult.Saved(existing?.id ?: 0L)
            } else {
                upsert(existing, categoryId, currencyCode, 0L, month = month.toString(), effectiveFrom = null)
            }
        }
    }

    private suspend fun writeRecurring(categoryId: Long, currencyCode: String, from: YearMonth, limit: Money?): SaveResult {
        val key = from.toString()
        val rows = dao.getForCategory(categoryId).filter { it.currencyCode == currencyCode && it.month == null }
        // "From [from] onward" replaces any recurring amount planned to start later.
        rows.filter { it.effectiveFrom != null && it.effectiveFrom > key }.forEach { dao.deleteById(it.id) }
        val sameStart = rows.firstOrNull { it.effectiveFrom == key }
        // A recurring row that already applies before [from]: history that must keep its own amount.
        val earlier = rows.any { it.id != sameStart?.id && (it.effectiveFrom == null || it.effectiveFrom < key) }
        if (limit == null) {
            if (!earlier) {
                sameStart?.let { dao.deleteById(it.id) }
                return SaveResult.Saved(sameStart?.id ?: 0L)
            }
            return upsert(sameStart, categoryId, currencyCode, 0L, month = null, effectiveFrom = key)
        }
        return upsert(sameStart, categoryId, currencyCode, limit.amountMinor, month = null, effectiveFrom = key)
    }

    private suspend fun validate(categoryId: Long, currencyCode: String, limit: Money?): SaveResult? {
        if (limit != null) {
            require(limit.currencyCode == currencyCode) { "Currency mismatch" }
            if (limit.amountMinor <= 0L) return SaveResult.LimitNotPositive
        }
        val category = categories.getById(categoryId)
        if (category == null || category.kind != CategoryKind.EXPENSE.name || category.archived) return SaveResult.InvalidCategory
        return null
    }

    private suspend fun upsert(
        existing: BudgetEntity?,
        categoryId: Long,
        currencyCode: String,
        limitMinor: Long,
        month: String?,
        effectiveFrom: String?,
    ): SaveResult {
        val now = clock()
        if (existing == null) {
            val id = dao.insert(
                BudgetEntity(
                    categoryId = categoryId, currencyCode = currencyCode, limitMinor = limitMinor,
                    createdAt = now, updatedAt = now, clientUuid = UUID.randomUUID().toString(),
                    month = month, effectiveFrom = effectiveFrom,
                )
            )
            return SaveResult.Saved(id)
        }
        dao.update(existing.copy(limitMinor = limitMinor, updatedAt = maxOf(now, existing.updatedAt + 1)))
        return SaveResult.Saved(existing.id)
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
    month = month?.let(::parseYearMonth),
    effectiveFrom = effectiveFrom?.let(::parseYearMonth),
)

/** "yyyy-MM" stored by v10; an unreadable value is treated as absent rather than crashing the budget screen. */
internal fun parseYearMonth(value: String): YearMonth? = runCatching { YearMonth.parse(value) }.getOrNull()
