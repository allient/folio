package com.allient.folio

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import androidx.compose.ui.platform.LocalResources
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
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLocales.wrap(newBase))
    private val penFilter = PenPressureFilter()
    private lateinit var updates: PlayUpdates

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = penFilter.dispatch(event) { super.dispatchTouchEvent(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PenSettings.init(this)
        AppAnalytics.init(this)
        enableEdgeToEdge()
        hideSystemBars()
        val repository = ViewModelProvider(this)[NotebookViewModel::class.java]
        val backup = ViewModelProvider(this)[LocalBackupViewModel::class.java]
        val reader = ViewModelProvider(this)[ReaderViewModel::class.java]
        LocalBackupScheduler.observeProcess(this)
        updates = PlayUpdates(this)
        if (savedInstanceState == null) updates.check()
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalPenThreshold provides PenSettings.thresholdState.floatValue, LocalInAppBrowser provides { url -> openInAppBrowser(url) }) {
                Box(Modifier.fillMaxSize()) {
                    FolioApp(repository, backup, reader)
                    UpdateReadyBar(updates)
                    UsageStatsPrompt()
                }
            }
        }
        // Recreation (rotation, process restore) or relaunch from Recents delivers the same intent again.
        val fromHistory = intent.flags and android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory && intent.action == android.content.Intent.ACTION_VIEW) intent.data?.let(reader::import)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == android.content.Intent.ACTION_VIEW) intent.data?.let { ViewModelProvider(this)[ReaderViewModel::class.java].import(it) }
    }

    override fun onResume() {
        super.onResume()
        updates.resume()
    }

    override fun onDestroy() {
        updates.dispose()
        super.onDestroy()
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
    val resources = LocalResources.current
    val files = remember(context) { NotebookFiles(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var openNotebookId by rememberSaveable { mutableStateOf<String?>(null) }
    val homeStore = remember(context) { HomePreferencesStore(context.applicationContext) }
    var homePreferences by remember { mutableStateOf(homeStore.read()) }
    val appVersion = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: resources.getString(R.string.main_version_unknown) }
    val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US) }
    var pendingReaderFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val readBook = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Re-reading every notebook took seconds behind a full-screen spinner. Only reload when the reader's notebook
        // editor saved something, and keep the library on screen meanwhile.
        reader.refresh()
        if (result.data?.getBooleanExtra(ReaderActivity.EXTRA_NOTEBOOKS_CHANGED, true) != false) repository.reload(showLoading = false)
        // Closing the reader is a natural pause: back up its notes and highlights soon, if anything changed.
        LocalBackupScheduler.soon(context)
    }
    val boardStore = remember(context) { LiveBoardStore(context.applicationContext) }
    fun boardList() = boardStore.list().map { com.allient.folio.home.HomeBoard(it.id, it.title, it.updatedAt) }
    var homeBoards by remember { mutableStateOf(boardList()) }
    fun refreshBoards() { homeBoards = boardList() }
    val boardScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshBoards() }
    fun openBoard(id: String) = boardScreen.launch(android.content.Intent(context, LiveBoardActivity::class.java).putExtra(LiveBoardActivity.EXTRA_BOARD_ID, id))
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
    var showSmartBackupGuide by rememberSaveable { mutableStateOf(false) }
    var libraryRestoreComplete by remember { mutableStateOf<Int?>(null) }
    var backupExportMessage by remember { mutableStateOf<String?>(null) }
    // Saveable: the save picker can outlive this composition (rotation while it is open).
    var pendingLibraryBackup by rememberSaveable { mutableStateOf<String?>(null) }
    val notebooks = repository.notebooks
    val libraryStateHolder = rememberSaveableStateHolder()
    // A PDF opened as a notebook: every PDF page becomes a page you write on (the reader is not involved).
    val importPdfNotebook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) repository.importDocument(uri) }
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
        val archive = pendingLibraryBackup?.let { java.io.File(it) }
        pendingLibraryBackup = null
        if (uri == null) archive?.delete() else if (archive != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { files.writeToUri(archive, uri) }
                backup.recordExport(uri.lastPathSegment?.substringAfterLast('/') ?: resources.getString(R.string.main_backup_file_fallback))
                backupExportMessage = resources.getString(R.string.main_backup_exported)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { backupExportMessage = error.message ?: resources.getString(R.string.main_backup_export_failed) }
            finally { archive.delete() }
        }
    }
    fun exportFullLibraryBackup() {
        if (repository.isSaving || backup.running || pendingLibraryBackup != null) return
        scope.launch {
            try {
                val archive = withContext(Dispatchers.IO) { files.exportLibraryBackup(repository.notebooks, repository.library) }
                pendingLibraryBackup = archive.absolutePath
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                saveLibraryBackup.launch("Folio-Full-Library-$stamp.${FolioFormat.EXTENSION}")
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { backupExportMessage = error.message ?: resources.getString(R.string.main_backup_create_failed) }
        }
    }
    val backupReminder = backup.state.needsReminder(System.currentTimeMillis())
    // Leaving a notebook (back to home, or to another notebook) schedules a backup two minutes later when something changed.
    var previousNotebookId by remember { mutableStateOf(openNotebookId) }
    LaunchedEffect(openNotebookId) {
        AppAnalytics.screen(if (openNotebookId == null) "library" else "notebook")
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

    FolioTheme(homePreferences) {
        Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).semantics { testTagsAsResourceId = true }, color = Color.White) {
            when {
                repository.loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = FolioBrand.Sage)
                    Text(stringResource(R.string.main_opening), Modifier.padding(top = 18.dp))
                }
                repository.loadError != null -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.main_open_failed_title), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.main_open_failed_message), Modifier.padding(top = 14.dp))
                    Text(repository.loadError.orEmpty(), Modifier.padding(top = 14.dp), color = ChromeMuted, fontSize = 12.sp)
                }
                showSettings -> {
                    val settings = backup.state
                    com.allient.folio.home.BackupSettingsScreen(
                        state = com.allient.folio.home.BackupSettingsUi(
                            enabled = settings.enabled, folder = settings.folderLabel,
                            status = backupStatus(resources, settings.mostRecentBackup, backup.versions),
                            lastFile = settings.mostRecentFile, running = backup.running, choosingFolder = backup.choosingFolder,
                            error = backup.folderError ?: settings.error, savingNotes = repository.isSaving,
                            versions = backup.versions.map { com.allient.folio.home.BackupVersionUi(it.id, backupDate(resources, it.createdAt), formatBackupSize(resources, it.size)) },
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
                        Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("backup-reminder"), shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF4D6)) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(if (backup.state.enabled) R.string.main_reminder_enabled else R.string.main_reminder_disabled),
                                    Modifier.weight(1f), fontSize = 13.sp, color = Color(0xFF4B3B13))
                                TextButton(onClick = ::exportFullLibraryBackup, enabled = !repository.isSaving && !backup.running) { Text(stringResource(R.string.main_export_backup)) }
                                if (backup.state.enabled) TextButton(onClick = { showSettings = true }, Modifier.testTag("backup-reminder-settings")) { Text(stringResource(R.string.main_settings)) }
                                else Button(onClick = { showSmartBackupGuide = true }, Modifier.testTag("backup-reminder-turn-on"), enabled = !backup.choosingFolder) { Text(stringResource(R.string.main_turn_on)) }
                            }
                        }
                    }
                    if (repository.saveError != null) {
                        Row(Modifier.fillMaxWidth().background(Color(0xFFF7E6E4)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.main_save_failed, repository.saveError.orEmpty()), Modifier.weight(1f), fontSize = 12.sp)
                            TextButton(onClick = { repository.saveNotes(repository.notebooks) }) { Text(stringResource(R.string.main_retry)) }
                        }
                    }
                    if (reader.busy) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(Modifier.weight(1f), color = ChromeInk)
                        Text(stringResource(R.string.main_importing_book), Modifier.padding(start = 12.dp), fontSize = 12.sp, color = ChromeMuted)
                    }
                    val homeBooks = remember(reader.documents, resources) { reader.documents.map { doc ->
                        com.allient.folio.home.HomeBook(doc.id, doc.title, doc.author, doc.format, doc.folderId, doc.favorite, doc.openedAt, doc.createdAt.takeIf { it > 0 } ?: doc.openedAt,
                            when {
                                doc.openedAt > 0 -> resources.getString(R.string.main_book_read, dateFormat.format(java.util.Date(doc.openedAt)))
                                doc.createdAt > 0 -> resources.getString(R.string.main_book_added, dateFormat.format(java.util.Date(doc.createdAt)))
                                else -> resources.getString(R.string.main_book_new)
                            },
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
                        boards = homeBoards,
                        boardPreview = { id, modifier -> LiveBoardPreview(boardStore, id, homeBoards.firstOrNull { it.id == id }?.updatedAt ?: 0L, modifier) },
                        onOpenBoard = ::openBoard,
                        onNewBoard = { title -> val board = boardStore.create(title); refreshBoards(); openBoard(board.id) },
                        onRenameBoard = { id, title -> boardStore.rename(id, title); refreshBoards() },
                        onDeleteBoard = { id -> boardStore.delete(id); refreshBoards() },
                        onImportPdfNotebook = { importPdfNotebook.launch(arrayOf("application/pdf", "application/epub+zip")) },
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
                        // The usage-statistics choice lives in AppAnalytics (the first-run prompt can change it).
                        preferences = homePreferences.copy(shareUsageStats = AppAnalytics.collecting),
                        onPreferencesChange = {
                            if (it.shareUsageStats != AppAnalytics.collecting) AppAnalytics.setEnabled(it.shareUsageStats)
                            homePreferences = it; homeStore.write(it)
                            // Applying a language restarts the activity in it (the choice itself is stored by AppLocales).
                            if (it.language != AppLocales.choice(context)) (context as? android.app.Activity)?.let { activity -> AppLocales.choose(activity, it.language) }
                        },
                        now = System::currentTimeMillis,
                        formatDate = { dateFormat.format(java.util.Date(it)) },
                        appVersion = appVersion,
                        onBackAction = { homeBack = it },
                        settings = repository.library,
                        onSettingsChange = repository::updateLibrary,
                        isSaving = repository.isSaving,
                        onImport = { importDocument.launch(arrayOf("*/*")) },
                        onOpen = { id ->
                            repository.markOpened(id)
                            openNotebookId = id
                        },
                        onCreate = { name, template, folder, infiniteCanvas ->
                            val created = Notebook(title = name, pages = listOf(NotebookPage(template = if (infiniteCanvas && !template.startsWith("inf-")) "blank" else template)), folderId = folder, openedAt = System.currentTimeMillis(), infiniteCanvas = infiniteCanvas)
                            repository.saveNotes(listOf(created) + notebooks)
                            AppAnalytics.event("notebook_created", "kind" to if (infiniteCanvas) "canvas" else "pages")
                            openNotebookId = created.id
                        },
                        onFavorite = { id -> repository.saveNotes(repository.notebooks.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }) },
                        onMove = { id, folder -> repository.saveNotes(repository.notebooks.map { if (it.id == id) it.copy(folderId = folder) else it }) },
                        onRename = { id, title -> repository.saveNotes(repository.notebooks.map { if (it.id == id) it.copy(title = title, updatedAt = System.currentTimeMillis()) else it }) },
                        onCopy = { id -> repository.notebooks.firstOrNull { it.id == id }?.let { original ->
                            val copied = original.copy(id = java.util.UUID.randomUUID().toString(), title = resources.getString(R.string.main_notebook_copy, original.title),updatedAt = System.currentTimeMillis(), openedAt = 0, lastViewedPageId = null,
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
                                catch (error: Exception) { importError = error.message ?: resources.getString(R.string.main_export_failed) }
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
                            repository.markOpened(id, pageId)
                            openNotebookId = id
                        }
                    },
                    onNotebookChange = { changed -> repository.saveNotes(repository.notebooks.map { if (it.id == changed.id) changed else it }) },
                    saveError = repository.saveError,
                    startInFocusMode = homePreferences.startFullscreen,
                )
            }
            if (importing) {
                Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                    FolioDialogSurface {
                        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = FolioBrand.Sage)
                            Text(stringResource(R.string.main_importing_document), Modifier.padding(top = 20.dp))
                            Text(stringResource(R.string.main_large_pdfs), Modifier.padding(top = 8.dp), fontSize = 12.sp, color = ChromeMuted)
                        }
                    }
                }
            }
            (importError ?: repository.importError ?: repository.audioError)?.let { message ->
                AlertDialog(onDismissRequest = { importError = null; repository.clearImportError(); repository.clearAudioError() }, title = { Text(stringResource(R.string.main_operation_failed)) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { importError = null; repository.clearImportError(); repository.clearAudioError() }) { Text(stringResource(R.string.main_ok)) } })
            }
            libraryRestoreComplete?.let { count ->
                AlertDialog(onDismissRequest = { libraryRestoreComplete = null }, title = { Text(stringResource(R.string.main_library_restored)) }, text = { Text(pluralStringResource(R.plurals.main_library_restored_message, count, count)) }, confirmButton = { TextButton(onClick = { libraryRestoreComplete = null }) { Text(stringResource(R.string.main_ok)) } })
            }
            reader.error?.let { message ->
                AlertDialog(onDismissRequest = { reader.error = null }, title = { Text(stringResource(R.string.main_reading_library)) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { reader.error = null }) { Text(stringResource(R.string.main_ok)) } })
            }
            if (showSmartBackupGuide) AlertDialog(onDismissRequest = { showSmartBackupGuide = false }, modifier = Modifier.testTag("smart-backup-guide"),
                title = { Text(stringResource(R.string.main_smart_backup_title)) },
                text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.main_smart_backup_intro))
                    Text(stringResource(R.string.main_smart_backup_step1))
                    Text(stringResource(R.string.main_smart_backup_step2))
                    Text(stringResource(R.string.main_smart_backup_step3))
                    Text(stringResource(R.string.main_smart_backup_footer), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } },
                confirmButton = { Button(onClick = { showSmartBackupGuide = false; openBackupFolderPicker(true) }, Modifier.testTag("smart-backup-choose-folder")) { Text(stringResource(R.string.main_choose_folder)) } },
                dismissButton = { TextButton(onClick = { showSmartBackupGuide = false }) { Text(stringResource(R.string.main_not_now)) } })
            if (!showSettings) backup.folderError?.let { message ->
                AlertDialog(onDismissRequest = { backup.clearFolderError() }, title = { Text(stringResource(R.string.main_smart_backups)) }, text = { Text(message) },
                    confirmButton = { TextButton(onClick = { backup.clearFolderError(); showSmartBackupGuide = true }) { Text(stringResource(R.string.main_try_again)) } },
                    dismissButton = { TextButton(onClick = { backup.clearFolderError() }) { Text(stringResource(R.string.main_close)) } })
            }
            backupExportMessage?.let { message ->
                AlertDialog(onDismissRequest = { backupExportMessage = null }, title = { Text(stringResource(R.string.main_library_backup)) }, text = { Text(message) },
                    confirmButton = { TextButton(onClick = { backupExportMessage = null }) { Text(stringResource(R.string.main_ok)) } })
            }
        }
    }
}

