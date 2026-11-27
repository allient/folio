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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
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

/** Tool ids are saved in preferences and sent to the editor; only their labels are translated. */
@StringRes private fun toolLabel(tool: String): Int = when (tool) {
    "Undo" -> R.string.tools_tool_undo; "Redo" -> R.string.tools_tool_redo; "Eraser" -> R.string.tools_tool_eraser
    "Shapes" -> R.string.tools_tool_shapes; "Lasso" -> R.string.tools_tool_lasso; "Layers" -> R.string.tools_tool_layers
    "Templates" -> R.string.tools_tool_templates; "Pressure" -> R.string.tools_tool_pressure; "Zoom" -> R.string.tools_tool_zoom
    "Text" -> R.string.tools_tool_text; "Insert" -> R.string.tools_tool_insert; "Page +" -> R.string.tools_tool_add_page
    "Pages" -> R.string.tools_tool_pages; "Share" -> R.string.tools_tool_share; "Gestures" -> R.string.tools_tool_gestures
    "Table" -> R.string.tools_tool_table; "Stickers" -> R.string.tools_tool_stickers; "Sticky" -> R.string.tools_tool_sticky
    else -> R.string.tools_tool_ruler
}

/** Built-in toolboxes keep their English names in preferences; names the user typed are shown as saved. */
private val builtInToolboxes = mapOf("Default" to R.string.tools_toolbox_default, "Meeting" to R.string.tools_toolbox_meeting,
    "Sketching" to R.string.tools_toolbox_sketching, "Journaling" to R.string.tools_toolbox_journaling)

