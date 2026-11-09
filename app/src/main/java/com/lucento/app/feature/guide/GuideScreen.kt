package com.lucento.app.feature.guide

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Smartphone
import com.lucento.app.core.designsystem.component.ActionDock
import com.lucento.app.core.designsystem.component.CheckMark
import com.lucento.app.core.designsystem.component.GroupLabel
import com.lucento.app.core.designsystem.component.PathRail
import com.lucento.app.core.designsystem.component.SetupTitle
import com.lucento.app.core.designsystem.component.icon
import com.lucento.app.core.designsystem.component.label
import com.lucento.app.core.designsystem.theme.Hierarchy
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
    // The goal sheet's starting name (a suggestion, or "" for something else); null while the sheet is closed.
    var addingGoal by remember { mutableStateOf<String?>(null) }
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

    Column(Modifier.fillMaxSize().imePadding()) {
        // Sunrise Path: back (not on the first page: the story resumes there anyway), the stage with its screen count
        // ("Set up · 3 of 4", counting the onboarding summary first), and Skip on the optional pages.
        val (screen, screens) = GuidedSetup.screenOf(state.step, state.features)
        PathRail(
            com.lucento.app.domain.SetupProgress(guideStage(state.step), state.furthestStage, guideStageProgress(state.step, steps), screen, screens),
            modifier = Modifier.padding(horizontal = Spacing.xs).padding(top = Spacing.xs),
            onBack = if (steps.firstOrNull() == state.step) null else viewModel::back,
            onSkip = if (GuidedSetup.isSkippable(state.step) && state.step != GuideStep.WELCOME) viewModel::next else null,
            finished = if (state.step == GuideStep.WELCOME) "All done" else null,
        )
        Column(Modifier.weight(1f).padding(horizontal = Spacing.gutter)) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "guide-step",
            modifier = Modifier.weight(1f),
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    GuideStep.WALLETS -> AccountsStep(
                        debts = false,
                        wallets = state.assets,
                        currency = state.currency,
                        suggested = state.spendTypes.map { it.walletType }.toSet(),
                        onAdd = { type -> editingWallet = WalletDraft(type = type, currencyCode = state.currency, isDefault = state.assets.isEmpty()) },
                    )
                    GuideStep.DEBTS -> AccountsStep(
                        debts = true,
                        wallets = state.debts,
                        currency = state.currency,
                        suggested = state.spendTypes.map { it.walletType }.toSet(),
                        onAdd = { type -> editingWallet = WalletDraft(type = type, currencyCode = state.currency) },
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
                    GuideStep.GOAL -> GoalStep(state, onAdd = { name -> addingGoal = name })
                    GuideStep.TRANSACTIONS -> TransactionsStep(state, onRecordTransactions)
                    GuideStep.WELCOME -> WelcomeStep(state)
                }
            }
        }

        ActionDock {
            if (state.step == GuideStep.BUDGETS) {
                val required = GuidedSetup.budgetsRequired(state.expenseCategories.size)
                val filled = state.expenseCategories.count { budgetText(it.id).isNotBlank() }
                LucentoPrimaryButton(
                    text = if (filled >= required) "Save and continue" else "Set ${required - filled} more to continue",
                    enabled = filled >= required,
                    trailingIcon = if (filled >= required) Icons.AutoMirrored.Outlined.ArrowForward else null,
                    onClick = {
                        viewModel.saveBudgets(state.expenseCategories.associate { it.id to budgetText(it.id) }, separator) { invalidBudgets = it }
                    },
                )
            } else if (state.step == GuideStep.WELCOME) {
                LucentoPrimaryButton(text = "Go to Home", trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward, onClick = {
                    haptics.confirm()
                    viewModel.finish(onFinished)
                })
            } else if (state.step == GuideStep.TRANSACTIONS && state.transactionCount == 0) {
                // Nothing recorded yet: trying the composer is the step's one action (Skip is in the rail).
                LucentoPrimaryButton(text = "Try it now", onClick = onRecordTransactions, icon = Icons.Outlined.EditNote)
            } else {
                LucentoPrimaryButton(
                    text = if (state.step == GuideStep.WALLETS && !state.canContinue) "Add one account to continue" else "Continue",
                    onClick = viewModel::next,
                    enabled = state.canContinue,
                    trailingIcon = if (state.canContinue) Icons.AutoMirrored.Outlined.ArrowForward else null,
                )
                if (state.step == GuideStep.TRANSACTIONS) {
                    LucentoTextButton(text = "Record more", onClick = onRecordTransactions, modifier = Modifier.fillMaxWidth())
                }
            }
        }
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
    addingGoal?.let { name ->
        NewGoalSheet(currencyCode = state.currency, onDismiss = { addingGoal = null }, initialName = name)
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    SetupTitle(title, body)
    Spacer(Modifier.height(22.dp))
}

