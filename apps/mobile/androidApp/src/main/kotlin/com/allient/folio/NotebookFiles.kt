package com.allient.folio

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream

/**
 * Folio's file format. Exports and backups are written as `.folio` with the Folio archive id;
 * archives from before the rename (`.knotes`, `application/vnd.knotes.archive+zip`) still import.
 */
internal object FolioFormat {
    const val EXTENSION = "folio"
    const val LEGACY_EXTENSION = "knotes"
    const val ARCHIVE = "application/vnd.folio.archive+zip"
    const val LEGACY_ARCHIVE = "application/vnd.knotes.archive+zip"
    val archiveFormats = setOf(ARCHIVE, LEGACY_ARCHIVE)
    const val NOTEBOOK_SCHEMA = "urn:folio:schema:notebook:2"
    const val LIBRARY_SCHEMA = "urn:folio:schema:library:1"
}

internal data class ImportedLibrary(
    val notebooks: List<Notebook>,
    val settings: com.allient.folio.home.LibrarySettings,
)

/** Import and export operations use only app storage and user-selected document URIs. */
internal class NotebookFiles(private val context: Context) {
    private val mediaDir get() = File(context.filesDir, "media").apply { mkdirs() }
    private val exportDir get() = File(context.cacheDir, "exports").apply { mkdirs() }

