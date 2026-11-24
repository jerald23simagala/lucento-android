package com.lucento.app.domain.entitlement

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tester round 2026-11-18, part C: a new wallet's plan check, asked when a type or currency is chosen (and by the guided
 * setup to hide its add options), is WalletRepository's own rule, so the screen and the save never disagree.
 */
class WalletAccessTest {

    private val free = PlanCatalog.FREE_CAPABILITIES
    private val pro = PlanCatalog.PRO_CAPABILITIES

    private var nextId = 1L
    private fun wallet(type: WalletType, currency: String = "PHP") = Wallet(nextId++, "W$nextId", type, Money(0, currency), 0)

    private val threeMoney = listOf(wallet(WalletType.CASH), wallet(WalletType.BANK), wallet(WalletType.SAVINGS))
    private val twoDebts = listOf(wallet(WalletType.CREDIT_CARD), wallet(WalletType.LOAN))

    @Test fun `money wallets and cards or loans are counted apart`() {
        assertEquals(ProFeature.MONEY_WALLETS, ProGates.walletFeature(WalletType.INVESTMENT))
        assertEquals(ProFeature.DEBT_ACCOUNTS, ProGates.walletFeature(WalletType.CREDIT_CARD))
        assertEquals(ProFeature.RECEIVABLES, ProGates.walletFeature(WalletType.RECEIVABLE))
        assertEquals(ProFeature.VALUED_ASSETS, ProGates.walletFeature(WalletType.OTHER_ASSET))

        // Three money wallets fill the free plan's money count but not its cards and loans.
        val access = ProGates.walletAccess(WalletType.E_WALLET, "PHP", null, "PHP", threeMoney, free)
        assertTrue(access is Access.LimitReached)
        assertEquals(3, (access as Access.LimitReached).used)
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.LOAN, "PHP", null, "PHP", threeMoney, free))
    }

    @Test fun `Pro-only kinds and other currencies are locked on the free plan, open on Pro`() {
        assertTrue(ProGates.walletAccess(WalletType.RECEIVABLE, "PHP", null, "PHP", emptyList(), free) is Access.Locked)
        assertTrue(ProGates.walletAccess(WalletType.BANK, "USD", null, "PHP", emptyList(), free) is Access.Locked)
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.RECEIVABLE, "PHP", null, "PHP", threeMoney, pro))
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.BANK, "USD", null, "PHP", threeMoney + twoDebts, pro))
    }

    @Test fun `an existing wallet is checked when it changes kind or moves into another currency, as the repository does`() {
        val bank = threeMoney[1]
        // Renaming, or retyping within its kind: never a new count.
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.E_WALLET, "PHP", bank, "PHP", threeMoney, free))
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.LOAN, "PHP", twoDebts[0], "PHP", threeMoney + twoDebts, free))
        // Into another kind: counted as a new one of it. Cash into a receivable (Pro), a third card or loan.
        assertTrue(ProGates.walletAccess(WalletType.RECEIVABLE, "PHP", bank, "PHP", threeMoney, free) is Access.Locked)
        val thirdDebt = ProGates.walletAccess(WalletType.CREDIT_CARD, "PHP", bank, "PHP", threeMoney + twoDebts, free)
        assertEquals(2, (thirdDebt as Access.LimitReached).used)
        // A receivable kept from Pro, turned into a fourth money wallet.
        val kept = wallet(WalletType.RECEIVABLE)
        assertTrue(ProGates.walletAccess(WalletType.CASH, "PHP", kept, "PHP", threeMoney + kept, free) is Access.LimitReached)
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.RECEIVABLE, "PHP", kept, "PHP", threeMoney + kept, free))
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.RECEIVABLE, "PHP", bank, "PHP", threeMoney, pro))
        // Into another currency: the foreign-wallet rule.
        assertTrue(ProGates.walletAccess(WalletType.BANK, "USD", bank, "PHP", threeMoney, free) is Access.Locked)
        // Already in another currency (kept from before a downgrade): editing it stays allowed.
        val dollars = wallet(WalletType.BANK, "USD")
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.BANK, "USD", dollars, "PHP", threeMoney + dollars, free))
    }

    @Test fun `without a main currency there is no currency check`() {
        assertEquals(Access.Allowed, ProGates.walletAccess(WalletType.BANK, "USD", null, null, emptyList(), free))
    }

    @Test fun `the first type a new wallet can be, or none`() {
        val order = ProGates.NEW_WALLET_ORDER
        assertEquals(WalletType.CASH, ProGates.firstAddableWalletType(order, "PHP", "PHP", emptyList(), free))
        // Money full: a card is the first that fits.
        assertEquals(WalletType.CREDIT_CARD, ProGates.firstAddableWalletType(order, "PHP", "PHP", threeMoney, free))
        // Everything full on the free plan (receivables and owned things are Pro): nothing fits, so Add opens the dialog.
        assertNull(ProGates.firstAddableWalletType(order, "PHP", "PHP", threeMoney + twoDebts, free))
        // The guided setup's cards-and-loans step at two.
        assertNull(ProGates.firstAddableWalletType(listOf(WalletType.CREDIT_CARD, WalletType.LOAN), "PHP", "PHP", twoDebts, free))
        assertEquals(WalletType.CASH, ProGates.firstAddableWalletType(order, "PHP", "PHP", threeMoney + twoDebts, pro))
    }

    @Test fun `Add wallet starts on Cash, on the type sheet when money wallets are full, or at the wall`() {
        assertEquals(ProGates.AddWalletStart.Editor(WalletType.CASH), ProGates.addWalletStart("PHP", emptyList(), free))
        // Money full, a card or loan still fits: the person picks, never a form that silently starts as a card.
        assertEquals(ProGates.AddWalletStart.ChooseType, ProGates.addWalletStart("PHP", threeMoney, free))
        val wall = ProGates.addWalletStart("PHP", threeMoney + twoDebts, free)
        assertTrue(wall is ProGates.AddWalletStart.Wall && wall.access is Access.LimitReached && wall.alsoFull is Access.LimitReached)
        assertEquals(ProGates.AddWalletStart.Editor(WalletType.CASH), ProGates.addWalletStart("PHP", threeMoney + twoDebts, pro))
    }
}
