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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.domain.BalanceAdjustment
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletBalanceCalculator
import com.lucento.app.feature.plan.DateField
import com.lucento.app.feature.transactions.dayLabel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** "today", "yesterday" or "on Mon, 12 October". */
private fun onDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today"
    today.minusDays(1) -> "yesterday"
    else -> "on ${dayLabel(date, today)}"
}

/**
 * "Start fresh" for a wallet that can't be reconciled: the user enters what is really in it (or owed on it) on a day,
 * and the gap from Lucento's tracked balance that day is previewed, then recorded as a Balance adjustment. History is
 * never rewritten, and later transactions still apply on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustBalanceSheet(
    wallet: Wallet,
    /** This wallet's recorded movements, to show the tracked balance on the chosen day. */
    history: List<Transaction>,
    onAdjust: (actualText: String, decimalSeparator: Char, date: LocalDate, clientUuid: String, (AdjustError?) -> Unit) -> Unit,
    onAdjusted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }
    // One idempotency key per opened sheet: a double tap or a retry records the adjustment once.
    val clientUuid = remember { UUID.randomUUID().toString() }
    val today = remember { LocalDate.now() }
    val ext = LucentoTheme.extendedColors

    var date by remember { mutableStateOf(today) }
    var amountText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AdjustError?>(null) }

    val liability = wallet.type.isLiability
    val tracked = WalletBalanceCalculator.balanceOn(wallet.openingBalance, wallet.type, wallet.id, history, date)
    val actual = (MoneyParser.parse(amountText, wallet.currencyCode, decimalSeparator) as? MoneyParser.Result.Success)?.money
    val plan = actual?.let { BalanceAdjustment.plan(wallet.type, tracked, it) }

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
            Text("Adjust balance", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Can’t find what’s missing? Enter what ${if (liability) "you really owe on" else "is really in"} " +
                    "“${wallet.name}” and Lucento records the difference as a Balance adjustment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.md))
            DateField(
                label = "Balance on",
                date = date,
                onDate = {
                    if (it != null) date = it
                    if (error == AdjustError.FutureDate || error == AdjustError.Unchanged) error = null
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

            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lucento tracked ${onDay(date, today)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AmountText(money = tracked, style = AmountStyles.row, showPlus = false)
            }

            Spacer(Modifier.height(Spacing.sm))
            val amountError = error is AdjustError.AmountInvalid || error is AdjustError.AmountTooPrecise ||
                error is AdjustError.AmountTooLarge
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it.take(20)
                    if (error != null && error != AdjustError.FutureDate) error = null
                },
                label = { Text(if (liability) "Actual amount owed" else "Actual balance") },
                prefix = { Text(MoneyFormatter.currencySymbol(wallet.currencyCode) + " ") },
                placeholder = { Text("0") },
                singleLine = true,
                isError = amountError,
                textStyle = AmountStyles.medium,
                supportingText = {
                    Text(
                        when (val e = error) {
                            AdjustError.AmountInvalid -> "Enter a number, like 1250${decimalSeparator}00."
                            is AdjustError.AmountTooPrecise ->
                                if (e.digits == 0) "${wallet.currencyCode} has no decimal places."
                                else "${wallet.currencyCode} allows up to ${e.digits} decimal places."
                            AdjustError.AmountTooLarge -> "That amount is larger than Lucento supports."
                            AdjustError.Failed -> "Couldn’t save the adjustment. Nothing was changed."
                            else -> if (liability) "From your latest statement or lender’s app." else "From your bank app, statement or a count of your cash."
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                plan != null -> {
                    // Signed from the wallet's point of view, as it will read in Transactions.
                    val signed = if (plan.type.isInflow) plan.amount else -plan.amount
                    InfoNote(
                        "Balance adjustment of ${MoneyFormatter.formatSigned(signed)} ${onDay(date, today)}. " +
                            "It shows in Transactions but isn’t counted as income or spending. Later transactions " +
                            "still apply on top.",
                        icon = Icons.Outlined.WarningAmber,
                        container = ext.warningContainer,
                        content = ext.warning,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                actual != null -> InfoNote(
                    "That matches what Lucento tracked — nothing to adjust.",
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            LucentoPrimaryButton(
                text = "Adjust balance",
                enabled = plan != null && !date.isAfter(today),
                onClick = {
                    onAdjust(amountText, decimalSeparator, date, clientUuid) { result ->
                        if (result == null) {
                            haptics.confirm()
                            close(onAdjusted)
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
