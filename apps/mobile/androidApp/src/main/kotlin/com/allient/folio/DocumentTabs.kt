package com.allient.folio

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DocumentTabs(
    tabs: List<DocumentTab>, currentId: String?, listTitle: String, tagPrefix: String,
    listButtonTag: String = "$tagPrefix-list", closeTagPrefix: String = "$tagPrefix-close-tab",
    onSelect: (String) -> Unit, onClose: (String) -> Unit, onHideTabs: () -> Unit, onCloseOthers: () -> Unit,
) {
    if (tabs.isEmpty()) return
    var showList by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(currentId, tabs.map { it.id }) {
        val index = tabs.indexOfFirst { it.id == currentId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    // Browser-style strip: flat tabs with rounded top corners; the active tab takes the surface colour
    // so it joins the toolbar below. Close buttons keep Compose's expanded minimum touch target.
    val colors = MaterialTheme.colorScheme
    val tabShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
    // Unfocused tabs: a clear grey between the bar and the white active tab, in any theme.
    val inactiveTab = androidx.compose.ui.graphics.lerp(colors.surface, colors.onSurface, 0.16f)
    Row(Modifier.fillMaxWidth().height(28.dp).background(colors.surfaceVariant).padding(start = 6.dp, end = 6.dp, top = 3.dp), verticalAlignment = Alignment.Bottom) {
        androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f).fillMaxHeight().testTag("$tagPrefix-tabs"), state = listState,
            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
            items(tabs.size, key = { tabs[it].id }) { index ->
                val book = tabs[index]
                val active = book.id == currentId
                Row(Modifier.width(200.dp).fillMaxHeight().clip(tabShape).background(if (active) colors.surface else inactiveTab),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab) { onSelect(book.id) }
                        .testTag("$tagPrefix-tab-${book.id}").semantics { selected = active; contentDescription = "Switch to ${book.title}" }.padding(start = 14.dp), contentAlignment = Alignment.CenterStart) {
                        Text(book.title, fontSize = 11.sp, color = if (active) colors.onSurface else colors.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onClose(book.id) }, modifier = Modifier.size(24.dp).testTag("$closeTagPrefix-${book.id}").semantics { contentDescription = "Close ${book.title}" }) {
                        InkSymbol(InkIcon.Close, Modifier.size(10.dp), if (active) colors.onSurface else colors.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.width(3.dp))
        Box(Modifier.fillMaxHeight()) {
        Row(Modifier.fillMaxHeight().clip(tabShape).background(inactiveTab).clickable { showList = true }
            .testTag(listButtonTag).semantics { contentDescription = "$listTitle list, ${tabs.size} tabs" }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${tabs.size}", fontSize = 12.sp, color = colors.onSurfaceVariant)
            Text("\u25BE", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        TabListMenu(showList, tabs, currentId, listTitle, onDismiss = { showList = false },
            onSelect = { showList = false; onSelect(it) }, onClose = onClose,
            onHideTabs = { showList = false; onHideTabs() }, onCloseOthers = { showList = false; onCloseOthers() })
        }
    }
}

/** Drop-down list of open tabs: hide the strip, close other tabs, and per-tab actions. */
@Composable
private fun TabListMenu(
    expanded: Boolean, tabs: List<DocumentTab>, currentId: String?, listTitle: String, onDismiss: () -> Unit,
    onSelect: (String) -> Unit, onClose: (String) -> Unit, onHideTabs: () -> Unit, onCloseOthers: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val card = RoundedCornerShape(10.dp)
    DropdownMenu(expanded, onDismiss, Modifier.width(320.dp).testTag("document-tab-menu"), shape = RoundedCornerShape(14.dp),
        containerColor = colors.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(card).background(colors.surface).clickable(onClick = onHideTabs)
                .testTag("document-tab-hide").padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hide tab bar", Modifier.weight(1f), fontSize = 14.sp, color = colors.onSurface)
                Text("\u25B4", fontSize = 14.sp, color = colors.onSurface)
            }
            if (tabs.size > 1) Text("Close other tabs", Modifier.align(Alignment.End).clip(RoundedCornerShape(6.dp)).clickable(onClick = onCloseOthers)
                .testTag("document-tab-close-others").padding(horizontal = 6.dp, vertical = 10.dp), fontSize = 13.sp, color = colors.error)
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).semantics { contentDescription = "$listTitle (${tabs.size})" },
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEach { tab ->
                    val active = tab.id == currentId
                    var actions by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(card).background(colors.surface)
                        .then(if (active) Modifier.border(2.dp, colors.primary, card) else Modifier)
                        .clickable(role = Role.Tab) { onSelect(tab.id) }.semantics { selected = active }.padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(tab.title, Modifier.weight(1f), fontSize = 14.sp, color = colors.onSurface,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box {
                            IconButton(onClick = { actions = true }, modifier = Modifier.semantics { contentDescription = "Options for ${tab.title}" }) {
                                InkSymbol(InkIcon.More, Modifier.size(18.dp), colors.onSurface)
                            }
                            DropdownMenu(actions, { actions = false }) {
                                DropdownMenuItem(text = { Text("Close tab") }, onClick = { actions = false; onClose(tab.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
