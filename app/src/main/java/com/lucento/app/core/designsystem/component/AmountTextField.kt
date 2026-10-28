package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
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
    if (replaceTapSelection && incoming.text == current.text) {
        return AmountEdit(amountFieldOnFocus(incoming), replaceTapSelection = false, textChanged = false)
    }
    val filtered = filter(incoming.text)
    val value = if (filtered == incoming.text) incoming else TextFieldValue(filtered, TextRange(filtered.length))
    return AmountEdit(value, replaceTapSelection = false, textChanged = filtered != current.text)
}

data class AmountEdit(val value: TextFieldValue, val replaceTapSelection: Boolean, val textChanged: Boolean)

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
    var focused by remember { mutableStateOf(false) }
    var replaceTapSelection by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (!focused && editor.text != value) {
        editor = TextFieldValue(value, TextRange(value.length))
    }
    val onEdit: (TextFieldValue) -> Unit = { incoming ->
        val edit = amountEditAfterFocus(editor, incoming, replaceTapSelection, filter)
        editor = edit.value
        replaceTapSelection = edit.replaceTapSelection
        if (edit.textChanged) onValueChange(edit.value.text)
    }
    val focusModifier = modifier.onFocusChanged { state ->
        val gained = state.isFocused && !focused
        focused = state.isFocused
        if (gained) {
            editor = amountFieldOnFocus(editor)
            replaceTapSelection = true
        }
        if (!state.isFocused) replaceTapSelection = false
    }
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
