package com.allient.folio.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeIconButton(Symbol.Back, "Back to library", false, onBack)
        Text(title, Modifier.weight(1f).padding(start = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = HomeInk)
        trailing()
    }
    HorizontalDivider(color = HomeHairline)
}

internal fun retentionText(days: Int) =
    if (days > 0) "Items are deleted permanently $days days after you delete them." else "Auto-delete is off. Items stay here until you delete them."

@Composable
internal fun TrashScreen(
    items: List<TrashItem>, retentionDays: Int, now: Long, formatDate: (Long) -> String,
    preview: @Composable (TrashItem, Modifier) -> Unit, onBack: () -> Unit,
    onRestore: (TrashItem) -> Unit, onDelete: (TrashItem) -> Unit, onEmpty: () -> Unit,
) {
    val style = LocalHomeStyle.current
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(HomeBackground).testTag("trash-screen")) {
        ScreenHeader("Trash bin", onBack) {
            TextButton(onClick = { confirmEmpty = true }, enabled = items.isNotEmpty(), modifier = Modifier.testTag("trash-empty")) { Text("Empty trash", color = if (items.isNotEmpty()) HomeDanger else HomeMuted) }
        }
        Text(retentionText(retentionDays), Modifier.padding(horizontal = 24.dp, vertical = 14.dp), fontSize = 13.sp, color = HomeMuted)
        if (items.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HomeSymbol(Symbol.Trash, Modifier.size(44.dp), tint = HomeLine)
            Text("Trash is empty", Modifier.padding(top = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = HomeInk)
            Text("Deleted notebooks, folders and books appear here.", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = HomeMuted)
        } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { "${it.kind}:${it.id}" }) { item ->
                val left = TrashRules.daysLeft(item.deletedAt, now, retentionDays)
                Row(Modifier.fillMaxWidth().widthIn(max = 900.dp).clip(RoundedCornerShape(style.item)).background(Color(0xFFF8F8F8)).padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("trash-item-${item.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(44.dp).aspectRatio(.75f).clip(RoundedCornerShape(style.item / 2)).border(1.dp, HomeLine, RoundedCornerShape(style.item / 2)).background(Color.White),
                        contentAlignment = Alignment.Center) { preview(item, Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.detail} · Deleted ${formatDate(item.deletedAt)}", Modifier.padding(top = 2.dp), fontSize = 11.sp, color = HomeMuted, maxLines = 2)
                        Text(when (left) { null -> "Kept until you delete it"; 0 -> "Deleted at next start"; 1 -> "1 day left"; else -> "$left days left" },
                            Modifier.padding(top = 2.dp), fontSize = 11.sp, color = if (left != null && left <= 3) HomeDanger else HomeMuted)
                    }
                    TextButton(onClick = { onRestore(item) }, modifier = Modifier.testTag("trash-restore-${item.id}")) { Text("Restore", color = style.accent) }
                    TextButton(onClick = { confirmDelete = item }, modifier = Modifier.testTag("trash-delete-${item.id}")) { Text("Delete permanently", color = HomeDanger, maxLines = 2, fontSize = 13.sp) }
                }
            }
        }
    }
    confirmDelete?.let { item -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Delete permanently?") },
        text = { Text(when (item.kind) {
            "folder" -> "The folder \"${item.title}\" is removed. Its notebooks and books stay in your library, without a folder."
            "book" -> "\"${item.title}\" and its highlights, notes and reading position are removed from Folio. The original file you imported is not changed."
            else -> "\"${item.title}\" and all its pages are removed from this device. This cannot be undone."
        }) },
        confirmButton = { TextButton(onClick = { onDelete(item); confirmDelete = null }, modifier = Modifier.testTag("trash-confirm-delete")) { Text("Delete permanently", color = HomeDanger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }) }
    if (confirmEmpty) AlertDialog(onDismissRequest = { confirmEmpty = false }, title = { Text("Empty trash?") },
        text = { Text("${items.size} ${if (items.size == 1) "item is" else "items are"} deleted permanently. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { onEmpty(); confirmEmpty = false }, modifier = Modifier.testTag("trash-confirm-empty")) { Text("Empty trash", color = HomeDanger) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } })
}

@Composable
private fun PreferenceSection(title: String, detail: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HomeInk)
        if (detail != null) Text(detail, Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = HomeMuted)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val style = LocalHomeStyle.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(style.item)).selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 4.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = style.accent))
        Text(label, Modifier.padding(start = 12.dp), fontSize = 14.sp, color = HomeInk)
    }
}

@Composable
private fun OptionCard(label: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val style = LocalHomeStyle.current
    Column(modifier.clip(RoundedCornerShape(style.card)).border(if (selected) 2.dp else 1.dp, if (selected) style.accent else HomeHairline, RoundedCornerShape(style.card))
        .background(if (selected) style.soft.copy(alpha = .45f) else Color.White).selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(12.dp).testTag(tag).semantics { contentDescription = label }) {
        content()
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = HomeInk, maxLines = 1)
            if (selected) HomeSymbol(Symbol.Check, Modifier.size(16.dp), tint = style.accent)
        }
    }
}

