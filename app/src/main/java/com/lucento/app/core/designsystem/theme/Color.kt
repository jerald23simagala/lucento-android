package com.lucento.app.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.lucento.app.domain.WalletType

/*
 * Lucento palette — "Sunrise Energy": warm ivory canvas, midnight-navy ink and structure, electric blue for every
 * interaction, and small deliberate amounts of sunlight yellow / tangerine for Lucento's light (the leaf in the mark,
 * the mascot, milestones). Gold and orange never mean status: positive / warning / negative are separate semantic
 * tokens, always paired with a sign or words. No gradients in UI chrome.
 *
 * Contrast (WCAG 2.1, checked with a script; ratios against the surface they sit on):
 *   light  ink 15.3 · muted 5.5 (4.9 on pale sky) · primary 4.9 · white-on-primary 4.9 · on-primary-container 6.6
 *          positive 5.2 · warning 5.5 · negative 5.6 · navy-on-sunlight 10.1 · outline 3.6
 *   dark   ink 14.9 on cards · muted 8.9 · primary 6.7 · navy-on-primary 6.7 · positive 8.3 · warning 8.9
 *          negative 6.9 · sunlight text 11.5 · outline 4.2
 * Sunlight yellow (1.5:1) and tangerine (2.3:1) are decorative on light surfaces: never text, never the only cue.
 */
internal object Palette {
    // Brand — Sunrise Energy (canonical)
    val SunlightYellow = Color(0xFFFFC928) // brand light, highlights
    val BrightTangerine = Color(0xFFFF8A24) // warm accent, logo warmth
    val ElectricBlue = Color(0xFF3563F5) // primary actions
    val SoftIvory = Color(0xFFFFFDF5) // light background
    val MidnightNavy = Color(0xFF17233F) // ink, dark surfaces
    val PaleSky = Color(0xFFEAF1FF) // secondary backgrounds, selected cards

    // Derived blues
    val BlueDeep = Color(0xFF2447C7) // blue text on pale sky
    val BlueHero = Color(0xFF2F56E8) // statement panel (white text 5.8:1)
    val BlueNight = Color(0xFF7C9AFF) // primary in the night theme
    val BlueAction = Color(0xFF5278FF) // night action accent (decorative fills, focus)

    // Derived warm
    val SunlightPale = Color(0xFFFFF4D1) // brand-highlight surface
    val AmberText = Color(0xFF9A5806) // text-safe amber (warning, accent text)

    // Semantic
    val Positive = Color(0xFF16865C) // rings and bars
    val PositiveText = Color(0xFF137A53)
    val Negative = Color(0xFFD64545) // indicators
    val NegativeText = Color(0xFFB83838)
    val Warning = Color(0xFFB86A08) // indicators

    // Night
    val NightBackground = Color(0xFF10182B)
    val NightSurface = Color(0xFF17233F)
    val NightElevated = Color(0xFF202E4D)
    val NightInk = Color(0xFFF8FAFF)
    val NightMuted = Color(0xFFB8C5DC)
}

internal val LightColors = lightColorScheme(
    primary = Palette.ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Palette.PaleSky,
    onPrimaryContainer = Palette.BlueDeep,
    inversePrimary = Palette.BlueNight,
    secondary = Palette.AmberText,
    onSecondary = Color.White,
    secondaryContainer = Palette.SunlightPale,
    onSecondaryContainer = Color(0xFF4A3000),
    tertiary = Palette.PositiveText,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3F5EC),
    onTertiaryContainer = Color(0xFF0B3D28),
    background = Palette.SoftIvory,
    onBackground = Palette.MidnightNavy,
    surface = Palette.SoftIvory,
    onSurface = Palette.MidnightNavy,
    surfaceVariant = Color(0xFFE8EDF7),
    onSurfaceVariant = Color(0xFF5E687D),
    surfaceTint = Palette.ElectricBlue,
    inverseSurface = Palette.MidnightNavy,
    inverseOnSurface = Color(0xFFF1F4FB),
    error = Palette.NegativeText,
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF5C1414),
    outline = Color(0xFF7A869F),
    outlineVariant = Color(0xFFE8EAF0), // border-subtle
    scrim = Color.Black,
    surfaceBright = Palette.SoftIvory,
    surfaceDim = Color(0xFFE9EBF1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF9F1),
    surfaceContainer = Palette.PaleSky,
    surfaceContainerHigh = Color(0xFFE1EAFE),
    surfaceContainerHighest = Color(0xFFD8E3FD),
)

