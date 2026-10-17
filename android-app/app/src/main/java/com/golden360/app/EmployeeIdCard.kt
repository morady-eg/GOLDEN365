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
import com.golden360.app.data.JobTitle
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
            employee.profilePhotoUri?.let { uriText ->
                runCatching {
decodeSampledBitmap(context, Uri.parse(uriText), 360, 360)
                }.getOrNull()
            }
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
            Text(if (employees.size == 1) "بطاقة هوية الموظف" else "معاينة طباعة ${employees.size} بطاقة", color = SoftGold, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Surface(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(0.78f).aspectRatio(EmployeeIdCardSpec.WIDTH_MM.toFloat() / EmployeeIdCardSpec.HEIGHT_MM.toFloat()),
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(EmployeeIdCardSpec.COMPANY_NAME, color = Color(0xFF151515), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Box(
                    modifier = Modifier.size(104.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center
                ) {
                    if (photo != null) {
                        Image(photo.asImageBitmap(), contentDescription = "الصورة الشخصية للموظف", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Text("بدون صورة", color = Color.DarkGray, fontSize = 12.sp)
                    }
                }
                Text(employee.fullName, color = Color(0xFF151515), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(JobTitle.entries.firstOrNull { it.name == employee.jobTitle }?.arabicLabel ?: employee.jobTitle, color = Color(0xFF6B5428), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("كود الموظف: ", color = Color.DarkGray, fontSize = 11.sp)
                    Text(employee.code, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (qr != null) {
                    Image(qr.asImageBitmap(), contentDescription = "باركود QR لكود الموظف", modifier = Modifier.size(94.dp))
                }
                Text(employee.code, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("EMPLOYEE ID", color = Color(0xFF8B6A28), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
        }
        Text("مقاس البطاقة: 54 × 86 مم • خلفية بيضاء", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Button(
            onClick = { printEmployeeIds(context, employees) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Text(if (employees.size == 1) "طباعة بطاقة الموظف" else "طباعة ${employees.size} بطاقة معًا", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("رجوع", color = SoftGold)
        }
    }
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
                val pageWidth = 153
                val pageHeight = 244
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
    val gold = android.graphics.Color.rgb(184, 145, 61)
    val deepGold = android.graphics.Color.rgb(135, 99, 31)
    val black = android.graphics.Color.rgb(24, 25, 27)
    val charcoal = android.graphics.Color.rgb(48, 48, 47)
    val ivory = android.graphics.Color.rgb(250, 249, 246)
    val muted = android.graphics.Color.rgb(103, 101, 96)
    val white = android.graphics.Color.WHITE
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Full artwork is 58 x 91 mm. The centered trim area is exactly 54 x 86 mm.
    val trimWidth = width * EmployeeIdCardSpec.WIDTH_MM / EmployeeIdCardSpec.DESIGN_WIDTH_MM
    val trimHeight = height * EmployeeIdCardSpec.HEIGHT_MM / EmployeeIdCardSpec.DESIGN_HEIGHT_MM
    val trimLeft = (width - trimWidth) / 2f
    val trimTop = (height - trimHeight) / 2f
    val trim = RectF(trimLeft, trimTop, trimLeft + trimWidth, trimTop + trimHeight)

    // Bleed background and restrained gold edge accents.
    canvas.drawColor(white)
    paint.color = ivory
    canvas.drawRect(0f, 0f, width, height, paint)
    paint.color = gold
    canvas.drawRect(0f, 0f, width, height * 0.018f, paint)
    canvas.drawRect(0f, height * 0.982f, width, height, paint)

    // Card face.
    paint.color = ivory
    canvas.drawRoundRect(trim, 3.5f, 3.5f, paint)
    paint.color = gold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 0.8f
    canvas.drawRoundRect(RectF(trim.left + 1.8f, trim.top + 1.8f, trim.right - 1.8f, trim.bottom - 1.8f), 3f, 3f, paint)
    paint.style = Paint.Style.FILL

    // Premium black header with a slim gold rule.
    val headerBottom = trim.top + trimHeight * 0.205f
    paint.color = black
    canvas.drawRoundRect(RectF(trim.left, trim.top, trim.right, headerBottom + 4f), 3.5f, 3.5f, paint)
    canvas.drawRect(trim.left, headerBottom, trim.right, headerBottom + 4f, paint)
    paint.color = gold
    canvas.drawRect(trim.left, headerBottom, trim.right, headerBottom + 1.5f, paint)

    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.color = gold
    paint.textSize = trimWidth * 0.072f
    canvas.drawText(EmployeeIdCardSpec.COMPANY_NAME, width / 2f, trim.top + trimHeight * 0.095f, paint)
    paint.color = white
    paint.textSize = trimWidth * 0.031f
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    canvas.drawText("EMPLOYEE IDENTIFICATION", width / 2f, trim.top + trimHeight * 0.145f, paint)

    // Portrait frame and optional photo.
    val photoSize = trimWidth * 0.36f
    val photoLeft = (width - photoSize) / 2f
    val photoTop = trim.top + trimHeight * 0.245f
    val photoRect = RectF(photoLeft, photoTop, photoLeft + photoSize, photoTop + photoSize)
    paint.color = gold
    canvas.drawRoundRect(RectF(photoRect.left - 2f, photoRect.top - 2f, photoRect.right + 2f, photoRect.bottom + 2f), 5f, 5f, paint)
    paint.color = android.graphics.Color.rgb(232, 229, 221)
    canvas.drawRoundRect(photoRect, 4f, 4f, paint)

    val photo = employee.profilePhotoUri?.let { uri ->
        runCatching { decodeSampledBitmap(context, Uri.parse(uri), 640, 640) }.getOrNull()
    }
    if (photo != null) {
        canvas.save()
        val path = android.graphics.Path().apply {
            addRoundRect(photoRect, 4f, 4f, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawBitmap(photo, null, photoRect, paint)
        canvas.restore()
        photo.recycle()
    } else {
        paint.color = charcoal
        paint.textSize = trimWidth * 0.036f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        canvas.drawText("GOLDEN SHINE", width / 2f, photoTop + photoSize * 0.54f, paint)
        paint.color = muted
        paint.textSize = trimWidth * 0.025f
        canvas.drawText("EMPLOYEE PHOTO", width / 2f, photoTop + photoSize * 0.64f, paint)
    }

    val nameY = photoRect.bottom + trimHeight * 0.062f
    paint.color = black
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    paint.textSize = trimWidth * 0.059f
    canvas.drawText(ellipsizeCentered(employee.fullName, paint, trimWidth - 13f), width / 2f, nameY, paint)

    val job = JobTitle.entries.firstOrNull { it.name == employee.jobTitle }?.arabicLabel ?: employee.jobTitle
    paint.color = deepGold
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    paint.textSize = trimWidth * 0.043f
    canvas.drawText(ellipsizeCentered(job, paint, trimWidth - 13f), width / 2f, nameY + trimHeight * 0.052f, paint)

    // Gold pill for the employee code.
    val codeY = nameY + trimHeight * 0.112f
    val codePill = RectF(trim.left + trimWidth * 0.13f, codeY - trimHeight * 0.034f, trim.right - trimWidth * 0.13f, codeY + trimHeight * 0.018f)
    paint.color = android.graphics.Color.rgb(239, 232, 215)
    canvas.drawRoundRect(codePill, 5f, 5f, paint)
    paint.color = black
    paint.textSize = trimWidth * 0.037f
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    canvas.drawText("ID  \${ellipsizeCentered(employee.code, paint, codePill.width() - 10f)}", width / 2f, codeY, paint)

    // QR code kept crisp and surrounded by a quiet zone for reliable scanning.
    val qrTop = trim.top + trimHeight * 0.725f
    val qrSize = trimWidth * 0.235f
    val qrRect = RectF(width / 2f - qrSize / 2f, qrTop, width / 2f + qrSize / 2f, qrTop + qrSize)
    paint.color = white
    canvas.drawRect(RectF(qrRect.left - 3f, qrRect.top - 3f, qrRect.right + 3f, qrRect.bottom + 3f), paint)
    createEmployeeQr(employee.code)?.let { qr ->
        canvas.drawBitmap(qr, null, qrRect, paint)
        qr.recycle()
    }

    paint.color = muted
    paint.textSize = trimWidth * 0.028f
    paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    canvas.drawText("SCAN TO VERIFY ID", width / 2f, qrRect.bottom + trimHeight * 0.032f, paint)

    // Fine gold divider and discreet footer.
    val dividerY = trim.bottom - trimHeight * 0.075f
    paint.color = gold
    canvas.drawRect(trim.left + trimWidth * 0.17f, dividerY, trim.right - trimWidth * 0.17f, dividerY + 0.65f, paint)
    paint.color = deepGold
    paint.textSize = trimWidth * 0.031f
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    canvas.drawText("GOLDEN SHINE  •  OFFICIAL STAFF", width / 2f, trim.bottom - trimHeight * 0.035f, paint)

    // Subtle trim guide remains outside the finished card after cutting.
    paint.color = android.graphics.Color.rgb(195, 190, 178)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 0.35f
    canvas.drawRect(trim, paint)
    paint.style = Paint.Style.FILL
}
