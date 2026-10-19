package com.example.knotes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Shared decoded media; app-private imports are immutable, so absolute paths identify versions. */
internal object NotebookImageStore {
    private val images = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val main = Handler(Looper.getMainLooper())
    private val decoder = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(),
        { runnable -> Thread(runnable, "knotes-image-decoder") },
    )

    fun get(path: String, filesDir: File?, allowDecode: Boolean, maxEdge: Int = 2400): Bitmap? {
        val source = File(path).let { if (it.isAbsolute || filesDir == null) it else File(filesDir, path) }
        val edge = maxEdge.coerceIn(1, 2400)
        val key = "${source.absolutePath}@$edge"
        images.get(key)?.takeUnless { it.isRecycled }?.let { return it }
        // Drawing code uses this branch: no file access, path canonicalization, or decode.
        if (!allowDecode) return null
        return try {
            val media = filesDir?.let { File(it, "media").canonicalPath + File.separator }
            if ((media != null && !source.canonicalPath.startsWith(media)) || !source.isFile) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight).toLong() > edge.toLong() * sample) sample *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: return null
            val oriented = orient(bitmap, source)
            images.put(key, oriented)
            oriented
        } catch (_: Exception) {
            null
        }
    }

    private fun orient(decoded: Bitmap, source: File): Bitmap {
        val exif = try { ExifInterface(source) } catch (_: Exception) { return decoded }
        val degrees = exif.rotationDegrees
        val flipped = exif.isFlipped
        if (degrees == 0 && !flipped) return decoded
        // ExifInterface defines orientation as horizontal flip first, then clockwise rotation.
        val transform = Matrix().apply {
            if (flipped) setScale(-1f, 1f)
            postRotate(degrees.toFloat())
        }
        var oriented: Bitmap? = null
        try {
            val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
            oriented = result
            return result
        } finally {
            // The undecorated decode has never entered the shared cache or a pinned view.
            if (oriented !== decoded) decoded.recycle()
        }
    }

    /** Cancel suppresses callbacks and removes queued work when a thumbnail leaves composition. */
    fun preload(images: List<InkImage>, filesDir: File, maxPixels: Int = 6 * 1024 * 1024, onReady: (Map<String, Bitmap>) -> Unit): () -> Unit {
        val cancelled = AtomicBoolean(false)
        val paths = images.map { it.path }.distinct()
        // Each visible page pins its sampled images, so eviction by another page cannot
        // make its images disappear. Sampling bounds the aggregate per-owner pixel budget.
        val edge = kotlin.math.sqrt(maxPixels.toDouble() / paths.size.coerceAtLeast(1)).toInt().coerceIn(1, 2400)
        val task = FutureTask<Unit> {
            val loaded = mutableMapOf<String, Bitmap>()
            for (path in paths) {
                if (cancelled.get()) break
                get(path, filesDir, allowDecode = true, maxEdge = edge)?.let { loaded[path] = it }
            }
            main.post { if (!cancelled.get()) onReady(loaded.toMap()) }
        }
        decoder.execute(task)
        return {
            cancelled.set(true)
            task.cancel(false)
            decoder.remove(task)
        }
    }
}
