package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalSceneTest {

    @Test
    fun `japan destinations get the fuji scene`() {
        listOf("Vacation to Japan", "Tokyo 2027", "Kyoto trip", "japan!", "Mt. Fuji climb", "日本旅行").forEach {
            assertEquals(it, GoalScene.JAPAN, GoalScene.of(it))
        }
    }

    @Test
    fun `other trips get the travel scene`() {
        listOf("Summer holiday", "Trip to Lisbon", "Family vacation", "Weekend getaway").forEach {
            assertEquals(it, GoalScene.TRAVEL, GoalScene.of(it))
        }
    }

    @Test
    fun `everything else stays plain, and words must match whole`() {
        listOf("Emergency fund", "New laptop", "Tripod", "Panama hat", "").forEach {
            assertEquals(it, GoalScene.NONE, GoalScene.of(it))
        }
    }
}

class GoalSceneChoiceTest {

    @Test
    fun `a stored choice wins over the name`() {
        assertEquals(GoalScene.MOUNTAINS, GoalScene.forGoal("Vacation to Japan", "mountains"))
        assertEquals(GoalScene.NONE, GoalScene.forGoal("Tokyo", "none"))
        assertEquals(GoalScene.JAPAN, GoalScene.forGoal("Emergency fund", "japan_fuji"))
    }

    @Test
    fun `no choice or an unknown key falls back to the name`() {
        assertEquals(GoalScene.JAPAN, GoalScene.forGoal("Tokyo", null))
        assertEquals(GoalScene.NONE, GoalScene.forGoal("Laptop", "something-old"))
    }

    @Test
    fun `keys round-trip and presets fit the name limit`() {
        GoalScene.entries.forEach { assertEquals(it, GoalScene.fromKey(it.key)) }
        GoalPresets.all.forEach { assert(it.name.length <= 40) }
        assertEquals(GoalScene.JAPAN, GoalPresets.all.first().scene)
    }
}
