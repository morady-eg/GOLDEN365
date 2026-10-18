package com.golden360.app.data

/** Monetary totals displayed by the monthly payroll overview. All amounts are in piasters. */
data class PayrollOverviewTotals(
    val baseSalaryPiasters: Long = 0,
    val absenceDeductionPiasters: Long = 0,
    val advancesPiasters: Long = 0,
    val penaltiesPiasters: Long = 0,
    val overtimePiasters: Long = 0,
    val bonusesPiasters: Long = 0,
    val netSalaryPiasters: Long = 0
) {
    operator fun plus(other: PayrollOverviewTotals) = PayrollOverviewTotals(
        baseSalaryPiasters = Math.addExact(baseSalaryPiasters, other.baseSalaryPiasters),
        absenceDeductionPiasters = Math.addExact(absenceDeductionPiasters, other.absenceDeductionPiasters),
        advancesPiasters = Math.addExact(advancesPiasters, other.advancesPiasters),
        penaltiesPiasters = Math.addExact(penaltiesPiasters, other.penaltiesPiasters),
        overtimePiasters = Math.addExact(overtimePiasters, other.overtimePiasters),
        bonusesPiasters = Math.addExact(bonusesPiasters, other.bonusesPiasters),
        netSalaryPiasters = Math.addExact(netSalaryPiasters, other.netSalaryPiasters)
    )
}

/** Prefer the immutable snapshot's absence count after approval; otherwise use the live calculation. */
internal fun payrollAbsenceDaysForDisplay(snapshotAbsentDays: Int?, calculatedAbsentDays: Int): Int =
    snapshotAbsentDays ?: calculatedAbsentDays
