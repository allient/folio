package com.allient.folio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.*
import androidx.compose.ui.geometry.Size
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

@Composable
internal fun ReaderGestureToggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.testTag("reader-gesture-${stableControlSlug(label)}").semantics { contentDescription = label })
    }
    HorizontalDivider()
}


@Composable
internal fun ReaderStatusLine(position: String, chapter: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableIntStateOf(-1) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now = System.currentTimeMillis()
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    battery = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 7.dp).testTag("reader-status-line"), verticalAlignment = Alignment.CenterVertically) {
        Text(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)), fontSize = 11.sp)
        if (battery >= 0) {
            Canvas(Modifier.padding(start = 10.dp, end = 4.dp).size(20.dp, 10.dp)) {
                drawRect(ink, size = Size(size.width * .88f, size.height), style = Stroke(1.dp.toPx()))
                drawRect(ink, topLeft = Offset(size.width * .9f, size.height * .3f), size = Size(size.width * .1f, size.height * .4f))
                drawRect(ink, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size((size.width * .88f - 4.dp.toPx()) * battery / 100f, size.height - 4.dp.toPx()))
            }
            Text("$battery%", Modifier.semantics { contentDescription = "Battery $battery percent" }, fontSize = 11.sp)
        }
        Text(chapter, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(position, fontSize = 11.sp)
    }
}

/** Compact endpoint symbols and a numeric slider, stacked on phone widths. */
@Composable
internal fun ReaderContrastRow(label: String, value: Int, maximum: Int, kind: String, onChange: (Int) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val endpoint: @Composable (Boolean) -> Unit = { strong ->
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (kind == "bold") Text("B", fontSize = 26.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Light)
            else Canvas(Modifier.size(24.dp)) {
                if (kind == "sharpen") {
                    val triangle = Path().apply { moveTo(size.width / 2, 1f); lineTo(size.width - 1, size.height - 2); lineTo(1f, size.height - 2); close() }
                    drawPath(triangle, ink, style = Stroke(1.dp.toPx()))
                    if (strong) drawPath(Path().apply { moveTo(size.width / 2, 1f); lineTo(size.width - 1, size.height - 2); lineTo(size.width / 2, size.height - 2); close() }, ink)
                    else repeat(3) { n -> val y = size.height * (.45f + n * .16f); drawLine(ink, Offset(size.width * .4f, y), Offset(size.width * .65f, y), 1.dp.toPx()) }
                } else {
                    drawCircle(ink, radius = size.minDimension * .43f, style = Stroke(1.dp.toPx()))
                    if (kind == "dark" && strong) drawArc(ink, -90f, 180f, true, topLeft = Offset(size.width * .07f, size.height * .07f), size = Size(size.width * .86f, size.height * .86f))
                    if (kind == "bleach" && !strong) repeat(3) { n -> drawLine(ink, Offset(size.width * .25f, size.height * (.3f + n * .2f)), Offset(size.width * .75f, size.height * (.3f + n * .2f)), 1.dp.toPx()) }
                }
            }
        }
    }
    val controls: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            endpoint(false)
            Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = 0f..maximum.toFloat(), steps = maximum - 1,
                modifier = Modifier.weight(1f).testTag("reader-contrast-${stableControlSlug(label)}").semantics { contentDescription = label })
            endpoint(true)
            Text("$value", Modifier.width(32.dp), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        if (maxWidth < 440.dp) Column {
            Text(label, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            controls()
        } else Row(Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(.3f), fontSize = 14.sp)
            Box(Modifier.weight(.7f)) { controls() }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
}

/** Deliberately quiet, high-contrast chrome, matching the paper-first reader. Compact: 50 dp tall with a 22 dp icon over a short label. */
@Composable private fun ReaderActionLayout(
    label: String, modifier: Modifier, active: Boolean, enabled: Boolean, onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier.testTag("reader-${stableControlSlug(label)}").heightIn(min = 50.dp).clickable(enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = label; selected = active },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.padding(bottom = 1.dp).size(width = 38.dp, height = 28.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) ink else Color.Transparent), contentAlignment = Alignment.Center) {
            icon(if (!enabled) ink.copy(alpha = .3f) else if (active) MaterialTheme.colorScheme.surface else ink)
        }
        Text(label, fontSize = 10.5.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = ink.copy(alpha = if (enabled) 1f else .3f))
    }
}

