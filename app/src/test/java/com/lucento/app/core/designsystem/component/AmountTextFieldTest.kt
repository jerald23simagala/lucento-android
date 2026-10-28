package com.lucento.app.core.designsystem.component

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextFieldTest {

    @Test fun focusSelectsTheWholeOldAmount() {
        val focused = amountFieldOnFocus(TextFieldValue("12.50", TextRange(2, 2)))
        assertEquals("12.50", focused.text)
        assertEquals(TextRange(0, 5), focused.selection)
    }

    @Test fun anEmptyAmountStaysAnEmptySelection() {
        val focused = amountFieldOnFocus(TextFieldValue(""))
        assertEquals("", focused.text)
        assertEquals(TextRange(0, 0), focused.selection)
    }
}