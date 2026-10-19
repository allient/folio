package com.allient.folio

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

/** Palette sections and outlines for the extended shape set. Every shape is one polyline, so retraced edges stand in for separate strokes. */
internal object ShapeLibrary {
    val groups: List<Pair<String, List<ShapeKind>>> = listOf(
        "Basic" to listOf(
            ShapeKind.Line, ShapeKind.Arrow, ShapeKind.Rectangle, ShapeKind.RoundedRectangle, ShapeKind.Square, ShapeKind.Capsule,
            ShapeKind.Ellipse, ShapeKind.Circle, ShapeKind.Triangle, ShapeKind.RightTriangle, ShapeKind.Diamond, ShapeKind.Parallelogram,
            ShapeKind.Trapezoid, ShapeKind.Pentagon, ShapeKind.Hexagon, ShapeKind.Octagon, ShapeKind.Star, ShapeKind.Cross,
            ShapeKind.Heart, ShapeKind.Cloud, ShapeKind.Pie, ShapeKind.Crescent, ShapeKind.Arc, ShapeKind.WavyLine, ShapeKind.Curve,
        ),
        "Flowchart" to listOf(
            ShapeKind.Rectangle, ShapeKind.RoundedRectangle, ShapeKind.Diamond, ShapeKind.Parallelogram, ShapeKind.Capsule, ShapeKind.Hexagon,
            ShapeKind.PredefinedProcess, ShapeKind.InternalStorage, ShapeKind.Document, ShapeKind.ManualInput, ShapeKind.ManualOperation, ShapeKind.OffPage,
            ShapeKind.Delay, ShapeKind.Display, ShapeKind.Cylinder, ShapeKind.Circle, ShapeKind.SummingJunction, ShapeKind.OrJunction,
            ShapeKind.Merge, ShapeKind.Triangle,
        ),
        "Arrows & callouts" to listOf(
            ShapeKind.BlockRight, ShapeKind.BlockLeft, ShapeKind.BlockUp, ShapeKind.BlockDown, ShapeKind.BlockLeftRight, ShapeKind.BlockUpDown,
            ShapeKind.BlockFourWay, ShapeKind.Chevron, ShapeKind.HomePlate, ShapeKind.CalloutRect, ShapeKind.CalloutRounded, ShapeKind.CalloutOval,
            ShapeKind.Starburst,
        ),
        "3D & math" to listOf(
            ShapeKind.Cube, ShapeKind.Cuboid, ShapeKind.Pyramid, ShapeKind.Cone, ShapeKind.Cylinder, ShapeKind.Sphere,
            ShapeKind.Hemisphere, ShapeKind.TriPrism, ShapeKind.Frustum, ShapeKind.Axes2D, ShapeKind.Axes3D, ShapeKind.DoubleArrow,
            ShapeKind.NumberGrid, ShapeKind.Angle, ShapeKind.RightAngle, ShapeKind.Brace, ShapeKind.Bracket, ShapeKind.Paren,
        ),
        "Connectors" to listOf(
            ShapeKind.Line, ShapeKind.Arrow, ShapeKind.DoubleArrow, ShapeKind.ElbowLine, ShapeKind.ElbowArrow, ShapeKind.ElbowDouble,
            ShapeKind.SCurve, ShapeKind.SCurveArrow, ShapeKind.SCurveDouble,
        ),
        "UML" to listOf(ShapeKind.Arrow, ShapeKind.Inheritance, ShapeKind.Aggregation, ShapeKind.Composition),
    )

