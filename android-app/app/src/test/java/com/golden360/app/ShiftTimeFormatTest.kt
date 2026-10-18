package com.golden360.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftTimeFormatTest {
    @Test
    fun midnightUsesTwelveAmInDisplay() {
        assertEquals("12:00 ص", formatShiftTimeForDisplay("00:00"))
    }

    @Test
    fun noonUsesTwelvePmInDisplay() {
        assertEquals("12:00 م", formatShiftTimeForDisplay("12:00"))
    }

    @Test
    fun afternoonUsesTwelveHourClockAndPmMarker() {
        assertEquals("01:05 م", formatShiftTimeForDisplay("13:05"))
    }

    @Test
    fun morningUsesAmMarker() {
        assertEquals("08:30 ص", formatShiftTimeForDisplay("08:30"))
    }

    @Test
    fun malformedStoredTimeUsesSafeDefault() {
        assertEquals("08:00 ص", formatShiftTimeForDisplay("invalid"))
    }
}
