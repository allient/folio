package com.example.knotes

import androidx.compose.ui.geometry.Offset

internal data class BrushStyle(val name: String, val width: Float, val color: Int, val eraser: Boolean = false, val sensitivity: Float = .5f, val presetId: String = name, val label: String = name,
    val texture: Float = .65f, val grain: Float = 1f, val angle: Float = 0f, val variant: String = "")
internal data class PressureProfile(val name: String, val threshold: Float)

internal val brushes = listOf(
    BrushStyle("Pen", 2f, android.graphics.Color.rgb(35, 41, 39)),
    BrushStyle("Pencil", 3f, android.graphics.Color.rgb(75, 71, 66), variant = "hb", texture = .72f),
    BrushStyle("Marker", 9f, android.graphics.Color.argb(92, 234, 194, 67)),
    BrushStyle("Fountain pen", 4f, android.graphics.Color.rgb(35, 41, 39)),
    BrushStyle("Brush pen", 7f, android.graphics.Color.rgb(35, 41, 39)),
    BrushStyle("Fineliner", 1.8f, android.graphics.Color.rgb(35, 41, 39)),
    BrushStyle("Highlighter", 16f, android.graphics.Color.argb(70, 247, 211, 59)),
    BrushStyle("Gel pen", 3.2f, android.graphics.Color.rgb(0, 80, 160)),
    BrushStyle("Calligraphy", 6f, android.graphics.Color.rgb(60, 40, 20)),
    BrushStyle("Spray", 14f, android.graphics.Color.rgb(120, 120, 120)),
    BrushStyle("Chalk", 12f, android.graphics.Color.rgb(200, 200, 200)),
    BrushStyle("Paint brush", 10f, android.graphics.Color.rgb(47, 93, 138), texture = .15f),
    BrushStyle("Eraser", 22f, android.graphics.Color.TRANSPARENT, eraser = true),
)

/** Contact quick profiles only set the activation threshold; curve shape comes from [CurvePreset]. */
internal val pressureProfiles = listOf(
    PressureProfile("Soft", 0.03f),
    PressureProfile("Balanced", 0.08f),
    PressureProfile("Firm", 0.16f),
)

internal enum class CurvePreset(val label: String, val points: List<Offset>) {
    Linear("Linear", listOf(Offset(0f, 0f), Offset(.5f, .5f), Offset(1f, 1f))),
    Exponential("Exponential", listOf(Offset(0f, 0f), Offset(.66f, .33f), Offset(1f, 1f))),
    Logarithmic("Logarithmic", listOf(Offset(0f, 0f), Offset(.33f, .66f), Offset(1f, 1f))),
    SCurve("S-curve", listOf(Offset(0f, 0f), Offset(.3f, .12f), Offset(.7f, .88f), Offset(1f, 1f))),
    InverseS("Inverse S", listOf(Offset(0f, 0f), Offset(.25f, .42f), Offset(.75f, .58f), Offset(1f, 1f))),
    Custom("Custom", emptyList()),
}

/**
 * Monotone cubic (Fritsch-Carlson) spline through sorted control points. Tangents are
 * precomputed so [value] is allocation-free on the input path.
 */
internal class ResponseCurve(val preset: CurvePreset, points: List<Offset>) {
    val points: List<Offset> = sanitize(points)
    private val xs = FloatArray(this.points.size) { this.points[it].x }
    private val ys = FloatArray(this.points.size) { this.points[it].y }
    private val ms = FloatArray(this.points.size)

