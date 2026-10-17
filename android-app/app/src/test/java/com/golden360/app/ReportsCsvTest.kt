package com.golden360.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsCsvTest {
    @Test
    fun csvIncludesUtf8BomAndUsesExcelCompatibleLineEndings() {
        val csv = ReportsCsv.toCsv(listOf(listOf("الموظف", "المبلغ"), listOf("أحمد", "125.50")))

        assertEquals("\uFEFFالموظف,المبلغ\r\nأحمد,125.50", csv)
    }

    @Test
    fun csvEscapesCommasQuotesAndNewlinesWithoutLosingText() {
        val csv = ReportsCsv.toCsv(listOf(listOf("ملاحظة"), listOf("سلفة, قال \"موافق\"\nتم")))

        assertEquals("\uFEFFملاحظة\r\n\"سلفة, قال \"\"موافق\"\"\nتم\"", csv)
    }

    @Test
    fun csvPreservesEmptyCellsAndNegativeAmounts() {
        val csv = ReportsCsv.toCsv(listOf(listOf("المرجع", "الصافي", "ملاحظة"), listOf("ADV-001", "-20.00", "")))

        assertEquals("\uFEFFالمرجع,الصافي,ملاحظة\r\nADV-001,-20.00,", csv)
    }
}
