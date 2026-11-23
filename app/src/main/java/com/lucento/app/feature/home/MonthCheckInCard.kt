package com.lucento.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.AmountFormat
import com.lucento.app.core.designsystem.component.IconTile
import com.lucento.app.core.designsystem.component.LucentoCard
import com.lucento.app.core.designsystem.component.LucentoPrimaryButton
import com.lucento.app.core.designsystem.component.LucentoTextButton
import com.lucento.app.core.designsystem.component.amountFormat
import com.lucento.app.core.designsystem.theme.Hierarchy
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.MonthCheckIn
import java.time.LocalDate
import java.time.YearMonth

/**
 * The monthly check-in on Home (owner, 2026-11-23): in the last days of a month it asks to plan the next one, and from the
 * 1st to the 7th it also offers the month that ended as a recap. Nothing is changed by the card itself: planning happens
 * on the plan page, and its close button ("Not this month") only stops the card asking about these months again. The
 * close button sits in the corner, so the actions that do something share one row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MonthCheckInCard(
    card: MonthCheckIn.Card,
    today: LocalDate,
    budget: BudgetPlan?,
    onPlan: (YearMonth) -> Unit,
    onRecap: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val fmt = amountFormat()
    val copy = CheckInCopy.of(card, today, budget, fmt)
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(
                icon = if (card.plan != null) Icons.Outlined.EditCalendar else Icons.Outlined.EmojiEvents,
                size = 40.dp,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f).padding(top = Spacing.xs)) {
                Text(copy.title, style = Hierarchy.cardTitle, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.xxs))
                // Balanced lines, so no word is left alone on the last one (critic r2).
                Text(
                    copy.body,
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 48dp, pulled into the card's padding so its icon lines up with the title.
            IconButton(onClick = onDismiss, modifier = Modifier.offset(x = Spacing.sm, y = -Spacing.sm)) {
                Icon(Icons.Outlined.Close, contentDescription = "Not this month", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Wraps at large text sizes, so every action keeps its whole label and its 48dp target. The card's main action is
        // the filled one, plain to see in both themes (design review 2026-11-23; critic r2: a tonal fill sank in dark).
        // There is one card at a time and only around a month's turn, so it doesn't compete with Home's own actions.
        Spacer(Modifier.height(Spacing.xs))
        // The main action first, under the text it belongs to; a second action follows it, or sits below it on a narrow
        // screen, so a wrap reads as an order rather than a gap.
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 40.dp + Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            card.plan?.let { month -> LucentoPrimaryButton(text = "Plan ${monthLabel(month)}", onClick = { onPlan(month) }, fillWidth = false) }
            card.recap?.let { month ->
                if (card.plan != null) LucentoTextButton(text = copy.recapAction, onClick = { onRecap(month) })
                else LucentoPrimaryButton(text = copy.recapAction, onClick = { onRecap(month) }, fillWidth = false)
            }
        }
    }
}

/** What closing the card stops: "We won’t ask about December again", or "November’s recap hidden" (it stays in Progress). */
internal fun checkInHiddenMessage(card: MonthCheckIn.Card): String = card.plan?.let { "We won’t ask about ${monthLabel(it)} again" }
    ?: "${monthLabel(card.recap!!)}’s recap hidden"

/** The card's words, from its figures only. Pure (apart from month names), so the cases are pinned in tests. */
internal data class CheckInCopy(val title: String, val body: String, val recapAction: String) {
    companion object {
        fun of(card: MonthCheckIn.Card, today: LocalDate, budget: BudgetPlan?, fmt: AmountFormat): CheckInCopy {
            val thisMonth = YearMonth.from(today)
            val recap = card.recap
            val recapAction = recap?.let { "See ${monthLabel(it)}’s recap" } ?: ""
            val plan = card.plan
            return when {
                // The last days of a month: plan the next one before it starts.
                plan != null && plan > thisMonth -> {
                    val ends = when (val left = MonthCheckIn.daysLeft(today)) {
                        0 -> "${monthLabel(thisMonth)} ends today."
                        1 -> "${monthLabel(thisMonth)} ends tomorrow."
                        else -> "${monthLabel(thisMonth)} ends in $left days."
                    }
                    // This month's figures are on the budget card right below: the card only says what to do.
                    CheckInCopy(
                        title = "Ready for ${monthLabel(plan)}?",
                        body = "$ends Set ${monthLabel(plan)}’s amounts now, so a birthday or another one-off cost has room.",
                        recapAction = recapAction,
                    )
                }
                // The first days of a month, with last month to look back on.
                plan != null && recap != null -> CheckInCopy(
                    title = "${monthLabel(plan)} has started",
                    body = (budget?.let { "${monthLabel(recap)}: ${budgetLine(it, fmt)}. " } ?: "${monthLabel(recap)} is done. ") +
                        "Check ${monthLabel(plan)}’s amounts for anything different this month.",
                    recapAction = recapAction,
                )
                plan != null -> CheckInCopy(
                    title = "${monthLabel(plan)} has started",
                    body = "Check its amounts for anything different this month, like a birthday or an event.",
                    recapAction = recapAction,
                )
                else -> {
                    val month = requireNotNull(recap)
                    CheckInCopy(
                        title = "${monthLabel(month)} in review",
                        body = (budget?.let { "${budgetLine(it, fmt)}. " }.orEmpty()) +
                            "See what came in, what went out and where it went.",
                        recapAction = recapAction,
                    )
                }
            }
        }

        /** "₱18,200 of ₱20,000 spent", or "₱1,200 over budget": the words carry the sign. */
        fun budgetLine(plan: BudgetPlan, fmt: AmountFormat): String =
            if (plan.remaining.isNegative) "${fmt.format(-plan.remaining)} over budget"
            else "${fmt.format(plan.spent)} of ${fmt.format(plan.total)} spent"
    }
}
