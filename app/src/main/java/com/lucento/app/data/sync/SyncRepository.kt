package com.lucento.app.data.sync

import com.lucento.app.data.local.BudgetDao
import com.lucento.app.data.local.BudgetEntity
import com.lucento.app.domain.CategoryDedup
import com.lucento.app.data.local.CategoryDao
import com.lucento.app.data.local.CategoryEntity
import com.lucento.app.data.local.DebtTermsDao
import com.lucento.app.data.local.DebtTermsEntity
import com.lucento.app.data.local.GoalContributionEntity
import com.lucento.app.data.local.GoalDao
import com.lucento.app.data.local.GoalEntity
import com.lucento.app.data.local.RecurringDao
import com.lucento.app.data.local.RecurringEntity
import com.lucento.app.data.local.SyncDao
import com.lucento.app.data.local.TransactionDao
import com.lucento.app.data.local.TransactionEntity
import com.lucento.app.data.local.WalletDao
import com.lucento.app.data.local.WalletEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.UUID

/**
 * The whole optional cloud sync engine (see docs/SYNC.md). Room is always the local source of truth — nothing
 * here is on the path of adding, editing or reading anything offline. [sync] runs only for a signed-in user who
 * turned sync on, from "Sync now" or [SyncWorker].
 *
 * One cycle:
 * 1. Resolve the signed-in user (after the stored session has loaded) and refuse to mix accounts on one device.
 * 2. Give any row still lacking a stable id one (fresh-install seed categories).
 * 3. Push tombstones (children first) then dirty rows (parents first), each carrying the server revision it was based
 *    on. The server accepts a causal successor outright and a concurrent edit only if it is the later edit; it reports
 *    what it accepted, so accepted rows record their new revision and rejected ones are resolved below.
 * 4. Pull, in keyset pages, every row the **server** stamped since the cursor — parents before children. Rows whose
 *    push or delete the server rejected are fetched by id first and replaced by the server's copy (a rejected delete
 *    therefore brings the newer edit back). A child whose wallet, category or goal is already gone is kept by
 *    restoring that parent locally (or an Uncategorized category); it is never dropped and never holds the cursor.
 *    Remote deletions are applied last, children first; one that a local foreign key still blocks is kept and counted
 *    as a conflict instead of failing the sync.
 * 5. Save the new cursor only after all of that succeeded. Any failure leaves the cursor, every dirty flag and
 *    every local row exactly as they were, so the next cycle simply repeats the work.
 *
 * Conflicts: [localEditWins] / [pullDecision]; the server applies the same rule on every write.
 */
