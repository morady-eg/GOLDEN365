package com.golden360.app

import java.util.Locale

internal object ReportsCsv {
    fun toCsv(rows: List<List<String>>): String =
        "\uFEFF" + rows.joinToString("\r\n") { row ->
            row.joinToString(",") { rawValue ->
                val value = neutralizeSpreadsheetFormula(rawValue)
                if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
                    "\"" + value.replace("\"", "\"\"") + "\""
                } else {
                    value
                }
            }
        }

    /**
     * Prevent Excel/LibreOffice from evaluating user-controlled text as a formula.
     * Keep ordinary negative numeric values unchanged for correct financial reports.
     */
    private fun neutralizeSpreadsheetFormula(value: String): String {
        val first = value.dropWhile { it.isWhitespace() }.firstOrNull()
        val numericAmount = value.trim().matches(Regex("-?[0-9]+(\\.[0-9]+)?"))
        return if (first in setOf('=', '+', '-', '@') && !numericAmount) "'$value" else value
    }

    fun formatPiasters(amount: Long): String {
        val absolute = kotlin.math.abs(amount)
        val whole = absolute / 100
        val fraction = absolute % 100
        val sign = if (amount < 0) "-" else ""
        return String.format(Locale.ROOT, "%s%d.%02d", sign, whole, fraction)
    }
}
