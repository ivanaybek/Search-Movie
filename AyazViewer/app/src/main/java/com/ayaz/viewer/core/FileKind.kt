package com.ayaz.viewer.core

import java.util.Locale

enum class FileKind { PDF, XLSX, UNKNOWN }

object FileKinds {
    const val MIME_PDF = "application/pdf"
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_XLS = "application/vnd.ms-excel"

    /** Seçicide gösterilecek türler. */
    val PICKER_ALL = arrayOf(MIME_PDF, MIME_XLSX)

    fun mimeOf(kind: FileKind): String = when (kind) {
        FileKind.PDF -> MIME_PDF
        FileKind.XLSX -> MIME_XLSX
        FileKind.UNKNOWN -> "application/octet-stream"
    }

    fun extensionOf(kind: FileKind): String = when (kind) {
        FileKind.PDF -> "pdf"
        FileKind.XLSX -> "xlsx"
        FileKind.UNKNOWN -> "bin"
    }

    fun fromName(name: String?): FileKind {
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: return FileKind.UNKNOWN
        return when (ext) {
            "pdf" -> FileKind.PDF
            "xlsx", "xlsm", "xls" -> FileKind.XLSX
            else -> FileKind.UNKNOWN
        }
    }

    fun fromMime(mime: String?): FileKind = when (mime?.lowercase(Locale.ROOT)) {
        MIME_PDF -> FileKind.PDF
        MIME_XLSX, MIME_XLS, "application/vnd.ms-excel.sheet.macroenabled.12" -> FileKind.XLSX
        else -> FileKind.UNKNOWN
    }

    /**
     * İçerik imzasına öncelik verir (uzantısı yanlış dosyalar için), sonra MIME türü ve uzantı.
     * Desteklenmeyen/bilinmeyen türlerde, bozuk dosyanın anlaşılır hata alabilmesi için ad/mime tahmini döner.
     */
    fun detect(name: String?, mime: String?, header: ByteArray?): FileKind {
        if (header != null && header.isNotEmpty()) {
            if (startsWithPdf(header)) return FileKind.PDF
            val isZip = header.size >= 4 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
            val isOle = header.size >= 4 && header[0] == 0xD0.toByte() && header[1] == 0xCF.toByte() &&
                header[2] == 0x11.toByte() && header[3] == 0xE0.toByte()
            if (isZip || isOle) return FileKind.XLSX
        }
        val byMime = fromMime(mime)
        if (byMime != FileKind.UNKNOWN) return byMime
        return fromName(name)
    }

    private fun startsWithPdf(h: ByteArray): Boolean {
        // PDF başlığı ilk 1024 bayt içinde herhangi bir yerde olabilir.
        val limit = minOf(h.size, 1024) - 5
        for (i in 0..limit) {
            if (h[i] == '%'.code.toByte() && h[i + 1] == 'P'.code.toByte() && h[i + 2] == 'D'.code.toByte() &&
                h[i + 3] == 'F'.code.toByte() && h[i + 4] == '-'.code.toByte()
            ) return true
        }
        return false
    }
}
