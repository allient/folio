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
    fun render(page: NotebookPage, width: Int, height: Int, filesDir: File): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paths = page.images.map { it.path }.distinct()
        val edge = NotebookImageStore.edgeFor(paths.size)
        // Decoding here also warms the shared cache, so the real canvas finds the same images ready when the page lands.
        val images = paths.mapNotNull { path -> NotebookImageStore.get(path, filesDir, allowDecode = true, maxEdge = edge)?.let { path to it } }.toMap()
        val ink = JetpackInkEngine()
        try {
            NotebookRenderer.drawPage(Canvas(bitmap), page, width, height, filesDir, allowImageDecode = false, ink = ink, viewportZoom = 1f, imageBitmaps = images)
        } finally { ink.release() }
        return bitmap
    }
}

/** Renders [page] off the main thread; null while rendering, or when there is no page. */
@Composable
internal fun rememberPageSnapshot(page: NotebookPage?, widthPx: Int, heightPx: Int, filesDir: File): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, page, widthPx, heightPx) {
        value = null
        if (page != null && widthPx > 1 && heightPx > 1) {
            value = withContext(Dispatchers.Default) { PageSnapshots.render(page, widthPx, heightPx, filesDir).asImageBitmap() }
        }
    }
