package com.lucento.app.core.designsystem.theme

import androidx.annotation.DrawableRes
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lucento.app.R
import com.lucento.app.data.preferences.ThemeId

/** One mode of a theme: the Material roles plus Lucento's extended tokens. */
@Immutable
data class ThemeColors(val scheme: ColorScheme, val extended: LucentoExtendedColors)

/** How a drawn card chip is plated. */
enum class CardMetal { GOLD, SILVER }

/**
 * The Home net-balance card in a theme (owner, 2026-11-05: the card takes the theme). It stays a physical card — the
 * same face in light and dark mode — with light ink on a deep diagonal gradient from [start] (top left) to [end].
 * The theme's landscape is printed on it as tone-on-tone silhouettes: scene pixels darker than [artThreshold] (luma,
 * 0–255) darken the face, brighter ones (sky, sun, moon, aurora) leave it as it is. Printing can only darken, so it
 * can only raise the ink's contrast; `ThemeContrastTest` checks the plain face. Gain and loss keep [Gain] / [Loss].
 */
@Immutable
data class BalanceCardFace(
    val start: Color,
    val end: Color,
    val ink: Color,
    val inkMuted: Color,
    /** The two month bars in the trend, earlier then current. */
    val bars: List<Color>,
    val metal: CardMetal,
    val artThreshold: Float,
) {
    companion object {
        /** The month-on-month change, up and down: the same in every theme, and always beside a signed figure. */
        val Gain = Color(0xFFA7F3C5)
        val Loss = Color(0xFFFFC2B8)
    }
}

/**
 * A curated colour theme (docs/DESIGN.md › Themes). A theme re-voices the brand — surfaces, primary, hero panel,
 * accents, chart series and its landscape — and never the financial semantics: positive / negative / warning, category
 * tiles and wallet faces are the same in every theme (see [BrandColors]). Every theme passes the same WCAG AA gates
 * (`ThemeContrastTest`) and its chart series the CVD gates (`DataSeriesTest`).
 */
