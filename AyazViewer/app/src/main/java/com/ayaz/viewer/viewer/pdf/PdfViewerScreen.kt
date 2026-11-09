package com.ayaz.viewer.viewer.pdf

import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayaz.viewer.data.RecentFile
import com.ayaz.viewer.ui.icons.AyazIcons
import com.ayaz.viewer.ui.theme.LocalAyazColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_ZOOM = 5f

/** Yakınlaştırma durumu. Ekran = içerik × ölçek + öteleme (orijin sol üst). */
@Stable
class PdfZoomState {
    var scale by mutableFloatStateOf(1f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
    /** Çizim çözünürlüğü için hareket bittikten sonra güncellenen ölçek. */
    var settledScale by mutableFloatStateOf(1f)
    var viewport: IntSize = IntSize.Zero

    fun reset() {
        scale = 1f; tx = 0f; ty = 0f; settledScale = 1f
    }

    private fun applyVertical(desired: Float, list: LazyListState) {
        val minTy = -(scale - 1f) * viewport.height
        val clamped = desired.coerceIn(minTy, 0f)
        val overflow = desired - clamped
        ty = clamped
        if (overflow != 0f) list.dispatchRawDelta(-overflow / scale)
    }

    fun panBy(dx: Float, dy: Float, list: LazyListState) {
        tx = (tx + dx).coerceIn(-(scale - 1f) * viewport.width, 0f)
        applyVertical(ty + dy, list)
    }

    fun zoomBy(factor: Float, centroid: Offset, list: LazyListState) {
        val newScale = (scale * factor).coerceIn(1f, MAX_ZOOM)
        val f = newScale / scale
        val newTx = centroid.x - (centroid.x - tx) * f
        val newTy = centroid.y - (centroid.y - ty) * f
        scale = newScale
        if (scale <= 1.001f) {
            scale = 1f; tx = 0f; ty = 0f
            return
        }
        tx = newTx.coerceIn(-(scale - 1f) * viewport.width, 0f)
        applyVertical(newTy, list)
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun PdfViewerScreen(
    file: RecentFile,
    controller: PdfController,
    initialPage: Int,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onPageChanged: (Int) -> Unit,
    edit: PdfEditActions,
    onSaveCopy: () -> Unit,
    startInEdit: Boolean = false,
) {
    val extra = LocalAyazColors.current
    val es = edit.state
    var editing by rememberSaveable { mutableStateOf(startInEdit) }
    var tool by rememberSaveable { mutableStateOf(if (startInEdit) PdfTool.TEXT else PdfTool.PEN) }
    var penColor by rememberSaveable { mutableIntStateOf(PEN_COLORS[0]) }
    var sizeIndex by rememberSaveable { mutableIntStateOf(1) }
    var textTarget by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val guardedBack: () -> Unit = { if (es.dirty) confirmExit = true else onBack() }
    BackHandler(enabled = es.dirty) { confirmExit = true }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val zoom = remember { PdfZoomState() }
    val sizesVersion by controller.sizesVersion.collectAsStateWithLifecycle()
    var restored by rememberSaveable { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    // arama durumu
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PdfSearchHit>>(emptyList()) }
    var hitIndex by remember { mutableIntStateOf(-1) }
    var searching by remember { mutableStateOf(false) }
    var searchProgress by remember { mutableIntStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchedQuery by remember { mutableStateOf("") }

    LaunchedEffect(controller) { controller.preloadSizes() }

    LaunchedEffect(controller) {
        if (!restored) {
            restored = true
            if (initialPage in 1 until controller.pageCount) listState.scrollToItem(initialPage)
        }
    }

    // Görünür sayfa: ekran ortasındaki öğe (yakınlaştırma hesaba katılarak).
    val currentPage by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) 0 else {
                val centerY = ((-zoom.ty + info.viewportSize.height / 2f) / zoom.scale).roundToInt()
                items.firstOrNull { centerY >= it.offset && centerY < it.offset + it.size + 12 }?.index
                    ?: listState.firstVisibleItemIndex
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { currentPage }.distinctUntilChanged().debounce(400).collectLatest { onPageChanged(it) }
    }
    // Görüntüleyiciden çıkarken (veya döndürmede) son sayfa beklemeden kaydedilir.
    val latestOnPageChanged by rememberUpdatedState(onPageChanged)
    DisposableEffect(Unit) {
        onDispose { latestOnPageChanged(currentPage) }
    }
    var indicatorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress to currentPage }.collectLatest {
            indicatorVisible = true
            delay(2200)
            indicatorVisible = false
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        if (!controller.isSearchSupported) {
            scope.launch {
                snackbar.showSnackbar("Bu cihazda PDF içi metin arama desteklenmiyor (Android 15 veya güncel sistem bileşeni gerekir).")
            }
            return
        }
        searchJob?.cancel()
        searchJob = scope.launch {
            searching = true
            searchProgress = 0
            hits = emptyList()
            hitIndex = -1
            val result = controller.search(q) { searchProgress = it }
            searching = false
            searchedQuery = q
            hits = result
            if (result.isEmpty()) {
                snackbar.showSnackbar("\"$q\" bulunamadı. Taranmış (görüntü) belgelerde metin araması yapılamaz.")
            } else {
                hitIndex = result.indexOfFirst { it.page >= currentPage }.let { if (it < 0) 0 else it }
                listState.animateScrollToItem(result[hitIndex].page)
            }
        }
    }

    LaunchedEffect(es.message) {
        es.message?.let { snackbar.showSnackbar(it); es.message = null }
    }

    fun gotoHit(delta: Int) {
        if (hits.isEmpty()) return
        hitIndex = (hitIndex + delta + hits.size) % hits.size
        scope.launch { listState.animateScrollToItem(hits[hitIndex].page) }
    }

    Scaffold(
        containerColor = extra.viewerBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (searchOpen) {
                PdfSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = ::runSearch,
                    onClose = {
                        searchOpen = false; searchJob?.cancel(); hits = emptyList(); hitIndex = -1; searching = false
                    },
                    status = when {
                        searching -> "$searchProgress/${controller.pageCount}"
                        hits.isNotEmpty() -> "${hitIndex + 1}/${hits.size}"
                        searchedQuery.isNotEmpty() && searchedQuery == query.trim() -> "0"
                        else -> ""
                    },
                    searching = searching,
                    onPrev = { gotoHit(-1) },
                    onNext = { gotoHit(1) },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${controller.pageCount} sayfa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = guardedBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                    actions = {
                        if (editing) {
                            IconButton(onClick = edit.undo, enabled = es.canUndo && !es.busy) { Icon(AyazIcons.Undo, "Geri al") }
                            IconButton(onClick = edit.save, enabled = es.dirty && !es.busy) { Icon(AyazIcons.Save, "Kaydet") }
                            IconButton(onClick = { editing = false }) { Icon(Icons.Filled.Check, "Düzenlemeyi bitir") }
                            return@TopAppBar
                        }
                        IconButton(onClick = { searchOpen = true }) { Icon(Icons.Filled.Search, "Belgede ara") }
                        IconButton(onClick = { editing = true; zoom.reset() }) { Icon(Icons.Filled.Edit, "Düzenle") }
                        IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Paylaş") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Diğer") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Sayfaya git") },
                                    leadingIcon = { Icon(AyazIcons.Pages, null) },
                                    onClick = { menu = false; showGoTo = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Yakınlaştırmayı sıfırla") },
                                    onClick = { menu = false; zoom.reset() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Telefona kaydet") },
                                    leadingIcon = { Icon(AyazIcons.Save, null) },
                                    onClick = { menu = false; onSaveCopy() },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        },
        bottomBar = {
            if (editing) {
                PdfEditToolbar(
                    tool = tool,
                    onTool = { tool = it; if (it == PdfTool.HIGHLIGHTER && penColor == PEN_COLORS[0]) penColor = PEN_COLORS[4] },
                    color = penColor,
                    onColor = { penColor = it },
                    sizeIndex = sizeIndex,
                    onSize = { sizeIndex = it },
                    busy = es.busy,
                    onRotate = { edit.pageOp(PageOp.Rotate(currentPage, 90)) },
                    onDeletePage = { confirmDelete = true },
                    canDelete = controller.pageCount > 1,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ZoomablePages(
                controller, listState, zoom, sizesVersion, hits, hitIndex,
                editing = editing,
                tool = tool,
                marks = es.marks,
                strokeColor = penColor,
                strokeWidth = if (tool == PdfTool.HIGHLIGHTER) HIGHLIGHT_WIDTHS[sizeIndex] else PEN_WIDTHS[sizeIndex],
                onStroke = { edit.add(it) },
                onTapPage = { page, pt ->
                    when (tool) {
                        PdfTool.TEXT -> textTarget = page to pt
                        PdfTool.ERASER -> es.marks.lastOrNull { it.page == page && hitsMark(it, pt) }?.let { edit.remove(it) }
                        else -> {}
                    }
                },
            )
            if (es.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            val indicatorAlpha by animateFloatAsState(if (indicatorVisible || searchOpen) 1f else 0f, tween(300), label = "indicator")
            Box(
                // Scaffold dolgusu gezinme çubuğunu zaten içerir; ayrıca eklenirse gösterge fazla yukarıda kalır.
                Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                    .graphicsLayer { alpha = indicatorAlpha },
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(Color(0xE6101014))
                        .clickable { showGoTo = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Sayfa ${currentPage + 1} / ${controller.pageCount}" },
                ) {
                    Text("${currentPage + 1} / ${controller.pageCount}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    textTarget?.let { (page, pt) ->
        TextNoteDialog(
            onDismiss = { textTarget = null },
            onConfirm = { text ->
                textTarget = null
                if (text.isNotBlank()) edit.add(TextMark(page, pt.x, pt.y, text, penColor, TEXT_SIZES[sizeIndex]))
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Sayfa ${currentPage + 1} silinsin mi?") },
            text = { Text("Sayfa belgeden kaldırılır. Kaydetmeden önce \"Geri al\" ile geri getirebilirsiniz.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; edit.pageOp(PageOp.Delete(currentPage)) }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
    if (confirmExit) {
        com.ayaz.viewer.viewer.UnsavedChangesDialog(
            onSave = { confirmExit = false; edit.save() },
            onDiscard = { confirmExit = false; onBack() },
            onDismiss = { confirmExit = false },
        )
    }

    if (showGoTo) {
        GoToPageDialog(
            pageCount = controller.pageCount,
            current = currentPage + 1,
            onDismiss = { showGoTo = false },
            onGo = { p ->
                showGoTo = false
                zoom.reset()
                scope.launch { listState.scrollToItem(p - 1) }
            },
        )
    }
}

@Composable
private fun ZoomablePages(
    controller: PdfController,
    listState: LazyListState,
    zoom: PdfZoomState,
    sizesVersion: Int,
    hits: List<PdfSearchHit>,
    hitIndex: Int,
    editing: Boolean = false,
    tool: PdfTool = PdfTool.PEN,
    marks: List<PdfMark> = emptyList(),
    strokeColor: Int = 0,
    strokeWidth: Float = 2f,
    onStroke: (InkMark) -> Unit = {},
    onTapPage: (Int, Offset) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val livePoints = remember { mutableStateListOf<Offset>() }
    var livePage by remember { mutableIntStateOf(-1) }
    val color by rememberUpdatedState(strokeColor)
    val width by rememberUpdatedState(strokeWidth)
    val strokeCallback by rememberUpdatedState(onStroke)
    val tapCallback by rememberUpdatedState(onTapPage)
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val sidePadding = 8.dp
    val pageWidthPx = with(density) { (viewport.width - 2 * sidePadding.roundToPx()).coerceAtLeast(1) }
    val listScrollEnabled by remember(editing) { derivedStateOf { zoom.scale <= 1f && !editing } }
    val sidePx = with(density) { sidePadding.toPx() }

    /** Ekran noktası → (sayfa, sayfa noktası). [onlyPage] verilirse o sayfaya göre (sınırlandırılmış). */
    fun locate(pos: Offset, onlyPage: Int = -1): Pair<Int, Offset>? {
        val info = listState.layoutInfo
        val cy = (pos.y - zoom.ty) / zoom.scale
        val cx = (pos.x - zoom.tx) / zoom.scale
        val item = if (onlyPage >= 0) info.visibleItemsInfo.firstOrNull { it.index == onlyPage }
        else info.visibleItemsInfo.firstOrNull { cy >= it.offset && cy < it.offset + it.size }
        item ?: return null
        val localX = cx - sidePx
        val localY = cy - item.offset
        val pw = (viewport.width - 2 * sidePx).coerceAtLeast(1f)
        if (onlyPage < 0 && (localX < 0 || localX > pw)) return null
        val ptsPerPx = controller.pageWidthPoints(item.index) / pw
        val maxY = item.size * ptsPerPx
        return item.index to Offset(
            (localX * ptsPerPx).coerceIn(0f, pw * ptsPerPx),
            (localY * ptsPerPx).coerceIn(0f, maxY),
        )
    }

    fun settle() {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(250)
            zoom.settledScale = zoom.scale
        }
    }

    fun animateZoomTo(target: Float, focus: Offset) {
        scope.launch {
            val anim = Animatable(zoom.scale)
            anim.animateTo(target, tween(220)) {
                val factor = value / zoom.scale
                if (factor != 1f) zoom.zoomBy(factor, focus, listState)
            }
            if (target <= 1f) zoom.reset()
            settle()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged {
                if (it != viewport) {
                    viewport = it
                    zoom.viewport = it
                    zoom.reset()
                }
            }
            .pointerInput(listState, editing, tool, controller) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    flingJob?.cancel()
                    val tracker = VelocityTracker()
                    var panning = false
                    var multi = false
                    var slop = 0f
                    val drawing = editing && (tool == PdfTool.PEN || tool == PdfTool.HIGHLIGHTER)
                    var strokePage = -1
                    if (drawing) {
                        locate(down.position)?.let { (pg, pt) ->
                            strokePage = pg
                            livePage = pg
                            livePoints.clear()
                            livePoints.add(pt)
                            down.consume()
                        }
                    }
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2 && strokePage >= 0) {
                            // ikinci parmak: çizimden vazgeç, kaydır/yakınlaştır
                            strokePage = -1
                            livePage = -1
                            livePoints.clear()
                        }
                        if (strokePage >= 0) {
                            val change = pressed.first()
                            locate(change.position, strokePage)?.let { (_, pt) -> livePoints.add(pt) }
                            change.consume()
                            continue
                        }
                        if (pressed.size >= 2) {
                            multi = true
                            val factor = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (factor != 1f) zoom.zoomBy(factor, centroid, listState)
                            if (zoom.scale > 1f) zoom.panBy(pan.x, pan.y, listState)
                            event.changes.forEach { it.consume() }
                        } else if (zoom.scale > 1f || editing) {
                            val change = pressed.first()
                            val delta = change.positionChange()
                            if (!panning) {
                                slop += delta.getDistance()
                                if (slop > viewConfiguration.touchSlop) panning = true
                            }
                            if (panning) {
                                zoom.panBy(delta.x, delta.y, listState)
                                tracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                            }
                        }
                    }
                    if (strokePage >= 0) {
                        if (livePoints.isNotEmpty()) {
                            strokeCallback(
                                InkMark(strokePage, livePoints.toList(), color, width, tool == PdfTool.HIGHLIGHTER),
                            )
                        }
                        livePage = -1
                        livePoints.clear()
                    } else if (editing && !panning && !multi && (tool == PdfTool.TEXT || tool == PdfTool.ERASER)) {
                        locate(down.position)?.let { (pg, pt) -> tapCallback(pg, pt) }
                    }
                    if (multi) settle()
                    if (panning && !multi && (zoom.scale > 1f || editing)) {
                        val v = tracker.calculateVelocity()
                        flingJob = scope.launch {
                            val ax = Animatable(0f)
                            val ay = Animatable(0f)
                            var lastX = 0f
                            var lastY = 0f
                            launch {
                                ax.animateDecay(v.x, exponentialDecay()) {
                                    zoom.panBy(value - lastX, 0f, listState); lastX = value
                                }
                            }
                            launch {
                                ay.animateDecay(v.y, exponentialDecay()) {
                                    zoom.panBy(0f, value - lastY, listState); lastY = value
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(listState, editing) {
                if (!editing) {
                    detectTapGestures(onDoubleTap = { pos ->
                        if (zoom.scale > 1.05f) animateZoomTo(1f, pos) else animateZoomTo(2.5f, pos)
                    })
                }
            },
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = listScrollEnabled,
            contentPadding = PaddingValues(horizontal = sidePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.tx
                    translationY = zoom.ty
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            items(controller.pageCount, key = { it }) { index ->
                val pageHits = remember(hits, index) { hits.filter { it.page == index } }
                val current = hits.getOrNull(hitIndex)?.takeIf { it.page == index }
                val pageMarks = marks.filter { it.page == index }
                val live = if (livePage == index) livePoints else null
                PdfPageItem(
                    controller, index, pageWidthPx, zoom.settledScale, sizesVersion, pageHits, current,
                    pageMarks, live, if (live != null) color else 0, width, tool == PdfTool.HIGHLIGHTER,
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    controller: PdfController,
    index: Int,
    pageWidthPx: Int,
    settledScale: Float,
    sizesVersion: Int,
    hits: List<PdfSearchHit>,
    currentHit: PdfSearchHit?,
    marks: List<PdfMark> = emptyList(),
    livePoints: List<Offset>? = null,
    liveColor: Int = 0,
    liveWidth: Float = 2f,
    liveHighlighter: Boolean = false,
) {
    val renderScale = settledScale.coerceIn(1f, 3f)
    val targetWidth = (pageWidthPx * renderScale).roundToInt()
    // Denetleyici değişince (kaydetme sonrası yeniden açma, sayfa döndürme/silme) sayfa yeni dosyadan
    // yeniden çizilmeli; aksi halde eski görüntü ekranda kalır ve kaydedilen çizimler görünmez.
    var bitmap by remember(controller, index) { mutableStateOf(controller.cached(index, targetWidth) ?: controller.bestCached(index)) }
    LaunchedEffect(controller, index, targetWidth) {
        if (pageWidthPx < 50) return@LaunchedEffect // yerleşim henüz ölçülmedi
        // Kısa bir gecikme: hızlı kaydırmada ekrandan çıkan sayfalar hiç çizilmez.
        if (controller.cached(index, targetWidth) == null) delay(30)
        controller.render(index, targetWidth)?.let { bitmap = it }
    }
    val aspect = remember(controller, index, sizesVersion) { controller.aspect(index) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / aspect)
            .shadow(3.dp, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .semantics { contentDescription = "Sayfa ${index + 1}" },
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            val image = remember(b) { b.asImageBitmap() }
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        } else {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = Color(0xFF8B8B95))
        }
        if (hits.isNotEmpty()) {
            val pageWidthPts = controller.pageWidthPoints(index)
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / pageWidthPts
                hits.forEach { hit ->
                    val strong = hit === currentHit
                    hit.rects.forEach { r ->
                        drawRect(
                            color = if (strong) Color(0x99FF9800) else Color(0x55FFEB3B),
                            topLeft = Offset(r.left * s, r.top * s),
                            size = Size(r.width() * s, r.height() * s),
                        )
                    }
                }
            }
        }
        if (marks.isNotEmpty() || livePoints != null) {
            val pageWidthPts = controller.pageWidthPoints(index)
            Canvas(Modifier.fillMaxSize()) {
                val sc = size.width / pageWidthPts
                marks.forEach { drawMark(it, sc) }
                if (livePoints != null && livePoints.isNotEmpty()) {
                    drawInk(livePoints, liveColor, liveWidth, liveHighlighter, sc)
                }
            }
        }
    }
}

@Composable
private fun PdfSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    status: String,
    searching: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Aramayı kapat") }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text("Belgede ara") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
        )
        if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp))
        }
        IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, "Önceki eşleşme") }
        IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, "Sonraki eşleşme") }
    }
}

@Composable
fun GoToPageDialog(pageCount: Int, current: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sayfaya git") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { t -> text = t.filter { it.isDigit() }.take(6) },
                label = { Text("Sayfa (1–$pageCount)") },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(value!!) }),
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onGo(value!!) }) { Text("Git") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}


// ====================================================================== düzenleme yardımcıları

private fun DrawScope.drawInk(points: List<Offset>, color: Int, width: Float, highlighter: Boolean, sc: Float) {
    val path = androidx.compose.ui.graphics.Path()
    path.moveTo(points[0].x * sc, points[0].y * sc)
    if (points.size == 1) path.lineTo(points[0].x * sc + 0.1f, points[0].y * sc)
    for (i in 1 until points.size) path.lineTo(points[i].x * sc, points[i].y * sc)
    drawPath(
        path,
        color = Color(color).copy(alpha = if (highlighter) 0.4f else 1f),
        style = Stroke(
            width = width * sc,
            cap = if (highlighter) StrokeCap.Square else StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
        blendMode = if (highlighter) BlendMode.Multiply else BlendMode.SrcOver,
    )
}

private fun DrawScope.drawMark(m: PdfMark, sc: Float) {
    when (m) {
        is InkMark -> if (m.points.isNotEmpty()) drawInk(m.points, m.color, m.width, m.highlighter, sc)
        is TextMark -> drawIntoCanvas { c ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = m.color
                textSize = m.size * sc
                typeface = android.graphics.Typeface.SANS_SERIF
            }
            val leading = m.size * 1.25f
            m.text.replace("\r", "").split('\n').forEachIndexed { i, line ->
                c.nativeCanvas.drawText(line, m.x * sc, (m.y + m.size + i * leading) * sc, paint)
            }
        }
    }
}

/** Silgi için: dokunulan nokta işaretlemenin üzerinde mi (sayfa noktası cinsinden). */
fun hitsMark(m: PdfMark, p: Offset): Boolean = when (m) {
    is InkMark -> {
        val tol = m.width / 2 + 10f
        m.points.any { (it - p).getDistance() <= tol } ||
            m.points.zipWithNext().any { (a, b) -> distanceToSegment(p, a, b) <= tol }
    }
    is TextMark -> {
        val lines = m.text.split('\n')
        val w = lines.maxOf { it.length } * m.size * 0.6f
        val h = lines.size * m.size * 1.25f + m.size * 0.3f
        p.x in (m.x - 6f)..(m.x + w + 6f) && p.y in (m.y - 6f)..(m.y + h + 6f)
    }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    return (p - Offset(a.x + ab.x * t, a.y + ab.y * t)).getDistance()
}

@Composable
private fun PdfEditToolbar(
    tool: PdfTool,
    onTool: (PdfTool) -> Unit,
    color: Int,
    onColor: (Int) -> Unit,
    sizeIndex: Int,
    onSize: (Int) -> Unit,
    busy: Boolean,
    onRotate: () -> Unit,
    onDeletePage: () -> Unit,
    canDelete: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PdfTool.entries.forEach { t ->
                val selected = t == tool
                val icon = when (t) {
                    PdfTool.PEN -> Icons.Filled.Create
                    PdfTool.HIGHLIGHTER -> AyazIcons.Highlighter
                    PdfTool.TEXT -> AyazIcons.TextFields
                    PdfTool.ERASER -> AyazIcons.Eraser
                }
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onTool(t) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Araç: ${t.label}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(t.label, style = MaterialTheme.typography.labelSmall)
                }
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = onRotate, enabled = !busy) { Icon(AyazIcons.RotateRight, "Sayfayı döndür") }
            IconButton(onClick = onDeletePage, enabled = !busy && canDelete) { Icon(Icons.Filled.Delete, "Sayfayı sil") }
        }
        if (tool != PdfTool.ERASER) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PEN_COLORS.forEach { c ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(28.dp).clip(CircleShape).background(Color(c))
                            .border(if (c == color) 3.dp else 1.dp, if (c == color) MaterialTheme.colorScheme.primary else Color(0x55888888), CircleShape)
                            .clickable { onColor(c) }
                            .semantics { contentDescription = "Renk ${PEN_COLORS.indexOf(c) + 1}" },
                    )
                }
                Box(Modifier.weight(1f))
                listOf("İnce", "Orta", "Kalın").forEachIndexed { i, label ->
                    val sel = i == sizeIndex
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onSize(i) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Text(
            when (tool) {
                PdfTool.PEN, PdfTool.HIGHLIGHTER -> "Tek parmakla çizin; iki parmakla kaydırın veya yakınlaştırın."
                PdfTool.TEXT -> "Metin eklemek istediğiniz yere dokunun."
                PdfTool.ERASER -> "Silmek için kaydedilmemiş bir çizime veya metne dokunun."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }
}

@Composable
private fun TextNoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Metin ekle") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Not yazın") },
                minLines = 2,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Ekle") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
