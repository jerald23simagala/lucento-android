package com.lucento.app.feature.pro.welcome

import android.content.res.Resources
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.min
import com.lucento.app.R
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.theme.AwardArtwork
import com.lucento.app.domain.entitlement.ProTourStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A card's art (owner, 2026-11-13: "simpler, and add our mascot to make it livelier"; then "subtle doodles instead of
 * plain blue, and as little empty blue as possible"): Lucento, the approved mascot, stepping a little in front of one
 * simple prop for the perk, on the cards' blue with faint doodles. The mascot is the app's own [LucentoMascot] (the
 * approved sprites, never redrawn), so it hops in as the card turns, floats a little and rests, in a pose that suits
 * the card ([mascot]); its shadow stays on the ground while it floats. The props are soft-3D cut-outs made to match it
 * (docs/design/illustrations/perks, tools/illustrations/export_perk_art.py), so the doodles show around them. The pair
 * is as large as the band allows (nearly its full width on a phone) and sits a little below the middle. Like the
 * medal, a card is an object: its art looks the same in every theme. Decorative: the card's words say everything it
 * shows, so TalkBack skips it.
 */
@Composable
internal fun PerkArt(stop: ProTourStop, modifier: Modifier = Modifier) {
    val prop = rememberPerkArt(stop.art)
    BoxWithConstraints(
        modifier
            .background(AwardArtwork.CardBlue)
            .drawBehind { drawDoodles() }
            .clipToBounds()
            .clearAndSetSemantics {},
        contentAlignment = BiasAlignment(0f, 0.35f),
    ) {
        // The pair's width is the prop, plus the mascot, less the step in front: 1.8 times the prop's square.
        val unit = min(maxHeight * 0.9f, maxWidth * 0.53f)
        val mascot = unit * MascotScale
        Box(Modifier.width(unit * (1f + MascotScale - MascotStep)).height(unit)) {
            Box(Modifier.size(unit).align(Alignment.BottomStart)) {
                prop?.let {
                    Image(bitmap = it, contentDescription = null, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
                }
            }
            Box(Modifier.size(mascot).align(Alignment.BottomEnd)) {
                Canvas(Modifier.fillMaxSize()) {
                    // Under the feet (the sprites' feet span 31 to 62 % across and end 93 % down).
                    val w = size.width * 0.42f
                    val h = size.height * 0.07f
                    drawOval(MascotShadow, topLeft = Offset(size.width * 0.465f - w / 2f, size.height * 0.93f - h / 2f), size = Size(w, h))
                }
                LucentoMascot(size = mascot, mood = stop.mascot)
            }
        }
    }
}

/** The mascot beside its prop: a little smaller than the prop's square, so the pair reads as one picture. */
private const val MascotScale = 0.92f

/** How far the mascot steps in front of its prop, as a share of the prop's square: it touches the prop, never hides it. */
private const val MascotStep = 0.12f

/** A soft shadow of the band's own blue, darker. */
private val MascotShadow = Color(0x5C0B1A66)

/** One doodle: a 24-unit glyph placed in the 140 dp tile (centre x, y and size in dp, turned by [turn] degrees). */
private class Doodle(val path: Path, val x: Float, val y: Float, val size: Float, val turn: Float, val bright: Boolean)

private fun glyph(data: String): Path = PathParser().parsePathString(data).toPath()

/**
 * The band's doodles: small line drawings of money things (a coin, a receipt, a little chart, a leaf, a sparkle and so
 * on) in faint white, repeated in a 140 dp tile whose rows shift by half a tile so no grid shows. Drawn, not pictured:
 * crisp at any size and nothing to download. Each doodle sits wholly inside its tile, so none is cut where tiles meet.
 */
private val DoodleTile: List<Doodle> by lazy {
    listOf(
        Doodle(glyph("M12 3 13.8 10.2 21 12 13.8 13.8 12 21 10.2 13.8 3 12 10.2 10.2Z"), 18f, 20f, 16f, 0f, true), // sparkle
        Doodle(glyph("M12 4a8 8 0 1 1 0 16a8 8 0 1 1 0-16zM12 7.5a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9z"), 66f, 16f, 18f, 0f, false), // coin
        Doodle(glyph("M3 14c2-4 4-4 6 0s4 4 6 0 4-4 6 0"), 114f, 28f, 20f, -12f, false), // squiggle
        Doodle(glyph("M5 19v-5M10 19V9M15 19v-7M20 19V5"), 30f, 68f, 18f, 6f, false), // little chart
        Doodle(glyph("M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"), 82f, 62f, 16f, 14f, true), // star
        Doodle(glyph("M12 6v12M6 12h12"), 124f, 84f, 12f, 0f, false), // plus
        Doodle(glyph("M5 19C5 10 11 5 19 5c0 8-5 14-14 14zM5 19l8-8"), 18f, 116f, 18f, -18f, false), // leaf
        Doodle(glyph("M7 3h10v18l-2.5-1.5L12 21l-2.5-1.5L7 21zM9.5 8h5M9.5 11.5h5M9.5 15h3"), 62f, 116f, 18f, -8f, false), // receipt
        Doodle(
            glyph(
                "M12 8.5a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7zM12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5" +
                    "M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8",
            ),
            110f, 118f, 18f, 0f, false,
        ), // sun
    )
}

private const val DoodleTileDp = 140f

private fun DrawScope.drawDoodles() {
    val tile = DoodleTileDp * density
    var row = 0
    var top = -tile / 2f
    while (top < size.height) {
        var left = if (row % 2 == 0) 0f else -tile / 2f
        while (left < size.width) {
            DoodleTile.forEach { d ->
                val side = d.size * density
                val scale = side / 24f
                val x = left + d.x * density - side / 2f
                val y = top + d.y * density - side / 2f
                withTransform({
                    translate(x, y)
                    rotate(d.turn, pivot = Offset(side / 2f, side / 2f))
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    drawPath(
                        d.path,
                        Color.White.copy(alpha = if (d.bright) 0.16f else 0.1f),
                        // 1.4 dp on screen, whatever the doodle's size.
                        style = Stroke(width = 1.4f * density / scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            left += tile
        }
        top += tile
        row++
    }
}

/**
 * What the mascot does on each card: waves on the first, points at the prop on most, thinks on the questions, presents
 * the reports and cheers on the last.
 */
internal val ProTourStop.mascot: MascotMood
    get() = when (this) {
        ProTourStop.WALLETS -> MascotMood.Greeting
        ProTourStop.NET_WORTH -> MascotMood.Pointing
        ProTourStop.SMART_READING -> MascotMood.Success
        ProTourStop.GOALS -> MascotMood.Pointing
        ProTourStop.INSIGHTS_PERIODS -> MascotMood.Pointing
        ProTourStop.STATEMENTS -> MascotMood.Success
        ProTourStop.AI_ANSWERS -> MascotMood.Curious
        ProTourStop.SYNC -> MascotMood.Pointing
        ProTourStop.SHARED -> MascotMood.Greeting
        ProTourStop.THEMES -> MascotMood.Celebrate
    }

/** Each card's prop (`perk_art_*`): from docs/design/illustrations/perks via tools/illustrations/export_perk_art.py. */
@get:DrawableRes
internal val ProTourStop.art: Int
    get() = when (this) {
        ProTourStop.WALLETS -> R.drawable.perk_art_wallets
        ProTourStop.NET_WORTH -> R.drawable.perk_art_net_worth
        ProTourStop.SMART_READING -> R.drawable.perk_art_smart_reading
        ProTourStop.GOALS -> R.drawable.perk_art_goals
        ProTourStop.INSIGHTS_PERIODS -> R.drawable.perk_art_insights_periods
        ProTourStop.STATEMENTS -> R.drawable.perk_art_statements
        ProTourStop.AI_ANSWERS -> R.drawable.perk_art_ai_answers
        ProTourStop.SYNC -> R.drawable.perk_art_sync
        ProTourStop.SHARED -> R.drawable.perk_art_shared
        ProTourStop.THEMES -> R.drawable.perk_art_themes
    }

/** Each card's prop as a small square for the collection (`perk_thumb_*`, 192 px), made by the same export. */
@get:DrawableRes
internal val ProTourStop.thumb: Int
    get() = when (this) {
        ProTourStop.WALLETS -> R.drawable.perk_thumb_wallets
        ProTourStop.NET_WORTH -> R.drawable.perk_thumb_net_worth
        ProTourStop.SMART_READING -> R.drawable.perk_thumb_smart_reading
        ProTourStop.GOALS -> R.drawable.perk_thumb_goals
        ProTourStop.INSIGHTS_PERIODS -> R.drawable.perk_thumb_insights_periods
        ProTourStop.STATEMENTS -> R.drawable.perk_thumb_statements
        ProTourStop.AI_ANSWERS -> R.drawable.perk_thumb_ai_answers
        ProTourStop.SYNC -> R.drawable.perk_thumb_sync
        ProTourStop.SHARED -> R.drawable.perk_thumb_shared
        ProTourStop.THEMES -> R.drawable.perk_thumb_themes
    }

/**
 * Decoded card pictures. Decoding one takes a frame or two, so the deck decodes the next card's picture while the
 * current one is showing ([load]); its face is then ready the moment it turns. Holds three: the card before, this one
 * and the next.
 */
internal class PerkArtCache(private val resources: Resources) {
    private val pictures = LruCache<Int, ImageBitmap>(3)

    fun peek(@DrawableRes id: Int): ImageBitmap? = pictures.get(id)

    suspend fun load(@DrawableRes id: Int): ImageBitmap = pictures.get(id)
        ?: withContext(Dispatchers.Default) { ImageBitmap.imageResource(resources, id).also { it.prepareToDraw() } }
            .also { pictures.put(id, it) }
}

/** The deck's cache; outside the deck each picture decodes on its own. */
internal val LocalPerkArtCache = staticCompositionLocalOf<PerkArtCache?> { null }

@Composable
private fun rememberPerkArt(@DrawableRes id: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    val shared = LocalPerkArtCache.current
    val cache = shared ?: remember(resources) { PerkArtCache(resources) }
    // The deck reuses a card's place for the next card, so a new id must replace the old picture at once (never show
    // the previous card's art while the new one decodes).
    val picture by produceState(cache.peek(id), cache, id) {
        value = cache.peek(id)
        if (value == null) value = cache.load(id)
    }
    return picture
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
