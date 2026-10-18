package com.golden360.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant

class DisplayDateTimeTest {
    @Test
    fun localTimestampUsesTwelveHourClockForMidnightAndAfternoon() {
        assertEquals("2026/10/18 12:05 AM", DisplayDateTime.formatLocal(LocalDateTime.of(2026, 10, 18, 0, 5)))
        assertEquals("2026/10/18 12:00 PM", DisplayDateTime.formatLocal(LocalDateTime.of(2026, 10, 18, 12, 0)))
        assertEquals("2026/10/18 01:07 PM", DisplayDateTime.formatLocal(LocalDateTime.of(2026, 10, 18, 13, 7)))
    }

    @Test
    fun shiftStartDisplayUsesArabicTwelveHourMarkers() {
        assertEquals("12:05 ص", formatShiftTimeForDisplay("00:05"))
        assertEquals("12:00 م", formatShiftTimeForDisplay("12:00"))
        assertEquals("01:07 م", formatShiftTimeForDisplay("13:07"))
    }

    @Test
    fun approvalAndTransactionTimestampsUseTwelveHourClock() {
        val zone = ZoneId.of("UTC")
        val instant = Instant.parse("2026-10-18T13:07:00Z").toEpochMilli()
        assertEquals("2026/10/18 - 01:07 PM", DisplayDateTime.formatApproval(instant, zone))
        assertEquals("2026-10-18 01:07 PM", DisplayDateTime.formatTransaction(instant, zone))
    }
}
