package com.allient.folio

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/** The pen activation threshold set in the app's pen settings; 0 means no filtering. */
val LocalPenThreshold = compositionLocalOf { 0f }

/**
 * Ignores pen contact lighter than [LocalPenThreshold] inside this element, so a hovering pen
 * cannot pick a menu item. Used for menus, whose popups do not pass through the screen's touch filter.
 */
fun Modifier.penPressureFilter(): Modifier = composed {
    val threshold = LocalPenThreshold.current
    if (threshold <= 0f) this else pointerInput(threshold) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach {
                    if ((it.type == PointerType.Stylus || it.type == PointerType.Eraser) && it.pressure <= threshold) it.consume()
                }
            }
        }
    }
}
