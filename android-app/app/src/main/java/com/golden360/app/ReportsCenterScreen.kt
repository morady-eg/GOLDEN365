package com.golden360.app

import android.content.Intent
import java.io.File
import androidx.core.content.FileProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
    var monthPickerOpen by remember { mutableStateOf(false) }
    var employeeExpanded by remember { mutableStateOf(false) }
    var selectedEmployeeId by remember { mutableStateOf("ALL") }

    LaunchedEffect(employees, month, selectedKind, selectedEmployeeId) {
        loading = true
        error = null
        payload = null
        try {
            require(month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) {
                "اكتب الشهر بصيغة صحيحة مثل 2026-10"
            }
            val report = withContext(Dispatchers.IO) {
                val allEmployees = EmployeeDatabase.get(context).employeeDao().getAllEmployees()
                val reportEmployees = if (selectedEmployeeId == "ALL") allEmployees else allEmployees.filter { it.id.toString() == selectedEmployeeId }
                buildReportPayload(reportEmployees, dao, month, selectedKind)
            }
            payload = report
        } catch (failure: Exception) {
            error = failure.message ?: "تعذر تجهيز التقرير"
        } finally {
            loading = false
        }
    }

    if (monthPickerOpen) {
        val initialMonthMillis = runCatching {
            YearMonth.parse(month).atDay(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMonthMillis)
        DatePickerDialog(
            onDismissRequest = { monthPickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        month = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().let { YearMonth.from(it).toString() }
                    }
                    monthPickerOpen = false
                }) { Text("اختيار الشهر") }
            },
            dismissButton = {
                TextButton(onClick = { monthPickerOpen = false }) { Text("إلغاء") }
            }
        ) {
            DatePicker(state = pickerState, title = { Text("اختر أي يوم من الشهر المطلوب") })
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
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("شهر التقرير") },
                    placeholder = { Text("اختر الشهر من التقويم") },
                    trailingIcon = {
                        IconButton(onClick = { monthPickerOpen = true }) {
                            Icon(Icons.Default.Assessment, contentDescription = "اختيار شهر التقرير", tint = SoftGold)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().clickable { monthPickerOpen = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = reportsFieldColors()
                )
                ExposedDropdownMenuBox(
                    expanded = employeeExpanded,
                    onExpandedChange = { employeeExpanded = !employeeExpanded }
                ) {
                    OutlinedTextField(
                        value = if (selectedEmployeeId == "ALL") "جميع الموظفين" else employees.firstOrNull { it.id.toString() == selectedEmployeeId }?.let { "${it.fullName} • ${it.code}" } ?: "جميع الموظفين",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نطاق الكشف") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = employeeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = reportsFieldColors()
                    )
                    ExposedDropdownMenu(expanded = employeeExpanded, onDismissRequest = { employeeExpanded = false }) {
                        DropdownMenuItem(text = { Text("كشف مجمع — جميع الموظفين") }, onClick = { selectedEmployeeId = "ALL"; employeeExpanded = false })
                        employees.forEach { employee ->
                            DropdownMenuItem(text = { Text("${employee.fullName} • ${employee.code}") }, onClick = { selectedEmployeeId = employee.id.toString(); employeeExpanded = false })
                        }
                    }
                }
                Text("يمكن استخراج كشف لموظف واحد أو كشف مجمع. تُعرض الأسماء والمسميات كما هي مسجلة في ملف الموظف، بالعربية أو الإنجليزية.", color = Muted, fontSize = 11.sp)
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
                        val reportToShare = report
                        val reportTitle = selectedKind.label
                        val reportMonth = month
                        val employeeScope = if (selectedEmployeeId == "ALL") "جميع الموظفين" else
                            employees.firstOrNull { it.id.toString() == selectedEmployeeId }?.fullName ?: "موظف"
                        scope.launch {
                            error = null
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val cacheDir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
                                    cacheDir.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }
                                        ?.forEach { it.delete() }
                                    val safeKind = selectedKind.fileName.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                    val pdfFile = File(cacheDir, "GOLDEN360_${safeKind}_${reportMonth}_${System.currentTimeMillis()}.pdf")
                                    ReportsPdf.write(pdfFile, reportTitle, reportMonth, employeeScope, reportToShare.csvRows)
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                }
                            }
                            result.onSuccess { uri ->
                                runCatching {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = android.content.ClipData.newUri(context.contentResolver, "GOLDEN 360 PDF report", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "مشاركة كشف PDF من GOLDEN 360"))
                                }.onFailure { failure ->
                                    error = "تم تجهيز PDF لكن تعذرت المشاركة: ${failure.message ?: "خطأ غير معروف"}"
                                }
                            }.onFailure { failure ->
                                error = "تعذر تجهيز ملف PDF: ${failure.message ?: "خطأ غير معروف"}"
                            }
                        }
                    },
                    enabled = !loading && report.csvRows.size > 1,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text("مشاركة الكشف PDF مباشرة", fontWeight = FontWeight.Bold)
                }
                Text(
                    "يتم إنشاء ملف PDF مؤقتًا داخل مساحة التطبيق ثم فتح قائمة المشاركة مباشرة؛ لا يلزم حفظه يدويًا في الجهاز. يمكنك إرساله عبر واتساب أو البريد أو أي تطبيق يدعم PDF.",
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
                    DisplayDateTime.formatTransaction(transaction.createdAtEpochMillis, ZoneId.systemDefault())
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
            val datedAbsenceDates = effectiveDatedAbsenceDates(employee, month, absences)
            val legacyDays = attendance?.legacyAbsentDays ?: 0
            val days = Math.addExact(legacyDays, datedAbsenceDates.size)
            totalDays = Math.addExact(totalDays, days.toLong())
            rows += listOf(employee.fullName, employee.code, month, days.toString(),
                datedAbsenceDates.joinToString("; ") { it.toString() }, legacyDays.toString())
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
        val datedAbsenceDates = effectiveDatedAbsenceDates(employee, month, absences)
        val absentDays = Math.addExact(attendance?.legacyAbsentDays ?: 0, datedAbsenceDates.size)
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

private fun effectiveDatedAbsenceDates(
    employee: EmployeeEntity,
    month: String,
    absences: List<com.golden360.app.data.DailyAbsenceEntity>
): List<java.time.LocalDate> = AttendancePolicy.effectiveAbsenceDatesForMonth(
    month = YearMonth.parse(month),
    weeklyOffDays = AttendancePolicy.parseWeeklyOffDays(employee.weeklyOffDays),
    absentDates = absences.map { java.time.LocalDate.parse(it.dateKey) }.toSet(),
    hireDate = employee.hireDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
    workStatus = employee.workStatus,
    statusEffectiveDate = employee.statusEffectiveDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
    suspensionPeriods = employee.suspensionPeriods
)

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
