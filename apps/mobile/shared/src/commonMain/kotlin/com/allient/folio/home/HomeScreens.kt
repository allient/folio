package com.allient.folio.home

import knotes.shared.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable
import knotes.shared.generated.resources.prefs_group_appearance
import knotes.shared.generated.resources.prefs_group_library
import knotes.shared.generated.resources.prefs_group_general
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.selection.toggleable
import com.composables.icons.lucide.Bug
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Github
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Shield

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeIconButton(Symbol.Back, stringResource(Res.string.home_back_to_library), false, onBack, tag = "back-to-library")
        Text(title, Modifier.weight(1f).padding(start = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = HomeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
    HorizontalDivider(color = HomeHairline)
}

@Composable
internal fun retentionText(days: Int) =
    if (days > 0) pluralStringResource(Res.plurals.home_trash_retention, days, days) else stringResource(Res.string.home_trash_retention_off)

@Composable
internal fun TrashScreen(
    items: List<TrashItem>, retentionDays: Int, now: Long, formatDate: (Long) -> String,
    preview: @Composable (TrashItem, Modifier) -> Unit, onBack: () -> Unit,
    onRestore: (TrashItem) -> Unit, onDelete: (TrashItem) -> Unit, onEmpty: () -> Unit,
) {
    val style = LocalHomeStyle.current
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(HomeBackground).testTag("trash-screen")) {
        ScreenHeader(stringResource(Res.string.home_trash_title), onBack) {
            TextButton(onClick = { confirmEmpty = true }, enabled = items.isNotEmpty(), modifier = Modifier.testTag("trash-empty")) { Text(stringResource(Res.string.home_trash_empty_action), color = if (items.isNotEmpty()) HomeDanger else HomeMuted) }
        }
        Text(retentionText(retentionDays), Modifier.padding(horizontal = 24.dp, vertical = 14.dp), fontSize = 13.sp, color = HomeMuted)
        if (items.isEmpty()) Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HomeSymbol(Symbol.Trash, Modifier.size(44.dp), tint = HomeLine)
            Text(stringResource(Res.string.home_trash_empty_title), Modifier.padding(top = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = HomeInk)
            Text(stringResource(Res.string.home_trash_empty_detail), Modifier.padding(top = 6.dp), fontSize = 13.sp, color = HomeMuted)
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
          // On phones the two actions move under the item so the title is not squeezed.
          val narrow = maxWidth < 560.dp
          LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { "${it.kind}:${it.id}" }) { item ->
                val left = TrashRules.daysLeft(item.deletedAt, now, retentionDays)
                val actions: @Composable () -> Unit = {
                    TextButton(onClick = { onRestore(item) }, modifier = Modifier.testTag("trash-restore-${item.id}")) { Text(stringResource(Res.string.home_restore), color = style.accent) }
                    TextButton(onClick = { confirmDelete = item }, modifier = Modifier.testTag("trash-delete-${item.id}")) { Text(stringResource(Res.string.home_delete_permanently), color = HomeDanger, maxLines = 2, fontSize = 13.sp) }
                }
                Column(Modifier.fillMaxWidth().widthIn(max = 900.dp).clip(RoundedCornerShape(style.item)).background(Color(0xFFF8F8F8)).padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("trash-item-${item.id}")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(44.dp).aspectRatio(.75f).clip(RoundedCornerShape(style.item / 2)).border(1.dp, HomeLine, RoundedCornerShape(style.item / 2)).background(Color.White),
                        contentAlignment = Alignment.Center) { preview(item, Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(Res.string.home_trash_deleted_on, item.detail, formatDate(item.deletedAt)), Modifier.padding(top = 2.dp), fontSize = 11.sp, color = HomeMuted, maxLines = 2)
                        Text(when (left) { null -> stringResource(Res.string.home_trash_kept); 0 -> stringResource(Res.string.home_trash_next_start); else -> pluralStringResource(Res.plurals.home_trash_days_left, left, left) },
                            Modifier.padding(top = 2.dp), fontSize = 11.sp, color = if (left != null && left <= 3) HomeDanger else HomeMuted)
                    }
                    if (!narrow) actions()
                }
                if (narrow) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) { actions() }
                }
            }
          }
        }
    }
    confirmDelete?.let { item -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text(stringResource(Res.string.home_delete_permanently_title)) },
        text = { Text(stringResource(when (item.kind) {
            "folder" -> Res.string.home_delete_folder_forever
            "book" -> Res.string.home_delete_book_forever
            else -> Res.string.home_delete_note_forever
        }, item.title)) },
        confirmButton = { TextButton(onClick = { onDelete(item); confirmDelete = null }, modifier = Modifier.testTag("trash-confirm-delete")) { Text(stringResource(Res.string.home_delete_permanently), color = HomeDanger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(Res.string.home_cancel)) } }) }
    if (confirmEmpty) AlertDialog(onDismissRequest = { confirmEmpty = false }, title = { Text(stringResource(Res.string.home_empty_trash_title)) },
        text = { Text(pluralStringResource(Res.plurals.home_empty_trash_text, items.size, items.size)) },
        confirmButton = { TextButton(onClick = { onEmpty(); confirmEmpty = false }, modifier = Modifier.testTag("trash-confirm-empty")) { Text(stringResource(Res.string.home_trash_empty_action), color = HomeDanger) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(Res.string.home_cancel)) } })
}

