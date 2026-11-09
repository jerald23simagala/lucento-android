package com.lucento.app.domain

import com.lucento.app.domain.SetupStage.GET_STARTED
import com.lucento.app.domain.SetupStage.PERSONALIZE
import com.lucento.app.domain.SetupStage.SET_UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The getting-started stepper's states, from the flow's own stage and the furthest stage reached. */
class SetupProgressTest {

    private fun statuses(p: SetupProgress) = SetupStage.entries.map { p.status(it) }

    @Test fun movingForwardCompletesEachStageBehindTheCurrentOne() {
        assertEquals(
            listOf(StageStatus.ACTIVE, StageStatus.UPCOMING, StageStatus.UPCOMING),
            statuses(SetupProgress(PERSONALIZE)),
        )
        assertEquals(
            listOf(StageStatus.COMPLETED, StageStatus.ACTIVE, StageStatus.UPCOMING),
            statuses(SetupProgress(SET_UP)),
        )
        assertEquals(
            listOf(StageStatus.COMPLETED, StageStatus.COMPLETED, StageStatus.ACTIVE),
            statuses(SetupProgress(GET_STARTED)),
        )
    }

    @Test fun goingBackKeepsFinishedStagesFinished() {
        // Reached Get started, then went back to a Set up screen: Set up is current again but keeps its check.
        val back = SetupProgress(current = SET_UP, furthest = GET_STARTED)
        assertEquals(listOf(StageStatus.COMPLETED, StageStatus.ACTIVE, StageStatus.REACHED), statuses(back))
        assertTrue(back.isDone(SET_UP))
        assertFalse(back.isDone(GET_STARTED))
        // All the way back to Personalize: both earlier stages stay done.
        val further = SetupProgress(current = PERSONALIZE, furthest = GET_STARTED)
        assertTrue(further.isDone(PERSONALIZE))
        assertEquals(StageStatus.ACTIVE, further.status(PERSONALIZE))
        assertEquals(StageStatus.COMPLETED, further.status(SET_UP))
    }

    @Test fun furthestIsNeverBehindTheCurrentStage() {
        val odd = SetupProgress(current = GET_STARTED, furthest = PERSONALIZE)
        assertEquals(GET_STARTED, odd.reached)
        assertEquals(listOf(StageStatus.COMPLETED, StageStatus.COMPLETED, StageStatus.ACTIVE), statuses(odd))
    }

    @Test fun theLineOutOfTheCurrentStageFillsWithItsProgress() {
        val p = SetupProgress(PERSONALIZE, progress = 0.6f)
        assertEquals(0.6f, p.connectorFill(PERSONALIZE))
        assertEquals(0f, p.connectorFill(SET_UP))
        assertEquals(0f, p.connectorFill(GET_STARTED)) // no line after the last stage
        val setUp = SetupProgress(SET_UP, progress = 0.3f)
        assertEquals(1f, setUp.connectorFill(PERSONALIZE))
        assertEquals(0.3f, setUp.connectorFill(SET_UP))
    }

    @Test fun linesToStagesAlreadyReachedStayFull() {
        val back = SetupProgress(current = PERSONALIZE, furthest = SET_UP, progress = 0.2f)
        assertEquals(1f, back.connectorFill(PERSONALIZE))
        assertEquals(0f, back.connectorFill(SET_UP))
    }

    @Test fun progressOutsideZeroToOneIsClamped() {
        assertEquals(1f, SetupProgress(PERSONALIZE, progress = 1.4f).connectorFill(PERSONALIZE))
        assertEquals(0f, SetupProgress(PERSONALIZE, progress = -0.5f).connectorFill(PERSONALIZE))
    }

    @Test fun talkBackHearsOneSentence() {
        assertEquals("Step 1 of 3, Personalize.", SetupProgress(PERSONALIZE).description)
        assertEquals("Step 2 of 3, Set up. Personalize done.", SetupProgress(SET_UP).description)
        assertEquals("Step 3 of 3, Get started. Personalize and Set up done.", SetupProgress(GET_STARTED).description)
        assertEquals("Step 2 of 3, Set up. Personalize done.", SetupProgress(SET_UP, furthest = GET_STARTED).description)
    }

    @Test
    fun `a counted screen gives the rail its caption, its fill and one more sentence for TalkBack`() {
        val p = SetupProgress(current = PERSONALIZE, screen = 2, screens = 5)
        assertEquals("Personalize · 2 of 5", p.caption)
        assertEquals(0.4f, p.fill, 0.0001f)
        assertEquals("Step 1 of 3, Personalize. Screen 2 of 5.", p.description)
        // Not counted: the stage alone, and the fill falls back to the given progress.
        val plain = SetupProgress(current = SET_UP, progress = 0.3f)
        assertEquals("Set up", plain.caption)
        assertEquals(0.3f, plain.fill, 0.0001f)
        assertEquals(false, SetupProgress(current = SET_UP, screen = 6, screens = 5).counted)
    }
}
