package com.lucento.app.feature.pro

import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.Capability
import com.lucento.app.domain.entitlement.DisplayPlan
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.ProFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tester round 2026-11-18, part C: the one Pro dialog says what the Free plan includes, and appears only where Pro helps. */
class ProWallTest {

    @Test fun `a count Pro lifts is a wall with its numbers, the most any plan allows is not`() {
        val wallets = ProWall.of(
            Access.LimitReached(ProFeature.MONEY_WALLETS, used = 3, limit = Limit.Count(3), withUpgrade = Limit.Unlimited),
        )
        assertEquals(ProWall(ProFeature.MONEY_WALLETS, 3, Limit.Count(3)), wallets)
        // Twenty goals is the most on any plan: no dialog that could only offer what doesn't help.
        val proGoals = PlanCatalog.limit(ProFeature.SAVINGS_GOALS, PlanCatalog.PRO_CAPABILITIES)
        assertNull(ProWall.of(Access.LimitReached(ProFeature.SAVINGS_GOALS, 20, proGoals, proGoals)))
        assertNull(ProWall.of(Access.Allowed))
        assertEquals(ProWall(ProFeature.PDF_STATEMENTS), ProWall.of(Access.Locked(ProFeature.PDF_STATEMENTS, Capability.LOCAL_PRO)))
    }

    @Test fun `titles say what the Free plan includes, as the owner wrote it`() {
        assertEquals("The Free plan includes 3 money wallets", ProCopy.wallTitle(ProWall(ProFeature.MONEY_WALLETS, 3, Limit.Count(3))))
        assertEquals("The Free plan includes 2 cards or loans", ProCopy.wallTitle(ProWall(ProFeature.DEBT_ACCOUNTS)))
        assertEquals("The Free plan includes 2 active goals", ProCopy.wallTitle(ProWall(ProFeature.SAVINGS_GOALS, 2, Limit.Count(2))))
        assertEquals("The Free plan includes 10 AI questions a month", ProCopy.wallTitle(ProWall(ProFeature.AI_ADVISER)))
        assertEquals("Grove is part of Lucento Pro", ProCopy.wallTitle(ProWall(ProFeature.EXTRA_THEMES, subject = "Grove")))
    }

    @Test fun `with every kind full, one dialog names both counts`() {
        val both = ProWall(
            ProFeature.MONEY_WALLETS, 3, Limit.Count(3),
            alsoFull = ProWall(ProFeature.DEBT_ACCOUNTS, 2, Limit.Count(2)),
        )
        assertEquals("The Free plan includes 3 money wallets and 2 cards or loans", ProCopy.wallTitle(both))
        assertTrue(ProCopy.wallBody(both).startsWith("You have 3 money wallets and 2 cards or loans. "))
    }

    @Test fun `bodies never offer what the person can't do, or count investments as things you own`() {
        val goals = ProCopy.wallBody(ProWall(ProFeature.SAVINGS_GOALS, 2, Limit.Count(2)))
        assertTrue(goals, goals.contains("When a goal is reached, archiving it frees a slot."))
        assertFalse(ProCopy.wallBody(ProWall(ProFeature.VALUED_ASSETS)).contains("investments"))
    }

    @Test fun `the preview and the try-on say what they show and that nothing is saved`() {
        assertTrue(ProCopy.longPeriodPreviewBody(6, hasData = true).startsWith("This is the shape of your last 6 months, with the figures hidden. "))
        // Nothing recorded: no picture, so no sentence about one.
        assertFalse(ProCopy.longPeriodPreviewBody(12, hasData = false).contains("shape"))
        assertTrue(ProCopy.longPeriodPreviewBody(12, hasData = false).endsWith("Your last 3 months are always free."))
        assertTrue(ProCopy.themeTrialBody("Sunrise").contains("nothing is saved: Done, or closing this sheet, goes back to Sunrise."))
        assertEquals("Unlock", ProCopy.WALL_UNLOCK)
    }

    @Test fun `every feature has a title and a body`() {
        ProFeature.entries.forEach { feature ->
            val wall = ProWall(feature)
            assertTrue(feature.name, ProCopy.wallTitle(wall).isNotBlank())
            assertTrue(feature.name, ProCopy.wallBody(wall).isNotBlank())
        }
    }

