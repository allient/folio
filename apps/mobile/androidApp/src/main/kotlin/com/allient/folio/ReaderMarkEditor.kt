package com.allient.folio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.allient.folio.reader.AnnotationKind
import com.allient.folio.reader.ReaderAnnotation
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/** Highlight and underline colours; the first four are the original palette, so saved marks keep their swatch. */
internal val ReaderMarkColors = listOf(
    "Yellow" to 0xFFFFD85A.toInt(), "Orange" to 0xFFFFB870.toInt(), "Coral" to 0xFFFF9C94.toInt(), "Pink" to 0xFFF0A6BD.toInt(),
    "Purple" to 0xFFC9AEF2.toInt(), "Blue" to 0xFF8FC7F4.toInt(), "Cyan" to 0xFF86DDE6.toInt(), "Green" to 0xFF93D9AF.toInt(),
    "Lime" to 0xFFD5E88A.toInt(), "Sand" to 0xFFE8D5A3.toInt(), "Gray" to 0xFFC9CED6.toInt(),
)

/** Swatch grid plus a "+" swatch that opens the full colour picker. A colour outside the palette shows as its own selected swatch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderColors(selected: Int, modifier: Modifier = Modifier, swatch: Int = 40, onSelect: (Int) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    val custom = selected.takeIf { value -> ReaderMarkColors.none { it.second == value } }
    val ink = MaterialTheme.colorScheme.onSurface
    @Composable fun dot(label: String, color: Int) {
        val chosen = selected == color
        Box(Modifier.size(swatch.dp).semantics { contentDescription = "$label highlight"; this.selected = chosen }
            .clip(CircleShape).background(Color(color)).border(if (chosen) 2.dp else 1.dp, if (chosen) ink else ink.copy(alpha = .18f), CircleShape)
            .clickable(onClickLabel = "Select ${label.lowercase()} highlight", role = Role.Button) { onSelect(color) },
            contentAlignment = Alignment.Center) { if (chosen) InkSymbol(InkIcon.Saved, Modifier.size((swatch * .5f).dp), ink) }
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderMarkColors.forEach { (label, color) -> dot(label, color) }
        custom?.let { dot("Custom", it) }
        Box(Modifier.size(swatch.dp).testTag("reader-color-custom").semantics { contentDescription = "Custom color" }.clip(CircleShape)
            .background(Brush.sweepGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFFD85A), Color(0xFF6BD68B), Color(0xFF5AB8FF), Color(0xFFB07CFF), Color(0xFFFF6B6B))))
            .border(1.dp, ink.copy(alpha = .18f), CircleShape).clickable(onClickLabel = "Pick a custom color", role = Role.Button) { picker = true },
            contentAlignment = Alignment.Center) {
            Box(Modifier.size((swatch * .5f).dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                LucideGlyph(Lucide.Plus, Modifier.size((swatch * .36f).dp), Color(0xFF1E2B28))
            }
        }
    }
    if (picker) BrushColorPicker(selected, onDismiss = { picker = false }, onApply = { value -> picker = false; onSelect(value or 0xFF000000.toInt()) })
}

/** Edit sheet for a saved highlight or underline: type, colour, a live preview, then Delete / Cancel / Save. */
@Composable
internal fun ReaderMarkEditor(
    mark: ReaderAnnotation, kind: AnnotationKind, color: Int,
    onKind: (AnnotationKind) -> Unit, onColor: (Int) -> Unit,
    onSave: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val sage = Color(0xFF2F7A68)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 380.dp).fillMaxWidth().testTag("reader-mark-editor"), shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 12.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(if (kind == AnnotationKind.UNDERLINE) "Edit underline" else "Edit highlight", fontSize = 20.sp, color = ink)
                // Type
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink.copy(alpha = .06f)).padding(4.dp)) {
                    listOf(AnnotationKind.HIGHLIGHT to ("Highlight" to InkIcon.ReaderHighlight), AnnotationKind.UNDERLINE to ("Underline" to InkIcon.ReaderUnderline)).forEach { (option, label) ->
                        val chosen = kind == option
                        Row(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(if (chosen) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .then(if (chosen) Modifier.border(1.dp, sage.copy(alpha = .5f), RoundedCornerShape(11.dp)) else Modifier)
                            .semantics { selected = chosen }.clickable(role = Role.Tab) { onKind(option) },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            InkSymbol(label.second, Modifier.size(20.dp), if (chosen) sage else ink.copy(alpha = .65f))
                            Text(label.first, Modifier.padding(start = 8.dp), fontSize = 14.sp, color = if (chosen) sage else ink.copy(alpha = .75f))
                        }
                    }
                }
                // Preview
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, ink.copy(alpha = .1f), RoundedCornerShape(14.dp)).padding(16.dp)) {
                    val tint = Color(color)
                    Text("A passage of the book", fontSize = 17.sp, color = Color(0xFF1E2B28), fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        modifier = if (kind == AnnotationKind.HIGHLIGHT) Modifier.background(tint.copy(alpha = .55f), RoundedCornerShape(3.dp)).padding(horizontal = 2.dp)
                        else Modifier.drawBehind { drawLine(tint, Offset(0f, size.height), Offset(size.width, size.height), 2.5.dp.toPx()) })
                }
                // Colour
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Color", fontSize = 13.sp, color = ink.copy(alpha = .65f))
                    ReaderColors(color, Modifier.fillMaxWidth(), onSelect = onColor)
                }
                // Actions
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB3261E)), modifier = Modifier.testTag("reader-mark-delete")) {
                        InkSymbol(InkIcon.Trash, Modifier.size(18.dp), Color(0xFFB3261E)); Text("Delete", Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Cancel", color = ink) }
                    Button(onClick = onSave, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = sage), modifier = Modifier.padding(start = 6.dp).testTag("reader-mark-save")) { Text("Save") }
                }
            }
        }
    }
}
