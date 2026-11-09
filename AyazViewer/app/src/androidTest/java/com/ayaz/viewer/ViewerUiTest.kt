package com.ayaz.viewer

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.viewer.ViewerActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ViewerUiTest {

    /** Takılan bir test, adıyla birlikte başarısız olsun (sonsuza dek beklemesin). */
    @get:org.junit.Rule(order = 0)
    val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(120)

    @get:Rule(order = 1)
    val rule = createEmptyComposeRule()

    private fun waitDesc(desc: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithContentDescription(desc).fetchSemanticsNodes().isNotEmpty() }

    private fun waitText(text: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** Ekran yönü gerçekten değişip etkinlik yeniden oluşturulana kadar bekler. */
    private fun awaitOrientation(scenario: ActivityScenario<ViewerActivity>, orientation: Int) {
        rule.waitUntil(30_000) {
            var current = -1
            scenario.onActivity { current = it.resources.configuration.orientation }
            current == orientation
        }
    }

    @Test
    fun pdfOpensRotatesAndShares() {
        val entry = TestFiles.seedRecent("multi_page.pdf", "Çok Sayfalı.pdf", FileKind.PDF)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use { scenario ->
            waitDesc("Sayfa 1 / 30")
            waitText("30 sayfa")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            awaitOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)
            waitText("30 sayfa")
            waitDesc("Sayfa 1 / 30")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            // Dönüş tamamlanmadan tıklanırsa tıklama yeniden oluşturulan ekrana denk gelebilir.
            awaitOrientation(scenario, Configuration.ORIENTATION_PORTRAIT)
            waitDesc("Sayfa 1 / 30")
            rule.onNodeWithContentDescription("Paylaş").performClick()
            waitText("WhatsApp")
            rule.onNodeWithText("Telegram").assertExists()
        }
    }

    /** [r],[g],[b] rengine yakın piksel sayısı (ekran görüntüsü üzerinde). */
    private fun countColor(desc: String, r: Int, g: Int, b: Int, tol: Int = 18): Int {
        val map = rule.onNodeWithContentDescription(desc).captureToImage().toPixelMap()
        var n = 0
        for (y in 0 until map.height step 2) for (x in 0 until map.width step 2) {
            val c = map[x, y]
            if (kotlin.math.abs((c.red * 255).toInt() - r) <= tol &&
                kotlin.math.abs((c.green * 255).toInt() - g) <= tol &&
                kotlin.math.abs((c.blue * 255).toInt() - b) <= tol
            ) n++
        }
        return n
    }

    @Test
    fun pdfPageIsActuallyDrawn() {
        val entry = TestFiles.seedRecent("single_page.pdf", "Çizim.pdf", FileKind.PDF)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Sayfa 1 / 1")
            // Sayfadaki mavi kare (#4F7CFF) ekranda görünmeli.
            rule.waitUntil(20_000) { countColor("Sayfa 1", 0x4F, 0x7C, 0xFF) > 500 }
        }
    }

    @Test
    fun xlsxGridDrawsCellFills() {
        val entry = TestFiles.seedRecent("simple.xlsx", "Dolgu.xlsx", FileKind.XLSX)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Tablo: Satışlar")
            Thread.sleep(500)
            // Başlık satırının dolgu rengi (#1F4E79) tabloda çizilmiş olmalı.
            val n = countColor("Tablo: Satışlar", 0x1F, 0x4E, 0x79, tol = 10)
            assertTrue("başlık dolgusu bulunamadı ($n)", n > 300)
        }
    }

    @Test
    fun pdfGoToPageAndRememberLastPage() {
        val entry = TestFiles.seedRecent("multi_page.pdf", "Hatırla.pdf", FileKind.PDF)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Sayfa 1 / 30")
            rule.onNodeWithContentDescription("Diğer").performClick()
            rule.onNodeWithText("Sayfaya git").performClick()
            rule.onNode(hasSetTextAction()).performTextReplacement("12")
            rule.onNodeWithText("Git").performClick()
            waitDesc("Sayfa 12 / 30")
        }
        // Görüntüleyici kapanınca sayfa kaydedilir (diske yazma eşzamansızdır).
        var saved = 0
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            saved = runBlocking { TestFiles.app.recents.get(entry.id)?.lastPage ?: -1 }
            if (saved == 11) break
            Thread.sleep(100)
        }
        assertTrue("kaydedilen sayfa: $saved", saved == 11)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Sayfa 12 / 30")
        }
    }

    @Test
    fun xlsxOpensSearchesAndRotates() {
        val entry = TestFiles.seedRecent("simple.xlsx", "Satış Tablosu.xlsx", FileKind.XLSX)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use { scenario ->
            waitDesc("Tablo: Satışlar")
            rule.onNodeWithContentDescription("Hücrelerde ara").performClick()
            rule.onNode(hasSetTextAction()).performTextInput("Çay")
            rule.onNode(hasSetTextAction()).performImeAction()
            waitDesc("Seçili hücre A2")
            // Her dönüşün bitmesi beklenir; aksi halde yeniden oluşturma sürerken ikinci dönüş veya ekranın
            // kapatılması (ActivityScenario.close) takılabiliyordu.
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            awaitOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE)
            waitDesc("Tablo: Satışlar")
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            awaitOrientation(scenario, Configuration.ORIENTATION_PORTRAIT)
            waitDesc("Tablo: Satışlar")
        }
    }

    @Test
    fun xlsxMultipleSheetTabs() {
        val entry = TestFiles.seedRecent("multi_sheet.xlsx", "Çok Sayfa.xlsx", FileKind.XLSX)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Tablo: Özet")
            rule.onNodeWithText("Veriler").performClick()
            waitDesc("Tablo: Veriler")
            rule.onNodeWithText("Boş Sayfa").performClick()
            waitText("Bu sayfa boş")
            rule.onNodeWithText("Gizli (gizli)").assertExists()
        }
    }

    @Test
    fun openWithFromAnotherAppAndCorruptFile() {
        val app = TestFiles.app
        val dir = File(app.cacheDir, "share/test-view").apply { mkdirs() }
        val good = TestFiles.asset("single_page.pdf", File(dir, "Dışarıdan Gelen.pdf"))
        val view = Intent(Intent.ACTION_VIEW).apply {
            setClass(app, ViewerActivity::class.java)
            setDataAndType(app.documents.contentUriFor(good), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ActivityScenario.launch<ViewerActivity>(view).use {
            waitDesc("Sayfa 1 / 1")
        }
        assertTrue(app.recents.items.value.any { it.name == "Dışarıdan Gelen.pdf" && it.isPrivateCopy })

        val bad = TestFiles.asset("corrupt.pdf", File(dir, "bozuk.pdf"))
        val badIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(app, ViewerActivity::class.java)
            setDataAndType(app.documents.contentUriFor(bad), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ActivityScenario.launch<ViewerActivity>(badIntent).use {
            waitText("Dosya açılamadı")
            rule.onNodeWithText("Kapat").assertExists()
        }

        val legacy = TestFiles.asset("legacy.xls", File(dir, "eski.xls"))
        val send = Intent(Intent.ACTION_SEND).apply {
            setClass(app, ViewerActivity::class.java)
            type = "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, app.documents.contentUriFor(legacy))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ActivityScenario.launch<ViewerActivity>(send).use {
            waitText("Dosya açılamadı")
        }
    }

    @Test
    fun xlsxEditCellAndOverwriteOriginal() {
        val entry = TestFiles.seedRecent("simple.xlsx", "Düzenle Kaydet.xlsx", FileKind.XLSX)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Tablo: Satışlar")
            // A2'yi aramayla seç
            rule.onNodeWithContentDescription("Hücrelerde ara").performClick()
            rule.onNode(hasSetTextAction()).performTextInput("Çay")
            rule.onNode(hasSetTextAction()).performImeAction()
            waitDesc("Seçili hücre A2")
            rule.onNodeWithContentDescription("Aramayı kapat").performClick()
            rule.onNodeWithContentDescription("Düzenle").performClick()
            waitDesc("Düzenlenen hücre A2")
            rule.onNodeWithContentDescription("Hücre değeri").performTextReplacement("Kahve ğüş")
            rule.onNodeWithContentDescription("Değeri uygula").performClick()
            waitDesc("Düzenlenen hücre A3") // uygulama sonrası alttaki hücreye geçer
            rule.onNodeWithContentDescription("Kaydet").performClick()
            waitText("Asıl dosyanın üzerine yaz")
            rule.onNodeWithText("Asıl dosyanın üzerine yaz").performClick()
            // Dosyanın kendisinde yeni değer var mı? (ekran açıkken bekle; kapanırsa kayıt iptal olabilir)
            val file = File(android.net.Uri.parse(entry.uri).path!!)
            val deadline = System.currentTimeMillis() + 15_000
            var value = ""
            while (System.currentTimeMillis() < deadline) {
                value = try {
                    com.ayaz.viewer.xlsx.XlsxReader(file, { android.util.Xml.newPullParser() }).use { r ->
                        val wb = r.readWorkbook()
                        com.ayaz.viewer.xlsx.CellFormatter(wb).text(r.readSheet(wb, 0), 1, 0)
                    }
                } catch (_: Exception) { "" }
                if (value == "Kahve ğüş") break
                Thread.sleep(200)
            }
            assertTrue("kaydedilen değer: $value", value == "Kahve ğüş")
        }
    }

    @Test
    fun xlsxUnsavedChangesWarning() {
        val entry = TestFiles.seedRecent("simple.xlsx", "Uyarı.xlsx", FileKind.XLSX)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Tablo: Satışlar")
            rule.onNodeWithContentDescription("Düzenle").performClick()
            rule.onNodeWithContentDescription("Satır ve sütun işlemleri").assertExists()
            // hücre seçmeden önce: dokunma ile B2 seçilir
            rule.onNodeWithContentDescription("Tablo: Satışlar").performTouchInput { click(Offset(width * 0.35f, height * 0.06f)) }
            rule.waitUntil(10_000) {
                rule.onAllNodesWithContentDescription("Düzenlenen hücre yok").fetchSemanticsNodes().isEmpty()
            }
            rule.onNodeWithContentDescription("Satır ve sütun işlemleri").performClick()
            rule.onNodeWithText("Üstüne satır ekle").performClick()
            rule.waitUntil(15_000) {
                try {
                    rule.onNodeWithContentDescription("Geri al").assertIsEnabled(); true
                } catch (_: AssertionError) {
                    false
                }
            }
            rule.onNodeWithContentDescription("Geri").performClick()
            waitText("Kaydedilmemiş değişiklikler")
            rule.onNodeWithText("Kaydetmeden çık").performClick()
        }
    }

    @Test
    fun pdfDrawAndSave() {
        val entry = TestFiles.seedRecent("single_page.pdf", "Çizim Kaydet.pdf", FileKind.PDF)
        ActivityScenario.launch<ViewerActivity>(ViewerActivity.intentFor(TestFiles.app, entry.id)).use {
            waitDesc("Sayfa 1 / 1")
            rule.onNodeWithContentDescription("Düzenle").performClick()
            rule.onNodeWithContentDescription("Araç: Kalem").assertExists()
            rule.onNodeWithContentDescription("Renk 2").performClick()
            rule.onNodeWithText("Kalın").performClick()
            rule.onNodeWithContentDescription("Sayfa 1").performTouchInput {
                swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.5f), durationMillis = 400)
            }
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Kaydet").performClick()
            waitText("Asıl dosyanın üzerine yaz")
            rule.onNodeWithText("Asıl dosyanın üzerine yaz").performClick()
            val file = File(android.net.Uri.parse(entry.uri).path!!)
            val deadline = System.currentTimeMillis() + 20_000
            var red = 0
            while (System.currentTimeMillis() < deadline) {
                red = try {
                    redPixelsOnFirstPage(file) // kaynak PDF'te kırmızı yok; yalnızca kayıttan sonra görünür
                } catch (_: Exception) { 0 }
                if (red > 30) break
                Thread.sleep(300)
            }
            assertTrue("kaydedilen PDF'te kırmızı çizim görünmeli ($red)", red > 30)
            waitDesc("Sayfa 1 / 1", 30_000)
            // Kayıttan sonra belge yeniden açılır: bekleyen işaretlemeler temizlenir ve sayfa yeni dosyadan
            // çizilmelidir. (Hata: eski sayfa görüntüsü ekranda kalıyor, kaydedilen çizim görünmüyordu.)
            rule.waitUntil(20_000) {
                rule.onAllNodesWithContentDescription("Kaydet").fetchSemanticsNodes()
                    .none { SemanticsProperties.Disabled !in it.config }
            }
            var onScreen = 0
            val screenDeadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < screenDeadline) {
                onScreen = try { redPixelsOnScreen("Sayfa 1") } catch (_: Throwable) { 0 }
                if (onScreen > 30) break
                Thread.sleep(300)
            }
            assertTrue("kaydettikten sonra ekrandaki sayfada çizim görünmeli ($onScreen)", onScreen > 30)
        }
    }

    private fun redPixelsOnScreen(desc: String): Int {
        val map = rule.onNodeWithContentDescription(desc).captureToImage().toPixelMap()
        var n = 0
        for (y in 0 until map.height step 2) for (x in 0 until map.width step 2) {
            val c = map[x, y]
            if (c.red > 0.78f && c.green < 0.43f && c.blue < 0.43f) n++
        }
        return n
    }

    private fun redPixelsOnFirstPage(f: File): Int {
        val pfd = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        android.graphics.pdf.PdfRenderer(pfd).use { r ->
            r.openPage(0).use { p ->
                val bmp = android.graphics.Bitmap.createBitmap(p.width, p.height, android.graphics.Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                p.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                var n = 0
                for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
                    val c = bmp.getPixel(x, y)
                    if (android.graphics.Color.red(c) > 200 && android.graphics.Color.green(c) < 110 && android.graphics.Color.blue(c) < 110) n++
                }
                return n
            }
        }
    }
}
