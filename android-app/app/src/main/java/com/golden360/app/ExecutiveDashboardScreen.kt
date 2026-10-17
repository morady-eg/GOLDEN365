package com.golden360.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class DashboardMonthSummary(
    val payroll: PayrollOverviewTotals = PayrollOverviewTotals(),
    val approvedEmployees: Int = 0,
    val absentDays: Int = 0,
    val employeesNeedingReview: Int = 0
)

@Composable
internal fun ExecutiveDashboardScreen(
    employees: List<EmployeeEntity>,
    dao: PayrollTransactionDao,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onAddEmployee: () -> Unit,
    onEmployees: () -> Unit,
    onPayroll: () -> Unit,
    onAttendance: () -> Unit,
    onOpenTransaction: (TransactionType) -> Unit
) {
    val month = remember { YearMonth.now() }
    val monthKey = month.toString()
    val monthLabel = remember(month) {
        month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ar")))
    }
    var summary by remember { mutableStateOf(DashboardMonthSummary()) }
    var summaryLoading by remember { mutableStateOf(true) }
    var summaryError by remember { mutableStateOf(false) }

    LaunchedEffect(employees, monthKey) {
        summaryLoading = true
        summaryError = false
        try {
            summary = withContext(Dispatchers.IO) {
                var payroll = PayrollOverviewTotals()
                var approvedCount = 0
                var absencesTotal = 0
                var reviewCount = 0
                employees.forEach { employee ->
                    val saved = dao.getPayrollSnapshot(employee.id, monthKey)
                    val attendance = dao.getAttendance(employee.id, monthKey)
                    val absences = dao.getDailyAbsences(employee.id, monthKey)
                    val weeklyOff = AttendancePolicy.parseWeeklyOffDays(employee.weeklyOffDays)
                    val absentDays = (attendance?.legacyAbsentDays ?: 0) + absences.count { row ->
                        !AttendancePolicy.isWeeklyOff(LocalDate.parse(row.dateKey), weeklyOff)
                    }
                    absencesTotal += absentDays
                    val amounts = TransactionType.entries.associateWith { type ->
                        dao.sumForEmployeeMonth(employee.id, monthKey, type.name)
                    }
                    val totals = if (saved != null) {
                        approvedCount++
                        PayrollOverviewTotals(
                            saved.baseSalaryPiasters, saved.absenceDeductionPiasters,
                            saved.advancesPiasters, saved.penaltiesPiasters,
                            saved.overtimePiasters, saved.bonusesPiasters, saved.netSalaryPiasters
                        )
                    } else {
                        val result = PayrollSummaryCalculator.calculate(
                            baseSalaryPiasters = employee.baseSalaryPiasters,
                            absentDays = absentDays,
                            penaltiesPiasters = amounts[TransactionType.PENALTY] ?: 0L,
                            advancesPiasters = amounts[TransactionType.ADVANCE] ?: 0L,
                            overtimePiasters = amounts[TransactionType.OVERTIME] ?: 0L,
                            bonusesPiasters = amounts[TransactionType.BONUS] ?: 0L
                        )
                        PayrollOverviewTotals(
                            employee.baseSalaryPiasters, result.absenceDeductionPiasters,
                            amounts[TransactionType.ADVANCE] ?: 0L,
                            amounts[TransactionType.PENALTY] ?: 0L,
                            amounts[TransactionType.OVERTIME] ?: 0L,
                            amounts[TransactionType.BONUS] ?: 0L,
                            result.netSalaryPiasters
                        )
                    }
                    if (PayrollPolicy.requiresManualReview(totals.netSalaryPiasters)) reviewCount++
                    payroll += totals
                }
                DashboardMonthSummary(payroll, approvedCount, absencesTotal, reviewCount)
            }
        } catch (_: Exception) {
            summaryError = true
        } finally {
            summaryLoading = false
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF171511),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF6B5428))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("GOLDEN SHINE MARBLE", color = SoftGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("لوحة الإدارة", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                        Text("نظرة تنفيذية على الموظفين والرواتب", color = Muted, fontSize = 12.sp)
                    }
                    Box(
                        Modifier.size(58.dp).background(Color(0xFF342919), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = SoftGold, modifier = Modifier.size(30.dp))
                    }
                }
                HorizontalDivider(color = Color(0xFF514020))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الفترة المالية", color = Muted, fontSize = 11.sp)
                        Text(monthLabel, color = SoftGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = Color(0xFF302718), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF72592A))) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(7.dp).background(Color(0xFF8FC69A), CircleShape))
                            Text("بيانات على الجهاز", color = SoftGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ملخص الشهر", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("أرقام محسوبة من السجلات الحالية", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = onPayroll) { Text("تفاصيل الرواتب", color = SoftGold) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("الموظفون", if (loading) "—" else employees.size.toString(), "ملف نشط", Icons.Default.People, Modifier.weight(1f))
            DashboardMetric("اعتماد الرواتب", if (summaryLoading) "—" else "${summary.approvedEmployees} / ${employees.size}", "موظف معتمد", Icons.Default.ReceiptLong, Modifier.weight(1f))
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPayroll),
            color = Color(0xFF211B10),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF80652F))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("صافي رواتب الشهر", color = Muted, fontSize = 12.sp)
                Text(if (summaryLoading || summaryError) "—" else dashboardMoney(summary.payroll.netSalaryPiasters), color = SoftGold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (summaryError) "تعذر تحميل الملخص؛ افتح الرواتب وأعد المحاولة" else "اضغط لعرض الكشف التفصيلي وحالة الاعتماد", color = Muted, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardMetric("أيام الغياب", if (summaryLoading || summaryError) "—" else summary.absentDays.toString(), "هذا الشهر", Icons.Default.AccessTime, Modifier.weight(1f))
            DashboardMetric("تحتاج مراجعة", if (summaryLoading || summaryError) "—" else summary.employeesNeedingReview.toString(), "صافي سالب", Icons.Default.Gavel, Modifier.weight(1f))
        }

        Text("إجراءات سريعة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        DashboardAction("إدارة الموظفين", "إضافة ملفات الموظفين وتحديث بياناتهم", Icons.Default.People, onEmployees)
        DashboardAction("مراجعة واعتماد الرواتب", "الكشف الشهري والتقارير وحالة الاعتماد", Icons.Default.ReceiptLong, onPayroll)
        DashboardAction("الحضور والغياب", "مراجعة تسجيل الغياب حسب الورديات", Icons.Default.AccessTime, onAttendance)

        Text("تسجيل حركة مالية", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardAction("سلفة", "إضافة سلفة", Icons.Default.Payments, { onOpenTransaction(TransactionType.ADVANCE) }, Modifier.weight(1f))
            DashboardAction("مكافأة", "إضافة مكافأة", Icons.Default.Redeem, { onOpenTransaction(TransactionType.BONUS) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DashboardAction("جزاء", "تسجيل جزاء", Icons.Default.Gavel, { onOpenTransaction(TransactionType.PENALTY) }, Modifier.weight(1f))
            DashboardAction("وردية إضافية", "تسجيل وردية", Icons.Default.AccessTime, { onOpenTransaction(TransactionType.OVERTIME) }, Modifier.weight(1f))
        }
        OutlinedButton(onClick = onAddEmployee, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftGold)) {
            Icon(Icons.Default.People, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("إضافة موظف جديد")
        }
        if (summaryError) Text("حدث خطأ أثناء قراءة ملخص الشهر. لم يتم عرض أرقام تقديرية.", color = Color(0xFFFFA39A), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DashboardMetric(title: String, value: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF40351F))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = SoftGold, modifier = Modifier.size(23.dp))
            Text(title, color = Muted, fontSize = 11.sp)
            Text(value, color = SoftGold, fontSize = if (value.length > 12) 13.sp else 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DashboardAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), color = Color(0xFF1A1711), shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Color(0xFF514020))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).background(Color(0xFF342919), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = SoftGold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = Gold)
        }
    }
}

private fun dashboardMoney(piasters: Long): String =
    java.math.BigDecimal.valueOf(piasters, 2).stripTrailingZeros().toPlainString() + " ج.م"
