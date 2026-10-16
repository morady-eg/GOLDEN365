package com.golden360.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.EmployeeDraft
import com.golden360.app.data.EmployeeValidator
import com.golden360.app.data.JobTitle
import kotlinx.coroutines.launch

private val Black = Color(0xFF101010)
private val Panel = Color(0xFF1B1B1B)
private val Gold = Color(0xFFD6B15C)
private val SoftGold = Color(0xFFF1D88F)
private val Muted = Color(0xFFB7B7B7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoldenTheme { EmployeeFormScreen() } }
    }
}

@Composable
private fun GoldenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Black, background = Black, surface = Panel,
            onBackground = Color.White, onSurface = Color.White, secondary = SoftGold
        ), content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeFormScreen() {
    var name by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<JobTitle?>(null) }
    var jobExpanded by remember { mutableStateOf(false) }
    var hireDate by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var shiftRate by remember { mutableStateOf("") }
    var profilePhoto by remember { mutableStateOf<String?>(null) }
    var idFront by remember { mutableStateOf<String?>(null) }
    var idBack by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var successMessage by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Column { Text("GOLDEN 360", color = SoftGold, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("إدارة الموظفين", color = Muted, fontSize = 12.sp) } },
                navigationIcon = { Icon(Icons.Default.ChevronLeft, contentDescription = "رجوع", tint = Gold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(58.dp).background(Color(0xFF302719), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = SoftGold, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("إضافة موظف", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("أدخل البيانات الأساسية للموظف", color = Muted, fontSize = 13.sp)
                }
                Text("جديد", color = SoftGold, fontSize = 12.sp, modifier = Modifier.background(Color(0xFF302719), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
            }
            HorizontalDivider(color = Color(0xFF4A3D24))
            SectionTitle("البيانات الأساسية")
            GoldenField("اسم الموظف *", name, { name = it; errors = errors - "fullName" }, errors["fullName"], "الاسم بالكامل")
            OutlinedFieldBlock("كود الموظف", "يُنشأ تلقائيًا عند الحفظ", readOnly = true, helper = "مثال: GS_000001")
            ExposedDropdownMenuBox(expanded = jobExpanded, onExpandedChange = { jobExpanded = !jobExpanded }) {
                OutlinedTextField(
                    value = job?.arabicLabel.orEmpty(), onValueChange = {}, readOnly = true,
                    label = { Text("الوظيفة *") }, placeholder = { Text("اختر الوظيفة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = jobExpanded) },
                    isError = errors.containsKey("jobTitle"), supportingText = { errors["jobTitle"]?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
                ExposedDropdownMenu(expanded = jobExpanded, onDismissRequest = { jobExpanded = false }) {
                    JobTitle.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.arabicLabel) }, onClick = { job = option; jobExpanded = false; errors = errors - "jobTitle" })
                    }
                }
            }
            GoldenField("تاريخ التعيين", hireDate, { hireDate = it }, null, "YYYY-MM-DD (اختياري)")
            SectionTitle("بيانات إضافية — اختيارية")
            GoldenField("الرقم القومي", nationalId, { nationalId = it; errors = errors - "nationalId" }, errors["nationalId"], "14 رقمًا", keyboardType = KeyboardType.Number)
            GoldenField("العنوان", address, { address = it }, null, "عنوان الموظف")
            SectionTitle("المرفقات — اختيارية")
            AttachmentRow("الصورة الشخصية", profilePhoto != null, "اختر صورة") { profilePhoto = "تم اختيار صورة (ربط منتقي الملفات ضمن خطوة تالية)" }
            AttachmentRow("بطاقة الرقم القومي — الوجه الأمامي", idFront != null, "اختر صورة") { idFront = "تم اختيار الوجه الأمامي (ربط منتقي الملفات ضمن خطوة تالية)" }
            AttachmentRow("بطاقة الرقم القومي — الوجه الخلفي", idBack != null, "اختر صورة") { idBack = "تم اختيار الوجه الخلفي (ربط منتقي الملفات ضمن خطوة تالية)" }
            SectionTitle("بيانات الأجر")
            GoldenField("سعر الوردية الإضافية بالجنيه *", shiftRate, { shiftRate = it; errors = errors - "extraShiftRate" }, errors["extraShiftRate"], "مثال: 250", keyboardType = KeyboardType.Decimal, helper = "سيُستخدم السعر تلقائيًا عند تسجيل وردية إضافية")
            if (successMessage.isNotBlank()) Text(successMessage, color = SoftGold, fontWeight = FontWeight.Medium)
            Button(
                onClick = {
                    val draft = EmployeeDraft(name, job, hireDate, nationalId, address, profilePhoto, idFront, idBack, shiftRate)
                    val result = EmployeeValidator.validate(draft)
                    errors = result.errors
                    if (result.isValid) {
                        successMessage = "تم التحقق من البيانات بنجاح. ربط الحفظ بقاعدة البيانات هو الخطوة التالية."
                        scope.launch { snackbar.showSnackbar("البيانات صحيحة وجاهزة للحفظ") }
                    } else {
                        successMessage = ""
                        scope.launch { snackbar.showSnackbar("راجع الحقول المطلوبة قبل المتابعة") }
                    }
                }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("التحقق من البيانات", fontWeight = FontWeight.Bold)
            }
            Text("* الحقول الإجبارية", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = SoftGold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun GoldenField(
    label: String, value: String, onValueChange: (String) -> Unit,
    error: String?, placeholder: String, keyboardType: KeyboardType = KeyboardType.Text,
    helper: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        placeholder = { Text(placeholder, color = Muted) }, modifier = Modifier.fillMaxWidth(),
        singleLine = keyboardType != KeyboardType.Text || label != "العنوان",
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), isError = error != null,
        supportingText = { if (error != null) Text(error) else if (helper != null) Text(helper, color = Muted) },
        shape = RoundedCornerShape(14.dp), colors = fieldColors()
    )
}

@Composable
private fun OutlinedFieldBlock(label: String, value: String, readOnly: Boolean, helper: String) {
    OutlinedTextField(
        value = value, onValueChange = {}, readOnly = readOnly, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), supportingText = { Text(helper, color = Muted) },
        shape = RoundedCornerShape(14.dp), colors = fieldColors()
    )
}

@Composable
private fun AttachmentRow(title: String, selected: Boolean, actionLabel: String, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A3D24)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Gold)
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(if (selected) "تم الاختيار" else "اختياري", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = onClick) { Text(if (selected) "تغيير" else actionLabel, color = SoftGold) }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold, unfocusedBorderColor = Color(0xFF4A4A4A),
    focusedLabelColor = SoftGold, unfocusedLabelColor = Muted,
    cursorColor = Gold, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    errorBorderColor = Color(0xFFE57373), errorLabelColor = Color(0xFFE57373)
)
