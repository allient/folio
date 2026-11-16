package com.allient.folio

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allient.folio.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** A single process-wide queue lets accepted writes survive an Activity finishing. */
internal object ReaderWrites {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    val mutex = Mutex()
    suspend fun <T> ordered(block: suspend () -> T): T = mutex.withLock { block() }
}
internal class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ReaderStore(application)
    var documents by mutableStateOf<List<ReaderDocument>>(emptyList()); private set
    var annotations by mutableStateOf<List<ReaderAnnotation>>(emptyList()); private set
    var current by mutableStateOf<ReaderDocument?>(null); private set
    var busy by mutableStateOf(false); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null)
    var importedId by mutableStateOf<String?>(null)
    var pendingWrites by mutableIntStateOf(0); private set
    /** Set once the reader used the notebook library (study mode), so leaving knows whether to reload it. */
    var notebooksTouched = false
    init { refresh() }
    fun refresh() = task {
        documents = withContext(Dispatchers.IO) { ReaderWrites.ordered { store.documents() } }
        current?.id?.let { load(it) }
        loading = false
    }
    suspend fun load(id: String) {
        val pair = withContext(Dispatchers.IO) { ReaderWrites.ordered { store.document(id) to store.annotations(id) } }
        current = pair.first; annotations = pair.second
    }
    fun open(id: String) = task { loading = true; load(id); loading = false }
    fun import(uri: Uri, folderId: String? = null) = task {
        busy = true
        try {
            val document = withContext(Dispatchers.IO) { ReaderWrites.ordered { store.importDocument(uri, folderId) } }
            documents = withContext(Dispatchers.IO) { store.documents() }; importedId = document.id
        } finally { busy = false }
    }
    fun removeDocument(id: String) = task { withContext(Dispatchers.IO) { ReaderWrites.ordered { store.deleteDocument(id) } }; refresh() }
    fun moveDocument(id: String, folderId: String?) = change(id) { it.copy(folderId = folderId) }
    fun setFavorite(id: String, favorite: Boolean) = change(id) { it.copy(favorite = favorite) }
    fun trashDocument(id: String, now: Long = System.currentTimeMillis()) = change(id) { if (it.deletedAt > 0) it else it.copy(deletedAt = now) }
    fun restoreDocument(id: String) = change(id) { it.copy(deletedAt = 0) }
    /** Deletes only books that are already in Trash. */
    fun deleteForever(ids: Collection<String>) = task {
        withContext(Dispatchers.IO) { ReaderWrites.ordered { ids.forEach { id -> if ((store.document(id)?.deletedAt ?: 0) > 0) store.deleteDocument(id) } } }
        refresh()
    }
    /** Removes expired Trash entries and unfiles books whose folder was deleted permanently. */
    fun purgeTrash(now: Long, retentionDays: Int, removedFolderIds: Set<String>) = task {
        withContext(Dispatchers.IO) { ReaderWrites.ordered {
            store.documents().forEach { doc ->
                if (LibraryTrash.expired(doc.deletedAt, now, retentionDays)) store.deleteDocument(doc.id)
                else if (doc.folderId != null && doc.folderId in removedFolderIds) store.saveDocument(doc.copy(folderId = null))
            }
        } }
        refresh()
    }
    private fun change(id: String, update: (ReaderDocument) -> ReaderDocument) = task {
        withContext(Dispatchers.IO) { ReaderWrites.ordered { store.document(id)?.let { store.saveDocument(update(it)) } } }
        refresh()
    }
    // Saves and removals update the list in memory instead of reading every annotation of the book back: that re-read
    // (and parse) of thousands of strokes on each stroke or page turn was the reader's costliest memory spike.
    fun save(annotation: ReaderAnnotation) {
        write(local = { list ->
            val others = list.filterNot { it.id == annotation.id }
            others.toMutableList().apply { add(others.indexOfLast { it.createdAt <= annotation.createdAt } + 1, annotation) }
        }) { store.saveAnnotation(annotation) }
    }
    fun remove(annotation: ReaderAnnotation) { write(local = { list -> list.filterNot { it.id == annotation.id } }) { store.deleteAnnotation(annotation.id) } }
    fun annotate(kind: AnnotationKind, anchor: String, quote: String = "", note: String = "", color: Int = 0xFFFFD85A.toInt()) {
        val id = current?.id ?: return
        save(ReaderAnnotation(UUID.randomUUID().toString(), id, kind, anchor, quote, note, color, System.currentTimeMillis()))
    }
    fun position(anchor: String, progress: Double) {
        val id = current?.id ?: return
        write(local = { it }) { store.updatePosition(id, anchor, progress) }
    }
    /**
     * Runs [action] in write order, then refreshes the open document. The book's annotations are brought up to date by
     * [local] (the change applied to the list in memory), or, without it, read back in full.
     */
    private fun write(local: ((List<ReaderAnnotation>) -> List<ReaderAnnotation>)? = null, action: () -> Unit) {
        pendingWrites++
        val id = current?.id
        ReaderWrites.scope.launch {
            val result = runCatching { ReaderWrites.ordered { action(); (id?.let { store.document(it) }) to (if (local == null) id?.let { store.annotations(it) }.orEmpty() else null) } }
            withContext(Dispatchers.Main) {
                pendingWrites--
                result.onSuccess { (document, notes) -> if (current?.id == id) { current = document; annotations = notes ?: local?.invoke(annotations) ?: annotations } }
                    .onFailure { error = it.message ?: "Could not save reader changes" }
            }
        }
    }
    fun exportNotes(): String = buildString {
        append("# ${current?.title.orEmpty()}\n\n")
        annotations.forEach { a ->
            append("## ${a.kind.name.lowercase().replaceFirstChar { it.uppercase() }}\n\n")
            if (a.quote.isNotBlank()) append(a.quote.lineSequence().joinToString("\n") { "> $it" } + "\n\n")
            if (a.note.isNotBlank()) append(a.note + "\n\n")
        }
    }
    private fun task(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Reader operation failed"; loading = false }
        catch (failure: OutOfMemoryError) { error = "Not enough memory to open this. Your notes are safe on this device; close other apps and try again."; loading = false }
    }
    // The store stays open while accepted writes finish; close it at the end of the queue.
    override fun onCleared() { ReaderWrites.scope.launch { ReaderWrites.ordered { store.close() } }; super.onCleared() }
}
