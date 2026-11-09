package com.ayaz.viewer.viewer.pdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

enum class PdfTool(val label: String) {
    PEN("Kalem"),
    HIGHLIGHTER("Vurgulayıcı"),
    TEXT("Metin"),
    ERASER("Silgi"),
}

sealed interface PdfUndo {
    data class Added(val mark: PdfMark) : PdfUndo
    data class Removed(val mark: PdfMark, val index: Int) : PdfUndo
    /** Sayfa işleminden önceki durum (çalışma dosyası ve bekleyen işaretlemeler). */
    data class Snapshot(val file: File?, val marks: List<PdfMark>) : PdfUndo
}

/** Bir PDF'in düzenleme oturumu. Bekleyen işaretlemeler ekranda çizilir, kaydedince PDF'e yazılır. */
class PdfEditState {
    val marks = mutableStateListOf<PdfMark>()
    val undo = ArrayDeque<PdfUndo>()
    /** Sayfa döndürme/silme sonrası oluşan çalışma kopyası (yoksa asıl dosya). */
    var workingFile: File? = null
    var sourceCopy: File? = null
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set

    fun refresh() {
        canUndo = undo.isNotEmpty()
        dirty = marks.isNotEmpty() || workingFile != null
    }
}

class PdfEditActions(
    val state: PdfEditState,
    val add: (PdfMark) -> Unit,
    val remove: (PdfMark) -> Unit,
    val undo: () -> Unit,
    val pageOp: (PageOp) -> Unit,
    val save: () -> Unit,
)

/** Kalem ve metin için renkler; vurgulayıcı için yarı saydam çizilir. */
val PEN_COLORS = listOf(0xFF111111.toInt(), 0xFFE53935.toInt(), 0xFF1E63FF.toInt(), 0xFF2E9E4F.toInt(), 0xFFFFD600.toInt())
val PEN_WIDTHS = listOf(1.5f, 3f, 5f)
val HIGHLIGHT_WIDTHS = listOf(8f, 12f, 18f)
val TEXT_SIZES = listOf(10f, 14f, 20f)
