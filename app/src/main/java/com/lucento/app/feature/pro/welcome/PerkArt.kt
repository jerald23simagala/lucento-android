package com.lucento.app.feature.pro.welcome

import android.content.res.Resources
import android.graphics.BitmapFactory
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
import androidx.compose.ui.geometry.Rect
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
    val aspect = rememberPropAspect(stop.art)
    // Only a pointing hand reaches the prop; in other poses Lucento stands just beside it (critic, app round 3).
    val touches = stop.mascot == MascotMood.Pointing
    BoxWithConstraints(
        modifier
            // Clip first: the doodles must never reach the card's words (critic, app round 1: they did, in dark).
            .clipToBounds()
            .background(AwardArtwork.CardBlue)
            // No doodle over or between the pair (critic, app round 3: stray doodles sat in the gaps).
            .drawBehind { drawDoodles(pairLayout(size.width / density, size.height / density, aspect, touches).keepClear()) }
            .clearAndSetSemantics {},
    ) {
        val pair = pairLayout(maxWidth.value, maxHeight.value, aspect, touches)
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
            // A short visit: the arrival, then one float (none after a wave, which already rocks), so the art is still
            // within about four seconds of each turn while the card's words are read (critic, app round 3).
            idleCycles = if (stop.mascot == MascotMood.Greeting) 0 else 1,
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
) {
    /**
     * Where no doodle goes, each with [DoodleClearance] around it: the prop, and the character as three shapes measured
     * from the approved sprites over the cards' poses (sprite fractions): the hair and the crystal with room above for
     * the hop (x 0.21–0.96, y −0.11–0.10); the head, arms, torch and the glow's bright core (x 0.05–1.03, y 0.10–0.70);
     * the lower body, feet and shadow (x 0.13–0.80, y 0.70–0.98). Shapes rather than one box, so doodles stay around the
     * pair instead of most of them going (critic, polish check: one box left 4–8 per card).
     */
    fun keepClear(): List<Rect> = (
        listOf(Rect(propX, propY, propX + propWidth, propY + propHeight)) +
            CharacterShapes.map { (l, t, r, b) -> Rect(mascotX + l * mascot, mascotY + t * mascot, mascotX + r * mascot, mascotY + b * mascot) }
        ).map { it.inflate(DoodleClearance) }

}

/** Room kept between the doodles and the pair, in dp. */
private const val DoodleClearance = 6f

/** The character's shapes for [PairLayout.keepClear], as left, top, right, bottom fractions of the sprite's box. */
private val CharacterShapes = listOf(
    listOf(0.21f, -0.11f, 0.96f, 0.10f),
    listOf(0.05f, 0.10f, 1.03f, 0.70f),
    listOf(0.13f, 0.70f, 0.80f, 0.98f),
)

/**
 * The pair as large as a [width] × [height] band allows, for a prop of [aspect] (width ÷ height of its visible pixels).
 * Measured from the approved sprites: the character spans 6–96.5 % of its box across, starts 3 % down and stands with
 * its feet 93 % down; its torch's glow reaches 112.8 % across. The prop stands on the same line as the feet, at most as
 * wide as the mascot's box and 86 % as tall. When [touches] (the pointing pose) the hand overlaps it by [HandOnProp];
 * otherwise the character stands [BesideProp] from it, so a hair tip or an elbow never grazes it. The pair stays together
 * at every band shape (critic, app round 2: spreading them apart on wide bands split the picture), centred on what
 * shows, a little below the middle, with the glow inside the band. The mascot is at most 0.88 of the band's height, so
 * its arrival hop (it rises about 9 % of its size) never takes the hair past the band's top.
 */
