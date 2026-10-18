package com.golden360.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.golden360.app.data.PayrollSnapshotEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class PayrollReportEmployee(
    val name: String,
    val code: String,
    val jobTitle: String,
    val baseSalary: Long,
    val overtime: Long,
    val bonuses: Long,
    val absenceDeduction: Long,
    val advances: Long,
    val penalties: Long,
    val netSalary: Long
)

internal val MONTHLY_PAYROLL_HEADERS = listOf(
    "الموظف", "الأساسي", "الورديات الإضافية", "المكافآت",
    "السلف", "خصم الغياب", "الجزاءات", "صافي المستحق"
)

internal fun monthlyPayrollEmployeeLabel(employee: PayrollReportEmployee): String =
    monthlyPayrollEmployeeLabel(employee)

internal object PayrollReportPdf {
    private val black = android.graphics.Color.rgb(28, 28, 28)
    private val gold = android.graphics.Color.rgb(164, 126, 43)
    private val paleGold = android.graphics.Color.rgb(248, 243, 230)
    private val ink = android.graphics.Color.rgb(48, 48, 48)
    private val muted = android.graphics.Color.rgb(105, 105, 105)
    private val line = android.graphics.Color.rgb(224, 219, 207)
    private val green = android.graphics.Color.rgb(41, 105, 74)
    private val red = android.graphics.Color.rgb(164, 48, 48)

