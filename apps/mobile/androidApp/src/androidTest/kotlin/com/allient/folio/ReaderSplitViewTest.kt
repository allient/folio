package com.allient.folio

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.pdf.PdfDocument
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allient.folio.reader.ReaderDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ReaderSplitViewTest {
    @Test fun splitSurvivesRecreationAndSavedLandscapeNeverLetterboxesLargeTablets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("reader-preferences", Context.MODE_PRIVATE)
        val id = "split-test-${UUID.randomUUID()}"
        val keys = listOf("splitActive", "splitOwner", "splitContent", "splitDocumentId", "splitStartPage", "splitHorizontal", "readerLandscape", "split-page.$id")
        val previous = preferences.all.filterKeys { it in keys }
        val file = File.createTempFile("split-view-", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        try {
            repeat(2) { index -> pdf.finishPage(pdf.startPage(PdfDocument.PageInfo.Builder(600, 900, index + 1).create())) }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val store = ReaderStore(context)
        store.saveDocument(ReaderDocument(id = id, title = "Split regression", format = "pdf", path = file.path, contentHash = id, pageCount = 2))
        try {
            preferences.edit().putBoolean("splitActive", true).putString("splitOwner", id)
                .putString("splitContent", "Current Doc").putString("splitDocumentId", id)
                .putInt("splitStartPage", 1).putBoolean("splitHorizontal", true).putInt("readerLandscape", 1).commit()
            ActivityScenario.launch<ReaderActivity>(Intent(context, ReaderActivity::class.java).putExtra("documentId", id)).use { scenario ->
                fun countPages(view: View): Int = (if (view is PdfPageReaderView) 1 else 0) +
                    (if (view is ViewGroup) (0 until view.childCount).sumOf { countPages(view.getChildAt(it)) } else 0)
                fun awaitSplit() {
                    val deadline = android.os.SystemClock.uptimeMillis() + 10_000
                    var count = 0
                    while (count != 2 && android.os.SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { count = countPages(it.window.decorView) }
                        if (count != 2) Thread.sleep(50)
                    }
                    assertEquals("Two independent PDF views must be restored", 2, count)
                    scenario.onActivity {
                        val displayControlsOrientation = android.os.Build.VERSION.SDK_INT >= 36
                        if (displayControlsOrientation) {
                            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, it.requestedOrientation)
                            val actual = it.windowManager.currentWindowMetrics.bounds
                            val available = it.windowManager.maximumWindowMetrics.bounds
                            assertEquals("Reader must use the entire display height", available.height(), actual.height())
                            assertEquals("Reader must use the entire display width", available.width(), actual.width())
                        } else {
                            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, it.requestedOrientation)
                            assertEquals(Configuration.ORIENTATION_LANDSCAPE, it.resources.configuration.orientation)
                        }
                    }
                }
                awaitSplit()
                scenario.recreate()
                awaitSplit()
            }
        } finally {
            runBlocking { ReaderWrites.ordered { store.deleteDocument(id) } }
            store.close()
            file.delete()
            preferences.edit().apply {
                keys.forEach(::remove)
                previous.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                } }
            }.commit()
        }
    }
}
