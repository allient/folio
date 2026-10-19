package com.allient.folio

import android.graphics.Canvas
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

    fun brushFor(style: BrushStyle, format: String = FORMAT): Brush {
        val key = BrushKey(style, format)
        return brushes[key] ?: Brush.createWithColorIntArgb(
            family = if (format == FORMAT && style.name in listOf("Calligraphy", "Fountain pen")) {
                BrushFamily(tip = BrushTip(scaleX = if (style.name == "Calligraphy") .2f else .5f, scaleY = 1f, rotationDegrees = if (style.variant.isBlank()) -35f else style.angle,
                    behaviors = listOf(pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .8f, 1.1f))),
                    inputModel = StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1).inputModel)
            } else if (format in listOf(FORMAT, V3_FORMAT) && style.name in naturalFamilies) {
                naturalFamilies.getValue(style.name)
            } else if (format == V2_FORMAT && style.name in expressiveFamilies) {
                expressiveFamilies.getValue(style.name)
            } else when (style.name) {
                "Fineliner", "Marker" -> StockBrushes.marker(StockBrushes.MarkerVersion.V1)
                "Gel pen" -> if (format == FORMAT) StockBrushes.marker(StockBrushes.MarkerVersion.V1) else StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1)
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
        if (liveStyle == null) { live.start(brushFor(style)); liveStyle = style; elapsed = -1L }
        elapsed = maxOf(elapsed + 1, point.elapsedMillis)
        batch.append(point, elapsed, style)
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
        renderer.draw(canvas, live, transform)
        canvas.restore()
    }

    fun cacheCompleted(source: InkStroke, stroke: Stroke) { completed.put(source.id, Cached(source, stroke)) }

    fun finish(source: InkStroke) {
        if (liveStyle == null) return
        if (NaturalMediaRenderer.supports(liveStyle!!.name)) { clearLive(); return }
        updateLive(); live.finishInput(); live.updateShape()
        completed.put(source.id, Cached(source, live.toImmutable()))
        clearLive()
    }

    // start() resets the native stroke on reuse. clear() is restricted in Ink 1.0.
    fun clearLive() { batch.clear(); livePoints.clear(); liveStyle = null; elapsed = -1L }

    fun draw(canvas: Canvas, source: InkStroke, width: Float, height: Float, zoom: Float = 1f) {
        if (source.points.isEmpty()) return
        if (source.engine == FORMAT && NaturalMediaRenderer.supports(source.brush)) {
            canvas.save(); canvas.scale(width / DOCUMENT_WIDTH, height / DOCUMENT_HEIGHT)
            natural.draw(canvas, source.points, BrushStyle(source.brush, source.width, source.color,
                texture = source.texture, grain = source.grain, angle = source.angle, variant = source.variant))
            canvas.restore(); return
        }
        val cached = completed[source.id]
        val ink = if (cached != null && (cached.source === source || cached.source == source)) cached.ink else {
            val style = BrushStyle(source.brush, source.width, source.color, angle = source.angle)
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

    fun release() { clearLive(); live = InProgressStroke(); completed.evictAll(); brushes.evictAll() }

    companion object {
        const val LEGACY_FORMAT = "jetpack-ink-v1"
        const val V2_FORMAT = "jetpack-ink-v2"
        const val V3_FORMAT = "jetpack-ink-v3"
        const val FORMAT = "jetpack-ink-v4"
        fun supports(format: String) = format in listOf(FORMAT, V3_FORMAT, V2_FORMAT, LEGACY_FORMAT)

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
                    inputModel = model, developerComment = "Knotes v3 smooth pressure pen"),
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
                    ), inputModel = model, developerComment = "Knotes v3 layered graphite pencil"),
                "Fountain pen" to BrushFamily(
                    tip = BrushTip(scaleX = .55f, scaleY = 1.35f, rotationDegrees = -35f,
                        behaviors = listOf(pressureBehavior(TargetNode.Target.SIZE_MULTIPLIER, .3f, 1.65f))),
                    inputModel = model, developerComment = "Knotes v3 angled fountain nib"),
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
                    ), inputModel = model, developerComment = "Knotes v3 flexible brush with subtle fiber dabs"),
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
                developerComment = "Knotes v2: full-range filtered pressure to width; no extra activation threshold",
            )
        }
        const val DOCUMENT_WIDTH = 600f
        const val DOCUMENT_HEIGHT = 960f
    }
}

