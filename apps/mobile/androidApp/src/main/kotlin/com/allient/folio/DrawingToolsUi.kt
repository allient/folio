package com.allient.folio

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.sin

@Composable
internal fun InkColorOptions(color: Int, onChange: (Int) -> Unit, onPickFromPage: ((Int) -> Unit) -> Unit, onClear: (() -> Unit)? = null, transparentSelected: Boolean = false, title: String = stringResource(R.string.tools_colors)) {
    val noFill = stringResource(R.string.tools_no_fill)
    var custom by remember { mutableStateOf(false) }
    val alpha = if (color ushr 24 == 0) 0xFF000000.toInt() else color and 0xFF000000.toInt()
    val pick = { custom = false; onPickFromPage { picked -> onChange(alpha or (picked and 0xFFFFFF)) } }
    if (custom) BrushColorPicker(alpha or (color and 0xFFFFFF), { custom = false }, { onChange(it); custom = false }, pick)
    val palette = listOf(0xFF000000, 0xFF555555, 0xFF888888, 0xFFBBBBBB, 0xFFFFFFFF, 0xFFB96868, 0xFF286851, 0xFF252F63,
        0xFF71B0AB, 0xFF84508C, 0xFFA58D48, 0xFFB5B75B, 0xFF244D32, 0xFF6B5179, 0xFF508DA7, 0xFF995B40)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 12.sp)
        Text(String.format(java.util.Locale.US, "#%08X", color), fontSize = 11.sp, color = ChromeMuted)
    }
    val swatches: List<Long?> = if (onClear != null) listOf(null) + palette else palette
    val columns = if (onClear != null) 9 else 8
    swatches.chunked(columns).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        row.forEach { value -> if (value == null) {
            Canvas(Modifier.size(30.dp).clip(CircleShape).border(if (transparentSelected) 3.dp else 1.dp, if (transparentSelected) Color(0xFF287463) else ChromeLine, CircleShape)
                .clickable { onClear?.invoke() }.semantics { contentDescription = noFill; selected = transparentSelected }) {
                val cell = size.width / 6f
                for (y in 0..5) for (x in 0..5) drawRect(if ((x+y)%2 == 0) Color.White else Color(0xFFCECECE), Offset(x*cell,y*cell), androidx.compose.ui.geometry.Size(cell,cell))
            }
        } else {
            val swatchDescription = stringResource(R.string.tools_color_value, String.format(java.util.Locale.US, "#%06X", value and 0xFFFFFF))
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(value))
            .border(if (!transparentSelected && (color and 0xFFFFFF) == (value.toInt() and 0xFFFFFF)) 3.dp else 1.dp, if (!transparentSelected && (color and 0xFFFFFF) == (value.toInt() and 0xFFFFFF)) Color(0xFF287463) else ChromeLine, CircleShape)
            .clickable { onChange(alpha or (value.toInt() and 0xFFFFFF)) }
            .semantics { contentDescription = swatchDescription })
        } }
        repeat(columns - row.size) { Spacer(Modifier.size(30.dp)) }
    } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { custom = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tools_custom_color), fontSize = 11.sp) }
        OutlinedButton(onClick = pick, modifier = Modifier.weight(1f)) { InkSymbol(InkIcon.Eyedropper, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(stringResource(R.string.tools_pick_from_page), fontSize = 11.sp) }
    }
}

