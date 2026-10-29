package com.lucento.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictCountTest {
    @Test fun neverSyncedSeedAdoptedOnSignInIsNotAConflict() {
        assertFalse(countsAsConflict(PullDecision.UPDATE, serverWins = true, localExists = true, localDirty = true, localBaseRevision = null, remoteRevision = 4))
        assertFalse(countsAsConflict(PullDecision.KEEP_LOCAL, serverWins = false, localExists = true, localDirty = true, localBaseRevision = null, remoteRevision = 4))
    }

    @Test fun editOfAKnownVersionRejectedByTheServerIsAConflict() {
        assertTrue(countsAsConflict(PullDecision.UPDATE, serverWins = true, localExists = true, localDirty = true, localBaseRevision = 2, remoteRevision = 3))
    }

    @Test fun rejectedDeleteComingBackIsAConflict() {
        assertTrue(countsAsConflict(PullDecision.INSERT, serverWins = true, localExists = false, localDirty = false, localBaseRevision = null, remoteRevision = 3))
    }

    @Test fun causalSuccessorIsNotAConflict() {
        assertFalse(countsAsConflict(PullDecision.KEEP_LOCAL, serverWins = false, localExists = true, localDirty = true, localBaseRevision = 3, remoteRevision = 3))
    }

    @Test fun plainNewRemoteRowIsNotAConflict() {
        assertFalse(countsAsConflict(PullDecision.INSERT, serverWins = false, localExists = false, localDirty = false, localBaseRevision = null, remoteRevision = 1))
    }
}
