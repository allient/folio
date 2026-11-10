package com.allient.folio

private val skippedElements = Regex("<(head|script|style|svg|math)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val blockBoundary = Regex("</?(p|div|h[1-6]|li|ul|ol|blockquote|pre|tr|table|section|article|header|footer|aside|figure|figcaption|dt|dd|hr)\\b[^>]*>|<br\\s*/?>", RegexOption.IGNORE_CASE)
private val anyTag = Regex("<[^>]*>")
private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")
private val namedEntities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "copy" to "©")
private val namedEntity = Regex("&([a-zA-Z]+);")
private const val PARAGRAPH_BREAK = '\u0000'

/** Plain-text paragraphs of one EPUB (X)HTML document, in reading order. Formatting, images and scripts are dropped. */
fun epubParagraphs(html: String): List<String> {
    val text = html.replace(skippedElements, " ").replace(blockBoundary, PARAGRAPH_BREAK.toString()).replace(anyTag, "")
    return text.split(PARAGRAPH_BREAK).map { decodeEntities(it).replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
}

private fun decodeEntities(value: String): String = value
    .replace(numericEntity) { match ->
        val code = match.groupValues[2].toIntOrNull(if (match.groupValues[1].isEmpty()) 10 else 16)
        if (code == null || code !in 1..0x10FFFF) "" else if (code < 0x10000) code.toChar().toString()
        else (code - 0x10000).let { charArrayOf((0xD800 + (it shr 10)).toChar(), (0xDC00 + (it and 0x3FF)).toChar()).concatToString() }
    }
    .replace(namedEntity) { match -> namedEntities[match.groupValues[1].lowercase()] ?: match.value }

/**
 * Splits already-wrapped lines into pages of at most [linesPerPage]. Each chapter starts on a new page;
 * blank lines (paragraph gaps) are dropped at the top of a page, and empty chapters add no page.
 */
fun paginateChapterLines(chapters: List<List<String>>, linesPerPage: Int): List<List<String>> {
    require(linesPerPage > 0)
    val pages = mutableListOf<List<String>>()
    for (chapter in chapters) {
        var current = mutableListOf<String>()
        for (line in chapter) {
            if (current.isEmpty() && line.isBlank()) continue
            current.add(line)
            if (current.size == linesPerPage) { pages.add(current); current = mutableListOf() }
        }
        while (current.lastOrNull()?.isBlank() == true) current.removeAt(current.lastIndex)
        if (current.isNotEmpty()) pages.add(current)
    }
    return pages
}
