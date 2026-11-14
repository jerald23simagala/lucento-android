package com.lucento.app

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Taps the way a person does, through Android's input system: down, two small drifts well inside the touch slop, then
 * up, at a finger's pace. `performClick` and a still down-up never move, so they can't catch a parent that consumes a
 * finger's movement and with it cancels every button inside (KNOWN_ISSUES PW-1: the Pro welcome's buttons were dead on
 * real phones while every emulator run passed).
 */
object Finger {

    /** The centre, on screen, of the last node [matcher] finds (an overlay is drawn after the app, so it comes last). */
    @OptIn(ExperimentalTestApi::class)
    fun centreOf(
        compose: AndroidComposeTestRule<*, out ComponentActivity>,
        matcher: SemanticsMatcher,
        timeout: Long = 10_000,
    ): Pair<Float, Float> {
        compose.waitUntilAtLeastOneExists(matcher, timeout)
        val centre = compose.onAllNodes(matcher).fetchSemanticsNodes().last().boundsInWindow.center
        val origin = IntArray(2)
        compose.runOnUiThread { compose.activity.window.decorView.getLocationOnScreen(origin) }
        return (origin[0] + centre.x) to (origin[1] + centre.y)
    }

    /** A finger at ([x], [y]) on screen. */
    fun tapAt(x: Float, y: Float) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val down = SystemClock.uptimeMillis()
        listOf(
            MotionEvent.ACTION_DOWN to 0f,
            MotionEvent.ACTION_MOVE to 1f,
            MotionEvent.ACTION_MOVE to 2f,
            MotionEvent.ACTION_UP to 2f,
        ).forEachIndexed { step, (action, drift) ->
            if (step > 0) SystemClock.sleep(30)
            val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
            val coords = arrayOf(
                MotionEvent.PointerCoords().apply {
                    this.x = x + drift
                    this.y = y + drift / 2f
                    pressure = 1f
                    size = 1f
                },
            )
            val event = MotionEvent.obtain(
                down, SystemClock.uptimeMillis(), action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            check(automation.injectInputEvent(event, true)) { "The finger's step $step at ($x, $y) wasn't injected" }
            event.recycle()
        }
    }
}
