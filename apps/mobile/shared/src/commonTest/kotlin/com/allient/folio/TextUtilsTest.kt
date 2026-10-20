package com.allient.folio

import kotlin.test.Test
import kotlin.test.assertEquals

class TextUtilsTest {
    @Test
    fun collapseWhitespaceJoinsLinesAndTrims() {
        assertEquals("Grant of Patent License.", "  Grant of\r\n Patent \tLicense.\n".collapseWhitespace())
    }

    @Test
    fun safeFileNameKeepsLettersAndReplacesSeparators() {
        assertEquals("Física (2026) notes", "Física (2026) notes".toSafeFileName("Book"))
        assertEquals("a_b_c", "a/b:c".toSafeFileName("Book"))
    }

    @Test
    fun safeFileNameFallsBackAndCaps() {
        assertEquals("Book", "   ".toSafeFileName("Book"))
        assertEquals(100, "x".repeat(300).toSafeFileName("Book").length)
    }
}
