package com.lucento.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoriesTest {
    @Test fun namesAreUniquePerKind() {
        val keys = DefaultCategories.all.map { it.kind to it.name.lowercase() }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun bothKindsArePresent() {
        assertTrue(DefaultCategories.all.any { it.kind == CategoryKind.EXPENSE })
        assertTrue(DefaultCategories.all.any { it.kind == CategoryKind.INCOME })
    }

    @Test fun namesAreSafeForSqlSeeding() {
        assertTrue(DefaultCategories.all.none { it.name.contains('\'') || it.iconKey.contains('\'') })
    }
}
