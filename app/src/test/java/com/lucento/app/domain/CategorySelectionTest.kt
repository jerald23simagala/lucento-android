package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySelectionTest {

    private fun preset(key: String) = CategorySelection.Choice.FromPreset(CategoryPresets.preset(key)!!)

    @Test fun presetLibraryIsWellFormed() {
        val keys = CategoryPresets.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        val names = CategoryPresets.all.map { CategoryPresets.normalize(it.name) }
        assertEquals(names.size, names.toSet().size)
        assertTrue(CategoryPresets.all.all { it.name.length <= com.lucento.app.data.repository.CategoryRepository.MAX_NAME })
        assertEquals(10, CategoryPresets.groups.size)
        assertTrue(CategoryPresets.groups.all { it.presets.isNotEmpty() })
        assertTrue(CategoryPresets.recommendedKeys.isNotEmpty())
        assertTrue(CategoryPresets.recommendedKeys.all { CategoryPresets.preset(it) != null })
        // A few required presets from each family.
        listOf("rent", "mortgage", "groceries", "fuel", "dental", "childcare", "streaming", "taxes", "emergency_fund",
            "home_office", "donations", "miscellaneous").forEach { assertTrue(it, CategoryPresets.preset(it) != null) }
    }

    @Test fun normalisationMatchesCaseSpacesAndAmpersands() {
        assertEquals("gifts and celebrations", CategoryPresets.normalize("  Gifts &  Celebrations "))
        assertEquals(CategoryPresets.normalize("Cafes and coffee"), CategoryPresets.normalize("CAFES & COFFEE"))
    }

    @Test fun freshInstallCreatesTheChosenCategoriesInOrder() {
        val plan = CategorySelection.plan(emptyList(), listOf(preset("rent"), preset("groceries"), CategorySelection.Choice.Custom("Boat fuel")))
        assertEquals(listOf("Rent", "Groceries", "Boat fuel"), plan.create.map { it.name })
        assertTrue(plan.create.all { it.kind == CategoryKind.EXPENSE })
        assertTrue(plan.deactivate.isEmpty())
    }

    @Test fun selectingAPresetTwiceOrMatchingACustomNameCreatesOneCategory() {
        val plan = CategorySelection.plan(emptyList(), listOf(preset("groceries"), preset("groceries"), CategorySelection.Choice.Custom(" groceries ")))
        assertEquals(1, plan.create.size)
    }

    @Test fun existingCategoriesAreReusedNotDuplicatedAndKeepTheirIds() {
        val existing = listOf(
            Category(1, "Groceries", CategoryKind.EXPENSE, "groceries"),
            Category(2, "Dining", CategoryKind.EXPENSE, "dining"),
            Category(3, "Rent", CategoryKind.EXPENSE, "housing", archived = true),
            Category(4, "Salary", CategoryKind.INCOME, "work"),
            Category(5, "Groceries", CategoryKind.INCOME, "income"), // same name, other kind: irrelevant
        )
        val plan = CategorySelection.plan(existing, listOf(preset("groceries"), preset("rent"), preset("water")))
        assertEquals(listOf(1L), plan.keep)
        assertEquals(listOf(3L), plan.restore)
        assertEquals(listOf("Water"), plan.create.map { it.name })
        // Dining was not chosen: deactivated (archived or deleted by the repository). Income is never touched.
        assertEquals(listOf(2L), plan.deactivate)
    }

    @Test fun existingCustomCategoriesCanBeKeptAsThemselves() {
        val existing = listOf(Category(7, "Boat", CategoryKind.EXPENSE, "other"))
        val (keys, unmatched) = CategorySelection.initialSelection(existing)
        assertTrue(keys.isEmpty())
        assertEquals(listOf(7L), unmatched.map { it.id })
        val plan = CategorySelection.plan(existing, listOf(CategorySelection.Choice.Existing(7, "Boat", "other")))
        assertEquals(listOf(7L), plan.keep)
        assertTrue(plan.isEmpty)
    }

    @Test fun initialSelectionTicksPresetsMatchingActiveCategories() {
        val existing = listOf(
            Category(1, "groceries", CategoryKind.EXPENSE, "groceries"),
            Category(2, "Internet", CategoryKind.EXPENSE, "utilities", archived = true),
        )
        val (keys, unmatched) = CategorySelection.initialSelection(existing)
        assertEquals(setOf("groceries"), keys)
        assertTrue(unmatched.isEmpty())
    }

    @Test fun customNamesAreCleaned() {
        assertEquals("Boat fuel", CategorySelection.cleanCustomName("  Boat   fuel "))
        assertNull(CategorySelection.cleanCustomName("   "))
        assertEquals(CategorySelection.MAX_CUSTOM, CategorySelection.cleanCustomName("x".repeat(80))!!.length)
    }
}
