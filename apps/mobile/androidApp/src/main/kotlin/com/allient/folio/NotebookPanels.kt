package com.allient.folio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_NOTEBOOK_LAYERS = 10

@Composable
internal fun NotebookPageThumbnail(page: NotebookPage, modifier: Modifier = Modifier, bordered: Boolean = true) {
    val filesDir = LocalContext.current.filesDir
    val imagePaths = page.images.map { it.path }
    var loadedImages by remember(imagePaths) { mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap()) }
    DisposableEffect(imagePaths, filesDir) {
        val cancel = NotebookImageStore.preload(page.images, filesDir, maxPixels = 256 * 1024) { loadedImages = it }
        onDispose(cancel)
    }
    Canvas(modifier.background(Color.White).then(if (bordered) Modifier.border(1.dp, ChromeLine) else Modifier)) {
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.clipRect(0f, 0f, size.width, size.height)
            NotebookRenderer.drawPage(canvas.nativeCanvas, page, size.width.toInt(), size.height.toInt(), filesDir, allowImageDecode = false, imageBitmaps = loadedImages)
            canvas.restore()
        }
    }
}

@Composable
internal fun NotebookPageManager(
    notebook: Notebook,
    currentPageId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onPageAction: (pageId: String, action: String) -> Unit,
    onDeletePages: (Set<String>) -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(0) }
    var gridMode by remember { mutableStateOf(true) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIdList by rememberSaveable { mutableStateOf(listOf<String>()) }
    val pageIds = notebook.pages.map { it.id }.toSet()
    val selectedIds = selectedIdList.filter { it in pageIds }.toSet()
    var confirmDelete by remember { mutableStateOf(false) }
    fun toggle(id: String) { selectedIdList = if (id in selectedIds) selectedIds.minus(id).toList() else selectedIds.plus(id).toList() }
    fun startSelecting(id: String? = null) { selecting = true; selectedIdList = listOfNotNull(id) }
    fun stopSelecting() { selecting = false; selectedIdList = emptyList() }
    fun open(id: String) { if (selecting) toggle(id) else onSelect(id) }
    // Deleting every page is not allowed: a notebook keeps at least one page.
    val canDelete = selectedIds.isNotEmpty() && selectedIds.size < notebook.pages.size
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(pluralStringResource(R.plurals.panel_delete_pages, selectedIds.size, selectedIds.size)) },
        text = { Text(stringResource(R.string.panel_delete_pages_message)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeletePages(selectedIds); stopSelecting() }, Modifier.testTag("pages-delete-confirm")) { Text(stringResource(R.string.tools_delete), color = Color(0xFFB3261E)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.tools_cancel)) } },
    )
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F8F7))) {
        if (selecting && tab == 0) Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            InkToolButton(InkIcon.Close, stringResource(R.string.panel_cancel_selection), ::stopSelecting, tag = "Cancel selection")
            Text(if (selectedIds.isEmpty()) stringResource(R.string.panel_select_pages) else pluralStringResource(R.plurals.panel_selected_count, selectedIds.size, selectedIds.size),
                Modifier.weight(1f).padding(start = 8.dp), fontSize = 18.sp, color = ChromeInk, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { selectedIdList = if (selectedIds.size == notebook.pages.size) emptyList() else notebook.pages.map { it.id } }) {
                Text(stringResource(if (selectedIds.size == notebook.pages.size) R.string.panel_clear else R.string.panel_select_all), color = Color(0xFF245B50))
            }
            Button(onClick = { confirmDelete = true }, enabled = canDelete, modifier = Modifier.testTag("pages-delete-selected"), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                InkSymbol(InkIcon.Trash, Modifier.size(17.dp), color = if (canDelete) Color.White else ChromeMuted); Text(stringResource(R.string.tools_delete), Modifier.padding(start = 6.dp))
            }
        } else Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            InkToolButton(InkIcon.Back, stringResource(R.string.panel_back_to_notebook), onClose, tag = "Back to notebook")
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(stringResource(if (tab == 0) R.string.panel_pages else if (tab == 1) R.string.panel_outline else R.string.panel_tags), fontSize = 18.sp, color = ChromeInk, fontWeight = FontWeight.Medium)
                Text(pluralStringResource(R.plurals.panel_pages_title, notebook.pages.size, notebook.pages.size, notebook.title), fontSize = 11.sp, color = ChromeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tab == 0) {
                TextButton(onClick = { gridMode = true }) { Text(stringResource(R.string.panel_grid), color = if (gridMode) Color(0xFF245B50) else ChromeMuted) }
                TextButton(onClick = { gridMode = false }) { Text(stringResource(R.string.panel_list), color = if (!gridMode) Color(0xFF245B50) else ChromeMuted) }
                if (notebook.pages.size > 1) TextButton(onClick = { startSelecting() }, Modifier.testTag("pages-select")) { Text(stringResource(R.string.panel_select), color = ChromeMuted) }
                Button(onClick = onAdd, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    InkSymbol(InkIcon.Add, Modifier.size(17.dp)); Text(stringResource(R.string.panel_add_page), Modifier.padding(start = 6.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            listOf(R.string.panel_pages, R.string.panel_outline, R.string.panel_tags).map { stringResource(it) }.forEachIndexed { index, label ->
                Column(Modifier.weight(1f).clickable(role = Role.Tab) { tab = index }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 13.sp, color = if (tab == index) Color(0xFF245B50) else ChromeMuted, modifier = Modifier.padding(vertical = 10.dp))
                    Box(Modifier.fillMaxWidth().height(if (tab == index) 3.dp else 1.dp).background(if (tab == index) Color(0xFF367967) else ChromeLine))
                }
            }
        }
        HorizontalDivider(color = ChromeLine)
        when (tab) {
            0 -> if (gridMode) LazyVerticalGrid(GridCells.Adaptive(116.dp), Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                gridItems(notebook.pages, key = { it.id }) { page ->
                    val index = notebook.pages.indexOfFirst { it.id == page.id }
                    PageManagerCard(page, index, page.id == currentPageId, { open(page.id) }, onPageAction,
                        checked = if (selecting) page.id in selectedIds else null, onLongPress = { if (!selecting) startSelecting(page.id) })
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(notebook.pages, key = { _, page -> page.id }) { index, page ->
                    val checked = selecting && page.id in selectedIds
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (checked) Color(0xFFE6F1ED) else Color.White),
                        border = androidx.compose.foundation.BorderStroke(if (page.id == currentPageId || checked) 2.dp else 1.dp, if (page.id == currentPageId || checked) Color(0xFF367967) else ChromeLine),
                        modifier = Modifier.combinedClickable(onClick = { open(page.id) }, onLongClick = { if (!selecting) startSelecting(page.id) })) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selecting) PageCheck(checked, Modifier.padding(end = 10.dp))
                            NotebookPageThumbnail(page, Modifier.width(48.dp).aspectRatio(1200f / 1920f).clip(RoundedCornerShape(6.dp)))
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(stringResource(if (page.id == currentPageId) R.string.panel_page_current else R.string.panel_page_number, index + 1), fontSize = 14.sp, color = ChromeInk, fontWeight = FontWeight.Medium)
                                if (page.outline.isNotBlank()) Text(page.outline, fontSize = 12.sp, color = ChromeMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(pluralStringResource(R.plurals.panel_strokes, page.strokes.size, page.strokes.size), fontSize = 11.sp, color = ChromeMuted)
                            }
                            if (!selecting) PageActions(index, notebook.pages.lastIndex, notebook.pages.size) { action -> onPageAction(page.id, action) }
                        }
                    }
                }
            }
            1 -> {
                val outlined = notebook.pages.mapIndexedNotNull { index, page -> if (page.outline.isNotBlank()) index to page else null }
                if (outlined.isEmpty()) Text(stringResource(R.string.panel_outline_empty), fontSize = 14.sp, color = ChromeMuted, modifier = Modifier.padding(24.dp))
                else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                    itemsIndexed(outlined, key = { _, pair -> pair.second.id }) { _, pair ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(pair.second.id) }.padding(16.dp)) {
                            Text(pair.second.outline, fontSize = 15.sp, color = ChromeInk); Text(stringResource(R.string.panel_page_number, pair.first + 1), fontSize = 12.sp, color = ChromeMuted)
                        }; HorizontalDivider(color = ChromeLine)
                    }
                }
            }
            else -> {
                val tags = notebook.pages.flatMap { it.strokes.flatMap { stroke -> stroke.tags } }.distinct().sorted()
                if (tags.isEmpty()) Text(stringResource(R.string.panel_tags_empty), fontSize = 14.sp, color = ChromeMuted, modifier = Modifier.padding(24.dp))
                else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                    itemsIndexed(tags, key = { _, tag -> tag }) { _, tag ->
                        val matches = notebook.pages.filter { page -> page.strokes.any { tag in it.tags } }
                        Column(Modifier.fillMaxWidth().clickable { matches.firstOrNull()?.let { onSelect(it.id) } }.padding(16.dp)) {
                            Text("# $tag", fontSize = 15.sp, color = ChromeInk); Text(pluralStringResource(R.plurals.panel_tag_pages, matches.size, matches.size), fontSize = 12.sp, color = ChromeMuted)
                        }; HorizontalDivider(color = ChromeLine)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCheck(checked: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).clip(CircleShape).background(if (checked) Color(0xFF2F7A68) else Color.White).border(1.5.dp, if (checked) Color(0xFF2F7A68) else ChromeMuted, CircleShape),
        contentAlignment = Alignment.Center) { if (checked) InkSymbol(InkIcon.Saved, Modifier.size(16.dp), color = Color.White) }
}

