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
        x.cash, x.bank, x.eWallet, x.savings, x.credit, x.loan,
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
