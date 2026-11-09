package com.ayaz.viewer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Storage Access Framework üzerinden salt-okunur dosya erişimi.
 *
 * Kullanıcının dosyaları asla değiştirilmez veya silinmez. Yazma işlemleri yalnızca uygulamanın
 * kendi özel alanında (güvenli kopyalar, paylaşım için geçici kopyalar) yapılır.
 */
class DocumentAccess(private val context: Context) {

    private val resolver = context.contentResolver
    val importDir = File(context.filesDir, "imported")
    private val shareDir = File(context.cacheDir, "share")
    private val viewDir = File(context.cacheDir, "view")

    data class Meta(val name: String, val size: Long, val mime: String?)

    fun queryMeta(uri: Uri): Meta {
        localFile(uri)?.let { return Meta(it.name, it.length(), null) }
        var name: String? = null
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {
            // Bazı sağlayıcılar sorguyu desteklemez; ad URI'den türetilir.
        }
        val mime = try { resolver.getType(uri) } catch (_: Exception) { null }
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        return Meta(sanitizeName(name ?: fallback ?: "belge"), size, mime)
    }

    fun localFile(uri: Uri): File? {
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val f = File(path)
        // Yalnızca uygulamanın kendi dizinlerindeki dosyalar.
        val ownRoots = listOf(context.filesDir.canonicalPath, context.cacheDir.canonicalPath)
        val canonical = f.canonicalPath
        return if (ownRoots.any { canonical.startsWith(it) }) f else null
    }

    @Throws(IOException::class)
    fun openInput(uri: Uri): InputStream {
        localFile(uri)?.let { return FileInputStream(it) }
        if (uri.scheme == "file") throw FileNotFoundException("unsupported file uri")
        return resolver.openInputStream(uri) ?: throw FileNotFoundException("no stream")
    }

