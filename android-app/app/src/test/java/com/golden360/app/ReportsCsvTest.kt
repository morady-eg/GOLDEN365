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
    fun csvNeutralizesSpreadsheetFormulasButPreservesNegativeAmounts() {
        val csv = ReportsCsv.toCsv(
            listOf(
                listOf("القيمة"),
                listOf("=1+1"),
                listOf("@SUM(A1:A2)"),
                listOf("-20.00")
            )
        )

        assertEquals("\uFEFFالقيمة\r\n'=1+1\r\n'@SUM(A1:A2)\r\n-20.00", csv)
    }

    @Test
    fun csvPreservesEmptyCellsAndNegativeAmounts() {
        val csv = ReportsCsv.toCsv(listOf(listOf("المرجع", "الصافي", "ملاحظة"), listOf("ADV-001", "-20.00", "")))

        assertEquals("\uFEFFالمرجع,الصافي,ملاحظة\r\nADV-001,-20.00,", csv)
    }
}