/**
 * Accounts (or, with [debts], cards and loans). Empty: an illustrated scene and one-tap rows that open the account
 * editor already set to that type, the ones from "How do you pay?" first. Filled: the starting balance, the accounts, and
 * chips to add another.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsStep(
    debts: Boolean,
    wallets: List<Wallet>,
    currency: String,
    suggested: Set<WalletType>,
    onAdd: (WalletType) -> Unit,
) {
    val quick = if (debts) listOf(WalletType.CREDIT_CARD, WalletType.LOAN) else listOf(WalletType.CASH, WalletType.BANK, WalletType.E_WALLET)
    val ordered = quick.sortedBy { if (it in suggested) 0 else 1 }
    StepHeader(
        if (debts) "Any cards or loans?" else "Add your accounts",
        when {
            debts -> "Add credit cards and loans so your net worth is honest. Skip this if you have none — you can add them later."
            wallets.isEmpty() -> "Start with the cash, bank accounts and e-wallets you use, with today’s balance. No bank login needed."
            else -> "Add every account you use; you can add more later from Wallets."
        },
    )
    if (wallets.isEmpty()) {
        if (!debts) {
            AccountsScene()
            Spacer(Modifier.height(Spacing.lg))
        }
        GroupLabel(if (debts) "Add one" else "Start with one", trailing = if (quick.any { it in suggested }) "Suggested from your answers" else null)
        LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            ordered.forEachIndexed { i, type ->
                QuickAddRow(type, onClick = { onAdd(type) })
                if (i < ordered.lastIndex) Hairline(inset = Spacing.lg + 44.dp + 14.dp)
            }
        }
        if (!debts) {
            LucentoTextButton(text = "Savings or other accounts", onClick = { onAdd(WalletType.SAVINGS) }, modifier = Modifier.fillMaxWidth())
        }
    } else {
        if (!debts) {
            StartingBalanceCard(wallets, currency)
            Spacer(Modifier.height(12.dp))
        }
        LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            wallets.forEachIndexed { i, w ->
                Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    WalletIdentityTile(w)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(w.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // A card or loan shows what it does to net worth (−300.00), never an unsigned owed amount.
                    MoneyText(money = if (w.type.isLiability) w.netWorthContribution else w.balance, style = AmountStyles.row, fractionScale = 1f)
                }
                if (i < wallets.lastIndex) Hairline()
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        GroupLabel("Add another")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            quick.forEach { type -> AddChip(type.shortLabel, type.icon, LucentoTheme.extendedColors.tone(type).content) { onAdd(type) } }
            if (!debts) AddChip("Other", Icons.Outlined.MoreHoriz, MaterialTheme.colorScheme.onSurfaceVariant) { onAdd(WalletType.SAVINGS) }
        }
    }
}

/** Short names for the quick-add rows and chips. */
private val WalletType.shortLabel: String
    get() = when (this) {
        WalletType.CASH -> "Cash"
        WalletType.BANK -> "Bank account"
        WalletType.E_WALLET -> "E-wallet"
        WalletType.CREDIT_CARD -> "Credit card"
        WalletType.LOAN -> "Loan"
        else -> label
    }

private val WalletType.quickDetail: String
    get() = when (this) {
        WalletType.CASH -> "The money in your wallet"
        WalletType.BANK -> "Savings, checking or time deposit"
        WalletType.E_WALLET -> "Mobile wallets and payment apps"
        WalletType.CREDIT_CARD -> "Buy now, pay the card later"
        WalletType.LOAN -> "Car, home, personal or student"
        else -> label
    }

