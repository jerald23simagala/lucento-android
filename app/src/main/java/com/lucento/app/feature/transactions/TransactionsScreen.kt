package com.lucento.app.feature.transactions

import com.lucento.app.core.designsystem.component.icon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.selection.selectableGroup
import com.lucento.app.core.designsystem.component.FlowStatTiles
import com.lucento.app.core.money.MoneyFormatter
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.ChoicePill
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.EmptyIllustration
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoAnimation
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.MascotScene
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.SearchField
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.ToneChip
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.domain.PeriodTotals
import com.lucento.app.domain.RangePreset
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Width of the Edit + Delete actions a row slides aside to reveal. */
private val RowActionWidth = 72.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    /** A tap on a row: the read-only View Transaction page. */
    onViewTransaction: (Transaction) -> Unit,
    /** Edit from a row's swipe actions. */
    onEditTransaction: (Transaction) -> Unit,
    /** Deletes a confirmed transaction (both legs for a transfer). */
    onDeleteTransaction: (Transaction) -> Unit,
    onAddWallet: () -> Unit,
    /** Shown on the empty state when receipt scanning is switched on. */
    onScanReceipt: (() -> Unit)? = null,
    /** Shown on the empty state when voice entry is switched on. */
    onSpeak: (() -> Unit)? = null,
    /** Opened from the dashboard for one category and month: a back arrow, and those filters applied once. */
    onBack: (() -> Unit)? = null,
    initialCategoryId: Long? = null,
    initialMonth: java.time.YearMonth? = null,
    viewModel: TransactionsViewModel = viewModel(factory = TransactionsViewModel.Factory),
) {
    LaunchedEffect(Unit) { viewModel.applyInitial(initialCategoryId, initialMonth) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasHistory = !state.loading && state.wallets.isNotEmpty() && state.totalCount > 0
    // Local text state keeps typing responsive; the ViewModel receives every change.
    var query by rememberSaveable { mutableStateOf(state.filter.query) }
    var searching by rememberSaveable { mutableStateOf(state.filter.query.isNotEmpty()) }
    // One row at a time shows its Edit / Delete actions.
    var revealedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmingDelete by remember { mutableStateOf<Transaction?>(null) }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val clearFilters = {
        query = ""
        searching = false
        viewModel.clearFilters()
    }

    // At large text the stat cards are tall; they scroll away with the history so it keeps most of the screen.
    val summaryPinned = LocalDensity.current.fontScale < 1.3f
    val gutter = com.lucento.app.core.designsystem.component.screenGutter()

    // A different set of rows (filters, search) or a scroll closes whatever row was open.
    LaunchedEffect(state.filter) { revealedId = null }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) revealedId = null }

    Column(Modifier.fillMaxSize()) {
        // Pinned: the title, filters and totals stay put while only the history scrolls beneath them.
        Column(Modifier.padding(horizontal = gutter)) {
            LucentoTopBar(
                title = "Transactions",
                onBack = onBack,
                subtitle = state.filter.month?.let { m ->
                    listOfNotNull(
                        state.filter.categoryId?.let { id -> state.categoriesById[id]?.name },
                        m.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " ${m.year}",
                    ).joinToString(" · ")
                },
                actions = {
                    if (hasHistory) {
                        IconButton(onClick = {
                            if (searching) {
                                query = ""
                                viewModel.setQuery("")
                            }
                            searching = !searching
                        }) {
                            Icon(
                                if (searching) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searching) "Close search" else "Search transactions",
                            )
                        }
                    }
                    ProfileButton()
                },
            )
            if (hasHistory) {
                val typeOptions = listOf<Pair<TransactionType?, String>>(
                    null to "All",
                    TransactionType.INCOME to "Income",
                    TransactionType.EXPENSE to "Expenses",
                    TransactionType.TRANSFER_OUT to "Transfers",
                )
                val selectedType = state.filter.type?.let { if (it.isTransfer) TransactionType.TRANSFER_OUT else it }
                if (LocalDensity.current.fontScale >= 1.5f) {
                    // Very large text: four equal tabs can't hold "Transfers"; pills size to their words and scroll.
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        typeOptions.forEach { (type, label) ->
                            ChoicePill(label = label, selected = type == selectedType, onClick = { viewModel.setType(type) })
                        }
                    }
                } else {
                    SegmentedToggle(
                        options = typeOptions,
                        selected = selectedType,
                        onSelect = { viewModel.setType(it) },
                        compact = true,
                        selectedTone = brandTone(),
                    )
                }
                AnimatedVisibility(
                    visible = searching,
                    enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                ) {
                    SearchField(
                        value = query,
                        onValueChange = {
                            query = it.take(60)
                            viewModel.setQuery(query)
                        },
                        placeholder = "Search notes, categories, wallets",
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                FilterRow(state = state, viewModel = viewModel, onClear = clearFilters)
                Spacer(Modifier.height(Spacing.xs))
                if (summaryPinned) {
                    ShownSummary(state)
                    Spacer(Modifier.height(Spacing.sm))
                }
            }
        }
        if (hasHistory && scrolled) Hairline()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Spacing.fabClearance),
        ) {
            if (hasHistory && !summaryPinned) {
                item(key = "summary") { ShownSummary(state) }
            }
            when {
                state.loading -> Unit

                state.wallets.isEmpty() -> item(key = "no-wallets") {
                    EmptyState(
                        title = "Add a wallet first",
                        body = "Every transaction belongs to a wallet — cash, a bank account, a card. " +
                            "It only takes a moment, and stays on this device.",
                        illustration = { MascotScene(mood = MascotMood.Curious, mascotSize = 96.dp) },
                        action = { LucentoPrimaryButton(text = "Add wallet", onClick = onAddWallet) },
                    )
                }

                state.totalCount == 0 -> item(key = "no-transactions") {
                    EmptyState(
                        title = "No transactions yet",
                        body = "Start by adding your first transaction to see your financial activity here.",
                        illustration = { EmptyIllustration(LucentoAnimation.EmptyTransactions) },
                        action = {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                LucentoPrimaryButton(text = "Add transaction", onClick = onAddTransaction)
                                if (onScanReceipt != null) {
                                    LucentoSecondaryButton(text = "Scan receipt", icon = Icons.Outlined.DocumentScanner, onClick = onScanReceipt)
                                }
                                if (onSpeak != null) {
                                    LucentoSecondaryButton(text = "Speak transaction", icon = Icons.Outlined.KeyboardVoice, onClick = onSpeak)
                                }
                            }
                        },
                    )
                }

                // The page opens on this month: early in a month that can be empty while older history exists.
                state.sections.isEmpty() && state.filter.isDefault -> item(key = "no-matches") {
                    EmptyState(
                        title = "Nothing this month yet",
                        body = "Transactions you add this month appear here. Earlier ones are under Any date.",
                        action = {
                            LucentoSecondaryButton(text = "Show any date", onClick = { viewModel.setPreset(RangePreset.ALL) })
                        },
                    )
                }

                state.sections.isEmpty() -> item(key = "no-matches") {
                    EmptyState(
                        title = "Nothing matches",
                        body = "No transactions fit this search and these filters.",
                        action = {
                            LucentoSecondaryButton(text = "Clear filters", onClick = clearFilters)
                        },
                    )
                }

                else -> {
                    item(key = "swipe-tip") { SwipeTip() }
                    state.sections.forEach { section ->
                    stickyHeader(key = "day-${section.group.date.toEpochDay()}") {
                        DayHeader(section)
                    }
                    item(key = "card-${section.group.date.toEpochDay()}") {
                        // Each day is its own card; rows inside are split by a visible hairline.
                        LucentoCard(modifier = Modifier.fillMaxWidth().animateItem(), contentPadding = 0.dp) {
                            section.group.items.forEachIndexed { index, transaction ->
                                RevealableRow(
                                    transaction = transaction,
                                    revealed = revealedId == transaction.id,
                                    onRevealChange = { open -> revealedId = if (open) transaction.id else null },
                                    onOpen = { onViewTransaction(transaction) },
                                    // A balance adjustment is never edited as income or spending: delete it and adjust again.
                                    onEdit = if (transaction.isAdjustment) null else {
                                        {
                                            revealedId = null
                                            onEditTransaction(transaction)
                                        }
                                    },
                                    onDelete = { confirmingDelete = transaction },
                                ) { rowModifier, toggle ->
                                    TransactionRow(
                                        transaction = transaction,
                                        category = transaction.categoryId?.let { state.categoriesById[it] },
                                        wallet = state.walletsById[transaction.walletId],
                                        onClick = toggle,
                                        transfer = transaction.transferGroup?.let { state.transfers[it] },
                                        walletNames = state.walletNames,
                                        perspectiveOfWallet = state.filter.walletId != null,
                                        dense = true,
                                        onClickLabel = when {
                                            revealedId == transaction.id -> "Hide actions"
                                            transaction.isTransfer -> "View transfer"
                                            com.lucento.app.domain.OutsideMovement.of(transaction) != null ->
                                                "View ${com.lucento.app.domain.OutsideMovement.of(transaction)!!.label.lowercase()}"
                                            transaction.isAdjustment -> "View balance adjustment"
                                            else -> "View transaction"
                                        },
                                        modifier = rowModifier,
                                    )
                                }
                                // Inset to the row text (12dp padding + 36dp tile + 10dp gap), not under the tile.
                                if (index < section.group.items.lastIndex) Hairline(inset = 58.dp)
                            }
                        }
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    }
                }
            }
        }
    }

    confirmingDelete?.let { transaction ->
        val transfer = transaction.isTransfer
        ConfirmDialog(
            title = if (transfer) "Delete this transfer?" else "Delete this transaction?",
            text = if (transfer) {
                "Both sides of the transfer will be permanently deleted — on your other devices too when cloud sync is on — and both wallet balances " +
                    "will update. This can’t be undone."
            } else {
                "It will be permanently deleted — on your other devices too when cloud sync is on — and the wallet balance will update. " +
                    "This can’t be undone."
            },
            confirmLabel = "Delete permanently",
            destructive = true,
            onConfirm = {
                confirmingDelete = null
                revealedId = null
                onDeleteTransaction(transaction)
            },
            onDismiss = { confirmingDelete = null },
        )
    }
}

