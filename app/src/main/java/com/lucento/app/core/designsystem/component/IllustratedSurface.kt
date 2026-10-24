package com.lucento.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lucento.app.R
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
) {
    val body: @Composable () -> Unit = {
        Box {
            Image(
                painter = painterResource(illustration.res),
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
