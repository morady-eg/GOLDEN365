package com.golden360.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PayrollCalculatorTest {
    @Test fun calculatesSalaryUsingThirtyDayMonthAndAllComponents() {
        val result = PayrollCalculator.calculate(
            baseSalaryPiasters = 300_000,
            absentDays = 2,
            penaltiesPiasters = 2_500,
            advancesPiasters = 10_000,
            extraShifts = 3,
            extraShiftRatePiasters = 5_000,
            bonusesPiasters = 1_500
        )
        assertEquals(277_500L, result.absenceDeductionPiasters)
        assertEquals(274_000L, result.netSalaryPiasters)
    }

    @Test fun absenceDeductionUsesThirtyDaysEvenForThirtyOneDayMonth() {
        val result = PayrollCalculator.calculate(
            baseSalaryPiasters = 300_000,
            absentDays = 1,
            penaltiesPiasters = 0,
            advancesPiasters = 0,
            extraShifts = 0,
            extraShiftRatePiasters = 0,
            bonusesPiasters = 0
        )
        assertEquals(10_000L, result.absenceDeductionPiasters)
        assertEquals(290_000L, result.netSalaryPiasters)
    }

    @Test fun rejectsAbsenceDaysOutsideThirtyDayBasis() {
        assertThrows(IllegalArgumentException::class.java) {
            PayrollCalculator.calculate(300_000, 31, 0, 0, 0, 0, 0)
        }
    }

    @Test fun generatesDistinctTypedReferences() {
        assertEquals("ADV-000001", TransactionReferenceFormatter.format(TransactionType.ADVANCE, 1))
        assertEquals("PEN-000009", TransactionReferenceFormatter.format(TransactionType.PENALTY, 9))
        assertEquals("BON-000010", TransactionReferenceFormatter.format(TransactionType.BONUS, 10))
        assertEquals("OT-000011", TransactionReferenceFormatter.format(TransactionType.OVERTIME, 11))
    }

    @Test fun rejectsNonPositiveReferenceSequence() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionReferenceFormatter.format(TransactionType.ADVANCE, 0)
        }
    }
}
