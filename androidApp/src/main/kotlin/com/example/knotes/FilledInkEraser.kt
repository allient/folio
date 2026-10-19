package com.example.knotes

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil

/** Boolean vector subtraction retains holes in solid shapes, including in PDF/PNG exports. */
internal object FilledInkEraser {
    const val FORMAT = "fill-cut-v1"

    fun intersectsPolygon(stroke: InkStroke, polygon: List<Offset>): Boolean {
        fun path(points: List<Offset>) = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }; close()
        }
        if (stroke.points.isEmpty() || polygon.size < 3) return false
        val intersection = Path()
        return intersection.op(path(stroke.points.map { Offset(it.x * 600f, it.y * 960f) }),
            path(polygon.map { Offset(it.x * 600f, it.y * 960f) }), Path.Op.INTERSECT) && !intersection.isEmpty
    }

    fun erase(stroke: InkStroke, from: Offset, to: Offset, radius: Float, width: Float, height: Float,
              wholeStroke: Boolean): InkStroke? {
        if (stroke.points.isEmpty()) return stroke
        val original = Path().apply {
            moveTo(stroke.points.first().x * width, stroke.points.first().y * height)
            stroke.points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
            close()
        }
        if (stroke.engine != FORMAT) {
            val outline = Path()
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = stroke.width * width / 600f
                strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            }.getFillPath(original, outline)
            original.op(outline, Path.Op.UNION)
        }
        val eraser = Path()
        if ((to - from).getDistance() < .001f) eraser.addCircle(from.x, from.y, radius, Path.Direction.CW)
        else {
            val axis = Path().apply { moveTo(from.x, from.y); lineTo(to.x, to.y) }
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = radius * 2; strokeCap = Paint.Cap.ROUND
            }.getFillPath(axis, eraser)
        }
        val intersection = Path()
        if (!intersection.op(original, eraser, Path.Op.INTERSECT) || intersection.isEmpty) return stroke
        if (wholeStroke) return null
        val remainder = Path()
        if (!remainder.op(original, eraser, Path.Op.DIFFERENCE)) return stroke
        if (remainder.isEmpty) return null
        val measure = PathMeasure(remainder, true)
        val contours = mutableListOf<List<InkPoint>>()
        val position = FloatArray(2)
        do {
            if (measure.length <= 0) continue
            val count = ceil(measure.length / (width / 600f).coerceAtLeast(.5f)).toInt().coerceIn(3, 8192)
            contours += (0..count).map { index ->
                measure.getPosTan(measure.length * index / count, position, null)
                InkPoint(position[0] / width, position[1] / height, 1f)
            }
        } while (measure.nextContour())
        if (contours.isEmpty()) return null
        // Closed contours share zero-area bridges. Winding is preserved, so holes stay holes
        // in the existing portable polygon representation without introducing bitmap masks.
        val points = buildList {
            val anchor = contours.first().first()
            for (contour in contours) {
                add(contour.first()); addAll(contour.drop(1)); add(contour.first()); add(anchor)
            }
        }
        return stroke.copy(points = points, engine = FORMAT)
    }
}
