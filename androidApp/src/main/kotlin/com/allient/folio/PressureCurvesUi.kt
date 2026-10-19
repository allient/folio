package com.allient.folio

import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.hypot

internal enum class CurveChannel(val label: String, val key: String, val legacyKey: String) {
    Pressure("Pressure", "pressure", "curve"), Velocity("Velocity", "velocity", "velocityCurve"), Tilt("Tilt", "tilt", "tiltCurve")
}

private val GraphBackground = Color(0xFFFAFAF8)
private val GraphGrid = Color(0xFFE6E6E3)
private val GraphDiagonal = Color(0xFFD2D2CE)

/**
 * Reads `<key>CurvePreset` / `<key>CurvePoints`. Older installs stored a two-point Bezier in
 * `<legacy>1x..2y`; an untouched (linear) legacy curve becomes the new default, a customised one is resampled.
 */
internal fun SharedPreferences.loadResponseCurve(channel: CurveChannel, default: ResponseCurve, legacyEnabled: Boolean = false): ResponseCurve {
    ResponseCurve.decode(getString("${channel.key}CurvePreset", null), getString("${channel.key}CurvePoints", null))?.let { return it }
    val legacy = channel.legacyKey
    if (!contains("${legacy}1x")) return default
    val first = Offset(getFloat("${legacy}1x", .25f), getFloat("${legacy}1y", .25f))
    val second = Offset(getFloat("${legacy}2x", .75f), getFloat("${legacy}2y", .75f))
    val linear = abs(first.x - first.y) < .01f && abs(second.x - second.y) < .01f
    return when {
        !linear -> ResponseCurve.fromLegacyBezier(first, second)
        // Keep an enabled secondary channel neutral exactly as before.
        channel != CurveChannel.Pressure && legacyEnabled -> ResponseCurve.of(CurvePreset.Linear)
        else -> default
    }
}

internal fun SharedPreferences.Editor.putResponseCurve(channel: CurveChannel, curve: ResponseCurve): SharedPreferences.Editor =
    putString("${channel.key}CurvePreset", curve.preset.name).putString("${channel.key}CurvePoints", curve.encode())
        .remove("${channel.legacyKey}1x").remove("${channel.legacyKey}1y").remove("${channel.legacyKey}2x").remove("${channel.legacyKey}2y")

@Composable
internal fun PressureDialog(
    threshold: Float,
    profileName: String,
    pressureEnabled: Boolean,
    pressureCurve: ResponseCurve,
    velocityEnabled: Boolean,
    velocityCurve: ResponseCurve,
    tiltEnabled: Boolean,
    tiltCurve: ResponseCurve,
    onProfile: (PressureProfile) -> Unit,
    onThreshold: (Float) -> Unit,
    onPressureEnabled: (Boolean) -> Unit,
    onPressureCurve: (ResponseCurve) -> Unit,
    onVelocityEnabled: (Boolean) -> Unit,
    onVelocityCurve: (ResponseCurve) -> Unit,
    onTiltEnabled: (Boolean) -> Unit,
    onTiltCurve: (ResponseCurve) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<CurveChannel?>(null) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    fun curveOf(c: CurveChannel) = when (c) { CurveChannel.Pressure -> pressureCurve; CurveChannel.Velocity -> velocityCurve; CurveChannel.Tilt -> tiltCurve }
    fun enabledOf(c: CurveChannel) = when (c) { CurveChannel.Pressure -> pressureEnabled; CurveChannel.Velocity -> velocityEnabled; CurveChannel.Tilt -> tiltEnabled }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max = 520.dp).heightIn(max = screenHeight * .92f), shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Global curves", modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Medium, color = ChromeInk)
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CurveChannel.entries.forEach { c ->
                        GlobalCurveCard(c, curveOf(c), enabledOf(c), { editing = c }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(ChromeLine))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Activation threshold", modifier = Modifier.weight(1f), fontSize = 13.sp, color = ChromeInk)
                    Text("${(threshold * 100).toInt()}%", fontSize = 13.sp, color = ChromeInk)
                }
                Text("Ignores hover and light contact below this pressure.", fontSize = 11.sp, color = ChromeMuted)
                LineSlider(threshold, 0f..0.5f, "Activation threshold", onValueChange = onThreshold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pressureProfiles.forEach { profile ->
                        FilterChip(selected = profileName == profile.name, onClick = { onProfile(profile) },
                            label = { Text("${profile.name} ${(profile.threshold * 100).toInt()}%", fontSize = 11.sp) })
                    }
                }
            }
        }
    }
    editing?.let { c ->
        CurveEditorDialog(c, curveOf(c), enabledOf(c),
            onCurve = when (c) { CurveChannel.Pressure -> onPressureCurve; CurveChannel.Velocity -> onVelocityCurve; CurveChannel.Tilt -> onTiltCurve },
            onEnabled = when (c) { CurveChannel.Pressure -> onPressureEnabled; CurveChannel.Velocity -> onVelocityEnabled; CurveChannel.Tilt -> onTiltEnabled },
            onDismiss = { editing = null })
    }
}