/** A titled group of settings rows on one white panel, rows separated by hairlines. */
@Composable
internal fun PreferenceGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val style = LocalHomeStyle.current
    Text(title, Modifier.padding(start = 4.dp, top = 28.dp, bottom = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HomeMuted)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(style.card)).background(Color.White).border(1.dp, HomeHairline, RoundedCornerShape(style.card)), content = content)
}

@Composable
internal fun PreferenceDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = HomeHairline)

/**
 * One setting: its name and a short explanation on the left, its control on the right. [below] is shown under the text
 * across the full width (the accent swatches).
 */
@Composable
internal fun PreferenceRow(title: String, detail: String? = null, modifier: Modifier = Modifier, below: (@Composable () -> Unit)? = null,
                          trailing: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = HomeInk)
                if (detail != null) Text(detail, Modifier.padding(top = 3.dp), fontSize = 12.sp, lineHeight = 17.sp, color = HomeMuted)
            }
            trailing()
        }
        if (below != null) Box(Modifier.padding(top = 14.dp)) { below() }
    }
}

/**
 * A select: shows the current choice with a chevron and opens a menu of the options, the current one checked.
 * The button is tagged [tag]; each option keeps the tag the old radio rows had (`"$optionTag-<id>"`).
 */
@Composable
private fun <T> PreferenceSelect(options: List<Pair<T, String>>, selected: T, tag: String, optionTag: String, onSelect: (T) -> Unit) {
    val style = LocalHomeStyle.current
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Box {
        Row(Modifier.widthIn(min = 120.dp, max = 240.dp).heightIn(min = 40.dp).clip(RoundedCornerShape(style.item))
            .border(1.dp, HomeLine, RoundedCornerShape(style.item)).background(HomeBackground)
            .clickable(role = Role.DropdownList, onClickLabel = current) { open = true }.padding(start = 14.dp, end = 10.dp).testTag(tag),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(current, Modifier.weight(1f, fill = false), fontSize = 14.sp, color = HomeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HomeSymbol(Symbol.ChevronDown, Modifier.size(16.dp))
        }
        DropdownMenu(open, { open = false }, containerColor = Color.White) {
            options.forEach { (id, label) ->
                val chosen = id == selected
                DropdownMenuItem(text = { Text(label, fontSize = 14.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, color = HomeInk) },
                    onClick = { open = false; if (!chosen) onSelect(id) },
                    trailingIcon = { if (chosen) HomeSymbol(Symbol.Check, Modifier.size(16.dp), tint = style.accent) else Spacer(Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("$optionTag-$id").semantics { this.selected = chosen })
            }
        }
    }
}

@Composable
internal fun PreferenceSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val style = LocalHomeStyle.current
    Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = style.accent, checkedThumbColor = Color.White,
        uncheckedTrackColor = HomeHairline, uncheckedBorderColor = HomeLine, uncheckedThumbColor = Color.White))
}