internal val DarkColors = darkColorScheme(
    primary = Palette.BlueNight,
    onPrimary = Palette.NightBackground,
    primaryContainer = Color(0xFF26397A),
    onPrimaryContainer = Color(0xFFE4EBFF),
    inversePrimary = Palette.ElectricBlue,
    secondary = Palette.SunlightYellow,
    onSecondary = Palette.MidnightNavy,
    secondaryContainer = Color(0xFF3D3214),
    onSecondaryContainer = Color(0xFFFFE9A8),
    tertiary = Color(0xFF3DD68C),
    onTertiary = Color(0xFF0A2E1E),
    tertiaryContainer = Color(0xFF123B2A),
    onTertiaryContainer = Color(0xFFC5F2DC),
    background = Palette.NightBackground,
    onBackground = Palette.NightInk,
    surface = Palette.NightBackground,
    onSurface = Palette.NightInk,
    surfaceVariant = Color(0xFF283859),
    onSurfaceVariant = Palette.NightMuted,
    surfaceTint = Palette.BlueNight,
    inverseSurface = Palette.NightInk,
    inverseOnSurface = Palette.MidnightNavy,
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF4A0E0E),
    errorContainer = Color(0xFF5A1E22),
    onErrorContainer = Color(0xFFFDE2E2),
    outline = Color(0xFF6F7C99),
    outlineVariant = Color(0xFF26365C),
    scrim = Color.Black,
    surfaceBright = Color(0xFF283859),
    surfaceDim = Palette.NightBackground,
    surfaceContainerLowest = Color(0xFF0B1222),
    surfaceContainerLow = Color(0xFF131D33),
    surfaceContainer = Palette.NightSurface,
    surfaceContainerHigh = Palette.NightElevated,
    surfaceContainerHighest = Color(0xFF283859),
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
    /** Spending, debt, overspending and destructive states. Paired with "−" or explicit wording. */
    val negative: Color,
    val negativeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    /** Fills for rings, bars and icons (not text): approaching a limit, over the limit. */
    val warningIndicator: Color,
    val negativeIndicator: Color,
    /** Guidance and neutral information (blue). */
    val info: Color,
    val infoContainer: Color,
    /** Sunlight yellow for decorative emphasis (the leaf, milestones). Not for small text on light surfaces. */
    val accent: Color,
    /** Yellow/amber that meets text contrast on the app background. */
    val accentText: Color,
    /** Bright tangerine: warm brand accent, never a status colour. */
    val warmAccent: Color,
    /** border-default: stronger card/section boundaries (decorative; control boundaries use colorScheme.outline). */
    val borderDefault: Color,
    /** Shadow tint for elevated surfaces (navy by day, black at night where shadows are rarely used). */
    val shadow: Color,
    /** Pale sunlight surface for brand-highlight moments; text on it uses [onHighlight]. */
    val highlight: Color,
    val onHighlight: Color,
    /** Solid brand panel for the one statement a screen is about (first-run setup). */
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroRule: Color,
    /** Charts: completed periods, the current period, and empty tracks. */
    val chartBar: Color,
    val chartBarMuted: Color,
    val chartHighlight: Color,
    val track: Color,
    /** Distinct, restrained colours for category shares (donut segments, legends). */
    val categorical: List<Color>,
    /** Progress that is going well (budget ring, goal bar). */
    val success: Color,
    val cash: TonePair,
    val bank: TonePair,
    val eWallet: TonePair,
    val savings: TonePair,
    val credit: TonePair,
    val loan: TonePair,
    /**
     * Category tiles: a tonal tile (pale tint by day, deep tint at night) with a saturated glyph, one hue per family
     * of spending (food coral, transport green, shopping rose, bills amber…). Purely decorative — the row's text and
     * signed amount carry the meaning — so the glyph only needs non-text contrast (≥ 3:1) on its own tile.
     */
    val categoryTones: Map<String, TonePair> = emptyMap(),
    val categoryFallback: TonePair = TonePair(Color(0xFFECEFF4), Color(0xFF56637D)),
) {
    fun categoryTone(iconKey: String?): TonePair = iconKey?.let { categoryTones[it] } ?: categoryFallback

    fun tone(type: WalletType): TonePair = when (type) {
        WalletType.CASH -> cash
        WalletType.BANK -> bank
        WalletType.E_WALLET -> eWallet
        WalletType.SAVINGS -> savings
        WalletType.CREDIT_CARD -> credit
        WalletType.LOAN -> loan
    }
}

