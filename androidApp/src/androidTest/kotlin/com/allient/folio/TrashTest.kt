package com.allient.folio

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.allient.folio.home.HomePreferences
import com.allient.folio.home.LibrarySettings
import com.allient.folio.home.NoteFolder
import com.allient.folio.home.TrashRules
import com.allient.folio.reader.ReaderDocument
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrashTest {
    private lateinit var sandbox: File
    private lateinit var context: Context
    private val prefix = "trash-test-${UUID.randomUUID()}"
    private val stores = mutableListOf<NotebookStorage>()
    private val day = TrashRules.DAY
    private val now = 1_800_000_000_000L

    @Before fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        sandbox = File(target.cacheDir, prefix).apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(sandbox, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(sandbox, "cache").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = File(sandbox, "db/$name").apply { parentFile!!.mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("$prefix-$name", mode)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openDatabase(getDatabasePath(name).path, factory, SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler)
        }
    }

    @After fun tearDown() {
        stores.forEach { it.close() }
        InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences("$prefix-${HomePreferencesStore.NAME}")
        sandbox.deleteRecursively()
    }

    private fun storage() = NotebookStorage(context).also { stores.add(it) }
    private fun note(title: String, folderId: String? = null) = Notebook(title = title, pages = listOf(NotebookPage(texts = listOf(InkText(text = "$title text")))), folderId = folderId)

    @Test fun softDeleteHidesNotebookButKeepsItsPagesInStorage() {
        val kept = note("Kept"); val removed = note("Removed")
        val trashed = LibraryTrash.trashNotebooks(listOf(kept, removed), setOf(removed.id), now)
        assertEquals(listOf(kept), LibraryTrash.active(trashed))
        val store = storage(); store.load(); store.save(trashed); store.close()
        val loaded = storage().load()
        val reloaded = loaded.single { it.id == removed.id }
        assertEquals(now, reloaded.deletedAt)
        assertEquals("Removed text", reloaded.pages.single().texts.single().text)
        assertEquals(listOf(kept.id), LibraryTrash.active(loaded).map { it.id })
        // Trashing again keeps the original deletion time.
        assertEquals(now, LibraryTrash.trashNotebooks(loaded, setOf(removed.id), now + day).single { it.id == removed.id }.deletedAt)
    }

    @Test fun restoreBringsNotebookBackWithItsFolder() {
        val inFolder = note("Inside", "folder-1")
        val restored = LibraryTrash.restoreNotebook(LibraryTrash.trashNotebooks(listOf(inFolder), setOf(inFolder.id), now), inFolder.id).single()
        assertEquals(0L, restored.deletedAt)
        assertEquals("folder-1", restored.folderId)
        assertEquals(inFolder, restored)
    }

    @Test fun permanentDeleteRemovesOnlyTrashedNotebooks() {
        val live = note("Live"); val trashed = note("Trashed").copy(deletedAt = now)
        assertEquals(2, LibraryTrash.deleteNotebooksForever(listOf(live, trashed), setOf(live.id)).size)
        val remaining = LibraryTrash.deleteNotebooksForever(listOf(live, trashed), setOf(live.id, trashed.id))
        assertEquals(listOf(live), remaining)
        val store = storage(); store.load(); store.save(listOf(live, trashed)); store.save(remaining); store.close()
        assertEquals(listOf(live.id), storage().load().map { it.id })
    }

    @Test fun purgeRespectsRetention() {
        val recent = note("Two days").copy(deletedAt = now - 2 * day)
        val week = note("Eight days").copy(deletedAt = now - 8 * day)
        val month = note("Thirty-one days").copy(deletedAt = now - 31 * day)
        val live = note("Live")
        val notes = listOf(recent, week, month, live)
        assertEquals(listOf(recent, live).map { it.id }, LibraryTrash.purge(notes, LibrarySettings(), now, 7).notes.map { it.id })
        assertEquals(listOf(recent, week, live).map { it.id }, LibraryTrash.purge(notes, LibrarySettings(), now, 30).notes.map { it.id })
        val never = LibraryTrash.purge(notes, LibrarySettings(), now, 0)
        assertEquals(notes, never.notes)
        assertFalse(never.changed(notes, LibrarySettings()))
        assertEquals(29, TrashRules.daysLeft(now - day, now, 30))
        assertNull(TrashRules.daysLeft(now - day, now, 0))
    }

    @Test fun trashedFolderKeepsContentsAndPurgeUnfilesThem() {
        val folder = NoteFolder("folder-1", "Projects")
        val inside = note("Inside", folder.id)
        val library = LibrarySettings(listOf(folder, NoteFolder("folder-2", "Other")))
        val trashed = LibraryTrash.trashFolder(library, folder.id, now)
        assertEquals(listOf("folder-2"), trashed.activeFolders.map { it.id })
        assertEquals(folder, LibraryTrash.restoreFolder(trashed, folder.id).folders.first())
        val purged = LibraryTrash.purge(listOf(inside), trashed.copy(folders = trashed.folders.map { if (it.id == folder.id) it.copy(deletedAt = now - 40 * day) else it }), now, 30)
        assertEquals(setOf(folder.id), purged.removedFolderIds)
        assertEquals(listOf("folder-2"), purged.library.folders.map { it.id })
        assertNull(purged.notes.single().folderId)
        // A live folder can't be deleted permanently.
        assertEquals(library, LibraryTrash.deleteFoldersForever(listOf(inside), library, setOf(folder.id)).second)
    }

    @Test fun trashedStateSurvivesSqliteSerializationAndLibraryBackup() {
        val folder = NoteFolder("folder-1", "Archive", "blue", "papers", deletedAt = now - day)
        val trashedNote = note("Old notes", folder.id).copy(deletedAt = now - 3 * day, createdAt = 1234)
        val liveNote = note("Current")
        val json = serializeNotebook(trashedNote)
        assertEquals(trashedNote, deserializeNotebook(json))
        val settings = LibrarySettings(listOf(folder))
        val store = storage(); store.load(); store.save(listOf(trashedNote, liveNote), settings); store.close()
        assertEquals(settings, storage().loadLibrary())

        val archive = NotebookFiles(context).exportLibraryBackup(listOf(trashedNote, liveNote), settings)
        val before = System.currentTimeMillis()
        val imported = NotebookFiles(context).importLibraryBackup(Uri.fromFile(archive))
        val restoredFolder = imported.settings.folders.single()
        assertTrue("Trashed folder stays in Trash", restoredFolder.deletedAt >= before)
        val restoredTrashed = imported.notebooks.single { it.title == "Old notes" }
        assertTrue("Trashed notebook stays in Trash", restoredTrashed.deletedAt >= before)
        assertEquals(restoredFolder.id, restoredTrashed.folderId)
        assertEquals(1234L, restoredTrashed.createdAt)
        assertEquals(0L, imported.notebooks.single { it.title == "Current" }.deletedAt)

        // A single-notebook import is an explicit user action, so it is never placed in Trash.
        assertEquals(0L, NotebookFiles(context).importBackup(Uri.fromFile(NotebookFiles(context).exportBackup(trashedNote))).deletedAt)
    }

    @Test fun readerDocumentTrashFieldsRoundTrip() {
        val doc = ReaderDocument("book", "Title", format = "pdf", path = "", contentHash = "abc", folderId = "folder-1", favorite = true, createdAt = 42, deletedAt = now)
        assertEquals(doc, ReaderStore.readDocument(ReaderStore.writeDocument(doc)))
        val legacy = ReaderStore.writeDocument(doc).apply { remove("favorite"); remove("createdAt"); remove("deletedAt") }
        val read = ReaderStore.readDocument(legacy)
        assertEquals(0L, read.deletedAt); assertFalse(read.favorite)
    }

    @Test fun homePreferencesPersistAndRejectUnknownValues() {
        val store = HomePreferencesStore(context)
        assertEquals(HomePreferences(), store.read())
        val custom = HomePreferences(accent = "sea", corners = "soft", viewMode = "list", sort = "name", startFullscreen = true, trashRetentionDays = 7)
        store.write(custom)
        assertEquals(custom, HomePreferencesStore(context).read())
        context.getSharedPreferences(HomePreferencesStore.NAME, Context.MODE_PRIVATE).edit()
            .putString(HomePreferencesStore.ACCENT, "neon").putInt(HomePreferencesStore.RETENTION, 12).commit()
        val fallback = HomePreferencesStore(context).read()
        assertEquals("sage", fallback.accent); assertEquals(30, fallback.trashRetentionDays)
    }
}
