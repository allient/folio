package com.allient.folio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

internal data class LocalBackupState(
    val enabled: Boolean = false,
    val folderUri: String? = null,
    val folderLabel: String = "No folder selected",
    val generation: Long = 0,
    val lastSuccess: Long = 0,
    val lastFile: String? = null,
    val lastExport: Long = 0,
    val lastExportFile: String? = null,
    val signature: String? = null,
    val error: String? = null,
    /** When the oldest change not yet in a backup was made; 0 when everything is backed up. */
    val firstUnbackedChange: Long = 0,
) {
    val mostRecentBackup: Long get() = maxOf(lastSuccess, lastExport)
    val mostRecentFile: String? get() = if (lastExport >= lastSuccess) lastExportFile ?: lastFile else lastFile ?: lastExportFile
    val hasUnbackedChanges: Boolean get() = firstUnbackedChange > 0

    /** Home banner: changes have gone unbacked for three days. */
    fun needsReminder(now: Long): Boolean {
        if (!hasUnbackedChanges) return false
        val reference = if (mostRecentBackup > 0) mostRecentBackup else firstUnbackedChange
        return now - reference > REMINDER_AFTER_MS
    }

    companion object { const val REMINDER_AFTER_MS = 3L * 24 * 60 * 60 * 1000 }
}

internal class LocalBackupStore(context: Context, name: String = PREFERENCES) {
    val preferences: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun read() = LocalBackupState(
        enabled = preferences.getBoolean("enabled", false), folderUri = preferences.getString("folderUri", null),
        folderLabel = preferences.getString("folderLabel", null) ?: preferences.getString("folderUri", null)?.let { folderLabel(Uri.parse(it)) } ?: "No folder selected",
        generation = preferences.getLong("generation", 0), lastSuccess = preferences.getLong("lastSuccess", 0),
        lastFile = preferences.getString("lastFile", null), lastExport = preferences.getLong("lastExport", 0),
        lastExportFile = preferences.getString("lastExportFile", null), signature = preferences.getString("signature", null), error = preferences.getString("error", null),
        firstUnbackedChange = preferences.getLong("firstUnbackedChange", 0),
    )
    fun configure(uri: Uri, label: String, enabled: Boolean) {
        val previous = read()
        preferences.edit().putString("folderUri", uri.toString()).putString("folderLabel", label)
            .putBoolean("enabled", enabled).putLong("generation", previous.generation + 1).remove("error")
            .apply { if (previous.folderUri != uri.toString()) remove("signature").remove("lastFile").remove("lastSuccess") }.commit()
    }
    fun disable() { preferences.edit().putBoolean("enabled", false).putLong("generation", read().generation + 1).commit() }
    fun isCurrent(state: LocalBackupState): Boolean = preferences.getBoolean("enabled", false) &&
        preferences.getLong("generation", 0) == state.generation && preferences.getString("folderUri", null) == state.folderUri
    /** Records that the library changed. Only the first change after a backup writes to disk. */
    fun markChanged(now: Long = System.currentTimeMillis()) {
        if (preferences.getLong("firstUnbackedChange", 0) == 0L) preferences.edit().putLong("firstUnbackedChange", now).apply()
    }
    /** Changes made after [snapshotAt] were not part of the snapshot and stay pending. */
    private fun SharedPreferences.Editor.clearPending(snapshotAt: Long): SharedPreferences.Editor = apply {
        if (preferences.getLong("firstUnbackedChange", 0) < snapshotAt) remove("firstUnbackedChange")
    }
    fun success(state: LocalBackupState, filename: String, signature: String, snapshotAt: Long) {
        if (isCurrent(state)) preferences.edit().putLong("lastSuccess", System.currentTimeMillis()).putString("lastFile", filename)
            .putString("signature", signature).remove("error").clearPending(snapshotAt).commit()
    }
    fun upToDate(state: LocalBackupState, snapshotAt: Long) { if (isCurrent(state)) preferences.edit().clearPending(snapshotAt).commit() }
    fun error(state: LocalBackupState, message: String) { if (isCurrent(state)) preferences.edit().putString("error", message).commit() }
    /** A manual full-library export also counts as a backup of the changes made before [snapshotAt]. */
    fun exported(filename: String, snapshotAt: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("lastExport", System.currentTimeMillis()).putString("lastExportFile", filename).clearPending(snapshotAt).commit()
    }

