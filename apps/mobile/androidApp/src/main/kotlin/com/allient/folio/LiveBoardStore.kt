package com.allient.folio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One live board in the manager. The scene itself lives in `<id>.json`, the preview in `<id>.jpg`. */
internal data class LiveBoard(val id: String, val title: String, val updatedAt: Long)

/**
 * Live boards (Excalidraw scenes) in the app's private storage:
 * `files/liveboards/index.json` lists them; each board has `<id>.json` with Excalidraw's own
 * `elements` and `appState` strings (exactly what excalidraw.com keeps in localStorage) and an
 * optional `<id>.jpg` preview. Images inserted on the board live in the page's IndexedDB and are
 * not part of a board yet.
 */
internal class LiveBoardStore(context: Context) {
    private val dir = File(context.filesDir, "liveboards").apply { mkdirs() }
    private val index = File(dir, "index.json")

    @Synchronized fun list(): List<LiveBoard> = readIndex().sortedByDescending { it.updatedAt }

    @Synchronized fun board(id: String): LiveBoard? = readIndex().firstOrNull { it.id == id }

    @Synchronized fun create(title: String): LiveBoard {
        val board = LiveBoard(UUID.randomUUID().toString(), title.ifBlank { "Untitled board" }, System.currentTimeMillis())
        writeIndex(readIndex() + board)
        return board
    }

    @Synchronized fun rename(id: String, title: String) =
        writeIndex(readIndex().map { if (it.id == id) it.copy(title = title.ifBlank { it.title }, updatedAt = System.currentTimeMillis()) else it })

    @Synchronized fun delete(id: String) {
        writeIndex(readIndex().filterNot { it.id == id })
        File(dir, "$id.json").delete()
        preview(id).delete()
    }

    /** Excalidraw's `elements` and `appState` JSON strings; an empty board for a new one. */
    @Synchronized fun scene(id: String): Pair<String, String> {
        val file = File(dir, "$id.json")
        if (!file.exists()) return "[]" to "{}"
        return runCatching { JSONObject(file.readText()).let { it.optString("elements", "[]") to it.optString("appState", "{}") } }.getOrDefault("[]" to "{}")
    }

    @Synchronized fun save(id: String, elements: String, appState: String, preview: Bitmap?) {
        if (readIndex().none { it.id == id }) return // deleted while open
        val file = File(dir, "$id.json")
        val temp = File(dir, "$id.json.tmp")
        temp.writeText(JSONObject().put("elements", elements).put("appState", appState).toString())
        if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
        preview?.let { bitmap -> preview(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        writeIndex(readIndex().map { if (it.id == id) it.copy(updatedAt = System.currentTimeMillis()) else it })
    }

    fun preview(id: String) = File(dir, "$id.jpg")

    private fun readIndex(): List<LiveBoard> = runCatching {
        val array = JSONArray(index.readText())
        (0 until array.length()).map { array.getJSONObject(it) }.map { LiveBoard(it.getString("id"), it.getString("title"), it.getLong("updatedAt")) }
    }.getOrDefault(emptyList())

    private fun writeIndex(boards: List<LiveBoard>) {
        val array = JSONArray()
        boards.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("updatedAt", it.updatedAt)) }
        index.writeText(array.toString())
    }
}

/** A board's saved preview for the library card; blank until the board has been opened once. */
@Composable
internal fun LiveBoardPreview(store: LiveBoardStore, id: String, version: Long, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, id, version) {
        value = withContext(Dispatchers.IO) { store.preview(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    }
    image?.let { Image(it, null, modifier, contentScale = ContentScale.Crop) }
}
