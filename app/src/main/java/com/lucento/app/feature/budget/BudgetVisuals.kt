package com.lucento.app.feature.budget

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lucento.app.core.designsystem.component.StatusTones
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.BudgetHealth
import com.lucento.app.domain.BudgetPlan
import com.lucento.app.domain.BurnLevel

/** One colour per budget state, shared by the hero card, the burn bar and the Budget screen. */
@Composable
fun healthColor(health: BudgetHealth): Color {
    val ext = LucentoTheme.extendedColors
    return when (health) {
        BudgetHealth.NOT_SET -> MaterialTheme.colorScheme.outline
        BudgetHealth.ON_TRACK -> ext.success
        BudgetHealth.APPROACHING, BudgetHealth.LOW -> ext.warning
        BudgetHealth.EXCEEDED -> ext.negative
    }
}

@Composable
fun burnColor(level: BurnLevel): Color {
    val ext = LucentoTheme.extendedColors
    return when (level) {
        BurnLevel.NO_BUDGET -> MaterialTheme.colorScheme.outline
        BurnLevel.NORMAL -> MaterialTheme.colorScheme.primary
        BurnLevel.APPROACHING -> ext.warning
        BurnLevel.EXHAUSTED -> ext.negative
    }
}

@Composable
fun healthTone(health: BudgetHealth): TonePair = when (health) {
    BudgetHealth.NOT_SET -> StatusTones.neutral
    BudgetHealth.ON_TRACK -> StatusTones.positive
    BudgetHealth.APPROACHING, BudgetHealth.LOW -> StatusTones.warning
    BudgetHealth.EXCEEDED -> StatusTones.negative
}

/** Short, supportive status words. Never judgemental; always paired with the figures. */
fun healthLabel(health: BudgetHealth): String = when (health) {
    BudgetHealth.NOT_SET -> "Not set"
    BudgetHealth.ON_TRACK -> "On track"
    BudgetHealth.APPROACHING -> "Getting close"
    BudgetHealth.LOW -> "Almost used"
    BudgetHealth.EXCEEDED -> "Over budget"
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
