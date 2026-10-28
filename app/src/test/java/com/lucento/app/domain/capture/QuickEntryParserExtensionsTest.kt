package com.lucento.app.domain.capture

import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuickEntryParserExtensionsTest {
    private val today = LocalDate.parse("2026-10-22")

    private fun date(text: String) = QuickEntryParser.extractDate(" $text ", today).first

    @Test fun monthNameDates() {
        assertEquals(LocalDate.parse("2026-10-12"), date("lunch on october 12"))
        assertEquals(LocalDate.parse("2026-10-12"), date("oct 12, 2026 lunch"))
        assertEquals(LocalDate.parse("2026-10-12"), date("the 12th of october"))
        assertEquals(LocalDate.parse("2025-11-03"), date("nov 3 dinner")) // later this year → last year
        assertEquals(today, date("february 30 rent")) // impossible date is ignored
    }

    @Test fun explicitTypeIsNullWhenNothingSaysIt() {
        assertNull(QuickEntryParser.explicitType(" lunch 250 "))
        assertEquals(TransactionType.EXPENSE, QuickEntryParser.explicitType(" bought shoes 900 "))
        assertEquals(TransactionType.INCOME, QuickEntryParser.explicitType(" got paid 5000 "))
        assertEquals(TransactionType.INCOME, QuickEntryParser.explicitType(" got 20 from mom "))
        assertEquals(TransactionType.EXPENSE, QuickEntryParser.explicitType(" got 500 from jollibee "))
        assertEquals(TransactionType.TRANSFER_OUT, QuickEntryParser.explicitType(" paid my credit card 3000 "))
        // The single-entry default is unchanged: no cue still means expense.
        assertEquals(TransactionType.EXPENSE, QuickEntryParser.detectType(" lunch 250 "))
    }

    @Test fun specificPresetCategoriesWinOverGeneralOnes() {
        val restaurants = Category(1, "Restaurants", CategoryKind.EXPENSE, "dining")
        val cafes = Category(2, "Cafes and coffee", CategoryKind.EXPENSE, "cafe")
        val taxis = Category(3, "Ride-hailing and taxis", CategoryKind.EXPENSE, "taxi")
        val transit = Category(4, "Public transportation", CategoryKind.EXPENSE, "transport")
        val all = listOf(restaurants, cafes, taxis, transit)
        assertEquals(2L, QuickEntryParser.detectCategory(" coffee 180 ", TransactionType.EXPENSE, all))
        assertEquals(1L, QuickEntryParser.detectCategory(" coffee 180 ", TransactionType.EXPENSE, listOf(restaurants)))
        assertEquals(3L, QuickEntryParser.detectCategory(" grab 220 ", TransactionType.EXPENSE, all))
        assertEquals(4L, QuickEntryParser.detectCategory(" mrt 30 ", TransactionType.EXPENSE, all))
    }

    @Test fun amountTokensTellPricesFromQuantities() {
        val tokens = QuickEntryParser.amountTokens(" bought 2 shirts ₱1,500 ")
        assertEquals(listOf(false, true), tokens.map { it.priceLike })
        assertEquals(listOf(false, true), tokens.map { it.hasCurrency })
    }
}
