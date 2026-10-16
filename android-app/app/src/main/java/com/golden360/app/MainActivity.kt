package com.golden360.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.EmployeeDao
import com.golden360.app.data.EmployeeDatabase
import com.golden360.app.data.EmployeeDraft
import com.golden360.app.data.EmployeeEntity
import com.golden360.app.data.EmployeeValidator
import com.golden360.app.data.PayrollDatabase
import com.golden360.app.data.JobTitle
import kotlinx.coroutines.launch

internal val Black = Color(0xFF101010)
internal val Panel = Color(0xFF1B1B1B)
internal val Gold = Color(0xFFD6B15C)
internal val SoftGold = Color(0xFFF1D88F)
internal val Muted = Color(0xFFB7B7B7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoldenTheme { Golden360App(EmployeeDatabase.get(applicationContext).employeeDao()) } }
    }
}

@Composable
private fun GoldenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Black, background = Black, surface = Panel,
            onBackground = Color.White, onSurface = Color.White, secondary = SoftGold
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Golden360App(dao: EmployeeDao) {
    var employees by remember { mutableStateOf<List<EmployeeEntity>>(emptyList()) }
    var editing by remember { mutableStateOf<EmployeeEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showPayroll by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(com.golden360.app.data.TransactionType.ADVANCE) }
    var activeTab by remember { mutableStateOf("home") }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        employees = dao.getActiveEmployees()
        loading = false
    }
    LaunchedEffect(dao) {
        runCatching { refresh() }.onFailure {
            snackbar.showSnackbar("تعذر تحميل الموظفين: ${it.message ?: "خطأ غير معروف"}")
        }
    }

    fun openTransactions(type: com.golden360.app.data.TransactionType) {
        selectedType = type
        showPayroll = true
    }

    Scaffold(
        containerColor = Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!showForm && !showPayroll) {
                TopAppBar(
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("GOLDEN 360", color = SoftGold, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp, fontSize = 19.sp)
                            Text(
                                when (activeTab) {
                                    "employees" -> "إدارة فريق العمل"
                                    "advances" -> "السلف المالية"
                                    "penalties" -> "الجزاءات"
                                    "more" -> "المزيد من الخدمات"
                                    else -> "إدارة الرواتب والموارد البشرية"
                                },
                                color = Muted, fontSize = 11.sp
                            )
                        }
                    },
                    actions = {
                        if (activeTab == "employees") {
                            IconButton(onClick = { searchQuery = if (searchQuery.isBlank()) " " else "" }) {
                                Icon(Icons.Default.Search, contentDescription = "بحث", tint = Gold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("GOLDEN 360", color = SoftGold, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                            Text(if (showForm) "ملف الموظف" else selectedType.arabicLabel, color = Muted, fontSize = 12.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showForm = false; showPayroll = false; editing = null }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "رجوع", tint = Gold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
                )
            }
        },
        bottomBar = {
            if (!showForm && !showPayroll) {
                NavigationBar(containerColor = Color(0xFF171717), contentColor = SoftGold, tonalElevation = 0.dp) {
                    val tabs = listOf(
                        Triple("home", "الرئيسية", Icons.Default.Home),
                        Triple("employees", "الموظفون", Icons.Default.People),
                        Triple("advances", "السلف", Icons.Default.Payments),
                        Triple("penalties", "الجزاءات", Icons.Default.Gavel),
                        Triple("more", "المزيد", Icons.Default.MoreHoriz)
                    )
                    tabs.forEach { (key, label, icon) ->
                        NavigationBarItem(
                            selected = activeTab == key,
                            onClick = {
                                activeTab = key
                                if (key == "advances") openTransactions(com.golden360.app.data.TransactionType.ADVANCE)
                                if (key == "penalties") openTransactions(com.golden360.app.data.TransactionType.PENALTY)
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Black, selectedTextColor = SoftGold,
                                indicatorColor = Gold, unselectedIconColor = Muted, unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!showForm && !showPayroll && activeTab == "employees") {
                FloatingActionButton(
                    onClick = { editing = null; showForm = true },
                    containerColor = Gold, contentColor = Black, shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "إضافة موظف") }
            }
        }
    ) { padding ->
        when {
            showForm -> EmployeeFormScreen(
                employee = editing, dao = dao, modifier = Modifier.padding(padding),
                onSaved = { message ->
                    scope.launch {
                        runCatching { refresh() }
                        showForm = false; editing = null
                        snackbar.showSnackbar(message)
                    }
                },
                onCancel = { showForm = false; editing = null }
            )
            showPayroll -> PayrollTransactionsScreen(
                employees = employees,
                dao = PayrollDatabase.get(LocalContext.current).transactions(),
                modifier = Modifier.padding(padding),
                onBack = { showPayroll = false },
                initialType = selectedType
            )
            activeTab == "employees" -> EmployeeListScreen(
                employees = employees.filter {
                    searchQuery.isBlank() || searchQuery == " " ||
                    it.fullName.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true)
                },
                loading = loading,
                modifier = Modifier.padding(padding),
                onEdit = { editing = it; showForm = true },
                onRefresh = { scope.launch { runCatching { refresh() }.onFailure { snackbar.showSnackbar("تعذر تحديث القائمة") } } },
                searchMode = searchQuery == " "
            )
            activeTab == "more" -> MoreServicesScreen(
                modifier = Modifier.padding(padding),
                onOpen = { openTransactions(it) }
            )
            else -> DashboardScreen(
                employees = employees,
                loading = loading,
                modifier = Modifier.padding(padding),
                onAddEmployee = { editing = null; showForm = true },
                onEmployees = { activeTab = "employees" },
                onOpen = { openTransactions(it) }
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    employees: List<EmployeeEntity>,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onAddEmployee: () -> Unit,
    onEmployees: () -> Unit,
    onOpen: (com.golden360.app.data.TransactionType) -> Unit
) {
    val today = java.time.LocalDate.now()
    val monthLabel = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("ar")).format(today)
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = Color(0xFF211B10),
                border = BorderStroke(1.dp, Color(0xFF594522))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("أهلاً بك في", color = Muted, fontSize = 13.sp)
                            Text("GOLDEN 360", color = SoftGold, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            Text("كل فريقك ورواتبك في مكان واحد", color = Color(0xFFE3D8BF), fontSize = 12.sp)
                        }
                        Box(Modifier.size(62.dp).background(Color(0xFF342819), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = SoftGold, modifier = Modifier.size(32.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFF594522))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("الفترة الحالية", color = Muted, fontSize = 11.sp)
                            Text(monthLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Surface(color = Color(0xFF3A2F19), shape = RoundedCornerShape(50)) {
                            Text("إدارة شهرية", color = SoftGold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
            }
            Text("نظرة عامة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("الموظفون", if (loading) "—" else employees.size.toString(), "فريق العمل", Icons.Default.People, Modifier.weight(1f))
                MetricCard("حالة النظام", "جاهز", "متابعة البيانات", Icons.Default.ReceiptLong, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الوصول السريع", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("الخدمات الأساسية", color = Muted, fontSize = 11.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("تسجيل سلفة", "تسجيل سلفة وخصمها من مرتب الشهر", Icons.Default.Payments) { onOpen(com.golden360.app.data.TransactionType.ADVANCE) }
                QuickActionCard("تسجيل جزاء", "إضافة جزاء مالي للموظف", Icons.Default.Gavel) { onOpen(com.golden360.app.data.TransactionType.PENALTY) }
                QuickActionCard("إضافة مكافأة", "إضافة مكافأة إلى مستحقات الموظف", Icons.Default.Redeem) { onOpen(com.golden360.app.data.TransactionType.BONUS) }
                QuickActionCard("وردية إضافية", "حساب تلقائي حسب سعر الموظف", Icons.Default.AccessTime) { onOpen(com.golden360.app.data.TransactionType.OVERTIME) }
            }
            Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFF34302A))) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onEmployees).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Color(0xFF302719), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.People, contentDescription = null, tint = SoftGold)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("إدارة الموظفين", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("إضافة وتعديل ملفات الموظفين", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Gold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFF39332A))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(23.dp))
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun QuickActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Color(0xFF3D3424)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(44.dp).background(Color(0xFF302719), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = SoftGold, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Gold)
        }
    }
}

