package com.allient.folio

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.concurrent.Executors
import java.io.File
import java.util.UUID

internal enum class RecordingStatus { Idle, Starting, Recording, Pausing, Paused, Resuming, Stopping }
internal data class EditorImportNotice(val notebookId: String, val pageId: String?, val kind: String, val previousPage: NotebookPage? = null, val token: String = UUID.randomUUID().toString())

/** Retains notebooks across activity recreation and orders all local disk operations. */
internal class NotebookViewModel(application: Application) : AndroidViewModel(application) {
    var notebooks: List<Notebook> by mutableStateOf(emptyList())
        private set
    var loading: Boolean by mutableStateOf(true)
        private set
    var loadError: String? by mutableStateOf(null)
        private set
    var saveError: String? by mutableStateOf(null)
        private set
    var isSaving: Boolean by mutableStateOf(false)
        private set
    var firstLaunch: Boolean by mutableStateOf(false)
        private set

    var importing: Boolean by mutableStateOf(false)
        private set
    var importError: String? by mutableStateOf(null)
        private set
    var importedNotebookId: String? by mutableStateOf(null)
        private set
    var importedLibraryCount: Int by mutableStateOf(-1)
        private set
    var editorImportNotice: EditorImportNotice? by mutableStateOf(null)
        private set
    var recordingStatus: RecordingStatus by mutableStateOf(RecordingStatus.Idle)
        private set
    var recordingNotebookId: String? by mutableStateOf(null)
        private set
    var audioError: String? by mutableStateOf(null)
        private set

    var library by mutableStateOf(com.allient.folio.home.LibrarySettings())
        private set

