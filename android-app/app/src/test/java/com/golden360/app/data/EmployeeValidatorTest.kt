package com.golden360.app.data

import org.junit.Assert.*
import org.junit.Test

class EmployeeValidatorTest {
    private fun validDraft() = EmployeeDraft(
        fullName = "أحمد محمد", jobTitle = JobTitle.TECHNICIAN, extraShiftRate = "250", baseSalary = "5000"
    )

    @Test fun rejectsBlankEmployeeName() {
        val result = EmployeeValidator.validate(validDraft().copy(fullName = "   "))
        assertEquals("اسم الموظف إجباري", result.errors["fullName"])
    }

    @Test fun rejectsMissingJobTitle() {
        assertTrue(EmployeeValidator.validate(validDraft().copy(jobTitle = null)).errors.containsKey("jobTitle"))
    }

    @Test fun rejectsMissingOrNonPositiveShiftRate() {
        assertTrue(EmployeeValidator.validate(validDraft().copy(extraShiftRate = "")).errors.containsKey("extraShiftRate"))
        assertTrue(EmployeeValidator.validate(validDraft().copy(extraShiftRate = "0")).errors.containsKey("extraShiftRate"))
        assertTrue(EmployeeValidator.validate(validDraft().copy(extraShiftRate = "-5")).errors.containsKey("extraShiftRate"))
    }

    @Test fun rejectsMissingOrNonPositiveBaseSalary() {
        assertTrue(EmployeeValidator.validate(validDraft().copy(baseSalary = "")).errors.containsKey("baseSalary"))
        assertTrue(EmployeeValidator.validate(validDraft().copy(baseSalary = "0")).errors.containsKey("baseSalary"))
        assertTrue(EmployeeValidator.validate(validDraft().copy(baseSalary = "-1")).errors.containsKey("baseSalary"))
    }

    @Test fun acceptsOptionalFieldsWhenBlank() {
        assertTrue(EmployeeValidator.validate(validDraft()).isValid)
    }

    @Test fun validatesNationalIdWhenProvided() {
        assertTrue(EmployeeValidator.validate(validDraft().copy(nationalId = "123")).errors.containsKey("nationalId"))
        assertFalse(EmployeeValidator.validate(validDraft().copy(nationalId = "12345678901234")).errors.containsKey("nationalId"))
    }

    @Test fun formatsUniqueSequenceWithGsPrefixAndSixDigits() {
        assertEquals("GS_000001", EmployeeCodeFormatter.format(1))
        assertEquals("GS_000042", EmployeeCodeFormatter.format(42))
        assertEquals("GS_1000000", EmployeeCodeFormatter.format(1_000_000))
    }

    @Test fun convertsMoneyToIntegerPiasters() {
        assertEquals(25000L, EmployeeValidator.toPiasters("250"))
        assertEquals(12550L, EmployeeValidator.toPiasters("125.50"))
        assertEquals(12550L, EmployeeValidator.toPiasters("125,50"))
        assertNull(EmployeeValidator.toPiasters("abc"))
        assertNull(EmployeeValidator.toPiasters("1.001"))
        assertNull(EmployeeValidator.toPiasters("999999999999999999999999"))
    }
}
