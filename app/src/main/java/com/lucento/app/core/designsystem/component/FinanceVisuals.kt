package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.AmountStyles
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.core.designsystem.theme.TonePair
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.FlowMonth
import com.lucento.app.domain.Insights
import com.lucento.app.domain.ScheduledFlow
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.padding
import com.lucento.app.core.money.CurrencyRules

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
    markerColor: Color = MaterialTheme.colorScheme.onSurface,
    /** A thin band either side of the marker so it reads over the fill; null on a hero, where the marker is white. */
    markerGap: Color? = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    val animated = animatedFraction(fraction.coerceIn(0f, 1f))
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
            if (markerGap != null) drawRect(markerGap, topLeft = Offset(x - 2.dp.toPx(), 0f), size = Size(4.dp.toPx(), size.height))
            drawRect(markerColor, topLeft = Offset(x - 1.dp.toPx(), 0f), size = Size(2.dp.toPx(), size.height))
        }
    }
}

/**
 * Income and expenses side by side for each month (oldest left), on round dashed gridlines labelled in whole units
 * ([Insights.axisTicks]) so a bar's size can be read. Income uses the positive colour, expenses the negative colour;
 * bars have rounded tops and grow in on first show (not with reduced motion). The current month's label is bold. A
 * legend and exact figures are always shown by the caller; TalkBack reads every month's figures from the text built here.
 */
