package com.allient.folio

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentDocumentTabsTest {
    @Test fun includesCurrentAndRecentlyOpenedItemsButExcludesTrashAndClosedTabs() {
        val notes = listOf(
            DocumentTab("current", "Current", "Notebook"),
            DocumentTab("recent", "Recent", "Canvas", openedAt = 30),
            DocumentTab("older", "Older", "Notebook", openedAt = 10),
            DocumentTab("closed", "Closed", "Notebook", openedAt = 50),
            DocumentTab("trash", "Trash", "Notebook", openedAt = 100, deletedAt = 1),
            DocumentTab("unopened", "Unopened", "Notebook"),
        )
        assertEquals(listOf("current", "recent", "older"), recentDocumentTabs(notes, "current", setOf("closed")).map { it.id })
        assertEquals(6, notes.size)
    }

    @Test fun reopeningAClosedNoteShowsItAndKeepsTheMostRecentNeighbours() {
        val notes = (1..20).map { DocumentTab("$it", "Note $it", "Notebook", openedAt = it.toLong()) }
        assertEquals(listOf("1", "20", "19"), recentDocumentTabs(notes, "1", setOf("1"), limit = 3).map { it.id })
        assertEquals(12, recentDocumentTabs(notes, "1", emptySet()).size)
    }
}

class StableDocumentTabOrderTest {
    private val notes = (1..5).map { DocumentTab("$it", "Note $it", "Notebook", openedAt = it.toLong()) }

    @Test fun selectingAnOpenTabKeepsTheOrder() {
        assertEquals(listOf("1", "2", "3"), stableDocumentTabOrder(listOf("1", "2", "3"), notes, "2", emptySet()))
    }

    @Test fun newTabsGoToTheEndAndClosedOrDeletedTabsDisappear() {
        val withTrash = notes.map { if (it.id == "2") it.copy(deletedAt = 1) else it }
        assertEquals(listOf("1", "5"), stableDocumentTabOrder(listOf("1", "2", "3"), withTrash, "5", setOf("3")))
    }

    @Test fun overflowDropsTheOldestTabButNeverTheCurrentOne() {
        assertEquals(listOf("3", "1"), stableDocumentTabOrder(listOf("2", "3"), notes, "1", emptySet(), limit = 2))
        assertEquals(listOf("2", "3"), stableDocumentTabOrder(listOf("1", "2", "3"), notes, "3", emptySet(), limit = 2))
    }

    @Test fun emptyOrderIsSeededFromRecentTabs() {
        assertEquals(listOf("1", "5", "4"), stableDocumentTabOrder(emptyList(), notes, "1", emptySet(), limit = 3))
    }
}
