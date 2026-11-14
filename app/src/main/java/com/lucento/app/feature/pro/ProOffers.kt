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
data class OfferAction(
    val price: ProductPrice,
    val label: String,
    val priceLine: String,
    val primary: Boolean,
    /** "Yearly" / "Monthly" when this is one period of a subscription; null for a one-time price. */
    val periodName: String? = null,
    /** How often it is charged: "a year", "a month", "once". */
    val per: String = "",
    /** "7-day free trial" when Play lists one. */
    val trial: String? = null,
    /** "Save 33%" on the yearly period, from Play's own amounts ([ProCopy.yearlySavingPercent]). */
    val saving: String? = null,
)

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
                val subs = pricesOf(PlayProducts.PRO_SUBSCRIPTION)
                subs.mapIndexed { i, p -> action(p, subscriptionLabel(p), primary = i == 0, saving = saving(p, subs)) }
            } else {
                emptyList()
            }
            cards += PlanCardModel(
                kind = PlanCardKind.PRO,
                current = plan == DisplayPlan.PRO,
                actions = offers,
                note = when {
                    PlayProducts.PRO_SUBSCRIPTION in pending -> PENDING_NOTE
                    offers.isNotEmpty() -> SUBSCRIPTION_TERMS
                    else -> null
                },
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
                val cloud = pricesOf(PlayProducts.CLOUD_ADDON)
                cloud.mapIndexed { i, p -> action(p, "Add Lucento Cloud ${periodWord(p)}", primary = i == 0, saving = saving(p, cloud)) }
            } else {
                emptyList()
            }
            cards += PlanCardModel(
                kind = PlanCardKind.CLOUD,
                current = active.any { it.source == EntitlementSource.PLAY_CLOUD_ADDON },
                actions = cloudOffers,
                note = when {
                    PlayProducts.CLOUD_ADDON in pending -> PENDING_NOTE
                    cloudOffers.isNotEmpty() -> SUBSCRIPTION_TERMS
                    else -> null
                },
            )
        }
        return cards
    }

    /**
     * Google Play's subscription policy: next to every subscribe button, say that it renews, that a trial turns into a
     * paid subscription, and where to cancel. The price line above the buttons gives the price, period and trial length.
     */
    const val SUBSCRIPTION_TERMS = "Renews automatically until you cancel in Google Play. A free trial becomes a paid " +
        "subscription when it ends unless you cancel before then. Cancel any time in Google Play › Payments & subscriptions."

    const val PENDING_NOTE = "Waiting for Google Play to confirm your payment. Finish it at the store or in Google Play; your plan " +
        "starts the moment it’s confirmed, even if Lucento is closed."

    private fun action(price: ProductPrice, label: String, primary: Boolean, saving: String? = null) = OfferAction(
        price = price,
        label = label,
        priceLine = ProCopy.priceLine(price),
        primary = primary,
        periodName = ProCopy.periodName(price),
        per = ProCopy.pricePer(price),
        trial = ProCopy.trialText(price),
        saving = saving,
    )

    /** "Save 33%" for the yearly price of a product that also has a monthly one; null otherwise. */
    private fun saving(price: ProductPrice, group: List<ProductPrice>): String? {
        if (price.billingPeriodIso != "P1Y") return null
        val monthly = group.firstOrNull { it.billingPeriodIso == "P1M" } ?: return null
        return ProCopy.yearlySavingPercent(price, monthly)?.let { "Save $it%" }
    }

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
