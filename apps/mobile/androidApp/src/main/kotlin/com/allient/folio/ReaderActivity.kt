@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.allient.folio

import android.content.Intent
import com.composables.icons.lucide.*

import android.os.Bundle
import android.view.*
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.allient.folio.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.*
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.getOrElse
import java.io.File
import kotlin.math.roundToInt

class ReaderActivity : AppCompatActivity() {
    private lateinit var model: ReaderViewModel
    private lateinit var notebookRepository: NotebookViewModel
    private var pdf: PdfReaderSession? by mutableStateOf(null)
    private var pdfContents by mutableStateOf<List<ReaderContentsEntry>>(emptyList())
    private var publication: Publication? by mutableStateOf(null)
    private var navigator: EpubNavigatorFragment? by mutableStateOf(null)
    private var selected: PdfSelection? by mutableStateOf(null)
    private var page by mutableIntStateOf(0)
    private var engineError: String? by mutableStateOf(null)
    private var engineLoading by mutableStateOf(true)
    private var epubProgress by mutableDoubleStateOf(0.0)
    private val settings by lazy { getSharedPreferences("reader-preferences", MODE_PRIVATE) }
    /** The notebook's preferences: the reader draws with the same, shared brush presets. */
    private val editorPrefs by lazy { getSharedPreferences("editor", MODE_PRIVATE) }
    private var fontScale by mutableDoubleStateOf(1.0)
    private var themeName by mutableStateOf("Light")
    private var scroll by mutableStateOf(false)
    private var lineHeight by mutableDoubleStateOf(1.5)
    private val penFilter = PenPressureFilter()
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = penFilter.dispatch(event) { super.dispatchTouchEvent(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        PenSettings.init(this)
        AppAnalytics.init(this)
        AppAnalytics.screen("reader")
        // Native navigators are rebuilt from persisted locators, never restored without a Publication.
        super.onCreate(null)
        selected = savedInstanceState?.getString("selectionAnchor")?.let { PdfSelection(it, savedInstanceState.getString("selectionText").orEmpty()) }
        enableEdgeToEdge()
        setReaderImmersive()
        fontScale = settings.getFloat("fontScale", 1f).toDouble()
        themeName = settings.getString("theme", "Light") ?: "Light"
        scroll = settings.getBoolean("scroll", false)
        lineHeight = settings.getFloat("lineHeight", 1.5f).toDouble()
        requestedOrientation = readerOrientation(settings.getInt("readerLandscape", 0))
        model = ViewModelProvider(this)[ReaderViewModel::class.java]
        notebookRepository = ViewModelProvider(this)[NotebookViewModel::class.java]
        lifecycleScope.launch {
            try {
                model.load(requireNotNull(intent.getStringExtra("documentId")))
                val document = requireNotNull(model.current) { "This document is no longer in your library" }
                check(File(document.path).isFile) { "The original file is not on this device. Re-import the same PDF or EPUB to continue; your saved notes and reading position will reconnect automatically." }
                if (document.format == "pdf") {
                    pdf = withContext(Dispatchers.IO) { PdfReaderSession(File(document.path)) }
                    pdfContents = withContext(Dispatchers.IO) {
                        try { pdf!!.contents() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { AppAnalytics.recordError(error, "pdf_contents"); emptyList() }
                    }
                    page = document.locator.takeIf { it.isNotBlank() }?.let { JSONObject(it).optInt("page") }?.coerceIn(0, pdf!!.pageCount - 1) ?: 0
                } else publication = withContext(Dispatchers.IO) { EpubReaderEngine.open(this@ReaderActivity, File(document.path)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { engineError = error.message ?: "Could not open document" }
            finally { engineLoading = false }
        }
        setContent { MaterialTheme(colorScheme = if (themeName == "Dark") darkColorScheme(primary = Color.White, onPrimary = Color.Black, secondary = Color.LightGray, secondaryContainer = Color.DarkGray) else lightColorScheme(primary = FolioBrand.Ink, secondary = FolioBrand.Muted, secondaryContainer = FolioBrand.SageSoft, onSecondaryContainer = FolioBrand.Ink, tertiary = FolioBrand.Sage, surface = Color.White, onSurface = FolioBrand.Ink, surfaceContainerLow = Color.White), typography = folioTypography()) { androidx.compose.runtime.CompositionLocalProvider(LocalPenThreshold provides PenSettings.thresholdState.floatValue) { ReaderContent() } } }
    }
    // API 36+ displays may ignore app orientation requests even at phone-sized
    // window widths (for example a resized tablet). Follow the display to avoid letterboxing.
    private fun supportsLandscapeLock() = android.os.Build.VERSION.SDK_INT < 36
    private fun readerOrientation(direction: Int): Int = when {
        !supportsLandscapeLock() -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        direction == 1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        direction == 2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setReaderImmersive()
    }
    private fun setReaderImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, !settings.getBoolean("gestureSystemBars", true))
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (settings.getBoolean("gestureSystemBars", true)) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun preferences() = EpubPreferences(fontSize = fontScale, theme = when (themeName) { "Dark" -> Theme.DARK; "Sepia" -> Theme.SEPIA; else -> Theme.LIGHT }, scroll = scroll, lineHeight = lineHeight)
    private fun applyPreferences() {
        navigator?.submitPreferences(preferences())
        settings.edit().putFloat("fontScale",fontScale.toFloat()).putString("theme",themeName).putBoolean("scroll",scroll).putFloat("lineHeight", lineHeight.toFloat()).apply()
    }
    private fun currentAnchor(): String? = if (pdf != null) JSONObject().put("page",page).toString() else navigator?.currentLocator?.value?.toJSON()?.toString()
    /** [searchQuote] is set for search results only, so the match opens as a selection; saved marks just open their page. */
    private fun jump(anchor: String, searchQuote: String? = null) {
        selected = null
        if (pdf != null) { page = JSONObject(anchor).getInt("page").coerceIn(0,pdf!!.pageCount - 1); if (searchQuote != null && JSONObject(anchor).has("rects")) selected = PdfSelection(anchor, searchQuote) }
        else Locator.fromJSON(JSONObject(anchor))?.let { navigator?.go(it) }
    }
    private fun saveSelection(kind: AnnotationKind, color: Int, note: String = "", underlineStyle: String = "solid") {
        val selection = selected ?: return
        model.annotate(kind, if (kind == AnnotationKind.UNDERLINE) JSONObject(selection.anchor).put("underlineStyle", underlineStyle).toString() else selection.anchor, selection.text, note, color)
        selected = null; navigator?.clearSelection()
    }
    @Composable private fun ReaderContent() {
        val scope = rememberCoroutineScope()
        val doc = model.current
        var panel by rememberSaveable { mutableStateOf<String?>(null) }
        var chromeVisible by rememberSaveable { mutableStateOf(intent.getBooleanExtra("showReaderControls", false)) }
        var showProgress by rememberSaveable { mutableStateOf(true) }
        var showBookTabs by remember { mutableStateOf(settings.getBoolean("showBookTabs", true)) }
        var readerTab by rememberSaveable { mutableStateOf("Progress") }
        var penSettings by remember { mutableStateOf(false) }
        if (penSettings) GlobalPenSettingsDialog { penSettings = false }
        var gestures by remember { mutableStateOf(ReaderGesturePreferences.read(settings)) }
        LaunchedEffect(gestures) { gestures.save(settings); setReaderImmersive() }
        BackHandler(enabled = !gestures.back) { chromeVisible = true; panel = "Gestures" }
        var openTabIds by remember {
            val ids = runCatching { org.json.JSONArray(settings.getString("openBookTabs", "[]")) }.getOrElse { org.json.JSONArray() }
            mutableStateOf((List(ids.length()) { ids.optString(it) }.filter { it.isNotBlank() } + listOfNotNull(intent.getStringExtra("documentId"))).distinct().takeLast(12))
        }
        LaunchedEffect(openTabIds) { settings.edit().putString("openBookTabs", org.json.JSONArray(openTabIds).toString()).apply() }
        val openBooks = openTabIds.mapNotNull { id -> (model.documents + listOfNotNull(doc)).firstOrNull { it.id == id && it.deletedAt == 0L } }
        fun switchBook(id: String) {
            if (id == doc?.id) return
            saveReadingPosition()
            panel = null
            intent.putExtra("documentId", id).putExtra("showReaderControls", true)
            recreate()
        }
        var contentsTab by rememberSaveable { mutableStateOf("TOC") }
        var expandedContents by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
        val epubContents = remember(publication) {
            val targets = mutableMapOf<String, Link>()
            fun convert(links: List<Link>, parent: String = "epub"): List<ReaderContentsEntry> = links.mapIndexed { index, link ->
                val id = "$parent-$index"
                targets[id] = link
                ReaderContentsEntry(id, link.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}", children = convert(link.children, id))
            }
            convert(publication?.tableOfContents.orEmpty().ifEmpty { publication?.readingOrder.orEmpty() }) to targets
        }
        val outline = if (pdf != null) pdfContents else epubContents.first
        val activeChapter = if (pdf != null) currentReaderChapter(pdfContents, page) else null
        var drawingToolbar by rememberSaveable { mutableStateOf(false) }
        var drawingPopup by rememberSaveable { mutableStateOf<String?>(null) }
        var splitHorizontal by rememberSaveable { mutableStateOf(settings.getBoolean("splitHorizontal", true)) }
        var pdfZoom by rememberSaveable { mutableFloatStateOf(settings.getFloat("pdfZoom", 1f)) }
        var pdfMargin by rememberSaveable { mutableFloatStateOf(settings.getFloat("pdfMargin", 0f).coerceIn(0f, 16f)) }
        var pdfContrast by rememberSaveable { mutableFloatStateOf(settings.getFloat("pdfContrast", 1f)) }
        var pdfBrightness by rememberSaveable { mutableFloatStateOf(settings.getFloat("pdfBrightness", 0f)) }
        var pdfEmbolden by rememberSaveable { mutableIntStateOf(settings.getInt("pdfEmbolden", 0).coerceIn(0, 5)) }
        var pdfSharpen by rememberSaveable { mutableIntStateOf(settings.getInt("pdfSharpen", 0).coerceIn(0, 5)) }
        var pdfBleach by rememberSaveable { mutableIntStateOf(settings.getInt("pdfBleach", 0).coerceIn(0, 100)) }
        var pdfDarken by rememberSaveable { mutableIntStateOf(settings.getInt("pdfDarken", 0).coerceIn(0, 100)) }
        var pdfSmoothing by rememberSaveable { mutableStateOf(settings.getBoolean("pdfSmoothing", true)) }
        val imageAdjustments = ReaderImageAdjustments(pdfEmbolden, pdfSharpen, pdfBleach, pdfDarken, pdfSmoothing)
        LaunchedEffect(imageAdjustments) {
            settings.edit().putInt("pdfEmbolden", pdfEmbolden).putInt("pdfSharpen", pdfSharpen)
                .putInt("pdfBleach", pdfBleach).putInt("pdfDarken", pdfDarken).putBoolean("pdfSmoothing", pdfSmoothing).apply()
        }
        var pdfScroll by rememberSaveable { mutableStateOf(settings.getBoolean("pdfScroll", false)) }
        var pdfSpread by rememberSaveable { mutableStateOf(settings.getBoolean("pdfSpread", false)) }
        var pdfRtl by rememberSaveable { mutableStateOf(settings.getBoolean("pdfRtl", false)) }
        var pdfContentFit by rememberSaveable { mutableStateOf(settings.getBoolean("pdfContentFit", false)) }
        var pdfFitWidth by rememberSaveable { mutableStateOf(settings.getBoolean("pdfFitWidth", false)) }
        var pdfEnhancement by rememberSaveable { mutableFloatStateOf(settings.getFloat("pdfEnhancement", 0f).coerceIn(0f, 17f)) }
        var pdfCrop by rememberSaveable { mutableStateOf(settings.getString("pdfCrop", "") ?: "") }
        var pendingCrop by rememberSaveable { mutableStateOf("") }
        val cropRect = remember(pdfCrop) { pdfCrop.split(",").mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }?.let { android.graphics.RectF(it[0], it[1], it[2], it[3]) } }
        LaunchedEffect(pdfZoom, pdfMargin, pdfContrast, pdfBrightness, pdfScroll, pdfSpread, pdfRtl, pdfFitWidth, pdfContentFit, pdfEnhancement, pdfCrop, splitHorizontal) {
            settings.edit().putFloat("pdfZoom", pdfZoom).putFloat("pdfMargin", pdfMargin)
                .putFloat("pdfContrast", pdfContrast).putFloat("pdfBrightness", pdfBrightness)
                .putBoolean("pdfScroll", pdfScroll).putBoolean("pdfSpread", pdfSpread).putBoolean("pdfRtl", pdfRtl)
                .putBoolean("pdfContentFit", pdfContentFit).putBoolean("pdfFitWidth", pdfFitWidth).putFloat("pdfEnhancement", pdfEnhancement)
                .putString("pdfCrop", pdfCrop).putBoolean("splitHorizontal", splitHorizontal).apply()
        }
        val redoInk = remember { mutableStateListOf<ReaderAnnotation>() }
        var bottomChromeHeight by remember { mutableIntStateOf(0) }
        var topChromeHeight by remember { mutableIntStateOf(0) }
        // Where the selection popup sits, so page taps and swipes underneath it are left to the popup.
        var selectionBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var selectionTextBounds by remember { mutableStateOf<Pair<String, androidx.compose.ui.geometry.Rect>?>(null) }
        var tool by rememberSaveable { mutableStateOf("Read") }
        var color by rememberSaveable { mutableIntStateOf(0xFFFFD85A.toInt()) }
        val brushState = rememberBrushPresetsState(editorPrefs)
        // Restores the reader's last pen once per loaded preset state (rotation and relaunch keep it).
        val restoredBrush = remember(brushState) { settings.getString("reader-selected-brush", null)?.let { id -> brushState.presets.firstOrNull { it.presetId == id } }?.also { brushState.selected = it } }
        val penBrush = restoredBrush.let { brushState.selected }
        LaunchedEffect(penBrush.presetId) { settings.edit().putString("reader-selected-brush", penBrush.presetId).apply() }
        var editingMark by remember { mutableStateOf<ReaderAnnotation?>(null) }
        var markKind by remember { mutableStateOf(AnnotationKind.HIGHLIGHT) }
        var noteAnchor by rememberSaveable { mutableStateOf<String?>(null) }
        var noteQuote by rememberSaveable { mutableStateOf("") }
        var noteDraft by rememberSaveable { mutableStateOf("") }
        var editingId by rememberSaveable { mutableStateOf<String?>(null) }
        var search by rememberSaveable { mutableStateOf("") }
        var results by remember { mutableStateOf<List<ReaderSearchResult>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }
        var searchJob by remember { mutableStateOf<Job?>(null) }
        var jumpDialog by rememberSaveable { mutableStateOf(false) }
        var pageDraft by rememberSaveable { mutableStateOf("") }
        var scrubPage by remember { mutableFloatStateOf(page.toFloat()) }
        var exporting by remember { mutableStateOf(false) }
        var recognizing by remember { mutableStateOf(false) }
        var readerNotice by remember { mutableStateOf<String?>(null) }
        var splitContent by rememberSaveable { mutableStateOf(settings.getString("splitContent", "Handwritten Notes") ?: "Handwritten Notes") }
        var splitDocumentId by rememberSaveable { mutableStateOf(settings.getString("splitDocumentId", null)) }
        var splitStartPage by rememberSaveable { mutableIntStateOf(settings.getInt("splitStartPage", 0)) }
        var landscapeDirection by rememberSaveable { mutableIntStateOf(if (supportsLandscapeLock()) settings.getInt("readerLandscape", 0) else 0) }
        var splitNoteDraft by rememberSaveable(doc?.id) { mutableStateOf(settings.getString("split-note-draft.${doc?.id}", "") ?: "") }
        var studyMode by rememberSaveable { mutableStateOf(settings.getBoolean("splitActive", false) && settings.getString("splitOwner", null) == intent.getStringExtra("documentId")) }
        var studyNotebookId by rememberSaveable { mutableStateOf(settings.getString("splitNotebookId", null)) }
        LaunchedEffect(studyMode, splitContent, splitDocumentId, splitStartPage, studyNotebookId) {
            settings.edit().putBoolean("splitActive", studyMode).putString("splitOwner", intent.getStringExtra("documentId"))
                .putString("splitContent", splitContent).putString("splitDocumentId", splitDocumentId)
                .putInt("splitStartPage", splitStartPage).putString("splitNotebookId", studyNotebookId).apply()
        }
        val nav = navigator
        val currentLocator by produceState<Locator?>(null, nav) { nav?.currentLocator?.collect { value = it } }
        val studyNotebook = studyNotebookId?.let { id -> notebookRepository.notebooks.firstOrNull { it.id == id && it.deletedAt == 0L } }
        BackHandler(enabled = tool == "MarqueeCrop") { pendingCrop = ""; tool = "Read"; chromeVisible = true }
        BackHandler(enabled = selected != null) { selected = null; navigator?.clearSelection() }
        BackHandler(enabled=studyMode) { studyMode=false }
        BackHandler(enabled = drawingToolbar && !studyMode) { drawingToolbar = false; drawingPopup = null; tool = "Read" }
        fun handlePageTap(x: Float, y: Float, width: Float, height: Float): Boolean {
            if (selected != null) { selected = null; navigator?.clearSelection(); return true }
            if (drawingToolbar) return false
            if (chromeVisible) {
                // The native PDF/EPUB view fills the screen beneath the Compose controls.
                // Leave taps in the overlay bands for those controls; only a tap on the
                // actual reading area dismisses the chrome.
                val overlayTop = dp(ReaderChromeBands.TOP_DP)
                val overlayBottom = dp(ReaderChromeBands.BOTTOM_DP)
                if (y < overlayTop || y > height - overlayBottom) return false
                chromeVisible = false
                return true
            }
            if (x in width * .28f..width * .72f && y in height * .28f..height * .72f) {
                chromeVisible = true
                return true
            }
            if (gestures.sides && gestures.clickLongPress && (x < width * .2f || x > width * .8f)) {
                val forward = (x > width * .8f) != pdfRtl
                if (pdf != null) page = (page + if (forward) 1 else -1).coerceIn(0, pdf!!.pageCount - 1)
                else if (forward) nav?.goForward(true) else nav?.goBackward(true)
                return true
            }
            return false
        }
        fun openStudyMode() {
            splitContent = "Handwritten Notes"
            val source = doc ?: return
            scope.launch {
                snapshotFlow { notebookRepository.loading }.first { !it }
                val pageReference = if (pdf != null) "PDF page ${page + 1}" else "EPUB · ${(epubProgress * 100).toInt()}%"
                val contextLine = "From ${source.title} · $pageReference"
                fun contextPage() = NotebookPage(texts=buildList {
                    add(InkText(text=contextLine,x=.08f,y=.06f,size=17f,color=0xFF626262.toInt(),bold=true))
                    selected?.text?.takeIf { it.isNotBlank() }?.let { add(InkText(text=it,x=.08f,y=.12f,size=14f,color=0xFF464646.toInt())) }
                },outline=contextLine)
                val title="Notes · ${source.title}"
                val existing=studyNotebookId?.let { id -> notebookRepository.notebooks.firstOrNull { it.id==id && it.deletedAt==0L } }
                    ?: notebookRepository.notebooks.firstOrNull { it.title==title && it.deletedAt==0L }
                if (existing != null) {
                    studyNotebookId=existing.id
                    val last=existing.pages.firstOrNull { it.id==existing.lastViewedPageId } ?: existing.pages.lastOrNull()
                    if (last?.outline != contextLine) {
                        val pageNote=contextPage()
                        val updated=existing.copy(pages=existing.pages+pageNote,lastViewedPageId=pageNote.id,updatedAt=System.currentTimeMillis())
                        notebookRepository.saveNotes(notebookRepository.notebooks.map { if(it.id==updated.id)updated else it })
                    }
                    studyMode=true
                    return@launch
                }
                val pageNote=contextPage()
                val note=Notebook(title=title,pages=listOf(pageNote),lastViewedPageId=pageNote.id)
                notebookRepository.saveNotes(notebookRepository.notebooks+note)
                studyNotebookId=note.id
                studyMode=true
            }
        }
        val bookmark = currentAnchor()?.let { anchor -> model.annotations.firstOrNull { it.kind == AnnotationKind.BOOKMARK && it.anchor == anchor } }
        fun toggleBookmark() {
            if (bookmark != null) model.remove(bookmark)
            else currentAnchor()?.let { model.annotate(AnnotationKind.BOOKMARK, it, note = if (pdf != null) "Page ${page + 1}" else nav?.currentLocator?.value?.title.orEmpty()) }
        }
        LaunchedEffect(nav) {
            if (nav != null) {
                @OptIn(FlowPreview::class)
                nav.currentLocator.debounce(350).collect { locator ->
                    epubProgress = locator.locations.totalProgression ?: locator.locations.progression ?: 0.0
                    model.position(locator.toJSON().toString(), epubProgress)
                }
            }
        }
        LaunchedEffect(page,pdf) {
            scrubPage = page.toFloat()
            pdf?.let { model.position(JSONObject().put("page",page).toString(), if (it.pageCount <= 1) 1.0 else page.toDouble() / (it.pageCount - 1)) }
        }
        LaunchedEffect(nav,model.annotations) {
            nav?.applyDecorations(model.annotations.filter { it.kind == AnnotationKind.HIGHLIGHT || it.kind == AnnotationKind.UNDERLINE }.mapNotNull { a ->
                Locator.fromJSON(JSONObject(a.anchor))?.let { locator -> Decoration(a.id, locator, if (a.kind == AnnotationKind.UNDERLINE) Decoration.Style.Underline(a.color) else Decoration.Style.Highlight(a.color)) }
            }, "annotations")
        }
        DisposableEffect(nav) {
            val listener = object : org.readium.r2.navigator.DecorableNavigator.Listener {
                override fun onDecorationActivated(event: org.readium.r2.navigator.DecorableNavigator.OnActivatedEvent): Boolean {
                    panel = "Annotations"; return true
                }
            }
            nav?.addDecorationListener("annotations", listener)
            onDispose { nav?.removeDecorationListener(listener) }
        }
        fun editNote(anchor: String, quote: String = "", existing: ReaderAnnotation? = null) {
            noteAnchor = anchor; noteQuote = quote; noteDraft = existing?.note.orEmpty(); editingId = existing?.id
        }
        fun export(kind: String) {
            AppAnalytics.event("book_exported", "format" to kind)
            if (doc == null || exporting) return
            exporting = true
            val notes = model.annotations.toList(); val markdown = model.exportNotes()
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(cacheDir,"exports").apply { mkdirs() }
                        val name = doc.title.toSafeFileName("Book")
                        if (kind == "pdf") ReaderExport.annotatedPdf(this@ReaderActivity, doc, notes, File(dir,"$name (annotated).pdf"))
                        else File(dir,"$name (notes).md").apply { writeText(markdown) }
                    }
                    NotebookFiles(this@ReaderActivity).shareFile(file, if (kind == "pdf") "application/pdf" else "text/markdown")
                } catch (error: Exception) { if (error is CancellationException) throw error; model.error = error.message ?: "Could not export document" }
                finally { exporting = false }
            }
        }
        fun recognizeScannedPage() {
            AppAnalytics.event("ocr_run")
            val session = pdf ?: return
            val index = page
            recognizing = true
            scope.launch {
                try {
                    val count = session.recognize(index)
                    tool = "Select"
                    chromeVisible = true
                    readerNotice = if (count > 0) "Recognized $count words on page ${index + 1}. Drag across text to highlight." else "No text was recognized on this page."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { model.error = error.message ?: "Could not recognize text" }
                finally { recognizing = false; panel = null }
            }
        }
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { BoxWithConstraints(Modifier.fillMaxSize()) {
            val readerHeight = maxHeight
            val readerWidth = maxWidth
            @Composable fun ReaderPane(modifier: Modifier) { BoxWithConstraints(modifier) {
            val paneWidth = maxWidth
            val paneHeight = maxHeight
            Box(Modifier.fillMaxSize()) {
            when {
                    engineLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    engineError != null -> Column(Modifier.align(Alignment.Center).padding(28.dp)) { Text("Could not open this document",style=MaterialTheme.typography.titleLarge); Text(engineError.orEmpty(),Modifier.padding(top=12.dp)); TextButton(onClick={finish()}) { Text("Back to library") } }
                    pdf != null -> {
                        val session = pdf!!
                        val records = model.annotations + listOfNotNull(selected?.let { ReaderAnnotation("selection",doc!!.id,AnnotationKind.HIGHLIGHT,it.anchor,color=0xFF76B9EF.toInt(),createdAt=0) })
                        @Composable fun PdfPage(index: Int, modifier: Modifier) {
                        val fittedContent by produceState<android.graphics.RectF?>(null, session, index, pdfContentFit) {
                            value = null
                            if (pdfContentFit) {
                                try { value = withContext(Dispatchers.IO) { session.contentBounds(index) } }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) { model.error = error.message ?: "Could not fit page content" }
                            }
                        }
                        AndroidView(factory={PdfPageReaderView(it)},update={ view ->
                            view.show(session,index,records); view.tool=tool; view.chromeVisible=chromeVisible; view.brush=penBrush; view.inkColor=penBrush.color
                            view.setLayout(pdfContentFit && pdfFitWidth, cropRect ?: fittedContent, pdfEnhancement, pdfScroll || pdfSpread, pdfRtl, alignTop = pdfContentFit && cropRect == null)
                            view.setDisplay(pdfZoom, pdfMargin, pdfContrast, pdfBrightness)
                            view.setImageAdjustments(imageAdjustments)
                            view.gestures = gestures; view.onShowControls = { chromeVisible = true }
                            view.onZoomChange = { pdfZoom = it }
                            view.setCropSelection(pendingCrop.split(",").mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }?.let { android.graphics.RectF(it[0], it[1], it[2], it[3]) })
                            view.onCrop = { rect -> pendingCrop = "${rect.left},${rect.top},${rect.right},${rect.bottom}" }
                            view.onMarqueeZoom = { pdfZoom = it; tool = "Read" }
                            view.onErase = { mark -> model.remove(mark); redoInk.clear() }
                            view.onPageTurn = { forward ->
                                if (forward) page = (page + 1).coerceAtMost(session.pageCount - 1)
                                else page = (page - 1).coerceAtLeast(0)
                                selected = null
                            }
                            view.onPageTap = { x, y, width, height -> handlePageTap(x, y, width, height) }
                            view.onSelectionBounds = { anchor, rect ->
                                if (selected?.anchor == anchor) selectionTextBounds = anchor to androidx.compose.ui.geometry.Rect(rect.left, rect.top, rect.right, rect.bottom)
                            }
                            view.onSelection={selection -> selected=selection; if(selection != null) chromeVisible=false; if(selection==null) model.error="No selectable text here. This may be a scanned page; OCR is required for text highlights."}
                            view.onInk={model.annotate(AnnotationKind.INK,it,color=penBrush.color); redoInk.clear()}; view.onError={model.error=it}
                        },modifier=modifier.clipToBounds())
                        }
                        if (pdfSpread && tool == "Read") {
                            val pager = rememberPagerState(initialPage = page / 2, pageCount = { (session.pageCount + 1) / 2 })
                            LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { page = it * 2 } }
                            LaunchedEffect(page) { if (pager.currentPage != page / 2) pager.scrollToPage(page / 2) }
                            HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = pdfRtl, userScrollEnabled = gestures.slide) { spread ->
                                Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.outlineVariant), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                    val pair = listOf(spread * 2, spread * 2 + 1).let { if (pdfRtl) it.reversed() else it }
                                    pair.forEach { index ->
                                        if (index < session.pageCount) PdfPage(index, Modifier.weight(1f).fillMaxHeight())
                                        else Spacer(Modifier.weight(1f).fillMaxHeight())
                                    }
                                }
                            }
                        } else if (pdfScroll && tool == "Read") {
                            val listState = rememberLazyListState(initialFirstVisibleItemIndex = page)
                            fun mostVisiblePage(): Int {
                                val layout = listState.layoutInfo
                                return layout.visibleItemsInfo.maxByOrNull { item ->
                                    (minOf(item.offset + item.size, layout.viewportEndOffset) - maxOf(item.offset, layout.viewportStartOffset)).coerceAtLeast(0)
                                }?.index ?: listState.firstVisibleItemIndex
                            }
                            LaunchedEffect(listState) {
                                snapshotFlow { mostVisiblePage() }.collect { page = it }
                            }
                            LaunchedEffect(page) {
                                if (page != mostVisiblePage()) listState.scrollToItem(page)
                            }
                            LazyColumn(Modifier.fillMaxSize(), state = listState, userScrollEnabled = gestures.slide) {
                                items(session.pageCount) { index ->
                                    var ratio by remember(session, index) { mutableFloatStateOf(.707f) }
                                    LaunchedEffect(session, index) { ratio = withContext(Dispatchers.IO) { session.aspect(index) } }
                                    PdfPage(index, Modifier.fillMaxWidth().height(paneWidth / ratio))
                                }
                            }
                        } else PdfPage(page, Modifier.fillMaxSize())
                    }
                    publication != null -> AndroidView(factory={ context ->
                        FragmentContainerView(context).apply {
                            id = READER_CONTAINER_ID
                            val callback = object : ActionMode.Callback {
                                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                    menu.add(0,1,1,"Highlight"); menu.add(0,2,2,"Add note"); menu.add(0,3,3,"Underline"); return true
                                }
                                override fun onPrepareActionMode(mode: ActionMode,menu: Menu)=false
                                override fun onDestroyActionMode(mode: ActionMode) {}
                                override fun onActionItemClicked(mode: ActionMode,item: MenuItem): Boolean {
                                    if(item.itemId !in 1..3) return false
                                    scope.launch {
                                        navigator?.currentSelection()?.let { selection ->
                                            selected=PdfSelection(selection.locator.toJSON().toString(),selection.locator.text.highlight.orEmpty())
                                            val location = IntArray(2)
                                            val view = navigator?.view
                                            view?.getLocationInWindow(location)
                                            val rect = selection.rect ?: android.graphics.RectF(0f, (view?.height ?: 0) * .4f, (view?.width ?: 0).toFloat(), (view?.height ?: 0) * .45f)
                                            selectionTextBounds = selected!!.anchor to androidx.compose.ui.geometry.Rect(rect.left + location[0], rect.top + location[1], rect.right + location[0], rect.bottom + location[1])
                                            if(item.itemId==2) editNote(selected!!.anchor,selected!!.text)
                                        }
                                        mode.finish()
                                    }; return true
                                }
                            }
                            val factory=EpubNavigatorFactory(publication!!).createFragmentFactory(
                                initialLocator=doc?.locator?.takeIf { it.isNotBlank() }?.let { Locator.fromJSON(JSONObject(it)) },
                                initialPreferences=preferences(),configuration=EpubNavigatorFragment.Configuration(selectionActionModeCallback=callback),
                                listener=object:EpubNavigatorFragment.Listener {
                                    override fun onExternalLinkActivated(url: org.readium.r2.shared.util.AbsoluteUrl) {
                                        val uri=android.net.Uri.parse(url.toString())
                                        if(uri.scheme in listOf("https","http","mailto")) try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,uri)) } catch(error:android.content.ActivityNotFoundException){model.error="No app can open this link"}
                                    }
                                    override fun onResourceLoadFailed(href: org.readium.r2.shared.util.Url,error: org.readium.r2.shared.util.data.ReadError) { model.error="Could not load this chapter: ${error.message}" }
                                })
                            val fragment=factory.instantiate(classLoader,EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                            post {
                                if (isAttachedToWindow && !isFinishing && !supportFragmentManager.isStateSaved) {
                                    supportFragmentManager.beginTransaction().replace(id,fragment,"reader-epub").commitNow()
                                    navigator=fragment
                                }
                            }
                        }
                    },modifier=Modifier.fillMaxSize().clipToBounds().pointerInput(scroll,nav,chromeVisible,gestures) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var handled = false
                            var turnedPage = false
                            while (!handled) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val delta = change.position - down.position
                                val horizontal = kotlin.math.abs(delta.x)
                                val vertical = kotlin.math.abs(delta.y)
                                if (!gestures.slide && (horizontal > 18.dp.toPx() || vertical > 18.dp.toPx())) change.consume()
                                if (gestures.bottom && !change.pressed && down.position.y > size.height - 48.dp.toPx() && delta.y < -48.dp.toPx() && vertical > horizontal) {
                                    chromeVisible = true; change.consume(); handled = true
                                } else if (gestures.slide && !scroll && horizontal >= maxOf(48.dp.toPx(), size.width * .12f) && horizontal > vertical * 1.25f) {
                                    change.consume()
                                    if (delta.x < 0) nav?.goForward(true) else nav?.goBackward(true)
                                    turnedPage = true
                                    handled = true
                                } else if (!change.pressed) {
                                    if (!turnedPage && horizontal < 18.dp.toPx() && vertical < 18.dp.toPx() &&
                                        handlePageTap(down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat())
                                    ) change.consume()
                                    handled = true
                                }
                            }
                        }
                    })
                }
            }
            if (tool == "MarqueeCrop") Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(4f), shadowElevation = 3.dp) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Drag to select an area. Drag its corners to adjust.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Marquee Crop", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { pendingCrop = ""; tool = "Read"; chromeVisible = true }) { Text("Cancel") }
                        TextButton(enabled = pendingCrop.isNotBlank(), onClick = { pdfCrop = pendingCrop; pdfContentFit = false; pdfZoom = 1f; pdfFitWidth = false; pendingCrop = ""; tool = "Read" }) {
                            InkSymbol(InkIcon.Saved, Modifier.size(24.dp)); Text("Apply crop")
                        }
                    }
                }
            }
            if (!chromeVisible && showProgress && selected == null) {
                ReaderStatusLine(
                    position = if (pdf != null) "${page + 1}/${pdf!!.pageCount}" else "${(epubProgress * 100).toInt()}%",
                    // The chapter title never swaps for a "Saving…" label: that flickered after every stroke.
                    chapter = (activeChapter?.title ?: currentLocator?.title).orEmpty().takeUnless { it == doc?.title }.orEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (drawingToolbar && !chromeVisible && pdf != null) {
                val pageInk = model.annotations.filter { it.kind == AnnotationKind.INK && JSONObject(it.anchor).optInt("page", -1) == page }
                ReaderDrawingToolbar(tool, brushState, bookmark != null, currentAnchor() != null,
                    pageInk.isNotEmpty(), redoInk.isNotEmpty(),
                    onClose = { tool = "Read"; drawingToolbar = false; drawingPopup = null },
                    onPan = { tool = "Read"; drawingPopup = null },
                    onBrush = { brush -> brushState.selected = brush; tool = "Pen"; drawingPopup = null },
                    onBrushChange = { brush -> brushState.update(brush); tool = "Pen" },
                    onBrushSettings = { drawingPopup = null },
                    onErase = { tool = "Eraser"; drawingPopup = if (drawingPopup == "Eraser") null else "Eraser" },
                    onUndo = { scope.launch {
                        // Undo stays enabled while a stroke is still being saved; it simply waits for the save, so the button never blinks.
                        snapshotFlow { model.pendingWrites }.first { it == 0 }
                        model.annotations.lastOrNull { it.kind == AnnotationKind.INK && JSONObject(it.anchor).optInt("page", -1) == page }?.let { redoInk.add(it); model.remove(it) }
                    } },
                    onRedo = { if (redoInk.isNotEmpty()) scope.launch { snapshotFlow { model.pendingWrites }.first { it == 0 }; if (redoInk.isNotEmpty()) model.save(redoInk.removeAt(redoInk.lastIndex)) } },
                    onBookmark = ::toggleBookmark,
                    onSelect = { tool = "Select"; drawingToolbar = false; drawingPopup = null },
                    onMore = { chromeVisible = true; drawingPopup = null },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).widthIn(max = 720.dp))
                drawingPopup?.let { popup ->
                    Surface(Modifier.align(Alignment.TopCenter).padding(top = 78.dp, start = 16.dp, end = 16.dp).widthIn(max = 480.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 3.dp) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Eraser", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                InkToolButton(InkIcon.Close, "Close eraser options", { drawingPopup = null }, buttonSize = 48.dp)
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().clickable { tool = "Eraser"; drawingPopup = null }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                InkSymbol(InkIcon.Eraser, color = MaterialTheme.colorScheme.onSurface); Text("Stroke eraser", Modifier.padding(start = 16.dp))
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().clickable { drawingPopup = "Clear" }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                InkSymbol(InkIcon.Trash, color = MaterialTheme.colorScheme.onSurface); Text("Erase all ink on this page", Modifier.padding(start = 16.dp))
                            }
                            if (popup == "Clear") Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Clear this page?", Modifier.weight(1f)); TextButton(onClick = { pageInk.forEach(model::remove); redoInk.clear(); drawingPopup = null }) { Text("Erase all") }
                            }
                        }
                    }
                }
            }
            selected?.let { selection ->
                val overlapping = remember(selection, model.annotations) { if (pdf != null) overlappingPdfMarks(selection, model.annotations) else emptyList() }
                val textBounds = selectionTextBounds?.takeIf { it.first == selection.anchor }?.second
                val density = androidx.compose.ui.platform.LocalDensity.current
                val margin = with(density) { 12.dp.roundToPx() }
                // Wide enough clearance that the selection handles (above the first line, below the last) stay reachable.
                val gap = with(density) { 40.dp.roundToPx() }
                var toolbarDrag by remember { mutableStateOf(androidx.compose.ui.unit.IntOffset.Zero) }
                val toolbarGeometry = remember { object { var base = androidx.compose.ui.unit.IntOffset.Zero; var toolbar = androidx.compose.ui.unit.IntSize.Zero; var window = androidx.compose.ui.unit.IntSize.Zero } }
                val positionProvider = remember(textBounds, margin, gap, toolbarDrag) {
                    object : androidx.compose.ui.window.PopupPositionProvider {
                        override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize): androidx.compose.ui.unit.IntOffset {
                            val base = selectionToolbarPosition(textBounds ?: androidx.compose.ui.geometry.Rect.Zero, windowSize, popupContentSize, margin, gap)
                            toolbarGeometry.base = base; toolbarGeometry.toolbar = popupContentSize; toolbarGeometry.window = windowSize
                            return draggedToolbarPosition(base, toolbarDrag, windowSize, popupContentSize, margin)
                        }
                    }
                }
                if (textBounds != null) androidx.compose.ui.window.Popup(popupPositionProvider = positionProvider) {
                    Surface(Modifier.widthIn(max = (readerWidth - 24.dp).coerceAtLeast(1.dp)).zIndex(2f),
                        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 6.dp) {
                        ReaderSelectionToolbar(color, pdf != null, overlapping.isNotEmpty(),
                            onEditMark = overlapping.firstOrNull()?.let { mark ->
                                { selected = null; navigator?.clearSelection(); editingMark = mark; markKind = mark.kind; color = mark.color; chromeVisible = true }
                            },
                            onDrag = { delta ->
                                val next = toolbarDrag + androidx.compose.ui.unit.IntOffset(delta.x.toInt(), delta.y.toInt())
                                toolbarDrag = draggedToolbarPosition(toolbarGeometry.base, next, toolbarGeometry.window, toolbarGeometry.toolbar, margin) - toolbarGeometry.base
                            },
                            onColor = { color = it }, onHighlight = { saveSelection(AnnotationKind.HIGHLIGHT, color) },
                            onUnderline = { style -> saveSelection(AnnotationKind.UNDERLINE, color, underlineStyle = style) },
                            onCopy = {
                                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Passage", selection.text))
                                selected = null; navigator?.clearSelection()
                            },
                            onNote = { editNote(selection.anchor, selection.text) },
                            onSearch = { search = selection.text.take(200); selected = null; navigator?.clearSelection(); panel = "Search" },
                            onShare = {
                                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, selection.text)
                                startActivity(Intent.createChooser(intent, "Share passage"))
                            },
                            onRemove = { overlapping.forEach(model::remove); selected = null },
                            onDismiss = { selected = null; navigator?.clearSelection() },
                        )
                    }
                }
            }

            if (panel == null && chromeVisible && selected == null) Dialog(
                onDismissRequest = { chromeVisible = false; selected = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
                SideEffect { dialogWindow?.setDimAmount(0f); dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
                Box(
                    Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.background(Color.Transparent).pointerInput(chromeVisible, panel, readerTab) {
                        if (chromeVisible) awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var finished = false
                            while (!finished) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    val onPopup = selected != null && selectionBounds?.contains(down.position) == true
                                    val onPage = !onPopup && readerTab != "Contents" && down.position.y >= topChromeHeight && down.position.y <= size.height - bottomChromeHeight
                                    if (gestures.slide && onPage && kotlin.math.abs(dx) > maxOf(48.dp.toPx(), size.width * .12f) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) {
                                        if (pdf != null) page = (page + if ((dx < 0f) != pdfRtl) 1 else -1).coerceIn(0, pdf!!.pageCount - 1)
                                        else if (dx < 0f) nav?.goForward(true) else nav?.goBackward(true)
                                        change.consume()
                                    } else if (onPage && kotlin.math.abs(dx) < 18.dp.toPx() && kotlin.math.abs(dy) < 18.dp.toPx()) {
                                        chromeVisible = false
                                        change.consume()
                                    }
                                    finished = true
                                }
                            }
                        }
                    },
                ) {
            if (chromeVisible) Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topChromeHeight = it.height }.background(MaterialTheme.colorScheme.surface).zIndex(1f)) {
            if (showBookTabs) DocumentTabs(openBooks.map { DocumentTab(it.id, it.title, it.format.uppercase()) }, doc?.id,
                listTitle = "Open books", tagPrefix = "reader-book", listButtonTag = "reader-open-books-list", closeTagPrefix = "reader-close-tab",
                onHideTabs = { showBookTabs = false; settings.edit().putBoolean("showBookTabs", false).apply() },
                onCloseOthers = {
                    val remaining = listOfNotNull(doc?.id)
                    openTabIds = remaining
                    settings.edit().putString("openBookTabs", org.json.JSONArray(remaining).toString()).apply()
                },
                onSelect = ::switchBook, onClose = { id ->
                val remaining = openTabIds - id
                openTabIds = remaining
                settings.edit().putString("openBookTabs", org.json.JSONArray(remaining).toString()).apply()
                if (id == doc?.id) {
                    val next = openBooks.lastOrNull { it.id != id }
                    if (next == null) finish() else switchBook(next.id)
                }
            })
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp),verticalAlignment = Alignment.CenterVertically) {
                InkToolButton(InkIcon.Previous, "Back to library", { finish() }, buttonSize = 44.dp, tint = MaterialTheme.colorScheme.onSurface)
                ReaderAction(Lucide.FileText, "Doc", Modifier.width(52.dp)) { panel = "Document" }
                Spacer(Modifier.weight(1f))
                Row(Modifier.weight(4f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                    // Phones: the everyday actions come first so they are visible without scrolling the row.
                    val compactActions = LocalConfiguration.current.screenWidthDp < 600
                    val secondary: @Composable () -> Unit = {
                        if (pdf != null) ReaderAction(Lucide.ScanText, if (recognizing) "OCR…" else "OCR", Modifier.width(60.dp), enabled = !recognizing) { recognizeScannedPage() }
                        ReaderAction(InkIcon.Hand, "Gestures", Modifier.width(64.dp)) { panel = "Gestures" }
                    }
                    if (!compactActions) secondary()
                    ReaderAction(InkIcon.Search, "Search", Modifier.width(60.dp)) { panel = "Search" }
                    ReaderAction(Lucide.NotebookPen, "Notes", Modifier.width(60.dp)) { if (pdf != null) { tool = "Pen"; drawingToolbar = true; chromeVisible = false } else panel = "Annotations" }
                    ReaderAction(InkIcon.ReaderBookmark, "Bookmark", Modifier.width(68.dp), active = bookmark != null, enabled = currentAnchor() != null) { toggleBookmark() }
                    ReaderAction(InkIcon.More, "More", Modifier.width(56.dp)) { panel = "More" }
                    if (compactActions) secondary()
                }
            }
            HorizontalDivider()
            if (tool == "Select" && pdf != null && selected == null) Text("Drag across text to select a passage. Long-press a word to select it.",Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
            if (chromeVisible && tool == "Pen" && pdf != null) Text("Write directly on the page. Switch to Read to pan or zoom.",Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
            if (chromeVisible) readerNotice?.let { notice -> Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) { Text(notice,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={readerNotice=null}){Text("Dismiss")} } }
            }
            if (chromeVisible && selected == null && readerTab != "Contents") Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = paneHeight * .65f).onSizeChanged { bottomChromeHeight = it.height }.background(MaterialTheme.colorScheme.surface).zIndex(1f)) {
            HorizontalDivider()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (readerTab == "Progress") {
            Text(doc?.title.orEmpty(), Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (pdf != null) "Page ${page + 1} of ${pdf!!.pageCount}" else currentLocator?.title.orEmpty(), Modifier.padding(horizontal = 24.dp, vertical = 4.dp), fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = null; if (pdf != null) page = (page - if (pdfSpread) 2 else 1).coerceAtLeast(0) else nav?.goBackward(true) },
                    enabled = if (pdf != null) page > 0 else nav != null, modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = "Previous page" }) { Text("‹", fontSize = 28.sp) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (pdf != null) {
                        val lastPage = (pdf!!.pageCount - 1).coerceAtLeast(1)
                        Slider(value = scrubPage.coerceIn(0f, lastPage.toFloat()), onValueChange = { scrubPage = it },
                            onValueChangeFinished = { page = scrubPage.roundToInt().coerceIn(0, pdf!!.pageCount - 1) },
                            valueRange = 0f..lastPage.toFloat(), enabled = pdf!!.pageCount > 1, modifier = Modifier.fillMaxWidth().height(34.dp))
                        Text("Page ${page + 1} of ${pdf!!.pageCount}", Modifier.clickable { pageDraft = (page + 1).toString(); jumpDialog = true },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(progress = { epubProgress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text("${(epubProgress * 100).toInt()}% read", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                TextButton(onClick = { selected = null; if (pdf != null) page = (page + if (pdfSpread) 2 else 1).coerceAtMost(pdf!!.pageCount - 1) else nav?.goForward(true) },
                    enabled = if (pdf != null) page < pdf!!.pageCount - 1 else nav != null, modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = "Next page" }) { Text("›", fontSize = 28.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                ReaderAction(Lucide.TableOfContents, "Contents", Modifier.weight(1f)) { readerTab = "Contents" }
                ReaderAction(Lucide.TextSelect, "Select", Modifier.weight(1f), active = tool == "Select", enabled = pdf != null) { tool = "Select"; chromeVisible = false }
                ReaderAction(Lucide.PenLine, "Handwriting", Modifier.weight(1f), enabled = pdf != null) { tool = "Pen"; drawingToolbar = true; chromeVisible = false }
                ReaderAction(InkIcon.Share, "Export notes", Modifier.weight(1f), enabled = !exporting && model.pendingWrites == 0) { export("notes") }
            }
            }
            when (readerTab) {
                "Format" -> {
                    if (pdf != null) {
                        ReaderFormatRow("Page Display") {
                            ReaderAction(InkIcon.Paper, "Single-page", Modifier.width(90.dp), active = !pdfScroll && !pdfSpread) { pdfScroll = false; pdfSpread = false }
                            ReaderAction(InkIcon.ReaderScroll, "Scrolling View", Modifier.width(100.dp), active = pdfScroll) { pdfScroll = true; pdfSpread = false; pdfZoom = 1f }
                            ReaderAction(InkIcon.ReaderLtr, "LTR", Modifier.width(72.dp), active = pdfSpread && !pdfRtl) { pdfRtl = false; pdfSpread = true; pdfScroll = false; pdfZoom = 1f }
                            ReaderAction(InkIcon.ReaderRtl, "RTL", Modifier.width(72.dp), active = pdfSpread && pdfRtl) { pdfRtl = true; pdfSpread = true; pdfScroll = false; pdfZoom = 1f }
                        }
                        ReaderFormatRow("Zoom") {
                            ReaderAction(InkIcon.ZoomIn, "Zoom In", Modifier.width(90.dp)) { pdfZoom = (pdfZoom + .25f).coerceAtMost(4f) }
                            ReaderAction(InkIcon.ZoomOut, "Zoom Out", Modifier.width(90.dp)) { pdfZoom = (pdfZoom - .25f).coerceAtLeast(1f) }
                            ReaderAction(InkIcon.MarqueeZoom, "Marquee Zoom", Modifier.width(110.dp)) { tool = "MarqueeZoom"; chromeVisible = false; readerNotice = "Drag a rectangle to zoom into that area." }
                            if (pdfZoom != 1f) ReaderAction(InkIcon.Rotate, "Reset Zoom", Modifier.width(90.dp)) { pdfZoom = 1f }
                        }
                        ReaderFormatRow("Crop") {
                            if (cropRect == null) {
                                ReaderAction(InkIcon.Expand, "Fit Screen", Modifier.width(90.dp), active = pdfContentFit && !pdfFitWidth) { pdfContentFit = true; pdfFitWidth = false; pdfZoom = 1f }
                                ReaderAction(InkIcon.FitWidth, "Fit Width", Modifier.width(90.dp), active = pdfContentFit && pdfFitWidth) { pdfContentFit = true; pdfFitWidth = true; pdfZoom = 1f }
                            }
                            ReaderAction(InkIcon.Crop, "Marquee Crop", Modifier.width(110.dp)) { pendingCrop = ""; tool = "MarqueeCrop"; pdfScroll = false; chromeVisible = false }
                            ReaderAction(InkIcon.Rotate, "Reset", Modifier.width(72.dp)) { pdfCrop = ""; pdfContentFit = false; pdfFitWidth = false; pdfZoom = 1f; pdfMargin = 0f }
                        }
                        ReaderSettingRow("Margin", pdfMargin, 0f..16f) { pdfMargin = it.roundToInt().toFloat() }
                        ReaderSettingRow("Text Enhancement", pdfEnhancement, 0f..17f) { pdfEnhancement = it.roundToInt().toFloat() }
                    } else {
                        ReaderSettingRow("Text size", (fontScale * 100).toFloat(), 70f..200f) { fontScale = it / 100.0; applyPreferences() }
                        ReaderSettingRow("Line spacing", (lineHeight * 100).toFloat(), 120f..220f) { lineHeight = it / 100.0; applyPreferences() }
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text("Continuous scrolling", Modifier.weight(1f)); Switch(scroll, { scroll = it; applyPreferences() }) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { listOf("Light", "Sepia", "Dark").forEach { name ->
                        ReaderAction(InkIcon.Paper, name, Modifier.weight(1f), active = themeName == name) { themeName = name; applyPreferences() }
                    } }
                }
                "Contrast" -> {
                    if (pdf != null) {
                        ReaderContrastRow("Embolden", pdfEmbolden, 5, "bold") { pdfEmbolden = it }
                        ReaderContrastRow("Sharpen Img", pdfSharpen, 5, "sharpen") { pdfSharpen = it }
                        ReaderContrastRow("Watermark Bleaching", pdfBleach, 100, "bleach") { pdfBleach = it }
                        ReaderContrastRow("Dark Color Enhancement", pdfDarken, 100, "dark") { pdfDarken = it }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Image Smoothing", Modifier.weight(1f), fontSize = 14.sp)
                            Switch(pdfSmoothing, { pdfSmoothing = it }, modifier = Modifier.testTag("reader-image-smoothing").semantics { contentDescription = "Image Smoothing" })
                        }
                        TextButton(onClick = {
                            pdfEmbolden = 0; pdfSharpen = 0; pdfBleach = 0; pdfDarken = 0; pdfSmoothing = true
                            pdfContrast = 1f; pdfBrightness = 0f; pdfEnhancement = 0f
                        }, Modifier.align(Alignment.End).padding(end = 16.dp).testTag("reader-reset-contrast")) { Text("Reset") }
                    } else {
                        Text("Reading theme", Modifier.padding(20.dp), style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { listOf("Light", "Sepia", "Dark").forEach { name -> ReaderAction(InkIcon.Flip, name, Modifier.weight(1f), active = themeName == name) { themeName = name; applyPreferences() } } }
                    }
                }
                "Navigation" -> Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    ReaderAction(InkIcon.Pages, "Contents", Modifier.weight(1f)) { readerTab = "Contents" }
                    ReaderAction(InkIcon.Search, "Search", Modifier.weight(1f)) { panel = "Search" }
                    ReaderAction(InkIcon.Save, "Bookmarks", Modifier.weight(1f)) { contentsTab = "Bookmarks"; readerTab = "Contents" }
                    ReaderAction(InkIcon.Redo, "Go to page", Modifier.weight(1f), enabled = pdf != null) { pageDraft = (page + 1).toString(); jumpDialog = true }
                }
                "Split View" -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        ReaderAction(InkIcon.SplitCurrent, "Current Doc", Modifier.width(100.dp), active = studyMode && splitContent == "Current Doc", enabled = doc != null && !engineLoading) {
                            splitContent = "Current Doc"; splitDocumentId = doc?.id; splitStartPage = page; studyMode = true; chromeVisible = false
                        }
                        ReaderAction(InkIcon.SplitAnother, "Another Doc", Modifier.width(100.dp), active = studyMode && splitContent == "Another Doc") { panel = "Split document" }
                        ReaderAction(InkIcon.SplitHandwriting, "Handwritten Notes", Modifier.width(130.dp), active = studyMode && splitContent == "Handwritten Notes", enabled = !engineLoading) { chromeVisible = false; openStudyMode() }
                        ReaderAction(InkIcon.SplitText, "Text Notes", Modifier.width(100.dp), active = studyMode && splitContent == "Text Notes", enabled = doc != null) { splitContent = "Text Notes"; studyMode = true; chromeVisible = false }
                    }
                    }
                    ReaderFormatRow("Split screen style") {
                        ReaderAction(InkIcon.SplitHorizontal, "Horizontal", Modifier.width(110.dp), active = splitHorizontal) { splitHorizontal = true }
                        ReaderAction(InkIcon.SplitVertical, "Vertical", Modifier.width(110.dp), active = !splitHorizontal) { splitHorizontal = false }
                    }
                    ReaderFormatRow("Landscape orientation") {
                        listOf("Auto" to 0, "Landscape left" to 1, "Landscape right" to 2).forEach { (label, value) ->
                            ReaderAction(if (value == 2) InkIcon.LandscapeRight else InkIcon.LandscapeLeft, label, Modifier.width(110.dp), active = landscapeDirection == value, enabled = value == 0 || supportsLandscapeLock()) {
                                landscapeDirection = value
                                settings.edit().putInt("readerLandscape", value).apply()
                                requestedOrientation = readerOrientation(value)
                            }
                        }
                    }
                    if (!supportsLandscapeLock()) Text("Rotate your tablet to use landscape. The reader follows the screen orientation.",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                    if (studyMode) TextButton(onClick = { studyMode = false }, Modifier.align(Alignment.End).padding(end = 16.dp)) { Text("Close split view") }
                }
            }
            }
            ReaderTabBar(readerTab) { readerTab = it }
            }
            if (chromeVisible && selected == null && readerTab == "Contents") {
                Box(Modifier.fillMaxSize().zIndex(2f).clickable { readerTab = "Progress"; chromeVisible = false }
                    .semantics { contentDescription = "Close contents" }.testTag("reader-contents-backdrop"))
                Surface(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(if (readerWidth >= 600.dp) .68f else .9f).zIndex(3f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Contents", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            InkToolButton(InkIcon.Close, "Close contents", { readerTab = "Progress" }, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider()
                        LazyColumn(Modifier.weight(1f)) {
                            if (contentsTab == "TOC") {
                                if (outline.isNotEmpty()) {
                                    val rows = visibleReaderContents(outline, expandedContents.toSet())
                                    items(rows, key = { it.entry.id }) { row ->
                                        val entry = row.entry
                                        val active = activeChapter?.id == entry.id
                                        Row(Modifier.fillMaxWidth().background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            .padding(start = (12 + row.depth.coerceAtMost(6) * 16).dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (entry.children.isNotEmpty()) IconButton(onClick = {
                                                expandedContents = ArrayList(if (entry.id in expandedContents) expandedContents - entry.id else expandedContents + entry.id)
                                            }, modifier = Modifier.testTag("reader-toc-toggle-${entry.id}").semantics { contentDescription = "${if (entry.id in expandedContents) "Collapse" else "Expand"} ${entry.title}" }) {
                                                Text(if (entry.id in expandedContents) "⌄" else "›", fontSize = 24.sp)
                                            } else Spacer(Modifier.width(12.dp))
                                            Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable {
                                                if (pdf != null && entry.pageIndex != null) { page = requireNotNull(entry.pageIndex); chromeVisible = false }
                                                else if (pdf == null) epubContents.second[entry.id]?.let { nav?.go(it); chromeVisible = false }
                                                else expandedContents = ArrayList(if (entry.id in expandedContents) expandedContents - entry.id else expandedContents + entry.id)
                                            }.padding(vertical = 12.dp).testTag("reader-toc-${entry.id}"), verticalAlignment = Alignment.CenterVertically) {
                                                Text(entry.title, Modifier.weight(1f), fontWeight = if (active || entry.children.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                                entry.pageIndex?.let { Text("${it + 1}", Modifier.padding(start = 12.dp), fontSize = 12.sp) }
                                            }
                                        }
                                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                                    }
                                } else if (pdf != null) {
                                    item { Text("This PDF has no chapter outline. Browse by page.", Modifier.padding(20.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    items(pdf!!.pageCount) { index ->
                                        Row(Modifier.fillMaxWidth().clickable { page = index; chromeVisible = false }.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Page ${index + 1}", Modifier.weight(1f), fontWeight = if (page == index) FontWeight.SemiBold else FontWeight.Normal)
                                            Text("${index + 1}", fontSize = 13.sp)
                                        }; HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                                    }
                                } else item { Text("No table of contents available.", Modifier.padding(20.dp)) }
                            } else {
                                val marks = model.annotations.filter { when (contentsTab) { "Bookmarks" -> it.kind == AnnotationKind.BOOKMARK; "Handwriting" -> it.kind == AnnotationKind.INK; else -> it.kind != AnnotationKind.BOOKMARK && it.kind != AnnotationKind.INK } }
                                if (marks.isEmpty()) item { Text("No ${contentsTab.lowercase()} yet", Modifier.padding(24.dp), fontSize = 14.sp) }
                                items(marks, key = { it.id }) { mark ->
                                    Column(Modifier.fillMaxWidth().clickable { jump(mark.anchor); chromeVisible = false }.padding(20.dp)) {
                                        Text(mark.note.ifBlank { mark.quote.ifBlank { if (pdf != null) "Page ${JSONObject(mark.anchor).optInt("page") + 1}" else "Saved location" } }, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        TextButton(onClick = { model.remove(mark) }) { Text("Delete") }
                                    }; HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth()) { listOf("TOC" to InkIcon.Pages, "Bookmarks" to InkIcon.ReaderBookmark, "Annotations" to InkIcon.More, "Handwriting" to InkIcon.ReaderHandwriting).forEach { (name, icon) ->
                            ReaderAction(icon, name, Modifier.weight(1f).padding(vertical = 6.dp), active = contentsTab == name) { contentsTab = name }
                        } }
                    }
                }
            }
                }
            }
            } }
            if (studyMode && (splitContent != "Handwritten Notes" || studyNotebook != null)) {
                val notePane: @Composable (Modifier) -> Unit = { paneModifier ->
                    Column(paneModifier) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (splitContent in listOf("Current Doc", "Another Doc")) model.documents.firstOrNull { it.id == splitDocumentId }?.title ?: doc?.title.orEmpty() else splitContent,
                            Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        InkToolButton(InkIcon.Close, "Close split view", { studyMode = false }, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider()
                    when (splitContent) {
                        "Current Doc", "Another Doc" -> {
                            val second = model.documents.firstOrNull { it.id == splitDocumentId } ?: doc
                            second?.let { key(it.id) { ReaderComparisonPane(it, splitStartPage, supportFragmentManager, Modifier.weight(1f).fillMaxWidth()) } }
                        }
                        "Text Notes" -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                            OutlinedTextField(splitNoteDraft, onValueChange = { value ->
                                splitNoteDraft = value.take(100_000)
                                settings.edit().putString("split-note-draft.${doc?.id}", splitNoteDraft).apply()
                            }, label = { Text("Write a note") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                            TextButton(enabled = splitNoteDraft.isNotBlank() && currentAnchor() != null && model.pendingWrites == 0, onClick = {
                                val anchor = currentAnchor()
                                if (anchor != null && doc != null) {
                                    val draft = splitNoteDraft
                                    val id = java.util.UUID.randomUUID().toString()
                                    model.save(ReaderAnnotation(id, doc.id, AnnotationKind.NOTE, anchor, note = draft, createdAt = System.currentTimeMillis()))
                                    scope.launch {
                                        snapshotFlow { model.pendingWrites }.first { it == 0 }
                                        if (model.annotations.any { it.id == id } && splitNoteDraft == draft) {
                                            splitNoteDraft = ""; settings.edit().remove("split-note-draft.${doc.id}").apply()
                                        }
                                    }
                                }
                            }) { Text("Save note at current location") }
                            model.annotations.filter { it.kind == AnnotationKind.NOTE }.forEach { note ->
                                HorizontalDivider()
                                Text(note.note, Modifier.fillMaxWidth().clickable { jump(note.anchor) }.padding(vertical = 12.dp))
                                TextButton(onClick = { editNote(note.anchor, note.quote, note) }) { Text("Edit note") }
                            }
                        }
                        else -> NotebookEditor(
                        notebook=studyNotebook!!,
                        repository=notebookRepository,
                        onBack={studyMode=false},
                        onNavigateNotebook={id, pageId ->
                            notebookRepository.notebooks.firstOrNull { it.id == id && it.deletedAt == 0L }?.let { target ->
                                notebookRepository.markOpened(id, pageId)
                                studyNotebookId = id
                            }
                        },
                        onNotebookChange={changed -> notebookRepository.saveNotes(notebookRepository.notebooks.map { if(it.id==changed.id) changed else it })},
                        isSaving=notebookRepository.isSaving,
                        saveError=notebookRepository.saveError,
                        backLabel="Back to reading",
                    )
                    }
                    }
                }
                if (splitHorizontal) Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { ReaderPane(Modifier.fillMaxSize()) }
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) { notePane(Modifier.fillMaxSize()) }
                } else Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { ReaderPane(Modifier.fillMaxSize()) }
                    HorizontalDivider()
                    Box(Modifier.weight(1f).fillMaxWidth()) { notePane(Modifier.fillMaxSize()) }
                }
            } else ReaderPane(Modifier.fillMaxSize())
        } }
        panel?.let { active -> ModalBottomSheet(onDismissRequest={searchJob?.cancel();panel=null}, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = active == "Gestures")) {
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=24.dp).heightIn(max=600.dp)) {
                Text(active,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(bottom=16.dp))
                when(active) {
                    "Split document" -> LazyColumn {
                        val choices = model.documents.filter { it.deletedAt == 0L && it.id != doc?.id }
                        if (choices.isEmpty()) item { Text("Import another PDF or EPUB from the library first.", Modifier.padding(vertical = 20.dp)) }
                        items(choices, key = { it.id }) { book ->
                            Text(book.title, Modifier.fillMaxWidth().clickable {
                                splitDocumentId = book.id; splitStartPage = book.locator.takeIf { it.isNotBlank() && book.format == "pdf" }?.let { JSONObject(it).optInt("page") } ?: 0
                                splitContent = "Another Doc"; studyMode = true; panel = null; chromeVisible = false
                            }.padding(vertical = 18.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            HorizontalDivider()
                        }
                    }
                    "Document" -> {
                        Text(doc?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("${doc?.format?.uppercase().orEmpty()} · ${if (pdf != null) "${pdf!!.pageCount} pages" else "EPUB book"}", Modifier.padding(vertical = 12.dp))
                        Text(if (model.pendingWrites > 0) "Saving…" else "Saved on this device", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { finish() }) { Text("Back to library") }
                    }
                    "Gestures" -> {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        ReaderGestureToggle("Stylus for writing", gestures.stylusWriting, pdf != null) { gestures = gestures.copy(stylusWriting = it) }
                        ReaderGestureToggle("Click / Long Press", gestures.clickLongPress) { gestures = gestures.copy(clickLongPress = it) }
                        ReaderGestureToggle("Slide / Drag the page", gestures.slide) { gestures = gestures.copy(slide = it) }
                        ReaderGestureToggle("Pinch-to-zoom", gestures.pinch, pdf != null) { gestures = gestures.copy(pinch = it) }
                        ReaderGestureToggle("System Bar Gestures", gestures.systemBars) { gestures = gestures.copy(systemBars = it) }
                        ReaderGestureToggle("Bottom Gestures", gestures.bottom) { gestures = gestures.copy(bottom = it) }
                        ReaderGestureToggle("Side Gestures", gestures.sides) { gestures = gestures.copy(sides = it) }
                        ReaderGestureToggle("Back Button", gestures.back) { gestures = gestures.copy(back = it) }
                        Text("Center tap always opens controls. Bottom swipe up opens controls; side taps turn pages. Disabling System Bar Gestures keeps system bars visible. Disabling Back opens these settings instead of leaving the reader.", Modifier.padding(vertical = 12.dp), fontSize = 12.sp)
                        ReaderGestureToggle("Show reading footer", showProgress) { showProgress = it }
                        }
                    }
                    "More" -> {
                        ReaderGestureToggle("Show book tabs", showBookTabs) { showBookTabs = it; settings.edit().putBoolean("showBookTabs", it).apply() }
                        TextButton(onClick = { panel = "Contents" }) { Text("Contents and bookmarks") }
                        TextButton(onClick = { panel = "Search" }) { Text("Search this book") }
                        TextButton(onClick={panel="Annotations"}) { Text("Highlights, notes & bookmarks") }
                        if (pdf != null) TextButton(onClick = ::recognizeScannedPage, enabled = !recognizing) { Text(if (recognizing) "Recognizing text…" else "Recognize scanned page") }
                        TextButton(onClick={currentAnchor()?.let { editNote(it) };panel=null}) { Text("Add a note at this location") }
                        TextButton(onClick={export("notes");panel=null},enabled=!exporting&&model.pendingWrites==0) { Text("Export reading notes (.md)") }
                        if(pdf!=null) TextButton(onClick={export("pdf");panel=null},enabled=!exporting&&model.pendingWrites==0) { Text("Export annotated PDF") }
                        Text("Your original file stays unchanged. Highlights, notes and bookmarks save automatically.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=16.dp))
                    }
                    "Appearance" -> {
                        Text("Text size · ${(fontScale*100).toInt()}%")
                        Slider(value=fontScale.toFloat(),onValueChange={fontScale=it.toDouble();applyPreferences()},valueRange=.7f..2f)
                        Text("Line spacing")
                        Slider(value=lineHeight.toFloat(),onValueChange={lineHeight=it.toDouble();applyPreferences()},valueRange=1.2f..2.2f)
                        Row { listOf("Light","Sepia","Dark").forEach { name -> FilterChip(selected=themeName==name,onClick={themeName=name;applyPreferences()},label={Text(name)},modifier=Modifier.padding(end=8.dp)) } }
                        Row(verticalAlignment=Alignment.CenterVertically) { Text("Continuous scrolling",Modifier.weight(1f)); Switch(checked=scroll,onCheckedChange={scroll=it;applyPreferences()}) }
                    }
                    "Annotations" -> {
                        if(model.annotations.isEmpty()) Text("Select text to highlight it, write with Pen, or add a note from the menu.")
                        LazyColumn { items(model.annotations,key={it.id}) { a ->
                            Column(Modifier.fillMaxWidth().clickable { jump(a.anchor); panel=null }.padding(vertical=12.dp)) {
                                Text(a.kind.name.lowercase().replaceFirstChar { it.uppercase() },style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                                if(a.quote.isNotBlank()) Text(a.quote,maxLines=3,overflow=TextOverflow.Ellipsis)
                                if(a.note.isNotBlank()) Text(a.note,style=MaterialTheme.typography.bodyMedium)
                                Row { TextButton(onClick={editNote(a.anchor,a.quote,a)}) { Text("Edit note") }; TextButton(onClick={model.remove(a)}) { Text("Delete") } }
                            }; HorizontalDivider()
                        } }
                    }
                    "Contents" -> {
                        if(pdf!=null) {
                            Text("Go to a page",style=MaterialTheme.typography.bodyMedium)
                            TextButton(onClick={pageDraft=(page+1).toString();jumpDialog=true;panel=null}) { Text("Page ${page+1} of ${pdf!!.pageCount}") }
                        } else {
                            fun flatten(links: List<Link>,depth: Int=0): List<Pair<Link,Int>> = links.flatMap { listOf(it to depth)+flatten(it.children,depth+1) }
                            val contents=flatten(publication?.tableOfContents.orEmpty().ifEmpty { publication?.readingOrder.orEmpty() })
                            LazyColumn { items(contents.size) { index -> val (link,depth)=contents[index]
                                Text(link.title ?: "Chapter ${index+1}",Modifier.fillMaxWidth().clickable { nav?.go(link);panel=null }.padding(start=(depth*12).dp,top=14.dp,bottom=14.dp))
                            } }
                        }
                    }
                    "Search" -> {
                        fun runSearch() {
                            if(search.trim().length<2) return
                            searchJob?.cancel();results=emptyList();searching=true
                            searchJob=scope.launch {
                                try {
                                    val query=search.trim();require(query.length>=2) { "Enter at least two characters" }
                                    if(pdf!=null) {
                                        val session=pdf!!
                                        for(index in 0 until session.pageCount) {
                                            ensureActive(); val found=withContext(Dispatchers.IO) { session.searchPage(index,query) }; results=results+found
                                            if(results.size>=300)break
                                        }
                                    } else publication?.search(query)?.let { iterator ->
                                        try {
                                        while(results.size<300) {
                                            ensureActive(); val batch=iterator.next().getOrElse { throw IllegalStateException(it.message) } ?: break
                                            results=results+batch.locators.map { ReaderSearchResult((it.text.before.orEmpty()+it.text.highlight.orEmpty()+it.text.after.orEmpty()).collapseWhitespace().ifBlank { it.title ?: "Match" },it.toJSON().toString()) }
                                        }
                                        } finally { iterator.close() }
                                    }
                                } catch(cancelled:CancellationException){throw cancelled}
                                catch(error:Exception){model.error=error.message}
                                finally{searching=false}
                            }
                        }
                        OutlinedTextField(value=search,onValueChange={search=it},label={Text("Search this document")},singleLine=true,
                            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={ runSearch() }),
                            modifier=Modifier.fillMaxWidth())
                        TextButton(onClick={ runSearch() },enabled=search.trim().length>=2) { Text(if(searching) "Restart search" else "Find") }
                        if(searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("${results.size} matches${if(results.size>=300) " (first 300)" else ""}",style=MaterialTheme.typography.labelSmall)
                        LazyColumn { items(results.size) { index -> val result=results[index]; Text(result.label,Modifier.fillMaxWidth().clickable { jump(result.anchor,result.quote);searchJob?.cancel();panel=null }.padding(vertical=14.dp));HorizontalDivider() } }
                    }
                }
            }
        } }
        noteAnchor?.let { anchor -> AlertDialog(onDismissRequest={noteAnchor=null},title={Text(if(editingId==null) "Add reading note" else "Edit note")},text={Column {
            if(noteQuote.isNotBlank())Text(noteQuote,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(bottom=12.dp))
            OutlinedTextField(value=noteDraft,onValueChange={noteDraft=it},label={Text("Your note")},minLines=3,maxLines=8)
        }},confirmButton={TextButton(onClick={
            val existing=model.annotations.firstOrNull { it.id==editingId }
            if(existing!=null)model.save(existing.copy(note=noteDraft,updatedAt=System.currentTimeMillis()))
            else model.annotate(if(noteQuote.isBlank())AnnotationKind.NOTE else AnnotationKind.HIGHLIGHT,anchor,noteQuote,noteDraft,color)
            noteAnchor=null;selected=null;navigator?.clearSelection()
        },enabled=noteDraft.isNotBlank()){Text("Save note")}},dismissButton={TextButton(onClick={noteAnchor=null}){Text("Cancel")}}) }
        editingMark?.let { mark -> ReaderMarkEditor(mark, markKind, color,
            onKind = { markKind = it }, onColor = { color = it },
            onSave = { model.save(mark.copy(kind = markKind, color = color, updatedAt = System.currentTimeMillis())); editingMark = null },
            onDelete = { model.remove(mark); editingMark = null }, onCancel = { editingMark = null }) }
        if(jumpDialog)AlertDialog(onDismissRequest={jumpDialog=false},title={Text("Go to page")},text={OutlinedTextField(value=pageDraft,onValueChange={pageDraft=it.filter(Char::isDigit)},label={Text("1–${pdf?.pageCount}")},singleLine=true)},confirmButton={TextButton(onClick={page=pageDraft.toInt()-1;selected=null;jumpDialog=false},enabled=pageDraft.toIntOrNull()?.let { it in 1..(pdf?.pageCount?:0) }==true){Text("Go")}},dismissButton={TextButton(onClick={jumpDialog=false}){Text("Cancel")}})
        model.error?.let { message -> AlertDialog(onDismissRequest={model.error=null},title={Text("Reader")},text={Text(message)},confirmButton={TextButton(onClick={model.error=null}){Text("OK")}}) }
        if(exporting)BasicAlertDialog(onDismissRequest={}) { Surface { Row(Modifier.padding(24.dp),verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator();Text("Preparing export…",Modifier.padding(start=16.dp)) } } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selected?.let { outState.putString("selectionAnchor", it.anchor); outState.putString("selectionText", it.text) }
    }
    private fun saveReadingPosition() {
        if (!::model.isInitialized) return
        pdf?.let { model.position(JSONObject().put("page", page).toString(), if (it.pageCount <= 1) 1.0 else page.toDouble() / (it.pageCount - 1)) }
        navigator?.currentLocator?.value?.let { locator -> model.position(locator.toJSON().toString(), locator.locations.totalProgression ?: locator.locations.progression ?: 0.0) }
    }
    override fun onPause() { saveReadingPosition(); super.onPause() }
    override fun onDestroy() {
        val session=pdf; val book=publication
        super.onDestroy()
        ReaderWrites.scope.launch { session?.close();book?.close() }
    }
    companion object { private const val READER_CONTAINER_ID=0x4b4e0001 }
}