@Immutable
data class ThemeSpec(
    val id: ThemeId,
    val displayName: String,
    /** One line for the theme picker and its spoken description. */
    val character: String,
    /** Null for a dark-only theme (Midnight): the light/dark mode is ignored while it is chosen. */
    val light: ThemeColors?,
    val dark: ThemeColors,
    /** The theme's landscape (decorative, never behind text): hero horizon band, empty states, picker. */
    @DrawableRes val scenery: Int,
    /** True when [scenery] is already a night scene, so dark mode shows it as painted instead of dimming it. */
    val sceneryIsNight: Boolean,
    /** The Home net-balance card's face. */
    val balanceCard: BalanceCardFace,
    /** The onboarding welcome artwork; Sunrise keeps the approved "A Brighter Little World" crop. */
    @DrawableRes val welcomeArt: Int = scenery,
) {
    val darkOnly: Boolean get() = light == null

    /** Whether this theme renders dark when the user's mode resolves to [modeIsDark]. */
    fun isDark(modeIsDark: Boolean): Boolean = light == null || modeIsDark

    fun colors(dark: Boolean): ThemeColors = if (dark || light == null) this.dark else light

    companion object {
        val Sunrise = ThemeSpec(
            id = ThemeId.SUNRISE,
            displayName = "Sunrise",
            character = "The signature. Warm ivory, electric blue and sunlight.",
            light = ThemeColors(LightColors, LightExtended),
            dark = ThemeColors(DarkColors, DarkExtended),
            scenery = R.drawable.scenery_sunrise_panorama,
            sceneryIsNight = false,
            // The approved Figma face, its start a shade deeper so the muted labels reach 4.5:1 in the bright corner.
            balanceCard = BalanceCardFace(
                start = Color(0xFF245BD8),
                end = Color(0xFF162D88),
                ink = Color.White,
                inkMuted = Color(0xFFDCE5FF),
                bars = listOf(Color(0xFF89C4FF), Color(0xFFC2E2FF)),
                metal = CardMetal.GOLD,
                artThreshold = 215f,
            ),
            welcomeArt = R.drawable.illustration_sunrise_landscape,
        )

        val Midnight = ThemeSpec(
            id = ThemeId.MIDNIGHT,
            displayName = "Midnight",
            character = "Quiet luxury. Indigo black with champagne gold. Always dark.",
            light = null,
            dark = ThemeColors(MidnightColors.scheme, DarkExtended.withBrand(MidnightColors.brand)),
            scenery = R.drawable.scenery_midnight_panorama,
            sceneryIsNight = true,
            balanceCard = BalanceCardFace(
                start = Color(0xFF2B3456),
                end = Color(0xFF0C1020),
                ink = Color(0xFFF4F1E8),
                inkMuted = Color(0xFFD8C795),
                bars = listOf(Color(0xFFC9AE67), Color(0xFFE8D9A8)),
                metal = CardMetal.GOLD,
                artThreshold = 110f,
            ),
        )

        val Grove = ThemeSpec(
            id = ThemeId.GROVE,
            displayName = "Grove",
            character = "Sage paper, forest green and harvest gold.",
            light = ThemeColors(GroveColors.lightScheme, LightExtended.withBrand(GroveColors.lightBrand)),
            dark = ThemeColors(GroveColors.darkScheme, DarkExtended.withBrand(GroveColors.darkBrand)),
            scenery = R.drawable.scenery_grove_panorama,
            sceneryIsNight = false,
            balanceCard = BalanceCardFace(
                start = Color(0xFF2A6E4E),
                end = Color(0xFF123423),
                ink = Color.White,
                inkMuted = Color(0xFFD3EADC),
                bars = listOf(Color(0xFFD9B45E), Color(0xFFEED9A3)),
                metal = CardMetal.GOLD,
                artThreshold = 210f,
            ),
        )

        val Mono = ThemeSpec(
            id = ThemeId.MONO,
            displayName = "Mono",
            character = "Understated graphite. The money is the colour.",
            light = ThemeColors(MonoColors.lightScheme, LightExtended.withBrand(MonoColors.lightBrand)),
            dark = ThemeColors(MonoColors.darkScheme, DarkExtended.withBrand(MonoColors.darkBrand)),
            scenery = R.drawable.scenery_mono_panorama,
            sceneryIsNight = false,
            balanceCard = BalanceCardFace(
                start = Color(0xFF454B5A),
                end = Color(0xFF1B1E26),
                ink = Color.White,
                inkMuted = Color(0xFFCDD2DC),
                bars = listOf(Color(0xFFA3ADC0), Color(0xFFD3D9E3)),
                metal = CardMetal.SILVER,
                artThreshold = 220f,
            ),
        )

        val Aurora = ThemeSpec(
            id = ThemeId.AURORA,
            displayName = "Aurora",
            character = "Violet with teal and magenta light.",
            light = ThemeColors(AuroraColors.lightScheme, LightExtended.withBrand(AuroraColors.lightBrand)),
            dark = ThemeColors(AuroraColors.darkScheme, DarkExtended.withBrand(AuroraColors.darkBrand)),
            scenery = R.drawable.scenery_aurora_panorama,
            sceneryIsNight = true,
            balanceCard = BalanceCardFace(
                start = Color(0xFF5C43CF),
                end = Color(0xFF22308F),
                ink = Color.White,
                inkMuted = Color(0xFFE0D9FB),
                bars = listOf(Color(0xFF4FD8C6), Color(0xFFA9EEE4)),
                metal = CardMetal.SILVER,
                artThreshold = 140f,
            ),
        )

        /** Picker order: the brand theme first. */
        val all: List<ThemeSpec> = listOf(Sunrise, Midnight, Grove, Mono, Aurora)

        fun of(id: ThemeId): ThemeSpec = all.first { it.id == id }
    }
}

// ---------------------------------------------------------------- Midnight (dark only)

