package com.lucento.app.feature.wallets

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.lucento.app.core.designsystem.component.CurrencyPickerSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.launch

fun Wallet.toDraft(): WalletDraft = WalletDraft(
    id = id,
    name = name,
    type = type,
    currencyCode = currencyCode,
    // The editor edits the opening balance; the current balance is derived from transactions.
    amountText = if (openingBalance.isZero) "" else MoneyFormatter.formatPlain(openingBalance),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletEditorSheet(
    initial: WalletDraft,
    onSave: (WalletDraft, Char, (DraftError?) -> Unit) -> Unit,
    /** Deletes the wallet; the callback receives 0 on success or the number of transactions blocking deletion. */
    onDelete: ((Long, (Int) -> Unit) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<DraftError?>(null) }
    var pickingCurrency by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var blockedByTransactions by remember { mutableStateOf<Int?>(null) }

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
                if (initial.id == null) "New wallet" else "Edit wallet",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(Spacing.xl))

            OutlinedTextField(
                value = draft.name,
                onValueChange = {
                    draft = draft.copy(name = it.take(40))
                    if (error is DraftError.NameRequired || error is DraftError.NameTaken) error = null
                },
                label = { Text("Name") },
                placeholder = { Text("e.g. Everyday account") },
                singleLine = true,
                isError = error is DraftError.NameRequired || error is DraftError.NameTaken,
                supportingText = {
                    when (error) {
                        DraftError.NameRequired -> Text("Give this wallet a name.")
                        DraftError.NameTaken -> Text("You already have a wallet with this name.")
                        else -> Unit
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.lg))
            Text("Type", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                WalletType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = {
                            haptics.tick()
                            draft = draft.copy(type = type)
                        },
                        label = { Text(type.label) },
                        leadingIcon = { Icon(type.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { pickingCurrency = true }
                    .padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Currency", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${draft.currencyCode} · ${MoneyFormatter.currencyName(draft.currencyCode)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Change currency")
            }
            if (error is DraftError.CurrencyLocked) {
                Text(
                    "This wallet has transactions, so its currency can't change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            val amountError = error is DraftError.AmountInvalid ||
                error is DraftError.AmountTooPrecise || error is DraftError.AmountTooLarge
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = {
                    draft = draft.copy(amountText = it.take(20))
                    if (amountError) error = null
                },
                label = {
                    Text(
                        when {
                            initial.id != null && draft.type.isLiability -> "Opening amount owed"
                            initial.id != null -> "Opening balance"
                            draft.type.isLiability -> "Amount owed"
                            else -> "Current balance"
                        }
                    )
                },
                prefix = { Text(MoneyFormatter.currencySymbol(draft.currencyCode) + " ") },
                placeholder = { Text("0") },
                singleLine = true,
                isError = amountError,
                supportingText = {
                    Text(
                        when (val e = error) {
                            DraftError.AmountInvalid -> "Enter a number, like 1250${decimalSeparator}00."
                            is DraftError.AmountTooPrecise ->
                                if (e.digits == 0) "${draft.currencyCode} has no decimal places."
                                else "${draft.currencyCode} allows up to ${e.digits} decimal places."
                            DraftError.AmountTooLarge -> "That amount is larger than Lucento supports."
                            else -> when {
                                initial.id != null -> "Where this wallet started, before the transactions you record."
                                draft.type.isLiability -> "Counts against your net worth."
                                else -> "Leave empty to start at zero."
                            }
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.xl))
            LucentoPrimaryButton(
                text = if (initial.id == null) "Add wallet" else "Save changes",
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
                TextButton(
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete wallet", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (pickingCurrency) {
        CurrencyPickerSheet(
            selected = draft.currencyCode,
            onSelect = {
                draft = draft.copy(currencyCode = it)
                if (error is DraftError.AmountTooPrecise || error is DraftError.CurrencyLocked) error = null
                pickingCurrency = false
            },
            onDismiss = { pickingCurrency = false },
        )
    }

    if (confirmingDelete && initial.id != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete “${initial.name}”?") },
            text = { Text("This empty wallet will be removed from this device. This can’t be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete(initial.id) { blocking ->
                        if (blocking == 0) close() else blockedByTransactions = blocking
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }

    blockedByTransactions?.let { count ->
        AlertDialog(
            onDismissRequest = { blockedByTransactions = null },
            title = { Text("Can’t delete yet") },
            text = {
                Text(
                    "“${initial.name}” still has $count ${if (count == 1) "transaction" else "transactions"}. " +
                        "Delete them from Activity first, so no financial history disappears by accident."
                )
            },
            confirmButton = { TextButton(onClick = { blockedByTransactions = null }) { Text("OK") } },
        )
    }
}
