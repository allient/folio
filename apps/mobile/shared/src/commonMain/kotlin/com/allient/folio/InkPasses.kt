package com.allient.folio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Splits a translucent stroke into passes, so a marker darkens where the stroke goes back over itself, like ink on
 * paper. A new pass starts where the direction has turned more than [turnDegrees] since the pass began: at each
 * reversal of a zigzag, and before a loop crosses itself. Directions are measured between samples at least [minStep]
 * apart (document units), so hand jitter doesn't split a pass. Passes share the turning sample, so they join without
 * a gap. Feed samples in order with [add]; the live stroke and the saved stroke use the same splitter.
 */
class InkPassSplitter(private val minStep: Float, turnDegrees: Float = 160f) {
    private val limit = turnDegrees * PI.toFloat() / 180f
    private var count = 0
    private var anchor = 0
    private var anchorX = 0f
    private var anchorY = 0f
    private var heading = Float.NaN
    private var turn = 0f
    private var lastStart = 0

    /** Adds the next sample; returns the index where a new pass starts, or -1. */
    fun add(x: Float, y: Float): Int {
        val index = count++
        if (index == 0) { anchorX = x; anchorY = y; return -1 }
        val dx = x - anchorX; val dy = y - anchorY
        if (!(hypot(dx, dy) >= minStep)) return -1
        val next = atan2(dy, dx)
        var start = -1
        if (!heading.isNaN()) {
            var delta = next - heading
            while (delta > PI) delta -= (2 * PI).toFloat()
            while (delta < -PI) delta += (2 * PI).toFloat()
            turn += delta
            if (abs(turn) > limit) {
                if (anchor > lastStart) { start = anchor; lastStart = anchor }
                turn = 0f
            }
        }
        heading = next; anchor = index; anchorX = x; anchorY = y
        return start
    }
}

/** Index where each pass of a stroke starts (see [InkPassSplitter]); the first is always 0. */
fun inkPassStarts(xs: FloatArray, ys: FloatArray, minStep: Float, turnDegrees: Float = 160f): List<Int> {
    val starts = mutableListOf(0)
    if (ys.size != xs.size) return starts
    val splitter = InkPassSplitter(minStep, turnDegrees)
    for (index in xs.indices) splitter.add(xs[index], ys[index]).let { if (it > 0) starts += it }
    return starts
}
