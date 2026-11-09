package com.ayaz.viewer

import android.app.Application
import com.ayaz.viewer.data.DocumentAccess
import com.ayaz.viewer.data.FolderRepository
import com.ayaz.viewer.data.RecentFilesRepository
import com.ayaz.viewer.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Basit bağımlılık kabı. Uygulama hiçbir ağ, analitik veya reklam bileşeni içermez. */
class AyazApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val documents: DocumentAccess by lazy { DocumentAccess(this) }
    val recents: RecentFilesRepository by lazy { RecentFilesRepository(this, documents, appScope) }
    val folders: FolderRepository by lazy { FolderRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // PDF düzenleme kitaplığının yerel kaynakları (yazı tipleri, glif listeleri).
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        appScope.launch(Dispatchers.IO) {
            // Paylaşım için oluşturulan eski geçici kopyaları ve görüntüleme önbelleğini temizle.
            documents.cleanupTemporary(maxAgeMs = 6 * 60 * 60 * 1000L)
        }
    }
}

val android.content.Context.ayazApp: AyazApp get() = applicationContext as AyazApp
