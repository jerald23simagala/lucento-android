package com.lucento.app.domain.capture

import com.lucento.app.domain.Category
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner, 2026-11-23: Gemini is for Ask Lucento, so typed and spoken messages know businesses the way scans do. When a
 * row's own words (category names and keywords) name no category, the person's usual category for that business comes
 * next, learned from their own entries, then the merchant dictionary's. An unknown business still asks.
 *
 * The businesses here are in the test dictionary ([ScanFixtures.directory]) but not among the keywords, so each test
 * reaches the new step ("meralco" or "starbucks" would be filed by their keywords before it).
 */
class TypedMerchantCategoryTest {

    private val f = ScanFixtures

    private fun one(text: String, history: List<Transaction> = emptyList()): TransactionCandidate =
        BatchEntryParser.parse(text, f.context(history = history)).single()

    private fun times(n: Int, note: String, category: Category, wallet: Wallet = f.cash) =
        (1..n).map { i -> f.saved(TransactionType.EXPENSE, 10_000L + i, wallet, "2026-11-${10 + i}", note, category) }

    @Test fun `a business the dictionary knows gets its category`() {
        val groceries = one("walmart 1200")
        assertEquals(f.groceries.id, groceries.categoryId)
        assertTrue(CandidateFlag.CATEGORY_UNMATCHED !in groceries.flags)
        assertEquals(f.utilities.id, one("pg&e 3400").categoryId)
        // A common word is a weak alias: it names the business only at the start ("target 900", not "on target").
        assertEquals(f.shopping.id, one("target 900").categoryId)
    }

    @Test fun `the person's own habit for the business comes before the dictionary`() {
        // Walmart filed under Shopping three times: that habit beats the dictionary's groceries.
        assertEquals(f.shopping.id, one("walmart 1200", times(3, "Walmart", f.shopping)).categoryId)
    }

    @Test fun `a habit works for a name the dictionary doesn't know`() {
        val history = times(2, "Padala kay Mama", f.shopping, f.gcash)
        assertEquals(f.shopping.id, one("padala kay mama 3000", history).categoryId)
    }

    @Test fun `one entry is not yet a habit`() {
        val row = one("aling nena 120", times(1, "Aling Nena", f.groceries))
        assertNull(row.categoryId)
        assertTrue(CandidateFlag.CATEGORY_UNMATCHED in row.flags)
    }

    @Test fun `the message's own words still win`() {
        assertEquals(f.shopping.id, one("shopping at walmart 1200").categoryId)
    }

    @Test fun `an unknown business still asks for a category`() {
        val row = one("aling nena 120")
        assertNull(row.categoryId)
        assertTrue(CandidateFlag.CATEGORY_UNMATCHED in row.flags)
    }

    @Test fun `the dictionary never files money coming in`() {
        val row = one("received 500 from walmart")
        assertEquals(TransactionType.INCOME, row.type)
        assertNotEquals(f.groceries.id, row.categoryId)
    }

    @Test fun `each row of a message gets its own business's category`() {
        val rows = BatchEntryParser.parse("walmart 1200, pg&e 3400, aling nena 120", f.context())
        assertEquals(listOf(f.groceries.id, f.utilities.id, null), rows.map { it.categoryId })
    }
}
