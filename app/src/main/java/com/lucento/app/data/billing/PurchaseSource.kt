package com.lucento.app.data.billing

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
)

/**
 * What the app needs from a store. `PlayPurchaseSource` (Billing Library 9.x) arrives with Google Play Billing; until
 * then, and in debug, CI and JVM tests, [FakePurchaseSource] stands in. Nothing else in the app knows the billing library.
 * Launching a purchase needs an Activity and is added with the Play implementation, behind this same interface.
 */
interface PurchaseSource {
    val availability: StateFlow<BillingAvailability>

    /** Pushed whenever Play reports a change (a purchase completing, a renewal), so the UI updates without a restart. */
    val updates: Flow<PurchaseQuery>

    /** Everything this Google account owns, from Play's local cache when offline. */
    suspend fun queryPurchases(): PurchaseQuery

    suspend fun queryPrices(productIds: Set<String>): List<ProductPrice>

    /** Confirms a purchase to Play (within three days, or Play refunds it). True when Play accepted it. */
    suspend fun acknowledge(purchaseToken: String): Boolean
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

    private var purchases: List<PurchaseRecord> = initial
    /** When set, [queryPurchases] fails with this reason (Play unreachable). */
    var failure: String? = null
    var prices: List<ProductPrice> = emptyList()
    val acknowledged = mutableListOf<String>()

    override suspend fun queryPurchases(): PurchaseQuery =
        failure?.let { PurchaseQuery.Failed(it) } ?: PurchaseQuery.Success(purchases)

    override suspend fun queryPrices(productIds: Set<String>): List<ProductPrice> = prices.filter { it.productId in productIds }

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        acknowledged += purchaseToken
        purchases = purchases.map { if (it.purchaseToken == purchaseToken) it.copy(acknowledged = true) else it }
        return true
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
