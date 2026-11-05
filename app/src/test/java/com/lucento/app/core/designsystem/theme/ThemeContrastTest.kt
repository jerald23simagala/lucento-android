package com.lucento.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Guards every theme's palette: each text/background pair the components use must keep WCAG 2.1 AA contrast
 * (4.5:1 for text, 3:1 for control boundaries and meaningful marks), in every theme and in each of its modes.
 * Failures are collected so one run names every pair that regressed.
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

    private val failures = mutableListOf<String>()

    private fun check(theme: String, min: Double, fg: Color, bg: Color, what: String) {
        val ratio = contrast(fg, bg)
        if (ratio < min) failures += "$theme: $what is %.2f:1, needs %.1f:1".format(ratio, min)
    }

    private fun assertNoFailures() = assertTrue(failures.joinToString("\n"), failures.isEmpty())

    private val lightPalettes = ThemeSpec.all.mapNotNull { spec -> spec.light?.let { "${spec.displayName} light" to it } }
    private val darkPalettes = ThemeSpec.all.map { spec -> "${spec.displayName} dark" to spec.dark }

    @Test
    fun `every theme has both palettes it promises`() {
        assertTrue(lightPalettes.size == ThemeSpec.all.count { !it.darkOnly })
        assertTrue(darkPalettes.size == ThemeSpec.all.size)
    }

    @Test
    fun `light palettes meet AA`() {
        for ((name, palette) in lightPalettes) {
            val c = palette.scheme
            val x = palette.extended
            listOf(c.background, c.surfaceContainerLowest, c.surfaceContainer).forEach { bg ->
                check(name, 4.5, c.onSurface, bg, "ink")
                check(name, 4.5, c.onSurfaceVariant, bg, "muted ink")
                check(name, 4.5, c.error, bg, "error text")
                check(name, 4.5, x.positive, bg, "positive")
                check(name, 4.5, x.negative, bg, "negative")
                check(name, 4.5, x.warning, bg, "warning")
                check(name, 4.5, x.accentText, bg, "accent text")
            }
            check(name, 4.5, c.primary, c.background, "primary text")
            check(name, 4.5, c.onPrimary, c.primary, "label on primary button")
            check(name, 4.5, c.onPrimaryContainer, c.primaryContainer, "on primary container")
            check(name, 4.5, c.onSecondaryContainer, c.secondaryContainer, "on highlight container")
            check(name, 4.5, x.onHighlight, x.highlight, "on brand highlight")
            check(name, 4.5, x.onHighlight, x.accent, "ink on accent")
            check(name, 4.5, x.onAccent, x.accent, "label on accent pill")
            check(name, 4.5, x.onAccent, x.warmAccent, "label on warm accent pill")
            listOf(x.hero, x.heroEnd).forEach { panel ->
                check(name, 4.5, x.onHero, panel, "hero text")
                check(name, 4.5, x.onHeroMuted, panel, "hero muted text")
            }
            check(name, 3.0, x.accent, x.hero, "statement rule on hero")
            check(name, 4.5, x.positive, x.positiveContainer, "positive pill")
            check(name, 4.5, x.negative, x.negativeContainer, "negative pill")
            check(name, 4.5, x.warning, x.warningContainer, "warning pill")
            check(name, 3.0, c.outline, c.background, "control outline")
            check(name, 3.0, x.success, c.background, "progress fill")
            check(name, 4.5, c.onSurfaceVariant, c.surfaceContainerHigh, "muted ink on segmented control")
            check(name, 4.5, c.onSurfaceVariant, c.surfaceContainerLow, "muted ink on a panel")
            check(name, 3.0, c.primary, c.primaryContainer, "selected dock icon")
        }
        assertNoFailures()
    }

    @Test
    fun `dark palettes meet AA`() {
        for ((name, palette) in darkPalettes) {
            val c = palette.scheme
            val x = palette.extended
            listOf(c.background, c.surfaceContainer, c.surfaceContainerHigh).forEach { bg ->
                check(name, 4.5, c.onSurface, bg, "ink")
                check(name, 4.5, c.onSurfaceVariant, bg, "muted ink")
                check(name, 4.5, c.primary, bg, "primary text")
                check(name, 4.5, c.error, bg, "error text")
                check(name, 4.5, x.positive, bg, "positive")
                check(name, 4.5, x.negative, bg, "negative")
                check(name, 4.5, x.warning, bg, "warning")
                check(name, 4.5, x.accentText, bg, "accent text")
            }
            check(name, 4.5, c.onPrimary, c.primary, "label on primary button")
            check(name, 4.5, c.onPrimaryContainer, c.primaryContainer, "on primary container")
            check(name, 4.5, c.onSecondaryContainer, c.secondaryContainer, "on highlight container")
            check(name, 4.5, x.onHighlight, x.highlight, "on brand highlight")
            // The add actions (top-bar pill, dock +) use the accent at night.
            check(name, 4.5, x.onAccent, x.accent, "label on accent add button")
            check(name, 4.5, x.onAccent, x.warmAccent, "label on warm accent pill")
            listOf(x.hero, x.heroEnd).forEach { panel ->
                check(name, 4.5, x.onHero, panel, "hero text")
                check(name, 4.5, x.onHeroMuted, panel, "hero muted text")
            }
            check(name, 3.0, x.accent, x.hero, "statement rule on hero")
            check(name, 3.0, c.outline, c.background, "control outline")
            check(name, 4.5, c.onSurfaceVariant, c.surfaceContainerLow, "muted ink on a panel")
        }
        assertNoFailures()
    }

    private fun blend(a: Color, b: Color, t: Float) =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)

    @Test
    fun `home balance card faces meet AA across their gradient`() {
        for (spec in ThemeSpec.all) {
            val f = spec.balanceCard
            val name = "${spec.displayName} balance card"
            val mid = blend(f.start, f.end, 0.5f)
            // Labels and figures can sit anywhere on the diagonal, from the bright top-left corner to the deep end.
            listOf(f.start, mid, f.end).forEach { bg ->
                check(name, 4.5, f.ink, bg, "ink")
                check(name, 4.5, f.inkMuted, bg, "muted ink")
            }
            // The trend (signed change, bars) sits at the top right, past the gradient's midpoint.
            listOf(mid, f.end).forEach { bg ->
                check(name, 4.5, BalanceCardFace.Gain, bg, "gain text")
                check(name, 4.5, BalanceCardFace.Loss, bg, "loss text")
                f.bars.forEach { bar -> check(name, 3.0, bar, bg, "trend bar") }
            }
            // The landscape is printed as darkening only, which is safe only for light ink on a deep face.
            if (luminance(f.ink) <= luminance(f.start)) failures += "$name: ink must be lighter than the face"
            if (f.artThreshold !in 0f..255f) failures += "$name: art threshold must be a luma value"
        }
        assertNoFailures()
    }

    @Test
    fun `category and wallet glyphs stay legible on their tiles`() {
        for ((name, palette) in lightPalettes + darkPalettes) {
            val x = palette.extended
            (x.categoryTones.values + x.categoryFallback + listOf(x.cash, x.bank, x.eWallet, x.savings, x.credit, x.loan))
                .forEach { tone -> check(name, 3.0, tone.content, tone.container, "glyph on tile") }
        }
        assertNoFailures()
    }
}
