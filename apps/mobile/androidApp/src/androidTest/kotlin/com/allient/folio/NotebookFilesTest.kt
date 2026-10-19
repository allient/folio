package com.allient.folio

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipException

/** Files are isolated from real notebooks; no external storage permission or UI interaction. */
@RunWith(AndroidJUnit4::class)
class NotebookFilesTest {
    private lateinit var sandbox: File
    private lateinit var context: Context
    private lateinit var files: NotebookFiles

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        sandbox = File(target.cacheDir, "file-tests-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(sandbox, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(sandbox, "cache").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = File(sandbox, "db/$name").apply { parentFile!!.mkdirs() }
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?): android.database.sqlite.SQLiteDatabase =
                android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, handler: android.database.DatabaseErrorHandler?): android.database.sqlite.SQLiteDatabase =
                android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        }
        files = NotebookFiles(context)
    }

    @After
    fun tearDown() { sandbox.deleteRecursively() }

    @Test
    fun portableBackupIncludesMediaAndSurvivesDeletingOriginalAssets() {
        val image = createImage()
        val attachment = File(image.parentFile, "attachment.txt").apply { writeText("Recorded attachment content") }
        val note = sampleNote(image).copy(attachments = listOf(NoteAttachment(name = "attachment.txt", path = attachment.absolutePath, mimeType = "text/plain")))
        val archive = files.exportBackup(note)
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        assertTrue(entries.containsKey("manifest.json"))
        assertTrue(entries.containsKey("notebook.json.zst"))
        assertEquals(2, entries.keys.count { it.startsWith("assets/") })
        val json = com.github.luben.zstd.ZstdInputStream(checkNotNull(entries["notebook.json.zst"]).inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        assertFalse(json.contains(context.filesDir.absolutePath))
        assertEquals("notebook.json.zst", org.json.JSONObject(String(checkNotNull(entries["manifest.json"]), Charsets.UTF_8)).getString("notebookPath"))

        val expectedImage = image.readBytes()
        image.delete(); attachment.delete()
        val imported = files.importBackup(Uri.fromFile(archive))
        assertEquals(note.title, imported.title)
        assertEquals(note.tags, imported.tags)
        assertEquals(2, imported.pages.size)
        assertEquals(note.pages.first().strokes.first().points, imported.pages.first().strokes.first().points)
        assertEquals("Editable text", imported.pages.first().texts.first().text)
        assertArrayEquals(expectedImage, File(imported.pages.first().images.first().path).readBytes())
        assertEquals("Recorded attachment content", File(imported.attachments.first().path).readText())
        // A shared source image remains one shared asset inside this imported notebook.
        assertEquals(imported.pages[0].images[0].path, imported.pages[1].images[0].path)
    }

    @Test
    fun importingSameBackupTwiceUsesDistinctNotebookAndContentIdentities() {
        val note = sampleNote(createImage())
        val archive = files.exportBackup(note)
        val first = files.importBackup(Uri.fromFile(archive))
        val second = files.importBackup(Uri.fromFile(archive))
        val firstIds = identities(first)
        val secondIds = identities(second)
        assertTrue(firstIds.intersect(identities(note)).isEmpty())
        assertTrue(firstIds.intersect(secondIds).isEmpty())
        assertNotEquals(first.pages.first().images.first().path, second.pages.first().images.first().path)
        assertArrayEquals(File(first.pages.first().images.first().path).readBytes(), File(second.pages.first().images.first().path).readBytes())
    }

    @Test
    fun unsafeZipEntryIsRejectedWithoutLeavingFilesOrEscapingImportDirectory() {
        val before = mediaFiles()
        val archive = zip("malformed.knotes", listOf(
            "assets/first.txt" to "Partial extraction".toByteArray(),
            "../escape.txt" to "Must never be written".toByteArray(),
        ))
        assertImportFails(archive)
        assertEquals(before, mediaFiles())
        assertFalse(File(context.filesDir, "escape.txt").exists())
        assertFalse(File(sandbox, "escape.txt").exists())
    }

    @Test
    fun missingReferencedAssetRejectsBackupAndCleansPartialExtraction() {
        val note = Notebook(title = "Missing asset", pages = listOf(NotebookPage(images = listOf(InkImage(path = "assets/missing.png")))))
        val archive = zip("missing.knotes", listOf(
            "assets/unrelated.txt" to "Partial extraction".toByteArray(),
            "notebook.json" to serializeNotebook(note).toByteArray(),
        ))
        val before = mediaFiles()
        assertImportFails(archive)
        assertEquals(before, mediaFiles())
    }

    @Test
    fun pdfAndPngExportsHaveValidHeadersAndRenderedPageContent() {
        val note = sampleNote(createImage())
        val png = files.exportPng(note, note.pages.first())
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), png.inputStream().use { source -> ByteArray(8).also { assertEquals(8, source.read(it)) } })
        val bitmap = checkNotNull(BitmapFactory.decodeFile(png.absolutePath))
        try {
            assertEquals(1200, bitmap.width); assertEquals(1920, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(600, 960))
            assertEquals(Color.WHITE, bitmap.getPixel(1190, 1910))
        } finally { bitmap.recycle() }

        val pdf = files.exportPdf(note)
        assertEquals("%PDF-", pdf.inputStream().use { source -> String(ByteArray(5).also { assertEquals(5, source.read(it)) }, Charsets.US_ASCII) })
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(note.pages.size, renderer.pageCount)
                renderer.openPage(0).use { page -> assertEquals(1200, page.width); assertEquals(1920, page.height) }
            }
        }
        val pages = files.importPdfPages(Uri.fromFile(pdf))
        assertEquals(2, pages.size)
        assertNotEquals(pages[0].id, pages[1].id)
        assertTrue(pages.all { it.images.size == 1 && File(it.images.single().path).isFile })
    }

    @Test
    fun invalidImageIsRejectedAndCopiedFileIsRemoved() {
        val source = File(sandbox, "not-an-image.png").apply { writeText("Invalid image") }
        val before = mediaFiles()
        var failed = false
        try { files.importImages(Uri.fromFile(source)) } catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Invalid image should be rejected", failed)
        assertEquals(before, mediaFiles())
    }


    @Test
    fun cutVectorShapeSurvivesBackupPngAndPdf() {
        val outline = listOf(InkPoint(.1f, .1f, 1f), InkPoint(.8f, .1f, 1f), InkPoint(.8f, .8f, 1f),
            InkPoint(.1f, .8f, 1f), InkPoint(.1f, .1f, 1f))
        val original = InkStroke("Fineliner", Color.BLUE, 3f, outline, filled = true, engine = "shape-v1")
        val center = androidx.compose.ui.geometry.Offset(240f, 384f)
        val cut = checkNotNull(FilledInkEraser.erase(original, center, center, 20f, 600f, 960f, false))
        val source = Notebook(title = "Vector erasing", pages = listOf(NotebookPage(strokes = listOf(cut))))
        val restored = files.importBackup(Uri.fromFile(files.exportBackup(source)))
        val actual = restored.pages.single().strokes.single()
        assertEquals(cut.points, actual.points); assertEquals(FilledInkEraser.FORMAT, actual.engine)
        fun assertHole(bitmap: Bitmap) {
            assertEquals(Color.WHITE, bitmap.getPixel(480, 768))
            assertEquals(Color.BLUE, bitmap.getPixel(600, 960))
        }
        val png = checkNotNull(BitmapFactory.decodeFile(files.exportPng(restored, restored.pages.single()).absolutePath))
        try { assertHole(png) } finally { png.recycle() }
        ParcelFileDescriptor.open(files.exportPdf(restored), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(1200, 1920, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertHole(bitmap)
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    private fun createImage(): File {
        val directory = File(context.filesDir, "media").apply { mkdirs() }
        val image = File(directory, "${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        return image
    }

    private fun sampleNote(image: File) = Notebook(
        title = "Portable notebook",
        tags = listOf("Project", "Test"),
        pages = listOf(
            NotebookPage(
                strokes = listOf(InkStroke("Pen", Color.BLACK, 3f, listOf(InkPoint(.1f, .1f, .5f), InkPoint(.2f, .2f, .7f)))),
                texts = listOf(InkText(text = "Editable text", x = .1f, y = .1f)),
                images = listOf(InkImage(path = image.absolutePath, x = .25f, y = .25f, width = .5f, height = .5f)),
            ),
            NotebookPage(images = listOf(InkImage(path = image.absolutePath))),
        ),
    )

    private fun identities(note: Notebook): Set<String> = buildSet {
        add(note.id)
        note.pages.forEach { page ->
            add(page.id)
            page.strokes.forEach { add(it.id) }
            page.texts.forEach { add(it.id) }
            page.images.forEach { add(it.id) }
        }
        note.attachments.forEach { add(it.id) }
    }

    private fun mediaFiles(): Set<String> = File(context.filesDir, "media").walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet()

    private fun zip(name: String, entries: List<Pair<String, ByteArray>>): File = File(sandbox, name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, content) -> zip.putNextEntry(ZipEntry(path)); zip.write(content); zip.closeEntry() } }
    }

    private fun assertImportFails(file: File) {
        var failed = false
        try { files.importBackup(Uri.fromFile(file)) }
        catch (_: IllegalArgumentException) { failed = true }
        // Newer Android versions reject traversal in getNextEntry before app validation runs.
        catch (_: ZipException) { failed = true }
        assertTrue("Malformed backup should be rejected", failed)
    }
}
