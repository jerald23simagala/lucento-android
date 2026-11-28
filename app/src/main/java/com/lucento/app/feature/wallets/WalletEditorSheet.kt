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
import com.lucento.app.feature.fx.FxRateField

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
    fxRateText = fxBasisRate?.let(com.lucento.app.core.money.FxRate::encode).orEmpty(),
    kind = kind,
    iconKey = iconKey,
    spendable = spendable,
    spendableCreditText = spendableCredit?.takeIf { it.amountMinor > 0L }?.let { MoneyFormatter.formatPlain(it) }.orEmpty(),
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
        kind = kind?.takeIf { it.type == type },
        iconKey = iconKey,
        spendable = spendable,
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
    /**
     * The active wallets and the main currency, so a type or currency the plan doesn’t include is met when it is chosen,
     * not after the form is filled in (owner, 2026-11-18). Null skips that check; Save still applies the plan.
     */
    activeWallets: List<Wallet>? = null,
    mainCurrency: String? = null,
    /** The guided setup: what the plan doesn’t include isn’t offered, and nothing here mentions Pro (owner, 2026-11-18). */
    inSetup: Boolean = false,
) = AmountsShown {
    val sheetState = com.lucento.app.core.designsystem.component.rememberStickySheetState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val decimalSeparator = remember { MoneyFormatter.decimalSeparator() }

    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<DraftError?>(null) }
    var pickingCurrency by remember { mutableStateOf(false) }
    var pickingInstitution by remember { mutableStateOf(false) }
    var pickingType by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }
    // A new loan's or receivable's account for the money received or lent (tester round, 2026-11-27).
    var pickingReleaseAccount by remember { mutableStateOf(false) }
    val messageHosts = com.lucento.app.core.designsystem.component.LocalMessageHosts.current
    val institution = LocalInstitutions.current[draft.institutionId]
    var confirmingDelete by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf<WalletRepository.DeleteResult?>(null) }
    // A Pro wall met in this sheet (the dialog), or in the guided setup a plain note instead.
    var wall by remember { mutableStateOf<com.lucento.app.feature.pro.ProWall?>(null) }
    var setupNote by remember { mutableStateOf<String?>(null) }
    val openPro = com.lucento.app.feature.pro.LocalOpenPro.current
    val plan = com.lucento.app.feature.pro.LocalEntitlements.current
    val capabilities = remember(plan) { plan.capabilities(System.currentTimeMillis()) }
    val editingWallet = activeWallets?.firstOrNull { it.id == initial.id }

    // The early check needs the wallet list and, for an edit, the wallet in it; until then (still loading) the save decides.
    val planKnown = activeWallets != null && (initial.id == null || editingWallet != null)

    /**
     * What the plan says to this wallet being [type] in [currency] (the repository’s own rule, asked early). An edit across
     * the balance side (own ↔ owe, or into or out of something valued by hand) gets no early answer: a wallet with history
     * can't make it on any plan, and the save says which (QA 2026-11-19).
     */
    fun planAccess(type: WalletType, currency: String): com.lucento.app.domain.entitlement.Access =
        if (planKnown && !(editingWallet != null && com.lucento.app.domain.WalletChangePolicy.crossesBalanceSide(editingWallet.type, type))) {
            com.lucento.app.domain.entitlement.ProGates.walletAccess(type, currency, editingWallet, mainCurrency, activeWallets.orEmpty(), capabilities)
        } else {
            com.lucento.app.domain.entitlement.Access.Allowed
        }

    // Onboarding's filled fields: tinted, rounded, outlined only while focused.
    val fieldColors = LucentoFields.filledColors()

    fun close(then: () -> Unit = onDismiss) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragToDismiss = false) {
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
                leading = { DraftIconTile(draft) },
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
                draft.type == WalletType.INVESTMENT -> if (initial.id == null) "Current value" else "Starting value"
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
            // Tester round (2026-11-17): a sectioned picker with what each type is for, in place of a flat menu.
            WalletTypeField(selected = draft.type, currencyCode = draft.currencyCode, onClick = { pickingType = true })
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

            // The kind of an investment, owned thing or loan: a label, an icon and a group in Net worth.
            if (draft.type.hasKinds) {
                Spacer(Modifier.height(Spacing.md))
                IconFieldHeader(Icons.Outlined.Category, "Kind (optional)")
                WalletKindChips(type = draft.type, currencyCode = draft.currencyCode, selected = draft.kind) { draft = draft.copy(kind = it) }
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
            // Without a bank logo, the wallet shows an icon the person can choose.
            if (draft.type == WalletType.CASH || institution == null) {
                Spacer(Modifier.height(Spacing.sm))
                WalletIconField(draft = draft, onClick = { pickingIcon = true })
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
                            WalletType.INVESTMENT -> if (draft.currencyCode == "PHP") "e.g. MP2 2026, Stock portfolio" else "e.g. Stock portfolio, Retirement fund"
                            WalletType.CREDIT_CARD -> "e.g. Travel card"
                            WalletType.LOAN -> "e.g. Car loan"
                            WalletType.RECEIVABLE -> "e.g. Loan to Ana"
                            WalletType.OTHER_ASSET -> "e.g. Home, car, jewellery"
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
                        "Create a wallet in the other currency instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs, start = Spacing.md),
                )
            }
            // Multicurrency: a wallet in another currency is valued in the main currency from this rate on.
            val main = com.lucento.app.feature.fx.LocalMainCurrency.current
            if (main != null && draft.currencyCode != main) {
                Spacer(Modifier.height(Spacing.sm))
                FxRateField(
                    currency = draft.currencyCode,
                    mainCurrency = main,
                    text = draft.fxRateText,
                    onTextChange = {
                        draft = draft.copy(fxRateText = it)
                        if (error == DraftError.RateRequired) error = null
                    },
                    isError = error == DraftError.RateRequired,
                    explanation = if (initial.id == null || initial.fxRateText.isBlank()) {
                        "Its balance is valued at this rate in your totals; each transaction then uses its own rate. " +
                            "Settings › Money can fill in rates online."
                    } else {
                        "The rate its starting balance is valued at. Changing it recalculates this wallet's value."
                    },
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            val amountError = error is DraftError.AmountInvalid || error == DraftError.ReleaseNeedsAmount ||
                error is DraftError.AmountTooPrecise || error is DraftError.AmountTooLarge || error is DraftError.AmountUnclear
            // A new loan or receivable recording the money that moved: the amount is everything owed (tester round, 2026-11-27).
            // Never in the guided setup, where balances are typed as they are today (QA, 2026-11-27).
            val offersRelease = initial.id == null && !inSetup && com.lucento.app.domain.LoanRelease.appliesTo(draft.type)
            val releasing = offersRelease && draft.recordRelease
            // Editing one whose money was recorded opens at zero, so say what it owes now, before anyone "fixes" the start.
            val owedNow = editingWallet
                ?.takeIf { com.lucento.app.domain.LoanRelease.appliesTo(it.type) && it.balance != it.openingBalance }
                ?.let { " With them, ${if (it.type == WalletType.RECEIVABLE) "you’re owed" else "you owe"} ${MoneyFormatter.format(it.balance, wholeUnits = false)} now." }
                .orEmpty()
            AmountTextField(
                value = draft.amountText,
                onValueChange = {
                    draft = draft.copy(amountText = it)
                    if (amountError || error == DraftError.ChargesTooHigh) error = null
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
                            DraftError.ReleaseNeedsAmount ->
                                if (receivable) "Enter how much you lent." else "Enter what you owe, to record the money you received."
                            else -> when {
                                draft.type.isValuedManually -> "What it’s worth today. Update it whenever the value changes — no transactions needed."
                                draft.type == WalletType.INVESTMENT && initial.id == null ->
                                    "What it’s worth today. Money you put in or take out is a transfer; use Update value when it grows or falls."
                                initial.id != null -> "Where this wallet started, before the transactions you record.$owedNow"
                                releasing && receivable -> "What they owe you: the amount you lent."
                                releasing -> "The principal on your contract, before charges are taken out. Leave out interest you’ll pay with each instalment."
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

            // Tester round (2026-11-27): a new loan's money received, or a new receivable's money lent, saved with it.
            if (offersRelease) {
                ReleaseSection(
                    draft = draft,
                    error = error,
                    accounts = activeWallets,
                    decimalSeparator = decimalSeparator,
                    fieldColors = fieldColors,
                    onChange = {
                        draft = it
                        if (error?.isRelease == true || error == DraftError.ReleaseNeedsAmount) error = null
                    },
                    onPickAccount = { pickingReleaseAccount = true },
                )
            }

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
                            error is DraftError.CreditLimitUnclear || error is DraftError.BillingDaysInvalid ||
                            error is DraftError.AmountDueInvalid || error is DraftError.AmountDueTooHigh
                        ) error = null
                    },
                )
            }

            // Tester round (2026-11-17): whether this wallet counts towards Safe to spend's money on hand.
            if (draft.type.canBeSpendingMoney) {
                Spacer(Modifier.height(Spacing.sm))
                SpendingMoneySection(
                    draft = draft,
                    error = error,
                    decimalSeparator = decimalSeparator,
                    fieldColors = fieldColors,
                    onChange = {
                        draft = it
                        if (error == DraftError.SpendableCreditInvalid || error == DraftError.SpendableCreditTooHigh) error = null
                    },
                )
            }

            if (draft.type.canBeDefault) {
                Spacer(Modifier.height(Spacing.sm))
                SwitchRow(
                    title = "Default wallet",
                    description = if (draft.type == WalletType.CREDIT_CARD) {
                        // Tester round (2026-11-18): a card can be the default; income and transfers still go to cash or a bank.
                        "New expenses go here unless you pick another wallet. Income and transfers go to your cash, bank or e-wallet."
                    } else {
                        "New transactions go here unless you pick another wallet in the composer."
                    },
                    checked = draft.isDefault,
                    onCheckedChange = { draft = draft.copy(isDefault = it) },
                )
            }

            // The guided setup says plainly that more can come later, never "Pro" (owner, 2026-11-18).
            // The database refused the write: nothing was saved (QA, 2026-11-27).
            if (error == DraftError.SaveFailed) {
                Spacer(Modifier.height(Spacing.md))
                com.lucento.app.core.designsystem.component.InfoNote(
                    "Couldn’t save. Nothing was changed, so you can try again.",
                    container = LucentoTheme.extendedColors.warningContainer,
                    content = LucentoTheme.extendedColors.warning,
                )
            }
            setupNote?.let { note ->
                Spacer(Modifier.height(Spacing.md))
                com.lucento.app.core.designsystem.component.InfoNote(note)
            }
            Spacer(Modifier.height(Spacing.lg))
            LucentoPrimaryButton(
                text = if (initial.id == null) "Add wallet" else "Save changes",
                onClick = {
                    onSave(draft, decimalSeparator) { result, savedId ->
                        val remind = draft.reminderDate
                        // Another account changed too, so say where the money went once the sheet has closed.
                        val recorded = if (result == null) releaseSavedMessage(draft, activeWallets) else null
                        fun announce() {
                            recorded?.let { messageHosts?.app?.show(it) }
                        }
                        when {
                            // A plan limit met at Save (the early check missed it, or wasn’t asked): the Pro dialog, or in
                            // the guided setup the plain note.
                            result is DraftError.PlanLimit -> {
                                haptics.reject()
                                if (inSetup) {
                                    setupNote = setupLine(result.feature, mainCurrency)
                                } else {
                                    wall = com.lucento.app.feature.pro.ProWall(result.feature, result.used, result.limit)
                                }
                            }
                            result != null -> {
                                haptics.reject()
                                error = result
                            }
                            savedId != null && remind != null && draft.type.hasInstallments && onPlanPayment != null ->
                                close {
                                    onPlanPayment(savedId, remind)
                                    announce()
                                }
                            else -> close {
                                onDismiss()
                                announce()
                            }
                        }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (pickingType) {
        // Types the plan doesn’t include now: marked Pro (picking one opens the dialog), or in the guided setup not offered.
        val locked = if (planKnown) {
            WalletType.entries.filter { planAccess(it, draft.currencyCode) != com.lucento.app.domain.entitlement.Access.Allowed }.toSet()
        } else if (plan.has(com.lucento.app.domain.entitlement.Capability.LOCAL_PRO, System.currentTimeMillis())) {
            emptySet()
        } else {
            // Without the list: the Pro-only kinds, never the wallet's own type (one kept from Pro stays editable).
            setOf(WalletType.RECEIVABLE, WalletType.OTHER_ASSET) - initial.type
        }
        WalletTypePickerSheet(
            selected = draft.type,
            currencyCode = draft.currencyCode,
            proTypes = if (inSetup) emptySet() else locked,
            hidden = if (inSetup) locked else emptySet(),
            proLabel = { type ->
                com.lucento.app.feature.pro.ProWall.of(planAccess(type, draft.currencyCode))
                    ?.let { com.lucento.app.feature.pro.ProCopy.wallTitle(it) } ?: "Lucento Pro feature"
            },
            onSelect = { type ->
                val met = com.lucento.app.feature.pro.ProWall.of(planAccess(type, draft.currencyCode))
                if (met != null && !inSetup) {
                    // The dialog at the tap that would cross the limit; the type stays as it was (owner, 2026-11-18).
                    pickingType = false
                    wall = met
                } else {
                    // A kind belongs to its type; an icon the person chose stays.
                    draft = draft.copy(type = type, kind = draft.kind?.takeIf { it.type == type })
                    if (error is DraftError.TypeLocked || error is DraftError.PlanLimit) error = null
                    setupNote = null
                    pickingType = false
                }
            },
            onDismiss = { pickingType = false },
        )
    }
    wall?.let { met ->
        com.lucento.app.feature.pro.ProWallDialog(
            met,
            onDismiss = { wall = null },
            // The sheet closes first, then the plan page opens.
            open = { feature ->
                close {
                    onDismiss()
                    openPro(feature)
                }
            },
        )
    }
    if (pickingIcon) {
        WalletIconPickerSheet(
            type = draft.type,
            current = com.lucento.app.domain.WalletIcon.fromKey(draft.iconKey),
            suggested = draft.kind?.defaultIcon,
            onSelect = {
                draft = draft.copy(iconKey = it.key)
                pickingIcon = false
            },
            onDismiss = { pickingIcon = false },
        )
    }
    if (pickingCurrency) {
        CurrencyPickerSheet(
            selected = draft.currencyCode,
            onSelect = { code ->
                val met = com.lucento.app.feature.pro.ProWall.of(planAccess(draft.type, code))
                pickingCurrency = false
                when {
                    // Another currency the plan doesn’t include yet: the dialog, or in the guided setup a plain note.
                    met != null && !inSetup -> wall = met
                    met != null -> setupNote = setupLine(met.feature, mainCurrency)
                    else -> {
                        // Money received or lent moves within one currency: keep its account if it fits, else the likeliest
                        // one; with none in the new currency there is nothing to record it in.
                        val usable = activeWallets?.filter { com.lucento.app.domain.LoanRelease.canUse(it, code) }
                        val account = draft.releaseAccountId?.takeIf { id -> usable?.any { it.id == id } ?: true }
                            ?: usable?.let { com.lucento.app.domain.LoanRelease.suggestedAccount(it, code) }?.id
                        draft = draft.copy(
                            currencyCode = code,
                            releaseAccountId = account,
                            recordRelease = draft.recordRelease && usable?.isNotEmpty() != false,
                        )
                        if (error is DraftError.AmountTooPrecise || error is DraftError.CurrencyLocked || error is DraftError.PlanLimit ||
                            error?.isRelease == true
                        ) error = null
                        setupNote = null
                    }
                }
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
    if (pickingReleaseAccount) {
        val loan = draft.type == WalletType.LOAN
        com.lucento.app.core.designsystem.component.AccountPickerSheet(
            title = if (loan) "Received in" else "Lent from",
            subtitle = if (loan) "The account the loan’s money went into" else "The account the money came from",
            // Accounts money moves through; another currency stays listed, dimmed, with why (Lucento never converts it).
            wallets = activeWallets.orEmpty().filter { it.type.acceptsTransactions },
            selectedId = draft.releaseAccountId,
            onPick = {
                draft = draft.copy(releaseAccountId = it.id)
                if (error == DraftError.ReleaseAccountRequired || error == DraftError.ReleaseAccountUnavailable) error = null
                pickingReleaseAccount = false
            },
            onDismiss = { pickingReleaseAccount = false },
            unavailable = { wallet ->
                if (wallet.currencyCode != draft.currencyCode) "In ${wallet.currencyCode}; this one is in ${draft.currencyCode}" else null
            },
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
            WalletRepository.DeleteResult.NotPermitted -> "Only the Shared Space’s owner can change this."
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
                    "${MoneyFormatter.format(suggested, wholeUnits = false)} a month ${if (receivable) "pays back" else "repays"} ${MoneyFormatter.format(owed!!, wholeUnits = false)} in ${if (months == 1) "1 month" else "$months months"}" +
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
            // Tinted like every other field in this form (the money received above uses the same look).
            filled = true,
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
                "what you owed on the statement day, less payments since. Your purchases already count as spending, so " +
                "the payment isn’t spending again; Safe to spend sets it aside from the account that pays the card."
        },
        isError = daysError,
    )
    // Tester round 3: a new card's balance is rarely all due at once. With both billing days, ask what the latest
    // statement asks to pay; the rest joins the next statement (CardSetup), so the first reminder isn't the whole balance.
    if (draft.id == null && draft.statementDayText.isNotBlank() && draft.dueDayText.isNotBlank()) {
        val dueError = error == DraftError.AmountDueInvalid || error == DraftError.AmountDueTooHigh
        Spacer(Modifier.height(Spacing.sm))
        AmountTextField(
            value = draft.amountDueText,
            onValueChange = { onChange(draft.copy(amountDueText = it)) },
            filter = { it.take(20) },
            label = { Text("Amount due now (optional)") },
            prefix = { Text(MoneyFormatter.currencySymbol(draft.currencyCode) + " ") },
            placeholder = { Text("All of it") },
            isError = dueError,
            textStyle = AmountStyles.medium,
            supportingText = {
                Text(
                    when (error) {
                        DraftError.AmountDueInvalid -> "Enter the amount due, like 1250${MoneyFormatter.decimalSeparator()}00."
                        DraftError.AmountDueTooHigh -> "The amount due can’t be more than what you owe on the card."
                        else -> "From your latest statement: what you must pay by the due day. The rest of what you owe is " +
                            "billed on your next statement. Leave empty if all of it is due."
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            shape = LucentoFields.shape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
internal fun HintText(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Spacing.md, top = Spacing.xs),
    )
}

/** The guided setup’s plain words when the plan holds no more of a kind (owner, 2026-11-18: no mention of Pro). */
/**
 * The guided setup's plain line for a limit, with no mention of Pro: the count, or for another currency what setup does
 * ("Setup uses your main currency, PHP."), which promises nothing the plan doesn't give.
 */
private fun setupLine(feature: com.lucento.app.domain.entitlement.ProFeature, mainCurrency: String?): String =
    if (feature == com.lucento.app.domain.entitlement.ProFeature.FOREIGN_CURRENCY_WALLETS) {
        "Setup uses your main currency${mainCurrency?.let { ", $it" }.orEmpty()}."
    } else {
        SETUP_ADD_LATER
    }

private val SETUP_ADD_LATER =
    "Setup adds up to ${com.lucento.app.domain.entitlement.PlanCatalog.FREE_MONEY_WALLETS} accounts and " +
        "${com.lucento.app.domain.entitlement.PlanCatalog.FREE_DEBT_ACCOUNTS} cards or loans. You can edit them any time in Wallets."
