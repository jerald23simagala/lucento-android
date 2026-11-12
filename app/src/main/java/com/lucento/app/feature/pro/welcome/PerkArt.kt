package com.lucento.app.feature.pro.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.lucento.app.core.designsystem.theme.AwardArtwork
import com.lucento.app.core.designsystem.theme.ThemeSpec
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.domain.entitlement.ProTourChapter
import com.lucento.app.domain.entitlement.ProTourStop

/** The art band's drawing space: each scene is drawn in 300 × 116 and scaled to the card's width. */
internal const val PerkArtAspect = 300f / 116f

/**
 * A card's art (owner's pick, 2026-11-12: "Scenes"): one small illustrated scene per thing Pro opened, in Lucento's
 * colours on the card back's blue, with a glow in its chapter's colour. Original drawings made for these cards (design
 * study: lucento-studio `lab/pro-reveal-art.html`). Decorative: the card's words say everything the picture shows.
 */
@Composable
internal fun PerkArt(stop: ProTourStop, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val family = MaterialTheme.typography.labelLarge.fontFamily
    val paths = remember { HashMap<String, Path>() }
    // The three Pro themes as their own colours (the scene shows them, so it follows them).
    val themes = remember {
        listOf(ThemeId.GROVE, ThemeId.MONO, ThemeId.AURORA).map { ThemeSpec.of(it).colors(dark = true).let { c -> c.scheme.primary to c.extended.accent } }
    }
    // Clipped: the rings rise from below the band and must not spill onto the card's face.
    Canvas(modifier.clipToBounds().clearAndSetSemantics {}) {
        drawRect(Brush.linearGradient(listOf(AwardArtwork.CardBlue, AwardArtwork.CardBlueDeep), start = Offset.Zero, end = Offset(size.width * 0.6f, size.height * 1.6f)))
        drawRect(Brush.radialGradient(listOf(glow(stop.chapter), Color.Transparent), center = Offset(size.width / 2f, size.height * 1.25f), radius = size.width * 0.62f))
        rings(this)
        val k = size.width / 300f
        scale(k, k, pivot = Offset.Zero) {
            Pen(this, paths, measurer, family).scene(stop, themes)
        }
    }
}

/** The card back's faint rings, rising from below like the sun on the Lucento symbol. */
internal fun rings(d: DrawScope) {
    val center = Offset(d.size.width / 2f, d.size.height * 1.35f)
    val step = d.size.width * 0.06f
    var r = step
    while (r < d.size.width) {
        d.drawCircle(Color.White.copy(alpha = 0.05f), radius = r, center = center, style = Stroke(width = d.size.width * 0.0067f))
        r += step
    }
}

private fun glow(chapter: ProTourChapter): Color = when (chapter) {
    ProTourChapter.YOUR_MONEY -> AwardArtwork.GlowMoney
    ProTourChapter.YOUR_GOALS -> AwardArtwork.GlowGoals
    ProTourChapter.YOUR_INSIGHTS -> AwardArtwork.GlowInsights
    ProTourChapter.MAKE_IT_YOURS -> AwardArtwork.GlowYours
}

/** A few drawing words for the scenes, in the 300 × 116 space (the same numbers as the design study's SVG). */
private class Pen(val d: DrawScope, val paths: HashMap<String, Path>, val measurer: TextMeasurer, val family: FontFamily?) {
    val paper = AwardArtwork.Paper
    val line = AwardArtwork.PaperLine
    val shade = AwardArtwork.PaperShade
    val sun = AwardArtwork.Gold
    val tangerine = AwardArtwork.RibbonTangerine
    val gold = AwardArtwork.GoldLight
    val goldDeep = AwardArtwork.GoldDeep
    val blue = AwardArtwork.CardBlue
    val lilac = AwardArtwork.Lilac
    val ink = AwardArtwork.StripeNavy

    fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) =
        d.drawRoundRect(c, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(r, r))

    fun dot(cx: Float, cy: Float, r: Float, c: Color) = d.drawCircle(c, radius = r, center = Offset(cx, cy))

    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) = d.drawOval(c, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2))

    private fun path(data: String): Path = paths.getOrPut(data) { PathParser().parsePathString(data).toPath() }

    fun fill(data: String, c: Color, alpha: Float = 1f) = d.drawPath(path(data), c, alpha = alpha)

    fun stroke(data: String, c: Color, width: Float, dash: FloatArray? = null) = d.drawPath(
        path(data),
        c,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash?.let { PathEffect.dashPathEffect(it) }),
    )

    /** A four-pointed sparkle. */
    fun star(x: Float, y: Float, r: Float, c: Color) {
        val p = Path().apply {
            moveTo(x, y - r)
            lineTo(x + 0.28f * r, y - 0.28f * r)
            lineTo(x + r, y)
            lineTo(x + 0.28f * r, y + 0.28f * r)
            lineTo(x, y + r)
            lineTo(x - 0.28f * r, y + 0.28f * r)
            lineTo(x - r, y)
            lineTo(x - 0.28f * r, y - 0.28f * r)
            close()
        }
        d.drawPath(p, c)
    }

    fun at(x: Float, y: Float, block: () -> Unit) = d.translate(x, y) { block() }

    fun turned(degrees: Float, block: () -> Unit) = d.rotate(degrees, pivot = Offset.Zero) { block() }

    fun scene(stop: ProTourStop, themes: List<Pair<Color, Color>>) = when (stop) {
        ProTourStop.WALLETS -> at(150f, 62f) {
            turned(-13f) { rr(-74f, -28f, 86f, 54f, 9f, sun); rr(-64f, -15f, 20f, 15f, 3f, Color(0xFFE0A800)) }
            turned(-2f) { rr(-46f, -31f, 86f, 54f, 9f, paper); rr(-36f, -18f, 20f, 15f, 3f, Color(0xFFE8C766)); rr(-36f, 8f, 48f, 5f, 2.5f, line) }
            turned(10f) { rr(-14f, -32f, 86f, 54f, 9f, tangerine); rr(-4f, -19f, 20f, 15f, 3f, Color(0xFFFFC27E)); rr(-4f, 7f, 40f, 5f, 2.5f, Color(0xFFFFB36B)) }
            dot(78f, -30f, 15f, AwardArtwork.GoldHighlight)
            stroke("M78 -38V-22M70 -30H86", ink, 3.2f)
        }
        ProTourStop.NET_WORTH -> at(150f, 66f) {
            at(-66f, 0f) {
                rr(-22f, -6f, 44f, 28f, 0f, AwardArtwork.CoinSide)
                oval(0f, 22f, 22f, 7f, AwardArtwork.CoinSide)
                oval(0f, -6f, 22f, 7f, sun)
                stroke("M-22 4c0 4 10 7 22 7s22-3 22-7M-22 13c0 4 10 7 22 7s22-3 22-7", AwardArtwork.CoinLine, 1.6f)
            }
            fill("M-14 28V-2L12 -24L38 -2V28Z", paper)
            stroke("M-20 0L12 -28L44 0", tangerine, 5f)
            rr(5f, 8f, 14f, 20f, 2f, blue)
            dot(72f, 2f, 22f, Color(0xFFEAF1FF))
            stroke("M50 2H94M72 -20c-10 11-10 33 0 44M72 -20c10 11 10 33 0 44", blue, 2.4f)
            star(102f, -26f, 7f, gold)
        }
        ProTourStop.SMART_READING -> at(150f, 60f) {
            fill("M-96 -30h66a10 10 0 0 1 10 10v26a10 10 0 0 1-10 10h-42l-14 12V16h-10a10 10 0 0 1-10-10v-26a10 10 0 0 1 10-10z", paper)
            rr(-86f, -18f, 48f, 5f, 2.5f, lilac)
            rr(-86f, -6f, 34f, 5f, 2.5f, lilac)
            stroke("M-12 -4H8", gold, 3f, dash = floatArrayOf(1f, 6f))
            stroke("M8 -11l8 7-8 7", gold, 3f)
            fill("M30 -38h54v70l-6.75-5-6.75 5-6.75-5-6.75 5-6.75-5-6.75 5-6.75-5-6.75 5z", paper)
            rr(38f, -28f, 38f, 5f, 2.5f, tangerine)
            rr(38f, -15f, 26f, 4f, 2f, line)
            rr(38f, -5f, 32f, 4f, 2f, line)
            rr(38f, 5f, 22f, 4f, 2f, line)
            star(-34f, -40f, 9f, gold)
            star(98f, -38f, 6f, gold)
        }
        ProTourStop.GOALS -> at(150f, 70f) {
            dot(-104f, -30f, 13f, sun)
            fill("M-130 46c34-30 70-34 104-16s66 12 100-8 56-8 56-8v32H-130z", AwardArtwork.GlowGoals.copy(alpha = 1f), alpha = 0.6f)
            stroke("M-62 30V-16", paper, 3f)
            fill("M-62 -16l24 8-24 8z", sun)
            stroke("M2 22V-40", paper, 3f)
            fill("M2 -40l26 9-26 9z", tangerine)
            stroke("M64 26V-18", paper, 3f)
            fill("M64 -18l22 8-22 8z", paper)
        }
        ProTourStop.INSIGHTS_PERIODS -> at(150f, 100f) {
            // Twelve months of bars: the nine Pro opened in gold, the last three (always free) in paper.
            listOf(22f, 28f, 26f, 34f, 38f, 36f, 44f, 50f, 48f, 56f, 62f, 70f).forEachIndexed { i, h ->
                rr(-94f + i * 16f, -h, 11f, h, 3f, if (i < 9) gold else paper.copy(alpha = 0.7f))
            }
            dot(98f, -84f, 9f, sun)
        }
        ProTourStop.STATEMENTS -> at(150f, 58f) {
            turned(-7f) { rr(-44f, -46f, 74f, 94f, 8f, shade) }
            rr(-32f, -48f, 74f, 94f, 8f, paper)
            rr(-22f, -38f, 32f, 7f, 3.5f, blue)
            rr(-22f, -24f, 52f, 4f, 2f, line)
            rr(-22f, -15f, 44f, 4f, 2f, line)
            rr(-22f, -6f, 50f, 4f, 2f, line)
            rr(-22f, 3f, 36f, 4f, 2f, line)
            rr(-46f, 18f, 38f, 20f, 5f, tangerine)
            label("PDF", -27f, 28f, paper)
            dot(30f, 30f, 17f, gold)
            d.drawCircle(goldDeep, radius = 17f, center = Offset(30f, 30f), style = Stroke(width = 2f))
            star(30f, 30f, 8f, goldDeep)
        }
        ProTourStop.AI_ANSWERS -> at(150f, 58f) {
            fill("M-96 -40h88a10 10 0 0 1 10 10v20a10 10 0 0 1-10 10h-62l-12 10V0h-14a10 10 0 0 1-10-10v-20a10 10 0 0 1 10-10z", paper)
            rr(-84f, -28f, 58f, 5f, 2.5f, line)
            rr(-84f, -17f, 40f, 5f, 2.5f, line)
            fill("M8 -8h82a10 10 0 0 1 10 10v24a10 10 0 0 1-10 10h-12v12l-14-12H8a10 10 0 0 1-10-10V2a10 10 0 0 1 10-10z", lilac)
            rr(20f, 4f, 62f, 5f, 2.5f, paper)
            rr(20f, 16f, 46f, 5f, 2.5f, paper)
            star(104f, -18f, 8f, gold)
            star(-104f, 14f, 5f, gold)
        }
        ProTourStop.SYNC -> at(150f, 60f) {
            rr(-88f, -44f, 46f, 86f, 10f, paper); rr(-81f, -33f, 32f, 56f, 3f, line); dot(-65f, 32f, 3f, line)
            rr(42f, -44f, 46f, 86f, 10f, paper); rr(49f, -33f, 32f, 56f, 3f, line); dot(65f, 32f, 3f, line)
            stroke("M-26 -10a28 28 0 0 1 50 -4", gold, 4f)
            stroke("M18 -24l7 10-12 2", gold, 4f)
            stroke("M26 10a28 28 0 0 1-50 4", gold, 4f)
            stroke("M-18 24l-7-10 12-2", gold, 4f)
        }
        ProTourStop.SHARED -> at(150f, 64f) {
            stroke("M-36 -30c22-20 50-20 72 0", gold, 3f, dash = floatArrayOf(2f, 6f))
            dot(-58f, -20f, 15f, sun)
            fill("M-86 32c0-22 13-36 28-36s28 14 28 36z", sun)
            dot(58f, -20f, 15f, tangerine)
            fill("M30 32c0-22 13-36 28-36s28 14 28 36z", tangerine)
            rr(-28f, -2f, 56f, 36f, 7f, paper)
            rr(-20f, 6f, 15f, 11f, 2f, Color(0xFFE8C766))
            rr(-20f, 22f, 34f, 4f, 2f, line)
        }
        ProTourStop.THEMES -> at(150f, 58f) {
            d.scale(0.9f, 0.9f, pivot = Offset.Zero) {
                fun phone(c: Pair<Color, Color>) {
                    rr(-30f, -42f, 60f, 84f, 11f, c.first)
                    rr(-21f, -31f, 30f, 6f, 3f, c.second)
                    rr(-21f, 20f, 42f, 12f, 6f, c.second)
                }
                turned(-11f) { at(-58f, 2f) { phone(themes[0]) } }
                at(0f, -6f) { phone(themes[1]) }
                turned(11f) { at(58f, 2f) { phone(themes[2]) } }
                star(100f, -36f, 7f, gold)
            }
        }
    }

    /** A short word centred on (cx, cy), at 11 units in the scene's space. */
    private fun label(text: String, cx: Float, cy: Float, c: Color) {
        val style = TextStyle(color = c, fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = with(d) { 11f.toSp() })
        val layout = measurer.measure(text, style)
        d.drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
    }
}
