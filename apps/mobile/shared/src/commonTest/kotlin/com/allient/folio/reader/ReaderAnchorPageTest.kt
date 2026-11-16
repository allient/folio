package com.allient.folio.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderAnchorPageTest {
    @Test fun readsThePageOfPdfAnchors() {
        assertEquals(0, readerAnchorPage("""{"page":0,"rects":[[0.1,0.2,0.3,0.4]]}"""))
        assertEquals(12, readerAnchorPage("""{"page":12,"points":[[0.5,0.5],[0.6,0.6]],"pressures":[1,1]}"""))
        assertEquals(7, readerAnchorPage("""{"points":[[1,2]],"page": 7,"engine":"jetpack-ink-v5"}"""))
        assertEquals(3, readerAnchorPage("{\"page\" : 3}"))
    }

    @Test fun anchorsWithoutAPageAreMinusOne() {
        assertEquals(-1, readerAnchorPage("""{"href":"chapter1.xhtml","locations":{"progression":0.5}}"""))
        assertEquals(-1, readerAnchorPage(""))
        assertEquals(-1, readerAnchorPage("""{"page":"x"}"""))
    }

    @Test fun annotationsKnowTheirPageWithoutParsing() {
        val note = ReaderAnnotation("a", "doc", AnnotationKind.INK, """{"page":4,"points":[[0,0],[1,1]]}""", createdAt = 1L)
        assertEquals(4, note.page)
        assertEquals(4, note.copy(color = 5).page)
        assertEquals(-1, ReaderAnnotation("b", "doc", AnnotationKind.BOOKMARK, "{}", createdAt = 1L).page)
    }
}
