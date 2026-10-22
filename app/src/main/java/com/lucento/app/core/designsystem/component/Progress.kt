package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme

/**
 * Circular progress (0..1, display only) with centred content, e.g. "62%" of a budget used.
 * [description] is what TalkBack reads; the ring itself is decorative.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 8.dp,
    color: Color = LucentoTheme.extendedColors.success,
    trackColor: Color = LucentoTheme.extendedColors.track,
    content: @Composable () -> Unit = {},
) {
    val reducedMotion = LucentoTheme.reducedMotion
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) tween(0) else tween(700, easing = FastOutSlowInEasing),
        label = "ring",
    )
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/**
 * Donut of category shares. [values] are display fractions summing to ≤ 1 (from exact money via
 * `Insights.fraction`); the caller always lists exact amounts beside it and passes [description].
 */
@Composable
fun DonutChart(
    values: List<Float>,
    colors: List<Color>,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    strokeWidth: Dp = 22.dp,
    content: @Composable () -> Unit = {},
) {
    val reducedMotion = LucentoTheme.reducedMotion
    val track = LucentoTheme.extendedColors.track
    val progress = remember(values) { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(values) {
        if (!reducedMotion) progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val gap = if (values.count { it > 0f } > 1) 1.6f else 0f
            var start = -90f
            values.forEachIndexed { index, value ->
                val sweep = 360f * value * progress.value
                if (sweep > gap) {
                    drawArc(
                        colors[index % colors.size],
                        start + gap / 2f,
                        sweep - gap,
                        false,
                        Offset(inset, inset),
                        arcSize,
                        style = Stroke(stroke),
                    )
                }
                start += sweep
            }
        }
        content()
    }
}
