package com.golden360.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayrollPolicyTest {
    @Test fun negativeNetSalaryIsPreservedAndFlaggedForManualReview() {
        val result = PayrollSummaryCalculator.calculate(
            baseSalaryPiasters = 100_000,
            absentDays = 0,
            penaltiesPiasters = 20_000,
            advancesPiasters = 100_000,
            overtimePiasters = 0,
            bonusesPiasters = 0
        )

        assertEquals(-20_000L, result.netSalaryPiasters)
        assertTrue(PayrollPolicy.requiresManualReview(result.netSalaryPiasters))
    }

    @Test fun nonNegativeNetSalaryDoesNotRequireManualReview() {
        assertFalse(PayrollPolicy.requiresManualReview(0))
        assertFalse(PayrollPolicy.requiresManualReview(1))
    }
}
