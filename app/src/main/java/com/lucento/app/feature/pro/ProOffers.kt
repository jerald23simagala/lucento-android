package com.lucento.app.feature.pro

import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PlayProducts
import java.time.Period

/** The three plan cards the Pro page can show (docs/MONETIZATION.md §4.1). */
enum class PlanCardKind { PRO, LIFETIME, CLOUD }

/** One way to buy, as the page shows it: Google Play's price in words above a button. */
data class OfferAction(val price: ProductPrice, val label: String, val priceLine: String, val primary: Boolean)

data class PlanCardModel(
    val kind: PlanCardKind,
    /** The person holds this plan now ("Your plan"). */
    val current: Boolean,
    val actions: List<OfferAction>,
    /** A line under the buttons (customer copy D-6), or why there are none (a pending payment, D-9). */
    val note: String? = null,
)

/**
 * Which plans the Pro page offers, to whom, and how each button reads (§4.1: "the app never shows two routes to the same
 * thing"). Pure, so every plan state is a JVM test; the screen only renders it.
 *
 * - Free: Lucento Pro (trial first when Play offers one) and Pro Lifetime.
 * - A subscriber: Pro Lifetime as "Switch to Lifetime", with the reminder to cancel in Google Play (D-11).
 * - A Lifetime owner: no Pro subscription (a worse deal for them); Lucento Cloud, offered once they lack cloud services.
 * - A pending payment for a plan replaces its buttons with the D-9 note, so nobody pays twice.
 *
 * Prices are only ever Play's strings ([ProductPrice.formattedPrice]); with none, there are no buttons.
 */
object ProOffers {
    fun cards(state: EntitlementState, prices: List<ProductPrice>, now: Long): List<PlanCardModel> {
        val plan = state.displayPlan(now)
        val ownsLifetime = plan == DisplayPlan.LIFETIME || plan == DisplayPlan.LIFETIME_WITH_SERVICES
        val active = state.active(now)
        val subscribed = active.any { it.source == EntitlementSource.PLAY_SUBSCRIPTION }
        val pending = state.entitlements.filter { it.status == EntitlementStatus.PENDING }.mapNotNullTo(HashSet()) { it.productId }
        fun pricesOf(product: String) = prices.filter { it.productId == product }

        val cards = mutableListOf<PlanCardModel>()
        if (!ownsLifetime) {
            val offers = if (plan == DisplayPlan.FREE && PlayProducts.PRO_SUBSCRIPTION !in pending) {
                pricesOf(PlayProducts.PRO_SUBSCRIPTION).mapIndexed { i, p -> action(p, subscriptionLabel(p), primary = i == 0) }
            } else {
                emptyList()
            }
            cards += PlanCardModel(
                kind = PlanCardKind.PRO,
                current = plan == DisplayPlan.PRO,
                actions = offers,
                note = if (PlayProducts.PRO_SUBSCRIPTION in pending) PENDING_NOTE else null,
            )
        }
        val lifetimeOffers = if (!ownsLifetime && PlayProducts.PRO_LIFETIME !in pending) {
            pricesOf(PlayProducts.PRO_LIFETIME).take(1).map { action(it, if (subscribed) "Switch to Lifetime" else "Buy Lifetime", primary = true) }
        } else {
            emptyList()
        }
        cards += PlanCardModel(
            kind = PlanCardKind.LIFETIME,
            current = ownsLifetime,
            actions = lifetimeOffers,
            note = when {
                PlayProducts.PRO_LIFETIME in pending -> PENDING_NOTE
                lifetimeOffers.isEmpty() -> null
                subscribed -> "After buying, cancel your subscription in Google Play. You keep Pro until the period you paid for ends."
                else -> "One-time payment through Google Play. Restored on any phone with your Google account or your Lucento account."
            },
        )
        if (ownsLifetime) {
            val cloudOffers = if (!state.has(Capability.CLOUD, now) && PlayProducts.CLOUD_ADDON !in pending) {
                pricesOf(PlayProducts.CLOUD_ADDON).mapIndexed { i, p -> action(p, if (i == 0) "Add Lucento Cloud" else "Choose ${periodWord(p)} instead", primary = i == 0) }
            } else {
                emptyList()
            }
            cards += PlanCardModel(
                kind = PlanCardKind.CLOUD,
                current = active.any { it.source == EntitlementSource.PLAY_CLOUD_ADDON },
                actions = cloudOffers,
                note = if (PlayProducts.CLOUD_ADDON in pending) PENDING_NOTE else null,
            )
        }
        return cards
    }

    const val PENDING_NOTE = "Waiting for Google Play to confirm your payment. Finish it at the store or in Google Play; your plan " +
        "starts the moment it’s confirmed, even if Lucento is closed."

    private fun action(price: ProductPrice, label: String, primary: Boolean) = OfferAction(price, label, ProCopy.priceLine(price), primary)

    /** Customer copy C: "Start 7-day free trial" (annual) · "Subscribe monthly". */
    private fun subscriptionLabel(price: ProductPrice): String {
        val trial = price.freeTrialPeriodIso?.let { runCatching { Period.parse(it) }.getOrNull() }
        return when {
            trial != null && trial.days > 0 -> "Start ${trial.days}-day free trial"
            trial != null && trial.months > 0 -> "Start ${trial.months}-month free trial"
            else -> "Subscribe ${periodWord(price)}"
        }
    }

    private fun periodWord(price: ProductPrice): String {
        val p = price.billingPeriodIso?.let { runCatching { Period.parse(it) }.getOrNull() } ?: return "now"
        return when {
            p.years == 1 && p.months == 0 -> "yearly"
            p.years == 0 && p.months == 1 -> "monthly"
            else -> "now"
        }
    }
}
