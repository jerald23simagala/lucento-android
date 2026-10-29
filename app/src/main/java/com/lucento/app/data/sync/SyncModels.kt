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
    /** Assigned by the server on every write (see SupabaseSyncRemote); read-only for clients, never pushed. */
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    /** Server revision: +1 on every accepted write. Read-only for clients (never encoded: always null when pushing). */
    val revision: Long? = null,
    /** The revision this push was based on. Always sent, null included, so the server never reuses a stored value. */
    @EncodeDefault @SerialName("base_revision") val baseRevision: Long? = null,
    // A card's or loan's optional terms (local `debt_terms`, synced from v9). Always sent, nulls included, so clearing
    // them syncs too. A build older than v9 never sends them, and the server then keeps what it has.
    @EncodeDefault @SerialName("interest_rate_bps") val interestRateBps: Int? = null,
    @EncodeDefault val repayment: String? = null,
    @EncodeDefault @SerialName("term_months") val termMonths: Int? = null,
    @EncodeDefault @SerialName("installment_minor") val installmentMinor: Long? = null,
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
    /** Assigned by the server on every write (see SupabaseSyncRemote); read-only for clients, never pushed. */
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    /** Server revision: +1 on every accepted write. Read-only for clients (never encoded: always null when pushing). */
    val revision: Long? = null,
    /** The revision this push was based on. Always sent, null included, so the server never reuses a stored value. */
    @EncodeDefault @SerialName("base_revision") val baseRevision: Long? = null,
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
    /** Assigned by the server on every write (see SupabaseSyncRemote); read-only for clients, never pushed. */
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    /** Server revision: +1 on every accepted write. Read-only for clients (never encoded: always null when pushing). */
    val revision: Long? = null,
    /** The revision this push was based on. Always sent, null included, so the server never reuses a stored value. */
    @EncodeDefault @SerialName("base_revision") val baseRevision: Long? = null,
)

/** A recurring schedule (v9). Wallet and category ids are the parents' client uuids, like every other row. */
@Serializable
internal data class RecurringRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val type: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("wallet_id") val walletId: String,
    @EncodeDefault @SerialName("category_id") val categoryId: String? = null,
    val frequency: String,
    @SerialName("interval_count") val intervalCount: Int,
    @SerialName("start_on") val startOn: Long,
    @EncodeDefault @SerialName("end_on") val endOn: Long? = null,
    @SerialName("next_index") val nextIndex: Int,
    val paused: Boolean,
    @EncodeDefault @SerialName("to_wallet_id") val toWalletId: String? = null,
    @EncodeDefault @SerialName("interest_minor") val interestMinor: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @EncodeDefault @SerialName("deleted_at") val deletedAt: String? = null,
    /** Assigned by the server on every write (see SupabaseSyncRemote); read-only for clients, never pushed. */
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    /** Server revision: +1 on every accepted write. Read-only for clients (never encoded: always null when pushing). */
    val revision: Long? = null,
    /** The revision this push was based on. Always sent, null included, so the server never reuses a stored value. */
    @EncodeDefault @SerialName("base_revision") val baseRevision: Long? = null,
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
    /** Assigned by the server on every write (see SupabaseSyncRemote); read-only for clients, never pushed. */
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    /** Server revision: +1 on every accepted write. Read-only for clients (never encoded: always null when pushing). */
    val revision: Long? = null,
    /** The revision this push was based on. Always sent, null included, so the server never reuses a stored value. */
    @EncodeDefault @SerialName("base_revision") val baseRevision: Long? = null,
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
    /** Assigned by the server on every write (see SupabaseSyncRemote); read-only for clients, never pushed. */
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    /** Server revision: +1 on every accepted write. Read-only for clients (never encoded: always null when pushing). */
    val revision: Long? = null,
    /** The revision this push was based on. Always sent, null included, so the server never reuses a stored value. */
    @EncodeDefault @SerialName("base_revision") val baseRevision: Long? = null,
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
    /** Assigned by the server on every write (see SupabaseSyncRemote); read-only for clients, never pushed. */
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    /** Server revision: +1 on every accepted write. Read-only for clients (never encoded: always null when pushing). */
    val revision: Long? = null,
    /** The revision this push was based on. Always sent, null included, so the server never reuses a stored value. */
    @EncodeDefault @SerialName("base_revision") val baseRevision: Long? = null,
)

