package com.golden360.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class TransactionDatePolicyTest {
    @Test fun missingDateDefaultsToToday() {
        val today = LocalDate.of(2026, 10, 19)
        assertEquals("2026-10-19", TransactionDatePolicy.resolve(null, today))
        assertEquals("2026-10-19", TransactionDatePolicy.resolve("", today))
        assertEquals("2026-10-19", TransactionDatePolicy.resolve("   ", today))
    }

    @Test fun selectedDateIsPreserved() {
        assertEquals(
            "2026-10-04",
            TransactionDatePolicy.resolve("2026-10-04", LocalDate.of(2026, 10, 19))
        )
    }

    @Test fun invalidSelectedDateIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionDatePolicy.resolve("2026-02-30", LocalDate.of(2026, 10, 19))
        }
    }
}