@Composable
internal fun DrawingChoice(label: String, selected: Boolean, onClick: () -> Unit,
                           modifier: Modifier = Modifier,
                           // Test tags stay English: pass the English label here when [label] is translated.
                           tag: String = label,
                           symbol: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.testTag("drawing-${stableControlSlug(tag)}").heightIn(min = 76.dp)
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
    // One section at a time keeps the panel short; it opens on the section holding the current shape.
    var tab by rememberSaveable { mutableIntStateOf(ShapeLibrary.groups.indexOfFirst { kind in it.second }.coerceAtLeast(0)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShapeLibrary.groups.forEachIndexed { i, (title, _) ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(stringResource(shapeGroupLabel(title)), fontSize = 12.sp) },
                    modifier = Modifier.testTag("shape-tab-${stableControlSlug(title)}"))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ShapeLibrary.groups[tab].second.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.forEach { shape -> ShapeCell(shape, kind == shape, Modifier.weight(1f)) { onChange(shape) } }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ShapeCell(shape: ShapeKind, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(shape.labelRes)
    Surface(onClick = onClick, modifier = modifier.height(42.dp).semantics(mergeDescendants = true) { contentDescription = label; this.selected = selected },
        shape = RoundedCornerShape(10.dp), color = if (selected) Color(0xFFE5EFEC) else Color(0xFFF7F8F7),
        border = BorderStroke(1.dp, if (selected) Color(0xFF357568) else ChromeLine)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(26.dp, 22.dp)) {
                val points = ShapeGeometry.coded(shape, Offset(.1f, .1f), Offset(.9f, .9f), size.width, size.height, arrowHeadSize = size.minDimension * .35f)
                val edges = Path(); val hidden = Path()
                points.zipWithNext { (from, _), (to, code) ->
                    if (code == SolidPen.EDGE || code == SolidPen.HIDDEN) (if (code == SolidPen.EDGE) edges else hidden).apply {
                        moveTo(from.x * size.width, from.y * size.height); lineTo(to.x * size.width, to.y * size.height)
                    }
                }
                drawPath(edges, ChromeInk, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(hidden, ChromeInk.copy(alpha = .45f), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
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
        EraserMode.entries.forEach { option -> DrawingChoice(stringResource(option.labelRes), mode == option, { onMode(option) }, Modifier.weight(1f), tag = option.label) {
            InkSymbol(when (option) { EraserMode.Pixel -> InkIcon.PartialEraser; EraserMode.Stroke -> InkIcon.Eraser; EraserMode.Lasso -> InkIcon.LassoEraser })
        } }
    }
    Text(stringResource(when (mode) {
        EraserMode.Pixel -> R.string.tools_eraser_pixel_hint
        EraserMode.Stroke -> R.string.tools_eraser_stroke_hint
        EraserMode.Lasso -> R.string.tools_eraser_lasso_hint
    }), fontSize = 12.sp, color = ChromeMuted)
    if (mode != EraserMode.Lasso) {
        val diameter = stringResource(R.string.tools_eraser_diameter)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(diameter, Modifier.weight(1f), fontSize = 13.sp)
            InkToolButton(InkIcon.Previous, stringResource(R.string.tools_eraser_decrease), { onWidth((widthMm - .1f).coerceAtLeast(minWidthMm)) }, buttonSize = 30.dp, tag = "Decrease eraser diameter")
            Text(stringResource(R.string.tools_millimetres, widthMm), fontSize = 12.sp)
            InkToolButton(InkIcon.Next, stringResource(R.string.tools_eraser_increase), { onWidth((widthMm + .1f).coerceAtMost(maxWidthMm)) }, buttonSize = 30.dp, tag = "Increase eraser diameter")
        }
        VariableWidthSlider(widthMm, minWidthMm, maxWidthMm, Modifier.fillMaxWidth().height(34.dp), diameter, onWidth)
    }
    ToolToggle(stringResource(R.string.tools_active_layer_only), stringResource(R.string.tools_active_layer_only_hint), activeLayerOnly, onScope)
    ToolToggle(stringResource(R.string.tools_auto_deselect_eraser), stringResource(R.string.tools_auto_deselect_eraser_hint), autoReturn, onAutoReturn)
    HorizontalDivider(color = ChromeLine)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onClearLayer, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            InkSymbol(InkIcon.Eraser, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.tools_erase_current_layer), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(onClick = onClearAll, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            InkSymbol(InkIcon.Layers, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.tools_erase_all_layers), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Text(stringResource(R.string.tools_erase_note), fontSize = 11.sp, color = ChromeMuted)
}

/** Translated names for the tool enums. Their English `label` and enum names are ids (test tags, saved settings). */
internal val ShapeKind.labelRes: Int @StringRes get() = when (this) {
    ShapeKind.Line -> R.string.tools_shape_line
    ShapeKind.WavyLine -> R.string.tools_shape_wavy_line
    ShapeKind.Curve -> R.string.tools_shape_curve
    ShapeKind.Arrow -> R.string.tools_shape_arrow
    ShapeKind.DoubleArrow -> R.string.tools_shape_double_arrow
    ShapeKind.Rectangle -> R.string.tools_shape_rectangle
    ShapeKind.RoundedRectangle -> R.string.tools_shape_rounded_rectangle
    ShapeKind.Square -> R.string.tools_shape_square
    ShapeKind.Parallelogram -> R.string.tools_shape_parallelogram
    ShapeKind.Trapezoid -> R.string.tools_shape_trapezoid
    ShapeKind.Triangle -> R.string.tools_shape_triangle
    ShapeKind.RightTriangle -> R.string.tools_shape_right_triangle
    ShapeKind.Diamond -> R.string.tools_shape_diamond
    ShapeKind.Pentagon -> R.string.tools_shape_pentagon
    ShapeKind.Hexagon -> R.string.tools_shape_hexagon
    ShapeKind.Ellipse -> R.string.tools_shape_ellipse
    ShapeKind.Circle -> R.string.tools_shape_circle
    ShapeKind.Star -> R.string.tools_shape_star
    ShapeKind.Cross -> R.string.tools_shape_cross
    ShapeKind.Cube -> R.string.tools_shape_cube
    ShapeKind.Cuboid -> R.string.tools_shape_cuboid
    ShapeKind.Pyramid -> R.string.tools_shape_pyramid
    ShapeKind.Cone -> R.string.tools_shape_cone
    ShapeKind.Cylinder -> R.string.tools_shape_cylinder
    ShapeKind.Sphere -> R.string.tools_shape_sphere
    ShapeKind.Capsule -> R.string.tools_shape_capsule
    ShapeKind.Octagon -> R.string.tools_shape_octagon
    ShapeKind.Heart -> R.string.tools_shape_heart
    ShapeKind.Cloud -> R.string.tools_shape_cloud
    ShapeKind.Pie -> R.string.tools_shape_pie
    ShapeKind.Crescent -> R.string.tools_shape_crescent
    ShapeKind.Arc -> R.string.tools_shape_arc
    ShapeKind.PredefinedProcess -> R.string.tools_shape_predefined_process
    ShapeKind.InternalStorage -> R.string.tools_shape_internal_storage
    ShapeKind.Document -> R.string.tools_shape_document
    ShapeKind.ManualInput -> R.string.tools_shape_manual_input
    ShapeKind.ManualOperation -> R.string.tools_shape_manual_operation
    ShapeKind.OffPage -> R.string.tools_shape_off_page
    ShapeKind.Delay -> R.string.tools_shape_delay
    ShapeKind.Display -> R.string.tools_shape_display
    ShapeKind.SummingJunction -> R.string.tools_shape_summing_junction
    ShapeKind.OrJunction -> R.string.tools_shape_or_junction
    ShapeKind.Merge -> R.string.tools_shape_merge
    ShapeKind.BlockRight -> R.string.tools_shape_block_right
    ShapeKind.BlockLeft -> R.string.tools_shape_block_left
    ShapeKind.BlockUp -> R.string.tools_shape_block_up
    ShapeKind.BlockDown -> R.string.tools_shape_block_down
    ShapeKind.BlockLeftRight -> R.string.tools_shape_block_left_right
    ShapeKind.BlockUpDown -> R.string.tools_shape_block_up_down
    ShapeKind.BlockFourWay -> R.string.tools_shape_block_four_way
    ShapeKind.Chevron -> R.string.tools_shape_chevron
    ShapeKind.HomePlate -> R.string.tools_shape_home_plate
    ShapeKind.CalloutRect -> R.string.tools_shape_callout_rect
    ShapeKind.CalloutRounded -> R.string.tools_shape_callout_rounded
    ShapeKind.CalloutOval -> R.string.tools_shape_callout_oval
    ShapeKind.Starburst -> R.string.tools_shape_starburst
    ShapeKind.Hemisphere -> R.string.tools_shape_hemisphere
    ShapeKind.TriPrism -> R.string.tools_shape_tri_prism
    ShapeKind.Frustum -> R.string.tools_shape_frustum
    ShapeKind.Axes2D -> R.string.tools_shape_axes_2d
    ShapeKind.Axes3D -> R.string.tools_shape_axes_3d
    ShapeKind.NumberGrid -> R.string.tools_shape_number_grid
    ShapeKind.Angle -> R.string.tools_shape_angle
    ShapeKind.RightAngle -> R.string.tools_shape_right_angle
    ShapeKind.Brace -> R.string.tools_shape_brace
    ShapeKind.Bracket -> R.string.tools_shape_bracket
    ShapeKind.Paren -> R.string.tools_shape_paren
    ShapeKind.ElbowLine -> R.string.tools_shape_elbow_line
    ShapeKind.ElbowArrow -> R.string.tools_shape_elbow_arrow
    ShapeKind.ElbowDouble -> R.string.tools_shape_elbow_double
    ShapeKind.SCurve -> R.string.tools_shape_scurve
    ShapeKind.SCurveArrow -> R.string.tools_shape_scurve_arrow
    ShapeKind.SCurveDouble -> R.string.tools_shape_scurve_double
    ShapeKind.Inheritance -> R.string.tools_shape_inheritance
    ShapeKind.Aggregation -> R.string.tools_shape_aggregation
    ShapeKind.Composition -> R.string.tools_shape_composition
}

internal val EraserMode.labelRes: Int @StringRes get() = when (this) {
    EraserMode.Pixel -> R.string.tools_eraser_pixel
    EraserMode.Stroke -> R.string.tools_eraser_stroke
    EraserMode.Lasso -> R.string.tools_eraser_lasso
}

internal val ShapeLineStyle.labelRes: Int @StringRes get() = when (this) {
    ShapeLineStyle.Solid -> R.string.tools_line_solid
    ShapeLineStyle.Dotted -> R.string.tools_line_dotted
    ShapeLineStyle.Dashed -> R.string.tools_line_dashed
    ShapeLineStyle.DashDot -> R.string.tools_line_dash_dot
}

/** [ShapeLibrary.groups] titles are English ids (test tags `shape-tab-…`). */
@StringRes private fun shapeGroupLabel(title: String): Int = when (title) {
    "Basic" -> R.string.tools_shape_group_basic
    "Flowchart" -> R.string.tools_shape_group_flowchart
    "Arrows & callouts" -> R.string.tools_shape_group_arrows
    "3D & math" -> R.string.tools_shape_group_math
    "Connectors" -> R.string.tools_shape_group_connectors
    else -> R.string.tools_shape_group_uml
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
            val label = stringResource(style.labelRes)
            Surface(onClick = { onChange(style) }, modifier = Modifier.size(60.dp, 40.dp).semantics { contentDescription = label; selected = chosen },
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
