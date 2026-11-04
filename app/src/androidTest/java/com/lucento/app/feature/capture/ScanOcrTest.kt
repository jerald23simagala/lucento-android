package com.lucento.app.feature.capture

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.domain.capture.ReceiptParser
import com.lucento.app.domain.capture.ScanKind
import com.lucento.app.domain.capture.StatementFlow
import com.lucento.app.domain.capture.StatementParser
import com.lucento.app.domain.capture.TotalConfidence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Scanning end to end on a device (owner, 2026-11-04): real on-device OCR (ML Kit's bundled model) on made-up images in
 * `androidTest/assets/scans`, rows rebuilt by [ReceiptParser.mergeRows], then told apart and read — a printed statement
 * and an e-wallet's history line by line, a receipt as one payment.
 */
@RunWith(AndroidJUnit4::class)
class ScanOcrTest {

    private val today = LocalDate.parse("2026-11-04")
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun read(name: String): List<String> = runBlocking {
        val file = File(instrumentation.targetContext.cacheDir, name)
        instrumentation.context.assets.open("scans/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        ReceiptParser.mergeRows(ReceiptOcr.recognize(instrumentation.targetContext, Uri.fromFile(file)))
            .also { Log.i("ScanOcrTest", "$name:\n" + it.joinToString("\n")) }
    }

    @Test fun aPrintedStatementIsReadLineByLine() {
        val lines = read("statement.png")
        val seen = lines.joinToString("\n")
        assertEquals(seen, ScanKind.STATEMENT, StatementParser.classify(lines))
        val reading = StatementParser.parse(lines, today, expectedCurrency = "PHP")
        assertEquals(seen, listOf("2000.00", "30000.00", "3456.78", "1000.00", "12.34").map(::BigDecimal), reading.lines.map { it.amount })
        assertEquals(
            seen,
            listOf(StatementFlow.OUT, StatementFlow.IN, StatementFlow.OUT, StatementFlow.OUT, StatementFlow.IN),
            reading.lines.map { it.flow },
        )
        assertTrue(seen, reading.balanceChecked)
        assertEquals(seen, LocalDate.parse("2026-10-03"), reading.lines.first().date)
        assertTrue(seen, reading.lines.first().description.contains("Withdrawal", ignoreCase = true))
    }

    @Test fun anEWalletHistoryIsReadLineByLine() {
        val lines = read("ewallet.png")
        val seen = lines.joinToString("\n")
        assertEquals(seen, ScanKind.STATEMENT, StatementParser.classify(lines))
        val reading = StatementParser.parse(lines, today, expectedCurrency = "PHP")
        assertEquals(seen, listOf("500.00", "2000.00", "1234.50").map(::BigDecimal), reading.lines.map { it.amount })
        assertEquals(seen, listOf(StatementFlow.OUT, StatementFlow.IN, StatementFlow.OUT), reading.lines.map { it.flow })
        assertEquals(seen, listOf("2026-11-03", "2026-11-02", "2026-11-01").map(LocalDate::parse), reading.lines.map { it.date })
        assertEquals(seen, listOf("Send Money", "Cash In", "Bills Payment"), reading.lines.map { it.description })
    }

    @Test fun aReceiptIsOnePaymentForItsTotal() {
        val lines = read("receipt.png")
        val seen = lines.joinToString("\n")
        assertEquals(seen, ScanKind.RECEIPT, StatementParser.classify(lines))
        val receipt = ReceiptParser.parse(lines, today, expectedCurrency = "PHP")
        assertEquals(seen, BigDecimal("369.00"), receipt.total)
        assertEquals(seen, TotalConfidence.LABELLED, receipt.totalConfidence)
        assertEquals(seen, LocalDate.parse("2026-11-03"), receipt.date)
    }
}
