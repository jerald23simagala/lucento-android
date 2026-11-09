package com.lucento.app.domain.ask

import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyParser
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/** What a typed question is about, read on the phone before anything is sent anywhere. */
enum class AskTopic {
    /** Anything about money or Lucento: reports, advice, plans. */
    MONEY,

    /** Whether to buy something ("Should I buy a new phone?", "Can I afford a 45,000 laptop?"). */
    PURCHASE,

    /** Clearly not about money (code, recipes, homework…): answered on the phone, never sent to Gemini. */
    OFF_TOPIC,
}

/**
 * Plain keyword rules (owner test round 2, 2026-11-09): a question with no money word that is plainly about something
 * else — "Give me VBA code to print a PDF in Excel" — gets a one-line answer saying what Ask Lucento is for, on the
 * phone, without spending the AI allowance; a purchase question can be answered on the phone with [PurchaseCheck]
 * when Gemini is off or unreachable. Anything in between goes to Gemini (when on), whose own instructions steer back
 * to money. Never presented as AI.
 */
object AskScope {

    const val OFF_TOPIC_REPLY = "That’s outside what I can help with. I’m Lucento’s money adviser: ask me about your " +
        "spending, budget, savings, debts, goals or a purchase you’re weighing — for example “Can I afford a new phone " +
        "this month?”"

    private val MONEY_WORDS = listOf(
        "money", "budget", "spend", "spent", "save", "saving", "income", "salary", "wage", " pay", "paid", "debt", "loan",
        "card", "buy", "afford", "purchase", "price", "cost", "invest", "fund", "bank", "wallet", "expense", "bill",
        "subscription", "goal", "worth", "cash", "peso", "dollar", "financ", " tax", " rent", "credit", "interest",
        "retire", "insurance", " mp2", "pag ibig", "stock", "crypto", "transaction", "lucento", "categor", "account",
        "allowance", "emergency", "net flow", "balance", "mortgage", "dividend", "earn",
    )

    private val OFF_TOPIC_SIGNS = listOf(
        " code ", " coding", " vba ", " macro", "python", "javascript", " java ", "kotlin", " sql ", " html", " css ",
        "script", "programming", " program ", "excel", "formula", "recipe", " cook", " bake", "weather", " poem",
        " song", "lyrics", " joke", "translate", " essay", "homework", " movie", "capital of", "who won", " game ",
    )

    private val PURCHASE_ASK = Regex(" (should|shall|can|could|do) i (really )?(buy|but|purchase|order|upgrade|get) ")
    private val NOT_A_PURCHASE = listOf(" loan", " credit", " card", " mortgage", " raise", " refund", " job")

    fun topic(question: String): AskTopic {
        val q = normalise(question)
        if (MONEY_WORDS.none { q.contains(it) } && OFF_TOPIC_SIGNS.any { q.contains(it) }) return AskTopic.OFF_TOPIC
        val buying = q.contains("afford") || q.contains("worth buying") || q.contains(" buying ") ||
            (PURCHASE_ASK.containsMatchIn(q) && NOT_A_PURCHASE.none { q.contains(it) })
        return if (buying) AskTopic.PURCHASE else AskTopic.MONEY
    }

    /**
     * The price in a purchase question, in [currency] only — never converted. A number counts as a price only when it
     * is marked as one: the currency's code or symbol, "for", "at", "costs", "price", a "k" ("45k") or thousands
     * separators ("45,000"). So "iPhone 17" is not read as 17. A price in another currency is ignored.
     */
    fun price(question: String, currency: String): Money? {
        for (m in AMOUNT.findAll(question)) {
            val codes = listOfNotNull(m.groups[1]?.value, m.groups[5]?.value).map { it.uppercase(Locale.ROOT) }.filter(::isCurrencyCode)
            val symbol = m.groups[2]?.value
            // Another currency's price is never converted (CLAUDE.md "Money rules").
            if (codes.any { it != currency } || (symbol != null && symbol != SYMBOLS[currency])) continue
            val number = m.groups[3]!!
            val k = m.groups[4] != null
            val lead = " " + question.substring(0, number.range.first).lowercase(Locale.ROOT).trimEnd()
            val marked = codes.isNotEmpty() || symbol != null || k || number.value.contains(',') || PRICE_LEADS.any { lead.endsWith(it) }
            if (!marked) continue
            val value = runCatching { BigDecimal(number.value.replace(",", "")) }.getOrNull() ?: continue
            val amount = if (k) value.multiply(BigDecimal(1000)) else value
            val money = MoneyParser.parseOrNull(amount.stripTrailingZeros().toPlainString(), currency) ?: continue
            if (money.amountMinor > 0L) return money
        }
        return null
    }

    private fun isCurrencyCode(code: String): Boolean = runCatching { Currency.getInstance(code) }.isSuccess

    private val AMOUNT = Regex("""(?:\b([A-Za-z]{3})\s?|([₱$€£¥₹₩])\s?)?(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?)([kK]\b)?(?:\s?([A-Za-z]{3})\b)?""")
    private val PRICE_LEADS = listOf(" for", " at", " costs", " cost", " price", " priced", " worth", " of", " is")
    private val SYMBOLS = mapOf("PHP" to "₱", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "INR" to "₹", "KRW" to "₩")

    private fun normalise(question: String): String =
        " " + question.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ") + " "
}

