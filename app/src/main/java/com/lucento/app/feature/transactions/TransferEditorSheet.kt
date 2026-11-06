package com.lucento.app.feature.transactions

import com.lucento.app.core.designsystem.component.Hairline
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.Box
import com.lucento.app.core.designsystem.component.AmountsShown
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
import androidx.compose.material.icons.filled.SwapHoriz
import com.lucento.app.core.designsystem.component.SheetHeader
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.Notes
import com.lucento.app.core.designsystem.component.IconFieldHeader
import com.lucento.app.core.designsystem.component.LucentoFields
import com.lucento.app.core.designsystem.component.WalletChoicePill

/**
 * Moves money between two of the user's own wallets. Saved as two linked legs in one database
 * transaction; never counted as income or spending. Both wallets must share a currency — Lucento does
 * not convert with guessed rates.
 *
 * Laid out like the other sheets (owner, 2026-11-04, round 7): sections under icon headers (From, To, Date, Note),
 * wallets as wrapping pills with their identity tiles, a filled note field with its icon, compact spacing, and a round
 * swap button between From and To.
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
) = AmountsShown {
    val sheetState = com.lucento.app.core.designsystem.component.rememberStickySheetState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<TransferDraftError?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val isNew = initial.group == null

    val from = wallets.firstOrNull { it.id == draft.fromWalletId }
    // Destinations: any other wallet. Between two currencies the amount that arrived is asked for (multicurrency).
    val destinations = wallets.filter { it.id != draft.fromWalletId }
    val to = wallets.firstOrNull { it.id == draft.toWalletId }
    val amountError = error is TransferDraftError.AmountInvalid || error is TransferDraftError.AmountZero ||
        error is TransferDraftError.AmountTooPrecise || error is TransferDraftError.AmountTooLarge ||
        error is TransferDraftError.AmountUnclear

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragToDismiss = false) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetHeader(
                title = if (isNew) "Transfer" else "Edit transfer",
                subtitle = "Move money between your own wallets. It isn’t income or spending.",
                icon = Icons.Filled.SwapHoriz,
                trailing = if (!isNew && onDelete != null) {
                    {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete transfer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    null
                },
            )
            if (sourceNote != null) InfoNote(sourceNote)

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
            // Only a problem with the amount shows here; what a transfer is, the header already says.
            when (val e = error) {
                TransferDraftError.AmountInvalid -> "Enter an amount, like 100${decimalSeparator}00."
                TransferDraftError.AmountZero -> "The amount must be greater than zero."
                is TransferDraftError.AmountTooPrecise ->
                    if (e.digits == 0) "${from?.currencyCode.orEmpty()} has no decimal places."
                    else "${from?.currencyCode.orEmpty()} allows up to ${e.digits} decimal places."
                TransferDraftError.AmountTooLarge -> "That amount is larger than Lucento supports."
                is TransferDraftError.AmountUnclear -> e.message
                else -> null
            }?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val mixedCurrencies = wallets.map { it.currencyCode }.distinct().size > 1
            IconFieldHeader(Icons.Filled.NorthEast, "From", Modifier.padding(top = Spacing.xs))
            WalletPills(wallets, draft.fromWalletId, mixedCurrencies) { id ->
                haptics.tick()
                val keepTo = wallets.firstOrNull { it.id == draft.toWalletId }?.takeIf { it.id != id }
                draft = draft.copy(fromWalletId = id, toWalletId = keepTo?.id)
                error = null
            }
            if (error is TransferDraftError.FromRequired) ErrorText("Choose where the money comes from.")

            // Between the two: a round button that swaps From and To (once both are chosen), on a hairline.
            val canSwap = draft.fromWalletId != null && draft.toWalletId != null
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Hairline()
                FilledTonalIconButton(
                    onClick = {
                        haptics.tick()
                        draft = draft.copy(fromWalletId = draft.toWalletId, toWalletId = draft.fromWalletId)
                        error = null
                    },
                    enabled = canSwap,
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Swap From and To")
                }
            }
            IconFieldHeader(Icons.Filled.SouthWest, "To")
            if (destinations.isEmpty()) {
                Text(
                    if (from == null) "Choose the source account first." else "No other account yet. Add one in Wallets to transfer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                WalletPills(destinations, draft.toWalletId, mixedCurrencies) { id ->
                    haptics.tick()
                    draft = draft.copy(toWalletId = id)
                    error = null
                }
            }
            when (error) {
                TransferDraftError.ToRequired -> ErrorText("Choose where the money goes.")
                TransferDraftError.SameWallet -> ErrorText("Choose two different accounts.")
                TransferDraftError.CurrencyMismatch -> ErrorText("Each account must keep its own currency.")
                else -> Unit
            }
            // Multicurrency: between two currencies, what arrived — the two real amounts are the rate paid.
            if (from != null && to != null && from.currencyCode != to.currencyCode) {
                ReceivedField(
                    from = from.currencyCode,
                    to = to.currencyCode,
                    sentText = draft.amountText,
                    text = draft.receivedText,
                    date = draft.date,
                    decimalSeparator = decimalSeparator,
                    isError = error == TransferDraftError.ReceivedInvalid,
                    onTextChange = {
                        draft = draft.copy(receivedText = it)
                        if (error == TransferDraftError.ReceivedInvalid) error = null
                    },
                )
            }

            IconFieldHeader(Icons.Outlined.CalendarMonth, "Date", Modifier.padding(top = Spacing.xs))
            DateChoices(date = draft.date, onDate = { draft = draft.copy(date = it) }, onPick = { pickingDate = true })

            IconFieldHeader(Icons.Outlined.EditNote, "Note (optional)", Modifier.padding(top = Spacing.xs))
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it.take(TransactionRepository.MAX_NOTE)) },
                label = { Text("Note") },
                placeholder = { Text("e.g. Card payment, ATM withdrawal") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                shape = LucentoFields.shape,
                colors = LucentoFields.filledColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error is TransferDraftError.Rejected) {
                ErrorText("This transfer couldn’t be saved. Check both accounts, then try again.")
            }

            Spacer(Modifier.height(Spacing.xs))
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

/** The wallets to pick from, wrapping onto more lines: each with its identity tile; the currency only when it varies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WalletPills(wallets: List<Wallet>, selectedId: Long?, showCurrency: Boolean, onSelect: (Long) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        wallets.forEach { w ->
            WalletChoicePill(w, selected = selectedId == w.id, onClick = { onSelect(w.id) }, detail = w.currencyCode.takeIf { showCurrency })
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.xs))
}

/**
 * Multicurrency: "They receive" in the destination's currency. While empty it is suggested from the day's rate when
 * "Exchange rates online" is on; the person types what actually arrived. Underneath, the rate the two amounts imply.
 */
