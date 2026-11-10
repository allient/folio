package com.allient.folio

/** How long a pull-up must be held on the last page before a page is added. */
const val PULL_TO_ADD_HOLD_MS = 3000L

/** Progress (0..1) of the pull-up-and-hold gesture that adds a page. */
fun pullToAddProgress(heldMillis: Long, holdMillis: Long = PULL_TO_ADD_HOLD_MS): Float =
    if (holdMillis <= 0) 1f else (heldMillis.toFloat() / holdMillis).coerceIn(0f, 1f)
