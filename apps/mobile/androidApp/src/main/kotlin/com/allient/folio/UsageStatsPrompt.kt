package com.allient.folio

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties

/** One-time choice for users in consent regions (see [AppAnalytics]). Can be changed later in Preferences › Privacy. */
@Composable
fun UsageStatsPrompt() {
    if (!AppAnalytics.choicePending) return
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = FolioBrand.Paper,
        title = { Text("Help improve Folio", color = FolioBrand.Ink) },
        text = {
            Text(
                "Folio can share anonymous usage statistics (which screens and features are used) and crash reports through Google Analytics and Crashlytics. " +
                    "Your notes, books, titles and file names are never shared. You can change this any time in Preferences › Privacy.",
                color = FolioBrand.Muted,
            )
        },
        confirmButton = {
            TextButton(onClick = { AppAnalytics.setEnabled(true) }) { Text("Allow", color = FolioBrand.Sage, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = { AppAnalytics.setEnabled(false) }) { Text("No thanks", color = FolioBrand.Ink) }
        },
    )
}
