package com.example.knotes

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
                val node = previous?.node ?: RenderNode("Knotes $key")
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
                val layerImages = page.images.filter { it.layerId == layer.id }
                if (layerImages.isNotEmpty()) record("${layer.id}/images", layerImages to images) { target ->
                    NotebookRenderer.drawPage(target, page.copy(layers = listOf(layer), strokes = emptyList(), texts = emptyList(), images = layerImages, blocks = emptyList()), w, h, filesDir,
                        allowImageDecode = false, imageBitmaps = images, drawBackground = false)
                }
                val layerBlocks = page.blocks.filter { it.layerId == layer.id }
                if (layerBlocks.isNotEmpty()) record("${layer.id}/blocks", layerBlocks) { target ->
                    NotebookRenderer.drawPage(target, page.copy(layers = listOf(layer), strokes = emptyList(), texts = emptyList(), images = emptyList(), blocks = layerBlocks), w, h, filesDir,
                        allowImageDecode = false, drawBackground = false)
                }
                page.strokes.filter { it.layerId == layer.id }.chunked(32).forEachIndexed { index, strokes ->
                    record("${layer.id}/ink/$index", strokes) { target ->
                        for (stroke in strokes) NotebookRenderer.drawStroke(target, stroke, w.toFloat(), h.toFloat(), ink, scale)
                    }
                }
                if (layer.id == activeLayerId) next += null // live ink below this layer's text
                val texts = page.texts.filter { it.layerId == layer.id }
                if (texts.isNotEmpty()) record("${layer.id}/text", texts) { target ->
                    NotebookRenderer.drawPage(target, page.copy(layers = listOf(layer), strokes = emptyList(), images = emptyList(), texts = texts, blocks = emptyList()), w, h, filesDir,
                        allowImageDecode = false, drawBackground = false)
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