    fun exportPdf(note: Notebook): File = exportFile(note.title, "pdf").also { output ->
        try {
            val document = PdfDocument()
            try {
                note.pages.forEachIndexed { index, page ->
                    val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(1200, 1920, index + 1).create())
                    try { drawExportPage(pdfPage.canvas, note, page) }
                    finally { document.finishPage(pdfPage) }
                }
                output.outputStream().use(document::writeTo)
            } finally { document.close() }
        } catch (error: Throwable) { output.delete(); throw error }
    }

    fun exportPng(note: Notebook, page: NotebookPage): File = exportFile(note.title, "png").also { output ->
        val bitmap = Bitmap.createBitmap(1200, 1920, Bitmap.Config.ARGB_8888)
        try {
            drawExportPage(android.graphics.Canvas(bitmap), note, page)
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } catch (error: Throwable) { output.delete(); throw error }
        finally { bitmap.recycle() }
    }

    private fun drawExportPage(canvas: android.graphics.Canvas, note: Notebook, page: NotebookPage) {
        if (!note.infiniteCanvas) { NotebookRenderer.drawPage(canvas, page, 1200, 1920, context.filesDir); return }
        val bounds = worldContentBounds(page)
        val scale = minOf(1120f/(bounds.width()*600f).coerceAtLeast(1f),1840f/(bounds.height()*960f).coerceAtLeast(1f))
        canvas.drawColor(com.allient.folio.InfinitePaper.parse(page.template)?.background ?: Color.WHITE)
        canvas.save()
        try {
            canvas.translate(600f-bounds.centerX()*600f*scale,960f-bounds.centerY()*960f*scale)
            canvas.scale(scale,scale)
            NotebookRenderer.drawInfinitePattern(canvas,page.template,600f,bounds.left*600f,bounds.top*960f,bounds.right*600f,bounds.bottom*960f,scale)
            NotebookRenderer.drawPage(canvas,page,600,960,context.filesDir,drawBackground=false)
        } finally { canvas.restore() }
    }

    /** A .folio ZIP package (legacy .knotes packages import the same way). JSON uses the standard Zstandard frame format; legacy ZIP exports remain importable. */
    fun exportBackup(note: Notebook): File {
        val json = JSONObject(serializeNotebook(note))
        val assets = linkedMapOf<String, File>()
        visitPaths(json) { path ->
            val source = File(path)
            require(source.isFile && source.canonicalPath.startsWith(mediaDir.canonicalPath + File.separator)) {
                "A notebook attachment is missing."
            }
            val existing = assets.entries.firstOrNull { it.value.absolutePath == source.absolutePath }?.key
            val entry = existing ?: "assets/${UUID.randomUUID()}_${safeName(source.name).replace("..", "_")}".also { assets[it] = source }
            entry
        }
        // Every successful export must fit the same limits used by our importer.
        require(assets.size + 2 <= MAX_BACKUP_ENTRIES) { "Backup contains too many files to restore." }
        val jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
        require(jsonBytes.size.toLong() <= MAX_JSON_BYTES) { "Notebook content exceeds the 32 MiB backup limit." }
        assets.values.forEach { source ->
            require(source.length() <= MAX_ASSET_BYTES) { "An attachment exceeds the 128 MiB backup limit: ${source.name}" }
        }
        require(jsonBytes.size.toLong() + assets.values.sumOf { it.length() } <= MAX_BACKUP_BYTES) { "Notebook backup exceeds the 2 GiB restore limit." }
        val manifest = archiveManifest("notebook", 1).put("notebookPath", "notebook.json.zst")
        return exportFile(note.title, FolioFormat.EXTENSION).also { output ->
            writeZip(output, linkedMapOf(
                "manifest.json" to manifest.toString().toByteArray(Charsets.UTF_8),
                "notebook.json.zst" to Zstd.compress(jsonBytes, ZSTD_LEVEL),
            ), assets)
        }
    }

    /** One portable archive contains the whole local library, its folders, and all referenced assets. */
    fun exportLibraryBackup(notebooks: List<Notebook>, settings: com.allient.folio.home.LibrarySettings): File {
        require(notebooks.map { it.id }.distinct().size == notebooks.size) { "Notebook IDs must be unique." }
        val files = linkedMapOf<String, ByteArray>()
        val assets = linkedMapOf<String, File>()
        val index = JSONArray()
        val readerSnapshot = ReaderStore(context).use { it.snapshot() }
        var totalJsonBytes = 0L
        notebooks.forEach { note ->
            val json = JSONObject(serializeNotebook(note))
            visitPaths(json) { path ->
                val source = File(path)
                require(source.isFile && source.canonicalPath.startsWith(mediaDir.canonicalPath + File.separator)) {
                    "A notebook attachment is missing."
                }
                val assetPath = assets.entries.firstOrNull { it.value.canonicalPath == source.canonicalPath }?.key
                    ?: "assets/${UUID.randomUUID()}_${safeName(source.name).replace("..", "_")}".also { assets[it] = source }
                assetPath
            }
            val path = "notebooks/${safeName(note.id)}/notebook.json.zst"
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            totalJsonBytes += bytes.size
            require(totalJsonBytes <= MAX_JSON_BYTES) { "Library notebook data exceeds the 32 MiB backup limit." }
            files[path] = Zstd.compress(bytes, ZSTD_LEVEL)
            index.put(JSONObject().put("id", note.id).put("path", path))
        }
        val library = JSONObject()
            .put("schemaVersion", 1)
            .put("folders", JSONArray().apply { settings.folders.forEach { put(folderJson(it)) } })
            .put("downloadedTemplates", JSONArray().apply { settings.downloaded.sorted().forEach(::put) })
            .put("notebooks", index)
            .put("reader", readerSnapshot)
        val libraryBytes = library.toString().toByteArray(Charsets.UTF_8)
        require(totalJsonBytes + libraryBytes.size <= MAX_JSON_BYTES) { "Library data exceeds the 32 MiB backup limit." }
        files["library.json.zst"] = Zstd.compress(libraryBytes, ZSTD_LEVEL)
        require(files.size + assets.size + 1 <= MAX_BACKUP_ENTRIES) { "Library backup contains too many files." }
        require(assets.values.all { it.length() <= MAX_ASSET_BYTES }) { "An attachment exceeds the 128 MiB backup limit." }
        require(totalJsonBytes + libraryBytes.size + assets.values.sumOf { it.length() } <= MAX_BACKUP_BYTES) {
            "Library backup exceeds the 2 GiB limit."
        }
        val manifest = archiveManifest("library", notebooks.size)
            .put("libraryPath", "library.json.zst")
            .put("notebookCount", notebooks.size)
        return exportFile("Folio Library Backup", FolioFormat.EXTENSION).also { output ->
            writeZip(output, linkedMapOf("manifest.json" to manifest.toString().toByteArray(Charsets.UTF_8)) + files, assets)
        }
    }

    /**
     * Upper-bound size of a library archive, used to check free space before writing: uncompressed
     * notebook JSON plus each distinct notebook asset. Reader originals are never part of an archive.
     */
    fun estimateLibraryBackupBytes(notebooks: List<Notebook>): Long {
        val assets = mutableSetOf<String>()
        var total = 64L * 1024
        notebooks.forEach { note ->
            val json = JSONObject(serializeNotebook(note))
            total += json.toString().length
            visitPaths(json) { path -> File(path).takeIf { it.isFile }?.let { if (assets.add(it.canonicalPath)) total += it.length() }; path }
        }
        return total
    }

    fun backupArchiveType(uri: Uri): String? = runCatching {
        input(uri).use { source -> ZipInputStream(source.buffered()).use { zip ->
            val entry = zip.nextEntry ?: return@use null
            if (entry.name != "manifest.json") return@use null
            val bytes = java.io.ByteArrayOutputStream()
            boundedCopy(zip, bytes, 64 * 1024)
            JSONObject(bytes.toString("UTF-8")).optString("archiveType").takeIf { it in setOf("notebook", "library") }
        } }
    }.getOrNull()

    /** Restores a library archive by merging it as fresh notebook/folder copies. */
    fun importLibraryBackup(uri: Uri): ImportedLibrary {
        val staging = File(mediaDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val jsonEntries = mutableMapOf<String, ByteArray>()
            val assetPaths = mutableMapOf<String, String>()
            var expandedTotal = 0L
            var entryCount = 0
            var manifest: JSONObject? = null
            input(uri).use { source -> ZipInputStream(source.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++entryCount <= MAX_BACKUP_ENTRIES) { "Library backup contains too many files." }
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    require(isSafeArchivePath(entry.name)) { "Invalid library backup entry." }
                    when {
                        entry.name == "manifest.json" -> {
                            require(manifest == null) { "Library backup has duplicate manifests." }
                            val bytes = java.io.ByteArrayOutputStream()
                            expandedTotal += boundedCopy(zip, bytes, 64 * 1024)
                            manifest = JSONObject(bytes.toString("UTF-8"))
                        }
                        entry.name == "library.json.zst" || entry.name.startsWith("notebooks/") && entry.name.endsWith("/notebook.json.zst") -> {
                            require(!jsonEntries.containsKey(entry.name)) { "Library backup has duplicate notebook data." }
                            val bytes = java.io.ByteArrayOutputStream()
                            expandedTotal += boundedCopy(zip, bytes, MAX_JSON_CONTAINER_BYTES)
                            jsonEntries[entry.name] = bytes.toByteArray()
                        }
                        entry.name.startsWith("assets/") -> {
                            require(!assetPaths.containsKey(entry.name)) { "Library backup has duplicate assets." }
                            val file = File(staging, "${UUID.randomUUID()}_${safeName(entry.name.substringAfterLast('/'))}")
                            file.outputStream().use { expandedTotal += boundedCopy(zip, it, MAX_ASSET_BYTES) }
                            assetPaths[entry.name] = file.absolutePath
                        }
                        else -> throw IllegalArgumentException("Library backup contains an unexpected file.")
                    }
                    require(expandedTotal <= MAX_BACKUP_BYTES) { "Library backup exceeds the 2 GiB restore limit." }
                    zip.closeEntry()
                }
            } }
            val archiveManifest = requireNotNull(manifest) { "Backup archive manifest is missing." }
            require(archiveManifest.optString("format") in FolioFormat.archiveFormats && archiveManifest.optInt("formatVersion") == 1) { "Unsupported backup archive version." }
            require(archiveManifest.optString("archiveType") == "library" && archiveManifest.optString("compression") == "zip+zstd-json") { "This is not a supported Folio library backup." }
            val libraryBytes = decompressJson(requireNotNull(jsonEntries.remove("library.json.zst")) { "Library index is missing." })
            var decodedJsonBytes = libraryBytes.size.toLong()
            val libraryJson = JSONObject(libraryBytes.toString(Charsets.UTF_8))
            require(libraryJson.optInt("schemaVersion") == 1) { "Unsupported library schema version." }
            val importedFolders = libraryJson.optJSONArray("folders") ?: JSONArray()
            val folderIdMap = mutableMapOf<String, String>()
            // Trashed items stay in Trash after a restore; their retention period restarts now.
            val restoredAt = System.currentTimeMillis()
            val folders = (0 until importedFolders.length()).map { index ->
                val folder = importedFolders.getJSONObject(index)
                val oldId = folder.getString("id")
                require(!folderIdMap.containsKey(oldId)) { "Library backup has duplicate folder IDs." }
                val newId = UUID.randomUUID().toString(); folderIdMap[oldId] = newId
                com.allient.folio.home.NoteFolder(newId, folder.optString("name", "Folder"), folder.optString("color", "sage"), folder.optString("style", "classic"),
                    if (folder.optLong("deletedAt", 0) > 0) restoredAt else 0)
            }
            val records = libraryJson.optJSONArray("notebooks") ?: JSONArray()
            require(records.length() == archiveManifest.optInt("notebookCount", -1)) { "Library backup notebook count does not match its manifest." }
            val usedPaths = mutableSetOf<String>()
            val recordIds = mutableSetOf<String>()
            val notebooks = (0 until records.length()).map { index ->
                val record = records.getJSONObject(index)
                val path = record.getString("path")
                require(path.startsWith("notebooks/") && path.endsWith("/notebook.json.zst") && usedPaths.add(path)) { "Invalid or duplicate notebook path in library index." }
                require(recordIds.add(record.getString("id"))) { "Library backup has duplicate notebook IDs." }
                val compressed = requireNotNull(jsonEntries.remove(path)) { "Library backup is missing a notebook." }
                val notebookBytes = decompressJson(compressed)
                decodedJsonBytes += notebookBytes.size
                require(decodedJsonBytes <= MAX_JSON_BYTES) { "Library JSON exceeds the 32 MiB restore limit." }
                val json = JSONObject(notebookBytes.toString(Charsets.UTF_8))
                visitPaths(json) { asset -> requireNotNull(assetPaths[asset]) { "Library backup is missing a referenced asset." } }
                val note = deserializeNotebook(json.toString())
                require(note.id == record.optString("id")) { "Library index and notebook IDs do not match." }
                val pageIds = note.pages.associate { it.id to UUID.randomUUID().toString() }
                require(pageIds.size == note.pages.size) { "Notebook has duplicate page IDs." }
                val lastPage = pageIds[note.lastViewedPageId]
                note.copy(
                    id = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis(),
                    folderId = note.folderId?.let(folderIdMap::get), deletedAt = if (note.deletedAt > 0) restoredAt else 0,
                    pages = note.pages.map { page -> page.copy(
                        id = pageIds.getValue(page.id),
                        strokes = page.strokes.map { it.copy(id = UUID.randomUUID().toString()) },
                        texts = page.texts.map { it.copy(id = UUID.randomUUID().toString()) },
                        images = page.images.map { it.copy(id = UUID.randomUUID().toString()) },
                    ) },
                    lastViewedPageId = lastPage,
                    attachments = note.attachments.map { it.copy(id = UUID.randomUUID().toString()) },
                )
            }
            require(jsonEntries.isEmpty()) { "Library backup contains unreferenced notebook data." }
            val downloaded = libraryJson.optJSONArray("downloadedTemplates")?.let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }.orEmpty()
            libraryJson.optJSONObject("reader")?.let { reader ->
                // Reader backups contain metadata and source URI references only; they never carry book files.
                val readerDocs = reader.optJSONArray("documents") ?: JSONArray()
                for (index in 0 until readerDocs.length()) {
                    val doc = readerDocs.getJSONObject(index)
                    val oldFolderId = doc.optString("folderId").takeIf { it.isNotBlank() }
                    if (oldFolderId != null) {
                        require(folderIdMap.containsKey(oldFolderId)) { "A reader book references a missing folder." }
                        doc.put("folderId", folderIdMap.getValue(oldFolderId))
                    }
                }
                ReaderStore(context).use { it.restore(reader) }
            }
            return ImportedLibrary(notebooks, com.allient.folio.home.LibrarySettings(folders, downloaded))
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun archiveManifest(type: String, notebookCount: Int) = JSONObject()
        .put("format", FolioFormat.ARCHIVE)
        .put("formatVersion", 1)
        .put("archiveType", type)
        .put("createdAt", System.currentTimeMillis())
        .put("compression", "zip+zstd-json")
        .put("notebookSchema", FolioFormat.NOTEBOOK_SCHEMA)
        .put("librarySchema", FolioFormat.LIBRARY_SCHEMA)
        .put("encrypted", false)
        .put("notebookCount", notebookCount)

    private fun writeZip(output: File, jsonFiles: Map<String, ByteArray>, assets: Map<String, File>) {
        try {
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                var total = 0L
                jsonFiles.forEach { (path, bytes) ->
                    total += bytes.size
                    require(total <= MAX_BACKUP_BYTES) { "Backup exceeds the 2 GiB limit." }
                    val entry = ZipEntry(path)
                    if (path.endsWith(".zst")) {
                        entry.method = ZipEntry.STORED
                        entry.size = bytes.size.toLong()
                        entry.compressedSize = bytes.size.toLong()
                        entry.crc = CRC32().apply { update(bytes) }.value
                    }
                    zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
                }
                assets.forEach { (path, file) ->
                    val stored = isAlreadyCompressed(file)
                    val entry = ZipEntry(path)
                    if (stored) {
                        val crc = CRC32().apply { file.inputStream().use { input ->
                            val buffer = ByteArray(8192); while (true) { val n = input.read(buffer); if (n < 0) break; update(buffer, 0, n) }
                        } }
                        entry.method = ZipEntry.STORED; entry.size = file.length(); entry.compressedSize = file.length(); entry.crc = crc.value
                    }
                    zip.putNextEntry(entry)
                    file.inputStream().use { total += boundedCopy(it, zip, MAX_ASSET_BYTES) }
                    require(total <= MAX_BACKUP_BYTES) { "Backup exceeds the 2 GiB limit." }
                    zip.closeEntry()
                }
            }
        } catch (error: Throwable) { output.delete(); throw error }
    }

    private fun isAlreadyCompressed(file: File): Boolean = file.extension.lowercase() in setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif", "mp3", "m4a", "aac", "ogg", "opus", "mp4", "mov", "pdf", "zip", "gz", "webm",
    )

    private fun decompressJson(compressed: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZstdInputStream(compressed.inputStream()).use { input -> boundedCopy(input, output, MAX_JSON_BYTES) }
        return output.toByteArray()
    }

    fun importBackup(uri: Uri): Notebook {
        val staging = File(mediaDir, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            var jsonText: String? = null
            val seenJsonEntries = mutableSetOf<String>()
            val assets = mutableMapOf<String, String>()
            var total = 0L
            var entries = 0
            input(uri).use { source ->
                val buffered = source.buffered()
                buffered.mark(4)
                val first = buffered.read(); val second = buffered.read(); buffered.reset()
                if (first == 'P'.code && second == 'K'.code) {
                    ZipInputStream(buffered).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            require(++entries <= MAX_BACKUP_ENTRIES) { "Backup contains too many files." }
                            if (entry.isDirectory) continue
                            require(isSafeArchivePath(entry.name)) { "Invalid backup entry." }
                            if (entry.name == "manifest.json" || entry.name == "library.json" || entry.name == "library.json.zst" ||
                                entry.name.endsWith("/notebook.json") || entry.name.endsWith("/notebook.json.zst") ||
                                entry.name == "notebook.json" || entry.name == "notebook.json.zst") {
                                require(seenJsonEntries.add(entry.name)) { "Duplicate JSON entry in backup." }
                                val bytes = java.io.ByteArrayOutputStream()
                                total += boundedCopy(zip, bytes, MAX_JSON_BYTES)
                                if (entry.name == "notebook.json" || entry.name == "notebook.json.zst") {
                                    require(jsonText == null) { "Backup contains duplicate notebook data." }
                                    val content = if (entry.name.endsWith(".zst")) decompressJson(bytes.toByteArray()) else bytes.toByteArray()
                                    jsonText = content.toString(Charsets.UTF_8)
                                }
                                else if (entry.name == "manifest.json") {
                                    val manifest = JSONObject(bytes.toString("UTF-8"))
                                    require(manifest.optString("format") in FolioFormat.archiveFormats && manifest.optInt("formatVersion") == 1) { "Unsupported backup archive version." }
                                    require(manifest.optString("compression") in setOf("zip-deflate", "zip+zstd-json")) { "Unsupported backup compression format." }
                                    require(manifest.optString("archiveType") == "notebook") { "This is a whole-library backup, which is not yet supported by the single-notebook import flow." }
                                } else if (entry.name == "library.json" || entry.name == "library.json.zst" || entry.name.endsWith("/notebook.json") || entry.name.endsWith("/notebook.json.zst")) {
                                    throw IllegalArgumentException("This is a whole-library backup, which is not yet supported by the single-notebook import flow.")
                                }
                            } else {
                                require(!assets.containsKey(entry.name)) { "Duplicate backup asset." }
                                val output = File(staging, UUID.randomUUID().toString() + "_" + safeName(entry.name.substringAfterLast('/')))
                                output.outputStream().use { total += boundedCopy(zip, it, MAX_ASSET_BYTES) }
                                assets[entry.name] = output.absolutePath
                            }
                            require(total <= MAX_BACKUP_BYTES) { "Backup exceeds the import size limit." }
                            zip.closeEntry()
                        }
                    }
                } else {
                    val bytes = java.io.ByteArrayOutputStream()
                    boundedCopy(buffered, bytes, MAX_JSON_BYTES)
                    jsonText = bytes.toString("UTF-8")
                }
            }
            val json = JSONObject(requireNotNull(jsonText) { "Notebook data is missing." })
            visitPaths(json) { path -> requireNotNull(assets[path]) { "Backup is missing a referenced asset." } }
            val note = deserializeNotebook(json.toString())
            val pageIds = note.pages.map { UUID.randomUUID().toString() }
            val lastViewedIndex = note.pages.indexOfFirst { it.id == note.lastViewedPageId }
            return note.copy(
                id = UUID.randomUUID().toString(),
                updatedAt = System.currentTimeMillis(), deletedAt = 0,
                lastViewedPageId = pageIds.getOrNull(lastViewedIndex),
                pages = note.pages.mapIndexed { index, page -> page.copy(
                    id = pageIds[index],
                    strokes = page.strokes.map { it.copy(id = UUID.randomUUID().toString()) },
                    texts = page.texts.map { it.copy(id = UUID.randomUUID().toString()) },
                    images = page.images.map { it.copy(id = UUID.randomUUID().toString()) },
                ) },
                attachments = note.attachments.map { it.copy(id = UUID.randomUUID().toString()) },
            )
        } catch (error: Throwable) { staging.deleteRecursively(); throw error }
    }

    fun importImages(uri: Uri): List<NotebookPage> {
        val image = copyAttachment(uri)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(image.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image format cannot be opened." }
            val rotation = runCatching { androidx.exifinterface.media.ExifInterface(image.path).rotationDegrees }.getOrDefault(0)
            val imageRatio = if (rotation == 90 || rotation == 270) bounds.outHeight.toFloat() / bounds.outWidth
                else bounds.outWidth.toFloat() / bounds.outHeight
            val pageRatio = 1200f / 1920f
            val width = if (imageRatio >= pageRatio) 1f else imageRatio / pageRatio
            val height = if (imageRatio >= pageRatio) pageRatio / imageRatio else 1f
            return listOf(NotebookPage(images = listOf(InkImage(path = image.path, x = (1f - width) / 2f, y = (1f - height) / 2f, width = width, height = height))))
        } catch (error: Throwable) { File(image.path).delete(); throw error }
    }

    fun importPdfPages(uri: Uri): List<NotebookPage> {
        val copied = mutableListOf<File>()
        // PdfRenderer requires a seekable descriptor; SAF providers may return a pipe.
        val localPdf = File(context.cacheDir, "pdf-import-${UUID.randomUUID()}.pdf")
        try {
            input(uri).use { source -> localPdf.outputStream().use { boundedCopy(source, it, 128L * 1024 * 1024) } }
            val descriptor = ParcelFileDescriptor.open(localPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor.use { PdfRenderer(it).use { renderer ->
                require(renderer.pageCount in 1..200) { "Import a PDF with 1–200 pages." }
                return (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = minOf(1200f / page.width, 1920f / page.height)
                        val bitmap = Bitmap.createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                        val file = File(mediaDir, "${UUID.randomUUID()}.png").also(copied::add)
                        val width = bitmap.width / 1200f; val height = bitmap.height / 1920f
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            file.outputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) }
                        } finally { bitmap.recycle() }
                        NotebookPage(images = listOf(InkImage(path = file.absolutePath, x = (1f - width) / 2f, y = (1f - height) / 2f, width = width, height = height)))
                    }
                }
            } }
        } catch (error: Throwable) { copied.forEach(File::delete); throw error }
        finally { localPdf.delete() }
    }

    fun copyAttachment(uri: Uri): NoteAttachment {
        var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "Attachment"
        // Metadata is optional: some document providers expose a readable stream without a query.
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) name = cursor.getString(column) ?: name
            }
        }
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val file = File(mediaDir, "${UUID.randomUUID()}_${safeName(name)}")
        try { input(uri).use { source -> file.outputStream().use { boundedCopy(source, it, 20L * 1024 * 1024) } } }
        catch (error: Throwable) { file.delete(); throw error }
        return NoteAttachment(name = name, path = file.absolutePath, mimeType = mime)
    }

    fun shareFile(file: File, mimeType: String) {
        val uri = contentUri(file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share notebook").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAttachment(attachment: NoteAttachment) {
        val uri = contentUri(File(attachment.path))
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType)
            clipData = ClipData.newRawUri(attachment.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun writeToUri(file: File, destination: Uri) {
        requireNotNull(context.contentResolver.openOutputStream(destination, "wt")) { "Cannot save this document." }.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    private fun contentUri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    private fun input(uri: Uri) = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open the selected document." }
    private fun exportFile(title: String, extension: String) = File(exportDir, "${safeName(title)}-${UUID.randomUUID()}.$extension")
    private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "Notebook" }

    private fun boundedCopy(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192); var total = 0L
        while (true) { val count = input.read(buffer); if (count < 0) break; total += count; require(total <= limit) { "Selected file is too large." }; output.write(buffer, 0, count) }
        return total
    }

    private fun visitPaths(value: Any, map: (String) -> String) {
        when (value) {
            is JSONObject -> value.keys().asSequence().toList().forEach { key ->
                val child = value.get(key)
                if (key == "path" && child is String && child.isNotBlank()) value.put(key, map(child)) else visitPaths(child, map)
            }
            is JSONArray -> (0 until value.length()).forEach { visitPaths(value.get(it), map) }
        }
    }

    private fun isSafeArchivePath(path: String): Boolean = path.isNotBlank() &&
        !path.startsWith('/') && !path.contains('\\') && path.split('/').all { it.isNotBlank() && it != "." && it != ".." } &&
        (path == "notebook.json" || path == "notebook.json.zst" || path == "manifest.json" || path == "library.json" || path == "library.json.zst" || path.startsWith("notebooks/") || path.startsWith("assets/"))

    private companion object {
        const val MAX_BACKUP_ENTRIES = 1000
        const val MAX_JSON_BYTES = 32L * 1024 * 1024
        const val MAX_JSON_CONTAINER_BYTES = MAX_JSON_BYTES + 1024L * 1024
        const val MAX_ASSET_BYTES = 128L * 1024 * 1024
        const val MAX_BACKUP_BYTES = 2048L * 1024 * 1024
        const val ZSTD_LEVEL = 3
    }
}
