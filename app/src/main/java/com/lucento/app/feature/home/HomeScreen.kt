package com.lucento.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.lucento.app.core.designsystem.component.ConsumptionBar
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MoneyText
import com.lucento.app.core.designsystem.component.NetFlowIndicator
import com.lucento.app.core.designsystem.component.ProfileButton
import com.lucento.app.core.designsystem.component.SectionHeader
import com.lucento.app.core.designsystem.component.StatementPanel
import com.lucento.app.core.designsystem.component.StatusPill
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.OverlineStyle
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetBurn
import com.lucento.app.domain.BudgetHealth
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.BurnLevel
import com.lucento.app.domain.Feature
import com.lucento.app.domain.GoalsOverview
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.UpcomingPayment
import com.lucento.app.domain.WellnessTier
import com.lucento.app.feature.budget.burnColor
import com.lucento.app.feature.budget.encouragement
import com.lucento.app.feature.transactions.shortDayLabel
import com.lucento.app.feature.wallets.WalletDraft
import com.lucento.app.feature.wallets.WalletEditorSheet
import com.lucento.app.feature.wallets.WalletsViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Callbacks Home needs from the app shell. Adding transactions happens through the app-wide + button. */
data class HomeActions(
    val onOpenBudget: () -> Unit,
    val onChooseCategories: () -> Unit,
    val onOpenInsights: () -> Unit,
    val onOpenGoals: () -> Unit,
    /** Opens Recurring payments; with a schedule id, straight to that payment's review / mark-paid sheet. */
    val onOpenRecurring: (Long?) -> Unit,
)

/**
 * The dashboard, top to bottom: the budget hero (with the Sun), net flow, the burn-rate bar, the savings level and
 * upcoming recurring payments. No duplicated action buttons — navigation lives in the dock, adding behind the + button.
 */
@Composable
fun HomeScreen(
    actions: HomeActions,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    walletsViewModel: WalletsViewModel = viewModel(factory = WalletsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingWallet by remember { mutableStateOf<WalletDraft?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        Greeting(today = state.today, name = state.displayName)

        AnimatedContent(
            targetState = when {
                state.loading -> HomeContent.Loading
                !state.hasWallets -> HomeContent.Welcome
                else -> HomeContent.Overview
            },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "home-content",
        ) { content ->
            when (content) {
                HomeContent.Loading -> Spacer(Modifier.height(1.dp))
                HomeContent.Welcome -> WelcomePanel(
                    onAddWallet = { editingWallet = WalletDraft(currencyCode = state.primaryCurrency) },
                )
                HomeContent.Overview -> Overview(state = state, actions = actions)
            }
        }
        // Clears the + button that floats above the dock.
        Spacer(Modifier.height(Spacing.fabClearance))
    }

    editingWallet?.let { draft ->
        WalletEditorSheet(
            initial = draft,
            onSave = walletsViewModel::save,
            onDelete = walletsViewModel::delete,
            onDismiss = { editingWallet = null },
        )
    }
}

private enum class HomeContent { Loading, Welcome, Overview }

/** Time-of-day greeting (with the name, when the user gave one) and the profile button. */
@Composable
private fun Greeting(today: LocalDate, name: String?) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateLabel = remember(today) {
        today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (name != null) "$greeting, ${name.substringBefore(' ')}" else greeting,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ProfileButton()
    }
}