/** One quick-add row: the type's toned tile, its name and what it covers, and a plus. Opens the editor set to it. */
@Composable
private fun QuickAddRow(type: WalletType, onClick: () -> Unit) {
    val tone = LucentoTheme.extendedColors.tone(type)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clickable(role = Role.Button, onClickLabel = "Add") { onClick() }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(type.icon, container = tone.container, content = tone.content, size = 44.dp, shape = MaterialTheme.shapes.medium)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(type.shortLabel, style = MaterialTheme.typography.titleMedium)
            Text(type.quickDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** A small "+ Cash" chip that opens the account editor set to that type. */
@Composable
private fun AddChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, LucentoTheme.extendedColors.borderDefault, shape)
            .clickable(role = Role.Button, onClickLabel = "Add $label") { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(5.dp))
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
    }
}

/**
 * The empty accounts page's scene: a little stack of a bank card, cash and an e-wallet in their type colours, and
 * Lucento pointing at it. Decorative; the rows below are the actions.
 */
@Composable
private fun AccountsScene() {
    val ext = LucentoTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(156.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerLow, ext.highlight))),
    ) {
        // Drawn on a 360×156 grid, scaled to the card's width (and never taller than it).
        val unit = minOf(maxWidth / 360, maxHeight / 156)
        @Composable
        fun Card(x: Int, y: Int, w: Int, hgt: Int, angle: Float, color: androidx.compose.ui.graphics.Color) {
            Box(
                Modifier
                    .offset(x = unit * x, y = unit * y)
                    .size(width = unit * w, height = unit * hgt)
                    .graphicsLayer { rotationZ = angle }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(color, androidx.compose.ui.graphics.Color.White, 0.22f), color))),
            ) {
                Box(Modifier.padding(start = unit * 14, top = unit * 14).size(width = unit * 26, height = unit * 18).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f)))
            }
        }
        Card(26, 22, 140, 88, -9f, ext.bank.content)
        Card(76, 50, 128, 74, 5f, ext.cash.content)
        Card(50, 80, 102, 60, -3f, ext.eWallet.content)
        LucentoMascot(
            size = 150.dp * (unit / 1.dp),
            mood = MascotMood.Pointing,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = Spacing.xs),
        )
    }
}

/**
 * The accounts in the main currency added up, in the same blue card language as Home's balance. Accounts in another
 * currency are never converted here; they're listed below with their own figures.
 */
@Composable
private fun StartingBalanceCard(wallets: List<Wallet>, currency: String) {
    val ext = LucentoTheme.extendedColors
    val total = GuidedSetup.startingBalance(wallets, currency)
    val counted = GuidedSetup.startingBalanceAccounts(wallets, currency)
    val others = wallets.count { !it.type.isLiability } - counted
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(ext.hero, ext.heroEnd)))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column {
            Text("STARTING BALANCE", style = Hierarchy.cardOverline, color = ext.onHeroMuted, modifier = Modifier.semantics { contentDescription = "Starting balance" })
            Spacer(Modifier.height(Spacing.xs))
            Text(MoneyFormatter.format(total), style = AmountStyles.hero, color = ext.onHero)
            Text(
                buildString {
                    append("Across ${counted} ${if (counted == 1) "account" else "accounts"}")
                    if (others > 0) append(" · other currencies listed below")
                },
                style = MaterialTheme.typography.bodySmall,
                color = ext.onHeroMuted,
            )
        }
    }
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
        "Give at least $required ${if (required == 1) "category" else "categories"} an amount for ${state.currency}. Your budget is always their sum, and it repeats every month.",
    )
    BudgetHero(state, text, separator, required)
    Spacer(Modifier.height(12.dp))
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
    }
}

/**
 * The budget as it's being typed: the total (never typed — always the sum of the rows, as the budget will be), whether
 * the minimum is met, and how it splits between categories. Each share is named in words, never colour alone.
 */
