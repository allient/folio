package com.allient.folio

import android.content.Context
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Distance of the rotation handle above the selection box. */
private const val ROTATE_HANDLE_DP = 28f

internal enum class CanvasMode { Write, Lasso, MoveSelection, Shape, Pan, FreeZoom, Fill, Objects, ColorPicker, ZoomWindow }
internal enum class ShapeKind(val label: String, val closed: Boolean = true) {
    Line("Line", false), WavyLine("Wavy line", false), Curve("Curve", false), Arrow("Arrow", false), DoubleArrow("Double arrow", false),
    Rectangle("Rectangle"), RoundedRectangle("Rounded rectangle"), Square("Square"), Parallelogram("Parallelogram"), Trapezoid("Trapezoid"),
    Triangle("Triangle"), RightTriangle("Right triangle"), Diamond("Diamond"), Pentagon("Pentagon"), Hexagon("Hexagon"),
    Ellipse("Ellipse"), Circle("Circle"), Star("Star"), Cross("Cross"),
    Cube("Cube", false), Cuboid("Cuboid", false), Pyramid("Pyramid", false),
    Cone("Cone", false), Cylinder("Cylinder", false), Sphere("Sphere", false),
    Capsule("Terminator"), Octagon("Octagon"), Heart("Heart"), Cloud("Cloud"), Pie("Pie"), Crescent("Crescent"), Arc("Arc", false),
    PredefinedProcess("Predefined process", false), InternalStorage("Internal storage", false), Document("Document"), ManualInput("Manual input"),
    ManualOperation("Manual operation"), OffPage("Off-page connector"), Delay("Delay"), Display("Display"),
    SummingJunction("Summing junction", false), OrJunction("Or junction", false), Merge("Merge"),
    BlockRight("Right arrow"), BlockLeft("Left arrow"), BlockUp("Up arrow"), BlockDown("Down arrow"), BlockLeftRight("Left-right arrow"),
    BlockUpDown("Up-down arrow"), BlockFourWay("Four-way arrow"), Chevron("Chevron"), HomePlate("Pentagon arrow"),
    CalloutRect("Speech bubble"), CalloutRounded("Rounded speech bubble"), CalloutOval("Oval speech bubble"), Starburst("Starburst"),
    Hemisphere("Hemisphere", false), TriPrism("Triangular prism", false), Frustum("Frustum", false), Axes2D("Axes", false), Axes3D("3D axes", false),
    NumberGrid("Number grid", false), Angle("Angle", false), RightAngle("Right angle", false), Brace("Curly brace", false), Bracket("Bracket", false), Paren("Parenthesis", false),
    ElbowLine("Elbow connector", false), ElbowArrow("Elbow arrow connector", false), ElbowDouble("Elbow double arrow connector", false),
    SCurve("Curved connector", false), SCurveArrow("Curved arrow connector", false), SCurveDouble("Curved double arrow connector", false),
    Inheritance("Inheritance", false), Aggregation("Aggregation", false), Composition("Composition", false),
}
internal enum class ShapeLineStyle(val label: String) { Solid("Solid"), Dotted("Dotted"), Dashed("Dashed"), DashDot("Dash dot") }
internal enum class LassoKind { Smart, Freeform, Rectangle }
internal enum class EraserMode(val label: String) { Pixel("Pixel Eraser"), Stroke("Stroke Eraser"), Lasso("Lasso Eraser") }

