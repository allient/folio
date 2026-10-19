package com.example.knotes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NaturalBrushTest {
    private fun render(width: Float, pressure: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 960, Bitmap.Config.ARGB_8888)
        val points = (0..100).map { InkPoint(.15f+it*.007f, .5f, pressure, it*10L) }
        val engine = JetpackInkEngine()
        try { engine.draw(Canvas(bitmap), InkStroke("Pencil", Color.BLACK, width, points, engine = JetpackInkEngine.FORMAT, texture = .8f), 600f, 960f) }
        finally { engine.release() }
        return bitmap
    }
    @Test fun broadPencilRetainsGrainRatherThanBecomingSolidPen() {
        val bitmap = render(32f, .7f)
        try {
            val alphas = (475..485).flatMap { y -> (120..480).map { x -> Color.alpha(bitmap.getPixel(x,y)) } }
            assertTrue("Graphite must have paper gaps", alphas.count { it < 30 } > alphas.size/12)
            assertTrue("Graphite must deposit visible pigment", alphas.count { it > 70 } > alphas.size/12)
            assertTrue("Not a flat opacity band", alphas.distinct().size > 30)
        } finally { bitmap.recycle() }
    }
    @Test fun pencilPressureChangesDarknessAndReplayIsDeterministic() {
        val light = render(16f,.2f); val dark = render(16f,.9f); val replay = render(16f,.9f)
        try {
            fun density(bitmap: Bitmap) = (475..485).sumOf { y -> (120..480).sumOf { x -> Color.alpha(bitmap.getPixel(x,y)) } }
            assertTrue(density(dark) > density(light))
            assertTrue(dark.sameAs(replay))
        } finally { light.recycle(); dark.recycle(); replay.recycle() }
    }
    @Test fun brushParametersAndUnboundedPinsSurviveStorage() {
        val preset = BrushCatalog.presets.first { it.variant == "2b" }
        val stroke = InkStroke(preset.name, preset.color, preset.width, listOf(InkPoint(-2f,3f,.5f)), engine = JetpackInkEngine.FORMAT,
            texture = .82f, grain = 1.7f, angle = -22f, variant = preset.variant)
        val note = Notebook(title = "World", infiniteCanvas = true, pages = listOf(NotebookPage(strokes = listOf(stroke))), pins = listOf(CanvasPin(name = "Sketch", x = -2f,y = 3f)))
        assertEquals(note, NotebookStorage.deserializeNotebook(NotebookStorage.serializeNotebook(note)))
        assertEquals(3f, BrushCatalog.default("Pencil").width)
        assertEquals(preset, BrushCatalog.reset(preset.copy(width = 50f, grain = 4f)))
    }
    @Test fun oldInfiniteSectionsMigrateWithoutDiscardingInk() {
        val old = Notebook(title = "Old", infiniteCanvas = true, canvasVersion = 0, pages = listOf(
            NotebookPage(strokes = listOf(InkStroke("Pen", Color.BLACK,2f,listOf(InkPoint(.2f,.3f,1f))))),
            NotebookPage(strokes = listOf(InkStroke("Pen", Color.BLACK,2f,listOf(InkPoint(.4f,.5f,1f)))))))
        val migrated = NotebookStorage.deserializeNotebook(NotebookStorage.serializeNotebook(old))
        assertEquals(1, migrated.pages.size)
        assertEquals(listOf(.3f,1.5f), migrated.pages.single().strokes.map { it.points.first().y })
        assertEquals(migrated, NotebookStorage.deserializeNotebook(NotebookStorage.serializeNotebook(migrated)))
    }
    @Test fun infiniteMigrationPreservesConflictingLayerVisibilityAndSectionPins() {
        val first = NotebookPage(layers = listOf(InkLayer(id="ink",name="Hidden",visible=false,locked=true)))
        val second = NotebookPage(layers = listOf(InkLayer(id="ink",name="Visible")),strokes = listOf(InkStroke("Pen",Color.BLACK,2f,listOf(InkPoint(.2f,.3f,1f)))))
        val old = Notebook(title="Old", infiniteCanvas=true,canvasVersion=0,pages=listOf(first,second),lastViewedPageId=second.id)
        val migrated = migrateInfiniteCanvas(old)
        val page = migrated.pages.single()
        assertEquals(2,page.layers.size)
        assertFalse(page.layers.first().visible)
        assertTrue(page.layers.last().visible)
        assertEquals(page.layers.last().id,page.strokes.single().layerId)
        assertEquals(second.id,migrated.lastViewedPageId)
        assertTrue(migrated.pins.any { it.id == second.id && it.y > 1f })
    }
    @Test fun infiniteExportContainsInkOutsideOppositeOriginalEdges() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        fun stroke(x: Float,y: Float,color: Int) = InkStroke("Fineliner",color,20f,listOf(InkPoint(x,y,1f),InkPoint(x+.2f,y+.1f,1f)),engine=JetpackInkEngine.FORMAT)
        val page = NotebookPage(strokes=listOf(stroke(-3f,-2f,Color.RED),stroke(4f,6f,Color.BLUE)))
        val note = Notebook(title="infinite-export-test-${System.nanoTime()}",infiniteCanvas=true,pages=listOf(page))
        val output = NotebookFiles(context).exportPng(note,page)
        try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(output.path)
            try {
                var red = 0; var blue = 0
                for (y in 0 until bitmap.height) for(x in 0 until bitmap.width) {
                    val color = bitmap.getPixel(x,y)
                    if (Color.red(color)>150 && Color.blue(color)<100) red++
                    if (Color.blue(color)>150 && Color.red(color)<100) blue++
                }
                assertTrue("Left/up content must be exported",red>0)
                assertTrue("Right/down content must be exported",blue>0)
                assertEquals(-3f,note.pages.single().strokes.first().points.first().x)
            } finally { bitmap.recycle() }
        } finally { output.delete() }
    }
}
