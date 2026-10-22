package com.lucento.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lucento.app.R

/** Inter for UI text; Inter Display (optically tuned for large sizes) for headings. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val InterDisplayFamily = FontFamily(
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
)

/** Tabular, lining figures so amounts align in columns and do not jitter while animating. */
const val TabularFigures = "tnum, lnum"

private fun display(size: Int, line: Int, weight: FontWeight = FontWeight.SemiBold, tracking: Double = -0.02) =
    TextStyle(
        fontFamily = InterDisplayFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = InterFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

val LucentoTypography = Typography(
    displayLarge = display(52, 58),
    displayMedium = display(42, 48),
    displaySmall = display(34, 40),
    headlineLarge = display(30, 36),
    headlineMedium = display(26, 32),
    headlineSmall = display(22, 28, tracking = -0.01),
    titleLarge = display(20, 26, weight = FontWeight.Medium, tracking = -0.01),
    titleMedium = text(16, 22, FontWeight.SemiBold),
    titleSmall = text(14, 20, FontWeight.SemiBold),
    bodyLarge = text(16, 24),
    bodyMedium = text(14, 20),
    bodySmall = text(12, 16),
    labelLarge = text(14, 20, FontWeight.Medium, tracking = 0.005),
    labelMedium = text(12, 16, FontWeight.Medium, tracking = 0.01),
    labelSmall = text(11, 14, FontWeight.Medium, tracking = 0.04),
)

/** Amount styles. Always tabular. */
object AmountStyles {
    val hero = display(40, 46).copy(fontFeatureSettings = TabularFigures)
    val large = display(24, 30).copy(fontFeatureSettings = TabularFigures)
    val row = text(16, 22, FontWeight.Medium).copy(fontFeatureSettings = TabularFigures)
}
