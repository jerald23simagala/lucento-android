package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A monthly spending limit. [categoryId] null = overall budget for [currencyCode]. Only unused categories
 * can ever be deleted (used ones are archived), so CASCADE only removes budgets of never-used categories.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["currency_code"]),
        Index(value = ["client_uuid"], unique = true),
    ],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Cloud sync bookkeeping, added in v6 (see MIGRATION_5_6 and docs/SYNC.md). */
    @ColumnInfo(name = "client_uuid") val clientUuid: String? = null,
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
    /** Server `revision` this row was last synced at; a push based on it is a causal successor (v7, docs/SYNC.md). */
    @ColumnInfo(name = "base_revision") val baseRevision: Long? = null,
)
