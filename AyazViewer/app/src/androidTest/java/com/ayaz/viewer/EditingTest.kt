package com.ayaz.viewer

import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayaz.viewer.viewer.pdf.InkMark
import com.ayaz.viewer.viewer.pdf.PageOp
import com.ayaz.viewer.viewer.pdf.PdfController
import com.ayaz.viewer.viewer.pdf.PdfEditor
import com.ayaz.viewer.viewer.pdf.TextMark
import com.ayaz.viewer.viewer.xlsx.SheetLoad
import com.ayaz.viewer.viewer.xlsx.XlsxController
import com.ayaz.viewer.xlsx.CellInput
import com.ayaz.viewer.xlsx.FormatLocale
import com.ayaz.viewer.xlsx.SheetEdit
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** PDF işaretleme/sayfa işlemleri ve XLSX düzenleme (cihaz üzerinde). */
@RunWith(AndroidJUnit4::class)
class EditingTest {

    @get:Rule(order = 0)
    val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(120)

    private val app get() = TestFiles.app

    private fun openPdf(f: File) = PdfController.open(
        app, { ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) }, { f }, f.length(),
    )

    private fun out(name: String) = File(app.cacheDir, "test/$name").apply { parentFile?.mkdirs(); delete() }

    @Test
    fun inkAndTextAreWrittenAsAnnotationsAndRendered() = runBlocking {
        val src = TestFiles.cacheCopy("single_page.pdf")
        val before = src.readBytes()
        val target = out("isaretli.pdf")
        val red = 0xFFE53935.toInt()
        // Sayfanın sol üst bölgesine kalın kırmızı bir çizgi + Türkçe metin
        val stroke = InkMark(0, (0..40).map { Offset(60f + it * 5f, 120f) }, red, 12f, false)
        val text = TextMark(0, 60f, 300f, "Merhaba ğüşıöç İ", 0xFF1E63FF.toInt(), 20f)
        PdfEditor.write(app, src, target, listOf(stroke, text), null)
        assertArrayEquals("kaynak dosya değişmemeli", before, src.readBytes())

        PDDocument.load(target).use { doc ->
            val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            assertTrue("eklenen metin aranabilir olmalı", text.contains("Merhaba ğüşıöç İ"))
            assertTrue("belgenin kendi metni korunmalı", text.contains("anahtar"))
        }
        val c = openPdf(target)
        try {
            val bmp = c.render(0, 1190)!! // 2 piksel / nokta
            var redPixels = 0
            for (x in 120..520 step 4) for (y in 220..260 step 2) {
                val p = bmp.getPixel(x, y)
                if (Color.red(p) > 200 && Color.green(p) < 110 && Color.blue(p) < 110) redPixels++
            }
            assertTrue("kırmızı çizgi ekranda çizilmeli ($redPixels)", redPixels > 100)
        } finally {
            c.close()
        }
    }

    @Test
    fun rotateAndDeletePages() = runBlocking {
        val mixed = TestFiles.cacheCopy("mixed_sizes.pdf")
        val rotated = out("dondurulmus.pdf")
        PdfEditor.write(app, mixed, rotated, emptyList(), PageOp.Rotate(0, 90))
        val c1 = openPdf(rotated)
        try {
            c1.render(0, 400)
            assertEquals("dikey A4 yatay olmalı", 0.707f, c1.aspect(0), 0.02f)
        } finally {
            c1.close()
        }
        val multi = TestFiles.cacheCopy("multi_page.pdf")
        val deleted = out("silinmis.pdf")
        PdfEditor.write(app, multi, deleted, emptyList(), PageOp.Delete(1))
        val c2 = openPdf(deleted)
        try {
            assertEquals(29, c2.pageCount)
        } finally {
            c2.close()
        }
    }

    @Test
    fun inkOnRotatedPageLandsWhereDrawn() = runBlocking {
        val mixed = TestFiles.cacheCopy("mixed_sizes.pdf")
        val rotated = out("dondurulmus2.pdf")
        PdfEditor.write(app, mixed, rotated, emptyList(), PageOp.Rotate(0, 90))
        val marked = out("dondurulmus_isaretli.pdf")
        // Görünen (döndürülmüş) sayfanın sol üstüne yakın kırmızı çizgi
        val stroke = InkMark(0, (0..30).map { Offset(40f + it * 4f, 60f) }, 0xFFE53935.toInt(), 14f, false)
        PdfEditor.write(app, rotated, marked, listOf(stroke), null)
        val c = openPdf(marked)
        try {
            val bmp = c.render(0, 842)!! // 1 piksel / nokta (yatay sayfa genişliği 842)
            var red = 0
            for (x in 40..160 step 2) for (y in 52..68) {
                val p = bmp.getPixel(x, y)
                if (Color.red(p) > 200 && Color.green(p) < 110) red++
            }
            assertTrue("çizgi döndürülmüş sayfada doğru yerde olmalı ($red)", red > 50)
        } finally {
            c.close()
        }
    }

    private suspend fun ready(c: XlsxController, index: Int): SheetLoad.Ready = withTimeout(30_000) {
        c.state.first { it.index == index && it is SheetLoad.Ready } as SheetLoad.Ready
    }

    @Test
    fun xlsxEditUndoAndOriginalUntouched() = runBlocking {
        val src = TestFiles.cacheCopy("formulas.xlsx")
        val before = src.readBytes()
        val c = XlsxController.open(src, false, FormatLocale.TURKISH, app.documents.editDir)
        try {
            withContext(Dispatchers.Main) { c.select(0) }
            ready(c, 0)
            val err = withContext(Dispatchers.Main) { c.applyEdit(SheetEdit.SetCell(0, 0, CellInput.Text("Düzenlendi ğüş"))) }
            assertNull(err)
            var r = ready(c, 0)
            assertEquals("Düzenlendi ğüş", r.formatter.text(r.sheet, 0, 0))
            assertTrue(c.dirty.value)
            assertNotEquals(src, c.currentFile)

            assertNull(withContext(Dispatchers.Main) { c.applyEdit(SheetEdit.InsertRows(0)) })
            r = ready(c, 0)
            assertEquals("Düzenlendi ğüş", r.formatter.text(r.sheet, 1, 0))
            assertEquals("SUM(A2:A6)", r.sheet.formulaAt(1, 1))

            withContext(Dispatchers.Main) { c.undo() }
            r = ready(c, 0)
            assertEquals("Düzenlendi ğüş", r.formatter.text(r.sheet, 0, 0))
            withContext(Dispatchers.Main) { c.undo() }
            r = ready(c, 0)
            assertEquals("1", r.formatter.text(r.sheet, 0, 0))
            assertArrayEquals("asıl dosya değişmemeli", before, src.readBytes())
        } finally {
            c.close()
        }
    }

    @Test
    fun overwriteAndNewCopyHelpers() = runBlocking {
        val entry = TestFiles.seedRecent("simple.xlsx", "Üzerine Yaz.xlsx", com.ayaz.viewer.core.FileKind.XLSX)
        val uri = android.net.Uri.parse(entry.uri)
        assertTrue("uygulama kopyası yazılabilir olmalı", app.documents.canOverwrite(uri))
        val replacement = TestFiles.cacheCopy("formulas.xlsx")
        app.documents.overwrite(uri, replacement)
        assertArrayEquals(replacement.readBytes(), File(uri.path!!).readBytes())
    }

    @Test
    fun createdDocumentsAreValid() = runBlocking {
        val pdf = out("yeni_metin.pdf")
        val body = (1..120).joinToString("\n") { "Satır $it: Türkçe ğüşıöç İ metni ve uzun bir cümle ki sayfaya sığmayıp alt satıra kaysın." }
        com.ayaz.viewer.viewer.pdf.PdfCreator.fromText(app, pdf, "Başlık ĞÜŞ", body, 12f)
        val c = openPdf(pdf)
        try {
            assertTrue("uzun metin birden çok sayfa olmalı (${c.pageCount})", c.pageCount >= 3)
        } finally {
            c.close()
        }
        PDDocument.load(pdf).use { doc ->
            val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            assertTrue("metin aranabilir olmalı", text.contains("Başlık ĞÜŞ") && text.contains("Satır 120"))
        }
        val blank = out("bos.pdf")
        com.ayaz.viewer.viewer.pdf.PdfCreator.blank(blank, 3)
        val c2 = openPdf(blank)
        try { assertEquals(3, c2.pageCount) } finally { c2.close() }

        val xlsx = out("yeni.xlsx")
        com.ayaz.viewer.xlsx.XlsxTemplates.blankWorkbook(xlsx)
        val x = XlsxController.open(xlsx, false, FormatLocale.TURKISH, app.documents.editDir)
        try {
            withContext(Dispatchers.Main) { x.select(0) }
            assertTrue(ready(x, 0).sheet.isEmpty)
            assertNull(withContext(Dispatchers.Main) { x.applyEdit(SheetEdit.SetCell(0, 0, CellInput.Text("İlk hücre"))) })
            val r = ready(x, 0)
            assertEquals("İlk hücre", r.formatter.text(r.sheet, 0, 0))
        } finally {
            x.close()
        }
    }
}