/**
 * Whether a purchase fits, worked out on the phone from the same figures Ask Lucento's AI sees ([AskSnapshot]): this
 * month's safe to spend (Home's figure), cash, bank, e-wallet and savings balances, the average month's spending and
 * net flow, net worth and what's owed. The rule (docs/CALCULATIONS.md "Ask Lucento reports" › "Purchase check"):
 * a price within safe to spend fits this month; above it, it can come from balances only if about three months of
 * average spending stays aside; otherwise wait, with how long saving at the average net flow would take.
 */
object PurchaseCheck {

    const val CUSHION_MONTHS = 3L

    fun answer(snapshot: AskSnapshot, price: Money?): List<CheckedAnswer.Part> {
        val out = Parts()
        val currency = snapshot.currency
        fun fig(id: String): Money? = snapshot.byId[id]?.amount?.takeIf { it.currencyCode == currency }
        val safe = fig("safe")
        val onHand = fig("on_hand")
        val savings = fig("savings")
        val avgOut = fig("avg_out")?.takeIf { it.amountMinor > 0L }
        val avgNet = fig("avg_net")
        val worth = fig("nw")
        val owed = fig("nw_owed")?.abs()?.takeIf { !it.isZero }
        val liquid = if (onHand == null && savings == null) null else (onHand ?: Money.zero(currency)) + (savings ?: Money.zero(currency))
        val cushion = avgOut?.let { Money(Math.multiplyExact(it.amountMinor, CUSHION_MONTHS), currency) }

        if (price == null) {
            out.text("Here’s where you stand for a purchase:\n")
            safe?.let { out.text("• Safe to spend for the rest of the month: ").figure(it).text("\n") }
            liquid?.let { l ->
                out.text("• Cash, bank, e-wallets and savings: ").figure(l)
                avgOut?.let { out.text(" — about ${months(l, it)} months of your average spending") }
                out.text("\n")
            }
            worth?.let { out.text("• Net worth: ").figure(it).text("\n") }
            owed?.let { out.text("• Owed on cards and loans: ").figure(it).text("\n") }
            out.text("Pay for it from this month’s money only if the price fits within your safe to spend. Above that, use savings only if ")
            if (cushion != null) out.text("about three months of spending (").figure(cushion).text(") stays aside") else out.text("a few months of spending stays aside")
            out.text(", and don’t put it on a card you can’t clear by the due date. Tell me the price in $currency — for example “Should I buy it for 45,000?” — and I’ll check it.")
            return out.parts
        }

        when {
            safe != null && !safe.isNegative && price <= safe -> {
                out.text("Yes, it fits. At ").figure(price).text(" it’s within the ").figure(safe)
                    .text(" that’s safe to spend for the rest of the month, so your bills and savings aren’t touched. It leaves ")
                    .figure(safe - price).text(" for the rest of the month.")
            }
            liquid == null -> out.text("I can’t tell yet: add your wallets with their balances first, and I’ll check it against them.")
            liquid < price -> {
                out.text("Not yet. ").figure(price).text(" is more than all your cash, bank, e-wallet and savings balances together (")
                    .figure(liquid).text(").")
                waitFor(out, price - liquid + (cushion ?: Money.zero(currency)), avgNet, if (cushion != null) "pay for it and keep three months of spending aside" else "save for it")
            }
            cushion == null -> {
                out.text("It’s more than this month’s safe to spend. Paying it would leave ").figure(liquid - price)
                    .text(" in your balances; after a full month of records I can tell how many months of spending that covers.")
            }
            liquid - price >= cushion -> {
                out.text("You can, but from savings rather than this month’s money: ").figure(price).text(" is more than your safe to spend")
                safe?.let { out.text(" (").figure(it).text(")") }
                out.text(". Paying it leaves ").figure(liquid - price).text(" — about ${months(liquid - price, avgOut!!)} months of your average spending.")
            }
            else -> {
                out.text("I’d wait. Paying ").figure(price).text(" would leave ").figure(liquid - price)
                    .text(", under three months of your average spending (").figure(cushion).text(") — a cushion worth keeping for emergencies.")
                waitFor(out, cushion - (liquid - price), avgNet, "pay for it and keep that cushion")
            }
        }
        owed?.let { out.text(" You also owe ").figure(it).text(" on cards and loans: clearing high-interest debt first usually beats a new purchase.") }
        return out.parts
    }

    /** "At your average net flow of X a month, you could [goal] in about N months." — only when money is being kept. */
    private fun waitFor(out: Parts, gap: Money, avgNet: Money?, goal: String) {
        if (avgNet == null || avgNet.amountMinor <= 0L || gap.amountMinor <= 0L) return
        val n = BigDecimal.valueOf(gap.amountMinor).divide(BigDecimal.valueOf(avgNet.amountMinor), 0, RoundingMode.CEILING)
        out.text(" At your average net flow of ").figure(avgNet).text(" a month, you could $goal in about $n ${if (n.toInt() == 1) "month" else "months"}.")
    }

    /** [amount] ÷ [perMonth] to one decimal ("4.5"). */
    internal fun months(amount: Money, perMonth: Money): String =
        BigDecimal.valueOf(amount.amountMinor).divide(BigDecimal.valueOf(perMonth.amountMinor), 1, RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString()

    private class Parts {
        val parts = ArrayList<CheckedAnswer.Part>()
        fun text(s: String) = apply { parts.add(CheckedAnswer.Part(s, isFigure = false)) }
        fun figure(m: Money) = apply { parts.add(CheckedAnswer.Part(Figures.plain(m), isFigure = true)) }
    }
}
