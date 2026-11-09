package com.ayaz.viewer.xlsx

enum class HAlign { GENERAL, LEFT, CENTER, RIGHT, FILL, JUSTIFY, CENTER_CONTINUOUS, DISTRIBUTED }

enum class VAlign { TOP, CENTER, BOTTOM, JUSTIFY, DISTRIBUTED }

/** Kenarlık çizgisi. [weight] 1=ince, 2=orta, 3=kalın. Renk ARGB, null = otomatik (siyah). */
data class BorderSide(val weight: Int, val color: Int?, val dashed: Boolean = false, val double: Boolean = false)

/** Bir hücre biçiminin (cellXfs) çözümlenmiş hali. Renkler ARGB tamsayıdır. */
data class CellStyle(
    val numFmtId: Int = 0,
    val formatCode: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val fontColor: Int? = null,
    val fontSizePt: Float = 11f,
    val fontName: String? = null,
    val fillColor: Int? = null,
    val hAlign: HAlign = HAlign.GENERAL,
    val vAlign: VAlign = VAlign.BOTTOM,
    val wrapText: Boolean = false,
    val indent: Int = 0,
    val borderLeft: BorderSide? = null,
    val borderRight: BorderSide? = null,
    val borderTop: BorderSide? = null,
    val borderBottom: BorderSide? = null,
) {
    /** Değeri olmasa bile görünür bir etki bırakan biçim (dolgu veya kenarlık). */
    val isVisuallyNonEmpty: Boolean
        get() = fillColor != null || borderLeft != null || borderRight != null ||
            borderTop != null || borderBottom != null

    companion object {
        val DEFAULT = CellStyle()
    }
}

/** Hücre değer tipleri. */
object CellType {
    const val EMPTY: Byte = 0
    const val NUMBER: Byte = 1
    /** Değer = paylaşılan dize (sharedStrings) indeksi. */
    const val SHARED_STRING: Byte = 2
    const val BOOLEAN: Byte = 3
    /** Değer = sayfaya özel dize tablosu indeksi (hata metni). */
    const val ERROR: Byte = 4
    /** ISO tarihten (t="d") dönüştürülmüş Excel seri numarası. */
    const val DATE: Byte = 5
    /** Değer = sayfaya özel dize tablosu indeksi (satır içi dize / formül metni). */
    const val LOCAL_STRING: Byte = 6
}

/**
 * Bir satırdaki dolu hücreler; bellek dostu olması için ilkel diziler kullanır.
 * Sütunlar artan sıradadır.
 */
class RowData internal constructor(
    val cols: IntArray,
    val types: ByteArray,
    val styles: IntArray,
    /** NUMBER/DATE için Double bitleri; dize tipleri için indeks; BOOLEAN için 0/1. */
    val values: LongArray,
    val size: Int,
) {
    /** [col] sütunundaki hücrenin dizi içindeki konumu; yoksa -1. */
    fun indexOf(col: Int): Int {
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = cols[mid]
            when {
                v < col -> lo = mid + 1
                v > col -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    /** Sütunu [col] veya daha büyük olan ilk hücrenin konumu (yoksa [size]). */
    fun firstIndexAtOrAfter(col: Int): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cols[mid] < col) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun numberAt(i: Int): Double = java.lang.Double.longBitsToDouble(values[i])
}

/** Çalışma kitabındaki bir sayfa girdisi. */
data class SheetEntry(
    val name: String,
    val path: String?,
    val hidden: Boolean,
    /** Grafik sayfası, makro sayfası vb. tablo olmayan sayfalar. */
    val isWorksheet: Boolean,
)

/** Çalışma kitabı genel bilgileri (sayfa listesi, biçimler, paylaşılan dizeler). */
class WorkbookData internal constructor(
    val sheets: List<SheetEntry>,
    val sharedStrings: Array<String>,
    val styles: List<CellStyle>,
    val date1904: Boolean,
    /** Biçimlerin dolgu/kenarlık içerip içermediği (stil indeksine göre). */
    internal val visibleStyle: BooleanArray,
) {
    fun style(index: Int): CellStyle = if (index in styles.indices) styles[index] else CellStyle.DEFAULT
}

/** Ayrıştırılmış bir çalışma sayfası. */
class SheetData internal constructor(
    val name: String,
    val rowCount: Int,
    val colCount: Int,
    private val rows: Array<RowData?>,
    val localStrings: Array<String>,
    /** Satır yükseklikleri (punto). NaN = varsayılan, 0 = gizli. */
    private val rowHeights: FloatArray,
    /** Sütun genişlikleri (karakter birimi). NaN = varsayılan, 0 = gizli. */
    private val colWidths: FloatArray,
    val defaultRowHeightPt: Float,
    val defaultColWidthChars: Float,
    val merges: List<CellRange>,
    val frozenRows: Int,
    val frozenCols: Int,
    val showGridLines: Boolean,
    val tabColor: Int?,
    /** Hücre sınırı aşıldığı için sayfanın yalnızca bir kısmı okunduysa true. */
    val truncated: Boolean,
    val cellCount: Int,
    /** Hücre formülleri ((satır shl 32) or sütun → "=" olmadan formül metni). */
    internal val formulas: Map<Long, String> = emptyMap(),
) {
    /** Hücredeki formül ("=" olmadan) veya null. */
    fun formulaAt(row: Int, col: Int): String? = formulas[(row.toLong() shl 32) or col.toLong()]

    fun row(index: Int): RowData? = if (index in 0 until rowCount) rows[index] else null

    fun rowHeightPt(index: Int): Float {
        if (index !in rowHeights.indices) return defaultRowHeightPt
        val h = rowHeights[index]
        return if (h.isNaN()) defaultRowHeightPt else h
    }

    fun colWidthChars(index: Int): Float {
        if (index !in colWidths.indices) return defaultColWidthChars
        val w = colWidths[index]
        return if (w.isNaN()) defaultColWidthChars else w
    }

    val isEmpty: Boolean get() = rowCount == 0 || colCount == 0
}

class XlsxException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Dosya boş (0 bayt). */
        EMPTY,
        /** ZIP değil; tanınmayan biçim. */
        NOT_XLSX,
        /** OLE2 kapsayıcı: eski .xls veya şifreli .xlsx. */
        LEGACY_OR_ENCRYPTED,
        /** ZIP bozuk veya zorunlu parçalar eksik. */
        CORRUPT,
        /** Sayfa bulunamadı. */
        SHEET_NOT_FOUND,
    }
}

class ParseCancelledException : RuntimeException("cancelled")