    fun points(kind: ShapeKind, a: Offset, b: Offset, start: Offset, finish: Offset, width: Float, height: Float, headSize: Float, unbounded: Boolean): List<Offset> {
        fun p(x: Float, y: Float) = Offset(a.x + (b.x - a.x) * x, a.y + (b.y - a.y) * y)
        fun poly(vararg v: Pair<Float, Float>) = v.map { p(it.first, it.second) }
        fun clamp(o: Offset) = if (unbounded) o else Offset(o.x.coerceIn(0f, 1f), o.y.coerceIn(0f, 1f))
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, from: Double, to: Double, steps: Int = 48) =
            (0..steps).map { i -> val t = from + (to - from) * i / steps; p(cx + rx * cos(t).toFloat(), cy + ry * sin(t).toFloat()) }
        fun quad(p0: Pair<Float, Float>, c: Pair<Float, Float>, p1: Pair<Float, Float>, steps: Int = 10) = (1..steps).map { i ->
            val t = i / steps.toFloat(); val u = 1 - t
            p(u * u * p0.first + 2 * u * t * c.first + t * t * p1.first, u * u * p0.second + 2 * u * t * c.second + t * t * p1.second)
        }
        fun rounded(v: List<Pair<Float, Float>>, r: Float): List<Offset> = buildList {
            v.forEachIndexed { i, cur ->
                val prev = v[(i + v.size - 1) % v.size]; val next = v[(i + 1) % v.size]
                fun toward(o: Pair<Float, Float>): Pair<Float, Float> {
                    val dx = o.first - cur.first; val dy = o.second - cur.second; val len = hypot(dx, dy).coerceAtLeast(1e-4f)
                    val d = minOf(r, len / 2f); return cur.first + dx / len * d to cur.second + dy / len * d
                }
                val s = toward(prev); val e = toward(next)
                if (isEmpty()) add(p(s.first, s.second))
                addAll(quad(s, cur, e))
            }
            add(first())
        }
        fun head(tip: Offset, dx: Float, dy: Float): List<Offset> {
            val len = hypot(dx, dy).coerceAtLeast(1e-4f); val ux = dx / len; val uy = dy / len
            fun wing(side: Float) = clamp(Offset(tip.x + (-ux - uy * side * .5f) * headSize / width, tip.y + (-uy + ux * side * .5f) * headSize / height))
            return listOf(wing(1f), tip, wing(-1f), tip)
        }
        fun polyhedron(v: List<Offset>, edges: List<Pair<Int, Int>>): List<Offset> {
            val visited = mutableSetOf<Int>()
            return buildList {
                fun walk(index: Int) {
                    add(v[index])
                    edges.forEachIndexed { e, (from, to) ->
                        if (e !in visited && (from == index || to == index)) { visited += e; walk(if (from == index) to else from); add(v[index]) }
                    }
                }
                walk(0)
            }
        }
        val connectorDirX = sign(finish.x - (start.x + finish.x) / 2f).let { if (it == 0f) 1f else it }
        val midX = (start.x + finish.x) / 2f
        fun elbow() = listOf(start, Offset(midX, start.y), Offset(midX, finish.y), finish)
        fun scurve() = (0..32).map { i ->
            val t = i / 32f; val u = 1 - t
            Offset(u * u * u * start.x + 3 * u * u * t * midX + 3 * u * t * t * midX + t * t * t * finish.x,
                u * u * u * start.y + 3 * u * u * t * start.y + 3 * u * t * t * finish.y + t * t * t * finish.y)
        }
        fun withHeads(line: List<Offset>, atEnd: Boolean, atStart: Boolean) = buildList {
            addAll(line)
            if (atEnd) addAll(head(finish, connectorDirX, 0f))
            if (atStart) { addAll(line.reversed()); addAll(head(start, -connectorDirX, 0f)) }
        }
        val dx = (finish.x - start.x) * width; val dy = (finish.y - start.y) * height
        val len = hypot(dx, dy).coerceAtLeast(1f); val ux = dx / len; val uy = dy / len
        fun along(distance: Float, side: Float = 0f) = Offset(finish.x - (ux * distance - uy * side) / width, finish.y - (uy * distance + ux * side) / height)
        val diamond = (headSize * 1.6f).coerceAtMost(len * .6f)

