package com.lucento.app.feature.plan

import com.lucento.app.core.designsystem.component.AmountsShown
import com.lucento.app.core.designsystem.component.label
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.IconFieldHeader
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.WalletIdentityTile
import java.time.LocalDate

/**
 * What a schedule is: a bill (expense), income, a payment towards a card or loan, or an instalment collected from a
 * receivable (owner round 10) — the two linked kinds move their principal between the user's own accounts.
 */
internal enum class ScheduleKind(val label: String, val shortLabel: String = label) {
    BILL("Bill"), INCOME("Income"), DEBT("Debt payment", "Debt"), COLLECTION("Collection", "Collect");

    val isLinked: Boolean get() = this == DEBT || this == COLLECTION
}

internal data class ScheduleDraft(
    val id: Long? = null,
    val name: String = "",
    val kind: ScheduleKind = ScheduleKind.BILL,
    /** The amount; for a debt payment, the whole payment (e.g. the monthly amortization). */
    val amountText: String = "",
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
)

/**
 * Wallets a debt payment can come from, or a collection go into: money accounts (never a card, loan or receivable,
 * nor a valued asset).
 */
internal fun PlanUiState.payingWallets(currencyCode: String? = null): List<Wallet> =
    wallets.filter {
        !it.type.isLiability && it.type != WalletType.RECEIVABLE && it.type.acceptsTransactions &&
            (currencyCode == null || it.currencyCode == currencyCode)
    }

internal fun PlanUiState.debts(): List<Wallet> = wallets.filter { it.type.isLiability }

/** "week", "month", "year": the unit of one step of [frequency]. */
private fun unitOf(frequency: Frequency): String = when (frequency) {
    Frequency.WEEKLY -> "week"
    Frequency.MONTHLY -> "month"
    Frequency.YEARLY -> "year"
}

/** What the user is owed: their receivables (owner round 10). */
internal fun PlanUiState.receivables(): List<Wallet> = wallets.filter { it.type == WalletType.RECEIVABLE }

/**
 * A payment towards [debt] starting on [firstDue], filled in from its terms: "Monthly Payment - Car loan" for the
 * monthly amortization over the remaining months, or "One-time Payment - Card" for what is owed. The split into
 * principal and interest is entered when each payment is marked paid.
 */
internal fun PlanUiState.debtDraft(debt: Wallet, firstDue: LocalDate): ScheduleDraft {
    val terms = debt.debt
    val monthly = terms?.repayment == DebtRepayment.MONTHLY
    val amount = if (monthly) terms?.installment else debt.balance.takeIf { it.amountMinor > 0 }
    // A receivable's reminder is a collection: "Monthly Collection - Loan to Ana", into a money account.
    val collection = debt.type == WalletType.RECEIVABLE
    val repayment = terms?.repayment ?: DebtRepayment.ONE_TIME
    return ScheduleDraft(
        name = if (collection) DebtMath.collectionName(repayment, debt.name, RecurringRepository.MAX_NAME)
        else DebtMath.reminderName(repayment, debt.name, RecurringRepository.MAX_NAME),
        kind = if (collection) ScheduleKind.COLLECTION else ScheduleKind.DEBT,
        amountText = amount?.let { MoneyFormatter.formatPlain(it) }.orEmpty(),
        walletId = payingWallets(debt.currencyCode).firstOrNull()?.id,
        toWalletId = debt.id,
        frequency = Frequency.MONTHLY,
        startDate = firstDue,
        endDate = if (monthly) terms?.termMonths?.let { firstDue.plusMonths(it - 1L) } else firstDue,
    )
}

