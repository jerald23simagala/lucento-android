package com.lucento.app.feature.pro

import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.OnlineStatus
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.ProFeature
import com.lucento.app.domain.entitlement.ProTourStop
import com.lucento.app.domain.entitlement.ProWelcomeMode
import com.lucento.app.feature.pro.welcome.ProWelcomeCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lucento Pro's welcome in words: numbers from the catalog, consent respected, nothing claimed before the server knows. */
class ProWelcomeCopyTest {
    private val all = Capability.entries.toSet()

    @Test fun every_stop_has_a_title_a_sentence_and_where_it_lives() {
        for (stop in ProTourStop.entries) {
            val copy = ProWelcomeCopy.stop(stop, all, signedIn = true, aiAnswersOn = false)
            assertTrue(stop.name, copy.title.isNotBlank() && copy.body.endsWith("."))
            // Said in words for everyone, since TalkBack can't see the lit control.
            assertTrue(stop.name, copy.where.endsWith("."))
        }
    }

    @Test fun a_restored_plan_is_welcomed_back_not_congratulated() {
        assertEquals("Welcome back to Lucento Pro", ProWelcomeCopy.curtainTitle(ProWelcomeMode.FIRST, restored = true))
        assertEquals("Welcome back to Pro", ProWelcomeCopy.summaryTitle(ProWelcomeMode.FIRST, restored = true))
        assertEquals("You’re on Lucento Pro", ProWelcomeCopy.curtainTitle(ProWelcomeMode.FIRST))
    }

