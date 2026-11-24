package com.lucento.app.feature.capture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.graphics.pdf.content.PdfPageTextContent
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import com.lucento.app.domain.capture.LayoutRow
import com.lucento.app.domain.capture.PageOrigin
import com.lucento.app.domain.capture.ReceiptParser
import com.lucento.app.domain.capture.ScanPage
import com.lucento.app.domain.capture.TextFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a PDF on this phone (docs/SCAN.md "PDF statements"): a bank's e-statement, a card statement, an invoice. The
 * file is opened through Android's own PDF renderer, read where it is, and never copied, uploaded or kept.
 *
 * - **Text first.** A PDF made by a bank carries its text: on Android 15 and later (and on Android 12–14 phones whose
 *   system update includes it) each page's exact text is read with its positions, so columns are rebuilt as on paper.
 * - **OCR when there is no text** (a scanned PDF, or an older phone): the page is drawn on the phone and read by the
 *   same on-device OCR as photos.
 * - **Locked PDFs** (statements protected with a birth date or account digits) ask for the password, which is used for
 *   this one read and never stored. Phones without the newer renderer can't open locked PDFs; the person is told how
 *   to save an unlocked copy.
 */
object PdfDocumentReader {

    /** A statement longer than this is read in parts. */
    const val MAX_PAGES = 20

    sealed interface Result {
        /** [total]: pages in the PDF (more than [pages] when it was longer than [MAX_PAGES]). */
        data class Pages(val pages: List<ScanPage>, val total: Int) : Result
        data object NeedsPassword : Result
        data object WrongPassword : Result
        /** Locked, and this phone's PDF renderer can't open locked files. */
        data object LockedUnsupported : Result
        data object Unreadable : Result
    }

    suspend fun read(context: Context, uri: Uri, password: String?): Result = withContext(Dispatchers.IO) {
        val file = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return@withContext Result.Unreadable
        try {
            when {
                Build.VERSION.SDK_INT >= 35 -> readWithText(file, password)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13 -> readWithTextPreV(file, password)
                else -> readByOcr(file)
            }
        } catch (e: OutOfMemoryError) {
            Result.Unreadable
        } catch (e: java.io.IOException) {
            Result.Unreadable
        } finally {
            runCatching { file.close() }
        }
    }

    @RequiresApi(35)
    private suspend fun readWithText(file: ParcelFileDescriptor, password: String?): Result {
        val renderer = try {
            if (password == null) PdfRenderer(file) else PdfRenderer(file, LoadParams.Builder().setPassword(password).build())
        } catch (e: SecurityException) {
            return if (password == null) Result.NeedsPassword else Result.WrongPassword
        }
        return renderer.use { r ->
            val count = r.pageCount
            val pages = (0 until minOf(count, MAX_PAGES)).map { index ->
                r.openPage(index).use { page ->
                    textPage(page.textContents) ?: ocrPage(page.width, page.height) { bitmap, matrix ->
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
            Result.Pages(pages, count)
        }
    }

    @SuppressLint("NewApi")
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private suspend fun readWithTextPreV(file: ParcelFileDescriptor, password: String?): Result {
        val renderer = try {
            if (password == null) PdfRendererPreV(file) else PdfRendererPreV(file, LoadParams.Builder().setPassword(password).build())
        } catch (e: SecurityException) {
            return if (password == null) Result.NeedsPassword else Result.WrongPassword
        }
        return renderer.use { r ->
            val count = r.pageCount
            val pages = (0 until minOf(count, MAX_PAGES)).map { index ->
                r.openPage(index).use { page ->
                    textPage(page.textContents) ?: ocrPage(page.width, page.height) { bitmap, matrix ->
                        page.render(bitmap, null, matrix, android.graphics.pdf.RenderParams.Builder(android.graphics.pdf.RenderParams.RENDER_MODE_FOR_DISPLAY).build())
                    }
                }
            }
            Result.Pages(pages, count)
        }
    }

    /** Older phones: no text layer and no locked files; each page is drawn and read by OCR. */
    private suspend fun readByOcr(file: ParcelFileDescriptor): Result {
        val renderer = try {
            PdfRenderer(file)
        } catch (e: SecurityException) {
            return Result.LockedUnsupported
        }
        return renderer.use { r ->
            val count = r.pageCount
            val pages = (0 until minOf(count, MAX_PAGES)).map { index ->
                r.openPage(index).use { page ->
                    ocrPage(page.width, page.height) { bitmap, matrix -> page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                }
            }
            Result.Pages(pages, count)
        }
    }

    /**
     * A page's own text as rows. Pieces that come with one box per line are placed by position, so a table drawn column
     * by column still reads row by row; without boxes the text is taken in its own order. Null when the page has no
     * real text (a scanned page): it is read by OCR instead. Called only from [readWithText] (Android 15+) and
     * [readWithTextPreV] (S extension 13), where these text objects exist.
     */
    @SuppressLint("NewApi")
    private fun textPage(contents: List<PdfPageTextContent>): ScanPage? {
        val text = contents.joinToString("\n") { it.text }
        if (text.count { it.isLetterOrDigit() } < MIN_TEXT) return null
        val fragments = ArrayList<TextFragment>()
        var positioned = true
        contents.forEach { content ->
            val lines = content.text.split('\n', '\r').map { it.trimEnd() }.filter { it.isNotBlank() }
            val bounds = content.bounds
            when {
                lines.isEmpty() -> Unit
                bounds.size == lines.size -> lines.zip(bounds).forEach { (line, box) -> fragments += fragment(line, box) }
                lines.size == 1 && bounds.isNotEmpty() -> fragments += fragment(lines.single(), union(bounds))
                else -> positioned = false
            }
        }
        if (positioned && fragments.isNotEmpty()) return ScanPage(ReceiptParser.layoutRows(fragments), PageOrigin.PDF_TEXT, fragments)
        val rows = text.split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }.map { LayoutRow(it) }
        return ScanPage(rows, PageOrigin.PDF_TEXT)
    }

    private const val MIN_TEXT = 20

    /** PDF points scaled ×4 to whole pixels, so small type keeps distinct positions. */
    private fun fragment(text: String, box: RectF) = TextFragment(
        text = text,
        left = (box.left * 4).toInt(),
        top = (box.top * 4).toInt(),
        right = (box.right * 4).toInt(),
        bottom = (box.bottom * 4).toInt(),
    )

    private fun union(boxes: List<RectF>): RectF = RectF(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })

    /** The page drawn white at about 2,400 px on its long side, read by on-device OCR, then the bitmap freed. */
    private suspend fun ocrPage(width: Int, height: Int, draw: (Bitmap, Matrix) -> Unit): ScanPage {
        val scale = minOf(3f, TARGET_PIXELS / maxOf(width, height).coerceAtLeast(1).toFloat())
        val bitmap = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.WHITE)
            draw(bitmap, Matrix().apply { setScale(scale, scale) })
            ScanPage.ofFragments(ReceiptOcr.recognize(bitmap), PageOrigin.PDF_IMAGE)
        } finally {
            bitmap.recycle()
        }
    }

    private const val TARGET_PIXELS = 2400f
}
