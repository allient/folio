package com.allient.folio

import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercise actual PDF viewport geometry and input, including Fit Width at normal zoom. */
class PdfDisplayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun bounds(view: PdfPageReaderView): RectF =
        RectF(PdfPageReaderView::class.java.getDeclaredMethod("bounds").apply { isAccessible = true }.invoke(view) as RectF)

    private fun withPage(drawContent: Boolean = true, check: (PdfPageReaderView) -> Unit) {
        val context = instrumentation.targetContext
        val file = File.createTempFile("pdf-display-", ".pdf", context.cacheDir)
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 900, 1).create())
            if (drawContent) page.canvas.drawText("Viewport regression", 40f, 80f, android.graphics.Paint().apply { textSize = 24f })
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        val session = PdfReaderSession(file)
        lateinit var view: PdfPageReaderView
        try {
            onMain {
                view = PdfPageReaderView(context)
                view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, 1000, 600)
                view.show(session, 0, emptyList())
            }
            val deadline = System.currentTimeMillis() + 5000
            var ready = false
            while (!ready && System.currentTimeMillis() < deadline) {
                onMain { ready = view.contentDescription.toString().startsWith("PDF page 1.") }
                if (!ready) Thread.sleep(20)
            }
            assertTrue("PDF must render before checking its viewport", ready)
            check(view)
        } finally {
            onMain {
                PdfPageReaderView::class.java.getDeclaredMethod("onDetachedFromWindow").apply { isAccessible = true }.invoke(view)
            }
            session.close()
            file.delete()
        }
    }

    @Test fun blankPageContentFitKeepsFullPage() = withPage(drawContent = false) { view ->
        assertEquals(RectF(0f, 0f, 1f, 1f), view.session!!.contentBounds(0))
    }

    @Test fun contentFitRemovesBlankMarginsAndResetRestoresFullPage() = withPage { view ->
        val content = view.session!!.contentBounds(0)
        assertTrue("Content excludes the large empty page bottom", content.bottom < .2f)
        assertTrue("Content excludes the empty right margin", content.right < .8f)
        assertTrue(content.left > 0f && content.top > 0f)
        onMain {
            val normal = bounds(view)
            view.setLayout(false, content, 0f, false, false, alignTop = true)
            val fitted = bounds(view)
            assertTrue("Fit Screen must enlarge content, not retain full-page margins", fitted.width() > normal.width())
            assertTrue("Content begins near the viewport top", fitted.top + content.top * fitted.height() < 20f)
            view.setLayout(true, content, 0f, false, false, alignTop = true)
            assertEquals("Fit Width fills the width with page content", 976f, bounds(view).width() * content.width(), 1f)
            view.setLayout(false, null, 0f, false, false)
            assertEquals(normal, bounds(view))
        }
    }

    @Test fun fitWidthFillsLandscapeViewportAndVerticalDragPansAtNormalZoom() = withPage { view ->
        onMain { view.setLayout(true, null, 0f, false, false) }
        Thread.sleep(100)
        onMain {
            val before = bounds(view)
            assertEquals(976f, before.width(), 1f)
            assertTrue(before.height() > view.height)
            assertEquals("Fit Width starts at the top of the page", 0f, before.top, 1f)
            val now = android.os.SystemClock.uptimeMillis()
            listOf(Triple(MotionEvent.ACTION_DOWN, 300f, now), Triple(MotionEvent.ACTION_MOVE, 100f, now + 100), Triple(MotionEvent.ACTION_UP, 100f, now + 200)).forEach { (action, y, time) ->
                MotionEvent.obtain(now, time, action, 500f, y, 0).also { view.onTouchEvent(it); it.recycle() }
            }
            assertTrue("Vertical drag must move a fitted page at zoom 1", bounds(view).top < before.top - 100f)
            view.setLayout(false, null, 0f, false, false)
            val fitted = bounds(view)
            assertTrue(fitted.width() <= view.width)
            assertTrue(fitted.height() <= view.height)
        }
    }

    @Test fun gestureSettingsDisablePageSwipesAndKeepBottomControlsAvailable() = withPage { view ->
        onMain {
            var turns = 0
            var controls = 0
            view.onPageTurn = { turns++ }
            view.onShowControls = { controls++ }
            fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
                val now = android.os.SystemClock.uptimeMillis()
                listOf(Triple(MotionEvent.ACTION_DOWN, x1 to y1, now), Triple(MotionEvent.ACTION_MOVE, x2 to y2, now + 80), Triple(MotionEvent.ACTION_UP, x2 to y2, now + 160)).forEach { (action, point, time) ->
                    MotionEvent.obtain(now, time, action, point.first, point.second, 0).also { view.onTouchEvent(it); it.recycle() }
                }
            }
            view.gestures = ReaderGesturePreferences(slide = false)
            swipe(800f, 300f, 200f, 300f)
            assertEquals(0, turns)
            swipe(500f, 590f, 500f, 300f)
            assertEquals(1, controls)
            view.gestures = ReaderGesturePreferences()
            swipe(800f, 300f, 200f, 300f)
            assertEquals(1, turns)
        }
    }

    @Test fun marqueeCropEmitsDraftWithoutChangingViewportUntilApplied() = withPage { view ->
        onMain {
            val before = bounds(view)
            var draft: RectF? = null
            view.tool = "MarqueeCrop"
            view.onCrop = { draft = it }
            val now = android.os.SystemClock.uptimeMillis()
            listOf(Triple(MotionEvent.ACTION_DOWN, 400f to 150f, now), Triple(MotionEvent.ACTION_MOVE, 600f to 400f, now + 100), Triple(MotionEvent.ACTION_UP, 600f to 400f, now + 200)).forEach { (action, point, time) ->
                MotionEvent.obtain(now, time, action, point.first, point.second, 0).also { view.onTouchEvent(it); it.recycle() }
            }
            assertNotNull(draft)
            assertEquals(before, bounds(view))
            view.setLayout(false, draft, 0f, false, false)
            assertTrue("Applying a crop must enlarge the selected area", bounds(view).width() > before.width())
            view.setLayout(false, null, 0f, false, false)
            assertEquals(before, bounds(view))
        }
    }
}
