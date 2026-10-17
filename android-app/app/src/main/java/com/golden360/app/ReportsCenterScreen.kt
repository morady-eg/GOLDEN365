package com.golden360.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.AttendancePolicy
import com.golden360.app.data.EmployeeEntity
import com.golden360.app.data.EmployeeDatabase
import com.golden360.app.data.PayrollSummaryCalculator
import com.golden360.app.data.PayrollTransactionDao
import com.golden360.app.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ReportKind(val label: String, val fileName: String) {
    PAYROLL("كشف الرواتب والإجماليات", "payroll"),
    ATTENDANCE("تقرير الحضور والغياب", "attendance"),
    ADVANCES("تقرير السلف", "advances"),
    PENALTIES("تقرير الجزاءات", "penalties"),
    BONUSES("تقرير المكافآت", "bonuses"),
    OVERTIME("تقرير الورديات الإضافية", "overtime");

    fun transactionType(): TransactionType? = when (this) {
        ADVANCES -> TransactionType.ADVANCE
        PENALTIES -> TransactionType.PENALTY
        BONUSES -> TransactionType.BONUS
        OVERTIME -> TransactionType.OVERTIME
        else -> null
    }
}

private data class ReportPayload(
    val csvRows: List<List<String>>,
    val recordCount: Int,
    val totalPiasters: Long? = null,
    val totalAbsenceDays: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportsCenterScreen(
    employees: List<EmployeeEntity>,
    dao: PayrollTransactionDao,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
    var selectedKind by remember { mutableStateOf(ReportKind.PAYROLL) }
    var kindExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var payload by remember { mutableStateOf<ReportPayload?>(null) }
    var shareAfterSave by remember { mutableStateOf(true) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val report = payload
        if (uri != null && report != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = requireNotNull(context.contentResolver.openOutputStream(uri)) {
                            "تعذر فتح ملف الحفظ"
                        }
                        output.use { it.write(ReportsCsv.toCsv(report.csvRows).toByteArray(Charsets.UTF_8)) }
                    }
                    if (shareAfterSave) shareCsvFile(context, uri)
                }
                error = result.exceptionOrNull()?.let {
                    "تعذر حفظ/مشاركة التقرير: ${it.message ?: "خطأ غير معروف"}"
                }
            }
        }
    }

    LaunchedEffect(employees, month, selectedKind) {
        loading = true
        error = null
        payload = null
        try {
            require(month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) {
                "اكتب الشهر بصيغة صحيحة مثل 2026-10"
            }
            val report = withContext(Dispatchers.IO) {
                val allEmployees = EmployeeDatabase.get(context).employeeDao().getAllEmployees()
                buildReportPayload(allEmployees, dao, month, selectedKind)
            }
            payload = report
        } catch (failure: Exception) {
            error = failure.message ?: "تعذر تجهيز التقرير"
        } finally {
            loading = false
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Assessment, contentDescription = null, tint = SoftGold)
            Column(Modifier.weight(1f)) {
                Text("مركز التقارير", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("تقارير شهرية فعلية قابلة للحفظ والمشاركة وفتحها في Excel", color = Muted, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = SoftGold)
                Text("رجوع", color = SoftGold)
            }
        }

        Surface(
            color = Panel,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF403625)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إعداد التقرير", color = SoftGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                ExposedDropdownMenuBox(
                    expanded = kindExpanded,
                    onExpandedChange = { kindExpanded = !kindExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedKind.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نوع التقرير") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kindExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = reportsFieldColors()
                    )
                    ExposedDropdownMenu(expanded = kindExpanded, onDismissRequest = { kindExpanded = false }) {
                        ReportKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(kind.label) },
                                onClick = { selectedKind = kind; kindExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it },
                    label = { Text("الشهر (YYYY-MM)") },
                    placeholder = { Text("2026-10") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = reportsFieldColors()
                )
                Text(
                    "التقرير يُبنى من قاعدة بيانات الرواتب والحضور المسجلة على هذا الجهاز. الحركات الملغاة تظهر في التقرير بحالتها ولا تدخل في إجمالي المبالغ الفعالة.",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }

        when {
            loading -> Text("جارٍ تجهيز بيانات التقرير…", color = SoftGold)
            error != null -> Text(error.orEmpty(), color = Color(0xFFFF8A80), fontSize = 13.sp)
            payload != null -> {
                val report = payload!!
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportSummaryTile("عدد السجلات", report.recordCount.toString(), Modifier.weight(1f))
                    if (report.totalPiasters != null) {
                        ReportSummaryTile("إجمالي المبالغ", "${ReportsCsv.formatPiasters(report.totalPiasters)} ج.م", Modifier.weight(1f))
                    } else if (report.totalAbsenceDays != null) {
                        ReportSummaryTile("إجمالي أيام الغياب", report.totalAbsenceDays.toString(), Modifier.weight(1f))
                    }
                }
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF403625)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("معاينة التقرير", color = SoftGold, fontWeight = FontWeight.Bold)
                        Text(report.csvRows.firstOrNull()?.joinToString(" • ").orEmpty(), color = Muted, fontSize = 10.sp)
                        report.csvRows.drop(1).take(8).forEach { row ->
                            Text(
                                row.take(4).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { "—" },
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                        if (report.recordCount > 8) {
                            Text("يظهر هنا أول 8 سجلات فقط؛ ملف التصدير يحتوي على جميع السجلات.", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                Button(
                    onClick = {
                        shareAfterSave = true
                        exportLauncher.launch("GOLDEN360_${selectedKind.fileName}_$month.csv")
                    },
                    enabled = !loading && report.csvRows.size > 1,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text("حفظ ومشاركة تقرير Excel (CSV)", fontWeight = FontWeight.Bold)
                }
                Text(
                    "يتم حفظ الملف بصيغة CSV متوافقة مع Excel مع دعم النص العربي. يمكن فتحه في Excel ثم حفظه بصيغة XLSX.",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ReportSummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(value, color = SoftGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private suspend fun buildReportPayload(
    employees: List<EmployeeEntity>,
    dao: PayrollTransactionDao,
    month: String,
    kind: ReportKind
): ReportPayload {
    val type = kind.transactionType()
    if (type != null) {
        val rows = mutableListOf(
            listOf("اسم الموظف", "كود الموظف", "رقم المرجع", "الشهر", "نوع الحركة", "المبلغ (ج.م)", "الكمية", "ملاحظات", "تاريخ التسجيل", "الحالة", "سبب الإلغاء", "مكان الوردية", "تاريخ الوردية", "الفترة")
        )
        var total = 0L
        var count = 0
        employees.forEach { employee ->
            dao.getForEmployeeMonth(employee.id, month).filter { it.type == type.name }.forEach { transaction ->
                val active = transaction.voidedAtEpochMillis == null
                if (active) total = Math.addExact(total, transaction.amountPiasters)
                count++
                val created = runCatching {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(transaction.createdAtEpochMillis))
                }.getOrDefault("")
                rows += listOf(
                    employee.fullName, employee.code, transaction.reference, transaction.monthKey,
                    type.arabicLabel, ReportsCsv.formatPiasters(transaction.amountPiasters),
                    transaction.quantity.toString(), transaction.note, created,
                    if (active) "فعالة" else "ملغاة",
                    transaction.voidReason.orEmpty(), transaction.overtimePlace,
                    transaction.overtimeDate, transaction.overtimeTime
                )
            }
        }
        return ReportPayload(rows, count, total)
    }

    if (kind == ReportKind.ATTENDANCE) {
        val rows = mutableListOf(listOf("اسم الموظف", "كود الموظف", "الشهر", "أيام الغياب", "تواريخ الغياب", "أيام الغياب القديمة"))
        var totalDays = 0L
        employees.forEach { employee ->
            val attendance = dao.getAttendance(employee.id, month)
            val absences = dao.getDailyAbsences(employee.id, month)
            val weeklyOffDays = AttendancePolicy.parseWeeklyOffDays(employee.weeklyOffDays)
            val datedAbsences = absences.filter { row ->
                !AttendancePolicy.isWeeklyOff(java.time.LocalDate.parse(row.dateKey), weeklyOffDays)
            }
            val legacyDays = attendance?.legacyAbsentDays ?: 0
            val days = legacyDays + datedAbsences.size
            totalDays += days
            rows += listOf(employee.fullName, employee.code, month, days.toString(),
                datedAbsences.joinToString("; ") { it.dateKey }, legacyDays.toString())
        }
        return ReportPayload(rows, employees.size, totalAbsenceDays = totalDays.toInt())
    }

    val rows = mutableListOf(listOf(
        "اسم الموظف", "كود الموظف", "الشهر", "الراتب الأساسي (ج.م)", "أيام الغياب",
        "خصم الغياب (ج.م)", "السلف (ج.م)", "الجزاءات (ج.م)", "الورديات الإضافية (ج.م)",
        "المكافآت (ج.م)", "صافي المستحق (ج.م)", "حالة الكشف"
    ))
    var totalBase = 0L
    var totalAbsence = 0L
    var totalAdvances = 0L
    var totalPenalties = 0L
    var totalOvertime = 0L
    var totalBonuses = 0L
    var totalNet = 0L
    employees.forEach { employee ->
        val saved = dao.getPayrollSnapshot(employee.id, month)
        val attendance = dao.getAttendance(employee.id, month)
        val absences = dao.getDailyAbsences(employee.id, month)
        val weeklyOffDays = AttendancePolicy.parseWeeklyOffDays(employee.weeklyOffDays)
        val absentDays = (attendance?.legacyAbsentDays ?: 0) + absences.count { row ->
            !AttendancePolicy.isWeeklyOff(java.time.LocalDate.parse(row.dateKey), weeklyOffDays)
        }
        val amounts = TransactionType.entries.associateWith { transactionType ->
            dao.sumForEmployeeMonth(employee.id, month, transactionType.name)
        }
        val base: Long
        val days: Int
        val absence: Long
        val advances: Long
        val penalties: Long
        val overtime: Long
        val bonuses: Long
        val net: Long
        if (saved != null) {
            base = saved.baseSalaryPiasters
            days = saved.absentDays
            absence = saved.absenceDeductionPiasters
            advances = saved.advancesPiasters
            penalties = saved.penaltiesPiasters
            overtime = saved.overtimePiasters
            bonuses = saved.bonusesPiasters
            net = saved.netSalaryPiasters
        } else {
            val summary = PayrollSummaryCalculator.calculate(
                baseSalaryPiasters = employee.baseSalaryPiasters,
                absentDays = absentDays,
                penaltiesPiasters = amounts[TransactionType.PENALTY] ?: 0L,
                advancesPiasters = amounts[TransactionType.ADVANCE] ?: 0L,
                overtimePiasters = amounts[TransactionType.OVERTIME] ?: 0L,
                bonusesPiasters = amounts[TransactionType.BONUS] ?: 0L
            )
            base = employee.baseSalaryPiasters
            days = absentDays
            absence = summary.absenceDeductionPiasters
            advances = amounts[TransactionType.ADVANCE] ?: 0L
            penalties = amounts[TransactionType.PENALTY] ?: 0L
            overtime = amounts[TransactionType.OVERTIME] ?: 0L
            bonuses = amounts[TransactionType.BONUS] ?: 0L
            net = summary.netSalaryPiasters
        }
        totalBase = Math.addExact(totalBase, base)
        totalAbsence = Math.addExact(totalAbsence, absence)
        totalAdvances = Math.addExact(totalAdvances, advances)
        totalPenalties = Math.addExact(totalPenalties, penalties)
        totalOvertime = Math.addExact(totalOvertime, overtime)
        totalBonuses = Math.addExact(totalBonuses, bonuses)
        totalNet = Math.addExact(totalNet, net)
        rows += listOf(
            employee.fullName, employee.code, month, ReportsCsv.formatPiasters(base), days.toString(),
            ReportsCsv.formatPiasters(absence), ReportsCsv.formatPiasters(advances),
            ReportsCsv.formatPiasters(penalties), ReportsCsv.formatPiasters(overtime),
            ReportsCsv.formatPiasters(bonuses), ReportsCsv.formatPiasters(net),
            if (saved != null) "معتمد" else if (net < 0) "يتطلب مراجعة" else "مسودة"
        )
    }
    rows += listOf("الإجمالي", "", month,
        ReportsCsv.formatPiasters(totalBase), "",
        ReportsCsv.formatPiasters(totalAbsence), ReportsCsv.formatPiasters(totalAdvances),
        ReportsCsv.formatPiasters(totalPenalties), ReportsCsv.formatPiasters(totalOvertime),
        ReportsCsv.formatPiasters(totalBonuses), ReportsCsv.formatPiasters(totalNet), "")
    return ReportPayload(rows, employees.size, totalNet)
}

private fun shareCsvFile(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "GOLDEN 360 CSV report", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة تقرير GOLDEN 360"))
}

@Composable
private fun reportsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold,
    unfocusedBorderColor = Color(0xFF4A4A4A),
    focusedLabelColor = SoftGold,
    unfocusedLabelColor = Muted,
    cursorColor = Gold,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)
