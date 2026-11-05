package com.lucento.app.feature.wallets

import com.lucento.app.core.designsystem.component.AmountsShown
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.LoanMath
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.lucento.app.core.designsystem.component.AmountTextField
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
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.SwitchRow
import com.lucento.app.core.designsystem.component.WalletTypeTile
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Wallet
import com.lucento.app.feature.plan.DateField
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.launch
import com.lucento.app.core.banks.Institution
import com.lucento.app.core.designsystem.component.InstitutionAvatar
import com.lucento.app.core.designsystem.component.LocalInstitutions
import androidx.compose.material.icons.outlined.Close
import java.time.LocalDate
import com.lucento.app.core.designsystem.component.LucentoFields
import com.lucento.app.core.designsystem.component.filledField
import com.lucento.app.core.designsystem.component.IconFieldHeader
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Percent

fun Wallet.toDraft(isDefault: Boolean = false): WalletDraft = WalletDraft(
    id = id,
    name = name,
    type = type,
    currencyCode = currencyCode,
    // The editor edits the opening balance; the current balance is derived from transactions.
    amountText = if (openingBalance.isZero) "" else MoneyFormatter.formatPlain(openingBalance),
    isDefault = isDefault,
    interestText = debt?.interestRateBps?.let { DebtMath.formatRate(it).removeSuffix("%") }.orEmpty(),
    repayment = debt?.repayment ?: DebtRepayment.ONE_TIME,
    termMonthsText = debt?.termMonths?.toString().orEmpty(),
    installmentText = debt?.installment?.let { MoneyFormatter.formatPlain(it) }.orEmpty(),
    institutionId = institutionId,
    creditLimitText = debt?.creditLimit?.let { MoneyFormatter.formatPlain(it) }.orEmpty(),
    statementDayText = debt?.statementDay?.toString().orEmpty(),
    dueDayText = debt?.dueDay?.toString().orEmpty(),
)

/**
 * The draft as the wallet it would become, for the editor's live card. Unfinished or invalid fields are simply left
 * off (a rate that doesn't parse shows no rate); nothing here is saved — saving still goes through the ViewModel's
 * full validation.
 */
