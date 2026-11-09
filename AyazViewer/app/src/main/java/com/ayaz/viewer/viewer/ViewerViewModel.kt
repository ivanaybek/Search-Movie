package com.ayaz.viewer.viewer

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayaz.viewer.AyazApp
import com.ayaz.viewer.core.ErrorMessages
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.data.FileOpener
import com.ayaz.viewer.data.NumberLocale
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.data.UserFacingException
import android.os.ParcelFileDescriptor
import com.ayaz.viewer.viewer.pdf.PageOp
import com.ayaz.viewer.viewer.pdf.PdfController
import com.ayaz.viewer.viewer.pdf.PdfEditActions
import com.ayaz.viewer.viewer.pdf.PdfEditState
import com.ayaz.viewer.viewer.pdf.PdfEditor
import com.ayaz.viewer.viewer.pdf.PdfUndo
import java.io.File
import com.ayaz.viewer.viewer.xlsx.XlsxController
import com.ayaz.viewer.xlsx.FormatLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ViewerState {
    data class Loading(val message: String) : ViewerState
    data class Error(val message: String, val file: RecentFile? = null) : ViewerState
    data class Pdf(val file: RecentFile, val controller: PdfController) : ViewerState
    data class Xlsx(val file: RecentFile, val controller: XlsxController) : ViewerState
}

class ViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AyazApp
    private val opener = FileOpener(app.documents, app.recents)
    private var handled = false

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Loading("Dosya açılıyor…"))
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    val settings = app.settings.settings

    /** Etkinlik ilk oluşturulduğunda bir kez çağrılır; döndürmede ViewModel korunur. */
    /** Yeni oluşturulan belgeler doğrudan düzenleme kipinde açılır. */
    var startInEdit = false
        private set

    fun handle(intent: Intent) {
        if (handled) return
        handled = true
        startInEdit = intent.getBooleanExtra(EXTRA_START_EDIT, false)
        viewModelScope.launch {
            try {
                val entry = resolve(intent)
                _state.value = ViewerState.Loading("Belge hazırlanıyor…")
                _state.value = openController(entry)
            } catch (e: UserFacingException) {
                _state.value = ViewerState.Error(e.message ?: ErrorMessages.NO_ACCESS)
            } catch (e: OutOfMemoryError) {
                _state.value = ViewerState.Error(ErrorMessages.OUT_OF_MEMORY)
            } catch (e: Exception) {
                _state.value = ViewerState.Error(ErrorMessages.generic(e))
            }
        }
    }

    private suspend fun resolve(intent: Intent): RecentFile {
        intent.getStringExtra(EXTRA_RECENT_ID)?.let { id ->
            val entry = app.recents.get(id) ?: throw UserFacingException("Dosya kaydı bulunamadı.")
            return entry
        }
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            else -> intent.data
        }
        if (uri == null) throw UserFacingException("Açılacak dosya bulunamadı.")
        _state.value = ViewerState.Loading("Dosya alınıyor…")
        return opener.open(uri, intent.type)
    }

    private suspend fun openController(entry: RecentFile): ViewerState = withContext(Dispatchers.IO) {
        val uri = Uri.parse(entry.uri)
        when (entry.kind) {
            FileKind.PDF -> ViewerState.Pdf(
                entry,
                PdfController.open(
                    context = app,
                    openFd = { app.documents.openFd(uri) },
                    fallbackCopy = { app.documents.materialize(uri, "pdf").first },
                    sizeHint = entry.size,
                ),
            )
            FileKind.XLSX -> {
                val (file, temporary) = try {
                    app.documents.materialize(uri, "xlsx")
                } catch (e: SecurityException) {
                    throw UserFacingException(ErrorMessages.NO_ACCESS, e)
                } catch (e: java.io.IOException) {
                    throw UserFacingException(ErrorMessages.generic(e), e)
                }
                val locale = if (app.settings.settings.value.numberLocale == NumberLocale.EN) FormatLocale.ENGLISH else FormatLocale.TURKISH
                ViewerState.Xlsx(entry, XlsxController.open(file, temporary, locale, app.documents.editDir))
            }
            FileKind.UNKNOWN -> throw UserFacingException(ErrorMessages.UNSUPPORTED)
        }
    }

    /** Görüntüleyiciyi verilen dosyayla (yeniden) açar; düzenleme oturumu sıfırlanır. */
    private suspend fun reopen(entry: RecentFile) {
        val old = _state.value
        _state.value = ViewerState.Loading("Belge yeniden açılıyor…")
        when (old) {
            is ViewerState.Pdf -> old.controller.close()
            is ViewerState.Xlsx -> old.controller.close()
            else -> {}
        }
        resetPdfEdit()
        _state.value = try {
            openController(app.recents.get(entry.id) ?: entry)
        } catch (e: UserFacingException) {
            ViewerState.Error(e.message ?: ErrorMessages.NO_ACCESS)
        } catch (e: Exception) {
            ViewerState.Error(ErrorMessages.generic(e))
        }
    }

    // ------------------------------------------------------------------ PDF düzenleme

    val pdfEdit = PdfEditState()

    private fun resetPdfEdit() {
        pdfEdit.marks.clear()
        pdfEdit.undo.clear()
        pdfEdit.workingFile = null
        pdfEdit.sourceCopy?.delete()
        pdfEdit.sourceCopy = null
        pdfEdit.refresh()
    }

    fun pdfEditActions(entry: RecentFile, requestSave: (SaveRequest) -> Unit) = PdfEditActions(
        state = pdfEdit,
        add = { m ->
            pdfEdit.marks.add(m)
            pdfEdit.undo.addLast(PdfUndo.Added(m))
            pdfEdit.refresh()
        },
        remove = { m ->
            val idx = pdfEdit.marks.indexOf(m)
            if (idx >= 0) {
                pdfEdit.marks.removeAt(idx)
                pdfEdit.undo.addLast(PdfUndo.Removed(m, idx))
                pdfEdit.refresh()
            }
        },
        undo = { undoPdf(entry) },
        pageOp = { op -> applyPageOp(entry, op) },
        save = {
            requestSave(
                SaveRequest(
                    produce = { buildPdfOutput(entry) },
                    onOverwritten = { viewModelScope.launch { reopen(entry) } },
                ),
            )
        },
    )

    private fun undoPdf(entry: RecentFile) {
        val last = pdfEdit.undo.removeLastOrNull() ?: return
        when (last) {
            is PdfUndo.Added -> pdfEdit.marks.remove(last.mark)
            is PdfUndo.Removed -> pdfEdit.marks.add(last.index.coerceAtMost(pdfEdit.marks.size), last.mark)
            is PdfUndo.Snapshot -> viewModelScope.launch {
                val discarded = pdfEdit.workingFile
                pdfEdit.workingFile = last.file
                pdfEdit.marks.clear()
                pdfEdit.marks.addAll(last.marks)
                showPdf(entry, last.file)
                if (discarded != null && discarded != last.file) discarded.delete()
            }
        }
        pdfEdit.refresh()
    }

    /** Düzenlemenin dayanacağı yerel dosya: son çalışma kopyası veya asıl dosyanın kopyası. */
    private suspend fun pdfSource(entry: RecentFile): File = withContext(Dispatchers.IO) {
        pdfEdit.workingFile?.let { return@withContext it }
        val uri = Uri.parse(entry.uri)
        app.documents.localFile(uri)?.takeIf { it.exists() }?.let { return@withContext it }
        pdfEdit.sourceCopy?.takeIf { it.exists() }?.let { return@withContext it }
        val copy = File.createTempFile("kaynak_", ".pdf", app.documents.editDir)
        app.documents.openInput(uri).use { input -> copy.outputStream().use { input.copyTo(it) } }
        pdfEdit.sourceCopy = copy
        copy
    }

    private fun applyPageOp(entry: RecentFile, op: PageOp) {
        if (pdfEdit.busy) return
        viewModelScope.launch {
            pdfEdit.busy = true
            pdfEdit.refresh()
            try {
                val src = pdfSource(entry)
                val out = withContext(Dispatchers.IO) {
                    File.createTempFile("duzenleme_", ".pdf", app.documents.editDir).also {
                        PdfEditor.write(app, src, it, pdfEdit.marks.toList(), op)
                    }
                }
                pdfEdit.undo.addLast(PdfUndo.Snapshot(pdfEdit.workingFile, pdfEdit.marks.toList()))
                pdfEdit.workingFile = out
                pdfEdit.marks.clear()
                showPdf(entry, out)
            } catch (e: Exception) {
                pdfEdit.message = "İşlem uygulanamadı: ${ErrorMessages.pdf(e)}"
            } catch (e: OutOfMemoryError) {
                pdfEdit.message = ErrorMessages.OUT_OF_MEMORY
            } finally {
                pdfEdit.busy = false
                pdfEdit.refresh()
            }
        }
    }

    /** Görüntüleyicide [file] içeriğini gösterir (asıl kayıt aynı kalır). */
    private suspend fun showPdf(entry: RecentFile, file: File?) {
        val target = file ?: pdfSource(entry)
        val controller = withContext(Dispatchers.IO) {
            PdfController.open(
                context = app,
                openFd = { ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY) },
                fallbackCopy = { File.createTempFile("gorunum_", ".pdf", app.documents.editDir).also { target.copyTo(it, overwrite = true) } },
                sizeHint = target.length(),
            )
        }
        val old = _state.value as? ViewerState.Pdf
        _state.value = ViewerState.Pdf(entry, controller)
        old?.controller?.close()
    }

    /** Kaydedilecek PDF: işaretlemeler gerçek PDF notu olarak eklenmiş yeni dosya. */
    private suspend fun buildPdfOutput(entry: RecentFile): File {
        val src = pdfSource(entry)
        val marks = pdfEdit.marks.toList()
        if (marks.isEmpty()) return src
        return withContext(Dispatchers.IO) {
            File.createTempFile("kayit_", ".pdf", app.documents.editDir).also {
                PdfEditor.write(app, src, it, marks, null)
            }
        }
    }

    fun savePdfPage(entry: RecentFile, page: Int) {
        if (!app.settings.settings.value.rememberLastPage) return
        viewModelScope.launch(NonCancellable) { app.recents.updatePosition(entry.id, page = page) }
    }

    fun saveSheet(entry: RecentFile, sheet: Int) {
        viewModelScope.launch(NonCancellable) { app.recents.updatePosition(entry.id, sheet = sheet) }
    }

    fun toggleLightSheets() = app.settings.update { it.copy(lightSheets = !it.lightSheets) }

    // ------------------------------------------------------------------ kaydetme

    /**
     * Kayıt konumu seçilirken bekleyen istek. ViewModel'de tutulur; böylece sistem seçicisi
     * açıkken ekran döndürülse veya etkinlik yeniden oluşturulsa da kaydetme kaybolmaz.
     */
    var pendingSave: SaveRequest? = null

    /** "Telefona kaydet": PDF'in o anki hâli (kaydedilmemiş işaretlemeler dahil). */
    fun pdfCopyRequest(entry: RecentFile) = SaveRequest(produce = { buildPdfOutput(entry) }, onOverwritten = {})

    fun canOverwrite(entry: RecentFile): Boolean = app.documents.canOverwrite(Uri.parse(entry.uri))

    /** Kullanıcı onayıyla asıl dosyanın üzerine yazar. Sonuç mesajı [done] ile bildirilir. */
    fun saveOverwrite(entry: RecentFile, request: SaveRequest, done: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                val f = request.produce()
                withContext(Dispatchers.IO) { app.documents.overwrite(Uri.parse(entry.uri), f) }
                app.recents.upsert(entry.copy(size = f.length(), lastOpened = System.currentTimeMillis()))
                request.onOverwritten()
                "Kaydedildi: \"${entry.name}\" güncellendi."
            } catch (e: SecurityException) {
                "Dosyaya yazma izni yok. Yeni kopya olarak kaydedin."
            } catch (e: Exception) {
                "Kaydedilemedi: ${ErrorMessages.generic(e)}"
            }
            done(msg)
        }
    }

    /** Kullanıcının seçtiği yeni konuma kaydeder ve yeni dosyayı son açılanlara ekler. */
    fun saveAsNew(target: Uri, request: SaveRequest, done: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                val f = request.produce()
                withContext(Dispatchers.IO) { app.documents.writeTo(target, f) }
                val saved = try { opener.open(target) } catch (_: Exception) { null }
                if (saved != null) reopen(saved)
                "Yeni kopya kaydedildi" + (saved?.let { ": ${it.name}" } ?: ".")
            } catch (e: Exception) {
                "Kaydedilemedi: ${ErrorMessages.generic(e)}"
            }
            done(msg)
        }
    }

    fun initialPage(entry: RecentFile): Int = if (app.settings.settings.value.rememberLastPage) entry.lastPage else 0

    override fun onCleared() {
        pdfEdit.undo.forEach { (it as? PdfUndo.Snapshot)?.file?.delete() }
        pdfEdit.workingFile?.delete()
        pdfEdit.sourceCopy?.delete()
        when (val s = _state.value) {
            is ViewerState.Pdf -> s.controller.close()
            is ViewerState.Xlsx -> s.controller.close()
            else -> {}
        }
        super.onCleared()
    }

    companion object {
        const val EXTRA_RECENT_ID = "com.ayaz.viewer.extra.RECENT_ID"
        const val EXTRA_START_EDIT = "com.ayaz.viewer.extra.START_EDIT"
    }
}
