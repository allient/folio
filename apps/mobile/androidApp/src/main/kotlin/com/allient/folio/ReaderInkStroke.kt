package com.allient.folio

import android.graphics.PointF
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reader ink anchors. Legacy strokes are `{page, points:[[x,y]…], width, normalizedWidth}` polylines in PDF page space.
 * Notebook-brush strokes add `brush`, `engine`, `variant`, `texture`, `grain`, `angle`, `sensitivity` and a `pressures`
 * array parallel to `points`. Points stay two-coordinate, so [com.allient.folio.reader.ReaderStore] validation and the
 * PDF export (which reads `points` and `width`) are unchanged. Brush widths use the notebook's 600 px page, so
 * `normalizedWidth = brush.width / 600`; the annotation's own color is the stroke color.
 */
internal object ReaderInk {
    const val PAGE_WIDTH = JetpackInkEngine.DOCUMENT_WIDTH
    const val PAGE_HEIGHT = JetpackInkEngine.DOCUMENT_HEIGHT
    const val LEGACY_WIDTH = .003f

    fun normalizedWidth(brush: BrushStyle) = (brush.width / PAGE_WIDTH).coerceIn(.0001f, .2f)
    fun normalizedWidth(anchor: JSONObject) = anchor.optDouble("normalizedWidth", LEGACY_WIDTH.toDouble()).toFloat()
    fun isBrush(anchor: JSONObject) = anchor.optString("brush").isNotEmpty()

    /** Adds the brush fields and pressures (rounded to 3 decimals) to an anchor from [PdfReaderSession.inkAnchor]. */
    fun withBrush(anchor: JSONObject, brush: BrushStyle, pressures: List<Float>, times: List<Long> = emptyList()): JSONObject = anchor
        .put("normalizedWidth", normalizedWidth(brush).toDouble())
        .put("brush", brush.name).put("engine", JetpackInkEngine.FORMAT).put("variant", brush.variant)
        .put("texture", brush.texture.toDouble()).put("grain", brush.grain.toDouble())
        .put("angle", brush.angle.toDouble()).put("sensitivity", brush.sensitivity.toDouble())
        .put("pressures", JSONArray().apply { pressures.forEach { put(Math.round(it.coerceIn(0f, 1f) * 1000) / 1000.0) } })
        .also { json -> if (times.size == pressures.size && times.isNotEmpty()) json.put("times", JSONArray().apply { times.forEach { put(it) } }) }

    /** The brush a stroke was drawn with, or null for legacy polyline ink. */
    fun brush(anchor: JSONObject, color: Int): BrushStyle? = if (!isBrush(anchor)) null else BrushStyle(
        anchor.getString("brush"), normalizedWidth(anchor) * PAGE_WIDTH, color,
        sensitivity = anchor.optDouble("sensitivity", .5).toFloat(), texture = anchor.optDouble("texture", .65).toFloat(),
        grain = anchor.optDouble("grain", 1.0).toFloat(), angle = anchor.optDouble("angle", 0.0).toFloat(), variant = anchor.optString("variant"))

    /** One pressure per point; missing entries (truncated or hand-edited anchors) draw at full pressure. */
    fun pressures(anchor: JSONObject, count: Int): List<Float> {
        val values = anchor.optJSONArray("pressures")
        return List(count) { index -> values?.optDouble(index, 1.0)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f }
    }

    /**
     * Milliseconds since pen-down for each point. The brush engine's size and smoothing depend on input timing, so the saved
     * stroke replays with the real times and looks exactly like the live one. Older strokes without times fall back to 1 ms steps.
     */
    fun times(anchor: JSONObject, count: Int): List<Long> {
        val values = anchor.optJSONArray("times")
        if (values == null || values.length() != count) return List(count) { it.toLong() }
        return List(count) { index -> values.optLong(index, index.toLong()) }
    }

    /** Page height → stroke height: y in a page [PAGE_HEIGHT]/[PAGE_WIDTH] times taller than wide, so brushes scale uniformly. */
    fun strokeY(pageY: Float, aspect: Float) = pageY * PAGE_WIDTH / (aspect * PAGE_HEIGHT)
    /** Height to pass to [NotebookRenderer.drawStroke] for a page drawn [pageWidth] px wide. */
    fun strokeHeight(pageWidth: Float) = pageWidth * PAGE_HEIGHT / PAGE_WIDTH

    /**
     * Converts a brush anchor to an [InkStroke]. [points] are page fractions (0..1 of the page width and height) from
     * [PdfReaderSession.project], which already handles PDF page space and rotation; [aspect] is page width / height.
     * Returns null for legacy anchors so they keep the polyline renderer.
     */
    fun toInkStroke(id: String, anchor: JSONObject, color: Int, points: List<PointF>, aspect: Float): InkStroke? {
        val brush = brush(anchor, color) ?: return null
        val pressures = pressures(anchor, points.size)
        val times = times(anchor, points.size)
        return InkStroke(brush.name, color, brush.width, points.mapIndexed { i, p -> InkPoint(p.x, strokeY(p.y, aspect), pressures[i], times[i]) },
            id = id, engine = anchor.optString("engine").ifEmpty { JetpackInkEngine.FORMAT },
            texture = brush.texture, grain = brush.grain, angle = brush.angle, variant = brush.variant)
    }
}
