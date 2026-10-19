package com.example.knotes

import android.util.AtomicFile
import android.content.Context
import android.content.ContentValues
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

internal data class InkPoint(val x: Float, val y: Float, val pressure: Float, val elapsedMillis: Long = 0)

internal data class InkStroke(
    val brush: String,
    val color: Int,
    val width: Float,
    val points: List<InkPoint>,
    val id: String = UUID.randomUUID().toString(),
    val layerId: String = "ink",
    val tags: List<String> = emptyList(),
    val filled: Boolean = false,
    val engine: String = "legacy",
    val lineStyle: String = "solid",
    val fillColor: Int? = null,
    val texture: Float = .65f,
    val grain: Float = 1f,
    val angle: Float = 0f,
    val variant: String = "",
)

internal data class InkLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val visible: Boolean = true,
    val locked: Boolean = false,
)

internal data class InkText(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val x: Float = .15f,
    val y: Float = .15f,
    val size: Float = 22f,
    val color: Int = 0xFF252525.toInt(),
    val layerId: String = "ink",
    val font: String = "Sans",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val alignment: String = "Start",
    val lineSpacing: Float = 1f,
    val kind: String = "plain",
    val target: String = "",
    val boxWidth: Float = .32f,
)

internal data class InkImage(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val x: Float = .1f,
    val y: Float = .1f,
    val width: Float = .6f,
    val height: Float = .4f,
    val layerId: String = "ink",
)

/** Tables and sticky notes. Geometry is page-normalised like images; a table's rows and columns are fractions of its height and width. */
internal data class InkBlock(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val x: Float = .2f,
    val y: Float = .2f,
    val width: Float = .5f,
    val height: Float = .3f,
    val layerId: String = "ink",
    /** Table body fill (0 = none) or sticky-note colour. */
    val fill: Int = 0,
    val text: String = "",
    val textSize: Float = 16f,
    val rows: List<Float> = emptyList(),
    val columns: List<Float> = emptyList(),
    val headerRow: Boolean = true,
    val headerColumn: Boolean = true,
    val borderWidth: Float = 3f,
    val borderColor: Int = 0xFF8E8E93.toInt(),
    val headerFill: Int = 0xFFC0C0C3.toInt(),
    /** Merged cell ranges as [firstRow, firstColumn, lastRow, lastColumn]. */
    val merges: List<List<Int>> = emptyList(),
) { companion object { const val Table = "table"; const val Sticky = "sticky" } }

internal data class NoteAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val mimeType: String,
)

internal data class NotebookPage(
    val id: String = UUID.randomUUID().toString(),
    val strokes: List<InkStroke> = emptyList(),
    val template: String = "blank",
    val layers: List<InkLayer> = listOf(InkLayer(id = "ink", name = "Layer 1")),
    val texts: List<InkText> = emptyList(),
    val images: List<InkImage> = emptyList(),
    val outline: String = "",
    val blocks: List<InkBlock> = emptyList(),
)

internal data class Notebook(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val pages: List<NotebookPage> = listOf(NotebookPage()),
    val tags: List<String> = emptyList(),
    val attachments: List<NoteAttachment> = emptyList(),
    val folderId: String? = null,
    val favorite: Boolean = false,
    val openedAt: Long = 0,
    val lastViewedPageId: String? = null,
    /** Fixed at creation: one unbounded surface, distinct from paged notebooks. */
    val infiniteCanvas: Boolean = false,
    val canvasVersion: Int = 1,
    val pins: List<CanvasPin> = emptyList(),
    val createdAt: Long = updatedAt,
    /** Soft delete: > 0 means the notebook is in Trash. Pages and media are kept until it is deleted permanently. */
    val deletedAt: Long = 0,
)

