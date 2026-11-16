package com.allient.folio.reader

/** Stable document identity is independent of its display name and filesystem location. */
data class ReaderDocument(
    val id: String,
    val title: String,
    val author: String = "",
    val format: String,
    val path: String,
    val coverPath: String = "",
    val contentHash: String,
    val pageCount: Int = 0,
    val locator: String = "",
    val progress: Double = 0.0,
    val openedAt: Long = 0,
    /** User-selected original's content URI; a backup keeps this as a reference, never the file bytes. */
    val sourceUri: String = "",
    /** Folio folder assignment, stored as metadata alongside the source URI reference. */
    val folderId: String? = null,
    val favorite: Boolean = false,
    /** Import time; 0 for books imported before this field existed. */
    val createdAt: Long = 0,
    /** Soft delete: > 0 means the book is in Trash. Annotations and the original file are kept. */
    val deletedAt: Long = 0,
)

enum class AnnotationKind { HIGHLIGHT, UNDERLINE, INK, NOTE, BOOKMARK }

/** PDF anchors contain page-space geometry; EPUB anchors contain Readium Locator JSON. */
data class ReaderAnnotation(
    val id: String,
    val documentId: String,
    val kind: AnnotationKind,
    val anchor: String,
    val quote: String = "",
    val note: String = "",
    val color: Int = 0xFFFFD85A.toInt(),
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    /** The PDF page of [anchor], read once here so no code has to parse the whole anchor to find it; -1 for EPUB anchors. */
    val page: Int = readerAnchorPage(anchor),
)

/**
 * The value of the top-level `"page"` key of a PDF anchor, or -1. A brush stroke's anchor holds thousands of numbers, so
 * parsing it as JSON just to learn the page cost memory and time on every page change; this scans for the key only.
 */
fun readerAnchorPage(anchor: String): Int {
    val key = anchor.indexOf("\"page\"")
    if (key < 0) return -1
    var i = key + 6
    while (i < anchor.length && (anchor[i] == ' ' || anchor[i] == ':')) i++
    var page = 0
    var digits = 0
    while (i < anchor.length && anchor[i] in '0'..'9' && digits < 9) { page = page * 10 + (anchor[i] - '0'); i++; digits++ }
    return if (digits == 0) -1 else page
}