@Composable
internal fun PreferencesScreen(value: HomePreferences, onChange: (HomePreferences) -> Unit, onBack: () -> Unit, onOpenBackup: () -> Unit) {
    val style = LocalHomeStyle.current
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(HomeBackground).testTag("preferences-screen")) {
        ScreenHeader("Preferences", onBack)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val columns = if (maxWidth >= 600.dp) 4 else 2
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    PreferenceSection("Accent colour", "Used for the library: the selected tab, the + button, the sidebar, check marks and links.") {
                        AccentPresets.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { preset ->
                                    OptionCard(preset.name, value.accent == preset.id, "accent-${preset.id}", Modifier.weight(1f), { onChange(value.copy(accent = preset.id)) }) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.size(30.dp).clip(CircleShape).background(preset.primary))
                                            Box(Modifier.size(30.dp).clip(CircleShape).background(preset.soft).border(1.dp, HomeHairline, CircleShape))
                                        }
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    PreferenceSection("Corners", "Applies to library cards, the tab control and the sidebar.") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CornerStyles.forEach { (id, label) ->
                                val sample = homeStyle(value.copy(corners = id))
                                OptionCard(label, value.corners == id, "corners-$id", Modifier.weight(1f), { onChange(value.copy(corners = id)) }) {
                                    Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(sample.card)).border(1.5.dp, HomeLine, RoundedCornerShape(sample.card)).background(Color(0xFFFAFAFA)))
                                }
                            }
                        }
                    }
                    PreferenceSection("Default view", "The library's Grid and List menu items change this too.") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("grid" to "Grid", "list" to "List").forEach { (id, label) ->
                                OptionCard(label, value.viewMode == id, "view-$id", Modifier.weight(1f), { onChange(value.copy(viewMode = id)) }) {
                                    if (id == "grid") Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { Box(Modifier.size(22.dp, 28.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, HomeLine, RoundedCornerShape(4.dp))) } }
                                    else Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.dp)).background(HomeHairline)) } }
                                }
                            }
                        }
                    }
                    PreferenceSection("Default sort", "The library's Sort by menu changes this too.") {
                        HomeSort.all.forEach { (id, label) -> ChoiceRow(label, value.sort == id, "sort-$id") { onChange(value.copy(sort = id)) } }
                    }
                    PreferenceSection("Notebooks") {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(style.item)).clickable { onChange(value.copy(startFullscreen = !value.startFullscreen)) }.padding(vertical = 4.dp).testTag("pref-start-fullscreen"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(value.startFullscreen, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = style.accent))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text("Start notebooks in fullscreen (focus mode)", fontSize = 14.sp, color = HomeInk)
                                Text("Notebooks opened from the library start with the toolbars hidden. Use the exit button to show them again.", fontSize = 12.sp, lineHeight = 17.sp, color = HomeMuted)
                            }
                        }
                    }
                    PreferenceSection("Trash", "Deleted items are kept in Trash for this long, then deleted when Folio starts.") {
                        TrashRules.retentionOptions.forEach { (days, label) -> ChoiceRow(label, value.trashRetentionDays == days, "retention-$days") { onChange(value.copy(trashRetentionDays = days)) } }
                    }
                    PreferenceSection("Backup") {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(style.item)).border(1.dp, HomeHairline, RoundedCornerShape(style.item)).clickable(onClick = onOpenBackup)
                            .padding(horizontal = 16.dp, vertical = 14.dp).testTag("pref-backup-settings"), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Backup settings", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomeInk)
                                Text("Local backup folder, back up now, export and restore", fontSize = 12.sp, color = HomeMuted)
                            }
                            HomeSymbol(Symbol.ChevronRight, Modifier.size(16.dp))
                        }
                    }
                    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 32.dp).heightIn(min = 48.dp).testTag("pref-reset"),
                        shape = RoundedCornerShape(style.item), border = BorderStroke(1.dp, HomeLine)) { Text("Reset to defaults", color = HomeInk) }
                }
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Reset preferences?") },
        text = { Text("Accent colour, corners, view, sort, fullscreen and Trash retention return to their defaults. Your notebooks and books are not changed.") },
        confirmButton = { TextButton(onClick = { onChange(HomePreferences()); confirmReset = false }, modifier = Modifier.testTag("pref-confirm-reset")) { Text("Reset") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
}

@Composable
internal fun AboutScreen(version: String, onBack: () -> Unit) {
    val style = LocalHomeStyle.current
    Column(Modifier.fillMaxSize().background(HomeBackground).testTag("about-screen")) {
        ScreenHeader("About", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                com.allient.folio.FolioIcon(Modifier.size(96.dp))
                Text("Folio", Modifier.padding(top = 18.dp), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = HomeInk, letterSpacing = (-.5).sp)
                Text("Handwritten Notes & eReader", Modifier.padding(top = 2.dp), fontSize = 15.sp, color = com.allient.folio.FolioBrand.Sage)
                Text("Version $version", Modifier.padding(top = 4.dp).testTag("about-version"), fontSize = 14.sp, color = HomeMuted)
                Text("Folio keeps everything on this device. There are no accounts and no cloud sync, and the app has no internet permission. " +
                    "Notebooks, folders and reading notes are stored in the app's private storage (SQLite databases and the media folder). " +
                    "Uninstalling Folio or clearing its data removes them, so export a .knotes backup to keep a copy.",
                    Modifier.padding(top = 20.dp), fontSize = 15.sp, lineHeight = 23.sp, color = HomeInk)
                Text("Open-source libraries", Modifier.padding(top = 28.dp, bottom = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HomeInk)
                listOf("Jetpack Ink" to "Handwriting strokes and rendering", "PDFium" to "PDF rendering", "PdfBox-Android" to "Annotated PDF export",
                    "Readium" to "EPUB reading", "ML Kit Text Recognition" to "On-device OCR", "Zstandard (zstd-jni)" to "Backup compression", "Geist" to "Interface typeface (SIL OFL)").forEach { (name, use) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomeInk)
                        Text(use, fontSize = 13.sp, color = HomeMuted)
                    }
                    HorizontalDivider(color = HomeHairline)
                }
            }
        }
    }
}
