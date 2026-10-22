package com.lucento.app.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.lucento.app.domain.WalletType

/*
 * Lucento palette — "pine & brass on ivory".
 *
 * Deep pine carries the brand and primary actions; warm ivory and ink neutrals do most of the work;
 * brass is the single accent, reserved for moments of emphasis (the statement rule, the current
 * month in a chart, the point of light in the mark). No gradients.
 *
 * Every text/background pair used by the UI was checked for WCAG AA (≥ 4.5:1 for body text,
 * ≥ 3:1 for large text and component boundaries) in both themes.
 */
internal object Palette {
    // Pine
    val Pine950 = Color(0xFF0B2620)
    val Pine900 = Color(0xFF123530)
    val Pine850 = Color(0xFF163E37)
    val Pine700 = Color(0xFF1C4A42)
    val Pine300 = Color(0xFF8FCBBB)
    val Pine100 = Color(0xFFD6E8E1)

    // Brass
    val Brass700 = Color(0xFF85672A)
    val Brass500 = Color(0xFFC49A45)
    val Brass300 = Color(0xFFD9B36C)
    val Brass100 = Color(0xFFF1E4C6)

    // Neutrals
    val Ivory = Color(0xFFF6F3EB)
    val Cream = Color(0xFFF6F1E5)
    val Ink = Color(0xFF161A19)
    val Night = Color(0xFF0D1211)
}

internal val LightColors = lightColorScheme(
    primary = Palette.Pine700,
    onPrimary = Color.White,
    primaryContainer = Palette.Pine100,
    onPrimaryContainer = Palette.Pine950,
    inversePrimary = Palette.Pine300,
    secondary = Palette.Brass700,
    onSecondary = Color.White,
    secondaryContainer = Palette.Brass100,
    onSecondaryContainer = Color(0xFF2C2008),
    tertiary = Color(0xFF46597A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE4F0),
    onTertiaryContainer = Color(0xFF16233A),
    background = Palette.Ivory,
    onBackground = Palette.Ink,
    surface = Palette.Ivory,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFE6E1D5),
    onSurfaceVariant = Color(0xFF565D5A),
    surfaceTint = Palette.Pine700,
    inverseSurface = Color(0xFF2A2F2D),
    inverseOnSurface = Color(0xFFF2EFE8),
    error = Color(0xFFA23B2C),
    onError = Color.White,
    errorContainer = Color(0xFFF8DCD5),
    onErrorContainer = Color(0xFF3D0D06),
    outline = Color(0xFF858A83),
    outlineVariant = Color(0xFFE0DACD),
    scrim = Color.Black,
    surfaceBright = Palette.Ivory,
    surfaceDim = Color(0xFFDCD6C8),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFAF8F2),
    surfaceContainer = Color(0xFFF0ECE2),
    surfaceContainerHigh = Color(0xFFE9E4D8),
    surfaceContainerHighest = Color(0xFFE2DCCE),
)

internal val DarkColors = darkColorScheme(
    primary = Palette.Pine300,
    onPrimary = Color(0xFF06261F),
    primaryContainer = Color(0xFF1A463E),
    onPrimaryContainer = Color(0xFFCFE9E0),
    inversePrimary = Palette.Pine700,
    secondary = Color(0xFFDDB86F),
    onSecondary = Color(0xFF3A2A08),
    secondaryContainer = Color(0xFF4D3B16),
    onSecondaryContainer = Color(0xFFF2E2BE),
    tertiary = Color(0xFFB3C4E2),
    onTertiary = Color(0xFF1E2B42),
    tertiaryContainer = Color(0xFF222F44),
    onTertiaryContainer = Color(0xFFDDE4F0),
    background = Palette.Night,
    onBackground = Color(0xFFE8E5DD),
    surface = Palette.Night,
    onSurface = Color(0xFFE8E5DD),
    surfaceVariant = Color(0xFF2A3331),
    onSurfaceVariant = Color(0xFFA5ADA9),
    surfaceTint = Palette.Pine300,
    inverseSurface = Color(0xFFE8E5DD),
    inverseOnSurface = Color(0xFF1A1E1D),
    error = Color(0xFFF0A193),
    onError = Color(0xFF4A1209),
    errorContainer = Color(0xFF6B261A),
    onErrorContainer = Color(0xFFF8DCD5),
    outline = Color(0xFF6F7874),
    outlineVariant = Color(0xFF2A3331),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A3331),
    surfaceDim = Palette.Night,
    surfaceContainerLowest = Color(0xFF090D0C),
    surfaceContainerLow = Color(0xFF121817),
    surfaceContainer = Color(0xFF161D1C),
    surfaceContainerHigh = Color(0xFF1C2423),
    surfaceContainerHighest = Color(0xFF232C2A),
)

