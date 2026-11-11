package com.lucento.app.feature.wallets

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
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.AmountTextField
import com.lucento.app.core.designsystem.component.AmountsShown
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.domain.OutsideMovement
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletBalanceCalculator
import com.lucento.app.feature.plan.DateField
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * A withdrawal from a money account, or an external payment on a card or loan (tester round 3): an amount, what it was
 * for (required — every statement lists it), and the day. Neither is income, spending or a transfer between wallets;
 * the sheet previews the balance, or what is still owed, after it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutsideMovementSheet(
    kind: OutsideMovement,
    wallet: Wallet,
    /** This wallet's recorded movements, to show the balance on the chosen day. */
    history: List<Transaction>,
    onSave: (kind: OutsideMovement, amountText: String, decimalSeparator: Char, date: LocalDate, reason: String, clientUuid: String, (AdjustError?) -> Unit) -> Unit,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) = AmountsShown {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }
    // One idempotency key per opened sheet: a double tap records it once.
    val clientUuid = remember { UUID.randomUUID().toString() }
    val today = remember { LocalDate.now() }
    val ext = LucentoTheme.extendedColors
    val payment = kind == OutsideMovement.EXTERNAL_PAYMENT

    var date by remember { mutableStateOf(today) }
    var amountText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var reasonTouched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AdjustError?>(null) }

    val balance = WalletBalanceCalculator.balanceOn(wallet.openingBalance, wallet.type, wallet.id, history, date)
    val parsed = MoneyParser.parse(amountText, wallet.currencyCode, decimalSeparator)
    val amount = (parsed as? MoneyParser.Result.Success)?.money?.takeIf { it.amountMinor > 0L }
    val liveClarification = when (parsed) {
        is MoneyParser.Result.Success -> if (amount == null) "Enter an amount above zero." else null
        MoneyParser.Result.Empty -> null
        MoneyParser.Result.Invalid -> "Enter a number, like 1250${decimalSeparator}00."
        is MoneyParser.Result.TooManyDecimals ->
            if (parsed.allowed == 0) "${wallet.currencyCode} has no decimal places."
            else "${wallet.currencyCode} allows up to ${parsed.allowed} decimal places."
        MoneyParser.Result.TooLarge -> "That amount is larger than Lucento supports."
        else -> MoneyParser.clarification(parsed, decimalSeparator)
    }
    // Both lower the figure the wallet shows: money in it, or what is owed on it.
    val after = amount?.let { balance - it }
    val reasonMissing = !OutsideMovement.reasonValid(reason)

    fun close(then: () -> Unit) {
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
            SheetHeader(
                title = kind.label,
                subtitle = wallet.name,
                leading = { com.lucento.app.core.designsystem.component.WalletIdentityTile(wallet) },
            )
            Text(
                if (payment) {
                    "A payment on “${wallet.name}” that didn’t come from one of your wallets — someone settling it for you, " +
                        "or a gift. It lowers what you owe and isn’t counted as income or spending. Paid from one of your " +
                        "wallets? Use From wallet."
                } else {
                    "Money taken out of “${wallet.name}” that isn’t spending and doesn’t go to another wallet in Lucento. " +
                        "It lowers the balance but never counts in your budget or Insights. Moving money to one of your " +
                        "wallets? Use Transfer. Paid for something? Add it with +."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.md))
            AmountTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    if (error != null && error != AdjustError.FutureDate && error != AdjustError.ReasonRequired) error = null
                },
                filter = { it.take(20) },
                label = { Text(if (payment) "Amount paid" else "Amount withdrawn") },
                prefix = { Text(MoneyFormatter.currencySymbol(wallet.currencyCode) + " ") },
                placeholder = { Text("0") },
                isError = liveClarification != null || error is AdjustError.AmountInvalid || error is AdjustError.AmountTooPrecise ||
                    error is AdjustError.AmountTooLarge || error is AdjustError.AmountUnclear,
                textStyle = AmountStyles.medium,
                supportingText = {
                    Text(
                        when (val e = error) {
                            AdjustError.AmountInvalid -> "Enter an amount above zero."
                            is AdjustError.AmountTooPrecise ->
                                if (e.digits == 0) "${wallet.currencyCode} has no decimal places."
                                else "${wallet.currencyCode} allows up to ${e.digits} decimal places."
                            AdjustError.AmountTooLarge -> "That amount is larger than Lucento supports."
                            is AdjustError.AmountUnclear -> e.message
                            AdjustError.Failed -> "Couldn’t save it. Nothing was changed."
                            else -> liveClarification ?: "In ${wallet.currencyCode}."
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            val showReasonError = (reasonTouched && reasonMissing) || error == AdjustError.ReasonRequired
            OutlinedTextField(
                value = reason,
                onValueChange = {
                    reason = it.take(OutsideMovement.MAX_REASON)
                    reasonTouched = true
                    if (error == AdjustError.ReasonRequired) error = null
                },
                label = { Text("What for?") },
                placeholder = { Text(if (payment) "e.g. Paid by Mom as a gift" else "e.g. Moved to my investment account") },
                singleLine = true,
                isError = showReasonError,
                supportingText = {
                    Text(
                        if (showReasonError) "Say what it was for — your statements list it."
                        else "Required. It shows in this wallet’s history and statements.",
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            DateField(
                label = if (payment) "Paid on" else "Withdrawn on",
                date = date,
                onDate = {
                    if (it != null) date = it
                    if (error == AdjustError.FutureDate) error = null
                },
                clearable = false,
            )
            if (error == AdjustError.FutureDate || date.isAfter(today)) {
                Text(
                    "Pick today or an earlier day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs, start = Spacing.lg),
                )
            }

            if (after != null) {
                Spacer(Modifier.height(Spacing.md))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (payment) "Still owed after" else "Balance after",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    AmountText(money = after, style = AmountStyles.row, signed = after.isNegative)
                }
                if (after.isNegative) {
                    InfoNote(
                        if (payment) "That’s more than Lucento shows owed on that day, so it would end up overpaid."
                        else "That’s more than Lucento shows in this account on that day, so its balance would go below zero.",
                        icon = Icons.Outlined.WarningAmber,
                        container = ext.warningContainer,
                        content = ext.warning,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            LucentoPrimaryButton(
                text = if (payment) "Record external payment" else "Withdraw",
                enabled = amount != null && !reasonMissing && !date.isAfter(today),
                onClick = {
                    onSave(kind, amountText, decimalSeparator, date, reason, clientUuid) { result ->
                        if (result == null) {
                            haptics.confirm()
                            close(onSaved)
                        } else {
                            haptics.reject()
                            error = result
                        }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
