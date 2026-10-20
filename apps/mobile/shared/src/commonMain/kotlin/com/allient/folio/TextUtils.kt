package com.allient.folio

private val Whitespace = Regex("\\s+")

/** One readable line: runs of spaces, tabs and line breaks become a single space, then trimmed. */
fun String.collapseWhitespace(): String = replace(Whitespace, " ").trim()

private val UnsafeFileCharacters = Regex("[^\\p{L}\\p{N}._ ()-]")

/** A file name that works on every storage provider: letters, digits and a few separators, at most 100 characters. */
fun String.toSafeFileName(fallback: String): String =
    replace(UnsafeFileCharacters, "_").trim().take(100).ifBlank { fallback }
