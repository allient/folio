package com.example.knotes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.OpenableColumns
import com.example.knotes.reader.*
import kotlinx.coroutines.ensureActive
import org.readium.r2.shared.publication.services.coverFitting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.Closeable
import java.security.MessageDigest
import java.util.UUID

/** All methods run on a storage dispatcher. Each annotation is committed independently in WAL. */
internal class ReaderStore(private val context: Context) : Closeable {
    private val helper = object : SQLiteOpenHelper(context, "knotes-reader.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE documents (id TEXT PRIMARY KEY, hash TEXT NOT NULL UNIQUE, payload TEXT NOT NULL, opened_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE annotations (id TEXT PRIMARY KEY, document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE, payload TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX annotations_document ON annotations(document_id, created_at)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Unsupported reader database migration") }
    }.apply { setWriteAheadLoggingEnabled(true) }

    val documentsDir = File(context.filesDir, "media/reader").apply { mkdirs() }
    fun documents(): List<ReaderDocument> = helper.readableDatabase.rawQuery("SELECT payload FROM documents ORDER BY opened_at DESC", null).use { rows ->
        buildList { while (rows.moveToNext()) add(readDocument(JSONObject(rows.getString(0)))) }
    }
    fun document(id: String): ReaderDocument? = helper.readableDatabase.rawQuery("SELECT payload FROM documents WHERE id = ?", arrayOf(id)).use { if (it.moveToFirst()) readDocument(JSONObject(it.getString(0))) else null }
    fun annotations(id: String): List<ReaderAnnotation> = helper.readableDatabase.rawQuery("SELECT payload FROM annotations WHERE document_id = ? ORDER BY created_at", arrayOf(id)).use { rows ->
        buildList { while (rows.moveToNext()) add(readAnnotation(JSONObject(rows.getString(0)))) }
    }
    fun saveDocument(doc: ReaderDocument) {
        val values = ContentValues().apply { put("id", doc.id); put("hash", doc.contentHash); put("payload", writeDocument(doc).toString()); put("opened_at", doc.openedAt) }
        // UPDATE, rather than REPLACE, preserves foreign-key children.
        if (helper.writableDatabase.update("documents", values, "id = ?", arrayOf(doc.id)) == 0) helper.writableDatabase.insertOrThrow("documents", null, values)
        LocalBackupScheduler.changed(context)
    }
    fun saveAnnotation(annotation: ReaderAnnotation) {
        require(annotation.anchor.length <= 2_000_000 && annotation.note.length <= 100_000 && annotation.quote.length <= 100_000) { "Annotation is too large." }
        val doc = requireNotNull(document(annotation.documentId)) { "Document no longer exists" }
        val anchor = JSONObject(annotation.anchor)
        if (doc.format == "pdf") {
            require(anchor.getInt("page") in 0 until doc.pageCount) { "Invalid annotation page" }
            for (key in listOf("rects", "points")) anchor.optJSONArray(key)?.let { entries ->
                require(entries.length() <= 10000) { "Annotation contains too many points" }
                for (i in 0 until entries.length()) {
                    val coordinates = entries.getJSONArray(i)
                    require(coordinates.length() == if (key == "rects") 4 else 2) { "Invalid annotation geometry" }
                    for (j in 0 until coordinates.length()) require(coordinates.getDouble(j).isFinite()) { "Invalid annotation coordinates" }
                }
            }
            if (annotation.kind == AnnotationKind.HIGHLIGHT || annotation.kind == AnnotationKind.UNDERLINE) require((anchor.optJSONArray("rects")?.length() ?: 0) > 0) { "Highlight has no text geometry" }
            if (annotation.kind == AnnotationKind.INK) require((anchor.optJSONArray("points")?.length() ?: 0) >= 2) { "Ink has no stroke geometry" }
        } else require(org.readium.r2.shared.publication.Locator.fromJSON(anchor) != null) { "Invalid EPUB locator" }
        helper.readableDatabase.rawQuery("SELECT document_id FROM annotations WHERE id = ?", arrayOf(annotation.id)).use {
            require(!it.moveToFirst() || it.getString(0) == annotation.documentId) { "Annotation belongs to another document" }
        }
        helper.writableDatabase.insertWithOnConflict("annotations", null, ContentValues().apply {
            put("id", annotation.id); put("document_id", annotation.documentId); put("payload", writeAnnotation(annotation).toString()); put("created_at", annotation.createdAt)
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "Could not save annotation" } }
        LocalBackupScheduler.changed(context)
    }
    fun deleteAnnotation(id: String) { helper.writableDatabase.delete("annotations", "id = ?", arrayOf(id)); LocalBackupScheduler.changed(context) }
    fun deleteDocument(id: String) {
        // Keep content-addressed originals for now: an in-flight export or backup may still use them.
        helper.writableDatabase.delete("documents", "id = ?", arrayOf(id))
        LocalBackupScheduler.changed(context)
    }
    fun updatePosition(id: String, locator: String, progress: Double) {
        val doc = document(id) ?: return
        saveDocument(doc.copy(locator = locator, progress = progress.coerceIn(0.0, 1.0), openedAt = System.currentTimeMillis()))
    }
    suspend fun importDocument(uri: Uri, targetFolderId: String? = null): ReaderDocument = withContext(Dispatchers.IO) {
        var name = "Document"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val staged = File(documentsDir, "${UUID.randomUUID()}.partial")
        var created: File? = null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open document" }.use { input -> staged.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= 128L * 1024 * 1024) { "Documents must be smaller than 128 MiB." }
                    digest.update(buffer, 0, count); output.write(buffer, 0, count)
                }
                output.fd.sync()
            } }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val existing = documents().firstOrNull { it.contentHash == hash }
            if (existing != null && File(existing.path).isFile) {
                // Importing a book again takes it out of Trash.
                val assigned = existing.copy(folderId = targetFolderId ?: existing.folderId, deletedAt = 0)
                if (assigned != existing) saveDocument(assigned)
                return@withContext assigned
            }
            val header = ByteArray(5); staged.inputStream().use { it.read(header) }
            val format = if (String(header, Charsets.US_ASCII).startsWith("%PDF-")) "pdf" else "epub"
            if (format == "epub") validateEpub(staged)
            val local = File(documentsDir, "$hash.$format")
            if (!local.exists()) { check(staged.renameTo(local)) { "Could not keep original document" }; created = local }
            val base = ReaderDocument(existing?.id ?: UUID.randomUUID().toString(), name.substringBeforeLast('.', name), format = format, path = local.absolutePath, contentHash = hash, sourceUri = uri.toString(), folderId = targetFolderId ?: existing?.folderId, favorite = existing?.favorite ?: false, createdAt = existing?.createdAt?.takeIf { it > 0 } ?: System.currentTimeMillis())
            val doc = if (format == "pdf") PdfReaderSession(local).use { pdf ->
                val cover = File(documentsDir, "$hash.jpg")
                val bitmap = pdf.render(0, 320)
                try { cover.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) } } finally { bitmap.recycle() }
                base.copy(pageCount = pdf.pageCount, coverPath = cover.absolutePath)
            } else {
                val publication = EpubReaderEngine.open(context, local)
                try {
                    val cover = File(documentsDir, "$hash.jpg")
                    val bitmap = publication.coverFitting(android.util.Size(320, 480))
                    if (bitmap != null) try { cover.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) } } finally { bitmap.recycle() }
                    base.copy(title = publication.metadata.title ?: base.title, author = publication.metadata.authors.joinToString { it.name }, coverPath = if (cover.isFile) cover.absolutePath else "")
                } finally { publication.close() }
            }
            val restored = existing?.let { doc.copy(locator=it.locator, progress=it.progress, openedAt=it.openedAt, sourceUri=uri.toString()) } ?: doc
            saveDocument(restored)
            created = null
            restored
        } finally { staged.delete(); created?.delete() }
    }

    /** Reject path traversal and expansion bombs before handing an EPUB to the navigator. */
    private fun validateEpub(file: File) {
        java.util.zip.ZipFile(file).use { zip ->
            require(zip.getEntry("META-INF/container.xml") != null) { "This file is not an EPUB book. Import a PDF or DRM-free EPUB." }
            var expanded = 0L; var entries = 0
            zip.entries().asSequence().forEach { entry ->
                require(++entries <= 20_000 && !entry.name.startsWith('/') && !entry.name.contains('\\') && entry.name.split('/').none { it == ".." }) { "Invalid EPUB archive" }
                require(entry.size in 0..64L * 1024 * 1024 || entry.isDirectory) { "EPUB resource is too large" }
                expanded += entry.size.coerceAtLeast(0)
                require(expanded <= 512L * 1024 * 1024) { "EPUB expands beyond the 512 MiB limit" }
            }
        }
    }

    fun snapshot(): JSONObject = JSONObject().put("version", 2).put("documents", JSONArray().apply {
        documents().forEach { doc -> put(writeDocument(doc)
            .put("path", "")
            .put("cover", JSONObject().put("path", ""))
            .put("sourceUri", doc.sourceUri)
            .put("annotations", JSONArray().apply { annotations(doc.id).forEach { put(writeAnnotation(it)) } })) }
    })
    fun restore(snapshot: JSONObject) {
        val version = snapshot.getInt("version")
        require(version in 1..2) { "Unsupported reader backup" }
        val docs = snapshot.getJSONArray("documents")
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val existing = documents().associateBy { it.contentHash }
            val hashes = mutableSetOf<String>()
            for (index in 0 until docs.length()) {
                val json = docs.getJSONObject(index); val incoming = readDocument(json)
                require(incoming.format in setOf("pdf", "epub") && hashes.add(incoming.contentHash)) { "Invalid or duplicate reader document" }
                if (version == 1) {
                    require(File(incoming.path).canonicalPath.startsWith(File(context.filesDir, "media").canonicalPath + File.separator) && File(incoming.path).isFile) { "Reader original is missing" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    File(incoming.path).inputStream().use { input -> val bytes = ByteArray(65536); while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) } }
                    require(digest.digest().joinToString("") { "%02x".format(it) } == incoming.contentHash) { "Reader original failed its integrity check" }
                } else require(incoming.path.isBlank()) { "Reader metadata backup must not contain a document path." }
                val found = existing[incoming.contentHash]
                val id = found?.id ?: UUID.randomUUID().toString()
                // A book that was in Trash stays there; its retention period restarts now.
                if (found == null) saveDocument(incoming.copy(id = id, path = "", coverPath = "", deletedAt = if (incoming.deletedAt > 0) System.currentTimeMillis() else 0))
                else saveDocument(found.copy(folderId = incoming.folderId))
                val savedAnnotations = json.getJSONArray("annotations")
                val known = annotations(id).map { it.id }.toSet()
                for (a in 0 until savedAnnotations.length()) {
                    val annotation = readAnnotation(savedAnnotations.getJSONObject(a))
                    if (annotation.id !in known) saveAnnotation(annotation.copy(documentId = id))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    override fun close() { helper.close() }

    companion object {
        fun writeDocument(d: ReaderDocument): JSONObject = JSONObject().put("id", d.id).put("title", d.title).put("author", d.author).put("format", d.format)
            .put("path", d.path).put("cover", JSONObject().put("path", d.coverPath)).put("hash", d.contentHash).put("pageCount", d.pageCount).put("locator", d.locator).put("progress", d.progress).put("openedAt", d.openedAt).put("sourceUri",d.sourceUri).put("folderId", d.folderId)
            .put("favorite", d.favorite).put("createdAt", d.createdAt).put("deletedAt", d.deletedAt)
        fun readDocument(j: JSONObject) = ReaderDocument(j.getString("id"), j.getString("title"), j.optString("author"), j.getString("format"), j.optString("path"), j.optJSONObject("cover")?.optString("path").orEmpty(), j.getString("hash"), j.optInt("pageCount"), j.optString("locator"), j.optDouble("progress", 0.0), j.optLong("openedAt"), j.optString("sourceUri"), j.optString("folderId").takeIf { it.isNotBlank() },
            j.optBoolean("favorite", false), j.optLong("createdAt", 0).coerceAtLeast(0), j.optLong("deletedAt", 0).coerceAtLeast(0))
        fun writeAnnotation(a: ReaderAnnotation): JSONObject = JSONObject().put("id", a.id).put("documentId", a.documentId).put("kind", a.kind.name).put("anchor", a.anchor).put("quote", a.quote).put("note", a.note).put("color", a.color).put("createdAt", a.createdAt).put("updatedAt", a.updatedAt)
        fun readAnnotation(j: JSONObject) = ReaderAnnotation(j.getString("id"), j.getString("documentId"), AnnotationKind.valueOf(j.getString("kind")), j.getString("anchor"), j.optString("quote"), j.optString("note"), j.getInt("color"), j.getLong("createdAt"), j.getLong("updatedAt"))
    }
}

internal fun isReaderDocument(context: Context, uri: Uri): Boolean {
    val mime = context.contentResolver.getType(uri)
    if (mime == "application/pdf" || mime == "application/epub+zip") return true
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst() && it.getString(0)?.substringAfterLast('.')?.lowercase() in setOf("pdf", "epub")) return true
    }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        val header=ByteArray(5); input.read(header)==5 && String(header, Charsets.US_ASCII)=="%PDF-"
    } ?: false
}
