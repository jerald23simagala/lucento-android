package com.lucento.app.core.designsystem.component

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The finish on every wallet face (tester round 3: "metallic or glassmorphism, like real bank cards"): light falling
 * from the top-left, a soft diagonal sheen like brushed metal or a laminated card, gentle depth towards the bottom-right
 * and a thin glass edge that is brightest along the top. Drawn over the face's own colour and under its text, at low
 * alpha, so it never costs the text its contrast. Static, so reduced motion needs nothing; purely decorative.
 *
 * [ink] is the face's text colour: a light face (dark ink) takes a gentler finish, so it doesn't turn grey.
 */
fun DrawScope.drawCardFinish(ink: Color, cornerRadius: Float) {
    val s = if (ink.luminance() < 0.4f) 0.55f else 1f
    val w = size.width
    val h = size.height
    // Depth: a touch darker towards the bottom-right corner.
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.55f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.16f * s),
            start = Offset.Zero,
            end = Offset(w, h),
        ),
    )
    // Light from the top-left.
    val glowCenter = Offset(w * 0.1f, -h * 0.15f)
    val glowRadius = w * 0.85f
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f * s), Color.Transparent), center = glowCenter, radius = glowRadius),
        radius = glowRadius,
        center = glowCenter,
    )
    // The sheen: one soft diagonal band, like light across brushed metal.
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.36f to Color.Transparent,
            0.46f to Color.White.copy(alpha = 0.11f * s),
            0.52f to Color.White.copy(alpha = 0.05f * s),
            0.62f to Color.Transparent,
            1f to Color.Transparent,
            start = Offset(0f, 0f),
            end = Offset(w, h * 1.4f),
        ),
    )
    // The glass edge: a hairline, brightest along the top.
    val stroke = 1.dp.toPx()
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f * s), Color.White.copy(alpha = 0.08f * s))),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(w - stroke, h - stroke),
        cornerRadius = CornerRadius(cornerRadius - stroke / 2),
        style = Stroke(stroke),
    )
}
