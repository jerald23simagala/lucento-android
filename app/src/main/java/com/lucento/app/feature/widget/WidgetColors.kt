package com.lucento.app.feature.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.lucento.app.core.designsystem.theme.ThemeColors
import com.lucento.app.core.designsystem.theme.ThemeSpec
import com.lucento.app.data.preferences.ThemeMode
import androidx.glance.color.ColorProvider as dayNightColor

/**
 * Lucento's theme on the home screen (docs/WIDGETS.md): the chosen theme's own colours, never the wallpaper's. With the
 * app's light / dark set to System the launcher switches them with the phone; a fixed mode stays fixed; Midnight is
 * always dark. Surfaces are the theme's cards: white by day, its container colour at night.
 */
internal class WidgetColors private constructor(
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val outline: ColorProvider,
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    val tonal: ColorProvider,
    val onTonal: ColorProvider,
    val track: ColorProvider,
    private val positive: ColorProvider,
    private val positiveContainer: ColorProvider,
    private val warning: ColorProvider,
    private val warningContainer: ColorProvider,
    private val negative: ColorProvider,
    private val negativeContainer: ColorProvider,
    private val success: ColorProvider,
    private val warningIndicator: ColorProvider,
    private val negativeIndicator: ColorProvider,
) {
    /** Text in a tone (figures, verdicts). */
    fun text(tone: WidgetTone): ColorProvider = when (tone) {
        WidgetTone.NEUTRAL -> onSurface
        WidgetTone.POSITIVE -> positive
        WidgetTone.WARNING -> warning
        WidgetTone.NEGATIVE -> negative
    }

    /** A chip's fill in a tone, as Home's StatusTones. */
    fun container(tone: WidgetTone): ColorProvider = when (tone) {
        WidgetTone.NEUTRAL -> tonal
        WidgetTone.POSITIVE -> positiveContainer
        WidgetTone.WARNING -> warningContainer
        WidgetTone.NEGATIVE -> negativeContainer
    }

    /** A bar's fill in a tone, as Home's ring (healthColor). */
    fun indicator(tone: WidgetTone): ColorProvider = when (tone) {
        WidgetTone.NEUTRAL -> outline
        WidgetTone.POSITIVE -> success
        WidgetTone.WARNING -> warningIndicator
        WidgetTone.NEGATIVE -> negativeIndicator
    }

    companion object {
        fun of(spec: ThemeSpec, mode: ThemeMode): WidgetColors {
            val light = spec.light
            val dark = spec.dark
            fun pick(color: (ThemeColors, Boolean) -> Color): ColorProvider = when {
                light == null -> ColorProvider(color(dark, true))
                mode == ThemeMode.LIGHT -> ColorProvider(color(light, false))
                mode == ThemeMode.DARK -> ColorProvider(color(dark, true))
                else -> dayNightColor(day = color(light, false), night = color(dark, true))
            }
            return WidgetColors(
                surface = pick { c, night -> if (night) c.scheme.surfaceContainer else c.scheme.surfaceContainerLowest },
                onSurface = pick { c, _ -> c.scheme.onSurface },
                onSurfaceVariant = pick { c, _ -> c.scheme.onSurfaceVariant },
                outline = pick { c, _ -> c.scheme.outline },
                primary = pick { c, _ -> c.scheme.primary },
                onPrimary = pick { c, _ -> c.scheme.onPrimary },
                tonal = pick { c, _ -> c.scheme.primaryContainer },
                onTonal = pick { c, _ -> c.scheme.onPrimaryContainer },
                track = pick { c, _ -> c.extended.track },
                positive = pick { c, _ -> c.extended.positive },
                positiveContainer = pick { c, _ -> c.extended.positiveContainer },
                warning = pick { c, _ -> c.extended.warning },
                warningContainer = pick { c, _ -> c.extended.warningContainer },
                negative = pick { c, _ -> c.extended.negative },
                negativeContainer = pick { c, _ -> c.extended.negativeContainer },
                success = pick { c, _ -> c.extended.success },
                warningIndicator = pick { c, _ -> c.extended.warningIndicator },
                negativeIndicator = pick { c, _ -> c.extended.negativeIndicator },
            )
        }
    }
}
