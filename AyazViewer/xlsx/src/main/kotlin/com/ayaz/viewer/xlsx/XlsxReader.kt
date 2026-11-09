package com.ayaz.viewer.xlsx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Bağımlılıksız, akış tabanlı XLSX okuyucu.
 *
 * Yalnızca okuma yapar; dosya hiçbir şekilde değiştirilmez. ZIP içeriği rastgele erişim için
 * [ZipFile] ile açılır, XML parçaları [XmlPullParser] ile akış halinde okunur (DOM yok).
 *
 * @param parserFactory Android'de `android.util.Xml.newPullParser()`, JVM testlerinde kxml2.
 */
class XlsxReader(
    file: File,
    private val parserFactory: () -> XmlPullParser,
    private val maxCells: Int = DEFAULT_MAX_CELLS,
) : Closeable {

    private val zip: ZipFile
    private val lowerCaseIndex: Map<String, ZipEntry> by lazy {
        val map = HashMap<String, ZipEntry>()
        val en = zip.entries()
        while (en.hasMoreElements()) {
            val e = en.nextElement()
            map[e.name.replace('\\', '/').trimStart('/').lowercase()] = e
        }
        map
    }

    init {
        if (!file.exists() || file.length() == 0L) throw XlsxException(XlsxException.Reason.EMPTY)
        val header = ByteArray(8)
        val read = FileInputStream(file).use { readUpTo(it, header) }
        val isZip = read >= 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() &&
            header[2] == 3.toByte() && header[3] == 4.toByte()
        if (!isZip) {
            val ole = read >= 8 && header.contentEquals(OLE_MAGIC)
            throw XlsxException(
                when {
                    ole -> XlsxException.Reason.LEGACY_OR_ENCRYPTED
                    read >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() -> XlsxException.Reason.CORRUPT
                    else -> XlsxException.Reason.NOT_XLSX
                },
            )
        }
        zip = try {
            ZipFile(file)
        } catch (e: ZipException) {
            throw XlsxException(XlsxException.Reason.CORRUPT, e)
        } catch (e: IOException) {
            throw XlsxException(XlsxException.Reason.CORRUPT, e)
        }
    }

    override fun close() {
        try { zip.close() } catch (_: IOException) {}
    }

    // ------------------------------------------------------------------ çalışma kitabı

    fun readWorkbook(): WorkbookData {
        try {
            val workbookPath = officeDocumentPath()
            val workbookEntry = findEntry(workbookPath) ?: throw XlsxException(XlsxException.Reason.CORRUPT)
            val rels = readRels(relsPathFor(workbookPath), dirOf(workbookPath))

            var date1904 = false
            val rawSheets = ArrayList<Triple<String, String?, String?>>() // name, rId, state
            parse(workbookEntry) { p ->
                var event = p.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (Xml.name(p)) {
                            "workbookPr" -> date1904 = Xml.boolAttr(p, "date1904", false)
                            "sheet" -> {
                                val name = Xml.attr(p, "name") ?: "Sayfa ${rawSheets.size + 1}"
                                rawSheets.add(Triple(name, Xml.relAttr(p, "id"), Xml.attr(p, "state")))
                            }
                        }
                    }
                    event = p.next()
                }
            }

            val sheets = ArrayList<SheetEntry>()
            for ((name, rid, state) in rawSheets) {
                val rel = rid?.let { rels[it] }
                val path = rel?.target
                val isWorksheet = when {
                    rel == null -> false
                    rel.type.endsWith("/worksheet") -> true
                    rel.type.isEmpty() -> path?.contains("worksheets/") == true
                    else -> false
                }
                sheets.add(SheetEntry(name, path, state == "hidden" || state == "veryHidden", isWorksheet && path != null))
            }
            if (sheets.none { it.isWorksheet }) {
                // Bozuk workbook.xml: çalışma sayfalarını doğrudan ZIP içinden bul.
                val found = lowerCaseIndex.keys
                    .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") && !it.contains("_rels") }
                    .sortedWith(compareBy({ it.length }, { it }))
                if (found.isNotEmpty()) {
                    val names = sheets.map { it.name }
                    sheets.clear()
                    found.forEachIndexed { i, path ->
                        sheets.add(SheetEntry(names.getOrNull(i) ?: "Sayfa ${i + 1}", path, false, true))
                    }
                }
            }

            val theme = rels.values.firstOrNull { it.type.endsWith("/theme") }?.target
                ?.let { path -> findEntry(path) }
                ?.let { e -> runCatching { parse(e) { ThemeParser.parse(it) } }.getOrNull() }
                ?: Colors.DEFAULT_THEME

            val stylesEntry = (rels.values.firstOrNull { it.type.endsWith("/styles") }?.target ?: "xl/styles.xml")
                .let { findEntry(it) }
            val styles = stylesEntry?.let { e ->
                try { parse(e) { StylesParser.parse(it, theme) } } catch (ex: Exception) { null }
            } ?: listOf(CellStyle(formatCode = "General"))

            val sstEntry = (rels.values.firstOrNull { it.type.endsWith("/sharedStrings") }?.target ?: "xl/sharedStrings.xml")
                .let { findEntry(it) }
            val sst = sstEntry?.let { e -> parse(e) { readSharedStrings(it) } } ?: emptyArray()

            val visible = BooleanArray(styles.size) { styles[it].isVisuallyNonEmpty }
            return WorkbookData(sheets, sst, styles, date1904, visible)
        } catch (e: XlsxException) {
            throw e
        } catch (e: XmlPullParserException) {
            throw XlsxException(XlsxException.Reason.CORRUPT, e)
        } catch (e: IOException) {
            throw XlsxException(XlsxException.Reason.CORRUPT, e)
        } catch (e: RuntimeException) {
            throw XlsxException(XlsxException.Reason.CORRUPT, e)
        }
    }

    private fun readSharedStrings(p: XmlPullParser): Array<String> {
        var list: ArrayList<String>? = null
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (Xml.name(p)) {
                    "sst" -> {
                        val count = Xml.attr(p, "uniqueCount")?.toIntOrNull() ?: Xml.attr(p, "count")?.toIntOrNull() ?: 64
                        list = ArrayList(count.coerceIn(16, 1_000_000))
                    }
                    "si" -> {
                        val l = list ?: ArrayList<String>(64).also { list = it }
                        l.add(Xml.readRichText(p))
                    }
                }
            }
            event = p.next()
        }
        return list?.toTypedArray() ?: emptyArray()
    }

    // ------------------------------------------------------------------ çalışma sayfası

    /**
     * [index] numaralı sayfayı okur. [isCancelled] düzenli aralıklarla çağrılır; true dönerse
     * [ParseCancelledException] fırlatılır.
     */
    fun readSheet(workbook: WorkbookData, index: Int, isCancelled: () -> Boolean = { false }): SheetData {
        val entry = workbook.sheets.getOrNull(index) ?: throw XlsxException(XlsxException.Reason.SHEET_NOT_FOUND)
        val path = entry.path ?: throw XlsxException(XlsxException.Reason.SHEET_NOT_FOUND)
        val zipEntry = findEntry(path) ?: throw XlsxException(XlsxException.Reason.SHEET_NOT_FOUND)
        return try {
            parse(zipEntry) { p -> SheetParser(workbook, entry.name, maxCells, isCancelled).parse(p) }
        } catch (e: ParseCancelledException) {
            throw e
        } catch (e: XmlPullParserException) {
            throw XlsxException(XlsxException.Reason.CORRUPT, e)
        } catch (e: IOException) {
            throw XlsxException(XlsxException.Reason.CORRUPT, e)
        }
    }

    // ------------------------------------------------------------------ düzenleyici için paket erişimi

    internal fun workbookPartPath(): String = officeDocumentPath()

    internal fun entryFor(path: String): ZipEntry? = findEntry(path)

    internal fun allEntries(): List<ZipEntry> {
        val list = ArrayList<ZipEntry>()
        val en = zip.entries()
        while (en.hasMoreElements()) list.add(en.nextElement())
        return list
    }

    internal fun openEntry(e: ZipEntry): InputStream = zip.getInputStream(e)

    internal fun readEntryText(e: ZipEntry): String =
        zip.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8).removePrefix("\uFEFF") }

    internal fun newParser(): XmlPullParser = parserFactory().also {
        try { it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true) } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------ yardımcılar

    private class Rel(val target: String, val type: String)

    private fun officeDocumentPath(): String {
        val rootRels = findEntry("_rels/.rels") ?: return "xl/workbook.xml"
        val rels = try { readRelsEntry(rootRels, "") } catch (_: Exception) { emptyMap() }
        return rels.values.firstOrNull { it.type.endsWith("/officeDocument") }?.target ?: "xl/workbook.xml"
    }

    private fun readRels(path: String, baseDir: String): Map<String, Rel> {
        val entry = findEntry(path) ?: return emptyMap()
        return readRelsEntry(entry, baseDir)
    }

    private fun readRelsEntry(entry: ZipEntry, baseDir: String): Map<String, Rel> {
        val map = HashMap<String, Rel>()
        parse(entry) { p ->
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && Xml.name(p) == "Relationship") {
                    val id = Xml.attr(p, "Id")
                    val target = Xml.attr(p, "Target")
                    val mode = Xml.attr(p, "TargetMode")
                    if (id != null && target != null && mode != "External") {
                        map[id] = Rel(resolvePath(baseDir, target), Xml.attr(p, "Type") ?: "")
                    }
                }
                event = p.next()
            }
        }
        return map
    }

    private fun findEntry(path: String): ZipEntry? {
        val clean = path.replace('\\', '/').trimStart('/')
        return zip.getEntry(clean) ?: lowerCaseIndex[clean.lowercase()]
    }

    private inline fun <T> parse(entry: ZipEntry, block: (XmlPullParser) -> T): T {
        val stream: InputStream = BufferedInputStream(zip.getInputStream(entry), 64 * 1024)
        return stream.use { input ->
            val p = parserFactory()
            try { p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true) } catch (_: Exception) {}
            p.setInput(input, null)
            block(p)
        }
    }

    companion object {
        /** Bellek güvenliği için bir sayfada okunacak en fazla dolu hücre sayısı. */
        const val DEFAULT_MAX_CELLS = 3_000_000

        private val OLE_MAGIC = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )

        private fun readUpTo(input: InputStream, buf: ByteArray): Int {
            var total = 0
            while (total < buf.size) {
                val n = input.read(buf, total, buf.size - total)
                if (n < 0) break
                total += n
            }
            return total
        }

        internal fun dirOf(path: String): String {
            val idx = path.lastIndexOf('/')
            return if (idx >= 0) path.substring(0, idx + 1) else ""
        }

        internal fun relsPathFor(path: String): String {
            val dir = dirOf(path)
            return dir + "_rels/" + path.substring(dir.length) + ".rels"
        }

        internal fun resolvePath(baseDir: String, target: String): String {
            val t = target.replace('\\', '/')
            val combined = if (t.startsWith("/")) t.substring(1) else baseDir + t
            val parts = ArrayList<String>()
            for (seg in combined.split('/')) {
                when (seg) {
                    "", "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                    else -> parts.add(seg)
                }
            }
            return parts.joinToString("/")
        }
    }
}

