package com.lucento.app.data.sharedspace

import com.lucento.app.data.sync.SyncReport
import com.lucento.app.data.sync.SyncResult
import com.lucento.app.domain.SharedAccess
import com.lucento.app.domain.SpaceRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lucento Shared: how the phone joins, leaves and is removed from a Shared Space (docs/shared/UX_FLOW.md). */
class SharedSpaceRepositoryTest {

    private val ownerId = "owner-1"
    private val me = "partner-1"

    private class FakeRemote : SharedSpaceRemote {
        var snapshot = SharedSpaceSnapshot()
        val calls = mutableListOf<String>()
        /** Code to refuse the next call with ("offline", "invite_expired", ...). */
        var refuse: String? = null
        var acceptOwner = "owner-1"

        private fun call(name: String) {
            calls += name
            refuse?.let { refuse = null; throw SharedSpaceException(it) }
        }

        override suspend fun snapshot(): SharedSpaceSnapshot = snapshot.also { call("snapshot") }
        override suspend fun createSpace(ownerLabel: String) = call("create:$ownerLabel")
        override suspend fun setMyLabel(label: String) = call("label:$label")
        override suspend fun invite(email: String): String = "invite-1".also { call("invite:$email") }
        override suspend fun revokeInvite(inviteId: String) = call("revoke:$inviteId")
        override suspend fun acceptInvite(inviteId: String, myLabel: String): String = acceptOwner.also { call("accept:$inviteId:$myLabel") }
        override suspend fun declineInvite(inviteId: String) = call("decline:$inviteId")
        override suspend fun removePartner(partnerId: String) = call("remove:$partnerId")
        override suspend fun leave(): String = acceptOwner.also { call("leave") }
        override suspend fun closeSpace() = call("close")
    }

    private class FakeTransitions(val partnerOf: MutableStateFlow<String?>) : LedgerTransitions {
        val events = mutableListOf<String>()
        var syncResult: SyncResult = SyncResult.Success(SyncReport())
        var unsynced = false

        override suspend fun joinSpace(ownerId: String) {
            events += "join:$ownerId"
            partnerOf.value = ownerId
        }

        override suspend fun returnToOwnLedger() {
            events += "return"
            partnerOf.value = null
        }

        override suspend fun sync(): SyncResult = syncResult.also { events += "sync" }
        override suspend fun hasUnsyncedChanges(): Boolean = unsynced
    }

    private class Setup(scope: TestScope, partner: String? = null) {
        val remote = FakeRemote()
        val store = InMemorySharedSpaceStore()
        val partnerOf = MutableStateFlow(partner)
        val transitions = FakeTransitions(partnerOf)
        val repo = SharedSpaceRepository(
            remote = remote, store = store, partnerOf = partnerOf, readPartnerOf = { partnerOf.value },
            signedInUserId = MutableStateFlow("partner-1"), transitions = transitions, scope = scope, clock = { 1_000L },
        )
    }

    private val invitation = ReceivedInvitation("inv-1", "owner-1", "Olivia", "olivia@example.com", null, null)

    @Test
    fun `joining accepts on the server first, then switches this phone and downloads the space`() = runTest {
        val s = Setup(this)
        s.remote.snapshot = SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia")

        val result = s.repo.join(invitation, "  Pat  ")

        assertEquals(JoinResult.Joined(ownerId, downloaded = true), result)
        assertEquals("accept:inv-1:Pat", s.remote.calls.first())
        assertEquals(listOf("join:$ownerId", "sync"), s.transitions.events)
        assertEquals(SharedAccess(SpaceRole.PARTNER, ownerName = "Olivia"), s.repo.access.first())
    }

    @Test
    fun `a refused invitation changes nothing on this phone`() = runTest {
        val s = Setup(this)
        s.remote.refuse = "invite_expired"

        assertEquals(JoinResult.Failed("invite_expired"), s.repo.join(invitation, "Pat"))
        assertTrue(s.transitions.events.isEmpty())
        assertEquals(SharedAccess.Solo, s.repo.access.first())
    }

