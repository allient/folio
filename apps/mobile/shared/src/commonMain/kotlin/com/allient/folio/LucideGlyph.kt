package com.allient.folio

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/** Stroke width of Folio's line icons on a 24-unit grid; Lucide ships 2, the custom ink icons use 1.5. */
const val FolioIconStroke = 1.5f

/**
 * Draws a Lucide [icon] in Folio's style: [tint] lines at [FolioIconStroke]. [fill], when set, paints
 * the icon's shapes underneath (for example a yellow favourite star).
 */
@Composable
fun LucideGlyph(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = FolioBrand.Ink, fill: Color? = null) {
    val styled = remember(icon, tint, fill) { icon.restyled(tint, fill) }
    Image(rememberVectorPainter(styled), contentDescription = null, modifier = modifier)
}

private fun ImageVector.restyled(stroke: Color, fill: Color?): ImageVector {
    val builder = ImageVector.Builder(name, defaultWidth, defaultHeight, viewportWidth, viewportHeight)
    fun copy(group: VectorGroup) {
        group.forEach { node ->
            when (node) {
                is VectorPath -> builder.addPath(
                    pathData = node.pathData, pathFillType = node.pathFillType, name = node.name,
                    fill = fill?.let(::SolidColor) ?: node.fill, fillAlpha = node.fillAlpha,
                    stroke = SolidColor(stroke), strokeAlpha = node.strokeAlpha, strokeLineWidth = FolioIconStroke,
                    strokeLineCap = node.strokeLineCap, strokeLineJoin = node.strokeLineJoin, strokeLineMiter = node.strokeLineMiter,
                )
                is VectorGroup -> {
                    builder.addGroup(node.name, node.rotation, node.pivotX, node.pivotY, node.scaleX, node.scaleY,
                        node.translationX, node.translationY, node.clipPathData)
                    copy(node)
                    builder.clearGroup()
                }
            }
        }
    }
    copy(root)
    return builder.build()
}
