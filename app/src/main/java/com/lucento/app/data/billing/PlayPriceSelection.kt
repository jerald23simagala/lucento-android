package com.lucento.app.data.billing

import com.lucento.app.domain.entitlement.PlayProducts

/** One phase of a Play offer's price (a free trial, then the recurring price), stripped of the billing library. */
data class PricePhase(
    val priceMicros: Long,
    val formattedPrice: String,
    /** ISO 8601 (P1M, P1Y, P7D). Null for a one-time price. */
    val billingPeriodIso: String?,
    /** INFINITE_RECURRING in Play's terms: the price that repeats until the person cancels. */
    val recurring: Boolean,
    /** ISO 4217 code of [priceMicros]. */
    val currencyCode: String? = null,
)

/** One offer Play lists for this person: a subscription base plan or offer, or a one-time purchase option. */
data class PlayOffer(
    val productId: String,
    /** Subscriptions only. */
    val basePlanId: String?,
    /** Null for a subscription's base-plan offer; a one-time product's offer id when it has one. */
    val offerId: String?,
    val offerToken: String,
    /** Subscriptions: the phases in order. Empty for a one-time product, which has [oneTimePrice]. */
    val phases: List<PricePhase> = emptyList(),
    val oneTimePrice: PricePhase? = null,
)

/**
 * Chooses the offer to show for each plan from what Play lists (docs/MONETIZATION.md §4.1, §9). Play only lists offers
 * this person may take, so a free trial appears only to someone who hasn't had one, and a founder price only while it
 * runs. Pure, so every rule is a JVM test.
 *
 * - A subscription base plan shows its free-trial offer when Play lists one, else the base plan itself. Offers with a
 *   paid introductory phase are skipped: the line would show the regular price while the first charge differs.
 * - A one-time product shows its lowest-priced option (the founder offer while it runs).
 * - Yearly comes before monthly: it is the better value and carries the trial.
 */
object PlayPriceSelection {
    fun choose(offers: List<PlayOffer>): List<ProductPrice> {
        val subscriptions = offers
            .filter { it.productId in PlayProducts.SUBSCRIPTIONS && it.basePlanId != null && it.phases.isNotEmpty() }
            .groupBy { it.productId to it.basePlanId }
            .mapNotNull { (_, group) -> chooseSubscription(group) }
        val oneTime = offers
            .filter { it.productId in PlayProducts.ONE_TIME && it.oneTimePrice != null }
            .groupBy { it.productId }
            .mapNotNull { (_, group) ->
                val best = group.minByOrNull { it.oneTimePrice!!.priceMicros } ?: return@mapNotNull null
                val price = best.oneTimePrice!!
                ProductPrice(
                    best.productId, null, best.offerToken, price.formattedPrice, billingPeriodIso = null,
                    priceMicros = price.priceMicros, currencyCode = price.currencyCode,
                )
            }
        return (subscriptions + oneTime).sortedWith(
            compareBy<ProductPrice>({ PRODUCT_ORDER.indexOf(it.productId) }, { periodRank(it.billingPeriodIso) }),
        )
    }

    private fun chooseSubscription(group: List<PlayOffer>): ProductPrice? {
        fun trial(o: PlayOffer) = o.phases.firstOrNull { it.priceMicros == 0L && !it.recurring }
        fun paidIntro(o: PlayOffer) = o.phases.any { !it.recurring && it.priceMicros > 0L }
        val pick = group.firstOrNull { trial(it) != null && !paidIntro(it) }
            ?: group.firstOrNull { it.offerId == null && !paidIntro(it) }
            ?: group.firstOrNull { !paidIntro(it) }
            ?: return null
        val price = pick.phases.lastOrNull { it.recurring } ?: pick.phases.last()
        return ProductPrice(
            productId = pick.productId,
            basePlanId = pick.basePlanId,
            offerToken = pick.offerToken,
            formattedPrice = price.formattedPrice,
            billingPeriodIso = price.billingPeriodIso,
            freeTrialPeriodIso = trial(pick)?.billingPeriodIso,
            priceMicros = price.priceMicros,
            currencyCode = price.currencyCode,
        )
    }

    private val PRODUCT_ORDER = listOf(PlayProducts.PRO_SUBSCRIPTION, PlayProducts.PRO_LIFETIME, PlayProducts.CLOUD_ADDON)

    private fun periodRank(iso: String?): Int = if (iso == null || iso.endsWith("Y")) 0 else 1
}
