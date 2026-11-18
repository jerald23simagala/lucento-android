package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The account picker's sections and recent accounts (tester round, 2026-11-17). */
class AccountChoicesTest {

    private fun wallet(id: Long, name: String, type: WalletType, currency: String = "PHP") =
        Wallet(id, name, type, Money(10_000, currency), createdAtEpochMillis = id)

    private val cash = wallet(1, "Cash", WalletType.CASH)
    private val bpi = wallet(2, "BPI Payroll", WalletType.BANK)
    private val gcash = wallet(3, "GCash", WalletType.E_WALLET)
    private val emergency = wallet(4, "Emergency fund", WalletType.SAVINGS)
    private val visa = wallet(5, "Gold Visa", WalletType.CREDIT_CARD)
    private val maya = wallet(6, "Maya", WalletType.E_WALLET)
    private val bdo = wallet(7, "BDO Savings", WalletType.BANK)
    private val all = listOf(visa, maya, cash, bpi, emergency, gcash, bdo)

    private val labels: (WalletType) -> String = { it.name.lowercase().replace('_', ' ') }

    private var nextId = 100L

    private fun transfer(from: Wallet, to: Wallet, date: String): Transfer {
        val day = LocalDate.parse(date)
        val group = "g${nextId}"
        val out = Transaction(nextId++, TransactionType.TRANSFER_OUT, Money(1_000, "PHP"), from.id, null, day, "", 0, group)
        val inn = Transaction(nextId++, TransactionType.TRANSFER_IN, Money(1_000, "PHP"), to.id, null, day, "", 0, group)
        return Transfer(group, out, inn)
    }

    @Test
    fun `accounts are grouped into the four families, in type order, keeping their order inside a type`() {
        val sections = AccountChoices.sections(all, emptyList(), "", labels)
        assertEquals(listOf(WalletFamily.EVERYDAY, WalletFamily.SET_ASIDE, WalletFamily.OWED), sections.map { it.family })
        assertEquals(listOf(cash, bpi, bdo, maya, gcash), sections.first().wallets)
        assertEquals(listOf(emergency), sections[1].wallets)
        assertEquals(all.size, sections.sumOf { it.wallets.size })
    }

    @Test
    fun `every type has a family`() {
        // The pickers list families only, so a type without one would vanish from them.
        assertEquals(WalletType.entries.toSet(), WalletType.entries.filter { it.family in WalletFamily.entries }.toSet())
        assertEquals(WalletFamily.OWNED, WalletType.RECEIVABLE.family)
        assertEquals(WalletFamily.OWED, WalletType.LOAN.family)
    }

    @Test
    fun `recent accounts lead a long list, newest first, at most three, only ones that are listed`() {
        val sections = AccountChoices.sections(all, listOf(4, 99, 4, 2, 3, 1), "", labels)
        assertEquals(null, sections.first().family)
        assertEquals(listOf(emergency, bpi, gcash), sections.first().wallets)
        // They still appear in their own family as well.
        assertTrue(sections.first { it.family == WalletFamily.SET_ASIDE }.wallets.contains(emergency))
    }

    @Test
    fun `a short list has no Recent, since it is quick to scan and repeats would only lengthen it`() {
        val six = listOf(cash, bpi, gcash, emergency, visa, maya)
        assertEquals(AccountChoices.SEARCH_FROM, six.size)
        val sections = AccountChoices.sections(six, listOf(4, 2), "", labels)
        assertTrue(sections.none { it.family == null })
        assertEquals(six.size, sections.sumOf { it.wallets.size })
    }

    @Test
    fun `a search matches the name, the kind or the bank, ignoring case and accents, without Recent`() {
        val cafe = wallet(8, "Café fund", WalletType.SAVINGS)
        val withCafe = all + cafe
        assertEquals(listOf(cafe), AccountChoices.sections(withCafe, listOf(1), "CAFE", labels).flatMap { it.wallets })
        assertEquals(listOf(maya, gcash), AccountChoices.sections(withCafe, listOf(1), "e wallet", labels).flatMap { it.wallets })
        val bank: (Wallet) -> String? = { if (it.id == 2L) "Bank of the Philippine Islands" else null }
        assertEquals(listOf(bpi), AccountChoices.sections(withCafe, emptyList(), "philippine", labels, bank).flatMap { it.wallets })
        assertTrue(AccountChoices.sections(withCafe, listOf(1), "zzz", labels).isEmpty())
    }

    @Test
    fun `recent destinations follow the source, newest first, without repeats or the source itself`() {
        val transfers = listOf(
            transfer(bpi, emergency, "2026-10-30"),
            transfer(bpi, gcash, "2026-11-02"),
            transfer(cash, maya, "2026-11-10"),
            transfer(bpi, emergency, "2026-11-15"),
            transfer(bpi, visa, "2026-11-15"),
        )
        // Same day: the one saved later is newer.
        assertEquals(listOf(5L, 4L, 3L), AccountChoices.recentDestinations(transfers, fromId = 2))
        assertEquals(listOf(6L), AccountChoices.recentDestinations(transfers, fromId = 1))
        assertEquals(listOf(5L, 4L, 6L), AccountChoices.recentDestinations(transfers, fromId = null))
        assertEquals(listOf(2L, 1L), AccountChoices.recentSources(transfers))
        assertTrue(AccountChoices.recentDestinations(emptyList(), fromId = 2).isEmpty())
    }
}
