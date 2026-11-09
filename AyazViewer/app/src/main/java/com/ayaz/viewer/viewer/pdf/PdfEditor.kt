package com.ayaz.viewer.viewer.pdf

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File
import java.io.IOException

/** Sayfa üzerine eklenen işaretleme. Koordinatlar ekranda görünen sayfaya göredir (nokta, sol üst orijin). */
sealed interface PdfMark {
    val page: Int
}

data class InkMark(
    override val page: Int,
    val points: List<Offset>,
    val color: Int,
    /** Çizgi kalınlığı (nokta). */
    val width: Float,
    val highlighter: Boolean,
) : PdfMark

data class TextMark(
    override val page: Int,
    /** Metin kutusunun sol üst köşesi (nokta). */
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int,
    val size: Float,
) : PdfMark

/** Sayfa işlemleri: döndürme (derece, 90'ın katı) ve silme. */
sealed interface PageOp {
    data class Rotate(val page: Int, val degrees: Int) : PageOp
    data class Delete(val page: Int) : PageOp
}

/**
 * PDFBox ile işaretlemeleri sayfa içeriğine çizer. Android'in kendi PDF bileşeni (PdfRenderer) PDF
 * notlarını (annotation) göstermediği için işaretlemeler not olarak değil, sayfanın içine eklenir:
 * böylece Ayaz Viewer dahil her görüntüleyicide aynı görünür. Belgenin mevcut metni değiştirilmez;
 * aranabilir ve seçilebilir kalır (eklenen metin notları da aranabilir metindir).
 */
object PdfEditor {

