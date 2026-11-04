package com.lucento.app.feature.home

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Owner, 2026-11-04: the sync chip says how fresh it is and what's waiting (never a bare "Synced"), and the dashboard's
 * hidden amounts show nothing about the figure.
 */
class SyncChipAndPrivacyTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test fun `Synced says how long ago`() {
        fun synced(ago: Long) = syncChipLabel(CloudStatus.SYNCED, SyncDetails(lastSyncedAtMillis = now - ago), now)
        assertEquals("Synced · now", synced(20_000))
        assertEquals("Synced · 5m", synced(5 * minute))
        assertEquals("Synced · 3h", synced(3 * 60 * minute))
        assertEquals("Synced · 2d", synced(2 * 24 * 60 * minute))
        assertEquals("a clock that moved back is not negative", "Synced · now", synced(-5 * minute))
    }

    @Test fun `Waiting counts the changes, and a phone that never synced says so`() {
        assertEquals("1 to sync", syncChipLabel(CloudStatus.PENDING, SyncDetails(lastSyncedAtMillis = now, pendingChanges = 1), now))
        assertEquals("4 to sync", syncChipLabel(CloudStatus.PENDING, SyncDetails(lastSyncedAtMillis = now, pendingChanges = 4), now))
        assertEquals("Not synced yet", syncChipLabel(CloudStatus.PENDING, SyncDetails(pendingChanges = 4), now))
        assertEquals("Syncing", syncChipLabel(CloudStatus.SYNCING, SyncDetails(), now))
        assertEquals("On device", syncChipLabel(CloudStatus.LOCAL, SyncDetails(), now))
    }

    @Test fun `TalkBack hears the same in full words`() {
        assertEquals(
            "Sync status: Synced 5 min ago",
            syncChipDescription(CloudStatus.SYNCED, SyncDetails(lastSyncedAtMillis = now - 5 * minute), now),
        )
        assertEquals(
            "Sync status: 2 changes waiting to sync",
            syncChipDescription(CloudStatus.PENDING, SyncDetails(lastSyncedAtMillis = now, pendingChanges = 2), now),
        )
    }

    @Test fun `the sheet says when syncs happen, so Synced is never mistaken for live`() {
        val synced = syncSheetCopy(CloudStatus.SYNCED, SyncDetails(lastSyncedAtMillis = now - 2 * minute), now)
        assertTrue(synced.body.startsWith("Synced 2 min ago, nothing waiting."))
        assertTrue(synced.body.contains("when you open it and a few seconds after each change"))
        assertTrue(synced.body.contains("about every 30 minutes"))
        val pending = syncSheetCopy(CloudStatus.PENDING, SyncDetails(lastSyncedAtMillis = now, pendingChanges = 3), now)
        assertTrue(pending.body.startsWith("3 changes waiting to sync."))
    }

    @Test fun `amounts in an answer are hidden, dates counts and percentages are not`() {
        assertEquals("You spent ₱•••• on food", MoneyFormatter.maskAmountsIn("You spent ₱1,234.50 on food"))
        assertEquals("PHP•••• out, PHP•••• in", MoneyFormatter.maskAmountsIn("PHP6,567.00 out, PHP35,000.00 in"))
        assertEquals("−$•••• over", MoneyFormatter.maskAmountsIn("−$40.00 over"))
        assertEquals("•••• € left", MoneyFormatter.maskAmountsIn("1.234,56 € left"))
        assertEquals("•••• USD", MoneyFormatter.maskAmountsIn("1,500 USD"))
        val plain = "Your top 5 categories in November 2026 used 125% of the budget on 3 days."
        assertEquals(plain, MoneyFormatter.maskAmountsIn(plain))
    }

    @Test fun `a hidden amount keeps the currency and nothing else`() {
        assertEquals("$••••", MoneyFormatter.masked("USD", Locale.US))
        val yen = MoneyFormatter.masked("JPY", Locale.JAPAN)
        assertTrue(yen, yen.endsWith("••••") && yen.length == 5) // "¥" or the full-width "￥", then the dots
        val euro = MoneyFormatter.masked("EUR", Locale.GERMANY)
        assertTrue(euro, euro.startsWith("••••") && euro.endsWith("€"))
        val peso = MoneyFormatter.masked("PHP", Locale.forLanguageTag("en-PH"))
        assertTrue(peso, peso.endsWith("••••"))
        assertFalse(peso.any { it.isDigit() })
        // The same for every amount: size, sign and decimals don't show.
        assertEquals(MoneyFormatter.masked("BHD", Locale.US), MoneyFormatter.masked("BHD", Locale.US))
        assertFalse(MoneyFormatter.masked("BHD", Locale.US).any { it.isDigit() })
        assertTrue(Money(-123_456, "USD").let { MoneyFormatter.masked(it.currencyCode, Locale.US) } == "$••••")
    }
}
