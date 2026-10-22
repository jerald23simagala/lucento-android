package com.lucento.app.domain

import com.lucento.app.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** CSV export hardening (audit finding 1.4). */
class CsvInjectionTest {

    private fun row(note: String = "", wallet: String = "Bank", category: String? = null, minor: Long = -1_250, type: TransactionType = TransactionType.EXPENSE): String {
        val t = Transaction(1, type, Money(kotlin.math.abs(minor), "USD"), 1, if (category != null) 9 else null, LocalDate.parse("2026-10-02"), note, 0)
        return CsvExporter.export(listOf(t), mapOf(1L to wallet), category?.let { mapOf(9L to it) } ?: emptyMap()).substringAfter("\r\n").removeSuffix("\r\n")
    }

    @Test fun formulaTriggersInNotesAreNeutralised() {
        assertEquals("'=HYPERLINK(\"x\")", CsvExporter.neutralizeFormula("=HYPERLINK(\"x\")"))
        listOf("=1+1", "+1+1", "-1+1", "@SUM(A1)", "=cmd|' /C calc'!A0").forEach { payload ->
            assertEquals("'$payload", CsvExporter.neutralizeFormula(payload))
        }
    }

    @Test fun leadingWhitespaceAndControlCharactersDoNotHideATrigger() {
        assertEquals("' =1+1", CsvExporter.neutralizeFormula(" =1+1"))
        assertEquals("'\t=1+1", CsvExporter.neutralizeFormula("\t=1+1"))
        assertEquals("'\r=1+1", CsvExporter.neutralizeFormula("\r=1+1"))
        assertEquals("'\u0000@x", CsvExporter.neutralizeFormula("\u0000@x"))
        assertEquals("'﻿=1", CsvExporter.neutralizeFormula("﻿=1"))
        assertEquals("'　-2", CsvExporter.neutralizeFormula("　-2"))
    }

    @Test fun leadingTabOrCarriageReturnIsNeutralisedEvenWithoutAFormula() {
        assertEquals("'\tnote", CsvExporter.neutralizeFormula("\tnote"))
    }

    @Test fun fullWidthTriggersAreNeutralised() {
        assertEquals("'＝1+1", CsvExporter.neutralizeFormula("＝1+1"))
        assertEquals("'＠A1", CsvExporter.neutralizeFormula("＠A1"))
    }

    @Test fun ordinaryTextIsUntouched() {
        listOf("Lunch", "Café crème", "1-on-1 coaching", "Rent (Oct)", "email me@x.com", "Q4=done", "", "   ").forEach {
            assertEquals(it, CsvExporter.neutralizeFormula(it))
        }
    }

    @Test fun walletAndCategoryNamesAreProtectedToo() {
        assertEquals("2026-10-02,expense,-12.50,USD,'@evil,'=bad,ok,", row(note = "ok", wallet = "@evil", category = "=bad"))
    }

    @Test fun quotesCommasAndLineBreaksAreEscaped() {
        assertEquals("2026-10-02,expense,-12.50,USD,Bank,,\"a, \"\"b\"\"\",", row(note = "a, \"b\""))
        assertEquals("2026-10-02,expense,-12.50,USD,Bank,,\"line1\nline2\",", row(note = "line1\nline2"))
        assertEquals("2026-10-02,expense,-12.50,USD,Bank,,\"x\r\ny\",", row(note = "x\r\ny"))
        // A formula behind a quote is both neutralised and quoted.
        assertEquals("2026-10-02,expense,-12.50,USD,Bank,,\"'=1,2\",", row(note = "=1,2"))
    }

    @Test fun unicodeIsPreserved() {
        assertEquals("2026-10-02,expense,-12.50,USD,Банк,,寿司 🍣,", row(note = "寿司 🍣", wallet = "Банк"))
    }

    @Test fun negativeAmountsStayNumeric() {
        assertEquals("-12.50", row().split(",")[2])
        assertEquals("12.50", row(type = TransactionType.INCOME).split(",")[2])
        // Even a note that is just a negative number is text the user typed and gets the apostrophe.
        assertEquals("'-5", CsvExporter.neutralizeFormula("-5"))
    }
}