@Composable internal fun ReaderAction(
    icon: InkIcon, label: String, modifier: Modifier = Modifier,
    active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit,
) = ReaderActionLayout(label, modifier, active, enabled, onClick) { tint -> InkSymbol(icon, Modifier.size(22.dp), tint) }

/** Same bar button with a Lucide icon, for reader controls that have no custom Folio glyph. */
@Composable internal fun ReaderAction(
    icon: ImageVector, label: String, modifier: Modifier = Modifier,
    active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit,
) = ReaderActionLayout(label, modifier, active, enabled, onClick) { tint -> LucideGlyph(icon, Modifier.size(22.dp), tint) }

@Composable internal fun ReaderTabBar(tab: String, onTab: (String) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf("Contents" to Lucide.TableOfContents, "Progress" to Lucide.Gauge, "Format" to Lucide.ALargeSmall,
            "Contrast" to Lucide.Contrast, "Navigation" to Lucide.Compass, "Split View" to Lucide.Columns2).forEach { (name, icon) ->
            Box(Modifier.weight(1f)) {
                if (tab == name) Box(Modifier.align(Alignment.TopCenter).width(28.dp).height(2.dp).background(MaterialTheme.colorScheme.onSurface))
                ReaderAction(icon, name, Modifier.fillMaxWidth().padding(vertical = 2.dp), active = tab == name) { onTab(name) }
            }
        }
    }
}

@Composable internal fun ReaderSettingRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.widthIn(min = 100.dp).weight(.35f), fontSize = 14.sp)
        InkToolButton(InkIcon.ZoomOut, "Decrease $label", { onChange((value - 1f).coerceAtLeast(range.start)) }, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(.65f))
        InkToolButton(InkIcon.ZoomIn, "Increase $label", { onChange((value + 1f).coerceAtMost(range.endInclusive)) }, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
        Text("${value.toInt()}", Modifier.width(30.dp), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
}

/** The pen settings hint in the reader, where tapping the selected pen returns to reading instead. */
internal const val READER_PEN_EDIT_HINT = "Long-press a pen to edit it."

/** One reader pen button: the bar [label] ("Pen", "Highlight", …) and the shared notebook preset it draws with. */
internal data class ReaderBrushSlot(val label: String, val brush: BrushStyle)

/** Brush types that annotate a book, in slot order; each slot takes the first preset of its types, in this priority. */
private val readerBrushTypes = listOf(
    "Pen" to listOf("Pen", "Fineliner", "Gel pen", "Fountain pen", "Brush pen"),
    "Highlight" to listOf("Highlighter", "Marker"),
    "Pencil" to listOf("Pencil"),
)

/**
 * The few pens the reader offers, picked by type from the shared notebook [presets] so the user's colours and
 * widths carry over. The [selected] brush fills its own slot; when it fits no shown slot (another type, or the
 * Pencil on a phone) it is appended so the pen in use is always visible. Without a preset of a slot's type the
 * catalog default is used. [pencil] adds the Pencil slot (tablets).
 */
internal fun readerBrushes(presets: List<BrushStyle>, selected: BrushStyle, pencil: Boolean): List<ReaderBrushSlot> {
    val kinds = if (pencil) readerBrushTypes else readerBrushTypes.filter { it.first != "Pencil" }
    val slots = kinds.map { (label, types) ->
        val brush = selected.takeIf { !it.eraser && it.name in types }
            ?: types.firstNotNullOfOrNull { type -> presets.firstOrNull { it.name == type && !it.eraser } }
            ?: brushes.first { it.name == types.first() }
        ReaderBrushSlot(label, brush)
    }
    val extra = selected.takeIf { pen -> !pen.eraser && slots.none { it.brush.presetId == pen.presetId } }
    return slots + listOfNotNull(extra?.let { ReaderBrushSlot(it.name, it) })
}

/** [ReaderAction]'s compact layout for drawing tools: the selected tool gets a sage pill and a sage label. */
@Composable private fun ReaderToolAction(
    label: String, tag: String, description: String, modifier: Modifier, active: Boolean,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null, icon: @Composable (tint: Color) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier.testTag(tag).heightIn(min = 50.dp).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
        .semantics(mergeDescendants = true) { contentDescription = description; selected = active },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.padding(bottom = 1.dp).size(width = 38.dp, height = 28.dp).clip(RoundedCornerShape(14.dp))
            .background(if (active) FolioBrand.Sage.copy(alpha = .16f) else Color.Transparent)
            .then(if (active) Modifier.border(1.5.dp, FolioBrand.Sage, RoundedCornerShape(14.dp)) else Modifier),
            contentAlignment = Alignment.Center) { icon(if (active) FolioBrand.Sage else ink) }
        Text(label, fontSize = 10.5.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (active) FolioBrand.Sage else ink, fontWeight = if (active) FontWeight.SemiBold else null)
    }
}

