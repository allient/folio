package com.allient.folio

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.UUID

/** Brush presets in the `editor` preferences, shared by every screen that draws with notebook pens. */
internal class BrushPresetStore(private val prefs: SharedPreferences) {
    /** [options] keeps the saved (creation) order; [presets] is the brush bar order. */
    class Snapshot(val options: List<BrushStyle>, val presets: List<BrushStyle>)

    fun load(): Snapshot {
        val legacy = brushes.filterNot { it.eraser }.map { brush ->
            val width = prefs.getFloat("brush-${brush.name}-width", brush.width)
            brush.copy(width = if (width.isFinite()) width.coerceIn(.5f, 32f) else brush.width,
                color = prefs.getInt("brush-${brush.name}-color", brush.color))
        }
        val options = runCatching {
            val json = org.json.JSONArray(prefs.getString("brush-presets-v2", "[]"))
            (0 until json.length()).map { index -> val item = json.getJSONObject(index)
                val type = brushes.first { it.name == item.getString("type") && !it.eraser }
                type.copy(width = item.getDouble("width").toFloat().coerceIn(BrushCatalog.widthRange(type.name)), color = item.getInt("color"),
                    sensitivity = item.optDouble("sensitivity", .5).toFloat().coerceIn(0f, 1f), presetId = item.getString("id"), label = item.optString("label", type.name),
                    texture = item.optDouble("texture", .65).toFloat(), grain = item.optDouble("grain", 1.0).toFloat(),
                    angle = item.optDouble("angle", 0.0).toFloat(), variant = item.optString("variant", ""))
            }.distinctBy { it.presetId }.ifEmpty { legacy }
        }.getOrDefault(legacy)
        val ids = (0 until options.size).mapNotNull { prefs.getString("brush-preset-id-$it", null) }
        val ordered = (ids.mapNotNull { id -> options.firstOrNull { it.presetId == id } } + options.filter { brush -> ids.none { it == brush.presetId } }).distinctBy { it.presetId }
        return Snapshot(options, ordered)
    }

    fun save(options: List<BrushStyle>, presets: List<BrushStyle>) {
        prefs.edit().apply {
            putString("brush-presets-v2", org.json.JSONArray().apply { options.forEach { brush -> put(org.json.JSONObject().apply {
                put("id", brush.presetId); put("type", brush.name); put("label", brush.label); put("width", brush.width); put("color", brush.color); put("sensitivity", brush.sensitivity)
                put("texture", brush.texture); put("grain", brush.grain); put("angle", brush.angle); put("variant", brush.variant)
            }) } }.toString())
            options.forEach { putFloat("brush-${it.name}-width", it.width); putInt("brush-${it.name}-color", it.color) }
            presets.forEachIndexed { index, brush -> putString("brush-preset-id-$index", brush.presetId); putString("brush-preset-$index", brush.name) }
        }.apply()
    }
}

/** Compose state for the shared brush presets. Screen-only state (tool mode, last pen) stays with the screen. */
@Stable
internal class BrushPresetsState(private val store: BrushPresetStore) {
    private val initial = store.load()
    var options by mutableStateOf(initial.options); private set
    var presets by mutableStateOf(initial.presets); private set
    /** The brush in use; a screen may also put a non-preset brush (the eraser) here. */
    var selected by mutableStateOf(initial.options.first())

    fun update(brush: BrushStyle) {
        selected = brush
        options = options.map { if (it.presetId == brush.presetId) brush else it }
        presets = presets.map { if (it.presetId == brush.presetId) brush else it }
    }

    /** Adds an independent copy of [source], selects it and returns it. */
    fun add(source: BrushStyle): BrushStyle {
        val added = source.copy(presetId = UUID.randomUUID().toString(), label = "${source.name} ${options.count { it.name == source.name } + 1}")
        options = options + added; presets = presets + added; selected = added
        return added
    }

    /** Deletes the selected preset; returns false (and changes nothing) when it is the last one. */
    fun delete(): Boolean {
        if (options.size <= 1) return false
        options = options.filterNot { it.presetId == selected.presetId }; presets = presets.filterNot { it.presetId == selected.presetId }
        selected = presets.firstOrNull() ?: options.first()
        return true
    }

    /** Moves preset [id] one slot in [direction] on the bar; false when it is already at that end. */
    fun move(id: String, direction: Int): Boolean {
        val next = reorderByDrag(presets, id, direction, BrushStyle::presetId)
        if (next === presets) return false
        presets = next
        return true
    }

    fun restore(restored: List<BrushStyle>) {
        options = restored; presets = restored
        selected = restored.firstOrNull { it.presetId == selected.presetId } ?: restored.first()
    }

    fun save() = store.save(options, presets)
}

/** Loads the presets when first composed (or when [key] changes) and saves every change. */
@Composable
internal fun rememberBrushPresetsState(prefs: SharedPreferences, key: Any? = null): BrushPresetsState {
    val state = remember(prefs, key) { BrushPresetsState(BrushPresetStore(prefs)) }
    LaunchedEffect(state, state.options, state.presets) { state.save() }
    return state
}

/**
 * The top-bar brush strip: at most [maxVisible] 36dp presets (more scroll with faded edges), long-press drag
 * reorders, and tapping the selected preset again (when [reselectOpensSettings]) toggles the [settings] drop-down.
 */
@Composable
internal fun BrushPresetBar(
    presets: List<BrushStyle>, selectedId: String, highlighted: Boolean, reselectOpensSettings: Boolean, maxVisible: Int,
    settingsOpen: Boolean, onSettingsOpenChange: (Boolean) -> Unit, onSelect: (BrushStyle) -> Unit,
    onMove: (id: String, direction: Int) -> Boolean, modifier: Modifier = Modifier, settings: @Composable () -> Unit,
) {
    Box(modifier) {
        val slotPx = with(LocalDensity.current) { 36.dp.toPx() }
        val listState = rememberLazyListState()
        var dragRemainder by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(selectedId) {
            val index = presets.indexOfFirst { it.presetId == selectedId }
            val visible = listState.layoutInfo.visibleItemsInfo
            if (index >= 0 && visible.isNotEmpty() && (index <= visible.first().index || index >= visible.last().index))
                listState.animateScrollToItem((index - 2).coerceAtLeast(0))
        }
        val scrollable = presets.size > maxVisible
        LazyRow(Modifier.width((36 * presets.size.coerceIn(1, maxVisible) - 4).dp).height(36.dp).horizontalFadeEdges(listState, scrollable),
            state = listState, userScrollEnabled = scrollable, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(presets, key = { it.presetId }) { brush ->
                BrushPreset(brush.name, brush.width, brush.color, highlighted && selectedId == brush.presetId,
                    id = brush.presetId, modifier = Modifier.animateItem(),
                    onClick = { if (selectedId == brush.presetId && reselectOpensSettings) onSettingsOpenChange(!settingsOpen) else onSelect(brush) },
                    onReorderDrag = { draggedId, delta ->
                        dragRemainder += delta
                        while (dragRemainder >= slotPx && onMove(draggedId, 1)) dragRemainder -= slotPx
                        while (dragRemainder <= -slotPx && onMove(draggedId, -1)) dragRemainder += slotPx
                    }, onReorderEnd = { dragRemainder = 0f })
            }
        }
        DropdownMenu(settingsOpen, onDismissRequest = { onSettingsOpenChange(false) },
            modifier = Modifier.penPressureFilter().width(360.dp).heightIn(max = 900.dp)) { settings() }
    }
}
