package com.ayaz.viewer.viewer.xlsx

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ayaz.viewer.xlsx.CellDisplay
import com.ayaz.viewer.xlsx.CellFormatter
import com.ayaz.viewer.xlsx.CellRange
import com.ayaz.viewer.xlsx.CellRef
import com.ayaz.viewer.xlsx.CellStyle
import com.ayaz.viewer.xlsx.CellType
import com.ayaz.viewer.xlsx.HAlign
import com.ayaz.viewer.xlsx.RowData
import com.ayaz.viewer.xlsx.SheetData
import com.ayaz.viewer.xlsx.VAlign
import com.ayaz.viewer.xlsx.WorkbookData
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Tablo renk paleti (ARGB). */
class GridPalette(
    val background: Int,
    val grid: Int,
    val headerBg: Int,
    val headerText: Int,
    val headerSelected: Int,
    val text: Int,
    val selection: Int,
    val frozenLine: Int,
    val hit: Int,
    val hitCurrent: Int,
    val dark: Boolean,
) {
    companion object {
        val DARK = GridPalette(
            background = 0xFF101014.toInt(), grid = 0xFF272730.toInt(), headerBg = 0xFF17171D.toInt(),
            headerText = 0xFF9A9AA6.toInt(), headerSelected = 0x404F7CFF, text = 0xFFE9E9EE.toInt(),
            selection = 0xFF4F7CFF.toInt(), frozenLine = 0xFF4A4A57.toInt(), hit = 0x66FFC107,
            hitCurrent = 0xAAFF9800.toInt(), dark = true,
        )
        val LIGHT = GridPalette(
            background = 0xFFFFFFFF.toInt(), grid = 0xFFE1E1E6.toInt(), headerBg = 0xFFF3F3F6.toInt(),
            headerText = 0xFF5F6370.toInt(), headerSelected = 0x333B63F0, text = 0xFF1B1B1F.toInt(),
            selection = 0xFF3B63F0.toInt(), frozenLine = 0xFFB0B3BD.toInt(), hit = 0x66FFC107,
            hitCurrent = 0xAAFF9800.toInt(), dark = false,
        )
    }
}

/**
 * Bir sayfanın geometrisi (temel birim: dp, yakınlaştırma 1'de). Excel ölçüleri kullanılır:
 * sütun genişliği ≈ karakter × 7 + 5 piksel, satır yüksekliği = punto × 4/3.
 */
class GridModel(val sheet: SheetData, val workbook: WorkbookData, val formatter: CellFormatter) {
    val rows: Int = min(max(sheet.rowCount + 5, 40), CellRef.MAX_ROWS)
    val cols: Int = min(max(sheet.colCount + 2, 12), CellRef.MAX_COLS)
    val colStarts = DoubleArray(cols + 1)
    val rowStarts = DoubleArray(rows + 1)
    private val maxMergeRows: Int

    init {
        for (c in 0 until cols) {
            val w = sheet.colWidthChars(c)
            colStarts[c + 1] = colStarts[c] + if (w <= 0f) 0.0 else (w * 7.0 + 5.0)
        }
        for (r in 0 until rows) {
            val h = sheet.rowHeightPt(r)
            rowStarts[r + 1] = rowStarts[r] + if (h <= 0f) 0.0 else h * 4.0 / 3.0
        }
        maxMergeRows = sheet.merges.maxOfOrNull { it.lastRow - it.firstRow } ?: 0
    }

    /** colStarts[i] <= x olan en büyük i. */
    fun colAt(x: Double): Int = indexAt(colStarts, x, cols)

    fun rowAt(y: Double): Int = indexAt(rowStarts, y, rows)

