package com.allient.folio.home

import com.allient.folio.FolioDialogSurface

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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import com.allient.folio.LucideGlyph
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.*
import androidx.compose.foundation.shape.CircleShape
import com.allient.folio.NotebookTemplateCatalog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import kotlin.math.roundToInt
import com.allient.folio.infinitePaperColorName
import com.allient.folio.infinitePaperPatternName
import com.allient.folio.templateCategoryName
import com.allient.folio.templateHeading
import com.allient.folio.templateName
import knotes.shared.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val Ink = HomeInk
private val Muted = HomeMuted
private val Line = HomeLine
private enum class HomeView { Home, Recent, Favorites }
private val papers = NotebookTemplateCatalog.all
internal enum class Symbol(val icon: ImageVector) {
    Trash(Lucide.Trash2), Copy(Lucide.Copy), Rename(Lucide.PencilLine), Share(Lucide.Share2), Folder(Lucide.Folder),
    All(Lucide.LayoutGrid), Star(Lucide.Star), Clock(Lucide.Clock), Hub(Lucide.Presentation), Tag(Lucide.Tag),
    Search(Lucide.Search), Menu(Lucide.Menu), Back(Lucide.ArrowLeft), Plus(Lucide.Plus), Close(Lucide.X), More(Lucide.Menu),
    Download(Lucide.Download), Sidebar(Lucide.PanelLeft), ChevronDown(Lucide.ChevronDown), ChevronRight(Lucide.ChevronRight),
    Check(Lucide.Check), Gear(Lucide.Settings), Info(Lucide.Info), Book(Lucide.BookOpen), Notes(Lucide.NotebookText),
    Dots(Lucide.EllipsisVertical), Select(Lucide.SquareCheck), Open(Lucide.ExternalLink),
    // Create menu: one distinct icon per action.
    NewNotebook(Lucide.NotebookPen), NewCanvas(Lucide.Infinity), NewFolder(Lucide.FolderPlus),
    ImportBook(Lucide.BookPlus), ImportAsNotebook(Lucide.FileInput), ImportBackup(Lucide.ArchiveRestore),
}
private val segments = listOf("all" to Res.string.home_segment_all, "notes" to Res.string.home_segment_notes, "canvas" to Res.string.home_segment_canvas, "books" to Res.string.home_segment_books)

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
    onOpenBook: (String) -> Unit = {}, onImportBook: (String?) -> Unit = {}, onImportPdfNotebook: () -> Unit = {},
    /** Live boards (the Live tab). The tab is hidden when [onOpenBoard] is null. */
    boards: List<HomeBoard> = emptyList(),
    boardPreview: @Composable (String, Modifier) -> Unit = { _, _ -> },
    onOpenBoard: ((String) -> Unit)? = null,
    onNewBoard: (String) -> Unit = {},
    onRenameBoard: (String, String) -> Unit = { _, _ -> },
    onDeleteBoard: (String) -> Unit = {},
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
    var newBoard by remember { mutableStateOf(false) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var sidebarPinned by rememberSaveable { mutableStateOf(true) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(saver = listSaver<MutableState<Set<String>>, String>({ it.value.toList() }, { mutableStateOf(it.toSet()) })) { mutableStateOf(setOf()) }
    var folds by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var bulkMove by rememberSaveable { mutableStateOf(false) }
    var bulkDelete by rememberSaveable { mutableStateOf(false) }
    var folderDialog by rememberSaveable { mutableStateOf(false) }
    // Saved by id so the folder editor survives rotation; the folder itself is looked up from settings.
    var editedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    val editedFolder = editedFolderId?.let { id -> settings.folders.firstOrNull { it.id == id } }
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
    var moveFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var moveBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by rememberSaveable { mutableStateOf("blank") }
    var draftName by rememberSaveable { mutableStateOf("") }
    var infiniteCanvas by rememberSaveable { mutableStateOf(false) }
    var infinitePattern by rememberSaveable { mutableStateOf(InfinitePaper().template) }
    // Resolved when shown, so the message is in the current app language.
    var notice by remember { mutableStateOf<(@Composable () -> String)?>(null) }
    val exitSelection = { selecting = false; selected = emptySet() }
    DisposableEffect(screen, folderId, searchMode, tag, drawerOpen, selecting, view, dragArmedId) {
        onBackAction(when {
            drawerOpen -> ({ drawerOpen = false })
            screen == "hub-create" -> ({ screen = "create" })
            screen != "home" -> ({ screen = "home" })
            selecting -> exitSelection
            dragArmedId != null -> ({ dragArmedId = null; notice = null })
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
    // Inside a subfolder, Back and "move out" go to the folder above, not straight to the library.
    val parentFolderId = currentFolder?.let { folders.parentOf(it) }
    val upName = folders.firstOrNull { it.id == parentFolderId }?.name ?: stringResource(Res.string.home_all_folders)
    LaunchedEffect(folderId, currentFolder) { if (folderId != null && currentFolder == null) folderId = null }
    val nextNotebookName = stringResource(Res.string.home_default_notebook_name, liveNotes.size + 1)
    val nextCanvasName = stringResource(Res.string.home_default_canvas_name, liveNotes.size + 1)
    val newNote = { infinite: Boolean ->
        draftName = if (infinite) nextCanvasName else nextNotebookName; selection = "blank"; infiniteCanvas = infinite; infinitePattern = InfinitePaper().template; screen = "create"
    }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
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
    val folderPreviews = remember(liveNotes, liveBooks, segment) { folderMosaicItems(liveNotes, liveBooks, segment) }
    val itemPreview: @Composable (FolderMosaicItem, Modifier) -> Unit = { item, modifier ->
        if (item.isBook) bookCover(item.id, modifier) else thumbnail(item.id, modifier)
    }
    @Composable fun folderCount(id: String) = liveNotes.count { placed(it.folderId) == id && ((it.infinite && showCanvas) || (!it.infinite && showNotes)) } + (if (showBooks) liveBooks.count { placed(it.folderId) == id } else 0)
    @Composable fun countLabel(count: Int) = pluralStringResource(libraryCountPlural(segment), count, count)
    fun latest(id: String) = maxOf(liveNotes.filter { placed(it.folderId) == id }.maxOfOrNull { it.updatedAt } ?: 0L, liveBooks.filter { placed(it.folderId) == id }.maxOfOrNull { it.openedAt } ?: 0L)
    @Composable fun folderDetail(id: String): String {
        val subfolders = folders.count { folders.parentOf(it) == id }
        val items = countLabel(folderCount(id))
        return if (subfolders == 0) items else pluralStringResource(Res.plurals.home_folder_detail, subfolders, subfolders, items)
    }
    val folderMatches = (when {
        view != HomeView.Home || tag != null -> emptyList()
        query.isNotEmpty() -> folders.filter { it.name.contains(query, true) }
        else -> folders.filter { folders.parentOf(it) == folderId }
    }).let { list ->
        when (preferences.sort) { HomeSort.Name, HomeSort.Type -> list.sortedBy { it.name.lowercase() }; HomeSort.Edited -> list.sortedByDescending { latest(it.id) }; else -> list }
    }
    val sections = buildList {
        if (folderId != null && dragArmedId != null) add(Section("parent", stringResource(Res.string.home_section_move_out), listOf(FolderEntry(null, stringResource(Res.string.home_drop_to_move_out)))))
        if (folderMatches.isNotEmpty()) add(Section("folders", stringResource(Res.string.home_section_folders), folderMatches.map { FolderEntry(it, folderDetail(it.id)) }))
        noteMatches.filter { !it.infinite }.takeIf { showNotes && it.isNotEmpty() }?.let { add(Section("notes", stringResource(Res.string.home_segment_notes), it.map(::NoteEntry))) }
        noteMatches.filter { it.infinite }.takeIf { showCanvas && it.isNotEmpty() }?.let { add(Section("canvas", stringResource(Res.string.home_segment_canvas), it.map(::NoteEntry))) }
        bookMatches.takeIf { showBooks && it.isNotEmpty() }?.let { add(Section("books", stringResource(Res.string.home_segment_books), it.map(::BookEntry))) }
    }
    val selectableKeys = sections.filter { it.key != "parent" }.flatMap { section -> section.entries.map { it.key } }
    val trashItems = buildList {
        settings.folders.filter { it.deletedAt > 0 }.forEach { add(TrashItem("folder", it.id, it.name, stringResource(Res.string.home_type_folder), it.deletedAt)) }
        notebooks.filter { it.deletedAt > 0 }.forEach { add(TrashItem("note", it.id, it.title, if (it.infinite) stringResource(Res.string.home_type_canvas) else pluralStringResource(Res.plurals.home_type_notebook_pages, it.pageCount, it.pageCount), it.deletedAt)) }
        books.filter { it.deletedAt > 0 }.forEach { add(TrashItem("book", it.id, it.title, stringResource(Res.string.home_type_book, it.format.uppercase()), it.deletedAt)) }
    }.sortedByDescending { it.deletedAt }
    fun ids(prefix: String) = selected.filter { it.startsWith("$prefix:") }.map { it.removePrefix("$prefix:") }.toSet()
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key }

    CompositionLocalProvider(LocalHomeStyle provides style) {
    BoxWithConstraints(Modifier.fillMaxSize().background(HomeBackground).onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }) {
        val wide = maxWidth >= 720.dp
        val screenWidth = maxWidth
        val sidebarShown = wide && sidebarPinned && screen in setOf("home", "trash", "preferences", "about")
        val compactBar = (if (sidebarShown) maxWidth - 265.dp else maxWidth) < 560.dp
        LaunchedEffect(wide) { if (wide) drawerOpen = false }
        val sidebarKey = when {
            screen == "trash" || screen == "preferences" || screen == "about" -> screen
            currentFolder != null -> "folder:${currentFolder.id}"
            view == HomeView.Recent -> "recent"; view == HomeView.Favorites -> "favorites"; else -> "home"
        }
        val sidebar: @Composable (Modifier) -> Unit = { modifier ->
            HomeSidebar(folders, sidebarKey, trashItems.size, modifier, onCreateFolder = { drawerOpen = false; editedFolderId = null; folderDialog = true }, onSelect = { key ->
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
                        if (draftName.isBlank()) draftName = nextNotebookName
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
                            // Phones get icon actions so the selected count keeps room next to five buttons.
                            if (compactBar) Box(Modifier.testTag("selection-cancel")) { HomeIconButton(Symbol.Close, stringResource(Res.string.home_cancel_selection), false, exitSelection, tag = "cancel-selection") }
                            else TextButton(onClick = exitSelection, modifier = Modifier.testTag("selection-cancel")) { Text(stringResource(Res.string.home_cancel), color = Ink) }
                            Text(pluralStringResource(Res.plurals.home_selected_count, selected.size, selected.size), Modifier.weight(1f).padding(start = 4.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val all = selectableKeys.isNotEmpty() && selected.containsAll(selectableKeys)
                            val toggleAll = { selected = if (all) emptySet() else selectableKeys.toSet() }
                            if (compactBar) HomeIconButton(Symbol.Select, stringResource(if (all) Res.string.home_clear_selection else Res.string.home_select_all), all, toggleAll, tag = if (all) "clear-selection" else "select-all")
                            else TextButton(onClick = toggleAll) { Text(stringResource(if (all) Res.string.home_clear else Res.string.home_select_all), color = style.accent) }
                            HomeIconButton(Symbol.Folder, stringResource(Res.string.home_move_selected), false, { if (selected.isNotEmpty()) bulkMove = true else notice = { stringResource(Res.string.home_notice_select_to_move) } }, tag = "move-selected")
                            HomeIconButton(Symbol.Star, stringResource(Res.string.home_favorite_selected), false, tag = "favorite-selected", onClick = {
                                val notes = ids("note"); val picked = ids("book")
                                if (notes.isNotEmpty() || picked.isNotEmpty()) {
                                    val target = !(liveNotes.filter { it.id in notes }.all { it.favorite } && liveBooks.filter { it.id in picked }.all { it.favorite })
                                    liveNotes.filter { it.id in notes && it.favorite != target }.forEach { onFavorite(it.id) }
                                    picked.forEach { onFavoriteBook(it, target) }
                                    notice = { stringResource(if (target) Res.string.home_notice_added_favorites else Res.string.home_notice_removed_favorites) }; exitSelection()
                                } else notice = { stringResource(Res.string.home_notice_folders_no_favorites) }
                            })
                            HomeIconButton(Symbol.Trash, stringResource(Res.string.home_delete_selected), false, { if (selected.isNotEmpty()) bulkDelete = true }, tag = "delete-selected")
                        } else {
                            val tabs = (if (onOpenBoard != null) segments + ("boards" to Res.string.home_segment_live) else segments).map { (id, label) -> id to stringResource(label) }
                            // Five tabs need the compact padding below ~760 dp, or the pill runs into the search button.
                            val tightTabs = compactBar || (tabs.size > 4 && screenWidth < 760.dp)
                            val pill: @Composable (Modifier) -> Unit = { modifier -> SegmentPill(tabs, segment, tightTabs, { segment = it; notice = null }, modifier) }
                            Box(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp)) {
                                Box(Modifier.align(Alignment.CenterStart)) { HomeIconButton(Symbol.Sidebar, stringResource(Res.string.home_toggle_sidebar), wide && sidebarPinned, { if (wide) sidebarPinned = !sidebarPinned else drawerOpen = true }, tag = "toggle-sidebar") }
                                if (!compactBar) pill(Modifier.align(Alignment.Center))
                                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                                    HomeIconButton(Symbol.Search, stringResource(Res.string.home_search_notebooks), searchMode == "text", { viewName = HomeView.Home.name; folderId = null; searchMode = if (searchMode == "text") "" else "text"; search = "" }, tag = "search-notebooks")
                                    Box {
                                        HomeIconButton(Symbol.Dots, stringResource(Res.string.home_library_menu), menu || sortMenu, { menu = !menu }, tag = "library-menu")
                                        val check: @Composable (Boolean) -> Unit = { on -> if (on) HomeSymbol(Symbol.Check, Modifier.size(18.dp), tint = style.accent) }
                                        DropdownMenu(menu, { menu = false }, modifier = Modifier.penPressureFilter()) {
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.home_select)) }, modifier = Modifier.testTag("home-select"), onClick = { menu = false; selecting = true; selected = emptySet() })
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.home_sort_by_current, sortName(preferences.sort))) }, modifier = Modifier.testTag("home-sort"),
                                                trailingIcon = { HomeSymbol(Symbol.ChevronRight, Modifier.size(14.dp)) }, onClick = { menu = false; sortMenu = true })
                                            HorizontalDivider(color = HomeHairline)
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.home_view_grid)) }, modifier = Modifier.testTag("home-view-grid"), trailingIcon = { check(preferences.viewMode != "list") }, onClick = { menu = false; onPreferencesChange(preferences.copy(viewMode = "grid")) })
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.home_view_list)) }, modifier = Modifier.testTag("home-view-list"), trailingIcon = { check(preferences.viewMode == "list") }, onClick = { menu = false; onPreferencesChange(preferences.copy(viewMode = "list")) })
                                            HorizontalDivider(color = HomeHairline)
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.home_search_tags)) }, modifier = Modifier.testTag("home-search-tags"), onClick = { menu = false; viewName = HomeView.Home.name; folderId = null; searchMode = if (searchMode == "tags") "" else "tags" })
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.template_hub_title)) }, modifier = Modifier.testTag("home-template-hub"), onClick = { menu = false; changeScreen("hub") })
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.home_import_document)) }, onClick = { menu = false; onImport() })
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.home_export_library)) }, modifier = Modifier.testTag("home-export-library-backup"), onClick = { menu = false; onExportLibraryBackup() })
                                            DropdownMenuItem(text = { Text(stringResource(Res.string.backup_settings_title)) }, modifier = Modifier.testTag("home-settings"), onClick = { menu = false; onOpenSettings() })
                                        }
                                        DropdownMenu(sortMenu, { sortMenu = false }, modifier = Modifier.penPressureFilter()) {
                                            Text(stringResource(Res.string.home_sort_by), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = Muted)
                                            HomeSort.all.forEach { (id, _) ->
                                                DropdownMenuItem(text = { Text(sortName(id)) }, modifier = Modifier.testTag("home-sort-$id"), trailingIcon = { check(preferences.sort == id) },
                                                    onClick = { sortMenu = false; onPreferencesChange(preferences.copy(sort = id)) })
                                            }
                                        }
                                    }
                                }
                            }
                            if (compactBar) Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) { pill(Modifier) }
                        }
                        if (searchMode == "text") OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag("library-search"), singleLine = true,
                            label = { Text(stringResource(Res.string.home_search_label)) }, shape = RoundedCornerShape(style.item),
                            trailingIcon = { HomeIconButton(Symbol.Close, stringResource(Res.string.home_close_search), false, { searchMode = ""; search = "" }, tag = "close-search") })
                        if (searchMode == "tags") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = style.accent, selectedLabelColor = Color.White, containerColor = Color.White, labelColor = Ink)
                            FilterChip(colors = chipColors, selected = tag == null, onClick = { tag = null }, label = { Text(stringResource(Res.string.home_all_tags)) })
                            allTags.forEach { value -> Spacer(Modifier.width(8.dp)); FilterChip(colors = chipColors, selected = tag == value, onClick = { tag = if (tag == value) null else value }, label = { Text(value) }) }
                            if (allTags.isEmpty()) Text(stringResource(Res.string.home_tags_hint), Modifier.padding(12.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
                            Spacer(Modifier.width(8.dp)); HomeIconButton(Symbol.Close, stringResource(Res.string.home_close_tag_search), false, { searchMode = ""; tag = null }, tag = "close-tag-search")
                        }
                        val heading = when { currentFolder != null -> currentFolder.name; tag != null -> "#$tag"; query.isNotEmpty() -> null; view == HomeView.Recent -> stringResource(Res.string.sidebar_recent); view == HomeView.Favorites -> stringResource(Res.string.sidebar_favorites); else -> null }
                        if (heading != null || notice != null) Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (currentFolder != null || (view != HomeView.Home && !global)) HomeIconButton(Symbol.Back, stringResource(Res.string.home_back_to_library), false, { folderId = parentFolderId; viewName = HomeView.Home.name; dragArmedId = null; notice = null }, tag = "back-to-library")
                            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                if (heading != null) Text(heading, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                notice?.let { Text(it(), fontSize = 12.sp, lineHeight = 16.sp, color = Muted) }
                            }
                            if (currentFolder != null && !selecting) MenuCaret(stringResource(Res.string.home_folder_options), { detailsFolderId = currentFolder.id })
                            if (dragArmedId != null) HomeIconButton(Symbol.Close, stringResource(Res.string.home_cancel_move), false, { dragArmedId = null; notice = null }, tag = "cancel-move")
                            if (tag != null) HomeIconButton(Symbol.Close, stringResource(Res.string.home_clear_tag_filter), false, { tag = null }, tag = "clear-tag-filter")
                            if (notice != null && dragArmedId == null) HomeIconButton(Symbol.Close, stringResource(Res.string.home_dismiss_message), false, { notice = null }, tag = "dismiss-message")
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            val grid = preferences.viewMode != "list"
                            val compact = maxWidth < 480.dp
                            val pad = if (compact) 16.dp else 28.dp
                            val gap = if (!grid) 12.dp else if (compact) 14.dp else 22.dp
                            val cell = if (compact) 100.dp else 112.dp
                            val columns = if (grid) ((maxWidth - pad * 2 + gap) / (cell + gap)).toInt().coerceAtLeast(2) else if (maxWidth >= 720.dp) 2 else 1
                            val itemSections = sections.count { it.key != "folders" && it.key != "parent" }
                            if (segment == "boards" && onOpenBoard != null) BoardsGrid(boards.filter { query.isEmpty() || it.title.contains(query, true) },
                                grid, columns, pad, gap, boardPreview, formatDate, onOpenBoard, onRenameBoard, onDeleteBoard)
                            else LazyVerticalGrid(columns = GridCells.Fixed(columns), modifier = Modifier.fillMaxSize().testTag("notebook-grid"), contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 112.dp),
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
                                                val openFolderLabel = stringResource(Res.string.home_open_folder, folder?.name ?: upName)
                                                LibraryTile(grid, folder?.name ?: upName, entry.detail, stringResource(Res.string.home_options_for, folder?.name ?: upName), { if (folder != null) detailsFolderId = folder.id },
                                                    Modifier.onGloballyPositioned { bounds[target] = it.boundsInRoot() }
                                                        .combinedClickable(onClick = { if (selecting && folder != null) toggle(entry.key) else folderId = folder?.id ?: parentFolderId }, onLongClickLabel = stringResource(Res.string.home_folder_options),
                                                            onLongClick = { if (folder != null) detailsFolderId = folder.id })
                                                        .testTag("folder-$target").semantics { contentDescription = openFolderLabel },
                                                    mark, highlighted = dragId != null && bounds[target]?.contains(dragPoint) == true, previewBackground = Color(0xFFFAFAFA)) {
                                                    val covers = if (folder?.style == "mosaic") folderPreviews[folder.id].orEmpty() else emptyList()
                                                    if (covers.isNotEmpty()) FolderMosaic(covers, itemPreview, Modifier.fillMaxSize().testTag("folder-cover-preview-$target"))
                                                    else FolderArtwork(folder?.color ?: "sage", folder?.style ?: "classic", if (grid) Modifier.fillMaxWidth(.85f).aspectRatio(1.35f) else Modifier.fillMaxWidth(.9f).aspectRatio(1.35f))
                                                }
                                            }
                                            is NoteEntry -> {
                                                val note = entry.note
                                                var cardBounds by remember { mutableStateOf(Rect.Zero) }
                                                val openLabel = stringResource(Res.string.home_open_item, note.title)
                                                val pages = if (note.infinite) stringResource(Res.string.home_infinite_badge) else "${note.currentPage.coerceIn(1, note.pageCount.coerceAtLeast(1))}/${note.pageCount}"
                                                LibraryTile(grid, note.title, if (grid) note.dateLabel else "${if (note.favorite) "★ " else ""}${note.dateLabel} · $pages", stringResource(Res.string.home_options_for, note.title), { detailsNoteId = note.id },
                                                    Modifier.onGloballyPositioned { cardBounds = it.boundsInRoot() }
                                                        .then(if (dragArmedId == note.id) Modifier.pointerInput(note.id, folderId, folders) {
                                                            detectDragGestures(
                                                                onDragStart = { local -> dragId = note.id; dragPoint = cardBounds.topLeft + local },
                                                                onDragEnd = {
                                                                    val targets = folderMatches.map { it.id } + listOfNotNull("root".takeIf { folderId != null })
                                                                    val target = targets.firstOrNull { bounds[it]?.contains(dragPoint) == true }
                                                                    if (target != null) { onMove(note.id, if (target == "root") parentFolderId else target); notice = { stringResource(Res.string.home_notice_notebook_moved) } } else notice = { stringResource(Res.string.home_notice_no_folder) }
                                                                    dragId = null; dragArmedId = null
                                                                }, onDragCancel = { dragId = null; dragArmedId = null; notice = null },
                                                                onDrag = { change, amount -> change.consume(); dragPoint += amount },
                                                            )
                                                        } else Modifier.combinedClickable(onClick = { if (selecting) toggle(entry.key) else onOpen(note.id) }, onLongClickLabel = stringResource(Res.string.home_notebook_options),
                                                            onLongClick = { if (selecting) toggle(entry.key) else detailsNoteId = note.id }))
                                                        .testTag("notebook-${note.id}").semantics { contentDescription = openLabel },
                                                    mark, highlighted = dragArmedId == note.id) {
                                                    thumbnail(note.id, Modifier.fillMaxSize().padding(if (grid) 8.dp else 3.dp))
                                                    if (grid) {
                                                        TileBadge(pages, Modifier.align(Alignment.BottomStart))
                                                        TileBadge(stringResource(if (note.infinite) Res.string.home_badge_canvas else Res.string.home_badge_note), Modifier.align(Alignment.TopStart), filled = note.infinite, tag = "notebook-kind-${note.id}")
                                                        if (note.favorite && mark == null) HomeSymbol(Symbol.Star, Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp), folderFill = true)
                                                    }
                                                }
                                            }
                                            is BookEntry -> {
                                                val book = entry.book
                                                val progress = "${(book.progress * 100).roundToInt()}%"
                                                val openLabel = stringResource(Res.string.home_open_item, book.title)
                                                LibraryTile(grid, book.title, if (book.missing) stringResource(Res.string.home_book_missing) else if (grid) book.dateLabel else (if (book.favorite) "★ " else "") + stringResource(Res.string.home_book_list_detail, book.format.uppercase(), progress, book.dateLabel),
                                                    stringResource(Res.string.home_options_for, book.title), { detailsBookId = book.id },
                                                    Modifier.combinedClickable(onClick = { if (selecting) toggle(entry.key) else onOpenBook(book.id) }, onLongClickLabel = stringResource(Res.string.home_book_options),
                                                        onLongClick = { if (selecting) toggle(entry.key) else detailsBookId = book.id })
                                                        .testTag("book-${book.id}").semantics { contentDescription = openLabel },
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
                                        Text(stringResource(when {
                                            global -> Res.string.home_empty_search
                                            view == HomeView.Favorites -> Res.string.home_empty_favorites
                                            view == HomeView.Recent -> Res.string.home_empty_recent
                                            currentFolder != null -> Res.string.home_empty_folder
                                            segment == "books" -> Res.string.home_empty_books
                                            else -> Res.string.home_empty
                                        }), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
                                        Text(stringResource(if (segment == "books") Res.string.home_empty_books_hint else Res.string.home_empty_hint), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = Muted)
                                    }
                                }
                            }
                            if (!selecting) Box(Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp)) {
                                val createLabel = stringResource(Res.string.home_create)
                                Surface(onClick = { if (segment == "boards" && onOpenBoard != null) newBoard = true else fabMenu = true }, shape = CircleShape, color = style.accent, shadowElevation = 6.dp,
                                    modifier = Modifier.size(60.dp).testTag("create-notebook").semantics { contentDescription = createLabel }) {
                                    Box(contentAlignment = Alignment.Center) { HomeSymbol(Symbol.Plus, Modifier.size(24.dp), tint = Color.White) }
                                }
                                DropdownMenu(fabMenu, { fabMenu = false }, modifier = Modifier.penPressureFilter()) {
                                    DropdownMenuItem(text = { Text(maxLines = 1, text = stringResource(Res.string.home_new_notebook)) }, modifier = Modifier.widthIn(min = 320.dp).testTag("home-new-notebook"), leadingIcon = { HomeSymbol(Symbol.NewNotebook, Modifier.size(20.dp)) }, onClick = { fabMenu = false; newNote(false) })
                                    DropdownMenuItem(text = { Text(maxLines = 1, text = stringResource(Res.string.home_import_pdf_notebook)) }, modifier = Modifier.widthIn(min = 320.dp).testTag("home-import-pdf-notebook"), leadingIcon = { HomeSymbol(Symbol.ImportAsNotebook, Modifier.size(20.dp)) }, onClick = { fabMenu = false; onImportPdfNotebook() })
                                    DropdownMenuItem(text = { Text(maxLines = 1, text = stringResource(Res.string.home_new_canvas)) }, modifier = Modifier.widthIn(min = 320.dp).testTag("home-new-canvas"), leadingIcon = { HomeSymbol(Symbol.NewCanvas, Modifier.size(20.dp)) }, onClick = { fabMenu = false; newNote(true) })
                                    DropdownMenuItem(text = { Text(maxLines = 1, text = stringResource(Res.string.home_new_folder)) }, modifier = Modifier.widthIn(min = 320.dp).testTag("home-new-folder"), leadingIcon = { HomeSymbol(Symbol.NewFolder, Modifier.size(20.dp)) }, onClick = { fabMenu = false; editedFolderId = null; folderDialog = true })
                                    HorizontalDivider(color = HomeHairline)
                                    DropdownMenuItem(text = { Text(maxLines = 1, text = stringResource(Res.string.home_import_book)) }, modifier = Modifier.widthIn(min = 320.dp).testTag("home-import-book"), leadingIcon = { HomeSymbol(Symbol.ImportBook, Modifier.size(20.dp)) }, onClick = { fabMenu = false; onImportBook(currentFolder?.id) })
                                    DropdownMenuItem(text = { Text(maxLines = 1, text = stringResource(Res.string.home_import_backup)) }, modifier = Modifier.widthIn(min = 320.dp).testTag("home-import"), leadingIcon = { HomeSymbol(Symbol.ImportBackup, Modifier.size(20.dp)) }, onClick = { fabMenu = false; onImport() })
                                }
                            }
                            if (newBoard) BoardNameDialog(stringResource(Res.string.boards_new_title), "", stringResource(Res.string.home_create), onDismiss = { newBoard = false }) { newBoard = false; onNewBoard(it) }
                        }
                        if (isSaving) Text(stringResource(Res.string.home_saving), Modifier.padding(horizontal = 24.dp, vertical = 4.dp), fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
                    }
                }
            }
        }
        if (!wide && drawerOpen && screen in setOf("home", "trash", "preferences", "about")) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).clickable(onClickLabel = stringResource(Res.string.home_close_sidebar)) { drawerOpen = false })
            Surface(Modifier.fillMaxHeight().widthIn(max = 300.dp).fillMaxWidth(.84f), shadowElevation = 8.dp, color = HomeSidebarBackground) { sidebar(Modifier.fillMaxSize()) }
        }
        if (dragId != null) Surface(Modifier.offset { IntOffset((dragPoint.x - rootOrigin.x).roundToInt(), (dragPoint.y - rootOrigin.y).roundToInt()) }.widthIn(max = 180.dp), shadowElevation = 6.dp, color = style.soft, shape = RoundedCornerShape(style.card)) {
            Text(notebooks.firstOrNull { it.id == dragId }?.title.orEmpty(), Modifier.padding(12.dp), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
    val trashNote = if (preferences.trashRetentionDays > 0) pluralStringResource(Res.plurals.home_restore_for_days, preferences.trashRetentionDays, preferences.trashRetentionDays) else stringResource(Res.string.home_restore_until_empty)
    detailsNoteId?.let { id -> liveNotes.firstOrNull { it.id == id }?.let { note ->
        LibraryDetailsDialog(note.title, if (note.infinite) stringResource(Res.string.home_details_infinite) else pluralStringResource(Res.plurals.home_details_pages, note.pageCount, note.pageCount, note.currentPage),
            stringResource(if (note.infinite) Res.string.home_type_infinite_notebook else Res.string.home_type_notebook), note.dateLabel,
            preview = { modifier -> thumbnail(note.id, modifier) }, onDismiss = { detailsNoteId = null },
            actions = listOf(
                DetailAction(Symbol.Open, stringResource(Res.string.home_open), "open") { detailsNoteId = null; onOpen(id) },
                DetailAction(Symbol.Trash, stringResource(Res.string.home_delete), "delete") { detailsNoteId = null; deleteId = id },
                DetailAction(Symbol.Folder, stringResource(Res.string.home_move), "move") { detailsNoteId = null; moveId = id },
                DetailAction(Symbol.Copy, stringResource(Res.string.home_copy), "copy") { detailsNoteId = null; onCopy(id) },
                DetailAction(Symbol.Rename, stringResource(Res.string.home_rename), "rename") { detailsNoteId = null; renameTitle = note.title; renameId = id },
                DetailAction(Symbol.Share, stringResource(Res.string.home_share_export), "share-and-export") { detailsNoteId = null; exportId = id },
                DetailAction(Symbol.Star, stringResource(if (note.favorite) Res.string.home_unfavorite else Res.string.home_favorites_action), if (note.favorite) "unfavorite" else "favorites", active = note.favorite) { onFavorite(id) },
            ))
    } }
    detailsBookId?.let { id -> liveBooks.firstOrNull { it.id == id }?.let { book ->
        LibraryDetailsDialog(book.title, listOfNotNull(book.author.takeIf { it.isNotBlank() }, if (book.format == "pdf") pluralStringResource(Res.plurals.home_page_count, book.pageCount, book.pageCount) else null,
                stringResource(Res.string.home_percent_read, "${(book.progress * 100).roundToInt()}%")).joinToString(" · "),
            stringResource(Res.string.home_type_book, book.format.uppercase()), if (book.missing) stringResource(Res.string.home_book_missing) else book.dateLabel,
            preview = { modifier -> bookCover(book.id, modifier) }, onDismiss = { detailsBookId = null },
            actions = listOf(
                DetailAction(Symbol.Open, stringResource(Res.string.home_open), "open") { detailsBookId = null; onOpenBook(id) },
                DetailAction(Symbol.Trash, stringResource(Res.string.home_delete), "delete") { detailsBookId = null; deleteBookId = id },
                DetailAction(Symbol.Folder, stringResource(Res.string.home_move), "move") { detailsBookId = null; moveBookId = id },
                DetailAction(Symbol.Star, stringResource(if (book.favorite) Res.string.home_unfavorite else Res.string.home_favorites_action), if (book.favorite) "unfavorite" else "favorites", active = book.favorite) { onFavoriteBook(id, !book.favorite) },
            ))
    } }
    detailsFolderId?.let { id -> folders.firstOrNull { it.id == id }?.let { folder ->
        val contents = liveNotes.filter { placed(it.folderId) == id }
        val bookCount = liveBooks.count { placed(it.folderId) == id }
        LibraryDetailsDialog(folder.name, stringResource(Res.string.home_folder_contents, pluralStringResource(Res.plurals.home_count_notebooks, contents.size, contents.size), pluralStringResource(Res.plurals.home_count_books, bookCount, bookCount)),
            stringResource(Res.string.home_type_folder), contents.maxByOrNull { it.updatedAt }?.dateLabel ?: stringResource(Res.string.home_no_notebooks_yet), isFolder = true,
            preview = { modifier ->
                val previews = folderPreviews[id].orEmpty()
                if (folder.style == "mosaic" && previews.isNotEmpty()) FolderMosaic(previews, itemPreview, modifier)
                else Box(modifier, contentAlignment = Alignment.Center) { FolderArtwork(folder.color, folder.style, Modifier.size(100.dp, 76.dp)) }
            },
            onDismiss = { detailsFolderId = null }, actions = listOf(
                DetailAction(Symbol.Open, stringResource(Res.string.home_open), "open") { detailsFolderId = null; screen = "home"; viewName = HomeView.Home.name; folderId = id },
                DetailAction(Symbol.Folder, stringResource(Res.string.home_move), "move") { detailsFolderId = null; moveFolderId = id },
                DetailAction(Symbol.Rename, stringResource(Res.string.home_edit_folder), "edit-folder") { detailsFolderId = null; editedFolderId = folder.id; folderDialog = true },
                DetailAction(Symbol.Trash, stringResource(Res.string.home_delete), "delete") { detailsFolderId = null; deleteFolderId = id },
            ))
    } }
    exportId?.let { id -> AlertDialog(onDismissRequest = { exportId = null }, title = { Text(stringResource(Res.string.home_share_export)) }, text = {
        Column { listOf("pdf" to Res.string.home_export_pdf, "png" to Res.string.home_export_png, "folio" to Res.string.home_export_folio).forEach { (format, label) ->
            TextButton(onClick = { exportId = null; onExport(id, format) }) { Text(stringResource(label)) }
        } }
    }, confirmButton = { TextButton(onClick = { exportId = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
    renameId?.let { id -> AlertDialog(onDismissRequest = { renameId = null }, title = { Text(stringResource(Res.string.home_rename_notebook)) }, text = {
        OutlinedTextField(renameTitle, { renameTitle = it }, label = { Text(stringResource(Res.string.home_notebook_name)) }, singleLine = true, shape = RoundedCornerShape(16.dp))
    }, confirmButton = { TextButton(enabled = renameTitle.isNotBlank(), onClick = { onRename(id, renameTitle.trim()); renameId = null }) { Text(stringResource(Res.string.home_save)) } }, dismissButton = { TextButton(onClick = { renameId = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
    deleteFolderId?.let { id -> AlertDialog(onDismissRequest = { deleteFolderId = null }, title = { Text(stringResource(Res.string.home_trash_folder_title)) },
        text = { Text(stringResource(Res.string.home_trash_folder_text)) },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm-delete-folder"), onClick = {
            onTrash("folder", setOf(id)); if (folderId == id) folderId = null; deleteFolderId = null
        }) { Text(stringResource(Res.string.home_move_to_trash)) } }, dismissButton = { TextButton(onClick = { deleteFolderId = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
    if (folderDialog) FolderEditorDialog(existing = editedFolder, folders = folders, onDismiss = { folderDialog = false }, onSave = { name, color, style ->
            val edit = editedFolder
            val updated = if (edit != null) settings.folders.map { if (it.id == edit.id) it.copy(name = name, color = color, style = style) else it } else {
                val id = generateSequence(1) { it + 1 }.map { "folder-$it" }.first { id -> settings.folders.none { it.id == id } }
                settings.folders + NoteFolder(id, name, color, style, parentId = currentFolder?.id?.takeIf { screen == "home" })
            }
            onSettingsChange(settings.copy(folders = updated)); folderDialog = false
    }, contentPreview = { modifier ->
        val covers = editedFolder?.let { folderPreviews[it.id] }.orEmpty()
        if (covers.isNotEmpty()) FolderMosaic(covers, itemPreview, modifier)
        else FolderMosaic(listOf(stringResource(Res.string.home_badge_note), "PDF", stringResource(Res.string.home_badge_canvas), "EPUB"), { format, cell ->
            Box(cell.background(style.soft).padding(4.dp), contentAlignment = Alignment.Center) {
                HomeSymbol(Symbol.Book, Modifier.size(20.dp), tint = style.accent)
                Text(format, Modifier.align(Alignment.BottomCenter), fontSize = 7.sp, color = style.accent)
            }
        }, modifier)
    })
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(Res.string.home_trash_notebook_title)) }, text = { Text(trashNote) },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm-delete-notebook"), onClick = { onTrash("note", setOf(id)); deleteId = null }) { Text(stringResource(Res.string.home_move_to_trash)) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
    deleteBookId?.let { id -> AlertDialog(onDismissRequest = { deleteBookId = null }, title = { Text(stringResource(Res.string.home_trash_book_title)) }, text = { Text(stringResource(Res.string.home_trash_book_text, trashNote)) },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm-delete-book"), onClick = { onTrash("book", setOf(id)); deleteBookId = null }) { Text(stringResource(Res.string.home_move_to_trash)) } },
        dismissButton = { TextButton(onClick = { deleteBookId = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
    if (bulkDelete) AlertDialog(onDismissRequest = { bulkDelete = false }, title = { Text(pluralStringResource(Res.plurals.home_bulk_trash_title, selected.size, selected.size)) },
        text = {
            val restore = if (preferences.trashRetentionDays > 0) pluralStringResource(Res.plurals.home_restore_them_for_days, preferences.trashRetentionDays, preferences.trashRetentionDays) else stringResource(Res.string.home_restore_them_until_empty)
            Text(listOfNotNull(stringResource(Res.string.home_bulk_trash_folders_note).takeIf { ids("folder").isNotEmpty() }, restore).joinToString(" "))
        },
        confirmButton = { TextButton(modifier = Modifier.testTag("confirm-bulk-delete"), onClick = {
            listOf("note", "book", "folder").forEach { kind -> ids(kind).takeIf { it.isNotEmpty() }?.let { onTrash(kind, it) } }
            val count = selected.size
            notice = { pluralStringResource(Res.plurals.home_notice_moved_to_trash, count, count) }; bulkDelete = false; exitSelection()
        }) { Text(stringResource(Res.string.home_move_to_trash)) } }, dismissButton = { TextButton(onClick = { bulkDelete = false }) { Text(stringResource(Res.string.home_cancel)) } })
    // [exclude]: folders being moved and their subfolders, which can't be their own destination.
    val moveTargets: @Composable (Set<String>, (String?) -> Unit) -> Unit = { exclude, pick ->
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { pick(null) }) { Text(stringResource(Res.string.home_main_library)) }
            folders.folderTree().filter { (folder, _) -> folder.id !in exclude }.forEach { (folder, depth) -> TextButton(onClick = { pick(folder.id) }, Modifier.padding(start = (depth * 16).dp)) { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
    }
    if (bulkMove) AlertDialog(onDismissRequest = { bulkMove = false }, title = { Text(stringResource(Res.string.home_move_to_folder)) }, text = {
        moveTargets(folders.withSubfolders(ids("folder"))) { target ->
            ids("note").forEach { onMove(it, target) }; ids("book").forEach { onMoveBook(it, target) }
            if (ids("folder").isNotEmpty()) onSettingsChange(settings.copy(folders = settings.folders.moveFolders(ids("folder"), target)))
            val name = folders.firstOrNull { it.id == target }?.name
            notice = { if (name != null) stringResource(Res.string.home_notice_moved_to, name) else stringResource(Res.string.home_notice_moved_to_main) }
            bulkMove = false; exitSelection()
        }
    }, confirmButton = { TextButton(onClick = { bulkMove = false }) { Text(stringResource(Res.string.home_cancel)) } })
    moveId?.let { id -> AlertDialog(onDismissRequest = { moveId = null }, title = { Text(stringResource(Res.string.home_move_to_folder)) }, text = {
        Column {
            TextButton(onClick = {
                screen = "home"; viewName = HomeView.Home.name; folderId = liveNotes.firstOrNull { it.id == id }?.folderId?.let(::placed)
                dragArmedId = id; moveId = null; notice = { stringResource(Res.string.home_notice_drag_hint) }
            }) { Text(stringResource(Res.string.home_drag_on_home)) }
            moveTargets(emptySet()) { target -> onMove(id, target); moveId = null }
        }
    }, confirmButton = { TextButton(onClick = { moveId = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
    moveBookId?.let { id -> AlertDialog(onDismissRequest = { moveBookId = null }, title = { Text(stringResource(Res.string.home_move_book_title)) }, text = {
        moveTargets(emptySet()) { target -> onMoveBook(id, target); moveBookId = null }
    }, confirmButton = { TextButton(onClick = { moveBookId = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
    moveFolderId?.let { id -> AlertDialog(onDismissRequest = { moveFolderId = null }, title = { Text(stringResource(Res.string.home_move_folder_title)) }, text = {
        moveTargets(folders.withSubfolders(setOf(id))) { target ->
            onSettingsChange(settings.copy(folders = settings.folders.moveFolders(setOf(id), target)))
            val name = folders.firstOrNull { it.id == target }?.name
            notice = { if (name != null) stringResource(Res.string.home_notice_moved_to, name) else stringResource(Res.string.home_notice_moved_to_main) }
            moveFolderId = null
        }
    }, confirmButton = { TextButton(onClick = { moveFolderId = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
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
    // Scrolls so the pattern and colour controls stay reachable on a phone in landscape.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.width(96.dp).aspectRatio(.75f).clip(RoundedCornerShape(10.dp)).border(2.dp, Ink, RoundedCornerShape(10.dp)).testTag("infinite-paper-preview")) { drawInfinitePaper(paper, 1.6f) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(infinitePaperPatternName(paper.pattern), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(Res.string.home_infinite_detail), Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
            }
        }
        Text(stringResource(Res.string.home_pattern), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Muted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfinitePaper.patterns.forEach { (id, _) ->
                val on = id == paper.pattern
                val label = infinitePaperPatternName(id)
                val description = if (on) stringResource(Res.string.home_selected_suffix, label) else label
                Column(Modifier.width(96.dp).clickable { onTemplate(paper.copy(pattern = id).template) }.testTag("infinite-paper-$id").semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
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
                    Text(infinitePaperColorName(paper.color) + " ▾", Modifier.padding(start = 8.dp), fontSize = 13.sp, color = Ink)
                }
                DropdownMenu(colorMenu, { colorMenu = false }, modifier = Modifier.penPressureFilter()) {
                    InfinitePaper.colors.forEach { (id, _) ->
                        DropdownMenuItem(text = { Text(infinitePaperColorName(id) + if (id == paper.color) "  ✓" else "") }, onClick = { onTemplate(paper.copy(color = id).template); colorMenu = false },
                            leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(InfinitePaper.colorArgb(id))).border(1.5.dp, Line, CircleShape)) })
                    }
                }
            }
            if (paper.pattern != "blank") Box {
                TextButton(onClick = { spacingMenu = true }, modifier = Modifier.testTag("infinite-paper-spacing")) { Text(stringResource(Res.string.home_spacing, paper.spacing) + " ▾", fontSize = 13.sp, color = Ink) }
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
            HomeIconButton(Symbol.Back, stringResource(Res.string.home_back), false, onBack, tag = "back")
            OutlinedTextField(name, onName, modifier = Modifier.weight(1f).padding(start = 8.dp, end = 12.dp).testTag("new-notebook-name"), singleLine = true, placeholder = { Text(stringResource(Res.string.home_notebook_name)) }, trailingIcon = { if (name.isNotEmpty()) HomeIconButton(Symbol.Close, stringResource(Res.string.home_clear_name), false, { onName("") }, tag = "clear-name") }, shape = RoundedCornerShape(12.dp))
            Button(onClick = onCreate, enabled = name.isNotBlank(), modifier = Modifier.height(52.dp).testTag("confirm-create-notebook"), shape = RoundedCornerShape(12.dp)) { Text(stringResource(Res.string.home_create), fontSize = 13.sp) }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Text(stringResource(Res.string.home_canvas_type), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Muted)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteTypeCard(
                    title = stringResource(Res.string.home_type_pages), detail = stringResource(Res.string.home_type_pages_detail), selected = !infiniteCanvas,
                    modifier = Modifier.weight(1f), onClick = { onInfiniteCanvas(false) },
                )
                NoteTypeCard(
                    title = stringResource(Res.string.home_type_infinite), detail = stringResource(Res.string.home_type_infinite_detail), selected = infiniteCanvas,
                    modifier = Modifier.weight(1f), onClick = { onInfiniteCanvas(true) },
                )
            }
        }
        Text(stringResource(Res.string.home_canvas_fixed), fontSize = 11.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
        if (infiniteCanvas) {
            InfinitePaperSection(infinitePattern, onInfinitePattern)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.template_page_template), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                Text(papers.firstOrNull { it.id == selected }?.let { templateName(it) } ?: stringResource(Res.string.template_blank), fontSize = 11.sp, lineHeight = 15.sp, color = Muted)
            }
            TextButton(onClick = onHub) { Text(stringResource(Res.string.template_browse), fontSize = 12.sp, lineHeight = 16.sp) }
        }
        Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NotebookTemplateCatalog.categories.forEach { tab -> FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Color.White, containerColor = Color.White, labelColor = Ink), selected = tab == category, onClick = { category = tab }, label = { Text(templateCategoryName(tab), fontSize = 12.sp, lineHeight = 16.sp) }) }
            }
            val shown = if (category == "Recent") NotebookTemplateCatalog.inCategory("Recent", (listOf(selected) + settings.downloaded).distinct()) else NotebookTemplateCatalog.inCategory(category)
            HorizontalDivider(color = Color(0xFFE8E8E8))
            LazyVerticalGrid(GridCells.Adaptive(124.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(shown, key = { it.id }) { paper ->
                    val name = templateName(paper)
                    val description = if (selected == paper.id) stringResource(Res.string.home_selected_suffix, name) else name
                    Column(Modifier.clickable { onSelected(paper.id) }.testTag("select-template-${paper.id}").semantics { contentDescription = description }) {
                    TemplatePreview(paper.id, templatePreview, Modifier.fillMaxWidth().aspectRatio(.72f).clip(RoundedCornerShape(8.dp)).border(if (selected == paper.id) 2.dp else 1.dp, if (selected == paper.id) Ink else Line, RoundedCornerShape(8.dp)))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (selected == paper.id) Text("✓", fontSize = 13.sp, color = Ink)
                    }
                } }
                if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Column { Text(stringResource(Res.string.template_no_recent), fontSize = 13.sp, lineHeight = 17.sp, color = Muted); TextButton(onClick = { category = "All" }) { Text(stringResource(Res.string.template_browse_all)) } } }
            }
        }
    }
}

@Composable
private fun TemplateHub(settings: LibrarySettings, onSettingsChange: (LibrarySettings) -> Unit, templatePreview: (@Composable (String, Modifier) -> Unit)?, onBack: () -> Unit, onUse: (String) -> Unit) {
    var category by rememberSaveable { mutableStateOf("General") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeIconButton(Symbol.Back, stringResource(Res.string.template_back_from_hub), false, onBack, tag = "back-from-template-hub")
            Text(stringResource(Res.string.template_hub_title), Modifier.weight(1f), fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            HomeIconButton(Symbol.Download, stringResource(Res.string.template_recent), category == "Recent", { category = "Recent" }, tag = "recent-templates")
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NotebookTemplateCatalog.categories.forEach { value -> TextButton(onClick = { category = value }, colors = ButtonDefaults.textButtonColors(contentColor = Ink)) {
                Text(templateCategoryName(value), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (category == value) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.then(if (category == value) Modifier.border(1.dp, Line, RoundedCornerShape(12.dp)).padding(6.dp) else Modifier.padding(6.dp)))
            } }
        }
        HorizontalDivider(color = Line)
        val shown = if (category == "Recent") NotebookTemplateCatalog.inCategory("Recent", settings.downloaded.toList()) else NotebookTemplateCatalog.inCategory(category).filter { it.id != "blank" }
        LazyVerticalGrid(GridCells.Adaptive(166.dp), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.id }) { paper -> Column(Modifier.border(1.dp, Ink, RoundedCornerShape(20.dp)).padding(10.dp)) {
                TemplatePreview(paper.id, templatePreview, Modifier.fillMaxWidth().aspectRatio(.75f).background(Color(0xFFFAFAFA)).padding(8.dp))
                Text(templateName(paper), Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(Res.string.template_ready), Modifier.padding(top = 8.dp).border(1.dp, Line, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, lineHeight = 13.sp, color = Muted)
                Spacer(Modifier.height(38.dp))
                val downloaded = paper.id in settings.downloaded
                OutlinedButton(onClick = { if (downloaded) onUse(paper.id) else onSettingsChange(settings.copy(downloaded = settings.downloaded + paper.id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("download-template-${paper.id}"), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(4.dp)) { Text(stringResource(if (downloaded) Res.string.template_use else Res.string.template_add), fontSize = 12.sp, lineHeight = 16.sp) }
            } }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(Res.string.template_no_recent_hub), Modifier.padding(16.dp), fontSize = 13.sp, lineHeight = 17.sp, color = Muted) }
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
    val headingText = templateHeading(template)
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
            "todo" -> { heading(headingText); for (i in 0..12) { val y = size.height * .16f + i * size.height * .058f; drawRect(ink, Offset(left, y), androidx.compose.ui.geometry.Size(size.width * .025f, size.width * .025f), style = Stroke(1f)); drawLine(ink, Offset(left + size.width * .08f, y + size.width * .025f), Offset(right, y + size.width * .025f), 1f) } }
            "meeting" -> { heading(headingText); drawLine(ink, Offset(left, size.height * .14f), Offset(right, size.height * .14f), 1f); drawLine(ink, Offset(left, size.height * .18f), Offset(right, size.height * .18f), 1f); ruled(size.height * .25f); drawLine(ink, Offset(size.width * .72f, size.height * .25f), Offset(size.width * .72f, size.height * .94f), 1f) }
            "daily", "weekly", "project" -> {
                heading(headingText)
                val top = size.height * .14f; val bottom = size.height * .92f
                val columns = if (template == "weekly") 5 else 2
                for (i in 0..columns) { val x = left + (right - left) * i / columns; drawLine(ink, Offset(x, top), Offset(x, bottom), 1f) }
                for (i in 0..5) { val y = top + (bottom - top) * i / 5; drawLine(ink, Offset(left, y), Offset(right, y), 1f) }
            }
            "cornell" -> { heading(headingText); ruled(size.height * .12f, size.height * .92f); drawLine(ink, Offset(size.width * .3f, size.height * .12f), Offset(size.width * .3f, size.height * .78f), 1.2f); drawLine(ink, Offset(left, size.height * .78f), Offset(right, size.height * .78f), 1.2f) }
            "lecture", "reading" -> { heading(headingText); drawLine(ink, Offset(left, size.height * .12f), Offset(right, size.height * .12f), 1f); ruled(size.height * .18f); if (template == "reading") drawLine(ink, Offset(size.width * .68f, size.height * .18f), Offset(size.width * .68f, size.height * .92f), 1f) }
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

/** [tag] gives the test tag `home-<tag>`, which stays English whatever the app language. */
@Composable
internal fun HomeIconButton(symbol: Symbol, label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    val style = LocalHomeStyle.current
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag("home-$tag").semantics { contentDescription = label }) {
        Box(Modifier.size(36.dp).background(if (selected) style.soft else Color.Transparent, RoundedCornerShape(style.item)), contentAlignment = Alignment.Center) {
            HomeSymbol(symbol, Modifier.size(22.dp), tint = if (selected) style.accent else Ink)
        }
    }
}

/** Folio's library icons: Lucide line icons. [folderFill] paints the shape yellow (favourite star). */
@Composable
internal fun HomeSymbol(symbol: Symbol, modifier: Modifier, folderFill: Boolean = false, tint: Color = Ink) =
    LucideGlyph(symbol.icon, modifier, tint, fill = if (folderFill) Color(0xFFF2C94C) else null)

/** [tag] gives the test tag `details-<tag>`. */
private data class DetailAction(val icon: Symbol, val label: String, val tag: String, val active: Boolean = false, val run: () -> Unit)

@Composable
private fun LibraryDetailsDialog(
    title: String, information: String, type: String, updated: String,
    preview: @Composable (Modifier) -> Unit, onDismiss: () -> Unit, actions: List<DetailAction>, isFolder: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FolioDialogSurface(Modifier.fillMaxWidth(.94f).widthIn(max = 760.dp).heightIn(max = 740.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.home_details_title), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) { HomeIconButton(Symbol.Close, stringResource(Res.string.home_close_details), false, onDismiss, tag = "close-details") }
                }
                HorizontalDivider(color = Line.copy(alpha = .5f))
                Text(title, Modifier.padding(horizontal = 24.dp, vertical = 18.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(100.dp).aspectRatio(.72f).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(8.dp)) { preview(Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(stringResource(Res.string.home_details_information, information), fontSize = 13.sp)
                        Text(stringResource(Res.string.home_details_type, type), fontSize = 13.sp)
                        Text(stringResource(if (isFolder) Res.string.home_details_folder_updated else Res.string.home_details_updated, updated), fontSize = 12.sp, color = Muted)
                    }
                }
                HorizontalDivider(Modifier.padding(top = 20.dp), color = Line.copy(alpha = .5f))
                BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                    val columns = minOf(actions.size.coerceAtLeast(1), if (maxWidth < 380.dp) 3 else 4)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { action ->
                                    Column(Modifier.weight(1f).testTag("details-${action.tag}").clickable(onClick = action.run).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(58.dp).background(if (action.active) Color(0xFFFFF3CC) else Color.Transparent, androidx.compose.foundation.shape.CircleShape)
                                                .border(if (action.active) 2.dp else 1.dp, if (action.active) Color(0xFFF2C94C) else Line, androidx.compose.foundation.shape.CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) { HomeSymbol(action.icon, Modifier.size(26.dp), folderFill = action.active) }
                                        Text(action.label, Modifier.padding(top = 10.dp), fontSize = 12.sp, fontWeight = if (action.active) FontWeight.SemiBold else null, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
