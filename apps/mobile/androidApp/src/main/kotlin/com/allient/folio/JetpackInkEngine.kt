package com.allient.folio

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import android.graphics.Matrix
import android.util.LruCache
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushCoat
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushPaint
import androidx.ink.brush.BrushTip
import androidx.ink.brush.behavior.SourceNode
import androidx.ink.brush.behavior.TargetNode
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke

/** Ink geometry in stable document units; input is already pressure-filtered by InkCanvasView. */
internal class JetpackInkEngine {
    // API 34+ defaults to android.graphics.Mesh; drawing finished strokes that way crashes
    // hwui's MeshOp on early Android 14 builds (MNP1095). Ink's wet layer is unaffected.
    // forcePathRendering is library-restricted but required for the MNP1095 crash fix above.
    @SuppressLint("RestrictedApi")
    private val renderer = CanvasStrokeRenderer.create(forcePathRendering = true)
    private val transform = Matrix()
    private data class BrushKey(val style: BrushStyle, val format: String)
    private val brushes = object : LruCache<BrushKey, Brush>(32) {}
    // Bound native geometry by input count as well as the number of stroke objects.
    private data class Cached(val source: InkStroke, val ink: Stroke)
    private val completed = object : LruCache<String, Cached>(40_000) {
        override fun sizeOf(key: String, value: Cached) = value.source.points.size.coerceAtLeast(256)
    }
    private val batch = MutableStrokeInputBatch()
    private val empty = MutableStrokeInputBatch()
    private var live = InProgressStroke()
    private var liveStyle: BrushStyle? = null
    private var elapsed = -1L
    private val natural = NaturalMediaRenderer()
    private val livePoints = mutableListOf<InkPoint>()
    // Translucent v5 ink: finished passes of the live stroke, the current pass's samples and times, and the splitter.
    private val livePasses = mutableListOf<Pass>()
    private val passPoints = mutableListOf<InkPoint>()
    private val passTimes = mutableListOf<Long>()
    private var splitter: InkPassSplitter? = null
    private var passOffset = 0 // stroke index of passPoints[0]

