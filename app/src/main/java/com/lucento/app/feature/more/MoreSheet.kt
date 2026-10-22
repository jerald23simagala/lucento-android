package com.lucento.app.feature.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Feature
import com.lucento.app.domain.GoalCalculator
import com.lucento.app.domain.RecurringMath
import com.lucento.app.feature.plan.PlanUiState
import com.lucento.app.feature.plan.PlanViewModel
import kotlinx.coroutines.launch

/** Secondary destinations reached from the dock's "More" button. */
enum class MoreDestination { GOALS, RECURRING, DEBTS, CATEGORIES }

/**
 * The "More" menu: secondary features, each with one line of real status. A module that is switched off is still
 * listed (its data is kept) and opens a screen that offers to turn it back on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    onOpen: (MoreDestination) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val f = state.features
    val ext = LucentoTheme.extendedColors
    val inset = Sizes.iconTile + Spacing.md + Spacing.xs

    fun open(destination: MoreDestination) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            onOpen(destination)
        }
    }

    LucentoBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding(),
        ) {
            Text("More", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(Spacing.sm))
            NavRow(
                icon = Icons.Outlined.Savings,
                title = "Savings goals",
                summary = if (f.isEnabled(Feature.SAVINGS_GOALS)) goalSummary(state) else OFF,
                tone = ext.savings,
                onClick = { open(MoreDestination.GOALS) },
            )
            Hairline(inset = inset)
            NavRow(
                icon = Icons.Outlined.EventRepeat,
                title = "Recurring payments",
                summary = if (f.isEnabled(Feature.RECURRING)) recurringSummary(state) else OFF,
                tone = ext.cash,
                onClick = { open(MoreDestination.RECURRING) },
            )
            Hairline(inset = inset)
            NavRow(
                icon = Icons.Outlined.CreditCard,
                title = "Debts & loans",
                summary = if (f.isEnabled(Feature.DEBTS_LOANS)) debtSummary(state) else OFF,
                tone = ext.credit,
                onClick = { open(MoreDestination.DEBTS) },
            )
            Hairline(inset = inset)
            NavRow(
                icon = Icons.Outlined.Category,
                title = "Categories",
                summary = categorySummary(state),
                tone = TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer),
                onClick = { open(MoreDestination.CATEGORIES) },
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

private const val OFF = "Turned off · your data is kept"

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

internal fun categorySummary(state: PlanUiState): String {
    val active = state.categories.count { it.kind == CategoryKind.EXPENSE && !it.archived }
    val archived = state.categories.count { it.kind == CategoryKind.EXPENSE && it.archived }
    return when (active) {
        0 -> "Choose the categories your budget is built from"
        1 -> "1 spending category"
        else -> "$active spending categories"
    } + if (archived > 0) " · $archived archived" else ""
}
