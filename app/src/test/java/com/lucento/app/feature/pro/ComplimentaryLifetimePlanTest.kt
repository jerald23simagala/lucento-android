package com.lucento.app.feature.pro

import com.lucento.app.data.billing.ProductPrice
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.PlayProducts
import com.lucento.app.domain.entitlement.PurchaseInterpreter
import com.lucento.app.domain.entitlement.PurchaseRecord
import com.lucento.app.domain.entitlement.Verifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

/**
 * Review M5: a complimentary Pro Lifetime from Mission Control (a GRANT phone row for lucento_pro_lifetime plus a GRANT
 * services row ending after 365 days) on the Lucento Plan page, the plan row and the member mark. It reads as Lifetime,
 * with the existing Lifetime words; nothing offers to sell it Lifetime again.
 */
class ComplimentaryLifetimePlanTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L // 2027-01-15 08:00 UTC
    private val servicesEnd = now + 355 * day // 2028-01-05

    private val prices = listOf(
        ProductPrice(PlayProducts.PRO_SUBSCRIPTION, "annual", "t-annual", "₱1,190.00", "P1Y", freeTrialPeriodIso = "P7D"),
        ProductPrice(PlayProducts.PRO_SUBSCRIPTION, "monthly", "t-monthly", "₱149.00", "P1M"),
        ProductPrice(PlayProducts.PRO_LIFETIME, null, "t-life", "₱3,490.00", null),
        ProductPrice(PlayProducts.CLOUD_ADDON, "annual", "t-cloud-y", "₱490.00", "P1Y"),
        ProductPrice(PlayProducts.CLOUD_ADDON, "monthly", "t-cloud-m", "₱59.00", "P1M"),
    )

    private fun grant(product: String?, grants: Set<Capability>, expiresAt: Long?, status: EntitlementStatus = EntitlementStatus.ACTIVE) =
        Entitlement(
            source = EntitlementSource.GRANT, status = status, grants = grants, startsAtMillis = now - 10 * day,
            expiresAtMillis = expiresAt, productId = product, verifiedAtMillis = now - 1000, verifiedBy = Verifier.SERVER,
        )

    private val phoneRow = grant(PlayProducts.PRO_LIFETIME, setOf(Capability.LOCAL_PRO), expiresAt = null)
    private val servicesRow = grant(null, setOf(Capability.CLOUD, Capability.AI_PRO, Capability.SHARED), expiresAt = servicesEnd)
    private val complimentary = EntitlementState(listOf(phoneRow, servicesRow))
    private val afterTheYear = EntitlementState(listOf(phoneRow, servicesRow.copy(status = EntitlementStatus.EXPIRED, expiresAtMillis = now - 5 * day)))

    private fun cards(state: EntitlementState) = ProOffers.cards(state, prices, now).associateBy { it.kind }
    private fun status(state: EntitlementState) = ProCopy.planStatus(state, now, ZoneOffset.UTC, Locale.US)

    @Test fun nothing_offers_lifetime_or_a_subscription_to_a_complimentary_lifetime() {
        val c = cards(complimentary)
        assertFalse("no Pro subscription card", PlanCardKind.PRO in c)
        val lifetime = c.getValue(PlanCardKind.LIFETIME)
        assertTrue(lifetime.current)
        assertEquals(emptyList<OfferAction>(), lifetime.actions)
        assertNull(lifetime.note)
        assertEquals("services included this year: nothing to buy", emptyList<OfferAction>(), c.getValue(PlanCardKind.CLOUD).actions)
        val labels = c.values.flatMap { card -> card.actions.map { it.label } }
        assertFalse(labels.any { it == "Buy Lifetime" || it == "Switch to Lifetime" })
    }

    @Test fun once_its_services_year_ends_lucento_cloud_is_offered_as_to_any_lifetime_owner() {
        val c = cards(afterTheYear)
        assertFalse(PlanCardKind.PRO in c)
        assertEquals(emptyList<OfferAction>(), c.getValue(PlanCardKind.LIFETIME).actions)
        assertEquals(listOf("Add Lucento Cloud yearly", "Add Lucento Cloud monthly"), c.getValue(PlanCardKind.CLOUD).actions.map { it.label })
    }

    @Test fun the_plan_row_uses_the_lifetime_words_and_the_services_rows_date() {
        val withServices = status(complimentary)
        assertEquals("Pro Lifetime · courtesy", withServices.title)
        assertEquals(
            "Cloud sync, Lucento Shared and the AI adviser’s Pro allowance are included until Jan 5, 2028.",
            withServices.detail,
        )
        assertEquals(ProCopy.ServiceTerm.Until("Jan 5, 2028"), ProCopy.serviceTerm(complimentary, now, ZoneOffset.UTC, Locale.US))

        val phoneOnly = status(afterTheYear)
        assertEquals("Pro Lifetime · courtesy", phoneOnly.title)
        assertTrue(phoneOnly.detail.startsWith("Every Pro feature that runs on this phone is yours."))
        assertNull("no online services left to put a term on", ProCopy.serviceTerm(afterTheYear, now, ZoneOffset.UTC, Locale.US))
    }

    @Test fun the_member_mark_and_plan_name_say_lifetime() {
        assertEquals("Lifetime", ProCopy.memberMark(complimentary, now))
        assertEquals("Pro Lifetime", ProCopy.planName(complimentary.displayPlan(now)))
        assertEquals("Lifetime", ProCopy.memberMark(afterTheYear, now))
    }

    @Test fun a_bought_lifetime_still_says_you_own_it() {
        val bought = EntitlementState(
            PurchaseInterpreter.entitlements(
                listOf(PurchaseRecord(PlayProducts.PRO_LIFETIME, "tok-life", "GPA.life", now - day, pending = false, acknowledged = true, autoRenewing = false)),
                now,
            ),
        )
        assertEquals("Pro Lifetime · you own it", status(bought).title)
        assertEquals("Pro Lifetime · you own it", status(EntitlementState(bought.entitlements + phoneRow + servicesRow)).title)
    }

    @Test fun a_ninety_day_courtesy_grant_reads_as_before() {
        val ninety = EntitlementState(listOf(grant(null, Capability.entries.toSet(), expiresAt = now + 80 * day)))
        assertEquals("Lucento Pro", status(ninety).title)
        assertTrue(status(ninety).detail.startsWith("Courtesy access until "))
        assertEquals(listOf("Buy Lifetime"), cards(ninety).getValue(PlanCardKind.LIFETIME).actions.map { it.label })
        assertEquals("Pro", ProCopy.memberMark(ninety, now))
    }
}