    @Test fun `a count's body gives the person's own number and that nothing existing changes`() {
        val body = ProCopy.wallBody(ProWall(ProFeature.DEBT_ACCOUNTS, 2, Limit.Count(2)))
        assertTrue(body, body.startsWith("You have 2. "))
        assertTrue(body, body.contains("carry on as usual"))
        // Kept from Pro after a downgrade: the body says how many there are, not the limit.
        assertTrue(ProCopy.wallBody(ProWall(ProFeature.MONEY_WALLETS, 5, Limit.Count(3))).startsWith("You have 5. "))
        // Without a number (a lock, not a count) there is no "You have".
        assertFalse(ProCopy.wallBody(ProWall(ProFeature.AI_ADVISER)).contains("You have"))
    }

    @Test fun `Pro Lifetime without Lucento Cloud hears about Lucento Cloud, not the Free plan`() {
        val lifetime = DisplayPlan.LIFETIME
        assertEquals("Cloud sync comes with Lucento Cloud", ProCopy.wallTitle(ProWall(ProFeature.CLOUD_SYNC), lifetime))
        assertEquals("Inviting a Partner comes with Lucento Cloud", ProCopy.wallTitle(ProWall(ProFeature.SHARED_SPACE), lifetime))
        assertEquals("Pro Lifetime includes 10 AI questions a month", ProCopy.wallTitle(ProWall(ProFeature.AI_ADVISER), lifetime))
        assertTrue(ProCopy.wallBody(ProWall(ProFeature.AI_ADVISER), lifetime).startsWith("Lucento Cloud gives you 50 a day."))
        // Free hears about the plan; what runs on the phone never takes the Cloud words.
        assertEquals("The Free plan includes 10 AI questions a month", ProCopy.wallTitle(ProWall(ProFeature.AI_ADVISER), DisplayPlan.FREE))
        // Lifetime with its services, or Pro, never meets these walls; the words only change for Lifetime without them.
        assertFalse(ProCopy.cloudWall(ProWall(ProFeature.CLOUD_SYNC), DisplayPlan.LIFETIME_WITH_SERVICES))
        assertEquals("Cloud sync is part of Lucento Pro", ProCopy.wallTitle(ProWall(ProFeature.CLOUD_SYNC)))
        assertFalse(ProCopy.cloudWall(ProWall(ProFeature.PDF_STATEMENTS), lifetime))
        listOf(ProFeature.CLOUD_SYNC, ProFeature.CLOUD_RESTORE, ProFeature.SMART_READING, ProFeature.SHARED_SPACE, ProFeature.AI_ADVISER).forEach {
            val wall = ProWall(it)
            val body = ProCopy.wallBody(wall, lifetime)
            assertTrue(it.name, ProCopy.cloudWall(wall, lifetime))
            assertFalse("${it.name}: $body", body.contains(ProCopy.wallTitle(wall, lifetime), ignoreCase = true))
            assertFalse("${it.name}: $body", body.contains("Free plan"))
        }
    }

    @Test fun `the plain sync notes say what the dialog says`() {
        assertTrue(ProCopy.syncLockedNote(paused = true, plan = DisplayPlan.FREE).startsWith("Cloud sync is paused: it’s part of Lucento Pro."))
        assertTrue(ProCopy.syncLockedNote(paused = false, plan = DisplayPlan.FREE).startsWith("Cloud sync is part of Lucento Pro."))
        val lifetime = ProCopy.syncLockedNote(paused = true, plan = DisplayPlan.LIFETIME)
        assertTrue(lifetime, lifetime.contains("it comes with Lucento Cloud") && lifetime.contains("when Lucento Cloud is on"))
        assertFalse(lifetime, lifetime.contains("Lucento Pro"))
        assertTrue(ProCopy.syncLockedNote(paused = false, plan = DisplayPlan.LIFETIME).startsWith("Cloud sync comes with Lucento Cloud."))
    }

    @Test fun `no body repeats its title`() {
        val walls = ProFeature.entries.map { ProWall(it) } +
            ProWall(ProFeature.EXTRA_THEMES, subject = "Grove") +
            ProWall(ProFeature.SAVINGS_GOALS, 2, Limit.Count(2))
        walls.forEach { wall ->
            val title = ProCopy.wallTitle(wall)
            val body = ProCopy.wallBody(wall)
            assertFalse("${wall.feature}: $body", body.contains(title, ignoreCase = true))
            assertFalse("${wall.feature}: $body", body.contains("is part of Lucento Pro"))
        }
    }
}
