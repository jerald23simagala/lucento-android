package com.lucento.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lucento.app.R

/**
 * Plus Jakarta Sans (OFL, bundled as static 400/500/600/700 instances — see docs/licenses) for the whole UI.
 * The Lucento logo is artwork, never typeset with this family.
 */
val LucentoFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/** Tabular figures so amounts align in columns and do not jitter while animating (Plus Jakarta's figures are lining). */
const val TabularFigures = "tnum"

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = LucentoFontFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

/*
 * Sunrise Energy scale (sp, so it follows the system font size):
 *   display 36 Bold · screen title 28 Bold · section heading 20 SemiBold · card heading 16 SemiBold
 *   body 14–16 Regular · form labels 14 Medium · supporting 12 Regular · buttons 16 SemiBold · metadata 12 Medium
 * Large sizes beyond the display style are reserved for the single hero figure on a screen.
 */
val LucentoTypography = Typography(
    displayLarge = style(40, 48, FontWeight.Bold, tracking = -0.02),
    displayMedium = style(36, 44, FontWeight.Bold, tracking = -0.02),
    displaySmall = style(32, 40, FontWeight.Bold, tracking = -0.02),
    headlineLarge = style(30, 38, FontWeight.Bold, tracking = -0.015),
    headlineMedium = style(28, 36, FontWeight.Bold, tracking = -0.015),
    headlineSmall = style(22, 30, FontWeight.SemiBold, tracking = -0.01),
    titleLarge = style(20, 28, FontWeight.SemiBold, tracking = -0.005),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20),
    bodySmall = style(12, 18),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium, tracking = 0.005),
    labelSmall = style(11, 16, FontWeight.Medium, tracking = 0.02),
)

/**
 * The hierarchy every screen shares, so a section's purpose, its main figure, the supporting detail and its action
 * read in the same order everywhere:
 *
 *   section title   [Hierarchy.sectionTitle] 18 SemiBold, on-surface — a block of a screen (SectionHeader)
 *   card title      [Hierarchy.cardTitle] 15 SemiBold, on-surface — what a card is about (CardHeader)
 *   context         [Hierarchy.context] 12 Medium, on-surface-variant — period, currency, scope ("October · USD")
 *   primary value   [AmountStyles.hero] / [AmountStyles.large] — the one figure the block exists for
 *   secondary value [AmountStyles.medium] / [AmountStyles.row] — figures that explain the primary one
 *   description     bodySmall, on-surface-variant — one or two lines of meaning, never a third heading
 *   action          LucentoTextButton (labelLarge, primary) — trailing the title it acts on
 *   field label     [Hierarchy.fieldLabel] 14 SemiBold, on-surface — inside forms and sheets (FieldHeader)
 *
 * Titles are sentence case; uppercase overlines are kept only for tiny chart/legend labels.
 */
object Hierarchy {
    val sectionTitle = style(18, 24, FontWeight.SemiBold, tracking = -0.005)
    val cardTitle = style(15, 20, FontWeight.SemiBold)
    val context = style(12, 16, FontWeight.Medium, tracking = 0.005)
    val fieldLabel = style(14, 20, FontWeight.SemiBold)
}

/** Editorial overline for tiny labels (chart legends, figure captions). Uppercased by the caller. */
val OverlineStyle = style(11, 16, FontWeight.SemiBold, tracking = 0.08)

/**
 * Caps so large type never clips: hero figures stop at 1.5×, dock labels at 1.3×.
 * Below the cap the system scale is left alone.
 */
object FontScaleCaps {
    const val Hero = 1.5f
    const val Navigation = 1.3f
}

fun cappedFontScale(systemScale: Float, cap: Float): Float = systemScale.coerceAtMost(cap)

/** Draws [content] with the system font scale, never above [maxScale]. Density (dp) is unchanged. */
@Composable
fun WithFontScaleCap(maxScale: Float, content: @Composable () -> Unit) {
    val current = LocalDensity.current
    val scale = cappedFontScale(current.fontScale, maxScale)
    if (scale == current.fontScale) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalDensity provides Density(current.density, fontScale = scale),
        content = content,
    )
}

/** Amount styles. Always tabular. Prominent but never crowding the layout. */
object AmountStyles {
    /** The single most important number on a screen (net worth). */
    val display = style(40, 48, FontWeight.Bold, tracking = -0.02).copy(fontFeatureSettings = TabularFigures)
    /** Large amount entry in the transaction editor. */
    val entry = style(36, 44, FontWeight.SemiBold, tracking = -0.02).copy(fontFeatureSettings = TabularFigures)
    val hero = style(32, 40, FontWeight.Bold, tracking = -0.02).copy(fontFeatureSettings = TabularFigures)
    val large = style(22, 28, FontWeight.Bold, tracking = -0.01).copy(fontFeatureSettings = TabularFigures)
    val medium = style(16, 22, FontWeight.SemiBold).copy(fontFeatureSettings = TabularFigures)
    val row = style(15, 20, FontWeight.SemiBold).copy(fontFeatureSettings = TabularFigures)
    val small = style(12, 16, FontWeight.Medium).copy(fontFeatureSettings = TabularFigures)
}