/** One row's outcome, rolled up by [SyncRepository] into a [SyncReport]. */
internal enum class SyncOutcome { PUSHED, PULLED_NEW, PULLED_UPDATE, LOCAL_WINS_CONFLICT, DELETED_LOCALLY, SKIPPED_STALE }

data class SyncReport(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: Int = 0,
    val tombstonesPushed: Int = 0,
    val deletedLocally: Int = 0,
    /** Deleted wallets, categories or goals brought back locally so a child row could be kept. */
    val parentsRestored: Int = 0,
    /** Pending local edits that tied with the server and were nudged one millisecond later so they can be pushed. */
    val pendingKept: Int = 0,
) {
    operator fun plus(other: SyncReport) = SyncReport(
        pushed + other.pushed,
        pulled + other.pulled,
        conflicts + other.conflicts,
        tombstonesPushed + other.tombstonesPushed,
        deletedLocally + other.deletedLocally,
        parentsRestored + other.parentsRestored,
        pendingKept + other.pendingKept,
    )
}

/**
 * Conflict rule, causal first (see docs/SYNC.md "Conflict resolution"). An unpushed local edit whose base is the
 * server's current revision is a causal successor — the user edited the latest version — so it wins outright, and
 * no clock is consulted. Only a *concurrent* edit (the server row changed since this device's base, or the base is
 * unknown) falls back to edit time: strictly later wins, a tie goes to the server. The database applies exactly the
 * same rule to pushes (`lucento_reject_stale_update`), so both sides always agree on the winner.
 */
internal fun localEditWins(localBaseRevision: Long?, localUpdatedAt: Long, remoteRevision: Long?, remoteUpdatedAt: Long): Boolean =
    if (remoteRevision != null && localBaseRevision == remoteRevision) true else localUpdatedAt > remoteUpdatedAt

/**
 * A remote delete against a local row: the delete wins unless this device holds an unpushed edit that beats it under
 * [localEditWins] (a causal edit made after seeing the delete can't exist — the row would be gone locally).
 */
internal fun localWinsOverRemoteDelete(localUpdatedAtMillis: Long, localDirty: Boolean, remoteDeletedAtMillis: Long): Boolean =
    localDirty && localUpdatedAtMillis > remoteDeletedAtMillis

/** What a pulled live (not deleted) row does to the local copy. */
internal enum class PullDecision { INSERT, UPDATE, KEEP_LOCAL, UNCHANGED }

/**
 * A pending (dirty) local edit survives a pull when it is a causal successor, or when it is not older than the
 * incoming row. A tie keeps the pending edit: the server's own rule keeps the stored row on an equal timestamp, so
 * [tieBreakUpdatedAt] moves the local edit one millisecond later and the next push is accepted.
 */
internal fun protectsPendingEdit(
    localBaseRevision: Long?,
    localUpdatedAt: Long,
    remoteRevision: Long?,
    remoteUpdatedAt: Long,
): Boolean = localEditWins(localBaseRevision, localUpdatedAt, remoteRevision, remoteUpdatedAt) ||
    localUpdatedAt == remoteUpdatedAt

/** Non-null when a dirty tie should be pushed as one millisecond later than the server row. */
internal fun tieBreakUpdatedAt(
    dirty: Boolean,
    localBaseRevision: Long?,
    localUpdatedAt: Long,
    remoteRevision: Long?,
    remoteUpdatedAt: Long,
): Long? {
    if (!dirty) return null
    val causal = remoteRevision != null && localBaseRevision == remoteRevision
    if (!causal && localUpdatedAt == remoteUpdatedAt) return remoteUpdatedAt + 1
    return null
}

