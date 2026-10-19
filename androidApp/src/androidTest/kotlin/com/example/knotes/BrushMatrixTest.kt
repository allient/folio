package com.example.knotes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Every preset must leave a visible mark at the lightest pressure; images go to external files/brush-matrix. */
@RunWith(AndroidJUnit4::class)
class BrushMatrixTest {
    private val pressures = listOf(.05f, .15f, .3f, .5f, .75f, 1f)

    @Test fun renderAllPresets() {
        val out = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("brush-matrix")!!
        val viewW = 1100; val rowH = 70
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = Color.DKGRAY }
        BrushCatalog.presets.forEachIndexed { index, preset ->
            val bitmap = Bitmap.createBitmap(viewW, rowH * pressures.size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val engine = JetpackInkEngine()
            val coverage = pressures.mapIndexed { row, raw ->
                val y = (row * rowH + rowH / 2f) / (rowH * pressures.size)
                val points = (0..60).map { InkPoint(.2f + it * .012f, y + .012f * kotlin.math.sin(it / 6f), preset.mapPressure(raw), it * 8L) }
                val docH = rowH * pressures.size * JetpackInkEngine.DOCUMENT_WIDTH / viewW
                // Map the strip to document units so widths match the real canvas scale (600 units = view width).
                val stroke = InkStroke(preset.name, preset.color, preset.width, points.map { it.copy(y = it.y * docH / JetpackInkEngine.DOCUMENT_HEIGHT) },
                    engine = JetpackInkEngine.FORMAT, texture = preset.texture, grain = preset.grain, angle = preset.angle, variant = preset.variant)
                engine.draw(canvas, stroke, viewW.toFloat(), viewW * JetpackInkEngine.DOCUMENT_HEIGHT / JetpackInkEngine.DOCUMENT_WIDTH)
                canvas.drawText("p=$raw", 10f, row * rowH + rowH / 2f + 8f, labelPaint)
                var inked = 0
                for (yy in row * rowH until (row + 1) * rowH) for (xx in 210 until viewW) { val c = bitmap.getPixel(xx, yy); if (Color.red(c) + Color.green(c) + Color.blue(c) < 740) inked++ }
                inked
            }
            engine.release()
            Log.i("BrushMatrix", "%02d %-12s %-22s w=%5.1f  %s".format(index, preset.name, preset.label, preset.width, coverage.joinToString(" ") { "%6d".format(it) }))
            File(out, "%02d-%s.png".format(index, preset.label.replace(Regex("[^A-Za-z0-9]+"), "_"))).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            assertTrue("${preset.label} invisible at light pressure: $coverage", coverage.first() > 300)
            assertTrue("${preset.label} ink must not shrink with pressure: $coverage", coverage.last() >= coverage.first())
        }
    }
}
