package com.example.knotes

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal val ChromeInk = Color(0xFF252525)
internal val ChromeMuted = Color(0xFF858585)
internal val ChromeLine = Color(0xFFE2E2E2)

internal enum class InkIcon(val path: String) {
    Back("M15 5 L8 12 L15 19 M8 12 H22"),
    Pen("M8 19 V12 L12 3 L16 12 V19 Z M8 12 H16 M12 3 V13 M7 22 H17"),
    Pencil("M8 20 L7 15 L16 3 L21 7 L12 20 Z M7 15 L12 20 M14 6 L19 10 M7 20 L6 22 L10 21"),
    Marker("M5 21 L7 15 L16 3 L22 8 L13 19 L5 21 M7 15 L13 19 M14 6 L20 11 M4 23 H14"),
    Eraser("M3 15 L13 4 Q14 3 15 4 L21 10 Q22 11 21 12 L13 21 H9 Z M8 10 L17 17 M13 21 H22"),
    PartialEraser("M4 14 L13 4 L21 11 L13 20 H10 Z M8 10 L17 17 M3 22 H7 M11 22 H15 M19 22 H22"),
    LassoEraser("M7 18 C0 15 1 4 11 3 C24 2 25 17 15 19 M5 17 L9 22 M8 21 L11 16 M12 10 L16 6 L21 11 L17 15 Z"),
    RectangleSelect("M3 8 V3 H8 M16 3 H21 V8 M21 16 V21 H16 M8 21 H3 V16 M3 11 V13 M11 3 H13 M21 11 V13 M11 21 H13"),
    Rotate("M4 8 A8 8 0 1 1 4 16 M4 3 V8 H9"),
    Cut("M9 9 L21 21 M9 15 L21 3 M9 7 A3 3 0 1 1 3 7 A3 3 0 1 1 9 7 M9 17 A3 3 0 1 1 3 17 A3 3 0 1 1 9 17"),
    Undo("M8 5 L3 10 L8 15 M3 10 H14 Q21 10 21 17 V19"),
    Redo("M16 5 L21 10 L16 15 M21 10 H10 Q3 10 3 17 V19"),
    Locate("M12 8 A4 4 0 1 1 12 16 A4 4 0 1 1 12 8 M12 2 V6 M12 18 V22 M2 12 H6 M18 12 H22"),
    Table("M3 5 H21 V19 H3 Z M3 10 H21 M3 14.5 H21 M9 5 V19 M15 5 V19"),
    Sticker("M12 3 A9 9 0 1 0 21 12 Q15 12 15 6 Q15 3 12 3 M8.5 10.5 V12 M13 10.5 V12 M8 15 Q11 18 14 15"),
    Sticky("M5 3 H19 V15 L15 21 H5 Z M15 21 V15 H19 M8 8 H16 M8 12 H13"),
    Ruler("M3 16 L16 3 L21 8 L8 21 Z M7 12 L9.5 14.5 M10 9 L12.5 11.5 M13 6 L15.5 8.5"),
    Previous("M14 7 L9 12 L14 17"),
    Next("M10 7 L15 12 L10 17"),
    Pages("M7 3 H20 V18 H7 Z M4 7 V21 H16 M10 7 H17 M10 10 H17 M10 13 H15"),
    AddPage("M5 3 H15 L20 8 V21 H5 Z M15 3 V8 H20 M9 14 H16 M12.5 10.5 V17.5"),
    Save("M4 3 H17 L21 7 V21 H3 V3 Z M7 3 V10 H16 V3 M7 21 V14 H17 V21 M13 5 V8"),
    Saved("M4 12 L10 18 L21 6"),
    Expand("M3 9 V3 H9 M15 3 H21 V9 M21 15 V21 H15 M9 21 H3 V15"),
    Collapse("M9 3 V9 H3 M15 3 V9 H21 M21 15 H15 V21 M3 15 H9 V21"),
    Pressure("M3 20 V4 M3 20 H21 M5 18 C14 18 9 6 20 5"),
    More("M4 11 H5 V13 H4 Z M11 11 H12 V13 H11 Z M18 11 H19 V13 H18 Z"),
    Close("M6 6 L18 18 M18 6 L6 18"),
    Add("M12 4 V20 M4 12 H20"),
    Search("M16 10 A6 6 0 1 1 4 10 A6 6 0 1 1 16 10 M15 15 L21 21"),
    Trash("M4 6 H20 M9 6 V3 H15 V6 M6 6 L7 21 H17 L18 6 M10 10 V17 M14 10 V17"),
    Rename("M4 20 L5 15 L17 3 L21 7 L9 19 Z M14 6 L18 10 M13 21 H22"),
    Tools("M4 5 H20 M4 12 H20 M4 19 H20 M8 2 V8 M16 9 V15 M10 16 V22"),
    Library("M3 4 H8 V21 H3 Z M10 4 H15 V21 H10 Z M17 5 L21 4 L24 20 L20 21 Z"),
    Paper("M5 2 H16 L21 7 V22 H5 Z M16 2 V7 H21"),
    Template("M3 3 H21 V21 H3 Z M3 9 H21 M3 15 H21 M9 3 V21 M15 3 V21"),
    Layers("M2 8 L12 2 L22 8 L12 14 Z M3 13 L12 19 L21 13 M3 18 L12 23 L21 18"),
    Shapes("M3 3 H15 V15 H3 Z M21 16 A6 6 0 1 1 9 16 A6 6 0 1 1 21 16"),
    Lasso("M6 17 C-2 10 4 3 12 3 C24 3 25 17 14 18 C5 19 3 14 8 12 C13 11 14 18 8 22"),
    // Tilted paint pail with a clear open rim, handle, and falling drop.
    Fill("M16.56 8.94 L7.62 0 L6.21 1.41 L8.59 3.79 L3.44 8.94 C2.85 9.53 2.85 10.48 3.44 11.06 L8.94 16.56 C9.53 17.15 10.48 17.15 11.06 16.56 L16.56 11.06 C17.15 10.48 17.15 9.53 16.56 8.94 Z M5.21 10 L10 5.21 L14.79 10 Z M20 12 C20 12 17.5 15 17.5 16.5 C17.5 19.8 22.5 19.8 22.5 16.5 C22.5 15 20 12 20 12 Z"),
    Eyedropper("M14 3 L21 10 M16 2 L22 8 L18 12 L12 6 Z M13 7 L3 17 V21 H7 L17 11 M5 17 L7 19"),
    Visibility("M2 12 C5 5 19 5 22 12 C19 19 5 19 2 12 Z M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9"),
    VisibilityOff("M3 3 L21 21 M10 6 C14 5 19 7 22 12 C21 14 20 15 18 16 M6 7 C4 8 3 10 2 12 C5 18 12 20 17 18 M10 10 A3 3 0 0 0 14 14"),
    Lock("M5 10 H19 V21 H5 Z M8 10 V7 A4 4 0 0 1 16 7 V10 M12 14 V17"),
    LockOpen("M5 10 H19 V21 H5 Z M8 10 V7 A4 4 0 0 1 16 7 M12 14 V17"),
    Text("M3 4 H21 M12 4 V21 M8 21 H16"),
    Insert("M9 8 L15 2 C19 -1 24 4 20 8 L10 19 C5 24 -1 18 4 13 L14 3 M8 15 L17 6"),
    Share("M15 3 L21 9 L15 15 M21 9 H12 Q6 9 6 15 M5 4 H2 V22 H20 V18"),
    Zoom("M16 10 A6 6 0 1 1 4 10 A6 6 0 1 1 16 10 M15 15 L21 21 M7 10 H13 M10 7 V13"),
    Hand("M7 13 V5 Q9 2 11 5 V11 V3 Q13 0 15 3 V11 V5 Q17 2 19 5 V13 V9 Q22 7 22 11 V16 Q22 22 15 22 H12 L4 15 Q2 11 6 13 L9 16"),
    Tag("M3 3 H12 L22 13 L13 22 L3 12 Z M7 7 H8 V8 H7 Z"),
    Flip("M12 2 V22 M8 5 L2 19 H8 Z M16 5 L22 19 H16 Z"),
    Copy("M9 8 H18 Q20 8 20 10 V20 Q20 22 18 22 H9 Q7 22 7 20 V10 Q7 8 9 8 Z M15 4 H5 Q3 4 3 6 V17 M10 12 H17 M10 16 H17"),
    Border("M4 4 H20 V20 H4 Z M3 23 H21 M6 9 H18"),
    Microphone("M8 5 A4 4 0 0 1 16 5 V11 A4 4 0 0 1 8 11 Z M5 10 V12 A7 7 0 0 0 19 12 V10 M12 19 V23 M8 23 H16"),
    Clock("M12 2 A10 10 0 1 1 12 22 A10 10 0 1 1 12 2 M12 6 V12 L16 14"),
}