class SyncRepository(
    private val remote: SyncRemote?,
    private val wallets: WalletDao,
    private val categories: CategoryDao,
    private val transactions: TransactionDao,
    private val budgets: BudgetDao,
    private val goals: GoalDao,
    private val syncMeta: SyncDao,
    private val preferences: SyncPreferencesStore,
    /** Recurring schedules (v9); null in tests that don't exercise them. */
    private val recurring: RecurringDao? = null,
    /** Debt terms, carried on their wallet's row (v9); null in tests that don't exercise them. */
    private val debtTerms: DebtTermsDao? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pageSize: Long = PULL_PAGE_SIZE,
) {
    /** How many local changes are waiting for the next sync (rows and deletions); read by the Home header. */
    fun observePendingChanges(): Flow<Int> = syncMeta.observePendingChanges()

    /**
     * Dirty rows plus deletions not yet pushed. The same total the Home header watches.
     * Sign-out uses it before wiping the phone.
     */
    suspend fun pendingChangeCount(): Int = observePendingChanges().first()

    /** True when a sign-out or account switch would drop changes that have not reached the server. */
    suspend fun hasUnsyncedChanges(): Boolean = pendingChangeCount() > 0

    suspend fun sync(): SyncResult = SyncFlight.exclusive { syncBody() }

    private suspend fun syncBody(): SyncResult {
        val r = remote ?: return SyncResult.NotConfigured
        val userId = try {
            r.currentUserId()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return fail(e)
        } ?: return SyncResult.NotSignedIn

        val owner = preferences.syncedUserId()
        if (owner != null && owner != userId) {
            preferences.setLastError(ACCOUNT_MISMATCH_MESSAGE)
            return SyncResult.AccountMismatch
        }

        return try {
            // Claimed before the first push: once any row has reached this account, no other account may follow.
            if (owner == null) preferences.setSyncedUserId(userId)
            assignMissingCategoryIds(userId)

            val rejected = Rejected()
            var report = pushTombstones(r, userId, rejected)
            report += pushWallets(r, userId, rejected)
            report += pushCategories(r, userId, rejected)
            report += pushBudgets(r, userId, rejected)
            report += pushTransactions(r, userId, rejected)
            report += pushRecurring(r, userId, rejected)
            report += pushGoals(r, userId, rejected)
            report += pushGoalContributions(r, userId, rejected)

            val pulled = pull(r, userId, rejected)
            report += pulled
            // A same-moment local edit was kept and nudged later; send it now so this cycle converges.
            if (pulled.pendingKept > 0) {
                val again = Rejected()
                report += pushTombstones(r, userId, again)
                report += pushWallets(r, userId, again)
                report += pushCategories(r, userId, again)
                report += pushBudgets(r, userId, again)
                report += pushTransactions(r, userId, again)
                report += pushRecurring(r, userId, again)
                report += pushGoals(r, userId, again)
                report += pushGoalContributions(r, userId, again)
            }
            // Copies of one category from two setups on this account fold into one; the merge's edits go up now.
            if (mergeDuplicateCategories() > 0) {
                val again = Rejected()
                report += pushTombstones(r, userId, again)
                report += pushBudgets(r, userId, again)
                report += pushTransactions(r, userId, again)
                report += pushRecurring(r, userId, again)
            }
            preferences.setLastSyncedAtMillis(clock())
            preferences.clearLastError()
            SyncResult.Success(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    private suspend fun fail(e: Exception): SyncResult.Retryable {
        // Stored and shown in Settings: never the raw message, which can carry request headers (see safeFailureDescription).
        val message = safeFailureDescription(e)
        preferences.setLastError(message)
        return SyncResult.Retryable(message)
    }

    /** Writes the server refused this cycle, by table; resolved at the start of that table's pull. */
    private class Rejected {
        val ids = mutableMapOf<String, MutableSet<String>>()
        fun add(table: String, id: String) = ids.getOrPut(table) { mutableSetOf() }.add(id)
    }

    // --- stable ids ---

    /**
     * Fresh-install seed categories are inserted by CategorySeed, which must stay valid at every schema version
     * and so cannot set `client_uuid`. They get a deterministic per-account id: the same seed on two devices of one
     * account then upserts into one cloud row instead of appearing twice.
     */
    private suspend fun assignMissingCategoryIds(userId: String) {
        categories.getWithoutClientUuid().forEach { category ->
            val slug = category.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            val seedId = "seed-$userId-${category.kind.lowercase()}-$slug"
            val id = if (categories.findByClientUuid(seedId) == null) seedId else UUID.randomUUID().toString()
            categories.assignClientUuid(category.id, id)
        }
    }

    /**
     * Folds duplicate categories ([CategoryDedup]): each copy's transactions, recurring schedules and budget amount move
     * to the keeper, then the copy is deleted — its tombstone tells the other devices. Returns how many copies merged.
     */
    private suspend fun mergeDuplicateCategories(): Int {
        val plan = CategoryDedup.plan(categories.getAll().map { CategoryDedup.Candidate(it.id, it.kind, it.name, it.archived, it.clientUuid) })
        if (plan.isEmpty()) return 0
        val now = clock()
        plan.forEach { (copy, keeper) ->
            categories.moveTransactions(copy, keeper, now)
            categories.moveRecurring(copy, keeper, now)
            budgets.getForCategory(copy).forEach { moved ->
                val existing = budgets.findAllocation(keeper, moved.currencyCode)
                if (existing == null) {
                    budgets.update(moved.copy(categoryId = keeper, updatedAt = now))
                } else {
                    val limit = CategoryDedup.mergedLimit(existing.limitMinor, moved.limitMinor)
                    if (limit != existing.limitMinor) budgets.update(existing.copy(limitMinor = limit, updatedAt = now))
                    budgets.deleteById(moved.id)
                }
            }
            categories.deleteById(copy)
        }
        return plan.size
    }

    // --- push ---

    private suspend fun pushTombstones(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val pending = syncMeta.unpushedTombstones()
        if (pending.isEmpty()) return SyncReport()
        val byTable = pending.groupBy { it.tableName }
        val done = mutableListOf<Long>()
        var accepted = 0
        CHILD_FIRST.forEach { table ->
            byTable[table].orEmpty().forEach { t ->
                if (r.softDelete(table, userId, t.clientUuid, t.baseRevision, SyncTime.toIso(clock()))) {
                    accepted++
                } else {
                    // A newer concurrent edit exists remotely (or the row never reached the server). The pull fetches
                    // the server copy: an edit that beat this delete comes back; a row that never existed stays gone.
                    rejected.add(table, t.clientUuid)
                }
                done += t.id
            }
        }
        syncMeta.markPushed(done)
        syncMeta.pruneOldPushed(olderThan = clock() - TOMBSTONE_RETENTION_MS)
        return SyncReport(tombstonesPushed = accepted)
    }

    /**
     * Upserts [rows] and records, for each one the server accepted, its new revision as the row's base. A row the server
     * left unchanged stays dirty and is resolved from the server's copy during the pull.
     */
    private suspend fun <E> pushRows(
        r: SyncRemote,
        table: String,
        rows: List<E>,
        idOf: (E) -> String,
        encode: (E) -> JsonObject,
        markPushed: suspend (E, Long) -> Unit,
        rejected: Rejected,
    ): SyncReport {
        if (rows.isEmpty()) return SyncReport()
        val acceptedRevisions = r.upsert(table, rows.map(encode)).associate { it.str("id") to it.revisionOrNull() }
        var pushed = 0
        rows.forEach { row ->
            val revision = acceptedRevisions[idOf(row)]
            if (revision != null) {
                markPushed(row, revision)
                pushed++
            } else {
                rejected.add(table, idOf(row))
            }
        }
        return SyncReport(pushed = pushed)
    }

    private suspend fun pushWallets(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val dirty = wallets.getDirty().filter { it.clientUuid != null }
        // A debt's terms ride on its wallet row; WalletRepository bumps the wallet's updated_at whenever they change.
        val terms = dirty.associate { it.id to debtTerms?.get(it.id) }
        return pushRows(r, "wallets", dirty, { it.clientUuid!! }, {
            val t = terms[it.id]
            encode(WalletRow.serializer(), WalletRow(
                id = it.clientUuid!!, userId = userId, name = it.name, type = it.type,
                currencyCode = it.currencyCode, openingBalanceMinor = it.openingBalanceMinor,
                sortOrder = it.sortOrder, archived = it.archived,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt), baseRevision = it.baseRevision,
                interestRateBps = t?.interestRateBps, repayment = t?.repayment, termMonths = t?.termMonths,
                installmentMinor = t?.installmentMinor,
            ))
        }, { row, rev -> wallets.markPushed(row.clientUuid!!, row.updatedAt, rev, clock()) }, rejected)
    }

    private suspend fun pushCategories(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val dirty = categories.getDirty().filter { it.clientUuid != null }
        return pushRows(r, "categories", dirty, { it.clientUuid!! }, {
            encode(CategoryRow.serializer(), CategoryRow(
                id = it.clientUuid!!, userId = userId, name = it.name, kind = it.kind, iconKey = it.iconKey,
                sortOrder = it.sortOrder, archived = it.archived,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt), baseRevision = it.baseRevision,
            ))
        }, { row, rev -> categories.markPushed(row.clientUuid!!, row.updatedAt, rev, clock()) }, rejected)
    }

    private suspend fun pushBudgets(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val dirty = budgets.getDirty().filter { it.clientUuid != null }
        val categoryUuid = dirty.mapNotNull { it.categoryId }.toSet().associateWith { categories.getById(it)?.clientUuid }
        // A budget whose category has no stable id yet waits for a later cycle rather than losing its category.
        val eligible = dirty.filter { it.categoryId == null || categoryUuid[it.categoryId] != null }
        return pushRows(r, "budgets", eligible, { it.clientUuid!! }, {
            encode(BudgetRow.serializer(), BudgetRow(
                id = it.clientUuid!!, userId = userId, categoryId = it.categoryId?.let(categoryUuid::get),
                currencyCode = it.currencyCode, limitMinor = it.limitMinor,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt), baseRevision = it.baseRevision,
            ))
        }, { row, rev -> budgets.markPushed(row.clientUuid!!, row.updatedAt, rev, clock()) }, rejected)
    }

    private suspend fun pushTransactions(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val dirty = transactions.getDirty()
        val walletUuid = dirty.map { it.walletId }.toSet().associateWith { wallets.getById(it)?.clientUuid }
        val categoryUuid = dirty.mapNotNull { it.categoryId }.toSet().associateWith { categories.getById(it)?.clientUuid }
        // Never push a transaction whose wallet or category can't be referenced yet: it would arrive orphaned or
        // uncategorised elsewhere. It stays dirty and goes out once its parent has a stable id.
        val eligible = dirty.filter { walletUuid[it.walletId] != null && (it.categoryId == null || categoryUuid[it.categoryId] != null) }
        return pushRows(r, "transactions", eligible, { it.clientUuid }, {
            encode(TransactionRow.serializer(), TransactionRow(
                id = it.clientUuid, userId = userId, type = it.type, amountMinor = it.amountMinor,
                currencyCode = it.currencyCode, walletId = walletUuid[it.walletId]!!,
                categoryId = it.categoryId?.let(categoryUuid::get), occurredOn = it.occurredOn, note = it.note,
                transferGroup = it.transferGroup,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt), baseRevision = it.baseRevision,
            ))
        }, { row, rev -> transactions.markPushed(row.clientUuid, row.updatedAt, rev, clock()) }, rejected)
    }

    private suspend fun pushRecurring(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val dao = recurring ?: return SyncReport()
        val dirty = dao.getDirty().filter { it.clientUuid != null }
        val walletIds = dirty.flatMap { listOfNotNull(it.walletId, it.toWalletId) }.toSet()
        val walletUuid = walletIds.associateWith { wallets.getById(it)?.clientUuid }
        val categoryUuid = dirty.mapNotNull { it.categoryId }.toSet().associateWith { categories.getById(it)?.clientUuid }
        // Like a transaction, a schedule waits until every wallet and category it points to has a stable id.
        val eligible = dirty.filter {
            walletUuid[it.walletId] != null && (it.toWalletId == null || walletUuid[it.toWalletId] != null) &&
                (it.categoryId == null || categoryUuid[it.categoryId] != null)
        }
        return pushRows(r, "recurring", eligible, { it.clientUuid!! }, {
            encode(RecurringRow.serializer(), RecurringRow(
                id = it.clientUuid!!, userId = userId, name = it.name, type = it.type, amountMinor = it.amountMinor,
                currencyCode = it.currencyCode, walletId = walletUuid[it.walletId]!!,
                categoryId = it.categoryId?.let(categoryUuid::get), frequency = it.frequency, intervalCount = it.intervalCount,
                startOn = it.startOn, endOn = it.endOn, nextIndex = it.nextIndex, paused = it.paused,
                toWalletId = it.toWalletId?.let(walletUuid::get), interestMinor = it.interestMinor,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt), baseRevision = it.baseRevision,
            ))
        }, { row, rev -> dao.markPushed(row.clientUuid!!, row.updatedAt, rev, clock()) }, rejected)
    }

    private suspend fun pushGoals(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val dirty = goals.getDirtyGoals().filter { it.clientUuid != null }
        val walletUuid = dirty.mapNotNull { it.walletId }.toSet().associateWith { wallets.getById(it)?.clientUuid }
        return pushRows(r, "goals", dirty, { it.clientUuid!! }, {
            // photo_path is never synced (see GoalEntity) — only the automatic scene_key picture travels.
            encode(GoalRow.serializer(), GoalRow(
                id = it.clientUuid!!, userId = userId, name = it.name, targetMinor = it.targetMinor,
                currencyCode = it.currencyCode, targetDate = it.targetDate,
                walletId = it.walletId?.let(walletUuid::get), archived = it.archived, sceneKey = it.sceneKey,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.updatedAt), baseRevision = it.baseRevision,
            ))
        }, { row, rev -> goals.markGoalPushed(row.clientUuid!!, row.updatedAt, rev, clock()) }, rejected)
    }

    private suspend fun pushGoalContributions(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val dirty = goals.getDirtyContributions()
        val goalUuid = dirty.map { it.goalId }.toSet().associateWith { goals.getById(it)?.clientUuid }
        val eligible = dirty.filter { goalUuid[it.goalId] != null }
        return pushRows(r, "goal_contributions", eligible, { it.clientUuid }, {
            encode(GoalContributionRowDto.serializer(), GoalContributionRowDto(
                id = it.clientUuid, userId = userId, goalId = goalUuid[it.goalId]!!, amountMinor = it.amountMinor,
                occurredOn = it.occurredOn, note = it.note,
                createdAt = SyncTime.toIso(it.createdAt), updatedAt = SyncTime.toIso(it.createdAt), baseRevision = it.baseRevision,
            ))
        }, { row, rev -> goals.markContributionPushed(row.clientUuid, rev, clock()) }, rejected)
    }

    // --- pull ---

    /** A remote deletion found during the pull; applied only after every upsert, children first. */
    private class PendingDelete(val table: String, val clientUuid: String?, val delete: suspend () -> Unit)

    private class PullState(val sinceMillis: Long) {
        var newestSeen: Long? = null
        var oldestDeferred: Long? = null
        var pulled = 0
        var conflicts = 0
        var parentsRestored = 0
        var pendingKept = 0
        val deletes = mutableListOf<PendingDelete>()
        /** Soft-deleted parents seen in this pull, including ones this phone never stored. */
        val deletedWallets = mutableMapOf<String, WalletRow>()
        val deletedCategories = mutableMapOf<String, CategoryRow>()
        val deletedGoals = mutableMapOf<String, GoalRow>()

        fun dropQueuedDelete(table: String, clientUuid: String) {
            deletes.removeAll { it.table == table && it.clientUuid == clientUuid }
        }

        fun seen(row: JsonObject) {
            val at = row.serverMillis() ?: return
            newestSeen = maxOf(newestSeen ?: at, at)
        }

        /** The row's parent hasn't arrived; the cursor must not move past it, so it is fetched again next cycle. */
        fun defer(row: JsonObject) {
            val at = row.serverMillis() ?: sinceMillis
            oldestDeferred = minOf(oldestDeferred ?: at, at)
        }
    }

    private suspend fun pull(r: SyncRemote, userId: String, rejected: Rejected): SyncReport {
        val since = preferences.pullCursorIso()?.let(SyncTime::fromIso) ?: 0L
        val state = PullState(since)
        val sinceIso = SyncTime.toIso(since)

        PARENT_FIRST.forEach { table ->
            // The server's copy of anything it refused this cycle is authoritative: this device's edit (or delete)
            // lost under the same rule both sides apply, so it is replaced rather than retried forever.
            rejected.ids[table]?.takeIf { it.isNotEmpty() }?.let { ids ->
                r.fetchByIds(table, userId, ids.toList()).forEach { row ->
                    applyPulled(table, row, state, serverWins = true)
                }
            }
            var after: SyncRemote.PageKey? = null
            while (true) {
                val page = r.fetchPage(table, userId, sinceIso, after, pageSize)
                page.forEach { row ->
                    state.seen(row)
                    applyPulled(table, row, state, serverWins = false)
                }
                if (page.size < pageSize) break
                val last = page.last()
                after = SyncRemote.PageKey(last.str(SupabaseSyncRemote.SERVER_UPDATED_AT), last.str("id"))
            }
        }

        var deletedLocally = 0
        CHILD_FIRST.forEach { table ->
            state.deletes.filter { it.table == table }.forEach { pending ->
                // A delete the local schema still refuses (e.g. this device added a transaction to a wallet another
                // device deleted) is kept: the data survives here and the conflict is reported, not thrown.
                if (runCatching { pending.delete() }.isSuccess) deletedLocally++ else state.conflicts++
            }
        }

        preferences.setPullCursorIso(
            SyncTime.toIso(nextPullCursor(since, state.newestSeen, state.oldestDeferred, CURSOR_OVERLAP_MS))
        )
        return SyncReport(
            pulled = state.pulled,
            conflicts = state.conflicts,
            deletedLocally = deletedLocally,
            parentsRestored = state.parentsRestored,
            pendingKept = state.pendingKept,
        )
    }

    /**
     * Applies one pulled row. With [serverWins] (the server refused this device's write) the server copy wins,
     * except a pending local edit that is not older than that copy — a same-moment edit is not overwritten.
     */
    private suspend fun applyPulled(table: String, json: JsonObject, s: PullState, serverWins: Boolean) {
        val revision = json.revisionOrNull()
        fun decide(localExists: Boolean, dirty: Boolean, base: Long?, updatedAt: Long, remoteUpdatedAtIso: String): PullDecision {
            val remoteAt = SyncTime.fromIso(remoteUpdatedAtIso)
            val decision = when {
                serverWins && localExists && dirty && protectsPendingEdit(base, updatedAt, revision, remoteAt) -> PullDecision.KEEP_LOCAL
                serverWins -> if (localExists) PullDecision.UPDATE else PullDecision.INSERT
                else -> pullDecision(localExists, dirty, base, updatedAt, revision, remoteAt)
            }
            if (countsAsConflict(decision, serverWins, localExists, dirty, base, revision)) s.conflicts++
            return decision
        }
        when (table) {
            "wallets" -> {
                val row = decode(WalletRow.serializer(), json)
                val local = wallets.findByClientUuid(row.id)
                if (row.deletedAt != null) {
                    s.deletedWallets[row.id] = row
                    return queueDelete(s, table, row.id, local?.updatedAt, local?.dirty, local?.baseRevision, row.deletedAt, serverWins) { wallets.deleteById(local!!.id) }
                }
                val walletId = when (decide(local != null, local?.dirty == true, local?.baseRevision, local?.updatedAt ?: 0, row.updatedAt)) {
                    PullDecision.INSERT -> wallets.insert(row.toEntity(syncedAt = clock()))
                    PullDecision.UPDATE -> {
                        if (local != null && holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            wallets.update(local.copy(updatedAt = at, dirty = true))
                        }) return
                        local!!.id.also { wallets.update(row.toEntity(syncedAt = freshSyncedAt(local.syncedAt), id = it)) }
                    }
                    PullDecision.KEEP_LOCAL -> {
                        if (local != null) holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            wallets.update(local.copy(updatedAt = at, dirty = true))
                        }
                        return
                    }
                    PullDecision.UNCHANGED -> return
                }
                applyDebtTerms(walletId, row)
                s.pulled++
            }
            "categories" -> {
                val row = decode(CategoryRow.serializer(), json)
                val local = categories.findByClientUuid(row.id)
                if (row.deletedAt != null) {
                    s.deletedCategories[row.id] = row
                    return queueDelete(s, table, row.id, local?.updatedAt, local?.dirty, local?.baseRevision, row.deletedAt, serverWins) { categories.deleteById(local!!.id) }
                }
                when (decide(local != null, local?.dirty == true, local?.baseRevision, local?.updatedAt ?: 0, row.updatedAt)) {
                    PullDecision.INSERT -> categories.insert(row.toEntity(syncedAt = clock()))
                    PullDecision.UPDATE -> {
                        if (local != null && holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            categories.update(local.copy(updatedAt = at, dirty = true))
                        }) return
                        categories.update(row.toEntity(syncedAt = freshSyncedAt(local!!.syncedAt), id = local.id))
                    }
                    PullDecision.KEEP_LOCAL -> {
                        if (local != null) holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            categories.update(local.copy(updatedAt = at, dirty = true))
                        }
                        return
                    }
                    PullDecision.UNCHANGED -> return
                }
                s.pulled++
            }
            "budgets" -> {
                val row = decode(BudgetRow.serializer(), json)
                val local = budgets.findByClientUuid(row.id)
                if (row.deletedAt != null) return queueDelete(s, table, row.id, local?.updatedAt, local?.dirty, local?.baseRevision, row.deletedAt, serverWins) { budgets.deleteById(local!!.id) }
                val categoryId = resolveCategory(row.categoryId, "EXPENSE", s)
                when (decide(local != null, local?.dirty == true, local?.baseRevision, local?.updatedAt ?: 0, row.updatedAt)) {
                    PullDecision.INSERT -> budgets.insert(row.toEntity(categoryId, syncedAt = clock()))
                    PullDecision.UPDATE -> {
                        if (local != null && holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            budgets.update(local.copy(updatedAt = at, dirty = true))
                        }) return
                        budgets.update(row.toEntity(categoryId, syncedAt = freshSyncedAt(local!!.syncedAt), id = local.id))
                    }
                    PullDecision.KEEP_LOCAL -> {
                        if (local != null) holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            budgets.update(local.copy(updatedAt = at, dirty = true))
                        }
                        return
                    }
                    PullDecision.UNCHANGED -> return
                }
                s.pulled++
            }
            "transactions" -> {
                val row = decode(TransactionRow.serializer(), json)
                val local = transactions.findByClientUuid(row.id)
                if (row.deletedAt != null) return queueDelete(s, table, row.id, local?.updatedAt, local?.dirty, local?.baseRevision, row.deletedAt, serverWins) { transactions.deleteById(local!!.id) }
                val walletId = resolveWallet(row.walletId, row.currencyCode, s)
                val categoryId = resolveCategory(row.categoryId, if (row.type == "INCOME") "INCOME" else "EXPENSE", s)
                when (decide(local != null, local?.dirty == true, local?.baseRevision, local?.updatedAt ?: 0, row.updatedAt)) {
                    PullDecision.INSERT -> transactions.insert(row.toEntity(walletId, categoryId, syncedAt = clock()))
                    PullDecision.UPDATE -> {
                        if (local != null && holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            transactions.update(local.copy(updatedAt = at, dirty = true))
                        }) return
                        transactions.update(row.toEntity(walletId, categoryId, syncedAt = freshSyncedAt(local!!.syncedAt), id = local.id))
                    }
                    PullDecision.KEEP_LOCAL -> {
                        if (local != null) holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            transactions.update(local.copy(updatedAt = at, dirty = true))
                        }
                        return
                    }
                    PullDecision.UNCHANGED -> return
                }
                s.pulled++
            }
            "recurring" -> {
                val dao = recurring ?: return
                val row = decode(RecurringRow.serializer(), json)
                val local = dao.findByClientUuid(row.id)
                if (row.deletedAt != null) return queueDelete(s, table, row.id, local?.updatedAt, local?.dirty, local?.baseRevision, row.deletedAt, serverWins) { dao.deleteById(local!!.id) }
                val walletId = resolveWallet(row.walletId, row.currencyCode, s)
                val toWalletId = row.toWalletId?.let { resolveWallet(it, row.currencyCode, s) }
                val categoryId = resolveCategory(row.categoryId, if (row.type == "INCOME") "INCOME" else "EXPENSE", s)
                when (decide(local != null, local?.dirty == true, local?.baseRevision, local?.updatedAt ?: 0, row.updatedAt)) {
                    PullDecision.INSERT -> dao.insert(row.toEntity(walletId, toWalletId, categoryId, syncedAt = clock()))
                    PullDecision.UPDATE -> {
                        if (local != null && holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            dao.update(local.copy(updatedAt = at, dirty = true))
                        }) return
                        dao.update(row.toEntity(walletId, toWalletId, categoryId, syncedAt = freshSyncedAt(local!!.syncedAt), id = local.id))
                    }
                    PullDecision.KEEP_LOCAL -> {
                        if (local != null) holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            dao.update(local.copy(updatedAt = at, dirty = true))
                        }
                        return
                    }
                    PullDecision.UNCHANGED -> return
                }
                s.pulled++
            }
            "goals" -> {
                val row = decode(GoalRow.serializer(), json)
                val local = goals.findGoalByClientUuid(row.id)
                if (row.deletedAt != null) {
                    s.deletedGoals[row.id] = row
                    return queueDelete(s, table, row.id, local?.updatedAt, local?.dirty, local?.baseRevision, row.deletedAt, serverWins) { goals.deleteById(local!!.id) }
                }
                // A linked wallet that hasn't arrived is only informational on a goal, so the link is dropped, not deferred.
                val walletId = row.walletId?.let { wallets.findByClientUuid(it)?.id }
                when (decide(local != null, local?.dirty == true, local?.baseRevision, local?.updatedAt ?: 0, row.updatedAt)) {
                    // photoPath never comes from the cloud — it names a file on the device that chose it.
                    PullDecision.INSERT -> goals.insert(row.toEntity(walletId, photoPath = null, syncedAt = clock()))
                    PullDecision.UPDATE -> {
                        if (local != null && holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            goals.update(local.copy(updatedAt = at, dirty = true))
                        }) return
                        goals.update(
                            row.toEntity(walletId, photoPath = local!!.photoPath, syncedAt = freshSyncedAt(local.syncedAt), id = local.id)
                        )
                    }
                    PullDecision.KEEP_LOCAL -> {
                        if (local != null) holdPending(local.dirty, local.baseRevision, local.updatedAt, revision, row.updatedAt, s) { at ->
                            goals.update(local.copy(updatedAt = at, dirty = true))
                        }
                        return
                    }
                    PullDecision.UNCHANGED -> return
                }
                s.pulled++
            }
            "goal_contributions" -> {
                val row = decode(GoalContributionRowDto.serializer(), json)
                val local = goals.findContributionByClientUuid(row.id)
                if (row.deletedAt != null) {
                    // Contributions are immutable, so a remote delete always applies.
                    if (local != null) s.deletes += PendingDelete(table, row.id) { goals.deleteContribution(local.id) }
                    return
                }
                if (local != null) return
                val goalId = resolveGoal(row.goalId, s)
                goals.insertContribution(
                    GoalContributionEntity(
                        goalId = goalId, amountMinor = row.amountMinor, occurredOn = row.occurredOn, note = row.note,
                        clientUuid = row.id, createdAt = SyncTime.fromIso(row.createdAt), dirty = false, syncedAt = clock(),
                        baseRevision = row.revision,
                    )
                )
                s.pulled++
            }
        }
    }

    /** A pulled wallet's debt terms replace the local ones; a row without them (or not a card or loan) has none. */
    private suspend fun applyDebtTerms(walletId: Long, row: WalletRow) {
        val dao = debtTerms ?: return
        val liability = row.type == "CREDIT_CARD" || row.type == "LOAN"
        val repayment = row.repayment
        if (!liability || repayment == null) {
            dao.delete(walletId)
            return
        }
        dao.upsert(
            DebtTermsEntity(
                walletId = walletId, interestRateBps = row.interestRateBps, repayment = repayment,
                termMonths = row.termMonths, installmentMinor = row.installmentMinor, updatedAt = SyncTime.fromIso(row.updatedAt),
            )
        )
    }

    /**
     * True when [dirty] pending values must be left in place. A timestamp tie is moved one millisecond later so the
     * server's tie-to-stored rule will accept the push.
     */
    private suspend fun holdPending(
        dirty: Boolean,
        base: Long?,
        updatedAt: Long,
        remoteRevision: Long?,
        remoteUpdatedAtIso: String,
        s: PullState,
        bump: suspend (Long) -> Unit,
    ): Boolean {
        val remoteAt = SyncTime.fromIso(remoteUpdatedAtIso)
        if (!dirty || !protectsPendingEdit(base, updatedAt, remoteRevision, remoteAt)) return false
        val next = tieBreakUpdatedAt(dirty, base, updatedAt, remoteRevision, remoteAt)
        if (next != null) {
            bump(next)
            s.pendingKept++
        }
        return true
    }

    /**
     * A pulled child whose parent is not on this phone. Restores the soft-deleted parent when this pull saw it,
     * otherwise a fallback (a "Restored account", or an "Uncategorized" category) under the same client id.
     * The restored row is not dirty, so it is not pushed and does not undelete the parent for other devices.
     * The cursor is allowed to move: deferring here used to re-download every later row forever.
     */
    private suspend fun resolveWallet(clientUuid: String, currencyCode: String, s: PullState): Long {
        wallets.findByClientUuid(clientUuid)?.let { return it.id }
        val restored = s.deletedWallets[clientUuid]
        val entity = if (restored != null) {
            restored.toEntity(syncedAt = clock())
        } else {
            WalletEntity(
                name = RESTORED_ACCOUNT_NAME, type = "CASH", currencyCode = currencyCode, openingBalanceMinor = 0,
                createdAt = clock(), updatedAt = clock(), clientUuid = clientUuid, dirty = false, syncedAt = clock(),
            )
        }
        s.dropQueuedDelete("wallets", clientUuid)
        s.parentsRestored++
        val id = wallets.insert(entity)
        if (restored != null) applyDebtTerms(id, restored)
        return id
    }

    private suspend fun resolveCategory(clientUuid: String?, kind: String, s: PullState): Long? {
        if (clientUuid == null) return null
        categories.findByClientUuid(clientUuid)?.let { return it.id }
        val restored = s.deletedCategories[clientUuid]
        val entity = if (restored != null) {
            restored.toEntity(syncedAt = clock())
        } else {
            CategoryEntity(
                name = UNCATEGORIZED_NAME, kind = kind, iconKey = "other", sortOrder = 0,
                createdAt = clock(), updatedAt = clock(), clientUuid = clientUuid, dirty = false, syncedAt = clock(),
            )
        }
        s.dropQueuedDelete("categories", clientUuid)
        s.parentsRestored++
        return categories.insert(entity)
    }

    private suspend fun resolveGoal(clientUuid: String, s: PullState): Long {
        goals.findGoalByClientUuid(clientUuid)?.let { return it.id }
        val restored = s.deletedGoals[clientUuid]
        val entity = if (restored != null) {
            restored.toEntity(localWalletId = null, photoPath = null, syncedAt = clock())
        } else {
            GoalEntity(
                name = "Restored goal", targetMinor = 0, currencyCode = "USD", targetDate = null, walletId = null,
                createdAt = clock(), updatedAt = clock(), clientUuid = clientUuid, dirty = false, syncedAt = clock(),
            )
        }
        s.dropQueuedDelete("goals", clientUuid)
        s.parentsRestored++
        return goals.insert(entity)
    }

    private fun queueDelete(
        s: PullState,
        table: String,
        clientUuid: String?,
        localUpdatedAt: Long?,
        localDirty: Boolean?,
        localBaseRevision: Long?,
        deletedAtIso: String,
        serverWins: Boolean,
        delete: suspend () -> Unit,
    ) {
        if (localUpdatedAt == null || localDirty == null) return // never had it, nothing to do
        if (!serverWins && localWinsOverRemoteDelete(localUpdatedAt, localDirty, SyncTime.fromIso(deletedAtIso))) {
            s.conflicts++ // a newer local edit survives; it is pushed and the server applies the same rule
        } else {
            if (serverWins && localDirty && localBaseRevision != null) s.conflicts++
            s.deletes += PendingDelete(table, clientUuid, delete)
        }
    }

    /**
     * The local dirty trigger ignores a write only when `synced_at` changes in the same statement, so a pulled update
     * must always carry a value different from the stored one — even within the same millisecond.
     */
    private fun freshSyncedAt(previous: Long?): Long = maxOf(clock(), (previous ?: 0L) + 1)

    private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject =
        SyncJson.encodeToJsonElement(serializer, value).jsonObject

    private fun <T> decode(serializer: KSerializer<T>, json: JsonObject): T = SyncJson.decodeFromJsonElement(serializer, json)

    companion object {
        const val PULL_PAGE_SIZE = 500L
        /** Covers writes stamped by the server before a pull's read but committed after it. */
        const val CURSOR_OVERLAP_MS = 60_000L
        const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        const val RESTORED_ACCOUNT_NAME = "Restored account"
        const val UNCATEGORIZED_NAME = "Uncategorized"
        const val ACCOUNT_MISMATCH_MESSAGE =
            "This phone still has another account’s data, so nothing was uploaded. Sign in with that account, or switch accounts to remove it from this phone."
        private val PARENT_FIRST = listOf("wallets", "categories", "budgets", "transactions", "recurring", "goals", "goal_contributions")
        private val CHILD_FIRST = listOf("goal_contributions", "recurring", "transactions", "budgets", "goals", "categories", "wallets")
    }
}

