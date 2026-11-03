package com.lucento.app.feature.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetHealth
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.BurnLevel
import com.lucento.app.domain.PacePosition

/** One colour per budget state, shared by the hero card, the burn bar and the Budget screen. */
@Composable
fun healthColor(health: BudgetHealth): Color {
    val ext = LucentoTheme.extendedColors
    return when (health) {
        BudgetHealth.NOT_SET -> MaterialTheme.colorScheme.outline
        BudgetHealth.ON_TRACK -> ext.success
        BudgetHealth.APPROACHING, BudgetHealth.LOW -> ext.warningIndicator
        BudgetHealth.EXCEEDED -> ext.negativeIndicator
    }
}

@Composable
fun burnColor(level: BurnLevel): Color {
    val ext = LucentoTheme.extendedColors
    return when (level) {
        BurnLevel.NO_BUDGET -> MaterialTheme.colorScheme.outline
        BurnLevel.NORMAL -> MaterialTheme.colorScheme.primary
        BurnLevel.APPROACHING -> ext.warningIndicator
        BurnLevel.EXHAUSTED -> ext.negativeIndicator
    }
}

@Composable
fun healthTone(health: BudgetHealth): TonePair = when (health) {
    BudgetHealth.NOT_SET -> StatusTones.neutral
    BudgetHealth.ON_TRACK -> StatusTones.positive
    BudgetHealth.APPROACHING, BudgetHealth.LOW -> StatusTones.warning
    BudgetHealth.EXCEEDED -> StatusTones.negative
}

/**
 * Short, supportive status words. Never judgemental; always paired with the figures. [remaining] distinguishes a
 * budget used exactly to the limit ("Fully used") from one that went over.
 */
fun healthLabel(health: BudgetHealth, remaining: Money? = null): String = when (health) {
    BudgetHealth.NOT_SET -> "Not set"
    BudgetHealth.ON_TRACK -> "On track"
    BudgetHealth.APPROACHING -> "Getting close"
    BudgetHealth.LOW -> "Almost used"
    BudgetHealth.EXCEEDED -> if (remaining != null && remaining.isZero) "Fully used" else "Over budget"
}

/**
 * The month's budget status in a word or two, the same on Home and Budget: Over budget, Ahead of pace (spending runs
 * faster than the month but hasn't gone over yet — the early warning worth acting on), On track, or Under budget
 * (spending slower than the month; also how a finished month within budget ends).
 */
enum class PaceVerdict(val label: String) {
    OVER("Over budget"),
    AHEAD("Ahead of pace"),
    ON_TRACK("On track"),
    UNDER("Under budget"),
}

fun paceVerdict(plan: BudgetPlan, position: PacePosition?, isCurrentMonth: Boolean): PaceVerdict = when {
    plan.remaining.isNegative -> PaceVerdict.OVER
    !isCurrentMonth -> PaceVerdict.UNDER
    position == PacePosition.AHEAD -> PaceVerdict.AHEAD
    position == PacePosition.BEHIND -> PaceVerdict.UNDER
    else -> PaceVerdict.ON_TRACK
}

fun verdictLabel(plan: BudgetPlan, position: PacePosition?, isCurrentMonth: Boolean): String =
    paceVerdict(plan, position, isCurrentMonth).label

/** Text colours for a [PaceVerdict] chip; the words carry the meaning, colour only reinforces it. */
@Composable
fun verdictTone(verdict: PaceVerdict): TonePair = when (verdict) {
    PaceVerdict.OVER -> StatusTones.negative
    PaceVerdict.AHEAD -> StatusTones.warning
    PaceVerdict.ON_TRACK, PaceVerdict.UNDER -> StatusTones.positive
}

/** One encouraging sentence for the month, from the plan's real figures. */
fun encouragement(plan: BudgetPlan): String = when (plan.health) {
    BudgetHealth.NOT_SET -> "Give your categories an amount and Lucento adds them up into your monthly budget."
    BudgetHealth.ON_TRACK -> "Comfortably within budget — nice and steady."
    BudgetHealth.APPROACHING -> "You’ve used most of this month’s budget. A little care keeps you on track."
    BudgetHealth.LOW -> "Only ${MoneyFormatter.format(plan.remaining)} left this month — small choices add up."
    BudgetHealth.EXCEEDED -> if (plan.remaining.isZero) {
        "This month’s budget is fully used. Every month is a fresh start."
    } else {
        "${MoneyFormatter.format(-plan.remaining)} over this month. It happens — next month is a fresh start."
    }
}
