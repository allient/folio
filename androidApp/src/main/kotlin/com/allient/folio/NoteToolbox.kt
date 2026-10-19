package com.allient.folio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val toolboxTools = linkedMapOf(
    "Undo" to InkIcon.Undo, "Redo" to InkIcon.Redo, "Eraser" to InkIcon.Eraser,
    "Shapes" to InkIcon.Shapes, "Lasso" to InkIcon.Lasso, "Layers" to InkIcon.Layers,
    "Templates" to InkIcon.Template, "Pressure" to InkIcon.Pressure, "Zoom" to InkIcon.Zoom,
    "Text" to InkIcon.Text, "Insert" to InkIcon.Insert, "Page +" to InkIcon.AddPage,
    "Pages" to InkIcon.Template, "Share" to InkIcon.Share, "Gestures" to InkIcon.Hand,
    "Table" to InkIcon.Table, "Stickers" to InkIcon.Sticker, "Sticky" to InkIcon.Sticky, "Ruler" to InkIcon.Ruler,
)
private val defaultTools = listOf("Undo", "Redo", "Eraser")

/** The floating toolbar's row (horizontal) or column (vertical); both scroll when the screen is too small. */
@Composable
private fun ToolboxBar(vertical: Boolean, content: @Composable () -> Unit) {
    if (vertical) Column(Modifier.padding(horizontal = 2.dp, vertical = 4.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    else Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { content() }
}

/** Canvas input is never captured by the toolbar's drag handle. Positions stay within the viewport. */
@Composable
internal fun NoteToolbox(
    brushes: List<BrushStyle>, selected: BrushStyle, onBrush: (BrushStyle) -> Unit,
    onExit: () -> Unit, onAction: (String) -> Unit, canUndo: Boolean, canRedo: Boolean,
    onRestoreBrushes: (List<BrushStyle>) -> Unit,
    brushSettings: @Composable () -> Unit,
    widthMillimetres: (BrushStyle) -> Float,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("note-toolbox", Context.MODE_PRIVATE) }
    var scenario by remember { mutableStateOf(prefs.getString("scenario", "Default") ?: "Default") }
    var names by remember { mutableStateOf((listOf("Default", "Meeting", "Sketching", "Journaling") + prefs.getStringSet("names", emptySet()).orEmpty().sorted()).distinct()) }
    fun toolsFor(name: String): List<String> = prefs.getString("tools-$name", null)?.split('|')?.filter { it in toolboxTools }
        ?: when (name) { "Meeting" -> listOf("Undo", "Redo", "Lasso", "Text", "Pages"); "Sketching" -> listOf("Undo", "Redo", "Eraser", "Layers", "Shapes"); "Journaling" -> listOf("Undo", "Eraser", "Templates", "Page +"); else -> defaultTools }
    var tools by remember { mutableStateOf(toolsFor(scenario)) }
    var brushDragRemainder by remember { mutableFloatStateOf(0f) }
    var brushReordering by remember { mutableStateOf(false) }
    var brushNames by remember { mutableStateOf(prefs.getString("brushes-$scenario", null)?.split('|')?.filter { name -> brushes.any { it.presetId == name } } ?: brushes.map { it.presetId }) }
    var knownBrushIds by remember { mutableStateOf(brushes.map { it.presetId }.toSet()) }
    LaunchedEffect(brushes.map { it.presetId }) {
        val current = brushes.map { it.presetId }.toSet()
        brushNames = (brushNames.filter { it in current } + (current - knownBrushIds)).distinct()
        knownBrushIds = current
    }
    fun saveBrushes(name: String) {
        prefs.edit().apply { brushes.forEach { putFloat("$name-${it.presetId}-width", it.width); putInt("$name-${it.presetId}-color", it.color); putFloat("$name-${it.presetId}-sensitivity", it.sensitivity) } }.apply()
    }
    fun restoreBrushes(name: String) = brushes.map { brush -> brush.copy(
        width = prefs.getFloat("$name-${brush.presetId}-width", brush.width).coerceIn(BrushCatalog.widthRange(brush.name)),
        color = prefs.getInt("$name-${brush.presetId}-color", brush.color),
        sensitivity = prefs.getFloat("$name-${brush.presetId}-sensitivity", brush.sensitivity).coerceIn(0f, 1f)) }
    LaunchedEffect(Unit) { onRestoreBrushes(restoreBrushes(scenario)) }
    LaunchedEffect(brushes) { saveBrushes(scenario) }
    var panel by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var scale by remember { mutableFloatStateOf(prefs.getFloat("scale", 1f).coerceIn(.8f, 1.2f)) }
    fun persist() { prefs.edit().putString("tools-$scenario", tools.joinToString("|")).putString("brushes-$scenario", brushNames.joinToString("|")).putString("scenario", scenario).putFloat("scale", scale).apply() }
    var position by remember { mutableStateOf(Offset(prefs.getFloat("x", 12f), prefs.getFloat("y", 12f))) }
    var vertical by remember { mutableStateOf(prefs.getBoolean("vertical", false)) }
    var barWidth by remember { mutableIntStateOf(0) }
    var barHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val limitX = (with(density) { maxWidth.toPx() } - barWidth).coerceAtLeast(0f)
        val limitY = (with(density) { maxHeight.toPx() } - barHeight).coerceAtLeast(0f)
        Surface(Modifier.offset { IntOffset(position.x.coerceIn(0f, limitX).roundToInt(), position.y.coerceIn(0f, limitY).roundToInt()) }
            .then(if (vertical) Modifier.heightIn(max = maxHeight) else Modifier.widthIn(max = maxWidth)).onSizeChanged { barWidth = it.width; barHeight = it.height },
            shape = RoundedCornerShape(if (vertical) 24.dp else 28.dp), color = Color(0xFFFAFAFA), shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, ChromeLine)) {
            ToolboxBar(vertical) {
                Box(Modifier.then(if (vertical) Modifier.width(44.dp).height(24.dp) else Modifier.width(24.dp).height(44.dp)).semantics { contentDescription = "Drag floating toolbar" }
                    .pointerInput(limitX, limitY) { detectDragGestures(onDragEnd = { prefs.edit().putFloat("x", position.x).putFloat("y", position.y).apply() }) { change, delta ->
                        change.consume(); position = Offset((position.x + delta.x).coerceIn(0f, limitX), (position.y + delta.y).coerceIn(0f, limitY))
                    } }, contentAlignment = Alignment.Center) { Text("⠿", color = ChromeMuted) }
                InkToolButton(InkIcon.Collapse, "Exit full screen", onExit)
                if (brushReordering) Text("Reordering brushes", Modifier.padding(horizontal = 4.dp), fontSize = 9.sp, color = Color(0xFF287463))
                // At most five brushes are visible; extra presets scroll inside this strip only.
                val shown = brushes.filter { it.presetId in brushNames }.distinctBy { it.presetId }
                val brushListState = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(selected.presetId) {
                    val index = shown.indexOfFirst { it.presetId == selected.presetId }
                    val visible = brushListState.layoutInfo.visibleItemsInfo
                    if (index >= 0 && visible.isNotEmpty() && (index <= visible.first().index || index >= visible.last().index))
                        brushListState.animateScrollToItem((index - 2).coerceAtLeast(0))
                }
                val brushItem: @Composable (BrushStyle) -> Unit = { brush ->
                    BrushPreset(brush.label, widthMillimetres(brush), brush.color, !selected.eraser && selected.presetId == brush.presetId, scale = scale, verticalDrag = vertical, onClick = {
                        if (!selected.eraser && selected.presetId == brush.presetId) panel = "brush" else onBrush(brush)
                    }, onReorderStart = { brushReordering = true }, onReorderDrag = { draggedId, delta ->
                        brushDragRemainder += delta
                        val step = with(density) { (if (vertical) 36f else 32f * scale + 4f).dp.toPx() }
                        fun ordered() = brushNames.map { n -> brushes.first { it.presetId == n } }
                        while (brushDragRemainder >= step && brushNames.indexOf(draggedId) < brushNames.lastIndex) {
                            brushNames = reorderByDrag(ordered(), draggedId, 1, BrushStyle::presetId).map { it.presetId }; brushDragRemainder -= step
                        }
                        while (brushDragRemainder <= -step && brushNames.indexOf(draggedId) > 0) {
                            brushNames = reorderByDrag(ordered(), draggedId, -1, BrushStyle::presetId).map { it.presetId }; brushDragRemainder += step
                        }
                    }, onReorderEnd = { brushDragRemainder = 0f; brushReordering = false; persist() })
                }
                if (vertical) androidx.compose.foundation.lazy.LazyColumn(Modifier.width((32f * scale).dp).height((36f * shown.size.coerceIn(1, 5) - 4f).dp),
                    state = brushListState, userScrollEnabled = shown.size > 5, verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) { items(shown, key = { it.presetId }) { brushItem(it) } }
                else androidx.compose.foundation.lazy.LazyRow(Modifier.width(((32f * scale + 4f) * shown.size.coerceIn(1, 5) - 4f).dp).height(36.dp)
                    .horizontalFadeEdges(brushListState, shown.size > 5),
                    state = brushListState, userScrollEnabled = shown.size > 5, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically) { items(shown, key = { it.presetId }) { brushItem(it) } }
                if (vertical) HorizontalDivider(Modifier.padding(vertical = 3.dp).width(24.dp), color = ChromeLine) else ChromeDivider()
                tools.forEach { tool -> InkToolButton(toolboxTools.getValue(tool), tool, { onAction(tool) },
                    enabled = when(tool) { "Undo" -> canUndo; "Redo" -> canRedo; else -> true }) }
                Box {
                    InkToolButton(InkIcon.More, "More toolbar options", { panel = "more" })
                    DropdownMenu(panel == "more", { panel = null }) {
                        DropdownMenuItem(text = { Text("Brush options") }, onClick = { panel = "brush" })
                        DropdownMenuItem(text = { Text("Customize Toolbar") }, onClick = { panel = "customize" })
                        DropdownMenuItem(text = { Text(if (vertical) "Horizontal toolbar" else "Vertical toolbar") }, onClick = {
                            vertical = !vertical; prefs.edit().putBoolean("vertical", vertical).apply(); panel = null
                        })
                        DropdownMenuItem(text = { Text("Reset toolbar position") }, onClick = { position = Offset(12f, 12f); prefs.edit().putFloat("x", 12f).putFloat("y", 12f).apply(); panel = null })
                    }
                }
                Box {
                    InkToolButton(InkIcon.Tools, "ToolBox: $scenario", { panel = "toolbox" })
                    DropdownMenu(panel == "toolbox", { panel = null }) {
                        names.forEach { name -> DropdownMenuItem(text = { Text(if (name == scenario) "✓ $name" else name) }, onClick = {
                            saveBrushes(scenario); val restored = restoreBrushes(name)
                            scenario = name; tools = toolsFor(name)
                            brushNames = prefs.getString("brushes-$name", null)?.split('|')?.filter { n -> brushes.any { it.presetId == n } } ?: brushes.map { it.presetId }
                            persist(); onRestoreBrushes(restored); panel = null
                        }) }
                        DropdownMenuItem(text = { Text("Save as new toolbox…") }, onClick = { newName = ""; panel = "new" })
                    }
                }
            }
        }
    }
    if (panel == "brush") ToolDialog("Brush options", { panel = null }, expanded = true) { brushSettings() }
    if (panel == "new") AlertDialog(onDismissRequest = { panel = null }, title = { Text("Save toolbox") }, text = {
        OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true)
    }, confirmButton = { TextButton(enabled = newName.trim().isNotEmpty() && newName.trim() !in names, onClick = {
        scenario = newName.trim(); saveBrushes(scenario); names = names + scenario; prefs.edit().putStringSet("names", names.toSet()).apply(); persist(); panel = null
    }) { Text("Save") } }, dismissButton = { TextButton(onClick = { panel = null }) { Text("Cancel") } })
    if (panel == "customize") AlertDialog(onDismissRequest = { panel = null }, title = { Text("Customize Toolbar · $scenario") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScrollCompat()) {
            Text("Size", fontSize = 13.sp)
            Slider(scale, { scale = it; persist() }, valueRange = .8f..1.2f)
            Text("Brushes", fontSize = 14.sp)
            brushes.forEach { brush -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(brush.presetId in brushNames, { checked -> brushNames = if (checked) brushNames + brush.presetId else brushNames - brush.presetId; persist() })
                BrushGlyph(brush.name, brush.color, Modifier.size(22.dp, 26.dp)); Text(brush.label, Modifier.padding(start = 8.dp), fontSize = 13.sp)
            } }
            Text("Tools · use arrows to reorder", fontSize = 14.sp)
            (tools + toolboxTools.keys.filterNot { it in tools }).forEach { tool -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(tool in tools, { checked -> tools = if (checked) tools + tool else tools - tool; persist() })
                Text(tool, Modifier.weight(1f), fontSize = 13.sp)
                if (tool in tools) {
                    TextButton(enabled = tools.indexOf(tool) > 0, onClick = { val list = tools.toMutableList(); val i = list.indexOf(tool); list.add(i - 1, list.removeAt(i)); tools = list; persist() }) { Text("↑") }
                    TextButton(enabled = tools.indexOf(tool) < tools.lastIndex, onClick = { val list = tools.toMutableList(); val i = list.indexOf(tool); list.add(i + 1, list.removeAt(i)); tools = list; persist() }) { Text("↓") }
                }
            } }
            Text("Exit full screen, More and ToolBox always remain available.", fontSize = 11.sp, color = ChromeMuted)
        }
    }, confirmButton = { TextButton(onClick = { panel = null }) { Text("Done") } }, dismissButton = { TextButton(onClick = { tools = defaultTools; brushNames = brushes.map { it.presetId }; scale = 1f; persist() }) { Text("Reset") } })
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(rememberScrollState())
