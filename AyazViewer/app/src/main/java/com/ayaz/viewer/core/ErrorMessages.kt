package com.ayaz.viewer.core

import com.ayaz.viewer.xlsx.XlsxException
import java.io.FileNotFoundException
import java.io.IOException

/** Kullanıcıya gösterilecek anlaşılır Türkçe hata mesajları. Dosya içeriği asla mesaja eklenmez. */
object ErrorMessages {

    const val NO_ACCESS = "Dosyaya erişilemiyor. Dosya taşınmış, silinmiş veya erişim izni kaldırılmış olabilir."
    const val EMPTY = "Dosya boş (0 bayt) olduğu için açılamadı."
    const val UNSUPPORTED = "Bu dosya türü desteklenmiyor. Ayaz Viewer PDF ve XLSX dosyalarını açar."
    const val NO_SPACE = "Cihazda yeterli boş alan yok."
    const val OUT_OF_MEMORY = "Bu dosyayı açmak için yeterli bellek yok."

    fun xlsx(reason: XlsxException.Reason): String = when (reason) {
        XlsxException.Reason.EMPTY -> EMPTY
        XlsxException.Reason.NOT_XLSX -> "Bu dosya geçerli bir Excel (XLSX) dosyası değil."
        XlsxException.Reason.LEGACY_OR_ENCRYPTED ->
            "Bu dosya eski .xls biçiminde veya parola korumalı. Lütfen dosyayı parolasız .xlsx olarak kaydedip tekrar deneyin."
        XlsxException.Reason.CORRUPT -> "Excel dosyası bozuk veya eksik. Dosya açılamadı."
        XlsxException.Reason.SHEET_NOT_FOUND -> "Çalışma sayfası dosyada bulunamadı."
    }

    fun pdf(t: Throwable): String = when (t) {
        is SecurityException -> "Bu PDF parola korumalı. Parola korumalı PDF dosyaları şu an desteklenmiyor."
        is FileNotFoundException -> NO_ACCESS
        is OutOfMemoryError -> OUT_OF_MEMORY
        is IOException -> "PDF dosyası bozuk veya geçersiz. Dosya açılamadı."
        else -> "PDF açılırken beklenmeyen bir hata oluştu."
    }

    fun generic(t: Throwable): String = when (t) {
        is XlsxException -> xlsx(t.reason)
        is SecurityException, is FileNotFoundException -> NO_ACCESS
        is OutOfMemoryError -> OUT_OF_MEMORY
        is IOException -> if (t.message?.contains("ENOSPC") == true) NO_SPACE else "Dosya okunurken bir hata oluştu."
        else -> "Beklenmeyen bir hata oluştu."
    }
}
