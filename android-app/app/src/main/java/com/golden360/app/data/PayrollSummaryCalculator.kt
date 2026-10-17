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
        val absenceDeduction = AbsenceDeductionCalculator.calculate(baseSalaryPiasters, absentDays)
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
    private val monthKeyPattern = Regex("[0-9]{4}-(0[1-9]|1[0-2])")

    fun requiresManualReview(netSalaryPiasters: Long): Boolean = netSalaryPiasters < 0L

    /** Validate snapshot invariants at the data layer, not only in the UI. */
    fun validateSnapshot(snapshot: PayrollSnapshotEntity) {
        require(snapshot.employeeId > 0) { "معرّف الموظف غير صحيح" }
        require(snapshot.monthKey.matches(monthKeyPattern)) { "الشهر يجب أن يكون بصيغة YYYY-MM" }
        require(snapshot.employeeName.isNotBlank() && snapshot.employeeCode.isNotBlank() && snapshot.jobTitle.isNotBlank()) {
            "بيانات الموظف الأساسية ناقصة في كشف الراتب"
        }
        require(snapshot.baseSalaryPiasters >= 0L) { "الراتب الأساسي لا يمكن أن يكون سالبًا" }
        require(snapshot.absentDays in 0..30) { "أيام الغياب يجب أن تكون من 0 إلى 30" }
        require(
            listOf(
                snapshot.absenceDeductionPiasters,
                snapshot.advancesPiasters,
                snapshot.penaltiesPiasters,
                snapshot.overtimePiasters,
                snapshot.bonusesPiasters
            ).all { it >= 0L }
        ) { "مبالغ الخصم والإضافات لا يمكن أن تكون سالبة" }

        val calculated = PayrollSummaryCalculator.calculate(
            baseSalaryPiasters = snapshot.baseSalaryPiasters,
            absentDays = snapshot.absentDays,
            penaltiesPiasters = snapshot.penaltiesPiasters,
            advancesPiasters = snapshot.advancesPiasters,
            overtimePiasters = snapshot.overtimePiasters,
            bonusesPiasters = snapshot.bonusesPiasters
        )
        require(snapshot.absenceDeductionPiasters == calculated.absenceDeductionPiasters &&
            snapshot.netSalaryPiasters == calculated.netSalaryPiasters
        ) { "إجمالي كشف الراتب لا يتطابق مع تفاصيله؛ أعد حساب الكشف قبل الاعتماد" }
    }

    /** Batch approval is intentionally stricter: negative balances require individual review. */
    fun validateBatchApproval(snapshots: List<PayrollSnapshotEntity>) {
        require(snapshots.isNotEmpty()) { "لا توجد كشوف جديدة للاعتماد" }
        require(snapshots.map { it.monthKey }.distinct().size == 1) {
            "يجب أن تنتمي جميع الكشوف إلى شهر واحد صحيح"
        }
        require(snapshots.map { it.employeeId }.distinct().size == snapshots.size) {
            "يوجد موظف مكرر في قائمة الاعتماد"
        }
        snapshots.forEach(::validateSnapshot)
        require(snapshots.none { requiresManualReview(it.netSalaryPiasters) }) {
            "لا يمكن اعتماد دفعة تحتوي على صافي راتب سالب؛ يجب مراجعة الكشف واعتماده بشكل منفصل"
        }
    }
}
