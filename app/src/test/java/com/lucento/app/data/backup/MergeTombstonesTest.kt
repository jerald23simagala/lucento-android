package com.lucento.app.data.backup

import com.lucento.app.domain.backup.BackupDeletion
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeTombstonesTest {
    private val d1 = BackupDeletion(table = "categories", id = "a", deletedAt = 1L)
    private val d2 = BackupDeletion(table = "transactions", id = "b", deletedAt = 2L)

    @Test fun identicalMergeAddsNoTombstonesEvenWhenAlreadyPushed() {
        assertEquals(emptyList<BackupDeletion>(), tombstonesToAdd(listOf(d1, d2), setOf("categories" to "a", "transactions" to "b")))
    }

    @Test fun onlyUnknownDeletionsAreAdded() {
        assertEquals(listOf(d2), tombstonesToAdd(listOf(d1, d2), setOf("categories" to "a")))
    }

    @Test fun duplicateDeletionsInABackupAreAddedOnce() {
        assertEquals(listOf(d1), tombstonesToAdd(listOf(d1, d1.copy(deletedAt = 9L)), emptySet()))
    }
}
