package com.allient.folio

import androidx.compose.ui.geometry.Offset
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Vector hit testing in page pixels. A swept eraser also cuts between input samples. */
internal object InkGeometry {
    private const val EPSILON = .000001f
    private data class Interval(val start: Float, val end: Float)
    private data class Bounds(val source: InkStroke, val left: Float, val top: Float, val right: Float, val bottom: Float, val pressure: Float)
    // Bound retained input as well as entry count; immutable stroke identities invalidate edits.
    private val bounds = object : android.util.LruCache<String, Bounds>(40_000) {
        override fun sizeOf(key: String, value: Bounds) = value.source.points.size.coerceAtLeast(128)
    }
    private fun boundsFor(stroke: InkStroke): Bounds = bounds[stroke.id]?.takeIf { it.source === stroke } ?: Bounds(stroke,
        stroke.points.minOf { it.x }, stroke.points.minOf { it.y }, stroke.points.maxOf { it.x }, stroke.points.maxOf { it.y },
        stroke.points.maxOf { it.pressure }).also { bounds.put(stroke.id, it) }

    fun contains(point: Offset, polygon: List<Offset>): Boolean {
        if (polygon.size < 3) return false
        var result = false
        var previous = polygon.last()
        for (current in polygon) {
            if (distanceSquared(point, previous, current) < EPSILON * EPSILON) return true
            if ((current.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x) result = !result
            previous = current
        }
        return result
    }

    /** Whether [point] lies within the stroke's cached bounds grown by [padding] (normalized units). */
    fun nearBounds(stroke: InkStroke, point: Offset, padding: Float = 0f): Boolean {
        if (stroke.points.isEmpty()) return false
        val box = boundsFor(stroke)
        return point.x >= box.left - padding && point.x <= box.right + padding &&
            point.y >= box.top - padding && point.y <= box.bottom + padding
    }

    // Runs for every stroke on the page when a lasso closes, on the main thread: reject by bounds
    // first and walk the points in place, or dense pages time out input dispatching (ANR).
    fun intersects(stroke: InkStroke, polygon: List<Offset>): Boolean {
        if (polygon.size < 3 || stroke.points.isEmpty()) return false
        var left = Float.MAX_VALUE; var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        for (p in polygon) { left = min(left, p.x); top = min(top, p.y); right = max(right, p.x); bottom = max(bottom, p.y) }
        val box = boundsFor(stroke)
        if (box.right + EPSILON < left || box.left - EPSILON > right ||
            box.bottom + EPSILON < top || box.top - EPSILON > bottom) return false
        if (stroke.filled) return FilledInkEraser.intersectsPolygon(stroke, polygon)
        val points = stroke.points
        for (p in points) if (contains(Offset(p.x, p.y), polygon)) return true
        for (index in 0 until points.lastIndex) {
            val a = Offset(points[index].x, points[index].y)
            val b = Offset(points[index + 1].x, points[index + 1].y)
            if (max(a.x, b.x) + EPSILON < left || min(a.x, b.x) - EPSILON > right ||
                max(a.y, b.y) + EPSILON < top || min(a.y, b.y) - EPSILON > bottom) continue
            for (i in polygon.indices) if (segmentsIntersect(a, b, polygon[i], polygon[(i + 1) % polygon.size])) return true
        }
        return false
    }

    private fun segmentsIntersect(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
        fun cross(p: Offset, q: Offset, r: Offset) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
        if (max(a.x, b.x) + EPSILON < min(c.x, d.x) || max(c.x, d.x) + EPSILON < min(a.x, b.x) ||
            max(a.y, b.y) + EPSILON < min(c.y, d.y) || max(c.y, d.y) + EPSILON < min(a.y, b.y)) return false
        return cross(a, b, c) * cross(a, b, d) <= 0f && cross(c, d, a) * cross(c, d, b) <= 0f
    }

    private fun distanceSquared(p: Offset, a: Offset, b: Offset): Float {
        val v = b - a
        val length = v.x * v.x + v.y * v.y
        val t = if (length < EPSILON) 0f else (((p - a).x * v.x + (p - a).y * v.y) / length).coerceIn(0f, 1f)
        val delta = p - (a + v * t)
        return delta.x * delta.x + delta.y * delta.y
    }

    fun erase(strokes: List<InkStroke>, from: Offset, to: Offset, radius: Float, width: Float, height: Float,
              wholeStroke: Boolean, editable: (InkStroke) -> Boolean): List<InkStroke> {
        val start = Offset(from.x * width, from.y * height)
        val end = Offset(to.x * width, to.y * height)
        var changed = false
        val result = strokes.flatMap { stroke ->
            if (!editable(stroke) || stroke.points.isEmpty()) return@flatMap listOf(stroke)
            val box = boundsFor(stroke)
            val padding = radius + stroke.width * width / 600f * 1.3f
            if (box.right * width + padding < min(start.x, end.x) || box.left * width - padding > max(start.x, end.x) ||
                box.bottom * height + padding < min(start.y, end.y) || box.top * height - padding > max(start.y, end.y)) return@flatMap listOf(stroke)
            if (stroke.filled) {
                val cut = FilledInkEraser.erase(stroke, start, end, radius, width, height, wholeStroke)
                if (cut !== stroke) changed = true
                return@flatMap listOfNotNull(cut)
            }
            // Include the ink's width so a thick nib is erased when its visible edge is touched.
            val pressure = box.pressure.coerceIn(0f, 1f)
            val factor = if (JetpackInkEngine.current(stroke.engine)) when (stroke.brush) {
                "Pen" -> .2f + 1.6f * pressure
                "Pencil" -> .15f + 1.3f * pressure
                "Fountain pen" -> .15f + 1.95f * pressure
                "Brush pen" -> .08f + 2.52f * pressure
                else -> 1f
            } else 1f
            val effectiveRadius = radius + stroke.width * width / 600f * factor / 2f
            fun screen(p: InkPoint) = Offset(p.x * width, p.y * height)
            if (stroke.points.size == 1) {
                if (distanceSquared(screen(stroke.points.first()), start, end) <= effectiveRadius * effectiveRadius) {
                    changed = true; return@flatMap emptyList()
                }
                return@flatMap listOf(stroke)
            }
            val cuts = stroke.points.zipWithNext().map { (a, b) -> capsuleIntervals(screen(a), screen(b), start, end, effectiveRadius) }
            if (cuts.all { it.isEmpty() }) return@flatMap listOf(stroke)
            changed = true
            if (wholeStroke) return@flatMap emptyList()
            val pieces = mutableListOf<List<InkPoint>>()
            var piece = mutableListOf<InkPoint>()
            fun finish() { if (piece.isNotEmpty()) pieces += piece; piece = mutableListOf() }
            stroke.points.zipWithNext().forEachIndexed { index, (a, b) ->
                fun at(t: Float) = InkPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
                    a.pressure + (b.pressure - a.pressure) * t,
                    a.elapsedMillis + ((b.elapsedMillis - a.elapsedMillis) * t).toLong())
                fun append(t: Float) { val p = at(t); if (piece.lastOrNull() != p) piece += p }
                var cursor = 0f
                for (cut in cuts[index]) {
                    if (cut.start > cursor + EPSILON) { append(cursor); append(cut.start) }
                    finish(); cursor = cut.end
                }
                if (cursor < 1f - EPSILON) { append(cursor); append(1f) }
            }
            finish()
            pieces.map { stroke.copy(id = UUID.randomUUID().toString(), points = it, filled = false) }
        }
        return if (changed) result else strokes
    }

