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

    private fun l(minor: Long, at: Long, uuid: String? = "u$minor") = CategoryDedup.Limit(minor, at, uuid)

    @Test
    fun `a set budget amount is never replaced by an unset one`() {
        assertEquals(1500L, CategoryDedup.mergedLimit(l(0, 999), l(1500, 1)))
        assertEquals(900L, CategoryDedup.mergedLimit(l(900, 1), l(0, 999)))
    }

    @Test
    fun `P1-8 - the most recently edited amount wins, whichever copy keeps the category`() {
        // Device QA C-1: the account's Rent 900 (edited later) must not be replaced by the phone's older Rent 500.
        assertEquals(900L, CategoryDedup.mergedLimit(l(500, 100), l(900, 200)))
        assertEquals(900L, CategoryDedup.mergedLimit(l(900, 200), l(500, 100)))
    }

    @Test
    fun `P1-8 - the result is the same in either order, including exact ties`() {
        val a = l(500, 100, "aaa")
        val b = l(900, 100, "bbb")
        assertEquals(CategoryDedup.mergedLimit(a, b), CategoryDedup.mergedLimit(b, a))
        assertEquals(500L, CategoryDedup.mergedLimit(a, b)) // tie: the smaller sync id's amount
        assertEquals(CategoryDedup.mergedLimit(l(1, 5, null), l(2, 5, "x")), CategoryDedup.mergedLimit(l(2, 5, "x"), l(1, 5, null)))
    }

    @Test
    fun `three or more copies end on the latest edit in every fold order`() {
        // QA note 1: K=500 at t1, C1=700 at t2, C2=900 at t3 must end at 900, not 700.
        val copies = listOf(l(500, 1), l(700, 2), l(900, 3), l(0, 4))
        val orders = listOf(copies, copies.reversed(), listOf(copies[2], copies[0], copies[3], copies[1]))
        orders.forEach { order ->
            assertEquals(900L, order.reduce(CategoryDedup::winner).minor)
        }
    }
}
