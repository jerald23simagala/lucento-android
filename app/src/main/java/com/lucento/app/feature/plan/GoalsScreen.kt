package com.lucento.app.feature.plan

import com.lucento.app.core.designsystem.component.Confirmation
import com.lucento.app.core.designsystem.component.ConfirmationHost
import com.lucento.app.domain.GoalMilestone
import com.lucento.app.core.designsystem.component.LucentoAnimation
import com.lucento.app.core.designsystem.component.EmptyIllustration
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.AmountText
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.SegmentedToggle
import com.lucento.app.core.designsystem.component.ShareBar
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.domain.GoalCalculator
import com.lucento.app.domain.GoalProgress
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class GoalDraft(
    val id: Long? = null,
    val name: String = "",
    val currencyCode: String,
    val amountText: String = "",
    val targetDate: LocalDate? = null,
    val walletId: Long? = null,
)

/** Savings goals with progress from earmarked contributions (wallet balances are never changed). */
@Composable
fun GoalsScreen(onBack: () -> Unit, viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalDraft?>(null) }
    var openGoalId by remember { mutableStateOf<Long?>(null) }
    var celebration by remember { mutableStateOf<Confirmation?>(null) }
    val progress = remember(state.goals, state.contributions, state.today) {
        state.goals.map { GoalCalculator.progress(it, state.contributions, state.today) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.fabClearance),
        ) {
            item(key = "bar") { LucentoTopBar(title = "Savings goals", onBack = onBack) }
            if (!state.loading && state.goals.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No goals yet",
                        body = "An emergency fund, a trip, a new laptop — set a target and Lucento tracks progress and when you’ll get there.",
                        illustration = { EmptyIllustration(LucentoAnimation.EmptyGoals) },
                        action = { LucentoPrimaryButton(text = "Create a goal", onClick = { editing = GoalDraft(currencyCode = state.primaryCurrency) }) },
                    )
                }
            }
            items(progress, key = { "goal-${it.goal.id}" }) { p ->
                GoalCard(p, onClick = { openGoalId = p.goal.id })
                Spacer(Modifier.height(Spacing.md))
            }
            if (state.goals.isNotEmpty()) {
                item(key = "note") {
                    InfoNote("Adding money to a goal sets it aside on paper — your wallet balances and net worth don’t change, so savings are never counted twice.")
                }
            }
        }
        if (state.goals.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { editing = GoalDraft(currencyCode = state.primaryCurrency) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add goal") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.gutter),
            )
        }
        ConfirmationHost(confirmation = celebration, onDismissed = { celebration = null })
    }

    val open = progress.firstOrNull { it.goal.id == openGoalId }
    if (open != null) {
        GoalDetailSheet(
            progress = open,
            history = state.contributions.filter { it.goalId == open.goal.id },
            linkedWallet = open.goal.linkedWalletId?.let { state.walletsById[it]?.name },
            onContribute = { amount, done ->
                val name = open.goal.name
                viewModel.contribute(open.goal.id, amount, state.today, "", PlanViewModel.newKey(), done) { milestone ->
                    // Close the sheet so the celebration is visible above the goal list (sheets are their own window).
                    openGoalId = null
                    celebration = Confirmation(
                        when (milestone) {
                            GoalMilestone.HALFWAY -> "Halfway to $name"
                            GoalMilestone.REACHED -> "$name: goal reached"
                        },
                        kind = Confirmation.Kind.Milestone,
                    )
                }
            },
            onEdit = {
                openGoalId = null
                editing = GoalDraft(
                    id = open.goal.id,
                    name = open.goal.name,
                    currencyCode = open.goal.currencyCode,
                    amountText = MoneyFormatter.formatPlain(open.goal.target),
                    targetDate = open.goal.targetDate,
                    walletId = open.goal.linkedWalletId,
                )
            },
            onDelete = {
                viewModel.deleteGoal(open.goal.id)
                openGoalId = null
            },
            onDeleteContribution = viewModel::deleteContribution,
            onDismiss = { openGoalId = null },
        )
    }

    editing?.let { draft ->
        GoalEditorSheet(
            initial = draft,
            state = state,
            onSave = { d, money, done -> viewModel.saveGoal(d.id, d.name, money, d.targetDate, d.walletId, done) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GoalCard(p: GoalProgress, onClick: () -> Unit) {
    val ext = LucentoTheme.extendedColors
    LucentoCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = Icons.Outlined.Flag, container = ext.savings.container, content = ext.savings.content)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(p.goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${MoneyFormatter.format(p.saved)} of ${MoneyFormatter.format(p.goal.target)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p.reached) StatusPill("Reached", StatusTones.positive) else Text("${p.percent}%", style = AmountStyles.medium)
        }
        Spacer(Modifier.height(Spacing.md))
        ShareBar(fraction = p.fraction, color = ext.success)
        Spacer(Modifier.height(Spacing.sm))
        Text(goalOutlook(p), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun goalOutlook(p: GoalProgress): String {
    if (p.reached) return "Target reached. Well done."
    val parts = ArrayList<String>()
    p.goal.targetDate?.let { parts += "Target ${formatDate(it)}" }
    p.requiredPerMonth?.let { parts += "${MoneyFormatter.format(it)} a month to get there" }
    parts += p.projectedCompletion?.let { "At your pace: ${formatDate(it)} (estimate)" } ?: "Add money to see a projected date"
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDetailSheet(
    progress: GoalProgress,
    history: List<com.lucento.app.domain.GoalContribution>,
    linkedWallet: String?,
    onContribute: (Money, (GoalRepository.ContributionResult) -> Unit) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteContribution: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val goal = progress.goal
    val separator = remember { MoneyFormatter.decimalSeparator() }
    var amountText by remember { mutableStateOf("") }
    var withdrawing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(goal.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit goal") }
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete goal", tint = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "${MoneyFormatter.format(progress.saved)} saved of ${MoneyFormatter.format(goal.target)} · ${progress.percent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (linkedWallet != null) {
                Text("Kept in $linkedWallet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.md))
            ShareBar(fraction = progress.fraction, color = LucentoTheme.extendedColors.success)
            Spacer(Modifier.height(Spacing.md))
            Text(goalOutlook(progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(Spacing.lg))
            SegmentedToggle(
                options = listOf(false to "Add money", true to "Take out"),
                selected = withdrawing,
                onSelect = { withdrawing = it; error = null },
            )
            Spacer(Modifier.height(Spacing.md))
            MoneyField(
                value = amountText,
                onValueChange = { amountText = it; error = null },
                label = if (withdrawing) "Amount to take out" else "Amount to set aside",
                currencyCode = goal.currencyCode,
                error = error,
            )
            Spacer(Modifier.height(Spacing.md))
            LucentoPrimaryButton(
                text = if (withdrawing) "Take out" else "Add to goal",
                onClick = {
                    val (money, amountError) = parsePositive(amountText, goal.currencyCode, separator)
                    if (money == null) {
                        error = amountError?.message(goal.currencyCode, separator)
                        return@LucentoPrimaryButton
                    }
                    onContribute(if (withdrawing) -money else money) { result ->
                        when (result) {
                            GoalRepository.ContributionResult.Saved -> {
                                amountText = ""
                                error = null
                            }
                            GoalRepository.ContributionResult.ExceedsSaved -> error = "That’s more than you’ve set aside (${MoneyFormatter.format(progress.saved)})."
                            GoalRepository.ContributionResult.AmountZero -> error = "The amount must be greater than zero."
                            GoalRepository.ContributionResult.CurrencyMismatch -> error = "This goal is in ${goal.currencyCode}."
                            GoalRepository.ContributionResult.GoalNotFound -> onDismiss()
                        }
                    }
                },
            )

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader("History")
                history.forEachIndexed { index, c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDate(c.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        AmountText(
                            c.amount,
                            showPlus = true,
                            color = if (c.amount.isNegative) MaterialTheme.colorScheme.onSurface else LucentoTheme.extendedColors.positive,
                        )
                        IconButton(onClick = { onDeleteContribution(c.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove this entry", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (index < history.lastIndex) Hairline()
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete “${goal.name}”?",
            text = "The goal and its history are removed. Your wallets and transactions aren’t affected.",
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
private fun GoalEditorSheet(
    initial: GoalDraft,
    state: PlanUiState,
    onSave: (GoalDraft, Money, (GoalRepository.SaveResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val separator = remember { MoneyFormatter.decimalSeparator() }
    var draft by remember { mutableStateOf(initial) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }

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
                if (initial.id == null) "New savings goal" else "Edit goal",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            TextInput(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it); nameError = null },
                label = "Name",
                placeholder = "e.g. Emergency fund",
                error = nameError,
                maxLength = GoalRepository.MAX_NAME,
            )
            if (state.currencies.size > 1 && initial.id == null) {
                PillRow(state.currencies.map { it to it }, draft.currencyCode, onSelect = { draft = draft.copy(currencyCode = it) })
            }
            MoneyField(
                value = draft.amountText,
                onValueChange = { draft = draft.copy(amountText = it); amountError = null },
                label = "Target amount",
                currencyCode = draft.currencyCode,
                error = amountError,
            )
            DateField(label = "Target date (optional)", date = draft.targetDate, onDate = { draft = draft.copy(targetDate = it) })
            val savingsWallets = state.wallets.filter { !it.type.isLiability && it.currencyCode == draft.currencyCode }
            if (savingsWallets.isNotEmpty()) {
                SectionHeader("Kept in (optional)")
                PillRow(
                    options = listOf<Pair<Long?, String>>(null to "Not linked") + savingsWallets.map { it.id to it.name },
                    selected = draft.walletId,
                    onSelect = { draft = draft.copy(walletId = it) },
                )
            }
            LucentoPrimaryButton(
                text = if (initial.id == null) "Create goal" else "Save changes",
                onClick = {
                    if (draft.name.isBlank()) {
                        nameError = "Give this goal a name."
                        return@LucentoPrimaryButton
                    }
                    val (money, error) = parsePositive(draft.amountText, draft.currencyCode, separator)
                    if (money == null) {
                        amountError = error?.message(draft.currencyCode, separator)
                        return@LucentoPrimaryButton
                    }
                    onSave(draft, money) { result ->
                        when (result) {
                            is GoalRepository.SaveResult.Saved -> close()
                            GoalRepository.SaveResult.BlankName -> nameError = "Give this goal a name."
                            GoalRepository.SaveResult.TargetNotPositive -> amountError = "The target must be greater than zero."
                            GoalRepository.SaveResult.CurrencyLocked -> amountError = "A goal’s currency can’t change."
                            GoalRepository.SaveResult.NotFound -> close()
                        }
                    }
                },
            )
            if (initial.id != null) {
                LucentoSecondaryButton(text = "Cancel", onClick = { close() })
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
