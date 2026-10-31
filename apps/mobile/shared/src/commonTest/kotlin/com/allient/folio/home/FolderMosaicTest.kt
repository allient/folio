package com.allient.folio.home

import kotlin.test.Test
import kotlin.test.assertEquals

class FolderMosaicTest {
    private fun note(id: String, infinite: Boolean = false, updatedAt: Long = 10, deletedAt: Long = 0) =
        HomeNotebook(id, id, 1, "folder", false, 0, updatedAt, "", emptyList(), "", infinite = infinite, deletedAt = deletedAt)

    private fun book(id: String, createdAt: Long = 20, deletedAt: Long = 0) =
        HomeBook(id, id, "", "pdf", "folder", false, 0, createdAt, "", 0f, 1, false, deletedAt)

    @Test fun previewsMatchEachTabAndAllIncludesEveryType() {
        val notes = listOf(note("note"), note("canvas", infinite = true))
        val books = listOf(book("book"))
        fun ids(segment: String) = folderMosaicItems(notes, books, segment)["folder"].orEmpty().map { it.id }.toSet()
        assertEquals(setOf("note", "canvas", "book"), ids("all"))
        assertEquals(setOf("note"), ids("notes"))
        assertEquals(setOf("canvas"), ids("canvas"))
        assertEquals(setOf("book"), ids("books"))
    }

    @Test fun excludesTrashAndKeepsFourMostRecentItemsPerFolder() {
        val notes = listOf(note("old"), note("recent", updatedAt = 50), note("trashed", updatedAt = 100, deletedAt = 1))
        val books = listOf(book("book1", 20), book("book2", 30), book("book3", 40), book("deleted", 200, deletedAt = 1), book("other").copy(folderId = "other"))
        val previews = folderMosaicItems(notes, books, "all")
        assertEquals(listOf("recent", "book3", "book2", "book1"), previews["folder"]!!.map { it.id })
        assertEquals(listOf("other"), previews["other"]!!.map { it.id })
        assertEquals(false, previews["folder"]!!.first().isBook)
        assertEquals(true, previews["folder"]!!.last().isBook)
    }
}
