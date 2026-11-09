package com.ayaz.viewer.xlsx

import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Bir hücreye yazılacak yeni değer. */
sealed interface CellInput {
    data object Empty : CellInput
    data class Text(val text: String) : CellInput
    data class Number(val value: Double) : CellInput
    data class Bool(val value: Boolean) : CellInput
    /** "=" işareti olmadan, Excel (İngilizce) sözdiziminde formül. */
    data class Formula(val formula: String) : CellInput
}

/** Bir çalışma sayfasına uygulanacak tek düzenleme. Satır/sütun indeksleri 0 tabanlıdır. */
sealed interface SheetEdit {
    data class SetCell(val row: Int, val col: Int, val input: CellInput) : SheetEdit
    data class InsertRows(val at: Int, val count: Int = 1) : SheetEdit
    data class DeleteRows(val at: Int, val count: Int = 1) : SheetEdit
    data class InsertCols(val at: Int, val count: Int = 1) : SheetEdit
    data class DeleteCols(val at: Int, val count: Int = 1) : SheetEdit
}

class XlsxEditException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason {
        /** Sayfada Excel tablosu var; satır/sütun ekleme-silme tablo tanımlarını bozar. */
        HAS_TABLES,
        /** Sayfa düzenleme için çok büyük. */
        TOO_LARGE,
        SHEET_NOT_FOUND,
        CORRUPT,
    }
}

/**
 * XLSX dosyasını düzenler. Kaynak dosya hiçbir zaman değiştirilmez; sonuç [target] dosyasına yazılır.
 *
 * Yalnızca düzenlenen sayfanın XML'i yeniden yazılır; biçimler, diğer sayfalar, resimler ve
 * diğer tüm parçalar olduğu gibi kopyalanır. Hücre değerleri satır içi dize/sayı olarak yazılır;
 * formüller Excel'de açılışta yeniden hesaplansın diye `fullCalcOnLoad` işaretlenir ve
 * `calcChain.xml` (artık geçersiz olabilecek hesap sırası) kaldırılır.
 */
object XlsxEditor {

    /** Düzenlenebilir en büyük sayfa XML boyutu (sıkıştırılmamış). */
    const val MAX_SHEET_XML_BYTES = 80L * 1024 * 1024

