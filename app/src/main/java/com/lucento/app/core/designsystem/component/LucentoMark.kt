package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Lucento mark: an open lens with a single point of light. Matches the launcher icon geometry
 * (ring radius 21/108, stroke 6/108, 300° sweep starting at -15°, dot at -45°).
 *
 * When [animate] is true the ring draws itself once and the point of light settles in —
 * a brief, purposeful brand moment on first launch.
 */
@Composable
fun LucentoMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    ringColor: Color = LucentoTheme.extendedColors.brandMark,
    lightColor: Color = LucentoTheme.extendedColors.accent,
    animate: Boolean = true,
) {
    val sweep = remember { Animatable(if (animate) 0f else 1f) }
    val dot = remember { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(animate) {
        if (animate) {
            sweep.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
            dot.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Lucento" },
    ) {
        val unit = this.size.minDimension / 66f // the mark occupies the 66/108 safe zone
        val radius = 21f * unit
        val stroke = 6f * unit
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Compose arcs start at 3 o'clock and sweep clockwise, like the vector path.
        drawArc(
            color = ringColor,
            startAngle = -15f,
            sweepAngle = 300f * sweep.value,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        val angle = Math.toRadians(-45.0)
        val dotCenter = Offset(
            x = center.x + (radius * cos(angle)).toFloat(),
            y = center.y + (radius * sin(angle)).toFloat(),
        )
        drawCircle(color = lightColor, radius = 4.4f * unit * dot.value, center = dotCenter)
    }
}
