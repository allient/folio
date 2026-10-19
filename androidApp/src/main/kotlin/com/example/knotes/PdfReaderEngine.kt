package com.example.knotes

import android.content.Context
import android.graphics.*
import android.os.ParcelFileDescriptor
import android.view.*
import com.example.knotes.reader.*
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import kotlin.math.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal data class PdfSelection(val anchor: String, val text: String)
internal data class ReaderSearchResult(val label: String, val anchor: String)
internal data class PdfTextLine(val selection: PdfSelection, val bounds: RectF)

/** Native pages/text handles are scoped to one operation; no full-document bitmap conversion. */
internal class PdfReaderSession(file: File, password: String? = null) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val document = try { PdfiumCore().newDocument(descriptor, password) } catch (error: Throwable) { descriptor.close(); throw error }
    val pageCount = document.getPageCount().also { require(it > 0) { "PDF has no pages" } }
    private var closed = false
    private data class OcrWord(val text: String, val box: RectF)
    private val ocrPages = linkedMapOf<Int, List<OcrWord>>()
    suspend fun recognize(page: Int): Int = withContext(Dispatchers.IO) {
        val bitmap = render(page, 2000)
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = suspendCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(it) }.addOnFailureListener { continuation.resumeWithException(it) }
            }
            val words = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                element.boundingBox?.let { box -> OcrWord(element.text, RectF(box.left.toFloat()/bitmap.width,box.top.toFloat()/bitmap.height,box.right.toFloat()/bitmap.width,box.bottom.toFloat()/bitmap.height)) }
            }
            synchronized(this@PdfReaderSession) {
                if (!closed) { ocrPages[page] = words; while (ocrPages.size > 3) ocrPages.remove(ocrPages.keys.first()) }
            }
            words.size
        } finally { recognizer.close(); bitmap.recycle() }
    }
    @Synchronized fun pageText(page: Int): String {
        if (closed) return ""
        return document.openPage(page)!!.use { p -> p.openTextPage().use { text -> text.textPageGetText(0,text.textPageCountChars().coerceAtMost(50000)).orEmpty() } }
    }
    @Synchronized fun textLines(page: Int): List<PdfTextLine> {
        if (closed) return emptyList()
        return document.openPage(page)!!.use { p -> p.openTextPage().use { text ->
            val result = mutableListOf<PdfTextLine>()
            val count = text.textPageCountRects(0, text.textPageCountChars().coerceAtMost(50000))
            for (i in 0 until count.coerceAtMost(1000)) text.textPageGetRect(i)?.let { r ->
                val quote = text.textPageGetBoundedText(r, 3000).orEmpty().trim()
                if (quote.isNotEmpty()) {
                    val a = p.mapPageCoordsToDevice(0,0,10000,10000,0,r.left.toDouble(),r.top.toDouble())
                    val b = p.mapPageCoordsToDevice(0,0,10000,10000,0,r.right.toDouble(),r.bottom.toDouble())
                    val anchor = JSONObject().put("page", page).put("rects",JSONArray().put(JSONArray(listOf(r.left,r.top,r.right,r.bottom)))).toString()
                    result += PdfTextLine(PdfSelection(anchor,quote),RectF(min(a.x,b.x)/10000f,min(a.y,b.y)/10000f,max(a.x,b.x)/10000f,max(a.y,b.y)/10000f))
                }
            }
            result
        } }
    }
    private fun selectOcr(page: Int, start: PointF, end: PointF): PdfSelection? {
        val words = ocrPages[page]?.takeIf { it.isNotEmpty() } ?: return null
        fun nearest(point: PointF) = words.indices.minByOrNull { i -> val b=words[i].box; val dx=maxOf(b.left-point.x,0f,point.x-b.right);val dy=maxOf(b.top-point.y,0f,point.y-b.bottom);dx*dx+dy*dy }!!
        val from=nearest(start); val to=nearest(end); val selected=words.subList(minOf(from,to),maxOf(from,to)+1)
        return document.openPage(page)!!.use { p ->
            val rects=JSONArray()
            selected.forEach { word -> val r=word.box
                val a=p.mapDeviceCoordsToPage(0,0,10000,10000,0,(r.left*10000).toInt(),(r.top*10000).toInt())
                val b=p.mapDeviceCoordsToPage(0,0,10000,10000,0,(r.right*10000).toInt(),(r.bottom*10000).toInt())
                rects.put(JSONArray(listOf(minOf(a.x,b.x),maxOf(a.y,b.y),maxOf(a.x,b.x),minOf(a.y,b.y))))
            }
            PdfSelection(JSONObject().put("page",page).put("rects",rects).put("ocr",true).toString(),selected.joinToString(" ") { it.text })
        }
    }
    @Synchronized fun aspect(page: Int): Float { check(!closed); return document.openPage(page)!!.use { it.getPageWidthPoint().toFloat() / it.getPageHeightPoint().coerceAtLeast(1) } }
    @Synchronized fun render(page: Int, width: Int): Bitmap {
        check(!closed)
        return document.openPage(page)!!.use { p ->
            val aspect = p.getPageWidthPoint().toDouble() / p.getPageHeightPoint().coerceAtLeast(1)
            val w = min(width.coerceIn(64, 2560), (4096 * aspect).toInt().coerceAtLeast(64))
            val h = (w / aspect).toInt().coerceIn(1, 4096)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try { p.renderPageBitmap(bitmap, 0, 0, w, h, renderAnnot = true); bitmap } catch (error: Throwable) { bitmap.recycle(); throw error }
        }
    }
    @Synchronized fun select(page: Int, start: PointF, end: PointF): PdfSelection? {
        check(!closed)
        if (ocrPages.containsKey(page)) return selectOcr(page,start,end)
        return document.openPage(page)!!.use { p -> p.openTextPage().use { text ->
            fun position(point: PointF) = p.mapDeviceCoordsToPage(0, 0, 10000, 10000, 0, (point.x * 10000).toInt(), (point.y * 10000).toInt())
            val first = position(start); val last = position(end)
            val tolerance = max(p.getPageWidthPoint(), p.getPageHeightPoint()) * .025
            var from = text.textPageGetCharIndexAtPos(first.x.toDouble(), first.y.toDouble(), tolerance, tolerance)
            var to = text.textPageGetCharIndexAtPos(last.x.toDouble(), last.y.toDouble(), tolerance, tolerance)
            if (from < 0) return@use null
            if (to < 0) to = from
            if (from > to) { val swap = from; from = to; to = swap }
            val count = text.textPageCountChars()
            if (from == to) {
                while (from > 0 && !text.textPageGetUnicode(from - 1).isWhitespace()) from--
                while (to + 1 < count && !text.textPageGetUnicode(to + 1).isWhitespace()) to++
            }
            val length = (to - from + 1).coerceAtMost(50000)
            val rects = JSONArray()
            val rectCount = text.textPageCountRects(from, length)
            for (r in 0 until rectCount) text.textPageGetRect(r)?.let { rect -> rects.put(JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))) }
            if (rects.length() == 0) return@use null
            PdfSelection(JSONObject().put("page", page).put("rects", rects).toString(), text.textPageGetText(from, length).orEmpty())
        } }
    }
    @Synchronized fun project(page: Int, anchor: String): List<List<PointF>> {
        if (closed) return emptyList()
        val json = JSONObject(anchor)
        return document.openPage(page)!!.use { p ->
            fun map(x: Double, y: Double): PointF { val pt = p.mapPageCoordsToDevice(0, 0, 10000, 10000, 0, x, y); return PointF(pt.x / 10000f, pt.y / 10000f) }
            if (json.has("rects")) {
                val rects = json.getJSONArray("rects")
                (0 until rects.length()).map { index -> val r = rects.getJSONArray(index)
                    listOf(map(r.getDouble(0), r.getDouble(1)), map(r.getDouble(2), r.getDouble(3))) }
            } else if (json.has("points")) {
                val points = json.getJSONArray("points")
                listOf((0 until points.length()).map { index -> val pt = points.getJSONArray(index); map(pt.getDouble(0), pt.getDouble(1)) })
            } else emptyList()
        }
    }
    @Synchronized fun inkAnchor(page: Int, points: List<PointF>, width: Float): String {
        check(!closed)
        return document.openPage(page)!!.use { p ->
            val coordinates = JSONArray()
            points.forEach { point -> val pt = p.mapDeviceCoordsToPage(0, 0, 10000, 10000, 0, (point.x * 10000).toInt(), (point.y * 10000).toInt()); coordinates.put(JSONArray(listOf(pt.x, pt.y))) }
            JSONObject().put("page", page).put("points", coordinates).put("width", width * p.getPageWidthPoint()).toString()
        }
    }
    @Synchronized fun searchPage(page: Int, query: String): List<ReaderSearchResult> {
        check(!closed)
        return document.openPage(page)!!.use { p -> p.openTextPage().use { text ->
            val results = mutableListOf<ReaderSearchResult>()
            text.findStart(query, emptySet(), 0)?.use { find ->
                while (results.size < 50 && find.findNext()) {
                    val index = find.getSchResultIndex(); val count = find.getSchCount()
                    val rects = JSONArray()
                    for (r in 0 until text.textPageCountRects(index, count)) text.textPageGetRect(r)?.let { rects.put(JSONArray(listOf(it.left, it.top, it.right, it.bottom))) }
                    val start = (index - 40).coerceAtLeast(0)
                    val snippet = text.textPageGetText(start, min(count + 100, text.textPageCountChars() - start)).orEmpty()
                    results += ReaderSearchResult("Page ${page + 1} · $snippet", JSONObject().put("page", page).put("rects", rects).toString())
                }
            }
            results
        } }
    }
    @Synchronized override fun close() { if (!closed) { closed = true; try { document.close() } finally { descriptor.close() } } }
}

