package com.ayaz.viewer.xlsx

/** Excel hücre referansı yardımcıları (A1 notasyonu). Tüm indeksler 0 tabanlıdır. */
object CellRef {

    const val MAX_ROWS = 1_048_576
    const val MAX_COLS = 16_384

    /** 0 → "A", 25 → "Z", 26 → "AA" */
    fun columnName(index: Int): String {
        require(index >= 0) { "negative column" }
        var n = index + 1
        val sb = StringBuilder(4)
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun name(row: Int, col: Int): String = columnName(col) + (row + 1)

    /** "AB" → 27. Geçersizse -1. */
    fun columnIndex(letters: String): Int {
        if (letters.isEmpty()) return -1
        var result = 0
        for (ch in letters) {
            val c = ch.uppercaseChar()
            if (c !in 'A'..'Z') return -1
            result = result * 26 + (c - 'A' + 1)
            if (result > MAX_COLS) return -1
        }
        return result - 1
    }

    /**
     * "B12" / "$B$12" referansını çözer. Sonuç: (satır shl 32) or sütun, geçersizse -1.
     * Yalnızca sütun ("B") verildiyse satır -1 değil, [ROW_MISSING] döner.
     */
    fun parse(ref: CharSequence): Long {
        var i = 0
        val len = ref.length
        var col = 0
        var letters = 0
        while (i < len) {
            val ch = ref[i]
            if (ch == '$') { i++; continue }
            val c = ch.uppercaseChar()
            if (c in 'A'..'Z') {
                col = col * 26 + (c - 'A' + 1)
                letters++
                if (col > MAX_COLS) return -1
                i++
            } else break
        }
        var row = 0
        var digits = 0
        while (i < len) {
            val ch = ref[i]
            if (ch == '$') { i++; continue }
            if (ch in '0'..'9') {
                row = row * 10 + (ch - '0')
                digits++
                if (row > MAX_ROWS) return -1
                i++
            } else return -1
        }
        if (letters == 0 && digits == 0) return -1
        val c = if (letters == 0) COL_MISSING else col - 1
        val r = if (digits == 0) ROW_MISSING else row - 1
        if (r < 0 && r != ROW_MISSING) return -1
        return (r.toLong() shl 32) or (c.toLong() and 0xFFFFFFFFL)
    }

    const val ROW_MISSING = Int.MAX_VALUE
    const val COL_MISSING = Int.MAX_VALUE

    fun rowOf(packed: Long): Int = (packed shr 32).toInt()
    fun colOf(packed: Long): Int = packed.toInt()
}

/** Kapsayıcı (inclusive) hücre aralığı. */
data class CellRange(val firstRow: Int, val firstCol: Int, val lastRow: Int, val lastCol: Int) {

    fun contains(row: Int, col: Int): Boolean =
        row in firstRow..lastRow && col in firstCol..lastCol

    val isSingleCell: Boolean get() = firstRow == lastRow && firstCol == lastCol

    companion object {
        /** "A1:C3" veya "B2" çözümler; geçersizse null. */
        fun parse(ref: String): CellRange? {
            val trimmed = ref.trim()
            if (trimmed.isEmpty()) return null
            val colon = trimmed.indexOf(':')
            val a = CellRef.parse(if (colon >= 0) trimmed.substring(0, colon) else trimmed)
            if (a < 0) return null
            val b = if (colon >= 0) CellRef.parse(trimmed.substring(colon + 1)) else a
            if (b < 0) return null
            val r1 = CellRef.rowOf(a)
            val c1 = CellRef.colOf(a)
            val r2 = CellRef.rowOf(b)
            val c2 = CellRef.colOf(b)
            if (r1 == CellRef.ROW_MISSING || r2 == CellRef.ROW_MISSING) return null
            if (c1 == CellRef.COL_MISSING || c2 == CellRef.COL_MISSING) return null
            return CellRange(minOf(r1, r2), minOf(c1, c2), maxOf(r1, r2), maxOf(c1, c2))
        }
    }
}
