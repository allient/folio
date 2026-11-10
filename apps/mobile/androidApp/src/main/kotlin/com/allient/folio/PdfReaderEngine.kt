package com.allient.folio

import android.content.Context
import android.graphics.*
import android.os.ParcelFileDescriptor
import android.view.*
import com.allient.folio.reader.*
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
/** Anchors share original PDF page coordinates, including OCR and cropped viewports. */
internal fun overlappingPdfMarks(selection: PdfSelection, marks: List<ReaderAnnotation>): List<ReaderAnnotation> {
    val anchor = JSONObject(selection.anchor)
    val boxes = anchor.optJSONArray("rects") ?: return emptyList()
    fun rectangles(json: JSONObject): List<RectF> {
        val rects = json.optJSONArray("rects") ?: return emptyList()
        return (0 until rects.length()).map { index ->
            val r = rects.getJSONArray(index)
            val x1 = r.getDouble(0).toFloat(); val y1 = r.getDouble(1).toFloat()
            val x2 = r.getDouble(2).toFloat(); val y2 = r.getDouble(3).toFloat()
            RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
        }
    }
    if (boxes.length() == 0) return emptyList()
    val selected = rectangles(anchor)
    return marks.filter { mark ->
        if (mark.kind != AnnotationKind.HIGHLIGHT && mark.kind != AnnotationKind.UNDERLINE) false
        else {
            val saved = JSONObject(mark.anchor)
            saved.optInt("page", -1) == anchor.optInt("page", -2) && rectangles(saved).any { r -> selected.any { RectF.intersects(r, it) } }
        }
    }
}

internal data class ReaderSearchResult(val label: String, val anchor: String, val quote: String = "")
internal data class PdfTextLine(val selection: PdfSelection, val bounds: RectF)

