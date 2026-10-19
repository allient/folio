package com.allient.folio.home

import com.allient.folio.penPressureFilter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allient.folio.InfinitePaper
import androidx.compose.foundation.shape.CircleShape
import com.allient.folio.NotebookTemplateCatalog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import kotlin.math.roundToInt

private val Ink = HomeInk
private val Muted = HomeMuted
private val Line = HomeLine
private enum class HomeView { Home, Recent, Favorites }
private val papers = NotebookTemplateCatalog.all
internal enum class Symbol { Trash, Copy, Rename, Share, Folder, All, Star, Clock, Hub, Tag, Search, Menu, Back, Plus, Close, More, Download, Sidebar, ChevronDown, ChevronRight, Check, Gear, Info, Book, Notes, Dots, Select, Open }
private val segments = listOf("all" to "All", "notes" to "Notes", "canvas" to "Canvas", "books" to "Books")

private sealed interface Entry { val key: String }
private data class FolderEntry(val folder: NoteFolder?, val detail: String) : Entry { override val key get() = "folder:${folder?.id ?: "root"}" }
private data class NoteEntry(val note: HomeNotebook) : Entry { override val key get() = "note:${note.id}" }
private data class BookEntry(val book: HomeBook) : Entry { override val key get() = "book:${book.id}" }
private data class Section(val key: String, val title: String, val entries: List<Entry>)

private fun <T> List<T>.sortedFor(sort: String, recent: Boolean, name: (T) -> String, edited: (T) -> Long, created: (T) -> Long, type: (T) -> String, opened: (T) -> Long): List<T> = when {
    recent -> sortedByDescending(opened)
    sort == HomeSort.Name -> sortedBy { name(it).lowercase() }
    sort == HomeSort.Created -> sortedByDescending(created)
    sort == HomeSort.Type -> sortedWith(compareBy(type).thenBy { name(it).lowercase() })
    else -> sortedByDescending(edited)
}

