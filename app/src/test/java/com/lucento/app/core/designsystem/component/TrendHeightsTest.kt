package com.lucento.app.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TrendLine's drawing scale: display only, never money. */
class TrendHeightsTest {

    @Test fun scalesBetweenLowestAndHighest() {
        assertEquals(listOf(0f, 0.5f, 1f), trendHeights(listOf(100L, 150L, 200L)))
    }

    @Test fun flatSeriesSitsInTheMiddle() {
        assertEquals(listOf(0.5f, 0.5f, 0.5f), trendHeights(listOf(42L, 42L, 42L)))
    }

    @Test fun negativeValuesScaleLikeAnyOther() {
        val heights = trendHeights(listOf(-300L, -100L, 100L))
        assertEquals(0f, heights.first())
        assertEquals(1f, heights.last())
        assertEquals(0.5f, heights[1])
    }

    @Test fun extremeValuesStayInRange() {
        val heights = trendHeights(listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE))
        assertTrue(heights.all { it in 0f..1f })
    }

    @Test fun emptySeriesHasNoPoints() {
        assertTrue(trendHeights(emptyList()).isEmpty())
    }
}