@Composable
private fun toolboxName(name: String): String = builtInToolboxes[name]?.let { stringResource(it) } ?: name

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
                val dragDescription = stringResource(R.string.tools_drag_toolbar)
                Box(Modifier.then(if (vertical) Modifier.width(44.dp).height(24.dp) else Modifier.width(24.dp).height(44.dp)).semantics { contentDescription = dragDescription }
                    .pointerInput(limitX, limitY) { detectDragGestures(onDragEnd = { prefs.edit().putFloat("x", position.x).putFloat("y", position.y).apply() }) { change, delta ->
                        change.consume(); position = Offset((position.x + delta.x).coerceIn(0f, limitX), (position.y + delta.y).coerceIn(0f, limitY))
                    } }, contentAlignment = Alignment.Center) { Text("⠿", color = ChromeMuted) }
                InkToolButton(InkIcon.Collapse, stringResource(R.string.tools_exit_full_screen), onExit, tag = "Exit full screen")
                if (brushReordering) Text(stringResource(R.string.tools_reordering_brushes),Modifier.padding(horizontal = 4.dp), fontSize = 9.sp, color = Color(0xFF287463))
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
                    BrushPreset(brushLabel(brush), widthMillimetres(brush), brush.color, !selected.eraser && selected.presetId == brush.presetId, scale = scale,
                        id = brush.label, glyph = brush.label, verticalDrag = vertical, onClick = {
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
                tools.forEach { tool -> InkToolButton(toolboxTools.getValue(tool), stringResource(toolLabel(tool)), { onAction(tool) },
                    enabled = when(tool) { "Undo" -> canUndo; "Redo" -> canRedo; else -> true }, tag = tool) }
                Box {
                    InkToolButton(InkIcon.More, stringResource(R.string.tools_more_toolbar_options), { panel = "more" }, tag = "More toolbar options")
                    DropdownMenu(panel == "more", { panel = null }, modifier = Modifier.penPressureFilter()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.tools_brush_options)) }, onClick = { panel = "brush" })
                        DropdownMenuItem(text = { Text(stringResource(R.string.tools_customize_toolbar)) }, onClick = { panel = "customize" })
                        DropdownMenuItem(text = { Text(stringResource(if (vertical) R.string.tools_horizontal_toolbar else R.string.tools_vertical_toolbar)) }, onClick = {
                            vertical = !vertical; prefs.edit().putBoolean("vertical", vertical).apply(); panel = null
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.tools_reset_toolbar_position)) }, onClick = { position = Offset(12f, 12f); prefs.edit().putFloat("x", 12f).putFloat("y", 12f).apply(); panel = null })
                    }
                }
                Box {
                    InkToolButton(InkIcon.Tools, stringResource(R.string.tools_toolbox_named, toolboxName(scenario)), { panel = "toolbox" }, tag = "ToolBox: $scenario")
                    DropdownMenu(panel == "toolbox", { panel = null }, modifier = Modifier.penPressureFilter()) {
                        names.forEach { name -> DropdownMenuItem(text = { Text(if (name == scenario) "✓ ${toolboxName(name)}" else toolboxName(name)) }, onClick = {
                            saveBrushes(scenario); val restored = restoreBrushes(name)
                            scenario = name; tools = toolsFor(name)
                            brushNames = prefs.getString("brushes-$name", null)?.split('|')?.filter { n -> brushes.any { it.presetId == n } } ?: brushes.map { it.presetId }
                            persist(); onRestoreBrushes(restored); panel = null
                        }) }
                        DropdownMenuItem(text = { Text(stringResource(R.string.tools_save_new_toolbox)) }, onClick = { newName = ""; panel = "new" })
                    }
                }
            }
        }
    }
    if (panel == "brush") ToolDialog(stringResource(R.string.tools_brush_options), { panel = null }, expanded = true, tag = "Brush options") { brushSettings() }
    if (panel == "new") AlertDialog(onDismissRequest = { panel = null }, title = { Text(stringResource(R.string.tools_save_toolbox)) }, text = {
        OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.tools_name)) }, singleLine = true)
    }, confirmButton = { TextButton(enabled = newName.trim().isNotEmpty() && newName.trim() !in names, onClick = {
        scenario = newName.trim(); saveBrushes(scenario); names = names + scenario; prefs.edit().putStringSet("names", names.toSet()).apply(); persist(); panel = null
    }) { Text(stringResource(R.string.tools_save)) } }, dismissButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.tools_cancel)) } })
    if (panel == "customize") AlertDialog(onDismissRequest = { panel = null }, title = { Text(stringResource(R.string.tools_customize_toolbar_named, toolboxName(scenario))) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScrollCompat()) {
            Text(stringResource(R.string.tools_size), fontSize = 13.sp)
            Slider(scale, { scale = it; persist() }, valueRange = .8f..1.2f)
            Text(stringResource(R.string.tools_brushes), fontSize = 14.sp)
            brushes.forEach { brush -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(brush.presetId in brushNames, { checked -> brushNames = if (checked) brushNames + brush.presetId else brushNames - brush.presetId; persist() })
                BrushGlyph(brush.name, brush.color, Modifier.size(22.dp, 26.dp)); Text(brushLabel(brush), Modifier.padding(start = 8.dp), fontSize = 13.sp)
            } }
            Text(stringResource(R.string.tools_tools_reorder), fontSize = 14.sp)
            (tools + toolboxTools.keys.filterNot { it in tools }).forEach { tool -> Row(verticalAlignment = Alignment.CenterVertically) {
                val label = stringResource(toolLabel(tool))
                Checkbox(tool in tools, { checked -> tools = if (checked) tools + tool else tools - tool; persist() })
                Text(label, Modifier.weight(1f), fontSize = 13.sp)
                if (tool in tools) {
                    val upDescription = stringResource(R.string.tools_move_tool_up, label)
                    val downDescription = stringResource(R.string.tools_move_tool_down, label)
                    TextButton(enabled = tools.indexOf(tool) > 0, onClick = { val list = tools.toMutableList(); val i = list.indexOf(tool); list.add(i - 1, list.removeAt(i)); tools = list; persist() },
                        modifier = Modifier.semantics { contentDescription = upDescription }) { Text("↑") }
                    TextButton(enabled = tools.indexOf(tool) < tools.lastIndex, onClick = { val list = tools.toMutableList(); val i = list.indexOf(tool); list.add(i + 1, list.removeAt(i)); tools = list; persist() },
                        modifier = Modifier.semantics { contentDescription = downDescription }) { Text("↓") }
                }
            } }
            Text(stringResource(R.string.tools_always_available), fontSize = 11.sp, color = ChromeMuted)
        }
    }, confirmButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.tools_done)) } }, dismissButton = { TextButton(onClick = { tools = defaultTools; brushNames = brushes.map { it.presetId }; scale = 1f; persist() }) { Text(stringResource(R.string.tools_reset)) } })
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(rememberScrollState())