@Composable
private fun GlobalCurveCard(channel: CurveChannel, curve: ResponseCurve, enabled: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.semantics { contentDescription = "${channel.label} curve" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F1EF))
            .clickable(role = Role.Button, onClick = onOpen), contentAlignment = Alignment.Center) {
            Text(channel.label.uppercase() + if (enabled) "" else " · OFF", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp, color = if (enabled) ChromeInk else ChromeMuted)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(GraphBackground)
            .border(1.dp, ChromeLine, RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onOpen)) {
            drawCurveGraph(curve, 6.dp.toPx(), if (enabled) ChromeInk else ChromeMuted, 2.dp.toPx())
        }
    }
}

private fun DrawScope.drawCurveGraph(curve: ResponseCurve, pad: Float, curveColor: Color, strokeWidth: Float, grid: Boolean = true) {
    val w = size.width - 2f * pad
    val h = size.height - 2f * pad
    val line = 1f.coerceAtLeast(strokeWidth / 2.5f)
    if (grid) for (i in 0..3) {
        val f = i / 3f
        drawLine(GraphGrid, Offset(pad + f * w, pad), Offset(pad + f * w, pad + h), line)
        drawLine(GraphGrid, Offset(pad, pad + f * h), Offset(pad + w, pad + f * h), line)
    }
    drawLine(GraphDiagonal, Offset(pad, pad + h), Offset(pad + w, pad), line)
    val path = Path().apply {
        moveTo(pad, pad + (1f - curve.value(0f)) * h)
        for (step in 1..64) { val x = step / 64f; lineTo(pad + x * w, pad + (1f - curve.value(x)) * h) }
    }
    drawPath(path, curveColor, style = Stroke(width = strokeWidth))
}

