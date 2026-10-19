package com.allient.folio

import android.graphics.Matrix
import android.os.Build
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput

/** UI-thread adapter: only accepted, pressure-remapped document points enter Ink authoring. */
@RequiresApi(29)
internal class FrontBufferedInkController(
    private val host: FrameLayout,
    private val engine: JetpackInkEngine,
    private val onVisibilityChanged: () -> Unit,
) {
    private var authoring: InProgressStrokesView? = null
    private var active: InProgressStrokeId? = null
    private var lastPoint: InkPoint? = null
    private var activeStyle: BrushStyle? = null
    private val batch = MutableStrokeInputBatch()
    private val transform = Matrix()
    private val pending = linkedMapOf<InProgressStrokeId, InkStroke>()
    private var attached = false
    val hasActive: Boolean get() = active != null
    val hasPending: Boolean get() = pending.isNotEmpty()
    fun isPending(modelId: String) = pending.values.any { it.id == modelId }

    private val finished = object : InProgressStrokesFinishedListener {
        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
            val view = authoring ?: return
            if (!attached) return
            for ((id, stroke) in strokes) {
                pending.remove(id)?.let { source -> engine.cacheCompleted(source, stroke) }
            }
            // Reveal saved vector strokes and remove their wet-layer copies in the SAME
            // UI loop, as required by Ink's handoff contract (especially translucent ink).
            onVisibilityChanged()
            view.removeFinishedStrokes(strokes.keys)
        }
    }

    fun attach() {
        attached = true
        createAuthoring()
    }

    private fun createAuthoring() {
        val view = InProgressStrokesView(host.context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isClickable = false
            addFinishedStrokesListener(finished)
        }
        authoring = view
        // Leave Ink's low-latency helper selection automatic. Do not set restricted
        // useHighLatencyRenderHelper, handoffDebounceTimeMs, flush, or sync APIs.
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.post { if (attached && authoring === view && view.isAttachedToWindow) view.eagerInit() }
    }

    private fun input(point: InkPoint, style: BrushStyle): StrokeInput = StrokeInput.create(
        x = point.x * JetpackInkEngine.DOCUMENT_WIDTH,
        y = point.y * JetpackInkEngine.DOCUMENT_HEIGHT,
        elapsedTimeMillis = point.elapsedMillis,
        toolType = InputToolType.STYLUS,
        pressure = pressure(point, style),
    )

    private fun pressure(point: InkPoint, style: BrushStyle) =
        engine.inputPressure(point, style)

    fun append(point: InkPoint, style: BrushStyle, width: Int, height: Int, zoom: Float, panX: Float, panY: Float) {
        val view = checkNotNull(authoring)
        val id = active
        if (id == null) {
            transform.setScale(width / JetpackInkEngine.DOCUMENT_WIDTH * zoom, height / JetpackInkEngine.DOCUMENT_HEIGHT * zoom)
            transform.postTranslate(panX, panY)
            // Start/finish inputs are owned by Ink's render-thread queue: don't mutate them.
            active = view.startStroke(input(point, style), engine.brushFor(style), transform)
            activeStyle = style
            view.post {
                if (attached && authoring === view) android.util.Log.i("FolioInk",
                    "Ink authoring: API=${Build.VERSION.SDK_INT}; surfaceAttached=${hasSurface(view)}; filteredInput=true")
            }
        } else {
            batch.clear()
            batch.add(InputToolType.STYLUS, point.x * JetpackInkEngine.DOCUMENT_WIDTH,
                point.y * JetpackInkEngine.DOCUMENT_HEIGHT, point.elapsedMillis, pressure = pressure(point, style))
            // Ink copies the batch into its input queue before returning.
            view.addToStroke(batch, id)
        }
        lastPoint = point
    }

    fun finish(source: InkStroke) {
        val id = active ?: return
        val last = checkNotNull(lastPoint)
        val style = checkNotNull(activeStyle)
        pending[id] = source
        active = null; lastPoint = null; activeStyle = null
        // Last accepted sample only. No fabricated nib-lift coordinate or zero-pressure tail.
        authoring?.finishStroke(input(last, style), id)
        onVisibilityChanged()
    }

    fun cancelActive() {
        active?.let { authoring?.cancelStroke(it) }
        active = null; lastPoint = null; activeStyle = null
    }

    fun reconcile(strokes: List<InkStroke>) {
        if (pending.values.any { source -> strokes.none { it.id == source.id && it == source } }) reset()
    }

    /** Viewport/tool/document edits use the already persisted vectors instead of stale wet ink. */
    fun reset() {
        if (!hasActive && !hasPending) return
        cancelActive()
        pending.clear()
        val old = authoring
        old?.removeFinishedStrokesListener(finished)
        authoring = null
        if (old != null) host.removeView(old)
        if (attached) createAuthoring()
        onVisibilityChanged()
    }

    fun detach() {
        attached = false
        cancelActive(); pending.clear()
        val view = authoring
        view?.removeFinishedStrokesListener(finished)
        authoring = null
        if (view != null) host.removeView(view)
        onVisibilityChanged()
    }

    private fun hasSurface(view: View): Boolean = view is SurfaceView ||
        (view is ViewGroup && (0 until view.childCount).any { hasSurface(view.getChildAt(it)) })
}
