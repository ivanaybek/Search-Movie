package com.ayaz.viewer

import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.core.FileKinds
import com.ayaz.viewer.core.Formatters
import com.ayaz.viewer.data.DocumentAccess
import com.ayaz.viewer.data.FileOpener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AppLogicTest {

    @Test
    fun detectsKindFromContent() {
        val pdf = "%PDF-1.7\n".toByteArray()
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0)
        val ole = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0)
        assertEquals(FileKind.PDF, FileKinds.detect("a.xlsx", null, pdf))
        assertEquals(FileKind.XLSX, FileKinds.detect("rapor.pdf", "application/pdf", zip))
        assertEquals(FileKind.XLSX, FileKinds.detect("eski.xls", null, ole))
        assertEquals(FileKind.PDF, FileKinds.detect("bos.pdf", null, ByteArray(0)))
        assertEquals(FileKind.XLSX, FileKinds.detect(null, FileKinds.MIME_XLSX, ByteArray(0)))
        assertEquals(FileKind.UNKNOWN, FileKinds.detect("resim.png", "image/png", byteArrayOf(1, 2, 3, 4)))
        // %PDF başlığı ilk 1024 bayt içinde herhangi bir yerde olabilir
        assertEquals(FileKind.PDF, FileKinds.detect(null, null, ByteArray(100) + pdf))
    }

    @Test
    fun sanitizesNamesKeepingTurkish() {
        assertEquals("Fatura_Şubat_ğüş.pdf", DocumentAccess.sanitizeName("Fatura/Şubat:ğüş.pdf"))
        assertEquals("belge", DocumentAccess.sanitizeName("   "))
        val long = "a".repeat(300) + ".xlsx"
        val s = DocumentAccess.sanitizeName(long)
        assertEquals(120, s.length)
        assertEquals("xlsx", s.substringAfterLast('.'))
    }

    @Test
    fun ensuresExtension() {
        assertEquals("rapor.pdf", FileOpener.ensureExtension("rapor.pdf", FileKind.PDF))
        assertEquals("rapor.pdf", FileOpener.ensureExtension("rapor", FileKind.PDF))
        assertEquals("tablo.xlsx", FileOpener.ensureExtension("tablo", FileKind.XLSX))
        assertEquals("tablo.XLSX", FileOpener.ensureExtension("tablo.XLSX", FileKind.XLSX))
    }

    @Test
    fun stableIdsAreDeterministic() {
        assertEquals(DocumentAccess.stableId("content://a/1"), DocumentAccess.stableId("content://a/1"))
        assertNotEquals(DocumentAccess.stableId("content://a/1"), DocumentAccess.stableId("content://a/2"))
        assertEquals(24, DocumentAccess.stableId("x").length)
    }

    @Test
    fun formatsSizesInTurkish() {
        assertEquals("512 B", Formatters.size(512))
        assertEquals("1,5 KB", Formatters.size(1536))
        assertEquals("2,0 MB", Formatters.size(2L * 1024 * 1024))
        assertEquals("Boyut bilinmiyor", Formatters.size(-1))
    }

    @Test
    fun relativeDates() {
        val zone = ZoneId.of("Europe/Istanbul")
        val now = LocalDateTime.of(2026, 11, 4, 15, 0).atZone(zone).toInstant().toEpochMilli()
        val today = LocalDateTime.of(2026, 11, 4, 9, 5).atZone(zone).toInstant().toEpochMilli()
        val yesterday = LocalDateTime.of(2026, 11, 3, 22, 10).atZone(zone).toInstant().toEpochMilli()
        val older = LocalDateTime.of(2025, 3, 1, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Bugün 09:05", Formatters.relativeDate(today, now, zone))
        assertEquals("Dün 22:10", Formatters.relativeDate(yesterday, now, zone))
        assertEquals("1 Mar 2025", Formatters.relativeDate(older, now, zone))
    }
}
