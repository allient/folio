package com.allient.folio

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * A live board: Excalidraw (excalidraw.com) in a WebView, with Folio's pen filters applied by
 * [PressureFilteringWebView]. Boards are listed in the library's Boards tab; each is saved in
 * [LiveBoardStore] and loaded into the page before its scripts run. Live sessions use Excalidraw's own rooms: start
 * one from Share, send the link, and others join from any browser.
 */
class LiveBoardActivity : ComponentActivity() {
    private lateinit var store: LiveBoardStore
    private lateinit var mapper: PenPressureMapper
    private lateinit var web: PressureFilteringWebView
    private var boardScript: ScriptHandler? = null
    /** The same script, for WebViews without document-start scripts (applied after load, then reload). */
    private var fallbackScript: String? = null
    /** The URL to load once the WebView has a size (a 0×0 viewport collapses Excalidraw's layout). */
    private var pendingUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PenSettings.init(this)
        AppAnalytics.init(this)
        AppAnalytics.screen("live_boards")
        enableEdgeToEdge()
        hideSystemBars()
        store = LiveBoardStore(this)
        mapper = PenPressureMapper(this)
        web = PressureFilteringWebView(this, mapper).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // Excalidraw keeps the scene in localStorage/IndexedDB
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                // Excalidraw pages stay in the board; anything else opens in the browser.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (isExcalidraw(request.url.host.orEmpty())) return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    fallbackScript?.let { script -> view.evaluateJavascript(script) { if (it.contains("applied")) view.reload() } }
                }
            }
        }
        setContent {
            MaterialTheme(typography = folioTypography()) {
                LiveBoards(store, web, mapper, intent.getStringExtra(EXTRA_BOARD_ID), onOpen = ::openBoard, onSave = ::saveBoard, onClose = ::finish,
                    onShareImage = ::shareImage, onShareFile = ::shareFile, onSession = ::openSessionDialog)
            }
        }
    }

    /** Loads [board] (or a room [url]) into the page. The saved scene is written to localStorage before Excalidraw starts. */
    private fun openBoard(board: LiveBoard, url: String) {
        val (elements, appState) = store.scene(board.id)
        val script = boardScript(board.id, elements, appState)
        boardScript?.remove()
        boardScript = if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            WebViewCompat.addDocumentStartJavaScript(web, script, setOf("https://$HOST")) else null
        fallbackScript = if (boardScript == null) script else null
        pendingUrl = url
        if (web.isLaidOut && web.width > 0) loadPending()
    }

    /** Loads the waiting URL; the board's AndroidView calls this once the WebView is laid out. */
    fun loadPending() { pendingUrl?.let { web.loadUrl(it); pendingUrl = null } }

    /** Reads the scene from the page and stores it with a preview. Safe to call often. */
    private fun saveBoard(board: LiveBoard, onDone: () -> Unit) {
        web.evaluateJavascript(READ_SCENE) { result ->
            val scene = parseScene(result)
            if (scene == null || isDestroyed) { onDone(); return@evaluateJavascript }
            capture(.35f) { preview ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { store.save(board.id, scene.first, scene.second, preview) }
                    preview?.recycle()
                    onDone()
                }
            }
        }
    }

    /**
     * What the board shows on screen. Excalidraw draws on a GPU canvas that View.draw can't see, so
     * this copies the window's pixels (PixelCopy, API 26+) and falls back to View.draw on API 25.
     */
    private fun capture(scale: Float, onResult: (Bitmap?) -> Unit) {
        if (web.width <= 0 || web.height <= 0 || !web.isAttachedToWindow) return onResult(null)
        val full = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
        fun finish(ok: Boolean) {
            if (!ok) { full.recycle(); onResult(null); return }
            if (scale == 1f) { onResult(full); return }
            val scaled = Bitmap.createScaledBitmap(full, (web.width * scale).toInt().coerceAtLeast(1), (web.height * scale).toInt().coerceAtLeast(1), true)
            full.recycle()
            onResult(scaled)
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val location = IntArray(2).also { web.getLocationInWindow(it) }
            val area = android.graphics.Rect(location[0], location[1], location[0] + web.width, location[1] + web.height)
            runCatching {
                android.view.PixelCopy.request(window, area, full, { finish(it == android.view.PixelCopy.SUCCESS) }, android.os.Handler(mainLooper))
            }.onFailure { finish(false) }
        } else {
            val ok = runCatching { Canvas(full).apply { drawColor(android.graphics.Color.WHITE) }.let { web.draw(it) } }.isSuccess
            finish(ok)
        }
    }

    private fun shareImage(board: LiveBoard) = capture(1f) { bitmap ->
        if (bitmap == null) return@capture
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                exportFile("${board.title.toSafeFileName("Board")}.png").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            bitmap.recycle()
            NotebookFiles(this@LiveBoardActivity).shareFile(file, "image/png")
        }
    }

    /** Shares the board as an `.excalidraw` file, which excalidraw.com and the Excalidraw apps open. */
    private fun shareFile(board: LiveBoard) {
        web.evaluateJavascript(READ_SCENE) { result ->
            val (elements, appState) = parseScene(result) ?: return@evaluateJavascript
            lifecycleScope.launch {
                val file = withContext(Dispatchers.IO) {
                    val background = runCatching { JSONObject(appState).optString("viewBackgroundColor", "#ffffff") }.getOrDefault("#ffffff")
                    val document = JSONObject().put("type", "excalidraw").put("version", 2).put("source", START_URL)
                        .put("elements", JSONArray(elements)).put("appState", JSONObject().put("viewBackgroundColor", background)).put("files", JSONObject())
                    exportFile("${board.title.toSafeFileName("Board")}.excalidraw").apply { writeText(document.toString()) }
                }
                NotebookFiles(this@LiveBoardActivity).shareFile(file, "application/json")
            }
        }
    }

    private fun exportFile(name: String) = File(File(cacheDir, "exports").apply { mkdirs() }, name)

    /** Opens Excalidraw's own "Live collaboration" dialog, where a session starts or stops. */
    private fun openSessionDialog() = web.evaluateJavascript("document.querySelector('.collab-button')?.click()", null)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Full screen like the notebooks: system bars hidden, shown briefly by swiping from an edge. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onResume() {
        super.onResume()
        mapper.refresh()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        boardScript?.remove()
        (web.parent as? android.view.ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    companion object {
        /** The board to open (see [LiveBoardStore]). */
        const val EXTRA_BOARD_ID = "boardId"
        private const val HOST = "excalidraw.com"
        const val START_URL = "https://excalidraw.com/"
        fun isExcalidraw(host: String) = host == HOST || host.endsWith(".$HOST")

        /** `[elements, appState]` as excalidraw.com keeps them in localStorage. */
        private const val READ_SCENE = "JSON.stringify([localStorage.getItem('excalidraw') || '[]', localStorage.getItem('excalidraw-state') || '{}'])"

        private fun parseScene(result: String?): Pair<String, String>? = runCatching {
            val array = JSONArray(JSONTokener(result).nextValue() as String)
            array.getString(0) to array.getString(1)
        }.getOrNull()

        /**
         * Runs before Excalidraw's scripts: when another board was showing in this WebView, replace the
         * page's stored scene with [id]'s. Always turns pen pressure on ("variable" stroke width):
         * with Excalidraw's "constant" default the pen's pressure is ignored.
         */
        private fun boardScript(id: String, elements: String, appState: String) = """
            (function () {
              var result = 'kept';
              try {
                if (sessionStorage.getItem('folio-board') !== ${JSONObject.quote(id)}) {
                  sessionStorage.setItem('folio-board', ${JSONObject.quote(id)});
                  localStorage.setItem('excalidraw', ${JSONObject.quote(elements)});
                  localStorage.setItem('excalidraw-state', ${JSONObject.quote(appState)});
                  result = 'applied';
                }
                var state = JSON.parse(localStorage.getItem('excalidraw-state') || '{}');
                state.currentItemStrokeVariability = 'variable';
                localStorage.setItem('excalidraw-state', JSON.stringify(state));
              } catch (e) {}
              return result;
            })();
        """.trimIndent()
    }
}

/** The board from the intent; back saves it and returns to the library. */
@Composable
private fun LiveBoards(
    store: LiveBoardStore, web: PressureFilteringWebView, mapper: PenPressureMapper, boardId: String?,
    onOpen: (LiveBoard, String) -> Unit, onSave: (LiveBoard, () -> Unit) -> Unit, onClose: () -> Unit,
    onShareImage: (LiveBoard) -> Unit, onShareFile: (LiveBoard) -> Unit, onSession: () -> Unit,
) {
    var board by remember { mutableStateOf(boardId?.let(store::board)) }
    val open = board ?: run { LaunchedEffect(Unit) { onClose() }; return }
    LaunchedEffect(open.id) { onOpen(open, LiveBoardActivity.START_URL) }
    BoardScreen(open, web, mapper,
        // Leave only after the save: the preview is copied from the screen while the board still shows.
        onBack = { onSave(open, onClose) },
        onRename = { title -> store.rename(open.id, title); board = store.board(open.id) },
        onAutosave = { onSave(open) {} },
        onJoin = { url -> onOpen(open, url) },
        onShareImage = { onShareImage(open) }, onShareFile = { onShareFile(open) }, onSession = onSession)
}

@Composable
private fun BoardScreen(
    board: LiveBoard, web: PressureFilteringWebView, mapper: PenPressureMapper, onBack: () -> Unit, onRename: (String) -> Unit,
    onAutosave: () -> Unit, onJoin: (String) -> Unit, onShareImage: () -> Unit, onShareFile: () -> Unit, onSession: () -> Unit,
) {
    var pressure by remember { mutableStateOf(false) }
    var penSettings by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Off by default: a resting hand must not pan, zoom or tap. Turn on to pan and zoom with fingers.
    var fingers by remember { mutableStateOf(false) }
    var roomUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fingers) { web.fingersEnabled = fingers }
    // Autosave every 15 s, and follow the page URL: a live session shows up as #room=… in it.
    LaunchedEffect(board.id) {
        var tick = 0
        while (true) {
            delay(1000)
            roomUrl = web.url?.takeIf { "#room=" in it }
            if (++tick % 15 == 0) onAutosave()
        }
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(FolioBrand.Paper).windowInsetsPadding(WindowInsets.displayCutout)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            InkToolButton(InkIcon.Back, "Back to boards", onBack, buttonSize = 48.dp)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { renaming = true }.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(board.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = FolioBrand.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (roomUrl != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(FolioBrand.Sage))
                    Text("  Live session", fontSize = 12.sp, color = FolioBrand.Sage, fontWeight = FontWeight.Medium)
                } else Text("Live board", fontSize = 12.sp, color = FolioBrand.Muted)
            }
            InkToolButton(InkIcon.Hand, if (fingers) "Fingers on: pan and zoom" else "Fingers off: pen only", { fingers = !fingers },
                selected = fingers, buttonSize = 48.dp, tint = if (fingers) FolioBrand.Sage else FolioBrand.Ink)
            InkToolButton(InkIcon.Pressure, "Pen on the board", { pressure = true }, buttonSize = 48.dp)
            Spacer(Modifier.width(6.dp))
            Button(onClick = { share = true }, colors = ButtonDefaults.buttonColors(containerColor = FolioBrand.Sage),
                contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.heightIn(min = 44.dp)) {
                InkSymbol(InkIcon.Share, color = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        }
        HorizontalDivider(color = FolioBrand.Hairline)
        // MATCH_PARENT matters: with AndroidView's default WRAP_CONTENT the WebView sizes its viewport to the
        // page, and Excalidraw's full-height layout collapses to zero.
        AndroidView(factory = { context ->
            web.apply {
                (parent as? android.view.ViewGroup)?.removeView(this)
                layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                doOnLayout { (context as? LiveBoardActivity)?.loadPending() }
            }
        }, modifier = Modifier.fillMaxSize())
    }

    if (renaming) TitleDialog("Rename board", board.title, "Rename", onDismiss = { renaming = false }) { renaming = false; onRename(it) }
    if (share) ShareDialog(roomUrl, onDismiss = { share = false },
        onSession = { share = false; onSession() }, onJoin = { share = false; onJoin(it) },
        onShareImage = { share = false; onShareImage() }, onShareFile = { share = false; onShareFile() })
    if (pressure) LiveBoardPressureDialog(mapper, onPenSettings = { pressure = false; penSettings = true }, onDismiss = { pressure = false })
    if (penSettings) GlobalPenSettingsDialog { penSettings = false; mapper.refresh() }
}

