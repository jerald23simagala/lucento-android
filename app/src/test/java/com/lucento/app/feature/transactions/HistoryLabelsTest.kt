package com.lucento.app.feature.transactions

import com.lucento.app.core.money.Money
import com.lucento.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Core screens refinement: the history's day headers, summary label and amount colours. */
class HistoryLabelsTest {

    private val today = LocalDate.parse("2026-11-18") // a Wednesday

    @Test fun dayTitleIsRelativeThenTheWeekday() {
        assertEquals("Today", dayTitle(today, today, Locale.UK))
        assertEquals("Yesterday", dayTitle(today.minusDays(1), today, Locale.UK))
        assertEquals("Monday", dayTitle(LocalDate.parse("2026-11-16"), today, Locale.UK))
        assertEquals("Sunday", dayTitle(LocalDate.parse("2025-11-16"), today, Locale.UK))
    }

    @Test fun captionNamesTheWeekdayOnlyWhenTheTitleDoesNot() {
        assertEquals("EEEdMMM", dayCaptionSkeleton(today, today))
        assertEquals("EEEdMMM", dayCaptionSkeleton(today.minusDays(1), today))
        assertEquals("dMMM", dayCaptionSkeleton(LocalDate.parse("2026-11-16"), today))
    }

    @Test fun captionAddsTheYearOnlyWhenItIsNotThisYear() {
        assertEquals("dMMMy", dayCaptionSkeleton(LocalDate.parse("2025-11-16"), today))
        val newYear = LocalDate.parse("2027-01-01")
        assertEquals("EEEdMMMy", dayCaptionSkeleton(LocalDate.parse("2026-12-31"), newYear)) // yesterday, last year
    }

    @Test fun netLabelFollowsTheSign() {
        assertEquals("Net inflow", netFlowLabel(Money(180_140, "USD")))
        assertEquals("Net inflow", netFlowLabel(Money.zero("USD")))
        assertEquals("Net outflow", netFlowLabel(Money(-1, "USD")))
    }

    @Test fun amountsAreColouredByType() {
        assertEquals(AmountInk.POSITIVE, amountInk(TransactionType.INCOME))
        assertEquals(AmountInk.NEGATIVE, amountInk(TransactionType.EXPENSE))
        assertEquals(AmountInk.MUTED, amountInk(TransactionType.TRANSFER_IN))
        assertEquals(AmountInk.MUTED, amountInk(TransactionType.TRANSFER_OUT))
    }

    /** A correction is neither earned nor spent: a "+" adjustment must never be painted as spending, nor "−" as income. */
    @Test fun adjustmentsStayNeutral() {
        assertEquals(AmountInk.NEUTRAL, amountInk(TransactionType.ADJUSTMENT_IN))
        assertEquals(AmountInk.NEUTRAL, amountInk(TransactionType.ADJUSTMENT_OUT))
    }

    @Test fun owedPerspectiveIsAlwaysNeutral() {
        TransactionType.entries.forEach { assertEquals(AmountInk.NEUTRAL, amountInk(it, owedPerspective = true)) }
    }
}