private fun JsonObject.str(key: String): String = (getValue(key) as JsonPrimitive).content

private fun JsonObject.revisionOrNull(): Long? = (this[SupabaseSyncRemote.REVISION] as? JsonPrimitive)?.longOrNull

private fun JsonObject.serverMillis(): Long? =
    (this[SupabaseSyncRemote.SERVER_UPDATED_AT] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(SyncTime::fromIso)

private fun WalletRow.toEntity(syncedAt: Long, id: Long = 0): WalletEntity = WalletEntity(
    id = id, name = name, type = type, currencyCode = currencyCode, openingBalanceMinor = openingBalanceMinor,
    sortOrder = sortOrder, archived = archived, createdAt = SyncTime.fromIso(createdAt), updatedAt = SyncTime.fromIso(updatedAt),
    clientUuid = this.id, dirty = false, syncedAt = syncedAt, baseRevision = revision,
)

private fun CategoryRow.toEntity(syncedAt: Long, id: Long = 0): CategoryEntity = CategoryEntity(
    id = id, name = name, kind = kind, iconKey = iconKey, sortOrder = sortOrder, archived = archived,
    createdAt = SyncTime.fromIso(createdAt), updatedAt = SyncTime.fromIso(updatedAt), clientUuid = this.id,
    dirty = false, syncedAt = syncedAt, baseRevision = revision,
)

private fun BudgetRow.toEntity(localCategoryId: Long?, syncedAt: Long, id: Long = 0): BudgetEntity = BudgetEntity(
    id = id, categoryId = localCategoryId, currencyCode = currencyCode, limitMinor = limitMinor,
    createdAt = SyncTime.fromIso(createdAt), updatedAt = SyncTime.fromIso(updatedAt), clientUuid = this.id,
    dirty = false, syncedAt = syncedAt, baseRevision = revision,
)

private fun TransactionRow.toEntity(localWalletId: Long, localCategoryId: Long?, syncedAt: Long, id: Long = 0): TransactionEntity =
    TransactionEntity(
        id = id, type = type, amountMinor = amountMinor, currencyCode = currencyCode, walletId = localWalletId,
        categoryId = localCategoryId, occurredOn = occurredOn, note = note, clientUuid = this.id,
        createdAt = SyncTime.fromIso(createdAt), updatedAt = SyncTime.fromIso(updatedAt), transferGroup = transferGroup,
        dirty = false, syncedAt = syncedAt, baseRevision = revision,
    )

private fun RecurringRow.toEntity(
    localWalletId: Long,
    localToWalletId: Long?,
    localCategoryId: Long?,
    syncedAt: Long,
    id: Long = 0,
): RecurringEntity = RecurringEntity(
    id = id, name = name, type = type, amountMinor = amountMinor, currencyCode = currencyCode, walletId = localWalletId,
    categoryId = localCategoryId, frequency = frequency, intervalCount = intervalCount, startOn = startOn, endOn = endOn,
    nextIndex = nextIndex, paused = paused, createdAt = SyncTime.fromIso(createdAt), updatedAt = SyncTime.fromIso(updatedAt),
    toWalletId = localToWalletId, interestMinor = interestMinor, clientUuid = this.id, dirty = false, syncedAt = syncedAt,
    baseRevision = revision,
)

private fun GoalRow.toEntity(localWalletId: Long?, photoPath: String?, syncedAt: Long, id: Long = 0): GoalEntity = GoalEntity(
    id = id, name = name, targetMinor = targetMinor, currencyCode = currencyCode, targetDate = targetDate,
    walletId = localWalletId, archived = archived, createdAt = SyncTime.fromIso(createdAt), updatedAt = SyncTime.fromIso(updatedAt),
    sceneKey = sceneKey, photoPath = photoPath, clientUuid = this.id, dirty = false, syncedAt = syncedAt, baseRevision = revision,
)