@Composable
fun NotebookHome(
    notebooks: List<HomeNotebook>, settings: LibrarySettings, isSaving: Boolean,
    onSettingsChange: (LibrarySettings) -> Unit, onImport: () -> Unit,
    onOpen: (String) -> Unit, onCreate: (String, String, String?, Boolean) -> Unit,
    /** Moves items of one kind ("note", "book" or "folder") to Trash. */
    onTrash: (String, Set<String>) -> Unit, onFavorite: (String) -> Unit, onMove: (String, String?) -> Unit,
    thumbnail: @Composable (String, Modifier) -> Unit,
    onBackAction: ((() -> Unit)?) -> Unit = {},
    onRename: (String, String) -> Unit,
    onCopy: (String) -> Unit,
    onExport: (String, String) -> Unit,
    onOpenSettings: () -> Unit = {},
    /** Opens the host's pen pressure settings; the Preferences screen hides the row when null. */
    onOpenPenSettings: (() -> Unit)? = null,
    onExportLibraryBackup: () -> Unit = {},
    templatePreview: (@Composable (String, Modifier) -> Unit)? = null,
    books: List<HomeBook> = emptyList(),
    bookCover: @Composable (String, Modifier) -> Unit = { _, _ -> },
    onOpenBook: (String) -> Unit = {}, onImportBook: (String?) -> Unit = {},
    onMoveBook: (String, String?) -> Unit = { _, _ -> }, onFavoriteBook: (String, Boolean) -> Unit = { _, _ -> },
    onRestore: (String, String) -> Unit = { _, _ -> }, onDeleteForever: (String, Set<String>) -> Unit = { _, _ -> }, onEmptyTrash: () -> Unit = {},
    preferences: HomePreferences = HomePreferences(), onPreferencesChange: (HomePreferences) -> Unit = {},
    now: () -> Long = { 0L }, formatDate: (Long) -> String = { "" }, appVersion: String = "",
) {
    var screen by rememberSaveable { mutableStateOf("home") }
    var viewName by rememberSaveable { mutableStateOf(HomeView.Home.name) }
    val view = HomeView.entries.firstOrNull { it.name == viewName } ?: HomeView.Home
    var segment by rememberSaveable { mutableStateOf("all") }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var searchMode by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var sidebarPinned by rememberSaveable { mutableStateOf(true) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var folds by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var bulkMove by remember { mutableStateOf(false) }
    var bulkDelete by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf(false) }
    var editedFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTitle by rememberSaveable { mutableStateOf("") }
    var dragArmedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var moveId by rememberSaveable { mutableStateOf<String?>(null) }
    var moveBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by rememberSaveable { mutableStateOf("blank") }
    var draftName by rememberSaveable { mutableStateOf("") }
    var infiniteCanvas by rememberSaveable { mutableStateOf(false) }
    var infinitePattern by rememberSaveable { mutableStateOf(InfinitePaper().template) }
    val exitSelection = { selecting = false; selected = emptySet() }
    DisposableEffect(screen, folderId, searchMode, tag, drawerOpen, selecting, view) {
        onBackAction(when {
            drawerOpen -> ({ drawerOpen = false })
            screen == "hub-create" -> ({ screen = "create" })
            screen != "home" -> ({ screen = "home" })
            selecting -> exitSelection
            searchMode.isNotEmpty() || tag != null -> ({ searchMode = ""; search = ""; tag = null })
            folderId != null -> ({ folderId = null; dragArmedId = null })
            view != HomeView.Home -> ({ viewName = HomeView.Home.name })
            else -> null
        })
        onDispose { onBackAction(null) }
    }
    val style = homeStyle(preferences)
    val liveNotes = notebooks.filter { it.deletedAt == 0L }
    val liveBooks = books.filter { it.deletedAt == 0L }
    val folders = settings.activeFolders
    val folderIds = folders.map { it.id }.toSet()
    fun placed(id: String?) = id?.takeIf { it in folderIds }
    val currentFolder = folders.firstOrNull { it.id == folderId }
    LaunchedEffect(folderId, currentFolder) { if (folderId != null && currentFolder == null) folderId = null }
    val newNote = { infinite: Boolean ->
        draftName = "${if (infinite) "Canvas" else "Notebook"}-${liveNotes.size + 1}"; selection = "blank"; infiniteCanvas = infinite; infinitePattern = InfinitePaper().template; screen = "create"
    }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var notice by remember { mutableStateOf<String?>(null) }
    val allTags = liveNotes.flatMap { it.tags }.distinct().sorted()
    val changeScreen: (String) -> Unit = { screen = it; notice = null }
    val query = if (searchMode == "text") search.trim() else ""
    val global = query.isNotEmpty() || tag != null
    val recent = view == HomeView.Recent && !global
    val noteMatches = liveNotes.filter { n ->
        val inView = when {
            global -> true
            view == HomeView.Recent -> n.openedAt > 0
            view == HomeView.Favorites -> n.favorite
            else -> placed(n.folderId) == folderId
        }
        inView && (tag == null || n.tags.any { it.equals(tag, true) }) &&
            (query.isEmpty() || n.title.contains(query, true) || n.tags.any { it.contains(query, true) } || n.searchableText.contains(query, true))
    }.sortedFor(preferences.sort, recent, { it.title }, { it.updatedAt }, { it.createdAt }, { if (it.infinite) "canvas" else "notes" }, { it.openedAt })
    val bookMatches = (if (tag != null) emptyList() else liveBooks.filter { b ->
        val inView = when {
            global -> true
            view == HomeView.Recent -> b.openedAt > 0
            view == HomeView.Favorites -> b.favorite
            else -> placed(b.folderId) == folderId
        }
        inView && (query.isEmpty() || b.title.contains(query, true) || b.author.contains(query, true))
    }).sortedFor(preferences.sort, recent, { it.title }, { maxOf(it.openedAt, it.createdAt) }, { it.createdAt }, { it.format }, { it.openedAt })
    val showNotes = segment == "all" || segment == "notes"
    val showCanvas = segment == "all" || segment == "canvas"
    val showBooks = segment == "all" || segment == "books"
    fun folderCount(id: String) = liveNotes.count { placed(it.folderId) == id && ((it.infinite && showCanvas) || (!it.infinite && showNotes)) } + (if (showBooks) liveBooks.count { placed(it.folderId) == id } else 0)
    fun countLabel(count: Int) = when (segment) { "notes" -> "notebook"; "canvas" -> "canvas"; "books" -> "book"; else -> "item" }
        .let { unit -> "$count ${if (count == 1) unit else if (unit == "canvas") "canvases" else "${unit}s"}" }
    fun latest(id: String) = maxOf(liveNotes.filter { placed(it.folderId) == id }.maxOfOrNull { it.updatedAt } ?: 0L, liveBooks.filter { placed(it.folderId) == id }.maxOfOrNull { it.openedAt } ?: 0L)
    val folderMatches = (if (view != HomeView.Home || folderId != null || tag != null) emptyList() else folders.filter { query.isEmpty() || it.name.contains(query, true) }).let { list ->
        when (preferences.sort) { HomeSort.Name, HomeSort.Type -> list.sortedBy { it.name.lowercase() }; HomeSort.Edited -> list.sortedByDescending { latest(it.id) }; else -> list }
    }
    val sections = buildList {
        if (folderId != null && dragArmedId != null) add(Section("parent", "Move out", listOf(FolderEntry(null, "Drop to move out"))))
        if (folderMatches.isNotEmpty()) add(Section("folders", "Folders", folderMatches.map { FolderEntry(it, countLabel(folderCount(it.id))) }))
        noteMatches.filter { !it.infinite }.takeIf { showNotes && it.isNotEmpty() }?.let { add(Section("notes", "Notes", it.map(::NoteEntry))) }
        noteMatches.filter { it.infinite }.takeIf { showCanvas && it.isNotEmpty() }?.let { add(Section("canvas", "Canvas", it.map(::NoteEntry))) }
        bookMatches.takeIf { showBooks && it.isNotEmpty() }?.let { add(Section("books", "Books", it.map(::BookEntry))) }
    }
    val selectableKeys = sections.filter { it.key != "parent" }.flatMap { section -> section.entries.map { it.key } }
    val trashItems = buildList {
        settings.folders.filter { it.deletedAt > 0 }.forEach { add(TrashItem("folder", it.id, it.name, "Folder", it.deletedAt)) }
        notebooks.filter { it.deletedAt > 0 }.forEach { add(TrashItem("note", it.id, it.title, if (it.infinite) "Canvas" else "Notebook · ${it.pageCount} ${if (it.pageCount == 1) "page" else "pages"}", it.deletedAt)) }
        books.filter { it.deletedAt > 0 }.forEach { add(TrashItem("book", it.id, it.title, "${it.format.uppercase()} book", it.deletedAt)) }
    }.sortedByDescending { it.deletedAt }
    fun ids(prefix: String) = selected.filter { it.startsWith("$prefix:") }.map { it.removePrefix("$prefix:") }.toSet()
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key }

    CompositionLocalProvider(LocalHomeStyle provides style) {
    BoxWithConstraints(Modifier.fillMaxSize().background(HomeBackground).onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
        val wide = maxWidth >= 720.dp
        val sidebarShown = wide && sidebarPinned && screen in setOf("home", "trash", "preferences", "about")
        val compactBar = (if (sidebarShown) maxWidth - 265.dp else maxWidth) < 560.dp
        LaunchedEffect(wide) { if (wide) drawerOpen = false }
        val sidebarKey = when {
            screen == "trash" || screen == "preferences" || screen == "about" -> screen
            currentFolder != null -> "folder:${currentFolder.id}"
            view == HomeView.Recent -> "recent"; view == HomeView.Favorites -> "favorites"; else -> "home"
        }
        val sidebar: @Composable (Modifier) -> Unit = { modifier ->
            HomeSidebar(folders, sidebarKey, trashItems.size, modifier, onCreateFolder = { drawerOpen = false; editedFolder = null; folderDialog = true }, onSelect = { key ->
                drawerOpen = false; exitSelection(); searchMode = ""; search = ""; tag = null; dragArmedId = null; notice = null
                when {
                    key == "trash" || key == "preferences" || key == "about" -> changeScreen(key)
                    key.startsWith("folder:") -> { screen = "home"; viewName = HomeView.Home.name; folderId = key.removePrefix("folder:") }
                    else -> { screen = "home"; folderId = null; viewName = when (key) { "recent" -> HomeView.Recent; "favorites" -> HomeView.Favorites; else -> HomeView.Home }.name }
                }
            })
        }
        Row(Modifier.fillMaxSize()) {
            if (sidebarShown) {
                sidebar(Modifier.width(264.dp).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(HomeHairline))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (screen) {
                    "hub", "hub-create" -> TemplateHub(settings, onSettingsChange, templatePreview, onBack = { changeScreen(if (screen == "hub-create") "create" else "home") }, onUse = { id ->
                        selection = id
                        if (draftName.isBlank()) draftName = "Notebook-${liveNotes.size + 1}"
                        changeScreen("create")
                    })
                    "create" -> CreateNotebookScreen(draftName, { draftName = it }, selection, { selection = it }, settings, infiniteCanvas, { infiniteCanvas = it }, infinitePattern, { infinitePattern = it }, templatePreview,
                        onBack = { changeScreen("home") }, onHub = { changeScreen("hub-create") },
                        onCreate = { if (draftName.isNotBlank()) { screen = "home"; onCreate(draftName.trim(), if (infiniteCanvas) infinitePattern else selection, currentFolder?.id, infiniteCanvas) } })
                    "trash" -> TrashScreen(trashItems, preferences.trashRetentionDays, now(), formatDate, preview = { item, modifier ->
                        when (item.kind) {
                            "note" -> thumbnail(item.id, modifier.padding(3.dp))
                            "book" -> bookCover(item.id, modifier)
                            else -> settings.folders.firstOrNull { it.id == item.id }?.let { FolderArtwork(it.color, it.style, Modifier.fillMaxWidth(.85f).aspectRatio(1.35f)) }
                        }
                    }, onBack = { changeScreen("home") }, onRestore = { onRestore(it.kind, it.id) }, onDelete = { onDeleteForever(it.kind, setOf(it.id)) }, onEmpty = onEmptyTrash)
                    "preferences" -> PreferencesScreen(preferences, onPreferencesChange, onBack = { changeScreen("home") }, onOpenBackup = onOpenSettings, onOpenPen = onOpenPenSettings)
                    "about" -> AboutScreen(appVersion, onBack = { changeScreen("home") })
                    else -> Column(Modifier.fillMaxSize()) {
                        if (selecting) Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp).testTag("selection-bar"), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = exitSelection, modifier = Modifier.testTag("selection-cancel")) { Text("Cancel", color = Ink) }
                            Text("${selected.size} selected", Modifier.weight(1f).padding(start = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            val all = selectableKeys.isNotEmpty() && selected.containsAll(selectableKeys)
                            TextButton(onClick = { selected = if (all) emptySet() else selectableKeys.toSet() }) { Text(if (all) "Clear" else "Select all", color = style.accent) }
                            HomeIconButton(Symbol.Folder, "Move selected", false, { if (ids("note").isNotEmpty() || ids("book").isNotEmpty()) bulkMove = true else notice = "Select notebooks or books to move" })
                            HomeIconButton(Symbol.Star, "Favorite selected", false, {
                                val notes = ids("note"); val picked = ids("book")
                                if (notes.isNotEmpty() || picked.isNotEmpty()) {
                                    val target = !(liveNotes.filter { it.id in notes }.all { it.favorite } && liveBooks.filter { it.id in picked }.all { it.favorite })
                                    liveNotes.filter { it.id in notes && it.favorite != target }.forEach { onFavorite(it.id) }
                                    picked.forEach { onFavoriteBook(it, target) }
                                    notice = if (target) "Added to Favorites" else "Removed from Favorites"; exitSelection()
                                } else notice = "Folders can't be added to Favorites"
                            })
                            HomeIconButton(Symbol.Trash, "Delete selected", false, { if (selected.isNotEmpty()) bulkDelete = true })
                        } else {
                            val pill: @Composable (Modifier) -> Unit = { modifier -> SegmentPill(segments, segment, compactBar, { segment = it; notice = null }, modifier) }
                            Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp)) {
                                Box(Modifier.align(Alignment.CenterStart)) { HomeIconButton(Symbol.Sidebar, "Toggle sidebar", wide && sidebarPinned, { if (wide) sidebarPinned = !sidebarPinned else drawerOpen = true }) }
                                if (!compactBar) pill(Modifier.align(Alignment.Center))
                                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                                    HomeIconButton(Symbol.Search, "Search notebooks", searchMode == "text", { viewName = HomeView.Home.name; folderId = null; searchMode = if (searchMode == "text") "" else "text"; search = "" })
                                    Box {
                                        HomeIconButton(Symbol.Dots, "Library menu", menu || sortMenu, { menu = !menu })
                                        val check: @Composable (Boolean) -> Unit = { on -> if (on) HomeSymbol(Symbol.Check, Modifier.size(18.dp), tint = style.accent) }
                                        DropdownMenu(menu, { menu = false }, modifier = Modifier.penPressureFilter()) {
                                            DropdownMenuItem(text = { Text("Select") }, modifier = Modifier.testTag("home-select"), onClick = { menu = false; selecting = true; selected = emptySet() })
                                            DropdownMenuItem(text = { Text("Sort by (${HomeSort.all.first { it.first == preferences.sort }.second})") }, modifier = Modifier.testTag("home-sort"),
                                                trailingIcon = { HomeSymbol(Symbol.ChevronRight, Modifier.size(14.dp)) }, onClick = { menu = false; sortMenu = true })
                                            HorizontalDivider(color = HomeHairline)
                                            DropdownMenuItem(text = { Text("Grid") }, modifier = Modifier.testTag("home-view-grid"), trailingIcon = { check(preferences.viewMode != "list") }, onClick = { menu = false; onPreferencesChange(preferences.copy(viewMode = "grid")) })
                                            DropdownMenuItem(text = { Text("List") }, modifier = Modifier.testTag("home-view-list"), trailingIcon = { check(preferences.viewMode == "list") }, onClick = { menu = false; onPreferencesChange(preferences.copy(viewMode = "list")) })
                                            HorizontalDivider(color = HomeHairline)
                                            DropdownMenuItem(text = { Text("Search tags") }, modifier = Modifier.testTag("home-search-tags"), onClick = { menu = false; viewName = HomeView.Home.name; folderId = null; searchMode = if (searchMode == "tags") "" else "tags" })
                                            DropdownMenuItem(text = { Text("Template Hub") }, modifier = Modifier.testTag("home-template-hub"), onClick = { menu = false; changeScreen("hub") })
                                            DropdownMenuItem(text = { Text("Import notebook or document") }, onClick = { menu = false; onImport() })
                                            DropdownMenuItem(text = { Text("Export full library backup") }, modifier = Modifier.testTag("home-export-library-backup"), onClick = { menu = false; onExportLibraryBackup() })
                                            DropdownMenuItem(text = { Text("Backup settings") }, modifier = Modifier.testTag("home-settings"), onClick = { menu = false; onOpenSettings() })
                                        }
                                        DropdownMenu(sortMenu, { sortMenu = false }, modifier = Modifier.penPressureFilter()) {
                                            Text("Sort by", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = Muted)
                                            HomeSort.all.forEach { (id, label) ->
                                                DropdownMenuItem(text = { Text(label) }, modifier = Modifier.testTag("home-sort-$id"), trailingIcon = { check(preferences.sort == id) },
                                                    onClick = { sortMenu = false; onPreferencesChange(preferences.copy(sort = id)) })
                                            }
                                        }
                                    }
                                }
                            }
                            if (compactBar) Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) { pill(Modifier) }
                        }
                        if (searchMode == "text") OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag("library-search"), singleLine = true,
                            label = { Text("Search notebooks, books, tags and text") }, shape = RoundedCornerShape(style.item),
                            trailingIcon = { HomeIconButton(Symbol.Close, "Close search", false, { searchMode = ""; search = "" }) })
                        if (searchMode == "tags") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = style.accent, selectedLabelColor = Color.White, containerColor = Color.White, labelColor = Ink)
                            FilterChip(colors = chipColors, selected = tag == null, onClick = { tag = null }, label = { Text("All tags") })
                            allTags.forEach { value -> Spacer(Modifier.width(8.dp)); FilterChip(colors = chipColors, selected = tag == value, onClick = { tag = if (tag == value) null else value }, label = { Text(value) }) }
                            if (allTags.isEmpty()) Text("Add tags from a notebook's editor", Modifier.padding(12.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
                            Spacer(Modifier.width(8.dp)); HomeIconButton(Symbol.Close, "Close tag search", false, { searchMode = ""; tag = null })
                        }
                        val heading = when { currentFolder != null -> currentFolder.name; tag != null -> "#$tag"; query.isNotEmpty() -> null; view == HomeView.Recent -> "Recent"; view == HomeView.Favorites -> "Favorites"; else -> null }
                        if (heading != null || notice != null) Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (currentFolder != null || (view != HomeView.Home && !global)) HomeIconButton(Symbol.Back, "Back to library", false, { folderId = null; viewName = HomeView.Home.name; dragArmedId = null; notice = null })
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                if (heading != null) Text(heading, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (notice != null) Text(notice.orEmpty(), fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
                            }
                            if (currentFolder != null && !selecting) MenuCaret("Folder options", { detailsFolderId = currentFolder.id })
                            if (dragArmedId != null) HomeIconButton(Symbol.Close, "Cancel move", false, { dragArmedId = null; notice = null })
                            if (tag != null) HomeIconButton(Symbol.Close, "Clear tag filter", false, { tag = null })
                            if (notice != null && dragArmedId == null) HomeIconButton(Symbol.Close, "Dismiss message", false, { notice = null })
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val grid = preferences.viewMode != "list"
                            val compact = maxWidth < 480.dp
                            val pad = if (compact) 16.dp else 28.dp
                            val gap = if (!grid) 12.dp else if (compact) 14.dp else 22.dp
                            val cell = if (compact) 100.dp else 112.dp
                            val columns = if (grid) ((maxWidth - pad * 2 + gap) / (cell + gap)).toInt().coerceAtLeast(2) else if (maxWidth >= 720.dp) 2 else 1
                            val itemSections = sections.count { it.key != "folders" && it.key != "parent" }
                            LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize().testTag("notebook-grid"), contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 112.dp),
                                horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(if (grid) 16.dp else 8.dp)) {
                                sections.forEach { section ->
                                    val limit = if (grid) columns else columns * 4
                                    val foldKey = "$segment:${section.key}"
                                    val defaultOpen = when (section.key) { "parent" -> true; "folders" -> itemSections == 0; else -> segment != "all" || itemSections <= 1 || global }
                                    val foldable = section.key != "parent" && section.entries.size > limit
                                    val open = !foldable || (section.key == "folders" && dragArmedId != null) || (folds[foldKey] ?: defaultOpen)
                                    item(key = "header-${section.key}", span = { GridItemSpan(maxLineSpan) }) {
                                        SectionHeader(section.key, section.title, section.entries.size, foldable, open) { folds = folds + (foldKey to !open) }
                                    }
                                    items(if (open) section.entries else section.entries.take(limit), key = { it.key }) { entry ->
                                        val mark = if (selecting && entry.key != "folder:root") entry.key in selected else null
                                        when (entry) {
                                            is FolderEntry -> {
                                                val folder = entry.folder
                                                val target = folder?.id ?: "root"
                                                DisposableEffect(target) { onDispose { bounds.remove(target) } }
                                                LibraryTile(grid, folder?.name ?: "All folders", entry.detail, "Options for ${folder?.name ?: "All folders"}", { if (folder != null) detailsFolderId = folder.id },
                                                    Modifier.onGloballyPositioned { bounds[target] = it.boundsInRoot() }
                                                        .combinedClickable(onClick = { if (selecting && folder != null) toggle(entry.key) else folderId = folder?.id }, onLongClickLabel = "Folder options",
                                                            onLongClick = { if (folder != null) detailsFolderId = folder.id })
                                                        .testTag("folder-$target").semantics { contentDescription = "Open folder ${folder?.name ?: "All folders"}" },
                                                    mark, highlighted = dragId != null && bounds[target]?.contains(dragPoint) == true, previewBackground = Color(0xFFFAFAFA)) {
                                                    FolderArtwork(folder?.color ?: "sage", folder?.style ?: "classic", if (grid) Modifier.fillMaxWidth(.85f).aspectRatio(1.35f) else Modifier.fillMaxWidth(.9f).aspectRatio(1.35f))
                                                }
                                            }
                                            is NoteEntry -> {
                                                val note = entry.note
                                                var cardBounds by remember { mutableStateOf(Rect.Zero) }
                                                val pages = if (note.infinite) "∞ Infinite" else "${note.currentPage.coerceIn(1, note.pageCount.coerceAtLeast(1))}/${note.pageCount}"
                                                LibraryTile(grid, note.title, if (grid) note.dateLabel else "${if (note.favorite) "★ " else ""}${note.dateLabel} · $pages", "Options for ${note.title}", { detailsNoteId = note.id },
                                                    Modifier.onGloballyPositioned { cardBounds = it.boundsInRoot() }
                                                        .then(if (dragArmedId == note.id) Modifier.pointerInput(note.id, folderId, folders) {
                                                            detectDragGestures(
                                                                onDragStart = { local -> dragId = note.id; dragPoint = cardBounds.topLeft + local },
                                                                onDragEnd = {
                                                                    val targets = if (folderId == null) folders.map { it.id } else listOf("root")
                                                                    val target = targets.firstOrNull { bounds[it]?.contains(dragPoint) == true }
                                                                    if (target != null) { onMove(note.id, target.takeUnless { it == "root" }); notice = "Notebook moved" } else notice = "No folder selected"
                                                                    dragId = null; dragArmedId = null
                                                                }, onDragCancel = { dragId = null; dragArmedId = null; notice = null },
                                                                onDrag = { change, amount -> change.consume(); dragPoint += amount },
                                                            )
                                                        } else Modifier.combinedClickable(onClick = { if (selecting) toggle(entry.key) else onOpen(note.id) }, onLongClickLabel = "Notebook options",
                                                            onLongClick = { if (selecting) toggle(entry.key) else detailsNoteId = note.id }))
                                                        .testTag("notebook-${note.id}").semantics { contentDescription = "Open ${note.title}" },
                                                    mark, highlighted = dragArmedId == note.id) {
                                                    thumbnail(note.id, Modifier.fillMaxSize().padding(if (grid) 8.dp else 3.dp))
                                                    if (grid) {
                                                        TileBadge(pages, Modifier.align(Alignment.BottomStart))
                                                        TileBadge(if (note.infinite) "CANVAS" else "NOTE", Modifier.align(Alignment.TopStart), filled = note.infinite, tag = "notebook-kind-${note.id}")
                                                        if (note.favorite && mark == null) HomeSymbol(Symbol.Star, Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp), folderFill = true)
                                                    }
                                                }
                                            }
                                            is BookEntry -> {
                                                val book = entry.book
                                                val progress = "${(book.progress * 100).roundToInt()}%"
                                                LibraryTile(grid, book.title, if (book.missing) "Original missing · re-import to read" else if (grid) book.dateLabel else "${if (book.favorite) "★ " else ""}${book.format.uppercase()} · $progress read · ${book.dateLabel}",
                                                    "Options for ${book.title}", { detailsBookId = book.id },
                                                    Modifier.combinedClickable(onClick = { if (selecting) toggle(entry.key) else onOpenBook(book.id) }, onLongClickLabel = "Book options",
                                                        onLongClick = { if (selecting) toggle(entry.key) else detailsBookId = book.id })
                                                        .testTag("book-${book.id}").semantics { contentDescription = "Open ${book.title}" },
                                                    mark, subtitleColor = if (book.missing) HomeDanger else Muted, previewBackground = Color(0xFFF4F4F2)) {
                                                    bookCover(book.id, Modifier.fillMaxSize())
                                                    if (grid) {
                                                        TileBadge(book.format.uppercase(), Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp))
                                                        if (mark == null) TileBadge(progress, Modifier.align(Alignment.TopEnd))
                                                        if (book.favorite && mark == null) HomeSymbol(Symbol.Star, Modifier.align(Alignment.TopStart).padding(8.dp).size(18.dp), folderFill = true)
                                                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(book.progress.coerceIn(0f, 1f)).height(4.dp).background(style.accent))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item(key = "gap-${section.key}", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(4.dp)) }
                                }
                                if (sections.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(when {
                                            global -> "No matching items"
                                            view == HomeView.Favorites -> "No favorites yet"
                                            view == HomeView.Recent -> "Open a notebook or book to find it here"
                                            currentFolder != null -> "This folder is empty"
                                            segment == "books" -> "No books yet"
                                            else -> "Nothing here yet"
                                        }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
                                        Text(if (segment == "books") "Tap + to import a PDF or DRM-free EPUB." else "Tap + to create a notebook or import a book.", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = Muted)
                                    }
                                }
                            }
                            if (!selecting) Box(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp)) {
                                Surface(onClick = { fabMenu = true }, shape = CircleShape, color = style.accent, shadowElevation = 6.dp,
                                    modifier = Modifier.size(60.dp).testTag("create-notebook").semantics { contentDescription = "Create" }) {
                                    Box(contentAlignment = Alignment.Center) { HomeSymbol(Symbol.Plus, Modifier.size(24.dp), tint = Color.White) }
                                }
                                DropdownMenu(fabMenu, { fabMenu = false }, modifier = Modifier.penPressureFilter()) {
                                    DropdownMenuItem(text = { Text("New notebook") }, modifier = Modifier.testTag("home-new-notebook"), leadingIcon = { HomeSymbol(Symbol.Notes, Modifier.size(20.dp)) }, onClick = { fabMenu = false; newNote(false) })
                                    DropdownMenuItem(text = { Text("New infinite canvas") }, modifier = Modifier.testTag("home-new-canvas"), leadingIcon = { HomeSymbol(Symbol.All, Modifier.size(20.dp)) }, onClick = { fabMenu = false; newNote(true) })
                                    DropdownMenuItem(text = { Text("New folder") }, modifier = Modifier.testTag("home-new-folder"), leadingIcon = { HomeSymbol(Symbol.Folder, Modifier.size(20.dp)) }, onClick = { fabMenu = false; editedFolder = null; folderDialog = true })
                                    HorizontalDivider(color = HomeHairline)
                                    DropdownMenuItem(text = { Text("Import PDF or EPUB book") }, modifier = Modifier.testTag("home-import-book"), leadingIcon = { HomeSymbol(Symbol.Book, Modifier.size(20.dp)) }, onClick = { fabMenu = false; onImportBook(currentFolder?.id) })
                                    DropdownMenuItem(text = { Text("Import notebook or backup") }, modifier = Modifier.testTag("home-import"), leadingIcon = { HomeSymbol(Symbol.Download, Modifier.size(20.dp)) }, onClick = { fabMenu = false; onImport() })
                                }
                            }
                        }
                        if (isSaving) Text("Saving to this device…", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
                    }
                }
            }
        }
        if (!wide && drawerOpen && screen in setOf("home", "trash", "preferences", "about")) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).clickable(onClickLabel = "Close sidebar") { drawerOpen = false })
            Surface(Modifier.fillMaxHeight().widthIn(max = 300.dp).fillMaxWidth(.84f), shadowElevation = 8.dp, color = HomeSidebarBackground) { sidebar(Modifier.fillMaxSize()) }
        }
        if (dragId != null) Surface(Modifier.offset { IntOffset((dragPoint.x - rootOrigin.x).roundToInt(), (dragPoint.y - rootOrigin.y).roundToInt()) }.widthIn(max = 180.dp), shadowElevation = 6.dp, color = style.soft, shape = RoundedCornerShape(style.card)) {
            Text(notebooks.firstOrNull { it.id == dragId }?.title.orEmpty(), Modifier.padding(12.dp), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
    val trashNote = if (preferences.trashRetentionDays > 0) "You can restore it from Trash for ${preferences.trashRetentionDays} days." else "You can restore it from Trash until you empty it."
    detailsNoteId?.let { id -> liveNotes.firstOrNull { it.id == id }?.let { note ->
        LibraryDetailsDialog(note.title, if (note.infinite) "Infinite canvas" else "${note.pageCount} pages · Page ${note.currentPage}", if (note.infinite) "Infinite notebook" else "Notebook", note.dateLabel,
            preview = { modifier -> thumbnail(note.id, modifier) }, onDismiss = { detailsNoteId = null },
            actions = listOf(
                DetailAction(Symbol.Open, "Open") { detailsNoteId = null; onOpen(id) },
                DetailAction(Symbol.Trash, "Delete") { detailsNoteId = null; deleteId = id },
                DetailAction(Symbol.Folder, "Move") { detailsNoteId = null; moveId = id },
                DetailAction(Symbol.Copy, "Copy") { detailsNoteId = null; onCopy(id) },
                DetailAction(Symbol.Rename, "Rename") { detailsNoteId = null; renameTitle = note.title; renameId = id },
                DetailAction(Symbol.Share, "Share and export") { detailsNoteId = null; exportId = id },
                DetailAction(Symbol.Star, if (note.favorite) "Unfavorite" else "Favorites") { onFavorite(id) },
            ))
    } }
    detailsBookId?.let { id -> liveBooks.firstOrNull { it.id == id }?.let { book ->
        LibraryDetailsDialog(book.title, listOfNotNull(book.author.takeIf { it.isNotBlank() }, if (book.format == "pdf") "${book.pageCount} pages" else null, "${(book.progress * 100).roundToInt()}% read").joinToString(" · "),
            "${book.format.uppercase()} book", if (book.missing) "Original missing · re-import to read" else book.dateLabel,
            preview = { modifier -> bookCover(book.id, modifier) }, onDismiss = { detailsBookId = null },
            actions = listOf(
                DetailAction(Symbol.Open, "Open") { detailsBookId = null; onOpenBook(id) },
                DetailAction(Symbol.Trash, "Delete") { detailsBookId = null; deleteBookId = id },
                DetailAction(Symbol.Folder, "Move") { detailsBookId = null; moveBookId = id },
                DetailAction(Symbol.Star, if (book.favorite) "Unfavorite" else "Favorites") { onFavoriteBook(id, !book.favorite) },
            ))
    } }
    detailsFolderId?.let { id -> folders.firstOrNull { it.id == id }?.let { folder ->
        val contents = liveNotes.filter { placed(it.folderId) == id }
        val bookCount = liveBooks.count { placed(it.folderId) == id }
        LibraryDetailsDialog(folder.name, "${contents.size} notebooks · $bookCount books", "Folder", contents.maxByOrNull { it.updatedAt }?.dateLabel ?: "No notebooks yet",
            preview = { modifier -> Box(modifier, contentAlignment = Alignment.Center) { FolderArtwork(folder.color, folder.style, Modifier.size(100.dp, 76.dp)) } },
            onDismiss = { detailsFolderId = null }, actions = listOf(
                DetailAction(Symbol.Open, "Open") { detailsFolderId = null; screen = "home"; viewName = HomeView.Home.name; folderId = id },
                DetailAction(Symbol.Rename, "Edit folder") { detailsFolderId = null; editedFolder = folder; folderDialog = true },
                DetailAction(Symbol.Trash, "Delete") { detailsFolderId = null; deleteFolderId = id },
            ))
    } }
    exportId?.let { id -> AlertDialog(onDismissRequest = { exportId = null }, title = { Text("Share and export") }, text = {
        Column { listOf("pdf" to "PDF · all pages", "png" to "PNG · current page", "knotes" to "Folio backup (.knotes) · editable").forEach { (format, label) ->
            TextButton(onClick = { exportId = null; onExport(id, format) }) { Text(label) }
        } }
    }, confirmButton = { TextButton(onClick = { exportId = null }) { Text("Cancel") } }) }
    renameId?.let { id -> AlertDialog(onDismissRequest = { renameId = null }, title = { Text("Rename notebook") }, text = {
        OutlinedTextField(renameTitle, { renameTitle = it }, label = { Text("Notebook name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
    }, confirmButton = { TextButton(enabled = renameTitle.isNotBlank(), onClick = { onRename(id, renameTitle.trim()); renameId = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { renameId = null }) { Text("Cancel") } }) }
    deleteFolderId?.let { id -> AlertDialog(onDismissRequest = { deleteFolderId = null }, title = { Text("Move folder to Trash?") },
        text = { Text("Its notebooks and books stay in your library without a folder. Restoring the folder from Trash puts them back inside.") },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm-delete-folder"), onClick = {
            onTrash("folder", setOf(id)); if (folderId == id) folderId = null; deleteFolderId = null
        }) { Text("Move to Trash") } }, dismissButton = { TextButton(onClick = { deleteFolderId = null }) { Text("Cancel") } }) }
    if (folderDialog) FolderEditorDialog(existing = editedFolder, folders = folders, onDismiss = { folderDialog = false }, onSave = { name, color, style ->
            val edit = editedFolder
            val updated = if (edit != null) settings.folders.map { if (it.id == edit.id) it.copy(name = name, color = color, style = style) else it } else {
                val id = generateSequence(1) { it + 1 }.map { "folder-$it" }.first { id -> settings.folders.none { it.id == id } }
                settings.folders + NoteFolder(id, name, color, style)
            }
            onSettingsChange(settings.copy(folders = updated)); folderDialog = false
    })
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Move notebook to Trash?") }, text = { Text(trashNote) },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm-delete-notebook"), onClick = { onTrash("note", setOf(id)); deleteId = null }) { Text("Move to Trash") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }) }
    deleteBookId?.let { id -> AlertDialog(onDismissRequest = { deleteBookId = null }, title = { Text("Move book to Trash?") }, text = { Text("Highlights, notes and reading position are kept. $trashNote") },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm-delete-book"), onClick = { onTrash("book", setOf(id)); deleteBookId = null }) { Text("Move to Trash") } },
        dismissButton = { TextButton(onClick = { deleteBookId = null }) { Text("Cancel") } }) }
    if (bulkDelete) AlertDialog(onDismissRequest = { bulkDelete = false }, title = { Text("Move ${selected.size} ${if (selected.size == 1) "item" else "items"} to Trash?") },
        text = { Text("${if (ids("folder").isNotEmpty()) "Notebooks and books inside deleted folders stay in your library. " else ""}${if (preferences.trashRetentionDays > 0) "You can restore them from Trash for ${preferences.trashRetentionDays} days." else "You can restore them from Trash until you empty it."}") },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm-bulk-delete"), onClick = {
            listOf("note", "book", "folder").forEach { kind -> ids(kind).takeIf { it.isNotEmpty() }?.let { onTrash(kind, it) } }
            notice = "Moved ${selected.size} ${if (selected.size == 1) "item" else "items"} to Trash"; bulkDelete = false; exitSelection()
        }) { Text("Move to Trash") } }, dismissButton = { TextButton(onClick = { bulkDelete = false }) { Text("Cancel") } })
    val moveTargets: @Composable ((String?) -> Unit) -> Unit = { pick ->
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { pick(null) }) { Text("Main library (no folder)") }
            folders.forEach { folder -> TextButton(onClick = { pick(folder.id) }) { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
    }
    if (bulkMove) AlertDialog(onDismissRequest = { bulkMove = false }, title = { Text("Move to folder") }, text = {
        moveTargets { target ->
            ids("note").forEach { onMove(it, target) }; ids("book").forEach { onMoveBook(it, target) }
            notice = if (ids("folder").isNotEmpty()) "Moved. Folders stay where they are." else "Moved to ${folders.firstOrNull { it.id == target }?.name ?: "the main library"}"
            bulkMove = false; exitSelection()
        }
    }, confirmButton = { TextButton(onClick = { bulkMove = false }) { Text("Cancel") } })
    moveId?.let { id -> AlertDialog(onDismissRequest = { moveId = null }, title = { Text("Move to folder") }, text = {
        Column {
            TextButton(onClick = {
                screen = "home"; viewName = HomeView.Home.name; folderId = liveNotes.firstOrNull { it.id == id }?.folderId?.let(::placed)
                dragArmedId = id; moveId = null; notice = "Drag the selected notebook onto a folder, or tap × to cancel"
            }) { Text("Drag on home screen") }
            moveTargets { target -> onMove(id, target); moveId = null }
        }
    }, confirmButton = { TextButton(onClick = { moveId = null }) { Text("Cancel") } }) }
    moveBookId?.let { id -> AlertDialog(onDismissRequest = { moveBookId = null }, title = { Text("Move book to folder") }, text = {
        moveTargets { target -> onMoveBook(id, target); moveBookId = null }
    }, confirmButton = { TextButton(onClick = { moveBookId = null }) { Text("Cancel") } }) }
    }
}

