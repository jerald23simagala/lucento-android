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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.LucentoTheme

/**
 * The Lucento mark: a rounded blue "L" with a sun-yellow leaf growing from its corner — clarity (the
 * clean letterform) with a sign of growth (the leaf). Same geometry as the launcher icon, drawn on a
 * 100-unit grid.
 *
 * When [animate] is true the leaf grows in once — a brief brand moment on first launch.
 */
@Composable
fun LucentoMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    markColor: Color = LucentoTheme.extendedColors.brandMark,
    leafColor: Color = LucentoTheme.extendedColors.brandLeaf,
    animate: Boolean = true,
) {
    val reducedMotion = LucentoTheme.reducedMotion
    val play = animate && !reducedMotion
    val stem = remember { Animatable(if (play) 0f else 1f) }
    val leaf = remember { Animatable(if (play) 0f else 1f) }

    LaunchedEffect(play) {
        if (play) {
            stem.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
            leaf.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Lucento" },
    ) {
        val u = this.size.minDimension / 100f
        val radius = CornerRadius(10f * u, 10f * u)
        // Stem grows downward, then the foot extends to the right.
        val stemHeight = 72f * u * stem.value.coerceAtLeast(0.01f)
        drawRoundRect(
            color = markColor,
            topLeft = Offset(20f * u, 14f * u),
            size = Size(21f * u, stemHeight),
            cornerRadius = radius,
        )
        if (stem.value > 0.6f) {
            val footWidth = 58f * u * ((stem.value - 0.6f) / 0.4f)
            drawRoundRect(
                color = markColor,
                topLeft = Offset(20f * u, 65f * u),
                size = Size(footWidth.coerceAtLeast(21f * u), 21f * u),
                cornerRadius = radius,
            )
        }
        val base = Offset(50f * u, 58f * u)
        val leafPath = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(48f * u, 38f * u, 62f * u, 22f * u, 84f * u, 16f * u)
            cubicTo(84f * u, 38f * u, 72f * u, 56f * u, base.x, base.y)
            close()
        }
        if (leaf.value > 0f) {
            scale(scale = leaf.value, pivot = base) {
                drawPath(leafPath, color = leafColor)
            }
        }
    }
}
