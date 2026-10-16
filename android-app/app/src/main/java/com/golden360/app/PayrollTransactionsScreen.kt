package com.golden360.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import com.golden360.app.data.PayrollTransactionDao
import com.golden360.app.data.PayrollTransactionEntity
import com.golden360.app.data.TransactionType
import com.golden360.app.data.EmployeeValidator
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayrollTransactionsScreen(
    employees: List<EmployeeEntity>,
    dao: PayrollTransactionDao,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    var selectedEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var employeeExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(TransactionType.ADVANCE) }
    var typeExpanded by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.now().toString()) }
    var amount by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var transactions by remember { mutableStateOf<List<PayrollTransactionEntity>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savedReference by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(employees) {
        if (selectedEmployee == null) selectedEmployee = employees.firstOrNull()
    }
    LaunchedEffect(selectedEmployee?.id, month) {
        val employeeId = selectedEmployee?.id
        transactions = if (employeeId != null && month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) {
            runCatching { dao.getForEmployeeMonth(employeeId, month) }.getOrDefault(emptyList())
        } else emptyList()
    }

    val overtimeAmount = runCatching {
        val count = quantity.toInt()
        require(count > 0)
        Math.multiplyExact(selectedEmployee?.extraShiftRatePiasters ?: 0L, count.toLong())
    }.getOrNull()

    Column(
        modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
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
            GoldenField("العدد", quantity, { quantity = it; savedReference = null }, KeyboardType.Number)
            if (selectedType == TransactionType.OVERTIME) {
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
            Button(
                enabled = !saving,
                onClick = {
                    val employee = selectedEmployee
                    val monthValid = month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))
                    val count = quantity.toIntOrNull()
                    val enteredAmount = if (selectedType == TransactionType.OVERTIME) overtimeAmount
                    else EmployeeValidator.toPiasters(amount)
                    if (employee == null) error = "اختر موظفًا"
                    else if (!monthValid) error = "أدخل الشهر بصيغة صحيحة مثل 2026-10"
                    else if (count == null || count <= 0) error = "العدد يجب أن يكون عددًا صحيحًا أكبر من صفر"
                    else if (enteredAmount == null || enteredAmount <= 0) error = "أدخل قيمة صحيحة أكبر من صفر"
                    else {
                        error = null
                        saving = true
                        scope.launch {
                            runCatching {
                                dao.create(employee.id, selectedType, month, enteredAmount, count, note)
                            }.onSuccess { reference ->
                                savedReference = reference
                                amount = ""
                                note = ""
                                transactions = dao.getForEmployeeMonth(employee.id, month)
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

private fun formatMoney(piasters: Long): String =
    java.math.BigDecimal.valueOf(piasters, 2).stripTrailingZeros().toPlainString()

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF4A4A4A),
    focusedLabelColor = SoftGold, unfocusedLabelColor = Muted,
    cursorColor = Gold, focusedTextColor = Color.White, unfocusedTextColor = Color.White
)
