package com.lucento.app.domain.capture.merchant

import java.util.Locale

/**
 * What kind of business a receipt comes from, by the receipt's own words, for a business no dictionary knows: a tip line
 * and a server's name make a restaurant; gallons and a pump number, a fuel station; labour and installation, a repair.
 * The last resort after the person's own habits, the dictionaries and the category words — and only when at least two
 * cues agree (or one that can't mean anything else), so "TABLE SALT" never makes a supermarket a restaurant.
 */
object ReceiptCues {

    private class Cue(val intent: String, val strong: Regex, val weak: Regex?)

    private fun re(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    private val cues = listOf(
        Cue(
            "fuel",
            re("\\b(gallons?|litres?|liters?|unleaded|diesel|gasoline|petrol|benzin|essence|gasolina|bensin|nafta|price/gal|pump\\s*#?\\s*\\d+)\\b"),
            re("\\b(regular|premium|fuel|pump)\\b"),
        ),
        Cue("parking", re("\\b(parking\\s+fee|car\\s*park|parkhaus|stationnement|estacionamiento|entry\\s+time|exit\\s+time)\\b"), re("\\b(parking|ticket|lot)\\b")),
        Cue("hotel", re("\\b(room\\s+(charge|rate|no)|folio|check[- ]?in\\s+date|check[- ]?out\\s+date|nights?\\s+stay)\\b"), re("\\b(hotel|guest|nights?)\\b")),
        Cue("pharmacy", re("\\b(pharmacy|farmacia|pharmacie|apotheke|drugstore|prescription|rx\\s*#)\\b"), null),
        Cue(
            "repairs",
            re("\\b(labou?r|installation|service\\s+call|parts\\s+and\\s+labou?r|aircon|air\\s*con(ditioner)?|hvac|plumbing|electrical\\s+work|contractor|cleaning\\s+service)\\b"),
            re("\\b(repairs?|install|maintenance|services?|technician|cleaning|freon|recharge)\\b"),
        ),
        Cue("takeout", re("\\b(food\\s+truck|take[- ]?out|to[- ]go|for\\s+pick[- ]?up)\\b"), re("\\b(delivery|pick[- ]?up)\\b")),
        Cue(
            "dining",
            // Restaurant checks abbreviate: "Tbl: 12  Pax: 3  Svr: JUN".
            re("\\b(gratuity|propina|pourboire|trinkgeld|mancia|server|svr|waiter|waitress|mesero|covers|pax|restaurant|restaurante|bistro|trattoria|taberna|taqueria|pizzeria|izakaya|dine[- ]in|cuisine)\\b|\\btbl\\s*[:#]"),
            re("\\b(table|guest|tip|grill|kitchen|cocina|mesa|ramen|sushi|bbq|burger|tacos?|cerveza|beer|wine|steak|pasta|iced\\s+tea)\\b|\\bservice\\s+charge\\b|\\bcheck\\s*#"),
        ),
        Cue(
            "groceries",
            re("\\b(supermarket|supermercado|supermarch[ée]|hypermarket|groceries|grocery|lebensmittel|alimentation)\\b"),
            re("(\\d\\s*(kg|lb)\\b|/kg\\b|/lb\\b|\\bproduce\\b|\\bdairy\\b|\\bbakery\\b|\\bmeat\\b|\\bfruit\\b|\\bvegetables?\\b)"),
        ),
    )

    /** The category intent the receipt's words point to, or null when they don't agree on one. */
    fun intent(lines: List<String>): String? {
        val text = lines.joinToString("\n").lowercase(Locale.ROOT)
        return cues.firstOrNull { cue ->
            val strong = cue.strong.findAll(text).map { it.value }.distinct().count()
            val weak = cue.weak?.findAll(text)?.map { it.value }?.distinct()?.count() ?: 0
            (strong >= 1 && (strong + weak >= 2 || cue.weak == null)) || weak >= 3
        }?.intent
    }
}
