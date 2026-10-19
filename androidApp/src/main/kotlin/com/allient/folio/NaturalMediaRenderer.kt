package com.allient.folio

import android.graphics.*
import kotlin.math.*

/** Paper grain is anchored to document space, independent of nib size and frame rate. */
internal class NaturalMediaRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val tile = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply {
        val random = java.util.Random(57421)
        val pixels = IntArray(128 * 128) { val tooth = random.nextInt(256); Color.argb(tooth, 255, 255, 255) }
        setPixels(pixels, 0, 128, 0, 0, 128, 128)
    }
    private val shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    // Grain contrast must run after tile sampling to keep paper gaps, and paint alpha stays opaque:
    // scaling alpha before a fixed contrast offset erased light strokes. Instead pressure moves the
    // tooth cutoff (light contact only marks paper peaks) and the peak opacity, never to zero.
    private val filters = arrayOfNulls<ColorMatrixColorFilter>(33)
    private var filterKey = Long.MIN_VALUE
    private fun grainFilter(color: Int, strength: Float, pressure: Float): ColorMatrixColorFilter {
        val key = color.toLong() shl 8 or (strength * 100).roundToInt().toLong()
        if (key != filterKey) { filters.fill(null); filterKey = key }
        val level = (pressure.coerceIn(0f, 1f) * 32f).roundToInt()
        filters[level]?.let { return it }
        val p = level / 32f
        val heavy = 170f * strength / (1f + 2f * strength)
        val cutoff = heavy + (1f - p) * (180f * strength - heavy).coerceAtLeast(0f)
        val peak = Color.alpha(color) * (.45f + .55f * p)
        val slope = peak / (255f - cutoff)
        val cm = floatArrayOf(
            0f,0f,0f,0f,Color.red(color).toFloat(),
            0f,0f,0f,0f,Color.green(color).toFloat(),
            0f,0f,0f,0f,Color.blue(color).toFloat(),
            0f,0f,0f,slope, -slope * cutoff)
        return ColorMatrixColorFilter(cm).also { filters[level] = it }
    }
    private val matrix = Matrix()

    fun draw(canvas: Canvas, points: List<InkPoint>, style: BrushStyle) {
        if (points.isEmpty()) return
        val pencil = style.name == "Pencil"
        paint.color = style.color
        paint.shader = shader
        // Constant grain density: a broad pencil shades with the side of the same lead.
        val grain = style.grain.coerceIn(.4f, 4f)
        val scale = if (grain.isFinite()) grain * .65f else .65f
        matrix.setScale(scale, scale)
        matrix.postRotate(if (style.angle.isFinite()) style.angle else 0f)
        try { shader.setLocalMatrix(matrix) } catch (_: Exception) { matrix.reset() }
        val baseAlpha = Color.alpha(style.color)
        val strength = if (style.texture.isFinite()) style.texture.coerceIn(0f, 1f) else 0f
        fun segment(a: InkPoint, b: InkPoint) {
            val p = ((a.pressure + b.pressure) * .5f).coerceIn(0f, 1f)
            paint.strokeWidth = style.width * if (pencil) (.68f + .32f * p) else (.5f + .5f * p)
            // Light contact still deposits faint graphite instead of disappearing.
            paint.alpha = 255
            paint.colorFilter = grainFilter(style.color, strength, p)
            canvas.drawLine(a.x * 600f, a.y * 960f, b.x * 600f, b.y * 960f, paint)
        }
        if (style.name == "Paint brush") { drawBristles(canvas, points, style); return }
        if (style.name == "Spray") {
            paint.shader = null; paint.colorFilter = null; paint.style = Paint.Style.FILL
            // Resample by distance, not event count, so fast and slow input have consistent density.
            var distance = 0f; var next = 0f; var dab = 0
            val spacing = max(.7f, style.width * .08f)
            fun spray(x: Float, y: Float, pressure: Float) {
                val random = java.util.Random(81281L + dab++ * 8191L)
                val dot = max(.7f, style.width * .03f) * style.grain.coerceIn(.4f, 4f)
                repeat(12) {
                    val angle = random.nextFloat() * PI * 2; val radius = sqrt(random.nextFloat()) * style.width * .5f
                    paint.alpha = (baseAlpha * (.5f + pressure * .5f) * (1f - radius / style.width)).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x + cos(angle).toFloat() * radius, y + sin(angle).toFloat() * radius, dot * (.6f + random.nextFloat() * .8f), paint)
                }
            }
            if (points.size == 1) spray(points[0].x * 600f, points[0].y * 960f, points[0].pressure)
            points.zipWithNext().forEach { (a, b) ->
                val ax = a.x * 600f; val ay = a.y * 960f; val dx = (b.x-a.x)*600f; val dy = (b.y-a.y)*960f
                val length = hypot(dx, dy).coerceIn(.0001f, 10000f)
                while (next <= distance + length && length > .0001f && distance < 10000f) {
                    val t = ((next-distance)/length).coerceIn(0f,1f)
                    spray(ax+dx*t, ay+dy*t, a.pressure+(b.pressure-a.pressure)*t); next += spacing
                }
                distance = (distance + length).coerceIn(0f, 10000f); next = next.coerceIn(0f, 10000f)
            }
            paint.style = Paint.Style.STROKE
        } else {
            if (points.size == 1) segment(points.first(), points.first())
            else for (i in 1 until points.size) segment(points[i-1], points[i])
        }
        paint.shader = null; paint.colorFilter = null
    }

    /** Round bristle brush: parallel bristle traces spread with pressure; texture = dryness (paint runs out along the stroke). */
    private fun drawBristles(canvas: Canvas, points: List<InkPoint>, style: BrushStyle) {
        paint.shader = null; paint.colorFilter = null; paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        val width = style.width.coerceIn(.5f, 200f)
        val count = (width * 1.4f / style.grain.coerceIn(.4f, 4f)).roundToInt().coerceIn(5, 40)
        val random = java.util.Random(4271L + count)
        val offsets = FloatArray(count) { (it + .5f) / count - .5f + (random.nextFloat() - .5f) * .6f / count }
        val thickness = FloatArray(count) { width / count * (1.2f + random.nextFloat() * 1.2f) }
        val load = FloatArray(count) { .55f + random.nextFloat() * .45f }
        val dryness = (if (style.texture.isFinite()) style.texture else 0f).coerceIn(0f, 1f)
        val baseAlpha = Color.alpha(style.color)
        paint.color = style.color
        if (points.size == 1) {
            val p = points[0]; paint.style = Paint.Style.FILL; paint.alpha = (baseAlpha * (.5f + .5f * p.pressure)).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x * 600f, p.y * 960f, width * (.25f + .25f * p.pressure), paint); paint.style = Paint.Style.STROKE; return
        }
        // Smoothed per-point normals keep bristles parallel through curves. Each bristle is drawn as continuous
        // paths in short butt-capped chunks: per-segment round caps overlapped and printed a ladder pattern.
        val n = points.size
        val xs = FloatArray(n) { points[it].x * 600f }; val ys = FloatArray(n) { points[it].y * 960f }
        val nxs = FloatArray(n); val nys = FloatArray(n); val dist = FloatArray(n)
        for (i in 0 until n) {
            val dx = xs[minOf(i + 1, n - 1)] - xs[maxOf(i - 1, 0)]; val dy = ys[minOf(i + 1, n - 1)] - ys[maxOf(i - 1, 0)]
            val len = hypot(dx, dy)
            if (len > .001f && len.isFinite()) { nxs[i] = -dy / len; nys[i] = dx / len } else if (i > 0) { nxs[i] = nxs[i - 1]; nys[i] = nys[i - 1] } else nys[i] = 1f
            if (i > 0) dist[i] = dist[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]).let { if (it.isFinite()) it else 0f }
        }
        paint.strokeCap = Paint.Cap.BUTT; paint.strokeJoin = Paint.Join.ROUND
        val chunk = max(4f, width * 1.5f)
        val path = Path()
        for (k in 0 until count) {
            var start = 0
            while (start < n - 1) {
                var end = start + 1
                while (end < n - 1 && dist[end] - dist[start] < chunk) end++
                var pressure = 0f
                path.rewind()
                for (i in start..end) {
                    val p = points[i].pressure.coerceIn(0f, 1f); pressure += p
                    val spread = width * (.35f + .65f * p) * offsets[k]
                    if (i == start) path.moveTo(xs[i] + nxs[i] * spread, ys[i] + nys[i] * spread) else path.lineTo(xs[i] + nxs[i] * spread, ys[i] + nys[i] * spread)
                }
                pressure /= (end - start + 1)
                // Paint runs out over a distance proportional to brush size; dry bristles drop out first.
                val remaining = (1f - dryness * dist[end] / (width * 45f)).coerceIn(.2f, 1f)
                val deposit = load[k] * remaining - dryness * .35f * (1f - load[k])
                if (deposit > .05f) {
                    paint.strokeWidth = thickness[k] * (.6f + .4f * pressure)
                    paint.alpha = (baseAlpha * deposit * (.45f + .55f * pressure)).toInt().coerceIn(0, 255)
                    canvas.drawPath(path, paint)
                }
                start = end
            }
        }
        paint.strokeCap = Paint.Cap.ROUND
    }
    companion object { fun supports(name: String) = name in setOf("Pencil", "Chalk", "Spray", "Paint brush") }
}