        return when (kind) {
            ShapeKind.Capsule -> {
                val ry = (b.y - a.y) / 2f; val rx = minOf(ry * height / width, (b.x - a.x) / 2f)
                val c = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                buildList {
                    for (i in 0..16) { val t = -PI / 2 + PI * i / 16; add(Offset(b.x - rx + rx * cos(t).toFloat(), c.y + ry * sin(t).toFloat())) }
                    for (i in 0..16) { val t = PI / 2 + PI * i / 16; add(Offset(a.x + rx + rx * cos(t).toFloat(), c.y + ry * sin(t).toFloat())) }
                    add(first())
                }
            }
            ShapeKind.Octagon -> (0..8).map { i -> val t = PI / 8 + i * PI / 4; p(.5f + .5f * cos(t).toFloat(), .5f + .5f * sin(t).toFloat()) }
            ShapeKind.Heart -> (0..64).map { i ->
                val t = i * 2 * PI / 64
                val x = 16 * sin(t).pow3(); val y = 13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)
                p(.5f + x.toFloat() / 34f, .45f - y.toFloat() / 34f)
            }
            ShapeKind.Cloud -> (0..96).map { i ->
                val t = i * 2 * PI / 96; val r = .8f + .2f * abs(sin(t * 4)).toFloat()
                p(.5f + .5f * r * cos(t).toFloat(), .5f + .5f * r * sin(t).toFloat())
            }
            ShapeKind.Pie -> listOf(p(.5f, .5f)) + ellipse(.5f, .5f, .5f, .5f, 0.0, 1.5 * PI, 36) + listOf(p(.5f, .5f))
            ShapeKind.Crescent -> ellipse(.5f, .5f, .5f, .5f, 70 * PI / 180, 290 * PI / 180, 36) +
                ellipse(.5f + .225f, .5f, .473f, .473f, 263.4 * PI / 180, 96.6 * PI / 180, 36) + listOf(p(.5f + .5f * cos(70 * PI / 180).toFloat(), .5f + .5f * sin(70 * PI / 180).toFloat()))
            ShapeKind.Arc -> ellipse(.5f, .9f, .5f, .8f, PI, 2 * PI, 32)
            ShapeKind.PredefinedProcess -> poly(0f to 0f, .12f to 0f, .12f to 1f, .12f to 0f, .88f to 0f, .88f to 1f, .88f to 0f, 1f to 0f, 1f to 1f, 0f to 1f, 0f to 0f)
            ShapeKind.InternalStorage -> poly(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f, 0f to .15f, 1f to .15f, .15f to .15f, .15f to 1f)
            ShapeKind.Document -> poly(0f to 0f, 1f to 0f, 1f to .82f) + (0..24).map { i -> val x = 1f - i / 24f; p(x, .82f + .1f * sin((1f - x) * 2 * PI).toFloat()) } + poly(0f to 0f)
            ShapeKind.ManualInput -> poly(0f to .2f, 1f to 0f, 1f to 1f, 0f to 1f, 0f to .2f)
            ShapeKind.ManualOperation -> poly(0f to 0f, 1f to 0f, .8f to 1f, .2f to 1f, 0f to 0f)
            ShapeKind.OffPage -> poly(0f to 0f, 1f to 0f, 1f to .65f, .5f to 1f, 0f to .65f, 0f to 0f)
            ShapeKind.Delay -> poly(0f to 0f, .5f to 0f) + ellipse(.5f, .5f, .5f, .5f, -PI / 2, PI / 2, 24) + poly(0f to 1f, 0f to 0f)
            ShapeKind.Display -> poly(0f to .5f, .2f to 0f, .8f to 0f) + ellipse(.8f, .5f, .2f, .5f, -PI / 2, PI / 2, 24) + poly(.2f to 1f, 0f to .5f)
            ShapeKind.SummingJunction -> ellipse(.5f, .5f, .5f, .5f, PI / 4, PI / 4 + 2 * PI, 48) + ellipse(.5f, .5f, .5f, .5f, 5 * PI / 4, 3 * PI / 4, 12).let { arc ->
                listOf(arc.first()) + arc + listOf(p(.5f + .5f * cos(7 * PI / 4).toFloat(), .5f + .5f * sin(7 * PI / 4).toFloat()))
            }
            ShapeKind.OrJunction -> ellipse(.5f, .5f, .5f, .5f, 0.0, 2 * PI, 48) + ellipse(.5f, .5f, .5f, .5f, PI, PI / 2, 12) + listOf(p(.5f, 0f))
            ShapeKind.Merge -> poly(0f to 0f, 1f to 0f, .5f to 1f, 0f to 0f)
            ShapeKind.BlockRight -> poly(0f to .3f, .6f to .3f, .6f to 0f, 1f to .5f, .6f to 1f, .6f to .7f, 0f to .7f, 0f to .3f)
            ShapeKind.BlockLeft -> poly(1f to .3f, .4f to .3f, .4f to 0f, 0f to .5f, .4f to 1f, .4f to .7f, 1f to .7f, 1f to .3f)
            ShapeKind.BlockUp -> poly(.3f to 1f, .3f to .4f, 0f to .4f, .5f to 0f, 1f to .4f, .7f to .4f, .7f to 1f, .3f to 1f)
            ShapeKind.BlockDown -> poly(.3f to 0f, .3f to .6f, 0f to .6f, .5f to 1f, 1f to .6f, .7f to .6f, .7f to 0f, .3f to 0f)
            ShapeKind.BlockLeftRight -> poly(0f to .5f, .22f to 0f, .22f to .3f, .78f to .3f, .78f to 0f, 1f to .5f, .78f to 1f, .78f to .7f, .22f to .7f, .22f to 1f, 0f to .5f)
            ShapeKind.BlockUpDown -> poly(.5f to 0f, 1f to .22f, .7f to .22f, .7f to .78f, 1f to .78f, .5f to 1f, 0f to .78f, .3f to .78f, .3f to .22f, 0f to .22f, .5f to 0f)
            ShapeKind.BlockFourWay -> poly(.5f to 0f, .66f to .18f, .56f to .18f, .56f to .44f, .82f to .44f, .82f to .34f, 1f to .5f, .82f to .66f, .82f to .56f, .56f to .56f,
                .56f to .82f, .66f to .82f, .5f to 1f, .34f to .82f, .44f to .82f, .44f to .56f, .18f to .56f, .18f to .66f, 0f to .5f, .18f to .34f, .18f to .44f, .44f to .44f,
                .44f to .18f, .34f to .18f, .5f to 0f)
            ShapeKind.Chevron -> poly(0f to 0f, .6f to 0f, 1f to .5f, .6f to 1f, 0f to 1f, .4f to .5f, 0f to 0f)
            ShapeKind.HomePlate -> poly(0f to 0f, .7f to 0f, 1f to .5f, .7f to 1f, 0f to 1f, 0f to 0f)
            ShapeKind.CalloutRect -> poly(0f to 0f, 1f to 0f, 1f to .75f, .45f to .75f, .25f to 1f, .25f to .75f, 0f to .75f, 0f to 0f)
            ShapeKind.CalloutRounded -> rounded(listOf(0f to 0f, 1f to 0f, 1f to .75f, .45f to .75f, .25f to 1f, .25f to .75f, 0f to .75f), .12f)
            ShapeKind.CalloutOval -> buildList {
                val tail = 100.0 to 128.0
                for (i in 0..72) {
                    val deg = i * 5.0
                    if (deg > tail.first && deg < tail.second) { if (deg < tail.first + 5.0) add(p(.2f, 1f)); continue }
                    val t = deg * PI / 180; add(p(.5f + .5f * cos(t).toFloat(), .4f + .4f * sin(t).toFloat()))
                }
            }
            ShapeKind.Starburst -> (0..24).map { i ->
                val t = -PI / 2 + i * PI / 12; val r = if (i % 2 == 0) 1f else .62f
                p(.5f + .5f * r * cos(t).toFloat(), .5f + .5f * r * sin(t).toFloat())
            }
            ShapeKind.Hemisphere -> ellipse(.5f, .7f, .5f, .7f, PI, 2 * PI, 32) + ellipse(.5f, .7f, .5f, .14f, 0.0, 2 * PI, 40)
            ShapeKind.TriPrism -> polyhedron(
                listOf(p(0f, 1f), p(.7f, 1f), p(.35f, .4f), p(.3f, .6f), p(1f, .6f), p(.65f, 0f)),
                listOf(0 to 1, 1 to 2, 2 to 0, 3 to 4, 4 to 5, 5 to 3, 0 to 3, 1 to 4, 2 to 5))
            ShapeKind.Frustum -> ellipse(.5f, .86f, .5f, .14f, 0.0, 2 * PI, 40) + listOf(p(.8f, .14f)) + ellipse(.5f, .14f, .3f, .1f, 0.0, 2 * PI, 32) +
                ellipse(.5f, .14f, .3f, .1f, 0.0, PI, 16) + listOf(p(0f, .86f))
            ShapeKind.Axes2D -> listOf(p(.1f, .9f), p(.1f, 0f)) + head(p(.1f, 0f), 0f, -1f) + listOf(p(.1f, .9f), p(1f, .9f)) + head(p(1f, .9f), 1f, 0f)
            ShapeKind.Axes3D -> listOf(p(.35f, .65f), p(.35f, 0f)) + head(p(.35f, 0f), 0f, -1f) + listOf(p(.35f, .65f), p(1f, .65f)) + head(p(1f, .65f), 1f, 0f) +
                listOf(p(.35f, .65f), p(0f, 1f)) + head(p(0f, 1f), -1f, 1f)
            ShapeKind.NumberGrid -> poly(0f to 0f, 1f to 0f, 1f to .25f, 0f to .25f, 0f to .5f, 1f to .5f, 1f to .75f, 0f to .75f, 0f to 1f, 1f to 1f,
                1f to 0f, .75f to 0f, .75f to 1f, .5f to 1f, .5f to 0f, .25f to 0f, .25f to 1f, 0f to 1f, 0f to 0f)
            ShapeKind.Angle -> poly(.8f to .25f, 0f to 1f, 1f to 1f, .3f to 1f) + ellipse(0f, 1f, .3f, .3f, 0.0, -43 * PI / 180, 10)
            ShapeKind.RightAngle -> poly(0f to 0f, 0f to 1f, 1f to 1f, .22f to 1f, .22f to .78f, 0f to .78f)
            ShapeKind.Brace -> listOf(p(1f, 0f)) + quad(1f to 0f, .5f to 0f, .5f to .2f) + listOf(p(.5f, .3f)) + quad(.5f to .3f, .5f to .5f, 0f to .5f) +
                quad(0f to .5f, .5f to .5f, .5f to .7f) + listOf(p(.5f, .8f)) + quad(.5f to .8f, .5f to 1f, 1f to 1f)
            ShapeKind.Bracket -> poly(1f to 0f, 0f to 0f, 0f to 1f, 1f to 1f)
            ShapeKind.Paren -> listOf(p(.8f, 0f)) + quad(.8f to 0f, 0f to .5f, .8f to 1f, 20)
            ShapeKind.ElbowLine -> elbow()
            ShapeKind.ElbowArrow -> withHeads(elbow(), atEnd = true, atStart = false)
            ShapeKind.ElbowDouble -> withHeads(elbow(), atEnd = true, atStart = true)
            ShapeKind.SCurve -> scurve()
            ShapeKind.SCurveArrow -> withHeads(scurve(), atEnd = true, atStart = false)
            ShapeKind.SCurveDouble -> withHeads(scurve(), atEnd = true, atStart = true)
            ShapeKind.Inheritance -> {
                val tipBase = along(headSize * 1.4f)
                listOf(start, tipBase, along(headSize * 1.4f, headSize * .55f), finish, along(headSize * 1.4f, -headSize * .55f), along(headSize * 1.4f, headSize * .55f))
            }
            ShapeKind.Aggregation, ShapeKind.Composition -> {
                val far = along(diamond)
                val s1 = along(diamond / 2f, diamond * .3f); val s2 = along(diamond / 2f, -diamond * .3f)
                if (kind == ShapeKind.Aggregation) listOf(start, far, s1, finish, s2, far)
                else listOf(start, far, s1, finish, s2, far, finish, s1, s2)
            }
            else -> listOf(start, finish)
        }
    }

    private fun Double.pow3() = this * this * this
}