/** Tek bir worksheet XML akışını ayrıştırır. */
internal class SheetParser(
    private val workbook: WorkbookData,
    private val name: String,
    private val maxCells: Int,
    private val isCancelled: () -> Boolean,
) {
    private class RowBuilder {
        var cols = IntArray(32)
        var types = ByteArray(32)
        var styles = IntArray(32)
        var values = LongArray(32)
        var size = 0
        var sorted = true

        fun reset() { size = 0; sorted = true }

        fun add(col: Int, type: Byte, style: Int, value: Long) {
            if (size == cols.size) {
                val n = size * 2
                cols = cols.copyOf(n); types = types.copyOf(n); styles = styles.copyOf(n); values = values.copyOf(n)
            }
            if (size > 0 && cols[size - 1] >= col) sorted = false
            cols[size] = col; types[size] = type; styles[size] = style; values[size] = value
            size++
        }

        fun build(): RowData {
            if (!sorted) {
                val order = (0 until size).sortedBy { cols[it] }
                val c = IntArray(size); val t = ByteArray(size); val s = IntArray(size); val v = LongArray(size)
                var n = 0
                for (i in order) {
                    if (n > 0 && c[n - 1] == cols[i]) n-- // aynı sütun tekrar ederse sonuncuyu tut
                    c[n] = cols[i]; t[n] = types[i]; s[n] = styles[i]; v[n] = values[i]
                    n++
                }
                return RowData(c.copyOf(n), t.copyOf(n), s.copyOf(n), v.copyOf(n), n)
            }
            return RowData(cols.copyOf(size), types.copyOf(size), styles.copyOf(size), values.copyOf(size), size)
        }
    }

    private val localStrings = ArrayList<String>()
    private val formulas = HashMap<Long, String>()
    private val sharedMasters = HashMap<String, Triple<Int, Int, String>>()
    private val sharedDependents = ArrayList<Triple<Int, Int, String>>()
    private val pendingFormulaText = HashSet<Long>()
    private val builtRows = ArrayList<RowData>()
    private val builtRowIndex = IntArrayList()
    private val heightRows = IntArrayList()
    private val heightValues = FloatArrayList()
    private class ColSpec(val min: Int, val max: Int, val width: Float?, val hidden: Boolean)
    private val colSpecs = ArrayList<ColSpec>()
    private val merges = ArrayList<CellRange>()
    private val builder = RowBuilder()

    private var maxValueRow = -1
    private var maxValueCol = -1
    private var maxStyledRow = -1
    private var maxStyledCol = -1
    private var cellCount = 0
    private var truncated = false

    fun parse(p: XmlPullParser): SheetData {
        var showGrid = true
        var sawView = false
        var inFirstView = false
        var frozenRows = 0
        var frozenCols = 0
        var defaultRowHeight = 15f
        var defaultColWidth: Float? = null
        var baseColWidth = 8f
        var tabColor: Int? = null

        var currentRow = -1
        var nextImplicitRow = 0
        var colCursor = 0
        var rowStyle = 0
        var rowOpen = false

        var event = p.eventType
        loop@ while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (Xml.name(p)) {
                    "c" -> {
                        if (!rowOpen) {
                            // <row> olmadan hücre: referanstan satır çıkar
                            val ref = Xml.attr(p, "r")?.let { CellRef.parse(it) } ?: -1L
                            val r = if (ref >= 0) CellRef.rowOf(ref) else CellRef.ROW_MISSING
                            currentRow = if (r != CellRef.ROW_MISSING) r else nextImplicitRow
                            nextImplicitRow = currentRow + 1
                            builder.reset(); rowOpen = true; colCursor = 0; rowStyle = 0
                        }
                        colCursor = readCell(p, currentRow, colCursor, rowStyle)
                        if (truncated) break@loop
                        event = p.next()
                        continue@loop
                    }
                    "row" -> {
                        if (rowOpen) finishRow(currentRow)
                        val r = Xml.attr(p, "r")?.toIntOrNull()?.minus(1)
                        currentRow = if (r != null && r >= 0 && r < CellRef.MAX_ROWS) r else nextImplicitRow
                        nextImplicitRow = currentRow + 1
                        colCursor = 0
                        rowStyle = if (Xml.boolAttr(p, "customFormat", false)) Xml.attr(p, "s")?.toIntOrNull() ?: 0 else 0
                        val hidden = Xml.boolAttr(p, "hidden", false)
                        val ht = Xml.attr(p, "ht")?.toFloatOrNull()
                        if (hidden) {
                            heightRows.add(currentRow); heightValues.add(0f)
                        } else if (ht != null && ht >= 0f && ht < 2000f) {
                            heightRows.add(currentRow); heightValues.add(ht)
                        }
                        builder.reset()
                        rowOpen = true
                    }
                    "sheetView" -> if (!sawView) {
                        sawView = true
                        inFirstView = true
                        showGrid = Xml.boolAttr(p, "showGridLines", true)
                    }
                    "pane" -> if (inFirstView) {
                        val state = Xml.attr(p, "state")
                        if (state == "frozen" || state == "frozenSplit") {
                            frozenCols = Xml.attr(p, "xSplit")?.toDoubleOrNull()?.toInt()?.coerceIn(0, 200) ?: 0
                            frozenRows = Xml.attr(p, "ySplit")?.toDoubleOrNull()?.toInt()?.coerceIn(0, 500) ?: 0
                        }
                    }
                    "tabColor" -> tabColor = Colors.resolve(Colors.read(p), Colors.DEFAULT_PALETTE, Colors.DEFAULT_THEME)
                    "sheetFormatPr" -> {
                        Xml.attr(p, "defaultRowHeight")?.toFloatOrNull()?.let { if (it > 0f && it < 500f) defaultRowHeight = it }
                        defaultColWidth = Xml.attr(p, "defaultColWidth")?.toFloatOrNull()?.takeIf { it > 0f && it < 256f }
                        Xml.attr(p, "baseColWidth")?.toFloatOrNull()?.let { if (it > 0f && it < 256f) baseColWidth = it }
                    }
                    "col" -> {
                        val min = Xml.attr(p, "min")?.toIntOrNull()
                        val max = Xml.attr(p, "max")?.toIntOrNull()
                        if (min != null && max != null && min >= 1 && max >= min) {
                            colSpecs.add(
                                ColSpec(
                                    min - 1, minOf(max, CellRef.MAX_COLS) - 1,
                                    Xml.attr(p, "width")?.toFloatOrNull()?.takeIf { it >= 0f && it < 256f },
                                    Xml.boolAttr(p, "hidden", false),
                                ),
                            )
                        }
                    }
                    "mergeCell" -> Xml.attr(p, "ref")?.let { CellRange.parse(it) }?.let {
                        if (!it.isSingleCell && merges.size < 100_000) merges.add(it)
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (Xml.name(p)) {
                    "row" -> if (rowOpen) { finishRow(currentRow); rowOpen = false }
                    "sheetView" -> inFirstView = false
                    "sheetData" -> if (rowOpen) { finishRow(currentRow); rowOpen = false }
                }
            }
            event = p.next()
        }
        if (rowOpen) finishRow(currentRow)

        // ---- sınırlar
        var rowCount = maxValueRow + 1
        var colCount = maxValueCol + 1
        for (m in merges) {
            if (m.lastRow < CellRef.MAX_ROWS && m.firstRow <= maxOf(maxValueRow, maxStyledRow) + 1) {
                rowCount = maxOf(rowCount, m.lastRow + 1)
            }
            if (m.lastCol < CellRef.MAX_COLS && m.firstCol <= maxOf(maxValueCol, maxStyledCol) + 1) {
                colCount = maxOf(colCount, m.lastCol + 1)
            }
        }
        if (maxStyledRow + 1 > rowCount) rowCount = minOf(maxStyledRow + 1, rowCount + 200)
        if (maxStyledCol + 1 > colCount) colCount = minOf(maxStyledCol + 1, colCount + 50)
        rowCount = rowCount.coerceIn(0, CellRef.MAX_ROWS)
        colCount = colCount.coerceIn(0, CellRef.MAX_COLS)

        val rows = arrayOfNulls<RowData>(rowCount)
        for (i in 0 until builtRows.size) {
            val r = builtRowIndex[i]
            if (r in 0 until rowCount) rows[r] = builtRows[i]
        }
        val heights = FloatArray(rowCount) { Float.NaN }
        for (i in 0 until heightRows.size) {
            val r = heightRows[i]
            if (r in 0 until rowCount) heights[r] = heightValues[i]
        }
        val widths = FloatArray(colCount) { Float.NaN }
        for (spec in colSpecs) {
            val end = minOf(spec.max, colCount - 1)
            for (c in spec.min..end) {
                widths[c] = if (spec.hidden) 0f else (spec.width ?: Float.NaN)
            }
        }
        // Paylaşılan formüllerin bağımlı hücrelerine formül metni türet.
        for ((r, c, si) in sharedDependents) {
            val m = sharedMasters[si] ?: continue
            formulas[(r.toLong() shl 32) or c.toLong()] = FormulaRefs.translate(m.third, r - m.first, c - m.second)
        }
        if (pendingFormulaText.isNotEmpty()) {
            for (i in 0 until builtRows.size) {
                val rowIdx = builtRowIndex[i]
                val rd = builtRows[i]
                for (k in 0 until rd.size) {
                    val key = (rowIdx.toLong() shl 32) or rd.cols[k].toLong()
                    if (key in pendingFormulaText) {
                        val f = formulas[key] ?: continue
                        rd.types[k] = CellType.LOCAL_STRING
                        rd.values[k] = addLocal("=$f")
                    }
                }
            }
        }

        val validMerges = merges.filter { it.firstRow < rowCount && it.firstCol < colCount }
            .map { CellRange(it.firstRow, it.firstCol, minOf(it.lastRow, rowCount - 1), minOf(it.lastCol, colCount - 1)) }
            .sortedWith(compareBy({ it.firstRow }, { it.firstCol }))

        return SheetData(
            name = name,
            rowCount = rowCount,
            colCount = colCount,
            rows = rows,
            localStrings = localStrings.toTypedArray(),
            rowHeights = heights,
            colWidths = widths,
            defaultRowHeightPt = defaultRowHeight,
            defaultColWidthChars = defaultColWidth ?: (baseColWidth + 0.71f),
            merges = validMerges,
            frozenRows = minOf(frozenRows, rowCount),
            frozenCols = minOf(frozenCols, colCount),
            showGridLines = showGrid,
            tabColor = tabColor,
            truncated = truncated,
            cellCount = cellCount,
            formulas = formulas,
        )
    }

    private fun finishRow(row: Int) {
        if (builder.size > 0 && row >= 0) {
            builtRows.add(builder.build())
            builtRowIndex.add(row)
        }
        builder.reset()
    }

    /** Geçerli <c> öğesini sonuna kadar okur, sıradaki sütun imlecini döndürür. */
    private fun readCell(p: XmlPullParser, row: Int, colCursor: Int, rowStyle: Int): Int {
        val ref = Xml.attr(p, "r")
        val t = Xml.attr(p, "t")
        val s = Xml.attr(p, "s")?.toIntOrNull() ?: rowStyle
        var col = colCursor
        if (ref != null) {
            val packed = CellRef.parse(ref)
            if (packed >= 0) {
                val c = CellRef.colOf(packed)
                if (c != CellRef.COL_MISSING) col = c
            }
        }
        var vText: String? = null
        var inline: String? = null
        var fText: String? = null
        var fShared = false
        var fSi: String? = null
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> when (Xml.name(p)) {
                    "v" -> vText = Xml.readText(p)
                    "is" -> inline = Xml.readRichText(p)
                    "f" -> {
                        fShared = Xml.attr(p, "t") == "shared"
                        fSi = Xml.attr(p, "si")
                        fText = Xml.readText(p)
                    }
                    else -> depth++
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }
        if (col < 0 || col >= CellRef.MAX_COLS || row < 0) return col + 1

        val key = (row.toLong() shl 32) or col.toLong()
        if (!fText.isNullOrEmpty()) {
            formulas[key] = fText!!
            if (fShared && fSi != null) sharedMasters[fSi!!] = Triple(row, col, fText!!)
        } else if (fShared && fSi != null) {
            sharedDependents.add(Triple(row, col, fSi!!))
        }
        val hasFormula = fText != null || fShared

        var type = CellType.EMPTY
        var value = 0L
        when (t) {
            "s" -> {
                val idx = vText?.trim()?.toIntOrNull()
                if (idx != null && idx >= 0 && idx < workbook.sharedStrings.size) {
                    if (workbook.sharedStrings[idx].isNotEmpty()) {
                        type = CellType.SHARED_STRING; value = idx.toLong()
                    }
                }
            }
            "inlineStr" -> {
                val text = inline ?: vText?.let { Xml.decodeEscapes(it) }
                if (!text.isNullOrEmpty()) { type = CellType.LOCAL_STRING; value = addLocal(text) }
            }
            "str" -> if (!vText.isNullOrEmpty()) { type = CellType.LOCAL_STRING; value = addLocal(Xml.decodeEscapes(vText!!)) }
            "b" -> if (!vText.isNullOrBlank()) {
                type = CellType.BOOLEAN
                val v = vText!!.trim()
                value = if (v == "1" || v.equals("true", ignoreCase = true)) 1L else 0L
            }
            "e" -> { type = CellType.ERROR; value = addLocal(vText?.trim()?.ifEmpty { null } ?: "#N/A") }
            "d" -> if (!vText.isNullOrBlank()) {
                val dt = ExcelDate.parseIso(vText!!)
                if (dt != null) {
                    type = CellType.DATE
                    value = java.lang.Double.doubleToRawLongBits(ExcelDate.fromDateTime(dt, workbook.date1904))
                } else {
                    type = CellType.LOCAL_STRING; value = addLocal(vText!!)
                }
            }
            else -> {
                val num = vText?.trim()?.toDoubleOrNull()
                if (num != null && !num.isNaN()) {
                    type = CellType.NUMBER; value = java.lang.Double.doubleToRawLongBits(num)
                } else if (!inline.isNullOrEmpty()) {
                    type = CellType.LOCAL_STRING; value = addLocal(inline!!)
                } else if (!vText.isNullOrEmpty()) {
                    type = CellType.LOCAL_STRING; value = addLocal(vText!!)
                }
            }
        }
        if (type == CellType.EMPTY && hasFormula && vText == null) {
            // Önbellek değeri yazılmamış formül: hesaplanmadığından formülün kendisi gösterilir.
            val text = fText?.takeIf { it.isNotEmpty() }
            if (text != null) { type = CellType.LOCAL_STRING; value = addLocal("=$text") }
            else pendingFormulaText.add(key)
        }

        val styleVisible = s > 0 && s < workbook.visibleStyle.size && workbook.visibleStyle[s]
        val pending = key in pendingFormulaText
        if (type == CellType.EMPTY && !styleVisible && !pending) return col + 1

        builder.add(col, type, if (s >= 0) s else 0, value)
        cellCount++
        if (type != CellType.EMPTY || pending) {
            if (row > maxValueRow) maxValueRow = row
            if (col > maxValueCol) maxValueCol = col
        } else {
            if (row > maxStyledRow) maxStyledRow = row
            if (col > maxStyledCol) maxStyledCol = col
        }
        if ((cellCount and 0xFFF) == 0 && isCancelled()) throw ParseCancelledException()
        if (cellCount >= maxCells) truncated = true
        return col + 1
    }

    private fun addLocal(text: String): Long {
        localStrings.add(text)
        return (localStrings.size - 1).toLong()
    }
}

internal class IntArrayList(capacity: Int = 64) {
    private var data = IntArray(capacity)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Int = data[i]
}

internal class FloatArrayList(capacity: Int = 64) {
    private var data = FloatArray(capacity)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int): Float = data[i]
}
