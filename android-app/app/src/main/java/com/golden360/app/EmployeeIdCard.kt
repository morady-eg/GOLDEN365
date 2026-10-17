package com.golden360.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.EmployeeEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

internal object EmployeeIdCardSpec {
    // Finished card after trimming (CR80 portrait).
    const val WIDTH_MM = 54
    const val HEIGHT_MM = 86
    // Artwork size includes bleed as requested; trim box is centered inside it.
    const val DESIGN_WIDTH_MM = 58
    const val DESIGN_HEIGHT_MM = 91
    const val PRINT_DPI = 300
    const val COMPANY_NAME = "GOLDEN SHINE"
    const val QR_SIZE_PX = 512
}

@Composable
internal fun EmployeeIdCardScreen(
    employees: List<EmployeeEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val employee = employees.first()
    val photo = produceState<Bitmap?>(initialValue = null, employee.profilePhotoUri) {
        value = withContext(Dispatchers.IO) {
            employee.profilePhotoUri?.takeIf { it.isNotBlank() }?.let { uriText ->
                runCatching { decodeSampledBitmap(context, Uri.parse(uriText), 360, 360) }.getOrNull()
            } ?: runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.golden)
            }.getOrNull()
        }
    }.value
    val qr = produceState<Bitmap?>(initialValue = null, employee.code) {
        value = withContext(Dispatchers.Default) { createEmployeeQr(employee.code) }
    }.value

    Column(
        modifier = Modifier.fillMaxSize().background(Black).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Gold)
            Text(if (employees.size == 1) "EMPLOYEE ID CARD" else "PREVIEW ${employees.size} EMPLOYEE ID CARDS", color = SoftGold, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Surface(
            modifier = Modifier.weight(1f, fill = false)
                .fillMaxWidth(0.78f)
                .aspectRatio(EmployeeIdCardSpec.WIDTH_MM.toFloat() / EmployeeIdCardSpec.HEIGHT_MM.toFloat()),
            color = Color(0xFF171717),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD6B15C))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF090909), RoundedCornerShape(5.dp)).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("GOLDEN SHINE", color = Color(0xFFE0BE69), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Text("EMPLOYEE IDENTITY", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp)
                    }
                }
                Box(
                    modifier = Modifier.size(92.dp).clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFFECE7DB)),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo != null) {
                        Image(
                            photo.asImageBitmap(),
                            contentDescription = if (employee.profilePhotoUri.isNullOrBlank()) "DEFAULT EMPLOYEE PHOTO" else "EMPLOYEE PHOTO",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Text(
                    employee.fullName.uppercase(java.util.Locale.ROOT),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
                Text(
                    employeeJobTitleEnglish(employee.jobTitle),
                    color = Color(0xFFE0BE69),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF29251C), RoundedCornerShape(5.dp)).padding(vertical = 5.dp, horizontal = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("EMPLOYEE NO.  ", color = Color(0xFFC6B78F), fontSize = 8.sp)
                    Text(employee.code, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                if (qr != null) {
                    Image(qr.asImageBitmap(), contentDescription = "EMPLOYEE QR CODE", modifier = Modifier.size(61.dp))
                }
                Text("SCAN TO IDENTIFY", color = Color(0xFFC6B78F), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Text("CARD SIZE: 54 × 86 MM • 300 DPI", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Button(
            onClick = { printEmployeeIds(context, employees) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Text(if (employees.size == 1) "PRINT EMPLOYEE ID" else "PRINT ${employees.size} EMPLOYEE IDS", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("BACK", color = SoftGold)
        }
    }
}

internal fun employeeJobTitleEnglish(jobTitle: String): String = when (jobTitle.uppercase(java.util.Locale.ROOT)) {
    "ADMIN" -> "ADMINISTRATOR"
    "SUPERVISOR" -> "SUPERVISOR"
    "TECHNICIAN" -> "TECHNICIAN"
    "ASSISTANT" -> "ASSISTANT"
    else -> jobTitle.replace('_', ' ').uppercase(java.util.Locale.ROOT)
}

internal fun createEmployeeQrMatrix(code: String) =
    QRCodeWriter().encode(code.also { require(it.isNotBlank()) }, BarcodeFormat.QR_CODE, EmployeeIdCardSpec.QR_SIZE_PX, EmployeeIdCardSpec.QR_SIZE_PX)

private fun createEmployeeQr(code: String): Bitmap? = runCatching {
    val matrix = createEmployeeQrMatrix(code)
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}.getOrNull()

internal fun employeeIdPrintPageCount(employeeCount: Int): Int = employeeCount.coerceAtLeast(0)
internal fun employeeIdRequestedPageIndices(employeeCount: Int, ranges: List<IntRange>): List<Int> = (0 until employeeCount.coerceAtLeast(0)).filter { index -> ranges.any { index >= it.first && index <= it.last } }

private fun printEmployeeIds(context: Context, employees: List<EmployeeEntity>) {
    if (employees.isEmpty()) return
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize("GOLDEN_SHINE_ID_BLEED", "GOLDEN SHINE ID 58x91 mm bleed", 2283, 3583))
        .setResolution(PrintAttributes.Resolution("GOLDEN_SHINE_ID_300", "300 dpi", EmployeeIdCardSpec.PRINT_DPI, EmployeeIdCardSpec.PRINT_DPI))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()
    printManager.print("GOLDEN SHINE - ${employees.size} employee IDs", EmployeeIdPrintAdapter(context, employees), attributes)
}

private class EmployeeIdPrintAdapter(private val context: Context, private val employees: List<EmployeeEntity>) : PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: android.os.Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(PrintDocumentInfo.Builder("golden-shine-employee-ids.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(employeeIdPrintPageCount(employees.size)).build(), oldAttributes != newAttributes)
    }

    override fun onWrite(pages: Array<out android.print.PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        val document = PdfDocument()
        try {
            val requestedPages = employeeIdRequestedPageIndices(employees.size, pages.map { it.start..it.end })
            requestedPages.forEach { pageIndex ->
                if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
                val pageWidth = 164
                val pageHeight = 258
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create())
                drawEmployeeCard(context, page.canvas, pageWidth.toFloat(), pageHeight.toFloat(), employees[pageIndex])
                document.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
            callback.onWriteFinished(requestedPages.map { android.print.PageRange(it, it) }.toTypedArray())
        } catch (error: Exception) {
            callback.onWriteFailed(error.message ?: "تعذر إنشاء بطاقات الموظفين")
        } finally { document.close() }
    }
}

private fun decodeSampledBitmap(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth && bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}
private fun ellipsizeCentered(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var shortened = text
    while (shortened.isNotEmpty() && paint.measureText("$shortened…") > maxWidth) shortened = shortened.dropLast(1)
    return if (shortened.isEmpty()) "" else "$shortened…"
}
private fun drawEmployeeCard(context: Context, canvas: Canvas, width: Float, height: Float, employee: EmployeeEntity) {
    val gold = android.graphics.Color.rgb(214, 177, 92)
    val lightGold = android.graphics.Color.rgb(241, 216, 143)
    val deepGold = android.graphics.Color.rgb(126, 94, 36)
    val black = android.graphics.Color.rgb(13, 14, 15)
    val panel = android.graphics.Color.rgb(28, 29, 30)
    val panelLight = android.graphics.Color.rgb(43, 40, 33)
    val ivory = android.graphics.Color.rgb(245, 242, 234)
    val white = android.graphics.Color.WHITE
    val muted = android.graphics.Color.rgb(190, 184, 168)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Artwork includes bleed; all critical content stays inside the 54 x 86 mm trim.
    val trimWidth = width * EmployeeIdCardSpec.WIDTH_MM / EmployeeIdCardSpec.DESIGN_WIDTH_MM
    val trimHeight = height * EmployeeIdCardSpec.HEIGHT_MM / EmployeeIdCardSpec.DESIGN_HEIGHT_MM
    val trimLeft = (width - trimWidth) / 2f
    val trimTop = (height - trimHeight) / 2f
    val trim = RectF(trimLeft, trimTop, trimLeft + trimWidth, trimTop + trimHeight)

    canvas.drawColor(ivory)
    // Dark premium face with a restrained metallic-gold frame.
    paint.color = black
    canvas.drawRoundRect(trim, 4f, 4f, paint)
    paint.color = gold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.15f
    canvas.drawRoundRect(RectF(trim.left + 1.8f, trim.top + 1.8f, trim.right - 1.8f, trim.bottom - 1.8f), 3f, 3f, paint)
    paint.style = Paint.Style.FILL

    // Distinctive brand band and geometric accent.
    val headerBottom = trim.top + trimHeight * 0.185f
    paint.color = panel
    canvas.drawRoundRect(RectF(trim.left, trim.top, trim.right, headerBottom + 4f), 4f, 4f, paint)
    canvas.drawRect(trim.left, headerBottom, trim.right, headerBottom + 4f, paint)
    paint.color = gold
    canvas.drawRect(trim.left, headerBottom, trim.right, headerBottom + 1.7f, paint)
    val accent = android.graphics.Path().apply {
        moveTo(trim.right - trimWidth * 0.23f, trim.top)
        lineTo(trim.right, trim.top)
        lineTo(trim.right, trim.top + trimHeight * 0.15f)
        close()
    }
    paint.color = android.graphics.Color.rgb(65, 52, 28)
    canvas.drawPath(accent, paint)
    paint.color = lightGold
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = trimWidth * 0.073f
    canvas.drawText(EmployeeIdCardSpec.COMPANY_NAME, trim.centerX(), trim.top + trimHeight * 0.082f, paint)
    paint.color = white
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    paint.textSize = trimWidth * 0.029f
    canvas.drawText("EMPLOYEE IDENTITY", trim.centerX(), trim.top + trimHeight * 0.135f, paint)

    // Employee portrait with a clean gold frame.
    val photoSize = trimWidth * 0.315f
    val photoLeft = trim.centerX() - photoSize / 2f
    val photoTop = trim.top + trimHeight * 0.215f
    val photoRect = RectF(photoLeft, photoTop, photoLeft + photoSize, photoTop + photoSize)
    paint.color = gold
    canvas.drawRoundRect(RectF(photoRect.left - 2f, photoRect.top - 2f, photoRect.right + 2f, photoRect.bottom + 2f), 4f, 4f, paint)
    paint.color = ivory
    canvas.drawRoundRect(photoRect, 3f, 3f, paint)

    val photo = employee.profilePhotoUri?.takeIf { it.isNotBlank() }?.let { uri ->
        runCatching { decodeSampledBitmap(context, Uri.parse(uri), 640, 640) }.getOrNull()
    } ?: runCatching {
        BitmapFactory.decodeResource(context.resources, R.drawable.golden)
    }.getOrNull()
    if (photo != null) {
        canvas.save()
        val path = android.graphics.Path().apply {
            addRoundRect(photoRect, 3f, 3f, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawBitmap(photo, null, photoRect, paint)
        canvas.restore()
        if (!photo.isRecycled) photo.recycle()
    } else {
        paint.color = panel
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = trimWidth * 0.035f
        canvas.drawText("GOLDEN SHINE", trim.centerX(), photoTop + photoSize * 0.54f, paint)
    }

    // Name and role are deliberately given separate visual hierarchy.
    val nameY = photoRect.bottom + trimHeight * 0.060f
    paint.textAlign = Paint.Align.CENTER
    paint.color = white
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = trimWidth * 0.057f
    canvas.drawText(ellipsizeCentered(employee.fullName.uppercase(java.util.Locale.ROOT), paint, trimWidth - 12f), trim.centerX(), nameY, paint)

    val job = employeeJobTitleEnglish(employee.jobTitle)
    paint.color = lightGold
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    paint.textSize = trimWidth * 0.040f
    canvas.drawText(ellipsizeCentered(job, paint, trimWidth - 12f), trim.centerX(), nameY + trimHeight * 0.050f, paint)

    // Employee number panel.
    val codeY = nameY + trimHeight * 0.112f
    val codePill = RectF(trim.left + trimWidth * 0.12f, codeY - trimHeight * 0.033f, trim.right - trimWidth * 0.12f, codeY + trimHeight * 0.018f)
    paint.color = panelLight
    canvas.drawRoundRect(codePill, 4f, 4f, paint)
    paint.color = gold
    canvas.drawRoundRect(RectF(codePill.left, codePill.top, codePill.left + 2.2f, codePill.bottom), 2f, 2f, paint)
    paint.color = muted
    paint.textSize = trimWidth * 0.026f
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    canvas.drawText("EMPLOYEE NO.", trim.centerX(), codeY - trimHeight * 0.004f, paint)
    paint.color = white
    paint.textSize = trimWidth * 0.038f
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    canvas.drawText(ellipsizeCentered(employee.code, paint, codePill.width() - 10f), trim.centerX(), codeY + trimHeight * 0.030f, paint)

    // QR is isolated in a white quiet zone for reliable scanning.
    val qrSize = trimWidth * 0.18f
    val qrTop = trim.top + trimHeight * 0.73f
    val qrRect = RectF(trim.centerX() - qrSize / 2f, qrTop, trim.centerX() + qrSize / 2f, qrTop + qrSize)
    paint.color = white
    canvas.drawRoundRect(RectF(qrRect.left - 3f, qrRect.top - 3f, qrRect.right + 3f, qrRect.bottom + 3f), 2f, 2f, paint)
    createEmployeeQr(employee.code)?.let { qr ->
        canvas.drawBitmap(qr, null, qrRect, paint)
        qr.recycle()
    }
    paint.color = muted
    paint.textSize = trimWidth * 0.025f
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    canvas.drawText("SCAN TO IDENTIFY", trim.centerX(), qrRect.bottom + trimHeight * 0.023f, paint)

    // Brand footer and subtle side rail.
    paint.color = gold
    canvas.drawRect(trim.left + trimWidth * 0.13f, trim.bottom - trimHeight * 0.048f, trim.right - trimWidth * 0.13f, trim.bottom - trimHeight * 0.048f + 0.7f, paint)
    paint.color = lightGold
    paint.textSize = trimWidth * 0.027f
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    canvas.drawText("GOLDEN SHINE  •  OFFICIAL STAFF", trim.centerX(), trim.bottom - trimHeight * 0.019f, paint)
    paint.color = gold
    canvas.drawRect(trim.left, trim.top + trimHeight * 0.21f, trim.left + 1.2f, trim.bottom - trimHeight * 0.08f, paint)

    // Trim guide is only a fine print guide at the finished edge.
    paint.color = android.graphics.Color.rgb(150, 145, 132)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 0.25f
    canvas.drawRect(trim, paint)
    paint.style = Paint.Style.FILL
}
