package com.allient.folio

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D solids stay one `shape-v1` stroke. Each point's pressure says how the segment that reaches it is drawn:
 * a visible edge, a hidden edge (dashed), a pen lift, or a point of the silhouette that only shapes the fill.
 * Transforms touch x and y alone, so the codes survive moving, scaling, rotating and mirroring.
 */
internal object SolidPen {
    const val EDGE = 1f
    const val HIDDEN = .5f
    const val SILHOUETTE = .25f
    const val LIFT = 0f

    val kinds = setOf(ShapeKind.Cube, ShapeKind.Cuboid, ShapeKind.Pyramid, ShapeKind.Cone, ShapeKind.Cylinder,
        ShapeKind.Sphere, ShapeKind.Hemisphere, ShapeKind.TriPrism, ShapeKind.Frustum)

    fun isCoded(stroke: InkStroke) = stroke.engine == "shape-v1" && stroke.points.any { it.pressure < EDGE }
    fun silhouette(points: List<InkPoint>) = points.filter { it.pressure == SILHOUETTE }

    /** Outline codes for a solid inside the box a–b, or null when [kind] is not a solid. */
    fun points(kind: ShapeKind, a: Offset, b: Offset): List<Pair<Offset, Float>>? {
        if (kind !in kinds) return null
        val out = mutableListOf<Pair<Offset, Float>>()
        fun p(x: Float, y: Float) = Offset(a.x + (b.x - a.x) * x, a.y + (b.y - a.y) * y)
        fun ps(vararg v: Pair<Float, Float>) = v.map { p(it.first, it.second) }
        // Angles run clockwise on screen: 0..π is the front (lower) half of a ground ellipse, π..2π the back half.
        fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Double, to: Double, steps: Int = 32) =
            (0..steps).map { i -> val t = from + (to - from) * i / steps; p(cx + rx * cos(t).toFloat(), cy + ry * sin(t).toFloat()) }
        fun line(code: Float, path: List<Offset>) { out += path.first() to LIFT; path.drop(1).forEach { out += it to code } }
        fun edge(path: List<Offset>) = line(EDGE, path)
        fun hidden(path: List<Offset>) = line(HIDDEN, path)
        fun silhouette(path: List<Offset>) { path.forEach { out += it to SILHOUETTE } }
        when (kind) {
            ShapeKind.Cube, ShapeKind.Cuboid -> {
                // Front face lower left, back face shifted up and right; only the back bottom-left corner is hidden.
                edge(ps(0f to .25f, .7f to .25f, .7f to 1f, 0f to 1f, 0f to .25f, .3f to 0f, 1f to 0f, .7f to .25f))
                edge(ps(1f to 0f, 1f to .75f, .7f to 1f))
                hidden(ps(.3f to 0f, .3f to .75f, 1f to .75f)); hidden(ps(.3f to .75f, 0f to 1f))
                silhouette(ps(0f to .25f, .3f to 0f, 1f to 0f, 1f to .75f, .7f to 1f, 0f to 1f))
            }
            ShapeKind.Pyramid -> {
                edge(ps(.5f to 0f, 0f to .72f, .52f to 1f, 1f to .72f, .5f to 0f)); edge(ps(.5f to 0f, .52f to 1f))
                hidden(ps(0f to .72f, .5f to .48f, 1f to .72f)); hidden(ps(.5f to 0f, .5f to .48f))
                silhouette(ps(.5f to 0f, 0f to .72f, .52f to 1f, 1f to .72f))
            }
            ShapeKind.TriPrism -> {
                edge(ps(0f to 1f, .7f to 1f, .35f to .4f, 0f to 1f)); edge(ps(.7f to 1f, 1f to .6f, .65f to 0f, .35f to .4f))
                hidden(ps(0f to 1f, .3f to .6f, 1f to .6f)); hidden(ps(.3f to .6f, .65f to 0f))
                silhouette(ps(0f to 1f, .7f to 1f, 1f to .6f, .65f to 0f, .35f to .4f))
            }
            ShapeKind.Cone -> {
                val front = arc(.5f, .86f, .5f, .14f, 0.0, PI)
                edge(listOf(p(.5f, 0f)) + front + p(.5f, 0f))
                hidden(arc(.5f, .86f, .5f, .14f, PI, 2 * PI))
                silhouette(listOf(p(.5f, 0f)) + front)
            }
            ShapeKind.Cylinder, ShapeKind.Frustum -> {
                val top = if (kind == ShapeKind.Cylinder) .5f else .3f
                val topRy = if (kind == ShapeKind.Cylinder) .14f else .1f
                val front = arc(.5f, .86f, .5f, .14f, 0.0, PI)
                edge(listOf(p(.5f + top, .14f)) + front + p(.5f - top, .14f))
                edge(arc(.5f, .14f, top, topRy, 0.0, 2 * PI, 48))
                hidden(arc(.5f, .86f, .5f, .14f, PI, 2 * PI))
                silhouette(arc(.5f, .14f, top, topRy, PI, 2 * PI) + front)
            }
            ShapeKind.Sphere -> {
                val outline = arc(.5f, .5f, .5f, .5f, 0.0, 2 * PI, 64)
                edge(outline); edge(arc(.5f, .5f, .5f, .13f, 0.0, PI)); hidden(arc(.5f, .5f, .5f, .13f, PI, 2 * PI))
                silhouette(outline)
            }
            ShapeKind.Hemisphere -> {
                val dome = arc(.5f, .7f, .5f, .7f, PI, 2 * PI)
                val front = arc(.5f, .7f, .5f, .14f, 0.0, PI)
                edge(dome + front.drop(1)); hidden(arc(.5f, .7f, .5f, .14f, PI, 2 * PI))
                silhouette(dome + front)
            }
            else -> return null
        }
        return out
    }
}
