package com.golden360.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
    @Test fun batchApprovalAcceptsOnlyConsistentNonNegativeSnapshotsForOneMonth() {
        PayrollPolicy.validateBatchApproval(listOf(snapshot(employeeId = 1), snapshot(employeeId = 2)))
    }

    @Test fun batchApprovalRejectsNegativeNetSalaryEvenIfCalledOutsideTheUi() {
        assertThrows(IllegalArgumentException::class.java) {
            PayrollPolicy.validateBatchApproval(listOf(snapshot(employeeId = 1, advances = 15_000)))
        }
    }

    @Test fun batchApprovalRejectsDuplicateEmployeesAndMixedMonths() {
        assertThrows(IllegalArgumentException::class.java) {
            PayrollPolicy.validateBatchApproval(listOf(snapshot(employeeId = 1), snapshot(employeeId = 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PayrollPolicy.validateBatchApproval(listOf(snapshot(employeeId = 1), snapshot(employeeId = 2, month = "2026-11")))
        }
    }

    @Test fun approvalRejectsSnapshotWhoseNetDoesNotMatchItsLedger() {
        assertThrows(IllegalArgumentException::class.java) {
            PayrollPolicy.validateSnapshot(snapshot(employeeId = 1).copy(netSalaryPiasters = 1))
        }
    }

    private fun snapshot(
        employeeId: Long,
        month: String = "2026-10",
        advances: Long = 0L
    ) = PayrollSnapshotEntity(
        employeeId = employeeId,
        monthKey = month,
        employeeName = "Test Employee $employeeId",
        employeeCode = "GS_${employeeId.toString().padStart(6, '0')}",
        jobTitle = "TECHNICIAN",
        baseSalaryPiasters = 10_000L,
        absentDays = 0,
        absenceDeductionPiasters = 0L,
        advancesPiasters = advances,
        penaltiesPiasters = 0L,
        overtimePiasters = 0L,
        bonusesPiasters = 0L,
        netSalaryPiasters = 10_000L - advances,
        approvedAtEpochMillis = 0L
    )

}