    /** Exact segment/capsule intersections, avoiding gaps and thousands of interpolated points. */
    private fun capsuleIntervals(a: Offset, b: Offset, start: Offset, end: Offset, radius: Float): List<Interval> {
        val intervals = mutableListOf<Interval>()
        val v = b - a
        fun circle(center: Offset) {
            val delta = a - center
            val aa = v.x * v.x + v.y * v.y
            val cc = delta.x * delta.x + delta.y * delta.y - radius * radius
            if (aa < EPSILON) { if (cc <= 0) intervals += Interval(0f, 1f); return }
            val bb = 2f * (delta.x * v.x + delta.y * v.y)
            val discriminant = bb * bb - 4f * aa * cc
            if (discriminant < 0f) return
            val root = sqrt(discriminant)
            val low = max(0f, (-bb - root) / (2f * aa))
            val high = min(1f, (-bb + root) / (2f * aa))
            if (low <= high) intervals += Interval(low, high)
        }
        circle(start)
        val axis = end - start
        val length = axis.getDistance()
        if (length > EPSILON) {
            circle(end)
            val u = axis / length
            val relative = a - start
            var low = 0f; var high = 1f
            fun slab(origin: Float, delta: Float, minimum: Float, maximum: Float): Boolean {
                if (abs(delta) < EPSILON) return origin in minimum..maximum
                val t1 = (minimum - origin) / delta; val t2 = (maximum - origin) / delta
                low = max(low, min(t1, t2)); high = min(high, max(t1, t2))
                return low <= high
            }
            if (slab(relative.x * u.x + relative.y * u.y, v.x * u.x + v.y * u.y, 0f, length) &&
                slab(relative.x * -u.y + relative.y * u.x, v.x * -u.y + v.y * u.x, -radius, radius)) intervals += Interval(low, high)
        }
        val merged = mutableListOf<Interval>()
        for (interval in intervals.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && interval.start <= last.end + EPSILON) merged[merged.lastIndex] = Interval(last.start, max(last.end, interval.end))
            else merged += interval
        }
        return merged
    }
}
