package com.golden360.app.data

enum class TransactionType(val referencePrefix: String, val arabicLabel: String) {
    ADVANCE("ADV", "سلفة"),
    PENALTY("PEN", "جزاء"),
    BONUS("BON", "مكافأة"),
    OVERTIME("OT", "وردية إضافية")
}

object TransactionDatePolicy {
    /** Returns a validated ISO date, defaulting to the supplied local date when no date was chosen. */
    fun resolve(selectedDate: String?, today: java.time.LocalDate = java.time.LocalDate.now()): String {
        val candidate = selectedDate?.trim().takeUnless { it.isNullOrEmpty() } ?: return today.toString()
        return runCatching { java.time.LocalDate.parse(candidate).toString() }
            .getOrElse { throw IllegalArgumentException("Transaction date must use YYYY-MM-DD", it) }
    }
}

object TransactionReferenceFormatter {
    fun format(type: TransactionType, sequence: Long): String {
        require(sequence > 0) { "sequence must be positive" }
        return "${type.referencePrefix}-" + sequence.toString().padStart(6, '0')
    }
}

data class PayrollResult(
    val absenceDeductionPiasters: Long,
    val overtimePayPiasters: Long,
    val netSalaryPiasters: Long
)

object AbsenceDeductionCalculator {
    private const val DAYS_PER_MONTH = 30L

    /**
     * Computes floor(base salary × absent days / 30) without overflowing the
     * intermediate multiplication for large but valid salary values.
     */
    fun calculate(baseSalaryPiasters: Long, absentDays: Int): Long {
        require(baseSalaryPiasters >= 0L) { "Base salary cannot be negative" }
        require(absentDays in 0..DAYS_PER_MONTH.toInt()) { "Absence days must be between 0 and 30" }
        val days = absentDays.toLong()
        val whole = Math.multiplyExact(baseSalaryPiasters / DAYS_PER_MONTH, days)
        val remainder = (baseSalaryPiasters % DAYS_PER_MONTH) * days / DAYS_PER_MONTH
        return Math.addExact(whole, remainder)
    }
}

object PayrollCalculator {
    private const val DAYS_PER_MONTH = 30

    /**
     * All money values are integer piasters to avoid floating-point rounding.
     * Advances are deducted in full in the month in which they are recorded.
     */
    fun calculate(
        baseSalaryPiasters: Long,
        absentDays: Int,
        penaltiesPiasters: Long,
        advancesPiasters: Long,
        extraShifts: Int,
        extraShiftRatePiasters: Long,
        bonusesPiasters: Long
    ): PayrollResult {
        require(baseSalaryPiasters >= 0) { "Base salary cannot be negative" }
        require(absentDays in 0..DAYS_PER_MONTH) { "Absence days must be between 0 and 30" }
        require(penaltiesPiasters >= 0 && advancesPiasters >= 0 && bonusesPiasters >= 0) {
            "Amounts cannot be negative"
        }
        require(extraShifts >= 0 && extraShiftRatePiasters >= 0) {
            "Overtime values cannot be negative"
        }

        val absenceDeduction = AbsenceDeductionCalculator.calculate(baseSalaryPiasters, absentDays)
        val overtimePay = Math.multiplyExact(extraShifts.toLong(), extraShiftRatePiasters)
        val deductions = Math.addExact(Math.addExact(absenceDeduction, penaltiesPiasters), advancesPiasters)
        val additions = Math.addExact(overtimePay, bonusesPiasters)
        val net = Math.addExact(Math.subtractExact(baseSalaryPiasters, deductions), additions)
        return PayrollResult(absenceDeduction, overtimePay, net)
    }
}
