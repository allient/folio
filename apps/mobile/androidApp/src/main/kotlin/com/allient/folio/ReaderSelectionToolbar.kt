package com.allient.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette

/**
 * One compact row: a grip to move the bar, the marks you use most, and a menu for the rest. It stays small so the
 * selection handles around the passage remain reachable, and it can be dragged out of the way.
 */
@Composable
internal fun ReaderSelectionToolbar(
    color: Int, supportsLineStyles: Boolean, canRemove: Boolean, modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit = {},
    onEditMark: (() -> Unit)? = null,
    onColor: (Int) -> Unit, onHighlight: () -> Unit, onUnderline: (String) -> Unit,
    onCopy: () -> Unit, onNote: () -> Unit, onSearch: () -> Unit, onShare: () -> Unit,
    onRemove: () -> Unit, onDismiss: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    var colorMenu by remember { mutableStateOf(false) }
    var underlineMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val view = LocalView.current
    @Composable fun action(icon: InkIcon, label: String, enabled: Boolean = true, action: () -> Unit) {
        IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(40.dp).testTag("reader-selection-${stableControlSlug(label)}")
            .semantics { contentDescription = label }) {
            InkSymbol(icon, Modifier.size(20.dp), if (enabled) ink else ink.copy(alpha = .3f))
        }
    }
    @Composable fun menuItem(icon: InkIcon, label: String, enabled: Boolean = true, action: () -> Unit) {
        DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = action, modifier = Modifier.testTag("reader-selection-${stableControlSlug(label)}"),
            leadingIcon = { InkSymbol(icon, Modifier.size(20.dp), if (enabled) ink else ink.copy(alpha = .3f)) })
    }
    Row(modifier.height(44.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // The popup window moves while it is dragged, so deltas come from screen coordinates, not from the moving window.
        Box(Modifier.width(26.dp).fillMaxHeight().testTag("reader-selection-move").semantics { contentDescription = "Move toolbar" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun screen(point: Offset): Offset { val at = IntArray(2); view.getLocationOnScreen(at); return Offset(at[0] + point.x, at[1] + point.y) }
                    var last = screen(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val now = screen(change.position)
                        if (now != last) { onDrag(now - last); last = now }
                        change.consume()
                    }
                }
            }, contentAlignment = Alignment.Center) {
            LucideGlyph(Lucide.GripVertical, Modifier.size(18.dp), ink.copy(alpha = .45f))
        }
        action(InkIcon.ReaderHighlight, "Highlight", action = onHighlight)
        Box {
            action(InkIcon.ReaderUnderline, "Underline") { if (supportsLineStyles) underlineMenu = true else onUnderline("solid") }
            DropdownMenu(underlineMenu, { underlineMenu = false }) {
                menuItem(InkIcon.ReaderUnderline, "Solid underline") { underlineMenu = false; onUnderline("solid") }
                menuItem(InkIcon.ReaderWavy, "Wavy underline") { underlineMenu = false; onUnderline("wavy") }
                menuItem(InkIcon.ReaderDashed, "Dashed underline") { underlineMenu = false; onUnderline("dashed") }
            }
        }
        Box {
            IconButton(onClick = { colorMenu = true }, modifier = Modifier.size(40.dp).testTag("reader-selection-color").semantics { contentDescription = "Annotation color" }) {
                Box(Modifier.size(20.dp).background(Color(color), RoundedCornerShape(5.dp)).border(1.dp, ink.copy(alpha = .5f), RoundedCornerShape(5.dp)))
            }
            DropdownMenu(colorMenu, { colorMenu = false }) {
                ReaderColors(color, Modifier.width(236.dp).padding(horizontal = 12.dp, vertical = 8.dp), swatch = 34) { onColor(it); colorMenu = false }
            }
        }
        // A long press selects a word even inside a saved mark; this opens that mark's editor (type, color, delete).
        onEditMark?.let { edit ->
            IconButton(onClick = edit, modifier = Modifier.size(40.dp).testTag("reader-selection-edit-mark").semantics { contentDescription = "Edit highlight" }) {
                LucideGlyph(Lucide.Palette, Modifier.size(20.dp), ink)
            }
        }
        action(InkIcon.Copy, "Copy", action = onCopy)
        action(InkIcon.Rename, "Add note", action = onNote)
        Box {
            action(InkIcon.More, "More selection actions") { moreMenu = true }
            DropdownMenu(moreMenu, { moreMenu = false }) {
                menuItem(InkIcon.Search, "Search in book") { moreMenu = false; onSearch() }
                menuItem(InkIcon.Share, "Share") { moreMenu = false; onShare() }
                menuItem(InkIcon.Trash, "Remove marks", canRemove) { moreMenu = false; onRemove() }
            }
        }
        action(InkIcon.Close, "Dismiss selection", action = onDismiss)
    }
}
