package com.allient.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Two compact rows keep all supported selection actions visible without sideways scrolling. */
@Composable
internal fun ReaderSelectionToolbar(
    color: Int, supportsLineStyles: Boolean, canRemove: Boolean, modifier: Modifier = Modifier,
    onColor: (Int) -> Unit, onHighlight: () -> Unit, onUnderline: (String) -> Unit,
    onCopy: () -> Unit, onNote: () -> Unit, onSearch: () -> Unit, onShare: () -> Unit,
    onRemove: () -> Unit, onDismiss: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    var colorMenu by remember { mutableStateOf(false) }
    @Composable fun RowScope.action(icon: InkIcon, label: String, enabled: Boolean = true, action: () -> Unit) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(48.dp).testTag("reader-selection-${stableControlSlug(label)}")
                .semantics { contentDescription = label }) {
                InkSymbol(icon, Modifier.size(26.dp), if (enabled) ink else ink.copy(alpha = .3f))
            }
        }
    }
    Column(modifier.padding(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            action(InkIcon.ReaderUnderline, "Underline") { onUnderline("solid") }
            if (supportsLineStyles) {
                action(InkIcon.ReaderWavy, "Wavy underline") { onUnderline("wavy") }
                action(InkIcon.ReaderDashed, "Dashed underline") { onUnderline("dashed") }
            }
            action(InkIcon.ReaderHighlight, "Highlight", action = onHighlight)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = { colorMenu = true }, modifier = Modifier.size(48.dp).testTag("reader-selection-color").semantics { contentDescription = "Annotation color" }) {
                    Box(Modifier.size(24.dp).background(Color(color), RoundedCornerShape(5.dp)).border(1.dp, ink.copy(alpha = .5f), RoundedCornerShape(5.dp)))
                }
                DropdownMenu(colorMenu, { colorMenu = false }) {
                    Row(Modifier.padding(horizontal = 8.dp)) { ReaderColors(color) { onColor(it); colorMenu = false } }
                }
            }
            action(InkIcon.Trash, "Remove marks", canRemove, onRemove)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            action(InkIcon.Copy, "Copy", action = onCopy)
            action(InkIcon.Rename, "Add note", action = onNote)
            action(InkIcon.Search, "Search in book", action = onSearch)
            action(InkIcon.Share, "Share", action = onShare)
            action(InkIcon.Close, "Dismiss selection", action = onDismiss)
        }
    }
}