/** The brand-blue fill for the selected history filter. */
@Composable
private fun brandTone(): (Any?) -> TonePair {
    val colors = MaterialTheme.colorScheme
    val tone = TonePair(colors.primary, colors.onPrimary)
    return { tone }
}

/**
 * A row that opens the View Transaction page on tap and slides left on a leftward swipe to uncover Edit and Delete on
 * its trailing edge (a tap on an open row closes it). The actions are only composed while visible, so TalkBack never
 * lands on hidden buttons; they are also offered as custom actions.
 */
@Composable
private fun RevealableRow(
    transaction: Transaction,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    content: @Composable (Modifier, () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val reducedMotion = LucentoTheme.reducedMotion
    val maxPx = with(density) { (RowActionWidth * (if (onEdit != null) 2 else 1)).toPx() }
    val offset = remember { Animatable(if (revealed) -maxPx else 0f) }
    val scope = rememberCoroutineScope()
    val target = if (revealed) -maxPx else 0f

    LaunchedEffect(revealed) {
        if (reducedMotion) offset.snapTo(target) else offset.animateTo(target, tween(220))
    }

    val noun = when {
        transaction.isTransfer -> "transfer"
        com.lucento.app.domain.OutsideMovement.of(transaction) != null -> com.lucento.app.domain.OutsideMovement.of(transaction)!!.label.lowercase()
        transaction.isAdjustment -> "balance adjustment"
        else -> "transaction"
    }
    val surface = if (LucentoTheme.isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics {
                customActions = listOfNotNull(
                    onEdit?.let { edit -> CustomAccessibilityAction("Edit $noun") { edit(); true } },
                    CustomAccessibilityAction("Delete $noun") { onDelete(); true },
                )
            },
    ) {
        if (revealed || offset.value != 0f) {
            Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                if (onEdit != null) {
                    RowAction(
                        label = "Edit",
                        icon = Icons.Outlined.Edit,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.primary,
                        onClick = onEdit,
                    )
                }
                RowAction(
                    label = "Delete",
                    icon = Icons.Outlined.Delete,
                    container = MaterialTheme.colorScheme.error,
                    content = MaterialTheme.colorScheme.onError,
                    onClick = onDelete,
                )
            }
        }
        content(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(surface)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-maxPx, 0f)) }
                    },
                    onDragStopped = {
                        val open = offset.value < -maxPx / 2
                        if (open != revealed) {
                            haptics.tick()
                            onRevealChange(open)
                        } else {
                            offset.animateTo(target, tween(180))
                        }
                    },
                ),
        ) {
            if (revealed) onRevealChange(false) else onOpen()
        }
    }
}