    companion object {
        const val PREFERENCES = "backup"
        fun folderLabel(uri: Uri): String {
            val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return "Selected folder"
            return if (uri.authority == "com.android.externalstorage.documents") {
                val volume = id.substringBefore(':')
                val path = id.substringAfter(':', "")
                if (volume == "primary") path.ifBlank { "Internal storage" } else "SD card / $path"
            } else "Selected folder"
        }
        fun initialFolder(current: String?): Uri = current?.let { runCatching {
            val tree = Uri.parse(it)
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        }.getOrNull() } ?: DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
    }
}

/** One backup file in the backup folder. Only files matching the app's own name pattern are ever listed. */
internal data class BackupVersion(val id: String, val name: String, val createdAt: Long, val size: Long, val partial: Boolean = false)

internal object BackupNames {
    private val pattern = Regex("""^(Folio|Knotes)-Backup-(\d{8}-\d{6})(?:-(\d{3}))?\.(?:folio|knotes)(\.partial)?$""")
    fun create(now: Long): String =
        "Folio-Backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", java.util.Locale.US).format(java.util.Date(now))}.${FolioFormat.EXTENSION}"
    fun isOwned(name: String) = pattern.matches(name)
    fun isPartial(name: String) = name.endsWith(".partial") && isOwned(name)
    /** The creation time in the name (local time, as written), or null for a name this app did not create. */
    fun timestamp(name: String): Long? {
        val match = pattern.matchEntire(name) ?: return null
        return runCatching {
            java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", java.util.Locale.US).apply { isLenient = false }
                .parse("${match.groupValues[2]}-${match.groupValues[3].ifBlank { "000" }}")?.time
        }.getOrNull()
    }
}

/**
 * Version retention: the 5 most recent backups, plus the newest backup of each of the last
 * 7 days, 4 weeks and 6 months. At most 22 files are kept. The newest is always kept.
 */
internal object BackupRetention {
    const val RECENT = 5
    const val DAYS = 7
    const val WEEKS = 4
    const val MONTHS = 6
    const val MAX_KEPT = RECENT + DAYS + WEEKS + MONTHS

    fun keep(versions: List<BackupVersion>, now: Long, zone: TimeZone = TimeZone.getDefault(), protect: Set<String> = emptySet()): Set<String> {
        val complete = versions.filterNot { it.partial }.sortedWith(compareByDescending<BackupVersion> { it.createdAt }.thenByDescending { it.name })
        val keep = linkedSetOf<String>()
        complete.take(RECENT).forEach { keep += it.id }
        fun calendar(time: Long) = Calendar.getInstance(zone, java.util.Locale.US).apply {
            firstDayOfWeek = Calendar.MONDAY; minimalDaysInFirstWeek = 4; timeInMillis = time
        }
        fun startOfDay(time: Long) = calendar(time).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        fun dayKey(time: Long) = startOfDay(time).timeInMillis
        fun weekKey(time: Long) = startOfDay(time).apply {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis
        fun monthKey(time: Long) = calendar(time).let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }
        fun bucketKeys(count: Int, field: Int, key: (Long) -> Long): Set<Long> = (0 until count).map { offset ->
            key(startOfDay(now).apply { add(field, -offset) }.timeInMillis)
        }.toSet()
        fun newestPerBucket(allowed: Set<Long>, key: (Long) -> Long) {
            val seen = mutableSetOf<Long>()
            complete.forEach { version -> val bucket = key(version.createdAt); if (bucket in allowed && seen.add(bucket)) keep += version.id }
        }
        newestPerBucket(bucketKeys(DAYS, Calendar.DAY_OF_MONTH, ::dayKey), ::dayKey)
        newestPerBucket(bucketKeys(WEEKS, Calendar.WEEK_OF_YEAR, ::weekKey), ::weekKey)
        newestPerBucket(bucketKeys(MONTHS, Calendar.MONTH) { monthKey(it).toLong() }) { monthKey(it).toLong() }
        complete.firstOrNull()?.let { keep += it.id }
        keep += protect.filter { id -> complete.any { it.id == id } }
        return keep
    }
}

/** Reads a library archive back and rejects it unless it is a complete, consistent ZIP. */
internal object BackupArchiveVerifier {
    private const val EOCD_MAX = 22 + 65535

