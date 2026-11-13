package com.lucento.app.feature.pro

import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MascotPose
import com.lucento.app.domain.entitlement.ProTourStop
import com.lucento.app.feature.pro.welcome.art
import com.lucento.app.feature.pro.welcome.doodleSpots
import com.lucento.app.feature.pro.welcome.leftEdge
import com.lucento.app.feature.pro.welcome.propObjectRight
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
        for (w in widths) for (h in heights) for (a in aspects) for (touches in listOf(true, false)) {
            val p = pairLayout(w, h, a, touches)
            val where = "band ${w}x$h, prop $a, touches $touches"
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
    fun a_pointing_hand_reaches_over_the_prop_and_other_poses_stand_beside_it() {
        for (w in widths) for (h in heights) for (a in aspects) {
            val where = "band ${w}x$h, prop $a"
            val pointing = pairLayout(w, h, a, touches = true)
            assertEquals("hand over the prop: $where", pointing.propX + pointing.propWidth - 8f, pointing.mascotX + pointing.mascot * 0.06f, 0.01f)
            // Measured from what shows: where the prop itself ends to where the pose's character starts.
            for (objectRight in listOf(0.88f, 0.93f, 1f)) for (poseLeft in listOf(0.068f, 0.084f)) {
                val beside = pairLayout(w, h, a, touches = false, objectRight = objectRight, poseLeft = poseLeft)
                assertEquals(
                    "10 dp beside the prop ($objectRight, $poseLeft): $where",
                    beside.propX + beside.propWidth * objectRight + 10f,
                    beside.mascotX + beside.mascot * poseLeft,
                    0.01f,
                )
            }
        }
    }

    @Test
    fun the_pair_sits_a_little_below_the_middle_with_room_for_the_hop() {
        for (w in widths) for (h in heights) for (a in aspects) {
            val p = pairLayout(w, h, a)
            val where = "band ${w}x$h, prop $a"
            val top = minOf(p.mascotY + p.mascot * 0.03f, p.propY)
            val bottom = p.mascotY + p.mascot * 0.97f
            val above = top
            val below = h - bottom
            assertTrue("gap above ($above) more than 1.3 times the gap below ($below): $where", above <= 1.3f * below + 0.5f)
            // The arrival hop lifts the hair about 9 % of the mascot's size: it must stay inside the band.
            assertTrue("no room for the hop: $where", p.mascotY + p.mascot * 0.03f >= 0.09f * p.mascot - 0.01f)
        }
    }

    @Test
    fun doodles_are_whole_and_never_over_or_between_the_pair() {
        for (w in widths) for (h in heights) for (a in aspects) for (touches in listOf(true, false)) {
            val pair = pairLayout(w, h, a, touches)
            val clear = pair.keepClear()
            val spots = doodleSpots(w, h, clear)
            val where = "band ${w}x$h, prop $a, touches $touches"
            spots.forEach { spot ->
                val b = spot.box
                assertTrue("a doodle cut by the band's edge: $where", b.left >= 0f && b.top >= 0f && b.right <= w && b.bottom <= h)
                assertTrue("a doodle over the pair: $where", clear.none { it.overlaps(b) })
                // At least 6 dp inside the card's rounded top corners (22 dp), and clear of the seam at the band's foot.
                if (b.left < 22f && b.top < 22f) assertTrue("a doodle in a corner: $where", kotlin.math.hypot(22f - b.left, 22f - b.top) <= 16.01f)
                if (b.right > w - 22f && b.top < 22f) assertTrue("a doodle in a corner: $where", kotlin.math.hypot(b.right - (w - 22f), 22f - b.top) <= 16.01f)
                assertTrue("a doodle on the seam: $where", b.bottom <= h - 6f)
            }
            // The gap between a standing character and its prop (10 dp wide, as tall as the prop) is inside the clear
            // areas (6 dp around each), so no doodle sits between them.
            if (!touches) {
                val gapLeft = pair.propX + pair.propWidth
                val gapRight = pair.mascotX + pair.mascot * 0.06f
                val gapTop = pair.propY
                val gapBottom = pair.propY + pair.propHeight
                assertTrue(
                    "a doodle in the gap: $where",
                    spots.none { it.box.right > gapLeft && it.box.left < gapRight && it.box.bottom > gapTop && it.box.top < gapBottom },
                )
            }
        }
        // And the band isn't bare: a big band keeps doodles around the pair.
        assertTrue(doodleSpots(379f, 207f, pairLayout(379f, 207f, 0.64f).keepClear()).size >= 4)
    }

    // The ten props' visible width ÷ height, from the exported perk_art_* files (tools/illustrations/export_perk_art.py).
    private val propAspects = mapOf(
        ProTourStop.WALLETS to 548f / 850f,
        ProTourStop.NET_WORTH to 721f / 741f,
        ProTourStop.SMART_READING to 746f / 642f,
        ProTourStop.GOALS to 582f / 888f,
        ProTourStop.INSIGHTS_PERIODS to 536f / 877f,
        ProTourStop.STATEMENTS to 560f / 807f,
        ProTourStop.AI_ANSWERS to 748f / 587f,
        ProTourStop.SYNC to 690f / 711f,
        ProTourStop.SHARED to 884f / 631f,
        ProTourStop.THEMES to 956f / 536f,
    )

    @Test
    fun every_card_keeps_a_texture_of_doodles_around_the_pair() {
        // The owner asked for subtle doodles instead of plain blue: keeping them off the pair must not empty the band
        // (critic, polish check: one clear box left 4–8 per card). Bands: 411 dp and 360 dp phones, and the short band.
        // Where the pair fills most of the band (wide props) there is little blue left to texture, so the floor per card
        // is 4; across the deck at 411 dp the bands average at least 8.
        for ((w, h) in listOf(378f to 208f, 327f to 179f, 378f to 119f)) {
            val counts = propAspects.map { (stop, aspect) ->
                val touches = stop.mascot == MascotMood.Pointing
                val pair = pairLayout(w, h, aspect, touches, stop.propObjectRight, stop.mascot.leftEdge)
                val n = doodleSpots(w, h, pair.keepClear()).size
                assertTrue("$stop in a ${w}x$h band has only $n doodles", n >= 4)
                n
            }
            if (w == 378f && h == 208f) assertTrue("the deck averages ${counts.average()} doodles at 411 dp", counts.average() >= 8.0)
        }
    }
}
