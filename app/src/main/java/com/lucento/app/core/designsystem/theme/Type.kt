package com.lucento.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

/** Editorial overline used for section labels ("THIS MONTH", "RECENT"). Uppercased by the caller. */
val OverlineStyle = style(11, 16, FontWeight.SemiBold, tracking = 0.08)

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
