package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Where each point sits between the lowest (0) and highest (1) of [values], for drawing only — never money. A flat
 * series sits in the middle; negative values are handled like any other.
 */
internal fun trendHeights(values: List<Long>): List<Float> {
    if (values.isEmpty()) return emptyList()
    val min = values.min().toDouble()
    val max = values.max().toDouble()
    if (max == min) return values.map { 0.5f }
    return values.map { ((it.toDouble() - min) / (max - min)).toFloat() }
}

/**
 * A small line chart of one series, oldest left: the line, a faint area under it (18 %), a dot on the latest point and
 * the [labels] (month names) beneath, the last one emphasised. Scaled to its own minimum and maximum, so it shows the
 * shape of the change; the caller prints the figures. [description] (first and last values in words) is what TalkBack
 * reads. Draws itself in left to right unless motion is reduced.
 */
@Composable
fun TrendLine(
    points: List<Long>,
    labels: List<String>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    currentLabelColor: Color = MaterialTheme.colorScheme.onSurface,
    /** The surface behind the chart, used to ring the end dot so it reads over the line. */
    background: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    width: Dp = 150.dp,
    height: Dp = 60.dp,
) {
    if (points.size < 2) return
    val reducedMotion = LucentoTheme.reducedMotion
    val progress = remember(points) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(points) {
        if (!reducedMotion) progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val heights = remember(points) { trendHeights(points) }
    Column(modifier.width(width).clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.width(width).height(height)) {
            val inset = 4.dp.toPx()
            val step = size.width / (heights.size - 1)
            val positions = heights.mapIndexed { i, h -> Offset(i * step, inset + (1f - h) * (size.height - inset * 2)) }
            val line = Path().apply {
                moveTo(positions.first().x, positions.first().y)
                positions.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(positions.last().x, size.height)
                lineTo(positions.first().x, size.height)
                close()
            }
            clipRect(right = size.width * progress.value + inset) {
                drawPath(area, color.copy(alpha = 0.18f))
                drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (progress.value >= 1f) {
                drawCircle(background, radius = 5.5.dp.toPx(), center = positions.last())
                drawCircle(color, radius = 3.5.dp.toPx(), center = positions.last())
            }
        }
        if (labels.size == points.size) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.width(width), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEachIndexed { index, label ->
                    val current = index == labels.lastIndex
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium),
                        color = if (current) currentLabelColor else labelColor,
                        maxLines = 1,
                    )
                }
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
    val animated = animatedFraction(fraction.coerceIn(0f, 1f), durationMillis = 600)
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
