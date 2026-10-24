@file:OptIn(ExperimentalSerializationApi::class)

package com.lucento.app.data.sync

import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `timestamptz` columns travel as ISO-8601 strings; Room keeps epoch millis. Converted only at the edge. */
internal object SyncTime {
    fun toIso(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis).toString()
    fun fromIso(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()
}

@Serializable
internal data class WalletRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val type: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("opening_balance_minor") val openingBalanceMinor: Long,
    @SerialName("sort_order") val sortOrder: Int,
    val archived: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @EncodeDefault @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
internal data class CategoryRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val kind: String,
    @SerialName("icon_key") val iconKey: String,
    @SerialName("sort_order") val sortOrder: Int,
    val archived: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @EncodeDefault @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
internal data class TransactionRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("wallet_id") val walletId: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("occurred_on") val occurredOn: Long,
    val note: String,
    @SerialName("transfer_group") val transferGroup: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @EncodeDefault @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
internal data class BudgetRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("limit_minor") val limitMinor: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @EncodeDefault @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
internal data class GoalRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("target_minor") val targetMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("target_date") val targetDate: Long? = null,
    @SerialName("wallet_id") val walletId: String? = null,
    val archived: Boolean,
    @SerialName("scene_key") val sceneKey: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @EncodeDefault @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
internal data class GoalContributionRowDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("goal_id") val goalId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("occurred_on") val occurredOn: Long,
    val note: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @EncodeDefault @SerialName("deleted_at") val deletedAt: String? = null,
)

/** One row's outcome, rolled up by [SyncRepository] into a [SyncReport]. */
internal enum class SyncOutcome { PUSHED, PULLED_NEW, PULLED_UPDATE, LOCAL_WINS_CONFLICT, DELETED_LOCALLY, SKIPPED_STALE }

data class SyncReport(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflictsKeptLocal: Int = 0,
    val tombstonesPushed: Int = 0,
    val deletedLocally: Int = 0,
) {
    operator fun plus(other: SyncReport) = SyncReport(
        pushed + other.pushed,
        pulled + other.pulled,
        conflictsKeptLocal + other.conflictsKeptLocal,
        tombstonesPushed + other.tombstonesPushed,
        deletedLocally + other.deletedLocally,
    )
}

/**
 * The one rule every conflict in [SyncRepository] reduces to (see docs/SYNC.md "Conflict resolution"):
 * a row this device has edited more recently than a remote delete is never thrown away — the delete is
 * skipped locally and the edit is pushed again on the next cycle, which will re-create the row remotely
 * (last-write-wins the other way). Pulled as a pure function so this one safety-critical decision is
 * directly unit-testable without a database or a network client.
 */
internal fun localWinsOverRemoteDelete(localUpdatedAtMillis: Long, localDirty: Boolean, remoteDeletedAtMillis: Long): Boolean =
    localDirty && localUpdatedAtMillis > remoteDeletedAtMillis

sealed interface SyncResult {
    data class Success(val report: SyncReport) : SyncResult
    data object NotSignedIn : SyncResult
    data object NotConfigured : SyncResult
    /** Network/server failure; safe to retry later. Nothing local was lost. */
    data class Retryable(val message: String) : SyncResult
}