@Composable
private fun ShareDialog(
    roomUrl: String?, onDismiss: () -> Unit, onSession: () -> Unit, onJoin: (String) -> Unit,
    onShareImage: () -> Unit, onShareFile: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    fun clipboardText() = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
    var joinLink by remember { mutableStateOf(clipboardText().takeIf { roomLink(it) != null }.orEmpty()) }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FolioBrand.Paper,
        title = { Text("Share board", color = FolioBrand.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Live session")
                if (roomUrl != null) {
                    Text("People with this link see the board and draw on it live, from any browser. The session is end-to-end encrypted.", fontSize = 13.sp, color = FolioBrand.Muted)
                    Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.dp, FolioBrand.Hairline)) {
                        Text(roomUrl, Modifier.fillMaxWidth().padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = FolioBrand.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard?.setPrimaryClip(ClipData.newPlainText("Folio live board", roomUrl)); copied = true }) {
                            Text(if (copied) "Copied" else "Copy link", color = FolioBrand.Ink)
                        }
                        Button(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Join my Folio board: $roomUrl"), "Send live link"))
                        }, colors = ButtonDefaults.buttonColors(containerColor = FolioBrand.Sage)) { Text("Send link…") }
                    }
                    TextButton(onClick = onSession) { Text("Stop session…", color = FolioBrand.Muted) }
                } else {
                    Text("Start a session and send the link in your call. Others watch and draw from any browser; the session is end-to-end encrypted.", fontSize = 13.sp, color = FolioBrand.Muted)
                    Button(onClick = onSession, colors = ButtonDefaults.buttonColors(containerColor = FolioBrand.Sage)) { Text("Start live session") }
                }
                HorizontalDivider(color = FolioBrand.Hairline)
                SectionLabel("Join a session")
                OutlinedTextField(joinLink, { joinLink = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("https://excalidraw.com/#room=…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { roomLink(joinLink)?.let(onJoin) }))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { joinLink = clipboardText() }) { Text("Paste", color = FolioBrand.Ink) }
                    Button(onClick = { roomLink(joinLink)?.let(onJoin) }, enabled = roomLink(joinLink) != null,
                        colors = ButtonDefaults.buttonColors(containerColor = FolioBrand.Sage)) { Text("Join") }
                }
                HorizontalDivider(color = FolioBrand.Hairline)
                SectionLabel("Export")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShareImage) { Text("Image (PNG)", color = FolioBrand.Ink) }
                    OutlinedButton(onClick = onShareFile) { Text("Excalidraw file", color = FolioBrand.Ink) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = FolioBrand.Sage, fontWeight = FontWeight.SemiBold) } },
    )
}

