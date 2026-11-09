package com.ayaz.viewer.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * Son açılan dosyaların kalıcı listesi (uygulama içi JSON dosyası, atomik yazma).
 * Uygulama kapatılıp açıldığında liste korunur.
 */
class RecentFilesRepository(
    context: Context,
    private val documents: DocumentAccess,
    scope: CoroutineScope,
    fileName: String = "recents.json",
) {
    private val store = AtomicFile(File(context.filesDir, fileName))
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val _items = MutableStateFlow<List<RecentFile>>(emptyList())
    val items: StateFlow<List<RecentFile>> = _items.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock { _items.value = read() }
            loaded.complete(Unit)
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    suspend fun get(id: String): RecentFile? {
        awaitLoaded()
        return _items.value.firstOrNull { it.id == id }
    }

    /** Kaydı ekler veya günceller; önceki sayfa/sayfa sekmesi bilgisi korunur. */
    suspend fun upsert(entry: RecentFile): RecentFile = mutate { list ->
        val previous = list.firstOrNull { it.id == entry.id }
        val merged = if (previous != null) {
            entry.copy(lastPage = previous.lastPage, lastSheet = previous.lastSheet)
        } else entry
        val rest = list.filterNot { it.id == entry.id }
        val newList = (listOf(merged) + rest).sortedByDescending { it.lastOpened }
        if (newList.size > MAX_ENTRIES) {
            newList.drop(MAX_ENTRIES).filter { it.isPrivateCopy }.forEach { documents.deleteImported(it.id) }
        }
        newList.take(MAX_ENTRIES) to merged
    }

    suspend fun updatePosition(id: String, page: Int? = null, sheet: Int? = null) {
        mutate { list ->
            list.map {
                if (it.id == id) it.copy(lastPage = page ?: it.lastPage, lastSheet = sheet ?: it.lastSheet) else it
            } to Unit
        }
    }

    /** Listeden kaldırır. Kullanıcının asıl dosyasına dokunulmaz; yalnızca uygulamanın kendi kopyası silinir. */
    suspend fun remove(id: String) {
        mutate { list ->
            list.firstOrNull { it.id == id }?.let { if (it.isPrivateCopy) documents.deleteImported(it.id) }
            list.filterNot { it.id == id } to Unit
        }
    }

    /**
     * Listeyi temizler. Uygulamada oluşturulan belgelerin tek kopyası uygulamada durduğu için
     * bunlar silinmez, listede kalır. Korunan belge sayısı döner.
     */
    suspend fun clear(): Int = mutate { list ->
        val (kept, dropped) = list.partition { it.created }
        dropped.filter { it.isPrivateCopy }.forEach { documents.deleteImported(it.id) }
        kept to kept.size
    }

    private suspend fun <T> mutate(block: (List<RecentFile>) -> Pair<List<RecentFile>, T>): T {
        awaitLoaded()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val (newList, result) = block(_items.value)
                _items.value = newList
                write(newList)
                result
            }
        }
    }

    private fun read(): List<RecentFile> = try {
        if (!store.baseFile.exists()) emptyList() else {
            val text = store.readFully().toString(Charsets.UTF_8)
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { RecentFile.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.lastOpened }
        }
    } catch (_: Exception) {
        emptyList() // bozuk liste dosyası uygulamayı çökertmez
    }

    private fun write(list: List<RecentFile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val out = store.startWrite()
        try {
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            store.finishWrite(out)
        } catch (e: Exception) {
            store.failWrite(out)
        }
    }

    companion object {
        const val MAX_ENTRIES = 100
    }
}