/** Bounded bitmap rendering off the main thread, with page-space selection and stylus strokes. */
internal class PdfPageReaderView(context: Context) : View(context) {
    var session: PdfReaderSession? = null
    var page = 0
    var tool = "Read"
    var chromeVisible = false
    var inkColor: Int = 0xFF303030.toInt()
    var inkWidth: Float = .003f
    var onSelection: (PdfSelection?) -> Unit = {}
    var onInk: (String) -> Unit = {}
    var onErase: (ReaderAnnotation) -> Unit = {}
    var onPageTurn: (forward: Boolean) -> Unit = {}
    var onPageTap: (x: Float, y: Float, width: Float, height: Float) -> Boolean = { _, _, _, _ -> false }
    var onAnnotationLongPress: (ReaderAnnotation) -> Unit = {}
    var onError: (String) -> Unit = {}
    var annotations: List<ReaderAnnotation> = emptyList()
    private data class Projection(val annotation: ReaderAnnotation, val groups: List<List<PointF>>, val inkWidth: Float)
    private var projections: List<Projection> = emptyList()
    private val pageBounds = RectF()
    private val annotationBounds = RectF()
    private val drawPath = Path()
    private var textLines: List<PdfTextLine> = emptyList()
    private val accessibility = object : androidx.customview.widget.ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int = textLines.indices.firstOrNull { lineBounds(it).contains(x.toInt(),y.toInt()) } ?: INVALID_ID
        override fun getVisibleVirtualViews(ids: MutableList<Int>) { textLines.indices.filterTo(ids) { Rect.intersects(lineBounds(it),Rect(0,0,width,height)) } }
        override fun onPopulateNodeForVirtualView(id: Int,node: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
            val line = textLines.getOrNull(id)
            node.text = line?.selection?.text ?: "PDF text"
            node.className = "android.widget.TextView"
            node.setBoundsInParent(if(line != null)lineBounds(id) else Rect(0,0,1,1))
            node.isClickable = true
            node.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,"Select passage"))
        }
        override fun onPerformActionForVirtualView(id: Int,action: Int,args: android.os.Bundle?): Boolean {
            if(action != android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)return false
            val line = textLines.getOrNull(id) ?: return false
            onSelection(line.selection);return true
        }
    }
    private fun lineBounds(index: Int): Rect {
        val page = bounds();val r = textLines[index].bounds
        return Rect((page.left+r.left*page.width()).toInt(),(page.top+r.top*page.height()).toInt(),(page.left+r.right*page.width()).toInt().coerceAtLeast(1),(page.top+r.bottom*page.height()).toInt().coerceAtLeast(1))
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var renderJob: Job? = null
    private var projectionJob: Job? = null
    private var bitmap: Bitmap? = null
    private var aspect = .707f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var zoom = 1f
    private var displayZoom = 1f
    private var displayMargin = 0f
    private var displayContrast = 1f
    private var displayBrightness = 0f
    private var pageFilter: ColorMatrixColorFilter? = null
    private val erasedInGesture = mutableSetOf<String>()
    fun setDisplay(scale: Float, margin: Float, contrast: Float, brightness: Float) {
        if (displayZoom != scale) { displayZoom = scale; zoom = scale; panX = 0f; panY = 0f; scheduleRender() }
        if (displayMargin != margin) { displayMargin = margin; invalidate() }
        if (displayContrast != contrast || displayBrightness != brightness) {
            displayContrast = contrast; displayBrightness = brightness
            val shift = 128f * (1f - contrast) + brightness
            pageFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(contrast,0f,0f,0f,shift, 0f,contrast,0f,0f,shift, 0f,0f,contrast,0f,shift, 0f,0f,0f,1f,0f)))
            invalidate()
        }
    }
    private fun eraseAt(x: Float, y: Float) {
        val point = normalize(x, y)
        val tolerance = 16f * resources.displayMetrics.density / bounds().width()
        projections.filter { it.annotation.kind == AnnotationKind.INK && it.annotation.id !in erasedInGesture }.forEach { projection ->
            val hit = projection.groups.any { points ->
                points.zipWithNext().any { (a, b) ->
                    val dx = b.x - a.x; val dy = b.y - a.y
                    val length = dx * dx + dy * dy
                    val t = if (length == 0f) 0f else (((point.x - a.x) * dx + (point.y - a.y) * dy) / length).coerceIn(0f, 1f)
                    hypot(point.x - a.x - t * dx, point.y - a.y - t * dy) < tolerance + projection.inkWidth / 2
                }
            }
            if (hit) { erasedInGesture.add(projection.annotation.id); onErase(projection.annotation) }
        }
    }
    private var panX = 0f
    private var panY = 0f
    private var previous = PointF()
    private var start = PointF()
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var downAt = 0L
    private var drawing = false
    private val stroke = mutableListOf<PointF>()
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoom = (zoom * detector.scaleFactor).coerceIn(1f, 4f); invalidate(); return true }
        override fun onScaleEnd(detector: ScaleGestureDetector) { scheduleRender() }
    })
    init {
        contentDescription = "PDF page. Use Select to highlight text, Pen to write, or pinch to zoom."; isFocusable = true
        androidx.core.view.ViewCompat.setAccessibilityDelegate(this,accessibility)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun dispatchHoverEvent(event: MotionEvent): Boolean = accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    fun show(session: PdfReaderSession, page: Int, annotations: List<ReaderAnnotation>) {
        if (this.session !== session || this.page != page) {
            this.session = session; this.page = page; bitmap = null; projections = emptyList(); textLines = emptyList(); accessibility.invalidateRoot(); stroke.clear(); zoom = displayZoom; panX = 0f; panY = 0f; scheduleRender()
        }
        if (this.annotations != annotations) { this.annotations = annotations; updateProjections() }
    }
    private fun bounds(): RectF {
        val inset = 24f + displayMargin * resources.displayMetrics.density
        val w = min(width.toFloat() - inset, (height - inset) * aspect).coerceAtLeast(1f) * zoom
        val h = w / aspect
        return pageBounds.apply { set((width - w) / 2 + panX, (height - h) / 2 + panY, (width + w) / 2 + panX, (height + h) / 2 + panY) }
    }
    private fun normalize(x: Float, y: Float): PointF { val b = bounds(); return PointF(((x - b.left) / b.width()).coerceIn(0f, 1f), ((y - b.top) / b.height()).coerceIn(0f, 1f)) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { scheduleRender() }
    private fun scheduleRender() {
        val current = session ?: return
        if (width <= 0 || height <= 0) return
        val index = page
        renderJob?.cancel()
        renderJob = scope.launch {
            try {
                val ratio = withContext(Dispatchers.IO) { current.aspect(index) }
                aspect = ratio
                val rendered = withContext(Dispatchers.IO) { current.render(index, (bounds().width()).toInt()) }
                ensureActive(); bitmap = rendered; invalidate(); updateProjections()
                textLines = withContext(Dispatchers.IO) { current.textLines(index) }
                contentDescription = "PDF page ${index+1}. ${if(textLines.isEmpty()) "Scanned page. Use Recognize text to enable highlighting." else "${textLines.size} text passages. Select a passage to annotate."}"
                accessibility.invalidateRoot()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onError(error.message ?: "Could not render page") }
        }
    }
    private fun updateProjections() {
        val current = session ?: return
        val index = page; val records = annotations.filter { JSONObject(it.anchor).optInt("page", -1) == index }
        projectionJob?.cancel()
        projectionJob = scope.launch {
            try { projections = withContext(Dispatchers.IO) { records.map { Projection(it, current.project(index, it.anchor), JSONObject(it.anchor).optDouble("normalizedWidth", .003).toFloat()) } }; invalidate() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onError(error.message ?: "Could not display annotations") }
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        canvas.clipRect(0, 0, width, height)
        // Keep the page edge visually seamless in immersive reading mode. The former
        // warm-gray matte looked like a second page/background on tablet-sized screens.
        canvas.drawColor(Color.WHITE)
        val b = bounds(); paint.color = Color.WHITE; paint.style = Paint.Style.FILL; canvas.drawRect(b, paint)
        bitmap?.let { paint.alpha = 255; paint.colorFilter = pageFilter; canvas.drawBitmap(it, null, b, paint); paint.colorFilter = null }
        projections.forEach { (annotation, groups, inkWidth) ->
            paint.color = annotation.color
            if (annotation.kind == AnnotationKind.INK) groups.forEach { points ->
                drawPath.rewind()
                points.forEachIndexed { i, p -> val x=b.left+p.x*b.width();val y=b.top+p.y*b.height();if(i==0)drawPath.moveTo(x,y)else drawPath.lineTo(x,y) }
                paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = inkWidth * b.width(); canvas.drawPath(drawPath, paint); paint.style = Paint.Style.FILL
            } else if (annotation.kind == AnnotationKind.HIGHLIGHT || annotation.kind == AnnotationKind.UNDERLINE) groups.forEach { points ->
                val a=points[0];val z=points[1]
                annotationBounds.set(b.left+min(a.x,z.x)*b.width(), b.top+min(a.y,z.y)*b.height(), b.left+max(a.x,z.x)*b.width(), b.top+max(a.y,z.y)*b.height())
                if (annotation.kind == AnnotationKind.UNDERLINE) { paint.strokeWidth = 2f * resources.displayMetrics.density; canvas.drawLine(annotationBounds.left,annotationBounds.bottom,annotationBounds.right,annotationBounds.bottom,paint) }
                else { paint.alpha = 90; canvas.drawRect(annotationBounds, paint); paint.alpha = 255 }
            }
        }
        if (stroke.isNotEmpty()) {
            drawPath.rewind()
            stroke.forEachIndexed { i,p -> val x=b.left+p.x*b.width();val y=b.top+p.y*b.height();if(i==0)drawPath.moveTo(x,y)else drawPath.lineTo(x,y) }
            paint.color = inkColor; paint.style = Paint.Style.STROKE; paint.strokeWidth = b.width() * inkWidth.coerceIn(.0008f,.02f); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; canvas.drawPath(drawPath,paint); paint.style = Paint.Style.FILL
        }
        canvas.restoreToCount(checkpoint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Compose draws the reader controls over this full-screen native view. Yield
        // those edge bands on ACTION_DOWN so the controls receive the gesture instead
        // of the page reader consuming it underneath.
        if (event.actionMasked == MotionEvent.ACTION_DOWN && chromeVisible) {
            val topControls = 88f * resources.displayMetrics.density
            val bottomControls = 176f * resources.displayMetrics.density
            if (event.y < topControls || event.y > height - bottomControls) return false
        }
        if (tool == "Read") scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); previous = PointF(event.x,event.y); gestureStartX = event.x; gestureStartY = event.y; downAt = event.eventTime; start = normalize(event.x,event.y); drawing = true; if (tool == "Pen") { stroke.clear(); stroke += start }; if (tool == "Eraser") { erasedInGesture.clear(); eraseAt(event.x, event.y) }; return true }
            MotionEvent.ACTION_POINTER_DOWN -> { drawing = false; stroke.clear() }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    if (tool == "Eraser" && drawing) eraseAt(event.x, event.y)
                    if (tool == "Read" && zoom > 1f) { panX = (panX + event.x - previous.x).coerceIn(-width * zoom / 2, width * zoom / 2); panY = (panY + event.y - previous.y).coerceIn(-height * zoom / 2,height * zoom / 2) }
                    if (tool == "Pen" && drawing && stroke.size < 10000) {
                        for (i in 0 until event.historySize) stroke += normalize(event.getHistoricalX(i),event.getHistoricalY(i))
                        stroke += normalize(event.x,event.y)
                    }
                }
                previous = PointF(event.x,event.y); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val current = session ?: return true; val index = page
                val dx = event.x - gestureStartX
                val dy = event.y - gestureStartY
                val isTap = drawing && abs(dx) < 18f * resources.displayMetrics.density && abs(dy) < 18f * resources.displayMetrics.density
                val held = event.eventTime - downAt >= android.view.ViewConfiguration.getLongPressTimeout()
                val point = normalize(event.x,event.y)
                val touchedMark = if (held && tool != "Pen") projections.firstOrNull { projection ->
                    projection.annotation.kind in listOf(AnnotationKind.HIGHLIGHT, AnnotationKind.UNDERLINE) && projection.groups.any { group ->
                        if (group.size < 2) false else {
                            val left = group.minOf { it.x } - .025f; val right = group.maxOf { it.x } + .025f
                            val top = group.minOf { it.y } - .025f; val bottom = group.maxOf { it.y } + .025f
                            point.x in left..right && point.y in top..bottom
                        }
                    }
                }?.annotation else null
                if (touchedMark != null) onAnnotationLongPress(touchedMark)
                val pageTapHandled = isTap && !held && onPageTap(event.x, event.y, width.toFloat(), height.toFloat())
                if (touchedMark == null && !pageTapHandled && drawing && (tool == "Select" || (tool == "Read" && held && isTap))) {
                    val end = normalize(event.x,event.y); val beginning = start
                    val longPressSelect = tool == "Read" && held && isTap
                    scope.launch { try { onSelection(withContext(Dispatchers.IO) {
                        if (longPressSelect) {
                            val lines = current.textLines(index)
                            val nearest = lines.minByOrNull { line ->
                                val dx = maxOf(line.bounds.left - beginning.x, 0f, beginning.x - line.bounds.right)
                                val dy = maxOf(line.bounds.top - beginning.y, 0f, beginning.y - line.bounds.bottom)
                                dx * dx + dy * dy
                            }
                            nearest?.takeIf { it.bounds.contains(beginning.x, beginning.y) || kotlin.math.sqrt(
                                maxOf(it.bounds.left - beginning.x, 0f, beginning.x - it.bounds.right).let { dx -> dx * dx } +
                                    maxOf(it.bounds.top - beginning.y, 0f, beginning.y - it.bounds.bottom).let { dy -> dy * dy }
                            ) < .06f }?.selection ?: current.select(index, beginning, end)
                        } else current.select(index, beginning, end)
                    }) } catch (error: Exception) { if (error is CancellationException) throw error; onError(error.message ?: "Could not select text") } }
                }
                if (!pageTapHandled && drawing && tool == "Read" && zoom <= 1.01f) {
                    val threshold = max(48f * resources.displayMetrics.density, width * .12f)
                    if (abs(dx) >= threshold && abs(dx) > abs(dy) * 1.25f) onPageTurn(dx < 0f)
                }
                if (!pageTapHandled && drawing && tool == "Pen" && stroke.size > 1) {
                    val points = stroke.toList()
                    stroke.clear(); invalidate()
                    val width = inkWidth.coerceIn(.0008f,.02f)
                    scope.launch { try { val anchor = withContext(Dispatchers.IO) { current.inkAnchor(index,points,width) }; onInk(JSONObject(anchor).put("normalizedWidth", width).toString()) } catch (error: Exception) { if (error is CancellationException) throw error; onError(error.message ?: "Could not save ink") } }
                }
                drawing = false; performClick()
            }
            MotionEvent.ACTION_CANCEL -> { drawing = false; stroke.clear(); invalidate() }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { scope.cancel(); bitmap = null; super.onDetachedFromWindow() }
}
