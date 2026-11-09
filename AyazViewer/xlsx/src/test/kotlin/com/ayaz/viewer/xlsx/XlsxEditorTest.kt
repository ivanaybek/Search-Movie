package com.ayaz.viewer.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class XlsxEditorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun edit(src: String, sheet: Int, e: SheetEdit, outName: String = "out.xlsx"): File {
        val out = tmp.newFile(outName)
        XlsxEditor.apply(TestSupport.resource(src), out, sheet, e, TestSupport.parserFactory)
        // İsteğe bağlı: dış doğrulama (openpyxl) için kopya bırak
        System.getProperty("ayaz.editOut")?.let { dir -> out.copyTo(File(dir, outName), overwrite = true) }
        return out
    }

    private fun editFile(src: File, sheet: Int, e: SheetEdit, outName: String): File {
        val out = tmp.newFile(outName)
        XlsxEditor.apply(src, out, sheet, e, TestSupport.parserFactory)
        System.getProperty("ayaz.editOut")?.let { dir -> out.copyTo(File(dir, outName), overwrite = true) }
        return out
    }

    private class Loaded(val wb: WorkbookData, val sheet: SheetData, val fmt: CellFormatter) {
        fun text(ref: String): String {
            val p = CellRef.parse(ref)
            return fmt.text(sheet, CellRef.rowOf(p), CellRef.colOf(p))
        }

        fun formula(ref: String): String? {
            val p = CellRef.parse(ref)
            return sheet.formulaAt(CellRef.rowOf(p), CellRef.colOf(p))
        }
    }

    private fun load(f: File, sheet: Int = 0): Loaded = XlsxReader(f, TestSupport.parserFactory).use { r ->
        val wb = r.readWorkbook()
        Loaded(wb, r.readSheet(wb, sheet), CellFormatter(wb))
    }

    private fun entryText(f: File, name: String): String? = ZipFile(f).use { z ->
        z.getEntry(name)?.let { e -> z.getInputStream(e).use { String(it.readBytes(), Charsets.UTF_8) } }
    }

    // ------------------------------------------------------------------ hücre düzenleme

    @Test
    fun setTextKeepsStyleAndOtherCells() {
        val out = edit("simple.xlsx", 0, SheetEdit.SetCell(0, 0, CellInput.Text("Ürünler ğüşİ")))
        val l = load(out)
        assertEquals("Ürünler ğüşİ", l.text("A1"))
        val st = l.wb.style(l.sheet.row(0)!!.styles[0])
        assertTrue("başlık biçimi korunmalı", st.bold)
        assertEquals(0xFF1F4E79.toInt(), st.fillColor)
        assertEquals("1.234,50 ₺", l.text("C2"))
        assertEquals("05.03.2024", l.text("D2"))
        assertEquals(1, l.sheet.frozenRows)
        assertEquals(22f, l.sheet.colWidthChars(0), 0.01f)
    }

    @Test
    fun setValuesInNewCellsRowsAndClear() {
        var f = edit("simple.xlsx", 0, SheetEdit.SetCell(1, 6, CellInput.Number(42.5)), "a.xlsx") // G2 yeni hücre
        f = editFile(f, 0, SheetEdit.SetCell(20, 1, CellInput.Text("Yeni satır")), "b.xlsx") // B21 yeni satır
        f = editFile(f, 0, SheetEdit.SetCell(5, 0, CellInput.Bool(true)), "c.xlsx") // A6 (boş satır 6)
        f = editFile(f, 0, SheetEdit.SetCell(1, 0, CellInput.Empty), "d.xlsx") // A2 temizle
        f = editFile(f, 0, SheetEdit.SetCell(2, 7, CellInput.Formula("B2*2")), "e.xlsx")
        val l = load(f)
        assertEquals("42,5", l.text("G2"))
        assertEquals("Yeni satır", l.text("B21"))
        assertEquals("DOĞRU", l.text("A6"))
        assertEquals("", l.text("A2"))
        assertEquals("12", l.text("B2"))
        assertEquals("B2*2", l.formula("H3"))
        assertEquals("=B2*2", l.text("H3")) // hesaplanmamış formül metni gösterilir
        assertEquals(21, l.sheet.rowCount)
    }

    @Test
    fun editingEmptySheetAndPrefixedNamespace() {
        val out = edit("multi_sheet.xlsx", 2, SheetEdit.SetCell(0, 0, CellInput.Text("artık dolu")))
        assertEquals("artık dolu", load(out, 2).text("A1"))
        val hc = edit("handcrafted.xlsx", 0, SheetEdit.SetCell(1, 1, CellInput.Number(7.0)), "hc.xlsx")
        val l = load(hc)
        assertEquals("7", l.text("B2"))
        assertEquals("satır içi ğüş", l.text("A2"))
        assertEquals("29.02.2024 13:45", l.text("C5"))
        assertTrue(entryText(hc, "xl/worksheets/sheet1.xml")!!.contains("<x:c r=\"B2\"><x:v>7</x:v></x:c>"))
    }

    @Test
    fun calcChainRemovedAndRecalcRequested() {
        val out = edit("formulas.xlsx", 0, SheetEdit.SetCell(0, 0, CellInput.Number(10.0)))
        ZipFile(out).use { z -> assertNull(z.getEntry("xl/calcChain.xml")) }
        assertFalse(entryText(out, "[Content_Types].xml")!!.contains("calcChain"))
        assertFalse(entryText(out, "xl/_rels/workbook.xml.rels")!!.contains("calcChain"))
        assertTrue(entryText(out, "xl/workbook.xml")!!.contains("fullCalcOnLoad=\"1\""))
        assertTrue(entryText(out, "xl/workbook.xml")!!.contains("calcId=\"191029\""))
        // diğer parçalar korunur
        assertTrue(entryText(out, "xl/worksheets/_rels/sheet1.xml.rels")!!.contains("example.com"))
        val l = load(out)
        assertEquals("10", l.text("A1"))
        // paylaşılan formüller bağımsız formüle açılır
        assertEquals("A3*2", l.formula("C3"))
        assertFalse(entryText(out, "xl/worksheets/sheet1.xml")!!.contains("t=\"shared\""))
        assertEquals("6", l.text("C3")) // önbellek değeri korunur
    }

    @Test
    fun formulasAreReadFromFile() {
        val l = load(TestSupport.resource("formulas.xlsx"))
        assertEquals("SUM(A1:A5)", l.formula("B1"))
        assertEquals("A1*2", l.formula("C1"))
        assertEquals("A4*2", l.formula("C4"))
        assertEquals("$" + "A$" + "1+'Diğer Sayfa'!A1+\"A1\"", l.formula("F5"))
    }

    // ------------------------------------------------------------------ satır/sütun

    @Test
    fun insertRowShiftsEverything() {
        val out = edit("formulas.xlsx", 0, SheetEdit.InsertRows(2))
        val l = load(out)
        assertEquals("3", l.text("A4"))
        assertEquals("", l.text("A3"))
        assertEquals("SUM(A1:A6)", l.formula("B1"))
        assertEquals("A2*2", l.formula("C2"))
        assertEquals("A4*2", l.formula("C4"))
        assertEquals("$" + "A$" + "1+'Diğer Sayfa'!A1+\"A1\"", l.formula("F6"))
        assertEquals("05.03.2024", l.text("E4"))
        assertTrue(l.sheet.merges.contains(CellRange(0, 3, 1, 4)))
        val xml = entryText(out, "xl/worksheets/sheet1.xml")!!
        assertTrue(xml.contains("sqref=\"A1:A6\""))
        assertTrue(xml.contains("sqref=\"F1:F11\""))
        assertTrue(xml.contains("<hyperlink ref=\"D1\""))
        assertFalse(xml.contains("<dimension"))
    }

    @Test
    fun deleteRowGivesRefErrorsAndShrinksRanges() {
        val out = edit("formulas.xlsx", 0, SheetEdit.DeleteRows(0))
        val l = load(out)
        assertEquals("2", l.text("A1"))
        assertEquals("A1*2", l.formula("C1"))
        assertEquals("#REF!+'Diğer Sayfa'!A1+\"A1\"", l.formula("F4"))
        assertTrue(l.sheet.merges.contains(CellRange(0, 3, 0, 4)))
        val xml = entryText(out, "xl/worksheets/sheet1.xml")!!
        assertTrue(xml.contains("sqref=\"A1:A4\""))
        assertTrue(xml.contains("sqref=\"F1:F9\""))
        assertFalse("silinen satırdaki köprü kaldırılmalı", xml.contains("<hyperlink "))
        assertTrue("koşullu biçim formülü sol üst hücreye göre kalmalı", xml.contains("<formula>A1&gt;3</formula>"))
        assertFalse(xml.contains("<hyperlinks"))
    }

    @Test
    fun deleteColumnRemovesAndShifts() {
        val out = edit("formulas.xlsx", 0, SheetEdit.DeleteCols(0))
        val l = load(out)
        assertEquals("SUM(#REF!)", l.formula("A1"))
        assertEquals("#REF!*2", l.formula("B3"))
        val xml = entryText(out, "xl/worksheets/sheet1.xml")!!
        assertFalse("A sütunundaki koşullu biçim kalkmalı", xml.contains("conditionalFormatting"))
        assertTrue(xml.contains("<col min=\"2\" max=\"3\""))
        assertFalse(xml.contains("min=\"1\" max=\"1\""))
        assertTrue(l.sheet.merges.contains(CellRange(0, 2, 1, 3)))
        assertEquals(20f, l.sheet.colWidthChars(1), 0.01f)
    }

    @Test
    fun insertColumnShifts() {
        val out = edit("formulas.xlsx", 0, SheetEdit.InsertCols(1))
        val l = load(out)
        assertEquals("1", l.text("A1"))
        assertEquals("SUM(A1:A5)", l.formula("C1"))
        assertEquals("A2*2", l.formula("D2"))
        assertTrue(l.sheet.merges.contains(CellRange(0, 4, 1, 5)))
        assertEquals(20f, l.sheet.colWidthChars(3), 0.01f)
        assertEquals(12f, l.sheet.colWidthChars(0), 0.01f)
    }

    @Test
    fun structuralEditsRefusedWhenSheetHasTables() {
        val src = tmp.newFile("tables.xlsx")
        java.util.zip.ZipOutputStream(src.outputStream()).use { z ->
            fun put(n: String, s: String) { z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(s.toByteArray()); z.closeEntry() }
            put("xl/workbook.xml", "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"T\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
            put("xl/worksheets/sheet1.xml", "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\"><c r=\"A1\"><v>1</v></c></row></sheetData><tableParts count=\"1\"><tablePart r:id=\"rId1\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/></tableParts></worksheet>")
        }
        try {
            XlsxEditor.apply(src, tmp.newFile("t-out.xlsx"), 0, SheetEdit.InsertRows(0), TestSupport.parserFactory)
            fail("tablo içeren sayfada satır ekleme reddedilmeli")
        } catch (e: XlsxEditException) {
            assertEquals(XlsxEditException.Reason.HAS_TABLES, e.reason)
        }
        // hücre düzenleme yine de çalışır
        val out = tmp.newFile("t-out2.xlsx")
        XlsxEditor.apply(src, out, 0, SheetEdit.SetCell(0, 0, CellInput.Number(5.0)), TestSupport.parserFactory)
        assertEquals("5", load(out).text("A1"))
    }

    @Test
    fun sourceFileIsNeverModified() {
        val src = TestSupport.resource("formulas.xlsx")
        val before = src.readBytes()
        edit("formulas.xlsx", 0, SheetEdit.DeleteCols(2))
        assertTrue(before.contentEquals(src.readBytes()))
    }

    @Test
    fun manyEditsInSequenceStayReadable() {
        var f = TestSupport.resource("formulas.xlsx")
        val ops = listOf(
            SheetEdit.InsertRows(0), SheetEdit.InsertCols(0), SheetEdit.SetCell(0, 0, CellInput.Text("Başlık")),
            SheetEdit.DeleteRows(3), SheetEdit.DeleteCols(4), SheetEdit.SetCell(10, 10, CellInput.Number(1e20)),
        )
        ops.forEachIndexed { i, op -> f = editFile(f, 0, op, "seq$i.xlsx") }
        val l = load(f)
        assertEquals("Başlık", l.text("A1"))
        assertEquals("1E+20", l.text("K11"))
        assertEquals("SUM(B2:B5)", l.formula("C2"))
    }
}