    private fun indexAt(starts: DoubleArray, v: Double, count: Int): Int {
        if (v <= 0) return 0
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= v) lo = mid else hi = mid - 1
        }
        return lo
    }

    private val displayCache = LruCache<Long, CellDisplay>(6000)
    private val emptyDisplay = CellDisplay("", null, CellDisplay.Kind.TEXT)

    fun display(r: Int, row: RowData, i: Int): CellDisplay? {
        val key = (r.toLong() shl 32) or row.cols[i].toLong()
        displayCache.get(key)?.let { return if (it === emptyDisplay) null else it }
        val d = formatter.display(sheet, row, i)
        displayCache.put(key, d ?: emptyDisplay)
        return d
    }

    fun mergeAt(r: Int, c: Int): CellRange? {
        val merges = sheet.merges
        if (merges.isEmpty()) return null
        var i = lowerBound(merges, r - maxMergeRows)
        while (i < merges.size) {
            val m = merges[i]
            if (m.firstRow > r) break
            if (m.contains(r, c)) return m
            i++
        }
        return null
    }

    fun visibleMerges(r0: Int, r1: Int, c0: Int, c1: Int, out: MutableList<CellRange>) {
        out.clear()
        val merges = sheet.merges
        if (merges.isEmpty()) return
        var i = lowerBound(merges, r0 - maxMergeRows)
        while (i < merges.size) {
            val m = merges[i]
            if (m.firstRow > r1) break
            if (m.lastRow >= r0 && m.lastCol >= c0 && m.firstCol <= c1) out.add(m)
            i++
        }
    }

    private fun lowerBound(list: List<CellRange>, row: Int): Int {
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].firstRow < row) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

/** Ekran yerleşimi (piksel). */
class GridMetrics(
    val z: Float,
    val headerW: Float,
    val headerH: Float,
    val fr: Int,
    val fc: Int,
    val x0: Float,
    val y0: Float,
    val maxScrollX: Float,
    val maxScrollY: Float,
)

@Stable
class GridState {
    var scrollX by mutableFloatStateOf(0f)
    var scrollY by mutableFloatStateOf(0f)
    var zoom by mutableFloatStateOf(1f)
    var selected by mutableLongStateOf(-1L)
    var viewW = 0f
    var viewH = 0f
    var density = 1f
    var model: GridModel? = null

    fun metrics(): GridMetrics? {
        val m = model ?: return null
        val z = zoom * density
        val headerH = 24f * density
        val digits = max(3, m.rows.toString().length)
        val headerW = (digits * 7.5f + 14f) * density
        var fc = m.sheet.frozenCols.coerceAtMost(m.cols)
        var fr = m.sheet.frozenRows.coerceAtMost(m.rows)
        if (m.colStarts[fc] * z > (viewW - headerW) * 0.6f) fc = 0
        if (m.rowStarts[fr] * z > (viewH - headerH) * 0.6f) fr = 0
        val x0 = headerW + (m.colStarts[fc] * z).toFloat()
        val y0 = headerH + (m.rowStarts[fr] * z).toFloat()
        val contentW = ((m.colStarts[m.cols] - m.colStarts[fc]) * z).toFloat()
        val contentH = ((m.rowStarts[m.rows] - m.rowStarts[fr]) * z).toFloat()
        return GridMetrics(
            z, headerW, headerH, fr, fc, x0, y0,
            max(0f, contentW - (viewW - x0)), max(0f, contentH - (viewH - y0)),
        )
    }

    fun clamp() {
        val mt = metrics() ?: return
        scrollX = scrollX.coerceIn(0f, mt.maxScrollX)
        scrollY = scrollY.coerceIn(0f, mt.maxScrollY)
    }

    fun scrollBy(dx: Float, dy: Float) {
        val mt = metrics() ?: return
        scrollX = (scrollX + dx).coerceIn(0f, mt.maxScrollX)
        scrollY = (scrollY + dy).coerceIn(0f, mt.maxScrollY)
    }

