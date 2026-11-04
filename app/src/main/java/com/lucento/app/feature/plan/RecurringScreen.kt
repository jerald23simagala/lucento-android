package com.lucento.app.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.core.designsystem.component.EmptyIllustration
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.MascotMessage
import com.lucento.app.core.designsystem.component.FieldHeader
import com.lucento.app.core.designsystem.component.FlowStatTiles
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.Hairline
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
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.component.ToneSectionHeader
import com.lucento.app.core.designsystem.component.WalletIdentityTile
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.scheduledFlowColor
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.domain.CreditCardMath
import com.lucento.app.domain.FlowTotals
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.RecurringSchedule
import com.lucento.app.domain.ScheduleOutlook
import com.lucento.app.domain.ScheduledFlow
import com.lucento.app.domain.ScheduledItem
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.flow
import com.lucento.app.feature.transactions.netFlowLabel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The page's two ways in: dated occurrences to act on, or each schedule once to manage. */
private enum class ScheduledView(val label: String) { AGENDA("Upcoming"), SCHEDULES("All schedules") }

/** The agenda's date range. [THIS_MONTH] is the default: overdue items, then today to the month's end. */
internal enum class ScheduledRange(val label: String) {
    THIS_MONTH("This month"),
    NEXT_30_DAYS("Next 30 days"),
    NEXT_3_MONTHS("Next 3 months"),
    CUSTOM("Custom range"),
}

/** [from]..[to] for [range]; a custom range is ordered and at most [ScheduleOutlook.MAX_RANGE_DAYS] long. */
internal fun scheduledRange(range: ScheduledRange, today: LocalDate, customFrom: LocalDate?, customTo: LocalDate?): Pair<LocalDate, LocalDate> =
    when (range) {
        ScheduledRange.THIS_MONTH -> today to YearMonth.from(today).atEndOfMonth()
        ScheduledRange.NEXT_30_DAYS -> today to today.plusDays(29)
        ScheduledRange.NEXT_3_MONTHS -> today to YearMonth.from(today).plusMonths(2).atEndOfMonth()
        ScheduledRange.CUSTOM -> {
            val from = customFrom ?: today
            val to = (customTo ?: from).let { if (it.isBefore(from)) from else it }
            from to minOf(to, from.plusDays(ScheduleOutlook.MAX_RANGE_DAYS - 1))
        }
    }

/** The agenda filters, as on Transactions: what kind, which wallet, which category. Null is "all". */
internal data class ScheduledFilter(val flow: ScheduledFlow? = null, val walletId: Long? = null, val categoryId: Long? = null) {
    val isActive: Boolean get() = flow != null || walletId != null || categoryId != null

    fun matches(item: ScheduledItem): Boolean = when (item) {
        is ScheduledItem.Occurrence -> matches(item.schedule)
        is ScheduledItem.CardPayment -> (flow == null || flow == ScheduledFlow.TRANSFER) &&
            (walletId == null || walletId == item.due.wallet.id) && categoryId == null
    }

    fun matches(schedule: RecurringSchedule): Boolean =
        (flow == null || flow == schedule.flow) &&
            (walletId == null || walletId == schedule.walletId || walletId == schedule.toWalletId) &&
            (categoryId == null || categoryId == schedule.categoryId)
}

