package com.ayaz.viewer.xlsx

/** Bir hücrenin ekranda gösterilecek hali. */
class CellDisplay(
    val text: String,
    /** Biçim kodundan gelen renk ([Red] gibi), yoksa null. */
    val formatColor: Int?,
    /** Varsayılan hizalama için: sayı/tarih sağa, metin sola, mantıksal/hata ortaya. */
    val kind: Kind,
) {
    enum class Kind { TEXT, NUMBER, BOOLEAN, ERROR }
}

/**
 * Hücre değerlerini biçim kodlarına göre metne çevirir. İş parçacığı güvenli değildir;
 * her tüketici kendi örneğini kullanmalıdır.
 */
class CellFormatter(private val workbook: WorkbookData, val locale: FormatLocale = FormatLocale.TURKISH) {

    private val formatCache = HashMap<Int, NumberFormat>()

    private fun formatFor(styleIndex: Int): NumberFormat =
        formatCache.getOrPut(styleIndex) {
            val st = workbook.style(styleIndex)
            NumberFormat.parse(st.formatCode ?: BuiltinFormats.code(st.numFmtId) ?: "General")
        }

    fun display(sheet: SheetData, row: RowData, i: Int): CellDisplay? {
        val style = row.styles[i]
        return when (row.types[i]) {
            CellType.NUMBER -> {
                val fv = formatFor(style).format(row.numberAt(i), workbook.date1904, locale)
                CellDisplay(fv.text, fv.color, CellDisplay.Kind.NUMBER)
            }
            CellType.DATE -> {
                val nf = formatFor(style).let { if (it.isDateFormat) it else DEFAULT_DATE }
                val fv = nf.format(row.numberAt(i), workbook.date1904, locale)
                CellDisplay(fv.text, fv.color, CellDisplay.Kind.NUMBER)
            }
            CellType.SHARED_STRING -> {
                val idx = row.values[i].toInt()
                val raw = workbook.sharedStrings.getOrElse(idx) { "" }
                val fv = formatFor(style).formatText(raw)
                CellDisplay(fv.text, fv.color, CellDisplay.Kind.TEXT)
            }
            CellType.LOCAL_STRING -> {
                val raw = sheet.localStrings.getOrElse(row.values[i].toInt()) { "" }
                val fv = formatFor(style).formatText(raw)
                CellDisplay(fv.text, fv.color, CellDisplay.Kind.TEXT)
            }
            CellType.BOOLEAN -> CellDisplay(
                if (row.values[i] != 0L) locale.trueText else locale.falseText, null, CellDisplay.Kind.BOOLEAN,
            )
            CellType.ERROR -> CellDisplay(
                sheet.localStrings.getOrElse(row.values[i].toInt()) { "#N/A" }, null, CellDisplay.Kind.ERROR,
            )
            else -> null
        }
    }

    fun display(sheet: SheetData, rowIndex: Int, col: Int): CellDisplay? {
        val row = sheet.row(rowIndex) ?: return null
        val i = row.indexOf(col)
        if (i < 0) return null
        return display(sheet, row, i)
    }

    /** Arama ve kopyalama için düz metin. */
    fun text(sheet: SheetData, rowIndex: Int, col: Int): String = display(sheet, rowIndex, col)?.text ?: ""

    companion object {
        private val DEFAULT_DATE = NumberFormat.parse("dd.mm.yyyy hh:mm")
    }
}

/** Basit hücre araması: büyük/küçük harf ve Türkçe karakter duyarsız. */
object SheetSearch {

    fun normalize(s: String): String = s.lowercase(TR).replace('ı', 'i').replace('İ', 'i')
        .replace("i̇", "i")

    private val TR: java.util.Locale = java.util.Locale.forLanguageTag("tr-TR")

    /**
     * [query] içeren hücreleri satır-sütun sırasıyla döndürür (en fazla [limit]).
     * Sonuç: (satır shl 32) or sütun.
     */
    fun find(
        sheet: SheetData,
        formatter: CellFormatter,
        query: String,
        limit: Int = 10_000,
        isCancelled: () -> Boolean = { false },
    ): LongArray {
        val q = normalize(query.trim())
        if (q.isEmpty()) return LongArray(0)
        val out = ArrayList<Long>()
        for (r in 0 until sheet.rowCount) {
            if ((r and 0x3FF) == 0 && isCancelled()) throw ParseCancelledException()
            val row = sheet.row(r) ?: continue
            for (i in 0 until row.size) {
                if (row.types[i] == CellType.EMPTY) continue
                val c = row.cols[i]
                if (c >= sheet.colCount) break
                val text = formatter.display(sheet, row, i)?.text ?: continue
                if (normalize(text).contains(q)) {
                    out.add((r.toLong() shl 32) or c.toLong())
                    if (out.size >= limit) return out.toLongArray()
                }
            }
        }
        return out.toLongArray()
    }
}