internal fun pairLayout(width: Float, height: Float, aspect: Float, touches: Boolean = true): PairLayout {
    val reach = if (touches) HandOnProp else -BesideProp // how far the character comes over the prop (negative: a gap)
    val propShare = minOf(1f, PropTall * aspect) // the prop's width in mascot boxes, at full size
    val fitWidth = (width - 2f * SideRoom + reach) / (propShare + (CharacterRight - CharacterLeft) + GlowBeyond)
    val m = minOf(height * MascotMostOfHeight, fitWidth).coerceAtLeast(1f)
    val propWidth = m * propShare
    val propHeight = propWidth / aspect
    val feet = Feet * m
    // The group's own top and bottom, from the mascot box's top: the higher of the head and the prop; the shadow.
    val groupTop = minOf(CharacterTop * m, feet - propHeight)
    val groupBottom = feet + ShadowBelow * m
    val top = (height - (groupBottom - groupTop)) * (1f + VerticalBias) / 2f
    val mascotY = top - groupTop
    val propY = mascotY + feet - propHeight
    val shows = propWidth - reach + (CharacterRight - CharacterLeft) * m
    val left = (width - shows) / 2f
    // Keep the torch's glow inside the band: move the pair left if it would spill over the right edge.
    val spill = (left + propWidth - reach - CharacterLeft * m + (CharacterRight + GlowBeyond) * m) - (width - 2f)
    val propX = (left - spill.coerceAtLeast(0f)).coerceAtLeast(SideRoom / 2f)
    val mascotX = propX + propWidth - reach - CharacterLeft * m
    return PairLayout(m, mascotX, mascotY, propWidth, propHeight, propX, propY)
}

/** The mascot's box at most this share of the band's height: room above the hair for the arrival hop. */
private const val MascotMostOfHeight = 0.88f

// The approved sprites' measurements (docs/mascot; the art is drawn as supplied).
private const val CharacterLeft = 0.06f
private const val CharacterRight = 0.965f
private const val CharacterTop = 0.03f
private const val Feet = 0.93f
private const val GlowBeyond = 0.163f // the torch's glow past the character's right edge
private const val ShadowBelow = 0.04f

/** The prop at most 86 % as tall as the mascot's box: it stands beside the character, never over it. */
private const val PropTall = 0.86f

/** How far a pointing hand reaches over the prop, in dp. */
private const val HandOnProp = 8f

/** The gap between the character and the prop in every other pose, in dp. */
private const val BesideProp = 10f

/** Room kept between the pair and the band's sides, in dp. */
private const val SideRoom = 8f

/** A little below the middle (0 is centred, 1 the foot): the gap above is at most 1.3 times the gap below. */
private const val VerticalBias = 0.12f

/** One kind of doodle: its size in dp, how far it is turned (degrees), and whether it is a little brighter. */
private class DoodleSpec(val size: Float, val turn: Float, val bright: Boolean)

/**
 * The band's doodles: small line drawings of money things (a sparkle, a coin, a squiggle, a little chart, a star, a
 * plus, a leaf, a receipt and a sun) in faint white. Drawn, not pictured: crisp at any size and nothing to download.
 * [DoodleGlyphs] holds their shapes, in the same order.
 */
private val DoodleTile = listOf(
    DoodleSpec(16f, 0f, true), // sparkle
    DoodleSpec(18f, 0f, false), // coin
    DoodleSpec(20f, -12f, false), // squiggle
    DoodleSpec(18f, 6f, false), // little chart
    DoodleSpec(16f, 14f, true), // star
    DoodleSpec(12f, 0f, false), // plus
    DoodleSpec(18f, -18f, false), // leaf
    DoodleSpec(18f, -8f, false), // receipt
    DoodleSpec(18f, 0f, false), // sun
)

private val DoodleGlyphs: List<Path> by lazy {
    listOf(
        "M12 3 13.8 10.2 21 12 13.8 13.8 12 21 10.2 13.8 3 12 10.2 10.2Z",
        "M12 4a8 8 0 1 1 0 16a8 8 0 1 1 0-16zM12 7.5a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9z",
        "M3 14c2-4 4-4 6 0s4 4 6 0 4-4 6 0",
        "M5 19v-5M10 19V9M15 19v-7M20 19V5",
        "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z",
        "M12 6v12M6 12h12",
        "M5 19C5 10 11 5 19 5c0 8-5 14-14 14zM5 19l8-8",
        "M7 3h10v18l-2.5-1.5L12 21l-2.5-1.5L7 21zM9.5 8h5M9.5 11.5h5M9.5 15h3",
        "M12 8.5a3.5 3.5 0 1 1 0 7a3.5 3.5 0 1 1 0-7zM12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5" +
            "M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8",
    ).map { PathParser().parsePathString(it).toPath() }
}

