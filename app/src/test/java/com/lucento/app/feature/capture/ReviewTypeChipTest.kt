package com.lucento.app.feature.capture

import com.lucento.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewTypeChipTest {

    @Test fun oneTapSwitchesExpenseAndIncome() {
        assertEquals(TransactionType.INCOME, flipIncomeExpense(TransactionType.EXPENSE))
        assertEquals(TransactionType.EXPENSE, flipIncomeExpense(TransactionType.INCOME))
        assertEquals(TransactionType.EXPENSE, flipIncomeExpense(TransactionType.TRANSFER_OUT))
    }
}