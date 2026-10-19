package com.example.knotes

import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.testTag
import java.util.Locale
import kotlin.math.sin

@Composable
internal fun InkColorOptions(color: Int, onChange: (Int) -> Unit, onPickFromPage: ((Int) -> Unit) -> Unit, onClear: (() -> Unit)? = null, transparentSelected: Boolean = false) {
    var custom by remember { mutableStateOf(false) }
    val pick = { custom = false; onPickFromPage { picked -> onChange((color and 0xFF000000.toInt()) or (picked and 0xFFFFFF)) } }
    if (custom) BrushColorPicker(color, { custom = false }, { onChange(it); custom = false }, pick)
    val palette = listOf(0xFF000000, 0xFF555555, 0xFF888888, 0xFFBBBBBB, 0xFFFFFFFF, 0xFFB96868, 0xFF286851, 0xFF252F63,
        0xFF71B0AB, 0xFF84508C, 0xFFA58D48, 0xFFB5B75B, 0xFF244D32, 0xFF6B5179, 0xFF508DA7, 0xFF995B40)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Colors", fontSize = 12.sp)
        Text(String.format(java.util.Locale.US, "#%08X", color), fontSize = 11.sp, color = ChromeMuted)
    }
    val swatches: List<Long?> = if (onClear != null) listOf(null) + palette else palette
    val columns = if (onClear != null) 9 else 8
    swatches.chunked(columns).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        row.forEach { value -> if (value == null) {
            Canvas(Modifier.size(30.dp).clip(CircleShape).border(if (transparentSelected) 3.dp else 1.dp, if (transparentSelected) Color(0xFF287463) else ChromeLine, CircleShape)
                .clickable { onClear?.invoke() }.semantics { contentDescription = "No fill"; selected = transparentSelected }) {
                val cell = size.width / 6f
                for (y in 0..5) for (x in 0..5) drawRect(if ((x+y)%2 == 0) Color.White else Color(0xFFCECECE), Offset(x*cell,y*cell), androidx.compose.ui.geometry.Size(cell,cell))
            }
        } else Box(Modifier.size(30.dp).clip(CircleShape).background(Color(value))
            .border(if (!transparentSelected && (color and 0xFFFFFF) == (value.toInt() and 0xFFFFFF)) 3.dp else 1.dp, if (!transparentSelected && (color and 0xFFFFFF) == (value.toInt() and 0xFFFFFF)) Color(0xFF287463) else ChromeLine, CircleShape)
            .clickable { onChange((color and 0xFF000000.toInt()) or (value.toInt() and 0xFFFFFF)) }
            .semantics { contentDescription = String.format(java.util.Locale.US, "Color #%06X", value and 0xFFFFFF) }) }
        repeat(columns - row.size) { Spacer(Modifier.size(30.dp)) }
    } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { custom = true }, modifier = Modifier.weight(1f)) { Text("Custom color", fontSize = 11.sp) }
        OutlinedButton(onClick = pick, modifier = Modifier.weight(1f)) { InkSymbol(InkIcon.Eyedropper, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Pick from page", fontSize = 11.sp) }
    }
}

@Composable
internal fun DrawingChoice(label: String, selected: Boolean, onClick: () -> Unit,
                           modifier: Modifier = Modifier, symbol: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.testTag("drawing-${stableControlSlug(label)}").heightIn(min = 76.dp)
        .semantics(mergeDescendants = true) { contentDescription = label; this.selected = selected },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFE8F0EB) else Color(0xFFF7F8F7),
        border = BorderStroke(1.dp, if (selected) Color(0xFF537363) else ChromeLine)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            symbol()
            Text(label, fontSize = 11.sp, color = ChromeInk)
        }
    }
}

