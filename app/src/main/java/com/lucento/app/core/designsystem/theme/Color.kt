package com.lucento.app.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Lucento palette: deep pine for trust and focus, a single warm brass accent used sparingly
 * for moments of emphasis, and warm paper / ink neutrals. No gradients.
 */
internal object Palette {
    val Pine900 = Color(0xFF0B2624)
    val Pine800 = Color(0xFF133B3A)
    val Pine700 = Color(0xFF1E4D4A)
    val Pine300 = Color(0xFF8FC7BF)
    val Pine100 = Color(0xFFD5E6E2)

    val Brass700 = Color(0xFF8A6A2C)
    val Brass400 = Color(0xFFD9B36C)
    val Brass100 = Color(0xFFF2E5C8)

    val Paper = Color(0xFFF6F4EF)
    val Ink = Color(0xFF0D1112)
}

internal val LightColors = lightColorScheme(
    primary = Palette.Pine700,
    onPrimary = Color.White,
    primaryContainer = Palette.Pine100,
    onPrimaryContainer = Palette.Pine900,
    inversePrimary = Palette.Pine300,
    secondary = Palette.Brass700,
    onSecondary = Color.White,
    secondaryContainer = Palette.Brass100,
    onSecondaryContainer = Color(0xFF2E2108),
    tertiary = Color(0xFF4A5B78),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE2EF),
    onTertiaryContainer = Color(0xFF16233A),
    background = Palette.Paper,
    onBackground = Color(0xFF141817),
    surface = Palette.Paper,
    onSurface = Color(0xFF141817),
    surfaceVariant = Color(0xFFE7E4DC),
    onSurfaceVariant = Color(0xFF5A605E),
    surfaceTint = Palette.Pine700,
    inverseSurface = Color(0xFF2A2F2E),
    inverseOnSurface = Color(0xFFF1EFEA),
    error = Color(0xFFA8443A),
    onError = Color.White,
    errorContainer = Color(0xFFF7DCD7),
    onErrorContainer = Color(0xFF3E0E09),
    outline = Color(0xFFC9C5BB),
    outlineVariant = Color(0xFFE0DCD3),
    scrim = Color.Black,
    surfaceBright = Palette.Paper,
    surfaceDim = Color(0xFFDDDAD2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF6),
    surfaceContainer = Color(0xFFF0EDE6),
    surfaceContainerHigh = Color(0xFFEAE7DF),
    surfaceContainerHighest = Color(0xFFE4E1D8),
)

internal val DarkColors = darkColorScheme(
    primary = Palette.Pine300,
    onPrimary = Color(0xFF0A2F2C),
    primaryContainer = Color(0xFF1C4744),
    onPrimaryContainer = Color(0xFFCDE8E3),
    inversePrimary = Palette.Pine700,
    secondary = Palette.Brass400,
    onSecondary = Color(0xFF3A2A08),
    secondaryContainer = Color(0xFF4F3C17),
    onSecondaryContainer = Color(0xFFF2E1BC),
    tertiary = Color(0xFFB4C3DE),
    onTertiary = Color(0xFF1E2B42),
    tertiaryContainer = Color(0xFF34435C),
    onTertiaryContainer = Color(0xFFDCE2EF),
    background = Palette.Ink,
    onBackground = Color(0xFFE8E6E1),
    surface = Palette.Ink,
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF2A302F),
    onSurfaceVariant = Color(0xFFA9B0AE),
    surfaceTint = Palette.Pine300,
    inverseSurface = Color(0xFFE8E6E1),
    inverseOnSurface = Color(0xFF1A1E1E),
    error = Color(0xFFE8968C),
    onError = Color(0xFF4A1510),
    errorContainer = Color(0xFF6B2A22),
    onErrorContainer = Color(0xFFF7DCD7),
    outline = Color(0xFF4A5352),
    outlineVariant = Color(0xFF2C3434),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A3131),
    surfaceDim = Palette.Ink,
    surfaceContainerLowest = Color(0xFF090C0D),
    surfaceContainerLow = Color(0xFF121718),
    surfaceContainer = Color(0xFF161C1D),
    surfaceContainerHigh = Color(0xFF1C2324),
    surfaceContainerHighest = Color(0xFF232A2B),
)

/** Semantic colours Material does not model directly. */
data class LucentoExtendedColors(
    val positive: Color,
    val negative: Color,
    val accent: Color,
    val brandMark: Color,
    val brandMarkOn: Color,
)

internal val LightExtended = LucentoExtendedColors(
    positive = Color(0xFF2F7A57),
    negative = Color(0xFFA8443A),
    accent = Palette.Brass400,
    brandMark = Palette.Pine800,
    brandMarkOn = Color(0xFFF3EEE3),
)

internal val DarkExtended = LucentoExtendedColors(
    positive = Color(0xFF7CC9A1),
    negative = Color(0xFFE8968C),
    accent = Palette.Brass400,
    brandMark = Color(0xFFF3EEE3),
    brandMarkOn = Palette.Pine800,
)
