package com.lucento.app.feature.pro.welcome

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lucento.app.Finger
import com.lucento.app.core.designsystem.theme.LucentoTheme
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.ProTourStop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The deck of Lucento Pro's welcome, pressed with a [Finger] (down, a pixel or two of drift, up), on its own over a
 * stand-in for the app that counts every touch reaching it. The old tour's buttons were dead on real phones because its
 * touch blocker wrapped them (KNOWN_ISSUES PW-1); the deck's blocker lies beside them, and this keeps it that way.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ProRevealDeckFingerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun the_deck_answers_a_finger_and_nothing_beneath_it_does() {
        var beneath = 0
        var collections = 0
        var index by mutableIntStateOf(-1)
        val all = Capability.entries.toSet()
        val cards = listOf(ProTourStop.WALLETS, ProTourStop.GOALS, ProTourStop.THEMES).map {
            RevealCard(it, ProWelcomeCopy.stop(it, all, signedIn = true, aiAnswersOn = false))
        }
        compose.setContent {
            LucentoTheme {
                Box(Modifier.fillMaxSize()) {
                    // The app beneath: anything that reaches it counts.
                    Box(Modifier.fillMaxSize().clickable { beneath++ })
                    ProRevealDeck(
                        cards = cards,
                        title = "You’re on Lucento Pro",
                        body = ProWelcomeCopy.revealBody(cards.size),
                        unwrap = true,
                        index = index,
                        onIndex = { index = it },
                        onCollection = { collections++ },
                    )
                }
            }
        }

        tap(ProWelcomeCopy.OPEN_DECK)
        compose.waitUntilAtLeastOneExists(hasText("Add every account you use"), 8_000)
        tap(ProWelcomeCopy.NEXT)
        compose.waitUntilAtLeastOneExists(hasText("Save for more at once"), 8_000)
        tap(ProWelcomeCopy.PREVIOUS)
        compose.waitUntilAtLeastOneExists(hasText("Add every account you use"), 8_000)
        assertEquals("the card face up", 0, index)

        // The dim beside the card, and the card's own words, take a finger without passing it on.
        val origin = IntArray(2)
        compose.runOnUiThread { compose.activity.window.decorView.getLocationOnScreen(origin) }
        val screen = compose.onRoot().fetchSemanticsNode().boundsInWindow
        Finger.tapAt(origin[0] + screen.left + 6f, origin[1] + screen.center.y)
        compose.waitForIdle()
        tap("Add every account you use")
        assertEquals("taps that reached the app beneath", 0, beneath)
        assertEquals("the dim and the words move nothing", 0, index)

        tap(ProWelcomeCopy.SKIP)
        assertEquals("Skip leads to the collection", 1, collections)
    }

    private fun tap(text: String) {
        val (x, y) = Finger.centreOf(compose, hasText(text))
        Finger.tapAt(x, y)
        compose.waitForIdle()
    }
}
