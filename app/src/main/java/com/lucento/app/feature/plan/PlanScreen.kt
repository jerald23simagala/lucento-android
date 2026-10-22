package com.lucento.app.feature.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetCalculator
import com.lucento.app.domain.BudgetStatus
import com.lucento.app.domain.Feature
import com.lucento.app.domain.GoalCalculator
import com.lucento.app.domain.RecurringMath
import java.time.YearMonth

/**
 * The Plan hub: a staging screen for advanced planning. Each row says what it holds (from real saved
 * data) and opens its own screen. Modules switched off in Settings are not listed.
 */
@Composable
fun PlanScreen(
    onBack: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val features = state.features
    val modules = listOf(Feature.BUDGETS, Feature.SAVINGS_GOALS, Feature.DEBTS_LOANS, Feature.RECURRING)
    val visible = modules.filter { features.isEnabled(it) }
    val hidden = modules - visible.toSet()
    val ext = LucentoTheme.extendedColors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = "Plan", subtitle = "All your financial plans in one place", onBack = onBack)

        if (visible.isEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            InfoNote("Every planning module is turned off. Turn on budgets, goals, debts or recurring payments in Settings › Enabled features.")
        } else {
            LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.sm) {
                visible.forEachIndexed { index, feature ->
                    when (feature) {
                        Feature.BUDGETS -> NavRow(
                            icon = Icons.Outlined.PieChart,
                            title = "Budgets",
                            summary = budgetSummary(state),
                            tone = TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                            onClick = onOpenBudgets,
                        )
                        Feature.SAVINGS_GOALS -> NavRow(
                            icon = Icons.Outlined.Savings,
                            title = "Savings goals",
                            summary = goalSummary(state),
                            tone = ext.savings,
                            onClick = onOpenGoals,
                        )
                        Feature.DEBTS_LOANS -> NavRow(
                            icon = Icons.Outlined.CreditCard,
                            title = "Debts & loans",
                            summary = debtSummary(state),
                            tone = ext.credit,
                            onClick = onOpenDebts,
                        )
                        Feature.RECURRING -> NavRow(
                            icon = Icons.Outlined.EventRepeat,
                            title = "Recurring payments",
                            summary = recurringSummary(state),
                            tone = ext.cash,
                            onClick = onOpenRecurring,
                        )
                        else -> Unit
                    }
                    if (index < visible.lastIndex) Hairline(inset = Sizes.iconTile + Spacing.md + Spacing.xs)
                }
            }
        }

        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Turned off: " + hidden.joinToString(", ") { it.title } + ". Your saved data is kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LucentoTextButton(text = "Manage in Settings", onClick = onOpenSettings)
        }

        Spacer(Modifier.height(Spacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LucentoMascot(size = 56.dp)
            Spacer(Modifier.width(Spacing.md))
            Text(
                "A clearer plan today, a brighter tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

internal fun budgetSummary(state: PlanUiState): String {
    if (state.budgets.isEmpty()) return "Set monthly limits overall or per category"
    val progress = BudgetCalculator.progressAll(state.budgets, state.transactions, YearMonth.from(state.today))
    val over = progress.count { it.status == BudgetStatus.OVER_LIMIT }
    val near = progress.count { it.status == BudgetStatus.NEAR_LIMIT }
    val count = "${state.budgets.size} active ${if (state.budgets.size == 1) "budget" else "budgets"}"
    return when {
        over > 0 -> "$count · $over over the limit"
        near > 0 -> "$count · $near close to the limit"
        else -> "$count · all on track"
    }
}

internal fun goalSummary(state: PlanUiState): String {
    if (state.goals.isEmpty()) return "Save toward targets with clear progress"
    val reached = state.goals.count { GoalCalculator.progress(it, state.contributions, state.today).reached }
    val count = "${state.goals.size} active ${if (state.goals.size == 1) "goal" else "goals"}"
    return if (reached > 0) "$count · $reached reached" else count
}

internal fun debtSummary(state: PlanUiState): String {
    val debts = state.wallets.filter { it.type.isLiability }
    if (debts.isEmpty()) return "Track credit cards and loans as accounts"
    val owed = debts.groupBy { it.currencyCode }
        .map { (code, list) -> Money.sum(code, list.map { it.balance }) }
        .joinToString(" · ") { MoneyFormatter.format(it) }
    return "${debts.size} ${if (debts.size == 1) "account" else "accounts"} · $owed owed"
}

internal fun recurringSummary(state: PlanUiState): String {
    if (state.schedules.isEmpty()) return "Bills, subscriptions and salary you confirm"
    val dueSoon = RecurringMath.upcoming(state.schedules, state.today.plusDays(RecurringMath.UPCOMING_WINDOW_DAYS)).size
    val active = state.schedules.count { !it.paused && !it.isFinished }
    return "$active active" + if (dueSoon > 0) " · $dueSoon due in the next two weeks" else ""
}
