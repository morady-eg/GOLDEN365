package com.golden360.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class AttendancePolicyTest {
    @Test fun configuredWeeklyOffIsPaidAndNeverCountedAsAbsence() {
        val offDays = setOf(DayOfWeek.FRIDAY)
        val friday = LocalDate.parse("2026-10-16")
        assertTrue(AttendancePolicy.isWeeklyOff(friday, offDays))
        assertEquals(AttendanceDayStatus.PAID_WEEKLY_OFF, AttendancePolicy.status(friday, offDays, emptySet(), LocalDate.parse("2026-10-17")))
        assertEquals(AttendanceDayStatus.PAID_WEEKLY_OFF, AttendancePolicy.status(friday, offDays, setOf(friday), LocalDate.parse("2026-10-17")))
        assertEquals(0, AttendancePolicy.absentDaysForMonth(YearMonth.of(2026, 10), offDays, setOf(friday)))
    }

    @Test fun scheduledWorkdayIsAutomaticallyPresentUnlessMarkedAbsent() {
        val workday = LocalDate.parse("2026-10-18")
        assertEquals(
            AttendanceDayStatus.AUTOMATIC_PRESENT,
            AttendancePolicy.status(workday, setOf(DayOfWeek.FRIDAY), emptySet(), workday, LocalTime.of(9, 0), "08:00")
        )
        assertEquals(
            AttendanceDayStatus.MANUALLY_ABSENT,
            AttendancePolicy.status(workday, setOf(DayOfWeek.FRIDAY), setOf(workday), workday, LocalTime.of(9, 0), "08:00")
        )
    }

    @Test fun futureWorkdayIsScheduledNotAlreadyPresent() {
        val future = LocalDate.parse("2026-10-20")
        assertEquals(
            AttendanceDayStatus.SCHEDULED,
            AttendancePolicy.status(future, emptySet(), emptySet(), LocalDate.parse("2026-10-17"), LocalTime.NOON, "08:00")
        )
    }

    @Test fun todayBeforeConfiguredShiftStartIsNotYetMarkedPresent() {
        val today = LocalDate.parse("2026-10-18")
        assertEquals(
            AttendanceDayStatus.SCHEDULED,
            AttendancePolicy.status(today, emptySet(), emptySet(), today, LocalTime.of(7, 59), "08:00")
        )
    }

    @Test fun absenceRecordedBeforeTodaysShiftStartDoesNotOverrideScheduledStatus() {
        val today = LocalDate.parse("2026-10-18")
        assertEquals(
            AttendanceDayStatus.SCHEDULED,
            AttendancePolicy.status(today, emptySet(), setOf(today), today, LocalTime.of(7, 59), "08:00")
        )
        assertEquals(
            AttendanceDayStatus.MANUALLY_ABSENT,
            AttendancePolicy.status(today, emptySet(), setOf(today), today, LocalTime.of(8, 0), "08:00")
        )
    }

    @Test fun todayAtOrAfterShiftStartIsAutomaticallyPresentWithoutCheckout() {
        val today = LocalDate.parse("2026-10-18")
        assertEquals(
            AttendanceDayStatus.AUTOMATIC_PRESENT,
            AttendancePolicy.status(today, emptySet(), emptySet(), today, LocalTime.of(8, 0), "08:00")
        )
    }

    @Test fun invalidShiftStartDoesNotMarkTodayPresentAutomatically() {
        val today = LocalDate.parse("2026-10-18")
        assertEquals(
            AttendanceDayStatus.SCHEDULED,
            AttendancePolicy.status(today, emptySet(), emptySet(), today, LocalTime.NOON, "invalid")
        )
    }

    @Test fun weeklyOffDaysCanBeStoredAndRestoredWithoutLocaleDependence() {
        val selected = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        assertEquals(selected, AttendancePolicy.parseWeeklyOffDays(AttendancePolicy.serializeWeeklyOffDays(selected)))
    }

    @Test fun monthlyAbsenceCountIgnoresWeeklyOffDatesAndOtherMonths() {
        val month = YearMonth.of(2026, 10)
        val offDays = setOf(DayOfWeek.FRIDAY)
        val absences = setOf(
            LocalDate.parse("2026-10-15"),
            LocalDate.parse("2026-10-16"),
            LocalDate.parse("2026-10-19"),
            LocalDate.parse("2026-11-02")
        )
        assertEquals(2, AttendancePolicy.absentDaysForMonth(month, offDays, absences))
    }

    @Test fun datesBeforeHireAreNotWorkdaysAndDoNotCountAsAbsence() {
        val hireDate = LocalDate.parse("2026-10-15")
        val beforeHire = LocalDate.parse("2026-10-10")
        val month = YearMonth.of(2026, 10)
        assertEquals(
            AttendanceDayStatus.NOT_YET_HIRED,
            AttendancePolicy.status(beforeHire, emptySet(), emptySet(), LocalDate.parse("2026-10-20"), LocalTime.NOON, "08:00", hireDate)
        )
        assertEquals(
            1,
            AttendancePolicy.absentDaysForMonth(
                month,
                emptySet(),
                setOf(beforeHire, LocalDate.parse("2026-10-16")),
                hireDate
            )
        )
    }

    @Test fun rejectsInvalidShiftStartTime() {
        assertTrue(AttendancePolicy.isValidShiftStartTime("08:30"))
        assertFalse(AttendancePolicy.isValidShiftStartTime("25:30"))
        assertFalse(AttendancePolicy.isValidShiftStartTime("8:30"))
    }
}
