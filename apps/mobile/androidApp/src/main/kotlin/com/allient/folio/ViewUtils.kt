package com.allient.folio

import android.os.Build

import android.content.Context
import android.view.View

/** Density-independent pixels to screen pixels, for touch slop, hit radii and handle sizes. */
internal fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
internal fun View.dp(value: Float): Float = value * resources.displayMetrics.density

/**
 * Height of the reader chrome bands drawn over the full-screen page. The page view leaves
 * touches in these bands to the Compose controls, so both sides must agree on the values.
 */
internal object ReaderChromeBands {
    const val TOP_DP = 88f
    /** Fixed strip above the page, so showing the top bar never moves the text. */
    const val PAGE_TOP_DP = 28f
    const val BOTTOM_DP = 176f
}

/** The window is smaller than the display (split screen, freeform, pop-up view). */
fun android.content.Context.isReducedWindow(): Boolean {
    val activity = findActivity()
    if (Build.VERSION.SDK_INT >= 30 && activity != null) {
        val wm = activity.windowManager
        val current = wm.currentWindowMetrics.bounds; val max = wm.maximumWindowMetrics.bounds
        return current.width() < max.width() * .95f || current.height() < max.height() * .95f
    }
    return activity?.isInMultiWindowMode == true
}

/** The display's own orientation, which a split-screen window's shape doesn't change. */
fun android.content.Context.displayIsLandscape(): Boolean {
    val activity = findActivity()
    if (Build.VERSION.SDK_INT >= 30 && activity != null) { val max = activity.windowManager.maximumWindowMetrics.bounds; return max.width() > max.height() }
    val metrics = resources.displayMetrics; return metrics.widthPixels > metrics.heightPixels
}

/** Before a portrait page width was recorded: the display's short side minus the same side chrome (rail) as now. */
fun android.content.Context.estimatedPortraitPageWidth(viewportWidth: Int): Int {
    val metrics = resources.displayMetrics
    val chrome = (maxOf(metrics.widthPixels, metrics.heightPixels) - viewportWidth).coerceAtLeast(0)
    return (minOf(metrics.widthPixels, metrics.heightPixels) - chrome).coerceAtLeast(0)
}

/** Long side / short side of the display: a portrait page shape to use before a portrait page size was recorded. */
fun android.content.Context.displayPortraitAspect(): Float {
    val metrics = resources.displayMetrics
    return maxOf(metrics.widthPixels, metrics.heightPixels).toFloat() / minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
}

/**
 * Page height / width when no full-screen size was recorded yet: the full display minus the same editor chrome the
 * current window has around its [viewportWidth] × [viewportHeight] drawing area.
 */
fun android.content.Context.estimatedFullPageAspect(viewportWidth: Int, viewportHeight: Int): Float {
    val activity = findActivity()
    if (Build.VERSION.SDK_INT < 30 || activity == null || viewportWidth <= 0 || viewportHeight <= 0) return 0f
    val wm = activity.windowManager
    val current = wm.currentWindowMetrics.bounds; val max = wm.maximumWindowMetrics.bounds
    val width = max.width() - (current.width() - viewportWidth); val height = max.height() - (current.height() - viewportHeight)
    return if (width > 0 && height > 0) height.toFloat() / width else 0f
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) { if (current is android.app.Activity) return current; current = current.baseContext }
    return null
}

/**
 * The app's resources in English. Test tags and flows use the English labels, so components whose tag is built from
 * their label take `tag = english.getString(...)` when the shown label is translated.
 */
@androidx.compose.runtime.Composable
internal fun rememberEnglishResources(): android.content.res.Resources {
    val context = androidx.compose.ui.platform.LocalContext.current
    val current = androidx.compose.ui.platform.LocalConfiguration.current
    return androidx.compose.runtime.remember(context, current) {
        val configuration = android.content.res.Configuration(current).apply { setLocale(java.util.Locale.ENGLISH) }
        context.createConfigurationContext(configuration).resources
    }
}
