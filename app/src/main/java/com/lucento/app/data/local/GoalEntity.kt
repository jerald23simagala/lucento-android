package com.lucento.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A savings goal. The optional linked wallet is informational; clearing it never affects the goal. */
@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["wallet_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["wallet_id"]),
        Index(value = ["client_uuid"], unique = true),
    ],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "target_minor") val targetMinor: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    /** Epoch day, or null for an open-ended goal. */
    @ColumnInfo(name = "target_date") val targetDate: Long?,
    @ColumnInfo(name = "wallet_id") val walletId: Long?,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Chosen card picture (`GoalScene.key`); null = automatic from the name. Added in database v4. */
    @ColumnInfo(name = "scene_key") val sceneKey: String? = null,
    /** Path of the user's own card photo (a private copy in app storage). Added in database v5. */
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    /**
     * Cloud sync bookkeeping, added in v6 (see MIGRATION_5_6 and docs/SYNC.md). [photoPath] itself is
     * intentionally never synced — it is a local file path, meaningless on another device.
     */
    @ColumnInfo(name = "client_uuid") val clientUuid: String? = null,
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
    /** Server `revision` this row was last synced at; a push based on it is a causal successor (v7, docs/SYNC.md). */
    @ColumnInfo(name = "base_revision") val baseRevision: Long? = null,
)

/** Money earmarked for (positive) or taken back from (negative) a goal. Deleted with its goal. */
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["goal_id"]), Index(value = ["client_uuid"], unique = true)],
)
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "occurred_on") val occurredOn: Long,
    val note: String,
    @ColumnInfo(name = "client_uuid") val clientUuid: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Cloud sync bookkeeping, added in v6 (see MIGRATION_5_6 and docs/SYNC.md). Contributions are append/delete-only. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
    /** Server `revision` this row was last synced at; a push based on it is a causal successor (v7, docs/SYNC.md). */
    @ColumnInfo(name = "base_revision") val baseRevision: Long? = null,
)