private fun categoryPalette(night: Boolean): Map<String, TonePair> {
    // Tonal tiles: a pale tint with a saturated glyph by day, a deep tint with a light glyph at night. Glyphs keep
    // at least 3:1 against their tile in both themes.
    fun t(dayTile: Long, dayGlyph: Long, nightTile: Long, nightGlyph: Long) =
        if (night) TonePair(Color(nightTile), Color(nightGlyph)) else TonePair(Color(dayTile), Color(dayGlyph))
    val coral = t(0xFFFFE9E4, 0xFFD9452F, 0xFF4A221D, 0xFFFFAA9C)
    val green = t(0xFFE3F5EC, 0xFF16865C, 0xFF123B2A, 0xFF7EE2B0)
    val rose = t(0xFFFDE6EE, 0xFFC93A68, 0xFF4A1D2C, 0xFFFFA3C2)
    val amber = t(0xFFFFF1D6, 0xFFB86A08, 0xFF3D2C10, 0xFFFFC46B)
    val blue = t(0xFFEAF1FF, 0xFF3563F5, 0xFF22325E, 0xFFA9BDFF)
    val teal = t(0xFFDDF4F1, 0xFF0D8478, 0xFF0F3A36, 0xFF7EDCD1)
    val indigo = t(0xFFE9E9FE, 0xFF5457D6, 0xFF26285E, 0xFFB4B6FF)
    val violet = t(0xFFF0EAFE, 0xFF7848E0, 0xFF30215A, 0xFFC8B3FF)
    val sky = t(0xFFE0F2FE, 0xFF0B7FC0, 0xFF0E3048, 0xFF8FD3FA)
    val income = green
    // One hue per family of spending, matching the preset groups (see domain/CategoryPresets.kt).
    val families = mapOf(
        coral to listOf("groceries", "dining", "cafe", "takeout", "work_meals", "convenience"),
        green to listOf("transport", "fuel", "taxi", "parking", "car", "repairs", "registration", "bicycle"),
        sky to listOf("travel", "hotel", "outdoor", "business_travel"),
        rose to listOf("shopping", "personal", "gift", "grooming", "childcare", "baby", "pets", "family"),
        amber to listOf("utilities", "water", "heating", "internet", "phone"),
        blue to listOf("housing", "mortgage", "insurance", "maintenance", "home_supplies", "furniture", "security"),
        teal to listOf("health", "dental", "vision", "pharmacy", "wellbeing", "fitness", "sports"),
        indigo to listOf("education", "tuition", "subscriptions", "streaming", "books", "home_office", "membership", "certification", "work_expenses"),
        violet to listOf("entertainment", "hobbies", "games", "social"),
        t(0xFFECEFF4, 0xFF56637D, 0xFF26304A, 0xFFB8C5DC) to listOf("tax", "fees", "bank_fees", "loan", "credit_card", "debt", "legal", "supplies"),
        t(0xFFDDF1EE, 0xFF0F766E, 0xFF0E3532, 0xFF7FD6CB) to listOf("savings", "emergency", "retirement", "investing"),
        t(0xFFFCE4F0, 0xFFBE2468, 0xFF4A1A33, 0xFFFF9FCC) to listOf("donation", "community"),
    )
    return families.flatMap { (tone, keys) -> keys.map { it to tone } }.toMap() + mapOf(
        "work" to income, "business" to income, "investments" to income, "refund" to income, "income" to income,
    )
}

