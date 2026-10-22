package com.lucento.app.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.lucento.app.data.preferences.ThemeMode

private val LocalExtendedColors = staticCompositionLocalOf { LightExtended }
private val LocalReducedMotion = staticCompositionLocalOf { false }
private val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Lucento deliberately does not use dynamic (wallpaper) colour: a consistent brand palette
 * keeps financial semantics (positive / negative / accent) predictable.
 */
@Composable
fun LucentoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) DarkExtended else LightExtended
    val context = LocalContext.current
    // "Remove animations" in accessibility settings sets the animator scale to 0.
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalExtendedColors provides extended,
        LocalReducedMotion provides reducedMotion,
        LocalIsDark provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = LucentoTypography,
            shapes = LucentoShapes,
            content = content,
        )
    }
}

object LucentoTheme {
    val extendedColors: LucentoExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

    /** True when the user asked the system to remove animations; decorative motion is skipped. */
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current

    /** True in the night theme; the mascot switches from Sun to Moon. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current
}
