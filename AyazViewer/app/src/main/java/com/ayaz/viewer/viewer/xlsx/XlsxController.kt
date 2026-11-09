package com.ayaz.viewer.viewer.xlsx

import android.util.Xml
import com.ayaz.viewer.core.ErrorMessages
import com.ayaz.viewer.data.UserFacingException
import com.ayaz.viewer.xlsx.CellFormatter
import com.ayaz.viewer.xlsx.FormatLocale
import com.ayaz.viewer.xlsx.ParseCancelledException
import com.ayaz.viewer.xlsx.SheetData
import com.ayaz.viewer.xlsx.SheetSearch
import com.ayaz.viewer.xlsx.WorkbookData
import com.ayaz.viewer.xlsx.SheetEdit
import com.ayaz.viewer.xlsx.XlsxEditException
import com.ayaz.viewer.xlsx.XlsxEditor
import com.ayaz.viewer.xlsx.XlsxException
import com.ayaz.viewer.xlsx.XlsxReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

sealed interface SheetLoad {
    val index: Int

    data class Loading(override val index: Int) : SheetLoad
    data class Ready(override val index: Int, val sheet: SheetData, val formatter: CellFormatter) : SheetLoad
    data class Message(override val index: Int, val text: String) : SheetLoad
}

/**
 * Bir XLSX çalışma kitabını açık tutar; sayfaları ihtiyaç anında (seçildiğinde) ayrıştırır.
 * Bellek için en fazla iki ayrıştırılmış sayfa önbellekte tutulur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class XlsxController private constructor(
    private var reader: XlsxReader,
    workbook: WorkbookData,
    private val file: File,
    private val temporary: Boolean,
    val locale: FormatLocale,
    private val editDir: File,
) {
    var workbook: WorkbookData = workbook
        private set

    /** Şu anki içerik (düzenleme yoksa açılan dosya, varsa son çalışma kopyası). */
    var currentFile: File = file
        private set
    private val history = ArrayDeque<File>()

    private val _dirty = MutableStateFlow(false)
    /** Kaydedilmemiş düzenleme var mı. */
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val parseDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = object : LinkedHashMap<Int, SheetData>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, SheetData>?): Boolean = size > 2
    }
    private var loadJob: Job? = null

    @Volatile
    private var closed = false

    private val _state = MutableStateFlow<SheetLoad>(SheetLoad.Loading(0))
    val state: StateFlow<SheetLoad> = _state.asStateFlow()

    val sheetCount: Int get() = workbook.sheets.size

    fun select(index: Int) {
        if (index !in workbook.sheets.indices || closed) return
        val current = _state.value
        if (current.index == index && current !is SheetLoad.Loading && loadJob?.isActive != true) return
        loadJob?.cancel()
        val entry = workbook.sheets[index]
        if (!entry.isWorksheet) {
            _state.value = SheetLoad.Message(index, "\"${entry.name}\" bir grafik veya özel sayfa. Bu sayfa türü görüntülenemiyor.")
            return
        }
        synchronized(cache) { cache[index] }?.let {
            _state.value = SheetLoad.Ready(index, it, CellFormatter(workbook, locale))
            return
        }
        _state.value = SheetLoad.Loading(index)
        loadJob = scope.launch {
            val result: SheetLoad = withContext(parseDispatcher) {
                try {
                    val job = coroutineContext[Job]
                    val sheet = reader.readSheet(workbook, index) { job?.isActive != true || closed }
                    synchronized(cache) { cache[index] = sheet }
                    SheetLoad.Ready(index, sheet, CellFormatter(workbook, locale))
                } catch (e: ParseCancelledException) {
                    SheetLoad.Loading(index)
                } catch (e: XlsxException) {
                    SheetLoad.Message(index, ErrorMessages.xlsx(e.reason))
                } catch (e: OutOfMemoryError) {
                    synchronized(cache) { cache.clear() }
                    SheetLoad.Message(index, "Bu sayfa çok büyük olduğu için bellek yetersiz kaldı.")
                } catch (e: Exception) {
                    SheetLoad.Message(index, ErrorMessages.xlsx(XlsxException.Reason.CORRUPT))
                }
            }
            if (isActive) _state.value = result
        }
    }

    /** Hücre araması arka planda yapılır; sonuç (satır shl 32) or sütun dizisidir. */
    suspend fun search(sheet: SheetData, query: String): LongArray = withContext(Dispatchers.Default) {
        val formatter = CellFormatter(workbook, locale)
        val ctx = coroutineContext
        try {
            SheetSearch.find(sheet, formatter, query) { !ctx.isActive }
        } catch (_: ParseCancelledException) {
            ctx.ensureActive()
            LongArray(0)
        }
    }

    // ------------------------------------------------------------------ düzenleme

    /**
     * Düzenlemeyi geçerli sayfaya uygular. Sonuç yeni bir çalışma kopyasına yazılır ve sayfa
     * yeniden okunur; açılan dosyaya dokunulmaz. Hata varsa kullanıcı mesajı döner.
     */
    suspend fun applyEdit(edit: SheetEdit): String? {
        val sheetIndex = _state.value.index
        if (closed) return null
        _busy.value = true
        try {
            return withContext(parseDispatcher) {
                val out = File.createTempFile("duzenleme_", ".xlsx", editDir)
                try {
                    XlsxEditor.apply(currentFile, out, sheetIndex, edit) { Xml.newPullParser() }
                } catch (e: XlsxEditException) {
                    out.delete()
                    return@withContext editMessage(e.reason)
                } catch (e: OutOfMemoryError) {
                    out.delete()
                    return@withContext "Bu sayfa düzenleme için çok büyük."
                } catch (e: Exception) {
                    out.delete()
                    return@withContext "Düzenleme uygulanamadı: dosya beklenmeyen bir yapıda."
                }
                history.addLast(currentFile)
                while (history.size > MAX_UNDO) {
                    val old = history.removeFirst()
                    if (old != file) old.delete()
                }
                switchTo(out)
                _dirty.value = true
                _canUndo.value = history.isNotEmpty()
                null
            }.also { if (it == null) reloadSheet(sheetIndex) }
        } finally {
            _busy.value = false
        }
    }

    /** Son düzenlemeyi geri alır. */
    suspend fun undo() {
        if (history.isEmpty() || closed) return
        val sheetIndex = _state.value.index
        withContext(parseDispatcher) {
            val prev = history.removeLast()
            val discarded = currentFile
            switchTo(prev)
            if (discarded != file) discarded.delete()
            _canUndo.value = history.isNotEmpty()
            _dirty.value = history.isNotEmpty()
        }
        reloadSheet(sheetIndex)
    }

    /** Kaydedildi: mevcut içerik artık dosyanın kendisiyle aynı. */
    fun markSaved() {
        _dirty.value = false
        CoroutineScope(parseDispatcher).launch {
            // Kaydedilen içerik artık asıl dosya; eski adımlara geri dönülmez.
            history.forEach { if (it != file && it != currentFile) it.delete() }
            history.clear()
            _canUndo.value = false
        }
    }

    private fun switchTo(f: File) {
        val newReader = XlsxReader(f, { Xml.newPullParser() })
        val wb = newReader.readWorkbook()
        val old = reader
        reader = newReader
        workbook = wb
        currentFile = f
        synchronized(cache) { cache.clear() }
        old.close()
    }

    private fun reloadSheet(index: Int) {
        _state.value = SheetLoad.Loading(index)
        select(index)
    }

    private fun editMessage(r: XlsxEditException.Reason): String = when (r) {
        XlsxEditException.Reason.HAS_TABLES ->
            "Bu sayfada Excel tablosu var; satır/sütun ekleme ve silme yapılamıyor. Hücre değerlerini düzenleyebilirsiniz."
        XlsxEditException.Reason.TOO_LARGE -> "Bu sayfa düzenleme için çok büyük."
        XlsxEditException.Reason.SHEET_NOT_FOUND -> "Bu sayfa düzenlenemiyor."
        XlsxEditException.Reason.CORRUPT -> "Düzenleme uygulanamadı: sayfa beklenmeyen bir yapıda."
    }

    fun close() {
        if (closed) return
        closed = true
        loadJob?.cancel()
        scope.cancel()
        CoroutineScope(parseDispatcher).launch {
            reader.close()
            if (temporary) file.delete()
            history.forEach { if (it != file) it.delete() }
            if (currentFile != file) currentFile.delete()
        }
    }

    companion object {
        private const val MAX_UNDO = 30

        @Throws(UserFacingException::class)
        fun open(file: File, temporary: Boolean, locale: FormatLocale, editDir: File): XlsxController {
            var reader: XlsxReader? = null
            try {
                reader = XlsxReader(file, { Xml.newPullParser() })
                val wb = reader.readWorkbook()
                if (wb.sheets.isEmpty()) throw XlsxException(XlsxException.Reason.CORRUPT)
                return XlsxController(reader, wb, file, temporary, locale, editDir)
            } catch (e: XlsxException) {
                reader?.close()
                if (temporary) file.delete()
                throw UserFacingException(ErrorMessages.xlsx(e.reason), e)
            } catch (e: OutOfMemoryError) {
                reader?.close()
                if (temporary) file.delete()
                throw UserFacingException(ErrorMessages.OUT_OF_MEMORY, e)
            } catch (e: Exception) {
                reader?.close()
                if (temporary) file.delete()
                throw UserFacingException(ErrorMessages.xlsx(XlsxException.Reason.CORRUPT), e)
            }
        }
    }
}