/** An Excalidraw room link (…#room=id,key) inside [text], or null. */
private fun roomLink(text: String): String? = Regex("https://(?:[\\w-]+\\.)?excalidraw\\.com/\\S*#room=\\S+").find(text)?.value

@Composable
private fun SectionLabel(text: String) =
    Text(text.uppercase(Locale.ROOT), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = FolioBrand.Muted)

@Composable
private fun TitleDialog(title: String, initial: String, action: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FolioBrand.Paper,
        title = { Text(title, color = FolioBrand.Ink) },
        text = {
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Untitled board") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onConfirm(text.trim()) }))
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text(action, color = FolioBrand.Sage, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FolioBrand.Ink) } },
    )
}

@Composable
private fun LiveBoardPressureDialog(mapper: PenPressureMapper, onPenSettings: () -> Unit, onDismiss: () -> Unit) {
    var min by remember { mutableFloatStateOf(mapper.minPressure) }
    var max by remember { mutableFloatStateOf(mapper.maxPressure) }
    var smooth by remember { mutableFloatStateOf(mapper.smoothing) }
    var sensitivity by remember { mutableFloatStateOf(mapper.sensitivity) }
    fun save() = mapper.setBoard(min, max.coerceAtLeast(min), smooth, sensitivity)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FolioBrand.Paper,
        title = { Text("Pen on the board", color = FolioBrand.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("The activation threshold and pressure curve come from your pen settings. These options apply to live boards only.", fontSize = 13.sp, color = FolioBrand.Muted)
                LabeledSlider("Sensitivity", sensitivity, 0f..1f) { sensitivity = it; save() }
                Text("Higher reaches full width with a lighter touch. 0.50 is linear.", fontSize = 12.sp, color = FolioBrand.Muted)
                LabeledSlider("Lightest stroke", min, 0f..0.5f) { min = it; save() }
                LabeledSlider("Strongest stroke", max, 0.3f..1f) { max = it; save() }
                LabeledSlider("Smoothing", smooth, 0f..PenPressureMapper.MAX_SMOOTHING) { smooth = it; save() }
                TextButton(onClick = { min = PenPressureMapper.DEFAULT_MIN; max = 1f; smooth = 0f; sensitivity = PenPressureMapper.DEFAULT_SENSITIVITY; save() }) {
                    Text("Reset to defaults", color = FolioBrand.Muted)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = FolioBrand.Sage, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onPenSettings) { Text("Pen settings…", color = FolioBrand.Ink) } },
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp, color = FolioBrand.Ink)
            Text(String.format(Locale.US, "%.2f", value), fontSize = 13.sp, color = FolioBrand.Muted)
        }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = FolioBrand.Sage, activeTrackColor = FolioBrand.Sage))
    }
}
