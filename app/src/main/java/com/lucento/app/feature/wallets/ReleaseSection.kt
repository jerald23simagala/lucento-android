package com.lucento.app.feature.wallets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.AccountField
import com.lucento.app.core.designsystem.component.AmountTextField
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.component.LedgerRow
import com.lucento.app.core.designsystem.component.LucentoFields
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.domain.LoanRelease
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.feature.plan.DateField
import java.time.LocalDate

/**
 * A new loan's "Money received" or a new receivable's "Money lent" (tester round, 2026-11-27; [LoanRelease]). It stays
 * off until switched on. Then it asks for the account the money went into or came from (the likeliest one is filled in),
 * a loan's charges and deductions, and the day. A short summary shows what saving will record, adding up to the amount
 * owed above. Nothing is written here: Add wallet saves it together with the wallet ([WalletsViewModel.save]).
 */
@Composable
internal fun ReleaseSection(
    draft: WalletDraft,
    error: DraftError?,
    /** The active wallets, or null while they load. */
    accounts: List<Wallet>?,
    decimalSeparator: Char,
    fieldColors: TextFieldColors,
    onChange: (WalletDraft) -> Unit,
    onPickAccount: () -> Unit,
) {
    val loan = draft.type == WalletType.LOAN
    val today = remember { LocalDate.now() }
    val usable = accounts?.filter { LoanRelease.canUse(it, draft.currencyCode) }
    // With no account in this currency to record it in, the switch stays off and says why.
    val noAccount = usable != null && usable.isEmpty()
    val on = draft.recordRelease && !noAccount
    // The wallets load with the sheet: if the switch went on before they arrived, start from the likeliest account then.
    val latest by rememberUpdatedState(draft)
    LaunchedEffect(on, usable) {
        if (on && latest.releaseAccountId == null && usable != null) {
            LoanRelease.suggestedAccount(usable, latest.currencyCode)?.let { onChange(latest.copy(releaseAccountId = it.id)) }
        }
    }

    Spacer(Modifier.height(Spacing.md))
    // One control, its icon the app's money-in / money-out arrow, rather than a header repeating the switch.
    SwitchRow(
        title = releaseSwitchTitle(draft.type),
        description = when {
            noAccount -> "Optional. This needs an account in ${draft.currencyCode}. Add one in Wallets first."
            loan -> "Optional, for a loan just released: record the account its money went into, less any charges."
            else -> "Optional: record the account the money came from, as a transfer into this receivable."
        },
        icon = if (loan) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
        checked = on,
        enabled = !noAccount,
        onCheckedChange = { checked ->
            // Switched on: keep the account already chosen if it still fits, otherwise start from the likeliest one.
            val kept = draft.releaseAccountId?.takeIf { id -> usable?.any { it.id == id } ?: true }
            val suggested = usable?.let { LoanRelease.suggestedAccount(it, draft.currencyCode) }?.id
            onChange(draft.copy(recordRelease = checked, releaseAccountId = kept ?: suggested))
        },
    )
    if (!on) return

    val chosen = accounts?.firstOrNull { it.id == draft.releaseAccountId }
    Spacer(Modifier.height(Spacing.sm))
    AccountField(
        wallet = chosen,
        onClick = onPickAccount,
        label = if (loan) "Received in" else "Lent from",
        isError = error == DraftError.ReleaseAccountRequired || error == DraftError.ReleaseAccountUnavailable,
        filled = true,
    )
    when (error) {
        DraftError.ReleaseAccountRequired ->
            HintText(if (loan) "Choose the account the money went into." else "Choose the account the money came from.", isError = true)
        DraftError.ReleaseAccountUnavailable ->
            HintText("That account can’t be used here. Choose one in ${draft.currencyCode}.", isError = true)
        else -> Unit
    }

    Spacer(Modifier.height(Spacing.sm))
    DateField(
        label = if (loan) "Received on" else "Lent on",
        date = draft.releaseDate ?: today,
        onDate = { onChange(draft.copy(releaseDate = it)) },
        clearable = false,
        filled = true,
        latest = today,
    )
    when (error) {
        DraftError.ReleaseDateInFuture -> HintText("Choose today or an earlier day.", isError = true)
        DraftError.ReleaseInvalid -> HintText("These amounts don’t add up. Check the amount owed and the charges.", isError = true)
        else -> Unit
    }

    if (loan) {
        Spacer(Modifier.height(Spacing.sm))
        // Shown while typing, not only after Add wallet.
        val tooHigh = error == DraftError.ChargesTooHigh || chargesTakeEverything(draft, decimalSeparator)
        val chargesError = error == DraftError.ChargesInvalid || error is DraftError.ChargesUnclear || tooHigh
        AmountTextField(
            value = draft.chargesText,
            onValueChange = { onChange(draft.copy(chargesText = it)) },
            filter = { it.take(20) },
            label = { Text("Charges (optional)", maxLines = 1) },
            prefix = { Text(MoneyFormatter.currencySymbol(draft.currencyCode) + " ") },
            placeholder = { Text("0") },
            isError = chargesError,
            textStyle = AmountStyles.medium,
            supportingText = {
                Text(
                    when {
                        error == DraftError.ChargesInvalid -> "Enter the charges, like 1250${decimalSeparator}00."
                        error is DraftError.ChargesUnclear -> error.message
                        tooHigh -> "The charges can’t be the whole loan or more."
                        else -> "Fees, taxes, deductions or interest the lender kept before paying you."
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            shape = LucentoFields.shape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }


    ReleaseSummary(draft, chosen, decimalSeparator)
}

/**
 * What saving will record, read from the fields as typed. Nothing shows while they don't add up yet: Add wallet then
 * says which field to fix. A loan with charges reads as a ledger (the amount owed, less the charges, is what reaches the
 * account); otherwise one sentence says where the money goes and that it is a transfer.
 */
@Composable
private fun ReleaseSummary(draft: WalletDraft, account: Wallet?, decimalSeparator: Char) {
    account ?: return
    val plan = releasePreview(draft, decimalSeparator) ?: return
    val fmt = amountFormat()
    val colors = MaterialTheme.colorScheme
    val loan = draft.type == WalletType.LOAN
    // The account's balance before and after, so money already in it shows up as counted twice (QA, 2026-11-27).
    val after = LoanRelease.accountAfter(account, draft.type, plan)
    val balanceLine = if (account.type.isLiability) {
        "What ${account.name} owes goes from ${fmt.signed(account.balance, showPlus = false)} to ${fmt.signed(after, showPlus = false)}."
    } else {
        "${account.name} goes from ${fmt.signed(account.balance, showPlus = false)} to ${fmt.signed(after, showPlus = false)}."
    }
    Spacer(Modifier.height(Spacing.sm))
    // Outlined, not tinted: the fields around it are tinted, and this is something to read, not to fill in.
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val sentence = MaterialTheme.typography.bodySmall
        when {
            draft.type != WalletType.LOAN -> Text(
                "${fmt.format(plan.moved)} comes out of ${account.name}. It’s money lent, not spending.",
                style = sentence,
                color = colors.onSurfaceVariant,
            )
            plan.charges.isZero -> Text(
                "${fmt.format(plan.moved)} goes into ${account.name}: a transfer from the loan, not income. If " +
                    "${account.name} shows less, enter the charges the lender kept.",
                style = sentence,
                color = colors.onSurfaceVariant,
            )
            else -> {
                LedgerRow("Amount owed", plan.moved + plan.charges)
                LedgerRow("Charges and deductions", -plan.charges)
                // The total's rule, as on a receipt.
                Hairline()
                LedgerRow("Into ${account.name}", plan.moved, emphasized = true)
                Text(
                    "If ${account.name} shows a different amount, change the charges until this matches. It’s a transfer " +
                        "from the loan, not income; the charges count as spending, under ${LoanRelease.FEES_CATEGORY}.",
                    style = sentence,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Text(balanceLine, style = sentence, color = colors.onSurface)
    }
    // An account added after the money moved was probably typed with it already in (or out of) its balance.
    val date = draft.releaseDate ?: LocalDate.now()
    if (LoanRelease.addedAfter(account, date)) {
        val added = java.time.Instant.ofEpochMilli(account.createdAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        Spacer(Modifier.height(Spacing.sm))
        InfoNote(
            if (loan) {
                "You added ${account.name} on ${shortDate(added)}, after this money arrived on ${shortDate(date)}. If its " +
                    "starting balance already includes the ${fmt.format(plan.moved)}, turn off ${releaseSwitchTitle(draft.type)} " +
                    "so it isn’t counted twice."
            } else {
                "You added ${account.name} on ${shortDate(added)}, after this money left on ${shortDate(date)}. If its " +
                    "starting balance already leaves out the ${fmt.format(plan.moved)}, turn off ${releaseSwitchTitle(draft.type)} " +
                    "so it isn’t taken out twice."
            },
            container = LucentoTheme.extendedColors.warningContainer,
            content = LucentoTheme.extendedColors.warning,
        )
    }
}

/** The switch's name, which the warning refers to. */
internal fun releaseSwitchTitle(type: WalletType): String = if (type == WalletType.LOAN) "Money received" else "Money lent"

/** True when the charges as typed are the whole amount owed or more (shown at once, not only after Add wallet). */
internal fun chargesTakeEverything(draft: WalletDraft, decimalSeparator: Char): Boolean {
    if (draft.type != WalletType.LOAN || draft.chargesText.isBlank()) return false
    val owed = (MoneyParser.parse(draft.amountText, draft.currencyCode, decimalSeparator) as? MoneyParser.Result.Success)?.money ?: return false
    val charges = (MoneyParser.parse(draft.chargesText, draft.currencyCode, decimalSeparator) as? MoneyParser.Result.Success)?.money ?: return false
    return owed.amountMinor > 0L && LoanRelease.plan(draft.type, owed, charges) == LoanRelease.Plan.ChargesTooHigh
}

/** The split the fields describe now ([LoanRelease.plan]), or null while the amount or the charges don't read. */
internal fun releasePreview(draft: WalletDraft, decimalSeparator: Char): LoanRelease.Plan.Ready? {
    val owed = (MoneyParser.parse(draft.amountText, draft.currencyCode, decimalSeparator) as? MoneyParser.Result.Success)?.money ?: return null
    val charges = if (draft.type == WalletType.LOAN) {
        when (val parsed = MoneyParser.parse(draft.chargesText, draft.currencyCode, decimalSeparator)) {
            MoneyParser.Result.Empty -> null
            is MoneyParser.Result.Success -> parsed.money
            else -> return null
        }
    } else {
        null
    }
    return LoanRelease.plan(draft.type, owed, charges) as? LoanRelease.Plan.Ready
}

/**
 * The line shown once the wallet is saved with its release, because another account changed too: "Car loan added.
 * Its money went into BDO Savings." No amount, so it stays right where the person hides their figures. Null when
 * nothing was recorded.
 */
internal fun releaseSavedMessage(draft: WalletDraft, accounts: List<Wallet>?): String? {
    if (draft.id != null || !draft.recordRelease || !LoanRelease.appliesTo(draft.type)) return null
    val account = accounts?.firstOrNull { it.id == draft.releaseAccountId } ?: return null
    val name = draft.name.trim()
    return if (draft.type == WalletType.LOAN) "$name added. Its money went into ${account.name}." else "$name added. The money came out of ${account.name}."
}
