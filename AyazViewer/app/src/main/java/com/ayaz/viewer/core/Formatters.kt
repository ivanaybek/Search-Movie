package com.ayaz.viewer.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val TR: Locale = Locale.forLanguageTag("tr-TR")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", TR)
    private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", TR)
    private val DATE_THIS_YEAR = DateTimeFormatter.ofPattern("d MMM, HH:mm", TR)

    /** 1536 → "1,5 KB" (Türkçe ondalık virgülü). */
    fun size(bytes: Long): String {
        if (bytes < 0) return "Boyut bilinmiyor"
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024.0
            unit++
        }
        val text = if (value >= 100) String.format(TR, "%.0f", value) else String.format(TR, "%.1f", value)
        return "$text ${units[unit]}"
    }

    /** "Bugün 14:30", "Dün 09:12", "3 Mar, 10:00", "12 Ara 2023" */
    fun relativeDate(epochMs: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val dt = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date: LocalDate = dt.toLocalDate()
        return when {
            date == today -> "Bugün ${dt.format(TIME)}"
            date == today.minusDays(1) -> "Dün ${dt.format(TIME)}"
            date.year == today.year -> dt.format(DATE_THIS_YEAR)
            else -> dt.format(DATE)
        }
    }

    fun kindLabel(kind: FileKind): String = when (kind) {
        FileKind.PDF -> "PDF"
        FileKind.XLSX -> "Excel"
        FileKind.UNKNOWN -> "Dosya"
    }
}
