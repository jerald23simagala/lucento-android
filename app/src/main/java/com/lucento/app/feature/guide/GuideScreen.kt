package com.lucento.app.feature.guide

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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.lucento.app.core.designsystem.component.AmountTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    if (state.loading) return
    val steps = state.steps
    val index = steps.indexOf(state.step).coerceAtLeast(0)

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter).imePadding()) {
        if (state.step != GuideStep.WELCOME) {
            GuideProgress(index, steps.size - 1, Modifier.padding(top = Spacing.md))
        }
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
                        onAdd = { editingWallet = WalletDraft(type = WalletType.BANK, currencyCode = state.currency, isDefault = state.assets.isEmpty()) },
                    )
                    GuideStep.DEBTS -> WalletsStep(
                        title = "Any cards or loans?",
                        body = "Add credit cards and loans so your net worth is honest. Skip this if you have none — you can add them later.",
                        wallets = state.debts,
                        addLabel = "Add a card or loan",
                        onAdd = { editingWallet = WalletDraft(type = WalletType.CREDIT_CARD, currencyCode = state.currency) },
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
private fun GuideProgress(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(bottom = Spacing.lg).semantics { contentDescription = "Setup step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            Box(
                Modifier.weight(1f).height(4.dp).background(
                    if (i <= current) LucentoTheme.extendedColors.accent else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(2.dp),
                ),
            )
        }
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
                    WalletTypeTile(w.type)
                    Spacer(Modifier.width(Spacing.md))
                    Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    MoneyText(money = w.balance, style = AmountStyles.row)
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
        "Give at least $required ${if (required == 1) "category" else "categories"} an amount for ${state.currency}. The rest can wait — your total budget is always the sum of these.",
    )
    val colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
    state.expenseCategories.forEach { category ->
        AmountTextField(
            value = text(category.id),
            onValueChange = { onChange(category.id, it) },
            filter = { it.take(20) },
            label = { Text(category.name) },
            prefix = { Text(MoneyFormatter.currencySymbol(state.currency) + " ") },
            placeholder = { Text("Not set") },
            isError = category.id in invalid,
            supportingText = if (category.id in invalid) ({ Text("Enter an amount above zero, like 250${separator}00.") }) else null,
            textStyle = AmountStyles.row,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.small,
            colors = colors,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        )
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

@Composable
private fun WelcomeStep(state: GuideUiState) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        LucentoMascot(size = 140.dp, mood = MascotMood.Happy, contentDescription = "Lucento, waving hello")
        Spacer(Modifier.height(Spacing.lg))
        Text("Welcome to Lucento", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Spacing.sm))
        val parts = buildList {
            add("${state.wallets.size} ${if (state.wallets.size == 1) "account" else "accounts"}")
            if (state.budgetedCount > 0) add("${state.budgetedCount} budgeted ${if (state.budgetedCount == 1) "category" else "categories"}")
            if (state.goals.isNotEmpty()) add("${state.goals.size} ${if (state.goals.size == 1) "goal" else "goals"}")
            if (state.transactionCount > 0) add("${state.transactionCount} ${if (state.transactionCount == 1) "transaction" else "transactions"}")
        }
        Text(
            "You’re set up: ${parts.joinToString(", ")}. This stays only on this phone unless cloud sync is on.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
