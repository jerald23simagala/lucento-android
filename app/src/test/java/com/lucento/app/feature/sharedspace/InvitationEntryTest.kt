package com.lucento.app.feature.sharedspace

import com.lucento.app.data.sharedspace.InviteStatus
import com.lucento.app.data.sharedspace.ReceivedInvitation
import com.lucento.app.data.sharedspace.SharedSpaceCache
import com.lucento.app.data.sharedspace.SharedSpaceRepository
import com.lucento.app.data.sharedspace.SharedSpaceSnapshot
import com.lucento.app.data.sharedspace.SpaceInvite
import com.lucento.app.data.sharedspace.SpaceMember
import com.lucento.app.data.sharedspace.SpacePrompt
import com.lucento.app.domain.SpaceRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where an invitation can be reviewed and joined from. Regression for the owner's two-device test (2026-11-05): the
 * invited account created a Shared Space of its own instead of joining, and the invitation then vanished from
 * Settings › Lucento Shared, although the server still lets an Owner of an empty space join (it closes that space).
 */
class InvitationEntryTest {

    private val invitation = ReceivedInvitation(
        id = "inv-1",
        ownerId = "rachel",
        inviterLabel = "Rachel",
        inviterEmail = "rachel@example.com",
        createdAt = "2026-11-05T21:49:36Z",
        expiresAt = "2026-11-12T21:49:36Z",
    )

    private fun ownSpace(
        members: List<SpaceMember> = emptyList(),
        invites: List<SpaceInvite> = emptyList(),
    ) = SharedSpaceSnapshot(
        role = SpaceRole.OWNER,
        ownerId = "jerald",
        ownerLabel = "Jerald",
        members = members,
        invites = invites,
        received = listOf(invitation),
    )

    @Test
    fun `a solo account can review what it received`() {
        assertEquals(listOf(invitation), SpaceMode.Solo(listOf(invitation)).joinableInvitations)
    }

    @Test
    fun `an Owner still sees invitations it received`() {
        assertEquals(listOf(invitation), SpaceMode.Owner(ownSpace()).joinableInvitations)
    }

    @Test
    fun `a Partner has nothing to join, here or on another phone`() {
        assertTrue(SpaceMode.Partner("Rachel", "rachel@example.com", "Jerald").joinableInvitations.isEmpty())
        assertTrue(SpaceMode.Elsewhere("Rachel").joinableInvitations.isEmpty())
        assertTrue(SpaceMode.NeedsAccount.joinableInvitations.isEmpty())
    }

    @Test
    fun `joining closes an Owner's space only while nobody is in it and nothing is waiting`() {
        assertTrue(ownSpace().closesOnJoin)
        val waiting = SpaceInvite("x", "pat@example.com", InviteStatus.PENDING, createdAt = null, expiresAt = null)
        assertFalse(ownSpace(invites = listOf(waiting)).closesOnJoin)
        assertFalse(ownSpace(members = listOf(SpaceMember("pat", "Pat", "pat@example.com", joinedAt = null))).closesOnJoin)
        // Finished invitations don't hold the space open.
        val declined = waiting.copy(status = InviteStatus.DECLINED)
        assertTrue(ownSpace(invites = listOf(declined)).closesOnJoin)
        // Only an Owner's snapshot can close.
        assertFalse(SharedSpaceSnapshot(role = null, received = listOf(invitation)).closesOnJoin)
    }

    @Test
    fun `Home keeps offering the invitation to an Owner of an empty space`() {
        val prompt = SharedSpaceRepository.promptFor(partnerOf = null, cache = SharedSpaceCache(snapshot = ownSpace()))
        assertEquals(SpacePrompt.Invitation(invitation), prompt)
    }

    @Test
    fun `the invitation names its sender and expiry`() {
        assertEquals("From rachel@example.com · Expires ${shortDate(invitation.expiresAt!!)}", invitationMeta(invitation))
        assertEquals("From rachel@example.com", invitationMeta(invitation.copy(expiresAt = null)))
        assertEquals("", invitationMeta(invitation.copy(inviterEmail = "", expiresAt = null)))
    }
}