@Composable
private fun NoteTypeCard(title: String, detail: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 68.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) LocalHomeStyle.current.soft else Color.White)
            .border(1.dp, if (selected) Ink else Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
            Text(detail, Modifier.padding(top = 2.dp), fontSize = 10.sp, lineHeight = 14.sp, color = Muted, maxLines = 2)
        }
    }
}

private fun DrawScope.drawInfinitePaper(paper: InfinitePaper, gapScale: Float = 1f) {
    drawRect(Color(paper.background))
    if (paper.pattern == "blank") return
    val gap = size.width * paper.gapFraction * gapScale
    val dot = if (paper.dark) Color(0xFF808080) else Color(0xFFBEC3C7)
    val rule = if (paper.dark) Color(0xFF626262) else Color(0xFFD6DADE)
    val dpx = 1f
    when (paper.pattern) {
        "dots" -> { var y = gap / 2; while (y < size.height) { var x = gap / 2; while (x < size.width) { drawCircle(dot, 1.4f, Offset(x, y)); x += gap }; y += gap } }
        "lines" -> { var y = gap; while (y < size.height) { drawLine(rule, Offset(0f, y), Offset(size.width, y), dpx); y += gap } }
        else -> {
            var i = 0; var y = 0f
            while (y < size.height) { drawLine(if (paper.pattern == "sheet" && i % 5 == 0) dot else rule, Offset(0f, y), Offset(size.width, y), dpx); y += gap; i++ }
            i = 0; var x = 0f
            while (x < size.width) { drawLine(if (paper.pattern == "sheet" && i % 5 == 0) dot else rule, Offset(x, 0f), Offset(x, size.height), dpx); x += gap; i++ }
        }
    }
}

