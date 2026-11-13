package com.lucento.app.feature.pro

import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.domain.entitlement.ProTourStop
import com.lucento.app.feature.pro.welcome.art
import com.lucento.app.feature.pro.welcome.mascot
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
    fun the_mascot_greets_on_the_first_card_and_cheers_on_the_last() {
        val stops = ProTourStop.entries
        assertEquals(MascotMood.Greeting, stops.first().mascot)
        assertEquals(MascotMood.Celebrate, stops.last().mascot)
        // Supportive concern belongs to budgets, never to a reward.
        assertTrue(stops.none { it.mascot == MascotMood.Concerned })
    }
}
