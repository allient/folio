package com.allient.folio.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import knotes.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** One kept backup file in the backup folder. */
data class BackupVersionUi(val id: String, val date: String, val size: String)

data class BackupSettingsUi(
    val enabled: Boolean = false,
    /** The backup folder's name in the app language; null when no folder was chosen. */
    val folder: String? = null,
    /** For example "Last backup 12:41 · 3 versions · 84 MB". */
    val status: String = "",
    val lastFile: String? = null,
    val running: Boolean = false,
    val choosingFolder: Boolean = false,
    val error: String? = null,
    val savingNotes: Boolean = false,
    val versions: List<BackupVersionUi> = emptyList(),
) {
    /** The folder card shows once a folder was chosen, or while smart backups are on (to choose one). */
    val showsFolder: Boolean get() = enabled || folder != null
}

/** Status dot colours: backing up normally, needs attention, off. */
private val BackupOk = Color(0xFF2F7A68)
private val BackupWarn = Color(0xFFC9803A)
private val BackupOff = Color(0xFFB5B8B4)

@Composable
fun BackupSettingsScreen(state: BackupSettingsUi, onBack: () -> Unit, onEnabled: (Boolean) -> Unit,
                         onChooseFolder: () -> Unit, onBackUpNow: () -> Unit, onRestore: () -> Unit,
                         onExportBackup: () -> Unit = {}, onRestoreVersion: (String) -> Unit = {}) {
    val style = LocalHomeStyle.current
    val busy = state.running || state.choosingFolder
    var confirmRestore by remember { mutableStateOf<BackupVersionUi?>(null) }
    confirmRestore?.let { version ->
        AlertDialog(onDismissRequest = { confirmRestore = null }, title = { Text(stringResource(Res.string.backup_restore_version_title)) },
            text = { Text(stringResource(Res.string.backup_restore_version_text, version.date)) },
            confirmButton = { TextButton(onClick = { confirmRestore = null; onRestoreVersion(version.id) }, modifier = Modifier.testTag("backup-restore-confirm")) { Text(stringResource(Res.string.home_restore)) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(Res.string.home_cancel)) } })
    }
    Column(Modifier.fillMaxSize().testTag("backup-settings-screen").background(HomeBackground)) {
        // Same header as the library's Trash, Preferences and About screens.
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.testTag("backup-settings-back")) { HomeIconButton(Symbol.Back, stringResource(Res.string.home_back), false, onBack, tag = "back") }
            Text(stringResource(Res.string.backup_settings_title), Modifier.padding(start = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = HomeInk)
        }
        HorizontalDivider(color = HomeHairline)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                BackupStatusPanel(state, busy, onBackUpNow)

                PreferenceGroup(stringResource(Res.string.backup_smart_title)) {
                    val smartLabel = stringResource(Res.string.backup_smart_title)
                    PreferenceRow(stringResource(if (state.enabled) Res.string.backup_on_status else Res.string.backup_choose_to_start),
                        stringResource(if (state.enabled) Res.string.backup_on_detail else Res.string.backup_off_detail),
                        Modifier.toggleable(state.enabled, enabled = !state.choosingFolder && !state.savingNotes, role = Role.Switch) { onEnabled(it) }
                            .testTag("backup-enabled").semantics { contentDescription = smartLabel }) {
                        PreferenceSwitch(state.enabled) { onEnabled(it) }
                    }
                    if (state.showsFolder) {
                        PreferenceDivider()
                        PreferenceRow(stringResource(Res.string.backup_folder)) {
                            OutlinedButton(onClick = onChooseFolder, enabled = !busy && !state.savingNotes, shape = RoundedCornerShape(style.item),
                                border = BorderStroke(1.dp, HomeLine), modifier = Modifier.heightIn(min = 40.dp).testTag("backup-choose-folder")) {
                                Text(stringResource(Res.string.backup_change_folder), color = HomeInk, fontSize = 14.sp)
                            }
                        }
                        // The folder name sits under its row title, full width, so long paths wrap instead of squeezing the button.
                        Text(state.folder ?: stringResource(Res.string.backup_no_folder_selected), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).offset(y = (-6).dp)
                            .testTag("backup-folder-path"), fontSize = 13.sp, lineHeight = 18.sp, color = if (state.folder != null) HomeInk else HomeMuted)
                    }
                    if (!state.enabled && state.lastFile != null) {
                        PreferenceDivider()
                        Text(stringResource(Res.string.backup_files_kept), Modifier.padding(16.dp), fontSize = 12.sp, color = HomeMuted)
                    }
                }

                if (state.versions.isNotEmpty()) PreferenceGroup(stringResource(Res.string.backup_versions)) {
                    state.versions.forEachIndexed { index, version ->
                        if (index > 0) PreferenceDivider()
                        PreferenceRow(version.date, version.size, Modifier.testTag("backup-version")) {
                            TextButton(onClick = { confirmRestore = version }, enabled = !busy && !state.savingNotes) {
                                Text(stringResource(Res.string.home_restore), color = style.accent)
                            }
                        }
                    }
                }

                PreferenceGroup(stringResource(Res.string.backup_group_file)) {
                    PreferenceRow(stringResource(Res.string.home_export_library), null,
                        Modifier.clickable(enabled = !busy && !state.savingNotes, onClick = onExportBackup).testTag("backup-export-library")) {
                        HomeSymbol(Symbol.ChevronRight, Modifier.size(16.dp))
                    }
                    PreferenceDivider()
                    PreferenceRow(stringResource(Res.string.backup_restore_from), null,
                        Modifier.clickable(enabled = !busy, onClick = onRestore).testTag("backup-restore")) {
                        HomeSymbol(Symbol.ChevronRight, Modifier.size(16.dp))
                    }
                }

                // Help, as small print: how to pick a folder and what a backup holds.
                Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(Res.string.backup_explanation), fontSize = 12.sp, lineHeight = 18.sp, color = HomeMuted, modifier = Modifier.testTag("backup-explanation"))
                    Column {
                        Text(stringResource(Res.string.backup_documents_title), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = HomeInk)
                        Text(stringResource(Res.string.backup_documents_detail), fontSize = 12.sp, lineHeight = 18.sp, color = HomeMuted)
                    }
                    Text(stringResource(Res.string.backup_format_detail), fontSize = 12.sp, lineHeight = 18.sp, color = HomeMuted)
                }
            }
        }
    }
}

