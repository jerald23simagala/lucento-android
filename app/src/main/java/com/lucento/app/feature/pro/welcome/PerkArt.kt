package com.lucento.app.feature.pro.welcome

import android.content.res.Resources
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.dp
import com.lucento.app.R
import com.lucento.app.core.designsystem.component.LucentoMascot
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.theme.AwardArtwork
import com.lucento.app.domain.entitlement.ProTourStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A card's art (owner, 2026-11-13: "simpler, and add our mascot to make it livelier"; then "subtle doodles instead of
 * plain blue, and as little empty blue as possible"): Lucento, the approved mascot, with its hand on one simple prop
 * for the perk, on the cards' blue with faint doodles. The mascot is the app's own [LucentoMascot] (the approved
 * sprites, never redrawn), so it hops in as the card turns, floats a little and rests, in a pose that suits the card
 * ([mascot]), over a soft shadow that shrinks as it rises. The props are soft-3D cut-outs trimmed to what is visible
 * (docs/design/illustrations/perks, tools/illustrations/export_perk_art.py), so the pair is sized and placed by what
 * really shows ([pairLayout]): as large as the band allows, centred a little below the middle, the torch's glow kept
 * inside the band. Like the medal, a card is an object: its art looks the same in every theme. Decorative: the card's
 * words say everything it shows, so TalkBack skips it.
 */
@Composable
internal fun PerkArt(stop: ProTourStop, modifier: Modifier = Modifier) {
    val prop = rememberPerkArt(stop.art)
    BoxWithConstraints(
        modifier
            // Clip first: the doodles must never reach the card's words (critic, app round 1: they did, in dark).
            .clipToBounds()
            .background(AwardArtwork.CardBlue)
            .drawBehind { drawDoodles() }
            .clearAndSetSemantics {},
    ) {
        val aspect = prop?.let { it.width.toFloat() / it.height } ?: 1f
        val pair = pairLayout(maxWidth.value, maxHeight.value, aspect)
        if (prop != null) {
            Image(
                bitmap = prop,
                contentDescription = null,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .offset(pair.propX.dp, pair.propY.dp)
                    .size(pair.propWidth.dp, pair.propHeight.dp),
            )
        }
        LucentoMascot(
            modifier = Modifier.offset(pair.mascotX.dp, pair.mascotY.dp),
            size = pair.mascot.dp,
            mood = stop.mascot,
            shadow = true,
        )
    }
}

/** Where the prop and the mascot go in a band, in dp from its top left; the mascot's numbers are its sprite's box. */
internal data class PairLayout(
    val mascot: Float,
    val mascotX: Float,
    val mascotY: Float,
    val propWidth: Float,
    val propHeight: Float,
    val propX: Float,
    val propY: Float,
)

/**
 * The pair as large as a [width] × [height] band allows, for a prop of [aspect] (width ÷ height of its visible pixels).
 * Measured from the approved sprites: the character spans 6–96.5 % of its box across, starts 3 % down and stands with
 * its feet 93 % down; its torch's glow reaches 112.8 % across. The prop stands on the same line as the feet, at most as
 * wide as the mascot's box and 86 % as tall, and the mascot's hand overlaps it by [HandOnProp]. The pair is centred on
 * what shows, a little below the middle, with the glow inside the band. A band much wider than tall (large text)
 * spreads them out: the prop centred at 30 % of the width, the mascot at 70 %.
 */
internal fun pairLayout(width: Float, height: Float, aspect: Float): PairLayout {
    val propShare = minOf(1f, PropTall * aspect) // the prop's width in mascot boxes, at full size
    val fitWidth = (width - 2f * SideRoom + HandOnProp) / (propShare + (CharacterRight - CharacterLeft) + GlowBeyond)
    val m = minOf(height * 0.96f, fitWidth).coerceAtLeast(1f)
    val propWidth = m * propShare
    val propHeight = propWidth / aspect
    val feet = Feet * m
    // The group's own top and bottom, from the mascot box's top: the higher of the head and the prop; the shadow.
    val groupTop = minOf(CharacterTop * m, feet - propHeight)
    val groupBottom = feet + ShadowBelow * m
    val top = (height - (groupBottom - groupTop)) * (1f + VerticalBias) / 2f
    val mascotY = top - groupTop
    val propY = mascotY + feet - propHeight
    val wide = width > 2.6f * height
    val propX: Float
    val mascotX: Float
    if (wide) {
        propX = width * 0.3f - propWidth / 2f
        mascotX = width * 0.7f - m * (CharacterLeft + CharacterRight) / 2f
    } else {
        val shows = propWidth - HandOnProp + (CharacterRight - CharacterLeft) * m
        val left = (width - shows) / 2f
        // Keep the torch's glow inside the band: move the pair left if it would spill over the right edge.
        val spill = (left + propWidth - HandOnProp - CharacterLeft * m + (CharacterRight + GlowBeyond) * m) - (width - 2f)
        propX = (left - spill.coerceAtLeast(0f)).coerceAtLeast(SideRoom / 2f)
        mascotX = propX + propWidth - HandOnProp - CharacterLeft * m
    }
    return PairLayout(m, mascotX, mascotY, propWidth, propHeight, propX, propY)
}

// The approved sprites' measurements (docs/mascot; the art is drawn as supplied).
private const val CharacterLeft = 0.06f
private const val CharacterRight = 0.965f
private const val CharacterTop = 0.03f
private const val Feet = 0.93f
private const val GlowBeyond = 0.163f // the torch's glow past the character's right edge
private const val ShadowBelow = 0.04f

/** The prop at most 86 % as tall as the mascot's box: it stands beside the character, never over it. */
private const val PropTall = 0.86f

/** How far the mascot's hand reaches over the prop, in dp. */
private const val HandOnProp = 8f

/** Room kept between the pair and the band's sides, in dp. */
private const val SideRoom = 8f

/** A little below the middle (0 is centred, 1 the foot): the gap above is at most 1.3 times the gap below. */
private const val VerticalBias = 0.12f

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
                // Whole doodles only: one the band's edge would cut is left out (no stubs along the edges).
                if (x < 0f || y < 0f || x + side > size.width || y + side > size.height) return@forEach
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
 * What the mascot does on each card: waves on the first, then points, thinks or simply smiles, so no two neighbouring
 * cards show the same picture, and cheers only on the last (its Success pose is kept for the end and the award).
 */
internal val ProTourStop.mascot: MascotMood
    get() = when (this) {
        ProTourStop.WALLETS -> MascotMood.Greeting
        ProTourStop.NET_WORTH -> MascotMood.Pointing
        ProTourStop.SMART_READING -> MascotMood.Curious
        ProTourStop.GOALS -> MascotMood.Pointing
        ProTourStop.INSIGHTS_PERIODS -> MascotMood.Happy
        ProTourStop.STATEMENTS -> MascotMood.Pointing
        ProTourStop.AI_ANSWERS -> MascotMood.Curious
        ProTourStop.SYNC -> MascotMood.Happy
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
