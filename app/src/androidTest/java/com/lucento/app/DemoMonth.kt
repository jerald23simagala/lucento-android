package com.lucento.app

import com.lucento.app.core.money.Money
import com.lucento.app.data.repository.GoalRepository
import com.lucento.app.data.repository.RecurringRepository
import com.lucento.app.data.repository.TransactionRepository
import com.lucento.app.data.repository.WalletRepository
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.DebtRepayment
import com.lucento.app.domain.DebtTerms
import com.lucento.app.domain.Frequency
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.WalletType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

/**
 * The demo month the screenshot tour and the website showcase both start from, created through the app's own
 * repositories so it follows every validation rule: an everyday bank, cash, savings, a Visa card with billing days, a
 * car loan three instalments in, three months of salary and spending, nine budget categories, two goals and four
 * schedules. [scale] multiplies every amount (×10 for the website's peso figures; ×1 in CI).
 */
object DemoMonth {
    suspend fun seed(container: AppContainer, currency: String, scale: Long, today: LocalDate) {
        fun money(major: Long, cents: Long = 0) = Money((major * 100 + cents) * scale, currency)
        val day = { d: Int -> today.withDayOfMonth(minOf(d, today.dayOfMonth)) }
        // Part B onboarding switches on only what the focus step chose; the tour visits every module.
        com.lucento.app.domain.Feature.entries.forEach { container.preferences.setFeatureEnabled(it, true) }
        val wallets = container.wallets
        fun id(r: WalletRepository.SaveResult) = (r as WalletRepository.SaveResult.Saved).id
        val bank = id(wallets.create("Everyday bank", WalletType.BANK, money(3_250)))
        val cash = id(wallets.create("Cash", WalletType.CASH, money(1_200)))
        val savings = id(wallets.create("Savings", WalletType.SAVINGS, money(8_400)))
        // A card with a limit and billing days: its statement closed ten days ago and its payment is due in five, so
        // Home lists the payment and the card shows its credit limit, available credit and amount due.
        id(
            wallets.create(
                "Visa card", WalletType.CREDIT_CARD, money(640),
                DebtTerms(
                    interestRateBps = 2400,
                    creditLimit = money(5_000),
                    statementDay = today.minusDays(10).dayOfMonth,
                    dueDay = today.plusDays(5).dayOfMonth,
                ),
            ),
        )
        // An instalment loan with three repayments recorded in earlier months (3 of 24 paid).
        val loan = id(
            wallets.create(
                "Car loan", WalletType.LOAN, money(12_000),
                DebtTerms(interestRateBps = 2400, repayment = DebtRepayment.MONTHLY, termMonths = 24, installment = money(634, 44)),
            ),
        )
        // The composer's wallet bar starts on the default, so it opens without asking which wallet to use.
        container.preferences.setDefaultWallet(bank)

        val categories = container.categories.observeCategories().first()
        fun cat(name: String) = categories.first { it.name == name && !it.archived }.id
        val salary = categories.first { it.kind == CategoryKind.INCOME && it.name == "Salary" }.id
        val tx = container.transactions
        suspend fun add(type: TransactionType, amount: Money, wallet: Long, category: Long?, date: LocalDate, note: String) {
            val r = tx.save(TransactionRepository.Input(null, UUID.randomUUID().toString(), type, amount, wallet, category, date, note))
            check(r is TransactionRepository.SaveResult.Saved) { "seed failed: $r" }
        }
        for (monthsBack in 2L downTo 1L) {
            val d = today.minusMonths(monthsBack).withDayOfMonth(10)
            add(TransactionType.INCOME, money(4_200), bank, salary, d, "Salary")
            add(TransactionType.EXPENSE, money(1_500), bank, cat("Rent"), d, "Rent")
            add(TransactionType.EXPENSE, money(380 + monthsBack * 40), bank, cat("Groceries"), d, "Groceries")
            add(TransactionType.EXPENSE, money(210), cash, cat("Restaurants"), d, "Dinners")
        }
        add(TransactionType.INCOME, money(4_200), bank, salary, day(1), "Salary")
        add(TransactionType.EXPENSE, money(1_500), bank, cat("Rent"), day(2), "Rent")
        add(TransactionType.EXPENSE, money(212, 40), bank, cat("Groceries"), day(4), "Weekly shop")
        add(TransactionType.EXPENSE, money(107, 60), bank, cat("Groceries"), day(9), "Market")
        add(TransactionType.EXPENSE, money(145), cash, cat("Restaurants"), day(6), "Dinner out")
        add(TransactionType.EXPENSE, money(96), bank, cat("Electricity"), day(5), "Power bill")
        add(TransactionType.EXPENSE, money(60), bank, cat("Internet"), day(3), "Fibre")
        add(TransactionType.EXPENSE, money(48), cash, cat("Public transportation"), day(7), "Transit card")
        add(TransactionType.EXPENSE, money(15, 99), bank, cat("Streaming services"), day(8), "Streaming")
        add(TransactionType.EXPENSE, money(89), bank, cat("Clothing and shoes"), day(8), "Shoes")
        add(TransactionType.EXPENSE, money(24), cash, null, day(8), "Uncategorised")

        val budgets = container.budgets
        listOf(
            "Rent" to money(1_500), "Groceries" to money(450), "Restaurants" to money(200), "Electricity" to money(120),
            "Internet" to money(60), "Public transportation" to money(80), "Streaming services" to money(20),
            "Clothing and shoes" to money(100), "Personal care" to money(60),
        ).forEach { (name, amount) -> budgets.setAllocation(cat(name), currency, amount) }

        val goals = container.goals
        val emergency = (goals.save(null, "Emergency fund", money(5_000), null, savings) as GoalRepository.SaveResult.Saved).id
        val trip = (goals.save(null, "Japan trip", money(3_000), today.plusMonths(7), savings) as GoalRepository.SaveResult.Saved).id
        goals.contribute(emergency, money(2_100), today.minusMonths(2), "", UUID.randomUUID().toString())
        goals.contribute(trip, money(1_650), today.minusMonths(1), "", UUID.randomUUID().toString())

        (1..3).forEach { n ->
            container.transactions.saveTransfer(
                TransactionRepository.TransferInput(
                    group = null, clientUuid = UUID.randomUUID().toString(), fromWalletId = bank, toWalletId = loan,
                    amount = money(400), date = today.minusMonths(n.toLong()).withDayOfMonth(1), note = "",
                ),
            )
        }

        val recurring = container.recurring
        fun schedule(name: String, amount: Money, start: LocalDate, category: Long?) = RecurringRepository.Input(
            null, name, TransactionType.EXPENSE, amount, bank, category, Frequency.MONTHLY, 1, start, null,
        )
        // Every scheduled bill has a category now (owner, 2026-11-03); the internet bill is overdue.
        recurring.save(schedule("Fibre internet", money(29, 99), today.minusDays(3), cat("Internet")))
        recurring.save(schedule("Phone plan", money(35), today.plusDays(5), cat("Mobile phone")))
        recurring.save(schedule("Rent", money(1_500), today.plusDays(12), cat("Rent")))
        // A loan instalment: a transfer from the bank to the loan, shown in neutral ink and never in Bills due.
        recurring.save(
            RecurringRepository.Input(
                null, "Car loan instalment", TransactionType.EXPENSE, money(634, 44), bank, null, Frequency.MONTHLY, 1,
                today.plusDays(8), null, toWalletId = loan,
            ),
        )
    }
}