    init {
        val n = xs.size
        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
        if (n == 2) { ms[0] = d[0]; ms[1] = d[0] } else {
            for (i in 1 until n - 1) {
                val h0 = xs[i] - xs[i - 1]; val h1 = xs[i + 1] - xs[i]
                ms[i] = if (d[i - 1] * d[i] <= 0f) 0f else 3f * (h0 + h1) / ((2f * h1 + h0) / d[i - 1] + (h1 + 2f * h0) / d[i])
            }
            ms[0] = endTangent(xs[1] - xs[0], xs[2] - xs[1], d[0], d[1])
            ms[n - 1] = endTangent(xs[n - 1] - xs[n - 2], xs[n - 2] - xs[n - 3], d[n - 2], d[n - 3])
        }
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { ms[i] = 0f; ms[i + 1] = 0f; continue }
            val a = ms[i] / d[i]; val b = ms[i + 1] / d[i]; val s = a * a + b * b
            if (s > 9f) { val t = 3f / kotlin.math.sqrt(s); ms[i] = t * a * d[i]; ms[i + 1] = t * b * d[i] }
        }
    }

    fun value(input: Float): Float {
        val x = if (input.isNaN()) 0f else input.coerceIn(0f, 1f)
        var i = 0
        while (i < xs.size - 2 && x > xs[i + 1]) i++
        val h = xs[i + 1] - xs[i]
        val t = ((x - xs[i]) / h).coerceIn(0f, 1f)
        val t2 = t * t; val t3 = t2 * t
        return ((2f * t3 - 3f * t2 + 1f) * ys[i] + (t3 - 2f * t2 + t) * h * ms[i] +
            (-2f * t3 + 3f * t2) * ys[i + 1] + (t3 - t2) * h * ms[i + 1]).coerceIn(0f, 1f)
    }

    fun withPoints(points: List<Offset>) = ResponseCurve(CurvePreset.Custom, points)
    fun encode(): String = points.joinToString(";") { "${it.x},${it.y}" }
    override fun equals(other: Any?) = other is ResponseCurve && other.preset == preset && other.points == points
    override fun hashCode() = preset.hashCode() * 31 + points.hashCode()

    companion object {
        fun of(preset: CurvePreset) = ResponseCurve(preset, preset.points)
        fun decode(preset: String?, points: String?): ResponseCurve? {
            val kind = CurvePreset.entries.firstOrNull { it.name == preset } ?: return null
            val parsed = points?.split(';')?.mapNotNull { pair ->
                val parts = pair.split(',')
                val x = parts.getOrNull(0)?.toFloatOrNull(); val y = parts.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null && x.isFinite() && y.isFinite()) Offset(x, y) else null
            }.orEmpty()
            return if (parsed.size >= 2) ResponseCurve(kind, parsed) else if (kind != CurvePreset.Custom) of(kind) else null
        }
        /** Converts the legacy two-control-point Bezier into spline points sampled from it. */
        fun fromLegacyBezier(first: Offset, second: Offset) = ResponseCurve(CurvePreset.Custom,
            listOf(0f, 1f / 3f, 2f / 3f, 1f).map { Offset(it, legacyBezierValue(it, first, second)) })

        private fun endTangent(h0: Float, h1: Float, d0: Float, d1: Float): Float {
            val m = ((2f * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
            return when { m * d0 <= 0f -> 0f; d0 * d1 <= 0f && kotlin.math.abs(m) > 3f * kotlin.math.abs(d0) -> 3f * d0; else -> m }
        }

        /** Sorted by x, endpoints pinned to x = 0 and 1, y clamped and non-decreasing. */
        private fun sanitize(points: List<Offset>): List<Offset> {
            val sorted = points.filter { it.x.isFinite() && it.y.isFinite() }.sortedBy { it.x }
            if (sorted.size < 2) return CurvePreset.Linear.points
            val out = ArrayList<Offset>(sorted.size)
            sorted.forEachIndexed { index, p ->
                val x = when (index) { 0 -> 0f; sorted.lastIndex -> 1f; else -> p.x.coerceIn(.01f, .99f) }
                if (out.isNotEmpty() && x - out.last().x < .005f) return@forEachIndexed
                out += Offset(x, p.y.coerceIn(out.lastOrNull()?.y ?: 0f, 1f))
            }
            if (out.last().x < 1f) out[out.lastIndex] = Offset(1f, out.last().y)
            return if (out.size < 2) CurvePreset.Linear.points else out
        }
    }
}

internal val defaultPressureCurve = ResponseCurve.of(CurvePreset.Exponential)
internal val defaultVelocityCurve = ResponseCurve.of(CurvePreset.Linear)
internal val defaultTiltCurve = ResponseCurve.of(CurvePreset.Logarithmic)

internal fun curveValue(input: Float, curve: ResponseCurve): Float = curve.value(input)

/** A linear secondary-channel curve has a neutral multiplier of one. */
internal fun curveScale(input: Float, curve: ResponseCurve): Float {
    val x = input.coerceIn(0f, 1f)
    if (x < .015f) return 1f
    return (curve.value(x) / x).coerceIn(.2f, 2.5f)
}

private fun legacyBezierValue(input: Float, first: Offset, second: Offset): Float {
    val x = input.coerceIn(0f, 1f)
    var low = 0f
    var high = 1f
    repeat(18) {
        val t = (low + high) / 2f
        val inverse = 1f - t
        val bezierX = 3f * inverse * inverse * t * first.x + 3f * inverse * t * t * second.x + t * t * t
        if (bezierX < x) low = t else high = t
    }
    val t = (low + high) / 2f
    val inverse = 1f - t
    return (3f * inverse * inverse * t * first.y + 3f * inverse * t * t * second.y + t * t * t).coerceIn(0f, 1f)
}

/** Brush feel follows contact filtering; the resulting pressure is stored with each point. */
internal fun BrushStyle.mapPressure(pressure: Float): Float = if (!eraser)
    Math.pow(pressure.coerceIn(0f, 1f).toDouble(), 1.8 - sensitivity.coerceIn(0f, 1f) * 1.6).toFloat()
else pressure.coerceIn(0f, 1f)

/** Convert document width units to physical millimetres using the actual drawing viewport and reported panel DPI. */
internal fun brushWidthMillimetres(documentUnits: Float, viewportWidthPx: Int, xdpi: Float): Float {
    if (viewportWidthPx <= 0 || !xdpi.isFinite() || xdpi < 40f) return documentUnits * 25.4f / 160f
    return documentUnits * viewportWidthPx / JetpackInkEngine.DOCUMENT_WIDTH / xdpi * 25.4f
}

internal fun brushWidthDocumentUnits(millimetres: Float, viewportWidthPx: Int, xdpi: Float): Float {
    if (viewportWidthPx <= 0 || !xdpi.isFinite() || xdpi < 40f) return millimetres * 160f / 25.4f
    return millimetres * xdpi / 25.4f * JetpackInkEngine.DOCUMENT_WIDTH / viewportWidthPx
}
