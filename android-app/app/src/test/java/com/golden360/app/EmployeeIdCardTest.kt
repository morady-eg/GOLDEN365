package com.golden360.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmployeeIdCardTest {
    @Test
    fun cardUsesStandardPortraitCr80Dimensions() {
        assertEquals(54, EmployeeIdCardSpec.WIDTH_MM)
        assertEquals(86, EmployeeIdCardSpec.HEIGHT_MM)
        assertTrue(EmployeeIdCardSpec.HEIGHT_MM > EmployeeIdCardSpec.WIDTH_MM)
    }

    @Test
    fun qrCodeIsGeneratedForEmployeeCode() {
        val matrix = createEmployeeQrMatrix("G365-0042")

        assertEquals(180, matrix.width)
        assertEquals(180, matrix.height)
        var blackModules = 0
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) blackModules++
            }
        }
        assertTrue("QR code should contain encoded modules", blackModules > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun qrCodeRejectsBlankEmployeeCode() {
        createEmployeeQrMatrix("   ")
    }
    
    @Test
    fun allPagesRangeDoesNotExpandAnUnboundedRange() {
        assertEquals(listOf(0, 1, 2), employeeIdRequestedPageIndices(3, listOf(0..Int.MAX_VALUE)))
        assertEquals(listOf(1), employeeIdRequestedPageIndices(3, listOf(1..1)))
    }

    @Test
    fun batchPrintingCreatesOnePagePerSelectedEmployee() {
        assertEquals(0, employeeIdPrintPageCount(0))
        assertEquals(1, employeeIdPrintPageCount(1))
        assertEquals(5, employeeIdPrintPageCount(5))
    }
}