@Composable
private fun ReceivedField(
    from: String,
    to: String,
    sentText: String,
    text: String,
    date: java.time.LocalDate,
    decimalSeparator: Char,
    isError: Boolean,
    onTextChange: (String) -> Unit,
) {
    val rates = com.lucento.app.feature.fx.LocalFxRates.current
    val sent = (com.lucento.app.core.money.MoneyParser.parse(sentText, from, decimalSeparator) as? com.lucento.app.core.money.MoneyParser.Result.Success)?.money
    val received = (com.lucento.app.core.money.MoneyParser.parse(text, to, decimalSeparator) as? com.lucento.app.core.money.MoneyParser.Result.Success)?.money
    val currentText by androidx.compose.runtime.rememberUpdatedState(text)
    val change by androidx.compose.runtime.rememberUpdatedState(onTextChange)
    var suggested by androidx.compose.runtime.remember(from, to, date) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(from, to, date, sent) {
        if (sent == null || sent.isZero || rates == null || (currentText.isNotBlank() && !suggested)) return@LaunchedEffect
        val rate = runCatching { rates.rate(from, to, date) }.getOrNull() ?: return@LaunchedEffect
        if (currentText.isBlank() || suggested) {
            change(MoneyFormatter.formatPlain(com.lucento.app.core.money.FxRate(from, to, rate).convert(sent).abs()))
            suggested = true
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            suggested = false
            onTextChange(it.take(20))
        },
        label = { Text("They receive") },
        prefix = { Text(MoneyFormatter.currencySymbol(to) + " ") },
        placeholder = { Text("0") },
        singleLine = true,
        isError = isError,
        supportingText = {
            val implied = if (sent != null && received != null && !sent.isZero && !received.isZero) {
                com.lucento.app.core.money.FxRate.implied(sent, received)
            } else {
                null
            }
            Text(
                when {
                    isError -> "Enter what arrived in $to — the two amounts are the exchange rate."
                    implied != null -> "1 $from = ${implied.display()} $to" + if (suggested) " (today’s rate — change it to what arrived)" else ""
                    else -> "What arrived in $to. The two amounts are the rate you got."
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        shape = com.lucento.app.core.designsystem.component.LucentoFields.shape,
        colors = com.lucento.app.core.designsystem.component.LucentoFields.filledColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
