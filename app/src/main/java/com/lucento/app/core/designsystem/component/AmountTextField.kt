package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The whole amount is selected when the field gains focus, so the next keystroke replaces the old value
 * instead of appending to it.
 */
fun amountFieldOnFocus(value: TextFieldValue): TextFieldValue =
    value.copy(selection = TextRange(0, value.text.length))

/**
 * What to do with the next edit after focus selected the whole amount.
 * A tap often follows focus with a second update that only moves the cursor; that update is put back to a
 * full selection so typing replaces the prefilled value ("8", tap, "9" → "9").
 */
fun amountEditAfterFocus(
    current: TextFieldValue,
    incoming: TextFieldValue,
    replaceTapSelection: Boolean,
    filter: (String) -> String,
): AmountEdit {
    // Keep the whole amount selected across every cursor-only update that follows focus (a tap, then the
    // keyboard showing, can each move the caret). The next real edit therefore replaces the amount.
    if (replaceTapSelection && incoming.text == current.text) {
        return AmountEdit(amountFieldOnFocus(incoming), replaceTapSelection = true, textChanged = false)
    }
    val filtered = filter(incoming.text)
    val value = if (filtered == incoming.text) incoming else TextFieldValue(filtered, TextRange(filtered.length))
    return AmountEdit(value, replaceTapSelection = false, textChanged = filtered != current.text)
}

data class AmountEdit(val value: TextFieldValue, val replaceTapSelection: Boolean, val textChanged: Boolean)

/**
 * Focus and keyboard state for an amount field. Hiding the keyboard often leaves the field focused, so a
 * later tap is not a new focus event and would only move the caret. [selectWhenImeReturns] selects the
 * amount again when the keyboard comes back.
 */
data class AmountFieldModel(
    val editor: TextFieldValue,
    val focused: Boolean = false,
    val replaceTapSelection: Boolean = false,
    val imeVisible: Boolean = false,
    val selectWhenImeReturns: Boolean = false,
)

fun amountFieldOnFocusChange(state: AmountFieldModel, isFocused: Boolean): AmountFieldModel {
    if (state.focused == isFocused) return state
    return state.copy(
        focused = isFocused,
        editor = if (isFocused) amountFieldOnFocus(state.editor) else state.editor,
        replaceTapSelection = isFocused,
        selectWhenImeReturns = if (isFocused) state.selectWhenImeReturns else false,
    )
}

fun amountFieldOnIme(state: AmountFieldModel, imeVisible: Boolean): AmountFieldModel {
    if (!state.focused) {
        return if (!state.imeVisible && !imeVisible && !state.selectWhenImeReturns) state
        else state.copy(imeVisible = imeVisible, selectWhenImeReturns = false)
    }
    if (!imeVisible && state.imeVisible) {
        return state.copy(imeVisible = false, selectWhenImeReturns = true)
    }
    if (imeVisible && !state.imeVisible && state.selectWhenImeReturns) {
        return state.copy(
            imeVisible = true,
            editor = amountFieldOnFocus(state.editor),
            replaceTapSelection = true,
            selectWhenImeReturns = false,
        )
    }
    return if (state.imeVisible == imeVisible) state else state.copy(imeVisible = imeVisible)
}

fun amountFieldOnEdit(state: AmountFieldModel, incoming: TextFieldValue, filter: (String) -> String): AmountFieldModel {
    val replacing = state.replaceTapSelection || state.selectWhenImeReturns
    // The caret was at the end, so the keystroke arrived as an append ("8" then "9" → "89").
    // That keystroke was meant to replace the selected amount.
    if (replacing && state.editor.text.isNotEmpty() && incoming.text.length > state.editor.text.length && incoming.text.startsWith(state.editor.text)) {
        val added = filter(incoming.text.removePrefix(state.editor.text))
        return state.copy(
            editor = TextFieldValue(added, TextRange(added.length)),
            replaceTapSelection = false,
            selectWhenImeReturns = false,
        )
    }
    if (state.selectWhenImeReturns && incoming.text == state.editor.text) {
        return state.copy(
            editor = amountFieldOnFocus(incoming),
            replaceTapSelection = true,
            selectWhenImeReturns = false,
        )
    }
    val edit = amountEditAfterFocus(state.editor, incoming, state.replaceTapSelection, filter)
    return state.copy(editor = edit.value, replaceTapSelection = edit.replaceTapSelection)
}

/**
 * Every amount field. [field] replaces the outlined field when the screen draws the amount itself
 * (the transaction editor's large figure). Focus still selects the whole value.
 */
@Composable
fun AmountTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    filter: (String) -> String = { it },
    label: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = MaterialTheme.shapes.small,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    field: (@Composable (TextFieldValue, (TextFieldValue) -> Unit, Modifier) -> Unit)? = null,
) {
    var model by remember { mutableStateOf(AmountFieldModel(TextFieldValue(value, TextRange(value.length)))) }
    if (!model.focused && model.editor.text != value) {
        model = model.copy(editor = TextFieldValue(value, TextRange(value.length)))
    }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) { model = amountFieldOnIme(model, imeVisible) }
    val onEdit: (TextFieldValue) -> Unit = { incoming ->
        val next = amountFieldOnEdit(model, incoming, filter)
        val changed = next.editor.text != model.editor.text
        model = next
        if (changed) onValueChange(next.editor.text)
    }
    val focusModifier = modifier.onFocusChanged { state ->
        model = amountFieldOnFocusChange(model, state.isFocused)
    }
    val editor = model.editor
    if (field != null) {
        field(editor, onEdit, focusModifier)
    } else {
        OutlinedTextField(
            value = editor,
            onValueChange = onEdit,
            modifier = focusModifier,
            label = label,
            prefix = prefix,
            suffix = suffix,
            placeholder = placeholder,
            supportingText = supportingText,
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = true,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = shape,
            colors = colors,
        )
    }
}
