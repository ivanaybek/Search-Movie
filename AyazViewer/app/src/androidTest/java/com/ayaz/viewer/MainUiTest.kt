package com.ayaz.viewer

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.data.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainUiTest {

    /** Takılan bir test, adıyla birlikte başarısız olsun (sonsuza dek beklemesin). */
    @get:org.junit.Rule(order = 0)
    val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(120)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        TestFiles.app.settings.update { it.copy(themeMode = ThemeMode.DARK) }
    }

    @After
    fun tearDown() {
        TestFiles.app.settings.update { it.copy(themeMode = ThemeMode.DARK) }
    }

    private fun waitForText(text: String, timeout: Long = 10_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun backgroundLuminance(): Float {
        val map = rule.onRoot().captureToImage().toPixelMap()
        // Sol kenardaki boşluk (liste iç boşluğu) her zaman zemin rengidir.
        val c = map[4, map.height / 2]
        return (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue)
    }

    @Test
    fun homeShowsOpenButtonAndNavigation() {
        rule.onNodeWithContentDescription("Dosya Aç").assertIsDisplayed()
        rule.onNodeWithText("Ana Sayfa").assertIsDisplayed()
        rule.onNodeWithText("Son Açılanlar").assertIsDisplayed()
        rule.onNodeWithText("Dosyalar").assertIsDisplayed()
        rule.onNodeWithText("Ayarlar").assertIsDisplayed()
        // Koyu tema varsayılan ve zemin gerçek siyaha yakın
        assertTrue("koyu zemin bekleniyordu", backgroundLuminance() < 0.08f)
    }

    @Test
    fun tabsNavigate() {
        rule.onNodeWithText("Dosyalar").performClick()
        waitForText("Hızlı erişim")
        waitForText("Klasör ekle")
        rule.onNodeWithText("Klasör ekle").assertExists()
        rule.onNodeWithText("Ayarlar").performClick()
        waitForText("Tema")
    }

    @Test
    fun lightAndDarkTheme() {
        rule.onNodeWithText("Ayarlar").performClick()
        waitForText("Açık")
        rule.onNodeWithText("Açık").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, TestFiles.app.settings.settings.value.themeMode)
        assertTrue("açık zemin bekleniyordu", backgroundLuminance() > 0.85f)
        rule.onNodeWithText("Koyu (varsayılan)").performClick()
        rule.waitForIdle()
        assertTrue("koyu zemin bekleniyordu", backgroundLuminance() < 0.08f)
    }

    @Test
    fun recentFilesListAndShareSheet() {
        TestFiles.clearRecents()
        TestFiles.seedRecent("multi_page.pdf", "Fatura Şubat.pdf", FileKind.PDF)
        TestFiles.seedRecent("simple.xlsx", "Bütçe 2024.xlsx", FileKind.XLSX)
        rule.onNodeWithText("Son Açılanlar").performClick()
        waitForText("Bütçe 2024.xlsx")
        rule.onNodeWithText("Fatura Şubat.pdf").assertIsDisplayed()

        rule.onNodeWithContentDescription("Seçenekler: Bütçe 2024.xlsx").performClick()
        rule.onNodeWithText("Paylaş").performClick()
        waitForText("WhatsApp")
        rule.onNodeWithText("Telegram").assertIsDisplayed()
        // Test emülatöründe uygulamalar yüklü olmadığından Türkçe uyarı gösterilmeli.
        rule.onNodeWithText("WhatsApp").performClick()
        waitForText("WhatsApp bu cihazda yüklü değil. Paylaşmak için önce WhatsApp uygulamasını yükleyin.")
    }

    @Test
    fun recentsSurviveActivityRecreation() {
        TestFiles.clearRecents()
        TestFiles.seedRecent("single_page.pdf", "Kalıcı Liste.pdf", FileKind.PDF)
        rule.onNodeWithText("Son Açılanlar").performClick()
        waitForText("Kalıcı Liste.pdf")
        rule.activityRule.scenario.recreate()
        // Sekme seçimi korunur; liste yeniden okunur.
        waitForText("Kalıcı Liste.pdf")
    }
}
