package com.lucento.app.data.billing

import android.content.Context
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Test builds only (docs/MONETIZATION.md §5.7): plans a tester can switch between without Google Play, so every plan's
 * screens can be seen on a phone and in the screenshot tour. Each scenario is a set of purchases [FakePurchaseSource]
 * reports, read by the same [com.lucento.app.domain.entitlement.PurchaseInterpreter] Google Play's will be — so what a
 * tester sees is what a real purchase produces. Release builds never construct a simulator.
 */
enum class SimulatedPlan(val label: String, val detail: String) {
    FREE("Free", "No purchases. This account’s server plan is ignored."),
    PRO_MONTHLY("Lucento Pro, monthly", "An active subscription that renews."),
    PRO_CANCELLED("Lucento Pro, cancelled", "Paid to the end of the period; it won’t renew."),
    PRO_PENDING("Lucento Pro, payment pending", "A cash payment Google Play hasn’t confirmed yet."),
    LIFETIME("Pro Lifetime, bought today", "Owned, with its first year of online services."),
    LIFETIME_YEAR_ENDED("Pro Lifetime, after its first year", "Owned; the included online services have ended."),
    LIFETIME_WITH_CLOUD("Pro Lifetime + Lucento Cloud", "Owned, after its first year, with the Cloud add-on."),
    ;

    fun purchases(now: Long): List<PurchaseRecord> {
        val day = 24L * 60 * 60 * 1000
        fun record(product: String, boughtAt: Long, pending: Boolean = false, renewing: Boolean = true) = PurchaseRecord(
            productId = product,
            purchaseToken = "debug-$name-$product",
            orderId = "GPA.debug-$name",
            purchaseTimeMillis = boughtAt,
            pending = pending,
            acknowledged = !pending,
            autoRenewing = renewing,
        )
        return when (this) {
            FREE -> emptyList()
            PRO_MONTHLY -> listOf(record(PlayProducts.PRO_SUBSCRIPTION, now - 3 * day))
            PRO_CANCELLED -> listOf(record(PlayProducts.PRO_SUBSCRIPTION, now - 20 * day, renewing = false))
            PRO_PENDING -> listOf(record(PlayProducts.PRO_SUBSCRIPTION, now - day, pending = true))
            LIFETIME -> listOf(record(PlayProducts.PRO_LIFETIME, now - day, renewing = false))
            LIFETIME_YEAR_ENDED -> listOf(record(PlayProducts.PRO_LIFETIME, now - 400 * day, renewing = false))
            LIFETIME_WITH_CLOUD -> listOf(
                record(PlayProducts.PRO_LIFETIME, now - 400 * day, renewing = false),
                record(PlayProducts.CLOUD_ADDON, now - 10 * day),
            )
        }
    }
}

/** Where the chosen scenario is kept, so it survives a restart (a tester checks a plan across app launches). */
interface SimulatorStore {
    fun read(): String?
    fun write(name: String?)
    fun readTestStore(): Boolean = false
    fun writeTestStore(on: Boolean) = Unit
}

class PrefsSimulatorStore(context: Context) : SimulatorStore {
    private val prefs = context.getSharedPreferences("lucento_debug_plan", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY, null)
    override fun write(name: String?) {
        prefs.edit().apply { if (name == null) remove(KEY) else putString(KEY, name) }.apply()
    }
    override fun readTestStore(): Boolean = prefs.getBoolean(TEST_STORE, false)
    override fun writeTestStore(on: Boolean) {
        prefs.edit().putBoolean(TEST_STORE, on).apply()
    }

    private companion object {
        const val KEY = "simulated_plan"
        const val TEST_STORE = "test_store"
    }
}

class InMemorySimulatorStore(var value: String? = null, var testStore: Boolean = false) : SimulatorStore {
    override fun read(): String? = value
    override fun write(name: String?) {
        value = name
    }
    override fun readTestStore(): Boolean = testStore
    override fun writeTestStore(on: Boolean) {
        testStore = on
    }
}

/**
 * Test builds only. Null [current] means "the account's real plan": Play (here, nothing) plus the server's rows. Any
 * scenario replaces both: [ignoresServer] makes the entitlement remote answer "no rows", so a tester whose account holds
 * a GRANT on lucento-test can still see the free plan.
 */
class DebugPlanSimulator(
    private val store: SimulatorStore,
    private val purchases: FakePurchaseSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _current = MutableStateFlow(store.read()?.let { saved -> SimulatedPlan.entries.firstOrNull { it.name == saved } })
    val current: StateFlow<SimulatedPlan?> get() = _current

    val ignoresServer: Boolean get() = _current.value != null

    /**
     * The test store: Google Play's purchase flow, faked. On, the Pro page lists [TEST_PRICES] and its buttons "buy" at
     * once through [FakePurchaseSource.launchPurchase] — nothing is charged, and every price says "test". Off (the
     * default), the page shows what a release without products on sale shows.
     */
    private val _testStore = MutableStateFlow(store.readTestStore())
    val testStore: StateFlow<Boolean> get() = _testStore

    init {
        purchases.setPurchases(_current.value?.purchases(clock()).orEmpty())
        purchases.prices = if (_testStore.value) TEST_PRICES else emptyList()
    }

    fun setTestStore(on: Boolean) {
        store.writeTestStore(on)
        _testStore.value = on
        purchases.prices = if (on) TEST_PRICES else emptyList()
    }

    /** Switches to [plan] (null: the account's own plan). The caller refreshes the entitlement manager afterwards. */
    fun select(plan: SimulatedPlan?) {
        store.write(plan?.name)
        _current.value = plan
        purchases.setPurchases(plan?.purchases(clock()).orEmpty())
    }
}

/**
 * Test builds only: what the test store lists, shaped like Google Play's answer (trial on yearly Pro, one Lifetime option,
 * Cloud monthly and yearly). Every string says "test" so a screenshot can't pass for a real price list; the real prices
 * only ever come from Play Console through `ProductDetails`.
 */
val TEST_PRICES: List<ProductPrice> = listOf(
    ProductPrice(PlayProducts.PRO_SUBSCRIPTION, "annual", "test-pro-annual", "₱1,190.00 (test)", "P1Y", freeTrialPeriodIso = "P7D", priceMicros = 1_190_000_000, currencyCode = "PHP"),
    ProductPrice(PlayProducts.PRO_SUBSCRIPTION, "monthly", "test-pro-monthly", "₱149.00 (test)", "P1M", priceMicros = 149_000_000, currencyCode = "PHP"),
    ProductPrice(PlayProducts.PRO_LIFETIME, null, "test-lifetime", "₱3,490.00 (test)", null, priceMicros = 3_490_000_000, currencyCode = "PHP"),
    ProductPrice(PlayProducts.CLOUD_ADDON, "annual", "test-cloud-annual", "₱490.00 (test)", "P1Y", priceMicros = 490_000_000, currencyCode = "PHP"),
    ProductPrice(PlayProducts.CLOUD_ADDON, "monthly", "test-cloud-monthly", "₱59.00 (test)", "P1M", priceMicros = 59_000_000, currencyCode = "PHP"),
)
