package com.golden360.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.AttendanceDayStatus
import com.golden360.app.data.AttendancePolicy
import com.golden360.app.data.EmployeeEntity
import com.golden360.app.data.PayrollTransactionDao
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttendanceScreen(
    employees: List<EmployeeEntity>,
    dao: PayrollTransactionDao,
    modifier: Modifier = Modifier
) {
    var selectedEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var employeeExpanded by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var absentDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var legacyAbsentDays by remember { mutableStateOf(0) }
    var payrollApproved by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var clockNow by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            clockNow = java.time.LocalDateTime.now()
            delay(30_000L)
        }
    }

    LaunchedEffect(employees) {
        if (selectedEmployee == null || employees.none { it.id == selectedEmployee?.id }) {
            selectedEmployee = employees.firstOrNull()
        }
    }

    LaunchedEffect(selectedEmployee?.id, month) {
        val employeeId = selectedEmployee?.id
        absentDates = emptySet()
        legacyAbsentDays = 0
        payrollApproved = false
        error = null
        if (employeeId != null) {
            loading = true
            runCatching {
                val rows = dao.getDailyAbsences(employeeId, month.toString())
                val monthly = dao.getAttendance(employeeId, month.toString())
                val snapshot = dao.getPayrollSnapshot(employeeId, month.toString())
                absentDates = rows.mapNotNull { row -> runCatching { LocalDate.parse(row.dateKey) }.getOrNull() }.toSet()
                legacyAbsentDays = monthly?.legacyAbsentDays ?: 0
                payrollApproved = snapshot != null
            }.onFailure { error = "تعذر تحميل سجل الحضور: ${it.message ?: "خطأ غير معروف"}" }
            loading = false
        }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("ar")) }
    val weeklyOffDays = AttendancePolicy.parseWeeklyOffDays(selectedEmployee?.weeklyOffDays.orEmpty())
    val today = clockNow.toLocalDate()
    val monthDates = remember(month) { (1..month.lengthOfMonth()).map { month.atDay(it) } }

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = SoftGold, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text("الحضور والغياب", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("الحضور تلقائي حسب الجدول؛ سجّل الغياب فقط", color = Muted, fontSize = 12.sp)
            }
        }
        if (employees.isEmpty()) {
            Text("أضف ملف موظف أولًا لتسجيل الحضور والغياب.", color = Muted)
        } else {
            ExposedDropdownMenuBox(expanded = employeeExpanded, onExpandedChange = { employeeExpanded = !employeeExpanded }) {
                OutlinedTextField(
                    value = selectedEmployee?.let { "${it.fullName} — ${it.code}" }.orEmpty(),
                    onValueChange = {}, readOnly = true, label = { Text("الموظف") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = employeeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp), colors = attendanceFieldColors()
                )
                ExposedDropdownMenu(expanded = employeeExpanded, onDismissRequest = { employeeExpanded = false }) {
                    employees.forEach { employee ->
                        DropdownMenuItem(
                            text = { Text("${employee.fullName} — ${employee.code}") },
                            onClick = { selectedEmployee = employee; employeeExpanded = false }
                        )
                    }
                }
            }
            Surface(
                color = Panel, shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF4A3D24))
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("الفترة", color = Muted, fontSize = 11.sp)
                            Text(month.toString(), color = SoftGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { month = month.minusMonths(1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "الشهر السابق", tint = SoftGold)
                        }
                        IconButton(onClick = { month = month.plusMonths(1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "الشهر التالي", tint = SoftGold)
                        }
                    }
                    Text("بداية الوردية: ${selectedEmployee?.shiftStartTime?.let(::formatShiftTimeForDisplay) ?: "—"}", color = Color.White, fontSize = 12.sp)
                    Text(
                        "الإجازة الأسبوعية المدفوعة: " + if (weeklyOffDays.isEmpty()) "لم تُحدد" else weeklyOffDays.sortedBy { it.value }.joinToString("، ") { day ->
                            when (day) {
                                java.time.DayOfWeek.SATURDAY -> "السبت"
                                java.time.DayOfWeek.SUNDAY -> "الأحد"
                                java.time.DayOfWeek.MONDAY -> "الإثنين"
                                java.time.DayOfWeek.TUESDAY -> "الثلاثاء"
                                java.time.DayOfWeek.WEDNESDAY -> "الأربعاء"
                                java.time.DayOfWeek.THURSDAY -> "الخميس"
                                java.time.DayOfWeek.FRIDAY -> "الجمعة"
                            }
                        },
                        color = Muted, fontSize = 12.sp
                    )
                    HorizontalDivider(color = Color(0xFF4A3D24))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("غياب مسجل بالتاريخ", color = Muted, fontSize = 11.sp)
                            Text(AttendancePolicy.absentDaysForMonth(
                                month, weeklyOffDays, absentDates,
                                selectedEmployee?.hireDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                                selectedEmployee?.workStatus ?: "ACTIVE",
                                selectedEmployee?.statusEffectiveDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                                selectedEmployee?.suspensionPeriods.orEmpty()
                            ).toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        if (legacyAbsentDays > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("غياب قديم غير مؤرخ", color = Muted, fontSize = 11.sp)
                                Text(legacyAbsentDays.toString(), color = SoftGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (payrollApproved) Text("تم اعتماد كشف هذا الشهر؛ سجل الحضور مقفول.", color = SoftGold, fontSize = 12.sp)
                    else Text("أيام العمل السابقة واليوم تُعد حاضرة تلقائيًا ما لم تسجّل غيابًا. لا يلزم تسجيل الانصراف.", color = Muted, fontSize = 11.sp)
                }
            }
            if (error != null) Text(error!!, color = Color(0xFFFF8A80), fontSize = 12.sp)
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(monthDates, key = { it.toString() }) { date ->
                        val hireDate = selectedEmployee?.hireDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        val statusEffectiveDate = selectedEmployee?.statusEffectiveDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        val status = AttendancePolicy.status(
                            date, weeklyOffDays, absentDates, today, clockNow.toLocalTime(),
                            selectedEmployee?.shiftStartTime ?: "08:00", hireDate,
                            selectedEmployee?.workStatus ?: "ACTIVE", statusEffectiveDate,
                            selectedEmployee?.suspensionPeriods.orEmpty()
                        )
                        val statusLabel = when (status) {
                            AttendanceDayStatus.PAID_WEEKLY_OFF -> "إجازة أسبوعية مدفوعة"
                            AttendanceDayStatus.MANUALLY_ABSENT -> "غياب مسجل — يُخصم حسب سياسة المرتب"
                            AttendanceDayStatus.AUTOMATIC_PRESENT -> "حاضر تلقائيًا"
                            AttendanceDayStatus.SCHEDULED -> "يوم عمل مجدول"
                            AttendanceDayStatus.NOT_YET_HIRED -> "قبل تاريخ التعيين"
                            AttendanceDayStatus.TEMPORARILY_SUSPENDED -> "إيقاف مؤقت — لا يُحتسب حضور أو غياب"
                            AttendanceDayStatus.EMPLOYMENT_ENDED -> "انتهى العمل — لا يُحتسب حضور بعد هذا التاريخ"
                        }
                        Surface(
                            color = Panel, shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, when (status) {
                                AttendanceDayStatus.MANUALLY_ABSENT -> Color(0xFF9A4A42)
                                AttendanceDayStatus.PAID_WEEKLY_OFF -> Color(0xFF4A673F)
                                AttendanceDayStatus.TEMPORARILY_SUSPENDED -> Gold
                                AttendanceDayStatus.EMPLOYMENT_ENDED -> Color(0xFF9A4A42)
                                else -> Color(0xFF403625)
                            })
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(date.format(dateFormatter), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(statusLabel, color = when (status) {
                                        AttendanceDayStatus.MANUALLY_ABSENT -> Color(0xFFFFA39A)
                                        AttendanceDayStatus.PAID_WEEKLY_OFF -> Color(0xFFA7D6A0)
                                AttendanceDayStatus.TEMPORARILY_SUSPENDED -> SoftGold
                                AttendanceDayStatus.EMPLOYMENT_ENDED -> Color(0xFFFFA39A)
                                        else -> Muted
                                    }, fontSize = 11.sp)
                                }
                                if ((status != AttendanceDayStatus.NOT_YET_HIRED || date in absentDates) &&
                                    (status !in setOf(AttendanceDayStatus.TEMPORARILY_SUSPENDED, AttendanceDayStatus.EMPLOYMENT_ENDED) || date in absentDates) &&
                                    (status != AttendanceDayStatus.PAID_WEEKLY_OFF || date in absentDates) &&
                                    !date.isAfter(today) &&
                                    (date in absentDates || status != AttendanceDayStatus.SCHEDULED)) {
                                    TextButton(
                                        enabled = !payrollApproved,
                                        onClick = {
                                            val employee = selectedEmployee ?: return@TextButton
                                            val markAbsent = date !in absentDates
                                            scope.launch {
                                                runCatching {
                                                    dao.setDailyAbsence(employee.id, date.toString(), month.toString(), employee.weeklyOffDays, markAbsent, employee.hireDate, employee.workStatus, employee.statusEffectiveDate, employee.suspensionPeriods)
                                                    val rows = dao.getDailyAbsences(employee.id, month.toString())
                                                    val monthly = dao.getAttendance(employee.id, month.toString())
                                                    absentDates = rows.map { LocalDate.parse(it.dateKey) }.toSet()
                                                    legacyAbsentDays = monthly?.legacyAbsentDays ?: 0
                                                }.onFailure { error = it.message ?: "تعذر تحديث الغياب" }
                                            }
                                        }
                                    ) {
                                        Text(if (date in absentDates) "إلغاء الغياب" else "تسجيل غياب", color = if (date in absentDates) SoftGold else Color(0xFFFFA39A), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun attendanceFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF4A4A4A),
    focusedLabelColor = SoftGold, unfocusedLabelColor = Muted,
    cursorColor = Gold, focusedTextColor = Color.White, unfocusedTextColor = Color.White
)
