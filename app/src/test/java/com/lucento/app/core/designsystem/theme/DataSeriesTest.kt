package com.lucento.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Each theme's categorical chart series must stay readable for everyone (the dataviz palette gates):
 *  - OKLCH lightness inside the mode's band (light 0.43–0.77, dark 0.48–0.67) and chroma ≥ 0.10 (not gray);
 *  - every adjacent pair ≥ 8 OKLab ΔE×100 apart under protanopia and deuteranopia (Machado 2009, severity 1);
 *  - every adjacent pair ≥ 15 apart for normal vision;
 *  - every slot ≥ 3:1 against the theme's card surface.
 * The slot order is the safety mechanism, so a reordered or edited series fails here until it is re-validated.
 */
class DataSeriesTest {

    private fun lin(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linear(color: Color) = doubleArrayOf(lin(color.red), lin(color.green), lin(color.blue))

    private fun oklab(rgb: DoubleArray): DoubleArray {
        val (r, g, b) = Triple(rgb[0], rgb[1], rgb[2])
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private val protan = arrayOf(
        doubleArrayOf(0.152286, 1.052583, -0.204868),
        doubleArrayOf(0.114503, 0.786281, 0.099216),
        doubleArrayOf(-0.003882, -0.048116, 1.051998),
    )
    private val deutan = arrayOf(
        doubleArrayOf(0.367322, 0.860646, -0.227968),
        doubleArrayOf(0.280085, 0.672501, 0.047413),
        doubleArrayOf(-0.011820, 0.042940, 0.968881),
    )

    private fun simulate(rgb: DoubleArray, m: Array<DoubleArray>) = DoubleArray(3) { i ->
        (m[i][0] * rgb[0] + m[i][1] * rgb[1] + m[i][2] * rgb[2]).coerceIn(0.0, 1.0)
    }

    private fun deltaE(a: Color, b: Color, cvd: Array<DoubleArray>? = null): Double {
        val la = oklab(linear(a).let { if (cvd == null) it else simulate(it, cvd) })
        val lb = oklab(linear(b).let { if (cvd == null) it else simulate(it, cvd) })
        return 100 * sqrt((la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2))
    }

    private fun lightness(c: Color) = oklab(linear(c))[0]

    private fun chroma(c: Color) = oklab(linear(c)).let { hypot(it[1], it[2]) }

    private fun luminance(c: Color) = linear(c).let { 0.2126 * it[0] + 0.7152 * it[1] + 0.0722 * it[2] }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun Color.hex() = "#%06X".format((0xFFFFFF and (red * 255 + 0.5).toInt().shl(16) or (green * 255 + 0.5).toInt().shl(8) or (blue * 255 + 0.5).toInt()))

    private data class Series(val name: String, val dark: Boolean, val slots: List<Color>, val card: Color)

    private val series = ThemeSpec.all.flatMap { spec ->
        listOfNotNull(
            spec.light?.let { Series("${spec.displayName} light", false, it.extended.categorical, it.scheme.surfaceContainerLowest) },
            Series("${spec.displayName} dark", true, spec.dark.extended.categorical, spec.dark.scheme.surfaceContainer),
        )
    }

    @Test
    fun `every series has seven slots`() {
        series.forEach { assertEquals("${it.name} slots", 7, it.slots.size) }
    }

    @Test
    fun `series pass the lightness, chroma, separation and contrast gates`() {
        val failures = mutableListOf<String>()
        for (s in series) {
            val (lo, hi) = if (s.dark) 0.48 to 0.67 else 0.43 to 0.77
            s.slots.forEach { c ->
                val l = lightness(c)
                if (l < lo || l > hi) failures += "${s.name}: ${c.hex()} lightness %.3f outside $lo–$hi".format(l)
                val ch = chroma(c)
                if (ch < 0.10) failures += "${s.name}: ${c.hex()} chroma %.3f below 0.10".format(ch)
                val cr = contrast(c, s.card)
                if (cr < 3.0) failures += "${s.name}: ${c.hex()} is %.2f:1 on cards, needs 3:1".format(cr)
            }
            s.slots.zipWithNext().forEach { (a, b) ->
                val cvd = min(deltaE(a, b, protan), deltaE(a, b, deutan))
                if (cvd < 8.0) failures += "${s.name}: ${a.hex()}↔${b.hex()} colour-blind ΔE %.1f, needs 8".format(cvd)
                val normal = deltaE(a, b)
                if (normal < 15.0) failures += "${s.name}: ${a.hex()}↔${b.hex()} ΔE %.1f, needs 15".format(normal)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
