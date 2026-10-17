package com.golden360.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.BackupManager
import com.golden360.app.data.EmployeeDao
import com.golden360.app.data.EmployeeDatabase
import com.golden360.app.data.EmployeeDraft
import com.golden360.app.data.EmployeeEntity
import com.golden360.app.data.EmployeeValidator
import com.golden360.app.data.AttendancePolicy
import com.golden360.app.data.PayrollDatabase
import com.golden360.app.data.JobTitle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var showIdCard by remember { mutableStateOf(false) }
    var showBackupScreen by remember { mutableStateOf(false) }
    var showReports by remember { mutableStateOf(false) }
    var selectedIdEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var selectedIdEmployees by remember { mutableStateOf<List<EmployeeEntity>>(emptyList()) }
    var selectedType by remember { mutableStateOf(com.golden360.app.data.TransactionType.ADVANCE) }
    var selectedPayrollEmployeeId by remember { mutableStateOf<Long?>(null) }
    var payrollInitialMonth by remember { mutableStateOf(java.time.YearMonth.now().toString()) }
    var activeTab by remember { mutableStateOf("home") }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activityContext = LocalContext.current

    suspend fun refresh() {
        loading = true
        try {
            employees = dao.getActiveEmployees()
        } finally {
            loading = false
        }
    }
    LaunchedEffect(dao) {
        runCatching { refresh() }.onFailure {
            snackbar.showSnackbar("تعذر تحميل الموظفين: ${it.message ?: "خطأ غير معروف"}")
        }
    }

    fun openTransactions(type: com.golden360.app.data.TransactionType) {
        selectedType = type
        selectedPayrollEmployeeId = null
        payrollInitialMonth = java.time.YearMonth.now().toString()
        showPayroll = true
    }

    Scaffold(
        containerColor = Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!showForm && !showPayroll && !showIdCard && !showBackupScreen && !showReports) {
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
                                    "attendance" -> "الحضور والغياب"
                                    else -> "إدارة الرواتب والموارد البشرية"
                                },
                                color = Muted, fontSize = 11.sp
                            )
                        }
                    },
                    actions = {
                        if (activeTab == "employees") {
                            IconButton(onClick = {
                                if (searchMode) searchQuery = ""
                                searchMode = !searchMode
                            }) {
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
                            Text(when { showForm -> "ملف الموظف"; showIdCard -> "بطاقة الموظف"; showBackupScreen -> "النسخ الاحتياطي والاستعادة"; showReports -> "مركز التقارير"; else -> selectedType.arabicLabel }, color = Muted, fontSize = 12.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showForm = false; showPayroll = false; showIdCard = false; showBackupScreen = false; showReports = false; selectedIdEmployee = null; editing = null }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "رجوع", tint = Gold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Black)
                )
            }
        },
        bottomBar = {
            if (!showForm && !showPayroll && !showIdCard && !showBackupScreen) {
                NavigationBar(containerColor = Color(0xFF171717), contentColor = SoftGold, tonalElevation = 0.dp) {
                    val tabs = listOf(
                        Triple("home", "الرئيسية", Icons.Default.Home),
                        Triple("employees", "الموظفون", Icons.Default.People),
                        Triple("attendance", "الحضور", Icons.Default.DateRange),
                        Triple("payroll", "الرواتب", Icons.Default.ReceiptLong),
                        Triple("more", "المزيد", Icons.Default.MoreHoriz)
                    )
                    tabs.forEach { (key, label, icon) ->
                        NavigationBarItem(
                            selected = activeTab == key,
                            onClick = {
                                activeTab = key
                                // الأقسام المالية تُفتح من شاشة المزيد، لتجنب تكرارها في التنقل
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
            if (!showForm && !showPayroll && !showIdCard && !showBackupScreen && activeTab == "employees") {
                FloatingActionButton(
                    onClick = { editing = null; showForm = true },
                    containerColor = Gold, contentColor = Black, shape = RoundedCornerShape(18.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "إضافة موظف") }
            }
        }
    ) { padding ->
        when {
            showReports -> ReportsCenterScreen(
                employees = employees,
                dao = PayrollDatabase.get(LocalContext.current).transactions(),
                modifier = Modifier.padding(padding),
                onBack = { showReports = false }
            )
            showBackupScreen -> BackupRestoreScreen(
                modifier = Modifier.padding(padding),
                onBack = { showBackupScreen = false },
                onFinished = { (activityContext as? Activity)?.recreate() }
            )
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
                initialType = selectedType,
                initialEmployeeId = selectedPayrollEmployeeId,
                initialMonth = payrollInitialMonth
            )
            showIdCard && selectedIdEmployees.isNotEmpty() -> EmployeeIdCardScreen(
                employees = selectedIdEmployees,
                onBack = { showIdCard = false; selectedIdEmployee = null; selectedIdEmployees = emptyList() }
            )
            activeTab == "employees" -> EmployeeListScreen(
                employees = employees.filter {
                    searchQuery.isBlank() ||
                    it.fullName.contains(searchQuery.trim(), ignoreCase = true) ||
                    it.code.contains(searchQuery.trim(), ignoreCase = true)
                },
                loading = loading,
                modifier = Modifier.padding(padding),
                onEdit = { editing = it; showForm = true },
                onPrintId = { selectedIdEmployee = it; selectedIdEmployees = listOf(it); showIdCard = true },
                onPrintIds = { selectedIdEmployees = it; selectedIdEmployee = it.firstOrNull(); showIdCard = it.isNotEmpty() },
                onRefresh = { scope.launch { runCatching { refresh() }.onFailure { snackbar.showSnackbar("تعذر تحديث القائمة") } } },
                searchMode = searchMode,
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )
            activeTab == "attendance" -> AttendanceScreen(
                employees = employees,
                dao = PayrollDatabase.get(LocalContext.current).transactions(),
                modifier = Modifier.padding(padding)
            )
            activeTab == "more" -> MoreServicesScreen(
                modifier = Modifier.padding(padding),
                onOpen = { openTransactions(it) },
                onBackup = { showBackupScreen = true },
                onReports = { showReports = true }
            )
            activeTab == "payroll" -> PayrollOverviewScreen(
                employees = employees,
                dao = PayrollDatabase.get(LocalContext.current).transactions(),
                modifier = Modifier.padding(padding),
                initialMonth = payrollInitialMonth,
                onOpenTransactions = { type, employeeId, selectedMonth ->
                    selectedType = type
                    selectedPayrollEmployeeId = employeeId
                    payrollInitialMonth = selectedMonth
                    showPayroll = true
                }
            )
            else -> ExecutiveDashboardScreen(
                employees = employees,
                dao = PayrollDatabase.get(LocalContext.current).transactions(),
                loading = loading,
                modifier = Modifier.padding(padding),
                onAddEmployee = { editing = null; showForm = true },
                onEmployees = { activeTab = "employees" },
                onPayroll = { activeTab = "payroll" },
                onAttendance = { activeTab = "attendance" },
                onOpenTransaction = { openTransactions(it) }
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
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF171717),
                border = BorderStroke(1.dp, Color(0xFF6B5428))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("GOLDEN SHINE MARBLE", color = Color(0xFFC8A65B), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("إدارة الموظفين والرواتب", color = Muted, fontSize = 13.sp)
                            Text("GOLDEN 360", color = SoftGold, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                            Text("كل معاملات فريقك في مكان واحد", color = Color(0xFFE0D3B4), fontSize = 12.sp)
                        }
                        Box(
                            Modifier.size(72.dp).background(Color(0xFF302516), RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = SoftGold, modifier = Modifier.size(36.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFF514020))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("الفترة المالية الحالية", color = Muted, fontSize = 11.sp)
                            Text(monthLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Surface(color = Color(0xFF3B2E16), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF72592A))) {
                            Row(
                                Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.size(6.dp).background(Color(0xFF8FC69A), CircleShape))
                                Text("نظام محلي", color = SoftGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("لوحة التحكم", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("ملخص سريع لفريق العمل", color = Muted, fontSize = 12.sp)
                }
                Surface(color = Color(0xFF242017), shape = RoundedCornerShape(10.dp)) {
                    Text("نظرة عامة", color = SoftGold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("إجمالي الموظفين", if (loading) "—" else employees.size.toString(), "ملفات الموظفين النشطة", Icons.Default.People, Modifier.weight(1f))
                MetricCard("إدارة الشهر", monthLabel, "فترة الرواتب الحالية", Icons.Default.ReceiptLong, Modifier.weight(1f))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الخدمات المالية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("اختر إجراءً", color = Muted, fontSize = 11.sp)
            }



            Surface(
                color = Color(0xFF1A1711),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF514020)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.clickable(onClick = onEmployees).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(48.dp).background(Color(0xFF342919), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.People, contentDescription = null, tint = SoftGold, modifier = Modifier.size(25.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ملفات الموظفين", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("عرض الموظفين وإضافة ملفات أو تعديلها", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Gold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1A1916),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF40351F))
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(38.dp).background(Color(0xFF342919), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = SoftGold, modifier = Modifier.size(21.dp))
            }
            Text(title, color = Muted, fontSize = 11.sp)
            Text(value, color = SoftGold, fontSize = if (value.length > 12) 12.sp else 23.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun LuxuryActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color(0xFF1B1914),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF514020))
    ) {
        Column(
            Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color(0xFF382B17), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = SoftGold, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Color(0xFF8F753B), modifier = Modifier.size(18.dp))
            }
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MoreServicesScreen(modifier: Modifier = Modifier, onOpen: (com.golden360.app.data.TransactionType) -> Unit, onBackup: () -> Unit, onReports: () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("المعاملات والخدمات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("كل عمليات الإضافة والخصم في مكان واحد، بدون تكرارها في باقي الشاشات.", color = Muted, fontSize = 13.sp)
        LuxuryActionCard("مركز التقارير", "كشف الرواتب والحضور والسلف والجزاءات والمكافآت وتصدير Excel", Icons.Default.Assessment, Modifier.fillMaxWidth(), onReports)
        SectionTitle("الإضافات المالية")
        LuxuryActionCard("تسجيل سلفة", "إضافة سلفة لموظف ومتابعة خصمها", Icons.Default.Payments, Modifier.fillMaxWidth()) {
            onOpen(com.golden360.app.data.TransactionType.ADVANCE)
        }
        LuxuryActionCard("تسجيل مكافأة", "تسجيل مبلغ إضافي للموظف", Icons.Default.Redeem, Modifier.fillMaxWidth()) {
            onOpen(com.golden360.app.data.TransactionType.BONUS)
        }
        SectionTitle("الخصومات")
        LuxuryActionCard("تسجيل جزاء", "تسجيل الخصم وربطه بالموظف", Icons.Default.Gavel, Modifier.fillMaxWidth()) {
            onOpen(com.golden360.app.data.TransactionType.PENALTY)
        }
        SectionTitle("الورديات")
        LuxuryActionCard("تسجيل وردية إضافية", "حساب المقابل حسب سعر الوردية في ملف الموظف", Icons.Default.AccessTime, Modifier.fillMaxWidth()) {
            onOpen(com.golden360.app.data.TransactionType.OVERTIME)
        }
        SectionTitle("حماية البيانات")
        LuxuryActionCard("النسخ الاحتياطي والاستعادة", "نسخة مشفرة لبيانات الموظفين والرواتب والحضور", Icons.Default.Save, Modifier.fillMaxWidth(), onBackup)
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRestoreScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var password by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = runCatching {
                    val secret = password.toCharArray()
                    try {
                        val encrypted = withContext(Dispatchers.IO) {
                            BackupManager.createEncryptedBackup(context, secret)
                        }
                        withContext(Dispatchers.IO) {
                            val output = requireNotNull(context.contentResolver.openOutputStream(uri)) {
                                "تعذر فتح ملف الحفظ"
                            }
                            output.use { it.write(encrypted) }
                            encrypted.fill(0)
                        }
                    } finally {
                        secret.fill(Char.MIN_VALUE)
                    }
                }
                busy = false
                result.onSuccess {
                    password = ""
                    snackbar.showSnackbar("تم إنشاء النسخة الاحتياطية المشفرة بنجاح")
                    onFinished()
                }.onFailure {
                    snackbar.showSnackbar("تعذر إنشاء النسخة: ${it.message ?: "خطأ غير معروف"}")
                }
            }
        }
    }

    val selectBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = runCatching {
                    val encrypted = withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "تعذر قراءة ملف النسخة الاحتياطية"
                        }.use { input ->
                            val bytes = input.readBytes()
                            require(bytes.size <= 200 * 1024 * 1024) { "حجم ملف النسخة أكبر من الحد المسموح" }
                            bytes
                        }
                    }
                    val secret = password.toCharArray()
                    try {
                        withContext(Dispatchers.IO) {
                            BackupManager.restoreEncryptedBackup(context, encrypted, secret)
                        }
                    } finally {
                        secret.fill(Char.MIN_VALUE)
                        encrypted.fill(0)
                    }
                }
                busy = false
                result.onSuccess {
                    password = ""
                    confirmRestore = false
                    snackbar.showSnackbar("تمت استعادة النسخة والتحقق من سلامة قواعد البيانات")
                    onFinished()
                }.onFailure {
                    snackbar.showSnackbar("لم تتم الاستعادة: ${it.message ?: "تعذر التحقق من النسخة"}")
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Black,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("النسخ الاحتياطي والاستعادة", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "أنشئ ملفًا مشفرًا يحفظ قواعد بيانات الموظفين والرواتب والحضور والحركات المالية وسجل التدقيق.",
                color = Muted, fontSize = 13.sp
            )
            Surface(
                color = Panel, shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF514020)), modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("كلمة مرور النسخة الاحتياطية", color = SoftGold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("كلمة مرور قوية (10 أحرف على الأقل)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors()
                    )
                    Text(
                        "احتفظ بكلمة المرور في مكان آمن. لا يمكن استعادة النسخة بدونها، ولا تُرسل إلى أي جهة.",
                        color = Muted, fontSize = 12.sp
                    )
                    Button(
                        enabled = !busy && password.length >= 10,
                        onClick = { createBackup.launch("GOLDEN360-backup.g360") },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("إنشاء نسخة احتياطية مشفرة")
                    }
                }
            }
            Surface(
                color = Panel, shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF6B3434)), modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("استعادة نسخة احتياطية", color = Color(0xFFFFB3A8), fontWeight = FontWeight.Bold)
                    Text(
                        "الاستعادة تستبدل بيانات التطبيق الحالية بالبيانات الموجودة في النسخة. أنشئ نسخة جديدة من بياناتك الحالية أولًا.",
                        color = Muted, fontSize = 12.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = confirmRestore,
                            onCheckedChange = { confirmRestore = it },
                            colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Muted, checkmarkColor = Black)
                        )
                        Text("أفهم أن الاستعادة ستستبدل البيانات الحالية", color = Color.White, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        enabled = !busy && password.length >= 10 && confirmRestore,
                        onClick = { selectBackup.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("اختيار نسخة واستعادتها", color = SoftGold)
                    }
                }
            }
            Text(
                "ملاحظة: صور الموظفين وصور بطاقات الهوية المرتبطة بمواقع خارجية لا تُضمَّن في هذه النسخة؛ يلزم نقلها منفصلة عند الانتقال إلى هاتف آخر.",
                color = Muted, fontSize = 11.sp
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("جارٍ التحقق والمعالجة بأمان…", color = SoftGold, fontSize = 12.sp)
                }
            }
            OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("رجوع", color = SoftGold)
            }
        }
    }
}

