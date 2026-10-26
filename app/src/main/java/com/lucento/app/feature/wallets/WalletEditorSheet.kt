package com.lucento.app.feature.wallets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.CurrencyPickerSheet
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.FieldHeader
import com.lucento.app.core.designsystem.component.WalletTypeTile
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.launch

fun Wallet.toDraft(isDefault: Boolean = false): WalletDraft = WalletDraft(
    id = id,
    name = name,
    type = type,
    currencyCode = currencyCode,
    // The editor edits the opening balance; the current balance is derived from transactions.
    amountText = if (openingBalance.isZero) "" else MoneyFormatter.formatPlain(openingBalance),
    isDefault = isDefault,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletEditorSheet(
    initial: WalletDraft,
    onSave: (WalletDraft, Char, (DraftError?) -> Unit) -> Unit,
    /** Deletes the wallet; the callback says whether it was deleted or what still uses it. */
    onDelete: ((Long, (WalletRepository.DeleteResult) -> Unit) -> Unit)?,
    onDismiss: () -> Unit,
    /** Opens Adjust balance for an existing money wallet; null hides the option (new or manually valued wallets). */
    onAdjustBalance: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<DraftError?>(null) }
    var pickingCurrency by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf<WalletRepository.DeleteResult?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )

    fun close(then: () -> Unit = onDismiss) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
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
                    if (initial.id == null) "New wallet" else "Edit wallet",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (initial.id != null && onDelete != null) {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete wallet", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            FieldHeader("Type")
            TypeGrid(
                selected = draft.type,
                onSelect = {
                    haptics.tick()
                    draft = draft.copy(type = it)
                    if (error is DraftError.TypeLocked) error = null
                },
            )
            (error as? DraftError.TypeLocked)?.let { locked ->
                Text(
                    "“${initial.name}” has ${locked.usage.describe()}. Switching between an account you own and one you owe " +
                        "would change what every one of them means, so it stays a${if (initial.type.isLiability) " liability" else "n asset"}. " +
                        "Create a new wallet of the other kind instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            val nameMessage = when (error) {
                DraftError.NameRequired -> "Give this wallet a name."
                DraftError.NameTaken -> "You already have a wallet with this name."
                DraftError.NotFound -> "This wallet no longer exists."
                else -> null
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = {
                    draft = draft.copy(name = it.take(40))
                    if (error is DraftError.NameRequired || error is DraftError.NameTaken) error = null
                },
                label = { Text("Name") },
                placeholder = {
                    Text(
                        when (draft.type) {
                            WalletType.CASH -> "e.g. Wallet cash"
                            WalletType.BANK -> "e.g. Everyday account"
                            WalletType.E_WALLET -> "e.g. Mobile wallet"
                            WalletType.SAVINGS -> "e.g. Emergency fund"
                            WalletType.CREDIT_CARD -> "e.g. Travel card"
                            WalletType.LOAN -> "e.g. Car loan"
                            WalletType.OTHER_ASSET -> "e.g. Home, car, investments"
                        }
                    )
                },
                singleLine = true,
                isError = error is DraftError.NameRequired || error is DraftError.NameTaken || error is DraftError.NotFound,
                supportingText = if (nameMessage != null) {
                    { Text(nameMessage) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button, onClickLabel = "Change currency") { pickingCurrency = true }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Currency",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${draft.currencyCode} · ${MoneyFormatter.currencyName(draft.currencyCode)}",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            (error as? DraftError.CurrencyLocked)?.let { locked ->
                Text(
                    "This wallet has ${locked.usage.describe()}, so its currency can’t change. " +
                        "Amounts are never converted; create a wallet in the other currency instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs, start = Spacing.lg),
                )
            }

            Spacer(Modifier.height(Spacing.md))
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
                            draft.type.isValuedManually -> "Estimated value"
                            initial.id != null && draft.type.isLiability -> "Opening amount owed"
                            initial.id != null -> "Opening balance"
                            draft.type.isLiability -> "Amount owed now"
                            else -> "Current balance"
                        }
                    )
                },
                prefix = { Text(MoneyFormatter.currencySymbol(draft.currencyCode) + " ") },
                placeholder = { Text("0") },
                singleLine = true,
                isError = amountError,
                textStyle = AmountStyles.medium,
                supportingText = {
                    Text(
                        when (val e = error) {
                            DraftError.AmountInvalid -> "Enter a number, like 1250${decimalSeparator}00."
                            is DraftError.AmountTooPrecise ->
                                if (e.digits == 0) "${draft.currencyCode} has no decimal places."
                                else "${draft.currencyCode} allows up to ${e.digits} decimal places."
                            DraftError.AmountTooLarge -> "That amount is larger than Lucento supports."
                            else -> when {
                                draft.type.isValuedManually -> "What it’s worth today. Update it whenever the value changes — no transactions needed."
                                initial.id != null -> "Where this wallet started, before the transactions you record."
                                draft.type == WalletType.LOAN -> "What you still owe on this loan. It counts against your net worth."
                                draft.type.isLiability -> "What you currently owe on this card. It counts against your net worth."
                                else -> "Leave empty to start at zero."
                            }
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.small,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            if (onAdjustBalance != null && initial.id != null && !initial.type.isValuedManually) {
                Spacer(Modifier.height(Spacing.xs))
                NavRow(
                    icon = Icons.Filled.Tune,
                    title = "Adjust balance",
                    summary = "Can’t reconcile? Set today’s real balance; the difference is recorded as a Balance adjustment.",
                    tone = StatusTones.warning,
                    onClick = { close(onAdjustBalance) },
                )
            }

            if (draft.type.isSpendable) {
                Spacer(Modifier.height(Spacing.sm))
                SwitchRow(
                    title = "Default wallet",
                    description = "New transactions go here unless you pick another wallet in the composer.",
                    checked = draft.isDefault,
                    onCheckedChange = { draft = draft.copy(isDefault = it) },
                )
            }

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
        ConfirmDialog(
            title = "Delete “${initial.name}”?",
            text = "This empty wallet will be removed from this device. This can’t be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDelete(initial.id) { result ->
                    if (result == WalletRepository.DeleteResult.Deleted) close() else blocked = result
                }
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    blocked?.let { result ->
        val text = when (result) {
            is WalletRepository.DeleteResult.HasTransactions ->
                "“${initial.name}” still has ${result.count} ${if (result.count == 1) "transaction" else "transactions"}. " +
                    "Delete them from Transactions first, so no financial history disappears by accident."
            is WalletRepository.DeleteResult.HasSchedules ->
                "“${initial.name}” is used by ${result.count} recurring ${if (result.count == 1) "payment" else "payments"}. " +
                    "Delete or change them in Plan › Recurring payments first."
            WalletRepository.DeleteResult.Deleted -> ""
        }
        ConfirmDialog(
            title = "Can’t delete yet",
            text = text,
            confirmLabel = "OK",
            dismissLabel = null,
            onConfirm = { blocked = null },
            onDismiss = { blocked = null },
        )
    }
}

/** Wallet kinds as recognisable tiles, four per row (seven kinds: two balanced rows). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeGrid(selected: WalletType, onSelect: (WalletType) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = 4
        val gap = Spacing.sm
        val tileWidth = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = columns,
        ) {
            WalletType.entries.forEach { type ->
                val isSelected = type == selected
                val border by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    tween(160),
                    label = "type-border",
                )
                Column(
                    Modifier
                        .width(tileWidth)
                        .clip(MaterialTheme.shapes.medium)
                        .border(BorderStroke(if (isSelected) 1.5.dp else 1.dp, border), MaterialTheme.shapes.medium)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(type) }
                        .padding(vertical = Spacing.md, horizontal = Spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WalletTypeTile(type, size = 36.dp)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        type.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
