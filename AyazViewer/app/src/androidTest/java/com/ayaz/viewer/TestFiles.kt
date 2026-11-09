package com.ayaz.viewer

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ayaz.viewer.core.FileKind
import com.ayaz.viewer.data.DocumentAccess
import com.ayaz.viewer.data.RecentFile
import kotlinx.coroutines.runBlocking
import java.io.File

/** Test varlıklarını (androidTest/assets) uygulama alanına kopyalama yardımcıları. */
object TestFiles {
    val app: AyazApp get() = ApplicationProvider.getApplicationContext()

    fun asset(name: String, target: File): File {
        target.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        return target
    }

    fun cacheCopy(name: String, as_: String = name): File = asset(name, File(app.cacheDir, "test/$as_"))

    /** Dosyayı "son açılanlar"a uygulama kopyası olarak ekler (WhatsApp'tan gelmiş gibi). */
    fun seedRecent(assetName: String, displayName: String = assetName, kind: FileKind): RecentFile {
        val id = DocumentAccess.stableId("test:$displayName")
        val file = asset(assetName, File(app.filesDir, "imported/$id/$displayName"))
        val entry = RecentFile(
            id = id,
            uri = Uri.fromFile(file).toString(),
            name = displayName,
            kind = kind,
            size = file.length(),
            lastOpened = System.currentTimeMillis(),
            isPrivateCopy = true,
        )
        return runBlocking { app.recents.upsert(entry) }
    }

    /** Testler arası temiz başlangıç: oluşturulan belgeler dahil tüm kayıtları kaldırır. */
    fun clearRecents() = runBlocking {
        app.recents.awaitLoaded()
        app.recents.items.value.forEach { app.recents.remove(it.id) }
    }
}
