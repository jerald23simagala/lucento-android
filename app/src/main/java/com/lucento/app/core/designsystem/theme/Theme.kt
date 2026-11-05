package com.lucento.app.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.data.preferences.ThemeMode

private val LocalExtendedColors = staticCompositionLocalOf { LightExtended }
private val LocalReducedMotion = staticCompositionLocalOf { false }
private val LocalIsDark = staticCompositionLocalOf { false }
private val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec.Sunrise }

/** How long a theme or light/dark switch cross-fades the palette. */
private const val ThemeTransitionMillis = 250

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Lucento deliberately does not use dynamic (wallpaper) colour: curated themes keep financial semantics
 * (positive / negative / warning) identical everywhere and every theme is contrast-checked.
 *
 * [darkTheme] is the user's light/dark choice; a dark-only [theme] (Midnight) renders dark regardless. Switching either
 * recomposes in place (no Activity restart) and cross-fades the colours while [animateChanges] is true (it is false
 * while the app launches, so the saved theme arriving doesn't fade in) unless the user removed animations.
 */
@Composable
fun LucentoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    theme: ThemeId = ThemeId.SUNRISE,
    animateChanges: Boolean = true,
    content: @Composable () -> Unit,
) {
    val spec = ThemeSpec.of(theme)
    val dark = spec.isDark(darkTheme)
    val context = LocalContext.current
    // "Remove animations" in accessibility settings sets the animator scale to 0.
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val colors = animateThemeColors(spec.colors(dark), animate = animateChanges && !reducedMotion)
    CompositionLocalProvider(
        LocalExtendedColors provides colors.extended,
        LocalReducedMotion provides reducedMotion,
        LocalIsDark provides dark,
        LocalThemeSpec provides spec,
    ) {
        MaterialTheme(
            colorScheme = colors.scheme,
            typography = LucentoTypography,
            shapes = LucentoShapes,
            content = content,
        )
    }
}

/** The palette on screen: [target] once settled; while switching, a blend from what was showing. */
@Composable
private fun animateThemeColors(target: ThemeColors, animate: Boolean): ThemeColors {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        // A switch during a switch starts from the blend on screen, so nothing jumps.
        from = lerp(from, to, progress.value)
        to = target
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(ThemeTransitionMillis))
        } else {
            progress.snapTo(1f)
        }
    }
    return lerp(from, to, progress.value)
}

object LucentoTheme {
    val extendedColors: LucentoExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

    /** True when the user asked the system to remove animations; decorative motion is skipped. */
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current

    /** True in the night palette (the dark mode, or a dark-only theme). */
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current

    /** The chosen theme: its name and landscape. Colours are read through MaterialTheme and [extendedColors]. */
    val spec: ThemeSpec
        @Composable @ReadOnlyComposable get() = LocalThemeSpec.current
}
