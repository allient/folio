package com.allient.folio

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-quality page snapshots for the page-turn animation. They go through the same renderer, ink engine and image
 * resolution as the real canvas, so the sliding preview and the page that replaces it look identical.
 */
internal object PageSnapshots {
    /**
     * A [width] × [height] view of [page] as the canvas first shows it: the page is [pageWidth] × [pageHeight] pixels
     * (0 = fill the view), centred horizontally and top-aligned, on the grey desk when it is narrower (landscape).
     */
    fun render(page: NotebookPage, width: Int, height: Int, filesDir: File, pageWidth: Int = 0, pageHeight: Int = 0): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val drawWidth = if (pageWidth > 0) pageWidth.coerceAtMost(width) else width
        val drawHeight = if (pageHeight > 0) pageHeight else height
        val paths = page.images.map { it.path }.distinct()
        val edge = NotebookImageStore.edgeFor(paths.size)
        // Decoding here also warms the shared cache, so the real canvas finds the same images ready when the page lands.
        val images = paths.mapNotNull { path -> NotebookImageStore.get(path, filesDir, allowDecode = true, maxEdge = edge)?.let { path to it } }.toMap()
        val ink = JetpackInkEngine()
        try {
            val canvas = Canvas(bitmap)
            if (drawWidth < width) canvas.drawColor(PAGE_SNAPSHOT_DESK)
            // Like the canvas: lay the page out at the view's width, then scale it to its on-screen width.
            val scale = drawWidth.toFloat() / width
            canvas.translate((width - drawWidth) / 2f, 0f)
            canvas.scale(scale, scale)
            val documentHeight = (drawHeight / scale).toInt().coerceAtLeast(1)
            canvas.clipRect(0, 0, width, documentHeight)
            NotebookRenderer.drawPage(canvas, page, width, documentHeight, filesDir, allowImageDecode = false, ink = ink,
                viewportZoom = scale, imageBitmaps = images)
        } finally { ink.release() }
        return bitmap
    }
}

/** Renders [page] off the main thread; null while rendering, or when there is no page. */
@Composable
internal fun rememberPageSnapshot(page: NotebookPage?, widthPx: Int, heightPx: Int, filesDir: File, pageWidthPx: Int = 0, pageHeightPx: Int = 0): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, page, widthPx, heightPx, pageWidthPx, pageHeightPx) {
        value = null
        if (page != null && widthPx > 1 && heightPx > 1) {
            value = withContext(Dispatchers.Default) { PageSnapshots.render(page, widthPx, heightPx, filesDir, pageWidthPx, pageHeightPx).asImageBitmap() }
        }
    }

/** Same grey as the canvas desk around a narrower page (InkCanvasView). */
private const val PAGE_SNAPSHOT_DESK = 0xFFE4E4E0.toInt()
