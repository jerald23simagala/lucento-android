package com.lucento.app.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.core.designsystem.component.EmptyIllustration
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.FieldHeader
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.HeroCard
import com.lucento.app.core.designsystem.component.HeroPill
import com.lucento.app.core.designsystem.component.HeroTones
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoAnimation
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.component.WalletTypeTile
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtMath
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringMath
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.RecurringState
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.feature.transactions.shortDayLabel
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class RecurringTab(val label: String) { UPCOMING("Upcoming"), ACTIVE("Active"), PAUSED("Paused") }

/**
 * Recurring payments are plans you confirm: "Mark paid" records a real transaction (once, never twice),
 * "Skip" moves to the next date. Nothing is posted automatically.
 */
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    /** Opens straight into this schedule's review sheet (from Home's upcoming payments), where it can be marked paid. */
    openScheduleId: Long? = null,
    /** Opens the full-page editor for a new schedule. */
    onAddSchedule: () -> Unit = {},
    /** Opens the full-page editor for this schedule. */
    onEditSchedule: (Long) -> Unit = {},
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(RecurringTab.UPCOMING) }
    var actingOn by rememberSaveable { mutableStateOf(openScheduleId) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }

    val shown = state.schedules
        .filter { s ->
            when (tab) {
                RecurringTab.UPCOMING -> RecurringMath.state(s, state.today) == RecurringState.UPCOMING
                RecurringTab.ACTIVE -> !s.paused && !s.isFinished
                RecurringTab.PAUSED -> s.paused
            }
        }
        .sortedBy { it.nextDue ?: LocalDate.MAX }

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
                            "Add rent, subscriptions, loan payments or your salary. Lucento reminds you here and on Home, and you confirm each one."
                        },
                        illustration = { EmptyIllustration(LucentoAnimation.EmptyRecurring) },
                        action = if (state.wallets.isEmpty()) {
                            null
                        } else {
                            { LucentoPrimaryButton(text = "Add recurring payment", onClick = onAddSchedule) }
                        },
                    )
                }
            } else if (state.schedules.isNotEmpty()) {
                item(key = "hero") { DueSoonCard(state) }
                item(key = "tabs") {
                    val colors = MaterialTheme.colorScheme
                    SegmentedToggle(
                        options = RecurringTab.entries.map { it to it.label },
                        selected = tab,
                        onSelect = { tab = it },
                        compact = true,
                        selectedTone = { TonePair(colors.primary, colors.onPrimary) },
                        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm),
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
                } else {
                    item(key = "card-$tab") {
                        // One card for the list, rows split by hairlines: the Transactions and Wallets treatment.
                        LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                            shown.forEachIndexed { index, schedule ->
                                ScheduleRow(schedule, state, onClick = { actingOn = schedule.id })
                                if (index < shown.lastIndex) Hairline(strong = true)
                            }
                        }
                    }
                }
                item(key = "rule") {
                    InfoNote(
                        "Scheduled items aren’t transactions until you mark them paid, so balances only change when money really moves.",
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
        }
        if (state.schedules.isNotEmpty() && state.wallets.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = onAddSchedule,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.gutter),
            )
        }
        ConfirmationHost(confirmation = confirmation, onDismissed = { confirmation = null })
    }

    state.schedules.firstOrNull { it.id == actingOn }?.let { schedule ->
        ScheduleActionsSheet(
            schedule = schedule,
            state = state,
            onMarkPaid = { amount, interest, date, done ->
                viewModel.markPaid(schedule.id, amount, date, interest) { result ->
                    done(result)
                    // Only after the transaction(s) were recorded and the schedule advanced in one database transaction.
                    if (result is RecurringRepository.PayResult.Recorded) {
                        confirmation = Confirmation(if (schedule.type == TransactionType.INCOME) "Income recorded" else "Payment recorded")
                    }
                }
            },
            onSkip = { viewModel.skip(schedule.id) },
            onPause = { viewModel.setPaused(schedule.id, !schedule.paused) },
            onEdit = {
                actingOn = null
                onEditSchedule(schedule.id)
            },
            onDelete = {
                viewModel.deleteSchedule(schedule.id)
                actingOn = null
            },
            onDismiss = { actingOn = null },
        )
    }

}

internal fun frequencyLabel(frequency: Frequency, interval: Int): String = when (frequency) {
    Frequency.WEEKLY -> if (interval == 1) "Weekly" else "Every $interval weeks"
    Frequency.MONTHLY -> if (interval == 1) "Monthly" else "Every $interval months"
    Frequency.YEARLY -> if (interval == 1) "Yearly" else "Every $interval years"
}

/** "Monthly", or "Once" for a schedule whose only date is its first. */
private fun RecurringSchedule.repeatLabel(): String =
    if (endDate == startDate) "Once" else frequencyLabel(frequency, interval)

/**
 * The page's statement, on brand blue, for the main currency and the next 30 days: what you'll pay (left), with how
 * many payments and how many are overdue; then stacked on the right what is already overdue (tangerine) and — only
 * when income is scheduled — the income expected, otherwise when the next payment is due (sunlight).
 */