    /** Returns the number of bytes read. Throws [IOException] when the archive is incomplete or inconsistent. */
    fun verify(input: InputStream, expectedNotebookCount: Int): Long {
        val tail = TailStream(input, EOCD_MAX)
        var entries = 0
        var manifest: JSONObject? = null
        var notebookEntries = 0
        var library = false
        try {
            val zip = ZipInputStream(tail.buffered())
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                val bytes = if (entry.name == "manifest.json") java.io.ByteArrayOutputStream() else null
                while (true) {
                    val count = zip.read(buffer); if (count < 0) break
                    if (bytes != null) { bytes.write(buffer, 0, count); if (bytes.size() > 64 * 1024) throw IOException("Backup manifest is too large.") }
                }
                when {
                    bytes != null -> { if (manifest != null) throw IOException("Backup has duplicate manifests."); manifest = JSONObject(bytes.toString("UTF-8")) }
                    entry.name == "library.json.zst" -> library = true
                    entry.name.startsWith("notebooks/") && entry.name.endsWith("/notebook.json.zst") -> notebookEntries++
                }
                zip.closeEntry()
            }
            tail.drain()
        } catch (error: IOException) { throw error }
        catch (error: Exception) { throw IOException("Backup archive could not be read back: ${error.message}", error) }
        val found = requireNotNullIo(manifest) { "Backup archive has no manifest." }
        if (found.optString("format") !in FolioFormat.archiveFormats || found.optString("archiveType") != "library") throw IOException("Backup archive has an unexpected manifest.")
        if (found.optInt("notebookCount", -1) != expectedNotebookCount || notebookEntries != expectedNotebookCount) throw IOException("Backup archive notebook count does not match the library.")
        if (!library) throw IOException("Backup archive has no library index.")
        val directoryEntries = tail.endOfCentralDirectoryEntries() ?: throw IOException("Backup archive is truncated.")
        if (directoryEntries != entries) throw IOException("Backup archive directory does not match its contents.")
        return tail.total
    }

    private inline fun <T : Any> requireNotNullIo(value: T?, message: () -> String): T = value ?: throw IOException(message())

    /** Counts bytes and keeps the last [capacity] bytes so the ZIP end record can be checked. */
    private class TailStream(source: InputStream, private val capacity: Int) : FilterInputStream(source) {
        private val ring = ByteArray(capacity)
        var total = 0L; private set
        private fun record(bytes: ByteArray, offset: Int, count: Int) {
            for (i in 0 until count) ring[((total + i) % capacity).toInt()] = bytes[offset + i]
            total += count
        }
        override fun read(): Int = super.read().also { if (it >= 0) { ring[(total % capacity).toInt()] = it.toByte(); total++ } }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) record(b, off, it) }
        override fun skip(n: Long): Long { val b = ByteArray(8192); var left = n; while (left > 0) { val r = read(b, 0, minOf(left, 8192).toInt()); if (r < 0) break; left -= r }; return n - left }
        override fun markSupported() = false
        fun drain() { val b = ByteArray(64 * 1024); while (read(b, 0, b.size) >= 0) Unit }
        fun endOfCentralDirectoryEntries(): Int? {
            val length = minOf(total, capacity.toLong()).toInt()
            val bytes = ByteArray(length) { ring[((total - length + it) % capacity).toInt()] }
            for (start in length - 22 downTo 0) {
                if (bytes[start] == 0x50.toByte() && bytes[start + 1] == 0x4b.toByte() && bytes[start + 2] == 0x05.toByte() && bytes[start + 3] == 0x06.toByte()) {
                    val comment = (bytes[start + 20].toInt() and 0xff) or ((bytes[start + 21].toInt() and 0xff) shl 8)
                    if (start + 22 + comment != length) continue
                    return (bytes[start + 10].toInt() and 0xff) or ((bytes[start + 11].toInt() and 0xff) shl 8)
                }
            }
            return null
        }
    }
}

/** The destination of local backups. Implementations only ever list or delete the app's own files. */
internal interface BackupFolder {
    fun list(): List<BackupVersion>
    /** Writes to a temporary name, verifies the copy, then renames it to [name]. */
    fun write(archive: File, name: String, expectedNotebookCount: Int, checkActive: () -> Unit): BackupVersion
    fun delete(version: BackupVersion)
    /** Free bytes on the destination volume, when the provider reports it. */
    fun freeBytes(): Long?
    fun open(version: BackupVersion): InputStream

