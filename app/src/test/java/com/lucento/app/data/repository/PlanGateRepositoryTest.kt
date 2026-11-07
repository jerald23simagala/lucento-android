package com.lucento.app.data.repository

import com.lucento.app.core.money.Money
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.EntitlementEvaluator
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.PlanCatalog
import com.lucento.app.domain.entitlement.ProFeature
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repository gates (docs/MONETIZATION.md §5.2, §6.3) against a Free plan: counts are of the right kind, only creation is
 * refused, and everything that exists keeps working. The app itself still wires [EntitlementGate.Permissive] (Phase 1).
 */
class PlanGateRepositoryTest {
    private val walletDao = FakeWalletDao()
    private val transactionDao = FakeTransactionDao()
    private val goalDao = FakeGoalDao()
    private val free = EntitlementGate { feature, used -> EntitlementEvaluator.access(PlanCatalog.FREE_CAPABILITIES, feature, used) }
    private val pro = EntitlementGate { feature, used -> EntitlementEvaluator.access(PlanCatalog.PRO_CAPABILITIES, feature, used) }

    private fun wallets(gate: EntitlementGate) = WalletRepository(walletDao, transactionDao, clock = { 1L }, gate = gate)
    private fun goals(gate: EntitlementGate) = GoalRepository(goalDao, clock = { 1L }, gate = gate)
    private val php = Money(0, "PHP")

    @Test fun free_allows_three_money_wallets_and_refuses_the_fourth_with_the_count() = runTest {
        val repo = wallets(free)
        assertTrue(repo.create("Cash", WalletType.CASH, php) is WalletRepository.SaveResult.Saved)
        assertTrue(repo.create("GCash", WalletType.E_WALLET, php) is WalletRepository.SaveResult.Saved)
        assertTrue(repo.create("BPI", WalletType.BANK, php) is WalletRepository.SaveResult.Saved)
        assertEquals(
            WalletRepository.SaveResult.LimitReached(ProFeature.MONEY_WALLETS, used = 3, limit = Limit.Count(3)),
            repo.create("Savings", WalletType.SAVINGS, php),
        )
        assertEquals(3, walletDao.observeActive().first().size)
    }

    @Test fun debt_accounts_are_counted_apart_from_money_wallets() = runTest {
        val repo = wallets(free)
        repeat(3) { assertTrue(repo.create("Money $it", WalletType.CASH, php) is WalletRepository.SaveResult.Saved) }
        assertTrue("a card still fits", repo.create("Visa", WalletType.CREDIT_CARD, php) is WalletRepository.SaveResult.Saved)
        assertTrue("a loan still fits", repo.create("Car loan", WalletType.LOAN, php) is WalletRepository.SaveResult.Saved)
        assertEquals(
            WalletRepository.SaveResult.LimitReached(ProFeature.DEBT_ACCOUNTS, used = 2, limit = Limit.Count(2)),
            repo.create("Mastercard", WalletType.CREDIT_CARD, php),
        )
    }

    @Test fun receivables_and_valued_assets_are_pro_kinds_on_free() = runTest {
        val repo = wallets(free)
        assertEquals(WalletRepository.SaveResult.ProRequired(ProFeature.RECEIVABLES), repo.create("Owed by Ana", WalletType.RECEIVABLE, php))
        assertEquals(WalletRepository.SaveResult.ProRequired(ProFeature.VALUED_ASSETS), repo.create("Car", WalletType.OTHER_ASSET, php))
        assertEquals(0, walletDao.observeActive().first().size)
    }

    @Test fun pro_has_no_wallet_limits() = runTest {
        val repo = wallets(pro)
        repeat(12) { assertTrue(repo.create("W$it", WalletType.values()[it % WalletType.values().size], php) is WalletRepository.SaveResult.Saved) }
    }

    @Test fun existing_wallets_over_the_limit_keep_working_after_a_downgrade() = runTest {
        val proRepo = wallets(pro)
        val ids = (1..6).map { (proRepo.create("W$it", WalletType.BANK, php) as WalletRepository.SaveResult.Saved).id }
        val freeRepo = wallets(free)
        // Editing, renaming and deleting what exists is never gated.
        assertTrue(freeRepo.update(ids[0], "Renamed", WalletType.BANK, Money(5_000, "PHP")) is WalletRepository.SaveResult.Saved)
        assertEquals(WalletRepository.DeleteResult.Deleted, freeRepo.delete(ids[5]))
        assertEquals(5, walletDao.observeActive().first().size)
        // Only a new one waits.
        assertTrue(freeRepo.create("W7", WalletType.BANK, php) is WalletRepository.SaveResult.LimitReached)
    }

    @Test fun free_allows_two_active_goals_archived_ones_never_count_and_restoring_is_gated() = runTest {
        val repo = goals(free)
        val a = (repo.save(null, "Emergency fund", Money(100, "PHP"), null, null) as GoalRepository.SaveResult.Saved).id
        assertTrue(repo.save(null, "Trip", Money(100, "PHP"), null, null) is GoalRepository.SaveResult.Saved)
        assertEquals(GoalRepository.SaveResult.LimitReached(used = 2, limit = Limit.Count(2)), repo.save(null, "Laptop", Money(100, "PHP"), null, null))

        // Reach and archive one: a slot frees up.
        assertEquals(GoalRepository.ContributionResult.Saved, repo.contribute(a, Money(100, "PHP"), java.time.LocalDate.of(2026, 1, 1), "", "c-1"))
        assertEquals(GoalRepository.ArchiveResult.Archived, repo.archive(a))
        assertTrue(repo.save(null, "Laptop", Money(100, "PHP"), null, null) is GoalRepository.SaveResult.Saved)

        // Bringing the archived one back would make three.
        assertEquals(GoalRepository.RestoreResult.LimitReached(used = 2, limit = Limit.Count(2)), repo.restore(a))
        assertTrue(goalDao.getById(a)!!.archived)
        // Editing an existing goal is never gated.
        val trip = goalDao.observeActive().first().first { it.name == "Trip" }.id
        assertTrue(repo.save(trip, "Trip to Palawan", Money(200, "PHP"), null, null) is GoalRepository.SaveResult.Saved)
    }

    @Test fun pro_allows_twenty_goals_and_the_twenty_first_is_the_most_on_any_plan() = runTest {
        val repo = goals(pro)
        repeat(20) { assertTrue(repo.save(null, "G$it", Money(1, "PHP"), null, null) is GoalRepository.SaveResult.Saved) }
        val result = repo.save(null, "G21", Money(1, "PHP"), null, null)
        assertEquals(GoalRepository.SaveResult.LimitReached(used = 20, limit = Limit.Count(20)), result)
        assertTrue(!(pro.check(ProFeature.SAVINGS_GOALS, 20) as Access.LimitReached).upgradeHelps)
    }

    @Test fun the_permissive_gate_the_app_wires_today_never_refuses() = runTest {
        val repo = wallets(EntitlementGate.Permissive)
        repeat(8) { assertTrue(repo.create("W$it", WalletType.CASH, php) is WalletRepository.SaveResult.Saved) }
        val g = goals(EntitlementGate.Permissive)
        repeat(25) { assertTrue(g.save(null, "G$it", Money(1, "PHP"), null, null) is GoalRepository.SaveResult.Saved) }
    }
}