@Composable
private fun CurveEditorDialog(
    channel: CurveChannel,
    curve: ResponseCurve,
    enabled: Boolean,
    onCurve: (ResponseCurve) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var history by remember(channel) { mutableStateOf(emptyList<ResponseCurve>()) }
    val current by rememberUpdatedState(curve)
    val currentOnCurve by rememberUpdatedState(onCurve)
    fun apply(next: ResponseCurve) { if (next != current) { history = history + current; currentOnCurve(next) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.92f).widthIn(max = 460.dp), shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InkToolButton(InkIcon.Undo, "Undo curve change", { history.lastOrNull()?.let { history = history.dropLast(1); currentOnCurve(it) } },
                        enabled = history.isNotEmpty(), buttonSize = 40.dp)
                    Spacer(Modifier.weight(1f))
                    CurvePreset.entries.filter { it != CurvePreset.Custom }.forEach { preset ->
                        PresetButton(preset, curve.preset == preset) { apply(ResponseCurve.of(preset)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                        Text("Strength", fontSize = 11.sp, color = ChromeMuted, softWrap = false, modifier = Modifier.wrapContentSize(unbounded = true).rotate(-90f))
                    }
                    CurveGraphEditor(curve, Modifier.weight(1f).aspectRatio(1f), onDragStart = { history = history + current }, onChange = { currentOnCurve(it) })
                }
                Text(channel.label, fontSize = 11.sp, color = ChromeMuted, modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (channel == CurveChannel.Pressure) "Apply pressure curve" else "Apply ${channel.label.lowercase()} to strokes",
                        modifier = Modifier.weight(1f), fontSize = 13.sp, color = ChromeInk)
                    Switch(checked = enabled, onCheckedChange = onEnabled)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(curve.preset.label, modifier = Modifier.weight(1f), fontSize = 12.sp, color = ChromeMuted)
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(preset: CurvePreset, selected: Boolean, size: Dp = 40.dp, onClick: () -> Unit) {
    val curve = remember(preset) { ResponseCurve.of(preset) }
    Canvas(Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(if (selected) Color(0xFFE6E6E3) else Color.Transparent)
        .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) ChromeInk else ChromeLine), RoundedCornerShape(6.dp))
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "${preset.label} curve"; this.selected = selected }) {
        drawCurveGraph(curve, 7.dp.toPx(), ChromeInk, 1.6.dp.toPx(), grid = false)
    }
}

@Composable
private fun CurveGraphEditor(curve: ResponseCurve, modifier: Modifier, onDragStart: () -> Unit, onChange: (ResponseCurve) -> Unit) {
    val current by rememberUpdatedState(curve)
    val currentStart by rememberUpdatedState(onDragStart)
    val currentChange by rememberUpdatedState(onChange)
    Canvas(
        modifier.clip(RoundedCornerShape(10.dp)).background(GraphBackground).border(1.dp, ChromeLine, RoundedCornerShape(10.dp))
            .semantics { contentDescription = "Curve editor"; stateDescription = "${curve.points.size} draggable control points" }
            .pointerInput(Unit) {
                val pad = 16.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width - 2f * pad
                    val h = size.height - 2f * pad
                    fun screen(p: Offset) = Offset(pad + p.x * w, pad + (1f - p.y) * h)
                    val points = current.points
                    val nearest = points.indices.minByOrNull { val s = screen(points[it]); hypot(down.position.x - s.x, down.position.y - s.y) }
                        ?: return@awaitEachGesture
                    val s = screen(points[nearest])
                    if (hypot(down.position.x - s.x, down.position.y - s.y) > 32.dp.toPx()) return@awaitEachGesture
                    down.consume()
                    currentStart()
                    drag(down.id) { change ->
                        change.consume()
                        val pts = current.points
                        if (nearest > pts.lastIndex) return@drag
                        val last = pts.lastIndex
                        val x = when (nearest) { 0 -> 0f; last -> 1f
                            else -> {
                                val lo = pts[nearest - 1].x + .04f; val hi = pts[nearest + 1].x - .04f
                                if (hi <= lo) (lo + hi) / 2f else ((change.position.x - pad) / w).coerceIn(lo, hi)
                            } }
                        val low = if (nearest == 0) 0f else pts[nearest - 1].y
                        val high = if (nearest == last) 1f else pts[nearest + 1].y
                        val y = (1f - (change.position.y - pad) / h).coerceIn(low, high)
                        currentChange(current.withPoints(pts.mapIndexed { i, p -> if (i == nearest) Offset(x, y) else p }))
                    }
                }
            },
    ) {
        val pad = 16.dp.toPx()
        drawCurveGraph(curve, pad, ChromeInk, 2.5.dp.toPx())
        val w = size.width - 2f * pad
        val h = size.height - 2f * pad
        curve.points.forEach {
            val c = Offset(pad + it.x * w, pad + (1f - it.y) * h)
            drawCircle(Color.Black.copy(alpha = .08f), 11.dp.toPx(), c + Offset(0f, 1.dp.toPx()))
            drawCircle(Color.White, 10.dp.toPx(), c)
            drawCircle(ChromeInk, 10.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        }
    }
}