    @Throws(XlsxEditException::class, XlsxException::class, IOException::class)
    fun apply(source: File, target: File, sheetIndex: Int, edit: SheetEdit, parserFactory: () -> XmlPullParser) {
        XlsxReader(source, parserFactory).use { reader ->
            val wb = reader.readWorkbook()
            val sheet = wb.sheets.getOrNull(sheetIndex)
            val sheetPath = sheet?.path
            if (sheet == null || !sheet.isWorksheet || sheetPath == null) throw XlsxEditException(XlsxEditException.Reason.SHEET_NOT_FOUND)
            val sheetEntry = reader.entryFor(sheetPath) ?: throw XlsxEditException(XlsxEditException.Reason.SHEET_NOT_FOUND)
            if (sheetEntry.size > MAX_SHEET_XML_BYTES) throw XlsxEditException(XlsxEditException.Reason.TOO_LARGE)

            val sheetXml = try { reader.readEntryText(sheetEntry) } catch (e: OutOfMemoryError) {
                throw XlsxEditException(XlsxEditException.Reason.TOO_LARGE, e)
            }
            val masters = collectSharedMasters(sheetXml, reader.newParser())
            val newSheet = try {
                SheetRewriter(sheetXml, edit, masters).run()
            } catch (e: XlsxEditException) {
                throw e
            } catch (e: RuntimeException) {
                throw XlsxEditException(XlsxEditException.Reason.CORRUPT, e)
            }

            // Çalışma kitabı: açılışta yeniden hesaplama; calcChain kaldırma.
            val workbookPath = reader.workbookPartPath()
            val workbookEntry = reader.entryFor(workbookPath) ?: throw XlsxEditException(XlsxEditException.Reason.CORRUPT)
            val relsEntry = reader.entryFor(XlsxReader.relsPathFor(workbookPath))
            val ctEntry = reader.entryFor("[Content_Types].xml")
            var calcChainName: String? = null
            var newRels: String? = null
            if (relsEntry != null) {
                val rels = reader.readEntryText(relsEntry)
                val m = Regex("<(?:\\w+:)?Relationship\\b[^>]*Type=\"[^\"]*/calcChain\"[^>]*/>").find(rels)
                if (m != null) {
                    val target0 = Regex("Target=\"([^\"]*)\"").find(m.value)?.groupValues?.get(1)
                    if (target0 != null) {
                        calcChainName = reader.entryFor(XlsxReader.resolvePath(XlsxReader.dirOf(workbookPath), target0))?.name
                    }
                    newRels = rels.removeRange(m.range)
                }
            }
            if (calcChainName == null) calcChainName = reader.entryFor("xl/calcChain.xml")?.name
            val newCt = ctEntry?.let { e ->
                val ct = reader.readEntryText(e)
                if (calcChainName != null) ct.replace(Regex("<(?:\\w+:)?Override\\b[^>]*PartName=\"/?${Regex.escape(calcChainName!!)}\"[^>]*/>"), "") else ct
            }
            val newWorkbook = patchWorkbook(reader.readEntryText(workbookEntry))

            val replaced = HashMap<String, String>()
            replaced[sheetEntry.name] = newSheet
            replaced[workbookEntry.name] = newWorkbook
            if (relsEntry != null && newRels != null) replaced[relsEntry.name] = newRels
            if (ctEntry != null && newCt != null) replaced[ctEntry.name] = newCt

            val tmp = File(target.parentFile, target.name + ".tmp")
            try {
                ZipOutputStream(tmp.outputStream().buffered(64 * 1024)).use { zos ->
                    for (e in reader.allEntries()) {
                        if (e.name == calcChainName) continue
                        val ne = ZipEntry(e.name)
                        zos.putNextEntry(ne)
                        val text = replaced[e.name]
                        if (text != null) {
                            zos.write(text.toByteArray(Charsets.UTF_8))
                        } else if (!e.isDirectory) {
                            reader.openEntry(e).use { it.copyTo(zos, 64 * 1024) }
                        }
                        zos.closeEntry()
                    }
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    private fun collectSharedMasters(xml: String, p: XmlPullParser): Map<String, Triple<Int, Int, String>> {
        if (!xml.contains("shared")) return emptyMap()
        val result = HashMap<String, Triple<Int, Int, String>>()
        p.setInput(StringReader(xml))
        var row = -1
        var nextRow = 0
        var col = 0
        var nextCol = 0
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (Xml.name(p)) {
                    "row" -> {
                        row = Xml.attr(p, "r")?.toIntOrNull()?.minus(1) ?: nextRow
                        nextRow = row + 1
                        nextCol = 0
                    }
                    "c" -> {
                        val ref = Xml.attr(p, "r")?.let { CellRef.parse(it) } ?: -1L
                        val c = if (ref >= 0) CellRef.colOf(ref) else CellRef.COL_MISSING
                        col = if (c != CellRef.COL_MISSING) c else nextCol
                        nextCol = col + 1
                    }
                    "f" -> if (Xml.attr(p, "t") == "shared") {
                        val si = Xml.attr(p, "si")
                        val text = Xml.readText(p)
                        if (si != null && text.isNotEmpty() && row >= 0) result[si] = Triple(row, col, text)
                    }
                }
            }
            event = p.next()
        }
        return result
    }

    internal fun patchWorkbook(xml: String): String {
        val calc = Regex("<((?:\\w+:)?)calcPr\\b([^>]*?)(/?)>").find(xml)
        if (calc != null) {
            val attrs = calc.groupValues[2].replace(Regex("\\s+fullCalcOnLoad=\"[^\"]*\""), "")
            val rep = "<${calc.groupValues[1]}calcPr$attrs fullCalcOnLoad=\"1\"${calc.groupValues[3]}>"
            return xml.replaceRange(calc.range, rep)
        }
        val prefix = Regex("<((?:\\w+:)?)workbook\\b").find(xml)?.groupValues?.get(1) ?: ""
        var insertAt = -1
        for (name in listOf("sheets", "functionGroups", "externalReferences", "definedNames")) {
            val close = "</$prefix$name>"
            val idx = xml.indexOf(close)
            if (idx >= 0) insertAt = maxOf(insertAt, idx + close.length)
            if (idx < 0 && name == "sheets") {
                val self = Regex("<$prefix$name\\b[^>]*/>").find(xml)
                if (self != null) insertAt = maxOf(insertAt, self.range.last + 1)
            }
        }
        if (insertAt < 0) return xml
        return xml.substring(0, insertAt) + "<${prefix}calcPr fullCalcOnLoad=\"1\"/>" + xml.substring(insertAt)
    }
}

// ====================================================================== XML yardımcıları

/** Ayrıştırılmış bir başlangıç etiketi; bilinmeyen öznitelikler olduğu gibi korunur. */
internal class XTag private constructor(
    val qName: String,
    private val attrs: MutableList<Array<String>>,
    val selfClosing: Boolean,
    val isEnd: Boolean,
) {
    val local: String get() = qName.substringAfter(':')
    val prefix: String get() = if (qName.contains(':')) qName.substringBefore(':') + ":" else ""

    fun attr(local: String): String? =
        attrs.firstOrNull { it[0].substringAfter(':') == local && !it[0].startsWith("xmlns") }?.get(1)

    fun setAttr(local: String, value: String?) {
        val idx = attrs.indexOfFirst { it[0].substringAfter(':') == local && !it[0].startsWith("xmlns") }
        if (value == null) {
            if (idx >= 0) attrs.removeAt(idx)
        } else if (idx >= 0) {
            attrs[idx][1] = XmlEsc.attr(value)
        } else {
            attrs.add(arrayOf(local, XmlEsc.attr(value)))
        }
    }

    fun render(selfClose: Boolean = selfClosing): String = buildString {
        append('<').append(qName)
        for (a in attrs) append(' ').append(a[0]).append("=\"").append(a[1]).append('"')
        append(if (selfClose) "/>" else ">")
    }

    companion object {
        fun parse(raw: String): XTag {
            var i = 1
            val isEnd = raw.length > 1 && raw[1] == '/'
            if (isEnd) i = 2
            val n = raw.length
            val nameStart = i
            while (i < n && !raw[i].isWhitespace() && raw[i] != '/' && raw[i] != '>') i++
            val qName = raw.substring(nameStart, i)
            val attrs = ArrayList<Array<String>>()
            while (i < n) {
                while (i < n && raw[i].isWhitespace()) i++
                if (i >= n || raw[i] == '/' || raw[i] == '>') break
                val an = i
                while (i < n && raw[i] != '=' && !raw[i].isWhitespace() && raw[i] != '>' && raw[i] != '/') i++
                val name = raw.substring(an, i)
                while (i < n && raw[i].isWhitespace()) i++
                if (i < n && raw[i] == '=') {
                    i++
                    while (i < n && raw[i].isWhitespace()) i++
                    val q = raw[i]
                    i++
                    val vs = i
                    while (i < n && raw[i] != q) i++
                    var value = raw.substring(vs, minOf(i, n))
                    if (q == '\'') value = value.replace("\"", "&quot;")
                    attrs.add(arrayOf(name, value))
                    i++
                } else {
                    attrs.add(arrayOf(name, ""))
                }
            }
            val selfClosing = raw.endsWith("/>")
            return XTag(qName, attrs, selfClosing, isEnd)
        }
    }
}

internal object XmlEsc {
    fun text(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch.code < 0x20 || ch == '\uFFFE' || ch == '\uFFFF' -> sb.append("_x").append("%04X".format(ch.code)).append('_')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun attr(s: String): String = text(s).replace("\"", "&quot;").replace("\n", "&#10;").replace("\r", "&#13;").replace("\t", "&#9;")

    fun unescape(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&') {
                val semi = s.indexOf(';', i)
                if (semi > i) {
                    val ent = s.substring(i + 1, semi)
                    val rep: String? = when {
                        ent == "lt" -> "<"
                        ent == "gt" -> ">"
                        ent == "amp" -> "&"
                        ent == "quot" -> "\""
                        ent == "apos" -> "'"
                        ent.startsWith("#x") || ent.startsWith("#X") -> ent.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                        ent.startsWith("#") -> ent.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                        else -> null
                    }
                    if (rep != null) { sb.append(rep); i = semi + 1; continue }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** [lt] konumundaki '<' ile başlayan işaretlemenin son karakterinin ('>') indeksi. */
    fun tagEnd(s: String, lt: Int): Int {
        if (s.startsWith("<!--", lt)) return s.indexOf("-->", lt).let { if (it < 0) s.length - 1 else it + 2 }
        if (s.startsWith("<![CDATA[", lt)) return s.indexOf("]]>", lt).let { if (it < 0) s.length - 1 else it + 2 }
        if (s.startsWith("<?", lt)) return s.indexOf("?>", lt).let { if (it < 0) s.length - 1 else it + 1 }
        var i = lt + 1
        var quote = 0.toChar()
        while (i < s.length) {
            val c = s[i]
            if (quote != 0.toChar()) {
                if (c == quote) quote = 0.toChar()
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return i
            }
            i++
        }
        throw XlsxEditException(XlsxEditException.Reason.CORRUPT)
    }

    /** [from] konumundan itibaren [qName] öğesinin kapanış etiketinin başlangıç indeksi (iç içe aynı adlar sayılır). */
    fun findClose(s: String, from: Int, qName: String): Int {
        var depth = 1
        var i = from
        val open = "<$qName"
        val close = "</$qName>"
        while (i < s.length) {
            val lt = s.indexOf('<', i)
            if (lt < 0) break
            if (s.startsWith(close, lt)) {
                depth--
                if (depth == 0) return lt
                i = lt + close.length
                continue
            }
            val gt = tagEnd(s, lt)
            if (s.startsWith(open, lt)) {
                val after = s.getOrNull(lt + open.length)
                if (after != null && (after.isWhitespace() || after == '>' || after == '/') && s[gt - 1] != '/') depth++
            }
            i = gt + 1
        }
        throw XlsxEditException(XlsxEditException.Reason.CORRUPT)
    }

    /**
     * Bir öğe içeriğindeki üst düzey alt öğeleri dolaşır. [f] her alt öğe için yerine yazılacak metni
     * (silmek için null) döndürür. Öğeler arasındaki metin ve yorumlar korunur.
     */
    fun mapChildren(s: String, f: (XTag, String, String?) -> String?): String {
        val out = StringBuilder(s.length + 64)
        var i = 0
        while (i < s.length) {
            val lt = s.indexOf('<', i)
            if (lt < 0) { out.append(s, i, s.length); break }
            out.append(s, i, lt)
            val gt = tagEnd(s, lt)
            val raw = s.substring(lt, gt + 1)
            i = gt + 1
            if (raw.startsWith("<!") || raw.startsWith("<?")) { out.append(raw); continue }
            val tag = XTag.parse(raw)
            if (tag.isEnd) { out.append(raw); continue }
            var inner: String? = null
            var whole = raw
            if (!tag.selfClosing) {
                val end = findClose(s, i, tag.qName)
                inner = s.substring(i, end)
                val closeLen = tag.qName.length + 3
                whole = s.substring(lt, end + closeLen)
                i = end + closeLen
            }
            f(tag, whole, inner)?.let { out.append(it) }
        }
        return out.toString()
    }
}

// ====================================================================== sayfa yeniden yazıcı

internal class SheetRewriter(
    private val xml: String,
    private val edit: SheetEdit,
    private val masters: Map<String, Triple<Int, Int, String>>,
) {
    private val shift: SheetShift? = when (edit) {
        is SheetEdit.InsertRows -> SheetShift(AxisShift(edit.at, edit.count), null)
        is SheetEdit.DeleteRows -> SheetShift(AxisShift(edit.at, -edit.count), null)
        is SheetEdit.InsertCols -> SheetShift(null, AxisShift(edit.at, edit.count))
        is SheetEdit.DeleteCols -> SheetShift(null, AxisShift(edit.at, -edit.count))
        is SheetEdit.SetCell -> null
    }
    private val set = edit as? SheetEdit.SetCell
    private var setDone = false
    private var p = ""

    fun run(): String {
        if (shift != null && Regex("<(?:\\w+:)?tablePart\\b").containsMatchIn(xml)) {
            throw XlsxEditException(XlsxEditException.Reason.HAS_TABLES)
        }
        // Kök öğeyi bul ve alt öğelerini işle.
        var rootStart = -1
        var i = 0
        while (true) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) throw XlsxEditException(XlsxEditException.Reason.CORRUPT)
            val gt = XmlEsc.tagEnd(xml, lt)
            val raw = xml.substring(lt, gt + 1)
            if (!raw.startsWith("<?") && !raw.startsWith("<!")) { rootStart = lt; i = gt + 1; break }
            i = gt + 1
        }
        val rootTag = XTag.parse(xml.substring(rootStart, i))
        val rootClose = xml.lastIndexOf("</${rootTag.qName}>")
        if (rootClose < 0) throw XlsxEditException(XlsxEditException.Reason.CORRUPT)
        p = rootTag.prefix
        val body = XmlEsc.mapChildren(xml.substring(i, rootClose), ::topLevel)
        return xml.substring(0, i) + body + xml.substring(rootClose)
    }

    private fun topLevel(tag: XTag, whole: String, inner: String?): String? {
        val s = shift
        return when (tag.local) {
            "dimension" -> null // isteğe bağlı; Excel yeniden hesaplar
            "sheetData" -> {
                val content = if (inner == null) "" else processSheetData(inner)
                val tail = if (set != null && !setDone) newRow(set).also { setDone = true } else ""
                tag.render(false) + content + tail + "</${tag.qName}>"
            }
            "cols" -> if (s?.cols != null && inner != null) {
                val rebuilt = XmlEsc.mapChildren(inner) { t, w, _ ->
                    if (t.local != "col") return@mapChildren w
                    val min = t.attr("min")?.toIntOrNull()
                    val max = t.attr("max")?.toIntOrNull()
                    if (min == null || max == null) return@mapChildren w
                    val m = s.cols.mapRange(min - 1, max - 1) ?: return@mapChildren null
                    t.setAttr("min", (m.first + 1).toString())
                    t.setAttr("max", minOf(m.last + 1, CellRef.MAX_COLS).toString())
                    t.render(true)
                }
                if (rebuilt.contains("<")) tag.render(false) + rebuilt + "</${tag.qName}>" else null
            } else whole
            "mergeCells" -> if (s != null && inner != null) {
                var count = 0
                val rebuilt = XmlEsc.mapChildren(inner) { t, w, _ ->
                    if (t.local != "mergeCell") return@mapChildren w
                    val ref = t.attr("ref") ?: return@mapChildren null
                    val range = CellRange.parse(ref) ?: return@mapChildren null
                    val m = s.mapRange(range) ?: return@mapChildren null
                    if (m.isSingleCell) return@mapChildren null
                    t.setAttr("ref", SheetShift.formatRange(m))
                    count++
                    t.render(true)
                }
                if (count == 0) null else { tag.setAttr("count", count.toString()); tag.render(false) + rebuilt + "</${tag.qName}>" }
            } else whole
            "hyperlinks" -> if (s != null && inner != null) {
                var count = 0
                val rebuilt = XmlEsc.mapChildren(inner) { t, w, _ ->
                    if (t.local != "hyperlink") return@mapChildren w
                    val ref = t.attr("ref") ?: return@mapChildren w
                    val m = s.mapRef(ref) ?: return@mapChildren null
                    t.setAttr("ref", m)
                    count++
                    if (t.selfClosing) t.render(true) else w.replaceFirst(Regex("^<[^>]*>"), t.render(false))
                }
                if (count == 0) null else tag.render(false) + rebuilt + "</${tag.qName}>"
            } else whole
            "conditionalFormatting" -> if (s != null) {
                val sq = tag.attr("sqref")
                val m = if (sq != null) s.mapSqref(sq) ?: return null else null
                if (m != null) tag.setAttr("sqref", m)
                if (inner == null) tag.render(true)
                else tag.render(false) + shiftFormulas(inner, s, sq ?: "A1", m ?: "A1") + "</${tag.qName}>"
            } else whole
            "dataValidations" -> if (s != null && inner != null) {
                var count = 0
                val rebuilt = XmlEsc.mapChildren(inner) { t, w, inn ->
                    if (t.local != "dataValidation") return@mapChildren w
                    val sq = t.attr("sqref") ?: return@mapChildren w
                    val m = s.mapSqref(sq) ?: return@mapChildren null
                    t.setAttr("sqref", m)
                    count++
                    if (inn == null) t.render(true) else t.render(false) + shiftFormulas(inn, s, sq, m) + "</${t.qName}>"
                }
                if (count == 0) null else { tag.setAttr("count", count.toString()); tag.render(false) + rebuilt + "</${tag.qName}>" }
            } else whole
            "autoFilter" -> if (s != null) {
                val ref = tag.attr("ref")
                if (ref != null) {
                    val m = s.mapRef(ref) ?: return null
                    tag.setAttr("ref", m)
                }
                if (inner == null) tag.render(true) else tag.render(false) + inner + "</${tag.qName}>"
            } else whole
            else -> whole
        }
    }

    /** Koşullu biçim / doğrulama formüllerini, eski ve yeni sol üst hücreye göre dönüştürür. */
    private fun shiftFormulas(fragment: String, s: SheetShift, oldSqref: String, newSqref: String): String {
        val old = CellRange.parse(oldSqref.trim().split(Regex("\\s+")).first())
        val new = CellRange.parse(newSqref.trim().split(Regex("\\s+")).first())
        val dRow = if (old != null && new != null) new.firstRow - old.firstRow else 0
        val dCol = if (old != null && new != null) new.firstCol - old.firstCol else 0
        return Regex("(<(?:\\w+:)?(?:formula|formula1|formula2)\\b[^>]*>)([^<]*)(</)").replace(fragment) { m ->
            m.groupValues[1] + XmlEsc.text(FormulaRefs.shiftAnchored(XmlEsc.unescape(m.groupValues[2]), s, dRow, dCol)) + m.groupValues[3]
        }
    }

    private fun processSheetData(inner: String): String {
        var nextRow = 0
        return XmlEsc.mapChildren(inner) { tag, whole, content ->
            if (tag.local != "row") return@mapChildren whole
            val r = tag.attr("r")?.toIntOrNull()?.minus(1)?.takeIf { it >= 0 } ?: nextRow
            nextRow = r + 1
            val before = if (set != null && !setDone && set.row < r) newRow(set).also { setDone = true } else ""
            val newR = shift?.rows?.map(r) ?: r
            if (newR < 0 || newR >= CellRef.MAX_ROWS) return@mapChildren before.ifEmpty { null }
            tag.setAttr("r", (newR + 1).toString())
            tag.setAttr("spans", null)
            val cells = processRow(content ?: "", r)
            val rowXml = if (cells.isEmpty() && content == null) tag.render(true) else tag.render(false) + cells + "</${tag.qName}>"
            before + rowXml
        }
    }

    private fun processRow(content: String, r: Int): String {
        var nextCol = 0
        val targetsRow = set != null && !setDone && set.row == r
        var result = XmlEsc.mapChildren(content) { tag, whole, inner ->
            if (tag.local != "c") return@mapChildren whole
            val ref = tag.attr("r")?.let { CellRef.parse(it) } ?: -1L
            val parsedCol = if (ref >= 0) CellRef.colOf(ref) else CellRef.COL_MISSING
            val col = if (parsedCol != CellRef.COL_MISSING) parsedCol else nextCol
            nextCol = col + 1
            if (set != null && !setDone && set.row == r) {
                if (set.col == col) {
                    setDone = true
                    return@mapChildren newCell(set, tag)
                }
                if (set.col < col) {
                    setDone = true
                    return@mapChildren newCell(set, null) + rewriteCell(tag, inner, r, col)
                }
            }
            rewriteCell(tag, inner, r, col)
        }
        if (targetsRow && !setDone) {
            result += newCell(set!!, null)
            setDone = true
        }
        return result
    }

    /** Hücreyi kaydırır; paylaşılan formülleri bağımsız formüllere çevirir. Silinen hücre için "" döner. */
    private fun rewriteCell(tag: XTag, inner: String?, r: Int, c: Int): String {
        val mapped = shift?.mapCell(r, c) ?: (r to c)
        if (mapped == null) return ""
        tag.setAttr("r", CellRef.name(mapped.first, mapped.second))
        if (inner == null) return tag.render(true)
        val s = shift
        val body = XmlEsc.mapChildren(inner) { t, w, fInner ->
            if (t.local != "f") return@mapChildren w
            var text = XmlEsc.unescape(fInner ?: "")
            when (t.attr("t")) {
                "shared" -> {
                    val m = masters[t.attr("si")]
                    if (text.isEmpty() && m != null) text = FormulaRefs.translate(m.third, r - m.first, c - m.second)
                    t.setAttr("t", null); t.setAttr("si", null); t.setAttr("ref", null)
                    if (text.isEmpty()) return@mapChildren null
                }
                "array" -> if (s != null) {
                    val ref = t.attr("ref")
                    if (ref != null) t.setAttr("ref", s.mapRef(ref) ?: CellRef.name(mapped.first, mapped.second))
                }
            }
            if (s != null && text.isNotEmpty()) text = FormulaRefs.shift(text, s)
            if (text.isEmpty() && t.selfClosing) t.render(true) else t.render(false) + XmlEsc.text(text) + "</${t.qName}>"
        }
        return tag.render(false) + body + "</${tag.qName}>"
    }

    private fun newRow(e: SheetEdit.SetCell): String {
        val cell = newCell(e, null)
        if (cell.isEmpty()) return ""
        return "<${p}row r=\"${e.row + 1}\">$cell</${p}row>"
    }

    private fun newCell(e: SheetEdit.SetCell, existing: XTag?): String {
        val style = existing?.attr("s")
        val ref = CellRef.name(e.row, e.col)
        val head = buildString {
            append('<').append(p).append("c r=\"").append(ref).append('"')
            if (style != null) append(" s=\"").append(style).append('"')
        }
        return when (val v = e.input) {
            CellInput.Empty -> if (style != null) "$head/>" else ""
            is CellInput.Number -> "$head><${p}v>${formatNumber(v.value)}</${p}v></${p}c>"
            is CellInput.Bool -> "$head t=\"b\"><${p}v>${if (v.value) 1 else 0}</${p}v></${p}c>"
            is CellInput.Text -> "$head t=\"inlineStr\"><${p}is><${p}t xml:space=\"preserve\">${XmlEsc.text(v.text)}</${p}t></${p}is></${p}c>"
            is CellInput.Formula -> "$head><${p}f>${XmlEsc.text(v.formula)}</${p}f></${p}c>"
        }
    }

    private fun formatNumber(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "0"
        if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) return d.toLong().toString()
        return java.math.BigDecimal(d.toString()).stripTrailingZeros().toPlainString()
    }
}