/** [checked] is null outside selection mode. */
@Composable
private fun PageManagerCard(page: NotebookPage, index: Int, selected: Boolean, onSelect: () -> Unit, onPageAction: (String, String) -> Unit,
    checked: Boolean? = null, onLongPress: () -> Unit = {}) {
    var menu by remember(page.id) { mutableStateOf(false) }
    val highlighted = checked == true
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (highlighted) Color(0xFFE6F1ED) else Color.White),
        border = androidx.compose.foundation.BorderStroke(if (selected || highlighted) 2.dp else 1.dp, if (selected || highlighted) Color(0xFF367967) else ChromeLine)) {
        Column(Modifier.combinedClickable(role = Role.Button, onClick = onSelect, onLongClick = onLongPress).padding(7.dp).testTag("page-card-${index + 1}")) {
            Box {
                NotebookPageThumbnail(page, Modifier.fillMaxWidth().aspectRatio(1200f / 1920f).clip(RoundedCornerShape(8.dp)))
                if (checked != null) PageCheck(checked, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Row(Modifier.fillMaxWidth().height(30.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (selected) R.string.panel_page_current else R.string.panel_page_number, index + 1), Modifier.weight(1f), fontSize = 11.sp, color = ChromeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (checked == null) Box {
                    InkToolButton(InkIcon.More, stringResource(R.string.panel_page_actions, index + 1), { menu = true }, buttonSize = 26.dp, tag = "Page ${index + 1} actions")
                    DropdownMenu(menu, { menu = false }, modifier = Modifier.penPressureFilter()) {
                        pageActions.forEach { (action, label) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menu = false; onPageAction(page.id, action) })
                        }
                    }
                }
            }
        }
    }
}