internal fun WalletDraft.previewWallet(decimalSeparator: Char): Wallet {
    fun positive(text: String) = (MoneyParser.parse(text, currencyCode, decimalSeparator) as? MoneyParser.Result.Success)
        ?.money?.takeIf { it.amountMinor > 0 }
    val amount = (MoneyParser.parse(amountText, currencyCode, decimalSeparator) as? MoneyParser.Result.Success)?.money
        ?: com.lucento.app.core.money.Money.zero(currencyCode)
    val terms = if (!type.hasTerms) {
        null
    } else {
        runCatching {
            val rate = (DebtMath.parseRate(interestText) as? DebtMath.RateResult.Valid)?.bps
            val months = termMonthsText.trim().toIntOrNull()?.takeIf { it in 1..DebtTerms.MAX_TERM_MONTHS }
            val monthly = type.hasInstallments && repayment == DebtRepayment.MONTHLY && months != null
            val card = type == WalletType.CREDIT_CARD
            val statement = statementDayText.trim().toIntOrNull()?.takeIf { it in 1..31 }
            val due = dueDayText.trim().toIntOrNull()?.takeIf { it in 1..31 }
            DebtTerms(
                interestRateBps = rate,
                repayment = if (monthly) DebtRepayment.MONTHLY else DebtRepayment.ONE_TIME,
                termMonths = months.takeIf { monthly },
                installment = positive(installmentText).takeIf { monthly },
                creditLimit = positive(creditLimitText).takeIf { card },
                statementDay = statement.takeIf { card && due != null },
                dueDay = due.takeIf { card && statement != null },
            )
        }.getOrNull()
    }
    return Wallet(
        id = 0,
        name = name.trim().ifEmpty { "Your ${type.label.lowercase()}" },
        type = type,
        balance = amount,
        createdAtEpochMillis = 0,
        openingBalance = amount,
        debt = terms,
        institutionId = institutionId,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletEditorSheet(
    initial: WalletDraft,
    onSave: (WalletDraft, Char, (DraftError?, Long?) -> Unit) -> Unit,
    /** Deletes the wallet; the callback says whether it was deleted or what still uses it. */
    onDelete: ((Long, (WalletRepository.DeleteResult) -> Unit) -> Unit)?,
    onDismiss: () -> Unit,
    /**
     * After a loan with a payment reminder date is saved: opens a prefilled recurring payment for it (wallet id, first
     * due date). Null when recurring payments are switched off, which also hides the reminder field. A credit card's
     * reminder comes from its due day instead (CreditCardMath), so it never opens one.
     */
    onPlanPayment: ((Long, LocalDate) -> Unit)? = null,
) = AmountsShown {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<DraftError?>(null) }
    var pickingCurrency by remember { mutableStateOf(false) }
    var pickingInstitution by remember { mutableStateOf(false) }
    val institution = LocalInstitutions.current[draft.institutionId]
    var confirmingDelete by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf<WalletRepository.DeleteResult?>(null) }

    // Onboarding's filled fields: tinted, rounded, outlined only while focused.
    val fieldColors = LucentoFields.filledColors()

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
            SheetHeader(
                title = if (initial.id == null) "New wallet" else "Edit wallet",
                subtitle = if (initial.id == null) "Build it below and watch it take shape." else initial.name,
                leading = { WalletTypeTile(draft.type) },
                trailing = if (initial.id != null && onDelete != null) {
                    {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete wallet", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    null
                },
            )

            val receivable = draft.type == WalletType.RECEIVABLE
            val amountLabel = when {
                draft.type.isValuedManually -> "Estimated value"
                initial.id != null && draft.type.isLiability -> "Opening amount owed"
                initial.id != null && receivable -> "Opening amount owed to you"
                initial.id != null -> "Opening balance"
                draft.type.isLiability -> "Amount owed now"
                receivable -> "Amount owed to you now"
                else -> "Current balance"
            }
            if (initial.id == null) {
                // The wallet being built, drawn by the same card as its own page — name, bank face, balance, rate,
                // credit limit and amount due, or a loan's instalments — following every choice below. A new type or
                // bank swaps the card in with a short fade (none when animations are off).
                Spacer(Modifier.height(Spacing.xs))
                val reducedMotion = LucentoTheme.reducedMotion
                AnimatedContent(
                    targetState = draft.type to draft.institutionId.takeIf { draft.type != WalletType.CASH },
                    transitionSpec = {
                        if (reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.96f)) togetherWith fadeOut(tween(160))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "wallet preview",
                ) { (type, institutionId) ->
                    val preview = draft.copy(type = type, institutionId = institutionId).previewWallet(decimalSeparator)
                    val today = remember { LocalDate.now() }
                    WalletCard(
                        wallet = preview,
                        movements = 0,
                        cycle = if (type == WalletType.CREDIT_CARD) CreditCardMath.cycle(preview, emptyList(), today) else null,
                        loan = if (type.hasInstallments && preview.balance.amountMinor > 0) LoanMath.progress(preview, emptyList()) else null,
                        today = today,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))
            IconFieldHeader(Icons.Outlined.Category, "Type")
            TypeDropdown(
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

            if (draft.type != WalletType.CASH) {
                Spacer(Modifier.height(Spacing.md))
                IconFieldHeader(Icons.Outlined.AccountBalance, "Bank or provider (optional)")
                InstitutionField(
                    institution = institution,
                    onClick = { pickingInstitution = true },
                    onClear = { draft = draft.copy(institutionId = null) },
                )
            }

            Spacer(Modifier.height(Spacing.md))
            IconFieldHeader(Icons.Outlined.EditNote, "Details")
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
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                placeholder = {
                    Text(
                        when (draft.type) {
                            WalletType.CASH -> "e.g. Wallet cash"
                            WalletType.BANK -> "e.g. Everyday account"
                            WalletType.E_WALLET -> "e.g. Mobile wallet"
                            WalletType.SAVINGS -> "e.g. Emergency fund"
                            WalletType.CREDIT_CARD -> "e.g. Travel card"
                            WalletType.LOAN -> "e.g. Car loan"
                            WalletType.RECEIVABLE -> "e.g. Loan to Ana"
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
                shape = LucentoFields.shape,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .filledField(isError = error is DraftError.CurrencyLocked)
                    .clickable(role = Role.Button, onClickLabel = "Change currency") { pickingCurrency = true }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.md))
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
                    modifier = Modifier.padding(top = Spacing.xs, start = Spacing.md),
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            val amountError = error is DraftError.AmountInvalid ||
                error is DraftError.AmountTooPrecise || error is DraftError.AmountTooLarge || error is DraftError.AmountUnclear
            AmountTextField(
                value = draft.amountText,
                onValueChange = {
                    draft = draft.copy(amountText = it)
                    if (amountError) error = null
                },
                filter = { it.take(20) },
                label = { Text(amountLabel) },
                prefix = { Text(MoneyFormatter.currencySymbol(draft.currencyCode) + " ") },
                placeholder = { Text("0") },
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
                            is DraftError.AmountUnclear -> e.message
                            else -> when {
                                draft.type.isValuedManually -> "What it’s worth today. Update it whenever the value changes — no transactions needed."
                                initial.id != null -> "Where this wallet started, before the transactions you record."
                                draft.type == WalletType.LOAN -> "What you still owe on this loan. It counts against your net worth."
                                receivable -> "What they still owe you. It counts towards your net worth, but not as money you can spend."
                                draft.type.isLiability -> "What you currently owe on this card. It counts against your net worth."
                                else -> "Leave empty to start at zero."
                            }
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = LucentoFields.shape,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.type.hasTerms) {
                DebtDetails(
                    draft = draft,
                    error = error,
                    decimalSeparator = decimalSeparator,
                    showReminder = onPlanPayment != null,
                    fieldColors = fieldColors,
                    onChange = {
                        draft = it
                        if (error is DraftError.InterestInvalid || error is DraftError.TermInvalid || error is DraftError.InstallmentInvalid ||
                            error is DraftError.InstallmentUnclear || error is DraftError.CreditLimitInvalid ||
                            error is DraftError.CreditLimitUnclear || error is DraftError.BillingDaysInvalid
                        ) error = null
                    },
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

            Spacer(Modifier.height(Spacing.lg))
            LucentoPrimaryButton(
                text = if (initial.id == null) "Add wallet" else "Save changes",
                onClick = {
                    onSave(draft, decimalSeparator) { result, savedId ->
                        val remind = draft.reminderDate
                        when {
                            result != null -> {
                                haptics.reject()
                                error = result
                            }
                            savedId != null && remind != null && draft.type.hasInstallments && onPlanPayment != null ->
                                close { onPlanPayment(savedId, remind) }
                            else -> close()
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
    if (pickingInstitution) {
        InstitutionPickerSheet(
            selectedId = draft.institutionId,
            onSelect = {
                draft = draft.copy(institutionId = it?.id)
                pickingInstitution = false
            },
            onDismiss = { pickingInstitution = false },
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

/**
 * A card's or loan's terms: an optional yearly interest rate, then for a credit card its limit and billing days
 * ([CardDetails]); for a loan one-time or monthly repayment (months and the monthly amortisation, with a suggestion
 * from the amount owed), and an optional payment reminder date that opens a prefilled recurring payment after saving.
 */
@Composable
private fun DebtDetails(
    draft: WalletDraft,
    error: DraftError?,
    decimalSeparator: Char,
    showReminder: Boolean,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onChange: (WalletDraft) -> Unit,
) {
    val receivable = draft.type == WalletType.RECEIVABLE
    Spacer(Modifier.height(Spacing.sm))
    IconFieldHeader(
        if (draft.type == WalletType.CREDIT_CARD) Icons.Outlined.CreditCard else Icons.Outlined.Percent,
        when {
            draft.type == WalletType.CREDIT_CARD -> "Card details"
            receivable -> "Repayment details"
            else -> "Debt details"
        },
    )
    AmountTextField(
        value = draft.interestText,
        onValueChange = { input -> onChange(draft.copy(interestText = input)) },
        filter = { input -> input.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
        label = { Text("Interest rate (optional)") },
        suffix = { Text("% a year") },
        placeholder = { Text("0") },
        isError = error == DraftError.InterestInvalid,
        supportingText = {
            Text(if (error == DraftError.InterestInvalid) "Enter a yearly rate from 0 to 100, like 12.5." else "Shown on the wallet card. Lucento never fetches rates.")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        shape = LucentoFields.shape,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.type == WalletType.CREDIT_CARD) {
        CardDetails(draft, error, fieldColors, onChange)
        return
    }
    Spacer(Modifier.height(Spacing.xs))
    SegmentedToggle(
        options = listOf(DebtRepayment.ONE_TIME to "One time", DebtRepayment.MONTHLY to "Monthly"),
        selected = draft.repayment,
        onSelect = { onChange(draft.copy(repayment = it)) },
        compact = true,
    )
    if (draft.repayment == DebtRepayment.MONTHLY) {
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = draft.termMonthsText,
                onValueChange = { input -> onChange(draft.copy(termMonthsText = input.filter { it.isDigit() }.take(3))) },
                label = { Text("Months") },
                placeholder = { Text("12") },
                singleLine = true,
                isError = error == DraftError.TermInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                shape = LucentoFields.shape,
                colors = fieldColors,
                modifier = Modifier.weight(0.8f),
            )
            AmountTextField(
                value = draft.installmentText,
                onValueChange = { onChange(draft.copy(installmentText = it)) },
                filter = { it.take(20) },
                label = { Text(if (receivable) "Monthly instalment" else "Monthly amortization") },
                prefix = { Text(MoneyFormatter.currencySymbol(draft.currencyCode) + " ") },
                placeholder = { Text("0") },
                isError = error == DraftError.InstallmentInvalid || error is DraftError.InstallmentUnclear,
                textStyle = AmountStyles.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = LucentoFields.shape,
                colors = fieldColors,
                modifier = Modifier.weight(1.4f),
            )
        }
        val owed = (MoneyParser.parse(draft.amountText, draft.currencyCode, decimalSeparator) as? MoneyParser.Result.Success)?.money
        val months = draft.termMonthsText.toIntOrNull()?.takeIf { it in 1..DebtTerms.MAX_TERM_MONTHS }
        val rate = (DebtMath.parseRate(draft.interestText) as? DebtMath.RateResult.Valid)?.bps
        val suggested = if (owed != null && months != null) DebtMath.monthlyInstallment(owed, months, rate) else null
        when {
            error == DraftError.TermInvalid -> HintText("Enter how many months, from 1 to ${DebtTerms.MAX_TERM_MONTHS}.", isError = true)
            error == DraftError.InstallmentInvalid ->
                HintText(if (receivable) "Enter what they pay you each month." else "Enter what you pay each month.", isError = true)
            error is DraftError.InstallmentUnclear -> HintText((error as DraftError.InstallmentUnclear).message, isError = true)
            suggested != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                HintText(
                    "${MoneyFormatter.format(suggested)} a month ${if (receivable) "pays back" else "repays"} ${MoneyFormatter.format(owed!!)} in ${if (months == 1) "1 month" else "$months months"}" +
                        (rate?.let { " at ${DebtMath.formatRate(it)}" } ?: "") + ".",
                    modifier = Modifier.weight(1f),
                )
                val applied = (MoneyParser.parse(draft.installmentText, draft.currencyCode, decimalSeparator) as? MoneyParser.Result.Success)?.money == suggested
                if (!applied) {
                    LucentoTextButton(text = "Use", onClick = { onChange(draft.copy(installmentText = MoneyFormatter.formatPlain(suggested))) })
                }
            }
            else -> HintText(
                if (receivable) "Enter what you’re owed, the months and the rate to get a suggested instalment."
                else "Enter what you owe, the months and the rate to get a suggested amortization."
            )
        }
    }
    if (showReminder) {
        Spacer(Modifier.height(Spacing.sm))
        DateField(
            label = when {
                receivable && draft.repayment == DebtRepayment.MONTHLY -> "Collection reminder · first due date (optional)"
                receivable -> "Collection reminder · due date (optional)"
                draft.repayment == DebtRepayment.MONTHLY -> "Payment reminder · first due date (optional)"
                else -> "Payment reminder · due date (optional)"
            },
            date = draft.reminderDate,
            onDate = { onChange(draft.copy(reminderDate = it)) },
            emptyText = "No reminder",
        )
        if (draft.reminderDate != null) {
            HintText(
                if (receivable) "After saving, a recurring collection opens with this filled in; each one received moves the instalment into your account."
                else "After saving, a recurring payment opens with this filled in, so you can split it into principal and interest."
            )
        }
    }
}

/**
 * A credit card's limit and billing days. With both days set, Lucento reminds the user on the due day with the amount
 * due — what was owed on the statement day, less payments since (CreditCardMath) — and leaves it out of Safe to spend.
 */
@Composable
private fun CardDetails(
    draft: WalletDraft,
    error: DraftError?,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onChange: (WalletDraft) -> Unit,
) {
    val limitError = error is DraftError.CreditLimitInvalid || error is DraftError.CreditLimitUnclear
    Spacer(Modifier.height(Spacing.sm))
    AmountTextField(
        value = draft.creditLimitText,
        onValueChange = { onChange(draft.copy(creditLimitText = it)) },
        filter = { it.take(20) },
        label = { Text("Credit limit (optional)") },
        prefix = { Text(MoneyFormatter.currencySymbol(draft.currencyCode) + " ") },
        placeholder = { Text("0") },
        isError = limitError,
        textStyle = AmountStyles.medium,
        supportingText = {
            Text(
                when (error) {
                    DraftError.CreditLimitInvalid -> "Enter the card’s limit, more than zero."
                    is DraftError.CreditLimitUnclear -> error.message
                    else -> "Shows how much credit is still available."
                }
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        shape = LucentoFields.shape,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Spacing.xs))
    val daysError = error == DraftError.BillingDaysInvalid
    // Side by side at normal sizes; one under the other when large text would squeeze the labels.
    if (LocalDensity.current.fontScale >= 1.5f) {
        DayField(draft.statementDayText, "Statement day", "15", daysError, fieldColors, Modifier.fillMaxWidth()) { onChange(draft.copy(statementDayText = it)) }
        Spacer(Modifier.height(Spacing.sm))
        DayField(draft.dueDayText, "Due day", "5", daysError, fieldColors, Modifier.fillMaxWidth()) { onChange(draft.copy(dueDayText = it)) }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DayField(draft.statementDayText, "Statement day", "15", daysError, fieldColors, Modifier.weight(1f)) { onChange(draft.copy(statementDayText = it)) }
            DayField(draft.dueDayText, "Due day", "5", daysError, fieldColors, Modifier.weight(1f)) { onChange(draft.copy(dueDayText = it)) }
        }
    }
    HintText(
        if (daysError) {
            "Enter both days, from 1 to 31, or leave both empty."
        } else {
            "Day of the month (a shorter month uses its last day). On the due day, Lucento reminds you of the amount due: " +
                "what you owed on the statement day, less payments since. It isn’t counted in Safe to spend — your card " +
                "purchases already are."
        },
        isError = daysError,
    )
}

/** A day of the month, 1–31, typed as digits. */
@Composable
private fun DayField(
    value: String,
    label: String,
    placeholder: String,
    isError: Boolean,
    colors: androidx.compose.material3.TextFieldColors,
    modifier: Modifier,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValue(input.filter { it.isDigit() }.take(2)) },
        label = { Text(label, maxLines = 1) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        shape = LucentoFields.shape,
        colors = colors,
        modifier = modifier,
    )
}

/** The wallet's bank or provider: its icon and name, or a prompt; a clear button removes it. */
@Composable
private fun InstitutionField(institution: Institution?, onClick: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .filledField()
            .clickable(role = Role.Button, onClickLabel = if (institution == null) "Choose bank or provider" else "Change bank or provider", onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (institution != null) {
            InstitutionAvatar(institution, size = 32.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(institution.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${institutionKind(institution.type)} · ${countryName(institution.country)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove ${institution.name}")
            }
        } else {
            Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.md))
            Text(
                "None — shows the wallet type’s look",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(end = Spacing.sm))
        }
    }
}

@Composable
private fun HintText(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Spacing.md, top = Spacing.xs),
    )
}

/** The wallet type as one dropdown field (its icon and name), so the sheet stays short; the menu lists every type with its icon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(selected: WalletType, onSelect: (WalletType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .filledField(active = expanded)
                .semantics { contentDescription = "Wallet type: ${selected.label}" }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WalletTypeTile(selected, size = 32.dp)
            Spacer(Modifier.width(Spacing.md))
            Text(selected.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WalletType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label, fontWeight = if (type == selected) FontWeight.SemiBold else null) },
                    leadingIcon = { WalletTypeTile(type, size = 28.dp) },
                    onClick = {
                        expanded = false
                        onSelect(type)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
