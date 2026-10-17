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
    var searchMode by remember { mutableStateOf(false) }
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
                            IconButton(onClick = { searchMode = !searchMode; if (!searchMode) searchQuery = "" }) {
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
                    searchQuery.isBlank() ||
                    it.fullName.contains(searchQuery.trim(), ignoreCase = true) ||
                    it.code.contains(searchQuery.trim(), ignoreCase = true)
                },
                loading = loading,
                modifier = Modifier.padding(padding),
                onEdit = { editing = it; showForm = true },
                onRefresh = { scope.launch { runCatching { refresh() }.onFailure { snackbar.showSnackbar("تعذر تحديث القائمة") } } },
                searchMode = searchMode,
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )
            activeTab == "more" -> MoreServicesScreen(
                modifier = Modifier.padding(padding),
                onOpen = { openTransactions(it) }
            )
            activeTab == "payroll" -> PayrollOverviewScreen(
                modifier = Modifier.padding(padding),
                onOpenTransactions = { openTransactions(com.golden360.app.data.TransactionType.ADVANCE) }
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
private fun PayrollOverviewScreen(
    modifier: Modifier = Modifier,
    onOpenTransactions: () -> Unit
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("الرواتب", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("مراجعة الرواتب وسجل العمليات المالية من مكان واحد.", color = Muted, fontSize = 13.sp)
        Surface(
            color = Panel,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFF403625)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = SoftGold, modifier = Modifier.size(30.dp))
                Text("سجل المعاملات المالية", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("راجع السلف والجزاءات والمكافآت والورديات الإضافية من سجل واحد.", color = Muted, fontSize = 12.sp)
                Button(
                    onClick = onOpenTransactions,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)
                ) {
                    Text("فتح سجل المعاملات", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronLeft, contentDescription = null)
                }
            }
        }
        Text("لإضافة سلفة أو جزاء أو وردية إضافية، افتح «المزيد» ثم اختر نوع العملية.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MoreServicesScreen(modifier: Modifier = Modifier, onOpen: (com.golden360.app.data.TransactionType) -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("المعاملات والخدمات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("كل عمليات الإضافة والخصم في مكان واحد، بدون تكرارها في باقي الشاشات.", color = Muted, fontSize = 13.sp)
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
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EmployeeListScreen(
    employees: List<EmployeeEntity>,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onEdit: (EmployeeEntity) -> Unit,
    onRefresh: () -> Unit,
    searchMode: Boolean = false,
    query: String = "",
    onQueryChange: (String) -> Unit = {}
) {
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
    var phone by remember(employee?.id) { mutableStateOf(employee?.phone.orEmpty()) }
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
                        }
            }
            HorizontalDivider(color = Color(0xFF4A3D24))
            SectionTitle("البيانات الأساسية")
            GoldenField("اسم الموظف *", name, { name = it; errors = errors - "fullName" }, errors["fullName"], "الاسم بالكامل")
            GoldenField("رقم هاتف واتساب", phone, { phone = it }, null, "مثال: 2010XXXXXXXX", KeyboardType.Phone)
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
            GoldenField("تاريخ التعيين", hireDate, { hireDate = it }, null, "YYYY-MM-DD (اختياري)")
            SectionTitle("بيانات إضافية — اختيارية")
            GoldenField("الرقم القومي", nationalId, { nationalId = it; errors = errors - "nationalId" }, errors["nationalId"], "14 رقمًا", KeyboardType.Number)
            GoldenField("العنوان", address, { address = it }, null, "عنوان الموظف")
            SectionTitle("المرفقات — اختيارية")
            AttachmentRow("الصورة الشخصية", profilePhoto != null, if (profilePhoto == null) "اختر صورة" else "تغيير") { profilePicker.launch(arrayOf("image/*")) }
            AttachmentRow("بطاقة الرقم القومي — الوجه الأمامي", idFront != null, if (idFront == null) "اختر صورة" else "تغيير") { frontPicker.launch(arrayOf("image/*")) }
            AttachmentRow("بطاقة الرقم القومي — الوجه الخلفي", idBack != null, if (idBack == null) "اختر صورة" else "تغيير") { backPicker.launch(arrayOf("image/*")) }
            SectionTitle("بيانات الأجر")
            GoldenField("الراتب الأساسي الشهري بالجنيه *", baseSalary, { baseSalary = it; errors = errors - "baseSalary" }, errors["baseSalary"], "مثال: 8000", KeyboardType.Decimal)
            GoldenField("سعر الوردية الإضافية بالجنيه *", shiftRate, { shiftRate = it; errors = errors - "extraShiftRate" }, errors["extraShiftRate"], "مثال: 250", KeyboardType.Decimal)
            Button(
                enabled = !saving,
                onClick = {
                    val draft = EmployeeDraft(fullName = name, phone = phone, jobTitle = job, hireDate = hireDate, nationalId = nationalId, address = address, profilePhotoUri = profilePhoto, nationalIdFrontUri = idFront, nationalIdBackUri = idBack, extraShiftRate = shiftRate, baseSalary = baseSalary)
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