/**
 * Scheduled transactions: plans you confirm. "Mark paid" records a real transaction (once, never twice), "Skip" moves to
 * the next date; nothing is posted automatically. The page opens on an agenda of every dated occurrence (overdue first,
 * then today to the month's end by default) that the filters and a From–To range reshape, summed up under the filters as
 * on Transactions — income, expenses and their net, with transfers apart. "All schedules" lists each plan once.
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
    /** Opens a card's page from its payment reminder (where Payment is). */
    onOpenWallet: (Long) -> Unit = {},
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(ScheduledView.AGENDA) }
    var range by rememberSaveable { mutableStateOf(ScheduledRange.THIS_MONTH) }
    var customFrom by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var customTo by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var flowFilter by rememberSaveable { mutableStateOf<ScheduledFlow?>(null) }
    var walletFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var actingOn by rememberSaveable { mutableStateOf(openScheduleId) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }

    val filter = ScheduledFilter(flowFilter, walletFilter, categoryFilter)
    val today = state.today
    val code = state.primaryCurrency
    val active = remember(state.schedules) { state.schedules.filter { !it.paused && !it.isFinished } }
    val (from, to) = scheduledRange(range, today, customFrom, customTo)
    // Each card's payment for its current statement (later statements aren't known yet), when due by the range's end.
    val cardDues = remember(state.wallets, state.transactions, today, to) {
        CreditCardMath.dues(state.wallets, state.transactions, today, maxOf(to, YearMonth.from(today).atEndOfMonth()))
    }
    val agenda = remember(active, cardDues, today, from, to, filter) {
        ScheduleOutlook.items(active, cardDues, today, from, to).filter(filter::matches)
    }
    val hasAnything = state.schedules.isNotEmpty() || cardDues.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "bar") {
                LucentoTopBar(title = "Scheduled", onBack = onBack, actions = {
                    if (state.schedules.isNotEmpty() && state.wallets.isNotEmpty()) {
                        com.lucento.app.core.designsystem.component.AddPillButton("Add", onClick = onAddSchedule, contentDescription = "Add scheduled transaction")
                    }
                })
            }
            if (!state.loading && !hasAnything) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing scheduled yet",
                        body = if (state.wallets.isEmpty()) {
                            "Add a wallet first — every scheduled transaction is paid from (or into) one."
                        } else {
                            "Add rent, subscriptions, loan payments or your salary. Lucento reminds you here and on Home, and you confirm each one."
                        },
                        illustration = { EmptyIllustration(LucentoAnimation.EmptyRecurring) },
                        action = if (state.wallets.isEmpty()) {
                            null
                        } else {
                            { LucentoPrimaryButton(text = "Schedule a transaction", onClick = onAddSchedule) }
                        },
                    )
                }
            } else if (hasAnything) {
                item(key = "view") {
                    val colors = MaterialTheme.colorScheme
                    SegmentedToggle(
                        options = ScheduledView.entries.map { it to it.label },
                        selected = view,
                        onSelect = { view = it },
                        compact = true,
                        selectedTone = { TonePair(colors.primary, colors.onPrimary) },
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                item(key = "filters") {
                    ScheduledFilters(
                        state = state,
                        filter = filter,
                        showRange = view == ScheduledView.AGENDA,
                        range = range,
                        from = from,
                        to = to,
                        onFlow = { flowFilter = it },
                        onWallet = { walletFilter = it },
                        onCategory = { categoryFilter = it },
                        onRange = { range = it },
                        onCustomRange = { start, end ->
                            customFrom = start
                            customTo = end
                            range = ScheduledRange.CUSTOM
                        },
                        onClear = {
                            flowFilter = null
                            walletFilter = null
                            categoryFilter = null
                            range = ScheduledRange.THIS_MONTH
                        },
                    )
                }
                when (view) {
                    ScheduledView.AGENDA -> agendaItems(
                        agenda = agenda,
                        state = state,
                        range = range,
                        from = from,
                        to = to,
                        currency = code,
                        onOpen = { item ->
                            when (item) {
                                is ScheduledItem.Occurrence -> actingOn = item.schedule.id
                                is ScheduledItem.CardPayment -> onOpenWallet(item.due.wallet.id)
                            }
                        },
                    )
                    ScheduledView.SCHEDULES -> scheduleItems(state, filter, onOpen = { actingOn = it })
                }
                item(key = "rule") {
                    InfoNote(
                        "Scheduled items aren’t transactions until you mark them paid, so balances only change when money really moves. " +
                            "Transfers pay a card or loan from your own money: they lower what you owe, aren’t spending, and stay out of net flow.",
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
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

/** The word for each flow, as on the filters, the hero and the chart legend. */
internal fun ScheduledFlow.label(): String = when (this) {
    ScheduledFlow.INCOME -> "Income"
    ScheduledFlow.EXPENSE -> "Expenses"
    ScheduledFlow.TRANSFER -> "Transfers"
}

/** An item's signed amount: income comes in (+), expenses and transfers go out (−). */
private fun signed(flow: ScheduledFlow, amount: Money): Money = if (flow == ScheduledFlow.INCOME) amount else -amount

private fun monthName(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

private val shortDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * The Transactions-style filters: kind as a segmented control, then pills for the date range (the agenda only), the
 * wallet and the category, and Clear once anything is set. The range menu has quick ranges and a From–To picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledFilters(
    state: PlanUiState,
    filter: ScheduledFilter,
    showRange: Boolean,
    range: ScheduledRange,
    from: LocalDate,
    to: LocalDate,
    onFlow: (ScheduledFlow?) -> Unit,
    onWallet: (Long?) -> Unit,
    onCategory: (Long?) -> Unit,
    onRange: (ScheduledRange) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
    onClear: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = Spacing.sm)) {
        val kinds = listOf<Pair<ScheduledFlow?, String>>(null to "All") + ScheduledFlow.entries.map { it to it.label() }
        // Large text: four equal segments can't hold "Expenses" and "Transfers"; pills size to their words and scroll.
        if (LocalDensity.current.fontScale >= 1.3f) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                kinds.forEach { (flow, label) -> ChoicePill(label = label, selected = flow == filter.flow, onClick = { onFlow(flow) }) }
            }
        } else {
            SegmentedToggle(
                options = kinds,
                selected = filter.flow,
                onSelect = onFlow,
                compact = true,
                selectedTone = { TonePair(colors.primary, colors.onPrimary) },
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (showRange) {
                FilterMenuPill(
                    label = if (range == ScheduledRange.CUSTOM) "${from.format(shortDate)} – ${to.format(shortDate)}" else range.label,
                    active = range != ScheduledRange.THIS_MONTH,
                    icon = Icons.Outlined.CalendarMonth,
                    tonal = true,
                    options = ScheduledRange.entries.map { r ->
                        (if (r == ScheduledRange.CUSTOM) "From – To…" else r.label) to {
                            if (r == ScheduledRange.CUSTOM) picking = true else onRange(r)
                        }
                    },
                )
            }
            val wallets = state.wallets
            FilterMenuPill(
                label = filter.walletId?.let { state.walletsById[it]?.name } ?: "All wallets",
                active = filter.walletId != null,
                icon = filter.walletId?.let { state.walletsById[it]?.type?.icon } ?: Icons.Outlined.AccountBalanceWallet,
                options = listOf<Pair<String, () -> Unit>>("All wallets" to { onWallet(null) }) + wallets.map { w -> w.name to { onWallet(w.id) } },
            )
            // Only categories that some schedule uses: the menu answers "which plans are groceries?", not every category.
            val used = state.schedules.mapNotNull { it.categoryId }.toSet()
            val categories = state.categories.filter { it.id in used }.sortedBy { it.name }
            if (categories.isNotEmpty()) {
                FilterMenuPill(
                    label = filter.categoryId?.let { state.categoriesById[it]?.name } ?: "All categories",
                    active = filter.categoryId != null,
                    icon = Icons.Outlined.Category,
                    options = listOf<Pair<String, () -> Unit>>("All categories" to { onCategory(null) }) + categories.map { c -> c.name to { onCategory(c.id) } },
                )
            }
            if (filter.isActive || range != ScheduledRange.THIS_MONTH) LucentoTextButton(text = "Clear", onClick = onClear)
        }
    }
    if (picking) {
        val zone = ZoneOffset.UTC
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from.atStartOfDay(zone).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = to.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                        val end = pickerState.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: start
                        if (start != null && end != null) onCustomRange(start, end)
                        picking = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("Scheduled between", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                showModeToggle = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A filter pill that opens a short menu (the Transactions page's filter pills). */
@Composable
private fun FilterMenuPill(
    label: String,
    active: Boolean,
    options: List<Pair<String, () -> Unit>>,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tonal: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ChoicePill(
            label = label,
            selected = active,
            leadingIcon = icon,
            trailingIcon = Icons.Filled.ArrowDropDown,
            role = Role.DropdownList,
            onClick = { expanded = true },
            tonal = tonal,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    expanded = false
                    action()
                })
            }
        }
    }
}

/**
 * The agenda: the range's summary (as on Transactions), then overdue items, then one card per month with a row for every
 * dated occurrence (and card payment). Empty ranges say so with the mascot.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.agendaItems(
    agenda: List<ScheduledItem>,
    state: PlanUiState,
    range: ScheduledRange,
    from: LocalDate,
    to: LocalDate,
    currency: String,
    onOpen: (ScheduledItem) -> Unit,
) {
    val today = state.today
    item(key = "range-summary") { AgendaSummary(ScheduleOutlook.totals(agenda, currency), agenda, range, from, to, currency) }
    if (agenda.isEmpty()) {
        item(key = "agenda-empty") {
            LucentoCard(Modifier.fillMaxWidth().padding(top = Spacing.sm), contentPadding = Spacing.md) {
                MascotMessage(
                    if (range == ScheduledRange.THIS_MONTH) "Nothing else due this month. New occurrences show up here before they’re due."
                    else "Nothing scheduled between ${from.format(shortDate)} and ${to.format(shortDate)}.",
                )
            }
        }
        return
    }
    val overdue = agenda.filter { it.overdue }
    val ahead = agenda.filterNot { it.overdue }.groupBy { YearMonth.from(it.date) }
    if (overdue.isNotEmpty()) {
        item(key = "overdue-head") {
            AgendaHeader("Overdue", if (overdue.size == 1) "1 item · still owed" else "${overdue.size} items · still owed", null, alert = true)
        }
        item(key = "overdue") { AgendaCard(overdue, state, today, onOpen) }
    }
    ahead.forEach { (month, items) ->
        item(key = "head-$month") {
            val totals = ScheduleOutlook.totals(items, currency)
            AgendaHeader(
                monthName(month) + if (month.year != today.year) " ${month.year}" else "",
                if (items.size == 1) "1 item" else "${items.size} items",
                // A month of transfers only has no net flow to show.
                totals.net.takeIf { !totals.income.isZero || !totals.expenses.isZero },
            )
        }
        item(key = "month-$month") { AgendaCard(items, state, today, onOpen) }
    }
}

/** A section of the agenda, styled like Transactions' day headers: the title, what it holds, and its net flow. */
@Composable
private fun AgendaHeader(title: String, caption: String, net: Money?, alert: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(top = Spacing.md, bottom = Spacing.sm, start = Spacing.xxs, end = Spacing.xxs)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (alert) LucentoTheme.extendedColors.negative else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (net != null) {
            Spacer(Modifier.width(Spacing.xs))
            AmountText(
                money = net,
                style = AmountStyles.small.copy(fontWeight = FontWeight.SemiBold),
                showPlus = true,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What the chosen range holds, in the Transactions summary's cards: income, expenses and their net flow. Transfers — card
 * and loan payments from the user's own money — sit on their own line beneath: they aren't spending and never change the
 * net. Then how many items, over which dates. Main currency only; others are named, never added in.
 */
@Composable
private fun AgendaSummary(totals: FlowTotals, agenda: List<ScheduledItem>, range: ScheduledRange, from: LocalDate, to: LocalDate, currency: String) {
    val others = ScheduleOutlook.otherCurrencies(agenda, currency)
    Column(Modifier.padding(top = Spacing.sm)) {
        FlowStatTiles(income = totals.income, expenses = totals.expenses, net = totals.net, netLabel = netFlowLabel(totals.net))
        if (!totals.transfers.isZero) {
            Spacer(Modifier.height(Spacing.sm))
            TransfersLine(totals.transfers)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            buildString {
                append(if (totals.count == 1) "1 item" else "${totals.count} items")
                append(" · ")
                append(if (range == ScheduledRange.THIS_MONTH) "to ${to.format(shortDate)}" else "${from.format(shortDate)} – ${to.format(shortDate)}")
                if (totals.overdueCount > 0) append(" · ${totals.overdueCount} overdue included")
                if (others.isNotEmpty()) append(" · also in ").append(others.joinToString())
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Scheduled transfers on their own card: neutral ink, and said plainly that they stay out of net flow. */
@Composable
private fun TransfersLine(transfers: Money) {
    val shown = -transfers
    LucentoCard(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Transfers ${MoneyFormatter.formatSigned(shown)}, card and loan payments from your own money, not in net flow"
        },
        contentPadding = Spacing.md,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                Icons.Outlined.SwapHoriz,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 30.dp,
                shape = RoundedCornerShape(9.dp),
            )
            Spacer(Modifier.width(Spacing.sm + Spacing.xxs))
            Column(Modifier.weight(1f)) {
                Text("Transfers", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "Card and loan payments · not in net flow",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            MoneyText(
                money = shown,
                style = AmountStyles.row.copy(fontWeight = FontWeight.Bold),
                color = scheduledFlowColor(ScheduledFlow.TRANSFER),
                fractionScale = 1f,
                minScale = 0.6f,
            )
        }
    }
}

@Composable
private fun AgendaCard(items: List<ScheduledItem>, state: PlanUiState, today: LocalDate, onOpen: (ScheduledItem) -> Unit) {
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        items.forEachIndexed { index, item ->
            AgendaRow(item, state, today, onClick = { onOpen(item) })
            if (index < items.lastIndex) Hairline(strong = true)
        }
    }
}

/**
 * One dated occurrence: a calendar badge (red when overdue, blue today), the item's tile when there's room, its name
 * and what it is, then the signed amount — income green, expenses red, transfers in neutral ink — and when it's due.
 */
@Composable
private fun AgendaRow(item: ScheduledItem, state: PlanUiState, today: LocalDate, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, item.date)
    val relative = when {
        item.overdue -> "Overdue"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }
    val (title, detail) = when (item) {
        is ScheduledItem.Occurrence -> {
            val s = item.schedule
            val from = state.walletsById[s.walletId]?.name
            val debt = s.toWalletId?.let { state.walletsById[it] }
            s.name to buildString {
                // A later date of the same plan is a projection (only the next one can be marked paid); say so first,
                // where it can't be cut off.
                append(if (item.isNext) s.repeatLabel() else "Projected")
                when {
                    debt != null -> append(" · ").append(from ?: "Account").append(" → ").append(debt.name)
                    else -> {
                        s.categoryId?.let { state.categoriesById[it]?.name }?.let { append(" · ").append(it) }
                        from?.let { append(" · ").append(it) }
                    }
                }
            }
        }
        is ScheduledItem.CardPayment -> "${item.due.wallet.name} payment" to "Statement amount due"
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val roomForTile = maxWidth >= 340.dp && LocalDensity.current.fontScale < 1.3f
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClickLabel = if (item is ScheduledItem.CardPayment) "Open card" else "Open $title", onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateBadge(item.date, today, item.overdue)
            Spacer(Modifier.width(Spacing.sm + Spacing.xxs))
            if (roomForTile) {
                when (item) {
                    is ScheduledItem.Occurrence -> {
                        val debt = item.schedule.toWalletId?.let { state.walletsById[it] }
                        if (debt != null) WalletIdentityTile(debt, size = 34.dp)
                        else CategoryTile(category = item.schedule.categoryId?.let { state.categoriesById[it] }, type = item.schedule.type, size = 34.dp)
                    }
                    is ScheduledItem.CardPayment -> WalletIdentityTile(item.due.wallet, size = 34.dp)
                }
                Spacer(Modifier.width(Spacing.sm + Spacing.xxs))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                AmountText(money = signed(item.flow, item.amount), style = AmountStyles.row, showPlus = true, color = scheduledFlowColor(item.flow))
                if (item.overdue) StatusPill(relative, StatusTones.negative)
                else Text(relative, style = MaterialTheme.typography.labelMedium, color = if (days == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** A small calendar leaf: weekday over the day number; tinted red when overdue and brand blue today. */
@Composable
private fun DateBadge(date: LocalDate, today: LocalDate, overdue: Boolean) {
    val ext = LucentoTheme.extendedColors
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        overdue -> ext.negativeContainer to ext.negative
        date == today -> colors.primary to colors.onPrimary
        else -> colors.surfaceContainerHigh to colors.onSurface
    }
    Column(
        Modifier
            .width(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .padding(vertical = Spacing.xs)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.8f),
            maxLines = 1,
        )
        Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = content, maxLines = 1, textAlign = TextAlign.Center)
        if (YearMonth.from(date) != YearMonth.from(today)) {
            Text(
                date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

/** Each schedule once, filtered by kind, wallet and category, in Active, Paused and Ended groups. */
private fun androidx.compose.foundation.lazy.LazyListScope.scheduleItems(state: PlanUiState, filter: ScheduledFilter, onOpen: (Long) -> Unit) {
    val shown = state.schedules.filter(filter::matches)
    val groups = listOf(
        "Active" to shown.filter { !it.paused && !it.isFinished },
        "Paused" to shown.filter { it.paused && !it.isFinished },
        "Ended" to shown.filter { it.isFinished },
    ).filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) {
        item(key = "schedules-empty") {
            LucentoCard(Modifier.fillMaxWidth().padding(top = Spacing.sm), contentPadding = Spacing.md) {
                MascotMessage(if (filter.isActive) "No schedules match these filters." else "No schedules yet.")
            }
        }
        return
    }
    groups.forEach { (title, list) ->
        item(key = "group-$title") {
            ToneSectionHeader(
                title,
                when (title) {
                    "Active" -> StatusTones.brand
                    else -> StatusTones.neutral
                },
                caption = if (list.size == 1) "1 schedule" else "${list.size} schedules",
            )
        }
        item(key = "list-$title") {
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                list.sortedBy { it.nextDue ?: LocalDate.MAX }.forEachIndexed { index, schedule ->
                    ScheduleRow(schedule, state, onClick = { onOpen(schedule.id) })
                    if (index < list.lastIndex) Hairline(strong = true)
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(schedule: RecurringSchedule, state: PlanUiState, onClick: () -> Unit) {
    val due = schedule.nextDue
    val overdue = due != null && due.isBefore(state.today)
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
            WalletIdentityTile(debt, size = 36.dp)
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
                money = signed(schedule.flow, if (schedule.type == TransactionType.INCOME) schedule.amount else schedule.total),
                style = AmountStyles.row,
                showPlus = true,
                color = scheduledFlowColor(schedule.flow),
            )
            when {
                schedule.paused -> StatusPill("Paused", StatusTones.neutral)
                due == null -> StatusPill("Ended", StatusTones.neutral)
                overdue -> StatusPill("Overdue", StatusTones.negative)
                else -> Text(com.lucento.app.feature.transactions.shortDayLabel(due, state.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        is MoneyParser.Result.Ambiguous, is MoneyParser.Result.WrongCurrency ->
            null to AmountError.Unclear(MoneyParser.clarification(parsed, separator).orEmpty())
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
            SheetHeader(
                title = schedule.name,
                subtitle = schedule.repeatLabel() + (due?.let { " · next on ${formatDate(it)}" } ?: " · no more dates"),
                leading = {
                    if (debt != null) {
                        WalletIdentityTile(debt)
                    } else {
                        CategoryTile(category = schedule.categoryId?.let { state.categoriesById[it] }, type = schedule.type)
                    }
                },
            )
            // The plan at a glance: the signed amount in its flow's colour, and where the money goes.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (schedule.flow) {
                            ScheduledFlow.INCOME -> "Expected into ${state.walletsById[schedule.walletId]?.name ?: "your account"}"
                            ScheduledFlow.EXPENSE -> "Paid from ${state.walletsById[schedule.walletId]?.name ?: "your account"}"
                            ScheduledFlow.TRANSFER -> "Transfer from ${state.walletsById[schedule.walletId]?.name ?: "your account"} to ${debt?.name ?: "the debt"}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        money = signed(schedule.flow, if (schedule.type == TransactionType.INCOME) schedule.amount else schedule.total),
                        style = AmountStyles.large,
                        showPlus = true,
                        color = scheduledFlowColor(schedule.flow),
                    )
                }
                when {
                    schedule.paused -> StatusPill("Paused", StatusTones.neutral)
                    due == null -> StatusPill("Ended", StatusTones.neutral)
                    due.isBefore(state.today) -> StatusPill("Overdue", StatusTones.negative)
                    due == state.today -> StatusPill("Due today", StatusTones.brand)
                    else -> StatusPill(com.lucento.app.feature.transactions.shortDayLabel(due, state.today), StatusTones.neutral)
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
