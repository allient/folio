package com.allient.folio

import android.content.Context

/**
 * Raw stylus pressure to the pressure Folio draws with: contact below [threshold] is hover (0),
 * the rest is rescaled to 0..1 and passed through the pressure response curve. The notebook
 * canvas and the live board both use this, so the pen feels the same everywhere.
 */
internal fun penPressure(raw: Float, threshold: Float, curve: ResponseCurve?): Float {
    if (!raw.isFinite()) return 0f
    val normalized = ((raw.coerceIn(0f, 1f) - threshold) / maxOf(.0001f, 1f - threshold)).coerceIn(0f, 1f)
    return curve?.value(normalized) ?: normalized
}

/**
 * The global pen settings (threshold and pressure curve from Preferences › Pen pressure) plus the
 * live board's own output range and smoothing. Reload with [refresh] when settings may have changed.
 */
internal class PenPressureMapper(context: Context) {
    private val pen = context.applicationContext.getSharedPreferences("editor", Context.MODE_PRIVATE)
    private val board = context.applicationContext.getSharedPreferences(LIVE_BOARD, Context.MODE_PRIVATE)

    var threshold = 0f; private set
    private var curve: ResponseCurve? = null

    /** Output range: Excalidraw draws width from pressure, so 0 would make an invisible stroke. */
    var minPressure = DEFAULT_MIN; private set
    var maxPressure = 1f; private set
    /** 0 = off, up to 0.85 = strong. Exponential smoothing of the pen position. */
    var smoothing = 0f; private set
    /**
     * Like a notebook brush's sensitivity ([sensitivityCurve]): 0.5 is linear, higher reaches full
     * width with a lighter touch. Most pens rarely pass 0.6–0.7 raw, so the board defaults high.
     */
    var sensitivity = DEFAULT_SENSITIVITY; private set

    init { refresh() }

    fun refresh() {
        threshold = pen.getFloat("threshold", pressureProfiles[1].threshold)
        curve = if (pen.getBoolean("pressureCurveEnabled", true)) pen.loadResponseCurve(CurveChannel.Pressure, defaultPressureCurve) else null
        minPressure = board.getFloat(MIN, DEFAULT_MIN)
        maxPressure = board.getFloat(MAX, 1f).coerceAtLeast(minPressure)
        smoothing = board.getFloat(SMOOTHING, 0f).coerceIn(0f, MAX_SMOOTHING)
        sensitivity = board.getFloat(SENSITIVITY, DEFAULT_SENSITIVITY).coerceIn(0f, 1f)
    }

    fun setBoard(min: Float, max: Float, smooth: Float, sensitivity: Float) {
        board.edit().putFloat(MIN, min).putFloat(MAX, max).putFloat(SMOOTHING, smooth).putFloat(SENSITIVITY, sensitivity).apply()
        refresh()
    }

    /** Raw stylus pressure to the pressure sent to the board. */
    fun map(raw: Float): Float = minPressure + sensitivityCurve(penPressure(raw, threshold, curve), sensitivity) * (maxPressure - minPressure)

    companion object {
        const val LIVE_BOARD = "live-board"
        private const val MIN = "min_pressure"
        private const val MAX = "max_pressure"
        private const val SMOOTHING = "smoothing"
        private const val SENSITIVITY = "sensitivity"
        const val DEFAULT_SENSITIVITY = .75f
        const val DEFAULT_MIN = .05f
        const val MAX_SMOOTHING = .85f
    }
}
