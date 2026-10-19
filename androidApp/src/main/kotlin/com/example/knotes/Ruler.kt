package com.example.knotes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

internal enum class RulerButton { LockPosition, LockAngle }

/**
 * On-screen straightedge. Design after XNotes (MIT, github.com/shardulvs/xnotes-android, see docs/ruler-and-zoom-window.md):
 * an infinite band fixed in screen space, so only the perpendicular offset matters for hit-testing and snapping.
 * Pure geometry in viewport pixels and radians; drawing lives in [RulerRenderer].
 */
internal class Ruler {
    var visible = false
    var cx = 0f
    var cy = 0f
    var angle = 0f
    var thickness = 0f
    var lockPosition = false
    var lockAngle = false
    var initialized = false

    val dx get() = cos(angle)
    val dy get() = sin(angle)
    /** Signed perpendicular distance of a point from the centre line. */
    fun across(x: Float, y: Float) = (x - cx) * -dy + (y - cy) * dx
    fun bodyContains(x: Float, y: Float) = abs(across(x, y)) <= thickness / 2f

    /** Project a point onto one long edge (+1 = +normal side), keeping its position along the band. */
    fun projectToEdge(x: Float, y: Float, side: Int): PointF {
        val offset = across(x, y) - side * thickness / 2f
        return PointF(x + dy * offset, y - dx * offset)
    }

    fun placeDefault(width: Int, height: Int, density: Float) {
        cx = width / 2f; cy = height * .45f; thickness = 96f * density; angle = 0f; initialized = true
    }

    private fun span(width: Int, height: Int) = minOf(width, height).toFloat()
    fun buttonRadius() = thickness * .17f
    fun handleRadius() = thickness * .2f
    fun handleDistance(width: Int, height: Int) = span(width, height) * .26f
    fun labelDistance(width: Int, height: Int) = span(width, height) * .42f

    fun buttonCenters(): List<Pair<RulerButton, PointF>> {
        val step = buttonRadius() * 2.6f
        return listOf(RulerButton.LockPosition to PointF(cx - dx * step / 2f, cy - dy * step / 2f),
            RulerButton.LockAngle to PointF(cx + dx * step / 2f, cy + dy * step / 2f))
    }
    fun hitButton(x: Float, y: Float, tolerance: Float): RulerButton? =
        buttonCenters().firstOrNull { hypot(it.second.x - x, it.second.y - y) <= maxOf(tolerance, buttonRadius()) }?.first
    fun handleCenters(distance: Float) = listOf(PointF(cx + dx * distance, cy + dy * distance), PointF(cx - dx * distance, cy - dy * distance))
    /** 0 for the +direction handle, 1 for the other. */
    fun hitHandle(x: Float, y: Float, distance: Float, tolerance: Float): Int? =
        handleCenters(distance).indexOfFirst { hypot(it.x - x, it.y - y) <= maxOf(tolerance, handleRadius()) }.takeIf { it >= 0 }

    companion object {
        const val AXIS_SNAP_DEGREES = 4.0
        /** Pull an angle onto the nearest horizontal or vertical orientation when within [AXIS_SNAP_DEGREES]. */
        fun snapToAxes(raw: Float): Float {
            val quarter = (PI / 2).toFloat()
            val nearest = round(raw / quarter) * quarter
            return if (abs(raw - nearest) <= Math.toRadians(AXIS_SNAP_DEGREES)) nearest else raw
        }
    }
}

