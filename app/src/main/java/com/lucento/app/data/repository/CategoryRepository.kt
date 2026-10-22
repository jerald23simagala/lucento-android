package com.lucento.app.data.repository

import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(
    private val dao: CategoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object BlankName : SaveResult
        data object DuplicateName : SaveResult
        data object NotFound : SaveResult
    }

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        /** Categories in use are archived instead of deleted, so history keeps its meaning. */
        data class Archived(val transactionCount: Int) : DeleteResult
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

    /** Deletes an unused category; archives one that transactions still reference. */
    suspend fun remove(id: Long): DeleteResult {
        val existing = dao.getById(id) ?: return DeleteResult.NotFound
        val used = dao.transactionCount(id)
        return if (used > 0) {
            dao.update(existing.copy(archived = true))
            DeleteResult.Archived(used)
        } else {
            dao.deleteById(id)
            DeleteResult.Deleted
        }
    }

    companion object {
        const val MAX_NAME = 30
    }
}

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    kind = runCatching { CategoryKind.valueOf(kind) }.getOrDefault(CategoryKind.EXPENSE),
    iconKey = iconKey,
    archived = archived,
)
