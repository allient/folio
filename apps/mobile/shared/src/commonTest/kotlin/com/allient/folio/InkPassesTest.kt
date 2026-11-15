package com.allient.folio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class InkPassesTest {
    @Test fun straightOrWavyStrokeIsOnePass() {
        val xs = FloatArray(40) { it * 4f }
        val ys = FloatArray(40) { if (it % 2 == 0) 0f else 1.5f }
        assertEquals(listOf(0), inkPassStarts(xs, ys, minStep = 3f))
    }

    @Test fun zigzagSplitsAtEachReversal() {
        // Three horizontal passes: right, back left, right again.
        val xs = (0..10).map { it * 10f } + (9 downTo 0).map { it * 10f } + (1..10).map { it * 10f }
        val ys = (0..10).map { 0f } + (9 downTo 0).map { 4f } + (1..10).map { 8f }
        val starts = inkPassStarts(xs.toFloatArray(), ys.toFloatArray(), minStep = 3f)
        assertEquals(3, starts.size)
        assertEquals(0, starts[0])
        // Each pass starts where the previous one turned, so the passes join without a gap.
        assertEquals(true, starts[1] in 9..12)
        assertEquals(true, starts[2] in 19..22)
    }

    @Test fun loopSplitsBeforeItCrossesItself() {
        val n = 72
        val xs = FloatArray(n + 1) { (50 * cos(2 * PI * it / n)).toFloat() }
        val ys = FloatArray(n + 1) { (50 * sin(2 * PI * it / n)).toFloat() }
        assertEquals(true, inkPassStarts(xs, ys, minStep = 3f).size >= 2)
    }

    @Test fun shortStrokesAreOnePass() {
        assertEquals(listOf(0), inkPassStarts(floatArrayOf(), floatArrayOf(), minStep = 3f))
        assertEquals(listOf(0), inkPassStarts(floatArrayOf(1f), floatArrayOf(1f), minStep = 3f))
        assertEquals(listOf(0), inkPassStarts(floatArrayOf(0f, 10f, 0f), floatArrayOf(0f, 0f, 0f), minStep = 20f))
    }
}