    @Test fun limits_come_from_the_catalog() {
        val wallets = ProWelcomeCopy.stop(ProTourStop.WALLETS, all, signedIn = true, aiAnswersOn = false).changes
        assertEquals("${PlanCatalog.FREE_MONEY_WALLETS}", wallets[0].before)
        // The Lucento Pro page's word for it.
        assertEquals("No limit", wallets[0].after)
        val goals = ProWelcomeCopy.stop(ProTourStop.GOALS, all, signedIn = true, aiAnswersOn = false).changes.single()
        assertEquals("${PlanCatalog.FREE_ACTIVE_GOALS}", goals.before)
        assertEquals("${PlanCatalog.PRO_ACTIVE_GOALS}", goals.after)
        val ai = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false).changes.single()
        assertEquals("${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH} a month", ai.before)
        assertEquals("${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} a day", ai.after)
    }

    @Test fun ai_answers_stay_the_persons_choice() {
        val off = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false).body
        assertTrue(off.contains("nothing is sent until you do"))
        val on = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = true).body
        assertTrue(on.startsWith("AI answers from Gemini are on"))
    }

    @Test fun the_ai_stop_says_what_the_screen_can_do_now() {
        val signedOut = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = false, aiAnswersOn = false, online = OnlineStatus.SIGN_IN_NEEDED).body
        assertTrue(signedOut.contains("It uses your Lucento account"))
        val waiting = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false, online = OnlineStatus.CONFIRMING).body
        assertTrue(waiting.contains("as soon as your Lucento account confirms Pro"))
        val ready = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false, online = OnlineStatus.CONFIRMED).body
        assertTrue(ready.contains("Turn AI answers on here"))
    }

    @Test fun the_ai_allowance_shows_on_its_stop_only_once_the_server_has_the_plan() {
        for (status in listOf(OnlineStatus.SIGN_IN_NEEDED, OnlineStatus.CONFIRMING)) {
            assertTrue(status.name, ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = false, aiAnswersOn = false, online = status).changes.isEmpty())
        }
        assertEquals(1, ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false, online = OnlineStatus.CONFIRMED).changes.size)
    }

    @Test fun empty_pages_are_pointed_at_the_button_they_really_have() {
        assertEquals("Create a goal is in the middle of Goals.", ProWelcomeCopy.stop(ProTourStop.GOALS, all, true, false, hasGoals = false).where)
        assertEquals("Add is at the top right of Goals.", ProWelcomeCopy.stop(ProTourStop.GOALS, all, true, false).where)
        assertEquals("Add wallet is in the middle of Wallets.", ProWelcomeCopy.stop(ProTourStop.WALLETS, all, true, false, hasWallets = false).where)
    }

    @Test fun the_summary_names_what_its_sections_leave_out_and_the_replay() {
        assertTrue(ProWelcomeCopy.alsoYours(all, replay = false)!!.contains("Watch this tour again"))
        assertFalse(ProWelcomeCopy.alsoYours(all, replay = true)!!.contains("Watch this tour again"))
        assertNull(ProWelcomeCopy.alsoYours(setOf(Capability.CLOUD), replay = false))
    }

    @Test fun online_values_claim_the_plan_only_once_the_server_has_it() {
        val pro = PlanCatalog.limit(ProFeature.AI_ADVISER, all)
        assertEquals("${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY} a day", ProWelcomeCopy.onlineValue(ProFeature.AI_ADVISER, pro, OnlineStatus.CONFIRMED))
        assertEquals("Starting soon", ProWelcomeCopy.onlineValue(ProFeature.AI_ADVISER, pro, OnlineStatus.CONFIRMING))
        assertEquals("Needs sign-in", ProWelcomeCopy.onlineValue(ProFeature.CLOUD_SYNC, Limit.Unlimited, OnlineStatus.SIGN_IN_NEEDED))
        assertEquals("Not in this version", ProWelcomeCopy.onlineValue(ProFeature.SHARED_SPACE, Limit.Count(1), OnlineStatus.NOT_IN_THIS_BUILD))
        // TalkBack never hears "now" for what isn't on yet.
        assertFalse(ProWelcomeCopy.spokenRow("Cloud sync", "Not included", "Needs sign-in", confirmed = false).contains("now"))
        assertTrue(ProWelcomeCopy.spokenRow("Money wallets", "3", "Unlimited", confirmed = true).endsWith("Unlimited now"))
    }

    @Test fun trends_read_as_months() {
        assertEquals("3 months", ProWelcomeCopy.limitText(ProFeature.INSIGHTS_LONG_PERIODS, Limit.None))
        assertEquals("12 months", ProWelcomeCopy.limitText(ProFeature.INSIGHTS_LONG_PERIODS, Limit.Unlimited))
        // The free plan has 3 months of trends: shown as such, never as "—".
        assertEquals("3 months", ProWelcomeCopy.freeShown(ProFeature.INSIGHTS_LONG_PERIODS, Limit.None))
        assertEquals("—", ProWelcomeCopy.freeShown(ProFeature.CLOUD_SYNC, Limit.None))
        assertEquals("Trends", ProWelcomeCopy.rowName(ProFeature.INSIGHTS_LONG_PERIODS))
    }

    @Test fun sync_and_shared_ask_a_signed_out_person_to_sign_in() {
        assertTrue(ProWelcomeCopy.stop(ProTourStop.SYNC, all, signedIn = false, aiAnswersOn = false).body.startsWith("Sign in here"))
        assertTrue(ProWelcomeCopy.stop(ProTourStop.SHARED, all, signedIn = false, aiAnswersOn = false).body.contains("sign in"))
        assertFalse(ProWelcomeCopy.stop(ProTourStop.SHARED, all, signedIn = true, aiAnswersOn = false).body.contains("sign in"))
    }

    @Test fun gemini_is_named_where_it_does_the_work() {
        assertTrue(ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, true, false).title.contains("Gemini"))
        assertTrue(ProWelcomeCopy.stop(ProTourStop.SMART_READING, all, true, false).title.contains("Gemini"))
        // Smarter reading stays the person's choice: the stop says where to turn it on, never that it is on.
        val off = ProWelcomeCopy.stop(ProTourStop.SMART_READING, all, true, false, smartReadingOn = false).body
        assertTrue(off.contains("nothing is sent until you do"))
        val signedOut = ProWelcomeCopy.stop(ProTourStop.SMART_READING, all, false, false, online = OnlineStatus.SIGN_IN_NEEDED).body
        assertTrue(signedOut.contains("It uses your Lucento account"))
    }

    @Test fun limits_read_naturally() {
        assertEquals("No limit", ProWelcomeCopy.limitText(ProFeature.MONEY_WALLETS, Limit.Unlimited))
        assertEquals("Included", ProWelcomeCopy.limitText(ProFeature.CLOUD_SYNC, Limit.Unlimited))
        assertEquals("Not included", ProWelcomeCopy.limitText(ProFeature.CLOUD_SYNC, Limit.None))
        assertEquals("1 Partner", ProWelcomeCopy.limitText(ProFeature.SHARED_SPACE, Limit.Count(1)))
        assertEquals("10 a month", ProWelcomeCopy.limitText(ProFeature.AI_ADVISER, Limit.PerMonth(10)))
        assertEquals("20", ProWelcomeCopy.limitText(ProFeature.SAVINGS_GOALS, Limit.Count(20)))
    }

    @Test fun the_summary_names_the_moment_and_leads_to_the_medal_only_when_there_is_one() {
        assertEquals("Congratulations, you’re Pro", ProWelcomeCopy.summaryTitle(ProWelcomeMode.FIRST))
        assertEquals("Collect your award", ProWelcomeCopy.summaryButton(ProWelcomeMode.FIRST))
        assertEquals("Done", ProWelcomeCopy.summaryButton(ProWelcomeMode.REPLAY))
        assertEquals("Done", ProWelcomeCopy.summaryButton(ProWelcomeMode.RETURNING))
        assertEquals("What Pro gives you", ProWelcomeCopy.summaryTitle(ProWelcomeMode.REPLAY))
    }

    @Test fun online_status_never_overclaims() {
        assertTrue(ProWelcomeCopy.onlineStatus(OnlineStatus.CONFIRMED)!!.startsWith("Confirmed"))
        assertTrue(ProWelcomeCopy.onlineStatus(OnlineStatus.CONFIRMING)!!.contains("as soon as"))
        assertTrue(ProWelcomeCopy.onlineStatus(OnlineStatus.SIGN_IN_NEEDED)!!.contains("Sign in"))
        assertNull(ProWelcomeCopy.onlineStatus(OnlineStatus.NOT_IN_PLAN))
        assertTrue(ProWelcomeCopy.onlineStatus(OnlineStatus.NOT_IN_THIS_BUILD)!!.contains("aren’t available here"))
    }

    @Test fun progress_names_the_chapter_and_the_step() {
        assertEquals("Your money · 1 of 8", ProWelcomeCopy.progress(ProTourStop.WALLETS, 0, 8))
        assertEquals("Make it yours · 8 of 8", ProWelcomeCopy.progress(ProTourStop.THEMES, 7, 8))
    }
}
