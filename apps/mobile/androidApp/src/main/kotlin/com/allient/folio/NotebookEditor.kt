package com.allient.folio

import androidx.compose.material3.FilterChip
import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import android.content.Intent
import android.net.Uri
import android.media.MediaPlayer
import kotlinx.coroutines.delay

@Composable
internal fun NotebookEditor(
    notebook: Notebook,
    onBack: () -> Unit,
    onNotebookChange: (Notebook) -> Unit,
    repository: NotebookViewModel,
    onNavigateNotebook: (String, String?) -> Unit = { _, _ -> },
    isSaving: Boolean = false,
    saveError: String? = null,
    backLabel: String = "Back to notebooks",
    startInFocusMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("editor", Context.MODE_PRIVATE) }
    val files = remember { NotebookFiles(context) }
    val latestNotebook by rememberUpdatedState(notebook)
    val latestOnChange by rememberUpdatedState(onNotebookChange)
    var pageIndex by rememberSaveable(notebook.id) { mutableStateOf(notebook.pages.indexOfFirst { it.id == notebook.lastViewedPageId }.coerceAtLeast(0)) }
    val legacyBrushes = remember(preferences) { brushes.filterNot { it.eraser }.map { brush ->
        val width = preferences.getFloat("brush-${brush.name}-width", brush.width)
        brush.copy(width = if (width.isFinite()) width.coerceIn(.5f, 32f) else brush.width,
            color = preferences.getInt("brush-${brush.name}-color", brush.color))
    } }
    val savedBrushes = remember { runCatching {
        val json = org.json.JSONArray(preferences.getString("brush-presets-v2", "[]"))
        (0 until json.length()).map { index -> val item = json.getJSONObject(index)
            val type = brushes.first { it.name == item.getString("type") && !it.eraser }
                type.copy(width = item.getDouble("width").toFloat().coerceIn(BrushCatalog.widthRange(type.name)), color = item.getInt("color"),
                sensitivity = item.optDouble("sensitivity", .5).toFloat().coerceIn(0f, 1f), presetId = item.getString("id"), label = item.optString("label", type.name),
                texture = item.optDouble("texture", .65).toFloat(), grain = item.optDouble("grain", 1.0).toFloat(),
                angle = item.optDouble("angle", 0.0).toFloat(), variant = item.optString("variant", ""))
        }.distinctBy { it.presetId }.ifEmpty { legacyBrushes }
    }.getOrDefault(legacyBrushes) }
    var selectedBrush by remember(notebook.id) { mutableStateOf(savedBrushes.first()) }
    val orderedBrushes = remember(savedBrushes) {
        val ids = (0 until savedBrushes.size).mapNotNull { preferences.getString("brush-preset-id-$it", null) }
        (ids.mapNotNull { id -> savedBrushes.firstOrNull { it.presetId == id } } + savedBrushes.filter { brush -> ids.none { it == brush.presetId } }).distinctBy { it.presetId }
    }
    var presets by remember(notebook.id) { mutableStateOf(orderedBrushes) }
    var brushOptions by remember(notebook.id) { mutableStateOf(savedBrushes) }
    var previousPen by remember(notebook.id) { mutableStateOf(savedBrushes.first()) }
    var brushDragRemainder by remember { mutableFloatStateOf(0f) }
    var showBrush by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(startInFocusMode) }
    var pageLayout by remember { mutableStateOf(preferences.getString("pageLayout", "Single Page") ?: "Single Page") }
    val effectivePageLayout = if (notebook.infiniteCanvas) "Infinite" else pageLayout
    var showPageBoundary by remember { mutableStateOf(preferences.getBoolean("showPageBoundary", false)) }
    var singleFingerSwipe by remember { mutableStateOf(preferences.getBoolean("singleFingerSwipe", true)) }
    var horizontalPageSwipe by remember { mutableStateOf(preferences.getBoolean("horizontalPageSwipe", true)) }
    var longPressSelectEnabled by remember { mutableStateOf(preferences.getBoolean("longPressSelectEnabled", false)) }
    var pinchToZoom by remember { mutableStateOf(preferences.getBoolean("pinchToZoom", true)) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var templateCategory by rememberSaveable { mutableStateOf("General") }
    var applyTemplateToAll by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val recording = repository.recordingNotebookId == notebook.id && repository.recordingStatus != RecordingStatus.Idle
    var recordingElapsed by remember(notebook.id) { mutableStateOf(0L) }
    LaunchedEffect(repository.recordingStatus, notebook.id) {
        while (repository.recordingNotebookId == notebook.id && repository.recordingStatus != RecordingStatus.Idle) {
            recordingElapsed = repository.currentRecordingElapsedMillis()
            delay(250)
        }
    }
    var pendingExportPath by rememberSaveable { mutableStateOf<String?>(null) }
    var importKind by rememberSaveable { mutableStateOf<String?>(null) }
    var importPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var importLayerId by rememberSaveable { mutableStateOf("ink") }
    var pagePlacement by rememberSaveable { mutableStateOf("After") }
    var clipboard by remember { mutableStateOf(emptyList<InkStroke>()) }
    var textClipboard by remember { mutableStateOf("") }
    var actionPageId by remember { mutableStateOf<String?>(null) }
    var editingTextId by rememberSaveable { mutableStateOf<String?>(null) }
    // Corner of the tool chip (0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right); draggable so it never hides ink.
    var toolChipCorner by remember { mutableIntStateOf(preferences.getInt("tool-chip-corner", 2)) }
    var threshold by remember { mutableFloatStateOf(preferences.getFloat("threshold", pressureProfiles[1].threshold)) }
    var pressureCurveEnabled by remember { mutableStateOf(preferences.getBoolean("pressureCurveEnabled", true)) }
    var pressureCurve by remember { mutableStateOf(preferences.loadResponseCurve(CurveChannel.Pressure, defaultPressureCurve)) }
    var velocityCurveEnabled by remember { mutableStateOf(preferences.getBoolean("velocityCurveEnabled", false)) }
    var velocityCurve by remember { mutableStateOf(preferences.loadResponseCurve(CurveChannel.Velocity, defaultVelocityCurve, velocityCurveEnabled)) }
    var tiltCurveEnabled by remember { mutableStateOf(preferences.getBoolean("tiltCurveEnabled", false)) }
    var tiltCurve by remember { mutableStateOf(preferences.loadResponseCurve(CurveChannel.Tilt, defaultTiltCurve, tiltCurveEnabled)) }
    var profileName by remember { mutableStateOf(preferences.getString("profile", "Balanced") ?: "Balanced") }
    var fingerDrawing by remember { mutableStateOf(preferences.getBoolean("fingerDrawing", true)) }
    var railOnRight by remember { mutableStateOf(preferences.getBoolean("railOnRight", false)) }
    var toolbarTools by remember {
        val newTools = setOf("Table", "Stickers", "Sticky", "Ruler")
        val saved = preferences.getStringSet("toolbarTools", null)?.toSet()
        // Layouts saved before these tools existed keep them visible once, then follow the user's choice.
        mutableStateOf(when {
            saved == null -> setOf("Template", "Layers", "Shapes", "Lasso", "Fill", "Pressure", "Gestures", "Zoom", "Page layout", "Text", "Insert", "Page +", "Share") + newTools
            !preferences.getBoolean("toolbarBlockToolsAdded", false) -> saved + newTools
            else -> saved
        })
    }
    var mode by remember { mutableStateOf(CanvasMode.Write) }
    var shape by remember { mutableStateOf(ShapeKind.Rectangle) }
    var rulerOn by remember { mutableStateOf(false) }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var tableDraft by remember { mutableStateOf(TableDraft()) }
    var editingStickyId by remember { mutableStateOf<String?>(null) }
    var cellSelection by remember { mutableStateOf<List<Int>?>(null) }
    var cellAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var zoomBox by remember { mutableStateOf<android.graphics.RectF?>(null) }
    var zoomPanelSize by remember { mutableStateOf(IntSize.Zero) }
    var shapeColor by remember { mutableIntStateOf(preferences.getInt("shapeColor", 0xFF252525.toInt())) }
    var shapeWidth by remember { mutableFloatStateOf(preferences.getFloat("shapeWidth", 3f).takeIf { it.isFinite() }?.coerceIn(.5f, 32f) ?: 3f) }
    var shapeLineStyle by remember { mutableStateOf(runCatching { ShapeLineStyle.valueOf(preferences.getString("shapeLineStyle", "Solid")!!) }.getOrDefault(ShapeLineStyle.Solid)) }
    var fillColor by remember { mutableIntStateOf(preferences.getInt("fillColor", 0xFF252525.toInt())) }
    var pageColorReceiver by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var colorReturnMode by remember { mutableStateOf(CanvasMode.Write) }
    var colorReturnSheet by remember { mutableStateOf<String?>(null) }
    var eraserMode by remember { mutableStateOf(runCatching { EraserMode.valueOf(preferences.getString("eraserMode", "Stroke")!!) }.getOrDefault(EraserMode.Stroke)) }
    var eraserWidth by remember { mutableFloatStateOf(preferences.getFloat("eraserWidth", 22f).coerceIn(4f, 80f)) }
    var eraseActiveLayerOnly by remember { mutableStateOf(preferences.getBoolean("eraseActiveLayerOnly", true)) }
    var autoReturnToPen by remember { mutableStateOf(preferences.getBoolean("autoReturnToPen", false)) }
    var lassoKind by remember { mutableStateOf(LassoKind.Freeform) }
    var lassoAutoSwitch by remember { mutableStateOf(false) }
    var shapeFilled by remember { mutableStateOf(preferences.getBoolean("shapeFilled", false)) }
    var shapeFillColor by remember { mutableIntStateOf(preferences.getInt("shapeFillColor", 0xFFE5EFEC.toInt())) }
    val safeIndex = pageIndex.coerceIn(0, notebook.pages.lastIndex)
    val pageListState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val savedPage = notebook.pages[safeIndex]
    var page by remember(savedPage.id) { mutableStateOf(savedPage) }
    val editingText = page.texts.firstOrNull { it.id == editingTextId }
    var undoStack by remember(savedPage.id) { mutableStateOf(emptyList<NotebookPage>()) }
    var redoStack by remember(savedPage.id) { mutableStateOf(emptyList<NotebookPage>()) }
    var activeLayer by remember(savedPage.id) { mutableStateOf(savedPage.layers.first().id) }
    var selectedIds by remember(savedPage.id) { mutableStateOf(emptySet<String>()) }
    var selectedObjectId by remember(savedPage.id) { mutableStateOf<String?>(null) }
    var selectedObjectIsText by remember(savedPage.id) { mutableStateOf(false) }
    var activeAudio by remember(notebook.id) { mutableStateOf<NoteAttachment?>(null) }
    var markerToDelete by remember { mutableStateOf<String?>(null) }
    var canvas by remember(savedPage.id) { mutableStateOf<InkCanvasView?>(null) }
    val zoomRatio = run {
        val w = canvas?.width ?: 0; val h = canvas?.height ?: 0
        if (w > 0 && h > 0 && zoomPanelSize.width > 0 && zoomPanelSize.height > 0) zoomPanelSize.height.toFloat() * w / (zoomPanelSize.width.toFloat() * h) else .5f
    }
    LaunchedEffect(zoomRatio) {
        zoomBox = zoomBox?.let { b -> android.graphics.RectF(b.left, b.top, b.right, b.top + b.width() * zoomRatio) }
    }
    var drawingViewportWidthPx by remember { mutableIntStateOf(0) }
    val panelXdpi = context.resources.displayMetrics.xdpi
    var zoom by remember(savedPage.id) { mutableFloatStateOf(1f) }
    var viewportCenterX by remember(savedPage.id) { mutableFloatStateOf(.5f) }
    var viewportCenterY by remember(savedPage.id) { mutableFloatStateOf(.5f) }
    var pendingPin by remember { mutableStateOf<Offset?>(null) }
    var editingPin by remember { mutableStateOf<CanvasPin?>(null) }
    var pinName by remember { mutableStateOf("") }
    var pinMenu by remember { mutableStateOf<String?>(null) }
    val initialPin = remember(notebook.id) { notebook.pins.firstOrNull { it.id == notebook.lastViewedPageId } }
    LaunchedEffect(canvas, initialPin?.id) { initialPin?.let { canvas?.jumpTo(it.x,it.y,it.zoom) } }

    LaunchedEffect(threshold, pressureCurve, pressureCurveEnabled, velocityCurveEnabled, velocityCurve, tiltCurveEnabled, tiltCurve, profileName, fingerDrawing, railOnRight) {
        preferences.edit().putFloat("threshold", threshold)
            .putBoolean("pressureCurveEnabled", pressureCurveEnabled).putResponseCurve(CurveChannel.Pressure, pressureCurve)
            .putBoolean("velocityCurveEnabled", velocityCurveEnabled).putResponseCurve(CurveChannel.Velocity, velocityCurve)
            .putBoolean("tiltCurveEnabled", tiltCurveEnabled).putResponseCurve(CurveChannel.Tilt, tiltCurve)
            .putString("profile", profileName).putBoolean("fingerDrawing", fingerDrawing).putBoolean("railOnRight", railOnRight).apply()
    }
    LaunchedEffect(pageLayout, showPageBoundary, singleFingerSwipe) {
        preferences.edit().putString("pageLayout", pageLayout).putBoolean("showPageBoundary", showPageBoundary)
            .putBoolean("singleFingerSwipe", singleFingerSwipe).apply()
    }
    LaunchedEffect(pinchToZoom) { preferences.edit().putBoolean("pinchToZoom", pinchToZoom).apply() }
    LaunchedEffect(horizontalPageSwipe, longPressSelectEnabled) {
        preferences.edit().putBoolean("horizontalPageSwipe", horizontalPageSwipe).putBoolean("longPressSelectEnabled", longPressSelectEnabled).apply()
    }
    LaunchedEffect(toolbarTools) { preferences.edit().putStringSet("toolbarTools", toolbarTools).putBoolean("toolbarBlockToolsAdded", true).apply() }
    LaunchedEffect(eraserMode, eraserWidth, eraseActiveLayerOnly, autoReturnToPen) {
        preferences.edit().putString("eraserMode", eraserMode.name).putFloat("eraserWidth", eraserWidth)
            .putBoolean("eraseActiveLayerOnly", eraseActiveLayerOnly).putBoolean("autoReturnToPen", autoReturnToPen).apply()
    }
    LaunchedEffect(brushOptions, presets) {
        preferences.edit().apply {
            putString("brush-presets-v2", org.json.JSONArray().apply { brushOptions.forEach { brush -> put(org.json.JSONObject().apply {
                put("id", brush.presetId); put("type", brush.name); put("label", brush.label); put("width", brush.width); put("color", brush.color); put("sensitivity", brush.sensitivity)
                put("texture", brush.texture); put("grain", brush.grain); put("angle", brush.angle); put("variant", brush.variant)
            }) } }.toString())
            brushOptions.forEach { putFloat("brush-${it.name}-width", it.width); putInt("brush-${it.name}-color", it.color) }
            presets.forEachIndexed { index, brush -> putString("brush-preset-id-$index", brush.presetId); putString("brush-preset-$index", brush.name) }
        }.apply()
    }
    LaunchedEffect(shapeColor, shapeWidth, shapeLineStyle, fillColor, lassoAutoSwitch, shapeFilled, shapeFillColor) {
        preferences.edit().putInt("shapeColor", shapeColor).putFloat("shapeWidth", shapeWidth)
            .putString("shapeLineStyle", shapeLineStyle.name).putInt("fillColor", fillColor)
            .putBoolean("lassoAutoSwitch", lassoAutoSwitch)
            .putBoolean("shapeFilled", shapeFilled).putInt("shapeFillColor", shapeFillColor).apply()
    }
    LaunchedEffect(selectedBrush) { if (!selectedBrush.eraser) previousPen = selectedBrush }
    LaunchedEffect(selectedIds.isEmpty(), mode) {
        if (selectedIds.isEmpty() && mode == CanvasMode.MoveSelection) mode = CanvasMode.Lasso
    }
    LaunchedEffect(page.layers) {
        selectedIds = selectedIds.filter { id -> page.strokes.any { stroke -> stroke.id == id && page.layers.any { it.id == stroke.layerId && it.visible && !it.locked } } }.toSet()
        val objectLayer = if (selectedObjectIsText) page.texts.firstOrNull { it.id == selectedObjectId }?.layerId else page.images.firstOrNull { it.id == selectedObjectId }?.layerId
        if (selectedObjectId != null && page.layers.none { it.id == objectLayer && it.visible && !it.locked }) canvas?.deselectObject()
    }
    LaunchedEffect(notebook.id, savedPage.id) {
        val current = latestNotebook
        if (current.lastViewedPageId != savedPage.id && current.pages.any { it.id == savedPage.id }) {
            latestOnChange(current.copy(lastViewedPageId = savedPage.id))
        }
    }
    LaunchedEffect(savedPage) {
        if (savedPage != page) page = savedPage
        if (page.layers.none { it.id == activeLayer }) activeLayer = page.layers.first().id
    }
    LaunchedEffect(effectivePageLayout, pageIndex, notebook.pages.size) {
        val viewport = pageListState.layoutInfo
        val dominantIndex = viewport.visibleItemsInfo.maxByOrNull { item ->
            (minOf(item.offset + item.size, viewport.viewportEndOffset) - maxOf(item.offset, viewport.viewportStartOffset)).coerceAtLeast(0)
        }?.index
        if (effectivePageLayout == "Scroll" && dominantIndex != safeIndex) {
            pageListState.animateScrollToItem(safeIndex)
        }
    }
    LaunchedEffect(effectivePageLayout, notebook.pages.size) {
        snapshotFlow {
            val viewport = pageListState.layoutInfo
            viewport.visibleItemsInfo.maxByOrNull { item ->
                (minOf(item.offset + item.size, viewport.viewportEndOffset) - maxOf(item.offset, viewport.viewportStartOffset)).coerceAtLeast(0)
            }?.index
        }.collect { visibleIndex ->
            if (effectivePageLayout == "Scroll" && visibleIndex != null && visibleIndex in notebook.pages.indices && pageIndex != visibleIndex) {
                pageIndex = visibleIndex
            }
        }
    }
    fun replacePage(updated: NotebookPage) {
        val current = latestNotebook
        latestOnChange(current.copy(updatedAt = System.currentTimeMillis(), pages = current.pages.map { if (it.id == updated.id) updated else it }))
    }
    fun applyPage(updated: NotebookPage) {
        if (updated == page) return
        undoStack = (undoStack + page).takeLast(80)
        redoStack = emptyList()
        page = updated
        if (updated.layers.none { it.id == activeLayer }) activeLayer = updated.layers.first().id
        replacePage(updated)
    }
    fun pickPageColor(receiver: (Int) -> Unit) {
        canvas?.flushInput()
        colorReturnMode = mode; colorReturnSheet = sheet; pageColorReceiver = receiver
        showBrush = false; focusMode = false; sheet = null; mode = CanvasMode.ColorPicker
    }
    fun save() { canvas?.flushInput(); replacePage(page) }
    fun addPage(position: String = pagePlacement) {
        if (notebook.infiniteCanvas) { sheet = "pins"; return }
        canvas?.flushInput()
        val added = NotebookPage(template = page.template)
        val currentPages = notebook.pages.map { if (it.id == page.id) page else it }.toMutableList()
        val insertAt = when (position) { "Before" -> safeIndex; "Last Page" -> currentPages.size; else -> safeIndex + 1 }
        currentPages.add(insertAt.coerceIn(0, currentPages.size), added)
        onNotebookChange(notebook.copy(pages = currentPages, updatedAt = System.currentTimeMillis()))
        pageIndex = insertAt
    }
    fun openToolPanel(name: String) {
        if (notebook.infiniteCanvas && name in listOf("templates", "page layout", "page+", "pages")) { sheet = "pins"; return }
        if (mode == CanvasMode.Shape || mode == CanvasMode.Fill || mode == CanvasMode.Pan) {
            canvas?.flushInput(); mode = CanvasMode.Write; selectedIds = emptySet()
        }
        sheet = name
    }
    fun undo() {
        canvas?.flushInput()
        if (undoStack.isNotEmpty()) {
            redoStack = redoStack + page; page = undoStack.last(); undoStack = undoStack.dropLast(1); selectedIds = emptySet(); if (page.layers.none { it.id == activeLayer }) activeLayer = page.layers.first().id; replacePage(page)
        }
    }
    fun redo() {
        canvas?.flushInput()
        if (redoStack.isNotEmpty()) {
            undoStack = undoStack + page; page = redoStack.last(); redoStack = redoStack.dropLast(1); selectedIds = emptySet(); if (page.layers.none { it.id == activeLayer }) activeLayer = page.layers.first().id; replacePage(page)
        }
    }
    fun updateBrush(brush: BrushStyle) {
        selectedBrush = brush
        brushOptions = brushOptions.map { if (it.presetId == brush.presetId) brush else it }
        presets = presets.map { if (it.presetId == brush.presetId) brush else it }
        mode = CanvasMode.Write
    }
    fun addBrush() {
        val source = if (selectedBrush.eraser) previousPen else selectedBrush
        val added = source.copy(presetId = UUID.randomUUID().toString(), label = "${source.name} ${brushOptions.count { it.name == source.name } + 1}")
        brushOptions = brushOptions + added; presets = presets + added; selectedBrush = added; mode = CanvasMode.Write; showBrush = true
    }
    fun deleteBrush() {
        if (brushOptions.size <= 1) return
        brushOptions = brushOptions.filterNot { it.presetId == selectedBrush.presetId }; presets = presets.filterNot { it.presetId == selectedBrush.presetId }
        selectedBrush = presets.firstOrNull() ?: brushOptions.first(); mode = CanvasMode.Write
    }
    fun stopRecording() {
        canvas?.flushInput()
        repository.stopRecording(notebook.id)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val endForegroundInput by rememberUpdatedState({
        canvas?.flushInput()
        repository.stopRecording(notebook.id)
    })
    DisposableEffect(lifecycle, repository, notebook.id) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) endForegroundInput() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); endForegroundInput() }
    }
    DisposableEffect(savedPage.id) { onDispose { canvas?.flushInput() } }
    fun startRecording() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            activeAudio = null
            repository.startRecording(notebook.id, page.id, activeLayer)
            sheet = null
        }
    }
    fun nextMarkerPosition(): Pair<Float, Float> {
        val index = page.texts.count { it.kind != "plain" }
        return (.10f + (index % 2) * .45f) to (.13f + (index / 2) * .09f).coerceAtMost(.86f)
    }
    fun openEmbedded(text: InkText) {
        when (text.kind) {
            "link-note" -> { canvas?.flushInput(); onNavigateNotebook(text.target, null) }
            "link-web" -> runCatching {
                val uri = Uri.parse(text.target)
                require(uri.scheme == "http" || uri.scheme == "https")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }.onFailure { message = "Could not open this website." }
            "link-file" -> notebook.attachments.firstOrNull { it.id == text.target }?.let { attachment ->
                runCatching { files.openAttachment(attachment) }.onFailure { message = "Could not open this file." }
            } ?: run { message = "This file is no longer attached." }
            "audio" -> activeAudio = notebook.attachments.firstOrNull { it.id == text.target }
                ?: run { message = "This recording is no longer attached."; null }
        }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else message = "Microphone permission is needed to record audio."
    }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pendingExportPath?.let(::File); pendingExportPath = null
        if (uri != null && file != null) scope.launch {
            busy = "Saving document…"
            try { withContext(Dispatchers.IO) { files.writeToUri(file, uri) } }
            catch (error: Exception) { message = error.message ?: "Could not save the document." }
            finally { busy = null }
        }
    }
    fun export(kind: String, share: Boolean = false) {
        sheet = null
        canvas?.flushInput()
        val exportedPage = page
        val current = latestNotebook
        val snapshot = current.copy(pages = current.pages.map { if (it.id == exportedPage.id) exportedPage else it })
        scope.launch {
            busy = "Preparing ${kind.uppercase()}…"
            try {
                val output = withContext(Dispatchers.IO) { when (kind) {
                    "pdf" -> files.exportPdf(snapshot); "png" -> files.exportPng(snapshot, exportedPage); else -> files.exportBackup(snapshot)
                } }
                if (share) files.shareFile(output, when(kind) { "pdf" -> "application/pdf"; "png" -> "image/png"; else -> "application/octet-stream" })
                else { pendingExportPath = output.absolutePath; saveDocument.launch(output.name) }
            } catch (error: Exception) { message = error.message ?: "Could not export the notebook." }
            finally { busy = null }
        }
    }
    val insertFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val target = importKind
            val destinationId = importPageId
            val destinationLayer = importLayerId
            importKind = null; importPageId = null; sheet = null
            if (target != null) repository.importIntoNotebook(uri, notebook.id, target, destinationId, destinationLayer)
        }
    }
    val importNotice = repository.editorImportNotice
    LaunchedEffect(importNotice?.token, notebook.id) {
        if (importNotice != null && importNotice.notebookId == notebook.id) {
            if (importNotice.kind == "pdf" || importNotice.kind == "page-image") pageIndex = latestNotebook.pages.indexOfFirst { it.id == importNotice.pageId }.coerceAtLeast(0)
            if (importNotice.kind == "image" && importNotice.pageId == page.id && importNotice.previousPage != null) {
                undoStack = (undoStack + importNotice.previousPage).takeLast(80)
                redoStack = emptyList()
            }
            repository.consumeEditorImport(importNotice.token)
        }
    }
    fun pageAction(id: String, action: String) {
        if (notebook.infiniteCanvas) { sheet = "pins"; return }
        val index = notebook.pages.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = notebook.pages[index]
        when (action) {
            "outline" -> { actionPageId = id; draft = target.outline; sheet = "outline" }
            "delete" -> { if (notebook.pages.size > 1) { actionPageId = id; sheet = "delete page" } }
            "duplicate" -> {
                val copied = target.copy(id = UUID.randomUUID().toString(), strokes = target.strokes.map { it.copy(id = UUID.randomUUID().toString()) }, texts = target.texts.map { it.copy(id = UUID.randomUUID().toString()) }, images = target.images.map { it.copy(id = UUID.randomUUID().toString()) })
                val pages = notebook.pages.toMutableList().apply { add(index + 1, copied) }
                onNotebookChange(notebook.copy(pages = pages, updatedAt = System.currentTimeMillis())); pageIndex = index + 1
            }
            "move-up", "move-down" -> {
                val destination = if (action == "move-up") index - 1 else index + 1
                if (destination in notebook.pages.indices) {
                    val pages = notebook.pages.toMutableList().apply { removeAt(index); add(destination, target) }
                    onNotebookChange(notebook.copy(pages = pages, updatedAt = System.currentTimeMillis()))
                    pageIndex = pages.indexOfFirst { it.id == page.id }
                }
            }
        }
    }
    fun editableLayer(id: String) = page.layers.any { it.id == id && it.visible && !it.locked }
    fun requireEditableLayer(): Boolean {
        if (editableLayer(activeLayer)) return true
        message = "Choose a visible, unlocked layer first."; return false
    }
    /** A page-normalised rectangle of [width] centred on the current view; [aspect] is on-screen height / width. */
    fun placeBlock(width: Float, aspect: Float): android.graphics.RectF {
        val cw = (canvas?.width ?: 1).coerceAtLeast(1).toFloat(); val ch = (canvas?.height ?: 1).coerceAtLeast(1).toFloat()
        val documentHeight = if (notebook.infiniteCanvas) cw * 1.6f else ch
        val height = (width * cw * aspect / documentHeight).coerceIn(.04f, 1f)
        var x = viewportCenterX - width / 2f; var y = viewportCenterY - height / 2f
        if (!notebook.infiniteCanvas) { x = x.coerceIn(0f, (1f - width).coerceAtLeast(0f)); y = y.coerceIn(0f, (1f - height).coerceAtLeast(0f)) }
        return android.graphics.RectF(x, y, x + width, y + height)
    }
    /** A z just in front of the active layer's items under the ink, so a new sticker or note lands on top of them. */
    fun frontUnderInk(): Float = PageOrder.objects(page, activeLayer).map { PageOrder.z(it) }.filter { it < PageOrder.INK }.maxOrNull()
        ?.let { (it + PageOrder.INK) / 2f } ?: 0f
    fun addBlock(added: InkBlock) {
        canvas?.flushInput()
        val block = added.copy(z = frontUnderInk())
        applyPage(page.copy(blocks = page.blocks + block)); selectedBlockId = block.id; selectedIds = emptySet(); sheet = null
        // Tables open in Arrange mode for cell editing; sticky notes stay selected in the current tool, like a new shape.
        if (block.kind == InkBlock.Table) mode = CanvasMode.Objects else canvas?.selectBlock(block.id)
    }
    fun updateBlock(change: (InkBlock) -> InkBlock) { applyPage(page.copy(blocks = page.blocks.map { if (it.id == selectedBlockId) change(it) else it })) }
    fun addTable() {
        if (!requireEditableLayer()) return
        val box = placeBlock(.62f, tableDraft.rows * .5f / tableDraft.columns)
        addBlock(newTable(tableDraft, box.left, box.top, box.width(), box.height(), activeLayer))
    }
    fun addStickyNote() {
        if (!requireEditableLayer()) return
        val box = placeBlock(.3f, .85f)
        val note = newSticky(box.left, box.top, box.width(), box.height(), activeLayer)
        addBlock(note); editingStickyId = note.id
    }
    fun addSticker(emoji: String) {
        if (!requireEditableLayer()) return
        val box = placeBlock(.2f, 1f)
        val file = runCatching { renderStickerFile(context, emoji) }.getOrElse { message = "Could not create this sticker."; return }
        canvas?.flushInput()
        val sticker = InkImage(path = file.absolutePath, x = box.left, y = box.top, width = box.width(), height = box.height(), layerId = activeLayer, z = frontUnderInk())
        applyPage(page.copy(images = page.images + sticker))
        // Selected right away in the current tool, like a new shape: drag to move, handles to resize.
        selectedBlockId = null; sheet = null; canvas?.selectObject(sticker.id, isText = false)
    }
    fun selectEraser() {
        canvas?.flushInput(); showBrush = false; selectedIds = emptySet()
        if (!selectedBrush.eraser) previousPen = selectedBrush
        selectedBrush = brushes.last(); mode = CanvasMode.Write
    }
    fun deleteSelection() {
        canvas?.flushInput()
        applyPage(page.copy(strokes = page.strokes.filterNot { it.id in selectedIds && editableLayer(it.layerId) })); selectedIds = emptySet()
    }
    fun transformSelection(scale: Float = 1f, rotation: Float = 0f, flipX: Boolean = false, flipY: Boolean = false) {
        canvas?.flushInput()
        val ids = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) }.map { it.id }.toSet()
        applyPage(page.copy(strokes = InkSelection.transform(page.strokes, ids, scale, rotation, flipX, flipY, unbounded = notebook.infiniteCanvas)))
    }
    /** Rotates or mirrors the selected image or plain text box. */
    fun transformObject(rotation: Float = 0f, flipX: Boolean = false, flipY: Boolean = false) {
        canvas?.flushInput()
        val id = selectedObjectId ?: return
        fun turned(current: Float) = ((current + rotation) % 360f + 360f) % 360f
        applyPage(page.copy(
            texts = page.texts.map { if (it.id == id && it.kind == "plain" && editableLayer(it.layerId)) it.copy(rotation = turned(it.rotation), flipX = it.flipX != flipX, flipY = it.flipY != flipY) else it },
            images = page.images.map { if (it.id == id && editableLayer(it.layerId)) it.copy(rotation = turned(it.rotation), flipX = it.flipX != flipX, flipY = it.flipY != flipY) else it }))
    }
    fun paste() {
        canvas?.flushInput()
        val layer = page.layers.firstOrNull { it.id == activeLayer }
        if (layer?.locked == true || layer?.visible == false) { message = "Choose a visible, unlocked layer first."; return }
        val points = clipboard.flatMap { it.points }
        if (points.isEmpty()) return
        val dx = if (notebook.infiniteCanvas) .03f else minOf(.03f, 1f - points.maxOf { it.x }).coerceAtLeast(0f)
        val dy = if (notebook.infiniteCanvas) .03f else minOf(.03f, 1f - points.maxOf { it.y }).coerceAtLeast(0f)
        val copies = clipboard.map { it.copy(id = UUID.randomUUID().toString(), layerId = activeLayer, points = it.points.map { p -> p.copy(x = p.x + dx, y = p.y + dy) }) }
        applyPage(page.copy(strokes = page.strokes + copies)); selectedIds = copies.map { it.id }.toSet(); mode = CanvasMode.Lasso
    }
    /** Actions from the bar next to a selection that need the editor (clipboard, text editing, the full menu). */
    fun handleCanvasAction(action: CanvasAction) {
        when (action) {
            CanvasAction.Copy -> { clipboard = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) } }
            CanvasAction.Cut -> { canvas?.flushInput(); clipboard = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) }; deleteSelection() }
            CanvasAction.Paste -> paste()
            CanvasAction.Fill -> sheet = if (page.blocks.any { it.id == selectedBlockId && it.kind == InkBlock.Sticky }) "note style" else "selection style"
            CanvasAction.Arrange -> sheet = "arrange"
            CanvasAction.More -> sheet = "selection"
            CanvasAction.Edit -> page.blocks.firstOrNull { it.id == selectedBlockId && it.kind == InkBlock.Sticky }?.let { editingStickyId = it.id }
                ?: page.texts.firstOrNull { it.id == selectedObjectId && it.kind == "plain" }?.let { editingTextId = it.id; draft = it.text; sheet = "edit text" }
            else -> Unit
        }
    }
    if (sheet == "pressure") PressureDialog(threshold, profileName, pressureCurveEnabled, pressureCurve,
        velocityCurveEnabled, velocityCurve, tiltCurveEnabled, tiltCurve,
        onProfile = { profileName = it.name; threshold = it.threshold },
        onThreshold = { threshold = it; profileName = pressureProfiles.firstOrNull { p -> abs(p.threshold - it) < .005f }?.name ?: "Custom" },
        onPressureEnabled = { pressureCurveEnabled = it }, onPressureCurve = { pressureCurve = it },
        onVelocityEnabled = { velocityCurveEnabled = it }, onVelocityCurve = { velocityCurve = it },
        onTiltEnabled = { tiltCurveEnabled = it }, onTiltCurve = { tiltCurve = it },
        onDismiss = { sheet = null })

    val dialog = sheet
    if (dialog != null && dialog !in listOf("pressure", "image", "pdf", "attachment")) {
        ToolDialog(when(dialog) { "edit text" -> "Text box"; "tags" -> "Tags"; "outline" -> "Page outline"; else -> dialog.replaceFirstChar { it.uppercase() } }, { sheet = null },
            expanded = dialog == "shapes",
            footer = if (dialog == "shapes") { {
                Button(onClick = { canvas?.flushInput(); mode = CanvasMode.Shape; if (selectedBrush.eraser) selectedBrush = previousPen; selectedIds = emptySet(); sheet = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Draw shape") }
            } } else null) {
            when (dialog) {
                "paper" -> {
                    val paper = com.allient.folio.InfinitePaper.parse(page.template) ?: com.allient.folio.InfinitePaper("blank")
                    fun set(next: com.allient.folio.InfinitePaper) { canvas?.flushInput(); applyPage(page.copy(template = next.template)) }
                    Text("Pattern", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.allient.folio.InfinitePaper.patterns.forEach { (id, label) ->
                            androidx.compose.material3.FilterChip(selected = paper.pattern == id, onClick = { set(paper.copy(pattern = id)) }, label = { Text(label.removeSuffix(" paper"), fontSize = 12.sp) })
                        }
                    }
                    Text("Background", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.allient.folio.InfinitePaper.colors.forEach { (id, label) ->
                            androidx.compose.material3.FilterChip(selected = paper.color == id, onClick = { set(paper.copy(color = id)) }, label = { Text(label, fontSize = 12.sp) },
                                leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(com.allient.folio.InfinitePaper.colorArgb(id))).border(1.dp, ChromeLine, CircleShape)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Spacing ${paper.spacing}", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(88.dp))
                        androidx.compose.material3.Slider(paper.spacing.toFloat(), { set(paper.copy(spacing = it.roundToInt().coerceIn(1, 8))) }, Modifier.weight(1f), enabled = paper.pattern != "blank", valueRange = 1f..8f, steps = 6)
                    }
                }
                "table" -> TableSheet(tableDraft, { tableDraft = it }, ::addTable)
                "stickers" -> StickerSheet(::addSticker)
                "pins" -> {
                    Text("One blank canvas · pan in all directions", fontSize = 12.sp, color = ChromeMuted)
                    Text("Long-press blank space to add a pin. Tap a pin to jump there.", fontSize = 12.sp)
                    TextButton(onClick = { pendingPin = Offset(viewportCenterX, viewportCenterY); editingPin = null; pinName = "Pin ${notebook.pins.size + 1}" }) { Text("Pin current view") }
                    notebook.pins.forEach { pin ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            InkSymbol(InkIcon.Tag)
                            Text(pin.name, Modifier.weight(1f).clickable { canvas?.jumpTo(pin.x, pin.y, pin.zoom); sheet = null }.padding(12.dp), fontSize = 13.sp)
                            Box {
                                InkToolButton(InkIcon.More, "Options for ${pin.name}", { pinMenu = pin.id })
                                DropdownMenu(pinMenu == pin.id, { pinMenu = null }, modifier = Modifier.penPressureFilter()) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { editingPin = pin; pinName = pin.name; pendingPin = Offset(pin.x, pin.y); pinMenu = null })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { latestOnChange(latestNotebook.copy(pins = latestNotebook.pins.filterNot { it.id == pin.id }, updatedAt = System.currentTimeMillis())); pinMenu = null })
                                }
                            }
                        }
                        HorizontalDivider(color = ChromeLine)
                    }
                    Text("${notebook.pins.size} pins", fontSize = 11.sp, color = ChromeMuted)
                }
                "templates" -> {
                    var applyAll by remember { mutableStateOf(false) }
                    Text("Select a paper template", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NotebookTemplateCatalog.categories.forEach { category ->
                            TextButton(onClick = { templateCategory = category }) { Text(category, color = if (templateCategory == category) ChromeInk else ChromeMuted, fontWeight = if (templateCategory == category) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = !applyAll, onClick = { applyAll = false })
                        Text("Current page", Modifier.clickable { applyAll = false }, fontSize = 11.sp)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.RadioButton(selected = applyAll, onClick = { applyAll = true })
                        Text("All pages", Modifier.clickable { applyAll = true }, fontSize = 11.sp)
                    }
                    val recentTemplates = notebook.pages.asReversed().map { it.template }.distinct().take(8)
                    val shown = NotebookTemplateCatalog.inCategory(templateCategory, recentTemplates).map { it.id to it.name }
                    LazyVerticalGrid(columns = GridCells.Adaptive(92.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        gridItems(shown, key = { it.first }) { (id, name) ->
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (page.template == id) Color(0xFFE5EFEC) else Color.White).border(if (page.template == id) 2.dp else 1.dp, if (page.template == id) Color(0xFF357568) else ChromeLine, RoundedCornerShape(12.dp)).clickable {
                                val targetPages = if (applyAll) latestNotebook.pages.map { it.copy(template = id) } else latestNotebook.pages.map { if (it.id == page.id) it.copy(template = id) else it }
                                val current = targetPages.firstOrNull { it.id == page.id } ?: page.copy(template = id)
                                if (applyAll) { canvas?.flushInput(); undoStack = emptyList(); redoStack = emptyList(); page = current; onNotebookChange(latestNotebook.copy(pages = targetPages, updatedAt = System.currentTimeMillis())) } else applyPage(current)
                                sheet = null
                            }.padding(8.dp)) {
                                NotebookPageThumbnail(page.copy(strokes = emptyList(), texts = emptyList(), images = emptyList(), template = id), Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(8.dp)).background(Color.White))
                                Text(name, Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                "pens" -> BrushSettings(if (selectedBrush.eraser) previousPen else selectedBrush, brushOptions, ::updateBrush, ::addBrush, ::deleteBrush, drawingViewportWidthPx, panelXdpi, ::pickPageColor)
                "layers" -> LayerControls(page, activeLayer, { activeLayer = it }, ::applyPage)
                "shapes" -> {
                    val shapeWidthMm = brushWidthMillimetres(shapeWidth, drawingViewportWidthPx, panelXdpi)
                    val shapeMinMm = brushWidthMillimetres(.5f, drawingViewportWidthPx, panelXdpi)
                    val shapeMaxMm = brushWidthMillimetres(32f, drawingViewportWidthPx, panelXdpi)
                    ShapeChoices(shape) { kind ->
                        canvas?.flushInput(); shape = kind; mode = CanvasMode.Shape
                        if (selectedBrush.eraser) selectedBrush = previousPen
                        selectedIds = emptySet()
                    }
                    HorizontalDivider(color = ChromeLine)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Line style", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        ShapeLineStyle.entries.forEach { style ->
                            Surface(onClick = { shapeLineStyle = style }, shape = RoundedCornerShape(10.dp),
                                color = if (shapeLineStyle == style) Color(0xFFE5EFEC) else Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (shapeLineStyle == style) Color(0xFF357568) else ChromeLine),
                                modifier = Modifier.padding(start = 4.dp).size(width = 52.dp, height = 36.dp).semantics { contentDescription = style.label; selected = shapeLineStyle == style }) {
                                Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
                                    val effect = when (style) {
                                        ShapeLineStyle.Dotted -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 4.dp.toPx()))
                                        ShapeLineStyle.Dashed -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx()))
                                        ShapeLineStyle.DashDot -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 3.dp.toPx(), 1.dp.toPx(), 3.dp.toPx()))
                                        else -> null
                                    }
                                    drawLine(ChromeInk, Offset.Zero, Offset(size.width, 0f), 2.dp.toPx(), pathEffect = effect)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Line width", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(String.format(Locale.US, "%.2f mm", shapeWidthMm), fontSize = 12.sp, color = ChromeMuted)
                    }
                    VariableWidthSlider(shapeWidthMm, shapeMinMm, shapeMaxMm, Modifier.fillMaxWidth().height(34.dp), "Shape line width") { shapeWidth = brushWidthDocumentUnits(it, drawingViewportWidthPx, panelXdpi).coerceIn(.5f, 32f) }
                    HorizontalDivider(color = ChromeLine)
                    InkColorOptions(shapeColor, { shapeColor = it }, ::pickPageColor, title = "Border")
                    HorizontalDivider(color = ChromeLine)
                    InkColorOptions(shapeFillColor, { shapeFillColor = it; shapeFilled = true }, ::pickPageColor,
                        onClear = { shapeFilled = false }, transparentSelected = !shapeFilled, title = "Fill")
                }
                "eraser" -> EraserControls(eraserMode,
                    brushWidthMillimetres(eraserWidth, drawingViewportWidthPx, panelXdpi),
                    brushWidthMillimetres(4f, drawingViewportWidthPx, panelXdpi),
                    brushWidthMillimetres(80f, drawingViewportWidthPx, panelXdpi), eraseActiveLayerOnly, autoReturnToPen,
                    onMode = { eraserMode = it; selectEraser() }, onWidth = { eraserWidth = brushWidthDocumentUnits(it, drawingViewportWidthPx, panelXdpi).coerceIn(4f, 80f) },
                    onScope = { eraseActiveLayerOnly = it }, onAutoReturn = { autoReturnToPen = it },
                    onClearLayer = { sheet = "erase layer ink" }, onClearAll = { sheet = "erase all ink" })
                "erase layer ink", "erase all ink" -> {
                    Text(if (dialog == "erase layer ink") "Clear the current layer?" else "Clear all layers?", fontSize = 14.sp)
                    Text("Handwriting, text and images will be removed. Page templates are kept, and you can undo this action.", fontSize = 12.sp, color = ChromeMuted)
                    TextButton(onClick = {
                        canvas?.flushInput()
                        val shouldClear = { layerId: String -> dialog == "erase all ink" || layerId == activeLayer }
                        applyPage(page.copy(strokes = page.strokes.filterNot { shouldClear(it.layerId) },
                            texts = page.texts.filterNot { shouldClear(it.layerId) },
                            images = page.images.filterNot { shouldClear(it.layerId) }))
                        selectedIds = emptySet(); sheet = null
                        if (autoReturnToPen) { selectedBrush = previousPen; mode = CanvasMode.Write }
                    }) { Text("Clear layer") }
                }
                "lasso" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LassoKind.entries.forEach { kind -> DrawingChoice(when (kind) { LassoKind.Smart -> "Smart lasso"; LassoKind.Freeform -> "Freehand"; LassoKind.Rectangle -> "Rectangle" }, kind == lassoKind, {
                                canvas?.flushInput(); lassoKind = kind; mode = CanvasMode.Lasso; selectedIds = emptySet(); sheet = null
                            }, Modifier.weight(1f)) { InkSymbol(if (kind == LassoKind.Rectangle) InkIcon.RectangleSelect else InkIcon.Lasso) } }
                        }
                        ToolAction(InkIcon.Layers, "Select all", "Select all ink on visible unlocked layers") {
                            selectedIds = page.strokes.filter { editableLayer(it.layerId) }.map { it.id }.toSet()
                            mode = CanvasMode.Lasso; sheet = null
                        }
                        ToolToggle("Auto-switch to pen", "Return to the previous pen after finishing a selection.", lassoAutoSwitch, { lassoAutoSwitch = it })
                    }
                    Text(if (lassoKind == LassoKind.Smart) "Loop around content to select intersecting strokes. Drag inside to move or drag any blue corner to resize." else "Enclose ink, then drag inside to move it or drag any blue corner to resize. Use your pen or finger. Locked and hidden layers are protected.", fontSize = 12.sp, color = ChromeMuted)
                    if (clipboard.isNotEmpty()) ToolAction(InkIcon.Copy, "Paste strokes") { paste(); sheet = null }
                }
                "selection" -> {
                    // Only what the bar beside the selection does not already show.
                    ToolAction(InkIcon.Cut, "Cut", "Remove the selection and keep it to paste") { canvas?.flushInput(); clipboard = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) }; deleteSelection(); sheet = null }
                    ToolAction(InkIcon.Hand, "Move selection", "Drag anywhere on the canvas to move the selected ink") { canvas?.flushInput(); mode = CanvasMode.MoveSelection; sheet = null }
                    ToolAction(InkIcon.Add, "Larger 10%") { transformSelection(scale = 1.1f); sheet = null }
                    ToolAction(InkIcon.Zoom, "Smaller 10%") { transformSelection(scale = 1f / 1.1f); sheet = null }
                    ToolAction(InkIcon.Layers, "Move to layer") { sheet = "selection layer" }
                    ToolAction(InkIcon.BringFront, "Bring to front", "Above other ink on this layer") {
                        canvas?.flushInput(); applyPage(page.copy(strokes = page.strokes.filterNot { it.id in selectedIds } + page.strokes.filter { it.id in selectedIds })); sheet = null
                    }
                    ToolAction(InkIcon.SendBack, "Send to back", "Under other ink on this layer") {
                        canvas?.flushInput(); applyPage(page.copy(strokes = page.strokes.filter { it.id in selectedIds } + page.strokes.filterNot { it.id in selectedIds })); sheet = null
                    }
                    ToolAction(InkIcon.Tag, "Tag selection") { draft = ""; sheet = "tags" }
                    ToolAction(InkIcon.Close, "Deselect") { selectedIds = emptySet(); if (mode == CanvasMode.MoveSelection) mode = CanvasMode.Lasso; sheet = null }
                }
                "arrange" -> {
                    val target = selectedObjectId ?: selectedBlockId
                    fun move(step: PageOrder.Move) { canvas?.flushInput(); target?.let { applyPage(PageOrder.reorder(page, it, step)) } }
                    Text("Order of overlapping items on this layer. Ink stays between: items in front cover your writing, items at the back sit under it.", fontSize = 12.sp, color = ChromeMuted)
                    ToolAction(InkIcon.BringFront, "Bring to front") { move(PageOrder.Move.Front); sheet = null }
                    ToolAction(InkIcon.BringFront, "Bring forward", "One step up") { move(PageOrder.Move.Forward) }
                    ToolAction(InkIcon.SendBack, "Send backward", "One step down") { move(PageOrder.Move.Backward) }
                    ToolAction(InkIcon.SendBack, "Send to back") { move(PageOrder.Move.Back); sheet = null }
                }
                "note style" -> page.blocks.firstOrNull { it.id == selectedBlockId }?.let { block -> StickyStyleControls(block) { changed -> updateBlock { changed } } }
                "selection layer" -> LayerControls(page, activeLayer, { activeLayer = it }, ::applyPage,
                    onChooseDestination = { updated, destination ->
                        canvas?.flushInput()
                        applyPage(updated.copy(strokes = updated.strokes.map {
                            if (it.id in selectedIds && editableLayer(it.layerId)) it.copy(layerId = destination) else it
                        }))
                        activeLayer = destination; sheet = null
                    })
                "delete selection" -> {
                    val selection = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) }
                    val description = if (selection.size == 1) {
                        if (selection.single().engine == "shape-v1") { if (selection.single().points.size <= 2) "this line" else "this shape" } else "this trace"
                    } else "these ${selection.size} selected traces, shapes or lines"
                    Text("Are you sure you want to delete $description?", fontSize = 14.sp)
                    Text("You can restore the deletion with Undo.", fontSize = 12.sp, color = ChromeMuted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { sheet = null }) { Text("No") }
                        TextButton(onClick = { deleteSelection(); sheet = null }) { Text("Yes, delete") }
                    }
                }
                "selection style" -> {
                    val selection = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) }
                    val fillable = selection.filter { it.points.size >= 3 && (it.filled ||
                        (kotlin.math.abs(it.points.first().x - it.points.last().x) < .003f && kotlin.math.abs(it.points.first().y - it.points.last().y) < .003f)) }.map { it.id }.toSet()
                    var editingFill by remember { mutableStateOf(fillable.isNotEmpty()) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        listOf(true to "Fill", false to "Border").forEach { (fill, label) ->
                            val chosen = editingFill == fill
                            Surface(shape = RoundedCornerShape(14.dp), color = if (chosen) Color(0xFFE5EFEC) else Color(0xFFF7F8F7),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (chosen) Color(0xFF287463) else ChromeLine),
                                modifier = Modifier.clip(RoundedCornerShape(14.dp)).selectable(selected = chosen, enabled = !fill || fillable.isNotEmpty(), role = Role.RadioButton, onClick = { editingFill = fill })) {
                                Text(label, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = ChromeInk, fontSize = 13.sp)
                            }
                        }
                    }
                    val first = selection.firstOrNull()
                    val currentColor = if (editingFill) first?.fillColor ?: first?.color ?: fillColor else first?.color ?: shapeColor
                    InkColorOptions(currentColor, { color ->
                        applyPage(page.copy(strokes = page.strokes.map { stroke ->
                            when {
                                stroke.id !in selectedIds || !editableLayer(stroke.layerId) -> stroke
                                editingFill && stroke.id in fillable -> stroke.copy(filled = true, fillColor = color)
                                !editingFill -> stroke.copy(color = color,
                                    fillColor = if (stroke.filled && stroke.fillColor == null) stroke.color else stroke.fillColor)
                                else -> stroke
                            }
                        }))
                    }, ::pickPageColor, onClear = if (editingFill) { {
                        applyPage(page.copy(strokes = page.strokes.map { if (it.id in fillable) it.copy(filled = false, fillColor = null) else it }))
                    } } else null, transparentSelected = editingFill && selection.filter { it.id in fillable }.all { !it.filled })
                    if (!editingFill) {
                        var width by remember(first?.id, first?.width) { mutableFloatStateOf(first?.width ?: 3f) }
                        val widthMm = brushWidthMillimetres(width, drawingViewportWidthPx, panelXdpi)
                        Text("Border / stroke width · ${String.format(Locale.US, "%.2f", widthMm)} mm", fontSize = 13.sp)
                        Slider(value = width.coerceIn(.5f, 32f), onValueChange = { width = it }, valueRange = .5f..32f,
                            onValueChangeFinished = { applyPage(page.copy(strokes = page.strokes.map {
                                if (it.id in selectedIds && editableLayer(it.layerId)) it.copy(width = width) else it
                            })) })
                        ShapeLineStyleChoices(first?.lineStyle ?: "solid") { style ->
                            applyPage(page.copy(strokes = page.strokes.map { if (it.id in selectedIds && editableLayer(it.layerId)) it.copy(lineStyle = style.name.lowercase()) else it }))
                        }
                    }
                    Text("Fill changes the inside of closed shapes. Border controls the outline and line width.", fontSize = 12.sp, color = ChromeMuted)
                }
                "selection color" -> {
                    val current = page.strokes.firstOrNull { it.id in selectedIds }?.color ?: 0xFF252525.toInt()
                    InkColorOptions(current, { color ->
                        applyPage(page.copy(strokes = page.strokes.map { if (it.id in selectedIds && editableLayer(it.layerId)) it.copy(color = (it.color and 0xFF000000.toInt()) or (color and 0xFFFFFF)) else it }))
                    }, ::pickPageColor)
                }
                "fill" -> {
                    InkColorOptions(fillColor, { fillColor = it }, ::pickPageColor)
                    Text("Tap inside a closed shape on the active layer to fill it.", fontSize = 12.sp, color = ChromeMuted)
                    Button(onClick = { mode = CanvasMode.Fill; sheet = null }, modifier = Modifier.fillMaxWidth()) { Text("Use fill") }
                }
                "text" -> {
                    ToolAction(InkIcon.Hand, "Arrange objects", "Select and drag text, images, tables and notes on the page") { mode = CanvasMode.Objects; sheet = null }
                    ToolAction(InkIcon.Add, "Add text box") { if (requireEditableLayer()) { editingTextId = null; draft = ""; sheet = "edit text" } }
                    page.texts.forEach { text ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text.text.take(60), Modifier.weight(1f).clickable { editingTextId = text.id; draft = text.text; sheet = "edit text" }, fontSize = 13.sp)
                            InkToolButton(InkIcon.Trash, "Delete text box", { if (editableLayer(text.layerId)) applyPage(page.copy(texts = page.texts.filterNot { it.id == text.id })) }, enabled = editableLayer(text.layerId))
                        }
                    }
                    Text("Text boxes appear on the active layer.", fontSize = 11.sp, color = ChromeMuted)
                }
                "timestamp" -> {
                    val marker = page.texts.firstOrNull { it.id == editingTextId && it.kind == "timestamp" }
                    val formats = listOf(
                        "MMMM d, yyyy" to "Long date", "yyyy-MM-dd" to "Short date",
                        "EEE, MMM d, yyyy" to "Day and date", "MMM d, yyyy · h:mm a" to "Date and time",
                        "yyyy-MM-dd HH:mm" to "24-hour date and time", "h:mm a" to "12-hour time", "HH:mm" to "24-hour time",
                    )
                    var moment by remember(marker?.id) { mutableStateOf(marker?.target?.toLongOrNull() ?: System.currentTimeMillis()) }
                    var pattern by remember(marker?.id) { mutableStateOf(formats.firstOrNull { (value, _) ->
                        marker?.text == SimpleDateFormat(value, Locale.getDefault()).format(Date(moment))
                    }?.first ?: "MMMM d, yyyy") }
                    Text("Choose the date and time", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val current = Calendar.getInstance().apply { timeInMillis = moment }
                            android.app.DatePickerDialog(context, { _, year, month, day ->
                                moment = Calendar.getInstance().apply { timeInMillis = moment; set(year, month, day) }.timeInMillis
                            }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(moment))) }
                        OutlinedButton(onClick = {
                            val current = Calendar.getInstance().apply { timeInMillis = moment }
                            android.app.TimePickerDialog(context, { _, hour, minute ->
                                moment = Calendar.getInstance().apply { timeInMillis = moment; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.timeInMillis
                            }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), false).show()
                        }) { Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(moment))) }
                    }
                    Text("Display format", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    formats.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (value, label) -> FilterChip(selected = pattern == value, onClick = { pattern = value }, label = { Text(label, fontSize = 11.sp) }) }
                        }
                    }
                    val preview = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(moment))
                    Text("Preview: $preview", fontSize = 14.sp)
                    Button(onClick = {
                        val updated = marker?.copy(text = preview, target = moment.toString())
                            ?: nextMarkerPosition().let { (x, y) -> InkText(text = preview, x = x, y = y, layerId = activeLayer, kind = "timestamp", target = moment.toString(), boxWidth = .45f) }
                        applyPage(page.copy(texts = page.texts.filterNot { it.id == updated.id } + updated))
                        sheet = null
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (marker == null) "Add date and time" else "Update date and time") }
                }
                "edit text", "rename", "tags", "outline" -> {
                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text(when(dialog) { "edit text" -> "Text"; "rename" -> "Notebook name"; "outline" -> "Outline heading"; else -> "Tag name" }) }, singleLine = dialog != "edit text")
                    if (dialog == "edit text") {
                        val baseText = editingText
                        var size by remember(baseText?.id) { mutableFloatStateOf(baseText?.size ?: 22f) }
                        var textColor by remember(baseText?.id) { mutableIntStateOf(baseText?.color ?: 0xFF252525.toInt()) }
                        var font by remember(baseText?.id) { mutableStateOf(baseText?.font ?: "Sans") }
                        var bold by remember(baseText?.id) { mutableStateOf(baseText?.bold ?: false) }
                        var italic by remember(baseText?.id) { mutableStateOf(baseText?.italic ?: false) }
                        var underline by remember(baseText?.id) { mutableStateOf(baseText?.underline ?: false) }
                        var alignment by remember(baseText?.id) { mutableStateOf(baseText?.alignment ?: "Start") }
                        var lineSpacing by remember(baseText?.id) { mutableFloatStateOf(baseText?.lineSpacing ?: 1f) }
                        var showTextColors by remember(baseText?.id) { mutableStateOf(false) }
                        Text("Font", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ChromeMuted)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Sans", "Serif", "Mono").forEach { family ->
                                FilterChip(selected = font == family, onClick = { font = family }, label = { Text(family) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("Bold", fontWeight = FontWeight.Bold) })
                            FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("Italic", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) })
                            FilterChip(selected = underline, onClick = { underline = !underline }, label = { Text("Underline", textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline) })
                        }
                        OutlinedButton(onClick = { showTextColors = !showTextColors }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(textColor)).border(1.dp, ChromeLine, CircleShape))
                            Spacer(Modifier.width(8.dp)); Text(if (showTextColors) "Hide color options" else "Text color")
                        }
                        if (showTextColors) InkColorOptions(textColor, { textColor = it }, ::pickPageColor)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Alignment", fontSize = 12.sp, color = ChromeMuted)
                            listOf("Start", "Center", "End").forEach { option -> FilterChip(selected = alignment == option, onClick = { alignment = option }, label = { Text(option) }) }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Line spacing · ${String.format(Locale.US, "%.1f", lineSpacing)}×", fontSize = 12.sp)
                            Slider(value = lineSpacing, onValueChange = { lineSpacing = it }, valueRange = .7f..2f)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Text size · ${size.toInt()}", fontSize = 12.sp)
                            Slider(value = size, onValueChange = { size = it }, valueRange = 12f..60f)
                        }
                        Button(enabled = draft.isNotBlank(), onClick = {
                            if (!editableLayer(editingText?.layerId ?: activeLayer)) { message = "Choose a visible, unlocked layer first."; return@Button }
                            val text = (editingText ?: InkText(text = draft, layerId = activeLayer, y = (.15f + page.texts.size * .07f).coerceAtMost(.85f))).copy(text = draft, size = size, color = textColor, font = font, bold = bold, italic = italic, underline = underline, alignment = alignment, lineSpacing = lineSpacing)
                            applyPage(page.copy(texts = page.texts.filterNot { it.id == text.id } + text)); sheet = null
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Apply text") }
                    } else {
                        TextButton(enabled = dialog == "outline" || draft.isNotBlank(), onClick = {
                            when(dialog) {
                                "rename" -> onNotebookChange(notebook.copy(title = draft.trim(), updatedAt = System.currentTimeMillis()))
                                "outline" -> {
                                    val target = notebook.pages.firstOrNull { it.id == actionPageId } ?: page
                                    val changed = target.copy(outline = draft.trim())
                                    if (target.id == page.id) applyPage(changed) else replacePage(changed)
                                }
                                "tags" -> if (selectedIds.isNotEmpty()) applyPage(page.copy(strokes = page.strokes.map { if (it.id in selectedIds) it.copy(tags = (it.tags + draft.trim()).distinct()) else it }))
                                else onNotebookChange(notebook.copy(tags = (notebook.tags + draft.trim()).distinct(), updatedAt = System.currentTimeMillis()))
                            }
                            sheet = null
                        }) { Text("Save") }
                    }
                    if (dialog == "tags") {
                        Text(if (selectedIds.isEmpty()) "Tags for this notebook" else "Tag ${selectedIds.size} selected strokes", fontSize = 11.sp, color = ChromeMuted)
                        notebook.tags.forEach { tag -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#$tag", Modifier.weight(1f), fontSize = 13.sp)
                            InkToolButton(InkIcon.Trash, "Remove tag $tag", { onNotebookChange(notebook.copy(tags = notebook.tags - tag)) })
                        } }
                    }
                }
                "insert" -> {
                    ToolAction(InkIcon.Table, "Table", "Insert an editable table that carries the ink written on it") { sheet = "table" }
                    ToolAction(InkIcon.Sticker, "Stickers", "Place an emoji sticker on the page") { sheet = "stickers" }
                    ToolAction(InkIcon.Sticky, "Sticky note", "Place a coloured note you can write on") { addStickyNote() }
                    ToolAction(InkIcon.Paper, "Image", "Place an image on the current page") { if (requireEditableLayer()) { importKind = "image"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("image/*")) } }
                    ToolAction(InkIcon.Pages, "Link to Notes", "Add a page link to another notebook") { sheet = "link notes" }
                    ToolAction(InkIcon.Pages, "Link to File", "Add a local file reference") { importKind = "link-file"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("*/*")) }
                    ToolAction(InkIcon.Search, "Link to Website", "Add a website URL to the page") { draft = "https://"; sheet = "website link" }
                    ToolAction(InkIcon.Insert, "Attachment", "Keep a local copy with this notebook") { importKind = "attachment"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("*/*")) }
                    ToolAction(InkIcon.Clock, "Date and time") { if (requireEditableLayer()) { editingTextId = null; sheet = "timestamp" } }
                    ToolAction(InkIcon.Microphone, if (recording) "Stop recording" else "Record audio") {
                        if (recording) stopRecording()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                        else microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                "attachments" -> {
                    if (notebook.attachments.isEmpty()) Text("No attachments yet.", fontSize = 13.sp, color = ChromeMuted)
                    notebook.attachments.forEach { attachment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(attachment.name, Modifier.weight(1f).clickable { runCatching { files.openAttachment(attachment) }.onFailure { message = "No app is available to open this file." } }, fontSize = 13.sp)
                            InkToolButton(InkIcon.Trash, "Remove attachment", { onNotebookChange(notebook.copy(attachments = notebook.attachments.filterNot { it.id == attachment.id })) })
                        }
                    }
                }
                "recordings" -> {
                    val recordings = notebook.attachments.filter { it.mimeType.startsWith("audio/") }
                    if (recordings.isEmpty()) Text("No recordings yet.", fontSize = 13.sp, color = ChromeMuted)
                    recordings.forEach { attachment -> ToolAction(InkIcon.Microphone, attachment.name.removeSuffix(".m4a"), "Play recording") {
                        activeAudio = attachment; sheet = null
                    } }
                }
                "page+" -> {
                    Text("Add a page", fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Before", "After", "Last Page").forEach { position -> FilterChip(selected = pagePlacement == position, onClick = { pagePlacement = position }, label = { Text(position) }) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Blank", "Ruled", "Grid", "Daily planner").forEach { name ->
                            val id = when (name) { "Ruled" -> "lined"; "Grid" -> "grid"; "Daily planner" -> "daily"; else -> "blank" }
                            TextButton(onClick = {
                                canvas?.flushInput(); val added = NotebookPage(template = id)
                                val pages = notebook.pages.toMutableList(); val at = when(pagePlacement) { "Before" -> safeIndex; "Last Page" -> pages.size; else -> safeIndex + 1 }
                                pages.add(at.coerceIn(0, pages.size), added); onNotebookChange(notebook.copy(pages = pages, updatedAt = System.currentTimeMillis())); pageIndex = at; sheet = null
                            }) { Text(name, fontSize = 11.sp) }
                        }
                    }
                    ToolAction(InkIcon.Template, "More templates") { sheet = "templates" }
                    ToolAction(InkIcon.Pages, "From another notebook") { sheet = "page from notebook" }
                    ToolAction(InkIcon.Paper, "From images") { if (requireEditableLayer()) { importKind = "page-image"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("image/*")) } }
                    ToolAction(InkIcon.Insert, "From files (PDF)") { importKind = "pdf"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("application/pdf")) }
                }
                "page from notebook" -> {
                    repository.notebooks.filter { it.id != notebook.id && it.deletedAt == 0L }.forEach { source ->
                        ToolAction(InkIcon.Pages, source.title, "Add its first page here") {
                            val original = source.pages.firstOrNull()
                            if (original != null) {
                                val clone = original.copy(id = java.util.UUID.randomUUID().toString())
                                val pages = notebook.pages.toMutableList(); val at = when(pagePlacement) { "Before" -> safeIndex; "Last Page" -> pages.size; else -> safeIndex + 1 }
                                pages.add(at.coerceIn(0, pages.size), clone); onNotebookChange(notebook.copy(pages = pages, updatedAt = System.currentTimeMillis())); pageIndex = at
                            }
                            sheet = null
                        }
                    }
                    if (repository.notebooks.none { it.id != notebook.id && it.deletedAt == 0L }) Text("No other notebooks are available yet.", fontSize = 12.sp, color = ChromeMuted)
                }
                "link notes" -> {
                    repository.notebooks.filter { it.id != notebook.id && it.deletedAt == 0L }.forEach { target -> ToolAction(InkIcon.Pages, target.title, "Link to page 1") {
                        val (x, y) = nextMarkerPosition()
                        val linkText = InkText(text = target.title, x = x, y = y, layerId = activeLayer, kind = "link-note", target = target.id)
                        if (requireEditableLayer()) applyPage(page.copy(texts = page.texts + linkText)); sheet = null
                    } }
                    if (repository.notebooks.none { it.id != notebook.id && it.deletedAt == 0L }) Text("Create another notebook to link it here.", fontSize = 12.sp, color = ChromeMuted)
                }
                "website link" -> {
                    OutlinedTextField(draft, { draft = it }, label = { Text("Website URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = draft.startsWith("http"), onClick = {
                        if (requireEditableLayer()) {
                            val (x, y) = nextMarkerPosition()
                            applyPage(page.copy(texts = page.texts + InkText(text = Uri.parse(draft).host ?: draft, x = x, y = y, layerId = activeLayer, kind = "link-web", target = draft)))
                        }
                        sheet = null
                    }) { Text("Add link") }
                }
                "share & export" -> {
                    ToolAction(InkIcon.Pages, "Save PDF", "All ${notebook.pages.size} pages") { export("pdf") }
                    ToolAction(InkIcon.Paper, "Save PNG", "Current page") { export("png") }
                    ToolAction(InkIcon.Save, "Save Folio backup (.folio)", "Editable notebook including local media") { export("folio") }
                    ToolAction(InkIcon.Share, "Share PDF") { export("pdf", true) }
                    ToolAction(InkIcon.Share, "Share current page") { export("png", true) }
                }
                "zoom" -> {
                    Text("Zoom · ${(zoom * 100).toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { canvas?.setZoom(zoom - .25f) }, modifier = Modifier.weight(1f)) { Text("− Zoom out") }
                        OutlinedButton(onClick = { canvas?.setZoom(zoom + .25f) }, modifier = Modifier.weight(1f)) { Text("+ Zoom in") }
                    }
                    Slider(zoom, onValueChange = { canvas?.setZoom(it) }, valueRange = .25f..5f,
                        modifier = Modifier.semantics { contentDescription = "Canvas zoom" })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(.5f, .75f, 1f, 1.5f, 2f, 3f).forEach { level ->
                            TextButton(onClick = { canvas?.setZoom(level) }, modifier = Modifier.weight(1f)) {
                                Text("${(level * 100).toInt()}%", fontSize = 10.sp)
                            }
                        }
                    }
                    ToolAction(InkIcon.Zoom, "Zoom window", "Write in a magnified panel while a box marks the area on the page") {
                        canvas?.flushInput(); mode = CanvasMode.ZoomWindow; sheet = null
                        if (zoomBox == null) zoomBox = android.graphics.RectF(.1f, .08f, .6f, .08f + .5f * zoomRatio)
                    }
                    ToolAction(InkIcon.RectangleSelect, "Free zoom-in", "Drag a rectangle around the area to fill the view") {
                        canvas?.flushInput(); mode = CanvasMode.FreeZoom; sheet = null
                    }
                    Text("Fit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ChromeMuted)
                    listOf("Fit to screen", "Fit to width", "Fit to height", "Fit to canvas").forEach { action ->
                        ToolAction(InkIcon.Expand, action) {
                            when (action) {
                                "Fit to screen" -> canvas?.fitToScreen()
                                "Fit to width" -> canvas?.fitToWidth()
                                "Fit to height" -> canvas?.fitToHeight()
                                else -> canvas?.fitToCanvas()
                            }
                        }
                    }
                    ToolAction(InkIcon.Hand, "Pan tool", "Drag the page with a finger or pen") { mode = CanvasMode.Pan; sheet = null }
                }
                "gestures" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Write with finger", fontSize = 14.sp); Text("Off keeps finger and palm touches from writing", fontSize = 11.sp, color = ChromeMuted) }
                        Switch(fingerDrawing, onCheckedChange = { fingerDrawing = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Touch to zoom", fontSize = 14.sp); Text("Pinch with two fingers", fontSize = 11.sp, color = ChromeMuted) }
                        Switch(pinchToZoom, onCheckedChange = { pinchToZoom = it })
                    }
                    ToolToggle("Swipe left/right to change page", "Enabled by default in Single Page layout.", horizontalPageSwipe, { horizontalPageSwipe = it })
                    ToolToggle("Long press to select", "Long-press a drawn shape or text to select it for editing.", longPressSelectEnabled, { longPressSelectEnabled = it })
                    Text("With finger drawing off, one finger pans a zoomed page. Two-finger vertical drags scroll through pages in Scroll layout.", fontSize = 12.sp, color = ChromeMuted)
                }
                "customize toolbar" -> {
                    Text("Show tools on the side rail", fontSize = 13.sp, color = ChromeMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Toolbar on the right", Modifier.weight(1f), fontSize = 14.sp)
                        Switch(railOnRight, onCheckedChange = { railOnRight = it })
                    }
                    listOf("Template", "Layers", "Shapes", "Lasso", "Fill", "Pressure", "Gestures", "Table", "Stickers", "Sticky", "Ruler", "Zoom", "Page layout", "Text", "Insert", "Page +", "Share").forEach { tool ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(tool, Modifier.weight(1f), fontSize = 14.sp)
                            Switch(checked = tool in toolbarTools, onCheckedChange = { checked -> toolbarTools = if (checked) toolbarTools + tool else toolbarTools - tool })
                        }
                    }
                }
                "page layout" -> {
                        listOf("Single Page", "Scroll").forEach { layout ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            if (!notebook.infiniteCanvas) { canvas?.flushInput(); pageLayout = layout }
                        }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(layout, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(if (layout == "Scroll") "View and write across pages in one vertical flow" else "Show one page at a time", fontSize = 11.sp, color = ChromeMuted)
                            }
                                androidx.compose.material3.RadioButton(selected = effectivePageLayout == layout, onClick = {
                                    if (!notebook.infiniteCanvas) { canvas?.flushInput(); pageLayout = layout }
                            })
                        }
                    }
                    HorizontalDivider(color = ChromeLine)
                    if (effectivePageLayout == "Scroll") {
                        ToolToggle("Page boundary line", "Show a divider between pages in Scroll layout.", showPageBoundary, { showPageBoundary = it })
                        ToolToggle("Single-finger scroll", "Swipe vertically with one finger to move through pages. The stylus still writes.", singleFingerSwipe, { singleFingerSwipe = it })
                    }
                }
                "search" -> {
                    OutlinedTextField(draft, { draft = it }, label = { Text("Search text, outlines and tags") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val results = notebook.pages.filter { item -> draft.isNotBlank() && (item.outline + " " + item.texts.joinToString(" ") { it.text } + " " + item.strokes.flatMap { it.tags }.joinToString(" ")).contains(draft, ignoreCase = true) }
                    results.forEach { item -> ToolAction(InkIcon.Pages, "Page ${notebook.pages.indexOf(item) + 1}", item.outline.ifBlank { null }) { pageIndex = notebook.pages.indexOf(item); sheet = null } }
                    Text(if (draft.isNotBlank() && results.isEmpty()) "No matches. Handwriting recognition is not included yet." else "Search indexes typed text, outlines and tags.", fontSize = 11.sp, color = ChromeMuted)
                }
                "more" -> {
                    ToolAction(InkIcon.Rename, "Rename notebook") { draft = notebook.title; sheet = "rename" }
                    ToolAction(InkIcon.Search, "Search") { draft = ""; sheet = "search" }
                    ToolAction(InkIcon.Sticker, "Stickers", "Place an emoji sticker on the page") { sheet = "stickers" }
                    ToolAction(InkIcon.Sticky, "Sticky note", "Place a coloured note you can write on") { addStickyNote() }
                    ToolAction(InkIcon.Tag, "Notebook tags") { selectedIds = emptySet(); draft = ""; sheet = "tags" }
                    ToolAction(InkIcon.More, "Customize Toolbar") { sheet = "customize toolbar" }
                    if (clipboard.isNotEmpty()) ToolAction(InkIcon.Copy, "Paste strokes") { paste(); sheet = null }
                    ToolAction(InkIcon.Save, "Save") { save(); message = "Notebook saved." }
                    ToolAction(InkIcon.Microphone, "Recordings") { sheet = "recordings" }
                    if (!notebook.infiniteCanvas) ToolAction(InkIcon.Previous, "Delete this page") { actionPageId = page.id; sheet = "delete page" }
                    ToolAction(InkIcon.Trash, if (notebook.infiniteCanvas) "Clear canvas" else "Clear page") { sheet = "clear page" }
                }
                "clear page", "delete page" -> {
                    Text(if (dialog == "clear page") "Remove content from all visible, unlocked layers? Paper and protected layers are kept. Undo can restore it." else "Delete this page from the notebook?", fontSize = 14.sp)
                    TextButton(onClick = {
                        if (dialog == "clear page") {
                            canvas?.flushInput()
                            applyPage(page.copy(strokes = page.strokes.filterNot { editableLayer(it.layerId) }, texts = page.texts.filterNot { editableLayer(it.layerId) }, images = page.images.filterNot { editableLayer(it.layerId) }))
                            selectedIds = emptySet(); canvas?.deselectObject()
                        }
                        else {
                            val pages = notebook.pages.filterNot { it.id == actionPageId }
                            if (pages.isNotEmpty()) { onNotebookChange(notebook.copy(pages = pages, updatedAt = System.currentTimeMillis())); pageIndex = pageIndex.coerceAtMost(pages.lastIndex) }
                        }
                        sheet = null
                    }) { Text(if (dialog == "clear page") "Clear page" else "Delete page") }
                }
            }
        }
    }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Folio") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
    pendingPin?.let { location -> AlertDialog(onDismissRequest = { pendingPin = null }, title = { Text(if (editingPin == null) "Add pin" else "Rename pin") },
        text = { OutlinedTextField(pinName, { pinName = it }, label = { Text("Pin name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = pinName.isNotBlank(), onClick = {
            val current = latestNotebook
            val pin = editingPin?.copy(name = pinName.trim()) ?: CanvasPin(name = pinName.trim(), x = location.x, y = location.y, zoom = zoom)
            latestOnChange(current.copy(pins = if (editingPin == null) current.pins + pin else current.pins.map { if (it.id == pin.id) pin else it }, updatedAt = System.currentTimeMillis()))
            pendingPin = null; editingPin = null
        }) { Text("Save pin") } }, dismissButton = { TextButton(onClick = { pendingPin = null }) { Text("Cancel") } }) }
    markerToDelete?.let { id -> AlertDialog(
        onDismissRequest = { markerToDelete = null },
        title = { Text("Remove marker?") },
        text = { Text("This removes the selected item from the page.") },
        confirmButton = { TextButton(onClick = {
            canvas?.flushInput()
            applyPage(page.copy(texts = page.texts.filterNot { it.id == id && editableLayer(it.layerId) }))
            canvas?.deselectObject(); markerToDelete = null
        }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { markerToDelete = null }) { Text("Cancel") } },
    ) }

    @Composable fun Rail() {
        Column(Modifier.width(49.dp).fillMaxHeight().background(Color(0xFFFAFAFA))) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if ("Template" in toolbarTools && !notebook.infiniteCanvas) InkRailButton(InkIcon.Template, "Template", { openToolPanel("templates") })
                if (notebook.infiniteCanvas) InkRailButton(InkIcon.Template, "Paper", { sheet = "paper" })
                if (notebook.infiniteCanvas) InkRailButton(InkIcon.Tag, "Pins", { sheet = "pins" })
                if (notebook.infiniteCanvas) InkRailButton(InkIcon.Hand, "Pan", { canvas?.flushInput(); mode = if (mode == CanvasMode.Pan) CanvasMode.Write else CanvasMode.Pan }, mode == CanvasMode.Pan)
                if ("Layers" in toolbarTools) InkRailButton(InkIcon.Layers, "Layers", { openToolPanel("layers") })
                if ("Shapes" in toolbarTools) InkRailButton(InkIcon.Shapes, "Shapes", { canvas?.flushInput(); mode = CanvasMode.Shape; if (selectedBrush.eraser) selectedBrush = previousPen; selectedIds = emptySet(); sheet = "shapes" }, mode == CanvasMode.Shape)
                if ("Lasso" in toolbarTools) InkRailButton(InkIcon.Lasso, "Lasso", {
                    canvas?.flushInput()
                    if (mode == CanvasMode.Lasso) sheet = "lasso" else { mode = CanvasMode.Lasso; selectedIds = emptySet() }
                }, mode == CanvasMode.Lasso)
                if ("Fill" in toolbarTools) InkRailButton(InkIcon.Fill, "Fill", { canvas?.flushInput(); mode = CanvasMode.Fill; if (selectedIds.isNotEmpty()) sheet = "selection style" else sheet = "fill" }, mode == CanvasMode.Fill)
                if ("Pressure" in toolbarTools) InkRailButton(InkIcon.Pressure, "Pressure", { openToolPanel("pressure") })
                if ("Gestures" in toolbarTools) InkRailButton(InkIcon.Hand, "Gestures", { openToolPanel("gestures") })
                if ("Table" in toolbarTools) InkRailButton(InkIcon.Table, "Table", { openToolPanel("table") })
                if ("Stickers" in toolbarTools) InkRailButton(InkIcon.Sticker, "Stickers", { openToolPanel("stickers") })
                if ("Sticky" in toolbarTools) InkRailButton(InkIcon.Sticky, "Sticky", { addStickyNote() })
                if ("Ruler" in toolbarTools) InkRailButton(InkIcon.Ruler, "Ruler", { canvas?.flushInput(); rulerOn = !rulerOn }, rulerOn)
                if ("Zoom" in toolbarTools) InkRailButton(InkIcon.Zoom, "${(zoom * 100).toInt()}%", { openToolPanel("zoom") }, mode == CanvasMode.Pan)
                if ("Page layout" in toolbarTools && !notebook.infiniteCanvas) InkRailButton(InkIcon.Pages, "Page", { openToolPanel("page layout") })
                if ("Text" in toolbarTools) InkRailButton(InkIcon.Text, "Text", { openToolPanel("text") })
                if ("Insert" in toolbarTools) InkRailButton(InkIcon.Insert, "Insert", { openToolPanel("insert") })
                if ("Page +" in toolbarTools && !notebook.infiniteCanvas) InkRailButton(InkIcon.AddPage, "Page +", { openToolPanel("page+") })
                if ("Share" in toolbarTools) InkRailButton(InkIcon.Share, "Share", { openToolPanel("share & export") })
            }
            InkRailButton(InkIcon.More, "More", { openToolPanel("more") })
        }
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            if (!focusMode) {
                // Phones and narrow windows: the title gives way and fewer brushes show, so page controls always fit.
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val compactBar = screenWidthDp < 600
                val maxBrushes = if (compactBar) ((screenWidthDp - 290) / 36).coerceIn(1, 5) else 5
                Row(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFFFAFAFA)), verticalAlignment = Alignment.CenterVertically) {
                    InkToolButton(InkIcon.Expand, "Focus mode", { focusMode = true }, Modifier.width(48.dp), buttonSize = 44.dp)
                    Box(Modifier.width(1.dp).fillMaxHeight().background(ChromeLine))
                    InkToolButton(InkIcon.Back, backLabel, { save(); onBack() }, buttonSize = 32.dp)
                    if (compactBar) Spacer(Modifier.weight(1f)) else Box(Modifier.weight(1f).padding(horizontal = 5.dp).clickable { draft = notebook.title; sheet = "rename" }.semantics { contentDescription = "Rename notebook" }) {
                        Text(notebook.title, fontSize = 13.sp, color = ChromeInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 5.dp))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color(0xFFB8B8B8)))
                    }
                    Box {
                        // At most five brushes are visible; extra presets scroll inside this strip only.
                        val brushSlotPx = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
                        val brushListState = androidx.compose.foundation.lazy.rememberLazyListState()
                        val brushScrollable = presets.size > maxBrushes
                        LaunchedEffect(selectedBrush.presetId) {
                            val index = presets.indexOfFirst { it.presetId == selectedBrush.presetId }
                            val visible = brushListState.layoutInfo.visibleItemsInfo
                            if (index >= 0 && visible.isNotEmpty() && (index <= visible.first().index || index >= visible.last().index))
                                brushListState.animateScrollToItem((index - 2).coerceAtLeast(0))
                        }
                        LazyRow(Modifier.width((36 * presets.size.coerceIn(1, maxBrushes) - 4).dp).height(36.dp)
                            .then(if (brushScrollable) Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val fade = 10.dp.toPx()
                                    if (brushListState.canScrollBackward) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fade), size = androidx.compose.ui.geometry.Size(fade, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                                    if (brushListState.canScrollForward) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - fade, size.width), topLeft = Offset(size.width - fade, 0f), size = androidx.compose.ui.geometry.Size(fade, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                                } else Modifier),
                            state = brushListState, userScrollEnabled = brushScrollable, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(presets, key = { it.presetId }) { brush ->
                            BrushPreset(brush.name, brush.width, brush.color, !selectedBrush.eraser && selectedBrush.presetId == brush.presetId && mode == CanvasMode.Write && sheet == null,
                                id = brush.presetId, modifier = Modifier.animateItem(),
                                onClick = { if (selectedBrush.presetId == brush.presetId && mode == CanvasMode.Write) showBrush = !showBrush
                                    else { selectedBrush = brush; mode = CanvasMode.Write; selectedIds = emptySet() } },
                                onReorderDrag = { draggedId, delta ->
                                    brushDragRemainder += delta
                                    while (brushDragRemainder >= brushSlotPx && presets.indexOfFirst { it.presetId == draggedId } < presets.lastIndex) { presets = reorderByDrag(presets, draggedId, 1, BrushStyle::presetId); brushDragRemainder -= brushSlotPx }
                                    while (brushDragRemainder <= -brushSlotPx && presets.indexOfFirst { it.presetId == draggedId } > 0) { presets = reorderByDrag(presets, draggedId, -1, BrushStyle::presetId); brushDragRemainder += brushSlotPx }
                                }, onReorderEnd = { brushDragRemainder = 0f })
                            }
                        }
                        DropdownMenu(showBrush, onDismissRequest = { showBrush = false }, modifier = Modifier.penPressureFilter().width(360.dp).heightIn(max = 900.dp)) { BrushSettings(selectedBrush, brushOptions, ::updateBrush, ::addBrush, ::deleteBrush, drawingViewportWidthPx, panelXdpi, ::pickPageColor) }
                    }
                    ChromeDivider()
                    InkToolButton(InkIcon.Eraser, "Eraser", {
                        if (selectedBrush.eraser && mode == CanvasMode.Write) sheet = "eraser" else selectEraser()
                    }, selected = selectedBrush.eraser && mode == CanvasMode.Write && sheet == null)
                    InkToolButton(InkIcon.Undo, "Undo", ::undo, enabled = undoStack.isNotEmpty())
                    InkToolButton(InkIcon.Redo, "Redo", ::redo, enabled = redoStack.isNotEmpty())
                    ChromeDivider()
                    if (!notebook.infiniteCanvas) {
                    InkToolButton(InkIcon.Previous, "Previous page", { canvas?.flushInput(); pageIndex = safeIndex - 1 }, enabled = safeIndex > 0, buttonSize = 28.dp)
                    Box(Modifier.width(42.dp).height(44.dp).clickable { canvas?.flushInput(); showPages = true }.semantics { contentDescription = "Page manager, page ${safeIndex + 1} of ${notebook.pages.size}" }, contentAlignment = Alignment.Center) { Text("${safeIndex + 1}/${notebook.pages.size}", fontSize = 11.sp) }
                    InkToolButton(InkIcon.Next, "Next page or add page", { canvas?.flushInput(); if (safeIndex < notebook.pages.lastIndex) pageIndex = safeIndex + 1 else addPage() }, buttonSize = 28.dp)
                    } else InkToolButton(InkIcon.Tag, "Canvas pins", { sheet = "pins" })
                    if (saveError != null) InkToolButton(InkIcon.More, "Save failed, retry", { message = saveError; save() })
                    Spacer(Modifier.width(3.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ChromeLine))
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!focusMode && !railOnRight) { Rail(); Box(Modifier.width(1.dp).fillMaxHeight().background(ChromeLine)) }
                Box(Modifier.weight(1f).fillMaxHeight().onSizeChanged { drawingViewportWidthPx = it.width }) {
                    if (effectivePageLayout == "Scroll") {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val pageHeight = maxHeight
                            LazyColumn(state = pageListState, modifier = Modifier.fillMaxSize(), userScrollEnabled = !fingerDrawing && (notebook.infiniteCanvas || singleFingerSwipe)) {
                                items(notebook.pages, key = { it.id }) { model ->
                                    val index = notebook.pages.indexOfFirst { it.id == model.id }
                                    Column(Modifier.fillMaxWidth()) {
                                    NotebookPageCanvas(
                                        model = model, isActive = model.id == page.id, pageHeight = pageHeight,
                                        swipeEnabled = if (notebook.infiniteCanvas) !fingerDrawing else singleFingerSwipe && !fingerDrawing,
                                        stackedCanvas = true, brush = selectedBrush, threshold = threshold,
                                        pressureCurve = pressureCurve, pressureCurveEnabled = pressureCurveEnabled,
                                        velocityCurveEnabled = velocityCurveEnabled, velocityCurve = velocityCurve,
                                        tiltCurveEnabled = tiltCurveEnabled, tiltCurve = tiltCurve,
                                        mode = mode, shape = shape, shapeFilled = shapeFilled, shapeFillColor = shapeFillColor, shapeColor = shapeColor, shapeWidth = shapeWidth,
                                        shapeLineStyle = shapeLineStyle, fillColor = fillColor, eraserMode = eraserMode, eraserWidth = eraserWidth,
                                        eraseActiveLayerOnly = eraseActiveLayerOnly, lassoKind = lassoKind, fingerDrawing = fingerDrawing, pinchToZoom = pinchToZoom,
                                        activeLayerId = if (model.id == page.id) activeLayer else model.layers.firstOrNull()?.id ?: "ink",
                                        selectedIds = if (model.id == page.id) selectedIds else emptySet(),
                                        onVerticalFingerScroll = { delta -> pageListState.dispatchRawDelta(delta) },
                                        pageSwipeEnabled = effectivePageLayout == "Single Page" && !notebook.infiniteCanvas && horizontalPageSwipe,
                                        longPressSelectEnabled = longPressSelectEnabled && fingerDrawing,
                                        onLongPressSelection = { isObject -> if (model.id == page.id) { mode = if (isObject) CanvasMode.Objects else CanvasMode.MoveSelection; sheet = null } },
                                        onEmbeddedAction = { item -> if (model.id == page.id) openEmbedded(item) },
                                        onHorizontalFingerSwipe = { delta ->
                                            val destination = safeIndex + delta
                                            if (destination in notebook.pages.indices) { canvas?.flushInput(); pageIndex = destination }
                                        },
                                        onCanvas = { if (model.id == page.id) canvas = it },
                                        onPageChange = { changed ->
                                            if (model.id == page.id) applyPage(changed) else replacePage(changed)
                                            if (model.id != page.id) pageIndex = index
                                        },
                                        onSelection = { ids ->
                                            if (ids.isNotEmpty()) pageIndex = index
                                            selectedIds = ids
                                            if (ids.isEmpty() && mode == CanvasMode.MoveSelection) mode = CanvasMode.Lasso
                                            if (ids.isNotEmpty() && lassoAutoSwitch && mode == CanvasMode.Lasso) { selectedBrush = previousPen; mode = CanvasMode.Write }
                                        },
                                        onObjectSelection = { id, text -> if (model.id == page.id) { selectedObjectId = id; selectedObjectIsText = text } },
                                        onCanvasAction = { handleCanvasAction(it) }, canPaste = clipboard.isNotEmpty(),
                                        onColorPicked = { picked ->
                                            val receiver = pageColorReceiver; pageColorReceiver = null
                                            mode = colorReturnMode; sheet = colorReturnSheet; receiver?.invoke(picked)
                                        },
                                        onZoom = { view, value -> if (model.id == page.id) { zoom = value; viewportCenterX = view.viewportCenterX; viewportCenterY = view.viewportCenterY } },
                                        onEraseFinished = { if (autoReturnToPen) { selectedBrush = previousPen; mode = CanvasMode.Write } },
                                    )
                                    if (!notebook.infiniteCanvas && showPageBoundary && index < notebook.pages.lastIndex) HorizontalDivider(thickness = 3.dp, color = Color(0xFF9A9A9A))
                                    }
                                }
                            }
                        }
                    } else {
                        key(page.id) {
                            AndroidView(factory = { ctx -> InkCanvasView(ctx) { updated -> applyPage(page.copy(strokes = updated)) }.also { canvas = it } },
                                update = { view ->
                                    view.onStrokesChanged = { updated -> applyPage(page.copy(strokes = updated.filter { stroke -> page.layers.any { it.id == stroke.layerId } })) }
                                    view.onObjectsChanged = { texts, images -> applyPage(page.copy(texts = texts, images = images)) }
                                    view.onObjectSelectionChanged = { id, text -> selectedObjectId = id; selectedObjectIsText = text }
                                    view.onCanvasAction = { handleCanvasAction(it) }; view.canPaste = clipboard.isNotEmpty()
                                    view.brush = selectedBrush; view.threshold = threshold
                                    view.infiniteCanvas = notebook.infiniteCanvas
                                    view.onAddPin = { location -> editingPin = null; pendingPin = location; pinName = "Pin ${latestNotebook.pins.size + 1}" }
                                    view.pressureCurve = pressureCurve; view.pressureCurveEnabled = pressureCurveEnabled
                                    view.velocityCurveEnabled = velocityCurveEnabled; view.velocityCurve = velocityCurve
                                    view.tiltCurveEnabled = tiltCurveEnabled; view.tiltCurve = tiltCurve
                                    view.mode = mode; view.shapeKind = shape; view.shapeFilled = shapeFilled; view.shapeFillColor = shapeFillColor; view.shapeColor = shapeColor; view.shapeWidth = shapeWidth; view.shapeLineStyle = shapeLineStyle; view.fillColor = fillColor; view.onColorPicked = { picked -> val receiver = pageColorReceiver; pageColorReceiver = null; mode = colorReturnMode; sheet = colorReturnSheet; receiver?.invoke(picked) }; view.eraserMode = eraserMode
                                    view.rulerVisible = rulerOn
                                    view.selectedBlockId = selectedBlockId
                                    view.selectedCells = cellSelection
                                    view.onBlockTap = { id, tapX, tapY ->
                                        page.blocks.firstOrNull { it.id == id && it.kind == InkBlock.Table }?.cellAt(tapX, tapY)?.let { (row, column) ->
                                            val anchor = cellAnchor; val current = cellSelection
                                            if (anchor == null || current == null) { cellAnchor = row to column; cellSelection = listOf(row, column, row, column) }
                                            else if (current[0] == current[2] && current[1] == current[3] && anchor == (row to column)) { cellAnchor = null; cellSelection = null }
                                            else cellSelection = page.blocks.first { it.id == id }.expandToMerges(listOf(anchor.first, anchor.second, row, column))
                                        }
                                    }
                                    view.onBlockSelectionChanged = { id -> if (id != selectedBlockId) { cellSelection = null; cellAnchor = null }; selectedBlockId = id }
                                    view.onBlocksChanged = { updatedBlocks, updatedStrokes, updatedTexts, updatedImages -> applyPage(page.copy(blocks = updatedBlocks, strokes = updatedStrokes, texts = updatedTexts, images = updatedImages)) }
                                    view.zoomWindow = zoomBox; view.zoomWindowRatio = zoomRatio; view.onZoomWindowChanged = { zoomBox = it }
                                    view.eraserWidth = eraserWidth; view.eraseActiveLayerOnly = eraseActiveLayerOnly; view.lassoKind = lassoKind
                                    view.onEraseFinished = { if (autoReturnToPen) { selectedBrush = previousPen; mode = CanvasMode.Write } }
                                    view.layers = page.layers; view.activeLayerId = activeLayer; view.template = page.template
                                    view.fingerDrawing = fingerDrawing; view.pinchToZoomEnabled = pinchToZoom
                                    view.singlePageFingerSwipeEnabled = effectivePageLayout == "Single Page" && !notebook.infiniteCanvas && horizontalPageSwipe
                                    view.longPressSelectEnabled = longPressSelectEnabled && fingerDrawing
                                    view.onLongPressSelection = { isObject ->
                                        mode = if (isObject) CanvasMode.Objects else CanvasMode.MoveSelection
                                        sheet = null
                                    }
                                    view.onEmbeddedAction = ::openEmbedded
                                    view.onHorizontalFingerSwipe = { delta ->
                                        val destination = safeIndex + delta
                                        if (destination in notebook.pages.indices) { view.flushInput(); pageIndex = destination }
                                    }
                                    // Live erasing and drags are published once at gesture completion.
                                    if (!view.hasPendingEdits) { view.texts = page.texts; view.images = page.images; view.blocks = page.blocks; view.strokes = page.strokes }
                                    view.selectedStrokeIds = selectedIds; view.onSelectionChanged = { ids ->
                                        selectedIds = ids
                                        if (ids.isEmpty() && mode == CanvasMode.MoveSelection) mode = CanvasMode.Lasso
                                        if (ids.isNotEmpty() && lassoAutoSwitch && mode == CanvasMode.Lasso) { selectedBrush = previousPen; mode = CanvasMode.Write }
                                    }
                                    view.onZoomChanged = { zoom = it; viewportCenterX = view.viewportCenterX; viewportCenterY = view.viewportCenterY }
                            }, modifier = Modifier.fillMaxSize().clipToBounds().background(Color.White))
                        }
                    }
                    zoomBox?.let { box ->
                        fun moveBox(next: android.graphics.RectF) { zoomBox = next }
                        fun step(direction: Int) {
                            val w = box.width(); val h = box.height()
                            var left = box.left + direction * w * .9f; var top = box.top
                            if (!notebook.infiniteCanvas) {
                                if (left + w > 1.001f) { left = 0f; top += h * .9f } else if (left < 0f) { left = (1f - w).coerceAtLeast(0f); top -= h * .9f }
                                top = top.coerceIn(0f, (1f - h).coerceAtLeast(0f))
                            }
                            moveBox(android.graphics.RectF(left, top, left + w, top + h))
                        }
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.4f)
                            .shadow(10.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color.White, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).testTag("zoom-window-panel")) {
                            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                InkToolButton(InkIcon.Locate, "Move zoom box to view", {
                                    val w = box.width(); val h = box.height()
                                    moveBox(android.graphics.RectF(viewportCenterX - w / 2f, viewportCenterY - h / 2f, viewportCenterX + w / 2f, viewportCenterY + h / 2f).let { if (notebook.infiniteCanvas) it else android.graphics.RectF((it.left).coerceIn(0f, 1f - w), it.top.coerceIn(0f, (1f - h).coerceAtLeast(0f)), it.left.coerceIn(0f, 1f - w) + w, it.top.coerceIn(0f, (1f - h).coerceAtLeast(0f)) + h) })
                                })
                                Spacer(Modifier.weight(1f))
                                InkToolButton(InkIcon.Previous, "Zoom box left", { step(-1) })
                                InkToolButton(InkIcon.Next, "Zoom box right", { step(1) })
                                InkToolButton(InkIcon.Undo, "Undo", { undo() })
                                InkToolButton(InkIcon.Redo, "Redo", { redo() })
                                InkToolButton(InkIcon.Close, "Close zoom window", { zoomBox = null; if (mode == CanvasMode.ZoomWindow) mode = CanvasMode.Write })
                            }
                            HorizontalDivider(color = ChromeLine)
                            AndroidView(factory = { ctx -> InkCanvasView(ctx) { updated -> applyPage(page.copy(strokes = updated)) } },
                                modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged { zoomPanelSize = it }, update = { view ->
                                    view.onStrokesChanged = { updated -> applyPage(page.copy(strokes = updated.filter { stroke -> page.layers.any { it.id == stroke.layerId } })) }
                                    view.infiniteCanvas = notebook.infiniteCanvas
                                    view.documentAspect = (canvas?.let { if (it.width > 0) it.height.toFloat() / it.width else 0f }) ?: 0f
                                    view.mode = CanvasMode.Write; view.fingerDrawing = true; view.pinchToZoomEnabled = false
                                    view.brush = selectedBrush; view.threshold = threshold
                                    view.pressureCurve = pressureCurve; view.pressureCurveEnabled = pressureCurveEnabled
                                    view.velocityCurveEnabled = velocityCurveEnabled; view.velocityCurve = velocityCurve
                                    view.tiltCurveEnabled = tiltCurveEnabled; view.tiltCurve = tiltCurve
                                    view.layers = page.layers; view.activeLayerId = activeLayer; view.template = page.template
                                    if (!view.hasPendingEdits) { view.texts = page.texts; view.images = page.images; view.strokes = page.strokes }
                                    if (zoomPanelSize.width > 0) view.showRegion(box)
                                })
                        }
                    }
                    page.blocks.firstOrNull { it.id == selectedBlockId && it.kind == InkBlock.Table }?.let { block ->
                        BlockToolbar(block, cellSelection, { changed -> updateBlock { changed } }, { cellSelection = it; if (it == null) cellAnchor = null }, { editingStickyId = block.id },
                            { applyPage(page.copy(blocks = page.blocks.filterNot { it.id == block.id })); selectedBlockId = null; canvas?.deselectObject() },
                            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).testTag("block-toolbar"))
                    }
                    page.blocks.firstOrNull { it.id == editingStickyId }?.let { note ->
                        StickyTextDialog(note.text, { text -> applyPage(page.copy(blocks = page.blocks.map { if (it.id == note.id) it.copy(text = text) else it })); editingStickyId = null }, { editingStickyId = null })
                    }
                    if (mode == CanvasMode.ColorPicker) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            InkSymbol(InkIcon.Eyedropper, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Tap a color on the page", fontSize = 12.sp)
                            TextButton(onClick = { pageColorReceiver = null; mode = colorReturnMode; sheet = colorReturnSheet }) { Text("Cancel") }
                        }
                    }
                    if (notebook.infiniteCanvas) InfiniteCanvasNavigator(page, notebook.pins, viewportCenterX, viewportCenterY, zoom,
                        spanY = canvas?.viewportSpanY ?: 1f,
                        onJump = { x, y -> canvas?.jumpTo(x, y) }, onLocate = { canvas?.locateContent() }, onPins = { sheet = "pins" },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
                    if (zoom > 1.01f && !notebook.infiniteCanvas) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                .clickable { canvas?.resetViewport() }
                                .semantics { contentDescription = "Zoom ${(zoom * 100).toInt()} percent. Reset view" },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF7F7F7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine),
                            shadowElevation = 2.dp,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${(zoom * 100).toInt()}%", fontSize = 12.sp, color = ChromeInk)
                                Spacer(Modifier.width(8.dp))
                                Text("Reset", fontSize = 12.sp, color = Color(0xFF315B83))
                            }
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            shape = RoundedCornerShape(8.dp), color = Color(0xFFF7F7F7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine), shadowElevation = 2.dp,
                        ) {
                            Canvas(Modifier.size(112.dp, 76.dp).padding(8.dp).pointerInput(canvas, zoom) {
                                detectDragGestures(onDrag = { change, _ ->
                                    change.consume()
                                    val pageX = (change.position.x / size.width).coerceIn(0f, 1f)
                                    val pageY = (change.position.y / size.height).coerceIn(0f, 1f)
                                    canvas?.panToFraction(pageX, pageY)
                                }, onDragStart = { position ->
                                    canvas?.panToFraction((position.x / size.width).coerceIn(0f, 1f), (position.y / size.height).coerceIn(0f, 1f))
                                })
                            }) {
                                drawRect(ChromeLine, style = Stroke(1.dp.toPx()))
                                val viewWidth = (1f / zoom).coerceIn(.08f, 1f)
                                val viewHeight = viewWidth
                                val left = (viewportCenterX - viewWidth / 2f).coerceIn(0f, 1f - viewWidth)
                                val top = (viewportCenterY - viewHeight / 2f).coerceIn(0f, 1f - viewHeight)
                                drawRect(Color(0x663B79A8), topLeft = Offset(left * size.width, top * size.height),
                                    size = androidx.compose.ui.geometry.Size(viewWidth * size.width, viewHeight * size.height))
                                drawRect(Color(0xFF376CA3), topLeft = Offset(left * size.width, top * size.height),
                                    size = androidx.compose.ui.geometry.Size(viewWidth * size.width, viewHeight * size.height), style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                    if (mode == CanvasMode.Shape || mode == CanvasMode.Lasso || mode == CanvasMode.MoveSelection || (mode == CanvasMode.Write && selectedBrush.eraser)) {
                        val toolLabel = when (mode) {
                            CanvasMode.MoveSelection -> "Move · drag anywhere; any corner to resize"
                            CanvasMode.Shape -> "${shape.label} · ${if (shapeFilled && shape.closed) "Filled" else "Outline"}"
                            CanvasMode.Lasso -> when (lassoKind) { LassoKind.Smart -> "Smart lasso"; LassoKind.Rectangle -> "Rectangle selection"; else -> "Freehand lasso" }
                            else -> "${eraserMode.label} · ${if (eraseActiveLayerOnly) "Active layer" else "Editable layers"}"
                        }
                        var chipDrag by remember { mutableStateOf(Offset.Zero) }
                        var chipCenter by remember { mutableStateOf(Offset.Zero) }
                        var chipParent by remember { mutableStateOf(IntSize.Zero) }
                        val chipAlignment = when (toolChipCorner) { 0 -> Alignment.TopStart; 1 -> Alignment.TopEnd; 3 -> Alignment.BottomEnd; else -> Alignment.BottomStart }
                        Surface(Modifier.align(chipAlignment).padding(12.dp)
                            .offset { IntOffset(chipDrag.x.roundToInt(), chipDrag.y.roundToInt()) }
                            .onGloballyPositioned { coordinates ->
                                chipCenter = coordinates.positionInParent() + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                                chipParent = coordinates.parentLayoutCoordinates?.size ?: IntSize.Zero
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(onDragEnd = {
                                    // Snap to the nearest corner so the chip never rests over the middle of the page.
                                    toolChipCorner = (if (chipCenter.x > chipParent.width / 2f) 1 else 0) + (if (chipCenter.y > chipParent.height / 2f) 2 else 0)
                                    preferences.edit().putInt("tool-chip-corner", toolChipCorner).apply()
                                    chipDrag = Offset.Zero
                                }, onDragCancel = { chipDrag = Offset.Zero }) { change, amount -> change.consume(); chipDrag += amount }
                            }
                            .semantics { contentDescription = "Tool options. Drag to move to another corner" },
                            shape = RoundedCornerShape(20.dp), shadowElevation = if (chipDrag != Offset.Zero) 6.dp else 0.dp,
                            color = Color(0xFFF4F7F5), border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine)) {
                            TextButton(onClick = { sheet = when (mode) { CanvasMode.Shape -> "shapes"; CanvasMode.Lasso -> "lasso"; CanvasMode.MoveSelection -> "selection"; else -> "eraser" } }) {
                                Text(toolLabel, fontSize = 11.sp, color = ChromeInk)
                                Spacer(Modifier.width(8.dp)); InkSymbol(InkIcon.Tools, Modifier.size(16.dp))
                            }
                        }
                    }
                    // After Cut or Copy the selection is gone; this chip keeps Paste one tap away until the clipboard is cleared.
                    if (clipboard.isNotEmpty() && selectedIds.isEmpty() && selectedObjectId == null) {
                        Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), shape = RoundedCornerShape(22.dp), shadowElevation = 4.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine), color = Color.White) {
                            Row(Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = ::paste, modifier = Modifier.testTag("action-paste-clipboard")) {
                                    InkSymbol(InkIcon.Paste, Modifier.size(20.dp), ChromeInk)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (clipboard.size == 1) "Paste 1 item" else "Paste ${clipboard.size} items", color = ChromeInk, fontSize = 14.sp)
                                }
                                InkToolButton(InkIcon.Close, "Clear clipboard", { clipboard = emptyList() })
                            }
                        }
                    }
                    // Images and plain text use the bar next to the selection; cards (dates, recordings, links) keep this one.
                    val selectedCard = page.texts.firstOrNull { it.id == selectedObjectId && it.kind != "plain" }
                    if (selectedObjectId != null && selectedCard != null) {
                        val selectedText: InkText? = selectedCard
                        Surface(Modifier.align(Alignment.TopCenter).padding(10.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp) {
                            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(when (selectedText?.kind) { "timestamp" -> "Date marker"; "audio" -> "Recording"; "link-note", "link-web", "link-file" -> "Link"; else -> if (selectedObjectIsText) "Text box" else "Image" }, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                if (selectedText?.kind == "plain" || selectedText?.kind == "timestamp") InkToolButton(InkIcon.Rename, "Edit selected item", {
                                    selectedText?.let { editingTextId = it.id; draft = it.text; sheet = if (it.kind == "timestamp") "timestamp" else "edit text" }
                                })
                                if (selectedText == null || selectedText.kind == "plain") {
                                    InkToolButton(InkIcon.FlipHorizontal, "Mirror left-right", { transformObject(flipX = true) })
                                    InkToolButton(InkIcon.FlipVertical, "Mirror top-bottom", { transformObject(flipY = true) })
                                    InkToolButton(InkIcon.Rotate, "Rotate left 90°", { transformObject(rotation = -90f) })
                                    InkToolButton(InkIcon.RotateRight, "Rotate right 90°", { transformObject(rotation = 90f) })
                                }
                                if (selectedText != null && selectedText.kind in listOf("link-note", "link-web", "link-file", "audio")) InkToolButton(InkIcon.Next, "Open selected item", { openEmbedded(selectedText) })
                                InkToolButton(InkIcon.Trash, if (selectedText?.kind != "plain" && selectedText != null) "Remove marker" else "Delete selected object", {
                                    if (selectedText != null && selectedText.kind != "plain") markerToDelete = selectedText.id
                                    else {
                                        canvas?.flushInput()
                                        applyPage(page.copy(texts = page.texts.filterNot { it.id == selectedObjectId && editableLayer(it.layerId) },
                                            images = page.images.filterNot { it.id == selectedObjectId && editableLayer(it.layerId) }))
                                        canvas?.deselectObject()
                                    }
                                })
                                InkToolButton(InkIcon.Close, "Deselect object", { canvas?.deselectObject() })
                            }
                        }
                    }
                    if (focusMode) {
                        NoteToolbox(brushOptions, selectedBrush, { brush -> selectedBrush = brush; mode = CanvasMode.Write; selectedIds = emptySet() },
                            onExit = { focusMode = false }, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty(),
                            onAction = { tool -> when (tool) {
                                "Undo" -> undo(); "Redo" -> redo()
                                "Eraser" -> if (selectedBrush.eraser) { sheet = "eraser" } else selectEraser()
                                "Lasso" -> { canvas?.flushInput(); mode = CanvasMode.Lasso; selectedIds = emptySet() }
                                "Page +" -> addPage()
                                "Sticky" -> addStickyNote()
                                "Ruler" -> { canvas?.flushInput(); rulerOn = !rulerOn }
                                "Pages" -> { if (notebook.infiniteCanvas) sheet = "pins" else { focusMode = false; showPages = true } }
                                else -> sheet = when (tool) { "Templates" -> "templates"; "Pressure" -> "pressure"; "Share" -> "share & export"; else -> tool.lowercase(Locale.ROOT) }
                            } }, onRestoreBrushes = { restored -> brushOptions = restored; presets = restored; selectedBrush = restored.firstOrNull { it.presetId == selectedBrush.presetId } ?: restored.first() },
                            brushSettings = { BrushSettings(selectedBrush, brushOptions, ::updateBrush, ::addBrush, ::deleteBrush, drawingViewportWidthPx, panelXdpi, ::pickPageColor) },
                            widthMillimetres = { it.width })
                    }
                    if (recording) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 420.dp).fillMaxWidth(),
                        color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                InkSymbol(InkIcon.Microphone, Modifier.size(20.dp), Color(0xFF9F3030))
                                Text(when (repository.recordingStatus) {
                                    RecordingStatus.Starting -> "Starting recording…"
                                    RecordingStatus.Pausing -> "Pausing…"
                                    RecordingStatus.Paused -> "Recording paused"
                                    RecordingStatus.Resuming -> "Resuming…"
                                    RecordingStatus.Stopping -> "Saving recording…"
                                    else -> "Recording"
                                }, Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("%d:%02d".format(Locale.US, recordingElapsed / 60_000, (recordingElapsed / 1_000) % 60), fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (repository.recordingStatus == RecordingStatus.Paused) HorizontalDivider(thickness = 3.dp, color = ChromeLine)
                            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF9F3030))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    if (repository.recordingStatus == RecordingStatus.Paused) repository.resumeRecording(notebook.id)
                                    else repository.pauseRecording(notebook.id)
                                }, enabled = repository.recordingStatus in listOf(RecordingStatus.Recording, RecordingStatus.Paused)) {
                                    Text(if (repository.recordingStatus == RecordingStatus.Paused) "Resume" else "Pause")
                                }
                                TextButton(onClick = ::stopRecording, enabled = repository.recordingStatus in listOf(RecordingStatus.Recording, RecordingStatus.Paused)) { Text("Finish") }
                            }
                        }
                    }
                    activeAudio?.let { attachment ->
                        RecordingMiniPlayer(attachment, onClose = { activeAudio = null }, Modifier.align(Alignment.BottomCenter).padding(12.dp))
                    }
                }
                if (!focusMode && railOnRight) { Box(Modifier.width(1.dp).fillMaxHeight().background(ChromeLine)); Rail() }
            }
        }
        if (showPages && !focusMode) {
            NotebookPageManager(notebook, page.id,
                onSelect = { id -> canvas?.flushInput(); pageIndex = notebook.pages.indexOfFirst { it.id == id }.coerceAtLeast(0); showPages = false },
                onClose = { showPages = false }, onAdd = ::addPage, onPageAction = ::pageAction)
        }
        busy?.let { label ->
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .87f)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(Modifier.size(26.dp)); Text(label, Modifier.padding(top = 14.dp), fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun NotebookPageCanvas(
    model: NotebookPage,
    isActive: Boolean,
    pageHeight: androidx.compose.ui.unit.Dp,
    swipeEnabled: Boolean,
    stackedCanvas: Boolean,
    brush: BrushStyle,
    threshold: Float,
    pressureCurve: ResponseCurve,
    pressureCurveEnabled: Boolean,
    velocityCurveEnabled: Boolean,
    velocityCurve: ResponseCurve,
    tiltCurveEnabled: Boolean,
    tiltCurve: ResponseCurve,
    mode: CanvasMode,
    shape: ShapeKind,
    shapeFilled: Boolean,
    shapeFillColor: Int,
    shapeColor: Int,
    shapeWidth: Float,
    shapeLineStyle: ShapeLineStyle,
    fillColor: Int,
    eraserMode: EraserMode,
    eraserWidth: Float,
    eraseActiveLayerOnly: Boolean,
    lassoKind: LassoKind,
    fingerDrawing: Boolean,
    pinchToZoom: Boolean,
    pageSwipeEnabled: Boolean,
    longPressSelectEnabled: Boolean,
    onLongPressSelection: (Boolean) -> Unit,
    onEmbeddedAction: (InkText) -> Unit,
    onHorizontalFingerSwipe: (Int) -> Unit,
    activeLayerId: String,
    selectedIds: Set<String>,
    onVerticalFingerScroll: (Float) -> Unit,
    onCanvas: (InkCanvasView) -> Unit,
    onPageChange: (NotebookPage) -> Unit,
    onSelection: (Set<String>) -> Unit,
    onObjectSelection: (String?, Boolean) -> Unit,
    onCanvasAction: (CanvasAction) -> Unit,
    canPaste: Boolean,
    onColorPicked: (Int) -> Unit,
    onZoom: (InkCanvasView, Float) -> Unit,
    onEraseFinished: () -> Unit,
) {
    key(model.id) {
        AndroidView(
            factory = { ctx -> InkCanvasView(ctx) { updated -> onPageChange(model.copy(strokes = updated)) } },
            update = { view ->
                onCanvas(view)
                view.onStrokesChanged = { updated -> onPageChange(model.copy(strokes = updated.filter { s -> model.layers.any { it.id == s.layerId } })) }
                view.onObjectsChanged = { texts, images -> onPageChange(model.copy(texts = texts, images = images)) }
                view.onObjectSelectionChanged = { id, isText -> if (isActive) onObjectSelection(id, isText) }
                view.onCanvasAction = { if (isActive) onCanvasAction(it) }; view.canPaste = canPaste
                view.brush = brush; view.threshold = threshold
                view.pressureCurve = pressureCurve; view.pressureCurveEnabled = pressureCurveEnabled
                view.velocityCurveEnabled = velocityCurveEnabled; view.velocityCurve = velocityCurve
                view.tiltCurveEnabled = tiltCurveEnabled; view.tiltCurve = tiltCurve
                view.mode = mode; view.shapeKind = shape; view.shapeFilled = shapeFilled; view.shapeFillColor = shapeFillColor; view.shapeColor = shapeColor
                view.shapeWidth = shapeWidth; view.shapeLineStyle = shapeLineStyle; view.fillColor = fillColor
                view.onColorPicked = onColorPicked; view.eraserMode = eraserMode; view.eraserWidth = eraserWidth
                view.eraseActiveLayerOnly = eraseActiveLayerOnly; view.lassoKind = lassoKind
                view.onEraseFinished = onEraseFinished
                view.layers = model.layers; view.activeLayerId = activeLayerId; view.template = model.template
                view.fingerDrawing = fingerDrawing; view.pinchToZoomEnabled = pinchToZoom; view.singleFingerVerticalSwipe = swipeEnabled
                view.twoFingerVerticalScroll = stackedCanvas
                view.singlePageFingerSwipeEnabled = pageSwipeEnabled; view.onHorizontalFingerSwipe = onHorizontalFingerSwipe
                view.longPressSelectEnabled = longPressSelectEnabled
                view.onLongPressSelection = { isObject -> if (isActive) onLongPressSelection(isObject) }
                view.onEmbeddedAction = { item -> if (isActive) onEmbeddedAction(item) }
                view.onVerticalFingerScroll = onVerticalFingerScroll
                if (!view.hasPendingEdits) { view.texts = model.texts; view.images = model.images; view.strokes = model.strokes }
                view.selectedStrokeIds = selectedIds
                view.onSelectionChanged = { onSelection(it) }
                view.onZoomChanged = { onZoom(view, it) }
            },
            modifier = Modifier.fillMaxWidth().height(pageHeight).clipToBounds().background(Color.White),
        )
    }
}

@Composable
private fun RecordingMiniPlayer(attachment: NoteAttachment, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var ready by remember(attachment.id) { mutableStateOf(false) }
    var playing by remember(attachment.id) { mutableStateOf(false) }
    var duration by remember(attachment.id) { mutableIntStateOf(0) }
    var position by remember(attachment.id) { mutableIntStateOf(0) }
    var error by remember(attachment.id) { mutableStateOf(false) }
    val player = remember(attachment.id) { MediaPlayer() }
    DisposableEffect(player) {
        try {
            player.setDataSource(attachment.path)
            player.setOnPreparedListener {
                ready = true; duration = it.duration.coerceAtLeast(0)
                it.start(); playing = true
            }
            player.setOnCompletionListener { playing = false; position = duration }
            player.prepareAsync()
        } catch (_: Exception) { error = true }
        onDispose { runCatching { player.release() } }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(300)
        }
    }
    fun clock(milliseconds: Int): String = "%d:%02d".format(Locale.US, milliseconds / 60_000, (milliseconds / 1_000) % 60)
    Surface(modifier.widthIn(max = 420.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp,
        color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkSymbol(InkIcon.Microphone, Modifier.size(20.dp))
                Text(attachment.name.removeSuffix(".m4a"), Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                InkToolButton(InkIcon.Close, "Close player", onClose)
            }
            if (error) Text("Could not play this recording.", fontSize = 12.sp)
            else {
                Slider(value = position.coerceIn(0, duration.coerceAtLeast(1)).toFloat(),
                    onValueChange = { value -> position = value.toInt(); if (ready) player.seekTo(position) },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = ready, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${clock(position)} / ${clock(duration)}", fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(enabled = ready, onClick = { position = (position - 10_000).coerceAtLeast(0); player.seekTo(position) }) { Text("−10s") }
                    TextButton(enabled = ready, onClick = {
                        if (playing) { player.pause(); playing = false }
                        else { if (position >= duration) { position = 0; player.seekTo(0) }; player.start(); playing = true }
                    }) { Text(if (playing) "Pause" else "Play") }
                    TextButton(enabled = ready, onClick = { position = (position + 10_000).coerceAtMost(duration); player.seekTo(position) }) { Text("+10s") }
                    TextButton(enabled = ready, onClick = { player.pause(); player.seekTo(0); position = 0; playing = false }) { Text("Stop") }
                }
            }
        }
    }
}

