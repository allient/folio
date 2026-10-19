package com.allient.folio.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Library navigation. [current] is "home", "recent", "favorites", "trash", "preferences", "about" or "folder:<id>". */
@Composable
internal fun HomeSidebar(folders: List<NoteFolder>, current: String, trashCount: Int, modifier: Modifier, onSelect: (String) -> Unit, onCreateFolder: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(modifier.background(Color(0xFFFAFAFA)).padding(horizontal = 12.dp).testTag("home-sidebar")) {
        Text("Folio", Modifier.padding(start = 12.dp, top = 22.dp, bottom = 18.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HomeInk)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SidebarItem(Symbol.Notes, "Notes", current == "home", { onSelect("home") })
            SidebarItem(Symbol.Folder, "Folders", false, { expanded = !expanded }) {
                Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onCreateFolder).semantics { contentDescription = "Create folder" }.testTag("sidebar-create-folder"),
                    contentAlignment = Alignment.Center) { HomeSymbol(Symbol.Plus, Modifier.size(15.dp)) }
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { expanded = !expanded }.semantics { contentDescription = if (expanded) "Collapse folders" else "Expand folders" },
                    contentAlignment = Alignment.Center) { HomeSymbol(Symbol.ChevronDown, Modifier.size(15.dp).rotate(if (expanded) 0f else -90f)) }
            }
            if (expanded) {
                folders.forEach { folder ->
                    SidebarItem(null, folder.name, current == "folder:${folder.id}", { onSelect("folder:${folder.id}") }, indent = true, folder = folder)
                }
                if (folders.isEmpty()) Text("No folders yet", Modifier.padding(start = 52.dp, top = 4.dp, bottom = 8.dp), fontSize = 12.sp, color = HomeMuted)
            }
            SidebarItem(Symbol.Clock, "Recent", current == "recent", { onSelect("recent") })
            SidebarItem(Symbol.Star, "Favorites", current == "favorites", { onSelect("favorites") })
            SidebarItem(Symbol.Trash, "Trash bin", current == "trash", { onSelect("trash") }) {
                if (trashCount > 0) Text("$trashCount", Modifier.padding(end = 8.dp), fontSize = 12.sp, color = HomeMuted)
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(HomeHairline))
        SidebarItem(Symbol.Gear, "Preferences", current == "preferences", { onSelect("preferences") })
        SidebarItem(Symbol.Info, "About", current == "about", { onSelect("about") })
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SidebarItem(symbol: Symbol?, label: String, selected: Boolean, onClick: () -> Unit, indent: Boolean = false, folder: NoteFolder? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val style = LocalHomeStyle.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(style.item)).background(if (selected) style.soft else Color.Transparent)
        .clickable(onClick = onClick).padding(start = if (indent) 40.dp else 12.dp, end = 4.dp)
        .testTag("sidebar-${label.lowercase().replace(' ', '-')}").semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically) {
        when {
            folder != null -> FolderArtwork(folder.color, folder.style, Modifier.size(26.dp, 20.dp))
            symbol != null -> HomeSymbol(symbol, Modifier.size(21.dp), tint = if (selected) style.accent else HomeInk)
        }
        Text(label, Modifier.weight(1f).padding(start = 14.dp), fontSize = if (indent) 14.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) style.accent else HomeInk)
        trailing()
    }
}