/** First run with no wallets: one clear next step, and an honest description of what follows. */
@Composable
private fun WelcomePanel(onAddWallet: () -> Unit) {
    val colors = LucentoTheme.extendedColors
    Column {
        Spacer(Modifier.height(Spacing.sm))
        StatementPanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Let’s set up your money",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                LucentoMascot(size = 56.dp, mood = MascotMood.Curious)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Add the accounts you use. Lucento keeps your budget, cash flow and goals in view — all calculated on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHeroMuted,
            )
            Spacer(Modifier.height(Spacing.xl))
            Button(
                onClick = onAddWallet,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.button),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = colors.onHero, contentColor = colors.hero),
            ) {
                Text("Add your first wallet", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Overview(state: HomeUiState, actions: HomeActions) {
    val f = state.features
    Column {
        Spacer(Modifier.height(Spacing.xs))
        val plan = state.budget
        if (plan != null) {
            BudgetHero(state, plan, actions)
            Spacer(Modifier.height(Spacing.md))
        }

        NetFlowCard(state, onOpenInsights = if (f.isEnabled(Feature.INSIGHTS)) actions.onOpenInsights else null)

        val burn = state.burn
        if (plan != null && burn != null && state.expenseCategoryCount > 0) {
            Spacer(Modifier.height(Spacing.md))
            BurnRateCard(plan, burn, onOpen = actions.onOpenBudget)
        }

        if (f.isEnabled(Feature.SAVINGS_GOALS)) {
            Spacer(Modifier.height(Spacing.md))
            SavingsLevelCard(state.goals, hasAnyGoals = state.hasAnyGoals, onOpen = actions.onOpenGoals)
        }

        if (f.isEnabled(Feature.RECURRING)) {
            Spacer(Modifier.height(Spacing.section))
            UpcomingSection(state, onOpen = actions.onOpenRecurring)
        }
    }
}

// ------------------------------------------------------------------ A. Budget hero

/**
 * The most prominent card: what's left of this month's budget (the sum of category amounts), with the total and
 * the amount spent beneath it, and Lucento (the mascot) whose torch light and look follow the month's
 * [WellnessTier] — bright and tidy while within budget at pace, dimmer and more worn as the budget runs out, with a
 * supportive (never judging) pose when 10% or less is left or the budget is used up.
 */
@Composable
private fun BudgetHero(state: HomeUiState, plan: BudgetPlan, actions: HomeActions) {
    val ext = LucentoTheme.extendedColors
    val monthName = monthLabel(plan.month)
    val mood = when {
        !plan.hasBudget -> MascotMood.Curious
        plan.health.isConcerning -> MascotMood.Concerned
        else -> MascotMood.Happy
    }
    val tier = if (plan.hasBudget) {
        WellnessTier.of(plan.health, aheadOfPace = state.burn?.aheadOfPace == true, overspent = plan.remaining.isNegative)
    } else {
        WellnessTier.STEADY
    }
    val mascotLabel = when {
        !plan.hasBudget -> "Lucento, ready when your budget is"
        plan.health.isConcerning -> "Lucento, torch dimmed, gently concerned: little or none of this month’s budget is left"
        tier == WellnessTier.GETTING_TIGHT -> "Lucento, torch a little dimmer: most of this month’s budget is used"
        else -> "Lucento, torch bright: you’re within budget"
    }
    Surface(
        onClick = if (state.expenseCategoryCount == 0) actions.onChooseCategories else actions.onOpenBudget,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = ext.hero,
        contentColor = ext.onHero,
    ) {
        Row(Modifier.padding(start = Spacing.xl, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (plan.hasBudget && plan.remaining.isNegative) "OVER BUDGET · " else "BUDGET LEFT · ") +
                        monthName.uppercase(Locale.getDefault()),
                    style = OverlineStyle,
                    color = ext.onHeroMuted,
                )
                Spacer(Modifier.height(Spacing.xs))
                when {
                    state.expenseCategoryCount == 0 -> {
                        Text("Finish your budget setup", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Choose the categories you spend on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.onHeroMuted,
                        )
                    }
                    !plan.hasBudget -> {
                        Text("No budget yet", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Give your categories an amount — the total is their sum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.onHeroMuted,
                        )
                    }
                    else -> {
                        MoneyText(
                            money = if (plan.remaining.isNegative) -plan.remaining else plan.remaining,
                            style = AmountStyles.hero,
                            color = ext.onHero,
                        )
                        Text(
                            "of ${MoneyFormatter.format(plan.total)} · ${MoneyFormatter.format(plan.spent)} spent",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.onHeroMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            encouragement(plan),
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.onHero,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (state.otherBudgetCurrencies.isNotEmpty()) {
                    Text(
                        "Budgets in ${state.otherBudgetCurrencies.joinToString()} are on the Budget tab.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.onHeroMuted,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            LucentoMascot(size = 84.dp, mood = mood, tier = tier, contentDescription = mascotLabel)
        }
    }
}

// ------------------------------------------------------------------ C. Net flow

@Composable
private fun NetFlowCard(state: HomeUiState, onOpenInsights: (() -> Unit)?, modifier: Modifier = Modifier) {
    val flow = state.netFlow ?: return
    val ext = LucentoTheme.extendedColors
    val direction = state.flowDirection
    val color = when (direction) {
        FlowDirection.POSITIVE -> ext.positive
        FlowDirection.NEGATIVE -> ext.negative
        FlowDirection.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    LucentoCard(modifier.fillMaxWidth(), onClick = onOpenInsights, contentPadding = Spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetFlowIndicator(
                marker = when (direction) {
                    FlowDirection.POSITIVE -> "up"
                    FlowDirection.NEGATIVE -> "down"
                    FlowDirection.NEUTRAL -> "flat"
                },
                size = 40.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    "NET FLOW · ${monthLabel(flow.month).uppercase(Locale.getDefault())}",
                    style = OverlineStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyText(money = flow.net, style = AmountStyles.large, showPlus = true, color = color, fractionScale = 0.75f)
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountText(flow.income, style = AmountStyles.small, showPlus = true, color = ext.positive)
                AmountText(-flow.expense, style = AmountStyles.small, color = ext.negative)
            }
            if (onOpenInsights != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.otherMonthTotals.forEach { t ->
            Text(
                "${t.currencyCode}: ${MoneyFormatter.formatSigned(t.income - t.expense, showPlus = true)} this month (kept separate, never converted)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

// ------------------------------------------------------------------ D. Burn rate

@Composable
private fun BurnRateCard(plan: BudgetPlan, burn: BudgetBurn, onOpen: () -> Unit) {
    val ext = LucentoTheme.extendedColors
    val monthName = monthLabel(burn.month)
    LucentoCard(Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = Spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BURN RATE · ${monthName.uppercase(Locale.getDefault())}",
                style = OverlineStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            val consumed = burn.consumedPercent
            if (consumed != null) {
                Text(
                    "$consumed% used",
                    style = AmountStyles.small,
                    color = when (burn.level) {
                        BurnLevel.EXHAUSTED -> ext.negative
                        BurnLevel.APPROACHING -> ext.warning
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        if (burn.level == BurnLevel.NO_BUDGET) {
            ConsumptionBar(
                fraction = 0f,
                color = burnColor(burn.level),
                markerFraction = burn.elapsedPercent / 100f,
                description = "No budget set for $monthName",
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Set category amounts to see how fast this month’s budget is used. So far: ${MoneyFormatter.format(burn.dailyAverage)} a day in budgeted categories.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@LucentoCard
        }
        ConsumptionBar(
            fraction = burn.consumedFraction,
            color = burnColor(burn.level),
            markerFraction = burn.elapsedPercent / 100f,
            description = "${burn.consumedPercent ?: 0}% of this month’s budget used, ${burn.elapsedPercent}% of the month gone",
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth()) {
            Text(
                when (burn.level) {
                    BurnLevel.EXHAUSTED -> "Budget used up"
                    BurnLevel.APPROACHING -> "Approaching the limit"
                    else -> if (burn.aheadOfPace) "A little ahead of the calendar" else "On pace"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Day ${burn.daysElapsed} of ${burn.daysInMonth}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            buildString {
                append("${MoneyFormatter.format(burn.dailyAverage)} a day so far")
                val allowance = burn.dailyAllowance
                if (allowance != null) {
                    append(" · ${MoneyFormatter.format(allowance)} a day keeps you on budget")
                } else if (plan.health == BudgetHealth.EXCEEDED) {
                    append(" · ${MoneyFormatter.format(-plan.remaining)} over")
                }
                append(".")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

// ------------------------------------------------------------------ E. Savings level

@Composable
private fun SavingsLevelCard(goals: GoalsOverview?, hasAnyGoals: Boolean, onOpen: () -> Unit) {
    val ext = LucentoTheme.extendedColors
    LucentoCard(Modifier.fillMaxWidth(), onClick = onOpen, contentPadding = Spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SAVINGS GOALS", style = OverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (goals == null) {
            Text(
                if (hasAnyGoals) "Your goals are in other currencies — open Savings goals to see them."
                else "Set a target — a trip, a cushion, a new laptop — and watch it fill up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasAnyGoals) LucentoTextButton(text = "Start a savings goal", onClick = onOpen)
            return@LucentoCard
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${goals.percent}%", style = AmountStyles.large)
            Text(
                " saved across ${goals.goalCount} ${if (goals.goalCount == 1) "goal" else "goals"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        ConsumptionBar(
            fraction = goals.fraction,
            color = ext.success,
            description = "${goals.percent}% of your savings targets reached",
            height = 8.dp,
        )
        Spacer(Modifier.height(Spacing.sm))
        val closest = goals.closest
        Text(
            when {
                closest != null -> "Closest: ${closest.goal.name} · ${closest.percent}% · ${MoneyFormatter.format(closest.remaining)} to go"
                goals.reachedCount == goals.goalCount -> "Every goal reached — wonderful."
                else -> "${MoneyFormatter.format(goals.saved)} of ${MoneyFormatter.format(goals.target)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------ F. Upcoming recurring payments

@Composable
private fun UpcomingSection(state: HomeUiState, onOpen: (Long?) -> Unit) {
    SectionHeader("Upcoming payments") {
        if (state.hasAnySchedules) LucentoTextButton(text = "See all", onClick = { onOpen(null) })
    }
    if (state.upcoming.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = Icons.Outlined.EventRepeat)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.hasAnySchedules) "Nothing due in the next 30 days" else "No recurring payments scheduled",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (state.hasAnySchedules) "You’re all caught up." else "Add rent, bills or subscriptions and Lucento reminds you here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!state.hasAnySchedules) {
            LucentoTextButton(text = "Add a recurring payment", onClick = { onOpen(null) })
        }
        return
    }
    state.upcoming.forEachIndexed { index, item ->
        UpcomingRow(item, state, onClick = { onOpen(item.schedule.id) })
        if (index < state.upcoming.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md)
    }
    Text(
        "Nothing is recorded until you mark a payment as paid.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun UpcomingRow(item: UpcomingPayment, state: HomeUiState, onClick: () -> Unit) {
    val schedule = item.schedule
    val wallet = state.walletsById[schedule.walletId]
    val days = ChronoUnit.DAYS.between(state.today, item.dueDate)
    val whenLabel = when {
        item.overdue -> "Overdue since ${shortDayLabel(item.dueDate, state.today)}"
        days == 0L -> "Due today"
        days == 1L -> "Due tomorrow"
        days < 7L -> "Due in $days days"
        else -> "Due ${shortDayLabel(item.dueDate, state.today)}"
    }
    val income = schedule.type == TransactionType.INCOME
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Review ${schedule.name}", onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category = schedule.categoryId?.let { state.categoriesById[it] }, type = schedule.type)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(schedule.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(whenLabel.takeIf { !item.overdue }, wallet?.name).joinToString(" · ").ifEmpty { wallet?.name.orEmpty() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.overdue) {
                StatusPill(whenLabel, StatusTones.negative, Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        AmountText(
            money = if (income) schedule.amount else -schedule.amount,
            showPlus = true,
            color = if (income) LucentoTheme.extendedColors.positive else MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal fun monthLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
