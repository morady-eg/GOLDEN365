package com.golden360.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class AttendanceDayStatus {
    PAID_WEEKLY_OFF,
    MANUALLY_ABSENT,
    AUTOMATIC_PRESENT,
    SCHEDULED,
    NOT_YET_HIRED,
    TEMPORARILY_SUSPENDED,
    EMPLOYMENT_ENDED
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

    /** Stores suspension intervals as start|end; an empty end means still suspended. End dates are exclusive. */
    fun startSuspension(history: String, startDate: LocalDate): String {
        require(history.split(";").filter { it.isNotBlank() }.none { it.substringAfter("|", "").isEmpty() }) {
            "يوجد إيقاف مؤقت مفتوح بالفعل"
        }
        val lastEnd = history.split(";").lastOrNull()?.substringAfter("|", "")?.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
        require(lastEnd == null || !startDate.isBefore(lastEnd)) { "تاريخ الإيقاف لا يمكن أن يسبق نهاية الإيقاف السابق" }
        val entry = "$startDate|"
        return if (history.isBlank()) entry else "$history;$entry"
    }

    fun endSuspension(history: String, endDate: LocalDate): String {
        val entries = history.split(";").filter { it.isNotBlank() }.toMutableList()
        val index = entries.indexOfLast { it.substringAfter("|", "").isEmpty() }
        require(index >= 0) { "لا يوجد إيقاف مؤقت مفتوح" }
        val startDate = LocalDate.parse(entries[index].substringBefore("|"))
        require(!endDate.isBefore(startDate)) { "تاريخ الاستئناف لا يمكن أن يسبق تاريخ الإيقاف" }
        entries[index] = "$startDate|$endDate"
        return entries.joinToString(";")
    }

    fun isSuspendedOn(date: LocalDate, suspensionPeriods: String): Boolean =
        suspensionPeriods.split(";").filter { it.isNotBlank() }.any { entry ->
            val parts = entry.split("|", limit = 2)
            val start = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
            val rawEnd = parts.getOrNull(1).orEmpty()
            val end = rawEnd.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            // Only a genuinely empty end marks an open suspension. A malformed closed
            // interval must not silently become an indefinite suspension.
            val validEnd = rawEnd.isBlank() || end != null
            validEnd && start != null && !date.isBefore(start) && (end == null || date.isBefore(end))
        }

    fun isEmployeeInactiveOn(
        date: LocalDate,
        workStatus: String = "ACTIVE",
        statusEffectiveDate: LocalDate? = null,
        suspensionPeriods: String = ""
    ): Boolean {
        if (isSuspendedOn(date, suspensionPeriods)) return true
        if (workStatus == "TERMINATED" && statusEffectiveDate != null && !date.isBefore(statusEffectiveDate)) return true
        if (workStatus == "SUSPENDED" && statusEffectiveDate != null && !date.isBefore(statusEffectiveDate)) return true
        return false
    }

    fun status(
        date: LocalDate,
        weeklyOffDays: Set<DayOfWeek>,
        absentDates: Set<LocalDate>,
        today: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now(),
        shiftStartTime: String = "08:00",
        hireDate: LocalDate? = null,
        workStatus: String = "ACTIVE",
        statusEffectiveDate: LocalDate? = null,
        suspensionPeriods: String = ""
    ): AttendanceDayStatus = when {
        hireDate != null && date.isBefore(hireDate) -> AttendanceDayStatus.NOT_YET_HIRED
        isEmployeeInactiveOn(date, workStatus, statusEffectiveDate, suspensionPeriods) &&
            (isSuspendedOn(date, suspensionPeriods) || workStatus == "SUSPENDED" && statusEffectiveDate != null && !date.isBefore(statusEffectiveDate)) ->
            AttendanceDayStatus.TEMPORARILY_SUSPENDED
        workStatus == "TERMINATED" && statusEffectiveDate != null && !date.isBefore(statusEffectiveDate) ->
            AttendanceDayStatus.EMPLOYMENT_ENDED
        isWeeklyOff(date, weeklyOffDays) -> AttendanceDayStatus.PAID_WEEKLY_OFF
        date.isAfter(today) -> AttendanceDayStatus.SCHEDULED
        date == today && (!isValidShiftStartTime(shiftStartTime) || currentTime.isBefore(LocalTime.parse(shiftStartTime))) ->
            AttendanceDayStatus.SCHEDULED
        date in absentDates -> AttendanceDayStatus.MANUALLY_ABSENT
        else -> AttendanceDayStatus.AUTOMATIC_PRESENT
    }

    fun effectiveAbsenceDatesForMonth(
        month: YearMonth,
        weeklyOffDays: Set<DayOfWeek>,
        absentDates: Set<LocalDate>,
        hireDate: LocalDate? = null,
        workStatus: String = "ACTIVE",
        statusEffectiveDate: LocalDate? = null,
        suspensionPeriods: String = ""
    ): List<LocalDate> = absentDates.filter { date ->
        YearMonth.from(date) == month &&
            (hireDate == null || !date.isBefore(hireDate)) &&
            !isEmployeeInactiveOn(date, workStatus, statusEffectiveDate, suspensionPeriods) &&
            !isWeeklyOff(date, weeklyOffDays)
    }.sorted()

    fun absentDaysForMonth(
        month: YearMonth,
        weeklyOffDays: Set<DayOfWeek>,
        absentDates: Set<LocalDate>,
        hireDate: LocalDate? = null,
        workStatus: String = "ACTIVE",
        statusEffectiveDate: LocalDate? = null,
        suspensionPeriods: String = ""
    ): Int = effectiveAbsenceDatesForMonth(
        month, weeklyOffDays, absentDates, hireDate, workStatus, statusEffectiveDate, suspensionPeriods
    ).size

    /** Legacy undated absences are retained; dated absences before employment are excluded. */
    fun effectiveAbsentDaysForMonth(
        month: YearMonth,
        weeklyOffDays: Set<DayOfWeek>,
        absentDates: Set<LocalDate>,
        legacyAbsentDays: Int = 0,
        hireDate: LocalDate? = null,
        workStatus: String = "ACTIVE",
        statusEffectiveDate: LocalDate? = null,
        suspensionPeriods: String = ""
    ): Int {
        require(legacyAbsentDays in 0..30) { "Legacy absence days must be between 0 and 30" }
        // Older versions stored undated monthly absences. When both legacy and dated
        // records exist, their historical overlap cannot be reconstructed reliably.
        // Payroll uses a 30-day month, so never let legacy data make salary calculation
        // fail or deduct more than a full month's base salary.
        val combined = Math.addExact(
            legacyAbsentDays.toLong(),
            absentDaysForMonth(month, weeklyOffDays, absentDates, hireDate, workStatus, statusEffectiveDate, suspensionPeriods).toLong()
        )
        return combined.coerceAtMost(30L).toInt()
    }
}
