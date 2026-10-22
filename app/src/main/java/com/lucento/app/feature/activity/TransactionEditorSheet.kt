package com.lucento.app.feature.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorSheet(
    initial: TransactionDraft,
    wallets: List<Wallet>,
    categories: List<Category>,
    onSave: (TransactionDraft, Char, (TransactionDraftError?) -> Unit) -> Unit,
    onDelete: ((Long) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<TransactionDraftError?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val wallet = wallets.firstOrNull { it.id == draft.walletId }
    val kind = if (draft.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    // Archived categories stay selectable only when this transaction already uses one.
    val choices = categories.filter { it.kind == kind && (!it.archived || it.id == draft.categoryId) }
    val amountError = error is TransactionDraftError.AmountInvalid || error is TransactionDraftError.AmountZero ||
        error is TransactionDraftError.AmountTooPrecise || error is TransactionDraftError.AmountTooLarge

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                if (initial.id == null) "New transaction" else "Edit transaction",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(Spacing.lg))

            val types = listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = draft.type == type,
                        onClick = {
                            if (draft.type != type) {
                                haptics.tick()
                                // A category belongs to one kind; drop it when the kind changes.
                                draft = draft.copy(type = type, categoryId = null)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                    ) { Text(label) }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = {
                    draft = draft.copy(amountText = it.take(20))
                    if (amountError) error = null
                },
                label = { Text("Amount") },
                prefix = if (wallet != null) {
                    { Text(MoneyFormatter.currencySymbol(wallet.currencyCode) + " ") }
                } else {
                    null
                },
                placeholder = { Text("0") },
                singleLine = true,
                isError = amountError,
                supportingText = {
                    Text(
                        when (val e = error) {
                            TransactionDraftError.AmountInvalid -> "Enter an amount, like 12${decimalSeparator}50."
                            TransactionDraftError.AmountZero -> "The amount must be greater than zero."
                            is TransactionDraftError.AmountTooPrecise ->
                                if (e.digits == 0) "${wallet?.currencyCode.orEmpty()} has no decimal places."
                                else "${wallet?.currencyCode.orEmpty()} allows up to ${e.digits} decimal places."
                            TransactionDraftError.AmountTooLarge -> "That amount is larger than Lucento supports."
                            else -> wallet?.let { "In ${it.currencyCode}, the currency of ${it.name}." } ?: ""
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))
            Text("Wallet", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                wallets.forEach { w ->
                    FilterChip(
                        selected = draft.walletId == w.id,
                        onClick = {
                            haptics.tick()
                            draft = draft.copy(walletId = w.id)
                            if (error is TransactionDraftError.WalletRequired || amountError) error = null
                        },
                        label = { Text("${w.name} · ${w.currencyCode}") },
                    )
                }
            }
            if (error is TransactionDraftError.WalletRequired) {
                Text("Choose a wallet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Text(
                    "  optional",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                choices.forEach { c ->
                    FilterChip(
                        selected = draft.categoryId == c.id,
                        onClick = {
                            haptics.tick()
                            draft = draft.copy(categoryId = if (draft.categoryId == c.id) null else c.id)
                        },
                        label = { Text(c.name) },
                        leadingIcon = { Icon(c.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { pickingDate = true }
                    .padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Date", style = MaterialTheme.typography.labelLarge)
                    Text(
                        draft.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Change date")
            }

            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it.take(TransactionRepository.MAX_NOTE)) },
                label = { Text("Note") },
                placeholder = { Text("e.g. Weekly shop") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error is TransactionDraftError.Rejected) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "This transaction couldn’t be saved. Check the wallet and category, then try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            LucentoPrimaryButton(
                text = if (initial.id == null) "Add transaction" else "Save changes",
                onClick = {
                    onSave(draft, decimalSeparator) { result ->
                        if (result == null) close() else {
                            haptics.reject()
                            error = result
                        }
                    }
                },
            )

            if (initial.id != null && onDelete != null) {
                Spacer(Modifier.height(Spacing.sm))
                TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete transaction", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (pickingDate) {
        // The Material date picker works in UTC midnight; convert both ways so the calendar date never shifts.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        draft = draft.copy(date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmingDelete && initial.id != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It will be removed from this device and the wallet balance will update. This can’t be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete(initial.id)
                    close()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}