/**
 * The pull rule. A clean local row has nothing to protect: the server's copy is the accepted state and is applied
 * even if its edit time is older (a fast-clock device must still take edits made elsewhere). A dirty row is kept when
 * [protectsPendingEdit] — including a same-timestamp edit — and is then pushed.
 */
internal fun pullDecision(
    localExists: Boolean,
    localDirty: Boolean,
    localBaseRevision: Long?,
    localUpdatedAt: Long,
    remoteRevision: Long?,
    remoteUpdatedAt: Long,
): PullDecision = when {
    !localExists -> PullDecision.INSERT
    localDirty && protectsPendingEdit(localBaseRevision, localUpdatedAt, remoteRevision, remoteUpdatedAt) -> PullDecision.KEEP_LOCAL
    !localDirty && localBaseRevision == remoteRevision && localUpdatedAt == remoteUpdatedAt -> PullDecision.UNCHANGED
    else -> PullDecision.UPDATE
}

/**
 * Next pull cursor, in server time. It trails the newest row seen by [overlapMillis] so a write whose server
 * timestamp was taken before — but committed after — this pull's read is still picked up next time (re-pulled
 * rows are idempotent). It never passes a row that was deferred because its parent hadn't arrived yet. With
 * nothing seen, the cursor stays where it was.
 */
internal fun nextPullCursor(sinceMillis: Long, newestSeenMillis: Long?, oldestDeferredMillis: Long?, overlapMillis: Long): Long {
    if (newestSeenMillis == null) return sinceMillis
    val trailing = newestSeenMillis - overlapMillis
    return if (oldestDeferredMillis != null) minOf(trailing, oldestDeferredMillis) else trailing
}

sealed interface SyncResult {
    data class Success(val report: SyncReport) : SyncResult
    data object NotSignedIn : SyncResult
    data object NotConfigured : SyncResult
    /**
     * This phone's ledger was synced with a different account. Nothing is pushed. Sign-out and account switching
     * wipe the ledger first; this result is the guard if a sync starts before that wipe.
     */
    data object AccountMismatch : SyncResult
    /** Network/server failure; safe to retry later. Nothing local was lost. */
    data class Retryable(val message: String) : SyncResult
}

/**
 * A short, safe description of a failure, for anything that is shown, stored or logged. Never use `Throwable.message`
 * directly: supabase-kt's `RestException` message embeds the request URL and **headers** — including
 * `Authorization: Bearer <access token>` on signed-in requests — and `HttpRequestException` embeds the URL (with the
 * user's id). Only the server's own error description, or the exception type, survives.
 */
internal fun safeFailureDescription(e: Throwable): String = when (e) {
    is io.github.jan.supabase.exceptions.RestException ->
        (e.description?.takeIf { it.isNotBlank() } ?: e.error).take(MAX_FAILURE_DESCRIPTION)
    is io.github.jan.supabase.exceptions.HttpRequestException -> "Couldn't reach the server"
    else -> e::class.simpleName ?: "Unexpected error"
}

private const val MAX_FAILURE_DESCRIPTION = 160

/** Wire format for sync rows: unknown server columns are ignored; defaults stay off except `deleted_at`. */
internal val SyncJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * Whether a pulled row means two devices really changed the same thing. A rejected delete coming back
 * (localExists = false under [serverWins]) is one. A local row that never synced (base revision null) has no server
 * version to have raced with: it can only meet a server row through a deterministic id such as a seed category,
 * which a fresh sign-in adopts — not a conflict (QA R6-07). Keeping a causal successor is just an unpushed edit.
 */
internal fun countsAsConflict(
    decision: PullDecision,
    serverWins: Boolean,
    localExists: Boolean,
    localDirty: Boolean,
    localBaseRevision: Long?,
    remoteRevision: Long?,
): Boolean {
    if (localExists && localBaseRevision == null) return false
    val concurrentKeep = decision == PullDecision.KEEP_LOCAL && !(remoteRevision != null && localBaseRevision == remoteRevision)
    return concurrentKeep || (serverWins && (localDirty || !localExists))
}
