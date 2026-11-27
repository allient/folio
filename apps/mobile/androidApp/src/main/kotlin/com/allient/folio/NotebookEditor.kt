package com.allient.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.derivedStateOf
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
    saveError: String? = null,
    backLabel: String = stringResource(R.string.editor_back_to_notebooks),
    startInFocusMode: Boolean = false,
) {
    val context = LocalContext.current
    // Tags stay English whatever the app language (see rememberEnglishResources).
    val english = rememberEnglishResources()
    // Messages set from callbacks resolve through the configuration-aware resources, so they follow the app language.
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("editor", Context.MODE_PRIVATE) }
    var showNoteTabs by remember { mutableStateOf(preferences.getBoolean("showNoteTabs", true)) }
    var closedNoteTabs by remember { mutableStateOf(preferences.getStringSet("closedNoteTabs", emptySet()).orEmpty().toSet()) }
    LaunchedEffect(notebook.id) {
        if (notebook.id in closedNoteTabs) {
            closedNoteTabs = closedNoteTabs - notebook.id
            preferences.edit().putStringSet("closedNoteTabs", closedNoteTabs).apply()
        }
    }
    // Tabs keep their position when selected; the saved order only grows at the end.
    val canvasSubtitle = stringResource(R.string.editor_kind_canvas)
    val notebookSubtitle = stringResource(R.string.editor_kind_notebook)
    val recentNotes = remember(repository.notebooks, notebook.id, closedNoteTabs, canvasSubtitle, notebookSubtitle) {
        val items = repository.notebooks.map { DocumentTab(it.id, it.title, if (it.infiniteCanvas) canvasSubtitle else notebookSubtitle, it.openedAt, it.deletedAt) }
        val previous = preferences.getString("noteTabOrder", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        val order = stableDocumentTabOrder(previous, items, notebook.id, closedNoteTabs)
        if (order != previous) preferences.edit().putString("noteTabOrder", order.joinToString("\n")).apply()
        order.mapNotNull { id -> items.firstOrNull { it.id == id } }
    }
    val files = remember { NotebookFiles(context) }
    val latestNotebook by rememberUpdatedState(notebook)
    val latestOnChange by rememberUpdatedState(onNotebookChange)
    var pageIndex by rememberSaveable(notebook.id) { mutableStateOf(notebook.pages.indexOfFirst { it.id == notebook.lastViewedPageId }.coerceAtLeast(0)) }
    val brushState = rememberBrushPresetsState(preferences, notebook.id)
    var selectedBrush by brushState::selected
    var previousPen by remember(notebook.id) { mutableStateOf(brushState.options.first()) }
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
    var shapeBorderNone by remember { mutableStateOf(preferences.getBoolean("shapeBorderNone", false)) }
    val safeIndex = pageIndex.coerceIn(0, notebook.pages.lastIndex)
    val pageListState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val savedPage = notebook.pages[safeIndex]
    var page by remember(savedPage.id) { mutableStateOf(savedPage) }
    var slideInPageId by remember { mutableStateOf<String?>(null) }
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
    var drawingViewportHeightPx by remember { mutableIntStateOf(0) }
    // Pages keep the shape they have full screen. In split screen or a freeform window the drawing area is smaller, and
    // squashing the page to fit it distorted images and ink; instead the page keeps its aspect and scrolls (0 = fill the view).
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val reducedWindow = remember(configuration) { context.isReducedWindow() }
    val landscapeDisplay = remember(configuration) { context.displayIsLandscape() }
    val fullPageAspectKey = if (landscapeDisplay) "page-aspect-landscape" else "page-aspect-portrait"
    LaunchedEffect(reducedWindow, drawingViewportWidthPx, drawingViewportHeightPx, fullPageAspectKey) {
        if (!reducedWindow && drawingViewportWidthPx > 0 && drawingViewportHeightPx > 0)
            preferences.edit().putFloat(fullPageAspectKey, drawingViewportHeightPx.toFloat() / drawingViewportWidthPx).apply()
    }
    val pageAspect = if (!reducedWindow || notebook.infiniteCanvas) 0f
        else preferences.getFloat(fullPageAspectKey, 0f).takeIf { it > 0f } ?: context.estimatedFullPageAspect(drawingViewportWidthPx, drawingViewportHeightPx)
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
    LaunchedEffect(shapeColor, shapeWidth, shapeLineStyle, fillColor, lassoAutoSwitch, shapeFilled, shapeFillColor, shapeBorderNone) {
        preferences.edit().putInt("shapeColor", shapeColor).putFloat("shapeWidth", shapeWidth)
            .putString("shapeLineStyle", shapeLineStyle.name).putInt("fillColor", fillColor)
            .putBoolean("lassoAutoSwitch", lassoAutoSwitch)
            .putBoolean("shapeFilled", shapeFilled).putInt("shapeFillColor", shapeFillColor)
            .putBoolean("shapeBorderNone", shapeBorderNone).apply()
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
    /** Inserts a page with [template] at [position]; keeps unsaved edits of the current page. */
    fun insertTemplatePage(template: String, position: String, navigate: Boolean = true, added: NotebookPage = NotebookPage(template = template)): NotebookPage {
        canvas?.flushInput()
        val currentPages = latestNotebook.pages.map { if (it.id == page.id) page else it }.toMutableList()
        val insertAt = when (position) { "Before" -> safeIndex; "Last Page" -> currentPages.size; else -> safeIndex + 1 }.coerceIn(0, currentPages.size)
        currentPages.add(insertAt, added)
        onNotebookChange(latestNotebook.copy(pages = currentPages, updatedAt = System.currentTimeMillis()))
        if (navigate) { slideInPageId = added.id; pageIndex = insertAt }
        return added
    }
    // Single-page layout: the page follows a sideways drag. Released past the threshold it slides to the next or previous
    // page; on the last page, dragging left reveals "Release to add page" and adds one.
    val pageShift = remember { Animatable(0f) }
    var pageTurning by remember { mutableStateOf(false) }
    val minTurnPx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    val pageTurnThresholdPx = maxOf(minTurnPx, drawingViewportWidthPx * .22f)
    // Neighbour pages are rendered ahead as full-quality snapshots, so the sliding preview matches the real page.
    val snapshotPages = effectivePageLayout == "Single Page"
    val snapshotContext = androidx.compose.ui.platform.LocalContext.current
    val pendingPage = remember(page.template, notebook.pages.size) { NotebookPage(template = page.template) } // the page a swipe past the end adds
    val previousSnapshot by rememberPageSnapshot(if (snapshotPages) notebook.pages.getOrNull(safeIndex - 1) else null, drawingViewportWidthPx, drawingViewportHeightPx, snapshotContext.filesDir)
    val nextSnapshot by rememberPageSnapshot(if (snapshotPages) (notebook.pages.getOrNull(safeIndex + 1) ?: pendingPage) else null, drawingViewportWidthPx, drawingViewportHeightPx, snapshotContext.filesDir)
    var addingPage by remember { mutableStateOf(false) }
    var landingSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var lastDrag by remember { mutableStateOf(0f to 0L) }
    var dragVelocity by remember { mutableStateOf(0f) } // px per ms, negative = toward the next page
    val flickMinPx = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    // Grey gutter between two pages while they slide, so the page edges are easy to see.
    val pageGapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    fun onPageDrag(dx: Float, released: Boolean) {
        if (pageTurning || notebook.infiniteCanvas) return
        val width = drawingViewportWidthPx.toFloat().coerceAtLeast(1f)
        val canPrevious = safeIndex > 0
        val now = System.currentTimeMillis()
        if (!released) {
            val (previousDx, previousTime) = lastDrag
            if (previousTime > 0L && now > previousTime) dragVelocity = dragVelocity * .4f + (dx - previousDx) / (now - previousTime) * .6f
            lastDrag = dx to now
            // Leftward is always allowed (next page or add page); rightward on the first page only stretches a little.
            val shift = if (dx < 0f || canPrevious) dx else dx * .25f
            scope.launch { pageShift.snapTo(shift.coerceIn(-width - pageGapPx, width + pageGapPx)) }
            return
        }
        val velocity = if (now - lastDrag.second < 120L) dragVelocity else 0f
        lastDrag = 0f to 0L; dragVelocity = 0f
        val atLast = safeIndex >= latestNotebook.pages.lastIndex
        // A quick flick turns the page too; on the last page, adding a page needs the full "Release to add page" pull.
        val forward = dx <= -pageTurnThresholdPx || (!atLast && dx < -flickMinPx && velocity < -.6f)
        val backward = canPrevious && (dx >= pageTurnThresholdPx || (dx > flickMinPx && velocity > .6f))
        scope.launch {
            pageTurning = true
            try {
                if (forward || backward) {
                    val direction = if (forward) -1f else 1f
                    canvas?.flushInput()
                    val newPage = if (forward && atLast) pendingPage.also { addingPage = true } else null
                    // Slide the current page out and the preview fully in, then swap in the real page at the same spot.
                    val remaining = (width - kotlin.math.abs(pageShift.value)) / width
                    pageShift.animateTo(direction * (width + pageGapPx), tween((140 + 200 * remaining).toInt(), easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                    val target = when {
                        backward -> safeIndex - 1
                        newPage == null -> safeIndex + 1
                        else -> latestNotebook.pages.size.also { insertTemplatePage(page.template, "Last Page", navigate = false, added = newPage) }
                    }
                    landingSnapshot = if (forward) nextSnapshot else previousSnapshot
                    pageIndex = target
                    pageShift.snapTo(0f)
                    addingPage = false
                    delay(160) // the new canvas has drawn by now; drop the preview underneath it
                    landingSnapshot = null
                } else pageShift.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = .85f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
            } finally { pageTurning = false; addingPage = false }
        }
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
            undoStack = (undoStack + page).takeLast(80); page = redoStack.last(); redoStack = redoStack.dropLast(1); selectedIds = emptySet(); if (page.layers.none { it.id == activeLayer }) activeLayer = page.layers.first().id; replacePage(page)
        }
    }
    fun updateBrush(brush: BrushStyle) { brushState.update(brush); mode = CanvasMode.Write }
    fun addBrush() { brushState.add(if (selectedBrush.eraser) previousPen else selectedBrush); mode = CanvasMode.Write; showBrush = true }
    fun deleteBrush() { if (brushState.delete()) mode = CanvasMode.Write }
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
            }.onFailure { message = resources.getString(R.string.editor_error_open_website) }
            "link-file" -> notebook.attachments.firstOrNull { it.id == text.target }?.let { attachment ->
                runCatching { files.openAttachment(attachment) }.onFailure { message = resources.getString(R.string.editor_error_open_file) }
            } ?: run { message = resources.getString(R.string.editor_error_file_missing) }
            "audio" -> activeAudio = notebook.attachments.firstOrNull { it.id == text.target }
                ?: run { message = resources.getString(R.string.editor_error_recording_missing); null }
        }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else message = resources.getString(R.string.editor_error_microphone_permission)
    }
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pendingExportPath?.let(::File); pendingExportPath = null
        if (uri != null && file != null) scope.launch {
            busy = resources.getString(R.string.editor_busy_saving_document)
            try { withContext(Dispatchers.IO) { files.writeToUri(file, uri) } }
            catch (error: Exception) { message = error.message ?: resources.getString(R.string.editor_error_save_document) }
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
            busy = resources.getString(R.string.editor_busy_preparing, kind.uppercase())
            try {
                val output = withContext(Dispatchers.IO) { when (kind) {
                    "pdf" -> files.exportPdf(snapshot); "png" -> files.exportPng(snapshot, exportedPage); else -> files.exportBackup(snapshot)
                } }
                AppAnalytics.event("notebook_exported", "format" to kind)
                if (share) files.shareFile(output, when(kind) { "pdf" -> "application/pdf"; "png" -> "image/png"; else -> "application/octet-stream" })
                else { pendingExportPath = output.absolutePath; saveDocument.launch(output.name) }
            } catch (error: Exception) { message = error.message ?: resources.getString(R.string.editor_error_export) }
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
            // A dropped image is selected in the current tool, like a new sticker: drag to move, handles to resize and rotate.
            if (importNotice.kind == "image" && importNotice.pageId == page.id) importNotice.selectImageIds.lastOrNull()?.let { id ->
                selectedBlockId = null; sheet = null; canvas?.post { canvas?.selectObject(id, isText = false) }
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
        message = resources.getString(R.string.editor_error_choose_layer); return false
    }
    fun dropImage(uri: android.net.Uri, pageId: String, point: Offset, pageAspect: Float): Boolean {
        if (!requireEditableLayer()) return false
        repository.importIntoNotebook(uri, notebook.id, "image", pageId, activeLayer, ImageDrop(point.x, point.y, pageAspect))
        return true
    }
    /** A page-normalised rectangle of [width] centred on the current view; [aspect] is on-screen height / width. */
    fun placeBlock(width: Float, aspect: Float): android.graphics.RectF {
        val cw = (canvas?.width ?: 1).coerceAtLeast(1).toFloat()
        val documentHeight = if (notebook.infiniteCanvas) cw * 1.6f else cw * (canvas?.pageAspect ?: 1f)
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
        val layer = activeLayer
        scope.launch {
            // Rendering and writing the PNG is file I/O: keep it off the main thread.
            val file = try { withContext(Dispatchers.IO) { renderStickerFile(context, emoji) } }
                catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (_: Exception) { message = resources.getString(R.string.editor_error_sticker); return@launch }
            canvas?.flushInput()
            val sticker = InkImage(path = file.absolutePath, x = box.left, y = box.top, width = box.width(), height = box.height(), layerId = layer, z = frontUnderInk())
            applyPage(page.copy(images = page.images + sticker))
            // Selected right away in the current tool, like a new shape: drag to move, handles to resize.
            selectedBlockId = null; sheet = null; canvas?.selectObject(sticker.id, isText = false)
        }
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
        if (layer?.locked == true || layer?.visible == false) { message = resources.getString(R.string.editor_error_choose_layer); return }
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
            CanvasAction.Fill -> sheet = when (page.blocks.firstOrNull { it.id == selectedBlockId }?.kind) {
                InkBlock.Sticky -> "note style"; InkBlock.Table -> "table style"; else -> "selection style" }
            CanvasAction.Arrange -> sheet = "arrange"
            CanvasAction.More -> sheet = "selection"
            CanvasAction.Edit -> if (page.blocks.any { it.id == selectedBlockId && it.kind == InkBlock.Table }) sheet = "table"
                else page.blocks.firstOrNull { it.id == selectedBlockId && it.kind == InkBlock.Sticky }?.let { editingStickyId = it.id }
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
        ToolDialog(editorSheetTitle(dialog), { sheet = null },
            expanded = dialog == "shapes",
            footer = if (dialog == "shapes") { {
                Button(onClick = { canvas?.flushInput(); mode = CanvasMode.Shape; if (selectedBrush.eraser) selectedBrush = previousPen; selectedIds = emptySet(); sheet = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.editor_draw_shape)) }
            } } else null, tag = when (dialog) { "edit text" -> "Text box"; "to text" -> "Convert to text"; "tags" -> "Tags"; "outline" -> "Page outline"; else -> dialog.replaceFirstChar { it.uppercase() } }) {
            when (dialog) {
                "paper" -> {
                    val paper = com.allient.folio.InfinitePaper.parse(page.template) ?: com.allient.folio.InfinitePaper("blank")
                    fun set(next: com.allient.folio.InfinitePaper) { canvas?.flushInput(); applyPage(page.copy(template = next.template)) }
                    Text(stringResource(R.string.editor_paper_pattern), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.allient.folio.InfinitePaper.patterns.forEach { (id, label) ->
                            androidx.compose.material3.FilterChip(selected = paper.pattern == id, onClick = { set(paper.copy(pattern = id)) }, label = { Text(com.allient.folio.infinitePaperPatternName(id).removeSuffix(" paper"), fontSize = 12.sp) })
                        }
                    }
                    Text(stringResource(R.string.editor_paper_background), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.allient.folio.InfinitePaper.colors.forEach { (id, label) ->
                            androidx.compose.material3.FilterChip(selected = paper.color == id, onClick = { set(paper.copy(color = id)) }, label = { Text(com.allient.folio.infinitePaperColorName(id), fontSize = 12.sp) },
                                leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(com.allient.folio.InfinitePaper.colorArgb(id))).border(1.dp, ChromeLine, CircleShape)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.editor_paper_spacing, paper.spacing), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(88.dp))
                        androidx.compose.material3.Slider(paper.spacing.toFloat(), { set(paper.copy(spacing = it.roundToInt().coerceIn(1, 8))) }, Modifier.weight(1f), enabled = paper.pattern != "blank", valueRange = 1f..8f, steps = 6)
                    }
                }
                "table" -> TableSheet(tableDraft, { tableDraft = it }, ::addTable)
                "stickers" -> StickerSheet(::addSticker)
                "pins" -> {
                    Text(stringResource(R.string.editor_pins_intro), fontSize = 12.sp, color = ChromeMuted)
                    Text(stringResource(R.string.editor_pins_help), fontSize = 12.sp)
                    TextButton(onClick = { pendingPin = Offset(viewportCenterX, viewportCenterY); editingPin = null; pinName = resources.getString(R.string.editor_pin_default_name, notebook.pins.size + 1) }) { Text(stringResource(R.string.editor_pin_current_view)) }
                    notebook.pins.forEach { pin ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            InkSymbol(InkIcon.Tag)
                            Text(pin.name, Modifier.weight(1f).clickable { canvas?.jumpTo(pin.x, pin.y, pin.zoom); sheet = null }.padding(12.dp), fontSize = 13.sp)
                            Box {
                                InkToolButton(InkIcon.More, stringResource(R.string.editor_pin_options, pin.name), { pinMenu = pin.id }, tag = english.getString(R.string.editor_pin_options, pin.name))
                                DropdownMenu(pinMenu == pin.id, { pinMenu = null }, modifier = Modifier.penPressureFilter()) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.editor_rename)) }, onClick = { editingPin = pin; pinName = pin.name; pendingPin = Offset(pin.x, pin.y); pinMenu = null })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.editor_delete)) }, onClick = { latestOnChange(latestNotebook.copy(pins = latestNotebook.pins.filterNot { it.id == pin.id }, updatedAt = System.currentTimeMillis())); pinMenu = null })
                                }
                            }
                        }
                        HorizontalDivider(color = ChromeLine)
                    }
                    Text(pluralStringResource(R.plurals.editor_pin_count, notebook.pins.size, notebook.pins.size), fontSize = 11.sp, color = ChromeMuted)
                }
                "templates" -> {
                    var applyAll by remember { mutableStateOf(false) }
                    Text(stringResource(R.string.editor_select_template), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NotebookTemplateCatalog.categories.forEach { category ->
                            TextButton(onClick = { templateCategory = category }) { Text(com.allient.folio.templateCategoryName(category), color = if (templateCategory == category) ChromeInk else ChromeMuted, fontWeight = if (templateCategory == category) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = !applyAll, onClick = { applyAll = false })
                        Text(stringResource(R.string.editor_current_page), Modifier.clickable { applyAll = false }, fontSize = 11.sp)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.RadioButton(selected = applyAll, onClick = { applyAll = true })
                        Text(stringResource(R.string.editor_all_pages), Modifier.clickable { applyAll = true }, fontSize = 11.sp)
                    }
                    val recentTemplates = notebook.pages.asReversed().map { it.template }.distinct().take(8)
                    val shown = NotebookTemplateCatalog.inCategory(templateCategory, recentTemplates).map { it.id to com.allient.folio.templateName(it) }
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
                "pens" -> BrushSettings(if (selectedBrush.eraser) previousPen else selectedBrush, brushState.options, ::updateBrush, ::addBrush, ::deleteBrush, ::pickPageColor)
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
                        Text(stringResource(R.string.editor_line_style), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        Text(stringResource(R.string.editor_line_width), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.editor_width_mm, shapeWidthMm), fontSize = 12.sp, color = ChromeMuted)
                    }
                    VariableWidthSlider(shapeWidthMm, shapeMinMm, shapeMaxMm, Modifier.fillMaxWidth().height(34.dp), stringResource(R.string.editor_shape_line_width)) { shapeWidth = brushWidthDocumentUnits(it, drawingViewportWidthPx, panelXdpi).coerceIn(.5f, 32f) }
                    HorizontalDivider(color = ChromeLine)
                    // A shape always keeps something visible: no border turns the fill on, no fill brings the border back.
                    InkColorOptions(shapeColor, { shapeColor = it; shapeBorderNone = false }, ::pickPageColor,
                        onClear = { shapeBorderNone = true; shapeFilled = true }, transparentSelected = shapeBorderNone, title = stringResource(R.string.editor_border))
                    HorizontalDivider(color = ChromeLine)
                    InkColorOptions(shapeFillColor, { shapeFillColor = it; shapeFilled = true }, ::pickPageColor,
                        onClear = { shapeFilled = false; shapeBorderNone = false }, transparentSelected = !shapeFilled, title = stringResource(R.string.editor_fill))
                }
                "eraser" -> EraserControls(eraserMode,
                    brushWidthMillimetres(eraserWidth, drawingViewportWidthPx, panelXdpi),
                    brushWidthMillimetres(4f, drawingViewportWidthPx, panelXdpi),
                    brushWidthMillimetres(80f, drawingViewportWidthPx, panelXdpi), eraseActiveLayerOnly, autoReturnToPen,
                    onMode = { eraserMode = it; selectEraser() }, onWidth = { eraserWidth = brushWidthDocumentUnits(it, drawingViewportWidthPx, panelXdpi).coerceIn(4f, 80f) },
                    onScope = { eraseActiveLayerOnly = it }, onAutoReturn = { autoReturnToPen = it },
                    onClearLayer = { sheet = "erase layer ink" }, onClearAll = { sheet = "erase all ink" })
                "erase layer ink", "erase all ink" -> {
                    Text(if (dialog == "erase layer ink") stringResource(R.string.editor_clear_layer_question) else stringResource(R.string.editor_clear_all_layers_question), fontSize = 14.sp)
                    Text(stringResource(R.string.editor_clear_layers_detail), fontSize = 12.sp, color = ChromeMuted)
                    TextButton(onClick = {
                        canvas?.flushInput()
                        val shouldClear = { layerId: String -> dialog == "erase all ink" || layerId == activeLayer }
                        applyPage(page.copy(strokes = page.strokes.filterNot { shouldClear(it.layerId) },
                            texts = page.texts.filterNot { shouldClear(it.layerId) },
                            images = page.images.filterNot { shouldClear(it.layerId) }))
                        selectedIds = emptySet(); sheet = null
                        if (autoReturnToPen) { selectedBrush = previousPen; mode = CanvasMode.Write }
                    }) { Text(stringResource(R.string.editor_clear_layer)) }
                }
                "lasso" -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LassoKind.entries.forEach { kind -> DrawingChoice(when (kind) { LassoKind.Smart -> stringResource(R.string.editor_lasso_smart); LassoKind.Freeform -> stringResource(R.string.editor_lasso_freehand); LassoKind.Rectangle -> stringResource(R.string.editor_lasso_rectangle) }, kind == lassoKind, {
                                canvas?.flushInput(); lassoKind = kind; mode = CanvasMode.Lasso; selectedIds = emptySet(); sheet = null
                            }, Modifier.weight(1f), tag = when (kind) { LassoKind.Smart -> "Smart lasso"; LassoKind.Freeform -> "Freehand"; LassoKind.Rectangle -> "Rectangle" }) { InkSymbol(if (kind == LassoKind.Rectangle) InkIcon.RectangleSelect else InkIcon.Lasso) } }
                        }
                        ToolAction(InkIcon.Layers, stringResource(R.string.editor_select_all), stringResource(R.string.editor_select_all_detail)) {
                            selectedIds = page.strokes.filter { editableLayer(it.layerId) }.map { it.id }.toSet()
                            mode = CanvasMode.Lasso; sheet = null
                        }
                        ToolToggle(stringResource(R.string.editor_lasso_auto_switch), stringResource(R.string.editor_lasso_auto_switch_detail), lassoAutoSwitch, { lassoAutoSwitch = it })
                    }
                    Text(if (lassoKind == LassoKind.Smart) stringResource(R.string.editor_lasso_smart_help) else stringResource(R.string.editor_lasso_help), fontSize = 12.sp, color = ChromeMuted)
                    if (clipboard.isNotEmpty()) ToolAction(InkIcon.Copy, stringResource(R.string.editor_paste_strokes)) { paste(); sheet = null }
                }
                "selection" -> {
                    // Only what the bar beside the selection does not already show.
                    ToolAction(InkIcon.Text, stringResource(R.string.editor_convert_to_text), stringResource(R.string.editor_convert_to_text_detail)) { canvas?.flushInput(); sheet = "to text" }
                    ToolAction(InkIcon.Cut, stringResource(R.string.editor_cut), stringResource(R.string.editor_cut_detail)) { canvas?.flushInput(); clipboard = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) }; deleteSelection(); sheet = null }
                    ToolAction(InkIcon.Hand, stringResource(R.string.editor_move_selection), stringResource(R.string.editor_move_selection_detail)) { canvas?.flushInput(); mode = CanvasMode.MoveSelection; sheet = null }
                    ToolAction(InkIcon.Add, stringResource(R.string.editor_larger)) { transformSelection(scale = 1.1f); sheet = null }
                    ToolAction(InkIcon.Zoom, stringResource(R.string.editor_smaller)) { transformSelection(scale = 1f / 1.1f); sheet = null }
                    ToolAction(InkIcon.Layers, stringResource(R.string.editor_move_to_layer)) { sheet = "selection layer" }
                    ToolAction(InkIcon.BringFront, stringResource(R.string.editor_bring_to_front), stringResource(R.string.editor_bring_to_front_detail)) {
                        canvas?.flushInput(); applyPage(page.copy(strokes = page.strokes.filterNot { it.id in selectedIds } + page.strokes.filter { it.id in selectedIds })); sheet = null
                    }
                    ToolAction(InkIcon.SendBack, stringResource(R.string.editor_send_to_back), stringResource(R.string.editor_send_to_back_detail)) {
                        canvas?.flushInput(); applyPage(page.copy(strokes = page.strokes.filter { it.id in selectedIds } + page.strokes.filterNot { it.id in selectedIds })); sheet = null
                    }
                    ToolAction(InkIcon.Tag, stringResource(R.string.editor_tag_selection)) { draft = ""; sheet = "tags" }
                    ToolAction(InkIcon.Close, stringResource(R.string.editor_deselect)) { selectedIds = emptySet(); if (mode == CanvasMode.MoveSelection) mode = CanvasMode.Lasso; sheet = null }
                }
                "arrange" -> {
                    val target = selectedObjectId ?: selectedBlockId
                    fun move(step: PageOrder.Move) { canvas?.flushInput(); target?.let { applyPage(PageOrder.reorder(page, it, step)) } }
                    Text(stringResource(R.string.editor_arrange_help), fontSize = 12.sp, color = ChromeMuted)
                    ToolAction(InkIcon.BringFront, stringResource(R.string.editor_bring_to_front)) { move(PageOrder.Move.Front); sheet = null }
                    ToolAction(InkIcon.BringFront, stringResource(R.string.editor_bring_forward), stringResource(R.string.editor_one_step_up)) { move(PageOrder.Move.Forward) }
                    ToolAction(InkIcon.SendBack, stringResource(R.string.editor_send_backward), stringResource(R.string.editor_one_step_down)) { move(PageOrder.Move.Backward) }
                    ToolAction(InkIcon.SendBack, stringResource(R.string.editor_send_to_back)) { move(PageOrder.Move.Back); sheet = null }
                }
                "note style" -> page.blocks.firstOrNull { it.id == selectedBlockId }?.let { block -> StickyStyleControls(block) { changed -> updateBlock { changed } } }
                "table" -> page.blocks.firstOrNull { it.id == selectedBlockId }?.let { block ->
                    TableStructureControls(block, cellSelection, { changed -> updateBlock { changed } }, { cellSelection = it; if (it == null) cellAnchor = null })
                }
                "table style" -> page.blocks.firstOrNull { it.id == selectedBlockId }?.let { block -> TableStyleControls(block) { changed -> updateBlock { changed } } }
                "to text" -> {
                    val strokes = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) }
                    /** A text box over the handwriting: same left edge, first baseline near its top, size from the line height. */
                    fun textBox(text: String, below: Boolean): InkText {
                        val points = strokes.flatMap { it.points }
                        val left = points.minOf { it.x }; val right = points.maxOf { it.x }
                        val top = points.minOf { it.y }; val bottom = points.maxOf { it.y }
                        val lines = text.lines().size.coerceAtLeast(1)
                        val size = ((bottom - top) * 960f / lines * .8f).coerceIn(12f, 96f)
                        val baseline = (if (below) bottom + .01f else top) + size * .85f / 960f
                        return InkText(text = text, x = left, y = baseline, size = size, layerId = strokes.first().layerId,
                            color = strokes.first().color, boxWidth = (right - left).coerceIn(.15f, .9f))
                    }
                    ConvertToTextPanel(strokes,
                        onReplace = { text ->
                            canvas?.flushInput()
                            applyPage(page.copy(strokes = page.strokes.filterNot { stroke -> strokes.any { it.id == stroke.id } }, texts = page.texts + textBox(text, below = false)))
                            selectedIds = emptySet(); sheet = null
                        },
                        onAdd = { text -> canvas?.flushInput(); applyPage(page.copy(texts = page.texts + textBox(text, below = true))); sheet = null },
                        onCopy = { text ->
                            context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText(resources.getString(R.string.editor_clip_handwriting), text))
                            sheet = null
                        })
                }
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
                    val question = if (selection.size == 1) stringResource(
                        if (selection.single().engine == "shape-v1") { if (selection.single().points.size <= 2) R.string.editor_delete_line_question else R.string.editor_delete_shape_question } else R.string.editor_delete_trace_question
                    ) else pluralStringResource(R.plurals.editor_delete_selection_question, selection.size, selection.size)
                    Text(question, fontSize = 14.sp)
                    Text(stringResource(R.string.editor_delete_undo_hint), fontSize = 12.sp, color = ChromeMuted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { sheet = null }) { Text(stringResource(R.string.editor_no)) }
                        TextButton(onClick = { deleteSelection(); sheet = null }) { Text(stringResource(R.string.editor_yes_delete)) }
                    }
                }
                "selection style" -> {
                    val selection = page.strokes.filter { it.id in selectedIds && editableLayer(it.layerId) }
                    val fillable = selection.filter { it.points.size >= 3 && (it.filled || SolidPen.isCoded(it) ||
                        (kotlin.math.abs(it.points.first().x - it.points.last().x) < .003f && kotlin.math.abs(it.points.first().y - it.points.last().y) < .003f)) }.map { it.id }.toSet()
                    var editingFill by remember { mutableStateOf(fillable.isNotEmpty()) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        listOf(true to stringResource(R.string.editor_fill), false to stringResource(R.string.editor_border)).forEach { (fill, label) ->
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
                        applyPage(page.copy(strokes = page.strokes.map { if (it.id in fillable) it.copy(filled = false, fillColor = null,
                            color = if (it.color ushr 24 == 0) it.color or 0xFF000000.toInt() else it.color) else it }))
                    } } else if (selection.any { it.filled }) { {
                        applyPage(page.copy(strokes = page.strokes.map { if (it.id in selectedIds && it.filled && editableLayer(it.layerId))
                            it.copy(color = it.color and 0xFFFFFF, fillColor = it.fillColor ?: it.color) else it }))
                    } } else null, transparentSelected = if (editingFill) selection.filter { it.id in fillable }.all { !it.filled }
                        else selection.isNotEmpty() && selection.all { android.graphics.Color.alpha(it.color) == 0 })
                    if (!editingFill) {
                        var width by remember(first?.id, first?.width) { mutableFloatStateOf(first?.width ?: 3f) }
                        val widthMm = brushWidthMillimetres(width, drawingViewportWidthPx, panelXdpi)
                        Text(stringResource(R.string.editor_border_width_mm, widthMm), fontSize = 13.sp)
                        Slider(value = width.coerceIn(.5f, 32f), onValueChange = { width = it }, valueRange = .5f..32f,
                            onValueChangeFinished = { applyPage(page.copy(strokes = page.strokes.map {
                                if (it.id in selectedIds && editableLayer(it.layerId)) it.copy(width = width) else it
                            })) })
                        ShapeLineStyleChoices(first?.lineStyle ?: "solid") { style ->
                            applyPage(page.copy(strokes = page.strokes.map { if (it.id in selectedIds && editableLayer(it.layerId)) it.copy(lineStyle = style.name.lowercase()) else it }))
                        }
                    }
                    Text(stringResource(R.string.editor_fill_border_help), fontSize = 12.sp, color = ChromeMuted)
                }
                "selection color" -> {
                    val current = page.strokes.firstOrNull { it.id in selectedIds }?.color ?: 0xFF252525.toInt()
                    InkColorOptions(current, { color ->
                        applyPage(page.copy(strokes = page.strokes.map { if (it.id in selectedIds && editableLayer(it.layerId)) it.copy(color = (it.color and 0xFF000000.toInt()) or (color and 0xFFFFFF)) else it }))
                    }, ::pickPageColor)
                }
                "fill" -> {
                    InkColorOptions(fillColor, { fillColor = it }, ::pickPageColor)
                    Text(stringResource(R.string.editor_fill_help), fontSize = 12.sp, color = ChromeMuted)
                    Button(onClick = { mode = CanvasMode.Fill; sheet = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.editor_use_fill)) }
                }
                "text" -> {
                    ToolAction(InkIcon.Hand, stringResource(R.string.editor_arrange_objects), stringResource(R.string.editor_arrange_objects_detail)) { mode = CanvasMode.Objects; sheet = null }
                    ToolAction(InkIcon.Add, stringResource(R.string.editor_add_text_box)) { if (requireEditableLayer()) { editingTextId = null; draft = ""; sheet = "edit text" } }
                    page.texts.forEach { text ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text.text.take(60), Modifier.weight(1f).clickable { editingTextId = text.id; draft = text.text; sheet = "edit text" }, fontSize = 13.sp)
                            InkToolButton(InkIcon.Trash, stringResource(R.string.editor_delete_text_box), { if (editableLayer(text.layerId)) applyPage(page.copy(texts = page.texts.filterNot { it.id == text.id })) }, enabled = editableLayer(text.layerId), tag = english.getString(R.string.editor_delete_text_box))
                        }
                    }
                    Text(stringResource(R.string.editor_text_boxes_hint), fontSize = 11.sp, color = ChromeMuted)
                }
                "timestamp" -> {
                    val marker = page.texts.firstOrNull { it.id == editingTextId && it.kind == "timestamp" }
                    val formats = listOf(
                        "MMMM d, yyyy" to R.string.editor_date_long, "yyyy-MM-dd" to R.string.editor_date_short,
                        "EEE, MMM d, yyyy" to R.string.editor_date_day, "MMM d, yyyy · h:mm a" to R.string.editor_date_time,
                        "yyyy-MM-dd HH:mm" to R.string.editor_date_time_24, "h:mm a" to R.string.editor_time_12, "HH:mm" to R.string.editor_time_24,
                    )
                    // Read through the configuration so a locale change recomposes the formatted labels.
                    val locale = LocalConfiguration.current.locales[0]
                    var moment by remember(marker?.id) { mutableStateOf(marker?.target?.toLongOrNull() ?: System.currentTimeMillis()) }
                    var pattern by remember(marker?.id) { mutableStateOf(formats.firstOrNull { (value, _) ->
                        marker?.text == SimpleDateFormat(value, locale).format(Date(moment))
                    }?.first ?: "MMMM d, yyyy") }
                    Text(stringResource(R.string.editor_choose_date_time), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val current = Calendar.getInstance().apply { timeInMillis = moment }
                            android.app.DatePickerDialog(context, { _, year, month, day ->
                                moment = Calendar.getInstance().apply { timeInMillis = moment; set(year, month, day) }.timeInMillis
                            }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show()
                        }) { Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale).format(Date(moment))) }
                        OutlinedButton(onClick = {
                            val current = Calendar.getInstance().apply { timeInMillis = moment }
                            android.app.TimePickerDialog(context, { _, hour, minute ->
                                moment = Calendar.getInstance().apply { timeInMillis = moment; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.timeInMillis
                            }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), false).show()
                        }) { Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale).format(Date(moment))) }
                    }
                    Text(stringResource(R.string.editor_display_format), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    formats.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (value, label) -> FilterChip(selected = pattern == value, onClick = { pattern = value }, label = { Text(stringResource(label), fontSize = 11.sp) }) }
                        }
                    }
                    val preview = SimpleDateFormat(pattern, locale).format(Date(moment))
                    Text(stringResource(R.string.editor_preview, preview), fontSize = 14.sp)
                    Button(onClick = {
                        val updated = marker?.copy(text = preview, target = moment.toString())
                            ?: nextMarkerPosition().let { (x, y) -> InkText(text = preview, x = x, y = y, layerId = activeLayer, kind = "timestamp", target = moment.toString(), boxWidth = .45f) }
                        applyPage(page.copy(texts = page.texts.filterNot { it.id == updated.id } + updated))
                        sheet = null
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (marker == null) stringResource(R.string.editor_add_date_time) else stringResource(R.string.editor_update_date_time)) }
                }
                "edit text", "rename", "tags", "outline" -> {
                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.fillMaxWidth(), label = { Text(when(dialog) { "edit text" -> stringResource(R.string.editor_field_text); "rename" -> stringResource(R.string.editor_field_notebook_name); "outline" -> stringResource(R.string.editor_field_outline); else -> stringResource(R.string.editor_field_tag) }) }, singleLine = dialog != "edit text")
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
                        Text(stringResource(R.string.editor_font), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ChromeMuted)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Sans", "Serif", "Mono").forEach { family ->
                                FilterChip(selected = font == family, onClick = { font = family }, label = { Text(stringResource(when (family) { "Serif" -> R.string.editor_font_serif; "Mono" -> R.string.editor_font_mono; else -> R.string.editor_font_sans })) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text(stringResource(R.string.editor_bold), fontWeight = FontWeight.Bold) })
                            FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text(stringResource(R.string.editor_italic), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) })
                            FilterChip(selected = underline, onClick = { underline = !underline }, label = { Text(stringResource(R.string.editor_underline), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline) })
                        }
                        OutlinedButton(onClick = { showTextColors = !showTextColors }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(textColor)).border(1.dp, ChromeLine, CircleShape))
                            Spacer(Modifier.width(8.dp)); Text(if (showTextColors) stringResource(R.string.editor_hide_colors) else stringResource(R.string.editor_text_color))
                        }
                        if (showTextColors) InkColorOptions(textColor, { textColor = it }, ::pickPageColor)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.editor_alignment), fontSize = 12.sp, color = ChromeMuted)
                            listOf("Start", "Center", "End").forEach { option -> FilterChip(selected = alignment == option, onClick = { alignment = option }, label = { Text(stringResource(when (option) { "Center" -> R.string.editor_align_center; "End" -> R.string.editor_align_end; else -> R.string.editor_align_start })) }) }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.editor_line_spacing_value, lineSpacing), fontSize = 12.sp)
                            Slider(value = lineSpacing, onValueChange = { lineSpacing = it }, valueRange = .7f..2f)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.editor_text_size_value, size.toInt()), fontSize = 12.sp)
                            Slider(value = size, onValueChange = { size = it }, valueRange = 12f..60f)
                        }
                        Button(enabled = draft.isNotBlank(), onClick = {
                            if (!editableLayer(editingText?.layerId ?: activeLayer)) { message = resources.getString(R.string.editor_error_choose_layer); return@Button }
                            val text = (editingText ?: InkText(text = draft, layerId = activeLayer, y = (.15f + page.texts.size * .07f).coerceAtMost(.85f))).copy(text = draft, size = size, color = textColor, font = font, bold = bold, italic = italic, underline = underline, alignment = alignment, lineSpacing = lineSpacing)
                            applyPage(page.copy(texts = page.texts.filterNot { it.id == text.id } + text)); sheet = null
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.editor_apply_text)) }
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
                        }) { Text(stringResource(R.string.editor_save)) }
                    }
                    if (dialog == "tags") {
                        Text(if (selectedIds.isEmpty()) stringResource(R.string.editor_tags_for_notebook) else pluralStringResource(R.plurals.editor_tag_selected_strokes, selectedIds.size, selectedIds.size), fontSize = 11.sp, color = ChromeMuted)
                        notebook.tags.forEach { tag -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#$tag", Modifier.weight(1f), fontSize = 13.sp)
                            InkToolButton(InkIcon.Trash, stringResource(R.string.editor_remove_tag, tag), { onNotebookChange(notebook.copy(tags = notebook.tags - tag)) }, tag = english.getString(R.string.editor_remove_tag, tag))
                        } }
                    }
                }
                "insert" -> {
                    ToolAction(InkIcon.Table, stringResource(R.string.editor_rail_table), stringResource(R.string.editor_insert_table_detail)) { sheet = "table" }
                    ToolAction(InkIcon.Sticker, stringResource(R.string.editor_rail_stickers), stringResource(R.string.editor_stickers_detail)) { sheet = "stickers" }
                    ToolAction(InkIcon.Sticky, stringResource(R.string.editor_sticky_note), stringResource(R.string.editor_sticky_note_detail)) { addStickyNote() }
                    ToolAction(InkIcon.Paper, stringResource(R.string.editor_image), stringResource(R.string.editor_image_detail)) { if (requireEditableLayer()) { importKind = "image"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("image/*")) } }
                    ToolAction(InkIcon.Pages, stringResource(R.string.editor_link_notes), stringResource(R.string.editor_link_notes_detail)) { sheet = "link notes" }
                    ToolAction(InkIcon.Pages, stringResource(R.string.editor_link_file), stringResource(R.string.editor_link_file_detail)) { importKind = "link-file"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("*/*")) }
                    ToolAction(InkIcon.Search, stringResource(R.string.editor_link_website), stringResource(R.string.editor_link_website_detail)) { draft = "https://"; sheet = "website link" }
                    ToolAction(InkIcon.Insert, stringResource(R.string.editor_attachment), stringResource(R.string.editor_attachment_detail)) { importKind = "attachment"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("*/*")) }
                    ToolAction(InkIcon.Clock, stringResource(R.string.editor_date_time)) { if (requireEditableLayer()) { editingTextId = null; sheet = "timestamp" } }
                    ToolAction(InkIcon.Microphone, if (recording) stringResource(R.string.editor_stop_recording) else stringResource(R.string.editor_record_audio)) {
                        if (recording) stopRecording()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                        else microphone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                "attachments" -> {
                    if (notebook.attachments.isEmpty()) Text(stringResource(R.string.editor_no_attachments), fontSize = 13.sp, color = ChromeMuted)
                    notebook.attachments.forEach { attachment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(attachment.name, Modifier.weight(1f).clickable { runCatching { files.openAttachment(attachment) }.onFailure { message = resources.getString(R.string.editor_error_no_app) } }, fontSize = 13.sp)
                            InkToolButton(InkIcon.Trash, stringResource(R.string.editor_remove_attachment), { onNotebookChange(notebook.copy(attachments = notebook.attachments.filterNot { it.id == attachment.id })) }, tag = english.getString(R.string.editor_remove_attachment))
                        }
                    }
                }
                "recordings" -> {
                    val recordings = notebook.attachments.filter { it.mimeType.startsWith("audio/") }
                    if (recordings.isEmpty()) Text(stringResource(R.string.editor_no_recordings), fontSize = 13.sp, color = ChromeMuted)
                    recordings.forEach { attachment -> ToolAction(InkIcon.Microphone, attachment.name.removeSuffix(".m4a"), stringResource(R.string.editor_play_recording)) {
                        activeAudio = attachment; sheet = null
                    } }
                }
                "page+" -> {
                    Text(stringResource(R.string.editor_add_a_page), fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Before", "After", "Last Page").forEach { position -> FilterChip(selected = pagePlacement == position, onClick = { pagePlacement = position }, label = { Text(stringResource(when (position) { "Before" -> R.string.editor_place_before; "Last Page" -> R.string.editor_place_last; else -> R.string.editor_place_after })) }) }
                    }
                    Text(stringResource(R.string.editor_tap_paper_hint), fontSize = 11.sp, color = ChromeMuted)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(R.string.editor_paper_blank to "blank", R.string.editor_paper_ruled to "lined", R.string.editor_paper_grid to "grid", R.string.editor_paper_daily to "daily").forEach { (nameRes, id) ->
                            val name = stringResource(nameRes)
                            val addDescription = stringResource(R.string.editor_add_template_page, name)
                            Column(Modifier.width(100.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0xFFD5DAD8), RoundedCornerShape(12.dp))
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { insertTemplatePage(id, pagePlacement); sheet = null }
                                .testTag("page-plus-$id").semantics { contentDescription = addDescription }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(6.dp)).border(1.dp, Color(0xFFE6E9E8), RoundedCornerShape(6.dp))) {
                                    NotebookPageThumbnail(NotebookPage(template = id), Modifier.fillMaxSize().background(Color.White))
                                    Box(Modifier.align(Alignment.Center).size(28.dp).clip(CircleShape).background(Color(0xFF2F7A68)), contentAlignment = Alignment.Center) {
                                        Text("+", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Text(name, Modifier.padding(top = 6.dp), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    ToolAction(InkIcon.Template, stringResource(R.string.editor_more_templates)) { sheet = "templates" }
                    ToolAction(InkIcon.Pages, stringResource(R.string.editor_from_notebook)) { sheet = "page from notebook" }
                    ToolAction(InkIcon.Paper, stringResource(R.string.editor_from_images)) { if (requireEditableLayer()) { importKind = "page-image"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("image/*")) } }
                    ToolAction(InkIcon.Insert, stringResource(R.string.editor_from_pdf)) { importKind = "pdf"; importPageId = page.id; importLayerId = activeLayer; sheet = null; insertFile.launch(arrayOf("application/pdf")) }
                }
                "page from notebook" -> {
                    repository.notebooks.filter { it.id != notebook.id && it.deletedAt == 0L }.forEach { source ->
                        ToolAction(InkIcon.Pages, source.title, stringResource(R.string.editor_add_first_page_here)) {
                            val original = source.pages.firstOrNull()
                            if (original != null) {
                                val clone = original.copy(id = java.util.UUID.randomUUID().toString())
                                val pages = notebook.pages.toMutableList(); val at = when(pagePlacement) { "Before" -> safeIndex; "Last Page" -> pages.size; else -> safeIndex + 1 }
                                pages.add(at.coerceIn(0, pages.size), clone); onNotebookChange(notebook.copy(pages = pages, updatedAt = System.currentTimeMillis())); pageIndex = at
                            }
                            sheet = null
                        }
                    }
                    if (repository.notebooks.none { it.id != notebook.id && it.deletedAt == 0L }) Text(stringResource(R.string.editor_no_other_notebooks), fontSize = 12.sp, color = ChromeMuted)
                }
                "link notes" -> {
                    repository.notebooks.filter { it.id != notebook.id && it.deletedAt == 0L }.forEach { target -> ToolAction(InkIcon.Pages, target.title, stringResource(R.string.editor_link_to_page_one)) {
                        val (x, y) = nextMarkerPosition()
                        val linkText = InkText(text = target.title, x = x, y = y, layerId = activeLayer, kind = "link-note", target = target.id)
                        if (requireEditableLayer()) applyPage(page.copy(texts = page.texts + linkText)); sheet = null
                    } }
                    if (repository.notebooks.none { it.id != notebook.id && it.deletedAt == 0L }) Text(stringResource(R.string.editor_create_notebook_to_link), fontSize = 12.sp, color = ChromeMuted)
                }
                "website link" -> {
                    OutlinedTextField(draft, { draft = it }, label = { Text(stringResource(R.string.editor_website_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = draft.startsWith("http"), onClick = {
                        if (requireEditableLayer()) {
                            val (x, y) = nextMarkerPosition()
                            applyPage(page.copy(texts = page.texts + InkText(text = Uri.parse(draft).host ?: draft, x = x, y = y, layerId = activeLayer, kind = "link-web", target = draft)))
                        }
                        sheet = null
                    }) { Text(stringResource(R.string.editor_add_link)) }
                }
                "share & export" -> {
                    ToolAction(InkIcon.Pages, stringResource(R.string.editor_save_pdf), pluralStringResource(R.plurals.editor_all_pages_count, notebook.pages.size, notebook.pages.size)) { export("pdf") }
                    ToolAction(InkIcon.Paper, stringResource(R.string.editor_save_png), stringResource(R.string.editor_current_page)) { export("png") }
                    ToolAction(InkIcon.Save, stringResource(R.string.editor_save_backup), stringResource(R.string.editor_save_backup_detail)) { export("folio") }
                    ToolAction(InkIcon.Share, stringResource(R.string.editor_share_pdf)) { export("pdf", true) }
                    ToolAction(InkIcon.Share, stringResource(R.string.editor_share_current_page)) { export("png", true) }
                }
                "zoom" -> {
                    Text(stringResource(R.string.editor_zoom_percent, (zoom * 100).toInt()), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { canvas?.setZoom(zoom - .25f) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.editor_zoom_out)) }
                        OutlinedButton(onClick = { canvas?.setZoom(zoom + .25f) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.editor_zoom_in)) }
                    }
                    val canvasZoomLabel = stringResource(R.string.editor_canvas_zoom)
                    Slider(zoom, onValueChange = { canvas?.setZoom(it) }, valueRange = .25f..5f,
                        modifier = Modifier.semantics { contentDescription = canvasZoomLabel })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(.5f, .75f, 1f, 1.5f, 2f, 3f).forEach { level ->
                            TextButton(onClick = { canvas?.setZoom(level) }, modifier = Modifier.weight(1f)) {
                                Text("${(level * 100).toInt()}%", fontSize = 10.sp)
                            }
                        }
                    }
                    ToolAction(InkIcon.Zoom, stringResource(R.string.editor_zoom_window), stringResource(R.string.editor_zoom_window_detail)) {
                        canvas?.flushInput(); mode = CanvasMode.ZoomWindow; sheet = null
                        if (zoomBox == null) zoomBox = android.graphics.RectF(.1f, .08f, .6f, .08f + .5f * zoomRatio)
                    }
                    ToolAction(InkIcon.RectangleSelect, stringResource(R.string.editor_free_zoom), stringResource(R.string.editor_free_zoom_detail)) {
                        canvas?.flushInput(); mode = CanvasMode.FreeZoom; sheet = null
                    }
                    Text(stringResource(R.string.editor_fit), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ChromeMuted)
                    listOf(R.string.editor_fit_screen, R.string.editor_fit_width, R.string.editor_fit_height, R.string.editor_fit_canvas).forEach { action ->
                        ToolAction(InkIcon.Expand, stringResource(action)) {
                            when (action) {
                                R.string.editor_fit_screen -> canvas?.fitToScreen()
                                R.string.editor_fit_width -> canvas?.fitToWidth()
                                R.string.editor_fit_height -> canvas?.fitToHeight()
                                else -> canvas?.fitToCanvas()
                            }
                        }
                    }
                    ToolAction(InkIcon.Hand, stringResource(R.string.editor_pan_tool), stringResource(R.string.editor_pan_tool_detail)) { mode = CanvasMode.Pan; sheet = null }
                }
                "gestures" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(stringResource(R.string.editor_write_with_finger), fontSize = 14.sp); Text(stringResource(R.string.editor_write_with_finger_detail), fontSize = 11.sp, color = ChromeMuted) }
                        Switch(fingerDrawing, onCheckedChange = { fingerDrawing = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(stringResource(R.string.editor_touch_zoom), fontSize = 14.sp); Text(stringResource(R.string.editor_touch_zoom_detail), fontSize = 11.sp, color = ChromeMuted) }
                        Switch(pinchToZoom, onCheckedChange = { pinchToZoom = it })
                    }
                    ToolToggle(stringResource(R.string.editor_swipe_pages), stringResource(R.string.editor_swipe_pages_detail), horizontalPageSwipe, { horizontalPageSwipe = it })
                    ToolToggle(stringResource(R.string.editor_long_press_select), stringResource(R.string.editor_long_press_select_detail), longPressSelectEnabled, { longPressSelectEnabled = it })
                    Text(stringResource(R.string.editor_gestures_help), fontSize = 12.sp, color = ChromeMuted)
                }
                "customize toolbar" -> {
                    Text(stringResource(R.string.editor_toolbar_show_tools), fontSize = 13.sp, color = ChromeMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.editor_toolbar_on_right), Modifier.weight(1f), fontSize = 14.sp)
                        Switch(railOnRight, onCheckedChange = { railOnRight = it })
                    }
                    listOf("Template", "Layers", "Shapes", "Lasso", "Fill", "Pressure", "Gestures", "Table", "Stickers", "Sticky", "Ruler", "Zoom", "Page layout", "Text", "Insert", "Page +", "Share").forEach { tool ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(toolbarToolLabel(tool), Modifier.weight(1f), fontSize = 14.sp)
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
                                Text(stringResource(if (layout == "Scroll") R.string.editor_layout_scroll else R.string.editor_layout_single_page), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(if (layout == "Scroll") stringResource(R.string.editor_layout_scroll_detail) else stringResource(R.string.editor_layout_single_detail), fontSize = 11.sp, color = ChromeMuted)
                            }
                                androidx.compose.material3.RadioButton(selected = effectivePageLayout == layout, onClick = {
                                    if (!notebook.infiniteCanvas) { canvas?.flushInput(); pageLayout = layout }
                            })
                        }
                    }
                    HorizontalDivider(color = ChromeLine)
                    if (effectivePageLayout == "Scroll") {
                        ToolToggle(stringResource(R.string.editor_page_boundary), stringResource(R.string.editor_page_boundary_detail), showPageBoundary, { showPageBoundary = it })
                        ToolToggle(stringResource(R.string.editor_single_finger_scroll), stringResource(R.string.editor_single_finger_scroll_detail), singleFingerSwipe, { singleFingerSwipe = it })
                    }
                }
                "search" -> {
                    OutlinedTextField(draft, { draft = it }, label = { Text(stringResource(R.string.editor_search_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val results = notebook.pages.filter { item -> draft.isNotBlank() && (item.outline + " " + item.texts.joinToString(" ") { it.text } + " " + item.strokes.flatMap { it.tags }.joinToString(" ")).contains(draft, ignoreCase = true) }
                    results.forEach { item -> ToolAction(InkIcon.Pages, stringResource(R.string.editor_page_number, notebook.pages.indexOf(item) + 1), item.outline.ifBlank { null }) { pageIndex = notebook.pages.indexOf(item); sheet = null } }
                    Text(if (draft.isNotBlank() && results.isEmpty()) stringResource(R.string.editor_search_no_matches) else stringResource(R.string.editor_search_help), fontSize = 11.sp, color = ChromeMuted)
                }
                "more" -> {
                    ToolToggle(stringResource(R.string.editor_show_note_tabs), stringResource(R.string.editor_show_note_tabs_detail), showNoteTabs, {
                        showNoteTabs = it; preferences.edit().putBoolean("showNoteTabs", it).apply()
                    })
                    ToolAction(InkIcon.Rename, stringResource(R.string.editor_rename_notebook)) { draft = notebook.title; sheet = "rename" }
                    ToolAction(InkIcon.Search, stringResource(R.string.editor_search)) { draft = ""; sheet = "search" }
                    // Only offer tools here that aren't already on the toolbar.
                    if ("Stickers" !in toolbarTools) ToolAction(InkIcon.Sticker, stringResource(R.string.editor_rail_stickers), stringResource(R.string.editor_stickers_detail)) { sheet = "stickers" }
                    if ("Sticky" !in toolbarTools) ToolAction(InkIcon.Sticky, stringResource(R.string.editor_sticky_note), stringResource(R.string.editor_sticky_note_detail)) { addStickyNote() }
                    ToolAction(InkIcon.Tag, stringResource(R.string.editor_notebook_tags)) { selectedIds = emptySet(); draft = ""; sheet = "tags" }
                    ToolAction(InkIcon.Tools, stringResource(R.string.editor_customize_toolbar)) { sheet = "customize toolbar" }
                    if (clipboard.isNotEmpty()) ToolAction(InkIcon.Copy, stringResource(R.string.editor_paste_strokes)) { paste(); sheet = null }
                    ToolAction(InkIcon.Save, stringResource(R.string.editor_save)) { save(); message = resources.getString(R.string.editor_notebook_saved) }
                    ToolAction(InkIcon.Microphone, stringResource(R.string.editor_recordings)) { sheet = "recordings" }
                    if (!notebook.infiniteCanvas) ToolAction(InkIcon.Trash, stringResource(R.string.editor_delete_this_page)) { actionPageId = page.id; sheet = "delete page" }
                    ToolAction(InkIcon.Eraser, if (notebook.infiniteCanvas) stringResource(R.string.editor_clear_canvas) else stringResource(R.string.editor_clear_page)) { sheet = "clear page" }
                }
                "clear page", "delete page" -> {
                    Text(if (dialog == "clear page") stringResource(R.string.editor_clear_page_question) else stringResource(R.string.editor_delete_page_question), fontSize = 14.sp)
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
                    }) { Text(if (dialog == "clear page") stringResource(R.string.editor_clear_page) else stringResource(R.string.editor_delete_page)) }
                }
            }
        }
    }
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Folio") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.editor_ok)) } }) }
    pendingPin?.let { location -> AlertDialog(onDismissRequest = { pendingPin = null }, title = { Text(if (editingPin == null) stringResource(R.string.editor_add_pin) else stringResource(R.string.editor_rename_pin)) },
        text = { OutlinedTextField(pinName, { pinName = it }, label = { Text(stringResource(R.string.editor_pin_name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = pinName.isNotBlank(), onClick = {
            val current = latestNotebook
            val pin = editingPin?.copy(name = pinName.trim()) ?: CanvasPin(name = pinName.trim(), x = location.x, y = location.y, zoom = zoom)
            latestOnChange(current.copy(pins = if (editingPin == null) current.pins + pin else current.pins.map { if (it.id == pin.id) pin else it }, updatedAt = System.currentTimeMillis()))
            pendingPin = null; editingPin = null
        }) { Text(stringResource(R.string.editor_save_pin)) } }, dismissButton = { TextButton(onClick = { pendingPin = null }) { Text(stringResource(R.string.editor_cancel)) } }) }
    markerToDelete?.let { id -> AlertDialog(
        onDismissRequest = { markerToDelete = null },
        title = { Text(stringResource(R.string.editor_remove_marker_question)) },
        text = { Text(stringResource(R.string.editor_remove_marker_detail)) },
        confirmButton = { TextButton(onClick = {
            canvas?.flushInput()
            applyPage(page.copy(texts = page.texts.filterNot { it.id == id && editableLayer(it.layerId) }))
            canvas?.deselectObject(); markerToDelete = null
        }) { Text(stringResource(R.string.editor_remove)) } },
        dismissButton = { TextButton(onClick = { markerToDelete = null }) { Text(stringResource(R.string.editor_cancel)) } },
    ) }

    @Composable fun Rail() {
        Column(Modifier.width(49.dp).fillMaxHeight().background(Color(0xFFFAFAFA))) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if ("Template" in toolbarTools && !notebook.infiniteCanvas) InkRailButton(InkIcon.Template, stringResource(R.string.editor_rail_template), { openToolPanel("templates") })
                if (notebook.infiniteCanvas) InkRailButton(InkIcon.Template, stringResource(R.string.editor_rail_paper), { sheet = "paper" })
                if (notebook.infiniteCanvas) InkRailButton(InkIcon.Tag, stringResource(R.string.editor_rail_pins), { sheet = "pins" })
                if (notebook.infiniteCanvas) InkRailButton(InkIcon.Hand, stringResource(R.string.editor_rail_pan), { canvas?.flushInput(); mode = if (mode == CanvasMode.Pan) CanvasMode.Write else CanvasMode.Pan }, mode == CanvasMode.Pan)
                if ("Layers" in toolbarTools) InkRailButton(InkIcon.Layers, stringResource(R.string.editor_rail_layers), { openToolPanel("layers") })
                if ("Shapes" in toolbarTools) InkRailButton(InkIcon.Shapes, stringResource(R.string.editor_rail_shapes), { canvas?.flushInput(); mode = CanvasMode.Shape; if (selectedBrush.eraser) selectedBrush = previousPen; selectedIds = emptySet(); sheet = "shapes" }, mode == CanvasMode.Shape)
                if ("Lasso" in toolbarTools) InkRailButton(InkIcon.Lasso, stringResource(R.string.editor_rail_lasso), {
                    canvas?.flushInput()
                    if (mode == CanvasMode.Lasso) sheet = "lasso" else { mode = CanvasMode.Lasso; selectedIds = emptySet() }
                }, mode == CanvasMode.Lasso)
                if ("Fill" in toolbarTools) InkRailButton(InkIcon.Fill, stringResource(R.string.editor_rail_fill), { canvas?.flushInput(); mode = CanvasMode.Fill; if (selectedIds.isNotEmpty()) sheet = "selection style" else sheet = "fill" }, mode == CanvasMode.Fill)
                if ("Pressure" in toolbarTools) InkRailButton(InkIcon.Pressure, stringResource(R.string.editor_rail_pressure), { openToolPanel("pressure") })
                if ("Gestures" in toolbarTools) InkRailButton(InkIcon.Hand, stringResource(R.string.editor_rail_gestures), { openToolPanel("gestures") })
                if ("Table" in toolbarTools) InkRailButton(InkIcon.Table, stringResource(R.string.editor_rail_table), { openToolPanel("table") })
                if ("Stickers" in toolbarTools) InkRailButton(InkIcon.Sticker, stringResource(R.string.editor_rail_stickers), { openToolPanel("stickers") })
                if ("Sticky" in toolbarTools) InkRailButton(InkIcon.Sticky, stringResource(R.string.editor_rail_sticky), { addStickyNote() })
                if ("Ruler" in toolbarTools) InkRailButton(InkIcon.Ruler, stringResource(R.string.editor_rail_ruler), { canvas?.flushInput(); rulerOn = !rulerOn }, rulerOn)
                if ("Zoom" in toolbarTools) InkRailButton(InkIcon.Zoom, "${(zoom * 100).toInt()}%", { openToolPanel("zoom") }, mode == CanvasMode.Pan)
                if ("Page layout" in toolbarTools && !notebook.infiniteCanvas) InkRailButton(InkIcon.Pages, stringResource(R.string.editor_rail_page), { openToolPanel("page layout") })
                if ("Text" in toolbarTools) InkRailButton(InkIcon.Text, stringResource(R.string.editor_rail_text), { openToolPanel("text") })
                if ("Insert" in toolbarTools) InkRailButton(InkIcon.Insert, stringResource(R.string.editor_rail_insert), { openToolPanel("insert") })
                if ("Page +" in toolbarTools && !notebook.infiniteCanvas) InkRailButton(InkIcon.AddPage, stringResource(R.string.editor_rail_page_add), { openToolPanel("page+") })
                if ("Share" in toolbarTools) InkRailButton(InkIcon.Share, stringResource(R.string.editor_rail_share), { openToolPanel("share & export") })
            }
            InkRailButton(InkIcon.More, stringResource(R.string.editor_more), { openToolPanel("more") })
        }
    }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            if (!focusMode) {
                if (showNoteTabs) androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(
                    primary = ChromeInk, onSurface = ChromeInk, surface = Color(0xFFFAFAFA), secondaryContainer = com.allient.folio.FolioBrand.SageSoft,
                    surfaceVariant = Color(0xFFE2E6E4), surfaceContainerHigh = Color(0xFFEEF1EF), onSurfaceVariant = ChromeMuted,
                )) {
                    DocumentTabs(recentNotes, notebook.id,
                        listTitle = stringResource(R.string.editor_recent_notes), tagPrefix = "notebook-note",
                        onHideTabs = { showNoteTabs = false; preferences.edit().putBoolean("showNoteTabs", false).apply() },
                        onCloseOthers = {
                            closedNoteTabs = (closedNoteTabs + recentNotes.map { it.id }.filter { it != notebook.id }).toList().takeLast(128).toSet()
                            preferences.edit().putStringSet("closedNoteTabs", closedNoteTabs).apply()
                        },
                        onSelect = { id -> if (id != notebook.id) { save(); onNavigateNotebook(id, null) } },
                        onClose = { id ->
                            closedNoteTabs = (closedNoteTabs + id).toList().takeLast(128).toSet()
                            preferences.edit().putStringSet("closedNoteTabs", closedNoteTabs).apply()
                            if (id == notebook.id) {
                                save()
                                val next = recentNotes.firstOrNull { it.id != id }
                                if (next == null) onBack() else onNavigateNotebook(next.id, null)
                            }
                        },
                    )
                }
                // Phones and narrow windows: the title gives way and fewer brushes show, so page controls always fit.
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val compactBar = screenWidthDp < 600
                // The other controls take ~312dp (focus, back, eraser, undo, redo, page controls); each brush
                // takes 36dp minus the last 4dp gap. More brushes overflow the row into the back button.
                val maxBrushes = if (compactBar) ((screenWidthDp - 308) / 36).coerceIn(1, 5) else 5
                val renameNotebookLabel = stringResource(R.string.editor_rename_notebook)
                Row(Modifier.fillMaxWidth().height(54.dp).background(Color(0xFFFAFAFA)), verticalAlignment = Alignment.CenterVertically) {
                    InkToolButton(InkIcon.Expand, stringResource(R.string.editor_focus_mode), { focusMode = true }, Modifier.width(48.dp), buttonSize = 44.dp, tag = english.getString(R.string.editor_focus_mode))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(ChromeLine))
                    InkToolButton(InkIcon.Back, backLabel, { save(); onBack() }, buttonSize = 32.dp)
                    if (compactBar) Spacer(Modifier.weight(1f)) else Box(Modifier.weight(1f).padding(horizontal = 5.dp).clickable { draft = notebook.title; sheet = "rename" }.semantics { contentDescription = renameNotebookLabel }) {
                        Text(notebook.title, fontSize = 13.sp, color = ChromeInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 5.dp))
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color(0xFFB8B8B8)))
                    }
                    // At most five brushes are visible; extra presets scroll inside this strip only.
                    BrushPresetBar(brushState.presets, selectedBrush.presetId, highlighted = !selectedBrush.eraser && mode == CanvasMode.Write && sheet == null,
                        reselectOpensSettings = mode == CanvasMode.Write, maxVisible = maxBrushes, settingsOpen = showBrush, onSettingsOpenChange = { showBrush = it },
                        onSelect = { brush -> selectedBrush = brush; mode = CanvasMode.Write; selectedIds = emptySet() }, onMove = brushState::move) {
                        BrushSettings(selectedBrush, brushState.options, ::updateBrush, ::addBrush, ::deleteBrush, ::pickPageColor)
                    }
                    ChromeDivider()
                    InkToolButton(InkIcon.Eraser, stringResource(R.string.editor_eraser), {
                        if (selectedBrush.eraser && mode == CanvasMode.Write) sheet = "eraser" else selectEraser()
                    }, selected = selectedBrush.eraser && mode == CanvasMode.Write && sheet == null, tag = english.getString(R.string.editor_eraser))
                    InkToolButton(InkIcon.Undo, stringResource(R.string.editor_undo), ::undo, enabled = undoStack.isNotEmpty(), tag = english.getString(R.string.editor_undo))
                    InkToolButton(InkIcon.Redo, stringResource(R.string.editor_redo), ::redo, enabled = redoStack.isNotEmpty(), tag = english.getString(R.string.editor_redo))
                    ChromeDivider()
                    if (!notebook.infiniteCanvas) {
                    val pageManagerLabel = stringResource(R.string.editor_page_manager_position, safeIndex + 1, notebook.pages.size)
                    InkToolButton(InkIcon.Previous, stringResource(R.string.editor_previous_page), { canvas?.flushInput(); pageIndex = safeIndex - 1 }, enabled = safeIndex > 0, buttonSize = 28.dp, tag = english.getString(R.string.editor_previous_page))
                    Box(Modifier.width(42.dp).height(44.dp).clickable { canvas?.flushInput(); showPages = true }.semantics { contentDescription = pageManagerLabel }, contentAlignment = Alignment.Center) { Text("${safeIndex + 1}/${notebook.pages.size}", fontSize = 11.sp) }
                    if (safeIndex < notebook.pages.lastIndex) InkToolButton(InkIcon.Next, stringResource(R.string.editor_next_page), { canvas?.flushInput(); pageIndex = safeIndex + 1 }, buttonSize = 28.dp, tag = english.getString(R.string.editor_next_page))
                    else InkToolButton(InkIcon.PlusSmall, stringResource(R.string.editor_add_page), { addPage("Last Page") }, buttonSize = 28.dp, tag = english.getString(R.string.editor_add_page))
                    } else InkToolButton(InkIcon.Tag, stringResource(R.string.editor_canvas_pins), { sheet = "pins" }, tag = english.getString(R.string.editor_canvas_pins))
                    if (saveError != null) InkToolButton(InkIcon.More, stringResource(R.string.editor_save_failed_retry), { message = saveError; save() }, tag = english.getString(R.string.editor_save_failed_retry))
                    Spacer(Modifier.width(3.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ChromeLine))
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!focusMode && !railOnRight) { Rail(); Box(Modifier.width(1.dp).fillMaxHeight().background(ChromeLine)) }
                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds().onSizeChanged { drawingViewportWidthPx = it.width; drawingViewportHeightPx = it.height }) {
                    if (effectivePageLayout == "Scroll") {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val pageHeight = if (pageAspect > 0f) maxWidth * pageAspect else maxHeight
                            LazyColumn(state = pageListState, modifier = Modifier.fillMaxSize(), userScrollEnabled = !fingerDrawing && (notebook.infiniteCanvas || singleFingerSwipe)) {
                                items(notebook.pages, key = { it.id }) { model ->
                                    val index = notebook.pages.indexOfFirst { it.id == model.id }
                                    Column(Modifier.fillMaxWidth().animateItem()) {
                                    NotebookPageCanvas(
                                        model = model, isActive = model.id == page.id, pageHeight = pageHeight,
                                        swipeEnabled = if (notebook.infiniteCanvas) !fingerDrawing else singleFingerSwipe && !fingerDrawing,
                                        stackedCanvas = true, brush = selectedBrush, threshold = threshold,
                                        pressureCurve = pressureCurve, pressureCurveEnabled = pressureCurveEnabled,
                                        velocityCurveEnabled = velocityCurveEnabled, velocityCurve = velocityCurve,
                                        tiltCurveEnabled = tiltCurveEnabled, tiltCurve = tiltCurve,
                                        mode = mode, shape = shape, shapeFilled = shapeFilled, shapeFillColor = shapeFillColor, shapeBorderNone = shapeBorderNone, shapeColor = shapeColor, shapeWidth = shapeWidth,
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
                                        onCanvas = { view -> view.onImageDrop = { uri, point, aspect -> dropImage(uri, model.id, point, aspect) }; if (model.id == page.id) canvas = view },
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
                        val pageSlide = remember { Animatable(0f) }
                        LaunchedEffect(page.id) {
                            if (slideInPageId == page.id) { pageSlide.snapTo(1f); pageSlide.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
                            slideInPageId = null
                        }
                        // Read the drag offset only in draw-phase lambdas so a drag never recomposes the editor.
                        val shiftSign by remember { derivedStateOf { kotlin.math.sign(pageShift.value) } }
                        if (shiftSign != 0f) Box(Modifier.fillMaxSize().background(Color(0xFFE4E7E4)))
                        landingSnapshot?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds) }
                        val onLastPage = safeIndex >= notebook.pages.lastIndex
                        // On the last page the incoming neighbour is the blank page a release would add, so releasing just
                        // finishes the same slide instead of swapping a placeholder for the page.
                        val neighbourSnapshot = when {
                            shiftSign < 0f -> nextSnapshot
                            shiftSign > 0f -> previousSnapshot
                            else -> null
                        }
                        if (neighbourSnapshot != null) {
                            androidx.compose.foundation.Image(neighbourSnapshot, null, Modifier.fillMaxSize().graphicsLayer {
                                val shift = pageShift.value
                                translationX = shift + if (shift < 0f) size.width + pageGapPx else -size.width - pageGapPx
                            }.pageEdges { true }.testTag("page-drag-neighbour"), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                        }
                        androidx.compose.animation.AnimatedVisibility(shiftSign < 0f && onLastPage && !addingPage, Modifier.align(Alignment.CenterEnd),
                            enter = androidx.compose.animation.fadeIn(tween(90)), exit = androidx.compose.animation.fadeOut(tween(180))) {
                            AddPageReveal({ -pageShift.value - pageGapPx }, pageTurnThresholdPx - pageGapPx, overPage = neighbourSnapshot != null)
                        }
                        key(page.id) {
                            AndroidView(factory = { ctx -> InkCanvasView(ctx) { updated -> applyPage(page.copy(strokes = updated)) }.also { canvas = it } },
                                update = { view ->
                                    view.onStrokesChanged = { updated -> applyPage(page.copy(strokes = updated.filter { stroke -> page.layers.any { it.id == stroke.layerId } })) }
                                    view.onObjectsChanged = { texts, images -> applyPage(page.copy(texts = texts, images = images)) }
                                    view.onObjectSelectionChanged = { id, text -> selectedObjectId = id; selectedObjectIsText = text }
                                    view.onCanvasAction = { handleCanvasAction(it) }; view.canPaste = clipboard.isNotEmpty()
                                    view.onImageDrop = { uri, point, aspect -> dropImage(uri, page.id, point, aspect) }
                                    view.brush = selectedBrush; view.threshold = threshold
                                    view.infiniteCanvas = notebook.infiniteCanvas
                                    view.documentAspect = pageAspect
                                    view.onAddPin = { location -> editingPin = null; pendingPin = location; pinName = resources.getString(R.string.editor_pin_default_name, latestNotebook.pins.size + 1) }
                                    view.pressureCurve = pressureCurve; view.pressureCurveEnabled = pressureCurveEnabled
                                    view.velocityCurveEnabled = velocityCurveEnabled; view.velocityCurve = velocityCurve
                                    view.tiltCurveEnabled = tiltCurveEnabled; view.tiltCurve = tiltCurve
                                    view.mode = mode; view.shapeKind = shape; view.shapeFilled = shapeFilled; view.shapeFillColor = shapeFillColor; view.shapeBorderNone = shapeBorderNone; view.shapeColor = shapeColor; view.shapeWidth = shapeWidth; view.shapeLineStyle = shapeLineStyle; view.fillColor = fillColor; view.onColorPicked = { picked -> val receiver = pageColorReceiver; pageColorReceiver = null; mode = colorReturnMode; sheet = colorReturnSheet; receiver?.invoke(picked) }; view.eraserMode = eraserMode
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
                                    view.onPageDrag = if (!notebook.infiniteCanvas) ::onPageDrag else null
                                    // Live erasing and drags are published once at gesture completion.
                                    if (!view.hasPendingEdits) { view.texts = page.texts; view.images = page.images; view.blocks = page.blocks; view.strokes = page.strokes }
                                    view.selectedStrokeIds = selectedIds; view.onSelectionChanged = { ids ->
                                        selectedIds = ids
                                        if (ids.isEmpty() && mode == CanvasMode.MoveSelection) mode = CanvasMode.Lasso
                                        if (ids.isNotEmpty() && lassoAutoSwitch && mode == CanvasMode.Lasso) { selectedBrush = previousPen; mode = CanvasMode.Write }
                                    }
                                    view.onZoomChanged = { zoom = it; viewportCenterX = view.viewportCenterX; viewportCenterY = view.viewportCenterY }
                            }, modifier = Modifier.fillMaxSize().graphicsLayer { translationY = pageSlide.value * size.height; translationX = pageShift.value }.clipToBounds().background(Color.White)
                                .pageEdges { pageShift.value != 0f })
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
                                InkToolButton(InkIcon.Locate, stringResource(R.string.editor_zoom_box_to_view), {
                                    val w = box.width(); val h = box.height()
                                    moveBox(android.graphics.RectF(viewportCenterX - w / 2f, viewportCenterY - h / 2f, viewportCenterX + w / 2f, viewportCenterY + h / 2f).let { if (notebook.infiniteCanvas) it else android.graphics.RectF((it.left).coerceIn(0f, 1f - w), it.top.coerceIn(0f, (1f - h).coerceAtLeast(0f)), it.left.coerceIn(0f, 1f - w) + w, it.top.coerceIn(0f, (1f - h).coerceAtLeast(0f)) + h) })
                                }, tag = english.getString(R.string.editor_zoom_box_to_view))
                                Spacer(Modifier.weight(1f))
                                InkToolButton(InkIcon.Previous, stringResource(R.string.editor_zoom_box_left), { step(-1) }, tag = english.getString(R.string.editor_zoom_box_left))
                                InkToolButton(InkIcon.Next, stringResource(R.string.editor_zoom_box_right), { step(1) }, tag = english.getString(R.string.editor_zoom_box_right))
                                InkToolButton(InkIcon.Undo, stringResource(R.string.editor_undo), { undo() }, tag = english.getString(R.string.editor_undo))
                                InkToolButton(InkIcon.Redo, stringResource(R.string.editor_redo), { redo() }, tag = english.getString(R.string.editor_redo))
                                InkToolButton(InkIcon.Close, stringResource(R.string.editor_close_zoom_window), { zoomBox = null; if (mode == CanvasMode.ZoomWindow) mode = CanvasMode.Write }, tag = english.getString(R.string.editor_close_zoom_window))
                            }
                            HorizontalDivider(color = ChromeLine)
                            AndroidView(factory = { ctx -> InkCanvasView(ctx) { updated -> applyPage(page.copy(strokes = updated)) } },
                                modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged { zoomPanelSize = it }, update = { view ->
                                    view.onStrokesChanged = { updated -> applyPage(page.copy(strokes = updated.filter { stroke -> page.layers.any { it.id == stroke.layerId } })) }
                                    view.infiniteCanvas = notebook.infiniteCanvas
                                    view.documentAspect = canvas?.pageAspect ?: 0f
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
                    page.blocks.firstOrNull { it.id == editingStickyId }?.let { note ->
                        StickyTextDialog(note.text, { text -> applyPage(page.copy(blocks = page.blocks.map { if (it.id == note.id) it.copy(text = text) else it })); editingStickyId = null }, { editingStickyId = null })
                    }
                    if (mode == CanvasMode.ColorPicker) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = RoundedCornerShape(24.dp), shadowElevation = 3.dp) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            InkSymbol(InkIcon.Eyedropper, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.editor_tap_color), fontSize = 12.sp)
                            TextButton(onClick = { pageColorReceiver = null; mode = colorReturnMode; sheet = colorReturnSheet }) { Text(stringResource(R.string.editor_cancel)) }
                        }
                    }
                    if (notebook.infiniteCanvas) InfiniteCanvasNavigator(page, notebook.pins, viewportCenterX, viewportCenterY, zoom,
                        spanY = canvas?.viewportSpanY ?: 1f,
                        onJump = { x, y -> canvas?.jumpTo(x, y) }, onLocate = { canvas?.locateContent() }, onPins = { sheet = "pins" },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
                    if (zoom > 1.01f && !notebook.infiniteCanvas) {
                        val zoomResetLabel = stringResource(R.string.editor_zoom_reset_description, (zoom * 100).toInt())
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                .clickable { canvas?.resetViewport() }
                                .semantics { contentDescription = zoomResetLabel },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF7F7F7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine),
                            shadowElevation = 2.dp,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${(zoom * 100).toInt()}%", fontSize = 12.sp, color = ChromeInk)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.editor_reset), fontSize = 12.sp, color = Color(0xFF315B83))
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
                            CanvasMode.MoveSelection -> stringResource(R.string.editor_chip_move)
                            CanvasMode.Shape -> stringResource(R.string.editor_chip_pair, shape.label, stringResource(if (shapeFilled && shape.closed) R.string.editor_chip_filled else R.string.editor_chip_outline))
                            CanvasMode.Lasso -> stringResource(when (lassoKind) { LassoKind.Smart -> R.string.editor_lasso_smart; LassoKind.Rectangle -> R.string.editor_chip_rectangle_selection; else -> R.string.editor_chip_freehand_lasso })
                            else -> stringResource(R.string.editor_chip_pair, eraserMode.label, stringResource(if (eraseActiveLayerOnly) R.string.editor_chip_active_layer else R.string.editor_chip_editable_layers))
                        }
                        val toolOptionsLabel = stringResource(R.string.editor_tool_options_description)
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
                            .semantics { contentDescription = toolOptionsLabel },
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
                                    Text(pluralStringResource(R.plurals.editor_paste_items, clipboard.size, clipboard.size), color = ChromeInk, fontSize = 14.sp)
                                }
                                InkToolButton(InkIcon.Close, stringResource(R.string.editor_clear_clipboard), { clipboard = emptyList() }, tag = english.getString(R.string.editor_clear_clipboard))
                            }
                        }
                    }
                    // Images and plain text use the bar next to the selection; cards (dates, recordings, links) keep this one.
                    val selectedCard = page.texts.firstOrNull { it.id == selectedObjectId && it.kind != "plain" }
                    if (selectedObjectId != null && selectedCard != null) {
                        val selectedText: InkText? = selectedCard
                        Surface(Modifier.align(Alignment.TopCenter).padding(10.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp) {
                            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(when (selectedText?.kind) { "timestamp" -> R.string.editor_card_date; "audio" -> R.string.editor_card_recording; "link-note", "link-web", "link-file" -> R.string.editor_card_link; else -> if (selectedObjectIsText) R.string.editor_text_box else R.string.editor_image }), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                if (selectedText?.kind == "plain" || selectedText?.kind == "timestamp") InkToolButton(InkIcon.Rename, stringResource(R.string.editor_edit_selected), {
                                    selectedText?.let { editingTextId = it.id; draft = it.text; sheet = if (it.kind == "timestamp") "timestamp" else "edit text" }
                                }, tag = english.getString(R.string.editor_edit_selected))
                                if (selectedText == null || selectedText.kind == "plain") {
                                    InkToolButton(InkIcon.FlipHorizontal, stringResource(R.string.editor_mirror_horizontal), { transformObject(flipX = true) }, tag = english.getString(R.string.editor_mirror_horizontal))
                                    InkToolButton(InkIcon.FlipVertical, stringResource(R.string.editor_mirror_vertical), { transformObject(flipY = true) }, tag = english.getString(R.string.editor_mirror_vertical))
                                    InkToolButton(InkIcon.Rotate, stringResource(R.string.editor_rotate_left), { transformObject(rotation = -90f) }, tag = english.getString(R.string.editor_rotate_left))
                                    InkToolButton(InkIcon.RotateRight, stringResource(R.string.editor_rotate_right), { transformObject(rotation = 90f) }, tag = english.getString(R.string.editor_rotate_right))
                                }
                                if (selectedText != null && selectedText.kind in listOf("link-note", "link-web", "link-file", "audio")) InkToolButton(InkIcon.Next, stringResource(R.string.editor_open_selected), { openEmbedded(selectedText) }, tag = english.getString(R.string.editor_open_selected))
                                InkToolButton(InkIcon.Trash, if (selectedText?.kind != "plain" && selectedText != null) stringResource(R.string.editor_remove_marker) else stringResource(R.string.editor_delete_selected_object), {
                                    if (selectedText != null && selectedText.kind != "plain") markerToDelete = selectedText.id
                                    else {
                                        canvas?.flushInput()
                                        applyPage(page.copy(texts = page.texts.filterNot { it.id == selectedObjectId && editableLayer(it.layerId) },
                                            images = page.images.filterNot { it.id == selectedObjectId && editableLayer(it.layerId) }))
                                        canvas?.deselectObject()
                                    }
                                }, tag = if (selectedText?.kind != "plain" && selectedText != null) "Remove marker" else "Delete selected object")
                                InkToolButton(InkIcon.Close, stringResource(R.string.editor_deselect_object), { canvas?.deselectObject() }, tag = english.getString(R.string.editor_deselect_object))
                            }
                        }
                    }
                    if (focusMode) {
                        NoteToolbox(brushState.options, selectedBrush, { brush -> selectedBrush = brush; mode = CanvasMode.Write; selectedIds = emptySet() },
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
                            } }, onRestoreBrushes = brushState::restore,
                            brushSettings = { BrushSettings(selectedBrush, brushState.options, ::updateBrush, ::addBrush, ::deleteBrush, ::pickPageColor) },
                            widthMillimetres = { it.width })
                    }
                    if (recording) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 420.dp).fillMaxWidth(),
                        color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                InkSymbol(InkIcon.Microphone, Modifier.size(20.dp), Color(0xFF9F3030))
                                Text(stringResource(when (repository.recordingStatus) {
                                    RecordingStatus.Starting -> R.string.editor_recording_starting
                                    RecordingStatus.Pausing -> R.string.editor_recording_pausing
                                    RecordingStatus.Paused -> R.string.editor_recording_paused
                                    RecordingStatus.Resuming -> R.string.editor_recording_resuming
                                    RecordingStatus.Stopping -> R.string.editor_recording_saving
                                    else -> R.string.editor_recording_active
                                }), Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                                    Text(if (repository.recordingStatus == RecordingStatus.Paused) stringResource(R.string.editor_resume) else stringResource(R.string.editor_pause))
                                }
                                TextButton(onClick = ::stopRecording, enabled = repository.recordingStatus in listOf(RecordingStatus.Recording, RecordingStatus.Paused)) { Text(stringResource(R.string.editor_finish)) }
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
                onClose = { showPages = false }, onAdd = ::addPage, onPageAction = ::pageAction,
                onDeletePages = { ids ->
                    canvas?.flushInput()
                    val current = latestNotebook.pages.map { if (it.id == page.id) page else it }
                    val kept = current.filterNot { it.id in ids }
                    if (kept.isNotEmpty() && !notebook.infiniteCanvas) {
                        // Stay on the current page if it survives, otherwise on the nearest earlier page that does.
                        val anchor = current.subList(0, safeIndex + 1).lastOrNull { it.id !in ids } ?: kept.first()
                        onNotebookChange(latestNotebook.copy(pages = kept, updatedAt = System.currentTimeMillis()))
                        pageIndex = kept.indexOfFirst { it.id == anchor.id }.coerceAtLeast(0)
                    }
                })
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
    shapeBorderNone: Boolean,
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
                view.mode = mode; view.shapeKind = shape; view.shapeFilled = shapeFilled; view.shapeFillColor = shapeFillColor; view.shapeBorderNone = shapeBorderNone; view.shapeColor = shapeColor
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
    val english = rememberEnglishResources()
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
                InkToolButton(InkIcon.Close, stringResource(R.string.editor_close_player), onClose, tag = english.getString(R.string.editor_close_player))
            }
            if (error) Text(stringResource(R.string.editor_error_play_recording), fontSize = 12.sp)
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
                    }) { Text(if (playing) stringResource(R.string.editor_pause) else stringResource(R.string.editor_play)) }
                    TextButton(enabled = ready, onClick = { position = (position + 10_000).coerceAtMost(duration); player.seekTo(position) }) { Text("+10s") }
                    TextButton(enabled = ready, onClick = { player.pause(); player.seekTo(0); position = 0; playing = false }) { Text(stringResource(R.string.editor_stop)) }
                }
            }
        }
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
                                accessibilityLabel: String = stringResource(R.string.editor_line_width), onValueChange: (Float) -> Unit) {
    val currentChange by rememberUpdatedState(onValueChange)
    val widthState = stringResource(R.string.editor_width_mm_short, value)
    Canvas(
        modifier.semantics {
            contentDescription = accessibilityLabel
            stateDescription = widthState
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

/** Thin vertical lines on the left and right page edges while [visible] (read at draw time). */
private fun Modifier.pageEdges(visible: () -> Boolean) = drawWithContent {
    drawContent()
    if (visible()) {
        val stroke = 1.dp.toPx()
        drawRect(Color(0xFFB9BFBB), size = androidx.compose.ui.geometry.Size(stroke, size.height))
        drawRect(Color(0xFFB9BFBB), topLeft = Offset(size.width - stroke, 0f), size = androidx.compose.ui.geometry.Size(stroke, size.height))
    }
}

@Composable
private fun AddPageReveal(revealPxOf: () -> Float, thresholdPx: Float, overPage: Boolean, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val revealPx = revealPxOf().coerceAtLeast(0f)
    val armed = revealPx >= thresholdPx
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }
    val progress = (revealPx / thresholdPx).coerceIn(0f, 1f)
    val scale by androidx.compose.animation.core.animateFloatAsState(if (armed) 1.08f else .72f + .28f * progress, label = "add-page-scale")
    val sage = Color(0xFF2F7A68)
    Box(modifier.fillMaxHeight().width(with(density) { revealPx.toDp() }).clipToBounds()
        .background(if (overPage) Color(0xFF2F7A68).copy(alpha = .06f + .06f * progress) else Color(0xFFEDF1EE)).testTag("add-page-reveal"),
        contentAlignment = Alignment.Center) {
        Column(Modifier.requiredWidth(200.dp).graphicsLayer { alpha = progress }, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkSymbol(InkIcon.Previous, Modifier.graphicsLayer { translationX = (1f - progress) * 18f }, color = sage)
                Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.size(64.dp).clip(CircleShape).background(if (armed) sage else Color(0xFFD6E8E1)),
                    contentAlignment = Alignment.Center) {
                    InkSymbol(InkIcon.AddPage, Modifier.size(30.dp), color = if (armed) Color.White else sage)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(if (armed) stringResource(R.string.editor_release_to_add_page) else stringResource(R.string.editor_swipe_to_add_page), color = if (armed) ChromeInk else ChromeMuted, fontSize = 13.sp)
        }
    }
}

/** Title of an editor sheet; [sheet] is the internal sheet identifier, never shown as is when a title exists. */
@Composable
private fun editorSheetTitle(sheet: String): String = when (sheet) {
    "paper" -> stringResource(R.string.editor_rail_paper)
    "table" -> stringResource(R.string.editor_rail_table)
    "stickers" -> stringResource(R.string.editor_rail_stickers)
    "pins" -> stringResource(R.string.editor_rail_pins)
    "templates" -> stringResource(R.string.editor_sheet_templates)
    "pens" -> stringResource(R.string.editor_sheet_pens)
    "layers" -> stringResource(R.string.editor_rail_layers)
    "shapes" -> stringResource(R.string.editor_rail_shapes)
    "eraser" -> stringResource(R.string.editor_eraser)
    "erase layer ink" -> stringResource(R.string.editor_sheet_erase_layer_ink)
    "erase all ink" -> stringResource(R.string.editor_sheet_erase_all_ink)
    "lasso" -> stringResource(R.string.editor_rail_lasso)
    "selection" -> stringResource(R.string.editor_sheet_selection)
    "arrange" -> stringResource(R.string.editor_sheet_arrange)
    "note style" -> stringResource(R.string.editor_sheet_note_style)
    "table style" -> stringResource(R.string.editor_sheet_table_style)
    "to text" -> stringResource(R.string.editor_convert_to_text)
    "selection layer" -> stringResource(R.string.editor_sheet_selection_layer)
    "delete selection" -> stringResource(R.string.editor_sheet_delete_selection)
    "selection style" -> stringResource(R.string.editor_sheet_selection_style)
    "selection color" -> stringResource(R.string.editor_sheet_selection_color)
    "fill" -> stringResource(R.string.editor_fill)
    "text" -> stringResource(R.string.editor_rail_text)
    "timestamp" -> stringResource(R.string.editor_date_time)
    "edit text" -> stringResource(R.string.editor_text_box)
    "rename" -> stringResource(R.string.editor_rename)
    "tags" -> stringResource(R.string.editor_sheet_tags)
    "outline" -> stringResource(R.string.editor_sheet_outline)
    "insert" -> stringResource(R.string.editor_rail_insert)
    "attachments" -> stringResource(R.string.editor_sheet_attachments)
    "recordings" -> stringResource(R.string.editor_recordings)
    "page+" -> stringResource(R.string.editor_sheet_page_add)
    "page from notebook" -> stringResource(R.string.editor_sheet_page_from_notebook)
    "link notes" -> stringResource(R.string.editor_link_notes)
    "website link" -> stringResource(R.string.editor_sheet_website_link)
    "share & export" -> stringResource(R.string.editor_sheet_share_export)
    "zoom" -> stringResource(R.string.editor_sheet_zoom)
    "gestures" -> stringResource(R.string.editor_rail_gestures)
    "customize toolbar" -> stringResource(R.string.editor_customize_toolbar)
    "page layout" -> stringResource(R.string.editor_tool_page_layout)
    "search" -> stringResource(R.string.editor_search)
    "more" -> stringResource(R.string.editor_more)
    "clear page" -> stringResource(R.string.editor_clear_page)
    "delete page" -> stringResource(R.string.editor_delete_page)
    else -> sheet.replaceFirstChar { it.uppercase() }
}

/** Display name of a toolbar tool; [tool] is the English identifier stored in the `toolbarTools` preference. */
@Composable
private fun toolbarToolLabel(tool: String): String = when (tool) {
    "Template" -> stringResource(R.string.editor_rail_template)
    "Layers" -> stringResource(R.string.editor_rail_layers)
    "Shapes" -> stringResource(R.string.editor_rail_shapes)
    "Lasso" -> stringResource(R.string.editor_rail_lasso)
    "Fill" -> stringResource(R.string.editor_fill)
    "Pressure" -> stringResource(R.string.editor_rail_pressure)
    "Gestures" -> stringResource(R.string.editor_rail_gestures)
    "Table" -> stringResource(R.string.editor_rail_table)
    "Stickers" -> stringResource(R.string.editor_rail_stickers)
    "Sticky" -> stringResource(R.string.editor_sticky_note)
    "Ruler" -> stringResource(R.string.editor_rail_ruler)
    "Zoom" -> stringResource(R.string.editor_sheet_zoom)
    "Page layout" -> stringResource(R.string.editor_tool_page_layout)
    "Text" -> stringResource(R.string.editor_rail_text)
    "Insert" -> stringResource(R.string.editor_rail_insert)
    "Page +" -> stringResource(R.string.editor_rail_page_add)
    "Share" -> stringResource(R.string.editor_rail_share)
    else -> tool
}
