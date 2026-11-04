package com.lucento.app.feature.guide

import com.lucento.app.core.designsystem.component.AmountsShown
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.component.StageIndicator

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.lucento.app.core.designsystem.component.CategoryTile
import com.lucento.app.core.money.MoneyParser
import com.lucento.app.domain.Category
import com.lucento.app.domain.TransactionType
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.lucento.app.core.designsystem.component.AmountTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoSecondaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.WalletIdentityTile
import com.lucento.app.core.designsystem.component.WalletTypeTile
import com.lucento.app.core.designsystem.component.rememberHaptics
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.GuideStep
import com.lucento.app.domain.GuidedSetup
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.feature.plan.NewGoalSheet
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletsViewModel

/**
 * The guided setup after onboarding, one step per page: accounts, then cards and loans, budget amounts (two at
 * least), one goal, a first few transactions, and the welcome. Everything is saved as the user goes, through the
 * same sheets and repositories as the rest of the app, and the story resumes where it was left.
 */
@Composable
fun GuideScreen(
    onRecordTransactions: () -> Unit,
    onFinished: () -> Unit,
    viewModel: GuideViewModel = viewModel(factory = GuideViewModel.Factory),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) = AmountsShown {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Same as onboarding: one count each time a page comes on screen, keyed on the page.
    if (!state.loading) LaunchedEffect(state.step) { viewModel.onStepShown(state.step) }
    var editingWallet by remember { mutableStateOf<WalletDraft?>(null) }
    var addingGoal by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    // The budget step's fields, kept here so its button can sit in the bottom bar with the other steps' buttons.
    val budgetTexts = remember { mutableStateMapOf<Long, String>() }
    var invalidBudgets by remember { mutableStateOf(emptySet<Long>()) }
    val separator = remember { MoneyFormatter.decimalSeparator() }
    fun budgetText(categoryId: Long): String =
        budgetTexts[categoryId] ?: state.allocation(categoryId)?.let(MoneyFormatter::formatPlain).orEmpty()

    // Back walks the story backwards; on the first page it stays (the setup resumes here on the next launch anyway).
    BackHandler(enabled = !state.loading) { viewModel.back() }

    if (state.loading) return@AmountsShown
    val steps = state.steps

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter).imePadding()) {
        StageIndicator(
            com.lucento.app.domain.SetupProgress(guideStage(state.step), state.furthestStage, guideStageProgress(state.step, steps)),
            modifier = Modifier.padding(top = Spacing.md),
        )
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "guide-step",
            modifier = Modifier.weight(1f),
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    GuideStep.WALLETS -> WalletsStep(
                        title = "Add your accounts",
                        body = "Start with the cash, bank accounts and e-wallets you use, with today’s balance. No bank connection needed.",
                        wallets = state.assets,
                        addLabel = if (state.assets.isEmpty()) "Add your first account" else "Add another account",
                        onAdd = { editingWallet = WalletDraft(type = state.firstAssetType, currencyCode = state.currency, isDefault = state.assets.isEmpty()) },
                    )
                    GuideStep.DEBTS -> WalletsStep(
                        title = "Any cards or loans?",
                        body = "Add credit cards and loans so your net worth is honest. Skip this if you have none — you can add them later.",
                        wallets = state.debts,
                        addLabel = "Add a card or loan",
                        onAdd = { editingWallet = WalletDraft(type = state.firstDebtType, currencyCode = state.currency) },
                    )
                    GuideStep.BUDGETS -> BudgetsStep(
                        state = state,
                        text = ::budgetText,
                        invalid = invalidBudgets,
                        separator = separator,
                        onChange = { id, value ->
                            budgetTexts[id] = value
                            invalidBudgets = invalidBudgets - id
                        },
                    )
                    GuideStep.GOAL -> GoalStep(state, onAdd = { addingGoal = true })
                    GuideStep.TRANSACTIONS -> TransactionsStep(state, onRecordTransactions)
                    GuideStep.WELCOME -> WelcomeStep(state)
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        run {
            if (state.step == GuideStep.BUDGETS) {
                val required = GuidedSetup.budgetsRequired(state.expenseCategories.size)
                val filled = state.expenseCategories.count { budgetText(it.id).isNotBlank() }
                LucentoPrimaryButton(
                    text = if (filled >= required) "Save and continue" else "Set ${required - filled} more to continue",
                    enabled = filled >= required,
                    onClick = {
                        viewModel.saveBudgets(state.expenseCategories.associate { it.id to budgetText(it.id) }, separator) { invalidBudgets = it }
                    },
                )
            } else if (state.step == GuideStep.WELCOME) {
                LucentoPrimaryButton(text = "Go to Home", onClick = {
                    haptics.confirm()
                    viewModel.finish(onFinished)
                })
            } else {
                LucentoPrimaryButton(text = "Continue", onClick = viewModel::next, enabled = state.canContinue)
                if (GuidedSetup.isSkippable(state.step)) {
                    LucentoTextButton(text = "Skip for now", onClick = viewModel::next, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }

    editingWallet?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = walletsViewModel::save,
            onDelete = null,
            onDismiss = { editingWallet = null },
        )
    }
    if (addingGoal) {
        NewGoalSheet(currencyCode = state.currency, onDismiss = { addingGoal = false })
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(Spacing.sm))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.lg))
}

