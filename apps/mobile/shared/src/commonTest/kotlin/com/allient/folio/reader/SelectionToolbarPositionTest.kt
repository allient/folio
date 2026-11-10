package com.allient.folio.reader

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionToolbarPositionTest {
    private fun position(rect: Rect) = selectionToolbarPosition(rect, IntSize(400, 800), IntSize(280, 112), 12, 12)

    @Test fun prefersAboveTheSelectedText() {
        assertEquals(IntOffset(90, 176), position(Rect(200f, 300f, 260f, 340f)))
    }

    @Test fun fallsBelowWhenTextIsNearTheTop() {
        assertEquals(IntOffset(90, 62), position(Rect(200f, 20f, 260f, 50f)))
    }

    @Test fun staysWithinSideAndBottomMargins() {
        assertEquals(IntOffset(12, 636), position(Rect(0f, 760f, 20f, 790f)))
        assertEquals(IntOffset(108, 176), position(Rect(390f, 300f, 400f, 340f)))
    }

    private val viewport = IntSize(400, 800)
    private val toolbar = IntSize(280, 48)

    @Test fun noDragKeepsTheAutomaticPosition() {
        assertEquals(IntOffset(90, 176), draggedToolbarPosition(IntOffset(90, 176), IntOffset.Zero, viewport, toolbar, 12))
    }

    @Test fun draggingMovesTheToolbarAndStopsAtTheMargins() {
        assertEquals(IntOffset(60, 226), draggedToolbarPosition(IntOffset(90, 176), IntOffset(-30, 50), viewport, toolbar, 12))
        assertEquals(IntOffset(12, 12), draggedToolbarPosition(IntOffset(90, 176), IntOffset(-500, -500), viewport, toolbar, 12))
        assertEquals(IntOffset(108, 740), draggedToolbarPosition(IntOffset(90, 176), IntOffset(500, 900), viewport, toolbar, 12))
    }

    @Test fun clearanceKeepsTheToolbarOffTheSelectionHandles() {
        // A larger gap leaves room for the handles that hang above the first line and below the last one.
        val rect = Rect(200f, 300f, 260f, 340f)
        assertEquals(IntOffset(90, 300 - 112 - 36), selectionToolbarPosition(rect, IntSize(400, 800), IntSize(280, 112), 12, 36))
    }
}