/** Candidate places for doodles: a staggered grid this many dp apart; accepted doodles keep [DoodleSpacing] between them. */
private const val DoodleStep = 24f
private const val DoodleSpacing = 34f

/** A doodle placed in a band: which one ([kind], an index into the doodles), and its box in dp. */
internal data class DoodleSpot(val kind: Int, val box: Rect)

/**
 * Where the doodles go in a [width] × [height] band (dp): they fill the free blue around the pair, so a band keeps its
 * texture however much of it the pair takes. Walks a staggered grid ([DoodleStep]) in reading order and keeps a doodle
 * when it is whole (one the band's edge would cut is left out, so no stubs along the edges), clear of [keepClear], and
 * at least [DoodleSpacing] from the ones already kept. The kinds follow the grid, so the same band always gets the same
 * doodles.
 */
internal fun doodleSpots(width: Float, height: Float, keepClear: List<Rect> = emptyList()): List<DoodleSpot> {
    val spots = mutableListOf<DoodleSpot>()
    var row = 0
    var y = DoodleStep / 2f
    while (y < height) {
        var x = DoodleStep / 2f + if (row % 2 == 1) DoodleStep / 2f else 0f
        var col = 0
        while (x < width) {
            val kind = (row * 4 + col * 7) % DoodleTile.size
            val half = DoodleTile[kind].size / 2f
            val box = Rect(x - half, y - half, x + half, y + half)
            val whole = box.left >= 0f && box.top >= 0f && box.right <= width && box.bottom <= height
            if (whole && keepClear.none { it.overlaps(box) } && spots.none { (it.box.center - box.center).getDistance() < DoodleSpacing }) {
                spots += DoodleSpot(kind, box)
            }
            x += DoodleStep
            col++
        }
        y += DoodleStep * 0.866f
        row++
    }
    return spots
}

private fun DrawScope.drawDoodles(keepClear: List<Rect>) {
    doodleSpots(size.width / density, size.height / density, keepClear).forEach { spot ->
        val d = DoodleTile[spot.kind]
        val side = d.size * density
        val scale = side / 24f
        val x = spot.box.left * density
        val y = spot.box.top * density
        withTransform({
            translate(x, y)
            rotate(d.turn, pivot = Offset(side / 2f, side / 2f))
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawPath(
                DoodleGlyphs[spot.kind],
                Color.White.copy(alpha = if (d.bright) 0.16f else 0.1f),
                // 1.4 dp on screen, whatever the doodle's size.
                style = Stroke(width = 1.4f * density / scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * What the mascot does on each card: waves on the first, points at the prop on every other card, and between them
 * smiles or thinks, so no two neighbouring cards show the same picture. Its cheer (the Success pose) is kept for the
 * award that follows the cards (critic, app round 2).
 */
internal val ProTourStop.mascot: MascotMood
    get() = when (this) {
        ProTourStop.WALLETS -> MascotMood.Greeting
        ProTourStop.NET_WORTH -> MascotMood.Pointing
        ProTourStop.SMART_READING -> MascotMood.Happy
        ProTourStop.GOALS -> MascotMood.Pointing
        ProTourStop.INSIGHTS_PERIODS -> MascotMood.Curious
        ProTourStop.STATEMENTS -> MascotMood.Pointing
        ProTourStop.AI_ANSWERS -> MascotMood.Curious
        ProTourStop.SYNC -> MascotMood.Pointing
        ProTourStop.SHARED -> MascotMood.Greeting
        ProTourStop.THEMES -> MascotMood.Happy
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

/** A prop picture's width ÷ height, read from its file's header (nothing is decoded), so the layout never waits for it. */
@Composable
private fun rememberPropAspect(@DrawableRes id: Int): Float {
    val resources = LocalContext.current.resources
    return remember(resources, id) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, id, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 1f
    }
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
