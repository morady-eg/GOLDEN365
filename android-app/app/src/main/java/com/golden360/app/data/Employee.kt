package com.golden360.app.data

/** الوظائف المتاحة في GOLDEN 360. */
enum class JobTitle(val arabicLabel: String) {
    ADMIN("إداري"), SUPERVISOR("مشرف"), TECHNICIAN("فني"), ASSISTANT("مساعد")
}

data class EmployeeDraft(
    val fullName: String,
    val phone: String = "",
    val jobTitle: JobTitle?,
    val hireDate: String = "",
    val nationalId: String = "",
    val address: String = "",
    val profilePhotoUri: String? = null,
    val nationalIdFrontUri: String? = null,
    val nationalIdBackUri: String? = null,
    val extraShiftRate: String,
    val baseSalary: String = ""
)

data class Employee(
    val id: Long = 0,
    val code: String,
    val fullName: String,
    val phone: String = "",
    val jobTitle: JobTitle,
    val hireDate: String? = null,
    val nationalId: String? = null,
    val address: String? = null,
    val profilePhotoUri: String? = null,
    val nationalIdFrontUri: String? = null,
    val nationalIdBackUri: String? = null,
    val extraShiftRatePiasters: Long,
    val baseSalaryPiasters: Long,
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
        validatePositiveMoney(draft.baseSalary, "baseSalary", "الراتب الأساسي", errors)
        validatePositiveMoney(draft.extraShiftRate, "extraShiftRate", "سعر الوردية الإضافية", errors)
        val phoneDigits = draft.phone.filter { it.isDigit() }
        if (phoneDigits.isNotEmpty() && !phoneDigits.matches(Regex("01[0125][0-9]{8}|20(10|11|12|15)[0-9]{8}|[1-9][0-9]{9,14}"))) {
            errors["phone"] = "رقم الهاتف غير صحيح؛ أدخل رقمًا محليًا أو دوليًا مع كود الدولة"
        }
        val hireDate = draft.hireDate.trim()
        if (hireDate.isNotEmpty() && runCatching { java.time.LocalDate.parse(hireDate) }.isFailure) {
            errors["hireDate"] = "تاريخ التعيين يجب أن يكون بصيغة YYYY-MM-DD"
        }
        val nationalId = draft.nationalId.trim()
        if (nationalId.isNotEmpty() && !nationalId.matches(Regex("[0-9]{14}"))) {
            errors["nationalId"] = "الرقم القومي يجب أن يتكون من 14 رقمًا"
        }
        return EmployeeValidationResult(errors)
    }

    private fun validatePositiveMoney(
        raw: String,
        key: String,
        label: String,
        errors: MutableMap<String, String>
    ) {
        val value = raw.trim().replace(',', '.')
        val parsed = value.toBigDecimalOrNull()
        if (value.isEmpty()) {
            errors[key] = "$label إجباري"
        } else if (parsed == null || parsed <= java.math.BigDecimal.ZERO) {
            errors[key] = "أدخل $label صحيحًا أكبر من صفر"
        } else if (parsed.scale().coerceAtLeast(0) > 2) {
            errors[key] = "$label يقبل منزلتين عشريتين كحد أقصى"
        }
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
