package com.lucento.app.domain

import com.lucento.app.data.sharedspace.InviteStatus
import com.lucento.app.data.sharedspace.SharedSpaceException
import com.lucento.app.data.sharedspace.SharedSpaceSnapshot
import com.lucento.app.data.sharedspace.normalizedEmail
import com.lucento.app.data.sharedspace.sharedSpaceMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lucento Shared's pure rules: names on attribution, labels, roles, and reading the database's answers. */
class SharedSpaceDomainTest {

    private val owner = "owner-id"
    private val partner = "partner-id"
    private val names = mapOf(owner to "Olivia", partner to "Pat")

    @Test
    fun `a solo phone sees no attribution for its own entries`() {
        val solo = PeopleDirectory(myId = "me")
        assertNull(solo.attribution(createdBy = "me", updatedBy = null))
        assertNull(solo.attribution(createdBy = null, updatedBy = null))
    }

    @Test
    fun `inside a space each entry says who added it, in names`() {
        val onOwnersPhone = PeopleDirectory(myId = owner, ownerId = owner, names = names)
        assertEquals(Attribution("Pat", null), onOwnersPhone.attribution(createdBy = partner, updatedBy = null))
        assertEquals(Attribution("you", null), onOwnersPhone.attribution(createdBy = owner, updatedBy = null))

        val onPartnersPhone = PeopleDirectory(myId = partner, ownerId = owner, names = names)
        assertEquals(Attribution("Olivia", "you"), onPartnersPhone.attribution(createdBy = owner, updatedBy = partner))
    }

    @Test
    fun `rows from before attribution existed read as the owner's`() {
        val onPartnersPhone = PeopleDirectory(myId = partner, ownerId = owner, names = names)
        assertEquals(Attribution("Olivia", null), onPartnersPhone.attribution(createdBy = null, updatedBy = null))
    }

    @Test
    fun `an edit is mentioned only when someone other than the creator made it`() {
        val p = PeopleDirectory(myId = owner, ownerId = owner, names = names)
        assertEquals(Attribution("Pat", null), p.attribution(createdBy = partner, updatedBy = partner))
        assertEquals(Attribution("Pat", "you"), p.attribution(createdBy = partner, updatedBy = owner))
    }

    @Test
    fun `someone this phone never learned is named plainly, and history after the space ends keeps showing`() {
        val afterSpace = PeopleDirectory(myId = owner, ownerId = null, names = names)
        assertEquals(Attribution("Pat", null), afterSpace.attribution(createdBy = partner, updatedBy = null))
        assertEquals("someone else", afterSpace.nameOf("unknown"))
    }

    @Test
    fun `wallet labels read only known values`() {
        assertEquals(WalletClassification.PARTNER, WalletClassification.fromKey("PARTNER"))
        assertNull(WalletClassification.fromKey("HOUSEHOLD_V2"))
        assertNull(WalletClassification.fromKey(null))
        assertEquals(setOf("SHARED", "OWNER", "PARTNER"), WalletClassification.NAMES)
    }

    @Test
    fun `only a partner's phone is kept from changing the structure`() {
        assertTrue(SharedAccess.Solo.canManageStructure)
        assertFalse(SharedAccess.Solo.inSpace)
        assertTrue(SharedAccess(SpaceRole.OWNER).canManageStructure)
        assertFalse(SharedAccess(SpaceRole.PARTNER).canManageStructure)
        assertEquals("Olivia", SharedAccess(SpaceRole.PARTNER, "Olivia").ownerDisplay)
        assertEquals("the Owner", SharedAccess(SpaceRole.PARTNER, " ").ownerDisplay)
    }

    @Test
    fun `display names prefer the chosen label, then the email's name`() {
        assertEquals("Olivia", displayNameOf(" Olivia ", "o@example.com"))
        assertEquals("olivia.r", displayNameOf("", "olivia.r@example.com"))
        assertNull(displayNameOf(null, null))
    }

    @Test
    fun `the database's answer is read field by field, tolerating what this version doesn't know`() {
        val json = Json.parseToJsonElement(
            """
            {"role":"OWNER","owner_id":"owner-id","owner_label":"Olivia","owner_email":"olivia@example.com","max_partners":1,
             "members":[{"partner_id":"partner-id","label":"","email":"pat@example.com","joined_at":"2026-11-05T10:00:00Z"}],
             "invites":[{"id":"i1","email":"pat@example.com","status":"accepted","created_at":"2026-11-04T10:00:00Z"},
                        {"id":"i2","email":"x@example.com","status":"something_new"}],
             "received":[],"future_field":42}
            """.trimIndent()
        ).jsonObject
        val s = SharedSpaceSnapshot.fromJson(json)

        assertEquals(SpaceRole.OWNER, s.role)
        assertEquals("Olivia", s.ownerName)
        assertEquals("pat", s.members.single().displayName)
        assertEquals(InviteStatus.ACCEPTED, s.invites[0].status)
        assertEquals(InviteStatus.PENDING, s.invites[1].status)
        assertEquals(0, s.openPlaces) // one place: held by the Partner (and the unreadable invitation reads as pending)
        assertNull(SharedSpaceSnapshot.fromJson(Json.parseToJsonElement("""{"role":"ADMIN"}""").jsonObject).role)
    }

    @Test
    fun `refusal codes are read out of the database's message`() {
        assertEquals("space_full", SharedSpaceException.codeOf("lucento_space:space_full"))
        assertEquals("invite_expired", SharedSpaceException.codeOf("lucento_space:invite_expired (details)"))
        assertNull(SharedSpaceException.codeOf("permission denied for table wallets"))
        assertNull(SharedSpaceException.codeOf(null))
    }

    @Test
    fun `every refusal has a plain sentence`() {
        listOf(
            "invalid_email", "self_invite", "already_invited", "already_member", "space_full", "no_space", "already_partner",
            "owns_space", "own_space", "email_unverified", "invite_not_found", "invite_accepted", "invite_declined",
            "invite_revoked", "invite_expired", "partner_not_found", "not_partner", "has_partners", "offline", "unavailable",
            "not_authenticated", "anything else",
        ).forEach { code ->
            val text = sharedSpaceMessage(code, other = "Olivia")
            assertTrue(code, text.isNotBlank() && !text.contains("lucento_space"))
        }
        assertTrue(sharedSpaceMessage("invite_revoked", "Olivia").startsWith("Olivia"))
    }

    @Test
    fun `invitation emails are trimmed and lowercased, and junk is refused`() {
        assertEquals("alex@example.com", normalizedEmail("  Alex@Example.COM "))
        assertNull(normalizedEmail("alex"))
        assertNull(normalizedEmail("alex@example"))
        assertNull(normalizedEmail("al ex@example.com"))
    }

    @Test
    fun `the shared indicator names the other person only once the ledger is really shared`() {
        // Solo, and an Owner whose invitation nobody has accepted yet: nothing is shared, so no indicator.
        assertNull(SharedAccess.Solo.sharedWith)
        assertNull(SharedAccess(SpaceRole.OWNER, ownerName = "Rachel").sharedWith)
        // The Owner's phone names the Partner; the Partner's phone names the Owner.
        assertEquals("Jerald", SharedAccess(SpaceRole.OWNER, ownerName = "Rachel", partnerName = "Jerald").sharedWith)
        assertEquals("Rachel", SharedAccess(SpaceRole.PARTNER, ownerName = "Rachel").sharedWith)
        // A Partner whose phone never learned the Owner's name still sees that the ledger is shared.
        assertEquals("the Owner", SharedAccess(SpaceRole.PARTNER).sharedWith)
    }
}