/** Page menu entries: the action ids go to the editor, the labels are resources. */
private val pageActions = listOf("outline" to R.string.panel_edit_outline, "duplicate" to R.string.panel_duplicate,
    "move-up" to R.string.panel_move_earlier, "move-down" to R.string.panel_move_later, "delete" to R.string.tools_delete)

/** Layers are stored with English default names ("Layer 2"); those are shown translated, names the user typed as saved. */
@Composable
private fun layerLabel(name: String): String =
    Regex("^Layer (\\d+)$").matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.let { stringResource(R.string.panel_layer_default_name, it) } ?: name

@Composable
private fun PageActions(index: Int, lastIndex: Int, pageCount: Int, onAction: (String) -> Unit) {
    var menu by remember(index) { mutableStateOf(false) }
    Box {
        InkToolButton(InkIcon.More, stringResource(R.string.panel_page_actions, index + 1), { menu = true }, buttonSize = 32.dp, tag = "Page ${index + 1} actions")
        DropdownMenu(menu, { menu = false }, modifier = Modifier.penPressureFilter()) {
            pageActions.forEach { (action, label) ->
                val enabled = when(action) { "move-up" -> index > 0; "move-down" -> index < lastIndex; "delete" -> pageCount > 1; else -> true }
                DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = enabled, onClick = { menu = false; onAction(action) })
            }
        }
    }
}

