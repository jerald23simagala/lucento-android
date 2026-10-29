package com.lucento.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Guards the Sunrise Energy palette: every text/background pair the components use must keep WCAG 2.1 AA contrast
 * (4.5:1 for text, 3:1 for control boundaries and meaningful marks) in both themes.
 */
class ThemeContrastTest {

    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertAtLeast(min: Double, fg: Color, bg: Color, what: String) {
        val ratio = contrast(fg, bg)
        assertTrue("$what is %.2f:1, needs %.1f:1".format(ratio, min), ratio >= min)
    }

    @Test
    fun `light theme text pairs meet AA`() {
        val c = LightColors
        val x = LightExtended
        listOf(c.background, c.surfaceContainerLowest, c.surfaceContainer).forEach { bg ->
            assertAtLeast(4.5, c.onSurface, bg, "ink")
            assertAtLeast(4.5, c.onSurfaceVariant, bg, "muted ink")
            assertAtLeast(4.5, c.error, bg, "error text")
            assertAtLeast(4.5, x.positive, bg, "positive")
            assertAtLeast(4.5, x.negative, bg, "negative")
            assertAtLeast(4.5, x.warning, bg, "warning")
            assertAtLeast(4.5, x.accentText, bg, "accent text")
        }
        assertAtLeast(4.5, c.primary, c.background, "primary text")
        assertAtLeast(4.5, c.onPrimary, c.primary, "label on primary button")
        assertAtLeast(4.5, c.onPrimaryContainer, c.primaryContainer, "on primary container")
        assertAtLeast(4.5, c.onSecondaryContainer, c.secondaryContainer, "on highlight container")
        assertAtLeast(4.5, x.onHighlight, x.highlight, "on brand highlight")
        assertAtLeast(4.5, x.onHighlight, x.accent, "navy on sunlight yellow")
        assertAtLeast(4.5, x.onHero, x.hero, "hero text")
        assertAtLeast(4.5, x.onHeroMuted, x.hero, "hero muted text")
        assertAtLeast(4.5, x.positive, x.positiveContainer, "positive pill")
        assertAtLeast(4.5, x.negative, x.negativeContainer, "negative pill")
        assertAtLeast(4.5, x.warning, x.warningContainer, "warning pill")
        assertAtLeast(3.0, c.outline, c.background, "control outline")
        assertAtLeast(3.0, x.success, c.background, "progress fill")
        assertAtLeast(4.5, c.onSurfaceVariant, c.surfaceContainerHigh, "muted ink on segmented control")
        assertAtLeast(3.0, c.primary, c.primaryContainer, "selected dock icon")
    }

    @Test
    fun `dark theme text pairs meet AA`() {
        val c = DarkColors
        val x = DarkExtended
        listOf(c.background, c.surfaceContainer, c.surfaceContainerHigh).forEach { bg ->
            assertAtLeast(4.5, c.onSurface, bg, "ink")
            assertAtLeast(4.5, c.onSurfaceVariant, bg, "muted ink")
            assertAtLeast(4.5, c.primary, bg, "primary text")
            assertAtLeast(4.5, c.error, bg, "error text")
            assertAtLeast(4.5, x.positive, bg, "positive")
            assertAtLeast(4.5, x.negative, bg, "negative")
            assertAtLeast(4.5, x.warning, bg, "warning")
            assertAtLeast(4.5, x.accentText, bg, "accent text")
        }
        assertAtLeast(4.5, c.onPrimary, c.primary, "label on primary button")
        assertAtLeast(4.5, c.onPrimaryContainer, c.primaryContainer, "on primary container")
        assertAtLeast(4.5, c.onSecondaryContainer, c.secondaryContainer, "on highlight container")
        assertAtLeast(4.5, x.onHighlight, x.highlight, "on brand highlight")
        assertAtLeast(4.5, x.onHero, x.hero, "hero text")
        assertAtLeast(4.5, x.onHeroMuted, x.hero, "hero muted text")
        assertAtLeast(3.0, c.outline, c.background, "control outline")
    }

    @Test
    fun `approved style tokens`() {
        assertEquals(Color(0xFF17233F), LightExtended.hero)
        assertEquals(Color(0xFFF8FAFF), LightExtended.onHero)
        assertEquals(Color(0xFFB8C5DC), LightExtended.onHeroMuted)
        assertEquals(Color(0xFFFFC928), LightExtended.heroRule)
        assertEquals(Color(0xFFE8EAF0), LightExtended.borderDefault)
        assertEquals(Color(0xFF17233F), LightExtended.shadow)
        assertEquals(Color(0xFF0B1222), DarkColors.background)
        assertEquals(Color(0xFF17233F), DarkColors.surfaceContainer)
        assertEquals(Color(0xFF202E4D), DarkColors.surfaceContainerHigh)
        assertEquals(Color(0xFF17233F), DarkExtended.shadow)
        assertEquals(Color(0xFF7C9AFF), DarkColors.primary)
        assertEquals(1.5f, cappedFontScale(2f, FontScaleCaps.Hero), 0f)
        assertEquals(1.3f, cappedFontScale(2f, FontScaleCaps.Navigation), 0f)
        assertEquals(1.1f, cappedFontScale(1.1f, FontScaleCaps.Hero), 0f)
        assertEquals(8.dp, Radii.xs)
        assertEquals(12.dp, Radii.sm)
        assertEquals(16.dp, Radii.md)
        assertEquals(24.dp, Radii.lg)
        assertEquals(28.dp, Radii.xl)
    }

    @Test
    fun `bar labels stay AA on the fill they sit on`() {
        val behind = LightExtended.track
        listOf(LightExtended.success, LightExtended.negativeIndicator, LightColors.primary, LightColors.primary.copy(alpha = 0.55f))
            .forEach { fill ->
                val ink = readableOn(fill, behind)
                assertAtLeast(4.5, ink, fill.compositeOver(behind), "bar label")
            }
        val nightBehind = DarkExtended.track
        listOf(DarkExtended.success, DarkExtended.negativeIndicator, DarkColors.primary, DarkColors.primary.copy(alpha = 0.55f))
            .forEach { fill ->
                val ink = readableOn(fill, nightBehind)
                assertAtLeast(4.5, ink, fill.compositeOver(nightBehind), "night bar label")
            }
    }

    @Test
    fun `category and wallet glyphs stay legible on their tiles`() {
        listOf(LightExtended, DarkExtended).forEach { x ->
            (x.categoryTones.values + x.categoryFallback + listOf(x.cash, x.bank, x.eWallet, x.savings, x.credit, x.loan))
                .forEach { tone -> assertAtLeast(3.0, tone.content, tone.container, "glyph on tile") }
        }
    }
}