    fun zoomBy(factor: Float, focus: Offset) {
        val before = metrics() ?: return
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) return
        // odaktaki içerik noktasını sabit tut
        val cx = (scrollX + (focus.x - before.x0)) / before.z
        val cy = (scrollY + (focus.y - before.y0)) / before.z
        zoom = newZoom
        val after = metrics() ?: return
        scrollX = cx * after.z - (focus.x - after.x0)
        scrollY = cy * after.z - (focus.y - after.y0)
        clamp()
    }

    /** Ekran noktasındaki hücre (birleşik hücrede sol üst hücre); başlıklarda -1. */
    fun hitTest(pos: Offset): Long {
        val m = model ?: return -1
        val mt = metrics() ?: return -1
        if (pos.x < mt.headerW || pos.y < mt.headerH) return -1
        val col = if (pos.x < mt.x0) m.colAt(((pos.x - mt.headerW) / mt.z).toDouble())
        else m.colAt((pos.x - mt.x0 + scrollX) / mt.z + m.colStarts[mt.fc])
        val row = if (pos.y < mt.y0) m.rowAt(((pos.y - mt.headerH) / mt.z).toDouble())
        else m.rowAt((pos.y - mt.y0 + scrollY) / mt.z + m.rowStarts[mt.fr])
        val merge = m.mergeAt(row, col)
        return if (merge != null) pack(merge.firstRow, merge.firstCol) else pack(row, col)
    }

    /** Hücreyi görünür alana getirir (gerekirse ortalar). */
    fun reveal(row: Int, col: Int) {
        val m = model ?: return
        val mt = metrics() ?: return
        if (col >= mt.fc) {
            val left = ((m.colStarts[col] - m.colStarts[mt.fc]) * mt.z).toFloat()
            val width = ((m.colStarts[col + 1] - m.colStarts[col]) * mt.z).toFloat()
            val avail = viewW - mt.x0
            if (left < scrollX || left + width > scrollX + avail) scrollX = left - (avail - width) / 2f
        }
        if (row >= mt.fr) {
            val top = ((m.rowStarts[row] - m.rowStarts[mt.fr]) * mt.z).toFloat()
            val height = ((m.rowStarts[row + 1] - m.rowStarts[row]) * mt.z).toFloat()
            val avail = viewH - mt.y0
            if (top < scrollY || top + height > scrollY + avail) scrollY = top - (avail - height) / 2f
        }
        clamp()
    }

    companion object {
        const val MIN_ZOOM = 0.3f
        const val MAX_ZOOM = 4f
        fun pack(row: Int, col: Int): Long = (row.toLong() shl 32) or col.toLong()
    }
}

@Composable
fun SheetGrid(
    model: GridModel,
    state: GridState,
    palette: GridPalette,
    hits: Set<Long>,
    currentHit: Long,
    onCellTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val painter = remember { GridPainter() }
    val tapHandler by androidx.compose.runtime.rememberUpdatedState(onCellTap)
    state.density = density
    state.model = model

    Canvas(
        modifier
            .onSizeChanged {
                state.viewW = it.width.toFloat()
                state.viewH = it.height.toFloat()
                state.clamp()
            }
            .semantics { contentDescription = "Tablo: ${model.sheet.name}" }
            .pointerInput(model) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    flingJob?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var moved = false
                    var multi = false
                    var travel = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) multi = true
                        val pan = event.calculatePan()
                        val factor = event.calculateZoom()
                        if (!moved) {
                            travel += pan.getDistance()
                            if (travel > viewConfiguration.touchSlop || multi) moved = true
                        }
                        if (moved) {
                            if (multi && factor != 1f) state.zoomBy(factor, event.calculateCentroid(useCurrent = true))
                            state.scrollBy(-pan.x, -pan.y)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (!multi) pressed.first().let { tracker.addPosition(it.uptimeMillis, it.position) }
                    }
                    if (!moved && !multi) {
                        val hit = state.hitTest(down.position)
                        if (hit >= 0) tapHandler(hit)
                    } else if (!multi) {
                        val v = tracker.calculateVelocity()
                        if (abs(v.x) + abs(v.y) > 300f) {
                            flingJob = scope.launch {
                                val ax = Animatable(0f)
                                val ay = Animatable(0f)
                                var lx = 0f
                                var ly = 0f
                                launch { ax.animateDecay(-v.x, exponentialDecay()) { state.scrollBy(value - lx, 0f); lx = value } }
                                launch { ay.animateDecay(-v.y, exponentialDecay()) { state.scrollBy(0f, value - ly); ly = value } }
                            }
                        }
                    }
                }
            },
    ) {
        painter.draw(this, model, state, palette, hits, currentHit)
    }
}

