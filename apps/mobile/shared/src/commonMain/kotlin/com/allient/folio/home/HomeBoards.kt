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
                Text("No boards yet", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = HomeInk)
                Text("Tap + to start a live board. Draw with your pen and share a link in your call.",
                    Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp), fontSize = 13.sp, color = HomeMuted)
            }
        } else {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader("boards", "Live", boards.size, foldable = false, open = true, onToggle = {})
            }
            items(boards, key = { "board:${it.id}" }) { board ->
                LibraryTile(grid, board.title, formatDate(board.updatedAt), "Options for ${board.title}", { options = board },
                    Modifier.clickable { onOpen(board.id) }.testTag("board-${board.id}"), selected = null) {
                    preview(board.id, Modifier.fillMaxSize())
                    TileBadge("LIVE", Modifier.align(Alignment.TopStart), filled = true)
                }
            }
        }
    }
    options?.let { board ->
        AlertDialog(onDismissRequest = { options = null },
            title = { Text(board.title, color = HomeInk) },
            text = {
                Column {
                    listOf("Open" to { onOpen(board.id) }, "Rename" to { renaming = board }, "Delete" to { deleting = board }).forEach { (label, action) ->
                        Text(label, Modifier.fillMaxWidth().clickable { options = null; action() }.padding(vertical = 14.dp),
                            fontSize = 15.sp, color = if (label == "Delete") Color(0xFFB3261E) else HomeInk)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { options = null }) { Text("Close", color = HomeInk) } })
    }
    renaming?.let { board -> BoardNameDialog("Rename board", board.title, "Rename", onDismiss = { renaming = null }) { renaming = null; onRename(board.id, it) } }
    deleting?.let { board ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Delete “${board.title}”?", color = HomeInk) },
            text = { Text("The board and its drawing are removed from this device.", color = HomeMuted) },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(board.id) }) { Text("Delete", color = Color(0xFFB3261E)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = HomeInk) } })
    }
}

/** Name field for a new or renamed board. */
@Composable
internal fun BoardNameDialog(title: String, initial: String, action: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val style = LocalHomeStyle.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title, color = HomeInk) },
        text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().testTag("board-name"), singleLine = true, placeholder = { Text("Untitled board") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text(action, color = style.accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = HomeInk) } })
}
