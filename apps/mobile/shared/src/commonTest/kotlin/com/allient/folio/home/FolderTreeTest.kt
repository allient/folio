package com.allient.folio.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FolderTreeTest {
    private val work = NoteFolder("work", "Work")
    private val clients = NoteFolder("clients", "Clients", parentId = "work")
    private val bank = NoteFolder("bank", "Bank", parentId = "clients")
    private val home = NoteFolder("home", "Home")

    @Test fun parentIsTheFolderItSitsIn() {
        val folders = listOf(work, clients, bank, home)
        assertEquals("work", folders.parentOf(clients))
        assertEquals("clients", folders.parentOf(bank))
        assertNull(folders.parentOf(work))
    }

    @Test fun missingOrTrashedParentMeansRoot() {
        // Active folders only: "work" is in Trash, so "clients" shows at the root.
        assertNull(listOf(clients, home).parentOf(clients))
    }

    @Test fun loopsFallBackToRoot() {
        val a = NoteFolder("a", "A", parentId = "b")
        val b = NoteFolder("b", "B", parentId = "a")
        val folders = listOf(a, b)
        assertNull(folders.parentOf(a))
        assertNull(folders.parentOf(b))
        assertEquals(listOf("a" to 0, "b" to 0), folders.folderTree().map { (folder, depth) -> folder.id to depth })
    }

    @Test fun subfoldersAtAnyDepthAreIncluded() {
        assertEquals(setOf("work", "clients", "bank"), listOf(work, clients, bank, home).withSubfolders(setOf("work")))
    }

    @Test fun movingAFolderChangesOnlyItsParent() {
        val moved = listOf(work, clients, bank, home).moveFolders(setOf("clients"), "home")
        assertEquals("home", moved.first { it.id == "clients" }.parentId)
        assertEquals("clients", moved.first { it.id == "bank" }.parentId)
        assertNull(listOf(work, clients).moveFolders(setOf("clients"), null).first { it.id == "clients" }.parentId)
    }

    @Test fun aFolderCannotMoveIntoItselfOrItsSubfolders() {
        val folders = listOf(work, clients, bank, home)
        assertEquals(folders, folders.moveFolders(setOf("work"), "bank"))
        assertEquals(folders, folders.moveFolders(setOf("work"), "work"))
    }

    @Test fun aSelectedSubfolderStaysInsideItsMovedParent() {
        val moved = listOf(work, clients, bank, home).moveFolders(setOf("clients", "bank"), "home")
        assertEquals("home", moved.first { it.id == "clients" }.parentId)
        assertEquals("clients", moved.first { it.id == "bank" }.parentId)
    }

    @Test fun treeListsSubfoldersUnderTheirParent() {
        val tree = listOf(bank, home, clients, work).folderTree().map { (folder, depth) -> folder.id to depth }
        assertEquals(listOf("home" to 0, "work" to 0, "clients" to 1, "bank" to 2), tree)
    }
}
