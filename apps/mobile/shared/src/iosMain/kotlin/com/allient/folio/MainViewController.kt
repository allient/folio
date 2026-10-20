package com.allient.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point. Folio ships on Android only for now; this shell keeps the shared module
 * compiling for iOS so common code stays free of Android APIs. See docs: architecture/modules.
 */
fun MainViewController() = ComposeUIViewController {
    MaterialTheme(typography = folioTypography()) {
        Column(
            Modifier.fillMaxSize().background(FolioBrand.Paper).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FolioIcon(Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("Folio for iPad is on the way.", color = FolioBrand.Ink)
        }
    }
}
