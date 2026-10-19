package com.allient.folio

import com.allient.folio.home.LibrarySettings
import com.allient.folio.home.TrashRules

/**
 * Soft delete for notebooks and folders. Trashed notebooks stay in the saved list with deletedAt > 0,
 * so their pages and media remain on disk until they are deleted permanently or purged.
 * A trashed folder keeps its notebooks and books where they are: they show as unfiled until the
 * folder is restored, and are only unfiled for good when the folder itself is deleted permanently.
 */
internal object LibraryTrash {
    const val DAY = TrashRules.DAY

    fun expired(deletedAt: Long, now: Long, retentionDays: Int) = TrashRules.expired(deletedAt, now, retentionDays)

    fun active(notes: List<Notebook>) = notes.filter { it.deletedAt == 0L }

    fun trashNotebooks(notes: List<Notebook>, ids: Set<String>, now: Long) =
        notes.map { if (it.id in ids && it.deletedAt == 0L) it.copy(deletedAt = now) else it }

    fun restoreNotebook(notes: List<Notebook>, id: String) = notes.map { if (it.id == id) it.copy(deletedAt = 0) else it }

    /** Only notebooks that are already in Trash can be removed. */
    fun deleteNotebooksForever(notes: List<Notebook>, ids: Set<String>) = notes.filterNot { it.id in ids && it.deletedAt > 0 }

    fun trashFolder(library: LibrarySettings, id: String, now: Long) =
        library.copy(folders = library.folders.map { if (it.id == id && it.deletedAt == 0L) it.copy(deletedAt = now) else it })

    fun restoreFolder(library: LibrarySettings, id: String) = library.copy(folders = library.folders.map { if (it.id == id) it.copy(deletedAt = 0) else it })

    /** Removes trashed folders and unfiles notebooks that still point at them. */
    fun deleteFoldersForever(notes: List<Notebook>, library: LibrarySettings, ids: Set<String>): Pair<List<Notebook>, LibrarySettings> {
        val removed = library.folders.filter { it.id in ids && it.deletedAt > 0 }.map { it.id }.toSet()
        return notes.map { if (it.folderId in removed) it.copy(folderId = null) else it } to library.copy(folders = library.folders.filterNot { it.id in removed })
    }

    data class Purge(val notes: List<Notebook>, val library: LibrarySettings, val removedFolderIds: Set<String>) {
        fun changed(notes: List<Notebook>, library: LibrarySettings) = notes != this.notes || library != this.library
    }

    fun purge(notes: List<Notebook>, library: LibrarySettings, now: Long, retentionDays: Int): Purge {
        val folderIds = library.folders.filter { expired(it.deletedAt, now, retentionDays) }.map { it.id }.toSet()
        val (kept, settings) = deleteFoldersForever(notes, library, folderIds)
        return Purge(deleteNotebooksForever(kept, kept.filter { expired(it.deletedAt, now, retentionDays) }.map { it.id }.toSet()), settings, folderIds)
    }
}
