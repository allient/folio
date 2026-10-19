package com.allient.folio

import java.util.UUID

internal data class CanvasPin(val id: String = UUID.randomUUID().toString(), val name: String, val x: Float, val y: Float, val zoom: Float = 1f)

/** Preserve old vertically appended sections in their original order on one world surface. */
internal fun migrateInfiniteCanvas(note: Notebook): Notebook {
    if (!note.infiniteCanvas || note.canvasVersion >= 1) return note
    val first = note.pages.firstOrNull() ?: NotebookPage()
    fun layerId(index: Int, id: String) = if (index == 0) id else "${note.pages[index].id}/$id"
    val layers = note.pages.flatMapIndexed { index, page -> page.layers.map { it.copy(id = layerId(index, it.id)) } }
    return note.copy(canvasVersion = 1, lastViewedPageId = note.lastViewedPageId ?: first.id, pages = listOf(first.copy(
        template = "blank", layers = layers,
        strokes = note.pages.flatMapIndexed { index, page -> page.strokes.map { stroke -> stroke.copy(layerId = layerId(index, stroke.layerId), points = stroke.points.map { it.copy(y = it.y + index) }) } },
        texts = note.pages.flatMapIndexed { index, page -> page.texts.map { it.copy(y = it.y + index, layerId = layerId(index, it.layerId)) } },
        images = note.pages.flatMapIndexed { index, page -> page.images.map { it.copy(y = it.y + index, layerId = layerId(index, it.layerId)) } },
    )), pins = note.pins + note.pages.mapIndexed { index, page ->
        CanvasPin(id = page.id, name = page.outline.ifBlank { "Section ${index + 1}" }, x = .5f, y = index + .5f)
    })
}

/** Conservative bounds in normalized world coordinates, including object and nib extents. */
internal fun worldContentBounds(page: NotebookPage): android.graphics.RectF {
    val bounds = android.graphics.RectF()
    var initialized = false
    fun include(left: Float, top: Float, right: Float, bottom: Float) {
        if (!initialized) { bounds.set(left, top, right, bottom); initialized = true } else bounds.union(left, top, right, bottom)
    }
    val visible = page.layers.filter { it.visible }.map { it.id }.toSet()
    page.strokes.filter { it.layerId in visible && it.points.isNotEmpty() }.forEach { stroke ->
        val pad = stroke.width * 3f
        include(stroke.points.minOf { it.x } - pad/600f, stroke.points.minOf { it.y } - pad/960f,
            stroke.points.maxOf { it.x } + pad/600f, stroke.points.maxOf { it.y } + pad/960f)
    }
    page.images.filter { it.layerId in visible }.forEach { include(it.x,it.y,it.x+it.width,it.y+it.height) }
    page.blocks.filter { it.layerId in visible }.forEach { include(it.x,it.y,it.x+it.width,it.y+it.height) }
    page.texts.filter { it.layerId in visible }.forEach { text ->
        if (text.kind != "plain") include(text.x,text.y,text.x+text.boxWidth,text.y+.16f)
        else {
            val width = (text.text.lines().maxOfOrNull { it.length } ?: 1) * text.size/600f
            val left = text.x - when (text.alignment) { "Center" -> width/2; "End" -> width; else -> 0f }
            include(left,text.y-text.size*1.5f/960f,left+width,text.y+text.text.lines().size*text.size*text.lineSpacing*1.7f/960f)
        }
    }
    if (!initialized) bounds.set(0f,0f,1f,1f)
    return bounds
}