private object MidnightColors {
    val scheme = DarkColors.copy(
        primary = Color(0xFF8FA8FF),
        onPrimary = Color(0xFF0B1026),
        primaryContainer = Color(0xFF26335E),
        onPrimaryContainer = Color(0xFFD9E1FF),
        inversePrimary = Color(0xFF4A63D6),
        secondary = Color(0xFFE3C87E),
        onSecondary = Color(0xFF17233F),
        secondaryContainer = Color(0xFF32290F),
        onSecondaryContainer = Color(0xFFF0DFAE),
        background = Color(0xFF0A0E1A),
        onBackground = Color(0xFFECEFF8),
        surface = Color(0xFF0A0E1A),
        onSurface = Color(0xFFECEFF8),
        surfaceVariant = Color(0xFF252F4A),
        onSurfaceVariant = Color(0xFF9AA5C2),
        surfaceTint = Color(0xFF8FA8FF),
        inverseSurface = Color(0xFFECEFF8),
        inverseOnSurface = Color(0xFF121829),
        outline = Color(0xFF6B7594),
        outlineVariant = Color(0xFF222B45),
        surfaceBright = Color(0xFF252F4A),
        surfaceDim = Color(0xFF0A0E1A),
        surfaceContainerLowest = Color(0xFF070A14),
        surfaceContainerLow = Color(0xFF0F1421),
        surfaceContainer = Color(0xFF121829),
        surfaceContainerHigh = Color(0xFF1A2338),
        surfaceContainerHighest = Color(0xFF252F4A),
    )
    val brand = BrandColors(
        info = Color(0xFF8FA8FF),
        infoContainer = Color(0xFF1A2347),
        accent = Color(0xFFE3C87E),
        accentText = Color(0xFFE3C87E),
        warmAccent = Color(0xFFE0A47A),
        onAccent = Color(0xFF17233F),
        borderDefault = Color(0xFF242E4A),
        shadow = Color.Black,
        highlight = Color(0xFF32290F),
        onHighlight = Color(0xFFF0DFAE),
        hero = Color(0xFF182036),
        onHero = Color(0xFFECEFF8),
        onHeroMuted = Color(0xFF9AA5C2),
        heroRule = Color(0xFF2A3452),
        chartBar = Color(0xFF8FA8FF),
        chartBarMuted = Color(0xFF283458),
        chartHighlight = Color(0xFF8FA8FF),
        track = Color(0xFF242E4A),
        categorical = series(0xFF7482D1, 0xFFC66878, 0xFF2A94C7, 0xFF609B53, 0xFFA472BB, 0xFFA5851D, 0xFF079E92),
    )
}

// ---------------------------------------------------------------- Grove

