package com.allient.folio

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ReaderSelectionTest {
    @Test fun removeMarksOnlyTargetsOverlappingTextOnTheSelectedPage() {
        fun anchor(page: Int, rect: String) = "{\"page\":$page,\"rects\":[$rect]}"
        val selection = PdfSelection(anchor(0, "[10,30,50,10]"), "selected")
        fun mark(id: String, page: Int, rect: String, kind: com.allient.folio.reader.AnnotationKind) =
            com.allient.folio.reader.ReaderAnnotation(id, "test", kind, anchor(page, rect), createdAt = 0)
        val marks = listOf(
            mark("highlight", 0, "[20,20,40,15]", com.allient.folio.reader.AnnotationKind.HIGHLIGHT),
            mark("underline", 0, "[20,20,40,15]", com.allient.folio.reader.AnnotationKind.UNDERLINE),
            mark("other-page", 1, "[20,20,40,15]", com.allient.folio.reader.AnnotationKind.HIGHLIGHT),
            mark("outside", 0, "[60,20,80,15]", com.allient.folio.reader.AnnotationKind.HIGHLIGHT),
            mark("ink", 0, "[20,20,40,15]", com.allient.folio.reader.AnnotationKind.INK),
        )
        assertEquals(listOf("highlight", "underline"), overlappingPdfMarks(selection, marks).map { it.id })
    }

    @Test fun holdThenDragSelectsTextWithoutTurningOrPanningThePage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("selection-", ".pdf", instrumentation.targetContext.cacheDir)
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 900, 1).create())
            page.canvas.drawText("Select these words by holding and dragging", 40f, 180f, Paint().apply { textSize = 24f })
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        PdfReaderSession(file).use { session ->
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                lateinit var view: PdfPageReaderView
                val results = LinkedBlockingQueue<PdfSelection>()
                var turns = 0
                activity.onActivity { host ->
                    view = PdfPageReaderView(host).apply {
                        onSelection = { it?.let(results::offer) }
                        onPageTurn = { turns++ }
                        show(session, 0, emptyList())
                    }
                    host.setContentView(view)
                }
                instrumentation.waitForIdleSync()
                val line = session.textLines(0).first().bounds
                var x1 = 0f; var x2 = 0f; var y = 0f
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, x: Float) = activity.onActivity {
                    MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).also { event -> view.onTouchEvent(event); event.recycle() }
                }
                activity.onActivity {
                    val b = RectF(PdfPageReaderView::class.java.getDeclaredMethod("bounds").apply { isAccessible = true }.invoke(view) as RectF)
                    x1 = b.left + (line.left + .003f) * b.width()
                    x2 = b.left + (line.right - .003f) * b.width()
                    y = b.top + line.centerY() * b.height()
                }
                send(MotionEvent.ACTION_DOWN, x1)
                Thread.sleep(ViewConfiguration.getLongPressTimeout() + 100L)
                send(MotionEvent.ACTION_MOVE, x2)
                send(MotionEvent.ACTION_UP, x2)
                val selected = results.poll(5, TimeUnit.SECONDS)
                assertNotNull("Long press followed by a drag must select text in Read mode", selected)
                assertTrue(selected!!.text.contains("words"))
                assertEquals("Selecting must not turn the page", 0, turns)
                val selectionBounds = LinkedBlockingQueue<RectF>()
                activity.onActivity {
                    view.onSelectionBounds = { anchor, rect -> if (anchor == selected.anchor) selectionBounds.offer(RectF(rect)) }
                    view.show(session, 0, listOf(com.allient.folio.reader.ReaderAnnotation("selection", "test", com.allient.folio.reader.AnnotationKind.HIGHLIGHT, selected.anchor, createdAt = 0)))
                }
                instrumentation.waitForIdleSync()
                var handles = emptyList<android.graphics.PointF>()
                val deadline = SystemClock.uptimeMillis() + 5000
                while (handles.isEmpty() && SystemClock.uptimeMillis() < deadline) {
                    activity.onActivity {
                        @Suppress("UNCHECKED_CAST")
                        handles = PdfPageReaderView::class.java.getDeclaredMethod("selectionHandles").apply { isAccessible = true }.invoke(view) as List<android.graphics.PointF>
                    }
                    if (handles.isEmpty()) Thread.sleep(20)
                }
                assertEquals("Both selection handles must be available", 2, handles.size)
                val toolbarAnchor = selectionBounds.poll(5, TimeUnit.SECONDS)
                assertNotNull("Selection toolbar must receive the rendered passage bounds", toolbarAnchor)
                activity.onActivity {
                    val location = IntArray(2); view.getLocationInWindow(location)
                    assertEquals(handles.first().x + location[0], toolbarAnchor!!.left, 1f)
                    assertEquals(handles.last().y + location[1], toolbarAnchor.bottom, 1f)
                }
                val handle = handles.first()
                y = handle.y + 8f * instrumentation.targetContext.resources.displayMetrics.density
                send(MotionEvent.ACTION_DOWN, handle.x)
                send(MotionEvent.ACTION_MOVE, (handles.first().x + handles.last().x) / 2f)
                send(MotionEvent.ACTION_UP, (handles.first().x + handles.last().x) / 2f)
                val adjusted = results.poll(5, TimeUnit.SECONDS)
                assertNotNull("Dragging a handle must update the selected range", adjusted)
                assertTrue("Moving the start handle inward shortens the passage", adjusted!!.text.length < selected.text.length)
                assertEquals(0, turns)
            }
        }
        file.delete()
    }
}
