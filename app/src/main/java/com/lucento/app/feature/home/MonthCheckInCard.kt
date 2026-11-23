package com.lucento.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
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
 * on the plan page, and its close button ("Not this month") only stops the card asking about these months again.
 *
 * Built for a 360 dp phone first (critic r3, P1): the words and the actions use the card's full width, the close button
 * sits in the corner without a row of its own, and below 400 dp or from 130 % text the icon tile gives its room to the
 * words. The card is there for about ten days a month, so it stays short.
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
    val compact = LocalConfiguration.current.screenWidthDp < 400 || LocalDensity.current.fontScale >= 1.3f
    LucentoCard(Modifier.fillMaxWidth(), contentPadding = Spacing.lg) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                // The title leaves room for the close button beside it; the words below use the whole width.
                Row(Modifier.padding(end = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!compact) {
                        IconTile(
                            icon = if (card.plan != null) Icons.Outlined.EditCalendar else Icons.Outlined.EmojiEvents,
                            size = 40.dp,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(Spacing.md))
                    }
                    Text(copy.title, style = Hierarchy.cardTitle, modifier = Modifier.semantics { heading() })
                }
                Spacer(Modifier.height(Spacing.xs))
                // Balanced lines, so no word is left alone on the last one (critic r2).
                Text(
                    copy.body,
                    style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 48dp, in the corner over the card's padding, so it takes no row of its own.
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).offset(x = Spacing.md, y = -Spacing.md)) {
                Icon(Icons.Outlined.Close, contentDescription = copy.closeLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Wraps at large text sizes, so every action keeps its whole label and its 48dp target. The card's main action is
        // the filled one, plain to see in both themes (design review 2026-11-23; critic r2: a tonal fill sank in dark).
        // There is one card at a time and only around a month's turn, so it doesn't compete with Home's own actions.
        Spacer(Modifier.height(Spacing.md))
        // The main action first; a second action follows it, or sits below it on a narrow screen, so a wrap reads as an
        // order rather than a gap.
        FlowRow(
            Modifier.fillMaxWidth(),
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

/**
 * The card's words, from its figures only. Pure (apart from month names), so the cases are pinned in tests. Short on
 * purpose: at 360 dp and 130 % text the body stays within three lines (critic r3, P1). [closeLabel] is what the close
 * button does, for TalkBack.
 */
internal data class CheckInCopy(val title: String, val body: String, val recapAction: String, val closeLabel: String) {
    companion object {
        fun of(card: MonthCheckIn.Card, today: LocalDate, budget: BudgetPlan?, fmt: AmountFormat): CheckInCopy {
            val thisMonth = YearMonth.from(today)
            val recap = card.recap
            val recapAction = recap?.let { "See ${monthLabel(it)}’s recap" } ?: ""
            val plan = card.plan
            val closeLabel = if (plan != null) "Not this month" else "Hide ${monthLabel(requireNotNull(recap))}’s recap"
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
                        body = "$ends Make room for ${monthLabel(plan)}’s one-offs, like a birthday.",
                        recapAction = recapAction,
                        closeLabel = closeLabel,
                    )
                }
                // The first days of a month, with last month to look back on.
                plan != null && recap != null -> CheckInCopy(
                    title = "${monthLabel(plan)} has started",
                    body = (budget?.let { "${monthLabel(recap)}: ${budgetLine(it, fmt)}. " } ?: "${monthLabel(recap)} is done. ") +
                        "Anything different this month?",
                    recapAction = recapAction,
                    closeLabel = closeLabel,
                )
                plan != null -> CheckInCopy(
                    title = "${monthLabel(plan)} has started",
                    body = "Anything different this month, like a birthday or an event?",
                    recapAction = recapAction,
                    closeLabel = closeLabel,
                )
                else -> {
                    val month = requireNotNull(recap)
                    CheckInCopy(
                        title = "${monthLabel(month)} in review",
                        body = (budget?.let { "${budgetLine(it, fmt)}. " }.orEmpty()) + "See where ${monthLabel(month)}’s money went.",
                        recapAction = recapAction,
                        closeLabel = closeLabel,
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