    @Throws(IOException::class)
    fun write(context: Context, source: File, target: File, marks: List<PdfMark>, op: PageOp? = null) {
        PDDocument.load(source, MemoryUsageSetting.setupMixed(48L * 1024 * 1024)).use { doc ->
            if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
            var font: PDFont? = null
            for ((pageIndex, pageMarks) in marks.groupBy { it.page }) {
                if (pageIndex !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(pageIndex)
                val space = Space(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (mark in pageMarks) {
                        cs.saveGraphicsState()
                        val m = space.m
                        cs.transform(Matrix(m[0], m[1], m[2], m[3], m[4], m[5]))
                        when (mark) {
                            is InkMark -> drawInk(cs, space, mark)
                            is TextMark -> {
                                val f = font ?: context.assets.open("fonts/DejaVuSans.ttf").use { PDType0Font.load(doc, it) }
                                    .also { font = it }
                                drawText(cs, space, mark, f)
                            }
                        }
                        cs.restoreGraphicsState()
                    }
                }
            }
            when (op) {
                is PageOp.Rotate -> if (op.page in 0 until doc.numberOfPages) {
                    val p = doc.getPage(op.page)
                    p.rotation = (((p.rotation + op.degrees) % 360) + 360) % 360
                }
                is PageOp.Delete -> if (op.page in 0 until doc.numberOfPages && doc.numberOfPages > 1) {
                    doc.removePage(op.page)
                }
                null -> {}
            }
            val tmp = File(target.parentFile, target.name + ".tmp")
            doc.save(tmp)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    // ------------------------------------------------------------------ koordinatlar

    /**
     * Ekran (döndürülmüş, y aşağı) koordinatlarını, y'si yukarı bakan "görünüm" uzayına taşıyan
     * ve oradan PDF kullanıcı uzayına geçen dönüşüm. Döndürme ve CropBox hesaba katılır.
     */
    private class Space(page: PDPage) {
        val crop: PDRectangle = page.cropBox
        val rotation = ((page.rotation % 360) + 360) % 360
        val displayHeight: Float = if (rotation == 90 || rotation == 270) crop.width else crop.height
        private val llx = crop.lowerLeftX
        private val lly = crop.lowerLeftY
        private val urx = crop.upperRightX
        private val ury = crop.upperRightY

        /** a b c d e f: (x, y') → (a x + c y' + e, b x + d y' + f) */
        val m: FloatArray = when (rotation) {
            90 -> floatArrayOf(0f, 1f, -1f, 0f, urx, lly)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f, urx, ury)
            270 -> floatArrayOf(0f, -1f, 1f, 0f, llx, ury)
            else -> floatArrayOf(1f, 0f, 0f, 1f, llx, lly)
        }

        /** Ekran y'sini görünüm y'sine çevirir. */
        fun flipY(y: Float) = displayHeight - y

        fun toUser(x: Float, yUp: Float): Pair<Float, Float> =
            (m[0] * x + m[2] * yUp + m[4]) to (m[1] * x + m[3] * yUp + m[5])

        /** Görünüm uzayındaki kutunun kullanıcı uzayındaki sınırlayıcı dikdörtgeni. */
        fun userRect(x0: Float, y0: Float, x1: Float, y1: Float): PDRectangle {
            val pts = listOf(toUser(x0, y0), toUser(x1, y0), toUser(x0, y1), toUser(x1, y1))
            val minX = pts.minOf { it.first }
            val minY = pts.minOf { it.second }
            val maxX = pts.maxOf { it.first }
            val maxY = pts.maxOf { it.second }
            return PDRectangle(minX, minY, maxX - minX, maxY - minY)
        }

        fun matrixArray(): COSArray = COSArray().apply { m.forEach { add(COSFloat(it)) } }
    }

    private fun rgb(color: Int): FloatArray = floatArrayOf(
        ((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f,
    )

    /** Görünüm uzayında (x, y yukarı) çizgi çizer. */
    private fun drawInk(cs: PDPageContentStream, space: Space, mark: InkMark) {
        if (mark.points.isEmpty()) return
        val pts = mark.points.map { Offset(it.x, space.flipY(it.y)) }
        val gs = PDExtendedGraphicsState()
        gs.strokingAlphaConstant = if (mark.highlighter) 0.4f else 1f
        if (mark.highlighter) gs.blendMode = BlendMode.MULTIPLY
        cs.setGraphicsStateParameters(gs)
        val c = rgb(mark.color)
        cs.setStrokingColor(c[0], c[1], c[2])
        cs.setLineWidth(mark.width)
        cs.setLineCapStyle(if (mark.highlighter) 2 else 1)
        cs.setLineJoinStyle(1)
        cs.moveTo(pts[0].x, pts[0].y)
        if (pts.size == 1) cs.lineTo(pts[0].x + 0.01f, pts[0].y) else for (i in 1 until pts.size) cs.lineTo(pts[i].x, pts[i].y)
        cs.stroke()
    }

    private fun drawText(cs: PDPageContentStream, space: Space, mark: TextMark, font: PDFont) {
        val lines = mark.text.replace("\r", "").split('\n')
        if (lines.all { it.isBlank() }) return
        val leading = mark.size * 1.25f
        val c = rgb(mark.color)
        cs.beginText()
        cs.setFont(font, mark.size)
        cs.setNonStrokingColor(c[0], c[1], c[2])
        // İlk satırın taban çizgisi: ekranda (y + boyut) → görünüm uzayında çevrilmiş y
        cs.newLineAtOffset(mark.x, space.flipY(mark.y + mark.size))
        lines.forEachIndexed { i, line ->
            if (i > 0) cs.newLineAtOffset(0f, -leading)
            cs.showText(supported(font, line))
        }
        cs.endText()
    }

    private fun safeWidth(font: PDFont, s: String): Float = try {
        font.getStringWidth(supported(font, s))
    } catch (_: Exception) {
        s.length * 500f
    }

    /** Yazı tipinde bulunmayan karakterleri (ör. emoji) "?" ile değiştirir; aksi halde PDFBox hata verir. */
    internal fun supported(font: PDFont, s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val ok = try { font.getStringWidth(ch); true } catch (_: Exception) { false }
            sb.append(if (ok) ch else "?")
            i += Character.charCount(cp)
        }
        return sb.toString()
    }
}
