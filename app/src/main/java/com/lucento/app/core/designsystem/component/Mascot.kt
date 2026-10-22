package com.lucento.app.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoExtendedColors
import com.lucento.app.core.designsystem.theme.LucentoTheme

/** Expression for the mascot; kept small on purpose — Lucento's friend, not a cartoon cast. */
enum class MascotMood { Happy, Curious }

/**
 * Lucento's original mascot pair, drawn entirely in vectors (no image assets, no downloads):
 * the **Sun** in the light theme and its night-time counterpart, the **Moon**, in the dark theme. They
 * share one face — confident brows, bright eyes, a restrained smile — so they read as the same
 * character at different times of day.
 *
 * Use it small and secondary on dashboards; it may be larger in onboarding and empty states. A gentle
 * float plays when [animate] is true and the system has not asked to remove animations.
 * Decorative by default (no accessibility label) unless [contentDescription] is given.
 */
@Composable
fun LucentoMascot(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animate: Boolean = true,
    mood: MascotMood = MascotMood.Happy,
    night: Boolean = LucentoTheme.isDark,
    contentDescription: String? = null,
) {
    val colors = LucentoTheme.extendedColors
    val play = animate && !LucentoTheme.reducedMotion
    val float = if (play) {
        val transition = rememberInfiniteTransition(label = "mascot")
        val value by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2800), RepeatMode.Reverse),
            label = "mascot-float",
        )
        value
    } else {
        0f
    }
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Canvas(modifier.size(size).then(semantics)) {
        val u = this.size.minDimension / 100f
        translate(top = float * 1.6f * u) {
            if (night) drawMoon(u, colors, mood) else drawSun(u, colors, mood)
        }
    }
}

private val MascotCenter = Offset(50f, 52f)

private fun DrawScope.drawSun(u: Float, c: LucentoExtendedColors, mood: MascotMood) {
    val center = Offset(MascotCenter.x * u, MascotCenter.y * u)
    // Twelve rounded rays, alternating long and short, slightly warmer than the face.
    for (i in 0 until 12) {
        val long = i % 2 == 0
        rotate(degrees = i * 30f, pivot = center) {
            val h = (if (long) 15f else 11f) * u
            drawRoundRect(
                color = c.mascotShade,
                topLeft = Offset(center.x - 4.5f * u, center.y - 46f * u + (if (long) 0f else 3f * u)),
                size = Size(9f * u, h),
                cornerRadius = CornerRadius(4.5f * u, 4.5f * u),
            )
        }
    }
    // Face with a soft, flat inner shade for depth (no gradient).
    drawCircle(color = c.mascotShade, radius = 31f * u, center = center)
    drawCircle(color = c.mascotBody, radius = 29f * u, center = Offset(center.x - 1.2f * u, center.y - 1.2f * u))
    drawFace(u, c, mood, sleepyEyes = false)
}

private fun DrawScope.drawMoon(u: Float, c: LucentoExtendedColors, mood: MascotMood) {
    val center = Offset(MascotCenter.x * u, MascotCenter.y * u)
    drawCircle(color = c.mascotShade, radius = 33f * u, center = center)
    drawCircle(color = c.mascotBody, radius = 31f * u, center = Offset(center.x - 1.2f * u, center.y - 1.2f * u))
    // Craters, kept away from the face.
    drawCircle(color = c.mascotShade, radius = 4.5f * u, center = Offset(68f * u, 32f * u))
    drawCircle(color = c.mascotShade, radius = 3f * u, center = Offset(31f * u, 72f * u))
    drawCircle(color = c.mascotShade, radius = 2.2f * u, center = Offset(72f * u, 70f * u))
    // Two small sparkles in sun yellow — the Sun's light, reflected.
    drawSparkle(Offset(14f * u, 18f * u), 6f * u, c.accent)
    drawSparkle(Offset(88f * u, 12f * u), 4f * u, c.accent)
    drawFace(u, c, mood, sleepyEyes = true)
}

private fun DrawScope.drawFace(u: Float, c: LucentoExtendedColors, mood: MascotMood, sleepyEyes: Boolean) {
    val ink = c.mascotFace
    val stroke = Stroke(width = 2.6f * u, cap = StrokeCap.Round)
    // Brows: level and confident; the right one lifts a little when curious.
    val lift = if (mood == MascotMood.Curious) 2.5f * u else 0f
    drawLine(ink, Offset(34f * u, 39f * u), Offset(43f * u, 37f * u), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
    drawLine(ink, Offset(57f * u, 37f * u - lift), Offset(66f * u, 39f * u - lift), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
    if (sleepyEyes) {
        // Contented, half-closed eyes for the night.
        drawArc(ink, 200f, 140f, false, Offset(34f * u, 44f * u), Size(10f * u, 8f * u), style = stroke)
        drawArc(ink, 200f, 140f, false, Offset(56f * u, 44f * u), Size(10f * u, 8f * u), style = stroke)
    } else {
        drawOval(ink, Offset(36f * u, 42f * u), Size(7f * u, 9f * u))
        drawOval(ink, Offset(57f * u, 42f * u), Size(7f * u, 9f * u))
        drawCircle(Color.White, 1.5f * u, Offset(38.4f * u, 44.6f * u))
        drawCircle(Color.White, 1.5f * u, Offset(59.4f * u, 44.6f * u))
    }
    // Cheeks and a restrained, slightly asymmetric smile.
    drawCircle(c.mascotCheek.copy(alpha = 0.45f), 4f * u, Offset(31f * u, 58f * u))
    drawCircle(c.mascotCheek.copy(alpha = 0.45f), 4f * u, Offset(69f * u, 58f * u))
    drawArc(ink, 25f, 125f, false, Offset(41f * u, 50f * u), Size(19f * u, 13f * u), style = stroke)
}

private fun DrawScope.drawSparkle(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        cubicTo(center.x, center.y, center.x, center.y, center.x + r, center.y)
        cubicTo(center.x, center.y, center.x, center.y, center.x, center.y + r)
        cubicTo(center.x, center.y, center.x, center.y, center.x - r, center.y)
        cubicTo(center.x, center.y, center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, color = color)
}
