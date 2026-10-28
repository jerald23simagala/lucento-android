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
        // A second caret move (keyboard show after the tap) must not drop the selection before the keystroke.
        assertEquals(true, edit.replaceTapSelection)
        val again = amountEditAfterFocus(edit.value, TextFieldValue("8", TextRange(1)), edit.replaceTapSelection) { it }
        assertEquals(TextRange(0, 1), again.value.selection)

        val typed = amountEditAfterFocus(again.value, TextFieldValue("9"), again.replaceTapSelection) { it }
        assertEquals("9", typed.value.text)
        assertEquals(false, typed.replaceTapSelection)
    }

    @Test fun hidingTheKeyboardThenTappingAgainReplacesTheAmount() {
        var state = AmountFieldModel(TextFieldValue(""))
        state = amountFieldOnFocusChange(state, true)
        state = amountFieldOnIme(state, true)
        state = amountFieldOnEdit(state, TextFieldValue("8", TextRange(1))) { it }
        assertEquals("8", state.editor.text)
        assertEquals(false, state.replaceTapSelection)

        // The keyboard hides and the field keeps focus, so this is not a new focus event.
        state = amountFieldOnIme(state, false)
        assertEquals(true, state.focused)
        assertEquals(true, state.selectWhenImeReturns)

        // Tapping the field brings the keyboard back and would otherwise leave the caret at the end.
        state = amountFieldOnIme(state, true)
        state = amountFieldOnEdit(state, TextFieldValue("8", TextRange(1))) { it }
        assertEquals(TextRange(0, 1), state.editor.selection)

        state = amountFieldOnEdit(state, TextFieldValue("9")) { it }
        assertEquals("9", state.editor.text)
    }

    @Test fun refocusAfterTheKeyboardDismissesFocusSelectsTheAmount() {
        var state = AmountFieldModel(TextFieldValue("8", TextRange(1)), focused = true, imeVisible = true)
        state = amountFieldOnFocusChange(state, false)
        state = amountFieldOnIme(state, false)
        state = amountFieldOnFocusChange(state, true)
        state = amountFieldOnEdit(state, TextFieldValue("8", TextRange(1))) { it }
        state = amountFieldOnEdit(state, TextFieldValue("9")) { it }
        assertEquals("9", state.editor.text)
    }

    @Test fun aDigitThatArrivesAsAnAppendAfterTheKeyboardReturnsReplacesTheAmount() {
        var state = AmountFieldModel(TextFieldValue("8", TextRange(1)), focused = true, imeVisible = true, replaceTapSelection = false)
        state = amountFieldOnIme(state, false)
        // Focus never left, and the field reports the new digit stuck on the end.
        state = amountFieldOnEdit(state, TextFieldValue("89", TextRange(2))) { it }
        assertEquals("9", state.editor.text)
    }

    @Test fun anEmptyAmountStaysAnEmptySelection() {
        val focused = amountFieldOnFocus(TextFieldValue(""))
        assertEquals("", focused.text)
        assertEquals(TextRange(0, 0), focused.selection)
    }
}