    companion object {
        /** Verifies a written copy by size, SHA-256 and ZIP structure. */
        fun verifyCopy(archive: File, copy: InputStream, expectedNotebookCount: Int) {
            val expected = MessageDigest.getInstance("SHA-256").let { digest ->
                archive.inputStream().use { input -> val b = ByteArray(64 * 1024); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
                digest.digest()
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val read = DigestInputStream(copy, digest).use { BackupArchiveVerifier.verify(it, expectedNotebookCount) }
            if (read != archive.length() || !MessageDigest.isEqual(expected, digest.digest())) throw IOException("The backup copy does not match the archive. The previous backups were kept.")
        }
    }
}

/** A backup folder chosen through the Storage Access Framework; no storage permission is needed. */
internal class SafBackupFolder(private val context: Context, private val tree: Uri) : BackupFolder {
    private val resolver get() = context.contentResolver
    private val parent get() = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    override fun list(): List<BackupVersion> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return resolver.query(children, columns, null, null, null)?.use { cursor -> buildList {
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (!BackupNames.isOwned(name) || cursor.getString(4) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val modified = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                add(BackupVersion(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)).toString(), name,
                    BackupNames.timestamp(name) ?: modified, if (cursor.isNull(2)) 0L else cursor.getLong(2), BackupNames.isPartial(name)))
            }
        } } ?: throw IOException("Could not read the backup folder.")
    }

    override fun write(archive: File, name: String, expectedNotebookCount: Int, checkActive: () -> Unit): BackupVersion {
        checkActive()
        val destination = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", "$name.partial")
            ?: throw IOException("Could not create a backup in the selected folder.")
        try {
            (resolver.openOutputStream(destination, "w") ?: throw IOException("Could not open the backup destination.")).use { out ->
                archive.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) { checkActive(); val count = input.read(buffer); if (count == -1) break; out.write(buffer, 0, count) }
                    out.flush()
                }
            }
            checkActive()
            (resolver.openInputStream(destination) ?: throw IOException("Could not read the backup back.")).use { BackupFolder.verifyCopy(archive, it, expectedNotebookCount) }
            checkActive()
            val complete = DocumentsContract.renameDocument(resolver, destination, name)
                ?: throw IOException("Could not finish the backup. Choose a folder in Documents on this device.")
            return BackupVersion(complete.toString(), name, BackupNames.timestamp(name) ?: System.currentTimeMillis(), archive.length())
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, destination) }
            throw error
        }
    }

    override fun delete(version: BackupVersion) {
        require(BackupNames.isOwned(version.name)) { "Refusing to delete a file Folio did not create." }
        DocumentsContract.deleteDocument(resolver, Uri.parse(version.id))
    }

    override fun freeBytes(): Long? = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(tree)
        resolver.query(DocumentsContract.buildRootsUri(tree.authority!!), arrayOf(DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_AVAILABLE_BYTES),
            null, null, null)?.use { cursor ->
            var best: Long? = null; var bestLength = -1
            while (cursor.moveToNext()) {
                val rootDoc = cursor.getString(0) ?: continue
                if (!cursor.isNull(1) && documentId.startsWith(rootDoc) && rootDoc.length > bestLength) { best = cursor.getLong(1); bestLength = rootDoc.length }
            }
            best
        }
    }.getOrNull()

    override fun open(version: BackupVersion): InputStream = resolver.openInputStream(Uri.parse(version.id)) ?: throw IOException("Could not open the backup.")
}

