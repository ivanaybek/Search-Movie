package com.ayaz.viewer.viewer.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import java.io.File
import java.io.IOException
import java.util.Calendar

/** Yeni PDF belgeleri oluşturur (A4). Türkçe karakterler için DejaVu Sans gömülür. */
object PdfCreator {

    private const val MARGIN = 56f

    @Throws(IOException::class)
    fun blank(target: File, pages: Int) {
        PDDocument().use { doc ->
            repeat(pages.coerceIn(1, 50)) { doc.addPage(PDPage(PDRectangle.A4)) }
            info(doc, "Boş belge")
            save(doc, target)
        }
    }

    /** Başlık ve metinden, satırları kaydırarak ve gerektiğinde yeni sayfaya geçerek PDF oluşturur. */
    @Throws(IOException::class)
    fun fromText(context: Context, target: File, title: String, body: String, fontSize: Float) {
        PDDocument().use { doc ->
            val font = context.assets.open("fonts/DejaVuSans.ttf").use { PDType0Font.load(doc, it) }
            val size = fontSize.coerceIn(8f, 32f)
            val titleSize = size * 1.6f
            val width = PDRectangle.A4.width - 2 * MARGIN
            val lines = ArrayList<Pair<String, Float>>() // metin, punto
            if (title.isNotBlank()) {
                wrap(font, PdfEditor.supported(font, title.trim()), titleSize, width).forEach { lines.add(it to titleSize) }
                lines.add("" to size)
            }
            for (para in body.replace("\r", "").split('\n')) {
                if (para.isBlank()) { lines.add("" to size); continue }
                wrap(font, PdfEditor.supported(font, para), size, width).forEach { lines.add(it to size) }
            }
            if (lines.isEmpty()) lines.add("" to size)

            var page: PDPage? = null
            var cs: PDPageContentStream? = null
            var y = 0f
            try {
                for ((text, pt) in lines) {
                    val leading = pt * 1.4f
                    if (cs == null || y - leading < MARGIN) {
                        cs?.endText()
                        cs?.close()
                        page = PDPage(PDRectangle.A4).also { doc.addPage(it) }
                        cs = PDPageContentStream(doc, page).also {
                            it.beginText()
                            it.setNonStrokingColor(0.07f, 0.07f, 0.09f)
                        }
                        y = PDRectangle.A4.height - MARGIN
                        cs.newLineAtOffset(MARGIN, y - pt)
                        y -= pt
                    } else {
                        cs.newLineAtOffset(0f, -leading)
                        y -= leading
                    }
                    cs.setFont(font, pt)
                    if (text.isNotEmpty()) cs.showText(text)
                }
            } finally {
                cs?.endText()
                cs?.close()
            }
            info(doc, title.ifBlank { "Belge" })
            save(doc, target)
        }
    }

    private fun wrap(font: PDFont, text: String, size: Float, width: Float): List<String> {
        fun w(s: String) = try { font.getStringWidth(s) / 1000f * size } catch (_: Exception) { s.length * size * 0.5f }
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (w(candidate) <= width) {
                line = StringBuilder(candidate)
                continue
            }
            if (line.isNotEmpty()) { out.add(line.toString()); line = StringBuilder() }
            // tek başına sığmayan uzun kelimeyi harf harf böl
            var rest = word
            while (w(rest) > width && rest.length > 1) {
                var n = rest.length - 1
                while (n > 1 && w(rest.substring(0, n)) > width) n--
                out.add(rest.substring(0, n))
                rest = rest.substring(n)
            }
            line = StringBuilder(rest)
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return out
    }

    private fun info(doc: PDDocument, title: String) {
        doc.documentInformation = PDDocumentInformation().apply {
            this.title = title
            creator = "Ayaz Viewer"
            producer = "Ayaz Viewer"
            creationDate = Calendar.getInstance()
        }
    }

    private fun save(doc: PDDocument, target: File) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        doc.save(tmp)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
