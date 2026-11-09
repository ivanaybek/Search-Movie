package com.ayaz.viewer.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/** Sistem dosya seçicisi (Storage Access Framework) isteği. */
data class PickRequest(val mimeTypes: Array<String>, val initialUri: Uri? = null) {
    override fun equals(other: Any?): Boolean =
        other is PickRequest && other.mimeTypes.contentEquals(mimeTypes) && other.initialUri == initialUri

    override fun hashCode(): Int = mimeTypes.contentHashCode() * 31 + (initialUri?.hashCode() ?: 0)
}

class OpenDocumentContract : ActivityResultContract<PickRequest, Uri?>() {
    override fun createIntent(context: Context, input: PickRequest): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (input.mimeTypes.size == 1) input.mimeTypes[0] else "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, input.mimeTypes)
            input.initialUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            // Yazma izni yalnızca kullanıcı düzenlemeyi kaydederken "Üzerine yaz" derse kullanılır.
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

object InitialLocations {
    /** Telefonun İndirilenler (Download) klasörü; desteklenmezse seçici varsayılan konumda açılır. */
    val DOWNLOADS: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
    val DOCUMENTS: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
}