@Composable
private fun WalletsStep(title: String, body: String, wallets: List<Wallet>, addLabel: String, onAdd: () -> Unit) {
    StepHeader(title, body)
    if (wallets.isNotEmpty()) {
        LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            wallets.forEachIndexed { i, w ->
                Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    WalletIdentityTile(w)
                    Spacer(Modifier.width(Spacing.md))
                    Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // A card or loan shows what it does to net worth (−300.00), never an unsigned owed amount.
                    MoneyText(money = if (w.type.isLiability) w.netWorthContribution else w.balance, style = AmountStyles.row)
                }
                if (i < wallets.lastIndex) Hairline()
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
    LucentoSecondaryButton(text = addLabel, onClick = onAdd, icon = Icons.Outlined.Add)
}

@Composable
private fun BudgetsStep(
    state: GuideUiState,
    text: (Long) -> String,
    invalid: Set<Long>,
    separator: Char,
    onChange: (Long, String) -> Unit,
) {
    val required = GuidedSetup.budgetsRequired(state.expenseCategories.size)
    StepHeader(
        "Set your monthly budget",
        "Give at least $required ${if (required == 1) "category" else "categories"} an amount for ${state.currency}. The rest can wait — your total budget is always the sum of these. They repeat every month; later you can change one month alone or every month from then on.",
    )
    val total = GuidedSetup.draftTotal(state.expenseCategories.map { text(it.id) }, state.currency, separator)
    // At large text the field moves under the category name, so neither has to squeeze.
    val stacked = LocalDensity.current.fontScale >= 1.5f
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        state.expenseCategories.forEachIndexed { i, category ->
            BudgetAmountRow(
                category = category,
                currency = state.currency,
                value = text(category.id),
                error = if (category.id in invalid) {
                    // An ambiguous amount or another currency's symbol says what to type instead (P0-3).
                    val parsed = MoneyParser.parse(text(category.id), state.currency, separator)
                    MoneyParser.clarification(parsed, separator) ?: "Enter an amount above zero, like 250${separator}00."
                } else null,
                stacked = stacked,
                onChange = { onChange(category.id, it) },
            )
            if (i < state.expenseCategories.lastIndex) Hairline(inset = RowTextInset)
        }
        // The total is never typed: it is always the sum of the rows above, as the budget will be.
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                .padding(horizontal = Spacing.md, vertical = Spacing.md)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = Icons.Outlined.DonutLarge,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Monthly budget", style = MaterialTheme.typography.titleSmall)
                Text("The sum of these amounts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.sm))
            MoneyText(money = total, style = AmountStyles.medium.copy(fontWeight = FontWeight.Bold), fractionScale = 1f)
        }
    }
}

/** Where a row's text starts: past the card's padding, the 40dp category tile and the gap after it. */
private val RowTextInset = Spacing.md + Sizes.iconTile + Spacing.md