/** An existing schedule as an editable draft; a debt payment's whole payment (principal + any interest) is its amount. */
internal fun RecurringSchedule.toDraft(): ScheduleDraft = ScheduleDraft(
    id = id,
    name = name,
    kind = when {
        isDebtPayment -> ScheduleKind.DEBT
        isCollection -> ScheduleKind.COLLECTION
        type == TransactionType.INCOME -> ScheduleKind.INCOME
        else -> ScheduleKind.BILL
    },
    amountText = MoneyFormatter.formatPlain(total),
    walletId = walletId,
    toWalletId = toWalletId,
    categoryId = categoryId,
    frequency = frequency,
    interval = interval,
    startDate = startDate,
    endDate = endDate,
)

/**
 * The new / edit recurring payment page (full screen, back pill, pinned save — the transaction editor's layout).
 * [scheduleId] edits an existing schedule; [prefillDebt] starts a debt payment for that card or loan on that date.
 * A debt payment is planned as one amount (the amortization); the principal / interest split is entered at Mark paid.
 */
@Composable
fun RecurringEditorScreen(
    onBack: () -> Unit,
    scheduleId: Long? = null,
    prefillDebt: Pair<Long, LocalDate>? = null,
    onSaved: () -> Unit = onBack,
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) = AmountsShown {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    var draft by remember { mutableStateOf<ScheduleDraft?>(null) }
    var initialised by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (state.loading || draft != null) return@LaunchedEffect
        val schedule = scheduleId?.let { id -> state.schedules.firstOrNull { it.id == id } }
        val debt = prefillDebt?.let { (id, _) -> state.walletsById[id] }
        draft = when {
            schedule != null -> schedule.toDraft()
            debt != null && !initialised -> state.debtDraft(debt, prefillDebt.second)
            else -> ScheduleDraft(walletId = state.payingWallets().firstOrNull()?.id ?: state.wallets.firstOrNull()?.id)
        }
        initialised = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        LucentoTopBar(
            title = if (scheduleId == null) "Schedule transaction" else "Edit schedule",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            // Wraps onto a second line at large text sizes instead of being cut off (owner round 10).
            titleMaxLines = 2,
        )
        draft?.let { current ->
            ScheduleEditorBody(
                draft = current,
                isNew = scheduleId == null,
                state = state,
                onChange = { draft = it },
                onSave = { input, done -> viewModel.saveSchedule(input, done) },
                onSaved = onSaved,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ScheduleEditorBody(
    draft: ScheduleDraft,
    isNew: Boolean,
    state: PlanUiState,
    onChange: (ScheduleDraft) -> Unit,
    onSave: (RecurringRepository.Input, (RecurringRepository.SaveResult) -> Unit) -> Unit,
    onSaved: () -> Unit,
) {
    val separator = remember { MoneyFormatter.decimalSeparator() }
    val haptics = rememberHaptics()
    var error by remember { mutableStateOf<String?>(null) }
    val debts = state.debts()
    val receivables = state.receivables()
    // The linked account: the card or loan a debt payment pays, or the receivable a collection comes from.
    val debt = draft.toWalletId?.let { state.walletsById[it] }
    val isDebt = draft.kind.isLinked
    val isCollection = draft.kind == ScheduleKind.COLLECTION
    val sources = if (isDebt) state.payingWallets(debt?.currencyCode) else state.wallets
    val wallet = state.walletsById[draft.walletId]?.takeIf { it in sources }
    val currency = debt?.currencyCode ?: wallet?.currencyCode ?: state.primaryCurrency
    val kind = if (draft.kind == ScheduleKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { it.kind == kind && (!it.archived || it.id == draft.categoryId) }
    // The linked kinds appear only when there is something to link to (or this schedule already is one).
    val kinds = ScheduleKind.entries.filter {
        when (it) {
            ScheduleKind.DEBT -> debts.isNotEmpty() || draft.kind == it
            ScheduleKind.COLLECTION -> receivables.isNotEmpty() || draft.kind == it
            else -> true
        }
    }
    fun update(next: ScheduleDraft) {
        error = null
        onChange(next)
    }

    /**
     * Points the draft at debt [to] — or, for a collection, at receivable [to]; an empty name and amount are filled in
     * from its terms.
     */
    fun payTo(to: Wallet?, base: ScheduleDraft): ScheduleDraft {
        val paying = state.payingWallets(to?.currencyCode)
        val terms = to?.debt
        val installment = terms?.installment?.takeIf { terms.repayment == DebtRepayment.MONTHLY }
        val collection = base.kind == ScheduleKind.COLLECTION
        val repayment = terms?.repayment ?: DebtRepayment.ONE_TIME
        return base.copy(
            kind = if (collection) ScheduleKind.COLLECTION else ScheduleKind.DEBT,
            toWalletId = to?.id,
            walletId = paying.firstOrNull { it.id == base.walletId }?.id ?: paying.firstOrNull()?.id,
            categoryId = null,
            name = base.name.ifBlank {
                to?.let {
                    if (collection) DebtMath.collectionName(repayment, it.name, RecurringRepository.MAX_NAME)
                    else DebtMath.reminderName(repayment, it.name, RecurringRepository.MAX_NAME)
                }.orEmpty()
            },
            amountText = base.amountText.ifBlank { installment?.let { MoneyFormatter.formatPlain(it) }.orEmpty() },
        )
    }

    // The dialogs' look (owner round 10): sections with an icon header, filled fields with leading icons, dropdowns
    // for accounts, category and how often — the wallet editor's and onboarding's pattern.
    val walletDetail: (Wallet) -> String = { w -> "${w.type.label} · ${w.currencyCode}" }
    val walletTile: @Composable (Wallet?, androidx.compose.ui.unit.Dp) -> Unit = { w, size ->
        if (w != null) {
            WalletIdentityTile(w, size = size)
        } else {
            IconTile(Icons.Outlined.AccountBalanceWallet, size = size, content = MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(8.dp))
        }
    }
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs, bottom = Spacing.md),
    ) {
        if (kinds.size > 1) {
            IconFieldHeader(Icons.Outlined.Category, "Type")
            SegmentedToggle(
                // Four kinds don't fit their full names on a phone: "Debt payment" would be cut off (owner round 10).
                options = kinds.map { it to if (kinds.size > 3) it.shortLabel else it.label },
                selected = draft.kind,
                onSelect = { picked ->
                    if (picked == draft.kind) return@SegmentedToggle
                    // Each kind starts clean: a name, amount, category or debt from another kind never carries over
                    // (a debt payment then fills its name and amount from the chosen loan's terms).
                    val fresh = draft.copy(kind = picked, name = "", amountText = "", toWalletId = null, categoryId = null)
                    update(
                        when (picked) {
                            ScheduleKind.DEBT -> payTo(debts.firstOrNull(), fresh)
                            ScheduleKind.COLLECTION -> payTo(receivables.firstOrNull(), fresh)
                            else -> fresh
                        }
                    )
                },
                compact = true,
            )
            Spacer(Modifier.height(Spacing.lg))
        }

        IconFieldHeader(Icons.Outlined.EditNote, "Details")
        TextInput(
            value = draft.name,
            onValueChange = { update(draft.copy(name = it)) },
            label = "Name",
            placeholder = when (draft.kind) {
                ScheduleKind.INCOME -> "e.g. Salary"
                ScheduleKind.DEBT -> "e.g. Monthly Payment - Car loan"
                ScheduleKind.COLLECTION -> "e.g. Monthly Collection - Loan to Ana"
                ScheduleKind.BILL -> "e.g. Internet bill"
            },
            maxLength = RecurringRepository.MAX_NAME,
            leadingIcon = Icons.AutoMirrored.Outlined.Label,
            filled = true,
        )
        Spacer(Modifier.height(Spacing.sm))
        val terms = debt?.debt
        val amortization = terms?.installment?.takeIf { isDebt && terms.repayment == DebtRepayment.MONTHLY }
        MoneyField(
            value = draft.amountText,
            onValueChange = { update(draft.copy(amountText = it)) },
            label = when {
                !isDebt -> "Amount"
                isCollection && amortization != null -> "Monthly instalment"
                isCollection -> "Instalment"
                amortization != null -> "Monthly amortization"
                else -> "Payment"
            },
            currencyCode = currency,
            supporting = amortization?.let { "From ${debt.name}’s terms: ${MoneyFormatter.format(it)} a month." },
            leadingIcon = Icons.Outlined.Payments,
            filled = true,
        )

        Spacer(Modifier.height(Spacing.lg))
        if (isDebt) {
            IconFieldHeader(Icons.Outlined.SwapHoriz, "Accounts")
            val targets = if (isCollection) receivables else debts
            FilledDropdown(
                label = if (isCollection) "Collect from" else "Pay to",
                options = targets,
                selected = debt?.takeIf { it in targets },
                onSelect = { update(payTo(it, draft)) },
                text = { it.name },
                detail = walletDetail,
                leading = walletTile,
                placeholder = if (isCollection) "Choose a receivable" else "Choose a card or loan",
            )
            Spacer(Modifier.height(Spacing.sm))
            if (sources.isEmpty()) {
                Text(
                    if (isCollection) "Add a cash, bank or e-wallet account in $currency to receive this into."
                    else "Add a cash, bank or e-wallet account in $currency to pay this from.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                FilledDropdown(
                    label = if (isCollection) "Received into" else "Paid from",
                    options = sources,
                    selected = wallet,
                    onSelect = { update(draft.copy(walletId = it.id)) },
                    text = { it.name },
                    detail = walletDetail,
                    leading = walletTile,
                    placeholder = "Choose an account",
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            InfoNote(
                if (isCollection) {
                    "When you mark an instalment received, you can split it into principal and interest. Interest is " +
                        "recorded as income under “${DebtMath.INTEREST_INCOME_CATEGORY}”; the rest moves into your account as a " +
                        "transfer, so it lowers what you’re owed without counting as income twice."
                } else {
                    "When you mark a payment paid, you split it into principal and interest from your statement. Interest is " +
                        "recorded as spending under “${DebtMath.INTEREST_CATEGORY}”; the rest moves to the debt as a transfer, " +
                        "so it lowers what you owe without counting as spending twice."
                },
            )
        } else {
            IconFieldHeader(Icons.Outlined.AccountBalanceWallet, "Account and category")
            FilledDropdown(
                label = if (draft.kind == ScheduleKind.INCOME) "Into" else "Paid from",
                options = state.wallets,
                selected = wallet,
                onSelect = { update(draft.copy(walletId = it.id)) },
                text = { it.name },
                detail = walletDetail,
                leading = walletTile,
                placeholder = "Choose an account",
            )
            Spacer(Modifier.height(Spacing.sm))
            // Required: every scheduled bill and income lands in a category, so budgets and Bills due see it.
            if (categories.isEmpty()) {
                Text(
                    if (draft.kind == ScheduleKind.INCOME) "Add an income category first (Budget → Categories)."
                    else "Add an expense category first (Budget → Categories).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                val type = if (draft.kind == ScheduleKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
                FilledDropdown(
                    label = "Category",
                    options = categories,
                    selected = categories.firstOrNull { it.id == draft.categoryId },
                    onSelect = { update(draft.copy(categoryId = it.id)) },
                    text = { it.name },
                    leading = { category, size -> CategoryTile(category = category, type = type, size = size) },
                    placeholder = "Choose a category",
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        IconFieldHeader(Icons.Outlined.EventRepeat, "Repeats")
        SegmentedToggle(
            options = Frequency.entries.map { it to frequencyLabel(it, 1) },
            selected = draft.frequency,
            onSelect = { update(draft.copy(frequency = it)) },
            compact = true,
        )
        Spacer(Modifier.height(Spacing.sm))
        FilledDropdown(
            label = "How often",
            options = listOf(1, 2, 3, 6),
            selected = draft.interval.takeIf { it in listOf(1, 2, 3, 6) } ?: 1,
            onSelect = { update(draft.copy(interval = it)) },
            text = { n -> frequencyLabel(draft.frequency, n).let { if (n == 1) "Every ${unitOf(draft.frequency)}" else it } },
            leading = { _, size ->
                IconTile(Icons.Outlined.Repeat, size = size, content = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
            },
        )
        Spacer(Modifier.height(Spacing.sm))
        DateField(label = "First date", date = draft.startDate, onDate = { update(draft.copy(startDate = it ?: draft.startDate)) }, clearable = false, filled = true)
        Spacer(Modifier.height(Spacing.sm))
        DateField(label = "Ends (optional)", date = draft.endDate, onDate = { update(draft.copy(endDate = it)) }, emptyText = "Never", filled = true)
        if (!isNew) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Changing the first date or how often it repeats restarts the schedule from the new first date. " +
                    "Transactions already recorded stay as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    // Pinned save, clear of the keyboard.
    Hairline()
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
        LucentoPrimaryButton(
            text = if (isNew) "Save schedule" else "Save changes",
            onClick = {
                val w = wallet
                val problem = when {
                    w == null -> when {
                        isCollection -> "Choose the account it’s received into."
                        isDebt -> "Choose the account it’s paid from."
                        else -> "Choose an account."
                    }
                    isCollection && debt == null -> "Choose who it’s collected from."
                    isDebt && debt == null -> "Choose the card or loan it pays."
                    draft.name.isBlank() -> "Give it a name."
                    !isDebt && categories.none { it.id == draft.categoryId } -> "Choose a category."
                    else -> null
                }
                if (problem != null || w == null) {
                    haptics.reject()
                    error = problem
                    return@LucentoPrimaryButton
                }
                val (money, amountError) = parsePositive(draft.amountText, w.currencyCode, separator)
                if (money == null) {
                    haptics.reject()
                    error = amountError?.message(w.currencyCode, separator)
                    return@LucentoPrimaryButton
                }
                onSave(
                    RecurringRepository.Input(
                        id = draft.id,
                        name = draft.name,
                        type = if (draft.kind == ScheduleKind.INCOME || isCollection) TransactionType.INCOME else TransactionType.EXPENSE,
                        amount = money,
                        walletId = w.id,
                        categoryId = draft.categoryId,
                        frequency = draft.frequency,
                        interval = draft.interval,
                        startDate = draft.startDate,
                        endDate = draft.endDate,
                        toWalletId = debt?.id.takeIf { isDebt },
                    ),
                ) { result ->
                    error = when (result) {
                        is RecurringRepository.SaveResult.Saved -> {
                            onSaved()
                            null
                        }
                        RecurringRepository.SaveResult.BlankName -> "Give it a name."
                        RecurringRepository.SaveResult.AmountNotPositive -> "The amount must be greater than zero."
                        RecurringRepository.SaveResult.EndBeforeStart -> "The end date is before the first date."
                        RecurringRepository.SaveResult.WalletNotFound -> "That account no longer exists."
                        RecurringRepository.SaveResult.CurrencyMismatch -> "The amount must be in the account’s currency."
                        RecurringRepository.SaveResult.CategoryMismatch -> "That category doesn’t match the type."
                        RecurringRepository.SaveResult.CategoryRequired -> "Choose a category."
                        RecurringRepository.SaveResult.InvalidDebt ->
                            if (isCollection) "Collect from a receivable into a money account in the same currency."
                            else "Pay a card or loan from a money account in the same currency."
                        RecurringRepository.SaveResult.InvalidInterval,
                        RecurringRepository.SaveResult.InvalidType,
                        RecurringRepository.SaveResult.NotFound -> "This schedule couldn’t be saved."
                        RecurringRepository.SaveResult.NotPermitted -> "Only the Shared Space’s owner can change this."
                    }
                    if (error != null) haptics.reject()
                }
            },
        )
    }
}
