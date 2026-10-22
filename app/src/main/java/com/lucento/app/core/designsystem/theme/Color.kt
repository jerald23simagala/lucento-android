package com.lucento.app.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.lucento.app.domain.WalletType

/*
 * Lucento palette — "daylight blue on warm cream", with a midnight-navy night theme.
 *
 * Brand blue carries primary actions and selection; deep navy carries text; warm cream is the canvas.
 * Sun yellow is the single warm accent (the leaf in the mark, the mascot, small highlights) and is never
 * used for small text on light surfaces. Green and coral are semantic only (money in / money out,
 * success / error) and always paired with a sign or words. No gradients.
 *
 * Contrast (WCAG 2.1, checked with a script; ratios against the surface they sit on):
 *   light  ink 16.6 · muted 5.8 · primary 5.1 · white-on-primary 5.2 · positive 4.9 · negative 5.4
 *   dark   ink 15.9 · muted 8.5 · primary 8.6 · positive 10.7 · negative 8.1 · accent text 12.9
 * Outlines meet 3:1 for component boundaries.
 */
internal object Palette {
    // Brand blues
    val Blue900 = Color(0xFF0B2A6F)
    val Blue800 = Color(0xFF123A9C)
    val Blue700 = Color(0xFF1D4ED8)
    val Blue600 = Color(0xFF2563EB) // primary
    val Blue400 = Color(0xFF60A5FA) // secondary
    val Blue300 = Color(0xFF8AB0FF)
    val Blue100 = Color(0xFFDCE6FF)
    val Blue50 = Color(0xFFF3F6FF) // tinted surface

    // Sun
    val Sun500 = Color(0xFFFBBF24) // accent
    val Sun300 = Color(0xFFFCD34D)
    val Peach = Color(0xFFFFE6C7) // highlight
    val Amber800 = Color(0xFF8A5A00)

    // Semantic
    val Green500 = Color(0xFF22C55E) // success
    val Green700 = Color(0xFF15803D)
    val Coral400 = Color(0xFFF87171) // error indicator
    val Red700 = Color(0xFFC53030)

    // Neutrals
    val Cream = Color(0xFFFFFDF8) // background
    val Navy950 = Color(0xFF0F1B3D) // ink
    val Midnight = Color(0xFF0B1226) // night background
}

internal val LightColors = lightColorScheme(
    primary = Palette.Blue600,
    onPrimary = Color.White,
    primaryContainer = Palette.Blue100,
    onPrimaryContainer = Palette.Blue800,
    inversePrimary = Palette.Blue300,
    secondary = Palette.Amber800,
    onSecondary = Color.White,
    secondaryContainer = Palette.Peach,
    onSecondaryContainer = Color(0xFF3D2600),
    tertiary = Palette.Green700,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF0B3D20),
    background = Palette.Cream,
    onBackground = Palette.Navy950,
    surface = Palette.Cream,
    onSurface = Palette.Navy950,
    surfaceVariant = Color(0xFFE6EBF5),
    onSurfaceVariant = Color(0xFF5B6478),
    surfaceTint = Palette.Blue600,
    inverseSurface = Color(0xFF1C2742),
    inverseOnSurface = Color(0xFFF1F4FB),
    error = Palette.Red700,
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E2),
    onErrorContainer = Color(0xFF5C1010),
    outline = Color(0xFF8A93A8),
    outlineVariant = Color(0xFFE3E8F2),
    scrim = Color.Black,
    surfaceBright = Palette.Cream,
    surfaceDim = Color(0xFFE6E9F0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF6),
    surfaceContainer = Palette.Blue50,
    surfaceContainerHigh = Color(0xFFEAEFFB),
    surfaceContainerHighest = Color(0xFFE1E8F8),
)