/** The one hint the swipe needs: shown above the history, scrolling away with it. */
@Composable
private fun SwipeTip() {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs, start = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.SwipeLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.xs + Spacing.xxs))
        Text(
            "Tip: tap to view · swipe left to edit or delete",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowAction(label: String, icon: ImageVector, container: Color, content: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .width(RowActionWidth)
            .fillMaxHeight()
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/** A day of history: the day in words ("Today", "Monday"), its date beside it, and the day's net at the end. */
@Composable
private fun DayHeader(section: DaySection) {
    val date = section.group.date
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .heightIn(min = 40.dp)
            .padding(top = Spacing.md, bottom = Spacing.sm, start = Spacing.xxs, end = Spacing.xxs)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayTitle(date),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            dayCaption(date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        section.net.forEachIndexed { index, money ->
            Spacer(Modifier.width(if (index > 0) Spacing.sm else Spacing.xs))
            AmountText(
                money = money,
                style = AmountStyles.small.copy(fontWeight = FontWeight.SemiBold),
                showPlus = true,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Net inflow" when what came in covers what went out, "Net outflow" when it doesn't. */
internal fun netFlowLabel(net: Money): String = if (net.amountMinor >= 0L) "Net inflow" else "Net outflow"

/**
 * Income, expenses and their net for whatever is currently shown, so filters answer "how much did I spend on X?" —
 * three stat cards, then what they cover. Income is the positive tone and expenses the negative one; the net stays in
 * ink with its own sign. Every figure is signed, so colour only reinforces. At large text the net takes its own row.
 */
@Composable
private fun ShownSummary(state: TransactionsUiState) {
    val primary: PeriodTotals = state.shownTotals.firstOrNull() ?: return
    val others = state.shownTotals.drop(1)
    val scope = "${state.matchCount} ${if (state.matchCount == 1) "transaction" else "transactions"}"
    val range = state.filter.month?.let { m -> m.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " ${m.year}" }
        ?: state.filter.preset.takeIf { it != RangePreset.ALL }?.let(::rangeLabel)
    Column {
        FlowStatTiles(income = primary.income, expenses = primary.expense, net = primary.net, netLabel = netFlowLabel(primary.net))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            buildString {
                append(scope)
                if (range != null) append(" · ").append(range)
                append(" · transfers excluded from totals")
                if (others.isNotEmpty()) append(" · also in ").append(others.joinToString(", ") { it.currencyCode })
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterRow(state: TransactionsUiState, viewModel: TransactionsViewModel, onClear: () -> Unit) {
    val f = state.filter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        MenuPill(
            // A month from the dashboard shows as that month until the user picks another range.
            label = f.month?.let { m -> m.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + " ${m.year}" }
                ?: rangeLabel(f.preset),
            active = f.preset != RangePreset.ALL || f.month != null,
            options = RangePreset.entries.map { preset -> rangeLabel(preset) to { viewModel.setPreset(preset) } },
            icon = Icons.Outlined.CalendarMonth,
            tonal = true,
        )
        MenuPill(
            label = f.walletId?.let { state.walletsById[it]?.name } ?: "All wallets",
            active = f.walletId != null,
            options = listOf<Pair<String, () -> Unit>>("All wallets" to { viewModel.setWallet(null) }) +
                state.wallets.map { w -> w.name to { viewModel.setWallet(w.id) } },
            icon = f.walletId?.let { state.walletsById[it]?.type?.icon } ?: Icons.Outlined.AccountBalanceWallet,
            optionIcons = listOf(Icons.Outlined.AccountBalanceWallet) + state.wallets.map { it.type.icon },
        )
        MenuPill(
            label = f.categoryId?.let { state.categoriesById[it]?.name } ?: "All categories",
            active = f.categoryId != null,
            options = listOf<Pair<String, () -> Unit>>("All categories" to { viewModel.setCategory(null) }) +
                state.categories.filter { !it.archived }.map { c -> c.name to { viewModel.setCategory(c.id) } },
        )
        if (f.isActive) {
            LucentoTextButton(text = "Clear", onClick = onClear)
        }
    }
}

@Composable
private fun MenuPill(
    label: String,
    active: Boolean,
    options: List<Pair<String, () -> Unit>>,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    optionIcons: List<androidx.compose.ui.graphics.vector.ImageVector>? = null,
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, (text, action) ->
                val glyph = optionIcons?.getOrNull(index)
                DropdownMenuItem(
                    text = { Text(text) },
                    leadingIcon = glyph?.let { { Icon(it, contentDescription = null) } },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

private fun rangeLabel(preset: RangePreset): String = when (preset) {
    RangePreset.ALL -> "Any date"
    RangePreset.THIS_MONTH -> "This month"
    RangePreset.LAST_MONTH -> "Last month"
    RangePreset.LAST_30_DAYS -> "Last 30 days"
}
