package com.allient.folio

import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResponseCurveTest {
    private fun assertMonotone(curve: ResponseCurve) {
        var previous = curve.value(0f)
        for (step in 1..400) {
            val y = curve.value(step / 400f)
            assertTrue("${curve.preset} not monotone at ${step / 400f}", y >= previous - 1e-5f)
            assertTrue(y in 0f..1f)
            previous = y
        }
    }

    @Test fun presetsAreMonotoneAndPinned() = CurvePreset.entries.filter { it != CurvePreset.Custom }.forEach { preset ->
        val curve = ResponseCurve.of(preset)
        assertEquals(0f, curve.value(0f), 1e-5f)
        assertEquals(1f, curve.value(1f), 1e-5f)
        assertMonotone(curve)
    }

    @Test fun presetShapes() {
        assertTrue(ResponseCurve.of(CurvePreset.Exponential).value(.5f) < .5f)
        assertTrue(ResponseCurve.of(CurvePreset.Logarithmic).value(.5f) > .5f)
        assertEquals(.5f, ResponseCurve.of(CurvePreset.Linear).value(.5f), 1e-4f)
        assertEquals(1f, curveScale(.6f, ResponseCurve.of(CurvePreset.Linear)), 1e-4f)
        assertEquals(CurvePreset.Exponential, defaultPressureCurve.preset)
    }

    @Test fun customPointsStayMonotoneAndRoundTrip() {
        val curve = ResponseCurve(CurvePreset.Custom, listOf(Offset(0f, 0f), Offset(.2f, .9f), Offset(.4f, .5f), Offset(1f, 1f)))
        assertMonotone(curve)
        assertMonotone(ResponseCurve(CurvePreset.Custom, listOf(Offset(0f, 0f), Offset(.1f, .95f), Offset(.12f, .96f), Offset(1f, 1f))))
        val decoded = ResponseCurve.decode(curve.preset.name, curve.encode())
        assertNotNull(decoded)
        assertEquals(curve, decoded)
        assertTrue(ResponseCurve.decode("Missing", "0,0;1,1") == null)
        assertEquals(ResponseCurve.of(CurvePreset.SCurve), ResponseCurve.decode("SCurve", "garbage"))
    }

    @Test fun legacyBezierResamples() {
        val curve = ResponseCurve.fromLegacyBezier(Offset(.25f, .10f), Offset(.75f, .60f))
        assertMonotone(curve)
        assertTrue(curve.value(.5f) < .5f)
    }
}