class FormulaRefsTest {
    private val insRow2 = SheetShift(AxisShift(2, 1), null)
    private val delRow2 = SheetShift(AxisShift(2, -1), null)
    private val insColB = SheetShift(null, AxisShift(1, 1))

    @Test
    fun shiftsReferencesButNotStringsNamesOrOtherSheets() {
        val f = "SUM(A1:B\$3)*Sheet2!C4+'My Sheet'!D5+\"A3\"+\$A\$3+A:A+3:3+LOG10(5)+Table1[Col3]+TRUE"
        assertEquals(
            "SUM(A1:B\$4)*Sheet2!C4+'My Sheet'!D5+\"A3\"+\$A\$4+A:A+4:4+LOG10(5)+Table1[Col3]+TRUE",
            FormulaRefs.shift(f, insRow2),
        )
    }

    @Test
    fun deletedReferencesBecomeRefErrors() {
        assertEquals("#REF!+A3", FormulaRefs.shift("A3+A4", delRow2))
        assertEquals("SUM(A1:A3)", FormulaRefs.shift("SUM(A1:A4)", delRow2))
        assertEquals("SUM(#REF!)", FormulaRefs.shift("SUM(A3:A3)", delRow2))
        assertEquals("C1+\$C\$2+A1", FormulaRefs.shift("B1+\$B\$2+A1", insColB))
        assertEquals("XFD1", FormulaRefs.shift("XFD1", SheetShift(null, AxisShift(16383, -1))).let { if (it == "#REF!") "XFD1" else it })
    }