@Composable
internal fun InkSymbol(icon: InkIcon, modifier: Modifier = Modifier, color: Color = ChromeInk) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            if (icon == InkIcon.Fill) {
                path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                drawPath(path, color)
            } else drawPath(path, color, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun InkToolButton(
    icon: InkIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    buttonSize: Dp = 36.dp,
    tint: Color = ChromeInk,
) {
    Box(
        modifier.testTag("action-${stableControlSlug(label)}").size(buttonSize).clip(RoundedCornerShape(4.dp))
            .background(if (selected) Color(0xFFEAEAEA) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { InkSymbol(icon, color = if (enabled) tint else Color(0xFFCCCCCC)) }
}

@Composable
internal fun InkRailButton(icon: InkIcon, label: String, onClick: () -> Unit, selected: Boolean = false) {
    Column(
        Modifier.testTag("rail-${icon.name.lowercase(Locale.ROOT)}").width(48.dp).height(58.dp)
            .background(if (selected) Color(0xFFECECEC) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        InkSymbol(icon)
        Text(label, color = ChromeInk, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
internal fun ChromeDivider() {
    Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(24.dp).background(ChromeLine))
}

@Composable
internal fun BrushPreset(name: String, width: Float, color: Int, selected: Boolean, scale: Float = 1f, id: String = name, verticalDrag: Boolean = false,
    onClick: () -> Unit, onReorderDrag: (String, Float) -> Unit = { _, _ -> }, onReorderStart: () -> Unit = {}, onReorderEnd: () -> Unit = {},
    modifier: Modifier = Modifier) {
    val currentId by androidx.compose.runtime.rememberUpdatedState(id)
    val currentClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    val currentDrag by androidx.compose.runtime.rememberUpdatedState(onReorderDrag)
    val currentStart by androidx.compose.runtime.rememberUpdatedState(onReorderStart)
    val currentEnd by androidx.compose.runtime.rememberUpdatedState(onReorderEnd)
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier.testTag("preset-${stableControlSlug(id)}").width((32 * scale).dp).height(32.dp).clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) { var draggedId = ""; detectDragGesturesAfterLongPress(
                onDragStart = { draggedId = currentId; dragging = true; currentStart() },
                onDragEnd = { dragging = false; currentEnd() }, onDragCancel = { dragging = false; currentEnd() },
            ) { change, drag -> change.consume(); currentDrag(draggedId, if (verticalDrag) drag.y else drag.x) } }
            .background(if (dragging) Color(0xFFCCE8DD) else if (selected) Color(0xFFE8ECEA) else Color.Transparent)
            .then(if (dragging) Modifier.shadow(8.dp, RoundedCornerShape(8.dp)).border(2.dp, Color(0xFF287463), RoundedCornerShape(8.dp)) else Modifier)
            .then(modifier)
            .clickable(role = Role.Button, onClick = { if (!dragging) currentClick() })
            .semantics { contentDescription = "$name, ${String.format(Locale.US, "%.1f", width)} width. Long press and drag to reorder"; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)) {
            Text(String.format(Locale.US, "%.1f", width), fontSize = 7.sp, lineHeight = 8.sp, color = ChromeMuted)
            BrushGlyph(name, color, Modifier.width((17 * scale).dp).height((19 * scale).dp))
        }
        if (dragging) Text("⠿", Modifier.align(Alignment.TopEnd).padding(end = 1.dp), color = Color(0xFF155D4B), fontSize = 9.sp)
    }
}

@Composable
internal fun PaperThumbnail(strokes: List<InkStroke>, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.White).border(1.dp, ChromeLine)) {
        strokes.forEach { stroke ->
            val points = stroke.points
            val ink = Color(stroke.color)
            if (points.size == 1) {
                val p = points.first()
                drawCircle(ink, 0.8.dp.toPx(), Offset(p.x * size.width, p.y * size.height))
            }
            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]
                drawLine(ink, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height),
                    (stroke.width * size.width / 600f).coerceAtLeast(0.7f), cap = StrokeCap.Round)
            }
        }
    }
}

internal fun stableControlSlug(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')

internal fun <T> reorderByDrag(items: List<T>, fromId: String, direction: Int, id: (T) -> String): List<T> {
    if (direction == 0) return items
    val from = items.indexOfFirst { id(it) == fromId }
    val to = (from + direction).coerceIn(0, items.lastIndex)
    if (from < 0 || from == to) return items
    return items.toMutableList().apply { add(to, removeAt(from)) }
}

/** Fades the start/end edge of a horizontal list when more items can scroll in from that side. */
internal fun Modifier.horizontalFadeEdges(state: androidx.compose.foundation.lazy.LazyListState, enabled: Boolean, fade: Dp = 10.dp): Modifier =
    if (!enabled) this else graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val width = fade.toPx()
        if (state.canScrollBackward) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, width),
            size = androidx.compose.ui.geometry.Size(width, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        if (state.canScrollForward) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - width, size.width),
            topLeft = Offset(size.width - width, 0f), size = androidx.compose.ui.geometry.Size(width, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }

