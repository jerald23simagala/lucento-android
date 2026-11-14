package com.lucento.app.data.billing

import android.app.Activity
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether Google Play Billing can be used on this phone right now. */
enum class BillingAvailability {
    UNKNOWN,
    AVAILABLE,
    /** No Play Store (sideloaded, custom ROM), or Play refused the connection. Cached entitlements still apply. */
    UNAVAILABLE,
}

/** The outcome of asking Play what this Google account owns. Only [Success] may remove anything (docs §5.1). */
sealed interface PurchaseQuery {
    data class Success(val purchases: List<PurchaseRecord>) : PurchaseQuery
    data class Failed(val reason: String) : PurchaseQuery
}

/** A price as Google Play formats it for this person; the only source of a price string in the app. */
data class ProductPrice(
    val productId: String,
    val basePlanId: String?,
    val offerToken: String?,
    val formattedPrice: String,
    /** ISO 8601 period of a subscription base plan (P1M, P1Y); null for a one-time product. */
    val billingPeriodIso: String?,
    /** ISO 8601 length of a free trial on this offer, when there is one. */
    val freeTrialPeriodIso: String? = null,
    /**
     * Play's own amount for [formattedPrice], in millionths of [currencyCode]. Only used to compare two of Play's prices
     * (the yearly saving); never shown, so the app still never writes a price of its own.
     */
    val priceMicros: Long? = null,
    /** ISO 4217 code of [priceMicros], as Play reports it. */
    val currencyCode: String? = null,
)

/**
 * What the app needs from a store. [PlayPurchaseSource] (Billing Library 9.x) in release builds; [FakePurchaseSource] in
 * debug, CI and JVM tests; [UnavailablePurchaseSource] in a release built without Play's licence key. Nothing else in the
 * app knows the billing library.
 */
interface PurchaseSource {
    val availability: StateFlow<BillingAvailability>

    /**
     * Pushed whenever Play reports a change (a purchase completing, a renewal), so the UI updates without a restart.
     * Always the **whole** list this Google account owns, like [queryPurchases]: a missing purchase means it ended.
     */
    val updates: Flow<PurchaseQuery>

    /** How each purchase started with [launchPurchase] ended. */
    val results: Flow<PurchaseResult>

    /** Everything this Google account owns, from Play's local cache when offline. */
    suspend fun queryPurchases(): PurchaseQuery

    suspend fun queryPrices(productIds: Set<String>): List<ProductPrice>

    /** Confirms a purchase to Play (within three days, or Play refunds it). True when Play accepted it. */
    suspend fun acknowledge(purchaseToken: String): Boolean

    /**
     * Consumes a one-time purchase: Play stops listing it, and the product can be bought again. Lifetime is never consumed
     * while it counts. Lucento consumes it only when it has ended, refunded or charged back, and Play still holds it
     * ([com.lucento.app.domain.entitlement.LifetimeRelease]). True when Play released it.
     */
    suspend fun consume(purchaseToken: String): Boolean

    /**
     * Opens Google Play's purchase sheet for [price] over [activity]. Null when the sheet opened; the outcome then arrives
     * on [results] and the plan on [updates]. [obfuscatedAccountId] ties the order to the signed-in Lucento account
     * (sha256 of its id, docs/MONETIZATION.md §5.4 linking rule); null when signed out.
     */
    suspend fun launchPurchase(activity: Activity, price: ProductPrice, obfuscatedAccountId: String?): BillingProblem?
}

/**
 * A release build without Play's licence key (`LUCENTO_PLAY_LICENSE_KEY`) can't check a purchase's signature, so it
 * doesn't bill: no prices, so the Pro page says Lucento Pro isn't on sale. Its query always fails, which never removes a
 * plan this phone already holds (docs §5.3 L-2).
 */
object UnavailablePurchaseSource : PurchaseSource {
    override val availability: StateFlow<BillingAvailability> = MutableStateFlow(BillingAvailability.UNAVAILABLE)
    override val updates: Flow<PurchaseQuery> = MutableSharedFlow()
    override val results: Flow<PurchaseResult> = MutableSharedFlow()
    override suspend fun queryPurchases(): PurchaseQuery = PurchaseQuery.Failed("billing not configured in this build")
    override suspend fun queryPrices(productIds: Set<String>): List<ProductPrice> = emptyList()
    override suspend fun acknowledge(purchaseToken: String): Boolean = false
    override suspend fun consume(purchaseToken: String): Boolean = false
    override suspend fun launchPurchase(activity: Activity, price: ProductPrice, obfuscatedAccountId: String?) = BillingProblem.UNAVAILABLE
}

/**
 * An in-memory store for debug builds, CI and tests. The `.debug` applicationId can never bill, so this is what the
 * test-build plan simulator drives.
 */
class FakePurchaseSource(
    initial: List<PurchaseRecord> = emptyList(),
    availability: BillingAvailability = BillingAvailability.AVAILABLE,
) : PurchaseSource {
    private val _availability = MutableStateFlow(availability)
    override val availability: StateFlow<BillingAvailability> get() = _availability

    private val _updates = MutableSharedFlow<PurchaseQuery>(extraBufferCapacity = 8)
    override val updates: Flow<PurchaseQuery> get() = _updates

    private val _results = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 8)
    override val results: Flow<PurchaseResult> get() = _results

    private var purchases: List<PurchaseRecord> = initial
    /** When set, [queryPurchases] fails with this reason (Play unreachable). */
    var failure: String? = null
    var prices: List<ProductPrice> = emptyList()
    val acknowledged = mutableListOf<String>()
    val consumed = mutableListOf<String>()
    /** When false, [consume] refuses, as Play may (no connection, Play's own error). */
    var consumeSucceeds: Boolean = true

    override suspend fun queryPurchases(): PurchaseQuery =
        failure?.let { PurchaseQuery.Failed(it) } ?: PurchaseQuery.Success(purchases)

    override suspend fun queryPrices(productIds: Set<String>): List<ProductPrice> = prices.filter { it.productId in productIds }

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        acknowledged += purchaseToken
        purchases = purchases.map { if (it.purchaseToken == purchaseToken) it.copy(acknowledged = true) else it }
        return true
    }

    /** Like Play: the purchase leaves what the account owns, and nobody is told (consuming isn't a purchase update). */
    override suspend fun consume(purchaseToken: String): Boolean {
        if (!consumeSucceeds || purchases.none { it.purchaseToken == purchaseToken }) return false
        consumed += purchaseToken
        purchases = purchases.filterNot { it.purchaseToken == purchaseToken }
        return true
    }

    /**
     * Test builds: "buys" [price] at once, as if Play confirmed the payment — the record joins what the fake account owns
     * (unacknowledged, like a fresh Play purchase) and listeners hear the whole list. Nothing is charged; it lasts until
     * the simulator changes the plan or the app restarts.
     */
    override suspend fun launchPurchase(activity: Activity, price: ProductPrice, obfuscatedAccountId: String?): BillingProblem? {
        if (_availability.value != BillingAvailability.AVAILABLE) return BillingProblem.UNAVAILABLE
        if (purchases.any { it.productId == price.productId && !it.pending }) {
            _results.tryEmit(PurchaseResult.Failed(BillingProblem.ALREADY_OWNED))
            return null
        }
        val now = System.currentTimeMillis()
        val record = PurchaseRecord(
            productId = price.productId,
            purchaseToken = "debug-buy-${price.productId}-$now",
            orderId = "GPA.debug-buy-$now",
            purchaseTimeMillis = now,
            pending = false,
            acknowledged = false,
            autoRenewing = price.productId in PlayProducts.SUBSCRIPTIONS,
        )
        setPurchases(purchases.filterNot { it.productId == price.productId } + record)
        _results.tryEmit(PurchaseResult.Completed)
        return null
    }

    /** Replaces what the fake Google account owns and tells listeners, like Play's `PurchasesUpdatedListener`. */
    fun setPurchases(records: List<PurchaseRecord>) {
        purchases = records
        _updates.tryEmit(PurchaseQuery.Success(records))
    }

    fun setAvailability(value: BillingAvailability) {
        _availability.value = value
    }
}
