package com.allient.folio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_NOTEBOOK_LAYERS = 10

@Composable
internal fun NotebookPageThumbnail(page: NotebookPage, modifier: Modifier = Modifier) {
    val filesDir = LocalContext.current.filesDir
    val imagePaths = page.images.map { it.path }
    var loadedImages by remember(imagePaths) { mutableStateOf<Map<String, android.graphics.Bitmap>>(emptyMap()) }
    DisposableEffect(imagePaths, filesDir) {
        val cancel = NotebookImageStore.preload(page.images, filesDir, maxPixels = 256 * 1024) { loadedImages = it }
        onDispose(cancel)
    }
    Canvas(modifier.background(Color.White).border(1.dp, ChromeLine)) {
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
) {
    var tab by remember { mutableIntStateOf(0) }
    var gridMode by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F8F7))) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            InkToolButton(InkIcon.Back, "Back to notebook", onClose)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(if (tab == 0) "Pages" else if (tab == 1) "Outline" else "Tags", fontSize = 18.sp, color = ChromeInk, fontWeight = FontWeight.Medium)
                Text("${notebook.pages.size} ${if (notebook.pages.size == 1) "page" else "pages"} · ${notebook.title}", fontSize = 11.sp, color = ChromeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tab == 0) {
                TextButton(onClick = { gridMode = true }) { Text("Grid", color = if (gridMode) Color(0xFF245B50) else ChromeMuted) }
                TextButton(onClick = { gridMode = false }) { Text("List", color = if (!gridMode) Color(0xFF245B50) else ChromeMuted) }
                Button(onClick = onAdd, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    InkSymbol(InkIcon.Add, Modifier.size(17.dp)); Text("Add page", Modifier.padding(start = 6.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            listOf("Pages", "Outline", "Tags").forEachIndexed { index, label ->
                Column(Modifier.weight(1f).clickable(role = Role.Tab) { tab = index }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 13.sp, color = if (tab == index) Color(0xFF245B50) else ChromeMuted, modifier = Modifier.padding(vertical = 10.dp))
                    Box(Modifier.fillMaxWidth().height(if (tab == index) 3.dp else 1.dp).background(if (tab == index) Color(0xFF367967) else ChromeLine))
                }
            }
        }
        HorizontalDivider(color = ChromeLine)
        when (tab) {
            0 -> if (gridMode) LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                gridItems(notebook.pages, key = { it.id }) { page ->
                    val index = notebook.pages.indexOfFirst { it.id == page.id }
                    PageManagerCard(page, index, page.id == currentPageId, { onSelect(page.id) }, onPageAction)
                }
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(notebook.pages, key = { _, page -> page.id }) { index, page ->
                    Card(onClick = { onSelect(page.id) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(if (page.id == currentPageId) 2.dp else 1.dp, if (page.id == currentPageId) Color(0xFF367967) else ChromeLine)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            NotebookPageThumbnail(page, Modifier.width(66.dp).aspectRatio(1200f / 1920f).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text("Page ${index + 1}${if (page.id == currentPageId) " · Current" else ""}", fontSize = 14.sp, color = ChromeInk, fontWeight = FontWeight.Medium)
                                if (page.outline.isNotBlank()) Text(page.outline, fontSize = 12.sp, color = ChromeMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${page.strokes.size} strokes", fontSize = 11.sp, color = ChromeMuted)
                            }
                            PageActions(index, notebook.pages.lastIndex, notebook.pages.size) { action -> onPageAction(page.id, action) }
                        }
                    }
                }
            }
            1 -> {
                val outlined = notebook.pages.mapIndexedNotNull { index, page -> if (page.outline.isNotBlank()) index to page else null }
                if (outlined.isEmpty()) Text("Add an outline from a page's menu.", fontSize = 14.sp, color = ChromeMuted, modifier = Modifier.padding(24.dp))
                else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                    itemsIndexed(outlined, key = { _, pair -> pair.second.id }) { _, pair ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(pair.second.id) }.padding(16.dp)) {
                            Text(pair.second.outline, fontSize = 15.sp, color = ChromeInk); Text("Page ${pair.first + 1}", fontSize = 12.sp, color = ChromeMuted)
                        }; HorizontalDivider(color = ChromeLine)
                    }
                }
            }
            else -> {
                val tags = notebook.pages.flatMap { it.strokes.flatMap { stroke -> stroke.tags } }.distinct().sorted()
                if (tags.isEmpty()) Text("Select handwriting with the lasso to add a tag.", fontSize = 14.sp, color = ChromeMuted, modifier = Modifier.padding(24.dp))
                else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
                    itemsIndexed(tags, key = { _, tag -> tag }) { _, tag ->
                        val matches = notebook.pages.filter { page -> page.strokes.any { tag in it.tags } }
                        Column(Modifier.fillMaxWidth().clickable { matches.firstOrNull()?.let { onSelect(it.id) } }.padding(16.dp)) {
                            Text("# $tag", fontSize = 15.sp, color = ChromeInk); Text("${matches.size} ${if (matches.size == 1) "page" else "pages"}", fontSize = 12.sp, color = ChromeMuted)
                        }; HorizontalDivider(color = ChromeLine)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageManagerCard(page: NotebookPage, index: Int, selected: Boolean, onSelect: () -> Unit, onPageAction: (String, String) -> Unit) {
    var menu by remember(page.id) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF367967) else ChromeLine)) {
        Column(Modifier.padding(9.dp)) {
            NotebookPageThumbnail(page, Modifier.fillMaxWidth().aspectRatio(1200f / 1920f).clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onSelect))
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Page ${index + 1}${if (selected) " · Current" else ""}", Modifier.weight(1f).clickable(onClick = onSelect), fontSize = 12.sp, color = ChromeInk)
                Box {
                    InkToolButton(InkIcon.More, "Page ${index + 1} actions", { menu = true }, buttonSize = 30.dp)
                    DropdownMenu(menu, { menu = false }) {
                        listOf("outline" to "Edit outline", "duplicate" to "Duplicate", "move-up" to "Move earlier", "move-down" to "Move later", "delete" to "Delete").forEach { (action, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onPageAction(page.id, action) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageActions(index: Int, lastIndex: Int, pageCount: Int, onAction: (String) -> Unit) {
    var menu by remember(index) { mutableStateOf(false) }
    Box {
        InkToolButton(InkIcon.More, "Page ${index + 1} actions", { menu = true }, buttonSize = 32.dp)
        DropdownMenu(menu, { menu = false }) {
            listOf("outline" to "Edit outline", "duplicate" to "Duplicate", "move-up" to "Move earlier", "move-down" to "Move later", "delete" to "Delete").forEach { (action, label) ->
                val enabled = when(action) { "move-up" -> index > 0; "move-down" -> index < lastIndex; "delete" -> pageCount > 1; else -> true }
                DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { menu = false; onAction(action) })
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
                Text(if (onChooseDestination == null) "Layers" else "Move selection to layer", color = ChromeInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${page.layers.size} of $MAX_NOTEBOOK_LAYERS · " + if (onChooseDestination == null) "Tap a layer to draw on it" else "Choose an unlocked, visible destination", color = ChromeMuted, fontSize = 11.sp)
            }
            TextButton(enabled = page.layers.size < MAX_NOTEBOOK_LAYERS, onClick = {
                val layer = InkLayer(name = "Layer ${page.layers.size + 1}")
                val updated = page.copy(layers = page.layers + layer)
                onChange(updated); onActiveLayer(layer.id)
            }) { Text("Add layer", color = if (page.layers.size < MAX_NOTEBOOK_LAYERS) ChromeInk else ChromeMuted) }
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
                Column(Modifier.weight(1f)) {
                    Text(layer.name, color = ChromeInk, fontSize = 14.sp, fontWeight = if (layer.id == activeLayerId) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        layer.id == activeLayerId -> "Active layer"
                        layer.locked -> "Locked"
                        !layer.visible -> "Hidden"
                        else -> "Tap to select"
                    }, color = ChromeMuted, fontSize = 11.sp)
                }
                InkToolButton(if (layer.visible) InkIcon.Visibility else InkIcon.VisibilityOff,
                    if (layer.visible) "Hide ${layer.name}" else "Show ${layer.name}",
                    { changeLayer(layer.copy(visible = !layer.visible)) }, selected = !layer.visible, buttonSize = 40.dp)
                InkToolButton(if (layer.locked) InkIcon.Lock else InkIcon.LockOpen,
                    if (layer.locked) "Unlock ${layer.name}" else "Lock ${layer.name}",
                    { changeLayer(layer.copy(locked = !layer.locked)) }, selected = layer.locked, buttonSize = 40.dp)
                Box {
                    InkToolButton(InkIcon.More, "${layer.name} actions", { menu = true }, buttonSize = 36.dp)
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = layer.id; layerName = layer.name })
                        DropdownMenuItem(text = { Text("Move up") }, enabled = page.layers.indexOf(layer) < page.layers.lastIndex, onClick = { menu = false; moveLayer(layer, 1) })
                        DropdownMenuItem(text = { Text("Move down") }, enabled = page.layers.indexOf(layer) > 0, onClick = { menu = false; moveLayer(layer, -1) })
                        DropdownMenuItem(text = { Text("Delete layer") }, enabled = page.layers.size > 1, onClick = { menu = false; deleting = layer })
                    }
                }
            }
            if (renaming == layer.id) {
                OutlinedTextField(layerName, { layerName = it }, label = { Text("Layer name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { renaming = null }) { Text("Cancel") }
                    TextButton(enabled = layerName.isNotBlank(), onClick = { changeLayer(layer.copy(name = layerName.trim())); renaming = null }) { Text("Rename") }
                }
            }
        }
    }
    deleting?.let { layer ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${layer.name}?") },
            text = { Text("The handwriting, text and images on this layer will be deleted.") },
            confirmButton = { TextButton(onClick = {
                if (page.layers.size > 1) {
                    val remaining = page.layers.filterNot { it.id == layer.id }
                    onChange(page.copy(layers = remaining, strokes = page.strokes.filterNot { it.layerId == layer.id }, texts = page.texts.filterNot { it.layerId == layer.id }, images = page.images.filterNot { it.layerId == layer.id }))
                    if (activeLayerId == layer.id) onActiveLayer(remaining.last().id)
                }
                deleting = null
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}