@Composable
private fun BudgetHero(state: GuideUiState, text: (Long) -> String, separator: Char, required: Int) {
    val ext = LucentoTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    val rows = state.expenseCategories.mapNotNull { c ->
        MoneyParser.parseOrNull(text(c.id), state.currency, separator)?.takeIf { it.amountMinor > 0L }?.let { c to it }
    }
    val total = GuidedSetup.draftTotal(state.expenseCategories.map { text(it.id) }, state.currency, separator)
    val shares = GuidedSetup.sharePercents(rows.map { it.second.amountMinor })
    val met = rows.size >= required
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MONTHLY BUDGET", style = Hierarchy.cardOverline, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f).semantics { contentDescription = "Monthly budget" })
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (met) ext.positiveContainer else scheme.surfaceContainerHigh)
                        .padding(horizontal = Spacing.sm, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (met) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ext.positive, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        "${minOf(rows.size, required)} of $required set",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (met) ext.positive else scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(MoneyFormatter.format(total), style = AmountStyles.hero)
            Text(
                "The sum of ${rows.size} of ${state.expenseCategories.size} categories",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(ext.track), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.forEach { (c, money) ->
                        Box(Modifier.weight(money.amountMinor.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(ext.categoryTone(c.iconKey).content))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rows.zip(shares).sortedByDescending { it.second }.take(3).forEach { (row, share) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(ext.categoryTone(row.first.iconKey).content))
                            Spacer(Modifier.width(6.dp))
                            Text("${row.first.name} $share%", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
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
private fun GoalStep(state: GuideUiState, onAdd: (String) -> Unit) {
    StepHeader("Save for something", "Pick one goal and Lucento shows how much a month gets you there.")
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
        return
    }
    // Starting points open the goal sheet with the name filled in; the amount is always the person's own.
    val ext = LucentoTheme.extendedColors
    val ideas = listOf(
        GoalIdea("Emergency fund", "3–6 months of costs", Icons.Outlined.HealthAndSafety, ext.savings),
        GoalIdea("Trip", "Somewhere worth it", Icons.Outlined.Flight, ext.bank),
        GoalIdea("New phone", "Or any big purchase", Icons.Outlined.Smartphone, ext.eWallet),
        GoalIdea("Education", "Tuition, courses", Icons.Outlined.School, ext.loan),
    )
    ideas.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { idea -> GoalIdeaTile(idea, Modifier.weight(1f).fillMaxHeight()) { onAdd(idea.name) } }
        }
        Spacer(Modifier.height(12.dp))
    }
    LucentoSecondaryButton(text = "Something else", onClick = { onAdd("") }, icon = Icons.Outlined.Add)
    Spacer(Modifier.height(Spacing.lg))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LucentoMascot(size = 56.dp, mood = MascotMood.Curious)
        Spacer(Modifier.width(Spacing.md))
        Text("Not sure yet? Skip it — goals live on Home whenever you’re ready.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class GoalIdea(val name: String, val detail: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tone: com.lucento.app.core.designsystem.theme.TonePair)

@Composable
private fun GoalIdeaTile(idea: GoalIdea, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, LucentoTheme.extendedColors.borderDefault, shape)
            .clickable(role = Role.Button, onClickLabel = "Start this goal") { onClick() }
            .heightIn(min = 128.dp)
            .padding(Spacing.lg),
    ) {
        IconTile(idea.icon, container = idea.tone.container, content = idea.tone.content, size = 44.dp, shape = MaterialTheme.shapes.medium)
        Spacer(Modifier.height(14.dp))
        Text(idea.name, style = MaterialTheme.typography.titleMedium)
        Text(idea.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransactionsStep(state: GuideUiState, onRecord: () -> Unit) {
    StepHeader("Type it like a message", "Record what you spent today. You check everything before it’s saved.")
    ExampleConversation(state.currency)
    Spacer(Modifier.height(Spacing.lg))
    GroupLabel("Try typing")
    Text(
        "“yesterday dinner 650”\n“salary 35000”\n“taxi 180 from my e-wallet”",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
    Spacer(Modifier.height(Spacing.lg))
    InfoNote("These are real entries in your history. Practising? Delete any of them later from Transactions.")
    if (state.transactionCount > 0) {
        Spacer(Modifier.height(Spacing.md))
        Text("${state.transactionCount} recorded so far.", style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * One example exchange with the composer, labelled as an example: a message and the rows it becomes. Amounts are
 * parsed in the person's own currency, so they read naturally (and stay whole-unit in currencies without cents).
 */
@Composable
private fun ExampleConversation(currency: String) {
    val scheme = MaterialTheme.colorScheme
    val examples = listOf(
        Triple("Coffee", "120", Icons.Outlined.LocalCafe),
        Triple("Lunch", "250", Icons.Outlined.Restaurant),
        Triple("Bus", "13", Icons.Outlined.DirectionsBus),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLow)
            .semantics(mergeDescendants = true) {}
            .padding(Spacing.lg),
    ) {
        Text(
            "coffee 120, lunch 250 and a bus 13",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onPrimary,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                .background(scheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top) {
            LucentoMascot(size = 34.dp, mood = MascotMood.Happy, animate = false)
            Spacer(Modifier.width(10.dp))
            LucentoCard(Modifier.weight(1f), contentPadding = 0.dp) {
                examples.forEach { (name, amount, icon) ->
                    val money = MoneyParser.parseOrNull(amount, currency) ?: return@forEach
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon, container = scheme.primaryContainer, content = scheme.onPrimaryContainer, size = 32.dp, shape = MaterialTheme.shapes.small)
                        Spacer(Modifier.width(Spacing.md))
                        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(MoneyFormatter.format(com.lucento.app.core.money.Money(-money.amountMinor, money.currencyCode)), style = AmountStyles.row)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Example · read on this phone", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/**
 * The finish line: back at sunrise (the theme's panorama), Lucento celebrating — shown after the writes it describes —
 * what's in place written out in words, and the awards earned along the way.
 */
@Composable
private fun WelcomeStep(state: GuideUiState) {
    val spec = LucentoTheme.spec
    val ext = LucentoTheme.extendedColors
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(24.dp))) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(spec.scenery),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(0f, 0.3f),
                modifier = Modifier.fillMaxSize(),
            )
            LucentoMascot(
                size = 150.dp,
                mood = MascotMood.Celebrate,
                contentDescription = "Lucento, celebrating",
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        SetupTitle(
            "You’re all set",
            "Here’s what’s in place. It’s kept on this phone, and copied to your account when sync is on.",
            milestone = true,
            center = true,
        )
        Spacer(Modifier.height(18.dp))
        val budgetTotal = com.lucento.app.core.money.Money.sum(
            state.currency,
            state.expenseCategories.mapNotNull { state.allocation(it.id) }.filter { it.currencyCode == state.currency },
        )
        LucentoCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            val rows = buildList {
                val counted = GuidedSetup.startingBalanceAccounts(state.wallets, state.currency)
                add(Triple(state.assets.isNotEmpty(), "Accounts", if (state.assets.isEmpty()) "Not yet" else "${plural(state.wallets.size, "account", "accounts")} · ${MoneyFormatter.format(GuidedSetup.startingBalance(state.wallets, state.currency))}".takeIf { counted > 0 } ?: plural(state.wallets.size, "account", "accounts")))
                if (state.features.isEnabled(com.lucento.app.domain.Feature.BUDGETS)) {
                    add(Triple(state.budgetedCount > 0, "Monthly budget", if (state.budgetedCount > 0) "${MoneyFormatter.format(budgetTotal)} across ${plural(state.budgetedCount, "category", "categories")}" else "Not set yet"))
                }
                add(Triple(state.transactionCount > 0, "First transactions", if (state.transactionCount > 0) "${state.transactionCount} recorded" else "Tap + on Home to record one"))
                if (state.features.isEnabled(com.lucento.app.domain.Feature.SAVINGS_GOALS)) {
                    add(Triple(state.goals.isNotEmpty(), "Savings goal", if (state.goals.isNotEmpty()) plural(state.goals.size, "goal", "goals") else "Optional — add one from Home any time"))
                }
            }
            rows.forEachIndexed { i, (done, title, detail) ->
                DoneRow(done, title, detail)
                if (i < rows.lastIndex) Hairline(inset = Spacing.lg + 24.dp + Spacing.md)
            }
        }
        if (state.earned.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val earned = com.lucento.app.domain.Achievement.entries.filter { it in state.earned }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(ext.highlight)
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    earned.take(3).forEach { com.lucento.app.feature.awards.AwardMedal(it, earned = true, size = 28.dp) }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(plural(earned.size, "award earned", "awards earned"), style = MaterialTheme.typography.titleSmall, color = ext.onHighlight)
                    Text(earned.joinToString { it.title }, style = MaterialTheme.typography.bodySmall, color = ext.onHighlight)
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** One line of the finish checklist: done or still open, said in words as well as with the mark. */
@Composable
private fun DoneRow(done: Boolean, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clearAndSetSemantics { contentDescription = "$title: ${if (done) "done" else "not yet"}, $detail" }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(done)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
