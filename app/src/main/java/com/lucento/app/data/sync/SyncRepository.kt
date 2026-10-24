package com.lucento.app.data.sync

import com.lucento.app.data.local.BudgetDao
import com.lucento.app.data.local.BudgetEntity
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.data.local.GoalContributionEntity
import com.lucento.app.data.local.GoalDao
import com.lucento.app.data.local.GoalEntity
import com.lucento.app.data.local.SyncDao
import com.lucento.app.data.local.SyncTombstoneEntity
import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.WalletEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

/**
 * The whole optional cloud sync engine in one place (see docs/SYNC.md for the design). Room is always the
 * local source of truth — nothing here is on the path of adding, editing or reading a transaction offline.
 * [sync] is only ever called when the user is signed in and has turned cloud sync on, from a manual
 * "Sync now" or from [SyncWorker]'s periodic background run.
 *
 * Lifecycle per call, matching the task's required order:
 * 1. Push this device's dirty rows (`dirty = 1`) and pending tombstones, children before parents so a
 *    remote foreign key never blocks a delete.
 * 2. Pull every row changed on the server since the last successful sync, parents before children so a
 *    pulled transaction's wallet already exists locally.
 * 3. Apply pulled tombstones locally, children before parents, skipping any row this device has edited
 *    more recently than the remote delete (last-write-wins: a newer local edit beats an older remote
 *    delete and is pushed back on the next cycle instead of being silently lost).
 * 4. Advance the local sync cursor only after every step above has succeeded.
 *
 * Conflict policy: last-write-wins per row by `updated_at`, the same rule the database enforces again on
 * every push (`on conflict ... where excluded.updated_at >= table.updated_at`), so a bug here can never let
 * a stale push overwrite a newer row. A local row that is still `dirty` always wins over an incoming pull —
 * an unsynced edit is never thrown away to make room for someone else's.
 */
