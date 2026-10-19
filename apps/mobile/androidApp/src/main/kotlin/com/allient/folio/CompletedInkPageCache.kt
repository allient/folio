package com.allient.folio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderNode
import androidx.annotation.RequiresApi
import java.io.File

/** Retained drawing commands in small blocks: adding ink re-records only its tail block. */
@RequiresApi(29)
internal class CompletedInkPageCache {
    private data class Block(val source: Any, val node: RenderNode)
    private val blocks = mutableMapOf<String, Block>()
    private var commands: List<RenderNode?> = emptyList()
    private var revision = -1L
    private var width = 0
    private var height = 0
    private var zoom = -1f

    fun draw(canvas: Canvas, page: NotebookPage, contentRevision: Long, w: Int, h: Int, scale: Float,
             images: Map<String, Bitmap>, filesDir: File, ink: JetpackInkEngine, activeLayerId: String,
             live: (Canvas) -> Unit) {
        if (revision != contentRevision || width != w || height != h || zoom != scale || blocks.values.any { !it.node.hasDisplayList() }) {
            val dimensionsChanged = width != w || height != h || zoom != scale
            val next = mutableListOf<RenderNode?>()
            val used = mutableSetOf<String>()
            fun record(key: String, source: Any, draw: (Canvas) -> Unit) {
                used += key
                val previous = blocks[key]
                val node = previous?.node ?: RenderNode("Folio $key")
                if (dimensionsChanged || previous?.source != source || !node.hasDisplayList()) {
                    node.setPosition(0, 0, w, h)
                    val recording = node.beginRecording(w, h)
                    try { draw(recording) } finally { node.endRecording() }
                    blocks[key] = Block(source, node)
                }
                next += node
            }
            record("paper", page.template) { target ->
                NotebookRenderer.drawPage(target, page.copy(layers = emptyList(), strokes = emptyList(), texts = emptyList(), images = emptyList(), blocks = emptyList()), w, h, filesDir, allowImageDecode = false)
            }
            for (layer in page.layers) {
                if (!layer.visible) continue
                // Objects under the ink, back to front (see PageOrder).
                val layerObjects = PageOrder.objects(page, layer.id)
                val below = layerObjects.filter { PageOrder.z(it) < PageOrder.INK }
                if (below.isNotEmpty()) record("${layer.id}/below", below to images) { target ->
                    NotebookRenderer.drawPage(target, page.copy(layers = listOf(layer), strokes = emptyList(), images = below.filterIsInstance<InkImage>(),
                        blocks = below.filterIsInstance<InkBlock>(), texts = below.filterIsInstance<InkText>()), w, h, filesDir,
                        allowImageDecode = false, imageBitmaps = images, drawBackground = false)
                }
                page.strokes.filter { it.layerId == layer.id }.chunked(32).forEachIndexed { index, strokes ->
                    record("${layer.id}/ink/$index", strokes) { target ->
                        for (stroke in strokes) NotebookRenderer.drawStroke(target, stroke, w.toFloat(), h.toFloat(), ink, scale)
                    }
                }
                if (layer.id == activeLayerId) next += null // live ink below this layer's text
                val above = layerObjects.filter { PageOrder.z(it) >= PageOrder.INK }
                if (above.isNotEmpty()) record("${layer.id}/above", above to images) { target ->
                    NotebookRenderer.drawPage(target, page.copy(layers = listOf(layer), strokes = emptyList(), images = above.filterIsInstance<InkImage>(),
                        blocks = above.filterIsInstance<InkBlock>(), texts = above.filterIsInstance<InkText>()), w, h, filesDir,
                        allowImageDecode = false, imageBitmaps = images, drawBackground = false)
                }
            }
            val iterator = blocks.iterator()
            while (iterator.hasNext()) { val entry = iterator.next(); if (entry.key !in used) { entry.value.node.discardDisplayList(); iterator.remove() } }
            commands = next; revision = contentRevision; width = w; height = h; zoom = scale
        }
        for (node in commands) if (node == null) live(canvas) else canvas.drawRenderNode(node)
    }

    fun release() { blocks.values.forEach { it.node.discardDisplayList() }; blocks.clear(); commands = emptyList(); revision = -1L }
}
