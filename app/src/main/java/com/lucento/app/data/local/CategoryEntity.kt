package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["kind", "archived", "sort_order"]),
        Index(value = ["client_uuid"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.lucento.app.domain.CategoryKind] name. */
    val kind: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /**
     * Added in database v6 so categories can take part in sync's last-write-wins ordering, the same way
     * every other syncable table already did. Existing rows were backfilled from [createdAt].
     */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(name = "client_uuid") val clientUuid: String? = null,
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
    /** Server `revision` this row was last synced at; a push based on it is a causal successor (v7, docs/SYNC.md). */
    @ColumnInfo(name = "base_revision") val baseRevision: Long? = null,
)
