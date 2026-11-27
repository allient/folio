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
