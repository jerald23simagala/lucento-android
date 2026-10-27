package com.lucento.app.feature.plan

import com.lucento.app.core.designsystem.component.icon
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.lucento.app.core.designsystem.component.FieldHeader
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
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
import java.time.LocalDate

/** What a schedule is: a bill (expense), income, or a payment towards a card or loan. */
internal enum class ScheduleKind(val label: String) { BILL("Bill"), INCOME("Income"), DEBT("Debt payment") }

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

/** Wallets a debt payment can come from: money accounts (never another card or loan). */
internal fun PlanUiState.payingWallets(currencyCode: String? = null): List<Wallet> =
    wallets.filter { !it.type.isLiability && (currencyCode == null || it.currencyCode == currencyCode) }

internal fun PlanUiState.debts(): List<Wallet> = wallets.filter { it.type.isLiability }

/**
 * A payment towards [debt] starting on [firstDue], filled in from its terms: "Monthly Payment - Car loan" for the
 * monthly amortization over the remaining months, or "One-time Payment - Card" for what is owed. The split into
 * principal and interest is entered when each payment is marked paid.
 */
internal fun PlanUiState.debtDraft(debt: Wallet, firstDue: LocalDate): ScheduleDraft {
    val terms = debt.debt
    val monthly = terms?.repayment == DebtRepayment.MONTHLY
    val amount = if (monthly) terms?.installment else debt.balance.takeIf { it.amountMinor > 0 }
    return ScheduleDraft(
        name = DebtMath.reminderName(terms?.repayment ?: DebtRepayment.ONE_TIME, debt.name, RecurringRepository.MAX_NAME),
        kind = ScheduleKind.DEBT,
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
) {
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
            title = if (scheduleId == null) "Scheduled transactions" else "Edit payment",
            subtitle = "Recurring payments",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
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
    val walletIcon: (Long?) -> androidx.compose.ui.graphics.vector.ImageVector? = { id -> id?.let { state.walletsById[it]?.type?.icon } }
    val debt = draft.toWalletId?.let { state.walletsById[it] }
    val isDebt = draft.kind == ScheduleKind.DEBT
    val sources = if (isDebt) state.payingWallets(debt?.currencyCode) else state.wallets
    val wallet = state.walletsById[draft.walletId]?.takeIf { it in sources }
    val currency = debt?.currencyCode ?: wallet?.currencyCode ?: state.primaryCurrency
    val kind = if (draft.kind == ScheduleKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { it.kind == kind && (!it.archived || it.id == draft.categoryId) }
    val kinds = ScheduleKind.entries.filter { it != ScheduleKind.DEBT || debts.isNotEmpty() || draft.kind == ScheduleKind.DEBT }
    fun update(next: ScheduleDraft) {
        error = null
        onChange(next)
    }

    /** Points the draft at debt [to]; an empty name and amount are filled in from its terms. */
    fun payTo(to: Wallet?, base: ScheduleDraft): ScheduleDraft {
        val paying = state.payingWallets(to?.currencyCode)
        val terms = to?.debt
        val installment = terms?.installment?.takeIf { terms.repayment == DebtRepayment.MONTHLY }
        return base.copy(
            kind = ScheduleKind.DEBT,
            toWalletId = to?.id,
            walletId = paying.firstOrNull { it.id == base.walletId }?.id ?: paying.firstOrNull()?.id,
            categoryId = null,
            name = base.name.ifBlank { to?.let { DebtMath.reminderName(terms?.repayment ?: DebtRepayment.ONE_TIME, it.name, RecurringRepository.MAX_NAME) }.orEmpty() },
            amountText = base.amountText.ifBlank { installment?.let { MoneyFormatter.formatPlain(it) }.orEmpty() },
        )
    }

    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
            .padding(top = Spacing.xs, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (kinds.size > 1) {
            SegmentedToggle(
                options = kinds.map { it to it.label },
                selected = draft.kind,
                onSelect = { picked ->
                    if (picked == draft.kind) return@SegmentedToggle
                    // Each kind starts clean: a name, amount, category or debt from another kind never carries over
                    // (a debt payment then fills its name and amount from the chosen loan's terms).
                    val fresh = draft.copy(kind = picked, name = "", amountText = "", toWalletId = null, categoryId = null)
                    update(if (picked == ScheduleKind.DEBT) payTo(debts.firstOrNull(), fresh) else fresh)
                },
                compact = true,
            )
        }
        TextInput(
            value = draft.name,
            onValueChange = { update(draft.copy(name = it)) },
            label = "Name",
            placeholder = when (draft.kind) {
                ScheduleKind.INCOME -> "e.g. Salary"
                ScheduleKind.DEBT -> "e.g. Monthly Payment - Car loan"
                ScheduleKind.BILL -> "e.g. Internet bill"
            },
            maxLength = RecurringRepository.MAX_NAME,
        )

        if (isDebt) {
            val terms = debt?.debt
            val amortization = terms?.installment?.takeIf { terms.repayment == DebtRepayment.MONTHLY }
            MoneyField(
                value = draft.amountText,
                onValueChange = { update(draft.copy(amountText = it)) },
                label = if (amortization != null) "Monthly amortization" else "Payment",
                currencyCode = currency,
                supporting = amortization?.let { "From ${debt.name}’s terms: ${MoneyFormatter.format(it)} a month." },
            )
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
                FieldHeader("Pay to")
                PillRow(debts.map { it.id to "${it.name} · ${it.currencyCode}" }, draft.toWalletId, onSelect = { id ->
                    update(payTo(state.walletsById[id], draft))
                }, icon = walletIcon)
                FieldHeader("Paid from")
                if (sources.isEmpty()) {
                    Text(
                        "Add a cash, bank or e-wallet account in $currency to pay this from.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    PillRow(sources.map { it.id to "${it.name} · ${it.currencyCode}" }, draft.walletId, onSelect = { update(draft.copy(walletId = it)) }, icon = walletIcon)
                }
            }
            InfoNote(
                "When you mark a payment paid, you split it into principal and interest from your statement. Interest is " +
                    "recorded as spending under “${DebtMath.INTEREST_CATEGORY}”; the rest moves to the debt as a transfer, " +
                    "so it lowers what you owe without counting as spending twice.",
            )
        } else {
            MoneyField(
                value = draft.amountText,
                onValueChange = { update(draft.copy(amountText = it)) },
                label = "Amount",
                currencyCode = currency,
            )
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
                FieldHeader(if (draft.kind == ScheduleKind.INCOME) "Into" else "Paid from")
                PillRow(state.wallets.map { it.id to "${it.name} · ${it.currencyCode}" }, draft.walletId, onSelect = { update(draft.copy(walletId = it)) }, icon = walletIcon)
                if (categories.isNotEmpty()) {
                    FieldHeader("Category")
                    PillRow(
                        options = listOf<Pair<Long?, String>>(null to "None") + categories.map { it.id to it.name },
                        selected = draft.categoryId,
                        onSelect = { update(draft.copy(categoryId = it)) },
                    )
                }
            }
        }

        LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
            FieldHeader("Repeats")
            PillRow(Frequency.entries.map { it to frequencyLabel(it, 1) }, draft.frequency, onSelect = { update(draft.copy(frequency = it)) })
            Spacer(Modifier.height(Spacing.xs))
            PillRow(
                options = listOf(1, 2, 3, 6).map { n -> n to frequencyLabel(draft.frequency, n) },
                selected = draft.interval,
                onSelect = { update(draft.copy(interval = it)) },
            )
            Spacer(Modifier.height(Spacing.sm))
            DateField(label = "First date", date = draft.startDate, onDate = { update(draft.copy(startDate = it ?: draft.startDate)) }, clearable = false)
            Spacer(Modifier.height(Spacing.sm))
            DateField(label = "Ends (optional)", date = draft.endDate, onDate = { update(draft.copy(endDate = it)) }, emptyText = "Never")
        }
        if (!isNew) {
            Text(
                "Changing the first date or how often it repeats restarts the schedule from the new first date. " +
                    "Transactions already recorded stay as they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }

    // Pinned save, clear of the keyboard.
    Hairline()
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
        LucentoPrimaryButton(
            text = if (isNew) "Save schedule" else "Save changes",
            onClick = {
                val w = wallet
                val problem = when {
                    w == null -> if (isDebt) "Choose the account it’s paid from." else "Choose an account."
                    isDebt && debt == null -> "Choose the card or loan it pays."
                    draft.name.isBlank() -> "Give it a name."
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
                        type = if (draft.kind == ScheduleKind.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
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
                        RecurringRepository.SaveResult.InvalidDebt -> "Pay a card or loan from a money account in the same currency."
                        RecurringRepository.SaveResult.InvalidInterval,
                        RecurringRepository.SaveResult.InvalidType,
                        RecurringRepository.SaveResult.NotFound -> "This schedule couldn’t be saved."
                    }
                    if (error != null) haptics.reject()
                }
            },
        )
    }
}
