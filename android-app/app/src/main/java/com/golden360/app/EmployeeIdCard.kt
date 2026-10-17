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
    const val WIDTH_MM = 54
    const val HEIGHT_MM = 86
    const val COMPANY_NAME = "GOLDEN SHINE"
    const val QR_SIZE_PX = 360
}

@Composable
internal fun EmployeeIdCardScreen(
    employee: EmployeeEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val photo = produceState<Bitmap?>(initialValue = null, employee.profilePhotoUri) {
        value = withContext(Dispatchers.IO) {
            employee.profilePhotoUri?.let { uriText ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriText))?.use { stream -> BitmapFactory.decodeStream(stream) }
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
            Text("بطاقة هوية الموظف", color = SoftGold, fontSize = 21.sp, fontWeight = FontWeight.Bold)
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
                    if (photo.value != null) {
                        Image(photo.value!!.asImageBitmap(), contentDescription = "الصورة الشخصية للموظف", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
            onClick = { printEmployeeId(context, employee) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Text("طباعة بطاقة الموظف", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("رجوع", color = SoftGold)
        }
    }
}

internal fun createEmployeeQrMatrix(code: String) =
    QRCodeWriter().encode(code.also { require(it.isNotBlank()) }, BarcodeFormat.QR_CODE, 180, 180)

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

private fun printEmployeeId(context: Context, employee: EmployeeEntity) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val attributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize("GOLDEN_SHINE_ID", "GOLDEN SHINE ID", 2126, 3386))
        .setResolution(PrintAttributes.Resolution("GOLDEN_SHINE_ID_300", "300 dpi", 300, 300))
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()
    printManager.print(
        "GOLDEN SHINE - ${employee.code}",
        EmployeeIdPrintAdapter(context, employee),
        attributes
    )
}

private class EmployeeIdPrintAdapter(
    private val context: Context,
    private val employee: EmployeeEntity
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("employee-${employee.code}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build(),
            oldAttributes != newAttributes
        )
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        val document = PdfDocument()
        try {
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                return
            }
            val pageWidth = 153
            val pageHeight = 244
            val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            drawEmployeeCard(context, page.canvas, pageWidth.toFloat(), pageHeight.toFloat(), employee)
            document.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
            callback.onWriteFinished(arrayOf(android.print.PageRange(0, 0)))
        } catch (error: Exception) {
            callback.onWriteFailed(error.message ?: "تعذر إنشاء بطاقة الموظف")
        } finally {
            document.close()
        }
    }
}

private fun drawEmployeeCard(context: Context, canvas: Canvas, width: Float, height: Float, employee: EmployeeEntity) {
    val gold = android.graphics.Color.rgb(182, 143, 55)
    val black = android.graphics.Color.rgb(25, 25, 25)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, width, height, paint)
    paint.color = gold
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f
    canvas.drawRoundRect(RectF(4f, 4f, width - 4f, height - 4f), 5f, 5f, paint)
    paint.style = Paint.Style.FILL
    paint.color = black
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 11f
    canvas.drawText(EmployeeIdCardSpec.COMPANY_NAME, width / 2f, 19f, paint)

    val photo = employee.profilePhotoUri?.let { uri ->
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream) }.getOrNull()
    }
    if (photo != null) {
        val size = 52f
        val left = (width - size) / 2f
        val top = 27f
        canvas.save()
        val path = android.graphics.Path().apply { addRoundRect(RectF(left, top, left + size, top + size), 4f, 4f, android.graphics.Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(photo, null, RectF(left, top, left + size, top + size), paint)
        canvas.restore()
        photo.recycle()
    } else {
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawRoundRect(RectF(width / 2f - 26f, 27f, width / 2f + 26f, 79f), 4f, 4f, paint)
        paint.color = black
        paint.textSize = 6f
        canvas.drawText("NO PHOTO", width / 2f, 55f, paint)
    }

    paint.color = black
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 9f
    canvas.drawText(employee.fullName.take(26), width / 2f, 92f, paint)
    paint.color = gold
    paint.textSize = 7f
    val job = JobTitle.entries.firstOrNull { it.name == employee.jobTitle }?.arabicLabel ?: employee.jobTitle
    canvas.drawText(job.take(30), width / 2f, 104f, paint)
    paint.color = black
    paint.textSize = 7f
    canvas.drawText("Employee code: ${employee.code}", width / 2f, 116f, paint)

    createEmployeeQr(employee.code)?.let { qr ->
        val qrRect = RectF(width / 2f - 31f, 124f, width / 2f + 31f, 186f)
        canvas.drawBitmap(qr, null, qrRect, paint)
        qr.recycle()
    }
    paint.color = black
    paint.textSize = 7f
    canvas.drawText(employee.code, width / 2f, 197f, paint)
    paint.color = gold
    paint.textSize = 6f
    canvas.drawText("EMPLOYEE ID", width / 2f, 213f, paint)
}
