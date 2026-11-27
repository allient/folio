package com.allient.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/** Brush preset editor (type, variant, size, opacity, pressure, texture, colour); [onPickFromPage] null hides page picking. */
@Composable
internal fun BrushSettings(brush: BrushStyle, presets: List<BrushStyle>, onChange: (BrushStyle) -> Unit,
    onAdd: () -> Unit, onDelete: () -> Unit, onPickFromPage: (((Int) -> Unit) -> Unit)? = null,
    editHint: String = stringResource(R.string.brush_edit_hint_default)) {
    val widthPx = brush.width
    val label = brushLabel(brush)
    // "Paint brush" is the stored type id; only the text shown is translated.
    val paint = brush.name == "Paint brush"
    var advanced by remember { mutableStateOf(false) }
    var showColorPicker by remember(brush.presetId) { mutableStateOf(false) }
    if (showColorPicker) BrushColorPicker(brush.color, { showColorPicker = false }, { color ->
        onChange(brush.copy(color = color)); showColorPicker = false
    }, onPickFromPage?.let { pick -> { showColorPicker = false; pick { onChange(brush.copy(color = (brush.color and 0xFF000000.toInt()) or (it and 0xFFFFFF))) } } })
    val palette = listOf(0xFF252525, 0xFF555555, 0xFF888888, 0xFFBBBBBB, 0xFFFFFFFF, 0xFFB33E3E, 0xFF446C50, 0xFF345C91,
        0xFF7BB9B2, 0xFF9966A1, 0xFFB9916B, 0xFFEAC243, 0xFF344F41, 0xFF68537E, 0xFF76A9C0, 0xFFC48787)
    Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.brush_title_width, label, stringResource(R.string.brush_px, widthPx)), fontSize = 12.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            InkToolButton(InkIcon.Rotate, stringResource(R.string.brush_reset), { onChange(BrushCatalog.reset(brush)) }, tag = "Reset brush to preset defaults")
            InkToolButton(InkIcon.Add, stringResource(R.string.brush_add), onAdd, tag = "Add brush preset")
            InkToolButton(InkIcon.Trash, stringResource(R.string.brush_delete), onDelete, enabled = presets.size > 1, tag = "Delete brush preset")
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            brushes.filterNot { it.eraser }.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { type ->
                        val chosen = brush.name == type.name
                        val typeName = brushTypeLabel(type.name)
                        val typeDescription = stringResource(R.string.brush_type_description, typeName)
                        Column(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (chosen) Color(0xFFE0F1EB) else Color(0xFFF6F7F6))
                            .border(if (chosen) 2.dp else 1.dp, if (chosen) Color(0xFF287463) else ChromeLine, RoundedCornerShape(6.dp))
                            .selectable(selected = chosen, role = Role.RadioButton,
                                onClick = { onChange(BrushCatalog.default(type.name).copy(presetId = brush.presetId)) })
                            .semantics { contentDescription = typeDescription }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                             BrushGlyph(type.name, brush.color, Modifier.size(16.dp, 20.dp))
                             Text(typeName,fontSize = 8.sp, maxLines = 2, lineHeight = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BrushCatalog.forType(brush.name).forEach { preset ->
                val presetLabel = brushLabel(preset)
                val presetDescription = stringResource(R.string.brush_preset_description, presetLabel)
                Surface(Modifier.width(118.dp).clickable { onChange(preset.copy(presetId = brush.presetId)) }
                    .semantics { contentDescription = presetDescription; selected = brush.variant == preset.variant },
                    shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(if (brush.variant == preset.variant) 2.dp else 1.dp, if (brush.variant == preset.variant) Color(0xFF287463) else ChromeLine)) {
                    Column(Modifier.padding(6.dp)) {
                        BrushPreview(preset)
                        Text(presetLabel, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.brush_px, preset.width),fontSize = 9.sp, lineHeight = 12.sp, color = ChromeMuted)
                    }
                }
            }
        }
        HorizontalDivider(color = ChromeLine)
        BrushPreview(brush)
        val range = BrushCatalog.widthRange(brush.name)
        val step = BrushCatalog.widthStep(brush.name)
        fun setWidth(value: Float) = onChange(brush.copy(width = ((value / step).roundToInt() * step).coerceIn(range)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tools_size), fontSize = 12.sp, modifier = Modifier.weight(1f))
            InkToolButton(InkIcon.Previous, stringResource(R.string.brush_decrease_width), { setWidth(widthPx - step) }, buttonSize = 28.dp, enabled = widthPx > range.start, tag = "Decrease brush width")
            Text(stringResource(R.string.brush_px, widthPx), fontSize = 12.sp)
            InkToolButton(InkIcon.Next, stringResource(R.string.brush_increase_width), { setWidth(widthPx + step) }, buttonSize = 28.dp, enabled = widthPx < range.endInclusive, tag = "Increase brush width")
        }
        VariableWidthSlider(widthPx.coerceIn(range), range.start, range.endInclusive, Modifier.fillMaxWidth().height(34.dp)) { setWidth(it) }
        Row { Text(stringResource(R.string.brush_opacity), fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(stringResource(R.string.tools_percent, android.graphics.Color.alpha(brush.color) * 100 / 255), fontSize = 12.sp) }
        LineSlider(android.graphics.Color.alpha(brush.color) / 255f, .1f..1f, stringResource(R.string.brush_opacity_description)) { onChange(brush.copy(color = (brush.color and 0x00FFFFFF) or ((it * 255).toInt() shl 24))) }
        if (BrushCatalog.pressureResponsive(brush.name)) {
            Row { Text(stringResource(R.string.brush_pressure_response), fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(stringResource(R.string.tools_percent, (brush.sensitivity * 100).toInt()), fontSize = 12.sp) }
            LineSlider(brush.sensitivity, 0f..1f, stringResource(R.string.brush_pressure_sensitivity, label)) { onChange(brush.copy(sensitivity = it)) }
            Text(stringResource(R.string.brush_pressure_hint), fontSize = 10.sp, color = ChromeMuted)
        } else Text(stringResource(R.string.brush_constant_width), fontSize = 10.sp, color = ChromeMuted)
        val natural = NaturalMediaRenderer.supports(brush.name)
        if (natural || BrushCatalog.angled(brush.name)) {
            Row(Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (paint) R.string.brush_bristles else if (natural) R.string.brush_texture_angle else R.string.brush_nib_angle), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(stringResource(if (advanced) R.string.brush_hide else R.string.brush_show), fontSize = 11.sp, color = Color(0xFF287463))
            }
        }
        if (advanced && (natural || BrushCatalog.angled(brush.name))) {
            if (natural) {
                val texture = (brush.texture * 100).toInt()
                Text(if (paint) stringResource(R.string.brush_dryness_value, texture) else stringResource(R.string.brush_grain_strength_value, texture), fontSize = 11.sp)
                LineSlider(brush.texture, 0f..1f, stringResource(R.string.brush_grain_strength)) { onChange(brush.copy(texture = it)) }
                Text(if (paint) stringResource(R.string.brush_coarseness_value, brush.grain) else stringResource(R.string.brush_grain_scale_value, brush.grain), fontSize = 11.sp)
                LineSlider(brush.grain, .4f..4f, stringResource(R.string.brush_grain_scale)) { onChange(brush.copy(grain = it)) }
            }
            if (BrushCatalog.angled(brush.name)) {
                val angle = brush.angle.toInt()
                Text(if (natural) stringResource(R.string.brush_texture_angle_value, angle) else stringResource(R.string.brush_nib_angle_value, angle), fontSize = 11.sp)
                LineSlider(brush.angle, -90f..90f, stringResource(R.string.brush_angle)) { onChange(brush.copy(angle = it)) }
            }
        }
        Text(stringResource(R.string.brush_sizes_note), fontSize = 10.sp, lineHeight = 12.sp, color = ChromeMuted)
        HorizontalDivider(color = ChromeLine)
        Row { Text(stringResource(R.string.tools_colors), fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(String.format(Locale.US, "#%08X", brush.color), fontSize = 11.sp, color = ChromeMuted) }
        palette.chunked(8).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            row.forEach { value ->
                val ink = (value.toInt() and 0x00FFFFFF) or (brush.color and 0xFF000000.toInt())
                val inkDescription = stringResource(R.string.brush_ink_color, String.format(Locale.US, "#%06X", value and 0xFFFFFF))
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(value))
                    .border(if (brush.color == ink) 3.dp else 1.dp, if (brush.color == ink) ChromeInk else Color(0xFFBBBBBB), CircleShape)
                    .clickable { onChange(brush.copy(color = ink)) }
                    .semantics { contentDescription = inkDescription })
            }
        } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showColorPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(brush.color or 0xFF000000.toInt())).border(1.dp, ChromeLine, CircleShape))
                Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.tools_custom_color), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onPickFromPage != null) OutlinedButton(onClick = { onPickFromPage { onChange(brush.copy(color = (brush.color and 0xFF000000.toInt()) or (it and 0xFFFFFF))) } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                InkSymbol(InkIcon.Eyedropper, Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.tools_pick_from_page), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(stringResource(R.string.brush_edit_hint, editHint), fontSize = 10.sp, color = ChromeMuted)
    }
}
