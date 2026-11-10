package com.allient.folio

import kotlin.test.Test
import kotlin.test.assertEquals

class EpubTextTest {
    @Test fun blockElementsBecomeParagraphsAndInlineTagsAreDropped() {
        val html = """<html><head><title>Skip me</title><style>p{color:red}</style></head>
            <body><h1>Chapter  One</h1><p>It was a <em>dark</em> and
            stormy night.</p><p>Second&nbsp;paragraph &amp; more&#8230;</p><br/><div>Last</div><script>x()</script></body></html>"""
        assertEquals(listOf("Chapter One", "It was a dark and stormy night.", "Second paragraph & more…", "Last"), epubParagraphs(html))
    }

    @Test fun emptyOrWhitespaceOnlyMarkupHasNoParagraphs() {
        assertEquals(emptyList<String>(), epubParagraphs("<html><body><p>  </p><div>\n</div></body></html>"))
    }

    @Test fun linesFillPagesAndAChapterStartsANewPage() {
        val chapters = listOf(listOf("a1", "a2", "a3"), listOf("b1"))
        assertEquals(listOf(listOf("a1", "a2"), listOf("a3"), listOf("b1")), paginateChapterLines(chapters, linesPerPage = 2))
    }

    @Test fun blankLinesAtThePageTopAreSkippedAndEmptyChaptersAddNoPage() {
        val chapters = listOf(listOf("a1", "a2", "", "a3"), emptyList(), listOf("", "b1"))
        assertEquals(listOf(listOf("a1", "a2"), listOf("a3"), listOf("b1")), paginateChapterLines(chapters, linesPerPage = 2))
    }
}
