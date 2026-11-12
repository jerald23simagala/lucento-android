package com.lucento.app.feature.pro.welcome

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucento.app.Finger
import com.lucento.app.core.designsystem.theme.LucentoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The curtain before Lucento Pro's tour, pressed with a [Finger] (down, a pixel or two of drift, up). On its own, over a
 * stand-in for the app, so the host's timer (which moves the curtain on after a moment) can't race the finger. Its
 * buttons were dead on real phones until 2026-11-12 (KNOWN_ISSUES PW-1).
 */
@RunWith(AndroidJUnit4::class)
class ProTourCurtainFingerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun the_curtain_answers_a_finger_and_nothing_beneath_it_does() {
        var shown = 0
        var skipped = 0
        var beneath = 0
        compose.setContent {
            LucentoTheme {
                Box(Modifier.fillMaxSize()) {
                    // The app beneath: anything that reaches it counts.
                    Box(Modifier.fillMaxSize().clickable { beneath++ })
                    ProTourCurtain(
                        title = "You’re on Lucento Pro",
                        body = "Here is what changed, in 6 quick stops.",
                        celebrate = false,
                        onShowMe = { shown++ },
                        onSkip = { skipped++ },
                    )
                }
            }
        }

        tap("Skip the tour")
        assertEquals("Skip the tour", 1, skipped)
        tap("Show me")
        assertEquals("Show me", 1, shown)

        // The dimmed screen above the card, and the card's own words, take a finger without passing it on.
        val origin = IntArray(2)
        compose.runOnUiThread { compose.activity.window.decorView.getLocationOnScreen(origin) }
        val screen = compose.onRoot().fetchSemanticsNode().boundsInWindow
        Finger.tapAt(origin[0] + screen.center.x, origin[1] + screen.top + screen.height * 0.08f)
        compose.waitForIdle()
        tap("You’re on Lucento Pro")
        assertEquals("taps that reached the app beneath", 0, beneath)
        assertEquals(1, shown)
        assertEquals(1, skipped)
    }

    private fun tap(text: String) {
        val (x, y) = Finger.centreOf(compose, hasText(text))
        Finger.tapAt(x, y)
        compose.waitForIdle()
    }
}
