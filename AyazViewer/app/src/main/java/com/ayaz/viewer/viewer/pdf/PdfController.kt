package com.ayaz.viewer.viewer.pdf

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import android.util.LruCache
import com.ayaz.viewer.core.ErrorMessages
import com.ayaz.viewer.data.UserFacingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Bir sayfadaki arama eşleşmesi; dikdörtgenler PDF nokta (1/72 inç) koordinatlarındadır. */
data class PdfSearchHit(val page: Int, val rects: List<RectF>)

/**
 * Android [PdfRenderer] sarmalayıcısı.
 *
 * - Sayfalar yalnızca görünür olduklarında (lazy) ve ekran genişliğine göre çizilir.
 * - Bitmap'ler boyut sınırlı bir LRU önbellekte tutulur; tüm belge belleğe yüklenmez.
 * - PdfRenderer iş parçacığı güvenli olmadığından tüm erişim tek iş parçacıklı bir
 *   dağıtıcı + kilit üzerinden yapılır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfController private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val tempFile: File?,
    cacheBytes: Int,
) {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @Volatile
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Sayfa boyutları (nokta). Bilinmeyenler için ilk sayfanın boyutu kullanılır. */
    private val widths = FloatArray(pageCount)
    private val heights = FloatArray(pageCount)
    private var defaultAspect = 1.4142f

    private val _sizesVersion = MutableStateFlow(0)
    /** Sayfa boyutları güncellendikçe artar (yerleşimin yeniden hesaplanması için). */
    val sizesVersion: StateFlow<Int> = _sizesVersion.asStateFlow()

    private val cache = object : LruCache<Long, Bitmap>(cacheBytes) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
    }

    init {
        if (pageCount > 0) {
            renderer.openPage(0).use { p ->
                widths[0] = p.width.toFloat()
                heights[0] = p.height.toFloat()
                if (p.width > 0) defaultAspect = p.height.toFloat() / p.width
            }
        }
    }

    /** Yükseklik / genişlik oranı. */
    fun aspect(page: Int): Float {
        val w = widths.getOrElse(page) { 0f }
        return if (w > 0f) heights[page] / w else defaultAspect
    }

    fun pageWidthPoints(page: Int): Float {
        val w = widths.getOrElse(page) { 0f }
        return if (w > 0f) w else widths.firstOrNull { it > 0f } ?: 595f
    }

    private fun key(page: Int, width: Int): Long = (page.toLong() shl 32) or width.toLong()

    fun cached(page: Int, width: Int): Bitmap? = cache.get(key(page, width))

    /** Bu sayfa için önbellekteki en iyi (en geniş) bitmap; yakınlaştırma değişirken titremeyi önler. */
    fun bestCached(page: Int): Bitmap? {
        var best: Bitmap? = null
        for ((k, v) in cache.snapshot()) {
            if ((k shr 32).toInt() == page && (best == null || v.width > best.width)) best = v
        }
        return best
    }

    /** Sayfayı [targetWidth] piksel genişliğinde çizer (piksel sayısı sınırlıdır). */
    suspend fun render(page: Int, targetWidth: Int): Bitmap? {
        if (page !in 0 until pageCount || targetWidth <= 0) return null
        cached(page, targetWidth)?.let { return it }
        return withContext(dispatcher) {
            lock.withLock {
                if (closed) return@withLock null
                coroutineContext.ensureActive()
                cached(page, targetWidth)?.let { return@withLock it }
                try {
                    renderLocked(page, targetWidth)
                } catch (e: OutOfMemoryError) {
                    cache.evictAll()
                    try { renderLocked(page, targetWidth / 2) } catch (_: Throwable) { null }
                } catch (_: IllegalStateException) {
                    null
                }
            }
        }
    }

    private fun renderLocked(page: Int, targetWidth: Int): Bitmap {
        renderer.openPage(page).use { p ->
            updateSize(page, p.width, p.height)
            var w = targetWidth
            var h = (w * (p.height.toFloat() / p.width.coerceAtLeast(1))).roundToInt().coerceAtLeast(1)
            val pixels = w.toLong() * h
            if (pixels > MAX_PIXELS) {
                val f = sqrt(MAX_PIXELS.toDouble() / pixels)
                w = (w * f).toInt().coerceAtLeast(1)
                h = (h * f).toInt().coerceAtLeast(1)
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE) // saydam PDF arka planları için
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            cache.put(key(page, targetWidth), bmp)
            return bmp
        }
    }

    private fun updateSize(page: Int, w: Int, h: Int) {
        if (widths[page] == w.toFloat() && heights[page] == h.toFloat()) return
        val before = aspect(page)
        widths[page] = w.toFloat()
        heights[page] = h.toFloat()
        if (abs(aspect(page) - before) > 0.001f) _sizesVersion.value++
    }

    /** Tüm sayfa boyutlarını arka planda, çizim isteklerinin arasına girerek okur. */
    fun preloadSizes() {
        scope.launch {
            var changed = false
            for (i in 0 until pageCount) {
                if (closed) return@launch
                if (widths[i] > 0f) continue
                lock.withLock {
                    if (!closed && widths[i] == 0f) {
                        try {
                            renderer.openPage(i).use { p ->
                                val before = aspect(i)
                                widths[i] = p.width.toFloat()
                                heights[i] = p.height.toFloat()
                                if (abs(aspect(i) - before) > 0.001f) changed = true
                            }
                        } catch (_: Exception) {}
                    }
                }
                if (i % 50 == 0) {
                    if (changed) { _sizesVersion.value++; changed = false }
                    yield()
                }
            }
            if (changed) _sizesVersion.value++
        }
    }

    /** Metin araması bu cihazda destekleniyor mu (Android 15+ veya güncel PDF sistem bileşeni). */
    val isSearchSupported: Boolean = searchAvailable()

    /**
     * Belgede metin arar. Tarama bitene kadar [onProgress] ile ilerleme bildirilir.
     * Metin katmanı olmayan (taranmış) belgelerde sonuç boş döner.
     */
    @SuppressLint("NewApi")
    suspend fun search(query: String, onProgress: (Int) -> Unit): List<PdfSearchHit> {
        if (!isSearchSupported || query.isBlank()) return emptyList()
        val hits = ArrayList<PdfSearchHit>()
        for (i in 0 until pageCount) {
            coroutineContext.ensureActive()
            val pageHits = withContext(dispatcher) {
                lock.withLock {
                    if (closed) return@withLock emptyList<PdfSearchHit>()
                    try {
                        renderer.openPage(i).use { p ->
                            p.searchText(query).map { m -> PdfSearchHit(i, m.bounds.map { RectF(it) }) }
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            hits.addAll(pageHits)
            onProgress(i + 1)
        }
        return hits
    }

    fun close() {
        if (closed) return
        closed = true
        scope.launch {
            lock.withLock {
                try { renderer.close() } catch (_: Exception) {}
                try { fd.close() } catch (_: Exception) {}
                tempFile?.delete()
            }
            cache.evictAll()
        }
    }

    companion object {
        /** Tek bir sayfa bitmap'i için üst sınır (~32 MB, ARGB_8888). */
        private const val MAX_PIXELS = 8_000_000L

        fun searchAvailable(): Boolean = Build.VERSION.SDK_INT >= 35 ||
            (Build.VERSION.SDK_INT >= 30 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13)

        private fun cacheBudget(context: Context): Int {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mb = (am.memoryClass / 3).coerceIn(32, 128)
            return mb * 1024 * 1024
        }

        /**
         * Belgeyi açar. Önce doğrudan dosya tanımlayıcısı denenir; sağlayıcı aranabilir (seekable)
         * bir tanımlayıcı vermiyorsa [fallbackCopy] ile geçici yerel kopya üzerinden açılır.
         */
        @Throws(UserFacingException::class)
        fun open(context: Context, openFd: () -> ParcelFileDescriptor, fallbackCopy: () -> File, sizeHint: Long): PdfController {
            if (sizeHint == 0L) throw UserFacingException(ErrorMessages.EMPTY)
            val budget = cacheBudget(context)
            var fd: ParcelFileDescriptor? = null
            try {
                fd = try {
                    openFd()
                } catch (e: SecurityException) {
                    throw UserFacingException(ErrorMessages.NO_ACCESS, e)
                } catch (e: java.io.FileNotFoundException) {
                    throw UserFacingException(ErrorMessages.NO_ACCESS, e)
                }
                if (fd.statSize == 0L) throw UserFacingException(ErrorMessages.EMPTY)
                val r = PdfRenderer(fd)
                return PdfController(fd, r, null, budget)
            } catch (e: UserFacingException) {
                fd?.close()
                throw e
            } catch (e: SecurityException) {
                fd?.close()
                throw UserFacingException(ErrorMessages.pdf(e), e)
            } catch (e: IOException) {
                fd?.close()
                // Aranamayan akışlar (pipe) için yerel kopya ile yeniden dene.
                val copy = try { fallbackCopy() } catch (c: Exception) { throw UserFacingException(ErrorMessages.generic(c), c) }
                if (copy.length() == 0L) {
                    copy.delete()
                    throw UserFacingException(ErrorMessages.EMPTY)
                }
                var fd2: ParcelFileDescriptor? = null
                try {
                    fd2 = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(fd2)
                    return PdfController(fd2, r, copy, budget)
                } catch (e2: Throwable) {
                    fd2?.close()
                    copy.delete()
                    throw UserFacingException(ErrorMessages.pdf(e2), e2)
                }
            } catch (e: OutOfMemoryError) {
                fd?.close()
                throw UserFacingException(ErrorMessages.OUT_OF_MEMORY, e)
            } catch (e: Exception) {
                fd?.close()
                throw UserFacingException(ErrorMessages.pdf(e), e)
            }
        }
    }
}
