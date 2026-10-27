package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDedupTest {
    private fun c(id: Long, name: String, uuid: String?, kind: String = "EXPENSE", archived: Boolean = false) =
        CategoryDedup.Candidate(id, kind, name, archived, uuid)

    @Test
    fun `copies merge into the one with the smallest sync id, whatever the local order`() {
        assertEquals(mapOf(1L to 2L, 3L to 2L), CategoryDedup.plan(listOf(c(1, "Rent", "m"), c(2, " rent", "a"), c(3, "RENT", "z"))))
    }

    @Test
    fun `different kinds, archived copies and unsynced rows are never merged`() {
        val plan = CategoryDedup.plan(
            listOf(c(1, "Bonus", "a", kind = "INCOME"), c(2, "Bonus", "b"), c(3, "Rent", "c"), c(4, "Rent", "d", archived = true), c(5, "Rent", null))
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `a set budget amount is never replaced by an unset one`() {
        assertEquals(1500L, CategoryDedup.mergedLimit(0, 1500))
        assertEquals(900L, CategoryDedup.mergedLimit(900, 1500))
    }
}
