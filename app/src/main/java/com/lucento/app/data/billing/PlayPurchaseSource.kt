package com.lucento.app.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Google Play Billing (Library 9.x) behind [PurchaseSource] (docs/MONETIZATION.md §9). Release builds only: the `.debug`
 * application id can never bill.
 *
 * - Reads what this Google account owns from Play's own cache (works offline), subscriptions and one-time products.
 * - Honours only purchases whose signature checks out against Play's licence key ([PurchaseSignature]). If any purchase
 *   fails the check, the whole reading is reported as failed, so a forged purchase neither adds a plan nor hides a real
 *   one (an unreachable reading never removes anything, §5.3 L-2).
 * - Pending payments (cash at a store) are enabled for one-time products and come through as `pending`.
 * - Acknowledging is the caller's job ([acknowledge]), after the server has seen the purchase when it can
 *   ([com.lucento.app.data.entitlement.EntitlementManager]).
 *
 * The callbacks are wrapped in coroutines here rather than through billing-ktx, so the library's Kotlin metadata can't
 * pin the project's compiler. Not a JVM test (it is the library); the decisions it relies on are:
 * [PurchaseSignature], [PlayPriceSelection], [BillingProblem].
 */
class PlayPurchaseSource(
    context: Context,
    private val scope: CoroutineScope,
    private val licenceKey: String,
) : PurchaseSource, PurchasesUpdatedListener {
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _availability = MutableStateFlow(BillingAvailability.UNKNOWN)
    override val availability: StateFlow<BillingAvailability> get() = _availability

    private val _updates = MutableSharedFlow<PurchaseQuery>(extraBufferCapacity = 8)
    override val updates: Flow<PurchaseQuery> get() = _updates

    private val _results = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 8)
    override val results: Flow<PurchaseResult> get() = _results

    private val connecting = Mutex()
    /** The last details Play returned per product: launching a purchase needs the object, not just its price. */
    private val details = ConcurrentHashMap<String, ProductDetails>()

    private suspend fun connected(): Boolean = connecting.withLock {
        if (client.isReady) return@withLock true
        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val once = AtomicBoolean(false)
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        _availability.value = when (result.responseCode) {
                            BillingResponseCode.OK -> BillingAvailability.AVAILABLE
                            BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingAvailability.UNAVAILABLE
                            else -> _availability.value
                        }
                        if (once.compareAndSet(false, true)) cont.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    // enableAutoServiceReconnection reconnects on the next call; nothing to do here.
                    override fun onBillingServiceDisconnected() = Unit
                })
            }
        }
        ok == true
    }

    override suspend fun queryPurchases(): PurchaseQuery {
        if (!connected()) return PurchaseQuery.Failed("Google Play billing isn't available")
        val subs = owned(ProductType.SUBS) ?: return PurchaseQuery.Failed("subscriptions couldn't be read")
        val inApp = owned(ProductType.INAPP) ?: return PurchaseQuery.Failed("one-time purchases couldn't be read")
        val ours = (subs + inApp).filter { p -> p.products.any { it in PlayProducts.ALL } }
        if (ours.any { !PurchaseSignature.verify(licenceKey, it.originalJson, it.signature) }) {
            Log.w(TAG, "a purchase failed its signature check; the reading is ignored")
            return PurchaseQuery.Failed("a purchase failed its signature check")
        }
        return PurchaseQuery.Success(ours.mapNotNull(::record))
    }

    private suspend fun owned(type: String): List<Purchase>? = suspendCancellableCoroutine { cont ->
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, list ->
            cont.resume(if (result.responseCode == BillingResponseCode.OK) list else null)
        }
    }

    private fun record(p: Purchase): PurchaseRecord? {
        val state = p.purchaseState
        if (state != Purchase.PurchaseState.PURCHASED && state != Purchase.PurchaseState.PENDING) return null
        val product = p.products.firstOrNull { it in PlayProducts.ALL } ?: return null
        return PurchaseRecord(
            productId = product,
            purchaseToken = p.purchaseToken,
            orderId = p.orderId,
            purchaseTimeMillis = p.purchaseTime,
            pending = state == Purchase.PurchaseState.PENDING,
            acknowledged = p.isAcknowledged,
            autoRenewing = p.isAutoRenewing,
            obfuscatedAccountId = p.accountIdentifiers?.obfuscatedAccountId?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun queryPrices(productIds: Set<String>): List<ProductPrice> {
        if (!connected()) return emptyList()
        val subs = productDetails(productIds.filter { it in PlayProducts.SUBSCRIPTIONS }, ProductType.SUBS)
        val inApp = productDetails(productIds.filter { it in PlayProducts.ONE_TIME }, ProductType.INAPP)
        (subs + inApp).forEach { details[it.productId] = it }
        return PlayPriceSelection.choose((subs + inApp).flatMap(::offers))
    }

    /** Play only answers one product type per query. */
    private suspend fun productDetails(ids: List<String>, type: String): List<ProductDetails> {
        if (ids.isEmpty()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build() })
            .build()
        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, found ->
                cont.resume(if (result.responseCode == BillingResponseCode.OK) found.productDetailsList else emptyList())
            }
        }
    }

    private fun offers(d: ProductDetails): List<PlayOffer> {
        val subscription = d.subscriptionOfferDetails.orEmpty().map { o ->
            PlayOffer(
                productId = d.productId,
                basePlanId = o.basePlanId,
                offerId = o.offerId,
                offerToken = o.offerToken,
                phases = o.pricingPhases.pricingPhaseList.map { ph ->
                    PricePhase(
                        priceMicros = ph.priceAmountMicros,
                        formattedPrice = ph.formattedPrice,
                        billingPeriodIso = ph.billingPeriod,
                        recurring = ph.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING,
                    )
                },
            )
        }
        val oneTime = d.oneTimePurchaseOfferDetailsList.orEmpty().mapNotNull { o ->
            val token = o.offerToken ?: return@mapNotNull null
            PlayOffer(
                productId = d.productId,
                basePlanId = null,
                offerId = o.offerId,
                offerToken = token,
                oneTimePrice = PricePhase(o.priceAmountMicros, o.formattedPrice, billingPeriodIso = null, recurring = false),
            )
        }
        return subscription + oneTime
    }

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        if (!connected()) return false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        return suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result -> cont.resume(result.responseCode == BillingResponseCode.OK) }
        }
    }

    override suspend fun launchPurchase(activity: Activity, price: ProductPrice, obfuscatedAccountId: String?): BillingProblem? {
        if (!connected()) return BillingProblem.UNAVAILABLE
        val product = details[price.productId]
            ?: run { queryPrices(setOf(price.productId)); details[price.productId] }
            ?: return BillingProblem.ITEM_UNAVAILABLE
        val token = price.offerToken ?: return BillingProblem.ITEM_UNAVAILABLE
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(token).build()),
            )
            .apply { obfuscatedAccountId?.let(::setObfuscatedAccountId) }
            .build()
        val result = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, params) }
        return when (result.responseCode) {
            BillingResponseCode.OK -> null
            BillingResponseCode.USER_CANCELED -> null.also { _results.tryEmit(PurchaseResult.Cancelled) }
            else -> BillingProblem.fromResponseCode(result.responseCode)
        }
    }

    /**
     * Play's callback for purchases made in the app *and* for ones completing elsewhere (a pending cash payment, a
     * purchase from the Play Store). The list it passes holds only the changed purchases, so the whole list is re-read
     * before anyone hears of it — [updates] always carries everything this account owns.
     */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val code = result.responseCode
        scope.launch {
            when (code) {
                BillingResponseCode.OK -> {
                    _updates.emit(queryPurchases())
                    val pending = purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }
                    _results.emit(if (pending) PurchaseResult.Pending else PurchaseResult.Completed)
                }
                BillingResponseCode.USER_CANCELED -> _results.emit(PurchaseResult.Cancelled)
                BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    _updates.emit(queryPurchases())
                    _results.emit(PurchaseResult.Failed(BillingProblem.ALREADY_OWNED))
                }
                else -> _results.emit(PurchaseResult.Failed(BillingProblem.fromResponseCode(code)))
            }
        }
    }

    private companion object {
        const val TAG = "LucentoBilling"
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
