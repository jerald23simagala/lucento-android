package com.lucento.app.data.entitlement

import com.lucento.app.data.billing.PurchaseQuery
import com.lucento.app.data.billing.PurchaseSource
import com.lucento.app.data.billing.PurchaseVerifier
import com.lucento.app.data.billing.VerifyOutcome
import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementEvaluator
import com.lucento.app.domain.entitlement.EntitlementMerge
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.ProFeature
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.PurchaseRecord
import com.lucento.app.domain.entitlement.Verifier
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
 * After every Play reading it also **confirms** what Play reported (docs/MONETIZATION.md §9): a purchase the server
 * hasn't recorded for this account goes to [verifier] (so cloud services and a new phone know about it), then every
 * paid purchase Play hasn't had acknowledged is acknowledged — whatever the server said, since Play itself vouches for
 * it, and Play refunds an unacknowledged purchase after three days. A pending payment is neither (nothing is granted
 * until Play confirms it). A failed acknowledgement is simply tried again at the next refresh.
 *
 * No I/O of its own beyond its collaborators, so every path is a JVM test with fakes.
 */
class EntitlementManager(
    private val cache: EntitlementCache,
    private val purchases: PurchaseSource,
    private val remote: EntitlementRemote? = null,
    /** Release builds with a server; null in debug (the fake store's purchases must never reach the server). */
    private val verifier: PurchaseVerifier? = null,
    /**
     * Security audit F-03: [PurchaseInterpreter.obfuscatedAccountId] of the signed-in Lucento account, or null when signed
     * out. A purchase Play says was made for a different account is never sent for this one.
     */
    private val signedInAccountHash: suspend () -> String? = { null },
    // Last, so existing callers can keep passing it as a trailing lambda.
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

    /**
     * Set when the server refused to record a purchase on this account (it belongs to another one, or needs support);
     * the Pro page explains it. Play's reading still counts on this phone either way.
     */
    private val _verificationIssue = MutableStateFlow<VerifyOutcome?>(null)
    val verificationIssue: StateFlow<VerifyOutcome?> get() = _verificationIssue

    private val lock = Mutex()
    private var loaded = false
    /** Purchases (by token hash) the server already answered for in this process; not sent again until a restart. */
    private val settled = mutableSetOf<String>()

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
                is PurchaseQuery.Failed -> Unit // Play unreachable: the cached reading stands (L-2; a Lifetime for a month)
            }
            var server = remote?.fetch()?.heardAt(now)
            server?.let { rows = EntitlementMerge.reconcileServer(rows, it) }
            if (play is PurchaseQuery.Success && confirm(play.purchases, rows)) {
                remote?.fetch()?.heardAt(now)?.let { fresh -> server = fresh; rows = EntitlementMerge.reconcileServer(rows, fresh) }
            }
            publish(rows)
            _ready.value = true
            RefreshOutcome(playReached = play is PurchaseQuery.Success, serverReached = server != null)
        }
    }

    /**
     * Sends what the server lacks to [verifier] and acknowledges what Play hasn't had acknowledged. True when the server
     * recorded something new, so its rows are worth reading again.
     */
    private suspend fun confirm(owned: List<PurchaseRecord>, rows: List<Entitlement>): Boolean {
        val recorded = rows.filter { it.verifiedBy == Verifier.SERVER }.mapNotNull { it.purchaseTokenHash }.toSet()
        val verifier = verifier
        var verified = false
        val account = if (verifier != null) runCatching { signedInAccountHash() }.getOrNull() else null
        for (purchase in owned) {
            if (purchase.pending) continue
            val hash = PurchaseInterpreter.tokenHash(purchase.purchaseToken)
            val forAnotherAccount = PurchaseInterpreter.madeForAnotherAccount(purchase, account)
            if (verifier != null && forAnotherAccount && hash !in settled) {
                // Bought by the other Lucento account on this Google Play account: theirs to verify (security audit F-03).
                settled += hash
                _verificationIssue.value = VerifyOutcome.OTHER_ACCOUNT
            }
            if (verifier != null && !forAnotherAccount && hash !in recorded && hash !in settled) {
                val outcome = runCatching { verifier.verify(purchase) }.getOrDefault(VerifyOutcome.UNAVAILABLE)
                if (outcome.settled) settled += hash
                when (outcome) {
                    VerifyOutcome.VERIFIED -> verified = true
                    VerifyOutcome.LINKED_ELSEWHERE, VerifyOutcome.REVIEW_REQUIRED, VerifyOutcome.OTHER_ACCOUNT,
                    VerifyOutcome.TEST_PURCHASE_REFUSED -> _verificationIssue.value = outcome
                    VerifyOutcome.SIGNED_OUT, VerifyOutcome.UNAVAILABLE -> Unit
                }
            }
            if (!purchase.acknowledged) runCatching { purchases.acknowledge(purchase.purchaseToken) }
        }
        return verified
    }

    /**
     * A different Lucento account signed in (or the first one): what the server said about each purchase was said about
     * the previous account, so every purchase is offered to the new one at the next refresh, and the old account's
     * "linked elsewhere" note goes.
     */
    suspend fun accountChanged() = lock.withLock {
        settled.clear()
        _verificationIssue.value = null
    }

    /** Applies a change Play pushed (a purchase completing, a renewal) without a full refresh. */
    suspend fun applyPlayUpdate(query: PurchaseQuery) {
        if (query !is PurchaseQuery.Success) return
        loadCached()
        lock.withLock {
            val now = clock()
            var rows = EntitlementMerge.reconcilePlay(_state.value.entitlements, PurchaseInterpreter.entitlements(query.purchases, now), now)
            // The plan shows the moment Play confirms it; the server's copy follows (best effort, not waited for twice).
            publish(rows)
            if (confirm(query.purchases, rows)) {
                remote?.fetch()?.heardAt(now)?.let { rows = EntitlementMerge.reconcileServer(rows, it) }
                publish(rows)
            }
        }
    }

    /** The server's rows as this phone heard them [now]: a Lifetime counts for a month from here (docs §5.3 L-2). */
    private fun List<Entitlement>.heardAt(now: Long): List<Entitlement> = map { it.copy(checkedAtMillis = now) }

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