/** A plain directory destination; used by tests and anywhere a File path is available. */
internal class DirectoryBackupFolder(private val directory: File) : BackupFolder {
    override fun list(): List<BackupVersion> = (directory.listFiles() ?: emptyArray()).filter { it.isFile && BackupNames.isOwned(it.name) }.map {
        BackupVersion(it.absolutePath, it.name, BackupNames.timestamp(it.name) ?: it.lastModified(), it.length(), BackupNames.isPartial(it.name))
    }
    override fun write(archive: File, name: String, expectedNotebookCount: Int, checkActive: () -> Unit): BackupVersion {
        val temporary = File(directory, "$name.partial")
        try {
            checkActive()
            archive.inputStream().use { input -> temporary.outputStream().use { input.copyTo(it); it.fd.sync() } }
            temporary.inputStream().use { BackupFolder.verifyCopy(archive, it, expectedNotebookCount) }
            checkActive()
            val target = File(directory, name)
            if (!temporary.renameTo(target)) throw IOException("Could not finish the backup.")
            return BackupVersion(target.absolutePath, name, BackupNames.timestamp(name) ?: target.lastModified(), target.length())
        } catch (error: Throwable) { temporary.delete(); throw error }
    }
    override fun delete(version: BackupVersion) {
        require(BackupNames.isOwned(version.name) && File(version.id).parentFile?.canonicalPath == directory.canonicalPath) { "Refusing to delete a file Folio did not create." }
        File(version.id).delete()
    }
    override fun freeBytes(): Long = directory.usableSpace
    override fun open(version: BackupVersion): InputStream = File(version.id).inputStream()
}

/** The backup itself, independent of WorkManager so it can be tested directly. */
internal class LocalBackupRunner(
    private val context: Context,
    private val store: LocalBackupStore,
    private val folderFor: (LocalBackupState) -> BackupFolder,
    private val localFreeBytes: () -> Long = { context.cacheDir.usableSpace },
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Outcome {
        data object Disabled : Outcome
        data object Unchanged : Outcome
        data class LowSpace(val needed: Long, val free: Long) : Outcome
        data class Saved(val version: BackupVersion, val deleted: List<String>) : Outcome
    }

    fun run(force: Boolean, checkActive: () -> Unit = {}): Outcome {
        val settings = store.read()
        if (!settings.enabled || settings.folderUri == null) return Outcome.Disabled
        val startedAt = now()
        val folder = folderFor(settings)
        val storage = NotebookStorage(context)
        var archive: File? = null
        try {
            val snapshot = storage.loadBackupSnapshot()
            val readerSnapshot = ReaderStore(context).use { it.snapshot().toString() }
            val signature = LocalBackupWorker.signature(snapshot, readerSnapshot)
            if (!force && signature == settings.signature && settings.error == null) {
                store.upToDate(settings, startedAt)
                return Outcome.Unchanged
            }
            checkActive()
            val files = NotebookFiles(context)
            val estimate = files.estimateLibraryBackupBytes(snapshot.notebooks) + readerSnapshot.length
            val needed = maxOf(2 * estimate, MIN_FREE_BYTES)
            val free = listOfNotNull(localFreeBytes(), folder.freeBytes()).minOrNull() ?: Long.MAX_VALUE
            if (free < needed) {
                store.error(settings, "Not enough free space for a backup: about ${megabytes(needed)} is needed and ${megabytes(free)} is free. " +
                    "Free up some space; Folio tries again after your next change. Existing backups were kept.")
                return Outcome.LowSpace(needed, free)
            }
            archive = files.exportLibraryBackup(snapshot.notebooks, snapshot.settings)
            val name = BackupNames.create(now())
            val saved = folder.write(archive, name, snapshot.notebooks.size, checkActive)
            store.success(settings, name, signature, startedAt)
            return Outcome.Saved(saved, prune(folder, saved))
        } finally { archive?.delete(); storage.close() }
    }

    /** Runs only after [newest] was verified; never deletes it or any file the app did not name. */
    private fun prune(folder: BackupFolder, newest: BackupVersion): List<String> {
        val versions = runCatching { folder.list() }.getOrElse { return emptyList() }
        val time = now()
        val keep = BackupRetention.keep(versions, time, protect = setOf(newest.id)) + newest.id
        val stale = versions.filter { it.partial && it.name != "${newest.name}.partial" && time - it.createdAt > STALE_PARTIAL_MS }
        val doomed = versions.filter { !it.partial && it.id !in keep && it.name != newest.name } + stale
        return doomed.mapNotNull { version -> runCatching { folder.delete(version); version.name }.getOrNull() }
    }

    companion object {
        const val MIN_FREE_BYTES = 200L * 1024 * 1024
        const val STALE_PARTIAL_MS = 24L * 60 * 60 * 1000
        fun megabytes(bytes: Long): String = "${(bytes + 512 * 1024) / (1024 * 1024)} MB"
    }
}

