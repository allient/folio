package com.allient.folio

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal object ShapeGeometry {
    /** Points with their pressure codes: solids carry [SolidPen] codes, every other shape is one visible edge. */
    fun coded(kind: ShapeKind, start: Offset, end: Offset, width: Float, height: Float,
              arrowHeadSize: Float = 24f * width / 600f, unbounded: Boolean = false): List<Pair<Offset, Float>> {
        val (a, b) = box(kind, start, end, width, height)
        return SolidPen.points(kind, a, b) ?: points(kind, start, end, width, height, arrowHeadSize, unbounded).map { it to SolidPen.EDGE }
    }

    private fun box(kind: ShapeKind, start: Offset, end: Offset, width: Float, height: Float): Pair<Offset, Offset> {
        var finish = end
        if (kind == ShapeKind.Circle || kind == ShapeKind.Square || kind == ShapeKind.Cube) {
            val side = min(abs(end.x - start.x) * width, abs(end.y - start.y) * height)
            finish = Offset(start.x + side / width * if (end.x >= start.x) 1f else -1f,
                start.y + side / height * if (end.y >= start.y) 1f else -1f)
        }
        return Offset(min(start.x, finish.x), min(start.y, finish.y)) to Offset(maxOf(start.x, finish.x), maxOf(start.y, finish.y))
    }

    private fun points(kind: ShapeKind, start: Offset, end: Offset, width: Float, height: Float,
               arrowHeadSize: Float = 24f * width / 600f, unbounded: Boolean = false): List<Offset> {
        val (a, b) = box(kind, start, end, width, height)
        val finish = if (kind == ShapeKind.Circle || kind == ShapeKind.Square) Offset(if (end.x >= start.x) b.x else a.x, if (end.y >= start.y) b.y else a.y) else end
        val center = (a + b) / 2f
        return when (kind) {
            ShapeKind.Line -> listOf(start, finish)
            ShapeKind.WavyLine, ShapeKind.Curve -> {
                val dx = (finish.x - start.x) * width; val dy = (finish.y - start.y) * height
                val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                (0..32).map { i ->
                    val t = i / 32f
                    val bend = if (kind == ShapeKind.WavyLine) sin(t * Math.PI * 4).toFloat() * .06f else sin(t * Math.PI).toFloat() * .18f
                    Offset(start.x + (finish.x - start.x) * t - dy / length * bend,
                        start.y + (finish.y - start.y) * t + dx / length * bend)
                }
            }
            ShapeKind.Arrow, ShapeKind.DoubleArrow -> {
                val delta = Offset((finish.x - start.x) * width, (finish.y - start.y) * height)
                val length = delta.getDistance()
                if (length < 1f) return listOf(start, finish)
                val direction = delta / length
                val head = min(arrowHeadSize, length * .3f)
                fun wing(tip: Offset, sign: Float, side: Float): Offset {
                    val x = tip.x + (direction.x * sign - direction.y * side * .5f) * head / width
                    val y = tip.y + (direction.y * sign + direction.x * side * .5f) * head / height
                    return Offset(if (unbounded) x else x.coerceIn(0f,1f), if (unbounded) y else y.coerceIn(0f,1f))
                }
                buildList {
                    add(start); add(finish); add(wing(finish, -1f, 1f)); add(finish); add(wing(finish, -1f, -1f)); add(finish)
                    if (kind == ShapeKind.DoubleArrow) { add(start); add(wing(start, 1f, 1f)); add(start); add(wing(start, 1f, -1f)) }
                }
            }
            ShapeKind.Rectangle, ShapeKind.Square -> listOf(a, Offset(b.x, a.y), b, Offset(a.x, b.y), a)
            ShapeKind.RoundedRectangle -> {
                val rx = (b.x - a.x) * .14f; val ry = (b.y - a.y) * .14f
                buildList {
                    val corners = listOf(Offset(b.x-rx,a.y+ry) to -90.0, Offset(b.x-rx,b.y-ry) to 0.0, Offset(a.x+rx,b.y-ry) to 90.0, Offset(a.x+rx,a.y+ry) to 180.0)
                    corners.forEach { (c, startAngle) -> for (i in 0..8) { val angle = Math.toRadians(startAngle + i * 90.0 / 8); add(Offset(c.x + rx*cos(angle).toFloat(), c.y + ry*sin(angle).toFloat())) } }
                    add(first())
                }
            }
            ShapeKind.Parallelogram -> listOf(Offset(a.x+(b.x-a.x)*.22f,a.y), Offset(b.x,a.y), Offset(b.x-(b.x-a.x)*.22f,b.y), Offset(a.x,b.y), Offset(a.x+(b.x-a.x)*.22f,a.y))
            ShapeKind.Trapezoid -> listOf(Offset(a.x+(b.x-a.x)*.2f,a.y), Offset(b.x-(b.x-a.x)*.2f,a.y), b, Offset(a.x,b.y), Offset(a.x+(b.x-a.x)*.2f,a.y))
            ShapeKind.Triangle -> listOf(Offset(center.x, a.y), b, Offset(a.x, b.y), Offset(center.x, a.y))
            ShapeKind.RightTriangle -> listOf(a, Offset(b.x,b.y), Offset(a.x,b.y), a)
            ShapeKind.Diamond -> listOf(Offset(center.x, a.y), Offset(b.x, center.y), Offset(center.x, b.y), Offset(a.x, center.y), Offset(center.x, a.y))
            ShapeKind.Pentagon, ShapeKind.Hexagon -> {
                val sides = if (kind == ShapeKind.Pentagon) 5 else 6
                (0..sides).map { index -> val angle = -Math.PI/2 + index * Math.PI * 2 / sides; Offset(center.x + (b.x-a.x)/2*cos(angle).toFloat(), center.y + (b.y-a.y)/2*sin(angle).toFloat()) }
            }
            ShapeKind.Cross -> listOf(Offset(center.x-(b.x-a.x)*.16f,a.y), Offset(center.x+(b.x-a.x)*.16f,a.y), Offset(center.x+(b.x-a.x)*.16f,center.y-(b.y-a.y)*.16f), Offset(b.x,center.y-(b.y-a.y)*.16f), Offset(b.x,center.y+(b.y-a.y)*.16f), Offset(center.x+(b.x-a.x)*.16f,center.y+(b.y-a.y)*.16f), Offset(center.x+(b.x-a.x)*.16f,b.y), Offset(center.x-(b.x-a.x)*.16f,b.y), Offset(center.x-(b.x-a.x)*.16f,center.y+(b.y-a.y)*.16f), Offset(a.x,center.y+(b.y-a.y)*.16f), Offset(a.x,center.y-(b.y-a.y)*.16f), Offset(center.x-(b.x-a.x)*.16f,center.y-(b.y-a.y)*.16f), Offset(center.x-(b.x-a.x)*.16f,a.y))
            ShapeKind.Star -> (0..10).map { index ->
                val angle = -Math.PI / 2 + index * Math.PI / 5
                val radius = if (index % 2 == 0) 1f else .42f
                Offset(center.x + (b.x - a.x) / 2 * radius * cos(angle).toFloat(), center.y + (b.y - a.y) / 2 * radius * sin(angle).toFloat())
            }
            ShapeKind.Ellipse, ShapeKind.Circle -> (0..96).map { index ->
                val angle = index * Math.PI * 2 / 96
                Offset(center.x + (b.x - a.x) / 2 * cos(angle).toFloat(), center.y + (b.y - a.y) / 2 * sin(angle).toFloat())
            }
            else -> ShapeLibrary.points(kind, a, b, start, finish, width, height, arrowHeadSize, unbounded)
        }
    }
}
