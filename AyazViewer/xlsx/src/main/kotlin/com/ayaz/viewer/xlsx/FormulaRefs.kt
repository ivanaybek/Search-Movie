package com.ayaz.viewer.xlsx

/**
 * Satır/sütun ekleme-silme sonrası indeks eşlemesi.
 * [delta] > 0 ise [at] konumuna [delta] adet ekleme, < 0 ise [at] konumundan itibaren silme.
 */
class AxisShift(val at: Int, val delta: Int) {
    private val count = if (delta < 0) -delta else delta

    /** Yeni indeks; silinen konum için -1. */
    fun map(i: Int): Int = when {
        delta > 0 -> if (i >= at) i + delta else i
        i < at -> i
        i < at + count -> -1
        else -> i - count
    }

    /** Kapsayıcı aralığın yeni hali; tamamen silindiyse null. */
    fun mapRange(start: Int, end: Int): IntRange? {
        if (delta > 0) return map(start)..map(end)
        val last = at + count - 1
        val s = when {
            start < at -> start
            start <= last -> at
            else -> start - count
        }
        val e = when {
            end < at -> end
            end <= last -> at - 1
            else -> end - count
        }
        return if (e < s) null else s..e
    }
}

/** Tek eksende (satır veya sütun) kaydırma. */
class SheetShift(val rows: AxisShift?, val cols: AxisShift?) {

    fun mapCell(row: Int, col: Int): Pair<Int, Int>? {
        val r = rows?.map(row) ?: row
        val c = cols?.map(col) ?: col
        return if (r < 0 || c < 0 || r >= CellRef.MAX_ROWS || c >= CellRef.MAX_COLS) null else r to c
    }

    fun mapRange(range: CellRange): CellRange? {
        val rr = if (rows != null) rows.mapRange(range.firstRow, range.lastRow) ?: return null else range.firstRow..range.lastRow
        val cc = if (cols != null) cols.mapRange(range.firstCol, range.lastCol) ?: return null else range.firstCol..range.lastCol
        if (rr.last >= CellRef.MAX_ROWS || cc.last >= CellRef.MAX_COLS) return null
        return CellRange(rr.first, cc.first, rr.last, cc.last)
    }

    /** "A1:C3" veya "B2" biçimli referansı kaydırır; silindiyse null. */
    fun mapRef(ref: String): String? {
        val range = CellRange.parse(ref) ?: return ref
        val mapped = mapRange(range) ?: return null
        return formatRange(mapped)
    }

    /** Boşlukla ayrılmış aralık listesi (sqref). Hepsi silindiyse null. */
    fun mapSqref(sqref: String): String? {
        val parts = sqref.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.mapNotNull { mapRef(it) }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    companion object {
        fun formatRange(r: CellRange): String =
            if (r.isSingleCell) CellRef.name(r.firstRow, r.firstCol)
            else CellRef.name(r.firstRow, r.firstCol) + ":" + CellRef.name(r.lastRow, r.lastCol)
    }
}

/**
 * Excel formül metinlerindeki A1 başvurularını dönüştürür. Metin sabitleri ("..."), başka sayfalara
 * yapılan başvurular (Sayfa2!A1) ve tablo başvuruları (Tablo1[Sütun]) korunur.
 */
object FormulaRefs {

    /** Satır/sütun ekleme-silmeye göre başvuruları kaydırır (mutlak başvurular dahil, Excel gibi). */
    fun shift(formula: String, shift: SheetShift): String = transform(formula) { ref -> ref.shifted(shift) }

    /**
     * Koşullu biçim / veri doğrulama formülleri: göreli başvurular uygulandığı aralığın sol üst
     * hücresine göredir. Mutlak başvurular kaydırılır, göreli başvurular sol üst hücrenin hareketi
     * ([dRow], [dCol]) kadar taşınır (Excel ile aynı davranış).
     */
    fun shiftAnchored(formula: String, shift: SheetShift, dRow: Int, dCol: Int): String =
        transform(formula) { ref -> ref.anchored(shift, dRow, dCol) }