class SyncRepository(
    private val client: SupabaseClient?,
    private val wallets: WalletDao,
    private val categories: CategoryDao,
    private val transactions: TransactionDao,
    private val budgets: BudgetDao,
    private val goals: GoalDao,
    private val syncMeta: SyncDao,
    private val preferences: SyncPreferencesStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(): SyncResult {
        val c = client ?: return SyncResult.NotConfigured
        val userId = runCatching { c.auth.currentUserOrNull()?.id }.getOrNull() ?: return SyncResult.NotSignedIn
        val startedAt = clock()

        return try {
            var report = SyncReport()
            report += pushTombstones(c, userId)
            report += pushWallets(c, userId)
            report += pushCategories(c, userId)
            report += pushBudgets(c, userId)
            report += pushTransactions(c, userId)
            report += pushGoals(c, userId)
            report += pushGoalContributions(c, userId)

            val since = preferences.lastSyncedAtMillis() ?: 0L
            report += pullWallets(c, userId, since)
            report += pullCategories(c, userId, since)
            report += pullBudgets(c, userId, since)
            report += pullTransactions(c, userId, since)
            report += pullGoals(c, userId, since)
            report += pullGoalContributions(c, userId, since)

            // A small overlap window, not the exact "now", so a row written by another device in the same
            // instant this pull ran is never missed by the next cycle's lower bound.
            preferences.setLastSyncedAtMillis(startedAt - CLOCK_SKEW_MARGIN_MS)
            preferences.clearLastError()
            SyncResult.Success(report)
        } catch (e: Exception) {
            preferences.setLastError(e.message ?: "Sync failed")
            SyncResult.Retryable(e.message ?: "Sync failed")
        }
    }

    // --- tombstones: children before parents, so a remote FK never blocks one of these deletes ---

    private suspend fun pushTombstones(c: SupabaseClient, userId: String): SyncReport {
        val pending = syncMeta.unpushedTombstones()
        if (pending.isEmpty()) return SyncReport()
        val byTable = pending.groupBy { it.tableName }
        val order = listOf("goal_contributions", "transactions", "budgets", "goals", "categories", "wallets")
        val pushedIds = mutableListOf<Long>()
        order.forEach { table ->
            val rows = byTable[table].orEmpty()
            if (rows.isEmpty()) return@forEach
            val ids = rows.map { it.clientUuid }
            val deletedAtIso = SyncTime.toIso(clock())
            // A soft delete (tombstone), never a hard DELETE: a device that is mid-pull elsewhere must still
            // see this row exist-then-vanish, not disappear from under an in-flight read.
            // updated_at moves too: other devices pull by updated_at, so a tombstone that kept the old timestamp
            // would never reach them. The server's last-write-wins trigger accepts it because it is newer.
            c.from(table).update({
                set("deleted_at", deletedAtIso)
                set("updated_at", deletedAtIso)
            }) {
                filter {
                    eq("user_id", userId)
                    isIn("id", ids)
                }
            }
            pushedIds += rows.map { it.id }
        }
        if (pushedIds.isNotEmpty()) syncMeta.markPushed(pushedIds)
        syncMeta.pruneOldPushed(olderThan = clock() - TOMBSTONE_RETENTION_MS)
        return SyncReport(tombstonesPushed = pushedIds.size)
    }

    // --- push: this device's dirty rows, upserted with the database's own last-write-wins guard ---

    private suspend fun pushWallets(c: SupabaseClient, userId: String): SyncReport {
        val dirty = wallets.getDirty().filter { it.clientUuid != null }
        if (dirty.isEmpty()) return SyncReport()
        val rows = dirty.map {
            WalletRow(
                id = it.clientUuid!!, userId = userId, name = it.name, type = it.type,
                currencyCode = it.currencyCode, openingBalanceMinor = it.openingBalanceMinor,
                sortOrder = it.sortOrder, archived = it.archived,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt),
            )
        }
        c.from("wallets").upsert(rows) { onConflict = "id" }
        val syncedAt = clock()
        dirty.forEach { wallets.clearDirtyIfUnchanged(it.clientUuid!!, it.updatedAt, syncedAt) }
        return SyncReport(pushed = dirty.size)
    }

    private suspend fun pushCategories(c: SupabaseClient, userId: String): SyncReport {
        val dirty = categories.getDirty().filter { it.clientUuid != null }
        if (dirty.isEmpty()) return SyncReport()
        val rows = dirty.map {
            CategoryRow(
                id = it.clientUuid!!, userId = userId, name = it.name, kind = it.kind, iconKey = it.iconKey,
                sortOrder = it.sortOrder, archived = it.archived,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt),
            )
        }
        c.from("categories").upsert(rows) { onConflict = "id" }
        val syncedAt = clock()
        dirty.forEach { categories.clearDirtyIfUnchanged(it.clientUuid!!, it.updatedAt, syncedAt) }
        return SyncReport(pushed = dirty.size)
    }

    private suspend fun pushBudgets(c: SupabaseClient, userId: String): SyncReport {
        val dirty = budgets.getDirty().filter { it.clientUuid != null }
        if (dirty.isEmpty()) return SyncReport()
        val categoryUuid = dirty.mapNotNull { it.categoryId }.toSet()
            .associateWith { id -> categories.getById(id)?.clientUuid }
        val rows = dirty.map {
            BudgetRow(
                id = it.clientUuid!!, userId = userId, categoryId = it.categoryId?.let(categoryUuid::get),
                currencyCode = it.currencyCode, limitMinor = it.limitMinor,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt),
            )
        }
        c.from("budgets").upsert(rows) { onConflict = "id" }
        val syncedAt = clock()
        dirty.forEach { budgets.clearDirtyIfUnchanged(it.clientUuid!!, it.updatedAt, syncedAt) }
        return SyncReport(pushed = dirty.size)
    }

    private suspend fun pushTransactions(c: SupabaseClient, userId: String): SyncReport {
        val dirty = transactions.getDirty()
        if (dirty.isEmpty()) return SyncReport()
        val walletUuid = dirty.map { it.walletId }.toSet().associateWith { id -> wallets.getById(id)?.clientUuid }
        val categoryUuid = dirty.mapNotNull { it.categoryId }.toSet()
            .associateWith { id -> categories.getById(id)?.clientUuid }
        // A row whose wallet has no client_uuid yet (shouldn't happen post-migration, but never risk a push
        // of orphaned data) is simply retried on a later cycle, once that wallet itself has synced.
        val eligible = dirty.filter { walletUuid[it.walletId] != null }
        if (eligible.isEmpty()) return SyncReport()
        val rows = eligible.map {
            TransactionRow(
                id = it.clientUuid, userId = userId, type = it.type, amountMinor = it.amountMinor,
                currencyCode = it.currencyCode, walletId = walletUuid[it.walletId]!!,
                categoryId = it.categoryId?.let(categoryUuid::get), occurredOn = it.occurredOn, note = it.note,
                transferGroup = it.transferGroup,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt),
            )
        }
        c.from("transactions").upsert(rows) { onConflict = "id" }
        val syncedAt = clock()
        eligible.forEach { transactions.clearDirtyIfUnchanged(it.clientUuid, it.updatedAt, syncedAt) }
        return SyncReport(pushed = eligible.size)
    }

    private suspend fun pushGoals(c: SupabaseClient, userId: String): SyncReport {
        val dirty = goals.getDirtyGoals().filter { it.clientUuid != null }
        if (dirty.isEmpty()) return SyncReport()
        val walletUuid = dirty.mapNotNull { it.walletId }.toSet().associateWith { id -> wallets.getById(id)?.clientUuid }
        val rows = dirty.map {
            // photo_path is never synced (see GoalEntity doc) — only the automatic scene_key picture travels.
            GoalRow(
                id = it.clientUuid!!, userId = userId, name = it.name, targetMinor = it.targetMinor,
                currencyCode = it.currencyCode, targetDate = it.targetDate,
                walletId = it.walletId?.let(walletUuid::get), archived = it.archived, sceneKey = it.sceneKey,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt),
            )
        }
        c.from("goals").upsert(rows) { onConflict = "id" }
        val syncedAt = clock()
        dirty.forEach { goals.clearGoalDirtyIfUnchanged(it.clientUuid!!, it.updatedAt, syncedAt) }
        return SyncReport(pushed = dirty.size)
    }

    private suspend fun pushGoalContributions(c: SupabaseClient, userId: String): SyncReport {
        val dirty = goals.getDirtyContributions()
        if (dirty.isEmpty()) return SyncReport()
        val goalUuid = dirty.map { it.goalId }.toSet().associateWith { id -> goals.getById(id)?.clientUuid }
        val eligible = dirty.filter { goalUuid[it.goalId] != null }
        if (eligible.isEmpty()) return SyncReport()
        val syncedAt = clock()
        val rows = eligible.map {
            GoalContributionRowDto(
                id = it.clientUuid, userId = userId, goalId = goalUuid[it.goalId]!!, amountMinor = it.amountMinor,
                occurredOn = it.occurredOn, note = it.note,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.createdAt),
            )
        }
        c.from("goal_contributions").upsert(rows) { onConflict = "id" }
        eligible.forEach { goals.clearContributionDirty(it.clientUuid, syncedAt) }
        return SyncReport(pushed = eligible.size)
    }

    // --- pull: rows changed remotely since the last successful sync, parents before children ---

    private suspend fun pullWallets(c: SupabaseClient, userId: String, since: Long): SyncReport {
        val remote = fetchChanged<WalletRow>(c, "wallets", userId, since)
        var pulled = 0
        var conflicts = 0
        var deletedLocally = 0
        remote.forEach { row ->
            val local = wallets.findByClientUuid(row.id)
            if (row.deletedAt != null) {
                if (local != null && !isLocalNewerThanDeletion(local.updatedAt, local.dirty, row.deletedAt)) {
                    wallets.deleteById(local.id); deletedLocally++
                } else if (local != null) conflicts++
                return@forEach
            }
            val remoteUpdatedAt = SyncTime.fromIso(row.updatedAt)
            when {
                local == null -> {
                    wallets.insert(row.toEntity(remoteUpdatedAt, syncedAt = clock())); pulled++
                }
                local.dirty && local.updatedAt > remoteUpdatedAt -> conflicts++ // kept local; will push next cycle
                remoteUpdatedAt > local.updatedAt -> {
                    wallets.update(row.toEntity(remoteUpdatedAt, syncedAt = clock(), id = local.id)); pulled++
                }
            }
        }
        return SyncReport(pulled = pulled, conflictsKeptLocal = conflicts, deletedLocally = deletedLocally)
    }

    private suspend fun pullCategories(c: SupabaseClient, userId: String, since: Long): SyncReport {
        val remote = fetchChanged<CategoryRow>(c, "categories", userId, since)
        var pulled = 0; var conflicts = 0; var deletedLocally = 0
        remote.forEach { row ->
            val local = categories.findByClientUuid(row.id)
            if (row.deletedAt != null) {
                if (local != null && !isLocalNewerThanDeletion(local.updatedAt, local.dirty, row.deletedAt)) {
                    categories.deleteById(local.id); deletedLocally++
                } else if (local != null) conflicts++
                return@forEach
            }
            val remoteUpdatedAt = SyncTime.fromIso(row.updatedAt)
            when {
                local == null -> { categories.insert(row.toEntity(remoteUpdatedAt)); pulled++ }
                local.dirty && local.updatedAt > remoteUpdatedAt -> conflicts++
                remoteUpdatedAt > local.updatedAt -> { categories.update(row.toEntity(remoteUpdatedAt, id = local.id)); pulled++ }
            }
        }
        return SyncReport(pulled = pulled, conflictsKeptLocal = conflicts, deletedLocally = deletedLocally)
    }

    private suspend fun pullBudgets(c: SupabaseClient, userId: String, since: Long): SyncReport {
        val remote = fetchChanged<BudgetRow>(c, "budgets", userId, since)
        var pulled = 0; var conflicts = 0; var deletedLocally = 0
        remote.forEach { row ->
            val local = budgets.findByClientUuid(row.id)
            if (row.deletedAt != null) {
                if (local != null && !isLocalNewerThanDeletion(local.updatedAt, local.dirty, row.deletedAt)) {
                    budgets.deleteById(local.id); deletedLocally++
                } else if (local != null) conflicts++
                return@forEach
            }
            val localCategoryId = row.categoryId?.let { categories.findByClientUuid(it)?.id }
            if (row.categoryId != null && localCategoryId == null) return@forEach // category not synced yet; retry later
            val remoteUpdatedAt = SyncTime.fromIso(row.updatedAt)
            when {
                local == null -> { budgets.insert(row.toEntity(remoteUpdatedAt, localCategoryId)); pulled++ }
                local.dirty && local.updatedAt > remoteUpdatedAt -> conflicts++
                remoteUpdatedAt > local.updatedAt -> { budgets.update(row.toEntity(remoteUpdatedAt, localCategoryId, id = local.id)); pulled++ }
            }
        }
        return SyncReport(pulled = pulled, conflictsKeptLocal = conflicts, deletedLocally = deletedLocally)
    }

    private suspend fun pullTransactions(c: SupabaseClient, userId: String, since: Long): SyncReport {
        val remote = fetchChanged<TransactionRow>(c, "transactions", userId, since)
        var pulled = 0; var conflicts = 0; var deletedLocally = 0
        remote.forEach { row ->
            val local = transactions.findByClientUuid(row.id)
            if (row.deletedAt != null) {
                if (local != null && !isLocalNewerThanDeletion(local.updatedAt, local.dirty, row.deletedAt)) {
                    transactions.deleteById(local.id); deletedLocally++
                } else if (local != null) conflicts++
                return@forEach
            }
            val localWalletId = wallets.findByClientUuid(row.walletId)?.id
            if (localWalletId == null) return@forEach // wallet not synced yet; retry next cycle
            val localCategoryId = row.categoryId?.let { categories.findByClientUuid(it)?.id }
            val remoteUpdatedAt = SyncTime.fromIso(row.updatedAt)
            when {
                local == null -> { transactions.insert(row.toEntity(remoteUpdatedAt, localWalletId, localCategoryId)); pulled++ }
                local.dirty && local.updatedAt > remoteUpdatedAt -> conflicts++
                remoteUpdatedAt > local.updatedAt -> {
                    transactions.update(row.toEntity(remoteUpdatedAt, localWalletId, localCategoryId, id = local.id)); pulled++
                }
            }
        }
        return SyncReport(pulled = pulled, conflictsKeptLocal = conflicts, deletedLocally = deletedLocally)
    }

    private suspend fun pullGoals(c: SupabaseClient, userId: String, since: Long): SyncReport {
        val remote = fetchChanged<GoalRow>(c, "goals", userId, since)
        var pulled = 0; var conflicts = 0; var deletedLocally = 0
        remote.forEach { row ->
            val local = goals.findGoalByClientUuid(row.id)
            if (row.deletedAt != null) {
                if (local != null && !isLocalNewerThanDeletion(local.updatedAt, local.dirty, row.deletedAt)) {
                    goals.deleteById(local.id); deletedLocally++
                } else if (local != null) conflicts++
                return@forEach
            }
            val localWalletId = row.walletId?.let { wallets.findByClientUuid(it)?.id }
            val remoteUpdatedAt = SyncTime.fromIso(row.updatedAt)
            when {
                // photoPath is never pulled from the cloud — it only ever names a file on the device that made it.
                local == null -> { goals.insert(row.toEntity(remoteUpdatedAt, localWalletId, photoPath = null)); pulled++ }
                local.dirty && local.updatedAt > remoteUpdatedAt -> conflicts++
                remoteUpdatedAt > local.updatedAt -> {
                    goals.update(row.toEntity(remoteUpdatedAt, localWalletId, photoPath = local.photoPath, id = local.id)); pulled++
                }
            }
        }
        return SyncReport(pulled = pulled, conflictsKeptLocal = conflicts, deletedLocally = deletedLocally)
    }

    private suspend fun pullGoalContributions(c: SupabaseClient, userId: String, since: Long): SyncReport {
        val remote = fetchChanged<GoalContributionRowDto>(c, "goal_contributions", userId, since)
        var pulled = 0; var deletedLocally = 0
        remote.forEach { row ->
            if (row.deletedAt != null) {
                // Contributions are immutable once created, so there is no "local edit beats remote delete" case.
                return@forEach
            }
            val localGoalId = goals.findGoalByClientUuid(row.goalId)?.id ?: return@forEach
            if (goals.getById(localGoalId) != null) {
                val exists = runCatching { goals.findContributionByClientUuid(row.id) }.getOrNull()
                if (exists == null) {
                    goals.insertContribution(
                        GoalContributionEntity(
                            goalId = localGoalId, amountMinor = row.amountMinor, occurredOn = row.occurredOn,
                            note = row.note, clientUuid = row.id, createdAt = SyncTime.fromIso(row.createdAt),
                            dirty = false, syncedAt = clock(),
                        )
                    )
                    pulled++
                }
            }
        }
        return SyncReport(pulled = pulled, deletedLocally = deletedLocally)
    }

    private fun isLocalNewerThanDeletion(localUpdatedAt: Long, localDirty: Boolean, remoteDeletedAtIso: String): Boolean =
        localWinsOverRemoteDelete(localUpdatedAt, localDirty, SyncTime.fromIso(remoteDeletedAtIso))

    private suspend inline fun <reified T : Any> fetchChanged(
        c: SupabaseClient,
        table: String,
        userId: String,
        since: Long,
    ): List<T> = c.from(table).select(columns = Columns.ALL) {
        filter {
            eq("user_id", userId)
            gte("updated_at", SyncTime.toIso(since))
        }
        order("updated_at", Order.ASCENDING)
        limit(PULL_PAGE_SIZE)
    }.decodeList()

    private companion object {
        const val PULL_PAGE_SIZE = 1000L
        const val CLOCK_SKEW_MARGIN_MS = 5_000L
        const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

private fun WalletRow.toEntity(updatedAtMillis: Long, syncedAt: Long, id: Long = 0): WalletEntity = WalletEntity(
    id = id, name = name, type = type, currencyCode = currencyCode, openingBalanceMinor = openingBalanceMinor,
    sortOrder = sortOrder, archived = archived, createdAt = SyncTime.fromIso(createdAt), updatedAt = updatedAtMillis,
    clientUuid = this.id, dirty = false, syncedAt = syncedAt,
)

private fun CategoryRow.toEntity(updatedAtMillis: Long, id: Long = 0): CategoryEntity = CategoryEntity(
    id = id, name = name, kind = kind, iconKey = iconKey, sortOrder = sortOrder, archived = archived,
    createdAt = SyncTime.fromIso(createdAt), updatedAt = updatedAtMillis, clientUuid = this.id, dirty = false,
    syncedAt = updatedAtMillis,
)

private fun BudgetRow.toEntity(updatedAtMillis: Long, localCategoryId: Long?, id: Long = 0): BudgetEntity = BudgetEntity(
    id = id, categoryId = localCategoryId, currencyCode = currencyCode, limitMinor = limitMinor,
    createdAt = SyncTime.fromIso(createdAt), updatedAt = updatedAtMillis, clientUuid = this.id, dirty = false,
    syncedAt = updatedAtMillis,
)

private fun TransactionRow.toEntity(
    updatedAtMillis: Long,
    localWalletId: Long,
    localCategoryId: Long?,
    id: Long = 0,
): TransactionEntity = TransactionEntity(
    id = id, type = type, amountMinor = amountMinor, currencyCode = currencyCode, walletId = localWalletId,
    categoryId = localCategoryId, occurredOn = occurredOn, note = note, clientUuid = this.id,
    createdAt = SyncTime.fromIso(createdAt), updatedAt = updatedAtMillis, transferGroup = transferGroup,
    dirty = false, syncedAt = updatedAtMillis,
)

private fun GoalRow.toEntity(updatedAtMillis: Long, localWalletId: Long?, photoPath: String?, id: Long = 0): GoalEntity = GoalEntity(
    id = id, name = name, targetMinor = targetMinor, currencyCode = currencyCode, targetDate = targetDate,
    walletId = localWalletId, archived = archived, createdAt = SyncTime.fromIso(createdAt), updatedAt = updatedAtMillis,
    sceneKey = sceneKey, photoPath = photoPath, clientUuid = this.id, dirty = false, syncedAt = updatedAtMillis,
)
