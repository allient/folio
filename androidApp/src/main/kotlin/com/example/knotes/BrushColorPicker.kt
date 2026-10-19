package com.example.knotes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** RGB selection preserves the brush's independently configured opacity. */
@Composable
internal fun BrushColorPicker(initial: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit, onPickFromPage: (() -> Unit)? = null) {
    val hsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf(String.format(Locale.US, "%06X", initial and 0xFFFFFF)) }
    fun rgb() = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    fun updateHex() { hex = String.format(Locale.US, "%06X", rgb() and 0xFFFFFF) }
    val valid = hex.matches(Regex("[0-9a-fA-F]{6}"))
    val currentHue by rememberUpdatedState(hue)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Custom color") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Drag to choose saturation and brightness", fontSize = 12.sp)
            Canvas(Modifier.fillMaxWidth().height(170.dp)
                .semantics { contentDescription = "Color saturation and brightness" }
                .pointerInput(Unit) { awaitEachGesture {
                    val down = awaitFirstDown()
                    fun choose(point: Offset) {
                        hue = currentHue
                        saturation = (point.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        brightness = 1f - (point.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        updateHex()
                    }
                    choose(down.position); down.consume()
                    do { val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        choose(change.position); change.consume()
                    } while (event.changes.any { it.id == down.id && it.pressed })
                } }) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val center = Offset(saturation * size.width, (1f - brightness) * size.height)
                drawCircle(Color.Black, 8.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                drawCircle(Color.White, 8.dp.toPx(), center, style = Stroke(1.5f.dp.toPx()))
            }
            Text("Hue", fontSize = 12.sp)
            Box(Modifier.fillMaxWidth().height(8.dp).background(Brush.horizontalGradient(
                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), RoundedCornerShape(4.dp)))
            Slider(hue, { hue = it; updateHex() }, valueRange = 0f..360f,
                modifier = Modifier.semantics { contentDescription = "Color hue" })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).background(Color(rgb()), RoundedCornerShape(12.dp)).border(1.dp, ChromeLine, RoundedCornerShape(12.dp)))
                OutlinedTextField(hex, { value ->
                    hex = value.removePrefix("#").take(6)
                    if (hex.matches(Regex("[0-9a-fA-F]{6}"))) {
                        val parsed = (0xFF000000.toInt() or hex.toInt(16))
                        val next = FloatArray(3); android.graphics.Color.colorToHSV(parsed, next)
                        hue = next[0]; saturation = next[1]; brightness = next[2]
                    }
                }, label = { Text("Hex color") }, prefix = { Text("#") }, singleLine = true,
                    isError = !valid, modifier = Modifier.weight(1f))
            }
            if (!valid) Text("Enter six hexadecimal digits, for example 345C91.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            Text("Opacity is kept unchanged.", fontSize = 11.sp, color = ChromeMuted)
            if (onPickFromPage != null) OutlinedButton(onClick = onPickFromPage, modifier = Modifier.fillMaxWidth()) {
                InkSymbol(InkIcon.Eyedropper, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pick from page")
            }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onApply((initial and 0xFF000000.toInt()) or (rgb() and 0xFFFFFF)) }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, shape = RoundedCornerShape(24.dp))
}
