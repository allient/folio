package com.example.knotes

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal object InkSelection {
    /** Rotate in document units and constrain the group together, preserving its proportions. */
    fun transform(strokes: List<InkStroke>, ids: Set<String>, scale: Float = 1f,
                  rotation: Float = 0f, flipX: Boolean = false, flipY: Boolean = false, unbounded: Boolean = false): List<InkStroke> {
        val points = strokes.filter { it.id in ids }.flatMap { it.points }
        if (points.isEmpty()) return strokes
        val centerX = (points.minOf { it.x } + points.maxOf { it.x }) / 2f
        val centerY = (points.minOf { it.y } + points.maxOf { it.y }) / 2f
        val angle = Math.toRadians(rotation.toDouble())
        val cosine = cos(angle).toFloat(); val sine = sin(angle).toFloat()
        fun transformed(p: InkPoint): InkPoint {
            val x = (p.x - centerX) * 600f * scale * if (flipX) -1f else 1f
            val y = (p.y - centerY) * 960f * scale * if (flipY) -1f else 1f
            return p.copy(x = centerX + (x * cosine - y * sine) / 600f, y = centerY + (x * sine + y * cosine) / 960f)
        }
        val changed = points.map(::transformed)
        if (unbounded) return strokes.map { if (it.id in ids) it.copy(width = (it.width * scale).coerceIn(.01f, 256f), points = it.points.map(::transformed)) else it }
        val minX = changed.minOf { it.x }; val maxX = changed.maxOf { it.x }
        val minY = changed.minOf { it.y }; val maxY = changed.maxOf { it.y }
        val fit = min(1f, min(1f / (maxX - minX).coerceAtLeast(.0001f), 1f / (maxY - minY).coerceAtLeast(.0001f)))
        val shiftX = (-(minX - centerX) * fit - centerX).coerceAtLeast(0f) - ((maxX - centerX) * fit + centerX - 1f).coerceAtLeast(0f)
        val shiftY = (-(minY - centerY) * fit - centerY).coerceAtLeast(0f) - ((maxY - centerY) * fit + centerY - 1f).coerceAtLeast(0f)
        return strokes.map { stroke -> if (stroke.id !in ids) stroke else stroke.copy(width = (stroke.width * scale * fit).coerceIn(.01f, 256f), points = stroke.points.map {
            val p = transformed(it)
            p.copy(x = (centerX + (p.x - centerX) * fit + shiftX).coerceIn(0f, 1f), y = (centerY + (p.y - centerY) * fit + shiftY).coerceIn(0f, 1f))
        }) }
    }
}
