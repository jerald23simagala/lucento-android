package com.lucento.app.data.entitlement

import com.lucento.app.data.billing.PurchaseQuery
import com.lucento.app.data.billing.PurchaseSource
import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementEvaluator
import com.lucento.app.domain.entitlement.EntitlementMerge
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.ProFeature
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The server's copy of this person's entitlements (`public.entitlements`, read under their own JWT). Null when signed out
 * or unreachable — which never changes anything (docs/MONETIZATION.md §5.1). The Supabase implementation arrives with the
 * server migration; until then the manager runs on Play and the cache alone.
 */
fun interface EntitlementRemote {
    suspend fun fetch(): List<Entitlement>?
}

/** What a [EntitlementManager.refresh] could reach: Restore purchases words its answer from this (customer copy D-8). */
data class RefreshOutcome(val playReached: Boolean, val serverReached: Boolean)

/**
 * Owns the one observable truth about plans ([state]) and combines its three inputs in a fixed order (docs §5.1):
 * the cached snapshot first (first frame, offline), then Google Play's local reading, then the server's. A source that
 * cannot be reached never removes anything; a source that answers replaces only the rows it owns ([EntitlementMerge]).
 *
 * No I/O of its own beyond the three collaborators, so every path is a JVM test with fakes.
 */
class EntitlementManager(
    private val cache: EntitlementCache,
    private val purchases: PurchaseSource,
    private val remote: EntitlementRemote? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(EntitlementState.FREE)
    val state: StateFlow<EntitlementState> get() = _state

    /**
     * True once a refresh has run in this process (whatever it reached). Until then [state] is the cache at best, so a
     * "your plan has ended" notice must wait for it — a cold start reads Free for a moment before the cache loads.
     */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> get() = _ready

    private val lock = Mutex()
    private var loaded = false

    /** Reads the cached snapshot. Idempotent; a refresh that already ran is never undone by a late load. */
    suspend fun loadCached() = lock.withLock {
        if (loaded) return@withLock
        loaded = true
        cache.read()?.let { _state.value = EntitlementState(it) }
    }

    /** Asks Play, then the server, and persists whatever changed. Safe to call often; concurrent calls queue. */
    suspend fun refresh(): RefreshOutcome {
        loadCached()
        return lock.withLock {
            val now = clock()
            var rows = _state.value.entitlements
            val play = purchases.queryPurchases()
            when (play) {
                is PurchaseQuery.Success -> rows = EntitlementMerge.reconcilePlay(rows, PurchaseInterpreter.entitlements(play.purchases, now), now)
                is PurchaseQuery.Failed -> Unit // Play unreachable: the cached reading stands (L-2)
            }
            val server = remote?.fetch()
            server?.let { rows = EntitlementMerge.reconcileServer(rows, it) }
            publish(rows)
            _ready.value = true
            RefreshOutcome(playReached = play is PurchaseQuery.Success, serverReached = server != null)
        }
    }

    /** Applies a change Play pushed (a purchase completing, a renewal) without a full refresh. */
    suspend fun applyPlayUpdate(query: PurchaseQuery) {
        if (query !is PurchaseQuery.Success) return
        loadCached()
        lock.withLock {
            val now = clock()
            publish(EntitlementMerge.reconcilePlay(_state.value.entitlements, PurchaseInterpreter.entitlements(query.purchases, now), now))
        }
    }

    /** Keeps [state] current with Play's pushed updates for as long as [scope] lives. */
    fun observePlayUpdates(scope: CoroutineScope) {
        purchases.updates.onEach { applyPlayUpdate(it) }.launchIn(scope)
    }

    /** Live access for a screen: recomputed whenever the plan or the usage changes. */
    fun access(feature: ProFeature, usage: Flow<Int>): Flow<Access> =
        combine(state, usage) { s, used -> EntitlementEvaluator.access(s, feature, used, clock()) }

    /** One-shot access for a repository gate. */
    fun check(feature: ProFeature, used: Int): Access = EntitlementEvaluator.access(_state.value, feature, used, clock())

    private suspend fun publish(rows: List<Entitlement>) {
        val next = EntitlementState(rows)
        if (next != _state.value) {
            _state.value = next
            cache.write(rows)
        }
    }
}
