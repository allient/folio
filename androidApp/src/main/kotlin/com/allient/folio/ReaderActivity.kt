@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.allient.folio

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
    private var publication: Publication? by mutableStateOf(null)
    private var navigator: EpubNavigatorFragment? by mutableStateOf(null)
    private var selected: PdfSelection? by mutableStateOf(null)
    private var page by mutableIntStateOf(0)
    private var engineError: String? by mutableStateOf(null)
    private var engineLoading by mutableStateOf(true)
    private var epubProgress by mutableDoubleStateOf(0.0)
    private val settings by lazy { getSharedPreferences("reader-preferences", MODE_PRIVATE) }
    private var fontScale by mutableDoubleStateOf(1.0)
    private var themeName by mutableStateOf("Light")
    private var scroll by mutableStateOf(false)
    private var lineHeight by mutableDoubleStateOf(1.5)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Native navigators are rebuilt from persisted locators, never restored without a Publication.
        super.onCreate(null)
        enableEdgeToEdge()
        setReaderImmersive()
        fontScale = settings.getFloat("fontScale", 1f).toDouble()
        themeName = settings.getString("theme", "Light") ?: "Light"
        scroll = settings.getBoolean("scroll", false)
        lineHeight = settings.getFloat("lineHeight", 1.5f).toDouble()
        model = ViewModelProvider(this)[ReaderViewModel::class.java]
        notebookRepository = ViewModelProvider(this)[NotebookViewModel::class.java]
        lifecycleScope.launch {
            try {
                model.load(requireNotNull(intent.getStringExtra("documentId")))
                val document = requireNotNull(model.current) { "This document is no longer in your library" }
                check(File(document.path).isFile) { "The original file is not on this device. Re-import the same PDF or EPUB to continue; your saved notes and reading position will reconnect automatically." }
                if (document.format == "pdf") {
                    pdf = withContext(Dispatchers.IO) { PdfReaderSession(File(document.path)) }
                    page = document.locator.takeIf { it.isNotBlank() }?.let { JSONObject(it).optInt("page") }?.coerceIn(0, pdf!!.pageCount - 1) ?: 0
                } else publication = withContext(Dispatchers.IO) { EpubReaderEngine.open(this@ReaderActivity, File(document.path)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { engineError = error.message ?: "Could not open document" }
            finally { engineLoading = false }
        }
        setContent { MaterialTheme(colorScheme = if (themeName == "Dark") darkColorScheme(primary = Color.White, onPrimary = Color.Black, secondary = Color.LightGray, secondaryContainer = Color.DarkGray) else lightColorScheme(primary = Color(0xFF252525), secondary = Color(0xFF555555), secondaryContainer = Color(0xFFE5E5E5), surface = Color.White, onSurface = Color(0xFF252525), surfaceContainerLow = Color.White)) { ReaderContent() } }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setReaderImmersive()
    }
    private fun setReaderImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun preferences() = EpubPreferences(fontSize = fontScale, theme = when (themeName) { "Dark" -> Theme.DARK; "Sepia" -> Theme.SEPIA; else -> Theme.LIGHT }, scroll = scroll, lineHeight = lineHeight)
    private fun applyPreferences() {
        navigator?.submitPreferences(preferences())
        settings.edit().putFloat("fontScale",fontScale.toFloat()).putString("theme",themeName).putBoolean("scroll",scroll).putFloat("lineHeight", lineHeight.toFloat()).apply()
    }
    private fun currentAnchor(): String? = if (pdf != null) JSONObject().put("page",page).toString() else navigator?.currentLocator?.value?.toJSON()?.toString()
    private fun jump(anchor: String) {
        selected = null
        if (pdf != null) { page = JSONObject(anchor).getInt("page").coerceIn(0,pdf!!.pageCount - 1); if (JSONObject(anchor).has("rects")) selected = PdfSelection(anchor, "Search result") }
        else Locator.fromJSON(JSONObject(anchor))?.let { navigator?.go(it) }
    }
    private fun saveSelection(kind: AnnotationKind, color: Int, note: String = "") {
        val selection = selected ?: return
        model.annotate(kind, selection.anchor, selection.text, note, color)
        selected = null; navigator?.clearSelection()
    }
    @Composable private fun ReaderContent() {
        val scope = rememberCoroutineScope()
        val doc = model.current
        var panel by rememberSaveable { mutableStateOf<String?>(null) }
        var chromeVisible by rememberSaveable { mutableStateOf(false) }
        var showProgress by rememberSaveable { mutableStateOf(true) }
        var readerTab by rememberSaveable { mutableStateOf("Progress") }
        var contentsTab by rememberSaveable { mutableStateOf("TOC") }
        var drawingToolbar by rememberSaveable { mutableStateOf(false) }
        var drawingPopup by rememberSaveable { mutableStateOf<String?>(null) }
        var splitHorizontal by rememberSaveable { mutableStateOf(true) }
        var pdfZoom by rememberSaveable { mutableFloatStateOf(1f) }
        var pdfMargin by rememberSaveable { mutableFloatStateOf(0f) }
        var pdfContrast by rememberSaveable { mutableFloatStateOf(1f) }
        var pdfBrightness by rememberSaveable { mutableFloatStateOf(0f) }
        val redoInk = remember { mutableStateListOf<ReaderAnnotation>() }
        var bottomChromeHeight by remember { mutableIntStateOf(0) }
        var topChromeHeight by remember { mutableIntStateOf(0) }
        var tool by rememberSaveable { mutableStateOf("Read") }
        var color by rememberSaveable { mutableIntStateOf(0xFFFFD85A.toInt()) }
        var penColor by rememberSaveable { mutableIntStateOf(0xFF303030.toInt()) }
        var penWidth by rememberSaveable { mutableFloatStateOf(.003f) }
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
        var studyMode by rememberSaveable { mutableStateOf(false) }
        var studyNotebookId by rememberSaveable { mutableStateOf<String?>(null) }
        val nav = navigator
        val studyNotebook = studyNotebookId?.let { id -> notebookRepository.notebooks.firstOrNull { it.id == id && it.deletedAt == 0L } }
        BackHandler(enabled=studyMode) { studyMode=false }
        BackHandler(enabled = drawingToolbar && !studyMode) { drawingToolbar = false; drawingPopup = null; tool = "Read" }
        fun handlePageTap(x: Float, y: Float, width: Float, height: Float): Boolean {
            if (drawingToolbar) return false
            if (chromeVisible) {
                // The native PDF/EPUB view fills the screen beneath the Compose controls.
                // Leave taps in the overlay bands for those controls; only a tap on the
                // actual reading area dismisses the chrome.
                val overlayTop = 88f * resources.displayMetrics.density
                val overlayBottom = 176f * resources.displayMetrics.density
                if (y < overlayTop || y > height - overlayBottom) return false
                chromeVisible = false
                return true
            }
            if (x in width * .28f..width * .72f && y in height * .28f..height * .72f) {
                chromeVisible = true
                return true
            }
            return false
        }
        fun openStudyMode() {
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
            if (doc == null || exporting) return
            exporting = true
            val notes = model.annotations.toList(); val markdown = model.exportNotes()
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(cacheDir,"exports").apply { mkdirs() }
                        if (kind == "pdf") ReaderExport.annotatedPdf(this@ReaderActivity, doc, notes, File(dir,"${doc.contentHash.take(12)}-annotated.pdf"))
                        else File(dir,"${doc.contentHash.take(12)}-notes.md").apply { writeText(markdown) }
                    }
                    NotebookFiles(this@ReaderActivity).shareFile(file, if (kind == "pdf") "application/pdf" else "text/markdown")
                } catch (error: Exception) { if (error is CancellationException) throw error; model.error = error.message ?: "Could not export document" }
                finally { exporting = false }
            }
        }
        fun recognizeScannedPage() {
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
            @Composable fun ReaderPane(modifier: Modifier) { Box(modifier) {
            Box(Modifier.fillMaxSize()) {
            when {
                    engineLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    engineError != null -> Column(Modifier.align(Alignment.Center).padding(28.dp)) { Text("Could not open this document",style=MaterialTheme.typography.titleLarge); Text(engineError.orEmpty(),Modifier.padding(top=12.dp)); TextButton(onClick={finish()}) { Text("Back to library") } }
                    pdf != null -> {
                        val session = pdf!!
                        val records = model.annotations + listOfNotNull(selected?.let { ReaderAnnotation("selection",doc!!.id,AnnotationKind.HIGHLIGHT,it.anchor,color=0xFF76B9EF.toInt(),createdAt=0) })
                        AndroidView(factory={PdfPageReaderView(it)},update={ view ->
                            view.show(session,page,records); view.tool=tool; view.chromeVisible=chromeVisible; view.inkColor=penColor; view.inkWidth=penWidth
                            view.setDisplay(pdfZoom, pdfMargin, pdfContrast, pdfBrightness)
                            view.onErase = { mark -> model.remove(mark); redoInk.clear() }
                            view.onPageTurn = { forward ->
                                if (forward) page = (page + 1).coerceAtMost(session.pageCount - 1)
                                else page = (page - 1).coerceAtLeast(0)
                                selected = null
                            }
                            view.onPageTap = { x, y, width, height -> handlePageTap(x, y, width, height) }
                            view.onAnnotationLongPress = { mark -> editingMark=mark; markKind=mark.kind; color=mark.color; chromeVisible=true }
                            view.onSelection={selection -> selected=selection; if(selection==null) model.error="No selectable text here. This may be a scanned page; OCR is required for text highlights."}
                            view.onInk={model.annotate(AnnotationKind.INK,it,color=penColor); redoInk.clear()}; view.onError={model.error=it}
                        },modifier=Modifier.fillMaxSize().clipToBounds())
                    }
                    publication != null -> AndroidView(factory={ context ->
                        FragmentContainerView(context).apply {
                            id = READER_CONTAINER_ID
                            val callback = object : ActionMode.Callback {
                                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                    menu.add(0,1,1,"Highlight"); menu.add(0,2,2,"Add note"); return true
                                }
                                override fun onPrepareActionMode(mode: ActionMode,menu: Menu)=false
                                override fun onDestroyActionMode(mode: ActionMode) {}
                                override fun onActionItemClicked(mode: ActionMode,item: MenuItem): Boolean {
                                    if(item.itemId !in 1..2) return false
                                    scope.launch {
                                        navigator?.currentSelection()?.let { selection ->
                                            selected=PdfSelection(selection.locator.toJSON().toString(),selection.locator.text.highlight.orEmpty())
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
                    },modifier=Modifier.fillMaxSize().clipToBounds().pointerInput(scroll,nav,chromeVisible) {
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
                                if (!scroll && horizontal >= maxOf(48.dp.toPx(), size.width * .12f) && horizontal > vertical * 1.25f) {
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
            if (!chromeVisible && showProgress && selected == null) {
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (model.pendingWrites > 0) "Saving…" else "${(if (pdf != null) doc?.progress ?: 0.0 else epubProgress).times(100).toInt()}%", fontSize = 11.sp)
                    Text(doc?.title.orEmpty(), Modifier.weight(1f).padding(horizontal = 20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(if (pdf != null) "${page + 1}/${pdf!!.pageCount}" else "${(epubProgress * 100).toInt()}%", fontSize = 11.sp)
                }
            }
            if (drawingToolbar && !chromeVisible && pdf != null) {
                val pageInk = model.annotations.filter { it.kind == AnnotationKind.INK && JSONObject(it.anchor).optInt("page", -1) == page }
                ReaderDrawingToolbar(tool, penWidth, penColor, pageInk.isNotEmpty() && model.pendingWrites == 0, redoInk.isNotEmpty() && model.pendingWrites == 0,
                    onRead = { tool = "Read"; drawingToolbar = false; drawingPopup = null },
                    onPreset = { penWidth = it; tool = "Pen"; drawingPopup = null },
                    onStyle = { drawingPopup = if (drawingPopup == "Pen") null else "Pen" },
                    onErase = { tool = "Eraser"; drawingPopup = if (drawingPopup == "Eraser") null else "Eraser" },
                    onUndo = { pageInk.lastOrNull()?.let { redoInk.add(it); model.remove(it) } },
                    onRedo = { if (redoInk.isNotEmpty()) model.save(redoInk.removeAt(redoInk.lastIndex)) },
                    onBookmark = ::toggleBookmark,
                    onSelect = { tool = "Select"; drawingToolbar = false; drawingPopup = null },
                    onMore = { chromeVisible = true; drawingPopup = null },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).widthIn(max = 720.dp))
                drawingPopup?.let { popup ->
                    Surface(Modifier.align(Alignment.TopCenter).padding(top = 78.dp, start = 16.dp, end = 16.dp).widthIn(max = 480.dp),
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation = 3.dp) {
                        if (popup == "Pen") ReaderPenPopover(penColor, penWidth, { penColor = it; tool = "Pen" }, { penWidth = it; tool = "Pen" }, { drawingPopup = null })
                        else Column {
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
            if (panel == null && (chromeVisible || selected != null)) Dialog(
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
                                    val onPage = readerTab != "Contents" && down.position.y >= topChromeHeight && down.position.y <= size.height - bottomChromeHeight
                                    if (onPage && kotlin.math.abs(dx) > maxOf(48.dp.toPx(), size.width * .12f) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f) {
                                        if (pdf != null) page = (page + if (dx < 0f) 1 else -1).coerceIn(0, pdf!!.pageCount - 1)
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
            if (chromeVisible) Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topChromeHeight = it.height }.zIndex(1f)) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp),verticalAlignment = Alignment.CenterVertically) {
                InkToolButton(InkIcon.Previous, "Back to library", { finish() }, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
                ReaderAction(InkIcon.Library, "Doc", Modifier.width(52.dp)) { panel = "Document" }
                Spacer(Modifier.weight(1f))
                Row(Modifier.weight(4f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                    if (pdf != null) ReaderAction(InkIcon.RectangleSelect, if (recognizing) "OCR…" else "OCR", Modifier.width(60.dp), enabled = !recognizing) { recognizeScannedPage() }
                    ReaderAction(InkIcon.Hand, "Gestures", Modifier.width(64.dp)) { panel = "Gestures" }
                    ReaderAction(InkIcon.Pen, "Notes", Modifier.width(60.dp)) { if (pdf != null) { tool = "Pen"; drawingToolbar = true; chromeVisible = false } else panel = "Annotations" }
                    ReaderAction(InkIcon.Save, "Bookmark", Modifier.width(68.dp), active = bookmark != null, enabled = currentAnchor() != null) { toggleBookmark() }
                    ReaderAction(InkIcon.Search, "Search", Modifier.width(60.dp)) { panel = "Search" }
                    ReaderAction(InkIcon.More, "More", Modifier.width(56.dp)) { panel = "More" }
                }
            }
            HorizontalDivider()
            if (tool == "Select" && pdf != null && selected == null) Text("Drag across text to select a passage. Long-press a line to select it.",Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
            if (chromeVisible && tool == "Pen" && pdf != null) Text("Write directly on the page. Switch to Read to pan or zoom.",Modifier.padding(horizontal=16.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
            if (chromeVisible) readerNotice?.let { notice -> Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) { Text(notice,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={readerNotice=null}){Text("Dismiss")} } }
            }
            selected?.let { selection ->
                Surface(Modifier.align(Alignment.Center).widthIn(max = 620.dp).padding(24.dp).zIndex(2f),shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shadowElevation=3.dp) { Column(Modifier.padding(12.dp)) {
                    Text(selection.text.ifBlank { "Selected passage" },maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                        ReaderColors(color) { color=it }
                        TextButton(onClick={saveSelection(AnnotationKind.HIGHLIGHT,color)}) { Text("Highlight") }
                        TextButton(onClick={saveSelection(AnnotationKind.UNDERLINE,color)}) { Text("Underline") }
                        TextButton(onClick={editNote(selection.anchor,selection.text)}) { Text("Note") }
                        TextButton(onClick={val clip=getSystemService(android.content.ClipboardManager::class.java);clip.setPrimaryClip(android.content.ClipData.newPlainText("Passage",selection.text))}) { Text("Copy") }
                        TextButton(onClick={selected=null;navigator?.clearSelection()}) { Text("Dismiss") }
                    }
                } }
            }
            if (chromeVisible && selected == null && readerTab != "Contents") Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = readerHeight * .65f).onSizeChanged { bottomChromeHeight = it.height }.background(MaterialTheme.colorScheme.surface).zIndex(1f)) {
            HorizontalDivider()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (readerTab == "Progress") {
            Text(doc?.title.orEmpty(), Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (pdf != null) "Page ${page + 1} of ${pdf!!.pageCount}" else nav?.currentLocator?.value?.title.orEmpty(), Modifier.padding(horizontal = 24.dp, vertical = 4.dp), fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = null; if (pdf != null) page = (page - 1).coerceAtLeast(0) else nav?.goBackward(true) },
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
                TextButton(onClick = { selected = null; if (pdf != null) page = (page + 1).coerceAtMost(pdf!!.pageCount - 1) else nav?.goForward(true) },
                    enabled = if (pdf != null) page < pdf!!.pageCount - 1 else nav != null, modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = "Next page" }) { Text("›", fontSize = 28.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                ReaderAction(InkIcon.Pages, "Contents", Modifier.weight(1f)) { readerTab = "Contents" }
                ReaderAction(InkIcon.Text, "Select", Modifier.weight(1f), active = tool == "Select", enabled = pdf != null) { tool = "Select"; chromeVisible = false }
                ReaderAction(InkIcon.Pen, "Handwriting", Modifier.weight(1f), enabled = pdf != null) { tool = "Pen"; drawingToolbar = true; chromeVisible = false }
                ReaderAction(InkIcon.Share, "Export notes", Modifier.weight(1f), enabled = !exporting && model.pendingWrites == 0) { export("notes") }
            }
            }
            when (readerTab) {
                "Format" -> {
                    if (pdf != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Page display", Modifier.weight(1f), fontSize = 14.sp)
                            ReaderAction(InkIcon.Paper, "Single page", Modifier.weight(1f), active = true) { pdfZoom = 1f }
                            ReaderAction(InkIcon.Expand, "Fit screen", Modifier.weight(1f)) { pdfZoom = 1f; pdfMargin = 0f }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        ReaderSettingRow("Zoom", pdfZoom * 100f, 100f..400f) { pdfZoom = it / 100f }
                        ReaderSettingRow("Margin", pdfMargin, 0f..80f) { pdfMargin = it }
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
                        ReaderSettingRow("Contrast", pdfContrast * 100, 50f..200f) { pdfContrast = it / 100 }
                        ReaderSettingRow("Brightness", pdfBrightness, -60f..60f) { pdfBrightness = it }
                        TextButton(onClick = { pdfContrast = 1f; pdfBrightness = 0f }, Modifier.align(Alignment.End).padding(end = 16.dp)) { Text("Reset") }
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
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        ReaderAction(InkIcon.Pen, "Handwritten notes", Modifier.weight(1f), enabled = !engineLoading) { chromeVisible = false; openStudyMode() }
                        ReaderAction(InkIcon.Text, "Text notes", Modifier.weight(1f)) { currentAnchor()?.let { editNote(it) }; chromeVisible = false }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Split screen style", Modifier.weight(1f), fontSize = 14.sp)
                        ReaderAction(InkIcon.Border, "Horizontal", Modifier.weight(1f), active = splitHorizontal) { splitHorizontal = true }
                        ReaderAction(InkIcon.Layers, "Vertical", Modifier.weight(1f), active = !splitHorizontal) { splitHorizontal = false }
                    }
                }
            }
            }
            ReaderTabBar(readerTab) { readerTab = it }
            }
            if (chromeVisible && selected == null && readerTab == "Contents") {
                Surface(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(if (readerWidth >= 600.dp) .68f else .9f).zIndex(3f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        Row(Modifier.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Contents", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            InkToolButton(InkIcon.Close, "Close contents", { readerTab = "Progress" }, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider()
                        LazyColumn(Modifier.weight(1f)) {
                            if (contentsTab == "TOC") {
                                if (pdf != null) items(pdf!!.pageCount) { index ->
                                    Row(Modifier.fillMaxWidth().clickable { page = index; chromeVisible = false }.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Page ${index + 1}", Modifier.weight(1f), fontWeight = if (page == index) FontWeight.SemiBold else FontWeight.Normal)
                                        Text("${index + 1}", fontSize = 13.sp)
                                    }; HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                                } else {
                                    fun flatten(links: List<Link>, depth: Int = 0): List<Pair<Link, Int>> = links.flatMap { listOf(it to depth) + flatten(it.children, depth + 1) }
                                    val links = flatten(publication?.tableOfContents.orEmpty().ifEmpty { publication?.readingOrder.orEmpty() })
                                    items(links.size) { index -> val (link, depth) = links[index]
                                        Text(link.title ?: "Chapter ${index + 1}", Modifier.fillMaxWidth().clickable { nav?.go(link); chromeVisible = false }.padding(start = (20 + depth * 16).dp, end = 20.dp, top = 18.dp, bottom = 18.dp)); HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                                    }
                                }
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
                        Row(Modifier.fillMaxWidth()) { listOf("TOC" to InkIcon.Pages, "Bookmarks" to InkIcon.Save, "Annotations" to InkIcon.More, "Handwriting" to InkIcon.Pen).forEach { (name, icon) ->
                            ReaderAction(icon, name, Modifier.weight(1f).padding(vertical = 6.dp), active = contentsTab == name) { contentsTab = name }
                        } }
                    }
                }
            }
                }
            }
            } }
            if (studyMode && studyNotebook != null) {
                val notePane: @Composable (Modifier) -> Unit = { paneModifier ->
                    NotebookEditor(
                        notebook=studyNotebook,
                        repository=notebookRepository,
                        onBack={studyMode=false},
                        onNavigateNotebook={id, pageId ->
                            notebookRepository.notebooks.firstOrNull { it.id == id && it.deletedAt == 0L }?.let { target ->
                                if (pageId != null && target.pages.any { it.id == pageId })
                                    notebookRepository.saveNotes(notebookRepository.notebooks.map { if (it.id == id) it.copy(lastViewedPageId = pageId) else it })
                                studyNotebookId = id
                            }
                        },
                        onNotebookChange={changed -> notebookRepository.saveNotes(notebookRepository.notebooks.map { if(it.id==changed.id) changed else it })},
                        isSaving=notebookRepository.isSaving,
                        saveError=notebookRepository.saveError,
                        backLabel="Back to reading",
                    )
                }
                if (maxWidth >= 720.dp && splitHorizontal) Row(Modifier.fillMaxSize()) {
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
        panel?.let { active -> ModalBottomSheet(onDismissRequest={searchJob?.cancel();panel=null}) {
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=24.dp).heightIn(max=600.dp)) {
                Text(active,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(bottom=16.dp))
                when(active) {
                    "Document" -> {
                        Text(doc?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("${doc?.format?.uppercase().orEmpty()} · ${if (pdf != null) "${pdf!!.pageCount} pages" else "EPUB book"}", Modifier.padding(vertical = 12.dp))
                        Text(if (model.pendingWrites > 0) "Saving…" else "Saved on this device", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { finish() }) { Text("Back to library") }
                    }
                    "Gestures" -> {
                        Text("Tap the center of the page to show or hide reader controls. Swipe left or right to turn pages.", style = MaterialTheme.typography.bodyMedium)
                        if (pdf != null) Text("Pinch to zoom in Read mode. Use Notes for handwriting and Select for text highlights.", Modifier.padding(top = 16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Show reading footer", Modifier.weight(1f)); Switch(showProgress, { showProgress = it }) }
                        TextButton(onClick = { tool = "Read"; drawingToolbar = false; panel = null; chromeVisible = false }) { Text("Read and navigate") }
                    }
                    "More" -> {
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
                    "Ink" -> {
                        Text("Pencil color", style=MaterialTheme.typography.titleMedium)
                        ReaderInkColors(penColor) { penColor=it }
                        Text("Stroke width", style=MaterialTheme.typography.titleMedium, modifier=Modifier.padding(top=12.dp))
                        Slider(value=penWidth, onValueChange={penWidth=it}, valueRange=.001f.. .012f)
                        Text("${(penWidth * 1000).toInt()} pt · use a stylus or finger to write", style=MaterialTheme.typography.bodySmall)
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
                        OutlinedTextField(value=search,onValueChange={search=it},label={Text("Search this document")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        TextButton(onClick={
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
                                            results=results+batch.locators.map { ReaderSearchResult((it.text.before.orEmpty()+it.text.highlight.orEmpty()+it.text.after.orEmpty()).ifBlank { it.title ?: "Match" },it.toJSON().toString()) }
                                        }
                                        } finally { iterator.close() }
                                    }
                                } catch(cancelled:CancellationException){throw cancelled}
                                catch(error:Exception){model.error=error.message}
                                finally{searching=false}
                            }
                        },enabled=search.trim().length>=2) { Text(if(searching) "Restart search" else "Find") }
                        if(searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("${results.size} matches${if(results.size>=300) " (first 300)" else ""}",style=MaterialTheme.typography.labelSmall)
                        LazyColumn { items(results.size) { index -> val result=results[index]; Text(result.label,Modifier.fillMaxWidth().clickable { jump(result.anchor);searchJob?.cancel();panel=null }.padding(vertical=14.dp));HorizontalDivider() } }
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
        editingMark?.let { mark -> AlertDialog(
            onDismissRequest={editingMark=null}, title={Text("Edit ${mark.kind.name.lowercase()}")},
            text={Column {
                Text("Color", style=MaterialTheme.typography.titleSmall)
                ReaderColors(color) { color=it }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=markKind==AnnotationKind.HIGHLIGHT,onClick={markKind=AnnotationKind.HIGHLIGHT},label={Text("Highlight")})
                    FilterChip(selected=markKind==AnnotationKind.UNDERLINE,onClick={markKind=AnnotationKind.UNDERLINE},label={Text("Underline")})
                }
            }},
            confirmButton={TextButton(onClick={model.save(mark.copy(kind=markKind,color=color,updatedAt=System.currentTimeMillis()));editingMark=null}){Text("Save")}},
            dismissButton={Row {
                TextButton(onClick={model.remove(mark);editingMark=null}){Text("Delete")}
                TextButton(onClick={editingMark=null}){Text("Cancel")}
            }}
        ) }
        if(jumpDialog)AlertDialog(onDismissRequest={jumpDialog=false},title={Text("Go to page")},text={OutlinedTextField(value=pageDraft,onValueChange={pageDraft=it.filter(Char::isDigit)},label={Text("1–${pdf?.pageCount}")},singleLine=true)},confirmButton={TextButton(onClick={page=pageDraft.toInt()-1;selected=null;jumpDialog=false},enabled=pageDraft.toIntOrNull()?.let { it in 1..(pdf?.pageCount?:0) }==true){Text("Go")}},dismissButton={TextButton(onClick={jumpDialog=false}){Text("Cancel")}})
        model.error?.let { message -> AlertDialog(onDismissRequest={model.error=null},title={Text("Reader")},text={Text(message)},confirmButton={TextButton(onClick={model.error=null}){Text("OK")}}) }
        if(exporting)BasicAlertDialog(onDismissRequest={}) { Surface { Row(Modifier.padding(24.dp),verticalAlignment=Alignment.CenterVertically) { CircularProgressIndicator();Text("Preparing export…",Modifier.padding(start=16.dp)) } } }
    }
    override fun onDestroy() {
        val session=pdf; val book=publication
        super.onDestroy()
        ReaderWrites.scope.launch { session?.close();book?.close() }
    }
    companion object { private const val READER_CONTAINER_ID=0x4b4e0001 }
}

@Composable internal fun ReaderColors(selected: Int,onSelect:(Int)->Unit) {
    listOf(0xFFFFD85A.toInt(),0xFF93D9AF.toInt(),0xFF8FC7F4.toInt(),0xFFF0A6BD.toInt()).forEach { color ->
        val label=when(color){0xFFFFD85A.toInt()->"Yellow";0xFF93D9AF.toInt()->"Green";0xFF8FC7F4.toInt()->"Blue";else->"Pink"}
        val chosen=selected==color
        Box(Modifier.padding(4.dp).size(40.dp).semantics { contentDescription="$label highlight"; this.selected=chosen }.clip(CircleShape).background(Color(color)).border(if(selected==color)2.dp else 0.dp,MaterialTheme.colorScheme.onSurface,CircleShape)
            .clickable(onClickLabel="Select ${when(color){0xFFFFD85A.toInt()->"yellow";0xFF93D9AF.toInt()->"green";0xFF8FC7F4.toInt()->"blue";else->"pink"}} highlight"){onSelect(color)})
    }
}

@Composable private fun ReaderInkColors(selected: Int,onSelect:(Int)->Unit) {
    listOf(0xFF242424.toInt(),0xFF315CBB.toInt(),0xFFB63838.toInt(),0xFF16866F.toInt()).forEach { color ->
        val label=when(color){0xFF242424.toInt()->"Black";0xFF315CBB.toInt()->"Blue";0xFFB63838.toInt()->"Red";else->"Green"}
        Box(Modifier.padding(4.dp).size(40.dp).semantics { contentDescription="$label pen color"; this.selected=selected==color }.clip(CircleShape).background(Color(color)).border(if(selected==color)2.dp else 0.dp,MaterialTheme.colorScheme.onSurface,CircleShape).clickable { onSelect(color) })
    }
}
