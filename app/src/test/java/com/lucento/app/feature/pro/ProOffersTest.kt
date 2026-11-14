package com.lucento.app.feature.pro

import com.lucento.app.data.billing.BillingProblem
import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.data.billing.PurchaseResult
import com.lucento.app.data.billing.VerifyOutcome
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.PurchaseRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lucento Pro, Phase 4: who is offered what on the Pro page, and how it reads (docs/MONETIZATION.md §4.1, customer copy C, D-6). */
class ProOffersTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private val prices = listOf(
        ProductPrice(PlayProducts.PRO_SUBSCRIPTION, "annual", "t-annual", "₱1,190.00", "P1Y", freeTrialPeriodIso = "P7D"),
        ProductPrice(PlayProducts.PRO_SUBSCRIPTION, "monthly", "t-monthly", "₱149.00", "P1M"),
        ProductPrice(PlayProducts.PRO_LIFETIME, null, "t-life", "₱3,490.00", null),
        ProductPrice(PlayProducts.CLOUD_ADDON, "annual", "t-cloud-y", "₱490.00", "P1Y"),
        ProductPrice(PlayProducts.CLOUD_ADDON, "monthly", "t-cloud-m", "₱59.00", "P1M"),
    )

    private fun state(vararg purchases: PurchaseRecord) = EntitlementState(PurchaseInterpreter.entitlements(purchases.toList(), now))
    private fun record(product: String, boughtAt: Long = now - day, pending: Boolean = false, renewing: Boolean = true) =
        PurchaseRecord(product, "tok-$product", "GPA.$product", boughtAt, pending = pending, acknowledged = !pending, autoRenewing = renewing)

    private fun cards(s: EntitlementState, p: List<ProductPrice> = prices) = ProOffers.cards(s, p, now).associateBy { it.kind }

    @Test fun free_sees_the_trial_first_then_monthly_and_buy_lifetime() {
        val c = cards(EntitlementState.FREE)
        assertEquals(setOf(PlanCardKind.PRO, PlanCardKind.LIFETIME), c.keys)
        val pro = c.getValue(PlanCardKind.PRO).actions
        assertEquals(listOf("Start 7-day free trial", "Subscribe monthly"), pro.map { it.label })
        assertEquals(listOf(true, false), pro.map { it.primary })
        assertEquals("₱1,190.00 a year · 7-day free trial", pro.first().priceLine)
        // Google Play's subscription policy: renewal, trial conversion and where to cancel, next to the buttons.
        assertEquals(ProOffers.SUBSCRIPTION_TERMS, c.getValue(PlanCardKind.PRO).note)
        assertTrue(ProOffers.SUBSCRIPTION_TERMS.contains("Renews automatically"))
        assertTrue(ProOffers.SUBSCRIPTION_TERMS.contains("free trial becomes a paid subscription"))
        val life = c.getValue(PlanCardKind.LIFETIME)
        assertEquals(listOf("Buy Lifetime"), life.actions.map { it.label })
        assertEquals("₱3,490.00 once", life.actions.single().priceLine)
        assertTrue(life.note!!.startsWith("One-time payment through Google Play"))
    }

    @Test fun without_a_trial_the_yearly_button_says_subscribe_yearly() {
        val noTrial = prices.map { if (it.offerToken == "t-annual") it.copy(freeTrialPeriodIso = null) else it }
        val labels = cards(EntitlementState.FREE, noTrial).getValue(PlanCardKind.PRO).actions.map { it.label }
        assertEquals(listOf("Subscribe yearly", "Subscribe monthly"), labels)
    }

    @Test fun a_subscriber_is_offered_lifetime_as_a_switch_with_the_cancel_reminder() {
        val c = cards(state(record(PlayProducts.PRO_SUBSCRIPTION)))
        assertTrue(c.getValue(PlanCardKind.PRO).current)
        assertEquals(emptyList<OfferAction>(), c.getValue(PlanCardKind.PRO).actions)
        val life = c.getValue(PlanCardKind.LIFETIME)
        assertEquals(listOf("Switch to Lifetime"), life.actions.map { it.label })
        assertTrue(life.note!!.contains("cancel your subscription in Google Play"))
    }

    @Test fun a_lifetime_owner_never_sees_the_subscription_and_gets_cloud_only_once_services_end() {
        val inYearOne = cards(state(record(PlayProducts.PRO_LIFETIME, renewing = false)))
        assertFalse(PlanCardKind.PRO in inYearOne)
        assertTrue(inYearOne.getValue(PlanCardKind.LIFETIME).current)
        assertEquals(emptyList<OfferAction>(), inYearOne.getValue(PlanCardKind.LIFETIME).actions)
        assertEquals("services included this year: nothing to buy", emptyList<OfferAction>(), inYearOne.getValue(PlanCardKind.CLOUD).actions)

        val afterYear = cards(state(record(PlayProducts.PRO_LIFETIME, boughtAt = now - 400 * day, renewing = false)))
        assertEquals(listOf("Add Lucento Cloud yearly", "Add Lucento Cloud monthly"), afterYear.getValue(PlanCardKind.CLOUD).actions.map { it.label })
        assertEquals(ProOffers.SUBSCRIPTION_TERMS, afterYear.getValue(PlanCardKind.CLOUD).note)
        assertNull("nothing to buy, so no terms", inYearOne.getValue(PlanCardKind.CLOUD).note)

        val withCloud = cards(state(record(PlayProducts.PRO_LIFETIME, boughtAt = now - 400 * day, renewing = false), record(PlayProducts.CLOUD_ADDON)))
        assertTrue(withCloud.getValue(PlanCardKind.CLOUD).current)
        assertEquals(emptyList<OfferAction>(), withCloud.getValue(PlanCardKind.CLOUD).actions)
    }

    @Test fun a_pending_payment_replaces_that_plans_buttons_so_nobody_pays_twice() {
        val c = cards(state(record(PlayProducts.PRO_LIFETIME, pending = true, renewing = false)))
        val life = c.getValue(PlanCardKind.LIFETIME)
        assertEquals(emptyList<OfferAction>(), life.actions)
        assertEquals(ProOffers.PENDING_NOTE, life.note)
        assertEquals("the subscription is still offered", 2, c.getValue(PlanCardKind.PRO).actions.size)
    }

    @Test fun no_prices_means_no_buttons_and_no_purchase_notes() {
        val c = cards(EntitlementState.FREE, emptyList())
        assertTrue(c.values.all { it.actions.isEmpty() })
        assertNull(c.getValue(PlanCardKind.LIFETIME).note)
        assertNull(c.getValue(PlanCardKind.PRO).note)
    }

    @Test fun purchase_outcomes_and_play_problems_read_plainly() {
        assertTrue(ProCopy.purchaseResult(PurchaseResult.Completed, PlayProducts.PRO_LIFETIME)!!.startsWith("Pro Lifetime is yours"))
        assertTrue(ProCopy.purchaseResult(PurchaseResult.Completed, PlayProducts.PRO_SUBSCRIPTION)!!.startsWith("Welcome to Lucento Pro"))
        assertEquals(ProOffers.PENDING_NOTE, ProCopy.purchaseResult(PurchaseResult.Pending, PlayProducts.PRO_LIFETIME))
        assertNull("a cancelled sheet says nothing", ProCopy.purchaseResult(PurchaseResult.Cancelled, PlayProducts.PRO_LIFETIME))
        BillingProblem.entries.forEach { assertTrue(it.name, ProCopy.billingProblem(it).isNotBlank()) }
        assertTrue(ProCopy.billingProblem(BillingProblem.NETWORK).contains("Nothing was charged"))
        assertNotNull(ProCopy.verificationIssue(VerifyOutcome.LINKED_ELSEWHERE))
        assertNotNull(ProCopy.verificationIssue(VerifyOutcome.REVIEW_REQUIRED))
        assertNull(ProCopy.verificationIssue(VerifyOutcome.SIGNED_OUT))
    }

    @Test fun the_account_hash_sent_to_play_is_64_hex_characters_and_never_the_id() {
        val id = "3f2a6c1e-0b6d-4c8e-9a51-7d2f4e8b1c90"
        val hashed = PurchaseInterpreter.obfuscatedAccountId(id)
        assertEquals(64, hashed.length)
        assertTrue(hashed.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(hashed.contains(id))
        assertEquals("case and spaces don't change it", hashed, PurchaseInterpreter.obfuscatedAccountId(" ${id.uppercase()} "))
    }
}
