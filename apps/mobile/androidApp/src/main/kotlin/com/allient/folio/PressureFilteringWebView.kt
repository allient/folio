package com.allient.folio

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView

/**
 * A WebView that applies Folio's pen filters before the page sees the stylus, the same way the
 * notebook canvas does:
 *
 * 1. **Threshold as hover:** contact at or below the pen threshold never draws. A stroke starts
 *    when pressure passes the threshold and **ends** as soon as it drops back to it (the page gets
 *    pointerup). Some pens (the MNP1095) keep sending MOVE with near-zero pressure after the tip
 *    leaves the glass; without this the board kept drawing in the air.
 * 2. **Pressure:** every sample, including the batched historical ones, gets the pressure from
 *    [PenPressureMapper] (threshold rescale, response curve, sensitivity, output range). Chromium
 *    passes it to the page as `PointerEvent.pressure` with `pointerType = "pen"`, which Excalidraw
 *    stores per point in its freedraw elements, so collaborators see the same widths.
 * 3. **Smoothing (optional):** exponential smoothing of the pen position within a stroke.
 * 4. **Palm rejection:** the page only ever sees the pen as a single pointer. Fingers touching at
 *    the same time, or shortly after the pen was near the glass, are dropped, so a resting hand can't
 *    tap the toolbar or turn a stroke into a pinch-zoom. At other times fingers pass through; in
 *    Excalidraw's pen mode they pan and zoom.
 *
 * Every pen down the page sees gets a matching up or cancel. A missing pointerup leaves Excalidraw
 * with a "pressed" pointer, and every later stroke then zooms instead of drawing.
 */
@SuppressLint("ViewConstructor")
internal class PressureFilteringWebView(context: Context, val mapper: PenPressureMapper) : WebView(context) {
    private var inContact = false
    private var strokeDownTime = 0L
    private var smoothX = Float.NaN
    private var smoothY = Float.NaN

    /** Last time the pen touched or hovered; fingers are ignored until [PALM_GRACE_MS] after it. */
    private var lastPenTime = 0L
    private var fingerBlocked = false
    /** The finger gesture the page is currently receiving, kept to cancel it if the pen comes down. */
    private var fingerGesture: MotionEvent? = null

    /** Fingers reach the page only when this is on (the board's "Fingers" switch); otherwise only the pen works. */
    var fingersEnabled = false

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromTool(0)) lastPenTime = event.eventTime // hover: the hand is about to rest on the glass
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val pen = (0 until event.pointerCount).firstOrNull { event.isFromTool(it) } ?: return dispatchFinger(event)
        lastPenTime = event.eventTime
        cancelFingerGesture()
        val action = event.actionMasked
        val penAction = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP ||
            ((action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) && event.actionIndex == pen)
        val firm = event.getPressure(pen) > mapper.threshold
        when {
            action == MotionEvent.ACTION_CANCEL -> if (inContact) end(event, pen, MotionEvent.ACTION_CANCEL)
            penAction && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) -> {
                if (inContact) end(event, pen, MotionEvent.ACTION_CANCEL)
                if (firm) begin(event, pen)
            }
            penAction -> if (inContact) end(event, pen, MotionEvent.ACTION_UP) // the pen lifted
            action == MotionEvent.ACTION_MOVE -> when {
                inContact && !firm -> end(event, pen, MotionEvent.ACTION_UP) // back to hover pressure: the stroke ends here
                inContact -> send(event, pen, MotionEvent.ACTION_MOVE)
                firm -> begin(event, pen) // pressed again: a new stroke
            }
            // Another pointer (a palm) going down or up while the pen is on the glass: not for the page.
        }
        return true
    }

    /**
     * A finger gesture that starts near pen use is dropped as a whole, so the page never sees half of
     * it. Events of a gesture the page never saw start (or saw cancelled) are dropped too.
     */
    private fun dispatchFinger(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) fingerBlocked = !fingersEnabled || inContact || event.eventTime - lastPenTime < PALM_GRACE_MS
        else if (fingerGesture == null) fingerBlocked = true // orphan: no DOWN reached the page
        val ending = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        if (fingerBlocked) {
            if (ending) fingerBlocked = false
            return true
        }
        fingerGesture?.recycle()
        fingerGesture = if (ending) null else MotionEvent.obtain(event)
        return super.dispatchTouchEvent(event)
    }

    /** The pen arrived during a finger pan or zoom: end that gesture for the page before the stroke. */
    private fun cancelFingerGesture() {
        val gesture = fingerGesture ?: return
        fingerGesture = null
        fingerBlocked = true // the rest of this finger gesture stays away from the page
        gesture.action = MotionEvent.ACTION_CANCEL
        try { super.dispatchTouchEvent(gesture) } finally { gesture.recycle() }
    }

    private fun MotionEvent.isFromTool(index: Int): Boolean {
        val tool = getToolType(index)
        return tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun begin(event: MotionEvent, pen: Int) {
        inContact = true
        strokeDownTime = event.eventTime
        smoothX = Float.NaN; smoothY = Float.NaN
        send(event, pen, MotionEvent.ACTION_DOWN, latestOnly = true)
    }

    private fun end(event: MotionEvent, pen: Int, action: Int) {
        inContact = false
        send(event, pen, action, latestOnly = true)
    }

    /**
     * Re-emits the pen pointer [pen] of [event] to the page as a single-pointer event with [action],
     * remapped pressure and (unless [latestOnly]) all batched samples.
     */
    private fun send(event: MotionEvent, pen: Int, action: Int, latestOnly: Boolean = false) {
        val ending = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        val properties = arrayOf(MotionEvent.PointerProperties().also { event.getPointerProperties(pen, it); it.id = 0 })
        val coords = arrayOf(MotionEvent.PointerCoords())
        fun sample(history: Int): Array<MotionEvent.PointerCoords> {
            val c = coords[0]
            if (history < 0) event.getPointerCoords(pen, c) else event.getHistoricalPointerCoords(pen, history, c)
            c.pressure = if (ending) mapper.minPressure else mapper.map(c.pressure)
            smooth(c)
            return coords
        }
        // Batched samples carry most of a fast stroke; remap them all or the width steps.
        val history = if (latestOnly) 0 else event.historySize
        val first = if (history > 0) sample(0) else sample(-1)
        val out = MotionEvent.obtain(
            strokeDownTime, if (history > 0) event.getHistoricalEventTime(0) else event.eventTime, action,
            1, properties, first, event.metaState, event.buttonState, event.xPrecision, event.yPrecision,
            event.deviceId, event.edgeFlags, event.source, event.flags,
        )
        for (i in 1 until history) out.addBatch(event.getHistoricalEventTime(i), sample(i), event.metaState)
        if (history > 0) out.addBatch(event.eventTime, sample(-1), event.metaState)
        try { super.dispatchTouchEvent(out) } finally { out.recycle() }
    }

    private fun smooth(c: MotionEvent.PointerCoords) {
        val k = mapper.smoothing
        if (k <= 0f || smoothX.isNaN()) { smoothX = c.x; smoothY = c.y; return }
        smoothX += (c.x - smoothX) * (1f - k)
        smoothY += (c.y - smoothY) * (1f - k)
        c.x = smoothX; c.y = smoothY
    }

    override fun destroy() {
        fingerGesture?.recycle()
        fingerGesture = null
        super.destroy()
    }

    private companion object {
        /** How long after the pen was last seen (touching or hovering) finger touches stay ignored. */
        const val PALM_GRACE_MS = 700L
    }
}
