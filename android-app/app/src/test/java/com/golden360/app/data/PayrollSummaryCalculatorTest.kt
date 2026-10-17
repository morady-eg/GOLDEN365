package com.golden360.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PayrollSummaryCalculatorTest {
    @Test fun calculatesNetSalaryFromRecordedMonthlyAmounts() {
        val result = PayrollSummaryCalculator.calculate(
            baseSalaryPiasters = 300_000,
            absentDays = 2,
            penaltiesPiasters = 2_500,
            advancesPiasters = 10_000,
            overtimePiasters = 15_000,
            bonusesPiasters = 1_500
        )
        assertEquals(20_000L, result.absenceDeductionPiasters)
        assertEquals(15_000L, result.overtimePayPiasters)
        assertEquals(284_000L, result.netSalaryPiasters)
    }

    @Test fun rejectsMoreThanThirtyAbsenceDays() {
        assertThrows(IllegalArgumentException::class.java) {
            PayrollSummaryCalculator.calculate(100_000, 31, 0, 0, 0, 0)
        }
    }

    @Test fun rejectsNegativeLedgerAmounts() {
        assertThrows(IllegalArgumentException::class.java) {
            PayrollSummaryCalculator.calculate(100_000, 0, -1, 0, 0, 0)
        }
    }

    @Test fun handlesZeroAbsenceAndZeroLedgerAmounts() {
        val result = PayrollSummaryCalculator.calculate(123_456, 0, 0, 0, 0, 0)
        assertEquals(0L, result.absenceDeductionPiasters)
        assertEquals(123_456L, result.netSalaryPiasters)
    }

    @Test fun rejectsArithmeticOverflowInsteadOfSilentlyWrapping() {
        assertThrows(ArithmeticException::class.java) {
            PayrollSummaryCalculator.calculate(Long.MAX_VALUE, 2, 0, 0, 0, 0)
        }
    }
}
