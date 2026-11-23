package com.lucento.app.domain.capture

import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.Transaction
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.capture.merchant.MerchantDirectory
import java.io.File
import java.time.LocalDate

/** Shared wallets, categories and a small merchant dictionary for the document-intelligence tests. */
object ScanFixtures {

    val today: LocalDate = LocalDate.parse("2026-11-22")

    val groceries = Category(1, "Groceries", CategoryKind.EXPENSE, "groceries")
    val dining = Category(2, "Dining", CategoryKind.EXPENSE, "dining")
    val transport = Category(3, "Transport", CategoryKind.EXPENSE, "transport")
    val utilities = Category(4, "Utilities", CategoryKind.EXPENSE, "utilities")
    val shopping = Category(5, "Shopping", CategoryKind.EXPENSE, "shopping")
    val health = Category(6, "Health", CategoryKind.EXPENSE, "health")
    val subscriptions = Category(7, "Subscriptions", CategoryKind.EXPENSE, "subscriptions")
    val fuel = Category(8, "Fuel", CategoryKind.EXPENSE, "fuel")
    val bankFees = Category(9, "Bank fees", CategoryKind.EXPENSE, "bank_fees")
    val salary = Category(20, "Salary", CategoryKind.INCOME, "work")
    val refunds = Category(21, "Refunds", CategoryKind.INCOME, "refund")
    val interest = Category(22, "Investments", CategoryKind.INCOME, "investments")
    val categories = listOf(groceries, dining, transport, utilities, shopping, health, subscriptions, fuel, bankFees, salary, refunds, interest)

    fun wallet(id: Long, name: String, type: WalletType, currency: String = "PHP", balanceMinor: Long = 0, institution: String? = null, created: Long = 0) =
        Wallet(id, name, type, Money(balanceMinor, currency), created, institutionId = institution)

    val cash = wallet(1, "Cash", WalletType.CASH)
    val bpi = wallet(2, "BPI Savings", WalletType.BANK, institution = "ph_bpi")
    val gcash = wallet(3, "GCash", WalletType.E_WALLET, institution = "ph_gcash")
    val bdoCard = wallet(4, "BDO Card", WalletType.CREDIT_CARD, institution = "ph_bdo")
    val chase = wallet(10, "Chase Checking", WalletType.BANK, currency = "USD", institution = "us_chase")
    val amex = wallet(11, "Amex Gold", WalletType.CREDIT_CARD, currency = "USD")
    val wallets = listOf(cash, bpi, gcash, bdoCard, chase, amex)

    /** The bank registry's names for the wallets above, as the composer builds them. */
    val labels: Map<Long, List<String>> = mapOf(
        bpi.id to listOf("BPI", "Bank of the Philippine Islands"),
        gcash.id to listOf("GCash"),
        bdoCard.id to listOf("BDO", "BDO Unibank"),
        chase.id to listOf("Chase", "JPMorgan Chase"),
    )

    /** A small dictionary in the assets' format, so these tests don't depend on the bundled files' contents. */
    val directory: MerchantDirectory = MerchantDirectory.parse(
        listOf(
            """
            {"region": "TEST", "version": 3, "entities": [
              {"name": "Walmart", "kind": "MERCHANT", "aliases": ["walmart", "wal-mart", "wm supercenter"], "category": "groceries", "countries": ["US"]},
              {"name": "Target", "kind": "MERCHANT", "aliases": ["target.com"], "weakAliases": ["target"], "category": "shopping", "countries": ["US"]},
              {"name": "Starbucks", "kind": "MERCHANT", "aliases": ["starbucks"], "category": "cafe", "countries": ["US", "PH", "JP"]},
              {"name": "Shell", "kind": "MERCHANT", "aliases": ["shell oil", "shell station"], "weakAliases": ["shell"], "category": "fuel", "countries": ["US", "PH"]},
              {"name": "Netflix", "kind": "BILLER", "aliases": ["netflix", "netflix.com"], "category": "streaming", "countries": ["US", "PH"]},
              {"name": "PG&E", "kind": "BILLER", "aliases": ["pg&e", "pacific gas and electric"], "category": "utilities", "countries": ["US"]},
              {"name": "Meralco", "kind": "BILLER", "aliases": ["meralco", "manila electric"], "category": "utilities", "countries": ["PH"]},
              {"name": "Mercury Drug", "kind": "MERCHANT", "aliases": ["mercury drug"], "category": "pharmacy", "countries": ["PH"]},
              {"name": "Intermarché", "kind": "MERCHANT", "aliases": ["intermarché"], "category": "groceries", "countries": ["FR"]},
              {"name": "American Express", "kind": "CARD_NETWORK", "aliases": ["american express", "amex"], "issuesCards": true, "countries": ["US"]},
              {"name": "Visa", "kind": "CARD_NETWORK", "aliases": ["visa"]},
              {"name": "GCash", "kind": "EWALLET", "aliases": ["gcash", "g-cash"], "countries": ["PH"]},
              {"name": "PayPal", "kind": "EWALLET", "aliases": ["paypal"], "prefixes": ["paypal *", "pp*"]},
              {"name": "Square", "kind": "PROCESSOR", "aliases": ["squareup"], "prefixes": ["sq *", "sq*"]},
              {"name": "Chase", "kind": "BANK", "aliases": ["jpmorgan chase", "chase bank"], "weakAliases": ["chase"], "countries": ["US"]},
              {"name": "BPI", "kind": "BANK", "aliases": ["bpi", "bank of the philippine islands"], "countries": ["PH"]},
              {"name": "BDO", "kind": "BANK", "aliases": ["bdo", "bdo unibank"], "countries": ["PH"]},
              {"name": "Bad", "kind": "NOT_A_KIND", "aliases": ["bad"]}
            ]}
            """.trimIndent(),
            "not json at all",
        ),
    )

    fun context(
        defaultWalletId: Long? = cash.id,
        history: List<Transaction> = emptyList(),
        wallets: List<Wallet> = this.wallets,
        directory: MerchantDirectory = this.directory,
        chosen: Boolean = false,
        dayFirst: Boolean? = null,
    ) = InterpretationContext(
        wallets, categories, today, defaultWalletId,
        dayFirst = dayFirst,
        defaultWalletChosen = chosen,
        directory = directory,
        history = history,
        walletLabels = labels,
    )

    private var nextId = 1000L

    fun saved(
        type: TransactionType,
        minor: Long,
        wallet: Wallet,
        date: String,
        note: String = "",
        category: Category? = null,
    ) = Transaction(nextId++, type, Money(minor, wallet.currencyCode), wallet.id, category?.id, LocalDate.parse(date), note, 0)

    /** The bundled dictionaries (app/src/main/assets/merchants), read the way the app reads them. */
    fun bundledDirectory(): MerchantDirectory? {
        val dir = listOf(File("src/main/assets/merchants"), File("app/src/main/assets/merchants")).firstOrNull { it.isDirectory } ?: return null
        val files = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedBy { it.name }?.map { it.readText() }.orEmpty()
        return if (files.isEmpty()) null else MerchantDirectory.parse(files)
    }
}