/**
 * The reader pens in the top bar. Tap picks a pen and starts drawing; tapping the pen
 * in use again ([onStopDrawing]) returns to reading. Long-press picks it and opens its settings (width, colour,
 * type, add or delete a preset) from the shared [brushes].
 */
@Composable internal fun ReaderBrushButtons(
    slots: List<ReaderBrushSlot>, brushes: BrushPresetsState, drawing: Boolean,
    onBrush: (BrushStyle) -> Unit, onBrushChange: (BrushStyle) -> Unit, onStopDrawing: () -> Unit,
) {
    var settingsFor by remember { mutableStateOf<String?>(null) }
    slots.forEach { slot ->
        val active = drawing && slot.brush.presetId == brushes.selected.presetId
        Box {
            ReaderToolAction(slot.label, "reader-brush-${stableControlSlug(slot.label)}",
                "${slot.label}, ${slot.brush.label}. Long press for pen settings", Modifier.width(if (slot.label.length > 6) 52.dp else 48.dp), active,
                onClick = { if (active) onStopDrawing() else onBrush(slot.brush) },
                onLongClick = { onBrush(slot.brush); settingsFor = slot.brush.presetId }) {
                BrushGlyph(slot.brush.name, slot.brush.color, Modifier.size(width = 19.dp, height = 22.dp))
            }
            DropdownMenu(settingsFor == slot.brush.presetId, onDismissRequest = { settingsFor = null },
                modifier = Modifier.penPressureFilter().width(360.dp).heightIn(max = 900.dp)) {
                BrushSettings(brushes.selected, brushes.options, onBrushChange,
                    onAdd = { val added = brushes.add(brushes.selected); onBrush(added); settingsFor = added.presetId },
                    onDelete = { if (brushes.delete()) { onBrush(brushes.selected); settingsFor = null } }, editHint = READER_PEN_EDIT_HINT)
            }
        }
    }
}

/** The eraser in the same compact style as the reader pens; tapping it while erasing returns to reading. */
@Composable internal fun ReaderEraserAction(active: Boolean, onClick: () -> Unit) =
    ReaderToolAction("Eraser", "reader-eraser", "Eraser", Modifier.width(48.dp), active, onClick) { tint ->
        InkSymbol(InkIcon.Eraser, Modifier.size(22.dp), tint)
    }

@Composable internal fun ReaderFormatRow(label: String, content: @Composable RowScope.() -> Unit) {
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (compact) Text(label, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!compact) Text(label, Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly, content = content)
        }
        HorizontalDivider()
    }
}
