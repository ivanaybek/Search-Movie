package com.ayaz.viewer.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val turkish = "Şükrü Çağlayan – İstanbul, Iğdır; ğüşıöç ĞÜŞİÖÇ"

    private fun load(name: String, sheet: Int = 0): Triple<WorkbookData, SheetData, CellFormatter> {
        TestSupport.open(name).use { r ->
            val wb = r.readWorkbook()
            val s = r.readSheet(wb, sheet)
            return Triple(wb, s, CellFormatter(wb))
        }
    }

    private fun text(t: Triple<WorkbookData, SheetData, CellFormatter>, ref: String): String {
        val p = CellRef.parse(ref)
        return t.third.text(t.second, CellRef.rowOf(p), CellRef.colOf(p))
    }

    @Test
    fun simpleWorkbookValuesAndFormats() {
        val t = load("simple.xlsx")
        val (wb, sheet, _) = t
        assertEquals(listOf("Satışlar"), wb.sheets.map { it.name })
        assertEquals(7, sheet.rowCount)
        assertEquals(6, sheet.colCount)
        assertEquals("Ürün", text(t, "A1"))
        assertEquals("Birim Fiyat", text(t, "C1"))
        assertEquals("Çay", text(t, "A2"))
        assertEquals("12", text(t, "B2"))
        assertEquals("1.234,50 ₺", text(t, "C2"))
        assertEquals("05.03.2024", text(t, "D2"))
        assertEquals("12,5%", text(t, "E2"))
        assertEquals(turkish, text(t, "F2"))
        assertEquals("1.500.000", text(t, "B4"))
        assertEquals("0,75 ₺", text(t, "C4"))
        assertEquals("15.01.2024 14:30", text(t, "D4"))
        assertEquals("-42", text(t, "B5"))
        assertEquals("-1.234,57 ₺", text(t, "C5"))
        assertEquals("29.02.2000", text(t, "D5"))
        assertEquals("-3,3%", text(t, "E5"))
        assertEquals("", text(t, "F5"))
        assertEquals("0,000012345", text(t, "B7"))
        assertEquals("123456789012", text(t, "C7"))
        assertEquals("DOĞRU", text(t, "D7"))
        assertEquals("0,3333333333", text(t, "E7"))
        assertEquals("5,00E+00", text(t, "F7"))
        // Boş satır
        assertNull(sheet.row(5))
    }

    @Test
    fun simpleWorkbookStylesAndLayout() {
        val (wb, sheet, _) = load("simple.xlsx")
        val header = sheet.row(0)!!
        val st = wb.style(header.styles[0])
        assertTrue(st.bold)
        assertEquals(0xFF1F4E79.toInt(), st.fillColor)
        assertEquals(0xFFFFFFFF.toInt(), st.fontColor)
        assertEquals(HAlign.CENTER, st.hAlign)
        assertNotNull(st.borderBottom)
        val a7 = sheet.row(6)!!
        val s7 = wb.style(a7.styles[a7.indexOf(0)])
        assertTrue(s7.bold && s7.italic)
        assertEquals(0xFFC00000.toInt(), s7.fontColor)
        assertEquals(1, sheet.frozenRows)
        assertEquals(1, sheet.frozenCols)
        assertEquals(22f, sheet.colWidthChars(0), 0.01f)
        assertEquals(24f, sheet.rowHeightPt(0), 0.01f)
        assertEquals(sheet.defaultRowHeightPt, sheet.rowHeightPt(3), 0.01f)
        assertTrue(sheet.showGridLines)
    }

    @Test
    fun multipleSheets() {
        TestSupport.open("multi_sheet.xlsx").use { r ->
            val wb = r.readWorkbook()
            assertEquals(listOf("Özet", "Veriler", "Boş Sayfa", "Gizli", "Birleşik"), wb.sheets.map { it.name })
            assertTrue(wb.sheets[3].hidden)
            assertTrue(wb.sheets.all { it.isWorksheet })
            val fmt = CellFormatter(wb)

            val ozet = r.readSheet(wb, 0)
            assertEquals("Toplam", fmt.text(ozet, 0, 0))
            assertEquals("1000", fmt.text(ozet, 0, 1))

            val data = r.readSheet(wb, 1)
            assertEquals(201, data.rowCount)
            assertEquals(10, data.colCount)
            assertEquals("2000", fmt.text(data, 199, 9))
            assertEquals("Son satır", fmt.text(data, 200, 0))
            assertEquals(2001, data.cellCount)

            val empty = r.readSheet(wb, 2)
            assertTrue(empty.isEmpty)

            val merged = r.readSheet(wb, 4)
            assertTrue(merged.merges.contains(CellRange(0, 0, 0, 3)))
            assertTrue(merged.merges.contains(CellRange(2, 1, 4, 2)))
            assertEquals("Başlık (A1:D1 birleşik)", fmt.text(merged, 0, 0))
            assertEquals(10, merged.rowCount)
            assertEquals(6, merged.colCount)
            assertEquals(0f, merged.colWidthChars(4), 0f)
            assertEquals(0xFFFF0000.toInt(), merged.tabColor)
            val a3 = merged.row(2)!!
            val fill = wb.style(a3.styles[a3.indexOf(0)]).fillColor
            assertNotNull(fill)
            // accent1 (4472C4) %60 açılmış -> belirgin şekilde daha açık bir mavi
            val r8 = (fill!! shr 16) and 0xFF
            val b8 = fill and 0xFF
            assertTrue("r=$r8", r8 in 150..200)
            assertTrue("b=$b8", b8 > 210)
        }
    }

    @Test
    fun handcraftedEdgeCases() {
        TestSupport.open("handcrafted.xlsx").use { r ->
            val wb = r.readWorkbook()
            assertTrue(wb.date1904)
            assertEquals(2, wb.sheets.size)
            assertTrue(wb.sheets[0].isWorksheet)
            assertFalse("grafik sayfası tablo değildir", wb.sheets[1].isWorksheet)
            val s = r.readSheet(wb, 0)
            val f = CellFormatter(wb)
            assertEquals("düz metin", f.text(s, 0, 0))
            assertEquals("Zengin metin", f.text(s, 0, 1))
            assertEquals("satır\rsonu", f.text(s, 0, 2))
            assertEquals("satır içi ğüş", f.text(s, 1, 0))
            assertEquals("2", f.text(s, 1, 1))
            assertEquals("ab", f.text(s, 1, 2))
            assertEquals("DOĞRU", f.text(s, 1, 3))
            assertEquals("#DIV/0!", f.text(s, 1, 4))
            assertEquals(CellDisplay.Kind.ERROR, f.display(s, 1, 4)!!.kind)
            assertEquals("0", f.text(s, 4, 0))
            assertEquals("", f.text(s, 4, 1)) // geçersiz paylaşılan dize indeksi
            assertEquals("29.02.2024 13:45", f.text(s, 4, 2))
            assertEquals(5, s.rowCount)
            assertEquals(5, s.colCount)
        }
    }

    @Test
    fun englishLocaleFormatting() {
        TestSupport.open("simple.xlsx").use { r ->
            val wb = r.readWorkbook()
            val s = r.readSheet(wb, 0)
            val f = CellFormatter(wb, FormatLocale.ENGLISH)
            assertEquals("1,234.50 ₺", f.text(s, 1, 2))
            assertEquals("TRUE", f.text(s, 6, 3))
        }
    }

    @Test
    fun invalidFilesFailGracefully() {
        expectReason("empty.xlsx", XlsxException.Reason.EMPTY)
        expectReason("not_xlsx.xlsx", XlsxException.Reason.NOT_XLSX)
        expectReason("legacy.xls", XlsxException.Reason.LEGACY_OR_ENCRYPTED)
        expectReason("corrupt.xlsx", XlsxException.Reason.CORRUPT)
        expectReason("zip_not_xlsx.xlsx", XlsxException.Reason.CORRUPT)
    }

    private fun expectReason(name: String, reason: XlsxException.Reason) {
        try {
            TestSupport.open(name).use { r ->
                val wb = r.readWorkbook()
                r.readSheet(wb, 0)
            }
            fail("$name için hata bekleniyordu")
        } catch (e: XlsxException) {
            assertEquals(name, reason, e.reason)
        }
    }

    @Test
    fun search() {
        val (_, sheet, fmt) = load("simple.xlsx")
        val hits = SheetSearch.find(sheet, fmt, "ISTANBUL")
        assertEquals(1, hits.size)
        assertEquals(1, CellRef.rowOf(hits[0]))
        assertEquals(5, CellRef.colOf(hits[0]))
        // Türkçe büyük/küçük harf: "ığdır" -> "Iğdır"
        assertEquals(2, SheetSearch.find(sheet, fmt, "ığdır").size)
        assertEquals(1, SheetSearch.find(sheet, fmt, "1.234,50").size)
        assertEquals(0, SheetSearch.find(sheet, fmt, "   ").size)
    }

    // ------------------------------------------------------------------ büyük dosya

    private fun writeLargeXlsx(file: File, rows: Int, cols: Int) {
        ZipOutputStream(file.outputStream().buffered()).use { z ->
            fun entry(name: String, body: String) {
                z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry()
            }
            entry(
                "[Content_Types].xml",
                """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""",
            )
            entry(
                "xl/workbook.xml",
                """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Büyük" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/></Relationships>""",
            )
            val sst = StringBuilder("""<?xml version="1.0"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            for (i in 0 until 1000) sst.append("<si><t>Ürün $i</t></si>")
            sst.append("</sst>")
            entry("xl/sharedStrings.xml", sst.toString())
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val w = z.bufferedWriter()
            w.write("""<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            for (r in 1..rows) {
                w.write("<row r=\"$r\">")
                w.write("<c r=\"A$r\" t=\"s\"><v>${r % 1000}</v></c>")
                for (c in 1 until cols) {
                    w.write("<c r=\"${CellRef.columnName(c)}$r\"><v>${r * c + 0.5}</v></c>")
                }
                w.write("</row>")
            }
            w.write("</sheetData></worksheet>")
            w.flush()
            z.closeEntry()
        }
    }

    @Test
    fun largeSheetParsesWithinBudget() {
        val f = tmp.newFile("large.xlsx")
        val rows = 100_000
        val cols = 12
        writeLargeXlsx(f, rows, cols)
        val start = System.nanoTime()
        XlsxReader(f, TestSupport.parserFactory).use { r ->
            val wb = r.readWorkbook()
            val s = r.readSheet(wb, 0)
            val ms = (System.nanoTime() - start) / 1_000_000
            println("Büyük sayfa: ${f.length() / 1024} KB, $rows x $cols hücre, ${ms} ms")
            assertEquals(rows, s.rowCount)
            assertEquals(cols, s.colCount)
            assertEquals(rows * cols, s.cellCount)
            assertFalse(s.truncated)
            val fmt = CellFormatter(wb)
            assertEquals("Ürün 0", fmt.text(s, rows - 1, 0))
            assertEquals("1100000,5", fmt.text(s, rows - 1, 11))
            assertTrue("ayrıştırma çok yavaş: $ms ms", ms < 30_000)
        }
    }

    @Test
    fun cellBudgetTruncatesInsteadOfCrashing() {
        val f = tmp.newFile("budget.xlsx")
        writeLargeXlsx(f, 5_000, 10)
        XlsxReader(f, TestSupport.parserFactory, maxCells = 10_000).use { r ->
            val wb = r.readWorkbook()
            val s = r.readSheet(wb, 0)
            assertTrue(s.truncated)
            assertEquals(10_000, s.cellCount)
            assertEquals(1_000, s.rowCount)
        }
    }

    @Test(expected = ParseCancelledException::class)
    fun cancellationStopsParsing() {
        val f = tmp.newFile("cancel.xlsx")
        writeLargeXlsx(f, 5_000, 10)
        XlsxReader(f, TestSupport.parserFactory).use { r ->
            val wb = r.readWorkbook()
            r.readSheet(wb, 0) { true }
        }
    }

    @Test
    fun pathResolution() {
        assertEquals("xl/worksheets/sheet1.xml", XlsxReader.resolvePath("xl/", "worksheets/sheet1.xml"))
        assertEquals("xl/worksheets/sheet1.xml", XlsxReader.resolvePath("xl/", "/xl/worksheets/sheet1.xml"))
        assertEquals("xl/theme/theme1.xml", XlsxReader.resolvePath("xl/worksheets/", "../theme/theme1.xml"))
        assertEquals("xl/_rels/workbook.xml.rels", XlsxReader.relsPathFor("xl/workbook.xml"))
    }
}
