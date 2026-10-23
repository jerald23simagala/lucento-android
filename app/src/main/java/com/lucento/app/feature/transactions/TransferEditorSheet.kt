package com.lucento.app.feature.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/**
 * Moves money between two of the user's own wallets. Saved as two linked legs in one database
 * transaction; never counted as income or spending. Both wallets must share a currency — Lucento does
 * not convert with guessed rates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferEditorSheet(
    initial: TransferDraft,
    wallets: List<Wallet>,
    onSave: (TransferDraft, Char, (TransferDraftError?) -> Unit) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    sourceNote: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<TransferDraftError?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val isNew = initial.group == null

    val from = wallets.firstOrNull { it.id == draft.fromWalletId }
    // Destinations: any other wallet in the same currency.
    val destinations = wallets.filter { it.id != draft.fromWalletId && (from == null || it.currencyCode == from.currencyCode) }
    val amountError = error is TransferDraftError.AmountInvalid || error is TransferDraftError.AmountZero ||
        error is TransferDraftError.AmountTooPrecise || error is TransferDraftError.AmountTooLarge

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isNew) "Transfer" else "Edit transfer",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!isNew && onDelete != null) {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete transfer", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (sourceNote != null) {
                Spacer(Modifier.height(Spacing.md))
                InfoNote(sourceNote)
            }

            Spacer(Modifier.height(Spacing.xl))
            AmountEntry(
                text = draft.amountText,
                currencyCode = from?.currencyCode,
                type = TransactionType.TRANSFER_OUT,
                isError = amountError,
                autoFocus = isNew && sourceNote == null,
                onTextChange = {
                    draft = draft.copy(amountText = it)
                    if (amountError) error = null
                },
            )
            Text(
                when (val e = error) {
                    TransferDraftError.AmountInvalid -> "Enter an amount, like 100${decimalSeparator}00."
                    TransferDraftError.AmountZero -> "The amount must be greater than zero."
                    is TransferDraftError.AmountTooPrecise ->
                        if (e.digits == 0) "${from?.currencyCode.orEmpty()} has no decimal places."
                        else "${from?.currencyCode.orEmpty()} allows up to ${e.digits} decimal places."
                    TransferDraftError.AmountTooLarge -> "That amount is larger than Lucento supports."
                    else -> "Moving money between your accounts isn’t income or spending."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (amountError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader("From")
            WalletPills(wallets, draft.fromWalletId) { id ->
                haptics.tick()
                val newFrom = wallets.firstOrNull { it.id == id }
                val keepTo = wallets.firstOrNull { it.id == draft.toWalletId }
                    ?.takeIf { it.id != id && it.currencyCode == newFrom?.currencyCode }
                draft = draft.copy(fromWalletId = id, toWalletId = keepTo?.id)
                error = null
            }
            if (error is TransferDraftError.FromRequired) ErrorText("Choose where the money comes from.")

            Spacer(Modifier.height(Spacing.md))
            SectionHeader("To")
            if (destinations.isEmpty()) {
                Text(
                    if (from == null) "Choose the source account first." else "No other ${from.currencyCode} account. Add one in Wallets to transfer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WalletPills(destinations, draft.toWalletId) { id ->
                    haptics.tick()
                    draft = draft.copy(toWalletId = id)
                    error = null
                }
            }
            when (error) {
                TransferDraftError.ToRequired -> ErrorText("Choose where the money goes.")
                TransferDraftError.SameWallet -> ErrorText("Choose two different accounts.")
                TransferDraftError.CurrencyMismatch -> ErrorText("Both accounts must use the same currency.")
                else -> Unit
            }

            Spacer(Modifier.height(Spacing.md))
            SectionHeader("Date")
            DateChoices(date = draft.date, onDate = { draft = draft.copy(date = it) }, onPick = { pickingDate = true })

            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it.take(TransactionRepository.MAX_NOTE)) },
                label = { Text("Note") },
                placeholder = { Text("e.g. Card payment, ATM withdrawal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error is TransferDraftError.Rejected) {
                Spacer(Modifier.height(Spacing.sm))
                ErrorText("This transfer couldn’t be saved. Check both accounts, then try again.")
            }

            Spacer(Modifier.height(Spacing.xl))
            LucentoPrimaryButton(
                text = if (isNew) "Transfer" else "Save changes",
                onClick = {
                    onSave(draft, decimalSeparator) { result ->
                        if (result == null) close() else {
                            haptics.reject()
                            error = result
                        }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (pickingDate) {
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

    if (confirmingDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete this transfer?",
            text = "Both sides of the transfer are removed and the two account balances update. This can’t be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDelete()
                close()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun WalletPills(wallets: List<Wallet>, selectedId: Long?, onSelect: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        wallets.forEach { w ->
            ChoicePill(
                label = "${w.name} · ${w.currencyCode}",
                selected = selectedId == w.id,
                leadingIcon = w.type.icon,
                onClick = { onSelect(w.id) },
            )
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.xs))
}
