package com.lucento.app.feature.awards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.theme.AwardArtwork
import com.lucento.app.domain.Achievement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What makes each award recognisable at a glance: its engraved emblem and its ribbon (dye plus centre stripe). */
@Immutable
data class AwardArt(val emblem: ImageVector, val ribbon: Color, val stripe: Color)

fun awardArt(achievement: Achievement): AwardArt = when (achievement) {
    Achievement.MONEY_KEEPER -> AwardArt(Icons.Filled.AccountBalanceWallet, AwardArtwork.RibbonBlue, AwardArtwork.StripeGold)
    Achievement.BUDGET_BUILDER -> AwardArt(Icons.Filled.PieChart, AwardArtwork.RibbonTangerine, AwardArtwork.StripeIvory)
    Achievement.FIRST_STEP -> AwardArt(Icons.AutoMirrored.Filled.DirectionsWalk, AwardArtwork.RibbonGreen, AwardArtwork.StripeIvory)
    Achievement.GETTING_CONSISTENT -> AwardArt(Icons.Filled.EventRepeat, AwardArtwork.RibbonPlum, AwardArtwork.StripeGold)
    Achievement.FINANCIAL_EXPLORER -> AwardArt(Icons.Filled.Explore, AwardArtwork.RibbonSky, AwardArtwork.StripeNavy)
    Achievement.GOAL_SETTER -> AwardArt(Icons.Filled.Flag, AwardArtwork.RibbonSun, AwardArtwork.StripeNavy)
    Achievement.SECURED -> AwardArt(Icons.Filled.Shield, AwardArtwork.RibbonGreen, AwardArtwork.StripeGold)
}

/** A medal is [size] wide and this much taller, the ribbon above the disc. */
const val MedalAspect = 1.22f

/** The disc's outer radius as a share of the medal's width; its centre sits one radius above the bottom edge. */
internal const val MedalRadius = 0.44f

/**
 * One award, drawn as a medal on a neck ribbon. Earned: struck gold with the award's emblem engraved and its ribbon in
 * colour. Not yet: an empty outline in the theme's quiet tones — the place the medal will hang — with a ring showing
 * [progress] (0–1) for awards that take several steps. [shine] (0–1) sweeps a highlight across the gold once; outside
 * that range there is none. Decorative: callers say what it is in words.
 */
@Composable
fun AwardMedal(
    achievement: Achievement,
    earned: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    progress: Float? = null,
    shine: Float = -1f,
) {
    val art = awardArt(achievement)
    val painter = rememberVectorPainter(art.emblem)
    val colors = MaterialTheme.colorScheme
    val ghost = GhostTones(
        ribbon = colors.surfaceContainerHigh,
        face = colors.surfaceContainer,
        line = colors.outline,
        emblem = colors.onSurfaceVariant.copy(alpha = 0.6f),
        progress = colors.primary,
    )
    Canvas(modifier.size(width = size, height = size * MedalAspect)) {
        val w = this.size.width
        val r = w * MedalRadius
        val center = Offset(w / 2f, this.size.height - r)
        drawRibbon(w, center.y, if (earned) art.ribbon else ghost.ribbon, if (earned) art.stripe else null, shaded = earned)
        if (earned) {
            drawGoldDisc(center, r, painter, shine)
        } else {
            drawGhostDisc(center, r, painter, ghost, progress)
        }
    }
}

private data class GhostTones(val ribbon: Color, val face: Color, val line: Color, val emblem: Color, val progress: Color)

/** Two straps meeting behind the disc in a V, with a centre stripe when [stripe] is set; [shaded] adds depth. */
private fun DrawScope.drawRibbon(w: Float, bottom: Float, dye: Color, stripe: Color?, shaded: Boolean) {
    val strapWidth = w * 0.25f
    val topInset = w * 0.08f
    fun strap(topLeft: Float, bottomLeft: Float, from: Float, to: Float) = Path().apply {
        moveTo(topLeft + strapWidth * from, 0f)
        lineTo(topLeft + strapWidth * to, 0f)
        lineTo(bottomLeft + strapWidth * to, bottom)
        lineTo(bottomLeft + strapWidth * from, bottom)
        close()
    }
    val bottomLeft = w / 2f - strapWidth / 2f
    val farTop = w - topInset - strapWidth
    val nearTop = topInset
    listOf(farTop to true, nearTop to false).forEach { (top, far) ->
        drawPath(strap(top, bottomLeft, 0f, 1f), dye)
        if (stripe != null) drawPath(strap(top, bottomLeft, 0.38f, 0.62f), stripe)
        // Depth (real silk only — an empty outline stays flat): the far strap sits in shadow, both darken towards the disc.
        if (shaded) drawPath(
            strap(top, bottomLeft, 0f, 1f),
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = if (far) 0.18f else 0f),
                1f to Color.Black.copy(alpha = if (far) 0.32f else 0.16f),
                startY = 0f,
                endY = bottom,
            ),
        )
    }
}