internal val DarkColors = darkColorScheme(
    primary = Palette.Blue300,
    onPrimary = Color(0xFF0B1F4D),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Palette.Blue100,
    inversePrimary = Palette.Blue600,
    secondary = Palette.Sun300,
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF4A3A12),
    onSecondaryContainer = Color(0xFFFFE9B0),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF05301A),
    tertiaryContainer = Color(0xFF123B26),
    onTertiaryContainer = Color(0xFFC8F5D9),
    background = Palette.Midnight,
    onBackground = Color(0xFFE8EDF8),
    surface = Palette.Midnight,
    onSurface = Color(0xFFE8EDF8),
    surfaceVariant = Color(0xFF243052),
    onSurfaceVariant = Color(0xFFA4AFC7),
    surfaceTint = Palette.Blue300,
    inverseSurface = Color(0xFFE8EDF8),
    inverseOnSurface = Color(0xFF141C33),
    error = Color(0xFFFC8A8A),
    onError = Color(0xFF4A0E0E),
    errorContainer = Color(0xFF5E1D1D),
    onErrorContainer = Color(0xFFFDE2E2),
    outline = Color(0xFF6B7694),
    outlineVariant = Color(0xFF222D4A),
    scrim = Color.Black,
    surfaceBright = Color(0xFF243052),
    surfaceDim = Palette.Midnight,
    surfaceContainerLowest = Color(0xFF070D1C),
    surfaceContainerLow = Color(0xFF10182F),
    surfaceContainer = Color(0xFF16203B),
    surfaceContainerHigh = Color(0xFF1B2644),
    surfaceContainerHighest = Color(0xFF222E50),
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
    /** Sun yellow for decorative emphasis (the leaf, small highlights). Not for small text on light surfaces. */
    val accent: Color,
    /** Yellow/amber that meets text contrast on the app background. */
    val accentText: Color,
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
    val brandMark: Color,
    val brandLeaf: Color,
    val cash: TonePair,
    val bank: TonePair,
    val eWallet: TonePair,
    val savings: TonePair,
    val credit: TonePair,
    val loan: TonePair,
    /** Mascot artwork (Sun in light theme, Moon in dark theme). */
    val mascotBody: Color,
    val mascotShade: Color,
    val mascotFace: Color,
    val mascotCheek: Color,
    /**
     * Category tiles, as in the reference design: a saturated tile with a white glyph, one hue per family of
     * spending (food coral, transport green, shopping rose, bills amber…). Purely decorative — the row's text and
     * signed amount carry the meaning — so the glyph only needs non-text contrast on its own tile.
     */
    val categoryTones: Map<String, TonePair> = emptyMap(),
    val categoryFallback: TonePair = TonePair(Color(0xFF94A3B8), Color.White),
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
    fun t(day: Long, dark: Long) = TonePair(Color(if (night) dark else day), Color.White)
    val coral = t(0xFFF2685A, 0xFFD9574A)
    val green = t(0xFF16A34A, 0xFF15803D)
    val rose = t(0xFFE5487A, 0xFFC93A68)
    val amber = t(0xFFF59E0B, 0xFFD38708)
    val blue = t(0xFF3B82F6, 0xFF2F6FDB)
    val teal = t(0xFF0D9488, 0xFF0F8A7E)
    val indigo = t(0xFF6366F1, 0xFF5457D6)
    val violet = t(0xFF8B5CF6, 0xFF7848E0)
    val sky = t(0xFF0EA5E9, 0xFF0B8CC7)
    val income = t(0xFF16A34A, 0xFF15803D)
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
        t(0xFF64748B, 0xFF56637D) to listOf("tax", "fees", "bank_fees", "loan", "credit_card", "debt", "legal", "supplies"),
        t(0xFF0F766E, 0xFF0E6B63) to listOf("savings", "emergency", "retirement", "investing"),
        t(0xFFDB2777, 0xFFBE2468) to listOf("donation", "community"),
    )
    return families.flatMap { (tone, keys) -> keys.map { it to tone } }.toMap() + mapOf(
        "work" to income, "business" to income, "investments" to income, "refund" to income, "income" to income,
    )
}

