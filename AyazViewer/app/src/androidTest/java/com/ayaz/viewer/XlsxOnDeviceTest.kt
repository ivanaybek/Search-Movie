package com.ayaz.viewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayaz.viewer.data.UserFacingException
import com.ayaz.viewer.viewer.xlsx.SheetLoad
import com.ayaz.viewer.viewer.xlsx.XlsxController
import com.ayaz.viewer.xlsx.FormatLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** XLSX motorunun Android'in kendi XmlPullParser'ı ile cihaz üzerinde testi. */
@RunWith(AndroidJUnit4::class)
class XlsxOnDeviceTest {

    /** Takılan bir test, adıyla birlikte başarısız olsun (sonsuza dek beklemesin). */
    @get:org.junit.Rule(order = 0)
    val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(120)

    private suspend fun load(c: XlsxController, index: Int): SheetLoad.Ready = withContext(Dispatchers.Main) {
        c.select(index)
        withTimeout(60_000) {
            c.state.first { it.index == index && (it is SheetLoad.Ready || it is SheetLoad.Message) }
        } as? SheetLoad.Ready ?: throw AssertionError("sayfa $index yüklenemedi: ${c.state.value}")
    }

    @Test
    fun simpleWorkbook() = runBlocking {
        val c = XlsxController.open(TestFiles.cacheCopy("simple.xlsx"), false, FormatLocale.TURKISH, TestFiles.app.documents.editDir)
        try {
            val r = load(c, 0)
            val f = r.formatter
            assertEquals("Satışlar", r.sheet.name)
            assertEquals("Ürün", f.text(r.sheet, 0, 0))
            assertEquals("1.234,50 ₺", f.text(r.sheet, 1, 2))
            assertEquals("05.03.2024", f.text(r.sheet, 1, 3))
            assertEquals("Şükrü Çağlayan – İstanbul, Iğdır; ğüşıöç ĞÜŞİÖÇ", f.text(r.sheet, 1, 5))
            assertEquals(1, r.sheet.frozenRows)
        } finally {
            c.close()
        }
    }

    @Test
    fun multipleSheetsAndMerges() = runBlocking {
        val c = XlsxController.open(TestFiles.cacheCopy("multi_sheet.xlsx"), false, FormatLocale.TURKISH, TestFiles.app.documents.editDir)
        try {
            assertEquals(5, c.sheetCount)
            val data = load(c, 1)
            assertEquals(201, data.sheet.rowCount)
            val empty = load(c, 2)
            assertTrue(empty.sheet.isEmpty)
            val merged = load(c, 4)
            assertEquals(2, merged.sheet.merges.size)
        } finally {
            c.close()
        }
    }

    @Test
    fun handcraftedAndChartSheet() = runBlocking {
        val c = XlsxController.open(TestFiles.cacheCopy("handcrafted.xlsx"), false, FormatLocale.TURKISH, TestFiles.app.documents.editDir)
        try {
            val r = load(c, 0)
            assertEquals("Zengin metin", r.formatter.text(r.sheet, 0, 1))
            assertEquals("29.02.2024 13:45", r.formatter.text(r.sheet, 4, 2))
            withContext(Dispatchers.Main) { c.select(1) }
            val msg = withTimeout(10_000) { c.state.first { it.index == 1 } }
            assertTrue(msg is SheetLoad.Message)
        } finally {
            c.close()
        }
    }

    private fun expectMessage(asset: String, contains: String) {
        try {
            XlsxController.open(TestFiles.cacheCopy(asset), false, FormatLocale.TURKISH, TestFiles.app.documents.editDir).close()
            fail("$asset için hata bekleniyordu")
        } catch (e: UserFacingException) {
            assertTrue("mesaj: ${e.message}", e.message!!.contains(contains, ignoreCase = true))
        }
    }

    @Test
    fun invalidFiles() {
        expectMessage("empty.xlsx", "boş")
        expectMessage("legacy.xls", ".xls")
        expectMessage("corrupt.xlsx", "bozuk")
        expectMessage("not_xlsx.xlsx", "geçerli")
    }

    @Test
    fun largeSheetOnDevice() = runBlocking {
        val f = File(TestFiles.app.cacheDir, "test/large_device.xlsx")
        f.parentFile?.mkdirs()
        val rows = 50_000
        ZipOutputStream(f.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("xl/workbook.xml"))
            z.write(
                ("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"Büyük\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>").toByteArray(),
            )
            z.closeEntry()
            z.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            z.write(
                ("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "</Relationships>").toByteArray(),
            )
            z.closeEntry()
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val w = z.bufferedWriter()
            w.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            for (r in 1..rows) {
                w.write("<row r=\"$r\">")
                for (c in 0 until 10) w.write("<c><v>${r * 10 + c}</v></c>")
                w.write("</row>")
            }
            w.write("</sheetData></worksheet>")
            w.flush()
            z.closeEntry()
        }
        val t0 = System.nanoTime()
        val c = XlsxController.open(f, false, FormatLocale.TURKISH, TestFiles.app.documents.editDir)
        try {
            val r = load(c, 0)
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("Cihazda büyük sayfa: $rows x 10 hücre, $ms ms")
            assertEquals(rows, r.sheet.rowCount)
            assertEquals("500009", r.formatter.text(r.sheet, rows - 1, 9))
            assertTrue("çok yavaş: $ms ms", ms < 60_000)
            val hits = c.search(r.sheet, "499999")
            assertEquals(1, hits.size)
        } finally {
            c.close()
        }
    }
}
