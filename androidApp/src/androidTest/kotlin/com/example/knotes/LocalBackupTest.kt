package com.example.knotes

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Smart local backups: retention, verification, change detection and what an archive contains. */
@RunWith(AndroidJUnit4::class)
class LocalBackupTest {
    private lateinit var sandbox: File
    private lateinit var context: Context
    private lateinit var folder: File
    private val utc = TimeZone.getTimeZone("UTC")

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        sandbox = File(target.cacheDir, "backup-tests-$id").apply { mkdirs() }
        folder = File(sandbox, "Folio Backups").apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(sandbox, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(sandbox, "cache").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = File(sandbox, "db/$name").apply { parentFile!!.mkdirs() }
            // Keeps the app's real backup settings untouched.
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = target.getSharedPreferences("test-$id-$name", mode)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?): android.database.sqlite.SQLiteDatabase =
                android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, handler: android.database.DatabaseErrorHandler?): android.database.sqlite.SQLiteDatabase =
                android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY, handler)
        }
        PDFBoxResourceLoader.init(context)
    }

    @After
    fun tearDown() {
        LocalBackupStore(context).preferences.edit().clear().commit()
        sandbox.deleteRecursively()
    }

    private fun utc(text: String): Long = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply { timeZone = utc }.parse(text)!!.time
    private fun version(text: String) = BackupVersion(text, "Folio-Backup-$text.knotes", utc(text), 1000)

    @Test
    fun retentionKeepsRecentDailyWeeklyAndMonthlyVersions() {
        val now = utc("2026-10-19 12:00") // a Monday
        val all = listOf(
            "2026-10-19 10:00", "2026-10-19 09:00", "2026-10-19 08:00", "2026-10-19 07:00", "2026-10-19 06:00", "2026-10-19 05:00",
            "2026-10-18 23:00", "2026-10-18 12:00", // yesterday: newest of the day only
            "2026-10-13 15:00", // six days ago, still a daily slot
            "2026-10-12 20:00", "2026-10-12 10:00", // same week as Oct 18, which is newer
            "2026-10-01 09:00", // week of Sep 28
            "2026-09-20 08:00", "2026-09-10 08:00", // September: newest only
            "2026-05-03 08:00", // sixth month back
            "2026-04-30 08:00", "2025-10-01 08:00", // outside every window
        ).map(::version)
        val kept = BackupRetention.keep(all.shuffled(), now, utc)
        assertEquals(setOf("2026-10-19 10:00", "2026-10-19 09:00", "2026-10-19 08:00", "2026-10-19 07:00", "2026-10-19 06:00",
            "2026-10-18 23:00", "2026-10-13 15:00", "2026-10-01 09:00", "2026-09-20 08:00", "2026-05-03 08:00"), kept)
    }

    @Test
    fun retentionAloneBoundsTheNumberOfBackups() {
        // A backup every 30 minutes for a year, pruned after each one as the app does.
        var folderContents = listOf<BackupVersion>()
        var time = utc("2025-10-19 00:00")
        val end = utc("2026-10-19 00:00")
        var peak = 0
        while (time < end) {
            val newest = BackupVersion("v$time", "Folio-Backup-$time.knotes", time, 1)
            val keep = BackupRetention.keep(folderContents + newest, time, utc, setOf(newest.id))
            folderContents = (folderContents + newest).filter { it.id in keep }
            assertTrue(newest in folderContents)
            peak = maxOf(peak, folderContents.size)
            time += 30L * 60 * 1000
        }
        assertTrue("kept $peak", peak <= BackupRetention.MAX_KEPT)
        assertTrue(folderContents.size >= BackupRetention.RECENT + BackupRetention.DAYS)
        // An old, lone backup is still the newest one and is never removed.
        val lone = version("2019-01-01 00:00")
        assertEquals(setOf(lone.id), BackupRetention.keep(listOf(lone), end, utc))
    }

    @Test
    fun pruningRemovesOnlyOwnBackupsAndNeverTheNewest() {
        configure()
        saveLibrary(listOf(Notebook(title = "Kept")))
        val foreign = listOf("notes.pdf", "Folio-Backup-final.knotes", "Folio-Full-Library-20240101-000000.knotes", "My-Folio-Backup-20240101-000000-000.knotes", "Other.partial")
            .map { File(folder, it).apply { writeText("not ours") } }
        val format = java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", java.util.Locale.US)
        val old = (1..30).map { day ->
            val prefix = if (day % 2 == 0) "Knotes" else "Folio"
            File(folder, "$prefix-Backup-${format.format(java.util.Date(System.currentTimeMillis() - (400L + day) * 24 * 60 * 60 * 1000))}.knotes").apply { writeText("old") }
        }
        val stalePartial = File(folder, "Folio-Backup-${format.format(java.util.Date(System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000))}.knotes.partial").apply { writeText("partial") }
        val outcome = runner().run(force = false) as LocalBackupRunner.Outcome.Saved
        assertTrue(File(outcome.version.id).isFile)
        foreign.forEach { assertTrue("${it.name} must be untouched", it.isFile && it.readText() == "not ours") }
        assertFalse(stalePartial.exists())
        val remaining = folder.listFiles()!!.filter { BackupNames.isOwned(it.name) }
        // All old backups are over a year old: only the 4 most recent of them survive, with the new one.
        assertEquals(BackupRetention.RECENT, remaining.size)
        assertTrue(remaining.any { it.name == outcome.version.name })
        assertEquals(old.size - (BackupRetention.RECENT - 1), outcome.deleted.count { !it.endsWith(".partial") })
    }

    @Test
    fun verificationRejectsTruncatedOrInconsistentArchives() {
        val note = Notebook(title = "Verified", attachments = listOf(attachment("verified.txt")))
        val archive = NotebookFiles(context).exportLibraryBackup(listOf(note), com.example.knotes.home.LibrarySettings())
        val bytes = archive.readBytes()
        assertEquals(bytes.size.toLong(), BackupArchiveVerifier.verify(bytes.inputStream(), 1))
        listOf(bytes.size / 2, bytes.size - 10, bytes.size - 1).forEach { length ->
            try { BackupArchiveVerifier.verify(bytes.copyOf(length).inputStream(), 1); fail("Accepted an archive truncated to $length bytes") }
            catch (expected: IOException) {}
        }
        try { BackupArchiveVerifier.verify(bytes.inputStream(), 2); fail("Accepted a wrong notebook count") } catch (expected: IOException) {}
        // A truncated source never becomes a backup and leaves no temporary file behind.
        val truncated = File(sandbox, "truncated.knotes").apply { writeBytes(bytes.copyOf(bytes.size - 10)) }
        try { DirectoryBackupFolder(folder).write(truncated, BackupNames.create(System.currentTimeMillis()), 1) {}; fail("Wrote a truncated backup") }
        catch (expected: IOException) {}
        assertTrue(folder.listFiles()!!.isEmpty())
        DirectoryBackupFolder(folder).write(archive, BackupNames.create(System.currentTimeMillis()), 1) {}
        assertEquals(1, folder.listFiles()!!.size)
    }

    @Test
    fun noBackupIsWrittenWhenNothingChanged() {
        configure()
        val note = Notebook(title = "Unchanged")
        saveLibrary(listOf(note))
        LocalBackupStore(context).markChanged()
        Thread.sleep(5) // the change happens strictly before the backup snapshot
        assertTrue(runner().run(force = false) is LocalBackupRunner.Outcome.Saved)
        assertFalse(LocalBackupStore(context).read().hasUnbackedChanges)
        assertTrue(runner().run(force = false) is LocalBackupRunner.Outcome.Unchanged)
        assertEquals(1, ownedBackups().size)
        saveLibrary(listOf(note.copy(title = "Changed")))
        Thread.sleep(5) // distinct file names
        assertTrue(runner().run(force = false) is LocalBackupRunner.Outcome.Saved)
        assertEquals(2, ownedBackups().size)
    }

    @Test
    fun lowFreeSpaceSkipsTheBackupAndReportsIt() {
        configure()
        saveLibrary(listOf(Notebook(title = "No space")))
        val outcome = LocalBackupRunner(context, LocalBackupStore(context), { DirectoryBackupFolder(folder) }, localFreeBytes = { 50L * 1024 * 1024 }).run(force = true)
        assertTrue(outcome is LocalBackupRunner.Outcome.LowSpace)
        assertTrue((outcome as LocalBackupRunner.Outcome.LowSpace).needed >= LocalBackupRunner.MIN_FREE_BYTES)
        assertTrue(folder.listFiles()!!.isEmpty())
        assertTrue(LocalBackupStore(context).read().error!!.contains("free space"))
    }

    @Test
    fun libraryBackupExcludesPdfAndEpubOriginalsAndCovers() = runBlocking {
        val pdf = File(sandbox, "book.pdf").also { file ->
            PDDocument().use { doc ->
                val page = PDPage(); doc.addPage(page)
                PDPageContentStream(doc, page).use { it.beginText(); it.setFont(PDType1Font.HELVETICA, 20f); it.newLineAtOffset(40f, 700f); it.showText("Original book text"); it.endText() }
                doc.save(file)
            }
        }
        val epub = File(sandbox, "book.epub").also(::writeEpub)
        val media = attachment("kept-attachment.txt")
        ReaderStore(context).use { store ->
            listOf(pdf, epub).forEach { store.importDocument(Uri.fromFile(it)) }
            assertEquals(2, store.documents().size)
        }
        val readerFiles = File(context.filesDir, "media/reader").listFiles()!!.filter { it.isFile }
        assertTrue("originals and covers are in app storage", readerFiles.size >= 2)
        val archive = NotebookFiles(context).exportLibraryBackup(listOf(Notebook(title = "With media", attachments = listOf(media))), com.example.knotes.home.LibrarySettings())
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(archive.inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() } }
        val originals = (readerFiles + pdf + epub).map { it.readBytes() }
        entries.forEach { (name, data) ->
            assertTrue("unexpected entry $name", name == "manifest.json" || name == "library.json.zst" || name.startsWith("notebooks/") || name.startsWith("assets/"))
            originals.forEach { original -> assertFalse("$name is a copy of a reader file", data.contentEquals(original)) }
        }
        // Notebook media stay in the backup.
        assertEquals(1, entries.keys.count { it.startsWith("assets/") })
        assertArrayEquals(File(media.path).readBytes(), entries.entries.single { it.key.startsWith("assets/") }.value)
        val library = JSONObject(String(com.github.luben.zstd.ZstdInputStream(entries.getValue("library.json.zst").inputStream()).use { it.readBytes() }))
        val docs = library.getJSONObject("reader").getJSONArray("documents")
        assertEquals(2, docs.length())
        for (i in 0 until docs.length()) {
            assertEquals("", docs.getJSONObject(i).getString("path"))
            assertEquals("", docs.getJSONObject(i).getJSONObject("cover").getString("path"))
        }
    }

    private fun configure() = LocalBackupStore(context).configure(Uri.parse("content://test/tree/backups"), "Test folder", true)
    private fun runner() = LocalBackupRunner(context, LocalBackupStore(context), { DirectoryBackupFolder(folder) })
    private fun ownedBackups() = folder.listFiles()!!.filter { BackupNames.isOwned(it.name) && !BackupNames.isPartial(it.name) }
    private fun saveLibrary(notes: List<Notebook>) {
        val storage = NotebookStorage(context)
        try { storage.save(notes, com.example.knotes.home.LibrarySettings()) } finally { storage.close() }
    }
    private fun attachment(name: String): NoteAttachment {
        val file = File(File(context.filesDir, "media").apply { mkdirs() }, "${UUID.randomUUID()}-$name").apply { writeText("Notebook media $name") }
        return NoteAttachment(name = name, path = file.absolutePath, mimeType = "text/plain")
    }
    private fun writeEpub(file: File) {
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "EPUB/package.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" unique-identifier="id" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:uuid:backup</dc:identifier><dc:title>Backup Sample</dc:title><dc:language>en</dc:language><meta property="dcterms:modified">2026-10-16T00:00:00Z</meta></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""",
            "EPUB/chapter.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head><body><p>Backup chapter.</p></body></html>""",
            "EPUB/nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Contents</title></head><body><nav epub:type="toc"><ol><li><a href="chapter.xhtml">One</a></li></ol></nav></body></html>""",
        )
        ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
    }
}
