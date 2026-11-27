package com.allient.folio

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties

/** One-time choice for users in consent regions (see [AppAnalytics]). Can be changed later in Preferences › Privacy. */
@Composable
fun UsageStatsPrompt() {
    if (!AppAnalytics.choicePending) return
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.main_usage_title), color = FolioBrand.Ink) },
        text = {
            Text(stringResource(R.string.main_usage_message), color = FolioBrand.Muted)
        },
        confirmButton = {
            TextButton(onClick = { AppAnalytics.setEnabled(true) }) { Text(stringResource(R.string.main_usage_allow), color = FolioBrand.Sage, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = { AppAnalytics.setEnabled(false) }) { Text(stringResource(R.string.main_usage_decline), color = FolioBrand.Ink) }
        },
    )
}
