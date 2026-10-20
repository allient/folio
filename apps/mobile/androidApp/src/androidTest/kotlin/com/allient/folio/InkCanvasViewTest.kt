package com.allient.folio

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic input verifies segmentation, not physical stylus calibration. */
@RunWith(AndroidJUnit4::class)
class InkCanvasViewTest {
    private fun onCanvas(block: (InkCanvasView) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val canvas = InkCanvasView(instrumentation.targetContext) {}
            canvas.layout(0, 0, 1200, 1920)
            canvas.threshold = .1f
            block(canvas)
        }
    }

    private fun InkCanvasView.send(action: Int, x: Float, y: Float, pressure: Float, finger: Boolean = false, mouse: Boolean = false, eraser: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = if (eraser) MotionEvent.TOOL_TYPE_ERASER else if (mouse) MotionEvent.TOOL_TYPE_MOUSE else if (finger) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_STYLUS }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = 1f }
        val event = MotionEvent.obtain(now, now, action, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, if (finger) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS, 0)
        onTouchEvent(event)
        event.recycle()
    }

    @Test fun lowPressureEndsSegmentAndNeverConnectsNextContact() = onCanvas { canvas ->
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 200f, 150f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 400f, 400f, .02f)
        canvas.send(MotionEvent.ACTION_MOVE, 800f, 800f, .6f)
        canvas.send(MotionEvent.ACTION_MOVE, 900f, 850f, .6f)
        canvas.send(MotionEvent.ACTION_UP, 1100f, 1800f, 0f)
        assertEquals(2, canvas.strokes.size)
        assertTrue(canvas.strokes.first().points.all { it.x < .3f })
        assertTrue(canvas.strokes.last().points.all { it.x > .6f })
        assertEquals(2, canvas.strokes.last().points.size)
        assertTrue(canvas.strokes.map { it.id }.distinct().size == 2)
    }

    @Test fun flushWithoutUpCommitsExactlyOnce() = onCanvas { canvas ->
        var commits = 0
        canvas.onStrokesChanged = { commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 200f, 200f, .5f)
        assertTrue(canvas.strokes.isEmpty())
        canvas.flushInput()
        assertEquals(1, canvas.strokes.size)
        assertEquals(1, commits)
        canvas.flushInput()
        canvas.send(MotionEvent.ACTION_UP, 1000f, 1800f, 0f)
        assertEquals(1, canvas.strokes.size)
        assertEquals(1, commits)
        assertEquals(2, canvas.strokes.single().points.size)
    }

    @Test fun historicalZeroPressureAlsoBreaksStroke() = onCanvas { canvas ->
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, .5f)
        val now = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        fun coords(xValue: Float, p: Float) = arrayOf(MotionEvent.PointerCoords().apply { x = xValue; y = 100f; pressure = p })
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), coords(200f, .5f), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        event.addBatch(now + 5, coords(400f, 0f), 0)
        event.addBatch(now + 10, coords(800f, .5f), 0)
        canvas.onTouchEvent(event); event.recycle()
        canvas.send(MotionEvent.ACTION_UP, 800f, 100f, 0f)
        assertEquals(2, canvas.strokes.size)
        assertEquals(2, canvas.strokes.first().points.size)
        assertEquals(1, canvas.strokes.last().points.size)
    }

    @Test fun fingerAndLockedLayerDoNotWrite() = onCanvas { canvas ->
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 200f, 200f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 200f, 200f, 0f, finger = true)
        assertTrue(canvas.strokes.isEmpty())
        canvas.layers = listOf(InkLayer(id = "ink", name = "Locked", locked = true))
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, 1f)
        canvas.send(MotionEvent.ACTION_UP, 100f, 100f, 0f)
        assertTrue(canvas.strokes.isEmpty())
    }

    @Test fun disabledFingerDrawingNeverWritesWhilePageSwipeIsEnabled() = onCanvas { canvas ->
        canvas.fingerDrawing = false
        canvas.singlePageFingerSwipeEnabled = true
        var swipes = 0
        canvas.onHorizontalFingerSwipe = { swipes++ }
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 110f, 300f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 110f, 300f, 0f, finger = true)
        assertTrue(canvas.strokes.isEmpty())
        assertEquals(0, swipes)
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 1100f, 100f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 1100f, 100f, 0f, finger = true)
        assertTrue(canvas.strokes.isEmpty())
        assertEquals(1, swipes)
        // Disabling finger drawing must not disable the pen.
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 300f, 300f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 300f, 300f, 0f)
        assertEquals(1, canvas.strokes.size)
    }

    @Test fun enabledFingerDrawingWritesHorizontalInkInsteadOfChangingPage() = onCanvas { canvas ->
        canvas.fingerDrawing = true
        canvas.singlePageFingerSwipeEnabled = true
        var swipes = 0
        canvas.onHorizontalFingerSwipe = { swipes++ }
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 1100f, 100f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 1100f, 100f, 0f, finger = true)
        assertEquals(1, canvas.strokes.size)
        assertEquals(0, swipes)
        canvas.fingerDrawing = false
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 400f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 110f, 700f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 110f, 700f, 0f, finger = true)
        assertEquals(1, canvas.strokes.size)
    }

    @Test fun fingerCanDrawOnInfiniteCanvasWhileTwoFingerScrollIsEnabled() = onCanvas { canvas ->
        canvas.fingerDrawing = true
        canvas.singleFingerVerticalSwipe = false
        canvas.twoFingerVerticalScroll = true
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 200f, 200f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 200f, 200f, 0f, finger = true)
        assertEquals(1, canvas.strokes.size)
    }

    @Test fun infiniteCanvasWritesBeyondEveryOriginalEdgeAndPreservesCoordinates() = onCanvas { canvas ->
        canvas.infiniteCanvas = true
        listOf(-2f to .5f, 3f to .5f, .5f to -2f, .5f to 3f).forEach { (x,y) ->
            canvas.jumpTo(x,y)
            canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .6f)
            canvas.send(MotionEvent.ACTION_MOVE, 650f, 980f, .6f)
            canvas.send(MotionEvent.ACTION_UP, 650f, 980f, 0f)
        }
        assertEquals(4, canvas.strokes.size)
        val note = Notebook(title = "Infinite", infiniteCanvas = true, pages = listOf(NotebookPage(strokes = canvas.strokes)),
            pins = listOf(CanvasPin(name = "Far away", x = -2f, y = 3f)))
        val restored = NotebookStorage.deserializeNotebook(NotebookStorage.serializeNotebook(note))
        // Storage rounds points to 5 decimals; everything else must survive unchanged.
        assertEquals(note.copy(pages = emptyList()), restored.copy(pages = emptyList()))
        note.pages.single().strokes.zip(restored.pages.single().strokes).forEach { (a, b) ->
            assertEquals(a.copy(points = emptyList()), b.copy(points = emptyList()))
            a.points.zip(b.points).forEach { (p, q) -> assertEquals(p.x, q.x, 1e-5f); assertEquals(p.y, q.y, 1e-5f) }
        }
        assertTrue(restored.pages.single().strokes.any { it.points.first().x < 0f })
        assertTrue(restored.pages.single().strokes.any { it.points.first().y > 1f })
    }
    @Test fun infiniteCanvasPansWithFingerAtOneHundredPercentWithoutWriting() = onCanvas { canvas ->
        canvas.infiniteCanvas = true; canvas.fingerDrawing = false
        canvas.send(MotionEvent.ACTION_DOWN, 500f, 500f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 1000f, 1400f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 1000f, 1400f, 0f, finger = true)
        assertTrue(canvas.viewportCenterX < .2f)
        assertTrue(canvas.viewportCenterY < .1f)
        assertTrue(canvas.strokes.isEmpty())
    }

    @Test fun shapeCommitsAtCutoffAndModeSwitchEndsInk() = onCanvas { canvas ->
        canvas.mode = CanvasMode.Shape
        canvas.shapeKind = ShapeKind.Rectangle
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 400f, 500f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 700f, 700f, .01f)
        assertEquals(1, canvas.strokes.size)
        assertEquals(5, canvas.strokes.first().points.size)
        canvas.send(MotionEvent.ACTION_UP, 1000f, 1000f, 0f)
        assertEquals(1, canvas.strokes.size)
        canvas.mode = CanvasMode.Write
        canvas.send(MotionEvent.ACTION_DOWN, 800f, 800f, .5f)
        canvas.mode = CanvasMode.Lasso
        assertEquals(2, canvas.strokes.size)
        canvas.send(MotionEvent.ACTION_MOVE, 1000f, 1000f, .5f)
        assertEquals(2, canvas.strokes.size)
    }

    @Test fun objectDragCommitsOnceAndHonorsLockedLayers() = onCanvas { canvas ->
        canvas.images = listOf(InkImage(id = "image", path = "unused", x = .1f, y = .1f, width = .2f, height = .2f))
        canvas.mode = CanvasMode.Objects
        var commits = 0
        canvas.onObjectsChanged = { _, _ -> commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 180f, 300f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 300f, 492f, .5f)
        assertEquals(0, commits)
        canvas.send(MotionEvent.ACTION_UP, 1100f, 1700f, 0f)
        assertEquals(1, commits)
        assertEquals(.2f, canvas.images.single().x, .001f)
        assertEquals(.2f, canvas.images.single().y, .001f)
        canvas.layers = listOf(InkLayer(id = "ink", name = "Locked", locked = true))
        canvas.send(MotionEvent.ACTION_DOWN, 300f, 492f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 500f, 700f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 500f, 700f, 0f)
        assertEquals(1, commits)
        assertEquals(.2f, canvas.images.single().x, .001f)
    }

    @Test fun sparseShapeEdgeCanBeErasedAndFillIsStored() = onCanvas { canvas ->
        canvas.mode = CanvasMode.Shape
        canvas.shapeKind = ShapeKind.Rectangle
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 500f, 500f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 500f, 500f, 0f)
        canvas.mode = CanvasMode.Fill
        canvas.send(MotionEvent.ACTION_DOWN, 300f, 300f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 300f, 300f, 0f)
        assertTrue(canvas.strokes.single().filled)
        canvas.mode = CanvasMode.Write
        canvas.brush = brushes.first { it.eraser }
        canvas.eraserMode = EraserMode.Pixel
        canvas.send(MotionEvent.ACTION_DOWN, 300f, 100f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 300f, 100f, 0f)
        assertFalse(canvas.strokes.isEmpty())
        assertTrue(canvas.strokes.all { it.filled && it.engine == FilledInkEraser.FORMAT })
        canvas.eraserMode = EraserMode.Stroke
        canvas.send(MotionEvent.ACTION_DOWN, 500f, 300f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 500f, 300f, 0f)
        assertTrue(canvas.strokes.size < 2)
    }
    private fun line(id: String = "line", layer: String = "ink", y: Float = .5f) = InkStroke(
        "Fineliner", android.graphics.Color.BLACK, 2f,
        listOf(InkPoint(.1f, y, .4f, 0), InkPoint(.9f, y, .8f, 100)), id = id, layerId = layer,
        tags = listOf("test"), engine = JetpackInkEngine.FORMAT)

    @Test fun partialEraserSplitsSparseInkAndPreservesMetadata() = onCanvas { canvas ->
        val original = line()
        canvas.strokes = listOf(original); canvas.brush = brushes.last(); canvas.eraserMode = EraserMode.Pixel
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f)
        assertEquals(2, canvas.strokes.size)
        canvas.send(MotionEvent.ACTION_UP, 600f, 960f, 0f)
        assertTrue(canvas.strokes.all { it.color == original.color && it.tags == original.tags && it.engine == original.engine })
        assertTrue(canvas.strokes.first().points.last().x < .49f)
        assertTrue(canvas.strokes.last().points.first().x > .51f)
        assertEquals(original.points.first(), canvas.strokes.first().points.first())
        assertEquals(original.points.last(), canvas.strokes.last().points.last())
        assertTrue(canvas.strokes.map { it.id }.distinct().size == 2)
    }

    @Test fun fastEraserSweepsBetweenEventsAndPublishesOnce() = onCanvas { canvas ->
        canvas.strokes = listOf(line()); canvas.brush = brushes.last(); canvas.eraserMode = EraserMode.Stroke
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 700f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 600f, 1200f, .5f)
        assertTrue(canvas.strokes.isEmpty()); assertEquals(0, commits)
        canvas.send(MotionEvent.ACTION_UP, 600f, 1200f, 0f)
        assertEquals(1, commits)
        canvas.flushInput(); assertEquals(1, commits)
    }

    @Test fun cancelledEraserRestoresInkWithoutPublishing() = onCanvas { canvas ->
        val original = listOf(line()); canvas.strokes = original; canvas.brush = brushes.last()
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f)
        assertTrue(canvas.strokes.isEmpty())
        canvas.send(MotionEvent.ACTION_CANCEL, 600f, 960f, 0f)
        assertEquals(original, canvas.strokes); assertEquals(0, commits)
    }

    @Test fun erasedHoverDoesNotJoinTwoEraserGestures() = onCanvas { canvas ->
        canvas.strokes = listOf(line()); canvas.brush = brushes.last()
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 700f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 600f, 900f, .01f)
        canvas.send(MotionEvent.ACTION_MOVE, 600f, 1200f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 600f, 1200f, 0f)
        assertEquals(1, canvas.strokes.size)
    }

    @Test fun eraserScopesProtectHiddenAndLockedLayers() = onCanvas { canvas ->
        canvas.layers = listOf(InkLayer("ink", "Ink"), InkLayer("other", "Other"),
            InkLayer("locked", "Locked", locked = true), InkLayer("hidden", "Hidden", visible = false))
        canvas.strokes = canvas.layers.map { line(it.id, it.id) }; canvas.brush = brushes.last()
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f); canvas.send(MotionEvent.ACTION_UP, 600f, 960f, 0f)
        assertEquals(setOf("other", "locked", "hidden"), canvas.strokes.map { it.layerId }.toSet())
        canvas.eraseActiveLayerOnly = false
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f); canvas.send(MotionEvent.ACTION_UP, 600f, 960f, 0f)
        assertEquals(setOf("locked", "hidden"), canvas.strokes.map { it.layerId }.toSet())
    }

    private fun InkCanvasView.loop() {
        send(MotionEvent.ACTION_DOWN, 400f, 800f, .5f)
        send(MotionEvent.ACTION_MOVE, 800f, 800f, .5f)
        send(MotionEvent.ACTION_MOVE, 800f, 1100f, .5f)
        send(MotionEvent.ACTION_MOVE, 400f, 1100f, .5f)
        send(MotionEvent.ACTION_UP, 400f, 800f, 0f)
    }

    @Test fun lassoEraserErasesCrossingSparseStrokesInOneAction() = onCanvas { canvas ->
        canvas.strokes = listOf(line(), line("outside", y = .2f))
        canvas.brush = brushes.last(); canvas.eraserMode = EraserMode.Lasso
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        canvas.loop()
        assertEquals(listOf("outside"), canvas.strokes.map { it.id }); assertEquals(1, commits)
        assertTrue(canvas.selectedStrokeIds.isEmpty())
    }

    @Test fun rectangleSelectionFindsSparseCrossingAndCanDrag() = onCanvas { canvas ->
        canvas.strokes = listOf(line()); canvas.mode = CanvasMode.Lasso; canvas.lassoKind = LassoKind.Rectangle
        canvas.send(MotionEvent.ACTION_DOWN, 400f, 800f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 800f, 1100f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 800f, 1100f, 0f)
        assertEquals(setOf("line"), canvas.selectedStrokeIds)
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 600f, 1100f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 600f, 1100f, 0f)
        assertEquals(1, commits); assertTrue(canvas.strokes.single().points.all { it.y > .5f })
    }

    @Test fun partialEraserHasSameDocumentSizeWhenZoomed() = onCanvas { canvas ->
        canvas.strokes = listOf(line()); canvas.brush = brushes.last(); canvas.eraserMode = EraserMode.Pixel
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f); canvas.send(MotionEvent.ACTION_UP, 600f, 960f, 0f)
        val gapAtFit = canvas.strokes.last().points.first().x - canvas.strokes.first().points.last().x
        canvas.strokes = listOf(line()); canvas.setZoom(2f)
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f); canvas.send(MotionEvent.ACTION_UP, 600f, 960f, 0f)
        val gapAtZoom = canvas.strokes.last().points.first().x - canvas.strokes.first().points.last().x
        assertEquals(gapAtFit, gapAtZoom, .0001f)
    }

    @Test fun shapeToolsProduceFiniteClosedAndOpenGeometry() = onCanvas { canvas ->
        canvas.mode = CanvasMode.Shape; canvas.shapeFilled = true
        for (kind in ShapeKind.entries) {
            canvas.strokes = emptyList(); canvas.shapeKind = kind
            canvas.send(MotionEvent.ACTION_DOWN, 200f, 300f, .4f)
            canvas.send(MotionEvent.ACTION_MOVE, 700f, 900f, .7f)
            canvas.send(MotionEvent.ACTION_UP, 1000f, 1400f, 0f)
            val stroke = canvas.strokes.single()
            assertEquals(kind.closed, stroke.filled)
            assertTrue(stroke.points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f })
            if (kind == ShapeKind.Circle || kind == ShapeKind.Square) {
                val w = (stroke.points.maxOf { it.x } - stroke.points.minOf { it.x }) * 1200f
                val h = (stroke.points.maxOf { it.y } - stroke.points.minOf { it.y }) * 1920f
                assertEquals(w, h, .01f)
            }
        }
    }

    @Test fun textDraggingContinuesForMultipleMovesAndCommitsOnce() = onCanvas { canvas ->
        canvas.texts = listOf(InkText(id = "text", text = "Hello", x = .1f, y = .2f))
        canvas.mode = CanvasMode.Objects
        var commits = 0; canvas.onObjectsChanged = { _, _ -> commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 130f, 380f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 250f, 480f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 370f, 580f, .5f)
        assertEquals(0, commits)
        canvas.send(MotionEvent.ACTION_UP, 370f, 580f, 0f)
        assertEquals(1, commits); assertEquals(.3f, canvas.texts.single().x, .001f)
        assertTrue(canvas.texts.single().y > .3f)
    }

    @Test fun fillGesturePublishesOnceAndCancellationRestoresIt() = onCanvas { canvas ->
        canvas.mode = CanvasMode.Shape; canvas.shapeKind = ShapeKind.Rectangle
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 600f, 600f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 600f, 600f, 0f)
        canvas.mode = CanvasMode.Fill
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 300f, 300f, .5f)
        assertTrue(canvas.strokes.single().filled); assertEquals(0, commits)
        canvas.send(MotionEvent.ACTION_CANCEL, 300f, 300f, 0f)
        assertFalse(canvas.strokes.single().filled); assertEquals(0, commits)
        canvas.send(MotionEvent.ACTION_DOWN, 300f, 300f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 300f, 300f, 0f)
        assertTrue(canvas.strokes.single().filled); assertEquals(1, commits)
    }

    @Test fun partialEraserCutsHoleInFilledShapeAndExportsAsVector() = onCanvas { canvas ->
        canvas.mode = CanvasMode.Shape; canvas.shapeKind = ShapeKind.Rectangle; canvas.shapeFilled = true
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 600f, 600f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 600f, 600f, 0f)
        canvas.mode = CanvasMode.Write; canvas.brush = brushes.last(); canvas.eraserMode = EraserMode.Pixel
        canvas.send(MotionEvent.ACTION_DOWN, 300f, 300f, .5f); canvas.send(MotionEvent.ACTION_UP, 300f, 300f, 0f)
        val result = canvas.strokes.single()
        assertTrue(result.filled); assertEquals(FilledInkEraser.FORMAT, result.engine)
        val bitmap = android.graphics.Bitmap.createBitmap(600, 960, android.graphics.Bitmap.Config.ARGB_8888)
        val drawing = android.graphics.Canvas(bitmap); drawing.drawColor(android.graphics.Color.WHITE)
        NotebookRenderer.drawStroke(drawing, result, 600f, 960f)
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(150, 150))
        assertFalse(android.graphics.Color.WHITE == bitmap.getPixel(200, 200))
        val hole = listOf(androidx.compose.ui.geometry.Offset(.249f, .155f), androidx.compose.ui.geometry.Offset(.251f, .155f),
            androidx.compose.ui.geometry.Offset(.251f, .157f), androidx.compose.ui.geometry.Offset(.249f, .157f))
        assertFalse(InkGeometry.intersects(result, hole))
        val note = Notebook(title = "Cut vector", pages = listOf(NotebookPage(strokes = listOf(result))))
        val restored = deserializeNotebook(serializeNotebook(note)).pages.single().strokes.single()
        assertEquals(result, restored)
        bitmap.recycle()
    }

    @Test fun selectionTransformsPreserveGroupAndStayOnPage() = onCanvas {
        val original = line()
        val changed = InkSelection.transform(listOf(original), setOf(original.id), scale = 2f, rotation = 90f).single()
        assertTrue(changed.points.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(original.tags, changed.tags)
        assertEquals(original.points.map { it.pressure }, changed.points.map { it.pressure })
        assertEquals(changed.points.first().x, changed.points.last().x, .001f)
        assertTrue(changed.points.first().y < changed.points.last().y)
        val flipped = InkSelection.transform(listOf(original), setOf(original.id), flipX = true).single()
        assertEquals(.9f, flipped.points.first().x, .0001f)
        assertEquals(.1f, flipped.points.last().x, .0001f)
    }

    @Test fun mouseDrawsWithPalmRejectionEnabled() = onCanvas { canvas ->
        assertFalse(canvas.fingerDrawing)
        canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, 1f, mouse = true)
        canvas.send(MotionEvent.ACTION_MOVE, 200f, 200f, 1f, mouse = true)
        canvas.send(MotionEvent.ACTION_UP, 200f, 200f, 0f, mouse = true)
        assertEquals(1, canvas.strokes.size)
    }

    @Test fun everyBrushRendersWithJetpackInk() = onCanvas {
        val bitmap = android.graphics.Bitmap.createBitmap(600, 960, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        for (brush in brushes.filterNot { it.eraser }) {
            canvas.drawColor(android.graphics.Color.WHITE)
            val stroke = line().copy(brush = brush.name, color = brush.color, width = brush.width)
            NotebookRenderer.drawStroke(canvas, stroke, 600f, 960f)
            assertFalse("${brush.name} must render", bitmap.getPixel(300, 480) == android.graphics.Color.WHITE)
        }
        bitmap.recycle()
    }

    @Test fun autoReturnCallbackHappensOnceAtPressureCutoff() = onCanvas { canvas ->
        canvas.strokes = listOf(line()); canvas.brush = brushes.last()
        var finishes = 0; canvas.onEraseFinished = { finishes++ }
        canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 600f, 1000f, .01f)
        assertEquals(1, finishes)
        canvas.send(MotionEvent.ACTION_MOVE, 800f, 1000f, .01f)
        canvas.send(MotionEvent.ACTION_UP, 1100f, 1700f, 0f)
        assertEquals(1, finishes)
    }

    @Test fun imageResizeAndCancelledTextDragHonorTransactions() = onCanvas { canvas ->
        canvas.images = listOf(InkImage(id = "image", path = "unused", x = .1f, y = .1f, width = .2f, height = .2f))
        canvas.mode = CanvasMode.Objects
        canvas.send(MotionEvent.ACTION_DOWN, 180f, 300f, .5f); canvas.send(MotionEvent.ACTION_UP, 180f, 300f, 0f)
        var commits = 0; canvas.onObjectsChanged = { _, _ -> commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 360f, 576f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 480f, 768f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 480f, 768f, 0f)
        assertEquals(.3f, canvas.images.single().width, .001f)
        assertEquals(.3f, canvas.images.single().height, .001f); assertEquals(1, commits)
        val original = listOf(InkText(id = "text", text = "Hello", x = .1f, y = .2f))
        canvas.texts = original
        canvas.send(MotionEvent.ACTION_DOWN, 130f, 380f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 250f, 480f, .5f)
        canvas.send(MotionEvent.ACTION_CANCEL, 250f, 480f, 0f)
        assertEquals(original, canvas.texts); assertEquals(1, commits)
    }

    @Test fun hardwareEraserOverridesShapeAndSelectionTools() = onCanvas { canvas ->
        for (tool in listOf(CanvasMode.Shape, CanvasMode.Lasso, CanvasMode.Objects, CanvasMode.Pan)) {
            canvas.strokes = listOf(line()); canvas.mode = tool
            canvas.send(MotionEvent.ACTION_DOWN, 600f, 960f, .5f, eraser = true)
            canvas.send(MotionEvent.ACTION_UP, 600f, 960f, 0f, eraser = true)
            assertTrue("Hardware eraser in $tool", canvas.strokes.isEmpty())
            assertFalse(canvas.brush.eraser)
        }
    }

    private fun InkCanvasView.selectCompactInk(): InkStroke {
        val stroke = line().copy(points = listOf(InkPoint(.2f, .3f, .4f, 0), InkPoint(.35f, .4f, .8f, 100)))
        strokes = listOf(stroke); mode = CanvasMode.Lasso; selectedStrokeIds = setOf(stroke.id)
        return stroke
    }

    @Test fun selectedInkCanBeDraggedByFingerWithFingerDrawingOff() = onCanvas { canvas ->
        canvas.selectCompactInk()
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 330f, 672f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 690f, 1056f, 1f, finger = true)
        assertEquals(0, commits)
        canvas.send(MotionEvent.ACTION_UP, 690f, 1056f, 0f, finger = true)
        assertEquals(.5f, canvas.strokes.single().points.first().x, .0001f)
        assertEquals(.5f, canvas.strokes.single().points.first().y, .0001f)
        assertEquals(setOf("line"), canvas.selectedStrokeIds); assertEquals(1, commits)
        assertFalse(canvas.fingerDrawing)
    }

    @Test fun shapeRendererKeepsFillAndBorderSeparate() = onCanvas {
        val shape = InkStroke("Fineliner", android.graphics.Color.BLUE, 8f,
            listOf(InkPoint(.2f,.2f,1f),InkPoint(.6f,.2f,1f),InkPoint(.6f,.6f,1f),InkPoint(.2f,.6f,1f),InkPoint(.2f,.2f,1f)),
            engine = "shape-v1", filled = true, fillColor = android.graphics.Color.YELLOW)
        val bitmap = android.graphics.Bitmap.createBitmap(600,960,android.graphics.Bitmap.Config.ARGB_8888)
        try {
            NotebookRenderer.drawPage(android.graphics.Canvas(bitmap), NotebookPage(strokes = listOf(shape)),600,960)
            assertEquals(android.graphics.Color.YELLOW, bitmap.getPixel(240,384))
            assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(120,384))
        } finally { bitmap.recycle() }
    }

    @Test fun pencilMovesSmallSelectedShapeFromCenterWithoutResizing() = onCanvas { canvas ->
        val shape = line().copy(points = listOf(
            InkPoint(.25f, .25f, .5f), InkPoint(.27f, .25f, .5f),
            InkPoint(.27f, .265f, .5f), InkPoint(.25f, .265f, .5f), InkPoint(.25f, .25f, .5f)))
        canvas.strokes = listOf(shape); canvas.mode = CanvasMode.Lasso; canvas.selectedStrokeIds = setOf(shape.id)
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 312f, 494.4f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 432f, 686.4f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 432f, 686.4f, 0f)
        val moved = canvas.strokes.single()
        assertEquals(.35f, moved.points.first().x, .0001f)
        assertEquals(.35f, moved.points.first().y, .0001f)
        assertEquals(shape.width, moved.width, .0001f)
        assertEquals(.02f, moved.points[1].x - moved.points[0].x, .0001f)
        assertEquals(setOf(shape.id), canvas.selectedStrokeIds); assertEquals(1, commits)
    }

    @Test fun eraserPresetDoesNotClearSelectionWhenDragging() = onCanvas { canvas ->
        canvas.brush = brushes.last(); canvas.selectCompactInk()
        canvas.send(MotionEvent.ACTION_DOWN, 330f, 672f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 450f, 864f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 450f, 864f, 0f)
        assertEquals(setOf("line"), canvas.selectedStrokeIds)
        assertEquals(.3f, canvas.strokes.single().points.first().x, .0001f)
        assertEquals(.4f, canvas.strokes.single().points.first().y, .0001f)
    }

    @Test fun moveModeCanStartOutsideSelectionAndRetainItAfterDrop() = onCanvas { canvas ->
        canvas.selectCompactInk(); canvas.mode = CanvasMode.MoveSelection
        canvas.send(MotionEvent.ACTION_DOWN, 1000f, 100f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 1120f, 292f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 1120f, 292f, 0f, finger = true)
        assertEquals(.3f, canvas.strokes.single().points.first().x, .0001f)
        assertEquals(.4f, canvas.strokes.single().points.first().y, .0001f)
        assertEquals(setOf("line"), canvas.selectedStrokeIds)
    }

    @Test fun fingerDragsSelectionInsteadOfPanningWhenZoomed() = onCanvas { canvas ->
        canvas.selectCompactInk()
        canvas.strokes = listOf(line().copy(points = listOf(InkPoint(.4f, .4f, .5f), InkPoint(.5f, .5f, .5f))))
        canvas.setZoom(2f)
        canvas.send(MotionEvent.ACTION_DOWN, 480f, 768f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 720f, 960f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 720f, 960f, 0f, finger = true)
        assertEquals(.5f, canvas.strokes.single().points.first().x, .0001f)
        assertEquals(.45f, canvas.strokes.single().points.first().y, .0001f)
        assertEquals(2f, canvas.zoom, .0001f)
    }

    @Test fun selectionDragClampsAtPageEdgeWithoutDistortionOrDrift() = onCanvas { canvas ->
        val original = canvas.selectCompactInk()
        canvas.send(MotionEvent.ACTION_DOWN, 330f, 672f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 5000f, 5000f, .5f)
        val points = canvas.strokes.single().points
        assertEquals(1f, points.last().x, .0001f); assertEquals(1f, points.last().y, .0001f)
        assertEquals(.15f, points.last().x - points.first().x, .0001f)
        assertEquals(.1f, points.last().y - points.first().y, .0001f)
        canvas.send(MotionEvent.ACTION_MOVE, 330f, 672f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 330f, 672f, 0f)
        assertEquals(original, canvas.strokes.single())
    }

    @Test fun pressureCutoffEndsSelectionDragAndIgnoresFalseHoverMoves() = onCanvas { canvas ->
        canvas.selectCompactInk()
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        canvas.send(MotionEvent.ACTION_DOWN, 330f, 672f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 450f, 864f, .5f)
        canvas.send(MotionEvent.ACTION_MOVE, 600f, 1000f, .01f)
        canvas.send(MotionEvent.ACTION_MOVE, 900f, 1200f, .5f)
        canvas.send(MotionEvent.ACTION_UP, 900f, 1200f, 0f)
        assertEquals(1, commits); assertEquals(setOf("line"), canvas.selectedStrokeIds)
        assertEquals(.3f, canvas.strokes.single().points.first().x, .0001f)
    }

    @Test fun draggingSelectionCornerResizesProportionallyInOneEdit() = onCanvas { canvas ->
        val original = canvas.selectCompactInk()
        var commits = 0; canvas.onStrokesChanged = { commits++ }
        // Bottom-right handle is eight page pixels beyond the selected points.
        canvas.send(MotionEvent.ACTION_DOWN, 428f, 776f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 616f, 976f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 616f, 976f, 0f, finger = true)
        val changed = canvas.strokes.single()
        assertEquals(.2f, changed.points.first().x, .0001f); assertEquals(.3f, changed.points.first().y, .0001f)
        assertEquals(.5f, changed.points.last().x, .0001f); assertEquals(.5f, changed.points.last().y, .0001f)
        assertEquals(original.width * 2f, changed.width, .0001f)
        assertEquals(original.points.map { it.pressure }, changed.points.map { it.pressure })
        assertEquals(1, commits)
    }

    @Test fun cancelledMoveOrResizeRestoresSelectionWithoutSaving() = onCanvas { canvas ->
        for (resize in listOf(false, true)) {
            val original = canvas.selectCompactInk()
            var commits = 0; canvas.onStrokesChanged = { commits++ }
            val x = if (resize) 428f else 330f; val y = if (resize) 776f else 672f
            canvas.send(MotionEvent.ACTION_DOWN, x, y, .5f)
            canvas.send(MotionEvent.ACTION_MOVE, x + 120f, y + 192f, .5f)
            canvas.send(MotionEvent.ACTION_CANCEL, x + 120f, y + 192f, 0f)
            assertEquals(original, canvas.strokes.single()); assertEquals(0, commits)
            assertEquals(setOf("line"), canvas.selectedStrokeIds)
        }
    }

    @Test fun fingerOutsideSelectionStillDoesNotWrite() = onCanvas { canvas ->
        val original = canvas.selectCompactInk()
        canvas.send(MotionEvent.ACTION_DOWN, 1000f, 1300f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_MOVE, 1100f, 1500f, 1f, finger = true)
        canvas.send(MotionEvent.ACTION_UP, 1100f, 1500f, 0f, finger = true)
        assertEquals(listOf(original), canvas.strokes)
        assertEquals(setOf("line"), canvas.selectedStrokeIds)
    }

}
