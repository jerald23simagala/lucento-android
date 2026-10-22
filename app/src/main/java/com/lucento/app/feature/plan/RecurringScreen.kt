package com.lucento.app.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringMath
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.RecurringState
import com.lucento.app.domain.TransactionType
import com.lucento.app.feature.transactions.shortDayLabel
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class RecurringTab(val label: String) { UPCOMING("Upcoming"), ACTIVE("Active"), PAUSED("Paused") }

private data class ScheduleDraft(
    val id: Long? = null,
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
)

/**
 * Recurring payments are plans you confirm: "Mark paid" records a real transaction (once, never twice),
 * "Skip" moves to the next date. Nothing is posted automatically.
 */
@Composable
fun RecurringScreen(onBack: () -> Unit, viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(RecurringTab.UPCOMING) }
    var editing by remember { mutableStateOf<ScheduleDraft?>(null) }
    var actingOn by remember { mutableStateOf<Long?>(null) }

    val shown = state.schedules
        .filter { s ->
            when (tab) {
                RecurringTab.UPCOMING -> RecurringMath.state(s, state.today) == RecurringState.UPCOMING
                RecurringTab.ACTIVE -> !s.paused && !s.isFinished
                RecurringTab.PAUSED -> s.paused
            }
        }
        .sortedBy { it.nextDue ?: LocalDate.MAX }
    val newDraft = { ScheduleDraft(walletId = state.wallets.firstOrNull()?.id) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "bar") { LucentoTopBar(title = "Recurring payments", onBack = onBack) }
            if (!state.loading && state.schedules.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing recurring yet",
                        body = if (state.wallets.isEmpty()) {
                            "Add a wallet first — every recurring payment is paid from (or into) one."
                        } else {
                            "Add rent, subscriptions or your salary. Lucento reminds you here and on Home, and you confirm each one."
                        },
                        illustration = { LucentoMascot(size = 96.dp, mood = MascotMood.Curious) },
                        action = if (state.wallets.isEmpty()) {
                            null
                        } else {
                            { LucentoPrimaryButton(text = "Add recurring payment", onClick = { editing = newDraft() }) }
                        },
                    )
                }
            } else if (state.schedules.isNotEmpty()) {
                item(key = "tabs") {
                    SegmentedToggle(
                        options = RecurringTab.entries.map { it to it.label },
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
                if (shown.isEmpty()) {
                    item(key = "none") {
                        Text(
                            when (tab) {
                                RecurringTab.UPCOMING -> "Nothing due in the next two weeks."
                                RecurringTab.ACTIVE -> "No active schedules."
                                RecurringTab.PAUSED -> "Nothing is paused."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.lg),
                        )
                    }
                }
                itemsIndexed(shown, key = { _, s -> "rec-${s.id}" }) { index, schedule ->
                    Column {
                        ScheduleRow(schedule, state, onClick = { actingOn = schedule.id })
                        if (index < shown.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
                    }
                }
                item(key = "rule") {
                    InfoNote(
                        "Scheduled items aren’t transactions until you mark them paid, so your balances only change when money really moves.",
                        modifier = Modifier.padding(top = Spacing.lg),
                    )
                }
            }
        }
        if (state.schedules.isNotEmpty() && state.wallets.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { editing = newDraft() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.gutter),
            )
        }
    }

    state.schedules.firstOrNull { it.id == actingOn }?.let { schedule ->
        ScheduleActionsSheet(
            schedule = schedule,
            state = state,
            onMarkPaid = { amount, date, done -> viewModel.markPaid(schedule.id, amount, date, done) },
            onSkip = { viewModel.skip(schedule.id) },
            onPause = { viewModel.setPaused(schedule.id, !schedule.paused) },
            onEdit = {
                actingOn = null
                editing = ScheduleDraft(
                    id = schedule.id,
                    name = schedule.name,
                    type = schedule.type,
                    amountText = MoneyFormatter.formatPlain(schedule.amount),
                    walletId = schedule.walletId,
                    categoryId = schedule.categoryId,
                    frequency = schedule.frequency,
                    interval = schedule.interval,
                    startDate = schedule.startDate,
                    endDate = schedule.endDate,
                )
            },
            onDelete = {
                viewModel.deleteSchedule(schedule.id)
                actingOn = null
            },
            onDismiss = { actingOn = null },
        )
    }

    editing?.let { draft ->
        ScheduleEditorSheet(
            initial = draft,
            state = state,
            onSave = { input, done -> viewModel.saveSchedule(input, done) },
            onDismiss = { editing = null },
        )
    }
}