@Composable
private fun InfinitePaperSection(template: String, onTemplate: (String) -> Unit) {
    val paper = remember(template) { InfinitePaper.parse(template) ?: InfinitePaper() }
    var colorMenu by remember { mutableStateOf(false) }
    var spacingMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(96.dp).aspectRatio(.75f).clip(RoundedCornerShape(10.dp)).border(2.dp, Ink, RoundedCornerShape(10.dp)).testTag("infinite-paper-preview")) { drawInfinitePaper(paper, 1.6f) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(InfinitePaper.patterns.first { it.first == paper.pattern }.second, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                Text("Pan in any direction and the paper follows. Long-press blank space to add a pin and jump back later.", Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
            }
        }
        Text("Pattern", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Muted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfinitePaper.patterns.forEach { (id, label) ->
                val on = id == paper.pattern
                Column(Modifier.width(96.dp).clickable { onTemplate(paper.copy(pattern = id).template) }.testTag("infinite-paper-$id").semantics { contentDescription = "$label${if (on) ", selected" else ""}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(.75f).clip(RoundedCornerShape(8.dp)).border(if (on) 2.dp else 1.dp, if (on) Ink else Line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) { drawInfinitePaper(paper.copy(pattern = id), 1.6f) }
                        if (on) Box(Modifier.size(24.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 13.sp) }
                    }
                    Text(label, Modifier.padding(top = 6.dp), fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFFF4F4F4), RoundedCornerShape(12.dp)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { colorMenu = true }, modifier = Modifier.testTag("infinite-paper-color")) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(paper.background)).border(1.5.dp, Line, CircleShape))
                    Text(InfinitePaper.colors.first { it.first == paper.color }.second + " ▾", Modifier.padding(start = 8.dp), fontSize = 13.sp, color = Ink)
                }
                DropdownMenu(colorMenu, { colorMenu = false }, modifier = Modifier.penPressureFilter()) {
                    InfinitePaper.colors.forEach { (id, label) ->
                        DropdownMenuItem(text = { Text(label + if (id == paper.color) "  ✓" else "") }, onClick = { onTemplate(paper.copy(color = id).template); colorMenu = false },
                            leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(InfinitePaper.colorArgb(id))).border(1.5.dp, Line, CircleShape)) })
                    }
                }
            }
            if (paper.pattern != "blank") Box {
                TextButton(onClick = { spacingMenu = true }, modifier = Modifier.testTag("infinite-paper-spacing")) { Text("Spacing ${paper.spacing} ▾", fontSize = 13.sp, color = Ink) }
                DropdownMenu(spacingMenu, { spacingMenu = false }, modifier = Modifier.penPressureFilter()) {
                    Row(Modifier.width(240.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Slider(paper.spacing.toFloat(), { onTemplate(paper.copy(spacing = it.roundToInt().coerceIn(1, 8)).template) }, Modifier.weight(1f), valueRange = 1f..8f, steps = 6)
                        Text("${paper.spacing}", Modifier.padding(start = 12.dp), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateNotebookScreen(name: String, onName: (String) -> Unit, selected: String, onSelected: (String) -> Unit, settings: LibrarySettings, infiniteCanvas: Boolean, onInfiniteCanvas: (Boolean) -> Unit, infinitePattern: String, onInfinitePattern: (String) -> Unit, templatePreview: (@Composable (String, Modifier) -> Unit)?, onBack: () -> Unit, onHub: () -> Unit, onCreate: () -> Unit) {
    var category by rememberSaveable { mutableStateOf(if (selected in settings.downloaded) "Recent" else papers.firstOrNull { it.id == selected }?.category ?: "General") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeIconButton(Symbol.Back, "Back", false, onBack)
            OutlinedTextField(name, onName, modifier = Modifier.weight(1f).padding(start = 8.dp, end = 12.dp).testTag("new-notebook-name"), singleLine = true, placeholder = { Text("Notebook name") }, trailingIcon = { if (name.isNotEmpty()) HomeIconButton(Symbol.Close, "Clear name", false, { onName("") }) }, shape = RoundedCornerShape(12.dp))
            Button(onClick = onCreate, enabled = name.isNotBlank(), modifier = Modifier.height(52.dp).testTag("confirm-create-notebook"), shape = RoundedCornerShape(12.dp)) { Text("Create", fontSize = 13.sp) }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Text("Canvas", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Muted)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteTypeCard(
                    title = "Pages", detail = "Individual pages", selected = !infiniteCanvas,
                    modifier = Modifier.weight(1f), onClick = { onInfiniteCanvas(false) },
                )
                NoteTypeCard(
                    title = "Infinite", detail = "One canvas · all directions", selected = infiniteCanvas,
                    modifier = Modifier.weight(1f), onClick = { onInfiniteCanvas(true) },
                )
            }
        }
        Text("Canvas type is fixed after creation.", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
        if (infiniteCanvas) {
            InfinitePaperSection(infinitePattern, onInfinitePattern)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Page template", fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                Text(papers.firstOrNull { it.id == selected }?.name ?: "Blank", fontSize = 11.sp, lineHeight = 15.sp, color = Muted)
            }
            TextButton(onClick = onHub) { Text("Browse templates", fontSize = 12.sp, lineHeight = 16.sp) }
        }
        Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NotebookTemplateCatalog.categories.forEach { tab -> FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Color.White, containerColor = Color.White, labelColor = Ink), selected = tab == category, onClick = { category = tab }, label = { Text(tab, fontSize = 12.sp, lineHeight = 16.sp) }) }
            }
            val shown = if (category == "Recent") NotebookTemplateCatalog.inCategory("Recent", (listOf(selected) + settings.downloaded).distinct()) else NotebookTemplateCatalog.inCategory(category)
            HorizontalDivider(color = Color(0xFFE8E8E8))
            LazyVerticalGrid(GridCells.Adaptive(124.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(shown, key = { it.id }) { paper -> Column(Modifier.clickable { onSelected(paper.id) }.testTag("select-template-${paper.id}").semantics { contentDescription = "${paper.name}${if (selected == paper.id) ", selected" else ""}" }) {
                    TemplatePreview(paper.id, templatePreview, Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(8.dp)).border(if (selected == paper.id) 2.dp else 1.dp, if (selected == paper.id) Ink else Line, RoundedCornerShape(8.dp)))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(paper.name, Modifier.weight(1f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (selected == paper.id) Text("✓", fontSize = 13.sp, color = Ink)
                    }
                } }
                if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Column { Text("No recent templates yet", fontSize = 13.sp, lineHeight = 17.sp, color = Muted); TextButton(onClick = { category = "All" }) { Text("Browse all templates") } } }
            }
        }
    }
}

