package com.lucento.app.feature.transactions

import com.lucento.app.domain.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor asks for a category exactly where the composer does (launch-film discovery issue 8). */
class CategoryRuleTest {

    @Test fun incomeAndExpensesNeedACategory() {
        assertTrue(categoryMissing(TransactionType.EXPENSE, null))
        assertTrue(categoryMissing(TransactionType.INCOME, null))
        assertFalse(categoryMissing(TransactionType.EXPENSE, 10L))
        assertFalse(categoryMissing(TransactionType.INCOME, 20L))
    }

    @Test fun transfersAndAdjustmentsDoNot() {
        assertFalse(categoryMissing(TransactionType.TRANSFER_OUT, null))
        assertFalse(categoryMissing(TransactionType.TRANSFER_IN, null))
        assertFalse(categoryMissing(TransactionType.ADJUSTMENT_IN, null))
        assertFalse(categoryMissing(TransactionType.ADJUSTMENT_OUT, null))
    }
}