/** One category: its tile and name on the left, the monthly amount typed on the right (below it at large text). */
@Composable
private fun BudgetAmountRow(
    category: Category,
    currency: String,
    value: String,
    error: String?,
    stacked: Boolean,
    onChange: (String) -> Unit,
) {
    val colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
    val field: @Composable (Modifier) -> Unit = { m ->
        AmountTextField(
            value = value,
            onValueChange = onChange,
            filter = { it.take(20) },
            prefix = { Text(MoneyFormatter.currencySymbol(currency) + " ") },
            placeholder = { Text("Not set", textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
            isError = error != null,
            textStyle = AmountStyles.row.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.small,
            colors = colors,
            // The field has no visible label: the category name beside it is the label, so TalkBack hears it too.
            modifier = m.semantics { contentDescription = "${category.name} monthly budget" },
        )
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryTile(category, TransactionType.EXPENSE)
            Spacer(Modifier.width(Spacing.md))
            Text(
                category.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!stacked) {
                Spacer(Modifier.width(Spacing.md))
                field(Modifier.width(144.dp))
            }
        }
        if (stacked) {
            Spacer(Modifier.height(Spacing.sm))
            field(Modifier.fillMaxWidth())
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = if (stacked) 0.dp else Sizes.iconTile + Spacing.md, top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun GoalStep(state: GuideUiState, onAdd: () -> Unit) {
    StepHeader("Save for something", "A trip, an emergency fund, a new phone — set one goal and Lucento shows how much a month gets you there.")
    if (state.goals.isNotEmpty()) {
        LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            state.goals.forEachIndexed { i, g ->
                Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(icon = Icons.Outlined.Flag, container = LucentoTheme.extendedColors.savings.container, content = LucentoTheme.extendedColors.savings.content)
                    Spacer(Modifier.width(Spacing.md))
                    Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(MoneyFormatter.format(g.target), style = AmountStyles.row)
                }
                if (i < state.goals.lastIndex) Hairline()
            }
        }
        Spacer(Modifier.height(Spacing.md))
    } else {
        LucentoSecondaryButton(text = "Add a goal", onClick = onAdd, icon = Icons.Outlined.Add)
    }
}

@Composable
private fun TransactionsStep(state: GuideUiState, onRecord: () -> Unit) {
    StepHeader(
        "Record a few transactions",
        "Type them like a message — “coffee 4.50, lunch 12” — and review before saving. Try a few from today.",
    )
    InfoNote("These are real entries in your history. Practising? Delete any of them later from Transactions.")
    Spacer(Modifier.height(Spacing.md))
    if (state.transactionCount > 0) {
        Text(
            "${state.transactionCount} recorded so far.",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
    }
    LucentoSecondaryButton(text = if (state.transactionCount > 0) "Record more" else "Open the composer", onClick = onRecord, icon = Icons.Outlined.Edit)
}

/**
 * Part D completion: what's in place (each line ticked or still open, in words), the milestones already earned, and
 * the one next thing — the + button. Shown after the writes it describes, so nothing here is premature.
 */
@Composable
private fun WelcomeStep(state: GuideUiState) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "You’re ready",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            LucentoMascot(size = 72.dp, mood = MascotMood.Happy, contentDescription = "Lucento, pleased")
        }
        Text(
            "Here’s what’s in place. It’s kept on this phone, and copied to your account when cloud sync is on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        DoneRow(state.assets.isNotEmpty(), "Accounts", plural(state.wallets.size, "account", "accounts"))
        if (state.features.isEnabled(com.lucento.app.domain.Feature.BUDGETS)) {
            DoneRow(
                state.budgetedCount > 0,
                "Budget",
                if (state.budgetedCount > 0) plural(state.budgetedCount, "category with an amount", "categories with an amount") else "Not set yet",
            )
        }
        if (state.features.isEnabled(com.lucento.app.domain.Feature.SAVINGS_GOALS)) {
            DoneRow(
                state.goals.isNotEmpty(),
                "Savings goal",
                if (state.goals.isNotEmpty()) plural(state.goals.size, "goal", "goals") else "Optional — add one any time",
            )
        }
        DoneRow(
            state.transactionCount > 0,
            "First transaction",
            if (state.transactionCount > 0) "${state.transactionCount} recorded" else "Tap + on Home to record one",
        )
        if (state.earned.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            Text("Milestones earned", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.xs))
            com.lucento.app.domain.Achievement.entries.filter { it in state.earned }.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.xxs).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = LucentoTheme.extendedColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("${a.title} · ${a.description}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** One line of the completion checklist: done or still open, said in words as well as with the mark. */
@Composable
private fun DoneRow(done: Boolean, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouch)
            .semantics(mergeDescendants = true) { contentDescription = "$title: ${if (done) "done" else "not yet"}, $detail" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
