package com.lucento.app.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberWordsTest {
    private fun n(text: String) = NumberWords.normalize(text)

    @Test fun spokenAmounts() {
        assertEquals("spent 2500 on groceries", n("spent two thousand five hundred on groceries"))
        assertEquals("150 pesos", n("a hundred and fifty pesos"))
        assertEquals("45 for lunch", n("forty-five for lunch"))
        assertEquals("25", n("twenty five"))
        assertEquals("1200000", n("one million two hundred thousand"))
    }

    @Test fun leavesOrdinaryWordsAlone() {
        assertEquals("lunch and coffee", n("lunch and coffee"))
        assertEquals("a coffee 180", n("a coffee 180"))
        assertEquals("Lunch 250, coffee 180", n("Lunch 250, coffee 180"))
    }

    @Test fun separateNumbersStaySeparate() {
        assertEquals("12 50", n("twelve fifty"))
        assertEquals("5 10", n("five ten"))
    }
}