    /** Paylaşılan formülün başka bir hücreye taşınmış hali: yalnızca göreli kısımlar kayar. */
    fun translate(formula: String, dRow: Int, dCol: Int): String {
        if (dRow == 0 && dCol == 0) return formula
        return transform(formula) { ref -> ref.translated(dRow, dCol) }
    }

    // ------------------------------------------------------------------ belirteçler

    private class Part(val col: Int?, val colAbs: Boolean, val row: Int?, val rowAbs: Boolean) {
        fun render(): String = buildString {
            if (col != null) { if (colAbs) append('$'); append(CellRef.columnName(col)) }
            if (row != null) { if (rowAbs) append('$'); append(row + 1) }
        }
    }

    private class Ref(val a: Part, val b: Part?) {
        val isRange get() = b != null

        fun render(): String = if (b == null) a.render() else a.render() + ":" + b.render()

        fun shifted(s: SheetShift): String {
            if (b == null) {
                val r = a.row?.let { s.rows?.map(it) ?: it }
                val c = a.col?.let { s.cols?.map(it) ?: it }
                if ((r != null && r < 0) || (c != null && c < 0)) return "#REF!"
                if ((r ?: 0) >= CellRef.MAX_ROWS || (c ?: 0) >= CellRef.MAX_COLS) return "#REF!"
                return Part(c, a.colAbs, r, a.rowAbs).render()
            }
            var r1 = a.row; var r2 = b.row; var c1 = a.col; var c2 = b.col
            if (r1 != null && r2 != null && s.rows != null) {
                val m = s.rows.mapRange(minOf(r1, r2), maxOf(r1, r2)) ?: return "#REF!"
                r1 = m.first; r2 = m.last
            }
            if (c1 != null && c2 != null && s.cols != null) {
                val m = s.cols.mapRange(minOf(c1, c2), maxOf(c1, c2)) ?: return "#REF!"
                c1 = m.first; c2 = m.last
            }
            if ((r2 ?: 0) >= CellRef.MAX_ROWS || (c2 ?: 0) >= CellRef.MAX_COLS) return "#REF!"
            return Ref(Part(c1, a.colAbs, r1, a.rowAbs), Part(c2, b.colAbs, r2, b.rowAbs)).render()
        }

        fun anchored(s: SheetShift, dRow: Int, dCol: Int): String {
            fun move(p: Part): Part? {
                val r = p.row?.let { if (p.rowAbs) (s.rows?.map(it) ?: it) else it + dRow }
                val c = p.col?.let { if (p.colAbs) (s.cols?.map(it) ?: it) else it + dCol }
                if ((r != null && (r < 0 || r >= CellRef.MAX_ROWS)) || (c != null && (c < 0 || c >= CellRef.MAX_COLS))) return null
                return Part(c, p.colAbs, r, p.rowAbs)
            }
            val na = move(a) ?: return "#REF!"
            if (b == null) return na.render()
            val nb = move(b) ?: return "#REF!"
            return Ref(na, nb).render()
        }

        fun translated(dRow: Int, dCol: Int): String {
            fun move(p: Part): Part? {
                val r = p.row?.let { if (p.rowAbs) it else it + dRow }
                val c = p.col?.let { if (p.colAbs) it else it + dCol }
                if ((r != null && (r < 0 || r >= CellRef.MAX_ROWS)) || (c != null && (c < 0 || c >= CellRef.MAX_COLS))) return null
                return Part(c, p.colAbs, r, p.rowAbs)
            }
            val na = move(a) ?: return "#REF!"
            if (b == null) return na.render()
            val nb = move(b) ?: return "#REF!"
            return Ref(na, nb).render()
        }
    }

    private fun isIdentChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '.' || c == '\\'