@Composable
private fun DueSoonCard(state: PlanUiState) {
    val ext = LucentoTheme.extendedColors
    val code = state.primaryCurrency
    val window = RecurringMath.upcoming(state.schedules, state.today.plusDays(RecurringMath.DASHBOARD_HORIZON_DAYS))
        .filter { (s, _) -> s.currencyCode == code }
    val outgoing = window.filter { (s, _) -> s.type == TransactionType.EXPENSE }
    val overdue = outgoing.filter { (_, date) -> date.isBefore(state.today) }
    val comingUp = outgoing.filterNot { (_, date) -> date.isBefore(state.today) }
    val income = window.filter { (s, _) -> s.type == TransactionType.INCOME }
    val dueTotal = Money.sum(code, outgoing.map { (s, _) -> s.total })
    val overdueTotal = Money.sum(code, overdue.map { (s, _) -> s.total })
    val schedulesIncome = state.schedules.any { it.type == TransactionType.INCOME && !it.paused && !it.isFinished }
    HeroCard(Modifier.padding(top = Spacing.xs)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.15f)) {
                HeroPill("To pay · next 30 days", HeroTones.main, Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.xs))
                MoneyText(money = -dueTotal, style = AmountStyles.hero, color = ext.onHero)
                Text(
                    buildString {
                        append(if (outgoing.size == 1) "1 payment" else "${outgoing.size} payments")
                        if (overdue.isNotEmpty()) append(" · ${overdue.size} overdue")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.onHeroMuted,
                    maxLines = 1,
                )
            }
            VerticalDivider(color = ext.onHeroMuted.copy(alpha = 0.35f), modifier = Modifier.padding(horizontal = Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HeroLine("Overdue", HeroTones.tangerine) {
                    MoneyText(money = -overdueTotal, style = AmountStyles.row, color = ext.onHero, fractionScale = 1f)
                }
                if (schedulesIncome) {
                    HeroLine("Income expected", HeroTones.sunlight) {
                        MoneyText(
                            money = Money.sum(code, income.map { (s, _) -> s.amount }),
                            style = AmountStyles.row,
                            color = ext.onHero,
                            showPlus = true,
                            fractionScale = 1f,
                        )
                    }
                } else {
                    // The next payment still ahead (today or later), however far off; overdue ones are counted above.
                    val nextAhead = state.schedules
                        .filter { !it.paused && it.type == TransactionType.EXPENSE }
                        .mapNotNull { s -> s.nextDue?.takeIf { !it.isBefore(state.today) } }
                        .minOrNull()
                    HeroLine("Next due", HeroTones.sunlight) {
                        Text(
                            nextAhead?.let { shortDayLabel(it, state.today) } ?: "Nothing planned",
                            style = AmountStyles.row,
                            color = ext.onHero,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroLine(label: String, tone: TonePair, value: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HeroPill(label, tone)
        Spacer(Modifier.height(Spacing.xxs))
        value()
    }
}

@Composable
private fun ScheduleRow(schedule: RecurringSchedule, state: PlanUiState, onClick: () -> Unit) {
    val due = schedule.nextDue
    val overdue = due != null && due.isBefore(state.today)
    val income = schedule.type == TransactionType.INCOME
    val debt = schedule.toWalletId?.let { state.walletsById[it] }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Open ${schedule.name}", onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs + Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (debt != null) {
            WalletTypeTile(debt.type, size = 36.dp)
        } else {
            CategoryTile(category = schedule.categoryId?.let { state.categoriesById[it] }, type = schedule.type, size = 36.dp)
        }
        Spacer(Modifier.width(Spacing.md - Spacing.xxs))
        Column(Modifier.weight(1f)) {
            Text(schedule.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(schedule.repeatLabel())
                    val from = state.walletsById[schedule.walletId]?.name
                    when {
                        debt != null -> append(" · ").append(from ?: "Account").append(" → ").append(debt.name)
                        from != null -> append(" · ").append(from)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (schedule.isDebtPayment && !schedule.interest.isZero) {
                Text(
                    "${MoneyFormatter.format(schedule.amount)} principal · ${MoneyFormatter.format(schedule.interest)} interest",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                money = if (income) schedule.amount else -schedule.total,
                style = AmountStyles.row,
                showPlus = true,
                color = if (income) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
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

/** A blank interest field is zero; anything typed must be a valid, non-negative amount. */
private fun parseInterest(text: String, currencyCode: String, separator: Char): Pair<Money?, AmountError?> {
    if (text.isBlank()) return Money.zero(currencyCode) to null
    return when (val parsed = MoneyParser.parse(text, currencyCode, separator)) {
        is MoneyParser.Result.Success -> parsed.money to null
        MoneyParser.Result.Empty -> Money.zero(currencyCode) to null
        MoneyParser.Result.Invalid -> null to AmountError.Invalid
        is MoneyParser.Result.TooManyDecimals -> null to AmountError.TooPrecise(parsed.allowed)
        MoneyParser.Result.TooLarge -> null to AmountError.TooLarge
    }
}

/**
 * A debt payment as paid: the whole payment and the part of it that was interest (from the statement). The rest is the
 * principal that goes against the debt, shown as you type.
 */
@Composable
private fun PaymentSplitFields(
    paymentText: String,
    interestText: String,
    currencyCode: String,
    debtName: String,
    onPayment: (String) -> Unit,
    onInterest: (String) -> Unit,
    error: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MoneyField(paymentText, onPayment, label = "Payment", currencyCode = currencyCode, modifier = Modifier.weight(1f))
        MoneyField(interestText, onInterest, label = "Of which interest", currencyCode = currencyCode, modifier = Modifier.weight(1f))
    }
    val separator = remember { MoneyFormatter.decimalSeparator() }
    val payment = (MoneyParser.parse(paymentText, currencyCode, separator) as? MoneyParser.Result.Success)?.money
    val interest = parseInterest(interestText, currencyCode, separator).first
    val principal = if (payment != null && interest != null) payment - interest else null
    Text(
        error ?: when {
            principal == null -> "Enter the payment and the interest your statement shows (0 if none)."
            principal.isNegative -> "The interest can’t be more than the payment."
            else -> "${MoneyFormatter.format(principal)} goes against $debtName; ${MoneyFormatter.format(interest!!)} is recorded as interest spending."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (error != null || principal?.isNegative == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleActionsSheet(
    schedule: RecurringSchedule,
    state: PlanUiState,
    onMarkPaid: (Money?, Money?, LocalDate?, (RecurringRepository.PayResult) -> Unit) -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val separator = remember { MoneyFormatter.decimalSeparator() }
    val due = schedule.nextDue
    val debt = schedule.toWalletId?.let { state.walletsById[it] }
    // For a debt payment this is the whole payment; the interest part is entered below and the rest is principal.
    var amountText by remember(schedule.id, schedule.nextIndex) { mutableStateOf(MoneyFormatter.formatPlain(schedule.total)) }
    var interestText by remember(schedule.id, schedule.nextIndex) { mutableStateOf("") }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (debt != null) {
                    WalletTypeTile(debt.type)
                } else {
                    CategoryTile(category = schedule.categoryId?.let { state.categoriesById[it] }, type = schedule.type)
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(schedule.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, modifier = Modifier.semantics { heading() })
                    Text(
                        schedule.repeatLabel() + (due?.let { " · next on ${formatDate(it)}" } ?: " · no more dates"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (due != null && !schedule.paused) {
                LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
                    FieldHeader(if (schedule.type == TransactionType.INCOME) "Record as received" else "Record as paid")
                    if (debt != null) {
                        PaymentSplitFields(
                            paymentText = amountText,
                            interestText = interestText,
                            currencyCode = schedule.currencyCode,
                            debtName = debt.name,
                            onPayment = { amountText = it; message = null },
                            onInterest = { interestText = it; message = null },
                            error = message,
                        )
                    } else {
                        MoneyField(
                            value = amountText,
                            onValueChange = { amountText = it; message = null },
                            label = "Amount",
                            currencyCode = schedule.currencyCode,
                            supporting = "Change it if this bill varies.",
                            error = message,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    DateField(label = "Date", date = paidOn, onDate = { paidOn = it ?: due }, clearable = false)
                }
                LucentoPrimaryButton(
                    text = if (schedule.type == TransactionType.INCOME) "Mark received" else "Mark paid",
                    onClick = {
                        val (money, error) = parsePositive(amountText, schedule.currencyCode, separator)
                        if (money == null) {
                            message = error?.message(schedule.currencyCode, separator)
                            return@LucentoPrimaryButton
                        }
                        val (interest, interestError) = parseInterest(interestText, schedule.currencyCode, separator)
                        if (interest == null) {
                            message = interestError?.message(schedule.currencyCode, separator)
                            return@LucentoPrimaryButton
                        }
                        if (debt != null && interest.amountMinor > money.amountMinor) {
                            message = "The interest can’t be more than the payment."
                            return@LucentoPrimaryButton
                        }
                        // A debt payment records principal (payment − interest) against the debt and the interest as spending.
                        val principal = if (debt != null) money - interest else money
                        onMarkPaid(principal, interest.takeIf { debt != null }, paidOn) { result ->
                            when (result) {
                                is RecurringRepository.PayResult.Recorded -> onDismiss()
                                RecurringRepository.PayResult.NotDue -> message = "This schedule has no more dates."
                                RecurringRepository.PayResult.NotFound -> onDismiss()
                                is RecurringRepository.PayResult.Rejected ->
                                    message = "Couldn’t record it — check that ${state.walletsById[schedule.walletId]?.name ?: "the wallet"}" +
                                        (debt?.let { " and ${it.name} still exist" } ?: " still exists") + "."
                            }
                        }
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