    @Test
    fun translateMovesOnlyRelativeParts() {
        assertEquals("B3*\$A\$1+C\$1", FormulaRefs.translate("A1*\$A\$1+B\$1", 2, 1))
        assertEquals("#REF!", FormulaRefs.translate("A1", -1, 0))
    }

    @Test
    fun axisShiftRanges() {
        val ins = AxisShift(5, 2)
        assertEquals(3..9, ins.mapRange(3, 7))
        assertEquals(7..9, ins.mapRange(5, 7))
        assertEquals(1..4, ins.mapRange(1, 4))
        val del = AxisShift(5, -2)
        assertEquals(3..5, del.mapRange(3, 7))
        assertEquals(null, del.mapRange(5, 6))
        assertEquals(5..6, del.mapRange(6, 8))
        assertEquals(-1, del.map(6))
        assertEquals(5, del.map(7))
    }
}

class CellEditingTest {
    private val tr = FormatLocale.TURKISH

    @Test
    fun parsesTurkishNumbersDatesFormulas() {
        assertEquals(CellInput.Number(1234.56), CellEditing.parseInput("1.234,56", tr, false, false))
        assertEquals(CellInput.Number(12.5), CellEditing.parseInput("12,5", tr, false, false))
        assertEquals(CellInput.Number(1.5), CellEditing.parseInput("1.5", tr, false, false))
        assertEquals(CellInput.Number(1500.0), CellEditing.parseInput("1.500", tr, false, false))
        assertEquals(CellInput.Number(0.125), CellEditing.parseInput("12,5%", tr, false, false))
        assertEquals(CellInput.Number(-3.0), CellEditing.parseInput("-3", tr, false, false))
        assertEquals(CellInput.Formula("SUM(A1:A3)"), CellEditing.parseInput("=SUM(A1:A3)", tr, false, false))
        assertEquals(CellInput.Text("123"), CellEditing.parseInput("'123", tr, false, false))
        assertEquals(CellInput.Bool(true), CellEditing.parseInput("doğru", tr, false, false))
        assertEquals(CellInput.Empty, CellEditing.parseInput("   ", tr, false, false))
        assertEquals(CellInput.Text("Iğdır 2024"), CellEditing.parseInput("Iğdır 2024", tr, false, false))
        assertEquals(CellInput.Text("05.03.2024"), CellEditing.parseInput("05.03.2024", tr, false, false))
        assertEquals(CellInput.Number(45356.0), CellEditing.parseInput("05.03.2024", tr, true, false))
        assertEquals(CellInput.Number(1234.5), CellEditing.parseInput("1,234.5", FormatLocale.ENGLISH, false, false))
    }