private object GroveColors {
    val lightScheme = LightColors.copy(
        primary = Color(0xFF2E7D5B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDFF0E5),
        onPrimaryContainer = Color(0xFF1F5F45),
        inversePrimary = Color(0xFF83CCA5),
        secondary = Color(0xFF7E5A0B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3EBD3),
        onSecondaryContainer = Color(0xFF3A3012),
        background = Color(0xFFF5F6EF),
        onBackground = Color(0xFF1D2B23),
        surface = Color(0xFFF5F6EF),
        onSurface = Color(0xFF1D2B23),
        surfaceVariant = Color(0xFFE3E8DC),
        onSurfaceVariant = Color(0xFF5A6A5E),
        surfaceTint = Color(0xFF2E7D5B),
        inverseSurface = Color(0xFF1D2B23),
        inverseOnSurface = Color(0xFFEEF2EA),
        outline = Color(0xFF75826F),
        outlineVariant = Color(0xFFE4E7DD),
        surfaceBright = Color(0xFFF5F6EF),
        surfaceDim = Color(0xFFE6E9DF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFAFAF5),
        surfaceContainer = Color(0xFFE9EFE2),
        surfaceContainerHigh = Color(0xFFE0E9D8),
        surfaceContainerHighest = Color(0xFFD6E1CC),
    )
    val lightBrand = BrandColors(
        info = Color(0xFF2E7D5B),
        infoContainer = Color(0xFFDFF0E5),
        accent = Color(0xFFD9A441),
        accentText = Color(0xFF7E5A0B),
        warmAccent = Color(0xFFE0955F),
        onAccent = Color(0xFF1D2B23),
        borderDefault = Color(0xFFD9DDD0),
        shadow = Color(0xFF1D2B23),
        highlight = Color(0xFFF3EBD3),
        onHighlight = Color(0xFF3A3012),
        hero = Color(0xFF24543F),
        onHero = Color.White,
        onHeroMuted = Color(0xFFC9E2D3),
        heroRule = Color(0xFF3A6B55),
        chartBar = Color(0xFF2E7D5B),
        chartBarMuted = Color(0xFFC5DECF),
        chartHighlight = Color(0xFF2E7D5B),
        track = Color(0xFFE3E8DC),
        categorical = series(0xFF008757, 0xFF8E57A5, 0xFFAE5528, 0xFF007CA5, 0xFF8A6E08, 0xFF3772BB, 0xFFAA4D75),
    )
    val darkScheme = DarkColors.copy(
        primary = Color(0xFF83CCA5),
        onPrimary = Color(0xFF0C2117),
        primaryContainer = Color(0xFF1F4733),
        onPrimaryContainer = Color(0xFFCFEBD9),
        inversePrimary = Color(0xFF2E7D5B),
        secondary = Color(0xFFE5BD5E),
        onSecondary = Color(0xFF1D2B23),
        secondaryContainer = Color(0xFF373014),
        onSecondaryContainer = Color(0xFFF0DFAE),
        background = Color(0xFF0F1511),
        onBackground = Color(0xFFEFF5EE),
        surface = Color(0xFF0F1511),
        onSurface = Color(0xFFEFF5EE),
        surfaceVariant = Color(0xFF2A3A2E),
        onSurfaceVariant = Color(0xFFA4B6A7),
        surfaceTint = Color(0xFF83CCA5),
        inverseSurface = Color(0xFFEFF5EE),
        inverseOnSurface = Color(0xFF17221A),
        outline = Color(0xFF6E8073),
        outlineVariant = Color(0xFF223026),
        surfaceBright = Color(0xFF2A3A2E),
        surfaceDim = Color(0xFF0F1511),
        surfaceContainerLowest = Color(0xFF0A0F0C),
        surfaceContainerLow = Color(0xFF131B15),
        surfaceContainer = Color(0xFF17221A),
        surfaceContainerHigh = Color(0xFF1F2D23),
        surfaceContainerHighest = Color(0xFF2A3A2E),
    )
    val darkBrand = BrandColors(
        info = Color(0xFF83CCA5),
        infoContainer = Color(0xFF173826),
        accent = Color(0xFFE5BD5E),
        accentText = Color(0xFFE5BD5E),
        warmAccent = Color(0xFFE0955F),
        onAccent = Color(0xFF1D2B23),
        borderDefault = Color(0xFF2B3B2F),
        shadow = Color.Black,
        highlight = Color(0xFF373014),
        onHighlight = Color(0xFFF0DFAE),
        hero = Color(0xFF1F2D23),
        onHero = Color(0xFFEFF5EE),
        onHeroMuted = Color(0xFFA4B6A7),
        heroRule = Color(0xFF2B3B2F),
        chartBar = Color(0xFF83CCA5),
        chartBarMuted = Color(0xFF2A4636),
        chartHighlight = Color(0xFF83CCA5),
        track = Color(0xFF2B3B2F),
        categorical = series(0xFF379F70, 0xFF548BD0, 0xFFC1688D, 0xFFA5851D, 0xFFA472BB, 0xFFC47048, 0xFF179AAC),
    )
}

// ---------------------------------------------------------------- Mono

