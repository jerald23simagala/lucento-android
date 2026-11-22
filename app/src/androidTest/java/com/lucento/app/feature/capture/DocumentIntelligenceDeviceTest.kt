package com.lucento.app.feature.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lucento.app.core.money.Money
import com.lucento.app.domain.Category
import com.lucento.app.domain.CategoryKind
import com.lucento.app.domain.TransactionType
import com.lucento.app.domain.Wallet
import com.lucento.app.domain.WalletType
import com.lucento.app.domain.capture.Interpretation
import com.lucento.app.domain.capture.InterpretationContext
import com.lucento.app.domain.capture.LocalTransactionInterpreter
import com.lucento.app.domain.capture.PageOrigin
import com.lucento.app.domain.capture.ScanDocument
import com.lucento.app.domain.capture.ScanPage
import com.lucento.app.domain.capture.merchant.MerchantDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Lucento Scan end to end on a device (scan roadmap, owner 2026-11-22): receipts drawn as images — tilted, turned
 * sideways, two in one photo — read by the real on-device OCR (ML Kit's bundled model) and the document reader, and a
 * PDF made on the phone read through [PdfDocumentReader] (its text layer on Android 15+, else page images and OCR).
 * The images are clean and computer-drawn: real photos (glare, creases, fading) are harder, and are tested by hand.
 */
@RunWith(AndroidJUnit4::class)
class DocumentIntelligenceDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val today = LocalDate.parse("2026-11-22")

    private val directory: MerchantDirectory by lazy {
        val names = app.assets.list("merchants").orEmpty().filter { it.endsWith(".json") }.sorted()
        MerchantDirectory.parse(names.map { n -> app.assets.open("merchants/$n").bufferedReader().use { it.readText() } })
    }

    private val categories = listOf(
        Category(1, "Groceries", CategoryKind.EXPENSE, "groceries"),
        Category(2, "Dining", CategoryKind.EXPENSE, "dining"),
        Category(3, "Health", CategoryKind.EXPENSE, "health"),
        Category(4, "Salary", CategoryKind.INCOME, "work"),
        Category(5, "Refunds", CategoryKind.INCOME, "refund"),
    )
    private val wallets = listOf(
        Wallet(1, "Cash", WalletType.CASH, Money(0, "PHP"), 0),
        Wallet(2, "BPI Savings", WalletType.BANK, Money(0, "PHP"), 0, institutionId = "ph_bpi"),
        Wallet(3, "US Checking", WalletType.BANK, Money(0, "USD"), 0),
    )

    private fun context() = InterpretationContext(wallets, categories, today, 1L, directory = directory, walletLabels = mapOf(2L to listOf("BPI")))

    /** Lines drawn like a printed receipt: words on the left, the amount right-aligned, monospace. */
    private fun draw(lines: List<Pair<String, String?>>, width: Int = 820): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 30f; typeface = Typeface.MONOSPACE }
        val right = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
        val bitmap = Bitmap.createBitmap(width, 100 + lines.size * 46, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        lines.forEachIndexed { i, (label, amount) ->
            val y = 70f + i * 46
            canvas.drawText(label, 40f, y, paint)
            amount?.let { canvas.drawText(it, width - 40f, y, right) }
        }
        return bitmap
    }

    private fun rotated(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        // Fill the corners a rotation leaves transparent, as paper would be.
        val out = Bitmap.createBitmap(turned.width, turned.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(turned, 0f, 0f, null) }
        return out
    }

    private fun sideBySide(a: Bitmap, b: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(a.width + b.width + 160, maxOf(a.height, b.height), Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(a, 0f, 0f, null); drawBitmap(b, a.width + 160f, 0f, null) }
        return out
    }

    private fun read(vararg bitmaps: Bitmap): Interpretation.Candidates = runBlocking {
        val pages = bitmaps.map { ScanPage.ofFragments(ReceiptOcr.recognize(it), PageOrigin.PHOTO) }
        pages.forEachIndexed { i, p -> Log.i("DocIntelDeviceTest", "page $i:\n" + p.lines.joinToString("\n")) }
        LocalTransactionInterpreter().interpretDocument(ScanDocument(pages), context()) as Interpretation.Candidates
    }

    private val mercury = listOf(
        "MERCURY DRUG" to null, "ORTIGAS AVE PASIG CITY" to null, "VAT REG TIN 000-388-474-000" to null, "11/20/2026 14:05" to null,
        "BIOGESIC 500MG" to "45.50", "VITAMIN C 500MG" to "120.00", "AMOUNT DUE" to "165.50", "CASH" to "200.00", "CHANGE" to "34.50",
    )
    private val jollibee = listOf(
        "JOLLIBEE" to null, "SM MEGAMALL" to null, "11/21/2026 12:31" to null, "CHICKENJOY 1PC" to "99.00", "SPAGHETTI" to "60.00",
        "TOTAL" to "159.00", "CASH" to "200.00", "CHANGE" to "41.00",
    )

    @Test fun aTiltedReceiptIsReadUpright() {
        val result = read(rotated(draw(mercury), 3f))
        val row = result.items.single()
        assertEquals(BigDecimal("165.50"), row.amount)
        assertEquals("Mercury Drug", row.description)
        assertEquals(LocalDate.parse("2026-11-20"), row.date)
        assertEquals(3L, row.categoryId)
    }

    @Test fun aReceiptPhotographedSidewaysIsReadUpright() {
        val result = read(rotated(draw(jollibee), 90f))
        val row = result.items.single()
        assertEquals(BigDecimal("159.00"), row.amount)
        assertEquals("Jollibee", row.description)
    }

    @Test fun twoReceiptsInOnePhotoAreTwoPayments() {
        val result = read(sideBySide(draw(mercury), draw(jollibee)))
        assertEquals(setOf(BigDecimal("165.50"), BigDecimal("159.00")), result.items.mapNotNull { it.amount }.toSet())
        assertEquals(2, result.items.size)
    }

    @Test fun aUsReceiptIsReadInDollars() {
        val walmart = listOf(
            "WALMART" to null, "2500 4TH AVE" to null, "SEATTLE WA 98121" to null, "11/18/26 14:02" to null,
            "GV MILK" to "3.49", "BREAD" to "2.99", "SUBTOTAL" to "6.48", "TAX" to "0.58", "TOTAL" to "$7.06", "VISA TEND" to "7.06",
        )
        val row = read(draw(walmart)).items.single()
        assertEquals(BigDecimal("7.06"), row.amount)
        assertEquals("USD", row.mentionedCurrency)
        assertEquals(3L, row.walletId)
        assertEquals(1L, row.categoryId)
    }

    @Test fun aPdfStatementIsReadPageByPage() = runBlocking {
        val file = File(app.cacheDir, "statement-test.pdf")
        val pdf = PdfDocument()
        val lines = listOf(
            listOf(
                "BPI SAVINGS ACCOUNT STATEMENT", "Statement Period: Nov 01, 2026 - Nov 20, 2026",
                "DATE    DESCRIPTION                 DEBIT       CREDIT      BALANCE",
                "11/01   BEGINNING BALANCE                                   10,000.00",
                "11/03   POS PURCHASE SM SUPERMARKET 1,845.50                 8,154.50",
                "11/05   PAYROLL ACME INC                        15,000.00   23,154.50",
            ),
            listOf(
                "DATE    DESCRIPTION                 DEBIT       CREDIT      BALANCE",
                "11/09   BILLS PAYMENT MERALCO       2,345.67                20,808.83",
                "11/18   SERVICE CHARGE                 50.00                20,758.83",
                "11/20   ENDING BALANCE                                      20,758.83",
            ),
        )
        lines.forEachIndexed { n, pageLines ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(612, 792, n + 1).create())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f; typeface = Typeface.MONOSPACE }
            pageLines.forEachIndexed { i, line -> page.canvas.drawText(line, 36f, 60f + i * 16, paint) }
            pdf.finishPage(page)
        }
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()

        val read = PdfDocumentReader.read(app, Uri.fromFile(file), null) as PdfDocumentReader.Result.Pages
        assertEquals(2, read.total)
        Log.i("DocIntelDeviceTest", "pdf pages: " + read.pages.map { it.origin } + "\n" + read.pages.flatMap { it.lines }.joinToString("\n"))
        val result = LocalTransactionInterpreter().interpretDocument(ScanDocument(read.pages), context()) as Interpretation.Candidates
        assertEquals(listOf("1845.50", "15000.00", "2345.67", "50.00").map(::BigDecimal), result.items.map { it.amount })
        assertEquals(TransactionType.INCOME, result.items[1].type)
        assertTrue(result.items.all { it.walletId == 2L })
        assertEquals(BigDecimal("20758.83"), result.statements.single().summary.closing)
        file.delete()
    }
}
