package com.example.knotes.home

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

const val SMART_BACKUP_EXPLANATION = "Backs up a few minutes after you close a note or leave the app, only when something changed. " +
    "Keeps recent versions and removes old ones automatically. PDF and EPUB files aren't copied; your notes and highlights are."

@Composable
fun BackupSettingsScreen(state: BackupSettingsUi, onBack: () -> Unit, onEnabled: (Boolean) -> Unit,
                         onChooseFolder: () -> Unit, onBackUpNow: () -> Unit, onRestore: () -> Unit,
                         onExportBackup: () -> Unit = {}, onRestoreVersion: (String) -> Unit = {}) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val busy = state.running || state.choosingFolder
    var confirmRestore by remember { mutableStateOf<BackupVersionUi?>(null) }
    confirmRestore?.let { version ->
        AlertDialog(onDismissRequest = { confirmRestore = null }, title = { Text("Restore this version?") },
            text = { Text("Notebooks, folders and reader notes from ${version.date} are added to your library as copies. Nothing in your current library is removed.") },
            confirmButton = { TextButton(onClick = { confirmRestore = null; onRestoreVersion(version.id) }, modifier = Modifier.testTag("backup-restore-confirm")) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize().testTag("backup-settings-screen").background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("backup-settings-back")) { Text("‹", fontSize = 30.sp); Spacer(Modifier.width(8.dp)); Text("Back") }
            Text("Settings", Modifier.padding(start = 8.dp), fontSize = 21.sp, fontWeight = FontWeight.Medium)
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
                    Text("Smart backups", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(SMART_BACKUP_EXPLANATION, color = muted, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.testTag("backup-explanation"))
                }
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF6F8F6), border = BorderStroke(1.dp, Color(0xFFE2E7E3))) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                                Text("Smart backups", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(if (state.enabled) "On: backs up after changes" else "Choose a folder to get started", fontSize = 13.sp, color = muted, modifier = Modifier.padding(top = 5.dp))
                            }
                            Switch(state.enabled, onCheckedChange = onEnabled, enabled = !state.choosingFolder && !state.savingNotes,
                                modifier = Modifier.testTag("backup-enabled").semantics { contentDescription = "Smart backups" })
                        }
                        Text(if (state.enabled) "Your first backup starts after folder selection. During long sessions Folio also backs up about every 15 minutes, and checks every few hours in case anything was missed. Android may delay background work."
                            else "Turn on backups to select a folder. Cancelling folder selection leaves backups off.", fontSize = 13.sp, lineHeight = 20.sp, color = muted)
                        if (!state.enabled && state.lastFile != null) Text("Existing backup files are kept when backup is off.", fontSize = 12.sp, color = muted)
                    }
                }
                if (state.enabled || state.folder != "No folder selected") {
                    Surface(shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Backup folder", fontSize = 13.sp, color = muted)
                            Text(state.folder, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("backup-folder-path"))
                            OutlinedButton(onClick = onChooseFolder, enabled = !busy && !state.savingNotes,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.testTag("backup-choose-folder").heightIn(min = 48.dp)) { Text("Change folder") }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Last backup", Modifier.weight(1f), fontSize = 13.sp, color = muted)
                                Surface(shape = RoundedCornerShape(20.dp), color = if (state.error != null) Color(0xFFFCEAE7) else Color(0xFFE8F0EB)) {
                                    Text(when { state.choosingFolder -> "Setting up"; state.running -> "Backing up…"; state.error != null -> "Needs attention"; state.enabled -> "On"; else -> "Off" },
                                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp)
                                }
                            }
                            Text(state.status, fontSize = 15.sp, modifier = Modifier.testTag("backup-last-success"))
                            state.lastFile?.let { Text(it, fontSize = 12.sp, color = muted) }
                            if (state.savingNotes) Text("Saving your latest notes…", fontSize = 12.sp, color = muted)
                            Button(onClick = onBackUpNow, enabled = state.enabled && !busy && !state.savingNotes,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("backup-now"), shape = RoundedCornerShape(16.dp)) {
                                if (state.running) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                                Text(if (state.running) "Backing up…" else "Back up now")
                            }
                            if (state.versions.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text("Kept versions", fontSize = 13.sp, color = muted)
                                state.versions.forEach { version ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("backup-version"), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(version.date, fontSize = 15.sp)
                                            Text(version.size, fontSize = 12.sp, color = muted)
                                        }
                                        TextButton(onClick = { confirmRestore = version }, enabled = !busy && !state.savingNotes) { Text("Restore") }
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
                    Text("Export full library backup")
                }
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFF6F7F9)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Choose a folder in Documents", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("In the Android folder picker, open Documents. You can create a folder called Folio Backups, open it, then tap Use this folder.", fontSize = 13.sp, color = muted, lineHeight = 21.sp)
                        Text("A .knotes backup includes notebooks and their attachments, plus reader titles, file references, reading positions, highlights and notes. PDF and EPUB files stay in this tablet's storage and are not copied; after restoring on another device, reimport a book to reopen it. Matching books reconnect to their saved annotations. Restoring adds copies and never erases your library. Backups stay on this device; nothing is sent online.", fontSize = 12.sp, color = muted, lineHeight = 19.sp)
                    }
                }
                OutlinedButton(onClick = onRestore, enabled = !busy, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("backup-restore")) { Text("Restore from backup") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
