package com.lucento.app.data.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.lucento.app.R
import com.lucento.app.core.designsystem.theme.LightColors
import com.lucento.app.core.designsystem.theme.Palette
import com.lucento.app.core.money.Money
import com.lucento.app.core.money.MoneyFormatter
import com.lucento.app.domain.ask.Report
import com.lucento.app.domain.ask.ReportLine
import com.lucento.app.domain.ask.ReportSection
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Writes a [Report] as an A4 PDF with Android's own [PdfDocument] (no library, nothing leaves the phone): the brand
 * header, the headline figure, each section as a table with its total, the notes on how it was calculated, and a footer
 * on every page. The figures are the report's own — the PDF and the screen can't disagree.
 */
class ReportPdfWriter(context: Context) {
    private val appContext = context.applicationContext
    private val regular: Typeface = font(R.font.plus_jakarta_sans_regular, Typeface.NORMAL)
    private val semibold: Typeface = font(R.font.plus_jakarta_sans_semibold, Typeface.BOLD)
    private val bold: Typeface = font(R.font.plus_jakarta_sans_bold, Typeface.BOLD)

    /** Writes the PDF into the app's private cache (for the share sheet) and returns the file; the last one is replaced. */
    fun writeToCache(report: Report, generatedAt: LocalDateTime = LocalDateTime.now()): File {
        val dir = File(appContext.cacheDir, DIRECTORY).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(report))
        FileOutputStream(file).use { write(report, it, generatedAt) }
        return file
    }

    fun write(report: Report, out: OutputStream, generatedAt: LocalDateTime = LocalDateTime.now()) {
        val document = PdfDocument()
        try {
            Layout(document, report, generatedAt).draw()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun font(id: Int, fallback: Int): Typeface =
        runCatching { ResourcesCompat.getFont(appContext, id) }.getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, fallback)

    /** One pass over the report, page by page. */
    private inner class Layout(private val document: PdfDocument, private val report: Report, private val generatedAt: LocalDateTime) {
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f
        /** The group heading last printed ("Income and expenses"), so each is printed once where it starts. */
        private var group: String? = null

        // The light theme's own tokens: a printed page is always light.
        private val ink = LightColors.onSurface.toArgb()
        private val muted = LightColors.onSurfaceVariant.toArgb()
        private val brand = Palette.BlueHero.toArgb()
        private val negative = Palette.NegativeText.toArgb()
        private val rule = LightColors.outlineVariant.toArgb()
        private val tint = Palette.PaleSky.toArgb()

        // Linear, subpixel text: a PDF keeps exact glyph advances, so hinting must not round small sizes (it ate spaces).
        private fun paint(face: Typeface, size: Float, color: Int = ink, align: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
            textAlign = align
        }

        fun draw() {
            newPage()
            // Title block.
            canvas.text(report.title, MARGIN, y + 22f, paint(bold, 22f))
            y += 30f
            canvas.text("${report.period} · ${report.currencyCode} · ${MoneyFormatter.currencyName(report.currencyCode)}", MARGIN, y + 12f, paint(regular, 10.5f, muted))
            y += 26f
            headline(report.headline)
            report.sections.forEach(::section)
            notes()
            moreFromLucento()
            finishPage()
        }

        private fun newPage() {
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            // Brand rule and wordmark on every page.
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 6f, Paint().apply { color = brand })
            canvas.text("Lucento", MARGIN, 34f, paint(bold, 14f, brand))
            val stamp = "Prepared ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()).format(generatedAt)}"
            canvas.text(stamp, PAGE_WIDTH - MARGIN, 34f, paint(regular, 9f, muted, Paint.Align.RIGHT))
            canvas.drawLine(MARGIN, 46f, PAGE_WIDTH - MARGIN, 46f, Paint().apply { color = rule; strokeWidth = 1f })
            y = 64f
        }

        private fun finishPage() {
            val footer = "Calculated on this phone by Lucento from your own records. Not financial advice. · lucentoapp.com"
            canvas.text(footer, MARGIN, PAGE_HEIGHT - 24f, paint(regular, 8f, muted))
            canvas.text("Page $pageNumber", PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 24f, paint(regular, 8f, muted, Paint.Align.RIGHT))
            document.finishPage(page)
        }

        /** Starts a new page when [height] more points wouldn't fit above the footer. */
        private fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - BOTTOM) {
                finishPage()
                newPage()
            }
        }

        private fun headline(line: ReportLine) {
            val detailLines = line.detail?.let { wrap(it, paint(regular, 10f), CONTENT - 32f) }.orEmpty()
            val height = 62f + detailLines.size * 13f
            ensure(height + 16f)
            canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + height), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint })
            canvas.text(line.label.uppercase(Locale.getDefault()), MARGIN + 16f, y + 20f, paint(semibold, 9f, brand))
            line.values.firstOrNull()?.let { canvas.text(MoneyFormatter.formatSigned(it, showPlus = false), MARGIN + 16f, y + 46f, paint(bold, 22f, if (it.isNegative) negative else ink)) }
            detailLines.forEachIndexed { i, text -> canvas.text(text, MARGIN + 16f, y + 62f + i * 13f, paint(regular, 10f, muted)) }
            y += height + 18f
        }

        private fun section(section: ReportSection) {
            val columns = section.columns.size.coerceAtLeast(1)
            val labelWidth = CONTENT - columns * COLUMN
            if (section.group != null && section.group != group) {
                ensure(70f)
                canvas.text(section.group.uppercase(Locale.getDefault()), MARGIN, y + 10f, paint(semibold, 8.5f, brand))
                y += 18f
            }
            group = section.group
            ensure(48f)
            canvas.text(section.title, MARGIN, y + 13f, paint(bold, 13f))
            section.columns.forEachIndexed { i, header ->
                canvas.text(header.uppercase(Locale.getDefault()), columnRight(i, columns), y + 13f, paint(semibold, 8f, muted, Paint.Align.RIGHT))
            }
            y += 20f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, Paint().apply { color = rule; strokeWidth = 1f })
            y += 4f
            if (section.lines.isEmpty() && section.total == null) {
                ensure(20f)
                canvas.text(section.emptyText, MARGIN, y + 13f, paint(regular, 10f, muted))
                y += 22f
            }
            section.lines.forEach { row(it, columns, labelWidth) }
            section.total?.let {
                ensure(24f)
                canvas.drawLine(MARGIN, y + 2f, PAGE_WIDTH - MARGIN, y + 2f, Paint().apply { color = ink; strokeWidth = 0.8f })
                y += 4f
                row(it.copy(emphasis = true), columns, labelWidth)
            }
            y += 14f
        }

        private fun row(line: ReportLine, columns: Int, labelWidth: Float) {
            val labelPaint = paint(if (line.emphasis) bold else regular, 10.5f)
            val detailPaint = paint(regular, 8.5f, muted)
            val label = ellipsize(line.label, labelPaint, labelWidth - 8f)
            val detail = line.detail?.let { wrap(it, detailPaint, labelWidth - 8f).take(3) }.orEmpty()
            val bar = if (line.share != null) 7f else 0f
            val height = 18f + detail.size * 11f + bar
            ensure(height)
            canvas.text(label, MARGIN, y + 13f, labelPaint)
            detail.forEachIndexed { i, text -> canvas.text(text, MARGIN, y + 25f + i * 11f, detailPaint) }
            line.share?.let { share ->
                // The line's share of its section, as a thin bar under the label (the amount is printed beside it).
                val top = y + 18f + detail.size * 11f + 1f
                val width = (labelWidth - 8f) * 0.7f
                canvas.drawRoundRect(RectF(MARGIN, top, MARGIN + width, top + 3.5f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rule })
                canvas.drawRoundRect(
                    RectF(MARGIN, top, MARGIN + width * share.coerceIn(0f, 1f), top + 3.5f),
                    2f,
                    2f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brand },
                )
            }
            line.values.take(columns).forEachIndexed { i, value ->
                if (value != null) {
                    val text = if (line.signed) MoneyFormatter.formatSigned(value, showPlus = true) else amount(value)
                    canvas.text(text, columnRight(i, columns), y + 13f, paint(if (line.emphasis) bold else semibold, 10.5f, color(value), Paint.Align.RIGHT))
                }
            }
            y += height + 4f
        }

        private fun notes() {
            if (report.notes.isEmpty()) return
            ensure(30f)
            canvas.text("How this was calculated", MARGIN, y + 12f, paint(semibold, 10f))
            y += 20f
            val p = paint(regular, 9f, muted)
            report.notes.forEach { note ->
                wrap("• $note", p, CONTENT).forEach { text ->
                    ensure(13f)
                    canvas.text(text, MARGIN, y + 10f, p)
                    y += 13f
                }
            }
        }

        /**
         * The closing panel (tester round 3): where Lucento lives online, as full https links — PDF viewers turn them into
         * links, which Android's PdfDocument can't add itself. Printed text only; nothing is sent from the phone.
         */
        private fun moreFromLucento() {
            val lines = listOf(
                "Lucento · Financial clarity without the complexity.",
                "Website: ${com.lucento.app.core.LucentoLinks.WEBSITE}",
                "Lucento Community on Facebook: ${com.lucento.app.core.LucentoLinks.COMMUNITY}",
            )
            val height = 24f + lines.size * 15f
            ensure(height + 20f)
            y += 6f
            canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + height), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint })
            canvas.text("MORE FROM LUCENTO", MARGIN + 16f, y + 18f, paint(semibold, 8.5f, brand))
            lines.forEachIndexed { i, text ->
                canvas.text(text, MARGIN + 16f, y + 34f + i * 15f, paint(if (i == 0) semibold else regular, 9.5f, if (i == 0) ink else brand))
            }
            y += height + 12f
        }

        /**
         * Text is drawn eight times larger on a canvas scaled down to match: the PDF keeps the glyph positions, so
         * spaces and letter widths aren't rounded at small sizes (they were, and words ran together).
         */
        private fun Canvas.text(value: String, x: Float, y: Float, p: Paint) {
            save()
            scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
            drawText(value, x * TEXT_SCALE, y * TEXT_SCALE, Paint(p).apply { textSize = p.textSize * TEXT_SCALE })
            restore()
        }

        private fun columnRight(index: Int, columns: Int): Float = PAGE_WIDTH - MARGIN - (columns - 1 - index) * COLUMN

        /** Signed with a real minus, so meaning never rests on colour alone. */
        private fun amount(value: Money): String = MoneyFormatter.formatSigned(value, showPlus = false)

        private fun color(value: Money): Int = if (value.isNegative) negative else ink

        private fun ellipsize(text: String, p: Paint, width: Float): String {
            if (p.measureText(text) <= width) return text
            var end = p.breakText(text, true, width - p.measureText("…"), null)
            if (end < 1) end = 1
            return text.take(end).trimEnd() + "…"
        }

        private fun wrap(text: String, p: Paint, width: Float): List<String> {
            val lines = ArrayList<String>()
            var current = StringBuilder()
            text.split(' ').forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (p.measureText(candidate) <= width || current.isEmpty()) {
                    current = StringBuilder(candidate)
                } else {
                    lines.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
            return lines.map { ellipsize(it, p, width) }
        }
    }

    companion object {
        const val DIRECTORY = "reports"
        private const val PAGE_WIDTH = 595 // A4 in points
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
        private const val BOTTOM = 56f
        private const val COLUMN = 92f
        private const val CONTENT = PAGE_WIDTH - 2 * MARGIN
        private const val TEXT_SCALE = 8f

        /** "Lucento-Net-worth-statement-2026-11.pdf". */
        fun fileName(report: Report): String =
            "Lucento-" + report.title.replace("&", "and").replace(Regex("[^A-Za-z0-9]+"), "-").trim('-') + "-${report.month}.pdf"
    }
}