internal val LightExtended = LucentoExtendedColors(
    positive = Palette.PositiveText,
    positiveContainer = Color(0xFFE3F5EC),
    negative = Palette.NegativeText,
    negativeContainer = Color(0xFFFDE7E7),
    warning = Palette.AmberText,
    warningContainer = Color(0xFFFFEFD9),
    warningIndicator = Palette.Warning,
    negativeIndicator = Palette.Negative,
    info = Palette.ElectricBlue,
    infoContainer = Palette.PaleSky,
    accent = Palette.SunlightYellow,
    accentText = Palette.AmberText,
    warmAccent = Palette.BrightTangerine,
    borderDefault = Color(0xFFD8DDE7),
    shadow = Palette.MidnightNavy,
    highlight = Palette.SunlightPale,
    onHighlight = Palette.MidnightNavy,
    hero = Palette.BlueHero,
    onHero = Color.White,
    onHeroMuted = Color(0xFFDCE5FF),
    heroRule = Color(0xFF4A6EF0),
    chartBar = Palette.ElectricBlue,
    chartBarMuted = Color(0xFFC7D5FC),
    chartHighlight = Palette.ElectricBlue,
    track = Color(0xFFE8EDF7),
    // Sunrise chart order: blue, tangerine, sunlight, emerald, blue-gray, then sky and violet for long tails.
    categorical = listOf(
        Palette.ElectricBlue,
        Palette.BrightTangerine,
        Palette.SunlightYellow,
        Palette.Positive,
        Color(0xFF8A97B0),
        Color(0xFF38A3E8),
        Color(0xFF8B7CF6),
    ),
    success = Palette.Positive,
    // Approved brand artwork: the mark keeps its original colours (not re-tinted to the UI palette).
    cash = TonePair(Palette.SunlightPale, Color(0xFF8A5200)),
    bank = TonePair(Palette.PaleSky, Palette.BlueDeep),
    eWallet = TonePair(Color(0xFFE0F2FE), Color(0xFF0369A1)),
    savings = TonePair(Color(0xFFE3F5EC), Palette.PositiveText),
    credit = TonePair(Color(0xFFFDE7E7), Color(0xFFB42318)),
    loan = TonePair(Color(0xFFEDE9FE), Color(0xFF5B21B6)),
    categoryTones = categoryPalette(night = false),
    categoryFallback = TonePair(Color(0xFFECEFF4), Color(0xFF56637D)),
)

internal val DarkExtended = LucentoExtendedColors(
    positive = Color(0xFF3DD68C),
    positiveContainer = Color(0xFF123B2A),
    negative = Color(0xFFFF8A8A),
    negativeContainer = Color(0xFF4A1A1F),
    warning = Color(0xFFFFB547),
    warningContainer = Color(0xFF3D2C10),
    warningIndicator = Color(0xFFFFB547),
    negativeIndicator = Color(0xFFFF8A8A),
    info = Palette.BlueNight,
    infoContainer = Color(0xFF1C2B55),
    accent = Palette.SunlightYellow,
    accentText = Palette.SunlightYellow,
    warmAccent = Palette.BrightTangerine,
    borderDefault = Color(0xFF2D3D63),
    shadow = Color.Black,
    highlight = Color(0xFF3D3214),
    onHighlight = Color(0xFFFFE9A8),
    // At night the statement panel is an elevated navy card; blue stays for interaction and the trend line.
    hero = Palette.NightElevated,
    onHero = Palette.NightInk,
    onHeroMuted = Palette.NightMuted,
    heroRule = Color(0xFF2D3D63),
    chartBar = Palette.BlueNight,
    chartBarMuted = Color(0xFF2C3D66),
    chartHighlight = Palette.BlueNight,
    track = Color(0xFF26365C),
    categorical = listOf(
        Palette.BlueNight,
        Color(0xFFFF9F4D),
        Palette.SunlightYellow,
        Color(0xFF3DD68C),
        Color(0xFF8A97B0),
        Color(0xFF6CC4F5),
        Color(0xFFA99BFF),
    ),
    success = Color(0xFF3DD68C),
    cash = TonePair(Color(0xFF3D3214), Color(0xFFFFD98A)),
    bank = TonePair(Color(0xFF22325E), Color(0xFFDCE6FF)),
    eWallet = TonePair(Color(0xFF0E3048), Color(0xFFB6E3FF)),
    savings = TonePair(Color(0xFF123B2A), Color(0xFFB8F0CC)),
    credit = TonePair(Color(0xFF4A1A1F), Color(0xFFFFC2C2)),
    loan = TonePair(Color(0xFF2E2157), Color(0xFFDDD3FF)),
    categoryTones = categoryPalette(night = true),
    categoryFallback = TonePair(Color(0xFF26304A), Color(0xFFB8C5DC)),
)
