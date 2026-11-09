package com.ayaz.viewer.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class XlsxTemplatesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun blankWorkbookIsReadableAndEditable() {
        val f = File(tmp.root, "yeni.xlsx")
        XlsxTemplates.blankWorkbook(f, "Gelirler/Giderler")
        XlsxReader(f, TestSupport.parserFactory).use { r ->
            val wb = r.readWorkbook()
            assertEquals(listOf("Gelirler Giderler"), wb.sheets.map { it.name })
            assertTrue(r.readSheet(wb, 0).isEmpty)
        }
        var cur = f
        val steps = listOf(
            SheetEdit.SetCell(0, 0, CellInput.Text("Ürün")),
            SheetEdit.SetCell(0, 1, CellInput.Text("Fiyat")),
            SheetEdit.SetCell(1, 0, CellInput.Text("Çay")),
            SheetEdit.SetCell(1, 1, CellInput.Number(12.5)),
            SheetEdit.SetCell(2, 1, CellInput.Formula("SUM(B2:B2)")),
        )
        steps.forEachIndexed { i, e ->
            val out = File(tmp.root, "adim$i.xlsx")
            XlsxEditor.apply(cur, out, 0, e, TestSupport.parserFactory)
            cur = out
        }
        System.getProperty("ayaz.editOut")?.let { cur.copyTo(File(it, "yeni_tablo.xlsx"), overwrite = true) }
        XlsxReader(cur, TestSupport.parserFactory).use { r ->
            val wb = r.readWorkbook()
            val s = r.readSheet(wb, 0)
            val fmt = CellFormatter(wb)
            assertEquals("Ürün", fmt.text(s, 0, 0))
            assertEquals("12,5", fmt.text(s, 1, 1))
            assertEquals("SUM(B2:B2)", s.formulaAt(2, 1))
        }
    }
}