/**
 * Change-triggered backups. All one-time requests share one unique name: an editing session
 * batches into at most one backup every 15 minutes (KEEP), while closing a note or leaving
 * the app replaces it with a backup two minutes later. A 6-hour periodic job is a safety net.
 */
internal object LocalBackupScheduler {
    const val CHANGE = "folio-local-backup-change"
    const val PERIODIC = "folio-local-backup-periodic"
    const val NOW = "folio-local-backup-now"
    private const val LEGACY_DAILY = "knotes-local-backup-daily"
    // Queued under the pre-com.allient.folio worker class name, which no longer exists.
    private const val LEGACY_PERIODIC = "knotes-local-backup-periodic"
    const val SESSION_DELAY_MINUTES = 15L
    const val CLOSE_DELAY_MINUTES = 2L
    private val lastSessionRequest = AtomicLong(0)
    private val observing = AtomicBoolean(false)

    private fun enabled(context: Context) = LocalBackupStore(context).read().let { it.enabled && it.folderUri != null }

    fun enable(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(LEGACY_DAILY); cancelUniqueWork(LEGACY_PERIODIC)
            enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LocalBackupWorker>(6, TimeUnit.HOURS).setInitialDelay(6, TimeUnit.HOURS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }

    /** Called when a library save is queued. Cheap: one preference write per backup, one enqueue per minute. */
    fun changed(context: Context) {
        runCatching {
            val app = context.applicationContext
            LocalBackupStore(app).markChanged()
            if (!enabled(app)) return
            val now = System.currentTimeMillis()
            val last = lastSessionRequest.get()
            if (now - last < 60_000 || !lastSessionRequest.compareAndSet(last, now)) return
            enqueue(app, ExistingWorkPolicy.KEEP, SESSION_DELAY_MINUTES)
        }
    }

    /** A note was closed or the app left the foreground: back up soon, if anything changed. */
    fun soon(context: Context) {
        runCatching {
            val app = context.applicationContext
            val state = LocalBackupStore(app).read()
            if (!state.enabled || state.folderUri == null || !state.hasUnbackedChanges) return
            enqueue(app, ExistingWorkPolicy.REPLACE, CLOSE_DELAY_MINUTES)
        }
    }

    private fun enqueue(context: Context, policy: ExistingWorkPolicy, delayMinutes: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(CHANGE, policy,
            OneTimeWorkRequestBuilder<LocalBackupWorker>().setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    fun now(context: Context, force: Boolean = true) {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LocalBackupWorker>().setInputData(workDataOf("force" to force))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).apply { listOf(LEGACY_DAILY, LEGACY_PERIODIC, PERIODIC, CHANGE, NOW).forEach(::cancelUniqueWork) }
    }

    /** Leaving the app (all activities stopped) schedules a backup two minutes later. Registered once per process. */
    fun observeProcess(context: Context) {
        if (!observing.compareAndSet(false, true)) return
        val app = context.applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = soon(app)
        })
    }
}

/** Durable, off-UI-thread backups. Disabling or replacing a folder cancels partial copies. */
class LocalBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = backupMutex.withLock { withContext(Dispatchers.IO) {
        val store = LocalBackupStore(applicationContext)
        val settings = store.read()
        if (!settings.enabled || settings.folderUri == null) return@withContext Result.success()
        try {
            val uri = Uri.parse(settings.folderUri)
            checkFolderGrant(applicationContext, uri)
            val job = coroutineContext
            LocalBackupRunner(applicationContext, store, { SafBackupFolder(applicationContext, uri) }).run(inputData.getBoolean("force", false)) {
                job.ensureActive()
                if (!store.isCurrent(settings)) throw CancellationException("Local backup settings changed")
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            // Settings changed mid-copy: the new settings schedule their own backup, so this run just ends.
            if (isActive) Result.success() else throw cancelled
        } catch (error: Exception) {
            store.error(settings, if (error is SecurityException) "Folder access is no longer available. Choose the backup folder again."
                else error.message?.takeIf { it.isNotBlank() } ?: "Could not create the backup. Check the folder and available storage.")
            if (error is IOException && runAttemptCount < 2) Result.retry() else Result.failure()
        }
    } }

