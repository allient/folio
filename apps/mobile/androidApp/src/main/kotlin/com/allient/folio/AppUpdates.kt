package com.allient.folio

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play in-app updates.
 *
 * - Priority 4–5 (set per release in the Play Console or fastlane `in_app_update_priority`), or a version
 *   more than [IMMEDIATE_AFTER_DAYS] days old: full-screen immediate update.
 * - Anything else: flexible update that downloads in the background, then a "Restart" bar.
 *   Declining is remembered per version for [ASK_AGAIN_DAYS] days so the prompt doesn't nag.
 *
 * Builds not installed from Play (debug, sideloaded) get an error from Play and nothing is shown.
 */
class PlayUpdates(private val activity: ComponentActivity) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val prefs = activity.getSharedPreferences("play_updates", Context.MODE_PRIVATE)
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    /** True when a flexible update finished downloading and waits for a restart. */
    var readyToInstall by mutableStateOf(false)
        private set

    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) readyToInstall = true
    }

    /** Call once from onCreate. */
    fun check() {
        manager.registerListener(listener)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
            val urgent = info.updatePriority() >= 4 || (info.clientVersionStalenessDays() ?: 0) >= IMMEDIATE_AFTER_DAYS
            when {
                urgent && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> start(info, AppUpdateType.IMMEDIATE)
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) && shouldAsk(info) -> {
                    prefs.edit().putLong(askedKey(info), System.currentTimeMillis()).apply()
                    start(info, AppUpdateType.FLEXIBLE)
                }
            }
        }
    }

    /** Call from onResume: finish an immediate update the user left, or show the bar for a finished download. */
    fun resume() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) readyToInstall = true
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) start(info, AppUpdateType.IMMEDIATE)
        }
    }

    fun install() {
        readyToInstall = false
        manager.completeUpdate()
    }

    fun later() {
        readyToInstall = false
    }

    fun dispose() = manager.unregisterListener(listener)

    private fun start(info: AppUpdateInfo, type: Int) {
        runCatching { manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build()) }
    }

    private fun askedKey(info: AppUpdateInfo) = "asked_${info.availableVersionCode()}"

    private fun shouldAsk(info: AppUpdateInfo): Boolean =
        System.currentTimeMillis() - prefs.getLong(askedKey(info), 0L) > ASK_AGAIN_DAYS * 24L * 60 * 60 * 1000

    private companion object {
        const val IMMEDIATE_AFTER_DAYS = 30
        const val ASK_AGAIN_DAYS = 3
    }
}

/** Bottom bar shown after a flexible update downloaded. Matches the Folio ink/sage chrome. */
@Composable
fun UpdateReadyBar(updates: PlayUpdates, modifier: Modifier = Modifier) {
    if (!updates.readyToInstall) return
    Box(modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = FolioBrand.Ink,
            contentColor = FolioBrand.Cream,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("A new version of Folio is ready.", Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = updates::later, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Later", color = FolioBrand.Cream.copy(alpha = 0.75f))
                }
                TextButton(onClick = updates::install, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Restart", color = FolioBrand.FoldMint, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