    @Test
    fun `a join whose first download fails is still a join - the space downloads at the next sync`() = runTest {
        val s = Setup(this)
        s.transitions.syncResult = SyncResult.Retryable("Couldn't reach the server")

        assertEquals(JoinResult.Joined(ownerId, downloaded = false), s.repo.join(invitation, "Pat"))
        assertEquals(ownerId, s.partnerOf.value)
    }

    @Test
    fun `leaving ends access on the server first, then returns the phone to the person's own account`() = runTest {
        val s = Setup(this, partner = ownerId)
        s.store.saveSnapshot(SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia"), 1L)

        assertEquals(SpaceActionResult.Done, s.repo.leave())

        assertEquals(listOf("leave"), s.remote.calls)
        assertEquals(listOf("return"), s.transitions.events)
        assertEquals(SpaceNotice(SpaceNotice.Kind.LEFT, "Olivia"), s.repo.takeNotice())
        assertNull("shown once", s.repo.takeNotice())
        assertNull(s.store.current().snapshot)
    }

    @Test
    fun `leaving offline changes nothing`() = runTest {
        val s = Setup(this, partner = ownerId)
        s.remote.refuse = SharedSpaceException.OFFLINE

        assertEquals(SpaceActionResult.Failed(SharedSpaceException.OFFLINE), s.repo.leave())
        assertTrue(s.transitions.events.isEmpty())
        assertEquals(ownerId, s.partnerOf.value)
    }

    @Test
    fun `leaving after already being removed still gives the ledger back`() = runTest {
        val s = Setup(this, partner = ownerId)
        s.remote.refuse = "not_partner"

        assertEquals(SpaceActionResult.Done, s.repo.leave())
        assertEquals(listOf("return"), s.transitions.events)
    }

    @Test
    fun `removal found by sync returns the phone and says who ended it`() = runTest {
        val s = Setup(this, partner = ownerId)
        s.store.rememberName(ownerId, "Olivia")

        s.repo.endedByOwner(ownerId)

        assertEquals(listOf("return"), s.transitions.events)
        assertEquals(SpaceNotice(SpaceNotice.Kind.REMOVED, "Olivia"), s.repo.takeNotice())
    }

    @Test
    fun `a stale removal for a space this phone no longer holds does nothing`() = runTest {
        val s = Setup(this, partner = "someone-else")
        s.repo.endedByOwner(ownerId)
        assertTrue(s.transitions.events.isEmpty())
        assertNull(s.repo.takeNotice())
    }

    @Test
    fun `what this phone may change follows the binding for partners and the snapshot for owners`() = runTest {
        val partner = Setup(this, partner = ownerId)
        val p = partner.repo.access.first()
        assertTrue(p.isPartner)
        assertFalse(p.canManageStructure)
        assertEquals("the Owner", p.ownerDisplay)

        val owner = Setup(this)
        owner.store.saveSnapshot(SharedSpaceSnapshot(role = SpaceRole.OWNER, ownerId = "me", ownerLabel = "Olivia"), 1L)
        val o = owner.repo.access.first()
        assertEquals(SpaceRole.OWNER, o.role)
        assertTrue(o.canManageStructure)

        assertEquals(SharedAccess.Solo, Setup(this).repo.access.first())
    }

    @Test
    fun `invitations are checked on the phone first and sent normalised`() = runTest {
        val s = Setup(this)
        assertEquals(SpaceActionResult.Failed("invalid_email"), s.repo.invite("not an email"))
        assertTrue(s.remote.calls.isEmpty())

        assertEquals(SpaceActionResult.Done, s.repo.invite("  Alex@Example.COM "))
        assertEquals("invite:alex@example.com", s.remote.calls.first())
        assertEquals("snapshot", s.remote.calls.last()) // the screen refreshes right after
    }

    @Test
    fun `names for attribution come from the space and stay after someone leaves`() = runTest {
        val s = Setup(this, partner = ownerId)
        s.store.saveSnapshot(
            SharedSpaceSnapshot(
                role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia",
                members = listOf(SpaceMember("partner-1", "Pat", "pat@example.com", null)),
            ),
            1L,
        )
        s.store.saveSnapshot(SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia"), 2L)
        val people = s.repo.people.first()

        assertEquals("you", people.nameOf("partner-1"))
        assertEquals("Olivia", people.nameOf(ownerId))
        assertEquals("Olivia", people.attribution(createdBy = null, updatedBy = null)!!.addedBy)
    }

    @Test
    fun `a failed refresh keeps what this phone already knew`() = runTest {
        val s = Setup(this)
        s.store.saveSnapshot(SharedSpaceSnapshot(role = SpaceRole.OWNER, ownerId = "me", ownerLabel = "Olivia"), 1L)
        s.remote.refuse = SharedSpaceException.OFFLINE

        assertEquals(SpaceActionResult.Failed(SharedSpaceException.OFFLINE), s.repo.refresh())
        assertEquals(SpaceRole.OWNER, s.store.current().snapshot?.role)
    }

    // ---------------------------------------------------------------- onboarding, and accounts that joined on another phone

    @Test
    fun `onboarding gives a Partner's account its space instead of its own ledger`() = runTest {
        val s = Setup(this)
        s.remote.snapshot = SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia")

        assertTrue(s.repo.adoptMembership())

        assertEquals("bound and emptied; onboarding's restore downloads it", listOf("join:$ownerId"), s.transitions.events)
        assertEquals(SharedAccess(SpaceRole.PARTNER, ownerName = "Olivia"), s.repo.access.first())
    }

    @Test
    fun `a phone that already holds the space is not emptied again`() = runTest {
        val s = Setup(this, partner = ownerId)
        s.remote.snapshot = SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia")

        assertTrue(s.repo.adoptMembership())
        assertTrue(s.transitions.events.isEmpty())
    }

    @Test
    fun `an account in no space carries on with its own setup and is offered its invitation`() = runTest {
        val s = Setup(this)
        s.remote.snapshot = SharedSpaceSnapshot(received = listOf(invitation))

        assertFalse(s.repo.adoptMembership())
        assertTrue(s.transitions.events.isEmpty())
        assertEquals(invitation, s.repo.invitationToOffer())
    }

    @Test
    fun `a phone still holding a space its account is no longer part of gives it back first`() = runTest {
        val s = Setup(this, partner = ownerId)
        s.store.rememberName(ownerId, "Olivia")
        s.transitions.unsynced = true
        s.remote.snapshot = SharedSpaceSnapshot()

        assertFalse(s.repo.adoptMembership())

        assertEquals(listOf("return"), s.transitions.events)
        assertEquals(SpaceNotice(SpaceNotice.Kind.REMOVED, "Olivia", unsyncedLost = true), s.repo.takeNotice())
    }

    @Test
    fun `a Partner of another space is moved to it`() = runTest {
        val s = Setup(this, partner = "earlier-owner")
        s.remote.snapshot = SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia")

        assertTrue(s.repo.adoptMembership())
        assertEquals(listOf("join:$ownerId"), s.transitions.events)
    }

    @Test
    fun `offline, onboarding keeps whatever the phone holds`() = runTest {
        val bound = Setup(this, partner = ownerId)
        bound.remote.refuse = SharedSpaceException.OFFLINE
        assertTrue("the restore retries the space it holds", bound.repo.adoptMembership())

        val solo = Setup(this)
        solo.remote.refuse = SharedSpaceException.OFFLINE
        assertFalse(solo.repo.adoptMembership())

        assertTrue(bound.transitions.events.isEmpty())
        assertTrue(solo.transitions.events.isEmpty())
    }

    @Test
    fun `an account that joined on another phone is prompted and can open the space here`() = runTest {
        val s = Setup(this)
        s.remote.snapshot = SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia")
        s.repo.refresh()
        assertEquals(SpacePrompt.JoinedElsewhere(ownerId, "Olivia"), s.repo.prompt.first())

        assertEquals(JoinResult.Joined(ownerId, downloaded = true), s.repo.openMembershipHere())

        assertEquals(listOf("join:$ownerId", "sync"), s.transitions.events)
        assertNull("no prompt once the phone holds the space", s.repo.prompt.first())
    }

    @Test
    fun `opening a space here needs the account to be a Partner`() = runTest {
        val s = Setup(this)
        s.remote.snapshot = SharedSpaceSnapshot()

        assertEquals(JoinResult.Failed("not_partner"), s.repo.openMembershipHere())
        assertTrue(s.transitions.events.isEmpty())
    }

    @Test
    fun `leaving from a phone that shows the person's own ledger keeps that ledger`() = runTest {
        val s = Setup(this)
        s.remote.snapshot = SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia")
        s.repo.refresh()
        s.remote.snapshot = SharedSpaceSnapshot()

        assertEquals(SpaceActionResult.Done, s.repo.leave())

        assertTrue("nothing wiped", s.transitions.events.isEmpty())
        assertNull(s.repo.takeNotice())
        assertNull(s.store.current().snapshot?.role)
    }

    @Test
    fun `leaving with unsynced changes says they were lost`() = runTest {
        val s = Setup(this, partner = ownerId)
        s.store.saveSnapshot(SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, ownerLabel = "Olivia"), 1L)
        s.transitions.unsynced = true

        s.repo.leave()

        assertEquals(SpaceNotice(SpaceNotice.Kind.LEFT, "Olivia", unsyncedLost = true), s.repo.takeNotice())
    }

    @Test
    fun `Home's prompt stays away once put aside and never shows on a phone holding a space`() = runTest {
        val s = Setup(this)
        s.remote.snapshot = SharedSpaceSnapshot(received = listOf(invitation))
        s.repo.refresh()
        assertEquals(SpacePrompt.Invitation(invitation), s.repo.prompt.first())

        s.repo.dismiss(invitation.id)
        assertNull(s.repo.prompt.first())
        assertNull("set aside in onboarding as well", s.repo.invitationToOffer())

        val partner = Setup(this, partner = ownerId)
        partner.remote.snapshot = SharedSpaceSnapshot(role = SpaceRole.PARTNER, ownerId = ownerId, received = listOf(invitation))
        partner.repo.refresh()
        assertNull(partner.repo.prompt.first())
        assertNull(partner.repo.invitationToOffer())
    }

    @Test
    fun `the notice mentions lost changes only when there were some and speaks only about this phone`() {
        val kept = SpaceNotice(SpaceNotice.Kind.REMOVED, "Olivia")
        val lost = kept.copy(unsyncedLost = true)
        val left = SpaceNotice(SpaceNotice.Kind.LEFT, "Olivia")

        assertFalse(kept.body.contains("couldn’t be kept"))
        assertTrue(lost.body.contains("couldn’t be kept"))
        assertEquals("You left Olivia’s Shared Space", left.title)
        listOf(kept, lost, left).forEach {
            assertTrue(it.body.contains("this phone"))
            assertFalse(it.body.contains("other phone", ignoreCase = true))
            assertFalse(it.body.contains("device", ignoreCase = true))
        }
    }

    @Test
    fun `without cloud sync in this build Lucento Shared says so and does nothing`() = runTest {
        val repo = SharedSpaceRepository(
            remote = null, store = InMemorySharedSpaceStore(), partnerOf = MutableStateFlow(null), readPartnerOf = { null },
            signedInUserId = MutableStateFlow(null), transitions = FakeTransitions(MutableStateFlow(null)), scope = this,
        )
        assertFalse(repo.available)
        assertEquals(SpaceActionResult.Failed(SharedSpaceException.UNAVAILABLE), repo.createSpace("Olivia"))
        assertEquals(JoinResult.Failed(SharedSpaceException.UNAVAILABLE), repo.join(invitation, "Pat"))
    }
}