private fun DrawScope.drawGoldDisc(center: Offset, r: Float, emblem: VectorPainter, shine: Float) {
    val metal = Brush.linearGradient(
        0f to AwardArtwork.GoldHighlight,
        0.35f to AwardArtwork.GoldLight,
        0.7f to AwardArtwork.Gold,
        1f to AwardArtwork.GoldDeep,
        start = Offset(center.x - r, center.y - r),
        end = Offset(center.x + r, center.y + r),
    )
    // Bail: the small loop the ribbon passes through.
    drawCircle(AwardArtwork.GoldEdge, radius = r * 0.16f, center = Offset(center.x, center.y - r * 0.98f))
    drawCircle(AwardArtwork.GoldLight, radius = r * 0.09f, center = Offset(center.x, center.y - r * 0.98f))
    // Milled rim: a ring of small lobes, lit from the top left like the face.
    val lobes = 26
    val lobe = r * 0.11f
    val rim = Brush.linearGradient(
        0f to AwardArtwork.GoldLight,
        1f to AwardArtwork.GoldEdge,
        start = Offset(center.x - r, center.y - r),
        end = Offset(center.x + r, center.y + r),
    )
    drawCircle(rim, radius = r - lobe * 0.8f, center = center)
    repeat(lobes) { i ->
        val a = 2 * PI * i / lobes
        drawCircle(rim, radius = lobe, center = Offset(center.x + (r - lobe) * cos(a).toFloat(), center.y + (r - lobe) * sin(a).toFloat()))
    }
    val face = r * 0.84f
    drawCircle(metal, radius = face, center = center)
    // Struck field: slightly sunken (lit from the opposite side) inside an engraved ring.
    val field = r * 0.68f
    drawCircle(
        Brush.linearGradient(
            0f to AwardArtwork.GoldDeep,
            0.55f to AwardArtwork.Gold,
            1f to AwardArtwork.GoldLight,
            start = Offset(center.x - field, center.y - field),
            end = Offset(center.x + field, center.y + field),
        ),
        radius = field,
        center = center,
    )
    drawCircle(AwardArtwork.Engraving.copy(alpha = 0.5f), radius = field, center = center, style = Stroke(width = r * 0.035f))
    drawCircle(AwardArtwork.GoldHighlight.copy(alpha = 0.7f), radius = field + r * 0.04f, center = center, style = Stroke(width = r * 0.02f))
    // Emblem, embossed: a light edge one step down-right, the engraving on top.
    val emblemSize = r * 0.8f
    val topLeft = Offset(center.x - emblemSize / 2f, center.y - emblemSize / 2f)
    drawEmblem(emblem, topLeft + Offset(r * 0.03f, r * 0.03f), emblemSize, AwardArtwork.GoldHighlight.copy(alpha = 0.85f))
    drawEmblem(emblem, topLeft, emblemSize, AwardArtwork.Engraving)
    if (shine in 0f..1f) {
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, face)) }
        clipPath(disc) {
            val x = center.x - face * 1.8f + shine * face * 3.6f
            rotate(degrees = 22f, pivot = center) {
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.65f),
                        1f to Color.Transparent,
                        startX = x - face * 0.28f,
                        endX = x + face * 0.28f,
                    ),
                    topLeft = Offset(x - face * 0.28f, center.y - face * 1.6f),
                    size = Size(face * 0.56f, face * 3.2f),
                )
            }
        }
    }
}

private fun DrawScope.drawGhostDisc(center: Offset, r: Float, emblem: VectorPainter, tones: GhostTones, progress: Float?) {
    drawCircle(tones.face, radius = r * 0.92f, center = center)
    val ring = r * 0.92f
    val stroke = r * 0.07f
    drawCircle(
        tones.line,
        radius = ring,
        center = center,
        style = Stroke(width = r * 0.03f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.12f, r * 0.09f))),
    )
    if (progress != null && progress > 0f) {
        drawArc(
            tones.progress,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(center.x - ring, center.y - ring),
            size = Size(ring * 2, ring * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
    val emblemSize = r * 0.8f
    drawEmblem(emblem, Offset(center.x - emblemSize / 2f, center.y - emblemSize / 2f), emblemSize, tones.emblem)
}

private fun DrawScope.drawEmblem(painter: VectorPainter, topLeft: Offset, size: Float, tint: Color) {
    translate(topLeft.x, topLeft.y) {
        with(painter) { draw(Size(size, size), colorFilter = ColorFilter.tint(tint)) }
    }
}
