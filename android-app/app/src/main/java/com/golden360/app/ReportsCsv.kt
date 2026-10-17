package com.golden360.app

import java.util.Locale

internal object ReportsCsv {
    fun toCsv(rows: List<List<String>>): String =
        "\uFEFF" + rows.joinToString("\r\n") { row ->
            row.joinToString(",") { value ->
                if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
                    "\"" + value.replace("\"", "\"\"") + "\""
                } else {
                    value
                }
            }
        }

    fun formatPiasters(amount: Long): String {
        val absolute = kotlin.math.abs(amount)
        val whole = absolute / 100
        val fraction = absolute % 100
        val sign = if (amount < 0) "-" else ""
        return String.format(Locale.ROOT, "%s%d.%02d", sign, whole, fraction)
    }
}