@Composable
private fun MoreServicesScreen(modifier: Modifier = Modifier, onOpen: (com.golden360.app.data.TransactionType) -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("كل الخدمات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("اختر القسم الذي تريد العمل عليه", color = Muted, fontSize = 13.sp)
        QuickActionCard("المكافآت", "تسجيل ومراجعة المكافآت", Icons.Default.Redeem) { onOpen(com.golden360.app.data.TransactionType.BONUS) }
        QuickActionCard("الورديات الإضافية", "حساب السعر من ملف الموظف تلقائيًا", Icons.Default.AccessTime) { onOpen(com.golden360.app.data.TransactionType.OVERTIME) }
        QuickActionCard("ملخص الرواتب", "عرض صافي المرتب والخصومات والإضافات", Icons.Default.ReceiptLong) { onOpen(com.golden360.app.data.TransactionType.ADVANCE) }
    }
}

@Composable
private fun EmployeeListScreen(
    employees: List<EmployeeEntity>,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onEdit: (EmployeeEntity) -> Unit,
    onRefresh: () -> Unit,
    searchMode: Boolean = false
) {
    var query by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("الموظفون", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("إجمالي الموظفين النشطين: ${employees.size}", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = onRefresh) { Text("تحديث", color = SoftGold) }
        }
        Spacer(Modifier.height(12.dp))
        if (searchMode) {
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث بالاسم أو كود الموظف") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), colors = fieldColors())
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
            employees.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = Gold, modifier = Modifier.size(46.dp))
                    Text("لا يوجد موظفون حتى الآن", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("ابدأ بإضافة أول ملف موظف", color = Muted, fontSize = 13.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(employees.filter { query.isBlank() || it.fullName.contains(query, true) || it.code.contains(query, true) }, key = { it.id }) { employee ->
                    Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF403625)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.clickable { onEdit(employee) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).background(Color(0xFF302719), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = SoftGold, modifier = Modifier.size(24.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(employee.fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${employee.code} • " + (JobTitle.entries.firstOrNull { it.name == employee.jobTitle }?.arabicLabel ?: employee.jobTitle), color = SoftGold, fontSize = 11.sp)
                                Text("الراتب الأساسي  ${formatMoney(employee.baseSalaryPiasters)} ج.م", color = Muted, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.Edit, contentDescription = "تعديل الموظف", tint = Gold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeFormScreen(
    employee: EmployeeEntity?,
    dao: EmployeeDao,
    modifier: Modifier = Modifier,
    onSaved: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var name by remember(employee?.id) { mutableStateOf(employee?.fullName.orEmpty()) }
    var job by remember(employee?.id) { mutableStateOf(employee?.jobTitle?.let { stored -> JobTitle.entries.firstOrNull { it.name == stored } }) }
    var jobExpanded by remember { mutableStateOf(false) }
    var hireDate by remember(employee?.id) { mutableStateOf(employee?.hireDate.orEmpty()) }
    var nationalId by remember(employee?.id) { mutableStateOf(employee?.nationalId.orEmpty()) }
    var address by remember(employee?.id) { mutableStateOf(employee?.address.orEmpty()) }
    var shiftRate by remember(employee?.id) { mutableStateOf(employee?.extraShiftRatePiasters?.let { formatMoney(it) }.orEmpty()) }
    var baseSalary by remember(employee?.id) { mutableStateOf(employee?.baseSalaryPiasters?.let { formatMoney(it) }.orEmpty()) }
    var profilePhoto by remember(employee?.id) { mutableStateOf(employee?.profilePhotoUri) }
    var idFront by remember(employee?.id) { mutableStateOf(employee?.nationalIdFrontUri) }
    var idBack by remember(employee?.id) { mutableStateOf(employee?.nationalIdBackUri) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }

    fun persistUri(uri: Uri?): String? {
        if (uri == null) return null
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return uri.toString()
    }

    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { profilePhoto = persistUri(it) ?: profilePhoto }
    val frontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { idFront = persistUri(it) ?: idFront }
    val backPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { idBack = persistUri(it) ?: idBack }

    Scaffold(
        modifier = modifier,
        containerColor = Black,
        snackbarHost = { SnackbarHost(snackbar) }
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
                    Text(if (employee == null) "إضافة موظف" else "تعديل بيانات الموظف", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("الحقول التي تحمل * إجبارية", color = Muted, fontSize = 13.sp)
                }
            }
            HorizontalDivider(color = Color(0xFF4A3D24))
            SectionTitle("البيانات الأساسية")
            GoldenField("اسم الموظف *", name, { name = it; errors = errors - "fullName" }, errors["fullName"], "الاسم بالكامل")
            OutlinedFieldBlock("كود الموظف", employee?.code ?: "يُنشأ تلقائيًا عند الحفظ", "مثال: GS_000001")
            ExposedDropdownMenuBox(expanded = jobExpanded, onExpandedChange = { jobExpanded = !jobExpanded }) {
                OutlinedTextField(
                    value = job?.arabicLabel.orEmpty(), onValueChange = {}, readOnly = true,
                    label = { Text("الوظيفة *") }, placeholder = { Text("اختر الوظيفة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = jobExpanded) },
                    isError = errors.containsKey("jobTitle"), supportingText = { errors["jobTitle"]?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp), colors = fieldColors()
                )
                ExposedDropdownMenu(expanded = jobExpanded, onDismissRequest = { jobExpanded = false }) {
                    JobTitle.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.arabicLabel) }, onClick = { job = option; jobExpanded = false; errors = errors - "jobTitle" })
                    }
                }
            }
            GoldenField("تاريخ التعيين", hireDate, { hireDate = it }, null, "YYYY-MM-DD (اختياري)")
            SectionTitle("بيانات إضافية — اختيارية")
            GoldenField("الرقم القومي", nationalId, { nationalId = it; errors = errors - "nationalId" }, errors["nationalId"], "14 رقمًا", KeyboardType.Number)
            GoldenField("العنوان", address, { address = it }, null, "عنوان الموظف")
            SectionTitle("المرفقات — اختيارية")
            AttachmentRow("الصورة الشخصية", profilePhoto != null, if (profilePhoto == null) "اختر صورة" else "تغيير") { profilePicker.launch(arrayOf("image/*")) }
            AttachmentRow("بطاقة الرقم القومي — الوجه الأمامي", idFront != null, if (idFront == null) "اختر صورة" else "تغيير") { frontPicker.launch(arrayOf("image/*")) }
            AttachmentRow("بطاقة الرقم القومي — الوجه الخلفي", idBack != null, if (idBack == null) "اختر صورة" else "تغيير") { backPicker.launch(arrayOf("image/*")) }
            SectionTitle("بيانات الأجر")
            GoldenField("الراتب الأساسي الشهري بالجنيه *", baseSalary, { baseSalary = it; errors = errors - "baseSalary" }, errors["baseSalary"], "مثال: 8000", KeyboardType.Decimal, "يُستخدم في حساب الراتب وخصم الغياب")
            GoldenField("سعر الوردية الإضافية بالجنيه *", shiftRate, { shiftRate = it; errors = errors - "extraShiftRate" }, errors["extraShiftRate"], "مثال: 250", KeyboardType.Decimal, "سيُستخدم السعر عند تسجيل وردية إضافية")
            Button(
                enabled = !saving,
                onClick = {
                    val draft = EmployeeDraft(name, job, hireDate, nationalId, address, profilePhoto, idFront, idBack, shiftRate, baseSalary)
                    val result = EmployeeValidator.validate(draft)
                    errors = result.errors
                    if (result.isValid) {
                        saving = true
                        scope.launch {
                            runCatching {
                                if (employee == null) dao.createEmployee(draft) else dao.updateEmployee(employee.id, draft)
                            }.onSuccess {
                                onSaved(if (employee == null) "تم حفظ الموظف بنجاح" else "تم تحديث بيانات الموظف")
                            }.onFailure {
                                snackbar.showSnackbar("تعذر الحفظ: ${it.message ?: "خطأ غير معروف"}")
                            }
                            saving = false
                        }
                    } else {
                        scope.launch { snackbar.showSnackbar("راجع الحقول المطلوبة قبل الحفظ") }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Black)
                else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("حفظ بيانات الموظف", fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("إلغاء", color = SoftGold) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun formatMoney(piasters: Long): String =
    java.math.BigDecimal.valueOf(piasters, 2).stripTrailingZeros().toPlainString()

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
        singleLine = label != "العنوان",
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), isError = error != null,
        supportingText = { if (error != null) Text(error) else if (helper != null) Text(helper, color = Muted) },
        shape = RoundedCornerShape(14.dp), colors = fieldColors()
    )
}

@Composable
private fun OutlinedFieldBlock(label: String, value: String, helper: String) {
    OutlinedTextField(
        value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), supportingText = { Text(helper, color = Muted) },
        shape = RoundedCornerShape(14.dp), colors = fieldColors()
    )
}

@Composable
private fun AttachmentRow(title: String, selected: Boolean, actionLabel: String, onClick: () -> Unit) {
    Surface(
        color = Panel, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF4A3D24)), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Gold)
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(if (selected) "تم اختيار الملف" else "اختياري", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = onClick) { Text(actionLabel, color = SoftGold) }
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
