package com.allient.folio

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val penFilter = PenPressureFilter()

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = penFilter.dispatch(event) { super.dispatchTouchEvent(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PenSettings.init(this)
        enableEdgeToEdge()
        hideSystemBars()
        val repository = ViewModelProvider(this)[NotebookViewModel::class.java]
        val backup = ViewModelProvider(this)[LocalBackupViewModel::class.java]
        val reader = ViewModelProvider(this)[ReaderViewModel::class.java]
        LocalBackupScheduler.observeProcess(this)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalPenThreshold provides PenSettings.thresholdState.floatValue) { FolioApp(repository, backup, reader) }
        }
        intent.data?.let(reader::import)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { ViewModelProvider(this)[ReaderViewModel::class.java].import(it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FolioApp(repository: NotebookViewModel, backup: LocalBackupViewModel, reader: ReaderViewModel) {
    val context = LocalContext.current
    val files = remember(context) { NotebookFiles(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var openNotebookId by rememberSaveable { mutableStateOf<String?>(null) }
    val homeStore = remember(context) { HomePreferencesStore(context.applicationContext) }
    var homePreferences by remember { mutableStateOf(homeStore.read()) }
    val appVersion = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown" }
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US) }
    var pendingReaderFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val readBook = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        reader.refresh(); repository.reload()
        // Closing the reader is a natural pause: back up its notes and highlights soon, if anything changed.
        LocalBackupScheduler.soon(context)
    }
    fun openReader(id: String) {
        readBook.launch(android.content.Intent(context, ReaderActivity::class.java).putExtra("documentId", id))
    }
    LaunchedEffect(reader.importedId) {
        reader.importedId?.let { id -> reader.importedId = null; openReader(id) }
    }
    val importBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) reader.import(uri, pendingReaderFolderId)
        pendingReaderFolderId = null
    }
    var homeBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    val importing = repository.importing
    var importError by remember { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPenSettings by remember { mutableStateOf(false) }
    if (showPenSettings) GlobalPenSettingsDialog { showPenSettings = false }
    var enableAfterFolderChoice by rememberSaveable { mutableStateOf(true) }
    var libraryRestoreComplete by remember { mutableStateOf<Int?>(null) }
    var backupExportMessage by remember { mutableStateOf<String?>(null) }
    var pendingLibraryBackup by remember { mutableStateOf<java.io.File?>(null) }
    val notebooks = repository.notebooks
    val libraryStateHolder = rememberSaveableStateHolder()
    val importDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            if (withContext(Dispatchers.IO) { isReaderDocument(context, uri) }) {
                reader.import(uri)
                return@launch
            }
            val archiveType = withContext(Dispatchers.IO) { files.backupArchiveType(uri) }
            if (archiveType == "library") repository.importLibraryDocument(uri) else repository.importDocument(uri)
        }
    }
    val chooseBackupFolder = rememberLauncherForActivityResult(object : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: android.content.Context, input: Uri?): android.content.Intent =
            super.createIntent(context, input).putExtra(android.content.Intent.EXTRA_LOCAL_ONLY, true)
    }) { uri ->
        if (uri != null) backup.chooseFolder(uri, enableAfterFolderChoice)
    }
    val saveLibraryBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val archive = pendingLibraryBackup
        pendingLibraryBackup = null
        if (uri == null) archive?.delete() else if (archive != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { files.writeToUri(archive, uri) }
                backup.recordExport(uri.lastPathSegment?.substringAfterLast('/') ?: "Folio full library backup")
                backupExportMessage = "Your full Folio library backup was exported. Keep this file somewhere safe."
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { backupExportMessage = error.message ?: "Could not export the library backup." }
            finally { archive.delete() }
        }
    }
    fun exportFullLibraryBackup() {
        if (repository.isSaving || backup.running || pendingLibraryBackup != null) return
        scope.launch {
            try {
                val archive = withContext(Dispatchers.IO) { files.exportLibraryBackup(repository.notebooks, repository.library) }
                pendingLibraryBackup = archive
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                saveLibraryBackup.launch("Folio-Full-Library-$stamp.${FolioFormat.EXTENSION}")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { backupExportMessage = error.message ?: "Could not create the library backup." }
        }
    }
    val backupReminder = backup.state.needsReminder(System.currentTimeMillis())
    // Leaving a notebook (back to home, or to another notebook) schedules a backup two minutes later when something changed.
    var previousNotebookId by remember { mutableStateOf(openNotebookId) }
    LaunchedEffect(openNotebookId) {
        if (previousNotebookId != null && previousNotebookId != openNotebookId) LocalBackupScheduler.soon(context)
        previousNotebookId = openNotebookId
    }
    LaunchedEffect(showSettings) { if (showSettings) backup.refreshVersions() }
    fun openBackupFolderPicker(enable: Boolean) {
        enableAfterFolderChoice = enable
        chooseBackupFolder.launch(LocalBackupStore.initialFolder(backup.state.folderUri))
    }
    LaunchedEffect(repository.importedNotebookId) {
        repository.importedNotebookId?.let { id ->
            openNotebookId = id
            showSettings = false
            repository.consumeImportedNotebook(id)
        }
    }
    LaunchedEffect(repository.importedLibraryCount) {
        if (repository.importedLibraryCount >= 0) {
            libraryRestoreComplete = repository.importedLibraryCount
            repository.consumeImportedLibraryCount()
        }
    }
    // Trash retention is applied once per launch, after both libraries have loaded.
    var trashPurged by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(repository.loading, reader.loading) {
        if (trashPurged || repository.loading || repository.loadError != null || reader.loading) return@LaunchedEffect
        trashPurged = true
        val now = System.currentTimeMillis()
        val purge = LibraryTrash.purge(repository.notebooks, repository.library, now, homePreferences.trashRetentionDays)
        if (purge.changed(repository.notebooks, repository.library)) repository.saveAll(purge.notes, purge.library)
        reader.purgeTrash(now, homePreferences.trashRetentionDays, purge.removedFolderIds)
    }
    val current = notebooks.firstOrNull { it.id == openNotebookId && it.deletedAt == 0L }
    BackHandler(enabled = current == null && homeBack != null && !importing) { homeBack?.invoke() }
    BackHandler(enabled = current != null && !importing) { openNotebookId = null }
    BackHandler(enabled = showSettings && !importing) { showSettings = false }

    MaterialTheme(shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
    ), typography = folioTypography(), colorScheme = lightColorScheme(
        primary = FolioBrand.Sage, onPrimary = Color.White, primaryContainer = FolioBrand.SageSoft, onPrimaryContainer = FolioBrand.Ink,
        secondary = FolioBrand.Ink, onSecondary = Color.White, secondaryContainer = FolioBrand.SageSoft, onSecondaryContainer = FolioBrand.Ink,
        background = FolioBrand.Paper, onBackground = FolioBrand.Ink, surface = Color.White, onSurface = FolioBrand.Ink,
        onSurfaceVariant = FolioBrand.Muted, outline = Color(0xFFCBC5B8), outlineVariant = FolioBrand.Hairline,
        surfaceContainerLowest = Color.White, surfaceContainerLow = FolioBrand.Paper, surfaceContainer = Color(0xFFF7F3EA),
        surfaceContainerHigh = Color(0xFFF3EEE3), surfaceContainerHighest = FolioBrand.Cream,
    )) {
        Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).semantics { testTagsAsResourceId = true }, color = Color.White) {
            when {
                repository.loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = FolioBrand.Sage)
                    Text("Opening notebooks…", Modifier.padding(top = 18.dp))
                }
                repository.loadError != null -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Could not open your notebooks", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Text("Your existing local data has been preserved. Close and reopen Folio to try again.", Modifier.padding(top = 14.dp))
                    Text(repository.loadError.orEmpty(), Modifier.padding(top = 14.dp), color = ChromeMuted, fontSize = 12.sp)
                }
                showSettings -> {
                    val settings = backup.state
                    com.allient.folio.home.BackupSettingsScreen(
                        state = com.allient.folio.home.BackupSettingsUi(
                            enabled = settings.enabled, folder = settings.folderLabel,
                            status = backupStatus(settings.mostRecentBackup, backup.versions),
                            lastFile = settings.mostRecentFile, running = backup.running, choosingFolder = backup.choosingFolder,
                            error = backup.folderError ?: settings.error, savingNotes = repository.isSaving,
                            versions = backup.versions.map { com.allient.folio.home.BackupVersionUi(it.id, backupDate(it.createdAt), formatBackupSize(it.size)) },
                        ),
                        onBack = { showSettings = false },
                        onEnabled = { if (it) openBackupFolderPicker(true) else backup.disable() },
                        onChooseFolder = { openBackupFolderPicker(settings.enabled) },
                        onBackUpNow = backup::backUpNow,
                        onRestore = { importDocument.launch(arrayOf("*/*")) },
                        onExportBackup = ::exportFullLibraryBackup,
                        // Uses the regular library import, which merges copies and never erases.
                        onRestoreVersion = { id -> repository.importLibraryDocument(Uri.parse(id)) },
                    )
                }
                current == null -> libraryStateHolder.SaveableStateProvider("library") { Column(Modifier.fillMaxSize()) {
                    if (backupReminder) {
                        Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF4D6)) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (backup.state.enabled) "Recent changes haven’t been backed up for 3 days. Check Smart backups in Settings, or export a copy now." else "Recent changes haven’t been backed up for 3 days. Export a copy, or turn on Smart backups in Settings.", Modifier.weight(1f), fontSize = 13.sp, color = Color(0xFF4B3B13))
                                TextButton(onClick = ::exportFullLibraryBackup, enabled = !repository.isSaving && !backup.running) { Text("Export backup") }
                            }
                        }
                    }
                    if (repository.saveError != null) {
                        Row(Modifier.fillMaxWidth().background(Color(0xFFF7E6E4)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Changes could not be saved: ${repository.saveError}", Modifier.weight(1f), fontSize = 12.sp)
                            TextButton(onClick = { repository.saveNotes(repository.notebooks) }) { Text("Retry") }
                        }
                    }
                    if (reader.busy) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(Modifier.weight(1f), color = ChromeInk)
                        Text("Importing book…", Modifier.padding(start = 12.dp), fontSize = 12.sp, color = ChromeMuted)
                    }
                    val homeBooks = remember(reader.documents) { reader.documents.map { doc ->
                        com.allient.folio.home.HomeBook(doc.id, doc.title, doc.author, doc.format, doc.folderId, doc.favorite, doc.openedAt, doc.createdAt.takeIf { it > 0 } ?: doc.openedAt,
                            when { doc.openedAt > 0 -> "Read ${dateFormat.format(java.util.Date(doc.openedAt))}"; doc.createdAt > 0 -> "Added ${dateFormat.format(java.util.Date(doc.createdAt))}"; else -> "New" },
                            doc.progress.toFloat(), doc.pageCount, !java.io.File(doc.path).isFile, doc.deletedAt)
                    } }
                    com.allient.folio.home.NotebookHome(
                        notebooks = notebooks.map { note -> com.allient.folio.home.HomeNotebook(
                            note.id, note.title, note.pages.size, note.folderId, note.favorite, note.openedAt, note.updatedAt,
                            dateFormat.format(java.util.Date(note.updatedAt)),
                            (note.tags + note.pages.flatMap { page -> page.strokes.flatMap { it.tags } }).distinct(),
                            note.pages.joinToString(" ") { page -> page.outline + " " + page.texts.joinToString(" ") { it.text } },
                            currentPage = note.pages.indexOfFirst { it.id == note.lastViewedPageId }.coerceAtLeast(0) + 1,
                            infinite = note.infiniteCanvas, createdAt = note.createdAt, deletedAt = note.deletedAt,
                        ) },
                        books = homeBooks,
                        bookCover = { id, modifier -> reader.documents.firstOrNull { it.id == id }?.let { BookCover(it, modifier) } },
                        onOpenBook = ::openReader,
                        onImportBook = { folderId ->
                            pendingReaderFolderId = folderId
                            importBook.launch(arrayOf("application/pdf", "application/epub+zip"))
                        },
                        onMoveBook = { id, folder -> reader.moveDocument(id, folder) },
                        onFavoriteBook = { id, favorite -> reader.setFavorite(id, favorite) },
                        onTrash = { kind, ids ->
                            val now = System.currentTimeMillis()
                            when (kind) {
                                "note" -> repository.saveNotes(LibraryTrash.trashNotebooks(repository.notebooks, ids, now))
                                "book" -> ids.forEach { reader.trashDocument(it, now) }
                                "folder" -> repository.updateLibrary(ids.fold(repository.library) { library, id -> LibraryTrash.trashFolder(library, id, now) })
                            }
                        },
                        onRestore = { kind, id ->
                            when (kind) {
                                "note" -> repository.saveNotes(LibraryTrash.restoreNotebook(repository.notebooks, id))
                                "book" -> reader.restoreDocument(id)
                                "folder" -> repository.updateLibrary(LibraryTrash.restoreFolder(repository.library, id))
                            }
                        },
                        onDeleteForever = { kind, ids ->
                            when (kind) {
                                "note" -> repository.saveNotes(LibraryTrash.deleteNotebooksForever(repository.notebooks, ids))
                                "book" -> reader.deleteForever(ids)
                                "folder" -> {
                                    val (kept, library) = LibraryTrash.deleteFoldersForever(repository.notebooks, repository.library, ids)
                                    repository.saveAll(kept, library)
                                    reader.purgeTrash(System.currentTimeMillis(), 0, ids)
                                }
                            }
                        },
                        onEmptyTrash = {
                            val folderIds = repository.library.folders.filter { it.deletedAt > 0 }.map { it.id }.toSet()
                            val (kept, library) = LibraryTrash.deleteFoldersForever(repository.notebooks, repository.library, folderIds)
                            repository.saveAll(LibraryTrash.deleteNotebooksForever(kept, kept.filter { it.deletedAt > 0 }.map { it.id }.toSet()), library)
                            reader.deleteForever(reader.documents.filter { it.deletedAt > 0 }.map { it.id })
                            reader.purgeTrash(System.currentTimeMillis(), 0, folderIds)
                        },
                        preferences = homePreferences,
                        onPreferencesChange = { homePreferences = it; homeStore.write(it) },
                        now = System::currentTimeMillis,
                        formatDate = { dateFormat.format(java.util.Date(it)) },
                        appVersion = appVersion,
                        onBackAction = { homeBack = it },
                        settings = repository.library,
                        onSettingsChange = repository::updateLibrary,
                        isSaving = repository.isSaving,
                        onImport = { importDocument.launch(arrayOf("*/*")) },
                        onOpen = { id ->
                            repository.saveNotes(repository.notebooks.map { if (it.id == id) it.copy(openedAt = System.currentTimeMillis()) else it })
                            openNotebookId = id
                        },
                        onCreate = { name, template, folder, infiniteCanvas ->
                            val created = Notebook(title = name, pages = listOf(NotebookPage(template = if (infiniteCanvas && !template.startsWith("inf-")) "blank" else template)), folderId = folder, openedAt = System.currentTimeMillis(), infiniteCanvas = infiniteCanvas)
                            repository.saveNotes(listOf(created) + notebooks)
                            openNotebookId = created.id
                        },
                        onFavorite = { id -> repository.saveNotes(repository.notebooks.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }) },
                        onMove = { id, folder -> repository.saveNotes(repository.notebooks.map { if (it.id == id) it.copy(folderId = folder) else it }) },
                        onRename = { id, title -> repository.saveNotes(repository.notebooks.map { if (it.id == id) it.copy(title = title, updatedAt = System.currentTimeMillis()) else it }) },
                        onCopy = { id -> repository.notebooks.firstOrNull { it.id == id }?.let { original ->
                            val copied = original.copy(id = java.util.UUID.randomUUID().toString(), title = "${original.title} copy", updatedAt = System.currentTimeMillis(), openedAt = 0, lastViewedPageId = null,
                                pages = original.pages.map { page -> page.copy(id = java.util.UUID.randomUUID().toString(),
                                    strokes = page.strokes.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                                    texts = page.texts.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                                    images = page.images.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                                ) }, attachments = original.attachments.map { it.copy(id = java.util.UUID.randomUUID().toString()) })
                            repository.saveNotes(listOf(copied) + repository.notebooks)
                        } },
                        onExport = { id, format -> repository.notebooks.firstOrNull { it.id == id }?.let { note ->
                            scope.launch {
                                try {
                                    val output = withContext(Dispatchers.IO) { when (format) {
                                        "png" -> files.exportPng(note, note.pages.firstOrNull { it.id == note.lastViewedPageId } ?: note.pages.first())
                                        "folio" -> files.exportBackup(note)
                                        else -> files.exportPdf(note)
                                    } }
                                    files.shareFile(output, when (format) { "png" -> "image/png"; "folio" -> "application/zip"; else -> "application/pdf" })
                                } catch (error: CancellationException) { throw error }
                                catch (error: Exception) { importError = error.message ?: "Could not export this notebook." }
                            }
                        } },
                        onOpenSettings = { showSettings = true },
                        onOpenPenSettings = { showPenSettings = true },
                        onExportLibraryBackup = ::exportFullLibraryBackup,
                        templatePreview = { id, modifier -> NotebookPageThumbnail(NotebookPage(template = id), modifier) },
                        thumbnail = { id, modifier -> notebooks.firstOrNull { it.id == id }?.let { note ->
                            (note.pages.firstOrNull { it.id == note.lastViewedPageId } ?: note.pages.firstOrNull())?.let { NotebookPageThumbnail(it, modifier) }
                        } },
                    )
                } }
                else -> NotebookEditor(
                    notebook = current,
                    repository = repository,
                    onBack = { openNotebookId = null },
                    onNavigateNotebook = { id, pageId ->
                        repository.notebooks.firstOrNull { it.id == id && it.deletedAt == 0L }?.let { target ->
                            if (pageId != null && (target.pages.any { it.id == pageId } || target.pins.any { it.id == pageId }))
                                repository.saveNotes(repository.notebooks.map { if (it.id == id) it.copy(lastViewedPageId = pageId) else it })
                            openNotebookId = id
                        }
                    },
                    onNotebookChange = { changed -> repository.saveNotes(repository.notebooks.map { if (it.id == changed.id) changed else it }) },
                    isSaving = repository.isSaving,
                    saveError = repository.saveError,
                    startInFocusMode = homePreferences.startFullscreen,
                )
            }
            if (importing) {
                Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color.White) {
                        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = FolioBrand.Sage)
                            Text("Importing document…", Modifier.padding(top = 20.dp))
                            Text("Large PDFs can take a moment.", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = ChromeMuted)
                        }
                    }
                }
            }
            (importError ?: repository.importError ?: repository.audioError)?.let { message ->
                AlertDialog(onDismissRequest = { importError = null; repository.clearImportError(); repository.clearAudioError() }, title = { Text("Operation failed") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { importError = null; repository.clearImportError(); repository.clearAudioError() }) { Text("OK") } })
            }
            libraryRestoreComplete?.let { count ->
                AlertDialog(onDismissRequest = { libraryRestoreComplete = null }, title = { Text("Library restored") }, text = { Text("Added $count notebooks and their folders. Existing notebooks were kept.") }, confirmButton = { TextButton(onClick = { libraryRestoreComplete = null }) { Text("OK") } })
            }
            reader.error?.let { message ->
                AlertDialog(onDismissRequest = { reader.error = null }, title = { Text("Reading library") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { reader.error = null }) { Text("OK") } })
            }
            backupExportMessage?.let { message ->
                AlertDialog(onDismissRequest = { backupExportMessage = null }, title = { Text("Library backup") }, text = { Text(message) },
                    confirmButton = { TextButton(onClick = { backupExportMessage = null }) { Text("OK") } })
            }
        }
    }
}

private fun isToday(time: Long): Boolean {
    val day = java.util.Calendar.getInstance().apply { timeInMillis = time }
    val today = java.util.Calendar.getInstance()
    return day.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && day.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun backupDate(time: Long): String {
    val clock = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(time))
    return if (isToday(time)) "Today $clock" else "${java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(java.util.Date(time))} $clock"
}

internal fun formatBackupSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 10L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> "${maxOf(1, bytes / 1024)} KB"
}

/** "Last backup 12:41 · 3 versions · 84 MB". */
private fun backupStatus(lastBackup: Long, versions: List<BackupVersion>): String {
    if (lastBackup <= 0) return "Not backed up yet"
    val clock = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(lastBackup))
    val time = if (isToday(lastBackup)) clock else "${java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(lastBackup))}, $clock"
    return buildString {
        append("Last backup ").append(time)
        if (versions.isNotEmpty()) append(" · ${versions.size} ${if (versions.size == 1) "version" else "versions"} · ${formatBackupSize(versions.sumOf { it.size })}")
    }
}