/** Görünür hücreleri doğrudan Android Canvas üzerine çizer. */
private class GridPainter {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint()
    private val line = Paint().apply { isAntiAlias = false }
    private val typefaces = arrayOf(
        Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
        Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC),
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC),
    )
    private val layouts = LruCache<String, StaticLayout>(300)
    private val merges = ArrayList<CellRange>()

    private class Region(val r0: Int, val r1: Int, val c0: Int, val c1: Int, val l: Float, val t: Float, val r: Float, val b: Float)

    fun draw(scope: DrawScope, m: GridModel, s: GridState, p: GridPalette, hits: Set<Long>, currentHit: Long) {
        val mt = s.metrics() ?: return
        val w = scope.size.width
        val h = scope.size.height
        // durum okumaları (yeniden çizim tetikler)
        val sx = s.scrollX
        val sy = s.scrollY
        val selected = s.selected

        fun xOf(c: Int): Float = if (c < mt.fc) mt.headerW + (m.colStarts[c] * mt.z).toFloat()
        else mt.x0 + ((m.colStarts[c] - m.colStarts[mt.fc]) * mt.z).toFloat() - sx

        fun yOf(r: Int): Float = if (r < mt.fr) mt.headerH + (m.rowStarts[r] * mt.z).toFloat()
        else mt.y0 + ((m.rowStarts[r] - m.rowStarts[mt.fr]) * mt.z).toFloat() - sy

        val scC0 = m.colAt(sx / mt.z + m.colStarts[mt.fc]).coerceAtLeast(mt.fc)
        val scC1 = m.colAt((sx + w - mt.x0) / mt.z + m.colStarts[mt.fc])
        val scR0 = m.rowAt(sy / mt.z + m.rowStarts[mt.fr]).coerceAtLeast(mt.fr)
        val scR1 = m.rowAt((sy + h - mt.y0) / mt.z + m.rowStarts[mt.fr])

        scope.drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.drawColor(p.background)
            val regions = ArrayList<Region>(4)
            regions.add(Region(scR0, scR1, scC0, scC1, mt.x0, mt.y0, w, h))
            if (mt.fr > 0) regions.add(Region(0, mt.fr - 1, scC0, scC1, mt.x0, mt.headerH, w, mt.y0))
            if (mt.fc > 0) regions.add(Region(scR0, scR1, 0, mt.fc - 1, mt.headerW, mt.y0, mt.x0, h))
            if (mt.fr > 0 && mt.fc > 0) regions.add(Region(0, mt.fr - 1, 0, mt.fc - 1, mt.headerW, mt.headerH, mt.x0, mt.y0))

            for (rg in regions) {
                if (rg.r0 > rg.r1 || rg.c0 > rg.c1) continue
                nc.save()
                nc.clipRect(rg.l, rg.t, rg.r, rg.b)
                drawRegion(nc, m, mt, p, rg, ::xOf, ::yOf, hits, currentHit, selected)
                nc.restore()
            }

            // donmuş bölme çizgileri
            line.color = p.frozenLine
            line.strokeWidth = 1.5f * s.density
            if (mt.fc > 0) nc.drawLine(mt.x0, mt.headerH, mt.x0, h, line)
            if (mt.fr > 0) nc.drawLine(mt.headerW, mt.y0, w, mt.y0, line)

            drawHeaders(nc, m, mt, p, s.density, w, h, scC0, scC1, scR0, scR1, ::xOf, ::yOf, selected)
        }
    }

    private fun drawRegion(
        nc: android.graphics.Canvas,
        m: GridModel,
        mt: GridMetrics,
        p: GridPalette,
        rg: Region,
        xOf: (Int) -> Float,
        yOf: (Int) -> Float,
        hits: Set<Long>,
        currentHit: Long,
        selected: Long,
    ) {
        val sheet = m.sheet
        val wb = m.workbook
        val z = mt.z
        m.visibleMerges(rg.r0, rg.r1, rg.c0, rg.c1, merges)

        // 1) kılavuz çizgileri
        if (sheet.showGridLines) {
            line.color = p.grid
            line.strokeWidth = 1f
            for (c in rg.c0..rg.c1 + 1) {
                val x = xOf(c)
                nc.drawLine(x, rg.t, x, rg.b, line)
            }
            for (r in rg.r0..rg.r1 + 1) {
                val y = yOf(r)
                nc.drawLine(rg.l, y, rg.r, y, line)
            }
        }

        // 2) dolgu renkleri
        for (r in rg.r0..rg.r1) {
            val row = sheet.row(r) ?: continue
            var i = row.firstIndexAtOrAfter(rg.c0)
            while (i < row.size) {
                val c = row.cols[i]
                if (c > rg.c1) break
                val st = wb.style(row.styles[i])
                val color = st.fillColor
                if (color != null && !inMerge(r, c)) {
                    fill.color = color
                    nc.drawRect(xOf(c), yOf(r), xOf(c + 1), yOf(r + 1), fill)
                }
                i++
            }
        }

        // 3) birleşik hücre arka planları (iç kılavuz çizgilerini örter)
        for (mg in merges) {
            val anchor = sheet.row(mg.firstRow)
            val idx = anchor?.indexOf(mg.firstCol) ?: -1
            val st = if (idx >= 0) wb.style(anchor!!.styles[idx]) else null
            fill.color = st?.fillColor ?: p.background
            nc.drawRect(xOf(mg.firstCol) + 0.5f, yOf(mg.firstRow) + 0.5f, xOf(mg.lastCol + 1) - 0.5f, yOf(mg.lastRow + 1) - 0.5f, fill)
        }

        // 4) arama vurguları
        if (hits.isNotEmpty()) {
            for (r in rg.r0..rg.r1) {
                val row = sheet.row(r) ?: continue
                var i = row.firstIndexAtOrAfter(rg.c0)
                while (i < row.size) {
                    val c = row.cols[i]
                    if (c > rg.c1) break
                    val key = GridState.pack(r, c)
                    if (key in hits) {
                        fill.color = if (key == currentHit) p.hitCurrent else p.hit
                        val mg = m.mergeAt(r, c)
                        if (mg != null) nc.drawRect(xOf(mg.firstCol), yOf(mg.firstRow), xOf(mg.lastCol + 1), yOf(mg.lastRow + 1), fill)
                        else nc.drawRect(xOf(c), yOf(r), xOf(c + 1), yOf(r + 1), fill)
                    }
                    i++
                }
            }
        }

        // 5) kenarlıklar
        for (r in rg.r0..rg.r1) {
            val row = sheet.row(r) ?: continue
            var i = row.firstIndexAtOrAfter(rg.c0)
            while (i < row.size) {
                val c = row.cols[i]
                if (c > rg.c1) break
                val st = wb.style(row.styles[i])
                if (st.borderLeft != null || st.borderRight != null || st.borderTop != null || st.borderBottom != null) {
                    val mg = m.mergeAt(r, c)
                    val l = xOf(mg?.firstCol ?: c)
                    val t = yOf(mg?.firstRow ?: r)
                    val rr = xOf((mg?.lastCol ?: c) + 1)
                    val b = yOf((mg?.lastRow ?: r) + 1)
                    st.borderLeft?.let { drawBorder(nc, it, l, t, l, b, p, z) }
                    st.borderRight?.let { drawBorder(nc, it, rr, t, rr, b, p, z) }
                    st.borderTop?.let { drawBorder(nc, it, l, t, rr, t, p, z) }
                    st.borderBottom?.let { drawBorder(nc, it, l, b, rr, b, p, z) }
                }
                i++
            }
        }

        // 6) metinler (taşma: solda başlayan uzun metinler de dahil)
        val fontScale = z * (4f / 3f) * 0.92f
        if (fontScale * 11f >= 3f) {
            for (r in rg.r0..rg.r1) {
                val row = sheet.row(r) ?: continue
                val top = yOf(r)
                val bottom = yOf(r + 1)
                if (bottom - top < 1f) continue
                var i = max(0, row.firstIndexAtOrAfter(rg.c0) - 1)
                while (i < row.size) {
                    val c = row.cols[i]
                    if (c > rg.c1) break
                    if (row.types[i] != CellType.EMPTY && !inMerge(r, c)) {
                        val d = m.display(r, row, i)
                        if (d != null && d.text.isNotEmpty()) {
                            val st = wb.style(row.styles[i])
                            val left = xOf(c)
                            val right = xOf(c + 1)
                            // sağdaki ilk dolu hücreye kadar taşabilir
                            var limit = rg.r
                            var j = i + 1
                            while (j < row.size) {
                                if (row.types[j] != CellType.EMPTY) { limit = xOf(row.cols[j]); break }
                                j++
                            }
                            val samePane = (c < mt.fc) == (rg.c0 < mt.fc)
                            if (c >= rg.c0 || (samePane && canOverflow(st, d))) {
                                drawText(nc, d, st, left, top, right, bottom, limit, fontScale, p, r, c)
                            }
                        }
                    }
                    i++
                }
            }
            for (mg in merges) {
                val anchor = sheet.row(mg.firstRow) ?: continue
                val idx = anchor.indexOf(mg.firstCol)
                if (idx < 0 || anchor.types[idx] == CellType.EMPTY) continue
                val d = m.display(mg.firstRow, anchor, idx) ?: continue
                val st = wb.style(anchor.styles[idx])
                val right = xOf(mg.lastCol + 1)
                drawText(nc, d, st, xOf(mg.firstCol), yOf(mg.firstRow), right, yOf(mg.lastRow + 1), right, fontScale, p, mg.firstRow, mg.firstCol)
            }
        }

        // 7) seçim
        if (selected >= 0) {
            val sr = (selected shr 32).toInt()
            val scol = selected.toInt()
            val mg = m.mergeAt(sr, scol)
            val r1 = mg?.firstRow ?: sr
            val r2 = mg?.lastRow ?: sr
            val c1 = mg?.firstCol ?: scol
            val c2 = mg?.lastCol ?: scol
            if (r2 >= rg.r0 && r1 <= rg.r1 && c2 >= rg.c0 && c1 <= rg.c1) {
                val l = xOf(mg?.firstCol ?: scol)
                val t = yOf(mg?.firstRow ?: sr)
                val rr = xOf((mg?.lastCol ?: scol) + 1)
                val b = yOf((mg?.lastRow ?: sr) + 1)
                fill.color = (p.selection and 0x00FFFFFF) or 0x22000000
                nc.drawRect(l, t, rr, b, fill)
                line.color = p.selection
                line.strokeWidth = 2.5f
                nc.drawLine(l, t, rr, t, line); nc.drawLine(l, b, rr, b, line)
                nc.drawLine(l, t, l, b, line); nc.drawLine(rr, t, rr, b, line)
            }
        }
    }

    private fun inMerge(r: Int, c: Int): Boolean {
        for (mg in merges) if (mg.contains(r, c)) return true
        return false
    }

    private fun canOverflow(st: CellStyle, d: CellDisplay): Boolean =
        d.kind == CellDisplay.Kind.TEXT && !st.wrapText && (st.hAlign == HAlign.GENERAL || st.hAlign == HAlign.LEFT)

    private fun drawBorder(nc: android.graphics.Canvas, side: com.ayaz.viewer.xlsx.BorderSide, x1: Float, y1: Float, x2: Float, y2: Float, p: GridPalette, z: Float) {
        var color = side.color ?: if (p.dark) 0xFFB8B8C2.toInt() else 0xFF000000.toInt()
        if (p.dark && side.color != null && luminance(color) < 0.2f) color = 0xFFB8B8C2.toInt()
        line.color = color
        line.strokeWidth = max(1f, side.weight * z * 0.75f)
        nc.drawLine(x1, y1, x2, y2, line)
    }

    private fun drawText(
        nc: android.graphics.Canvas,
        d: CellDisplay,
        st: CellStyle,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        overflowLimit: Float,
        fontScale: Float,
        p: GridPalette,
        r: Int,
        c: Int,
    ) {
        val cellW = right - left
        if (cellW < 2f) return
        val styleIdx = (if (st.bold) 1 else 0) + (if (st.italic) 2 else 0)
        paint.typeface = typefaces[styleIdx]
        paint.textSize = st.fontSizePt * fontScale
        paint.isUnderlineText = st.underline
        paint.isStrikeThruText = st.strike
        paint.color = textColor(st, d, p)
        val pad = fontScale * 2.2f + 1f
        val indent = st.indent * 9f * fontScale

        if (st.wrapText && d.kind == CellDisplay.Kind.TEXT) {
            val width = (cellW - 2 * pad - indent).toInt()
            if (width <= 4) return
            val key = "$r:$c:$width:${paint.textSize.toInt()}:$styleIdx"
            val layout = layouts.get(key) ?: run {
                val align = when (st.hAlign) {
                    HAlign.CENTER, HAlign.CENTER_CONTINUOUS -> Layout.Alignment.ALIGN_CENTER
                    HAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                    else -> Layout.Alignment.ALIGN_NORMAL
                }
                StaticLayout.Builder.obtain(d.text, 0, d.text.length, TextPaint(paint), width)
                    .setAlignment(align).setIncludePad(false).setMaxLines(200).build()
                    .also { layouts.put(key, it) }
            }
            val lh = layout.height.toFloat()
            val y = when (st.vAlign) {
                VAlign.TOP -> top + pad
                VAlign.CENTER, VAlign.JUSTIFY, VAlign.DISTRIBUTED -> top + (bottom - top - lh) / 2f
                VAlign.BOTTOM -> bottom - pad - lh
            }
            nc.save()
            nc.clipRect(left, top, right, bottom)
            nc.translate(left + pad + indent, max(top + pad, y).coerceAtMost(bottom))
            layout.draw(nc)
            nc.restore()
            return
        }

        var text = if (d.text.indexOf('\n') >= 0 || d.text.indexOf('\r') >= 0) d.text.replace('\n', ' ').replace('\r', ' ') else d.text
        var tw = paint.measureText(text)
        val isNumber = d.kind == CellDisplay.Kind.NUMBER
        if (isNumber && tw > cellW - 2 * pad) {
            // Excel davranışı: sığmayan sayı yerine ####
            val hw = paint.measureText("#").coerceAtLeast(1f)
            val n = floor((cellW - 2 * pad) / hw).toInt().coerceIn(1, 60)
            text = "#".repeat(n)
            tw = hw * n
        }
        val align = when (st.hAlign) {
            HAlign.GENERAL -> when (d.kind) {
                CellDisplay.Kind.NUMBER -> HAlign.RIGHT
                CellDisplay.Kind.BOOLEAN, CellDisplay.Kind.ERROR -> HAlign.CENTER
                CellDisplay.Kind.TEXT -> HAlign.LEFT
            }
            HAlign.CENTER_CONTINUOUS, HAlign.DISTRIBUTED -> HAlign.CENTER
            HAlign.FILL, HAlign.JUSTIFY -> HAlign.LEFT
            else -> st.hAlign
        }
        val x = when (align) {
            HAlign.RIGHT -> right - pad - tw - indent
            HAlign.CENTER -> left + (cellW - tw) / 2f
            else -> left + pad + indent
        }
        val fm = paint.fontMetrics
        val baseline = when (st.vAlign) {
            VAlign.TOP -> top + pad - fm.ascent
            VAlign.CENTER, VAlign.JUSTIFY, VAlign.DISTRIBUTED -> (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f
            VAlign.BOTTOM -> bottom - pad * 0.6f - fm.descent
        }
        val clipRight = if (align == HAlign.LEFT && d.kind == CellDisplay.Kind.TEXT && !st.wrapText) max(right, overflowLimit) else right
        nc.save()
        nc.clipRect(left, top, clipRight, bottom)
        nc.drawText(text, x, baseline, paint)
        nc.restore()
    }

    private fun textColor(st: CellStyle, d: CellDisplay, p: GridPalette): Int {
        val explicit = d.formatColor ?: st.fontColor
        val fillColor = st.fillColor
        if (explicit != null) {
            if (p.dark && fillColor == null) return adaptForDark(explicit, p)
            return explicit
        }
        if (fillColor != null) return if (luminance(fillColor) > 0.45f) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        return p.text
    }

    /** Koyu zeminde okunamayacak kadar koyu yazı renklerini açar (tonu korunur). */
    private fun adaptForDark(color: Int, p: GridPalette): Int {
        if (luminance(color) >= 0.25f) return color
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl)
        if (hsl[1] < 0.15f) return p.text
        hsl[2] = 0.72f
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }

    private fun luminance(color: Int): Float {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun drawHeaders(
        nc: android.graphics.Canvas,
        m: GridModel,
        mt: GridMetrics,
        p: GridPalette,
        density: Float,
        w: Float,
        h: Float,
        scC0: Int,
        scC1: Int,
        scR0: Int,
        scR1: Int,
        xOf: (Int) -> Float,
        yOf: (Int) -> Float,
        selected: Long,
    ) {
        val selRow = if (selected >= 0) (selected shr 32).toInt() else -1
        val selCol = if (selected >= 0) selected.toInt() else -1
        fill.color = p.headerBg
        nc.drawRect(0f, 0f, w, mt.headerH, fill)
        nc.drawRect(0f, 0f, mt.headerW, h, fill)
        paint.typeface = typefaces[0]
        paint.textSize = 11f * density
        paint.isUnderlineText = false
        paint.isStrikeThruText = false
        val fm = paint.fontMetrics
        line.color = p.grid
        line.strokeWidth = 1f

        fun colHeader(c: Int) {
            val l = xOf(c)
            val r = xOf(c + 1)
            if (r - l < 1f) return
            if (c == selCol) { fill.color = p.headerSelected; nc.drawRect(l, 0f, r, mt.headerH, fill) }
            val label = CellRef.columnName(c)
            val tw = paint.measureText(label)
            paint.color = if (c == selCol) p.selection else p.headerText
            if (r - l > tw + 2f) nc.drawText(label, (l + r - tw) / 2f, mt.headerH / 2f - (fm.ascent + fm.descent) / 2f, paint)
            nc.drawLine(r, 0f, r, mt.headerH, line)
        }

        fun rowHeader(rIdx: Int) {
            val t = yOf(rIdx)
            val b = yOf(rIdx + 1)
            if (b - t < 1f) return
            if (rIdx == selRow) { fill.color = p.headerSelected; nc.drawRect(0f, t, mt.headerW, b, fill) }
            val label = (rIdx + 1).toString()
            val tw = paint.measureText(label)
            paint.color = if (rIdx == selRow) p.selection else p.headerText
            if (b - t > (fm.descent - fm.ascent) * 0.7f) {
                nc.drawText(label, (mt.headerW - tw) / 2f, (t + b) / 2f - (fm.ascent + fm.descent) / 2f, paint)
            }
            nc.drawLine(0f, b, mt.headerW, b, line)
        }

        nc.save()
        nc.clipRect(mt.x0, 0f, w, mt.headerH)
        for (c in scC0..scC1) colHeader(c)
        nc.restore()
        if (mt.fc > 0) {
            nc.save(); nc.clipRect(mt.headerW, 0f, mt.x0, mt.headerH)
            for (c in 0 until mt.fc) colHeader(c)
            nc.restore()
        }
        nc.save()
        nc.clipRect(0f, mt.y0, mt.headerW, h)
        for (r in scR0..scR1) rowHeader(r)
        nc.restore()
        if (mt.fr > 0) {
            nc.save(); nc.clipRect(0f, mt.headerH, mt.headerW, mt.y0)
            for (r in 0 until mt.fr) rowHeader(r)
            nc.restore()
        }
        line.color = p.frozenLine
        nc.drawLine(0f, mt.headerH, w, mt.headerH, line)
        nc.drawLine(mt.headerW, 0f, mt.headerW, h, line)
        fill.color = p.headerBg
        nc.drawRect(0f, 0f, mt.headerW, mt.headerH, fill)
        // köşe üçgeni
        line.color = p.headerText
        nc.drawLine(mt.headerW - 6f * density, mt.headerH - 2f * density, mt.headerW - 2f * density, mt.headerH - 6f * density, line)
    }
}
