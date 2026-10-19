package com.example.knotes

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun InfiniteCanvasNavigator(page: NotebookPage, pins: List<CanvasPin>, x: Float, y: Float, zoom: Float, spanY: Float,
    onJump: (Float, Float) -> Unit, onLocate: () -> Unit, onPins: () -> Unit, modifier: Modifier = Modifier) {
    val bounds = remember(page) { worldContentBounds(page) }
    val spanX = 1f / zoom
    val left = minOf(bounds.left, pins.minOfOrNull { it.x } ?: bounds.left, x-spanX/2) - .2f
    val right = maxOf(bounds.right, pins.maxOfOrNull { it.x } ?: bounds.right, x+spanX/2) + .2f
    val top = minOf(bounds.top, pins.minOfOrNull { it.y } ?: bounds.top, y-spanY/2) - .2f
    val bottom = maxOf(bounds.bottom, pins.maxOfOrNull { it.y } ?: bounds.bottom, y+spanY/2) + .2f
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ChromeLine), shadowElevation = 2.dp) {
        Column(Modifier.padding(6.dp)) {
            Canvas(Modifier.size(140.dp, 82.dp).semantics { contentDescription = "Infinite canvas map. Tap to navigate" }
                .pointerInput(left, right, top, bottom) { detectTapGestures { onJump(left + it.x/size.width*(right-left), top + it.y/size.height*(bottom-top)) } }) {
                fun point(px: Float, py: Float) = Offset((px-left)/(right-left)*size.width, (py-top)/(bottom-top)*size.height)
                drawRect(Color(0xFFF5F5F3))
                page.strokes.forEach { stroke -> stroke.points.zipWithNext().forEach { (a,b) -> drawLine(Color(stroke.color), point(a.x,a.y), point(b.x,b.y), 1.dp.toPx()) } }
                page.images.forEach { drawRect(Color.LightGray, point(it.x,it.y), Size(it.width/(right-left)*size.width,it.height/(bottom-top)*size.height)) }
                page.texts.forEach { drawCircle(ChromeMuted, 2.dp.toPx(), point(it.x,it.y)) }
                pins.forEach { drawCircle(Color(0xFF287463), 3.dp.toPx(), point(it.x,it.y)) }
                drawRect(Color(0x443B79A8), point(x-spanX/2,y-spanY/2), Size(spanX/(right-left)*size.width, spanY/(bottom-top)*size.height))
                drawRect(Color(0xFF376CA3), point(x-spanX/2,y-spanY/2), Size(spanX/(right-left)*size.width, spanY/(bottom-top)*size.height), style = Stroke(1.dp.toPx()))
            }
            Text("${(zoom*100).toInt()}% · ${(x*600).toInt()}, ${(y*960).toInt()} px", fontSize = 9.sp)
            Row {
                InkToolButton(InkIcon.Search, "Back to content", onLocate, buttonSize = 40.dp)
                InkToolButton(InkIcon.Tag, "Open canvas pins", onPins, buttonSize = 40.dp)
                Text("${pins.size} pins", fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