/** The accent colours as round swatches; the chosen one has a ring in its own colour. */
@Composable
private fun AccentSwatches(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AccentPresets.forEach { preset ->
            val chosen = preset.id == selected
            val name = accentName(preset)
            Box(Modifier.size(44.dp).clip(CircleShape).border(if (chosen) 2.dp else 0.dp, if (chosen) preset.primary else Color.Transparent, CircleShape)
                .selectable(chosen, role = Role.RadioButton) { onSelect(preset.id) }.padding(5.dp).testTag("accent-${preset.id}")
                .semantics { contentDescription = name }, contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(preset.primary), contentAlignment = Alignment.Center) {
                    if (chosen) HomeSymbol(Symbol.Check, Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun PreferencesScreen(value: HomePreferences, onChange: (HomePreferences) -> Unit, onBack: () -> Unit, onOpenBackup: () -> Unit, onOpenPen: (() -> Unit)? = null) {
    val style = LocalHomeStyle.current
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(HomeBackground).testTag("preferences-screen")) {
        ScreenHeader(stringResource(Res.string.prefs_title), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                PreferenceGroup(stringResource(Res.string.prefs_group_general)) {
                    val languages = listOf("" to stringResource(Res.string.pref_language_system)) + com.allient.folio.AppLanguages.supported.map { it.tag to it.nativeName }
                    PreferenceRow(stringResource(Res.string.pref_language_title), stringResource(Res.string.pref_language_detail)) {
                        PreferenceSelect(languages, value.language, "pref-language", "language") { onChange(value.copy(language = it)) }
                    }
                    PreferenceDivider()
                    PreferenceRow(stringResource(Res.string.prefs_fullscreen_title), stringResource(Res.string.prefs_fullscreen_detail),
                        Modifier.toggleable(value.startFullscreen, role = Role.Switch) { onChange(value.copy(startFullscreen = it)) }.testTag("pref-start-fullscreen")) {
                        PreferenceSwitch(value.startFullscreen) { onChange(value.copy(startFullscreen = it)) }
                    }
                }
                PreferenceGroup(stringResource(Res.string.prefs_group_library)) {
                    PreferenceRow(stringResource(Res.string.prefs_view_title), stringResource(Res.string.prefs_view_detail)) {
                        PreferenceSelect(listOf("grid" to stringResource(Res.string.home_view_grid), "list" to stringResource(Res.string.home_view_list)),
                            value.viewMode, "pref-view", "view") { onChange(value.copy(viewMode = it)) }
                    }
                    PreferenceDivider()
                    PreferenceRow(stringResource(Res.string.prefs_sort_title), stringResource(Res.string.prefs_sort_detail)) {
                        PreferenceSelect(HomeSort.all.map { (id, _) -> id to sortName(id) }, value.sort, "pref-sort", "sort") { onChange(value.copy(sort = it)) }
                    }
                    PreferenceDivider()
                    PreferenceRow(stringResource(Res.string.prefs_trash_title), stringResource(Res.string.prefs_trash_detail)) {
                        PreferenceSelect(TrashRules.retentionOptions.map { (days, _) -> days to retentionName(days) }, value.trashRetentionDays, "pref-retention", "retention") {
                            onChange(value.copy(trashRetentionDays = it))
                        }
                    }
                }
                PreferenceGroup(stringResource(Res.string.prefs_group_appearance)) {
                    PreferenceRow(stringResource(Res.string.prefs_accent_title), stringResource(Res.string.prefs_accent_detail),
                        below = { AccentSwatches(value.accent) { onChange(value.copy(accent = it)) } })
                    PreferenceDivider()
                    PreferenceRow(stringResource(Res.string.prefs_corners_title), stringResource(Res.string.prefs_corners_detail)) {
                        PreferenceSelect(CornerStyles.map { (id, _) -> id to cornerStyleName(id) }, value.corners, "pref-corners", "corners") { onChange(value.copy(corners = it)) }
                    }
                }
                if (onOpenPen != null) PreferenceGroup(stringResource(Res.string.prefs_pen_title)) {
                    PreferenceRow(stringResource(Res.string.prefs_pen_pressure_title), stringResource(Res.string.prefs_pen_pressure_detail),
                        Modifier.clickable(onClick = onOpenPen).testTag("pref-pen-pressure")) { HomeSymbol(Symbol.ChevronRight, Modifier.size(16.dp)) }
                }
                PreferenceGroup(stringResource(Res.string.prefs_backup_title)) {
                    PreferenceRow(stringResource(Res.string.backup_settings_title), stringResource(Res.string.prefs_backup_detail),
                        Modifier.clickable(onClick = onOpenBackup).testTag("pref-backup-settings")) { HomeSymbol(Symbol.ChevronRight, Modifier.size(16.dp)) }
                }
                PreferenceGroup(stringResource(Res.string.prefs_privacy_title)) {
                    PreferenceRow(stringResource(Res.string.prefs_usage_title), stringResource(Res.string.prefs_usage_detail),
                        Modifier.toggleable(value.shareUsageStats, role = Role.Switch) { onChange(value.copy(shareUsageStats = it)) }.testTag("pref-usage-stats")) {
                        PreferenceSwitch(value.shareUsageStats) { onChange(value.copy(shareUsageStats = it)) }
                    }
                }
                TextButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 24.dp).heightIn(min = 48.dp).testTag("pref-reset"),
                    shape = RoundedCornerShape(style.item)) { Text(stringResource(Res.string.prefs_reset), color = HomeDanger) }
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(stringResource(Res.string.prefs_reset_title)) },
        text = { Text(stringResource(Res.string.prefs_reset_text)) },
        confirmButton = { TextButton(onClick = { onChange(HomePreferences(shareUsageStats = value.shareUsageStats, language = value.language)); confirmReset = false }, modifier = Modifier.testTag("pref-confirm-reset")) { Text(stringResource(Res.string.prefs_reset_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(Res.string.home_cancel)) } })
}

private data class AboutLink(val tag: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val subtitle: String, val url: String)

@Composable
internal fun AboutScreen(version: String, onBack: () -> Unit) {
    val style = LocalHomeStyle.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val inAppBrowser = com.allient.folio.LocalInAppBrowser.current
    fun openAllient() { inAppBrowser?.invoke(com.allient.folio.FolioLinks.ALLIENT) ?: uriHandler.openUri(com.allient.folio.FolioLinks.ALLIENT) }
    val links = listOf(
        AboutLink("website", Lucide.Globe, stringResource(Res.string.about_link_docs), "folio.allient.io", com.allient.folio.FolioLinks.SITE),
        AboutLink("github", Lucide.Github, stringResource(Res.string.about_link_star), "github.com/allient/folio", com.allient.folio.FolioLinks.GITHUB),
        AboutLink("issues", Lucide.Bug, stringResource(Res.string.about_link_issues), stringResource(Res.string.about_link_issues_detail), com.allient.folio.FolioLinks.ISSUES),
        AboutLink("privacy", Lucide.Shield, stringResource(Res.string.about_link_privacy), "folio.allient.io/privacy", com.allient.folio.FolioLinks.PRIVACY),
        AboutLink("license", Lucide.Scale, stringResource(Res.string.about_link_license), "Apache License 2.0", com.allient.folio.FolioLinks.LICENSE),
    )
    val libraries = listOf("Jetpack Ink" to stringResource(Res.string.about_lib_ink), "PDFium" to stringResource(Res.string.about_lib_pdfium), "PdfBox-Android" to stringResource(Res.string.about_lib_pdfbox),
        "Readium" to stringResource(Res.string.about_lib_readium), "ML Kit Text Recognition" to stringResource(Res.string.about_lib_mlkit), "Zstandard (zstd-jni)" to stringResource(Res.string.about_lib_zstd),
        "Lucide" to stringResource(Res.string.about_lib_lucide), "Geist" to stringResource(Res.string.about_lib_geist))
    @Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Text(title, Modifier.padding(top = 28.dp, bottom = 10.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HomeInk)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(style.card)).background(Color.White).border(1.dp, HomeHairline, RoundedCornerShape(style.card)), content = content)
    }
    Column(Modifier.fillMaxSize().background(HomeBackground).testTag("about-screen")) {
        ScreenHeader(stringResource(Res.string.about_title), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    com.allient.folio.FolioIcon(Modifier.size(84.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Folio", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = HomeInk, letterSpacing = (-.5).sp)
                        Text(stringResource(Res.string.about_tagline), Modifier.padding(top = 2.dp), fontSize = 15.sp, color = com.allient.folio.FolioBrand.Sage)
                        Text(stringResource(Res.string.about_version, version), Modifier.padding(top = 10.dp).clip(RoundedCornerShape(style.pill)).background(style.soft).padding(horizontal = 12.dp, vertical = 4.dp).testTag("about-version"),
                            fontSize = 13.sp, color = HomeInk)
                    }
                    // Small "Made at Allient" badge, like the one in the README; opens allient.io inside the app.
                    val openAllientLabel = stringResource(Res.string.about_open_allient)
                    val madeAtAllient = stringResource(Res.string.about_made_at_allient)
                    Row(Modifier.align(Alignment.Top).padding(top = 6.dp).clip(RoundedCornerShape(4.dp)).height(IntrinsicSize.Min)
                        .clickable(role = Role.Button, onClickLabel = openAllientLabel) { openAllient() }
                        .semantics(mergeDescendants = true) { contentDescription = madeAtAllient }.testTag("about-maintainer"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.about_made_at), Modifier.fillMaxHeight().background(Color(0xFFE4E7EB)).padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp, color = Color(0xFF4B5563))
                        Box(Modifier.fillMaxHeight().background(Color(0xFFF3F4F6)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            com.allient.folio.AllientLogo(Modifier.height(13.dp))
                        }
                    }
                }
                Column(Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(style.card)).background(style.soft.copy(alpha = .5f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.about_privacy_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HomeInk)
                    Text(stringResource(Res.string.about_privacy_body),
                        fontSize = 14.sp, lineHeight = 21.sp, color = HomeInk)
                    Text(stringResource(Res.string.about_privacy_internet),
                        fontSize = 13.sp, lineHeight = 19.sp, color = HomeMuted)
                }
                Section(stringResource(Res.string.about_links)) {
                    links.forEachIndexed { index, link ->
                        val openLabel = stringResource(Res.string.home_open_item, link.title)
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClickLabel = openLabel) { uriHandler.openUri(link.url) }
                            .padding(horizontal = 16.dp, vertical = 8.dp).testTag("about-link-${link.tag}").semantics { contentDescription = "${link.title}, ${link.subtitle}" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(style.item)).background(style.soft), contentAlignment = Alignment.Center) {
                                com.allient.folio.LucideGlyph(link.icon, Modifier.size(20.dp), style.accent)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(link.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = HomeInk)
                                Text(link.subtitle, fontSize = 13.sp, color = HomeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            com.allient.folio.LucideGlyph(Lucide.ExternalLink, Modifier.size(18.dp), HomeMuted)
                        }
                        if (index < links.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = HomeHairline)
                    }
                }
                Section(stringResource(Res.string.about_libraries)) {
                    libraries.forEachIndexed { index, (name, use) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HomeInk)
                            Text(use, Modifier.weight(1.3f), fontSize = 13.sp, color = HomeMuted, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        if (index < libraries.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = HomeHairline)
                    }
                }
                Text(stringResource(Res.string.about_footer), Modifier.padding(top = 24.dp, bottom = 8.dp).clickable(role = Role.Button, onClickLabel = stringResource(Res.string.about_open_allient)) { openAllient() }.testTag("about-allient").fillMaxWidth(),
                    fontSize = 13.sp, color = HomeMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
