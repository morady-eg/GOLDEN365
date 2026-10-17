package com.golden360.app.data

/**
 * Calculates net pay from amounts already recorded in the ledger, preserving
 * the historical overtime rate used when each overtime entry was created.
 */
object PayrollSummaryCalculator {
    fun calculate(
        baseSalaryPiasters: Long,
        absentDays: Int,
        penaltiesPiasters: Long,
        advancesPiasters: Long,
        overtimePiasters: Long,
        bonusesPiasters: Long
    ): PayrollResult {
        require(baseSalaryPiasters >= 0) { "Base salary cannot be negative" }
        require(absentDays in 0..30) { "Absence days must be between 0 and 30" }
        require(listOf(penaltiesPiasters, advancesPiasters, overtimePiasters, bonusesPiasters).all { it >= 0 }) {
            "Ledger amounts cannot be negative"
        }
        val absenceDeduction = Math.multiplyExact(baseSalaryPiasters, absentDays.toLong()) / 30L
        val deductions = Math.addExact(Math.addExact(absenceDeduction, penaltiesPiasters), advancesPiasters)
        val additions = Math.addExact(overtimePiasters, bonusesPiasters)
        val net = Math.addExact(Math.subtractExact(baseSalaryPiasters, deductions), additions)
        return PayrollResult(
            absenceDeductionPiasters = absenceDeduction,
            overtimePayPiasters = overtimePiasters,
            netSalaryPiasters = net
        )
    }
}

/**
 * Payroll policy: never silently clamp an employee's net salary to zero.
 * A negative result is preserved as a balance requiring manual review before payment.
 */
object PayrollPolicy {
    fun requiresManualReview(netSalaryPiasters: Long): Boolean = netSalaryPiasters < 0L
}
