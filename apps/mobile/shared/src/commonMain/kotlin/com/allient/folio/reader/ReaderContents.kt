package com.allient.folio.reader

/** Platform-neutral outline; PDF targets are zero-based, EPUB targets are resolved by id. */
data class ReaderContentsEntry(
    val id: String,
    val title: String,
    val pageIndex: Int? = null,
    val children: List<ReaderContentsEntry> = emptyList(),
)

data class ReaderContentsRow(val entry: ReaderContentsEntry, val depth: Int)

fun visibleReaderContents(entries: List<ReaderContentsEntry>, expanded: Set<String>, depth: Int = 0): List<ReaderContentsRow> =
    entries.flatMap { entry ->
        listOf(ReaderContentsRow(entry, depth)) + if (entry.id in expanded) visibleReaderContents(entry.children, expanded, depth + 1) else emptyList()
    }

/** A child remains active until the next destination, even when its parent is collapsed. */
fun currentReaderChapter(entries: List<ReaderContentsEntry>, pageIndex: Int): ReaderContentsEntry? {
    var current: ReaderContentsEntry? = null
    fun visit(nodes: List<ReaderContentsEntry>) {
        nodes.forEach { entry ->
            val target = entry.pageIndex
            if (target != null && target <= pageIndex && target >= (current?.pageIndex ?: -1)) current = entry
            visit(entry.children)
        }
    }
    visit(entries)
    return current
}
