package com.lucento.app.feature.pro

import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MascotPose
import com.lucento.app.domain.entitlement.ProTourStop
import com.lucento.app.feature.pro.welcome.art
import com.lucento.app.feature.pro.welcome.mascot
import com.lucento.app.feature.pro.welcome.pairLayout
import com.lucento.app.feature.pro.welcome.thumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerkArtTest {
    @Test
    fun every_card_has_its_own_picture_and_its_own_thumbnail() {
        val stops = ProTourStop.entries
        val pictures = stops.map { it.art }
        val thumbs = stops.map { it.thumb }
        assertEquals("one picture per card", stops.size, pictures.toSet().size)
        assertEquals("one thumbnail per card", stops.size, thumbs.toSet().size)
        assertTrue("a thumbnail is never a card's picture", pictures.intersect(thumbs.toSet()).isEmpty())
    }

    @Test
    fun the_mascot_greets_on_the_first_card_and_keeps_its_cheer_for_the_award() {
        val stops = ProTourStop.entries
        assertEquals(MascotMood.Greeting, stops.first().mascot)
        // The cheering picture comes with the award after the cards, so no card shows it.
        assertTrue(stops.none { it.mascot.pose == MascotPose.Success })
        // Supportive concern belongs to budgets, never to a reward.
        assertTrue(stops.none { it.mascot == MascotMood.Concerned })
    }

    @Test
    fun no_two_neighbouring_cards_show_the_same_mascot_picture() {
        val poses = ProTourStop.entries.map { it.mascot.pose }
        poses.zipWithNext().forEachIndexed { i, (a, b) -> assertTrue("cards ${i + 1} and ${i + 2} both show $a", a != b) }
    }

    // The bands the deck really makes: 300–380 dp wide, from the large-text minimum (300 : 84) to nearly square; props
    // from tall (a wallet) to wide (paint pots).
    private val widths = listOf(300f, 328f, 346f, 379f)
    private val heights = listOf(84f, 106f, 150f, 200f, 264f, 341f, 380f)
    private val aspects = listOf(0.64f, 0.97f, 1.4f, 1.79f)

    @Test
    fun the_pair_and_the_torch_glow_stay_inside_every_band() {
        for (w in widths) for (h in heights) for (a in aspects) {
            val p = pairLayout(w, h, a)
            val where = "band ${w}x$h, prop $a"
            assertTrue("prop starts left of the band: $where", p.propX >= 0f)
            assertTrue("prop runs past the band: $where", p.propX + p.propWidth <= w + 0.01f)
            assertTrue("glow runs past the band: $where", p.mascotX + p.mascot * (0.965f + 0.163f) <= w + 0.01f)
            assertTrue("head above the band: $where", p.mascotY + p.mascot * 0.03f >= -0.01f)
            assertTrue("prop above the band: $where", p.propY >= -0.01f)
            assertTrue("shadow below the band: $where", p.mascotY + p.mascot * 0.97f <= h + 0.01f)
            assertEquals("prop not standing on the feet line: $where", p.mascotY + p.mascot * 0.93f, p.propY + p.propHeight, 0.01f)
        }
    }

    @Test
    fun the_mascots_hand_reaches_over_the_prop_and_the_pair_sits_a_little_below_the_middle() {
        for (w in widths) for (h in heights) for (a in aspects) {
            val p = pairLayout(w, h, a)
            val where = "band ${w}x$h, prop $a"
            assertEquals("hand over the prop: $where", p.propX + p.propWidth - 8f, p.mascotX + p.mascot * 0.06f, 0.01f)
            val top = minOf(p.mascotY + p.mascot * 0.03f, p.propY)
            val bottom = p.mascotY + p.mascot * 0.97f
            val above = top
            val below = h - bottom
            assertTrue("gap above ($above) more than 1.3 times the gap below ($below): $where", above <= 1.3f * below + 0.5f)
            // The arrival hop lifts the hair about 9 % of the mascot's size: it must stay inside the band.
            assertTrue("no room for the hop: $where", p.mascotY + p.mascot * 0.03f >= 0.09f * p.mascot - 0.01f)
        }
    }
}
