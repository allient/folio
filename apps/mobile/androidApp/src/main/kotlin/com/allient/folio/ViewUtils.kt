package com.allient.folio

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
    const val BOTTOM_DP = 176f
}
