package com.golden360.app.data

/** الوظائف المتاحة في GOLDEN 360. */
enum class JobTitle(val arabicLabel: String) {
    ADMIN("إداري"), SUPERVISOR("مشرف"), TECHNICIAN("فني"), ASSISTANT("مساعد")
}

data class EmployeeDraft(
    val fullName: String,
    val jobTitle: JobTitle?,
    val hireDate: String = "",
    val nationalId: String = "",
    val address: String = "",
    val profilePhotoUri: String? = null,
    val nationalIdFrontUri: String? = null,
    val nationalIdBackUri: String? = null,
    val extraShiftRate: String
)

data class Employee(
    val id: Long = 0,
    val code: String,
    val fullName: String,
    val jobTitle: JobTitle,
    val hireDate: String? = null,
    val nationalId: String? = null,
    val address: String? = null,
    val profilePhotoUri: String? = null,
    val nationalIdFrontUri: String? = null,
    val nationalIdBackUri: String? = null,
    val extraShiftRatePiasters: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

data class EmployeeValidationResult(val errors: Map<String, String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object EmployeeValidator {
    fun validate(draft: EmployeeDraft): EmployeeValidationResult {
        val errors = linkedMapOf<String, String>()
        if (draft.fullName.trim().isEmpty()) errors["fullName"] = "اسم الموظف إجباري"
        if (draft.jobTitle == null) errors["jobTitle"] = "اختر الوظيفة"
        val rate = draft.extraShiftRate.trim().replace(',', '.')
        val parsed = rate.toBigDecimalOrNull()
        if (rate.isEmpty()) {
            errors["extraShiftRate"] = "سعر الوردية الإضافية إجباري"
        } else if (parsed == null || parsed <= java.math.BigDecimal.ZERO) {
            errors["extraShiftRate"] = "أدخل سعرًا صحيحًا أكبر من صفر"
        } else if (parsed.scale().coerceAtLeast(0) > 2) {
            errors["extraShiftRate"] = "السعر يقبل منزلتين عشريتين كحد أقصى"
        }
        val nationalId = draft.nationalId.trim()
        if (nationalId.isNotEmpty() && !nationalId.matches(Regex("[0-9]{14}"))) {
            errors["nationalId"] = "الرقم القومي يجب أن يتكون من 14 رقمًا"
        }
        return EmployeeValidationResult(errors)
    }

    fun toPiasters(amount: String): Long? = runCatching {
        amount.trim().replace(',', '.').toBigDecimal().movePointRight(2).longValueExact()
    }.getOrNull()
}

object EmployeeCodeFormatter {
    fun format(sequence: Long): String {
        require(sequence > 0) { "sequence must be positive" }
        return "GS_" + sequence.toString().padStart(6, '0')
    }
}