/** Native pages/text handles are scoped to one operation; no full-document bitmap conversion. */
internal class PdfReaderSession(file: File, password: String? = null) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val document = try { PdfiumCore().newDocument(descriptor, password) } catch (error: Throwable) {
        descriptor.close()
        if (error is io.legere.pdfiumandroid.api.PdfPasswordException) throw IllegalArgumentException("This PDF is password protected. Remove the password in another app, then import it again.", error)
        throw error
    }
    val pageCount = document.getPageCount().also { if (it <= 0) { document.close(); descriptor.close(); throw IllegalArgumentException("This PDF has no pages.") } }
    private var closed = false
    @Synchronized fun contents(): List<ReaderContentsEntry> {
        check(!closed)
        var count = 0
        fun convert(items: List<io.legere.pdfiumandroid.api.Bookmark>, parent: String = "pdf", depth: Int = 0): List<ReaderContentsEntry> {
            if (depth > 32) return emptyList()
            return items.take((10_000 - count).coerceAtLeast(0)).mapIndexed { index, bookmark ->
                count++
                val id = "$parent-$index"
                val target = bookmark.pageIdx.takeIf { it >= 0 && it < pageCount }?.toInt()
                ReaderContentsEntry(id, bookmark.title.orEmpty().trim().ifBlank { target?.let { "Page ${it + 1}" } ?: "Section" }, target,
                    convert(bookmark.children, id, depth + 1))
            }
        }
        return convert(document.getTableOfContents())
    }
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
            val chars = text.textPageCountChars().coerceAtMost(50000)
            // Split on the line breaks Pdfium generates, so each line's quote holds exactly its own
            // characters. Reading text by rectangle instead picked up glyphs of neighbouring lines.
            fun addLine(from: Int, to: Int) {
                if (to < from || result.size >= 1000) return
                val quote = text.textPageGetText(from, to - from + 1).orEmpty().collapseWhitespace()
                if (quote.isEmpty()) return
                var left = Float.MAX_VALUE; var top = -Float.MAX_VALUE; var right = -Float.MAX_VALUE; var bottom = Float.MAX_VALUE
                val rects = JSONArray()
                for (i in 0 until text.textPageCountRects(from, to - from + 1)) text.textPageGetRect(i)?.let { r ->
                    rects.put(JSONArray(listOf(r.left, r.top, r.right, r.bottom)))
                    left = min(left, r.left); right = max(right, r.right); top = max(top, r.top); bottom = min(bottom, r.bottom)
                }
                if (rects.length() == 0) return
                val a = p.mapPageCoordsToDevice(0,0,10000,10000,0,left.toDouble(),top.toDouble())
                val b = p.mapPageCoordsToDevice(0,0,10000,10000,0,right.toDouble(),bottom.toDouble())
                val anchor = JSONObject().put("page", page).put("rects", rects).toString()
                result += PdfTextLine(PdfSelection(anchor,quote),RectF(min(a.x,b.x)/10000f,min(a.y,b.y)/10000f,max(a.x,b.x)/10000f,max(a.y,b.y)/10000f))
            }
            var lineStart = 0
            for (i in 0 until chars) {
                val c = text.textPageGetUnicode(i)
                if (c == '\n' || c == '\r') { addLine(lineStart, i - 1); lineStart = i + 1 }
            }
            addLine(lineStart, chars - 1)
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
    private val contentBoxes = linkedMapOf<Int, RectF>()
    /** Low-resolution raster analysis also handles scanned pages and illustrations. */
    @Synchronized fun contentBounds(page: Int): RectF {
        contentBoxes[page]?.let { return RectF(it) }
        val image = render(page, 640)
        val result = try {
            val row = IntArray(image.width)
            var left = image.width; var top = image.height; var right = -1; var bottom = -1
            for (y in 0 until image.height) {
                image.getPixels(row, 0, image.width, 0, y, image.width, 1)
                for (x in row.indices) {
                    val pixel = row[x]
                    if ((pixel ushr 24) > 32 && minOf((pixel shr 16) and 255, (pixel shr 8) and 255, pixel and 255) < 235) {
                        left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
                    }
                }
            }
            if (right < left || bottom < top) RectF(0f, 0f, 1f, 1f)
            else RectF((left - 6).coerceAtLeast(0).toFloat() / image.width, (top - 6).coerceAtLeast(0).toFloat() / image.height,
                (right + 7).coerceAtMost(image.width).toFloat() / image.width, (bottom + 7).coerceAtMost(image.height).toFloat() / image.height)
        } finally { image.recycle() }
        contentBoxes[page] = result
        while (contentBoxes.size > 16) contentBoxes.remove(contentBoxes.keys.first())
        return RectF(result)
    }
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
                    val start = (index - 50).coerceAtLeast(0)
                    val raw = text.textPageGetText(start, min(index - start + count + 70, text.textPageCountChars() - start)).orEmpty()
                    // One readable line, cut at word boundaries around the match.
                    var snippet = raw.collapseWhitespace()
                    if (start > 0) snippet = "…" + snippet.substringAfter(' ', snippet)
                    if (start + raw.length < text.textPageCountChars()) snippet = snippet.substringBeforeLast(' ', snippet) + "…"
                    results += ReaderSearchResult("Page ${page + 1} · $snippet", JSONObject().put("page", page).put("rects", rects).toString(), text.textPageGetText(index, count).orEmpty())
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
    /** Active notebook brush for new strokes; null keeps the plain [inkColor]/[inkWidth] polyline pen. Eraser styles are ignored. */
    var brush: BrushStyle? = null
    var onSelection: (PdfSelection?) -> Unit = {}
    var onSelectionBounds: (anchor: String, boundsInWindow: RectF) -> Unit = { _, _ -> }
    var onInk: (String) -> Unit = {}
    var onErase: (ReaderAnnotation) -> Unit = {}
    var onPageTurn: (forward: Boolean) -> Unit = {}
    var onPageTap: (x: Float, y: Float, width: Float, height: Float) -> Boolean = { _, _, _, _ -> false }
    var gestures = ReaderGesturePreferences()
    var onShowControls: () -> Unit = {}
    var onError: (String) -> Unit = {}
    var annotations: List<ReaderAnnotation> = emptyList()
    /** [stroke] is set for notebook-brush ink and drawn by [NotebookRenderer]; legacy ink keeps the polyline. */
    private data class Projection(val annotation: ReaderAnnotation, val groups: List<List<PointF>>, val inkWidth: Float, val stroke: InkStroke? = null)
    private var projections: List<Projection> = emptyList()
    /**
     * A finished stroke stays on screen from the moment the pen lifts until its saved copy has been projected, so the
     * ink never disappears and reappears (the "re-render" flash) while it is written to the database.
     */
    private class PendingInk(val projection: Projection) { var anchor: String? = null }
    private val pendingInk = mutableListOf<PendingInk>()
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
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
    private var imageAdjustments = ReaderImageAdjustments()
    fun setImageAdjustments(value: ReaderImageAdjustments) {
        if (imageAdjustments == value) return
        val pixelsChanged = imageAdjustments.copy(smoothing = value.smoothing) != value
        imageAdjustments = value
        paint.isFilterBitmap = value.smoothing
        if (pixelsChanged) scheduleRender(100)
        invalidate()
    }
    var onZoomChange: (Float) -> Unit = {}
    var onCrop: (RectF) -> Unit = {}
    var onMarqueeZoom: (Float) -> Unit = {}
    private var cropSelection: RectF? = null
    private var cropHandle = -1
    fun setCropSelection(rect: RectF?) { if (cropSelection != rect) { cropSelection = rect; invalidate() } }
    private var resetToTop = false
    private var topAligned = false
    private var fitWidth = false
    private var crop = RectF(0f, 0f, 1f, 1f)
    private var enhancement = 0f
    private var scrolling = false
    private var rtl = false
    fun setLayout(widthFit: Boolean, rect: RectF?, textEnhancement: Float, continuous: Boolean, rightToLeft: Boolean, alignTop: Boolean = false) {
        val next = rect ?: RectF(0f, 0f, 1f, 1f)
        val changed = fitWidth != widthFit || crop != next || topAligned != alignTop
        topAligned = alignTop; fitWidth = widthFit; crop.set(next); scrolling = continuous; rtl = rightToLeft
        if (enhancement != textEnhancement) { enhancement = textEnhancement; updateFilter() }
        if (changed) { resetToTop = widthFit; panX = 0f; panY = 0f; scheduleRender(); invalidate() }
    }
    private fun updateFilter() {
        val contrast = displayContrast * (1f + enhancement * .045f)
        val shift = 128f * (1f - contrast) + displayBrightness - enhancement * 1.5f
        pageFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(contrast,0f,0f,0f,shift, 0f,contrast,0f,0f,shift, 0f,0f,contrast,0f,shift, 0f,0f,0f,1f,0f)))
        invalidate()
    }
    private var pageFilter: ColorMatrixColorFilter? = null
    private val erasedInGesture = mutableSetOf<String>()
    fun setDisplay(scale: Float, margin: Float, contrast: Float, brightness: Float) {
        if (displayZoom != scale) { displayZoom = scale; zoom = scale; panX = 0f; panY = 0f; resetToTop = fitWidth; scheduleRender() }
        if (displayMargin != margin) { displayMargin = margin; invalidate() }
        if (displayContrast != contrast || displayBrightness != brightness) {
            displayContrast = contrast; displayBrightness = brightness
            updateFilter()
        }
    }
    private fun eraseAt(x: Float, y: Float) {
        val point = normalize(x, y)
        val tolerance = dp(16f) / bounds().width()
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
    private var selecting = false
    private var selectionJob: Job? = null
    private var preview: PdfSelection? = null
    private var previewGroups: List<List<PointF>> = emptyList()
    private var selectionFrom = PointF()
    private var selectionTo = PointF()
    private var draggingHandle = -1
    /** Set when a held stylus turns an automatic pen stroke into a text selection; lasts for the rest of that gesture. */
    private var gestureToolOverride: String? = null
    private var gestureStylusAutoPen = false
    private val longPress = Runnable {
        if (drawing && tool == "Read") {
            // A stylus that stays still is selecting, not writing: drop the dot it started and continue as a Read gesture.
            if (gestureStylusAutoPen) { gestureToolOverride = "Read"; clearStroke() }
            selecting = true
            parent?.requestDisallowInterceptTouchEvent(true)
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            updateSelection(start, start)
        }
    }
    private fun updateSelection(from: PointF, to: PointF, publish: Boolean = false) {
        val current = session ?: return
        val index = page
        selectionJob?.cancel()
        selectionJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val selection = current.select(index, from, to)
                    selection to selection?.let { current.project(index, it.anchor) }.orEmpty()
                }
                preview = result.first; previewGroups = result.second
                selectionFrom = from; selectionTo = to
                invalidate()
                if (publish) onSelection(preview)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                onError(error.message ?: "Could not select text")
            }
        }
    }
    /** Selection handles are a stem plus a large dot below the text, so they are easy to see and grab with a finger or stylus. */
    private val handleDrop get() = dp(16f)
    private val handleRadius get() = dp(13f)
    private fun selectionHandles(): List<PointF> {
        val groups = if (selecting || draggingHandle >= 0) previewGroups else projections.firstOrNull { it.annotation.id == "selection" }?.groups.orEmpty()
        if (groups.isEmpty()) return emptyList()
        val b = bounds()
        val first = groups.first(); val last = groups.last()
        return listOf(PointF(b.left + first.minOf { it.x } * b.width(), b.top + first.maxOf { it.y } * b.height()),
            PointF(b.left + last.maxOf { it.x } * b.width(), b.top + last.maxOf { it.y } * b.height()))
    }
    private val windowLocation = IntArray(2)
    private val reportedSelectionBounds = RectF()
    private var reportedSelectionAnchor: String? = null
    private fun reportSelectionBounds() {
        val selection = projections.firstOrNull { it.annotation.id == "selection" } ?: return
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (group in selection.groups) for (point in group) {
            minX = minOf(minX, point.x); minY = minOf(minY, point.y)
            maxX = maxOf(maxX, point.x); maxY = maxOf(maxY, point.y)
        }
        if (!minX.isFinite()) return
        val page = bounds()
        val left = (page.left + minX * page.width()).coerceIn(0f, width.toFloat())
        val top = (page.top + minY * page.height()).coerceIn(0f, height.toFloat())
        val right = (page.left + maxX * page.width()).coerceIn(0f, width.toFloat())
        val bottom = (page.top + maxY * page.height()).coerceIn(0f, height.toFloat())
        if (right <= left || bottom <= top) return
        getLocationInWindow(windowLocation)
        val windowLeft = left + windowLocation[0]; val windowTop = top + windowLocation[1]
        val windowRight = right + windowLocation[0]; val windowBottom = bottom + windowLocation[1]
        if (reportedSelectionAnchor == selection.annotation.anchor && reportedSelectionBounds.left == windowLeft && reportedSelectionBounds.top == windowTop && reportedSelectionBounds.right == windowRight && reportedSelectionBounds.bottom == windowBottom) return
        reportedSelectionAnchor = selection.annotation.anchor
        reportedSelectionBounds.set(windowLeft, windowTop, windowRight, windowBottom)
        onSelectionBounds(selection.annotation.anchor, RectF(reportedSelectionBounds))
    }
    private var drawing = false
    private val stroke = mutableListOf<PointF>()
    private val strokePressures = mutableListOf<Float>()
    private val strokeTimes = mutableListOf<Long>()
    private var strokeBrush: BrushStyle? = null
    private val ink = JetpackInkEngine()
    private fun startStroke(point: PointF, event: MotionEvent) {
        stroke.clear(); strokePressures.clear(); strokeTimes.clear(); ink.clearLive()
        strokeBrush = brush?.takeUnless { it.eraser }
        addStrokePoint(point, event.getPressure(0), 0L, event)
    }
    private fun addStrokePoint(point: PointF, raw: Float, elapsed: Long, event: MotionEvent) {
        val stylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        val active = strokeBrush
        val pressure = if (!stylus || !raw.isFinite()) 1f else active?.mapPressure(raw.coerceIn(0f, 1f)) ?: raw.coerceIn(0f, 1f)
        stroke += point; strokePressures += pressure; strokeTimes += elapsed
        if (active != null) ink.append(InkPoint(point.x, ReaderInk.strokeY(point.y, aspect), pressure, elapsed), active)
    }
    private fun clearStroke() { stroke.clear(); strokePressures.clear(); strokeTimes.clear(); ink.clearLive(); strokeBrush = null }
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Zoom around the fingers: the page point under the focus stays under it.
            val next = (zoom * detector.scaleFactor).coerceIn(1f, 4f)
            val ratio = next / zoom
            val fx = detector.focusX - width / 2f; val fy = detector.focusY - height / 2f
            panX = fx - (fx - panX) * ratio; panY = fy - (fy - panY) * ratio
            zoom = next; clampPan(); invalidate(); return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) { displayZoom = zoom; onZoomChange(zoom); scheduleRender() }
    })
    init {
        contentDescription = "PDF page. Use Select to highlight text, Pen to write, or pinch to zoom."; isFocusable = true
        androidx.core.view.ViewCompat.setAccessibilityDelegate(this,accessibility)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun dispatchHoverEvent(event: MotionEvent): Boolean = accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    fun show(session: PdfReaderSession, page: Int, annotations: List<ReaderAnnotation>) {
        if (this.session !== session || this.page != page) {
            removeCallbacks(longPress); selectionJob?.cancel(); selecting = false; draggingHandle = -1; preview = null; previewGroups = emptyList()
            this.session = session; this.page = page; bitmap = null; projections = emptyList(); pendingInk.clear(); textLines = emptyList(); accessibility.invalidateRoot(); clearStroke(); zoom = displayZoom; panX = 0f; panY = 0f; scheduleRender()
        }
        if (this.annotations != annotations) { this.annotations = annotations; updateProjections() }
    }
    private fun bounds(): RectF {
        val inset = 24f + dp(displayMargin)
        val croppedAspect = aspect * crop.width() / crop.height()
        val visibleWidth = (if (fitWidth) width.toFloat() - inset else min(width.toFloat() - inset, (height - inset) * croppedAspect)).coerceAtLeast(1f) * zoom
        val w = visibleWidth / crop.width()
        val h = w / aspect
        val left = (width - visibleWidth) / 2 + panX - crop.left * w
        val top = (if (topAligned && h * crop.height() < height - inset) inset / 2 else (height - h * crop.height()) / 2) + panY - crop.top * h
        return pageBounds.apply { set(left, top, left + w, top + h) }
    }
    /** Keeps a zoomed page covering the view: it can be panned to its edges, never off screen. */
    private fun clampPan() {
        val saved = zoom; val pannedX = panX; val pannedY = panY
        panX = 0f; panY = 0f
        val b = bounds()
        val limitX = ((b.width() * crop.width() - width) / 2f).coerceAtLeast(0f)
        val limitY = ((b.height() * crop.height() - height) / 2f).coerceAtLeast(0f)
        zoom = saved; panX = pannedX.coerceIn(-limitX, limitX); panY = pannedY.coerceIn(-limitY, limitY)
    }
    private fun normalize(x: Float, y: Float): PointF { val b = bounds(); return PointF(((x - b.left) / b.width()).coerceIn(0f, 1f), ((y - b.top) / b.height()).coerceIn(0f, 1f)) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { resetToTop = fitWidth; clampPan(); scheduleRender() }
    private fun scheduleRender(debounceMillis: Long = 0) {
        val current = session ?: return
        if (width <= 0 || height <= 0) return
        val index = page
        val adjustments = imageAdjustments
        renderJob?.cancel()
        renderJob = scope.launch {
            try {
                if (debounceMillis > 0) delay(debounceMillis)
                val ratio = withContext(Dispatchers.IO) { current.aspect(index) }
                aspect = ratio
                if (resetToTop) {
                    panY = ((bounds().height() * crop.height() - height) / 2f).coerceAtLeast(0f)
                    resetToTop = false
                }
                val target = bounds().width().toInt()
                val rendered = withContext(Dispatchers.IO) {
                    val image = current.render(index, target)
                    try { adjustReaderImage(image, adjustments); image }
                    catch (error: Throwable) { image.recycle(); throw error }
                }
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
            try { projections = withContext(Dispatchers.IO) {
                val ratio by lazy { current.aspect(index) }
                records.map { record ->
                    val json = JSONObject(record.anchor); val groups = current.project(index, record.anchor)
                    val brushStroke = if (record.kind == AnnotationKind.INK && ReaderInk.isBrush(json)) ReaderInk.toInkStroke(record.id, json, record.color, groups.firstOrNull().orEmpty(), ratio) else null
                    Projection(record, groups, ReaderInk.normalizedWidth(json), brushStroke)
                }
            }
                pendingInk.removeAll { pending -> pending.anchor?.let { saved -> records.any { it.anchor == saved } } == true }
                invalidate() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onError(error.message ?: "Could not display annotations") }
        }
    }
    override fun onDraw(canvas: Canvas) {
        reportSelectionBounds()
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        canvas.clipRect(0, 0, width, height)
        // Keep the page edge visually seamless in immersive reading mode. The former
        // warm-gray matte looked like a second page/background on tablet-sized screens.
        canvas.drawColor(Color.WHITE)
        val b = bounds(); paint.color = Color.WHITE; paint.style = Paint.Style.FILL; canvas.drawRect(b, paint)
        val pageClip = canvas.save()
        canvas.clipRect(b.left + crop.left * b.width(), b.top + crop.top * b.height(), b.left + crop.right * b.width(), b.top + crop.bottom * b.height())
        bitmap?.let { paint.alpha = 255; paint.colorFilter = pageFilter; canvas.drawBitmap(it, null, b, paint); paint.colorFilter = null }
        fun drawMark(projection: Projection) {
            val (annotation, groups, inkWidth, brushStroke) = projection
            if (annotation.id == "selection" && (selecting || draggingHandle >= 0)) return
            paint.color = annotation.color
            if (brushStroke != null) {
                val inkClip = canvas.save(); canvas.translate(b.left, b.top)
                NotebookRenderer.drawStroke(canvas, brushStroke, b.width(), ReaderInk.strokeHeight(b.width()), ink)
                canvas.restoreToCount(inkClip)
            } else if (annotation.kind == AnnotationKind.INK) groups.forEach { points ->
                drawPath.rewind()
                points.forEachIndexed { i, p -> val x=b.left+p.x*b.width();val y=b.top+p.y*b.height();if(i==0)drawPath.moveTo(x,y)else drawPath.lineTo(x,y) }
                paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = inkWidth * b.width(); canvas.drawPath(drawPath, paint); paint.style = Paint.Style.FILL
            } else if (annotation.kind == AnnotationKind.HIGHLIGHT || annotation.kind == AnnotationKind.UNDERLINE) groups.forEach { points ->
                val a=points[0];val z=points[1]
                annotationBounds.set(b.left+min(a.x,z.x)*b.width(), b.top+min(a.y,z.y)*b.height(), b.left+max(a.x,z.x)*b.width(), b.top+max(a.y,z.y)*b.height())
                if (annotation.kind == AnnotationKind.UNDERLINE) {
                    paint.strokeWidth = dp(2f)
                    when (JSONObject(annotation.anchor).optString("underlineStyle", "solid")) {
                        "wavy" -> {
                            drawPath.rewind(); drawPath.moveTo(annotationBounds.left, annotationBounds.bottom)
                            var x = annotationBounds.left
                            while (x < annotationBounds.right) {
                                val next = min(x + dp(2f), annotationBounds.right)
                                drawPath.lineTo(next, annotationBounds.bottom + sin((next - annotationBounds.left) / dp(8f) * (2 * PI)).toFloat() * dp(2f))
                                x = next
                            }
                            paint.style = Paint.Style.STROKE; canvas.drawPath(drawPath, paint); paint.style = Paint.Style.FILL
                        }
                        else -> {
                            if (JSONObject(annotation.anchor).optString("underlineStyle") == "dashed") paint.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(3f)), 0f)
                            canvas.drawLine(annotationBounds.left,annotationBounds.bottom,annotationBounds.right,annotationBounds.bottom,paint)
                            paint.pathEffect = null
                        }
                    }
                }
                else { paint.alpha = 90; canvas.drawRect(annotationBounds, paint); paint.alpha = 255 }
            }
        }
        projections.forEach { drawMark(it) }
        pendingInk.forEach { drawMark(it.projection) }
        if (selecting || draggingHandle >= 0) {
            paint.color = 0x5576B9EF
            previewGroups.forEach { points ->
                if (points.size >= 2) canvas.drawRect(b.left + points.minOf { it.x } * b.width(), b.top + points.minOf { it.y } * b.height(), b.left + points.maxOf { it.x } * b.width(), b.top + points.maxOf { it.y } * b.height(), paint)
            }
        }
        paint.color = Color.rgb(47, 122, 104)
        selectionHandles().forEach { handle ->
            val centerY = handle.y + handleDrop
            paint.color = Color.rgb(47, 122, 104); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2.5f); paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(handle.x, handle.y, handle.x, centerY, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(handle.x, centerY, handleRadius + dp(2f), paint.apply { color = Color.WHITE })
            canvas.drawCircle(handle.x, centerY, handleRadius, paint.apply { color = Color.rgb(47, 122, 104) })
            paint.color = Color.WHITE; canvas.drawCircle(handle.x, centerY, handleRadius * .38f, paint)
            paint.color = Color.rgb(47, 122, 104)
        }
        if (stroke.isNotEmpty() && strokeBrush != null) {
            // Same engine as the saved stroke, so the live line already looks final.
            val live = canvas.save(); canvas.translate(b.left, b.top)
            ink.drawLive(canvas, b.width().toInt(), ReaderInk.strokeHeight(b.width()).toInt(), 1f)
            canvas.restoreToCount(live)
        } else if (stroke.isNotEmpty()) {
            drawPath.rewind()
            stroke.forEachIndexed { i,p -> val x=b.left+p.x*b.width();val y=b.top+p.y*b.height();if(i==0)drawPath.moveTo(x,y)else drawPath.lineTo(x,y) }
            paint.color = inkColor; paint.style = Paint.Style.STROKE; paint.strokeWidth = b.width() * inkWidth.coerceIn(.0008f,.02f); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND; canvas.drawPath(drawPath,paint); paint.style = Paint.Style.FILL
        }
        canvas.restoreToCount(pageClip)
        if (tool == "MarqueeCrop") cropSelection?.let { rect ->
            val left = b.left + rect.left * b.width(); val right = b.left + rect.right * b.width()
            val top = b.top + rect.top * b.height(); val bottom = b.top + rect.bottom * b.height()
            paint.color = 0x88000000.toInt(); paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, width.toFloat(), top, paint)
            canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), paint)
            canvas.drawRect(0f, top, left, bottom, paint); canvas.drawRect(right, top, width.toFloat(), bottom, paint)
            paint.color = Color.rgb(47, 122, 104); paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
            canvas.drawRect(left, top, right, bottom, paint); paint.style = Paint.Style.FILL
            listOf(PointF(left, top), PointF(right, top), PointF(right, bottom), PointF(left, bottom)).forEach { canvas.drawCircle(it.x, it.y, dp(7f), paint) }
        }
        if (drawing && cropHandle < 0 && tool in listOf("MarqueeCrop", "MarqueeZoom")) {
            paint.color = Color.DKGRAY; paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f)
            canvas.drawRect(min(gestureStartX, previous.x), min(gestureStartY, previous.y), max(gestureStartX, previous.x), max(gestureStartY, previous.y), paint)
            paint.style = Paint.Style.FILL
        }
        canvas.restoreToCount(checkpoint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) gestureToolOverride = null
        val stylusAutoPen = gestures.stylusWriting && (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) && this.tool == "Read"
        if (event.actionMasked == MotionEvent.ACTION_DOWN) gestureStylusAutoPen = stylusAutoPen
        val tool = gestureToolOverride ?: if (stylusAutoPen) "Pen" else this.tool
        // Compose draws the reader controls over this full-screen native view. Yield
        // those edge bands on ACTION_DOWN so the controls receive the gesture instead
        // of the page reader consuming it underneath.
        if (event.actionMasked == MotionEvent.ACTION_DOWN && chromeVisible) {
            val topControls = dp(ReaderChromeBands.TOP_DP)
            val bottomControls = dp(ReaderChromeBands.BOTTOM_DP)
            if (event.y < topControls || event.y > height - bottomControls) return false
        }
        if (tool == "Read" && gestures.pinch) scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(longPress)
                selecting = false
                draggingHandle = selectionHandles().withIndex().filter { (_, it) -> hypot(event.x - it.x, event.y - it.y - handleDrop) < dp(40f) }
                    .minByOrNull { (_, it) -> hypot(event.x - it.x, event.y - it.y - handleDrop) }?.index ?: -1
                if (draggingHandle >= 0) {
                    // Grabbing a selection handle only moves the handle, even for a stylus that would otherwise write.
                    gestureToolOverride = "Read"; clearStroke()
                    val groups = projections.firstOrNull { it.annotation.id == "selection" }?.groups.orEmpty()
                    if (groups.isNotEmpty()) {
                        selectionFrom = PointF(groups.first().minOf { it.x }, groups.first().map { it.y }.average().toFloat())
                        selectionTo = PointF(groups.last().maxOf { it.x }, groups.last().map { it.y }.average().toFloat())
                    }
                }
                if ((tool == "Read" || stylusAutoPen) && gestures.clickLongPress && draggingHandle < 0) postDelayed(longPress, android.view.ViewConfiguration.getLongPressTimeout().toLong() + if (stylusAutoPen) 300L else 0L)
                cropHandle = -1
                if (tool == "MarqueeCrop") cropSelection?.let { rect ->
                    val b = bounds()
                    val corners = listOf(PointF(rect.left, rect.top), PointF(rect.right, rect.top), PointF(rect.right, rect.bottom), PointF(rect.left, rect.bottom))
                    cropHandle = corners.indexOfFirst { hypot(event.x - b.left - it.x * b.width(), event.y - b.top - it.y * b.height()) < dp(28f) }
                }
                parent?.requestDisallowInterceptTouchEvent(!scrolling || tool != "Read" || zoom > 1f); previous = PointF(event.x,event.y); gestureStartX = event.x; gestureStartY = event.y; downAt = event.eventTime; start = normalize(event.x,event.y); drawing = true; if (tool == "Pen" && draggingHandle < 0) startStroke(start, event); if (tool == "Eraser") { erasedInGesture.clear(); eraseAt(event.x, event.y) }; return true }
            MotionEvent.ACTION_POINTER_DOWN -> { removeCallbacks(longPress); selecting = false; draggingHandle = -1; selectionJob?.cancel(); drawing = false; clearStroke() }
            MotionEvent.ACTION_MOVE -> {
                if (!selecting && draggingHandle < 0 && hypot(event.x - gestureStartX, event.y - gestureStartY) > android.view.ViewConfiguration.get(context).scaledTouchSlop) removeCallbacks(longPress)
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    if (draggingHandle >= 0) {
                        val point = normalize(event.x, event.y - handleDrop)
                        updateSelection(if (draggingHandle == 0) point else selectionFrom, if (draggingHandle == 1) point else selectionTo)
                    } else if (selecting || tool == "Select") updateSelection(start, normalize(event.x, event.y))
                    if (tool == "Eraser" && drawing) eraseAt(event.x, event.y)
                    if (tool == "Read" && gestures.slide && !selecting && draggingHandle < 0 && (zoom > 1f || fitWidth)) { panX += event.x - previous.x; panY += event.y - previous.y; clampPan() }
                    if (tool == "Pen" && drawing && stroke.size < 10000) {
                        for (i in 0 until event.historySize) addStrokePoint(normalize(event.getHistoricalX(i),event.getHistoricalY(i)), event.getHistoricalPressure(i), event.getHistoricalEventTime(i) - downAt, event)
                        addStrokePoint(normalize(event.x,event.y), event.pressure, event.eventTime - downAt, event)
                    }
                }
                if (tool == "MarqueeCrop" && cropHandle >= 0) cropSelection?.let { rect ->
                    val point = normalize(event.x, event.y)
                    when (cropHandle) {
                        0 -> { rect.left = point.x.coerceAtMost(rect.right - .025f); rect.top = point.y.coerceAtMost(rect.bottom - .025f) }
                        1 -> { rect.right = point.x.coerceAtLeast(rect.left + .025f); rect.top = point.y.coerceAtMost(rect.bottom - .025f) }
                        2 -> { rect.right = point.x.coerceAtLeast(rect.left + .025f); rect.bottom = point.y.coerceAtLeast(rect.top + .025f) }
                        3 -> { rect.left = point.x.coerceAtMost(rect.right - .025f); rect.bottom = point.y.coerceAtLeast(rect.top + .025f) }
                    }
                    onCrop(RectF(rect))
                }
                previous = PointF(event.x,event.y); invalidate()
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if ((selecting && hypot(event.x - gestureStartX, event.y - gestureStartY) > dp(18f)) || draggingHandle >= 0) {
                    val point = normalize(event.x, event.y - if (draggingHandle >= 0) handleDrop else 0f)
                    updateSelection(if (draggingHandle == 0) point else if (draggingHandle == 1) selectionFrom else start,
                        if (draggingHandle == 0) selectionTo else point, publish = true)
                    selecting = false; draggingHandle = -1; drawing = false
                    return true
                }
                val current = session ?: return true; val index = page
                val dx = event.x - gestureStartX
                val dy = event.y - gestureStartY
                if (gestures.bottom && tool == "Read" && gestureStartY > height - dp(48f) && dy < -dp(48f) && abs(dy) > abs(dx)) {
                    drawing = false; onShowControls(); return true
                }
                val isTap = drawing && abs(dx) < dp(18f) && abs(dy) < dp(18f)
                val held = event.eventTime - downAt >= android.view.ViewConfiguration.getLongPressTimeout()
                val point = normalize(event.x,event.y)
                // While writing or erasing, a short touch (the dot of an "i", a full stop) is ink, never a page tap that opens the menu or turns the page.
                val pageTapHandled = isTap && !held && tool != "Pen" && tool != "Eraser" && onPageTap(event.x, event.y, width.toFloat(), height.toFloat())
                if (!pageTapHandled && drawing && (tool == "Select" || (gestures.clickLongPress && tool == "Read" && held && isTap))) {
                    val end = normalize(event.x,event.y); val beginning = start
                    val longPressSelect = tool == "Read" && held && isTap
                    scope.launch { try { onSelection(withContext(Dispatchers.IO) {
                        if (longPressSelect) {
                            // A long press selects the pressed word (the range handles extend it). Only when no word is
                            // under the finger does the nearest line stand in, as before.
                            current.select(index, beginning, beginning) ?: run {
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
                            }
                        } else current.select(index, beginning, end)
                    }) } catch (error: Exception) { if (error is CancellationException) throw error; onError(error.message ?: "Could not select text") } }
                }
                if (drawing && cropHandle < 0 && tool in listOf("MarqueeCrop", "MarqueeZoom") && abs(dx) > dp(16f) && abs(dy) > dp(16f)) {
                    val end = normalize(event.x, event.y)
                    val rect = RectF(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
                    if (rect.width() > .02f && rect.height() > .02f) {
                        if (tool == "MarqueeCrop") onCrop(rect)
                        else {
                            val b = bounds()
                            val next = (zoom * min(width / (rect.width() * b.width()), height / (rect.height() * b.height()))).coerceIn(1f, 4f)
                            val focusX = b.left + rect.centerX() * b.width() - width / 2f
                            val focusY = b.top + rect.centerY() * b.height() - height / 2f
                            panX = (panX - focusX) * next / zoom
                            panY = (panY - focusY) * next / zoom
                            zoom = next; displayZoom = next; clampPan(); scheduleRender()
                            onMarqueeZoom(next)
                        }
                    }
                }
                if (gestures.slide && !pageTapHandled && drawing && tool == "Read" && !scrolling && zoom <= 1.01f) {
                    val threshold = max(dp(48f), width * .12f)
                    if (abs(dx) >= threshold && abs(dx) > abs(dy) * 1.25f) onPageTurn((dx < 0f) != rtl)
                }
                // A single touch point becomes a dot: the brush engine and the saved anchor both need at least two points.
                if (drawing && tool == "Pen" && stroke.size == 1) { stroke += stroke[0]; strokePressures += strokePressures[0]; strokeTimes += strokeTimes[0] + 8L }
                if (!pageTapHandled && drawing && tool == "Pen" && stroke.size > 1) {
                    val points = stroke.toList(); val pressures = strokePressures.toList(); val times = strokeTimes.toList(); val used = strokeBrush
                    val width = if (used != null) ReaderInk.normalizedWidth(used) else inkWidth.coerceIn(.0008f,.02f)
                    // Show the finished stroke at once (same engine, same points) and keep it until the saved copy is projected.
                    val previewId = "pending-${System.nanoTime()}"
                    val previewStroke = used?.let { ReaderInk.toInkStroke(previewId, ReaderInk.withBrush(JSONObject().put("normalizedWidth", width.toDouble()), it, pressures, times), inkColor, points, aspect) }
                    val pending = PendingInk(Projection(ReaderAnnotation(previewId, "", AnnotationKind.INK, "", color = inkColor, createdAt = 0L), listOf(points), width, previewStroke))
                    pendingInk += pending
                    clearStroke(); invalidate()
                    scope.launch { try { val anchor = withContext(Dispatchers.IO) { current.inkAnchor(index,points,width) }
                        val json = JSONObject(anchor).put("normalizedWidth", width)
                        val saved = (if (used != null) ReaderInk.withBrush(json, used, pressures, times) else json).toString()
                        pending.anchor = saved
                        // Safety net: the saved copy normally replaces the preview within a frame or two.
                        postDelayed({ if (pendingInk.remove(pending)) invalidate() }, 4000)
                        onInk(saved) } catch (error: Exception) { pendingInk.remove(pending); invalidate(); if (error is CancellationException) throw error; onError(error.message ?: "Could not save ink") } }
                }
                selecting = false; draggingHandle = -1; drawing = false; invalidate(); performClick()
            }
            MotionEvent.ACTION_CANCEL -> { gestureToolOverride = null; removeCallbacks(longPress); selectionJob?.cancel(); selecting = false; draggingHandle = -1; drawing = false; clearStroke(); invalidate() }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Compose can detach and re-attach the same view; a cancelled scope would leave the page blank.
        if (!scope.isActive) { scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate); scheduleRender(); updateProjections() }
    }
    override fun onDetachedFromWindow() { removeCallbacks(longPress); scope.cancel(); bitmap = null; clearStroke(); ink.release(); super.onDetachedFromWindow() }
}