internal object RulerRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEBF0F0F6.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF55575C.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2D31.toInt(); strokeWidth = 1.6f }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2D31.toInt(); textAlign = Paint.Align.CENTER }
    private val button = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFF26282C.toInt() }
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7F7FA.toInt() }
    private val boxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3C41.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val ticks = FloatArray(4 * 2400)

    /** [pxPerCm] is the on-screen length of one centimetre of page content at the current zoom. */
    fun draw(canvas: Canvas, ruler: Ruler, width: Int, height: Int, density: Float, pxPerCm: Float) {
        val reach = hypot(width.toFloat(), height.toFloat())
        val half = ruler.thickness / 2f
        canvas.save()
        canvas.translate(ruler.cx, ruler.cy); canvas.rotate(Math.toDegrees(ruler.angle.toDouble()).toFloat())
        canvas.drawRect(-reach, -half, reach, half, fill)
        canvas.drawLine(-reach, -half, reach, -half, edge); canvas.drawLine(-reach, half, reach, half, edge)

        val mm = pxPerCm / 10f
        val showMm = mm >= 3.2f
        var cmStep = 1
        while (pxPerCm * cmStep < 44f * density / 2f && cmStep < 100) cmStep = when (cmStep) { 1 -> 2; 2 -> 5; else -> cmStep * 2 }
        val unit = if (showMm) mm else pxPerCm * cmStep
        val first = (-reach / unit).toInt() - 1; val last = (reach / unit).toInt() + 1
        var n = 0
        text.textSize = ruler.thickness * .2f
        for (i in first..last) {
            if (n + 8 > ticks.size) break
            val s = i * unit
            val isCm = if (showMm) i % 10 == 0 else true
            val isHalf = showMm && i % 5 == 0
            val length = ruler.thickness * if (isCm) .3f else if (isHalf) .2f else .12f
            ticks[n++] = s; ticks[n++] = -half; ticks[n++] = s; ticks[n++] = -half + length
            ticks[n++] = s; ticks[n++] = half; ticks[n++] = s; ticks[n++] = half - length
            if (isCm && keepLabel(s, ruler, width, height)) { val cm = if (showMm) i / 10 else i * cmStep; canvas.drawText(abs(cm).toString(), s, text.textSize * .35f, text) }
        }
        canvas.drawLines(ticks, 0, n, tick)
        canvas.restore()

        val radius = ruler.buttonRadius()
        ruler.buttonCenters().forEach { (kind, p) ->
            val on = if (kind == RulerButton.LockPosition) ruler.lockPosition else ruler.lockAngle
            button.color = if (on) 0xFF2B2D31.toInt() else 0xFFE4E4EA.toInt()
            canvas.drawCircle(p.x, p.y, radius, button)
            glyph.strokeWidth = 2.2f * density; glyph.color = if (on) 0xFFFFFFFF.toInt() else 0xFF26282C.toInt()
            val g = radius * .5f
            if (kind == RulerButton.LockPosition) {
                canvas.drawCircle(p.x, p.y, g * .6f, glyph)
                canvas.drawLine(p.x - g * 1.2f, p.y, p.x + g * 1.2f, p.y, glyph); canvas.drawLine(p.x, p.y - g * 1.2f, p.x, p.y + g * 1.2f, glyph)
            } else {
                canvas.drawLine(p.x - g, p.y + g * .7f, p.x + g, p.y + g * .7f, glyph); canvas.drawLine(p.x - g, p.y + g * .7f, p.x + g * .6f, p.y - g * .8f, glyph)
            }
        }
        val handleR = ruler.handleRadius()
        ruler.handleCenters(ruler.handleDistance(width, height)).forEach { p ->
            button.color = 0xFFF2F2F7.toInt(); canvas.drawCircle(p.x, p.y, handleR, button)
            boxEdge.strokeWidth = 2.4f * density; canvas.drawCircle(p.x, p.y, handleR, boxEdge)
            glyph.strokeWidth = 2.2f * density; glyph.color = 0xFF26282C.toInt()
            val arc = RectF(p.x - handleR * .5f, p.y - handleR * .5f, p.x + handleR * .5f, p.y + handleR * .5f)
            canvas.drawArc(arc, 200f, 120f, false, glyph); canvas.drawArc(arc, 20f, 120f, false, glyph)
        }
        val degrees = ((Math.toDegrees(ruler.angle.toDouble()) % 360 + 360) % 360).let { round(it).toInt() % 360 }
        text.textSize = ruler.thickness * .3f; text.typeface = android.graphics.Typeface.DEFAULT_BOLD
        for (side in listOf(-1, 1)) {
            val d = ruler.labelDistance(width, height) * side
            val x = ruler.cx + ruler.dx * d; val y = ruler.cy + ruler.dy * d
            val w = text.measureText("359°") * .62f + 18f * density; val h = text.textSize * 1.4f
            canvas.drawRect(x - w / 2, y - h / 2, x + w / 2, y + h / 2, box); canvas.drawRect(x - w / 2, y - h / 2, x + w / 2, y + h / 2, boxEdge)
            canvas.drawText("$degrees°", x, y + text.textSize * .35f, text)
        }
        text.typeface = android.graphics.Typeface.DEFAULT
    }

    /** Labels yield to the buttons, rotation handles and angle read-outs that sit on the band. */
    private fun keepLabel(s: Float, ruler: Ruler, width: Int, height: Int): Boolean {
        val clear = ruler.handleRadius() * 1.5f
        if (abs(s) < ruler.buttonRadius() * 3.4f) return false
        val handle = ruler.handleDistance(width, height); val label = ruler.labelDistance(width, height)
        if (abs(abs(s) - handle) < clear) return false
        return abs(abs(s) - label) >= ruler.thickness * .9f
    }

    fun angleBetween(ax: Float, ay: Float, bx: Float, by: Float) = atan2(by - ay, bx - ax)
}
