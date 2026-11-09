package com.ayaz.viewer

import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayaz.viewer.data.UserFacingException
import com.ayaz.viewer.viewer.pdf.PdfController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfControllerTest {

    /** Takılan bir test, adıyla birlikte başarısız olsun (sonsuza dek beklemesin). */
    @get:org.junit.Rule(order = 0)
    val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(120)

    private fun open(file: File): PdfController = PdfController.open(
        context = TestFiles.app,
        openFd = { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) },
        fallbackCopy = { file },
        sizeHint = file.length(),
    )

    private fun expectError(asset: String, contains: String) {
        val f = TestFiles.cacheCopy(asset)
        try {
            open(f).close()
            fail("$asset için hata bekleniyordu")
        } catch (e: UserFacingException) {
            assertTrue("mesaj: ${e.message}", e.message!!.contains(contains, ignoreCase = true))
        }
    }

    @Test
    fun singlePageRendersContent() = runBlocking {
        val c = open(TestFiles.cacheCopy("single_page.pdf"))
        try {
            assertEquals(1, c.pageCount)
            val bmp = c.render(0, 1080)!!
            assertEquals(1080, bmp.width)
            assertEquals(1527, bmp.height, 2)
            // Mavi kare: PDF'te (60,400)-(260,600) nokta, alt-sol orijinli.
            val s = 1080f / 595.27f
            val px = bmp.getPixel((160 * s).toInt(), ((841.89f - 500) * s).toInt())
            assertTrue("mavi bekleniyordu: ${Integer.toHexString(px)}", Color.blue(px) > 200 && Color.red(px) < 140)
            val corner = bmp.getPixel(5, 5)
            assertEquals(Color.WHITE, corner)
        } finally {
            c.close()
        }
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("beklenen $expected±$tolerance, gelen $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }

    @Test
    fun multiPageDocument() = runBlocking {
        val c = open(TestFiles.cacheCopy("multi_page.pdf"))
        try {
            assertEquals(30, c.pageCount)
            assertNotNull(c.render(0, 720))
            assertNotNull(c.render(29, 720))
            assertEquals(1.414f, c.aspect(15), 0.01f)
        } finally {
            c.close()
        }
    }

    @Test
    fun largeDocumentIsFastAndBounded() = runBlocking {
        val f = TestFiles.cacheCopy("large.pdf")
        val t0 = System.nanoTime()
        val c = open(f)
        val openMs = (System.nanoTime() - t0) / 1_000_000
        try {
            assertEquals(400, c.pageCount)
            val times = ArrayList<Long>()
            // 60 farklı sayfa çiz: önbellek sınırlı olduğundan bellek taşmamalı.
            for (p in 0 until 400 step 7) {
                val s = System.nanoTime()
                assertNotNull("sayfa $p", c.render(p, 1080))
                times.add((System.nanoTime() - s) / 1_000_000)
            }
            println("Büyük PDF: açılış ${openMs} ms, sayfa çizimi ort. ${times.average().toInt()} ms, en fazla ${times.max()} ms")
            assertTrue("açılış yavaş: $openMs", openMs < 5000)
            assertTrue("çizim yavaş: ${times.max()}", times.max() < 5000)
            val rt = Runtime.getRuntime()
            assertTrue("Java yığını aşırı dolu", rt.totalMemory() - rt.freeMemory() < rt.maxMemory() * 0.8)
        } finally {
            c.close()
        }
    }

    @Test
    fun mixedPageSizes() = runBlocking {
        val c = open(TestFiles.cacheCopy("mixed_sizes.pdf"))
        try {
            assertEquals(3, c.pageCount)
            c.render(1, 600)
            assertEquals(1.414f, c.aspect(0), 0.01f)
            assertEquals(0.707f, c.aspect(1), 0.01f)
        } finally {
            c.close()
        }
    }

    @Test
    fun encryptedPdfShowsTurkishMessage() = expectError("encrypted.pdf", "parola")

    @Test
    fun corruptPdfDoesNotCrash() = expectError("corrupt.pdf", "bozuk")

    @Test
    fun emptyPdfDoesNotCrash() = expectError("empty.pdf", "boş")

    @Test
    fun turkishFileName() = runBlocking {
        val f = TestFiles.cacheCopy("single_page.pdf", "Türkçe_Dosya_ğüşiöç.pdf")
        val c = open(f)
        try {
            assertEquals(1, c.pageCount)
            assertNotNull(c.render(0, 500))
        } finally {
            c.close()
        }
    }

    @Test
    fun textSearchWhenSupported() = runBlocking {
        assumeTrue("Bu cihazda PdfRenderer metin araması yok (Android 15+ gerekir)", PdfController.searchAvailable())
        val c = open(TestFiles.cacheCopy("multi_page.pdf"))
        try {
            val hits = kotlinx.coroutines.withTimeout(60_000) { c.search("anahtar") {} }
            assertEquals(setOf(6, 22), hits.map { it.page }.toSet())
            assertTrue(hits.all { it.rects.isNotEmpty() })
            assertTrue(kotlinx.coroutines.withTimeout(60_000) { c.search("bulunmayan-kelime-xyz") {} }.isEmpty())
        } finally {
            c.close()
        }
    }
}
