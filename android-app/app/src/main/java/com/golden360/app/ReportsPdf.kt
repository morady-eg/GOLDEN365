package com.golden360.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a paginated, print-ready PDF for every report kind. The resulting file is kept in
 * the app cache only and is shared through FileProvider; it is not written to Downloads.
 */
internal object ReportsPdf {
    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 28f
    private val black = android.graphics.Color.rgb(28, 28, 28)
    private val gold = android.graphics.Color.rgb(164, 126, 43)
    private val paleGold = android.graphics.Color.rgb(248, 243, 230)
    private val ink = android.graphics.Color.rgb(48, 48, 48)
    private val muted = android.graphics.Color.rgb(105, 105, 105)
    private val line = android.graphics.Color.rgb(224, 219, 207)
    private val white = android.graphics.Color.WHITE

    fun write(file: File, title: String, month: String, employeeScope: String, rows: List<List<String>>) {
        require(rows.isNotEmpty()) { "لا توجد بيانات لإنشاء التقرير" }
        require(rows.first().isNotEmpty()) { "أعمدة التقرير غير موجودة" }
        file.parentFile?.mkdirs()
        val document = PdfDocument()
        try {
            val headers = rows.first()
            val body = rows.drop(1)
            val columnCount = headers.size
            val availableWidth = PAGE_WIDTH - MARGIN * 2
            val widths = columnWidths(headers, availableWidth)
            val headerHeight = if (columnCount >= 12) 52f else 40f
            val rowHeight = if (columnCount >= 12) 44f else if (columnCount >= 8) 38f else 34f
            val topOfTable = 132f
            val footerReserve = 28f
            val rowsPerPage = (((PAGE_HEIGHT - MARGIN - footerReserve - topOfTable - headerHeight) / rowHeight).toInt()).coerceAtLeast(1)
            val chunks = body.chunked(rowsPerPage).ifEmpty { listOf(emptyList()) }
            chunks.forEachIndexed { pageIndex, chunk ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())
                val canvas = page.canvas
                canvas.drawColor(white)
                drawHeader(canvas, title, month, employeeScope, pageIndex + 1, chunks.size)
                drawTableHeader(canvas, headers, widths, topOfTable, headerHeight)
                var y = topOfTable + headerHeight
                chunk.forEachIndexed { index, row ->
                    val isTotal = row.firstOrNull()?.contains("الإجمالي") == true ||
                        row.firstOrNull()?.contains("المجموع") == true
                    val rowColor = when {
                        isTotal -> paleGold
                        index % 2 == 0 -> android.graphics.Color.rgb(249, 248, 245)
                        else -> white
                    }
                    canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, paint(rowColor))
                    canvas.drawLine(MARGIN, y + rowHeight, PAGE_WIDTH - MARGIN, y + rowHeight, paint(line, 0.8f))
                    drawRow(canvas, headers, row, widths, y, rowHeight, isTotal)
                    y += rowHeight
                }
                drawFooter(canvas, pageIndex + 1, chunks.size)
                document.finishPage(page)
            }
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun columnWidths(headers: List<String>, availableWidth: Float): List<Float> {
        if (headers.size <= 1) return listOf(availableWidth)
        val weights = headers.mapIndexed { index, header ->
            when {
                index == 0 -> if (headers.size >= 12) 1.7f else 1.8f
                header.contains("ملاحظات") || header.contains("سبب") || header.contains("تواريخ") -> 1.6f
                header.contains("اسم") || header.contains("الموظف") -> 1.35f
                header.contains("المبلغ") || header.contains("القيمة") || header.contains("صافي") -> 1.15f
                else -> 1f
            }
        }
        val total = weights.sum()
        return weights.map { availableWidth * it / total }
    }

    private fun drawHeader(
        canvas: Canvas,
        title: String,
        month: String,
        employeeScope: String,
        page: Int,
        pages: Int
    ) {
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 104f, paint(black))
        canvas.drawRect(0f, 98f, PAGE_WIDTH.toFloat(), 104f, paint(gold))
        centered(canvas, "GOLDEN 360", PAGE_WIDTH / 2f, 28f, paint(white, 17f, true))
        centered(canvas, title, PAGE_WIDTH / 2f, 52f, paint(android.graphics.Color.rgb(239, 211, 143), 12f, true))
        centered(canvas, "الفترة: $month    •    النطاق: $employeeScope", PAGE_WIDTH / 2f, 73f, paint(white, 9f))
        centered(canvas, "تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ROOT)),
            PAGE_WIDTH / 2f, 91f, paint(android.graphics.Color.rgb(220, 220, 220), 8f))
        val pageLabel = "صفحة $page من $pages"
        centered(canvas, pageLabel, PAGE_WIDTH / 2f, PAGE_HEIGHT - 8f, paint(muted, 7.5f))
    }

    private fun drawTableHeader(canvas: Canvas, headers: List<String>, widths: List<Float>, top: Float, height: Float) {
        canvas.drawRoundRect(RectF(MARGIN, top, PAGE_WIDTH - MARGIN, top + height), 5f, 5f, paint(gold))
        var x = PAGE_WIDTH - MARGIN
        headers.forEachIndexed { index, header ->
            x -= widths[index]
            drawCell(canvas, header, x, top, widths[index], height, paint(white, fontSize(headers.size, true), true), true, maxLines = 3)
        }
    }

    private fun drawRow(
        canvas: Canvas,
        headers: List<String>,
        row: List<String>,
        widths: List<Float>,
        top: Float,
        height: Float,
        isTotal: Boolean
    ) {
        var x = PAGE_WIDTH - MARGIN
        headers.forEachIndexed { index, _ ->
            val width = widths[index]
            x -= width
            val value = row.getOrElse(index) { "" }
            val amountLike = value.trim().matches(Regex("-?[0-9][0-9,.]*")) ||
                headers[index].contains("المبلغ") || headers[index].contains("ج.م") || headers[index].contains("صافي")
            val color = if (isTotal && amountLike) gold else ink
            drawCell(
                canvas, value, x, top, width, height,
                paint(color, fontSize(headers.size, false), isTotal),
                isRtl(value) || isRtl(headers[index]),
                maxLines = if (headers.size >= 12) 2 else 3
            )
        }
    }

    private fun drawCell(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        textPaint: TextPaint,
        rtl: Boolean,
        maxLines: Int
    ) {
        val safeWidth = (width - 8f).toInt().coerceAtLeast(1)
        val direction = if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, safeWidth)
            .setAlignment(if (rtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(direction)
            .setIncludePad(false)
            .setLineSpacing(1f, 1f)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.clipRect(x, y, x + width, y + height)
        canvas.translate(x + 4f, y + (height - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas, page: Int, pages: Int) {
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 20f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 20f, paint(gold, 0.8f))
        val footer = "GOLDEN 360 • تقرير صادر من بيانات التطبيق • $page / $pages"
        centered(canvas, footer, PAGE_WIDTH / 2f, PAGE_HEIGHT - 8f, paint(muted, 7.5f))
    }

    private fun isRtl(value: String): Boolean = value.any { it in '\u0590'..'\u08FF' }

    private fun fontSize(columnCount: Int, header: Boolean): Float = when {
        columnCount >= 13 -> if (header) 6.2f else 5.8f
        columnCount >= 10 -> if (header) 7f else 6.5f
        columnCount >= 8 -> if (header) 8f else 7.5f
        else -> if (header) 9.5f else 9f
    }

    private fun centered(canvas: Canvas, text: String, x: Float, y: Float, textPaint: Paint) {
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x, y, textPaint)
    }

    private fun paint(color: Int, size: Float = 10f, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
}