internal class NotebookStorage(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "knotes-notebooks.json"))
    val databaseFile: File = context.getDatabasePath("knotes.db")
    private val helper = Database(context.applicationContext)
    private val cachedNotes = mutableMapOf<String, String>()
    private val cachedPages = mutableMapOf<String, String>()
    private val serializedPages = mutableMapOf<String, Pair<NotebookPage, String>>()

    /** Non-null means existing storage could not be read; save is blocked to preserve it. */
    var loadError: String? = null
        private set

    @Synchronized
    fun load(): List<Notebook> = try {
        val db = helper.writableDatabase
        migrateLegacy(db)
        val notes = mutableListOf<Notebook>()
        cachedNotes.clear()
        cachedPages.clear()
        db.query("notebooks", arrayOf("id", "payload"), null, null, null, null, "updated_at DESC").use { rows ->
            while (rows.moveToNext()) {
                val id = rows.getString(0)
                val payload = rows.getString(1)
                val json = JSONObject(payload)
                val pages = JSONArray()
                db.query("pages", arrayOf("id", "length(payload)", "position"), "notebook_id = ?", arrayOf(id), null, null, "position ASC").use { pageRows ->
                    while (pageRows.moveToNext()) {
                        val pagePayload = readPagePayload(db, pageRows.getString(0), pageRows.getLong(1))
                        pages.put(JSONObject(pagePayload))
                        cachedPages[pageRows.getString(0)] = "$id:${pageRows.getInt(2)}:$pagePayload"
                    }
                }
                cachedNotes[id] = payload
                notes.add(readNote(json.put("pages", pages)))
            }
        }
        loadError = null
        notes
    } catch (error: Exception) {
        loadError = error.message ?: "Could not read saved notebooks"
        emptyList()
    }

    private fun readPagePayload(db: SQLiteDatabase, pageId: String, characterCount: Long): String {
        require(characterCount in 1..Int.MAX_VALUE.toLong()) { "Invalid notebook page size" }
        // SQLite CursorWindow cannot hold an arbitrarily large row. Read JSON in small
        // substr chunks; SQLite length/substr both count characters, including Unicode.
        val chunkCharacters = 16_384L
        val text = StringBuilder(minOf(characterCount, chunkCharacters).toInt())
        var offset = 1L
        while (offset <= characterCount) {
            db.rawQuery(
                "SELECT substr(payload, ?, ?) FROM pages WHERE id = ?",
                arrayOf(offset.toString(), chunkCharacters.toString(), pageId),
            ).use { row ->
                check(row.moveToFirst()) { "Notebook page disappeared while reading" }
                val chunk = row.getString(0)
                check(!chunk.isNullOrEmpty()) { "Notebook page could not be read completely" }
                text.append(chunk)
            }
            offset += chunkCharacters
        }
        return text.toString()
    }

    @Synchronized
    fun save(notebooks: List<Notebook>, library: com.example.knotes.home.LibrarySettings? = null) {
        check(loadError == null) { "Saved notebooks could not be loaded. Original data preserved: $loadError" }
        require(notebooks.map { it.id }.distinct().size == notebooks.size) { "Notebook IDs must be unique" }
        val pageIds = notebooks.flatMap { note -> note.pages.map { it.id } }
        require(pageIds.distinct().size == pageIds.size) { "Page IDs must be unique across notebooks" }
        val db = helper.writableDatabase
        migrateLegacy(db)
        val nextNotes = mutableMapOf<String, String>()
        val nextPages = mutableMapOf<String, String>()
        db.beginTransaction()
        try {
            notebooks.forEach { note ->
                cachedNotes[note.id]?.let { existing ->
                    require(JSONObject(existing).optBoolean("infiniteCanvas", false) == note.infiniteCanvas) { "Canvas type is fixed when a notebook is created" }
                }
                require(!note.infiniteCanvas || (note.pages.size <= 1 && note.pages.all { it.template == "blank" || it.template.startsWith("inf-") })) { "Infinite canvases use one surface" }
                val json = writeNote(note.copy(pages = emptyList()))
                json.remove("pages")
                val metadata = json.toString()
                nextNotes[note.id] = metadata
                if (cachedNotes[note.id] != metadata) {
                    db.replaceOrThrow("notebooks", null, ContentValues().apply {
                        put("id", note.id); put("title", note.title); put("updated_at", note.updatedAt); put("payload", metadata)
                    })
                }
                note.pages.forEachIndexed { index, page ->
                    val cachedPage = serializedPages[page.id]
                    val payload = if (cachedPage?.first == page) cachedPage.second else {
                        writeNote(note.copy(pages = listOf(page))).getJSONArray("pages").getJSONObject(0).toString()
                            .also { serializedPages[page.id] = page to it }
                    }
                    // Include order and ownership so page moves also update correctly.
                    val cacheValue = "${note.id}:$index:$payload"
                    nextPages[page.id] = cacheValue
                    if (cachedPages[page.id] != cacheValue) {
                        db.replaceOrThrow("pages", null, ContentValues().apply {
                            put("id", page.id); put("notebook_id", note.id); put("position", index); put("payload", payload)
                        })
                    }
                }
            }
            db.query("pages", arrayOf("id"), null, null, null, null, null).use { rows ->
                val obsolete = mutableListOf<String>()
                while (rows.moveToNext()) if (rows.getString(0) !in nextPages) obsolete.add(rows.getString(0))
                obsolete.forEach { db.delete("pages", "id = ?", arrayOf(it)) }
            }
            db.query("notebooks", arrayOf("id"), null, null, null, null, null).use { rows ->
                val obsolete = mutableListOf<String>()
                while (rows.moveToNext()) if (rows.getString(0) !in nextNotes) obsolete.add(rows.getString(0))
                obsolete.forEach { db.delete("notebooks", "id = ?", arrayOf(it)) }
            }
            if (library != null) writeLibrary(db, library)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        cachedNotes.clear(); cachedNotes.putAll(nextNotes)
        cachedPages.clear(); cachedPages.putAll(nextPages)
        serializedPages.keys.retainAll(nextPages.keys)
    }

    @Synchronized
    fun loadLibrary(): com.example.knotes.home.LibrarySettings {
        helper.readableDatabase.rawQuery("SELECT value FROM settings WHERE key = 'library'", null).use { rows ->
            if (!rows.moveToFirst()) return com.example.knotes.home.LibrarySettings()
            val json = JSONObject(rows.getString(0))
            val folders = json.optJSONArray("folders") ?: JSONArray()
            val downloads = json.optJSONArray("downloads") ?: JSONArray()
            return com.example.knotes.home.LibrarySettings(
                folders = List(folders.length()) { index -> folders.getJSONObject(index).let {
                    com.example.knotes.home.NoteFolder(it.getString("id"), it.getString("name"), it.optString("color", "sage"), it.optString("style", "classic"), it.optLong("deletedAt", 0).coerceAtLeast(0))
                } },
                downloaded = List(downloads.length()) { downloads.getString(it) }.toSet(),
            )
        }
    }

    private fun writeLibrary(db: SQLiteDatabase, settings: com.example.knotes.home.LibrarySettings) {
        db.replaceOrThrow("settings", null, ContentValues().apply {
            put("key", "library")
            put("value", JSONObject().put("folders", JSONArray().apply {
                settings.folders.forEach { put(folderJson(it)) }
            }).put("downloads", JSONArray(settings.downloaded.toList())).toString())
        })
    }

    @Synchronized
    fun close() = helper.close()

    /** One SQLite read transaction keeps notebook and folder data from different saves apart. */
    @Synchronized
    fun loadBackupSnapshot(): ImportedLibrary {
        val db = helper.writableDatabase
        db.beginTransactionNonExclusive()
        return try {
            val notebooks = load()
            check(loadError == null) { "Could not read your notebooks. No backup was created." }
            val snapshot = ImportedLibrary(notebooks, loadLibrary())
            db.setTransactionSuccessful()
            snapshot
        } finally { db.endTransaction() }
    }

    private fun migrateLegacy(db: SQLiteDatabase) {
        db.rawQuery("SELECT value FROM settings WHERE key = 'legacy_migrated'", null).use { if (it.moveToFirst()) return }
        val notes = try {
            val root = file.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            require(root.has("notes")) { "Missing notes array in legacy storage" }
            root.objects("notes").map(::readNote)
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
            emptyList()
        }
        db.beginTransaction()
        try {
            notes.forEach { note ->
                val json = writeNote(note)
                val pages = json.getJSONArray("pages")
                json.remove("pages")
                db.insertOrThrow("notebooks", null, ContentValues().apply {
                    put("id", note.id); put("title", note.title); put("updated_at", note.updatedAt); put("payload", json.toString())
                })
                note.pages.forEachIndexed { index, page ->
                    db.insertOrThrow("pages", null, ContentValues().apply {
                        put("id", page.id); put("notebook_id", note.id); put("position", index); put("payload", pages.getJSONObject(index).toString())
                    })
                }
            }
            db.execSQL("INSERT INTO settings(key, value) VALUES('legacy_migrated', '1')")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private class Database(context: Context) : SQLiteOpenHelper(
        context, "knotes.db", null, 1,
        DatabaseErrorHandler { throw SQLiteDatabaseCorruptException("Notebook database is damaged; original files preserved") },
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE notebooks(id TEXT PRIMARY KEY, title TEXT NOT NULL, updated_at INTEGER NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE pages(id TEXT PRIMARY KEY, notebook_id TEXT NOT NULL, position INTEGER NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX pages_notebook_position ON pages(notebook_id, position)")
            db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("Unsupported notebook database upgrade: $oldVersion to $newVersion")
        }
    }

    fun serializeNotebook(note: Notebook): String = Companion.serializeNotebook(note)
    fun deserializeNotebook(json: String): Notebook = Companion.deserializeNotebook(json)

    companion object {
        /** Metadata and vectors only; portable archives must bundle image/attachment files separately. */
        fun serializeNotebook(note: Notebook): String = writeNote(note).put("version", 2).toString()
        fun deserializeNotebook(json: String): Notebook {
            val root = JSONObject(json)
            require(root.has("pages") && root.get("pages") is JSONArray) { "Invalid Folio note: missing pages" }
            return readNote(root)
        }

    private fun readNote(note: JSONObject): Notebook = Notebook(
        id = note.id(),
        title = note.optString("title", "Untitled note"),
        updatedAt = note.optLong("updatedAt", System.currentTimeMillis()).coerceAtLeast(0),
        pages = note.objects("pages").map { page ->
            val layers = page.objects("layers").map { layer ->
                InkLayer(layer.id(), layer.optString("name", "Layer"), layer.optBoolean("visible", true), layer.optBoolean("locked", false))
            }.ifEmpty { listOf(InkLayer(id = "ink", name = "Layer 1")) }
            // Older notes have implicit "ink" layers. Preserve orphaned content rather than hiding it.
            val strokes = page.objects("strokes").map { stroke ->
                InkStroke(
                    brush = stroke.optString("brush", "Pen"),
                    color = stroke.optInt("color", 0xFF252525.toInt()),
                    width = stroke.number("width", 3f, .01f, 256f),
                    points = stroke.objects("points").map { point -> InkPoint(point.coordinate("x", note), point.coordinate("y", note), point.unit("pressure"), point.optLong("elapsedMillis", 0).coerceIn(0L, 86_400_000L)) },
                    id = stroke.id(), layerId = stroke.layer(), tags = stroke.strings("tags"), filled = stroke.optBoolean("filled", false), engine = stroke.optString("engine", "legacy"), lineStyle = stroke.optString("lineStyle", "solid"),
                    fillColor = if (stroke.has("fillColor") && !stroke.isNull("fillColor")) stroke.getInt("fillColor") else null,
                    texture = stroke.number("texture", .65f, 0f, 1f), grain = stroke.number("grain", 1f, .4f, 4f),
                    angle = stroke.number("angle", 0f, -180f, 180f), variant = stroke.optString("variant", ""),
                )
            }
            val texts = page.objects("texts").map { text ->
                InkText(text.id(), text.optString("text", ""), text.coordinate("x", note, .15f), text.coordinate("y", note, .15f),
                    text.number("size", 22f, 1f, 512f), text.optInt("color", 0xFF252525.toInt()), text.layer(),
                    text.optString("font", "Sans"), text.optBoolean("bold", false), text.optBoolean("italic", false),
                    text.optBoolean("underline", false), text.optString("alignment", "Start"), text.number("lineSpacing", 1f, .6f, 2.5f),
                    text.optString("kind", "plain"), text.optString("target", ""), text.number("boxWidth", .32f, .08f, 1f))
            }
            val images = page.objects("images").map { image ->
                InkImage(image.id(), image.optString("path", ""), image.coordinate("x", note, .1f), image.coordinate("y", note, .1f),
                    image.number("width", .6f, .001f, 1f), image.number("height", .4f, .001f, 1f), image.layer())
            }
            val blocks = page.objects("blocks").map { block ->
                fun fractions(key: String) = block.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optDouble(it, 0.0).toFloat().coerceIn(.001f, 1f) } }.orEmpty()
                InkBlock(block.id(), block.optString("kind", InkBlock.Table), block.coordinate("x", note, .2f), block.coordinate("y", note, .2f),
                    block.number("width", .5f, .01f, 4f), block.number("height", .3f, .01f, 4f), block.layer(), block.optInt("fill", 0),
                    block.optString("text", ""), block.number("textSize", 16f, 6f, 96f), fractions("rows"), fractions("columns"),
                    block.optBoolean("headerRow", true), block.optBoolean("headerColumn", true), block.number("borderWidth", 3f, 0f, 16f),
                    block.optInt("borderColor", 0xFF8E8E93.toInt()), block.optInt("headerFill", 0xFFC0C0C3.toInt()),
                    block.optJSONArray("merges")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.takeIf { it.length() == 4 }?.let { m -> List(4) { k -> m.optInt(k) } } } }.orEmpty())
            }
            val referencedLayers = (strokes.map { it.layerId } + texts.map { it.layerId } + images.map { it.layerId } + blocks.map { it.layerId }).distinct()
            val recoveredLayers = referencedLayers.filter { id -> layers.none { it.id == id } }.map { InkLayer(id = it, name = "Recovered layer") }
            NotebookPage(page.id(), strokes, page.optString("template", "blank"), layers + recoveredLayers, texts, images, page.optString("outline", ""), blocks)
        }.ifEmpty { listOf(NotebookPage()) },
        folderId = note.optString("folderId", "").ifBlank { null },
        favorite = note.optBoolean("favorite", false),
        openedAt = note.optLong("openedAt", 0),
        lastViewedPageId = note.optString("lastViewedPageId", "").ifBlank { null },
        infiniteCanvas = note.optBoolean("infiniteCanvas", false),
        canvasVersion = note.optInt("canvasVersion", 0),
        createdAt = note.optLong("createdAt", 0).takeIf { it > 0 } ?: note.optLong("updatedAt", System.currentTimeMillis()).coerceAtLeast(0),
        deletedAt = note.optLong("deletedAt", 0).coerceAtLeast(0),
        pins = note.objects("pins").map { CanvasPin(it.id(), it.optString("name", "Pin"), it.number("x", .5f, -100000f, 100000f), it.number("y", .5f, -100000f, 100000f), it.number("zoom", 1f, .1f, 5f)) },
        tags = note.strings("tags"),
        attachments = note.objects("attachments").map {
            NoteAttachment(it.id(), it.optString("name", "Attachment"), it.optString("path", ""), it.optString("mimeType", "application/octet-stream"))
        },
    ).let { if (it.infiniteCanvas && it.canvasVersion == 0) migrateInfiniteCanvas(it) else it }

    private fun writeNote(note: Notebook): JSONObject = JSONObject()
        .put("id", note.id).put("title", note.title).put("updatedAt", note.updatedAt)
        .put("lastViewedPageId", note.lastViewedPageId ?: "")
        .put("infiniteCanvas", note.infiniteCanvas)
        .put("canvasVersion", note.canvasVersion)
        .put("createdAt", note.createdAt).put("deletedAt", note.deletedAt)
        .put("pins", array(note.pins.map { JSONObject().put("id", it.id).put("name", it.name).put("x", it.x).put("y", it.y).put("zoom", it.zoom) }))
        .put("folderId", note.folderId ?: "").put("favorite", note.favorite).put("openedAt", note.openedAt)
        .put("tags", array(note.tags)).put("attachments", array(note.attachments.map {
            JSONObject().put("id", it.id).put("name", it.name).put("path", it.path).put("mimeType", it.mimeType)
        })).put("pages", array(note.pages.map { page ->
            JSONObject().put("id", page.id).put("template", page.template).put("outline", page.outline)
                .put("layers", array(page.layers.map { layer ->
                    JSONObject().put("id", layer.id).put("name", layer.name).put("visible", layer.visible).put("locked", layer.locked)
                })).put("strokes", array(page.strokes.map { stroke ->
                    JSONObject().put("id", stroke.id).put("layerId", stroke.layerId).put("tags", array(stroke.tags))
                        .put("texture", stroke.texture).put("grain", stroke.grain).put("angle", stroke.angle).put("variant", stroke.variant)
                        .put("engine", stroke.engine).put("filled", stroke.filled).put("fillColor", stroke.fillColor ?: JSONObject.NULL).put("lineStyle", stroke.lineStyle).put("brush", stroke.brush).put("color", stroke.color).put("width", finite(stroke.width, 3f).toDouble())
                        .put("points", array(stroke.points.map { point ->
                            JSONObject().put("x", finite(point.x).toDouble()).put("y", finite(point.y).toDouble()).put("pressure", finite(point.pressure).toDouble()).put("elapsedMillis", point.elapsedMillis)
                        }))
                })).put("texts", array(page.texts.map { text ->
                    JSONObject().put("id", text.id).put("text", text.text).put("x", finite(text.x).toDouble()).put("y", finite(text.y).toDouble())
                        .put("size", finite(text.size, 22f).toDouble()).put("color", text.color).put("layerId", text.layerId)
                        .put("font", text.font).put("bold", text.bold).put("italic", text.italic).put("underline", text.underline)
                        .put("alignment", text.alignment).put("lineSpacing", finite(text.lineSpacing, 1f).toDouble())
                        .put("kind", text.kind).put("target", text.target).put("boxWidth", finite(text.boxWidth, .32f).toDouble())
                })).put("images", array(page.images.map { image ->
                    JSONObject().put("id", image.id).put("path", image.path).put("x", finite(image.x).toDouble()).put("y", finite(image.y).toDouble())
                        .put("width", finite(image.width, .6f).toDouble()).put("height", finite(image.height, .4f).toDouble()).put("layerId", image.layerId)
                })).put("blocks", array(page.blocks.map { block ->
                    JSONObject().put("id", block.id).put("kind", block.kind).put("x", finite(block.x).toDouble()).put("y", finite(block.y).toDouble())
                        .put("width", finite(block.width, .5f).toDouble()).put("height", finite(block.height, .3f).toDouble()).put("layerId", block.layerId)
                        .put("fill", block.fill).put("text", block.text).put("textSize", finite(block.textSize, 16f).toDouble())
                        .put("rows", array(block.rows.map { finite(it, 1f).toDouble() })).put("columns", array(block.columns.map { finite(it, 1f).toDouble() }))
                        .put("headerRow", block.headerRow).put("headerColumn", block.headerColumn).put("borderWidth", finite(block.borderWidth, 3f).toDouble())
                        .put("borderColor", block.borderColor).put("headerFill", block.headerFill)
                        .put("merges", array(block.merges.map { array(it) }))
                }))
        }))

    private fun JSONObject.id() = optString("id", "").ifBlank { UUID.randomUUID().toString() }
    private fun JSONObject.layer() = optString("layerId", "ink").ifBlank { "ink" }
    private fun JSONObject.unit(key: String, default: Float = 0f) = number(key, default, 0f, 1f)
    private fun JSONObject.coordinate(key: String, note: JSONObject, default: Float = 0f) =
        if (note.optBoolean("infiniteCanvas", false)) number(key, default, -100000f, 100000f) else unit(key, default)
    private fun JSONObject.number(key: String, default: Float, min: Float, max: Float): Float =
        finite(optDouble(key, default.toDouble()).toFloat(), default).coerceIn(min, max)
    private fun finite(value: Float, default: Float = 0f) = if (value.isFinite()) value else default
    private fun JSONObject.objects(key: String): List<JSONObject> {
        if (!has(key)) return emptyList()
        val items = getJSONArray(key)
        return List(items.length()) { items.getJSONObject(it) }
    }
    private fun JSONObject.strings(key: String): List<String> {
        if (!has(key)) return emptyList()
        val items = getJSONArray(key)
        return List(items.length()) { items.getString(it) }.filter { it.isNotBlank() }.distinct()
    }
    private fun array(items: List<*>): JSONArray = JSONArray().apply { items.forEach { put(it) } }
}
}

/** Shared by SQLite, library backups and the backup signature. deletedAt is written only for folders in Trash. */
internal fun folderJson(folder: com.example.knotes.home.NoteFolder): JSONObject = JSONObject().put("id", folder.id).put("name", folder.name)
    .put("color", folder.color).put("style", folder.style).apply { if (folder.deletedAt > 0) put("deletedAt", folder.deletedAt) }

internal fun serializeNotebook(note: Notebook): String = NotebookStorage.serializeNotebook(note)
internal fun deserializeNotebook(json: String): Notebook = NotebookStorage.deserializeNotebook(json)
