package com.lucento.app.feature.account

import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Entitlement
import com.lucento.app.domain.entitlement.EntitlementSource
import com.lucento.app.domain.entitlement.EntitlementState
import com.lucento.app.domain.entitlement.EntitlementStatus
import com.lucento.app.domain.entitlement.Verifier
import com.lucento.app.feature.legal.LegalDocuments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun row(
        source: EntitlementSource,
        status: EntitlementStatus = EntitlementStatus.ACTIVE,
        expires: Long? = now + 30 * day,
        grants: Set<Capability> = setOf(Capability.LOCAL_PRO, Capability.CLOUD),
    ) = Entitlement(
        source = source,
        status = status,
        grants = grants,
        startsAtMillis = now - day,
        expiresAtMillis = expires,
        productId = "p",
        verifiedAtMillis = now,
        verifiedBy = Verifier.SERVER,
    )

    @Test
    fun `a free account has nothing from Google Play to mention`() {
        assertEquals(DeletionPlan(), DeletionPlan.of(EntitlementState.FREE, now))
        assertEquals(DeletionPlan(), DeletionPlan.of(null, now))
    }

    @Test
    fun `a live subscription or Cloud add-on asks to cancel in Google Play`() {
        assertTrue(DeletionPlan.of(EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION))), now).subscribed)
        assertTrue(DeletionPlan.of(EntitlementState(listOf(row(EntitlementSource.PLAY_CLOUD_ADDON))), now).subscribed)
        // Cancelled but paid up: it no longer renews, yet still worth the reminder until it ends.
        assertTrue(
            DeletionPlan.of(EntitlementState(listOf(row(EntitlementSource.PLAY_SUBSCRIPTION, EntitlementStatus.CANCELLED_ACTIVE))), now).subscribed,
        )
    }

    @Test
    fun `an ended subscription and a grant are not mentioned`() {
        val ended = row(EntitlementSource.PLAY_SUBSCRIPTION, expires = now - 10 * day)
        val grant = row(EntitlementSource.GRANT)
        assertEquals(DeletionPlan(), DeletionPlan.of(EntitlementState(listOf(ended, grant)), now))
    }

    @Test
    fun `Lifetime is mentioned as restorable, with the add-on as a subscription`() {
        val lifetime = row(EntitlementSource.PLAY_LIFETIME, expires = null, grants = setOf(Capability.LOCAL_PRO))
        assertEquals(DeletionPlan(lifetime = true), DeletionPlan.of(EntitlementState(listOf(lifetime)), now))
        val both = DeletionPlan.of(EntitlementState(listOf(lifetime, row(EntitlementSource.PLAY_CLOUD_ADDON))), now)
        assertEquals(DeletionPlan(subscribed = true, lifetime = true), both)
    }

    @Test
    fun `the confirmation says what goes and adds the Play notes only when they apply`() {
        val plain = AccountDeletionCopy.paragraphs(DeletionPlan())
        assertEquals(3, plain.size)
        assertTrue(plain[0].contains("permanently deletes"))
        assertTrue(plain.any { it.contains("Shared Space") })
        assertTrue(plain.any { it.contains("backup file") })
        assertFalse(plain.any { it.contains("Google Play") })

        val paid = AccountDeletionCopy.paragraphs(DeletionPlan(subscribed = true, lifetime = true))
        assertEquals(5, paid.size)
        assertTrue(paid.any { it.contains("doesn’t cancel your Lucento subscription") })
        assertTrue(paid.any { it.contains("restore it later") })
    }

    @Test
    fun `already deleted counts as done, a lost session asks to sign in, anything else changes nothing`() {
        assertEquals(DeleteAccountResult.Deleted, DeleteAccountFailures.fromServer(400, "P0002"))
        assertEquals(DeleteAccountResult.Failed(DeleteAccountFailures.SIGNED_OUT), DeleteAccountFailures.fromServer(401, null))
        assertEquals(DeleteAccountResult.Failed(DeleteAccountFailures.SIGNED_OUT), DeleteAccountFailures.fromServer(400, "28000"))
        // The function isn't on the server yet (PostgREST's PGRST202, 404): the person is told how to ask instead.
        val missing = DeleteAccountFailures.fromServer(404, "PGRST202")
        assertEquals(DeleteAccountResult.Failed(DeleteAccountFailures.UNAVAILABLE), missing)
        assertTrue(DeleteAccountFailures.UNAVAILABLE.contains(LegalDocuments.CONTACT_EMAIL))
        assertTrue(DeleteAccountFailures.UNAVAILABLE.contains("Nothing was changed"))
    }

    @Test
    fun `both legal documents name the contact address and the deletion page`() {
        val privacy = LegalDocuments.privacy.sections.flatMap { it.items }.joinToString(" ")
        val terms = LegalDocuments.terms.sections.flatMap { it.items }.joinToString(" ")
        assertTrue(privacy.contains(LegalDocuments.CONTACT_EMAIL))
        assertTrue(privacy.contains(LegalDocuments.DELETE_ACCOUNT_URL))
        assertTrue(privacy.contains("Settings › Cloud sync › Delete account"))
        assertTrue(terms.contains(LegalDocuments.CONTACT_EMAIL))
        assertFalse(privacy.contains("isn’t available yet"))
        assertFalse((privacy + terms).contains("in development"))
    }
}