internal fun frequencyLabel(frequency: Frequency, interval: Int): String = when (frequency) {
    Frequency.WEEKLY -> if (interval == 1) "Weekly" else "Every $interval weeks"
    Frequency.MONTHLY -> if (interval == 1) "Monthly" else "Every $interval months"
    Frequency.YEARLY -> if (interval == 1) "Yearly" else "Every $interval years"
}

@Composable
private fun ScheduleRow(schedule: RecurringSchedule, state: PlanUiState, onClick: () -> Unit) {
    val due = schedule.nextDue
    val overdue = due != null && due.isBefore(state.today)
    val category = schedule.categoryId?.let { state.categoriesById[it] }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Open ${schedule.name}", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = category, type = schedule.type)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(schedule.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(frequencyLabel(schedule.frequency, schedule.interval))
                    state.walletsById[schedule.walletId]?.let { append(" · ").append(it.name) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                money = if (schedule.type == TransactionType.INCOME) schedule.amount else -schedule.amount,
                showPlus = true,
                color = if (schedule.type == TransactionType.INCOME) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
            )
            when {
                schedule.paused -> StatusPill("Paused", StatusTones.neutral)
                due == null -> StatusPill("Ended", StatusTones.neutral)
                overdue -> StatusPill("Overdue", StatusTones.negative)
                else -> Text(shortDayLabel(due, state.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleActionsSheet(
    schedule: RecurringSchedule,
    state: PlanUiState,
    onMarkPaid: (Money?, LocalDate?, (RecurringRepository.PayResult) -> Unit) -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val separator = remember { MoneyFormatter.decimalSeparator() }
    val due = schedule.nextDue
    var amountText by remember(schedule.id, schedule.nextIndex) { mutableStateOf(MoneyFormatter.formatPlain(schedule.amount)) }
    var paidOn by remember(schedule.id, schedule.nextIndex) { mutableStateOf<LocalDate?>(due) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(schedule.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                frequencyLabel(schedule.frequency, schedule.interval) +
                    (due?.let { " · next on ${formatDate(it)}" } ?: " · no more dates"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (due != null && !schedule.paused) {
                SectionHeader(if (schedule.type == TransactionType.INCOME) "Record as received" else "Record as paid")
                MoneyField(
                    value = amountText,
                    onValueChange = { amountText = it; message = null },
                    label = "Amount",
                    currencyCode = schedule.currencyCode,
                    supporting = "Change it if this bill varies.",
                    error = message,
                )
                DateField(label = "Date", date = paidOn, onDate = { paidOn = it ?: due }, clearable = false)
                LucentoPrimaryButton(
                    text = if (schedule.type == TransactionType.INCOME) "Mark received" else "Mark paid",
                    onClick = {
                        val (money, error) = parsePositive(amountText, schedule.currencyCode, separator)
                        if (money == null) {
                            message = error?.message(schedule.currencyCode, separator)
                            return@LucentoPrimaryButton
                        }
                        onMarkPaid(money, paidOn, { result ->
                            when (result) {
                                is RecurringRepository.PayResult.Recorded -> onDismiss()
                                RecurringRepository.PayResult.NotDue -> message = "This schedule has no more dates."
                                RecurringRepository.PayResult.NotFound -> onDismiss()
                                is RecurringRepository.PayResult.Rejected ->
                                    message = "Couldn’t record it — check that ${state.walletsById[schedule.walletId]?.name ?: "the wallet"} still exists."
                            }
                        })
                    },
                )
                LucentoSecondaryButton(text = "Skip this one", onClick = {
                    onSkip()
                    onDismiss()
                })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LucentoTextButton(text = if (schedule.paused) "Resume" else "Pause", onClick = {
                    onPause()
                    onDismiss()
                })
                LucentoTextButton(text = "Edit", onClick = onEdit)
                LucentoTextButton(text = "Delete", onClick = { confirmingDelete = true }, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete “${schedule.name}”?",
            text = "Future reminders stop. Transactions it already recorded stay in your history.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditorSheet(
    initial: ScheduleDraft,
    state: PlanUiState,
    onSave: (RecurringRepository.Input, (RecurringRepository.SaveResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val separator = remember { MoneyFormatter.decimalSeparator() }
    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val wallet = state.walletsById[draft.walletId]
    val kind = if (draft.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { it.kind == kind && (!it.archived || it.id == draft.categoryId) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                if (initial.id == null) "New recurring payment" else "Edit recurring payment",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            SegmentedToggle(
                options = listOf(TransactionType.EXPENSE to "Bill or expense", TransactionType.INCOME to "Income"),
                selected = draft.type,
                onSelect = { draft = draft.copy(type = it, categoryId = null) },
            )
            TextInput(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it); error = null },
                label = "Name",
                placeholder = if (draft.type == TransactionType.INCOME) "e.g. Salary" else "e.g. Internet bill",
                maxLength = RecurringRepository.MAX_NAME,
            )
            MoneyField(
                value = draft.amountText,
                onValueChange = { draft = draft.copy(amountText = it); error = null },
                label = "Amount",
                currencyCode = wallet?.currencyCode ?: state.primaryCurrency,
            )
            SectionHeader(if (draft.type == TransactionType.INCOME) "Into" else "Paid from")
            PillRow(state.wallets.map { it.id to "${it.name} · ${it.currencyCode}" }, draft.walletId, onSelect = { draft = draft.copy(walletId = it) })
            if (categories.isNotEmpty()) {
                SectionHeader("Category")
                PillRow(
                    options = listOf<Pair<Long?, String>>(null to "None") + categories.map { it.id to it.name },
                    selected = draft.categoryId,
                    onSelect = { draft = draft.copy(categoryId = it) },
                )
            }
            SectionHeader("Repeats")
            PillRow(Frequency.entries.map { it to frequencyLabel(it, 1) }, draft.frequency, onSelect = { draft = draft.copy(frequency = it) })
            PillRow(
                options = listOf(1, 2, 3, 6).map { n -> n to frequencyLabel(draft.frequency, n) },
                selected = draft.interval,
                onSelect = { draft = draft.copy(interval = it) },
            )
            DateField(label = "First date", date = draft.startDate, onDate = { draft = draft.copy(startDate = it ?: draft.startDate) }, clearable = false)
            DateField(label = "Ends (optional)", date = draft.endDate, onDate = { draft = draft.copy(endDate = it) }, emptyText = "Never")
            if (initial.id != null) {
                Text(
                    "Changing the first date or how often it repeats restarts the schedule from the new first date. " +
                        "Transactions already recorded stay as they are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            LucentoPrimaryButton(
                text = if (initial.id == null) "Save schedule" else "Save changes",
                onClick = {
                    val w = wallet
                    if (w == null) {
                        error = "Choose an account."
                        return@LucentoPrimaryButton
                    }
                    if (draft.name.isBlank()) {
                        error = "Give it a name."
                        return@LucentoPrimaryButton
                    }
                    val (money, amountError) = parsePositive(draft.amountText, w.currencyCode, separator)
                    if (money == null) {
                        error = amountError?.message(w.currencyCode, separator)
                        return@LucentoPrimaryButton
                    }
                    onSave(
                        RecurringRepository.Input(
                            id = draft.id,
                            name = draft.name,
                            type = draft.type,
                            amount = money,
                            walletId = w.id,
                            categoryId = draft.categoryId,
                            frequency = draft.frequency,
                            interval = draft.interval,
                            startDate = draft.startDate,
                            endDate = draft.endDate,
                        ),
                    ) { result ->
                        error = when (result) {
                            is RecurringRepository.SaveResult.Saved -> {
                                close()
                                null
                            }
                            RecurringRepository.SaveResult.BlankName -> "Give it a name."
                            RecurringRepository.SaveResult.AmountNotPositive -> "The amount must be greater than zero."
                            RecurringRepository.SaveResult.EndBeforeStart -> "The end date is before the first date."
                            RecurringRepository.SaveResult.WalletNotFound -> "That account no longer exists."
                            RecurringRepository.SaveResult.CurrencyMismatch -> "The amount must be in the account’s currency."
                            RecurringRepository.SaveResult.CategoryMismatch -> "That category doesn’t match the type."
                            RecurringRepository.SaveResult.InvalidInterval,
                            RecurringRepository.SaveResult.InvalidType,
                            RecurringRepository.SaveResult.NotFound -> "This schedule couldn’t be saved."
                        }
                    }
                },
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
