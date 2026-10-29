package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTourTest {
    @Test fun theExampleIsOnlyInMemoryFigures() {
        val example = PreviewTour.example("USD")
        assertEquals(Money(1_250_00, "USD"), example.balance)
        assertEquals(Money(4_00, "USD"), example.entry)
        assertEquals("Coffee", example.entryNote)
        assertEquals(Money(1_250, "JPY"), PreviewTour.example("JPY").balance)
    }

    @Test fun theInvitationHidesAfterTheFirstRealEntryOrOnceOpened() {
        assertTrue(PreviewTour.showRow(realEntryCount = 0, opened = false))
        assertFalse(PreviewTour.showRow(realEntryCount = 1, opened = false))
        assertFalse(PreviewTour.showRow(realEntryCount = 0, opened = true))
        assertFalse(PreviewTour.showRow(realEntryCount = 4, opened = true))
    }
}