    fun brushFor(style: BrushStyle, format: String = FORMAT): Brush {
        val key = BrushKey(style, format)
        return brushes[key] ?: Brush.createWithColorIntArgb(
            family = if (current(format) && style.name in listOf("Calligraphy", "Fountain pen")) {
                BrushFamily(tip = BrushTip(scaleX = if (style.name == "Calligraphy") .2f else .5f, scaleY = 1f, rotationDegrees = if (style.variant.isBlank()) -35f else style.angle,
                    behaviors = listOf(pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .8f, 1.1f))),
                    inputModel = StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1).inputModel)
            } else if ((current(format) || format == V3_FORMAT) && style.name in naturalFamilies) {
                naturalFamilies.getValue(style.name)
            } else if (format == V2_FORMAT && style.name in expressiveFamilies) {
                expressiveFamilies.getValue(style.name)
            } else when (style.name) {
                "Fineliner", "Marker" -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
                "Gel pen" -> if (current(format)) StockBrushes.marker(StockBrushes.MarkerVersion.V1) else StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1)
                "Highlighter" -> StockBrushes.highlighter(version = StockBrushes.HighlighterVersion.V1)
                else -> StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1)
            }, colorIntArgb = style.color, size = style.width.coerceAtLeast(.1f), epsilon = .1f,
        ).also { brushes.put(key, it) }
    }

    fun inputPressure(point: InkPoint, style: BrushStyle, format: String = FORMAT): Float =
        // v1 squared Brush pen pressure. Keep that mapping only when replaying old ink.
        (if (format == LEGACY_FORMAT && style.name == "Brush pen") point.pressure * point.pressure
        else point.pressure).coerceIn(0f, 1f)

    private fun MutableStrokeInputBatch.append(point: InkPoint, time: Long, style: BrushStyle, format: String = FORMAT) {
        add(InputToolType.STYLUS, point.x * DOCUMENT_WIDTH, point.y * DOCUMENT_HEIGHT, time,
            pressure = inputPressure(point, style, format))
    }

    fun append(point: InkPoint, style: BrushStyle) {
        if (NaturalMediaRenderer.supports(style.name)) { liveStyle = style; livePoints += point; return }
        if (liveStyle == null) {
            val passes = drawsInPasses(style, FORMAT)
            // The current pass is drawn opaque in a layer that applies the alpha once, like each saved pass.
            live.start(brushFor(if (passes) opaque(style) else style)); liveStyle = style; elapsed = -1L
            splitter = if (passes) InkPassSplitter(passStep(style.width)) else null
        }
        elapsed = maxOf(elapsed + 1, point.elapsedMillis)
        val passSplitter = splitter
        if (passSplitter == null) { batch.append(point, elapsed, style); return }
        passPoints += point; passTimes += elapsed
        val start = passSplitter.add(point.x * DOCUMENT_WIDTH, point.y * DOCUMENT_HEIGHT)
        val local = start - passOffset
        if (start < 0 || local <= 0) { batch.append(point, elapsed, style); return }
        // A pass ended at an earlier sample: save it with the real colour and restart the live pass from that sample.
        livePasses += Pass(passStroke(passPoints.subList(0, local + 1), passTimes.subList(0, local + 1), style, FORMAT),
            if (passOffset > 0) passPoints[0] else null)
        val keptPoints = passPoints.subList(local, passPoints.size).toList(); val keptTimes = passTimes.subList(local, passTimes.size).toList()
        passPoints.clear(); passPoints += keptPoints; passTimes.clear(); passTimes += keptTimes
        passOffset = start
        batch.clear(); live.start(brushFor(opaque(style)))
        for (index in keptPoints.indices) batch.append(keptPoints[index], keptTimes[index], style)
    }

    /** One pass of a translucent stroke; [joint] is the sample it shares with the previous pass. */
    private data class Pass(val stroke: Stroke, val joint: InkPoint?)
    private val jointPath = android.graphics.Path()

    /**
     * Two passes meet with overlapping round ends, which left a dark dot at every turn. A pass is drawn without the
     * disc around the sample it shares with the previous pass, so the turn is inked once; real overlaps still darken.
     */
    private fun clipJoint(canvas: Canvas, joint: InkPoint?, width: Float) {
        if (joint == null) return
        jointPath.rewind()
        jointPath.addCircle(joint.x * DOCUMENT_WIDTH, joint.y * DOCUMENT_HEIGHT, width * .55f, android.graphics.Path.Direction.CW)
        if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutPath(jointPath)
        else @Suppress("DEPRECATION") canvas.clipPath(jointPath, android.graphics.Region.Op.DIFFERENCE)
    }

    private fun drawPass(canvas: Canvas, pass: Pass, width: Float) {
        if (pass.joint == null) { renderer.draw(canvas, pass.stroke, transform); return }
        canvas.save(); clipJoint(canvas, pass.joint, width); renderer.draw(canvas, pass.stroke, transform); canvas.restore()
    }

    private var passNode: Any? = null // RenderNode (API 29+) holding the live stroke's finished passes
    private var passNodeKey: List<Any> = emptyList()

    @RequiresApi(29)
    private fun livePassNode(width: Int, height: Int, zoom: Float, inkWidth: Float): RenderNode {
        val node = passNode as? RenderNode ?: RenderNode("Folio live passes").also { passNode = it }
        val key = listOf(livePasses.size, width, height, zoom)
        if (key != passNodeKey || !node.hasDisplayList()) {
            // Generous bounds: a render node clips, and infinite-canvas ink can sit outside the first sheet.
            node.setPosition(-width * 8, -height * 8, width * 9, height * 9)
            val recording = node.beginRecording()
            try {
                recording.translate(width * 8f, height * 8f)
                recording.scale(width / DOCUMENT_WIDTH, height / DOCUMENT_HEIGHT)
                transform.setScale(width / DOCUMENT_WIDTH * zoom, height / DOCUMENT_HEIGHT * zoom)
                for (pass in livePasses) drawPass(recording, pass, inkWidth)
            } finally { node.endRecording() }
            passNodeKey = key
        }
        return node
    }

    /** True when the live stroke must be drawn here, in passes, instead of by Ink's front-buffered wet layer. */
    fun drawsLiveInPasses(style: BrushStyle) = drawsInPasses(style, FORMAT)

    private fun opaque(style: BrushStyle) = style.copy(color = style.color or (0xFF shl 24))
    private fun passStep(width: Float) = (width * .6f).coerceAtLeast(1f)

    /** Translucent single-coat v5 ink is drawn in passes (see [InkPassSplitter]). */
    private fun drawsInPasses(style: BrushStyle, format: String) = format == FORMAT && !NaturalMediaRenderer.supports(style.name) &&
        android.graphics.Color.alpha(style.color) < 255 && brushFor(style, format).family.coats.size == 1

    private fun passStroke(points: List<InkPoint>, times: List<Long>, style: BrushStyle, format: String): Stroke {
        val inputs = MutableStrokeInputBatch()
        for (index in points.indices) inputs.append(points[index], times[index], style, format)
        return Stroke(brushFor(style, format), inputs)
    }

    private fun updateLive() {
        if (!batch.isEmpty()) { live.enqueueInputs(batch, empty); batch.clear() }
        if (live.isUpdateNeeded()) live.updateShape()
    }

    fun drawLive(canvas: Canvas, width: Int, height: Int, zoom: Float) {
        if (liveStyle == null) return
        if (NaturalMediaRenderer.supports(liveStyle!!.name)) {
            canvas.save(); canvas.scale(width / DOCUMENT_WIDTH, height / DOCUMENT_HEIGHT)
            natural.draw(canvas, livePoints, liveStyle!!); canvas.restore(); return
        }
        updateLive()
        canvas.save()
        canvas.scale(width / DOCUMENT_WIDTH, height / DOCUMENT_HEIGHT)
        transform.setScale(width / DOCUMENT_WIDTH * zoom, height / DOCUMENT_HEIGHT * zoom)
        if (splitter == null) renderer.draw(canvas, live, transform)
        else {
            val style = liveStyle!!
            // Finished passes are recorded once, not redrawn for every frame of a fast stroke.
            if (Build.VERSION.SDK_INT >= 29 && canvas.isHardwareAccelerated) {
                val node = livePassNode(width, height, zoom, style.width)
                canvas.restore(); canvas.drawRenderNode(node); canvas.save(); canvas.scale(width / DOCUMENT_WIDTH, height / DOCUMENT_HEIGHT)
            } else for (pass in livePasses) drawPass(canvas, pass, style.width)
            canvas.save(); if (passOffset > 0) clipJoint(canvas, passPoints.firstOrNull(), style.width)
            canvas.saveLayerAlpha(null, android.graphics.Color.alpha(style.color))
            renderer.draw(canvas, live, transform)
            canvas.restore(); canvas.restore()
        }
        canvas.restore()
    }

    fun cacheCompleted(source: InkStroke, stroke: Stroke) { completed.put(source.id, Cached(source, stroke)) }

    fun finish(source: InkStroke) {
        if (liveStyle == null) return
        if (NaturalMediaRenderer.supports(liveStyle!!.name)) { clearLive(); return }
        updateLive(); live.finishInput(); live.updateShape()
        // The live pass used an opaque brush, so a stroke in passes keeps the passes built while drawing and adds its
        // last pass with the real colour; rebuilding the whole stroke on lift made its end appear late.
        if (splitter == null) completed.put(source.id, Cached(source, live.toImmutable()))
        else if (passPoints.isNotEmpty()) {
            val last = Pass(passStroke(passPoints, passTimes, liveStyle!!, FORMAT), if (passOffset > 0) passPoints[0] else null)
            passCache.put(source.id, Passes(source, livePasses + last))
        }
        clearLive()
    }

    // start() resets the native stroke on reuse. clear() is restricted in Ink 1.0.
    fun clearLive() { batch.clear(); livePoints.clear(); liveStyle = null; elapsed = -1L; livePasses.clear(); passPoints.clear(); passTimes.clear(); splitter = null; passOffset = 0; passNodeKey = emptyList() }

    fun draw(canvas: Canvas, source: InkStroke, width: Float, height: Float, zoom: Float = 1f) {
        if (source.points.isEmpty()) return
        if (current(source.engine) && NaturalMediaRenderer.supports(source.brush)) {
            canvas.save(); canvas.scale(width / DOCUMENT_WIDTH, height / DOCUMENT_HEIGHT)
            natural.draw(canvas, source.points, BrushStyle(source.brush, source.width, source.color,
                texture = source.texture, grain = source.grain, angle = source.angle, variant = source.variant))
            canvas.restore(); return
        }
        val style = BrushStyle(source.brush, source.width, source.color, angle = source.angle)
        if (drawsInPasses(style, source.engine)) {
            canvas.save()
            canvas.scale(width / DOCUMENT_WIDTH, height / DOCUMENT_HEIGHT)
            transform.setScale(width / DOCUMENT_WIDTH * zoom, height / DOCUMENT_HEIGHT * zoom)
            for (pass in passes(source, style)) drawPass(canvas, pass, source.width)
            canvas.restore(); return
        }
        val cached = completed[source.id]
        val ink = if (cached != null && (cached.source === source || cached.source == source)) cached.ink else {
            val inputs = MutableStrokeInputBatch()
            var time = -1L
            for (point in source.points) { time = maxOf(time + 1, point.elapsedMillis); inputs.append(point, time, style, source.engine) }
            Stroke(brushFor(style, source.engine), inputs).also { completed.put(source.id, Cached(source, it)) }
        }
        canvas.save()
        canvas.scale(width / DOCUMENT_WIDTH, height / DOCUMENT_HEIGHT)
        transform.setScale(width / DOCUMENT_WIDTH * zoom, height / DOCUMENT_HEIGHT * zoom)
        renderer.draw(canvas, ink, transform)
        canvas.restore()
    }

    /**
     * Builds the native stroke for [source] now (on a worker), so its first draw on the UI thread only renders. The
     * reader calls this while it projects a page's marks; the result lands in the same caches [draw] uses.
     */
    fun prepare(source: InkStroke) {
        if (source.points.isEmpty()) return
        if (current(source.engine) && NaturalMediaRenderer.supports(source.brush)) return
        val style = BrushStyle(source.brush, source.width, source.color, angle = source.angle)
        if (drawsInPasses(style, source.engine)) { passes(source, style); return }
        val cached = completed[source.id]
        if (cached != null && (cached.source === source || cached.source == source)) return
        val inputs = MutableStrokeInputBatch()
        var time = -1L
        for (point in source.points) { time = maxOf(time + 1, point.elapsedMillis); inputs.append(point, time, style, source.engine) }
        completed.put(source.id, Cached(source, Stroke(brushFor(style, source.engine), inputs)))
    }

    private data class Passes(val source: InkStroke, val strokes: List<Pass>)
    private val passCache = object : LruCache<String, Passes>(40_000) {
        override fun sizeOf(key: String, value: Passes) = value.source.points.size.coerceAtLeast(256)
    }

    private fun passes(source: InkStroke, style: BrushStyle): List<Pass> {
        passCache[source.id]?.let { if (it.source === source || it.source == source) return it.strokes }
        val points = source.points
        val times = ArrayList<Long>(points.size)
        var time = -1L
        for (point in points) { time = maxOf(time + 1, point.elapsedMillis); times += time }
        val starts = inkPassStarts(FloatArray(points.size) { points[it].x * DOCUMENT_WIDTH }, FloatArray(points.size) { points[it].y * DOCUMENT_HEIGHT },
            passStep(source.width))
        val strokes = starts.indices.map { index ->
            val end = if (index + 1 < starts.size) starts[index + 1] + 1 else points.size
            Pass(passStroke(points.subList(starts[index], end), times.subList(starts[index], end), style, source.engine),
                if (index > 0) points[starts[index]] else null)
        }
        return strokes.also { passCache.put(source.id, Passes(source, it)) }
    }

    fun release() { clearLive(); live = InProgressStroke(); completed.evictAll(); passCache.evictAll(); brushes.evictAll() }

    companion object {
        const val LEGACY_FORMAT = "jetpack-ink-v1"
        const val V2_FORMAT = "jetpack-ink-v2"
        const val V3_FORMAT = "jetpack-ink-v3"
        const val V4_FORMAT = "jetpack-ink-v4"
        /** v5 = v4 plus translucent strokes drawn in passes, so they darken where they cross themselves. */
        const val FORMAT = "jetpack-ink-v5"
        fun supports(format: String) = format in listOf(FORMAT, V4_FORMAT, V3_FORMAT, V2_FORMAT, LEGACY_FORMAT)
        /** v4 and v5 share brushes; v5 only changes how translucent strokes are composited. */
        fun current(format: String) = format == FORMAT || format == V4_FORMAT

        private fun pressureBehavior(target: TargetNode.Target, low: Float, high: Float) = BrushBehavior(
            listOf(TargetNode(target = target, targetModifierRangeStart = low,
                targetModifierRangeEnd = high,
                input = SourceNode(SourceNode.Source.NORMALIZED_PRESSURE, 0f, 1f)))
        )

        // Each coat is rendered by Ink for both live strokes and saved/exported pages.
        // The pencil combines a light continuous graphite trace with sparse pigment particles.
        private val naturalFamilies: Map<String, BrushFamily> by lazy {
            val model = StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1).inputModel
            mapOf(
                "Pen" to BrushFamily(
                    tip = BrushTip(behaviors = listOf(pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .28f, 1.45f))),
                    inputModel = model, developerComment = "Folio v3 smooth pressure pen"),
                "Pencil" to BrushFamily(
                    coats = listOf(
                        BrushCoat(
                            tip = BrushTip(scaleX = 1.1f, scaleY = .72f, rotationDegrees = -22f,
                                behaviors = listOf(
                                    pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .24f, 1.25f),
                                    pressureBehavior(TargetNode.Target.OPACITY_MULTIPLIER, .35f, .9f))),
                            paint = BrushPaint(colorFunctions = listOf(BrushPaint.ColorFunction.OpacityMultiplier(.55f)))),
                        BrushCoat(
                            tip = BrushTip(scaleX = .53f, scaleY = .42f, rotationDegrees = -22f,
                                particleGapDistanceScale = .42f,
                                behaviors = listOf(
                                    pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .32f, 1.45f),
                                    pressureBehavior(TargetNode.Target.OPACITY_MULTIPLIER, .3f, 1f))),
                            paint = BrushPaint(colorFunctions = listOf(BrushPaint.ColorFunction.OpacityMultiplier(.42f))))
                    ), inputModel = model, developerComment = "Folio v3 layered graphite pencil"),
                "Fountain pen" to BrushFamily(
                    tip = BrushTip(scaleX = .55f, scaleY = 1.35f, rotationDegrees = -35f,
                        behaviors = listOf(pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .3f, 1.65f))),
                    inputModel = model, developerComment = "Folio v3 angled fountain nib"),
                "Brush pen" to BrushFamily(
                    coats = listOf(
                        BrushCoat(tip = BrushTip(scaleX = 1.12f, scaleY = .86f, cornerRounding = 1f,
                            behaviors = listOf(
                                pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .12f, 2.2f),
                                pressureBehavior(TargetNode.Target.OPACITY_MULTIPLIER, .58f, 1f))),
                            paint = BrushPaint()),
                        BrushCoat(tip = BrushTip(scaleX = .28f, scaleY = 1.05f, rotationDegrees = -12f,
                            particleGapDistanceScale = .18f,
                            behaviors = listOf(pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .15f, 2.1f))),
                            paint = BrushPaint(colorFunctions = listOf(BrushPaint.ColorFunction.OpacityMultiplier(.24f))))
                    ), inputModel = model, developerComment = "Folio v3 flexible brush with subtle fiber dabs"),
            )
        }

        // Artistic width ratios, not hardware calibration or contact thresholds.
        // Pressure already passed the user's dead zone and editable response curve.
        private val expressiveFamilies: Map<String, BrushFamily> by lazy {
            mapOf(
                "Pen" to pressureFamily(.20f, 1.80f),
                "Pencil" to pressureFamily(.15f, 1.45f, pencil = true),
                "Fountain pen" to pressureFamily(.15f, 2.10f),
                "Brush pen" to pressureFamily(.08f, 2.60f),
            )
        }

        private fun pressureFamily(minWidth: Float, maxWidth: Float, pencil: Boolean = false): BrushFamily {
            fun behavior(target: TargetNode.Target, low: Float, high: Float) = BrushBehavior(
                listOf(TargetNode(target = target, targetModifierRangeStart = low,
                    targetModifierRangeEnd = high,
                    input = SourceNode(SourceNode.Source.NORMALIZED_PRESSURE, 0f, 1f)))
            )
            return BrushFamily(
                tip = BrushTip(behaviors = buildList {
                    add(behavior(TargetNode.Target.SIZE_MULTIPLIER, minWidth, maxWidth))
                    if (pencil) add(behavior(TargetNode.Target.OPACITY_MULTIPLIER, .35f, 1f))
                }),
                inputModel = StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1).inputModel,
                developerComment = "Folio v2: full-range filtered pressure to width; no extra activation threshold",
            )
        }
        const val DOCUMENT_WIDTH = 600f
        const val DOCUMENT_HEIGHT = 960f
    }
}

