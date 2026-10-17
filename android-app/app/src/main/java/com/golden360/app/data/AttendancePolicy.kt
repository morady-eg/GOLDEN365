package com.golden360.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class AttendanceDayStatus {
    PAID_WEEKLY_OFF,
    MANUALLY_ABSENT,
    AUTOMATIC_PRESENT,
    SCHEDULED
}

/**
 * Attendance is schedule-based: every configured working day defaults to present
 * unless an authorized user explicitly records an absence. No checkout is required.
 * Weekly off days are paid and can never be counted as absence.
 */
object AttendancePolicy {
    fun isValidShiftStartTime(value: String): Boolean =
        value.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))

    fun serializeWeeklyOffDays(days: Set<DayOfWeek>): String =
        days.map { it.name }.sorted().joinToString(",")

    fun parseWeeklyOffDays(value: String): Set<DayOfWeek> =
        value.split(",")
            .mapNotNull { token -> runCatching { DayOfWeek.valueOf(token.trim().uppercase()) }.getOrNull() }
            .toSet()

    fun isWeeklyOff(date: LocalDate, weeklyOffDays: Set<DayOfWeek>): Boolean =
        date.dayOfWeek in weeklyOffDays

    fun status(
        date: LocalDate,
        weeklyOffDays: Set<DayOfWeek>,
        absentDates: Set<LocalDate>,
        today: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now(),
        shiftStartTime: String = "08:00"
    ): AttendanceDayStatus = when {
        isWeeklyOff(date, weeklyOffDays) -> AttendanceDayStatus.PAID_WEEKLY_OFF
        date.isAfter(today) -> AttendanceDayStatus.SCHEDULED
        date == today && isValidShiftStartTime(shiftStartTime) && currentTime.isBefore(LocalTime.parse(shiftStartTime)) ->
            AttendanceDayStatus.SCHEDULED
        date in absentDates -> AttendanceDayStatus.MANUALLY_ABSENT
        else -> AttendanceDayStatus.AUTOMATIC_PRESENT
    }

    fun absentDaysForMonth(
        month: YearMonth,
        weeklyOffDays: Set<DayOfWeek>,
        absentDates: Set<LocalDate>
    ): Int = absentDates.count { date ->
        YearMonth.from(date) == month && !isWeeklyOff(date, weeklyOffDays)
    }
}