@Composable
private fun BrushSettings(brush: BrushStyle, presets: List<BrushStyle>, onChange: (BrushStyle) -> Unit,
    onAdd: () -> Unit, onDelete: () -> Unit, viewportWidthPx: Int, xdpi: Float, onPickFromPage: ((Int) -> Unit) -> Unit) {
    val widthPx = brush.width
    var advanced by remember { mutableStateOf(false) }
    var showColorPicker by remember(brush.presetId) { mutableStateOf(false) }
    if (showColorPicker) BrushColorPicker(brush.color, { showColorPicker = false }, { color ->
        onChange(brush.copy(color = color)); showColorPicker = false
    }, { showColorPicker = false; onPickFromPage { onChange(brush.copy(color = (brush.color and 0xFF000000.toInt()) or (it and 0xFFFFFF))) } })
    val palette = listOf(0xFF252525, 0xFF555555, 0xFF888888, 0xFFBBBBBB, 0xFFFFFFFF, 0xFFB33E3E, 0xFF446C50, 0xFF345C91,
        0xFF7BB9B2, 0xFF9966A1, 0xFFB9916B, 0xFFEAC243, 0xFF344F41, 0xFF68537E, 0xFF76A9C0, 0xFFC48787)
    Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${brush.label} · ${String.format(Locale.US, "%.1f px", widthPx)}", fontSize = 12.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            InkToolButton(InkIcon.Rotate, "Reset brush to preset defaults", { onChange(BrushCatalog.reset(brush)) })
            InkToolButton(InkIcon.Add, "Add brush preset", onAdd)
            InkToolButton(InkIcon.Trash, "Delete brush preset", onDelete, enabled = presets.size > 1)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            brushes.filterNot { it.eraser }.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { type ->
                        val chosen = brush.name == type.name
                        Column(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (chosen) Color(0xFFE0F1EB) else Color(0xFFF6F7F6))
                            .border(if (chosen) 2.dp else 1.dp, if (chosen) Color(0xFF287463) else ChromeLine, RoundedCornerShape(6.dp))
                            .selectable(selected = chosen, role = Role.RadioButton,
                                onClick = { onChange(BrushCatalog.default(type.name).copy(presetId = brush.presetId)) })
                            .semantics { contentDescription = "Brush type ${type.name}" }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                             BrushGlyph(type.name, brush.color, Modifier.size(16.dp, 20.dp))
                             Text(type.name, fontSize = 8.sp, maxLines = 2, lineHeight = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BrushCatalog.forType(brush.name).forEach { preset ->
                Surface(Modifier.width(118.dp).clickable { onChange(preset.copy(presetId = brush.presetId)) }
                    .semantics { contentDescription = "Preset ${preset.label}"; selected = brush.variant == preset.variant },
                    shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(if (brush.variant == preset.variant) 2.dp else 1.dp, if (brush.variant == preset.variant) Color(0xFF287463) else ChromeLine)) {
                    Column(Modifier.padding(6.dp)) {
                        BrushPreview(preset)
                        Text(preset.label, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
                        Text(String.format(Locale.US, "%.1f px", preset.width), fontSize = 9.sp, lineHeight = 12.sp, color = ChromeMuted)
                    }
                }
            }
        }
        HorizontalDivider(color = ChromeLine)
        BrushPreview(brush)
        val range = BrushCatalog.widthRange(brush.name)
        val step = BrushCatalog.widthStep(brush.name)
        fun setWidth(value: Float) = onChange(brush.copy(width = ((value / step).roundToInt() * step).coerceIn(range)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", fontSize = 12.sp, modifier = Modifier.weight(1f))
            InkToolButton(InkIcon.Previous, "Decrease brush width", { setWidth(widthPx - step) }, buttonSize = 28.dp, enabled = widthPx > range.start)
            Text(String.format(Locale.US, "%.1f px", widthPx), fontSize = 12.sp)
            InkToolButton(InkIcon.Next, "Increase brush width", { setWidth(widthPx + step) }, buttonSize = 28.dp, enabled = widthPx < range.endInclusive)
        }
        VariableWidthSlider(widthPx.coerceIn(range), range.start, range.endInclusive, Modifier.fillMaxWidth().height(34.dp)) { setWidth(it) }
        Row { Text("Opacity", fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("${android.graphics.Color.alpha(brush.color) * 100 / 255}%", fontSize = 12.sp) }
        LineSlider(android.graphics.Color.alpha(brush.color) / 255f, .1f..1f, "Brush opacity") { onChange(brush.copy(color = (brush.color and 0x00FFFFFF) or ((it * 255).toInt() shl 24))) }
        if (BrushCatalog.pressureResponsive(brush.name)) {
            Row { Text("Pressure response", fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("${(brush.sensitivity * 100).toInt()}%", fontSize = 12.sp) }
            LineSlider(brush.sensitivity, 0f..1f, "${brush.label} pressure sensitivity") { onChange(brush.copy(sensitivity = it)) }
            Text("Higher makes light pressure fuller. Curves: Pressure in the toolbox.", fontSize = 10.sp, color = ChromeMuted)
        } else Text("Constant-width tip: pressure does not change this brush.", fontSize = 10.sp, color = ChromeMuted)
        val natural = NaturalMediaRenderer.supports(brush.name)
        if (natural || BrushCatalog.angled(brush.name)) {
            Row(Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (brush.name == "Paint brush") "Bristles & paint load" else if (natural) "Texture & angle" else "Nib angle", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(if (advanced) "Hide" else "Show", fontSize = 11.sp, color = Color(0xFF287463))
            }
        }
        if (advanced && (natural || BrushCatalog.angled(brush.name))) {
            if (natural) {
                Text("${if (brush.name == "Paint brush") "Dryness" else "Grain strength"} · ${(brush.texture * 100).toInt()}%", fontSize = 11.sp)
                LineSlider(brush.texture, 0f..1f, "Grain strength") { onChange(brush.copy(texture = it)) }
                Text("${if (brush.name == "Paint brush") "Bristle coarseness" else "Grain scale"} · ${String.format(Locale.US, "%.1f", brush.grain)}", fontSize = 11.sp)
                LineSlider(brush.grain, .4f..4f, "Grain scale") { onChange(brush.copy(grain = it)) }
            }
            if (BrushCatalog.angled(brush.name)) {
                Text("${if (natural) "Texture" else "Nib"} angle · ${brush.angle.toInt()}°", fontSize = 11.sp)
                LineSlider(brush.angle, -90f..90f, "Angle") { onChange(brush.copy(angle = it)) }
            }
        }
        Text("Sizes are canvas px at 100% zoom. Pressure brushes draw up to about 2× at full pressure.", fontSize = 10.sp, lineHeight = 12.sp, color = ChromeMuted)
        HorizontalDivider(color = ChromeLine)
        Row { Text("Colors", fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(String.format(Locale.US, "#%08X", brush.color), fontSize = 11.sp, color = ChromeMuted) }
        palette.chunked(8).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { value ->
                val ink = (value.toInt() and 0x00FFFFFF) or (brush.color and 0xFF000000.toInt())
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(value))
                    .border(if (brush.color == ink) 3.dp else 1.dp, if (brush.color == ink) ChromeInk else Color(0xFFBBBBBB), CircleShape)
                    .clickable { onChange(brush.copy(color = ink)) }
                    .semantics { contentDescription = String.format(Locale.US, "Ink color #%06X", value and 0xFFFFFF) })
            }
        } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showColorPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(brush.color or 0xFF000000.toInt())).border(1.dp, ChromeLine, CircleShape))
                Spacer(Modifier.width(5.dp)); Text("Custom color", fontSize = 10.sp, maxLines = 1)
            }
            OutlinedButton(onClick = { onPickFromPage { onChange(brush.copy(color = (brush.color and 0xFF000000.toInt()) or (it and 0xFFFFFF))) } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                InkSymbol(InkIcon.Eyedropper, Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text("Pick from page", fontSize = 10.sp, maxLines = 1)
            }
        }
        Text("Tap the selected brush again to edit. + creates an independent preset.", fontSize = 10.sp, color = ChromeMuted)
    }
}

