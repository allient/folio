package com.allient.folio.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import knotes.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** A live board (Excalidraw scene) as the library lists it; the scene itself is stored by the host. */
data class HomeBoard(val id: String, val title: String, val updatedAt: Long)

/** The Live tab: live boards as library cards, with rename and delete. */
@Composable
internal fun BoardsGrid(
    boards: List<HomeBoard>, grid: Boolean, columns: Int, pad: Dp, gap: Dp,
    preview: @Composable (String, Modifier) -> Unit, formatDate: (Long) -> String,
    onOpen: (String) -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
) {
    var options by remember { mutableStateOf<HomeBoard?>(null) }
    var renaming by remember { mutableStateOf<HomeBoard?>(null) }
    var deleting by remember { mutableStateOf<HomeBoard?>(null) }
    LazyVerticalGrid(GridCells.Fixed(columns), Modifier.fillMaxSize().testTag("board-grid"),
        contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(if (grid) 16.dp else 8.dp)) {
        if (boards.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.boards_empty_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = HomeInk)
                Text(stringResource(Res.string.boards_empty_detail),
                    Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp), fontSize = 13.sp, color = HomeMuted)
            }
        } else {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("boards", stringResource(Res.string.home_segment_live), boards.size, foldable = false, open = true, onToggle = {})
            }
            items(boards, key = { "board:${it.id}" }) { board ->
                LibraryTile(grid, board.title, formatDate(board.updatedAt), stringResource(Res.string.home_options_for, board.title), { options = board },
                    Modifier.clickable { onOpen(board.id) }.testTag("board-${board.id}"), selected = null) {
                    preview(board.id, Modifier.fillMaxSize())
                    TileBadge(stringResource(Res.string.boards_badge), Modifier.align(Alignment.TopStart), filled = true)
                }
            }
        }
    }
    options?.let { board ->
        AlertDialog(onDismissRequest = { options = null },
            title = { Text(board.title, color = HomeInk) },
            text = {
                Column {
                    listOf(Res.string.home_open to { onOpen(board.id) }, Res.string.home_rename to { renaming = board }, Res.string.home_delete to { deleting = board }).forEach { (label, action) ->
                        Text(stringResource(label), Modifier.fillMaxWidth().clickable { options = null; action() }.padding(vertical = 14.dp),
                            fontSize = 15.sp, color = if (label == Res.string.home_delete) Color(0xFFB3261E) else HomeInk)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { options = null }) { Text(stringResource(Res.string.home_close), color = HomeInk) } })
    }
    renaming?.let { board -> BoardNameDialog(stringResource(Res.string.boards_rename_title), board.title, stringResource(Res.string.home_rename), onDismiss = { renaming = null }) { renaming = null; onRename(board.id, it) } }
    deleting?.let { board ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.boards_delete_title, board.title), color = HomeInk) },
            text = { Text(stringResource(Res.string.boards_delete_text), color = HomeMuted) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(board.id) }) { Text(stringResource(Res.string.home_delete), color = Color(0xFFB3261E)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.home_cancel), color = HomeInk) } })
    }
}

/** Name field for a new or renamed board. */
@Composable
internal fun BoardNameDialog(title: String, initial: String, action: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val style = LocalHomeStyle.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title, color = HomeInk) },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().testTag("board-name"), singleLine = true, placeholder = { Text(stringResource(Res.string.boards_untitled)) }) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text(action, color = style.accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_cancel), color = HomeInk) } })
}
