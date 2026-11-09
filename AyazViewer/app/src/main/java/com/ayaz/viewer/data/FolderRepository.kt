package com.ayaz.viewer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.core.FileKinds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

data class GrantedFolder(val treeUri: String, val name: String)

data class FolderEntry(
    val name: String,
    val documentId: String,
    val isDirectory: Boolean,
    val kind: FileKind,
    val size: Long,
    val lastModified: Long,
    val uri: Uri,
)

/**
 * Kullanıcının "Klasör ekle" ile izin verdiği klasörler. Geniş depolama izni yerine yalnızca
 * seçilen klasörlere (ACTION_OPEN_DOCUMENT_TREE) okuma erişimi kullanılır.
 */
class FolderRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("ayaz_folders", Context.MODE_PRIVATE)
    private val _folders = MutableStateFlow(read())
    val folders: StateFlow<List<GrantedFolder>> = _folders.asStateFlow()

    private fun read(): List<GrantedFolder> = try {
        val arr = JSONArray(prefs.getString("folders", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            GrantedFolder(o.getString("uri"), o.getString("name"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun write(list: List<GrantedFolder>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("uri", it.treeUri).put("name", it.name)) }
        prefs.edit().putString("folders", arr.toString()).apply()
        _folders.value = list
    }

    fun add(treeUri: Uri): GrantedFolder? {
        return try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val name = queryName(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                ?: docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Klasör" }
            val folder = GrantedFolder(treeUri.toString(), name)
            write(_folders.value.filterNot { it.treeUri == folder.treeUri } + folder)
            folder
        } catch (_: Exception) {
            null
        }
    }

    fun remove(folder: GrantedFolder) {
        try {
            val uri = Uri.parse(folder.treeUri)
            val flags = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }?.let {
                (if (it.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            } ?: Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}
        write(_folders.value.filterNot { it.treeUri == folder.treeUri })
    }

    fun rootDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    private fun queryName(docUri: Uri): String? = try {
        context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    /** Klasördeki alt klasörleri ve PDF/XLSX dosyalarını tek sorguda listeler. */
    fun listChildren(treeUri: Uri, documentId: String): List<FolderEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = ArrayList<FolderEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val kind = if (isDir) FileKind.UNKNOWN else {
                    val byMime = FileKinds.fromMime(mime)
                    if (byMime != FileKind.UNKNOWN) byMime else FileKinds.fromName(name)
                }
                if (!isDir && kind == FileKind.UNKNOWN) continue
                if (!isDir && name.endsWith(".xls", ignoreCase = true)) continue
                if (name.startsWith(".")) continue
                result.add(
                    FolderEntry(
                        name = name,
                        documentId = id,
                        isDirectory = isDir,
                        kind = kind,
                        size = if (c.isNull(3)) -1L else c.getLong(3),
                        lastModified = if (c.isNull(4)) 0L else c.getLong(4),
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                    ),
                )
            }
        }
        val collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))
        return result.sortedWith(compareBy<FolderEntry> { !it.isDirectory }.thenComparator { a, b -> collator.compare(a.name, b.name) })
    }
}
