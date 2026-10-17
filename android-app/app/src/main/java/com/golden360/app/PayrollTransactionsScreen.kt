package com.golden360.app

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.EmployeeEntity
import com.golden360.app.data.JobTitle
import com.golden360.app.data.PayrollTransactionDao
import com.golden360.app.data.PayrollTransactionEntity
import com.golden360.app.data.MonthlyAttendanceEntity
import com.golden360.app.data.PayrollSnapshotEntity
import com.golden360.app.data.PayrollAuditEventEntity
import com.golden360.app.data.TransactionType
import com.golden360.app.data.EmployeeValidator
import com.golden360.app.data.AttendancePolicy
import com.golden360.app.data.PhoneNumberFormatter
import com.golden360.app.data.PayrollSummaryCalculator
import com.golden360.app.data.PayrollResult
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayrollTransactionsScreen(
    employees: List<EmployeeEntity>,
    dao: PayrollTransactionDao,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialType: TransactionType = TransactionType.ADVANCE,
    initialEmployeeId: Long? = null,
    initialMonth: String = java.time.YearMonth.now().toString()
) {
    var selectedEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var employeeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    var typeExpanded by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(initialMonth) }
    var amount by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var overtimePlace by remember { mutableStateOf("") }
    var overtimeDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var overtimeDatePickerOpen by remember { mutableStateOf(false) }
    var overtimeTime by remember { mutableStateOf("") }
    var overtimeTimePickerOpen by remember { mutableStateOf(false) }
    var absentDays by remember { mutableStateOf("0") }
    var monthlyTotals by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var transactions by remember { mutableStateOf<List<PayrollTransactionEntity>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savedReference by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var whatsappMessage by remember { mutableStateOf<String?>(null) }
    var pendingPayrollSnapshot by remember { mutableStateOf<PayrollSnapshotEntity?>(null) }
    var confirmPayrollApproval by remember { mutableStateOf(false) }
    var approvedSnapshot by remember { mutableStateOf<PayrollSnapshotEntity?>(null) }
    var auditEvents by remember { mutableStateOf<List<PayrollAuditEventEntity>>(emptyList()) }
    var pendingVoidTransaction by remember { mutableStateOf<PayrollTransactionEntity?>(null) }
    var voidReason by remember { mutableStateOf("") }
    val payrollPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot = pendingPayrollSnapshot
        if (uri != null && snapshot != null) runCatching { writePayrollSnapshotPdf(context, uri, snapshot) }
            .onFailure { error = "تعذر إنشاء ملف PDF: ${it.message ?: "خطأ غير معروف"}" }
        pendingPayrollSnapshot = null
    }

    LaunchedEffect(employees, initialEmployeeId) {
        if (selectedEmployee == null || (initialEmployeeId != null && selectedEmployee?.id != initialEmployeeId)) {
            selectedEmployee = employees.firstOrNull { it.id == initialEmployeeId } ?: selectedEmployee ?: employees.firstOrNull()
        }
    }
    LaunchedEffect(initialMonth) { month = initialMonth }
    LaunchedEffect(selectedEmployee?.id, month) {
        transactions = emptyList(); monthlyTotals = emptyMap(); absentDays = "0"; approvedSnapshot = null; auditEvents = emptyList(); error = null
        amount = ""; note = ""; overtimePlace = ""; overtimeTime = ""; savedReference = null; whatsappMessage = null
        val employeeId = selectedEmployee?.id
        if (employeeId != null && month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) {
            runCatching {
                val loadedTransactions = dao.getForEmployeeMonth(employeeId, month)
                val loadedTotals = TransactionType.entries.associate { type -> type.name to dao.sumForEmployeeMonth(employeeId, month, type.name) }
                val attendance = dao.getAttendance(employeeId, month)
                val datedAbsences = dao.getDailyAbsences(employeeId, month)
                val weeklyOffDays = AttendancePolicy.parseWeeklyOffDays(selectedEmployee?.weeklyOffDays.orEmpty())
                val effectiveDatedAbsences = datedAbsences.count { row ->
                    val date = java.time.LocalDate.parse(row.dateKey)
                    !AttendancePolicy.isWeeklyOff(date, weeklyOffDays)
                }
                val legacyAbsences = attendance?.legacyAbsentDays ?: 0
                val effectiveAbsentDays = legacyAbsences + effectiveDatedAbsences
                val savedSnapshot = dao.getPayrollSnapshot(employeeId, month)
                val loadedAuditEvents = dao.getAuditEvents(employeeId, month)
                transactions = loadedTransactions; monthlyTotals = loadedTotals; absentDays = effectiveAbsentDays.toString(); approvedSnapshot = savedSnapshot; auditEvents = loadedAuditEvents
            }.onFailure { transactions = emptyList(); monthlyTotals = emptyMap(); absentDays = "0"; approvedSnapshot = null; auditEvents = emptyList(); error = "تعذر تحميل بيانات الشهر. حاول تحديث الشاشة." }
        } else if (employeeId != null) error = "أدخل الشهر بصيغة صحيحة مثل 2026-10"
    }

    val payrollSummary = runCatching {
        val snapshot = approvedSnapshot
        if (snapshot != null) {
            PayrollResult(snapshot.absenceDeductionPiasters, snapshot.overtimePiasters, snapshot.netSalaryPiasters)
        } else {
            val employee = selectedEmployee ?: throw IllegalArgumentException("اختر موظفًا")
            val absent = absentDays.toIntOrNull() ?: throw IllegalArgumentException("أيام الغياب يجب أن تكون من 0 إلى 30")
            PayrollSummaryCalculator.calculate(
                baseSalaryPiasters = employee.baseSalaryPiasters,
                absentDays = absent,
                penaltiesPiasters = monthlyTotals[TransactionType.PENALTY.name] ?: 0L,
                advancesPiasters = monthlyTotals[TransactionType.ADVANCE.name] ?: 0L,
                overtimePiasters = monthlyTotals[TransactionType.OVERTIME.name] ?: 0L,
                bonusesPiasters = monthlyTotals[TransactionType.BONUS.name] ?: 0L
            )
        }
    }.getOrNull()

    val overtimeAmount = runCatching {
        val count = quantity.toInt()
        require(count > 0)
        Math.multiplyExact(selectedEmployee?.extraShiftRatePiasters ?: 0L, count.toLong())
    }.getOrNull()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SoftGold, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text("الحركات المالية", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("السلف والجزاءات والمكافآت والورديات الإضافية", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = onBack) { Text("رجوع", color = SoftGold) }
        }
        HorizontalDivider(color = Color(0xFF4A3D24))
        Text("اختر نوع الحركة", color = SoftGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransactionType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type; savedReference = null; error = null },
                    label = { Text(type.arabicLabel, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Gold,
                        selectedLabelColor = Black,
                        containerColor = Panel,
                        labelColor = Muted
                    )
                )
            }
        }
        if (employees.isEmpty()) {
            Text("أضف موظفًا أولًا حتى تتمكن من تسجيل الحركات.", color = Muted)
        } else {
            ExposedDropdownMenuBox(expanded = employeeExpanded, onExpandedChange = { employeeExpanded = !employeeExpanded }) {
                OutlinedTextField(
                    value = selectedEmployee?.let { "${it.fullName} — ${it.code}" }.orEmpty(),
                    onValueChange = {}, readOnly = true, label = { Text("الموظف") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = employeeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp), colors = fieldColors()
                )
                ExposedDropdownMenu(expanded = employeeExpanded, onDismissRequest = { employeeExpanded = false }) {
                    employees.forEach { employee ->
                        DropdownMenuItem(
                            text = { Text("${employee.fullName} — ${employee.code}") },
                            onClick = { selectedEmployee = employee; employeeExpanded = false; savedReference = null }
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                OutlinedTextField(
                    value = selectedType.arabicLabel, onValueChange = {}, readOnly = true,
                    label = { Text("نوع الحركة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp), colors = fieldColors()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    TransactionType.entries.forEach { type ->
                        DropdownMenuItem(text = { Text(type.arabicLabel) }, onClick = {
                            selectedType = type; typeExpanded = false; savedReference = null
                        })
                    }
                }
            }
            GoldenField("شهر الحركة (YYYY-MM)", month, { month = it; savedReference = null }, KeyboardType.Ascii)
            Surface(
                color = Panel, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF4A3D24))
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("أيام الغياب المسجلة لهذا الشهر", color = Muted, fontSize = 12.sp)
                        Text("$absentDays يوم", color = SoftGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("يتم تسجيل الغياب بالتاريخ من تبويب «الحضور»؛ الإجازة الأسبوعية مدفوعة.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            if (selectedType == TransactionType.OVERTIME) {
                var quantityExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = quantityExpanded, onExpandedChange = { quantityExpanded = !quantityExpanded }) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("عدد الورديات (للحساب الداخلي)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = quantityExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                    ExposedDropdownMenu(expanded = quantityExpanded, onDismissRequest = { quantityExpanded = false }) {
                        (1..10).forEach { count ->
                            DropdownMenuItem(text = { Text("$count وردية") }, onClick = { quantity = count.toString(); quantityExpanded = false; savedReference = null })
                        }
                    }
                }
                GoldenField("مكان الوردية الإضافية", overtimePlace, { overtimePlace = it; savedReference = null }, KeyboardType.Text)
                OutlinedTextField(
                    value = overtimeDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("تاريخ الوردية") },
                    trailingIcon = {
                        IconButton(onClick = { overtimeDatePickerOpen = true }) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = "اختيار التاريخ", tint = SoftGold)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                if (overtimeDatePickerOpen) {
                    val initialDateMillis = runCatching {
                        java.time.LocalDate.parse(overtimeDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                    }.getOrDefault(java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
                    DatePickerDialog(
                        onDismissRequest = { overtimeDatePickerOpen = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    overtimeDate = java.time.Instant.ofEpochMilli(millis)
                                        .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                                    savedReference = null
                                }
                                overtimeDatePickerOpen = false
                            }) { Text("تأكيد", color = SoftGold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { overtimeDatePickerOpen = false }) { Text("إلغاء", color = Muted) }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
                OutlinedTextField(
                    value = overtimeTime,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("موعد الوردية") },
                    trailingIcon = {
                        IconButton(onClick = { overtimeTimePickerOpen = true }) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = "اختيار الوقت", tint = SoftGold)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                if (overtimeTimePickerOpen) {
                    val parsedTime = overtimeTime.split(":").mapNotNull { it.toIntOrNull() }
                    val timePickerState = rememberTimePickerState(
                        initialHour = parsedTime.getOrNull(0)?.coerceIn(0, 23) ?: 8,
                        initialMinute = parsedTime.getOrNull(1)?.coerceIn(0, 59) ?: 0,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { overtimeTimePickerOpen = false },
                        title = { Text("اختيار موعد الوردية", color = Color.White) },
                        text = { TimePicker(state = timePickerState) },
                        confirmButton = {
                            TextButton(onClick = {
                                overtimeTime = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                                overtimeTimePickerOpen = false
                                savedReference = null
                            }) { Text("تأكيد", color = SoftGold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { overtimeTimePickerOpen = false }) { Text("إلغاء", color = Muted) }
                        },
                        containerColor = Panel
                    )
                }
                Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF4A3D24))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("قيمة الوردية المحددة للموظف", color = Muted, fontSize = 12.sp)
                        Text(
                            overtimeAmount?.let { "${formatMoney(it)} ج.م" } ?: "أدخل عددًا صحيحًا أكبر من صفر",
                            color = SoftGold, fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                    }
                }
            } else {
                GoldenField("قيمة الحركة بالجنيه", amount, { amount = it; savedReference = null }, KeyboardType.Decimal)
            }
            GoldenField("ملاحظات (اختياري)", note, { note = it }, KeyboardType.Text)
            if (error != null) Text(error!!, color = Color(0xFFFF8A80), fontSize = 13.sp)
            if (savedReference != null) {
                Surface(color = Color(0xFF302719), shape = RoundedCornerShape(10.dp)) {
                    Text("تم الحفظ — الرقم المرجعي: $savedReference", color = SoftGold, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
            }
            if (whatsappMessage != null) {
                OutlinedButton(
                    onClick = {
                        val phone = PhoneNumberFormatter.normalizeWhatsappPhone(selectedEmployee?.phone.orEmpty())
                        if (phone != null) {
                            val uri = Uri.parse("https://wa.me/$phone?text=" + Uri.encode(whatsappMessage))
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } else error = "أدخل رقم واتساب صحيحًا في ملف الموظف أولاً"
                    }, modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftGold)
                ) { Text("إرسال التفاصيل عبر واتساب") }
            }
            Button(
                enabled = !saving && approvedSnapshot == null,
                onClick = {
                    val employee = selectedEmployee
                    val monthValid = month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))
                    val count = quantity.toIntOrNull()
                    val enteredAmount = if (selectedType == TransactionType.OVERTIME) overtimeAmount
                    else EmployeeValidator.toPiasters(amount)
                    if (employee == null) error = "اختر موظفًا"
                    else if (!monthValid) error = "أدخل الشهر بصيغة صحيحة مثل 2026-10"
                    else if (count == null || count <= 0) error = "العدد يجب أن يكون عددًا صحيحًا أكبر من صفر"
                    else if (selectedType == TransactionType.OVERTIME && overtimePlace.isBlank()) error = "أدخل مكان الوردية الإضافية"
                    else if (selectedType == TransactionType.OVERTIME && runCatching { java.time.LocalDate.parse(overtimeDate) }.isFailure) error = "أدخل تاريخ الوردية بصيغة YYYY-MM-DD"
                    else if (selectedType == TransactionType.OVERTIME && runCatching { java.time.YearMonth.from(java.time.LocalDate.parse(overtimeDate)).toString() }.getOrNull() != month) error = "شهر الحركة يجب أن يطابق شهر تاريخ الوردية"
                    else if (selectedType == TransactionType.OVERTIME && !overtimeTime.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) error = "اختر موعدًا صحيحًا للوردية"
                    else if (enteredAmount == null || enteredAmount <= 0) error = "أدخل قيمة صحيحة أكبر من صفر"
                    else {
                        error = null
                        saving = true
                        scope.launch {
                            runCatching {
                                val currentEmployee = com.golden360.app.data.EmployeeDatabase.get(context).employeeDao().getById(employee.id)
                                 ?: error("الموظف لم يعد موجودًا في قاعدة البيانات")
                             require(!currentEmployee.archived) { "لا يمكن تسجيل حركة مالية لموظف مؤرشف" }
                             require(selectedType != TransactionType.OVERTIME || enteredAmount == Math.multiplyExact(currentEmployee.extraShiftRatePiasters, count.toLong())) { "تغير سعر الوردية؛ حدّث بيانات الموظف وأعد المحاولة" }
                             dao.create(currentEmployee.id, selectedType, month, enteredAmount, count, note,
                                    overtimePlace = if (selectedType == TransactionType.OVERTIME) overtimePlace else "",
                                    overtimeDate = if (selectedType == TransactionType.OVERTIME) overtimeDate else "",
                                    overtimeTime = if (selectedType == TransactionType.OVERTIME) overtimeTime else "")
                            }.onSuccess { reference ->
                                savedReference = reference
                                runCatching {
                                val dateLabel = java.time.LocalDate.now().toString()
                                val jobLabel = JobTitle.entries.firstOrNull { it.name == employee.jobTitle }?.arabicLabel ?: employee.jobTitle
                                whatsappMessage = when (selectedType) {
                                    TransactionType.OVERTIME -> buildString {
                                        append("*بيانات الموظف*\n")
                                        append("الاسم: ${employee.fullName}\nالكود الوظيفي: ${employee.code}\nالوظيفة: ${jobLabel}\n\n")
                                        append("*تفاصيل الوردية الإضافية*\n")
                                        append("مكان الوردية: ${overtimePlace.trim()}\nتاريخ الوردية: ${overtimeDate.trim()}\nموعد الوردية: ${overtimeTime.trim()}\nعدد الورديات: ${count}\nالقيمة الإجمالية: ${formatMoney(enteredAmount)} ج.م\nشهر المرتب: ${month}")
                                        if (note.isNotBlank()) append("\nملاحظات: ${note.trim()}")
                                        append("\n\nالرقم المرجعي: ${reference}\n\n*GOLDEN SHINE MARBLE*\nGOLDEN 360")
                                    }
                                    TransactionType.PENALTY -> buildString {
                                        append("*بيانات الموظف*\n")
                                        append("الاسم: ${employee.fullName}\nالكود الوظيفي: ${employee.code}\nالوظيفة: ${jobLabel}\n\n")
                                        append("*تفاصيل معاملة جزاء*\n")
                                        append("قيمة الجزاء: ${formatMoney(enteredAmount)} ج.م\nتاريخ التسجيل: ${dateLabel}")
                                        if (note.isNotBlank()) append("\nسبب الجزاء/الملاحظات: ${note.trim()}")
                                        append("\n\nالرقم المرجعي: ${reference}\n\n*GOLDEN SHINE MARBLE*\nGOLDEN 360")
                                    }
                                    else -> buildString {
                                        append("*بيانات الموظف*\n")
                                        append("الاسم: ${employee.fullName}\nالكود الوظيفي: ${employee.code}\nالوظيفة: ${jobLabel}\n\n")
                                        append("*تفاصيل ${selectedType.arabicLabel}*\n")
                                        append("القيمة: ${formatMoney(enteredAmount)} ج.م\nالشهر: ${month}\nتاريخ التسجيل: ${dateLabel}")
                                        if (note.isNotBlank()) append("\nملاحظات: ${note.trim()}")
                                        append("\n\nالرقم المرجعي: ${reference}\n\n*GOLDEN SHINE MARBLE*\nGOLDEN 360")
                                    }
                                }
                                amount = ""
                                note = ""
                                overtimePlace = ""
                                overtimeTime = ""
                                transactions = dao.getForEmployeeMonth(employee.id, month)
                                monthlyTotals = TransactionType.entries.associate { type -> type.name to dao.sumForEmployeeMonth(employee.id, month, type.name) }
                                 auditEvents = dao.getAuditEvents(employee.id, month)
                                }.onFailure {
                                    error = "تم حفظ الحركة بالرقم المرجعي: ${reference}، لكن تعذر تحديث الشاشة. أعد فتح الحركات للتحقق من البيانات."
                                }
                            }.onFailure { error = "تعذر حفظ الحركة: ${it.message ?: "خطأ غير معروف"}" }
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), color = Black, strokeWidth = 2.dp)
                else Text("حفظ الحركة وإنشاء رقم مرجعي", fontWeight = FontWeight.Bold)
            }
            if (approvedSnapshot != null) Text("هذا الشهر معتمد؛ تم إيقاف تعديل الغياب والحركات لحماية نسخة كشف الراتب.", color = SoftGold, fontSize = 12.sp)
            if (payrollSummary != null && selectedEmployee != null) {
                Surface(color = Color(0xFF302719), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF6A5429))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("ملخص المرتب — ${approvedSnapshot?.employeeName ?: selectedEmployee!!.fullName}", color = SoftGold, fontWeight = FontWeight.Bold)
                        Text("الراتب الأساسي: ${formatMoney(approvedSnapshot?.baseSalaryPiasters ?: selectedEmployee!!.baseSalaryPiasters)} ج.م", color = Color.White, fontSize = 13.sp)
                        Text("خصم الغياب: ${formatMoney(payrollSummary.absenceDeductionPiasters)} ج.م", color = Muted, fontSize = 13.sp)
                        Text("السلف: ${formatMoney(approvedSnapshot?.advancesPiasters ?: (monthlyTotals[TransactionType.ADVANCE.name] ?: 0L))} ج.م", color = Muted, fontSize = 13.sp)
                        Text("الجزاءات: ${formatMoney(approvedSnapshot?.penaltiesPiasters ?: (monthlyTotals[TransactionType.PENALTY.name] ?: 0L))} ج.م", color = Muted, fontSize = 13.sp)
                        Text("الورديات الإضافية: ${formatMoney(payrollSummary.overtimePayPiasters)} ج.م", color = Muted, fontSize = 13.sp)
                        Text("المكافآت: ${formatMoney(approvedSnapshot?.bonusesPiasters ?: (monthlyTotals[TransactionType.BONUS.name] ?: 0L))} ج.م", color = Muted, fontSize = 13.sp)
                        HorizontalDivider(color = Color(0xFF6A5429))
                        Text("صافي المرتب: ${formatMoney(payrollSummary.netSalaryPiasters)} ج.م", color = SoftGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (com.golden360.app.data.PayrollPolicy.requiresManualReview(payrollSummary.netSalaryPiasters)) {
                            Text("تنبيه: صافي المرتب سالب. لم يتم تصفيره تلقائيًا؛ يجب مراجعة المديونية والخصومات يدويًا قبل الصرف.", color = Color(0xFFFF8A80), fontSize = 12.sp)
                        }
                        Button(
                            enabled = !saving && month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")) && (absentDays.toIntOrNull() in 0..30),
                            onClick = { confirmPayrollApproval = true },
                            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
                        ) { Text("اعتماد كشف الراتب وتصدير PDF", fontWeight = FontWeight.Bold) }
                        Text("النسخة المعتمدة ثابتة ولا تتغير تلقائيًا عند تعديل ملف الموظف.", color = Muted, fontSize = 11.sp)
                    }
                }
            } else if (selectedEmployee != null) {
                Text("أدخل أيام غياب صحيحة من 0 إلى 30 لعرض صافي المرتب.", color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
            Text("حركات الشهر المحدد", color = SoftGold, fontWeight = FontWeight.Bold)
            if (transactions.isEmpty()) {
                Text("لا توجد حركات مسجلة لهذا الموظف في هذا الشهر.", color = Muted, fontSize = 12.sp)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                ) {
                    transactions.forEach { transaction ->
                        Surface(
                            color = Panel,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (transaction.voidedAtEpochMillis != null) Color(0xFF69413D) else Color(0xFF3D3424))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(transaction.reference, color = if (transaction.voidedAtEpochMillis != null) Muted else SoftGold, fontWeight = FontWeight.Bold)
                                    Text(TransactionType.entries.firstOrNull { it.name == transaction.type }?.arabicLabel ?: transaction.type, color = Color.White)
                                    if (transaction.type == TransactionType.OVERTIME.name) Text("المكان: ${transaction.overtimePlace} • التاريخ: ${transaction.overtimeDate} • الموعد: ${transaction.overtimeTime} • العدد: ${transaction.quantity}", color = Muted, fontSize = 11.sp)
                                    if (transaction.note.isNotBlank()) Text(transaction.note, color = Muted, fontSize = 12.sp)
                                    if (transaction.voidedAtEpochMillis != null) {
                                        Text("ملغاة • السبب: ${transaction.voidReason ?: "—"}", color = Color(0xFFFFA39A), fontSize = 11.sp)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${formatMoney(transaction.amountPiasters)} ج.م", color = if (transaction.voidedAtEpochMillis != null) Muted else Color.White, fontWeight = FontWeight.SemiBold)
                                    if (transaction.voidedAtEpochMillis == null && approvedSnapshot == null) {
                                        TextButton(enabled = !saving, onClick = {
                                            pendingVoidTransaction = transaction
                                            voidReason = ""
                                        }) {
                                            Text("إلغاء الحركة", color = Color(0xFFFFA39A), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("سجل التدقيق المالي", color = SoftGold, fontWeight = FontWeight.Bold)
            if (auditEvents.isEmpty()) {
                Text("لا توجد أحداث تدقيق لهذا الشهر حتى الآن.", color = Muted, fontSize = 12.sp)
            } else {
                auditEvents.take(20).forEach { event ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        val actionLabel = when (event.action) {
                            "TRANSACTION_CREATED" -> "تسجيل حركة مالية"
                            "TRANSACTION_VOIDED" -> "إلغاء حركة مالية"
                            "ABSENCE_UPDATED" -> "تعديل أيام الغياب"
                            "ABSENCE_DATE_MARKED" -> "تسجيل غياب بتاريخ"
                            "ABSENCE_DATE_CLEARED" -> "إلغاء غياب بتاريخ"
                            "PAYROLL_APPROVED" -> "اعتماد كشف الراتب"
                            else -> event.action
                        }
                        Text("${actionLabel} • ${java.time.Instant.ofEpochMilli(event.createdAtEpochMillis)}", color = Color.White, fontSize = 12.sp)
                        Text("قبل: ${event.beforeValue ?: "—"} | بعد: ${event.afterValue} | المرجع: ${event.transactionReference ?: "—"}", color = Muted, fontSize = 11.sp)
                    }
                }
                if (auditEvents.size > 20) Text("يُعرض أحدث 20 حدثًا من أصل ${auditEvents.size}.", color = Muted, fontSize = 11.sp)
            }
            if (pendingVoidTransaction != null) {
                AlertDialog(
                    onDismissRequest = {
                        if (!saving) {
                            pendingVoidTransaction = null
                            voidReason = ""
                        }
                    },
                    title = { Text("تأكيد إلغاء الحركة") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("سيتم استبعاد الحركة من إجمالي المرتب مع الاحتفاظ بها في سجل التدقيق. لا يمكن إلغاء الحركة بعد اعتماد كشف الراتب.")
                            OutlinedTextField(
                                value = voidReason,
                                onValueChange = { voidReason = it },
                                label = { Text("سبب الإلغاء (إجباري)") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                enabled = !saving
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !saving && voidReason.isNotBlank(),
                            onClick = {
                                val target = pendingVoidTransaction ?: return@TextButton
                                saving = true
                                scope.launch {
                                    runCatching {
                                        dao.voidTransaction(target.reference, voidReason)
                                        val updatedTransactions = dao.getForEmployeeMonth(target.employeeId, target.monthKey)
                                        val updatedTotals = TransactionType.entries.associate { type ->
                                            type.name to dao.sumForEmployeeMonth(target.employeeId, target.monthKey, type.name)
                                        }
                                        val updatedAudit = dao.getAuditEvents(target.employeeId, target.monthKey)
                                        Triple(updatedTransactions, updatedTotals, updatedAudit)
                                    }.onSuccess { updated ->
                                        transactions = updated.first
                                        monthlyTotals = updated.second
                                        auditEvents = updated.third
                                        pendingVoidTransaction = null
                                        voidReason = ""
                                        savedReference = null
                                    }.onFailure { error = "تعذر إلغاء الحركة: ${it.message ?: "خطأ غير معروف"}" }
                                    saving = false
                                }
                            }
                        ) { Text(if (saving) "جارٍ الإلغاء…" else "تأكيد الإلغاء") }
                    },
                    dismissButton = {
                        TextButton(enabled = !saving, onClick = {
                            pendingVoidTransaction = null
                            voidReason = ""
                        }) { Text("تراجع") }
                    }
                )
            }
        }
    }
    if (confirmPayrollApproval) {
        val reviewNeeded = payrollSummary?.let { com.golden360.app.data.PayrollPolicy.requiresManualReview(it.netSalaryPiasters) } == true
        AlertDialog(
            onDismissRequest = { if (!saving) confirmPayrollApproval = false },
            title = { Text("تأكيد اعتماد كشف الراتب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم تثبيت كشف الموظف ${selectedEmployee?.fullName ?: "—"} لشهر $month بقيمة صافي ${payrollSummary?.let { formatMoney(it.netSalaryPiasters) } ?: "—"} ج.م.")
                    Text("بعد الاعتماد لن يمكن تعديل حركات الشهر أو الغياب، وسيُحفظ سجل تدقيق للكشف.", color = Muted)
                    if (reviewNeeded) {
                        Text("تنبيه مهم: صافي الراتب سالب. لا تعتمد الكشف إلا بعد مراجعة المديونية والخصومات يدويًا.", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && selectedEmployee != null && payrollSummary != null && absentDays.toIntOrNull() in 0..30,
                    onClick = {
                        val employee = selectedEmployee
                        val summary = payrollSummary
                        val days = absentDays.toIntOrNull()
                        if (employee == null || summary == null || days == null) {
                            error = "راجع بيانات الموظف وأيام الغياب"
                            confirmPayrollApproval = false
                        } else {
                            saving = true
                            scope.launch {
                                runCatching {
                                    val snapshot = PayrollSnapshotEntity(
                                        employeeId = employee.id,
                                        monthKey = month,
                                        employeeName = employee.fullName,
                                        employeeCode = employee.code,
                                        jobTitle = JobTitle.entries.firstOrNull { it.name == employee.jobTitle }?.arabicLabel ?: employee.jobTitle,
                                        baseSalaryPiasters = employee.baseSalaryPiasters,
                                        absentDays = days,
                                        absenceDeductionPiasters = summary.absenceDeductionPiasters,
                                        advancesPiasters = monthlyTotals[TransactionType.ADVANCE.name] ?: 0L,
                                        penaltiesPiasters = monthlyTotals[TransactionType.PENALTY.name] ?: 0L,
                                        overtimePiasters = monthlyTotals[TransactionType.OVERTIME.name] ?: 0L,
                                        bonusesPiasters = monthlyTotals[TransactionType.BONUS.name] ?: 0L,
                                        netSalaryPiasters = summary.netSalaryPiasters,
                                        approvedAtEpochMillis = System.currentTimeMillis()
                                    )
                                    dao.approvePayrollSnapshot(snapshot).also {
                                        auditEvents = dao.getAuditEvents(employee.id, month)
                                    }
                                }.onSuccess { snapshot ->
                                    approvedSnapshot = snapshot
                                    pendingPayrollSnapshot = snapshot
                                    confirmPayrollApproval = false
                                    payrollPdfLauncher.launch("كشف_راتب_${snapshot.employeeCode}_${snapshot.monthKey}.pdf")
                                }.onFailure { failure ->
                                    error = "تعذر اعتماد الكشف: ${failure.message ?: "قد يكون الكشف معتمدًا بالفعل"}"
                                }
                                saving = false
                            }
                        }
                    }
                ) { Text(if (saving) "جارٍ الاعتماد…" else "تأكيد الاعتماد") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { confirmPayrollApproval = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun GoldenField(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp), colors = fieldColors()
    )
}

private fun formatMoney(piasters: Long): String = java.math.BigDecimal.valueOf(piasters, 2).stripTrailingZeros().toPlainString()

private fun writePayrollSnapshotPdf(context: Context, uri: Uri, snapshot: PayrollSnapshotEntity) {
    PayrollReportPdf.writeIndividual(context, uri, snapshot)
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF4A4A4A),
    focusedLabelColor = SoftGold, unfocusedLabelColor = Muted,
    cursorColor = Gold, focusedTextColor = Color.White, unfocusedTextColor = Color.White
)