@Composable
internal fun LayerControls(
    page: NotebookPage,
    activeLayerId: String,
    onActiveLayer: (String) -> Unit,
    onChange: (NotebookPage) -> Unit,
    onChooseDestination: ((NotebookPage, String) -> Unit)? = null,
) {
    var deleting by remember(page.id) { mutableStateOf<InkLayer?>(null) }
    var renaming by remember(page.id) { mutableStateOf<String?>(null) }
    var layerName by remember(page.id) { mutableStateOf("") }
    fun changeLayer(layer: InkLayer) {
        onChange(page.copy(layers = page.layers.map { if (it.id == layer.id) layer else it }))
        if (layer.id == activeLayerId && (!layer.visible || layer.locked)) {
            page.layers.firstOrNull { it.id != layer.id && it.visible && !it.locked }?.let { onActiveLayer(it.id) }
        }
    }
    fun moveLayer(layer: InkLayer, delta: Int) {
        val index = page.layers.indexOfFirst { it.id == layer.id }
        val destination = index + delta
        if (index < 0 || destination !in page.layers.indices) return
        val updated = page.layers.toMutableList()
        updated.removeAt(index); updated.add(destination, layer)
        onChange(page.copy(layers = updated))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (onChooseDestination == null) R.string.panel_layers else R.string.panel_move_to_layer), color = ChromeInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(if (onChooseDestination == null) R.string.panel_layers_count_draw else R.string.panel_layers_count_move, page.layers.size, MAX_NOTEBOOK_LAYERS), color = ChromeMuted, fontSize = 11.sp)
            }
            TextButton(enabled = page.layers.size < MAX_NOTEBOOK_LAYERS, onClick = {
                // Stored in English like the first layer; layerLabel translates it on screen.
                val layer = InkLayer(name = "Layer ${page.layers.size + 1}")
                val updated = page.copy(layers = page.layers + layer)
                onChange(updated); onActiveLayer(layer.id)
            }) { Text(stringResource(R.string.panel_add_layer), color = if (page.layers.size < MAX_NOTEBOOK_LAYERS) ChromeInk else ChromeMuted) }
        }
        page.layers.asReversed().forEach { layer ->
            var menu by remember(layer.id) { mutableStateOf(false) }
            val preview = page.copy(
                layers = listOf(layer.copy(visible = true, locked = false)),
                strokes = page.strokes.filter { it.layerId == layer.id },
                texts = page.texts.filter { it.layerId == layer.id },
                images = page.images.filter { it.layerId == layer.id },
            )
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (layer.id == activeLayerId) Color(0xFFEAF3EF) else Color(0xFFF7F8F7))
                .border(if (layer.id == activeLayerId) 2.dp else 1.dp, if (layer.id == activeLayerId) Color(0xFF347765) else ChromeLine, RoundedCornerShape(16.dp))
                .clickable(enabled = onChooseDestination == null || (layer.visible && !layer.locked)) {
                    if (onChooseDestination != null) onChooseDestination(page, layer.id) else onActiveLayer(layer.id)
                }.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NotebookPageThumbnail(preview, Modifier.width(42.dp).height(58.dp).clip(RoundedCornerShape(7.dp)))
                val name = layerLabel(layer.name)
                Column(Modifier.weight(1f)) {
                    Text(name, color = ChromeInk, fontSize = 14.sp, fontWeight = if (layer.id == activeLayerId) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(when {
                        layer.id == activeLayerId -> R.string.panel_layer_active
                        layer.locked -> R.string.panel_layer_locked
                        !layer.visible -> R.string.panel_layer_hidden
                        else -> R.string.panel_layer_tap
                    }), color = ChromeMuted, fontSize = 11.sp)
                }
                InkToolButton(if (layer.visible) InkIcon.Visibility else InkIcon.VisibilityOff,
                    stringResource(if (layer.visible) R.string.panel_hide_layer else R.string.panel_show_layer, name),
                    { changeLayer(layer.copy(visible = !layer.visible)) }, selected = !layer.visible, buttonSize = 40.dp,
                    tag = if (layer.visible) "Hide ${layer.name}" else "Show ${layer.name}")
                InkToolButton(if (layer.locked) InkIcon.Lock else InkIcon.LockOpen,
                    stringResource(if (layer.locked) R.string.panel_unlock_layer else R.string.panel_lock_layer, name),
                    { changeLayer(layer.copy(locked = !layer.locked)) }, selected = layer.locked, buttonSize = 40.dp,
                    tag = if (layer.locked) "Unlock ${layer.name}" else "Lock ${layer.name}")
                Box {
                    InkToolButton(InkIcon.More, stringResource(R.string.panel_layer_actions, name), { menu = true }, buttonSize = 36.dp, tag = "${layer.name} actions")
                    DropdownMenu(menu, { menu = false }, modifier = Modifier.penPressureFilter()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.tools_rename)) }, onClick = { menu = false; renaming = layer.id; layerName = name })
                        DropdownMenuItem(text = { Text(stringResource(R.string.panel_move_up)) }, enabled = page.layers.indexOf(layer) < page.layers.lastIndex, onClick = { menu = false; moveLayer(layer, 1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.panel_move_down)) }, enabled = page.layers.indexOf(layer) > 0, onClick = { menu = false; moveLayer(layer, -1) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.panel_delete_layer)) }, enabled = page.layers.size > 1, onClick = { menu = false; deleting = layer })
                    }
                }
            }
            if (renaming == layer.id) {
                OutlinedTextField(layerName, { layerName = it }, label = { Text(stringResource(R.string.panel_layer_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.tools_cancel)) }
                    TextButton(enabled = layerName.isNotBlank(), onClick = { changeLayer(layer.copy(name = layerName.trim())); renaming = null }) { Text(stringResource(R.string.tools_rename)) }
                }
            }
        }
    }
    deleting?.let { layer ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.panel_delete_layer_title, layerLabel(layer.name))) },
            text = { Text(stringResource(R.string.panel_delete_layer_message)) },
            confirmButton = { TextButton(onClick = {
                if (page.layers.size > 1) {
                    val remaining = page.layers.filterNot { it.id == layer.id }
                    onChange(page.copy(layers = remaining, strokes = page.strokes.filterNot { it.layerId == layer.id }, texts = page.texts.filterNot { it.layerId == layer.id }, images = page.images.filterNot { it.layerId == layer.id }))
                    if (activeLayerId == layer.id) onActiveLayer(remaining.last().id)
                }
                deleting = null
            }) { Text(stringResource(R.string.tools_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.tools_cancel)) } })
    }
}