private object MonoColors {
    val lightScheme = LightColors.copy(
        primary = Color(0xFF46557A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7EAF1),
        onPrimaryContainer = Color(0xFF36446A),
        inversePrimary = Color(0xFFA8B6D8),
        secondary = Color(0xFF525B6B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFECEEF1),
        onSecondaryContainer = Color(0xFF2A2E38),
        background = Color(0xFFF8F8F7),
        onBackground = Color(0xFF1C1F27),
        surface = Color(0xFFF8F8F7),
        onSurface = Color(0xFF1C1F27),
        surfaceVariant = Color(0xFFE8E9EC),
        onSurfaceVariant = Color(0xFF636977),
        surfaceTint = Color(0xFF46557A),
        inverseSurface = Color(0xFF1C1F27),
        inverseOnSurface = Color(0xFFF1F2F4),
        outline = Color(0xFF7D828E),
        outlineVariant = Color(0xFFE9EAEC),
        surfaceBright = Color(0xFFF8F8F7),
        surfaceDim = Color(0xFFE7E8EA),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFBFBFA),
        surfaceContainer = Color(0xFFEFF0F2),
        surfaceContainerHigh = Color(0xFFE9EBEE),
        surfaceContainerHighest = Color(0xFFE0E3E7),
    )
    val lightBrand = BrandColors(
        info = Color(0xFF46557A),
        infoContainer = Color(0xFFE7EAF1),
        accent = Color(0xFFC9CFD9),
        accentText = Color(0xFF525B6B),
        warmAccent = Color(0xFFB5ADA3),
        onAccent = Color(0xFF1C1F27),
        borderDefault = Color(0xFFE0E1E4),
        shadow = Color(0xFF1C1F27),
        highlight = Color(0xFFECEEF1),
        onHighlight = Color(0xFF2A2E38),
        hero = Color(0xFF262B38),
        onHero = Color.White,
        onHeroMuted = Color(0xFFB9BFCC),
        heroRule = Color(0xFF3A4050),
        chartBar = Color(0xFF46557A),
        chartBarMuted = Color(0xFFCDD3E0),
        chartHighlight = Color(0xFF46557A),
        track = Color(0xFFE8E9EC),
        categorical = series(0xFF4A70B7, 0xFF33854A, 0xFF875CA5, 0xFFA65C20, 0xFF007CA5, 0xFF86700A, 0xFFA4517B),
    )
    val darkScheme = DarkColors.copy(
        primary = Color(0xFFA8B6D8),
        onPrimary = Color(0xFF151924),
        primaryContainer = Color(0xFF323C52),
        onPrimaryContainer = Color(0xFFDDE3F0),
        inversePrimary = Color(0xFF46557A),
        secondary = Color(0xFFC2C9D6),
        onSecondary = Color(0xFF1C1F27),
        secondaryContainer = Color(0xFF2A2E38),
        onSecondaryContainer = Color(0xFFD8DEE8),
        background = Color(0xFF121316),
        onBackground = Color(0xFFF1F2F4),
        surface = Color(0xFF121316),
        onSurface = Color(0xFFF1F2F4),
        surfaceVariant = Color(0xFF2C3038),
        onSurfaceVariant = Color(0xFF9DA4B0),
        surfaceTint = Color(0xFFA8B6D8),
        inverseSurface = Color(0xFFF1F2F4),
        inverseOnSurface = Color(0xFF1A1C21),
        outline = Color(0xFF6E7480),
        outlineVariant = Color(0xFF25282E),
        surfaceBright = Color(0xFF2C3038),
        surfaceDim = Color(0xFF121316),
        surfaceContainerLowest = Color(0xFF0C0D0F),
        surfaceContainerLow = Color(0xFF16171B),
        surfaceContainer = Color(0xFF1A1C21),
        surfaceContainerHigh = Color(0xFF22252B),
        surfaceContainerHighest = Color(0xFF2C3038),
    )
    val darkBrand = BrandColors(
        info = Color(0xFFA8B6D8),
        infoContainer = Color(0xFF262D3D),
        accent = Color(0xFFC2C9D6),
        accentText = Color(0xFFC2C9D6),
        warmAccent = Color(0xFFB5ADA3),
        onAccent = Color(0xFF1C1F27),
        borderDefault = Color(0xFF2D3038),
        shadow = Color.Black,
        highlight = Color(0xFF2A2E38),
        onHighlight = Color(0xFFD8DEE8),
        hero = Color(0xFF22252B),
        onHero = Color(0xFFF1F2F4),
        onHeroMuted = Color(0xFF9DA4B0),
        heroRule = Color(0xFF2D3038),
        chartBar = Color(0xFFA8B6D8),
        chartBarMuted = Color(0xFF343B4C),
        chartHighlight = Color(0xFFA8B6D8),
        track = Color(0xFF2D3038),
        categorical = series(0xFF6088D2, 0xFFC0743B, 0xFF1996C3, 0xFF4D9D61, 0xFF9F73BF, 0xFFA0881E, 0xFFBE6993),
    )
}