@Composable
private fun EmployeeListScreen(
    employees: List<EmployeeEntity>,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onEdit: (EmployeeEntity) -> Unit,
    onPrintId: (EmployeeEntity) -> Unit,
    onPrintIds: (List<EmployeeEntity>) -> Unit,
    onRefresh: () -> Unit,
    searchMode: Boolean = false,
    query: String = "",
    onQueryChange: (String) -> Unit = {}
) {
    var selectedEmployeeIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val visibleEmployees = employees.filter { query.isBlank() || it.fullName.contains(query, true) || it.code.contains(query, true) }
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("الموظفون", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("إجمالي الموظفين النشطين: ${employees.size}", color = Muted, fontSize = 12.sp)
            }
            TextButton(enabled = !loading, onClick = onRefresh) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = SoftGold, strokeWidth = 2.dp)
                else Text("تحديث", color = SoftGold)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (searchMode) {
            OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث بالاسم أو كود الموظف") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), colors = fieldColors())
        }
        if (visibleEmployees.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val visibleIds = visibleEmployees.map { it.id }.toSet()
                    selectedEmployeeIds = if (selectedEmployeeIds.containsAll(visibleIds)) selectedEmployeeIds - visibleIds else selectedEmployeeIds + visibleIds
                }) {
                    Text(if (visibleEmployees.isNotEmpty() && selectedEmployeeIds.containsAll(visibleEmployees.map { it.id })) "إلغاء تحديد الظاهر" else "تحديد الكل", color = SoftGold)
                }
                Spacer(Modifier.weight(1f))
                Button(enabled = selectedEmployeeIds.isNotEmpty(), onClick = { onPrintIds(employees.filter { it.id in selectedEmployeeIds }) }, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("طباعة المحدد (${selectedEmployeeIds.size})")
                }
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
            employees.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = Gold, modifier = Modifier.size(46.dp))
                    Text(
                        if (searchMode && query.isNotBlank()) "لا توجد نتائج مطابقة للبحث" else "لا يوجد موظفون حتى الآن",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (searchMode && query.isNotBlank()) "جرّب اسمًا أو كودًا آخر، أو امسح البحث" else "ابدأ بإضافة أول ملف موظف",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
                items(visibleEmployees, key = { it.id }) { employee ->
                    Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (employee.id in selectedEmployeeIds) Gold else Color(0xFF403625)), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = employee.id in selectedEmployeeIds,
                                onCheckedChange = { checked -> selectedEmployeeIds = if (checked) selectedEmployeeIds + employee.id else selectedEmployeeIds - employee.id },
                                colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Muted, checkmarkColor = Black)
                            )
                            EmployeeAvatar(
                                photoUri = employee.profilePhotoUri,
                                modifier = Modifier.size(48.dp)
                            )
                            Column(Modifier.weight(1f).clickable { onEdit(employee) }.padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(employee.fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${employee.code} • " + (JobTitle.entries.firstOrNull { it.name == employee.jobTitle }?.arabicLabel ?: employee.jobTitle), color = SoftGold, fontSize = 11.sp)
                                Text("الراتب الأساسي  ${formatMoney(employee.baseSalaryPiasters)} ج.م", color = Muted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { onEdit(employee) }) { Icon(Icons.Default.Edit, contentDescription = "تعديل الموظف", tint = Gold) }
                                IconButton(onClick = { onPrintId(employee) }) { Icon(Icons.Default.Print, contentDescription = "طباعة بطاقة الموظف", tint = Gold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeAvatar(photoUri: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val photo = produceState<android.graphics.Bitmap?>(initialValue = null, photoUri) {
        value = withContext(Dispatchers.IO) {
            photoUri?.takeIf { it.isNotBlank() }?.let { raw ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(raw))?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
        }
    }.value
    if (photo != null) {
        Image(
            bitmap = photo.asImageBitmap(),
            contentDescription = "الصورة الشخصية للموظف",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(R.drawable.golden),
            contentDescription = "الصورة الافتراضية للموظف",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

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
    var phone by remember(employee?.id) { mutableStateOf(employee?.phone.orEmpty()) }
    var shiftRate by remember(employee?.id) { mutableStateOf(employee?.extraShiftRatePiasters?.let { formatMoney(it) }.orEmpty()) }
    var baseSalary by remember(employee?.id) { mutableStateOf(employee?.baseSalaryPiasters?.let { formatMoney(it) }.orEmpty()) }
    var shiftStartTime by remember(employee?.id) { mutableStateOf(employee?.shiftStartTime ?: "08:00") }
    var weeklyOffDays by remember(employee?.id) { mutableStateOf(AttendancePolicy.parseWeeklyOffDays(employee?.weeklyOffDays.orEmpty())) }
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
                        }
            }
            HorizontalDivider(color = Color(0xFF4A3D24))
            SectionTitle("البيانات الأساسية")
            GoldenField("اسم الموظف *", name, { name = it; errors = errors - "fullName" }, errors["fullName"], "الاسم بالكامل")
            GoldenField("رقم هاتف واتساب", phone, { phone = it; errors = errors - "phone" }, errors["phone"], "مثال: 2010XXXXXXXX", KeyboardType.Phone)
            OutlinedFieldBlock("كود الموظف", employee?.code ?: "يُنشأ تلقائيًا عند الحفظ")
            ExposedDropdownMenuBox(expanded = jobExpanded, onExpandedChange = { jobExpanded = !jobExpanded }) {
                OutlinedTextField(
                    value = job?.arabicLabel.orEmpty(), onValueChange = {}, readOnly = true,
                    label = { Text("الوظيفة *") }, placeholder = { Text("اختر الوظيفة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = jobExpanded) },
                    isError = errors.containsKey("jobTitle"),
                    modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp), colors = fieldColors()
                )
                ExposedDropdownMenu(expanded = jobExpanded, onDismissRequest = { jobExpanded = false }) {
                    JobTitle.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.arabicLabel) }, onClick = { job = option; jobExpanded = false; errors = errors - "jobTitle" })
                    }
                }
            }
            GoldenField("تاريخ التعيين", hireDate, { hireDate = it; errors = errors - "hireDate" }, errors["hireDate"], "YYYY-MM-DD (اختياري)")
            SectionTitle("بيانات إضافية — اختيارية")
            GoldenField("الرقم القومي", nationalId, { nationalId = it; errors = errors - "nationalId" }, errors["nationalId"], "14 رقمًا", KeyboardType.Number)
            GoldenField("العنوان", address, { address = it }, null, "عنوان الموظف")
            SectionTitle("المرفقات — اختيارية")
            AttachmentRow("الصورة الشخصية", profilePhoto != null, if (profilePhoto == null) "اختر صورة" else "تغيير") { profilePicker.launch(arrayOf("image/*")) }
            AttachmentRow("بطاقة الرقم القومي — الوجه الأمامي", idFront != null, if (idFront == null) "اختر صورة" else "تغيير") { frontPicker.launch(arrayOf("image/*")) }
            AttachmentRow("بطاقة الرقم القومي — الوجه الخلفي", idBack != null, if (idBack == null) "اختر صورة" else "تغيير") { backPicker.launch(arrayOf("image/*")) }
            SectionTitle("مواعيد الوردية والإجازة الأسبوعية")
            Text("يُفترض الحضور تلقائيًا في يوم العمل ما لم تسجّل غيابًا يدويًا. لا يلزم تسجيل الانصراف.", color = Muted, fontSize = 12.sp)
            GoldenField("موعد بداية الوردية *", shiftStartTime, { shiftStartTime = it; errors = errors - "shiftStartTime" }, errors["shiftStartTime"], "مثال: 08:00")
            Text("حدد أيام الإجازة الأسبوعية المدفوعة (لن يُخصم عنها أي مبلغ):", color = Color.White, fontSize = 13.sp)
            val weekdays = listOf(
                java.time.DayOfWeek.SATURDAY to "السبت",
                java.time.DayOfWeek.SUNDAY to "الأحد",
                java.time.DayOfWeek.MONDAY to "الإثنين",
                java.time.DayOfWeek.TUESDAY to "الثلاثاء",
                java.time.DayOfWeek.WEDNESDAY to "الأربعاء",
                java.time.DayOfWeek.THURSDAY to "الخميس",
                java.time.DayOfWeek.FRIDAY to "الجمعة"
            )
            weekdays.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (day, label) ->
                        Row(Modifier.weight(1f).clickable {
                            weeklyOffDays = if (day in weeklyOffDays) weeklyOffDays - day else weeklyOffDays + day
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = day in weeklyOffDays,
                                onCheckedChange = { checked ->
                                    weeklyOffDays = if (checked) weeklyOffDays + day else weeklyOffDays - day
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Muted, checkmarkColor = Black)
                            )
                            Text(label, color = Color.White, fontSize = 12.sp)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SectionTitle("بيانات الأجر")
            GoldenField("الراتب الأساسي الشهري بالجنيه *", baseSalary, { baseSalary = it; errors = errors - "baseSalary" }, errors["baseSalary"], "مثال: 8000", KeyboardType.Decimal)
            GoldenField("سعر الوردية الإضافية بالجنيه *", shiftRate, { shiftRate = it; errors = errors - "extraShiftRate" }, errors["extraShiftRate"], "مثال: 250", KeyboardType.Decimal)
            Button(
                enabled = !saving,
                onClick = {
                    val draft = EmployeeDraft(fullName = name, phone = phone, jobTitle = job, hireDate = hireDate, nationalId = nationalId, address = address, profilePhotoUri = profilePhoto, nationalIdFrontUri = idFront, nationalIdBackUri = idBack, extraShiftRate = shiftRate, baseSalary = baseSalary, shiftStartTime = shiftStartTime, weeklyOffDays = weeklyOffDays)
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
    error: String?, placeholder: String, keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        placeholder = { Text(placeholder, color = Muted) }, modifier = Modifier.fillMaxWidth(),
        singleLine = label != "العنوان",
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), isError = error != null,
        supportingText = { if (error != null) Text(error) },
        shape = RoundedCornerShape(14.dp), colors = fieldColors()
    )
}

@Composable
private fun OutlinedFieldBlock(label: String, value: String) {
    OutlinedTextField(
        value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
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
