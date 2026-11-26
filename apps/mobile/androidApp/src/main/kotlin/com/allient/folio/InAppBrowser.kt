package com.allient.folio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Opens [url] in a Custom Tab, which stays inside Folio's task, using the Custom Tabs intent extras directly
 * (no androidx.browser dependency). Browsers without Custom Tabs open the page normally.
 */
fun Context.openInAppBrowser(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
        .putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", 0xFF2F7A68.toInt())
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { }
}
