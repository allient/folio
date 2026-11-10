package com.allient.folio.reader

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Prefer above the selected passage, then below it, keeping the toolbar inside the viewport. */
fun selectionToolbarPosition(selection: Rect, viewport: IntSize, toolbar: IntSize, margin: Int, gap: Int): IntOffset {
    val maxX = (viewport.width - toolbar.width - margin).coerceAtLeast(margin)
    val maxY = (viewport.height - toolbar.height - margin).coerceAtLeast(margin)
    val x = (selection.center.x - toolbar.width / 2f).roundToInt().coerceIn(margin, maxX)
    val above = selection.top.roundToInt() - toolbar.height - gap
    val below = selection.bottom.roundToInt() + gap
    val y = when {
        above >= margin -> above
        below <= maxY -> below
        selection.top > viewport.height - selection.bottom -> above
        else -> below
    }.coerceIn(margin, maxY)
    return IntOffset(x, y)
}

/** The automatic [base] position moved by the user's [drag], kept inside the viewport margins. */
fun draggedToolbarPosition(base: IntOffset, drag: IntOffset, viewport: IntSize, toolbar: IntSize, margin: Int): IntOffset {
    val maxX = (viewport.width - toolbar.width - margin).coerceAtLeast(margin)
    val maxY = (viewport.height - toolbar.height - margin).coerceAtLeast(margin)
    return IntOffset((base.x + drag.x).coerceIn(margin, maxX), (base.y + drag.y).coerceIn(margin, maxY))
}
