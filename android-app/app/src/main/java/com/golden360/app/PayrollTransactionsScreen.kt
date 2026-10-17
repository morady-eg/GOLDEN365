package com.golden360.app

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.golden360.app.data.TransactionType
import com.golden360.app.data.EmployeeValidator
import com.golden360.app.data.PayrollSummaryCalculator
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayrollTransactionsScreen(
    employees: List<EmployeeEntity>,
    dao: PayrollTransactionDao,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialType: TransactionType = TransactionType.ADVANCE
) {
    var selectedEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var employeeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    var typeExpanded by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
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
    var approvedSnapshot by remember { mutableStateOf<PayrollSnapshotEntity?>(null) }
    val payrollPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot = pendingPayrollSnapshot
        if (uri != null && snapshot != null) runCatching { writePayrollSnapshotPdf(context, uri, snapshot) }
            .onFailure { error = "تعذر إنشاء ملف PDF: ${it.message ?: "خطأ غير معروف"}" }
        pendingPayrollSnapshot = null
    }

    LaunchedEffect(employees) {
        if (selectedEmployee == null) selectedEmployee = employees.firstOrNull()
    }
    LaunchedEffect(selectedEmployee?.id, month) {
        transactions = emptyList(); monthlyTotals = emptyMap(); absentDays = "0"; approvedSnapshot = null; error = null
        val employeeId = selectedEmployee?.id
        if (employeeId != null && month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) {
            runCatching {
                val loadedTransactions = dao.getForEmployeeMonth(employeeId, month)
                val loadedTotals = TransactionType.entries.associate { type -> type.name to dao.sumForEmployeeMonth(employeeId, month, type.name) }
                val attendance = dao.getAttendance(employeeId, month)
                val savedSnapshot = dao.getPayrollSnapshot(employeeId, month)
                transactions = loadedTransactions; monthlyTotals = loadedTotals; absentDays = (attendance?.absentDays ?: 0).toString(); approvedSnapshot = savedSnapshot
            }.onFailure { transactions = emptyList(); monthlyTotals = emptyMap(); absentDays = "0"; approvedSnapshot = null; error = "تعذر تحميل بيانات الشهر. حاول تحديث الشاشة." }
        } else if (employeeId != null) error = "أدخل الشهر بصيغة صحيحة مثل 2026-10"
    }

    val payrollSummary = runCatching {
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
            var absentExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = absentExpanded, onExpandedChange = { if (approvedSnapshot == null) absentExpanded = !absentExpanded }) {
                OutlinedTextField(
                    value = absentDays,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("أيام الغياب في الشهر") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = absentExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                ExposedDropdownMenu(expanded = absentExpanded, onDismissRequest = { absentExpanded = false }) {
                    (0..30).forEach { days ->
                        DropdownMenuItem(enabled = approvedSnapshot == null, text = { Text("$days يوم") }, onClick = {
                            absentDays = days.toString(); absentExpanded = false
                            val employeeId = selectedEmployee?.id; val monthKey = month
                            if (employeeId != null && monthKey.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) scope.launch {
                                runCatching { dao.saveAttendance(MonthlyAttendanceEntity(employeeId, monthKey, days)) }
                                    .onFailure { error = "تعذر حفظ أيام الغياب: ${it.message ?: "خطأ غير معروف"}" }
                            }
                        })
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
                        val phone = selectedEmployee?.phone.orEmpty().filter { it.isDigit() }
                        if (phone.isNotBlank()) {
                            val uri = Uri.parse("https://wa.me/$phone?text=" + Uri.encode(whatsappMessage))
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } else error = "أضف رقم هاتف الموظف في ملفه أولاً"
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
                                dao.create(employee.id, selectedType, month, enteredAmount, count, note,
                                    overtimePlace = if (selectedType == TransactionType.OVERTIME) overtimePlace else "",
                                    overtimeDate = if (selectedType == TransactionType.OVERTIME) overtimeDate else "",
                                    overtimeTime = if (selectedType == TransactionType.OVERTIME) overtimeTime else "")
                            }.onSuccess { reference ->
                                savedReference = reference
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
                        Text("ملخص المرتب — ${selectedEmployee!!.fullName}", color = SoftGold, fontWeight = FontWeight.Bold)
                        Text("الراتب الأساسي: ${formatMoney(selectedEmployee!!.baseSalaryPiasters)} ج.م", color = Color.White, fontSize = 13.sp)
                        Text("خصم الغياب: ${formatMoney(payrollSummary.absenceDeductionPiasters)} ج.م", color = Muted, fontSize = 13.sp)
                        Text("السلف: ${formatMoney(monthlyTotals[TransactionType.ADVANCE.name] ?: 0L)} ج.م", color = Muted, fontSize = 13.sp)
                        Text("الجزاءات: ${formatMoney(monthlyTotals[TransactionType.PENALTY.name] ?: 0L)} ج.م", color = Muted, fontSize = 13.sp)
                        Text("الورديات الإضافية: ${formatMoney(payrollSummary.overtimePayPiasters)} ج.م", color = Muted, fontSize = 13.sp)
                        Text("المكافآت: ${formatMoney(monthlyTotals[TransactionType.BONUS.name] ?: 0L)} ج.م", color = Muted, fontSize = 13.sp)
                        HorizontalDivider(color = Color(0xFF6A5429))
                        Text("صافي المرتب: ${formatMoney(payrollSummary.netSalaryPiasters)} ج.م", color = SoftGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Button(
                            enabled = !saving && month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")) && (absentDays.toIntOrNull() in 0..30),
                            onClick = {
                                val employee = selectedEmployee; val summary = payrollSummary; val days = absentDays.toIntOrNull()
                                if (employee == null || summary == null || days == null) error = "راجع بيانات الموظف وأيام الغياب"
                                else scope.launch {
                                    runCatching {
                                        dao.getPayrollSnapshot(employee.id, month) ?: PayrollSnapshotEntity(
                                            employeeId = employee.id, monthKey = month, employeeName = employee.fullName, employeeCode = employee.code,
                                            jobTitle = JobTitle.entries.firstOrNull { it.name == employee.jobTitle }?.arabicLabel ?: employee.jobTitle,
                                            baseSalaryPiasters = employee.baseSalaryPiasters, absentDays = days,
                                            absenceDeductionPiasters = summary.absenceDeductionPiasters,
                                            advancesPiasters = monthlyTotals[TransactionType.ADVANCE.name] ?: 0L,
                                            penaltiesPiasters = monthlyTotals[TransactionType.PENALTY.name] ?: 0L,
                                            overtimePiasters = monthlyTotals[TransactionType.OVERTIME.name] ?: 0L,
                                            bonusesPiasters = monthlyTotals[TransactionType.BONUS.name] ?: 0L,
                                            netSalaryPiasters = summary.netSalaryPiasters, approvedAtEpochMillis = System.currentTimeMillis()
                                        ).also { dao.insertPayrollSnapshot(it) }
                                    }.onSuccess { snapshot -> approvedSnapshot = snapshot; pendingPayrollSnapshot = snapshot; payrollPdfLauncher.launch("كشف_راتب_${snapshot.employeeCode}_${snapshot.monthKey}.pdf") }
                                        .onFailure { error = "تعذر اعتماد الكشف: ${it.message ?: "قد يكون الكشف معتمدًا بالفعل"}" }
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(transactions, key = { it.id }) { transaction ->
                        Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF3D3424))) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(transaction.reference, color = SoftGold, fontWeight = FontWeight.Bold)
                                    Text(TransactionType.entries.firstOrNull { it.name == transaction.type }?.arabicLabel ?: transaction.type, color = Color.White)
                                    if (transaction.type == TransactionType.OVERTIME.name) Text("المكان: ${transaction.overtimePlace} • التاريخ: ${transaction.overtimeDate} • الموعد: ${transaction.overtimeTime}", color = Muted, fontSize = 11.sp)
                                    if (transaction.note.isNotBlank()) Text(transaction.note, color = Muted, fontSize = 12.sp)
                                }
                                Text("${formatMoney(transaction.amountPiasters)} ج.م", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
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
    val document = PdfDocument()
    try {
        val width = 595; val height = 842
        val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
        val canvas = page.canvas; canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(30,30,30); textAlign = Paint.Align.CENTER; textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        canvas.drawText("GOLDEN SHINE", width / 2f, 55f, paint)
        paint.textSize = 16f; canvas.drawText("Payroll Statement", width / 2f, 88f, paint)
        paint.textAlign = Paint.Align.LEFT; paint.textSize = 12f; paint.typeface = android.graphics.Typeface.DEFAULT
        val rows = listOf("Employee: ${snapshot.employeeName}", "Employee code: ${snapshot.employeeCode}", "Job title: ${snapshot.jobTitle}", "Payroll month: ${snapshot.monthKey}", "Base salary: ${formatMoney(snapshot.baseSalaryPiasters)} EGP", "Absence days: ${snapshot.absentDays}", "Absence deduction: ${formatMoney(snapshot.absenceDeductionPiasters)} EGP", "Advances: ${formatMoney(snapshot.advancesPiasters)} EGP", "Penalties: ${formatMoney(snapshot.penaltiesPiasters)} EGP", "Overtime: ${formatMoney(snapshot.overtimePiasters)} EGP", "Bonuses: ${formatMoney(snapshot.bonusesPiasters)} EGP", "NET SALARY: ${formatMoney(snapshot.netSalaryPiasters)} EGP", "Approved at: ${java.time.Instant.ofEpochMilli(snapshot.approvedAtEpochMillis)}", "Saved snapshot; not recalculated automatically.")
        var y = 140f
        rows.forEachIndexed { index, row ->
            paint.textSize = if (index == 11) 16f else 12f; paint.typeface = if (index == 11) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            paint.color = if (index == 11) android.graphics.Color.rgb(145,105,25) else android.graphics.Color.rgb(35,35,35)
            canvas.drawText(row.take(88), 48f, y, paint); y += if (index == 11) 38f else 32f
        }
        document.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) } ?: throw IllegalStateException("تعذر فتح ملف PDF للكتابة")
    } finally { document.close() }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF4A4A4A),
    focusedLabelColor = SoftGold, unfocusedLabelColor = Muted,
    cursorColor = Gold, focusedTextColor = Color.White, unfocusedTextColor = Color.White
)