    private val storage = NotebookStorage(application)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "knotes-local-storage")
    }
    private val lock = Any()
    private val waitingImports = java.util.ArrayDeque<Uri>()
    private val waitingLibraryImports = java.util.ArrayDeque<Uri>()
    private val completedImports = linkedMapOf<String, Notebook>()
    private val files = NotebookFiles(application)
    private data class EditorImportRequest(val uri: Uri, val notebookId: String, val kind: String, val pageId: String?, val layerId: String)
    private data class EditorImportResult(val request: EditorImportRequest, val pages: List<NotebookPage> = emptyList(), val attachment: NoteAttachment? = null, val id: String = UUID.randomUUID().toString())
    private data class RecordingPlacement(val notebookId: String, val pageId: String?, val layerId: String)
    private data class CompletedRecording(val placement: RecordingPlacement, val attachment: NoteAttachment)
    private val waitingEditorImports = java.util.ArrayDeque<EditorImportRequest>()
    private val completedEditorImports = linkedMapOf<String, EditorImportResult>()
    private val completedRecordings = linkedMapOf<String, CompletedRecording>()
    private var activeRecordingPlacement: RecordingPlacement? = null
    private val recorder = NotebookAudioRecorder(application, ::recordingCompleted)

    private data class Snapshot(val revision: Long, val notes: List<Notebook>, val library: com.allient.folio.home.LibrarySettings)
    private var pending: Snapshot? = null
    private var draining = false
    private var revision = 0L
    @Volatile private var cleared = false

    init {
        worker.execute {
            var initial = emptyList<Notebook>()
            var initialLoadError: String? = null
            var initialSaveError: String? = null
            var created = false
            var loadedLibrary = com.allient.folio.home.LibrarySettings()
            try {
                val preferences = getApplication<Application>().getSharedPreferences("library-onboarding", Context.MODE_PRIVATE)
                // An existing database may deliberately contain no notebooks after deletion.
                val existingDatabase = storage.databaseFile.exists()
                val initialized = preferences.getBoolean("initialized", false) || existingDatabase
                initial = storage.load()
                if (storage.loadError == null) loadedLibrary = storage.loadLibrary()
                initialLoadError = storage.loadError
                if (initialLoadError == null && initial.isEmpty() && !initialized) {
                    initial = listOf(Notebook(title = "My First Note"))
                    created = true
                    try {
                        storage.save(initial, loadedLibrary)
                    } catch (error: Exception) {
                        initialSaveError = error.message ?: "Could not save your first notebook"
                    }
                }
                if (initialLoadError == null && initialSaveError == null) {
                    preferences.edit().putBoolean("initialized", true).commit()
                }
            } catch (error: Exception) {
                initialLoadError = error.message ?: "Could not open local notebooks"
            }
            postMain {
                notebooks = initial
                library = loadedLibrary
                loadError = initialLoadError
                saveError = initialSaveError
                firstLaunch = created
                loading = false
                startNextImport()
            }
        }
    }

    fun saveNotes(updated: List<Notebook>) {
        onMain {
            if (cleared) return@onMain
            if (loading) {
                saveError = "Notebooks are still loading. Please try again."
                return@onMain
            }
            if (loadError != null) {
                saveError = "Existing notebooks could not be loaded. Original data has been preserved."
                return@onMain
            }
            // Models and nested lists are immutable snapshots from the editor.
            notebooks = updated.toList()
            revision += 1
            val snapshot = Snapshot(revision, notebooks, library)
            isSaving = true
            val schedule = synchronized(lock) {
                pending = snapshot
                if (draining) false else {
                    draining = true
                    true
                }
            }
            if (schedule) worker.execute(::drainWrites)
            // Every library change goes through here, so backups learn about it in one place.
            LocalBackupScheduler.changed(getApplication())
        }
    }

    /** Reload changes written by the reader's separate activity-scoped notebook editor. */
    fun reload(): Unit {
        onMain {
            if (isSaving) {
                main.postDelayed({ reload() }, 100)
                return@onMain
            }
            loading = true
            worker.execute {
                val loaded = storage.load()
                val failure = storage.loadError
                val settings = if (failure == null) storage.loadLibrary() else library
                postMain {
                    if (!cleared) {
                        notebooks = loaded
                        loadError = failure
                        if (failure == null) library = settings
                        loading = false
                    }
                }
            }
        }
    }

    fun updateLibrary(settings: com.allient.folio.home.LibrarySettings) {
        onMain {
            if (loading || loadError != null) return@onMain
            library = settings
            saveNotes(notebooks)
        }
    }

    /** Notebook and folder changes that belong together, such as purging Trash, share one write. */
    fun saveAll(updated: List<Notebook>, settings: com.allient.folio.home.LibrarySettings) {
        onMain {
            if (loading || loadError != null) return@onMain
            library = settings
            saveNotes(updated)
        }
    }

    /** Results arriving while startup is loading are queued, rather than discarded. */
    fun importDocument(uri: Uri) {
        onMain {
            waitingImports.addLast(uri)
            startNextImport()
        }
    }

    fun importLibraryDocument(uri: Uri) {
        onMain {
            waitingLibraryImports.addLast(uri)
            startNextImport()
        }
    }

    fun importIntoNotebook(uri: Uri, notebookId: String, kind: String, pageId: String?, layerId: String) {
        onMain {
            if (notebooks.any { it.id == notebookId && it.infiniteCanvas } && kind in listOf("pdf", "page-image")) {
                importError = "Infinite canvases have no pages. Insert an image or attach the PDF instead."
                return@onMain
            }
            waitingEditorImports.addLast(EditorImportRequest(uri, notebookId, kind, pageId, layerId))
            startNextImport()
        }
    }

    fun consumeEditorImport(token: String) { onMain { if (editorImportNotice?.token == token) editorImportNotice = null } }

    fun clearAudioError() { onMain { audioError = null } }

    fun startRecording(notebookId: String, pageId: String? = null, layerId: String = "ink") {
        onMain {
            if (loading || loadError != null || recordingStatus != RecordingStatus.Idle || notebooks.none { it.id == notebookId }) return@onMain
            audioError = null
            recordingNotebookId = notebookId
            recordingStatus = RecordingStatus.Starting
            synchronized(lock) { activeRecordingPlacement = RecordingPlacement(notebookId, pageId, layerId) }
            recorder.start(notebookId) { result ->
                if (cleared) return@start
                if (result.isFailure) {
                    audioError = result.exceptionOrNull()?.message ?: "Audio recording is not available."
                    if (recordingStatus == RecordingStatus.Starting) {
                        recordingStatus = RecordingStatus.Idle
                        recordingNotebookId = null
                        synchronized(lock) { activeRecordingPlacement = null }
                    }
                } else if (recordingStatus == RecordingStatus.Starting) recordingStatus = RecordingStatus.Recording
            }
        }
    }

    fun stopRecording(notebookId: String) {
        onMain {
            if (recordingNotebookId != notebookId || recordingStatus == RecordingStatus.Idle || recordingStatus == RecordingStatus.Stopping) return@onMain
            recordingStatus = RecordingStatus.Stopping
            recorder.stop { result ->
                if (cleared) return@stop
                result.exceptionOrNull()?.let { audioError = it.message ?: "The recording could not be saved." }
                recordingStatus = RecordingStatus.Idle
                recordingNotebookId = null
                synchronized(lock) { activeRecordingPlacement = null }
            }
        }
    }

    fun pauseRecording(notebookId: String) {
        onMain {
            if (recordingNotebookId != notebookId || recordingStatus != RecordingStatus.Recording) return@onMain
            recordingStatus = RecordingStatus.Pausing
            recorder.pause { result ->
                if (cleared) return@pause
                if (result.isFailure) audioError = result.exceptionOrNull()?.message ?: "The recording could not be paused."
                if (recordingStatus == RecordingStatus.Pausing) {
                    recordingStatus = if (result.isSuccess) RecordingStatus.Paused else RecordingStatus.Recording
                }
            }
        }
    }

    fun resumeRecording(notebookId: String) {
        onMain {
            if (recordingNotebookId != notebookId || recordingStatus != RecordingStatus.Paused) return@onMain
            recordingStatus = RecordingStatus.Resuming
            recorder.resume { result ->
                if (cleared) return@resume
                if (result.isFailure) audioError = result.exceptionOrNull()?.message ?: "The recording could not be resumed."
                if (recordingStatus == RecordingStatus.Resuming) {
                    recordingStatus = if (result.isSuccess) RecordingStatus.Recording else RecordingStatus.Paused
                }
            }
        }
    }

    fun currentRecordingElapsedMillis(): Long = recorder.currentRecordingElapsedMillis()

    /** Called on the audio worker before its UI callback, retaining ownership through disposal. */
    private fun recordingCompleted(notebookId: String, attachment: NoteAttachment) {
        val placement = synchronized(lock) {
            (activeRecordingPlacement?.takeIf { it.notebookId == notebookId }
                ?: RecordingPlacement(notebookId, null, "ink")).also { activeRecordingPlacement = null }
        }
        synchronized(lock) { completedRecordings[attachment.id] = CompletedRecording(placement, attachment) }
        postMain {
            val target = notebooks.firstOrNull { it.id == notebookId }
            if (target != null) saveNotes(notebooks.map { if (it.id == target.id) it.withPlacedAttachment(attachment, placement.pageId, placement.layerId, "audio") else it })
            else worker.execute { deleteOwnedFiles(listOf(attachment.path)) }
            synchronized(lock) { completedRecordings.remove(attachment.id) }
        }
    }

    private fun Notebook.withPlacedAttachment(attachment: NoteAttachment, pageId: String?, layerId: String, kind: String): Notebook {
        val page = pages.firstOrNull { it.id == pageId }
            ?: pages.firstOrNull { it.id == lastViewedPageId }
            ?: pages.firstOrNull()
        val placedPages = if (page == null) pages else pages.map { current ->
            if (current.id != page.id) current else {
                val selectedLayer = current.layers.firstOrNull { it.id == layerId && it.visible && !it.locked }
                    ?: current.layers.firstOrNull { it.visible && !it.locked }
                if (selectedLayer == null) current else current.copy(texts = current.texts + InkText(
                    text = attachment.name,
                    x = .15f,
                    y = (.15f + current.texts.size * .08f).coerceAtMost(.85f),
                    layerId = selectedLayer.id,
                    kind = kind,
                    target = attachment.id,
                ))
            }
        }
        return copy(attachments = attachments + attachment, pages = placedPages, updatedAt = System.currentTimeMillis())
    }

    fun clearImportError() { onMain { importError = null } }

    fun consumeImportedNotebook(id: String) {
        onMain { if (importedNotebookId == id) importedNotebookId = null }
    }

    fun consumeImportedLibraryCount() { onMain { importedLibraryCount = -1 } }

    private fun startNextImport() {
        if (loading || importing || (waitingImports.isEmpty() && waitingEditorImports.isEmpty() && waitingLibraryImports.isEmpty())) return
        if (loadError != null) {
            waitingImports.clear()
            waitingLibraryImports.clear()
            waitingEditorImports.clear()
            importError = "Existing notebooks could not be loaded. The selected document was not imported."
            return
        }
        if (waitingEditorImports.isNotEmpty()) {
            startEditorImport(waitingEditorImports.removeFirst())
            return
        }
        if (waitingLibraryImports.isNotEmpty()) {
            val uri = waitingLibraryImports.removeFirst()
            importing = true
            importError = null
            worker.execute {
                try {
                    val imported = files.importLibraryBackup(uri)
                    postMain {
                        library = library.copy(
                            folders = library.folders + imported.settings.folders,
                            downloaded = library.downloaded + imported.settings.downloaded,
                        )
                        saveNotes(imported.notebooks + notebooks)
                        importedLibraryCount = imported.notebooks.size
                        importing = false
                        startNextImport()
                    }
                } catch (error: Exception) {
                    postMain {
                        importError = error.message?.takeIf { it.isNotBlank() } ?: "The Folio library backup could not be restored."
                        importing = false
                        startNextImport()
                    }
                }
            }
            return
        }
        val uri = waitingImports.removeFirst()
        importing = true
        importError = null
        worker.execute {
            try {
                val imported = readImportedNotebook(uri).copy(folderId = null, openedAt = System.currentTimeMillis())
                synchronized(lock) { completedImports[imported.id] = imported }
                postMain {
                    // Merge against the current state after disk work, including edits made meanwhile.
                    saveNotes(listOf(imported) + notebooks)
                    synchronized(lock) { completedImports.remove(imported.id) }
                    importedNotebookId = imported.id
                    importing = false
                    startNextImport()
                }
            } catch (error: Exception) {
                postMain {
                    importError = error.message?.takeIf { it.isNotBlank() } ?: "The selected document could not be imported."
                    importing = false
                    startNextImport()
                }
            }
        }
    }

    private fun startEditorImport(request: EditorImportRequest) {
        importing = true
        importError = null
        worker.execute {
            try {
                val result = when (request.kind) {
                    "image", "page-image" -> EditorImportResult(request, pages = files.importImages(request.uri))
                    "pdf" -> EditorImportResult(request, pages = files.importPdfPages(request.uri))
                    else -> EditorImportResult(request, attachment = files.copyAttachment(request.uri))
                }
                synchronized(lock) { completedEditorImports[result.id] = result }
                postMain {
                    try {
                        val previousPage = if (request.kind == "image") notebooks.firstOrNull { it.id == request.notebookId }?.pages?.firstOrNull { it.id == request.pageId } else null
                        val merged = mergeEditorImport(notebooks, result)
                        requireNotNull(merged) { "The destination notebook or page no longer exists." }
                        saveNotes(merged)
                        editorImportNotice = EditorImportNotice(request.notebookId, if (request.kind == "pdf" || request.kind == "page-image") result.pages.firstOrNull()?.id else request.pageId, request.kind, previousPage)
                    } catch (error: Exception) {
                        importError = error.message ?: "This file could not be inserted."
                        worker.execute { deleteImportedResult(result) }
                    } finally {
                        synchronized(lock) { completedEditorImports.remove(result.id) }
                        importing = false
                        startNextImport()
                    }
                }
            } catch (error: Exception) {
                postMain {
                    importError = error.message ?: "This file could not be imported."
                    importing = false
                    startNextImport()
                }
            }
        }
    }

    private fun mergeEditorImport(notes: List<Notebook>, result: EditorImportResult): List<Notebook>? {
        val request = result.request
        val target = notes.firstOrNull { it.id == request.notebookId } ?: return null
        val updated = when (request.kind) {
            "image" -> {
                val page = target.pages.firstOrNull { it.id == request.pageId } ?: return null
                val layer = page.layers.firstOrNull { it.id == request.layerId && it.visible && !it.locked }
                    ?: page.layers.firstOrNull { it.visible && !it.locked }
                requireNotNull(layer) { "Choose a visible, unlocked layer before inserting an image." }
                val changed = page.copy(images = page.images + result.pages.flatMap { it.images }.map { it.copy(layerId = layer.id) })
                target.copy(pages = target.pages.map { if (it.id == page.id) changed else it })
            }
            "pdf" -> target.copy(pages = target.pages + result.pages, lastViewedPageId = result.pages.firstOrNull()?.id ?: target.lastViewedPageId)
            "page-image" -> target.copy(pages = target.pages + result.pages.map { source ->
                source.copy(template = "blank", strokes = emptyList(), texts = emptyList(), images = source.images.map { it.copy(layerId = "ink") }, layers = listOf(InkLayer(id = "ink", name = "Layer 1")))
            }, lastViewedPageId = result.pages.firstOrNull()?.id ?: target.lastViewedPageId)
            "link-file", "attachment" -> target.withPlacedAttachment(checkNotNull(result.attachment), request.pageId, request.layerId, "link-file")
            else -> target.copy(attachments = target.attachments + checkNotNull(result.attachment))
        }.copy(updatedAt = System.currentTimeMillis())
        return notes.map { if (it.id == target.id) updated else it }
    }

    private fun deleteImportedResult(result: EditorImportResult) = deleteOwnedFiles(result.pages.flatMap { it.images }.map { it.path } + listOfNotNull(result.attachment?.path))

    private fun deleteOwnedFiles(paths: List<String>) {
        val media = File(getApplication<Application>().filesDir, "media").canonicalPath + File.separator
        paths.distinct().forEach { path ->
            runCatching { File(path).takeIf { it.canonicalPath.startsWith(media) }?.delete() }
        }
    }

    private fun readImportedNotebook(uri: Uri): Notebook {
        val application = getApplication<Application>()
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported notebook"
        runCatching {
            application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) name = cursor.getString(column) ?: name
            }
        }
        val mime = runCatching { application.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val title = name.substringBeforeLast('.', name).ifBlank { "Imported notebook" }
        val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        return when {
            mime == "application/pdf" || extension == "pdf" -> Notebook(title = title, pages = files.importPdfPages(uri))
            mime.startsWith("image/") || extension in listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif") -> Notebook(title = title, pages = files.importImages(uri))
            else -> files.importBackup(uri)
        }
    }

    fun clearSaveError() {
        onMain { saveError = null }
    }

    private fun drainWrites() {
        while (true) {
            val snapshot = synchronized(lock) {
                val next = pending
                pending = null
                if (next == null) draining = false
                next
            } ?: return
            val failure = try {
                storage.save(snapshot.notes, snapshot.library)
                null
            } catch (error: Exception) {
                error.message ?: "Could not save notebooks to this device"
            }
            postMain {
                // A completed older write must not mark newer changes as saved.
                if (revision == snapshot.revision) {
                    saveError = failure
                    isSaving = false
                }
            }
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!cleared) action()
        } else postMain(action)
    }

    private fun postMain(action: () -> Unit) {
        main.post { if (!cleared) action() }
    }

    override fun onCleared() {
        cleared = true
        // A completed import must survive closing the activity before its main callback runs.
        val finalNotes = notebooks
        val finalLibrary = library
        recorder.close { worker.execute {
            try {
                val completed = synchronized(lock) { completedImports.values.toList() }
                val editorResults = synchronized(lock) { completedEditorImports.values.toList() }
                val recordings = synchronized(lock) { completedRecordings.values.toList() }
                if ((completed.isNotEmpty() || editorResults.isNotEmpty() || recordings.isNotEmpty()) && loadError == null) {
                    val ids = completed.map { it.id }.toSet()
                    var merged = completed + finalNotes.filterNot { it.id in ids }
                    editorResults.forEach { result ->
                        val updated = runCatching { mergeEditorImport(merged, result) }.getOrNull()
                        if (updated != null) merged = updated else deleteImportedResult(result)
                    }
                    recordings.forEach { (placement, attachment) ->
                        if (merged.any { it.id == placement.notebookId }) merged = merged.map { if (it.id == placement.notebookId) it.withPlacedAttachment(attachment, placement.pageId, placement.layerId, "audio") else it }
                        else deleteOwnedFiles(listOf(attachment.path))
                    }
                    storage.save(merged, finalLibrary)
                }
            } catch (error: Exception) {
                android.util.Log.e("Knotes", "Could not persist a completed import while closing", error)
            } finally {
                synchronized(lock) { activeRecordingPlacement = null }
                storage.close()
            }
        }; worker.shutdown() }
        super.onCleared()
    }
}
