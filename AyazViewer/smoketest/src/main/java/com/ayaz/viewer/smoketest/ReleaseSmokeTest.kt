package com.ayaz.viewer.smoketest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Yayın sürümüyle aynı kodu (R8 ile küçültülmüş "staging") gerçek bir kullanıcı gibi kullanır.
 *
 * Uygulamanın iç sınıflarına erişmez; yalnızca ekrandakileri görür ve dokunur. Böylece küçültme
 * (R8) sonrası ortaya çıkabilecek çökmeleri yakalar. Adımlar sırasında Play Store için ekran
 * görüntüleri /data/local/tmp/ayaz-shots klasörüne kaydedilir.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val context: Context = instrumentation.context

    @Before
    fun setUp() {
        shell("pm clear $PKG")
        shell("logcat -b crash -c")
        shell("rm -rf $SHOTS")
        shell("mkdir -p $SHOTS")
        enterDemoMode()
        device.pressHome()
    }

    @After
    fun tearDown() {
        shell("am broadcast -a com.android.systemui.demo -e command exit")
    }

    @Test
    fun releaseBuildEndToEnd() {
        // 1) Başka bir uygulamadan (WhatsApp gibi) geçici izinle "Birlikte aç": PDF
        openWith("Proje Raporu.pdf", "application/pdf")
        waitFor(By.text("30 sayfa"), "PDF açılmadı")
        settle(2000)
        screenshot("2_pdf")
        device.pressBack()

        // 2) "Birlikte aç": Excel (çok sayfalı, biçimli)
        openWith("Bütçe 2026.xlsx", XLSX)
        waitFor(By.desc("Tablo: Özet"), "Excel tablosu açılmadı")
        waitFor(By.text("Veriler"), "Sayfa sekmeleri görünmedi")
        settle(1500)
        screenshot("3_excel")
        device.pressBack()

        // 3) Ana sayfa: iki dosya da "Son dosyalar"da
        launchApp()
        waitFor(By.text("Dosya Aç"), "Ana sayfa açılmadı")
        findScrolling(By.text("Proje Raporu.pdf"))
        findScrolling(By.text("Bütçe 2026.xlsx"))
        scrollToTop()
        settle(800)
        screenshot("1_ana_sayfa")

        // 4) Paylaşım penceresinde yalnızca WhatsApp ve Telegram
        findScrolling(By.desc("Seçenekler: Proje Raporu.pdf")).click()
        waitFor(By.text("Paylaş"), "Dosya menüsü açılmadı").click()
        waitFor(By.text("WhatsApp"), "Paylaşım penceresinde WhatsApp yok")
        waitFor(By.text("Telegram"), "Paylaşım penceresinde Telegram yok")
        settle(800)
        screenshot("5_paylas")
        device.pressBack()
        device.wait(Until.gone(By.text("Telegram")), 5_000)

        // 5) Oluştur → metinden PDF (PDFBox ve gömülü yazı tipi küçültülmüş sürümde çalışmalı)
        scrollToTop()
        waitFor(By.desc("Oluştur"), "Oluştur kartı yok").click()
        waitFor(By.desc("Yeni PDF (metin yaz)"), "Oluştur penceresi açılmadı")
        settle(800)
        screenshot("4_olustur")
        device.findObject(By.desc("Yeni PDF (metin yaz)")).click()
        typeInto(By.desc("Başlık"), "Toplantı Notları")
        typeInto(
            By.desc("Metin"),
            "Gündem: bütçe, takvim ve görev dağılımı.\nKatılımcılar: Ayşe, Çağrı, Gökhan, İpek, Şule, Ömer.\n" +
                "Kararlar: proje raporu cuma gününe kadar paylaşılacak.",
        )
        waitFor(By.text("PDF oluştur").enabled(true), "Başlık yazılamadı (PDF oluştur düğmesi etkinleşmedi)").click()
        waitFor(By.text("Toplantı Notları.pdf"), "Oluşturulan PDF açılmadı")
        waitFor(By.text("1 sayfa"), "Oluşturulan PDF'in sayfa sayısı görünmedi")

        // 6) PDF düzenleme: kalemle çiz, vurgula, kaydet (asıl dosyanın üzerine)
        waitFor(By.desc("Araç: Kalem"), "Düzenleme araçları görünmedi").click()
        val page = pageArea()
        drawWave(page, 0.30f)
        waitFor(By.desc("Araç: Vurgulayıcı"), "Vurgulayıcı yok").click()
        drawLine(page, 0.42f)
        settle(800)
        screenshot("6_pdf_duzenleme")
        save("PDF")
        device.pressBack()

        // 7) Oluştur → yeni Excel tablosu, bir hücreye yaz ve kaydet
        waitFor(By.text("Dosya Aç"), "Ana sayfaya dönülmedi")
        scrollToTop()
        waitFor(By.desc("Oluştur"), "Oluştur kartı yok").click()
        waitFor(By.desc("Yeni Excel tablosu"), "Oluştur penceresi açılmadı").click()
        val grid = waitFor(By.desc("Tablo: Sayfa1"), "Yeni tablo açılmadı")
        tapFirstCell(grid)
        waitFor(By.desc("Düzenlenen hücre A1"), "A1 hücresi seçilemedi")
        typeInto(By.desc("Hücre değeri"), "Market alışverişi")
        waitFor(By.desc("Değeri uygula"), "Uygula düğmesi yok").click()
        waitFor(By.desc("Düzenlenen hücre A2"), "Değer uygulanmadı")
        save("Excel")
        device.pressBack()

        // 8) Ayarlar ve açık kaynak lisansları
        waitFor(By.text("Ayarlar"), "Alt menü yok").click()
        findScrolling(By.text("Açık kaynak lisansları")).click()
        waitFor(By.textContains("PdfBox-Android"), "Lisans metni açılmadı")
        waitFor(By.text("Kapat"), "Kapat düğmesi yok").click()

        assertNoCrash()
    }

    // ------------------------------------------------------------------ yardımcılar

    private fun shell(cmd: String): String = device.executeShellCommand(cmd)

    private fun enterDemoMode() {
        shell("settings put global sysui_demo_allowed 1")
        val demo = "am broadcast -a com.android.systemui.demo -e command"
        shell("$demo enter")
        shell("$demo clock -e hhmm 0941")
        shell("$demo battery -e level 100 -e plugged false")
        shell("$demo network -e wifi show -e level 4")
        shell("$demo network -e mobile show -e datatype none -e level 4")
        shell("$demo notifications -e visible false")
    }

    private fun launchApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(PKG)
            ?: error("Uygulama yüklü değil: $PKG")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(PKG).depth(0)), TIMEOUT)
    }

    /** Dosyayı bu test uygulamasının FileProvider'ı üzerinden, yalnızca geçici okuma izniyle gönderir. */
    private fun openWith(assetName: String, mime: String) {
        val dir = File(context.cacheDir, "samples").apply { mkdirs() }
        val file = File(dir, assetName)
        context.assets.open(assetName).use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(context, "com.ayaz.viewer.smoketest.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .setPackage(PKG)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(PKG).depth(0)), TIMEOUT)
    }

    private fun waitFor(selector: BySelector, message: String, timeout: Long = TIMEOUT): UiObject2 {
        val found = device.wait(Until.findObject(selector), timeout)
        if (found == null) {
            assertNoCrash()
            screenshot("hata_" + SystemClock.uptimeMillis())
            fail("$message ($selector)\nEkrandakiler:\n${screenSummary()}")
        }
        return found!!
    }

    /** Hata ayıklama: ekrandaki metinler ve açıklamalar (hata mesajına eklenir). */
    private fun screenSummary(): String = try {
        val any = Pattern.compile(".+", Pattern.DOTALL)
        val texts = device.findObjects(By.text(any)).map { "text: ${it.text.take(80)}" }
        val descs = device.findObjects(By.desc(any)).map { "desc: ${it.contentDescription.take(80)}" }
        (texts + descs).distinct().take(60).joinToString("\n").ifEmpty { "(erişilebilirlik ağacında metin yok)" }
    } catch (e: Exception) {
        "(ekran okunamadı: $e)"
    }

    /** Kaydet → "Asıl dosyanın üzerine yaz"; kayıt bitince Kaydet düğmesi yeniden pasifleşir. */
    private fun save(what: String) {
        waitFor(By.desc("Kaydet").enabled(true), "$what: Kaydet düğmesi etkin değil (değişiklik algılanmadı)").click()
        waitFor(By.text("Asıl dosyanın üzerine yaz"), "$what: kaydetme seçenekleri çıkmadı").click()
        // Uygulama sonucu kısa bir bildirimle (Toast) söyler: "Kaydedildi…" veya "Kaydedilemedi: …"
        val toast = device.wait(Until.findObject(By.textStartsWith("Kaydedil")), 8_000)?.text
        val ok = device.wait(Until.findObject(By.desc("Kaydet").enabled(false)), TIMEOUT)
        if (ok == null) {
            screenshot("hata_kaydet_" + SystemClock.uptimeMillis())
            fail("$what kaydedilemedi | bildirim: ${toast ?: "(görülmedi)"} | ekran: ${screenSummary().replace("\n", " ; ")}")
        }
        assertNoCrash()
    }

    /** Kaydırılabilir listede aşağı doğru arar. */
    private fun findScrolling(selector: BySelector): UiObject2 {
        device.wait(Until.findObject(selector), 3_000)?.let { return it }
        val list = device.findObject(By.scrollable(true).pkg(PKG))
        val found = list?.scrollUntil(Direction.DOWN, Until.findObject(selector)) ?: device.findObject(selector)
        if (found == null) {
            screenshot("hata_" + SystemClock.uptimeMillis())
            fail("Bulunamadı: $selector")
        }
        return found!!
    }

    private fun scrollToTop() {
        device.findObject(By.scrollable(true).pkg(PKG))?.scroll(Direction.UP, 3f)
    }

    /**
     * Metin alanına panodan yapıştırarak yazar (Türkçe karakterler korunur). Compose metin alanları
     * erişilebilirlik "metni ayarla" eylemini UiAutomator'dan güvenilir biçimde almadığı için
     * yapıştırma kullanılır; sonucu çağıran taraf ekrandaki etkisiyle (ör. düğmenin etkinleşmesi) doğrular.
     */
    private fun typeInto(selector: BySelector, text: String) {
        waitFor(selector, "Metin alanı yok").click()
        device.waitForIdle()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        instrumentation.runOnMainSync { clipboard.setPrimaryClip(ClipData.newPlainText("smoke", text)) }
        device.pressKeyCode(KeyEvent.KEYCODE_PASTE)
        device.waitForIdle()
        SystemClock.sleep(400)
    }

    private fun settle(ms: Long) {
        device.waitForIdle()
        SystemClock.sleep(ms)
    }

    /** PDF sayfasının ekrandaki alanı: üst çubuk ile alttaki araç çubuğu arası. */
    private fun pageArea(): IntArray {
        val w = device.displayWidth
        val h = device.displayHeight
        return intArrayOf((w * 0.12f).toInt(), (h * 0.18f).toInt(), (w * 0.88f).toInt(), (h * 0.62f).toInt())
    }

    private fun drawWave(area: IntArray, yRatio: Float) {
        val y = area[1] + ((area[3] - area[1]) * yRatio).toInt()
        val points = (0..12).map { i ->
            val x = area[0] + (area[2] - area[0]) * i / 12
            Point(x, y + if (i % 2 == 0) -18 else 18)
        }
        device.swipe(points.toTypedArray(), 6)
        device.waitForIdle()
    }

    private fun drawLine(area: IntArray, yRatio: Float) {
        val y = area[1] + ((area[3] - area[1]) * yRatio).toInt()
        device.swipe(area[0], y, area[2], y, 40)
        device.waitForIdle()
    }

    private fun tapFirstCell(grid: UiObject2) {
        val b = grid.visibleBounds
        val dp = context.resources.displayMetrics.density
        // Satır başlığı (en fazla ~36dp) ve sütun başlığı (24dp) sonrasındaki ilk hücre (A1).
        device.click(b.left + (58 * dp).toInt(), b.top + (34 * dp).toInt())
        device.waitForIdle()
    }

    private fun assertNoCrash() {
        val crashes = shell("logcat -b crash -d")
        assertFalse("Uygulama çöktü:\n$crashes", crashes.contains("Process: $PKG"))
        val anr = device.findObject(By.textContains("yanıt vermiyor")) ?: device.findObject(By.textContains("isn't responding"))
        assertFalse("Uygulama yanıt vermiyor (ANR)", anr != null)
    }

    private fun screenshot(name: String) {
        shell("screencap -p $SHOTS/$name.png")
    }

    private companion object {
        const val PKG = "com.ayaz.viewer"
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val SHOTS = "/data/local/tmp/ayaz-shots"
        const val TIMEOUT = 20_000L
    }
}
