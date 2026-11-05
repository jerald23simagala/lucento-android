package com.lucento.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lucento.app.R
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * Lucento's landscape illustrations ("A Brighter Little World"). Decorative backgrounds only: no text, figures or
 * logos are baked in, and they are never announced by screen readers. Shipped as WebP crops of one painterly
 * panorama in drawable-nodpi; source and crop boxes: docs/design/illustrations.
 */
enum class LucentoIllustration(@DrawableRes val res: Int) {
    /** Golden sunrise over layered mountains, a lake and trees — welcome moments. */
    SunriseLandscape(R.drawable.illustration_sunrise_landscape),

    /** Calm blue mountains, lake and island — budget and savings cards. */
    SoftMountains(R.drawable.illustration_soft_mountains),

    /** Mount Fuji at sunrise among clouds — Japan travel goals. */
    JapanFuji(R.drawable.illustration_japan_fuji),
}

/**
 * A surface with an illustration behind its content. The artwork fills the surface (cropped, never distorted,
 * positioned by [artworkAlignment]) and is clipped to [shape]; [overlay] is painted between the artwork and the
 * content so text can sit on a solid or near-solid scrim (see [IllustrationScrims]). The content decides the height —
 * the artwork never drives layout, so font scaling and long text behave exactly as on a plain card.
 *
 * The artwork is decorative (no content description). Pass [onClick] to make the whole surface one button.
 */
@Composable
fun IllustratedSurface(
    illustration: LucentoIllustration,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = contentColorFor(containerColor),
    border: BorderStroke? = null,
    overlay: Brush? = null,
    artworkAlignment: Alignment = Alignment.Center,
    artworkAlpha: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) = IllustratedSurface(
    artwork = illustration.res,
    modifier = modifier,
    shape = shape,
    containerColor = containerColor,
    contentColor = contentColor,
    border = border,
    overlay = overlay,
    artworkAlignment = artworkAlignment,
    artworkAlpha = artworkAlpha,
    contentPadding = contentPadding,
    onClick = onClick,
    content = content,
)

/** [IllustratedSurface] with any decorative [artwork], e.g. the chosen theme's landscape ([ThemeSpec.scenery]). */
@Composable
fun IllustratedSurface(
    @DrawableRes artwork: Int,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = contentColorFor(containerColor),
    border: BorderStroke? = null,
    overlay: Brush? = null,
    artworkAlignment: Alignment = Alignment.Center,
    artworkAlpha: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Box {
            Image(
                painter = painterResource(artwork),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = artworkAlignment,
                alpha = artworkAlpha,
                modifier = Modifier.matchParentSize(),
            )
            if (overlay != null) Box(Modifier.matchParentSize().background(overlay))
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = body,
        )
    } else {
        Surface(modifier = modifier, shape = shape, color = containerColor, contentColor = contentColor, border = border, content = body)
    }
}

/**
 * Where the horizon sits in every theme panorama (they share one composition): a vertical bias that frames the ridge
 * crests and the low sun in a short, wide strip.
 */
private val HorizonFraming = BiasAlignment(horizontalBias = 0.3f, verticalBias = 0.5f)

/**
 * The chosen theme's landscape as a strip along the bottom of a hero panel: ridges and the low sun (or moonlit haze),
 * fading up into the panel. Decorative and silent, and never behind text — it is its own space below the content, so
 * the panel's text keeps its tested contrast. Light-painted scenes are dimmed at night.
 */
@Composable
fun HeroHorizon(modifier: Modifier = Modifier, height: Dp = 36.dp) {
    val spec = LucentoTheme.spec
    val dim = LucentoTheme.isDark && !spec.sceneryIsNight
    Image(
        painter = painterResource(spec.scenery),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = HorizonFraming,
        colorFilter = if (dim) ColorFilter.tint(Color.Black.copy(alpha = 0.42f), BlendMode.SrcAtop) else null,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Fade the artwork itself in from the top, so it blends into a solid or gradient panel alike.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Black), blendMode = BlendMode.DstIn)
            },
    )
}

/**
 * Lucento standing in the chosen theme's landscape — the empty-state and welcome vignette. The mascot keeps its own
 * label; the landscape is silent.
 */
@Composable
fun MascotScene(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    mascotSize: Dp = 96.dp,
    contentDescription: String? = null,
) {
    val spec = LucentoTheme.spec
    val dim = LucentoTheme.isDark && !spec.sceneryIsNight
    IllustratedSurface(
        artwork = spec.scenery,
        modifier = modifier.widthIn(max = 320.dp).fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.background,
        overlay = if (dim) SolidColor(MaterialTheme.colorScheme.background.copy(alpha = 0.45f)) else null,
        artworkAlignment = BiasAlignment(horizontalBias = 0.3f, verticalBias = 0.6f),
        contentPadding = PaddingValues(top = Spacing.lg, bottom = Spacing.sm),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LucentoMascot(size = mascotSize, mood = mood, contentDescription = contentDescription)
        }
    }
}

/**
 * Prints a landscape as tone-on-tone silhouettes: every pixel becomes black, as opaque as it is darker than
 * [threshold] (luma 0–255) times [strength] — ridges and trees print, sky / sun / moon vanish. Drawn over a deep face
 * it can only darken, so light text on that face only gains contrast. Used by the Home net-balance card.
 */
fun silhouetteFilter(threshold: Float, strength: Float = 0.4f): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            -strength * 0.2126f, -strength * 0.7152f, -strength * 0.0722f, 0f, strength * threshold,
        ),
    ),
)

/** Scrims that keep text on solid colour while letting the artwork show where nothing needs reading. */
object IllustrationScrims {
    /**
     * Solid [color] from the start edge until [fadeWidthPx] before the end, then fading to [endAlpha]: the artwork
     * shows only in the trailing band (behind the mascot on the budget hero). Measured in pixels from the end, so
     * text stays on solid colour at any width.
     */
    fun trailingWindow(color: Color, fadeWidthPx: Float, endAlpha: Float): Brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val start = (size.width - fadeWidthPx).coerceAtLeast(0f)
            return LinearGradientShader(
                from = Offset(start, 0f),
                to = Offset(size.width, 0f),
                colors = listOf(color, color.copy(alpha = endAlpha)),
            )
        }
    }

    /**
     * The artwork shows through the top [windowPx] (with an optional [wash] for the night theme) and fades into solid
     * [color] over the last [fadePx] of that window; everything below sits on solid colour.
     */
    fun topWindow(color: Color, windowPx: Float, fadePx: Float, wash: Color = Color.Transparent): Brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val fadeStart = (windowPx - fadePx).coerceAtLeast(0f)
            return LinearGradientShader(
                from = Offset(0f, 0f),
                to = Offset(0f, windowPx.coerceAtLeast(1f)),
                colors = listOf(wash, wash, color),
                colorStops = listOf(0f, fadeStart / windowPx.coerceAtLeast(1f), 1f),
            )
        }
    }
}