internal val LightExtended = LucentoExtendedColors(
    positive = Palette.Green700,
    positiveContainer = Color(0xFFDCFCE7),
    negative = Palette.Red700,
    negativeContainer = Color(0xFFFDE2E2),
    warning = Palette.Amber800,
    warningContainer = Color(0xFFFFF1D6),
    accent = Palette.Sun500,
    accentText = Palette.Amber800,
    hero = Palette.Blue700,
    onHero = Color.White,
    onHeroMuted = Palette.Blue100,
    heroRule = Color(0xFF3B6BE6),
    chartBar = Palette.Blue600,
    chartBarMuted = Color(0xFFBFD3FB),
    chartHighlight = Palette.Blue600,
    track = Color(0xFFE6EBF5),
    categorical = listOf(
        Palette.Blue600,
        Color(0xFFF59E0B),
        Palette.Green500,
        Palette.Coral400,
        Color(0xFF38BDF8),
        Color(0xFF8B5CF6),
        Color(0xFF94A3B8),
    ),
    success = Palette.Green500,
    brandMark = Palette.Blue600,
    brandLeaf = Palette.Sun500,
    cash = TonePair(Palette.Peach, Color(0xFF8A4B00)),
    bank = TonePair(Palette.Blue100, Palette.Blue700),
    eWallet = TonePair(Color(0xFFE0F2FE), Color(0xFF0369A1)),
    savings = TonePair(Color(0xFFDCFCE7), Palette.Green700),
    credit = TonePair(Color(0xFFFDE2E2), Color(0xFFB42318)),
    loan = TonePair(Color(0xFFEDE9FE), Color(0xFF5B21B6)),
    mascotBody = Palette.Sun500,
    mascotShade = Color(0xFFF59E0B),
    mascotFace = Palette.Navy950,
    mascotCheek = Color(0xFFFB923C),
    categoryTones = categoryPalette(night = false),
    categoryFallback = TonePair(Color(0xFF94A3B8), Color.White),
)

internal val DarkExtended = LucentoExtendedColors(
    positive = Color(0xFF4ADE80),
    positiveContainer = Color(0xFF123B26),
    negative = Color(0xFFFC8A8A),
    negativeContainer = Color(0xFF4A1A1A),
    warning = Palette.Sun300,
    warningContainer = Color(0xFF3D3011),
    accent = Palette.Sun500,
    accentText = Palette.Sun300,
    hero = Color(0xFF1E3A8A),
    onHero = Color.White,
    onHeroMuted = Color(0xFFC3D3FF),
    heroRule = Color(0xFF2E4DA3),
    chartBar = Palette.Blue300,
    chartBarMuted = Color(0xFF2A3A66),
    chartHighlight = Palette.Blue300,
    track = Color(0xFF222D4A),
    categorical = listOf(
        Palette.Blue300,
        Palette.Sun300,
        Color(0xFF4ADE80),
        Color(0xFFFC8A8A),
        Color(0xFF7DD3FC),
        Color(0xFFC4B5FD),
        Color(0xFF94A3B8),
    ),
    success = Color(0xFF4ADE80),
    brandMark = Palette.Blue300,
    brandLeaf = Palette.Sun500,
    cash = TonePair(Color(0xFF3D2E12), Color(0xFFFFD49A)),
    bank = TonePair(Color(0xFF1E3366), Palette.Blue100),
    eWallet = TonePair(Color(0xFF0E3048), Color(0xFFB6E3FF)),
    savings = TonePair(Color(0xFF123B26), Color(0xFFB8F0CC)),
    credit = TonePair(Color(0xFF4A1A1A), Color(0xFFFFC2C2)),
    loan = TonePair(Color(0xFF2E2157), Color(0xFFDDD3FF)),
    mascotBody = Color(0xFFE4EBFF),
    mascotShade = Color(0xFFB9C7EE),
    mascotFace = Color(0xFF1B2850),
    mascotCheek = Color(0xFFF9A8D4),
    categoryTones = categoryPalette(night = true),
    categoryFallback = TonePair(Color(0xFF5B6680), Color.White),
)