@Composable
internal fun ShapeChoices(kind: ShapeKind, onChange: (ShapeKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShapeLibrary.groups.forEach { (title, kinds) ->
            Text(title, fontSize = 12.sp, color = ChromeMuted, fontWeight = FontWeight.Medium)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                kinds.chunked(6).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { shape -> ShapeCell(shape, kind == shape, Modifier.weight(1f)) { onChange(shape) } }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeCell(shape: ShapeKind, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(42.dp).semantics(mergeDescendants = true) { contentDescription = shape.label; this.selected = selected },
        shape = RoundedCornerShape(10.dp), color = if (selected) Color(0xFFE5EFEC) else Color(0xFFF7F8F7),
        border = BorderStroke(1.dp, if (selected) Color(0xFF357568) else ChromeLine)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(26.dp, 22.dp)) {
                val points = ShapeGeometry.points(shape, Offset(.1f, .1f), Offset(.9f, .9f), size.width, size.height, arrowHeadSize = size.minDimension * .35f)
                val path = Path().apply {
                    moveTo(points.first().x * size.width, points.first().y * size.height)
                    points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                }
                drawPath(path, ChromeInk, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
internal fun ToolToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 13.sp, color = ChromeInk)
            Text(description, fontSize = 11.sp, color = ChromeMuted)
        }
        Switch(checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
internal fun EraserControls(mode: EraserMode, widthMm: Float, minWidthMm: Float, maxWidthMm: Float, activeLayerOnly: Boolean, autoReturn: Boolean,
                           onMode: (EraserMode) -> Unit, onWidth: (Float) -> Unit,
                           onScope: (Boolean) -> Unit, onAutoReturn: (Boolean) -> Unit,
                           onClearLayer: () -> Unit, onClearAll: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EraserMode.entries.forEach { option -> DrawingChoice(option.label, mode == option, { onMode(option) }, Modifier.weight(1f)) {
            InkSymbol(when (option) { EraserMode.Pixel -> InkIcon.PartialEraser; EraserMode.Stroke -> InkIcon.Eraser; EraserMode.Lasso -> InkIcon.LassoEraser })
        } }
    }
    Text(when (mode) {
        EraserMode.Pixel -> "Drag across the area you want to erase."
        EraserMode.Stroke -> "Touch any part of a stroke to erase the whole stroke."
        EraserMode.Lasso -> "Draw a loop around ink to erase the selected strokes."
    }, fontSize = 12.sp, color = ChromeMuted)
    if (mode != EraserMode.Lasso) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Eraser diameter", Modifier.weight(1f), fontSize = 13.sp)
            InkToolButton(InkIcon.Previous, "Decrease eraser diameter", { onWidth((widthMm - .1f).coerceAtLeast(minWidthMm)) }, buttonSize = 30.dp)
            Text(String.format(Locale.US, "%.1f mm", widthMm), fontSize = 12.sp)
            InkToolButton(InkIcon.Next, "Increase eraser diameter", { onWidth((widthMm + .1f).coerceAtMost(maxWidthMm)) }, buttonSize = 30.dp)
        }
        VariableWidthSlider(widthMm, minWidthMm, maxWidthMm, Modifier.fillMaxWidth().height(34.dp), "Eraser diameter", onWidth)
    }
    ToolToggle("Active layer only", "Off erases across visible, unlocked layers.", activeLayerOnly, onScope)
    ToolToggle("Auto-deselect Eraser", "Return to the previous writing brush after erasing.", autoReturn, onAutoReturn)
    HorizontalDivider(color = ChromeLine)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClearLayer, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            InkSymbol(InkIcon.Eraser, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Erase current layer", fontSize = 11.sp, maxLines = 1)
        }
        OutlinedButton(onClick = onClearAll, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            InkSymbol(InkIcon.Layers, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Erase all layers", fontSize = 11.sp, maxLines = 1)
        }
    }
    Text("Templates are kept. Clear actions can be undone.", fontSize = 11.sp, color = ChromeMuted)
}

/** Preview uses exactly the same versioned rendering path as live and saved strokes. */
@Composable
internal fun BrushPreview(brush: BrushStyle) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, ChromeLine)) {
        AndroidView(factory = { BrushPreviewView(it) }, update = { it.brush = brush }, modifier = Modifier.fillMaxWidth().height(44.dp))
    }
}

private class BrushPreviewView(context: Context) : View(context) {
    private val engine = JetpackInkEngine()
    var brush: BrushStyle = brushes.first()
        set(value) { if (field != value) { field = value; invalidate() } }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val samples = (0..88).map { index ->
            val t = index / 88f
            InkPoint(.07f + .86f * t, .5f + .22f * sin(t * Math.PI * 4).toFloat(), brush.mapPressure(.12f + .88f * sin(t * Math.PI).toFloat()), index * 8L)
        }
        engine.draw(canvas, InkStroke(brush.name, brush.color, brush.width, samples, id = "preview", engine = JetpackInkEngine.FORMAT,
            texture = brush.texture, grain = brush.grain, angle = brush.angle, variant = brush.variant), width.toFloat(), height.toFloat())
    }
    override fun onDetachedFromWindow() { engine.release(); super.onDetachedFromWindow() }
}

@Composable
internal fun ShapeLineStyleChoices(selectedStyle: String, onChange: (ShapeLineStyle) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        ShapeLineStyle.entries.forEach { style ->
            val chosen = selectedStyle == style.name.lowercase()
            Surface(onClick = { onChange(style) }, modifier = Modifier.size(60.dp, 40.dp).semantics { contentDescription = style.label; selected = chosen },
                shape = RoundedCornerShape(12.dp), color = if (chosen) Color(0xFFE5EFEC) else Color(0xFFF7F8F7), border = BorderStroke(1.dp, if (chosen) Color(0xFF287463) else ChromeLine)) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val effect = when (style) {
                        ShapeLineStyle.Dotted -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 4.dp.toPx()))
                        ShapeLineStyle.Dashed -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx()))
                        ShapeLineStyle.DashDot -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 3.dp.toPx(), 1.dp.toPx(), 3.dp.toPx()))
                        else -> null
                    }
                    drawLine(ChromeInk, Offset(0f,size.height/2), Offset(size.width,size.height/2), 1.8.dp.toPx(), cap = StrokeCap.Round, pathEffect = effect)
                }
            }
        }
    }
}
