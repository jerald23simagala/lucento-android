package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One row per deletion of a row that had been given a [client_uuid]. Added automatically by an `AFTER DELETE`
 * trigger on every syncable table (see MIGRATION_5_6) — no repository or ViewModel needs to know sync exists.
 * The sync engine pushes these as remote soft-deletes (`deleted_at`) and then marks them [pushed], so a device
 * that deleted something offline can tell every other device to delete it too, without a hard DELETE ever
 * racing a slow pull on another device and resurrecting the row.
 */
@Entity(
    tableName = "sync_tombstones",
    indices = [Index(value = ["pushed"])],
)
data class SyncTombstoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
    @ColumnInfo(defaultValue = "0") val pushed: Boolean = false,
    /** The deleted row's `base_revision` (added in v7), so the server can tell a causal delete from a concurrent one. */
    @ColumnInfo(name = "base_revision") val baseRevision: Long? = null,
)

@Dao
interface SyncDao {
    /**
     * Local changes cloud sync has not confirmed yet: dirty rows in every synced table plus unpushed deletions.
     * Observed by the Home header's sync status. Debt terms travel on their wallet row, so they aren't counted apart.
     */
    @Query(
        "SELECT (SELECT COUNT(*) FROM wallets WHERE dirty = 1) + (SELECT COUNT(*) FROM categories WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM transactions WHERE dirty = 1) + (SELECT COUNT(*) FROM budgets WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM goals WHERE dirty = 1) + (SELECT COUNT(*) FROM goal_contributions WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM recurring WHERE dirty = 1) + " +
            "(SELECT COUNT(*) FROM sync_tombstones WHERE pushed = 0)",
    )
    fun observePendingChanges(): Flow<Int>

    @Query("SELECT * FROM sync_tombstones WHERE pushed = 0 ORDER BY id ASC")
    suspend fun unpushedTombstones(): List<SyncTombstoneEntity>

    @Query("UPDATE sync_tombstones SET pushed = 1 WHERE id IN (:ids)")
    suspend fun markPushed(ids: List<Long>)

    /** Old, already-pushed tombstones are harmless to keep but serve no purpose; trimmed periodically. */
    @Query("DELETE FROM sync_tombstones WHERE pushed = 1 AND deleted_at < :olderThan")
    suspend fun pruneOldPushed(olderThan: Long)

    @Insert
    suspend fun insert(tombstone: SyncTombstoneEntity): Long
}
