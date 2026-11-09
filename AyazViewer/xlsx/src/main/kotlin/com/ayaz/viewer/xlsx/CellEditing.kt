package com.ayaz.viewer.xlsx

import java.time.LocalDateTime

/** Hücre düzenleme kutusu için metin ↔ değer dönüşümleri. */
object CellEditing {

    /** Hücrenin düzenleme kutusunda gösterilecek hali (formüller "=" ile). */
    fun editText(sheet: SheetData, workbook: WorkbookData, locale: FormatLocale, row: Int, col: Int): String {
        sheet.formulaAt(row, col)?.let { return "=$it" }
        val rd = sheet.row(row) ?: return ""
        val i = rd.indexOf(col)
        if (i < 0) return ""
        val style = workbook.style(rd.styles[i])
        val fmt = NumberFormat.parse(style.formatCode ?: BuiltinFormats.code(style.numFmtId) ?: "General")
        return when (rd.types[i]) {
            CellType.NUMBER, CellType.DATE -> {
                val v = rd.numberAt(i)
                when {
                    rd.types[i] == CellType.DATE || fmt.isDateFormat -> dateText(v, workbook.date1904)
                    isPercent(style.formatCode) -> NumberFormat.formatGeneral(v * 100, locale) + "%"
                    else -> NumberFormat.formatGeneral(v, locale)
                }
            }
            CellType.SHARED_STRING -> protectText(workbook.sharedStrings.getOrElse(rd.values[i].toInt()) { "" }, locale)
            CellType.LOCAL_STRING -> protectText(sheet.localStrings.getOrElse(rd.values[i].toInt()) { "" }, locale)
            CellType.BOOLEAN -> if (rd.values[i] != 0L) locale.trueText else locale.falseText
            CellType.ERROR -> sheet.localStrings.getOrElse(rd.values[i].toInt()) { "" }
            else -> ""
        }
    }

    /** Hücrenin biçimi tarih biçimi mi (girilen tarihin seri numarası olarak yazılması için). */
    fun isDateStyle(sheet: SheetData, workbook: WorkbookData, row: Int, col: Int): Boolean {
        val rd = sheet.row(row) ?: return false
        val i = rd.indexOf(col)
        if (i < 0) return false
        val style = workbook.style(rd.styles[i])
        return NumberFormat.parse(style.formatCode ?: BuiltinFormats.code(style.numFmtId) ?: "General").isDateFormat
    }

    /** Kullanıcının yazdığı metni hücre değerine çevirir. */
    fun parseInput(text: String, locale: FormatLocale, dateStyle: Boolean, date1904: Boolean): CellInput {
        if (text.isBlank()) return CellInput.Empty
        if (text.startsWith("'")) return CellInput.Text(text.substring(1))
        if (text.startsWith("=") && text.length > 1) return CellInput.Formula(text.substring(1).trim())
        val t = text.trim()
        val upper = t.uppercase(java.util.Locale.forLanguageTag("tr-TR"))
        if (upper == locale.trueText || upper == "TRUE" || upper == "DOĞRU") return CellInput.Bool(true)
        if (upper == locale.falseText || upper == "FALSE" || upper == "YANLIŞ") return CellInput.Bool(false)
        if (dateStyle) parseDate(t, locale)?.let { return CellInput.Number(ExcelDate.fromDateTime(it, date1904)) }
        parseNumber(t, locale)?.let { return CellInput.Number(it) }
        return CellInput.Text(text)
    }

    fun parseNumber(s: String, locale: FormatLocale): Double? {
        var t = s.trim().replace(" ", "").replace(" ", "")
        if (t.isEmpty()) return null
        var percent = false
        if (t.endsWith("%")) { percent = true; t = t.dropLast(1) }
        val dec = locale.decimalSeparator
        val grp = locale.groupingSeparator
        val d = Regex.escape(dec.toString())
        val g = Regex.escape(grp.toString())
        val grouped = Regex("^[+-]?\\d{1,3}($g\\d{3})+($d\\d+)?$")
        val plain = Regex("^[+-]?(\\d+($d\\d*)?|$d\\d+)([eE][+-]?\\d+)?$")
        val normalized = when {
            grouped.matches(t) -> t.replace(grp.toString(), "").replace(dec, '.')
            plain.matches(t) -> t.replace(dec, '.')
            // Türkçe ayarda "1.5" gibi tek noktalı girişi de ondalık kabul et.
            dec == ',' && Regex("^[+-]?\\d+\\.\\d+$").matches(t) -> t
            else -> null
        } ?: return null
        val v = normalized.toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return if (percent) v / 100.0 else v
    }

    fun parseDate(s: String, locale: FormatLocale): LocalDateTime? {
        val m = Regex("^(\\d{1,4})[./-](\\d{1,2})[./-](\\d{1,4})(?:\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?$").find(s.trim()) ?: return null
        val g = m.groupValues
        val a = g[1].toInt()
        val b = g[2].toInt()
        val c = g[3].toInt()
        val (year, month, day) = when {
            g[1].length == 4 -> Triple(a, b, c) // yyyy-mm-dd
            locale.decimalSeparator == '.' && s.contains('/') -> Triple(c, a, b) // İngilizce: a/g/yyyy
            else -> Triple(c, b, a) // Türkçe: gg.aa.yyyy
        }
        val fullYear = if (year < 100) 2000 + year else year
        return try {
            LocalDateTime.of(
                fullYear, month, day,
                g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun dateText(serial: Double, date1904: Boolean): String {
        val dt = ExcelDate.toDateTime(serial, date1904) ?: return NumberFormat.formatGeneral(serial, FormatLocale.TURKISH)
        val d = "%02d.%02d.%04d".format(dt.dayOfMonth, dt.monthValue, dt.year)
        return when {
            dt.second != 0 -> d + " %02d:%02d:%02d".format(dt.hour, dt.minute, dt.second)
            dt.hour != 0 || dt.minute != 0 -> d + " %02d:%02d".format(dt.hour, dt.minute)
            else -> d
        }
    }

    private fun isPercent(code: String?): Boolean {
        if (code == null) return false
        var inQuote = false
        for (ch in code) {
            if (ch == '"') inQuote = !inQuote
            if (!inQuote && ch == '%') return true
        }
        return false
    }

    /** Sayı veya formül gibi görünen metinlerin metin olarak kalması için başına ' eklenir. */
    private fun protectText(s: String, locale: FormatLocale): String {
        val looksSpecial = s.startsWith("=") || s.startsWith("'") || parseNumber(s, locale) != null ||
            s.equals(locale.trueText, true) || s.equals(locale.falseText, true)
        return if (looksSpecial) "'$s" else s
    }
}