/** A container/content pair, e.g. for a tinted icon tile. */
@Immutable
data class TonePair(val container: Color, val content: Color)

/** Semantic colours Material does not model directly. Read through `LucentoTheme.extendedColors`. */
@Immutable
data class LucentoExtendedColors(
    /** Income and gains. Always paired with a "+" sign, never colour alone. */
    val positive: Color,
    val positiveContainer: Color,
    /** Debt, overspending and destructive states. Paired with "−" or explicit wording. */
    val negative: Color,
    val warning: Color,
    /** Brass for decorative emphasis (rules, highlights). Not for small text on light surfaces. */
    val accent: Color,
    /** Brass that meets text contrast on the app background. */
    val accentText: Color,
    /** The pine "statement" panel used for the net-worth hero. */
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroRule: Color,
    /** Charts: completed periods, the current period, and empty tracks. */
    val chartBar: Color,
    val chartBarMuted: Color,
    val chartHighlight: Color,
    val track: Color,
    val brandMark: Color,
    val brandMarkOn: Color,
    val cash: TonePair,
    val bank: TonePair,
    val eWallet: TonePair,
    val savings: TonePair,
    val credit: TonePair,
) {
    fun tone(type: WalletType): TonePair = when (type) {
        WalletType.CASH -> cash
        WalletType.BANK -> bank
        WalletType.E_WALLET -> eWallet
        WalletType.SAVINGS -> savings
        WalletType.CREDIT_CARD -> credit
    }
}

internal val LightExtended = LucentoExtendedColors(
    positive = Color(0xFF1F6E4B),
    positiveContainer = Color(0xFFDCEEE3),
    negative = Color(0xFFA23B2C),
    warning = Color(0xFF8E5F0B),
    accent = Palette.Brass300,
    accentText = Palette.Brass700,
    hero = Palette.Pine850,
    onHero = Palette.Cream,
    onHeroMuted = Color(0xFFA9C3BA),
    heroRule = Color(0xFF2C5A51),
    chartBar = Palette.Pine700,
    chartBarMuted = Color(0xFFB9CCC4),
    chartHighlight = Palette.Brass500,
    track = Color(0xFFE6E1D5),
    brandMark = Palette.Pine850,
    brandMarkOn = Color(0xFFF3EEE3),
    cash = TonePair(Palette.Brass100, Color(0xFF6B5220)),
    bank = TonePair(Palette.Pine100, Palette.Pine700),
    eWallet = TonePair(Color(0xFFDDE4F0), Color(0xFF33496B)),
    savings = TonePair(Color(0xFFE2EAD3), Color(0xFF4A5E26)),
    credit = TonePair(Color(0xFFF3DED6), Color(0xFF8A3B26)),
)

internal val DarkExtended = LucentoExtendedColors(
    positive = Color(0xFF7FD1A6),
    positiveContainer = Color(0xFF173A2B),
    negative = Color(0xFFF0A193),
    warning = Color(0xFFE6B566),
    accent = Palette.Brass300,
    accentText = Color(0xFFE2C07E),
    hero = Color(0xFF163A33),
    onHero = Color(0xFFF2EEE4),
    onHeroMuted = Color(0xFFA3BEB5),
    heroRule = Color(0xFF2B5249),
    chartBar = Palette.Pine300,
    chartBarMuted = Color(0xFF2F4A44),
    chartHighlight = Palette.Brass300,
    track = Color(0xFF232C2A),
    brandMark = Color(0xFFF3EEE3),
    brandMarkOn = Palette.Pine850,
    cash = TonePair(Color(0xFF3D3118), Color(0xFFE8CD8E)),
    bank = TonePair(Color(0xFF1A3F38), Color(0xFF9AD3C3)),
    eWallet = TonePair(Color(0xFF222F44), Color(0xFFB3C4E2)),
    savings = TonePair(Color(0xFF2B3519), Color(0xFFC2D69A)),
    credit = TonePair(Color(0xFF43241B), Color(0xFFF0B4A0)),
)
