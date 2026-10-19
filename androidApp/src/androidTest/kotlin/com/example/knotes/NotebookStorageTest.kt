package com.example.knotes

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookStorageTest {
    private lateinit var directory: File
    private lateinit var isolated: Context
    private val stores = mutableListOf<NotebookStorage>()

    @Before fun setup() {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(real.cacheDir, "storage-test-${UUID.randomUUID()}").apply { mkdirs() }
        isolated = object : ContextWrapper(real) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(directory, "files").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = File(directory, "db/$name").apply { parentFile!!.mkdirs() }
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler)
        }
    }

    @After fun cleanup() {
        stores.forEach { it.close() }
        directory.deleteRecursively()
    }

    private fun storage() = NotebookStorage(isolated).also { stores.add(it) }

    @Test fun canvasModeCannotChangeAfterCreationAndRejectedSaveKeepsOriginal() {
        val note = Notebook(title = "Paged")
        val store = storage(); store.load(); store.save(listOf(note))
        try { store.save(listOf(note.copy(infiniteCanvas = true))); fail("Mode conversion must be rejected") }
        catch (_: IllegalArgumentException) { }
        assertEquals(listOf(note), store.load())
    }

    @Test fun folderAppearanceSurvivesDatabaseReopenAndAffectsBackupSignature() {
        val settings = com.example.knotes.home.LibrarySettings(folders = listOf(
            com.example.knotes.home.NoteFolder("creative", "Creative projects", "lavender", "papers")))
        val store = storage()
        store.load()
        store.save(emptyList(), settings)
        store.close()
        assertEquals(settings, storage().loadLibrary())
        val original = ImportedLibrary(emptyList(), settings)
        val recolored = original.copy(settings = settings.copy(folders = settings.folders.map { it.copy(color = "blue") }))
        assertNotEquals(LocalBackupWorker.signature(original), LocalBackupWorker.signature(recolored))
    }

    @Test fun legacyMigrationPreservesSourceAndRunsOnlyOnce() {
        val legacy = File(isolated.filesDir, "knotes-notebooks.json")
        val source = """{"notes":[{"id":"legacy","title":"Old note","updatedAt":42,"pages":[{"id":"page-old","strokes":[{"brush":"Pen","color":-16777216,"width":3,"points":[{"x":0.2,"y":0.3,"pressure":0.5}]}]}]}]}"""
        legacy.writeText(source)
        val first = storage()
        val note = first.load().single()
        assertNull(first.loadError)
        assertEquals("legacy", note.id)
        assertEquals("ink", note.pages.single().strokes.single().layerId)
        assertEquals("Layer 1", note.pages.single().layers.single().name)
        assertEquals(source, legacy.readText())
        first.save(listOf(note.copy(title = "Updated in SQLite")))
        first.close()
        assertEquals("Updated in SQLite", storage().load().single().title)
        assertEquals(source, legacy.readText())
    }

    @Test fun extendedModelRoundTripsThroughJsonAndDatabase() {
        val layer = InkLayer(id = "details", name = "Details", visible = false, locked = true)
        val page = NotebookPage(
            id = "page", template = "dots", outline = "Chapter one", layers = listOf(layer),
            strokes = listOf(InkStroke("Rectangle", 0xFF123456.toInt(), 5f,
                listOf(InkPoint(.2f, .3f, .6f)), id = "stroke", layerId = layer.id, tags = listOf("diagram"), filled = true)),
            texts = listOf(InkText(id = "text", text = "Label", layerId = layer.id)),
            images = listOf(InkImage(id = "image", path = "/local/image.png", layerId = layer.id)),
        )
        val note = Notebook(id = "note", title = "Extended", updatedAt = 123, pages = listOf(page),
            tags = listOf("work"), attachments = listOf(NoteAttachment("attachment", "Recording", "/local/audio.m4a", "audio/mp4")))
        assertEquals(note, deserializeNotebook(serializeNotebook(note)))
        val store = storage()
        assertTrue(store.load().isEmpty())
        store.save(listOf(note))
        assertEquals(listOf(note), store.load())
        store.close()
        assertEquals(listOf(note), storage().load())
    }

    @Test fun shapeFillAndBorderColorsRoundTripIndependently() {
        val shape = InkStroke("Fineliner", 0xFF123456.toInt(), 8f,
            listOf(InkPoint(.2f,.2f,1f), InkPoint(.6f,.2f,1f), InkPoint(.6f,.6f,1f), InkPoint(.2f,.2f,1f)),
            engine = "shape-v1", filled = true, fillColor = 0xFFFFCC22.toInt(), lineStyle = "dashed")
        val note = Notebook(title = "Separate styles", pages = listOf(NotebookPage(strokes = listOf(shape))))
        val store = storage(); store.load(); store.save(listOf(note))
        val saved = store.load().single().pages.single().strokes.single()
        assertEquals(shape, saved)
        assertEquals(0xFF123456.toInt(), saved.color)
        assertEquals(0xFFFFCC22.toInt(), saved.fillColor)
    }

    @Test fun savesUpdateDeleteAndReorderPagesAndKeepIndependentCopies() {
        val first = NotebookPage(id = "first", outline = "First")
        val second = NotebookPage(id = "second", outline = "Second")
        val original = Notebook(id = "original", title = "Original", updatedAt = 1, pages = listOf(first, second))
        val store = storage()
        store.load()
        store.save(listOf(original))
        val reordered = original.copy(pages = listOf(second.copy(outline = "Edited"), first))
        store.save(listOf(reordered))
        assertEquals(listOf("second", "first"), store.load().single().pages.map { it.id })
        assertEquals("Edited", store.load().single().pages.first().outline)
        val copy = reordered.copy(id = "copy", title = "Copy", updatedAt = 2,
            pages = reordered.pages.map { it.copy(id = "copy-${it.id}") })
        store.save(listOf(reordered.copy(pages = listOf(first)), copy))
        val saved = store.load().associateBy { it.id }
        assertEquals(listOf(first), saved.getValue("original").pages)
        assertEquals(2, saved.getValue("copy").pages.size)
        store.save(listOf(copy))
        assertEquals(listOf(copy), storage().load())
        store.save(emptyList())
        assertTrue(storage().load().isEmpty())
    }

    @Test fun corruptLegacySourceBlocksSaveWithoutOverwritingOriginal() {
        val legacy = File(isolated.filesDir, "knotes-notebooks.json")
        val corrupt = "{broken-notebook-data"
        legacy.writeText(corrupt)
        val store = storage()
        assertTrue(store.load().isEmpty())
        assertNotNull(store.loadError)
        try {
            store.save(listOf(Notebook(title = "Must not replace damaged data")))
            fail("Saving after a corrupt load must be blocked")
        } catch (_: IllegalStateException) {
            assertEquals(corrupt, legacy.readText())
        }
        // Repairing the migration source allows retry, without clearing the database.
        legacy.writeText("""{"notes":[]}""")
        assertTrue(store.load().isEmpty())
        assertNull(store.loadError)
        store.save(listOf(Notebook(title = "Recovered")))
        assertEquals("Recovered", store.load().single().title)
    }

    @Test fun interruptedLegacyAtomicWriteRecoversBackup() {
        val legacy = File(isolated.filesDir, "knotes-notebooks.json")
        val valid = """{"notes":[{"id":"recovered","title":"Recovered backup","pages":[]}]}"""
        File(legacy.path + ".bak").writeText(valid)
        legacy.writeText("interrupted write")
        assertEquals("Recovered backup", storage().load().single().title)
        assertEquals(valid, legacy.readText())
    }

    @Test fun invalidNumbersAreFiniteAndUnknownLayersRemainVisibleInModel() {
        val json = """{"title":"Malformed numbers","pages":[{"strokes":[{"width":-3,"layerId":"orphan","points":[{"x":5,"y":-2,"pressure":100}]}]}]}"""
        val page = deserializeNotebook(json).pages.single()
        val stroke = page.strokes.single()
        assertEquals(.01f, stroke.width, 0f)
        assertEquals(InkPoint(1f, 0f, 1f), stroke.points.single())
        assertTrue(page.layers.any { it.id == "orphan" && it.visible })
    }
}