    fun writeIndividual(context: Context, uri: Uri, snapshot: PayrollSnapshotEntity) {
        val document = PdfDocument()
        try {
            val width = 595
            val height = 842
            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
            val canvas = page.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            val margin = 36f
            val right = width - margin
            val regular = paint(ink, 12f)
            val small = paint(muted, 9f)
            val bold = paint(black, 14f, true)
            val header = RectF(margin, 28f, right, 112f)
            canvas.drawRoundRect(header, 12f, 12f, paint(black))
            canvas.drawRect(margin, 100f, right, 112f, paint(gold))
            drawLogo(canvas, context, RectF(margin + 12f, 36f, margin + 70f, 94f))
            centered(canvas, "GOLDEN 360", width / 2f, 57f, paint(android.graphics.Color.WHITE, 22f, true))
            centered(canvas, "كشف راتب تفصيلي", width / 2f, 83f, paint(android.graphics.Color.rgb(239, 211, 143), 13f, true))
            centered(canvas, "كشف معتمد • نسخة محفوظة", width / 2f, 132f, small)

            // Employee information panel
            val infoTop = 150f
            canvas.drawRoundRect(RectF(margin, infoTop, right, 242f), 9f, 9f, paint(android.graphics.Color.rgb(250, 249, 246)))
            canvas.drawRoundRect(RectF(margin, infoTop, right, infoTop + 4f), 3f, 3f, paint(gold))
            drawRtl(canvas, "بيانات الموظف", right - 14f, infoTop + 24f, 180f, paint(gold, 11f, true))
            drawRtl(canvas, "اسم الموظف", right - 14f, infoTop + 47f, 175f, small)
            drawRtl(canvas, snapshot.employeeName, right - 14f, infoTop + 65f, 290f, bold)
            drawRtl(canvas, "كود الموظف: ${snapshot.employeeCode}", width / 2f - 12f, infoTop + 47f, 190f, small)
            drawRtl(canvas, "المسمى الوظيفي: ${snapshot.jobTitle}", width / 2f - 12f, infoTop + 70f, 230f, regular)
            drawRtl(canvas, "الفترة: ${snapshot.monthKey}", right - 14f, infoTop + 83f, 190f, regular)

            // Earnings and deductions table
            val tableTop = 262f
            val tableBottom = 574f
            canvas.drawRoundRect(RectF(margin, tableTop, right, tableBottom), 9f, 9f, paint(android.graphics.Color.WHITE, 1f, false, line))
            canvas.drawRoundRect(RectF(margin, tableTop, right, tableTop + 36f), 8f, 8f, paint(black))
            canvas.drawRect(margin, tableTop + 26f, right, tableTop + 36f, paint(black))
            drawRtl(canvas, "بيان الراتب", right - 16f, tableTop + 23f, 260f, paint(android.graphics.Color.WHITE, 11f, true))
            val valuePaint = paint(android.graphics.Color.WHITE, 11f, true).apply { textAlign = Paint.Align.LEFT }
            canvas.drawText("القيمة (ج.م)", margin + 16f, tableTop + 23f, valuePaint)
            val entries = listOf(
                "الراتب الأساسي" to snapshot.baseSalaryPiasters,
                "خصم الغياب (${snapshot.absentDays} يوم)" to -snapshot.absenceDeductionPiasters,
                "السلف" to -snapshot.advancesPiasters,
                "الجزاءات" to -snapshot.penaltiesPiasters,
                "الورديات الإضافية" to snapshot.overtimePiasters,
                "المكافآت" to snapshot.bonusesPiasters
            )
            var y = tableTop + 36f
            entries.forEachIndexed { index, entry ->
                val rowTop = y
                val rowBottom = rowTop + 38f
                if (index % 2 == 0) canvas.drawRect(margin + 1f, rowTop, right - 1f, rowBottom, paint(android.graphics.Color.rgb(250, 249, 246)))
                canvas.drawLine(margin + 12f, rowBottom, right - 12f, rowBottom, paint(line, 1f))
                drawRtl(canvas, entry.first, right - 16f, rowTop + 24f, 280f, regular)
                val amountPaint = paint(if (entry.second < 0) red else ink, 12f, true).apply { textAlign = Paint.Align.LEFT }
                canvas.drawText(money(kotlin.math.abs(entry.second)), margin + 16f, rowTop + 24f, amountPaint)
                y = rowBottom
            }
            val netTop = tableBottom + 14f
            canvas.drawRoundRect(RectF(margin, netTop, right, netTop + 62f), 9f, 9f, paint(paleGold))
            canvas.drawRoundRect(RectF(margin, netTop, right, netTop + 62f), 9f, 9f, paint(gold, 1f, false, gold))
            drawRtl(canvas, "صافي الراتب المستحق", right - 16f, netTop + 24f, 240f, paint(black, 12f, true))
            val netPaint = paint(if (snapshot.netSalaryPiasters < 0) red else gold, 20f, true).apply { textAlign = Paint.Align.LEFT }
            canvas.drawText("${money(snapshot.netSalaryPiasters)} ج.م", margin + 16f, netTop + 43f, netPaint)
            if (snapshot.netSalaryPiasters < 0) drawRtl(canvas, "يتطلب مراجعة قبل الصرف", right - 16f, netTop + 48f, 220f, paint(red, 9f, true))

            val approvedAt = runCatching {
                DateTimeFormatter.ofPattern("yyyy/MM/dd - HH:mm", Locale.ROOT)
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(snapshot.approvedAtEpochMillis))
            }.getOrDefault("—")
            drawRtl(canvas, "تاريخ الاعتماد: $approvedAt", right - 4f, 666f, 250f, small)
            drawRtl(canvas, "تم إنشاء هذا الكشف من نظام GOLDEN 360، والقيم المعروضة هي القيم المحفوظة عند الاعتماد.", right - 4f, 687f, width - 2 * margin, small)

            // Signature blocks
            val sigTop = 728f
            val sigPaint = paint(muted, 10f)
            canvas.drawLine(margin + 8f, sigTop, margin + 155f, sigTop, paint(line, 1.2f))
            canvas.drawLine(width / 2f - 65f, sigTop, width / 2f + 65f, sigTop, paint(line, 1.2f))
            canvas.drawLine(right - 163f, sigTop, right - 8f, sigTop, paint(line, 1.2f))
            centered(canvas, "توقيع الموظف", margin + 82f, sigTop + 19f, sigPaint)
            centered(canvas, "الحسابات", width / 2f, sigTop + 19f, sigPaint)
            centered(canvas, "اعتماد الإدارة", right - 82f, sigTop + 19f, sigPaint)
            canvas.drawLine(margin, 790f, right, 790f, paint(gold, 1.2f))
            centered(canvas, "GOLDEN 360  •  نظام إدارة الرواتب", width / 2f, 810f, paint(muted, 9f))
            document.finishPage(page)
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: throw IllegalStateException("تعذر فتح ملف PDF للكتابة")
        } finally {
            document.close()
        }
    }

    fun writeMonthly(context: Context, uri: Uri, month: String, employees: List<PayrollReportEmployee>) {
        val document = PdfDocument()
        try {
            val width = 842
            val height = 595
            val margin = 30f
            // Column sequence is authored right-to-left: employee, base, overtime, bonuses, advances, absence, penalties, net.
            val columns = listOf(145f, 90f, 90f, 90f, 90f, 90f, 90f, 97f)
            val headers = MONTHLY_PAYROLL_HEADERS
            val chunks = employees.chunked(8).ifEmpty { listOf(emptyList()) }
            chunks.forEachIndexed { pageIndex, chunk ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, pageIndex + 1).create())
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawRect(0f, 0f, width.toFloat(), 92f, paint(black))
                canvas.drawRect(0f, 86f, width.toFloat(), 92f, paint(gold))
                drawLogo(canvas, context, RectF(margin + 8f, 8f, margin + 72f, 80f))
                centered(canvas, "GOLDEN 360", width / 2f, 31f, paint(android.graphics.Color.WHITE, 17f, true))
                centered(canvas, "كشف الرواتب المجمع • $month", width / 2f, 57f, paint(android.graphics.Color.rgb(239, 211, 143), 12f, true))
                val subtitle = "عدد الموظفين: ${employees.size}    |    الصفحة ${pageIndex + 1} من ${chunks.size}"
                centered(canvas, subtitle, width / 2f, 77f, paint(android.graphics.Color.WHITE, 8f))
                var x = width - margin
                val top = 116f
                val rowHeight = 34f
                canvas.drawRoundRect(RectF(margin, top, width - margin, top + rowHeight), 5f, 5f, paint(gold))
                headers.forEachIndexed { i, label ->
                    x -= columns[i]
                    drawCell(canvas, label, x, top, columns[i], rowHeight, paint(android.graphics.Color.WHITE, 9f, true), true)
                }
                var y = top + rowHeight
                chunk.forEachIndexed { rowIndex, employee ->
                    x = width - margin
                    val rowPaint = if (rowIndex % 2 == 0) paint(android.graphics.Color.rgb(249, 248, 245)) else paint(android.graphics.Color.WHITE)
                    canvas.drawRect(margin, y, width - margin, y + rowHeight, rowPaint)
                    canvas.drawLine(margin, y + rowHeight, width - margin, y + rowHeight, paint(line, 0.8f))
                    val values = listOf(
                        "${employee.name}\n${employee.code}",
                        money(employee.baseSalary),
                        money(employee.overtime),
                        money(employee.bonuses),
                        money(employee.advances),
                        money(employee.absenceDeduction),
                        money(employee.penalties),
                        money(employee.netSalary)
                    )
                    values.forEachIndexed { i, value ->
                        x -= columns[i]
                        drawCell(canvas, value, x, y, columns[i], rowHeight, paint(if (i == 7 && employee.netSalary < 0) red else ink, if (i == 0) 8.5f else 9f, i == 7), i == 0)
                    }
                    y += rowHeight
                }
                if (pageIndex == chunks.lastIndex) {
                    val totalTop = maxOf(y + 6f, 425f)
                    val totalValues = listOf(
                        "الإجمالي",
                        money(employees.sumOf { it.baseSalary }),
                        money(employees.sumOf { it.overtime }),
                        money(employees.sumOf { it.bonuses }),
                        money(employees.sumOf { it.advances }),
                        money(employees.sumOf { it.absenceDeduction }),
                        money(employees.sumOf { it.penalties }),
                        money(employees.fold(0L) { total, item -> Math.addExact(total, item.netSalary) })
                    )
                    canvas.drawRoundRect(RectF(margin, totalTop, width - margin, totalTop + 30f), 5f, 5f, paint(paleGold))
                    canvas.drawRoundRect(RectF(margin, totalTop, width - margin, totalTop + 30f), 5f, 5f, paint(gold, 1f, false, gold))
                    var totalX = width - margin
                    totalValues.forEachIndexed { i, value ->
                        totalX -= columns[i]
                        drawCell(canvas, value, totalX, totalTop, columns[i], 30f, paint(if (i == totalValues.lastIndex) gold else black, 8.5f, true), i == 0)
                    }
                    val sigY = 510f
                    canvas.drawLine(margin + 10f, sigY, margin + 170f, sigY, paint(line, 1.1f))
                    canvas.drawLine(width / 2f - 80f, sigY, width / 2f + 80f, sigY, paint(line, 1.1f))
                    canvas.drawLine(width - margin - 170f, sigY, width - margin - 10f, sigY, paint(line, 1.1f))
                    centered(canvas, "إعداد الحسابات", margin + 90f, sigY + 16f, paint(muted, 8.5f))
                    centered(canvas, "مراجعة", width / 2f, sigY + 16f, paint(muted, 8.5f))
                    centered(canvas, "اعتماد الإدارة", width - margin - 90f, sigY + 16f, paint(muted, 8.5f))
                } else {
                    centered(canvas, "يتبع في الصفحة التالية", width / 2f, 566f, paint(muted, 8f))
                }
                document.finishPage(page)
            }
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: throw IllegalStateException("تعذر فتح ملف PDF للكتابة")
        } finally {
            document.close()
        }
    }

    private fun drawLogo(canvas: Canvas, context: Context, bounds: RectF) {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.golden) ?: return
        try {
            val scale = minOf(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            val logoWidth = bitmap.width * scale
            val logoHeight = bitmap.height * scale
            val left = bounds.centerX() - logoWidth / 2f
            val top = bounds.centerY() - logoHeight / 2f
            val destination = RectF(left, top, left + logoWidth, top + logoHeight)
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, null, destination, logoPaint)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawCell(canvas: Canvas, text: String, x: Float, y: Float, width: Float, height: Float, paint: TextPaint, rtl: Boolean) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (width - 10f).toInt().coerceAtLeast(1))
            .setAlignment(if (rtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(x + 5f, y + (height - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawRtl(canvas: Canvas, text: String, right: Float, baseline: Float, maxWidth: Float, paint: TextPaint) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(right - maxWidth, baseline - layout.height + 5f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun centered(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x, y, paint)
    }

    private fun paint(color: Int, size: Float = 10f, bold: Boolean = false, strokeColor: Int? = null): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                else android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            if (strokeColor != null) {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                this.color = strokeColor
            }
        }

    private fun money(piasters: Long): String = String.format(Locale.US, "%,.2f", piasters / 100.0)
}
