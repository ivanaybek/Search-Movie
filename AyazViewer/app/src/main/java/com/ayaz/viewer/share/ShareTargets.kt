package com.ayaz.viewer.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.ayaz.viewer.core.FileKinds
import com.ayaz.viewer.data.DocumentAccess
import com.ayaz.viewer.data.RecentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Yalnızca iki paylaşım hedefi desteklenir. */
enum class ShareApp(val title: String) {
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
}

data class ShareVariant(val app: ShareApp, val packageName: String, val label: String)

sealed interface ShareResult {
    data object Started : ShareResult
    data class Failed(val message: String) : ShareResult
}

/**
 * WhatsApp ve Telegram'a doğrudan dosya paylaşımı. Genel Android paylaşım menüsü (chooser)
 * kullanılmaz; niyet (intent) doğrudan seçilen paket adına yönlendirilir.
 */
object ShareTargets {

    val VARIANTS: List<ShareVariant> = listOf(
        ShareVariant(ShareApp.WHATSAPP, "com.whatsapp", "WhatsApp"),
        ShareVariant(ShareApp.WHATSAPP, "com.whatsapp.w4b", "WhatsApp Business"),
        ShareVariant(ShareApp.TELEGRAM, "org.telegram.messenger", "Telegram"),
        ShareVariant(ShareApp.TELEGRAM, "org.telegram.messenger.web", "Telegram"),
        ShareVariant(ShareApp.TELEGRAM, "org.telegram.messenger.beta", "Telegram Beta"),
        ShareVariant(ShareApp.TELEGRAM, "org.thunderdog.challegram", "Telegram X"),
    )

    fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Cihazda yüklü olan varyantlar (ör. hem WhatsApp hem WhatsApp Business). */
    fun installedVariants(context: Context, app: ShareApp): List<ShareVariant> {
        val pm = context.packageManager
        return VARIANTS.filter { it.app == app && isInstalled(pm, it.packageName) }
            .distinctBy { it.packageName }
    }

    fun notInstalledMessage(app: ShareApp): String =
        "${app.title} bu cihazda yüklü değil. Paylaşmak için önce ${app.title} uygulamasını yükleyin."

    /** Gerçek dosyayı (bağlantı değil) gönderen ACTION_SEND niyeti. */
    fun buildIntent(contentUri: Uri, mimeType: String, packageName: String, displayName: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            // ClipData, geçici okuma izninin hedef uygulamaya güvenle aktarılmasını sağlar.
            clipData = ClipData.newRawUri(displayName, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(packageName)
        }

    /**
     * Dosyayı hazırlar (içerik değiştirilmeden, aynı ad ve uzantıyla) ve hedef uygulamanın
     * paylaşım ekranını açar. Kullanıcı hedef uygulamada vazgeçerse bu bir hata değildir.
     */
    suspend fun share(context: Context, documents: DocumentAccess, file: RecentFile, variant: ShareVariant): ShareResult {
        if (!isInstalled(context.packageManager, variant.packageName)) {
            return ShareResult.Failed(notInstalledMessage(variant.app))
        }
        val prepared = withContext(Dispatchers.IO) {
            try {
                documents.prepareShareFile(Uri.parse(file.uri), file.name)
            } catch (_: Exception) {
                null
            }
        } ?: return ShareResult.Failed("Dosya paylaşım için hazırlanamadı. Dosyaya erişim kaldırılmış olabilir.")

        val contentUri = try {
            documents.contentUriFor(prepared)
        } catch (_: IllegalArgumentException) {
            return ShareResult.Failed("Dosya paylaşım için hazırlanamadı.")
        }
        val intent = buildIntent(contentUri, FileKinds.mimeOf(file.kind), variant.packageName, file.name)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            return ShareResult.Failed("${variant.label} bu dosya türünü kabul etmiyor.")
        }
        return try {
            context.startActivity(intent)
            ShareResult.Started
        } catch (_: ActivityNotFoundException) {
            ShareResult.Failed(notInstalledMessage(variant.app))
        } catch (_: SecurityException) {
            ShareResult.Failed("${variant.label} açılamadı.")
        }
    }
}