    companion object {
        private val backupMutex = Mutex()
        internal fun signature(snapshot: ImportedLibrary, readerSnapshot: String = ""): String {
            val digest = MessageDigest.getInstance("SHA-256")
            snapshot.notebooks.sortedBy { it.id }.forEach { digest.update(serializeNotebook(it).toByteArray(Charsets.UTF_8)) }
            val library = JSONObject().put("folders", JSONArray().apply { snapshot.settings.folders.forEach { put(folderJson(it)) } }).put("downloaded", JSONArray(snapshot.settings.downloaded.sorted()))
            digest.update(library.toString().toByteArray(Charsets.UTF_8))
            digest.update(readerSnapshot.toByteArray(Charsets.UTF_8))
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        internal fun checkFolderGrant(context: Context, uri: Uri) {
            if (!context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission && it.isWritePermission })
                throw SecurityException("Folder access is no longer available. Choose the backup folder again.")
        }
    }
}

internal class LocalBackupViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalBackupStore(application)
    private val main = Handler(Looper.getMainLooper())
    var state by mutableStateOf(store.read())
        private set
    var running by mutableStateOf(false)
        private set
    var choosingFolder by mutableStateOf(false)
        private set
    var folderError by mutableStateOf<String?>(null)
        private set
    /** Kept versions in the backup folder, newest first. */
    var versions by mutableStateOf<List<BackupVersion>>(emptyList())
        private set
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        main.post { state = store.read(); if (key == "lastSuccess" || key == "folderUri") refreshVersions() }
    }
    init {
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        val work = WorkManager.getInstance(application)
        viewModelScope.launch {
            combine(work.getWorkInfosForUniqueWorkFlow(LocalBackupScheduler.NOW), work.getWorkInfosForUniqueWorkFlow(LocalBackupScheduler.CHANGE),
                work.getWorkInfosForUniqueWorkFlow(LocalBackupScheduler.PERIODIC)) { now, change, periodic ->
                now.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } ||
                    (change + periodic).any { it.state == WorkInfo.State.RUNNING }
            }.collect { running = it }
        }
        if (state.enabled && state.folderUri != null) {
            LocalBackupScheduler.enable(application)
            if (state.lastSuccess == 0L) LocalBackupScheduler.now(application, false)
        }
        refreshVersions()
    }
    fun refreshVersions() {
        val settings = store.read()
        val folder = settings.folderUri ?: run { versions = emptyList(); return }
        viewModelScope.launch {
            versions = withContext(Dispatchers.IO) {
                runCatching { SafBackupFolder(getApplication(), Uri.parse(folder)).list().filterNot { it.partial }.sortedByDescending { it.createdAt } }.getOrDefault(emptyList())
            }
        }
    }
    fun disable() {
        store.disable(); state = store.read()
        LocalBackupScheduler.disable(getApplication())
    }
    fun chooseFolder(uri: Uri, enable: Boolean) {
        choosingFolder = true; folderError = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    val previous = store.read()
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    var label = LocalBackupStore.folderLabel(uri)
                    try {
                        val parent = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                        context.contentResolver.query(parent, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
                            require(cursor.moveToFirst() && cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) { "Choose a folder on this device." }
                            require(cursor.getInt(2) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0) { "Folio cannot write to this folder. Choose another folder." }
                            if (label == "Selected folder") label = cursor.getString(0)
                        } ?: error("Could not open this folder. Choose another folder.")
                    } catch (error: Exception) {
                        // Do not keep a grant for a folder that was rejected (apps have a limited number of grants).
                        if (previous.folderUri != uri.toString()) runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
                        throw error
                    }
                    store.configure(uri, label, enable)
                    LocalBackupScheduler.disable(context)
                    if (previous.folderUri != null && previous.folderUri != uri.toString()) runCatching {
                        context.contentResolver.releasePersistableUriPermission(Uri.parse(previous.folderUri), flags)
                    }
                    if (enable) { LocalBackupScheduler.enable(context); LocalBackupScheduler.now(context) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { folderError = error.message ?: "Could not use this folder. Please choose another." }
            finally { state = store.read(); choosingFolder = false; refreshVersions() }
        }
    }
    fun backUpNow() { if (state.enabled && state.folderUri != null) LocalBackupScheduler.now(getApplication()) }
    fun recordExport(filename: String) { store.exported(filename); state = store.read() }
    override fun onCleared() { store.preferences.unregisterOnSharedPreferenceChangeListener(listener); super.onCleared() }
}
