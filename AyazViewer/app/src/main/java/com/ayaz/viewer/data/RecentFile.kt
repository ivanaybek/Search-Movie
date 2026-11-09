package com.ayaz.viewer.data

import com.ayaz.viewer.core.FileKind
import org.json.JSONObject

/** Son açılan bir dosyanın kaydı. Yalnızca meta veri tutulur; dosya içeriği saklanmaz. */
data class RecentFile(
    val id: String,
    /** content:// (kalıcı izinli) veya file:// (uygulama içi güvenli kopya). */
    val uri: String,
    val name: String,
    val kind: FileKind,
    val size: Long,
    val lastOpened: Long,
    val lastPage: Int = 0,
    val lastSheet: Int = 0,
    val isPrivateCopy: Boolean = false,
    /** Uygulama içinde oluşturulmuş belge (tek kopyası uygulamada). */
    val created: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("uri", uri)
        .put("name", name)
        .put("kind", kind.name)
        .put("size", size)
        .put("lastOpened", lastOpened)
        .put("lastPage", lastPage)
        .put("lastSheet", lastSheet)
        .put("isPrivateCopy", isPrivateCopy)
        .put("created", created)

    companion object {
        fun fromJson(o: JSONObject): RecentFile? = try {
            RecentFile(
                id = o.getString("id"),
                uri = o.getString("uri"),
                name = o.getString("name"),
                kind = runCatching { FileKind.valueOf(o.getString("kind")) }.getOrDefault(FileKind.UNKNOWN),
                size = o.optLong("size", -1L),
                lastOpened = o.optLong("lastOpened", 0L),
                lastPage = o.optInt("lastPage", 0),
                lastSheet = o.optInt("lastSheet", 0),
                isPrivateCopy = o.optBoolean("isPrivateCopy", false),
                created = o.optBoolean("created", false),
            )
        } catch (_: Exception) {
            null
        }
    }
}
