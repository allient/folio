package com.allient.folio.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val FolderInk = Color(0xFF263C35)
private val FolderMuted = Color(0xFF6C7C75)
private val FolderColors = listOf(
    "sage" to Color(0xFFADCDBD), "blue" to Color(0xFFAECFF4),
    "lavender" to Color(0xFFC5BCEC), "rose" to Color(0xFFEDB7C7),
    "peach" to Color(0xFFF1C3A5), "sand" to Color(0xFFE9D48E),
)
private val FolderStyles = listOf("classic" to "Classic", "papers" to "With papers", "tabbed" to "Tabbed", "open" to "Open")

/** Shared by notebook and reader libraries; appearance is part of the saved folder. */
@Composable
fun FolderEditorDialog(
    existing: NoteFolder? = null,
    folders: List<NoteFolder>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var color by rememberSaveable(existing?.id) { mutableStateOf(existing?.color ?: "sage") }
    var style by rememberSaveable(existing?.id) { mutableStateOf(existing?.style ?: "classic") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val duplicate = folders.any { it.id != existing?.id && it.name.equals(name.trim(), ignoreCase = true) }
    val valid = name.isNotBlank() && !duplicate
    val submit = { attempted = true; if (valid) onSave(name.trim(), color, style) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().imePadding().padding(20.dp), contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 540.dp
            Surface(
                modifier = Modifier.widthIn(max = 740.dp).fillMaxWidth().heightIn(max = maxHeight).testTag("folder-editor"),
                shape = RoundedCornerShape(28.dp), color = Color(0xFFFFFEFC), tonalElevation = 0.dp,
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 20.dp, end = 16.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR LIBRARY, ORGANIZED", color = FolderMuted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (existing == null) "A place for your ideas" else "Make it your own", color = FolderInk, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("folder-editor-close").semantics { contentDescription = "Close folder editor" }) {
                            Text("×", fontSize = 28.sp, color = FolderMuted)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE9EDE8))
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(28.dp)) {
                        val preview: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFF1F5F0)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LIVE PREVIEW", fontSize = 9.sp, letterSpacing = 1.6.sp, color = FolderMuted)
                                FolderArtwork(color, style, Modifier.padding(top = 18.dp, bottom = 12.dp).width(160.dp).height(116.dp))
                                Text(name.trim().ifBlank { "Your new folder" }, color = FolderInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${FolderStyles.firstOrNull { it.first == style }?.second ?: "Classic"} · ${color.replaceFirstChar { it.uppercase() }}", color = FolderMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        val fields: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier) {
                                Text("Folder name", color = FolderInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = name, onValueChange = { name = it; attempted = false },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("folder-name").semantics { contentDescription = "Folder name" },
                                    placeholder = { Text("e.g. Creative projects", color = FolderMuted, fontSize = 14.sp) },
                                    singleLine = true, shape = RoundedCornerShape(12.dp),
                                    isError = duplicate || (attempted && name.isBlank()),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submit() }),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF477C65), unfocusedBorderColor = Color(0xFFD6DFD7), focusedTextColor = FolderInk, unfocusedTextColor = FolderInk),
                                )
                                Text(if (duplicate) "A folder with this name already exists." else if (attempted && name.isBlank()) "Give your folder a name." else "Keep related notebooks and documents together.",
                                    color = if (duplicate || (attempted && name.isBlank())) MaterialTheme.colorScheme.error else FolderMuted,
                                    fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
                                Text("Folder color", color = FolderInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
                                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    FolderColors.forEach { (id, tint) ->
                                        Box(Modifier.weight(1f).height(48.dp).clip(CircleShape).selectable(color == id, role = Role.RadioButton, onClick = { color = id })
                                            .testTag("folder-color-$id").semantics { contentDescription = "$id folder color" }, contentAlignment = Alignment.Center) {
                                            Box(Modifier.size(34.dp).border(if (color == id) 2.dp else 0.dp, if (color == id) FolderInk else Color.Transparent, CircleShape).padding(4.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                                                if (color == id) SelectionCheck(Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                            preview(Modifier.width(200.dp)); fields(Modifier.weight(1f))
                        } else Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                            preview(Modifier.fillMaxWidth()); fields(Modifier.fillMaxWidth())
                        }
                        Row(Modifier.padding(top = 28.dp, bottom = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Choose a style", color = FolderInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text("A little personality", color = FolderMuted, fontSize = 11.sp)
                        }
                        // Two rows on narrow screens preserve comfortable touch targets.
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FolderStyles.chunked(if (wide) 4 else 2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { (id, label) ->
                                        val chosen = style == id
                                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                            .background(if (chosen) Color(0xFFEBF2EB) else Color(0xFFF8F9F6))
                                            .border(if (chosen) 1.5.dp else 1.dp, if (chosen) Color(0xFF628873) else Color(0xFFE3E8E0), RoundedCornerShape(14.dp))
                                            .selectable(chosen, role = Role.RadioButton, onClick = { style = id }).testTag("folder-style-$id")
                                            .semantics { contentDescription = "$label folder style" }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            FolderArtwork(color, id, Modifier.width(76.dp).height(58.dp))
                                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                if (chosen) SelectionCheck(Modifier.size(12.dp))
                                                Text(label, fontSize = 11.sp, color = FolderInk, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE9EDE8))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = FolderMuted) }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = submit, enabled = valid, modifier = Modifier.height(48.dp).testTag("folder-save"), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FolderInk, contentColor = Color.White), contentPadding = PaddingValues(horizontal = 24.dp)) {
                            Text(if (existing == null) "Create folder" else "Save changes", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionCheck(modifier: Modifier) {
    Canvas(modifier) {
        val check = Path().apply { moveTo(size.width * .16f, size.height * .5f); lineTo(size.width * .4f, size.height * .74f); lineTo(size.width * .85f, size.height * .23f) }
        drawPath(check, FolderInk, style = Stroke(2.dp.toPx()))
    }
}

/** Resolution-independent artwork shared by the editor preview and saved library cards. */
@Composable
fun FolderArtwork(color: String, style: String, modifier: Modifier = Modifier) {
    val tint = FolderColors.firstOrNull { it.first == color }?.second ?: FolderColors.first().second
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val back = Path().apply {
            moveTo(w * .06f, h * .25f); quadraticTo(w * .06f, h * .1f, w * .17f, h * .1f)
            lineTo(w * .38f, h * .1f); lineTo(w * .48f, h * .24f)
            lineTo(w * .84f, h * .24f); quadraticTo(w * .94f, h * .24f, w * .94f, h * .38f)
            lineTo(w * .94f, h * .82f); quadraticTo(w * .94f, h * .94f, w * .84f, h * .94f)
            lineTo(w * .16f, h * .94f); quadraticTo(w * .06f, h * .94f, w * .06f, h * .82f); close()
        }
        drawPath(back, tint.copy(red = tint.red * .83f, green = tint.green * .87f, blue = tint.blue * .88f))
        if (style == "papers" || style == "open") {
            drawRoundRect(Color(0xFFEAF0E8), Offset(w * .16f, h * .29f), Size(w * .68f, h * .55f), CornerRadius(w * .035f))
            drawRoundRect(Color.White, Offset(w * .2f, h * .34f), Size(w * .66f, h * .5f), CornerRadius(w * .035f))
        }
        val front = Path().apply {
            val top = if (style == "open") .48f else .4f
            moveTo(w * .13f, h * top); lineTo(w * .87f, h * top)
            quadraticTo(w * .98f, h * top, w * .96f, h * (top + .13f))
            lineTo(w * .91f, h * .84f); quadraticTo(w * .9f, h * .94f, w * .81f, h * .94f)
            lineTo(w * .16f, h * .94f); quadraticTo(w * .07f, h * .94f, w * .06f, h * .84f)
            lineTo(w * (if (style == "open") .015f else .06f), h * (top + .12f))
            quadraticTo(w * .04f, h * top, w * .13f, h * top); close()
        }
        drawPath(front, tint)
        drawPath(front, Color.White.copy(alpha = .45f), style = Stroke(1.dp.toPx()))
        if (style == "tabbed") {
            drawRoundRect(Color.White.copy(alpha = .75f), Offset(w * .23f, h * .55f), Size(w * .42f, h * .16f), CornerRadius(w * .025f))
            drawLine(FolderInk.copy(alpha = .3f), Offset(w * .29f, h * .63f), Offset(w * .57f, h * .63f), 1.dp.toPx())
        }
    }
}