/**
 * The top of the screen answers "are my notes backed up?": a status dot and the state in large type, the last backup
 * underneath, any problem in red, and Back up now.
 */
@Composable
private fun BackupStatusPanel(state: BackupSettingsUi, busy: Boolean, onBackUpNow: () -> Unit) {
    val style = LocalHomeStyle.current
    val dot = when { state.error != null -> BackupWarn; state.enabled || busy -> BackupOk; else -> BackupOff }
    val label = stringResource(when {
        state.choosingFolder -> Res.string.backup_state_setting_up; state.running -> Res.string.backup_state_running
        state.error != null -> Res.string.backup_state_attention; state.enabled -> Res.string.backup_state_on; else -> Res.string.backup_state_off })
    Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(style.card)).background(Color.White)
        .border(1.dp, HomeHairline, RoundedCornerShape(style.card)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 520.dp
            val text = @Composable {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                        Text(label, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = HomeInk, letterSpacing = (-.3).sp)
                    }
                    if (state.status.isNotEmpty()) Text(state.status, Modifier.padding(top = 6.dp).testTag("backup-last-success"), fontSize = 14.sp, color = HomeInk)
                    state.lastFile?.let { Text(it, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = HomeMuted, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) }
                    if (state.savingNotes) Text(stringResource(Res.string.backup_saving_notes), Modifier.padding(top = 2.dp), fontSize = 12.sp, color = HomeMuted)
                }
            }
            val button = @Composable { modifier: Modifier ->
                Button(onClick = onBackUpNow, enabled = state.enabled && !busy && !state.savingNotes, shape = RoundedCornerShape(style.item),
                    colors = ButtonDefaults.buttonColors(containerColor = style.accent), modifier = modifier.heightIn(min = 48.dp).testTag("backup-now")) {
                    if (state.running) { CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                    Text(stringResource(if (state.running) Res.string.backup_state_running else Res.string.backup_now))
                }
            }
            if (wide) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) { text() }
                button(Modifier)
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { text(); button(Modifier.fillMaxWidth()) }
        }
        state.error?.let { error ->
            Text(error, Modifier.fillMaxWidth().clip(RoundedCornerShape(style.item)).background(Color(0xFFFCEAE7)).padding(14.dp).testTag("backup-error"),
                color = Color(0xFF8A2A1D), fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
