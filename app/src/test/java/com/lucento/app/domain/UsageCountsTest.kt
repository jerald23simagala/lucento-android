package com.lucento.app.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCountsTest {
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour

    @Test fun theAllowlistIsTheTwelveSpecEvents() {
        assertEquals(
            listOf(
                "onboarding_started",
                "onboarding_step_viewed",
                "first_entry_saved",
                "d1_return",
                "d7_return",
                "first_week_card_step_done",
                "budget_suggestion_shown",
                "budget_suggestion_accepted",
                "pro_dialog_shown",
                "plan_page_viewed",
                "purchase_started",
                "purchase_completed",
            ),
            UsageCounts.EVENTS,
        )
        assertEquals(12, UsageCounts.EVENTS.distinct().size)
        assertTrue(UsageCounts.EVENTS.all { it.length <= 40 })
    }

    /**
     * The app's list and the server table's own check must name the same events: a name only the app knows is refused
     * by the server (and was, before CRO-2, stuck at the front of the queue).
     */
    @Test fun theAllowlistMatchesTheLatestServerMigration() {
        val dir = listOf(java.io.File("../supabase/migrations"), java.io.File("supabase/migrations")).firstOrNull { it.isDirectory }
        org.junit.Assume.assumeTrue("the repo's supabase/migrations isn't reachable from here", dir != null)
        val latest = dir!!.listFiles { f -> f.name.endsWith(".sql") }!!.sortedBy { it.name }
            .map { it.readText() }
            .last { it.contains("constraint usage_events_event_allowlist check") }
        val check = latest.substringAfter("constraint usage_events_event_allowlist check").substringBefore(");")
        val names = Regex("'([a-z0-9_]+)'").findAll(check).map { it.groupValues[1] }.toList()
        assertEquals(UsageCounts.EVENTS.sorted(), names.sorted())
    }

    @Test fun propsDropAnythingOutsideTheAllowlist() {
        val kept = UsageCounts.props(mapOf("step" to "1", "amount" to "12.50", "note" to "coffee", "ms" to "40"))
        assertEquals(mapOf("step" to "1", "ms" to "40"), kept)
        val pro = UsageCounts.props(mapOf("feature" to "money_wallets", "from" to "wall", "plan" to "annual", "price" to "1190"))
        assertEquals(mapOf("feature" to "money_wallets", "from" to "wall", "plan" to "annual"), pro)
    }

    @Test fun onlyDataAndConstraintErrorsAreRefusals() {
        assertTrue(UsageCounts.isRefusal("23514"))   // name outside the allowlist
        assertTrue(UsageCounts.isRefusal("22023"))   // the reserved install id
        assertFalse(UsageCounts.isRefusal(null))      // no answer at all: try again later
        assertFalse(UsageCounts.isRefusal("42501"))   // permissions: a server problem, not this count's
        assertFalse(UsageCounts.isRefusal("PGRST202"))
        assertFalse(UsageCounts.isRefusal("2"))
    }

    @Test fun aRefusedCountIsDroppedAndTheOnesBehindItStillSend() = runBlocking {
        val sent = mutableListOf<String>()
        var offline = false
        val store = Store()
        val service = UsageCountsService(
            load = { store.ledger },
            save = { store.ledger = it },
            send = UsageEventSender { _, event, _ ->
                if (offline) throw IllegalStateException("offline")
                if (event.name == UsageCounts.PURCHASE_STARTED) throw UsageEventRefused("23514")  // an older server
                sent += event.name
            },
            deleteRemote = UsageEventDeleter { },
            newInstallId = { "install" },
            now = { 10 },
            appVersion = "1.0",
            launchInBackground = { block -> runBlocking { block() } },
        )
        service.setOptIn(true)
        offline = true
        service.record(UsageCounts.PURCHASE_STARTED, mapOf("plan" to "monthly"))
        service.record(UsageCounts.PLAN_PAGE_VIEWED, mapOf("from" to "settings"))
        assertEquals("no network keeps both", 2, store.ledger.queue.size)
        offline = false
        service.record(UsageCounts.D1_RETURN, mapOf("day_index" to "1"))
        assertEquals(listOf(UsageCounts.PLAN_PAGE_VIEWED, UsageCounts.D1_RETURN), sent)
        assertTrue("the refused one doesn't block the queue", store.ledger.queue.isEmpty())
    }

    @Test fun conversionPropsNameTheDoorAndThePlan() {
        assertEquals(mapOf("from" to "settings"), ConversionCounts.planPageProps("settings", null))
        assertEquals(mapOf("from" to "plan_ended"), ConversionCounts.planPageProps("plan_ended", null))
        assertEquals(mapOf("from" to "onboarding", "feature" to "cloud_restore"), ConversionCounts.planPageProps("onboarding", "CLOUD_RESTORE"))
        assertEquals(mapOf("from" to "wall", "feature" to "extra_themes"), ConversionCounts.planPageProps("", "EXTRA_THEMES"))
        assertEquals(mapOf("from" to "other"), ConversionCounts.planPageProps("", null))
        assertEquals(mapOf("from" to "other"), ConversionCounts.planPageProps("made-up door", null))

        val sub = com.lucento.app.domain.entitlement.PlayProducts.PRO_SUBSCRIPTION
        assertEquals("monthly", ConversionCounts.plan(sub, "P1M", withTrial = false))
        assertEquals("annual", ConversionCounts.plan(sub, "P1Y", withTrial = false))
        assertEquals("annual_trial", ConversionCounts.plan(sub, "P1Y", withTrial = true))
        assertEquals("lifetime", ConversionCounts.plan(com.lucento.app.domain.entitlement.PlayProducts.PRO_LIFETIME, null, withTrial = false))
        assertEquals("cloud_annual", ConversionCounts.plan(com.lucento.app.domain.entitlement.PlayProducts.CLOUD_ADDON, "P1Y", withTrial = false))
        assertEquals("other", ConversionCounts.plan("something_else", "P1M", withTrial = false))
        // Every value fits a prop and survives the on-phone queue's format.
        val values = listOf("monthly", "annual", "annual_trial", "lifetime", "cloud_monthly", "cloud_annual", "other") +
            com.lucento.app.domain.entitlement.ProFeature.entries.map { ConversionCounts.feature(it.name) }
        assertTrue(values.all { it.length <= UsageCounts.MAX_PROP_LENGTH && Regex("[a-z0-9_]+").matches(it) })
    }

    @Test fun optingInOnWelcomeQueuesTheStartEventAndOptingOutClearsIt() {
        val seen = UsageLedgerLogic.noteWelcome(UsageLedger(), now = 1_000)
        assertTrue(seen.onboardingSeen)
        assertTrue(UsageLedgerLogic.drain(seen.copy(queue = listOf(UsageEvent("onboarding_started")))).isEmpty())
        val opted = UsageLedgerLogic.optIn(seen, now = 2_000, installId = "install-a")
        assertEquals("install-a", opted.installId)
        assertEquals(listOf("onboarding_started"), opted.queue.map { it.name })
        assertEquals(opted.queue, UsageLedgerLogic.drain(opted))
        val out = UsageLedgerLogic.afterOptOut(opted, deleted = true)
        assertFalse(out.optedIn)
        assertNull(out.installId)
        assertTrue(out.queue.isEmpty())
        assertTrue(UsageLedgerLogic.drain(out).isEmpty())
        val again = UsageLedgerLogic.optIn(out.copy(onboardingSeen = true), now = 3_000, installId = "install-b")
        assertEquals("install-b", again.installId)
    }

    @Test fun eventsAreNotQueuedUntilThePersonOptsIn() {
        val ledger = UsageLedger(onboardingSeen = true, installedAtMillis = 0)
        val stepped = UsageLedgerLogic.stepViewed(ledger, step = 2, now = 50)
        assertTrue(stepped.queue.isEmpty())
        val on = ledger.copy(optedIn = true, installId = "id")
        val viewed = UsageLedgerLogic.stepViewed(on, step = 2, now = 80)
        assertEquals("onboarding_step_viewed", viewed.queue.single().name)
        assertEquals("2", viewed.queue.single().props["step"])
        assertEquals("80", viewed.queue.single().props["ms"])
    }

    @Test fun returnEventsFireOnceInsideTheirWindows() {
        val sent = emptySet<String>()
        assertTrue(UsageLedgerLogic.returnsDue(0, 17 * hour, sent).isEmpty())
        assertEquals(listOf("d1_return"), UsageLedgerLogic.returnsDue(0, 18 * hour, sent).map { it.name })
        assertEquals(listOf("d1_return"), UsageLedgerLogic.returnsDue(0, 48 * hour, sent).map { it.name })
        assertTrue(UsageLedgerLogic.returnsDue(0, 49 * hour, sent).none { it.name == "d1_return" })
        assertEquals(listOf("d7_return"), UsageLedgerLogic.returnsDue(0, 6 * day, sent).map { it.name })
        assertEquals(listOf("d7_return"), UsageLedgerLogic.returnsDue(0, 8 * day, sent).map { it.name })
        assertTrue(UsageLedgerLogic.returnsDue(0, 5 * day, sent).isEmpty())
        assertTrue(UsageLedgerLogic.returnsDue(0, 18 * hour, setOf("d1_return")).isEmpty())
    }

    @Test fun theQueueRoundTripsAndDropsUnknownEvents() {
        val encoded = UsageLedgerCodec.encode(
            listOf(UsageEvent("first_entry_saved", mapOf("ms" to "40")), UsageEvent("not_a_real_event")),
        )
        val decoded = UsageLedgerCodec.decode(encoded + "\nnot_a_real_event|step=1")
        assertEquals(listOf(UsageEvent("first_entry_saved", mapOf("ms" to "40"))), decoded)
    }

    @Test fun aFailedUploadKeepsTheRestAndOptingOutNeverSends() = runBlocking {
        val sent = mutableListOf<String>()
        var fail = true
        val store = Store()
        val service = UsageCountsService(
            load = { store.ledger },
            save = { store.ledger = it },
            send = UsageEventSender { _, event, _ ->
                if (fail) throw IllegalStateException("offline")
                sent += event.name
            },
            deleteRemote = UsageEventDeleter { },
            newInstallId = { "install" },
            now = { 10 },
            appVersion = "1.0",
            launchInBackground = { block -> runBlocking { block() } },
        )
        service.noteWelcome()
        service.setOptIn(true)
        assertEquals(listOf("onboarding_started"), store.ledger.queue.map { it.name })
        assertTrue(sent.isEmpty())
        fail = false
        service.record("onboarding_step_viewed", mapOf("step" to "2"))
        assertEquals(listOf("onboarding_started", "onboarding_step_viewed"), sent)
        assertTrue(store.ledger.queue.isEmpty())
        service.record("onboarding_step_viewed", mapOf("step" to "3"))
        service.setOptIn(false)
        assertTrue(store.ledger.queue.isEmpty())
        assertNull(store.ledger.installId)
        assertFalse(sent.contains("should-not-send"))
    }

    @Test fun aFailedOptOutKeepsTheInstallIdForTheNextLaunch() = runBlocking {
        val deleted = mutableListOf<String>()
        var fail = true
        val store = Store(UsageLedger(optedIn = true, installId = "old-id", onboardingSeen = true))
        val service = UsageCountsService(
            load = { store.ledger },
            save = { store.ledger = it },
            send = UsageEventSender { _, _, _ -> },
            deleteRemote = UsageEventDeleter { id ->
                if (fail) throw IllegalStateException("offline")
                deleted += id
            },
            newInstallId = { "new-id" },
            now = { 10 },
            appVersion = "1",
            launchInBackground = { block -> runBlocking { block() } },
        )
        service.setOptIn(false)
        assertEquals("old-id", store.ledger.pendingDeletionInstallId)
        assertNull(store.ledger.installId)
        assertTrue(deleted.isEmpty())
        fail = false
        service.retryPendingDeletion()
        assertEquals(listOf("old-id"), deleted)
        assertNull(store.ledger.pendingDeletionInstallId)
    }

    @Test fun optOutIsSavedBeforeTheDeleteSoAKillKeepsThePendingId() = runBlocking {
        val store = Store(UsageLedger(optedIn = true, installId = "old-id", queue = listOf(UsageEvent("d1_return"))))
        val service = service(store, deleteRemote = { error("should not run") }, launchInBackground = { })
        service.setOptIn(false)
        assertFalse(store.ledger.optedIn)
        assertTrue(store.ledger.queue.isEmpty())
        assertNull(store.ledger.installId)
        assertEquals("old-id", store.ledger.pendingDeletionInstallId)
    }

    @Test fun aSlowDeleteDoesNotBlockSavingTheOptOut() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = Store(UsageLedger(optedIn = true, installId = "old-id", queue = listOf(UsageEvent("d1_return"))))
        lateinit var background: suspend () -> Unit
        val service = service(
            store,
            deleteRemote = {
                started.complete(Unit)
                release.await()
            },
            launchInBackground = { background = it },
        )
        service.setOptIn(false)
        assertFalse(store.ledger.optedIn)
        assertTrue(store.ledger.queue.isEmpty())
        assertEquals("old-id", store.ledger.pendingDeletionInstallId)
        coroutineScope {
            val job = launch { background() }
            started.await()
            assertEquals("old-id", store.ledger.pendingDeletionInstallId)
            release.complete(Unit)
            job.join()
        }
        assertNull(store.ledger.pendingDeletionInstallId)
    }

    private fun service(
        store: Store,
        deleteRemote: suspend (String) -> Unit = {},
        launchInBackground: (suspend () -> Unit) -> Unit = { block -> runBlocking { block() } },
    ) = UsageCountsService(
        load = { store.ledger },
        save = { store.ledger = it },
        send = UsageEventSender { _, _, _ -> },
        deleteRemote = UsageEventDeleter { deleteRemote(it) },
        newInstallId = { "new-id" },
        now = { 10 },
        appVersion = "1",
        launchInBackground = launchInBackground,
    )

    private class Store(var ledger: UsageLedger = UsageLedger())
}