@Composable
fun IncomeExpenseChart(
    months: List<FlowMonth>,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
) {
    val fmt = amountFormat()
    if (months.isEmpty()) return
    val colors = LucentoTheme.extendedColors
    val reducedMotion = LucentoTheme.reducedMotion
    val currency = months.first().income.currencyCode
    val maxMinor = months.maxOf { maxOf(it.income.amountMinor, it.expense.amountMinor) }
    val digits = CurrencyRules.fractionDigits(currency)
    val ticks = remember(maxMinor, digits) { Insights.axisTicks(maxMinor, digits) }
    var unit = 1L
    repeat(digits) { unit *= 10 }
    // Bars scale to the top gridline, so they read against the labels.
    val scale = Money(Math.multiplyExact(ticks.last().coerceAtLeast(1L), unit), currency)
    val progress = remember(months) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(months) {
        if (!reducedMotion) progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val locale = Locale.getDefault()
    val spoken = months.joinToString("; ") {
        "${it.month.month.getDisplayName(TextStyle.FULL, locale)}: income ${fmt.spoken(it.income)}, " +
            "expenses ${fmt.spoken(it.expense)}"
    }
    val grid = colors.track
    val incomeColor = colors.positive
    val expenseColor = colors.negative
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Hidden amounts hide the scale too: gridlines stay, their numbers would give the size away.
    val hidden = LocalAmountsHidden.current
    val labels = remember(ticks, locale, hidden) { ticks.map { if (hidden) "" else axisLabel(it, locale) } }
    val axisWidth = 44.dp
    Column(modifier.clearAndSetSemantics { contentDescription = "Income and expenses by month. $spoken" }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val left = axisWidth.toPx()
            val top = 6.dp.toPx()
            val plotHeight = size.height - top
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            // Gridlines and their labels, zero at the bottom.
            ticks.forEachIndexed { i, _ ->
                val y = size.height - plotHeight * i / (ticks.size - 1).coerceAtLeast(1)
                drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = if (i == 0) null else dash)
                val layout = measurer.measure(labels[i], labelStyle)
                drawText(layout, topLeft = Offset(left - layout.size.width - 8.dp.toPx(), (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
            }
            val count = months.size
            val width = size.width - left
            val groupGap = (if (count > 6) 8.dp else 16.dp).toPx()
            val pairGap = 3.dp.toPx()
            val groupWidth = ((width - groupGap * count) / count).coerceAtLeast(2f)
            val barWidth = ((groupWidth - pairGap) / 2f).coerceIn(1f, 28.dp.toPx())
            val inner = barWidth * 2 + pairGap
            val stub = 2.dp.toPx()
            months.forEachIndexed { index, m ->
                val groupLeft = left + groupGap / 2f + index * (groupWidth + groupGap) + (groupWidth - inner) / 2f
                listOf(m.income to incomeColor, m.expense to expenseColor).forEachIndexed { j, (amount, color) ->
                    val f = Insights.fraction(amount, scale)
                    if (f <= 0f) return@forEachIndexed
                    val h = (plotHeight * f * progress.value).coerceAtLeast(stub)
                    val radius = minOf(6.dp.toPx(), barWidth / 2f)
                    // Rounded top, square base on the zero line.
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(groupLeft + j * (barWidth + pairGap), size.height - h, groupLeft + j * (barWidth + pairGap) + barWidth, size.height),
                                topLeft = CornerRadius(radius, radius),
                                topRight = CornerRadius(radius, radius),
                            ),
                        )
                    }
                    drawPath(path, Brush.verticalGradient(listOf(color, color.copy(alpha = 0.82f)), startY = size.height - h, endY = size.height))
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth().padding(start = axisWidth)) {
            months.forEachIndexed { index, item ->
                val current = index == months.lastIndex
                Text(
                    item.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (current) FontWeight.Bold else null,
                    color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** An axis value in whole units, compact once it's long: 5,000 · 250K · 1.5M. */
private fun axisLabel(value: Long, locale: Locale): String = when {
    value >= 1_000_000 -> java.text.DecimalFormat("#.#", java.text.DecimalFormatSymbols.getInstance(locale)).format(value / 1_000_000.0) + "M"
    value >= 100_000 -> java.text.DecimalFormat("#.#", java.text.DecimalFormatSymbols.getInstance(locale)).format(value / 1_000.0) + "K"
    else -> java.text.NumberFormat.getIntegerInstance(locale).format(value)
}

/**
 * A scheduled amount's ink (owner, 2026-11-03): income green, expenses red, and transfers to a card or loan in neutral
 * ink — they move your own money and are neither. The amount is always signed too, so colour only reinforces.
 */
@Composable
fun scheduledFlowColor(flow: ScheduledFlow): Color = when (flow) {
    ScheduledFlow.INCOME -> LucentoTheme.extendedColors.positive
    ScheduledFlow.EXPENSE -> LucentoTheme.extendedColors.negative
    ScheduledFlow.TRANSFER -> MaterialTheme.colorScheme.onSurface
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

/**
 * Income, expenses and their net as three stat cards — the Transactions summary, reused wherever money in and out is
 * totalled (the Scheduled agenda). [expenses] is the amount spent (positive); it is shown with its minus sign. Income is
 * the positive tone and expenses the negative one; the net stays in ink with its own sign, so colour only reinforces.
 * Large text: income and expenses on one row and the net on the next; very large text, one card per row.
 */
@Composable
fun FlowStatTiles(
    income: Money,
    expenses: Money,
    net: Money,
    netLabel: String,
    modifier: Modifier = Modifier,
) {
    val fmt = amountFormat()
    val ext = LucentoTheme.extendedColors
    val spent = -expenses
    val incomeTile: @Composable (Modifier) -> Unit = { m ->
        StatTile(
            "Income", Icons.Filled.SouthWest, TonePair(ext.positiveContainer, ext.success),
            description = "Income ${fmt.spokenSigned(income, showPlus = true)}", modifier = m,
        ) { MoneyText(money = income, style = StatFigureStyle, showPlus = true, color = ext.positive, fractionScale = 1f) }
    }
    val spentTile: @Composable (Modifier) -> Unit = { m ->
        StatTile(
            "Expenses", Icons.Filled.NorthEast, TonePair(ext.negativeContainer, ext.negativeIndicator),
            description = "Expenses ${fmt.spokenSigned(spent)}", modifier = m,
        ) { MoneyText(money = spent, style = StatFigureStyle, color = ext.negative, fractionScale = 1f) }
    }
    val netTile: @Composable (Modifier) -> Unit = { m ->
        StatTile(
            netLabel,
            if (net.isNegative) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
            TonePair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary),
            description = "$netLabel ${fmt.spokenSigned(net, showPlus = true)}",
            modifier = m,
        ) { MoneyText(money = net, style = StatFigureStyle, showPlus = true, color = MaterialTheme.colorScheme.onSurface, fractionScale = 1f) }
    }
    val fontScale = LocalDensity.current.fontScale
    when {
        fontScale >= 1.5f -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            incomeTile(Modifier.fillMaxWidth())
            spentTile(Modifier.fillMaxWidth())
            netTile(Modifier.fillMaxWidth())
        }
        fontScale >= 1.3f -> Column(modifier) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                incomeTile(Modifier.weight(1f).fillMaxHeight())
                spentTile(Modifier.weight(1f).fillMaxHeight())
            }
            Spacer(Modifier.height(Spacing.sm))
            netTile(Modifier.fillMaxWidth())
        }
        else -> Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            incomeTile(Modifier.weight(1f).fillMaxHeight())
            spentTile(Modifier.weight(1f).fillMaxHeight())
            netTile(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Stat-card figures: 15sp Bold, tabular; they shrink to fit rather than wrap. */
private val StatFigureStyle = AmountStyles.row.copy(fontWeight = FontWeight.Bold)