private fun Notebook.withPageStrokes(pageId: String, strokes: List<InkStroke>): Notebook = copy(
    updatedAt = System.currentTimeMillis(),
    pages = pages.map { if (it.id == pageId) it.copy(strokes = strokes) else it },
)

/** Thin monochrome slider matching [VariableWidthSlider]; Material's default track is too heavy for tool panels. */
@Composable
internal fun LineSlider(value: Float, range: ClosedFloatingPointRange<Float>, label: String, modifier: Modifier = Modifier, onValueChange: (Float) -> Unit) {
    val currentChange by rememberUpdatedState(onValueChange)
    val span = (range.endInclusive - range.start).coerceAtLeast(.0001f)
    Canvas(
        modifier.fillMaxWidth().height(28.dp).semantics {
            contentDescription = label
            stateDescription = "${((value - range.start) / span * 100).roundToInt()}%"
        }.pointerInput(range) {
            fun update(x: Float) {
                val inset = 9.dp.toPx()
                currentChange(range.start + span * ((x - inset) / (size.width - inset * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f))
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                update(down.position.x)
                drag(down.id) { change -> update(change.position.x); change.consume() }
            }
        },
    ) {
        val left = 9.dp.toPx(); val right = size.width - left; val center = size.height / 2f
        val thumb = Offset(left + (right - left) * ((value - range.start) / span).coerceIn(0f, 1f), center)
        drawLine(ChromeLine, Offset(left, center), Offset(right, center), 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color(0xFF303030), Offset(left, center), Offset(thumb.x, center), 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, 9.dp.toPx(), thumb)
        drawCircle(Color(0xFF555555), 9.dp.toPx(), thumb, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
internal fun VariableWidthSlider(value: Float, minimum: Float, maximum: Float, modifier: Modifier = Modifier,
                                accessibilityLabel: String = "Line width", onValueChange: (Float) -> Unit) {
    val currentChange by rememberUpdatedState(onValueChange)
    Canvas(
        modifier.semantics {
            contentDescription = accessibilityLabel
            stateDescription = String.format(Locale.US, "%.1f mm", value)
        }.pointerInput(minimum, maximum) {
            fun update(x: Float) {
                val inset = 8.dp.toPx()
                val fraction = ((x - inset) / (size.width - inset * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                currentChange(minimum + (maximum - minimum) * fraction)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                update(down.position.x)
                drag(down.id) { change -> update(change.position.x); change.consume() }
            }
        },
    ) {
        val left = 8.dp.toPx()
        val right = size.width - left
        val center = size.height / 2f
        val fraction = ((value - minimum) / (maximum - minimum).coerceAtLeast(.001f)).coerceIn(0f, 1f)
        val thin = 1.dp.toPx()
        val thick = 9.dp.toPx()
        val path = Path().apply {
            moveTo(left, center - thin / 2f)
            lineTo(right, center - thick / 2f)
            lineTo(right, center + thick / 2f)
            lineTo(left, center + thin / 2f)
            close()
        }
        drawPath(path, Color(0xFF303030))
        val thumb = Offset(left + (right - left) * fraction, center)
        drawCircle(Color.White, 9.dp.toPx(), thumb)
        drawCircle(Color(0xFF555555), 9.dp.toPx(), thumb, style = Stroke(1.5.dp.toPx()))
    }
}
