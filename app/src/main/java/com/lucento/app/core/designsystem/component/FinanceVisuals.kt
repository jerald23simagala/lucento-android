package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.lucento.app.domain.FlowMonth
import com.lucento.app.domain.Insights
import java.time.format.TextStyle
import java.util.Locale

/**
 * A consumption bar: how much of a budget is used ([fraction], 0..1, display only), with an optional thin
 * marker at [markerFraction] (how far through the period today is), so spending can be read against time.
 * [description] is what TalkBack reads; the caller always prints the figures as text too.
 */
@Composable
fun ConsumptionBar(
    fraction: Float,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    markerFraction: Float? = null,
    height: Dp = 10.dp,
    trackColor: Color = LucentoTheme.extendedColors.track,
) {
    val reducedMotion = LucentoTheme.reducedMotion
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) tween(0) else tween(700, easing = FastOutSlowInEasing),
        label = "consumption",
    )
    val markerColor = MaterialTheme.colorScheme.onSurface
    val gap = MaterialTheme.colorScheme.surfaceContainerLowest
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height + 8.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val barHeight = height.toPx()
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        drawRoundRect(trackColor, topLeft = Offset(0f, top), size = Size(size.width, barHeight), cornerRadius = radius)
        if (animated > 0f) {
            val width = (size.width * animated).coerceAtLeast(barHeight)
            drawRoundRect(color, topLeft = Offset(0f, top), size = Size(width, barHeight), cornerRadius = radius)
        }
        if (markerFraction != null && markerFraction in 0f..1f) {
            val x = (size.width * markerFraction).coerceIn(1.5.dp.toPx(), size.width - 1.5.dp.toPx())
            // A hairline gap either side keeps the marker legible over the filled part.
            drawRect(gap, topLeft = Offset(x - 2.dp.toPx(), 0f), size = Size(4.dp.toPx(), size.height))
            drawRect(markerColor, topLeft = Offset(x - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
        }
    }
}

/**
 * Income and expenses side by side for each month (oldest left), scaled to the largest value. Income uses the
 * positive colour, expenses the negative colour; a legend and exact figures are always shown by the caller, and
 * TalkBack reads every month's figures from [description]-style text built here.
 */
@Composable
fun IncomeExpenseChart(
    months: List<FlowMonth>,
    modifier: Modifier = Modifier,
    height: Dp = 148.dp,
) {
    if (months.isEmpty()) return
    val colors = LucentoTheme.extendedColors
    val reducedMotion = LucentoTheme.reducedMotion
    val currency = months.first().income.currencyCode
    val max = months.maxOf { maxOf(it.income.amountMinor, it.expense.amountMinor) }.let { Money(it, currency) }
    val progress = remember(months) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(months) {
        if (!reducedMotion) progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val locale = Locale.getDefault()
    val spoken = months.joinToString("; ") {
        "${it.month.month.getDisplayName(TextStyle.FULL, locale)}: income ${MoneyFormatter.format(it.income)}, " +
            "expenses ${MoneyFormatter.format(it.expense)}"
    }
    val track = colors.track
    val incomeColor = colors.positive
    val expenseColor = colors.negative
    Column(modifier.clearAndSetSemantics { contentDescription = "Income and expenses by month. $spoken" }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val count = months.size
            val groupGap = (if (count > 6) 8.dp else 14.dp).toPx()
            val pairGap = 3.dp.toPx()
            val groupWidth = ((size.width - groupGap * (count - 1)) / count).coerceAtLeast(2f)
            val barWidth = ((groupWidth - pairGap) / 2f).coerceAtLeast(1f)
            val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            val stub = 2.dp.toPx()
            // Baseline.
            drawRect(track, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            months.forEachIndexed { index, m ->
                val left = index * (groupWidth + groupGap)
                listOf(m.income to incomeColor, m.expense to expenseColor).forEachIndexed { j, (amount, color) ->
                    val f = Insights.fraction(amount, max)
                    val h = if (f <= 0f) stub else (size.height * f * progress.value).coerceAtLeast(stub)
                    drawRoundRect(
                        color = if (f <= 0f) track else color,
                        topLeft = Offset(left + j * (barWidth + pairGap), size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = radius,
                    )
                }
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

/** A small coloured dot and label, for chart legends. */
@Composable
fun LegendKey(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(Spacing.xs + 2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/**
 * A horizontal bar split into proportional segments ([weights] are non-negative display weights, e.g. minor
 * units of each asset type), drawn relative to [scaleTo] so two bars (assets and liabilities) share one scale.
 */
@Composable
fun CompositionBar(
    weights: List<Long>,
    colors: List<Color>,
    scaleTo: Long,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val reducedMotion = LucentoTheme.reducedMotion
    val track = LucentoTheme.extendedColors.track
    val progress = remember(weights, scaleTo) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(weights, scaleTo) {
        if (!reducedMotion) progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .clearAndSetSemantics { contentDescription = description },
    ) {
        drawRect(track)
        if (scaleTo <= 0L) return@Canvas
        val gap = 2.dp.toPx()
        var x = 0f
        weights.forEachIndexed { i, w ->
            if (w <= 0L) return@forEachIndexed
            // Proportions in Double are display-only; money is never computed from them.
            val width = (size.width * (w.toDouble() / scaleTo.toDouble()).toFloat() * progress.value).coerceAtLeast(0f)
            if (width > 0f) {
                drawRect(colors[i % colors.size], topLeft = Offset(x, 0f), size = Size((width - gap).coerceAtLeast(1f), size.height))
                x += width
            }
        }
    }
}

/**
 * Net-flow indicator: the bundled Lottie glyph for "up", "down" or "flat" (one short, one-shot draw), with a
 * static tinted icon when motion is reduced or the file cannot load. Decorative: the signed amount beside it
 * carries the meaning.
 */
@Composable
fun NetFlowIndicator(marker: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = LucentoTheme.extendedColors
    val (container, content, icon) = when (marker) {
        "up" -> Triple(colors.positiveContainer, colors.positive, Icons.AutoMirrored.Filled.TrendingUp)
        "down" -> Triple(colors.negativeContainer, colors.negative, Icons.AutoMirrored.Filled.TrendingDown)
        else -> Triple(colors.track, MaterialTheme.colorScheme.onSurfaceVariant, Icons.AutoMirrored.Filled.TrendingFlat)
    }
    val still: @Composable () -> Unit = {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.27f)).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.55f))
        }
    }
    if (LucentoTheme.reducedMotion) {
        Box(modifier) { still() }
        return
    }
    // Re-keyed on the marker so a change of direction plays the new glyph once.
    androidx.compose.runtime.key(marker) {
        LucentoLottieAnimation(
            animation = LucentoAnimation.NetFlow,
            modifier = modifier.size(size),
            marker = marker,
            iterations = 1,
            fallback = still,
        )
    }
}

/** Evenly spaced row helper for small legends. */
@Composable
fun LegendRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) { content() }
}
