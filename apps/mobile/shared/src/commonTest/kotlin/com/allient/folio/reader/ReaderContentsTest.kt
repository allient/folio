package com.allient.folio.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderContentsTest {
    private val child = ReaderContentsEntry("child", "Subchapter", 3)
    private val chapters = listOf(ReaderContentsEntry("parent", "Chapter", 1, listOf(child)), ReaderContentsEntry("next", "Next chapter", 8))

    @Test fun collapsingParentHidesChildrenAndExpansionRetainsHierarchy() {
        assertEquals(listOf("parent", "next"), visibleReaderContents(chapters, emptySet()).map { it.entry.id })
        val rows = visibleReaderContents(chapters, setOf("parent"))
        assertEquals(listOf("parent", "child", "next"), rows.map { it.entry.id })
        assertEquals(listOf(0, 1, 0), rows.map { it.depth })
    }

    @Test fun currentChapterFollowsDestinationsRegardlessOfCollapsedState() {
        assertNull(currentReaderChapter(chapters, 0))
        assertEquals("parent", currentReaderChapter(chapters, 2)?.id)
        assertEquals("child", currentReaderChapter(chapters, 7)?.id)
        assertEquals("next", currentReaderChapter(chapters, 8)?.id)
    }
}