    @Test
    fun editTextRoundTrips() {
        XlsxReader(TestSupport.resource("simple.xlsx"), TestSupport.parserFactory).use { r ->
            val wb = r.readWorkbook()
            val s = r.readSheet(wb, 0)
            assertEquals("Çay", CellEditing.editText(s, wb, tr, 1, 0))
            assertEquals("1234,5", CellEditing.editText(s, wb, tr, 1, 2))
            assertEquals("05.03.2024", CellEditing.editText(s, wb, tr, 1, 3))
            assertEquals("15.01.2024 14:30", CellEditing.editText(s, wb, tr, 3, 3))
            assertEquals("12,5%", CellEditing.editText(s, wb, tr, 1, 4))
            assertEquals("DOĞRU", CellEditing.editText(s, wb, tr, 6, 3))
            assertTrue(CellEditing.isDateStyle(s, wb, 1, 3))
            assertFalse(CellEditing.isDateStyle(s, wb, 1, 2))
        }
        XlsxReader(TestSupport.resource("formulas.xlsx"), TestSupport.parserFactory).use { r ->
            val wb = r.readWorkbook()
            val s = r.readSheet(wb, 0)
            assertEquals("=SUM(A1:A5)", CellEditing.editText(s, wb, tr, 0, 1))
            assertEquals("=A2*2", CellEditing.editText(s, wb, tr, 1, 2))
        }
    }
}
