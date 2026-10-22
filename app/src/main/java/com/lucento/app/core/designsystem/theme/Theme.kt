package com.lucento.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.lucento.app.data.preferences.ThemeMode

private val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

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
    CompositionLocalProvider(LocalExtendedColors provides extended) {
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
}
