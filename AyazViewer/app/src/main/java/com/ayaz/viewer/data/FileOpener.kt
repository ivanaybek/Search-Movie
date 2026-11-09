package com.ayaz.viewer.data

import android.net.Uri
import com.ayaz.viewer.core.ErrorMessages
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.core.FileKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Kullanıcıya gösterilecek mesajı taşıyan hata. */
class UserFacingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Seçici, "Birlikte aç" veya "Paylaş" ile gelen bir URI'yi açılabilir bir [RecentFile] kaydına çevirir.
 *
 * - Kalıcı izin alınabiliyorsa (SAF seçici, izin verilmiş klasörler) dosya yerinde okunur.
 * - Alınamıyorsa (ör. WhatsApp'tan geçici izinle gelen dosya) uygulamanın özel alanına
 *   birebir güvenli kopya oluşturulur; böylece "Son Açılanlar"dan tekrar açılabilir.
 */
class FileOpener(
    private val documents: DocumentAccess,
    private val recents: RecentFilesRepository,
) {

    suspend fun open(uri: Uri, mimeHint: String? = null): RecentFile = withContext(Dispatchers.IO) {
        val meta = try {
            documents.queryMeta(uri)
        } catch (e: SecurityException) {
            throw UserFacingException(ErrorMessages.NO_ACCESS, e)
        }
        val header = try {
            documents.readHeader(uri)
        } catch (e: SecurityException) {
            throw UserFacingException(ErrorMessages.NO_ACCESS, e)
        } catch (e: FileNotFoundException) {
            throw UserFacingException(ErrorMessages.NO_ACCESS, e)
        } catch (e: IOException) {
            throw UserFacingException(ErrorMessages.generic(e), e)
        }
        val kind = FileKinds.detect(meta.name, meta.mime ?: mimeHint, header)
        if (kind == FileKind.UNKNOWN) throw UserFacingException(ErrorMessages.UNSUPPORTED)
        val name = ensureExtension(meta.name, kind)
        val id = DocumentAccess.stableId(uri.toString())

        val local = documents.localFile(uri)
        val persisted = local != null || documents.takePersistable(uri) || documents.hasPersistedRead(uri)
        val entry = if (persisted) {
            RecentFile(
                id = id,
                uri = uri.toString(),
                name = name,
                kind = kind,
                size = if (meta.size >= 0) meta.size else -1L,
                lastOpened = System.currentTimeMillis(),
                isPrivateCopy = local != null && local.startsWith(documents.importDir),
            )
        } else {
            val copy: File = try {
                documents.importCopy(uri, id, name)
            } catch (e: SecurityException) {
                throw UserFacingException(ErrorMessages.NO_ACCESS, e)
            } catch (e: IOException) {
                throw UserFacingException(ErrorMessages.generic(e), e)
            }
            RecentFile(
                id = id,
                uri = Uri.fromFile(copy).toString(),
                name = name,
                kind = kind,
                size = copy.length(),
                lastOpened = System.currentTimeMillis(),
                isPrivateCopy = true,
            )
        }
        recents.upsert(entry)
    }

    /** Son açılanlardan yeniden açma: erişimin hâlâ geçerli olduğunu doğrular. */
    suspend fun reopen(entry: RecentFile): RecentFile = withContext(Dispatchers.IO) {
        val uri = Uri.parse(entry.uri)
        try {
            documents.readHeader(uri, 8)
        } catch (e: Exception) {
            throw UserFacingException(
                if (entry.isPrivateCopy) "Dosyanın uygulama içi kopyası bulunamadı. Lütfen dosyayı yeniden açın."
                else ErrorMessages.NO_ACCESS + " Dosyayı \"Dosya Aç\" ile yeniden seçebilirsiniz.",
                e,
            )
        }
        recents.upsert(entry.copy(lastOpened = System.currentTimeMillis()))
    }

    companion object {
        fun ensureExtension(name: String, kind: FileKind): String {
            val ext = FileKinds.extensionOf(kind)
            val current = name.substringAfterLast('.', "").lowercase()
            val ok = when (kind) {
                FileKind.PDF -> current == "pdf"
                FileKind.XLSX -> current == "xlsx" || current == "xlsm" || current == "xls"
                FileKind.UNKNOWN -> true
            }
            return if (ok) name else "$name.$ext"
        }
    }
}
