package com.allient.folio

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

/** Display-only processing of a freshly rendered page; originals and exported PDFs stay intact. */
internal data class ReaderImageAdjustments(
    val embolden: Int = 0,
    val sharpen: Int = 0,
    val bleach: Int = 0,
    val darken: Int = 0,
    val smoothing: Boolean = true,
)

/** Three source rows keep spatial filtering bounded without a second full-page bitmap. */
internal suspend fun adjustReaderImage(bitmap: Bitmap, settings: ReaderImageAdjustments) {
    if (settings.embolden == 0 && settings.sharpen == 0 && settings.bleach == 0 && settings.darken == 0) return
    val width = bitmap.width
    val height = bitmap.height
    var previous = IntArray(width)
    var current = IntArray(width)
    var next = IntArray(width)
    val output = IntArray(width)
    bitmap.getPixels(current, 0, width, 0, 0, width, 1)
    current.copyInto(previous)
    bitmap.getPixels(next, 0, width, 0, minOf(1, height - 1), width, 1)
    val bold = settings.embolden.coerceIn(0, 5) / 5f
    val sharp = settings.sharpen.coerceIn(0, 5) * .3f
    val bleach = settings.bleach.coerceIn(0, 100) / 100f
    val dark = settings.darken.coerceIn(0, 100) / 100f
    for (y in 0 until height) {
        currentCoroutineContext().ensureActive()
        for (x in 0 until width) {
            val left = (x - 1).coerceAtLeast(0)
            val right = (x + 1).coerceAtMost(width - 1)
            val pixel = current[x]
            val luminance = (((pixel shr 16) and 255) * .2126f + ((pixel shr 8) and 255) * .7152f + (pixel and 255) * .0722f) / 255f
            val light = ((luminance - .4f) / .6f).coerceIn(0f, 1f)
            var result = pixel and -0x1000000
            for (shift in 16 downTo 0 step 8) {
                val center = (pixel shr shift) and 255
                val above = (previous[x] shr shift) and 255
                val below = (next[x] shr shift) and 255
                val before = (current[left] shr shift) and 255
                val after = (current[right] shr shift) and 255
                var value = center + bold * (minOf(minOf(center, above, below), minOf(before, after)) - center)
                value += sharp * (center - (above + below + before + after) / 4f)
                value += bleach * light * light * 155f
                value *= 1f - dark * .65f * (1f - luminance)
                result = result or (value.roundToInt().coerceIn(0, 255) shl shift)
            }
            output[x] = result
        }
        bitmap.setPixels(output, 0, width, 0, y, width, 1)
        val spare = previous; previous = current; current = next; next = spare
        if (y + 2 < height) bitmap.getPixels(next, 0, width, 0, y + 2, width, 1) else current.copyInto(next)
    }
}
