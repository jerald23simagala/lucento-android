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

    @Test fun aTapThatOnlyMovesTheCursorDoesNotUndoSelectAll() {
        val selected = amountFieldOnFocus(TextFieldValue("8"))
        val tapped = TextFieldValue("8", TextRange(1))
        val edit = amountEditAfterFocus(selected, tapped, replaceTapSelection = true) { it }
        assertEquals("8", edit.value.text)
        assertEquals(TextRange(0, 1), edit.value.selection)
        assertEquals(false, edit.replaceTapSelection)

        val typed = amountEditAfterFocus(edit.value, TextFieldValue("9"), edit.replaceTapSelection) { it }
        assertEquals("9", typed.value.text)
    }

    @Test fun anEmptyAmountStaysAnEmptySelection() {
        val focused = amountFieldOnFocus(TextFieldValue(""))
        assertEquals("", focused.text)
        assertEquals(TextRange(0, 0), focused.selection)
    }
}