// ---------------------------------------------------------------- Aurora

private object AuroraColors {
    val lightScheme = LightColors.copy(
        primary = Color(0xFF6C4DE0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFECE6FC),
        onPrimaryContainer = Color(0xFF5133C0),
        inversePrimary = Color(0xFFA98FFF),
        secondary = Color(0xFF0A7467),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEFE8FF),
        onSecondaryContainer = Color(0xFF35246E),
        background = Color(0xFFF8F6FD),
        onBackground = Color(0xFF231E3B),
        surface = Color(0xFFF8F6FD),
        onSurface = Color(0xFF231E3B),
        surfaceVariant = Color(0xFFE9E5F4),
        onSurfaceVariant = Color(0xFF675F8A),
        surfaceTint = Color(0xFF6C4DE0),
        inverseSurface = Color(0xFF231E3B),
        inverseOnSurface = Color(0xFFF2EFFC),
        outline = Color(0xFF857DA3),
        outlineVariant = Color(0xFFEAE6F3),
        surfaceBright = Color(0xFFF8F6FD),
        surfaceDim = Color(0xFFE8E4F2),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFBFAFE),
        surfaceContainer = Color(0xFFEEEAF9),
        surfaceContainerHigh = Color(0xFFE6E0F7),
        surfaceContainerHighest = Color(0xFFDCD4F3),
    )
    val lightBrand = BrandColors(
        info = Color(0xFF6C4DE0),
        infoContainer = Color(0xFFECE6FC),
        accent = Color(0xFF2BB8A8),
        accentText = Color(0xFF0A7467),
        warmAccent = Color(0xFFE86AA6),
        onAccent = Color(0xFF231E3B),
        borderDefault = Color(0xFFE1DCEF),
        shadow = Color(0xFF231E3B),
        highlight = Color(0xFFEFE8FF),
        onHighlight = Color(0xFF35246E),
        // The system's only gradient, on the hero panel alone. White and muted text pass AA at both ends.
        hero = Color(0xFF4B37B8),
        heroEnd = Color(0xFF2F4DB0),
        onHero = Color.White,
        onHeroMuted = Color(0xFFD7CFF6),
        heroRule = Color(0xFF6450C8),
        chartBar = Color(0xFF6C4DE0),
        chartBarMuted = Color(0xFFD3C8F7),
        chartHighlight = Color(0xFF6C4DE0),
        track = Color(0xFFE9E5F4),
        categorical = series(0xFF795DBA, 0xFF3F842E, 0xFF3772BB, 0xFFB34F2A, 0xFF007CA5, 0xFF8A6E08, 0xFFA84B84),
    )
    val darkScheme = DarkColors.copy(
        primary = Color(0xFFA98FFF),
        onPrimary = Color(0xFF170F3A),
        primaryContainer = Color(0xFF342973),
        onPrimaryContainer = Color(0xFFE3DBFF),
        inversePrimary = Color(0xFF6C4DE0),
        secondary = Color(0xFF4FD8C6),
        onSecondary = Color(0xFF231E3B),
        secondaryContainer = Color(0xFF332960),
        onSecondaryContainer = Color(0xFFE3DBFF),
        background = Color(0xFF0D0A1C),
        onBackground = Color(0xFFF2EFFC),
        surface = Color(0xFF0D0A1C),
        onSurface = Color(0xFFF2EFFC),
        surfaceVariant = Color(0xFF2D2558),
        onSurfaceVariant = Color(0xFFB0A7D2),
        surfaceTint = Color(0xFFA98FFF),
        inverseSurface = Color(0xFFF2EFFC),
        inverseOnSurface = Color(0xFF171331),
        outline = Color(0xFF776E9E),
        outlineVariant = Color(0xFF262049),
        surfaceBright = Color(0xFF2D2558),
        surfaceDim = Color(0xFF0D0A1C),
        surfaceContainerLowest = Color(0xFF080612),
        surfaceContainerLow = Color(0xFF120E27),
        surfaceContainer = Color(0xFF171331),
        surfaceContainerHigh = Color(0xFF211B45),
        surfaceContainerHighest = Color(0xFF2D2558),
    )
    val darkBrand = BrandColors(
        info = Color(0xFFA98FFF),
        infoContainer = Color(0xFF251D52),
        accent = Color(0xFF4FD8C6),
        accentText = Color(0xFF4FD8C6),
        warmAccent = Color(0xFFF08BBE),
        onAccent = Color(0xFF231E3B),
        borderDefault = Color(0xFF2D2558),
        shadow = Color.Black,
        highlight = Color(0xFF332960),
        onHighlight = Color(0xFFE3DBFF),
        hero = Color(0xFF211B45),
        heroEnd = Color(0xFF2A2560),
        onHero = Color(0xFFF2EFFC),
        onHeroMuted = Color(0xFFB0A7D2),
        heroRule = Color(0xFF2D2558),
        chartBar = Color(0xFFA98FFF),
        chartBarMuted = Color(0xFF352C6B),
        chartHighlight = Color(0xFFA98FFF),
        track = Color(0xFF2D2558),
        categorical = series(0xFF8F77CF, 0xFF079E92, 0xFFA88400, 0xFFBF669B, 0xFF5B9C4D, 0xFF448DD4, 0xFFCA6B49),
    )
}