internal class InkCanvasView(context: Context, onChanged: (List<InkStroke>) -> Unit) : FrameLayout(context) {
    init { setWillNotDraw(false); contentDescription = "Handwriting canvas"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    private var frontInk: FrontBufferedInkController? = null
    private var backendReported = false
    private val ink = JetpackInkEngine()
    private var segmentStartedAt = 0L
    private var cachedPage: NotebookPage? = null
    private var contentRevision = 0L
    private var pageCommands: CompletedInkPageCache? = null
    private var cancelImageLoad: (() -> Unit)? = null
    private var imageBitmaps: Map<String, android.graphics.Bitmap> = emptyMap()
    private var imagePaths: List<String> = emptyList()
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(55, 108, 177) }
    private val selectionHandleBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val lassoPath = Path()
    private fun contentChanged() { cachedPage = null; contentRevision++; postInvalidateOnAnimation() }
    private var activePoints = mutableListOf<InkPoint>()
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var activeBrush = brushes[0]
    private var gestureMode = CanvasMode.Write
    private var shapeStart: Offset? = null
    private var shapeEnd: Offset? = null
    private val lasso = mutableListOf<Offset>()
    private var editBaseline: List<InkStroke>? = null
    private var lastEraserPoint: Offset? = null
    private var eraseGesture = false
    private var eraserCursor: Offset? = null
    private var movingSelection = false
    private var resizingSelection = false
    private var rotatingSelection = false
    private var rotatePivot = Offset.Zero
    private var rotateStartAngle = 0f
    private var resizeVector = Offset.Zero
    private var resizePivot = Offset.Zero
    private var lastPosition = Offset.Zero
    private var panX = 0f
    private var panY = 0f
    private var zoomValue = 1f
    private var freeZoomStart: Offset? = null
    private var freeZoomEnd: Offset? = null
    private val freeZoomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 108, 177); style = Paint.Style.STROKE; strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }
    private var multitouch = false
    private var previousMultiFocus = Offset.Zero
    private var dragBaseline: List<InkStroke>? = null
    private var dragOrigin = Offset.Zero
    private var selectedObjectId: String? = null
    private var selectedObjectIsText = false
    private var draggingObject = false
    private var resizingImage = false
    private var rotatingObject = false
    private var objectRotationBase = 0f
    private var objectRotationStart = 0f
    private var resizingTextCard = false
    private var embeddedTouchId: String? = null
    private var embeddedTouchAction = false
    private var embeddedTouchStart = Offset.Zero
    var onEmbeddedAction: (InkText) -> Unit = {}
    private var objectsBaseline: Pair<List<InkText>, List<InkImage>>? = null
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 108, 177); style = Paint.Style.STROKE; strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            finishGesture(); resetFrontInk()
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val previous = zoomValue
            zoomValue = (previous * detector.scaleFactor).coerceIn(.25f, 5f)
            val factor = zoomValue / previous
            // Scale around the current focal point. Centroid translation is handled
            // independently from MotionEvent below, including when span stays constant.
            panX = detector.focusX - (detector.focusX - panX) * factor
            panY = detector.focusY - (detector.focusY - panY) * factor
            constrainPan(); onZoomChanged(zoomValue); postInvalidateOnAnimation(); return true
        }
    })

    var strokes: List<InkStroke> = emptyList()
        set(value) {
            if (field == value) return
            if (Build.VERSION.SDK_INT >= 29) {
                if (frontInk?.hasActive == true) finishGesture()
                frontInk?.reconcile(value)
            }
            field = value; contentChanged()
        }
    var brush: BrushStyle = brushes[0]
        set(value) { if (field != value) { finishGesture(); resetFrontInk() }; field = value }
    var threshold: Float = pressureProfiles[1].threshold
    var pressureCurve: ResponseCurve = defaultPressureCurve
    var pressureCurveEnabled: Boolean = true
    var velocityCurveEnabled: Boolean = false
    var velocityCurve: ResponseCurve = defaultVelocityCurve
    var tiltCurveEnabled: Boolean = false
    var tiltCurve: ResponseCurve = defaultTiltCurve
    private var lastSampleTime = 0L
    var onStrokesChanged: (List<InkStroke>) -> Unit = onChanged
    var mode: CanvasMode = CanvasMode.Write
        set(value) { if (field != value) { finishGesture(); resetFrontInk() }; field = value }
    var shapeKind: ShapeKind = ShapeKind.Line
        set(value) { if (field != value) finishGesture(); field = value }
    var shapeFilled: Boolean = false
        set(value) { if (field != value) finishGesture(); field = value }
    var shapeColor: Int = Color.BLACK
        set(value) { if (field != value) { field = value; postInvalidateOnAnimation() } }
    var shapeWidth: Float = 1f
        set(value) { if (field != value) { field = value; postInvalidateOnAnimation() } }
    var shapeLineStyle: ShapeLineStyle = ShapeLineStyle.Solid
        set(value) { if (field != value) { field = value; postInvalidateOnAnimation() } }
    var fillColor: Int = Color.BLACK
    var onColorPicked: (Int) -> Unit = {}
    var lassoKind: LassoKind = LassoKind.Freeform
        set(value) { if (field != value) finishGesture(); field = value }
    var eraserMode: EraserMode = EraserMode.Stroke
        set(value) { if (field != value) finishGesture(); field = value }
    var eraserWidth: Float = 22f
        set(value) { if (field != value) finishGesture(); field = value.coerceIn(4f, 80f) }
    var eraseActiveLayerOnly = true
    var onEraseFinished: () -> Unit = {}
    val hasPendingEdits: Boolean get() = editBaseline != null || dragBaseline != null || objectsBaseline != null || blocksBaseline != null
    var activeLayerId: String = "ink"
        set(value) { if (field != value) { finishGesture(); resetFrontInk(); field = value; contentChanged() } }
    var layers: List<InkLayer> = listOf(InkLayer(id = "ink", name = "Layer 1"))
        set(value) { if (field != value) { finishGesture(); resetFrontInk(); field = value; contentChanged() } }
    var template: String = "blank"
        set(value) { if (field != value) { field = value; contentChanged() } }
    // --- Tables and sticky notes (InkBlock). Ink inside a table moves and scales with it. ---
    private var blockContent: List<InkBlock> = emptyList()
    var blocks: List<InkBlock>
        get() = blockContent
        set(value) { if (blockContent != value) { finishGesture(); resetFrontInk(); blockContent = value; contentChanged() } }
    var selectedBlockId: String? = null
        set(value) { if (field != value) { field = value; postInvalidateOnAnimation() } }
    var onBlocksChanged: (List<InkBlock>, List<InkStroke>) -> Unit = { _, _ -> }
    var onBlockSelectionChanged: (String?) -> Unit = {}
    private var blocksBaseline: Pair<List<InkBlock>, List<InkStroke>>? = null
    /** Selected table cells as [firstRow, firstColumn, lastRow, lastColumn]; drawn over the selected table. */
    var selectedCells: List<Int>? = null
        set(value) { if (field != value) { field = value; postInvalidateOnAnimation() } }
    /** A touch that selected a block without moving it, with the page-normalised point. */
    var onBlockTap: (String, Float, Float) -> Unit = { _, _, _ -> }
    private var blockTouch = Offset.Zero
    private var draggingBlock = false
    private var resizingBlock = false
    private var blockStrokeIds: Set<String> = emptySet()
    private fun blockBounds(block: InkBlock) = RectF(block.x * width, block.y * documentHeight, (block.x + block.width) * width, (block.y + block.height) * documentHeight)
    private fun startBlockDrag(block: InkBlock, resize: Boolean) {
        draggingBlock = true; resizingBlock = resize; blocksBaseline = blockContent to strokes
        blockStrokeIds = strokes.filter { s ->
            editable(s.layerId) && s.points.isNotEmpty() &&
                (s.points.minOf { it.x } + s.points.maxOf { it.x }) / 2f in block.x..(block.x + block.width) &&
                (s.points.minOf { it.y } + s.points.maxOf { it.y }) / 2f in block.y..(block.y + block.height)
        }.map { it.id }.toSet()
    }
    private fun moveBlock(point: Offset) {
        val id = selectedBlockId ?: return
        val dx = point.x - lastPosition.x; val dy = point.y - lastPosition.y
        var scaleX = 1f; var scaleY = 1f; var shiftX = 0f; var shiftY = 0f; var originX = 0f; var originY = 0f
        blockContent = blockContent.map { b ->
            if (b.id != id || !editable(b.layerId)) b
            else if (resizingBlock) {
                val w = (b.width + dx).coerceIn(.04f, if (infiniteCanvas) 4f else (1f - b.x).coerceAtLeast(.04f))
                val h = (b.height + dy).coerceIn(.03f, if (infiniteCanvas) 4f else (1f - b.y).coerceAtLeast(.03f))
                scaleX = w / b.width; scaleY = h / b.height; originX = b.x; originY = b.y
                b.copy(width = w, height = h)
            } else {
                val nx = if (infiniteCanvas) b.x + dx else (b.x + dx).coerceIn(0f, (1f - b.width).coerceAtLeast(0f))
                val ny = if (infiniteCanvas) b.y + dy else (b.y + dy).coerceIn(0f, (1f - b.height).coerceAtLeast(0f))
                shiftX = nx - b.x; shiftY = ny - b.y
                b.copy(x = nx, y = ny)
            }
        }
        if (blockStrokeIds.isNotEmpty() && (shiftX != 0f || shiftY != 0f || scaleX != 1f || scaleY != 1f)) {
            strokes = strokes.map { s ->
                if (s.id !in blockStrokeIds) s else s.copy(points = s.points.map { p ->
                    if (resizingBlock) p.copy(x = originX + (p.x - originX) * scaleX, y = originY + (p.y - originY) * scaleY) else p.copy(x = p.x + shiftX, y = p.y + shiftY)
                })
            }
        }
        contentChanged()
    }
    private fun publishBlocks() {
        val previous = blocksBaseline
        val wasResizing = resizingBlock
        blocksBaseline = null; draggingBlock = false; resizingBlock = false; blockStrokeIds = emptySet()
        if (previous != null && (previous.first != blockContent || previous.second != strokes)) onBlocksChanged(blockContent, strokes)
        else if (previous != null && !wasResizing) selectedBlockId?.let { onBlockTap(it, blockTouch.x, blockTouch.y) }
    }

    private var textContent: List<InkText> = emptyList()
    var texts: List<InkText>
        get() = textContent
        set(value) { if (textContent != value) { finishGesture(); resetFrontInk(); textContent = value; contentChanged() } }
    private var imageContent: List<InkImage> = emptyList()
    var images: List<InkImage>
        get() = imageContent
        set(value) {
            if (imageContent == value) return
            finishGesture(); resetFrontInk()
            imageContent = value; contentChanged()
            val paths = value.map { it.path }.distinct()
            if (paths != imagePaths) { imagePaths = paths; imageBitmaps = emptyMap(); loadImages() }
        }
    private fun loadImages() {
        cancelImageLoad?.invoke()
        cancelImageLoad = NotebookImageStore.preload(images, context.filesDir) { imageBitmaps = it; contentChanged() }
    }
    var fingerDrawing: Boolean = false
    var longPressSelectEnabled: Boolean = false
    var onLongPressSelection: ((Boolean) -> Unit)? = null
    var pinchToZoomEnabled: Boolean = true
    var singleFingerVerticalSwipe: Boolean = false
    var twoFingerVerticalScroll: Boolean = false
    var onVerticalFingerScroll: ((Float) -> Unit)? = null
    var singlePageFingerSwipeEnabled: Boolean = false
    var onHorizontalFingerSwipe: ((Int) -> Unit)? = null
    private var fingerSwipeStartY = 0f
    private var fingerSwipeLastY = 0f
    private var fingerSwipeActive = false
    private var horizontalSwipeStart = Offset.Zero
    private var horizontalSwipeTracking = false
    private var horizontalSwipeConsumed = false
    private var longPressPickerPending = false
    private var longPressPickerStart = Offset.Zero
    private val longPressPickerRunnable = Runnable {
        if (longPressPickerPending && longPressSelectEnabled && fingerDrawing && gestureMode == CanvasMode.Write) {
            longPressPickerPending = false
            activePoints.clear(); ink.clearLive()
            if (Build.VERSION.SDK_INT >= 29) frontInk?.cancelActive()
            activePointerId = MotionEvent.INVALID_POINTER_ID
            horizontalSwipeTracking = false
            val point = position(longPressPickerStart.x, longPressPickerStart.y)
            selectedStrokeIds = emptySet(); onSelectionChanged(emptySet())
            selectedObjectId = null; onObjectSelectionChanged(null, false)
            gestureMode = CanvasMode.Objects
            beginObjectDrag(point)
            draggingObject = false; objectsBaseline = null; draggingBlock = false; resizingBlock = false; blocksBaseline = null
            if (selectedObjectId != null || selectedBlockId != null) {
                onLongPressSelection?.invoke(true)
            } else {
                val x = point.x * width; val y = point.y * documentHeight
                val hitRadius = 28f * resources.displayMetrics.density / zoomValue
                val hit = strokes.asReversed().asSequence().filter { editable(it.layerId) }
                    .mapNotNull { stroke ->
                        val distance = stroke.points.minOfOrNull { sample -> hypot(x - sample.x * width, y - sample.y * documentHeight) } ?: Float.MAX_VALUE
                        if (distance <= hitRadius + stroke.width * width / 1200f) stroke to distance else null
                    }.minByOrNull { it.second }?.first
                if (hit != null) {
                    selectedStrokeIds = setOf(hit.id); onSelectionChanged(selectedStrokeIds)
                    onLongPressSelection?.invoke(false)
                }
            }
            postInvalidateOnAnimation()
        }
    }
    private fun cancelLongPressPicker() {
        longPressPickerPending = false
        removeCallbacks(longPressPickerRunnable)
    }
    var selectedStrokeIds: Set<String> = emptySet()
        set(value) { if (field != value) { field = value; postInvalidateOnAnimation() } }
    var onSelectionChanged: (Set<String>) -> Unit = {}
    var onZoomChanged: (Float) -> Unit = {}
    var infiniteCanvas: Boolean = false
    var onAddPin: (Offset) -> Unit = {}
    /** When positive, a page is `width * documentAspect` tall instead of filling the view, so a second, differently sized view maps ink uniformly. */
    var documentAspect: Float = 0f
    private val documentHeight: Int get() = if (infiniteCanvas) (width * 1.6f).toInt().coerceAtLeast(1) else if (documentAspect > 0f) (width * documentAspect).toInt().coerceAtLeast(1) else height

    // --- Zoom window: a draggable box on the page whose contents are written in a magnified panel (see docs/ruler-and-zoom-window.md). ---
    /** Page-normalised box; its height tracks [zoomWindowRatio] (box height / width) so it matches the panel's shape. */
    var zoomWindow: RectF? = null
        set(value) { field = value; postInvalidateOnAnimation() }
    var zoomWindowRatio: Float = .5f
    var onZoomWindowChanged: (RectF) -> Unit = {}
    private var zoomWindowGesture = 0 // 0 none, 1 move, 2 resize
    private var zoomWindowGrab = Offset.Zero
    private val zoomWindowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF2A8CF0.toInt() }
    private val zoomWindowHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A8CF0.toInt() }

    /** Show a page-normalised region filling this view's width (used by the zoom panel). */
    fun showRegion(region: RectF) {
        if (width <= 0 || region.width() <= 0f) return
        zoomValue = 1f / region.width()
        panX = width / 2f - region.centerX() * width * zoomValue
        panY = height / 2f - region.centerY() * documentHeight * zoomValue
        onZoomChanged(zoomValue); postInvalidateOnAnimation()
    }
    private fun clampZoomWindow(box: RectF): RectF {
        if (infiniteCanvas) return box
        val w = box.width().coerceIn(.08f, 1f); val h = w * zoomWindowRatio
        val left = box.left.coerceIn(0f, (1f - w).coerceAtLeast(0f)); val top = box.top.coerceIn(0f, (1f - h).coerceAtLeast(0f))
        return RectF(left, top, left + w, top + h)
    }
    private fun handleZoomWindowTouch(event: MotionEvent): Boolean {
        val point = position(event.x, event.y)
        val current = zoomWindow
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                finishGesture()
                val handleReach = 28f * resources.displayMetrics.density / zoomValue
                if (current != null && hypot((point.x - current.right) * width, (point.y - current.bottom) * documentHeight) <= handleReach) zoomWindowGesture = 2
                else if (current != null && current.contains(point.x, point.y)) { zoomWindowGesture = 1; zoomWindowGrab = Offset(current.left - point.x, current.top - point.y) }
                else {
                    val w = (current?.width() ?: .4f).coerceIn(.08f, 1f)
                    zoomWindowGesture = 1; zoomWindowGrab = Offset(-w / 2f, -w * zoomWindowRatio / 2f)
                    val box = clampZoomWindow(RectF(point.x + zoomWindowGrab.x, point.y + zoomWindowGrab.y, point.x + zoomWindowGrab.x + w, point.y + zoomWindowGrab.y + w * zoomWindowRatio))
                    zoomWindow = box; onZoomWindowChanged(box)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val box = zoomWindow ?: return true
                val next = when (zoomWindowGesture) {
                    1 -> RectF(point.x + zoomWindowGrab.x, point.y + zoomWindowGrab.y, point.x + zoomWindowGrab.x + box.width(), point.y + zoomWindowGrab.y + box.height())
                    2 -> { val w = (point.x - box.left).coerceAtLeast(.08f); RectF(box.left, box.top, box.left + w, box.top + w * zoomWindowRatio) }
                    else -> null
                }
                if (next != null) clampZoomWindow(next).let { zoomWindow = it; onZoomWindowChanged(it) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> zoomWindowGesture = 0
        }
        postInvalidateOnAnimation(); return true
    }
    private fun drawZoomWindow(canvas: Canvas, box: RectF) {
        val scale = 1f / zoomValue; val density = resources.displayMetrics.density
        zoomWindowPaint.strokeWidth = 2.2f * density * scale
        val l = box.left * width; val t = box.top * documentHeight; val r = box.right * width; val b = box.bottom * documentHeight
        zoomWindowPaint.style = Paint.Style.FILL; zoomWindowPaint.alpha = 22; canvas.drawRect(l, t, r, b, zoomWindowPaint)
        zoomWindowPaint.style = Paint.Style.STROKE; zoomWindowPaint.alpha = 255; canvas.drawRect(l, t, r, b, zoomWindowPaint)
        for (i in 0..2) for (j in 0..i) canvas.drawCircle(r - (2 + (2 - i) * 5) * density * scale, b - (2 + (2 - j) * 5) * density * scale, 1.4f * density * scale, zoomWindowHandle)
    }

    private var pinPending = false
    private var pinStart = Offset.Zero
    private val addPinRunnable = Runnable {
        if (pinPending && infiniteCanvas) {
            pinPending = false
            cancelLongPressPicker()
            activePoints.clear(); ink.clearLive(); if (Build.VERSION.SDK_INT >= 29) frontInk?.cancelActive()
            activePointerId = MotionEvent.INVALID_POINTER_ID
            onAddPin(position(pinStart.x, pinStart.y)); postInvalidateOnAnimation()
        }
    }
    private fun blankAt(point: Offset): Boolean {
        val radius = 18f * resources.displayMetrics.density / width.coerceAtLeast(1) / zoomValue
        val hitInk = strokes.any { stroke -> stroke.points.isNotEmpty() &&
            InkGeometry.erase(listOf(stroke), point, point, radius * width, width.toFloat(), documentHeight.toFloat(), true) { true }.isEmpty() }
        return !hitInk && texts.none { point.x in it.x..(it.x + it.boxWidth) && point.y in it.y..(it.y + .12f) } &&
            images.none { point.x in it.x..(it.x + it.width) && point.y in it.y..(it.y + it.height) }
    }
    var onObjectsChanged: (List<InkText>, List<InkImage>) -> Unit = { _, _ -> }
    var onObjectSelectionChanged: (String?, Boolean) -> Unit = { _, _ -> }
    fun deselectObject() { selectedObjectId = null; selectedBlockId = null; onBlockSelectionChanged(null); onObjectSelectionChanged(null, false); invalidate() }
    val zoom: Float get() = zoomValue
    val viewportCenterX: Float get() = if (width > 0) ((width / 2f - panX) / width / zoomValue).let { if (infiniteCanvas) it else it.coerceIn(0f, 1f) } else .5f
    val viewportCenterY: Float get() = if (documentHeight > 0) ((height / 2f - panY) / documentHeight / zoomValue).let { if (infiniteCanvas) it else it.coerceIn(0f, 1f) } else .5f
    val viewportSpanY: Float get() = height.toFloat() / documentHeight / zoomValue
    fun jumpTo(x: Float, y: Float, scale: Float = zoomValue) {
        finishGesture(); resetFrontInk(); zoomValue = scale.coerceIn(.1f, 5f)
        panX = width / 2f - x * width * zoomValue; panY = height / 2f - y * documentHeight * zoomValue
        constrainPan(); onZoomChanged(zoomValue); invalidate()
    }
    fun locateContent() {
        val points = strokes.flatMap { it.points }
        val xs = points.map { it.x } + texts.map { it.x } + images.map { it.x }
        val ys = points.map { it.y } + texts.map { it.y } + images.map { it.y }
        if (xs.isEmpty()) { resetViewport(); return }
        val left = xs.min(); val right = xs.max(); val top = ys.min(); val bottom = ys.max()
        val scale = minOf(.85f / (right-left).coerceAtLeast(.5f), height * .85f / ((bottom-top).coerceAtLeast(.5f) * documentHeight)).coerceIn(.1f, 2f)
        jumpTo((left+right)/2, (top+bottom)/2, scale)
    }

    fun flushInput() { finishGesture() }

    fun setZoom(value: Float) {
        finishGesture(); resetFrontInk()
        val old = zoomValue
        zoomValue = value.coerceIn(.25f, 5f)
        val factor = zoomValue / old
        panX = width / 2f - (width / 2f - panX) * factor
        panY = height / 2f - (height / 2f - panY) * factor
        constrainPan(); onZoomChanged(zoomValue); postInvalidateOnAnimation()
    }
    fun resetViewport() { finishGesture(); resetFrontInk(); zoomValue = 1f; panX = 0f; panY = 0f; onZoomChanged(1f); postInvalidateOnAnimation() }
    fun fitToScreen() = resetViewport()
    fun fitToWidth() = resetViewport()
    fun fitToHeight() = resetViewport()
    fun fitToCanvas() = resetViewport()
    fun panToFraction(x: Float, y: Float) {
        if (infiniteCanvas) { jumpTo(x, y); return }
        panX = (width * (1f - zoomValue) / 2f) - (x.coerceIn(0f, 1f) - .5f) * width * zoomValue
        panY = (height * (1f - zoomValue) / 2f) - (y.coerceIn(0f, 1f) - .5f) * height * zoomValue
        constrainPan(); onZoomChanged(zoomValue); postInvalidateOnAnimation()
    }
    private fun constrainPan() {
        if (infiniteCanvas) return
        if (zoomValue <= 1f) {
            panX = width * (1f - zoomValue) / 2f
            panY = height * (1f - zoomValue) / 2f
        } else {
            panX = panX.coerceIn(width * (1f - zoomValue), 0f)
            panY = panY.coerceIn(height * (1f - zoomValue), 0f)
        }
    }
    private fun editable(id: String) = layers.any { it.id == id && it.visible && !it.locked }
    private fun position(x: Float, y: Float) = Offset((x - panX) / zoomValue / width.coerceAtLeast(1), (y - panY) / zoomValue / documentHeight.coerceAtLeast(1))
    private fun gesturePosition(x: Float, y: Float): Offset {
        if (gestureMode == CanvasMode.Pan) return Offset(x, y)
        if (rulerSnapSide != 0 && ruler.visible) { val snapped = ruler.projectToEdge(x, y, rulerSnapSide); return position(snapped.x, snapped.y) }
        return position(x, y)
    }

    // --- Ruler: screen-space straightedge. Fingers move/rotate it; the pen snaps to its edge. ---
    internal val ruler = Ruler()
    private var rulerSnapSide = 0
    private var rulerMode = 0 // 0 none, 1 move, 2 rotate, 3 two-finger transform
    private var rulerGrab = Offset.Zero
    private var rulerRotateSign = 1f
    private var rulerStartAngle = 0f; private var rulerStartFinger = 0f; private var rulerStartCenter = Offset.Zero; private var rulerStartCentroid = Offset.Zero
    var rulerVisible: Boolean
        get() = ruler.visible
        set(value) {
            if (ruler.visible == value) return
            ruler.visible = value
            if (value && !ruler.initialized && width > 0) ruler.placeDefault(width, height, resources.displayMetrics.density)
            if (!value) { rulerMode = 0; rulerSnapSide = 0 }
            postInvalidateOnAnimation()
        }
    private fun rulerTolerance() = 22f * resources.displayMetrics.density
    private fun engageRuler(x: Float, y: Float) {
        rulerSnapSide = 0
        if (!ruler.visible || !(gestureMode == CanvasMode.Shape || (gestureMode == CanvasMode.Write && !activeBrush.eraser))) return
        val a = ruler.across(x, y); val half = ruler.thickness / 2f
        val toTop = abs(a - half); val toBottom = abs(a + half)
        if (minOf(toTop, toBottom) <= 24f * resources.displayMetrics.density) rulerSnapSide = if (toTop <= toBottom) 1 else -1
    }
    private fun handleRulerTouch(event: MotionEvent): Boolean {
        val tool = event.getToolType(event.actionIndex.coerceIn(0, event.pointerCount - 1))
        val pen = isStylus(tool)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x; val y = event.y
                val handle = ruler.hitHandle(x, y, ruler.handleDistance(width, height), rulerTolerance())
                val button = ruler.hitButton(x, y, rulerTolerance())
                when {
                    handle != null -> { if (!ruler.lockAngle) { rulerMode = 2; rulerRotateSign = if (handle == 0) 1f else -1f }; return true }
                    button != null -> { if (button == RulerButton.LockPosition) ruler.lockPosition = !ruler.lockPosition else ruler.lockAngle = !ruler.lockAngle; postInvalidateOnAnimation(); return true }
                    !pen && ruler.bodyContains(x, y) -> { if (!ruler.lockPosition) { rulerMode = 1; rulerGrab = Offset(ruler.cx - x, ruler.cy - y) }; return true }
                }
                return false
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (rulerMode == 1 && event.pointerCount == 2) {
                rulerMode = 3
                rulerStartAngle = ruler.angle; rulerStartCenter = Offset(ruler.cx, ruler.cy)
                rulerStartFinger = RulerRenderer.angleBetween(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                rulerStartCentroid = Offset((event.getX(0) + event.getX(1)) / 2f, (event.getY(0) + event.getY(1)) / 2f)
                return true
            } else return rulerMode != 0
            MotionEvent.ACTION_MOVE -> when (rulerMode) {
                1 -> { ruler.cx = event.x + rulerGrab.x; ruler.cy = event.y + rulerGrab.y; postInvalidateOnAnimation(); return true }
                2 -> {
                    var raw = RulerRenderer.angleBetween(ruler.cx, ruler.cy, event.x, event.y)
                    if (rulerRotateSign < 0) raw += PI.toFloat()
                    ruler.angle = Ruler.snapToAxes(raw); postInvalidateOnAnimation(); return true
                }
                3 -> if (event.pointerCount >= 2) {
                    if (!ruler.lockAngle) ruler.angle = Ruler.snapToAxes(rulerStartAngle + RulerRenderer.angleBetween(event.getX(0), event.getY(0), event.getX(1), event.getY(1)) - rulerStartFinger)
                    if (!ruler.lockPosition) {
                        ruler.cx = rulerStartCenter.x + (event.getX(0) + event.getX(1)) / 2f - rulerStartCentroid.x
                        ruler.cy = rulerStartCenter.y + (event.getY(0) + event.getY(1)) / 2f - rulerStartCentroid.y
                    }
                    postInvalidateOnAnimation(); return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> if (rulerMode == 3) {
                val remaining = if (event.actionIndex == 0) 1 else 0
                rulerMode = 1; rulerGrab = Offset(ruler.cx - event.getX(remaining), ruler.cy - event.getY(remaining)); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (rulerMode != 0) { rulerMode = 0; postInvalidateOnAnimation(); return true }
        }
        return rulerMode != 0
    }

    private fun resetFrontInk() { if (Build.VERSION.SDK_INT >= 29) frontInk?.reset() }
    private fun canUseFrontInk() = Build.VERSION.SDK_INT >= 29 && isHardwareAccelerated && frontInk != null &&
        !NaturalMediaRenderer.supports(activeBrush.name) &&
        mode == CanvasMode.Write && !activeBrush.eraser && editable(activeLayerId) &&
        layers.lastOrNull { it.visible }?.id == activeLayerId && texts.none { it.layerId == activeLayerId }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); loadImages()
        if (Build.VERSION.SDK_INT >= 29 && isHardwareAccelerated) {
            frontInk = FrontBufferedInkController(this, ink, {
                cachedPage = null
                contentRevision++
                // Ink removes its wet copy in this UI loop. Schedule the dry page
                // for the same traversal, without postInvalidateOnAnimation's delay.
                invalidate()
            }).also { it.attach() }
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (oldw != 0 && oldh != 0 && (w != oldw || h != oldh)) { finishGesture(); resetFrontInk() }
        super.onSizeChanged(w, h, oldw, oldh)
    }
    // All input belongs to our pressure filter; the authoring child only renders accepted samples.
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val pointerType = if (event.pointerCount > 0) event.getToolType(0) else MotionEvent.TOOL_TYPE_UNKNOWN
        val pointerIsStylus = isStylus(pointerType)
        val pointerIsMouse = pointerType == MotionEvent.TOOL_TYPE_MOUSE
        // In the stacked-page layout, let a finger gesture reach the parent
        // vertical scroller; stylus and mouse input remain captured by this page canvas.
        if (singleFingerVerticalSwipe && mode != CanvasMode.FreeZoom && event.pointerCount == 1 && !pointerIsStylus && !pointerIsMouse) return false
        return true
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(addPinRunnable)
        cancelLongPressPicker()
        finishGesture()
        if (Build.VERSION.SDK_INT >= 29) frontInk?.detach()
        frontInk = null
        cancelImageLoad?.invoke(); cancelImageLoad = null
        if (Build.VERSION.SDK_INT >= 29) pageCommands?.release()
        pageCommands = null
        ink.release(); imageBitmaps = emptyMap()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!backendReported) {
            android.util.Log.i("KnotesInk", "Jetpack Ink 1.1.0-alpha09; hardwareCanvas=${canvas.isHardwareAccelerated}; commandCache=${Build.VERSION.SDK_INT >= 29 && canvas.isHardwareAccelerated}")
            backendReported = true
        }
        canvas.save()
        // Keep the viewport in screen coordinates. Clipping after translate/scale
        // expands the clip with the page, so panned content can paint into the rails.
        canvas.clipRect(0, 0, width, height)
        if (infiniteCanvas) canvas.drawColor(com.allient.folio.InfinitePaper.parse(template)?.background ?: Color.WHITE)
        canvas.translate(panX, panY); canvas.scale(zoomValue, zoomValue)
        if (infiniteCanvas) NotebookRenderer.drawInfinitePattern(canvas, template, width.toFloat(), -panX / zoomValue, -panY / zoomValue, (width - panX) / zoomValue, (height - panY) / zoomValue, zoomValue)
        if (infiniteCanvas) NotebookRenderer.drawInfinitePattern(canvas, template, width.toFloat(), -panX / zoomValue, -panY / zoomValue, (width - panX) / zoomValue, (height - panY) / zoomValue, zoomValue)
        val page = cachedPage ?: NotebookPage(id = "render-cache", strokes = if (Build.VERSION.SDK_INT >= 29) strokes.filterNot { frontInk?.isPending(it.id) == true } else strokes, template = template,
            layers = layers, texts = texts, images = images, blocks = blocks).also { cachedPage = it }
        fun drawLive(target: Canvas) {
            if (editable(activeLayerId)) {
                if (activePoints.isNotEmpty() && (Build.VERSION.SDK_INT < 29 || frontInk?.hasActive != true)) ink.drawLive(target, width, documentHeight, zoomValue)
                shapeStroke()?.let { NotebookRenderer.drawStroke(target, it, width.toFloat(), documentHeight.toFloat()) }
            }
        }
        if (Build.VERSION.SDK_INT >= 29 && canvas.isHardwareAccelerated) {
            val commands = pageCommands ?: CompletedInkPageCache().also { pageCommands = it }
            commands.draw(canvas, page, contentRevision, width, documentHeight, zoomValue, imageBitmaps, context.filesDir, ink, activeLayerId, ::drawLive)
        } else {
            // Software/API 25–28 fallback preserves the same layer order and vector data.
            val index = layers.indexOfFirst { it.id == activeLayerId }.coerceAtLeast(0)
            NotebookRenderer.drawPage(canvas, page.copy(layers = layers.take(index + 1), texts = texts.filter { it.layerId != activeLayerId }),
                width, documentHeight, context.filesDir, allowImageDecode = false, ink = ink, viewportZoom = zoomValue, imageBitmaps = imageBitmaps)
            drawLive(canvas)
            NotebookRenderer.drawPage(canvas, page.copy(layers = layers.drop(index), strokes = strokes.filter { it.layerId != activeLayerId }, images = images.filter { it.layerId != activeLayerId }, blocks = blocks.filter { it.layerId != activeLayerId }),
                width, documentHeight, context.filesDir, allowImageDecode = false, ink = ink, viewportZoom = zoomValue, imageBitmaps = imageBitmaps, drawBackground = false)
        }
        selectionBounds()?.let {
            canvas.drawRect(it, selectionPaint)
            val radius = 7f * resources.displayMetrics.density / zoomValue
            val rotateY = it.top - ROTATE_HANDLE_DP * resources.displayMetrics.density / zoomValue
            canvas.drawLine(it.centerX(), it.top, it.centerX(), rotateY, selectionPaint)
            canvas.drawCircle(it.centerX(), rotateY, radius + 2f / zoomValue, selectionHandleBackground)
            canvas.drawCircle(it.centerX(), rotateY, radius, handlePaint)
            listOf(Offset(it.left, it.top), Offset(it.right, it.top), Offset(it.right, it.bottom), Offset(it.left, it.bottom)).forEach { handle ->
                canvas.drawCircle(handle.x, handle.y, radius + 2f / zoomValue, selectionHandleBackground)
                canvas.drawCircle(handle.x, handle.y, radius, handlePaint)
            }
        }
        if (mode == CanvasMode.Objects) blockContent.firstOrNull { it.id == selectedBlockId }?.let { block ->
            val bounds = blockBounds(block)
            canvas.drawRect(bounds, selectionPaint)
            canvas.drawCircle(bounds.right, bounds.bottom, 8f * resources.displayMetrics.density / zoomValue, handlePaint)
            val cells = selectedCells
            if (block.kind == InkBlock.Table && cells != null && cells.size == 4) {
                val rows = block.rows.ifEmpty { listOf(1f) }; val columns = block.columns.ifEmpty { listOf(1f) }
                val rowSum = rows.sum().coerceAtLeast(.001f); val columnSum = columns.sum().coerceAtLeast(.001f)
                val r0 = cells[0].coerceIn(0, rows.lastIndex); val r1 = cells[2].coerceIn(r0, rows.lastIndex)
                val c0 = cells[1].coerceIn(0, columns.lastIndex); val c1 = cells[3].coerceIn(c0, columns.lastIndex)
                val top = bounds.top + bounds.height() * rows.take(r0).sum() / rowSum; val bottom = bounds.top + bounds.height() * rows.take(r1 + 1).sum() / rowSum
                val left = bounds.left + bounds.width() * columns.take(c0).sum() / columnSum; val right = bounds.left + bounds.width() * columns.take(c1 + 1).sum() / columnSum
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x442A8CF0 }
                canvas.drawRect(left, top, right, bottom, fill); canvas.drawRect(left, top, right, bottom, selectionPaint)
            }
        }
        if (mode == CanvasMode.Objects) selectedObjectBounds()?.let {
            val xform = selectedObjectTransform()
            if (xform != null) {
                canvas.save(); canvas.rotate(xform.rotation, xform.px, xform.py)
                if (selectedObjectIsText) canvas.scale(if (xform.flipX) -1f else 1f, if (xform.flipY) -1f else 1f, xform.px, xform.py)
                val handleY = it.top - ROTATE_HANDLE_DP * resources.displayMetrics.density / zoomValue
                val handleRadius = 7f * resources.displayMetrics.density / zoomValue
                canvas.drawLine(it.centerX(), it.top, it.centerX(), handleY, selectionPaint)
                canvas.drawCircle(it.centerX(), handleY, handleRadius + 2f / zoomValue, selectionHandleBackground)
                canvas.drawCircle(it.centerX(), handleY, handleRadius, handlePaint)
            }
            canvas.drawRect(it, selectionPaint)
            if (!selectedObjectIsText || texts.any { it.id == selectedObjectId && it.kind != "plain" }) {
                canvas.drawCircle(it.right, it.bottom, 7f / zoomValue, handlePaint)
            }
            if (xform != null) canvas.restore()
        }
        if (lasso.isNotEmpty()) {
            val polygon = lassoPolygon()
            lassoPath.rewind(); lassoPath.moveTo(polygon.first().x * width, polygon.first().y * documentHeight)
            for (i in 1 until polygon.size) lassoPath.lineTo(polygon[i].x * width, polygon[i].y * documentHeight)
            lassoPath.close()
            canvas.drawPath(lassoPath, selectionPaint)
        }
        if (mode == CanvasMode.FreeZoom) {
            val start = freeZoomStart; val end = freeZoomEnd
            if (start != null && end != null) canvas.drawRect(
                minOf(start.x, end.x) * width, minOf(start.y, end.y) * documentHeight,
                maxOf(start.x, end.x) * width, maxOf(start.y, end.y) * documentHeight, freeZoomPaint)
        }
        zoomWindow?.let { drawZoomWindow(canvas, it) }
        eraserCursor?.takeIf { brush.eraser && eraserMode != EraserMode.Lasso }?.let {
            canvas.drawCircle(it.x * width, it.y * documentHeight, eraserWidth * width / 1200f, selectionPaint)
        }
        canvas.restore()
        if (ruler.visible) RulerRenderer.draw(canvas, ruler, width, height, resources.displayMetrics.density, resources.displayMetrics.xdpi / 2.54f * zoomValue)
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 0) return true
        if (ruler.visible && handleRulerTouch(event)) return true
        if (infiniteCanvas) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pinStart = Offset(event.x, event.y)
                    pinPending = mode in listOf(CanvasMode.Write, CanvasMode.Pan) && !brush.eraser && blankAt(position(event.x, event.y))
                    if (pinPending) postDelayed(addPinRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> if ((Offset(event.x, event.y)-pinStart).getDistance() > ViewConfiguration.get(context).scaledTouchSlop) { pinPending = false; removeCallbacks(addPinRunnable) }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pinPending = false; removeCallbacks(addPinRunnable) }
            }
        }
        if (mode == CanvasMode.ColorPicker) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val point = position(event.x, event.y)
                if (infiniteCanvas || (point.x in 0f..1f && point.y in 0f..1f)) {
                    val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                    try {
                        val sampleCanvas = Canvas(bitmap)
                        sampleCanvas.translate(-point.x * width, -point.y * documentHeight)
                        NotebookRenderer.drawPage(sampleCanvas, NotebookPage(strokes = strokes, texts = texts, images = images, layers = layers, template = template), width, documentHeight, context.filesDir, allowImageDecode = false, imageBitmaps = imageBitmaps)
                        onColorPicked(bitmap.getPixel(0, 0))
                    } finally { bitmap.recycle() }
                }
            }
            return true
        }
        if (mode == CanvasMode.ZoomWindow) return handleZoomWindowTouch(event)
        if (mode == CanvasMode.FreeZoom) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    finishGesture(); freeZoomStart = position(event.x, event.y); freeZoomEnd = freeZoomStart
                }
                MotionEvent.ACTION_MOVE -> if (freeZoomStart != null) {
                    freeZoomEnd = position(event.x, event.y); postInvalidateOnAnimation()
                }
                MotionEvent.ACTION_UP -> {
                    val start = freeZoomStart; val end = position(event.x, event.y)
                    freeZoomStart = null; freeZoomEnd = null
                    if (start != null) {
                        val left = minOf(start.x, end.x); val right = maxOf(start.x, end.x)
                        val top = minOf(start.y, end.y); val bottom = maxOf(start.y, end.y)
                        if (right - left > .025f && bottom - top > .025f) {
                            val factor = max(width / ((right - left) * width), height / ((bottom - top) * documentHeight))
                            zoomValue = (zoomValue * factor).coerceIn(.25f, 5f)
                            panX = width / 2f - ((left + right) / 2f) * width * zoomValue
                            panY = height / 2f - ((top + bottom) / 2f) * documentHeight * zoomValue
                            constrainPan(); onZoomChanged(zoomValue)
                        }
                    }
                    postInvalidateOnAnimation()
                }
                MotionEvent.ACTION_CANCEL -> { freeZoomStart = null; freeZoomEnd = null; postInvalidateOnAnimation() }
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.pointerCount == 1) {
            val point = position(event.x, event.y)
            val x = point.x * width; val y = point.y * documentHeight
            val hit = layers.asReversed().asSequence().filter { it.visible && !it.locked }.mapNotNull { layer ->
                texts.asReversed().firstOrNull { it.kind != "plain" && it.layerId == layer.id && textBounds(it).contains(x, y) }
            }.firstOrNull()
            if (hit != null) {
                finishGesture()
                embeddedTouchId = hit.id
                embeddedTouchStart = Offset(event.x, event.y)
                val bounds = textBounds(hit)
                embeddedTouchAction = hit.kind != "timestamp" && x >= bounds.right - bounds.height().coerceAtLeast(34f * resources.displayMetrics.density / zoomValue)
                if (!embeddedTouchAction) {
                    gestureMode = CanvasMode.Objects
                    lastPosition = point
                    beginObjectDrag(point)
                }
                return true
            }
        }
        embeddedTouchId?.let { id ->
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> if (!embeddedTouchAction) {
                    val point = position(event.x, event.y)
                    moveObject(point); lastPosition = point
                    postInvalidateOnAnimation()
                }
                MotionEvent.ACTION_UP -> {
                    val moved = hypot(event.x - embeddedTouchStart.x, event.y - embeddedTouchStart.y) > ViewConfiguration.get(context).scaledTouchSlop
                    if (embeddedTouchAction && !moved) texts.firstOrNull { it.id == id }?.let(onEmbeddedAction)
                    else if (!embeddedTouchAction) { publishObjects(); onLongPressSelection?.invoke(true) }
                    embeddedTouchId = null; embeddedTouchAction = false
                    postInvalidateOnAnimation()
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    publishObjects(); embeddedTouchId = null; embeddedTouchAction = false
                }
            }
            return true
        }
        val firstToolType = event.getToolType(0)
        val firstPointerIsMouse = firstToolType == MotionEvent.TOOL_TYPE_MOUSE
        if (singleFingerVerticalSwipe && event.pointerCount == 1 && !isStylus(firstToolType) && !firstPointerIsMouse) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fingerSwipeStartY = event.y; fingerSwipeLastY = event.y; fingerSwipeActive = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!fingerSwipeActive && kotlin.math.abs(event.y - fingerSwipeStartY) > 8f * resources.displayMetrics.density) fingerSwipeActive = true
                    if (fingerSwipeActive) onVerticalFingerScroll?.invoke(fingerSwipeLastY - event.y)
                    fingerSwipeLastY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> fingerSwipeActive = false
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.flags and MotionEvent.FLAG_CANCELED != 0) {
            cancelLongPressPicker()
            activePoints.clear(); ink.clearLive(); if (Build.VERSION.SDK_INT >= 29) frontInk?.cancelActive(); shapeStart = null; shapeEnd = null; lasso.clear(); movingSelection = false
            dragBaseline?.let { strokes = it }; dragBaseline = null
            val previousObjects = objectsBaseline; objectsBaseline = null
            if (previousObjects != null) { textContent = previousObjects.first; imageContent = previousObjects.second; contentChanged() }
            val previousEdits = editBaseline; editBaseline = null
            if (previousEdits != null) strokes = previousEdits
            lastEraserPoint = null; eraseGesture = false; eraserCursor = null
            draggingObject = false; resizingImage = false; rotatingObject = false; resizingSelection = false; rotatingSelection = false
            horizontalSwipeTracking = false; horizontalSwipeConsumed = false
            activePointerId = MotionEvent.INVALID_POINTER_ID; multitouch = false; postInvalidateOnAnimation(); return true
        }
        // Finger gestures navigate only. A palm landing beside an active pen must not move the page.
        val hasPen = (0 until event.pointerCount).any { isStylus(event.getToolType(it)) }
        if (!hasPen && pinchToZoomEnabled) scaler.onTouchEvent(event)
        if (!hasPen && event.pointerCount > 1) {
            val focus = multiTouchFocus(event)
            if (!multitouch) {
                finishGesture(); multitouch = true; previousMultiFocus = focus
            } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                val deltaX = focus.x - previousMultiFocus.x
                val deltaY = focus.y - previousMultiFocus.y
                previousMultiFocus = focus
                // In stacked-page/scroll mode, a two-finger vertical drag moves
                // through pages. Pinching and horizontal two-finger panning keep
                // their existing canvas behavior.
                val scrollThreshold = 2f * resources.displayMetrics.density
                if (!infiniteCanvas && twoFingerVerticalScroll && kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.15f && kotlin.math.abs(deltaY) > scrollThreshold) {
                    onVerticalFingerScroll?.invoke(-deltaY)
                } else {
                    panX += deltaX
                    panY += deltaY
                    constrainPan(); onZoomChanged(zoomValue); postInvalidateOnAnimation()
                }
            }
            return true
        }
        if (multitouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP) multitouch = false
            return true
        }
        val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
        val stylus = isStylus(event.getToolType(index))
        val mouse = event.getToolType(index) == MotionEvent.TOOL_TYPE_MOUSE
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                cancelLongPressPicker()
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && !stylus) return true
                finishGesture()
                // When zoomed, a single finger moves the page by default; the stylus
                // remains dedicated to writing. Pan mode can still move with either tool.
                val point = position(event.getX(index), event.getY(index))
                val bounds = selectionBounds()
                val selectionTool = mode == CanvasMode.Lasso || mode == CanvasMode.MoveSelection || mode == CanvasMode.Write || mode == CanvasMode.Shape
                val px = point.x * width; val py = point.y * documentHeight
                // A fixed 30dp hit radius can cover the entire selection on a dense
                // display. Keep a central drag region even for small/flat shapes.
                val threshold = minOf(30f * resources.displayMetrics.density / zoomValue,
                    bounds?.let { minOf(it.width(), it.height()) * .3f } ?: Float.MAX_VALUE)
                val handles = bounds?.let { b -> listOf(
                    Offset(b.left, b.top) to Offset(b.right, b.bottom), Offset(b.right, b.top) to Offset(b.left, b.bottom),
                    Offset(b.right, b.bottom) to Offset(b.left, b.top), Offset(b.left, b.bottom) to Offset(b.right, b.top),
                ) }.orEmpty()
                val handle = handles.minByOrNull { (corner, _) -> hypot(px - corner.x, py - corner.y) }
                resizingSelection = selectionTool && handle != null && hypot(px - handle.first.x, py - handle.first.y) <= threshold
                if (resizingSelection && handle != null) {
                    // Handles sit outside the ink; keep the opposite content corner fixed.
                    resizePivot = Offset(
                        if (handle.second.x < bounds!!.centerX()) bounds.left + 8f else bounds.right - 8f,
                        if (handle.second.y < bounds.centerY()) bounds.top + 8f else bounds.bottom - 8f)
                    resizeVector = Offset(px - resizePivot.x, py - resizePivot.y)
                }
                val rotateHandle = bounds?.let { Offset(it.centerX(), it.top - ROTATE_HANDLE_DP * resources.displayMetrics.density / zoomValue) }
                rotatingSelection = selectionTool && !resizingSelection && rotateHandle != null &&
                    hypot(px - rotateHandle.x, py - rotateHandle.y) <= 30f * resources.displayMetrics.density / zoomValue
                if (rotatingSelection && bounds != null) {
                    rotatePivot = Offset(bounds.centerX(), bounds.centerY())
                    rotateStartAngle = kotlin.math.atan2(py - rotatePivot.y, px - rotatePivot.x)
                }
                val selectionDrag = selectedStrokeIds.isNotEmpty() && selectionTool &&
                    (resizingSelection || rotatingSelection || mode == CanvasMode.MoveSelection || bounds?.contains(point.x * width, point.y * documentHeight) == true)
                val pageSwipeGesture = singlePageFingerSwipeEnabled && !fingerDrawing && mode == CanvasMode.Write && !brush.eraser
                val panByFinger = !stylus && !mouse && !fingerDrawing && !selectionDrag && (infiniteCanvas || zoomValue > 1.01f)
                if (!stylus && !mouse && !fingerDrawing && !selectionDrag && !pageSwipeGesture && mode != CanvasMode.Pan && !panByFinger) return true
                if (stylus || fingerDrawing) requestUnbufferedDispatch(event)
                activePointerId = event.getPointerId(index); activeBrush = brush
                gestureMode = if (selectionDrag) CanvasMode.MoveSelection else if (panByFinger) CanvasMode.Pan else mode
                horizontalSwipeTracking = pageSwipeGesture && !stylus && !mouse &&
                    mode == CanvasMode.Write && !activeBrush.eraser
                horizontalSwipeConsumed = false
                horizontalSwipeStart = Offset(event.getX(index), event.getY(index))
                // A hardware eraser must erase even if a shape or lasso was selected.
                if (event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER) {
                    activeBrush = brushes.first { it.eraser }; gestureMode = CanvasMode.Write
                }
                if ((gestureMode == CanvasMode.Write && activeBrush.eraser) || (gestureMode == CanvasMode.Shape && !selectionDrag)) { selectedStrokeIds = emptySet(); onSelectionChanged(emptySet()) }
                engageRuler(event.getX(index), event.getY(index))
                lastPosition = gesturePosition(event.getX(index), event.getY(index))
                lastSampleTime = event.eventTime
                // Page navigation is allowed with finger drawing off, but must
                // never start an ink segment (even for taps or vertical drags).
                if (horizontalSwipeTracking) return true
                if (gestureMode == CanvasMode.Pan) return true
                if (!pinPending && longPressSelectEnabled && fingerDrawing && !stylus && !mouse && event.pointerCount == 1 && gestureMode == CanvasMode.Write && !activeBrush.eraser) {
                    longPressPickerPending = true
                    longPressPickerStart = Offset(event.getX(index), event.getY(index))
                    postDelayed(longPressPickerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                if (gestureMode == CanvasMode.Objects) beginObjectDrag(lastPosition)
                if (gestureMode == CanvasMode.Lasso || gestureMode == CanvasMode.MoveSelection) {
                    movingSelection = selectionDrag
                    if (movingSelection) { dragBaseline = strokes; dragOrigin = lastPosition }
                    else if (gestureMode == CanvasMode.Lasso) { selectedStrokeIds = emptySet(); onSelectionChanged(emptySet()); lasso.clear() }
                }
                sample(lastPosition, if (stylus) event.getPressure(index) else 1f, event.eventTime,
                    if (stylus) event.getAxisValue(MotionEvent.AXIS_TILT, index) else 0f)
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPressPickerPending) {
                    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                    if (hypot(event.x - longPressPickerStart.x, event.y - longPressPickerStart.y) > touchSlop) cancelLongPressPicker()
                }
                val pointer = event.findPointerIndex(activePointerId)
                if (pointer < 0) return true
                if (horizontalSwipeTracking && !horizontalSwipeConsumed) {
                    val dx = event.getX(pointer) - horizontalSwipeStart.x
                    val dy = event.getY(pointer) - horizontalSwipeStart.y
                    val threshold = 56f * resources.displayMetrics.density
                    if (kotlin.math.abs(dx) >= threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.35f) {
                        horizontalSwipeConsumed = true; horizontalSwipeTracking = false
                        activePoints.clear(); ink.clearLive(); if (Build.VERSION.SDK_INT >= 29) frontInk?.cancelActive()
                        shapeStart = null; shapeEnd = null; activePointerId = MotionEvent.INVALID_POINTER_ID
                        onHorizontalFingerSwipe?.invoke(if (dx < 0f) 1 else -1)
                        postInvalidateOnAnimation(); return true
                    }
                    return true
                }
                for (history in 0 until event.historySize) sample(
                    gesturePosition(event.getHistoricalX(pointer, history), event.getHistoricalY(pointer, history)),
                    if (isStylus(event.getToolType(pointer))) event.getHistoricalPressure(pointer, history) else 1f,
                    event.getHistoricalEventTime(history),
                    if (isStylus(event.getToolType(pointer))) event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, pointer, history) else 0f,
                )
                sample(gesturePosition(event.getX(pointer), event.getY(pointer)), if (isStylus(event.getToolType(pointer))) event.getPressure(pointer) else 1f,
                    event.eventTime, if (isStylus(event.getToolType(pointer))) event.getAxisValue(MotionEvent.AXIS_TILT, pointer) else 0f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(index) == activePointerId) {
                cancelLongPressPicker()
                // ACTION_UP coordinates may jump while the pen leaves proximity; never append them.
                finishGesture()
                horizontalSwipeTracking = false; horizontalSwipeConsumed = false
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        postInvalidateOnAnimation(); return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 0 && isStylus(event.getToolType(0))) {
            if (activePointerId != MotionEvent.INVALID_POINTER_ID) return true
            eraserCursor = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) null else position(event.x, event.y)
            postInvalidateOnAnimation()
            return true
        }
        return super.onHoverEvent(event)
    }
    private fun isStylus(type: Int) = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
    private fun multiTouchFocus(event: MotionEvent): Offset {
        var x = 0f
        var y = 0f
        for (index in 0 until event.pointerCount) { x += event.getX(index); y += event.getY(index) }
        return Offset(x / event.pointerCount, y / event.pointerCount)
    }

    private fun sample(point: Offset, raw: Float, eventTime: Long, rawTilt: Float = 0f) {
        if (gestureMode == CanvasMode.Pan) {
            panX += point.x - lastPosition.x
            panY += point.y - lastPosition.y
            constrainPan()
            onZoomChanged(zoomValue)
            lastPosition = point
            return
        }
        val drawingContact = gestureMode == CanvasMode.Write || gestureMode == CanvasMode.Shape
        // Moving a selection is not drawing: pen pressure below the ink threshold must not end the drag.
        if ((drawingContact && (raw <= threshold || !raw.isFinite())) || (movingSelection && !raw.isFinite()) || !point.x.isFinite() || !point.y.isFinite() ||
            (!infiniteCanvas && !movingSelection && (point.x !in 0f..1f || point.y !in 0f..1f))) {
            // A faulty stylus can keep emitting MOVE after lift. Finish this drag once
            // and wait for a new contact instead of turning the remaining MOVE into a lasso.
            if (movingSelection) activePointerId = MotionEvent.INVALID_POINTER_ID
            commitSegment(); commitShape(); finishLasso(); movingSelection = false; publishDrag(); publishObjects(); publishEdits()
            lastEraserPoint = null; eraserCursor = null; resizingSelection = false; rotatingSelection = false
            return
        }
        val normalizedPressure = ((raw.coerceIn(0f, 1f) - threshold) / max(.0001f, 1f - threshold)).coerceIn(0f, 1f)
        var filteredPressure = if (pressureCurveEnabled) pressureCurve.value(normalizedPressure) else normalizedPressure
        val elapsed = (eventTime - lastSampleTime).coerceAtLeast(1L).toFloat()
        val distance = hypot((point.x - lastPosition.x) * width, (point.y - lastPosition.y) * width)
        val speed = (distance / elapsed / 1.5f).coerceIn(0f, 1f) // normalize around 1500 px/s
        if (velocityCurveEnabled) filteredPressure *= curveScale(speed, velocityCurve)
        val tilt = (rawTilt / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
        if (tiltCurveEnabled) filteredPressure *= curveScale(tilt, tiltCurve)
        filteredPressure = filteredPressure.coerceIn(0f, 1f)
        lastSampleTime = eventTime
        // Apply pen feel only after contact rejection. Store the mapped sample for identical replay.
        val pressure = activeBrush.mapPressure(filteredPressure)
        when (gestureMode) {
            CanvasMode.Write -> {
                if (activeBrush.eraser) {
                    eraseGesture = true; eraserCursor = point
                    if (eraserMode == EraserMode.Lasso) lasso += point
                    else {
                        if (editBaseline == null) editBaseline = strokes
                        strokes = InkGeometry.erase(strokes, lastEraserPoint ?: point, point,
                            eraserWidth * width / 1200f, width.toFloat(), documentHeight.toFloat(), eraserMode == EraserMode.Stroke, ::canErase)
                        lastEraserPoint = point
                    }
                } else if (editable(activeLayerId)) {
                    if (activePoints.isEmpty()) segmentStartedAt = eventTime
                    val elapsed = maxOf((eventTime - segmentStartedAt).coerceAtLeast(0), (activePoints.lastOrNull()?.elapsedMillis ?: -1L) + 1)
                    val accepted = InkPoint(point.x, point.y, pressure, elapsed)
                    activePoints += accepted
                    if (Build.VERSION.SDK_INT >= 29 && (frontInk?.hasActive == true || (activePoints.size == 1 && canUseFrontInk()))) {
                        frontInk?.append(accepted, activeBrush, width, documentHeight, zoomValue, panX, panY)
                    } else ink.append(accepted, activeBrush)
                }
            }
            CanvasMode.Shape -> if (editable(activeLayerId)) {
                if (shapeStart == null) shapeStart = point
                shapeEnd = point
            }
            CanvasMode.Lasso, CanvasMode.MoveSelection -> if (movingSelection) {
                val baseline = dragBaseline ?: return
                val dx = point.x - dragOrigin.x; val dy = point.y - dragOrigin.y
                // Translate from the initial snapshot; clamp the group without distortion
                // or cumulative drift, including when the pointer leaves the page.
                val selected = baseline.filter { it.id in selectedStrokeIds && editable(it.layerId) }.flatMap { it.points }
                if (selected.isNotEmpty()) {
                    val minX = selected.minOf { it.x }; val maxX = selected.maxOf { it.x }
                    val minY = selected.minOf { it.y }; val maxY = selected.maxOf { it.y }
                    if (rotatingSelection) {
                        val delta = kotlin.math.atan2(point.y * documentHeight - rotatePivot.y, point.x * width - rotatePivot.x) - rotateStartAngle
                        val cosine = kotlin.math.cos(delta); val sine = kotlin.math.sin(delta)
                        strokes = baseline.map { if (it.id in selectedStrokeIds && editable(it.layerId)) it.copy(points = it.points.map { p ->
                            val rx = p.x * width - rotatePivot.x; val ry = p.y * documentHeight - rotatePivot.y
                            val nx = (rotatePivot.x + rx * cosine - ry * sine) / width
                            val ny = (rotatePivot.y + rx * sine + ry * cosine) / documentHeight
                            p.copy(x = if (infiniteCanvas) nx else nx.coerceIn(0f, 1f), y = if (infiniteCanvas) ny else ny.coerceIn(0f, 1f))
                        }) else it }
                    } else if (resizingSelection) {
                        val current = Offset(point.x * width - resizePivot.x, point.y * documentHeight - resizePivot.y)
                        val baselineVector = resizeVector
                        val startLength = baselineVector.getDistance().coerceAtLeast(1f)
                        val scale = (current.getDistance() / startLength).coerceIn(.1f, 20f)
                        val minXPx = minX * width; val maxXPx = maxX * width
                        val minYPx = minY * documentHeight; val maxYPx = maxY * documentHeight
                        val xLimit = if (resizePivot.x <= minXPx) (width - resizePivot.x) / (maxXPx - resizePivot.x).coerceAtLeast(1f)
                            else resizePivot.x / (resizePivot.x - minXPx).coerceAtLeast(1f)
                        val yLimit = if (resizePivot.y <= minYPx) (height - resizePivot.y) / (maxYPx - resizePivot.y).coerceAtLeast(1f)
                            else resizePivot.y / (resizePivot.y - minYPx).coerceAtLeast(1f)
                        val boundedScale = if (infiniteCanvas) scale else scale.coerceAtMost(minOf(xLimit, yLimit, 20f).coerceAtLeast(.1f))
                        strokes = baseline.map { if (it.id in selectedStrokeIds && editable(it.layerId)) it.copy(
                            width = (it.width * boundedScale).coerceIn(.01f, 256f),
                            points = it.points.map { p ->
                                val nx = (resizePivot.x + (p.x * width - resizePivot.x) * boundedScale) / width
                                val ny = (resizePivot.y + (p.y * documentHeight - resizePivot.y) * boundedScale) / documentHeight
                                p.copy(x = if (infiniteCanvas) nx else nx.coerceIn(0f,1f), y = if (infiniteCanvas) ny else ny.coerceIn(0f,1f))
                            }) else it }
                    } else {
                        val boundedX = if (infiniteCanvas) dx else dx.coerceIn(-minX, 1f - maxX)
                        val boundedY = if (infiniteCanvas) dy else dy.coerceIn(-minY, 1f - maxY)
                        strokes = baseline.map { if (it.id in selectedStrokeIds && editable(it.layerId)) it.copy(points = it.points.map { p -> p.copy(x = p.x + boundedX, y = p.y + boundedY) }) else it }
                    }
                }
            } else if (gestureMode == CanvasMode.Lasso) lasso += point
            CanvasMode.Objects -> moveObject(point)
            CanvasMode.Fill -> if (editable(activeLayerId)) {
                val target = strokes.lastOrNull { stroke ->
                    stroke.layerId == activeLayerId && stroke.points.size >= 3 &&
                        hypot((stroke.points.first().x - stroke.points.last().x) * width, (stroke.points.first().y - stroke.points.last().y) * documentHeight) < 24f &&
                        InkGeometry.contains(point, stroke.points.map { Offset(it.x, it.y) })
                }
                if (target != null && (!target.filled || (target.fillColor ?: target.color) != fillColor)) {
                    if (editBaseline == null) editBaseline = strokes
                    strokes = strokes.map { if (it.id == target.id) it.copy(filled = true, fillColor = fillColor) else it }
                }
            }
            CanvasMode.Pan, CanvasMode.FreeZoom, CanvasMode.ColorPicker, CanvasMode.ZoomWindow -> Unit
        }
        lastPosition = point
    }
    private fun textBounds(text: InkText): RectF {
        if (text.kind != "plain") return RectF(text.x * width, text.y * documentHeight,
            (text.x + text.boxWidth.coerceIn(.08f, 1f)) * width,
            (text.y + (.055f * (text.size / 22f)).coerceIn(.035f, .16f)) * documentHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = text.size * width / 600f
            val family = when (text.font) { "Serif" -> android.graphics.Typeface.SERIF; "Mono" -> android.graphics.Typeface.MONOSPACE; else -> android.graphics.Typeface.SANS_SERIF }
            typeface = android.graphics.Typeface.create(family, (if (text.bold) android.graphics.Typeface.BOLD else 0) or (if (text.italic) android.graphics.Typeface.ITALIC else 0))
        }
        val lines = text.text.lines()
        val textWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val anchor = text.x * width
        val left = when (text.alignment) { "Center" -> anchor - textWidth / 2f; "End" -> anchor - textWidth; else -> anchor }
        val right = left + textWidth
        val lineHeight = paint.fontSpacing * text.lineSpacing.coerceIn(.6f, 2.5f)
        return RectF(left, text.y * documentHeight + paint.ascent(), right, text.y * documentHeight + paint.descent() + (lines.size - 1) * lineHeight)
    }
    private fun imageBounds(image: InkImage) = RectF(image.x * width, image.y * documentHeight, (image.x + image.width) * width, (image.y + image.height) * documentHeight)
    private class ObjectTransform(val px: Float, val py: Float, val rotation: Float, val flipX: Boolean, val flipY: Boolean)
    private fun selectedObjectTransform(): ObjectTransform? {
        if (selectedObjectIsText) {
            val t = texts.firstOrNull { it.id == selectedObjectId && it.kind == "plain" } ?: return null
            return ObjectTransform(t.x * width, t.y * documentHeight, t.rotation, t.flipX, t.flipY)
        }
        val i = images.firstOrNull { it.id == selectedObjectId } ?: return null
        return ObjectTransform((i.x + i.width / 2f) * width, (i.y + i.height / 2f) * documentHeight, i.rotation, i.flipX, i.flipY)
    }
    /** Maps a page point into an object's unrotated frame, so hit tests can use its plain bounds. */
    private fun toObjectFrame(x: Float, y: Float, px: Float, py: Float, rotation: Float, flipX: Boolean, flipY: Boolean): Offset {
        val a = -Math.toRadians(rotation.toDouble())
        val dx = x - px; val dy = y - py
        val rx = (dx * kotlin.math.cos(a) - dy * kotlin.math.sin(a)).toFloat()
        val ry = (dx * kotlin.math.sin(a) + dy * kotlin.math.cos(a)).toFloat()
        return Offset(px + if (flipX) -rx else rx, py + if (flipY) -ry else ry)
    }
    private fun selectedObjectBounds(): RectF? {
        return if (selectedObjectIsText) texts.firstOrNull { it.id == selectedObjectId && layers.any { l -> l.id == it.layerId && l.visible } }?.let(::textBounds)
        else images.firstOrNull { it.id == selectedObjectId && layers.any { l -> l.id == it.layerId && l.visible } }?.let(::imageBounds)
    }
    private fun beginObjectDrag(point: Offset) {
        val x = point.x * width; val y = point.y * documentHeight
        blockTouch = point
        draggingObject = false; resizingImage = false; rotatingObject = false; draggingBlock = false; resizingBlock = false
        blockContent.firstOrNull { it.id == selectedBlockId && editable(it.layerId) }?.let { block ->
            val bounds = blockBounds(block)
            if (hypot(x - bounds.right, y - bounds.bottom) < 24f * resources.displayMetrics.density / zoomValue) { startBlockDrag(block, resize = true); return }
        }
        // The rotation handle floats above a selected image or plain text box.
        selectedObjectTransform()?.let { xf ->
            val box = selectedObjectBounds() ?: return@let
            val local = toObjectFrame(x, y, xf.px, xf.py, xf.rotation, selectedObjectIsText && xf.flipX, selectedObjectIsText && xf.flipY)
            val handleY = box.top - ROTATE_HANDLE_DP * resources.displayMetrics.density / zoomValue
            if (hypot(local.x - box.centerX(), local.y - handleY) <= 30f * resources.displayMetrics.density / zoomValue) {
                rotatingObject = true; draggingObject = true; objectsBaseline = texts to images
                objectRotationBase = xf.rotation
                objectRotationStart = Math.toDegrees(kotlin.math.atan2((y - xf.py).toDouble(), (x - xf.px).toDouble())).toFloat()
                return
            }
        }
        // A selected image's corner handle is checked before choosing a new object.
        val selectedTextCard = texts.firstOrNull { it.id == selectedObjectId && it.kind != "plain" && editable(it.layerId) }
        if (selectedObjectIsText && selectedTextCard != null) {
            val bounds = textBounds(selectedTextCard)
            if (hypot(x - bounds.right, y - bounds.bottom) < 20f * resources.displayMetrics.density / zoomValue) {
                draggingObject = true; resizingTextCard = true; objectsBaseline = texts to images; return
            }
        }
        val selectedImage = images.firstOrNull { it.id == selectedObjectId && editable(it.layerId) }
        if (!selectedObjectIsText && selectedImage != null) {
            val bounds = imageBounds(selectedImage)
            val local = toObjectFrame(x, y, bounds.centerX(), bounds.centerY(), selectedImage.rotation, false, false)
            if (hypot(local.x - bounds.right, local.y - bounds.bottom) < 20f / zoomValue) {
                draggingObject = true; resizingImage = true; objectsBaseline = texts to images; return
            }
        }
        selectedObjectId = null; selectedBlockId = null
        for (layer in layers.asReversed().filter { it.visible && !it.locked }) {
            val text = texts.asReversed().firstOrNull { t ->
                val local = if (t.kind == "plain") toObjectFrame(x, y, t.x * width, t.y * documentHeight, t.rotation, t.flipX, t.flipY) else Offset(x, y)
                t.layerId == layer.id && textBounds(t).apply { inset(-8f, -8f) }.contains(local.x, local.y)
            }
            if (text != null) { selectedObjectId = text.id; selectedObjectIsText = true; break }
            val image = images.asReversed().firstOrNull { i ->
                val b = imageBounds(i)
                val local = toObjectFrame(x, y, b.centerX(), b.centerY(), i.rotation, false, false)
                i.layerId == layer.id && b.contains(local.x, local.y)
            }
            if (image != null) { selectedObjectId = image.id; selectedObjectIsText = false; break }
            val block = blockContent.asReversed().firstOrNull { it.layerId == layer.id && blockBounds(it).contains(x, y) }
            if (block != null) { selectedBlockId = block.id; break }
        }
        draggingObject = selectedObjectId != null
        onObjectSelectionChanged(selectedObjectId, selectedObjectIsText)
        onBlockSelectionChanged(selectedBlockId)
        if (draggingObject) objectsBaseline = texts to images
        blockContent.firstOrNull { it.id == selectedBlockId }?.let { startBlockDrag(it, resize = false) }
    }
    private fun moveObject(point: Offset) {
        if (draggingBlock) { moveBlock(point); return }
        if (!draggingObject) return
        if (rotatingObject) {
            val xf = selectedObjectTransform() ?: return
            val now = Math.toDegrees(kotlin.math.atan2((point.y * documentHeight - xf.py).toDouble(), (point.x * width - xf.px).toDouble())).toFloat()
            var angle = ((objectRotationBase + now - objectRotationStart) % 360f + 360f) % 360f
            // Settle on the straight angles so a quick turn lines up without effort.
            val straight = kotlin.math.round(angle / 90f) * 90f
            if (kotlin.math.abs(angle - straight) < 4f) angle = straight % 360f
            if (selectedObjectIsText) textContent = texts.map { if (it.id == selectedObjectId && editable(it.layerId)) it.copy(rotation = angle) else it }
            else imageContent = images.map { if (it.id == selectedObjectId && editable(it.layerId)) it.copy(rotation = angle) else it }
            contentChanged(); return
        }
        val dx = point.x - lastPosition.x; val dy = point.y - lastPosition.y
        if (selectedObjectIsText) {
            textContent = texts.map { text ->
                if (text.id != selectedObjectId || !editable(text.layerId)) text else {
                    if (infiniteCanvas && !resizingTextCard) return@map text.copy(x = text.x + dx, y = text.y + dy)
                    if (resizingTextCard && text.kind != "plain") return@map text.copy(
                        boxWidth = (text.boxWidth + dx).coerceIn(.08f, if (infiniteCanvas) 1f else (1f - text.x).coerceAtLeast(.08f)),
                        size = (text.size + dy * 22f / .055f).coerceIn(14f, 64f))
                    if (text.kind != "plain") return@map text.copy(
                        x = (text.x + dx).coerceIn(0f, (1f - text.boxWidth).coerceAtLeast(0f)),
                        y = (text.y + dy).coerceIn(0f, (1f - (.055f * (text.size / 22f)).coerceIn(.035f, .16f)).coerceAtLeast(0f)))
                    val bounds = textBounds(text)
                    // Baseline coordinates differ from the glyph bounds; keep the visible glyphs on the page.
                    val minY = (text.y - bounds.top / height).coerceIn(0f, 1f)
                    val maxY = (text.y + 1f - bounds.bottom / height).coerceIn(minY, 1f)
                    text.copy(x = (text.x + dx).coerceIn(0f, (1f - bounds.width() / width).coerceAtLeast(0f)), y = (text.y + dy).coerceIn(minY, maxY))
                }
            }
        } else {
            imageContent = images.map { image ->
                if (image.id != selectedObjectId || !editable(image.layerId)) image
                else if (infiniteCanvas && !resizingImage) image.copy(x = image.x + dx, y = image.y + dy)
                else if (resizingImage) image.copy(width = (image.width + localDelta(dx, dy, image.rotation).x).coerceIn(.02f, if (infiniteCanvas) 1f else (1f - image.x).coerceAtLeast(.02f)), height = (image.height + localDelta(dx, dy, image.rotation).y).coerceIn(.02f, if (infiniteCanvas) 1f else (1f - image.y).coerceAtLeast(.02f)))
                else image.copy(x = (image.x + dx).coerceIn(0f, (1f - image.width).coerceAtLeast(0f)), y = (image.y + dy).coerceIn(0f, (1f - image.height).coerceAtLeast(0f)))
            }
        }
        contentChanged()
    }
    /** A drag delta in page space, expressed along an object's rotated axes (in page-normalised units). */
    private fun localDelta(dx: Float, dy: Float, rotation: Float): Offset {
        val a = -Math.toRadians(rotation.toDouble())
        val px = dx * width; val py = dy * documentHeight
        return Offset(((px * kotlin.math.cos(a) - py * kotlin.math.sin(a)) / width).toFloat(), ((px * kotlin.math.sin(a) + py * kotlin.math.cos(a)) / documentHeight).toFloat())
    }
    private fun publishObjects() {
        val previous = objectsBaseline
        objectsBaseline = null; draggingObject = false; resizingImage = false; rotatingObject = false; resizingTextCard = false
        publishBlocks()
        if (previous != null && (previous.first != texts || previous.second != images)) onObjectsChanged(texts, images)
    }
    private fun makeStroke(points: List<InkPoint>) = InkStroke(activeBrush.name, activeBrush.color, activeBrush.width, points, layerId = activeLayerId, engine = JetpackInkEngine.FORMAT,
        texture = activeBrush.texture, grain = activeBrush.grain, angle = activeBrush.angle, variant = activeBrush.variant)
    private fun commitSegment() {
        if (activePoints.isNotEmpty()) {
            val committed = makeStroke(activePoints.toList())
            if (Build.VERSION.SDK_INT >= 29 && frontInk?.hasActive == true) frontInk?.finish(committed)
            else ink.finish(committed)
            activePoints.clear()
            strokes = strokes + committed; onStrokesChanged(strokes)
        }
    }
    private fun finishGesture() {
        commitSegment(); commitShape(); finishLasso(); publishDrag(); publishObjects(); movingSelection = false
        activePointerId = MotionEvent.INVALID_POINTER_ID; lastEraserPoint = null; eraserCursor = null
        publishEdits(); resizingSelection = false; rotatingSelection = false; rulerSnapSide = 0; postInvalidateOnAnimation()
    }
    private fun publishDrag() { if (dragBaseline != null) { dragBaseline = null; onStrokesChanged(strokes) } }
    private fun shapeStroke(): InkStroke? {
        val start = shapeStart ?: return null
        val end = shapeEnd ?: return null
        val outline = ShapeGeometry.points(shapeKind, start, end, width.toFloat(), documentHeight.toFloat(), unbounded = infiniteCanvas)
        return makeStroke(outline.map { InkPoint(it.x, it.y, 1f) })
            .copy(engine = "shape-v1", brush = "Fineliner", color = shapeColor, width = shapeWidth,
                lineStyle = shapeLineStyle.name.lowercase(), filled = shapeFilled && shapeKind.closed)
    }
    private fun commitShape() {
        val stroke = shapeStroke()
        shapeStart = null; shapeEnd = null
        if (stroke != null) {
            strokes = strokes + stroke; onStrokesChanged(strokes)
            // Select the new shape so its handles (resize, rotate) and the mirror/rotate bar show at once.
            selectedStrokeIds = setOf(stroke.id); onSelectionChanged(selectedStrokeIds)
        }
    }
    private fun canErase(stroke: InkStroke) = editable(stroke.layerId) && (!eraseActiveLayerOnly || stroke.layerId == activeLayerId)
    private fun lassoPolygon(): List<Offset> {
        if (lassoKind != LassoKind.Rectangle || lasso.size < 2 || (gestureMode == CanvasMode.Write && activeBrush.eraser)) return lasso
        val a = lasso.first(); val b = lasso.last()
        return listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y))
    }
    private fun finishLasso() {
        val polygon = lassoPolygon()
        val erase = gestureMode == CanvasMode.Write && activeBrush.eraser && eraserMode == EraserMode.Lasso
        if (polygon.size >= 3) {
            val ids = strokes.filter { (if (erase) canErase(it) else editable(it.layerId)) && InkGeometry.intersects(it, polygon) }.map { it.id }.toSet()
            if (erase && ids.isNotEmpty()) {
                if (editBaseline == null) editBaseline = strokes
                strokes = strokes.filterNot { it.id in ids }
            } else if (!erase) { selectedStrokeIds = ids; onSelectionChanged(ids) }
        }
        lasso.clear()
    }
    private fun publishEdits() {
        val previous = editBaseline
        val erased = eraseGesture
        editBaseline = null; eraseGesture = false
        if (previous != null && previous != strokes) onStrokesChanged(strokes)
        if (erased) onEraseFinished()
    }
    private fun selectionBounds(): RectF? {
        val points = strokes.filter { it.id in selectedStrokeIds && layers.any { l -> l.id == it.layerId && l.visible } }.flatMap { it.points }
        if (points.isEmpty()) return null
        return RectF(points.minOf { it.x } * width - 8, points.minOf { it.y } * documentHeight - 8, points.maxOf { it.x } * width + 8, points.maxOf { it.y } * documentHeight + 8)
    }
}
