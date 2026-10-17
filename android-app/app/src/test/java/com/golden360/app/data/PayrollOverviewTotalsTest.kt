package com.golden360.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PayrollOverviewTotalsTest {
    @Test fun aggregatesAllMonthlyPayrollColumnsWithoutLosingNegativeBalances() {
        val first = PayrollOverviewTotals(300_000, 10_000, 5_000, 2_000, 15_000, 1_000, 299_000)
        val second = PayrollOverviewTotals(200_000, 0, 10_000, 0, 0, 500, -9_500)
        assertEquals(
            PayrollOverviewTotals(500_000, 10_000, 15_000, 2_000, 15_000, 1_500, 289_500),
            first + second
        )
    }

    @Test fun emptyMonthTotalsAreZero() {
        assertEquals(PayrollOverviewTotals(), PayrollOverviewTotals() + PayrollOverviewTotals())
    }
}
