package com.allient.folio

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/** Upright nib silhouettes share a baseline and ink reservoir, independent of brush width. */
@Composable
internal fun BrushGlyph(name: String, color: Int, modifier: Modifier = Modifier) {
    val outline = remember(name) {
        val data = when (name) {
            "Pencil" -> "M10,26 L10,14 L16,3 L22,14 L22,26 Z M10,14 L16,17 L22,14 M16,17 L16,26 M13,9 L19,9"
            "Marker" -> "M8,26 L8,17 L10,17 L10,8 L22,4 L22,17 L24,17 L24,26 Z M10,17 L22,17 M10,11 L22,7"
            "Highlighter" -> "M6,26 L6,18 L9,18 L9,9 L23,5 L23,18 L26,18 L26,26 Z M9,18 L23,18 M9,13 L23,9"
            "Fountain pen" -> "M10,26 L7,17 L16,2 L25,17 L22,26 Z M16,3 L16,17 M10,23 L22,23"
            // Soft bristle tip: no slit, so it never reads as a fountain nib.
            "Brush pen" -> "M10,26 V20 C10,14 14,9 16,2 C18,9 22,14 22,20 V26 Z M10,20 H22 M16,6 C15,11 15,15 16,20"
            "Fineliner" -> "M10,26 L10,18 L13,18 L13,12 L15,12 L15,4 L17,4 L17,12 L19,12 L19,18 L22,18 L22,26 Z M13,18 L19,18"
            // Rubber grip ribs distinguish it from the plain ballpoint.
            "Gel pen" -> "M10,26 V13 L14,7 L16,3 L18,7 L22,13 V26 Z M10,13 H22 M10,17 H22 M10,20 H22 M10,23 H22"
            // Square-cut dip nib: the flat edge is what makes calligraphy thick-thin strokes.
            "Calligraphy" -> "M9,26 L9,17 L12,4 H20 L23,17 L23,26 Z M12,4 H20 M16,4 V14 M9,23 H23"
            // Round brush: bristle tuft, metal ferrule, handle.
            "Paint brush" -> "M16,2 C18,5 20,8 20,12 V14 H12 V12 C12,8 14,5 16,2 Z M12,14 H20 V18 H12 Z M14,18 H18 V26 H14 Z M14,8 C15,10 15,12 15,14 M18,8 C17,10 17,12 17,14"
            "Chalk" -> "M9,26 L7,7 L21,5 L24,24 Z M7,7 L12,10 L21,5 M12,10 L14,25"
            "Spray" -> "M8,26 V13 Q8,9 12,9 H20 Q24,9 24,13 V26 Z M13,9 V4 H19 V9 M19,5 H24 M27,3 H29 M28,7 H30"
            // Ballpoint: tapered cone ending in a ball.
            else -> "M11,26 V14 L15,6 H17 L21,14 V26 Z M11,14 H21"
        }
        PathParser().parsePathString(data).toPath()
    }
    val slit = remember { Path().apply { moveTo(14f, 18f); lineTo(18f, 18f) } }
    Canvas(modifier) {
        scale(size.width / 32f, size.height / 36f, pivot = Offset.Zero) {
            val edge = Color(0xFF303432)
            val stroke = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(outline, Color(0xFFF8F9F7))
            drawPath(outline, edge, style = stroke)
            when (name) {
                "Fountain pen" -> { drawCircle(edge, 1.4f, Offset(16f, 18f)); drawPath(slit, edge, style = stroke) }
                "Calligraphy" -> drawCircle(edge, 1.3f, Offset(16f, 15f))
                "Pen", "Gel pen" -> drawCircle(edge, 1.3f, Offset(16f, if (name == "Pen") 4.5f else 2.5f))
            }
            drawRoundRect(Color(color).copy(alpha = 1f), Offset(5f, 26f), Size(22f, 8f), CornerRadius(2f))
            drawRoundRect(edge, Offset(5f, 26f), Size(22f, 8f), CornerRadius(2f), style = stroke)
            drawLine(Color.White.copy(alpha = 0.55f), Offset(8f, 28.5f), Offset(24f, 28.5f), 1f)
        }
    }
}
