package com.allient.folio

import android.graphics.PointF
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReaderInkStrokeTest {
    private val legacy = "{\"page\":0,\"points\":[[10.0,700.0],[20.0,690.0]],\"width\":1.8,\"normalizedWidth\":0.003}"

    @Test fun brushAnchorRoundTripsBrushFieldsAndPressuresWithTwoCoordinatePoints() {
        val brush = BrushStyle("Fountain pen", 4.5f, 0xFF102030.toInt(), sensitivity = .7f, texture = .4f, grain = .8f, angle = 30f, variant = "fine")
        val json = ReaderInk.withBrush(JSONObject(legacy), brush, listOf(.25f, .75f))
        val saved = JSONObject(json.toString())
        assertTrue(ReaderInk.isBrush(saved))
        val points = saved.getJSONArray("points")
        (0 until points.length()).forEach { assertEquals(2, points.getJSONArray(it).length()) }
        assertEquals(points.length(), saved.getJSONArray("pressures").length())
        assertEquals(listOf(.25f, .75f), ReaderInk.pressures(saved, 2))
        assertEquals(4.5f / 600f, ReaderInk.normalizedWidth(saved), 1e-6f)
        val restored = ReaderInk.brush(saved, brush.color)!!
        assertEquals("Fountain pen", restored.name); assertEquals(4.5f, restored.width, 1e-4f); assertEquals(brush.color, restored.color)
        assertEquals("fine", restored.variant); assertEquals(.4f, restored.texture, 1e-6f); assertEquals(.8f, restored.grain, 1e-6f)
        assertEquals(30f, restored.angle, 1e-6f); assertEquals(.7f, restored.sensitivity, 1e-6f)
        assertEquals(JetpackInkEngine.FORMAT, saved.getString("engine"))
    }

    @Test fun legacyAnchorIsRecognisedAndKeepsTodaysWidth() {
        val json = JSONObject(legacy)
        assertFalse(ReaderInk.isBrush(json))
        assertNull(ReaderInk.brush(json, 0xFF000000.toInt()))
        assertEquals(.003f, ReaderInk.normalizedWidth(json), 1e-6f)
        assertEquals(ReaderInk.LEGACY_WIDTH, ReaderInk.normalizedWidth(JSONObject("{\"page\":0,\"points\":[]}")), 1e-6f)
        assertNull(ReaderInk.toInkStroke("a", json, 0, listOf(PointF(0f, 0f), PointF(1f, 1f)), .75f))
    }

    @Test fun anchorBecomesAnInkStrokeInUniformNotebookSpace() {
        val brush = BrushStyle("Pencil", 6f, 0xFF404040.toInt(), variant = "hb")
        val json = ReaderInk.withBrush(JSONObject(legacy), brush, listOf(.5f, 1f))
        // Page fractions from PdfReaderSession.project on a page 0.75 wide per unit of height.
        val stroke = ReaderInk.toInkStroke("id-1", json, brush.color, listOf(PointF(.1f, .2f), PointF(.5f, 1f)), .75f)!!
        assertEquals("id-1", stroke.id); assertEquals("Pencil", stroke.brush); assertEquals(6f, stroke.width, 1e-4f)
        assertEquals(brush.color, stroke.color); assertEquals("hb", stroke.variant); assertEquals(JetpackInkEngine.FORMAT, stroke.engine)
        // x keeps the page width; y is measured in a page 960/600 times taller than wide.
        val heightRatio = (1f / .75f) / (960f / 600f)
        assertEquals(.1f, stroke.points[0].x, 1e-6f); assertEquals(.2f * heightRatio, stroke.points[0].y, 1e-6f)
        assertEquals(heightRatio, stroke.points[1].y, 1e-6f)
        assertEquals(listOf(.5f, 1f), stroke.points.map { it.pressure })
        // Missing pressures (a hand-edited or truncated anchor) draw at full pressure.
        val short = ReaderInk.toInkStroke("id-2", JSONObject(json.toString()).apply { remove("pressures") }, 0, listOf(PointF(0f, 0f)), 1f)!!
        assertEquals(1f, short.points.single().pressure, 0f)
    }
}
