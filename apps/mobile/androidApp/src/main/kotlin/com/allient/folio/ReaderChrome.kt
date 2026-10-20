package com.allient.folio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Deliberately quiet, high-contrast chrome, matching the paper-first reader. */
@Composable internal fun ReaderAction(
    icon: InkIcon, label: String, modifier: Modifier = Modifier,
    active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier.testTag("reader-${stableControlSlug(label)}").heightIn(min = 64.dp).clickable(enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = label; selected = active },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.padding(bottom = 5.dp).size(32.dp).clip(RoundedCornerShape(4.dp))
            .background(if (active) ink else Color.Transparent), contentAlignment = Alignment.Center) {
            InkSymbol(icon, Modifier.size(25.dp), if (!enabled) ink.copy(alpha = .3f) else if (active) MaterialTheme.colorScheme.surface else ink)
        }
        Text(label, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = ink.copy(alpha = if (enabled) 1f else .3f))
    }
}

@Composable internal fun ReaderTabBar(tab: String, onTab: (String) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf("Contents" to InkIcon.Pages, "Progress" to InkIcon.Redo, "Format" to InkIcon.Text,
            "Contrast" to InkIcon.Flip, "Navigation" to InkIcon.Paper, "Split View" to InkIcon.Border).forEach { (name, icon) ->
            Box(Modifier.weight(1f)) {
                if (tab == name) Box(Modifier.align(Alignment.TopCenter).width(28.dp).height(2.dp).background(MaterialTheme.colorScheme.onSurface))
                ReaderAction(icon, name, Modifier.fillMaxWidth().padding(vertical = 8.dp), active = tab == name) { onTab(name) }
            }
        }
    }
}

@Composable internal fun ReaderSettingRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.widthIn(min = 100.dp).weight(.35f), fontSize = 14.sp)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(.65f))
        Text("${value.toInt()}", Modifier.width(38.dp), fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
}

@Composable internal fun ReaderDrawingToolbar(
    tool: String, width: Float, color: Int, canUndo: Boolean, canRedo: Boolean,
    onRead: () -> Unit, onPreset: (Float) -> Unit, onStyle: () -> Unit, onErase: () -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit, onBookmark: () -> Unit, onSelect: () -> Unit, onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            InkToolButton(InkIcon.Collapse, "Close drawing toolbar", onRead, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
            InkToolButton(InkIcon.Hand, "Pan and zoom", onRead, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
            listOf(.002f, .004f, .007f).forEachIndexed { index, preset ->
                Column(Modifier.width(48.dp).height(48.dp).clickable(role = Role.Button) { onPreset(preset) }
                    .semantics { contentDescription = "Pen preset ${index + 1}"; selected = tool == "Pen" && kotlin.math.abs(width - preset) < .0001f },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    InkSymbol(InkIcon.Pen, color = Color(color))
                    Text("${(preset * 1000).toInt()}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (tool == "Pen" && kotlin.math.abs(width - preset) < .0001f) Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape))
                }
            }
            InkToolButton(InkIcon.Pencil, "Pen style", onStyle, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
            InkToolButton(InkIcon.Eraser, "Eraser", onErase, selected = tool == "Eraser", buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
            InkToolButton(InkIcon.Undo, "Undo ink", onUndo, enabled = canUndo, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
            InkToolButton(InkIcon.Redo, "Redo ink", onRedo, enabled = canRedo, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
            InkToolButton(InkIcon.Save, "Bookmark page", onBookmark, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
            InkToolButton(InkIcon.Text, "Select text", onSelect, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
            InkToolButton(InkIcon.More, "More drawing tools", onMore, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable internal fun ReaderPenPopover(color: Int, width: Float, onColor: (Int) -> Unit, onWidth: (Float) -> Unit, onClose: () -> Unit) {
    Column(Modifier.widthIn(max = 480.dp).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pen", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            InkToolButton(InkIcon.Close, "Close pen style", onClose, buttonSize = 48.dp, tint = MaterialTheme.colorScheme.onSurface)
        }
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val path = Path().apply { moveTo(8f, size.height * .5f); cubicTo(size.width * .3f, 0f, size.width * .6f, size.height, size.width - 8f, size.height * .5f) }
            drawPath(path, Color(color), style = Stroke((width * size.width).coerceAtLeast(1f), cap = StrokeCap.Round))
        }
        HorizontalDivider()
        Text("Colors", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleSmall)
        val colors = listOf(0xFF242424, 0xFF666666, 0xFF999999, 0xFFFFFFFF, 0xFFB63838, 0xFF16866F, 0xFF315CBB, 0xFFE9C54A)
        // Two rows of four so the swatches fit a 360 dp phone without clipping.
        colors.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { value ->
                Box(Modifier.size(44.dp).semantics { contentDescription = "Pen color ${value.toString(16)}"; selected = color == value.toInt() }
                    .clickable(role = Role.Button) { onColor(value.toInt()) }.padding(5.dp)
                    .border(if (color == value.toInt()) 2.dp else 1.dp, MaterialTheme.colorScheme.onSurface, CircleShape).padding(3.dp)
                    .background(Color(value), CircleShape))
            }
        } }
        Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Line width", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("${(width * 1000).toInt()}")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkToolButton(InkIcon.Previous, "Thinner stroke", { onWidth((width - .001f).coerceAtLeast(.001f)) }, buttonSize = 48.dp)
            Slider(width, onWidth, valueRange = .001f.. .012f, modifier = Modifier.weight(1f))
            InkToolButton(InkIcon.Add, "Thicker stroke", { onWidth((width + .001f).coerceAtMost(.012f)) }, buttonSize = 48.dp)
        }
    }
}