    @Throws(IOException::class)
    fun openFd(uri: Uri): ParcelFileDescriptor {
        localFile(uri)?.let { return ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
        if (uri.scheme == "file") throw FileNotFoundException("unsupported file uri")
        return resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("no descriptor")
    }

    /** İlk [n] baytı okur (tür tespiti ve erişim kontrolü için). */
    @Throws(IOException::class)
    fun readHeader(uri: Uri, n: Int = 1024): ByteArray = openInput(uri).use { input ->
        val buf = ByteArray(n)
        var total = 0
        while (total < n) {
            val r = input.read(buf, total, n - total)
            if (r < 0) break
            total += r
        }
        buf.copyOf(total)
    }

    /** Kalıcı okuma (mümkünse okuma+yazma) izni alır. Okuma izni alınamazsa false. */
    fun takePersistable(uri: Uri): Boolean {
        try {
            resolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            return true
        } catch (_: Exception) {
            // yazma izni verilmemiş olabilir; yalnızca okuma dene
        }
        return try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** Dosyanın üzerine yazılabilir mi: uygulama kopyası veya kalıcı yazma izni. */
    fun canOverwrite(uri: Uri): Boolean {
        localFile(uri)?.let { return it.exists() && it.canWrite() }
        val s = uri.toString()
        return resolver.persistedUriPermissions.any { p ->
            p.isWritePermission && (p.uri == uri || (isTreeUri(p.uri) && s.startsWith(p.uri.toString())))
        }
    }

    /**
     * [source] içeriğini kullanıcı onayıyla [uri] dosyasının üzerine yazar.
     * Önce tamamen yazılıp sonra yer değiştirilir (uygulama kopyalarında); SAF dosyalarında "wt" kipi kullanılır.
     */
    @Throws(IOException::class)
    fun overwrite(uri: Uri, source: File) {
        localFile(uri)?.let { target ->
            val tmp = File(target.parentFile, ".kaydediliyor")
            source.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return
        }
        writeTo(uri, source)
    }

    /** [source] içeriğini [uri] konumuna yazar (yeni oluşturulan belge veya üzerine yazma). */
    @Throws(IOException::class)
    fun writeTo(uri: Uri, source: File) {
        val out = try {
            resolver.openOutputStream(uri, "wt")
        } catch (_: Exception) {
            null
        } ?: resolver.openOutputStream(uri, "w") ?: throw IOException("no output stream")
        out.use { o -> source.inputStream().use { it.copyTo(o, BUFFER) } }
    }

    /** Düzenleme sırasında oluşturulan geçici çalışma kopyaları için klasör. */
    val editDir: File get() = File(context.cacheDir, "edit").apply { mkdirs() }

    /** URI için kalıcı okuma izni var mı (doğrudan veya izin verilmiş bir klasör ağacı üzerinden). */
    fun hasPersistedRead(uri: Uri): Boolean {
        val s = uri.toString()
        return resolver.persistedUriPermissions.any { p ->
            p.isReadPermission && (p.uri == uri || (isTreeUri(p.uri) && s.startsWith(p.uri.toString())))
        }
    }

    private fun isTreeUri(uri: Uri) = uri.pathSegments.firstOrNull() == "tree"

    /** Kalıcı erişim verilemeyen dosyanın uygulama içi güvenli kopyasını oluşturur. */
    @Throws(IOException::class)
    fun importCopy(uri: Uri, id: String, name: String): File {
        val dir = File(importDir, id)
        if (dir.exists()) dir.deleteRecursively()
        if (!dir.mkdirs() && !dir.exists()) throw IOException("cannot create dir")
        val target = File(dir, name)
        val tmp = File(dir, ".partial")
        try {
            openInput(uri).use { input -> tmp.outputStream().use { out -> input.copyTo(out, BUFFER) } }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (e: IOException) {
            dir.deleteRecursively()
            throw e
        }
        return target
    }

    fun deleteImported(id: String) {
        if (id.isBlank() || id.contains('/') || id.contains("..")) return
        File(importDir, id).deleteRecursively()
    }

    /** Rastgele erişim gerektiren okuyucular (XLSX) için yerel dosya. İkinci değer: geçici mi. */
    @Throws(IOException::class)
    fun materialize(uri: Uri, extension: String): Pair<File, Boolean> {
        localFile(uri)?.let { if (it.exists()) return it to false }
        viewDir.mkdirs()
        val f = File.createTempFile("view_", ".$extension", viewDir)
        try {
            openInput(uri).use { input -> f.outputStream().use { input.copyTo(it, BUFFER) } }
        } catch (e: IOException) {
            f.delete()
            throw e
        }
        return f to true
    }

    /**
     * Paylaşım için dosya: içerik birebir korunur, ad ve uzantı aynı kalır.
     * Uygulama kopyası zaten varsa doğrudan o kullanılır.
     */
    @Throws(IOException::class)
    fun prepareShareFile(uri: Uri, name: String): File {
        localFile(uri)?.let { if (it.exists() && it.name == name) return it }
        val dir = File(shareDir, UUID.randomUUID().toString())
        dir.mkdirs()
        val target = File(dir, sanitizeName(name))
        try {
            openInput(uri).use { input -> target.outputStream().use { input.copyTo(it, BUFFER) } }
        } catch (e: IOException) {
            dir.deleteRecursively()
            throw e
        }
        return target
    }

    fun contentUriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun importedUsageBytes(): Long = importDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Son açılanlar listesinde artık bulunmayan uygulama kopyalarını siler. Serbest kalan bayt döner. */
    fun removeOrphanCopies(validIds: Set<String>): Long {
        var freed = 0L
        importDir.listFiles()?.forEach { dir ->
            if (dir.name !in validIds) {
                freed += dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                dir.deleteRecursively()
            }
        }
        return freed
    }

    fun cleanupTemporary(maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        shareDir.listFiles()?.forEach { if (now - it.lastModified() > maxAgeMs) it.deleteRecursively() }
        viewDir.listFiles()?.forEach { if (now - it.lastModified() > 60_000L) it.delete() }
        File(context.cacheDir, "edit").listFiles()?.forEach { if (now - it.lastModified() > 60_000L) it.deleteRecursively() }
    }

    companion object {
        private const val BUFFER = 256 * 1024

        fun stableId(key: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }

        /** Dosya adındaki geçersiz karakterleri temizler; Türkçe karakterler korunur. */
        fun sanitizeName(raw: String): String {
            var name = raw.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            if (name.isEmpty() || name == "." || name == "..") name = "belge"
            if (name.length > 120) {
                val ext = name.substringAfterLast('.', "")
                name = if (ext.isNotEmpty() && ext.length <= 8) {
                    name.substring(0, 120 - ext.length - 1) + "." + ext
                } else name.substring(0, 120)
            }
            return name
        }
    }
}
