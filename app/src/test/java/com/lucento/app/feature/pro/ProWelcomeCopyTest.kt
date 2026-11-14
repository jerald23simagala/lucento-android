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
    private val year = ProCopy.ServiceTerm.Until("Nov 12, 2027")
    private val serviceCards = listOf(ProTourStop.SMART_READING, ProTourStop.AI_ANSWERS, ProTourStop.SYNC, ProTourStop.SHARED)

    @Test fun every_card_has_its_chapter_a_title_where_it_lives_a_sentence_and_a_short_name() {
        for (stop in ProTourStop.entries) {
            val copy = ProWelcomeCopy.stop(stop, all, signedIn = true, aiAnswersOn = false)
            assertEquals(stop.name, stop.chapter.title, copy.chapter)
            assertTrue(stop.name, copy.title.isNotBlank() && copy.body.endsWith("."))
            // Said in words: a card can't point at the control.
            assertTrue(stop.name, copy.where.endsWith("."))
            assertTrue(stop.name, copy.short.isNotBlank())
        }
    }

    @Test fun no_card_talks_about_a_screen_it_is_not_on() {
        // The tour's words said "here" and "like this one"; a card is never on that screen.
        for (stop in ProTourStop.entries) {
            for (online in OnlineStatus.entries) {
                for (signedIn in listOf(true, false)) {
                    val copy = ProWelcomeCopy.stop(stop, all, signedIn, aiAnswersOn = false, online = online)
                    val words = copy.body + " " + copy.where
                    assertFalse("$stop $online", Regex("\\bhere\\b").containsMatchIn(words))
                    assertFalse("$stop $online", words.contains("like this one"))
                }
            }
        }
    }

    @Test fun a_restored_plan_is_welcomed_back_not_congratulated() {
        assertEquals("Welcome back to Lucento\u00A0Pro", ProWelcomeCopy.revealTitle(ProWelcomeMode.FIRST, restored = true))
        assertEquals("Welcome back to Pro", ProWelcomeCopy.collectionTitle(ProWelcomeMode.FIRST, restored = true))
        assertEquals("You’re on Lucento\u00A0Pro", ProWelcomeCopy.revealTitle(ProWelcomeMode.FIRST))
        assertEquals("Your Pro cards", ProWelcomeCopy.revealTitle(ProWelcomeMode.REPLAY))
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
        assertEquals("${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH}\u00A0a\u00A0month", ai.before)
        assertEquals("${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY}\u00A0a\u00A0day", ai.after)
        assertEquals("Money wallets: 3 on the free plan, No limit now", ProWelcomeCopy.spokenChange(wallets[0]))
    }

    @Test fun ai_answers_stay_the_persons_choice() {
        val off = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false).body
        assertTrue(off.contains("nothing is sent until you do"))
        val on = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = true).body
        assertTrue(on.startsWith("AI answers from Gemini are on"))
    }

    @Test fun the_ai_card_says_what_can_be_done_now() {
        val signedOut = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = false, aiAnswersOn = false, online = OnlineStatus.SIGN_IN_NEEDED).body
        assertTrue(signedOut.contains("It uses your Lucento account"))
        val waiting = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false, online = OnlineStatus.CONFIRMING).body
        assertTrue(waiting.contains("as soon as your Lucento account confirms Pro"))
        val ready = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false, online = OnlineStatus.CONFIRMED).body
        assertTrue(ready.contains("Turn it on in Ask Lucento"))
    }

    @Test fun the_ai_allowance_shows_only_once_the_server_has_the_plan() {
        for (status in listOf(OnlineStatus.SIGN_IN_NEEDED, OnlineStatus.CONFIRMING)) {
            val copy = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = false, aiAnswersOn = false, online = status)
            assertTrue(status.name, copy.changes.isEmpty())
            assertNull(status.name, copy.gain)
        }
        val confirmed = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = true, aiAnswersOn = false, online = OnlineStatus.CONFIRMED)
        assertEquals(1, confirmed.changes.size)
        assertEquals("${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY}\u00A0a\u00A0day", confirmed.gain)
    }

    @Test fun online_services_say_how_long_they_are_included() {
        for (stop in listOf(ProTourStop.SMART_READING, ProTourStop.SYNC, ProTourStop.SHARED)) {
            assertEquals(stop.name, "Included until Nov 12, 2027", ProWelcomeCopy.stop(stop, all, true, false, term = year).term)
            assertEquals(stop.name, "While you subscribe", ProWelcomeCopy.stop(stop, all, true, false, term = ProCopy.ServiceTerm.Subscription).term)
            assertEquals(stop.name, "With Lucento Cloud", ProWelcomeCopy.stop(stop, all, true, false, term = ProCopy.ServiceTerm.Cloud).term)
        }
        // The AI adviser carries on after a Lifetime's included year, at the free plan's allowance (as the Pro page says).
        assertEquals(
            "${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY}\u00A0a\u00A0day until Nov\u00A012,\u00A02027, then ${PlanCatalog.FREE_AI_QUESTIONS_PER_MONTH}\u00A0a\u00A0month",
            ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, true, false, term = year).term,
        )
        // What runs on the phone is owned: never a term.
        for (stop in ProTourStop.entries - serviceCards.toSet()) {
            assertNull(stop.name, ProWelcomeCopy.stop(stop, all, true, false, term = year).term)
        }
    }

    @Test fun the_collection_shows_each_cards_gain_or_until_when() {
        fun tag(stop: ProTourStop, term: ProCopy.ServiceTerm?, online: OnlineStatus = OnlineStatus.CONFIRMED): String? =
            ProWelcomeCopy.collectionTag(stop, ProWelcomeCopy.stop(stop, all, true, false, online = online, term = term), term)
        assertEquals("No limit", tag(ProTourStop.WALLETS, year))
        assertEquals("${PlanCatalog.PRO_ACTIVE_GOALS} goals", tag(ProTourStop.GOALS, year))
        assertEquals("12 months", tag(ProTourStop.INSIGHTS_PERIODS, year))
        assertEquals("Until Nov 12, 2027", tag(ProTourStop.SYNC, year))
        // The AI adviser shows its allowance, never a date: it doesn't stop when the year ends.
        assertEquals("${PlanCatalog.PRO_AI_QUESTIONS_PER_DAY}\u00A0a\u00A0day", tag(ProTourStop.AI_ANSWERS, year))
        assertNull(tag(ProTourStop.AI_ANSWERS, year, OnlineStatus.CONFIRMING))
        assertNull(tag(ProTourStop.SYNC, ProCopy.ServiceTerm.Subscription))
        assertNull(tag(ProTourStop.THEMES, year))
    }

    @Test fun empty_pages_are_pointed_at_the_button_they_really_have() {
        assertEquals("Tap Create a goal in the middle of Goals.", ProWelcomeCopy.stop(ProTourStop.GOALS, all, true, false, hasGoals = false).where)
        assertEquals("Tap Add at the top right of Goals.", ProWelcomeCopy.stop(ProTourStop.GOALS, all, true, false).where)
        assertEquals("Tap Add wallet in the middle of Wallets.", ProWelcomeCopy.stop(ProTourStop.WALLETS, all, true, false, hasWallets = false).where)
        assertEquals("Tap Add at the top right of Wallets.", ProWelcomeCopy.stop(ProTourStop.WALLETS, all, true, false).where)
        assertEquals("In Wallets, under Add at the top right.", ProWelcomeCopy.stop(ProTourStop.NET_WORTH, all, true, false).where)
        assertEquals("In Wallets, under Add wallet in the middle.", ProWelcomeCopy.stop(ProTourStop.NET_WORTH, all, true, false, hasWallets = false).where)
    }

    @Test fun trends_read_as_months() {
        assertEquals("3 months", ProWelcomeCopy.limitText(ProFeature.INSIGHTS_LONG_PERIODS, Limit.None))
        assertEquals("12 months", ProWelcomeCopy.limitText(ProFeature.INSIGHTS_LONG_PERIODS, Limit.Unlimited))
    }

    @Test fun sync_and_shared_ask_a_signed_out_person_to_sign_in() {
        assertTrue(ProWelcomeCopy.stop(ProTourStop.SYNC, all, signedIn = false, aiAnswersOn = false).body.startsWith("Sign in from Settings\u00A0› Account\u00A0&\u00A0sync"))
        assertTrue(ProWelcomeCopy.stop(ProTourStop.SHARED, all, signedIn = false, aiAnswersOn = false).body.contains("sign in"))
        assertFalse(ProWelcomeCopy.stop(ProTourStop.SHARED, all, signedIn = true, aiAnswersOn = false).body.contains("sign in"))
    }

    @Test fun gemini_is_named_where_it_does_the_work() {
        assertTrue(ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, true, false).title.contains("Gemini"))
        assertTrue(ProWelcomeCopy.stop(ProTourStop.SMART_READING, all, true, false).title.contains("Gemini"))
        // Smarter reading stays the person's choice: the card says where to turn it on, never that it is on.
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
        assertEquals("10\u00A0a\u00A0month", ProWelcomeCopy.limitText(ProFeature.AI_ADVISER, Limit.PerMonth(10)))
        assertEquals("20", ProWelcomeCopy.limitText(ProFeature.SAVINGS_GOALS, Limit.Count(20)))
    }

    @Test fun the_collection_leads_to_the_medal_only_when_there_is_one() {
        assertEquals("Congratulations, you’re Pro", ProWelcomeCopy.collectionTitle(ProWelcomeMode.FIRST))
        assertEquals("Collect your award", ProWelcomeCopy.collectionButton(ProWelcomeMode.FIRST))
        assertEquals("Done", ProWelcomeCopy.collectionButton(ProWelcomeMode.REPLAY))
        assertEquals("Done", ProWelcomeCopy.collectionButton(ProWelcomeMode.RETURNING))
        assertEquals("What Pro gives you", ProWelcomeCopy.collectionTitle(ProWelcomeMode.REPLAY))
    }

    @Test fun online_status_never_overclaims() {
        // Confirmed: the plan's own line says it all.
        assertNull(ProWelcomeCopy.onlineStatus(OnlineStatus.CONFIRMED))
        assertTrue(ProWelcomeCopy.onlineStatus(OnlineStatus.CONFIRMING)!!.contains("as soon as"))
        assertTrue(ProWelcomeCopy.onlineStatus(OnlineStatus.SIGN_IN_NEEDED)!!.contains("Sign in"))
        assertNull(ProWelcomeCopy.onlineStatus(OnlineStatus.NOT_IN_PLAN))
        assertTrue(ProWelcomeCopy.onlineStatus(OnlineStatus.NOT_IN_THIS_BUILD)!!.contains("aren’t available"))
    }

    @Test fun counts_and_progress_read_naturally() {
        assertEquals("1 of 8", ProWelcomeCopy.progress(0, 8))
        assertEquals("8 of 8", ProWelcomeCopy.progress(7, 8))
        // A number stays with its noun ("holds 6 / perks" split at 200 % text: critic round 3).
        assertEquals("Your Pro deck holds 10\u00A0perks.", ProWelcomeCopy.revealBody(10))
        assertEquals("Your Pro deck holds 1\u00A0perk.", ProWelcomeCopy.revealBody(1))
        assertEquals("Your Pro collection\u00A0· 6\u00A0perks", ProWelcomeCopy.collectionEyebrow(6))
        // The band breaks only after its dot: "Lucento Pro ·" / "10 perks".
        assertEquals("Lucento\u00A0Pro\u00A0· 10\u00A0perks", ProWelcomeCopy.band(10))
        assertEquals(1, ProWelcomeCopy.band(10).count { it == ' ' })
    }

    @Test fun at_large_text_the_collection_title_is_short_enough_not_to_break() {
        assertEquals("You’re Pro", ProWelcomeCopy.collectionTitle(ProWelcomeMode.FIRST, compact = true))
        // A welcome back and a replay are short already.
        assertEquals("Welcome back to Pro", ProWelcomeCopy.collectionTitle(ProWelcomeMode.FIRST, restored = true, compact = true))
        assertEquals("What Pro gives you", ProWelcomeCopy.collectionTitle(ProWelcomeMode.REPLAY, compact = true))
    }

    @Test fun a_card_says_each_thing_once() {
        // Critic round 3: "save or share" twice on the statements card; the three theme names twice on the themes card
        // (the swatches already name them).
        val statements = ProWelcomeCopy.stop(ProTourStop.STATEMENTS, all, signedIn = true, aiAnswersOn = false)
        assertEquals(1, Regex("save or share", RegexOption.IGNORE_CASE).findAll(statements.body + " " + statements.where).count())
        val themes = ProWelcomeCopy.stop(ProTourStop.THEMES, all, signedIn = true, aiAnswersOn = false)
        for (name in listOf("Grove", "Mono", "Aurora")) assertFalse(name, (themes.body + " " + themes.where).contains(name))
        assertEquals("Choose 6 or 12 months above the charts in Insights.", ProWelcomeCopy.stop(ProTourStop.INSIGHTS_PERIODS, all, true, false).where)
    }

    @Test fun an_online_service_says_where_it_stands_before_its_numbers() {
        // Critic round 4: at large text "50 a day" showed while "sign in" and "turn it on" sat under "More".
        for (stop in serviceCards) {
            val signedOut = ProWelcomeCopy.stop(stop, all, signedIn = false, aiAnswersOn = false, online = OnlineStatus.SIGN_IN_NEEDED)
            assertEquals(stop.name, ProWelcomeCopy.ServiceState.SIGN_IN, signedOut.status)
            val waiting = ProWelcomeCopy.stop(stop, all, signedIn = true, aiAnswersOn = false, online = OnlineStatus.CONFIRMING)
            assertEquals(stop.name, ProWelcomeCopy.ServiceState.WAITING, waiting.status)
        }
        // Confirmed: the two opt-ins say whether they are on; sync and Lucento Shared have no switch of their own here.
        assertEquals(ProWelcomeCopy.ServiceState.OFF, ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, true, aiAnswersOn = false).status)
        assertEquals(ProWelcomeCopy.ServiceState.ON, ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, true, aiAnswersOn = true).status)
        assertEquals(ProWelcomeCopy.ServiceState.OFF, ProWelcomeCopy.stop(ProTourStop.SMART_READING, all, true, false, smartReadingOn = false).status)
        assertEquals(ProWelcomeCopy.ServiceState.ON, ProWelcomeCopy.stop(ProTourStop.SMART_READING, all, true, false, smartReadingOn = true).status)
        assertNull(ProWelcomeCopy.stop(ProTourStop.SYNC, all, true, false).status)
        assertNull(ProWelcomeCopy.stop(ProTourStop.SHARED, all, true, false).status)
        // What runs on the phone has nothing to wait for.
        for (stop in ProTourStop.entries - serviceCards.toSet()) {
            for (online in OnlineStatus.entries) assertNull(stop.name, ProWelcomeCopy.stop(stop, all, true, false, online = online).status)
        }
    }

    @Test fun the_ai_card_states_the_allowance_only_once_the_server_has_confirmed_it() {
        for (online in listOf(OnlineStatus.SIGN_IN_NEEDED, OnlineStatus.CONFIRMING)) {
            val term = ProWelcomeCopy.stop(ProTourStop.AI_ANSWERS, all, signedIn = false, aiAnswersOn = false, online = online, term = year).term
            assertEquals(online.name, ProWelcomeCopy.serviceTermText(year), term)
        }
    }

    @Test fun menu_paths_and_counts_keep_their_words_together() {
        // Critic round 4: "Account / & sync", "10 a / month", "side by / side".
        val sync = ProWelcomeCopy.stop(ProTourStop.SYNC, all, signedIn = false, aiAnswersOn = false)
        assertTrue(sync.body.contains("Settings\u00A0› Account\u00A0&\u00A0sync"))
        assertTrue(ProWelcomeCopy.stop(ProTourStop.GOALS, all, true, false).body.contains("side\u00A0by\u00A0side"))
        assertTrue(ProWelcomeCopy.stop(ProTourStop.SHARED, all, true, false).body.startsWith("Invite one Partner"))
    }

    @Test fun sync_and_shared_have_their_own_chapter() {
        // Not "Make it yours": they reach other phones (critic round 3).
        assertEquals("Beyond this phone", ProWelcomeCopy.stop(ProTourStop.SYNC, all, true, false).chapter)
        assertEquals("Beyond this phone", ProWelcomeCopy.stop(ProTourStop.SHARED, all, true, false).chapter)
        assertEquals("Make it yours", ProWelcomeCopy.stop(ProTourStop.THEMES, all, true, false).chapter)
    }
}
