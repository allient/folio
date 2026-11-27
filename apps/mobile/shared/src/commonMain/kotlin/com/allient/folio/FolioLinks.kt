package com.allient.folio

import androidx.compose.runtime.staticCompositionLocalOf

/** Public addresses shown in About and used by the docs site (`apps/docs/src/site.ts` holds the same site and GitHub URLs). */
object FolioLinks {
    const val SITE = "https://folio.allient.io"
    const val GITHUB = "https://github.com/allient/folio"
    const val ISSUES = "$GITHUB/issues"
    const val PRIVACY = "$SITE/privacy/"
    const val LICENSE = "$GITHUB/blob/main/LICENSE"
    const val ALLIENT = "https://allient.io"
}

/** Opens a page inside the app (Android provides a Custom Tab). Null falls back to the platform's link handler. */
val LocalInAppBrowser = staticCompositionLocalOf<((String) -> Unit)?> { null }
