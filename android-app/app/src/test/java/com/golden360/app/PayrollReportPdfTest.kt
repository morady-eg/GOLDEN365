package com.golden360.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PayrollReportPdfTest {
    @Test
    fun monthlyPayrollColumnsUseRequiredRightToLeftSequence() {
        assertEquals(
            listOf("الموظف", "الأساسي", "الورديات الإضافية", "المكافآت", "السلف", "خصم الغياب", "الجزاءات", "صافي المستحق"),
            MONTHLY_PAYROLL_HEADERS
        )
    }

    @Test
    fun monthlyPayrollEmployeeLabelContainsNameAndCodeWithoutJobTitle() {
        val employee = PayrollReportEmployee(
            name = "EMPLOYEE NAME",
            code = "G360-001",
            jobTitle = "SUPERVISOR",
            baseSalary = 100_000,
            overtime = 0,
            bonuses = 0,
            absenceDeduction = 0,
            advances = 0,
            penalties = 0,
            netSalary = 100_000
        )

        assertEquals("EMPLOYEE NAME\nG360-001", monthlyPayrollEmployeeLabel(employee))
    }
}
