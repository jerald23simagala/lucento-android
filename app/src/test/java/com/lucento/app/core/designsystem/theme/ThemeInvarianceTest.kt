package com.lucento.app.core.designsystem.theme

import com.lucento.app.data.preferences.ThemeId
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Themes re-voice the brand, never the money: every theme must keep Sunrise's financial semantics for its mode —
 * positive / negative / warning tones, progress success, error roles, category tiles and wallet faces.
 */
class ThemeInvarianceTest {

    private fun semantics(x: LucentoExtendedColors) = listOf(
        x.positive, x.positiveContainer, x.negative, x.negativeContainer, x.warning, x.warningContainer,
        x.warningIndicator, x.negativeIndicator, x.success,
        x.cash, x.bank, x.eWallet, x.savings, x.credit, x.loan, x.investment,
        x.categoryTones, x.categoryFallback,
        WalletType.entries.map { x.walletCard(it) },
    )

    private fun errorRoles(c: androidx.compose.material3.ColorScheme) = listOf(
        c.error, c.onError, c.errorContainer, c.onErrorContainer,
        c.tertiary, c.onTertiary, c.tertiaryContainer, c.onTertiaryContainer,
    )

    @Test
    fun `financial semantics are identical in every theme`() {
        for (spec in ThemeSpec.all) {
            spec.light?.let { light ->
                assertEquals("${spec.displayName} light semantics", semantics(LightExtended), semantics(light.extended))
                assertEquals("${spec.displayName} light error roles", errorRoles(LightColors), errorRoles(light.scheme))
            }
            assertEquals("${spec.displayName} dark semantics", semantics(DarkExtended), semantics(spec.dark.extended))
            assertEquals("${spec.displayName} dark error roles", errorRoles(DarkColors), errorRoles(spec.dark.scheme))
        }
    }

    @Test
    fun `home shortcuts follow the theme with one hue each and never a status colour`() {
        for (spec in ThemeSpec.all) {
            for ((mode, colors) in listOfNotNull(spec.light?.let { "light" to it }, "dark" to spec.dark)) {
                val x = colors.extended
                val tones = x.shortcuts.all
                val status = listOf(
                    TonePair(x.positiveContainer, x.positive),
                    TonePair(x.negativeContainer, x.negative),
                    TonePair(x.warningContainer, x.warning),
                )
                tones.forEach { tone ->
                    assertFalse("${spec.displayName} $mode: a shortcut uses a status tone", tone in status)
                }
                if (spec.id == ThemeId.MONO) {
                    assertEquals("Mono keeps one graphite tone", 1, tones.toSet().size)
                } else {
                    // Design review (2026-11-17): not just different values — far enough from red, amber and green that
                    // no tile reads as a state. Goals is exempt: it carries the theme's primary (Grove's is green).
                    val shortcuts = x.shortcuts
                    listOf("Insights" to shortcuts.insights, "Awards" to shortcuts.awards, "Debts" to shortcuts.debts, "Scheduled" to shortcuts.scheduled)
                        .forEach { (name, tone) ->
                            // The glyph, or the tile when it carries more colour than the glyph (a solid tile is what the
                            // eye reads: design review round 2, a sunlight tile at night sat 5.7 from the warning amber).
                            val dominant = if (okLabChroma(tone.container) > okLabChroma(tone.content)) tone.container else tone.content
                            listOf("positive" to x.positive, "negative" to x.negative, "warning" to x.warning).forEach { (state, color) ->
                                val distance = okLabDistance(dominant, color)
                                assertTrue(
                                    "${spec.displayName} $mode: $name is ${"%.1f".format(distance)} ΔE from $state (needs ≥ 15)",
                                    distance >= 15.0,
                                )
                            }
                        }
                    assertEquals("${spec.displayName} $mode: five distinct shortcut glyphs", 5, tones.map { it.content }.toSet().size)
                    // Goals carries the theme's own primary, so the row always says which theme is on.
                    assertEquals("${spec.displayName} $mode: Goals in the theme's primary", colors.scheme.primary, x.shortcuts.goals.content)
                }
            }
        }
    }

    private fun lin(c: Float): Double = if (c <= 0.04045f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)

    private fun okLab(c: androidx.compose.ui.graphics.Color): DoubleArray {
        val r = lin(c.red)
        val g = lin(c.green)
        val bl = lin(c.blue)
        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * bl)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * bl)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * bl)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    /** OKLab distance ×100 (as `DataSeriesTest` measures it): about 15 is where two colours stop reading as one. */
    private fun okLabDistance(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val p = okLab(a)
        val q = okLab(b)
        return 100 * Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]))
    }

    private fun okLabChroma(c: androidx.compose.ui.graphics.Color): Double = okLab(c).let { Math.hypot(it[1], it[2]) }

    @Test
    fun `sunrise is the shipped palette, untouched`() {
        assertSame(LightColors, ThemeSpec.Sunrise.light!!.scheme)
        assertSame(DarkColors, ThemeSpec.Sunrise.dark.scheme)
        assertSame(LightExtended, ThemeSpec.Sunrise.light!!.extended)
        assertSame(DarkExtended, ThemeSpec.Sunrise.dark.extended)
        assertEquals(LightExtended.hero, LightExtended.heroEnd)
    }

    @Test
    fun `every theme id has exactly one spec, brand theme first`() {
        assertEquals(ThemeId.entries.toSet(), ThemeSpec.all.map { it.id }.toSet())
        assertEquals(ThemeId.entries.size, ThemeSpec.all.size)
        assertEquals(ThemeId.SUNRISE, ThemeSpec.all.first().id)
        ThemeId.entries.forEach { assertEquals(it, ThemeSpec.of(it).id) }
    }

    @Test
    fun `midnight is dark only and ignores the mode`() {
        val midnight = ThemeSpec.of(ThemeId.MIDNIGHT)
        assertNull(midnight.light)
        assertTrue(midnight.darkOnly)
        assertTrue(midnight.isDark(modeIsDark = false))
        assertSame(midnight.dark, midnight.colors(dark = false))
        ThemeSpec.all.filter { !it.darkOnly }.forEach { spec ->
            assertFalse(spec.isDark(modeIsDark = false))
            assertTrue(spec.isDark(modeIsDark = true))
            assertSame(spec.light, spec.colors(dark = false))
        }
    }

    @Test
    fun `stored theme keys round trip and unknown values fall back to sunrise`() {
        ThemeId.entries.forEach { assertEquals(it, ThemeId.fromKey(it.key)) }
        assertEquals(ThemeId.SUNRISE, ThemeId.fromKey(null))
        assertEquals(ThemeId.SUNRISE, ThemeId.fromKey(""))
        assertEquals(ThemeId.SUNRISE, ThemeId.fromKey("solarized"))
        assertEquals(ThemeId.entries.size, ThemeId.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `the cross-fade starts and ends exactly on the two palettes`() {
        val from = ThemeSpec.Sunrise.colors(dark = false)
        val to = ThemeSpec.of(ThemeId.GROVE).colors(dark = false)
        assertSame(from, lerp(from, to, 0f))
        assertSame(to, lerp(from, to, 1f))
        val mid = lerp(from, to, 0.5f)
        // Semantics don't move during a theme switch within one mode.
        assertEquals(semantics(from.extended), semantics(mid.extended))
    }
}
