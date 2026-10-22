package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.Insights
import com.lucento.app.domain.MonthAmount
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month-by-month bars for one currency, oldest left. The latest month is highlighted in brass because
 * it is still in progress. Heights are relative to the largest month; the exact figures are exposed
 * to accessibility services and shown by the caller as text, so the chart never stands alone.
 */
@Composable
fun MonthlyBarChart(
    months: List<MonthAmount>,
    modifier: Modifier = Modifier,
    height: Dp = 112.dp,
) {
    if (months.isEmpty()) return
    val colors = LucentoTheme.extendedColors
    val reducedMotion = LucentoTheme.reducedMotion
    val currency = months.first().amount.currencyCode
    val max = months.maxOf { it.amount.amountMinor }.let { Money(it, currency) }
    val fractions = months.map { Insights.fraction(it.amount, max) }

    val progress = remember(months) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(months) {
        if (!reducedMotion) progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    val locale = Locale.getDefault()
    val description = months.joinToString(separator = "; ") {
        "${it.month.month.getDisplayName(TextStyle.FULL, locale)}: ${MoneyFormatter.format(it.amount)}"
    }

    Column(modifier.clearAndSetSemantics { contentDescription = "Spending by month. $description" }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val count = fractions.size
            val gap = 10.dp.toPx()
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
            val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            val stub = 2.dp.toPx()
            fractions.forEachIndexed { index, fraction ->
                val left = index * (barWidth + gap)
                val isCurrent = index == count - 1
                // Empty months get a hairline stub so the time axis stays legible.
                val barHeight = if (fraction <= 0f) stub else (size.height * fraction * progress.value).coerceAtLeast(stub)
                drawRoundRect(
                    color = when {
                        fraction <= 0f -> colors.track
                        isCurrent -> colors.chartHighlight
                        else -> colors.chartBarMuted
                    },
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth()) {
            months.forEachIndexed { index, item ->
                Text(
                    item.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == months.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A thin proportional bar (0..1). Display-only; the exact amount is always printed beside it. */
@Composable
fun ShareBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = LucentoTheme.extendedColors.chartBar,
    trackColor: Color = LucentoTheme.extendedColors.track,
    height: Dp = 6.dp,
) {
    val reducedMotion = LucentoTheme.reducedMotion
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) tween(0) else tween(500, easing = FastOutSlowInEasing),
        label = "share",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}