@Composable
private fun TemplateHub(settings: LibrarySettings, onSettingsChange: (LibrarySettings) -> Unit, templatePreview: (@Composable (String, Modifier) -> Unit)?, onBack: () -> Unit, onUse: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf("General") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeIconButton(Symbol.Back, "Back from Template Hub", false, onBack)
            Text("Template Hub", Modifier.weight(1f), fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            HomeIconButton(Symbol.Download, "Recent templates", category == "Recent", { category = "Recent" })
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NotebookTemplateCatalog.categories.forEach { value -> TextButton(onClick = { category = value }, colors = ButtonDefaults.textButtonColors(contentColor = Ink)) {
                Text(value, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (category == value) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.then(if (category == value) Modifier.border(1.dp, Line, RoundedCornerShape(12.dp)).padding(6.dp) else Modifier.padding(6.dp)))
            } }
        }
        HorizontalDivider(color = Line)
        val shown = if (category == "Recent") NotebookTemplateCatalog.inCategory("Recent", settings.downloaded.toList()) else NotebookTemplateCatalog.inCategory(category).filter { it.id != "blank" }
        LazyVerticalGrid(GridCells.Adaptive(166.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { paper -> Column(Modifier.border(1.dp, Ink, RoundedCornerShape(20.dp)).padding(10.dp)) {
                TemplatePreview(paper.id, templatePreview, Modifier.fillMaxWidth().aspectRatio(.75f).background(Color(0xFFFAFAFA)).padding(8.dp))
                Text(paper.name, Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
                Text("Ready to use · Local", Modifier.padding(top = 8.dp).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, lineHeight = 13.sp, color = Muted)
                Spacer(Modifier.height(38.dp))
                val downloaded = paper.id in settings.downloaded
                OutlinedButton(onClick = { if (downloaded) onUse(paper.id) else onSettingsChange(settings.copy(downloaded = settings.downloaded + paper.id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("download-template-${paper.id}"), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(4.dp)) { Text(if (downloaded) "Use template" else "Add to library", fontSize = 12.sp, lineHeight = 16.sp) }
            } }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No recent templates yet. Select a template to add it to your local library.", Modifier.padding(16.dp), fontSize = 13.sp, lineHeight = 17.sp, color = Muted) }
        }
    }
}

@Composable
private fun TemplatePreview(template: String, override: (@Composable (String, Modifier) -> Unit)?, modifier: Modifier = Modifier) {
    if (override != null) { override(template, modifier); return }
    if (template in setOf("daily", "time-block", "weekly", "monthly", "habit", "priority", "kanban", "revision", "vocabulary", "lab", "graph", "hex-grid", "sketch", "mind-map", "comic")) {
        PaperTemplatePreview(template, modifier)
    } else PaperPreview(template, modifier)
}

@Composable
fun PaperPreview(template: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.background(Color.White)) {
        val ink = Color(0xFFB6BDBA)
        val spacing = size.width / when (template) { "wide-ruled", "english", "custom-ruled" -> 15f; else -> 24f }
        val left = size.width * .07f; val right = size.width * .93f
        fun heading(text: String) { drawText(measurer, text, topLeft = Offset(left, size.height * .035f), style = TextStyle(color = Ink, fontSize = (size.width * .065f).toSp(), fontWeight = FontWeight.Medium)) }
        fun ruled(top: Float = size.height * .08f, bottom: Float = size.height * .94f, gap: Float = spacing) {
            var y = top
            while (y < bottom) { drawLine(ink, Offset(left, y), Offset(right, y), 1f); y += gap }
            if (template == "english") { var y2 = top + gap * .5f; while (y2 < bottom) { drawLine(Color(0xFFE3B6B6), Offset(left, y2), Offset(right, y2), 1f); y2 += gap } }
        }
        when (template) {
            "blank" -> Unit
            "todo" -> { heading("TO-DO LIST"); for (i in 0..12) { val y = size.height * .16f + i * size.height * .058f; drawRect(ink, Offset(left, y), androidx.compose.ui.geometry.Size(size.width * .025f, size.width * .025f), style = Stroke(1f)); drawLine(ink, Offset(left + size.width * .08f, y + size.width * .025f), Offset(right, y + size.width * .025f), 1f) } }
            "meeting" -> { heading("MEETING NOTES"); drawLine(ink, Offset(left, size.height * .14f), Offset(right, size.height * .14f), 1f); drawLine(ink, Offset(left, size.height * .18f), Offset(right, size.height * .18f), 1f); ruled(size.height * .25f); drawLine(ink, Offset(size.width * .72f, size.height * .25f), Offset(size.width * .72f, size.height * .94f), 1f) }
            "daily", "weekly", "project" -> {
                heading(when(template) { "daily" -> "DAILY PLANNER"; "weekly" -> "WEEKLY PLANNER"; else -> "PROJECT PLANNER" })
                val top = size.height * .14f; val bottom = size.height * .92f
                val columns = if (template == "weekly") 5 else 2
                for (i in 0..columns) { val x = left + (right - left) * i / columns; drawLine(ink, Offset(x, top), Offset(x, bottom), 1f) }
                for (i in 0..5) { val y = top + (bottom - top) * i / 5; drawLine(ink, Offset(left, y), Offset(right, y), 1f) }
            }
            "cornell" -> { heading("CORNELL NOTES"); ruled(size.height * .12f, size.height * .92f); drawLine(ink, Offset(size.width * .3f, size.height * .12f), Offset(size.width * .3f, size.height * .78f), 1.2f); drawLine(ink, Offset(left, size.height * .78f), Offset(right, size.height * .78f), 1.2f) }
            "lecture", "reading" -> { heading(if (template == "lecture") "LECTURE NOTES" else "READING NOTES"); drawLine(ink, Offset(left, size.height * .12f), Offset(right, size.height * .12f), 1f); ruled(size.height * .18f); if (template == "reading") drawLine(ink, Offset(size.width * .68f, size.height * .18f), Offset(size.width * .68f, size.height * .92f), 1f) }
            "isometric" -> { val step = size.width / 12f; var y = -size.height; while (y < size.height * 2) { drawLine(ink, Offset(0f, y), Offset(size.width, y + size.width), 1f); drawLine(ink, Offset(0f, y), Offset(size.width, y - size.width), 1f); y += step } }
            "music" -> { val staffGap = size.height / 12f; for (staff in 0..2) for (line in 0..4) { val y = size.height * .12f + staff * staffGap * 3f + line * staffGap * .35f; drawLine(ink, Offset(left, y), Offset(right, y), 1f) } }
            "storyboard" -> { for (row in 0..2) for (col in 0..1) { val x = left + col * size.width * .44f; val y = size.height * .08f + row * size.height * .29f; drawRoundRect(ink, topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(size.width * .4f, size.height * .22f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f), style = Stroke(1f)) } }
            "grid", "big-grid", "math-grid", "custom-grid" -> {
                val step = size.width / if (template == "big-grid") 12f else 24f
                var y = step; while (y < size.height) { drawLine(ink, Offset(0f, y), Offset(size.width, y), 1f); y += step }
                var x = step; while (x < size.width) { drawLine(ink, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            }
            "dots", "custom-dots" -> { var y = spacing; while (y < size.height) { var x = spacing; while (x < size.width) { drawCircle(ink, radius = 1f, center = Offset(x, y)); x += spacing }; y += spacing } }
            else -> ruled(gap = spacing)
        }
    }
}

@Composable
internal fun HomeIconButton(symbol: Symbol, label: String, selected: Boolean, onClick: () -> Unit) {
    val style = LocalHomeStyle.current
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag("home-${label.lowercase().replace(' ', '-')}").semantics { contentDescription = label }) {
        Box(Modifier.size(36.dp).background(if (selected) style.soft else Color.Transparent, RoundedCornerShape(style.item)), contentAlignment = Alignment.Center) {
            HomeSymbol(symbol, Modifier.size(22.dp), tint = if (selected) style.accent else Ink)
        }
    }
}

@Composable
internal fun HomeSymbol(symbol: Symbol, modifier: Modifier, folderFill: Boolean = false, tint: Color = Ink) {
    Canvas(modifier) {
        val path = Path()
        fun line(vararg points: Float) { path.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) path.lineTo(points[i], points[i+1]) }
        when (symbol) {
            Symbol.Trash -> { line(4f,6f,20f,6f); line(8f,6f,8f,3f,16f,3f,16f,6f); line(6f,6f,7f,22f,17f,22f,18f,6f); line(10f,10f,10f,18f); line(14f,10f,14f,18f) }
            Symbol.Copy -> { line(8f,8f,22f,8f,22f,22f,8f,22f,8f,8f); line(4f,17f,2f,17f,2f,2f,17f,2f,17f,4f) }
            Symbol.Rename -> { line(3f,19f,5f,13f,16f,2f,21f,7f,10f,18f,3f,19f); line(12f,22f,22f,22f) }
            Symbol.Share -> { line(14f,2f,22f,9f,14f,16f); line(22f,9f,11f,9f,7f,13f); line(6f,4f,2f,4f,2f,22f,20f,22f,20f,17f) }

            Symbol.Folder -> { line(2f, 7f, 9f, 7f, 11f, 4f, 20f, 6f, 20f, 10f); line(2f, 7f, 2f, 21f, 19f, 21f, 23f, 10f, 7f, 10f, 2f, 21f) }
            Symbol.All -> { line(3f, 3f, 21f, 3f, 21f, 21f, 3f, 21f, 3f, 3f); line(7f, 7f, 17f, 7f); line(7f, 12f, 17f, 12f); line(7f, 17f, 13f, 17f) }
            Symbol.Star -> { line(12f, 2f, 15f, 8f, 22f, 9f, 17f, 14f, 18f, 21f, 12f, 18f, 6f, 21f, 7f, 14f, 2f, 9f, 9f, 8f, 12f, 2f) }
            Symbol.Clock -> { path.addOval(Rect(2f,2f,22f,22f)); line(12f, 5f, 12f, 12f, 17f, 15f) }
            Symbol.Hub -> { line(3f, 12f, 21f, 12f, 21f, 21f, 3f, 21f, 3f, 12f); line(2f, 16f, 22f, 16f); line(12f, 12f, 6f, 6f, 9f, 2f, 12f, 6f, 15f, 2f, 18f, 6f, 12f, 12f) }
            Symbol.Tag -> { line(3f, 3f, 12f, 3f, 22f, 13f, 13f, 22f, 3f, 12f, 3f, 3f); path.addOval(Rect(6f,6f,9f,9f)) }
            Symbol.Search -> { path.addOval(Rect(2f,2f,17f,17f)); line(15f,15f,22f,22f) }
            Symbol.Menu -> { path.addOval(Rect(2f,2f,22f,22f)); line(7f,8f,17f,8f); line(7f,12f,17f,12f); line(7f,16f,17f,16f) }
            Symbol.Back -> { line(12f,5f,5f,12f,12f,19f); line(5f,12f,22f,12f) }
            Symbol.Plus -> { line(12f,2f,12f,22f); line(2f,12f,22f,12f) }
            Symbol.Close -> { line(5f,5f,19f,19f); line(5f,19f,19f,5f) }
            Symbol.More -> { line(4f,7f,20f,7f); line(4f,12f,20f,12f); line(4f,17f,20f,17f) }
            Symbol.Download -> { line(4f,8f,4f,21f,20f,21f,20f,8f); line(12f,2f,12f,15f); line(7f,10f,12f,15f,17f,10f) }
            Symbol.Sidebar -> { line(3f,4f,21f,4f,21f,20f,3f,20f,3f,4f); line(9f,4f,9f,20f); line(5f,8f,7f,8f); line(5f,11f,7f,11f) }
            Symbol.ChevronDown -> line(5f,9f,12f,16f,19f,9f)
            Symbol.ChevronRight -> line(9f,5f,16f,12f,9f,19f)
            Symbol.Check -> line(4f,12.5f,9.5f,18f,20f,6f)
            Symbol.Gear -> { path.addOval(Rect(5f,5f,19f,19f)); path.addOval(Rect(9.5f,9.5f,14.5f,14.5f)); line(12f,1.5f,12f,5f); line(12f,19f,12f,22.5f); line(1.5f,12f,5f,12f); line(19f,12f,22.5f,12f); line(4.6f,4.6f,7f,7f); line(17f,17f,19.4f,19.4f); line(4.6f,19.4f,7f,17f); line(17f,7f,19.4f,4.6f) }
            Symbol.Info -> { path.addOval(Rect(2f,2f,22f,22f)); line(12f,10.5f,12f,17f); line(12f,7f,12f,7.6f) }
            Symbol.Book -> { line(12f,6f,10f,4f,3f,4f,3f,19f,10f,19f,12f,21f,14f,19f,21f,19f,21f,4f,14f,4f,12f,6f,12f,21f) }
            Symbol.Notes -> { line(5f,2.5f,19f,2.5f,19f,21.5f,5f,21.5f,5f,2.5f); line(8.5f,8f,15.5f,8f); line(8.5f,12f,15.5f,12f); line(8.5f,16f,13f,16f) }
            Symbol.Dots -> { path.addOval(Rect(10.6f,3.6f,13.4f,6.4f)); path.addOval(Rect(10.6f,10.6f,13.4f,13.4f)); path.addOval(Rect(10.6f,17.6f,13.4f,20.4f)) }
            Symbol.Select -> { line(3f,3f,21f,3f,21f,21f,3f,21f,3f,3f); line(7f,12f,10.5f,15.5f,17f,8.5f) }
            Symbol.Open -> { line(10f,4f,4f,4f,4f,20f,20f,20f,20f,14f); line(13f,3f,21f,3f,21f,11f); line(21f,3f,11f,13f) }
        }
        with(drawContext.canvas) {
            save(); scale(size.width / 24f, size.height / 24f)
            if (folderFill || symbol == Symbol.Dots) drawPath(path, androidx.compose.ui.graphics.Paint().apply { color = if (symbol == Symbol.Dots) tint else Color(0xFFF2C94C) })
            drawPath(path, androidx.compose.ui.graphics.Paint().apply { color = tint; style = androidx.compose.ui.graphics.PaintingStyle.Stroke; strokeWidth = 1.4f; strokeJoin = androidx.compose.ui.graphics.StrokeJoin.Round; strokeCap = androidx.compose.ui.graphics.StrokeCap.Round })
            restore()
        }
    }
}

private data class DetailAction(val icon: Symbol, val label: String, val run: () -> Unit)

@Composable
private fun LibraryDetailsDialog(
    title: String, information: String, type: String, updated: String,
    preview: @Composable (Modifier) -> Unit, onDismiss: () -> Unit, actions: List<DetailAction>,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max = 760.dp).heightIn(max = 740.dp), shape = RoundedCornerShape(28.dp), color = Color.White, tonalElevation = 6.dp) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    Text("General", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) { HomeIconButton(Symbol.Close, "Close details", false, onDismiss) }
                }
                HorizontalDivider(color = Line.copy(alpha = .5f))
                Text(title, Modifier.padding(horizontal = 24.dp, vertical = 18.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(100.dp).aspectRatio(.72f).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(8.dp)) { preview(Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Information: $information", fontSize = 13.sp)
                        Text("Type: $type", fontSize = 13.sp)
                        Text("${if (type == "Folder") "Latest notebook update" else "Updated"}: $updated", fontSize = 12.sp, color = Muted)
                    }
                }
                HorizontalDivider(Modifier.padding(top = 20.dp), color = Line.copy(alpha = .5f))
                BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                    val columns = if (maxWidth < 380.dp) 3 else 4
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { action ->
                                    Column(Modifier.weight(1f).testTag("details-${action.label.lowercase().replace(' ', '-')}").clickable(onClick = action.run).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(58.dp).border(1.dp, Line, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { HomeSymbol(action.icon, Modifier.size(26.dp)) }
                                        Text(action.label, Modifier.padding(top = 10.dp), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
