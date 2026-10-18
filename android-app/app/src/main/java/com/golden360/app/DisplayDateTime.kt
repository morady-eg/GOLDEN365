package com.golden360.app

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Centralized, locale-stable 12-hour display formatting for reports. Stored shift times remain 24-hour values. */
internal object DisplayDateTime {
    private val localFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd hh:mm a", Locale.ENGLISH)
    private val approvalFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd - hh:mm a", Locale.ENGLISH)
    private val transactionFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a", Locale.ENGLISH)

    fun formatLocal(value: LocalDateTime): String = localFormatter.format(value)

    fun formatApproval(epochMillis: Long, zoneId: ZoneId): String =
        approvalFormatter.withZone(zoneId).format(Instant.ofEpochMilli(epochMillis))

    fun formatTransaction(epochMillis: Long, zoneId: ZoneId): String =
        transactionFormatter.withZone(zoneId).format(Instant.ofEpochMilli(epochMillis))
}
