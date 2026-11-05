package com.lucento.app.feature.wallets

import com.lucento.app.core.banks.InstitutionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** How the institution picker groups and labels rows (pure functions; the shipped registry as data). */
class InstitutionPickerSectionsTest {

    private val registry by lazy { InstitutionRegistry.parse(File("src/main/assets/banks/institutions.json").readText()) }
    private val names = mapOf("PH" to "Philippines", "AE" to "United Arab Emirates", "US" to "United States")
    private val label: (String) -> String = { names[it] ?: it }

    @Test fun `browsing a country groups it by kind, banks first`() {
        val sections = pickerSections(registry.search("", "PH"), "PH", searching = false, countryLabel = label)
        assertEquals(listOf("traditional_bank", "digital_bank", "financial_platform"), sections.map { it.key })
        assertEquals(listOf("Banks", "Digital banks", "Payments & investing"), sections.map { it.title })
        assertTrue(sections.all { s -> s.items.all { it.country == "PH" && it.type == s.key } })
        assertEquals(registry.countryCounts["PH"], sections.sumOf { it.items.size })
        // the heading already says kind and the chip says country, so a plain row needs no second line
        assertNull(pickerSubtitle(sections.first().items.first(), GroupedBy.KIND, alias = null, countryLabel = label))
    }

    @Test fun `browsing every country groups by country name`() {
        val sections = pickerSections(registry.search(""), null, searching = false, countryLabel = label)
        assertEquals(registry.countryCounts.size, sections.size)
        assertEquals(sections.map { label(it.key) }.sorted(), sections.map { it.title })
        assertEquals(registry.all.size, sections.sumOf { it.items.size })
    }

    @Test fun `a search lists the chosen country's matches, then other countries'`() {
        val sections = pickerSections(registry.search("hsbc", "PH"), "PH", searching = true, countryLabel = label)
        assertEquals(listOf("In Philippines", "Other countries"), sections.map { it.title })
        assertEquals(listOf("ph_hsbc_ph"), sections[0].items.map { it.id })
        assertTrue(sections[1].items.none { it.country == "PH" } && sections[1].items.isNotEmpty())
        val elsewhere = sections[1].items.first()
        assertEquals("Bank · ${label(elsewhere.country)}", pickerSubtitle(elsewhere, GroupedBy.ELSEWHERE, null, label))

        val onlyAbroad = pickerSections(registry.search("emirates nbd", "PH"), "PH", searching = true, countryLabel = label)
        assertEquals(listOf("Other countries"), onlyAbroad.map { it.title })
        assertTrue(pickerSections(emptyList(), "PH", searching = true).isEmpty())
    }

    @Test fun `the country menu pins home and the country on screen, then lists the rest alphabetically`() {
        val (pinned, others) = countryOptions(registry, home = "AE", shown = "PH", label = label)
        assertEquals(listOf("AE", "PH"), pinned)
        assertTrue("AE" !in others && "PH" !in others)
        assertEquals(registry.countryCounts.size, pinned.size + others.size)
        assertEquals(others.sortedBy(label), others)
        assertEquals(listOf("PH"), countryOptions(registry, home = "PH", shown = "PH", label = label).first)
        assertEquals(emptyList<String>(), countryOptions(registry, home = "XX", shown = null, label = label).first) // no institutions there
    }

    @Test fun `a row found through an alias says so`() {
        val mari = registry["ph_maribank"]!!
        val alias = registry.matchedAlias(mari, "seabank")
        assertEquals("Also known as SeaBank · Digital bank", pickerSubtitle(mari, GroupedBy.HOME, alias, label))
    }
}