    /** [i] konumunda bir hücre/sütun/satır parçası okur: $A$1, A, $3 ... */
    private fun readPart(s: String, start: Int): Pair<Part, Int>? {
        var i = start
        var colAbs = false
        var rowAbs = false
        if (i < s.length && s[i] == '$') { colAbs = true; i++ }
        val lettersStart = i
        while (i < s.length && s[i].uppercaseChar() in 'A'..'Z' && i - lettersStart < 3) i++
        val letters = s.substring(lettersStart, i)
        var j = i
        if (j < s.length && s[j] == '$') { rowAbs = true; j++ }
        val digitsStart = j
        while (j < s.length && s[j].isDigit()) j++
        val digits = s.substring(digitsStart, j)
        if (letters.isEmpty() && digits.isEmpty()) return null
        if (letters.isEmpty()) {
            // yalnızca satır ($3): "$" sütun işareti aslında satıra aitti
            val abs = colAbs || rowAbs
            val row = digits.toIntOrNull()?.minus(1) ?: return null
            if (row < 0) return null
            return Part(null, false, row, abs) to j
        }
        val col = CellRef.columnIndex(letters)
        if (col < 0) return null
        if (digits.isEmpty()) {
            if (rowAbs) return null // "A$" geçersiz
            return Part(col, colAbs, null, false) to i
        }
        val row = digits.toIntOrNull()?.minus(1) ?: return null
        if (row < 0 || row >= CellRef.MAX_ROWS) return null
        return Part(col, colAbs, row, rowAbs) to j
    }

    private fun transform(formula: String, mapRef: (Ref) -> String): String {
        val s = formula
        val out = StringBuilder(s.length + 8)
        var i = 0
        var foreign = false // bir önceki belirteç başka sayfayı niteliyorsa
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> {
                    var j = i + 1
                    while (j < s.length) {
                        if (s[j] == '"') { if (j + 1 < s.length && s[j + 1] == '"') j += 2 else break } else j++
                    }
                    val end = minOf(j + 1, s.length)
                    out.append(s, i, end); i = end; foreign = false
                }
                c == '\'' -> {
                    // 'Sayfa Adı'!A1
                    var j = i + 1
                    while (j < s.length) {
                        if (s[j] == '\'') { if (j + 1 < s.length && s[j + 1] == '\'') j += 2 else break } else j++
                    }
                    val end = minOf(j + 1, s.length)
                    out.append(s, i, end); i = end
                    if (i < s.length && s[i] == '!') { out.append('!'); i++; foreign = true }
                }
                c == '[' -> {
                    var depth = 0
                    var j = i
                    while (j < s.length) {
                        if (s[j] == '[') depth++ else if (s[j] == ']') { depth--; if (depth == 0) break }
                        j++
                    }
                    val end = minOf(j + 1, s.length)
                    out.append(s, i, end); i = end; foreign = false
                }
                c == '$' || c.isLetter() || c.isDigit() -> {
                    val prevIdent = i > 0 && isIdentChar(s[i - 1])
                    val first = if (!prevIdent) readPart(s, i) else null
                    if (first == null) {
                        // sıradan tanımlayıcı veya sayı: sonuna kadar kopyala
                        var j = i
                        while (j < s.length && (isIdentChar(s[j]) || s[j] == '$')) j++
                        if (j == i) j = i + 1
                        val token = s.substring(i, j)
                        out.append(token); i = j
                        if (i < s.length && s[i] == '!') { out.append('!'); i++; foreign = true } else foreign = false
                        continue
                    }
                    var (pa, j) = first
                    var pb: Part? = null
                    if (j < s.length && s[j] == ':') {
                        val second = readPart(s, j + 1)
                        if (second != null) { pb = second.first; j = second.second }
                    }
                    val next = if (j < s.length) s[j] else ' '
                    val looksLikeName = next == '(' || isIdentChar(next) || next == '!'
                    val validShape = if (pb == null) pa.col != null && pa.row != null
                    else (pa.col != null) == (pb.col != null) && (pa.row != null) == (pb.row != null)
                    if (looksLikeName || !validShape) {
                        var k = i
                        while (k < s.length && (isIdentChar(s[k]) || s[k] == '$')) k++
                        if (k == i) k = i + 1
                        out.append(s, i, k); i = k
                        if (i < s.length && s[i] == '!') { out.append('!'); i++; foreign = true } else foreign = false
                        continue
                    }
                    val ref = Ref(pa, pb)
                    out.append(if (foreign) ref.render() else mapRef(ref))
                    i = j
                    foreign = false
                }
                else -> { out.append(c); i++; foreign = false }
            }
        }
        return out.toString()
    }
}