private fun isToday(time: Long): Boolean {
    val day = java.util.Calendar.getInstance().apply { timeInMillis = time }
    val today = java.util.Calendar.getInstance()
    return day.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && day.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
}

/** A date in the app language's own order and words ("Thu, Nov 26", "jue, 26 nov"); AppLocales sets the default locale. */
private fun localDate(skeleton: String, time: Long): String {
    val locale = java.util.Locale.getDefault()
    return java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(java.util.Date(time))
}

private fun backupDate(resources: Resources, time: Long): String {
    val clock = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(time))
    return if (isToday(time)) resources.getString(R.string.main_today_at, clock)
    else resources.getString(R.string.main_date_time, localDate("EEEMMMd", time), clock)
}

/** Sizes with the app language's decimal separator ("1,5 GB" in Spanish and Portuguese). */
internal fun formatBackupSize(resources: Resources, bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> resources.getString(R.string.main_size_gb, bytes / (1024.0 * 1024 * 1024))
    bytes >= 10L * 1024 * 1024 -> resources.getString(R.string.main_size_mb, (bytes / (1024 * 1024)).toInt())
    bytes >= 1024L * 1024 -> resources.getString(R.string.main_size_mb_decimal, bytes / (1024.0 * 1024))
    else -> resources.getString(R.string.main_size_kb, maxOf(1L, bytes / 1024).toInt())
}

/** "Last backup 12:41 · 3 versions · 84 MB". */
private fun backupStatus(resources: Resources, lastBackup: Long, versions: List<BackupVersion>): String {
    if (lastBackup <= 0) return resources.getString(R.string.main_not_backed_up)
    val clock = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(lastBackup))
    val time = if (isToday(lastBackup)) clock else resources.getString(R.string.main_day_time, localDate("MMMd", lastBackup), clock)
    return if (versions.isEmpty()) resources.getString(R.string.main_last_backup, time)
    else resources.getQuantityString(R.plurals.main_last_backup_versions, versions.size, time, versions.size, formatBackupSize(resources, versions.sumOf { it.size }))
}
