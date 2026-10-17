package com.golden360.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.golden360.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.util.Locale

private data class MonthlyPayrollRow(
    val employee: EmployeeEntity,
    val absentDays: Int,
    val totals: PayrollOverviewTotals,
    val approved: Boolean
)

@Composable
internal fun PayrollOverviewScreen(
    employees: List<EmployeeEntity>,
    dao: PayrollTransactionDao,
    modifier: Modifier = Modifier,
    initialMonth: String = YearMonth.now().toString(),
    onOpenTransactions: (TransactionType, Long?, String) -> Unit
) {
    var month by remember(initialMonth) { mutableStateOf(runCatching { YearMonth.parse(initialMonth) }.getOrDefault(YearMonth.now())) }
    var rows by remember { mutableStateOf<List<MonthlyPayrollRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(employees, month, refreshToken) {
        loading = true
        rows = emptyList()
        error = null
        try {
            rows = withContext(Dispatchers.IO) {
                employees.map { employee ->
                    val key = month.toString()
                    val saved = dao.getPayrollSnapshot(employee.id, key)
                    val attendance = dao.getAttendance(employee.id, key)
                    val absences = dao.getDailyAbsences(employee.id, key)
                    val offDays = AttendancePolicy.parseWeeklyOffDays(employee.weeklyOffDays)
                    val absentDays = (attendance?.legacyAbsentDays ?: 0) + absences.count { row ->
                        !AttendancePolicy.isWeeklyOff(java.time.LocalDate.parse(row.dateKey), offDays)
                    }
                    val amounts = TransactionType.entries.associateWith { type ->
                        dao.sumForEmployeeMonth(employee.id, key, type.name)
                    }
                    val result = if (saved != null) {
                        PayrollOverviewTotals(
                            saved.baseSalaryPiasters, saved.absenceDeductionPiasters, saved.advancesPiasters,
                            saved.penaltiesPiasters, saved.overtimePiasters, saved.bonusesPiasters, saved.netSalaryPiasters
                        )
                    } else {
                        val summary = PayrollSummaryCalculator.calculate(
                            employee.baseSalaryPiasters, absentDays,
                            amounts[TransactionType.PENALTY] ?: 0L,
                            amounts[TransactionType.ADVANCE] ?: 0L,
                            amounts[TransactionType.OVERTIME] ?: 0L,
                            amounts[TransactionType.BONUS] ?: 0L
                        )
                        PayrollOverviewTotals(
                            employee.baseSalaryPiasters, summary.absenceDeductionPiasters,
                            amounts[TransactionType.ADVANCE] ?: 0L, amounts[TransactionType.PENALTY] ?: 0L,
                            amounts[TransactionType.OVERTIME] ?: 0L, amounts[TransactionType.BONUS] ?: 0L,
                            summary.netSalaryPiasters
                        )
                    }
                    MonthlyPayrollRow(employee, absentDays, result, saved != null)
                }
            }
        } catch (failure: Exception) {
            rows = emptyList()
            error = "تعذر تحميل كشف الرواتب لهذا الشهر. ${failure.message ?: "حاول تحديث الشاشة."}"
        } finally {
            loading = false
        }
    }

    val totals = remember(rows) { rows.fold(PayrollOverviewTotals()) { acc, row -> acc + row.totals } }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("كشف الرواتب الشهري", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("ملخص جميع الموظفين والخصومات والإضافات وصافي المستحقات", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = { refreshToken++ }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = SoftGold)
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF403625)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronRight, contentDescription = "الشهر السابق", tint = SoftGold) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(month.toString(), color = SoftGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("شهر كشف الرواتب", color = Muted, fontSize = 11.sp)
                }
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronLeft, contentDescription = "الشهر التالي", tint = SoftGold) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("الموظفون", rows.size.toString(), Modifier.weight(1f))
            SummaryTile("المعتمد", rows.count { it.approved }.toString(), Modifier.weight(1f))
            SummaryTile("مراجعة", rows.count { PayrollPolicy.requiresManualReview(it.totals.netSalaryPiasters) }.toString(), Modifier.weight(1f))
        }
        SummaryTile("إجمالي صافي الرواتب", money(totals.netSalaryPiasters), Modifier.fillMaxWidth(), true)
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ملخص المبالغ", color = SoftGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                AmountLine("إجمالي الرواتب الأساسية", totals.baseSalaryPiasters)
                AmountLine("خصم الغياب", totals.absenceDeductionPiasters)
                AmountLine("السلف", totals.advancesPiasters)
                AmountLine("الجزاءات", totals.penaltiesPiasters)
                AmountLine("الورديات الإضافية", totals.overtimePiasters, true)
                AmountLine("المكافآت", totals.bonusesPiasters, true)
                HorizontalDivider(color = Color(0xFF4A3D24))
                AmountLine("صافي المستحقات", totals.netSalaryPiasters, emphasis = true)
            }
        }
        Button(onClick = { onOpenTransactions(TransactionType.ADVANCE, null, month.toString()) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)) {
            Icon(Icons.Default.ReceiptLong, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("فتح سجل الحركات المالية", fontWeight = FontWeight.Bold)
        }
        Text("تفاصيل الموظفين", color = SoftGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        when {
            loading -> Text("جارٍ حساب كشف الرواتب...", color = Muted)
            error != null -> Text(error!!, color = Color(0xFFFF8A80))
            rows.isEmpty() -> Text("لا يوجد موظفون نشطون لعرض رواتبهم.", color = Muted)
            else -> rows.forEach { row ->
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (row.approved) Color(0xFF5D7544) else Color(0xFF403625)), modifier = Modifier.fillMaxWidth().clickable {
                    onOpenTransactions(TransactionType.ADVANCE, row.employee.id, month.toString())
                }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.employee.fullName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${row.employee.code} • ${row.employee.jobTitle}", color = Muted, fontSize = 11.sp)
                            }
                            Text(if (row.approved) "معتمد" else "غير معتمد", color = if (row.approved) Color(0xFFB7D88A) else SoftGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        AmountLine("الراتب الأساسي", row.totals.baseSalaryPiasters)
                        AmountLine("خصم الغياب (${row.absentDays} يوم)", row.totals.absenceDeductionPiasters)
                        AmountLine("السلف والجزاءات", Math.addExact(row.totals.advancesPiasters, row.totals.penaltiesPiasters))
                        AmountLine("الإضافات", Math.addExact(row.totals.overtimePiasters, row.totals.bonusesPiasters), true)
                        HorizontalDivider(color = Color(0xFF403625))
                        AmountLine("صافي الراتب", row.totals.netSalaryPiasters, emphasis = true)
                        if (PayrollPolicy.requiresManualReview(row.totals.netSalaryPiasters)) Text("تنبيه: صافي الراتب سالب ويحتاج مراجعة قبل الصرف.", color = Color(0xFFFF8A80), fontSize = 12.sp)
                        Text("اضغط لفتح التفاصيل ومراجعة أو اعتماد الكشف", color = SoftGold, fontSize = 11.sp)
                    }
                }
            }
        }
        Text("الكشف غير المعتمد يتحدث مع تغيّر الحركات. بعد الاعتماد تُعرض القيم المحفوظة في لقطة الراتب.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Surface(color = if (highlight) Color(0xFF2B2416) else Panel, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, if (highlight) Color(0xFF8F753B) else Color(0xFF403625)), modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(value, color = if (highlight) SoftGold else Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AmountLine(label: String, amountPiasters: Long, positive: Boolean = false, emphasis: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (emphasis) SoftGold else Muted, fontSize = if (emphasis) 13.sp else 12.sp, modifier = Modifier.weight(1f))
        Text(money(amountPiasters), color = when { emphasis -> SoftGold; positive -> Color(0xFFB7D88A); else -> Color.White }, fontSize = if (emphasis) 14.sp else 12.sp, fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Medium)
    }
}

private fun money(piasters: Long): String = String.format(Locale.US, "%,.2f ج.م", piasters / 100.0)
