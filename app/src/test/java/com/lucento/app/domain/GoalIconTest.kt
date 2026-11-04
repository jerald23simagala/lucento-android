package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goal icons (owner, 2026-11-04): chosen keys, older scene keys, and the automatic choice from the name. */
class GoalIconTest {

    @Test fun keysRoundTripAndStayBackupSafe() {
        GoalIcon.entries.forEach {
            assertEquals(it, GoalIcon.fromKey(it.key))
            // Backups accept a scene key of 1–64 characters without spaces.
            assertTrue(it.key.length in 1..64 && it.key.none(Char::isWhitespace))
        }
    }

    @Test fun olderScenePicturesMapToTheClosestIcon() {
        assertEquals(GoalIcon.TRAVEL, GoalIcon.fromKey("japan_fuji"))
        assertEquals(GoalIcon.TRAVEL, GoalIcon.fromKey("sunrise"))
        assertEquals(GoalIcon.EMERGENCY, GoalIcon.fromKey("mountains"))
        assertEquals(GoalIcon.SAVINGS, GoalIcon.fromKey("none"))
        assertNull(GoalIcon.fromKey("icon_spaceship"))
        assertNull(GoalIcon.fromKey("whatever"))
        assertNull(GoalIcon.fromKey(null))
    }

    @Test fun normalizeKeepsOnlyKnownKeys() {
        assertEquals("icon_car", GoalIcon.normalizeKey("icon_car"))
        assertEquals("japan_fuji", GoalIcon.normalizeKey("japan_fuji"))
        assertNull(GoalIcon.normalizeKey("icon_spaceship"))
        assertNull(GoalIcon.normalizeKey(null))
    }

    @Test fun theNameDecidesWhenNothingIsChosen() {
        assertEquals(GoalIcon.TRAVEL, GoalIcon.of("Trip to Japan"))
        assertEquals(GoalIcon.TRAVEL, GoalIcon.of("日本 2027"))
        assertEquals(GoalIcon.TRAVEL, GoalIcon.of("Honeymoon in Bali"))
        assertEquals(GoalIcon.EMERGENCY, GoalIcon.of("Emergency fund"))
        assertEquals(GoalIcon.HOME, GoalIcon.of("Condo down payment"))
        assertEquals(GoalIcon.CAR, GoalIcon.of("New car"))
        assertEquals(GoalIcon.GADGET, GoalIcon.of("MacBook Pro"))
        assertEquals(GoalIcon.EDUCATION, GoalIcon.of("Kids' tuition"))
        assertEquals(GoalIcon.WEDDING, GoalIcon.of("Wedding fund"))
        assertEquals(GoalIcon.GIFT, GoalIcon.of("Christmas gifts"))
        assertEquals(GoalIcon.SAVINGS, GoalIcon.of("Something else"))
    }

    @Test fun aChoiceWinsOverTheName() {
        assertEquals(GoalIcon.CAR, GoalIcon.forGoal("Trip to Japan", "icon_car"))
        assertEquals(GoalIcon.TRAVEL, GoalIcon.forGoal("Trip to Japan", "icon_spaceship"))
    }

    @Test fun suggestionsFitTheNameLimitAndUseDistinctIcons() {
        GoalSuggestions.all.forEach { assertTrue(it.name.length <= 40) }
        assertEquals(GoalSuggestions.all.size, GoalSuggestions.all.map { it.icon }.toSet().size)
        GoalSuggestions.all.forEach { assertEquals(it.name, it.icon, GoalIcon.of(it.name)) }
    }
}
