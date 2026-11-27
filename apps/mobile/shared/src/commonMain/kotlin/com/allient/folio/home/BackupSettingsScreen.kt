package com.allient.folio.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val folder: String = "No folder selected",
    /** For example "Last backup 12:41 · 3 versions · 84 MB". */
    val status: String = "Not backed up yet",
    val lastFile: String? = null,
    val running: Boolean = false,
    val choosingFolder: Boolean = false,
    val error: String? = null,
    val savingNotes: Boolean = false,
    val versions: List<BackupVersionUi> = emptyList(),
)

@Composable
fun BackupSettingsScreen(state: BackupSettingsUi, onBack: () -> Unit, onEnabled: (Boolean) -> Unit,
                         onChooseFolder: () -> Unit, onBackUpNow: () -> Unit, onRestore: () -> Unit,
                         onExportBackup: () -> Unit = {}, onRestoreVersion: (String) -> Unit = {}) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val busy = state.running || state.choosingFolder
    var confirmRestore by remember { mutableStateOf<BackupVersionUi?>(null) }
    confirmRestore?.let { version ->
        AlertDialog(onDismissRequest = { confirmRestore = null }, title = { Text(stringResource(Res.string.backup_restore_version_title)) },
            text = { Text(stringResource(Res.string.backup_restore_version_text, version.date)) },
            confirmButton = { TextButton(onClick = { confirmRestore = null; onRestoreVersion(version.id) }, modifier = Modifier.testTag("backup-restore-confirm")) { Text(stringResource(Res.string.home_restore)) } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text(stringResource(Res.string.home_cancel)) } })
    }
    Column(Modifier.fillMaxSize().testTag("backup-settings-screen").background(MaterialTheme.colorScheme.surface)) {
        // Same header as the library's Trash, Preferences and About screens.
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.testTag("backup-settings-back")) { HomeIconButton(Symbol.Back, stringResource(Res.string.home_back), false, onBack, tag = "back") }
            Text(stringResource(Res.string.backup_settings_title), Modifier.padding(start = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = HomeInk)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFE8F0EB), modifier = Modifier.size(64.dp)) {
                    Canvas(Modifier.padding(17.dp)) {
                        val p = Path().apply {
                            moveTo(size.width * .05f, size.height * .25f); lineTo(size.width * .4f, size.height * .25f)
                            lineTo(size.width * .52f, size.height * .4f); lineTo(size.width * .95f, size.height * .4f)
                            lineTo(size.width * .95f, size.height * .85f); lineTo(size.width * .05f, size.height * .85f); close()
                        }
                        drawPath(p, ink, style = Stroke(1.8.dp.toPx()))
                        drawLine(ink, Offset(size.width * .28f, size.height * .62f), Offset(size.width * .72f, size.height * .62f), 1.8.dp.toPx())
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.backup_smart_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.backup_explanation), color = muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.testTag("backup-explanation"))
                }
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF6F8F6), border = BorderStroke(1.dp, Color(0xFFE2E7E3))) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(stringResource(Res.string.backup_smart_title), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(stringResource(if (state.enabled) Res.string.backup_on_status else Res.string.backup_choose_to_start), fontSize = 13.sp, color = muted, modifier = Modifier.padding(top = 5.dp))
                            }
                            val smartLabel = stringResource(Res.string.backup_smart_title)
                            Switch(state.enabled, onCheckedChange = onEnabled, enabled = !state.choosingFolder && !state.savingNotes,
                                modifier = Modifier.testTag("backup-enabled").semantics { contentDescription = smartLabel })
                        }
                        Text(stringResource(if (state.enabled) Res.string.backup_on_detail else Res.string.backup_off_detail), fontSize = 13.sp, lineHeight = 20.sp, color = muted)
                        if (!state.enabled && state.lastFile != null) Text(stringResource(Res.string.backup_files_kept), fontSize = 12.sp, color = muted)
                    }
                }
                if (state.enabled || state.folder != "No folder selected") {
                    Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(Res.string.backup_folder), fontSize = 13.sp, color = muted)
                            Text(state.folder, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("backup-folder-path"))
                            OutlinedButton(onClick = onChooseFolder, enabled = !busy && !state.savingNotes,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.testTag("backup-choose-folder").heightIn(min = 48.dp)) { Text(stringResource(Res.string.backup_change_folder)) }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.backup_last), Modifier.weight(1f), fontSize = 13.sp, color = muted)
                                Surface(shape = RoundedCornerShape(20.dp), color = if (state.error != null) Color(0xFFFCEAE7) else Color(0xFFE8F0EB)) {
                                    Text(stringResource(when { state.choosingFolder -> Res.string.backup_state_setting_up; state.running -> Res.string.backup_state_running; state.error != null -> Res.string.backup_state_attention; state.enabled -> Res.string.backup_state_on; else -> Res.string.backup_state_off }),
                                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp)
                                }
                            }
                            Text(state.status, fontSize = 15.sp, modifier = Modifier.testTag("backup-last-success"))
                            state.lastFile?.let { Text(it, fontSize = 12.sp, color = muted) }
                            if (state.savingNotes) Text(stringResource(Res.string.backup_saving_notes), fontSize = 12.sp, color = muted)
                            Button(onClick = onBackUpNow, enabled = state.enabled && !busy && !state.savingNotes,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("backup-now"), shape = RoundedCornerShape(16.dp)) {
                                if (state.running) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                                Text(stringResource(if (state.running) Res.string.backup_state_running else Res.string.backup_now))
                            }
                            if (state.versions.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(stringResource(Res.string.backup_versions), fontSize = 13.sp, color = muted)
                                state.versions.forEach { version ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("backup-version"), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(version.date, fontSize = 15.sp)
                                            Text(version.size, fontSize = 12.sp, color = muted)
                                        }
                                        TextButton(onClick = { confirmRestore = version }, enabled = !busy && !state.savingNotes) { Text(stringResource(Res.string.home_restore)) }
                                    }
                                }
                            }
                        }
                    }
                }
                state.error?.let { error ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(error, Modifier.padding(16.dp).testTag("backup-error"), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
                Button(onClick = onExportBackup, enabled = !busy && !state.savingNotes,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("backup-export-library"), shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(Res.string.home_export_library))
                }
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFF6F7F9)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(stringResource(Res.string.backup_documents_title), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(stringResource(Res.string.backup_documents_detail), fontSize = 13.sp, color = muted, lineHeight = 21.sp)
                        Text(stringResource(Res.string.backup_format_detail), fontSize = 12.sp, color = muted, lineHeight = 19.sp)
                    }
                }
                OutlinedButton(onClick = onRestore, enabled = !busy, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("backup-restore")) { Text(stringResource(Res.string.backup_restore_from)) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