// ---------------------------------------------------------------- Cross-fade between themes

internal fun lerp(a: ThemeColors, b: ThemeColors, t: Float): ThemeColors = when {
    t <= 0f -> a
    t >= 1f -> b
    else -> ThemeColors(lerp(a.scheme, b.scheme, t), lerp(a.extended, b.extended, t))
}

private fun lerp(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme = a.copy(
    primary = mix(a.primary, b.primary, t),
    onPrimary = mix(a.onPrimary, b.onPrimary, t),
    primaryContainer = mix(a.primaryContainer, b.primaryContainer, t),
    onPrimaryContainer = mix(a.onPrimaryContainer, b.onPrimaryContainer, t),
    inversePrimary = mix(a.inversePrimary, b.inversePrimary, t),
    secondary = mix(a.secondary, b.secondary, t),
    onSecondary = mix(a.onSecondary, b.onSecondary, t),
    secondaryContainer = mix(a.secondaryContainer, b.secondaryContainer, t),
    onSecondaryContainer = mix(a.onSecondaryContainer, b.onSecondaryContainer, t),
    tertiary = mix(a.tertiary, b.tertiary, t),
    onTertiary = mix(a.onTertiary, b.onTertiary, t),
    tertiaryContainer = mix(a.tertiaryContainer, b.tertiaryContainer, t),
    onTertiaryContainer = mix(a.onTertiaryContainer, b.onTertiaryContainer, t),
    background = mix(a.background, b.background, t),
    onBackground = mix(a.onBackground, b.onBackground, t),
    surface = mix(a.surface, b.surface, t),
    onSurface = mix(a.onSurface, b.onSurface, t),
    surfaceVariant = mix(a.surfaceVariant, b.surfaceVariant, t),
    onSurfaceVariant = mix(a.onSurfaceVariant, b.onSurfaceVariant, t),
    surfaceTint = mix(a.surfaceTint, b.surfaceTint, t),
    inverseSurface = mix(a.inverseSurface, b.inverseSurface, t),
    inverseOnSurface = mix(a.inverseOnSurface, b.inverseOnSurface, t),
    error = mix(a.error, b.error, t),
    onError = mix(a.onError, b.onError, t),
    errorContainer = mix(a.errorContainer, b.errorContainer, t),
    onErrorContainer = mix(a.onErrorContainer, b.onErrorContainer, t),
    outline = mix(a.outline, b.outline, t),
    outlineVariant = mix(a.outlineVariant, b.outlineVariant, t),
    scrim = mix(a.scrim, b.scrim, t),
    surfaceBright = mix(a.surfaceBright, b.surfaceBright, t),
    surfaceDim = mix(a.surfaceDim, b.surfaceDim, t),
    surfaceContainerLowest = mix(a.surfaceContainerLowest, b.surfaceContainerLowest, t),
    surfaceContainerLow = mix(a.surfaceContainerLow, b.surfaceContainerLow, t),
    surfaceContainer = mix(a.surfaceContainer, b.surfaceContainer, t),
    surfaceContainerHigh = mix(a.surfaceContainerHigh, b.surfaceContainerHigh, t),
    surfaceContainerHighest = mix(a.surfaceContainerHighest, b.surfaceContainerHighest, t),
)

/** [lerp] that leaves an unchanged colour exactly as it is (Compose's lerp round-trips through Oklab). */
private fun mix(a: Color, b: Color, t: Float): Color = if (a == b) a else lerp(a, b, t)

private fun lerp(a: TonePair, b: TonePair, t: Float): TonePair =
    if (a == b) a else TonePair(mix(a.container, b.container, t), mix(a.content, b.content, t))

private fun lerp(a: List<Color>, b: List<Color>, t: Float): List<Color> =
    if (a == b || a.size != b.size) b else a.zip(b) { x, y -> mix(x, y, t) }

private fun lerp(a: LucentoExtendedColors, b: LucentoExtendedColors, t: Float): LucentoExtendedColors = LucentoExtendedColors(
    positive = mix(a.positive, b.positive, t),
    positiveContainer = mix(a.positiveContainer, b.positiveContainer, t),
    negative = mix(a.negative, b.negative, t),
    negativeContainer = mix(a.negativeContainer, b.negativeContainer, t),
    warning = mix(a.warning, b.warning, t),
    warningContainer = mix(a.warningContainer, b.warningContainer, t),
    warningIndicator = mix(a.warningIndicator, b.warningIndicator, t),
    negativeIndicator = mix(a.negativeIndicator, b.negativeIndicator, t),
    info = mix(a.info, b.info, t),
    infoContainer = mix(a.infoContainer, b.infoContainer, t),
    accent = mix(a.accent, b.accent, t),
    accentText = mix(a.accentText, b.accentText, t),
    warmAccent = mix(a.warmAccent, b.warmAccent, t),
    onAccent = mix(a.onAccent, b.onAccent, t),
    borderDefault = mix(a.borderDefault, b.borderDefault, t),
    shadow = mix(a.shadow, b.shadow, t),
    highlight = mix(a.highlight, b.highlight, t),
    onHighlight = mix(a.onHighlight, b.onHighlight, t),
    hero = mix(a.hero, b.hero, t),
    onHero = mix(a.onHero, b.onHero, t),
    onHeroMuted = mix(a.onHeroMuted, b.onHeroMuted, t),
    heroRule = mix(a.heroRule, b.heroRule, t),
    chartBar = mix(a.chartBar, b.chartBar, t),
    chartBarMuted = mix(a.chartBarMuted, b.chartBarMuted, t),
    chartHighlight = mix(a.chartHighlight, b.chartHighlight, t),
    track = mix(a.track, b.track, t),
    categorical = lerp(a.categorical, b.categorical, t),
    success = mix(a.success, b.success, t),
    cash = lerp(a.cash, b.cash, t),
    bank = lerp(a.bank, b.bank, t),
    eWallet = lerp(a.eWallet, b.eWallet, t),
    savings = lerp(a.savings, b.savings, t),
    credit = lerp(a.credit, b.credit, t),
    loan = lerp(a.loan, b.loan, t),
    categoryTones = if (a.categoryTones == b.categoryTones) b.categoryTones else b.categoryTones.mapValues { (key, to) ->
        a.categoryTones[key]?.let { from -> lerp(from, to, t) } ?: to
    },
    categoryFallback = lerp(a.categoryFallback, b.categoryFallback, t),
    heroEnd = mix(a.heroEnd, b.heroEnd, t),
)
