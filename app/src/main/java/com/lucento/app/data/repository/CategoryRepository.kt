package com.lucento.app.data.repository

import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.CategorySelection
import com.lucento.app.domain.CategorySelectionPlan
import com.lucento.app.domain.CategoryUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val dao: CategoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val runner: TransactionRunner = TransactionRunner.Direct,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object BlankName : SaveResult
        data object DuplicateName : SaveResult
        data object NotFound : SaveResult
    }

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        /**
         * Categories that anything references are archived instead of deleted: history keeps its label, budgets
         * keep their limit and recurring payments keep their category. Archiving is reversible with [restore].
         */
        data class Archived(val usage: CategoryUsage) : DeleteResult
        data object NotFound : DeleteResult
    }

    /** All categories including archived ones (needed to label old transactions). */
    fun observeCategories(): Flow<List<Category>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun create(name: String, kind: CategoryKind, iconKey: String = "other"): SaveResult {
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return SaveResult.BlankName
        if (dao.countActiveWithName(kind.name, trimmed, excludeId = 0) > 0) return SaveResult.DuplicateName
        val id = dao.insert(
            CategoryEntity(
                name = trimmed,
                kind = kind.name,
                iconKey = iconKey,
                sortOrder = dao.maxSortOrder(kind.name) + 1,
                createdAt = clock(),
            )
        )
        return SaveResult.Saved(id)
    }

    suspend fun rename(id: Long, name: String): SaveResult {
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return SaveResult.BlankName
        if (dao.countActiveWithName(existing.kind, trimmed, excludeId = id) > 0) return SaveResult.DuplicateName
        dao.update(existing.copy(name = trimmed))
        return SaveResult.Saved(id)
    }

    /** What references category [id]. */
    suspend fun usage(id: Long): CategoryUsage = CategoryUsage(
        transactionCount = dao.transactionCount(id),
        budgetCount = dao.budgetCount(id),
        scheduleCount = dao.recurringCount(id),
    )

    /**
     * Removes a category without losing anything that depends on it.
     *
     * Only a category nothing references is deleted. One used by transactions, budgets or recurring schedules is
     * archived: it disappears from pickers but keeps labelling history, and its budget keeps working. Deleting it
     * would cascade-delete its budgets and silently uncategorise schedules, so that path does not exist.
     */
    suspend fun remove(id: Long): DeleteResult {
        val existing = dao.getById(id) ?: return DeleteResult.NotFound
        val usage = usage(id)
        return if (usage.isUsed) {
            if (!existing.archived) dao.update(existing.copy(archived = true))
            DeleteResult.Archived(usage)
        } else {
            dao.deleteById(id)
            DeleteResult.Deleted
        }
    }

    /** Brings an archived category back. Refused (DuplicateName) when an active category already has its name. */
    suspend fun restore(id: Long): SaveResult {
        val existing = dao.getById(id) ?: return SaveResult.NotFound
        if (!existing.archived) return SaveResult.Saved(id)
        if (dao.countActiveWithName(existing.kind, existing.name, excludeId = id) > 0) return SaveResult.DuplicateName
        dao.update(existing.copy(archived = false))
        return SaveResult.Saved(id)
    }

    /** Result of applying a category selection (onboarding or the Budget screen's category setup). */
    data class SelectionResult(val created: Int, val restored: Int, val archived: Int, val deleted: Int)

    /**
     * Applies the user's chosen categories in one database transaction (see [CategorySelection.plan]):
     * matching categories are kept with their ids, archived matches are restored, new ones are created in the order
     * chosen, and active expense categories left unchosen are deactivated through [remove] — archived when anything
     * references them, deleted only when nothing does. Income categories are never touched.
     */
    suspend fun applySelection(choices: List<CategorySelection.Choice>): SelectionResult = runner.run {
        val existing = dao.getAll().map { it.toDomain() }
        val plan: CategorySelectionPlan = CategorySelection.plan(existing, choices)
        var archived = 0
        var deleted = 0
        plan.restore.forEach { id -> dao.getById(id)?.let { dao.update(it.copy(archived = false)) } }
        plan.deactivate.forEach { id ->
            when (remove(id)) {
                is DeleteResult.Archived -> archived++
                DeleteResult.Deleted -> deleted++
                DeleteResult.NotFound -> Unit
            }
        }
        var created = 0
        plan.create.forEach { seed ->
            if (create(seed.name, seed.kind, seed.iconKey) is SaveResult.Saved) created++
        }
        SelectionResult(created = created, restored = plan.restore.size, archived = archived, deleted = deleted)
    }

    /**
     * Moves a category one place up ([delta] = -1) or down (+1) among the active categories of its kind by swapping
     * sort orders with its neighbour. Ids and history are unchanged.
     */
    suspend fun move(id: Long, delta: Int): Boolean = runner.run {
        val target = dao.getById(id) ?: return@run false
        val siblings = dao.getAll().filter { it.kind == target.kind && !it.archived }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.id })
        val index = siblings.indexOfFirst { it.id == id }
        val swapWith = index + delta
        if (index < 0 || swapWith !in siblings.indices) return@run false
        val reordered = siblings.toMutableList()
        reordered[index] = siblings[swapWith]
        reordered[swapWith] = siblings[index]
        // Re-number the whole list so equal sort orders (possible after imports) can never make a move a no-op.
        reordered.forEachIndexed { i, c -> if (c.sortOrder != i) dao.update(c.copy(sortOrder = i)) }
        true
    }

    companion object {
        /** Long enough for every preset name ("Religious or community contributions"). */
        const val MAX_NAME = 40
    }
}

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    kind = runCatching { CategoryKind.valueOf(kind) }.getOrDefault(CategoryKind.EXPENSE),
    iconKey = iconKey,
    archived = archived,
)
